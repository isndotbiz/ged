<script lang="ts">
  import { t } from '$lib/i18n';
  import { getResearchLogs, insertResearchLog, deleteResearchLog } from '$lib/db';
  import type { ResearchLogEntry } from '$lib/types';

  let entries = $state<ResearchLogEntry[]>([]);
  let showEditor = $state(false);
  let filterType = $state<string | null>(null);

  // Editor fields
  let repository = $state('');
  let searchTerms = $state('');
  let recordsViewed = $state('');
  let conclusion = $state('');
  let resultType = $state<'POSITIVE' | 'NEGATIVE' | 'INCONCLUSIVE'>('NEGATIVE');
  let searchDate = $state('');

  async function load() { entries = await getResearchLogs(); }

  async function save() {
    await insertResearchLog({
      personXref: '', repository, searchTerms, recordsViewed, conclusion,
      sourceXref: '', resultType, searchDate, createdAt: new Date().toISOString()
    });
    showEditor = false;
    repository = ''; searchTerms = ''; recordsViewed = ''; conclusion = ''; searchDate = '';
    resultType = 'NEGATIVE';
    await load();
  }

  async function remove(id: number) { await deleteResearchLog(id); await load(); }

  let filtered = $derived(filterType ? entries.filter(e => e.resultType === filterType) : entries);

  $effect(() => { load(); });
</script>

<div class="p-8 max-w-4xl animate-fade-in">
  <div class="flex items-center justify-between mb-8">
    <div>
      <h1 class="text-2xl font-bold tracking-tight" style="font-family: var(--font-serif); color: var(--ink);">{t('nav.researchLog')}</h1>
      <p class="text-sm text-ink-muted mt-1">Track searches and conclusions per the Genealogical Proof Standard</p>
    </div>
    <button onclick={() => showEditor = !showEditor} class="px-4 py-2 text-sm font-medium btn-accent">
      {showEditor ? t('common.cancel') : t('research.addEntry')}
    </button>
  </div>

  <!-- Summary -->
  <div class="flex gap-3 mb-6">
    <div class="px-3 py-1.5 rounded-lg bg-green-50 text-green-700 text-sm font-medium">{entries.filter(e => e.resultType === 'POSITIVE').length} Positive</div>
    <div class="px-3 py-1.5 rounded-lg bg-orange-50 text-orange-700 text-sm font-medium">{entries.filter(e => e.resultType === 'NEGATIVE').length} Negative</div>
    <div class="px-3 py-1.5 rounded-lg text-sm font-medium" style="background: var(--parchment); color: var(--ink-light);">{entries.filter(e => e.resultType === 'INCONCLUSIVE').length} Inconclusive</div>
  </div>

  <!-- Filter -->
  <div class="flex gap-2 mb-6">
    <button onclick={() => filterType = null} class="px-3 py-1 text-xs rounded-full {filterType === null ? 'btn-filter-active' : 'btn-filter'}">All</button>
    {#each ['POSITIVE', 'NEGATIVE', 'INCONCLUSIVE'] as rt}
      <button onclick={() => filterType = filterType === rt ? null : rt} class="px-3 py-1 text-xs rounded-full {filterType === rt ? 'btn-filter-active' : 'btn-filter'}">{rt.charAt(0) + rt.slice(1).toLowerCase()}</button>
    {/each}
  </div>

  <!-- Editor -->
  {#if showEditor}
    <div class="arch-card rounded-xl p-6 mb-6">
      <div class="grid grid-cols-2 gap-4 mb-4">
        <input bind:value={repository} placeholder={t('research.repositorySource')} class="px-3 py-2 text-sm arch-input"  aria-label={t('research.repositorySource')} />
        <input bind:value={searchDate} placeholder={t('research.searchDate')} class="px-3 py-2 text-sm arch-input"  aria-label={t('research.searchDate')} />
      </div>
      <input bind:value={searchTerms} placeholder={t('research.searchTerms')} class="w-full px-3 py-2 text-sm arch-input mb-4"  aria-label={t('research.searchTerms')} />
      <textarea bind:value={recordsViewed} placeholder={t('research.recordsViewed')} class="w-full px-3 py-2 text-sm arch-input mb-4 h-20"></textarea>
      <textarea bind:value={conclusion} placeholder={t('research.conclusion')} class="w-full px-3 py-2 text-sm arch-input mb-4 h-20"></textarea>
      <div class="flex gap-2 mb-4">
        {#each (['POSITIVE', 'NEGATIVE', 'INCONCLUSIVE'] as const) as rt}
          <button onclick={() => resultType = rt} class="px-3 py-1.5 text-xs rounded-lg {resultType === rt ? 'btn-filter-active' : 'btn-filter'}">{rt.charAt(0) + rt.slice(1).toLowerCase()}</button>
        {/each}
      </div>
      <button onclick={save} class="px-4 py-2 text-sm font-medium btn-accent" aria-label={t('common.actions')}>{t('common.save')}</button>
    </div>
  {/if}

  <!-- Entries -->
  {#if filtered.length === 0}
    <div class="arch-card rounded-xl p-8 text-center">
      <p class="text-ink-muted text-sm">{t('research.noEntries')}</p>
    </div>
  {:else}
    <div class="space-y-3">
      {#each filtered as entry}
        <div class="arch-card rounded-xl p-4">
          <div class="flex items-center justify-between mb-2">
            <div class="flex items-center gap-2">
              <span class="px-2 py-0.5 text-[10px] font-semibold rounded-full {entry.resultType === 'POSITIVE' ? 'bg-green-100 text-green-700' : entry.resultType === 'NEGATIVE' ? 'bg-orange-100 text-orange-700' : 'btn-filter'}">{entry.resultType}</span>
              {#if entry.searchDate}<span class="text-[10px] text-ink-faint">{entry.searchDate}</span>{/if}
            </div>
            <button onclick={() => remove(entry.id)} class="text-[10px] text-red-500 hover:text-red-700">{t('common.delete')}</button>
          </div>
          {#if entry.repository}<div class="text-sm font-medium text-ink">{entry.repository}</div>{/if}
          {#if entry.searchTerms}<div class="text-xs text-ink-light mt-1">Search: {entry.searchTerms}</div>{/if}
          {#if entry.recordsViewed}<div class="text-xs text-ink-muted mt-1">Records: {entry.recordsViewed}</div>{/if}
          {#if entry.conclusion}<div class="text-xs text-blue-600 mt-1">{entry.conclusion}</div>{/if}
        </div>
      {/each}
    </div>
  {/if}
</div>
