<script lang="ts">
  import { t } from '$lib/i18n';
  import { getPersons, getPerson, getParents, getMediaWithPaths, getPrimaryPhoto, getSpouseFamilies, getChildren, getEvents } from '$lib/db';
  import type { Person, GedcomEvent, GedcomMedia } from '$lib/types';
  import { getCachedFaceCrop, faceCropToObjectPosition } from '$lib/face-crop';
  import { isTauri } from '$lib/platform';

  let _convertFileSrc: ((path: string) => string) | null = null;
  let _invoke: ((cmd: string, args?: Record<string, unknown>) => Promise<unknown>) | null = null;
  async function initTauriCore() {
    if (isTauri()) {
      const mod = await import('@tauri-apps/api/core');
      _convertFileSrc = mod.convertFileSrc;
      _invoke = mod.invoke;
    }
  }
  function convertFileSrc(path: string): string {
    if (_convertFileSrc) return _convertFileSrc(path);
    return path;
  }
  async function invoke<T>(cmd: string, args?: Record<string, unknown>): Promise<T> {
    if (_invoke) return _invoke(cmd, args) as Promise<T>;
    return '' as unknown as T;
  }

  $effect(() => { initTauriCore(); });

  // Cache of base64 data URLs for photos
  let photoCache = $state<Map<string, string>>(new Map());

  async function loadPhoto(path: string): Promise<string> {
    if (!path) return '';
    if (photoCache.has(path)) return photoCache.get(path)!;
    try {
      const dataUrl = await invoke<string>('read_image_base64', { path });
      photoCache = new Map(photoCache).set(path, dataUrl);
      return dataUrl;
    } catch {
      // Fallback to convertFileSrc
      return convertFileSrc(path);
    }
  }

  function photoSrc(path: string): string {
    if (!path) return '';
    return photoCache.get(path) ?? convertFileSrc(path);
  }

  interface AncestorRow {
    person: Person | null;
    photoPath: string;
    thumbPath: string;
    objectPosition: string;
  }

  let persons = $state.raw<Person[]>([]);
  let search = $state('');
  let maxGen = $state(5);
  let layout = $state<'side' | 'down'>('side');
  let ancestors = $state<AncestorRow[]>([]);
  let rootPerson = $state<Person | null>(null);
  let selected = $state<Person | null>(null);
  let selectedMedia = $state.raw<GedcomMedia[]>([]);

  async function load() {
    try {
      persons = await getPersons('', 2000);
      if (persons.length > 0 && !rootPerson) {
        // Find Jonathan directly by xref
        let me = await getPerson('I2');
        if (!me) me = persons.find(p => p.givenName === 'Jonathan' && p.surname === 'Mallinger') ?? persons[0];
        if (me) await buildTree(me);
      }
    } catch (e) {
      console.error('load DB error:', e);
    }
  }

  async function getThumb(filePath: string): Promise<string> {
    if (!filePath) return '';
    try {
      const thumbPath = await invoke<string>('generate_thumbnail', { path: filePath, size: 120 });
      return thumbPath;
    } catch {
      return filePath; // fallback to original
    }
  }

  async function buildTree(person: Person) {
    rootPerson = person;
    selected = null;
    selectedMedia = [];

    const maxSlots = Math.pow(2, maxGen) - 1;
    const arr: AncestorRow[] = new Array(maxSlots + 1).fill(null).map(() => ({ person: null, photoPath: '', thumbPath: '', objectPosition: '50% 50%' }));

    async function fill(xref: string, idx: number) {
      if (idx > maxSlots) return;
      const p = await getPerson(xref);
      if (!p) return;
      const photo = await getPrimaryPhoto(p.xref);
      const photoPath = photo?.filePath ?? '';
      const thumbPath = photoPath ? await getThumb(photoPath) : '';
      let objectPosition = '50% 50%';
      if (photo?.title?.startsWith('crop:')) {
        const parts = photo.title.replace('crop:', '').split(',');
        const x = parseFloat(parts[0] ?? '50');
        const y = parseFloat(parts[1] ?? '30');
        objectPosition = `${x}% ${y}%`;
      } else if (photo?.id && photoPath) {
        const crop = await getCachedFaceCrop(photo.id, convertFileSrc(thumbPath || photoPath));
        if (crop) objectPosition = faceCropToObjectPosition(crop);
      }
      arr[idx] = { person: p, photoPath, thumbPath, objectPosition };
      const parents = await getParents(p.xref);
      if (parents.father) await fill(parents.father.xref, idx * 2);
      if (parents.mother) await fill(parents.mother.xref, idx * 2 + 1);
    }

    await fill(person.xref, 1);
    ancestors = arr;

    // Preload photos via Rust base64 command
    for (const a of arr) {
      if (a.photoPath) loadPhoto(a.photoPath);
    }
  }

  async function clickPerson(p: Person) {
    selected = p;
    selectedMedia = await getMediaWithPaths(p.xref);
  }

  async function setRoot(p: Person) {
    search = '';
    await buildTree(p);
  }

  function isMale(p: Person): boolean { return p.sex === 'M'; }
  function isFemale(p: Person): boolean { return p.sex === 'F'; }

  function _borderColor(p: Person): string {
    return isFemale(p) ? '#D94A8C' : '#4A90D9';
  }

  function _bgColor(p: Person): string {
    return isFemale(p) ? '#FDF2F8' : '#EFF6FF';
  }

  function avatarBg(p: Person): string {
    return isFemale(p) ? '#D94A8C' : '#4A90D9';
  }

  function initials(p: Person): string {
    return ((p.givenName?.[0] ?? '') + (p.surname?.[0] ?? '')).toUpperCase();
  }

  // --- Export PNG/SVG ---
  async function downloadPng() {
    const date = new Date().toISOString().slice(0, 10);
    const name = rootPerson ? `${rootPerson.givenName}-${rootPerson.surname}`.replace(/\s+/g, '-') : 'chart';
    const filename = `gedfix-pedigree-${name}-${date}.png`;

    const treeEl = document.getElementById('pedigree-tree-area');
    if (!treeEl) return;

    const rect = treeEl.getBoundingClientRect();
    const canvas = document.createElement('canvas');
    canvas.width = rect.width * window.devicePixelRatio;
    canvas.height = rect.height * window.devicePixelRatio;
    const ctx = canvas.getContext('2d')!;
    ctx.scale(window.devicePixelRatio, window.devicePixelRatio);
    ctx.fillStyle = '#f5f0e8';
    ctx.fillRect(0, 0, rect.width, rect.height);

    const svgData = `<svg xmlns="http://www.w3.org/2000/svg" width="${rect.width}" height="${rect.height}">
      <foreignObject width="100%" height="100%">
        <div xmlns="http://www.w3.org/1999/xhtml" style="width:${rect.width}px;height:${rect.height}px;overflow:hidden;">
          ${treeEl.innerHTML}
        </div>
      </foreignObject>
    </svg>`;

    const blob = new Blob([svgData], { type: 'image/svg+xml' });
    const url = URL.createObjectURL(blob);
    const img = new Image();
    img.onload = () => {
      ctx.drawImage(img, 0, 0);
      URL.revokeObjectURL(url);
      canvas.toBlob((b) => {
        if (!b) return;
        const a = document.createElement('a');
        a.href = URL.createObjectURL(b);
        a.download = filename;
        a.click();
      }, 'image/png');
    };
    img.src = url;
  }

  // --- Print mode state ---
  let printMode = $state<'none' | 'pedigree' | 'family-group'>('none');

  interface FamilyGroupData {
    husband: Person | null;
    wife: Person | null;
    marriageDate: string;
    marriagePlace: string;
    husbandEvents: GedcomEvent[];
    wifeEvents: GedcomEvent[];
    children: { person: Person; events: GedcomEvent[] }[];
    familyXref: string;
  }

  let familyGroupData = $state<FamilyGroupData | null>(null);

  function handlePrintPedigree() {
    printMode = 'pedigree';
    // Use tick-based delay so DOM updates before print dialog
    setTimeout(() => {
      window.print();
      printMode = 'none';
    }, 100);
  }

  async function handlePrintFamilyGroup() {
    if (!rootPerson) return;
    const families = await getSpouseFamilies(rootPerson.xref);
    const fam = families[0] ?? null;

    let husband: Person | null = null;
    let wife: Person | null = null;
    let husbandEvents: GedcomEvent[] = [];
    let wifeEvents: GedcomEvent[] = [];
    let childrenData: { person: Person; events: GedcomEvent[] }[] = [];

    if (fam) {
      const p1 = fam.partner1Xref ? await getPerson(fam.partner1Xref) : null;
      const p2 = fam.partner2Xref ? await getPerson(fam.partner2Xref) : null;
      // Assign husband/wife by sex
      if (p1?.sex === 'F') { wife = p1; husband = p2; }
      else if (p2?.sex === 'F') { wife = p2; husband = p1; }
      else { husband = p1; wife = p2; }

      if (husband) husbandEvents = await getEvents(husband.xref);
      if (wife) wifeEvents = await getEvents(wife.xref);

      const kids = await getChildren(fam.xref);
      for (const kid of kids) {
        const kidEvents = await getEvents(kid.xref);
        childrenData.push({ person: kid, events: kidEvents });
      }
    } else {
      // No family found - show root person as husband with their events
      if (rootPerson.sex === 'F') {
        wife = rootPerson;
        wifeEvents = await getEvents(rootPerson.xref);
      } else {
        husband = rootPerson;
        husbandEvents = await getEvents(rootPerson.xref);
      }
    }

    familyGroupData = {
      husband,
      wife,
      marriageDate: fam?.marriageDate ?? '',
      marriagePlace: fam?.marriagePlace ?? '',
      husbandEvents,
      wifeEvents,
      children: childrenData,
      familyXref: fam?.xref ?? '',
    };

    printMode = 'family-group';
    setTimeout(() => {
      window.print();
      printMode = 'none';
    }, 100);
  }

  function formatEventType(t: string): string {
    const map: Record<string, string> = {
      BIRT: 'Birth', DEAT: 'Death', BURI: 'Burial', CHR: 'Christening',
      BAPM: 'Baptism', MARR: 'Marriage', DIV: 'Divorce', OCCU: 'Occupation',
      RESI: 'Residence', IMMI: 'Immigration', EMIG: 'Emigration', NATU: 'Naturalization',
      CENS: 'Census', GRAD: 'Graduation', RETI: 'Retirement', WILL: 'Will',
      PROB: 'Probate', SSN: 'Social Security', MILI: 'Military',
    };
    return map[t] ?? t;
  }

  let filtered = $derived(search.trim()
    ? persons.filter(p => `${p.givenName} ${p.surname}`.toLowerCase().includes(search.toLowerCase())).slice(0, 8)
    : []);

  function getGenerations(): AncestorRow[][] {
    const gens: AncestorRow[][] = [];
    for (let g = 0; g < maxGen; g++) {
      const start = Math.pow(2, g);
      const end = Math.pow(2, g + 1);
      const row: AncestorRow[] = [];
      for (let i = start; i < end; i++) {
        row.push(ancestors[i] ?? { person: null, photoPath: '', thumbPath: '' });
      }
      gens.push(row.map((slot) => slot ?? { person: null, photoPath: '', thumbPath: '', objectPosition: '50% 50%' }));
    }
    return gens;
  }

  $effect(() => { load(); });
</script>

<style>
  .face-photo {
    object-fit: cover;
    object-position: center 20%;
  }
  .male-card {
    background: linear-gradient(135deg, var(--card-male-bg) 0%, color-mix(in srgb, var(--card-male-bg) 80%, var(--card-male-border)) 100%);
    border: 2px solid var(--card-male-border);
  }
  .male-card:hover { border-color: var(--card-male-border-hover); box-shadow: 0 4px 12px rgba(74, 107, 138, 0.15); }
  .male-card.active { border-color: var(--card-male-border-hover); box-shadow: 0 4px 16px rgba(74, 107, 138, 0.25); }

  .female-card {
    background: linear-gradient(135deg, var(--card-female-bg) 0%, color-mix(in srgb, var(--card-female-bg) 80%, var(--card-female-border)) 100%);
    border: 2px solid var(--card-female-border);
  }
  .female-card:hover { border-color: var(--card-female-border-hover); box-shadow: 0 4px 12px rgba(138, 74, 107, 0.15); }
  .female-card.active { border-color: var(--card-female-border-hover); box-shadow: 0 4px 16px rgba(138, 74, 107, 0.25); }

  .unknown-card {
    background: linear-gradient(135deg, var(--card-unknown-bg) 0%, color-mix(in srgb, var(--card-unknown-bg) 80%, var(--card-unknown-border)) 100%);
    border: 2px solid var(--card-unknown-border);
  }

  .male-ring { box-shadow: 0 0 0 3px #3B82F6; }
  .female-ring { box-shadow: 0 0 0 3px #EC4899; }

  /* Print button styling */
  .arch-btn-ghost {
    background: transparent;
    border: 1px solid var(--border-rule, #d4c5a9);
    color: var(--ink, #3d3229);
    cursor: pointer;
  }
  .arch-btn-ghost:hover {
    background: var(--parchment, #f5f0e8);
    border-color: var(--ink-light, #6b5d4f);
  }

  /* Family Group Sheet - screen hidden, print visible */
  .fgs-print-sheet {
    display: none;
  }

  /* ============ PRINT STYLES ============ */
  @media print {
    /* Hide everything except the tree area or FGS */
    :global(nav),
    :global(.sidebar),
    :global(aside),
    :global(header),
    :global(footer) {
      display: none !important;
    }

    /* Hide toolbar, detail panel, and search in print */
    :global(.arch-toolbar) {
      display: none !important;
    }

    /* Pedigree print mode */
    .fgs-print-sheet {
      display: none !important;
    }
  }

  /* Family Group Sheet print mode overrides */
  @media print {
    .fgs-print-sheet {
      display: block !important;
      position: fixed;
      top: 0;
      left: 0;
      right: 0;
      bottom: 0;
      z-index: 10000;
      background: white;
      padding: 0.5in;
      font-family: 'Georgia', 'Times New Roman', serif;
      font-size: 11pt;
      color: #1a1a1a;
      overflow: visible;
    }
    .fgs-title {
      font-size: 18pt;
      font-weight: bold;
      text-align: center;
      margin-bottom: 2pt;
      letter-spacing: 0.5pt;
      border-bottom: 2px solid #333;
      padding-bottom: 4pt;
    }
    .fgs-subtitle {
      text-align: center;
      font-size: 9pt;
      color: #666;
      margin-bottom: 12pt;
    }
    .fgs-section {
      margin-bottom: 10pt;
      page-break-inside: avoid;
    }
    .fgs-section-title {
      font-size: 12pt;
      font-weight: bold;
      border-bottom: 1px solid #999;
      padding-bottom: 2pt;
      margin-bottom: 4pt;
    }
    .fgs-section-title.fgs-male { color: #1e40af; }
    .fgs-section-title.fgs-female { color: #9d174d; }
    .fgs-table {
      width: 100%;
      border-collapse: collapse;
    }
    .fgs-table td,
    .fgs-table th {
      padding: 2pt 4pt;
      vertical-align: top;
      text-align: left;
      border-bottom: 1px dotted #ccc;
      font-size: 10pt;
    }
    .fgs-label {
      width: 80pt;
      font-weight: bold;
      color: #555;
    }
    .fgs-value {
      color: #1a1a1a;
    }
    .fgs-children-table th {
      background: #f0f0f0;
      font-size: 9pt;
      font-weight: bold;
      border-bottom: 1px solid #999;
    }
    .fgs-child-name {
      font-weight: 600;
    }
    .fgs-child-event td {
      font-size: 9pt;
      color: #555;
      font-style: italic;
      border-bottom: none;
    }
    .fgs-empty {
      font-style: italic;
      color: #999;
      font-size: 10pt;
    }
    .fgs-source-note {
      font-size: 9pt;
      color: #555;
    }
    .fgs-footer {
      margin-top: 16pt;
      text-align: center;
      font-size: 8pt;
      color: #aaa;
      border-top: 1px solid #ddd;
      padding-top: 4pt;
    }

    /* Pedigree chart print optimizations */
    .face-photo { print-color-adjust: exact; -webkit-print-color-adjust: exact; }
    .male-card, .female-card, .unknown-card {
      print-color-adjust: exact;
      -webkit-print-color-adjust: exact;
      box-shadow: none !important;
    }
    .male-ring, .female-ring {
      print-color-adjust: exact;
      -webkit-print-color-adjust: exact;
    }
  }
</style>

<div class="flex flex-col h-full">
  <!-- Toolbar -->
  <div class="flex items-center gap-3 px-5 py-3 shrink-0 arch-toolbar">
    <h1 class="text-lg font-bold text-ink">Family Tree</h1>

    <div class="arch-tabs ml-3">
      <button onclick={() => { layout = 'side'; if (rootPerson) buildTree(rootPerson); }} class="arch-tab {layout === 'side' ? 'active' : ''}">
        Sideways
      </button>
      <button onclick={() => { layout = 'down'; if (rootPerson) buildTree(rootPerson); }} class="arch-tab {layout === 'down' ? 'active' : ''}">
        Top-Down
      </button>
    </div>

    <div class="flex items-center gap-1">
      <button onclick={() => { maxGen = Math.max(2, maxGen - 1); if (rootPerson) buildTree(rootPerson); }} class="w-6 h-6 flex items-center justify-center rounded text-ink-light text-sm" style="background: var(--parchment);">-</button>
      <span class="text-xs text-ink-muted w-10 text-center">{maxGen} gen</span>
      <button onclick={() => { maxGen = Math.min(7, maxGen + 1); if (rootPerson) buildTree(rootPerson); }} class="w-6 h-6 flex items-center justify-center rounded text-ink-light text-sm" style="background: var(--parchment);">+</button>
    </div>

    <!-- Legend -->
    <div class="flex items-center gap-3 ml-3 text-[10px]">
      <span class="flex items-center gap-1"><span class="w-3 h-3 rounded-full bg-[#4A90D9]"></span> Male</span>
      <span class="flex items-center gap-1"><span class="w-3 h-3 rounded-full bg-[#D94A8C]"></span> Female</span>
    </div>

    <!-- Print actions -->
    <div class="flex items-center gap-1.5 ml-3">
      <button onclick={handlePrintPedigree} class="px-2.5 py-1 text-[11px] rounded-lg transition-all arch-btn-ghost" title={t('pedigree.printChart')} aria-label={t('common.actions')}>
        <svg class="w-3.5 h-3.5 inline-block mr-0.5 -mt-0.5" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24"><path d="M6 9V2h12v7M6 18H4a2 2 0 01-2-2v-5a2 2 0 012-2h16a2 2 0 012 2v5a2 2 0 01-2 2h-2"/><rect x="6" y="14" width="12" height="8"/></svg>
        {t('nav.pedigree')}
      </button>
      <button onclick={handlePrintFamilyGroup} class="px-2.5 py-1 text-[11px] rounded-lg transition-all arch-btn-ghost" title={t('common.print')}>
        <svg class="w-3.5 h-3.5 inline-block mr-0.5 -mt-0.5" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24"><path d="M14 2H6a2 2 0 00-2 2v16a2 2 0 002 2h12a2 2 0 002-2V8z"/><polyline points="14 2 14 8 20 8"/><line x1="16" y1="13" x2="8" y2="13"/><line x1="16" y1="17" x2="8" y2="17"/></svg>
        Family Group
      </button>
      <button onclick={downloadPng} class="px-2.5 py-1 text-[11px] rounded-lg transition-all arch-btn-ghost" title={t('pedigree.downloadPng')}>
        <svg class="w-3.5 h-3.5 inline-block mr-0.5 -mt-0.5" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24"><path d="M21 15v4a2 2 0 01-2 2H5a2 2 0 01-2-2v-4M7 10l5 5 5-5M12 15V3"/></svg>
        {t('pedigree.downloadPng')}
      </button>
    </div>

    <div class="relative ml-auto">
      <input bind:value={search} placeholder={t('pedigree.findPerson')} class="w-52 px-3 py-1.5 text-sm rounded-lg outline-none arch-input"  aria-label={t('pedigree.findPerson')} />
      {#if filtered.length > 0}
        <div class="absolute top-full mt-1 left-0 right-0 rounded-lg z-20 max-h-48 overflow-auto" style="background: var(--vellum); box-shadow: var(--shadow-lg); border: 1px solid var(--border-rule);">
          {#each filtered as p}
            <button onclick={() => setRoot(p)} class="w-full text-left px-3 py-1.5 text-sm hover:opacity-80">
              <span class="inline-block w-2 h-2 rounded-full mr-1.5" style="background:{avatarBg(p)}"></span>
              {p.givenName} {p.surname} <span class="text-ink-faint text-xs">{p.birthDate ?? ''}</span>
            </button>
          {/each}
        </div>
      {/if}
    </div>
  </div>

  <div class="flex flex-1 overflow-hidden">
    <!-- Tree area -->
    <div id="pedigree-tree-area" class="flex-1 overflow-auto p-6" style="background: var(--paper);">
      {#if ancestors.length > 0}

        {#if layout === 'side'}
          <!-- SIDEWAYS: me on left, ancestors going right -->
          <div class="flex items-stretch" style="min-height: {Math.pow(2, maxGen - 1) * 82}px;">
            {#each getGenerations() as gen, gi}
              <div class="flex flex-col justify-around shrink-0" style="min-width: 220px;">
                {#each gen as slot}
                  {#if slot.person}
                    {@const p = slot.person}
                    <button
                      onclick={() => clickPerson(p)}
                      class="flex items-center gap-2.5 px-2.5 py-2 mx-1 my-1 rounded-xl transition-all
                             {isFemale(p) ? 'female-card' : isMale(p) ? 'male-card' : 'unknown-card'}
                             {selected?.xref === p.xref ? 'active' : ''}"
                    >
                      <!-- Face photo with gender ring -->
                      {#if slot.thumbPath || slot.photoPath}
                        <img
                          src={photoSrc(slot.thumbPath || slot.photoPath)}
                          alt=""
                          class="w-11 h-11 rounded-full face-photo shrink-0 {isFemale(p) ? 'female-ring' : 'male-ring'}"
                          style="object-position: {slot.objectPosition};"
                          onerror={(e) => { (e.target as HTMLImageElement).style.display='none'; }}
                        />
                      {:else}
                        <div class="w-11 h-11 rounded-full flex items-center justify-center text-white text-sm font-bold shrink-0" style="background:{avatarBg(p)}">
                          {initials(p)}
                        </div>
                      {/if}
                      <div class="text-left min-w-0 flex-1">
                        <div class="text-[11px] font-bold text-ink truncate">{p.givenName} {p.surname}</div>
                        <div class="text-[10px] text-ink-muted truncate">
                          {p.birthDate ? `b.${p.birthDate}` : ''}{p.deathDate ? ` d.${p.deathDate}` : ''}
                        </div>
                        <div class="text-[9px] text-ink-faint truncate">{p.birthPlace ?? ''}</div>
                      </div>
                    </button>
                  {:else}
                    <div class="mx-1 my-1 h-[60px]"></div>
                  {/if}
                {/each}
              </div>
              <!-- Connector lines -->
              {#if gi < maxGen - 1}
                <div class="w-5 shrink-0 flex flex-col justify-around">
                  {#each gen as _}
                    <div class="flex-1 flex items-center">
                      <div class="w-full border-t border-dashed border-gray-300"></div>
                    </div>
                  {/each}
                </div>
              {/if}
            {/each}
          </div>

        {:else}
          <!-- TOP-DOWN: me at top, ancestors going down -->
          <div class="flex flex-col items-center gap-1">
            {#each getGenerations() as gen, gi}
              <div class="flex justify-center gap-2 w-full">
                {#each gen as slot}
                  <div class="flex-1 flex justify-center" style="max-width: {Math.max(120, 600 / gen.length)}px;">
                    {#if slot.person}
                      {@const p = slot.person}
                      <button
                        onclick={() => clickPerson(p)}
                        class="flex flex-col items-center px-2 py-2 rounded-xl transition-all w-full
                               {isFemale(p) ? 'female-card' : isMale(p) ? 'male-card' : 'unknown-card'}
                               {selected?.xref === p.xref ? 'active' : ''}"
                      >
                        {#if slot.thumbPath || slot.photoPath}
                          <img
                            src={photoSrc(slot.thumbPath || slot.photoPath)}
                            alt=""
                            class="w-12 h-12 rounded-full face-photo mb-1 {isFemale(p) ? 'female-ring' : 'male-ring'}"
                            style="object-position: {slot.objectPosition};"
                            onerror={(e) => { (e.target as HTMLImageElement).style.display='none'; }}
                          />
                        {:else}
                          <div class="w-12 h-12 rounded-full flex items-center justify-center text-white text-sm font-bold mb-1" style="background:{avatarBg(p)}">
                            {initials(p)}
                          </div>
                        {/if}
                        <div class="text-[10px] font-bold text-ink text-center truncate w-full">{p.givenName}</div>
                        <div class="text-[10px] font-bold text-ink text-center truncate w-full">{p.surname}</div>
                        <div class="text-[8px] text-ink-muted text-center truncate w-full">
                          {p.birthDate ? `b.${p.birthDate}` : ''}
                        </div>
                      </button>
                    {:else}
                      <div class="w-full"></div>
                    {/if}
                  </div>
                {/each}
              </div>
              {#if gi < maxGen - 1}
                <div class="h-3 flex justify-center w-full">
                  <div class="border-l border-dashed border-gray-300 h-full"></div>
                </div>
              {/if}
            {/each}
          </div>
        {/if}

      {:else}
        <div class="flex items-center justify-center h-full text-ink-faint text-sm">Loading tree...</div>
      {/if}
    </div>

    <!-- Detail panel -->
    {#if selected}
      <div class="w-[320px] overflow-y-auto shrink-0 animate-fade-in" style="border-left: 1px solid var(--border-rule); background: var(--vellum);">
        <div class="p-5">
          {#if selectedMedia.length > 0}
            {@const firstImg = selectedMedia.find(m => /\.(jpe?g|png|gif|webp|bmp)$/i.test(m.filePath))}
            {#if firstImg}
              <img src={photoSrc(firstImg.filePath)} alt="" class="w-full h-52 object-cover object-top rounded-xl mb-4" style="box-shadow: var(--shadow-sm);" onerror={(e) => { (e.target as HTMLImageElement).style.display='none'; }} />
            {/if}
          {:else}
            <div class="w-full h-28 rounded-xl mb-4 flex items-center justify-center text-white text-4xl font-bold" style="background:{avatarBg(selected)}">
              {initials(selected)}
            </div>
          {/if}

          <div class="flex items-center gap-2">
            <span class="w-3 h-3 rounded-full" style="background:{avatarBg(selected)}"></span>
            <h2 class="text-lg font-bold">{selected.givenName} {selected.surname}{selected.suffix ? ` ${selected.suffix}` : ''}</h2>
          </div>
          <p class="text-xs text-ink-faint mt-0.5 ml-5">
            {isFemale(selected) ? 'Female' : isMale(selected) ? 'Male' : 'Unknown'} &middot; {selected.xref}
          </p>

          <div class="mt-3 space-y-1.5 text-sm">
            {#if selected.birthDate}<div class="flex justify-between"><span class="text-ink-faint">{t('common.born')}</span><span class="font-medium">{selected.birthDate}</span></div>{/if}
            {#if selected.birthPlace}<div class="flex justify-between gap-2"><span class="text-ink-faint shrink-0">Place</span><span class="text-right text-xs">{selected.birthPlace}</span></div>{/if}
            {#if selected.deathDate}<div class="flex justify-between"><span class="text-ink-faint">{t('common.died')}</span><span class="font-medium">{selected.deathDate}</span></div>{/if}
            {#if selected.deathPlace}<div class="flex justify-between gap-2"><span class="text-ink-faint shrink-0">Place</span><span class="text-right text-xs">{selected.deathPlace}</span></div>{/if}
            <div class="flex justify-between"><span class="text-ink-faint">{t('dashboard.sources')}</span><span>{selected.sourceCount}</span></div>
            <div class="flex justify-between"><span class="text-ink-faint">{t('common.media')}</span><span>{selected.mediaCount}</span></div>
          </div>

          <!-- All media grid -->
          {#if selectedMedia.length > 0}
            <h3 class="arch-section-header mt-5">All Media ({selectedMedia.length})</h3>
            <div class="grid grid-cols-3 gap-1.5">
              {#each selectedMedia as m}
                {#if /\.(jpe?g|png|gif|webp|bmp)$/i.test(m.filePath)}
                  <img src={photoSrc(m.filePath)} alt={m.title || ''} class="w-full aspect-square object-cover object-top rounded-lg" onerror={(e) => { (e.target as HTMLImageElement).style.display='none'; }} />
                {:else if m.filePath}
                  <div class="w-full aspect-square rounded-lg flex flex-col items-center justify-center text-[9px] text-ink-faint font-bold gap-0.5" style="background: var(--parchment);">
                    <span>{m.filePath.split('.').pop()?.toUpperCase()}</span>
                    <span class="text-[7px] font-normal truncate w-full text-center px-1">{m.title || m.filePath.split('/').pop()}</span>
                  </div>
                {/if}
              {/each}
            </div>
          {/if}

          <button onclick={() => setRoot(selected!)} class="w-full mt-4 px-3 py-2 text-sm btn-accent">
            Set as Root Person
          </button>
        </div>
      </div>
    {/if}
  </div>

  <!-- Family Group Sheet (print-only) -->
  {#if printMode === 'family-group' && familyGroupData}
    <div class="fgs-print-sheet">
      <h1 class="fgs-title">Family Group Sheet</h1>
      <p class="fgs-subtitle">Prepared {new Date().toLocaleDateString('en-US', { year: 'numeric', month: 'long', day: 'numeric' })}</p>

      <!-- Husband -->
      <div class="fgs-section">
        <h2 class="fgs-section-title fgs-male">Husband</h2>
        {#if familyGroupData.husband}
          {@const h = familyGroupData.husband}
          <table class="fgs-table"><tbody>
            <tr><td class="fgs-label">{t('common.name')}</td><td class="fgs-value">{h.givenName} {h.surname}{h.suffix ? ` ${h.suffix}` : ''}</td></tr>
            {#if h.birthDate}<tr><td class="fgs-label">Birth</td><td class="fgs-value">{h.birthDate}{h.birthPlace ? ` -- ${h.birthPlace}` : ''}</td></tr>{/if}
            {#if h.deathDate}<tr><td class="fgs-label">Death</td><td class="fgs-value">{h.deathDate}{h.deathPlace ? ` -- ${h.deathPlace}` : ''}</td></tr>{/if}
            {#each familyGroupData.husbandEvents.filter(e => !['BIRT','DEAT'].includes(e.eventType)) as evt}
              <tr><td class="fgs-label">{formatEventType(evt.eventType)}</td><td class="fgs-value">{evt.dateValue ?? ''}{evt.place ? ` -- ${evt.place}` : ''}{evt.description ? ` (${evt.description})` : ''}</td></tr>
            {/each}
          </tbody></table>
        {:else}
          <p class="fgs-empty">{t('common.unknown')}</p>
        {/if}
      </div>

      <!-- Wife -->
      <div class="fgs-section">
        <h2 class="fgs-section-title fgs-female">Wife</h2>
        {#if familyGroupData.wife}
          {@const w = familyGroupData.wife}
          <table class="fgs-table"><tbody>
            <tr><td class="fgs-label">{t('common.name')}</td><td class="fgs-value">{w.givenName} {w.surname}{w.suffix ? ` ${w.suffix}` : ''}</td></tr>
            {#if w.birthDate}<tr><td class="fgs-label">Birth</td><td class="fgs-value">{w.birthDate}{w.birthPlace ? ` -- ${w.birthPlace}` : ''}</td></tr>{/if}
            {#if w.deathDate}<tr><td class="fgs-label">Death</td><td class="fgs-value">{w.deathDate}{w.deathPlace ? ` -- ${w.deathPlace}` : ''}</td></tr>{/if}
            {#each familyGroupData.wifeEvents.filter(e => !['BIRT','DEAT'].includes(e.eventType)) as evt}
              <tr><td class="fgs-label">{formatEventType(evt.eventType)}</td><td class="fgs-value">{evt.dateValue ?? ''}{evt.place ? ` -- ${evt.place}` : ''}{evt.description ? ` (${evt.description})` : ''}</td></tr>
            {/each}
          </tbody></table>
        {:else}
          <p class="fgs-empty">{t('common.unknown')}</p>
        {/if}
      </div>

      <!-- Marriage -->
      {#if familyGroupData.marriageDate || familyGroupData.marriagePlace}
        <div class="fgs-section">
          <h2 class="fgs-section-title">{t('families.marriage')}</h2>
          <table class="fgs-table"><tbody>
            {#if familyGroupData.marriageDate}<tr><td class="fgs-label">{t('common.date')}</td><td class="fgs-value">{familyGroupData.marriageDate}</td></tr>{/if}
            {#if familyGroupData.marriagePlace}<tr><td class="fgs-label">Place</td><td class="fgs-value">{familyGroupData.marriagePlace}</td></tr>{/if}
          </tbody></table>
        </div>
      {/if}

      <!-- Children -->
      <div class="fgs-section">
        <h2 class="fgs-section-title">{t('families.children')}</h2>
        {#if familyGroupData.children.length > 0}
          <table class="fgs-table fgs-children-table">
            <thead>
              <tr>
                <th>#</th>
                <th>{t('common.name')}</th>
                <th>{t('people.sex')}</th>
                <th>Birth</th>
                <th>{t('people.birthPlace')}</th>
                <th>Death</th>
                <th>{t('people.deathPlace')}</th>
              </tr>
            </thead>
            <tbody>
              {#each familyGroupData.children as child, i}
                {@const c = child.person}
                <tr>
                  <td>{i + 1}</td>
                  <td class="fgs-child-name">{c.givenName} {c.surname}{c.suffix ? ` ${c.suffix}` : ''}</td>
                  <td>{c.sex === 'M' ? 'M' : c.sex === 'F' ? 'F' : '?'}</td>
                  <td>{c.birthDate ?? ''}</td>
                  <td>{c.birthPlace ?? ''}</td>
                  <td>{c.deathDate ?? ''}</td>
                  <td>{c.deathPlace ?? ''}</td>
                </tr>
                {#each child.events.filter(e => !['BIRT','DEAT'].includes(e.eventType)) as evt}
                  <tr class="fgs-child-event">
                    <td></td>
                    <td colspan="6">{formatEventType(evt.eventType)}: {evt.dateValue ?? ''}{evt.place ? ` -- ${evt.place}` : ''}{evt.description ? ` (${evt.description})` : ''}</td>
                  </tr>
                {/each}
              {/each}
            </tbody>
          </table>
        {:else}
          <p class="fgs-empty">{t('families.noChildren')}</p>
        {/if}
      </div>

      <!-- Sources -->
      <div class="fgs-section">
        <h2 class="fgs-section-title">Sources Cited</h2>
        <p class="fgs-source-note">
          Husband sources: {familyGroupData.husband?.sourceCount ?? 0} |
          Wife sources: {familyGroupData.wife?.sourceCount ?? 0} |
          Family: {familyGroupData.familyXref || 'N/A'}
        </p>
      </div>

      <div class="fgs-footer">
        Generated by GedFix Archival Atlas
      </div>
    </div>
  {/if}
</div>
