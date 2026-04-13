<script lang="ts">
  import { t } from '$lib/i18n';
  import { getSources, getCitationsForSource, getDb, updateSourceType, classifySources } from '$lib/db';
  import type { Source, SourceType } from '$lib/types';

  let sources = $state<Source[]>([]);
  let search = $state('');
  let citationCounts = $state<Map<string, number>>(new Map());
  let expandedXref = $state<string | null>(null);
  let expandedCitations = $state<{ personXref: string; personName: string; page: string; quality: string }[]>([]);
  let loadingCitations = $state(false);
  let typeFilter = $state<SourceType | 'all'>('all');
  let reclassifying = $state(false);

  const sourceTypeLabels: Record<SourceType, { label: string; color: string }> = {
    online_tree: { label: 'Tree', color: '#F59E0B' },
    vital_record: { label: 'Vital', color: '#10B981' },
    census: { label: 'Census', color: '#3B82F6' },
    newspaper: { label: 'News', color: '#8B5CF6' },
    church_record: { label: 'Church', color: '#14B8A6' },
    military: { label: 'Military', color: '#F97316' },
    immigration: { label: 'Immig', color: '#06B6D4' },
    other: { label: 'Other', color: '#6B7280' },
    unknown: { label: 'Unclassified', color: '#9CA3AF' },
  };

  const sourceTypes: SourceType[] = ['online_tree', 'vital_record', 'census', 'newspaper', 'church_record', 'military', 'immigration', 'other', 'unknown'];

  async function handleTypeChange(xref: string, newType: SourceType) {
    await updateSourceType(xref, newType);
    await load();
  }

  async function reclassifyAll() {
    reclassifying = true;
    await classifySources();
    await load();
    reclassifying = false;
  }

  async function load() {
    try {
      const srcs = await getSources();
      const db = await getDb();
      sources = srcs;
      const rows = await db.select<{ sourceXref: string; cnt: number }[]>(
        'SELECT sourceXref, COUNT(*) as cnt FROM citation GROUP BY sourceXref'
      );
      const map = new Map<string, number>();
      for (const r of rows) map.set(r.sourceXref, r.cnt);
      citationCounts = map;
    } catch (e) {
      console.error('Failed to load sources:', e);
    }
  }

  let filtered = $derived(
    sources.filter(s => {
      if (typeFilter !== 'all' && s.sourceType !== typeFilter) return false;
      if (!search.trim()) return true;
      const q = search.toLowerCase();
      return s.title.toLowerCase().includes(q) || s.author.toLowerCase().includes(q);
    })
  );

  let sortedFiltered = $derived(
    [...filtered].sort((a, b) => (citationCounts.get(b.xref) ?? 0) - (citationCounts.get(a.xref) ?? 0))
  );

  let totalCitations = $derived(
    Array.from(citationCounts.values()).reduce((sum, c) => sum + c, 0)
  );

  async function toggleExpand(xref: string) {
    if (expandedXref === xref) {
      expandedXref = null;
      expandedCitations = [];
      return;
    }
    expandedXref = xref;
    loadingCitations = true;
    expandedCitations = [];
    try {
      expandedCitations = await getCitationsForSource(xref);
    } finally {
      loadingCitations = false;
    }
  }

  function qualityLabel(q: string): string {
    const labels: Record<string, string> = {
      '0': 'Unreliable', '1': 'Questionable', '2': 'Secondary', '3': 'Primary',
    };
    return labels[q] || q || 'Unrated';
  }

  function qualityColor(q: string): string {
    const colors: Record<string, string> = {
      '3': 'bg-green-100 text-green-700',
      '2': 'bg-blue-100 text-blue-600',
      '1': 'bg-amber-100 text-amber-700',
      '0': 'bg-red-100 text-red-600',
    };
    return colors[q] || 'bg-gray-100 text-gray-500';
  }

  let initialized = false;
  $effect(() => {
    if (initialized) return;
    initialized = true;
    load();
  });
</script>

<div class="p-8 max-w-4xl animate-fade-in">
  <h1 class="text-2xl font-bold tracking-tight" style="font-family: var(--font-serif); color: var(--ink);">{t('dashboard.sources')}</h1>
  <p class="text-sm text-ink-muted mt-1 mb-6">
    {sources.length} sources &middot; {totalCitations} citations
  </p>

  <div class="flex flex-wrap gap-2 mb-4">
    <button class="px-3 py-1 text-xs rounded-full" style="background: {typeFilter === 'all' ? 'var(--accent)' : 'var(--parchment)'}; color: {typeFilter === 'all' ? '#fff' : 'var(--ink-muted)'};" onclick={() => { typeFilter = 'all'; }}>All</button>
    {#each sourceTypes as st}
      {@const info = sourceTypeLabels[st]}
      {@const count = sources.filter(s => s.sourceType === st).length}
      {#if count > 0}
        <button class="px-3 py-1 text-xs rounded-full" style="background: {typeFilter === st ? info.color : 'var(--parchment)'}; color: {typeFilter === st ? '#fff' : 'var(--ink-muted)'};" onclick={() => { typeFilter = st; }}>{info.label} ({count})</button>
      {/if}
    {/each}
    <button class="px-3 py-1 text-xs rounded-full" style="background: var(--parchment); color: var(--ink-muted);" onclick={reclassifyAll} disabled={reclassifying}>{reclassifying ? 'Classifying...' : 'Reclassify All'}</button>
  </div>

  <input
    bind:value={search}
    placeholder={t('sources.searchPlaceholder')}
    class="w-full max-w-md px-3 py-2 text-sm rounded-lg mb-6 border-none outline-none transition-colors arch-input"
   aria-label={t('sources.searchPlaceholder')} />

  {#if sortedFiltered.length === 0}
    <div class="arch-card rounded-xl p-8 text-center">
      <p class="text-ink-muted text-sm">No sources found</p>
    </div>
  {:else}
    <div class="space-y-2">
      {#each sortedFiltered as source (source.xref)}
        {@const count = citationCounts.get(source.xref) ?? 0}
        {@const isExpanded = expandedXref === source.xref}

        <div class="arch-card rounded-xl overflow-hidden transition-shadow"
             style={isExpanded ? 'box-shadow: var(--shadow-lg, 0 4px 12px rgba(0,0,0,0.08));' : ''}>
          <!-- Source header row -->
          <button
            onclick={() => toggleExpand(source.xref)}
            class="w-full text-left px-4 py-3 flex items-start gap-3 hover:bg-black/[0.02] transition-colors"
          >
            <!-- Expand chevron -->
            <svg
              class="w-4 h-4 mt-0.5 shrink-0 transition-transform text-ink-faint"
              style="transform: rotate({isExpanded ? '90deg' : '0deg'});"
              viewBox="0 0 20 20" fill="currentColor"
            >
              <path fill-rule="evenodd" d="M7.21 14.77a.75.75 0 01.02-1.06L11.168 10 7.23 6.29a.75.75 0 111.04-1.08l4.5 4.25a.75.75 0 010 1.08l-4.5 4.25a.75.75 0 01-1.06-.02z" clip-rule="evenodd" />
            </svg>

            <div class="flex-1 min-w-0">
              <div class="flex items-center gap-2">
                <div class="font-medium text-sm" style="color: var(--ink);">
                  {source.title || '(Untitled)'}
                </div>
                <span class="shrink-0 text-[10px] px-1.5 py-0.5 rounded-full font-semibold" style="background: {sourceTypeLabels[source.sourceType || 'unknown'].color}22; color: {sourceTypeLabels[source.sourceType || 'unknown'].color};">{sourceTypeLabels[source.sourceType || 'unknown'].label}</span>
              </div>
              {#if source.author}
                <div class="text-xs text-ink-muted mt-0.5">By {source.author}</div>
              {/if}
              {#if source.publisher}
                <div class="text-xs text-ink-faint mt-0.5">{source.publisher}</div>
              {/if}
            </div>

            <!-- Citation count badge -->
            {#if count > 0}
              <span
                class="shrink-0 inline-flex items-center justify-center px-2 py-0.5 text-xs font-semibold rounded-full tabular-nums"
                style="background: var(--accent-subtle, rgba(180,130,60,0.12)); color: var(--accent, #b4823c);"
              >
                {count}
              </span>
            {:else}
              <span class="shrink-0 inline-flex items-center justify-center px-2 py-0.5 text-xs rounded-full tabular-nums"
                    style="background: var(--parchment); color: var(--ink-faint, #999);">
                0
              </span>
            {/if}
          </button>

          <!-- Expanded citation details -->
          {#if isExpanded}
            <div class="px-4 pb-4 pt-1" style="border-top: 1px solid var(--border-rule, #e5e5e5);">
              <div class="flex items-center gap-3 mb-2">
                <div class="text-[10px] text-ink-faint uppercase tracking-wider font-medium">{source.xref}</div>
                <select
                  class="text-xs px-2 py-1 rounded arch-input"
                  aria-label="Source type"
                  value={source.sourceType || 'unknown'}
                  onchange={(e) => handleTypeChange(source.xref, (e.target as HTMLSelectElement).value as SourceType)}
                >
                  {#each sourceTypes as st}
                    <option value={st}>{sourceTypeLabels[st].label}</option>
                  {/each}
                </select>
              </div>

              {#if loadingCitations}
                <div class="py-4 text-center text-xs text-ink-muted">Loading citations...</div>
              {:else if expandedCitations.length === 0}
                <div class="py-4 text-center text-xs text-ink-muted">No citations found for this source</div>
              {:else}
                <div class="text-xs text-ink-muted mb-2">
                  {expandedCitations.length} citation{expandedCitations.length === 1 ? '' : 's'} linking to {new Set(expandedCitations.map(c => c.personXref)).size} {new Set(expandedCitations.map(c => c.personXref)).size === 1 ? 'person' : 'people'}
                </div>
                <div class="rounded-lg overflow-hidden" style="border: 1px solid var(--border-subtle, #eee);">
                  <table class="w-full text-sm">
                    <thead>
                      <tr style="background: var(--parchment);">
                        <th class="text-left px-3 py-2 text-[10px] uppercase tracking-wider font-medium text-ink-faint">{t('common.person')}</th>
                        <th class="text-left px-3 py-2 text-[10px] uppercase tracking-wider font-medium text-ink-faint">{t('common.page')}</th>
                        <th class="text-left px-3 py-2 text-[10px] uppercase tracking-wider font-medium text-ink-faint">{t('common.quality')}</th>
                      </tr>
                    </thead>
                    <tbody class="divide-y" style="--tw-divide-color: var(--border-subtle, #eee);">
                      {#each expandedCitations as cit (cit.personXref + cit.page)}
                        <tr class="hover:bg-black/[0.015] transition-colors">
                          <td class="px-3 py-2">
                            <div class="font-medium text-xs" style="color: var(--ink);">{cit.personName || cit.personXref}</div>
                            {#if cit.personName}
                              <div class="text-[10px] text-ink-faint">{cit.personXref}</div>
                            {/if}
                          </td>
                          <td class="px-3 py-2 text-xs text-ink-muted">
                            {cit.page || '--'}
                          </td>
                          <td class="px-3 py-2">
                            <span class="text-[10px] px-1.5 py-0.5 rounded-full font-medium {qualityColor(cit.quality)}">
                              {qualityLabel(cit.quality)}
                            </span>
                          </td>
                        </tr>
                      {/each}
                    </tbody>
                  </table>
                </div>
              {/if}
            </div>
          {/if}
        </div>
      {/each}
    </div>
  {/if}
</div>
