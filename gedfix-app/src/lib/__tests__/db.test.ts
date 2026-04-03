import { beforeEach, describe, expect, it, vi } from 'vitest';

vi.mock('$lib/platform', () => ({ isTauri: () => true }));

describe('db.ts helpers', () => {
  beforeEach(() => {
    vi.resetModules();
  });

  it('parseDateForSort handles common formats', async () => {
    const mod = await import('$lib/db');
    expect(mod.parseDateForSort('1 Jan 1900')).toBe(1900);
    expect(mod.parseDateForSort('Abt 1850')).toBe(1850);
    expect(mod.parseDateForSort('Bet 1800 and 1810')).toBe(1800);
    expect(mod.parseDateForSort('')).toBe(0);
    expect(mod.parseDateForSort(null)).toBe(0);
  });

  it('SAFE_FIELDS allows whitelisted fields only', async () => {
    const mod = await import('$lib/db');
    expect(mod.SAFE_FIELDS.person.has('birthDate')).toBe(true);
    expect(mod.SAFE_FIELDS.person.has('id')).toBe(false);
    expect(mod.SAFE_FIELDS.family.has('partner1Xref')).toBe(true);
  });

  it('getPersons handles empty search and limit', async () => {
    const fakeDb = {
      select: vi.fn(async (query: string, args?: unknown[]) => {
        if (query.includes('SELECT * FROM person ORDER BY surname')) {
          expect(args).toEqual([2]);
          return [{ xref: 'I1' }, { xref: 'I2' }];
        }
        return [];
      }),
      execute: vi.fn(async () => ({ rowsAffected: 0, lastInsertId: 0 })),
    };

    vi.doMock('@tauri-apps/plugin-sql', () => ({
      default: { load: vi.fn(async () => fakeDb) },
    }));

    const mod = await import('$lib/db');
    const rows = await mod.getPersons('', 2);
    expect(rows).toHaveLength(2);
  });

  it('createTables ensureColumn triggers ALTER when column missing', async () => {
    const execute = vi.fn(async () => ({ rowsAffected: 0, lastInsertId: 0 }));
    const select = vi.fn(async (query: string) => {
      if (query.includes('PRAGMA table_info(media_person_link)')) return [];
      if (query.includes('PRAGMA table_info(media)')) return [];
      if (query.includes('PRAGMA table_info(bookmark)')) return [];
      if (query.includes('SELECT COUNT(*) as c FROM proposal')) return [{ c: 0 }];
      return [];
    });
    const fakeDb = { select, execute };

    vi.doMock('@tauri-apps/plugin-sql', () => ({
      default: { load: vi.fn(async () => fakeDb) },
    }));

    const mod = await import('$lib/db');
    await mod.getDb();

    const alterCalls = execute.mock.calls.filter((call) => String(call.at(0) ?? '').includes('ALTER TABLE'));
    expect(alterCalls.length).toBeGreaterThan(0);
  });

  it('proposal status transitions pending -> approved and pending -> rejected', async () => {
    const execute = vi.fn(async () => ({ rowsAffected: 1, lastInsertId: 1 }));
    const select = vi.fn(async (query: string) => {
      if (query.includes('SELECT * FROM proposal WHERE id = $1')) {
        return [{
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
        }];
      }
      return [];
    });

    const fakeDb = { select, execute };
    vi.doMock('@tauri-apps/plugin-sql', () => ({ default: { load: vi.fn(async () => fakeDb) } }));

    const mod = await import('$lib/db');
    await mod.getDb();
    const approve = await mod.approveProposal(1);
    expect(approve.success).toBe(true);
    await mod.rejectProposal(1);

    expect(execute.mock.calls.some((call) => String(call.at(0) ?? '').includes("status = 'approved'"))).toBe(true);
    expect(execute.mock.calls.some((call) => String(call.at(0) ?? '').includes("status = 'rejected'"))).toBe(true);
  });
});
