import { getDb } from './db'
import type { Person, FaceDetectionBox } from './types'
import { isTauri } from './platform'

async function tauriInvoke<T>(cmd: string, args: Record<string, unknown>): Promise<T> {
	if (!isTauri()) throw new Error('Tauri required')
	const { invoke } = await import('@tauri-apps/api/core')
	return invoke<T>(cmd, args)
}

interface MatchResult {
	filePath: string
	matchedPerson: Person | null
	matchType: 'exact' | 'surname' | 'fuzzy' | 'none'
	confidence: number
}

interface MatchReport {
	total: number
	matched: number
	unmatched: number
	created: number
	linked: number
	results: MatchResult[]
}

interface FaceApiDetection {
	detection: {
		score?: number
		box: {
			x: number
			y: number
			width: number
			height: number
		}
	}
	landmarks: unknown
}

let faceModelsLoaded = false

async function getFaceApi() {
	return await import('@vladmandic/face-api')
}

async function ensureFaceModelsLoaded() {
	if (faceModelsLoaded) return
	try {
		const faceapi = await getFaceApi()
		await Promise.all([
			faceapi.nets.tinyFaceDetector.loadFromUri('/models/face-api'),
			faceapi.nets.faceLandmark68Net.loadFromUri('/models/face-api'),
		])
		faceModelsLoaded = true
	} catch (error) {
		faceModelsLoaded = false
		throw error
	}
}

async function loadImageElement(path: string): Promise<HTMLImageElement> {
	const src = isTauri() ? (await import('@tauri-apps/api/core')).convertFileSrc(path) : path
	return await new Promise<HTMLImageElement>((resolve, reject) => {
		const img = new Image()
		img.onload = () => resolve(img)
		img.onerror = () => reject(new Error(`Unable to load image at ${path}`))
		img.src = src
	})
}

export async function detectFaces(mediaPath: string): Promise<FaceDetectionBox[]> {
	if (typeof window === 'undefined' || !mediaPath) return []
	await ensureFaceModelsLoaded()
	const faceapi = await getFaceApi()
	const image = await loadImageElement(mediaPath)
	const detections = await faceapi
		.detectAllFaces(
			image,
			new faceapi.TinyFaceDetectorOptions({ inputSize: 512, scoreThreshold: 0.35 })
		)
		.withFaceLandmarks(true)

	return (detections as FaceApiDetection[]).map((d) => ({
		x: d.detection.box.x,
		y: d.detection.box.y,
		w: d.detection.box.width,
		h: d.detection.box.height,
		confidence: d.detection.score ?? 0,
		imageWidth: image.naturalWidth || image.width,
		imageHeight: image.naturalHeight || image.height,
	}))
}

export async function tagFaceOnMedia(
	mediaXref: string,
	personXref: string,
	faceBox: { x: number; y: number; w: number; h: number }
): Promise<void> {
	const db = await getDb()
	let mediaId = Number.parseInt(mediaXref, 10)
	if (!Number.isFinite(mediaId) || mediaId <= 0) {
		const row = await db.select<{ id: number }[]>(`SELECT id FROM media WHERE xref = $1 LIMIT 1`, [
			mediaXref,
		])
		mediaId = row[0]?.id ?? 0
	}
	if (!mediaId) throw new Error(`Media not found: ${mediaXref}`)

	await db.execute(
		`INSERT INTO media_person_link (mediaId, personXref, role, isPrimary, faceX, faceY, faceW, faceH, addedBy, createdAt)
     VALUES ($1, $2, 'face_tag', 0, $3, $4, $5, $6, 'face_detection', datetime('now'))
     ON CONFLICT(mediaId, personXref) DO UPDATE SET
       faceX = excluded.faceX,
       faceY = excluded.faceY,
       faceW = excluded.faceW,
       faceH = excluded.faceH`,
		[mediaId, personXref, faceBox.x, faceBox.y, faceBox.w, faceBox.h]
	)
}

export async function scanAndMatchMedia(
	directories: string[],
	onProgress?: (pct: number, msg: string) => void
): Promise<MatchReport> {
	const db = await getDb()
	const report: MatchReport = {
		total: 0,
		matched: 0,
		unmatched: 0,
		created: 0,
		linked: 0,
		results: [],
	}

	onProgress?.(5, 'Scanning directories...')

	const allFiles: string[] = []
	for (const dir of directories) {
		try {
			const files = await tauriInvoke<string[]>('list_image_files', { dir })
			allFiles.push(...files)
		} catch {
			// Directory not accessible, skip
		}
	}
	report.total = allFiles.length

	onProgress?.(15, `Found ${allFiles.length} files. Loading existing media...`)

	const existingMedia = await db.select<{ filePath: string }[]>('SELECT filePath FROM media')
	const existingPaths = new Set(existingMedia.map((m) => m.filePath))

	const persons = await db.select<Person[]>('SELECT * FROM person')
	const bySurname = new Map<string, Person[]>()
	for (const p of persons) {
		const key = p.surname.toLowerCase().trim()
		if (!key) continue
		if (!bySurname.has(key)) bySurname.set(key, [])
		bySurname.get(key)!.push(p)
	}

	// Process each file
	const unlinked = allFiles.filter((f) => !existingPaths.has(f))
	onProgress?.(25, `${unlinked.length} unlinked files to process...`)

	for (let i = 0; i < unlinked.length; i++) {
		if (i % 20 === 0) {
			onProgress?.(25 + (i / unlinked.length) * 70, `Matching ${i}/${unlinked.length}...`)
		}

		const filePath = unlinked[i]
		const result = matchFileToPersons(filePath, bySurname)
		report.results.push(result)

		if (result.matchedPerson) {
			report.matched++
			// Insert media entry
			const ext = filePath.split('.').pop()?.toLowerCase() || ''
			const isImage = ['jpg', 'jpeg', 'png', 'gif', 'bmp', 'webp', 'tiff', 'tif'].includes(ext)
			const format = isImage ? 'photo' : 'document'
			const title =
				filePath
					.split('/')
					.pop()
					?.replace(/\.[^.]+$/, '')
					.replace(/_/g, ' ') || ''

			const insertResult = await db.execute(
				`INSERT INTO media (xref, ownerXref, filePath, format, title) VALUES ('', '', $1, $2, $3)`,
				[filePath, format, title]
			)
			const mediaId = insertResult.lastInsertId ?? 0
			if (mediaId > 0) {
				report.created++
				// Create link
				await db.execute(
					`INSERT OR IGNORE INTO media_person_link (mediaId, personXref, role, isPrimary, addedBy, createdAt)
           VALUES ($1, $2, 'tagged', 0, 'media_matcher', datetime('now'))`,
					[mediaId, result.matchedPerson.xref]
				)
				report.linked++
			}
		} else {
			report.unmatched++
		}
	}

	onProgress?.(100, `Done: ${report.matched} matched, ${report.unmatched} unmatched`)
	return report
}

function matchFileToPersons(filePath: string, bySurname: Map<string, Person[]>): MatchResult {
	// Extract parts from path: .../Surname/Firstname_Surname_Title.ext
	const parts = filePath.split('/')
	const filename = parts[parts.length - 1]?.replace(/\.[^.]+$/, '') || ''
	const parentDir = parts[parts.length - 2] || ''

	// Try surname from parent directory
	const dirSurname = parentDir.toLowerCase().replace(/_/g, ' ').trim()
	const candidates = bySurname.get(dirSurname) || []

	if (candidates.length === 0) {
		// Try surname from filename
		const filenameParts = filename.split('_')
		for (const part of filenameParts) {
			const normalized = part.toLowerCase().trim()
			if (bySurname.has(normalized)) {
				const surnameMatches = bySurname.get(normalized)!
				// Try to match given name too
				const bestMatch = findBestNameMatch(filename, surnameMatches)
				if (bestMatch)
					return { filePath, matchedPerson: bestMatch, matchType: 'fuzzy', confidence: 0.6 }
				return { filePath, matchedPerson: surnameMatches[0], matchType: 'surname', confidence: 0.4 }
			}
		}
		return { filePath, matchedPerson: null, matchType: 'none', confidence: 0 }
	}

	// Have surname candidates — try to match given name from filename
	const bestMatch = findBestNameMatch(filename, candidates)
	if (bestMatch) return { filePath, matchedPerson: bestMatch, matchType: 'exact', confidence: 0.9 }

	// Surname-only match — pick the first candidate
	if (candidates.length === 1) {
		return { filePath, matchedPerson: candidates[0], matchType: 'surname', confidence: 0.7 }
	}

	return { filePath, matchedPerson: candidates[0], matchType: 'surname', confidence: 0.4 }
}

function findBestNameMatch(filename: string, candidates: Person[]): Person | null {
	const normalizedFile = filename.toLowerCase().replace(/_/g, ' ')

	for (const p of candidates) {
		const given = p.givenName.toLowerCase().trim()
		if (!given) continue
		// Check if given name appears in filename
		if (normalizedFile.includes(given)) {
			return p
		}
		// Check first name only
		const firstName = given.split(' ')[0]
		if (firstName.length > 2 && normalizedFile.includes(firstName)) {
			return p
		}
	}
	return null
}

export const __testables = {
	matchFileToPersons,
	findBestNameMatch,
}

// Dedup: find media with identical filePaths and merge
export async function deduplicateMedia(
	onProgress?: (pct: number, msg: string) => void
): Promise<{ duplicateGroups: number; entriesRemoved: number; linksTransferred: number }> {
	const db = await getDb()
	let duplicateGroups = 0
	let entriesRemoved = 0
	let linksTransferred = 0

	onProgress?.(10, 'Finding duplicate media entries...')

	// Find groups of media with identical filePaths
	const dupes = await db.select<{ filePath: string; cnt: number }[]>(
		`SELECT filePath, COUNT(*) as cnt FROM media WHERE filePath != '' GROUP BY filePath HAVING cnt > 1`
	)

	onProgress?.(30, `Found ${dupes.length} duplicate groups...`)

	for (let i = 0; i < dupes.length; i++) {
		const group = dupes[i]
		if (i % 10 === 0) onProgress?.(30 + (i / dupes.length) * 60, `Merging ${i}/${dupes.length}...`)

		// Get all media rows with this path
		const rows = await db.select<{ id: number; xref: string }[]>(
			`SELECT id, xref FROM media WHERE filePath = $1 ORDER BY id`,
			[group.filePath]
		)
		if (rows.length < 2) continue

		// Keep the first one (or the one with an xref)
		const keepRow = rows.find((r) => r.xref) || rows[0]
		const removeRows = rows.filter((r) => r.id !== keepRow.id)

		for (const rm of removeRows) {
			// Transfer all person links from removed to kept
			const links = await db.select<{ personXref: string; role: string; isPrimary: number }[]>(
				`SELECT personXref, role, isPrimary FROM media_person_link WHERE mediaId = $1`,
				[rm.id]
			)
			for (const link of links) {
				await db.execute(
					`INSERT OR IGNORE INTO media_person_link (mediaId, personXref, role, isPrimary, addedBy, createdAt)
           VALUES ($1, $2, $3, $4, 'dedup_merge', datetime('now'))`,
					[keepRow.id, link.personXref, link.role, link.isPrimary]
				)
				linksTransferred++
			}
			// Delete links from removed media
			await db.execute(`DELETE FROM media_person_link WHERE mediaId = $1`, [rm.id])
			// Delete the duplicate media row
			await db.execute(`DELETE FROM media WHERE id = $1`, [rm.id])
			entriesRemoved++
		}
		duplicateGroups++
	}

	onProgress?.(100, `Done: ${duplicateGroups} groups merged, ${entriesRemoved} entries removed`)
	return { duplicateGroups, entriesRemoved, linksTransferred }
}

// People dedup: find potential duplicate persons
export interface DuplicateCandidate {
	person1: Person
	person2: Person
	score: number
	matchReasons: string[]
}

export async function findDuplicatePeople(
	onProgress?: (pct: number, msg: string) => void
): Promise<DuplicateCandidate[]> {
	const db = await getDb()
	const candidates: DuplicateCandidate[] = []

	onProgress?.(5, 'Loading persons...')
	const persons = await db.select<Person[]>('SELECT * FROM person ORDER BY surname, givenName')

	onProgress?.(15, `Comparing ${persons.length} persons...`)

	// Group by surname for O(n) per group instead of O(n^2) total
	const bySurname = new Map<string, Person[]>()
	for (const p of persons) {
		const key = p.surname.toLowerCase().trim()
		if (!key) continue
		if (!bySurname.has(key)) bySurname.set(key, [])
		bySurname.get(key)!.push(p)
	}

	let groupsDone = 0
	const totalGroups = bySurname.size

	for (const [surname, group] of bySurname) {
		groupsDone++
		if (groupsDone % 10 === 0) {
			onProgress?.(15 + (groupsDone / totalGroups) * 80, `Checking ${surname}...`)
		}

		for (let i = 0; i < group.length; i++) {
			for (let j = i + 1; j < group.length; j++) {
				const p1 = group[i]
				const p2 = group[j]
				const { score, reasons } = scoreDuplicate(p1, p2)
				if (score >= 0.5) {
					candidates.push({ person1: p1, person2: p2, score, matchReasons: reasons })
				}
			}
		}
	}

	candidates.sort((a, b) => b.score - a.score)
	onProgress?.(100, `Found ${candidates.length} potential duplicates`)
	return candidates
}

function scoreDuplicate(p1: Person, p2: Person): { score: number; reasons: string[] } {
	let score = 0
	const reasons: string[] = []

	// Same given name
	const g1 = p1.givenName.toLowerCase().trim()
	const g2 = p2.givenName.toLowerCase().trim()
	if (g1 && g2 && g1 === g2) {
		score += 0.3
		reasons.push('Same given name')
	} else if (g1 && g2 && (g1.startsWith(g2) || g2.startsWith(g1))) {
		score += 0.15
		reasons.push('Similar given name')
	}

	// Same birth date
	if (p1.birthDate && p2.birthDate) {
		if (p1.birthDate === p2.birthDate) {
			score += 0.3
			reasons.push('Same birth date')
		} else {
			const y1 = p1.birthDate.match(/(\d{4})/)?.[1]
			const y2 = p2.birthDate.match(/(\d{4})/)?.[1]
			if (y1 && y2 && y1 === y2) {
				score += 0.1
				reasons.push('Same birth year')
			}
		}
	}

	// Same birth place
	if (
		p1.birthPlace &&
		p2.birthPlace &&
		p1.birthPlace.toLowerCase() === p2.birthPlace.toLowerCase()
	) {
		score += 0.2
		reasons.push('Same birth place')
	}

	// Same death date
	if (p1.deathDate && p2.deathDate && p1.deathDate === p2.deathDate) {
		score += 0.2
		reasons.push('Same death date')
	}

	// Surname already matched (same group), so baseline
	score += 0.1
	reasons.push('Same surname')

	return { score: Math.min(1, score), reasons }
}

export async function mergePersons(
	keepXref: string,
	removeXref: string
): Promise<{ success: boolean; error?: string }> {
	const db = await getDb()
	try {
		// Snapshot the removed person for undo
		const removedPerson = await db.select<Person[]>(`SELECT * FROM person WHERE xref = $1`, [
			removeXref,
		])
		const removedEvents = await db.select<any[]>(`SELECT * FROM event WHERE ownerXref = $1`, [
			removeXref,
		])
		const removedMediaLinks = await db.select<any[]>(
			`SELECT * FROM media_person_link WHERE personXref = $1`,
			[removeXref]
		)
		const removedChildLinks = await db.select<any[]>(
			`SELECT * FROM child_link WHERE childXref = $1`,
			[removeXref]
		)

		// Log the merge for future undo
		const mergeLogResult = await db.execute(
			`INSERT INTO merge_log (keepXref, removeXref, removedPersonJson, removedEventsJson, removedMediaLinksJson, removedChildLinksJson) VALUES ($1, $2, $3, $4, $5, $6)`,
			[
				keepXref,
				removeXref,
				JSON.stringify(removedPerson),
				JSON.stringify(removedEvents),
				JSON.stringify(removedMediaLinks),
				JSON.stringify(removedChildLinks),
			]
		)
		const mergeLogId = mergeLogResult.lastInsertId ?? 0

		// Transfer events
		await db.execute(`UPDATE event SET ownerXref = $1 WHERE ownerXref = $2`, [keepXref, removeXref])
		// Transfer media links
		await db.execute(
			`UPDATE OR IGNORE media_person_link SET personXref = $1 WHERE personXref = $2`,
			[keepXref, removeXref]
		)
		// Clean up duplicate media links
		await db.execute(
			`DELETE FROM media_person_link WHERE personXref = $1 AND id NOT IN (
      SELECT MIN(id) FROM media_person_link WHERE personXref = $1 GROUP BY mediaId
    )`,
			[keepXref]
		)
		// Transfer child links
		await db.execute(`UPDATE OR IGNORE child_link SET childXref = $1 WHERE childXref = $2`, [
			keepXref,
			removeXref,
		])
		// Transfer family partner references
		await db.execute(`UPDATE family SET partner1Xref = $1 WHERE partner1Xref = $2`, [
			keepXref,
			removeXref,
		])
		await db.execute(`UPDATE family SET partner2Xref = $1 WHERE partner2Xref = $2`, [
			keepXref,
			removeXref,
		])
		// Fill in missing fields on the kept person
		const keep = await db.select<Person[]>(`SELECT * FROM person WHERE xref = $1`, [keepXref])
		if (keep.length > 0 && removedPerson.length > 0) {
			const k = keep[0]
			const r = removedPerson[0]
			const updates: string[] = []
			const vals: any[] = []
			let i = 1
			if (!k.birthDate && r.birthDate) {
				updates.push(`birthDate = $${i++}`)
				vals.push(r.birthDate)
			}
			if (!k.birthPlace && r.birthPlace) {
				updates.push(`birthPlace = $${i++}`)
				vals.push(r.birthPlace)
			}
			if (!k.deathDate && r.deathDate) {
				updates.push(`deathDate = $${i++}`)
				vals.push(r.deathDate)
			}
			if (!k.deathPlace && r.deathPlace) {
				updates.push(`deathPlace = $${i++}`)
				vals.push(r.deathPlace)
			}
			if (updates.length > 0) {
				vals.push(keepXref)
				await db.execute(`UPDATE person SET ${updates.join(', ')} WHERE xref = $${i}`, vals)
			}
		}
		// Delete the removed person
		await db.execute(`DELETE FROM person WHERE xref = $1`, [removeXref])
		const { undoManager } = await import('./undo-manager')
		await undoManager.push(
			{
				id: crypto.randomUUID(),
				description: `Merged ${removeXref} into ${keepXref}`,
				timestamp: new Date().toISOString(),
				undo: async () => {
					await unmergePersons(mergeLogId)
				},
				redo: async () => {
					await mergePersons(keepXref, removeXref)
				},
			},
			{
				tableName: 'merge_log',
				rowId: String(mergeLogId),
				oldData: { keepXref, removeXref },
				newData: { keepXref, removeXref, mergeLogId },
			}
		)
		return { success: true }
	} catch (e) {
		return { success: false, error: String(e) }
	}
}

export interface MergeLogEntry {
	id: number
	keepXref: string
	removeXref: string
	removedPersonJson: string
	mergedAt: string
	undoneAt: string
}

export async function getMergeLog(): Promise<MergeLogEntry[]> {
	const db = await getDb()
	return db.select<MergeLogEntry[]>(`SELECT * FROM merge_log ORDER BY mergedAt DESC`)
}

export async function unmergePersons(
	mergeLogId: number
): Promise<{ success: boolean; error?: string }> {
	const db = await getDb()
	try {
		const rows = await db.select<any[]>(`SELECT * FROM merge_log WHERE id = $1`, [mergeLogId])
		if (rows.length === 0) return { success: false, error: 'Merge log entry not found' }
		const log = rows[0]
		if (log.undoneAt) return { success: false, error: 'Already undone' }

		const removedPerson = JSON.parse(log.removedPersonJson)[0]
		const removedEvents = JSON.parse(log.removedEventsJson)
		const removedMediaLinks = JSON.parse(log.removedMediaLinksJson)
		const removedChildLinks = JSON.parse(log.removedChildLinksJson)

		if (!removedPerson) return { success: false, error: 'No person data in merge log' }

		// Re-insert the removed person
		await db.execute(
			`INSERT OR IGNORE INTO person (xref, givenName, surname, suffix, sex, isLiving, birthDate, birthPlace, deathDate, deathPlace, sourceCount, mediaCount, personColor, proofStatus) VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,$10,$11,$12,$13,$14)`,
			[
				removedPerson.xref,
				removedPerson.givenName,
				removedPerson.surname,
				removedPerson.suffix,
				removedPerson.sex,
				removedPerson.isLiving,
				removedPerson.birthDate,
				removedPerson.birthPlace,
				removedPerson.deathDate,
				removedPerson.deathPlace,
				removedPerson.sourceCount,
				removedPerson.mediaCount,
				removedPerson.personColor || '',
				removedPerson.proofStatus || 'UNKNOWN',
			]
		)

		// Restore events that belonged to the removed person
		for (const evt of removedEvents) {
			await db.execute(
				`INSERT INTO event (ownerXref, ownerType, eventType, dateValue, place, description) VALUES ($1,$2,$3,$4,$5,$6)`,
				[evt.ownerXref, evt.ownerType, evt.eventType, evt.dateValue, evt.place, evt.description]
			)
		}

		// Restore media links
		for (const ml of removedMediaLinks) {
			await db.execute(
				`INSERT OR IGNORE INTO media_person_link (mediaId, personXref, role, isPrimary, sortOrder, faceX, faceY, faceW, faceH, caption, addedBy, verified, createdAt) VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,$10,$11,$12,$13)`,
				[
					ml.mediaId,
					ml.personXref,
					ml.role,
					ml.isPrimary,
					ml.sortOrder,
					ml.faceX,
					ml.faceY,
					ml.faceW,
					ml.faceH,
					ml.caption,
					ml.addedBy,
					ml.verified,
					ml.createdAt,
				]
			)
		}

		// Restore child links
		for (const cl of removedChildLinks) {
			await db.execute(
				`INSERT OR IGNORE INTO child_link (familyXref, childXref, childOrder) VALUES ($1,$2,$3)`,
				[cl.familyXref, cl.childXref, cl.childOrder]
			)
		}

		// Mark as undone
		await db.execute(`UPDATE merge_log SET undoneAt = datetime('now') WHERE id = $1`, [mergeLogId])

		return { success: true }
	} catch (e) {
		return { success: false, error: String(e) }
	}
}
