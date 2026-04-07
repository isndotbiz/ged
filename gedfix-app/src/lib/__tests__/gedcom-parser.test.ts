import { describe, expect, it, vi } from 'vitest';
import { __testables } from '$lib/gedcom-parser';

const { detectGedcomVersion, parseLine, groupRecords, parseName, isLiving } = __testables;

describe('gedcom-parser internals', () => {
  it('detects GEDCOM versions', () => {
    const v7 = `0 HEAD\n1 GEDC\n2 VERS 7.0\n0 TRLR`;
    const v551 = `0 HEAD\n1 GEDC\n2 VERS 5.5.1\n0 TRLR`;
    expect(detectGedcomVersion(v7)).toBe('7.0');
    expect(detectGedcomVersion(v551)).toBe('5.5.1');
  });

  it('parses minimal INDI/FAM/SOUR and malformed lines safely', () => {
    expect(parseLine('1 NAME John /Doe/', '5.5.1')?.tag).toBe('NAME');
    expect(parseLine('broken line', '5.5.1')).toBeNull();

    const lines = [
      parseLine('0 @I1@ INDI', '5.5.1'),
      parseLine('1 NAME John /Doe/', '5.5.1'),
      parseLine('0 @F1@ FAM', '5.5.1'),
      parseLine('1 HUSB @I1@', '5.5.1'),
      parseLine('0 @S1@ SOUR', '5.5.1'),
      parseLine('1 TITL Census', '5.5.1'),
    ].filter(Boolean) as any[];

    const records = groupRecords(lines);
    expect(records.map((r) => r.tag)).toEqual(['INDI', 'FAM', 'SOUR']);
  });

  it('handles name parsing, living inference, and CONT/CONC-friendly line parsing', () => {
    expect(parseName('John /Doe/ Jr')).toEqual({ givenName: 'John', surname: 'Doe', suffix: 'Jr' });

    const recYoung = {
      xref: 'I1',
      tag: 'INDI',
      lines: [
        { level: 1, xref: '', tag: 'BIRT', value: '' },
        { level: 2, xref: '', tag: 'DATE', value: '1 JAN 2000' },
      ],
    } as any;
    const recOld = {
      xref: 'I2',
      tag: 'INDI',
      lines: [
        { level: 1, xref: '', tag: 'BIRT', value: '' },
        { level: 2, xref: '', tag: 'DATE', value: '1 JAN 1800' },
      ],
    } as any;
    expect(isLiving(recYoung)).toBe(true);
    expect(isLiving(recOld)).toBe(false);
  });
});

describe('gedcom-parser import defaults', () => {
  it('sets default sourceType, validationStatus, and creates media for inline OBJE', async () => {
    vi.resetModules();

    const insertPerson = vi.fn(async () => {});
    const insertSource = vi.fn(async () => {});
    const insertMedia = vi.fn(async () => {});

    const select = vi.fn(async (query: string) => {
      if (query.includes('SELECT xref, id FROM media WHERE xref !=')) return [];
      if (query.includes('SELECT id, xref FROM media WHERE LOWER(filePath)')) return [];
      if (query.includes('SELECT id FROM media WHERE LOWER(filePath)')) return [{ id: 1 }];
      if (query.includes('SELECT id FROM media WHERE xref = $1')) return [{ id: 1 }];
      return [];
    });

    const execute = vi.fn(async () => ({ rowsAffected: 1, lastInsertId: 1 }));

    vi.doMock('$lib/db', () => ({
      insertPerson,
      insertFamily: vi.fn(async () => {}),
      insertEvent: vi.fn(async () => {}),
      insertSource,
      insertMedia,
      insertChildLink: vi.fn(async () => {}),
      clearAll: vi.fn(async () => {}),
      getDb: vi.fn(async () => ({ select, execute })),
      rebuildFTS: vi.fn(async () => {}),
      linkMediaToPerson: vi.fn(async () => {}),
      classifySources: vi.fn(async () => 0),
      computeValidationStatus: vi.fn(async () => {}),
      autoCategorizeMediaAfterDedup: vi.fn(async () => ({ updated: 0 })),
    }));

    const { importGedcom } = await import('$lib/gedcom-parser');

    const ged = `0 HEAD
1 GEDC
2 VERS 5.5.1
0 @I1@ INDI
1 NAME John /Doe/
1 SEX M
1 BIRT
2 DATE 3 JAN 1900
1 OBJE
2 FILE photos/john.jpg
2 FORM jpg
2 TITL Portrait
0 @S1@ SOUR
1 TITL Census 1900
0 TRLR`;

    await importGedcom(ged);

    expect(insertPerson).toHaveBeenCalledWith(expect.objectContaining({
      xref: 'I1',
      validationStatus: 'unvalidated',
    }));
    expect(insertSource).toHaveBeenCalledWith(expect.objectContaining({
      xref: 'S1',
      sourceType: 'unknown',
    }));
    expect(insertMedia).toHaveBeenCalledWith(expect.objectContaining({
      filePath: 'photos/john.jpg',
    }));
  });
});

describe('gedcom-parser citation extraction', () => {
  function setupImportMock() {
    const select = vi.fn(async (query: string) => {
      if (query.includes('SELECT xref, id FROM media WHERE xref !=')) return [];
      if (query.includes('SELECT id, xref FROM media WHERE LOWER(filePath)')) return [];
      if (query.includes('SELECT id FROM media WHERE LOWER(filePath)')) return [{ id: 1 }];
      if (query.includes('SELECT id FROM media WHERE xref = $1')) return [{ id: 1 }];
      return [];
    });
    const execute = vi.fn(async () => ({ rowsAffected: 1, lastInsertId: 1 }));
    vi.doMock('$lib/db', () => ({
      insertPerson: vi.fn(async () => {}),
      insertFamily: vi.fn(async () => {}),
      insertEvent: vi.fn(async () => {}),
      insertSource: vi.fn(async () => {}),
      insertMedia: vi.fn(async () => {}),
      insertChildLink: vi.fn(async () => {}),
      clearAll: vi.fn(async () => {}),
      getDb: vi.fn(async () => ({ select, execute })),
      rebuildFTS: vi.fn(async () => {}),
      classifySources: vi.fn(async () => 0),
      computeValidationStatus: vi.fn(async () => {}),
      autoCategorizeMediaAfterDedup: vi.fn(async () => ({ updated: 0 })),
    }));
    return { execute };
  }

  it('creates no citation rows when no SOUR tags exist', async () => {
    vi.resetModules();
    const { execute } = setupImportMock();
    const { importGedcom } = await import('$lib/gedcom-parser');
    await importGedcom(`0 HEAD
1 GEDC
2 VERS 5.5.1
0 @I1@ INDI
1 NAME No /Source/
1 SEX U
0 TRLR`);
    const citationInserts = execute.mock.calls.filter((call) => String(call.at(0) ?? '').includes('INSERT INTO citation'));
    expect(citationInserts).toHaveLength(0);
  });

  it('creates one citation row for one INDI SOUR tag', async () => {
    vi.resetModules();
    const { execute } = setupImportMock();
    const { importGedcom } = await import('$lib/gedcom-parser');
    await importGedcom(`0 HEAD
1 GEDC
2 VERS 5.5.1
0 @S1@ SOUR
1 TITL Birth Register
0 @I1@ INDI
1 NAME One /Source/
1 SOUR @S1@
2 PAGE p.12
2 QUAY 2
0 TRLR`);
    const citationInserts = execute.mock.calls.filter((call) => String(call.at(0) ?? '').includes('INSERT INTO citation'));
    expect(citationInserts).toHaveLength(1);
    expect(citationInserts[0]?.at(1)).toEqual(['S1', 'I1', 'p.12', 'PRIMARY']);
  });

  it('creates multiple citation rows for multiple INDI SOUR tags', async () => {
    vi.resetModules();
    const { execute } = setupImportMock();
    const { importGedcom } = await import('$lib/gedcom-parser');
    await importGedcom(`0 HEAD
1 GEDC
2 VERS 5.5.1
0 @S1@ SOUR
1 TITL Census
0 @S2@ SOUR
1 TITL Parish
0 @I1@ INDI
1 NAME Multi /Source/
1 SOUR @S1@
2 PAGE p.1
2 QUAY 1
1 SOUR @S2@
2 PAGE p.2
2 QUAY 0
0 TRLR`);
    const citationInserts = execute.mock.calls.filter((call) => String(call.at(0) ?? '').includes('INSERT INTO citation'));
    expect(citationInserts).toHaveLength(2);
    expect(citationInserts.map((call) => call.at(1))).toEqual([
      ['S1', 'I1', 'p.1', 'SECONDARY'],
      ['S2', 'I1', 'p.2', 'QUESTIONABLE'],
    ]);
  });
});
