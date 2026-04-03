<script lang="ts">
  import { t } from '$lib/i18n';
  import { getBookmarks, deleteBookmark, getPerson, updateBookmarkCategory, reorderBookmarks } from '$lib/db';
  import type { Bookmark, Person } from '$lib/types';

  let bookmarks = $state<(Bookmark & { person: Person | null })[]>([]);
  let activeCategory = $state('All');
  let newCategory = $state('');
  let draggingId = $state<number | null>(null);

  async function load() {
    const raw = await getBookmarks();
    bookmarks = await Promise.all(raw.map(async b => ({ ...b, person: await getPerson(b.personXref) })));
  }

  async function remove(id: number) { await deleteBookmark(id); await load(); }
  async function changeCategory(id: number, category: string) {
    await updateBookmarkCategory(id, category);
    await load();
  }

  function onDragStart(id: number) {
    draggingId = id;
  }

  async function onDrop(targetId: number) {
    if (!draggingId || draggingId === targetId) return;
    const ids = bookmarks.map((b) => b.id);
    const from = ids.indexOf(draggingId);
    const to = ids.indexOf(targetId);
    if (from < 0 || to < 0) return;
    ids.splice(from, 1);
    ids.splice(to, 0, draggingId);
    await reorderBookmarks(ids);
    draggingId = null;
    await load();
  }

  let categories = $derived(['All', ...new Set(bookmarks.map((b) => b.category || 'General'))]);
  let visible = $derived(activeCategory === 'All' ? bookmarks : bookmarks.filter((b) => (b.category || 'General') === activeCategory));

  $effect(() => { load(); });
</script>

<div class="p-8 max-w-4xl animate-fade-in">
  <h1 class="text-2xl font-bold tracking-tight" style="font-family: var(--font-serif); color: var(--ink);">{t('nav.bookmarks')}</h1>
  <p class="text-sm text-ink-muted mt-1 mb-8">{bookmarks.length} bookmarked people</p>

  <div class="flex flex-wrap gap-2 mb-4 items-center">
    {#each categories as c}
      <button class="px-3 py-1 text-xs rounded-full {activeCategory === c ? 'btn-filter-active' : 'btn-filter'}" onclick={() => activeCategory = c}>{c}</button>
    {/each}
    <input class="arch-input px-3 py-1 text-xs" placeholder="New category..." bind:value={newCategory} />
  </div>

  {#if bookmarks.length === 0}
    <div class="arch-card rounded-xl p-8 text-center">
      <p class="text-ink-muted text-sm">No bookmarks yet</p>
      <p class="text-ink-faint text-xs mt-1">Bookmark people from their detail page for quick access</p>
    </div>
  {:else}
    <div class="space-y-2">
      {#each visible as bm}
        <div
          class="arch-card rounded-xl p-4 flex items-center justify-between"
          role="listitem"
          draggable="true"
          ondragstart={() => onDragStart(bm.id)}
          ondragover={(e) => e.preventDefault()}
          ondrop={() => onDrop(bm.id)}
        >
          <div>
            {#if bm.person}
              <a href="/people/{encodeURIComponent(bm.personXref)}" class="font-medium text-sm hover:underline" style="color: var(--ink); text-decoration: none;">{bm.person.givenName} {bm.person.surname}</a>
            {:else}
              <a href="/people/{encodeURIComponent(bm.personXref)}" class="font-medium text-sm hover:underline" style="color: var(--ink); text-decoration: none;">{bm.personXref}</a>
            {/if}
            {#if bm.label}<div class="text-xs text-ink-muted">{bm.label}</div>{/if}
            <div class="mt-1">
              <select class="arch-input text-[11px] px-2 py-1" value={bm.category || 'General'} onchange={(e) => changeCategory(bm.id, (e.currentTarget as HTMLSelectElement).value)}>
                {#each categories.filter((c) => c !== 'All') as c}
                  <option value={c}>{c}</option>
                {/each}
                {#if newCategory.trim()}
                  <option value={newCategory.trim()}>{newCategory.trim()}</option>
                {/if}
              </select>
            </div>
            <div class="text-[10px] text-ink-faint">{new Date(bm.createdAt).toLocaleDateString()}</div>
          </div>
          <button onclick={() => remove(bm.id)} class="text-xs text-red-500 hover:text-red-700">Remove</button>
        </div>
      {/each}
    </div>
  {/if}
</div>
