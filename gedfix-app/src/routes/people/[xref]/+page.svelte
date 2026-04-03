<script lang="ts">
  import { t } from '$lib/i18n';
  import { page } from '$app/stores';
  import { goto } from '$app/navigation';
  import { getPerson, getEvents, getSpouseFamilies, getChildren, getParents, getMediaForPerson, getPrimaryPhoto, getDb, getNotes, getAlternateNames, insertAlternateName, updateAlternateName, deleteAlternateName, getPersons, isBookmarked, insertBookmark, getBookmarks, deleteBookmark } from '$lib/db';
  import { getThumbUrl } from '$lib/photo';
  import { isTauri } from '$lib/platform';
  import { lazyImage } from '$lib/lazy-image';
  import { focusTrap } from '$lib/accessibility';
  import type { Person, GedcomEvent, Family, GedcomMedia, ResearchNote, AlternateName } from '$lib/types';

  // --- State ---
  let person = $state<Person | null>(null);
  let events = $state.raw<GedcomEvent[]>([]);
  let parents = $state<{ father: Person | null; mother: Person | null }>({ father: null, mother: null });
  let spouseFamilies = $state.raw<{ family: Family; spouse: Person | null; children: Person[] }[]>([]);
  let media = $state.raw<(GedcomMedia & { isPrimary?: boolean; role?: string })[]>([]);
  let primaryPhoto = $state<GedcomMedia | null>(null);
  let photoUrl = $state('');
  let notes = $state.raw<ResearchNote[]>([]);
  let citations = $state.raw<{ sourceXref: string; sourceTitle: string; page: string; quality: string }[]>([]);
  let lightboxMedia = $state<GedcomMedia | null>(null);
  let loading = $state(true);
  let activeTab = $state<'overview' | 'names'>('overview');
  let currentPeople = $state.raw<Person[]>([]);
  let bookmarked = $state(false);
  let swipeStartX = $state<number | null>(null);
  let swipeDelta = $state(0);

  // Alternate names
  let alternateNames = $state.raw<AlternateName[]>([]);
  let altLoading = $state(false);
  let altError = $state('');
  let altDraft = $state({ givenName: '', surname: '', suffix: '', nameType: 'Hebrew', source: '' });
  let editingAltId = $state<number | null>(null);
  let editDraft = $state({ givenName: '', surname: '', suffix: '', nameType: 'Hebrew', source: '' });
  const nameTypes = ['Hebrew','Yiddish','Polish','German','Married','Maiden','Nickname','Religious','Legal'];

  // Tauri file src converter
  let _convertFileSrc: ((path: string) => string) | null = null;
  async function initFileSrc() {
    if (isTauri()) {
      const mod = await import('@tauri-apps/api/core');
      _convertFileSrc = mod.convertFileSrc;
    }
  }
  function convertFileSrc(path: string): string {
    if (_convertFileSrc) return _convertFileSrc(path);
    return path;
  }

  // --- Event labels ---
  const eventLabels: Record<string, string> = {
    BIRT: 'Birth', DEAT: 'Death', BURI: 'Burial', CHR: 'Christening',
    BAPM: 'Baptism', MARR: 'Marriage', DIV: 'Divorce', RESI: 'Residence',
    OCCU: 'Occupation', EDUC: 'Education', EMIG: 'Emigration', IMMI: 'Immigration',
    NATU: 'Naturalization', CENS: 'Census', PROB: 'Probate', WILL: 'Will', EVEN: 'Event',
    RELI: 'Religion', GRAD: 'Graduation', MILI: 'Military',
  };

  const eventIcons: Record<string, string> = {
    BIRT: '\u2605', DEAT: '\u271D', BURI: '\u26B0', CHR: '\u2720',
    BAPM: '\u2720', MARR: '\uD83D\uDC8D', DIV: '\u2702', RESI: '\uD83C\uDFE0',
    OCCU: '\uD83D\uDCBC', EDUC: '\uD83C\uDF93', EMIG: '\uD83D\uDEA2', IMMI: '\uD83D\uDEA2',
    NATU: '\uD83C\uDDFA\uD83C\uDDF8', CENS: '\uD83D\uDCCB', PROB: '\u2696', WILL: '\uD83D\uDCDC', EVEN: '\uD83D\uDD39',
    RELI: '\u271E', GRAD: '\uD83C\uDF93', MILI: '\u2694',
  };

  // --- Helpers ---
  function getInitials(p: Person): string {
    return ((p.givenName?.[0] ?? '') + (p.surname?.[0] ?? '')).toUpperCase() || '?';
  }

  function avatarColor(p: Person): string {
    return p.personColor || (p.sex === 'F' ? '#D94A8C' : '#4A90D9');
  }

  function fullName(p: Person): string {
    const parts = [p.givenName, p.surname].filter(Boolean);
    if (p.suffix) parts.push(p.suffix);
    return parts.join(' ') || 'Unknown';
  }

  function genderIcon(sex: string): string {
    if (sex === 'M') return '\u2642';
    if (sex === 'F') return '\u2640';
    return '\u26A5';
  }

  function parseYear(dateStr: string): number | null {
    if (!dateStr) return null;
    const m = dateStr.match(/(\d{4})/);
    return m ? parseInt(m[1]) : null;
  }

  function calcAge(birthDate: string, deathDate: string): string | null {
    const by = parseYear(birthDate);
    const dy = parseYear(deathDate);
    if (by == null || dy == null) return null;
    const age = dy - by;
    return age >= 0 ? `${age}` : null;
  }

  function isImage(path: string): boolean {
    return /\.(jpe?g|png|gif|bmp|webp)$/i.test(path);
  }

  function qualityLabel(q: string): string {
    const labels: Record<string, string> = {
      PRIMARY: 'Primary', SECONDARY: 'Secondary', QUESTIONABLE: 'Questionable', UNKNOWN: 'Unknown'
    };
    return labels[q] || q || 'Unknown';
  }

  function qualityColor(q: string): string {
    const colors: Record<string, string> = {
      PRIMARY: '#2D7D46', SECONDARY: '#4A90D9', QUESTIONABLE: '#C9880E', UNKNOWN: '#7A6F62'
    };
    return colors[q] || '#7A6F62';
  }

  function handleLightboxKeydown(e: KeyboardEvent) {
    if (e.key === 'Escape') lightboxMedia = null;
  }

  // --- Data loading ---
  async function loadPerson(xref: string) {
    loading = true;
    person = null;
    events = [];
    parents = { father: null, mother: null };
    spouseFamilies = [];
    media = [];
    primaryPhoto = null;
    photoUrl = '';
    notes = [];
    citations = [];
    await initFileSrc();

    const p = await getPerson(xref);
    if (!p) {
      loading = false;
      return;
    }
    person = p;
    currentPeople = await getPersons('', 5000);
    bookmarked = await isBookmarked(xref);

    // Parallel data fetching
    const [evts, par, med, photo, fams, personNotes, altNames] = await Promise.all([
      getEvents(xref),
      getParents(xref),
      getMediaForPerson(xref),
      getPrimaryPhoto(xref),
      getSpouseFamilies(xref),
      getNotes(xref),
      getAlternateNames(xref),
    ]);

    events = evts;
    parents = par;
    media = med;
    primaryPhoto = photo;
    notes = personNotes;
    alternateNames = altNames;

    // Load photo URL
    if (photo?.filePath) {
      try { photoUrl = await getThumbUrl(photo.filePath, 200); } catch { photoUrl = ''; }
    } else {
      photoUrl = '';
    }

    // Load family details
    const famDetails = await Promise.all(fams.map(async (f) => {
      const spouseXref = f.partner1Xref === xref ? f.partner2Xref : f.partner1Xref;
      const [spouse, children] = await Promise.all([
        spouseXref ? getPerson(spouseXref) : Promise.resolve(null),
        getChildren(f.xref),
      ]);
      return { family: f, spouse, children };
    }));
    spouseFamilies = famDetails;

    // Load citations for this person
    try {
      const db = await getDb();
      const rows = await db.select<{ sourceXref: string; sourceTitle: string; page: string; quality: string }[]>(
        `SELECT c.sourceXref, COALESCE(s.title, c.sourceXref) as sourceTitle, c.page, c.quality
         FROM citation c
         LEFT JOIN source s ON s.xref = c.sourceXref
         WHERE c.personXref = $1
         ORDER BY s.title`,
        [xref]
      );
      citations = rows;
    } catch { citations = []; }

    loading = false;
  }

  async function toggleBookmark(): Promise<void> {
    const current = person;
    if (!current) return;
    if (!bookmarked) {
      await insertBookmark({
        personXref: current.xref,
        label: fullName(current),
        category: 'General',
        sortOrder: undefined,
        createdAt: new Date().toISOString(),
      });
      bookmarked = true;
      return;
    }
    const all = await getBookmarks();
    const existing = all.find((b) => b.personXref === current.xref);
    if (existing) await deleteBookmark(existing.id);
    bookmarked = false;
  }

  function navigateRelative(step: -1 | 1): void {
    if (!person || currentPeople.length === 0) return;
    const idx = currentPeople.findIndex((p) => p.xref === person!.xref);
    if (idx < 0) return;
    const nextIdx = (idx + step + currentPeople.length) % currentPeople.length;
    const next = currentPeople[nextIdx];
    if (next) goto(`/people/${next.xref}`);
  }

  function handleShortcut(e: KeyboardEvent): void {
    const target = e.target as HTMLElement | null;
    if (target && (target.tagName === 'INPUT' || target.tagName === 'TEXTAREA' || target.isContentEditable)) return;
    const key = e.key.toLowerCase();
    if (key === 'e') {
      activeTab = 'names';
      e.preventDefault();
      return;
    }
    if (key === 'b') {
      toggleBookmark();
      e.preventDefault();
      return;
    }
    if (e.key === '[') {
      navigateRelative(-1);
      e.preventDefault();
      return;
    }
    if (e.key === ']') {
      navigateRelative(1);
      e.preventDefault();
    }
  }

  function handleSwipeStart(e: TouchEvent): void {
    const start = e.changedTouches[0]?.clientX ?? 0;
    if (start > 24) return;
    swipeStartX = start;
    swipeDelta = 0;
  }

  function handleSwipeMove(e: TouchEvent): void {
    if (swipeStartX === null) return;
    const current = e.changedTouches[0]?.clientX ?? 0;
    swipeDelta = Math.max(0, current - swipeStartX);
  }

  function handleSwipeEnd(): void {
    if (swipeStartX !== null && swipeDelta > 100) history.back();
    swipeStartX = null;
    swipeDelta = 0;
  }

  async function refreshAlternateNames() {
    if (!person) return;
    altLoading = true;
    altError = '';
    try {
      alternateNames = await getAlternateNames(person.xref);
    } catch (e) {
      altError = String(e);
    }
    altLoading = false;
  }

  async function addAlternateName() {
    if (!person) return;
    if (!altDraft.givenName.trim() && !altDraft.surname.trim()) return;
    await insertAlternateName({
      personXref: person.xref,
      givenName: altDraft.givenName.trim(),
      surname: altDraft.surname.trim(),
      suffix: altDraft.suffix.trim(),
      nameType: altDraft.nameType,
      source: altDraft.source.trim(),
    });
    altDraft = { givenName: '', surname: '', suffix: '', nameType: 'Hebrew', source: '' };
    await refreshAlternateNames();
  }

  function startEditAlt(a: AlternateName) {
    editingAltId = a.id;
    editDraft = {
      givenName: a.givenName || '',
      surname: a.surname || '',
      suffix: a.suffix || '',
      nameType: a.nameType || 'Hebrew',
      source: a.source || '',
    };
  }

  function cancelEditAlt() {
    editingAltId = null;
  }

  async function saveEditAlt(id: number) {
    await updateAlternateName(id, {
      givenName: editDraft.givenName.trim(),
      surname: editDraft.surname.trim(),
      suffix: editDraft.suffix.trim(),
      nameType: editDraft.nameType,
      source: editDraft.source.trim(),
    });
    editingAltId = null;
    await refreshAlternateNames();
  }

  async function removeAlt(id: number) {
    await deleteAlternateName(id);
    await refreshAlternateNames();
  }

  // Mini card photo cache
  let miniPhotoCache = $state.raw<Map<string, string>>(new Map());

  async function loadMiniPhoto(xref: string): Promise<string> {
    if (miniPhotoCache.has(xref)) return miniPhotoCache.get(xref)!;
    const photo = await getPrimaryPhoto(xref);
    if (photo?.filePath) {
      const updates = new Map(miniPhotoCache);
      updates.set(xref, photo.filePath);
      miniPhotoCache = updates;
      return photo.filePath;
    }
    return '';
  }

  // React to route param changes
  $effect(() => {
    const xref = $page.params.xref;
    if (xref) loadPerson(xref);
  });

  $effect(() => {
    window.addEventListener('keydown', handleShortcut);
    return () => window.removeEventListener('keydown', handleShortcut);
  });
</script>

{#if loading}
  <div class="flex items-center justify-center h-full" style="color: var(--ink-muted);">
    <div class="text-center">
      <div class="text-lg mb-2" style="font-family: var(--font-heading);">{t('people.loadingPerson')}</div>
      <div class="text-sm" style="color: var(--ink-faint);">{t('people.fetchingBio')}</div>
    </div>
  </div>
{:else if !person}
  <div class="flex items-center justify-center h-full" style="color: var(--ink-muted);">
    <div class="text-center">
      <div class="text-2xl mb-2">{t('people.notFound')}</div>
      <button onclick={() => goto('/people')} class="arch-btn mt-4">{t('people.backToPeople')}</button>
    </div>
  </div>
{:else}
  <!-- Back navigation -->
  <div class="sticky top-0 z-10 px-6 py-3 flex items-center gap-3" style="background: var(--vellum); border-bottom: 1px solid var(--border-rule);">
    <button onclick={() => goto('/people')} class="flex items-center gap-1.5 text-sm transition-colors hover:opacity-70" style="color: var(--accent);">
      <span style="font-size: 1.1em;">&larr;</span> {t('people.backToPeople')}
    </button>
    <span style="color: var(--ink-faint);">/</span>
    <span class="text-sm font-medium" style="color: var(--ink-light);">{fullName(person)}</span>
  </div>

  <div class="person-detail-page" role="region" aria-label={t('people.title')} ontouchstart={handleSwipeStart} ontouchmove={handleSwipeMove} ontouchend={handleSwipeEnd}>
    <div class="swipe-back-indicator" style="opacity: {Math.min(swipeDelta / 120, 1)};">←</div>
    <!-- ===== Two-column layout ===== -->
    <div class="detail-layout">
      <!-- ===== MAIN CONTENT (70%) ===== -->
      <div class="main-content">

        <!-- ===== HEADER CARD ===== -->
        <section class="bio-header">
          <div class="header-photo">
            {#if photoUrl}
              <img src={photoUrl} alt={fullName(person)} class="header-photo-img" onerror={() => { photoUrl = ''; }} />
            {:else}
              <div class="header-initials" style="background: {avatarColor(person)};">
                {getInitials(person)}
              </div>
            {/if}
          </div>
          <div class="header-info">
            <div class="flex items-center gap-2 flex-wrap">
              <h1 class="header-name">{fullName(person)}</h1>
              <span class="gender-icon" title={person.sex === 'M' ? 'Male' : person.sex === 'F' ? 'Female' : 'Unknown'}>{genderIcon(person.sex)}</span>
            </div>
            <div class="header-dates">
              {#if person.birthDate}
                <span>b. {person.birthDate}{#if person.birthPlace} &middot; {person.birthPlace}{/if}</span>
              {/if}
              {#if person.deathDate}
                <span class="ml-1">&ndash; d. {person.deathDate}{#if person.deathPlace} &middot; {person.deathPlace}{/if}</span>
              {/if}
            </div>
            {#if calcAge(person.birthDate, person.deathDate)}
              <div class="header-age">
                Lived to approximately {calcAge(person.birthDate, person.deathDate)} years
              </div>
            {/if}
            <div class="header-actions">
              <button class="arch-btn arch-btn-sm" onclick={() => { activeTab = 'names'; }} title={t('people.editDetails')}>Edit</button>
              <button class="arch-btn arch-btn-sm" onclick={toggleBookmark} aria-label={bookmarked ? t('people.removeBookmark') : t('common.bookmark')}>
                {bookmarked ? t('people.bookmarked') : t('common.bookmark')}
              </button>
            </div>
          </div>
        </section>

        <div class="arch-card tab-bar mb-6">
          <button class="tab-btn {activeTab === 'overview' ? 'active' : ''}" onclick={() => activeTab = 'overview'}>{t('common.overview')}</button>
          <button class="tab-btn {activeTab === 'names' ? 'active' : ''}" onclick={() => activeTab = 'names'}>{t('people.names')}</button>
        </div>

        {#if activeTab === 'overview'}
        <!-- ===== SECTION 1: LIFE TIMELINE ===== -->
        {#if events.length > 0}
          <section class="detail-section">
            <h2 class="section-heading">{t('people.lifeTimeline')}</h2>
            <div class="timeline">
              {#each events as evt, i}
                <div class="timeline-item">
                  <div class="timeline-line">
                    <div class="timeline-dot" class:timeline-dot-birth={evt.eventType === 'BIRT'} class:timeline-dot-death={evt.eventType === 'DEAT'} class:timeline-dot-marriage={evt.eventType === 'MARR'}>
                      <span class="timeline-icon">{eventIcons[evt.eventType] || '\u25CF'}</span>
                    </div>
                    {#if i < events.length - 1}
                      <div class="timeline-connector"></div>
                    {/if}
                  </div>
                  <div class="timeline-content">
                    <div class="timeline-event-header">
                      <span class="event-badge">{eventLabels[evt.eventType] || evt.eventType}</span>
                      {#if evt.dateValue}
                        <span class="event-date">{evt.dateValue}</span>
                      {/if}
                    </div>
                    {#if evt.place}
                      <div class="event-place">{evt.place}</div>
                    {/if}
                    {#if evt.description}
                      <div class="event-desc">{evt.description}</div>
                    {/if}
                  </div>
                </div>
              {/each}
            </div>
          </section>
        {/if}

        <!-- ===== SECTION 2: FAMILY ===== -->
        <section class="detail-section">
          <h2 class="section-heading">{t('common.family')}</h2>

          <!-- Parents -->
          {#if parents.father || parents.mother}
            <h3 class="subsection-heading">{t('people.parents')}</h3>
            <div class="person-cards">
              {#if parents.father}
                {@const f = parents.father}
                <button class="mini-card" onclick={() => goto(`/people/${encodeURIComponent(f.xref)}`)}>
                  {#await loadMiniPhoto(f.xref) then filePath}
                    {#if filePath}
                      <img use:lazyImage={filePath} alt="" class="mini-card-photo" />
                    {:else}
                      <div class="mini-card-initials" style="background: {avatarColor(f)};">{getInitials(f)}</div>
                    {/if}
                  {/await}
                  <div class="mini-card-info">
                    <div class="mini-card-name">{fullName(f)}</div>
                    <div class="mini-card-dates">
                      {#if f.birthDate}b. {f.birthDate}{/if}
                      {#if f.deathDate} &ndash; d. {f.deathDate}{/if}
                    </div>
                  </div>
                </button>
              {/if}
              {#if parents.mother}
                {@const m = parents.mother}
                <button class="mini-card" onclick={() => goto(`/people/${encodeURIComponent(m.xref)}`)}>
                  {#await loadMiniPhoto(m.xref) then filePath}
                    {#if filePath}
                      <img use:lazyImage={filePath} alt="" class="mini-card-photo" />
                    {:else}
                      <div class="mini-card-initials" style="background: {avatarColor(m)};">{getInitials(m)}</div>
                    {/if}
                  {/await}
                  <div class="mini-card-info">
                    <div class="mini-card-name">{fullName(m)}</div>
                    <div class="mini-card-dates">
                      {#if m.birthDate}b. {m.birthDate}{/if}
                      {#if m.deathDate} &ndash; d. {m.deathDate}{/if}
                    </div>
                  </div>
                </button>
              {/if}
            </div>
          {/if}

          <!-- Spouses & Children -->
          {#each spouseFamilies as fam}
            {#if fam.spouse}
              <h3 class="subsection-heading">
                {t('families.spouse')}
                {#if fam.family.marriageDate} &middot; m. {fam.family.marriageDate}{/if}
                {#if fam.family.marriagePlace} &middot; {fam.family.marriagePlace}{/if}
              </h3>
              <div class="person-cards">
                {#if fam.spouse}
                {@const sp = fam.spouse}
                <button class="mini-card" onclick={() => goto(`/people/${encodeURIComponent(sp.xref)}`)}>
                  {#await loadMiniPhoto(sp.xref) then filePath}
                    {#if filePath}
                      <img use:lazyImage={filePath} alt="" class="mini-card-photo" />
                    {:else}
                      <div class="mini-card-initials" style="background: {avatarColor(sp)};">{getInitials(sp)}</div>
                    {/if}
                  {/await}
                  <div class="mini-card-info">
                    <div class="mini-card-name">{fullName(sp)}</div>
                    <div class="mini-card-dates">
                      {#if sp.birthDate}b. {sp.birthDate}{/if}
                      {#if sp.deathDate} &ndash; d. {sp.deathDate}{/if}
                    </div>
                  </div>
                </button>
                {/if}
              </div>
            {/if}

            {#if fam.children.length > 0}
              <h3 class="subsection-heading">{t('families.children')}</h3>
              <div class="person-cards">
                {#each fam.children as child}
                  <button class="mini-card" onclick={() => goto(`/people/${encodeURIComponent(child.xref)}`)}>
                    {#await loadMiniPhoto(child.xref) then filePath}
                      {#if filePath}
                        <img use:lazyImage={filePath} alt="" class="mini-card-photo" />
                      {:else}
                        <div class="mini-card-initials" style="background: {avatarColor(child)};">{getInitials(child)}</div>
                      {/if}
                    {/await}
                    <div class="mini-card-info">
                      <div class="mini-card-name">{fullName(child)}</div>
                      <div class="mini-card-dates">
                        {#if child.birthDate}b. {child.birthDate}{/if}
                        {#if child.deathDate} &ndash; d. {child.deathDate}{/if}
                      </div>
                    </div>
                  </button>
                {/each}
              </div>
            {/if}
          {/each}

          {#if !parents.father && !parents.mother && spouseFamilies.length === 0}
            <div class="empty-state">{t('relationship.noConnection')}</div>
          {/if}
        </section>

        <!-- ===== SECTION 3: MEDIA GALLERY ===== -->
        {#if media.length > 0}
          <section class="detail-section">
            <h2 class="section-heading">{t('people.mediaGallery')}</h2>
            <div class="media-grid">
              {#each media as m}
                {#if isImage(m.filePath)}
                  <button class="media-thumb" onclick={() => lightboxMedia = m}>
                    <img use:lazyImage={m.filePath} alt={m.title || 'Photo'} class="media-thumb-img" />
                    {#if m.title}
                      <div class="media-thumb-caption">{m.title}</div>
                    {/if}
                  </button>
                {:else}
                  <div class="media-thumb media-thumb-file">
                    <div class="media-file-icon">{m.format || 'FILE'}</div>
                    {#if m.title}
                      <div class="media-thumb-caption">{m.title}</div>
                    {/if}
                  </div>
                {/if}
              {/each}
            </div>
          </section>
        {/if}

        <!-- ===== SECTION 4: SOURCES & CITATIONS ===== -->
        {#if citations.length > 0}
          <section class="detail-section">
            <h2 class="section-heading">{t('people.sourcesCitations')}</h2>
            <div class="citations-list">
              {#each citations as cit}
                <div class="citation-row">
                  <div class="citation-title">{cit.sourceTitle}</div>
                  <div class="citation-meta">
                    {#if cit.page}
                      <span class="citation-page">p. {cit.page}</span>
                    {/if}
                    <span class="citation-quality" style="color: {qualityColor(cit.quality)};">
                      {qualityLabel(cit.quality)}
                    </span>
                  </div>
                </div>
              {/each}
            </div>
          </section>
        {/if}

        <!-- ===== SECTION 5: NOTES ===== -->
        {#if notes.length > 0}
          <section class="detail-section">
            <h2 class="section-heading">{t('nav.notes')}</h2>
            {#each notes as note}
              <div class="note-card">
                {#if note.title}
                  <div class="note-title">{note.title}</div>
                {/if}
                <div class="note-content">{note.content}</div>
                <div class="note-date">{note.updatedAt || note.createdAt}</div>
              </div>
            {/each}
          </section>
        {/if}
        {:else}
          <section class="detail-section">
            <h2 class="section-heading">{t('people.alternateNames')}</h2>
            <p class="text-xs text-ink-muted mb-4">{t('people.alternateNamesHelp')}</p>

            <div class="arch-card rounded-xl p-4 mb-4">
              <div class="grid grid-cols-1 md:grid-cols-2 gap-3">
                <input class="arch-input px-3 py-2 text-sm rounded-lg" placeholder={t('people.givenName')} bind:value={altDraft.givenName}  aria-label={t('people.givenName')} />
                <input class="arch-input px-3 py-2 text-sm rounded-lg" placeholder={t('people.surname')} bind:value={altDraft.surname}  aria-label={t('people.surname')} />
                <input class="arch-input px-3 py-2 text-sm rounded-lg" placeholder={t('people.suffixOptional')} bind:value={altDraft.suffix}  aria-label={t('people.suffixOptional')} />
                <select class="arch-input px-3 py-2 text-sm rounded-lg" bind:value={altDraft.nameType} aria-label={t('common.filter')}>
                  {#each nameTypes as t}<option value={t}>{t}</option>{/each}
                </select>
                <input class="arch-input px-3 py-2 text-sm rounded-lg md:col-span-2" placeholder={t('evidence.sourceNotes')} bind:value={altDraft.source}  aria-label={t('evidence.sourceNotes')} />
              </div>
              <div class="mt-3">
                <button class="btn-accent px-4 py-2 text-sm" onclick={addAlternateName} aria-label={t('common.actions')}>{t('people.addAlternateName')}</button>
              </div>
            </div>

            {#if altLoading}
              <div class="text-sm text-ink-faint py-4 text-center">{t('people.loadingAlternateNames')}</div>
            {:else if altError}
              <div class="text-xs text-ink-muted">{altError}</div>
            {:else if alternateNames.length === 0}
              <div class="text-sm text-ink-faint py-8 text-center">{t('people.noAlternateNames')}</div>
            {:else}
              <div class="arch-card rounded-xl divide-y arch-card-divide">
                {#each alternateNames as a}
                  <div class="px-4 py-3">
                    {#if editingAltId === a.id}
                      <div class="grid grid-cols-1 md:grid-cols-2 gap-2">
                        <input class="arch-input px-3 py-2 text-sm rounded-lg" bind:value={editDraft.givenName}  aria-label={t('common.search')} />
                        <input class="arch-input px-3 py-2 text-sm rounded-lg" bind:value={editDraft.surname}  aria-label={t('common.search')} />
                        <input class="arch-input px-3 py-2 text-sm rounded-lg" bind:value={editDraft.suffix}  aria-label={t('common.search')} />
                        <select class="arch-input px-3 py-2 text-sm rounded-lg" bind:value={editDraft.nameType} aria-label={t('common.filter')}>
                          {#each nameTypes as t}<option value={t}>{t}</option>{/each}
                        </select>
                        <input class="arch-input px-3 py-2 text-sm rounded-lg md:col-span-2" bind:value={editDraft.source}  aria-label={t('common.search')} />
                      </div>
                      <div class="mt-2 flex gap-2">
                        <button class="btn-accent px-3 py-1.5 text-xs" onclick={() => saveEditAlt(a.id)}>{t('common.save')}</button>
                        <button class="btn-secondary px-3 py-1.5 text-xs" onclick={cancelEditAlt} aria-label={t('common.actions')}>{t('common.cancel')}</button>
                      </div>
                    {:else}
                      <div class="flex items-start justify-between gap-3">
                        <div>
                          <div class="text-sm font-medium text-ink">{a.givenName} {a.surname} {a.suffix}</div>
                          <div class="text-xs text-ink-muted">{a.nameType}{a.source ? ` • ${a.source}` : ''}</div>
                        </div>
                        <div class="flex gap-2">
                          <button class="btn-secondary px-3 py-1.5 text-xs" onclick={() => startEditAlt(a)}>{t('common.edit')}</button>
                          <button class="px-3 py-1.5 text-xs rounded-lg" style="background: var(--parchment); color: var(--color-error);" onclick={() => removeAlt(a.id)}>{t('common.delete')}</button>
                        </div>
                      </div>
                    {/if}
                  </div>
                {/each}
              </div>
            {/if}
          </section>
        {/if}

      </div>

      <!-- ===== SIDEBAR (30%) ===== -->
      <aside class="sidebar">
        <div class="sidebar-card">
          <h3 class="sidebar-heading">{t('people.quickFacts')}</h3>
          <dl class="fact-list">
            <div class="fact-row">
              <dt>{t('people.givenName')}</dt>
              <dd>{person.givenName || '\u2014'}</dd>
            </div>
            <div class="fact-row">
              <dt>{t('people.surname')}</dt>
              <dd>{person.surname || '\u2014'}</dd>
            </div>
            {#if person.suffix}
              <div class="fact-row">
                <dt>{t('people.suffix')}</dt>
                <dd>{person.suffix}</dd>
              </div>
            {/if}
            <div class="fact-row">
              <dt>{t('people.sex')}</dt>
              <dd>{person.sex === 'M' ? t('people.male') : person.sex === 'F' ? t('people.female') : t('common.unknown')}</dd>
            </div>
            <div class="fact-row">
              <dt>{t('common.born')}</dt>
              <dd>{person.birthDate || '\u2014'}</dd>
            </div>
            {#if person.birthPlace}
              <div class="fact-row">
                <dt>{t('people.birthPlace')}</dt>
                <dd>{person.birthPlace}</dd>
              </div>
            {/if}
            <div class="fact-row">
              <dt>{t('common.died')}</dt>
              <dd>{person.deathDate || '\u2014'}</dd>
            </div>
            {#if person.deathPlace}
              <div class="fact-row">
                <dt>{t('people.deathPlace')}</dt>
                <dd>{person.deathPlace}</dd>
              </div>
            {/if}
            {#if calcAge(person.birthDate, person.deathDate)}
              <div class="fact-row">
                <dt>{t('people.ageAtDeath')}</dt>
                <dd>~{calcAge(person.birthDate, person.deathDate)} {t('common.years')}</dd>
              </div>
            {/if}
            <div class="fact-row">
              <dt>{t('people.living')}</dt>
              <dd>{person.isLiving ? t('common.yes') : t('common.no')}</dd>
            </div>
            <div class="fact-row">
              <dt>XREF</dt>
              <dd class="font-mono text-xs" style="color: var(--ink-faint);">{person.xref}</dd>
            </div>
          </dl>
        </div>

        <!-- Sidebar stats -->
        <div class="sidebar-card">
          <h3 class="sidebar-heading">{t('people.recordStats')}</h3>
          <dl class="fact-list">
            <div class="fact-row">
              <dt>{t('common.events')}</dt>
              <dd>{events.length}</dd>
            </div>
            <div class="fact-row">
              <dt>{t('common.media')}</dt>
              <dd>{media.length}</dd>
            </div>
            <div class="fact-row">
              <dt>{t('dashboard.sources')}</dt>
              <dd>{citations.length}</dd>
            </div>
            <div class="fact-row">
              <dt>{t('nav.notes')}</dt>
              <dd>{notes.length}</dd>
            </div>
            <div class="fact-row">
              <dt>{t('people.proofStatus')}</dt>
              <dd>
                <span class="proof-badge proof-{person.proofStatus.toLowerCase()}">{person.proofStatus}</span>
              </dd>
            </div>
          </dl>
        </div>

        <!-- Sidebar families -->
        {#if spouseFamilies.length > 0}
          <div class="sidebar-card">
            <h3 class="sidebar-heading">{t('dashboard.families')}</h3>
            {#each spouseFamilies as fam}
              <div class="sidebar-family">
                {#if fam.spouse}
                  <div class="text-sm" style="color: var(--ink-light);">
                    {t('families.marriage')} {fam.spouse.givenName} {fam.spouse.surname}
                  </div>
                {/if}
                {#if fam.family.marriageDate}
                  <div class="text-xs" style="color: var(--ink-muted);">{fam.family.marriageDate}</div>
                {/if}
                <div class="text-xs" style="color: var(--ink-faint);">{fam.children.length} {t('families.children').toLowerCase()}</div>
              </div>
            {/each}
          </div>
        {/if}
      </aside>
    </div>
  </div>

  <!-- ===== LIGHTBOX ===== -->
  {#if lightboxMedia}
    <div
      class="lightbox-overlay"
      onclick={() => lightboxMedia = null}
      onkeydown={handleLightboxKeydown}
      role="dialog"
      aria-labelledby="media-lightbox-title"
      tabindex="-1"
      use:focusTrap
    >
      <h2 id="media-lightbox-title" style="position:absolute;width:1px;height:1px;overflow:hidden;clip:rect(0,0,0,0);">{t('media.title')}</h2>
      <button class="lightbox-close" onclick={() => lightboxMedia = null} aria-label={t('common.close')}>&times;</button>
      <!-- svelte-ignore a11y_click_events_have_key_events -->
      <div class="lightbox-content" onclick={(e) => e.stopPropagation()} role="presentation">
        <img use:lazyImage={lightboxMedia.filePath} alt={lightboxMedia.title || 'Photo'} class="lightbox-img" />
        {#if lightboxMedia.title}
          <div class="lightbox-caption">{lightboxMedia.title}</div>
        {/if}
      </div>
    </div>
  {/if}
{/if}

<style>
  /* ===== PAGE LAYOUT ===== */
  .person-detail-page {
    max-width: 1200px;
    margin: 0 auto;
    padding: 1.5rem;
    min-height: calc(100vh - 52px);
    position: relative;
  }

  .swipe-back-indicator {
    position: fixed;
    left: 8px;
    top: 50%;
    transform: translateY(-50%);
    width: 28px;
    height: 28px;
    border-radius: 999px;
    display: flex;
    align-items: center;
    justify-content: center;
    background: color-mix(in srgb, var(--parchment) 70%, transparent);
    color: var(--ink);
    border: 1px solid var(--border-rule);
    pointer-events: none;
    transition: opacity 100ms linear;
    z-index: 40;
  }

  .detail-layout {
    display: grid;
    grid-template-columns: 1fr 320px;
    gap: 1.5rem;
    align-items: start;
  }

  .main-content {
    display: flex;
    flex-direction: column;
    gap: 1.5rem;
  }

  /* ===== BIO HEADER ===== */
  .bio-header {
    display: flex;
    gap: 1.5rem;
    padding: 1.5rem;
    background: color-mix(in srgb, var(--parchment) 40%, var(--vellum));
    border: 1px solid var(--border-rule);
    border-radius: 8px;
  }

  .header-photo {
    flex-shrink: 0;
  }

  .header-photo-img {
    width: 140px;
    height: 140px;
    border-radius: 8px;
    object-fit: cover;
    border: 2px solid var(--border-rule);
  }

  .header-initials {
    width: 140px;
    height: 140px;
    border-radius: 8px;
    display: flex;
    align-items: center;
    justify-content: center;
    font-size: 3rem;
    font-weight: 700;
    color: white;
    font-family: var(--font-heading);
  }

  .header-info {
    flex: 1;
    min-width: 0;
  }

  .header-name {
    font-size: 1.75rem;
    font-weight: 700;
    color: var(--ink);
    font-family: var(--font-heading);
    margin: 0;
    line-height: 1.2;
  }

  .gender-icon {
    font-size: 1.4rem;
    opacity: 0.6;
  }

  .header-dates {
    margin-top: 0.5rem;
    font-size: 0.95rem;
    color: var(--ink-light);
    line-height: 1.5;
  }

  .header-age {
    margin-top: 0.35rem;
    font-size: 0.85rem;
    color: var(--ink-muted);
    font-style: italic;
  }

  .header-actions {
    margin-top: 0.75rem;
  }

  /* ===== SECTIONS ===== */
  .detail-section {
    background: var(--vellum);
    border: 1px solid var(--border-rule);
    border-radius: 8px;
    padding: 1.25rem;
  }

  .section-heading {
    font-size: 1.1rem;
    font-weight: 700;
    color: var(--ink);
    font-family: var(--font-heading);
    margin: 0 0 1rem 0;
    padding-bottom: 0.5rem;
    border-bottom: 1px solid var(--border-rule);
  }

  .subsection-heading {
    font-size: 0.85rem;
    font-weight: 600;
    color: var(--ink-muted);
    text-transform: uppercase;
    letter-spacing: 0.05em;
    margin: 1rem 0 0.5rem 0;
  }

  .subsection-heading:first-of-type {
    margin-top: 0;
  }

  .empty-state {
    text-align: center;
    padding: 1.5rem;
    color: var(--ink-faint);
    font-size: 0.9rem;
    font-style: italic;
  }

  /* ===== TIMELINE ===== */
  .timeline {
    position: relative;
    padding-left: 0;
  }

  .timeline-item {
    display: flex;
    gap: 0.75rem;
    position: relative;
  }

  .timeline-line {
    display: flex;
    flex-direction: column;
    align-items: center;
    width: 32px;
    flex-shrink: 0;
  }

  .timeline-dot {
    width: 28px;
    height: 28px;
    border-radius: 50%;
    background: var(--parchment);
    border: 2px solid var(--ink-faint);
    display: flex;
    align-items: center;
    justify-content: center;
    font-size: 0.75rem;
    flex-shrink: 0;
    z-index: 1;
  }

  .timeline-dot-birth {
    border-color: #2D7D46;
    background: #E8F5E9;
  }

  .timeline-dot-death {
    border-color: #8B4513;
    background: #FBE9E7;
  }

  .timeline-dot-marriage {
    border-color: #C9880E;
    background: #FFF8E1;
  }

  .timeline-icon {
    font-size: 0.7rem;
    line-height: 1;
  }

  .timeline-connector {
    width: 2px;
    flex: 1;
    min-height: 12px;
    background: var(--ink-faint);
    opacity: 0.4;
  }

  .timeline-content {
    flex: 1;
    padding-bottom: 1rem;
    min-width: 0;
  }

  .timeline-event-header {
    display: flex;
    align-items: center;
    gap: 0.5rem;
    flex-wrap: wrap;
  }

  .event-badge {
    display: inline-block;
    padding: 0.15rem 0.5rem;
    border-radius: 4px;
    background: var(--accent-subtle);
    color: var(--accent);
    font-size: 0.75rem;
    font-weight: 600;
    text-transform: uppercase;
    letter-spacing: 0.03em;
  }

  .event-date {
    font-size: 0.85rem;
    color: var(--ink-light);
    font-weight: 500;
  }

  .event-place {
    font-size: 0.8rem;
    color: var(--ink-muted);
    margin-top: 0.2rem;
  }

  .event-desc {
    font-size: 0.8rem;
    color: var(--ink-light);
    margin-top: 0.15rem;
    font-style: italic;
  }

  /* ===== PERSON MINI CARDS ===== */
  .person-cards {
    display: flex;
    flex-wrap: wrap;
    gap: 0.5rem;
  }

  .mini-card {
    display: flex;
    align-items: center;
    gap: 0.5rem;
    padding: 0.5rem 0.75rem;
    border: 1px solid var(--border-rule);
    border-radius: 8px;
    background: color-mix(in srgb, var(--parchment) 30%, var(--vellum));
    cursor: pointer;
    transition: all 150ms ease;
    text-align: left;
    min-width: 180px;
  }

  .mini-card:hover {
    border-color: var(--accent);
    background: var(--accent-subtle);
  }

  .mini-card-photo {
    width: 36px;
    height: 36px;
    border-radius: 50%;
    object-fit: cover;
    flex-shrink: 0;
  }

  .mini-card-initials {
    width: 36px;
    height: 36px;
    border-radius: 50%;
    display: flex;
    align-items: center;
    justify-content: center;
    font-size: 0.75rem;
    font-weight: 700;
    color: white;
    flex-shrink: 0;
  }

  .mini-card-info {
    min-width: 0;
  }

  .mini-card-name {
    font-size: 0.85rem;
    font-weight: 600;
    color: var(--ink);
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }

  .mini-card-dates {
    font-size: 0.72rem;
    color: var(--ink-muted);
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }

  /* ===== MEDIA GALLERY ===== */
  .media-grid {
    display: grid;
    grid-template-columns: repeat(auto-fill, minmax(140px, 1fr));
    gap: 0.75rem;
  }

  .media-thumb {
    border-radius: 6px;
    overflow: hidden;
    border: 1px solid var(--border-rule);
    background: var(--parchment);
    cursor: pointer;
    transition: all 150ms ease;
  }

  .media-thumb:hover {
    border-color: var(--accent);
    box-shadow: 0 2px 8px rgba(0,0,0,0.08);
  }

  .media-thumb-img {
    width: 100%;
    aspect-ratio: 1;
    object-fit: cover;
    display: block;
  }

  .media-thumb-file {
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    aspect-ratio: 1;
    cursor: default;
  }

  /* ===== TABS ===== */
  .tab-bar {
    display: flex;
    gap: 0.5rem;
    padding: 0.5rem;
  }

  .tab-btn {
    flex: 1;
    padding: 0.5rem 0.75rem;
    font-size: 0.8rem;
    border-radius: 0.5rem;
    border: 1px solid var(--border-subtle);
    background: var(--vellum);
    color: var(--ink-muted);
    transition: background 150ms, color 150ms, border-color 150ms;
  }

  .tab-btn.active {
    background: var(--parchment);
    color: var(--ink);
    border-color: var(--accent);
  }

  .media-file-icon {
    font-size: 1.5rem;
    font-weight: 700;
    color: var(--ink-faint);
    font-family: var(--font-heading);
  }

  .media-thumb-caption {
    padding: 0.35rem 0.5rem;
    font-size: 0.7rem;
    color: var(--ink-muted);
    text-align: center;
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }

  /* ===== CITATIONS ===== */
  .citations-list {
    display: flex;
    flex-direction: column;
    gap: 0.5rem;
  }

  .citation-row {
    display: flex;
    justify-content: space-between;
    align-items: center;
    padding: 0.5rem 0.75rem;
    border-radius: 6px;
    background: color-mix(in srgb, var(--parchment) 30%, var(--vellum));
    border: 1px solid var(--border-rule);
    gap: 1rem;
  }

  .citation-title {
    font-size: 0.85rem;
    color: var(--ink);
    font-weight: 500;
    min-width: 0;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .citation-meta {
    display: flex;
    align-items: center;
    gap: 0.75rem;
    flex-shrink: 0;
  }

  .citation-page {
    font-size: 0.75rem;
    color: var(--ink-muted);
  }

  .citation-quality {
    font-size: 0.7rem;
    font-weight: 600;
    text-transform: uppercase;
    letter-spacing: 0.03em;
  }

  /* ===== NOTES ===== */
  .note-card {
    padding: 0.75rem;
    border-radius: 6px;
    background: color-mix(in srgb, var(--parchment) 30%, var(--vellum));
    border: 1px solid var(--border-rule);
    margin-bottom: 0.5rem;
  }

  .note-card:last-child {
    margin-bottom: 0;
  }

  .note-title {
    font-size: 0.85rem;
    font-weight: 600;
    color: var(--ink);
    margin-bottom: 0.35rem;
  }

  .note-content {
    font-size: 0.82rem;
    color: var(--ink-light);
    line-height: 1.5;
    white-space: pre-wrap;
  }

  .note-date {
    font-size: 0.7rem;
    color: var(--ink-faint);
    margin-top: 0.35rem;
  }

  /* ===== SIDEBAR ===== */
  .sidebar {
    display: flex;
    flex-direction: column;
    gap: 1rem;
    position: sticky;
    top: 60px;
  }

  .sidebar-card {
    background: var(--vellum);
    border: 1px solid var(--border-rule);
    border-radius: 8px;
    padding: 1rem;
  }

  .sidebar-heading {
    font-size: 0.85rem;
    font-weight: 700;
    color: var(--ink);
    font-family: var(--font-heading);
    margin: 0 0 0.75rem 0;
    padding-bottom: 0.4rem;
    border-bottom: 1px solid var(--border-rule);
  }

  .fact-list {
    display: flex;
    flex-direction: column;
    gap: 0.35rem;
    margin: 0;
    padding: 0;
  }

  .fact-row {
    display: flex;
    justify-content: space-between;
    align-items: baseline;
    gap: 0.5rem;
  }

  .fact-row dt {
    font-size: 0.75rem;
    color: var(--ink-muted);
    font-weight: 500;
    flex-shrink: 0;
  }

  .fact-row dd {
    font-size: 0.8rem;
    color: var(--ink);
    text-align: right;
    margin: 0;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .sidebar-family {
    padding: 0.5rem 0;
    border-bottom: 1px solid var(--border-rule);
  }

  .sidebar-family:last-child {
    border-bottom: none;
    padding-bottom: 0;
  }

  /* Proof status badges */
  .proof-badge {
    display: inline-block;
    padding: 0.1rem 0.4rem;
    border-radius: 3px;
    font-size: 0.65rem;
    font-weight: 700;
    text-transform: uppercase;
    letter-spacing: 0.05em;
  }

  .proof-proven { background: #E8F5E9; color: #2D7D46; }
  .proof-disproven { background: #FBE9E7; color: #C62828; }
  .proof-disputed { background: #FFF3E0; color: #E65100; }
  .proof-proposed { background: #E3F2FD; color: #1565C0; }
  .proof-unknown { background: var(--parchment); color: var(--ink-muted); }

  /* ===== LIGHTBOX ===== */
  .lightbox-overlay {
    position: fixed;
    inset: 0;
    background: rgba(0,0,0,0.85);
    z-index: 1000;
    display: flex;
    align-items: center;
    justify-content: center;
    cursor: pointer;
  }

  .lightbox-close {
    position: fixed;
    top: 1rem;
    right: 1.5rem;
    font-size: 2rem;
    color: white;
    background: none;
    border: none;
    cursor: pointer;
    z-index: 1001;
    opacity: 0.8;
    transition: opacity 150ms;
  }

  .lightbox-close:hover {
    opacity: 1;
  }

  .lightbox-content {
    cursor: default;
    text-align: center;
    max-width: 90vw;
    max-height: 90vh;
  }

  .lightbox-img {
    max-width: 90vw;
    max-height: 85vh;
    object-fit: contain;
    border-radius: 4px;
  }

  .lightbox-caption {
    color: rgba(255,255,255,0.8);
    font-size: 0.85rem;
    margin-top: 0.5rem;
  }

  /* ===== RESPONSIVE ===== */
  @media (max-width: 860px) {
    .detail-layout {
      grid-template-columns: 1fr;
    }

    .sidebar {
      position: static;
    }

    .bio-header {
      flex-direction: column;
      align-items: center;
      text-align: center;
    }

    .header-actions {
      justify-content: center;
      display: flex;
    }

    .person-cards {
      flex-direction: column;
    }

    .mini-card {
      min-width: unset;
    }
  }
</style>
