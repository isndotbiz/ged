<script lang="ts">
  import { getSources } from '$lib/db';
  import type { Source } from '$lib/types';

  let sources = $state<Source[]>([]);
  let search = $state('');

  async function load() { sources = await getSources(); }

  let filtered = $derived(
    search.trim()
      ? sources.filter(s => s.title.toLowerCase().includes(search.toLowerCase()) || s.author.toLowerCase().includes(search.toLowerCase()))
      : sources
  );

  $effect(() => { load(); });
</script>

<div class="p-8 max-w-4xl animate-fade-in">
  <h1 class="text-2xl font-bold tracking-tight" style="font-family: var(--font-serif); color: var(--ink);">Sources</h1>
  <p class="text-sm text-ink-muted mt-1 mb-6">{sources.length} sources</p>

  <input
    bind:value={search}
    placeholder="Search sources..."
    class="w-full max-w-md px-3 py-2 text-sm border border-gray-200 rounded-lg mb-6 focus:outline-none focus:ring-2 focus:ring-amber-500/20"
  />

  {#if filtered.length === 0}
    <div class="arch-card rounded-xl p-8 text-center">
      <p class="text-ink-muted text-sm">No sources found</p>
    </div>
  {:else}
    <div class="space-y-2">
      {#each filtered as source}
        <div class="arch-card rounded-xl p-4">
          <div class="font-medium text-sm text-ink">{source.title || '(Untitled)'}</div>
          {#if source.author}<div class="text-xs text-ink-muted mt-1">By {source.author}</div>{/if}
          {#if source.publisher}<div class="text-xs text-ink-faint mt-0.5">{source.publisher}</div>{/if}
          <div class="text-[10px] text-ink-faint mt-1">{source.xref}</div>
        </div>
      {/each}
    </div>
  {/if}
</div>
