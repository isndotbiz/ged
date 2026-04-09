import { beforeEach, describe, expect, it, vi } from 'vitest';

vi.mock('$lib/db', () => ({
  getAllPersons: vi.fn(),
  getFamilies: vi.fn(),
  getEvents: vi.fn(),
  getNotes: vi.fn(),
  getChildren: vi.fn(),
  getDb: vi.fn(async () => ({ select: vi.fn(async () => []), execute: vi.fn(async () => ({ rowsAffected: 0, lastInsertId: 0 })) })),
  clearAll: vi.fn(async () => {}),
  insertPerson: vi.fn(async () => {}),
  insertFamily: vi.fn(async () => {}),
  insertEvent: vi.fn(async () => {}),
  insertSource: vi.fn(async () => {}),
  insertMedia: vi.fn(async () => {}),
  insertChildLink: vi.fn(async () => {}),
  rebuildFTS: vi.fn(async () => {}),
  classifySources: vi.fn(async () => 0),
  computeValidationStatus: vi.fn(async () => {}),
  autoCategorizeMediaAfterDedup: vi.fn(async () => ({ updated: 0 })),
  autoLinkOrphanMedia: vi.fn(async () => ({ linked: 0, skipped: 0 })),
}));

import {
  getAllPersons,
  getFamilies,
  getEvents,
  getNotes,
  getChildren,
  getDb,
  clearAll,
  insertPerson,
  insertFamily,
  insertEvent,
  insertChildLink,
} from '$lib/db';
import { exportGedcom } from '$lib/gedcom-exporter';
import { importGedcom } from '$lib/gedcom-parser';

describe('gedcom-exporter', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(getAllPersons).mockResolvedValue([]);
    vi.mocked(getFamilies).mockResolvedValue([]);
    vi.mocked(getEvents).mockResolvedValue([]);
    vi.mocked(getNotes).mockResolvedValue([]);
    vi.mocked(getChildren).mockResolvedValue([]);
  });

  it('empty DB exports valid HEAD and TRLR only', async () => {
    const out = await exportGedcom();
    expect(out.startsWith('0 HEAD\n')).toBe(true);
    expect(out.includes('\n0 TRLR\n')).toBe(true);
    expect(out.includes(' INDI\n')).toBe(false);
    expect(out.includes(' FAM\n')).toBe(false);
  });

  it('one person exports INDI with GEDCOM level tags', async () => {
    vi.mocked(getAllPersons).mockResolvedValue([
      {
        id: 1,
        xref: 'I1',
        givenName: 'John',
        surname: 'Doe',
        suffix: '',
        sex: 'M',
        isLiving: 0 as any,
        birthDate: '1923-07-14',
        birthPlace: 'Boston',
        deathDate: '',
        deathPlace: '',
        sourceCount: 0,
        mediaCount: 0,
        personColor: '',
        proofStatus: 'UNKNOWN',
        validationStatus: 'unvalidated',
      },
    ]);
    vi.mocked(getNotes).mockResolvedValue([
      {
        id: 1,
        ownerXref: 'I1',
        ownerType: 'INDI',
        title: 'Research Note',
        content: 'Line one',
        createdAt: '',
        updatedAt: '',
      },
    ]);

    const out = await exportGedcom();
    expect(out).toContain('0 @I1@ INDI');
    expect(out).toContain('1 NAME John /Doe/');
    expect(out).toContain('1 SEX M');
    expect(out).toContain('1 BIRT');
    expect(out).toContain('2 DATE 14 JUL 1923');
    expect(out).toContain('2 PLAC Boston');
    expect(out).toContain('1 NOTE Research Note');
  });

  it('one family exports FAM with HUSB and WIFE', async () => {
    vi.mocked(getFamilies).mockResolvedValue([
      {
        id: 1,
        xref: 'F1',
        partner1Xref: 'I1',
        partner2Xref: 'I2',
        marriageDate: '',
        marriagePlace: '',
      },
    ]);
    vi.mocked(getChildren).mockResolvedValue([{ xref: 'I3' } as any]);

    const out = await exportGedcom();
    expect(out).toContain('0 @F1@ FAM');
    expect(out).toContain('1 HUSB @I1@');
    expect(out).toContain('1 WIFE @I2@');
    expect(out).toContain('1 CHIL @I3@');
  });

  it('round-trips 3-person family and preserves birth dates', async () => {
    type StoredPerson = {
      id: number;
      xref: string;
      givenName: string;
      surname: string;
      suffix: string;
      sex: string;
      isLiving: number;
      birthDate: string;
      birthPlace: string;
      deathDate: string;
      deathPlace: string;
      sourceCount: number;
      mediaCount: number;
      personColor: string;
      proofStatus: string;
      validationStatus: string;
    };
    type StoredFamily = {
      id: number;
      xref: string;
      partner1Xref: string;
      partner2Xref: string;
      marriageDate: string;
      marriagePlace: string;
    };
    type StoredEvent = { ownerXref: string; eventType: string; dateValue: string; place: string; description: string };
    type StoredChild = { familyXref: string; childXref: string; childOrder: number };

    const persons: StoredPerson[] = [];
    const families: StoredFamily[] = [];
    const events: StoredEvent[] = [];
    const childLinks: StoredChild[] = [];

    vi.mocked(clearAll).mockImplementation(async () => {
      persons.length = 0;
      families.length = 0;
      events.length = 0;
      childLinks.length = 0;
    });
    vi.mocked(insertPerson).mockImplementation(async (p: any) => {
      persons.push({ id: persons.length + 1, ...p });
    });
    vi.mocked(insertFamily).mockImplementation(async (f: any) => {
      families.push({ id: families.length + 1, ...f });
    });
    vi.mocked(insertEvent).mockImplementation(async (e: any) => {
      events.push(e);
    });
    vi.mocked(insertChildLink).mockImplementation(async (c: any) => {
      childLinks.push(c);
    });
    vi.mocked(getAllPersons).mockImplementation(async () => persons as any);
    vi.mocked(getFamilies).mockImplementation(async () => families as any);
    vi.mocked(getEvents).mockImplementation(async (ownerXref: string) => events.filter((e) => e.ownerXref === ownerXref) as any);
    vi.mocked(getChildren).mockImplementation(async (familyXref: string) => {
      return childLinks
        .filter((c) => c.familyXref === familyXref)
        .sort((a, b) => a.childOrder - b.childOrder)
        .map((c) => ({ xref: c.childXref })) as any;
    });
    vi.mocked(getNotes).mockResolvedValue([]);
    vi.mocked(getDb).mockResolvedValue({
      select: async (query: string) => {
        if (query.includes('SELECT COUNT(*) as c FROM person')) return [{ c: persons.length }];
        if (query.includes('SELECT xref, id FROM media WHERE xref !=')) return [];
        if (query.includes('SELECT id, xref FROM media WHERE LOWER(filePath)')) return [];
        if (query.includes('SELECT id FROM media WHERE LOWER(filePath)')) return [];
        if (query.includes('SELECT id FROM media WHERE xref = $1')) return [];
        return [];
      },
      execute: async () => ({ rowsAffected: 1, lastInsertId: 1 }),
    } as any);

    const inputGedcom = `0 HEAD
1 GEDC
2 VERS 5.5.1
0 @I1@ INDI
1 NAME John /Parent/
1 SEX M
1 BIRT
2 DATE 1 JAN 1970
1 DEAT
2 DATE 1 JAN 2020
0 @I2@ INDI
1 NAME Jane /Parent/
1 SEX F
1 BIRT
2 DATE 2 FEB 1972
1 DEAT
2 DATE 2 FEB 2022
0 @I3@ INDI
1 NAME Chris /Child/
1 SEX M
1 BIRT
2 DATE 3 MAR 2000
1 DEAT
2 DATE 3 MAR 2070
0 @F1@ FAM
1 HUSB @I1@
1 WIFE @I2@
1 CHIL @I3@
0 TRLR`;

    await importGedcom(inputGedcom);
    const exported = await exportGedcom();
    await clearAll();
    await importGedcom(exported, undefined, { force: true });

    expect(persons).toHaveLength(3);
    expect(families).toHaveLength(1);
    expect(Object.fromEntries(persons.map((p) => [p.xref, p.birthDate]))).toEqual({
      I1: '1 JAN 1970',
      I2: '2 FEB 1972',
      I3: '3 MAR 2000',
    });
  });
});
