<script lang="ts">
  import { page } from '$app/stores';
  import { goto } from '$app/navigation';
  import { appStats, treeIssues, isImporting, importProgress, importMessage } from '$lib/stores';
  import { isDbEmpty, getStats, getSetting, setSetting, exportDbAsJson } from '$lib/db';
  import { importGedcom } from '$lib/gedcom-parser';
  import { isTauri } from '$lib/platform';
  import { pickAndReadTextFile } from '$lib/platform-fs';
  import { announce } from '$lib/accessibility';
  import { clearShortcuts, getShortcuts, normalizeKeyCombo, registerShortcut, shouldIgnoreShortcutTarget, triggerShortcut } from '$lib/shortcuts';
  import { undoManager } from '$lib/undo-manager';
  import '../app.css';
  import type { Snippet } from 'svelte';

  let { children }: { children: Snippet } = $props();
  let sidebarOpen = $state(false);
  type ThemeMode = 'dark';
  let themeMode = $state<ThemeMode>('dark');
  let showWebWelcome = $state(false);
  let webWelcomeError = $state('');
  let showShortcutOverlay = $state(false);
  let gChordOpen = $state(false);
  let gChordTimer: ReturnType<typeof setTimeout> | null = null;
  let isOffline = $state(false);

  interface NavSection {
    title: string;
    items: NavItem[];
  }

  interface NavItem {
    path: string;
    label: string;
    icon: string;
    countKey?: keyof typeof $appStats;
    badge?: string;
  }

  const navSections: NavSection[] = [
    {
      title: '',
      items: [
        { path: '/', label: 'Overview', icon: 'M3 12l9-9 9 9M5 12v7a2 2 0 002 2h10a2 2 0 002-2v-7' },
        { path: '/dashboard', label: 'Dashboard', icon: 'M9 19v-6a2 2 0 00-2-2H5a2 2 0 00-2 2v6a2 2 0 002 2h2a2 2 0 002-2zm0 0V9a2 2 0 012-2h2a2 2 0 012 2v10m-6 0a2 2 0 002 2h2a2 2 0 002-2m0 0V5a2 2 0 012-2h2a2 2 0 012 2v14a2 2 0 01-2 2h-2a2 2 0 01-2-2z' },
      ]
    },
    {
      title: 'TREE',
      items: [
        { path: '/people', label: 'People', icon: 'M17 21v-2a4 4 0 00-4-4H5a4 4 0 00-4 4v2M9 11a4 4 0 100-8 4 4 0 000 8zM23 21v-2a4 4 0 00-3-3.87M16 3.13a4 4 0 010 7.75', countKey: 'personCount' },
        { path: '/families', label: 'Families', icon: 'M12 4.354a4 4 0 110 5.292M15 21H3v-1a6 6 0 0112 0v1zm0 0h6v-1a6 6 0 00-9-5.197', countKey: 'familyCount' },
        { path: '/pedigree', label: 'Pedigree', icon: 'M4 5h16M4 9h12M4 13h16M4 17h8' },
        { path: '/fan-chart', label: 'Fan Chart', icon: 'M3 20a9 9 0 1118 0M6 20a6 6 0 1112 0M9 20a3 3 0 116 0' },
        { path: '/descendants', label: 'Descendants', icon: 'M12 5v14m0 0l-4-4m4 4l4-4' },
        { path: '/relationship', label: 'Relationships', icon: 'M7 7h10M7 12h10M7 17h10' },
        { path: '/reports', label: 'Reports', icon: 'M9 12h6m-6 4h6m-7-8h8m-9 13h10a2 2 0 002-2V5a2 2 0 00-2-2H7a2 2 0 00-2 2v14a2 2 0 002 2z' },
        { path: '/timeline', label: 'Timeline', icon: 'M12 8v4l3 3m6-3a9 9 0 11-18 0 9 9 0 0118 0z' },
        { path: '/places', label: 'Places', icon: 'M17.657 16.657L13.414 20.9a2 2 0 01-2.827 0l-4.244-4.243a8 8 0 1111.314 0z M15 11a3 3 0 11-6 0 3 3 0 016 0z', countKey: 'placeCount' },
        { path: '/map', label: 'Map', icon: 'M9 20l-5.447-2.724A1 1 0 013 16.382V5.618a1 1 0 011.447-.894L9 7m0 13l6-3m-6 3V7m6 10l5.447 2.724A1 1 0 0021 18.382V7.618a1 1 0 00-.553-.894L15 4m0 13V4m0 0L9 7' },
        { path: '/sources', label: 'Sources', icon: 'M12 6.253v13m0-13C10.832 5.477 9.246 5 7.5 5S4.168 5.477 3 6.253v13C4.168 18.477 5.754 18 7.5 18s3.332.477 4.5 1.253m0-13C13.168 5.477 14.754 5 16.5 5c1.747 0 3.332.477 4.5 1.253v13C19.832 18.477 18.247 18 16.5 18c-1.746 0-3.332.477-4.5 1.253', countKey: 'sourceCount' },
        { path: '/media', label: 'Media', icon: 'M4 16l4.586-4.586a2 2 0 012.828 0L16 16m-2-2l1.586-1.586a2 2 0 012.828 0L20 14m-6-6h.01M6 20h12a2 2 0 002-2V6a2 2 0 00-2-2H6a2 2 0 00-2 2v12a2 2 0 002 2z', countKey: 'mediaCount' },
      ]
    },
    {
      title: 'RESEARCH',
      items: [
        { path: '/search', label: 'Search', icon: 'M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z' },
        { path: '/tasks', label: 'Tasks', icon: 'M9 5H7a2 2 0 00-2 2v12a2 2 0 002 2h10a2 2 0 002-2V7a2 2 0 00-2-2h-2M9 5a2 2 0 002 2h2a2 2 0 002-2M9 5a2 2 0 012-2h2a2 2 0 012 2m-6 9l2 2 4-4' },
        { path: '/notes', label: 'Notes', icon: 'M11 5H6a2 2 0 00-2 2v11a2 2 0 002 2h11a2 2 0 002-2v-5m-1.414-9.414a2 2 0 112.828 2.828L11.828 15H9v-2.828l8.586-8.586z' },
        { path: '/bookmarks', label: 'Bookmarks', icon: 'M5 5a2 2 0 012-2h10a2 2 0 012 2v16l-7-3.5L5 21V5z' },
        { path: '/research-log', label: 'Research Log', icon: 'M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z', countKey: 'researchLogCount' },
        { path: '/evidence', label: 'Evidence', icon: 'M9 12h6m-6 4h6m-6-8h6M7 4h10a2 2 0 012 2v12a2 2 0 01-2 2H7a2 2 0 01-2-2V6a2 2 0 012-2z' },
        { path: '/dna-tools', label: 'DNA Tools', icon: 'M19.428 15.428a2 2 0 00-1.022-.547l-2.387-.477a6 6 0 00-3.86.517l-.318.158a6 6 0 01-3.86.517L6.05 15.21a2 2 0 00-1.806.547M8 4h8l-1 1v5.172a2 2 0 00.586 1.414l5 5c1.26 1.26.367 3.414-1.415 3.414H4.828c-1.782 0-2.674-2.154-1.414-3.414l5-5A2 2 0 009 10.172V5L8 4z' },
      ]
    },
    {
      title: 'DATA QUALITY',
      items: [
        { path: '/issues', label: 'Issues', icon: 'M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z' },
        { path: '/contradictions', label: 'Contradictions', icon: 'M18.364 18.364A9 9 0 005.636 5.636m12.728 12.728A9 9 0 015.636 5.636m12.728 12.728L5.636 5.636' },
        { path: '/groups', label: 'Groups', icon: 'M19 11H5m14 0a2 2 0 012 2v6a2 2 0 01-2 2H5a2 2 0 01-2-2v-6a2 2 0 012-2m14 0V9a2 2 0 00-2-2M5 11V9a2 2 0 012-2m0 0V5a2 2 0 012-2h6a2 2 0 012 2v2M7 7h10', countKey: 'groupCount' },
      ]
    },
    {
      title: 'AI',
      items: [
        { path: '/proposals', label: 'Proposals', icon: 'M9 5H7a2 2 0 00-2 2v12a2 2 0 002 2h10a2 2 0 002-2V7a2 2 0 00-2-2h-2M9 5a2 2 0 002 2h2a2 2 0 002-2M9 5a2 2 0 012-2h2a2 2 0 012 2m-3 7h3m-3 4h3m-6-4h.01M9 16h.01' },
        { path: '/ai-chat', label: 'AI Chat', icon: 'M8 12h.01M12 12h.01M16 12h.01M21 12c0 4.418-4.03 8-9 8a9.863 9.863 0 01-4.255-.949L3 20l1.395-3.72C3.512 15.042 3 13.574 3 12c0-4.418 4.03-8 9-8s9 3.582 9 8z' },
        { path: '/stories', label: 'Stories', icon: 'M12 6.253v13m0-13C10.832 5.477 9.246 5 7.5 5S4.168 5.477 3 6.253v13C4.168 18.477 5.754 18 7.5 18s3.332.477 4.5 1.253m0-13C13.168 5.477 14.754 5 16.5 5c1.747 0 3.332.477 4.5 1.253v13C19.832 18.477 18.247 18 16.5 18c-1.746 0-3.332.477-4.5 1.253' },
        { path: '/ai-settings', label: 'AI Settings', icon: 'M10.325 4.317c.426-1.756 2.924-1.756 3.35 0a1.724 1.724 0 002.573 1.066c1.543-.94 3.31.826 2.37 2.37a1.724 1.724 0 001.066 2.573c1.756.426 1.756 2.924 0 3.35a1.724 1.724 0 00-1.066 2.573c.94 1.543-.826 3.31-2.37 2.37a1.724 1.724 0 00-2.573 1.066c-.426 1.756-2.924 1.756-3.35 0a1.724 1.724 0 00-2.573-1.066c-1.543.94-3.31-.826-2.37-2.37a1.724 1.724 0 00-1.066-2.573c-1.756-.426-1.756-2.924 0-3.35a1.724 1.724 0 001.066-2.573c-.94-1.543.826-3.31 2.37-2.37.996.608 2.296.07 2.572-1.065z M15 12a3 3 0 11-6 0 3 3 0 016 0z' },
        { path: '/services', label: 'Services', icon: 'M13 10V3L4 14h7v7l9-11h-7z' },
      ]
    },
    {
      title: '',
      items: [
        { path: '/cleanup', label: 'Cleanup', icon: 'M19.428 15.428a2 2 0 00-1.022-.547l-2.387-.477a6 6 0 00-3.86.517l-.318.158a6 6 0 01-3.86.517L6.05 15.21a2 2 0 00-1.806.547M8 4h8l-1 1v5.172a2 2 0 00.586 1.414l5 5c1.26 1.26.367 3.414-1.415 3.414H4.828c-1.782 0-2.674-2.154-1.414-3.414l5-5A2 2 0 009 10.172V5L8 4z' },
        { path: '/settings', label: 'Settings', icon: 'M10.325 4.317c.426-1.756 2.924-1.756 3.35 0a1.724 1.724 0 002.573 1.066c1.543-.94 3.31.826 2.37 2.37a1.724 1.724 0 001.066 2.573c1.756.426 1.756 2.924 0 3.35a1.724 1.724 0 00-1.066 2.573c.94 1.543-.826 3.31-2.37 2.37a1.724 1.724 0 00-2.573 1.066c-.426 1.756-2.924 1.756-3.35 0a1.724 1.724 0 00-2.573-1.066c-1.543.94-3.31-.826-2.37-2.37a1.724 1.724 0 00-1.066-2.573c-1.756-.426-1.756-2.924 0-3.35a1.724 1.724 0 001.066-2.573c-.94-1.543.826-3.31 2.37-2.37.996.608 2.296.07 2.572-1.065z M15 12a3 3 0 11-6 0 3 3 0 016 0z' },
      ]
    },
  ];

  function getCount(item: NavItem): number | undefined {
    if (!item.countKey) return undefined;
    const val = ($appStats as any)[item.countKey];
    return typeof val === 'number' && val > 0 ? val : undefined;
  }

  function isActive(itemPath: string, currentPath: string): boolean {
    if (itemPath === '/') return currentPath === '/';
    return currentPath.startsWith(itemPath);
  }

  // Close sidebar on navigation (mobile)
  $effect(() => {
    $page.url.pathname;
    sidebarOpen = false;
  });

  function openNewPerson() {
    goto('/people');
  }

  function closeOverlays() {
    showShortcutOverlay = false;
    document.dispatchEvent(new CustomEvent('gedfix:close-overlays'));
  }

  function handleGlobalKeydown(e: KeyboardEvent) {
    if (shouldIgnoreShortcutTarget(e.target)) return;
    if (gChordOpen) {
      const key = e.key.toLowerCase();
      gChordOpen = false;
      if (gChordTimer) clearTimeout(gChordTimer);
      if (triggerShortcut(`g then ${key}`)) {
        e.preventDefault();
      }
      return;
    }

    if (e.key.toLowerCase() === 'g' && !e.metaKey && !e.ctrlKey && !e.shiftKey && !e.altKey) {
      gChordOpen = true;
      if (gChordTimer) clearTimeout(gChordTimer);
      gChordTimer = setTimeout(() => {
        gChordOpen = false;
      }, 1000);
      return;
    }

    if (e.key === 'Escape') {
      closeOverlays();
      return;
    }

    const combo = normalizeKeyCombo(e);
    if (triggerShortcut(combo)) {
      e.preventDefault();
      return;
    }
    if (!e.metaKey && !e.ctrlKey && !e.altKey && triggerShortcut(e.key.toLowerCase())) {
      e.preventDefault();
    }
  }

  function bindShortcuts() {
    clearShortcuts();
    registerShortcut('mod+k', () => goto('/search'), 'Search', 'Global');
    registerShortcut('mod+n', openNewPerson, 'New person', 'Global');
    registerShortcut('g then p', () => goto('/people'), 'Go to People', 'Navigation');
    registerShortcut('g then f', () => goto('/families'), 'Go to Families', 'Navigation');
    registerShortcut('g then d', () => goto('/dashboard'), 'Go to Dashboard', 'Navigation');
    registerShortcut('g then m', () => goto('/map'), 'Go to Map', 'Navigation');
    registerShortcut('g then i', () => goto('/issues'), 'Go to Issues', 'Navigation');
    registerShortcut('g then t', () => goto('/tasks'), 'Go to Tasks', 'Navigation');
    registerShortcut('g then s', () => goto('/settings'), 'Go to Settings', 'Navigation');
    registerShortcut('?', () => { showShortcutOverlay = !showShortcutOverlay; }, 'Show help', 'Global');
    registerShortcut('mod+z', async () => {
      const action = await undoManager.undo();
      if (action) announce(`Undone: ${action.description}`);
    }, 'Undo', 'Global');
    registerShortcut('mod+shift+z', async () => {
      const action = await undoManager.redo();
      if (action) announce(`Redone: ${action.description}`);
    }, 'Redo', 'Global');
  }

  $effect(() => {
    bindShortcuts();
    window.addEventListener('keydown', handleGlobalKeydown);
    return () => window.removeEventListener('keydown', handleGlobalKeydown);
  });

  $effect(() => {
    isOffline = typeof navigator !== 'undefined' ? !navigator.onLine : false;
    const online = () => { isOffline = false; };
    const offline = () => { isOffline = true; };
    window.addEventListener('online', online);
    window.addEventListener('offline', offline);
    return () => {
      window.removeEventListener('online', online);
      window.removeEventListener('offline', offline);
    };
  });

  async function autoImport() {
    try {
      const empty = await isDbEmpty();
      if (!empty) {
        const stats = await getStats();
        appStats.set(stats);
        showWebWelcome = false;
        return;
      }

      // On web, skip auto-import — user must import via Settings
      if (!isTauri()) {
        showWebWelcome = true;
        return;
      }

      $isImporting = true;
      $importMessage = 'Reading GEDCOM file...';

      const { readTextFile } = await import('@tauri-apps/plugin-fs');
      const { homeDir, join } = await import('@tauri-apps/api/path');

      const home = await homeDir();
      const primaryPath = await join(home, 'Documents', 'Family Tree Maker', 'Ancestry_2026-03-25.ged');
      const fallbackPath = await join(home, 'Documents', 'GedFix', 'mallinger_cleaned.ged');
      let filePath = primaryPath;

      let text: string;
      try {
        text = await readTextFile(filePath);
      } catch {
        try {
          filePath = fallbackPath;
          text = await readTextFile(filePath);
        } catch {
          $importMessage = 'GEDCOM file not found. Use Settings to import.';
          $isImporting = false;
          return;
        }
      }

      await createAutoBackup();
      await importGedcom(text, (pct, msg) => {
        $importProgress = pct;
        $importMessage = msg;
      });

      const stats = await getStats();
      appStats.set(stats);
      showWebWelcome = false;
      $isImporting = false;
      $importMessage = '';
    } catch (e) {
      console.error('Import error:', e);
      $importMessage = `Import error: ${e}`;
      $isImporting = false;
    }
  }

  async function importWebGedcom() {
    webWelcomeError = '';
    const result = await pickAndReadTextFile([{ name: 'GEDCOM', extensions: ['ged'] }]);
    if (!result) return;
    $isImporting = true;
    $importProgress = 0;
    $importMessage = 'Reading file...';
    try {
      await createAutoBackup();
      await importGedcom(result.text, (pct, msg) => {
        $importProgress = pct;
        $importMessage = msg;
      });
      const stats = await getStats();
      appStats.set(stats);
      showWebWelcome = false;
      $importMessage = '';
    } catch (e) {
      console.error('Import error:', e);
      webWelcomeError = `Import failed: ${e}`;
    } finally {
      $isImporting = false;
    }
  }

  async function createAutoBackup(): Promise<void> {
    try {
      const backup = await exportDbAsJson();
      const stamp = new Date().toISOString().replace(/[:.]/g, '-');
      if (isTauri()) {
        const { writeTextFile, readDir, remove } = await import('@tauri-apps/plugin-fs');
        const { appDataDir, join } = await import('@tauri-apps/api/path');
        const base = await appDataDir();
        const folder = await join(base, 'backups');
        const path = await join(folder, `backup-${stamp}.json`);
        await writeTextFile(path, JSON.stringify(backup));
        try {
          const files = await readDir(folder);
          const backups = files.filter((f) => f.name?.startsWith('backup-') && f.name?.endsWith('.json'));
          backups.sort((a, b) => (b.name || '').localeCompare(a.name || ''));
          for (const stale of backups.slice(5)) {
            if (stale.name) await remove(await join(folder, stale.name));
          }
        } catch {
          // Best effort retention cleanup.
        }
      } else {
        const key = `backup-${stamp}`;
        localStorage.setItem(key, JSON.stringify(backup));
        const keys = Object.keys(localStorage).filter((k) => k.startsWith('backup-')).sort().reverse();
        for (const stale of keys.slice(5)) localStorage.removeItem(stale);
      }
    } catch (e) {
      console.warn('Auto-backup failed:', e);
    }
  }

  let hasAutoImported = false;
  let hasLoadedTheme = false;

  $effect(() => {
    if (!hasAutoImported) {
      hasAutoImported = true;
      autoImport();
    }
  });

  $effect(() => {
    const path = $page.url.pathname;
    const label = navSections.flatMap((s) => s.items).find((item) => item.path === path)?.label ?? path;
    announce(`Navigated to ${label}`);
    requestAnimationFrame(() => {
      const heading = document.querySelector('main h1') as HTMLElement | null;
      heading?.focus?.();
    });
  });

  function applyTheme(mode: ThemeMode) {
    if (typeof document === 'undefined') return;
    document.documentElement.dataset.theme = mode;
  }

  async function loadThemePreference() {
    try {
      themeMode = 'dark';
      applyTheme('dark');
      await setSetting('theme_mode', 'dark');
    } catch (e) {
      console.error('Failed to load theme preference:', e);
      themeMode = 'dark';
      applyTheme('dark');
    }
  }

  async function toggleTheme() {
    themeMode = 'dark';
    applyTheme('dark');
    try {
      await setSetting('theme_mode', 'dark');
    } catch (e) {
      console.error('Failed to save theme preference:', e);
    }
  }

  $effect(() => {
    if (hasLoadedTheme) return;
    hasLoadedTheme = true;
    loadThemePreference();
  });
</script>

<a href="#main-content" class="skip-nav">Skip to content</a>
<div aria-live="polite" aria-atomic="true" class="sr-only" id="announcer"></div>
<div class="flex h-screen select-none" style="background: var(--color-charcoal);">
  <!-- Mobile hamburger -->
  <button
    class="mobile-hamburger fixed top-3 left-3 z-[60] p-2 rounded-lg"
    style="background: var(--color-charcoal); color: var(--color-white);"
    onclick={() => { sidebarOpen = !sidebarOpen; }}
    aria-label="Toggle navigation"
  >
    <svg class="w-6 h-6" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24">
      {#if sidebarOpen}
        <path stroke-linecap="round" stroke-linejoin="round" d="M6 18L18 6M6 6l12 12" />
      {:else}
        <path stroke-linecap="round" stroke-linejoin="round" d="M4 6h16M4 12h16M4 18h16" />
      {/if}
    </svg>
  </button>

  {#if sidebarOpen}
    <button
      type="button"
      class="sidebar-backdrop"
      aria-label="Close navigation"
      onclick={() => { sidebarOpen = false; }}
    ></button>
  {/if}

  <!-- Sidebar — dark archive panel -->
  <nav
    aria-label="Main navigation"
    class="flex flex-col pt-5 pb-3 px-2.5 shrink-0 overflow-y-auto animate-slide-in {sidebarOpen ? 'sidebar-mobile-overlay' : ''}"
    class:sidebar-mobile-hidden={!sidebarOpen}
    style="width: var(--sidebar-width); background: var(--color-charcoal);"
  >
    <!-- Masthead — click to go home -->
    <a href="/" class="block px-3 mb-5 no-underline transition-opacity hover:opacity-80" title="Go to Overview">
      <svg width="24" height="24" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg" class="mb-1.5" style="color: var(--color-blue);">
        <path d="M12 2L3 7v6c0 5.25 3.75 10.15 9 11.25C17.25 23.15 21 18.25 21 13V7L12 2z" stroke="currentColor" stroke-width="1.5" fill="none"/>
        <path d="M12 6l-4 3v4c0 2.5 1.8 4.8 4 5.3 2.2-.5 4-2.8 4-5.3V9L12 6z" stroke="currentColor" stroke-width="1" fill="currentColor" opacity="0.15"/>
        <path d="M12 8v8M8 12h8" stroke="currentColor" stroke-width="1.2" stroke-linecap="round"/>
      </svg>
      <h1
        class="text-[15px] tracking-[0.12em] uppercase"
        style="font-family: var(--font-serif); color: var(--color-white); font-weight: 600;"
      >GedFix</h1>
      <div class="text-[8px] tracking-[0.2em] uppercase mt-0.5" style="color: var(--color-muted); font-family: var(--font-mono);">Genealogical Research</div>
      <div class="mt-1.5" style="border-top: 2px double rgba(30, 159, 242, 0.15);"></div>
    </a>

    <div class="stagger-children">
      {#each navSections as section}
        {#if section.title}
          <div class="px-3 pt-4 pb-1">
            <span
              class="sidebar-section text-[9px] tracking-[0.14em] uppercase"
            >{section.title}</span>
            <div class="mt-1" style="border-top: 1px solid rgba(30, 159, 242, 0.15);"></div>
          </div>
        {/if}
        <div class="flex flex-col gap-px">
          {#each section.items as item}
            <a
              href={item.path}
              aria-current={isActive(item.path, $page.url.pathname) ? 'page' : undefined}
              class="nav-link flex items-center gap-2.5 px-3 py-[7px] rounded-lg text-[13px] transition-all
                     {isActive(item.path, $page.url.pathname) ? 'nav-active' : ''}"
              style="font-weight: 500;"
            >
              <svg class="w-[15px] h-[15px] shrink-0 opacity-60" fill="none" stroke="currentColor" stroke-width="1.5" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" d={item.icon} />
              </svg>
              <span class="flex-1 truncate">{item.label}</span>
              {#if getCount(item) !== undefined}
                <span class="nav-count text-[9px] tabular-nums px-1.5 py-0.5 rounded">{getCount(item)}</span>
              {/if}
            </a>
          {/each}
        </div>
      {/each}
    </div>

    <!-- Footer catalog notation -->
    <div class="mt-auto px-3 pt-3" style="border-top: 2px double rgba(30, 159, 242, 0.15);">
      <button
        type="button"
        class="w-full flex items-center justify-between gap-2 px-2.5 py-2 rounded-md text-left sidebar-theme-toggle"
        style="color: var(--color-muted); font-family: var(--font-mono);"
        onclick={toggleTheme}
        aria-label="Toggle dark mode"
      >
        <span class="text-[11px] tracking-[0.08em] uppercase">Theme</span>
        <span class="text-[10px] px-1.5 py-0.5 rounded nav-count">Dark</span>
      </button>
      <div
        class="text-[10px]"
        style="color: var(--color-muted); font-family: var(--font-mono);"
      >
        {$appStats.personCount > 0 ? `${$appStats.personCount.toLocaleString()} records` : 'No data loaded'}
      </div>
      <div class="text-[8px] mt-1" style="color: var(--color-muted); font-family: var(--font-mono); letter-spacing: 0.1em;">CAT. {new Date().getFullYear()}</div>
    </div>
  </nav>

  <!-- Content area -->
  <main id="main-content" class="flex-1 overflow-auto app-main" tabindex="-1">
    {#if isOffline}
      <div class="offline-banner">You're offline — changes are saved locally</div>
    {/if}
    {#if $isImporting}
      <div class="flex items-center justify-center h-full">
        <div class="text-center animate-fade-in" style="max-width: 360px;">
          <div
            class="text-[11px] uppercase tracking-[0.1em] mb-4"
            style="font-family: var(--font-serif); color: var(--ink-muted); font-weight: 600; letter-spacing: 0.12em;"
          >Importing Records</div>
          <div class="mb-4" style="border-top: 2px double var(--ink-faint);"></div>
          <div class="h-1 rounded-full overflow-hidden mb-3" style="background: var(--parchment);" role="progressbar" aria-valuenow={$importProgress} aria-valuemin={0} aria-valuemax={100}>
            <div
              class="h-full rounded-full transition-all"
              style="width: {$importProgress}%; background: var(--accent); transition-duration: var(--duration-normal);"
            ></div>
          </div>
          <p class="text-sm" style="color: var(--ink-muted); font-family: var(--font-sans);">{$importMessage}</p>
        </div>
      </div>
    {:else if showWebWelcome}
      <div class="flex items-center justify-center h-full p-6">
        <div class="arch-card rounded-xl p-8 w-full max-w-xl text-center animate-fade-in">
          <h2 class="dossier-header" style="font-size: 1.6rem; margin-bottom: 0.75rem;">Welcome to GedFix</h2>
          <p class="text-sm mb-6" style="color: var(--ink-muted); font-family: var(--font-sans);">
            Import a GEDCOM file to get started.
          </p>
          <button class="btn-accent px-5 py-2.5" onclick={importWebGedcom}>
            Import GEDCOM
          </button>
          {#if webWelcomeError}
            <p class="text-xs mt-3" style="color: var(--color-error);">{webWelcomeError}</p>
          {/if}
        </div>
      </div>
    {:else}
      {@render children()}
    {/if}
  </main>
</div>

{#if showShortcutOverlay}
  <button
    type="button"
    class="shortcut-backdrop"
    aria-label="Close shortcuts"
    onclick={() => { showShortcutOverlay = false; }}
  ></button>
  <div class="shortcut-overlay" role="dialog" aria-modal="true" aria-labelledby="shortcut-title">
    <div class="shortcut-header">
      <h2 id="shortcut-title">Keyboard Shortcuts</h2>
      <button type="button" class="btn-secondary px-3 py-1" onclick={() => { showShortcutOverlay = false; }}>Close</button>
    </div>
    <div class="shortcut-grid">
      {#each ['Global', 'Navigation', 'Person Detail'] as cat}
        <div class="shortcut-col">
          <h3>{cat}</h3>
          {#each getShortcuts().filter((s) => s.category === cat) as def}
            <div class="shortcut-row">
              <kbd>{def.combo}</kbd>
              <span>{def.description}</span>
            </div>
          {/each}
        </div>
      {/each}
    </div>
  </div>
{/if}

<style>
  .skip-nav {
    position: absolute;
    left: -9999px;
    top: 0;
    z-index: 9999;
    background: var(--paper);
    color: var(--ink);
    padding: 0.5rem 0.75rem;
    border-radius: 0.4rem;
  }

  .skip-nav:focus {
    left: 0.75rem;
    top: 0.75rem;
  }

  .sr-only {
    position: absolute;
    width: 1px;
    height: 1px;
    padding: 0;
    margin: -1px;
    overflow: hidden;
    clip: rect(0, 0, 0, 0);
    border: 0;
  }

  .offline-banner {
    min-height: 32px;
    background: #f0c66a;
    color: #1f1f1f;
    text-align: center;
    display: flex;
    align-items: center;
    justify-content: center;
    font-size: 0.85rem;
    font-weight: 600;
  }

  .shortcut-backdrop {
    position: fixed;
    inset: 0;
    z-index: 80;
    background: rgba(0, 0, 0, 0.4);
    border: 0;
  }

  .shortcut-overlay {
    position: fixed;
    top: 50%;
    left: 50%;
    transform: translate(-50%, -50%);
    z-index: 81;
    width: min(900px, 92vw);
    max-height: 80vh;
    overflow: auto;
    padding: 1rem;
    border-radius: 0.8rem;
    background: var(--vellum);
    border: 1px solid var(--border-rule);
  }

  .shortcut-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 0.75rem;
  }

  .shortcut-grid {
    display: grid;
    grid-template-columns: repeat(3, minmax(0, 1fr));
    gap: 0.75rem;
  }

  .shortcut-col h3 {
    margin: 0 0 0.4rem;
    font-size: 0.8rem;
    color: var(--ink-muted);
    text-transform: uppercase;
  }

  .shortcut-row {
    display: flex;
    justify-content: space-between;
    gap: 0.5rem;
    align-items: center;
    padding: 0.35rem 0;
    border-bottom: 1px solid var(--border-subtle);
    color: var(--ink-light);
    font-size: 0.85rem;
  }

  .shortcut-row kbd {
    border: 1px solid var(--border-rule);
    border-radius: 0.35rem;
    padding: 0.1rem 0.35rem;
    background: var(--paper);
    color: var(--ink);
    font-size: 0.72rem;
  }
  .nav-link {
    font-family: var(--font-mono);
    color: var(--color-muted);
    padding-left: 12px;
  }

  .nav-link:hover {
    background: var(--color-steel);
    color: var(--color-white);
  }

  .nav-link:hover svg {
    opacity: 1;
  }

  .nav-active {
    color: var(--color-blue);
    border-left: 3px solid var(--color-blue);
    background: rgba(30, 159, 242, 0.12);
    padding-left: 9px;
  }

  .nav-count {
    background: var(--color-steel);
    color: var(--color-muted);
    font-family: var(--font-mono);
  }

  .sidebar-section {
    border-left: 2px solid var(--color-blue);
    padding-left: 6px;
    color: var(--color-white);
    font-family: var(--font-serif);
    font-weight: 400;
    font-variant: small-caps;
    letter-spacing: 0.14em;
  }

  .mobile-hamburger {
    display: none;
  }

  .sidebar-backdrop {
    display: none;
  }

  @media (max-width: 767px) {
    .mobile-hamburger {
      display: inline-flex;
      align-items: center;
      justify-content: center;
      min-width: 44px;
      min-height: 44px;
    }

    nav {
      position: fixed;
      top: 0;
      left: 0;
      bottom: 0;
      z-index: 55;
      width: min(84vw, 300px) !important;
      transform: translateX(0);
      transition: transform 180ms ease;
    }

    .sidebar-mobile-hidden {
      transform: translateX(-110%);
    }

    .sidebar-mobile-overlay {
      box-shadow: 8px 0 28px rgba(0, 0, 0, 0.45);
    }

    .sidebar-backdrop {
      display: block;
      position: fixed;
      inset: 0;
      z-index: 50;
      background: rgba(0, 0, 0, 0.35);
      border: 0;
      padding: 0;
      margin: 0;
    }

    .app-main {
      width: 100%;
      padding-top: 3.5rem;
    }

    .nav-link {
      min-height: 44px;
    }
  }
</style>
