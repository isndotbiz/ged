<script lang="ts">
  import { t } from '$lib/i18n';
  import { getPlaces, getDb, getEventsForPlace } from '$lib/db';
  import type { Place, GedcomEvent } from '$lib/types';
  import { browser } from '$app/environment';

  let places = $state<Place[]>([]);
  let geocodingAll = $state(false);
  let geocodeProgress = $state({ done: 0, total: 0 });
  let mapContainer: HTMLDivElement;
  let mapInstance: any = null;
  let leafletLib: any = null;
  let baseTileLayer: any = null;
  let markers: any[] = [];
  let themeMode = $state<'light' | 'dark'>('light');

  let totalEvents = $derived(places.reduce((s, p) => s + p.eventCount, 0));
  let geocodedCount = $derived(places.filter(p => p.latitude != null).length);
  let unresolvedCount = $derived(places.filter(p => p.latitude == null).length);
  let geocodedPlaces = $derived(places.filter(p => p.latitude != null && p.longitude != null));

  // eslint-disable-next-line @typescript-eslint/no-unused-vars
  function getMarkerColor(_place: Place): string {
    // We'll determine dominant event type when events are loaded
    // Default based on common patterns in place names
    return '#D97706'; // warm amber/orange default
  }

  function createMarkerIcon(L: any, color: string): any {
    return L.divIcon({
      className: 'custom-marker',
      html: `<div style="
        width: 12px; height: 12px;
        background: ${color};
        border: 2px solid white;
        border-radius: 50%;
        box-shadow: 0 1px 4px rgba(0,0,0,0.3);
      "></div>`,
      iconSize: [16, 16],
      iconAnchor: [8, 8],
      popupAnchor: [0, -10],
    });
  }

  function buildPopupContent(place: Place, events?: (GedcomEvent & { personName?: string })[]): string {
    let html = `
      <div style="font-family: var(--font-sans); min-width: 200px; max-width: 280px;">
        <div style="font-family: var(--font-serif); font-weight: 700; font-size: 13px; color: var(--ink); margin-bottom: 4px;">
          ${place.name}
        </div>
        <div style="font-size: 11px; color: var(--ink-muted); margin-bottom: 6px;">
          ${place.eventCount} event${place.eventCount !== 1 ? 's' : ''} &middot;
          ${place.latitude?.toFixed(4)}, ${place.longitude?.toFixed(4)}
        </div>
    `;

    if (events && events.length > 0) {
      html += `<div style="border-top: 1px solid var(--border-subtle); padding-top: 6px; max-height: 180px; overflow-y: auto;">`;
      for (const e of events) {
        const typeColor = getEventColor(e.eventType);
        html += `
          <div style="display: flex; align-items: center; gap: 6px; padding: 3px 0; font-size: 11px;">
            <span style="width: 6px; height: 6px; border-radius: 50%; background: ${typeColor}; flex-shrink: 0;"></span>
            <span style="color: var(--ink-light); flex: 1; overflow: hidden; text-overflow: ellipsis; white-space: nowrap;">
              ${e.personName || e.ownerXref}
            </span>
            <span style="color: var(--ink-faint); font-size: 10px; flex-shrink: 0;">
              ${e.eventType}${e.dateValue ? ' ' + e.dateValue : ''}
            </span>
          </div>
        `;
      }
      html += `</div>`;
    } else {
      html += `
        <button
          class="popup-view-events"
          data-place="${place.name.replace(/"/g, '&quot;')}"
          style="
            display: block; width: 100%; padding: 4px 8px; margin-top: 4px;
            font-size: 11px; font-weight: 500;
            background: var(--accent); color: white;
            border: none; border-radius: 4px; cursor: pointer;
            font-family: var(--font-sans);
          "
        >View Events</button>
      `;
    }

    html += `</div>`;
    return html;
  }

  function getEventColor(eventType: string): string {
    const t = eventType?.toUpperCase() || '';
    if (t === 'BIRT' || t === 'BIRTH') return '#16a34a';
    if (t === 'DEAT' || t === 'DEATH') return '#dc2626';
    if (t === 'MARR' || t === 'MARRIAGE') return '#2563eb';
    if (t === 'BURI' || t === 'BURIAL') return '#7c3aed';
    if (t === 'RESI' || t === 'RESIDENCE') return '#0891b2';
    if (t === 'IMMI' || t === 'IMMIGRATION') return '#ca8a04';
    return '#D97706';
  }

  function getDominantEventColor(events: (GedcomEvent & { personName?: string })[]): string {
    if (!events || events.length === 0) return '#D97706';
    const counts: Record<string, number> = {};
    for (const e of events) {
      const t = e.eventType?.toUpperCase() || 'OTHER';
      counts[t] = (counts[t] || 0) + 1;
    }
    const dominant = Object.entries(counts).sort((a, b) => b[1] - a[1])[0][0];
    return getEventColor(dominant);
  }

  async function initMap() {
    if (!browser || !mapContainer) return;
    const L = await import('leaflet');
    leafletLib = L;

    mapInstance = L.map(mapContainer, {
      center: [39.8, -98.5],
      zoom: 4,
      zoomControl: true,
    });

    updateBaseTileLayer();

    // Handle popup button clicks via event delegation
    mapInstance.on('popupopen', (e: any) => {
      const popup = e.popup;
      const container = popup.getElement();
      if (!container) return;

      const btn = container.querySelector('.popup-view-events') as HTMLElement;
      if (btn) {
        btn.addEventListener('click', async () => {
          const placeName = btn.getAttribute('data-place');
          if (!placeName) return;
          btn.textContent = 'Loading...';
          btn.style.opacity = '0.5';
          try {
            const events = await getEventsForPlace(placeName);
            const place = places.find(p => p.name === placeName);
            if (place) {
              // Update marker color based on dominant event type
              const color = getDominantEventColor(events);
              const marker = markers.find(m => m._placeName === placeName);
              if (marker) {
                marker.setIcon(createMarkerIcon(leafletLib, color));
              }
              popup.setContent(buildPopupContent(place, events));
            }
          } catch (err) {
            console.error('Failed to load events for', placeName, err);
            btn.textContent = 'Error loading';
          }
        });
      }
    });

    addMarkers();
  }

  function updateBaseTileLayer() {
    if (!mapInstance || !leafletLib) return;
    if (baseTileLayer) {
      mapInstance.removeLayer(baseTileLayer);
      baseTileLayer = null;
    }
    if (themeMode === 'dark') {
      baseTileLayer = leafletLib.tileLayer('https://{s}.basemaps.cartocdn.com/dark_all/{z}/{x}/{y}{r}.png', {
        attribution: '&copy; OpenStreetMap contributors &copy; CARTO',
        subdomains: 'abcd',
        maxZoom: 20,
      });
    } else {
      baseTileLayer = leafletLib.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
        attribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>',
        maxZoom: 18,
      });
    }
    baseTileLayer.addTo(mapInstance);
  }

  function addMarkers() {
    if (!mapInstance || !leafletLib) return;
    const L = leafletLib;

    // Clear existing
    for (const m of markers) {
      mapInstance.removeLayer(m);
    }
    markers = [];

    const geoPlaces = geocodedPlaces;
    if (geoPlaces.length === 0) return;

    const bounds: [number, number][] = [];

    for (const place of geoPlaces) {
      const lat = place.latitude!;
      const lng = place.longitude!;
      bounds.push([lat, lng]);

      const color = '#D97706'; // Default; will update on popup click
      const icon = createMarkerIcon(L, color);

      const marker = L.marker([lat, lng], { icon })
        .addTo(mapInstance)
        .bindPopup(() => buildPopupContent(place), { maxWidth: 300 });

      (marker as any)._placeName = place.name;
      markers.push(marker);
    }

    if (bounds.length > 0) {
      mapInstance.fitBounds(L.latLngBounds(bounds), { padding: [40, 40], maxZoom: 10 });
    }
  }

  async function geocodeAll() {
    const unresolved = places.filter(p => p.latitude == null);
    if (unresolved.length === 0 || geocodingAll) return;
    geocodingAll = true;
    geocodeProgress = { done: 0, total: unresolved.length };

    const d = await getDb();
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
          if (!isNaN(lat) && !isNaN(lon)) {
            await d.execute('UPDATE place SET latitude = $1, longitude = $2 WHERE id = $3', [lat, lon, place.id]);
            place.latitude = lat;
            place.longitude = lon;
          }
        }
      } catch (err) {
        console.error('Geocode failed for', place.name, err);
      }
      geocodeProgress = { done: geocodeProgress.done + 1, total: geocodeProgress.total };
      await new Promise(r => setTimeout(r, 1100));
    }
    geocodingAll = false;

    // Refresh markers
    places = [...places];
    addMarkers();
  }

  async function load() {
    places = await getPlaces();
  }

  $effect(() => { load(); });

  $effect(() => {
    if (!browser) return;
    // Re-add markers whenever geocodedPlaces changes
    const _ = geocodedPlaces;
    if (mapInstance && leafletLib) {
      addMarkers();
    }
  });

  $effect(() => {
    if (!browser || !mapContainer) return;
    if (!mapInstance) {
      initMap();
    }
  });

  $effect(() => {
    if (!browser) return;
    const root = document.documentElement;
    const syncTheme = () => {
      themeMode = root.dataset.theme === 'dark' ? 'dark' : 'light';
    };
    syncTheme();
    const observer = new MutationObserver(syncTheme);
    observer.observe(root, { attributes: true, attributeFilter: ['data-theme'] });
    return () => observer.disconnect();
  });

  $effect(() => {
    // eslint-disable-next-line @typescript-eslint/no-unused-expressions
    themeMode;
    if (mapInstance && leafletLib) {
      updateBaseTileLayer();
    }
  });
</script>

<svelte:head>
  <link rel="stylesheet" href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css" />
</svelte:head>

<div class="map-page">
  <!-- Stats Bar -->
  <div class="arch-card stats-bar">
    <div class="stats-inner">
      <div class="stats-group">
        <div class="stat-item">
          <span class="stat-value">{places.length}</span>
          <span class="stat-label">{t('places.totalPlaces')}</span>
        </div>
        <div class="stat-divider"></div>
        <div class="stat-item">
          <span class="stat-value" style="color: var(--color-validated);">{geocodedCount}</span>
          <span class="stat-label">{t('map.geocoded')}</span>
        </div>
        <div class="stat-divider"></div>
        <div class="stat-item">
          <span class="stat-value" style="color: var(--ink-muted);">{unresolvedCount}</span>
          <span class="stat-label">{t('common.unresolved')}</span>
        </div>
        <div class="stat-divider"></div>
        <div class="stat-item">
          <span class="stat-value" style="color: var(--accent);">{totalEvents}</span>
          <span class="stat-label">Events</span>
        </div>
      </div>

      <div class="stats-actions">
        <!-- Legend -->
        <div class="legend">
          <span class="legend-dot" style="background: #16a34a;" title={t('map.birth')}></span>
          <span class="legend-dot" style="background: #dc2626;" title={t('map.death')}></span>
          <span class="legend-dot" style="background: #2563eb;" title={t('families.marriage')}></span>
          <span class="legend-dot" style="background: #D97706;" title={t('common.other')}></span>
        </div>

        <button
          onclick={geocodeAll}
          disabled={geocodingAll || unresolvedCount === 0}
          class="btn-geocode"
        >
          {#if geocodingAll}
            Geocoding {geocodeProgress.done}/{geocodeProgress.total}...
          {:else}
            Geocode Missing ({unresolvedCount})
          {/if}
        </button>
      </div>
    </div>

    {#if geocodingAll}
      <div class="geocode-progress-track">
        <div
          class="geocode-progress-bar"
          style="width: {geocodeProgress.total > 0 ? (geocodeProgress.done / geocodeProgress.total) * 100 : 0}%"
        ></div>
      </div>
    {/if}
  </div>

  <!-- Map -->
  <div class="map-container" bind:this={mapContainer}></div>
</div>

<style>
  .map-page {
    display: flex;
    flex-direction: column;
    height: calc(100vh - 48px);
    overflow: hidden;
  }

  .stats-bar {
    border-radius: 0;
    border-left: none;
    border-right: none;
    border-top: none;
    flex-shrink: 0;
    z-index: 500;
    position: relative;
  }

  .stats-inner {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: 0.625rem 1.25rem;
    gap: 1rem;
  }

  .stats-group {
    display: flex;
    align-items: center;
    gap: 1rem;
  }

  .stat-item {
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 1px;
  }

  .stat-value {
    font-family: var(--font-serif);
    font-weight: 700;
    font-size: 1.125rem;
    color: var(--ink);
    line-height: 1.2;
  }

  .stat-label {
    font-family: var(--font-sans);
    font-size: 0.6rem;
    font-weight: 600;
    text-transform: uppercase;
    letter-spacing: 0.08em;
    color: var(--ink-muted);
  }

  .stat-divider {
    width: 1px;
    height: 28px;
    background: var(--border-rule);
  }

  .stats-actions {
    display: flex;
    align-items: center;
    gap: 1rem;
  }

  .legend {
    display: flex;
    align-items: center;
    gap: 6px;
  }

  .legend-dot {
    width: 8px;
    height: 8px;
    border-radius: 50%;
    border: 1.5px solid white;
    box-shadow: 0 0 0 0.5px rgba(0,0,0,0.15);
    cursor: help;
  }

  .btn-geocode {
    padding: 0.375rem 0.875rem;
    font-size: 0.7rem;
    font-weight: 600;
    font-family: var(--font-sans);
    background: var(--ink);
    color: var(--parchment);
    border: none;
    border-radius: 0.375rem;
    cursor: pointer;
    transition: opacity var(--duration-fast);
    white-space: nowrap;
  }

  .btn-geocode:disabled {
    opacity: 0.4;
    cursor: not-allowed;
  }

  .btn-geocode:hover:not(:disabled) {
    opacity: 0.85;
  }

  .geocode-progress-track {
    height: 2px;
    background: var(--parchment);
  }

  .geocode-progress-bar {
    height: 100%;
    background: var(--accent);
    transition: width 0.3s ease;
  }

  .map-container {
    flex: 1;
    min-height: 0;
  }

  /* Override Leaflet popup styles for archival theme */
  :global(.leaflet-popup-content-wrapper) {
    border-radius: 8px !important;
    box-shadow: 0 4px 16px rgba(26, 22, 18, 0.15) !important;
    border: 1px solid rgba(26, 22, 18, 0.08) !important;
  }

  :global(.leaflet-popup-content) {
    margin: 10px 12px !important;
    font-size: 12px !important;
  }

  :global(.leaflet-popup-tip) {
    box-shadow: 0 2px 4px rgba(26, 22, 18, 0.1) !important;
  }

  :global(.leaflet-control-zoom a) {
    background: var(--vellum) !important;
    color: var(--ink) !important;
    border-color: var(--border-subtle) !important;
  }

  :global(.leaflet-control-zoom a:hover) {
    background: var(--parchment) !important;
  }

  :global(.leaflet-control-attribution) {
    font-size: 9px !important;
    background: rgba(245, 240, 232, 0.85) !important;
    color: var(--ink-faint) !important;
  }

  /* Custom marker - remove default leaflet icon background */
  :global(.custom-marker) {
    background: none !important;
    border: none !important;
  }

  @media (max-width: 768px) {
    .map-page {
      height: calc(100vh - 44px);
    }

    .stats-inner {
      flex-direction: column;
      padding: 0.5rem 0.75rem;
      gap: 0.5rem;
    }

    .stats-group {
      gap: 0.75rem;
    }

    .stat-value {
      font-size: 0.95rem;
    }
  }
</style>
