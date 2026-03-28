<script lang="ts">
  import { getDb, getAIChatHistory, insertAIChatMessage, clearAIChatHistory, getPersons, getEvents } from '$lib/db';
  import type { AIChatMessage, Person, GedcomEvent } from '$lib/types';

  // --- Research modes with rich system prompts ---
  const researchModes = [
    {
      id: 'general',
      label: 'General Research',
      icon: 'M',
      prompt: `You are an expert genealogy research assistant with deep knowledge of historical records, migration patterns, and family history methodology. You follow the Genealogical Proof Standard (GPS): reasonably exhaustive search, complete and accurate citations, analysis and correlation of evidence, resolution of conflicting evidence, and a soundly reasoned conclusion. When analyzing family connections, distinguish between direct evidence, indirect evidence, and negative evidence. Always cite the type of record and repository when referencing sources. Format dates as DD MMM YYYY. Use standard genealogical abbreviations (b., d., m., bur., chr., c./ca., fl.).`,
    },
    {
      id: 'record',
      label: 'Record Analysis',
      icon: 'R',
      prompt: `You are a historical document specialist and paleographer. Your expertise covers: census records (US 1790-1950, UK 1841-1921, Canadian), vital records (birth/marriage/death certificates), church records (baptism, marriage, burial registers), immigration/naturalization records, military records, land/property records, probate/wills, and newspaper archives. When analyzing a record: (1) identify the record type, date, and jurisdiction, (2) extract every name, date, place, relationship, and occupation, (3) note any abbreviations or period-specific terminology, (4) flag potential transcription errors or ambiguities, (5) suggest related records that might provide corroborating evidence. For handwriting, describe letterforms precisely.`,
    },
    {
      id: 'brickwall',
      label: 'Brick Wall Breaker',
      icon: 'B',
      prompt: `You are a genealogy problem-solver specializing in breaking through research dead ends. Your methodology: (1) Restate what IS known with citations, (2) Identify ALL assumptions being made, (3) Challenge each assumption with alternative explanations, (4) Propose a cluster research strategy — examining the subject's FAN club (Friends, Associates, Neighbors), (5) Suggest record types not yet consulted, (6) Consider name spelling variants, phonetic equivalents, and naming patterns of the era and culture, (7) Look for indirect evidence through collateral relatives, (8) Consider historical context (wars, migrations, epidemics, economic conditions) that might explain gaps. Think laterally, not just linearly.`,
    },
    {
      id: 'source',
      label: 'Source Evaluation',
      icon: 'S',
      prompt: `You are a genealogical evidence analyst following the standards of the Board for Certification of Genealogists. Evaluate sources using this framework: (1) SOURCE CLASSIFICATION: Original vs. derivative (image copy, transcription, abstract, compiled); authored vs. record. (2) INFORMATION CLASSIFICATION: Primary (firsthand knowledge), secondary (secondhand), indeterminate. (3) EVIDENCE CLASSIFICATION: Direct (answers the question), indirect (requires inference), negative (absence of expected data). Apply the Genealogical Proof Standard. Identify conflicts between sources and explain which should be weighted more heavily and why. Note informant reliability — who provided the information and what would they likely know?`,
    },
    {
      id: 'dna',
      label: 'DNA Interpretation',
      icon: 'D',
      prompt: `You are a genetic genealogy specialist. You help interpret DNA results across all major testing companies (AncestryDNA, 23andMe, FamilyTreeDNA, MyHeritage, LivingDNA). Your expertise covers: (1) Shared cM analysis — use the Shared cM Project data to predict relationships, (2) Segment analysis — triangulation, pile-up regions, endogamy effects, (3) Ethnicity estimates — explain admixture algorithms and their limitations, (4) Y-DNA — haplogroups, STR markers, surname projects, (5) mtDNA — maternal lineage tracing, heteroplasmy, (6) Tools — explain Leeds Method, WATO, DNA Painter, What Are The Odds. Always note that DNA predictions are probabilistic, not deterministic. Explain endogamy and pedigree collapse when relevant.`,
    },
    {
      id: 'names',
      label: 'Name Variants',
      icon: 'N',
      prompt: `You are a historical onomastics specialist — an expert in the evolution of personal names, surnames, and naming conventions across cultures and centuries. Your knowledge covers: (1) Patronymic systems (Scandinavian, Welsh, Scottish, Jewish, Slavic), (2) Anglicization patterns of immigrant names, (3) Soundex and phonetic equivalents, (4) Common clerical misspellings and abbreviations in historical records, (5) Naming conventions by culture (given name order, middle names, religious names, name days), (6) Surname origins (occupational, geographic, patronymic, descriptive), (7) Jewish naming: Hebrew/Yiddish/secular name relationships, name changes at Ellis Island (myth vs. reality), (8) Name changes due to religious conversion, enslavement, or assimilation. Provide all known variants and their linguistic derivation.`,
    },
  ];

  let messages = $state<AIChatMessage[]>([]);
  let input = $state('');
  let isLoading = $state(false);
  let provider = $state('');
  let model = $state('');
  let error = $state('');
  let activeMode = $state('general');

  // Person context
  let personSearch = $state('');
  let personResults = $state<Person[]>([]);
  let selectedPerson = $state<Person | null>(null);
  let selectedPersonEvents = $state<GedcomEvent[]>([]);
  let showPersonDropdown = $state(false);

  // Provider quick-switch
  let showProviderMenu = $state(false);
  let availableProviders = $state<{ id: string; name: string; model: string }[]>([]);

  let chatContainer: HTMLDivElement;

  async function loadConfig() {
    const db = await getDb();
    const rows = await db.select<{ value: string }[]>(`SELECT value FROM settings WHERE key = 'ai_active_provider'`);
    provider = rows[0]?.value || 'groq';
    const mrows = await db.select<{ value: string }[]>(`SELECT value FROM settings WHERE key = $1`, [`ai_model_${provider}`]);
    model = mrows[0]?.value || getDefaultModel(provider);
    messages = await getAIChatHistory();
    await loadAvailableProviders();
    scrollToBottom();
  }

  async function loadAvailableProviders() {
    const db = await getDb();
    const providerIds = ['anthropic', 'openai', 'xai', 'gemini', 'groq', 'together', 'openrouter'];
    const providerNames: Record<string, string> = {
      anthropic: 'Anthropic', openai: 'OpenAI', xai: 'xAI', gemini: 'Gemini',
      groq: 'Groq', together: 'Together', openrouter: 'OpenRouter',
    };
    const result: { id: string; name: string; model: string }[] = [];
    for (const pid of providerIds) {
      const keyRows = await db.select<{ value: string }[]>(`SELECT value FROM settings WHERE key = $1`, [`ai_key_${pid}`]);
      if (keyRows[0]?.value) {
        const modelRows = await db.select<{ value: string }[]>(`SELECT value FROM settings WHERE key = $1`, [`ai_model_${pid}`]);
        result.push({ id: pid, name: providerNames[pid] || pid, model: modelRows[0]?.value || getDefaultModel(pid) });
      }
    }
    availableProviders = result;
  }

  function getDefaultModel(p: string): string {
    const defaults: Record<string, string> = {
      anthropic: 'claude-haiku-4-5-20251001', openai: 'gpt-4o-mini', xai: 'grok-3-mini',
      gemini: 'gemini-2.5-flash', groq: 'llama-3.3-70b-versatile',
      together: 'meta-llama/Llama-3.3-70B-Instruct-Turbo', openrouter: 'meta-llama/llama-4-maverick',
    };
    return defaults[p] || 'gpt-4o-mini';
  }

  function scrollToBottom() {
    requestAnimationFrame(() => {
      if (chatContainer) chatContainer.scrollTop = chatContainer.scrollHeight;
    });
  }

  // Person search with debounce
  let searchTimeout: ReturnType<typeof setTimeout>;
  async function onPersonSearch() {
    clearTimeout(searchTimeout);
    if (!personSearch.trim()) {
      personResults = [];
      showPersonDropdown = false;
      return;
    }
    searchTimeout = setTimeout(async () => {
      personResults = await getPersons(personSearch, 20);
      showPersonDropdown = personResults.length > 0;
    }, 200);
  }

  async function selectPerson(person: Person) {
    selectedPerson = person;
    selectedPersonEvents = await getEvents(person.xref);
    personSearch = '';
    showPersonDropdown = false;
  }

  function clearPersonContext() {
    selectedPerson = null;
    selectedPersonEvents = [];
  }

  function buildPersonContext(): string {
    if (!selectedPerson) return '';
    const p = selectedPerson;
    let ctx = `\n\n[PERSON CONTEXT]\nName: ${p.givenName} ${p.surname}`;
    if (p.birthDate || p.birthPlace) ctx += `\nBorn: ${p.birthDate || 'unknown'}${p.birthPlace ? ' in ' + p.birthPlace : ''}`;
    if (p.deathDate || p.deathPlace) ctx += `\nDied: ${p.deathDate || 'unknown'}${p.deathPlace ? ' in ' + p.deathPlace : ''}`;
    if (p.sex && p.sex !== 'U') ctx += `\nSex: ${p.sex === 'M' ? 'Male' : p.sex === 'F' ? 'Female' : p.sex}`;
    if (selectedPersonEvents.length > 0) {
      ctx += `\nEvents:`;
      for (const ev of selectedPersonEvents) {
        ctx += `\n  - ${ev.eventType}${ev.dateValue ? ' ' + ev.dateValue : ''}${ev.place ? ' at ' + ev.place : ''}${ev.description ? ' (' + ev.description + ')' : ''}`;
      }
    }
    ctx += `\n[END CONTEXT]`;
    return ctx;
  }

  function getCurrentSystemPrompt(): string {
    const mode = researchModes.find(m => m.id === activeMode) || researchModes[0];
    return mode.prompt + buildPersonContext();
  }

  async function switchProvider(pid: string, pModel: string) {
    provider = pid;
    model = pModel;
    const db = await getDb();
    await db.execute(`INSERT OR REPLACE INTO settings (key, value) VALUES ('ai_active_provider', $1)`, [pid]);
    showProviderMenu = false;
  }

  async function send() {
    if (!input.trim() || isLoading) return;
    error = '';
    const userMsg = input.trim();
    input = '';

    const ts = new Date().toISOString();
    const personXref = selectedPerson?.xref || '';
    await insertAIChatMessage({ provider, model, role: 'user', content: userMsg, personXref, timestamp: ts });
    messages = await getAIChatHistory();
    scrollToBottom();
    isLoading = true;

    try {
      const db = await getDb();
      const keyRows = await db.select<{ value: string }[]>(`SELECT value FROM settings WHERE key = $1`, [`ai_key_${provider}`]);
      const apiKey = keyRows[0]?.value;
      if (!apiKey) throw new Error(`No API key for ${provider}. Go to AI Settings to add one.`);

      const systemPrompt = getCurrentSystemPrompt();

      // Build conversation history from last 20 messages
      const recentMessages = messages.slice(-20);
      const conversationHistory = recentMessages.map(m => ({
        role: m.role as 'user' | 'assistant',
        content: m.content,
      }));

      let url = '';
      let headers: Record<string, string> = { 'Content-Type': 'application/json' };
      let body = '';

      if (provider === 'anthropic') {
        url = 'https://api.anthropic.com/v1/messages';
        headers['x-api-key'] = apiKey;
        headers['anthropic-version'] = '2023-06-01';
        body = JSON.stringify({
          model,
          max_tokens: 4096,
          system: systemPrompt,
          messages: conversationHistory,
        });
      } else if (provider === 'gemini') {
        url = `https://generativelanguage.googleapis.com/v1beta/models/${model}:generateContent?key=${apiKey}`;
        const geminiContents = conversationHistory.map(m => ({
          role: m.role === 'assistant' ? 'model' : 'user',
          parts: [{ text: m.content }],
        }));
        body = JSON.stringify({
          systemInstruction: { parts: [{ text: systemPrompt }] },
          contents: geminiContents,
          generationConfig: { maxOutputTokens: 4096 },
        });
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
        body = JSON.stringify({
          model,
          messages: [
            { role: 'system', content: systemPrompt },
            ...conversationHistory,
          ],
          max_tokens: 4096,
        });
      }

      const resp = await fetch(url, { method: 'POST', headers, body });
      if (!resp.ok) {
        const errBody = await resp.text();
        throw new Error(`API error ${resp.status}: ${errBody.slice(0, 200)}`);
      }
      const data = await resp.json();

      let reply = '';
      if (provider === 'anthropic') {
        reply = data.content?.[0]?.text || 'No response';
      } else if (provider === 'gemini') {
        reply = data.candidates?.[0]?.content?.parts?.[0]?.text || 'No response';
      } else {
        reply = data.choices?.[0]?.message?.content || 'No response';
      }

      await insertAIChatMessage({ provider, model, role: 'assistant', content: reply, personXref, timestamp: new Date().toISOString() });
      messages = await getAIChatHistory();
      scrollToBottom();
    } catch (e: any) {
      error = e.message || 'Unknown error';
    }
    isLoading = false;
  }

  async function clearChat() {
    await clearAIChatHistory();
    messages = [];
  }

  function renderMarkdown(text: string): string {
    let html = text
      // Escape HTML entities
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;');

    // Code blocks (triple backtick)
    html = html.replace(/```(\w*)\n([\s\S]*?)```/g, '<pre style="background:var(--parchment-deep);padding:0.75rem;border-radius:6px;overflow-x:auto;font-size:0.8rem;margin:0.5rem 0;"><code>$2</code></pre>');

    // Inline code
    html = html.replace(/`([^`]+)`/g, '<code style="background:var(--parchment-deep);padding:0.125rem 0.375rem;border-radius:3px;font-size:0.85em;">$1</code>');

    // Bold
    html = html.replace(/\*\*([^*]+)\*\*/g, '<strong>$1</strong>');

    // Italic
    html = html.replace(/\*([^*]+)\*/g, '<em>$1</em>');

    // Headings (h3 for all levels to keep consistent)
    html = html.replace(/^### (.+)$/gm, '<h4 style="font-family:var(--font-serif);font-size:0.95rem;font-weight:600;margin:0.75rem 0 0.25rem;">$1</h4>');
    html = html.replace(/^## (.+)$/gm, '<h3 style="font-family:var(--font-serif);font-size:1.05rem;font-weight:600;margin:0.75rem 0 0.25rem;">$1</h3>');
    html = html.replace(/^# (.+)$/gm, '<h3 style="font-family:var(--font-serif);font-size:1.1rem;font-weight:700;margin:0.75rem 0 0.25rem;">$1</h3>');

    // Numbered lists
    html = html.replace(/^(\d+)\. (.+)$/gm, '<div style="display:flex;gap:0.5rem;margin:0.15rem 0;"><span style="color:var(--ink-muted);min-width:1.25rem;text-align:right;">$1.</span><span>$2</span></div>');

    // Bullet lists
    html = html.replace(/^[-*] (.+)$/gm, '<div style="display:flex;gap:0.5rem;margin:0.15rem 0;padding-left:0.25rem;"><span style="color:var(--ink-muted);">&bull;</span><span>$1</span></div>');

    // Line breaks (double newline = paragraph break, single = br)
    html = html.replace(/\n\n/g, '<div style="height:0.5rem;"></div>');
    html = html.replace(/\n/g, '<br>');

    return html;
  }

  function getModeLabel(): string {
    return researchModes.find(m => m.id === activeMode)?.label || 'General Research';
  }

  $effect(() => { loadConfig(); });
</script>

<div class="flex flex-col h-full" style="font-family: var(--font-sans); color: var(--ink);">
  <!-- Top toolbar -->
  <div class="shrink-0 border-b" style="border-color: var(--border-subtle, rgba(0,0,0,0.06));">
    <!-- Mode selector pills -->
    <div class="px-4 pt-3 pb-2">
      <div class="flex items-center gap-2 overflow-x-auto pb-1">
        {#each researchModes as mode}
          <button
            onclick={() => activeMode = mode.id}
            class="shrink-0 px-3 py-1.5 text-xs font-medium rounded-full transition-all whitespace-nowrap"
            style="{activeMode === mode.id
              ? 'background: var(--ink); color: var(--parchment, #fff);'
              : 'background: var(--parchment-deep, #f5f0e8); color: var(--ink-muted);'}"
          >
            {mode.label}
          </button>
        {/each}
      </div>
    </div>

    <!-- Context bar: person selector + provider -->
    <div class="px-4 pb-3 flex items-center gap-3 flex-wrap">
      <!-- Person context selector -->
      <div class="relative flex-1 min-w-[200px] max-w-[360px]">
        {#if selectedPerson}
          <div class="flex items-center gap-2 px-3 py-1.5 rounded-lg text-xs" style="background: var(--parchment-deep, #f5f0e8);">
            <span class="font-medium" style="color: var(--ink);">{selectedPerson.givenName} {selectedPerson.surname}</span>
            {#if selectedPerson.birthDate}
              <span style="color: var(--ink-faint);">b. {selectedPerson.birthDate}</span>
            {/if}
            <button onclick={clearPersonContext} class="ml-auto text-ink-faint hover:text-red-500" title="Remove person context">&times;</button>
          </div>
        {:else}
          <input
            type="text"
            bind:value={personSearch}
            oninput={onPersonSearch}
            onfocus={() => { if (personResults.length > 0) showPersonDropdown = true; }}
            onblur={() => setTimeout(() => showPersonDropdown = false, 200)}
            placeholder="Attach person context..."
            class="w-full px-3 py-1.5 text-xs rounded-lg border outline-none"
            style="background: var(--parchment, #fff); border-color: var(--border-subtle, rgba(0,0,0,0.1)); color: var(--ink);"
          />
          {#if showPersonDropdown && personResults.length > 0}
            <div class="absolute top-full left-0 right-0 mt-1 rounded-lg shadow-lg border z-50 max-h-48 overflow-y-auto" style="background: var(--parchment, #fff); border-color: var(--border-subtle, rgba(0,0,0,0.1));">
              {#each personResults as person}
                <button
                  onmousedown={() => selectPerson(person)}
                  class="w-full text-left px-3 py-2 text-xs hover:bg-black/5 flex items-center gap-2"
                >
                  <span class="font-medium">{person.givenName} {person.surname}</span>
                  {#if person.birthDate || person.deathDate}
                    <span style="color: var(--ink-faint);">
                      ({person.birthDate || '?'} &ndash; {person.deathDate || '?'})
                    </span>
                  {/if}
                </button>
              {/each}
            </div>
          {/if}
        {/if}
      </div>

      <!-- Provider/model quick-switch -->
      <div class="relative">
        <button
          onclick={() => showProviderMenu = !showProviderMenu}
          class="flex items-center gap-1.5 px-3 py-1.5 text-xs rounded-lg border transition-all"
          style="border-color: var(--border-subtle, rgba(0,0,0,0.1)); color: var(--ink-muted);"
        >
          <span class="font-medium">{provider}</span>
          <span style="color: var(--ink-faint);">/</span>
          <span style="color: var(--ink-faint);">{model.length > 28 ? model.slice(0, 28) + '...' : model}</span>
          <span class="text-[10px]">&#9662;</span>
        </button>
        {#if showProviderMenu && availableProviders.length > 0}
          <div class="absolute top-full right-0 mt-1 rounded-lg shadow-lg border z-50 min-w-[200px]" style="background: var(--parchment, #fff); border-color: var(--border-subtle, rgba(0,0,0,0.1));">
            {#each availableProviders as ap}
              <button
                onclick={() => switchProvider(ap.id, ap.model)}
                class="w-full text-left px-3 py-2 text-xs hover:bg-black/5 flex items-center justify-between"
              >
                <span class="font-medium {ap.id === provider ? 'text-blue-600' : ''}">{ap.name}</span>
                <span style="color: var(--ink-faint);">{ap.model.length > 20 ? ap.model.slice(0, 20) + '...' : ap.model}</span>
              </button>
            {/each}
          </div>
        {/if}
      </div>

      <!-- Clear chat -->
      <button onclick={clearChat} class="text-xs px-2 py-1 rounded transition-all" style="color: var(--ink-faint);" title="Clear conversation">
        Clear
      </button>
    </div>
  </div>

  <!-- Chat messages area -->
  <div bind:this={chatContainer} class="flex-1 overflow-y-auto px-5 py-4">
    {#if messages.length === 0}
      <div class="flex flex-col items-center justify-center h-full text-center" style="color: var(--ink-muted);">
        <div class="text-4xl mb-4" style="font-family: var(--font-serif); color: var(--ink-faint);">Genealogy Research</div>
        <p class="text-sm max-w-md" style="color: var(--ink-muted);">
          Ask about your family tree, analyze records, break through brick walls, evaluate sources, or interpret DNA results.
        </p>
        <div class="mt-4 flex flex-wrap gap-2 justify-center max-w-lg">
          {#each [
            'What records should I search for a 1890s immigrant?',
            'Help me break through my brick wall on the Smith line',
            'Evaluate this source: 1920 US Census',
            'What does 45 shared cM mean?',
          ] as suggestion}
            <button
              onclick={() => { input = suggestion; }}
              class="px-3 py-1.5 text-xs rounded-lg border transition-all hover:bg-black/5"
              style="border-color: var(--border-subtle, rgba(0,0,0,0.1)); color: var(--ink-light);"
            >
              {suggestion}
            </button>
          {/each}
        </div>
        <p class="text-[10px] mt-6" style="color: var(--ink-faint);">
          Research Mode: {getModeLabel()} &middot; {provider} / {model}
        </p>
      </div>
    {:else}
      <!-- Mode label above messages -->
      <div class="text-center mb-4">
        <span class="inline-block px-3 py-1 text-[10px] font-semibold uppercase tracking-wider rounded-full" style="background: var(--parchment-deep, #f5f0e8); color: var(--ink-faint);">
          Research Mode: {getModeLabel()}
        </span>
      </div>

      <div class="space-y-4 max-w-3xl mx-auto">
        {#each messages as msg}
          <div class="{msg.role === 'user' ? 'flex justify-end' : 'flex justify-start'}">
            {#if msg.role === 'user'}
              <div class="max-w-[80%] px-4 py-2.5 rounded-2xl text-sm" style="background: var(--ink); color: var(--parchment, #fff);">
                {msg.content}
              </div>
            {:else}
              <div class="max-w-[85%] px-4 py-3 rounded-2xl text-sm leading-relaxed arch-card" style="font-family: var(--font-sans);">
                {@html renderMarkdown(msg.content)}
              </div>
            {/if}
          </div>
        {/each}
        {#if isLoading}
          <div class="flex justify-start">
            <div class="px-4 py-3 rounded-2xl arch-card text-sm" style="color: var(--ink-muted);">
              <span class="inline-flex gap-1">
                <span class="animate-pulse">Researching</span>
                <span class="animate-pulse" style="animation-delay: 0.2s;">.</span>
                <span class="animate-pulse" style="animation-delay: 0.4s;">.</span>
                <span class="animate-pulse" style="animation-delay: 0.6s;">.</span>
              </span>
            </div>
          </div>
        {/if}
        {#if error}
          <div class="max-w-3xl mx-auto px-4 py-2.5 rounded-xl text-xs" style="background: #fef2f2; color: #dc2626; border: 1px solid #fecaca;">
            {error}
          </div>
        {/if}
      </div>
    {/if}
  </div>

  <!-- Input area pinned to bottom -->
  <div class="shrink-0 border-t px-4 py-3" style="border-color: var(--border-subtle, rgba(0,0,0,0.06)); background: var(--parchment, #fff);">
    {#if selectedPerson}
      <div class="text-[10px] mb-1.5 px-1" style="color: var(--ink-faint);">
        Context: {selectedPerson.givenName} {selectedPerson.surname}
        {#if selectedPerson.birthDate} (b. {selectedPerson.birthDate}){/if}
        &middot; {selectedPersonEvents.length} events
      </div>
    {/if}
    <div class="flex gap-2 max-w-3xl mx-auto">
      <input
        bind:value={input}
        placeholder="Ask about your family tree..."
        class="flex-1 px-4 py-2.5 text-sm rounded-full border outline-none transition-all"
        style="background: var(--parchment, #fff); border-color: var(--border-subtle, rgba(0,0,0,0.12)); color: var(--ink);"
        onfocus={(e) => { const el = e.currentTarget as HTMLInputElement; el.style.borderColor = 'var(--ink-light)'; }}
        onblur={(e) => { const el = e.currentTarget as HTMLInputElement; el.style.borderColor = 'var(--border-subtle, rgba(0,0,0,0.12))'; }}
        onkeydown={(e) => e.key === 'Enter' && send()}
      />
      <button
        onclick={send}
        disabled={!input.trim() || isLoading}
        class="px-5 py-2.5 text-sm font-medium rounded-full transition-all disabled:opacity-40"
        style="background: var(--ink); color: var(--parchment, #fff);"
      >
        Send
      </button>
    </div>
  </div>
</div>
