import type { TreeIssue, Person, GedcomEvent } from './types'
import { getAllPersons, getFamilies, getDb } from './db'

function parseYear(dateStr: string): number | null {
	if (!dateStr) return null
	const match = dateStr.match(/(\d{4})/)
	return match ? parseInt(match[1]) : null
}

export const __testables = { parseYear }

export async function analyzeTree(
	onProgress?: (pct: number, msg: string) => void
): Promise<TreeIssue[]> {
	const issues: TreeIssue[] = []
	let issueId = 0

	const addIssue = (
		category: TreeIssue['category'],
		severity: TreeIssue['severity'],
		personXref: string,
		familyXref: string,
		title: string,
		detail: string,
		suggestion: string,
		autoFixable: boolean = false,
		fixDescription: string = ''
	) => {
		issues.push({
			id: `issue-${++issueId}`,
			category,
			severity,
			personXref,
			familyXref,
			title,
			detail,
			suggestion,
			autoFixable,
			fixDescription,
		})
	}

	onProgress?.(5, 'Loading data...')
	const db = await getDb()
	const persons = await getAllPersons()
	const families = await getFamilies()
	const personMap = new Map<string, Person>()
	for (const p of persons) personMap.set(p.xref, p)

	// Batch-load all events to avoid N+1 queries
	const allEvents = await db.select<GedcomEvent[]>('SELECT * FROM event')
	const eventsByOwner = new Map<string, GedcomEvent[]>()
	for (const e of allEvents) {
		if (!eventsByOwner.has(e.ownerXref)) eventsByOwner.set(e.ownerXref, [])
		eventsByOwner.get(e.ownerXref)!.push(e)
	}

	// Batch-load all child links to avoid N+1 queries
	const allChildLinks = await db.select<{ familyXref: string; childXref: string }[]>(
		'SELECT * FROM child_link'
	)
	const childrenByFamily = new Map<string, string[]>()
	for (const cl of allChildLinks) {
		if (!childrenByFamily.has(cl.familyXref)) childrenByFamily.set(cl.familyXref, [])
		childrenByFamily.get(cl.familyXref)!.push(cl.childXref)
	}

	const total = persons.length + families.length
	let checked = 0

	// --- Person checks ---
	for (const p of persons) {
		checked++
		if (checked % 100 === 0) {
			onProgress?.(5 + Math.round((checked / total) * 90), `Checking person ${checked}/${total}...`)
		}

		const birthYear = parseYear(p.birthDate)
		const deathYear = parseYear(p.deathDate)

		// 1. Death before birth
		if (birthYear && deathYear && deathYear < birthYear) {
			addIssue(
				'date',
				'critical',
				p.xref,
				'',
				'Death before birth',
				`${p.givenName} ${p.surname}: born ${p.birthDate}, died ${p.deathDate}`,
				'Verify and correct birth/death dates',
				true,
				'Swap birth and death dates'
			)
		}

		// 2. Age over 120
		if (birthYear && deathYear && deathYear - birthYear > 120) {
			addIssue(
				'date',
				'warning',
				p.xref,
				'',
				'Unreasonable age (>120)',
				`${p.givenName} ${p.surname}: lived ${deathYear - birthYear} years`,
				'Verify birth and death dates'
			)
		}

		// 3. Birth in the future
		const currentYear = new Date().getFullYear()
		if (birthYear && birthYear > currentYear) {
			addIssue(
				'date',
				'critical',
				p.xref,
				'',
				'Future birth date',
				`${p.givenName} ${p.surname}: born ${p.birthDate}`,
				'Correct birth date',
				true,
				'Clear birth date'
			)
		}

		// 4. No name
		if (!p.givenName && !p.surname) {
			addIssue(
				'quality',
				'warning',
				p.xref,
				'',
				'Missing name',
				`Person ${p.xref} has no name recorded`,
				'Add name information'
			)
		}

		// 5. No events at all
		const events = eventsByOwner.get(p.xref) || []
		if (events.length === 0) {
			addIssue(
				'quality',
				'info',
				p.xref,
				'',
				'No events recorded',
				`${p.givenName} ${p.surname} has no events`,
				'Add birth, death, or other life events'
			)
		}

		// 6. No sources
		if (p.sourceCount === 0) {
			addIssue(
				'quality',
				'warning',
				p.xref,
				'',
				'No source citations',
				`${p.givenName} ${p.surname} has no sources — this person is unvalidated`,
				'Add source citations from vital records, census, or church records'
			)
		}

		// 6b. Tree-only sources (has citations but all from online trees)
		if (p.sourceCount > 0 && (p as any).validationStatus === 'tree_only') {
			addIssue(
				'quality',
				'warning',
				p.xref,
				'',
				'Online tree sources only',
				`${p.givenName} ${p.surname} has ${p.sourceCount} source(s) but all are from online trees. No primary or secondary records found.`,
				'Search for vital records, census entries, church records, or newspapers to validate this person'
			)
		}

		// 7. Birth before 1000 AD
		if (birthYear && birthYear < 1000) {
			addIssue(
				'date',
				'warning',
				p.xref,
				'',
				'Very early birth date',
				`${p.givenName} ${p.surname}: born ${p.birthDate}`,
				'Verify this date is correct'
			)
		}
	}

	// --- Family checks ---
	for (const fam of families) {
		checked++
		if (checked % 50 === 0) {
			onProgress?.(5 + Math.round((checked / total) * 90), `Checking family ${fam.xref}...`)
		}

		const p1 = fam.partner1Xref ? personMap.get(fam.partner1Xref) : null
		const p2 = fam.partner2Xref ? personMap.get(fam.partner2Xref) : null

		// 8. Marriage before age 14
		const marrYear = parseYear(fam.marriageDate)
		if (marrYear) {
			if (p1) {
				const birthYear = parseYear(p1.birthDate)
				if (birthYear && marrYear - birthYear < 14) {
					addIssue(
						'date',
						'warning',
						p1.xref,
						fam.xref,
						'Marriage before age 14',
						`${p1.givenName} ${p1.surname} married at age ${marrYear - birthYear}`,
						'Verify marriage and birth dates'
					)
				}
			}
			if (p2) {
				const birthYear = parseYear(p2.birthDate)
				if (birthYear && marrYear - birthYear < 14) {
					addIssue(
						'date',
						'warning',
						p2.xref,
						fam.xref,
						'Marriage before age 14',
						`${p2.givenName} ${p2.surname} married at age ${marrYear - birthYear}`,
						'Verify marriage and birth dates'
					)
				}
			}
		}

		// 9. Parent-child age difference < 12
		const childXrefs = childrenByFamily.get(fam.xref) || []
		const children = childXrefs.map((x) => personMap.get(x)).filter((p): p is Person => !!p)
		for (const child of children) {
			const childBirth = parseYear(child.birthDate)
			if (!childBirth) continue

			for (const parent of [p1, p2]) {
				if (!parent) continue
				const parentBirth = parseYear(parent.birthDate)
				if (parentBirth && childBirth - parentBirth < 12) {
					addIssue(
						'relationship',
						'warning',
						child.xref,
						fam.xref,
						'Parent too young',
						`${parent.givenName} ${parent.surname} was ${childBirth - parentBirth} when ${child.givenName} was born`,
						'Verify parent-child relationship'
					)
				}
			}

			// 10. Child born after parent death
			for (const parent of [p1, p2]) {
				if (!parent) continue
				const parentDeath = parseYear(parent.deathDate)
				if (parentDeath && childBirth > parentDeath + 1) {
					addIssue(
						'relationship',
						'critical',
						child.xref,
						fam.xref,
						'Born after parent death',
						`${child.givenName} ${child.surname} born ${child.birthDate}, parent ${parent.givenName} died ${parent.deathDate}`,
						'Verify dates and parent-child relationship'
					)
				}
			}
		}

		// 11. Same person as both partners
		if (fam.partner1Xref && fam.partner1Xref === fam.partner2Xref) {
			addIssue(
				'relationship',
				'critical',
				fam.partner1Xref,
				fam.xref,
				'Person married to self',
				`Family ${fam.xref} has same person as both partners`,
				'Correct family record',
				true,
				'Remove duplicate partner from family'
			)
		}

		// 12. No partners in family
		if (!fam.partner1Xref && !fam.partner2Xref) {
			addIssue(
				'relationship',
				'warning',
				'',
				fam.xref,
				'Family with no partners',
				`Family ${fam.xref} has no partner records`,
				'Add partner information or remove family',
				true,
				'Delete empty family record'
			)
		}
	}

	// --- Duplicate checks ---
	const nameMap = new Map<string, Person[]>()
	for (const p of persons) {
		if (!p.givenName && !p.surname) continue
		const key = `${p.givenName.toLowerCase()}|${p.surname.toLowerCase()}`
		if (!nameMap.has(key)) nameMap.set(key, [])
		nameMap.get(key)!.push(p)
	}

	for (const [_key, group] of nameMap) {
		if (group.length > 1) {
			// Check if same birth year too
			const withBirth = group.filter((p) => parseYear(p.birthDate))
			const birthYears = new Map<number, Person[]>()
			for (const p of withBirth) {
				const y = parseYear(p.birthDate)!
				if (!birthYears.has(y)) birthYears.set(y, [])
				birthYears.get(y)!.push(p)
			}

			for (const [year, dupes] of birthYears) {
				if (dupes.length > 1) {
					addIssue(
						'duplicate',
						'warning',
						dupes[0].xref,
						'',
						`Potential duplicate: ${dupes[0].givenName} ${dupes[0].surname}`,
						`${dupes.length} persons named "${dupes[0].givenName} ${dupes[0].surname}" born in ${year}: ${dupes.map((d) => d.xref).join(', ')}`,
						'Review and merge if duplicates'
					)
				}
			}
		}
	}

	onProgress?.(100, `Analysis complete: ${issues.length} issues found`)
	return issues
}
