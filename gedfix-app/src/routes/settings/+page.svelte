<script lang="ts">
  import { clearAll, getStats, isDbEmpty } from '$lib/db';
  import { importGedcom } from '$lib/gedcom-parser';
  import { appStats, isImporting, importProgress, importMessage } from '$lib/stores';
  import { open } from '@tauri-apps/plugin-dialog';
  import { readTextFile } from '@tauri-apps/plugin-fs';

  let livingThreshold = $state(110);
  let confirmClear = $state(false);

  async function importFile() {
    const path = await open({
      multiple: false,
      filters: [{ name: 'GEDCOM', extensions: ['ged'] }],
    });
    if (!path) return;

    $isImporting = true;
    $importProgress = 0;
    $importMessage = 'Reading file...';

    try {
      const text = await readTextFile(path as string);
      await importGedcom(text, (pct, msg) => {
        $importProgress = pct;
        $importMessage = msg;
      });
      const stats = await getStats();
      appStats.set(stats);
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
    confirmClear = false;
  }

  interface ApiKeyConfig {
    name: string;
    key: string;
    placeholder: string;
  }

  let apiKeys = $state<ApiKeyConfig[]>([
    { name: 'OpenAI', key: '', placeholder: 'sk-...' },
    { name: 'Anthropic', key: '', placeholder: 'sk-ant-...' },
    { name: 'Google AI', key: '', placeholder: 'AIza...' },
    { name: 'FamilySearch', key: '', placeholder: 'API key' },
    { name: 'FindMyPast', key: '', placeholder: 'API key' },
    { name: 'MyHeritage', key: '', placeholder: 'API key' },
    { name: 'Ancestry', key: '', placeholder: 'API key' },
  ]);
</script>

<div class="p-8 max-w-2xl animate-fade-in">
  <div class="mb-8">
    <h1 class="text-2xl font-bold tracking-tight" style="font-family: var(--font-serif); color: var(--ink);">Settings</h1>
    <p class="text-sm text-ink-muted mt-1">Configure your GedFix application</p>
  </div>

  <!-- Data Management -->
  <section class="mb-8">
    <h2 class="arch-section-header">Data Management</h2>
    <div class="arch-card divide-y arch-card-divide">
      <div class="flex items-center justify-between px-5 py-4">
        <div>
          <div class="text-sm font-medium text-ink">Import GEDCOM</div>
          <div class="text-xs text-ink-muted">Load a GEDCOM file into the database</div>
        </div>
        <button
          onclick={importFile}
          disabled={$isImporting}
          class="px-4 py-2 text-sm btn-accent disabled:opacity-50 transition-colors"
        >
          {$isImporting ? 'Importing...' : 'Choose File'}
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
          <div class="text-sm font-medium text-ink">Clear Database</div>
          <div class="text-xs text-ink-muted">Remove all imported data</div>
        </div>
        {#if confirmClear}
          <div class="flex gap-2">
            <button onclick={clearDatabase} class="px-3 py-1.5 bg-red-500 text-white text-xs font-medium rounded-lg hover:bg-red-600 transition-colors">Confirm</button>
            <button onclick={() => { confirmClear = false; }} class="px-3 py-1.5 text-xs font-medium btn-secondary transition-colors">Cancel</button>
          </div>
        {:else}
          <button onclick={() => { confirmClear = true; }} class="px-4 py-2 text-red-600 text-sm font-medium rounded-lg hover:bg-red-50 transition-colors" style="background: var(--parchment);">Clear</button>
        {/if}
      </div>

      <div class="flex items-center justify-between px-5 py-4">
        <div>
          <div class="text-sm font-medium text-ink">Database Stats</div>
          <div class="text-xs text-ink-muted">Current data summary</div>
        </div>
        <div class="text-xs text-ink-muted text-right">
          <div>{$appStats.personCount} people, {$appStats.familyCount} families</div>
          <div>{$appStats.eventCount} events, {$appStats.sourceCount} sources</div>
        </div>
      </div>
    </div>
  </section>

  <!-- Display Preferences -->
  <section class="mb-8">
    <h2 class="arch-section-header">Display Preferences</h2>
    <div class="arch-card divide-y arch-card-divide">
      <div class="flex items-center justify-between px-5 py-4">
        <div>
          <div class="text-sm font-medium text-ink">Living Person Threshold</div>
          <div class="text-xs text-ink-muted">Years from birth to assume deceased (no death record)</div>
        </div>
        <div class="flex items-center gap-2">
          <input
            type="number"
            bind:value={livingThreshold}
            min="80"
            max="130"
            class="w-16 px-2 py-1.5 text-sm bg-gray-50 rounded-lg border border-gray-200 outline-none text-center"
          />
          <span class="text-xs text-ink-faint">years</span>
        </div>
      </div>
    </div>
  </section>

  <!-- API Keys -->
  <section class="mb-8">
    <h2 class="arch-section-header">AI & Service API Keys</h2>
    <div class="arch-card divide-y arch-card-divide">
      {#each apiKeys as config, i}
        <div class="flex items-center justify-between px-5 py-3.5">
          <span class="text-sm font-medium text-ink w-28">{config.name}</span>
          <input
            type="password"
            bind:value={apiKeys[i].key}
            placeholder={config.placeholder}
            class="flex-1 ml-4 px-3 py-1.5 text-sm bg-gray-50 rounded-lg border border-gray-200 outline-none placeholder:text-gray-300 focus:border-amber-600/40 transition-colors"
          />
        </div>
      {/each}
    </div>
    <p class="text-xs text-ink-faint mt-2 px-1">API keys are stored locally and never transmitted.</p>
  </section>

  <!-- About -->
  <section>
    <h2 class="arch-section-header">About</h2>
    <div class="arch-card px-5 py-4">
      <div class="text-sm font-medium text-ink">GedFix</div>
      <div class="text-xs text-ink-muted mt-0.5">Version 1.0.0</div>
      <div class="text-xs text-ink-faint mt-2">A premium genealogy application for managing and analyzing GEDCOM family tree data.</div>
    </div>
  </section>
</div>
