# Genealogy Service API & Data Standards Integration Research

**Date:** 2026-03-29
**Researcher:** Ava Sterling
**Purpose:** Actionable integration guide for building a desktop/web/mobile genealogy app

---

## Executive Summary

FamilySearch is the clear integration-first target: free, well-documented REST API with OAuth2, individual record read/write, and GEDCOM X support. WikiTree and Geni offer viable secondary integrations. Ancestry and FindMyPast are effectively walled gardens. MyHeritage's API is read-only and approval-gated. Geneanet and RootsFinder have no public developer APIs.

**Strategic recommendation:** Build on FamilySearch first, add WikiTree and Geni for breadth, support GEDCOM 5.5.1 as the universal interchange format while preparing for 7.0 adoption.

---

## Service-by-Service API Analysis

### 1. FamilySearch (familysearch.org)

| Category | Details |
|----------|---------|
| **API Availability** | Yes - full public REST API, free for all developers |
| **Developer Portal** | https://developers.familysearch.org/ |
| **Registration** | Free at developer portal; register app to get client ID |
| **Pricing** | Completely free (nonprofit organization) |
| **Authentication** | OAuth 2.0 with four grant types: Authorization Code (web/desktop/mobile), Unauthenticated Session (limited), Client Credentials (service accounts, restricted) |
| **Token Expiry** | 24 hours after issue, or 60 minutes of inactivity |
| **Data Sync** | Full individual record read/write via REST endpoints. Persons, relationships, sources, memories, places, search, match |
| **GEDCOM Support** | Upload GEDCOM to Pedigree Resource File (up to 100MB). Export available as GEDCOM 7.0 via partner apps (up to 8 generations). No direct GEDCOM-via-API endpoint |
| **GEDCOM X** | Native format for API responses. JSON and XML representations. Closely mirrors FamilySearch Family Tree internal data model |
| **Rate Limits** | Not publicly documented; subject to fair use. Throttling reported by developers at high volumes |
| **SDKs** | JavaScript SDK on GitHub. Community libraries in various languages |
| **Key Endpoints** | Person, Relationship, Search, Match, Ancestry (pedigree), Descendancy, Sources, Memories, Places |
| **Integration Tier** | **Tier 1 - Primary Target** |

**Strategic Notes:**
- The only major service offering free, full read/write API access
- GEDCOM X is the wire format; plan to support it natively
- FamilySearch certifies partner apps for compatibility - worth pursuing certification
- The shared "World Tree" model means writes affect the global tree (not private trees)

---

### 2. Ancestry (ancestry.com)

| Category | Details |
|----------|---------|
| **API Availability** | No public API. No developer program. Undocumented internal APIs exist |
| **Developer Portal** | None |
| **Registration** | N/A |
| **Pricing** | N/A |
| **Authentication** | N/A (no API) |
| **Data Sync** | No programmatic access. Family Tree Maker has a proprietary sync protocol (TreeSync) but it is not publicly documented or available |
| **GEDCOM Support** | GEDCOM import/export via web UI and Family Tree Maker desktop app only. No API-based GEDCOM operations |
| **Rate Limits** | N/A |
| **Integration Tier** | **Tier 4 - No Integration Possible** |

**Strategic Notes:**
- Ancestry is the largest genealogy platform but operates as a completely closed ecosystem
- An undocumented AncestryDNA API exists (Python wrapper on GitHub: `MattW224/ancestryDnaWrapper`) but is unsupported, could break at any time, and is DNA-only
- Ancestry promised a public API years ago but never delivered
- The only viable "integration" is GEDCOM file import/export through the web UI
- Tools like `cdhorn/ancestry-tools` work with exported GEDCOM files, not live API data

---

### 3. MyHeritage (myheritage.com)

| Category | Details |
|----------|---------|
| **API Availability** | Yes - Family Graph API (REST, returns JSON) |
| **Developer Portal** | https://www.familygraph.com/documentation |
| **Registration** | Requires application and approval by MyHeritage. Must request an encrypted access key |
| **Pricing** | Free after approval (details not public) |
| **Authentication** | OAuth 2.0 |
| **Data Sync** | **Read-only** as of last documentation. Write support (add/edit/delete) was described as "coming soon" for years but has not materialized publicly |
| **GEDCOM Support** | GEDCOM import/export via web UI and Family Tree Builder desktop app. No GEDCOM operations via API |
| **Rate Limits** | Not publicly documented |
| **SDKs** | PHP SDK, iOS SDK, Android SDK (for login and Family Graph API) |
| **Integration Tier** | **Tier 3 - Limited (Read-Only, Approval Required)** |

**Strategic Notes:**
- The Family Graph API was launched in 2011 and has not seen significant public updates
- Read-only limitation severely constrains integration value
- Approval process adds friction and uncertainty
- MyHeritage is strong in DNA matching and Smart Matching - these features are not exposed via API
- Consider this a "nice to have" rather than a primary integration target

---

### 4. FindMyPast (findmypast.com)

| Category | Details |
|----------|---------|
| **API Availability** | Limited - Hints API only (for record matching) |
| **Developer Portal** | http://findmypast.github.io/public_docs/hints-api/ |
| **Registration** | Must contact FindMyPast directly for API key |
| **Pricing** | Not publicly documented |
| **Authentication** | API key in request headers |
| **Data Sync** | Hints API only: submit person data, receive matching record hints. Can update hint status. No tree read/write |
| **GEDCOM Support** | GEDCOM import via web UI. No GEDCOM operations via API |
| **Rate Limits** | Not publicly documented |
| **Integration Tier** | **Tier 3 - Limited (Hints Only)** |

**Strategic Notes:**
- The Hints API is useful for record discovery but not for tree synchronization
- Documentation is described as "meager" by reviewers
- FindMyPast is strong in UK/Irish records - the Hints API is the only programmatic way to leverage this
- Could be valuable as a hints/suggestions integration even without full tree sync

---

### 5. Geni (geni.com)

| Category | Details |
|----------|---------|
| **API Availability** | Yes - full REST API (JSON and XML) |
| **Developer Portal** | https://www.geni.com/platform/developer/help |
| **Registration** | Register at https://sandbox.geni.com/platform/developer/apps (sandbox first, then production) |
| **Pricing** | Free |
| **Authentication** | OAuth 2.0 |
| **Data Sync** | Read and write access to profiles, relationships, and tree data |
| **GEDCOM Support** | GEDCOM import via web UI and via API (GitHub: `geni/geni-gedcom` importer). Import limit: first 5 generations of ancestors plus siblings initially, then expands after merge review. Focus profile must be born after 1800 |
| **Rate Limits** | 40 requests per 10 seconds (default for unapproved apps). Higher limits available after app review/approval |
| **Sandbox** | Full sandbox environment at sandbox.geni.com for development |
| **Integration Tier** | **Tier 2 - Strong Integration** |

**Strategic Notes:**
- Geni uses a single shared "World Family Tree" model (like FamilySearch)
- The sandbox environment is excellent for development
- Rate limits are restrictive for unapproved apps but reasonable after approval
- Owned by MyHeritage since 2012, but API remains independent
- GEDCOM import via API is possible through their open-source importer tool
- Good secondary integration target after FamilySearch

---

### 6. WikiTree (wikitree.com)

| Category | Details |
|----------|---------|
| **API Availability** | Yes - public API (HTTP GET/POST, returns JSON) |
| **Developer Portal** | https://www.wikitree.com/wiki/Help:API_Documentation |
| **GitHub** | https://github.com/wikitree/wikitree-api |
| **Registration** | Join WikiTree Apps Project. No formal API key required - authentication is user-based |
| **Pricing** | Free (WikiTree is free and community-driven) |
| **Authentication** | Multi-step user authentication flow: user authenticates on WikiTree.com, returns to app with token. Not standard OAuth2 |
| **Data Sync** | Read-only via API. Profile data, relationships, ancestors, descendants. Write operations require user to be a "Wiki Genealogist" who signed the Honor Code |
| **GEDCOM Support** | GEDCOM import via GEDCOMpare tool (profile-by-profile matching, not bulk import). Export as GEDCOM 5.5 with UTF-8. No GEDCOM operations via API |
| **Rate Limits** | Not formally documented but community-enforced fair use |
| **Integration Tier** | **Tier 2 - Good (Read-Focused)** |

**Strategic Notes:**
- WikiTree is a single shared tree with strong community governance
- The API is read-only but well-documented with GitHub examples
- Python wrapper available: `djhenderson/pywikitree`
- The non-standard auth flow requires custom implementation (not a standard OAuth2 library)
- GEDCOMpare's profile-by-profile approach is intentional - prevents mass duplication
- Good for pulling data and showing WikiTree connections; limited for pushing data back

---

### 7. Geneanet (geneanet.org)

| Category | Details |
|----------|---------|
| **API Availability** | No public API for tree/genealogy data |
| **Developer Portal** | None |
| **Data Sync** | No programmatic access |
| **GEDCOM Support** | GEDCOM import/export via web UI only |
| **Integration Tier** | **Tier 4 - No Integration Possible** |

**Strategic Notes:**
- Geneanet has explicitly stated they do not plan to open search APIs to external developers
- Strong in French/European genealogy data
- Only integration path is manual GEDCOM file exchange
- Has a surname origins API endpoint but it is limited to name lookups, not tree data

---

### 8. RootsFinder (rootsfinder.com)

| Category | Details |
|----------|---------|
| **API Availability** | No public API |
| **Developer Portal** | None |
| **Data Sync** | No programmatic access. Integrates with FamilySearch via their own FamilySearch API usage |
| **GEDCOM Support** | GEDCOM import via web UI. Uses FamilySearch import for tree data |
| **Integration Tier** | **Tier 4 - No Integration Possible** |

**Strategic Notes:**
- Founded by Dallan Quass (former FamilySearch CTO) - deeply integrated with FamilySearch
- Pulls hints from FamilySearch, FindMyPast, WikiTree, Geni, and others
- No developer API means no direct integration
- Their Chrome Web Clipper extension shows the integration approach they favor (browser-based)
- If you integrate with FamilySearch API, your users can already push/pull data that RootsFinder also accesses

---

## Comparison Matrix

| Service | Public API | Auth | Read | Write | GEDCOM via API | Rate Limits | Free | Integration Tier |
|---------|-----------|------|------|-------|---------------|-------------|------|-----------------|
| **FamilySearch** | REST | OAuth2 | Yes | Yes | Upload only | Undocumented | Yes | Tier 1 |
| **Ancestry** | None | N/A | No | No | No | N/A | N/A | Tier 4 |
| **MyHeritage** | REST | OAuth2 | Yes | No | No | Undocumented | Yes* | Tier 3 |
| **FindMyPast** | REST (hints) | API Key | Hints | Status | No | Undocumented | Unknown | Tier 3 |
| **Geni** | REST | OAuth2 | Yes | Yes | Import tool | 40/10s | Yes | Tier 2 |
| **WikiTree** | HTTP/JSON | Custom token | Yes | No | No | Fair use | Yes | Tier 2 |
| **Geneanet** | None | N/A | No | No | No | N/A | N/A | Tier 4 |
| **RootsFinder** | None | N/A | No | No | No | N/A | N/A | Tier 4 |

*Requires approval

---

## Data Standards Analysis

### GEDCOM 5.5.1 vs 7.0

| Aspect | GEDCOM 5.5.1 | GEDCOM 7.0 |
|--------|-------------|------------|
| **Encoding** | ANSEL, ASCII, or UTF-8 | UTF-8 only |
| **Line Continuation** | CONT and CONC tags | Removed (no length limits) |
| **Notes** | NOTE_RECORD (ambiguous payload) | SHARED_NOTE_RECORD (explicit) |
| **Relationships** | RELA (free text) | ROLE (enumerated values) |
| **Date Phrases** | Inline in DateValue | Moved to PHRASE substructure |
| **Multimedia** | FILE references | GEDZip bundling (associated .zip) |
| **Negative Assertions** | Not supported | Supported (e.g., "never married") |
| **Extensions** | Unofficial _CUSTOM tags | Formal extension mechanism with URIs |
| **Backward Compatible** | N/A | No - files are not interchangeable |
| **Adoption** | Universal (industry standard) | Growing but still minority |
| **Specification** | Static (2019 was last update) | Active development (v7.0.18 current) |

**Recommendation: Support both.** GEDCOM 5.5.1 remains the universal interchange format. GEDCOM 7.0 is the future but adoption is incomplete. Your gedfix tool already handles 5.5.1; adding 7.0 support positions you ahead of the market.

### GEDCOM X

| Aspect | Details |
|--------|---------|
| **Format** | JSON and XML representations |
| **Creator** | FamilySearch (LDS Church) |
| **Use Case** | API wire format for bulk data transfer between applications |
| **vs GEDCOM** | Similar expressive power. GEDCOM = file exchange. GEDCOM X = application-to-application communication |
| **Adoption** | Primarily FamilySearch ecosystem. Not widely adopted outside it |
| **Key Features** | Source descriptions, evidence references, contributor metadata, media bundling, conclusions model |
| **Specification** | https://github.com/FamilySearch/gedcomx |

**Recommendation:** Support GEDCOM X for FamilySearch API integration. It is the native wire format for their API and maps cleanly to their data model. Do not prioritize it as a general interchange format.

### Other Open Standards

| Standard | Format | Notes |
|----------|--------|-------|
| **Gramps XML** | XML (.gramps, gzip compressed) | Lossless format for Gramps desktop app. Supports all data that GEDCOM cannot. Free format. Good for Gramps interop but niche adoption |
| **FamilyML** | XML | Human-readable, based on GEDCOM + GedML. Abandoned |
| **GenXML** | XML | By CoSoft for Cognatio app. Abandoned |
| **GeniML** | XML | By IGENIE. Presented at NGS GenTech 2004. Abandoned |
| **FHISO** | Standards body | Family History Information Standards Organisation. Working on next-gen standards but progress is slow |

**Recommendation:** Only Gramps XML is worth considering beyond GEDCOM, and only if you want Gramps desktop app interop. The other XML alternatives are abandoned. FHISO is worth monitoring but has no shipping standard.

---

## Strategic Integration Roadmap

### Phase 1: Foundation (Months 1-3)
1. **GEDCOM 5.5.1 read/write** - Already built in gedfix
2. **FamilySearch API integration** - OAuth2 flow, person/relationship CRUD, search/match
3. **GEDCOM X parser** - For FamilySearch API responses
4. **GEDCOM 7.0 read support** - Future-proofing

### Phase 2: Breadth (Months 4-6)
1. **WikiTree API** - Read-only profile/ancestry data pull
2. **Geni API** - Read/write profile sync with sandbox testing
3. **GEDCOM 7.0 write support** - Full round-trip capability

### Phase 3: Hints & Discovery (Months 7-9)
1. **FindMyPast Hints API** - Record matching suggestions
2. **MyHeritage Family Graph** - Apply for API access, implement read-only data pull
3. **Cross-service deduplication** - Identify same person across multiple services

### Phase 4: Advanced (Months 10-12)
1. **Gramps XML import/export** - For desktop interop
2. **GEDCOM 7.0 GEDZip** - Media bundling
3. **FamilySearch certification** - Official partner status

---

## Second-Order Effects to Consider

1. **Shared tree conflict**: FamilySearch, WikiTree, and Geni all use shared "world tree" models. Writes to one affect everyone. Your app needs a conflict resolution strategy and possibly a local-first architecture that syncs selectively.

2. **Authentication complexity**: Four different auth mechanisms (OAuth2 x3, custom token x1, API key x1). Abstract this behind a unified auth service.

3. **Data model mismatch**: Each service models genealogy data slightly differently. Build an internal canonical model (closest to GEDCOM X) and map each service to/from it.

4. **Rate limit management**: Geni's 40/10s limit and FamilySearch's undocumented limits mean you need request queuing, retry logic, and backoff strategies.

5. **Privacy and consent**: Some services (WikiTree) have honor codes about data sharing. Your app should surface these policies to users before syncing data between services.

6. **Ancestry's absence**: The largest platform having no API means users will want GEDCOM file import/export as a first-class feature, not an afterthought. This is your bridge to the Ancestry ecosystem.

---

## Sources

- [FamilySearch Developer Portal](https://developers.familysearch.org/)
- [FamilySearch Authentication](https://developers.familysearch.org/main/docs/authentication)
- [FamilySearch API Reference](https://developers.familysearch.org/main/reference/api-reference-guide)
- [FamilySearch GEDCOM X](https://developers.familysearch.org/main/docs/gedcom-x)
- [Ancestry Support - API Question](https://support.ancestry.com/s/question/0D51500001jn5C4CAI/does-ancestrycom-offer-an-api-for-developers)
- [MyHeritage Family Graph API](https://www.familygraph.com/documentation)
- [FindMyPast Hints API](http://findmypast.github.io/public_docs/hints-api/)
- [Geni API Developer Help](https://www.geni.com/platform/developer/help)
- [Geni API Project](https://www.geni.com/projects/The-Geni-API/1124)
- [WikiTree API Documentation](https://www.wikitree.com/wiki/Help:API_Documentation)
- [WikiTree API GitHub](https://github.com/wikitree/wikitree-api)
- [Geneanet API Page](https://en.geneanet.org/genealogy/api/API)
- [RootsFinder on Devpost](https://devpost.com/software/rootsfinder)
- [GEDCOM 7.0 Specification](https://gedcom.io/specifications/FamilySearchGEDCOMv7.html)
- [GEDCOM 5.5.1 to 7.0 Migration Guide](https://gedcom.io/migrate/)
- [GEDCOM X on FamilySearch Innovate](https://www.familysearch.org/innovate/gedcom-x)
- [Genealogy APIs Overview (Tamura Jones)](https://www.tamurajones.net/GenealogyAPIs.xhtml)
- [GEDCOM Alternatives (Tamura Jones)](https://www.tamurajones.net/GEDCOMAlternatives.xhtml)
- [Gramps and GEDCOM](https://www.gramps-project.org/wiki/index.php/Gramps_and_GEDCOM)
