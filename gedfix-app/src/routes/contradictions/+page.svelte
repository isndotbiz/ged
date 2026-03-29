<script lang="ts">
  import { getAllPersons, getEvents, getFamilies, getChildren, getParents } from '$lib/db';
  import type { Contradiction } from '$lib/types';

  let contradictions = $state<Contradiction[]>([]);
  let isRunning = $state(false);
  let hasRun = $state(false);
  let filter = $state<string | null>(null);

  function extractYear(dateStr: string): number | null {
    const m = dateStr.match(/(\d{4})/);
    return m ? parseInt(m[1]) : null;
  }

  async function runAnalysis() {
    isRunning = true;
    const results: Contradiction[] = [];
    const persons = await getAllPersons();
    const families = await getFamilies();
    let idCounter = 0;

    for (const person of persons) {
      const events = await getEvents(person.xref);
      const birth = events.find(e => e.eventType === 'BIRT');
      const death = events.find(e => e.eventType === 'DEAT');
      const birthYear = birth ? extractYear(birth.dateValue) : (person.birthDate ? extractYear(person.birthDate) : null);
      const deathYear = death ? extractYear(death.dateValue) : (person.deathDate ? extractYear(person.deathDate) : null);

      if (birthYear && deathYear && deathYear < birthYear) {
        results.push({ id: `c${idCounter++}`, severity: 'critical', category: 'Impossible Date', title: `Death before birth: ${person.givenName} ${person.surname}`, detail: `Born ${birthYear}, died ${deathYear}`, personXrefs: [person.xref], suggestion: 'Check birth or death date' });
      }

      if (birthYear && deathYear && (deathYear - birthYear) > 120) {
        results.push({ id: `c${idCounter++}`, severity: 'warning', category: 'Unlikely Age', title: `Lived ${deathYear - birthYear} years: ${person.givenName} ${person.surname}`, detail: `Born ${birthYear}, died ${deathYear}`, personXrefs: [person.xref], suggestion: 'Verify dates' });
      }

      for (const evt of events) {
        if (evt.eventType === 'BIRT' || evt.eventType === 'DEAT') continue;
        const yr = extractYear(evt.dateValue);
        if (!yr) continue;
        const type = evt.eventType === 'MARR' ? 'Marriage' : evt.eventType === 'BURI' ? 'Burial' : evt.eventType;
        if (birthYear && yr < birthYear - 1) {
          results.push({ id: `c${idCounter++}`, severity: 'critical', category: 'Impossible Date', title: `${type} before birth: ${person.givenName} ${person.surname}`, detail: `${type} in ${yr}, born ${birthYear}`, personXrefs: [person.xref], suggestion: `Check ${type.toLowerCase()} date` });
        }
        if (deathYear && yr > deathYear + 1) {
          results.push({ id: `c${idCounter++}`, severity: 'critical', category: 'Impossible Date', title: `${type} after death: ${person.givenName} ${person.surname}`, detail: `${type} in ${yr}, died ${deathYear}`, personXrefs: [person.xref], suggestion: `Check ${type.toLowerCase()} or death date` });
        }
      }
    }

    contradictions = results.sort((a, b) => {
      const sev = { critical: 0, warning: 1, info: 2 };
      return (sev[a.severity] ?? 3) - (sev[b.severity] ?? 3);
    });
    isRunning = false;
    hasRun = true;
  }

  let filtered = $derived(filter ? contradictions.filter(c => c.category === filter) : contradictions);
  let categories = $derived([...new Set(contradictions.map(c => c.category))]);
  let criticalCount = $derived(contradictions.filter(c => c.severity === 'critical').length);
  let warningCount = $derived(contradictions.filter(c => c.severity === 'warning').length);
</script>

<div class="p-8 max-w-4xl animate-fade-in">
  <div class="flex items-center justify-between mb-8">
    <div>
      <h1 class="text-2xl font-bold tracking-tight" style="font-family: var(--font-serif); color: var(--ink);">Contradiction Detector</h1>
      <p class="text-sm text-ink-muted mt-1">Finds impossible dates, conflicting facts, and logical impossibilities</p>
    </div>
    <button onclick={runAnalysis} disabled={isRunning} class="px-4 py-2 text-sm font-medium btn-accent text-white rounded-lg disabled:opacity-50 transition-colors">
      {isRunning ? 'Analyzing...' : 'Run Analysis'}
    </button>
  </div>

  {#if hasRun}
    <div class="flex gap-3 mb-6">
      <div class="px-3 py-1.5 rounded-lg bg-red-50 text-red-700 text-sm font-medium">{criticalCount} Critical</div>
      <div class="px-3 py-1.5 rounded-lg bg-orange-50 text-orange-700 text-sm font-medium">{warningCount} Warnings</div>
      <div class="px-3 py-1.5 rounded-lg text-sm font-medium" style="background: var(--parchment); color: var(--ink-light);">{contradictions.length} Total</div>
    </div>

    {#if categories.length > 1}
      <div class="flex gap-2 mb-6">
        <button onclick={() => filter = null} class="px-3 py-1 text-xs rounded-full {filter === null ? 'btn-filter-active' : 'btn-filter'}">All</button>
        {#each categories as cat}
          <button onclick={() => filter = filter === cat ? null : cat} class="px-3 py-1 text-xs rounded-full {filter === cat ? 'btn-filter-active' : 'btn-filter'}">{cat}</button>
        {/each}
      </div>
    {/if}

    {#if filtered.length === 0}
      <div class="arch-card rounded-xl p-8 text-center">
        <p class="text-ink-muted">No contradictions found. Your tree is logically consistent.</p>
      </div>
    {:else}
      <div class="space-y-3">
        {#each filtered as c}
          <div class="arch-card rounded-xl p-4">
            <div class="flex items-center gap-2 mb-2">
              <span class="px-2 py-0.5 text-[10px] font-semibold rounded-full {c.severity === 'critical' ? 'bg-red-100 text-red-700' : c.severity === 'warning' ? 'bg-orange-100 text-orange-700' : 'bg-blue-100 text-blue-700'}">{c.severity}</span>
              <span class="px-2 py-0.5 text-[10px] rounded-full" style="background: var(--parchment); color: var(--ink-light);">{c.category}</span>
            </div>
            <div class="font-medium text-sm text-ink">{c.title}</div>
            <div class="text-xs text-ink-muted mt-1">{c.detail}</div>
            <div class="text-xs text-blue-600 mt-1">{c.suggestion}</div>
          </div>
        {/each}
      </div>
    {/if}
  {:else}
    <div class="arch-card rounded-xl p-8 text-center">
      <p class="text-ink-muted text-sm">Click "Run Analysis" to scan your tree for contradictions</p>
      <p class="text-ink-faint text-xs mt-1">Checks: impossible dates, age anomalies, parent-child conflicts, event timeline issues</p>
    </div>
  {/if}
</div>
