<script lang="ts">
  import { getFamilies, getPerson, getChildren, getEvents } from '$lib/db';
  import type { Person, Family, GedcomEvent } from '$lib/types';

  interface FamilyRow {
    family: Family;
    partner1: Person | null;
    partner2: Person | null;
    childCount: number;
    marriageEvent: GedcomEvent | null;
  }

  let families = $state<FamilyRow[]>([]);
  let search = $state('');
  let loading = $state(true);
  let expandedXref = $state<string | null>(null);
  let childrenCache = $state<Map<string, Person[]>>(new Map());
  let loadingChildren = $state<string | null>(null);

  async function load() {
    loading = true;
    const fams = await getFamilies();
    const rows: FamilyRow[] = [];
    for (const f of fams) {
      const [p1, p2, children, events] = await Promise.all([
        f.partner1Xref ? getPerson(f.partner1Xref) : Promise.resolve(null),
        f.partner2Xref ? getPerson(f.partner2Xref) : Promise.resolve(null),
        getChildren(f.xref),
        getEvents(f.xref),
      ]);
      const marriageEvent = events.find(e => e.eventType === 'MARR') ?? null;
      rows.push({ family: f, partner1: p1, partner2: p2, childCount: children.length, marriageEvent });
    }
    families = rows;
    loading = false;
  }

  let filtered = $derived(search.trim()
    ? families.filter(r => {
        const q = search.toLowerCase();
        const n1 = r.partner1 ? `${r.partner1.givenName} ${r.partner1.surname}`.toLowerCase() : '';
        const n2 = r.partner2 ? `${r.partner2.givenName} ${r.partner2.surname}`.toLowerCase() : '';
        const place = (r.family.marriagePlace ?? '').toLowerCase();
        return n1.includes(q) || n2.includes(q) || r.family.xref.toLowerCase().includes(q) || place.includes(q);
      })
    : families);

  function personName(p: Person | null): string {
    if (!p) return '(unknown)';
    return `${p.givenName ?? ''} ${p.surname ?? ''}`.trim() || p.xref;
  }

  async function toggleExpand(xref: string) {
    if (expandedXref === xref) {
      expandedXref = null;
      return;
    }
    expandedXref = xref;
    if (!childrenCache.has(xref)) {
      loadingChildren = xref;
      const children = await getChildren(xref);
      const updated = new Map(childrenCache);
      updated.set(xref, children);
      childrenCache = updated;
      loadingChildren = null;
    }
  }


  $effect(() => { load(); });
</script>

<div class="p-8 max-w-5xl animate-fade-in">
  <div class="mb-6">
    <h1 class="text-2xl font-bold tracking-tight" style="font-family: var(--font-serif); color: var(--ink);">Families</h1>
    <p class="text-sm mt-1" style="color: var(--ink-muted);">{families.length} family records</p>
  </div>

  <div class="mb-4">
    <input
      type="text"
      placeholder="Search by name, place, or xref..."
      bind:value={search}
      class="w-full max-w-sm px-3 py-2 text-sm rounded-lg border-none outline-none transition-colors arch-input"
    />
  </div>

  {#if loading}
    <div class="text-sm py-8 text-center" style="color: var(--ink-faint);">Loading families...</div>
  {:else}
    <div class="arch-card rounded-xl overflow-hidden">
      {#each filtered as row, i}
        {@const isExpanded = expandedXref === row.family.xref}
        {@const children = childrenCache.get(row.family.xref) ?? []}
        {@const isLoadingThis = loadingChildren === row.family.xref}

        <div class:border-t={i > 0} style="border-color: var(--parchment-dark, rgba(0,0,0,0.06));">
          <!-- Family header row -->
          <button
            class="w-full flex items-center gap-3 px-5 py-3.5 text-left transition-colors cursor-pointer"
            style="background: {isExpanded ? 'var(--parchment)' : 'transparent'};"
            onclick={() => toggleExpand(row.family.xref)}
            onmouseenter={(e) => e.currentTarget.style.background = isExpanded ? 'var(--parchment)' : 'rgba(0,0,0,0.02)'}
            onmouseleave={(e) => e.currentTarget.style.background = isExpanded ? 'var(--parchment)' : 'transparent'}
          >
            <!-- Expand chevron -->
            <span
              class="text-xs shrink-0 transition-transform duration-200"
              style="color: var(--ink-faint); transform: rotate({isExpanded ? '90deg' : '0deg'});"
            >&#9654;</span>

            <!-- Partner names -->
            <div class="flex items-center gap-2 flex-1 min-w-0">
              {#if row.partner1}
                <a
                  href="/people/{encodeURIComponent(row.partner1.xref)}"
                  class="text-sm font-medium truncate underline-offset-2 hover:underline"
                  style="color: var(--ink); text-decoration: none;"
                  onclick={(e) => e.stopPropagation()}
                >{personName(row.partner1)}</a>
              {:else}
                <span class="text-sm font-medium truncate" style="color: var(--ink);">{personName(row.partner1)}</span>
              {/if}
              <span class="text-xs" style="color: var(--ink-faint);">&amp;</span>
              {#if row.partner2}
                <a
                  href="/people/{encodeURIComponent(row.partner2.xref)}"
                  class="text-sm font-medium truncate underline-offset-2 hover:underline"
                  style="color: var(--ink); text-decoration: none;"
                  onclick={(e) => e.stopPropagation()}
                >{personName(row.partner2)}</a>
              {:else}
                <span class="text-sm font-medium truncate" style="color: var(--ink);">{personName(row.partner2)}</span>
              {/if}
            </div>

            <!-- Marriage info -->
            {#if row.family.marriageDate || row.marriageEvent?.dateValue}
              <span class="text-xs shrink-0" style="color: var(--ink-muted);">
                m. {row.family.marriageDate || row.marriageEvent?.dateValue}
              </span>
            {/if}

            <!-- Child count badge -->
            {#if row.childCount > 0}
              <span class="text-xs px-2 py-0.5 rounded-full shrink-0" style="background: var(--parchment); color: var(--ink-muted);">
                {row.childCount} {row.childCount === 1 ? 'child' : 'children'}
              </span>
            {/if}

            <!-- Xref -->
            <span class="text-xs shrink-0" style="color: var(--ink-faint);">{row.family.xref}</span>
          </button>

          <!-- Expanded detail panel -->
          {#if isExpanded}
            <div class="px-5 pb-4 pt-1" style="background: var(--parchment);">
              <!-- Marriage details -->
              {#if row.family.marriageDate || row.family.marriagePlace || row.marriageEvent}
                <div class="mb-3 px-3 py-2 rounded-lg" style="background: rgba(255,255,255,0.5);">
                  <div class="text-xs font-semibold uppercase tracking-wider mb-1" style="color: var(--ink-faint);">Marriage</div>
                  <div class="flex flex-wrap gap-x-6 gap-y-1">
                    {#if row.family.marriageDate || row.marriageEvent?.dateValue}
                      <div class="text-sm" style="color: var(--ink);">
                        <span style="color: var(--ink-muted);">Date:</span> {row.family.marriageDate || row.marriageEvent?.dateValue}
                      </div>
                    {/if}
                    {#if row.family.marriagePlace || row.marriageEvent?.place}
                      <div class="text-sm" style="color: var(--ink);">
                        <span style="color: var(--ink-muted);">Place:</span> {row.family.marriagePlace || row.marriageEvent?.place}
                      </div>
                    {/if}
                  </div>
                </div>
              {/if}

              <!-- Children list -->
              <div class="px-3 py-2 rounded-lg" style="background: rgba(255,255,255,0.5);">
                <div class="text-xs font-semibold uppercase tracking-wider mb-2" style="color: var(--ink-faint);">
                  Children ({row.childCount})
                </div>
                {#if isLoadingThis}
                  <div class="text-xs py-2" style="color: var(--ink-faint);">Loading children...</div>
                {:else if children.length === 0}
                  <div class="text-xs py-2" style="color: var(--ink-faint);">No children recorded</div>
                {:else}
                  <div class="space-y-1">
                    {#each children as child}
                      <a
                        href="/people/{encodeURIComponent(child.xref)}"
                        class="w-full flex items-center gap-3 px-2 py-1.5 rounded-md text-left transition-colors cursor-pointer hover:underline"
                        style="background: transparent; color: var(--ink); text-decoration: none;"
                        onmouseenter={(e) => e.currentTarget.style.background = 'rgba(0,0,0,0.03)'}
                        onmouseleave={(e) => e.currentTarget.style.background = 'transparent'}
                      >
                        <!-- Sex indicator -->
                        <span class="text-xs w-4 text-center shrink-0" style="color: var(--ink-faint);">
                          {child.sex === 'M' ? '\u2642' : child.sex === 'F' ? '\u2640' : '\u25CB'}
                        </span>
                        <span class="text-sm font-medium" style="color: var(--ink);">
                          {personName(child)}
                        </span>
                        {#if child.birthDate}
                          <span class="text-xs" style="color: var(--ink-muted);">b. {child.birthDate}</span>
                        {/if}
                        {#if child.deathDate}
                          <span class="text-xs" style="color: var(--ink-faint);">d. {child.deathDate}</span>
                        {/if}
                      </a>
                    {/each}
                  </div>
                {/if}
              </div>
            </div>
          {/if}
        </div>
      {/each}
      {#if filtered.length === 0}
        <div class="p-6 text-center text-sm" style="color: var(--ink-faint);">No families found</div>
      {/if}
    </div>
  {/if}
</div>
