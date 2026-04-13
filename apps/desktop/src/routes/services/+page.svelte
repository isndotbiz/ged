<script lang="ts">
  import { t } from '$lib/i18n';
  import { getAllPersons, getDb, getSetting, setSetting } from '$lib/db';
  import { isTauri } from '$lib/platform';
  import { exportGedcom } from '$lib/gedcom-exporter';

  // --- Service connection state ---
  interface ServiceStatus {
    connected: boolean;
    username: string;
    lastSync: string;
    loading: boolean;
    error: string;
  }

  let familysearch = $state<ServiceStatus>({ connected: false, username: '', lastSync: '', loading: false, error: '' });
  let geni = $state<ServiceStatus>({ connected: false, username: '', lastSync: '', loading: false, error: '' });
  let wikitree = $state<ServiceStatus>({ connected: false, username: '', lastSync: '', loading: false, error: '' });

  let wikitreeApiKey = $state('');
  let exportStatus = $state('');
  let exportLoading = $state(false);
  let fsSearchGiven = $state('');
  let fsSearchSurname = $state('');
  let fsSearchYear = $state('');
  let fsSearchLoading = $state(false);
  let fsSearchResults = $state<{ id: string; title: string; summary: string }[]>([]);

  interface MatchCandidate {
    externalId: string;
    externalName: string;
    localXref: string;
    localName: string;
    confidence: number;
    reason: string;
    source: 'familysearch' | 'geni' | 'wikitree';
    payload: string;
  }

  interface SyncProgress {
    active: boolean;
    message: string;
    pct: number;
  }

  let fsSyncProgress = $state<SyncProgress>({ active: false, message: '', pct: 0 });
  let geniSyncProgress = $state<SyncProgress>({ active: false, message: '', pct: 0 });
  let wtSearchProgress = $state<SyncProgress>({ active: false, message: '', pct: 0 });
  let geniMatches = $state<MatchCandidate[]>([]);
  let wikitreeMatches = $state<MatchCandidate[]>([]);

  // --- Load saved connection state ---
  async function loadConnectionState() {
    try {
      const fsToken = await getSetting('service_familysearch_token');
      const fsUser = await getSetting('service_familysearch_user');
      const fsSync = await getSetting('service_familysearch_last_sync');
      if (fsToken) {
        familysearch = { ...familysearch, connected: true, username: fsUser ?? '', lastSync: fsSync ?? '' };
      }

      const geniToken = await getSetting('service_geni_token');
      const geniUser = await getSetting('service_geni_user');
      const geniSync = await getSetting('service_geni_last_sync');
      if (geniToken) {
        geni = { ...geni, connected: true, username: geniUser ?? '', lastSync: geniSync ?? '' };
      }

      const wtKey = await getSetting('service_wikitree_app_id');
      const wtUser = await getSetting('service_wikitree_user');
      if (wtKey) {
        wikitreeApiKey = wtKey;
        wikitree = { ...wikitree, connected: true, username: wtUser ?? '' };
      }
    } catch (e) {
      console.error('Failed to load service state:', e);
    }
  }

  $effect(() => { loadConnectionState(); });

  // --- FamilySearch OAuth2 ---
  async function connectFamilySearch() {
    familysearch = { ...familysearch, loading: true, error: '' };
    try {
      const clientId = await getSetting('service_familysearch_client_id');
      if (!clientId) {
        familysearch = { ...familysearch, loading: false, error: 'Set your FamilySearch Client ID in the field below first.' };
        return;
      }
      const redirectUri = getFamilySearchRedirectUri();
      const codeVerifier = generateCodeVerifier();
      const codeChallenge = await createCodeChallenge(codeVerifier);
      const state = cryptoRandomString(32);
      await setSetting('service_familysearch_pkce_verifier', codeVerifier);
      await setSetting('service_familysearch_oauth_state', state);
      const authUrl = `https://ident.familysearch.org/cis-web/oauth2/v3/authorization?response_type=code&client_id=${encodeURIComponent(clientId)}&redirect_uri=${encodeURIComponent(redirectUri)}&scope=openid%20profile&code_challenge=${encodeURIComponent(codeChallenge)}&code_challenge_method=S256&state=${encodeURIComponent(state)}`;
      window.location.href = authUrl;
    } catch (e) {
      familysearch = { ...familysearch, loading: false, error: `Connection failed: ${e}` };
    }
  }

  async function disconnectFamilySearch() {
    await setSetting('service_familysearch_token', '');
    await setSetting('service_familysearch_refresh_token', '');
    await setSetting('service_familysearch_user', '');
    await setSetting('service_familysearch_last_sync', '');
    fsSearchResults = [];
    familysearch = { connected: false, username: '', lastSync: '', loading: false, error: '' };
  }

  // --- Geni OAuth2 ---
  async function connectGeni() {
    geni = { ...geni, loading: true, error: '' };
    try {
      const clientId = await getSetting('service_geni_client_id');
      if (!clientId) {
        geni = { ...geni, loading: false, error: 'Set your Geni App Key in the field below first.' };
        return;
      }
      const state = cryptoRandomString(32);
      await setSetting('service_geni_oauth_state', state);
      const redirectUri = `${window.location.origin}/services?service=geni`;
      const authUrl = `https://www.geni.com/platform/oauth/authorize?client_id=${encodeURIComponent(clientId)}&redirect_uri=${encodeURIComponent(redirectUri)}&response_type=code&state=${encodeURIComponent(state)}`;
      window.location.href = authUrl;
    } catch (e) {
      geni = { ...geni, loading: false, error: `Connection failed: ${e}` };
    }
  }

  async function disconnectGeni() {
    await setSetting('service_geni_token', '');
    await setSetting('service_geni_user', '');
    await setSetting('service_geni_last_sync', '');
    geni = { connected: false, username: '', lastSync: '', loading: false, error: '' };
  }

  // --- WikiTree API Key ---
  async function connectWikiTree() {
    wikitree = { ...wikitree, loading: true, error: '' };
    if (!wikitreeApiKey.trim()) {
      wikitree = { ...wikitree, loading: false, error: 'Enter your WikiTree API key first.' };
      return;
    }
    try {
      const resp = await fetch('https://api.wikitree.com/api.php', {
        method: 'POST',
        headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
        body: `action=getProfile&key=${encodeURIComponent(wikitreeApiKey.trim())}&fields=Id,Name,RealName`
      });
      if (!resp.ok) throw new Error(`HTTP ${resp.status}`);
      const data = await resp.json();
      const profileName = data?.[0]?.profile?.RealName ?? data?.[0]?.profile?.Name ?? 'Connected';
      await setSetting('service_wikitree_app_id', wikitreeApiKey.trim());
      await setSetting('service_wikitree_user', profileName);
      wikitree = { connected: true, username: profileName, lastSync: '', loading: false, error: '' };
    } catch (e) {
      wikitree = { ...wikitree, loading: false, error: `Could not validate key: ${e}` };
    }
  }

  async function disconnectWikiTree() {
    await setSetting('service_wikitree_app_id', '');
    await setSetting('service_wikitree_user', '');
    wikitreeApiKey = '';
    wikitree = { connected: false, username: '', lastSync: '', loading: false, error: '' };
  }

  // --- Client ID fields ---
  let fsClientId = $state('');
  let geniClientId = $state('');

  async function loadClientIds() {
    fsClientId = (await getSetting('service_familysearch_client_id')) ?? '';
    geniClientId = (await getSetting('service_geni_client_id')) ?? '';
  }
  $effect(() => { loadClientIds(); });

  async function saveFsClientId() {
    await setSetting('service_familysearch_client_id', fsClientId.trim());
  }
  async function saveGeniClientId() {
    await setSetting('service_geni_client_id', geniClientId.trim());
  }

  // --- GEDCOM Export ---
  async function exportGedcomFile(format: '5.5.1' | '7.0') {
    exportLoading = true;
    exportStatus = '';
    try {
      const gedcomText = await exportGedcom(format);
      const blob = new Blob([gedcomText], { type: 'text/plain' });
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = `gedfix_export_${format.replace('.', '_')}_${new Date().toISOString().slice(0, 10)}.ged`;
      a.click();
      URL.revokeObjectURL(url);
      exportStatus = 'Export complete!';
    } catch (e) {
      exportStatus = `Export failed: ${e}`;
    } finally {
      exportLoading = false;
      setTimeout(() => { exportStatus = ''; }, 4000);
    }
  }

  async function handleExportGedcom551() {
    await exportGedcomFile('5.5.1');
  }

  async function handleExportGedcom70() {
    await exportGedcomFile('7.0');
  }

  function getFamilySearchRedirectUri() {
    return isTauri() ? 'http://localhost:1420/services' : 'https://gedfix.isn.biz/services';
  }

  function base64UrlEncode(bytes: Uint8Array): string {
    let binary = '';
    for (const b of bytes) binary += String.fromCharCode(b);
    return btoa(binary).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/g, '');
  }

  function cryptoRandomString(length = 64): string {
    const bytes = new Uint8Array(length);
    crypto.getRandomValues(bytes);
    return base64UrlEncode(bytes).slice(0, length);
  }

  function generateCodeVerifier(): string {
    return cryptoRandomString(64);
  }

  async function createCodeChallenge(verifier: string): Promise<string> {
    const data = new TextEncoder().encode(verifier);
    const digest = await crypto.subtle.digest('SHA-256', data);
    return base64UrlEncode(new Uint8Array(digest));
  }

  async function handleFamilySearchCallback() {
    const params = new URLSearchParams(window.location.search);
    const code = params.get('code');
    const returnedState = params.get('state');
    if (!code) return;

    familysearch = { ...familysearch, loading: true, error: '' };
    try {
      const expectedState = await getSetting('service_familysearch_oauth_state');
      const verifier = await getSetting('service_familysearch_pkce_verifier');
      const clientId = await getSetting('service_familysearch_client_id');
      if (!clientId || !verifier) throw new Error('Missing PKCE state or client ID');
      if (!returnedState || returnedState !== expectedState) throw new Error('OAuth state mismatch');

      const body = new URLSearchParams({
        grant_type: 'authorization_code',
        code,
        client_id: clientId,
        redirect_uri: getFamilySearchRedirectUri(),
        code_verifier: verifier
      });

      const tokenResp = await fetch('https://ident.familysearch.org/cis-web/oauth2/v3/token', {
        method: 'POST',
        headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
        body: body.toString()
      });
      if (!tokenResp.ok) {
        const err = await tokenResp.text();
        throw new Error(`Token exchange failed (${tokenResp.status}): ${err}`);
      }
      const tokenData = await tokenResp.json();
      const accessToken = tokenData.access_token as string;
      const refreshToken = tokenData.refresh_token as string;
      if (!accessToken) throw new Error('No access token returned');

      await setSetting('service_familysearch_token', accessToken);
      await setSetting('service_familysearch_refresh_token', refreshToken || '');
      await setSetting('service_familysearch_last_sync', new Date().toISOString());
      await setSetting('service_familysearch_pkce_verifier', '');
      await setSetting('service_familysearch_oauth_state', '');

      let username = 'Connected';
      try {
        const profileResp = await fetch('https://api.familysearch.org/platform/users/current', {
          headers: {
            Authorization: `Bearer ${accessToken}`,
            Accept: 'application/json'
          }
        });
        if (profileResp.ok) {
          const profileData = await profileResp.json();
          const contactName = profileData?.users?.[0]?.contactName;
          if (contactName) username = contactName;
        }
      } catch { /* no-op */ }

      await setSetting('service_familysearch_user', username);
      familysearch = { connected: true, username, lastSync: new Date().toISOString(), loading: false, error: '' };
      const clean = new URL(window.location.href);
      clean.searchParams.delete('code');
      clean.searchParams.delete('state');
      clean.searchParams.delete('scope');
      clean.searchParams.delete('session_state');
      window.history.replaceState({}, '', clean.toString());
    } catch (e) {
      familysearch = { ...familysearch, loading: false, error: `FamilySearch OAuth failed: ${e}` };
    }
  }

  async function handleGeniCallback() {
    const params = new URLSearchParams(window.location.search);
    const code = params.get('code');
    const service = params.get('service');
    const returnedState = params.get('state');
    if (!code || service !== 'geni') return;

    geni = { ...geni, loading: true, error: '' };
    try {
      const expectedState = await getSetting('service_geni_oauth_state');
      const clientId = await getSetting('service_geni_client_id');
      if (!clientId) throw new Error('Missing Geni App Key');
      if (!returnedState || returnedState !== expectedState) throw new Error('OAuth state mismatch');

      const tokenResp = await fetch('https://www.geni.com/platform/oauth/access_token', {
        method: 'POST',
        headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
        body: new URLSearchParams({
          grant_type: 'authorization_code',
          code,
          client_id: clientId,
          redirect_uri: `${window.location.origin}/services?service=geni`
        }).toString()
      });
      if (!tokenResp.ok) {
        const err = await tokenResp.text();
        throw new Error(`Token exchange failed (${tokenResp.status}): ${err}`);
      }
      const tokenData = await tokenResp.json();
      const accessToken = tokenData.access_token as string;
      if (!accessToken) throw new Error('No access token returned');

      await setSetting('service_geni_token', accessToken);
      await setSetting('service_geni_oauth_state', '');

      let username = 'Connected';
      try {
        const meResp = await fetch('https://www.geni.com/api/profile', {
          headers: { Authorization: `Bearer ${accessToken}` }
        });
        if (meResp.ok) {
          const me = await meResp.json();
          username = me?.name || me?.first_name || username;
        }
      } catch { /* no-op */ }

      await setSetting('service_geni_user', username);
      await setSetting('service_geni_last_sync', new Date().toISOString());
      geni = { connected: true, username, lastSync: new Date().toISOString(), loading: false, error: '' };

      const clean = new URL(window.location.href);
      clean.searchParams.delete('code');
      clean.searchParams.delete('state');
      clean.searchParams.delete('service');
      window.history.replaceState({}, '', clean.toString());
    } catch (e) {
      geni = { ...geni, loading: false, error: `Geni OAuth failed: ${e}` };
    }
  }

  async function familysearchApiCall(path: string): Promise<any> {
    const token = await getSetting('service_familysearch_token');
    if (!token) throw new Error('No FamilySearch token found. Connect FamilySearch first.');
    const resp = await fetch(`https://api.familysearch.org${path}`, {
      headers: { Authorization: `Bearer ${token}`, Accept: 'application/json' }
    });
    if (!resp.ok) {
      const err = await resp.text();
      throw new Error(`FamilySearch API failed (${resp.status}): ${err}`);
    }
    return await resp.json();
  }

  function parseYear(text: string): number | null {
    const m = (text || '').match(/(\d{4})/);
    return m ? Number.parseInt(m[1], 10) : null;
  }

  function fullName(givenName: string, surname: string): string {
    return `${givenName || ''} ${surname || ''}`.trim().replace(/\s+/g, ' ').toLowerCase();
  }

  function personMatchConfidence(
    localGiven: string,
    localSurname: string,
    localBirthDate: string,
    extGiven: string,
    extSurname: string,
    extBirthDate: string
  ): { score: number; reason: string } {
    const local = fullName(localGiven, localSurname);
    const ext = fullName(extGiven, extSurname);
    const localYear = parseYear(localBirthDate);
    const extYear = parseYear(extBirthDate);
    const sameName = local && ext && local === ext;
    const sharedSurname = (localSurname || '').trim().toLowerCase() && (localSurname || '').trim().toLowerCase() === (extSurname || '').trim().toLowerCase();
    if (sameName && localYear && extYear && Math.abs(localYear - extYear) <= 1) {
      return { score: 0.95, reason: 'Exact name and birth year match' };
    }
    if (sameName) return { score: 0.82, reason: 'Exact full-name match' };
    if (sharedSurname && localYear && extYear && Math.abs(localYear - extYear) <= 2) {
      return { score: 0.74, reason: 'Surname and close birth year match' };
    }
    if (sharedSurname) return { score: 0.62, reason: 'Surname match' };
    return { score: 0, reason: '' };
  }

  async function createImportProposal(
    source: 'familysearch' | 'geni' | 'wikitree',
    externalId: string,
    localXref: string,
    payload: unknown,
    reasoning: string,
    confidence: number
  ) {
    const db = await getDb();
    await db.execute(
      `INSERT INTO proposal (agentRunId, entityType, entityId, fieldName, oldValue, newValue, confidence, reasoning, evidenceSource, status, createdAt)
       VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,'pending',datetime('now'))`,
      [
        `${source}_sync`,
        'person',
        localXref || externalId,
        'external_import',
        '',
        JSON.stringify(payload),
        confidence,
        reasoning,
        source
      ]
    );
  }

  async function searchFamilySearch() {
    fsSearchLoading = true;
    fsSearchResults = [];
    familysearch = { ...familysearch, error: '' };
    try {
      const token = await getSetting('service_familysearch_token');
      if (!token) throw new Error('No FamilySearch access token. Connect first.');
      const givenName = fsSearchGiven.trim();
      const surname = fsSearchSurname.trim();
      const year = fsSearchYear.trim();
      if (!givenName || !surname || !year) throw new Error('Enter given name, surname, and birth year');
      const q = `givenName:${givenName}+surname:${surname}+birthLikeDate:${year}`;
      const resp = await fetch(`https://api.familysearch.org/platform/search/persons?q=${encodeURIComponent(q)}`, {
        headers: {
          Authorization: `Bearer ${token}`,
          Accept: 'application/json'
        }
      });
      if (!resp.ok) {
        const err = await resp.text();
        throw new Error(`Search failed (${resp.status}): ${err}`);
      }
      const data = await resp.json();
      const entries = data?.entries || [];
      fsSearchResults = entries.map((entry: any) => {
        const content = entry.content || {};
        const id = content.id || entry.id || '';
        const title = content?.display?.name || content?.display?.fullName || id || 'Unknown person';
        const lifespan = content?.display?.lifespan || '';
        const summary = [lifespan, content?.display?.birthPlace, content?.display?.deathPlace].filter(Boolean).join(' · ');
        return { id, title, summary };
      });
    } catch (e) {
      familysearch = { ...familysearch, error: String(e) };
    } finally {
      fsSearchLoading = false;
    }
  }

  async function importFamilySearchResult(result: { id: string; title: string; summary: string }) {
    const db = await getDb();
    await db.execute(
      `INSERT INTO proposal (agentRunId, entityType, entityId, fieldName, oldValue, newValue, confidence, reasoning, evidenceSource, status, createdAt)
       VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,'pending',datetime('now'))`,
      [
        'familysearch_search',
        'person',
        result.id || '',
        'source',
        '',
        JSON.stringify(result),
        0.75,
        'Imported from FamilySearch search result',
        'https://api.familysearch.org/platform/search/persons'
      ]
    );
    familysearch = { ...familysearch, lastSync: new Date().toISOString() };
    await setSetting('service_familysearch_last_sync', familysearch.lastSync);
  }

  $effect(() => {
    handleFamilySearchCallback();
    handleGeniCallback();
  });

  async function syncFamilySearchTree() {
    fsSyncProgress = { active: true, pct: 5, message: 'Loading FamilySearch tree persons...' };
    familysearch = { ...familysearch, error: '' };
    try {
      const data = await familysearchApiCall('/platform/tree/persons?count=200');
      const entries = data?.persons || data?.entries || [];
      fsSyncProgress = { active: true, pct: 35, message: `Fetched ${entries.length} external profiles. Matching local tree...` };

      const locals = await getAllPersons();
      let created = 0;
      for (let i = 0; i < entries.length; i++) {
        const ext = entries[i];
        const extId = ext?.id || ext?.personId || `fs_${i}`;
        const extGiven = ext?.names?.[0]?.nameForms?.[0]?.parts?.find((p: any) => p.type === 'http://gedcomx.org/Given')?.value || ext?.display?.name?.split(' ')[0] || '';
        const extSurname = ext?.names?.[0]?.nameForms?.[0]?.parts?.find((p: any) => p.type === 'http://gedcomx.org/Surname')?.value || ext?.display?.name?.split(' ').slice(1).join(' ') || '';
        const extBirth = ext?.display?.birthDate || '';

        let bestLocal = '';
        let bestScore = 0;
        let bestReason = '';
        for (const p of locals) {
          const m = personMatchConfidence(p.givenName, p.surname, p.birthDate, extGiven, extSurname, extBirth);
          if (m.score > bestScore) {
            bestScore = m.score;
            bestLocal = p.xref;
            bestReason = m.reason;
          }
        }

        if (bestScore >= 0.72) {
          await createImportProposal('familysearch', extId, bestLocal, ext, `FamilySearch sync candidate: ${bestReason}`, bestScore);
          created++;
        } else {
          await createImportProposal('familysearch', extId, '', ext, 'No confident local match found; review as potential new person', 0.6);
          created++;
        }

        if (i % 10 === 0) {
          fsSyncProgress = { active: true, pct: 35 + Math.round(((i + 1) / Math.max(entries.length, 1)) * 55), message: `Creating proposals ${i + 1}/${entries.length}...` };
        }
      }
      const now = new Date().toISOString();
      familysearch = { ...familysearch, lastSync: now };
      await setSetting('service_familysearch_last_sync', now);
      fsSyncProgress = { active: true, pct: 100, message: `Created ${created} FamilySearch proposals.` };
      setTimeout(() => { fsSyncProgress = { active: false, pct: 0, message: '' }; }, 2500);
    } catch (e) {
      familysearch = { ...familysearch, error: String(e) };
      fsSyncProgress = { active: false, pct: 0, message: '' };
    }
  }

  async function importFromGeni() {
    geniSyncProgress = { active: true, pct: 8, message: 'Loading managed profiles from Geni...' };
    geni = { ...geni, error: '' };
    try {
      const token = await getSetting('service_geni_token');
      if (!token) throw new Error('No Geni token found. Connect Geni first.');

      const resp = await fetch('https://www.geni.com/api/user/managed-profiles', {
        headers: { Authorization: `Bearer ${token}` }
      });
      if (!resp.ok) {
        const err = await resp.text();
        throw new Error(`Geni request failed (${resp.status}): ${err}`);
      }
      const data = await resp.json();
      const profiles = Array.isArray(data?.results) ? data.results : (Array.isArray(data) ? data : []);
      const locals = await getAllPersons();
      geniMatches = [];
      let created = 0;

      for (let i = 0; i < profiles.length; i++) {
        const profile = profiles[i];
        const extGiven = profile?.first_name || profile?.name?.split(' ')[0] || '';
        const extSurname = profile?.last_name || profile?.name?.split(' ').slice(1).join(' ') || '';
        const extBirth = profile?.birth?.date || profile?.birth_date || '';

        let best: MatchCandidate | null = null;
        for (const p of locals) {
          const m = personMatchConfidence(p.givenName, p.surname, p.birthDate, extGiven, extSurname, extBirth);
          if (m.score > (best?.confidence ?? 0)) {
            best = {
              externalId: String(profile?.id || profile?.guid || ''),
              externalName: `${extGiven} ${extSurname}`.trim(),
              localXref: p.xref,
              localName: `${p.givenName} ${p.surname}`.trim(),
              confidence: m.score,
              reason: m.reason,
              source: 'geni',
              payload: JSON.stringify(profile),
            };
          }
        }
        if (best && best.confidence >= 0.65) {
          geniMatches = [...geniMatches, best];
          await createImportProposal('geni', best.externalId, best.localXref, profile, `Geni import candidate: ${best.reason}`, best.confidence);
          created++;
        }
        if (i % 10 === 0) {
          geniSyncProgress = { active: true, pct: 25 + Math.round(((i + 1) / Math.max(profiles.length, 1)) * 70), message: `Matching profiles ${i + 1}/${profiles.length}...` };
        }
      }
      const now = new Date().toISOString();
      geni = { ...geni, lastSync: now };
      await setSetting('service_geni_last_sync', now);
      geniSyncProgress = { active: true, pct: 100, message: `Imported ${created} Geni matches as proposals.` };
      setTimeout(() => { geniSyncProgress = { active: false, pct: 0, message: '' }; }, 2500);
    } catch (e) {
      geni = { ...geni, error: String(e) };
      geniSyncProgress = { active: false, pct: 0, message: '' };
    }
  }

  async function searchWikiTree() {
    wtSearchProgress = { active: true, pct: 10, message: 'Searching WikiTree+ against local people...' };
    wikitree = { ...wikitree, error: '' };
    wikitreeMatches = [];
    try {
      const key = await getSetting('service_wikitree_app_id');
      if (!key) throw new Error('No WikiTree API key found. Connect WikiTree first.');

      const locals = (await getAllPersons()).slice(0, 25);
      for (let i = 0; i < locals.length; i++) {
        const p = locals[i];
        const body = new URLSearchParams({
          action: 'searchPerson',
          key,
          appId: key,
          FirstName: p.givenName || '',
          LastNameAtBirth: p.surname || '',
          BirthDate: p.birthDate || '',
        }).toString();

        const resp = await fetch('https://api.wikitree.com/api.php', {
          method: 'POST',
          headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
          body
        });
        if (!resp.ok) continue;
        const raw = await resp.json();
        const candidates = raw?.[0]?.matches || raw?.[0]?.people || [];
        for (const c of candidates.slice(0, 2)) {
          const wtGiven = c?.FirstName || '';
          const wtSurname = c?.LastNameAtBirth || c?.LastNameCurrent || '';
          const wtBirth = c?.BirthDate || '';
          const m = personMatchConfidence(p.givenName, p.surname, p.birthDate, wtGiven, wtSurname, wtBirth);
          if (m.score < 0.55) continue;
          const match: MatchCandidate = {
            externalId: String(c?.Id || c?.Name || ''),
            externalName: `${wtGiven} ${wtSurname}`.trim(),
            localXref: p.xref,
            localName: `${p.givenName} ${p.surname}`.trim(),
            confidence: m.score,
            reason: m.reason,
            source: 'wikitree',
            payload: JSON.stringify(c),
          };
          wikitreeMatches = [...wikitreeMatches, match];
          await createImportProposal('wikitree', match.externalId, match.localXref, c, `WikiTree search candidate: ${match.reason}`, match.confidence);
        }
        wtSearchProgress = { active: true, pct: 15 + Math.round(((i + 1) / Math.max(locals.length, 1)) * 80), message: `Searching ${i + 1}/${locals.length} local people...` };
      }
      wtSearchProgress = { active: true, pct: 100, message: `Found ${wikitreeMatches.length} WikiTree matches (saved as proposals).` };
      setTimeout(() => { wtSearchProgress = { active: false, pct: 0, message: '' }; }, 2500);
    } catch (e) {
      wikitree = { ...wikitree, error: String(e) };
      wtSearchProgress = { active: false, pct: 0, message: '' };
    }
  }
</script>

<div class="p-6 max-w-5xl mx-auto">
  <!-- Page header -->
  <div class="arch-section-header mb-8">
    <h1 class="arch-page-title">{t('nav.services')}</h1>
    <p class="arch-page-subtitle">{t('services.subtitle')}</p>
  </div>

  <!-- FamilySearch — Tier 1 -->
  <div class="arch-card mb-6">
    <div class="flex items-start justify-between gap-4 mb-4">
      <div class="flex items-center gap-3">
        <div class="w-10 h-10 rounded-lg flex items-center justify-center" style="background: var(--accent-bg, rgba(76,111,82,0.1));">
          <svg class="w-5 h-5" fill="none" stroke="currentColor" stroke-width="1.5" viewBox="0 0 24 24" style="color: var(--accent);">
            <path stroke-linecap="round" stroke-linejoin="round" d="M12 21a9.004 9.004 0 008.716-6.747M12 21a9.004 9.004 0 01-8.716-6.747M12 21c2.485 0 4.5-4.03 4.5-9S14.485 3 12 3m0 18c-2.485 0-4.5-4.03-4.5-9S9.515 3 12 3m0 0a8.997 8.997 0 017.843 4.582M12 3a8.997 8.997 0 00-7.843 4.582m15.686 0A11.953 11.953 0 0112 10.5c-2.998 0-5.74-1.1-7.843-2.918m15.686 0A8.959 8.959 0 0121 12c0 .778-.099 1.533-.284 2.253m0 0A17.919 17.919 0 0112 16.5a17.92 17.92 0 01-8.716-2.247m0 0A9.015 9.015 0 013 12c0-1.605.42-3.113 1.157-4.418" />
          </svg>
        </div>
        <div>
          <h2 class="text-base font-semibold" style="color: var(--ink); font-family: var(--font-serif);">{t('services.familysearch')}</h2>
          <p class="text-xs mt-0.5" style="color: var(--ink-muted); font-family: var(--font-sans);">The world's largest free genealogy platform, operated by The Church of Jesus Christ of Latter-day Saints. Over 13 billion records including vital records, census data, immigration records, military records, and church registers from 100+ countries. Features a collaborative shared tree with 1.4 billion connected profiles. Free REST API with OAuth2 — search historical records, read and update the shared tree, access digital images of original documents, and retrieve standardized place data. Completely free, no subscription required.</p>
        </div>
      </div>
      <div class="flex items-center gap-2">
        <span class="inline-flex items-center gap-1.5 px-2 py-1 rounded text-[10px] uppercase tracking-wider" style="font-family: var(--font-mono); {familysearch.connected ? 'background: rgba(34,139,34,0.1); color: #228b22;' : 'background: var(--parchment); color: var(--ink-muted);'}">
          <span class="w-2 h-2 rounded-full" style="background: {familysearch.connected ? '#228b22' : '#999'};"></span>
          {familysearch.connected ? 'Connected' : 'Disconnected'}
        </span>
        <span class="px-2 py-0.5 rounded text-[9px] uppercase tracking-wider font-semibold" style="background: var(--accent-bg, rgba(76,111,82,0.1)); color: var(--accent); font-family: var(--font-mono);">{t('services.tier1')}</span>
      </div>
    </div>

    {#if familysearch.connected}
      <div class="mb-4 p-3 rounded-lg" style="background: var(--parchment); border: 1px solid var(--border);">
        <div class="text-xs" style="color: var(--ink-muted); font-family: var(--font-sans);">
          Signed in as <strong style="color: var(--ink);">{familysearch.username}</strong>
          {#if familysearch.lastSync}
            <span class="ml-2">Last sync: {familysearch.lastSync}</span>
          {/if}
        </div>
      </div>
      <div class="grid grid-cols-1 md:grid-cols-4 gap-2 mb-4">
        <input class="arch-input" placeholder={t('people.givenName')} bind:value={fsSearchGiven}  aria-label={t('people.givenName')} />
        <input class="arch-input" placeholder={t('people.surname')} bind:value={fsSearchSurname}  aria-label={t('people.surname')} />
        <input class="arch-input" placeholder={t('services.birthYear')} bind:value={fsSearchYear}  aria-label={t('services.birthYear')} />
        <button class="btn-accent" onclick={searchFamilySearch} disabled={fsSearchLoading} aria-label={t('common.actions')}>
          {fsSearchLoading ? 'Searching...' : 'Search Records'}
        </button>
      </div>
      {#if fsSearchResults.length > 0}
        <div class="arch-card mb-4 divide-y arch-card-divide">
          {#each fsSearchResults as result}
            <div class="px-3 py-2 flex items-center justify-between gap-2">
              <div>
                <div class="text-xs font-semibold" style="color: var(--ink);">{result.title}</div>
                <div class="text-[11px]" style="color: var(--ink-muted);">{result.summary || result.id}</div>
              </div>
              <button class="btn-secondary text-xs" onclick={() => importFamilySearchResult(result)}>Import to Proposals</button>
            </div>
          {/each}
        </div>
      {/if}
      <div class="flex flex-wrap gap-2">
        <button class="btn-secondary" onclick={syncFamilySearchTree} aria-label={t('common.actions')}>
          <svg class="w-4 h-4 inline-block mr-1" fill="none" stroke="currentColor" stroke-width="1.5" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" d="M16.023 9.348h4.992v-.001M2.985 19.644v-4.992m0 0h4.992m-4.993 0l3.181 3.183a8.25 8.25 0 0013.803-3.7M4.031 9.865a8.25 8.25 0 0113.803-3.7l3.181 3.182" /></svg>
          {t('services.syncTree')}
        </button>
        <button class="btn-danger-outline" onclick={disconnectFamilySearch} aria-label={t('common.actions')}>{t('services.disconnect')}</button>
      </div>
      {#if fsSyncProgress.active}
        <div class="mt-3 p-3 rounded-lg" style="background: var(--parchment); border: 1px solid var(--border);">
          <div class="text-[11px] mb-1" style="color: var(--ink-muted);">{fsSyncProgress.message}</div>
          <div class="h-1 rounded-full overflow-hidden" style="background: rgba(255,255,255,0.08);">
            <div class="h-full rounded-full transition-all" style="width: {fsSyncProgress.pct}%; background: var(--accent);"></div>
          </div>
        </div>
      {/if}
    {:else}
      <div class="mb-4">
        <label for="familysearch-client-id" class="block text-xs font-medium mb-1.5" style="color: var(--ink-muted); font-family: var(--font-sans);">FamilySearch Client ID</label>
        <div class="flex gap-2">
          <input
            id="familysearch-client-id"
            type="text"
            bind:value={fsClientId}
            placeholder={t('services.registerFamilySearch')}
            class="arch-input flex-1"
           aria-label={t('services.registerFamilySearch')} />
          <button class="btn-secondary text-xs" onclick={saveFsClientId} aria-label={t('common.actions')}>{t('common.save')}</button>
        </div>
        <p class="text-[10px] mt-1" style="color: var(--ink-faint); font-family: var(--font-mono);">API: https://api.familysearch.org &middot; Register: https://developers.familysearch.org/</p>
      </div>
      <button class="btn-accent" onclick={connectFamilySearch} disabled={familysearch.loading}>
        {#if familysearch.loading}
          Connecting...
        {:else}
          <svg class="w-4 h-4 inline-block mr-1" fill="none" stroke="currentColor" stroke-width="1.5" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" d="M15.75 9V5.25A2.25 2.25 0 0013.5 3h-6a2.25 2.25 0 00-2.25 2.25v13.5A2.25 2.25 0 007.5 21h6a2.25 2.25 0 002.25-2.25V15m3 0l3-3m0 0l-3-3m3 3H9" /></svg>
          Connect with FamilySearch
        {/if}
      </button>
      {#if familysearch.error}
        <p class="text-xs mt-2" style="color: var(--danger, #c33);">{familysearch.error}</p>
      {/if}
    {/if}
  </div>

  <!-- Geni — Tier 2 -->
  <div class="arch-card mb-6">
    <div class="flex items-start justify-between gap-4 mb-4">
      <div class="flex items-center gap-3">
        <div class="w-10 h-10 rounded-lg flex items-center justify-center" style="background: rgba(59,130,246,0.1);">
          <svg class="w-5 h-5" fill="none" stroke="currentColor" stroke-width="1.5" viewBox="0 0 24 24" style="color: #3b82f6;">
            <path stroke-linecap="round" stroke-linejoin="round" d="M18 18.72a9.094 9.094 0 003.741-.479 3 3 0 00-4.682-2.72m.94 3.198l.001.031c0 .225-.012.447-.037.666A11.944 11.944 0 0112 21c-2.17 0-4.207-.576-5.963-1.584A6.062 6.062 0 016 18.719m12 0a5.971 5.971 0 00-.941-3.197m0 0A5.995 5.995 0 0012 12.75a5.995 5.995 0 00-5.058 2.772m0 0a3 3 0 00-4.681 2.72 8.986 8.986 0 003.74.477m.94-3.197a5.971 5.971 0 00-.94 3.197M15 6.75a3 3 0 11-6 0 3 3 0 016 0zm6 3a2.25 2.25 0 11-4.5 0 2.25 2.25 0 014.5 0zm-13.5 0a2.25 2.25 0 11-4.5 0 2.25 2.25 0 014.5 0z" />
          </svg>
        </div>
        <div>
          <h2 class="text-base font-semibold" style="color: var(--ink); font-family: var(--font-serif);">{t('services.geni')}</h2>
          <p class="text-xs mt-0.5" style="color: var(--ink-muted); font-family: var(--font-sans);">Full REST API with read/write access. OAuth2 authentication. Shared World Family Tree.</p>
        </div>
      </div>
      <div class="flex items-center gap-2">
        <span class="inline-flex items-center gap-1.5 px-2 py-1 rounded text-[10px] uppercase tracking-wider" style="font-family: var(--font-mono); {geni.connected ? 'background: rgba(34,139,34,0.1); color: #228b22;' : 'background: var(--parchment); color: var(--ink-muted);'}">
          <span class="w-2 h-2 rounded-full" style="background: {geni.connected ? '#228b22' : '#999'};"></span>
          {geni.connected ? 'Connected' : 'Disconnected'}
        </span>
        <span class="px-2 py-0.5 rounded text-[9px] uppercase tracking-wider font-semibold" style="background: rgba(59,130,246,0.1); color: #3b82f6; font-family: var(--font-mono);">{t('services.tier2')}</span>
      </div>
    </div>

    {#if geni.connected}
      <div class="mb-4 p-3 rounded-lg" style="background: var(--parchment); border: 1px solid var(--border);">
        <div class="text-xs" style="color: var(--ink-muted); font-family: var(--font-sans);">
          Signed in as <strong style="color: var(--ink);">{geni.username}</strong>
          {#if geni.lastSync}
            <span class="ml-2">Last sync: {geni.lastSync}</span>
          {/if}
        </div>
      </div>
      <div class="flex flex-wrap gap-2">
        <button class="btn-accent" onclick={importFromGeni}>
          <svg class="w-4 h-4 inline-block mr-1" fill="none" stroke="currentColor" stroke-width="1.5" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" d="M3 16.5v2.25A2.25 2.25 0 005.25 21h13.5A2.25 2.25 0 0021 18.75V16.5m-13.5-9L12 3m0 0l4.5 4.5M12 3v13.5" /></svg>
          Import from Geni
        </button>
        <button class="btn-danger-outline" onclick={disconnectGeni} aria-label={t('common.actions')}>{t('services.disconnect')}</button>
      </div>
      {#if geniSyncProgress.active}
        <div class="mt-3 p-3 rounded-lg" style="background: var(--parchment); border: 1px solid var(--border);">
          <div class="text-[11px] mb-1" style="color: var(--ink-muted);">{geniSyncProgress.message}</div>
          <div class="h-1 rounded-full overflow-hidden" style="background: rgba(255,255,255,0.08);">
            <div class="h-full rounded-full transition-all" style="width: {geniSyncProgress.pct}%; background: var(--accent);"></div>
          </div>
        </div>
      {/if}
      {#if geniMatches.length > 0}
        <div class="mt-3 divide-y arch-card-divide rounded-lg" style="border: 1px solid var(--border);">
          {#each geniMatches.slice(0, 6) as m}
            <div class="px-3 py-2 flex items-center justify-between gap-2">
              <div class="text-xs" style="color: var(--ink-muted);">
                <strong style="color: var(--ink);">{m.externalName}</strong> → {m.localName}
              </div>
              <span class="text-[10px]" style="color: var(--accent);">{Math.round(m.confidence * 100)}%</span>
            </div>
          {/each}
        </div>
      {/if}
    {:else}
      <div class="mb-4">
        <label for="geni-client-id" class="block text-xs font-medium mb-1.5" style="color: var(--ink-muted); font-family: var(--font-sans);">Geni App Key (Client ID)</label>
        <div class="flex gap-2">
          <input
            id="geni-client-id"
            type="text"
            bind:value={geniClientId}
            placeholder={t('services.registerGeni')}
            class="arch-input flex-1"
           aria-label={t('services.registerGeni')} />
          <button class="btn-secondary text-xs" onclick={saveGeniClientId} aria-label={t('common.actions')}>{t('common.save')}</button>
        </div>
        <p class="text-[10px] mt-1" style="color: var(--ink-faint); font-family: var(--font-mono);">API: https://www.geni.com/api/ &middot; Rate limit: 40 req/10s</p>
      </div>
      <button class="btn-accent" onclick={connectGeni} disabled={geni.loading}>
        {#if geni.loading}
          Connecting...
        {:else}
          <svg class="w-4 h-4 inline-block mr-1" fill="none" stroke="currentColor" stroke-width="1.5" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" d="M15.75 9V5.25A2.25 2.25 0 0013.5 3h-6a2.25 2.25 0 00-2.25 2.25v13.5A2.25 2.25 0 007.5 21h6a2.25 2.25 0 002.25-2.25V15m3 0l3-3m0 0l-3-3m3 3H9" /></svg>
          Connect with Geni
        {/if}
      </button>
      {#if geni.error}
        <p class="text-xs mt-2" style="color: var(--danger, #c33);">{geni.error}</p>
      {/if}
    {/if}
  </div>

  <!-- WikiTree — Tier 2 (read-only) -->
  <div class="arch-card mb-6">
    <div class="flex items-start justify-between gap-4 mb-4">
      <div class="flex items-center gap-3">
        <div class="w-10 h-10 rounded-lg flex items-center justify-center" style="background: rgba(34,139,34,0.1);">
          <svg class="w-5 h-5" fill="none" stroke="currentColor" stroke-width="1.5" viewBox="0 0 24 24" style="color: #228b22;">
            <path stroke-linecap="round" stroke-linejoin="round" d="M6.429 9.75L2.25 12l4.179 2.25m0-4.5l5.571 3 5.571-3m-11.142 0L2.25 7.5 12 2.25l9.75 5.25-4.179 2.25m0 0L21.75 12l-4.179 2.25m0 0L12 17.25 6.43 14.25m11.142 0l4.179 2.25L12 21.75l-9.75-5.25 4.179-2.25" />
          </svg>
        </div>
        <div>
          <h2 class="text-base font-semibold" style="color: var(--ink); font-family: var(--font-serif);">{t('services.wikitree')}</h2>
          <p class="text-xs mt-0.5" style="color: var(--ink-muted); font-family: var(--font-sans);">Community-driven shared tree. Read-only API with simple key authentication.</p>
        </div>
      </div>
      <div class="flex items-center gap-2">
        <span class="inline-flex items-center gap-1.5 px-2 py-1 rounded text-[10px] uppercase tracking-wider" style="font-family: var(--font-mono); {wikitree.connected ? 'background: rgba(34,139,34,0.1); color: #228b22;' : 'background: var(--parchment); color: var(--ink-muted);'}">
          <span class="w-2 h-2 rounded-full" style="background: {wikitree.connected ? '#228b22' : '#999'};"></span>
          {wikitree.connected ? 'Connected' : 'Disconnected'}
        </span>
        <span class="px-2 py-0.5 rounded text-[9px] uppercase tracking-wider font-semibold" style="background: rgba(34,139,34,0.1); color: #228b22; font-family: var(--font-mono);">{t('services.tier2')}</span>
      </div>
    </div>

    {#if wikitree.connected}
      <div class="mb-4 p-3 rounded-lg" style="background: var(--parchment); border: 1px solid var(--border);">
        <div class="text-xs" style="color: var(--ink-muted); font-family: var(--font-sans);">
          Connected as <strong style="color: var(--ink);">{wikitree.username}</strong>
        </div>
      </div>
      <div class="flex flex-wrap gap-2">
        <button class="btn-accent" onclick={searchWikiTree}>
          <svg class="w-4 h-4 inline-block mr-1" fill="none" stroke="currentColor" stroke-width="1.5" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z" /></svg>
          Search WikiTree
        </button>
        <button class="btn-danger-outline" onclick={disconnectWikiTree} aria-label={t('common.actions')}>{t('services.disconnect')}</button>
      </div>
      {#if wtSearchProgress.active}
        <div class="mt-3 p-3 rounded-lg" style="background: var(--parchment); border: 1px solid var(--border);">
          <div class="text-[11px] mb-1" style="color: var(--ink-muted);">{wtSearchProgress.message}</div>
          <div class="h-1 rounded-full overflow-hidden" style="background: rgba(255,255,255,0.08);">
            <div class="h-full rounded-full transition-all" style="width: {wtSearchProgress.pct}%; background: var(--accent);"></div>
          </div>
        </div>
      {/if}
      {#if wikitreeMatches.length > 0}
        <div class="mt-3 divide-y arch-card-divide rounded-lg" style="border: 1px solid var(--border);">
          {#each wikitreeMatches.slice(0, 6) as m}
            <div class="px-3 py-2 flex items-center justify-between gap-2">
              <div class="text-xs" style="color: var(--ink-muted);">
                <strong style="color: var(--ink);">{m.externalName}</strong> → {m.localName}
              </div>
              <span class="text-[10px]" style="color: var(--accent);">{Math.round(m.confidence * 100)}%</span>
            </div>
          {/each}
        </div>
      {/if}
    {:else}
      <div class="mb-4">
        <label for="wikitree-api-key" class="block text-xs font-medium mb-1.5" style="color: var(--ink-muted); font-family: var(--font-sans);">WikiTree API Key</label>
        <div class="flex gap-2">
          <input
            id="wikitree-api-key"
            type="text"
            bind:value={wikitreeApiKey}
            placeholder={t('services.registerWikiTree')}
            class="arch-input flex-1"
           aria-label={t('services.registerWikiTree')} />
        </div>
        <p class="text-[10px] mt-1" style="color: var(--ink-faint); font-family: var(--font-mono);">API: https://api.wikitree.com/api.php &middot; Read-only access</p>
      </div>
      <button class="btn-accent" onclick={connectWikiTree} disabled={wikitree.loading}>
        {#if wikitree.loading}
          Validating...
        {:else}
          <svg class="w-4 h-4 inline-block mr-1" fill="none" stroke="currentColor" stroke-width="1.5" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" d="M15.75 5.25a3 3 0 013 3m3 0a6 6 0 01-7.029 5.912c-.563-.097-1.159.026-1.563.43L10.5 17.25H8.25v2.25H6v2.25H2.25v-2.818c0-.597.237-1.17.659-1.591l6.499-6.499c.404-.404.527-1 .43-1.563A6 6 0 1121.75 8.25z" /></svg>
          Connect to WikiTree
        {/if}
      </button>
      {#if wikitree.error}
        <p class="text-xs mt-2" style="color: var(--danger, #c33);">{wikitree.error}</p>
      {/if}
    {/if}
  </div>

  <!-- GEDCOM Export/Import -->
  <div class="arch-card mb-6">
    <div class="flex items-start justify-between gap-4 mb-4">
      <div class="flex items-center gap-3">
        <div class="w-10 h-10 rounded-lg flex items-center justify-center" style="background: rgba(139,92,34,0.1);">
          <svg class="w-5 h-5" fill="none" stroke="currentColor" stroke-width="1.5" viewBox="0 0 24 24" style="color: #8b5c22;">
            <path stroke-linecap="round" stroke-linejoin="round" d="M19.5 14.25v-2.625a3.375 3.375 0 00-3.375-3.375h-1.5A1.125 1.125 0 0113.5 7.125v-1.5a3.375 3.375 0 00-3.375-3.375H8.25m2.25 0H5.625c-.621 0-1.125.504-1.125 1.125v17.25c0 .621.504 1.125 1.125 1.125h12.75c.621 0 1.125-.504 1.125-1.125V11.25a9 9 0 00-9-9z" />
          </svg>
        </div>
        <div>
          <h2 class="text-base font-semibold" style="color: var(--ink); font-family: var(--font-serif);">GEDCOM Export / Import</h2>
          <p class="text-xs mt-0.5" style="color: var(--ink-muted); font-family: var(--font-sans);">Export your tree data as standard GEDCOM files for use with any genealogy software.</p>
        </div>
      </div>
    </div>

    <div class="grid grid-cols-1 sm:grid-cols-2 gap-4 mb-4">
      <div class="p-4 rounded-lg" style="background: var(--parchment); border: 1px solid var(--border);">
        <h3 class="text-sm font-semibold mb-1" style="color: var(--ink); font-family: var(--font-serif);">GEDCOM 5.5.1</h3>
        <p class="text-[11px] mb-3" style="color: var(--ink-muted); font-family: var(--font-sans);">Universal interchange format. Compatible with all major genealogy applications.</p>
        <button class="btn-accent w-full" onclick={handleExportGedcom551} disabled={exportLoading}>
          {#if exportLoading}
            Exporting...
          {:else}
            <svg class="w-4 h-4 inline-block mr-1" fill="none" stroke="currentColor" stroke-width="1.5" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" d="M3 16.5v2.25A2.25 2.25 0 005.25 21h13.5A2.25 2.25 0 0021 18.75V16.5M16.5 12L12 16.5m0 0L7.5 12m4.5 4.5V3" /></svg>
            Export GEDCOM 5.5.1
          {/if}
        </button>
      </div>
      <div class="p-4 rounded-lg" style="background: var(--parchment); border: 1px solid var(--border);">
        <h3 class="text-sm font-semibold mb-1" style="color: var(--ink); font-family: var(--font-serif);">GEDCOM 7.0</h3>
        <p class="text-[11px] mb-3" style="color: var(--ink-muted); font-family: var(--font-sans);">Next-generation format with UTF-8, GEDZip media bundling, and formal extensions.</p>
        <button class="btn-secondary w-full" onclick={handleExportGedcom70} disabled={exportLoading}>
          <svg class="w-4 h-4 inline-block mr-1" fill="none" stroke="currentColor" stroke-width="1.5" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" d="M3 16.5v2.25A2.25 2.25 0 005.25 21h13.5A2.25 2.25 0 0021 18.75V16.5M16.5 12L12 16.5m0 0L7.5 12m4.5 4.5V3" /></svg>
          Export GEDCOM 7.0
        </button>
      </div>
    </div>

    {#if exportStatus}
      <p class="text-xs" style="color: {exportStatus.includes('failed') ? 'var(--danger, #c33)' : 'var(--accent)'}; font-family: var(--font-sans);">{exportStatus}</p>
    {/if}

    <div class="mt-3 p-3 rounded" style="background: var(--parchment); border-left: 3px solid var(--accent);">
      <p class="text-[11px]" style="color: var(--ink-muted); font-family: var(--font-sans);">
        <strong>Format notes:</strong> GEDCOM 5.5.1 is supported by Ancestry, FamilySearch, MyHeritage, and all desktop genealogy apps.
        GEDCOM 7.0 uses UTF-8 only, removes CONT/CONC line continuation, and adds GEDZip media bundling. Adoption is growing but not yet universal.
      </p>
    </div>
  </div>

  <!-- Info footer -->
  <div class="arch-card" style="border-left: 3px solid var(--accent);">
    <div class="flex items-start gap-3">
      <svg class="w-5 h-5 mt-0.5 shrink-0" fill="none" stroke="currentColor" stroke-width="1.5" viewBox="0 0 24 24" style="color: var(--accent);">
        <path stroke-linecap="round" stroke-linejoin="round" d="M11.25 11.25l.041-.02a.75.75 0 011.063.852l-.708 2.836a.75.75 0 001.063.853l.041-.021M21 12a9 9 0 11-18 0 9 9 0 0118 0zm-9-3.75h.008v.008H12V8.25z" />
      </svg>
      <div>
        <h3 class="text-sm font-semibold mb-1" style="color: var(--ink); font-family: var(--font-serif);">Integration Roadmap</h3>
        <p class="text-[11px]" style="color: var(--ink-muted); font-family: var(--font-sans);">
          Services like Ancestry and FindMyPast do not offer public APIs. Data exchange with those platforms is available through GEDCOM file export/import.
          MyHeritage offers a read-only API that requires approval. Support may be added in a future release.
        </p>
      </div>
    </div>
  </div>
</div>

<style>
  .arch-input {
    padding: 0.5rem 0.75rem;
    border: 1px solid var(--border);
    border-radius: 0.375rem;
    background: var(--paper);
    color: var(--ink);
    font-family: var(--font-mono);
    font-size: 0.75rem;
    transition: border-color 0.15s;
  }
  .arch-input:focus {
    outline: none;
    border-color: var(--accent);
    box-shadow: 0 0 0 2px rgba(76, 111, 82, 0.15);
  }
  .arch-input::placeholder {
    color: var(--ink-faint);
  }

  .btn-secondary {
    display: inline-flex;
    align-items: center;
    padding: 0.4rem 0.85rem;
    border: 1px solid var(--border);
    border-radius: 0.375rem;
    background: var(--paper);
    color: var(--ink);
    font-family: var(--font-sans);
    font-size: 0.75rem;
    font-weight: 500;
    cursor: pointer;
    transition: all 0.15s;
  }
  .btn-secondary:hover:not(:disabled) {
    background: var(--parchment);
    border-color: var(--ink-muted);
  }
  .btn-secondary:disabled {
    opacity: 0.5;
    cursor: not-allowed;
  }

  .btn-danger-outline {
    display: inline-flex;
    align-items: center;
    padding: 0.4rem 0.85rem;
    border: 1px solid var(--danger, #c33);
    border-radius: 0.375rem;
    background: transparent;
    color: var(--danger, #c33);
    font-family: var(--font-sans);
    font-size: 0.75rem;
    font-weight: 500;
    cursor: pointer;
    transition: all 0.15s;
  }
  .btn-danger-outline:hover {
    background: rgba(204, 51, 51, 0.05);
  }
</style>
