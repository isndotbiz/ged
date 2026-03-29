<script lang="ts">
  import { getNotes, insertNote, deleteNote } from '$lib/db';
  import type { ResearchNote } from '$lib/types';

  let notes = $state<ResearchNote[]>([]);
  let showEditor = $state(false);
  let title = $state('');
  let content = $state('');

  async function load() { notes = await getNotes(); }

  async function create() {
    const now = new Date().toISOString();
    await insertNote({ ownerXref: '', ownerType: 'INDI', title, content, createdAt: now, updatedAt: now });
    showEditor = false; title = ''; content = '';
    await load();
  }

  async function remove(id: number) { await deleteNote(id); await load(); }

  $effect(() => { load(); });
</script>

<div class="p-8 max-w-4xl animate-fade-in">
  <div class="flex items-center justify-between mb-8">
    <div>
      <h1 class="text-2xl font-bold tracking-tight" style="font-family: var(--font-serif); color: var(--ink);">Notes</h1>
      <p class="text-sm text-ink-muted mt-1">{notes.length} notes</p>
    </div>
    <button onclick={() => showEditor = !showEditor} class="px-4 py-2 text-sm font-medium btn-accent">{showEditor ? 'Cancel' : 'New Note'}</button>
  </div>

  {#if showEditor}
    <div class="arch-card rounded-xl p-6 mb-6">
      <input bind:value={title} placeholder="Title" class="w-full px-3 py-2 text-sm arch-input mb-3" />
      <textarea bind:value={content} placeholder="Content" class="w-full px-3 py-2 text-sm arch-input mb-4 h-32"></textarea>
      <button onclick={create} disabled={!title.trim()} class="px-4 py-2 text-sm font-medium btn-accent">Save</button>
    </div>
  {/if}

  <div class="space-y-3">
    {#each notes as note}
      <div class="arch-card rounded-xl p-4">
        <div class="flex items-center justify-between mb-2">
          <span class="font-medium text-sm text-ink">{note.title}</span>
          <button onclick={() => remove(note.id)} class="text-xs text-red-500 hover:text-red-700">Delete</button>
        </div>
        <div class="text-xs text-ink-light whitespace-pre-wrap">{note.content}</div>
        <div class="text-[10px] text-ink-faint mt-2">{new Date(note.updatedAt).toLocaleDateString()}</div>
      </div>
    {/each}
  </div>
</div>
