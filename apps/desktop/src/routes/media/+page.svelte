<script lang="ts">
  import { t } from '$lib/i18n';
  import {
    getMediaForManagement,
    updateMediaCategory,
    processDeleteQueue,
    deduplicateMediaByNormalizedPath,
    autoCategorizeMediaAfterDedup,
    moveNonPortraitHeadshotsToOther,
    getPeopleForMedia,
    getAllPersons,
    updateMediaFilePath,
    normalizeMediaPath,
    scanAndImportMediaDirectory,
    type MediaCategory,
  } from '$lib/db';
  import type { GedcomMedia, FaceDetectionBox, Person } from '$lib/types';
  import { isTauri } from '$lib/platform';
  import { detectFaces, tagFaceOnMedia } from '$lib/media-matcher';

  type ManagedMedia = GedcomMedia & { category: MediaCategory; linkCount: number; peopleLabel: string };
  type RenamePreviewItem = { id: number; currentPath: string; proposedPath: string };

  const tabs: { id: MediaCategory; label: string }[] = [
    { id: 'headshots', label: 'Headshots' },
    { id: 'other', label: 'Other' },
    { id: 'uncategorized', label: 'Uncategorized' },
    { id: 'delete-queue', label: 'Delete Queue' },
  ];

  let media = $state<ManagedMedia[]>([]);
  let activeCategory = $state<MediaCategory>('headshots');
  let search = $state('');
  let selectedIds = $state<Set<number>>(new Set());
  let bulkCategory = $state<MediaCategory>('uncategorized');
  let statusMsg = $state('');
  let busy = $state(false);
  let deleteResult = $state<{ mediaRemoved: number; linksRemoved: number } | null>(null);
  let dedupResult = $state<{ duplicateGroups: number; entriesRemoved: number; linksTransferred: number; categorizedUpdated: number } | null>(null);
  let movedFromHeadshots = $state<number | null>(null);

  let renamePreview = $state<RenamePreviewItem[]>([]);
  let renameBusy = $state(false);
  let people = $state<Person[]>([]);
  let detectionBusyByMedia = $state<Record<number, boolean>>({});
  let faceDetections = $state<Record<number, FaceDetectionBox[]>>({});
  let selectedFace = $state<{ mediaId: number; index: number } | null>(null);
  let selectedPersonXref = $state('');
  let linkedOnly = $state(true);
  let imagesOnly = $state(true);
  let scanResult = $state<{ imported: number; matched: number; skipped: number } | null>(null);
  let scanProgress = $state('');

  let _convertFileSrc: ((path: string) => string) | null = null;

  let initialized = $state(false);
  $effect(() => {
    if (initialized) return;
    initialized = true;
    if (isTauri()) {
      import('@tauri-apps/api/core').then((mod) => {
        _convertFileSrc = mod.convertFileSrc;
      });
    }
    load();
    getAllPersons().then((rows) => { people = rows; });
  });

  let visibleMedia = $derived(
    media.filter((m) => {
      if (m.category !== activeCategory) return false;
      if (!search.trim()) return true;
      const q = search.toLowerCase();
      return (
        (m.title || '').toLowerCase().includes(q) ||
        (m.filePath || '').toLowerCase().includes(q) ||
        (m.peopleLabel || '').toLowerCase().includes(q)
      );
    })
  );

  let counts = $derived(
    tabs.reduce((acc, t) => {
      acc[t.id] = media.filter((m) => m.category === t.id).length;
      return acc;
    }, {} as Record<MediaCategory, number>)
  );

  function imageSrc(path: string): string {
    if (_convertFileSrc) return _convertFileSrc(path);
    return path;
  }

  function fileName(path: string): string {
    const parts = path.split('/');
    return parts[parts.length - 1] || path;
  }

  function dirname(path: string): string {
    const i = path.lastIndexOf('/');
    return i >= 0 ? path.slice(0, i + 1) : '';
  }

  function extension(path: string): string {
    const i = path.lastIndexOf('.');
    return i >= 0 ? path.slice(i + 1).toLowerCase() : 'jpg';
  }

  function stem(path: string): string {
    const name = fileName(path);
    const i = name.lastIndexOf('.');
    return i >= 0 ? name.slice(0, i) : name;
  }

  function slug(value: string): string {
    return value
      .toLowerCase()
      .trim()
      .replace(/[^a-z0-9]+/g, '-')
      .replace(/^-+|-+$/g, '') || 'unknown';
  }

  function eventSlug(title: string): string {
    const t = title.toLowerCase();
    if (t.includes('wedding') || t.includes('marriage')) return 'wedding';
    if (t.includes('birth')) return 'birth';
    if (t.includes('death') || t.includes('obit')) return 'death';
    if (t.includes('group') || t.includes('family')) return 'group';
    return 'portrait';
  }

  function isSelected(id: number): boolean {
    return selectedIds.has(id);
  }

  function selectMedia(id: number, evt: MouseEvent): void {
    const toggle = evt.metaKey || evt.ctrlKey;
    if (toggle) {
      const next = new Set(selectedIds);
      if (next.has(id)) next.delete(id);
      else next.add(id);
      selectedIds = next;
      return;
    }
    selectedIds = new Set([id]);
  }

  function clearSelection(): void {
    selectedIds = new Set();
  }

  async function load(): Promise<void> {
    media = await getMediaForManagement({ linkedOnly, imagesOnly });
    selectedIds = new Set();
  }

  async function moveSelection(category: MediaCategory): Promise<void> {
    const ids = Array.from(selectedIds);
    if (ids.length === 0) return;
    busy = true;
    statusMsg = `Moving ${ids.length} media item(s) to ${category}...`;
    try {
      await updateMediaCategory(ids, category);
      await load();
      statusMsg = `Moved ${ids.length} media item(s) to ${category}.`;
    } catch (e) {
      statusMsg = `Move failed: ${e}`;
    }
    busy = false;
  }

  async function onDropTab(category: MediaCategory, evt: DragEvent): Promise<void> {
    evt.preventDefault();
    await moveSelection(category);
  }

  function onDragStart(mediaId: number, evt: DragEvent): void {
    if (!selectedIds.has(mediaId)) {
      selectedIds = new Set([mediaId]);
    }
    evt.dataTransfer?.setData('text/plain', String(mediaId));
    evt.dataTransfer!.effectAllowed = 'move';
  }

  async function queueForDelete(): Promise<void> {
    await moveSelection('delete-queue');
  }

  async function runDeleteQueue(): Promise<void> {
    busy = true;
    statusMsg = 'Processing delete queue...';
    try {
      deleteResult = await processDeleteQueue();
      await load();
      statusMsg = `Removed ${deleteResult.mediaRemoved} media item(s) and ${deleteResult.linksRemoved} link(s).`;
    } catch (e) {
      statusMsg = `Delete queue failed: ${e}`;
    }
    busy = false;
  }

  async function runDedupPipeline(): Promise<void> {
    busy = true;
    statusMsg = 'Deduplicating media by normalized path...';
    try {
      const dedup = await deduplicateMediaByNormalizedPath();
      const categorized = await autoCategorizeMediaAfterDedup();
      dedupResult = { ...dedup, categorizedUpdated: categorized.updated };
      await load();
      statusMsg = 'Deduplication complete.';
    } catch (e) {
      statusMsg = `Dedup failed: ${e}`;
    }
    busy = false;
  }

  async function runHeadshotCleanup(): Promise<void> {
    busy = true;
    statusMsg = 'Moving non-portrait media out of Headshots...';
    try {
      movedFromHeadshots = await moveNonPortraitHeadshotsToOther();
      await load();
      statusMsg = `Moved ${movedFromHeadshots} media item(s) from Headshots to Other.`;
    } catch (e) {
      statusMsg = `Headshot cleanup failed: ${e}`;
    }
    busy = false;
  }

  async function buildRenamePreview(): Promise<void> {
    renameBusy = true;
    statusMsg = 'Building rename preview...';
    renamePreview = [];

    const source = selectedIds.size > 0
      ? media.filter((m) => selectedIds.has(m.id))
      : visibleMedia;

    const allExisting = new Set(media.map((m) => normalizeMediaPath(m.filePath)));
    const sequenceByBase = new Map<string, number>();
    const preview: RenamePreviewItem[] = [];

    for (const item of source) {
      const people = await getPeopleForMedia(item.id);
      const ext = extension(item.filePath);
      const dir = dirname(item.filePath);
      const currentNorm = normalizeMediaPath(item.filePath);
      let basePrefix = '';

      if (people.length === 0) {
        basePrefix = `unlinked-${slug(stem(item.filePath))}`;
      } else if (people.length > 1) {
        const surname = slug(people[0]?.surname || 'family');
        basePrefix = `${surname}-family-group`;
      } else {
        const p = people[0];
        basePrefix = `${slug(p.surname || 'unknown')}-${slug(p.givenName || 'unknown')}-${eventSlug(item.title || '')}`;
      }

      const seqKey = `${dir}|${basePrefix}|${ext}`;
      let seq = sequenceByBase.get(seqKey) ?? 1;
      let candidate = '';
      while (true) {
        const suffix = String(seq).padStart(3, '0');
        candidate = `${dir}${basePrefix}-${suffix}.${ext}`;
        const norm = normalizeMediaPath(candidate);
        if (norm === currentNorm || !allExisting.has(norm)) {
          allExisting.add(norm);
          sequenceByBase.set(seqKey, seq + 1);
          break;
        }
        seq++;
      }

      preview.push({ id: item.id, currentPath: item.filePath, proposedPath: candidate });
    }

    renamePreview = preview;
    renameBusy = false;
    statusMsg = `Prepared ${preview.length} rename proposal(s).`;
  }

  async function applyRename(): Promise<void> {
    if (renamePreview.length === 0) return;
    renameBusy = true;
    statusMsg = 'Applying rename updates...';
    let updated = 0;
    try {
      for (const row of renamePreview) {
        if (normalizeMediaPath(row.currentPath) === normalizeMediaPath(row.proposedPath)) continue;
        await updateMediaFilePath(row.id, row.proposedPath);
        updated++;
      }
      await load();
      renamePreview = [];
      statusMsg = `Updated ${updated} media path(s).`;
    } catch (e) {
      statusMsg = `Rename failed: ${e}`;
    }
    renameBusy = false;
  }

  async function runFaceDetection(item: ManagedMedia): Promise<void> {
    detectionBusyByMedia = { ...detectionBusyByMedia, [item.id]: true };
    try {
      const boxes = await detectFaces(item.filePath);
      faceDetections = { ...faceDetections, [item.id]: boxes };
      selectedFace = null;
      selectedPersonXref = '';
      statusMsg = boxes.length > 0
        ? `Detected ${boxes.length} face(s) in ${fileName(item.filePath)}.`
        : `No faces detected in ${fileName(item.filePath)}.`;
    } catch (e) {
      statusMsg = `Face detection failed: ${e}`;
    }
    detectionBusyByMedia = { ...detectionBusyByMedia, [item.id]: false };
  }

  async function assignSelectedFace(): Promise<void> {
    if (!selectedFace || !selectedPersonXref) return;
    const boxes = faceDetections[selectedFace.mediaId] || [];
    const box = boxes[selectedFace.index];
    if (!box) return;
    try {
      await tagFaceOnMedia(String(selectedFace.mediaId), selectedPersonXref, {
        x: box.x,
        y: box.y,
        w: box.w,
        h: box.h,
      });
      statusMsg = 'Face tag saved to media-person links.';
      selectedFace = null;
      selectedPersonXref = '';
      await load();
    } catch (e) {
      statusMsg = `Face tagging failed: ${e}`;
    }
  }

  async function runScanDirectory(): Promise<void> {
    if (!isTauri()) {
      statusMsg = 'Directory scanning requires the desktop app.';
      return;
    }
    busy = true;
    scanProgress = 'Selecting directory...';
    statusMsg = '';
    try {
      const { open } = await import('@tauri-apps/plugin-dialog');
      const dir = await open({ directory: true, title: 'Select media folder to scan' });
      if (!dir) {
        busy = false;
        scanProgress = '';
        return;
      }
      scanProgress = 'Scanning...';
      scanResult = await scanAndImportMediaDirectory(dir as string, (_pct, msg) => {
        scanProgress = msg;
      });
      // Re-categorize after importing new files
      const cat = await autoCategorizeMediaAfterDedup();
      await load();
      statusMsg = `Scan complete: ${scanResult.imported} imported, ${scanResult.matched} matched to people. ${cat.portraits} portraits, ${cat.documents} documents classified.`;
    } catch (e) {
      statusMsg = `Scan failed: ${e}`;
    }
    busy = false;
    scanProgress = '';
  }
</script>

<div class="arch-page max-w-none h-full overflow-y-auto">
  <div class="space-y-4 pb-12">
    <section class="arch-card p-6 spirit-surface animate-fade-up">
      <h1 class="display-gradient text-4xl">{t('media.management')}</h1>
      <p class="text-muted mt-2">Category management, deduplication, delete queue, and filename normalization.</p>
      <div class="mt-4 flex flex-wrap gap-3">
        <button class="btn-primary" onclick={runScanDirectory} disabled={busy}>Scan Media Directory</button>
        <button class="btn-primary" onclick={runDedupPipeline} disabled={busy}>{t('cleanup.deduplicateMedia')}</button>
        <button class="btn-outline" onclick={runHeadshotCleanup} disabled={busy}>Move Non-Portrait Headshots</button>
      </div>
      {#if scanProgress}
        <p class="mt-2 text-muted font-mono text-sm">{scanProgress}</p>
      {/if}
      {#if scanResult}
        <div class="mt-3 grid grid-cols-3 gap-3">
          <div class="arch-card p-3 text-center"><div class="font-mono text-2xl">{scanResult.imported}</div><div class="text-muted">Imported</div></div>
          <div class="arch-card p-3 text-center"><div class="font-mono text-2xl">{scanResult.matched}</div><div class="text-muted">Matched</div></div>
          <div class="arch-card p-3 text-center"><div class="font-mono text-2xl">{scanResult.skipped}</div><div class="text-muted">Already Existed</div></div>
        </div>
      {/if}
      <div class="mt-3 flex flex-wrap gap-4 items-center">
        <label class="flex items-center gap-2 text-sm cursor-pointer">
          <input type="checkbox" bind:checked={linkedOnly} onchange={load} />
          Show linked to people only
        </label>
        <label class="flex items-center gap-2 text-sm cursor-pointer">
          <input type="checkbox" bind:checked={imagesOnly} onchange={load} />
          Images only
        </label>
      </div>
      {#if dedupResult}
        <div class="mt-4 grid grid-cols-2 md:grid-cols-4 gap-3">
          <div class="arch-card p-3 text-center"><div class="font-mono text-2xl">{dedupResult.duplicateGroups}</div><div class="text-muted">{t('nav.groups')}</div></div>
          <div class="arch-card p-3 text-center"><div class="font-mono text-2xl">{dedupResult.entriesRemoved}</div><div class="text-muted">Removed</div></div>
          <div class="arch-card p-3 text-center"><div class="font-mono text-2xl">{dedupResult.linksTransferred}</div><div class="text-muted">Links Merged</div></div>
          <div class="arch-card p-3 text-center"><div class="font-mono text-2xl">{dedupResult.categorizedUpdated}</div><div class="text-muted">Categorized</div></div>
        </div>
      {/if}
      {#if movedFromHeadshots !== null}
        <p class="mt-3 text-muted">Moved {movedFromHeadshots} media item(s) from Headshots to Other.</p>
      {/if}
    </section>

    <section class="arch-card p-6">
      <div class="flex flex-wrap gap-2 mb-4" role="tablist" aria-label={t('media.categories')}>
        {#each tabs as tab}
          <button
            role="tab"
            aria-selected={activeCategory === tab.id}
            class="tab-chip {activeCategory === tab.id ? 'is-active' : ''}"
            onclick={() => { activeCategory = tab.id; clearSelection(); }}
            ondragover={(e) => e.preventDefault()}
            ondrop={(e) => onDropTab(tab.id, e)}
          >
            {tab.label} ({counts[tab.id] ?? 0})
          </button>
        {/each}
      </div>

      <div class="flex flex-wrap gap-3 items-center mb-4">
        <input class="arch-input flex-1 min-w-[280px]" type="text" bind:value={search} placeholder={t('media.searchPlaceholder')} aria-label={t('media.searchPlaceholder')} />
        <select class="arch-input min-w-[220px]" bind:value={bulkCategory} aria-label={t('common.filter')}>
          {#each tabs as tab}
            <option value={tab.id}>{tab.label}</option>
          {/each}
        </select>
        <button class="btn-outline" onclick={() => moveSelection(bulkCategory)} disabled={busy || selectedIds.size === 0}>Move selected to...</button>
        <button class="btn-outline" onclick={queueForDelete} disabled={busy || selectedIds.size === 0}>Queue for Delete</button>
        {#if activeCategory === 'delete-queue'}
          <button class="btn-danger" onclick={runDeleteQueue} disabled={busy}>Process Delete Queue</button>
        {/if}
      </div>

      <p class="text-muted mb-3">Selected: {selectedIds.size}. Use Cmd/Ctrl+click for multi-select, then drag selected cards to another category tab.</p>

      {#if deleteResult}
        <p class="text-muted mb-3">Delete queue processed: {deleteResult.mediaRemoved} media removed, {deleteResult.linksRemoved} links removed.</p>
      {/if}

      <div class="media-grid">
        {#each visibleMedia as item}
          <div
            class="media-card {isSelected(item.id) ? 'is-selected' : ''} text-left"
            onclick={(e) => selectMedia(item.id, e)}
            onkeydown={(e) => (e.key === 'Enter' || e.key === ' ') && selectMedia(item.id, e as unknown as MouseEvent)}
            role="button"
            tabindex="0"
            draggable="true"
            ondragstart={(e) => onDragStart(item.id, e)}
          >
            <div class="thumb-wrap">
              <img src={imageSrc(item.filePath)} alt={item.title || fileName(item.filePath)} loading="lazy" />
              {#if (faceDetections[item.id] || []).length > 0}
                <div class="face-overlay">
                  {#each faceDetections[item.id] as face, idx}
                    <button
                      type="button"
                      class="face-box {selectedFace?.mediaId === item.id && selectedFace?.index === idx ? 'is-active' : ''}"
                      style="left: {(face.x / face.imageWidth) * 100}%; top: {(face.y / face.imageHeight) * 100}%; width: {(face.w / face.imageWidth) * 100}%; height: {(face.h / face.imageHeight) * 100}%;"
                      onclick={(e) => { e.stopPropagation(); selectedFace = { mediaId: item.id, index: idx }; }}
                      aria-label={t('media.selectFace')}
                    ></button>
                  {/each}
                </div>
              {/if}
            </div>
            <div class="p-3 space-y-1">
              <div class="font-mono text-base truncate">{fileName(item.filePath)}</div>
              <div class="text-muted truncate">{item.title || 'Untitled'}</div>
              <div class="text-muted truncate">{item.peopleLabel || 'Unlinked'}</div>
              <div class="text-muted">Links: {item.linkCount}</div>
              <button class="btn-outline w-full text-xs mt-2" onclick={(e) => { e.stopPropagation(); runFaceDetection(item); }} disabled={detectionBusyByMedia[item.id]}>
                {detectionBusyByMedia[item.id] ? 'Detecting...' : 'Detect Faces'}
              </button>
              {#if selectedFace?.mediaId === item.id}
                <div class="mt-2 flex gap-2">
                  <select class="arch-input flex-1" bind:value={selectedPersonXref} onclick={(e) => e.stopPropagation()} aria-label={t('common.filter')}>
                    <option value="">Assign person...</option>
                    {#each people as p}
                      <option value={p.xref}>{p.givenName} {p.surname} ({p.xref})</option>
                    {/each}
                  </select>
                  <button class="btn-primary text-xs" onclick={(e) => { e.stopPropagation(); assignSelectedFace(); }} disabled={!selectedPersonXref}>Tag</button>
                </div>
              {/if}
            </div>
          </div>
        {/each}
      </div>
      {#if visibleMedia.length === 0}
        <p class="text-muted py-8">{t('media.noMedia')}</p>
      {/if}
    </section>

    <section class="arch-card p-6">
      <h2 class="display-gradient-spirit text-3xl">{t('media.rename')}</h2>
      <p class="text-muted mt-2">Naming scheme: <span class="font-mono">[surname]-[givenname]-[event]-[sequence].[ext]</span></p>
      <div class="mt-4 flex flex-wrap gap-3">
        <button class="btn-primary" onclick={buildRenamePreview} disabled={renameBusy}>Build Preview</button>
        <button class="btn-outline" onclick={applyRename} disabled={renameBusy || renamePreview.length === 0}>Apply Rename</button>
      </div>
      <p class="text-muted mt-3">Preview source: selected media if any, otherwise active category results.</p>
      {#if renamePreview.length > 0}
        <div class="mt-4 overflow-auto">
          <table class="rename-table">
            <thead>
              <tr><th>Current</th><th>Proposed</th></tr>
            </thead>
            <tbody>
              {#each renamePreview as row}
                <tr>
                  <td class="font-mono">{row.currentPath}</td>
                  <td class="font-mono">{row.proposedPath}</td>
                </tr>
              {/each}
            </tbody>
          </table>
        </div>
      {/if}
    </section>

    {#if statusMsg}
      <div class="arch-card p-4 text-muted">{statusMsg}</div>
    {/if}
  </div>
</div>

<style>
  .text-muted {
    color: var(--color-muted);
  }

  .tab-chip {
    border: 2px solid var(--color-blue);
    color: var(--color-blue);
    background: transparent;
    padding: 0.75rem 1rem;
    border-radius: 0.75rem;
    font-family: var(--font-mono);
    font-weight: 700;
    letter-spacing: 0.04em;
    transition: all var(--motion-duration) var(--motion-ease);
  }

  .tab-chip.is-active,
  .tab-chip:hover {
    background: var(--color-blue);
    color: var(--color-charcoal);
  }

  .media-grid {
    display: grid;
    grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
    gap: 1rem;
  }

  .media-card {
    background: var(--color-concrete);
    border: 1px solid rgba(30, 159, 242, 0.15);
    border-radius: 0.8rem;
    overflow: hidden;
    transition: transform var(--motion-duration) var(--motion-ease), border-color var(--motion-duration) var(--motion-ease);
  }

  .media-card:hover {
    transform: translateY(-4px);
    border-color: rgba(95, 223, 223, 0.5);
  }

  .media-card.is-selected {
    border: 2px solid var(--color-cyan);
    box-shadow: 0 0 0 1px rgba(95, 223, 223, 0.5), 0 0 24px rgba(95, 223, 223, 0.25);
  }

  .thumb-wrap {
    background: #0a0f16;
    aspect-ratio: 1;
    position: relative;
  }

  .thumb-wrap img {
    width: 100%;
    height: 100%;
    object-fit: cover;
  }

  .face-overlay {
    position: absolute;
    inset: 0;
    pointer-events: none;
  }

  .face-box {
    position: absolute;
    border: 2px solid rgba(95, 223, 223, 0.9);
    background: rgba(95, 223, 223, 0.12);
    border-radius: 0.35rem;
    pointer-events: auto;
  }

  .face-box.is-active {
    border-color: var(--color-accent);
    background: rgba(255, 45, 85, 0.16);
  }

  .rename-table {
    width: 100%;
    border-collapse: collapse;
  }

  .rename-table th,
  .rename-table td {
    padding: 0.75rem;
    border: 1px solid rgba(95, 223, 223, 0.2);
    text-align: left;
    font-size: 1rem;
  }

  .rename-table th {
    color: var(--color-white);
    font-family: var(--font-mono);
  }
</style>
