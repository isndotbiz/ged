<script lang="ts">
  import { getPersons, getPerson, getParents, getMediaWithPaths, getPrimaryPhoto } from '$lib/db';
  import type { Person, GedcomMedia } from '$lib/types';
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
    persons = await getPersons('', 2000);
    if (persons.length > 0 && !rootPerson) {
      // Find Jonathan directly by xref
      let me = await getPerson('I2');
      if (!me) me = persons.find(p => p.givenName === 'Jonathan' && p.surname === 'Mallinger') ?? persons[0];
      if (me) await buildTree(me);
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
    const arr: AncestorRow[] = new Array(maxSlots + 1).fill(null).map(() => ({ person: null, photoPath: '', thumbPath: '' }));

    async function fill(xref: string, idx: number) {
      if (idx > maxSlots) return;
      const p = await getPerson(xref);
      if (!p) return;
      const photo = await getPrimaryPhoto(p.xref);
      const photoPath = photo?.filePath ?? '';
      const thumbPath = photoPath ? await getThumb(photoPath) : '';
      arr[idx] = { person: p, photoPath, thumbPath };
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

  function borderColor(p: Person): string {
    return isFemale(p) ? '#D94A8C' : '#4A90D9';
  }

  function bgColor(p: Person): string {
    return isFemale(p) ? '#FDF2F8' : '#EFF6FF';
  }

  function avatarBg(p: Person): string {
    return isFemale(p) ? '#D94A8C' : '#4A90D9';
  }

  function initials(p: Person): string {
    return ((p.givenName?.[0] ?? '') + (p.surname?.[0] ?? '')).toUpperCase();
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
      gens.push(row);
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
</style>

<div class="flex flex-col h-full">
  <!-- Toolbar -->
  <div class="flex items-center gap-3 px-5 py-3 shrink-0 arch-toolbar">
    <h1 class="text-lg font-bold text-ink">Family Tree</h1>

    <div class="arch-tabs ml-3">
      <button onclick={() => { layout = 'side'; rootPerson && buildTree(rootPerson); }} class="arch-tab {layout === 'side' ? 'active' : ''}">
        Sideways
      </button>
      <button onclick={() => { layout = 'down'; rootPerson && buildTree(rootPerson); }} class="arch-tab {layout === 'down' ? 'active' : ''}">
        Top-Down
      </button>
    </div>

    <div class="flex items-center gap-1">
      <button onclick={() => { maxGen = Math.max(2, maxGen - 1); rootPerson && buildTree(rootPerson); }} class="w-6 h-6 flex items-center justify-center rounded text-ink-light text-sm" style="background: var(--parchment);">-</button>
      <span class="text-xs text-ink-muted w-10 text-center">{maxGen} gen</span>
      <button onclick={() => { maxGen = Math.min(7, maxGen + 1); rootPerson && buildTree(rootPerson); }} class="w-6 h-6 flex items-center justify-center rounded text-ink-light text-sm" style="background: var(--parchment);">+</button>
    </div>

    <!-- Legend -->
    <div class="flex items-center gap-3 ml-3 text-[10px]">
      <span class="flex items-center gap-1"><span class="w-3 h-3 rounded-full bg-[#4A90D9]"></span> Male</span>
      <span class="flex items-center gap-1"><span class="w-3 h-3 rounded-full bg-[#D94A8C]"></span> Female</span>
    </div>

    <div class="relative ml-auto">
      <input bind:value={search} placeholder="Find person..." class="w-52 px-3 py-1.5 text-sm rounded-lg outline-none arch-input" />
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
    <div class="flex-1 overflow-auto p-6" style="background: var(--paper);">
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
            {#if selected.birthDate}<div class="flex justify-between"><span class="text-ink-faint">Born</span><span class="font-medium">{selected.birthDate}</span></div>{/if}
            {#if selected.birthPlace}<div class="flex justify-between gap-2"><span class="text-ink-faint shrink-0">Place</span><span class="text-right text-xs">{selected.birthPlace}</span></div>{/if}
            {#if selected.deathDate}<div class="flex justify-between"><span class="text-ink-faint">Died</span><span class="font-medium">{selected.deathDate}</span></div>{/if}
            {#if selected.deathPlace}<div class="flex justify-between gap-2"><span class="text-ink-faint shrink-0">Place</span><span class="text-right text-xs">{selected.deathPlace}</span></div>{/if}
            <div class="flex justify-between"><span class="text-ink-faint">Sources</span><span>{selected.sourceCount}</span></div>
            <div class="flex justify-between"><span class="text-ink-faint">Media</span><span>{selected.mediaCount}</span></div>
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
</div>
