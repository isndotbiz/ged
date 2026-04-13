<script lang="ts">
  import { clearAll, getStats, isDbEmpty, getSetting, setSetting, exportDbAsJson, importDbFromJson, getDb } from '$lib/db';
  import { importGedcom } from '$lib/gedcom-parser';
  import { exportGedcom } from '$lib/gedcom-exporter';
  import { appStats, isImporting, importProgress, importMessage } from '$lib/stores';
  import { pickAndReadTextFile } from '$lib/platform-fs';
  import { isTauri } from '$lib/platform';
  import { loadLocale, setLocale, getLocale, t, type Locale } from '$lib/i18n';

  let livingThreshold = $state(110);
  let confirmClear = $state(false);
  let saveStatus = $state('');
  let backupBusy = $state(false);
  let lastBackupDate = $state('');
  let mergeGedcomBusy = $state(false);
  let themePreference = $state<'light' | 'dark' | 'system'>('system');
  let exportFormat = $state<'5.5.1' | '7.0'>('5.5.1');
  let exportPreview = $state({ personCount: 0, familyCount: 0, eventCount: 0, sourceCount: 0, mediaCount: 0, placeCount: 0 });
  let exportBusy = $state(false);
  let exportMessage = $state('');
  let jsonBusy = $state(false);
  let jsonMessage = $state('');
  let language = $state<Locale>('en');
  let autoBackups = $state.raw<{ key: string; exportedAt: string }[]>([]);
  let dbSize = $state('0 KB');
  let deferredInstallPrompt = $state<any>(null);
  let hideInstallPrompt = $state(false);
  let undoHistory = $state.raw<{ id: string; description: string; createdAt: string }[]>([]);


  async function loadSettings() {
    const threshold = await getSetting('living_threshold');
    if (threshold) livingThreshold = parseInt(threshold);

    await loadLocale();
    language = getLocale();
    hideInstallPrompt = localStorage.getItem('install-prompt-dismissed') === '1';
    await refreshBackups();
    await refreshDbStats();
    await loadUndoHistory();
    const lbd = await getSetting('last_backup_date');
    if (lbd) lastBackupDate = lbd;
    const savedTheme = await getSetting('theme_mode');
    if (savedTheme === 'dark' || savedTheme === 'light') themePreference = savedTheme;
    else themePreference = 'system';
  }

  async function backupNow() {
    backupBusy = true;
    try {
      if (isTauri()) {
        const { invoke } = await import('@tauri-apps/api/core');
        await invoke('backup_database');
      } else {
        // Web: export JSON backup
        const backup = await exportDbAsJson();
        const date = new Date().toISOString().slice(0, 10);
        const blob = new Blob([JSON.stringify(backup)], { type: 'application/json' });
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = `gedfix-backup-${date}.json`;
        a.click();
        URL.revokeObjectURL(url);
      }
      const now = new Date().toLocaleString();
      lastBackupDate = now;
      await setSetting('last_backup_date', now);
      saveStatus = t('settings.backupSuccess');
      setTimeout(() => { saveStatus = ''; }, 3000);
    } catch (e) {
      saveStatus = `Backup failed: ${e}`;
    }
    backupBusy = false;
  }

  async function mergeGedcom() {
    const result = await pickAndReadTextFile([{ name: 'GEDCOM', extensions: ['ged'] }]);
    if (!result) return;
    mergeGedcomBusy = true;
    $isImporting = true;
    $importProgress = 0;
    $importMessage = 'Merging file...';
    try {
      const linkResult = await importGedcom(result.text, (pct, msg) => {
        $importProgress = pct;
        $importMessage = msg;
      }, { force: false, mode: 'merge' });
      const stats = await getStats();
      appStats.set(stats);
      $importMessage = `Merge complete${linkResult.linked > 0 ? ` • ${t('import.mediaLinked', { count: linkResult.linked })}` : ''}`;
    } catch (e) {
      $importMessage = `Merge error: ${e}`;
    }
    $isImporting = false;
    mergeGedcomBusy = false;
  }

  async function saveTheme() {
    await setSetting('theme_mode', themePreference);
    // Apply immediately
    const mode = themePreference === 'system'
      ? (window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light')
      : themePreference;
    document.documentElement.dataset.theme = mode;
    saveStatus = 'Theme saved';
    setTimeout(() => { saveStatus = ''; }, 2000);
  }


  async function saveLivingThreshold() {
    await setSetting('living_threshold', String(livingThreshold));
    saveStatus = 'Threshold saved';
    setTimeout(() => { saveStatus = ''; }, 2000);
  }

  async function importFile() {
    const result = await pickAndReadTextFile([{ name: 'GEDCOM', extensions: ['ged'] }]);
    if (!result) return;

    $isImporting = true;
    $importProgress = 0;
    $importMessage = 'Reading file...';

    try {
      const linkResult = await importGedcom(result.text, (pct, msg) => {
        $importProgress = pct;
        $importMessage = msg;
      }, { force: true });
      const stats = await getStats();
      appStats.set(stats);
      $importMessage = `Import complete${linkResult.linked > 0 ? ` • ${t('import.mediaLinked', { count: linkResult.linked })}` : ''}`;
    } catch (e) {
      console.error('Import error:', e);
      $importMessage = `Error: ${e}`;
    }
    $isImporting = false;
  }

  async function clearDatabase() {
    await clearAll();
    const stats = await getStats();
    appStats.set(stats);
    await refreshExportPreview();
    confirmClear = false;
  }

  async function refreshExportPreview() {
    const stats = await getStats();
    exportPreview = {
      personCount: stats.personCount,
      familyCount: stats.familyCount,
      eventCount: stats.eventCount,
      sourceCount: stats.sourceCount,
      mediaCount: stats.mediaCount,
      placeCount: stats.placeCount,
    };
  }

  async function exportGedcomFile() {
    exportBusy = true;
    exportMessage = '';
    try {
      const gedcom = await exportGedcom(exportFormat);
      const date = new Date().toISOString().slice(0, 10);
      const filename = `gedfix_export_${date}_${exportFormat.replace(/\./g, '')}.ged`;

      if (await isDbEmpty()) {
        exportMessage = 'Database is empty. Import data before exporting.';
        exportBusy = false;
        return;
      }

      if (!isTauri()) {
        const blob = new Blob([gedcom], { type: 'text/plain' });
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = filename;
        a.click();
        URL.revokeObjectURL(url);
        exportMessage = `Saved ${filename}`;
      } else {
        const { writeTextFile } = await import('@tauri-apps/plugin-fs');
        const { downloadDir, join } = await import('@tauri-apps/api/path');
        const downloads = await downloadDir();
        const path = await join(downloads, filename);
        await writeTextFile(path, gedcom);
        exportMessage = `Saved ${path}`;
      }
    } catch (e) {
      exportMessage = `Export error: ${e}`;
    }
    exportBusy = false;
  }

  async function exportGedcomQuickDownload() {
    exportBusy = true;
    exportMessage = '';
    try {
      const gedcom = await exportGedcom();
      const date = new Date().toISOString().slice(0, 10);
      const filename = `gedfix-export-${date}.ged`;
      const blob = new Blob([gedcom], { type: 'text/plain' });
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = filename;
      a.click();
      URL.revokeObjectURL(url);
      exportMessage = `Saved ${filename}`;
    } catch (e) {
      exportMessage = `Export error: ${e}`;
    } finally {
      exportBusy = false;
    }
  }

  async function exportJson() {
    jsonBusy = true;
    jsonMessage = '';
    try {
      const backup = await exportDbAsJson();
      const json = JSON.stringify(backup, null, 2);
      const date = new Date().toISOString().slice(0, 10);
      const filename = `gedfix_backup_${date}.json`;

      if (!isTauri()) {
        const blob = new Blob([json], { type: 'application/json' });
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = filename;
        a.click();
        URL.revokeObjectURL(url);
        jsonMessage = `Saved ${filename}`;
      } else {
        const { writeTextFile } = await import('@tauri-apps/plugin-fs');
        const { downloadDir, join } = await import('@tauri-apps/api/path');
        const downloads = await downloadDir();
        const path = await join(downloads, filename);
        await writeTextFile(path, json);
        jsonMessage = `Saved ${path}`;
      }
    } catch (e) {
      jsonMessage = `Export error: ${e}`;
    }
    jsonBusy = false;
  }

  async function importJson() {
    jsonBusy = true;
    jsonMessage = '';
    try {
      const result = await pickAndReadTextFile([{ name: 'JSON', extensions: ['json'] }]);
      if (!result) { jsonBusy = false; return; }
      const backup = JSON.parse(result.text);
      await importDbFromJson(backup);
      const stats = await getStats();
      appStats.set(stats);
      await refreshExportPreview();
      jsonMessage = 'Import complete';
    } catch (e) {
      jsonMessage = `Import error: ${e}`;
    }
    jsonBusy = false;
  }

  async function refreshBackups() {
    if (isTauri()) return;
    const keys = Object.keys(localStorage).filter((k) => k.startsWith('backup-')).sort().reverse();
    autoBackups = keys.map((key) => {
      const raw = localStorage.getItem(key);
      const exportedAt = raw ? (JSON.parse(raw).exportedAt as string) : key;
      return { key, exportedAt };
    }).slice(0, 5);
  }

  async function restoreAutoBackup(key: string) {
    const raw = localStorage.getItem(key);
    if (!raw) return;
    if (!confirm('This will replace all data. Continue?')) return;
    await importDbFromJson(JSON.parse(raw));
    const stats = await getStats();
    appStats.set(stats);
    await refreshExportPreview();
  }

  async function deleteAutoBackup(key: string) {
    localStorage.removeItem(key);
    await refreshBackups();
  }

  async function refreshDbStats() {
    const backup = await exportDbAsJson();
    const bytes = new Blob([JSON.stringify(backup)]).size;
    dbSize = bytes > 1024 * 1024 ? `${(bytes / (1024 * 1024)).toFixed(2)} MB` : `${Math.round(bytes / 1024)} KB`;
  }

  async function onLanguageChange() {
    await setLocale(language);
  }

  function dismissInstallPrompt() {
    localStorage.setItem('install-prompt-dismissed', '1');
    hideInstallPrompt = true;
  }

  async function loadUndoHistory() {
    const d = await getDb();
    undoHistory = await d.select<{ id: string; description: string; createdAt: string }[]>(
      `SELECT id, description, createdAt FROM undo_log ORDER BY createdAt DESC LIMIT 20`
    );
  }

  $effect(() => {
    const handler = (e: Event) => {
      e.preventDefault();
      deferredInstallPrompt = e as any;
    };
    window.addEventListener('beforeinstallprompt', handler as EventListener);
    return () => window.removeEventListener('beforeinstallprompt', handler as EventListener);
  });
  $effect(() => { loadSettings(); });
  $effect(() => { refreshExportPreview(); });
</script>

<div class="p-8 max-w-2xl animate-fade-in">
  <div class="mb-8">
    <h1 class="text-2xl font-bold tracking-tight" style="font-family: var(--font-serif); color: var(--ink);">{t('settings.title')}</h1>
    <p class="text-sm text-ink-muted mt-1">{t('settings.subtitle')}</p>
    {#if saveStatus}
      <div class="mt-2 text-xs font-medium px-3 py-1 rounded-lg inline-block" style="background: var(--parchment); color: var(--sepia);">{saveStatus}</div>
    {/if}
  </div>

  <!-- Data Management -->
  <section class="mb-8">
    <h2 class="arch-section-header">{t('settings.dataManagement')}</h2>
    <div class="arch-card divide-y arch-card-divide">
      <div class="flex items-center justify-between px-5 py-4">
        <div>
          <div class="text-sm font-medium text-ink">{t('nav.importGedcom')}</div>
          <div class="text-xs text-ink-muted">Load a GEDCOM file into the database</div>
        </div>
        <button
          onclick={importFile}
          disabled={$isImporting}
          class="px-4 py-2 text-sm btn-accent disabled:opacity-50 transition-colors"
         aria-label={t('common.actions')}>
          {$isImporting ? t('common.importing') : 'Choose File'}
        </button>
      </div>

      <div class="flex items-center justify-between px-5 py-4">
        <div>
          <div class="text-sm font-medium text-ink">{t('settings.mergeGedcom')}</div>
          <div class="text-xs text-ink-muted">{t('settings.mergeNote')}</div>
        </div>
        <button onclick={mergeGedcom} disabled={mergeGedcomBusy || $isImporting} class="px-4 py-2 text-sm btn-secondary disabled:opacity-50 transition-colors" aria-label={t('common.actions')}>
          {mergeGedcomBusy ? t('common.merging') : t('settings.mergeGedcom')}
        </button>
      </div>

      <div class="flex items-center justify-between px-5 py-4">
        <div>
          <div class="text-sm font-medium text-ink">{t('settings.backupNow')}</div>
          {#if lastBackupDate}<div class="text-xs text-ink-muted">{t('settings.lastBackup')}: {lastBackupDate}</div>{/if}
        </div>
        <button onclick={backupNow} disabled={backupBusy} class="px-4 py-2 text-sm btn-secondary disabled:opacity-50 transition-colors" aria-label={t('common.actions')}>
          {backupBusy ? 'Backing up...' : t('settings.backupNow')}
        </button>
      </div>

      {#if $isImporting}
        <div class="px-5 py-3">
          <div class="w-full h-1.5 arch-progress-track mb-1">
            <div class="h-full arch-progress-bar transition-all duration-300" style="width: {$importProgress}%"></div>
          </div>
          <p class="text-xs text-ink-muted">{$importMessage}</p>
        </div>
      {/if}

      <div class="flex items-center justify-between px-5 py-4">
        <div>
          <div class="text-sm font-medium text-ink">{t('settings.clearDatabase')}</div>
          <div class="text-xs text-ink-muted">Remove all imported data</div>
        </div>
        {#if confirmClear}
          <div class="flex gap-2">
            <button onclick={clearDatabase} class="px-3 py-1.5 bg-red-500 text-white text-xs font-medium rounded-lg hover:bg-red-600 transition-colors" aria-label={t('common.actions')}>{t('common.confirm')}</button>
            <button onclick={() => { confirmClear = false; }} class="px-3 py-1.5 text-xs font-medium btn-secondary transition-colors">{t('common.cancel')}</button>
          </div>
        {:else}
          <button onclick={() => { confirmClear = true; }} class="px-4 py-2 text-red-600 text-sm font-medium rounded-lg hover:bg-red-50 transition-colors" style="background: var(--parchment);">{t('common.clear')}</button>
        {/if}
      </div>

      <div class="flex items-center justify-between px-5 py-4">
        <div>
          <div class="text-sm font-medium text-ink">{t('settings.databaseStats')}</div>
          <div class="text-xs text-ink-muted">Current data summary</div>
        </div>
        <div class="text-xs text-ink-muted text-right">
          <div>{$appStats.personCount} people, {$appStats.familyCount} families</div>
          <div>{$appStats.eventCount} events, {$appStats.sourceCount} sources</div>
        </div>
      </div>
    </div>
  </section>

  <!-- Export / Backup -->
  <section class="mb-8">
    <h2 class="arch-section-header">{t('settings.exportBackup')}</h2>
    <div class="arch-card divide-y arch-card-divide">
      <div class="px-5 py-4">
        <div class="text-sm font-medium text-ink">{t('backup.exportPreview')}</div>
        <div class="text-xs text-ink-muted mt-1">
          {exportPreview.personCount} people, {exportPreview.familyCount} families, {exportPreview.eventCount} events, {exportPreview.sourceCount} sources, {exportPreview.mediaCount} media, {exportPreview.placeCount} places
        </div>
      </div>

      <div class="flex items-center justify-between px-5 py-4">
        <div>
          <div class="text-sm font-medium text-ink">{t('settings.exportGedcom')}</div>
          <div class="text-xs text-ink-muted">Download GEDCOM 5.5.1</div>
        </div>
        <button onclick={exportGedcomQuickDownload} disabled={exportBusy} class="px-4 py-2 text-sm btn-accent disabled:opacity-50 transition-colors">
          {exportBusy ? t('common.exporting') : t('settings.exportGedcom')}
        </button>
      </div>

      <div class="flex items-center justify-between px-5 py-4">
        <div>
          <div class="text-sm font-medium text-ink">{t('backup.exportGedcom')}</div>
          <div class="text-xs text-ink-muted">Download a GEDCOM file (5.5.1 or 7.0)</div>
        </div>
        <div class="flex items-center gap-2">
          <select bind:value={exportFormat} class="px-2 py-1.5 text-xs rounded-lg arch-input" aria-label={t('common.filter')}>
            <option value="5.5.1">5.5.1</option>
            <option value="7.0">7.0</option>
          </select>
          <button onclick={exportGedcomFile} disabled={exportBusy} class="px-4 py-2 text-sm btn-accent disabled:opacity-50 transition-colors">
            {exportBusy ? t('common.exporting') : `Export ${exportFormat}`}
          </button>
        </div>
      </div>
      {#if exportMessage}
        <div class="px-5 pb-4 text-xs text-ink-muted">{exportMessage}</div>
      {/if}

      <div class="flex items-center justify-between px-5 py-4">
        <div>
          <div class="text-sm font-medium text-ink">{t('backup.exportJson')}</div>
          <div class="text-xs text-ink-muted">Full database snapshot for restore</div>
        </div>
        <button onclick={exportJson} disabled={jsonBusy} class="px-4 py-2 text-sm btn-accent disabled:opacity-50 transition-colors" aria-label={t('common.actions')}>
          {jsonBusy ? t('common.exporting') : 'Export JSON'}
        </button>
      </div>

      <div class="flex items-center justify-between px-5 py-4">
        <div>
          <div class="text-sm font-medium text-ink">{t('backup.importJson')}</div>
          <div class="text-xs text-ink-muted">Restore database from a JSON export</div>
        </div>
        <button onclick={importJson} disabled={jsonBusy} class="px-4 py-2 text-sm btn-accent disabled:opacity-50 transition-colors" aria-label={t('common.actions')}>
          {jsonBusy ? t('common.importing') : 'Import JSON'}
        </button>
      </div>
      {#if jsonMessage}
        <div class="px-5 pb-4 text-xs text-ink-muted">{jsonMessage}</div>
      {/if}

      <div class="px-5 py-4">
        <div class="text-sm font-medium text-ink">{t('backup.autoBackups')}</div>
        <div class="text-xs text-ink-muted mb-2">Last 5 auto backups before import</div>
        {#if autoBackups.length === 0}
          <div class="text-xs text-ink-faint">{t('backup.noBackups')}</div>
        {:else}
          {#each autoBackups as backup}
            <div class="flex items-center justify-between py-1">
              <span class="text-xs text-ink-muted">{backup.exportedAt}</span>
              <div class="flex items-center gap-2">
                <button class="btn-secondary px-2 py-1 text-xs" onclick={() => restoreAutoBackup(backup.key)}>Restore</button>
                <button class="btn-secondary px-2 py-1 text-xs" onclick={() => deleteAutoBackup(backup.key)}>Delete</button>
              </div>
            </div>
          {/each}
        {/if}
      </div>
      <div class="px-5 pb-4 text-xs text-ink-muted">Storage used: {dbSize}</div>
    </div>
  </section>

  <!-- Display Preferences -->
  <section class="mb-8">
    <h2 class="arch-section-header">{t('settings.displayPreferences')}</h2>
    <div class="arch-card divide-y arch-card-divide">
      <div class="flex items-center justify-between px-5 py-4">
        <div>
          <div class="text-sm font-medium text-ink">{t('settings.livingThreshold')}</div>
          <div class="text-xs text-ink-muted">Years from birth to assume deceased (no death record)</div>
        </div>
        <div class="flex items-center gap-2">
          <input
            type="number"
            bind:value={livingThreshold}
            onchange={saveLivingThreshold}
            min="80"
            max="130"
            class="w-16 px-2 py-1.5 text-sm rounded-lg border outline-none text-center arch-input"
           aria-label={t('common.search')} />
          <span class="text-xs text-ink-faint">years</span>
        </div>
      </div>
      <div class="flex items-center justify-between px-5 py-4">
        <div>
          <div class="text-sm font-medium text-ink">{t('nav.theme')}</div>
          <div class="text-xs text-ink-muted">Choose light, dark, or follow system</div>
        </div>
        <select bind:value={themePreference} onchange={saveTheme} class="arch-input px-2 py-1.5 text-sm" aria-label={t('common.filter')}>
          <option value="system">System</option>
          <option value="light">Light</option>
          <option value="dark">Dark</option>
        </select>
      </div>
      <div class="flex items-center justify-between px-5 py-4">
        <div>
          <div class="text-sm font-medium text-ink">{t('settings.language')}</div>
          <div class="text-xs text-ink-muted">Change application locale</div>
        </div>
        <select bind:value={language} onchange={onLanguageChange} class="arch-input px-2 py-1.5 text-sm" aria-label={t('common.filter')}>
          <option value="en">English</option>
          <option value="es">Español</option>
          <option value="de">Deutsch</option>
          <option value="fr">Français</option>
          <option value="pt">Português</option>
        </select>
      </div>
      {#if deferredInstallPrompt && !hideInstallPrompt}
        <div class="flex items-center justify-between px-5 py-4">
          <div>
            <div class="text-sm font-medium text-ink">{t('settings.installApp')}</div>
            <div class="text-xs text-ink-muted">Install GedFix as a web app</div>
          </div>
          <div class="flex gap-2">
            <button
              class="btn-accent px-3 py-2 text-sm"
              onclick={async () => {
                await deferredInstallPrompt.prompt();
                deferredInstallPrompt = null;
              }}
            >Install</button>
            <button class="btn-secondary px-3 py-2 text-sm" onclick={dismissInstallPrompt}>Not now</button>
          </div>
        </div>
      {/if}
    </div>
  </section>


  <!-- About -->
  <section>
    <h2 class="arch-section-header">{t('settings.about')}</h2>
    <div class="arch-card px-5 py-4">
      <div class="text-sm font-medium text-ink">GedFix</div>
      <div class="text-xs text-ink-muted mt-0.5">Version 1.0.0</div>
      <div class="text-xs text-ink-faint mt-2">A premium genealogy application for managing and analyzing GEDCOM family tree data.</div>
    </div>
  </section>

  <section class="mt-8">
    <h2 class="arch-section-header">{t('settings.editHistory')}</h2>
    <div class="arch-card px-5 py-4">
      {#if undoHistory.length === 0}
        <div class="text-xs text-ink-faint">No recent edits logged.</div>
      {:else}
        {#each undoHistory as row}
          <div class="flex items-center justify-between py-1 text-xs">
            <span class="text-ink-muted">{row.description}</span>
            <span class="text-ink-faint">{row.createdAt}</span>
          </div>
        {/each}
      {/if}
    </div>
  </section>
</div>
