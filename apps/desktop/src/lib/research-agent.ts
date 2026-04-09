import { getPerson, getEvents, getSpouseFamilies, getChildren, getParents, getSources, getDb,
         insertProposal, insertAgentRun, updateAgentRun, validateProposal } from './db';
import type { Person, Proposal } from './types';

interface ResearchProgress {
  phase: string;
  pct: number;
  message: string;
  currentPerson?: string;
}

interface ResearchCandidate {
  xref: string;
  name: string;
  missingFields: string[];
  priority: number;
}

interface BatchResearchResult {
  totalProposals: number;
  totalErrors: number;
  runs: string[];
}

interface ParsedProposal {
  fieldName: string;
  newValue: string;
  confidence: number;
  reasoning: string;
  evidenceSource: string;
}

export async function runResearchAgent(
  personXref: string,
  onProgress?: (p: ResearchProgress) => void
): Promise<{ runId: string; proposalCount: number; errors: string[] }> {
  const errors: string[] = [];
  const runId = crypto.randomUUID();

  onProgress?.({ phase: 'init', pct: 5, message: 'Loading person data...' });

  // 1. Load person context
  const person = await getPerson(personXref);
  if (!person) return { runId, proposalCount: 0, errors: ['Person not found'] };

  const events = await getEvents(personXref);
  const parents = await getParents(personXref);
  const spouseFamilies = await getSpouseFamilies(personXref);

  // 2. Get AI provider config
  onProgress?.({ phase: 'config', pct: 10, message: 'Loading AI configuration...' });
  const db = await getDb();
  const allSettings = await db.select<{key:string; value:string}[]>(
    `SELECT key, value FROM settings WHERE key LIKE 'ai_%'`
  );
  const settingsMap = new Map(allSettings.map(s => [s.key, s.value]));
  const provider = settingsMap.get('ai_active_provider') || 'anthropic';
  const model = settingsMap.get(`ai_model_${provider}`) || getDefaultModel(provider);
  const apiKey = settingsMap.get(`ai_key_${provider}`);
  if (!apiKey) return { runId, proposalCount: 0, errors: [`No API key configured for ${provider}. Go to AI Settings.`] };

  // 3. Create agent run
  await insertAgentRun({
    runId, provider, model, personXref, proposalCount: 0, status: 'running',
    startedAt: new Date().toISOString(), completedAt: '',
  });

  // 4. Build research context
  onProgress?.({ phase: 'research', pct: 20, message: `Researching ${person.givenName} ${person.surname}...` });

  const personContext = buildPersonContext(person, events, parents, spouseFamilies);
  const systemPrompt = buildResearchPrompt();
  const userPrompt = buildResearchQuery(person, personContext);

  // 5. Call AI
  onProgress?.({ phase: 'ai', pct: 40, message: `Asking ${provider} for research findings...` });

  let aiResponse: string;
  try {
    aiResponse = await callAI(provider, model, apiKey, systemPrompt, userPrompt);
  } catch (e: any) {
    await updateAgentRun(runId, { status: 'failed', completedAt: new Date().toISOString() });
    return { runId, proposalCount: 0, errors: [`AI call failed: ${e.message}`] };
  }

  // 6. Parse proposals from AI response
  onProgress?.({ phase: 'parse', pct: 70, message: 'Parsing research findings...' });

  let parsed: ParsedProposal[];
  try {
    parsed = parseAIProposals(aiResponse);
  } catch (e: any) {
    await updateAgentRun(runId, { status: 'failed', completedAt: new Date().toISOString() });
    return { runId, proposalCount: 0, errors: [`Failed to parse AI response: ${e.message}`] };
  }

  // 7. Validate and insert proposals
  onProgress?.({ phase: 'validate', pct: 85, message: `Validating ${parsed.length} proposals...` });

  let proposalCount = 0;
  for (const pp of parsed) {
    const proposal: Omit<Proposal, 'id'> = {
      agentRunId: runId,
      entityType: 'person',
      entityId: personXref,
      fieldName: pp.fieldName,
      oldValue: (person as any)[pp.fieldName] ?? '',
      newValue: pp.newValue,
      confidence: pp.confidence,
      reasoning: pp.reasoning,
      evidenceSource: pp.evidenceSource,
      status: 'pending',
      createdAt: new Date().toISOString(),
      resolvedAt: '',
    };

    const validation = await validateProposal(proposal);
    if (validation.valid) {
      await insertProposal(proposal);
      proposalCount++;
    } else {
      errors.push(`Rejected: ${pp.fieldName} = "${pp.newValue}" — ${validation.violations.join('; ')}`);
    }
  }

  // 8. Complete
  onProgress?.({ phase: 'done', pct: 100, message: `Found ${proposalCount} proposals for review` });
  await updateAgentRun(runId, {
    status: 'completed',
    proposalCount,
    completedAt: new Date().toISOString(),
  });

  return { runId, proposalCount, errors };
}

export async function runBatchResearch(
  personXrefs: string[],
  onProgress?: (p: ResearchProgress) => void
): Promise<BatchResearchResult> {
  let totalProposals = 0;
  let totalErrors = 0;
  const runs: string[] = [];

  for (let i = 0; i < personXrefs.length; i++) {
    const xref = personXrefs[i];
    const person = await getPerson(xref);
    const name = person ? `${person.givenName} ${person.surname}` : xref;

    onProgress?.({
      phase: 'researching',
      pct: Math.round((i / personXrefs.length) * 100),
      message: `Researching ${name} (${i + 1}/${personXrefs.length})...`,
      currentPerson: name,
    });

    try {
      const result = await runResearchAgent(xref);
      totalProposals += result.proposalCount;
      totalErrors += result.errors.length;
      runs.push(result.runId);
    } catch {
      totalErrors++;
    }

    // Rate limit: wait 1s between API calls to avoid throttling
    if (i < personXrefs.length - 1) {
      await new Promise(r => setTimeout(r, 1000));
    }
  }

  onProgress?.({ phase: 'done', pct: 100, message: `Done: ${totalProposals} proposals from ${personXrefs.length} people` });
  return { totalProposals, totalErrors, runs };
}

export async function findResearchCandidates(): Promise<ResearchCandidate[]> {
  const db = await getDb();
  const persons = await db.select<Person[]>(`SELECT * FROM person ORDER BY surname, givenName`);
  const candidates: ResearchCandidate[] = [];

  for (const p of persons) {
    const missing: string[] = [];
    if (!p.birthDate) missing.push('birth date');
    if (!p.birthPlace) missing.push('birth place');
    if (!p.deathDate) missing.push('death date');
    if (!p.deathPlace) missing.push('death place');

    // Check if person has any sources
    const srcCount = p.sourceCount || 0;
    if (srcCount === 0) missing.push('sources');

    if (missing.length > 0) {
      candidates.push({
        xref: p.xref,
        name: `${p.givenName} ${p.surname}`.trim(),
        missingFields: missing,
        priority: missing.length + (srcCount === 0 ? 2 : 0), // Higher = more needed
      });
    }
  }

  return candidates.sort((a, b) => b.priority - a.priority);
}

function getDefaultModel(provider: string): string {
  const defaults: Record<string, string> = {
    anthropic: 'claude-sonnet-4-20250514',
    openai: 'gpt-4o',
    xai: 'grok-3-mini',
    gemini: 'gemini-2.5-flash',
    groq: 'llama-3.3-70b-versatile',
    together: 'meta-llama/Llama-3.3-70B-Instruct-Turbo',
    openrouter: 'anthropic/claude-sonnet-4',
  };
  return defaults[provider] || 'gpt-4o';
}

function buildPersonContext(person: Person, events: any[], parents: any, families: any[]): string {
  let ctx = `Name: ${person.givenName} ${person.surname}`;
  if (person.suffix) ctx += ` ${person.suffix}`;
  ctx += `\nSex: ${person.sex}`;
  if (person.birthDate) ctx += `\nBirth: ${person.birthDate}`;
  if (person.birthPlace) ctx += ` in ${person.birthPlace}`;
  if (person.deathDate) ctx += `\nDeath: ${person.deathDate}`;
  if (person.deathPlace) ctx += ` in ${person.deathPlace}`;
  if (parents.father) ctx += `\nFather: ${parents.father.givenName} ${parents.father.surname}`;
  if (parents.mother) ctx += `\nMother: ${parents.mother.givenName} ${parents.mother.surname}`;

  if (events.length > 0) {
    ctx += '\nEvents:';
    for (const e of events) {
      ctx += `\n  ${e.eventType}: ${e.dateValue || 'unknown date'}${e.place ? ' at ' + e.place : ''}`;
    }
  }

  // Identify gaps
  const gaps: string[] = [];
  if (!person.birthDate) gaps.push('birth date');
  if (!person.birthPlace) gaps.push('birth place');
  if (!person.deathDate && !person.isLiving) gaps.push('death date');
  if (!person.deathPlace && !person.isLiving) gaps.push('death place');
  if (!parents.father) gaps.push('father identity');
  if (!parents.mother) gaps.push('mother identity');

  if (gaps.length > 0) {
    ctx += `\n\nMISSING INFORMATION: ${gaps.join(', ')}`;
  }

  return ctx;
}

function buildResearchPrompt(): string {
  return `You are an autonomous genealogical research agent. Your job is to analyze a person's record and propose specific, verifiable improvements to their family tree data.

CRITICAL RULES:
1. Only propose changes you have HIGH confidence in based on the data provided
2. Every proposal MUST include reasoning and ideally an evidence source
3. Never fabricate information — only infer from existing data patterns
4. Prefer filling gaps (missing dates, places) over changing existing data
5. Use standard date format: DD MMM YYYY (e.g., "15 Mar 1892")
6. For places, use full format: "City, County, State, Country"

You MUST respond with ONLY a JSON array of proposals. Each proposal:
{
  "fieldName": "birthDate" | "birthPlace" | "deathDate" | "deathPlace" | "surname" | "givenName" | "suffix",
  "newValue": "the proposed value",
  "confidence": 0.0-1.0,
  "reasoning": "why you believe this is correct",
  "evidenceSource": "record type or citation"
}

If you have NO proposals, return an empty array: []

IMPORTANT: Respond ONLY with the JSON array. No other text.`;
}

function buildResearchQuery(person: Person, context: string): string {
  return `Analyze this person's genealogical record and propose specific improvements.

${context}

Research tasks (in priority order):
1. MISSING DATA: Fill in any missing dates or places. Use standard genealogical reasoning — if a person was born in City X and their siblings were also born there, the birth place is likely City X.
2. DATE STANDARDIZATION: Convert any non-standard dates to "DD MMM YYYY" format (e.g., "15 Mar 1892"). If only a year is known, use "ABT 1892".
3. PLACE STANDARDIZATION: Expand abbreviated places to full format "City, County, State/Province, Country". Use historical place names appropriate to the time period.
4. CORRECTIONS: If any data appears incorrect based on other evidence in the record (e.g., a child born before their parent), flag and correct it.
5. INFERENCES: If parents, siblings, or spouses provide context that narrows down missing information, propose it with appropriate confidence.

IMPORTANT: Only propose changes you can justify from the data provided. Set confidence to:
- 0.9+ for standardization of existing data
- 0.7-0.9 for strong inferences from family context
- 0.5-0.7 for reasonable guesses based on patterns
- Do NOT propose anything below 0.5 confidence

Return your proposals as a JSON array.`;
}

async function callAI(provider: string, model: string, apiKey: string, systemPrompt: string, userPrompt: string): Promise<string> {
  let url = '';
  let headers: Record<string, string> = { 'Content-Type': 'application/json' };
  let body = '';

  if (provider === 'anthropic') {
    url = 'https://api.anthropic.com/v1/messages';
    headers['x-api-key'] = apiKey;
    headers['anthropic-version'] = '2023-06-01';
    body = JSON.stringify({
      model, max_tokens: 4096, system: systemPrompt,
      messages: [{ role: 'user', content: userPrompt }],
    });
  } else if (provider === 'gemini') {
    url = `https://generativelanguage.googleapis.com/v1beta/models/${model}:generateContent`;
    headers['x-goog-api-key'] = apiKey;
    body = JSON.stringify({
      systemInstruction: { parts: [{ text: systemPrompt }] },
      contents: [{ parts: [{ text: userPrompt }] }],
      generationConfig: { maxOutputTokens: 4096 },
    });
  } else {
    const endpoints: Record<string, string> = {
      openai: 'https://api.openai.com/v1/chat/completions',
      xai: 'https://api.x.ai/v1/chat/completions',
      groq: 'https://api.groq.com/openai/v1/chat/completions',
      together: 'https://api.together.xyz/v1/chat/completions',
      openrouter: 'https://openrouter.ai/api/v1/chat/completions',
    };
    url = endpoints[provider] || endpoints.openai;
    headers['Authorization'] = `Bearer ${apiKey}`;
    body = JSON.stringify({
      model,
      messages: [{ role: 'system', content: systemPrompt }, { role: 'user', content: userPrompt }],
      max_tokens: 4096,
    });
  }

  const resp = await fetch(url, { method: 'POST', headers, body });
  if (!resp.ok) {
    const errBody = await resp.text();
    throw new Error(`API ${resp.status}: ${errBody.slice(0, 200)}`);
  }

  const data = await resp.json();
  if (provider === 'anthropic') return data.content?.[0]?.text || '';
  if (provider === 'gemini') return data.candidates?.[0]?.content?.parts?.[0]?.text || '';
  return data.choices?.[0]?.message?.content || '';
}

function parseAIProposals(response: string): ParsedProposal[] {
  // Extract JSON array from response (may be wrapped in markdown code blocks)
  let jsonStr = response.trim();
  const jsonMatch = jsonStr.match(/\[[\s\S]*\]/);
  if (!jsonMatch) return [];
  jsonStr = jsonMatch[0];

  const parsed = JSON.parse(jsonStr);
  if (!Array.isArray(parsed)) return [];

  const validFields = new Set(['birthDate', 'birthPlace', 'deathDate', 'deathPlace', 'surname', 'givenName', 'suffix']);

  return parsed
    .filter((p: any) => p.fieldName && validFields.has(p.fieldName) && p.newValue)
    .map((p: any) => ({
      fieldName: p.fieldName,
      newValue: String(p.newValue),
      confidence: Math.min(1, Math.max(0, Number(p.confidence) || 0.5)),
      reasoning: String(p.reasoning || ''),
      evidenceSource: String(p.evidenceSource || ''),
    }));
}
