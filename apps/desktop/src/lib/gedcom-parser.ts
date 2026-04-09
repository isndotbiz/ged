import {
	insertPerson,
	insertFamily,
	insertEvent,
	insertSource,
	insertMedia,
	insertChildLink,
	clearAll,
	getDb,
	rebuildFTS,
	classifySources,
	computeValidationStatus,
	autoCategorizeMediaAfterDedup,
	autoLinkOrphanMedia,
} from './db'
import { clearRelationshipCache } from './relationship-finder'

interface GedLine {
	level: number
	xref: string
	tag: string
	value: string
}

function detectGedcomVersion(text: string): '5.5.1' | '7.0' {
	const m = text.match(/0\s+HEAD[\s\S]{0,4000}?1\s+GEDC[\s\S]{0,1000}?2\s+VERS\s+([0-9.]+)/i)
	if (!m) return '5.5.1'
	return m[1].startsWith('7') ? '7.0' : '5.5.1'
}

function normalizeTag(tag: string, version: '5.5.1' | '7.0'): string {
	let normalized = tag
	if (version === '7.0' && tag.startsWith('http')) {
		const parts = tag.split('/')
		normalized = parts[parts.length - 1] || tag
	}
	normalized = normalized.toUpperCase()
	if (version === '7.0' && normalized === 'SHARED_NOTE') return 'NOTE'
	if (version === '7.0' && normalized === 'ROLE') return 'RELA'
	return normalized
}

function parseLine(raw: string, version: '5.5.1' | '7.0'): GedLine | null {
	const trimmed = raw.trim()
	if (!trimmed) return null

	// Single regex handles both xref and plain GEDCOM lines
	// LEVEL [@XREF@] TAG [VALUE]
	const m = trimmed.match(/^(\d+)\s+(?:@([^@]+)@\s+)?(\S+)\s*(.*)?$/)
	if (!m) return null
	return {
		level: parseInt(m[1]),
		xref: m[2] || '',
		tag: normalizeTag(m[3], version),
		value: (m[4] || '').trim(),
	}
}

interface Record {
	xref: string
	tag: string
	lines: GedLine[]
}

interface CitationDraft {
	personXref: string
	sourceXref: string
	page: string
	quality: 'PRIMARY' | 'SECONDARY' | 'QUESTIONABLE' | 'UNKNOWN'
}

function groupRecords(lines: GedLine[]): Record[] {
	const records: Record[] = []
	let current: Record | null = null

	for (const line of lines) {
		if (line.level === 0) {
			if (current) records.push(current)
			current = { xref: line.xref, tag: line.tag || line.value, lines: [line] }
		} else if (current) {
			current.lines.push(line)
		}
	}
	if (current) records.push(current)
	return records
}

function getSubValue(lines: GedLine[], parentLevel: number, startIdx: number, tag: string): string {
	for (let i = startIdx + 1; i < lines.length; i++) {
		if (lines[i].level <= parentLevel) break
		if (lines[i].level === parentLevel + 1 && lines[i].tag === tag) {
			return lines[i].value
		}
	}
	return ''
}

function getConcatenatedValue(
	lines: GedLine[],
	parentLevel: number,
	startIdx: number,
	version: '5.5.1' | '7.0'
): string {
	let result =
		version === '7.0' ? lines[startIdx].value.replace(/@@/g, '\n') : lines[startIdx].value
	if (version === '7.0') return result
	for (let i = startIdx + 1; i < lines.length; i++) {
		if (lines[i].level <= parentLevel) break
		if (lines[i].tag === 'CONT') result += '\n' + lines[i].value
		else if (lines[i].tag === 'CONC') result += lines[i].value
	}
	return result
}

function cleanPointer(value: string): string {
	const cleaned = value.replace(/@/g, '')
	return cleaned === 'VOID' ? '' : cleaned
}

function parseName(nameStr: string): { givenName: string; surname: string; suffix: string } {
	// Format: "Given /Surname/Suffix" or "Given /Surname/"
	const surnameMatch = nameStr.match(/^(.*?)\/([^/]*)\/(.*)$/)
	if (surnameMatch) {
		return {
			givenName: surnameMatch[1].trim(),
			surname: surnameMatch[2].trim(),
			suffix: surnameMatch[3].trim(),
		}
	}
	return { givenName: nameStr.trim(), surname: '', suffix: '' }
}

function isLiving(rec: Record): boolean {
	const hasDeathTag = rec.lines.some((l) => l.tag === 'DEAT')
	if (hasDeathTag) return false

	// Check birth year - if born > 110 years ago, assume dead
	for (let i = 0; i < rec.lines.length; i++) {
		if (rec.lines[i].tag === 'BIRT') {
			const dateStr = getSubValue(rec.lines, rec.lines[i].level, i, 'DATE')
			const yearMatch = dateStr.match(/(\d{4})/)
			if (yearMatch) {
				const birthYear = parseInt(yearMatch[1])
				if (new Date().getFullYear() - birthYear > 110) return false
			}
		}
	}
	return !hasDeathTag
}

function extractEventDate(rec: Record, eventTag: string): { date: string; place: string } {
	for (let i = 0; i < rec.lines.length; i++) {
		if (rec.lines[i].tag === eventTag) {
			const date = getSubValue(rec.lines, rec.lines[i].level, i, 'DATE')
			const place = getSubValue(rec.lines, rec.lines[i].level, i, 'PLAC')
			return { date, place }
		}
	}
	return { date: '', place: '' }
}

function countSubTags(rec: Record, tag: string): number {
	return rec.lines.filter((l) => l.tag === tag).length
}

function mapQuayToQuality(quay: string): 'PRIMARY' | 'SECONDARY' | 'QUESTIONABLE' | 'UNKNOWN' {
	const q = quay.trim()
	if (q === '0') return 'QUESTIONABLE'
	if (q === '1') return 'SECONDARY'
	if (q === '2' || q === '3') return 'PRIMARY'
	return 'UNKNOWN'
}

function getParentLine(lines: GedLine[], idx: number): GedLine | null {
	const level = lines[idx].level
	for (let i = idx - 1; i >= 0; i--) {
		if (lines[i].level < level) return lines[i]
	}
	return null
}

function extractCitationsForRecord(
	rec: Record,
	personXrefsForRecord: string[],
	eventTags: string[]
): CitationDraft[] {
	const out: CitationDraft[] = []
	const seen = new Set<string>()
	for (let i = 0; i < rec.lines.length; i++) {
		const line = rec.lines[i]
		if (line.tag !== 'SOUR' || !line.value.startsWith('@')) continue
		const sourceXref = cleanPointer(line.value)
		if (!sourceXref) continue
		const parent = getParentLine(rec.lines, i)
		const isRecordLevel = line.level === 1
		const isEventLevel = parent != null && parent.level === 1 && eventTags.includes(parent.tag)
		if (!isRecordLevel && !isEventLevel) continue
		const page = getSubValue(rec.lines, line.level, i, 'PAGE')
		const quality = mapQuayToQuality(getSubValue(rec.lines, line.level, i, 'QUAY'))
		for (const personXref of personXrefsForRecord) {
			if (!personXref) continue
			const key = `${personXref}|${sourceXref}|${page}|${quality}`
			if (seen.has(key)) continue
			seen.add(key)
			out.push({ personXref, sourceXref, page, quality })
		}
	}
	return out
}

export const __testables = {
	detectGedcomVersion,
	parseLine,
	groupRecords,
	parseName,
	isLiving,
}

export async function importGedcom(
	text: string,
	onProgress?: (pct: number, msg: string) => void,
	options?: { force?: boolean; mode?: 'replace' | 'merge' }
): Promise<{ linked: number; skipped: number }> {
	const force = options?.force === true
	const mergeMode = options?.mode === 'merge'
	const db = await getDb()
	const existing = await db.select<{ c: number }[]>(`SELECT COUNT(*) as c FROM person`)
	const preImportPersonCount = existing[0]?.c ?? 0
	if (!force && !mergeMode && preImportPersonCount > 0) {
		throw new Error('DB not empty — pass force:true to overwrite')
	}

	try {
		if (!mergeMode) {
			onProgress?.(0, 'Clearing database...')
			await clearAll()
		} else {
			onProgress?.(0, 'Merging records...')
		}

		const pendingMediaLinks: {
			personXref: string
			objeRefs: string[]
			inlineMediaIds: number[]
			primaryPhotoXref: string
		}[] = []
		const mediaFileCache = new Map<string, number>()

		async function findMediaByFile(filePath: string): Promise<{ id: number; xref: string } | null> {
			const rows = await db.select<{ id: number; xref: string }[]>(
				`SELECT id, xref FROM media WHERE LOWER(filePath) = LOWER($1) LIMIT 1`,
				[filePath]
			)
			return rows.length > 0 ? rows[0] : null
		}

		async function ensureMediaForFile(
			filePath: string,
			format: string,
			title: string,
			xref?: string
		): Promise<number> {
			const normalized = filePath.trim().replace(/\\/g, '/')
			const normalizedKey = normalized.toLowerCase()
			if (!normalized) return 0
			if (mediaFileCache.has(normalizedKey)) return mediaFileCache.get(normalizedKey)!
			const existing = await findMediaByFile(normalized)
			if (existing) {
				if (!existing.xref && xref) {
					await db.execute(`UPDATE media SET xref = $1 WHERE id = $2`, [xref, existing.id])
				}
				mediaFileCache.set(normalizedKey, existing.id)
				return existing.id
			}
			await insertMedia({ xref: xref || '', ownerXref: '', filePath: normalized, format, title })
			const newRows = await db.select<{ id: number }[]>(
				`SELECT id FROM media WHERE LOWER(filePath) = LOWER($1) ORDER BY id DESC LIMIT 1`,
				[normalized]
			)
			const newId = newRows.length > 0 ? newRows[0].id : 0
			if (newId) {
				mediaFileCache.set(normalizedKey, newId)
			}
			return newId
		}

		onProgress?.(5, 'Parsing GEDCOM lines...')
		const gedVersion = detectGedcomVersion(text)
		const rawLines = text.split('\n')
		const parsed: GedLine[] = []
		for (const raw of rawLines) {
			const line = parseLine(raw, gedVersion)
			if (line) parsed.push(line)
		}

		onProgress?.(15, 'Grouping records...')
		const records = groupRecords(parsed)

		const total = records.length
		let processed = 0

		await db.execute('BEGIN TRANSACTION')
		try {
			for (const rec of records) {
				processed++
				if (processed % 200 === 0) {
					const pct = 15 + Math.round((processed / total) * 80)
					onProgress?.(pct, `Processing record ${processed}/${total}...`)
				}

				if (rec.tag === 'INDI') {
					// Extract name
					const nameLine = rec.lines.find((l) => l.tag === 'NAME')
					const nameStr = nameLine ? nameLine.value : ''
					const { givenName, surname, suffix } = parseName(nameStr)

					// Extract sex
					const sexLine = rec.lines.find((l) => l.tag === 'SEX')
					const sex = sexLine ? sexLine.value : 'U'

					// Extract birth/death
					const birth = extractEventDate(rec, 'BIRT')
					const death = extractEventDate(rec, 'DEAT')

					// Count sources and media
					const sourceCount = countSubTags(rec, 'SOUR')
					const mediaCount = countSubTags(rec, 'OBJE')

					await insertPerson({
						xref: rec.xref,
						givenName,
						surname,
						suffix,
						sex,
						isLiving: isLiving(rec),
						birthDate: birth.date,
						birthPlace: birth.place,
						deathDate: death.date,
						deathPlace: death.place,
						sourceCount,
						mediaCount,
						personColor: '',
						proofStatus: 'UNKNOWN',
						validationStatus: 'unvalidated',
					})

					const indiCitations = extractCitationsForRecord(
						rec,
						[rec.xref],
						[
							'BIRT',
							'DEAT',
							'BURI',
							'CHR',
							'BAPM',
							'RESI',
							'OCCU',
							'EDUC',
							'EMIG',
							'IMMI',
							'NATU',
							'CENS',
							'PROB',
							'WILL',
							'EVEN',
						]
					)
					for (const c of indiCitations) {
						await db.execute(
							`INSERT INTO citation (sourceXref, personXref, eventId, page, quality, text, note) VALUES ($1,$2,'',$3,$4,'','')`,
							[c.sourceXref, c.personXref, c.page, c.quality]
						)
					}

					// Extract events
					const eventTags = [
						'BIRT',
						'DEAT',
						'BURI',
						'CHR',
						'BAPM',
						'RESI',
						'OCCU',
						'EDUC',
						'EMIG',
						'IMMI',
						'NATU',
						'CENS',
						'PROB',
						'WILL',
						'EVEN',
					]
					for (let i = 0; i < rec.lines.length; i++) {
						const l = rec.lines[i]
						if (l.level === 1 && eventTags.includes(l.tag)) {
							const date = getSubValue(rec.lines, l.level, i, 'DATE')
							const place = getSubValue(rec.lines, l.level, i, 'PLAC')
							const desc = l.tag === 'EVEN' ? getSubValue(rec.lines, l.level, i, 'TYPE') : ''
							await insertEvent({
								ownerXref: rec.xref,
								ownerType: 'INDI',
								eventType: l.tag,
								dateValue: date,
								place,
								description: desc || l.value,
							})
						}
					}

					// Collect OBJE references for this person (will link after all OBJE records processed)
					let primaryPhotoXref = ''
					const objeRefs: string[] = []
					const inlineMediaIds: number[] = []

					for (let i = 0; i < rec.lines.length; i++) {
						const l = rec.lines[i]
						if (l.level === 1 && l.tag === '_PHOTO' && l.value.startsWith('@')) {
							primaryPhotoXref = cleanPointer(l.value)
						}
						if (l.tag === 'OBJE' && l.value.startsWith('@')) {
							const ref = cleanPointer(l.value)
							if (ref) objeRefs.push(ref)
						}
						if (l.level === 1 && l.tag === 'OBJE' && !l.value.startsWith('@')) {
							// Inline OBJE — insert directly as media with ownerXref for backward compat
							const file = getSubValue(rec.lines, l.level, i, 'FILE')
							const form = getSubValue(rec.lines, l.level, i, 'FORM')
							const title = getSubValue(rec.lines, l.level, i, 'TITL')
							if (file) {
								const mid = await ensureMediaForFile(file, form, title)
								if (mid) inlineMediaIds.push(mid)
							}
						}
					}

					// Store person's OBJE refs for linking after all records parsed
					if (objeRefs.length > 0 || inlineMediaIds.length > 0 || primaryPhotoXref) {
						pendingMediaLinks.push({
							personXref: rec.xref,
							objeRefs,
							inlineMediaIds,
							primaryPhotoXref,
						})
					}
				} else if (rec.tag === 'FAM') {
					let partner1 = ''
					let partner2 = ''
					const children: string[] = []

					for (const l of rec.lines) {
						if (l.tag === 'HUSB') partner1 = cleanPointer(l.value)
						if (l.tag === 'WIFE') partner2 = cleanPointer(l.value)
						if (l.tag === 'CHIL') {
							const child = cleanPointer(l.value)
							if (child) children.push(child)
						}
					}

					const marr = extractEventDate(rec, 'MARR')

					await insertFamily({
						xref: rec.xref,
						partner1Xref: partner1,
						partner2Xref: partner2,
						marriageDate: marr.date,
						marriagePlace: marr.place,
					})

					const famCitations = extractCitationsForRecord(
						rec,
						[partner1, partner2].filter(Boolean),
						['MARR', 'DIV', 'ANUL', 'EVEN']
					)
					for (const c of famCitations) {
						await db.execute(
							`INSERT INTO citation (sourceXref, personXref, eventId, page, quality, text, note) VALUES ($1,$2,'',$3,$4,'','')`,
							[c.sourceXref, c.personXref, c.page, c.quality]
						)
					}

					// Insert child links
					for (let ci = 0; ci < children.length; ci++) {
						await insertChildLink({
							familyXref: rec.xref,
							childXref: children[ci],
							childOrder: ci,
						})
					}

					// Family events
					const famEventTags = ['MARR', 'DIV', 'ANUL', 'EVEN']
					for (let i = 0; i < rec.lines.length; i++) {
						const l = rec.lines[i]
						if (l.level === 1 && famEventTags.includes(l.tag)) {
							const date = getSubValue(rec.lines, l.level, i, 'DATE')
							const place = getSubValue(rec.lines, l.level, i, 'PLAC')
							await insertEvent({
								ownerXref: rec.xref,
								ownerType: 'FAM',
								eventType: l.tag,
								dateValue: date,
								place,
								description: l.value,
							})
						}
					}
				} else if (rec.tag === 'SOUR') {
					let title = ''
					let author = ''
					let publisher = ''

					for (let i = 0; i < rec.lines.length; i++) {
						const l = rec.lines[i]
						if (l.tag === 'TITL') title = getConcatenatedValue(rec.lines, l.level, i, gedVersion)
						if (l.tag === 'AUTH') author = getConcatenatedValue(rec.lines, l.level, i, gedVersion)
						if (l.tag === 'PUBL')
							publisher = getConcatenatedValue(rec.lines, l.level, i, gedVersion)
					}

					await insertSource({
						xref: rec.xref,
						title,
						author,
						publisher,
						sourceType: 'unknown',
					})
				} else if (rec.tag === 'EVEN') {
					const personRefs = rec.lines
						.filter(
							(l) =>
								l.level === 1 &&
								['ASSO', 'INDI', '_INDI', '_PER'].includes(l.tag) &&
								l.value.startsWith('@')
						)
						.map((l) => cleanPointer(l.value))
						.filter(Boolean)
					const evenCitations = extractCitationsForRecord(rec, personRefs, ['EVEN'])
					for (const c of evenCitations) {
						await db.execute(
							`INSERT INTO citation (sourceXref, personXref, eventId, page, quality, text, note) VALUES ($1,$2,'',$3,$4,'','')`,
							[c.sourceXref, c.personXref, c.page, c.quality]
						)
					}
				} else if (rec.tag === 'OBJE') {
					// Top-level media object — canonical file record
					let filePath = ''
					let format = ''
					let title = ''

					for (let i = 0; i < rec.lines.length; i++) {
						const l = rec.lines[i]
						if (l.tag === 'FILE') {
							filePath = l.value
							format = getSubValue(rec.lines, l.level, i, 'FORM')
						}
						if (l.tag === 'TITL') title = l.value
					}

					if (filePath) {
						const mediaId = await ensureMediaForFile(filePath, format, title, rec.xref)
						// ensureMediaForFile already updates the cache with normalized key
						// but if xref wasn't set on existing row, update it
						if (mediaId && rec.xref) {
							await db.execute(
								`UPDATE media SET xref = $1 WHERE id = $2 AND (xref = '' OR xref IS NULL)`,
								[rec.xref, mediaId]
							)
						}
					}
				}
			}

			// Create media_person_link entries from collected OBJE references
			onProgress?.(93, 'Linking media to people...')
			const allMediaXrefs = await db.select<{ xref: string; id: number }[]>(
				`SELECT xref, id FROM media WHERE xref != ''`
			)
			const mediaXrefMap = new Map(allMediaXrefs.map((m) => [m.xref, m.id]))
			for (const link of pendingMediaLinks) {
				const seenInline = new Set<number>()
				for (const mediaId of link.inlineMediaIds) {
					if (!mediaId || seenInline.has(mediaId)) continue
					seenInline.add(mediaId)
					await db.execute(
						`
        INSERT OR IGNORE INTO media_person_link (mediaId, personXref, isPrimary, role, addedBy, createdAt)
        VALUES ($1, $2, 0, 'tagged', 'gedcom_import', datetime('now'))
      `,
						[mediaId, link.personXref]
					)
				}
				for (const objeXref of link.objeRefs) {
					let mediaId = mediaXrefMap.get(objeXref)
					if (mediaId != null) {
						const isPrimary = objeXref === link.primaryPhotoXref
						await db.execute(
							`
          INSERT OR IGNORE INTO media_person_link (mediaId, personXref, isPrimary, role, addedBy, createdAt)
          VALUES ($1, $2, $3, $4, 'gedcom_import', datetime('now'))
        `,
							[
								mediaId,
								link.personXref,
								isPrimary ? 1 : 0,
								isPrimary ? 'primary_portrait' : 'tagged',
							]
						)
					} else {
						// OBJE record hasn't been seen yet — create a placeholder media row
						await insertMedia({
							xref: objeXref,
							ownerXref: '',
							filePath: '',
							format: objeXref === link.primaryPhotoXref ? 'PRIMARY' : '',
							title: '',
						})
						const newRows = await db.select<{ id: number }[]>(
							`SELECT id FROM media WHERE xref = $1 LIMIT 1`,
							[objeXref]
						)
						if (newRows.length > 0) {
							mediaId = newRows[0].id
							mediaXrefMap.set(objeXref, mediaId)
							const isPrimary = objeXref === link.primaryPhotoXref
							await db.execute(
								`
            INSERT OR IGNORE INTO media_person_link (mediaId, personXref, isPrimary, role, addedBy, createdAt)
            VALUES ($1, $2, $3, $4, 'gedcom_import', datetime('now'))
          `,
								[
									mediaId,
									link.personXref,
									isPrimary ? 1 : 0,
									isPrimary ? 'primary_portrait' : 'tagged',
								]
							)
						}
					}
				}
				// Handle primaryPhotoXref if not in objeRefs
				if (link.primaryPhotoXref && !link.objeRefs.includes(link.primaryPhotoXref)) {
					const mediaId = mediaXrefMap.get(link.primaryPhotoXref)
					if (mediaId != null) {
						await db.execute(
							`
          INSERT OR IGNORE INTO media_person_link (mediaId, personXref, isPrimary, role, addedBy, createdAt)
          VALUES ($1, $2, 1, 'primary_portrait', 'gedcom_import', datetime('now'))
        `,
							[mediaId, link.personXref]
						)
					}
				}
			}

			// Build place index from events
			onProgress?.(96, 'Building place index...')
			await db.execute(`INSERT INTO place (name, normalized, eventCount)
    SELECT place, place, COUNT(*) FROM event WHERE place != '' GROUP BY place`)

			await db.execute('COMMIT')
		} catch (e) {
			await db.execute('ROLLBACK')
			throw e
		}

		// Rebuild FTS index after bulk load
		onProgress?.(95, 'Building search index...')
		await rebuildFTS()

		// Classify sources and compute validation status
		onProgress?.(97, 'Classifying sources...')
		await classifySources()
		await computeValidationStatus()

		// Auto-link orphan media to persons via filename XREF, then categorize
		onProgress?.(99, 'Linking media...')
		const linkResult = await autoLinkOrphanMedia()
		await autoCategorizeMediaAfterDedup()

		// Run PRAGMA optimize after bulk load
		await db.execute('PRAGMA optimize')

		clearRelationshipCache()
		onProgress?.(100, 'Import complete!')
		return linkResult
	} catch (error) {
		let currentPersonCount = 0
		try {
			const current = await db.select<{ c: number }[]>(`SELECT COUNT(*) as c FROM person`)
			currentPersonCount = current[0]?.c ?? 0
		} catch {
			// Keep original import error if count check fails.
		}

		if (preImportPersonCount > 0 && currentPersonCount === 0) {
			console.error(
				'Import failed — DB was cleared but import did not complete. Reload from backup.'
			)
		}
		throw error
	}
}
