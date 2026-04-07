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
});
