<script lang="ts">
  import { t } from '$lib/i18n';
  import { getAllPersons, getTasks, insertTask, updateTask, deleteTask } from '$lib/db';
  import type { ResearchTask, Person } from '$lib/types';

  let tasks = $state<ResearchTask[]>([]);
  let showEditor = $state(false);
  let title = $state('');
  let description = $state('');
  let priority = $state<'HIGH' | 'MEDIUM' | 'LOW'>('MEDIUM');
  let filterStatus = $state<string | null>(null);
  let dueDate = $state('');
  let personXref = $state('');
  let persons = $state<Person[]>([]);

  async function load() { tasks = await getTasks(); }
  async function loadPeople() { persons = await getAllPersons(); }

  async function create() {
    await insertTask({ personXref, title, description, status: 'TODO', priority, dueDate, createdAt: new Date().toISOString(), completedAt: '' });
    showEditor = false; title = ''; description = ''; priority = 'MEDIUM'; dueDate = ''; personXref = '';
    await load();
  }

  async function toggle(t: ResearchTask) {
    const newStatus = t.status === 'DONE' ? 'TODO' : 'DONE';
    await updateTask(t.id, newStatus, newStatus === 'DONE' ? new Date().toISOString() : '');
    await load();
  }

  async function remove(id: number) { await deleteTask(id); await load(); }

  function dueSortValue(task: ResearchTask): number {
    if (!task.dueDate) return Number.MAX_SAFE_INTEGER;
    const ts = new Date(task.dueDate).getTime();
    return Number.isFinite(ts) ? ts : Number.MAX_SAFE_INTEGER;
  }

  function isOverdue(task: ResearchTask): boolean {
    if (task.status === 'DONE' || !task.dueDate) return false;
    return new Date(task.dueDate).getTime() < new Date().setHours(0, 0, 0, 0);
  }

  function personName(xref: string): string {
    if (!xref) return '';
    const p = persons.find((row) => row.xref === xref);
    return p ? `${p.givenName} ${p.surname}`.trim() : xref;
  }

  let filtered = $derived(
    (filterStatus ? tasks.filter(t => t.status === filterStatus) : tasks)
      .slice()
      .sort((a, b) => dueSortValue(a) - dueSortValue(b))
  );
  let todoCount = $derived(tasks.filter(t => t.status !== 'DONE').length);

  $effect(() => { load(); loadPeople(); });
</script>

<div class="p-8 max-w-4xl animate-fade-in">
  <div class="flex items-center justify-between mb-8">
    <div>
      <h1 class="text-2xl font-bold tracking-tight" style="font-family: var(--font-serif); color: var(--ink);">{t('research.tasks')}</h1>
      <p class="text-sm text-ink-muted mt-1">{todoCount} pending tasks</p>
    </div>
    <button onclick={() => showEditor = !showEditor} class="px-4 py-2 text-sm font-medium btn-accent">{showEditor ? 'Cancel' : 'Add Task'}</button>
  </div>

  {#if showEditor}
    <div class="arch-card rounded-xl p-6 mb-6">
      <input bind:value={title} placeholder="Task title" class="w-full px-3 py-2 text-sm arch-input mb-3" />
      <textarea bind:value={description} placeholder={t('common.description')} class="w-full px-3 py-2 text-sm arch-input mb-3 h-20"></textarea>
      <div class="grid grid-cols-1 md:grid-cols-2 gap-2 mb-3">
        <input type="date" bind:value={dueDate} class="w-full px-3 py-2 text-sm arch-input" />
        <select bind:value={personXref} class="w-full px-3 py-2 text-sm arch-input">
          <option value="">Link to person (optional)</option>
          {#each persons as p}
            <option value={p.xref}>{p.givenName} {p.surname} ({p.xref})</option>
          {/each}
        </select>
      </div>
      <div class="flex gap-2 mb-4">
        {#each (['HIGH', 'MEDIUM', 'LOW'] as const) as p}
          <button onclick={() => priority = p} class="px-3 py-1 text-xs rounded-lg {priority === p ? (p === 'HIGH' ? 'bg-red-500 text-white' : p === 'MEDIUM' ? 'bg-orange-500 text-white' : 'bg-green-500 text-white') : 'btn-filter'}">{p}</button>
        {/each}
      </div>
      <button onclick={create} disabled={!title.trim()} class="px-4 py-2 text-sm font-medium btn-accent">{t('common.create')}</button>
    </div>
  {/if}

  <div class="space-y-2">
    <div class="flex gap-2 mb-2">
      <button onclick={() => filterStatus = null} class="px-3 py-1 text-xs rounded-full {filterStatus === null ? 'btn-filter-active' : 'btn-filter'}">All</button>
      <button onclick={() => filterStatus = 'TODO'} class="px-3 py-1 text-xs rounded-full {filterStatus === 'TODO' ? 'btn-filter-active' : 'btn-filter'}">TODO</button>
      <button onclick={() => filterStatus = 'IN_PROGRESS'} class="px-3 py-1 text-xs rounded-full {filterStatus === 'IN_PROGRESS' ? 'btn-filter-active' : 'btn-filter'}">In Progress</button>
      <button onclick={() => filterStatus = 'DONE'} class="px-3 py-1 text-xs rounded-full {filterStatus === 'DONE' ? 'btn-filter-active' : 'btn-filter'}">Done</button>
    </div>
    {#each filtered as task}
      <div class="arch-card rounded-xl p-4 flex items-start gap-3 {isOverdue(task) ? 'task-overdue' : ''}">
        <button onclick={() => toggle(task)} class="mt-0.5 w-5 h-5 rounded border-2 flex items-center justify-center shrink-0 {task.status === 'DONE' ? 'bg-green-500 border-green-500 text-white' : 'border-gray-300'}">
          {#if task.status === 'DONE'}<svg class="w-3 h-3" fill="none" stroke="currentColor" stroke-width="3" viewBox="0 0 24 24"><path d="M5 13l4 4L19 7"/></svg>{/if}
        </button>
        <div class="flex-1 min-w-0">
          <div class="flex items-center gap-2">
            <span class="font-medium text-sm {task.status === 'DONE' ? 'line-through text-ink-faint' : 'text-ink'}">{task.title}</span>
            <span class="px-1.5 py-0.5 text-[9px] font-semibold rounded {task.priority === 'HIGH' ? 'bg-red-100 text-red-600' : task.priority === 'MEDIUM' ? 'bg-orange-100 text-orange-600' : 'bg-green-100 text-green-600'}">{task.priority}</span>
          </div>
          {#if task.description}<div class="text-xs text-ink-muted mt-0.5">{task.description}</div>{/if}
          {#if task.personXref}
            <a href="/people/{encodeURIComponent(task.personXref)}" class="text-xs mt-1 inline-block hover:underline" style="color: var(--accent);">
              {personName(task.personXref)}
            </a>
          {/if}
          {#if task.dueDate}
            <div class="text-[11px] mt-1 {isOverdue(task) ? 'text-red-400' : 'text-ink-faint'}">
              Due: {new Date(task.dueDate).toLocaleDateString()}
            </div>
          {/if}
        </div>
        <button onclick={() => remove(task.id)} class="text-xs text-red-500 hover:text-red-700 shrink-0">Delete</button>
      </div>
    {/each}
  </div>
</div>

<style>
  .task-overdue {
    border-color: color-mix(in srgb, var(--color-accent) 50%, transparent);
    box-shadow: 0 0 0 1px color-mix(in srgb, var(--color-accent) 35%, transparent);
  }
</style>
