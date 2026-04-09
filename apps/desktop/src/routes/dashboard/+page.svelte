<script lang="ts">
  import { t } from '$lib/i18n';
  import { onMount } from 'svelte';
  import { getDb } from '$lib/db';

  // ── Reactive state ──
  let totalPeople = $state(0);
  let maleCount = $state(0);
  let femaleCount = $state(0);
  let totalFamilies = $state(0);
  let totalSources = $state(0);
  let avgCitationsPerSource = $state(0);
  // eslint-disable-next-line @typescript-eslint/no-unused-vars
  let totalEvents = $state(0);
  let totalMedia = $state(0);
  // eslint-disable-next-line @typescript-eslint/no-unused-vars
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

  // Source validation
  let validatedCount = $state(0);
  let treeOnlyCount = $state(0);
  let unvalidatedCount = $state(0);
  let validationPct = $state(0);
  let topSurnames = $state<{surname: string; count: number}[]>([]);
  let birthDecadeData = $state<{label: string; value: number}[]>([]);
  let lastImportDate = $state('');

  let loading = $state(true);

  // Canvas refs
  let donutCanvas = $state<HTMLCanvasElement | null>(null);
  let timelineCanvas = $state<HTMLCanvasElement | null>(null);
  let placesCanvas = $state<HTMLCanvasElement | null>(null);
  let themeTick = $state(0);

  interface ChartPalette {
    ink: string;
    muted: string;
    faint: string;
    accent: string;
    accentAlt: string;
    validated: string;
    media: string;
    grid: string;
  }

  function getChartPalette(): ChartPalette {
    const styles = getComputedStyle(document.documentElement);
    const read = (name: string, fallback: string) => styles.getPropertyValue(name).trim() || fallback;
    return {
      ink: read('--color-white', '#F0F4F8'),
      muted: read('--color-muted', '#6B7280'),
      faint: read('--ink-faint', 'rgba(240, 244, 248, 0.6)'),
      accent: read('--color-blue', '#1E9FF2'),
      accentAlt: read('--spirit-electric', '#8B5CF6'),
      validated: read('--color-cyan', '#5FDFDF'),
      media: read('--color-blue-dark', '#1578C2'),
      grid: 'rgba(95, 223, 223, 0.12)',
    };
  }

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

    const palette = getChartPalette();
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
    ctx.fillStyle = palette.ink;
    ctx.font = `bold ${Math.floor(outerR * 0.4)}px "Archivo Black", sans-serif`;
    ctx.textAlign = 'center';
    ctx.textBaseline = 'middle';
    ctx.fillText(`${Math.round(completenessScore)}%`, cx, cy - 6);
    ctx.font = `${Math.floor(outerR * 0.15)}px "JetBrains Mono", monospace`;
    ctx.fillStyle = palette.muted;
    ctx.fillText('complete', cx, cy + outerR * 0.18);
  }

  function drawTimelineChart(canvas: HTMLCanvasElement, data: {label: string; value: number}[]) {
    const ctx = canvas.getContext('2d')!;
    const palette = getChartPalette();
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
    ctx.strokeStyle = palette.grid;
    ctx.lineWidth = 1;
    for (let i = 0; i <= 4; i++) {
      const y = pad.top + plotH - (plotH * i / 4);
      ctx.beginPath();
      ctx.moveTo(pad.left, y);
      ctx.lineTo(pad.left + plotW, y);
      ctx.stroke();

      ctx.fillStyle = palette.muted;
      ctx.font = '10px "JetBrains Mono", monospace';
      ctx.textAlign = 'right';
      ctx.textBaseline = 'middle';
      ctx.fillText(String(Math.round(maxVal * i / 4)), pad.left - 6, y);
    }

    // Bars
    const gradient = ctx.createLinearGradient(0, pad.top, 0, pad.top + plotH);
    gradient.addColorStop(0, palette.accent);
    gradient.addColorStop(1, palette.accentAlt);

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
        ctx.fillStyle = palette.muted;
        ctx.font = '9px "JetBrains Mono", monospace';
        ctx.textAlign = 'center';
        ctx.textBaseline = 'top';
        ctx.fillText(d.label, x + barW / 2, pad.top + plotH + 6);
      }
    });
  }

  function drawHorizontalBarChart(canvas: HTMLCanvasElement, data: {label: string; value: number}[]) {
    const ctx = canvas.getContext('2d')!;
    const palette = getChartPalette();
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
    const colors = [
      palette.accent,
      palette.accentAlt,
      palette.validated,
      palette.media,
      palette.faint
    ];

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
      ctx.fillStyle = palette.ink;
      ctx.font = '11px "JetBrains Mono", monospace';
      ctx.textAlign = 'right';
      ctx.textBaseline = 'middle';
      const label = d.label.length > 18 ? d.label.slice(0, 16) + '...' : d.label;
      ctx.fillText(label, pad.left - 8, y + barH / 2);

      // Value
      ctx.fillStyle = palette.muted;
      ctx.font = '10px "JetBrains Mono", monospace';
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
    const [mediaStats] = await db.select<{cnt: number}[]>(`SELECT COUNT(*) as cnt FROM media`);
    totalMedia = mediaStats.cnt;

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

    // Source validation status
    const validationData = await db.select<{validationStatus: string; c: number}[]>(
      `SELECT COALESCE(validationStatus, 'unvalidated') as validationStatus, COUNT(*) as c FROM person GROUP BY validationStatus`
    );
    for (const row of validationData) {
      if (row.validationStatus === 'validated') validatedCount = row.c;
      else if (row.validationStatus === 'tree_only') treeOnlyCount = row.c;
      else unvalidatedCount += row.c;
    }
    validationPct = totalPeople > 0 ? Math.round((validatedCount / totalPeople) * 100) : 0;

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

    // Top 5 surnames
    const surnameData = await db.select<{surname: string; count: number}[]>(
      `SELECT surname, COUNT(*) as count FROM person WHERE surname != '' AND surname IS NOT NULL GROUP BY surname ORDER BY count DESC LIMIT 5`
    );
    topSurnames = surnameData;

    // Birth decade bar chart
    const bdData = await db.select<{decade: string; cnt: number}[]>(
      `SELECT (CAST(SUBSTR(birthDate, -4, 4) AS INTEGER) / 10 * 10) || 's' as decade, COUNT(*) as cnt
       FROM person WHERE birthDate != '' AND birthDate IS NOT NULL AND LENGTH(birthDate) >= 4
       AND CAST(SUBSTR(birthDate, -4, 4) AS INTEGER) > 1000
       GROUP BY CAST(SUBSTR(birthDate, -4, 4) AS INTEGER) / 10
       ORDER BY CAST(SUBSTR(birthDate, -4, 4) AS INTEGER) / 10`
    );
    birthDecadeData = bdData.map(d => ({ label: d.decade, value: d.cnt }));

    // Last import date from settings
    try {
      const lir = await db.select<{value: string}[]>(`SELECT value FROM settings WHERE key = 'last_import_date'`);
      if (lir[0]?.value) lastImportDate = lir[0].value;
    } catch { /* optional */ }

    loading = false;
  }

  onMount(() => {
    loadDashboardData();
  });

  onMount(() => {
    const root = document.documentElement;
    const observer = new MutationObserver(() => {
      themeTick += 1;
    });
    observer.observe(root, { attributes: true, attributeFilter: ['data-theme'] });
    return () => observer.disconnect();
  });

  // Draw charts when data and canvases are ready
  $effect(() => {
    // eslint-disable-next-line @typescript-eslint/no-unused-expressions
    themeTick;
    if (!loading && donutCanvas) {
      const palette = getChartPalette();
      drawDonutChart(donutCanvas, [
        {label: 'Birth Date', value: withBirthDate, color: palette.accent},
        {label: 'Death Date', value: withDeathDate, color: palette.accentAlt},
        {label: 'Sources', value: withSource, color: palette.validated},
        {label: 'Media', value: withMedia, color: palette.media},
      ]);
    }
  });

  $effect(() => {
    // eslint-disable-next-line @typescript-eslint/no-unused-expressions
    themeTick;
    if (!loading && timelineCanvas && eventsByDecade.length > 0) {
      drawTimelineChart(timelineCanvas, eventsByDecade);
    }
  });

  $effect(() => {
    // eslint-disable-next-line @typescript-eslint/no-unused-expressions
    themeTick;
    if (!loading && placesCanvas && topPlaces.length > 0) {
      drawHorizontalBarChart(placesCanvas, topPlaces);
    }
  });

  function fmtNum(n: number): string {
    return n.toLocaleString();
  }
</script>

<div class="dashboard-shell immersive-bg">
  <div class="dashboard-inner">
    <section class="hero">
      <div>
        <p class="hero-kicker">{t('dashboard.showcase')}</p>
        <h1 class="hero-title display-gradient-spirit">{t('dashboard.familyTreeTitle')}</h1>
        <p class="hero-subtitle">{t('dashboard.subtitle')}</p>
      </div>
      <div class="hero-actions">
        <a class="btn-primary" href="/settings">{t('common.import')}</a>
        <a class="btn-outline" href="/search">{t('common.search')}</a>
        <a class="btn-outline" href="/cleanup">{t('nav.cleanup')}</a>
        <a class="btn-outline" href="/media">{t('common.media')}</a>
      </div>
    </section>

    {#if loading}
      <div class="loading-state">
        <div class="spinner"></div>
        <p>{t('dashboard.loadingAnalytics')}</p>
      </div>
    {:else}
      <section class="stats-grid">
        <div class="spirit-surface stat-card">
          <div class="stat-label">{t('dashboard.people')}</div>
          <div class="stat-value">{fmtNum(totalPeople)}</div>
          <div class="stat-meta">{fmtNum(maleCount)} male · {fmtNum(femaleCount)} female</div>
        </div>
        <div class="spirit-surface stat-card">
          <div class="stat-label">{t('dashboard.families')}</div>
          <div class="stat-value">{fmtNum(totalFamilies)}</div>
          <div class="stat-meta">{totalPeople > 0 ? (totalFamilies / totalPeople * 100).toFixed(1) : 0}% family ratio</div>
        </div>
        <div class="spirit-surface stat-card">
          <div class="stat-label">{t('common.media')}</div>
          <div class="stat-value">{fmtNum(totalMedia)}</div>
          <div class="stat-meta">{withMedia}% of people with media</div>
        </div>
        <div class="spirit-surface stat-card">
          <div class="stat-label">{t('dashboard.sources')}</div>
          <div class="stat-value">{fmtNum(totalSources)}</div>
          <div class="stat-meta">{avgCitationsPerSource} avg citations/source</div>
        </div>
      </section>

      <section class="dashboard-grid-2">
        <div class="spirit-surface panel">
          <div class="panel-header">{t('dashboard.treeCompleteness')}</div>
          <div class="panel-body">
            <div class="donut-wrap">
              <canvas bind:this={donutCanvas}></canvas>
            </div>
            <div class="legend">
              <div class="legend-row">
                <span class="legend-dot" style="background: var(--color-blue);"></span>
                <span>{t('dashboard.birthDate')}</span>
                <span class="legend-value">{withBirthDate}%</span>
              </div>
              <div class="legend-row">
                <span class="legend-dot" style="background: var(--spirit-electric);"></span>
                <span>{t('dashboard.deathDate')}</span>
                <span class="legend-value">{withDeathDate}%</span>
              </div>
              <div class="legend-row">
                <span class="legend-dot" style="background: var(--color-cyan);"></span>
                <span>{t('dashboard.hasSource')}</span>
                <span class="legend-value">{withSource}%</span>
              </div>
              <div class="legend-row">
                <span class="legend-dot" style="background: var(--color-blue-dark);"></span>
                <span>{t('dashboard.hasMedia')}</span>
                <span class="legend-value">{withMedia}%</span>
              </div>
            </div>
          </div>
        </div>

        <div class="spirit-surface panel">
          <div class="panel-header">{t('dashboard.generationDepth')}</div>
          <div class="depth-wrap">
            <div class="depth-value display-gradient">{generationDepth}</div>
            <div class="depth-sub">{t('dashboard.generationsTraced')}</div>
            <div class="depth-bars">
              {#each Array(Math.min(generationDepth, 12)) as _, i}
                <div style="width: {100 - (i * 6)}%; opacity: {1 - (i * 0.07)};"></div>
              {/each}
            </div>
          </div>
        </div>
      </section>

      <section class="dashboard-grid-2">
        <div class="spirit-surface panel">
          <div class="panel-header">{t('dashboard.eventTimelineByDecade')}</div>
          <div class="chart-wrap">
            <canvas bind:this={timelineCanvas}></canvas>
          </div>
        </div>
        <div class="spirit-surface panel">
          <div class="panel-header">{t('dashboard.topPlacesByEventCount')}</div>
          <div class="chart-wrap">
            <canvas bind:this={placesCanvas}></canvas>
          </div>
        </div>
      </section>

      <section class="dashboard-grid-3">
        <div class="spirit-surface panel">
          <div class="panel-header">{t('dashboard.missingData')}</div>
          <div class="quality-list">
            <div>
              <div class="quality-bar"><span style="width: {totalPeople > 0 ? (missingBirth / totalPeople * 100) : 0}%; background: var(--color-warning);"></span></div>
              <div class="quality-meta">{fmtNum(missingBirth)} without birth date</div>
            </div>
            <div>
              <div class="quality-bar"><span style="width: {totalPeople > 0 ? (missingDeath / totalPeople * 100) : 0}%; background: var(--color-accent);"></span></div>
              <div class="quality-meta">{fmtNum(missingDeath)} deceased without death date</div>
            </div>
            <div>
              <div class="quality-bar"><span style="width: {totalPeople > 0 ? (missingSources / totalPeople * 100) : 0}%; background: rgba(240, 244, 248, 0.4);"></span></div>
              <div class="quality-meta">{fmtNum(missingSources)} without any source</div>
            </div>
          </div>
        </div>

        <div class="spirit-surface panel">
          <div class="panel-header">{t('dashboard.orphanedRecords')}</div>
          <div class="spotlight">
            <div class="spotlight-value" style="color: {orphanedPeople > 0 ? 'var(--color-accent)' : 'var(--color-cyan)'};">
              {fmtNum(orphanedPeople)}
            </div>
            <div class="spotlight-meta">people not linked to any family</div>
            <div class="spotlight-pill">{orphanedPeople > 0 ? 'Review recommended' : 'All records connected'}</div>
          </div>
        </div>

        <div class="spirit-surface panel">
          <div class="panel-header">Most Documented</div>
          {#if mostDocumented}
            <div class="spotlight">
              <div class="spotlight-name">{mostDocumented.name}</div>
              <div class="spotlight-meta">{mostDocumented.xref}</div>
              <div class="spotlight-value display-gradient">{fmtNum(mostDocumented.score)}</div>
              <div class="spotlight-meta">{t('people.eventsSourcesMedia')}</div>
            </div>
          {:else}
            <div class="empty-state">No data available</div>
          {/if}
        </div>
      </section>

      <section class="stats-grid" style="grid-template-columns: repeat(3, minmax(0, 1fr));">
        <div class="spirit-surface stat-card">
          <div class="stat-label" style="color: var(--color-cyan);">Validated</div>
          <div class="stat-value" style="color: var(--color-cyan);">{fmtNum(validatedCount)}</div>
          <div class="stat-meta">People with non-tree source records</div>
        </div>
        <div class="spirit-surface stat-card">
          <div class="stat-label" style="color: var(--color-warning, #F59E0B);">Tree Only</div>
          <div class="stat-value" style="color: var(--color-warning, #F59E0B);">{fmtNum(treeOnlyCount)}</div>
          <div class="stat-meta">Sourced only from online trees</div>
        </div>
        <div class="spirit-surface stat-card">
          <div class="stat-label" style="color: var(--color-accent);">Unvalidated</div>
          <div class="stat-value" style="color: var(--color-accent);">{fmtNum(unvalidatedCount)}</div>
          <div class="stat-meta">No source citations at all</div>
        </div>
      </section>

      <section class="spirit-surface panel">
        <div class="panel-header">Source Validation Progress</div>
        <div class="quality-bar" style="height: 12px; margin-bottom: 0.5rem;">
          <span style="width: {validationPct}%; background: var(--color-cyan);"></span>
        </div>
        <div class="stat-meta">{validationPct}% {t('dashboard.validatedPct')} — people validated with primary/secondary records</div>
      </section>

      <!-- Birth decade SVG bar chart + Top Surnames -->
      <section class="dashboard-grid-2">
        <div class="spirit-surface panel">
          <div class="panel-header">{t('dashboard.byDecade')} (births)</div>
          {#if birthDecadeData.length > 0}
            {@const maxVal = Math.max(...birthDecadeData.map(d => d.value), 1)}
            <div style="overflow-x: auto;">
              <svg width="100%" viewBox="0 0 {birthDecadeData.length * 40 + 40} 120" style="min-width: {birthDecadeData.length * 40}px;">
                {#each birthDecadeData as d, i}
                  {@const barH = Math.round((d.value / maxVal) * 80)}
                  <rect
                    x={i * 40 + 10}
                    y={90 - barH}
                    width="28"
                    height={barH}
                    rx="3"
                    fill="var(--color-blue, #1E9FF2)"
                    opacity="0.85"
                  />
                  <text x={i * 40 + 24} y="108" text-anchor="middle" font-size="7" fill="currentColor" opacity="0.6">{d.label}</text>
                  <text x={i * 40 + 24} y={87 - barH} text-anchor="middle" font-size="8" fill="currentColor" opacity="0.8">{d.value}</text>
                {/each}
              </svg>
            </div>
          {:else}
            <div class="empty-state">No birth data available</div>
          {/if}
        </div>

        <div class="spirit-surface panel">
          <div class="panel-header">{t('dashboard.topSurnames')}</div>
          {#if topSurnames.length > 0}
            {@const maxSurname = Math.max(...topSurnames.map(s => s.count), 1)}
            <div class="quality-list">
              {#each topSurnames as s}
                <div>
                  <div style="display:flex;justify-content:space-between;margin-bottom:2px;">
                    <span style="font-size:0.75rem;font-weight:600;color:var(--ink);">{s.surname}</span>
                    <span style="font-size:0.7rem;color:var(--ink-muted);font-family:var(--font-mono);">{s.count}</span>
                  </div>
                  <div class="quality-bar">
                    <span style="width: {(s.count / maxSurname * 100)}%; background: var(--color-cyan);"></span>
                  </div>
                </div>
              {/each}
            </div>
          {:else}
            <div class="empty-state">No surname data available</div>
          {/if}
          {#if lastImportDate}<div class="stat-meta" style="margin-top:0.5rem;">Last import: {lastImportDate}</div>{/if}
        </div>
      </section>
    {/if}
  </div>
</div>

<style>
  @keyframes spin {
    to { transform: rotate(360deg); }
  }

  .dashboard-shell {
    min-height: 100vh;
    overflow-y: auto;
  }

  .dashboard-inner {
    max-width: 88rem;
    margin: 0 auto;
    padding: 2.5rem 2.5rem 4rem;
    display: flex;
    flex-direction: column;
    gap: 2rem;
  }

  .hero {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 2rem;
  }

  .hero-kicker {
    font-family: var(--font-mono);
    text-transform: uppercase;
    letter-spacing: 0.2em;
    font-size: 0.7rem;
    color: var(--color-muted);
    margin: 0 0 0.5rem;
  }

  .hero-title {
    font-size: 2.8rem;
    margin: 0 0 0.6rem;
  }

  .hero-subtitle {
    color: var(--color-muted);
    max-width: 32rem;
  }

  .hero-actions {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 140px));
    gap: 0.75rem;
  }

  .loading-state {
    min-height: 320px;
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    gap: 0.75rem;
    color: var(--color-muted);
  }

  .spinner {
    width: 36px;
    height: 36px;
    border: 3px solid rgba(240, 244, 248, 0.2);
    border-top-color: var(--color-blue);
    border-radius: 50%;
    animation: spin 0.8s linear infinite;
  }

  .stats-grid {
    display: grid;
    grid-template-columns: repeat(4, minmax(0, 1fr));
    gap: 1rem;
  }

  .stat-card {
    padding: 1.5rem;
    border-radius: 1rem;
  }

  .stat-label {
    font-family: var(--font-mono);
    text-transform: uppercase;
    letter-spacing: 0.18em;
    font-size: 0.65rem;
    color: var(--color-muted);
  }

  .stat-value {
    font-family: var(--font-serif);
    font-size: 2.2rem;
    margin: 0.6rem 0 0.4rem;
  }

  .stat-meta {
    font-size: 0.85rem;
    color: var(--color-muted);
  }

  .dashboard-grid-2 {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 1.5rem;
  }

  .dashboard-grid-3 {
    display: grid;
    grid-template-columns: repeat(3, minmax(0, 1fr));
    gap: 1.5rem;
  }

  .panel {
    padding: 1.5rem;
    border-radius: 1rem;
  }

  .panel-header {
    font-family: var(--font-mono);
    text-transform: uppercase;
    letter-spacing: 0.16em;
    font-size: 0.7rem;
    color: var(--color-muted);
    margin-bottom: 1rem;
  }

  .panel-body {
    display: grid;
    grid-template-columns: 160px minmax(0, 1fr);
    gap: 1.5rem;
    align-items: center;
  }

  .donut-wrap {
    width: 160px;
    height: 160px;
  }

  .donut-wrap canvas {
    width: 160px;
    height: 160px;
  }

  .legend {
    display: grid;
    gap: 0.6rem;
  }

  .legend-row {
    display: grid;
    grid-template-columns: 12px 1fr auto;
    gap: 0.5rem;
    align-items: center;
    font-size: 0.9rem;
    color: var(--color-white);
  }

  .legend-dot {
    width: 10px;
    height: 10px;
    border-radius: 999px;
  }

  .legend-value {
    font-family: var(--font-mono);
    color: var(--color-muted);
  }

  .depth-wrap {
    text-align: center;
  }

  .depth-value {
    font-size: 3.2rem;
  }

  .depth-sub {
    color: var(--color-muted);
    font-size: 0.85rem;
    margin-top: 0.4rem;
  }

  .depth-bars {
    margin-top: 1rem;
  }

  .depth-bars div {
    height: 4px;
    background: var(--color-blue);
    margin: 4px auto;
    border-radius: 999px;
  }

  .chart-wrap {
    height: 240px;
  }

  .chart-wrap canvas {
    width: 100%;
    height: 100%;
  }

  .quality-list {
    display: grid;
    gap: 0.9rem;
  }

  .quality-bar {
    height: 6px;
    background: rgba(240, 244, 248, 0.12);
    border-radius: 999px;
    overflow: hidden;
  }

  .quality-bar span {
    display: block;
    height: 100%;
    border-radius: 999px;
  }

  .quality-meta {
    font-size: 0.85rem;
    color: var(--color-muted);
    margin-top: 0.4rem;
  }

  .spotlight {
    text-align: center;
    padding: 1rem 0;
  }

  .spotlight-value {
    font-family: var(--font-serif);
    font-size: 2.6rem;
  }

  .spotlight-meta {
    color: var(--color-muted);
    font-size: 0.8rem;
    margin-top: 0.4rem;
  }

  .spotlight-name {
    font-family: var(--font-serif);
    font-size: 1.1rem;
    margin-bottom: 0.3rem;
  }

  .spotlight-pill {
    margin-top: 0.7rem;
    display: inline-block;
    padding: 0.3rem 0.8rem;
    border-radius: 999px;
    background: rgba(30, 159, 242, 0.15);
    color: var(--color-blue);
    font-size: 0.7rem;
    text-transform: uppercase;
    letter-spacing: 0.12em;
    font-family: var(--font-mono);
  }

  .empty-state {
    text-align: center;
    color: var(--color-muted);
    padding: 1.5rem 0;
  }

  @media (max-width: 1100px) {
    .stats-grid {
      grid-template-columns: repeat(2, minmax(0, 1fr));
    }
    .dashboard-grid-3 {
      grid-template-columns: repeat(2, minmax(0, 1fr));
    }
  }

  @media (max-width: 820px) {
    .hero {
      flex-direction: column;
      align-items: flex-start;
    }
    .hero-actions {
      grid-template-columns: repeat(2, minmax(0, 1fr));
    }
    .panel-body {
      grid-template-columns: 1fr;
      justify-items: center;
    }
  }

  @media (max-width: 640px) {
    .dashboard-inner {
      padding: 2rem 1.25rem 3rem;
    }
    .stats-grid,
    .dashboard-grid-2,
    .dashboard-grid-3 {
      grid-template-columns: 1fr;
    }
  }
</style>
