<script lang="ts">
  import { t } from '$lib/i18n';
  import {
    getProposals, approveProposal, rejectProposal, undoChange,
    getAgentRuns, getChangeLog, getQualityRules, toggleQualityRule,
    getPerson, getPendingProposalCount, getPersons
  } from '$lib/db';
  import { runResearchAgent, runBatchResearch, findResearchCandidates } from '$lib/research-agent';
  import { findSources, batchFindSources } from '$lib/source-finder';
  import { appStats } from '$lib/stores';
  import type { Proposal, AgentRun, ChangeLogEntry, QualityRule, Person } from '$lib/types';

  // --- State ---
  let activeTab = $state<'pending' | 'history' | 'rules'>('pending');
  let proposals = $state<Proposal[]>([]);
  let changeLog = $state<ChangeLogEntry[]>([]);
  let qualityRules = $state<QualityRule[]>([]);
  let agentRuns = $state<AgentRun[]>([]);
  let personCache = $state<Map<string, Person>>(new Map());

  // Stats
  let pendingCount = $state(0);
  let approvedCount = $state(0);
  let rejectedCount = $state(0);
  let totalRuns = $state(0);

  // Research flow
  let showResearchSearch = $state(false);
  let researchQuery = $state('');
  let researchResults = $state<Person[]>([]);
  let isResearching = $state(false);
  let researchProgress = $state(0);
  let researchMessage = $state('');
  let searchTimeout: ReturnType<typeof setTimeout> | null = null;

  // Batch research
  let researchCandidates = $state<{ xref: string; name: string; missingFields: string[]; priority: number }[]>([]);
  let batchProgress = $state(0);
  let batchMessage = $state('');

  // Source finder
  let sourceSearching = $state(false);
  let sourceProgress = $state(0);
  let sourceMessage = $state('');

  // Loading
  let isLoading = $state(true);

  // --- Derived ---
  let pendingProposals = $derived(proposals.filter(p => p.status === 'pending'));
  let historyProposals = $derived(proposals.filter(p => p.status === 'approved' || p.status === 'rejected'));

  let groupedPending = $derived.by(() => {
    const groups = new Map<string, Proposal[]>();
    for (const p of pendingProposals) {
      const key = p.agentRunId;
      if (!groups.has(key)) groups.set(key, []);
      groups.get(key)!.push(p);
    }
    return groups;
  });

  let agentRunMap = $derived.by(() => {
    const map = new Map<string, AgentRun>();
    for (const r of agentRuns) map.set(r.runId, r);
    return map;
  });

  // --- Init ---
  async function loadData() {
    isLoading = true;
    try {
      const [p, runs, log, rules] = await Promise.all([
        getProposals(),
        getAgentRuns(),
        getChangeLog(),
        getQualityRules(),
      ]);
      proposals = p;
      agentRuns = runs;
      changeLog = log;
      qualityRules = rules;

      pendingCount = p.filter(x => x.status === 'pending').length;
      approvedCount = p.filter(x => x.status === 'approved').length;
      rejectedCount = p.filter(x => x.status === 'rejected').length;
      totalRuns = runs.length;

      // Cache person data for proposals
      const xrefs = new Set<string>();
      for (const prop of p) xrefs.add(prop.entityId);
      for (const run of runs) xrefs.add(run.personXref);
      const cache = new Map<string, Person>();
      for (const xref of xrefs) {
        if (xref) {
          try {
            const person = await getPerson(xref);
            if (person) cache.set(xref, person);
          } catch { /* person may not exist */ }
        }
      }
      personCache = cache;
    } catch (e) {
      console.error('Failed to load proposals data:', e);
    }
    isLoading = false;
  }

  $effect(() => { loadData(); });

  // --- Actions ---
  async function handleApprove(proposal: Proposal) {
    await approveProposal(proposal.id);
    await loadData();
  }

  async function handleReject(proposal: Proposal) {
    await rejectProposal(proposal.id);
    await loadData();
  }

  async function handleApproveGroup(runId: string) {
    const group = groupedPending.get(runId) || [];
    for (const p of group) await approveProposal(p.id);
    await loadData();
  }

  async function handleRejectGroup(runId: string) {
    const group = groupedPending.get(runId) || [];
    for (const p of group) await rejectProposal(p.id);
    await loadData();
  }

  async function handleUndo(entry: ChangeLogEntry) {
    await undoChange(entry.id);
    await loadData();
  }

  async function handleToggleRule(rule: QualityRule) {
    await toggleQualityRule(rule.id, rule.isActive ? 0 : 1);
    await loadData();
  }

  // --- Research Flow ---
  function openResearchSearch() {
    showResearchSearch = true;
    researchQuery = '';
    researchResults = [];
  }

  function closeResearchSearch() {
    showResearchSearch = false;
    researchQuery = '';
    researchResults = [];
  }

  function onSearchInput() {
    if (searchTimeout) clearTimeout(searchTimeout);
    if (researchQuery.length < 2) {
      researchResults = [];
      return;
    }
    searchTimeout = setTimeout(async () => {
      try {
        researchResults = await getPersons(researchQuery, 10);
      } catch { researchResults = []; }
    }, 300);
  }

  async function startResearch(xref: string) {
    showResearchSearch = false;
    isResearching = true;
    researchProgress = 0;
    researchMessage = 'Starting research agent...';
    try {
      await runResearchAgent(xref, (p) => {
        researchProgress = p.pct;
        researchMessage = p.message;
      });
      researchMessage = 'Research complete!';
      await loadData();
    } catch (e) {
      researchMessage = `Research failed: ${e}`;
    }
    setTimeout(() => { isResearching = false; }, 1500);
  }

  // --- Batch Research ---
  async function scanCandidates() {
    researchCandidates = await findResearchCandidates();
  }

  async function batchResearch() {
    const top50 = researchCandidates.slice(0, 50).map(c => c.xref);
    isResearching = true;
    batchProgress = 0;
    batchMessage = 'Starting batch research...';

    const result = await runBatchResearch(top50, (p) => {
      batchProgress = p.pct;
      batchMessage = p.message;
    });

    batchProgress = 100;
    batchMessage = `Done! ${result.totalProposals} proposals from ${top50.length} people`;
    isResearching = false;
    await loadData();
  }

  async function refreshProposals() {
    await loadData();
  }

  // --- Source Finder ---
  async function batchSourceSearch() {
    const unsourced = researchCandidates.filter(c => c.missingFields.includes('sources')).slice(0, 25);
    sourceSearching = true;
    sourceProgress = 0;
    sourceMessage = 'Starting source search...';

    await batchFindSources(unsourced.map(c => c.xref), (p) => {
      sourceProgress = p.pct;
      sourceMessage = p.message;
    });

    sourceSearching = false;
    await refreshProposals();
  }

  // --- Helpers ---
  function personName(xref: string): string {
    const p = personCache.get(xref);
    if (!p) return xref;
    return `${p.givenName} ${p.surname}`.trim() || xref;
  }

  function confidenceColor(c: number): string {
    if (c < 0.5) return 'var(--color-error, #dc2626)';
    if (c < 0.8) return 'var(--color-warning, #f59e0b)';
    return 'var(--color-validated, #16a34a)';
  }

  function confidencePercent(c: number): number {
    return Math.round(c * 100);
  }

  function formatDate(iso: string): string {
    if (!iso) return '';
    try {
      return new Date(iso).toLocaleDateString('en-US', { month: 'short', day: 'numeric', year: 'numeric', hour: '2-digit', minute: '2-digit' });
    } catch { return iso; }
  }

  function ruleTypeBadge(type: string): { bg: string; text: string } {
    switch (type) {
      case 'reject': return { bg: 'rgba(220,38,38,0.1)', text: '#dc2626' };
      case 'warn': return { bg: 'rgba(245,158,11,0.1)', text: '#d97706' };
      case 'require_evidence': return { bg: 'rgba(59,130,246,0.1)', text: '#2563eb' };
      default: return { bg: 'rgba(107,114,128,0.1)', text: '#6b7280' };
    }
  }

  function fieldLabel(name: string): string {
    return name
      .replace(/([A-Z])/g, ' $1')
      .replace(/^./, s => s.toUpperCase())
      .trim();
  }
</script>

<div class="p-8 max-w-5xl animate-fade-in">
  <!-- Header -->
  <div class="flex items-center justify-between mb-6">
    <div>
      <h1 class="text-2xl font-bold tracking-tight" style="font-family: var(--font-serif); color: var(--ink);">
        {t('proposals.title')}
      </h1>
      <p class="text-sm mt-1" style="color: var(--ink-muted);">
        {t('proposals.subtitle')}
      </p>
    </div>
    <button
      onclick={openResearchSearch}
      class="px-4 py-2 btn-accent text-white text-sm font-medium rounded-lg transition-colors"
     aria-label={t('common.actions')}>
      {t('proposals.researchPerson')}
    </button>
  </div>

  <!-- Research Search Overlay -->
  {#if showResearchSearch}
    <div class="fixed inset-0 z-50 flex items-start justify-center pt-32" style="background: rgba(0,0,0,0.4);">
      <div class="arch-card rounded-xl p-6 w-full max-w-md animate-fade-in" style="background: var(--paper);">
        <div class="flex items-center justify-between mb-4">
          <h2 class="text-lg font-semibold" style="font-family: var(--font-serif); color: var(--ink);">Search Person to Research</h2>
          <button onclick={closeResearchSearch} class="text-xl leading-none" style="color: var(--ink-faint);" aria-label={t('common.actions')}>&times;</button>
        </div>
        <input
          type="text"
          bind:value={researchQuery}
          oninput={onSearchInput}
          placeholder={t('proposals.typeAName')}
          class="w-full px-3 py-2 rounded-lg text-sm border"
          style="border-color: var(--ink-faint); font-family: var(--font-sans); color: var(--ink); background: var(--paper);"
         aria-label={t('proposals.typeAName')} />
        {#if researchResults.length > 0}
          <div class="mt-2 max-h-60 overflow-y-auto space-y-1">
            {#each researchResults as person}
              <button
                onclick={() => startResearch(person.xref)}
                class="w-full text-left px-3 py-2 rounded-lg text-sm transition-colors hover:opacity-80"
                style="background: var(--parchment); color: var(--ink); font-family: var(--font-sans);"
              >
                <span class="font-medium">{person.givenName} {person.surname}</span>
                {#if person.birthDate}
                  <span class="ml-2" style="color: var(--ink-muted);">b. {person.birthDate}</span>
                {/if}
              </button>
            {/each}
          </div>
        {:else if researchQuery.length >= 2}
          <p class="text-xs mt-2" style="color: var(--ink-muted);">No results found</p>
        {/if}
      </div>
    </div>
  {/if}

  <!-- Research Progress -->
  {#if isResearching}
    <div class="arch-card rounded-xl p-6 mb-6">
      <div class="text-xs uppercase tracking-wider mb-2" style="font-family: var(--font-serif); color: var(--ink-muted); font-weight: 600;">
        Research in Progress
      </div>
      <div class="h-1.5 rounded-full overflow-hidden mb-2" style="background: var(--parchment);">
        <div
          class="h-full rounded-full transition-all"
          style="width: {researchProgress}%; background: var(--accent); transition-duration: 300ms;"
        ></div>
      </div>
      <p class="text-xs" style="color: var(--ink-muted);">{researchMessage}</p>
    </div>
  {/if}

  <!-- Batch Research Section -->
  {#if !isResearching}
    <div class="arch-card rounded-xl p-5 mb-6">
      <div class="flex items-center justify-between">
        <div>
          <h3 class="text-sm font-semibold" style="font-family: var(--font-serif); color: var(--ink);">Batch Research</h3>
          <p class="text-xs mt-0.5" style="color: var(--ink-muted);">
            {#if researchCandidates.length > 0}
              {researchCandidates.length} people have missing data
            {:else}
              Click "Find Candidates" to scan your tree
            {/if}
          </p>
        </div>
        <div class="flex gap-2">
          <button onclick={scanCandidates} class="px-3 py-2 text-xs btn-secondary rounded-lg transition-colors">
            Find Candidates
          </button>
          {#if researchCandidates.length > 0}
            <button onclick={batchResearch} class="px-4 py-2 btn-accent text-white text-sm font-medium rounded-lg transition-colors">
              Research Top {Math.min(50, researchCandidates.length)}
            </button>
          {/if}
        </div>
      </div>
      {#if batchProgress > 0}
        <div class="mt-3">
          <div class="w-full h-1.5 rounded-full overflow-hidden" style="background: var(--parchment);">
            <div class="h-full rounded-full transition-all" style="width: {batchProgress}%; background: var(--accent); transition-duration: 300ms;"></div>
          </div>
          <p class="text-xs mt-1" style="color: var(--ink-muted);">{batchMessage}</p>
        </div>
      {/if}
    </div>
  {/if}

  <!-- Auto-Source Finder -->
  {#if !sourceSearching}
    <div class="arch-card rounded-xl p-5 mb-6">
      <div class="flex items-center justify-between">
        <div>
          <h3 class="text-sm font-semibold" style="font-family: var(--font-serif); color: var(--ink);">Auto-Source Finder</h3>
          <p class="text-xs text-ink-muted mt-0.5" style="color: var(--ink-muted);">Search FamilySearch (22.7B records) + historic newspapers for sources</p>
        </div>
        <div class="flex gap-2">
          {#if researchCandidates.length > 0}
            <button onclick={batchSourceSearch}
              class="px-4 py-2 btn-accent text-white text-sm font-medium rounded-lg transition-colors">
              Find Sources for Top {Math.min(25, researchCandidates.filter(c => c.missingFields.includes('sources')).length)}
            </button>
          {/if}
        </div>
      </div>
      {#if sourceProgress > 0}
        <div class="mt-3">
          <div class="w-full h-1.5 rounded-full overflow-hidden" style="background: var(--parchment);">
            <div class="h-full rounded-full transition-all duration-300" style="width: {sourceProgress}%; background: var(--accent);"></div>
          </div>
          <p class="text-xs mt-1" style="color: var(--ink-muted);">{sourceMessage}</p>
        </div>
      {/if}
    </div>
  {/if}

  <!-- Stats Bar -->
  <div class="flex gap-3 mb-6">
    <div class="arch-card rounded-xl px-5 py-3 text-center">
      <div class="text-xl font-bold" style="color: #d97706;">{pendingCount}</div>
      <div class="text-xs" style="color: var(--ink-muted);">{t('common.pending')}</div>
    </div>
    <div class="arch-card rounded-xl px-5 py-3 text-center">
      <div class="text-xl font-bold" style="color: #16a34a;">{approvedCount}</div>
      <div class="text-xs" style="color: var(--ink-muted);">{t('proposals.approved')}</div>
    </div>
    <div class="arch-card rounded-xl px-5 py-3 text-center">
      <div class="text-xl font-bold" style="color: #dc2626;">{rejectedCount}</div>
      <div class="text-xs" style="color: var(--ink-muted);">{t('proposals.rejected')}</div>
    </div>
    <div class="arch-card rounded-xl px-5 py-3 text-center">
      <div class="text-xl font-bold" style="color: var(--ink);">{totalRuns}</div>
      <div class="text-xs" style="color: var(--ink-muted);">{t('proposals.agentRuns')}</div>
    </div>
  </div>

  <!-- Tabs -->
  <div class="flex gap-1 mb-6" style="border-bottom: 1px solid var(--ink-faint);">
    <button
      onclick={() => { activeTab = 'pending'; }}
      class="px-4 py-2 text-sm font-medium transition-colors rounded-t-lg"
      style="{activeTab === 'pending'
        ? 'background: var(--paper); color: var(--ink); border: 1px solid var(--ink-faint); border-bottom: 1px solid var(--paper); margin-bottom: -1px;'
        : 'color: var(--ink-muted); background: transparent;'}"
    >
      Pending
      {#if pendingCount > 0}
        <span class="ml-1.5 text-xs px-1.5 py-0.5 rounded-full" style="background: rgba(217,119,6,0.15); color: #d97706;">{pendingCount}</span>
      {/if}
    </button>
    <button
      onclick={() => { activeTab = 'history'; }}
      class="px-4 py-2 text-sm font-medium transition-colors rounded-t-lg"
      style="{activeTab === 'history'
        ? 'background: var(--paper); color: var(--ink); border: 1px solid var(--ink-faint); border-bottom: 1px solid var(--paper); margin-bottom: -1px;'
        : 'color: var(--ink-muted); background: transparent;'}"
    >
      History
    </button>
    <button
      onclick={() => { activeTab = 'rules'; }}
      class="px-4 py-2 text-sm font-medium transition-colors rounded-t-lg"
      style="{activeTab === 'rules'
        ? 'background: var(--paper); color: var(--ink); border: 1px solid var(--ink-faint); border-bottom: 1px solid var(--paper); margin-bottom: -1px;'
        : 'color: var(--ink-muted); background: transparent;'}"
    >
      Rules
    </button>
  </div>

  <!-- Loading -->
  {#if isLoading}
    <div class="text-center py-16">
      <p class="text-sm" style="color: var(--ink-muted);">Loading proposals...</p>
    </div>

  <!-- Pending Tab -->
  {:else if activeTab === 'pending'}
    {#if pendingProposals.length === 0}
      <div class="text-center py-16">
        <div class="text-4xl mb-3" style="color: var(--ink-faint);">&#10003;</div>
        <p class="text-sm" style="color: var(--ink-muted);">{t('proposals.noPending')}</p>
      </div>
    {:else}
      <div class="space-y-6">
        {#each [...groupedPending] as [runId, groupProposals]}
          {@const run = agentRunMap.get(runId)}
          <div class="arch-card rounded-xl overflow-hidden">
            <!-- Group Header -->
            <div class="px-5 py-3 flex items-center justify-between" style="background: var(--parchment); border-bottom: 1px solid var(--ink-faint);">
              <div>
                <div class="text-sm font-semibold" style="color: var(--ink); font-family: var(--font-serif);">
                  {#if run}
                    {run.provider}/{run.model}
                    <span class="font-normal ml-2" style="color: var(--ink-muted);">
                      for {personName(run.personXref)}
                    </span>
                  {:else}
                    Run {runId.slice(0, 8)}
                  {/if}
                </div>
                {#if run}
                  <div class="text-xs mt-0.5" style="color: var(--ink-muted); font-family: var(--font-mono);">
                    {formatDate(run.startedAt)} &middot; {groupProposals.length} proposals
                  </div>
                {/if}
              </div>
              <div class="flex gap-2">
                <button
                  onclick={() => handleApproveGroup(runId)}
                  class="px-3 py-1.5 text-xs font-medium rounded-lg text-white transition-colors"
                  style="background: var(--color-validated, #16a34a);"
                >Approve All</button>
                <button
                  onclick={() => handleRejectGroup(runId)}
                  class="px-3 py-1.5 text-xs font-medium rounded-lg transition-colors"
                  style="background: rgba(220,38,38,0.1); color: #dc2626;"
                >Reject All</button>
              </div>
            </div>

            <!-- Proposal Cards -->
            <div class="divide-y" style="border-color: var(--ink-faint);">
              {#each groupProposals as proposal}
                <div class="px-5 py-4">
                  <div class="flex items-start justify-between gap-4">
                    <div class="flex-1 min-w-0">
                      <!-- Field Label -->
                      <div class="text-xs uppercase tracking-wider mb-1.5" style="color: var(--ink-muted); font-weight: 600; font-family: var(--font-sans);">
                        {fieldLabel(proposal.fieldName)}
                      </div>

                      <!-- Diff Display -->
                      <div class="flex items-center gap-2 mb-2 text-sm">
                        {#if proposal.oldValue}
                          <span style="color: #b91c1c; text-decoration: line-through; opacity: 0.7;">{proposal.oldValue}</span>
                          <span style="color: var(--ink-faint);">&#8594;</span>
                        {/if}
                        <span class="font-semibold" style="color: #15803d;">{proposal.newValue}</span>
                      </div>

                      <!-- Confidence Bar -->
                      <div class="flex items-center gap-2 mb-2">
                        <div class="flex-1 h-1 rounded-full overflow-hidden" style="background: var(--parchment); max-width: 120px;">
                          <div
                            class="h-full rounded-full"
                            style="width: {confidencePercent(proposal.confidence)}%; background: {confidenceColor(proposal.confidence)};"
                          ></div>
                        </div>
                        <span class="text-xs tabular-nums" style="color: var(--ink-muted); font-family: var(--font-mono);">
                          {confidencePercent(proposal.confidence)}%
                        </span>
                      </div>

                      <!-- Reasoning -->
                      <p class="text-xs leading-relaxed" style="color: var(--ink-muted);">{proposal.reasoning}</p>

                      <!-- Evidence -->
                      {#if proposal.evidenceSource}
                        <div class="text-xs mt-1.5 flex items-center gap-1" style="color: var(--accent);">
                          <svg class="w-3 h-3 shrink-0" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24">
                            <path stroke-linecap="round" stroke-linejoin="round" d="M12 6.253v13m0-13C10.832 5.477 9.246 5 7.5 5S4.168 5.477 3 6.253v13C4.168 18.477 5.754 18 7.5 18s3.332.477 4.5 1.253m0-13C13.168 5.477 14.754 5 16.5 5c1.747 0 3.332.477 4.5 1.253v13C19.832 18.477 18.247 18 16.5 18c-1.746 0-3.332.477-4.5 1.253" />
                          </svg>
                          <span>{proposal.evidenceSource}</span>
                        </div>
                      {/if}
                    </div>

                    <!-- Actions -->
                    <div class="flex gap-1.5 shrink-0">
                      <button
                        onclick={() => handleApprove(proposal)}
                        class="px-3 py-1.5 text-xs font-medium rounded-lg text-white transition-colors"
                        style="background: var(--color-validated, #16a34a);"
                        title={t('proposals.approve')}
                      >{t('proposals.approve')}</button>
                      <button
                        onclick={() => handleReject(proposal)}
                        class="px-3 py-1.5 text-xs font-medium rounded-lg transition-colors"
                        style="background: rgba(220,38,38,0.1); color: #dc2626;"
                        title={t('proposals.reject')}
                      >{t('proposals.reject')}</button>
                    </div>
                  </div>
                </div>
              {/each}
            </div>
          </div>
        {/each}
      </div>
    {/if}

  <!-- History Tab -->
  {:else if activeTab === 'history'}
    {#if changeLog.length === 0}
      <div class="text-center py-16">
        <p class="text-sm" style="color: var(--ink-muted);">No change history yet.</p>
      </div>
    {:else}
      <div class="space-y-2">
        {#each changeLog as entry}
          <div class="arch-card rounded-xl px-5 py-3.5">
            <div class="flex items-center justify-between">
              <div class="flex-1 min-w-0">
                <div class="flex items-center gap-2 text-sm">
                  <span class="text-xs uppercase tracking-wider font-semibold" style="color: var(--ink-muted);">
                    {fieldLabel(entry.fieldName)}
                  </span>
                  <span style="color: var(--ink-faint);">&middot;</span>
                  <span class="text-xs" style="color: var(--ink-muted); font-family: var(--font-mono);">{entry.entityId}</span>
                </div>
                <div class="flex items-center gap-2 mt-1 text-sm">
                  {#if entry.oldValue}
                    <span style="color: #b91c1c; text-decoration: line-through; opacity: 0.7;">{entry.oldValue}</span>
                    <span style="color: var(--ink-faint);">&#8594;</span>
                  {/if}
                  <span class="font-medium" style="color: #15803d;">{entry.newValue}</span>
                </div>
                <div class="flex items-center gap-2 mt-1 text-xs" style="color: var(--ink-muted);">
                  <span>{formatDate(entry.appliedAt)}</span>
                  <span style="color: var(--ink-faint);">&middot;</span>
                  <span style="font-family: var(--font-mono);">{entry.actor}</span>
                  {#if entry.undoneAt}
                    <span class="px-1.5 py-0.5 rounded text-xs" style="background: rgba(220,38,38,0.1); color: #dc2626;">Undone</span>
                  {/if}
                </div>
              </div>
              {#if !entry.undoneAt}
                <button
                  onclick={() => handleUndo(entry)}
                  class="px-3 py-1.5 text-xs font-medium rounded-lg transition-colors"
                  style="background: var(--parchment); color: var(--ink-light);"
                  title={t('proposals.undo')}
                >{t('common.undo')}</button>
              {/if}
            </div>
          </div>
        {/each}
      </div>
    {/if}

  <!-- Rules Tab -->
  {:else if activeTab === 'rules'}
    {#if qualityRules.length === 0}
      <div class="text-center py-16">
        <p class="text-sm" style="color: var(--ink-muted);">No quality rules configured.</p>
      </div>
    {:else}
      <div class="space-y-2">
        {#each qualityRules as rule}
          {@const badge = ruleTypeBadge(rule.ruleType)}
          <div class="arch-card rounded-xl px-5 py-3.5">
            <div class="flex items-center justify-between">
              <div class="flex-1 min-w-0">
                <div class="flex items-center gap-2">
                  <span class="text-sm font-medium" style="color: var(--ink);">{rule.name}</span>
                  <span class="text-[10px] px-1.5 py-0.5 rounded-full font-medium" style="background: {badge.bg}; color: {badge.text};">
                    {rule.ruleType}
                  </span>
                </div>
                <p class="text-xs mt-0.5" style="color: var(--ink-muted);">{rule.description}</p>
              </div>
              <label class="relative inline-flex items-center cursor-pointer shrink-0 ml-4">
                <input
                  type="checkbox"
                  checked={!!rule.isActive}
                  onchange={() => handleToggleRule(rule)}
                  class="sr-only peer"
                  aria-label={`Toggle rule ${rule.name}`}
                />
                <div class="w-9 h-5 rounded-full peer transition-colors"
                  style="background: {rule.isActive ? 'var(--color-validated, #16a34a)' : 'var(--ink-faint)'};"
                >
                  <div class="absolute top-0.5 left-0.5 w-4 h-4 rounded-full transition-transform"
                    style="background: white; transform: {rule.isActive ? 'translateX(16px)' : 'translateX(0)'};"
                  ></div>
                </div>
              </label>
            </div>
          </div>
        {/each}
      </div>
    {/if}
  {/if}
</div>
