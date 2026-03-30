<script lang="ts">
  import { getAllMedia, getAllMediaWithPersons, getPersons, getPerson, getDb } from '$lib/db';
  import type { GedcomMedia, Person } from '$lib/types';
  import { isTauri } from '$lib/platform';

  async function tauriInvoke<T>(cmd: string, args: Record<string, unknown>): Promise<T> {
    if (!isTauri()) throw new Error('Desktop only');
    const { invoke } = await import('@tauri-apps/api/core');
    return invoke<T>(cmd, args);
  }

  type MediaMode = 'browse' | 'rename' | 'faces' | 'auto-crop' | 'dedup' | 'cleanup' | 'surnames' | 'ai-classify' | 'write-exif' | 'organize';

  let allMedia = $state.raw<(GedcomMedia & { person?: Person | null })[]>([]);
  let mode = $state<MediaMode>('browse');
  let search = $state('');
  let isRenaming = $state(false);
  let renameResult = $state<{ renamed: number; failed: number; output_dir: string } | null>(null);

  // Face pick state
  let pickMedia = $state<(GedcomMedia & { person?: Person | null }) | null>(null);
  let pickImgSrc = $state('');

  // --- Feature 1: Auto Crop + Approval ---
  interface CropCandidate {
    person: Person;
    media: GedcomMedia;
    originalSrc: string;
    thumbPath: string;
  }

  let cropCandidates = $state<CropCandidate[]>([]);
  let cropIndex = $state(0);
  let isAutoCropping = $state(false);
  let autoCropProgress = $state(0);
  let autoCropTotal = $state(0);
  let autoCropApproved = $state(0);
  let autoCropSkipped = $state(0);

  // --- Feature 2: Deduplication ---
  interface DupGroup {
    filePath: string;
    entries: (GedcomMedia & { person?: Person | null })[];
    keepIds: Set<number>;
  }
  let dupGroups = $state<DupGroup[]>([]);
  let isDedupScanning = $state(false);
  let dedupRemoved = $state(0);

  // --- Feature 3: Cleanup ---
  let missingFiles = $state<(GedcomMedia & { person?: Person | null })[]>([]);
  let orphanedEntries = $state<(GedcomMedia & { person?: Person | null })[]>([]);
  let isScanningCleanup = $state(false);
  let cleanupDone = $state(false);
  let cleanupStats = $state({ missing: 0, orphaned: 0, cleaned: 0 });

  // --- Feature 4: Lightbox ---
  let lightboxIndex = $state(-1);
  let lightboxSrc = $state('');
  let isLightboxLoading = $state(false);

  // --- Feature 5: Surname Groups ---
  interface SurnameGroup {
    surname: string;
    items: (GedcomMedia & { person?: Person | null })[];
    expanded: boolean;
  }
  let surnameGroups = $state<SurnameGroup[]>([]);

  // --- Feature 6: AI Vision Classification ---
  let isClassifying = $state(false);
  let classifyProgress = $state(0);
  let classifyTotal = $state(0);
  let classifyCurrentFile = $state('');
  let classifyResults = $state<{ classified: number; errors: number }>({ classified: 0, errors: 0 });
  let classifyDone = $state(false);

  // AI classification cache stored in DB settings
  type MediaClassification = { mediaId: number; category: string; personCount: number; confidence: number };
  let classifications = $state<Map<number, MediaClassification>>(new Map());

  // --- Feature 7: Write EXIF ---
  interface ExifWriteItem {
    file_path: string;
    person_name: string;
    original_filename: string;
    gedcom_xref: string;
    category: string;
    description: string;
  }
  let isWritingExif = $state(false);
  let exifResult = $state<{ written: number; failed: number; errors: string[] } | null>(null);

  // --- Feature 8: Organize Folders ---
  interface OrganizeItem {
    source_path: string;
    category: string;
    person_surname: string;
    person_given: string;
    media_title: string;
  }
  let isOrganizing = $state(false);
  let organizeResult = $state<{ organized: number; failed: number; output_dir: string } | null>(null);

  // ============================================================
  // Category definitions with display names
  // ============================================================

  const categoryDefs = [
    // Photos
    { id: 'single', label: 'Headshots', group: 'PHOTOS' },
    { id: 'group', label: 'Group Photos', group: 'PHOTOS', match: ['married', 'family-group'] },
    { id: 'castles', label: 'Homes & Castles', group: 'PHOTOS' },
    { id: 'graves', label: 'Graves', group: 'PHOTOS' },
    { id: 'crests', label: 'Crests & Flags', group: 'PHOTOS' },
    // Documents
    { id: 'ship-manifests', label: 'Ship Manifests', group: 'DOCUMENTS' },
    { id: 'census', label: 'Census Records', group: 'DOCUMENTS' },
    { id: 'marriage-docs', label: 'Marriage Records', group: 'DOCUMENTS' },
    { id: 'wills', label: 'Wills & Probate', group: 'DOCUMENTS' },
    { id: 'histories', label: 'Histories', group: 'DOCUMENTS' },
    { id: 'other-docs', label: 'Other Documents', group: 'DOCUMENTS' },
    // Other
    { id: 'other', label: 'Other', group: 'OTHER' },
  ];

  let activeCategory = $state<string>('single');

  // Tool modes that replace the content area
  const toolModes: MediaMode[] = ['auto-crop', 'dedup', 'cleanup', 'rename', 'ai-classify', 'write-exif', 'organize'];
  let isToolActive = $derived(toolModes.includes(mode));

  // ============================================================
  // Classification loading
  // ============================================================

  async function loadClassifications() {
    const db = await getDb();
    try {
      const rows = await db.select<{ key: string; value: string }[]>(
        `SELECT key, value FROM settings WHERE key LIKE 'media_class_%'`
      );
      const map = new Map<number, MediaClassification>();
      for (const row of rows) {
        const mediaId = parseInt(row.key.replace('media_class_', ''));
        try { map.set(mediaId, JSON.parse(row.value)); } catch {}
      }
      classifications = map;
    } catch {}
  }

  async function classifyWithAI() {
    isClassifying = true;
    classifyDone = false;
    classifyResults = { classified: 0, errors: 0 };

    const db = await getDb();

    let apiKey = '';
    let providerToUse = 'groq';
    let modelToUse = 'llama-3.2-11b-vision-preview';
    let endpoint = 'https://api.groq.com/openai/v1/chat/completions';

    const groqKey = await db.select<{value:string}[]>(`SELECT value FROM settings WHERE key = 'ai_key_groq'`);
    if (groqKey[0]?.value) {
      apiKey = groqKey[0].value;
    } else {
      const openaiKey = await db.select<{value:string}[]>(`SELECT value FROM settings WHERE key = 'ai_key_openai'`);
      if (openaiKey[0]?.value) {
        apiKey = openaiKey[0].value;
        providerToUse = 'openai';
        modelToUse = 'gpt-4o-mini';
        endpoint = 'https://api.openai.com/v1/chat/completions';
      } else {
        const orKey = await db.select<{value:string}[]>(`SELECT value FROM settings WHERE key = 'ai_key_openrouter'`);
        if (orKey[0]?.value) {
          apiKey = orKey[0].value;
          providerToUse = 'openrouter';
          modelToUse = 'meta-llama/llama-3.2-11b-vision-instruct:free';
          endpoint = 'https://openrouter.ai/api/v1/chat/completions';
        }
      }
    }

    if (!apiKey) {
      classifyResults = { classified: 0, errors: 1 };
      classifyCurrentFile = 'No API key found. Add a Groq, OpenAI, or OpenRouter key in AI Settings.';
      isClassifying = false;
      return;
    }

    const images = allMedia.filter(m => /\.(jpe?g|png|gif|webp|bmp)$/i.test(m.filePath) && !classifications.has(m.id));
    classifyTotal = images.length;
    classifyProgress = 0;

    for (const m of images) {
      classifyCurrentFile = fileName(m.filePath);
      try {
        const base64 = await tauriInvoke<string>('read_image_base64', { path: m.filePath });
        if (!base64) { classifyResults.errors++; classifyProgress++; continue; }

        const ext = m.filePath.split('.').pop()?.toLowerCase() || 'jpg';
        const mime = ext === 'png' ? 'image/png' : ext === 'gif' ? 'image/gif' : ext === 'webp' ? 'image/webp' : 'image/jpeg';

        const resp = await fetch(endpoint, {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json',
            'Authorization': `Bearer ${apiKey}`,
          },
          body: JSON.stringify({
            model: modelToUse,
            messages: [{
              role: 'user',
              content: [
                {
                  type: 'text',
                  text: `Classify this image. Respond with ONLY a JSON object, no other text:
{"category":"single"|"married"|"family-group"|"graves"|"castles"|"crests"|"document"|"other","person_count":N,"confidence":0.0-1.0}

Rules:
- "single": exactly 1 person visible (portrait, headshot)
- "married": exactly 2 people who appear to be a couple
- "family-group": 3+ people, OR 2 people of same sex, OR a group
- "graves": headstone, cemetery, burial marker
- "castles": castle, manor, estate, palace, building
- "crests": coat of arms, heraldry, emblem, crest
- "document": scanned document, certificate, census record, newspaper, handwritten text, form
- "other": landscape, map, object, or unclassifiable
- person_count: number of human faces/people visible (0 if none)`
                },
                {
                  type: 'image_url',
                  image_url: { url: base64 }
                }
              ]
            }],
            max_tokens: 100,
            temperature: 0.1,
          }),
        });

        if (resp.ok) {
          const data = await resp.json();
          const text = data.choices?.[0]?.message?.content || '';
          const jsonMatch = text.match(/\{[^}]+\}/);
          if (jsonMatch) {
            const parsed = JSON.parse(jsonMatch[0]);
            const classification: MediaClassification = {
              mediaId: m.id,
              category: parsed.category || 'other',
              personCount: parsed.person_count ?? 0,
              confidence: parsed.confidence ?? 0.5,
            };
            classifications.set(m.id, classification);
            await db.execute(
              `INSERT OR REPLACE INTO settings (key, value) VALUES ($1, $2)`,
              [`media_class_${m.id}`, JSON.stringify(classification)]
            );
            classifyResults.classified++;
          } else {
            classifyResults.errors++;
          }
        } else {
          classifyResults.errors++;
          if (resp.status === 429) {
            await new Promise(r => setTimeout(r, 2000));
          }
        }
      } catch {
        classifyResults.errors++;
      }
      classifyProgress++;
      await new Promise(r => setTimeout(r, 200));
    }

    isClassifying = false;
    classifyDone = true;
    await loadClassifications();
  }

  // Enhanced categorize that uses AI classification when available
  function categorizeWithAI(m: GedcomMedia & { person?: Person | null }): string {
    const aiClass = classifications.get(m.id);
    if (aiClass) return aiClass.category;
    return categorize(m);
  }

  async function load() {
    categoryCache.clear();
    const rows = await getAllMediaWithPersons();
    const enriched = rows.map(r => ({
      ...r,
      person: r.personGivenName || r.personSurname ? {
        xref: r.ownerXref,
        givenName: r.personGivenName,
        surname: r.personSurname,
        sex: r.personSex,
        birthDate: r.personBirthDate,
        deathDate: r.personDeathDate,
      } as Person : null,
    }));
    allMedia = enriched;
  }

  let filtered = $derived(
    search.trim()
      ? allMedia.filter(m => {
          const t = search.toLowerCase();
          return (m.person?.givenName?.toLowerCase().includes(t) ?? false)
            || (m.person?.surname?.toLowerCase().includes(t) ?? false)
            || (m.title?.toLowerCase().includes(t) ?? false);
        })
      : allMedia
  );

  let photos = $derived(filtered.filter(m => /\.(jpe?g|png|gif|webp|bmp)$/i.test(m.filePath)));

  // Category counts for sidebar badges
  let categoryCounts = $derived(() => {
    const counts: Record<string, number> = {};
    for (const m of filtered) {
      const cat = categorizeWithAI(m);
      // Map married and family-group to 'group' for display
      const displayCat = (cat === 'married' || cat === 'family-group') ? 'group' : cat;
      counts[displayCat] = (counts[displayCat] || 0) + 1;
    }
    return counts;
  });

  const categoryCache = new Map<number, string>();

  function categorize(m: GedcomMedia & { person?: Person | null }): string {
    if (categoryCache.has(m.id)) return categoryCache.get(m.id)!;
    const result = categorizeUncached(m);
    categoryCache.set(m.id, result);
    return result;
  }

  function categorizeUncached(m: GedcomMedia & { person?: Person | null }): string {
    const fp = m.filePath.toLowerCase();
    const fname = fp.split('/').pop() || '';
    const t = (m.title || '').toLowerCase();
    const combined = t + ' ' + fname;

    // 1. Documents -- check FIRST. Many scanned documents are .jpg files.
    if (/^\d{4}\s/.test(t) && !/photo|portrait|picture/i.test(combined)) return detectDocType(combined);
    if (/manifest|passenger|ship.list|vessel|embark|disembark/i.test(combined)) return 'ship-manifests';
    if (/census|enumerat/i.test(combined)) return 'census';
    if (/marriage[._\s-]?(cert|doc|record|licen|register|entry)/i.test(combined)) return 'marriage-docs';
    if (/\bwill\b|testament|probate|estate[._\s]of|bequest|will.abstract/i.test(combined)) return 'wills';
    if (/\bdeed\b|deed.abstract|indenture|conveyance/i.test(combined)) return 'other-docs';
    if (/\bobit\b|obituar/i.test(combined)) return 'other-docs';
    if (/\bdeath\b|death.cert|death.record|death.notice|death.register/i.test(combined)) return 'other-docs';
    if (/\bbirth\b.*\b(cert|record|register|entry)/i.test(combined)) return 'other-docs';
    if (/\bburial\b|burial.record|burial.register/i.test(combined)) return 'other-docs';
    if (/\bbaptis/i.test(combined)) return 'other-docs';
    if (/immigration|naturali[sz]ation|passport|border.cross/i.test(combined)) return 'other-docs';
    if (/news.?paper|clipping|article|\bsun\b|\btimes\b|\bherald\b|\bgazette\b|\btribune\b/i.test(combined)) return 'other-docs';
    if (/inscription|memorial|plaque|monument/i.test(combined)) return 'other-docs';
    if (/\brecord\b|\bregister\b|\bindex\b|\babstract\b|\btranscript/i.test(combined)) return 'other-docs';
    if (/\bbattalion\b|\bregiment\b|\bmilitary\b|\bservice\b.*\brecord\b|\benlist/i.test(combined)) return 'other-docs';
    if (/histor|chronicle|narrative|memoir|biography|personal.memoirs/i.test(combined)) return 'histories';
    if (/\.(pdf|doc|docx|txt|html?)$/i.test(fp)) return 'other-docs';
    if (/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\./i.test(fname)) return 'other-docs';

    // 2. Specific photo types
    if (/grave|cemetery|headstone|tombstone|gravesite/i.test(combined)) return 'graves';
    if (/castle|manor|palace|chateau|fortress/i.test(combined)) return 'castles';
    if (/crest|coat.of.arms|heraldry|emblem|blazon/i.test(combined)) return 'crests';

    // 3. People photos
    if (/\bwedding\b|\bcouple\b|\bbride\b|\bgroom\b|\bmarried\b/i.test(combined)) return 'married';
    if (/\breunion\b|\bgathering\b|\bgroup\b.*\bphoto\b|\bfamily\b.*\bportrait\b/i.test(combined)) return 'family-group';
    if (/\bgenerations?\b|\bbrothers\b|\bsisters\b|\bsiblings\b/i.test(combined)) return 'family-group';

    // 4. Default: if it's an image file, it's a single person photo
    if (/\.(jpe?g|png|gif|webp|bmp|tiff?)$/i.test(fp)) return 'single';
    return 'other';
  }

  function detectDocType(combined: string): string {
    if (/census/i.test(combined)) return 'census';
    if (/will|probate|testament/i.test(combined)) return 'wills';
    if (/marriage/i.test(combined)) return 'marriage-docs';
    if (/manifest|passenger/i.test(combined)) return 'ship-manifests';
    return 'other-docs';
  }

  // Filter by active category -- 'group' matches both married and family-group
  let categoryFiltered = $derived(
    (() => {
      if (isToolActive) return filtered;
      const cat = activeCategory;
      if (cat === 'group') {
        return filtered.filter(m => {
          const c = categorizeWithAI(m);
          return c === 'married' || c === 'family-group';
        });
      }
      return filtered.filter(m => categorizeWithAI(m) === cat);
    })()
  );

  // ============================================================
  // Write EXIF Metadata
  // ============================================================

  async function writeExifMetadata() {
    isWritingExif = true;
    exifResult = null;

    const items: ExifWriteItem[] = categoryFiltered
      .filter(m => m.filePath && /\.(jpe?g|png|gif|webp|bmp|tiff?)$/i.test(m.filePath))
      .map(m => ({
        file_path: m.filePath,
        person_name: [m.person?.givenName, m.person?.surname].filter(Boolean).join(' '),
        original_filename: fileName(m.filePath),
        gedcom_xref: m.ownerXref || m.xref || '',
        category: categorizeWithAI(m),
        description: m.title || '',
      }));

    try {
      const result = await tauriInvoke<{ written: number; failed: number; errors: string[] }>('write_exif_metadata', { items });
      exifResult = result;
    } catch (e: any) {
      exifResult = { written: 0, failed: items.length, errors: [String(e)] };
    }
    isWritingExif = false;
  }

  // ============================================================
  // Organize Media Folders
  // ============================================================

  async function organizeMediaFolders() {
    isOrganizing = true;
    organizeResult = null;

    const items: OrganizeItem[] = allMedia
      .filter(m => m.filePath)
      .map(m => ({
        source_path: m.filePath,
        category: categorizeWithAI(m),
        person_surname: m.person?.surname || 'Unknown',
        person_given: m.person?.givenName || '',
        media_title: m.title || '',
      }));

    try {
      const result = await tauriInvoke<{ organized: number; failed: number; output_dir: string }>('organize_media_folders', { items });
      organizeResult = result;
    } catch (e: any) {
      organizeResult = { organized: 0, failed: items.length, output_dir: String(e) };
    }
    isOrganizing = false;
  }

  // ============================================================
  // Rename All
  // ============================================================

  async function renameAll() {
    isRenaming = true;
    const items = allMedia.filter(m => m.filePath && m.person).map(m => ({
      original_path: m.filePath,
      person_given: m.person!.givenName,
      person_surname: m.person!.surname,
      media_title: m.title || '',
      media_xref: m.xref || `media_${m.id}`,
    }));
    try {
      const r = await tauriInvoke<{ renamed: number; failed: number; output_dir: string }>('batch_rename_media', { items });
      renameResult = r;
    } catch (e: any) {
      renameResult = { renamed: 0, failed: items.length, output_dir: String(e) };
    }
    isRenaming = false;
  }

  // ============================================================
  // Face Pick
  // ============================================================

  async function startPick(m: GedcomMedia & { person?: Person | null }) {
    pickMedia = m;
    try {
      pickImgSrc = await tauriInvoke<string>('read_image_base64', { path: m.filePath });
    } catch { pickImgSrc = ''; }
  }

  async function setPrimary() {
    if (!pickMedia?.person) return;
    const db = await getDb();
    await db.execute(`UPDATE media SET format = '' WHERE ownerXref = $1 AND format = 'PRIMARY'`, [pickMedia.person.xref]);
    await db.execute(`UPDATE media SET format = 'PRIMARY' WHERE id = $1`, [pickMedia.id]);
    try {
      await tauriInvoke('generate_thumbnail', { path: pickMedia.filePath, size: 200 });
    } catch {}
    pickMedia = null;
    await load();
  }

  // ============================================================
  // Feature 1: Auto Crop + Approval
  // ============================================================

  async function startAutoCrop() {
    isAutoCropping = true;
    autoCropProgress = 0;
    cropIndex = 0;
    autoCropApproved = 0;
    autoCropSkipped = 0;
    cropCandidates = [];

    const db = await getDb();
    const peopleWithMedia = await db.select<{ ownerXref: string }[]>(
      `SELECT DISTINCT m.ownerXref FROM media m
       WHERE m.ownerXref != '' AND m.filePath != ''
       AND (m.filePath LIKE '%.jpg' OR m.filePath LIKE '%.jpeg' OR m.filePath LIKE '%.png' OR m.filePath LIKE '%.gif' OR m.filePath LIKE '%.JPG')
       AND m.ownerXref NOT IN (SELECT ownerXref FROM media WHERE format = 'PRIMARY')
       ORDER BY m.ownerXref`
    );

    autoCropTotal = peopleWithMedia.length;
    const candidates: CropCandidate[] = [];

    for (let i = 0; i < peopleWithMedia.length; i++) {
      autoCropProgress = i + 1;
      const xref = peopleWithMedia[i].ownerXref;
      const person = await getPerson(xref);
      if (!person) continue;

      const media = await db.select<GedcomMedia[]>(
        `SELECT * FROM media WHERE ownerXref = $1 AND filePath != ''
         AND (filePath LIKE '%.jpg' OR filePath LIKE '%.jpeg' OR filePath LIKE '%.png' OR filePath LIKE '%.gif' OR filePath LIKE '%.JPG')
         LIMIT 1`,
        [xref]
      );
      if (media.length === 0) continue;

      try {
        const thumbPath = await tauriInvoke<string>('generate_thumbnail', { path: media[0].filePath, size: 200 });
        const originalSrc = await tauriInvoke<string>('read_image_base64', { path: media[0].filePath });
        candidates.push({ person, media: media[0], originalSrc, thumbPath });
      } catch {}
    }

    cropCandidates = candidates;
    autoCropTotal = candidates.length;
    isAutoCropping = false;
  }

  async function approveCrop() {
    if (cropIndex >= cropCandidates.length) return;
    const candidate = cropCandidates[cropIndex];
    const db = await getDb();
    await db.execute(`UPDATE media SET format = '' WHERE ownerXref = $1 AND format = 'PRIMARY'`, [candidate.person.xref]);
    await db.execute(`UPDATE media SET format = 'PRIMARY' WHERE id = $1`, [candidate.media.id]);
    autoCropApproved++;
    cropIndex++;
    if (cropIndex >= cropCandidates.length) {
      await load();
    }
  }

  function skipCrop() {
    autoCropSkipped++;
    cropIndex++;
    if (cropIndex >= cropCandidates.length) {
      load();
    }
  }

  function rejectCrop() {
    autoCropSkipped++;
    cropIndex++;
    if (cropIndex >= cropCandidates.length) {
      load();
    }
  }

  let currentCropThumbSrc = $state('');

  async function loadCropThumb(path: string) {
    try {
      currentCropThumbSrc = await tauriInvoke<string>('read_image_base64', { path });
    } catch {
      currentCropThumbSrc = '';
    }
  }

  $effect(() => {
    if (cropCandidates.length > 0 && cropIndex < cropCandidates.length) {
      loadCropThumb(cropCandidates[cropIndex].thumbPath);
    }
  });

  // ============================================================
  // Feature 2: Deduplication
  // ============================================================

  async function scanDuplicates() {
    isDedupScanning = true;
    dedupRemoved = 0;
    const groups = new Map<string, (GedcomMedia & { person?: Person | null })[]>();

    for (const m of allMedia) {
      if (!m.filePath) continue;
      const key = m.filePath;
      if (!groups.has(key)) groups.set(key, []);
      groups.get(key)!.push(m);
    }

    dupGroups = Array.from(groups.entries())
      .filter(([_, entries]) => entries.length > 1)
      .map(([filePath, entries]) => ({
        filePath,
        entries,
        keepIds: new Set([entries[0].id]),
      }));

    isDedupScanning = false;
  }

  function toggleKeep(group: DupGroup, id: number) {
    if (group.keepIds.has(id)) {
      if (group.keepIds.size > 1) group.keepIds.delete(id);
    } else {
      group.keepIds.add(id);
    }
    dupGroups = [...dupGroups];
  }

  async function removeDuplicateLinks() {
    const db = await getDb();
    let removed = 0;
    for (const group of dupGroups) {
      for (const entry of group.entries) {
        if (!group.keepIds.has(entry.id)) {
          await db.execute(`DELETE FROM media WHERE id = $1`, [entry.id]);
          removed++;
        }
      }
    }
    dedupRemoved = removed;
    dupGroups = [];
    await load();
  }

  // ============================================================
  // Feature 3: Cleanup
  // ============================================================

  async function scanCleanup() {
    isScanningCleanup = true;
    cleanupDone = false;
    cleanupStats = { missing: 0, orphaned: 0, cleaned: 0 };
    const missing: (GedcomMedia & { person?: Person | null })[] = [];
    const orphaned: (GedcomMedia & { person?: Person | null })[] = [];

    for (const m of allMedia) {
      if (!m.ownerXref) {
        orphaned.push(m);
        continue;
      }
      if (m.filePath) {
        try {
          const exists = await tauriInvoke<boolean>('check_file_exists', { path: m.filePath });
          if (!exists) missing.push(m);
        } catch {
          missing.push(m);
        }
      }
    }

    missingFiles = missing;
    orphanedEntries = orphaned;
    cleanupStats = { missing: missing.length, orphaned: orphaned.length, cleaned: 0 };
    isScanningCleanup = false;
  }

  async function cleanupMissing() {
    const db = await getDb();
    let cleaned = 0;
    for (const m of missingFiles) {
      await db.execute(`DELETE FROM media WHERE id = $1`, [m.id]);
      cleaned++;
    }
    cleanupStats = { ...cleanupStats, cleaned: cleanupStats.cleaned + cleaned };
    missingFiles = [];
    cleanupDone = true;
    await load();
  }

  async function cleanupOrphaned() {
    const db = await getDb();
    let cleaned = 0;
    for (const m of orphanedEntries) {
      await db.execute(`DELETE FROM media WHERE id = $1`, [m.id]);
      cleaned++;
    }
    cleanupStats = { ...cleanupStats, cleaned: cleanupStats.cleaned + cleaned };
    orphanedEntries = [];
    cleanupDone = true;
    await load();
  }

  // ============================================================
  // Feature 4: Lightbox
  // ============================================================

  async function openLightbox(index: number) {
    const items = categoryFiltered;
    if (index < 0 || index >= items.length) return;
    lightboxIndex = index;
    isLightboxLoading = true;
    try {
      lightboxSrc = await tauriInvoke<string>('read_image_base64', { path: items[index].filePath });
    } catch {
      lightboxSrc = '';
    }
    isLightboxLoading = false;
  }

  async function lightboxNav(delta: number) {
    const newIdx = lightboxIndex + delta;
    if (newIdx >= 0 && newIdx < categoryFiltered.length) {
      await openLightbox(newIdx);
    }
  }

  function closeLightbox() {
    lightboxIndex = -1;
    lightboxSrc = '';
  }

  function handleKeydown(e: KeyboardEvent) {
    if (lightboxIndex < 0) return;
    if (e.key === 'Escape') closeLightbox();
    else if (e.key === 'ArrowLeft') lightboxNav(-1);
    else if (e.key === 'ArrowRight') lightboxNav(1);
  }

  // ============================================================
  // Feature 5: Surname Groups
  // ============================================================

  function buildSurnameGroups() {
    const map = new Map<string, (GedcomMedia & { person?: Person | null })[]>();
    for (const m of allMedia) {
      const surname = m.person?.surname || '(Unknown)';
      if (!map.has(surname)) map.set(surname, []);
      map.get(surname)!.push(m);
    }
    surnameGroups = Array.from(map.entries())
      .sort((a, b) => b[1].length - a[1].length)
      .map(([surname, items]) => ({ surname, items, expanded: false }));
  }

  function toggleSurnameGroup(idx: number) {
    surnameGroups[idx].expanded = !surnameGroups[idx].expanded;
    surnameGroups = [...surnameGroups];
  }

  // ============================================================
  // Helpers
  // ============================================================

  function fileName(p: string): string { return p.split('/').pop() ?? p; }

  // Sidebar tool items
  const sidebarTools: { id: MediaMode; label: string }[] = [
    { id: 'write-exif', label: 'Write EXIF' },
    { id: 'organize', label: 'Organize' },
    { id: 'ai-classify', label: 'AI Classify' },
    { id: 'auto-crop', label: 'Auto Crop' },
    { id: 'dedup', label: 'Dedup' },
    { id: 'cleanup', label: 'Cleanup' },
    { id: 'rename', label: 'Rename' },
  ];

  // Dropdown state for mobile tools
  let showToolsDropdown = $state(false);

  $effect(() => { load(); loadClassifications(); });
  $effect(() => { if (mode === 'surnames' && allMedia.length > 0) buildSurnameGroups(); });
</script>

<svelte:window onkeydown={handleKeydown} />

<div class="flex flex-col h-full">
  <!-- ===== TOP BAR ===== -->
  <div class="flex items-center gap-3 px-5 py-3 shrink-0" style="background: var(--sidebar-bg, var(--parchment)); border-bottom: 1px solid color-mix(in srgb, var(--ink) 10%, transparent);">
    <h1 class="text-lg font-bold" style="font-family: var(--font-serif); color: var(--ink);">Media</h1>
    <span class="text-xs px-2 py-0.5 rounded-full" style="background: color-mix(in srgb, var(--ink) 8%, transparent); color: var(--ink-muted, var(--ink)); font-family: var(--font-mono);">{allMedia.length}</span>

    <input
      bind:value={search}
      placeholder="Search..."
      class="ml-auto w-56 px-3 py-1.5 text-sm arch-input"
    />

    <!-- Tools dropdown for toolbar -->
    <div class="relative">
      <button
        onclick={() => showToolsDropdown = !showToolsDropdown}
        class="px-3 py-1.5 text-xs rounded-lg transition-colors"
        style="background: {isToolActive ? 'var(--accent, #6b5b3e)' : 'color-mix(in srgb, var(--ink) 6%, transparent)'}; color: {isToolActive ? 'white' : 'var(--ink)'};"
      >
        {isToolActive ? sidebarTools.find(t => t.id === mode)?.label ?? 'Tools' : 'Tools'}
        <span class="ml-1 text-[9px]">&#9662;</span>
      </button>
      {#if showToolsDropdown}
        <div
          class="absolute right-0 top-full mt-1 w-40 rounded-lg shadow-xl z-30 py-1"
          style="background: var(--parchment, white); border: 1px solid color-mix(in srgb, var(--ink) 12%, transparent);"
        >
          {#each sidebarTools as tool}
            <button
              onclick={() => { mode = tool.id; showToolsDropdown = false; }}
              class="w-full text-left px-3 py-2 text-xs transition-colors"
              style="color: var(--ink); {mode === tool.id ? 'background: color-mix(in srgb, var(--accent, #6b5b3e) 12%, transparent);' : ''}"
              onmouseenter={(e) => { (e.currentTarget as HTMLElement).style.background = 'color-mix(in srgb, var(--ink) 5%, transparent)'; }}
              onmouseleave={(e) => { (e.currentTarget as HTMLElement).style.background = mode === tool.id ? 'color-mix(in srgb, var(--accent, #6b5b3e) 12%, transparent)' : ''; }}
            >{tool.label}</button>
          {/each}
          {#if isToolActive}
            <div style="border-top: 1px solid color-mix(in srgb, var(--ink) 8%, transparent); margin: 2px 0;"></div>
            <button
              onclick={() => { mode = 'browse'; showToolsDropdown = false; }}
              class="w-full text-left px-3 py-2 text-xs transition-colors"
              style="color: var(--ink-muted, var(--ink));"
            >Back to Browse</button>
          {/if}
        </div>
      {/if}
    </div>
  </div>

  <!-- ===== TWO-PANEL LAYOUT ===== -->
  <div class="flex flex-1 overflow-hidden">

    <!-- ===== LEFT SIDEBAR ===== -->
    <div class="w-[200px] shrink-0 overflow-y-auto py-3" style="background: var(--sidebar-bg, color-mix(in srgb, var(--parchment, #f5f0e8) 100%, transparent)); border-right: 1px solid color-mix(in srgb, var(--ink) 8%, transparent);">

      {#each ['PHOTOS', 'DOCUMENTS', 'OTHER'] as groupName}
        <div class="px-3 pt-3 pb-1">
          <span class="text-[9px] font-bold uppercase tracking-widest" style="color: var(--ink-faint, #999); font-family: var(--font-mono);">{groupName}</span>
        </div>
        {#each categoryDefs.filter(c => c.group === groupName) as cat}
          {@const count = categoryCounts()[cat.id] || 0}
          <button
            onclick={() => { activeCategory = cat.id; if (isToolActive) mode = 'browse'; }}
            class="w-full flex items-center gap-2 px-3 py-1.5 text-left text-xs transition-all"
            style="color: {isToolActive ? 'var(--ink-faint, #aaa)' : activeCategory === cat.id ? 'var(--ink)' : 'var(--ink-muted, #666)'}; background: {activeCategory === cat.id && !isToolActive ? 'color-mix(in srgb, var(--accent, #6b5b3e) 10%, transparent)' : 'transparent'}; border-left: 3px solid {activeCategory === cat.id && !isToolActive ? 'var(--accent, #6b5b3e)' : 'transparent'}; opacity: {isToolActive ? '0.5' : '1'};"
          >
            <span class="w-1.5 h-1.5 rounded-full shrink-0" style="background: {count > 0 ? 'var(--accent, #6b5b3e)' : 'color-mix(in srgb, var(--ink) 15%, transparent)'};"></span>
            <span class="truncate flex-1" style="font-family: var(--font-sans);">{cat.label}</span>
            {#if count > 0}
              <span class="text-[9px] tabular-nums" style="color: var(--ink-faint, #999); font-family: var(--font-mono);">{count}</span>
            {/if}
          </button>
        {/each}
      {/each}

      <!-- Sidebar Tools Section -->
      <div class="mt-4 mx-3" style="border-top: 1px solid color-mix(in srgb, var(--ink) 10%, transparent);"></div>
      <div class="px-3 pt-3 pb-1">
        <span class="text-[9px] font-bold uppercase tracking-widest" style="color: var(--ink-faint, #999); font-family: var(--font-mono);">TOOLS</span>
      </div>
      {#each sidebarTools as tool}
        <button
          onclick={() => { mode = tool.id; }}
          class="w-full flex items-center gap-2 px-3 py-1.5 text-left text-xs transition-all"
          style="color: {mode === tool.id ? 'var(--ink)' : 'var(--ink-muted, #666)'}; background: {mode === tool.id ? 'color-mix(in srgb, var(--accent, #6b5b3e) 10%, transparent)' : 'transparent'}; border-left: 3px solid {mode === tool.id ? 'var(--accent, #6b5b3e)' : 'transparent'};"
        >
          <span class="truncate" style="font-family: var(--font-sans);">{tool.label}</span>
        </button>
      {/each}
    </div>

    <!-- ===== CONTENT AREA ===== -->
    <div class="flex-1 overflow-auto p-5">

      <!-- ========== WRITE EXIF MODE ========== -->
      {#if mode === 'write-exif'}
        <div class="max-w-2xl mx-auto animate-fade-in">
          <div class="arch-card p-6">
            <h2 class="text-sm font-semibold mb-2" style="font-family: var(--font-serif);">Write EXIF Metadata</h2>
            <p class="text-xs mb-3" style="color: var(--ink-muted, #666);">
              Embeds person name, GEDCOM reference, category, and description into the EXIF metadata of each image file.
              This writes to {categoryFiltered.filter(m => /\.(jpe?g|png|gif|webp|bmp|tiff?)$/i.test(m.filePath)).length} images
              in the current category ({categoryDefs.find(c => c.id === activeCategory)?.label || activeCategory}).
            </p>
            <p class="text-[10px] mb-4" style="color: var(--accent, #6b5b3e);">Original files are modified in-place. Back up before running.</p>

            {#if exifResult}
              <div class="rounded-lg p-3 mb-4 text-xs" style="background: color-mix(in srgb, var(--color-validated, green) 10%, transparent); color: var(--color-validated, green);">
                Done! {exifResult.written} written, {exifResult.failed} failed.
                {#if exifResult.errors.length > 0}
                  <div class="mt-1 text-[10px]" style="color: var(--color-error, red);">{exifResult.errors.slice(0, 5).join(', ')}</div>
                {/if}
              </div>
            {/if}

            <button onclick={writeExifMetadata} disabled={isWritingExif} class="px-4 py-2 text-sm btn-accent disabled:opacity-50">
              {isWritingExif ? 'Writing EXIF...' : `Write EXIF to ${categoryFiltered.filter(m => /\.(jpe?g|png|gif|webp|bmp|tiff?)$/i.test(m.filePath)).length} Files`}
            </button>
          </div>
        </div>

      <!-- ========== ORGANIZE MODE ========== -->
      {:else if mode === 'organize'}
        <div class="max-w-2xl mx-auto animate-fade-in">
          <div class="arch-card p-6">
            <h2 class="text-sm font-semibold mb-2" style="font-family: var(--font-serif);">Organize Media Folders</h2>
            <p class="text-xs mb-3" style="color: var(--ink-muted, #666);">
              Copies all {allMedia.filter(m => m.filePath).length} media files into organized folders by category and surname.
              Structure: <code class="px-1 rounded" style="background: var(--parchment);">Category/Surname/file.ext</code>
            </p>
            <p class="text-[10px] mb-4" style="color: var(--accent, #6b5b3e);">Originals are preserved -- this creates copies.</p>

            {#if organizeResult}
              <div class="rounded-lg p-3 mb-4 text-xs" style="background: color-mix(in srgb, var(--color-validated, green) 10%, transparent); color: var(--color-validated, green);">
                Done! {organizeResult.organized} organized, {organizeResult.failed} failed.
                {#if organizeResult.output_dir}
                  <div class="mt-1 text-[10px]" style="color: var(--ink-muted, #666);">Output: {organizeResult.output_dir}</div>
                {/if}
              </div>
            {/if}

            <button onclick={organizeMediaFolders} disabled={isOrganizing} class="px-4 py-2 text-sm btn-accent disabled:opacity-50">
              {isOrganizing ? 'Organizing...' : `Organize ${allMedia.filter(m => m.filePath).length} Files`}
            </button>
          </div>
        </div>

      <!-- ========== RENAME MODE ========== -->
      {:else if mode === 'rename'}
        <div class="max-w-2xl mx-auto animate-fade-in">
          <div class="arch-card p-6">
            <h2 class="text-sm font-semibold mb-2" style="font-family: var(--font-serif);">Rename & Organize for MyHeritage / Ancestry</h2>
            <p class="text-xs mb-3" style="color: var(--ink-muted, #666);">
              Copies {allMedia.filter(m => m.person).length} files to <code class="px-1 rounded" style="background: var(--parchment);">~/Documents/GedFix/renamed_media/</code> organized by surname:
              <code class="px-1 rounded text-[10px]" style="background: var(--parchment);">Surname/Firstname_Surname_Title.ext</code>
            </p>
            <p class="text-[10px] mb-4" style="color: var(--accent, #6b5b3e);">Originals are preserved -- this creates copies.</p>

            {#if renameResult}
              <div class="rounded-lg p-3 mb-4 text-xs" style="background: color-mix(in srgb, var(--color-validated, green) 10%, transparent); color: var(--color-validated, green);">
                Done! {renameResult.renamed} renamed, {renameResult.failed} failed. Output: {renameResult.output_dir}
              </div>
            {/if}

            <button onclick={renameAll} disabled={isRenaming} class="px-4 py-2 text-sm btn-accent disabled:opacity-50">
              {isRenaming ? 'Renaming...' : `Rename ${allMedia.filter(m => m.person).length} Files`}
            </button>
          </div>
        </div>

      <!-- ========== FACES MODE (Pick Faces via Browse) ========== -->
      {:else if pickMedia && pickImgSrc}
        <div class="max-w-3xl mx-auto arch-card p-5 animate-fade-in">
          <div class="flex items-center justify-between mb-3">
            <div>
              <h2 class="text-sm font-semibold" style="font-family: var(--font-serif);">Set as Primary Photo</h2>
              <p class="text-xs" style="color: var(--ink-muted, #666);">{pickMedia.person?.givenName} {pickMedia.person?.surname}</p>
            </div>
            <div class="flex gap-2">
              <button onclick={setPrimary} class="px-4 py-2 text-xs font-medium btn-accent">Set as Primary</button>
              <button onclick={() => pickMedia = null} class="px-4 py-2 text-xs btn-secondary">Cancel</button>
            </div>
          </div>
          <img src={pickImgSrc} alt="" class="w-full max-h-[500px] object-contain rounded-lg" />
          <p class="text-[10px] mt-2" style="color: var(--ink-faint, #999);">{fileName(pickMedia.filePath)}</p>
        </div>

      <!-- ========== AUTO CROP MODE ========== -->
      {:else if mode === 'auto-crop'}
        <div class="max-w-4xl mx-auto animate-fade-in">
          {#if isAutoCropping}
            <div class="arch-card p-6 text-center">
              <h2 class="text-sm font-semibold mb-3" style="font-family: var(--font-serif);">Generating Auto Crops...</h2>
              <div class="arch-progress-track h-3 mb-3">
                <div class="arch-progress-bar h-3" style="width: {autoCropTotal > 0 ? (autoCropProgress / autoCropTotal * 100) : 0}%"></div>
              </div>
              <p class="text-xs" style="color: var(--ink-muted, #666);">{autoCropProgress} of {autoCropTotal} people processed</p>
            </div>

          {:else if cropCandidates.length === 0}
            <div class="arch-card p-6 text-center">
              <h2 class="text-sm font-semibold mb-2" style="font-family: var(--font-serif);">Auto Face Crop & Approval</h2>
              <p class="text-xs mb-4" style="color: var(--ink-muted, #666);">
                Automatically generates face-focused thumbnails for every person who has photos but no primary photo set.
                You then approve or skip each one.
              </p>
              <button onclick={startAutoCrop} class="px-5 py-2.5 text-sm btn-accent">Auto Crop All</button>
            </div>

          {:else if cropIndex >= cropCandidates.length}
            <div class="arch-card p-6 text-center">
              <h2 class="text-sm font-semibold mb-2" style="font-family: var(--font-serif);">Auto Crop Complete</h2>
              <div class="flex justify-center gap-8 mt-4 text-sm">
                <div><span class="text-2xl font-bold" style="color: var(--color-validated, green);">{autoCropApproved}</span><br/><span class="text-xs" style="color: var(--ink-muted, #666);">Approved</span></div>
                <div><span class="text-2xl font-bold" style="color: var(--ink-faint, #999);">{autoCropSkipped}</span><br/><span class="text-xs" style="color: var(--ink-muted, #666);">Skipped</span></div>
              </div>
              <button onclick={() => { cropCandidates = []; }} class="mt-5 px-4 py-2 text-xs btn-secondary">Done</button>
            </div>

          {:else}
            {@const candidate = cropCandidates[cropIndex]}
            <div class="arch-progress-track h-2 mb-4">
              <div class="arch-progress-bar h-2" style="width: {(cropIndex / cropCandidates.length) * 100}%"></div>
            </div>

            <div class="flex items-center justify-between mb-3">
              <h2 class="text-sm font-semibold" style="font-family: var(--font-serif);">
                {candidate.person.givenName} {candidate.person.surname}
                {#if candidate.person.birthDate || candidate.person.deathDate}
                  <span class="text-xs font-normal ml-2" style="color: var(--ink-muted, #666);">
                    ({candidate.person.birthDate || '?'} - {candidate.person.deathDate || '?'})
                  </span>
                {/if}
              </h2>
              <span class="text-xs" style="color: var(--ink-faint, #999);">{cropIndex + 1} of {cropCandidates.length}</span>
            </div>

            <div class="grid grid-cols-2 gap-6">
              <div class="arch-card p-4">
                <p class="text-[10px] mb-2 font-semibold uppercase tracking-wider" style="color: var(--ink-muted, #666);">Original</p>
                <img src={candidate.originalSrc} alt="" class="w-full max-h-[400px] object-contain rounded-lg" />
                <p class="text-[9px] mt-1 truncate" style="color: var(--ink-faint, #999);">{fileName(candidate.media.filePath)}</p>
              </div>

              <div class="arch-card p-4 flex flex-col items-center">
                <p class="text-[10px] mb-2 font-semibold uppercase tracking-wider" style="color: var(--ink-muted, #666);">Auto-Cropped Preview</p>
                <div class="w-[200px] h-[200px] rounded-full overflow-hidden my-4" style="border: 4px solid color-mix(in srgb, var(--color-validated, green) 30%, transparent);">
                  {#if currentCropThumbSrc}
                    <img src={currentCropThumbSrc} alt="" class="w-full h-full object-cover" />
                  {:else}
                    <div class="w-full h-full flex items-center justify-center text-xs" style="background: var(--parchment); color: var(--ink-faint, #999);">Loading...</div>
                  {/if}
                </div>
                <p class="text-[10px]" style="color: var(--ink-muted, #666);">200px circular avatar</p>
              </div>
            </div>

            <div class="flex justify-center gap-3 mt-5">
              <button onclick={approveCrop} class="px-6 py-2.5 text-sm btn-accent font-semibold">Approve</button>
              <button onclick={skipCrop} class="px-5 py-2.5 text-sm btn-secondary">Skip</button>
              <button onclick={rejectCrop} class="px-5 py-2.5 text-sm rounded-lg transition-colors" style="color: var(--color-error, red); border: 1px solid color-mix(in srgb, var(--color-error, red) 30%, transparent);">Reject</button>
            </div>

            <div class="flex justify-center gap-6 mt-3 text-xs" style="color: var(--ink-faint, #999);">
              <span>Approved: {autoCropApproved}</span>
              <span>Skipped: {autoCropSkipped}</span>
              <span>Remaining: {cropCandidates.length - cropIndex}</span>
            </div>
          {/if}
        </div>

      <!-- ========== DEDUP MODE ========== -->
      {:else if mode === 'dedup'}
        <div class="max-w-3xl mx-auto animate-fade-in">
          <div class="arch-card p-6 mb-4">
            <h2 class="text-sm font-semibold mb-2" style="font-family: var(--font-serif);">Image Deduplication</h2>
            <p class="text-xs mb-4" style="color: var(--ink-muted, #666);">
              Finds media entries that share the same file path (same image linked to multiple people or duplicated entries).
              Select which links to keep, then remove the rest.
            </p>

            {#if dedupRemoved > 0}
              <div class="rounded-lg p-3 mb-4 text-xs" style="background: color-mix(in srgb, var(--color-validated, green) 10%, transparent); color: var(--color-validated, green);">
                Removed {dedupRemoved} duplicate link{dedupRemoved !== 1 ? 's' : ''}.
              </div>
            {/if}

            {#if dupGroups.length === 0}
              <button onclick={scanDuplicates} disabled={isDedupScanning} class="px-4 py-2 text-sm btn-accent disabled:opacity-50">
                {isDedupScanning ? 'Scanning...' : 'Find Duplicates'}
              </button>
            {/if}
          </div>

          {#if dupGroups.length > 0}
            <p class="text-xs mb-3" style="color: var(--ink-muted, #666);">Found {dupGroups.length} duplicate group{dupGroups.length !== 1 ? 's' : ''}</p>

            {#each dupGroups as group, gi}
              <div class="arch-card p-4 mb-3">
                <div class="flex gap-4">
                  <div class="w-24 h-24 rounded-lg overflow-hidden shrink-0" style="background: var(--parchment);">
                    {#await (tauriInvoke('read_image_base64', { path: group.filePath }) as Promise<string>).catch(() => '') then src}
                      {#if src}<img {src} alt="" class="w-full h-full object-cover object-top" />{/if}
                    {/await}
                  </div>

                  <div class="flex-1">
                    <p class="text-[10px] truncate mb-2" style="color: var(--ink-faint, #999);">{fileName(group.filePath)}</p>
                    <p class="text-xs font-semibold mb-2">{group.entries.length} linked entries:</p>

                    {#each group.entries as entry}
                      <label class="flex items-center gap-2 text-xs mb-1 cursor-pointer">
                        <input
                          type="checkbox"
                          checked={group.keepIds.has(entry.id)}
                          onchange={() => toggleKeep(group, entry.id)}
                          class="rounded"
                        />
                        <span style="color: {group.keepIds.has(entry.id) ? 'var(--ink)' : 'var(--ink-faint, #999)'}; {group.keepIds.has(entry.id) ? '' : 'text-decoration: line-through;'}">
                          {entry.person?.givenName} {entry.person?.surname}
                          {#if entry.title} - {entry.title}{/if}
                          <span class="text-[9px] ml-1" style="color: var(--ink-faint, #999);">(ID: {entry.id})</span>
                        </span>
                      </label>
                    {/each}
                  </div>
                </div>
              </div>
            {/each}

            <div class="flex gap-3 mt-4">
              <button onclick={removeDuplicateLinks} class="px-4 py-2 text-sm btn-accent">
                Remove {dupGroups.reduce((s, g) => s + g.entries.length - g.keepIds.size, 0)} Duplicate Links
              </button>
              <button onclick={() => dupGroups = []} class="px-4 py-2 text-sm btn-secondary">Cancel</button>
            </div>
          {/if}
        </div>

      <!-- ========== CLEANUP MODE ========== -->
      {:else if mode === 'cleanup'}
        <div class="max-w-3xl mx-auto animate-fade-in">
          <div class="arch-card p-6 mb-4">
            <h2 class="text-sm font-semibold mb-2" style="font-family: var(--font-serif);">Media Cleanup Tools</h2>
            <p class="text-xs mb-4" style="color: var(--ink-muted, #666);">
              Scan for missing files, orphaned entries, and other media issues. Only DB entries are removed -- original files are never deleted.
            </p>

            {#if cleanupDone}
              <div class="rounded-lg p-3 mb-4 text-xs" style="background: color-mix(in srgb, var(--color-validated, green) 10%, transparent); color: var(--color-validated, green);">
                Cleaned {cleanupStats.cleaned} entries.
              </div>
            {/if}

            <button onclick={scanCleanup} disabled={isScanningCleanup} class="px-4 py-2 text-sm btn-accent disabled:opacity-50">
              {isScanningCleanup ? 'Scanning...' : 'Scan for Issues'}
            </button>
          </div>

          {#if (missingFiles.length > 0 || orphanedEntries.length > 0)}
            {#if missingFiles.length > 0}
              <div class="arch-card p-5 mb-3">
                <div class="flex items-center justify-between mb-3">
                  <div>
                    <h3 class="text-sm font-semibold" style="font-family: var(--font-serif);">Missing Files</h3>
                    <p class="text-xs" style="color: var(--ink-muted, #666);">{missingFiles.length} media entries reference files that no longer exist</p>
                  </div>
                  <button onclick={cleanupMissing} class="px-3 py-1.5 text-xs btn-accent">Clean Up</button>
                </div>
                <div class="max-h-48 overflow-y-auto">
                  {#each missingFiles as m}
                    <div class="flex items-center gap-2 py-1" style="border-bottom: 1px solid color-mix(in srgb, var(--ink) 6%, transparent);">
                      <span class="text-[10px] font-mono" style="color: var(--color-error, red);">MISSING</span>
                      <span class="text-[10px] truncate flex-1" style="color: var(--ink);">{fileName(m.filePath)}</span>
                      <span class="text-[9px]" style="color: var(--ink-faint, #999);">{m.person?.givenName} {m.person?.surname}</span>
                    </div>
                  {/each}
                </div>
              </div>
            {/if}

            {#if orphanedEntries.length > 0}
              <div class="arch-card p-5 mb-3">
                <div class="flex items-center justify-between mb-3">
                  <div>
                    <h3 class="text-sm font-semibold" style="font-family: var(--font-serif);">Orphaned Entries</h3>
                    <p class="text-xs" style="color: var(--ink-muted, #666);">{orphanedEntries.length} media entries not linked to any person</p>
                  </div>
                  <button onclick={cleanupOrphaned} class="px-3 py-1.5 text-xs btn-accent">Clean Up</button>
                </div>
                <div class="max-h-48 overflow-y-auto">
                  {#each orphanedEntries as m}
                    <div class="flex items-center gap-2 py-1" style="border-bottom: 1px solid color-mix(in srgb, var(--ink) 6%, transparent);">
                      <span class="text-[10px] font-mono" style="color: orange;">ORPHAN</span>
                      <span class="text-[10px] truncate flex-1" style="color: var(--ink);">{m.title || fileName(m.filePath)}</span>
                      <span class="text-[9px]" style="color: var(--ink-faint, #999);">{m.format || 'no format'}</span>
                    </div>
                  {/each}
                </div>
              </div>
            {/if}
          {:else if !isScanningCleanup && cleanupStats.missing === 0 && cleanupStats.orphaned === 0 && !cleanupDone}
            <!-- Haven't scanned yet -->
          {:else if !isScanningCleanup && cleanupStats.missing === 0 && cleanupStats.orphaned === 0}
            <div class="arch-card p-5 text-center">
              <p class="text-sm font-semibold" style="color: var(--color-validated, green);">All clean! No issues found.</p>
            </div>
          {/if}
        </div>

      <!-- ========== SURNAMES MODE ========== -->
      {:else if mode === 'surnames'}
        <div class="max-w-4xl mx-auto animate-fade-in">
          <h2 class="text-sm font-semibold mb-3" style="font-family: var(--font-serif);">Media by Surname</h2>
          <p class="text-xs mb-4" style="color: var(--ink-muted, #666);">{surnameGroups.length} surname{surnameGroups.length !== 1 ? 's' : ''} with media</p>

          {#each surnameGroups as group, gi}
            <div class="arch-card mb-2 overflow-hidden">
              <button
                onclick={() => toggleSurnameGroup(gi)}
                class="w-full flex items-center justify-between px-4 py-3 text-left transition-colors"
                onmouseenter={(e) => { (e.currentTarget as HTMLElement).style.background = 'color-mix(in srgb, var(--ink) 3%, transparent)'; }}
                onmouseleave={(e) => { (e.currentTarget as HTMLElement).style.background = ''; }}
              >
                <span class="text-sm font-semibold" style="font-family: var(--font-serif);">{group.surname}</span>
                <span class="text-xs" style="color: var(--ink-faint, #999);">{group.items.length} media</span>
              </button>

              {#if group.expanded}
                <div class="px-4 pb-4">
                  <div class="grid grid-cols-8 gap-1.5">
                    {#each group.items.filter(m => /\.(jpe?g|png|gif|webp|bmp)$/i.test(m.filePath)).slice(0, 24) as m}
                      <div class="aspect-square rounded-lg overflow-hidden" style="background: var(--parchment);">
                        {#await (tauriInvoke('read_image_base64', { path: m.filePath }) as Promise<string>).catch(() => '') then src}
                          {#if src}<img {src} alt="" class="w-full h-full object-cover object-top" />{/if}
                        {/await}
                      </div>
                    {/each}
                  </div>
                  {#if group.items.filter(m => /\.(jpe?g|png|gif|webp|bmp)$/i.test(m.filePath)).length > 24}
                    <p class="text-[10px] mt-2" style="color: var(--ink-faint, #999);">... and {group.items.filter(m => /\.(jpe?g|png|gif|webp|bmp)$/i.test(m.filePath)).length - 24} more</p>
                  {/if}
                  {#if group.items.filter(m => !/\.(jpe?g|png|gif|webp|bmp)$/i.test(m.filePath)).length > 0}
                    <p class="text-[10px] mt-1" style="color: var(--ink-faint, #999);">{group.items.filter(m => !/\.(jpe?g|png|gif|webp|bmp)$/i.test(m.filePath)).length} non-image files</p>
                  {/if}
                </div>
              {/if}
            </div>
          {/each}
        </div>

      <!-- ========== AI CLASSIFY MODE ========== -->
      {:else if mode === 'ai-classify'}
        <div class="max-w-3xl mx-auto animate-fade-in">
          <div class="arch-card p-6 mb-4">
            <h2 class="text-sm font-semibold mb-2" style="font-family: var(--font-serif);">AI Image Classification</h2>
            <p class="text-xs mb-2" style="color: var(--ink-muted, #666);">
              Uses AI vision (Groq Llama Vision) to analyze each image and classify it:
              single person, married couple, family/group, document, grave, castle, crest, or other.
            </p>
            <p class="text-xs mb-4" style="color: var(--ink-faint, #999);">
              Cost: ~$0.05 for {allMedia.filter(m => /\.(jpe?g|png|gif|webp|bmp)$/i.test(m.filePath)).length} images via Groq.
              Already classified: {classifications.size} images.
              Remaining: {allMedia.filter(m => /\.(jpe?g|png|gif|webp|bmp)$/i.test(m.filePath) && !classifications.has(m.id)).length} images.
            </p>

            {#if classifyDone}
              <div class="rounded-lg p-3 mb-4 text-xs" style="background: color-mix(in srgb, var(--color-validated, green) 10%, transparent); color: var(--color-validated, green);">
                Done! {classifyResults.classified} classified, {classifyResults.errors} errors.
              </div>
            {/if}

            {#if isClassifying}
              <div class="mb-4">
                <div class="flex items-center justify-between text-xs mb-1" style="color: var(--ink-muted, #666);">
                  <span class="truncate max-w-[300px]">{classifyCurrentFile}</span>
                  <span style="font-family: var(--font-mono);">{classifyProgress}/{classifyTotal}</span>
                </div>
                <div class="arch-progress-track h-2">
                  <div class="arch-progress-bar h-2" style="width: {classifyTotal > 0 ? (classifyProgress / classifyTotal) * 100 : 0}%;"></div>
                </div>
              </div>
            {:else}
              <button onclick={classifyWithAI} class="px-5 py-2.5 text-sm btn-accent disabled:opacity-50">
                Classify {allMedia.filter(m => /\.(jpe?g|png|gif|webp|bmp)$/i.test(m.filePath) && !classifications.has(m.id)).length} Unclassified Images
              </button>
            {/if}
          </div>

          {#if classifications.size > 0}
            <div class="arch-card p-5 mb-4">
              <h3 class="text-xs font-semibold mb-3" style="font-family: var(--font-serif);">Classification Summary</h3>
              <div class="grid grid-cols-4 gap-3">
                {#each ['single', 'married', 'family-group', 'document', 'graves', 'castles', 'crests', 'other'] as cat}
                  {@const count = [...classifications.values()].filter(c => c.category === cat).length}
                  {#if count > 0}
                    <button
                      onclick={() => { activeCategory = (cat === 'married' || cat === 'family-group') ? 'group' : cat; mode = 'browse'; }}
                      class="arch-card p-3 text-center hover:ring-2 transition-all"
                      style="--tw-ring-color: var(--accent, #6b5b3e);"
                    >
                      <div class="text-lg font-bold" style="color: var(--accent, #6b5b3e); font-family: var(--font-mono);">{count}</div>
                      <div class="text-[10px]" style="color: var(--ink-muted, #666);">{cat.replace('-', ' ')}</div>
                    </button>
                  {/if}
                {/each}
              </div>
            </div>

            <button
              onclick={async () => {
                const db = await getDb();
                await db.execute(`DELETE FROM settings WHERE key LIKE 'media_class_%'`);
                classifications = new Map();
                classifyDone = false;
              }}
              class="text-xs px-3 py-1.5 rounded-lg"
              style="color: var(--color-error, red);"
            >Reset All Classifications</button>
          {/if}
        </div>

      <!-- ========== BROWSE MODE (Default) ========== -->
      {:else}
        {#if !isTauri()}
          <div class="arch-card p-4 mb-4" style="border-left: 3px solid var(--accent);">
            <p class="text-sm" style="color: var(--ink); font-family: var(--font-sans);">
              Photo thumbnails require the desktop app. Use Settings to import your GEDCOM on desktop first.
            </p>
          </div>
          <div class="arch-card divide-y arch-card-divide">
            {#each categoryFiltered as m}
              <div class="px-4 py-3">
                <div class="text-sm font-semibold" style="color: var(--ink);">{m.title || fileName(m.filePath)}</div>
                <div class="text-xs mt-1" style="color: var(--ink-muted);">
                  Linked: {m.person?.givenName} {m.person?.surname}
                </div>
                <div class="text-xs mt-1" style="color: var(--ink-faint);">
                  Category: {categorizeWithAI(m).replace('-', ' ')}
                </div>
                <div class="text-[10px] mt-1" style="color: var(--ink-faint);">{m.filePath}</div>
                {#if m.format === 'PRIMARY'}
                  <span class="inline-flex mt-1.5 text-[9px] px-1.5 py-0.5 rounded-full font-semibold" style="background: color-mix(in srgb, var(--color-validated, green) 10%, transparent); color: var(--color-validated, green);">Primary</span>
                {/if}
              </div>
            {/each}
          </div>
        {:else}
          <div class="grid grid-cols-3 gap-4">
            {#each categoryFiltered as m, idx}
              <button
                onclick={() => {
                  if (/\.(jpe?g|png|gif|webp|bmp)$/i.test(m.filePath)) openLightbox(idx);
                }}
                oncontextmenu={(e) => { e.preventDefault(); startPick(m); }}
                class="arch-card overflow-hidden text-left cursor-pointer hover:ring-2 transition-all group"
                style="--tw-ring-color: var(--accent, #6b5b3e);"
              >
                {#if /\.(jpe?g|png|gif|webp|bmp)$/i.test(m.filePath)}
                  <div class="aspect-square overflow-hidden" style="background: var(--parchment);">
                    {#await (tauriInvoke('read_image_base64', { path: m.filePath }) as Promise<string>).catch(() => '') then src}
                      {#if src}<img {src} alt="" class="w-full h-full object-cover" />
                      {:else}<div class="w-full h-full flex items-center justify-center text-xs" style="color: var(--ink-faint, #999);">No preview</div>{/if}
                    {/await}
                  </div>
                {:else}
                  <div class="aspect-square flex flex-col items-center justify-center gap-2" style="background: var(--parchment);">
                    <svg class="w-8 h-8 opacity-40" fill="none" stroke="currentColor" stroke-width="1.5" viewBox="0 0 24 24" style="color: var(--ink-muted, #666);">
                      <path stroke-linecap="round" stroke-linejoin="round" d="M19.5 14.25v-2.625a3.375 3.375 0 00-3.375-3.375h-1.5A1.125 1.125 0 0113.5 7.125v-1.5a3.375 3.375 0 00-3.375-3.375H8.25m2.25 0H5.625c-.621 0-1.125.504-1.125 1.125v17.25c0 .621.504 1.125 1.125 1.125h12.75c.621 0 1.125-.504 1.125-1.125V11.25a9 9 0 00-9-9z" />
                    </svg>
                    <span class="text-xs font-bold" style="color: var(--ink-faint, #999);">{m.filePath.split('.').pop()?.toUpperCase()}</span>
                  </div>
                {/if}
                <div class="p-3">
                  <div class="text-xs font-bold truncate" style="font-family: var(--font-sans); color: var(--ink);">{m.person?.givenName} {m.person?.surname}</div>
                  <div class="text-[10px] truncate mt-0.5" style="color: var(--ink-muted, #666);">{m.title || fileName(m.filePath)}</div>
                  <div class="flex items-center gap-1.5 mt-1.5">
                    <span class="text-[8px] px-1.5 py-0.5 rounded-full" style="background: var(--parchment); color: var(--ink-faint, #999); font-family: var(--font-mono);">{categorizeWithAI(m).replace('-', ' ')}</span>
                    {#if m.format === 'PRIMARY'}
                      <span class="text-[8px] px-1.5 py-0.5 rounded-full font-semibold" style="background: color-mix(in srgb, var(--color-validated, green) 10%, transparent); color: var(--color-validated, green);">Primary</span>
                    {/if}
                  </div>
                </div>
              </button>
            {/each}
          </div>
        {/if}

        {#if categoryFiltered.length === 0}
          <div class="flex flex-col items-center justify-center py-20" style="color: var(--ink-faint, #999);">
            <svg class="w-12 h-12 mb-3 opacity-30" fill="none" stroke="currentColor" stroke-width="1" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" d="M2.25 15.75l5.159-5.159a2.25 2.25 0 013.182 0l5.159 5.159m-1.5-1.5l1.409-1.409a2.25 2.25 0 013.182 0l2.909 2.909M3.75 21h16.5A2.25 2.25 0 0022.5 18.75V5.25A2.25 2.25 0 0020.25 3H3.75A2.25 2.25 0 001.5 5.25v13.5A2.25 2.25 0 003.75 21z" />
            </svg>
            <p class="text-sm">No media in this category</p>
            <p class="text-xs mt-1">Try a different category or clear the search</p>
          </div>
        {/if}
      {/if}
    </div>
  </div>
</div>

<!-- ========== LIGHTBOX OVERLAY ========== -->
{#if lightboxIndex >= 0}
  {@const item = categoryFiltered[lightboxIndex]}
  <div
    class="fixed inset-0 z-50 flex items-center justify-center"
    style="background: rgba(0,0,0,0.88);"
    role="dialog"
  >
    <button
      onclick={closeLightbox}
      class="absolute top-4 right-4 w-10 h-10 flex items-center justify-center text-white/80 hover:text-white text-2xl rounded-full hover:bg-white/10 transition-colors z-10"
      aria-label="Close"
    >&times;</button>

    {#if lightboxIndex > 0}
      <button
        onclick={() => lightboxNav(-1)}
        class="absolute left-4 top-1/2 -translate-y-1/2 w-12 h-12 flex items-center justify-center text-white/70 hover:text-white text-3xl rounded-full hover:bg-white/10 transition-colors z-10"
        aria-label="Previous"
      >&#8249;</button>
    {/if}

    {#if lightboxIndex < categoryFiltered.length - 1}
      <button
        onclick={() => lightboxNav(1)}
        class="absolute right-4 top-1/2 -translate-y-1/2 w-12 h-12 flex items-center justify-center text-white/70 hover:text-white text-3xl rounded-full hover:bg-white/10 transition-colors z-10"
        aria-label="Next"
      >&#8250;</button>
    {/if}

    <div class="max-w-[85vw] max-h-[85vh] flex flex-col items-center">
      {#if isLightboxLoading}
        <div class="text-white/60 text-sm">Loading...</div>
      {:else if lightboxSrc}
        <img src={lightboxSrc} alt="" class="max-w-full max-h-[75vh] object-contain rounded-lg shadow-2xl" />
      {:else}
        <div class="text-white/60 text-sm">Could not load image</div>
      {/if}

      {#if item}
        <div class="mt-4 text-center">
          <p class="text-white text-sm font-semibold">{item.person?.givenName} {item.person?.surname}</p>
          {#if item.person?.birthDate || item.person?.deathDate}
            <p class="text-white/60 text-xs">{item.person?.birthDate || '?'} - {item.person?.deathDate || '?'}</p>
          {/if}
          <p class="text-white/40 text-[10px] mt-1">{item.title || fileName(item.filePath)}</p>
          <p class="text-white/30 text-[9px]">{item.filePath}</p>
          {#if item.format}
            <span class="text-[9px] px-2 py-0.5 bg-white/10 text-white/60 rounded-full mt-1 inline-block">{item.format}</span>
          {/if}
          <p class="text-white/30 text-[9px] mt-1">{lightboxIndex + 1} / {categoryFiltered.length}</p>
          <button
            onclick={() => { closeLightbox(); startPick(item); }}
            class="mt-2 px-3 py-1 text-[10px] rounded-full bg-white/10 text-white/70 hover:bg-white/20 hover:text-white transition-colors"
          >Set as Primary</button>
        </div>
      {/if}
    </div>
  </div>
{/if}
