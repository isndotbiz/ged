<script lang="ts">
  import { appStats } from '$lib/stores';
  import { getPerson, getParents, getPrimaryPhoto, getMediaWithPaths, getStats, getDb } from '$lib/db';
  import type { Person, GedcomMedia } from '$lib/types';
  import { getThumbUrl, cropFace, saveFaceCrop, clearCache, getFullImageBase64 } from '$lib/photo';

  interface TreeNode {
    person: Person | null;
    thumbUrl: string;
    gen: number;
    slot: number;
  }

  let nodes = $state<TreeNode[]>([]);
  let maxGen = $state(6);
  let rootPerson = $state<Person | null>(null);
  let treeReady = $state(false);
  let treeLoading = $state(false);
  let history = $state<string[]>([]);

  // Face picker state
  let showPicker = $state(false);
  let pickerPerson = $state<Person | null>(null);
  let pickerPhotos = $state<{ media: GedcomMedia; url: string }[]>([]);

  // Crop state
  let showCrop = $state(false);
  let cropMedia = $state<GedcomMedia | null>(null);
  let cropSrc = $state('');
  let cropX = $state(50);
  let cropY = $state(30);
  let cropZoom = $state(2.5);
  let cropPreviewUrl = $state('');
  let isSaving = $state(false);

  // ===== Tree building (batch BFS for speed) =====
  async function loadTree(xref?: string) {
    if (treeLoading) return;
    treeLoading = true;
    treeReady = false;

    // Find root: use provided xref, or find a good starting person
    let p: Person | null = null;
    if (xref) p = await getPerson(xref);
    if (!p) {
      // Try Jonathan Mallinger first (tree owner)
      p = await getPerson('I2');
      if (!p) {
        const db = await getDb();
        const candidates = await db.select<{xref: string}[]>(
          `SELECT xref FROM person WHERE givenName = 'Jonathan' AND surname = 'Mallinger' LIMIT 1`
        );
        if (candidates.length > 0) p = await getPerson(candidates[0].xref);
      }
      if (!p) {
        const db = await getDb();
        const first = await db.select<{xref: string}[]>(`SELECT xref FROM person LIMIT 1`);
        if (first.length > 0) p = await getPerson(first[0].xref);
      }
    }
    if (!p) { treeLoading = false; return; }
    rootPerson = p;

    const max = Math.pow(2, maxGen) - 1;
    const arr: TreeNode[] = Array.from({ length: max + 1 }, () => ({ person: null, thumbUrl: '', gen: 0, slot: 0 }));

    // BFS level-by-level with parallel fetches per level
    type QueueItem = { xref: string; idx: number; gen: number; slot: number };
    let queue: QueueItem[] = [{ xref: p.xref, idx: 1, gen: 0, slot: 0 }];

    while (queue.length > 0) {
      const [persons, photos, parentsList] = await Promise.all([
        Promise.all(queue.map(q => getPerson(q.xref))),
        Promise.all(queue.map(q => getPrimaryPhoto(q.xref))),
        Promise.all(queue.map(q => getParents(q.xref))),
      ]);

      const thumbs = await Promise.all(photos.map(async (photo) => {
        if (!photo?.filePath) return '';
        try {
          if (photo.title?.startsWith('crop:')) {
            const parts = photo.title.replace('crop:', '').split(',');
            return await cropFace(photo.filePath, parseFloat(parts[0]??'50'), parseFloat(parts[1]??'30'), parseFloat(parts[2]??'2.5'), 96);
          }
          return await getThumbUrl(photo.filePath);
        } catch { return ''; }
      }));

      const nextQueue: QueueItem[] = [];
      for (let i = 0; i < queue.length; i++) {
        const q = queue[i];
        const person = persons[i];
        if (!person) continue;
        arr[q.idx] = { person, thumbUrl: thumbs[i], gen: q.gen, slot: q.slot };
        if (q.gen + 1 < maxGen) {
          const parents = parentsList[i];
          if (parents.father) nextQueue.push({ xref: parents.father.xref, idx: q.idx * 2, gen: q.gen + 1, slot: q.slot * 2 });
          if (parents.mother) nextQueue.push({ xref: parents.mother.xref, idx: q.idx * 2 + 1, gen: q.gen + 1, slot: q.slot * 2 + 1 });
        }
      }
      queue = nextQueue;
    }

    nodes = arr;
    treeReady = true;
    treeLoading = false;
    appStats.set(await getStats());
  }

  function nav(p: Person) {
    if (rootPerson) history = [...history, rootPerson.xref];
    loadTree(p.xref);
  }
  function back() {
    if (history.length) { const x = history.pop()!; history = [...history]; loadTree(x); }
  }

  const svgCache = new Map<string, string>();

  // ===== Placeholder images =====
  function crestSvg(name: string, sex: string): string {
    const h = [...name].reduce((a, c) => ((a << 5) - a + c.charCodeAt(0)) | 0, 0);
    const hue = Math.abs(h) % 360;
    const c1 = `hsl(${hue},45%,35%)`, c2 = `hsl(${hue},55%,55%)`;
    const bg = sex === 'F' ? '#FDF2F8' : '#EFF6FF';
    const l = (name[0] ?? '?').toUpperCase();
    return `data:image/svg+xml,${encodeURIComponent(`<svg xmlns="http://www.w3.org/2000/svg" width="96" height="96" viewBox="0 0 96 96"><rect width="96" height="96" rx="48" fill="${bg}"/><path d="M48 12C28 12 20 24 20 36C20 60 48 82 48 82S76 60 76 36C76 24 68 12 48 12Z" fill="${c1}" stroke="${c2}" stroke-width="2"/><text x="48" y="48" text-anchor="middle" dominant-baseline="central" font-family="serif" font-size="24" font-weight="bold" fill="white">${l}</text></svg>`)}`;
  }

  function personSvg(p: Person): string {
    const f = p.sex === 'F';
    const h = [...(p.surname || 'X')].reduce((a, c) => ((a << 5) - a + c.charCodeAt(0)) | 0, 0);
    const hue = Math.abs(h) % 360;
    const cl = `hsl(${hue},50%,45%)`, bg = `hsl(${(hue+180)%360},30%,85%)`;
    if (f) return `data:image/svg+xml,${encodeURIComponent(`<svg xmlns="http://www.w3.org/2000/svg" width="96" height="96" viewBox="0 0 96 96"><rect width="96" height="96" rx="48" fill="${bg}"/><ellipse cx="48" cy="78" rx="22" ry="16" fill="${cl}"/><circle cx="48" cy="38" r="15" fill="#DEB887"/><ellipse cx="48" cy="28" rx="17" ry="13" fill="#4A3728"/><ellipse cx="48" cy="36" rx="12" ry="10" fill="#DEB887"/><circle cx="43" cy="37" r="1.5" fill="#333"/><circle cx="53" cy="37" r="1.5" fill="#333"/><ellipse cx="48" cy="42" rx="3" ry="1.5" fill="#C4756E"/></svg>`)}`;
    return `data:image/svg+xml,${encodeURIComponent(`<svg xmlns="http://www.w3.org/2000/svg" width="96" height="96" viewBox="0 0 96 96"><rect width="96" height="96" rx="48" fill="${bg}"/><rect x="32" y="60" width="32" height="26" rx="4" fill="${cl}"/><circle cx="48" cy="38" r="14" fill="#DEB887"/><rect x="34" y="25" width="28" height="11" rx="4" fill="#2C1810"/><ellipse cx="48" cy="36" rx="11" ry="9" fill="#DEB887"/><circle cx="43" cy="37" r="1.5" fill="#333"/><circle cx="53" cy="37" r="1.5" fill="#333"/><line x1="44" y1="42" x2="52" y2="42" stroke="#C4756E" stroke-width="2" stroke-linecap="round"/></svg>`)}`;
  }

  function getImg(n: TreeNode): string {
    if (n.thumbUrl) return n.thumbUrl;
    if (!n.person) return crestSvg('?', 'U');
    if (n.person.surname) return personSvg(n.person);
    return crestSvg(n.person.givenName || '?', n.person.sex);
  }

  // ===== Face picker =====
  async function openPicker(p: Person) {
    pickerPerson = p;
    showPicker = true;
    showCrop = false;
    pickerPhotos = [];
    const media = await getMediaWithPaths(p.xref);
    for (const m of media) {
      if (/\.(jpe?g|png|gif|webp|bmp)$/i.test(m.filePath)) {
        const url = await getThumbUrl(m.filePath, 200);
        if (url) pickerPhotos = [...pickerPhotos, { media: m, url }];
      }
    }
  }

  async function startCrop(media: GedcomMedia) {
    cropMedia = media;
    cropSrc = await getFullImageBase64(media.filePath);
    cropX = 50; cropY = 30; cropZoom = 2.5;
    cropPreviewUrl = '';
    showCrop = true;
    updatePreview();
  }

  async function updatePreview() {
    if (!cropMedia) return;
    try {
      cropPreviewUrl = await cropFace(cropMedia.filePath, cropX, cropY, cropZoom, 200);
    } catch { cropPreviewUrl = ''; }
  }

  function onClickPhoto(e: MouseEvent) {
    const rect = (e.currentTarget as HTMLElement).getBoundingClientRect();
    cropX = Math.round((e.clientX - rect.left) / rect.width * 100);
    cropY = Math.round((e.clientY - rect.top) / rect.height * 100);
    updatePreview();
  }

  function onZoomChange() {
    updatePreview();
  }

  async function saveCrop() {
    if (!pickerPerson || !cropMedia) return;
    isSaving = true;

    try {
      const db = await getDb();

      // 1. Clear old primary photo cache (junction table)
      const oldPrimary = await db.select<{filePath:string}[]>(
        `SELECT m.filePath FROM media m
         JOIN media_person_link mpl ON m.id = mpl.mediaId
         WHERE mpl.personXref = $1 AND mpl.isPrimary = 1 AND m.filePath != ''`,
        [pickerPerson.xref]
      );
      for (const old of oldPrimary) {
        await clearCache(old.filePath);
      }

      // 2. Generate cropped thumbnail via Rust
      await saveFaceCrop(cropMedia.filePath, cropX, cropY, cropZoom);
      await clearCache(cropMedia.filePath);

      // 3. Update junction table: clear old primary, set new
      await db.execute(
        `UPDATE media_person_link SET isPrimary = 0 WHERE personXref = $1`,
        [pickerPerson.xref]
      );

      // Ensure link exists, then set primary
      await db.execute(
        `INSERT OR IGNORE INTO media_person_link (mediaId, personXref, isPrimary, role, addedBy, createdAt)
         VALUES ($1, $2, 1, 'primary_portrait', 'manual', datetime('now'))`,
        [cropMedia.id, pickerPerson.xref]
      );
      await db.execute(
        `UPDATE media_person_link SET isPrimary = 1, role = 'primary_portrait'
         WHERE mediaId = $1 AND personXref = $2`,
        [cropMedia.id, pickerPerson.xref]
      );

      // 4. Store crop coordinates PER PERSON in junction table caption
      //    (not in media.title — that's shared across all people using this image)
      const cropData = `crop:${Math.round(cropX)},${Math.round(cropY)},${cropZoom.toFixed(1)}`;
      await db.execute(
        `UPDATE media_person_link SET caption = $1 WHERE mediaId = $2 AND personXref = $3`,
        [cropData, cropMedia.id, pickerPerson.xref]
      );

      console.log('Crop saved for', pickerPerson.givenName, pickerPerson.surname);

    } catch (e) {
      console.error('Save crop failed:', e);
    }

    const rootXref = rootPerson?.xref;
    showCrop = false; showPicker = false; pickerPerson = null; cropMedia = null; isSaving = false;
    nodes = []; treeReady = false;
    await loadTree(rootXref);
  }

  function getGens(): TreeNode[][] {
    const g: TreeNode[][] = [];
    for (let i = 0; i < maxGen; i++) {
      const s = Math.pow(2, i), e = Math.pow(2, i + 1), r: TreeNode[] = [];
      for (let j = s; j < e; j++) r.push(nodes[j] ?? { person: null, thumbUrl: '', gen: i, slot: j - s });
      g.push(r);
    }
    return g;
  }

  $effect(() => { if (!treeReady && !treeLoading) loadTree(); });
</script>

<style>
  /* Archival ancestor cards */
  .m-card {
    background: var(--card-male-bg);
    border: 1.5px solid var(--card-male-border);
    box-shadow: var(--shadow-sm);
    border-radius: 10px;
  }
  .m-card:hover { border-color: var(--card-male-border-hover); }
  .f-card {
    background: var(--card-female-bg);
    border: 1.5px solid var(--card-female-border);
    box-shadow: var(--shadow-sm);
    border-radius: 10px;
  }
  .f-card:hover { border-color: var(--card-female-border-hover); }
  .u-card {
    background: var(--card-unknown-bg);
    border: 1.5px solid var(--card-unknown-border);
    box-shadow: var(--shadow-sm);
    border-radius: 10px;
  }
  .m-card:hover, .f-card:hover, .u-card:hover {
    box-shadow: var(--shadow-md), inset 0 1px 2px rgba(26, 22, 18, 0.04);
  }
</style>

<div class="flex flex-col h-full overflow-hidden">
  <!-- Research desk toolbar -->
  <div
    class="flex items-center gap-3 px-5 py-2.5 shrink-0 animate-fade-in"
    style="background: var(--vellum); border-bottom: 1px solid var(--border-rule);"
  >
    <button
      onclick={() => loadTree('I2')}
      class="px-2 py-1 rounded-md transition-all"
      style="background: var(--parchment); color: var(--ink-light); border: 1px solid var(--border-subtle);"
      title="Go to home person"
    >
      <svg class="w-4 h-4" fill="none" stroke="currentColor" stroke-width="1.5" viewBox="0 0 24 24">
        <path stroke-linecap="round" stroke-linejoin="round" d="M3 12l9-9 9 9M5 12v7a2 2 0 002 2h10a2 2 0 002-2v-7" />
      </svg>
    </button>
    {#if history.length > 0}
      <button
        onclick={back}
        class="px-2.5 py-1 text-xs rounded-md transition-all"
        style="background: var(--parchment); color: var(--ink-light); font-family: var(--font-sans); border: 1px solid var(--border-subtle);"
      >&larr;</button>
    {/if}
    <h1
      class="text-lg"
      style="font-family: var(--font-serif); font-weight: 700; color: var(--ink); letter-spacing: -0.01em;"
    >Family Tree</h1>
    {#if rootPerson}
      <span
        class="text-xs"
        style="color: var(--ink-muted); font-family: var(--font-sans); font-style: italic;"
      >{rootPerson.givenName} {rootPerson.surname}</span>
    {/if}
    <div class="h-5 mx-2" style="border-left: 1px solid var(--border-rule);"></div>
    <div class="flex items-center gap-1">
      <button
        onclick={() => { maxGen = Math.max(3, maxGen-1); rootPerson && loadTree(rootPerson.xref); }}
        class="w-6 h-6 rounded flex items-center justify-center text-xs transition-all"
        style="background: var(--parchment); color: var(--ink-light); border: 1px solid var(--border-subtle); font-family: var(--font-mono);"
      >-</button>
      <span
        class="text-xs w-10 text-center"
        style="color: var(--ink-muted); font-family: var(--font-mono);"
      >{maxGen}gen</span>
      <button
        onclick={() => { maxGen = Math.min(8, maxGen+1); rootPerson && loadTree(rootPerson.xref); }}
        class="w-6 h-6 rounded flex items-center justify-center text-xs transition-all"
        style="background: var(--parchment); color: var(--ink-light); border: 1px solid var(--border-subtle); font-family: var(--font-mono);"
      >+</button>
    </div>
    <span
      class="text-[10px] ml-auto"
      style="color: var(--ink-faint); font-family: var(--font-sans);"
    >click photo = crop face | click name = navigate</span>
  </div>

  <!-- Tree canvas -->
  <div class="flex-1 overflow-auto p-4" style="background: var(--paper-warm, var(--paper));">
    {#if treeReady}
      <div class="flex items-stretch animate-reveal animate-dossier" style="min-height:{Math.pow(2,maxGen-1)*68}px;min-width:{maxGen*200}px;">
        {#each getGens() as gen, gi}
          <div class="flex flex-col justify-around shrink-0" style="min-width:{gi<2?215:gi<4?185:160}px;">
            {#each gen as node}
              {#if node.person}
                {@const p = node.person}
                <div class="flex items-center gap-2 px-2 py-1 mx-0.5 my-0.5 rounded-lg transition-all {p.sex==='F'?'f-card':p.sex==='M'?'m-card':'u-card'}" style="transition-duration: var(--duration-fast);">
                  <button onclick={() => openPicker(p)} class="shrink-0 group" title="Pick face photo">
                    <img
                      src={getImg(node)}
                      alt=""
                      class="w-10 h-10 rounded-full object-cover border-2 group-hover:ring-2 group-hover:ring-amber-500/60"
                      style="border-color: {p.sex==='F' ? 'var(--card-female-border-hover)' : p.sex==='M' ? 'var(--card-male-border-hover)' : 'var(--card-unknown-border)'};"
                      onerror={(e) => { (e.target as HTMLImageElement).src = personSvg(p); }}
                    />
                  </button>
                  <button onclick={() => nav(p)} class="text-left min-w-0 flex-1 hover:opacity-80" title="Navigate to {p.givenName}">
                    <div class="text-[11px] font-bold truncate leading-tight" style="color: var(--ink); font-family: var(--font-sans);">{p.givenName}</div>
                    <div class="text-[10px] truncate leading-tight" style="color: var(--ink-light); font-family: var(--font-serif); font-weight: 600;">{p.surname}</div>
                    <div class="text-[9px] truncate" style="color: var(--ink-muted); font-family: var(--font-mono);">{p.birthDate?`b.${p.birthDate}`:''}{p.deathDate?` d.${p.deathDate}`:''}</div>
                  </button>
                </div>
              {:else}
                <div class="mx-0.5 my-0.5 h-[48px] flex items-center justify-center">
                  <div class="w-8 h-8 rounded-full" style="border: 1px dashed var(--ink-faint); opacity: 0.3;"></div>
                </div>
              {/if}
            {/each}
          </div>
          {#if gi < maxGen-1}
            <div class="w-3 shrink-0 flex flex-col justify-around">
              {#each gen as _}<div class="flex-1 flex items-center"><div class="w-full" style="border-top: 1px solid var(--ink-faint); opacity: 0.4;"></div></div>{/each}
            </div>
          {/if}
        {/each}
      </div>
    {:else}
      <div class="flex items-center justify-center h-full" style="color: var(--ink-faint);">
        <span style="font-family: var(--font-serif); font-style: italic;">Loading tree...</span>
      </div>
    {/if}
  </div>
</div>

<!-- FACE PICKER OVERLAY -->
{#if showPicker && pickerPerson && !showCrop}
  <div class="fixed inset-0 z-50 flex items-center justify-center p-6 animate-fade-in" style="background: rgba(26,22,18,0.6);" role="dialog" tabindex="-1">
    <div class="max-w-3xl w-full max-h-[80vh] overflow-hidden flex flex-col rounded-xl animate-dossier" style="background: var(--vellum); box-shadow: var(--shadow-overlay);">
      <div class="flex items-center justify-between px-6 py-3 shrink-0" style="border-bottom: 1px solid var(--border-rule);">
        <h2 style="font-family: var(--font-serif); font-weight: 700; font-size: 1.1rem; color: var(--ink);">Pick Photo — {pickerPerson.givenName} {pickerPerson.surname}</h2>
        <button
          onclick={() => showPicker = false}
          class="w-8 h-8 rounded-full flex items-center justify-center text-lg transition-all"
          style="background: var(--parchment); color: var(--ink-muted);"
        >&times;</button>
      </div>
      <div class="flex-1 overflow-auto p-6">
        {#if pickerPhotos.length > 0}
          <p class="text-xs mb-4" style="color: var(--ink-muted); font-family: var(--font-sans);">Click a photo to zoom in and crop the face</p>
          <div class="grid grid-cols-4 gap-4">
            {#each pickerPhotos as ph}
              <button
                onclick={() => startCrop(ph.media)}
                class="group relative aspect-square rounded-lg overflow-hidden border-2 border-transparent hover:border-amber-600 transition-all"
                style="box-shadow: var(--shadow-sm);"
              >
                <img src={ph.url} alt="" class="w-full h-full object-cover" onerror={(e) => { (e.target as HTMLImageElement).style.display = 'none'; }} />
                <div class="absolute inset-0 group-hover:bg-black/20 flex items-center justify-center transition-all">
                  <span class="text-white font-medium text-sm opacity-0 group-hover:opacity-100 px-3 py-1 rounded-full" style="background: rgba(26,22,18,0.6); font-family: var(--font-sans);">Crop Face</span>
                </div>
                {#if ph.media.format === 'PRIMARY'}
                  <div class="absolute top-2 right-2 w-5 h-5 rounded-full flex items-center justify-center text-white text-[10px] font-bold" style="background: var(--color-validated);">&#10003;</div>
                {/if}
              </button>
            {/each}
          </div>
        {:else}
          <div class="text-center py-16" style="color: var(--ink-faint);">
            <div class="text-4xl mb-3">&#128247;</div>
            <p style="font-family: var(--font-serif); font-style: italic;">No photos linked to {pickerPerson.givenName}</p>
          </div>
        {/if}
      </div>
    </div>
  </div>
{/if}

<!-- CROP TOOL OVERLAY -->
{#if showCrop && cropMedia && pickerPerson}
  <div class="fixed inset-0 z-50 flex items-center justify-center p-6 animate-fade-in" style="background: rgba(26,22,18,0.7);" role="dialog" tabindex="-1">
    <div class="max-w-4xl w-full max-h-[90vh] overflow-hidden flex flex-col rounded-xl animate-dossier" style="background: var(--vellum); box-shadow: var(--shadow-overlay);">
      <div class="flex items-center justify-between px-6 py-3 shrink-0" style="border-bottom: 1px solid var(--border-rule);">
        <div>
          <h2 style="font-family: var(--font-serif); font-weight: 700; font-size: 1.1rem; color: var(--ink);">Crop Face — {pickerPerson.givenName} {pickerPerson.surname}</h2>
          <p class="text-xs" style="color: var(--ink-muted); font-family: var(--font-sans);">Click on the face you want, adjust zoom, then save</p>
        </div>
        <div class="flex gap-2">
          <button
            onclick={() => showCrop = false}
            class="px-3 py-1.5 text-xs rounded-md"
            style="background: var(--parchment); color: var(--ink-light); font-family: var(--font-sans); border: 1px solid var(--border-subtle);"
          >&larr; Back</button>
          <button
            onclick={() => { showCrop = false; showPicker = false; }}
            class="w-8 h-8 rounded-full flex items-center justify-center text-lg transition-all"
            style="background: var(--parchment); color: var(--ink-muted);"
          >&times;</button>
        </div>
      </div>

      <div class="flex-1 overflow-auto p-6">
        <div class="flex gap-6">
          <!-- Left: full photo with click target -->
          <div class="flex-1">
            {#if cropSrc}
              <div
                class="relative rounded-lg overflow-hidden cursor-crosshair"
                style="border: 1px solid var(--border-rule);"
                onclick={onClickPhoto}
                role="button"
                tabindex="0"
                onkeydown={(e) => { if (e.key === 'Enter' || e.key === ' ') onClickPhoto(e as unknown as MouseEvent); }}
              >
                <img src={cropSrc} alt="" class="w-full" />
                <div
                  class="absolute w-16 h-16 rounded-full pointer-events-none"
                  style="left:{cropX}%;top:{cropY}%;transform:translate(-50%,-50%);border: 3px solid white;box-shadow:0 0 0 9999px rgba(26,22,18,0.4);"
                ></div>
              </div>
            {:else}
              <div class="h-64 rounded-lg flex items-center justify-center" style="background: var(--parchment); color: var(--ink-faint);">Loading image...</div>
            {/if}

            <!-- Zoom slider -->
            <div class="flex items-center gap-3 mt-4">
              <span class="text-xs w-10" style="color: var(--ink-muted); font-family: var(--font-sans);">Zoom</span>
              <input
                type="range" min="1.5" max="5" step="0.1"
                bind:value={cropZoom}
                oninput={onZoomChange}
                class="flex-1"
              />
              <span class="text-xs w-10 text-right" style="color: var(--ink-light); font-family: var(--font-mono);">{cropZoom.toFixed(1)}x</span>
            </div>
          </div>

          <!-- Right: preview + save -->
          <div class="w-48 flex flex-col items-center gap-4">
            <div class="text-xs font-medium" style="color: var(--ink-muted); font-family: var(--font-sans); text-transform: uppercase; letter-spacing: 0.08em;">Preview</div>

            {#if cropPreviewUrl}
              <img
                src={cropPreviewUrl} alt=""
                class="w-32 h-32 rounded-full object-cover shadow-xl"
                style="border: 3px solid {pickerPerson.sex==='F' ? 'var(--card-female-border-hover)' : 'var(--card-male-border-hover)'};"
              />
            {:else}
              <div class="w-32 h-32 rounded-full flex items-center justify-center text-xs" style="background: var(--parchment); color: var(--ink-faint); font-family: var(--font-serif); font-style: italic;">Click a face</div>
            {/if}

            <div class="text-xs" style="color: var(--ink-muted); font-family: var(--font-sans); text-transform: uppercase; letter-spacing: 0.08em;">Tree size</div>
            {#if cropPreviewUrl}
              <img
                src={cropPreviewUrl} alt=""
                class="w-11 h-11 rounded-full object-cover"
                style="border: 2px solid {pickerPerson.sex==='F' ? 'var(--card-female-border-hover)' : 'var(--card-male-border-hover)'};"
              />
            {:else}
              <div class="w-11 h-11 rounded-full" style="background: var(--parchment);"></div>
            {/if}

            <button
              onclick={saveCrop}
              disabled={isSaving || !cropPreviewUrl}
              class="btn-accent w-full px-4 py-2.5 text-sm font-medium rounded-lg disabled:opacity-50 mt-4 transition-all"
              style="background: var(--accent); color: white; font-family: var(--font-sans);"
            >
              {isSaving ? 'Saving...' : 'Save as Icon'}
            </button>

            <p class="text-[10px] text-center" style="color: var(--ink-faint); font-family: var(--font-sans);">Original photo is never modified. A cropped copy is saved.</p>
          </div>
        </div>
      </div>
    </div>
  </div>
{/if}
