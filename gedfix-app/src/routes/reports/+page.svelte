<script lang="ts">
  import { getChildren, getDb, getEvents, getParents, getPersons, getSpouseFamilies } from '$lib/db';
  import type { GedcomEvent, Person } from '$lib/types';

  interface ReportEntry {
    person: Person;
    generation: number;
    numberLabel: string;
    events: GedcomEvent[];
    sources: { title: string; page: string }[];
    media: { title: string; filePath: string }[];
  }

  let people = $state<Person[]>([]);
  let search = $state('');
  let selected = $state<Person | null>(null);
  let reportType = $state<'ascending' | 'descending'>('ascending');
  let generating = $state(false);
  let generated = $state(false);
  let reportTitle = $state('');
  let sections = $state<{ generation: number; entries: ReportEntry[] }[]>([]);

  const suggestions = $derived(
    search.trim()
      ? people.filter((p) => `${p.givenName} ${p.surname}`.toLowerCase().includes(search.toLowerCase())).slice(0, 10)
      : []
  );

  function fullName(p: Person): string {
    const value = `${p.givenName || ''} ${p.surname || ''}`.trim();
    return value || p.xref;
  }

  async function loadPeople() {
    people = await getPersons('', 1200);
  }

  async function fetchEntry(person: Person, generation: number, numberLabel: string): Promise<ReportEntry> {
    const db = await getDb();
    const [events, sources, media] = await Promise.all([
      getEvents(person.xref),
      db.select<{ title: string; page: string }[]>(
        `SELECT COALESCE(s.title, c.sourceXref) AS title, c.page
         FROM citation c
         LEFT JOIN source s ON s.xref = c.sourceXref
         WHERE c.personXref = $1
         ORDER BY s.title
         LIMIT 12`,
        [person.xref]
      ),
      db.select<{ title: string; filePath: string }[]>(
        `SELECT COALESCE(title, filePath) AS title, filePath FROM media WHERE ownerXref = $1 LIMIT 12`,
        [person.xref]
      )
    ]);

    return { person, generation, numberLabel, events, sources, media };
  }

  async function buildAscending(root: Person, maxGenerations = 6) {
    const grouped = new Map<number, ReportEntry[]>();
    const visit = async (person: Person | null, generation: number, ahnentafel: number): Promise<void> => {
      if (!person || generation > maxGenerations) return;
      const entry = await fetchEntry(person, generation, String(ahnentafel));
      if (!grouped.has(generation)) grouped.set(generation, []);
      grouped.get(generation)!.push(entry);

      const parents = await getParents(person.xref);
      await visit(parents.father, generation + 1, ahnentafel * 2);
      await visit(parents.mother, generation + 1, ahnentafel * 2 + 1);
    };
    await visit(root, 1, 1);
    return Array.from(grouped.entries())
      .sort((a, b) => a[0] - b[0])
      .map(([generation, entries]) => ({
        generation,
        entries: entries.sort((a, b) => Number(a.numberLabel) - Number(b.numberLabel))
      }));
  }

  async function buildDescending(root: Person, maxGenerations = 5) {
    const grouped = new Map<number, ReportEntry[]>();
    const seen = new Set<string>();
    const visit = async (person: Person | null, generation: number, index: number): Promise<void> => {
      if (!person || generation > maxGenerations) return;
      if (seen.has(person.xref)) return;
      seen.add(person.xref);

      const entry = await fetchEntry(person, generation, `${generation}.${index}`);
      if (!grouped.has(generation)) grouped.set(generation, []);
      grouped.get(generation)!.push(entry);

      const families = await getSpouseFamilies(person.xref);
      let childIdx = 1;
      for (const family of families) {
        const children = await getChildren(family.xref);
        for (const child of children) {
          await visit(child, generation + 1, childIdx++);
        }
      }
    };
    await visit(root, 1, 1);
    return Array.from(grouped.entries())
      .sort((a, b) => a[0] - b[0])
      .map(([generation, entries]) => ({ generation, entries }));
  }

  async function generateReport() {
    if (!selected) return;
    generating = true;
    generated = false;
    reportTitle = `${reportType === 'ascending' ? 'Ascending (Ancestors)' : 'Descending (Descendants)'} Report for ${fullName(selected)}`;
    sections = reportType === 'ascending'
      ? await buildAscending(selected)
      : await buildDescending(selected);
    generating = false;
    generated = true;
  }

  function printReport() {
    window.print();
  }

  $effect(() => {
    loadPeople();
  });
</script>

<div class="arch-page" style="max-width: 78rem; overflow-y: auto; height: 100vh; padding-bottom: 4rem;">
  <div class="mb-6 no-print">
    <h1 class="dossier-header" style="margin-bottom: 0.5rem;">Ancestry Reports</h1>
    <p class="text-sm" style="color: var(--ink-muted);">Generate printable direct-line reports with events, sources, and media references.</p>
  </div>

  <div class="arch-card p-4 mb-4 no-print">
    <label for="report-person-search" class="block text-xs mb-1" style="color: var(--ink-muted);">Person</label>
    <input id="report-person-search" class="arch-input w-full px-3 py-2 text-sm" bind:value={search} placeholder="Search people..." />
    {#if suggestions.length > 0}
      <div class="mt-2 grid gap-1">
        {#each suggestions as person}
          <button
            type="button"
            class="text-left px-3 py-2 rounded-lg hover:opacity-80"
            style="background: var(--parchment); color: var(--ink);"
            onclick={() => {
              selected = person;
              search = fullName(person);
            }}
          >
            {fullName(person)}
          </button>
        {/each}
      </div>
    {/if}

    <div class="mt-4 flex flex-wrap items-end gap-3">
      <div>
        <label for="report-type" class="block text-xs mb-1" style="color: var(--ink-muted);">Report Type</label>
        <select id="report-type" class="arch-input px-3 py-2 text-sm" bind:value={reportType}>
          <option value="ascending">Ascending (Ancestors)</option>
          <option value="descending">Descending (Descendants)</option>
        </select>
      </div>
      <button class="btn-accent px-4 py-2" onclick={generateReport} disabled={!selected || generating}>
        {generating ? 'Generating...' : 'Generate'}
      </button>
      <button class="btn-secondary px-4 py-2" onclick={printReport} disabled={!generated}>
        Print Report
      </button>
    </div>
  </div>

  {#if generated}
    <article class="arch-card p-6 report-content">
      <header class="mb-6">
        <h2 class="text-xl" style="font-family: var(--font-serif); color: var(--ink);">{reportTitle}</h2>
        <p class="text-xs mt-1" style="color: var(--ink-muted);">Generated {new Date().toLocaleString()}</p>
      </header>

      {#each sections as section}
        <section class="mb-8">
          <h3 class="text-base mb-3" style="font-family: var(--font-serif); color: var(--ink); border-bottom: 1px solid var(--border-rule); padding-bottom: 0.3rem;">
            Generation {section.generation}
          </h3>
          {#each section.entries as entry}
            <div class="mb-4 p-3 rounded-lg" style="background: var(--vellum); border: 1px solid var(--border-subtle);">
              <div class="text-sm font-semibold" style="color: var(--ink);">
                {reportType === 'ascending' ? `${entry.numberLabel}. ` : ''}{fullName(entry.person)}
              </div>
              <div class="text-xs mt-1" style="color: var(--ink-muted);">
                {entry.person.birthDate ? `b. ${entry.person.birthDate}` : 'Birth unknown'}
                {entry.person.deathDate ? ` · d. ${entry.person.deathDate}` : ''}
              </div>
              {#if entry.events.length > 0}
                <div class="text-xs mt-2" style="color: var(--ink-light);">
                  <strong>Events:</strong>
                  {#each entry.events.slice(0, 8) as evt}
                    <span class="mr-2">{evt.eventType}{evt.dateValue ? ` (${evt.dateValue})` : ''}</span>
                  {/each}
                </div>
              {/if}
              <div class="text-xs mt-2" style="color: var(--ink-light);">
                <strong>Sources:</strong> {entry.sources.length > 0 ? '' : 'None'}
                {#each entry.sources.slice(0, 5) as source}
                  <span class="mr-2">{source.title}{source.page ? ` p.${source.page}` : ''}</span>
                {/each}
              </div>
              <div class="text-xs mt-2" style="color: var(--ink-light);">
                <strong>Media:</strong> {entry.media.length > 0 ? '' : 'None'}
                {#each entry.media.slice(0, 5) as media}
                  <span class="mr-2">{media.title}</span>
                {/each}
              </div>
            </div>
          {/each}
        </section>
      {/each}
    </article>
  {/if}
</div>

<style>
  @media print {
    :global(nav[aria-label='Main navigation']),
    :global(.mobile-hamburger),
    :global(.no-print) {
      display: none !important;
    }

    :global(main) {
      overflow: visible !important;
    }

    .report-content {
      border: none !important;
      box-shadow: none !important;
      padding: 0 !important;
      background: white !important;
      color: black !important;
      font-family: Georgia, 'Times New Roman', serif !important;
    }
  }
</style>
