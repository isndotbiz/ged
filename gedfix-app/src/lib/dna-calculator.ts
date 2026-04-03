import type { DNARelationshipPrediction } from './types';

interface RelationshipRange {
  label: string;
  averageCM: number;
  minCM: number;
  maxCM: number;
}

const RELATIONSHIP_TABLE: RelationshipRange[] = [
  { label: 'Identical Twin', averageCM: 3400, minCM: 3300, maxCM: 3500 },
  { label: 'Parent/Child', averageCM: 3485, minCM: 3330, maxCM: 3720 },
  { label: 'Full Sibling', averageCM: 2613, minCM: 2209, maxCM: 3384 },
  { label: 'Grandparent/Grandchild', averageCM: 1754, minCM: 1156, maxCM: 2311 },
  { label: 'Aunt/Uncle', averageCM: 1741, minCM: 1201, maxCM: 2282 },
  { label: 'Half Sibling', averageCM: 1759, minCM: 1160, maxCM: 2436 },
  { label: 'Niece/Nephew', averageCM: 1741, minCM: 1201, maxCM: 2282 },
  { label: 'Great-Grandparent', averageCM: 881, minCM: 492, maxCM: 1315 },
  { label: 'Great-Aunt/Uncle', averageCM: 851, minCM: 492, maxCM: 1315 },
  { label: '1st Cousin', averageCM: 866, minCM: 553, maxCM: 1225 },
  { label: 'Half Aunt/Uncle', averageCM: 851, minCM: 492, maxCM: 1315 },
  { label: '1st Cousin Once Removed', averageCM: 433, minCM: 220, maxCM: 680 },
  { label: 'Great-Great-Grandparent', averageCM: 444, minCM: 176, maxCM: 712 },
  { label: 'Half 1st Cousin', averageCM: 433, minCM: 156, maxCM: 550 },
  { label: '2nd Cousin', averageCM: 229, minCM: 41, maxCM: 502 },
  { label: '2nd Cousin Once Removed', averageCM: 122, minCM: 18, maxCM: 338 },
  { label: '3rd Cousin', averageCM: 73, minCM: 0, maxCM: 234 },
  { label: '3rd Cousin Once Removed', averageCM: 48, minCM: 0, maxCM: 173 },
  { label: '4th Cousin', averageCM: 35, minCM: 0, maxCM: 139 },
  { label: '4th Cousin Once Removed', averageCM: 20, minCM: 0, maxCM: 99 },
  { label: '5th Cousin', averageCM: 15, minCM: 0, maxCM: 75 },
  { label: '6th Cousin', averageCM: 10, minCM: 0, maxCM: 53 },
];

export function predictRelationships(sharedCM: number): DNARelationshipPrediction[] {
  if (sharedCM <= 0) return [];

  return RELATIONSHIP_TABLE
    .filter(r => sharedCM >= r.minCM && sharedCM <= r.maxCM)
    .sort((a, b) => Math.abs(a.averageCM - sharedCM) - Math.abs(b.averageCM - sharedCM))
    .map(range => {
      const distance = Math.abs(range.averageCM - sharedCM);
      const span = range.maxCM - range.minCM;
      const closeness = span > 0 ? Math.max(0, Math.min(1, 1 - distance / span)) : 1;
      const probability = closeness > 0.8 ? 'Very likely' : closeness > 0.5 ? 'Likely' : closeness > 0.3 ? 'Possible' : 'Less likely';
      return { relationship: range.label, averageCM: range.averageCM, minCM: range.minCM, maxCM: range.maxCM, probability };
    });
}

export function allRelationships(): RelationshipRange[] {
  return RELATIONSHIP_TABLE;
}

export function expectedCMForRelationship(name: string): RelationshipRange | undefined {
  return RELATIONSHIP_TABLE.find(r => r.label.toLowerCase() === name.toLowerCase());
}
