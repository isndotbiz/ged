import { describe, expect, it } from 'vitest';
import { allRelationships, expectedCMForRelationship, predictRelationships } from '$lib/dna-calculator';

describe('dna-calculator', () => {
  it('predicts close relationship for ~3400 cM', () => {
    const result = predictRelationships(3400);
    expect(result.length).toBeGreaterThan(0);
    expect(result.some((r) => r.relationship === 'Parent/Child' || r.relationship === 'Identical Twin')).toBe(true);
  });

  it('handles edge values', () => {
    expect(predictRelationships(0)).toEqual([]);
    expect(predictRelationships(-5)).toEqual([]);
    expect(predictRelationships(10000)).toEqual([]);
  });

  it('supports reverse lookup', () => {
    expect(expectedCMForRelationship('2nd Cousin')?.averageCM).toBe(229);
    expect(allRelationships().length).toBeGreaterThan(10);
  });
});
