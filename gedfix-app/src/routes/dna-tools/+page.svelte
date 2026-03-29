<script lang="ts">
  import { predictRelationships, allRelationships, expectedCMForRelationship } from '$lib/dna-calculator';
  import type { DNARelationshipPrediction } from '$lib/types';

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
</script>

<div class="p-8 max-w-4xl animate-fade-in">
  <h1 class="text-2xl font-bold tracking-tight" style="font-family: var(--font-serif); color: var(--ink);">DNA Tools</h1>
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
      <select bind:value={selectedRel} onchange={lookupRel} class="px-3 py-2 text-sm border border-gray-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-amber-500/20">
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
          <th class="py-2 font-medium">Relationship</th>
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
</div>
