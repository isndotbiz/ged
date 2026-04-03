<script lang="ts">
  import { t } from '$lib/i18n';
  import { getPersons, getEvents, getSpouseFamilies, getChildren, getParents, getMediaWithPaths, getPrimaryPhoto, getGroups, batchAddToGroup, batchBookmark, batchRemoveBookmarks, batchDeletePersons } from '$lib/db';
  import { runResearchAgent } from '$lib/research-agent';
  import { findSources } from '$lib/source-finder';
  import { exportSubsetGedcom } from '$lib/gedcom-exporter';
  import type { Person, GedcomEvent, Family, GedcomMedia } from '$lib/types';
  import { isTauri } from '$lib/platform';
  import { lazyImage } from '$lib/lazy-image';

  // Sync file src converter — loaded once on init
  let _convertFileSrc: ((path: string) => string) | null = null;
  async function initFileSrc() {
    if (isTauri()) {
      const mod = await import('@tauri-apps/api/core');
      _convertFileSrc = mod.convertFileSrc;
    }
  }
  function convertFileSrc(path: string): string {
    if (_convertFileSrc) return _convertFileSrc(path);
    return path;
  }

  $effect(() => { initFileSrc(); });
  import { VList } from 'virtua/svelte';

  let search = $state('');
  let isResearchingPerson = $state(false);
  let researchPersonMsg = $state('');
  // $state.raw for large arrays — 79x faster than $state for iteration
  let persons = $state.raw<Person[]>([]);
  let selected = $state<Person | null>(null);
  let selectedEvents = $state.raw<GedcomEvent[]>([]);
  let selectedFamilies = $state.raw<{ family: Family; spouse: Person | null; children: Person[] }[]>([]);
  let selectedParents = $state<{ father: Person | null; mother: Person | null }>({ father: null, mother: null });
  let selectedMedia = $state.raw<GedcomMedia[]>([]);
  let selectedPrimaryPhoto = $state<GedcomMedia | null>(null);
  let expandedMedia = $state<GedcomMedia | null>(null);
  let selectedXrefs = $state.raw<Set<string>>(new Set());
  let lastCheckedIndex = $state<number | null>(null);
  let selectedGroupId = $state<number>(0);
  let groups = $state.raw<{ id: number; name: string }[]>([]);

  // Photo cache for list avatars
  let photoCache = $state.raw<Map<string, string>>(new Map());

  let searchTimeout: ReturnType<typeof setTimeout>;

  async function load() {
    persons = await getPersons(search, 2000);
    groups = await getGroups();
    loadPhotos(persons.slice(0, 50));
  }

  function togglePersonSelection(xref: string, index: number, shiftKey: boolean) {
    const next = new Set(selectedXrefs);
    if (shiftKey && lastCheckedIndex !== null) {
      const [a, b] = [lastCheckedIndex, index].sort((x, y) => x - y);
      for (let i = a; i <= b; i++) next.add(persons[i].xref);
    } else if (next.has(xref)) {
      next.delete(xref);
    } else {
      next.add(xref);
    }
    lastCheckedIndex = index;
    selectedXrefs = next;
  }

  function toggleSelectAll() {
    if (selectedXrefs.size === persons.length) {
      selectedXrefs = new Set();
      return;
    }
    selectedXrefs = new Set(persons.map((p) => p.xref));
  }

  async function applyBatchBookmark(remove = false) {
    const xrefs = Array.from(selectedXrefs);
    if (xrefs.length === 0) return;
    if (remove) await batchRemoveBookmarks(xrefs);
    else await batchBookmark(xrefs);
    selectedXrefs = new Set();
  }

  async function applyBatchGroup() {
    const xrefs = Array.from(selectedXrefs);
    if (!selectedGroupId || xrefs.length === 0) return;
    await batchAddToGroup(xrefs, selectedGroupId);
    selectedXrefs = new Set();
  }

  async function applyBatchDelete() {
    const xrefs = Array.from(selectedXrefs);
    if (xrefs.length === 0) return;
    if (!confirm(`Delete ${xrefs.length} selected people?`)) return;
    await batchDeletePersons(xrefs);
    selectedXrefs = new Set();
    await load();
  }

  async function exportSelected() {
    const xrefs = Array.from(selectedXrefs);
    if (xrefs.length === 0) return;
    const gedcom = await exportSubsetGedcom(xrefs);
    const blob = new Blob([gedcom], { type: 'text/plain' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `gedfix_subset_${new Date().toISOString().slice(0, 10)}.ged`;
    a.click();
    URL.revokeObjectURL(url);
  }

  async function loadPhotos(people: Person[]) {
    const updates = new Map(photoCache);
    for (const p of people) {
      if (updates.has(p.xref) || p.mediaCount === 0) continue;
      const photo = await getPrimaryPhoto(p.xref);
      if (photo?.filePath) updates.set(p.xref, photo.filePath);
    }
    if (updates.size !== photoCache.size) photoCache = updates;
  }

  function onSearchInput() {
    clearTimeout(searchTimeout);
    searchTimeout = setTimeout(load, 150);
  }

  async function selectPerson(p: Person) {
    selected = p;
    // Parallel data fetching
    const [events, parents, media, photo, fams] = await Promise.all([
      getEvents(p.xref),
      getParents(p.xref),
      getMediaWithPaths(p.xref),
      getPrimaryPhoto(p.xref),
      getSpouseFamilies(p.xref),
    ]);
    selectedEvents = events;
    selectedParents = parents;
    selectedMedia = media;
    selectedPrimaryPhoto = photo;
    expandedMedia = null;

    // Load family details
    const famDetails = await Promise.all(fams.map(async (f) => {
      const spouseXref = f.partner1Xref === p.xref ? f.partner2Xref : f.partner1Xref;
      const { getPerson } = await import('$lib/db');
      const [spouse, children] = await Promise.all([
        spouseXref ? getPerson(spouseXref) : Promise.resolve(null),
        getChildren(f.xref),
      ]);
      return { family: f, spouse, children };
    }));
    selectedFamilies = famDetails;
  }

  function getInitials(p: Person): string {
    return ((p.givenName?.[0] ?? '') + (p.surname?.[0] ?? '')).toUpperCase() || '?';
  }

  function avatarColor(p: Person): string {
    return p.personColor || (p.sex === 'F' ? '#D94A8C' : '#4A90D9');
  }

  function formatDates(p: Person): string {
    const parts: string[] = [];
    if (p.birthDate) parts.push(`b. ${p.birthDate}`);
    if (p.deathDate) parts.push(`d. ${p.deathDate}`);
    return parts.join(' \u2013 ');
  }

  function isImage(path: string): boolean {
    return /\.(jpe?g|png|gif|bmp|webp)$/i.test(path);
  }

  function fileType(path: string): string {
    if (/\.pdf$/i.test(path)) return 'PDF';
    if (/\.html?$/i.test(path)) return 'HTM';
    if (/\.docx?$/i.test(path)) return 'DOC';
    return 'FILE';
  }

  const eventLabels: Record<string, string> = {
    BIRT: 'Birth', DEAT: 'Death', BURI: 'Burial', CHR: 'Christening',
    BAPM: 'Baptism', MARR: 'Marriage', DIV: 'Divorce', RESI: 'Residence',
    OCCU: 'Occupation', EDUC: 'Education', EMIG: 'Emigration', IMMI: 'Immigration',
    NATU: 'Naturalization', CENS: 'Census', PROB: 'Probate', WILL: 'Will', EVEN: 'Event',
    RELI: 'Religion', GRAD: 'Graduation', MILI: 'Military',
  };

  function eventIcon(type: string): string {
    const icons: Record<string, string> = {
      'BIRT': 'Born', 'DEAT': 'Died', 'MARR': 'Married', 'BURI': 'Buried',
      'BAPM': 'Baptized', 'CHR': 'Christened', 'EMIG': 'Emigrated', 'IMMI': 'Immigrated',
      'NATU': 'Naturalized', 'CENS': 'Census', 'RESI': 'Residence', 'OCCU': 'Occupation',
      'EDUC': 'Education', 'RELI': 'Religion', 'GRAD': 'Graduated', 'MILI': 'Military',
      'PROB': 'Probate', 'WILL': 'Will', 'DIV': 'Divorced', 'EVEN': 'Event',
    };
    return icons[type] || eventLabels[type] || type;
  }

  async function researchSelected() {
    if (!selected) return;
    isResearchingPerson = true;
    researchPersonMsg = 'Researching...';
    try {
      const result = await runResearchAgent(selected.xref, (p) => {
        researchPersonMsg = p.message;
      });
      researchPersonMsg = `Found ${result.proposalCount} proposals. Check Proposals page.`;
    } catch (e) {
      researchPersonMsg = `Error: ${e}`;
    }
    setTimeout(() => { isResearchingPerson = false; researchPersonMsg = ''; }, 3000);
  }

  async function findSourcesForSelected() {
    if (!selected) return;
    isResearchingPerson = true;
    researchPersonMsg = 'Searching records...';
    try {
      const result = await findSources(selected.xref, (p) => {
        researchPersonMsg = p.message;
      });
      researchPersonMsg = `Found ${result.sourcesFound} sources. Check Proposals page.`;
    } catch (e) {
      researchPersonMsg = `Error: ${e}`;
    }
    setTimeout(() => { isResearchingPerson = false; researchPersonMsg = ''; }, 3000);
  }

  $effect(() => { load(); });
</script>

<div class="flex h-full">
  <!-- Person List with Virtual Scrolling -->
  <div class="w-[340px] flex flex-col shrink-0" style="border-right: 1px solid var(--border-rule); background: color-mix(in srgb, var(--vellum) 50%, transparent);">
    <div class="p-3" style="border-bottom: 1px solid var(--border-rule);">
      <div class="mb-2">
        <label class="text-xs text-ink-muted inline-flex items-center gap-2">
          <input type="checkbox" checked={selectedXrefs.size === persons.length && persons.length > 0} onchange={toggleSelectAll} />
          Select all
        </label>
      </div>
      <input
        type="text"
        placeholder="Search {persons.length} people..."
        bind:value={search}
        oninput={onSearchInput}
        class="w-full px-3 py-2 text-sm rounded-lg border-none outline-none transition-colors arch-input"
      />
    </div>

    <!-- Virtual scroll list — only renders visible items, handles 10K+ -->
    <div class="flex-1" style="contain: strict;">
      <VList data={persons} style="height: 100%;" getKey={(_, i) => persons[i]?.xref ?? i}>
        {#snippet children(person, index)}
          <button
            onclick={() => selectPerson(person)}
            class="w-full flex items-center gap-3 px-4 py-2.5 text-left hover:bg-black/[0.03] transition-colors contain-content
                   {selected?.xref === person.xref ? 'bg-[var(--accent-subtle)]' : ''}"
          >
            <input
              type="checkbox"
              checked={selectedXrefs.has(person.xref)}
              onclick={(e) => {
                e.stopPropagation();
                togglePersonSelection(person.xref, index, (e as MouseEvent).shiftKey);
              }}
              aria-label={`Select ${person.givenName} ${person.surname}`}
            />
            {#if photoCache.get(person.xref)}
              <img
                use:lazyImage={photoCache.get(person.xref)!}
                alt=""
                class="w-8 h-8 rounded-full object-cover shrink-0"
                style="background: var(--parchment);"
              />
            {:else}
              <div
                class="w-8 h-8 rounded-full flex items-center justify-center text-white text-xs font-semibold shrink-0"
                style="background: {avatarColor(person)}"
              >
                {getInitials(person)}
              </div>
            {/if}
            <div class="flex-1 min-w-0">
              <div class="text-sm font-medium text-ink truncate">
                {person.givenName} {person.surname}{person.suffix ? ` ${person.suffix}` : ''}
              </div>
              <div class="text-xs text-ink-muted truncate">{formatDates(person)}</div>
            </div>
            {#if person.mediaCount > 0}
              <span class="text-[10px] text-ink-faint tabular-nums">{person.mediaCount}</span>
            {/if}
            {#if person.sourceCount > 0}
              <span class="w-1.5 h-1.5 rounded-full bg-green-400 shrink-0" title="Has sources"></span>
            {/if}
          </button>
        {/snippet}
      </VList>
    </div>

    <div class="px-4 py-2 text-xs text-ink-faint tabular-nums" style="border-top: 1px solid var(--border-rule);">
      {persons.length} people
    </div>
  </div>

  <!-- Person Detail -->
  <div class="flex-1 overflow-auto">
    {#if selected}
      <div class="p-8 max-w-3xl animate-fade-in">
        <!-- Header -->
        <div class="flex items-start gap-5 mb-8">
          {#if selectedPrimaryPhoto?.filePath}
            <img
              src={convertFileSrc(selectedPrimaryPhoto.filePath)}
              alt={selected.givenName}
              class="w-24 h-24 rounded-2xl object-cover shadow-md shrink-0"
              onerror={(e) => { (e.target as HTMLImageElement).style.display = 'none'; }}
            />
          {:else}
            <div
              class="w-24 h-24 rounded-2xl flex items-center justify-center text-white text-2xl font-bold shrink-0 shadow-md"
              style="background: {avatarColor(selected)}"
            >
              {getInitials(selected)}
            </div>
          {/if}
          <div>
            <h1 class="text-xl font-bold text-ink">
              {selected.givenName} {selected.surname}{selected.suffix ? ` ${selected.suffix}` : ''}
            </h1>
            <p class="text-sm text-ink-muted mt-0.5">{formatDates(selected)}</p>
            {#if selected.birthPlace}
              <p class="text-xs text-ink-faint mt-0.5">{selected.birthPlace}</p>
            {/if}
            <div class="flex items-center gap-2 mt-2 flex-wrap">
              <span class="text-xs px-2 py-0.5 rounded-full {selected.sex === 'F' ? 'bg-pink-100 text-pink-600' : 'bg-blue-100 text-blue-600'}">
                {selected.sex === 'F' ? 'Female' : selected.sex === 'M' ? 'Male' : 'Unknown'}
              </span>
              {#if selected.isLiving}
                <span class="text-xs px-2 py-0.5 rounded-full bg-green-100 text-green-600">{t('people.living')}</span>
              {/if}
              {#if selected.proofStatus && selected.proofStatus !== 'UNKNOWN'}
                <span class="text-xs px-2 py-0.5 rounded-full bg-purple-100 text-purple-600">{selected.proofStatus}</span>
              {/if}
              {#if selected.sourceCount > 0}
                <span class="text-xs px-2 py-0.5 rounded-full bg-blue-50 text-blue-600">{selected.sourceCount} sources</span>
              {/if}
              <span class="text-xs text-ink-faint">{selected.xref}</span>
            </div>
          </div>
        </div>

        <!-- Research actions -->
        <div class="flex gap-2 mt-3 mb-4">
          <button onclick={researchSelected} disabled={isResearchingPerson}
            class="px-3 py-1.5 text-xs font-medium rounded-md text-white transition-colors disabled:opacity-50"
            style="background: var(--accent);"
          >{isResearchingPerson ? '...' : 'AI Research'}</button>
          <button onclick={findSourcesForSelected} disabled={isResearchingPerson}
            class="px-3 py-1.5 text-xs font-medium rounded-md transition-colors disabled:opacity-50"
            style="background: var(--parchment); color: var(--ink);"
          >Find Sources</button>
        </div>
        {#if researchPersonMsg}
          <div class="text-xs text-ink-muted mb-3 italic">{researchPersonMsg}</div>
        {/if}

        <!-- Data completeness -->
        {#if selected}
          {@const fields = ['birthDate', 'birthPlace', 'deathDate', 'deathPlace']}
          {@const filled = fields.filter(f => (selected as any)[f]).length}
          {@const pct = Math.round((filled / fields.length) * 100)}
          <div class="mt-3 mb-4">
            <div class="flex items-center justify-between mb-1">
              <span class="text-[10px] text-ink-faint">Data completeness</span>
              <span class="text-[10px] font-medium" style="color: {pct >= 75 ? 'var(--color-validated)' : pct >= 50 ? 'var(--color-warning)' : 'var(--color-error)'};">{pct}%</span>
            </div>
            <div class="w-full h-1 rounded-full" style="background: var(--parchment);">
              <div class="h-full rounded-full transition-all" style="width: {pct}%; background: {pct >= 75 ? 'var(--color-validated)' : pct >= 50 ? 'var(--color-warning)' : 'var(--color-error)'};"></div>
            </div>
            {#if filled < fields.length}
              <div class="flex flex-wrap gap-1 mt-1.5">
                {#each fields.filter(f => !(selected as any)[f]) as missing}
                  <span class="text-[9px] px-1.5 py-0.5 rounded" style="background: rgba(166,61,47,0.08); color: var(--color-error);">
                    Missing {missing.replace(/([A-Z])/g, ' $1').toLowerCase()}
                  </span>
                {/each}
              </div>
            {/if}
          </div>
        {/if}

        <!-- Parents -->
        {#if selectedParents.father || selectedParents.mother}
          <section class="mb-6">
            <h2 class="arch-section-header">Parents</h2>
            <div class="flex gap-3">
              {#each [selectedParents.father, selectedParents.mother].filter(Boolean) as parent}
                <button onclick={() => selectPerson(parent!)} class="flex items-center gap-2 px-3 py-2 rounded-lg arch-card hover:opacity-80 transition-colors">
                  <div class="w-6 h-6 rounded-full flex items-center justify-center text-white text-[10px] font-semibold" style="background: {avatarColor(parent!)}">
                    {getInitials(parent!)}
                  </div>
                  <span class="text-sm text-ink-light">{parent!.givenName} {parent!.surname}</span>
                </button>
              {/each}
            </div>
          </section>
        {/if}

        <!-- Media Gallery -->
        {#if selectedMedia.length > 0}
          <section class="mb-6">
            <h2 class="arch-section-header">
              Photos & Documents ({selectedMedia.length})
            </h2>
            <div class="grid grid-cols-4 gap-3">
              {#each selectedMedia as m (m.id)}
                {#if isImage(m.filePath)}
                  <button
                    onclick={() => expandedMedia = expandedMedia?.id === m.id ? null : m}
                    class="aspect-square rounded-xl overflow-hidden hover:ring-2 transition-all contain-content {expandedMedia?.id === m.id ? 'ring-2' : ''}"
                    style="background: var(--parchment); border: 1px solid var(--border-subtle); --tw-ring-color: var(--accent);"
                  >
                    <img
                      use:lazyImage={m.filePath}
                      alt={m.title || 'Photo'}
                      class="w-full h-full object-cover"
                    />
                  </button>
                {:else}
                  <div class="aspect-square rounded-xl flex flex-col items-center justify-center gap-1 p-2 contain-content" style="background: var(--parchment); border: 1px solid var(--border-subtle);">
                    <span class="text-xs font-bold text-ink-faint">{fileType(m.filePath)}</span>
                    <span class="text-[10px] text-ink-faint text-center truncate w-full">{m.title || m.filePath.split('/').pop()}</span>
                  </div>
                {/if}
              {/each}
            </div>

            {#if expandedMedia}
              <div class="mt-4 arch-card rounded-xl overflow-hidden" style="box-shadow: var(--shadow-lg);">
                <img
                  src={convertFileSrc(expandedMedia.filePath)}
                  alt={expandedMedia.title || 'Photo'}
                  class="w-full max-h-[500px] object-contain"
                  style="background: var(--parchment);"
                />
                <div class="p-3" style="border-top: 1px solid var(--border-subtle);">
                  <div class="text-sm font-medium text-ink">{expandedMedia.title || expandedMedia.filePath.split('/').pop()}</div>
                  <div class="text-[10px] text-ink-faint mt-0.5 truncate">{expandedMedia.filePath}</div>
                </div>
              </div>
            {/if}
          </section>
        {/if}

        <!-- Events -->
        {#if selectedEvents.length > 0}
          <section class="mb-6">
            <h2 class="arch-section-header">{t('people.lifeEvents')}</h2>
            <div class="arch-card rounded-xl divide-y arch-card-divide">
              {#each selectedEvents as ev (ev.id)}
                <div class="flex items-start gap-3 px-4 py-3 contain-content">
                  <div class="w-2 h-2 rounded-full mt-1.5 shrink-0
                    {ev.eventType === 'BIRT' ? 'bg-green-400' :
                     ev.eventType === 'DEAT' ? 'bg-gray-400' :
                     ev.eventType === 'MARR' ? 'bg-pink-400' :
                     ev.eventType === 'BURI' ? 'bg-amber-700' : 'bg-blue-400'}"></div>
                  <div class="flex-1 min-w-0">
                    <div class="text-sm font-medium text-ink">{eventIcon(ev.eventType)}</div>
                    {#if ev.dateValue}<div class="text-xs text-ink-muted">{ev.dateValue}</div>{/if}
                    {#if ev.place}<div class="text-xs text-ink-faint">{ev.place}</div>{/if}
                    {#if ev.description}<div class="text-xs text-ink-faint italic">{ev.description}</div>{/if}
                  </div>
                </div>
              {/each}
            </div>
          </section>
        {/if}

        <!-- Families -->
        {#if selectedFamilies.length > 0}
          <section class="mb-6">
            <h2 class="arch-section-header">{t('dashboard.families')}</h2>
            {#each selectedFamilies as sf (sf.family.xref)}
              <div class="arch-card rounded-xl p-4 mb-3 contain-content">
                {#if sf.spouse}
                  <div class="flex items-center gap-2 mb-3">
                    <span class="text-xs text-ink-faint">Spouse:</span>
                    <button onclick={() => selectPerson(sf.spouse!)} class="flex items-center gap-2 hover:opacity-80 rounded-md px-2 py-1 transition-colors">
                      <div class="w-5 h-5 rounded-full flex items-center justify-center text-white text-[9px] font-semibold" style="background: {avatarColor(sf.spouse)}">
                        {getInitials(sf.spouse)}
                      </div>
                      <span class="text-sm text-ink-light">{sf.spouse.givenName} {sf.spouse.surname}</span>
                    </button>
                    {#if sf.family.marriageDate}
                      <span class="text-xs text-ink-faint ml-auto">m. {sf.family.marriageDate}</span>
                    {/if}
                  </div>
                {/if}
                {#if sf.children.length > 0}
                  <div class="text-xs text-ink-faint mb-2">Children ({sf.children.length}):</div>
                  <div class="flex flex-wrap gap-2">
                    {#each sf.children as child (child.xref)}
                      <button onclick={() => selectPerson(child)} class="flex items-center gap-1.5 px-2 py-1 rounded-md hover:opacity-80 transition-colors">
                        <div class="w-5 h-5 rounded-full flex items-center justify-center text-white text-[9px] font-semibold" style="background: {avatarColor(child)}">
                          {getInitials(child)}
                        </div>
                        <span class="text-xs text-ink-light">{child.givenName} {child.surname}</span>
                      </button>
                    {/each}
                  </div>
                {/if}
              </div>
            {/each}
          </section>
        {/if}
      </div>
    {:else}
      <div class="flex items-center justify-center h-full text-sm text-ink-faint">
        {t('people.selectPrompt')}
      </div>
    {/if}
  </div>
</div>

{#if selectedXrefs.size > 0}
  <div class="batch-bar no-print">
    <span>{selectedXrefs.size} selected</span>
    <select bind:value={selectedGroupId} class="arch-input">
      <option value={0}>Add to group...</option>
      {#each groups as group}
        <option value={group.id}>{group.name}</option>
      {/each}
    </select>
    <button class="btn-secondary px-3 py-2" onclick={applyBatchGroup}>Add Group</button>
    <button class="btn-secondary px-3 py-2" onclick={() => applyBatchBookmark(false)}>Bookmark</button>
    <button class="btn-secondary px-3 py-2" onclick={() => applyBatchBookmark(true)}>Unbookmark</button>
    <button class="btn-secondary px-3 py-2" onclick={exportSelected}>{t('common.export')}</button>
    <button class="btn-danger px-3 py-2" onclick={applyBatchDelete}>{t('common.delete')}</button>
  </div>
{/if}

<style>
  .batch-bar {
    position: fixed;
    left: calc(var(--sidebar-width) + 1rem);
    right: 1rem;
    bottom: 1rem;
    z-index: 40;
    display: flex;
    align-items: center;
    gap: 0.5rem;
    padding: 0.6rem;
    border: 1px solid var(--border-rule);
    border-radius: 0.6rem;
    background: var(--vellum);
  }
  @media (max-width: 767px) {
    .batch-bar {
      left: 0.5rem;
      right: 0.5rem;
      flex-wrap: wrap;
    }
  }
</style>
