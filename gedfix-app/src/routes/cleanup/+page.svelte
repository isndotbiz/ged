<script lang="ts">
  import { appStats } from '$lib/stores';
  import { getDb } from '$lib/db';
  import type { DuplicateCandidate } from '$lib/media-matcher';
  import { exportGedcom } from '$lib/gedcom-exporter';
  import { writeTextFile } from '@tauri-apps/plugin-fs';
  import { join, downloadDir, homeDir } from '@tauri-apps/api/path';

  // --- Stats ---
  let totalPeople = $state(0);
  let totalFamilies = $state(0);
  let totalMedia = $state(0);
  let linkedMedia = $state(0);
  let unlinkedMedia = $state(0);
  let totalSources = $state(0);
  let statsLoaded = $state(false);

  // --- Media Matching ---
  let mediaScanning = $state(false);
  let mediaScanProgress = $state(0);
  let mediaScanMsg = $state('');
  let mediaMatchResult = $state<{ matched: number; unmatched: number; created: number; linked: number } | null>(null);

  // --- Deduplication ---
  let deduping = $state(false);
  let dedupeProgress = $state(0);
  let dedupeMsg = $state('');
  let dedupeResult = $state<{ duplicateGroups: number; entriesRemoved: number; linksTransferred: number } | null>(null);

  // --- Duplicate People ---
  let dupScanning = $state(false);
  let dupProgress = $state(0);
  let dupMsg = $state('');
  let duplicatePairs = $state<DuplicateCandidate[]>([]);
  let mergingIds = $state<Set<string>>(new Set());

  // --- Export ---
  let exporting = $state(false);
  let exportPath = $state('');
  let exportError = $state('');

  async function loadStats() {
    try {
      const db = await getDb();
      const people = await db.select<{cnt: number}[]>('SELECT COUNT(*) as cnt FROM person');
      const families = await db.select<{cnt: number}[]>('SELECT COUNT(*) as cnt FROM family');
      const media = await db.select<{cnt: number}[]>('SELECT COUNT(*) as cnt FROM media');
      const sources = await db.select<{cnt: number}[]>('SELECT COUNT(*) as cnt FROM source');
      const linked = await db.select<{cnt: number}[]>(
        `SELECT COUNT(DISTINCT m.id) as cnt FROM media m
         JOIN media_person_link mpl ON mpl.mediaId = m.id`
      );

      totalPeople = people[0]?.cnt ?? 0;
      totalFamilies = families[0]?.cnt ?? 0;
      totalMedia = media[0]?.cnt ?? 0;
      totalSources = sources[0]?.cnt ?? 0;
      linkedMedia = linked[0]?.cnt ?? 0;
      unlinkedMedia = totalMedia - linkedMedia;
      statsLoaded = true;
    } catch (e) {
      console.error('Failed to load stats:', e);
      // Fall back to store stats
      totalPeople = $appStats.personCount;
      totalFamilies = $appStats.familyCount;
      totalMedia = $appStats.mediaCount;
      totalSources = $appStats.sourceCount;
      statsLoaded = true;
    }
  }

  async function scanAndMatch() {
    mediaScanning = true;
    mediaScanProgress = 0;
    mediaScanMsg = 'Scanning media directories...';
    mediaMatchResult = null;

    try {
      const { scanAndMatchMedia } = await import('$lib/media-matcher');
      const home = await homeDir();
      const dirs = [
        `${home}/Documents/GedFix/media/photos`,
        `${home}/Documents/GedFix/renamed_media`,
      ];
      const result = await scanAndMatchMedia(dirs, (pct: number, msg: string) => {
        mediaScanProgress = pct;
        mediaScanMsg = msg;
      });
      mediaMatchResult = result;
      mediaScanMsg = 'Scan complete';
      // Refresh stats
      await loadStats();
    } catch (e) {
      mediaScanMsg = `Error: ${e}`;
    }
    mediaScanning = false;
  }

  async function deduplicateMedia() {
    deduping = true;
    dedupeProgress = 0;
    dedupeMsg = 'Finding duplicate media...';
    dedupeResult = null;

    try {
      const { deduplicateMedia: dedupe } = await import('$lib/media-matcher');
      const result = await dedupe((pct: number, msg: string) => {
        dedupeProgress = pct;
        dedupeMsg = msg;
      });
      dedupeResult = result;
      dedupeMsg = 'Deduplication complete';
      await loadStats();
    } catch (e) {
      dedupeMsg = `Error: ${e}`;
    }
    deduping = false;
  }

  async function scanDuplicatePeople() {
    dupScanning = true;
    dupProgress = 0;
    dupMsg = 'Scanning for duplicate people...';
    duplicatePairs = [];

    try {
      const { findDuplicatePeople } = await import('$lib/media-matcher');
      const pairs = await findDuplicatePeople((pct: number, msg: string) => {
        dupProgress = pct;
        dupMsg = msg;
      });
      duplicatePairs = pairs.sort((a, b) => b.score - a.score);
      dupMsg = `Found ${pairs.length} potential duplicate pairs`;
    } catch (e) {
      dupMsg = `Error: ${e}`;
    }
    dupScanning = false;
  }

  function pairId(pair: DuplicateCandidate): string {
    return `${pair.person1.xref}-${pair.person2.xref}`;
  }

  async function mergePair(pair: DuplicateCandidate) {
    const id = pairId(pair);
    const next = new Set(mergingIds); next.add(id); mergingIds = next;
    // Keep the person with more data
    const keep = (pair.person1.sourceCount + pair.person1.mediaCount) >= (pair.person2.sourceCount + pair.person2.mediaCount) ? pair.person1 : pair.person2;
    const remove = keep.xref === pair.person1.xref ? pair.person2 : pair.person1;
    try {
      const { mergePersons } = await import('$lib/media-matcher');
      await mergePersons(keep.xref, remove.xref);
      duplicatePairs = duplicatePairs.filter(p => pairId(p) !== id);
      await loadStats();
    } catch (e) {
      console.error('Merge failed:', e);
    }
    const done = new Set(mergingIds); done.delete(id); mergingIds = done;
  }

  function skipPair(pair: DuplicateCandidate) {
    duplicatePairs = duplicatePairs.filter(p => pairId(p) !== pairId(pair));
  }

  async function doExport() {
    exporting = true;
    exportPath = '';
    exportError = '';
    try {
      const gedcom = await exportGedcom();
      const downloads = await downloadDir();
      const date = new Date().toISOString().slice(0, 10);
      const filename = `gedfix_export_${date}.ged`;
      const path = await join(downloads, filename);
      await writeTextFile(path, gedcom);
      exportPath = path;
    } catch (e) {
      exportError = String(e);
    }
    exporting = false;
  }

  $effect(() => {
    loadStats();
  });
</script>

<div class="p-8 max-w-4xl animate-fade-in">
  <!-- Page Header -->
  <div class="mb-6">
    <h1 class="text-2xl font-bold tracking-tight" style="font-family: var(--font-serif); color: var(--ink);">Tree Cleanup</h1>
    <p class="text-sm text-ink-muted mt-1">Match media, deduplicate records, and export your tree</p>
    <div class="mt-3" style="border-top: 2px double var(--ink-faint);"></div>
  </div>

  <div class="space-y-5">

    <!-- Section 1: Stats Dashboard -->
    <div class="arch-card rounded-xl p-6">
      <div class="flex items-center gap-3 mb-5">
        <div class="w-8 h-8 rounded-lg flex items-center justify-center" style="background: var(--accent-subtle);">
          <svg class="w-4 h-4" fill="none" stroke="var(--accent)" stroke-width="1.5" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" d="M3 13.125C3 12.504 3.504 12 4.125 12h2.25c.621 0 1.125.504 1.125 1.125v6.75C7.5 20.496 6.996 21 6.375 21h-2.25A1.125 1.125 0 013 19.875v-6.75zM9.75 8.625c0-.621.504-1.125 1.125-1.125h2.25c.621 0 1.125.504 1.125 1.125v11.25c0 .621-.504 1.125-1.125 1.125h-2.25a1.125 1.125 0 01-1.125-1.125V8.625zM16.5 4.125c0-.621.504-1.125 1.125-1.125h2.25C20.496 3 21 3.504 21 4.125v15.75c0 .621-.504 1.125-1.125 1.125h-2.25a1.125 1.125 0 01-1.125-1.125V4.125z" />
          </svg>
        </div>
        <div>
          <h2 class="text-sm font-semibold text-ink">Database Overview</h2>
          <p class="text-xs text-ink-muted">Current record counts and media status</p>
        </div>
      </div>

      {#if statsLoaded}
        <div class="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-5 gap-3">
          <div class="rounded-lg p-4 text-center" style="background: var(--parchment);">
            <div class="text-2xl font-bold text-ink" style="font-family: var(--font-mono);">{totalPeople.toLocaleString()}</div>
            <div class="text-[10px] uppercase tracking-wider text-ink-muted mt-1" style="font-family: var(--font-sans);">People</div>
          </div>
          <div class="rounded-lg p-4 text-center" style="background: var(--parchment);">
            <div class="text-2xl font-bold text-ink" style="font-family: var(--font-mono);">{totalFamilies.toLocaleString()}</div>
            <div class="text-[10px] uppercase tracking-wider text-ink-muted mt-1" style="font-family: var(--font-sans);">Families</div>
          </div>
          <div class="rounded-lg p-4 text-center" style="background: var(--parchment);">
            <div class="text-2xl font-bold text-ink" style="font-family: var(--font-mono);">{totalSources.toLocaleString()}</div>
            <div class="text-[10px] uppercase tracking-wider text-ink-muted mt-1" style="font-family: var(--font-sans);">Sources</div>
          </div>
          <div class="rounded-lg p-4 text-center" style="background: var(--parchment);">
            <div class="text-2xl font-bold text-ink" style="font-family: var(--font-mono);">{linkedMedia.toLocaleString()}</div>
            <div class="text-[10px] uppercase tracking-wider mt-1" style="font-family: var(--font-sans); color: var(--color-validated);">Linked Media</div>
          </div>
          <div class="rounded-lg p-4 text-center" style="background: var(--parchment);">
            <div class="text-2xl font-bold" style="font-family: var(--font-mono); color: {unlinkedMedia > 0 ? 'var(--color-warning)' : 'var(--ink)'};">{unlinkedMedia.toLocaleString()}</div>
            <div class="text-[10px] uppercase tracking-wider text-ink-muted mt-1" style="font-family: var(--font-sans);">Unlinked Media</div>
          </div>
        </div>
      {:else}
        <div class="flex items-center gap-2 text-sm text-ink-muted py-4">
          <svg class="w-4 h-4 animate-spin" fill="none" viewBox="0 0 24 24"><circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"></circle><path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8v4a4 4 0 00-4 4H4z"></path></svg>
          Loading stats...
        </div>
      {/if}
    </div>

    <!-- Section 2: Match Unlinked Media -->
    <div class="arch-card rounded-xl p-6">
      <div class="flex items-center justify-between mb-4">
        <div class="flex items-center gap-3">
          <div class="w-8 h-8 rounded-lg flex items-center justify-center" style="background: var(--accent-subtle);">
            <svg class="w-4 h-4" fill="none" stroke="var(--accent)" stroke-width="1.5" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" d="M13.19 8.688a4.5 4.5 0 011.242 7.244l-4.5 4.5a4.5 4.5 0 01-6.364-6.364l1.757-1.757m9.86-2.06a4.5 4.5 0 00-6.364-6.364L4.5 8.257m10.5.243l4.5 4.5" />
            </svg>
          </div>
          <div>
            <h2 class="text-sm font-semibold text-ink">Match Unlinked Media</h2>
            <p class="text-xs text-ink-muted">Scan photos and renamed media directories, match files to people</p>
          </div>
        </div>
        <button
          onclick={scanAndMatch}
          disabled={mediaScanning}
          class="px-4 py-2 btn-accent text-white text-sm font-medium rounded-lg disabled:opacity-50 transition-colors"
        >
          {mediaScanning ? 'Scanning...' : 'Scan & Match Media'}
        </button>
      </div>

      {#if mediaScanning}
        <div class="mt-2">
          <div class="w-full h-1.5 arch-progress-track rounded-full overflow-hidden mb-2">
            <div class="h-full arch-progress-bar rounded-full transition-all duration-300" style="width: {mediaScanProgress}%"></div>
          </div>
          <p class="text-xs text-ink-muted">{mediaScanMsg}</p>
        </div>
      {/if}

      {#if mediaMatchResult}
        <div class="grid grid-cols-4 gap-3 mt-4">
          <div class="rounded-lg p-3 text-center" style="background: var(--parchment);">
            <div class="text-lg font-bold" style="color: var(--color-validated); font-family: var(--font-mono);">{mediaMatchResult.matched}</div>
            <div class="text-[10px] uppercase tracking-wider text-ink-muted">Matched</div>
          </div>
          <div class="rounded-lg p-3 text-center" style="background: var(--parchment);">
            <div class="text-lg font-bold" style="color: var(--color-warning); font-family: var(--font-mono);">{mediaMatchResult.unmatched}</div>
            <div class="text-[10px] uppercase tracking-wider text-ink-muted">Unmatched</div>
          </div>
          <div class="rounded-lg p-3 text-center" style="background: var(--parchment);">
            <div class="text-lg font-bold text-ink" style="font-family: var(--font-mono);">{mediaMatchResult.created}</div>
            <div class="text-[10px] uppercase tracking-wider text-ink-muted">Created</div>
          </div>
          <div class="rounded-lg p-3 text-center" style="background: var(--parchment);">
            <div class="text-lg font-bold text-ink" style="font-family: var(--font-mono);">{mediaMatchResult.linked}</div>
            <div class="text-[10px] uppercase tracking-wider text-ink-muted">Linked</div>
          </div>
        </div>
      {/if}
    </div>

    <!-- Section 3: Deduplicate Images -->
    <div class="arch-card rounded-xl p-6">
      <div class="flex items-center justify-between mb-4">
        <div class="flex items-center gap-3">
          <div class="w-8 h-8 rounded-lg flex items-center justify-center" style="background: var(--accent-subtle);">
            <svg class="w-4 h-4" fill="none" stroke="var(--accent)" stroke-width="1.5" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" d="M15.75 17.25v3.375c0 .621-.504 1.125-1.125 1.125h-9.75a1.125 1.125 0 01-1.125-1.125V7.875c0-.621.504-1.125 1.125-1.125H6.75a9.06 9.06 0 011.5.124m7.5 10.376h3.375c.621 0 1.125-.504 1.125-1.125V11.25c0-4.46-3.243-8.161-7.5-8.876a9.06 9.06 0 00-1.5-.124H9.375c-.621 0-1.125.504-1.125 1.125v3.5m7.5 10.375H9.375a1.125 1.125 0 01-1.125-1.125v-9.25m12 6.625v-1.875a3.375 3.375 0 00-3.375-3.375h-1.5a1.125 1.125 0 01-1.125-1.125v-1.5a3.375 3.375 0 00-3.375-3.375H9.75" />
            </svg>
          </div>
          <div>
            <h2 class="text-sm font-semibold text-ink">Deduplicate Images</h2>
            <p class="text-xs text-ink-muted">Find duplicate media entries and merge them, transferring all links</p>
          </div>
        </div>
        <button
          onclick={deduplicateMedia}
          disabled={deduping}
          class="px-4 py-2 btn-accent text-white text-sm font-medium rounded-lg disabled:opacity-50 transition-colors"
        >
          {deduping ? 'Processing...' : 'Find & Merge Duplicates'}
        </button>
      </div>

      {#if deduping}
        <div class="mt-2">
          <div class="w-full h-1.5 arch-progress-track rounded-full overflow-hidden mb-2">
            <div class="h-full arch-progress-bar rounded-full transition-all duration-300" style="width: {dedupeProgress}%"></div>
          </div>
          <p class="text-xs text-ink-muted">{dedupeMsg}</p>
        </div>
      {/if}

      {#if dedupeResult}
        <div class="grid grid-cols-3 gap-3 mt-4">
          <div class="rounded-lg p-3 text-center" style="background: var(--parchment);">
            <div class="text-lg font-bold text-ink" style="font-family: var(--font-mono);">{dedupeResult.duplicateGroups}</div>
            <div class="text-[10px] uppercase tracking-wider text-ink-muted">Groups Found</div>
          </div>
          <div class="rounded-lg p-3 text-center" style="background: var(--parchment);">
            <div class="text-lg font-bold" style="color: var(--color-validated); font-family: var(--font-mono);">{dedupeResult.entriesRemoved}</div>
            <div class="text-[10px] uppercase tracking-wider text-ink-muted">Entries Removed</div>
          </div>
          <div class="rounded-lg p-3 text-center" style="background: var(--parchment);">
            <div class="text-lg font-bold text-ink" style="font-family: var(--font-mono);">{dedupeResult.linksTransferred}</div>
            <div class="text-[10px] uppercase tracking-wider text-ink-muted">Links Transferred</div>
          </div>
        </div>
      {/if}
    </div>

    <!-- Section 4: Find Duplicate People -->
    <div class="arch-card rounded-xl p-6">
      <div class="flex items-center justify-between mb-4">
        <div class="flex items-center gap-3">
          <div class="w-8 h-8 rounded-lg flex items-center justify-center" style="background: var(--accent-subtle);">
            <svg class="w-4 h-4" fill="none" stroke="var(--accent)" stroke-width="1.5" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" d="M15 19.128a9.38 9.38 0 002.625.372 9.337 9.337 0 004.121-.952 4.125 4.125 0 00-7.533-2.493M15 19.128v-.003c0-1.113-.285-2.16-.786-3.07M15 19.128v.106A12.318 12.318 0 018.624 21c-2.331 0-4.512-.645-6.374-1.766l-.001-.109a6.375 6.375 0 0111.964-3.07M12 6.375a3.375 3.375 0 11-6.75 0 3.375 3.375 0 016.75 0zm8.25 2.25a2.625 2.625 0 11-5.25 0 2.625 2.625 0 015.25 0z" />
            </svg>
          </div>
          <div>
            <h2 class="text-sm font-semibold text-ink">Find Duplicate People</h2>
            <p class="text-xs text-ink-muted">Detect potential duplicate person records and merge them</p>
          </div>
        </div>
        <button
          onclick={scanDuplicatePeople}
          disabled={dupScanning}
          class="px-4 py-2 btn-accent text-white text-sm font-medium rounded-lg disabled:opacity-50 transition-colors"
        >
          {dupScanning ? 'Scanning...' : 'Scan for Duplicates'}
        </button>
      </div>

      {#if dupScanning}
        <div class="mt-2">
          <div class="w-full h-1.5 arch-progress-track rounded-full overflow-hidden mb-2">
            <div class="h-full arch-progress-bar rounded-full transition-all duration-300" style="width: {dupProgress}%"></div>
          </div>
          <p class="text-xs text-ink-muted">{dupMsg}</p>
        </div>
      {/if}

      {#if !dupScanning && duplicatePairs.length > 0}
        <div class="text-xs text-ink-muted mb-3">{duplicatePairs.length} candidate pairs sorted by match score</div>
        <div class="space-y-3 max-h-[480px] overflow-y-auto">
          {#each duplicatePairs as pair}
            {@const pid = pairId(pair)}
            {@const isMerging = mergingIds.has(pid)}
            {@const scorePercent = Math.round(pair.score * 100)}
            <div class="rounded-lg border p-4 transition-all" style="background: var(--paper); border-color: var(--border-subtle);">
              <div class="flex items-center justify-between mb-3">
                <div class="flex items-center gap-2">
                  <span class="text-[10px] font-bold px-2 py-0.5 rounded-full"
                    style="background: {scorePercent >= 90 ? 'rgba(166,61,47,0.1)' : scorePercent >= 75 ? 'rgba(184,134,11,0.1)' : 'rgba(74,124,63,0.1)'};
                           color: {scorePercent >= 90 ? 'var(--color-error)' : scorePercent >= 75 ? 'var(--color-warning)' : 'var(--color-validated)'};"
                  >{scorePercent}% match</span>
                  {#each pair.matchReasons as reason}
                    <span class="text-[9px] px-1.5 py-0.5 rounded" style="background: var(--parchment); color: var(--ink-muted);">{reason}</span>
                  {/each}
                </div>
                <div class="flex gap-1.5">
                  <button onclick={() => mergePair(pair)} disabled={isMerging}
                    class="px-3 py-1.5 text-xs font-medium rounded-md text-white transition-colors disabled:opacity-50"
                    style="background: var(--color-validated);">{isMerging ? 'Merging...' : 'Merge'}</button>
                  <button onclick={() => skipPair(pair)}
                    class="px-3 py-1.5 text-xs font-medium rounded-md transition-colors"
                    style="background: var(--parchment); color: var(--ink-light);">Skip</button>
                </div>
              </div>
              <div class="grid grid-cols-2 gap-4">
                <div class="rounded-lg p-3" style="background: var(--vellum);">
                  <div class="text-sm font-semibold text-ink">{pair.person1.givenName} {pair.person1.surname}</div>
                  <div class="text-[10px] text-ink-faint mt-0.5" style="font-family: var(--font-mono);">{pair.person1.xref}</div>
                  {#if pair.person1.birthDate}<div class="text-xs text-ink-muted mt-1.5">b. {pair.person1.birthDate}</div>{/if}
                  {#if pair.person1.birthPlace}<div class="text-xs text-ink-faint">{pair.person1.birthPlace}</div>{/if}
                  {#if pair.person1.deathDate}<div class="text-xs text-ink-muted">d. {pair.person1.deathDate}</div>{/if}
                  <div class="flex gap-3 mt-2 text-[10px] text-ink-faint">
                    <span>{pair.person1.sourceCount} sources</span><span>{pair.person1.mediaCount} media</span>
                  </div>
                </div>
                <div class="rounded-lg p-3" style="background: var(--vellum);">
                  <div class="text-sm font-semibold text-ink">{pair.person2.givenName} {pair.person2.surname}</div>
                  <div class="text-[10px] text-ink-faint mt-0.5" style="font-family: var(--font-mono);">{pair.person2.xref}</div>
                  {#if pair.person2.birthDate}<div class="text-xs text-ink-muted mt-1.5">b. {pair.person2.birthDate}</div>{/if}
                  {#if pair.person2.birthPlace}<div class="text-xs text-ink-faint">{pair.person2.birthPlace}</div>{/if}
                  {#if pair.person2.deathDate}<div class="text-xs text-ink-muted">d. {pair.person2.deathDate}</div>{/if}
                  <div class="flex gap-3 mt-2 text-[10px] text-ink-faint">
                    <span>{pair.person2.sourceCount} sources</span><span>{pair.person2.mediaCount} media</span>
                  </div>
                </div>
              </div>
            </div>
          {/each}
        </div>
      {/if}

      {#if !dupScanning && duplicatePairs.length === 0 && dupMsg}
        <div class="text-sm text-ink-faint py-4 text-center">{dupMsg}</div>
      {/if}
    </div>

    <!-- Section 5: Export GEDCOM -->
    <div class="arch-card rounded-xl p-6">
      <div class="flex items-center justify-between mb-4">
        <div class="flex items-center gap-3">
          <div class="w-8 h-8 rounded-lg flex items-center justify-center" style="background: var(--accent-subtle);">
            <svg class="w-4 h-4" fill="none" stroke="var(--accent)" stroke-width="1.5" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" d="M3 16.5v2.25A2.25 2.25 0 005.25 21h13.5A2.25 2.25 0 0021 18.75V16.5M16.5 12L12 16.5m0 0L7.5 12m4.5 4.5V3" />
            </svg>
          </div>
          <div>
            <h2 class="text-sm font-semibold text-ink">Export GEDCOM</h2>
            <p class="text-xs text-ink-muted">Export your full tree as a GEDCOM 5.5.1 file to Downloads</p>
          </div>
        </div>
        <button
          onclick={doExport}
          disabled={exporting}
          class="px-4 py-2 btn-accent text-white text-sm font-medium rounded-lg disabled:opacity-50 transition-colors"
        >
          {#if exporting}
            <span class="flex items-center gap-2">
              <svg class="w-4 h-4 animate-spin" fill="none" viewBox="0 0 24 24"><circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"></circle><path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8v4a4 4 0 00-4 4H4z"></path></svg>
              Exporting...
            </span>
          {:else}
            Export GEDCOM 5.5.1
          {/if}
        </button>
      </div>

      {#if exportPath}
        <div class="rounded-lg p-3 mt-2 flex items-center gap-2" style="background: rgba(74,124,63,0.06); border: 1px solid rgba(74,124,63,0.15);">
          <svg class="w-4 h-4 shrink-0" fill="none" stroke="var(--color-validated)" stroke-width="2" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" d="M9 12.75L11.25 15 15 9.75M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
          </svg>
          <div>
            <div class="text-xs font-medium" style="color: var(--color-validated);">Export saved successfully</div>
            <div class="text-[10px] text-ink-muted mt-0.5" style="font-family: var(--font-mono); word-break: break-all;">{exportPath}</div>
          </div>
        </div>
      {/if}

      {#if exportError}
        <div class="rounded-lg p-3 mt-2 flex items-center gap-2" style="background: rgba(166,61,47,0.06); border: 1px solid rgba(166,61,47,0.15);">
          <svg class="w-4 h-4 shrink-0" fill="none" stroke="var(--color-error)" stroke-width="2" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" d="M12 9v3.75m9-.75a9 9 0 11-18 0 9 9 0 0118 0zm-9 3.75h.008v.008H12v-.008z" />
          </svg>
          <div class="text-xs" style="color: var(--color-error);">{exportError}</div>
        </div>
      {/if}
    </div>

  </div>
</div>
