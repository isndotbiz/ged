import { beforeEach, describe, expect, it, vi } from 'vitest'

vi.mock('$lib/platform', () => ({ isTauri: () => true }))

describe('db.ts helpers', () => {
	beforeEach(() => {
		vi.resetModules()
	})

	it('SAFE_FIELDS allows whitelisted fields only', async () => {
		const mod = await import('$lib/db')
		expect(mod.SAFE_FIELDS.person.has('birthDate')).toBe(true)
		expect(mod.SAFE_FIELDS.person.has('id')).toBe(false)
		expect(mod.SAFE_FIELDS.family.has('partner1Xref')).toBe(true)
	})

	it('getPersons handles empty search and limit', async () => {
		const fakeDb = {
			select: vi.fn(async (query: string, args?: unknown[]) => {
				if (query.includes('SELECT * FROM person ORDER BY surname')) {
					expect(args).toEqual([2])
					return [{ xref: 'I1' }, { xref: 'I2' }]
				}
				return []
			}),
			execute: vi.fn(async () => ({ rowsAffected: 0, lastInsertId: 0 })),
		}

		vi.doMock('@tauri-apps/plugin-sql', () => ({
			default: { load: vi.fn(async () => fakeDb) },
		}))

		const mod = await import('$lib/db')
		const rows = await mod.getPersons('', 2)
		expect(rows).toHaveLength(2)
	})

	it('createTables ensureColumn triggers ALTER when column missing', async () => {
		const execute = vi.fn(async () => ({ rowsAffected: 0, lastInsertId: 0 }))
		const select = vi.fn(async (query: string) => {
			if (query.includes('PRAGMA table_info(media_person_link)')) return []
			if (query.includes('PRAGMA table_info(media)')) return []
			if (query.includes('PRAGMA table_info(bookmark)')) return []
			if (query.includes('SELECT COUNT(*) as c FROM proposal')) return [{ c: 0 }]
			return []
		})
		const fakeDb = { select, execute }

		vi.doMock('@tauri-apps/plugin-sql', () => ({
			default: { load: vi.fn(async () => fakeDb) },
		}))

		const mod = await import('$lib/db')
		await mod.getDb()

		const alterCalls = execute.mock.calls.filter((call) =>
			String(call.at(0) ?? '').includes('ALTER TABLE')
		)
		expect(alterCalls.length).toBeGreaterThan(0)
	})

	it('proposal status transitions pending -> approved and pending -> rejected', async () => {
		const execute = vi.fn(async () => ({ rowsAffected: 1, lastInsertId: 1 }))
		const select = vi.fn(async (query: string) => {
			if (query.includes('SELECT * FROM proposal WHERE id = $1')) {
				return [
					{
						id: 1,
						agentRunId: 'run-1',
						entityType: 'person',
						entityId: 'I1',
						fieldName: 'birthDate',
						oldValue: '',
						newValue: '1 JAN 1900',
						confidence: 0.9,
						reasoning: '',
						evidenceSource: '',
						status: 'pending',
						createdAt: '',
						resolvedAt: '',
					},
				]
			}
			return []
		})

		const fakeDb = { select, execute }
		vi.doMock('@tauri-apps/plugin-sql', () => ({ default: { load: vi.fn(async () => fakeDb) } }))

		const mod = await import('$lib/db')
		await mod.getDb()
		const approve = await mod.approveProposal(1)
		expect(approve.success).toBe(true)
		await mod.rejectProposal(1)

		expect(
			execute.mock.calls.some((call) => String(call.at(0) ?? '').includes("status = 'approved'"))
		).toBe(true)
		expect(
			execute.mock.calls.some((call) => String(call.at(0) ?? '').includes("status = 'rejected'"))
		).toBe(true)
	})

	it('classifySources classifies tree/census/church titles', async () => {
		const sources = [
			{ xref: 'S1', title: 'Ancestry Family Tree', publisher: '', sourceType: 'unknown' },
			{ xref: 'S2', title: '1910 Census of Ohio', publisher: '', sourceType: 'unknown' },
			{ xref: 'S3', title: 'Parish Baptism Register', publisher: '', sourceType: 'unknown' },
		]

		const execute = vi.fn(async (query: string, args?: unknown[]) => {
			const lower = query.toLowerCase()
			const apply = (nextType: string, predicate: (s: any) => boolean) => {
				for (const src of sources) {
					if (src.sourceType === 'unknown' && predicate(src)) src.sourceType = nextType
				}
			}

			if (lower.includes("set sourcetype = 'online_tree'")) {
				const raw = String(args?.[0] ?? '')
				const regex = new RegExp(
					`^${raw.replace(/[.*+?^${}()|[\]\\]/g, '\\$&').replace(/%/g, '.*')}$`,
					'i'
				)
				apply('online_tree', (s) => regex.test(s.title) || regex.test(s.publisher))
			} else if (lower.includes("set sourcetype = 'census'")) {
				apply('census', (s) => s.title.toLowerCase().includes('census'))
			} else if (lower.includes("set sourcetype = 'church_record'")) {
				apply('church_record', (s) =>
					['church', 'parish', 'baptis', 'christening'].some((k) =>
						s.title.toLowerCase().includes(k)
					)
				)
			}

			return { rowsAffected: 1, lastInsertId: 0 }
		})

		const select = vi.fn(async (query: string) => {
			if (query.includes('COUNT(*) as c FROM source WHERE sourceType !=')) {
				return [{ c: sources.filter((s) => s.sourceType !== 'unknown').length }]
			}
			return []
		})

		vi.doMock('@tauri-apps/plugin-sql', () => ({
			default: { load: vi.fn(async () => ({ select, execute })) },
		}))

		const mod = await import('$lib/db')
		await mod.getDb()
		await mod.classifySources()

		expect(sources.find((s) => s.xref === 'S1')?.sourceType).toBe('online_tree')
		expect(sources.find((s) => s.xref === 'S2')?.sourceType).toBe('census')
		expect(sources.find((s) => s.xref === 'S3')?.sourceType).toBe('church_record')
	})

	it('computeValidationStatus sets validated/tree_only/unvalidated from citations', async () => {
		const persons = [
			{ xref: 'I1', validationStatus: 'unvalidated' },
			{ xref: 'I2', validationStatus: 'unvalidated' },
			{ xref: 'I3', validationStatus: 'unvalidated' },
		]
		const citations = [
			{ personXref: 'I1', sourceType: 'vital_record' },
			{ personXref: 'I2', sourceType: 'online_tree' },
		]

		const execute = vi.fn(async (query: string) => {
			if (query.includes("UPDATE person SET validationStatus = 'unvalidated'")) {
				for (const p of persons) {
					if (!p.validationStatus) p.validationStatus = 'unvalidated'
				}
			}
			if (query.includes("UPDATE person SET validationStatus = 'validated'")) {
				const validated = new Set(
					citations
						.filter((c) => !['online_tree', 'unknown'].includes(c.sourceType))
						.map((c) => c.personXref)
				)
				for (const p of persons) {
					if (validated.has(p.xref)) p.validationStatus = 'validated'
				}
			}
			if (query.includes("UPDATE person SET validationStatus = 'tree_only'")) {
				const treeOnly = new Set(
					citations.filter((c) => c.sourceType === 'online_tree').map((c) => c.personXref)
				)
				for (const p of persons) {
					if (p.validationStatus === 'unvalidated' && treeOnly.has(p.xref))
						p.validationStatus = 'tree_only'
				}
			}
			return { rowsAffected: 1, lastInsertId: 0 }
		})
		const select = vi.fn(async () => [])

		vi.doMock('@tauri-apps/plugin-sql', () => ({
			default: { load: vi.fn(async () => ({ select, execute })) },
		}))

		const mod = await import('$lib/db')
		await mod.getDb()
		await mod.computeValidationStatus()

		expect(persons.find((p) => p.xref === 'I1')?.validationStatus).toBe('validated')
		expect(persons.find((p) => p.xref === 'I2')?.validationStatus).toBe('tree_only')
		expect(persons.find((p) => p.xref === 'I3')?.validationStatus).toBe('unvalidated')
	})

	it('computeValidationStatus promotes statuses from inserted person/source/citation rows', async () => {
		const persons: Array<{ id: number; xref: string; validationStatus: string }> = []
		const sources: Array<{ id: number; xref: string; sourceType: string }> = []
		const citations: Array<{ sourceXref: string; personXref: string }> = []
		let personId = 1
		let sourceId = 1

		const execute = vi.fn(async (query: string, args?: any[]) => {
			if (query.includes('INSERT OR IGNORE INTO person')) {
				const xref = String(args?.[0] ?? '')
				if (!persons.some((p) => p.xref === xref)) {
					persons.push({
						id: personId++,
						xref,
						validationStatus: String(args?.[14] ?? 'unvalidated'),
					})
				}
			} else if (query.includes('INSERT OR IGNORE INTO source')) {
				const xref = String(args?.[0] ?? '')
				if (!sources.some((s) => s.xref === xref)) {
					sources.push({
						id: sourceId++,
						xref,
						sourceType: String(args?.[4] ?? 'unknown'),
					})
				}
			} else if (query.includes('INSERT INTO citation')) {
				citations.push({
					sourceXref: String(args?.[0] ?? ''),
					personXref: String(args?.[1] ?? ''),
				})
			} else if (query.includes("UPDATE person SET validationStatus = 'unvalidated'")) {
				for (const p of persons) {
					if (!p.validationStatus) p.validationStatus = 'unvalidated'
				}
			} else if (query.includes("UPDATE person SET validationStatus = 'validated'")) {
				const sourceTypeByXref = new Map(sources.map((s) => [s.xref, s.sourceType]))
				const validated = new Set(
					citations
						.filter((c) => {
							const st = sourceTypeByXref.get(c.sourceXref) || 'unknown'
							return st !== 'online_tree' && st !== 'unknown'
						})
						.map((c) => c.personXref)
				)
				for (const p of persons) {
					if (validated.has(p.xref)) p.validationStatus = 'validated'
				}
			} else if (query.includes("UPDATE person SET validationStatus = 'tree_only'")) {
				const sourceTypeByXref = new Map(sources.map((s) => [s.xref, s.sourceType]))
				const treeOnly = new Set(
					citations
						.filter((c) => (sourceTypeByXref.get(c.sourceXref) || 'unknown') === 'online_tree')
						.map((c) => c.personXref)
				)
				for (const p of persons) {
					if (p.validationStatus === 'unvalidated' && treeOnly.has(p.xref)) {
						p.validationStatus = 'tree_only'
					}
				}
			}
			return { rowsAffected: 1, lastInsertId: 1 }
		})

		const select = vi.fn(async (query: string, args?: any[]) => {
			if (query.includes('PRAGMA table_info(')) return []
			if (query.includes('SELECT * FROM person WHERE xref = $1')) {
				const xref = String(args?.[0] ?? '')
				const p = persons.find((row) => row.xref === xref)
				return p ? [{ ...p }] : []
			}
			return []
		})

		vi.doMock('@tauri-apps/plugin-sql', () => ({
			default: { load: vi.fn(async () => ({ select, execute })) },
		}))

		const mod = await import('$lib/db')
		await mod.getDb()

		await mod.insertPerson({
			xref: 'I_VALID',
			givenName: 'Val',
			surname: 'Person',
			suffix: '',
			sex: 'U',
			isLiving: false,
			birthDate: '',
			birthPlace: '',
			deathDate: '',
			deathPlace: '',
			sourceCount: 1,
			mediaCount: 0,
			personColor: '',
			proofStatus: 'UNKNOWN',
			validationStatus: 'unvalidated',
		})
		await mod.insertPerson({
			xref: 'I_TREE',
			givenName: 'Tree',
			surname: 'Only',
			suffix: '',
			sex: 'U',
			isLiving: false,
			birthDate: '',
			birthPlace: '',
			deathDate: '',
			deathPlace: '',
			sourceCount: 1,
			mediaCount: 0,
			personColor: '',
			proofStatus: 'UNKNOWN',
			validationStatus: 'unvalidated',
		})
		await mod.insertPerson({
			xref: 'I_NONE',
			givenName: 'No',
			surname: 'Citation',
			suffix: '',
			sex: 'U',
			isLiving: false,
			birthDate: '',
			birthPlace: '',
			deathDate: '',
			deathPlace: '',
			sourceCount: 0,
			mediaCount: 0,
			personColor: '',
			proofStatus: 'UNKNOWN',
			validationStatus: 'unvalidated',
		})

		await mod.insertSource({
			xref: 'S_VITAL',
			title: 'Birth Record',
			author: '',
			publisher: '',
			sourceType: 'vital_record',
		})
		await mod.insertSource({
			xref: 'S_TREE',
			title: 'Ancestry Family Tree',
			author: '',
			publisher: '',
			sourceType: 'online_tree',
		})

		const db = await mod.getDb()
		await db.execute(
			`INSERT INTO citation (sourceXref, personXref, eventId, page, quality, text, note) VALUES ($1,$2,$3,$4,$5,$6,$7)`,
			['S_VITAL', 'I_VALID', '', 'p.1', 'PRIMARY', '', '']
		)
		await db.execute(
			`INSERT INTO citation (sourceXref, personXref, eventId, page, quality, text, note) VALUES ($1,$2,$3,$4,$5,$6,$7)`,
			['S_TREE', 'I_TREE', '', 'p.2', 'SECONDARY', '', '']
		)

		await mod.computeValidationStatus()

		expect((await mod.getPerson('I_VALID'))?.validationStatus).toBe('validated')
		expect((await mod.getPerson('I_TREE'))?.validationStatus).toBe('tree_only')
		expect((await mod.getPerson('I_NONE'))?.validationStatus).toBe('unvalidated')
	})

	it('getMediaForManagement uses INNER JOIN when linkedOnly=true and LEFT JOIN when linkedOnly=false', async () => {
		const select = vi.fn(async () => [])
		const execute = vi.fn(async () => ({ rowsAffected: 0, lastInsertId: 0 }))
		vi.doMock('@tauri-apps/plugin-sql', () => ({
			default: { load: vi.fn(async () => ({ select, execute })) },
		}))

		const mod = await import('$lib/db')
		await mod.getDb()
		await mod.getMediaForManagement({ linkedOnly: true, imagesOnly: false })
		await mod.getMediaForManagement({ linkedOnly: false, imagesOnly: true })

		const queries = select.mock.calls.map((call) => String(call.at(0) ?? ''))
		expect(
			queries.some((q) => q.includes('INNER JOIN media_person_link mpl ON mpl.mediaId = m.id'))
		).toBe(true)
		expect(
			queries.some((q) => q.includes('LEFT JOIN media_person_link mpl ON mpl.mediaId = m.id'))
		).toBe(true)
	})

	it('autoLinkOrphanMedia links orphan media to person via filename xref token', async () => {
		const persons = [{ xref: 'I0001' }]
		const orphanMedia = [{ id: 7, filePath: 'Smith_John_I0001_photo.jpg' }]
		const links: Array<{ mediaId: number; personXref: string }> = []

		const select = vi.fn(async (query: string, args?: any[]) => {
			if (query.includes('PRAGMA table_info(')) return []
			if (query.includes('FROM media m') && query.includes('LEFT JOIN media_person_link mpl')) {
				return orphanMedia.filter((m) => !links.some((l) => l.mediaId === m.id))
			}
			if (query.includes('SELECT xref FROM person WHERE xref = $1 LIMIT 1')) {
				const xref = String(args?.[0] ?? '')
				return persons.filter((p) => p.xref === xref)
			}
			return []
		})
		const execute = vi.fn(async (query: string, args?: any[]) => {
			if (query.includes('INSERT OR IGNORE INTO media_person_link')) {
				const mediaId = Number(args?.[0] ?? 0)
				const personXref = String(args?.[1] ?? '')
				if (!links.some((l) => l.mediaId === mediaId && l.personXref === personXref)) {
					links.push({ mediaId, personXref })
					return { rowsAffected: 1, lastInsertId: 1 }
				}
				return { rowsAffected: 0, lastInsertId: 0 }
			}
			return { rowsAffected: 0, lastInsertId: 0 }
		})

		vi.doMock('@tauri-apps/plugin-sql', () => ({
			default: { load: vi.fn(async () => ({ select, execute })) },
		}))

		const mod = await import('$lib/db')
		await mod.getDb()
		const result = await mod.autoLinkOrphanMedia()

		expect(result).toEqual({ linked: 1, skipped: 0 })
		expect(links).toEqual([{ mediaId: 7, personXref: 'I0001' }])
	})

	it('insertPerson skips duplicate xref rows', async () => {
		const persons: Array<{ xref: string }> = []
		const execute = vi.fn(async (query: string, args?: any[]) => {
			if (query.includes('INSERT OR IGNORE INTO person')) {
				const xref = String(args?.[0] ?? '')
				if (!persons.some((p) => p.xref === xref)) persons.push({ xref })
			}
			return { rowsAffected: 1, lastInsertId: 1 }
		})
		const select = vi.fn(async (query: string) => {
			if (query.includes('PRAGMA table_info(')) return []
			return []
		})

		vi.doMock('@tauri-apps/plugin-sql', () => ({
			default: { load: vi.fn(async () => ({ select, execute })) },
		}))

		const mod = await import('$lib/db')
		await mod.getDb()

		const payload = {
			xref: 'I_DUP',
			givenName: 'Dup',
			surname: 'Person',
			suffix: '',
			sex: 'U',
			isLiving: false,
			birthDate: '',
			birthPlace: '',
			deathDate: '',
			deathPlace: '',
			sourceCount: 0,
			mediaCount: 0,
			personColor: '',
			proofStatus: 'UNKNOWN' as const,
			validationStatus: 'unvalidated' as const,
		}
		await mod.insertPerson(payload)
		await mod.insertPerson(payload)

		expect(persons).toHaveLength(1)
		expect(persons[0]?.xref).toBe('I_DUP')
	})

	it('computeValidationStatus does not crash on empty DB', async () => {
		const execute = vi.fn(async () => ({ rowsAffected: 0, lastInsertId: 0 }))
		const select = vi.fn(async (query: string) => {
			if (query.includes('PRAGMA table_info(')) return []
			return []
		})
		vi.doMock('@tauri-apps/plugin-sql', () => ({
			default: { load: vi.fn(async () => ({ select, execute })) },
		}))

		const mod = await import('$lib/db')
		await mod.getDb()
		await expect(mod.computeValidationStatus()).resolves.toBeUndefined()
	})

	it('batchDeletePersons with empty input returns without touching DB', async () => {
		const load = vi.fn(async () => ({
			select: vi.fn(async () => []),
			execute: vi.fn(async () => ({ rowsAffected: 0, lastInsertId: 0 })),
		}))
		vi.doMock('@tauri-apps/plugin-sql', () => ({ default: { load } }))

		const mod = await import('$lib/db')
		await expect(mod.batchDeletePersons([])).resolves.toBeUndefined()
		expect(load).not.toHaveBeenCalled()
	})

	it('getPersons handles SQL-special search terms safely', async () => {
		const select = vi.fn(async (query: string, args?: unknown[]) => {
			if (query.includes('JOIN person_fts')) {
				expect(Array.isArray(args)).toBe(true)
				expect(String(args?.[0] ?? '')).toContain('"%')
				return []
			}
			if (query.includes('JOIN alternate_name')) {
				expect(args).toEqual([`%%_' OR 1=1 --%`, 10])
				return []
			}
			return []
		})
		const execute = vi.fn(async () => ({ rowsAffected: 0, lastInsertId: 0 }))

		vi.doMock('@tauri-apps/plugin-sql', () => ({
			default: { load: vi.fn(async () => ({ select, execute })) },
		}))

		const mod = await import('$lib/db')
		await mod.getDb()
		await expect(mod.getPersons(`%_' OR 1=1 --`, 10)).resolves.toEqual([])
	})

	it('getPersons returns partial-name matches via FTS', async () => {
		const select = vi.fn(async (query: string, args?: unknown[]) => {
			if (query.includes('SELECT COUNT(*) as c FROM person_fts')) {
				return [{ c: 2 }]
			}
			if (query.includes('JOIN person_fts')) {
				expect(args).toEqual(['"Jo"*', 10])
				return [
					{
						id: 1,
						xref: 'I1',
						givenName: 'John',
						surname: 'Doe',
						suffix: '',
						sex: 'M',
						isLiving: 0,
						birthDate: '',
						birthPlace: '',
						deathDate: '',
						deathPlace: '',
						sourceCount: 0,
						mediaCount: 0,
						personColor: '',
						proofStatus: 'UNKNOWN',
						validationStatus: 'unvalidated',
					},
				]
			}
			if (query.includes('JOIN alternate_name')) return []
			return []
		})
		const execute = vi.fn(async () => ({ rowsAffected: 0, lastInsertId: 0 }))

		vi.doMock('@tauri-apps/plugin-sql', () => ({
			default: { load: vi.fn(async () => ({ select, execute })) },
		}))

		const mod = await import('$lib/db')
		await mod.getDb()
		await expect(mod.getPersons('Jo', 10)).resolves.toMatchObject([
			{ xref: 'I1', givenName: 'John' },
		])
	})
})
