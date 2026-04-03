<script lang="ts">
  import { t } from '$lib/i18n';
  import { getPersons, getPerson, getDb, insertBookmark, isBookmarked } from '$lib/db';
  import type { Person, Source, Family, Place, GedcomEvent, GedcomMedia, ResearchNote, AlternateName } from '$lib/types';

  let query = $state('');
  type SearchResult =
    | { type: 'Person'; score: number; person: Person; subtitle?: string }
    | { type: 'Source'; score: number; source: Source }
    | { type: 'Family'; score: number; family: Family; partner1: Person | null; partner2: Person | null }
    | { type: 'Place'; score: number; place: Place }
    | { type: 'Event'; score: number; event: GedcomEvent }
    | { type: 'Note'; score: number; note: ResearchNote }
    | { type: 'Media'; score: number; media: GedcomMedia };

  let results = $state<SearchResult[]>([]);
  let searching = $state(false);
  let hasSearched = $state(false);
  let bookmarkSaving = $state<Record<string, boolean>>({});

  let debounceTimer: ReturnType<typeof setTimeout>;

  function onInput() {
    clearTimeout(debounceTimer);
    if (!query.trim()) {
      results = [];
      hasSearched = false;
      return;
    }
    debounceTimer = setTimeout(doSearch, 250);
  }

  async function doSearch() {
    if (!query.trim()) return;
    searching = true;

    const q = query.trim().toLowerCase();
    const scoreMatch = (text: string) => {
      const t = text.toLowerCase();
      if (t === q) return 3;
      if (t.startsWith(q)) return 2;
      if (t.includes(q)) return 1;
      return 0;
    };

    const db = await getDb();
    const next: SearchResult[] = [];

    // People (includes alternate names via getPersons)
    const people = await getPersons(query, 80);
    for (const p of people) {
      let s = Math.max(scoreMatch(`${p.givenName} ${p.surname}`), scoreMatch(p.xref));
      if (s === 0) s = 1;
      next.push({ type: 'Person', score: s, person: p });
    }

    // Alternate names
    const altRows = await db.select<(AlternateName & { personGiven: string; personSurname: string })[]>(
      `SELECT a.*, p.givenName as personGiven, p.surname as personSurname
       FROM alternate_name a JOIN person p ON p.xref = a.personXref
       WHERE a.givenName LIKE $1 OR a.surname LIKE $1 OR a.nameType LIKE $1
       LIMIT 40`,
      [`%${q}%`]
    );
    for (const a of altRows) {
      const p = await getPerson(a.personXref);
      if (!p) continue;
      const s = Math.max(scoreMatch(`${a.givenName} ${a.surname}`), scoreMatch(a.nameType));
      next.push({ type: 'Person', score: s, person: p, subtitle: `${a.nameType}: ${a.givenName} ${a.surname}` });
    }

    // Sources
    const sources = await db.select<Source[]>(
      `SELECT * FROM source WHERE title LIKE $1 OR author LIKE $1 OR publisher LIKE $1 LIMIT 40`,
      [`%${q}%`]
    );
    for (const s of sources) {
      const score = Math.max(scoreMatch(s.title || ''), scoreMatch(s.author || ''), scoreMatch(s.publisher || ''));
      next.push({ type: 'Source', score, source: s });
    }

    // Families
    const families = await db.select<Family[]>(`SELECT * FROM family LIMIT 80`);
    for (const f of families) {
      const p1 = f.partner1Xref ? await getPerson(f.partner1Xref) : null;
      const p2 = f.partner2Xref ? await getPerson(f.partner2Xref) : null;
      const n1 = p1 ? `${p1.givenName} ${p1.surname}` : '';
      const n2 = p2 ? `${p2.givenName} ${p2.surname}` : '';
      const score = Math.max(scoreMatch(n1), scoreMatch(n2), scoreMatch(f.marriagePlace || ''));
      if (score > 0) next.push({ type: 'Family', score, family: f, partner1: p1, partner2: p2 });
    }

    // Places
    const places = await db.select<Place[]>(
      `SELECT * FROM place WHERE name LIKE $1 ORDER BY eventCount DESC LIMIT 50`,
      [`%${q}%`]
    );
    for (const p of places) {
      next.push({ type: 'Place', score: scoreMatch(p.name), place: p });
    }

    // Events
    const events = await db.select<GedcomEvent[]>(
      `SELECT * FROM event WHERE place LIKE $1 OR description LIKE $1 OR dateValue LIKE $1 LIMIT 50`,
      [`%${q}%`]
    );
    for (const e of events) {
      const score = Math.max(scoreMatch(e.place || ''), scoreMatch(e.description || ''), scoreMatch(e.dateValue || ''));
      next.push({ type: 'Event', score, event: e });
    }

    // Notes
    const notes = await db.select<ResearchNote[]>(
      `SELECT * FROM note WHERE title LIKE $1 OR content LIKE $1 LIMIT 50`,
      [`%${q}%`]
    );
    for (const n of notes) {
      const score = Math.max(scoreMatch(n.title || ''), scoreMatch(n.content || ''));
      next.push({ type: 'Note', score, note: n });
    }

    // Media
    const media = await db.select<GedcomMedia[]>(
      `SELECT * FROM media WHERE title LIKE $1 OR filePath LIKE $1 LIMIT 50`,
      [`%${q}%`]
    );
    for (const m of media) {
      const score = Math.max(scoreMatch(m.title || ''), scoreMatch(m.filePath || ''));
      next.push({ type: 'Media', score, media: m });
    }

    results = next
      .filter(r => r.score > 0)
      .sort((a, b) => b.score - a.score)
      .slice(0, 120);

    searching = false;
    hasSearched = true;
  }

  function formatDates(p: Person): string {
    const parts: string[] = [];
    if (p.birthDate) parts.push(`b. ${p.birthDate}`);
    if (p.deathDate) parts.push(`d. ${p.deathDate}`);
    return parts.join(' - ');
  }

  let totalResults = $derived(results.length);

  async function quickBookmark(person: Person) {
    if (!person?.xref) return;
    bookmarkSaving = { ...bookmarkSaving, [person.xref]: true };
    try {
      if (!(await isBookmarked(person.xref))) {
        await insertBookmark({
          personXref: person.xref,
          label: `${person.givenName} ${person.surname}`.trim(),
          category: 'Search',
          sortOrder: undefined,
          createdAt: new Date().toISOString(),
        });
      }
    } finally {
      bookmarkSaving = { ...bookmarkSaving, [person.xref]: false };
    }
  }
</script>

<div class="p-8 max-w-3xl animate-fade-in" aria-busy={searching}>
  <div class="mb-6">
    <h1 class="text-2xl font-bold tracking-tight" style="font-family: var(--font-serif); color: var(--ink);">{t('common.search')}</h1>
    <p class="text-sm text-ink-muted mt-1">{t('search.subtitle')}</p>
  </div>

  <div class="mb-6">
    <input
      type="text"
      placeholder={t('search.placeholder')}
      bind:value={query}
      oninput={onInput}
      class="w-full px-4 py-3 text-base rounded-xl arch-input transition-all"
     aria-label={t('search.placeholder')} />
  </div>

  {#if searching}
    <div class="text-sm text-ink-faint py-4 text-center">{t('search.searching')}</div>
  {:else if hasSearched}
    <div class="text-xs text-ink-faint mb-4" aria-live="polite">
      {t('search.resultsCount', { count: totalResults })}
    </div>

    {#if results.length > 0}
      <div class="arch-card rounded-xl divide-y arch-card-divide">
        {#each results as r}
          {#if r.type === 'Person'}
            <div class="flex items-center gap-3 px-4 py-2.5 hover:bg-black/[0.02] transition-colors">
              <div class="w-7 h-7 rounded-full flex items-center justify-center text-white text-[10px] font-semibold shrink-0"
                style="background: {r.person.sex === 'F' ? '#D94A8C' : '#4A90D9'}">
                {(r.person.givenName?.[0] ?? '') + (r.person.surname?.[0] ?? '')}
              </div>
              <div class="flex-1 min-w-0">
                <a href="/people/{encodeURIComponent(r.person.xref)}" class="text-sm font-medium text-ink truncate block hover:underline" style="color: var(--ink); text-decoration: none;">{r.person.givenName} {r.person.surname}</a>
                <div class="text-xs text-ink-muted">{r.subtitle || formatDates(r.person)}</div>
              </div>
              <button class="btn-outline text-[10px] px-2 py-1" onclick={() => quickBookmark(r.person)} disabled={bookmarkSaving[r.person.xref]}>
                {bookmarkSaving[r.person.xref] ? '...' : 'Bookmark'}
              </button>
              <span class="text-[10px] uppercase tracking-wider px-2 py-1 rounded-full" style="background: var(--parchment); color: var(--ink-muted);">{t('common.person')}</span>
            </div>
          {:else if r.type === 'Source'}
            <div class="px-4 py-2.5 flex items-start justify-between gap-3">
              <div>
                <div class="text-sm font-medium text-ink">{r.source.title || '(untitled)'}</div>
                {#if r.source.author}<div class="text-xs text-ink-muted">by {r.source.author}</div>{/if}
                {#if r.source.publisher}<div class="text-xs text-ink-faint">{r.source.publisher}</div>{/if}
              </div>
              <span class="text-[10px] uppercase tracking-wider px-2 py-1 rounded-full" style="background: var(--parchment); color: var(--ink-muted);">{t('common.source')}</span>
            </div>
          {:else if r.type === 'Family'}
            <div class="px-4 py-2.5 flex items-center gap-2">
              <div class="flex-1 min-w-0">
                <div class="text-sm text-ink">
                  {r.partner1 ? `${r.partner1.givenName} ${r.partner1.surname}` : '(unknown)'}
                  <span class="text-xs text-ink-faint mx-1">&</span>
                  {r.partner2 ? `${r.partner2.givenName} ${r.partner2.surname}` : '(unknown)'}
                </div>
                {#if r.family.marriageDate}
                  <div class="text-xs text-ink-faint">m. {r.family.marriageDate}</div>
                {/if}
              </div>
              <span class="text-[10px] uppercase tracking-wider px-2 py-1 rounded-full" style="background: var(--parchment); color: var(--ink-muted);">{t('common.family')}</span>
            </div>
          {:else if r.type === 'Place'}
            <div class="px-4 py-2.5 flex items-center justify-between gap-3">
              <div>
                <div class="text-sm text-ink">{r.place.name}</div>
                <div class="text-xs text-ink-faint">{r.place.eventCount} events</div>
              </div>
              <span class="text-[10px] uppercase tracking-wider px-2 py-1 rounded-full" style="background: var(--parchment); color: var(--ink-muted);">Place</span>
            </div>
          {:else if r.type === 'Event'}
            <div class="px-4 py-2.5 flex items-center justify-between gap-3">
              <div>
                <div class="text-sm text-ink">{r.event.eventType} {r.event.dateValue}</div>
                {#if r.event.place}<div class="text-xs text-ink-faint">{r.event.place}</div>{/if}
              </div>
              <span class="text-[10px] uppercase tracking-wider px-2 py-1 rounded-full" style="background: var(--parchment); color: var(--ink-muted);">{t('common.event')}</span>
            </div>
          {:else if r.type === 'Note'}
            <div class="px-4 py-2.5 flex items-center justify-between gap-3">
              <div class="min-w-0">
                <div class="text-sm text-ink truncate">{r.note.title || 'Note'}</div>
                <div class="text-xs text-ink-faint truncate">{r.note.content}</div>
              </div>
              <span class="text-[10px] uppercase tracking-wider px-2 py-1 rounded-full" style="background: var(--parchment); color: var(--ink-muted);">{t('common.note')}</span>
            </div>
          {:else if r.type === 'Media'}
            <div class="px-4 py-2.5 flex items-center justify-between gap-3">
              <div class="min-w-0">
                <div class="text-sm text-ink truncate">{r.media.title || 'Untitled media'}</div>
                <div class="text-xs text-ink-faint truncate">{r.media.filePath}</div>
              </div>
              <span class="text-[10px] uppercase tracking-wider px-2 py-1 rounded-full" style="background: var(--parchment); color: var(--ink-muted);">{t('common.media')}</span>
            </div>
          {/if}
        {/each}
      </div>
    {/if}

    {#if totalResults === 0}
      <div class="text-sm text-ink-faint py-8 text-center">{t('search.noResultsFor', { query })}</div>
    {/if}
  {:else}
    <div class="text-sm text-ink-faint py-16 text-center">
      {t('search.startTyping')}
    </div>
  {/if}
</div>
