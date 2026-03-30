<script lang="ts">
  import { getPerson, getParents, getPersons } from '$lib/db';
  import type { Person } from '$lib/types';

  interface FanNode {
    depth: number;
    index: number;
    person: Person | null;
  }

  interface HitRegion {
    person: Person | null;
    innerR: number;
    outerR: number;
    startAngle: number;
    endAngle: number;
  }

  const maxGenerations = 6;
  const rootPadding = 46;

  let canvasEl = $state<HTMLCanvasElement | null>(null);
  let containerEl = $state<HTMLDivElement | null>(null);
  let people = $state<Person[]>([]);
  let rootPerson = $state<Person | null>(null);
  let search = $state('');
  let fanNodes = $state<FanNode[]>([]);
  let hitRegions = $state<HitRegion[]>([]);
  let loading = $state(true);

  const filteredPeople = $derived(
    search.trim()
      ? people.filter((p) => {
          const t = search.toLowerCase();
          return `${p.givenName} ${p.surname}`.toLowerCase().includes(t) || p.xref.toLowerCase().includes(t);
        }).slice(0, 8)
      : people.slice(0, 8)
  );

  function nameOf(person: Person | null): string {
    if (!person) return 'Unknown';
    const value = `${person.givenName || ''} ${person.surname || ''}`.trim();
    return value || person.xref;
  }

  function birthYear(person: Person | null): string {
    if (!person?.birthDate) return '';
    const m = person.birthDate.match(/\b(\d{4})\b/);
    return m ? m[1] : '';
  }

  function surnameHue(surname: string): number {
    let hash = 0;
    for (let i = 0; i < surname.length; i++) hash = (hash * 31 + surname.charCodeAt(i)) >>> 0;
    return hash % 360;
  }

  function drawFanChart() {
    if (!canvasEl || !containerEl) return;
    const ctx = canvasEl.getContext('2d');
    if (!ctx) return;

    const rect = containerEl.getBoundingClientRect();
    const width = Math.max(640, Math.floor(rect.width));
    const height = Math.max(420, Math.floor(rect.height));
    const dpr = window.devicePixelRatio || 1;

    canvasEl.width = width * dpr;
    canvasEl.height = height * dpr;
    canvasEl.style.width = `${width}px`;
    canvasEl.style.height = `${height}px`;
    ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
    ctx.clearRect(0, 0, width, height);

    const style = getComputedStyle(document.documentElement);
    const ink = style.getPropertyValue('--ink').trim() || '#1A1612';
    const muted = style.getPropertyValue('--ink-muted').trim() || '#7A6F62';
    const vellum = style.getPropertyValue('--vellum').trim() || '#FDFBF7';
    const border = style.getPropertyValue('--border-subtle').trim() || 'rgba(0,0,0,0.12)';

    const centerX = width / 2;
    const centerY = height - rootPadding;
    const outerRadius = Math.min(width / 2 - 18, height - 70);
    const ringWidth = outerRadius / (maxGenerations - 1);

    hitRegions = [];

    for (const node of fanNodes) {
      if (node.depth === 0) continue;
      const segmentCount = 2 ** node.depth;
      const segmentAngle = Math.PI / segmentCount;
      const startAngle = Math.PI + node.index * segmentAngle;
      const endAngle = startAngle + segmentAngle;
      const innerR = node.depth * ringWidth;
      const outerR = (node.depth + 1) * ringWidth;

      ctx.beginPath();
      ctx.arc(centerX, centerY, outerR, startAngle, endAngle);
      ctx.arc(centerX, centerY, innerR, endAngle, startAngle, true);
      ctx.closePath();

      if (node.person?.surname) {
        const hue = surnameHue(node.person.surname);
        ctx.fillStyle = `hsla(${hue}, 54%, 56%, 0.32)`;
      } else {
        ctx.fillStyle = vellum;
      }
      ctx.fill();
      ctx.strokeStyle = border;
      ctx.lineWidth = 1;
      ctx.stroke();

      const midAngle = (startAngle + endAngle) / 2;
      const textR = innerR + ringWidth * 0.52;
      const tx = centerX + Math.cos(midAngle) * textR;
      const ty = centerY + Math.sin(midAngle) * textR;
      const label = nameOf(node.person);
      const year = birthYear(node.person);
      const text = year ? `${label} (${year})` : label;

      ctx.save();
      ctx.translate(tx, ty);
      ctx.rotate(midAngle - Math.PI * 1.5);
      ctx.fillStyle = node.person ? ink : muted;
      ctx.font = `${Math.max(9, 14 - node.depth)}px var(--font-sans)`;
      ctx.textAlign = 'center';
      ctx.textBaseline = 'middle';
      ctx.fillText(text.length > 24 ? `${text.slice(0, 23)}...` : text, 0, 0);
      ctx.restore();

      hitRegions.push({ person: node.person, innerR, outerR, startAngle, endAngle });
    }

    ctx.beginPath();
    ctx.arc(centerX, centerY, ringWidth * 0.9, 0, Math.PI * 2);
    ctx.fillStyle = vellum;
    ctx.fill();
    ctx.strokeStyle = border;
    ctx.stroke();

    if (rootPerson) {
      ctx.fillStyle = ink;
      ctx.font = '600 13px var(--font-serif)';
      ctx.textAlign = 'center';
      ctx.fillText(nameOf(rootPerson), centerX, centerY - 4);
      const rootYear = birthYear(rootPerson);
      if (rootYear) {
        ctx.fillStyle = muted;
        ctx.font = '11px var(--font-mono)';
        ctx.fillText(rootYear, centerX, centerY + 12);
      }
    }
  }

  async function buildFan(root: Person | null): Promise<FanNode[]> {
    const nodes: FanNode[] = [];
    const visit = async (person: Person | null, depth: number, index: number): Promise<void> => {
      nodes.push({ depth, index, person });
      if (depth >= maxGenerations - 1) return;
      if (!person) {
        await visit(null, depth + 1, index * 2);
        await visit(null, depth + 1, index * 2 + 1);
        return;
      }
      const par = await getParents(person.xref);
      await visit(par.father, depth + 1, index * 2);
      await visit(par.mother, depth + 1, index * 2 + 1);
    };
    await visit(root, 0, 0);
    return nodes;
  }

  async function setRoot(person: Person) {
    rootPerson = person;
    fanNodes = await buildFan(person);
    drawFanChart();
  }

  function clickChart(e: MouseEvent) {
    if (!canvasEl || !rootPerson) return;
    const rect = canvasEl.getBoundingClientRect();
    const x = e.clientX - rect.left;
    const y = e.clientY - rect.top;
    const centerX = rect.width / 2;
    const centerY = rect.height - rootPadding;
    const dx = x - centerX;
    const dy = y - centerY;
    const distance = Math.hypot(dx, dy);
    let angle = Math.atan2(dy, dx);
    if (angle < 0) angle += Math.PI * 2;

    const hit = hitRegions.find((r) => {
      if (!r.person) return false;
      if (distance < r.innerR || distance > r.outerR) return false;
      return angle >= r.startAngle && angle <= r.endAngle;
    });
    if (hit?.person) setRoot(hit.person);
  }

  async function loadPeople() {
    loading = true;
    people = await getPersons('', 1000);
    if (people.length > 0) {
      const full = await getPerson(people[0].xref);
      if (full) await setRoot(full);
    }
    loading = false;
  }

  $effect(() => {
    loadPeople();
  });

  $effect(() => {
    fanNodes;
    rootPerson;
    if (!loading) drawFanChart();
  });

  $effect(() => {
    if (!containerEl) return;
    const resizeObserver = new ResizeObserver(() => drawFanChart());
    resizeObserver.observe(containerEl);
    const themeObserver = new MutationObserver(() => drawFanChart());
    themeObserver.observe(document.documentElement, { attributes: true, attributeFilter: ['data-theme'] });
    return () => {
      resizeObserver.disconnect();
      themeObserver.disconnect();
    };
  });
</script>

<div class="arch-page" style="max-width: 90rem; overflow-y: auto; height: 100vh; padding-bottom: 3rem;">
  <div class="mb-4">
    <h1 class="dossier-header" style="margin-bottom: 0.5rem;">Fan Chart</h1>
    <p class="text-sm" style="color: var(--ink-muted);">Semicircular ancestor chart. Click any ancestor segment to re-root the chart.</p>
  </div>

  <div class="arch-card p-4 mb-4">
    <label for="fan-chart-person-search" class="block text-xs mb-2" style="color: var(--ink-muted);">Root Person</label>
    <input
      id="fan-chart-person-search"
      class="arch-input w-full px-3 py-2 text-sm"
      placeholder="Type a name to change chart root"
      bind:value={search}
    />
    {#if search.trim().length > 0}
      <div class="mt-2 grid gap-1">
        {#each filteredPeople as p}
          <button
            type="button"
            class="text-left px-3 py-2 rounded-lg hover:opacity-80"
            style="background: var(--parchment); color: var(--ink);"
            onclick={async () => {
              const full = await getPerson(p.xref);
              if (full) await setRoot(full);
              search = '';
            }}
          >
            {nameOf(p)} {#if birthYear(p)}({birthYear(p)}){/if}
          </button>
        {/each}
      </div>
    {/if}
  </div>

  <div class="arch-card p-4" bind:this={containerEl} style="height: 72vh; min-height: 420px;">
    {#if loading}
      <div class="h-full flex items-center justify-center text-sm" style="color: var(--ink-muted);">Loading fan chart...</div>
    {:else if !rootPerson}
      <div class="h-full flex items-center justify-center text-sm" style="color: var(--ink-muted);">No people found in database.</div>
    {:else}
      <canvas bind:this={canvasEl} class="w-full h-full" onclick={clickChart}></canvas>
    {/if}
  </div>
</div>
