<script lang="ts">
  import { t } from '$lib/i18n';
  import { appStats } from '$lib/stores';
  import { getDb, findSameNameGenerations, findCollateralRelatives, updatePerson, deduplicateMediaByNormalizedPath, autoCategorizeMediaAfterDedup } from '$lib/db';
  import type { Person } from '$lib/types';
  import type { DuplicateCandidate, MergeLogEntry } from '$lib/media-matcher';
  import { exportGedcom } from '$lib/gedcom-exporter';
  import { isTauri } from '$lib/platform';

  // --- Stats ---
  let totalPeople = $state(0);
  let totalFamilies = $state(0);
  let totalMedia = $state(0);
  let linkedMedia = $state(0);
  let unlinkedMedia = $state(0);
  let totalSources = $state(0);
  let statsLoaded = $state(false);

  // --- Media Stats ---
  let mediaStatsLoaded = $state(false);
  let mediaStatsError = $state('');
  let mediaTotal = $state(0);
  let mediaUniquePaths = $state(0);
  let mediaLinkRows = $state(0);
  let topLinkedMedia = $state<{ id: number; title: string; filePath: string; personCount: number }[]>([]);
  let duplicateFilePaths = $state<{ filePath: string; cnt: number }[]>([]);
  let dedupChecksRunning = $state(false);
  let dedupChecksError = $state('');
  let dedupChecks = $state<{
    totalMedia: number;
    duplicatePaths: { filePath: string; cnt: number }[];
    duplicateLinks: { mediaId: number; personXref: string; cnt: number }[];
    emmaLinks: { givenName: string; surname: string; linked_media: number }[];
    suspiciousPeople: { givenName: string; surname: string; link_count: number }[];
    sharedMedia: { id: number; filePath: string; title: string; person_count: number }[];
  } | null>(null);

  // --- Media Matching ---
  let mediaScanning = $state(false);
  let mediaScanProgress = $state(0);
  let mediaScanMsg = $state('');
  let mediaMatchResult = $state<{ matched: number; unmatched: number; created: number; linked: number } | null>(null);

  // --- Deduplication ---
  let deduping = $state(false);
  let dedupeProgress = $state(0);
  let dedupeMsg = $state('');
  let dedupeResult = $state<{ duplicateGroups: number; entriesRemoved: number; linksTransferred: number; categorizedUpdated: number } | null>(null);

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

  async function loadMediaStats() {
    mediaStatsLoaded = false;
    mediaStatsError = '';
    try {
      const db = await getDb();
      const total = await db.select<{ cnt: number }[]>('SELECT COUNT(*) as cnt FROM media');
      const unique = await db.select<{ cnt: number }[]>(
        `SELECT COUNT(DISTINCT filePath) as cnt FROM media WHERE filePath != ''`
      );
      const linkRows = await db.select<{ cnt: number }[]>('SELECT COUNT(*) as cnt FROM media_person_link');
      const top = await db.select<{ id: number; filePath: string; title: string; personCount: number }[]>(`
        SELECT m.id, m.filePath, m.title, COUNT(mpl.personXref) as personCount
        FROM media m
        JOIN media_person_link mpl ON mpl.mediaId = m.id
        GROUP BY m.id
        ORDER BY personCount DESC
        LIMIT 5
      `);
      const duplicates = await db.select<{ filePath: string; cnt: number }[]>(`
        SELECT LOWER(REPLACE(TRIM(filePath), '\\', '/')) as filePath, COUNT(*) as cnt
        FROM media
        WHERE filePath != ''
        GROUP BY LOWER(REPLACE(TRIM(filePath), '\\', '/'))
        HAVING COUNT(*) > 1
        ORDER BY cnt DESC
      `);

      mediaTotal = total[0]?.cnt ?? 0;
      mediaUniquePaths = unique[0]?.cnt ?? 0;
      mediaLinkRows = linkRows[0]?.cnt ?? 0;
      topLinkedMedia = top;
      duplicateFilePaths = duplicates;
      mediaStatsLoaded = true;
    } catch (e) {
      mediaStatsError = String(e);
      mediaStatsLoaded = true;
    }
  }

  async function runDedupChecks() {
    dedupChecksRunning = true;
    dedupChecksError = '';
    try {
      const db = await getDb();
      const total = await db.select<{ total_media: number }[]>(`SELECT COUNT(*) as total_media FROM media`);
      const dups = await db.select<{ filePath: string; cnt: number }[]>(
        `SELECT LOWER(REPLACE(TRIM(filePath), '\\', '/')) as filePath, COUNT(*) as cnt
         FROM media
         WHERE filePath != ''
         GROUP BY LOWER(REPLACE(TRIM(filePath), '\\', '/'))
         HAVING COUNT(*) > 1`
      );
      const dupLinks = await db.select<{ mediaId: number; personXref: string; cnt: number }[]>(
        `SELECT mediaId, personXref, COUNT(*) as cnt
         FROM media_person_link
         GROUP BY mediaId, personXref
         HAVING COUNT(*) > 1
         ORDER BY cnt DESC
         LIMIT 50`
      );
      const emma = await db.select<{ givenName: string; surname: string; linked_media: number }[]>(`
        SELECT p.givenName, p.surname, COUNT(DISTINCT mpl.mediaId) as linked_media
        FROM person p
        JOIN media_person_link mpl ON mpl.personXref = p.xref
        WHERE p.surname LIKE '%Skorbisz%' OR p.surname LIKE '%Haas%'
        GROUP BY p.xref
      `);
      const suspicious = await db.select<{ givenName: string; surname: string; link_count: number }[]>(`
        SELECT p.givenName, p.surname, COUNT(*) as link_count
        FROM person p
        JOIN media_person_link mpl ON mpl.personXref = p.xref
        GROUP BY p.xref
        HAVING COUNT(*) > 50
        ORDER BY link_count DESC
      `);
      const shared = await db.select<{ id: number; filePath: string; title: string; person_count: number }[]>(`
        SELECT m.id, m.filePath, m.title, COUNT(mpl.personXref) as person_count
        FROM media m
        JOIN media_person_link mpl ON mpl.mediaId = m.id
        GROUP BY m.id
        HAVING COUNT(mpl.personXref) > 10
        ORDER BY person_count DESC
        LIMIT 20
      `);

      dedupChecks = {
        totalMedia: total[0]?.total_media ?? 0,
        duplicatePaths: dups,
        duplicateLinks: dupLinks,
        emmaLinks: emma,
        suspiciousPeople: suspicious,
        sharedMedia: shared,
      };
    } catch (e) {
      dedupChecksError = String(e);
    }
    dedupChecksRunning = false;
  }

  async function scanAndMatch() {
    mediaScanning = true;
    mediaScanProgress = 0;
    mediaScanMsg = 'Scanning media directories...';
    mediaMatchResult = null;

    try {
      const { scanAndMatchMedia } = await import('$lib/media-matcher');
      if (!isTauri()) { mediaScanMsg = 'Media scanning requires desktop app'; mediaScanning = false; return; }
      const { homeDir } = await import('@tauri-apps/api/path');
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
      dedupeProgress = 30;
      dedupeMsg = 'Merging duplicate media by normalized path...';
      const result = await deduplicateMediaByNormalizedPath();
      dedupeProgress = 75;
      dedupeMsg = 'Auto-categorizing media...';
      const categorized = await autoCategorizeMediaAfterDedup();
      dedupeResult = { ...result, categorizedUpdated: categorized.updated };
      dedupeProgress = 100;
      dedupeMsg = 'Deduplication complete';
      await loadStats();
      await loadMediaStats();
      await runDedupChecks();
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
      const date = new Date().toISOString().slice(0, 10);
      const filename = `gedfix_export_${date}.ged`;

      if (isTauri()) {
        const { writeTextFile } = await import('@tauri-apps/plugin-fs');
        const { downloadDir, join } = await import('@tauri-apps/api/path');
        const downloads = await downloadDir();
        const path = await join(downloads, filename);
        await writeTextFile(path, gedcom);
        exportPath = path;
      } else {
        // Web: download via browser
        const blob = new Blob([gedcom], { type: 'text/plain' });
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = filename;
        a.click();
        URL.revokeObjectURL(url);
        exportPath = filename;
      }
    } catch (e) {
      exportError = String(e);
    }
    exporting = false;
  }

  // --- Merge History & Undo ---
  let mergeLog = $state<MergeLogEntry[]>([]);
  let mergeLogLoading = $state(false);
  let unmerging = $state<Set<number>>(new Set());

  async function loadMergeLog() {
    mergeLogLoading = true;
    try {
      const { getMergeLog } = await import('$lib/media-matcher');
      mergeLog = await getMergeLog();
    } catch { mergeLog = []; }
    mergeLogLoading = false;
  }

  async function undoMerge(logId: number) {
    unmerging = new Set([...unmerging, logId]);
    try {
      const { unmergePersons } = await import('$lib/media-matcher');
      const result = await unmergePersons(logId);
      if (result.success) {
        mergeLog = mergeLog.map(m => m.id === logId ? { ...m, undoneAt: 'just now' } : m);
      } else {
        console.error('Unmerge failed:', result.error);
      }
    } catch (e) {
      console.error('Unmerge error:', e);
    }
    unmerging = new Set([...unmerging].filter(id => id !== logId));
  }

  // --- Sr/Jr Generation Labeling ---
  let genScanning = $state(false);
  let genPairs = $state<{ elder: Person; younger: Person; yearGap: number }[]>([]);
  let genMsg = $state('');
  let genApplying = $state<Set<string>>(new Set());

  async function scanSameNameGenerations() {
    genScanning = true;
    genMsg = 'Scanning for same-name generations...';
    genPairs = [];
    try {
      const results = await findSameNameGenerations();
      genPairs = results;
      genMsg = `Found ${results.length} potential father-son pairs`;
    } catch (e) {
      genMsg = `Error: ${e}`;
    }
    genScanning = false;
  }

  async function applySrJr(elder: Person, younger: Person) {
    const key = `${elder.xref}-${younger.xref}`;
    const next = new Set(genApplying); next.add(key); genApplying = next;
    try {
      if (!elder.suffix) await updatePerson(elder.xref, { suffix: 'Sr' });
      if (!younger.suffix) await updatePerson(younger.xref, { suffix: 'Jr' });
      genPairs = genPairs.filter(p => !(p.elder.xref === elder.xref && p.younger.xref === younger.xref));
    } catch (e) {
      console.error('Failed to apply Sr/Jr:', e);
    }
    const done = new Set(genApplying); done.delete(key); genApplying = done;
  }

  function skipGenPair(elder: Person, younger: Person) {
    genPairs = genPairs.filter(p => !(p.elder.xref === elder.xref && p.younger.xref === younger.xref));
  }

  // --- Collateral Line Tagging ---
  let collateralScanning = $state(false);
  let collateralResults = $state<{ person: Person; relationship: string }[]>([]);
  let collateralMsg = $state('');
  let collateralRootXref = $state('');
  let collateralTagging = $state<Set<string>>(new Set());

  async function scanCollaterals() {
    if (!collateralRootXref.trim()) {
      collateralMsg = 'Please enter a root person XREF (e.g., @I1@)';
      return;
    }
    collateralScanning = true;
    collateralMsg = 'Walking direct line and finding collateral relatives...';
    collateralResults = [];
    try {
      const results = await findCollateralRelatives(collateralRootXref.trim());
      collateralResults = results;
      collateralMsg = `Found ${results.length} collateral relatives`;
    } catch (e) {
      collateralMsg = `Error: ${e}`;
    }
    collateralScanning = false;
  }

  async function tagCollateral(person: Person) {
    const next = new Set(collateralTagging); next.add(person.xref); collateralTagging = next;
    try {
      await updatePerson(person.xref, { personColor: 'collateral' });
      collateralResults = collateralResults.filter(r => r.person.xref !== person.xref);
    } catch (e) {
      console.error('Failed to tag collateral:', e);
    }
    const done = new Set(collateralTagging); done.delete(person.xref); collateralTagging = done;
  }

  async function tagAllCollaterals() {
    for (const r of collateralResults) {
      await tagCollateral(r.person);
    }
  }

  $effect(() => {
    loadStats();
    loadMediaStats();
  });
</script>

<div class="p-8 max-w-4xl animate-fade-in">
  <!-- Page Header -->
  <div class="mb-6">
    <h1 class="text-2xl font-bold tracking-tight" style="font-family: var(--font-serif); color: var(--ink);">{t('cleanup.title')}</h1>
    <p class="text-sm text-ink-muted mt-1">{t('cleanup.subtitle')}</p>
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
          <h2 class="text-sm font-semibold text-ink">{t('cleanup.databaseOverview')}</h2>
          <p class="text-xs text-ink-muted">Current record counts and media status</p>
        </div>
      </div>

      {#if statsLoaded}
        <div class="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-5 gap-3">
          <div class="rounded-lg p-4 text-center" style="background: var(--parchment);">
            <div class="text-2xl font-bold text-ink" style="font-family: var(--font-mono);">{totalPeople.toLocaleString()}</div>
            <div class="text-[10px] uppercase tracking-wider text-ink-muted mt-1" style="font-family: var(--font-sans);">{t('dashboard.people')}</div>
          </div>
          <div class="rounded-lg p-4 text-center" style="background: var(--parchment);">
            <div class="text-2xl font-bold text-ink" style="font-family: var(--font-mono);">{totalFamilies.toLocaleString()}</div>
            <div class="text-[10px] uppercase tracking-wider text-ink-muted mt-1" style="font-family: var(--font-sans);">{t('dashboard.families')}</div>
          </div>
          <div class="rounded-lg p-4 text-center" style="background: var(--parchment);">
            <div class="text-2xl font-bold text-ink" style="font-family: var(--font-mono);">{totalSources.toLocaleString()}</div>
            <div class="text-[10px] uppercase tracking-wider text-ink-muted mt-1" style="font-family: var(--font-sans);">{t('dashboard.sources')}</div>
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

    <!-- Section 1b: Media Stats -->
    <div class="arch-card rounded-xl p-6">
      <div class="flex items-center gap-3 mb-5">
        <div class="w-8 h-8 rounded-lg flex items-center justify-center" style="background: var(--accent-subtle);">
          <svg class="w-4 h-4" fill="none" stroke="var(--accent)" stroke-width="1.5" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" d="M7.5 3.75h9A2.25 2.25 0 0118.75 6v12A2.25 2.25 0 0116.5 20.25h-9A2.25 2.25 0 015.25 18V6A2.25 2.25 0 017.5 3.75zM8.25 8.25h7.5M8.25 12h7.5M8.25 15.75h4.5" />
          </svg>
        </div>
        <div>
          <h2 class="text-sm font-semibold text-ink">{t('cleanup.mediaStats')}</h2>
          <p class="text-xs text-ink-muted">Verify dedup and linking without raw SQL</p>
        </div>
      </div>

      {#if !mediaStatsLoaded}
        <div class="flex items-center gap-2 text-sm text-ink-muted py-4">
          <svg class="w-4 h-4 animate-spin" fill="none" viewBox="0 0 24 24"><circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"></circle><path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8v4a4 4 0 00-4 4H4z"></path></svg>
          Loading media stats...
        </div>
      {:else if mediaStatsError}
        <div class="rounded-lg p-3 mt-2 flex items-center gap-2" style="background: rgba(166,61,47,0.06); border: 1px solid rgba(166,61,47,0.15);">
          <svg class="w-4 h-4 shrink-0" fill="none" stroke="var(--color-error)" stroke-width="2" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" d="M12 9v3.75m9-.75a9 9 0 11-18 0 9 9 0 0118 0zm-9 3.75h.008v.008H12v-.008z" />
          </svg>
          <div class="text-xs" style="color: var(--color-error);">{mediaStatsError}</div>
        </div>
      {:else}
        <div class="grid grid-cols-2 sm:grid-cols-3 gap-3">
          <div class="rounded-lg p-4 text-center" style="background: var(--parchment);">
            <div class="text-2xl font-bold text-ink" style="font-family: var(--font-mono);">{mediaTotal.toLocaleString()}</div>
            <div class="text-[10px] uppercase tracking-wider text-ink-muted mt-1" style="font-family: var(--font-sans);">Media Rows</div>
          </div>
          <div class="rounded-lg p-4 text-center" style="background: var(--parchment);">
            <div class="text-2xl font-bold text-ink" style="font-family: var(--font-mono);">{mediaUniquePaths.toLocaleString()}</div>
            <div class="text-[10px] uppercase tracking-wider text-ink-muted mt-1" style="font-family: var(--font-sans);">Unique File Paths</div>
          </div>
          <div class="rounded-lg p-4 text-center" style="background: var(--parchment);">
            <div class="text-2xl font-bold text-ink" style="font-family: var(--font-mono);">{mediaLinkRows.toLocaleString()}</div>
            <div class="text-[10px] uppercase tracking-wider text-ink-muted mt-1" style="font-family: var(--font-sans);">Media Links</div>
          </div>
        </div>

        <div class="mt-4 grid grid-cols-1 md:grid-cols-2 gap-4">
          <div class="rounded-lg p-4" style="background: var(--vellum);">
            <div class="text-xs font-semibold text-ink mb-2">Top 5 Most-Linked Media</div>
            {#if topLinkedMedia.length === 0}
              <div class="text-xs text-ink-muted">No linked media found.</div>
            {:else}
              <div class="space-y-2">
                {#each topLinkedMedia as m}
                  <div class="flex items-start justify-between gap-3 text-xs">
                    <div class="truncate">
                      <div class="text-ink">{m.title || 'Untitled'}</div>
                      <div class="text-ink-faint" style="font-family: var(--font-mono);">{m.filePath}</div>
                    </div>
                    <div class="text-ink-muted" style="font-family: var(--font-mono);">{m.personCount} linked</div>
                  </div>
                {/each}
              </div>
            {/if}
          </div>

          <div class="rounded-lg p-4" style="background: var(--vellum);">
            <div class="text-xs font-semibold text-ink mb-2">Duplicate File Paths</div>
            {#if duplicateFilePaths.length === 0}
              <div class="text-xs" style="color: var(--color-validated);">No duplicates found.</div>
            {:else}
              <div class="space-y-2 max-h-[200px] overflow-y-auto">
                {#each duplicateFilePaths as dup}
                  <div class="flex items-start justify-between gap-3 text-xs">
                    <div class="text-ink-faint" style="font-family: var(--font-mono);">{dup.filePath}</div>
                    <div class="text-ink-muted" style="font-family: var(--font-mono);">x{dup.cnt}</div>
                  </div>
                {/each}
              </div>
            {/if}
          </div>
        </div>
      {/if}
    </div>

    <!-- Section 1c: Dedup Verification -->
    <div class="arch-card rounded-xl p-6">
      <div class="flex items-center justify-between mb-4">
        <div>
          <h2 class="text-sm font-semibold text-ink">{t('cleanup.dedupVerification')}</h2>
          <p class="text-xs text-ink-muted">Runs the SQL checks and shows results inline</p>
        </div>
        <button onclick={runDedupChecks} disabled={dedupChecksRunning} class="px-4 py-2 btn-accent text-white text-sm font-medium rounded-lg disabled:opacity-50 transition-colors">
          {dedupChecksRunning ? 'Running...' : 'Run Dedup Checks'}
        </button>
      </div>

      {#if dedupChecksError}
        <div class="text-xs text-ink-muted">{dedupChecksError}</div>
      {/if}

      {#if dedupChecks}
        <div class="grid grid-cols-2 sm:grid-cols-3 gap-3 mb-4">
          <div class="rounded-lg p-3 text-center" style="background: var(--parchment);">
            <div class="text-lg font-bold text-ink" style="font-family: var(--font-mono);">{dedupChecks.totalMedia.toLocaleString()}</div>
            <div class="text-[10px] uppercase tracking-wider text-ink-muted">Total Media</div>
          </div>
          <div class="rounded-lg p-3 text-center" style="background: var(--parchment);">
            <div class="text-lg font-bold" style="font-family: var(--font-mono); color: {dedupChecks.duplicatePaths.length > 0 ? 'var(--color-warning)' : 'var(--color-validated)'};">{dedupChecks.duplicatePaths.length}</div>
            <div class="text-[10px] uppercase tracking-wider text-ink-muted">Duplicate Paths</div>
          </div>
          <div class="rounded-lg p-3 text-center" style="background: var(--parchment);">
            <div class="text-lg font-bold" style="font-family: var(--font-mono); color: {dedupChecks.duplicateLinks.length > 0 ? 'var(--color-warning)' : 'var(--color-validated)'};">{dedupChecks.duplicateLinks.length}</div>
            <div class="text-[10px] uppercase tracking-wider text-ink-muted">Duplicate Links</div>
          </div>
        </div>

        <div class="grid grid-cols-1 md:grid-cols-2 gap-4">
          <div class="rounded-lg p-4" style="background: var(--vellum);">
            <div class="text-xs font-semibold text-ink mb-2">Duplicate File Paths</div>
            {#if dedupChecks.duplicatePaths.length === 0}
              <div class="text-xs" style="color: var(--color-validated);">No duplicates found.</div>
            {:else}
              <div class="space-y-2 max-h-[180px] overflow-y-auto">
                {#each dedupChecks.duplicatePaths as dup}
                  <div class="flex items-start justify-between gap-3 text-xs">
                    <div class="text-ink-faint" style="font-family: var(--font-mono);">{dup.filePath}</div>
                    <div class="text-ink-muted" style="font-family: var(--font-mono);">x{dup.cnt}</div>
                  </div>
                {/each}
              </div>
            {/if}
          </div>

          <div class="rounded-lg p-4" style="background: var(--vellum);">
            <div class="text-xs font-semibold text-ink mb-2">Emma Skorbisz Haas</div>
            {#if dedupChecks.emmaLinks.length === 0}
              <div class="text-xs text-ink-muted">No matches found.</div>
            {:else}
              <div class="space-y-2">
                {#each dedupChecks.emmaLinks as row}
                  <div class="flex items-center justify-between text-xs">
                    <div class="text-ink">{row.givenName} {row.surname}</div>
                    <div class="text-ink-muted" style="font-family: var(--font-mono);">{row.linked_media} linked</div>
                  </div>
                {/each}
              </div>
            {/if}
          </div>

          <div class="rounded-lg p-4" style="background: var(--vellum);">
            <div class="text-xs font-semibold text-ink mb-2">People with 50+ Links</div>
            {#if dedupChecks.suspiciousPeople.length === 0}
              <div class="text-xs" style="color: var(--color-validated);">{t('common.none')}</div>
            {:else}
              <div class="space-y-2 max-h-[180px] overflow-y-auto">
                {#each dedupChecks.suspiciousPeople as row}
                  <div class="flex items-center justify-between text-xs">
                    <div class="text-ink">{row.givenName} {row.surname}</div>
                    <div class="text-ink-muted" style="font-family: var(--font-mono);">{row.link_count}</div>
                  </div>
                {/each}
              </div>
            {/if}
          </div>

          <div class="rounded-lg p-4" style="background: var(--vellum);">
            <div class="text-xs font-semibold text-ink mb-2">Duplicate Media Links</div>
            {#if dedupChecks.duplicateLinks.length === 0}
              <div class="text-xs" style="color: var(--color-validated);">{t('common.none')}</div>
            {:else}
              <div class="space-y-2 max-h-[180px] overflow-y-auto">
                {#each dedupChecks.duplicateLinks as row}
                  <div class="flex items-center justify-between text-xs">
                    <div class="text-ink-faint" style="font-family: var(--font-mono);">{row.personXref} / {row.mediaId}</div>
                    <div class="text-ink-muted" style="font-family: var(--font-mono);">x{row.cnt}</div>
                  </div>
                {/each}
              </div>
            {/if}
          </div>

          <div class="rounded-lg p-4" style="background: var(--vellum);">
            <div class="text-xs font-semibold text-ink mb-2">Shared Media (10+ People)</div>
            {#if dedupChecks.sharedMedia.length === 0}
              <div class="text-xs text-ink-muted">{t('common.none')}</div>
            {:else}
              <div class="space-y-2 max-h-[180px] overflow-y-auto">
                {#each dedupChecks.sharedMedia as row}
                  <div class="text-xs">
                    <div class="text-ink">{row.title || 'Untitled'}</div>
                    <div class="text-ink-faint" style="font-family: var(--font-mono);">{row.filePath}</div>
                    <div class="text-ink-muted" style="font-family: var(--font-mono);">{row.person_count} people</div>
                  </div>
                {/each}
              </div>
            {/if}
          </div>
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
            <h2 class="text-sm font-semibold text-ink">{t('cleanup.matchUnlinked')}</h2>
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
            <h2 class="text-sm font-semibold text-ink">{t('cleanup.deduplicateMedia')}</h2>
            <p class="text-xs text-ink-muted">Merge duplicates by normalized path, transfer links, then auto-categorize</p>
          </div>
        </div>
        <button
          onclick={deduplicateMedia}
          disabled={deduping}
          class="px-4 py-2 btn-accent text-white text-sm font-medium rounded-lg disabled:opacity-50 transition-colors"
        >
          {deduping ? 'Processing...' : 'Deduplicate Media'}
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
        <div class="grid grid-cols-2 md:grid-cols-4 gap-3 mt-4">
          <div class="rounded-lg p-3 text-center" style="background: var(--parchment);">
            <div class="text-lg font-bold text-ink" style="font-family: var(--font-mono);">{dedupeResult.duplicateGroups}</div>
            <div class="text-[10px] uppercase tracking-wider text-ink-muted">{t('cleanup.groupsFound')}</div>
          </div>
          <div class="rounded-lg p-3 text-center" style="background: var(--parchment);">
            <div class="text-lg font-bold" style="color: var(--color-validated); font-family: var(--font-mono);">{dedupeResult.entriesRemoved}</div>
            <div class="text-[10px] uppercase tracking-wider text-ink-muted">{t('cleanup.entriesRemoved')}</div>
          </div>
          <div class="rounded-lg p-3 text-center" style="background: var(--parchment);">
            <div class="text-lg font-bold text-ink" style="font-family: var(--font-mono);">{dedupeResult.linksTransferred}</div>
            <div class="text-[10px] uppercase tracking-wider text-ink-muted">{t('cleanup.linksTransferred')}</div>
          </div>
          <div class="rounded-lg p-3 text-center" style="background: var(--parchment);">
            <div class="text-lg font-bold text-ink" style="font-family: var(--font-mono);">{dedupeResult.categorizedUpdated}</div>
            <div class="text-[10px] uppercase tracking-wider text-ink-muted">{t('cleanup.categoriesUpdated')}</div>
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
            <h2 class="text-sm font-semibold text-ink">{t('cleanup.findDuplicates')}</h2>
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
            <h2 class="text-sm font-semibold text-ink">{t('backup.exportGedcom')}</h2>
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

    <!-- Section: Merge History & Undo -->
    <div class="arch-card rounded-xl p-6">
      <div class="flex items-center justify-between mb-4">
        <div class="flex items-center gap-3">
          <svg class="w-5 h-5" style="color: var(--accent);" fill="none" stroke="currentColor" stroke-width="1.5" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" d="M9 15L3 9m0 0l6-6M3 9h12a6 6 0 010 12h-3" />
          </svg>
          <div>
            <h2 class="text-sm font-semibold text-ink">{t('cleanup.mergeHistory')}</h2>
            <p class="text-xs text-ink-muted">View and undo recent person merges</p>
          </div>
        </div>
        <button onclick={loadMergeLog} disabled={mergeLogLoading} class="px-4 py-2 text-xs btn-accent">
          {mergeLogLoading ? 'Loading...' : 'Load History'}
        </button>
      </div>

      {#if mergeLog.length > 0}
        <div class="divide-y" style="border-color: var(--border-subtle);">
          {#each mergeLog as entry}
            {@const person = JSON.parse(entry.removedPersonJson)?.[0]}
            <div class="flex items-center justify-between py-3">
              <div>
                <span class="text-sm font-medium text-ink">
                  {person ? `${person.givenName} ${person.surname}` : entry.removeXref}
                </span>
                <span class="text-xs text-ink-muted mx-2">merged into</span>
                <span class="text-sm text-ink-light">{entry.keepXref}</span>
                <div class="text-[10px] text-ink-faint mt-0.5">{entry.mergedAt}</div>
              </div>
              {#if entry.undoneAt}
                <span class="text-xs text-green-600 font-medium px-2 py-1 rounded" style="background: rgba(74, 124, 63, 0.1);">Restored</span>
              {:else}
                <button
                  onclick={() => undoMerge(entry.id)}
                  disabled={unmerging.has(entry.id)}
                  class="px-3 py-1.5 text-xs font-medium rounded-lg transition-colors"
                  style="background: var(--parchment); color: var(--color-warning);"
                >
                  {unmerging.has(entry.id) ? 'Restoring...' : 'Undo Merge'}
                </button>
              {/if}
            </div>
          {/each}
        </div>
      {:else if !mergeLogLoading}
        <p class="text-xs text-ink-faint text-center py-4">{t('cleanup.noMergeHistory')}</p>
      {/if}
    </div>

    <!-- Section 6: Sr/Jr Generation Labeling -->
    <div class="arch-card rounded-xl p-6">
      <div class="flex items-center justify-between mb-4">
        <div class="flex items-center gap-3">
          <div class="w-8 h-8 rounded-lg flex items-center justify-center" style="background: var(--accent-subtle);">
            <svg class="w-4 h-4" fill="none" stroke="var(--accent)" stroke-width="1.5" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" d="M15 19.128a9.38 9.38 0 002.625.372 9.337 9.337 0 004.121-.952 4.125 4.125 0 00-7.533-2.493M15 19.128v-.003c0-1.113-.285-2.16-.786-3.07M15 19.128v.106A12.318 12.318 0 018.624 21c-2.331 0-4.512-.645-6.374-1.766l-.001-.109a6.375 6.375 0 0111.964-3.07M12 6.375a3.375 3.375 0 11-6.75 0 3.375 3.375 0 016.75 0zm8.25 2.25a2.625 2.625 0 11-5.25 0 2.625 2.625 0 015.25 0z" />
            </svg>
          </div>
          <div>
            <h2 class="text-sm font-semibold text-ink">{t('cleanup.srJr')}</h2>
            <p class="text-xs text-ink-muted">Find people with same first+last name born 20-50 years apart (father-son)</p>
          </div>
        </div>
        <button
          onclick={scanSameNameGenerations}
          disabled={genScanning}
          class="px-4 py-2 btn-accent text-white text-sm font-medium rounded-lg disabled:opacity-50 transition-colors"
        >
          {genScanning ? 'Scanning...' : 'Scan for Same-Name Generations'}
        </button>
      </div>

      {#if genScanning}
        <div class="flex items-center gap-2 text-sm text-ink-muted py-4">
          <svg class="w-4 h-4 animate-spin" fill="none" viewBox="0 0 24 24"><circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"></circle><path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8v4a4 4 0 00-4 4H4z"></path></svg>
          {genMsg}
        </div>
      {/if}

      {#if !genScanning && genPairs.length > 0}
        <div class="text-xs text-ink-muted mb-3">{genMsg}</div>
        <div class="overflow-x-auto">
          <table class="w-full text-sm">
            <thead>
              <tr class="text-left text-[10px] uppercase tracking-wider text-ink-muted" style="border-bottom: 1px solid var(--ink-faint);">
                <th class="pb-2 pr-3">Elder</th>
                <th class="pb-2 pr-3">Birth</th>
                <th class="pb-2 pr-3">Younger</th>
                <th class="pb-2 pr-3">Birth</th>
                <th class="pb-2 pr-3">Gap</th>
                <th class="pb-2 text-right">{t('common.actions')}</th>
              </tr>
            </thead>
            <tbody>
              {#each genPairs as pair}
                {@const key = `${pair.elder.xref}-${pair.younger.xref}`}
                <tr style="border-bottom: 1px solid var(--ink-faint);">
                  <td class="py-2 pr-3 text-ink">{pair.elder.givenName} {pair.elder.surname} {pair.elder.suffix}</td>
                  <td class="py-2 pr-3 text-ink-muted" style="font-family: var(--font-mono);">{pair.elder.birthDate}</td>
                  <td class="py-2 pr-3 text-ink">{pair.younger.givenName} {pair.younger.surname} {pair.younger.suffix}</td>
                  <td class="py-2 pr-3 text-ink-muted" style="font-family: var(--font-mono);">{pair.younger.birthDate}</td>
                  <td class="py-2 pr-3 text-ink-muted" style="font-family: var(--font-mono);">{pair.yearGap}y</td>
                  <td class="py-2 text-right">
                    <div class="flex items-center justify-end gap-2">
                      <button
                        onclick={() => applySrJr(pair.elder, pair.younger)}
                        disabled={genApplying.has(key)}
                        class="px-3 py-1 text-xs font-medium rounded-md transition-colors"
                        style="background: rgba(74,124,63,0.1); color: var(--color-validated); border: 1px solid rgba(74,124,63,0.2);"
                      >
                        {genApplying.has(key) ? 'Applying...' : 'Apply Sr/Jr'}
                      </button>
                      <button
                        onclick={() => skipGenPair(pair.elder, pair.younger)}
                        class="px-3 py-1 text-xs font-medium rounded-md text-ink-muted transition-colors"
                        style="background: var(--parchment); border: 1px solid var(--ink-faint);"
                      >
                        Skip
                      </button>
                    </div>
                  </td>
                </tr>
              {/each}
            </tbody>
          </table>
        </div>
      {/if}

      {#if !genScanning && genPairs.length === 0 && genMsg}
        <div class="text-sm text-ink-faint py-4 text-center">{genMsg}</div>
      {/if}
    </div>

    <!-- Section 7: Collateral Line Tagging -->
    <div class="arch-card rounded-xl p-6">
      <div class="flex items-center justify-between mb-4">
        <div class="flex items-center gap-3">
          <div class="w-8 h-8 rounded-lg flex items-center justify-center" style="background: var(--accent-subtle);">
            <svg class="w-4 h-4" fill="none" stroke="var(--accent)" stroke-width="1.5" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" d="M7.5 21L3 16.5m0 0L7.5 12M3 16.5h13.5m0-13.5L21 7.5m0 0L16.5 12M21 7.5H7.5" />
            </svg>
          </div>
          <div>
            <h2 class="text-sm font-semibold text-ink">{t('cleanup.collateral')}</h2>
            <p class="text-xs text-ink-muted">Identify siblings of direct-line ancestors and tag them as collateral</p>
          </div>
        </div>
      </div>

      <div class="flex items-center gap-3 mb-4">
        <input
          type="text"
          bind:value={collateralRootXref}
          placeholder="Root person XREF (e.g., @I1@)"
          class="flex-1 px-3 py-2 text-sm rounded-lg text-ink"
          style="background: var(--parchment); border: 1px solid var(--ink-faint); font-family: var(--font-mono);"
        />
        <button
          onclick={scanCollaterals}
          disabled={collateralScanning}
          class="px-4 py-2 btn-accent text-white text-sm font-medium rounded-lg disabled:opacity-50 transition-colors"
        >
          {collateralScanning ? 'Scanning...' : 'Find Collateral Relatives'}
        </button>
      </div>

      {#if collateralScanning}
        <div class="flex items-center gap-2 text-sm text-ink-muted py-4">
          <svg class="w-4 h-4 animate-spin" fill="none" viewBox="0 0 24 24"><circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"></circle><path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8v4a4 4 0 00-4 4H4z"></path></svg>
          {collateralMsg}
        </div>
      {/if}

      {#if !collateralScanning && collateralResults.length > 0}
        <div class="flex items-center justify-between mb-3">
          <div class="text-xs text-ink-muted">{collateralMsg}</div>
          <button
            onclick={tagAllCollaterals}
            class="px-3 py-1 text-xs font-medium rounded-md transition-colors"
            style="background: rgba(74,124,63,0.1); color: var(--color-validated); border: 1px solid rgba(74,124,63,0.2);"
          >
            Tag All as Collateral
          </button>
        </div>
        <div class="overflow-x-auto">
          <table class="w-full text-sm">
            <thead>
              <tr class="text-left text-[10px] uppercase tracking-wider text-ink-muted" style="border-bottom: 1px solid var(--ink-faint);">
                <th class="pb-2 pr-3">XREF</th>
                <th class="pb-2 pr-3">{t('common.name')}</th>
                <th class="pb-2 pr-3">Birth</th>
                <th class="pb-2 pr-3">Death</th>
                <th class="pb-2 pr-3">{t('common.status')}</th>
                <th class="pb-2 text-right">Action</th>
              </tr>
            </thead>
            <tbody>
              {#each collateralResults as result}
                <tr style="border-bottom: 1px solid var(--ink-faint);">
                  <td class="py-2 pr-3 text-ink-muted" style="font-family: var(--font-mono); font-size: 11px;">{result.person.xref}</td>
                  <td class="py-2 pr-3 text-ink">{result.person.givenName} {result.person.surname}</td>
                  <td class="py-2 pr-3 text-ink-muted" style="font-family: var(--font-mono);">{result.person.birthDate || '--'}</td>
                  <td class="py-2 pr-3 text-ink-muted" style="font-family: var(--font-mono);">{result.person.deathDate || '--'}</td>
                  <td class="py-2 pr-3">
                    {#if result.person.personColor === 'collateral'}
                      <span class="text-[10px] uppercase tracking-wider px-2 py-0.5 rounded-full" style="background: rgba(74,124,63,0.1); color: var(--color-validated);">Tagged</span>
                    {:else}
                      <span class="text-[10px] uppercase tracking-wider px-2 py-0.5 rounded-full" style="background: var(--parchment); color: var(--ink-muted);">{result.relationship}</span>
                    {/if}
                  </td>
                  <td class="py-2 text-right">
                    <button
                      onclick={() => tagCollateral(result.person)}
                      disabled={collateralTagging.has(result.person.xref) || result.person.personColor === 'collateral'}
                      class="px-3 py-1 text-xs font-medium rounded-md transition-colors disabled:opacity-50"
                      style="background: rgba(74,124,63,0.1); color: var(--color-validated); border: 1px solid rgba(74,124,63,0.2);"
                    >
                      {collateralTagging.has(result.person.xref) ? 'Tagging...' : 'Tag Collateral'}
                    </button>
                  </td>
                </tr>
              {/each}
            </tbody>
          </table>
        </div>
      {/if}

      {#if !collateralScanning && collateralResults.length === 0 && collateralMsg}
        <div class="text-sm text-ink-faint py-4 text-center">{collateralMsg}</div>
      {/if}
    </div>

  </div>
</div>
