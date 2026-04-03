<script lang="ts">
  import { t } from '$lib/i18n';
  import { getDb, getPersons, getEvents, getStories, insertStory, deleteStory, getParents, getChildren, getSpouseFamilies } from '$lib/db';
  import type { Person, GedcomEvent, GeneratedStory } from '$lib/types';

  // --- State ---
  let storyMode = $state<'single' | 'family'>('single');
  let view = $state<'generate' | 'library'>('generate');

  // Person selection
  let personSearch = $state('');
  let personResults = $state<Person[]>([]);
  let selectedPeople = $state<Person[]>([]);
  let showDropdown = $state(false);

  // Provider
  let provider = $state('groq');
  let model = $state('');
  let availableProviders = $state<{ id: string; name: string; model: string }[]>([]);

  // Generation
  let isGenerating = $state(false);
  let batchProgress = $state(0);
  let batchTotal = $state(0);
  let currentPerson = $state('');
  let generatedPreview = $state('');
  let error = $state('');

  // Library
  let stories = $state<GeneratedStory[]>([]);
  let expandedStory = $state<number | null>(null);

  // Cost estimation
  const pricing: Record<string, { input: number; output: number; speed: number; name: string }> = {
    groq: { input: 0.59, output: 0.79, speed: 394, name: 'Groq (Llama 3.3 70B)' },
    openrouter: { input: 0.15, output: 0.60, speed: 80, name: 'OpenRouter (Maverick)' },
    anthropic: { input: 1.00, output: 5.00, speed: 60, name: 'Anthropic (Haiku 4.5)' },
    gemini: { input: 0.30, output: 2.50, speed: 120, name: 'Google (Gemini Flash)' },
    together: { input: 0.60, output: 1.70, speed: 100, name: 'Together (DeepSeek V3)' },
    openai: { input: 0.15, output: 0.60, speed: 80, name: 'OpenAI (GPT-4o-mini)' },
    xai: { input: 2.00, output: 10.00, speed: 50, name: 'xAI (Grok)' },
  };

  function getDefaultModel(p: string): string {
    const defaults: Record<string, string> = {
      anthropic: 'claude-haiku-4-5-20251001', openai: 'gpt-4o-mini', xai: 'grok-3-mini',
      gemini: 'gemini-2.5-flash', groq: 'llama-3.3-70b-versatile',
      together: 'meta-llama/Llama-3.3-70B-Instruct-Turbo', openrouter: 'meta-llama/llama-4-maverick',
    };
    return defaults[p] || 'gpt-4o-mini';
  }

  // Token estimates
  const SINGLE_INPUT_TOKENS = 800;
  const SINGLE_OUTPUT_TOKENS = 2500;
  const FAMILY_INPUT_TOKENS = 3000;
  const FAMILY_OUTPUT_TOKENS = 5000;

  let costEstimate = $derived.by(() => {
    const p = pricing[provider] || pricing.groq;
    const count = selectedPeople.length || 1;
    if (storyMode === 'single') {
      const inputCost = (SINGLE_INPUT_TOKENS * count / 1_000_000) * p.input;
      const outputCost = (SINGLE_OUTPUT_TOKENS * count / 1_000_000) * p.output;
      return inputCost + outputCost;
    } else {
      const inputCost = (FAMILY_INPUT_TOKENS / 1_000_000) * p.input;
      const outputCost = (FAMILY_OUTPUT_TOKENS / 1_000_000) * p.output;
      return inputCost + outputCost;
    }
  });

  let timeEstimate = $derived.by(() => {
    const p = pricing[provider] || pricing.groq;
    const count = selectedPeople.length || 1;
    const totalTokens = storyMode === 'single'
      ? SINGLE_OUTPUT_TOKENS * count
      : FAMILY_OUTPUT_TOKENS;
    const seconds = totalTokens / p.speed;
    if (seconds < 60) return `~${Math.ceil(seconds)}s`;
    return `~${Math.ceil(seconds / 60)}m ${Math.ceil(seconds % 60)}s`;
  });

  // --- Init ---
  async function loadConfig() {
    const db = await getDb();
    const rows = await db.select<{ value: string }[]>(`SELECT value FROM settings WHERE key = 'ai_active_provider'`);
    provider = rows[0]?.value || 'groq';
    const mrows = await db.select<{ value: string }[]>(`SELECT value FROM settings WHERE key = $1`, [`ai_model_${provider}`]);
    model = mrows[0]?.value || getDefaultModel(provider);
    await loadProviders();
    stories = await getStories();
  }

  async function loadProviders() {
    const db = await getDb();
    const ids = ['anthropic', 'openai', 'xai', 'gemini', 'groq', 'together', 'openrouter'];
    const names: Record<string, string> = { anthropic: 'Anthropic', openai: 'OpenAI', xai: 'xAI', gemini: 'Gemini', groq: 'Groq', together: 'Together', openrouter: 'OpenRouter' };
    const result: typeof availableProviders = [];
    for (const pid of ids) {
      const keyRows = await db.select<{ value: string }[]>(`SELECT value FROM settings WHERE key = $1`, [`ai_key_${pid}`]);
      if (keyRows[0]?.value) {
        const modelRows = await db.select<{ value: string }[]>(`SELECT value FROM settings WHERE key = $1`, [`ai_model_${pid}`]);
        result.push({ id: pid, name: names[pid] || pid, model: modelRows[0]?.value || getDefaultModel(pid) });
      }
    }
    availableProviders = result;
  }

  // --- Person search ---
  let searchTimeout: ReturnType<typeof setTimeout>;
  function onSearch() {
    clearTimeout(searchTimeout);
    if (!personSearch.trim()) { personResults = []; showDropdown = false; return; }
    searchTimeout = setTimeout(async () => {
      personResults = await getPersons(personSearch, 20);
      showDropdown = personResults.length > 0;
    }, 200);
  }

  function addPerson(p: Person) {
    if (!selectedPeople.find(s => s.xref === p.xref)) {
      selectedPeople = [...selectedPeople, p];
    }
    personSearch = '';
    showDropdown = false;
  }

  function removePerson(xref: string) {
    selectedPeople = selectedPeople.filter(p => p.xref !== xref);
  }

  // --- Story generation ---
  async function buildPersonContext(person: Person): Promise<string> {
    const events = await getEvents(person.xref);
    const parents = await getParents(person.xref);
    const families = await getSpouseFamilies(person.xref);

    let ctx = `Name: ${person.givenName} ${person.surname}`;
    if (person.sex !== 'U') ctx += ` | Sex: ${person.sex === 'M' ? 'Male' : 'Female'}`;
    if (person.birthDate) ctx += ` | Born: ${person.birthDate}`;
    if (person.birthPlace) ctx += ` in ${person.birthPlace}`;
    if (person.deathDate) ctx += ` | Died: ${person.deathDate}`;
    if (person.deathPlace) ctx += ` in ${person.deathPlace}`;
    if (parents.father) ctx += ` | Father: ${parents.father.givenName} ${parents.father.surname}`;
    if (parents.mother) ctx += ` | Mother: ${parents.mother.givenName} ${parents.mother.surname}`;

    for (const fam of families) {
      const spouseXref = fam.partner1Xref === person.xref ? fam.partner2Xref : fam.partner1Xref;
      if (spouseXref) {
        const { getPerson } = await import('$lib/db');
        const spouse = await getPerson(spouseXref);
        if (spouse) ctx += ` | Spouse: ${spouse.givenName} ${spouse.surname}`;
      }
      if (fam.marriageDate) ctx += ` (m. ${fam.marriageDate})`;
      if (fam.marriagePlace) ctx += ` at ${fam.marriagePlace}`;
      const children = await getChildren(fam.xref);
      if (children.length > 0) {
        ctx += ` | Children: ${children.map(c => c.givenName).join(', ')}`;
      }
    }

    if (events.length > 0) {
      ctx += `\nLife events:`;
      for (const ev of events) {
        ctx += `\n  - ${ev.eventType}${ev.dateValue ? ' ' + ev.dateValue : ''}${ev.place ? ' at ' + ev.place : ''}${ev.description ? ' (' + ev.description + ')' : ''}`;
      }
    }
    return ctx;
  }

  function getSinglePrompt(): string {
    return `You are a gifted biographical writer and genealogy historian. Write a compelling, historically-grounded biographical narrative about the person described below.

INSTRUCTIONS:
- Write 1,500-2,000 words in a warm, literary style — like a chapter in a family history book
- Weave in historical context: what was happening in their region during their lifetime (wars, migrations, economic conditions, political changes)
- Use the specific dates, places, and events provided as the factual backbone
- Fill in likely daily life details based on the era, location, and social class
- Include sensory details about places and times
- Cite sources naturally: "According to the birth record...", "The 1920 census reveals..."
- End with the person's legacy and connection to descendants
- Use section breaks with meaningful subheadings
- Do NOT fabricate specific facts not supported by the records — clearly mark speculative passages with "likely" or "perhaps"

Write the narrative now.`;
  }

  function getFamilyPrompt(): string {
    return `You are a gifted family historian and narrative writer. Write a sweeping multi-generational family narrative about the people described below.

INSTRUCTIONS:
- Write 3,000-4,000 words covering the family across generations
- Structure chronologically, starting with the earliest ancestor
- Weave in major historical events that shaped each generation's choices
- Show how one generation's decisions affected the next
- Include migration patterns, occupational evolution, naming traditions
- Use specific dates and places as factual anchors
- Describe the world each generation was born into
- Include family dynamics: marriages, children, losses
- Cite records naturally throughout
- End with a reflection on the family's journey and enduring traits
- Do NOT fabricate specific facts — mark speculation clearly

Write the family narrative now.`;
  }

  async function callAI(systemPrompt: string, userContent: string): Promise<string> {
    const db = await getDb();
    const keyRows = await db.select<{ value: string }[]>(`SELECT value FROM settings WHERE key = $1`, [`ai_key_${provider}`]);
    const apiKey = keyRows[0]?.value;
    if (!apiKey) throw new Error(`No API key for ${provider}. Go to AI Settings to add one.`);

    const currentModel = model || getDefaultModel(provider);
    let url = '';
    let headers: Record<string, string> = { 'Content-Type': 'application/json' };
    let body = '';

    if (provider === 'anthropic') {
      url = 'https://api.anthropic.com/v1/messages';
      headers['x-api-key'] = apiKey;
      headers['anthropic-version'] = '2023-06-01';
      body = JSON.stringify({ model: currentModel, max_tokens: 6000, system: systemPrompt, messages: [{ role: 'user', content: userContent }] });
    } else if (provider === 'gemini') {
      url = `https://generativelanguage.googleapis.com/v1beta/models/${currentModel}:generateContent?key=${apiKey}`;
      body = JSON.stringify({ systemInstruction: { parts: [{ text: systemPrompt }] }, contents: [{ parts: [{ text: userContent }] }], generationConfig: { maxOutputTokens: 6000 } });
    } else {
      const endpoints: Record<string, string> = {
        openai: 'https://api.openai.com/v1/chat/completions',
        xai: 'https://api.x.ai/v1/chat/completions',
        groq: 'https://api.groq.com/openai/v1/chat/completions',
        together: 'https://api.together.xyz/v1/chat/completions',
        openrouter: 'https://openrouter.ai/api/v1/chat/completions',
      };
      url = endpoints[provider] || endpoints.openai;
      headers['Authorization'] = `Bearer ${apiKey}`;
      body = JSON.stringify({ model: currentModel, messages: [{ role: 'system', content: systemPrompt }, { role: 'user', content: userContent }], max_tokens: 6000 });
    }

    const resp = await fetch(url, { method: 'POST', headers, body });
    if (!resp.ok) {
      const errBody = await resp.text();
      throw new Error(`API error ${resp.status}: ${errBody.slice(0, 200)}`);
    }
    const data = await resp.json();

    if (provider === 'anthropic') return data.content?.[0]?.text || 'No response';
    if (provider === 'gemini') return data.candidates?.[0]?.content?.parts?.[0]?.text || 'No response';
    return data.choices?.[0]?.message?.content || 'No response';
  }

  async function generate() {
    if (selectedPeople.length === 0) return;
    isGenerating = true;
    error = '';
    generatedPreview = '';

    try {
      if (storyMode === 'single') {
        batchTotal = selectedPeople.length;
        batchProgress = 0;

        for (const person of selectedPeople) {
          currentPerson = `${person.givenName} ${person.surname}`;
          const ctx = await buildPersonContext(person);
          const narrative = await callAI(getSinglePrompt(), ctx);
          batchProgress++;

          await insertStory({
            personXref: person.xref,
            familyXref: '',
            storyType: 'single',
            title: `${person.givenName} ${person.surname} — A Biographical Narrative`,
            content: narrative,
            provider,
            model: model || getDefaultModel(provider),
            tokensUsed: SINGLE_INPUT_TOKENS + SINGLE_OUTPUT_TOKENS,
            costEstimate: costEstimate / selectedPeople.length,
            createdAt: new Date().toISOString(),
          });

          if (selectedPeople.length === 1) generatedPreview = narrative;
        }
      } else {
        batchTotal = 1;
        batchProgress = 0;
        currentPerson = 'Family narrative';

        const allCtx = await Promise.all(selectedPeople.map(buildPersonContext));
        const combined = allCtx.join('\n\n---\n\n');
        const narrative = await callAI(getFamilyPrompt(), combined);
        batchProgress = 1;

        const surnames = [...new Set(selectedPeople.map(p => p.surname))].join(' & ');
        await insertStory({
          personXref: selectedPeople[0].xref,
          familyXref: '',
          storyType: 'family',
          title: `The ${surnames} Family — A Multi-Generational Narrative`,
          content: narrative,
          provider,
          model: model || getDefaultModel(provider),
          tokensUsed: FAMILY_INPUT_TOKENS + FAMILY_OUTPUT_TOKENS,
          costEstimate,
          createdAt: new Date().toISOString(),
        });

        generatedPreview = narrative;
      }

      stories = await getStories();
    } catch (e: any) {
      error = e.message || 'Generation failed';
    }
    isGenerating = false;
  }

  async function removeStory(id: number) {
    await deleteStory(id);
    stories = await getStories();
    if (expandedStory === id) expandedStory = null;
  }

  function renderMarkdown(text: string): string {
    let html = text
      .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
    html = html.replace(/\*\*([^*]+)\*\*/g, '<strong>$1</strong>');
    html = html.replace(/\*([^*]+)\*/g, '<em>$1</em>');
    html = html.replace(/^### (.+)$/gm, '<h4 style="font-family:var(--font-serif);font-size:0.95rem;font-weight:600;margin:0.75rem 0 0.25rem;">$1</h4>');
    html = html.replace(/^## (.+)$/gm, '<h3 style="font-family:var(--font-serif);font-size:1.1rem;font-weight:600;margin:1rem 0 0.25rem;">$1</h3>');
    html = html.replace(/^# (.+)$/gm, '<h2 style="font-family:var(--font-serif);font-size:1.25rem;font-weight:700;margin:1rem 0 0.5rem;">$1</h2>');
    html = html.replace(/^[-*] (.+)$/gm, '<div style="display:flex;gap:0.5rem;margin:0.15rem 0;padding-left:0.25rem;"><span style="color:var(--ink-muted);">&bull;</span><span>$1</span></div>');
    html = html.replace(/\n\n/g, '<div style="height:0.5rem;"></div>');
    html = html.replace(/\n/g, '<br>');
    return html;
  }

  function formatDate(iso: string): string {
    return new Date(iso).toLocaleDateString('en-US', { year: 'numeric', month: 'short', day: 'numeric' });
  }

  $effect(() => { loadConfig(); });
</script>

<div class="flex flex-col h-full overflow-hidden" style="font-family: var(--font-sans); color: var(--ink);">
  <!-- Toolbar -->
  <div class="shrink-0 px-6 py-4" style="border-bottom: 1px solid var(--border-rule);">
    <div class="flex items-center gap-4 mb-3">
      <h1 class="dossier-header mb-0 pb-0" style="border-bottom: none; margin-bottom: 0;">{t('ai.storyGenerator')}</h1>
      <div class="arch-tabs">
        <button onclick={() => view = 'generate'} class="arch-tab {view === 'generate' ? 'active' : ''}">Generate</button>
        <button onclick={() => view = 'library'} class="arch-tab {view === 'library' ? 'active' : ''}">Library ({stories.length})</button>
      </div>
    </div>

    {#if view === 'generate'}
      <div class="flex items-center gap-4 flex-wrap">
        <!-- Mode selector -->
        <div class="arch-tabs">
          <button onclick={() => storyMode = 'single'} class="arch-tab {storyMode === 'single' ? 'active' : ''}">Single Person</button>
          <button onclick={() => storyMode = 'family'} class="arch-tab {storyMode === 'family' ? 'active' : ''}">Family / Multi-Gen</button>
        </div>

        <!-- Provider selector -->
        <select
          bind:value={provider}
          onchange={() => { model = getDefaultModel(provider); }}
          class="px-3 py-1.5 text-xs rounded-lg border"
          style="background: var(--parchment); border-color: var(--border-subtle); color: var(--ink); font-family: var(--font-sans);"
        >
          {#each availableProviders as ap}
            <option value={ap.id}>{ap.name} — {ap.model.length > 25 ? ap.model.slice(0, 25) + '...' : ap.model}</option>
          {/each}
          {#if availableProviders.length === 0}
            <option value="groq">{t('ai.noApiKeys')}</option>
          {/if}
        </select>

        <!-- Cost + Time estimate -->
        <div class="flex items-center gap-3 ml-auto">
          <div class="text-right">
            <div class="text-[10px] uppercase tracking-wider" style="color: var(--ink-faint); font-family: var(--font-mono);">{t('ai.estimatedCost')}</div>
            <div class="text-sm font-bold" style="color: var(--accent); font-family: var(--font-mono);">
              {costEstimate < 0.01 ? '<$0.01' : `$${costEstimate.toFixed(2)}`}
            </div>
          </div>
          <div class="text-right">
            <div class="text-[10px] uppercase tracking-wider" style="color: var(--ink-faint); font-family: var(--font-mono);">{t('ai.estimatedTime')}</div>
            <div class="text-sm font-bold" style="color: var(--ink-light); font-family: var(--font-mono);">{timeEstimate}</div>
          </div>
        </div>
      </div>
    {/if}
  </div>

  <!-- Content -->
  <div class="flex-1 overflow-auto p-6">
    {#if view === 'generate'}
      <div class="max-w-3xl mx-auto">
        <!-- Person selector -->
        <div class="mb-6">
          <div class="arch-section-header mb-2">
            {storyMode === 'single' ? 'Select People (batch mode — one story per person)' : 'Select Family Members'}
          </div>

          <!-- Selected people chips -->
          {#if selectedPeople.length > 0}
            <div class="flex flex-wrap gap-2 mb-3">
              {#each selectedPeople as person}
                <span class="inline-flex items-center gap-1.5 px-3 py-1 rounded-full text-xs" style="background: var(--parchment); color: var(--ink-light); font-family: var(--font-sans);">
                  {person.givenName} {person.surname}
                  {#if person.birthDate}
                    <span style="color: var(--ink-faint);">b.{person.birthDate}</span>
                  {/if}
                  <button onclick={() => removePerson(person.xref)} class="hover:text-red-500 ml-0.5">&times;</button>
                </span>
              {/each}
            </div>
          {/if}

          <!-- Search input -->
          <div class="relative">
            <input
              type="text"
              bind:value={personSearch}
              oninput={onSearch}
              onfocus={() => { if (personResults.length > 0) showDropdown = true; }}
              onblur={() => setTimeout(() => showDropdown = false, 200)}
              placeholder="Search for a person to add..."
              class="w-full arch-input px-3 py-2 text-sm"
            />
            {#if showDropdown && personResults.length > 0}
              <div class="absolute top-full left-0 right-0 mt-1 arch-card rounded-lg z-50 max-h-48 overflow-y-auto" style="box-shadow: var(--shadow-lg);">
                {#each personResults as person}
                  <button
                    onmousedown={() => addPerson(person)}
                    class="w-full text-left px-3 py-2 text-xs hover:bg-black/5 flex items-center gap-2"
                  >
                    <span class="font-medium" style="color: var(--ink);">{person.givenName} {person.surname}</span>
                    {#if person.birthDate || person.deathDate}
                      <span style="color: var(--ink-faint);">({person.birthDate || '?'} — {person.deathDate || '?'})</span>
                    {/if}
                  </button>
                {/each}
              </div>
            {/if}
          </div>
        </div>

        <!-- Batch info card -->
        {#if selectedPeople.length > 0}
          <div class="arch-card rounded-xl p-5 mb-6">
            <div class="flex items-center justify-between mb-3">
              <div>
                <div class="text-sm font-semibold" style="font-family: var(--font-serif); color: var(--ink);">
                  {storyMode === 'single'
                    ? `${selectedPeople.length} ${selectedPeople.length === 1 ? 'story' : 'stories'} to generate`
                    : '1 multi-generational narrative'}
                </div>
                <div class="text-xs mt-0.5" style="color: var(--ink-muted);">
                  Using {pricing[provider]?.name || provider} &middot;
                  {storyMode === 'single' ? `~${(SINGLE_OUTPUT_TOKENS * selectedPeople.length).toLocaleString()} output tokens` : `~${FAMILY_OUTPUT_TOKENS.toLocaleString()} output tokens`}
                </div>
              </div>
              <button
                onclick={generate}
                disabled={isGenerating}
                class="btn-accent px-5 py-2.5 text-sm rounded-lg disabled:opacity-50"
              >
                {isGenerating ? 'Generating...' : `Generate ${storyMode === 'single' ? selectedPeople.length + ' Stories' : 'Family Narrative'}`}
              </button>
            </div>

            <!-- Progress bar -->
            {#if isGenerating}
              <div class="mt-3">
                <div class="flex items-center justify-between text-xs mb-1" style="color: var(--ink-muted);">
                  <span>{currentPerson}</span>
                  <span style="font-family: var(--font-mono);">{batchProgress}/{batchTotal}</span>
                </div>
                <div class="arch-progress-track h-1.5">
                  <div class="arch-progress-bar h-full" style="width: {batchTotal > 0 ? (batchProgress / batchTotal) * 100 : 0}%;"></div>
                </div>
              </div>
            {/if}
          </div>
        {/if}

        <!-- Error -->
        {#if error}
          <div class="rounded-xl px-4 py-3 mb-6 text-xs" style="background: #fef2f2; color: #dc2626; border: 1px solid #fecaca;">
            {error}
          </div>
        {/if}

        <!-- Generated preview -->
        {#if generatedPreview && !isGenerating}
          <div class="arch-card rounded-xl p-6 mb-6">
            <div class="flex items-center justify-between mb-4">
              <h3 style="font-family: var(--font-serif); font-weight: 700; font-size: 1.1rem; color: var(--ink);">Generated Narrative</h3>
              <span class="text-[10px]" style="color: var(--ink-faint); font-family: var(--font-mono);">
                {provider} / {model || getDefaultModel(provider)}
              </span>
            </div>
            <div class="text-sm leading-relaxed" style="font-family: var(--font-serif); color: var(--ink-light);">
              {@html renderMarkdown(generatedPreview)}
            </div>
          </div>
        {/if}

        <!-- Empty state -->
        {#if selectedPeople.length === 0 && !isGenerating && !generatedPreview}
          <div class="arch-card rounded-xl p-10 text-center">
            <div class="text-3xl mb-3" style="font-family: var(--font-serif); color: var(--ink-faint);">{t('nav.stories')}</div>
            <p class="text-sm mb-2" style="color: var(--ink-muted);">Generate AI-powered biographical narratives from your genealogical records.</p>
            <p class="text-xs" style="color: var(--ink-faint);">
              Each story uses birth, death, marriage, and life event records enriched with historical context
              about the places and times your ancestors lived.
            </p>
            <div class="mt-6 flex justify-center gap-4 text-xs" style="color: var(--ink-faint); font-family: var(--font-mono);">
              <span>Single person: ~1,500 words</span>
              <span>|</span>
              <span>Family narrative: ~3,000 words</span>
            </div>
          </div>
        {/if}
      </div>

    {:else}
      <!-- LIBRARY VIEW -->
      <div class="max-w-3xl mx-auto">
        {#if stories.length === 0}
          <div class="arch-card rounded-xl p-10 text-center">
            <p class="text-sm" style="color: var(--ink-muted); font-family: var(--font-serif); font-style: italic;">No stories generated yet.</p>
          </div>
        {:else}
          <div class="space-y-3">
            {#each stories as story}
              <div class="arch-card rounded-xl overflow-hidden">
                <button
                  onclick={() => expandedStory = expandedStory === story.id ? null : story.id}
                  class="w-full text-left px-5 py-4 flex items-center justify-between"
                >
                  <div>
                    <div class="text-sm font-semibold" style="font-family: var(--font-serif); color: var(--ink);">{story.title}</div>
                    <div class="text-xs mt-0.5 flex items-center gap-2" style="color: var(--ink-muted);">
                      <span class="px-1.5 py-0.5 rounded text-[9px]" style="background: var(--parchment); font-family: var(--font-mono);">
                        {story.storyType === 'single' ? 'Single' : 'Family'}
                      </span>
                      <span>{story.provider} / {story.model.length > 20 ? story.model.slice(0, 20) + '...' : story.model}</span>
                      <span style="font-family: var(--font-mono);">${story.costEstimate.toFixed(3)}</span>
                      <span>{formatDate(story.createdAt)}</span>
                    </div>
                  </div>
                  <span class="text-xs" style="color: var(--ink-faint);">{expandedStory === story.id ? '\u25B2' : '\u25BC'}</span>
                </button>

                {#if expandedStory === story.id}
                  <div class="px-5 pb-5" style="border-top: 1px solid var(--border-subtle);">
                    <div class="pt-4 text-sm leading-relaxed" style="font-family: var(--font-serif); color: var(--ink-light);">
                      {@html renderMarkdown(story.content)}
                    </div>
                    <div class="mt-4 flex justify-end">
                      <button onclick={() => removeStory(story.id)} class="text-xs px-3 py-1 rounded-lg" style="color: var(--color-error);">Delete Story</button>
                    </div>
                  </div>
                {/if}
              </div>
            {/each}
          </div>
        {/if}
      </div>
    {/if}
  </div>
</div>
