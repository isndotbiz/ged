import { describe, expect, it, vi } from 'vitest';

vi.mock('$lib/db', () => ({
  getAllPersons: vi.fn(),
  getFamilies: vi.fn(),
  getDb: vi.fn(),
}));

import { analyzeTree, __testables } from '$lib/tree-analyzer';
import { getAllPersons, getFamilies, getDb } from '$lib/db';

describe('tree-analyzer', () => {
  it('parseYear handles empty and year strings', () => {
    expect(__testables.parseYear('1 JAN 1900')).toBe(1900);
    expect(__testables.parseYear('')).toBeNull();
  });

  it('detects death before birth and returns empty for clean data', async () => {
    vi.mocked(getDb).mockResolvedValue({ select: vi.fn(async () => []) } as any);
    vi.mocked(getFamilies).mockResolvedValue([] as any);

    vi.mocked(getAllPersons).mockResolvedValue([
      { xref: 'I1', givenName: 'A', surname: 'B', birthDate: '1900', deathDate: '1890', sourceCount: 1 },
    ] as any);
    const issues = await analyzeTree();
    expect(issues.some((i) => i.title === 'Death before birth')).toBe(true);

    vi.mocked(getAllPersons).mockResolvedValue([
      { xref: 'I2', givenName: 'C', surname: 'D', birthDate: '1900', deathDate: '1980', sourceCount: 1 },
    ] as any);
    const clean = await analyzeTree();
    expect(clean.some((i) => i.title === 'Death before birth')).toBe(false);
  });
});
