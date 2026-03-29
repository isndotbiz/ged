<script lang="ts">
  import { getPlaces } from '$lib/db';
  import type { Place } from '$lib/types';

  let places = $state<Place[]>([]);
  let search = $state('');

  async function load() { places = await getPlaces(); }

  let filtered = $derived(
    search.trim()
      ? places.filter(p => p.name.toLowerCase().includes(search.toLowerCase()))
      : places
  );

  let totalEvents = $derived(places.reduce((s, p) => s + p.eventCount, 0));

  $effect(() => { load(); });
</script>

<div class="p-8 max-w-4xl animate-fade-in">
  <h1 class="text-2xl font-bold tracking-tight" style="font-family: var(--font-serif); color: var(--ink);">Places</h1>
  <p class="text-sm text-ink-muted mt-1 mb-6">{places.length} places, {totalEvents} total events</p>

  <input
    bind:value={search}
    placeholder="Search places..."
    class="w-full max-w-md px-3 py-2 text-sm rounded-lg mb-6 arch-input"
  />

  {#if filtered.length === 0}
    <div class="arch-card rounded-xl p-8 text-center">
      <p class="text-ink-muted text-sm">No places found</p>
    </div>
  {:else}
    <div class="arch-card rounded-xl overflow-hidden">
      <table class="w-full text-sm">
        <thead>
          <tr class="text-left text-xs text-ink-muted" style="background: var(--parchment); border-bottom: 1px solid var(--border-subtle);">
            <th class="py-2 px-4 font-medium">Place</th>
            <th class="py-2 px-4 font-medium text-right">Events</th>
            <th class="py-2 px-4 font-medium text-right">Coordinates</th>
          </tr>
        </thead>
        <tbody>
          {#each filtered.slice(0, 100) as place}
            <tr class="hover:opacity-90" style="border-bottom: 1px solid var(--border-subtle);">
              <td class="py-2 px-4 text-ink-light">{place.name}</td>
              <td class="py-2 px-4 text-right">
                <span class="px-2 py-0.5 text-[10px] font-medium rounded-full bg-green-50 text-green-700">{place.eventCount}</span>
              </td>
              <td class="py-2 px-4 text-right text-xs text-ink-faint">
                {place.latitude != null ? `${place.latitude.toFixed(2)}, ${place.longitude?.toFixed(2)}` : '—'}
              </td>
            </tr>
          {/each}
        </tbody>
      </table>
      {#if filtered.length > 100}
        <div class="p-3 text-center text-xs text-ink-faint">Showing 100 of {filtered.length}</div>
      {/if}
    </div>
  {/if}
</div>
