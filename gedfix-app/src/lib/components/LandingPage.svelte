<script lang="ts">
  import { t } from '$lib/i18n';
  import { pickAndReadTextFile } from '$lib/platform-fs';
  import { importGedcom } from '$lib/gedcom-parser';
  import { getStats } from '$lib/db';
  import { appStats, isImporting, importProgress, importMessage } from '$lib/stores';

  let { onImported = () => {} }: { onImported?: () => void } = $props();

  async function importFromPicker() {
    const picked = await pickAndReadTextFile([{ name: 'GEDCOM', extensions: ['ged'] }]);
    if (!picked) return;
    await runImport(picked.text);
  }

  async function loadDemo() {
    const resp = await fetch('/sample.ged');
    const text = await resp.text();
    await runImport(text);
  }

  async function runImport(text: string) {
    $isImporting = true;
    $importProgress = 0;
    $importMessage = t('landing.importing');
    try {
      await importGedcom(text, (pct, msg) => {
        $importProgress = pct;
        $importMessage = msg;
      });
      appStats.set(await getStats());
      onImported();
    } finally {
      $isImporting = false;
      $importMessage = '';
    }
  }
</script>

<section class="landing-page">
  <div class="hero">
    <h1>GedFix</h1>
    <p>{t('landing.tagline')}</p>
    <div class="cta-row">
      <button class="btn-accent" onclick={importFromPicker}>{t('landing.getStarted')}</button>
      <button class="btn-secondary" onclick={loadDemo}>{t('landing.tryDemo')}</button>
    </div>
  </div>

  <div class="feature-grid">
    <article class="feature-card"><h2>{t('landing.importTitle')}</h2><p>{t('landing.importText')}</p></article>
    <article class="feature-card"><h2>{t('landing.visualizeTitle')}</h2><p>{t('landing.visualizeText')}</p></article>
    <article class="feature-card"><h2>{t('landing.researchTitle')}</h2><p>{t('landing.researchText')}</p></article>
    <article class="feature-card"><h2>{t('landing.cleanTitle')}</h2><p>{t('landing.cleanText')}</p></article>
    <article class="feature-card"><h2>{t('landing.collaborateTitle')}</h2><p>{t('landing.collaborateText')}</p></article>
    <article class="feature-card"><h2>{t('landing.analyzeTitle')}</h2><p>{t('landing.analyzeText')}</p></article>
  </div>
</section>

<style>
  .landing-page {
    min-height: 100%;
    padding: 2rem;
    background:
      radial-gradient(circle at 10% 0%, rgba(95,223,223,0.2), transparent 40%),
      radial-gradient(circle at 95% 0%, rgba(255,45,85,0.14), transparent 30%),
      linear-gradient(180deg, #0a0e14 0%, #111827 100%);
  }

  .hero {
    max-width: 760px;
    margin: 0 auto 2rem;
    text-align: center;
  }

  .hero h1 {
    margin: 0;
    font-family: 'Archivo Black', sans-serif;
    font-size: clamp(3rem, 7vw, 5rem);
    color: var(--ink);
    letter-spacing: 0.04em;
  }

  .hero p {
    margin-top: 0.75rem;
    color: var(--ink-light);
    font-size: clamp(1rem, 2.2vw, 1.35rem);
  }

  .cta-row {
    margin-top: 1.25rem;
    display: inline-flex;
    gap: 0.75rem;
    flex-wrap: wrap;
  }

  .feature-grid {
    max-width: 980px;
    margin: 0 auto;
    display: grid;
    grid-template-columns: repeat(3, minmax(0, 1fr));
    gap: 1rem;
  }

  .feature-card {
    background: color-mix(in srgb, var(--vellum) 85%, #000 15%);
    border: 1px solid var(--border-rule);
    border-radius: 12px;
    padding: 1rem;
  }

  .feature-card h2 {
    margin: 0 0 0.4rem;
    font-size: 1rem;
    color: var(--ink);
  }

  .feature-card p {
    margin: 0;
    color: var(--ink-muted);
    font-size: 0.92rem;
    line-height: 1.4;
  }

  @media (max-width: 768px) {
    .landing-page {
      padding: 1rem;
    }

    .feature-grid {
      grid-template-columns: 1fr;
    }
  }
</style>
