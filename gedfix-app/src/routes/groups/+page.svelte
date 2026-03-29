<script lang="ts">
  import { getGroups, insertGroup, deleteGroup, getGroupMembers, getGroupMemberCount, getPerson } from '$lib/db';
  import type { PersonGroup, Person } from '$lib/types';

  let groups = $state<(PersonGroup & { memberCount: number })[]>([]);
  let showEditor = $state(false);
  let selectedGroup = $state<number | null>(null);
  let members = $state<(Person | null)[]>([]);

  let newName = $state('');
  let newDesc = $state('');
  let newColor = $state('');

  const presetColors = ['#8B6914', '#4A6B8A', '#4A7C3F', '#B8860B', '#8A4A6B', '#6B4A8A', '#A63D2F', '#5A8A8A'];

  async function load() {
    const raw = await getGroups();
    groups = await Promise.all(raw.map(async g => ({ ...g, memberCount: await getGroupMemberCount(g.id) })));
  }

  async function createGroup() {
    await insertGroup({ name: newName, color: newColor, description: newDesc, createdAt: new Date().toISOString() });
    showEditor = false;
    newName = ''; newDesc = ''; newColor = '';
    await load();
  }

  async function removeGroup(id: number) {
    await deleteGroup(id);
    if (selectedGroup === id) selectedGroup = null;
    await load();
  }

  async function selectGroup(id: number) {
    selectedGroup = selectedGroup === id ? null : id;
    if (selectedGroup !== null) {
      const xrefs = await getGroupMembers(id);
      members = await Promise.all(xrefs.map(x => getPerson(x)));
    }
  }

  $effect(() => { load(); });
</script>

<div class="p-8 max-w-4xl animate-fade-in">
  <div class="flex items-center justify-between mb-8">
    <div>
      <h1 class="text-2xl font-bold tracking-tight" style="font-family: var(--font-serif); color: var(--ink);">Groups</h1>
      <p class="text-sm text-ink-muted mt-1">Organize people into custom groups for research</p>
    </div>
    <button onclick={() => showEditor = !showEditor} class="px-4 py-2 text-sm font-medium btn-accent text-white rounded-lg">
      {showEditor ? 'Cancel' : 'New Group'}
    </button>
  </div>

  {#if showEditor}
    <div class="arch-card rounded-xl p-6 mb-6">
      <input bind:value={newName} placeholder="Group Name" class="w-full px-3 py-2 text-sm border border-gray-200 rounded-lg mb-3 focus:outline-none focus:ring-2 focus:ring-amber-500/20" />
      <input bind:value={newDesc} placeholder="Description (optional)" class="w-full px-3 py-2 text-sm border border-gray-200 rounded-lg mb-3 focus:outline-none focus:ring-2 focus:ring-amber-500/20" />
      <div class="flex gap-2 mb-4">
        {#each presetColors as c}
          <button
            onclick={() => newColor = c}
            aria-label="Select color {c}"
            class="w-7 h-7 rounded-md border-2 transition-all {newColor === c ? 'border-gray-900 scale-110' : 'border-transparent'}"
            style="background: {c}"
          ></button>
        {/each}
      </div>
      <button onclick={createGroup} disabled={!newName.trim()} class="px-4 py-2 text-sm font-medium btn-accent text-white rounded-lg disabled:opacity-50">Create</button>
    </div>
  {/if}

  {#if groups.length === 0}
    <div class="arch-card rounded-xl p-8 text-center">
      <p class="text-ink-muted text-sm">No groups yet</p>
      <p class="text-ink-faint text-xs mt-1">e.g., "Civil War Veterans", "Brick Wall", "Unverified"</p>
    </div>
  {:else}
    <div class="space-y-3">
      {#each groups as group}
        <div class="w-full text-left arch-card rounded-xl p-4 hover:border-blue-200 transition-colors cursor-pointer {selectedGroup === group.id ? 'ring-2 ring-amber-500/20' : ''}" onclick={() => selectGroup(group.id)} role="button" tabindex="0" onkeydown={(e) => e.key === 'Enter' && selectGroup(group.id)}>
          <div class="flex items-center justify-between">
            <div class="flex items-center gap-3">
              {#if group.color}
                <div class="w-4 h-4 rounded" style="background: {group.color}"></div>
              {/if}
              <div>
                <div class="font-medium text-sm">{group.name}</div>
                {#if group.description}<div class="text-xs text-ink-muted">{group.description}</div>{/if}
              </div>
            </div>
            <div class="flex items-center gap-3">
              <span class="text-xs text-ink-faint">{group.memberCount} members</span>
              <button onclick={(e) => { e.stopPropagation(); removeGroup(group.id); }} class="text-xs text-red-500 hover:text-red-700">Delete</button>
            </div>
          </div>
        </div>

        {#if selectedGroup === group.id && members.length > 0}
          <div class="ml-4 rounded-lg p-3 space-y-1" style="background: var(--parchment);">
            {#each members as m}
              {#if m}
                <div class="text-sm text-ink-light">{m.givenName} {m.surname}</div>
              {/if}
            {/each}
          </div>
        {/if}
      {/each}
    </div>
  {/if}
</div>
