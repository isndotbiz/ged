<script lang="ts">
  import { goto } from '$app/navigation';
  import { getChildren, getPerson, getPersons, getSpouseFamilies } from '$lib/db';
  import type { Person } from '$lib/types';

  type DescNode = {
    person: Person;
    spouseName: string;
    children: DescNode[];
    collapsed: boolean;
    depth: number;
  };

  let people = $state.raw<Person[]>([]);
  let selectedXref = $state('');
  let maxDepth = $state(4);
  let loading = $state(false);
  let root = $state<DescNode | null>(null);

  async function loadPeople() {
    people = await getPersons('', 5000);
    if (!selectedXref && people.length > 0) selectedXref = people[0].xref;
  }

  async function buildNode(person: Person, depth: number): Promise<DescNode> {
    const node: DescNode = { person, spouseName: '', children: [], collapsed: false, depth };
    if (depth >= maxDepth) return node;

    const fams = await getSpouseFamilies(person.xref);
    const children: DescNode[] = [];
    for (const fam of fams) {
      const spouseXref = fam.partner1Xref === person.xref ? fam.partner2Xref : fam.partner1Xref;
      if (spouseXref) {
        const spouse = await getPerson(spouseXref);
        node.spouseName = spouse ? `${spouse.givenName} ${spouse.surname}`.trim() : '';
      }
      const kids = await getChildren(fam.xref);
      for (const child of kids) children.push(await buildNode(child, depth + 1));
    }
    node.children = children;
    return node;
  }

  async function loadTree() {
    if (!selectedXref) return;
    loading = true;
    const person = await getPerson(selectedXref);
    root = person ? await buildNode(person, 0) : null;
    loading = false;
  }

  function nameFor(p: Person): string {
    return `${p.givenName} ${p.surname}`.trim() || p.xref;
  }

  function yearsFor(p: Person): string {
    const b = p.birthDate.match(/(\d{4})/)?.[1] ?? '?';
    const d = p.deathDate.match(/(\d{4})/)?.[1] ?? (p.isLiving ? 'living' : '?');
    return `${b} - ${d}`;
  }

  function generationColor(depth: number): string {
    const palette = ['var(--color-blue)', 'var(--color-cyan)', '#58c783', 'var(--color-accent)', '#f5b45f'];
    return palette[depth % palette.length];
  }

  function toggle(node: DescNode): void {
    node.collapsed = !node.collapsed;
  }

  $effect(() => {
    loadPeople();
  });

  $effect(() => {
    if (selectedXref) loadTree();
  });
</script>

<div class="p-6 descendants-page">
  <div class="flex items-end gap-3 mb-4 no-print">
    <div class="flex-1">
      <label class="text-xs text-ink-muted" for="root-person">Root person</label>
      <select id="root-person" class="arch-input w-full" bind:value={selectedXref}>
        {#each people as person}
          <option value={person.xref}>{nameFor(person)}</option>
        {/each}
      </select>
    </div>
    <div>
      <label class="text-xs text-ink-muted" for="depth">Depth</label>
      <select id="depth" class="arch-input" bind:value={maxDepth}>
        {#each Array.from({ length: 10 }, (_, i) => i + 1) as d}
          <option value={d}>{d}</option>
        {/each}
      </select>
    </div>
    <button class="btn-secondary px-3 py-2" onclick={() => window.print()} aria-label="Print descendants chart">Print</button>
  </div>

  {#if loading}
    <p class="text-ink-muted">Loading descendants...</p>
  {:else if root}
    <h1 class="text-xl font-bold mb-4" tabindex="-1">Descendants of {nameFor(root.person)}</h1>
    <div class="tree-root">
      {#snippet renderNode(node: DescNode)}
        <div class="node-wrap">
          <div class="node" style="border-color: {generationColor(node.depth)};">
            <button class="name-link" onclick={() => goto(`/people/${node.person.xref}`)}>{nameFor(node.person)}</button>
            <div class="years">{yearsFor(node.person)}</div>
            {#if node.spouseName}
              <div class="spouse">+ {node.spouseName}</div>
            {/if}
            {#if node.children.length > 0}
              <button class="collapse" onclick={() => toggle(node)}>{node.collapsed ? '+' : '-'}</button>
            {/if}
          </div>
          {#if node.children.length > 0 && !node.collapsed}
            <div class="children">
              {#each node.children as child}
                <div class="child-branch">
                  <div class="connector"></div>
                  {@render renderNode(child)}
                </div>
              {/each}
            </div>
          {/if}
        </div>
      {/snippet}

      {@render renderNode(root)}
    </div>
  {/if}
</div>

<style>
  .tree-root { overflow: auto; padding-bottom: 2rem; }
  .node-wrap { text-align: center; }
  .node {
    min-width: 180px;
    display: inline-flex;
    flex-direction: column;
    gap: 0.2rem;
    border: 2px solid var(--border-rule);
    background: var(--vellum);
    border-radius: 0.5rem;
    padding: 0.5rem;
    position: relative;
  }
  .name-link { background: transparent; border: 0; color: var(--ink); font-weight: 700; cursor: pointer; }
  .years, .spouse { font-size: 0.78rem; color: var(--ink-muted); }
  .children { display: flex; justify-content: center; gap: 1rem; margin-top: 1rem; }
  .child-branch { display: flex; flex-direction: column; align-items: center; }
  .connector { width: 2px; height: 14px; background: var(--border-rule); margin-bottom: 0.4rem; }
  .collapse {
    position: absolute;
    top: 0.35rem;
    right: 0.35rem;
    border: 1px solid var(--border-rule);
    background: var(--paper);
    color: var(--ink);
    border-radius: 0.25rem;
    width: 22px;
    height: 22px;
    line-height: 1;
  }

  @media print {
    @page { size: landscape; }
    .tree-root { overflow: visible; }
    .node { border: 1px solid #000 !important; background: #fff !important; color: #000 !important; }
    .years, .spouse, .name-link { color: #000 !important; }
  }
</style>
