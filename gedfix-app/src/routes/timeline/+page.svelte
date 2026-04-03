<script lang="ts">
  import { t } from '$lib/i18n';
  import { getAllEvents, getPerson } from '$lib/db';
  import type { GedcomEvent, Person } from '$lib/types';

  interface TimelineItem {
    event: GedcomEvent;
    person: Person | null;
    year: number;
  }

  let items = $state<TimelineItem[]>([]);
  let loading = $state(true);
  let filterType = $state('');
  let filterPerson = $state('');

  const eventLabels: Record<string, string> = {
    BIRT: 'Birth', DEAT: 'Death', BURI: 'Burial', CHR: 'Christening',
    BAPM: 'Baptism', MARR: 'Marriage', DIV: 'Divorce', RESI: 'Residence',
    OCCU: 'Occupation', EDUC: 'Education', EMIG: 'Emigration', IMMI: 'Immigration',
    NATU: 'Naturalization', CENS: 'Census', PROB: 'Probate', WILL: 'Will', EVEN: 'Event',
  };

  const eventColors: Record<string, string> = {
    BIRT: '#34C759', DEAT: '#8E8E93', BURI: '#636366', CHR: '#AF52DE',
    BAPM: '#AF52DE', MARR: '#FF2D55', DIV: '#FF9500', RESI: '#4A6B8A',
    OCCU: '#5856D6', CENS: '#00C7BE', EVEN: '#8B6914',
  };

  function parseYear(d: string): number | null {
    const m = d.match(/(\d{4})/);
    return m ? parseInt(m[1]) : null;
  }

  async function load() {
    loading = true;
    const events = await getAllEvents();

    const timelineItems: TimelineItem[] = [];
    const personCache = new Map<string, Person | null>();

    for (const ev of events) {
      const year = parseYear(ev.dateValue);
      if (!year) continue;

      if (!personCache.has(ev.ownerXref)) {
        personCache.set(ev.ownerXref, await getPerson(ev.ownerXref));
      }

      timelineItems.push({
        event: ev,
        person: personCache.get(ev.ownerXref) ?? null,
        year,
      });
    }

    timelineItems.sort((a, b) => a.year - b.year);
    items = timelineItems;
    loading = false;
  }

  let eventTypes = $derived([...new Set(items.map(i => i.event.eventType))].sort());

  let filtered = $derived(items.filter(i => {
    if (filterType && i.event.eventType !== filterType) return false;
    if (filterPerson.trim()) {
      const q = filterPerson.toLowerCase();
      const name = i.person ? `${i.person.givenName} ${i.person.surname}`.toLowerCase() : '';
      if (!name.includes(q)) return false;
    }
    return true;
  }));

  let yearGroups = $derived.by(() => {
    const groups = new Map<number, TimelineItem[]>();
    for (const item of filtered) {
      if (!groups.has(item.year)) groups.set(item.year, []);
      groups.get(item.year)!.push(item);
    }
    return [...groups.entries()].sort((a, b) => a[0] - b[0]);
  });

  function getEventColor(type: string): string {
    return eventColors[type] ?? '#8B6914';
  }

  $effect(() => { load(); });
</script>

<div class="flex flex-col h-full animate-fade-in">
  <div class="p-6 pb-3 shrink-0">
    <h1 class="text-2xl font-bold tracking-tight" style="font-family: var(--font-serif); color: var(--ink);">{t('nav.timeline')}</h1>
    <p class="text-sm text-ink-muted mt-1">{filtered.length} events across {yearGroups.length} years</p>

    <div class="flex gap-3 mt-4">
      <input
        type="text"
        placeholder={t('timeline.filterByPerson')}
        bind:value={filterPerson}
        class="w-56 px-3 py-2 text-sm rounded-lg border-none outline-none arch-input"
       aria-label={t('timeline.filterByPerson')} />
      <select
        bind:value={filterType}
        class="px-3 py-2 text-sm rounded-lg border-none outline-none arch-input"
       aria-label={t('common.filter')}>
        <option value="">{t('timeline.allEventTypes')}</option>
        {#each eventTypes as t}
          <option value={t}>{eventLabels[t] ?? t}</option>
        {/each}
      </select>
    </div>
  </div>

  <div class="flex-1 overflow-auto px-6 pb-6">
    {#if loading}
      <div class="text-sm text-ink-faint py-8 text-center">{t('timeline.loading')}</div>
    {:else}
      <div class="relative pl-8">
        <!-- Vertical line -->
        <div class="absolute left-3 top-0 bottom-0 w-px" style="background: var(--border-rule);"></div>

        {#each yearGroups as [year, groupItems]}
          <!-- Year header -->
          <div class="relative mb-4 mt-6 first:mt-0">
            <div class="absolute left-[-29px] w-6 h-6 rounded-full flex items-center justify-center" style="background: var(--accent);">
              <div class="w-2 h-2 rounded-full bg-white"></div>
            </div>
            <h3 class="text-lg font-bold text-ink tabular-nums">{year}</h3>
          </div>

          {#each groupItems as item}
            <div class="relative mb-2 ml-4">
              <div class="absolute left-[-37px] top-2 w-2 h-2 rounded-full" style="background: {getEventColor(item.event.eventType)}"></div>
              <div class="arch-card rounded-lg px-4 py-2.5 flex items-center gap-3">
                <span class="text-xs font-medium px-2 py-0.5 rounded-full" style="background: {getEventColor(item.event.eventType)}15; color: {getEventColor(item.event.eventType)}">
                  {eventLabels[item.event.eventType] ?? item.event.eventType}
                </span>
                {#if item.person}
                  <a href="/people/{encodeURIComponent(item.person.xref)}" class="text-sm font-medium hover:underline" style="color: var(--ink); text-decoration: none;">
                    {item.person.givenName} {item.person.surname}
                  </a>
                {:else}
                  <span class="text-sm text-ink font-medium">{item.event.ownerXref}</span>
                {/if}
                {#if item.event.dateValue}
                  <span class="text-xs text-ink-faint ml-auto">{item.event.dateValue}</span>
                {/if}
                {#if item.event.place}
                  <span class="text-xs text-ink-faint">{item.event.place}</span>
                {/if}
              </div>
            </div>
          {/each}
        {/each}

        {#if yearGroups.length === 0}
          <div class="text-sm text-ink-faint py-8 text-center">{t('timeline.noMatches')}</div>
        {/if}
      </div>
    {/if}
  </div>
</div>
