<script lang="ts">
  import { t } from '$lib/i18n';
  import { getPlaces, getDb, getEventsForPlace } from '$lib/db';
  import type { Place, GedcomEvent } from '$lib/types';

  let places = $state<Place[]>([]);
  let search = $state('');
  let sortCol = $state<'name' | 'eventCount' | 'coords'>('eventCount');
  let sortAsc = $state(false);
  let expandedPlace = $state<string | null>(null);
  let expandedEvents = $state<(GedcomEvent & { personName?: string })[]>([]);
  let loadingEvents = $state(false);
  let geocodingId = $state<number | null>(null);
  let geocodingAll = $state(false);
  let geocodeProgress = $state({ done: 0, total: 0 });

  async function load() { places = await getPlaces(); }

  let filtered = $derived(
    (() => {
      let list = search.trim()
        ? places.filter(p => p.name.toLowerCase().includes(search.toLowerCase()))
        : [...places];

      list.sort((a, b) => {
        let cmp = 0;
        if (sortCol === 'name') {
          cmp = a.name.localeCompare(b.name);
        } else if (sortCol === 'eventCount') {
          cmp = a.eventCount - b.eventCount;
        } else {
          const aHas = a.latitude != null ? 1 : 0;
          const bHas = b.latitude != null ? 1 : 0;
          cmp = aHas - bHas;
          if (cmp === 0 && a.latitude != null && b.latitude != null) {
            cmp = a.latitude - b.latitude;
          }
        }
        return sortAsc ? cmp : -cmp;
      });

      return list;
    })()
  );

  let totalEvents = $derived(places.reduce((s, p) => s + p.eventCount, 0));
  let geocodedCount = $derived(places.filter(p => p.latitude != null).length);
  let unresolvedCount = $derived(places.filter(p => p.latitude == null).length);

  function toggleSort(col: 'name' | 'eventCount' | 'coords') {
    if (sortCol === col) {
      sortAsc = !sortAsc;
    } else {
      sortCol = col;
      sortAsc = col === 'name';
    }
  }

  function sortIndicator(col: 'name' | 'eventCount' | 'coords'): string {
    if (sortCol !== col) return '';
    return sortAsc ? ' \u25B2' : ' \u25BC';
  }

  async function toggleExpand(placeName: string) {
    if (expandedPlace === placeName) {
      expandedPlace = null;
      expandedEvents = [];
      return;
    }
    expandedPlace = placeName;
    loadingEvents = true;
    try {
      expandedEvents = await getEventsForPlace(placeName);
    } catch {
      expandedEvents = [];
    }
    loadingEvents = false;
  }

  async function geocodePlace(place: Place) {
    if (geocodingId === place.id) return;
    geocodingId = place.id;
    try {
      const encoded = encodeURIComponent(place.name);
      const resp = await fetch(
        `https://nominatim.openstreetmap.org/search?format=json&q=${encoded}&limit=1`,
        { headers: { 'User-Agent': 'GedFix/1.0' } }
      );
      const data = await resp.json();
      if (data.length > 0) {
        const lat = parseFloat(data[0].lat);
        const lon = parseFloat(data[0].lon);
        const d = await getDb();
        await d.execute('UPDATE place SET latitude = $1, longitude = $2 WHERE id = $3', [lat, lon, place.id]);
        // Update local state
        const idx = places.findIndex(p => p.id === place.id);
        if (idx >= 0) {
          places[idx] = { ...places[idx], latitude: lat, longitude: lon };
          places = [...places];
        }
      }
    } catch (err) {
      console.error('Geocode failed for', place.name, err);
    }
    geocodingId = null;
  }

  async function geocodeAll() {
    const unresolved = places.filter(p => p.latitude == null);
    if (unresolved.length === 0 || geocodingAll) return;
    geocodingAll = true;
    geocodeProgress = { done: 0, total: unresolved.length };

    for (const place of unresolved) {
      try {
        const encoded = encodeURIComponent(place.name);
        const resp = await fetch(
          `https://nominatim.openstreetmap.org/search?format=json&q=${encoded}&limit=1`,
          { headers: { 'User-Agent': 'GedFix/1.0' } }
        );
        const data = await resp.json();
        if (data.length > 0) {
          const lat = parseFloat(data[0].lat);
          const lon = parseFloat(data[0].lon);
          const d = await getDb();
          await d.execute('UPDATE place SET latitude = $1, longitude = $2 WHERE id = $3', [lat, lon, place.id]);
          const idx = places.findIndex(p => p.id === place.id);
          if (idx >= 0) {
            places[idx] = { ...places[idx], latitude: lat, longitude: lon };
            places = [...places];
          }
        }
      } catch (err) {
        console.error('Geocode failed for', place.name, err);
      }
      geocodeProgress = { done: geocodeProgress.done + 1, total: geocodeProgress.total };
      // Nominatim rate limit: 1 request per second
      await new Promise(r => setTimeout(r, 1100));
    }
    geocodingAll = false;
  }

  $effect(() => { load(); });
</script>

<div class="p-8 max-w-5xl animate-fade-in">
  <div class="flex items-start justify-between mb-1">
    <h1 class="text-2xl font-bold tracking-tight" style="font-family: var(--font-serif); color: var(--ink);">{t('nav.places')}</h1>
    <button
      onclick={geocodeAll}
      disabled={geocodingAll || unresolvedCount === 0}
      class="px-3 py-1.5 text-xs font-medium rounded-lg transition-all"
      style="background: var(--ink); color: var(--parchment); opacity: {geocodingAll || unresolvedCount === 0 ? 0.4 : 1};"
    >
      {#if geocodingAll}
        Geocoding {geocodeProgress.done}/{geocodeProgress.total}...
      {:else}
        Geocode All ({unresolvedCount})
      {/if}
    </button>
  </div>

  <p class="text-sm text-ink-muted mt-1 mb-6">
    {places.length} places &middot; {totalEvents} events &middot;
    <span style="color: var(--accent-green, #16a34a);">{geocodedCount} geocoded</span> &middot;
    <span class="text-ink-faint">{unresolvedCount} unresolved</span>
  </p>

  <input
    bind:value={search}
    placeholder={t('places.searchPlaceholder')}
    class="w-full max-w-md px-3 py-2 text-sm rounded-lg mb-6 arch-input"
   aria-label={t('places.searchPlaceholder')} />

  {#if filtered.length === 0}
    <div class="arch-card rounded-xl p-8 text-center">
      <p class="text-ink-muted text-sm">{t('places.noPlaces')}</p>
    </div>
  {:else}
    <div class="arch-card rounded-xl overflow-hidden">
      <table class="w-full text-sm">
        <thead>
          <tr class="text-left text-xs text-ink-muted" style="background: var(--parchment); border-bottom: 1px solid var(--border-subtle);">
            <th class="py-2 px-4 font-medium w-6"></th>
            <th
              class="py-2 px-4 font-medium cursor-pointer select-none hover:opacity-70 transition-opacity"
              onclick={() => toggleSort('name')}
            >
              Place{sortIndicator('name')}
            </th>
            <th
              class="py-2 px-4 font-medium text-right cursor-pointer select-none hover:opacity-70 transition-opacity"
              onclick={() => toggleSort('eventCount')}
            >
              Events{sortIndicator('eventCount')}
            </th>
            <th
              class="py-2 px-4 font-medium text-right cursor-pointer select-none hover:opacity-70 transition-opacity"
              onclick={() => toggleSort('coords')}
            >
              Coordinates{sortIndicator('coords')}
            </th>
            <th class="py-2 px-4 font-medium text-right">{t('common.actions')}</th>
          </tr>
        </thead>
        <tbody>
          {#each filtered as place (place.id)}
            <tr
              class="hover:opacity-90 cursor-pointer transition-colors"
              style="border-bottom: 1px solid var(--border-subtle); {expandedPlace === place.name ? 'background: color-mix(in srgb, var(--parchment) 60%, transparent);' : ''}"
              onclick={() => toggleExpand(place.name)}
            >
              <td class="py-2 px-4 text-ink-faint text-xs">
                <span class="inline-block transition-transform" style="transform: rotate({expandedPlace === place.name ? '90deg' : '0deg'});">
                  &#9656;
                </span>
              </td>
              <td class="py-2 px-4 text-ink-light">{place.name}</td>
              <td class="py-2 px-4 text-right">
                <span class="px-2 py-0.5 text-[10px] font-medium rounded-full bg-green-50 text-green-700">{place.eventCount}</span>
              </td>
              <td class="py-2 px-4 text-right text-xs text-ink-faint">
                {place.latitude != null ? `${place.latitude.toFixed(4)}, ${place.longitude?.toFixed(4)}` : '---'}
              </td>
              <td class="py-2 px-4 text-right" onclick={(e) => e.stopPropagation()}>
                {#if place.latitude == null}
                  <button
                    onclick={() => geocodePlace(place)}
                    disabled={geocodingId === place.id}
                    class="px-2 py-0.5 text-[10px] font-medium rounded transition-all"
                    style="border: 1px solid var(--border-subtle); color: var(--ink); opacity: {geocodingId === place.id ? 0.4 : 0.7};"
                  >
                    {geocodingId === place.id ? 'Looking up...' : 'Geocode'}
                  </button>
                {:else}
                  <span class="text-[10px] text-ink-faint">{t('common.resolved')}</span>
                {/if}
              </td>
            </tr>

            {#if expandedPlace === place.name}
              <tr style="background: color-mix(in srgb, var(--parchment) 40%, transparent);">
                <td colspan="5" class="px-4 py-3">
                  {#if loadingEvents}
                    <p class="text-xs text-ink-muted py-2 pl-6">Loading events...</p>
                  {:else if expandedEvents.length === 0}
                    <p class="text-xs text-ink-muted py-2 pl-6">{t('map.noEvents')}</p>
                  {:else}
                    <table class="w-full text-xs ml-6" style="max-width: calc(100% - 1.5rem);">
                      <thead>
                        <tr class="text-ink-muted" style="border-bottom: 1px solid var(--border-subtle);">
                          <th class="py-1 pr-4 text-left font-medium">Event Type</th>
                          <th class="py-1 pr-4 text-left font-medium">{t('common.date')}</th>
                          <th class="py-1 text-left font-medium">{t('common.person')}</th>
                        </tr>
                      </thead>
                      <tbody>
                        {#each expandedEvents as evt}
                          <tr style="border-bottom: 1px solid var(--border-subtle);">
                            <td class="py-1.5 pr-4">
                              <span class="px-1.5 py-0.5 rounded text-[10px] font-medium" style="background: color-mix(in srgb, var(--ink) 8%, transparent); color: var(--ink);">
                                {evt.eventType}
                              </span>
                            </td>
                            <td class="py-1.5 pr-4 text-ink-light">{evt.dateValue || '---'}</td>
                            <td class="py-1.5 text-ink-light">{evt.personName || evt.ownerXref}</td>
                          </tr>
                        {/each}
                      </tbody>
                    </table>
                  {/if}
                </td>
              </tr>
            {/if}
          {/each}
        </tbody>
      </table>
    </div>

    <p class="text-xs text-ink-faint mt-3 text-right">{filtered.length} places shown</p>
  {/if}
</div>
