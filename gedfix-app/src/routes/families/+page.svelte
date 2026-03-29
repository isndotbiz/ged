<script lang="ts">
  import { getFamilies, getPerson, getChildren } from '$lib/db';
  import type { Person, Family } from '$lib/types';

  interface FamilyRow {
    family: Family;
    partner1: Person | null;
    partner2: Person | null;
    childCount: number;
  }

  let families = $state<FamilyRow[]>([]);
  let search = $state('');
  let loading = $state(true);

  async function load() {
    loading = true;
    const fams = await getFamilies();
    const rows: FamilyRow[] = [];
    for (const f of fams) {
      const p1 = f.partner1Xref ? await getPerson(f.partner1Xref) : null;
      const p2 = f.partner2Xref ? await getPerson(f.partner2Xref) : null;
      const children = await getChildren(f.xref);
      rows.push({ family: f, partner1: p1, partner2: p2, childCount: children.length });
    }
    families = rows;
    loading = false;
  }

  let filtered = $derived(search.trim()
    ? families.filter(r => {
        const q = search.toLowerCase();
        const n1 = r.partner1 ? `${r.partner1.givenName} ${r.partner1.surname}`.toLowerCase() : '';
        const n2 = r.partner2 ? `${r.partner2.givenName} ${r.partner2.surname}`.toLowerCase() : '';
        return n1.includes(q) || n2.includes(q) || r.family.xref.toLowerCase().includes(q);
      })
    : families);

  function personName(p: Person | null): string {
    if (!p) return '(unknown)';
    return `${p.givenName} ${p.surname}`.trim() || p.xref;
  }

  $effect(() => { load(); });
</script>

<div class="p-8 max-w-4xl animate-fade-in">
  <div class="mb-6">
    <h1 class="text-2xl font-bold tracking-tight" style="font-family: var(--font-serif); color: var(--ink);">Families</h1>
    <p class="text-sm text-ink-muted mt-1">{families.length} family records</p>
  </div>

  <div class="mb-4">
    <input
      type="text"
      placeholder="Search families..."
      bind:value={search}
      class="w-full max-w-sm px-3 py-2 text-sm rounded-lg border-none outline-none transition-colors arch-input"
    />
  </div>

  {#if loading}
    <div class="text-sm text-ink-faint py-8 text-center">Loading families...</div>
  {:else}
    <div class="arch-card rounded-xl divide-y arch-card-divide">
      {#each filtered as row}
        <div class="flex items-center gap-4 px-5 py-3.5 hover:bg-black/[0.02] transition-colors">
          <div class="flex items-center gap-2 flex-1 min-w-0">
            <span class="text-sm font-medium text-ink truncate">{personName(row.partner1)}</span>
            <span class="text-xs text-ink-faint">&</span>
            <span class="text-sm font-medium text-ink truncate">{personName(row.partner2)}</span>
          </div>
          {#if row.family.marriageDate}
            <span class="text-xs text-ink-muted shrink-0">m. {row.family.marriageDate}</span>
          {/if}
          {#if row.childCount > 0}
            <span class="text-xs text-ink-faint px-2 py-0.5 rounded-full shrink-0" style="background: var(--parchment);">
              {row.childCount} {row.childCount === 1 ? 'child' : 'children'}
            </span>
          {/if}
          <span class="text-xs text-ink-faint shrink-0">{row.family.xref}</span>
        </div>
      {/each}
      {#if filtered.length === 0}
        <div class="p-6 text-center text-sm text-ink-faint">No families found</div>
      {/if}
    </div>
  {/if}
</div>
