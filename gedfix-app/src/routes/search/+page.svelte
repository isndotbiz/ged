<script lang="ts">
  import { getPersons, getSources, getFamilies, getPerson } from '$lib/db';
  import type { Person, Source, Family } from '$lib/types';

  let query = $state('');
  let personResults = $state<Person[]>([]);
  let sourceResults = $state<Source[]>([]);
  let familyResults = $state<{ family: Family; partner1: Person | null; partner2: Person | null }[]>([]);
  let searching = $state(false);
  let hasSearched = $state(false);

  let debounceTimer: ReturnType<typeof setTimeout>;

  function onInput() {
    clearTimeout(debounceTimer);
    if (!query.trim()) {
      personResults = [];
      sourceResults = [];
      familyResults = [];
      hasSearched = false;
      return;
    }
    debounceTimer = setTimeout(doSearch, 250);
  }

  async function doSearch() {
    if (!query.trim()) return;
    searching = true;

    const q = query.trim().toLowerCase();

    // Search persons
    personResults = await getPersons(query, 50);

    // Search sources
    const allSources = await getSources();
    sourceResults = allSources.filter(s =>
      s.title.toLowerCase().includes(q) ||
      s.author.toLowerCase().includes(q)
    ).slice(0, 30);

    // Search families
    const allFamilies = await getFamilies();
    const famResults: typeof familyResults = [];
    for (const f of allFamilies) {
      const p1 = f.partner1Xref ? await getPerson(f.partner1Xref) : null;
      const p2 = f.partner2Xref ? await getPerson(f.partner2Xref) : null;
      const n1 = p1 ? `${p1.givenName} ${p1.surname}`.toLowerCase() : '';
      const n2 = p2 ? `${p2.givenName} ${p2.surname}`.toLowerCase() : '';
      if (n1.includes(q) || n2.includes(q) || f.marriagePlace.toLowerCase().includes(q)) {
        famResults.push({ family: f, partner1: p1, partner2: p2 });
      }
      if (famResults.length >= 30) break;
    }
    familyResults = famResults;

    searching = false;
    hasSearched = true;
  }

  function formatDates(p: Person): string {
    const parts: string[] = [];
    if (p.birthDate) parts.push(`b. ${p.birthDate}`);
    if (p.deathDate) parts.push(`d. ${p.deathDate}`);
    return parts.join(' - ');
  }

  let totalResults = $derived(personResults.length + sourceResults.length + familyResults.length);
</script>

<div class="p-8 max-w-3xl animate-fade-in">
  <div class="mb-6">
    <h1 class="text-2xl font-bold tracking-tight" style="font-family: var(--font-serif); color: var(--ink);">Search</h1>
    <p class="text-sm text-ink-muted mt-1">Search across all data</p>
  </div>

  <div class="mb-6">
    <input
      type="text"
      placeholder="Search people, sources, families..."
      bind:value={query}
      oninput={onInput}
      class="w-full px-4 py-3 text-base rounded-xl arch-input transition-all"
    />
  </div>

  {#if searching}
    <div class="text-sm text-ink-faint py-4 text-center">Searching...</div>
  {:else if hasSearched}
    <div class="text-xs text-ink-faint mb-4">{totalResults} results</div>

    <!-- People -->
    {#if personResults.length > 0}
      <div class="mb-6">
        <h2 class="text-xs font-semibold text-ink-faint uppercase tracking-wider mb-2">People ({personResults.length})</h2>
        <div class="arch-card rounded-xl divide-y arch-card-divide">
          {#each personResults as p}
            <a href="/people/{encodeURIComponent(p.xref)}" class="flex items-center gap-3 px-4 py-2.5 hover:bg-black/[0.02] transition-colors" style="color: var(--ink); text-decoration: none;">
              <div class="w-7 h-7 rounded-full flex items-center justify-center text-white text-[10px] font-semibold shrink-0"
                style="background: {p.sex === 'F' ? '#D94A8C' : '#4A90D9'}">
                {(p.givenName?.[0] ?? '') + (p.surname?.[0] ?? '')}
              </div>
              <div class="flex-1 min-w-0">
                <div class="text-sm font-medium text-ink truncate">{p.givenName} {p.surname}</div>
                <div class="text-xs text-ink-muted">{formatDates(p)}</div>
              </div>
            </a>
          {/each}
        </div>
      </div>
    {/if}

    <!-- Sources -->
    {#if sourceResults.length > 0}
      <div class="mb-6">
        <h2 class="text-xs font-semibold text-ink-faint uppercase tracking-wider mb-2">Sources ({sourceResults.length})</h2>
        <div class="arch-card rounded-xl divide-y arch-card-divide">
          {#each sourceResults as s}
            <div class="px-4 py-2.5">
              <div class="text-sm font-medium text-ink">{s.title || '(untitled)'}</div>
              {#if s.author}<div class="text-xs text-ink-muted">by {s.author}</div>{/if}
              {#if s.publisher}<div class="text-xs text-ink-faint">{s.publisher}</div>{/if}
            </div>
          {/each}
        </div>
      </div>
    {/if}

    <!-- Families -->
    {#if familyResults.length > 0}
      <div class="mb-6">
        <h2 class="text-xs font-semibold text-ink-faint uppercase tracking-wider mb-2">Families ({familyResults.length})</h2>
        <div class="arch-card rounded-xl divide-y arch-card-divide">
          {#each familyResults as r}
            <div class="px-4 py-2.5 flex items-center gap-2">
              <span class="text-sm text-ink">
                {r.partner1 ? `${r.partner1.givenName} ${r.partner1.surname}` : '(unknown)'}
              </span>
              <span class="text-xs text-ink-faint">&</span>
              <span class="text-sm text-ink">
                {r.partner2 ? `${r.partner2.givenName} ${r.partner2.surname}` : '(unknown)'}
              </span>
              {#if r.family.marriageDate}
                <span class="text-xs text-ink-faint ml-auto">m. {r.family.marriageDate}</span>
              {/if}
            </div>
          {/each}
        </div>
      </div>
    {/if}

    {#if totalResults === 0}
      <div class="text-sm text-ink-faint py-8 text-center">No results found for "{query}"</div>
    {/if}
  {:else}
    <div class="text-sm text-ink-faint py-16 text-center">
      Start typing to search across people, sources, and families
    </div>
  {/if}
</div>
