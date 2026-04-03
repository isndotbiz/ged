<script lang="ts">
  import { t } from '$lib/i18n';
  import { addEvidence, getEvidence, getFactSummary, getGpsChecklist, getPersons, setGpsChecklist } from '$lib/db';
  import type { Person } from '$lib/types';

  let people = $state.raw<Person[]>([]);
  let selectedXref = $state('');
  let summaries = $state.raw<{ factType: string; valueCount: number; evidenceCount: number }[]>([]);
  let evidence = $state.raw<any[]>([]);
  let checklist = $state.raw<{ item: number; completed: number; notes: string }[]>([]);
  let draft = $state({ factType: 'birth_date', factValue: '', sourceXref: '', informationType: 'primary', evidenceType: 'direct', quality: 'possible', analysisNotes: '' });

  const gpsItems = [
    'Reasonably exhaustive search conducted',
    'Complete and accurate citations',
    'Analysis and correlation of evidence',
    'Resolution of conflicting evidence',
    'Soundly written conclusion',
  ];

  async function loadPeople() {
    people = await getPersons('', 5000);
    if (!selectedXref && people.length > 0) selectedXref = people[0].xref;
  }

  async function loadPerson() {
    if (!selectedXref) return;
    summaries = await getFactSummary(selectedXref);
    evidence = await getEvidence(selectedXref);
    checklist = await getGpsChecklist(selectedXref);
  }

  async function add() {
    if (!selectedXref || !draft.factValue.trim()) return;
    await addEvidence({ ...draft, personXref: selectedXref, factValue: draft.factValue.trim() });
    draft.factValue = '';
    await loadPerson();
  }

  async function toggleItem(item: number, completed: number) {
    await setGpsChecklist(selectedXref, item, completed ? 0 : 1, '');
    await loadPerson();
  }

  function done(item: number): boolean {
    return checklist.some((row) => row.item === item && row.completed === 1);
  }

  $effect(() => {
    loadPeople();
  });

  $effect(() => {
    if (selectedXref) loadPerson();
  });
</script>

<div class="p-6 max-w-5xl">
  <h1 class="text-2xl font-bold mb-4">{t('evidence.title')}</h1>
  <div class="mb-4">
    <label class="text-xs text-ink-muted" for="evidence-person">{t('common.person')}</label>
    <select id="evidence-person" class="arch-input w-full" bind:value={selectedXref}>
      {#each people as person}
        <option value={person.xref}>{person.givenName} {person.surname}</option>
      {/each}
    </select>
  </div>

  <section class="arch-card p-4 mb-4">
    <h2 class="font-semibold mb-2">{t('evidence.addEvidence')}</h2>
    <div class="grid grid-cols-1 md:grid-cols-3 gap-2">
      <input class="arch-input" placeholder={t('evidence.factType')} bind:value={draft.factType} />
      <input class="arch-input" placeholder={t('evidence.factValue')} bind:value={draft.factValue} />
      <input class="arch-input" placeholder="Source XREF" bind:value={draft.sourceXref} />
      <select class="arch-input" bind:value={draft.informationType}>
        <option value="primary">{t('evidence.primary')}</option>
        <option value="secondary">{t('evidence.secondary')}</option>
        <option value="undetermined">Undetermined</option>
      </select>
      <select class="arch-input" bind:value={draft.evidenceType}>
        <option value="direct">Direct</option>
        <option value="indirect">Indirect</option>
        <option value="negative">Negative</option>
      </select>
      <select class="arch-input" bind:value={draft.quality}>
        <option value="proven">Proven</option>
        <option value="probable">Probable</option>
        <option value="possible">Possible</option>
        <option value="undetermined">Undetermined</option>
      </select>
      <input class="arch-input md:col-span-3" placeholder="Analysis notes" bind:value={draft.analysisNotes} />
    </div>
    <button class="btn-accent px-4 py-2 mt-3" onclick={add}>{t('evidence.addEvidence')}</button>
  </section>

  <section class="arch-card p-4 mb-4">
    <h2 class="font-semibold mb-2">{t('evidence.factSummary')}</h2>
    {#if summaries.length === 0}
      <div class="text-sm text-ink-faint">{t('evidence.noRecords')}</div>
    {:else}
      {#each summaries as row}
        <div class="py-1 text-sm text-ink-light">{row.factType}: {row.evidenceCount} entries, {row.valueCount} unique values</div>
      {/each}
    {/if}
  </section>

  <section class="arch-card p-4 mb-4">
    <h2 class="font-semibold mb-2">Evidence Entries</h2>
    {#each evidence as row}
      <div class="py-2 border-b border-subtle text-sm">
        <div class="font-medium">{row.factType}: {row.factValue}</div>
        <div class="text-ink-muted">{row.informationType} / {row.evidenceType} / {row.quality}</div>
      </div>
    {/each}
  </section>

  <section class="arch-card p-4">
    <h2 class="font-semibold mb-2">{t('evidence.gpsChecklist')}</h2>
    {#each gpsItems as item, idx}
      <label class="flex items-center gap-2 py-1 text-sm">
        <input type="checkbox" checked={done(idx + 1)} onchange={() => toggleItem(idx + 1, done(idx + 1) ? 1 : 0)} />
        <span>{idx + 1}. {item}</span>
      </label>
    {/each}
  </section>
</div>
