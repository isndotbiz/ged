import { beforeEach, describe, expect, it, vi } from 'vitest';

vi.mock('$lib/db', () => ({
  getChildren: vi.fn(),
  getParents: vi.fn(),
  getPerson: vi.fn(),
  getSpouseFamilies: vi.fn(),
}));

import { findRelationshipPath, summarizeRelationship } from '$lib/relationship-finder';
import { getChildren, getParents, getPerson, getSpouseFamilies } from '$lib/db';

describe('relationship-finder', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('returns null for unconnected persons', async () => {
    vi.mocked(getParents).mockResolvedValue({ father: null, mother: null } as any);
    vi.mocked(getSpouseFamilies).mockResolvedValue([] as any);
    vi.mocked(getChildren).mockResolvedValue([] as any);
    vi.mocked(getPerson).mockImplementation(async (xref: string) => ({ xref, givenName: xref, surname: '' } as any));

    const path = await findRelationshipPath('I1', 'I2');
    expect(path).toBeNull();
  });

  it('finds direct parent-child relationship', async () => {
    vi.mocked(getParents).mockImplementation(async (xref: string) => {
      if (xref === 'C1') return { father: { xref: 'P1' }, mother: null } as any;
      return { father: null, mother: null } as any;
    });
    vi.mocked(getSpouseFamilies).mockResolvedValue([] as any);
    vi.mocked(getChildren).mockResolvedValue([] as any);
    vi.mocked(getPerson).mockImplementation(async (xref: string) => ({ xref, givenName: xref, surname: '' } as any));

    const path = await findRelationshipPath('C1', 'P1');
    expect(path?.map((s) => s.relationship)).toEqual(['self', 'parent']);
  });

  it('returns a clear same-person path and summary', async () => {
    vi.mocked(getPerson).mockResolvedValue({ xref: 'I1', givenName: 'Ada', surname: 'Lovelace' } as any);

    const path = await findRelationshipPath('I1', 'I1');
    expect(path).toEqual([
      { personXref: 'I1', personName: 'Ada Lovelace', relationship: 'self' },
    ]);
    expect(summarizeRelationship(path)).toBe('Same person');
  });

  it('does not infinite-loop on circular data', async () => {
    vi.mocked(getParents).mockResolvedValue({ father: null, mother: null } as any);
    vi.mocked(getSpouseFamilies).mockImplementation(async (xref: string) => {
      if (xref === 'A') return [{ xref: 'F1', partner1Xref: 'A', partner2Xref: 'B' }] as any;
      if (xref === 'B') return [{ xref: 'F1', partner1Xref: 'A', partner2Xref: 'B' }] as any;
      return [] as any;
    });
    vi.mocked(getChildren).mockImplementation(async (familyXref: string) => {
      if (familyXref === 'F1') return [{ xref: 'A' }, { xref: 'B' }] as any;
      return [] as any;
    });
    vi.mocked(getPerson).mockImplementation(async (xref: string) => ({ xref, givenName: xref, surname: '' } as any));

    const path = await findRelationshipPath('A', 'B');
    expect(path).not.toBeNull();
    expect(path && path.length).toBeGreaterThan(0);
  });

  it('summarizes parent, grandparent, and cousin labels', () => {
    expect(summarizeRelationship([
      { personXref: 'A', personName: 'A', relationship: 'self' },
      { personXref: 'B', personName: 'B', relationship: 'parent' },
    ])).toBe('Parent');

    expect(summarizeRelationship([
      { personXref: 'A', personName: 'A', relationship: 'self' },
      { personXref: 'B', personName: 'B', relationship: 'parent' },
      { personXref: 'C', personName: 'C', relationship: 'parent' },
    ])).toBe('Grandparent');

    expect(summarizeRelationship([
      { personXref: 'A', personName: 'A', relationship: 'self' },
      { personXref: 'B', personName: 'B', relationship: 'parent' },
      { personXref: 'C', personName: 'C', relationship: 'parent' },
      { personXref: 'D', personName: 'D', relationship: 'child' },
      { personXref: 'E', personName: 'E', relationship: 'child' },
    ])).toBe('Cousin');
  });

  it('shows 30-generation no-path message when no relationship exists', () => {
    expect(summarizeRelationship(null)).toBe('No relationship found within 30 generations');
  });

  it('returns no path and clean summary when person exists but has no family links', async () => {
    vi.mocked(getParents).mockResolvedValue({ father: null, mother: null } as any);
    vi.mocked(getSpouseFamilies).mockResolvedValue([] as any);
    vi.mocked(getChildren).mockResolvedValue([] as any);
    vi.mocked(getPerson).mockImplementation(async (xref: string) => ({ xref, givenName: xref, surname: '' } as any));

    const path = await findRelationshipPath('I_EXISTING', 'I_OTHER');
    expect(path).toBeNull();
    expect(summarizeRelationship(path)).toBe('No relationship found within 30 generations');
  });

  it('labels half-sibling pattern distinctly from full sibling', () => {
    expect(summarizeRelationship([
      { personXref: 'A', personName: 'A', relationship: 'self' },
      { personXref: 'P', personName: 'Parent', relationship: 'parent' },
      { personXref: 'B', personName: 'B', relationship: 'child' },
    ])).toBe('Sibling or Half-Sibling');
  });

  it('labels step relationships that traverse spouse links', () => {
    expect(summarizeRelationship([
      { personXref: 'A', personName: 'A', relationship: 'self' },
      { personXref: 'S', personName: 'Spouse', relationship: 'spouse' },
      { personXref: 'C', personName: 'Child', relationship: 'child' },
    ])).toBe('Stepchild');

    expect(summarizeRelationship([
      { personXref: 'A', personName: 'A', relationship: 'self' },
      { personXref: 'P', personName: 'Parent', relationship: 'parent' },
      { personXref: 'S', personName: 'Spouse', relationship: 'spouse' },
    ])).toBe('Stepparent');
  });

  it('terminates on circular family references without hanging', async () => {
    vi.mocked(getParents).mockImplementation(async (xref: string) => {
      if (xref === 'A') return { father: { xref: 'A' }, mother: null } as any;
      if (xref === 'B') return { father: { xref: 'A' }, mother: null } as any;
      return { father: null, mother: null } as any;
    });
    vi.mocked(getSpouseFamilies).mockImplementation(async (xref: string) => {
      if (xref === 'A') return [{ xref: 'F1', partner1Xref: 'A', partner2Xref: 'A' }] as any;
      return [] as any;
    });
    vi.mocked(getChildren).mockImplementation(async (familyXref: string) => {
      if (familyXref === 'F1') return [{ xref: 'A' }, { xref: 'B' }] as any;
      return [] as any;
    });
    vi.mocked(getPerson).mockImplementation(async (xref: string) => ({ xref, givenName: xref, surname: '' } as any));

    const result = await Promise.race([
      findRelationshipPath('A', 'Z'),
      new Promise<null>((_, reject) => setTimeout(() => reject(new Error('relationship search timeout')), 200)),
    ]);
    expect(result).toBeNull();
  });
});
