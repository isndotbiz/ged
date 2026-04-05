<script lang="ts">
  import { t } from '$lib/i18n';
  import { getAllPersons, searchNotes, insertNote, deleteNote } from '$lib/db';
  import type { ResearchNote, Person } from '$lib/types';

  let notes = $state<ResearchNote[]>([]);
  let people = $state<Person[]>([]);
  let peopleByXref = $state<Record<string, Person>>({});
  let showEditor = $state(false);
  let title = $state('');
  let content = $state('');
  let personXref = $state('');
  let search = $state('');
  let searchDebounceTimer: ReturnType<typeof setTimeout>;
  let initialized = $state(false);

  async function load() { notes = await searchNotes(search); }
  async function loadPeople() {
    people = await getAllPersons();
    const map: Record<string, Person> = {};
    for (const p of people) map[p.xref] = p;
    peopleByXref = map;
  }

  async function create() {
    const now = new Date().toISOString();
    await insertNote({ ownerXref: personXref, ownerType: 'INDI', title, content, createdAt: now, updatedAt: now });
    showEditor = false; title = ''; content = ''; personXref = '';
    await load();
  }

  async function remove(id: number) { await deleteNote(id); await load(); }

  function onSearchInput() {
    clearTimeout(searchDebounceTimer);
    searchDebounceTimer = setTimeout(() => {
      load();
    }, 300);
  }

  function renderMarkdown(text: string): string {
    let html = text
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;');
    html = html.replace(/```([\s\S]*?)```/g, '<pre style="background:var(--parchment);padding:0.6rem;border-radius:0.4rem;overflow:auto;"><code>$1</code></pre>');
    html = html.replace(/`([^`]+)`/g, '<code style="background:var(--parchment);padding:0.05rem 0.3rem;border-radius:0.25rem;">$1</code>');
    html = html.replace(/\*\*([^*]+)\*\*/g, '<strong>$1</strong>');
    html = html.replace(/\*([^*]+)\*/g, '<em>$1</em>');
    html = html.replace(/^### (.+)$/gm, '<h4>$1</h4>');
    html = html.replace(/^## (.+)$/gm, '<h3>$1</h3>');
    html = html.replace(/^# (.+)$/gm, '<h2>$1</h2>');
    html = html.replace(/^[-*] (.+)$/gm, '<div>&bull; $1</div>');
    html = html.replace(/\n/g, '<br>');
    return html;
  }

  function ownerName(note: ResearchNote): string {
    if (!note.ownerXref) return '';
    const p = peopleByXref[note.ownerXref];
    return p ? `${p.givenName} ${p.surname}`.trim() : note.ownerXref;
  }

  $effect(() => {
    if (initialized) return;
    initialized = true;
    load();
    loadPeople();
  });
</script>

<div class="p-8 max-w-4xl animate-fade-in">
  <div class="flex items-center justify-between mb-8">
    <div>
      <h1 class="text-2xl font-bold tracking-tight" style="font-family: var(--font-serif); color: var(--ink);">{t('nav.notes')}</h1>
      <p class="text-sm text-ink-muted mt-1">{notes.length} notes</p>
    </div>
    <button onclick={() => showEditor = !showEditor} class="px-4 py-2 text-sm font-medium btn-accent">{showEditor ? 'Cancel' : 'New Note'}</button>
  </div>

  <div class="mb-4">
    <input bind:value={search} oninput={onSearchInput} placeholder={t('notes.searchNotes')} class="w-full px-3 py-2 text-sm arch-input"  aria-label={t('notes.searchNotes')} />
  </div>

  {#if showEditor}
    <div class="arch-card rounded-xl p-6 mb-6">
      <input bind:value={title} placeholder={t('common.title')} class="w-full px-3 py-2 text-sm arch-input mb-3"  aria-label={t('common.title')} />
      <select bind:value={personXref} class="w-full px-3 py-2 text-sm arch-input mb-3" aria-label={t('common.filter')}>
        <option value="">Link to person (optional)</option>
        {#each people as p}
          <option value={p.xref}>{p.givenName} {p.surname} ({p.xref})</option>
        {/each}
      </select>
      <textarea bind:value={content} placeholder={t('common.content')} class="w-full px-3 py-2 text-sm arch-input mb-4 h-32"></textarea>
      <button onclick={create} disabled={!title.trim()} class="px-4 py-2 text-sm font-medium btn-accent" aria-label={t('common.actions')}>{t('common.save')}</button>
    </div>
  {/if}

  <div class="space-y-3">
    {#each notes as note}
      <div class="arch-card rounded-xl p-4">
        <div class="flex items-center justify-between mb-2">
          <span class="font-medium text-sm text-ink">{note.title}</span>
          <button onclick={() => remove(note.id)} class="text-xs text-red-500 hover:text-red-700">Delete</button>
        </div>
        {#if note.ownerXref}
          <a href="/people/{encodeURIComponent(note.ownerXref)}" class="inline-flex mb-2 px-2 py-0.5 rounded-full text-[11px] no-underline hover:underline" style="background: var(--parchment); color: var(--accent);">
            {ownerName(note)}
          </a>
        {/if}
        <div class="text-xs text-ink-light markdown-note">{@html renderMarkdown(note.content)}</div>
        <div class="text-[10px] text-ink-faint mt-2">{new Date(note.updatedAt).toLocaleDateString()}</div>
      </div>
    {/each}
  </div>
</div>

<style>
  .markdown-note :global(h2),
  .markdown-note :global(h3),
  .markdown-note :global(h4) {
    margin: 0.35rem 0;
    font-family: var(--font-serif);
    color: var(--ink);
  }
</style>
