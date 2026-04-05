<script lang="ts">
  import { t } from '$lib/i18n';
  import { goto } from '$app/navigation';
  import { getPersons } from '$lib/db';
  import { findRelationshipPath, summarizeRelationship, type RelationshipStep } from '$lib/relationship-finder';
  import type { Person } from '$lib/types';

  let people = $state.raw<Person[]>([]);
  let personA = $state('');
  let personB = $state('');
  let loading = $state(false);
  let path = $state<RelationshipStep[] | null>(null);
  let relationSummary = $state('');
  let error = $state('');

  async function loadPeople() {
    people = await getPersons('', 5000);
    if (!personA && people.length > 0) personA = people[0].xref;
    if (!personB && people.length > 1) personB = people[1].xref;
  }

  function label(xref: string): string {
    const p = people.find((item) => item.xref === xref);
    return p ? `${p.givenName} ${p.surname}`.trim() : xref;
  }

  async function run() {
    error = '';
    if (!personA || !personB) return;
    loading = true;
    path = await findRelationshipPath(personA, personB);
    relationSummary = summarizeRelationship(path);
    if (!path) error = 'No relationship found within 15 generations';
    loading = false;
  }

  function years(xref: string): string {
    const p = people.find((item) => item.xref === xref);
    if (!p) return '';
    const b = p.birthDate.match(/(\d{4})/)?.[1] ?? '?';
    const d = p.deathDate.match(/(\d{4})/)?.[1] ?? (p.isLiving ? 'living' : '?');
    return `${b} - ${d}`;
  }

  $effect(() => {
    loadPeople();
  });
</script>

<div class="p-6 max-w-4xl">
  <h1 class="text-2xl font-bold mb-4">{t('relationship.title')}</h1>
  <div class="grid grid-cols-1 md:grid-cols-3 gap-3 mb-4 no-print">
    <div>
      <label class="text-xs text-ink-muted" for="person-a">{t('relationship.personA')}</label>
      <select id="person-a" class="arch-input w-full" bind:value={personA} aria-label={t('common.filter')}>
        {#each people as p}
          <option value={p.xref}>{label(p.xref)}</option>
        {/each}
      </select>
    </div>
    <div>
      <label class="text-xs text-ink-muted" for="person-b">{t('relationship.personB')}</label>
      <select id="person-b" class="arch-input w-full" bind:value={personB} aria-label={t('common.filter')}>
        {#each people as p}
          <option value={p.xref}>{label(p.xref)}</option>
        {/each}
      </select>
    </div>
    <div class="flex items-end gap-2">
      <button class="btn-accent px-4 py-2 w-full" onclick={run} disabled={loading} aria-label={t('common.actions')}>
        {loading ? 'Finding...' : 'Find Relationship'}
      </button>
      <button class="btn-secondary px-3 py-2" onclick={() => window.print()} aria-label={t('relationship.printRelationship')}>{t('common.print')}</button>
    </div>
  </div>

  {#if relationSummary}
    <p class="text-lg font-semibold mb-3">{relationSummary}</p>
  {/if}

  {#if error}
    <p class="text-red-300">{error}</p>
  {/if}

  {#if path}
    <div class="chain">
      {#each path as step, idx (step.personXref + '-' + idx)}
        <div class="card">
          <button class="name" onclick={() => goto(`/people/${step.personXref}`)}>{step.personName}</button>
          <div class="years">{years(step.personXref)}</div>
        </div>
        {#if idx < path.length - 1}
          <div class="arrow">{path[idx + 1].relationship} of</div>
        {/if}
      {/each}
    </div>
  {/if}
</div>

<style>
  .chain {
    display: flex;
    flex-direction: column;
    gap: 0.7rem;
  }
  .card {
    border: 1px solid var(--border-rule);
    background: var(--vellum);
    border-radius: 0.6rem;
    padding: 0.65rem 0.8rem;
  }
  .name {
    border: 0;
    background: transparent;
    color: var(--ink);
    font-weight: 700;
    padding: 0;
  }
  .years {
    color: var(--ink-muted);
    font-size: 0.82rem;
  }
  .arrow {
    color: var(--ink-faint);
    font-size: 0.8rem;
    margin-left: 0.35rem;
  }
</style>
