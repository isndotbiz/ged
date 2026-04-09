<script lang="ts">
  import { t } from '$lib/i18n';
  import { getPersons, getSources, getPlaces, searchNotes, insertBookmark, isBookmarked } from '$lib/db';
  import type { Person, Source, Place, ResearchNote } from '$lib/types';

  let query = $state('');
  let searching = $state(false);
  let hasSearched = $state(false);
  let peopleResults = $state<Person[]>([]);
  let sourceResults = $state<Source[]>([]);
  let noteResults = $state<ResearchNote[]>([]);
  let placeResults = $state<Place[]>([]);
  let bookmarkSaving = $state<Record<string, boolean>>({});

  let debounceTimer: ReturnType<typeof setTimeout>;

  function onInput() {
    clearTimeout(debounceTimer);
    if (!query.trim()) {
      hasSearched = false;
      peopleResults = [];
      sourceResults = [];
      noteResults = [];
      placeResults = [];
      return;
    }
    debounceTimer = setTimeout(doSearch, 250);
  }

  async function doSearch() {
    const q = query.trim().toLowerCase();
    if (!q) return;
    searching = true;

    peopleResults = await getPersons(query, 80);

    const allSources = await getSources();
    sourceResults = allSources
      .filter((s) => (s.title || '').toLowerCase().includes(q))
      .slice(0, 50);

    noteResults = (await searchNotes(query)).slice(0, 50);

    const allPlaces = await getPlaces();
    placeResults = allPlaces
      .filter((p) => (p.name || '').toLowerCase().includes(q))
      .slice(0, 50);

    hasSearched = true;
    searching = false;
  }

  function formatDates(p: Person): string {
    const parts: string[] = [];
    if (p.birthDate) parts.push(`b. ${p.birthDate}`);
    if (p.deathDate) parts.push(`d. ${p.deathDate}`);
    return parts.join(' - ');
  }

  let totalResults = $derived(peopleResults.length + sourceResults.length + noteResults.length + placeResults.length);

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

<div class="p-8 max-w-4xl animate-fade-in" aria-busy={searching}>
  <div class="mb-6">
    <h1 class="text-2xl font-bold tracking-tight" style="font-family: var(--font-serif); color: var(--ink);">{t('common.search')}</h1>
    <p class="text-sm text-ink-muted mt-1">{t('search.subtitle')}</p>
  </div>

  <div class="mb-6">
    <input
      id="global-search-input"
      type="text"
      placeholder={t('search.placeholder')}
      bind:value={query}
      oninput={onInput}
      class="w-full px-4 py-3 text-base rounded-xl arch-input transition-all"
      aria-label={t('search.placeholder')}
    />
  </div>

  {#if searching}
    <div class="text-sm text-ink-faint py-4 text-center">{t('search.searching')}</div>
  {:else if hasSearched}
    <div class="text-xs text-ink-faint mb-4" aria-live="polite">
      {t('search.resultsCount', { count: totalResults })}
    </div>

    {#if totalResults === 0}
      <div class="text-sm text-ink-faint py-8 text-center">{t('search.noResultsFor', { query })}</div>
    {:else}
      <div class="grid gap-4">
        <section class="arch-card rounded-xl p-4">
          <h2 class="text-sm font-semibold mb-3">{t('common.person')}</h2>
          {#if peopleResults.length === 0}
            <p class="text-xs text-ink-faint">{t('search.noResultsFor', { query })}</p>
          {:else}
            <div class="divide-y arch-card-divide">
              {#each peopleResults as person}
                <div class="flex items-center gap-3 py-2.5">
                  <div class="w-7 h-7 rounded-full flex items-center justify-center text-white text-[10px] font-semibold shrink-0"
                    style="background: {person.sex === 'F' ? '#D94A8C' : '#4A90D9'}">
                    {(person.givenName?.[0] ?? '') + (person.surname?.[0] ?? '')}
                  </div>
                  <div class="flex-1 min-w-0">
                    <a href="/people/{encodeURIComponent(person.xref)}" class="text-sm font-medium text-ink truncate block hover:underline" style="color: var(--ink); text-decoration: none;">{person.givenName} {person.surname}</a>
                    <div class="text-xs text-ink-muted">{formatDates(person)}</div>
                  </div>
                  <button class="btn-outline text-[10px] px-2 py-1" onclick={() => quickBookmark(person)} disabled={bookmarkSaving[person.xref]}>
                    {bookmarkSaving[person.xref] ? '...' : t('common.bookmark')}
                  </button>
                </div>
              {/each}
            </div>
          {/if}
        </section>

        <section class="arch-card rounded-xl p-4">
          <h2 class="text-sm font-semibold mb-3">{t('search.sources')}</h2>
          {#if sourceResults.length === 0}
            <p class="text-xs text-ink-faint">{t('search.noResultsFor', { query })}</p>
          {:else}
            <div class="divide-y arch-card-divide">
              {#each sourceResults as source}
                <a href="/sources" class="block py-2.5 hover:underline" style="color: var(--ink); text-decoration: none;">
                  <div class="text-sm font-medium">{source.title || '(untitled)'}</div>
                  {#if source.author}<div class="text-xs text-ink-muted">{source.author}</div>{/if}
                </a>
              {/each}
            </div>
          {/if}
        </section>

        <section class="arch-card rounded-xl p-4">
          <h2 class="text-sm font-semibold mb-3">{t('search.notes')}</h2>
          {#if noteResults.length === 0}
            <p class="text-xs text-ink-faint">{t('search.noResultsFor', { query })}</p>
          {:else}
            <div class="divide-y arch-card-divide">
              {#each noteResults as note}
                <a href="/notes" class="block py-2.5 hover:underline" style="color: var(--ink); text-decoration: none;">
                  <div class="text-sm font-medium">{note.title || t('common.note')}</div>
                  <div class="text-xs text-ink-muted truncate">{note.content}</div>
                </a>
              {/each}
            </div>
          {/if}
        </section>

        <section class="arch-card rounded-xl p-4">
          <h2 class="text-sm font-semibold mb-3">{t('search.places')}</h2>
          {#if placeResults.length === 0}
            <p class="text-xs text-ink-faint">{t('search.noResultsFor', { query })}</p>
          {:else}
            <div class="divide-y arch-card-divide">
              {#each placeResults as place}
                <a href="/places" class="block py-2.5 hover:underline" style="color: var(--ink); text-decoration: none;">
                  <div class="text-sm font-medium">{place.name}</div>
                  <div class="text-xs text-ink-muted">{place.eventCount} {t('common.events')}</div>
                </a>
              {/each}
            </div>
          {/if}
        </section>
      </div>
    {/if}
  {:else}
    <div class="text-sm text-ink-faint py-16 text-center">
      {t('search.startTyping')}
    </div>
  {/if}
</div>
