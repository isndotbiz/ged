<script lang="ts">
  import { t } from '$lib/i18n';
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
  let sortDir = $state<'asc' | 'desc'>('desc');
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

  function marriageYear(row: FamilyRow): number {
    const raw = row.family.marriageDate || row.marriageEvent?.dateValue || '';
    const match = raw.match(/\b(\d{4})\b/);
    return match ? Number(match[1]) : 0;
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

  let sorted = $derived(
    [...filtered].sort((a, b) => {
      const ay = marriageYear(a);
      const by = marriageYear(b);
      if (ay === by) return 0;
      if (ay === 0) return 1;
      if (by === 0) return -1;
      return sortDir === 'desc' ? by - ay : ay - by;
    })
  );

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
    <h1 class="text-2xl font-bold tracking-tight display-gradient-spirit">{t('dashboard.families')}</h1>
    <p class="text-sm mt-1" style="color: var(--color-muted);">{families.length} family records</p>
  </div>

  <div class="mb-4 flex flex-wrap items-center gap-3">
    <input
      type="text"
      placeholder={t('families.searchPlaceholder')}
      bind:value={search}
      class="w-full max-w-sm px-3 py-2 text-sm rounded-lg border-none outline-none transition-colors arch-input"
     aria-label={t('families.searchPlaceholder')} />
    <button class="btn-outline text-xs" onclick={() => sortDir = sortDir === 'desc' ? 'asc' : 'desc'}>
      Sort {sortDir === 'desc' ? 'Newest' : 'Oldest'}
    </button>
  </div>

  {#if loading}
    <div class="text-sm py-8 text-center" style="color: var(--ink-faint);">Loading families...</div>
  {:else}
    <div class="arch-card rounded-xl overflow-hidden">
      {#each sorted as row, i}
        {@const isExpanded = expandedXref === row.family.xref}
        {@const children = childrenCache.get(row.family.xref) ?? []}
        {@const isLoadingThis = loadingChildren === row.family.xref}

        <div class:border-t={i > 0} style="border-color: rgba(30, 159, 242, 0.15);">
          <!-- Family header row -->
          <button
            class="w-full flex items-center gap-3 px-5 py-3.5 text-left transition-colors cursor-pointer"
            style="background: {isExpanded ? 'var(--color-steel)' : 'transparent'};"
            onclick={() => toggleExpand(row.family.xref)}
            onmouseenter={(e) => e.currentTarget.style.background = isExpanded ? 'var(--color-steel)' : 'rgba(30, 159, 242, 0.08)'}
            onmouseleave={(e) => e.currentTarget.style.background = isExpanded ? 'var(--color-steel)' : 'transparent'}
          >
            <!-- Expand chevron -->
            <span
              class="text-xs shrink-0 transition-transform duration-200"
              style="color: var(--color-muted); transform: rotate({isExpanded ? '90deg' : '0deg'});"
            >&#9654;</span>

            <!-- Partner names -->
            <div class="flex items-center gap-2 flex-1 min-w-0">
              {#if row.partner1}
                <a
                  href="/people/{encodeURIComponent(row.partner1.xref)}"
                  class="text-sm font-medium truncate underline-offset-2 hover:underline"
                  style="color: var(--color-white); text-decoration: none;"
                  onclick={(e) => e.stopPropagation()}
                >{personName(row.partner1)}</a>
              {:else}
                <span class="text-sm font-medium truncate" style="color: var(--color-white);">{personName(row.partner1)}</span>
              {/if}
              <span class="text-xs" style="color: var(--color-muted);">&amp;</span>
              {#if row.partner2}
                <a
                  href="/people/{encodeURIComponent(row.partner2.xref)}"
                  class="text-sm font-medium truncate underline-offset-2 hover:underline"
                  style="color: var(--color-white); text-decoration: none;"
                  onclick={(e) => e.stopPropagation()}
                >{personName(row.partner2)}</a>
              {:else}
                <span class="text-sm font-medium truncate" style="color: var(--color-white);">{personName(row.partner2)}</span>
              {/if}
            </div>

            <!-- Marriage info -->
            {#if row.family.marriageDate || row.marriageEvent?.dateValue}
              <span class="text-xs shrink-0" style="color: var(--color-muted);">
                m. {row.family.marriageDate || row.marriageEvent?.dateValue}
              </span>
            {/if}

            <!-- Child count badge -->
            {#if row.childCount > 0}
              <span class="text-xs px-2 py-0.5 rounded-full shrink-0" style="background: var(--color-steel); color: var(--color-muted);">
                {row.childCount} {row.childCount === 1 ? 'child' : 'children'}
              </span>
            {/if}

            <!-- Xref -->
            <span class="text-xs shrink-0" style="color: var(--color-muted);">{row.family.xref}</span>
          </button>

          <!-- Expanded detail panel -->
          {#if isExpanded}
            <div class="px-5 pb-4 pt-1" style="background: var(--color-steel);">
              <!-- Marriage details -->
              {#if row.family.marriageDate || row.family.marriagePlace || row.marriageEvent}
                <div class="mb-3 px-3 py-2 rounded-lg" style="background: rgba(13, 17, 23, 0.55); border: 1px solid rgba(30, 159, 242, 0.15);">
                  <div class="text-xs font-semibold uppercase tracking-wider mb-1" style="color: var(--color-muted);">{t('families.marriage')}</div>
                  <div class="flex flex-wrap gap-x-6 gap-y-1">
                    {#if row.family.marriageDate || row.marriageEvent?.dateValue}
                      <div class="text-sm" style="color: var(--color-white);">
                        <span style="color: var(--color-muted);">Date:</span> {row.family.marriageDate || row.marriageEvent?.dateValue}
                      </div>
                    {/if}
                    {#if row.family.marriagePlace || row.marriageEvent?.place}
                      <div class="text-sm" style="color: var(--color-white);">
                        <span style="color: var(--color-muted);">{t('map.place')}</span> {row.family.marriagePlace || row.marriageEvent?.place}
                      </div>
                    {/if}
                  </div>
                </div>
              {/if}

              <!-- Children list -->
              <div class="px-3 py-2 rounded-lg" style="background: rgba(13, 17, 23, 0.55); border: 1px solid rgba(30, 159, 242, 0.15);">
                <div class="text-xs font-semibold uppercase tracking-wider mb-2" style="color: var(--color-muted);">
                  Children ({row.childCount})
                </div>
                {#if isLoadingThis}
                  <div class="text-xs py-2" style="color: var(--color-muted);">Loading children...</div>
                {:else if children.length === 0}
                  <div class="text-xs py-2" style="color: var(--color-muted);">{t('families.noChildren')}</div>
                {:else}
                  <div class="space-y-1">
                    {#each children as child}
                      <a
                        href="/people/{encodeURIComponent(child.xref)}"
                        class="w-full flex items-center gap-3 px-2 py-1.5 rounded-md text-left transition-colors cursor-pointer hover:underline"
                        style="background: transparent; color: var(--color-white); text-decoration: none;"
                        onmouseenter={(e) => e.currentTarget.style.background = 'rgba(30, 159, 242, 0.12)'}
                        onmouseleave={(e) => e.currentTarget.style.background = 'transparent'}
                      >
                        <!-- Sex indicator -->
                        <span class="text-xs w-4 text-center shrink-0" style="color: var(--color-muted);">
                          {child.sex === 'M' ? '\u2642' : child.sex === 'F' ? '\u2640' : '\u25CB'}
                        </span>
                        <span class="text-sm font-medium" style="color: var(--color-white);">
                          {personName(child)}
                        </span>
                        {#if child.birthDate}
                          <span class="text-xs" style="color: var(--color-muted);">b. {child.birthDate}</span>
                        {/if}
                        {#if child.deathDate}
                          <span class="text-xs" style="color: var(--color-muted);">d. {child.deathDate}</span>
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
        <div class="p-6 text-center text-sm" style="color: var(--color-muted);">{t('families.noFamiliesFound')}</div>
      {/if}
    </div>
  {/if}
</div>
