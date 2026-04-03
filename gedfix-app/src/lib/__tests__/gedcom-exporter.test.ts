import { beforeEach, describe, expect, it, vi } from 'vitest';

vi.mock('$lib/db', () => ({ getDb: vi.fn() }));

import { getDb } from '$lib/db';
import { exportGedcom } from '$lib/gedcom-exporter';

describe('gedcom-exporter', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('exports single person and family in 5.5.1 and 7.0 headers', async () => {
    vi.mocked(getDb).mockResolvedValue({
      select: vi.fn(async (query: string) => {
        if (query.includes('FROM person')) return [{ xref: 'I1', givenName: 'John', surname: 'Doe', suffix: '', sex: 'M', birthDate: '1 JAN 1900', birthPlace: '', deathDate: '', deathPlace: '' }];
        if (query.includes('FROM event')) return [];
        if (query.includes('FROM media m JOIN media_person_link')) return [];
        if (query.includes('FROM child_link')) return [{ childXref: 'I1', familyXref: 'F1', childOrder: 0 }];
        if (query.includes('FROM family')) return [{ xref: 'F1', partner1Xref: 'I1', partner2Xref: '', marriageDate: '', marriagePlace: '' }];
        if (query.includes('FROM source')) return [];
        if (query.includes('FROM media WHERE')) return [];
        return [];
      }),
      execute: vi.fn(),
    } as any);

    const out551 = await exportGedcom('5.5.1');
    expect(out551).toContain('2 VERS 5.5.1');
    expect(out551).toContain('0 @I1@ INDI');
    expect(out551).toContain('0 @F1@ FAM');

    const out7 = await exportGedcom('7.0');
    expect(out7).toContain('2 VERS 7.0');
  });
});
