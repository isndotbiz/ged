import { describe, expect, it } from 'vitest'
import { __testables } from '$lib/media-matcher'

const { matchFileToPersons, findBestNameMatch } = __testables

describe('media-matcher filename matching', () => {
	const doe = [
		{ xref: 'I1', givenName: 'John', surname: 'Doe' },
		{ xref: 'I2', givenName: 'Jane', surname: 'Doe' },
	] as any[]

	it('matches filename to person by surname + given name', () => {
		const bySurname = new Map<string, any[]>([['doe', doe]])
		const match = matchFileToPersons('/photos/Doe/John_Doe_1900.jpg', bySurname)
		expect(match.matchedPerson?.xref).toBe('I1')
		expect(match.confidence).toBeGreaterThan(0.5)
	})

	it('findBestNameMatch returns null when none', () => {
		const match = findBestNameMatch('unknown_person', doe as any)
		expect(match).toBeNull()
	})

	it('dedup heuristic: same path key should normalize as duplicate', async () => {
		const { normalizeMediaPath } = await import('$lib/db')
		expect(normalizeMediaPath('A\\B\\photo.JPG')).toBe('a/b/photo.jpg')
	})
})
