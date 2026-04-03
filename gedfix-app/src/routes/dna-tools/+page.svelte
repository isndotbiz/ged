<script lang="ts">
  import { t } from '$lib/i18n';
  import { predictRelationships, allRelationships, expectedCMForRelationship } from '$lib/dna-calculator';
  import { getPersons, getDb } from '$lib/db';
  import type { DNARelationshipPrediction, Person } from '$lib/types';

  let sharedCM = $state('');
  let predictions = $state<DNARelationshipPrediction[]>([]);
  let selectedRel = $state('');
  let lookup = $state<{ label: string; averageCM: number; minCM: number; maxCM: number } | undefined>();

  function predict() {
    const cm = parseFloat(sharedCM);
    if (!isNaN(cm) && cm > 0) predictions = predictRelationships(cm);
  }

  function lookupRel() {
    lookup = expectedCMForRelationship(selectedRel);
  }

  const rels = allRelationships();

  function probColor(p: string): string {
    if (p === 'Very likely') return 'bg-green-50 text-green-700 border-green-200';
    if (p === 'Likely') return 'bg-blue-50 text-blue-700 border-blue-200';
    if (p === 'Possible') return 'bg-yellow-50 text-yellow-700 border-yellow-200';
    return 'bg-gray-50 text-gray-600 border-gray-200';
  }

  // --- Relationship Calculator state ---
  let searchA = $state('');
  let searchB = $state('');
  let suggestionsA = $state<Person[]>([]);
  let suggestionsB = $state<Person[]>([]);
  let personA = $state<Person | null>(null);
  let personB = $state<Person | null>(null);
  let relResult = $state('');
  let relPath = $state<string[]>([]);
  let relLoading = $state(false);
  let showDropdownA = $state(false);
  let showDropdownB = $state(false);

  function personDisplay(p: Person): string {
    const name = `${p.givenName || ''} ${p.surname || ''}`.trim() || p.xref;
    const birth = p.birthDate ? ` (b. ${p.birthDate})` : '';
    return `${name}${birth}`;
  }

  async function searchPersons(query: string, which: 'A' | 'B') {
    if (query.trim().length < 2) {
      if (which === 'A') { suggestionsA = []; showDropdownA = false; }
      else { suggestionsB = []; showDropdownB = false; }
      return;
    }
    const results = await getPersons(query.trim(), 15);
    if (which === 'A') { suggestionsA = results; showDropdownA = results.length > 0; }
    else { suggestionsB = results; showDropdownB = results.length > 0; }
  }

  function selectPerson(p: Person, which: 'A' | 'B') {
    if (which === 'A') {
      personA = p;
      searchA = personDisplay(p);
      showDropdownA = false;
      suggestionsA = [];
    } else {
      personB = p;
      searchB = personDisplay(p);
      showDropdownB = false;
      suggestionsB = [];
    }
  }

  function clearPerson(which: 'A' | 'B') {
    if (which === 'A') { personA = null; searchA = ''; suggestionsA = []; }
    else { personB = null; searchB = ''; suggestionsB = []; }
    relResult = '';
    relPath = [];
  }

  // --- BFS Ancestor Map: xref -> generation depth ---
  type AncestorMap = Map<string, number>;
  type FamilyGraph = {
    parentOf: Map<string, string[]>;   // child -> parent xrefs
    childrenOf: Map<string, string[]>; // parent -> child xrefs
    spouseOf: Map<string, string[]>;   // person -> spouse xrefs
  };

  async function buildFamilyGraph(): Promise<FamilyGraph> {
    const db = await getDb();
    const families = await db.select<{ xref: string; partner1Xref: string; partner2Xref: string }[]>(
      'SELECT xref, partner1Xref, partner2Xref FROM family'
    );
    const childLinks = await db.select<{ familyXref: string; childXref: string }[]>(
      'SELECT familyXref, childXref FROM child_link'
    );

    const parentOf = new Map<string, string[]>();
    const childrenOf = new Map<string, string[]>();
    const spouseOf = new Map<string, string[]>();

    const familyPartners = new Map<string, { p1: string; p2: string }>();
    for (const f of families) {
      familyPartners.set(f.xref, { p1: f.partner1Xref, p2: f.partner2Xref });
      if (f.partner1Xref && f.partner2Xref) {
        if (!spouseOf.has(f.partner1Xref)) spouseOf.set(f.partner1Xref, []);
        if (!spouseOf.has(f.partner2Xref)) spouseOf.set(f.partner2Xref, []);
        spouseOf.get(f.partner1Xref)!.push(f.partner2Xref);
        spouseOf.get(f.partner2Xref)!.push(f.partner1Xref);
      }
    }

    for (const cl of childLinks) {
      const fp = familyPartners.get(cl.familyXref);
      if (!fp) continue;
      const parents: string[] = [];
      if (fp.p1) parents.push(fp.p1);
      if (fp.p2) parents.push(fp.p2);
      parentOf.set(cl.childXref, [...(parentOf.get(cl.childXref) || []), ...parents]);
      for (const px of parents) {
        if (!childrenOf.has(px)) childrenOf.set(px, []);
        childrenOf.get(px)!.push(cl.childXref);
      }
    }

    return { parentOf, childrenOf, spouseOf };
  }

  function getAncestors(startXref: string, graph: FamilyGraph): AncestorMap {
    const ancestors: AncestorMap = new Map();
    ancestors.set(startXref, 0);
    const queue: { xref: string; depth: number }[] = [{ xref: startXref, depth: 0 }];
    while (queue.length > 0) {
      const { xref, depth } = queue.shift()!;
      const parents = graph.parentOf.get(xref) || [];
      for (const px of parents) {
        if (!ancestors.has(px)) {
          ancestors.set(px, depth + 1);
          queue.push({ xref: px, depth: depth + 1 });
        }
      }
    }
    return ancestors;
  }

  function describeRelationship(genA: number, genB: number): string {
    // genA = generations from person A to common ancestor
    // genB = generations from person B to common ancestor
    if (genA === 0 && genB === 0) return 'the same person as';
    if (genA === 0 && genB === 1) return 'the parent of';
    if (genA === 1 && genB === 0) return 'the child of';
    if (genA === 0 && genB === 2) return 'the grandparent of';
    if (genA === 2 && genB === 0) return 'the grandchild of';
    if (genA === 0 && genB > 2) return `the ${'great-'.repeat(genB - 2)}grandparent of`;
    if (genA > 2 && genB === 0) return `the ${'great-'.repeat(genA - 2)}grandchild of`;
    if (genA === 1 && genB === 1) return 'the sibling of';
    if (genA === 1 && genB === 2) return 'the uncle/aunt of';
    if (genA === 2 && genB === 1) return 'the niece/nephew of';
    if (genA === 1 && genB > 2) return `the great-${'great-'.repeat(genB - 3)}uncle/aunt of`;
    if (genA > 2 && genB === 1) return `the great-${'great-'.repeat(genA - 3)}niece/nephew of`;

    // Cousins: min generation - 1 = cousin degree, difference = times removed
    const minGen = Math.min(genA, genB);
    const cousinDegree = minGen - 1;
    const removed = Math.abs(genA - genB);

    const ordinal = (n: number) => {
      if (n === 1) return '1st';
      if (n === 2) return '2nd';
      if (n === 3) return '3rd';
      return `${n}th`;
    };

    let label = `${ordinal(cousinDegree)} cousin`;
    if (removed > 0) {
      label += ` ${removed} time${removed > 1 ? 's' : ''} removed`;
    }
    return `the ${label} of`;
  }

  async function calculateRelationship() {
    if (!personA || !personB) return;
    relLoading = true;
    relResult = '';
    relPath = [];

    try {
      const graph = await buildFamilyGraph();
      const ancestorsA = getAncestors(personA.xref, graph);
      const ancestorsB = getAncestors(personB.xref, graph);

      // Find common ancestors
      let bestCommon: { xref: string; genA: number; genB: number } | null = null;
      for (const [xref, genA] of ancestorsA) {
        const genB = ancestorsB.get(xref);
        if (genB !== undefined) {
          if (!bestCommon || (genA + genB) < (bestCommon.genA + bestCommon.genB)) {
            bestCommon = { xref, genA, genB };
          }
        }
      }

      if (!bestCommon) {
        // Check for spouse relationship
        const spouses = graph.spouseOf.get(personA.xref) || [];
        if (spouses.includes(personB.xref)) {
          relResult = `${personDisplay(personA)} is the spouse of ${personDisplay(personB)}`;
        } else {
          relResult = `No common ancestor found between ${personDisplay(personA)} and ${personDisplay(personB)}`;
        }
      } else {
        const relDesc = describeRelationship(bestCommon.genA, bestCommon.genB);
        relResult = `${personDisplay(personA)} is ${relDesc} ${personDisplay(personB)}`;
      }
    } catch (err) {
      relResult = `Error calculating relationship: ${err instanceof Error ? err.message : String(err)}`;
    } finally {
      relLoading = false;
    }
  }
</script>

<div class="p-8 max-w-4xl animate-fade-in">
  <h1 class="text-2xl font-bold tracking-tight" style="font-family: var(--font-serif); color: var(--ink);">{t('nav.dnaTools')}</h1>
  <p class="text-sm text-ink-muted mt-1 mb-8">Relationship prediction using Shared cM Project v4 data</p>

  <!-- cM to Relationship -->
  <div class="arch-card rounded-xl p-6 mb-6">
    <h2 class="text-sm font-semibold text-ink mb-1">Shared cM → Relationship</h2>
    <p class="text-xs text-ink-muted mb-4">Enter shared centiMorgans to predict possible relationships</p>
    <div class="flex gap-3 items-end mb-4">
      <input
        type="number"
        bind:value={sharedCM}
        placeholder="e.g. 850"
        class="w-40 px-3 py-2 text-sm border border-gray-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-amber-500/20 focus:border-amber-500"
        onkeydown={(e) => e.key === 'Enter' && predict()}
        aria-label="e.g. 850"
      />
      <button onclick={predict} class="px-4 py-2 text-sm font-medium btn-accent text-white rounded-lg transition-colors">
        Predict
      </button>
    </div>

    {#if predictions.length > 0}
      <div class="space-y-2">
        {#each predictions as pred}
          <div class="flex items-center justify-between p-3 rounded-lg border {probColor(pred.probability)}">
            <div>
              <span class="font-medium text-sm">{pred.relationship}</span>
              <span class="text-xs ml-2 opacity-70">Avg: {pred.averageCM} cM ({pred.minCM}–{pred.maxCM})</span>
            </div>
            <span class="text-xs font-semibold px-2 py-0.5 rounded-full bg-white/50">{pred.probability}</span>
          </div>
        {/each}
      </div>
    {/if}
  </div>

  <!-- Relationship to cM Lookup -->
  <div class="arch-card rounded-xl p-6 mb-6">
    <h2 class="text-sm font-semibold text-ink mb-4">Relationship → Expected cM</h2>
    <div class="flex gap-3 items-end mb-4">
      <select bind:value={selectedRel} onchange={lookupRel} class="px-3 py-2 text-sm border border-gray-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-amber-500/20" aria-label={t('common.filter')}>
        <option value="">Select relationship...</option>
        {#each rels as r}
          <option value={r.label}>{r.label}</option>
        {/each}
      </select>
    </div>
    {#if lookup}
      <div class="flex gap-8 p-4 bg-blue-50 rounded-lg">
        <div>
          <div class="text-xs text-blue-600">Average</div>
          <div class="text-2xl font-bold text-blue-900">{lookup.averageCM} cM</div>
        </div>
        <div>
          <div class="text-xs text-blue-600">Range</div>
          <div class="text-2xl font-bold text-blue-900">{lookup.minCM}–{lookup.maxCM} cM</div>
        </div>
      </div>
    {/if}
  </div>

  <!-- Reference Table -->
  <div class="arch-card rounded-xl p-6">
    <h2 class="text-sm font-semibold text-ink mb-1">Shared cM Reference Table</h2>
    <p class="text-xs text-ink-muted mb-4">Based on the Shared cM Project v4 by Blaine Bettinger</p>
    <table class="w-full text-sm">
      <thead>
        <tr class="text-left text-xs text-ink-muted border-b border-gray-100">
          <th class="py-2 font-medium">{t('relationship.path')}</th>
          <th class="py-2 font-medium text-right">Avg cM</th>
          <th class="py-2 font-medium text-right">Min</th>
          <th class="py-2 font-medium text-right">Max</th>
        </tr>
      </thead>
      <tbody>
        {#each rels as r}
          <tr class="border-b border-gray-50 hover:bg-gray-50/50">
            <td class="py-1.5 text-ink-light">{r.label}</td>
            <td class="py-1.5 text-right font-medium tabular-nums">{r.averageCM}</td>
            <td class="py-1.5 text-right text-ink-muted tabular-nums">{r.minCM}</td>
            <td class="py-1.5 text-right text-ink-muted tabular-nums">{r.maxCM}</td>
          </tr>
        {/each}
      </tbody>
    </table>
  </div>

  <!-- Relationship Calculator -->
  <div class="arch-card rounded-xl p-6 mt-6">
    <h2 class="text-sm font-semibold text-ink mb-1">Relationship Calculator</h2>
    <p class="text-xs text-ink-muted mb-4">{t('relationship.subtitle')}</p>

    <div class="grid grid-cols-1 md:grid-cols-2 gap-4 mb-4">
      <!-- Person A -->
      <div class="relative">
        <label for="relationship-person-a" class="block text-xs font-medium text-ink-muted mb-1">{t('relationship.personA')}</label>
        {#if personA}
          <div class="flex items-center gap-2 px-3 py-2 text-sm border border-green-200 bg-green-50 rounded-lg">
            <span class="flex-1 text-green-800 font-medium truncate">{personDisplay(personA)}</span>
            <button onclick={() => clearPerson('A')} class="text-green-600 hover:text-red-500 text-xs font-bold flex-shrink-0">&times;</button>
          </div>
        {:else}
          <input
            id="relationship-person-a"
            type="text"
            bind:value={searchA}
            oninput={() => searchPersons(searchA, 'A')}
            onfocus={() => { if (suggestionsA.length > 0) showDropdownA = true; }}
            onblur={() => setTimeout(() => showDropdownA = false, 200)}
            placeholder={t('people.searchByName')}
            class="w-full px-3 py-2 text-sm border border-gray-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-amber-500/20 focus:border-amber-500"
            aria-label={t('people.searchByName')}
          />
          {#if showDropdownA && suggestionsA.length > 0}
            <div class="absolute z-10 w-full mt-1 bg-white border border-gray-200 rounded-lg shadow-lg max-h-48 overflow-y-auto">
              {#each suggestionsA as s}
                <button
                  class="w-full text-left px-3 py-2 text-sm hover:bg-amber-50 border-b border-gray-50 last:border-0"
                  onmousedown={() => selectPerson(s, 'A')}
                >
                  <span class="font-medium">{s.givenName} {s.surname}</span>
                  {#if s.birthDate}<span class="text-xs text-ink-muted ml-1">(b. {s.birthDate})</span>{/if}
                </button>
              {/each}
            </div>
          {/if}
        {/if}
      </div>

      <!-- Person B -->
      <div class="relative">
        <label for="relationship-person-b" class="block text-xs font-medium text-ink-muted mb-1">{t('relationship.personB')}</label>
        {#if personB}
          <div class="flex items-center gap-2 px-3 py-2 text-sm border border-green-200 bg-green-50 rounded-lg">
            <span class="flex-1 text-green-800 font-medium truncate">{personDisplay(personB)}</span>
            <button onclick={() => clearPerson('B')} class="text-green-600 hover:text-red-500 text-xs font-bold flex-shrink-0">&times;</button>
          </div>
        {:else}
          <input
            id="relationship-person-b"
            type="text"
            bind:value={searchB}
            oninput={() => searchPersons(searchB, 'B')}
            onfocus={() => { if (suggestionsB.length > 0) showDropdownB = true; }}
            onblur={() => setTimeout(() => showDropdownB = false, 200)}
            placeholder={t('people.searchByName')}
            class="w-full px-3 py-2 text-sm border border-gray-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-amber-500/20 focus:border-amber-500"
            aria-label={t('people.searchByName')}
          />
          {#if showDropdownB && suggestionsB.length > 0}
            <div class="absolute z-10 w-full mt-1 bg-white border border-gray-200 rounded-lg shadow-lg max-h-48 overflow-y-auto">
              {#each suggestionsB as s}
                <button
                  class="w-full text-left px-3 py-2 text-sm hover:bg-amber-50 border-b border-gray-50 last:border-0"
                  onmousedown={() => selectPerson(s, 'B')}
                >
                  <span class="font-medium">{s.givenName} {s.surname}</span>
                  {#if s.birthDate}<span class="text-xs text-ink-muted ml-1">(b. {s.birthDate})</span>{/if}
                </button>
              {/each}
            </div>
          {/if}
        {/if}
      </div>
    </div>

    <button
      onclick={calculateRelationship}
      disabled={!personA || !personB || relLoading}
      class="px-4 py-2 text-sm font-medium btn-accent text-white rounded-lg transition-colors disabled:opacity-40 disabled:cursor-not-allowed"
     aria-label={t('common.actions')}>
      {relLoading ? 'Calculating...' : 'Calculate Relationship'}
    </button>

    {#if relResult}
      <div class="mt-4 p-4 rounded-lg border {relResult.includes('No common ancestor') || relResult.includes('Error') ? 'bg-yellow-50 border-yellow-200' : 'bg-blue-50 border-blue-200'}">
        <p class="text-sm font-medium {relResult.includes('No common ancestor') || relResult.includes('Error') ? 'text-yellow-800' : 'text-blue-900'}">
          {relResult}
        </p>
      </div>
    {/if}
  </div>
</div>
