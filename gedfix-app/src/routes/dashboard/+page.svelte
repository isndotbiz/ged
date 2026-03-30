<script lang="ts">
  import { onMount } from 'svelte';
  import { getDb } from '$lib/db';

  // ── Reactive state ──
  let totalPeople = $state(0);
  let maleCount = $state(0);
  let femaleCount = $state(0);
  let totalFamilies = $state(0);
  let totalSources = $state(0);
  let avgCitationsPerSource = $state(0);
  let totalEvents = $state(0);
  let eventBreakdown = $state<{type: string; count: number}[]>([]);

  // Completeness
  let withBirthDate = $state(0);
  let withDeathDate = $state(0);
  let withSource = $state(0);
  let withMedia = $state(0);
  let completenessScore = $state(0);
  let generationDepth = $state(0);

  // Charts
  let eventsByDecade = $state<{label: string; value: number}[]>([]);
  let topPlaces = $state<{label: string; value: number}[]>([]);

  // Data quality
  let missingBirth = $state(0);
  let missingDeath = $state(0);
  let missingSources = $state(0);
  let orphanedPeople = $state(0);
  let mostDocumented = $state<{name: string; xref: string; score: number} | null>(null);

  let loading = $state(true);

  // Canvas refs
  let donutCanvas = $state<HTMLCanvasElement | null>(null);
  let timelineCanvas = $state<HTMLCanvasElement | null>(null);
  let placesCanvas = $state<HTMLCanvasElement | null>(null);

  // ── Chart drawing functions ──

  function drawDonutChart(canvas: HTMLCanvasElement, segments: {label: string; value: number; color: string}[]) {
    const ctx = canvas.getContext('2d')!;
    const dpr = window.devicePixelRatio || 1;
    const rect = canvas.getBoundingClientRect();
    canvas.width = rect.width * dpr;
    canvas.height = rect.height * dpr;
    ctx.scale(dpr, dpr);

    const w = rect.width;
    const h = rect.height;
    const cx = w / 2;
    const cy = h / 2;
    const outerR = Math.min(w, h) / 2 - 10;
    const innerR = outerR * 0.6;

    const total = segments.reduce((s, seg) => s + seg.value, 0);
    if (total === 0) return;

    let startAngle = -Math.PI / 2;
    for (const seg of segments) {
      const sweep = (seg.value / total) * Math.PI * 2;
      ctx.beginPath();
      ctx.arc(cx, cy, outerR, startAngle, startAngle + sweep);
      ctx.arc(cx, cy, innerR, startAngle + sweep, startAngle, true);
      ctx.closePath();
      ctx.fillStyle = seg.color;
      ctx.fill();
      startAngle += sweep;
    }

    // Center text
    ctx.fillStyle = '#1A1612';
    ctx.font = `bold ${Math.floor(outerR * 0.4)}px Georgia, serif`;
    ctx.textAlign = 'center';
    ctx.textBaseline = 'middle';
    ctx.fillText(`${Math.round(completenessScore)}%`, cx, cy - 6);
    ctx.font = `${Math.floor(outerR * 0.15)}px "Gill Sans", sans-serif`;
    ctx.fillStyle = '#7A6F62';
    ctx.fillText('complete', cx, cy + outerR * 0.18);
  }

  function drawTimelineChart(canvas: HTMLCanvasElement, data: {label: string; value: number}[]) {
    const ctx = canvas.getContext('2d')!;
    const dpr = window.devicePixelRatio || 1;
    const rect = canvas.getBoundingClientRect();
    canvas.width = rect.width * dpr;
    canvas.height = rect.height * dpr;
    ctx.scale(dpr, dpr);

    const w = rect.width;
    const h = rect.height;
    const pad = {top: 20, right: 20, bottom: 40, left: 50};
    const plotW = w - pad.left - pad.right;
    const plotH = h - pad.top - pad.bottom;

    if (data.length === 0) return;

    const maxVal = Math.max(...data.map(d => d.value), 1);
    const barW = Math.max(2, (plotW / data.length) - 2);

    // Grid lines
    ctx.strokeStyle = 'rgba(26, 22, 18, 0.08)';
    ctx.lineWidth = 1;
    for (let i = 0; i <= 4; i++) {
      const y = pad.top + plotH - (plotH * i / 4);
      ctx.beginPath();
      ctx.moveTo(pad.left, y);
      ctx.lineTo(pad.left + plotW, y);
      ctx.stroke();

      ctx.fillStyle = '#7A6F62';
      ctx.font = '10px "Gill Sans", sans-serif';
      ctx.textAlign = 'right';
      ctx.textBaseline = 'middle';
      ctx.fillText(String(Math.round(maxVal * i / 4)), pad.left - 6, y);
    }

    // Bars
    const gradient = ctx.createLinearGradient(0, pad.top, 0, pad.top + plotH);
    gradient.addColorStop(0, '#8B6914');
    gradient.addColorStop(1, '#C4A44A');

    data.forEach((d, i) => {
      const x = pad.left + (i / data.length) * plotW + 1;
      const barH = (d.value / maxVal) * plotH;
      const y = pad.top + plotH - barH;

      ctx.fillStyle = gradient;
      ctx.beginPath();
      const radius = Math.min(3, barW / 2);
      ctx.moveTo(x, y + radius);
      ctx.arcTo(x, y, x + barW, y, radius);
      ctx.arcTo(x + barW, y, x + barW, y + barH, radius);
      ctx.lineTo(x + barW, pad.top + plotH);
      ctx.lineTo(x, pad.top + plotH);
      ctx.closePath();
      ctx.fill();

      // Labels (show every Nth label to avoid overlap)
      const step = Math.max(1, Math.floor(data.length / 8));
      if (i % step === 0) {
        ctx.fillStyle = '#7A6F62';
        ctx.font = '9px "Gill Sans", sans-serif';
        ctx.textAlign = 'center';
        ctx.textBaseline = 'top';
        ctx.fillText(d.label, x + barW / 2, pad.top + plotH + 6);
      }
    });
  }

  function drawHorizontalBarChart(canvas: HTMLCanvasElement, data: {label: string; value: number}[]) {
    const ctx = canvas.getContext('2d')!;
    const dpr = window.devicePixelRatio || 1;
    const rect = canvas.getBoundingClientRect();
    canvas.width = rect.width * dpr;
    canvas.height = rect.height * dpr;
    ctx.scale(dpr, dpr);

    const w = rect.width;
    const h = rect.height;
    const pad = {top: 10, right: 20, bottom: 10, left: 120};
    const plotW = w - pad.left - pad.right;
    const plotH = h - pad.top - pad.bottom;

    if (data.length === 0) return;

    const maxVal = Math.max(...data.map(d => d.value), 1);
    const barH = Math.min(24, (plotH / data.length) - 4);
    const colors = ['#8B6914', '#A37D1A', '#C4A44A', '#4A7C3F', '#3D6B8A', '#6B4A3D', '#8A6B4A', '#5A7A4A', '#7A5A4A', '#4A5A7A'];

    data.forEach((d, i) => {
      const y = pad.top + (i / data.length) * plotH + 2;
      const barW = (d.value / maxVal) * plotW;

      // Bar
      ctx.fillStyle = colors[i % colors.length];
      ctx.beginPath();
      const radius = Math.min(4, barH / 2);
      ctx.moveTo(pad.left, y);
      ctx.lineTo(pad.left + barW - radius, y);
      ctx.arcTo(pad.left + barW, y, pad.left + barW, y + barH, radius);
      ctx.arcTo(pad.left + barW, y + barH, pad.left, y + barH, radius);
      ctx.lineTo(pad.left, y + barH);
      ctx.closePath();
      ctx.fill();

      // Label
      ctx.fillStyle = '#1A1612';
      ctx.font = '11px "Gill Sans", sans-serif';
      ctx.textAlign = 'right';
      ctx.textBaseline = 'middle';
      const label = d.label.length > 18 ? d.label.slice(0, 16) + '...' : d.label;
      ctx.fillText(label, pad.left - 8, y + barH / 2);

      // Value
      ctx.fillStyle = '#7A6F62';
      ctx.font = '10px "Gill Sans", sans-serif';
      ctx.textAlign = 'left';
      ctx.fillText(String(d.value), pad.left + barW + 6, y + barH / 2);
    });
  }

  // ── Data loading ──

  async function loadDashboardData() {
    const db = await getDb();

    // Row 1: Key stats
    const [personStats] = await db.select<{total: number; males: number; females: number}[]>(
      `SELECT COUNT(*) as total,
              SUM(CASE WHEN sex = 'M' THEN 1 ELSE 0 END) as males,
              SUM(CASE WHEN sex = 'F' THEN 1 ELSE 0 END) as females
       FROM person`
    );
    totalPeople = personStats.total;
    maleCount = personStats.males;
    femaleCount = personStats.females;

    const [famStats] = await db.select<{cnt: number}[]>(`SELECT COUNT(*) as cnt FROM family`);
    totalFamilies = famStats.cnt;

    const [srcStats] = await db.select<{cnt: number; avgCite: number}[]>(
      `SELECT COUNT(*) as cnt,
              COALESCE((SELECT CAST(COUNT(*) AS REAL) / NULLIF(COUNT(DISTINCT sourceXref), 0) FROM citation), 0) as avgCite
       FROM source`
    );
    totalSources = srcStats.cnt;
    avgCitationsPerSource = Math.round(srcStats.avgCite * 10) / 10;

    const [evtStats] = await db.select<{cnt: number}[]>(`SELECT COUNT(*) as cnt FROM event`);
    totalEvents = evtStats.cnt;

    eventBreakdown = await db.select<{type: string; count: number}[]>(
      `SELECT eventType as type, COUNT(*) as count FROM event GROUP BY eventType ORDER BY count DESC LIMIT 6`
    );

    // Row 2: Completeness
    const [comp] = await db.select<{
      total: number; withBirth: number; withDeath: number; withSrc: number; withMed: number;
    }[]>(
      `SELECT COUNT(*) as total,
              SUM(CASE WHEN birthDate != '' AND birthDate IS NOT NULL THEN 1 ELSE 0 END) as withBirth,
              SUM(CASE WHEN deathDate != '' AND deathDate IS NOT NULL THEN 1 ELSE 0 END) as withDeath,
              SUM(CASE WHEN sourceCount > 0 THEN 1 ELSE 0 END) as withSrc,
              SUM(CASE WHEN mediaCount > 0 THEN 1 ELSE 0 END) as withMed
       FROM person`
    );
    withBirthDate = comp.total > 0 ? Math.round((comp.withBirth / comp.total) * 100) : 0;
    withDeathDate = comp.total > 0 ? Math.round((comp.withDeath / comp.total) * 100) : 0;
    withSource = comp.total > 0 ? Math.round((comp.withSrc / comp.total) * 100) : 0;
    withMedia = comp.total > 0 ? Math.round((comp.withMed / comp.total) * 100) : 0;
    completenessScore = Math.round((withBirthDate + withDeathDate + withSource + withMedia) / 4);

    // Generation depth: count max ancestor chain depth via recursive CTE
    try {
      const [depthResult] = await db.select<{depth: number}[]>(
        `WITH RECURSIVE ancestors(xref, depth) AS (
           SELECT p.xref, 0 FROM person p
           WHERE NOT EXISTS (SELECT 1 FROM child_link cl JOIN family f ON cl.familyXref = f.xref WHERE cl.childXref = p.xref AND (f.partner1Xref != '' OR f.partner2Xref != ''))
           UNION ALL
           SELECT cl.childXref, a.depth + 1
           FROM ancestors a
           JOIN family f ON (f.partner1Xref = a.xref OR f.partner2Xref = a.xref)
           JOIN child_link cl ON cl.familyXref = f.xref
           WHERE a.depth < 50
         )
         SELECT MAX(depth) as depth FROM ancestors`
      );
      generationDepth = depthResult?.depth ?? 0;
    } catch {
      // Fallback: estimate from birth year range
      const [yearRange] = await db.select<{minYear: number; maxYear: number}[]>(
        `SELECT MIN(CAST(SUBSTR(birthDate, 1, 4) AS INTEGER)) as minYear,
                MAX(CAST(SUBSTR(birthDate, 1, 4) AS INTEGER)) as maxYear
         FROM person WHERE birthDate != '' AND birthDate IS NOT NULL AND LENGTH(birthDate) >= 4
         AND CAST(SUBSTR(birthDate, 1, 4) AS INTEGER) > 1000`
      );
      if (yearRange?.minYear && yearRange?.maxYear) {
        generationDepth = Math.round((yearRange.maxYear - yearRange.minYear) / 28);
      }
    }

    // Row 3: Event timeline by decade
    const decadeData = await db.select<{decade: string; count: number}[]>(
      `SELECT (CAST(SUBSTR(dateValue, 1, 3) AS INTEGER) * 10) || '0s' as decade,
              COUNT(*) as count
       FROM event
       WHERE dateValue != '' AND dateValue IS NOT NULL AND LENGTH(dateValue) >= 4
       AND CAST(SUBSTR(dateValue, 1, 4) AS INTEGER) > 1000
       GROUP BY CAST(SUBSTR(dateValue, 1, 3) AS INTEGER)
       ORDER BY CAST(SUBSTR(dateValue, 1, 3) AS INTEGER)`
    );
    eventsByDecade = decadeData.map(d => ({label: d.decade, value: d.count}));

    // Top 10 places
    const placeData = await db.select<{place: string; count: number}[]>(
      `SELECT place, COUNT(*) as count FROM event
       WHERE place != '' AND place IS NOT NULL
       GROUP BY place ORDER BY count DESC LIMIT 10`
    );
    topPlaces = placeData.map(d => ({label: d.place, value: d.count}));

    // Row 4: Data quality
    missingBirth = comp.total - comp.withBirth;
    const [deadNoDeath] = await db.select<{cnt: number}[]>(
      `SELECT COUNT(*) as cnt FROM person WHERE isLiving = 0 AND (deathDate = '' OR deathDate IS NULL)`
    );
    missingDeath = deadNoDeath.cnt;
    missingSources = await db.select<{cnt: number}[]>(
      `SELECT COUNT(*) as cnt FROM person WHERE sourceCount = 0`
    ).then(r => r[0].cnt);

    // Orphaned: not in any family as partner or child
    const [orphans] = await db.select<{cnt: number}[]>(
      `SELECT COUNT(*) as cnt FROM person p
       WHERE p.xref NOT IN (SELECT partner1Xref FROM family WHERE partner1Xref != '')
       AND p.xref NOT IN (SELECT partner2Xref FROM family WHERE partner2Xref != '')
       AND p.xref NOT IN (SELECT childXref FROM child_link)`
    );
    orphanedPeople = orphans.cnt;

    // Most documented person
    const topPeople = await db.select<{xref: string; givenName: string; surname: string; score: number}[]>(
      `SELECT p.xref, p.givenName, p.surname,
              (SELECT COUNT(*) FROM event e WHERE e.ownerXref = p.xref)
              + p.sourceCount + p.mediaCount as score
       FROM person p ORDER BY score DESC LIMIT 1`
    );
    if (topPeople.length > 0) {
      const tp = topPeople[0];
      mostDocumented = {name: `${tp.givenName} ${tp.surname}`.trim(), xref: tp.xref, score: tp.score};
    }

    loading = false;
  }

  onMount(() => {
    loadDashboardData();
  });

  // Draw charts when data and canvases are ready
  $effect(() => {
    if (!loading && donutCanvas) {
      drawDonutChart(donutCanvas, [
        {label: 'Birth Date', value: withBirthDate, color: '#8B6914'},
        {label: 'Death Date', value: withDeathDate, color: '#C4A44A'},
        {label: 'Sources', value: withSource, color: '#4A7C3F'},
        {label: 'Media', value: withMedia, color: '#3D6B8A'},
      ]);
    }
  });

  $effect(() => {
    if (!loading && timelineCanvas && eventsByDecade.length > 0) {
      drawTimelineChart(timelineCanvas, eventsByDecade);
    }
  });

  $effect(() => {
    if (!loading && placesCanvas && topPlaces.length > 0) {
      drawHorizontalBarChart(placesCanvas, topPlaces);
    }
  });

  function fmtNum(n: number): string {
    return n.toLocaleString();
  }
</script>

<div class="arch-page" style="max-width: 80rem; overflow-y: auto; height: 100vh; padding-bottom: 4rem;">
  <!-- Header -->
  <div style="margin-bottom: 1.5rem;">
    <h1 style="font-size: 1.75rem; margin: 0 0 0.25rem 0;" class="dossier-header" style:border-bottom="none" style:padding-bottom="0" style:margin-bottom="0.25rem">
      Analytics Dashboard
    </h1>
    <p style="color: var(--ink-muted); font-size: 0.85rem; margin: 0;">
      Family tree statistics and data quality overview
    </p>
  </div>

  {#if loading}
    <div style="display: flex; align-items: center; justify-content: center; height: 60vh;">
      <div style="text-align: center;">
        <div style="width: 36px; height: 36px; border: 3px solid var(--parchment); border-top-color: var(--accent); border-radius: 50%; animation: spin 0.8s linear infinite; margin: 0 auto 1rem;"></div>
        <p style="color: var(--ink-muted); font-size: 0.85rem;">Loading analytics...</p>
      </div>
    </div>
  {:else}
    <!-- Row 1: Key Stats Cards -->
    <div class="dashboard-grid stagger-children" style="margin-bottom: 1.5rem;">
      <!-- Total People -->
      <div class="arch-card stat-card">
        <div class="stat-icon" style="background: var(--card-male-bg); color: var(--card-male-border-hover);">
          <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2"/><circle cx="9" cy="7" r="4"/><path d="M22 21v-2a4 4 0 0 0-3-3.87"/><path d="M16 3.13a4 4 0 0 1 0 7.75"/></svg>
        </div>
        <div class="stat-label">Total People</div>
        <div class="stat-value">{fmtNum(totalPeople)}</div>
        <div class="stat-breakdown">
          <span style="color: var(--card-male-border-hover);">{fmtNum(maleCount)} male</span>
          <span style="color: var(--ink-faint); margin: 0 0.25rem;">|</span>
          <span style="color: var(--card-female-border-hover);">{fmtNum(femaleCount)} female</span>
        </div>
      </div>

      <!-- Total Families -->
      <div class="arch-card stat-card">
        <div class="stat-icon" style="background: rgba(139, 105, 20, 0.1); color: var(--accent);">
          <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M3 9l9-7 9 7v11a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z"/><polyline points="9 22 9 12 15 12 15 22"/></svg>
        </div>
        <div class="stat-label">Total Families</div>
        <div class="stat-value">{fmtNum(totalFamilies)}</div>
        <div class="stat-breakdown">
          <span style="color: var(--ink-muted);">{totalPeople > 0 ? (totalFamilies / totalPeople * 100).toFixed(1) : 0}% family ratio</span>
        </div>
      </div>

      <!-- Total Sources -->
      <div class="arch-card stat-card">
        <div class="stat-icon" style="background: rgba(74, 124, 63, 0.1); color: var(--color-validated);">
          <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/><polyline points="14 2 14 8 20 8"/><line x1="16" y1="13" x2="8" y2="13"/><line x1="16" y1="17" x2="8" y2="17"/></svg>
        </div>
        <div class="stat-label">Total Sources</div>
        <div class="stat-value">{fmtNum(totalSources)}</div>
        <div class="stat-breakdown">
          <span style="color: var(--ink-muted);">{avgCitationsPerSource} avg citations/source</span>
        </div>
      </div>

      <!-- Total Events -->
      <div class="arch-card stat-card">
        <div class="stat-icon" style="background: rgba(61, 107, 138, 0.1); color: #3D6B8A;">
          <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="3" y="4" width="18" height="18" rx="2" ry="2"/><line x1="16" y1="2" x2="16" y2="6"/><line x1="8" y1="2" x2="8" y2="6"/><line x1="3" y1="10" x2="21" y2="10"/></svg>
        </div>
        <div class="stat-label">Total Events</div>
        <div class="stat-value">{fmtNum(totalEvents)}</div>
        <div class="stat-breakdown">
          {#each eventBreakdown.slice(0, 3) as evt}
            <span class="event-pill">{evt.type}: {fmtNum(evt.count)}</span>
          {/each}
        </div>
      </div>
    </div>

    <!-- Row 2: Tree Completeness -->
    <div class="dashboard-grid-2 stagger-children" style="margin-bottom: 1.5rem;">
      <!-- Completeness Score Donut -->
      <div class="arch-card" style="padding: 1.25rem;">
        <div class="arch-section-header">Tree Completeness</div>
        <div style="display: flex; align-items: center; gap: 1.5rem;">
          <div style="width: 160px; height: 160px; flex-shrink: 0;">
            <canvas bind:this={donutCanvas} style="width: 160px; height: 160px;"></canvas>
          </div>
          <div style="flex: 1;">
            <div class="completeness-row">
              <span class="completeness-dot" style="background: #8B6914;"></span>
              <span class="completeness-label">Birth Date</span>
              <span class="completeness-pct">{withBirthDate}%</span>
            </div>
            <div class="completeness-row">
              <span class="completeness-dot" style="background: #C4A44A;"></span>
              <span class="completeness-label">Death Date</span>
              <span class="completeness-pct">{withDeathDate}%</span>
            </div>
            <div class="completeness-row">
              <span class="completeness-dot" style="background: #4A7C3F;"></span>
              <span class="completeness-label">Has Source</span>
              <span class="completeness-pct">{withSource}%</span>
            </div>
            <div class="completeness-row">
              <span class="completeness-dot" style="background: #3D6B8A;"></span>
              <span class="completeness-label">Has Media</span>
              <span class="completeness-pct">{withMedia}%</span>
            </div>
          </div>
        </div>
      </div>

      <!-- Generation Depth -->
      <div class="arch-card" style="padding: 1.25rem; display: flex; flex-direction: column; justify-content: center;">
        <div class="arch-section-header">Generation Depth</div>
        <div style="text-align: center; padding: 1rem 0;">
          <div style="font-family: var(--font-serif); font-size: 3.5rem; font-weight: 700; color: var(--accent); line-height: 1;">
            {generationDepth}
          </div>
          <div style="color: var(--ink-muted); font-size: 0.85rem; margin-top: 0.5rem;">
            generations traced
          </div>
          <div style="margin-top: 1rem;">
            {#each Array(Math.min(generationDepth, 12)) as _, i}
              <div style="
                height: 4px;
                background: var(--accent);
                opacity: {1 - (i * 0.07)};
                margin: 3px auto;
                border-radius: 2px;
                width: {100 - (i * 6)}%;
              "></div>
            {/each}
          </div>
        </div>
      </div>
    </div>

    <!-- Row 3: Charts -->
    <div class="dashboard-grid-2 stagger-children" style="margin-bottom: 1.5rem;">
      <!-- Event Timeline -->
      <div class="arch-card" style="padding: 1.25rem;">
        <div class="arch-section-header">Event Timeline by Decade</div>
        <div style="height: 240px;">
          <canvas bind:this={timelineCanvas} style="width: 100%; height: 100%;"></canvas>
        </div>
      </div>

      <!-- Place Distribution -->
      <div class="arch-card" style="padding: 1.25rem;">
        <div class="arch-section-header">Top Places by Event Count</div>
        <div style="height: 240px;">
          <canvas bind:this={placesCanvas} style="width: 100%; height: 100%;"></canvas>
        </div>
      </div>
    </div>

    <!-- Row 4: Data Quality -->
    <div class="dashboard-grid-3 stagger-children">
      <!-- Missing Data -->
      <div class="arch-card" style="padding: 1.25rem;">
        <div class="arch-section-header">Missing Data</div>
        <div class="quality-list">
          <div class="quality-item">
            <div class="quality-bar-wrap">
              <div class="quality-bar" style="width: {totalPeople > 0 ? (missingBirth / totalPeople * 100) : 0}%; background: var(--color-warning);"></div>
            </div>
            <div class="quality-detail">
              <span class="quality-count">{fmtNum(missingBirth)}</span>
              <span class="quality-desc">without birth date</span>
            </div>
          </div>
          <div class="quality-item">
            <div class="quality-bar-wrap">
              <div class="quality-bar" style="width: {totalPeople > 0 ? (missingDeath / totalPeople * 100) : 0}%; background: var(--color-error);"></div>
            </div>
            <div class="quality-detail">
              <span class="quality-count">{fmtNum(missingDeath)}</span>
              <span class="quality-desc">deceased without death date</span>
            </div>
          </div>
          <div class="quality-item">
            <div class="quality-bar-wrap">
              <div class="quality-bar" style="width: {totalPeople > 0 ? (missingSources / totalPeople * 100) : 0}%; background: var(--ink-faint);"></div>
            </div>
            <div class="quality-detail">
              <span class="quality-count">{fmtNum(missingSources)}</span>
              <span class="quality-desc">without any source</span>
            </div>
          </div>
        </div>
      </div>

      <!-- Orphaned Records -->
      <div class="arch-card" style="padding: 1.25rem;">
        <div class="arch-section-header">Orphaned Records</div>
        <div style="text-align: center; padding: 1rem 0;">
          <div style="font-family: var(--font-serif); font-size: 2.5rem; font-weight: 700; color: {orphanedPeople > 0 ? 'var(--color-error)' : 'var(--color-validated)'}; line-height: 1;">
            {fmtNum(orphanedPeople)}
          </div>
          <div style="color: var(--ink-muted); font-size: 0.8rem; margin-top: 0.5rem;">
            people not linked to any family
          </div>
          {#if orphanedPeople > 0}
            <div style="margin-top: 0.75rem; font-size: 0.75rem; color: var(--color-warning); background: rgba(184, 134, 11, 0.08); padding: 0.4rem 0.75rem; border-radius: 0.5rem; display: inline-block;">
              Review recommended
            </div>
          {:else}
            <div style="margin-top: 0.75rem; font-size: 0.75rem; color: var(--color-validated); background: rgba(74, 124, 63, 0.08); padding: 0.4rem 0.75rem; border-radius: 0.5rem; display: inline-block;">
              All records connected
            </div>
          {/if}
        </div>
      </div>

      <!-- Most Documented -->
      <div class="arch-card" style="padding: 1.25rem;">
        <div class="arch-section-header">Most Documented Person</div>
        {#if mostDocumented}
          <div style="text-align: center; padding: 1rem 0;">
            <div style="width: 48px; height: 48px; border-radius: 50%; background: var(--accent-subtle); border: 2px solid var(--accent); display: flex; align-items: center; justify-content: center; margin: 0 auto 0.75rem;">
              <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="var(--accent)" stroke-width="2"><path d="M12 15l-2 5l9-11h-5l2-5l-9 11z"/></svg>
            </div>
            <div style="font-family: var(--font-serif); font-size: 1.1rem; font-weight: 600; color: var(--ink);">
              {mostDocumented.name}
            </div>
            <div style="color: var(--ink-muted); font-size: 0.75rem; margin-top: 0.25rem; font-family: var(--font-mono);">
              {mostDocumented.xref}
            </div>
            <div style="margin-top: 0.75rem; font-size: 0.85rem; color: var(--accent); font-weight: 600;">
              {fmtNum(mostDocumented.score)} total records
            </div>
            <div style="font-size: 0.7rem; color: var(--ink-faint); margin-top: 0.25rem;">
              events + sources + media
            </div>
          </div>
        {:else}
          <div style="text-align: center; padding: 2rem 0; color: var(--ink-faint); font-size: 0.85rem;">
            No data available
          </div>
        {/if}
      </div>
    </div>
  {/if}
</div>

<style>
  @keyframes spin {
    to { transform: rotate(360deg); }
  }

  /* ── Dashboard grid layouts ── */
  .dashboard-grid {
    display: grid;
    grid-template-columns: repeat(4, 1fr);
    gap: 1rem;
  }

  .dashboard-grid-2 {
    display: grid;
    grid-template-columns: repeat(2, 1fr);
    gap: 1rem;
  }

  .dashboard-grid-3 {
    display: grid;
    grid-template-columns: repeat(3, 1fr);
    gap: 1rem;
  }

  /* ── Stat cards ── */
  .stat-card {
    padding: 1.25rem;
    display: flex;
    flex-direction: column;
    gap: 0.5rem;
  }

  .stat-icon {
    width: 36px;
    height: 36px;
    border-radius: 0.5rem;
    display: flex;
    align-items: center;
    justify-content: center;
  }

  .stat-label {
    font-size: 0.7rem;
    font-weight: 600;
    text-transform: uppercase;
    letter-spacing: 0.08em;
    color: var(--ink-muted);
  }

  .stat-value {
    font-family: var(--font-serif);
    font-size: 2rem;
    font-weight: 700;
    color: var(--ink);
    line-height: 1;
  }

  .stat-breakdown {
    font-size: 0.75rem;
    color: var(--ink-muted);
    display: flex;
    flex-wrap: wrap;
    gap: 0.25rem;
    align-items: center;
  }

  .event-pill {
    background: var(--parchment);
    padding: 0.15rem 0.5rem;
    border-radius: 9999px;
    font-size: 0.65rem;
    color: var(--ink-light);
    white-space: nowrap;
  }

  /* ── Completeness legend ── */
  .completeness-row {
    display: flex;
    align-items: center;
    gap: 0.5rem;
    padding: 0.4rem 0;
    border-bottom: 1px solid var(--border-subtle);
  }

  .completeness-row:last-child {
    border-bottom: none;
  }

  .completeness-dot {
    width: 10px;
    height: 10px;
    border-radius: 50%;
    flex-shrink: 0;
  }

  .completeness-label {
    flex: 1;
    font-size: 0.8rem;
    color: var(--ink-light);
  }

  .completeness-pct {
    font-family: var(--font-mono);
    font-size: 0.8rem;
    font-weight: 600;
    color: var(--ink);
  }

  /* ── Data quality bars ── */
  .quality-list {
    display: flex;
    flex-direction: column;
    gap: 0.75rem;
  }

  .quality-item {
    display: flex;
    flex-direction: column;
    gap: 0.35rem;
  }

  .quality-bar-wrap {
    height: 6px;
    background: var(--parchment);
    border-radius: 3px;
    overflow: hidden;
  }

  .quality-bar {
    height: 100%;
    border-radius: 3px;
    transition: width 0.6s var(--ease-out);
  }

  .quality-detail {
    display: flex;
    align-items: baseline;
    gap: 0.35rem;
  }

  .quality-count {
    font-family: var(--font-mono);
    font-size: 0.85rem;
    font-weight: 600;
    color: var(--ink);
  }

  .quality-desc {
    font-size: 0.75rem;
    color: var(--ink-muted);
  }

  /* ── Responsive ── */
  @media (max-width: 1024px) {
    .dashboard-grid {
      grid-template-columns: repeat(2, 1fr);
    }
    .dashboard-grid-3 {
      grid-template-columns: repeat(2, 1fr);
    }
  }

  @media (max-width: 640px) {
    .dashboard-grid,
    .dashboard-grid-2,
    .dashboard-grid-3 {
      grid-template-columns: 1fr;
    }
  }
</style>
