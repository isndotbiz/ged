import { getChildren, getParents, getPerson, getSpouseFamilies } from './db';

export interface RelationshipStep {
  personXref: string;
  personName: string;
  relationship: 'self' | 'parent' | 'child' | 'spouse';
}

type Edge = { to: string; relationship: RelationshipStep['relationship'] };

async function neighbors(xref: string): Promise<Edge[]> {
  const out: Edge[] = [];
  const parents = await getParents(xref);
  if (parents.father?.xref) out.push({ to: parents.father.xref, relationship: 'parent' });
  if (parents.mother?.xref) out.push({ to: parents.mother.xref, relationship: 'parent' });

  const fams = await getSpouseFamilies(xref);
  for (const fam of fams) {
    const spouse = fam.partner1Xref === xref ? fam.partner2Xref : fam.partner1Xref;
    if (spouse) out.push({ to: spouse, relationship: 'spouse' });
    const kids = await getChildren(fam.xref);
    for (const child of kids) out.push({ to: child.xref, relationship: 'child' });
  }
  return out;
}

export async function findRelationshipPath(xrefA: string, xrefB: string): Promise<RelationshipStep[] | null> {
  if (xrefA === xrefB) {
    const person = await getPerson(xrefA);
    return person ? [{ personXref: person.xref, personName: `${person.givenName} ${person.surname}`.trim(), relationship: 'self' }] : null;
  }

  const maxDepth = 30;
  const queue: string[] = [xrefA];
  const visited = new Set<string>([xrefA]);
  const prev = new Map<string, { from: string; rel: RelationshipStep['relationship'] }>();
  const depth = new Map<string, number>([[xrefA, 0]]);

  while (queue.length > 0) {
    const current = queue.shift()!;
    const d = depth.get(current) ?? 0;
    if (d >= maxDepth) continue;
    const next = await neighbors(current);
    for (const n of next) {
      if (visited.has(n.to)) continue;
      visited.add(n.to);
      prev.set(n.to, { from: current, rel: n.relationship });
      depth.set(n.to, d + 1);
      if (n.to === xrefB) {
        queue.length = 0;
        break;
      }
      queue.push(n.to);
    }
  }

  if (!visited.has(xrefB)) return null;

  const path: { xref: string; rel: RelationshipStep['relationship'] }[] = [];
  let cursor = xrefB;
  while (cursor !== xrefA) {
    const p = prev.get(cursor);
    if (!p) break;
    path.push({ xref: cursor, rel: p.rel });
    cursor = p.from;
  }
  path.push({ xref: xrefA, rel: 'self' });
  path.reverse();

  const withNames: RelationshipStep[] = [];
  for (let i = 0; i < path.length; i++) {
    const p = await getPerson(path[i].xref);
    if (!p) continue;
    withNames.push({
      personXref: p.xref,
      personName: `${p.givenName} ${p.surname}`.trim() || p.xref,
      relationship: i === 0 ? 'self' : path[i].rel,
    });
  }
  return withNames;
}

export function summarizeRelationship(path: RelationshipStep[] | null): string {
  if (!path || path.length < 2) return 'No relationship found within 15 generations';
  const pattern = path.slice(1).map((s) => s.relationship);
  if (pattern.every((r) => r === 'parent')) return `Direct ancestor (${pattern.length} generations)`;
  if (pattern.every((r) => r === 'child')) return `Direct descendant (${pattern.length} generations)`;
  if (pattern.length === 1 && pattern[0] === 'spouse') return 'Spouse';
  return 'Connected relative';
}
