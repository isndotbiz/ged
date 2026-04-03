<script lang="ts">
  import { t } from '$lib/i18n';
  import { treeIssues, isAnalyzing } from '$lib/stores';
  import { analyzeTree } from '$lib/tree-analyzer';
  import { applyIssueFix, dismissIssue, getDismissedIssueKeys, clearDismissedIssues } from '$lib/db';
  import type { TreeIssue } from '$lib/types';

  let progress = $state(0);
  let progressMsg = $state('');
  let filterCategory = $state('');
  let filterSeverity = $state('');
  let selected = $state<Set<string>>(new Set());
  let dismissedKeys = $state<Set<string>>(new Set());
  let fixingIds = $state<Set<string>>(new Set());
  let fixResults = $state<Map<string, { success: boolean; error?: string }>>(new Map());
  let bulkFixing = $state(false);

  function issueKey(issue: TreeIssue): string {
    return `${issue.title}|${issue.personXref}|${issue.familyXref}`;
  }

  async function runAnalysis() {
    $isAnalyzing = true;
    progress = 0;
    progressMsg = 'Starting analysis...';
    selected = new Set();
    fixResults = new Map();

    const dismissed = await getDismissedIssueKeys();
    dismissedKeys = dismissed;

    const issues = await analyzeTree((pct, msg) => {
      progress = pct;
      progressMsg = msg;
    });

    // Filter out dismissed issues
    const filtered = issues.filter(i => !dismissed.has(issueKey(i)));
    treeIssues.set(filtered);
    $isAnalyzing = false;
  }

  let filtered = $derived($treeIssues.filter(i => {
    if (filterCategory && i.category !== filterCategory) return false;
    if (filterSeverity && i.severity !== filterSeverity) return false;
    return true;
  }));

  let counts = $derived.by(() => {
    let critical = 0, warning = 0, info = 0;
    for (const i of $treeIssues) {
      if (i.severity === 'critical') critical++;
      else if (i.severity === 'warning') warning++;
      else info++;
    }
    let fixable = 0;
    for (const i of filtered) if (i.autoFixable) fixable++;
    return { critical, warning, info, fixable };
  });
  let criticalCount = $derived(counts.critical);
  let warningCount = $derived(counts.warning);
  let infoCount = $derived(counts.info);
  let fixableCount = $derived(counts.fixable);

  let healthScore = $derived.by(() => {
    if ($treeIssues.length === 0) return null;
    const penalty = criticalCount * 5 + warningCount * 2 + infoCount * 0.5;
    return Math.max(0, Math.round(100 - penalty));
  });

  let selectedCount = $derived(selected.size);
  let selectedFixable = $derived(filtered.filter(i => selected.has(i.id) && i.autoFixable));
  let allVisibleSelected = $derived(filtered.length > 0 && filtered.every(i => selected.has(i.id)));

  function toggleSelect(id: string) {
    const next = new Set(selected);
    if (next.has(id)) next.delete(id); else next.add(id);
    selected = next;
  }

  function toggleSelectAll() {
    if (allVisibleSelected) {
      selected = new Set();
    } else {
      selected = new Set(filtered.map(i => i.id));
    }
  }

  async function applyFixToIssue(issue: TreeIssue): Promise<{ success: boolean; error?: string }> {
    const adding = new Set(fixingIds); adding.add(issue.id); fixingIds = adding;
    const result = await applyIssueFix(issue);
    const nextResults = new Map(fixResults); nextResults.set(issue.id, result); fixResults = nextResults;
    const removing = new Set(fixingIds); removing.delete(issue.id); fixingIds = removing;
    return result;
  }

  function removeIssuesFromList(ids: Set<string>) {
    setTimeout(() => {
      treeIssues.update(issues => issues.filter(i => !ids.has(i.id)));
      const next = new Set(selected); ids.forEach(id => next.delete(id)); selected = next;
    }, 600);
  }

  async function fixOne(issue: TreeIssue) {
    const result = await applyFixToIssue(issue);
    if (result.success) removeIssuesFromList(new Set([issue.id]));
  }

  async function fixSelected() {
    bulkFixing = true;
    const toFix = selectedFixable;
    const fixedIds = new Set<string>();

    for (const issue of toFix) {
      const result = await applyFixToIssue(issue);
      if (result.success) fixedIds.add(issue.id);
    }

    if (fixedIds.size > 0) removeIssuesFromList(fixedIds);
    bulkFixing = false;
  }

  async function dismissOne(issue: TreeIssue) {
    const key = issueKey(issue);
    await dismissIssue(key);
    const nextDismissed = new Set(dismissedKeys); nextDismissed.add(key); dismissedKeys = nextDismissed;
    treeIssues.update(issues => issues.filter(i => i.id !== issue.id));
    const nextSel = new Set(selected); nextSel.delete(issue.id); selected = nextSel;
  }

  async function dismissSelected() {
    const toDismiss = filtered.filter(i => selected.has(i.id));
    const nextDismissed = new Set(dismissedKeys);
    for (const issue of toDismiss) {
      const key = issueKey(issue);
      await dismissIssue(key);
      nextDismissed.add(key);
    }
    dismissedKeys = nextDismissed;
    const dismissedIds = new Set(toDismiss.map(i => i.id));
    treeIssues.update(issues => issues.filter(i => !dismissedIds.has(i.id)));
    const nextSel = new Set(selected); dismissedIds.forEach(id => nextSel.delete(id)); selected = nextSel;
  }

  async function resetDismissed() {
    await clearDismissedIssues();
    dismissedKeys = new Set();
  }

  const severityColors: Record<string, { bg: string; text: string; dot: string }> = {
    critical: { bg: 'bg-red-50', text: 'text-red-700', dot: 'bg-red-500' },
    warning: { bg: 'bg-orange-50', text: 'text-orange-700', dot: 'bg-orange-400' },
    info: { bg: 'bg-blue-50', text: 'text-blue-700', dot: 'bg-blue-400' },
  };

  const categoryLabels: Record<string, string> = {
    date: 'Date Issues',
    relationship: 'Relationship Issues',
    quality: 'Data Quality',
    duplicate: 'Potential Duplicates',
  };
</script>

<div class="p-8 max-w-4xl animate-fade-in">
  <div class="flex items-center justify-between mb-6">
    <div>
      <h1 class="text-2xl font-bold tracking-tight" style="font-family: var(--font-serif); color: var(--ink);">{t('nav.dataQuality')}</h1>
      <p class="text-sm text-ink-muted mt-1">Analyze, fix, and clean your family tree</p>
    </div>
    <div class="flex gap-2">
      {#if dismissedKeys.size > 0}
        <button
          onclick={resetDismissed}
          class="px-3 py-2 text-xs btn-secondary transition-colors rounded-lg"
          title={t('issues.showDismissed')}
        >Reset {dismissedKeys.size} Dismissed</button>
      {/if}
      <button
        onclick={runAnalysis}
        disabled={$isAnalyzing}
        class="px-4 py-2 btn-accent text-white text-sm font-medium rounded-lg disabled:opacity-50 transition-colors"
      >
        {$isAnalyzing ? 'Analyzing...' : $treeIssues.length > 0 ? 'Re-analyze' : 'Run Analysis'}
      </button>
    </div>
  </div>

  {#if $isAnalyzing}
    <div class="arch-card rounded-xl p-6 mb-6">
      <div class="w-full h-1.5 arch-progress-track rounded-full overflow-hidden mb-2">
        <div class="h-full arch-progress-bar rounded-full transition-all duration-300" style="width: {progress}%"></div>
      </div>
      <p class="text-xs text-ink-muted">{progressMsg}</p>
    </div>
  {/if}

  {#if healthScore !== null}
    <!-- Health Score + Summary -->
    <div class="flex gap-4 mb-6">
      <div class="arch-card rounded-xl p-6 flex items-center gap-4">
        <div class="relative w-16 h-16">
          <svg viewBox="0 0 36 36" class="w-16 h-16 -rotate-90">
            <path d="M18 2.0845 a 15.9155 15.9155 0 0 1 0 31.831 a 15.9155 15.9155 0 0 1 0 -31.831"
              fill="none" stroke="#E5E7EB" stroke-width="3" />
            <path d="M18 2.0845 a 15.9155 15.9155 0 0 1 0 31.831 a 15.9155 15.9155 0 0 1 0 -31.831"
              fill="none"
              stroke={healthScore >= 80 ? '#34C759' : healthScore >= 50 ? '#FF9500' : '#FF3B30'}
              stroke-width="3"
              stroke-dasharray="{healthScore}, 100" />
          </svg>
          <span class="absolute inset-0 flex items-center justify-center text-lg font-bold text-ink">{healthScore}</span>
        </div>
        <div>
          <div class="text-sm font-semibold text-ink">{t('dashboard.healthScore')}</div>
          <div class="text-xs text-ink-muted">{$treeIssues.length} issues · {fixableCount} auto-fixable</div>
        </div>
      </div>

      <div class="flex gap-3">
        <button onclick={() => { filterSeverity = filterSeverity === 'critical' ? '' : 'critical'; }}
          class="arch-card rounded-xl px-5 py-4 text-center hover:bg-red-50/50 transition-colors {filterSeverity === 'critical' ? 'ring-2 ring-red-300' : ''}">
          <div class="text-xl font-bold text-red-600">{criticalCount}</div>
          <div class="text-xs text-ink-muted">{t('common.critical')}</div>
        </button>
        <button onclick={() => { filterSeverity = filterSeverity === 'warning' ? '' : 'warning'; }}
          class="arch-card rounded-xl px-5 py-4 text-center hover:bg-orange-50/50 transition-colors {filterSeverity === 'warning' ? 'ring-2 ring-orange-300' : ''}">
          <div class="text-xl font-bold text-orange-500">{warningCount}</div>
          <div class="text-xs text-ink-muted">Warnings</div>
        </button>
        <button onclick={() => { filterSeverity = filterSeverity === 'info' ? '' : 'info'; }}
          class="arch-card rounded-xl px-5 py-4 text-center hover:bg-blue-50/50 transition-colors {filterSeverity === 'info' ? 'ring-2 ring-blue-300' : ''}">
          <div class="text-xl font-bold text-blue-500">{infoCount}</div>
          <div class="text-xs text-ink-muted">{t('common.info')}</div>
        </button>
      </div>
    </div>

    <!-- Toolbar: filters + bulk actions -->
    <div class="flex items-center justify-between mb-4 gap-3">
      <div class="flex gap-2">
        <button onclick={() => { filterCategory = ''; }} class="text-xs px-3 py-1.5 rounded-full {filterCategory === '' ? 'btn-filter-active' : 'btn-filter'} transition-colors">All</button>
        {#each Object.entries(categoryLabels) as [key, label]}
          <button onclick={() => { filterCategory = filterCategory === key ? '' : key; }} class="text-xs px-3 py-1.5 rounded-full {filterCategory === key ? 'btn-filter-active' : 'btn-filter'} transition-colors">{label}</button>
        {/each}
      </div>

      {#if selectedCount > 0}
        <div class="flex items-center gap-2">
          <span class="text-xs text-ink-muted">{selectedCount} selected</span>
          {#if selectedFixable.length > 0}
            <button
              onclick={fixSelected}
              disabled={bulkFixing}
              class="px-3 py-1.5 text-xs font-medium rounded-lg text-white transition-colors disabled:opacity-50"
              style="background: var(--color-validated);"
            >{bulkFixing ? 'Fixing...' : `Fix ${selectedFixable.length} Issues`}</button>
          {/if}
          <button
            onclick={dismissSelected}
            class="px-3 py-1.5 text-xs font-medium rounded-lg transition-colors"
            style="background: var(--parchment); color: var(--ink-light);"
          >{t('issues.dismissSelected')}</button>
        </div>
      {/if}
    </div>

    <!-- Issue list -->
    <div class="space-y-2">
      <!-- Select all header -->
      {#if filtered.length > 0}
        <div class="flex items-center gap-3 px-5 py-2">
          <label class="flex items-center gap-2 cursor-pointer">
            <input
              type="checkbox"
              checked={allVisibleSelected}
              onchange={toggleSelectAll}
              class="w-3.5 h-3.5 rounded accent-amber-700"
            />
            <span class="text-xs text-ink-muted">Select all ({filtered.length})</span>
          </label>
        </div>
      {/if}

      {#each filtered as issue}
        {@const sev = severityColors[issue.severity]}
        {@const isFixing = fixingIds.has(issue.id)}
        {@const result = fixResults.get(issue.id)}
        <div class="arch-card rounded-xl px-5 py-3.5 transition-all {result?.success ? 'opacity-50 scale-[0.98]' : ''}">
          <div class="flex items-start gap-3">
            <input
              type="checkbox"
              checked={selected.has(issue.id)}
              onchange={() => toggleSelect(issue.id)}
              class="w-3.5 h-3.5 rounded mt-1 shrink-0 accent-amber-700 cursor-pointer"
            />
            <span class="w-2 h-2 rounded-full mt-1.5 shrink-0 {sev.dot}"></span>
            <div class="flex-1 min-w-0">
              <div class="flex items-center gap-2">
                <div class="text-sm font-medium text-ink">{issue.title}</div>
                {#if issue.autoFixable}
                  <span class="text-[9px] px-1.5 py-0.5 rounded-full font-medium" style="background: rgba(74,124,63,0.1); color: var(--color-validated);">Auto-fix</span>
                {/if}
              </div>
              <div class="text-xs text-ink-muted mt-0.5">{issue.detail}</div>
              {#if issue.fixDescription}
                <div class="text-xs mt-1" style="color: var(--color-validated); font-style: italic;">Fix: {issue.fixDescription}</div>
              {:else if issue.suggestion}
                <div class="text-xs text-ink-faint mt-1 italic">{issue.suggestion}</div>
              {/if}
              {#if result && !result.success}
                <div class="text-xs text-red-600 mt-1">Fix failed: {result.error}</div>
              {/if}
            </div>
            <div class="flex items-center gap-1.5 shrink-0">
              <span class="text-xs px-2 py-0.5 rounded-full {sev.bg} {sev.text}">{issue.severity}</span>
              {#if issue.autoFixable}
                <button
                  onclick={() => fixOne(issue)}
                  disabled={isFixing}
                  class="px-2.5 py-1 text-xs font-medium rounded-md text-white transition-colors disabled:opacity-50"
                  style="background: var(--color-validated);"
                  title={issue.fixDescription}
                >{isFixing ? '...' : 'Fix'}</button>
              {/if}
              <button
                onclick={() => dismissOne(issue)}
                class="px-2 py-1 text-xs rounded-md transition-colors"
                style="color: var(--ink-faint);"
                title="Dismiss this issue"
              >&times;</button>
            </div>
          </div>
        </div>
      {/each}
      {#if filtered.length === 0 && $treeIssues.length > 0}
        <div class="text-sm text-ink-faint py-8 text-center">{t('issues.noMatches')}</div>
      {/if}
    </div>
  {/if}
</div>
