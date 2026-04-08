<script lang="ts">
  import { t } from '$lib/i18n';
  import { getDb } from '$lib/db';

  const providers = [
    { id: 'anthropic', name: 'Anthropic (Claude)', desc: 'Best for genealogy research. Excellent reasoning and document analysis.', models: ['claude-sonnet-4-20250514', 'claude-haiku-4-5-20251001', 'claude-opus-4-6'], capabilities: ['Document transcription', 'Source evaluation', 'Proof portfolio generation', 'Contradiction detection', 'Multi-language records'], authType: 'api-key' },
    { id: 'openai', name: 'OpenAI', desc: 'Strong at structured data extraction and image analysis.', models: ['gpt-4o', 'gpt-4o-mini', 'gpt-4.1', 'gpt-4.1-mini', 'gpt-4.1-nano', 'o3-mini', 'o4-mini'], capabilities: ['Census image extraction', 'Record parsing', 'Name variants', 'Photo analysis', 'Vision/OCR'], authType: 'api-key' },
    { id: 'xai', name: 'xAI (Grok)', desc: 'Grok models from xAI. Fast reasoning with real-time knowledge.', models: ['grok-3', 'grok-3-mini', 'grok-2'], capabilities: ['Real-time knowledge', 'Fast reasoning', 'Unbiased analysis', 'Historical research'], authType: 'api-key' },
    { id: 'gemini', name: 'Google Gemini', desc: 'Largest context window. Best for analyzing entire trees at once.', models: ['gemini-2.5-pro', 'gemini-2.5-flash'], capabilities: ['Analyze full GEDCOM files', '1M token context', 'Multi-language translation', 'Cross-reference documents'], authType: 'api-key' },
    { id: 'openrouter', name: 'OpenRouter', desc: 'Access to 200+ models through one API key.', models: ['anthropic/claude-sonnet-4', 'openai/gpt-4o', 'google/gemini-2.5-flash', 'meta-llama/llama-4-maverick', 'deepseek/deepseek-chat-v3-0324', 'qwen/qwen-3-235b-a22b', 'x-ai/grok-3'], capabilities: ['200+ models one key', 'Cost optimization', 'Fallback provider', 'Model comparison'], authType: 'api-key' },
    { id: 'groq', name: 'Groq', desc: 'Fastest AI inference. Ultra-low latency for instant responses.', models: ['llama-3.3-70b-versatile', 'llama-3.1-8b-instant', 'mixtral-8x7b-32768', 'gemma2-9b-it', 'llama-guard-3-8b'], capabilities: ['Fastest inference available', 'Instant name suggestions', 'Quick date parsing', 'Free tier available'], authType: 'api-key' },
    { id: 'together', name: 'Together AI', desc: 'Open source models including DeepSeek. Good speed and pricing.', models: ['meta-llama/Llama-3.3-70B-Instruct-Turbo', 'deepseek-ai/DeepSeek-V3', 'deepseek-ai/DeepSeek-R1', 'Qwen/Qwen2.5-72B-Instruct-Turbo', 'google/gemma-2-27b-it'], capabilities: ['DeepSeek V3 & R1', 'Open source models', 'Batch processing', 'Name normalization'], authType: 'api-key' },
  ];

  let apiKeys = $state<Record<string, string>>({});
  let expanded = $state<string | null>(null);
  let showKeys = $state<Record<string, boolean>>({});
  let saveStatus = $state<Record<string, string>>({});
  let activeProvider = $state('anthropic');
  let activeModel = $state<Record<string, string>>({});

  // Load saved keys from DB
  async function loadKeys() {
    try {
      const db = await getDb();
      for (const p of providers) {
        const rows = await db.select<{ value: string }[]>(`SELECT value FROM settings WHERE key = $1`, [`ai_key_${p.id}`]);
        if (rows.length > 0 && rows[0].value) apiKeys[p.id] = rows[0].value;

        const modelRows = await db.select<{ value: string }[]>(`SELECT value FROM settings WHERE key = $1`, [`ai_model_${p.id}`]);
        if (modelRows.length > 0 && modelRows[0].value) activeModel[p.id] = modelRows[0].value;
      }
      const activeRows = await db.select<{ value: string }[]>(`SELECT value FROM settings WHERE key = 'ai_active_provider'`);
      if (activeRows.length > 0) activeProvider = activeRows[0].value;
    } catch (e) {
      console.error('Failed to load AI keys:', e);
    }
  }

  async function saveKey(providerId: string) {
    try {
      const db = await getDb();
      await db.execute(`INSERT OR REPLACE INTO settings (key, value) VALUES ($1, $2)`, [`ai_key_${providerId}`, apiKeys[providerId] ?? '']);
      saveStatus[providerId] = 'Saved!';
      setTimeout(() => { saveStatus[providerId] = ''; }, 2000);
    } catch (e) {
      saveStatus[providerId] = 'Error saving';
    }
  }

  async function saveModel(providerId: string, model: string) {
    activeModel[providerId] = model;
    const db = await getDb();
    await db.execute(`INSERT OR REPLACE INTO settings (key, value) VALUES ($1, $2)`, [`ai_model_${providerId}`, model]);
  }

  async function setActiveProvider(id: string) {
    activeProvider = id;
    const db = await getDb();
    await db.execute(`INSERT OR REPLACE INTO settings (key, value) VALUES ('ai_active_provider', $1)`, [id]);
  }

  async function testConnection(providerId: string) {
    saveStatus[providerId] = 'Testing...';
    const key = apiKeys[providerId];
    if (!key) { saveStatus[providerId] = 'No key'; return; }

    try {
      const provider = providers.find(p => p.id === providerId)!;
      let url = '';
      let headers: Record<string, string> = { 'Content-Type': 'application/json' };
      let body = '';

      if (providerId === 'anthropic') {
        url = 'https://api.anthropic.com/v1/messages';
        headers['x-api-key'] = key;
        headers['anthropic-version'] = '2023-06-01';
        body = JSON.stringify({ model: 'claude-haiku-4-5-20251001', max_tokens: 10, messages: [{ role: 'user', content: 'Hi' }] });
      } else if (providerId === 'gemini') {
        const model = activeModel[providerId] || 'gemini-2.5-flash';
        url = `https://generativelanguage.googleapis.com/v1beta/models/${model}:generateContent?key=${key}`;
        body = JSON.stringify({ contents: [{ parts: [{ text: 'Hi' }] }], generationConfig: { maxOutputTokens: 10 } });
      } else {
        // OpenAI-compatible (openai, xai, groq, together, openrouter)
        const endpoints: Record<string, string> = {
          openai: 'https://api.openai.com/v1/chat/completions',
          xai: 'https://api.x.ai/v1/chat/completions',
          groq: 'https://api.groq.com/openai/v1/chat/completions',
          together: 'https://api.together.xyz/v1/chat/completions',
          openrouter: 'https://openrouter.ai/api/v1/chat/completions',
        };
        url = endpoints[providerId];
        headers['Authorization'] = `Bearer ${key}`;
        body = JSON.stringify({ model: provider.models[0], messages: [{ role: 'user', content: 'Hi' }], max_tokens: 10 });
      }

      const resp = await fetch(url, { method: 'POST', headers, body });
      saveStatus[providerId] = resp.ok ? 'Connected!' : `Error ${resp.status}`;
    } catch (e: any) {
      saveStatus[providerId] = `Failed: ${e.message?.slice(0, 30)}`;
    }
    setTimeout(() => { saveStatus[providerId] = ''; }, 4000);
  }

  // Create settings table if not exists
  async function ensureSettingsTable() {
    const db = await getDb();
    await db.execute(`CREATE TABLE IF NOT EXISTS settings (key TEXT PRIMARY KEY, value TEXT DEFAULT '')`);
  }

  $effect(() => {
    ensureSettingsTable().then(() => loadKeys());
  });
</script>

<div class="p-8 max-w-4xl animate-fade-in overflow-y-auto h-full">
  <h1 class="text-2xl font-bold tracking-tight" style="font-family: var(--font-serif); color: var(--ink);">{t('ai.settingsTitle')}</h1>
  <p class="text-sm text-ink-muted mt-1 mb-2">Configure AI providers — keys are saved locally to your database</p>
  <div class="flex items-start gap-2 p-3 mb-4 rounded-lg text-xs" style="background: #fef9c3; border: 1px solid #fde047; color: #713f12;">
    <span style="font-size: 14px; line-height: 1;">&#128274;</span>
    <div>
      <strong>Local storage only.</strong> API keys are stored in your GedFix database file at <code style="font-size: 10px;">~/Library/Application Support/com.gedfix.app/</code> — protected by macOS file permissions (your user only). Keys are never sent anywhere except directly to the AI provider when you use a feature. Do not sync your GedFix database to iCloud or a shared drive with API keys saved.
    </div>
  </div>

  <!-- Active provider -->
  <div class="arch-card p-4 mb-6">
    <div class="text-xs font-semibold text-ink-faint uppercase tracking-wider mb-2">Active Provider</div>
    <div class="flex flex-wrap gap-2">
      {#each providers as p}
        <button
          onclick={() => setActiveProvider(p.id)}
          class="px-3 py-1.5 text-xs font-medium rounded-lg transition-all
                 {activeProvider === p.id ? 'btn-filter-active' : apiKeys[p.id] ? 'bg-green-50 text-green-700 border border-green-200' : 'btn-filter'}"
        >
          {p.name}
          {#if apiKeys[p.id]}
            <span class="ml-1 text-[9px]">&#10003;</span>
          {/if}
        </button>
      {/each}
    </div>
  </div>

  <!-- Provider cards -->
  <div class="space-y-3">
    {#each providers as provider}
      <div class="arch-card overflow-hidden">
        <button onclick={() => expanded = expanded === provider.id ? null : provider.id} class="w-full text-left p-4 flex items-center justify-between">
          <div class="flex-1">
            <div class="flex items-center gap-2">
              <span class="font-medium text-sm">{provider.name}</span>
              {#if apiKeys[provider.id]}
                <span class="px-2 py-0.5 text-[10px] rounded-full bg-green-100 text-green-700 font-semibold">Key saved</span>
              {:else}
                <span class="px-2 py-0.5 text-[10px] rounded-full text-ink-muted" style="background: var(--parchment);">No key</span>
              {/if}
              {#if saveStatus[provider.id]}
                <span class="text-[10px] text-blue-600 font-medium">{saveStatus[provider.id]}</span>
              {/if}
            </div>
            <div class="text-xs text-ink-muted mt-0.5">{provider.desc}</div>
          </div>
          <span class="text-ink-faint text-xs">{expanded === provider.id ? '\u25B2' : '\u25BC'}</span>
        </button>

        {#if expanded === provider.id}
          <div class="px-4 pb-4 border-t border-gray-50 pt-4 space-y-3">
            <!-- API Key input -->
            <div class="flex gap-2">
              <input
                type={showKeys[provider.id] ? 'text' : 'password'}
                bind:value={apiKeys[provider.id]}
                placeholder={t('ai.pasteApiKey')}
                class="flex-1 px-3 py-2 text-sm border border-gray-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500/20 font-mono"
               aria-label={t('ai.pasteApiKey')} />
              <button onclick={() => showKeys[provider.id] = !showKeys[provider.id]} class="px-3 py-2 text-xs text-ink-light btn-secondary">
                {showKeys[provider.id] ? t('common.hide') : t('common.show')}
              </button>
            </div>
            <div class="flex gap-2">
              <button onclick={() => saveKey(provider.id)} class="px-4 py-2 text-xs font-medium btn-accent">
                Save Key
              </button>
              <button onclick={() => testConnection(provider.id)} class="px-4 py-2 text-xs font-medium btn-secondary" disabled={!apiKeys[provider.id]}>
                Test Connection
              </button>
            </div>

            <!-- Model selector -->
            <div>
              <span class="text-xs font-medium text-ink-light">Model:</span>
              <div class="flex flex-wrap gap-1.5 mt-1">
                {#each provider.models as model}
                  <button
                    onclick={() => saveModel(provider.id, model)}
                    class="px-2.5 py-1 text-[10px] rounded-lg transition-all
                           {(activeModel[provider.id] || provider.models[0]) === model ? 'btn-filter-active' : 'btn-filter'}"
                  >
                    {model}
                  </button>
                {/each}
              </div>
            </div>

            <!-- Capabilities -->
            <div>
              <span class="text-xs font-medium text-ink-light">Capabilities:</span>
              <div class="mt-1 space-y-0.5">
                {#each provider.capabilities as cap}
                  <div class="flex items-center gap-1.5">
                    <span class="text-green-500 text-xs">&#10003;</span>
                    <span class="text-xs text-ink-light">{cap}</span>
                  </div>
                {/each}
              </div>
            </div>
          </div>
        {/if}
      </div>
    {/each}
  </div>
</div>
