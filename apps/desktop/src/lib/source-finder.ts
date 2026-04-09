import { getPerson, insertProposal, insertAgentRun, updateAgentRun, validateProposal } from './db';
import type { Person, Proposal } from './types';

interface SourceSearchResult {
  title: string;
  collection: string;
  recordUrl: string;
  matchScore: number;
  extractedData: Record<string, string>;
}

interface SourceFinderProgress {
  phase: string;
  pct: number;
  message: string;
}

// Search FamilySearch for records matching a person
async function searchFamilySearch(person: Person): Promise<SourceSearchResult[]> {
  const results: SourceSearchResult[] = [];

  // FamilySearch search API (public, no auth required for basic search)
  const params = new URLSearchParams();
  if (person.givenName) params.set('q.givenName', person.givenName);
  if (person.surname) params.set('q.surname', person.surname);
  if (person.birthDate) {
    const year = person.birthDate.match(/(\d{4})/)?.[1];
    if (year) params.set('q.birthLikeDate.from', String(parseInt(year) - 2));
    if (year) params.set('q.birthLikeDate.to', String(parseInt(year) + 2));
  }
  if (person.birthPlace) params.set('q.birthLikePlace', person.birthPlace);

  try {
    const url = `https://api.familysearch.org/platform/search/records?${params.toString()}&count=5`;
    const resp = await fetch(url, {
      headers: { 'Accept': 'application/json' },
    });

    if (resp.ok) {
      const data = await resp.json();
      const entries = data?.entries || data?.searchResult?.entries || [];

      for (const entry of entries.slice(0, 5)) {
        const content = entry?.content?.gedcomx;
        if (!content) continue;

        const primaryPerson = content.persons?.[0];
        const facts = primaryPerson?.facts || [];
        const names = primaryPerson?.names?.[0]?.nameForms?.[0];

        const extracted: Record<string, string> = {};
        if (names?.fullText) extracted.name = names.fullText;

        for (const fact of facts) {
          const type = fact.type?.replace('http://gedcomx.org/', '') || '';
          if (fact.date?.original) extracted[`${type}Date`] = fact.date.original;
          if (fact.place?.original) extracted[`${type}Place`] = fact.place.original;
        }

        const sourceDesc = content.sourceDescriptions?.[0];
        const title = sourceDesc?.titles?.[0]?.value || entry.title || 'FamilySearch Record';
        const collection = sourceDesc?.componentOf?.description || '';
        const recordUrl = entry.links?.record?.href || entry.id || '';

        results.push({
          title,
          collection,
          recordUrl,
          matchScore: entry.score || 0.5,
          extractedData: extracted,
        });
      }
    }
  } catch (e) {
    // FamilySearch API may require auth — fall back gracefully
    console.warn('FamilySearch search failed:', e);
  }

  return results;
}

// Search Chronicling America (Library of Congress) for newspaper mentions
async function searchNewspapers(person: Person): Promise<SourceSearchResult[]> {
  const results: SourceSearchResult[] = [];
  const searchName = `${person.givenName} ${person.surname}`.trim();
  if (!searchName) return results;

  try {
    const params = new URLSearchParams({
      terms: searchName,
      format: 'json',
      rows: '5',
    });

    // Add date range if we know birth/death
    const birthYear = person.birthDate?.match(/(\d{4})/)?.[1];
    const deathYear = person.deathDate?.match(/(\d{4})/)?.[1];
    if (birthYear) params.set('dateFilterType', 'yearRange');
    if (birthYear) params.set('date1', birthYear);
    if (deathYear) params.set('date2', String(parseInt(deathYear) + 5));

    const resp = await fetch(`https://chroniclingamerica.loc.gov/search/pages/results/?${params.toString()}`);
    if (resp.ok) {
      const data = await resp.json();
      for (const item of (data.items || []).slice(0, 3)) {
        results.push({
          title: `${item.title || 'Newspaper'} — ${item.date || ''}`,
          collection: 'Chronicling America / Library of Congress',
          recordUrl: item.url || '',
          matchScore: 0.4, // Newspaper mentions are lower confidence
          extractedData: { date: item.date || '', newspaper: item.title || '' },
        });
      }
    }
  } catch (e) {
    console.warn('Chronicling America search failed:', e);
  }

  return results;
}

// Main source finder: searches multiple databases for a person
export async function findSources(
  personXref: string,
  onProgress?: (p: SourceFinderProgress) => void
): Promise<{ runId: string; sourcesFound: number; proposalsCreated: number }> {
  const runId = crypto.randomUUID();
  let sourcesFound = 0;
  let proposalsCreated = 0;

  const person = await getPerson(personXref);
  if (!person) return { runId, sourcesFound: 0, proposalsCreated: 0 };

  onProgress?.({ phase: 'init', pct: 5, message: `Searching for ${person.givenName} ${person.surname}...` });

  await insertAgentRun({
    runId, provider: 'source-finder', model: 'multi-db', personXref,
    proposalCount: 0, status: 'running', startedAt: new Date().toISOString(), completedAt: '',
  });

  // Search FamilySearch
  onProgress?.({ phase: 'familysearch', pct: 20, message: 'Searching FamilySearch (22.7B records)...' });
  const fsResults = await searchFamilySearch(person);
  sourcesFound += fsResults.length;

  // Search Chronicling America
  onProgress?.({ phase: 'newspapers', pct: 50, message: 'Searching historic newspapers...' });
  const newsResults = await searchNewspapers(person);
  sourcesFound += newsResults.length;

  // Create proposals from search results
  onProgress?.({ phase: 'proposals', pct: 70, message: `Processing ${sourcesFound} records found...` });

  const allResults = [...fsResults, ...newsResults];
  for (const result of allResults) {
    // Create a source citation proposal
    const proposal: Omit<Proposal, 'id'> = {
      agentRunId: runId,
      entityType: 'person',
      entityId: personXref,
      fieldName: 'source',
      oldValue: '',
      newValue: result.title,
      confidence: Math.min(0.95, result.matchScore),
      reasoning: `Found in ${result.collection || 'public records'}. ${Object.entries(result.extractedData).map(([k, v]) => `${k}: ${v}`).join(', ')}`,
      evidenceSource: result.recordUrl,
      status: 'pending',
      createdAt: new Date().toISOString(),
      resolvedAt: '',
    };

    // Also create field-level proposals from extracted data
    for (const [key, value] of Object.entries(result.extractedData)) {
      let fieldName = '';
      if (key === 'BirthDate' && !person.birthDate) fieldName = 'birthDate';
      else if (key === 'BirthPlace' && !person.birthPlace) fieldName = 'birthPlace';
      else if (key === 'DeathDate' && !person.deathDate) fieldName = 'deathDate';
      else if (key === 'DeathPlace' && !person.deathPlace) fieldName = 'deathPlace';

      if (fieldName && value) {
        const fieldProposal: Omit<Proposal, 'id'> = {
          agentRunId: runId,
          entityType: 'person',
          entityId: personXref,
          fieldName,
          oldValue: (person as unknown as Record<string, string>)[fieldName] || '',
          newValue: value,
          confidence: Math.min(0.9, result.matchScore),
          reasoning: `Extracted from: ${result.title}`,
          evidenceSource: result.recordUrl,
          status: 'pending',
          createdAt: new Date().toISOString(),
          resolvedAt: '',
        };

        const validation = await validateProposal(fieldProposal);
        if (validation.valid) {
          await insertProposal(fieldProposal);
          proposalsCreated++;
        }
      }
    }

    // Insert the source citation proposal
    await insertProposal(proposal);
    proposalsCreated++;
  }

  onProgress?.({ phase: 'done', pct: 100, message: `Found ${sourcesFound} records, created ${proposalsCreated} proposals` });

  await updateAgentRun(runId, {
    status: 'completed',
    proposalCount: proposalsCreated,
    completedAt: new Date().toISOString(),
  });

  return { runId, sourcesFound, proposalsCreated };
}

// Batch source finding for multiple people
export async function batchFindSources(
  personXrefs: string[],
  onProgress?: (p: SourceFinderProgress & { current: number; total: number }) => void
): Promise<{ totalSources: number; totalProposals: number }> {
  let totalSources = 0;
  let totalProposals = 0;

  for (let i = 0; i < personXrefs.length; i++) {
    const person = await getPerson(personXrefs[i]);
    const name = person ? `${person.givenName} ${person.surname}` : personXrefs[i];

    onProgress?.({
      phase: 'searching',
      pct: Math.round((i / personXrefs.length) * 100),
      message: `Searching for ${name} (${i + 1}/${personXrefs.length})...`,
      current: i + 1,
      total: personXrefs.length,
    });

    try {
      const result = await findSources(personXrefs[i]);
      totalSources += result.sourcesFound;
      totalProposals += result.proposalsCreated;
    } catch (e) {
      console.warn(`Source search failed for ${personXrefs[i]}:`, e);
    }

    // Rate limit — 2s between searches to be respectful to public APIs
    if (i < personXrefs.length - 1) {
      await new Promise(r => setTimeout(r, 2000));
    }
  }

  onProgress?.({
    phase: 'done', pct: 100, current: personXrefs.length, total: personXrefs.length,
    message: `Done! Found ${totalSources} sources, created ${totalProposals} proposals`,
  });

  return { totalSources, totalProposals };
}
