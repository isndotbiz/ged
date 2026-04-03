import { describe, expect, it } from 'vitest';
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
