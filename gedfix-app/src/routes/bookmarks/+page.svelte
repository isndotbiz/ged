<script lang="ts">
  import { getBookmarks, deleteBookmark, getPerson } from '$lib/db';
  import type { Bookmark, Person } from '$lib/types';

  let bookmarks = $state<(Bookmark & { person: Person | null })[]>([]);

  async function load() {
    const raw = await getBookmarks();
    bookmarks = await Promise.all(raw.map(async b => ({ ...b, person: await getPerson(b.personXref) })));
  }

  async function remove(id: number) { await deleteBookmark(id); await load(); }

  $effect(() => { load(); });
</script>

<div class="p-8 max-w-4xl animate-fade-in">
  <h1 class="text-2xl font-bold tracking-tight" style="font-family: var(--font-serif); color: var(--ink);">Bookmarks</h1>
  <p class="text-sm text-ink-muted mt-1 mb-8">{bookmarks.length} bookmarked people</p>

  {#if bookmarks.length === 0}
    <div class="arch-card rounded-xl p-8 text-center">
      <p class="text-ink-muted text-sm">No bookmarks yet</p>
      <p class="text-ink-faint text-xs mt-1">Bookmark people from their detail page for quick access</p>
    </div>
  {:else}
    <div class="space-y-2">
      {#each bookmarks as bm}
        <div class="arch-card rounded-xl p-4 flex items-center justify-between">
          <div>
            {#if bm.person}
              <a href="/people/{encodeURIComponent(bm.personXref)}" class="font-medium text-sm hover:underline" style="color: var(--ink); text-decoration: none;">{bm.person.givenName} {bm.person.surname}</a>
            {:else}
              <a href="/people/{encodeURIComponent(bm.personXref)}" class="font-medium text-sm hover:underline" style="color: var(--ink); text-decoration: none;">{bm.personXref}</a>
            {/if}
            {#if bm.label}<div class="text-xs text-ink-muted">{bm.label}</div>{/if}
            <div class="text-[10px] text-ink-faint">{new Date(bm.createdAt).toLocaleDateString()}</div>
          </div>
          <button onclick={() => remove(bm.id)} class="text-xs text-red-500 hover:text-red-700">Remove</button>
        </div>
      {/each}
    </div>
  {/if}
</div>
