# GedFix — Monorepo

## 1Password Connect — Credential Access
All credentials via 1Password Connect. No service accounts, no desktop auth, no plaintext .env secrets.
- `OP_CONNECT_HOST` — set in global CLAUDE.md (Helsinki primary 100.65.226.125:8100)
- `OP_CONNECT_TOKEN` — set in global CLAUDE.md (inherited automatically)
- Vaults: `Research`, `TrueNAS Infrastructure`

## Monorepo Structure

```
apps/desktop/       Tauri 2 + SvelteKit 5 — mac/win/linux/android/iOS
  src/              SvelteKit frontend (30+ routes)
  src-tauri/        Rust backend (image processing, file I/O, Chrome ext bridge)
  apps/chrome-ext/  Chrome Extension MV3 — scrapes Ancestry/FamilySearch/etc
packages/core/      @gedfix/core — shared TypeScript types, schema, SQL queries
tools/cli/          Python CLI — GEDCOM cleaning, deduplication, validation
  gedfix/           Python library
  tests/            pytest suite
  scripts/          Processing and integrity scripts
archive/swift/      Swift/SwiftUI macOS experiment (reference, not active)
archive/kmp/        Kotlin Multiplatform experiment (reference, not active)
data/               Genealogy data files (mostly gitignored)
docs/               Methodology documentation
```

## Platform Status

| Platform | Path | Status |
|----------|------|--------|
| macOS | apps/desktop/ | ✅ Complete |
| Windows | apps/desktop/ | ✅ Complete |
| Linux | apps/desktop/ | ✅ Complete |
| Web | apps/desktop/ (VITE_WEB=true) | ✅ Live at gedfix.isn.biz |
| Android | apps/desktop/ (Tauri mobile) | 🔧 APK building |
| iOS | apps/desktop/ (Tauri mobile) | 🔧 CI wired, needs device test |
| Chrome Ext | apps/desktop/apps/chrome-ext/ | 🔧 In progress |

## Desktop App Development

```bash
pnpm dev                    # macOS desktop dev
pnpm dev:web                # web dev (browser, sql.js)
pnpm build                  # desktop build
pnpm build:web              # web build (Cloudflare Pages)
pnpm -C packages/core build # rebuild @gedfix/core
```

## Mobile Development

```bash
# From apps/desktop/
npm run tauri android dev   # Android (needs Android Studio + NDK 27)
npm run tauri ios dev       # iOS (needs Xcode + macOS)
```

## Python CLI

```bash
pip install -e tools/cli
gedfix --help

gedfix scan input.ged --report out/report.json
gedfix check input.ged --level standard|aggressive|ultra
gedfix fix input.ged --out cleaned.ged --level standard|aggressive|ultra|comprehensive
gedfix dedupe input.ged --out deduped.ged --threshold 95.0
gedfix review input.ged --issues-file session.json
```

Fix levels (`tools/cli/gedfix/rules.yaml`):

| Level | merge_duplicates | fuzzy_threshold | fix_places |
|-------|-----------------|-----------------|------------|
| standard | no | 96 | no |
| aggressive | yes | 92 | yes |
| ultra | yes | 88 | yes |
| comprehensive | yes | 90 | yes |

## Key Scripts

```bash
tools/cli/scripts/verify_data_integrity.sh original.ged processed.ged
tools/cli/scripts/rollback_to_backup.sh <backup_file>
tools/cli/scripts/master_gedcom_workflow.sh data/master/roots_master.ged
```

## Notes

- Large data files excluded from git: `.txt`, `.large`, `maps/`, `media/`
- Output files in `out/` excluded from git
- Place geocoding cache: `fact/places_cache.csv`
- `images/` removed from repo — Spirit Atlas content saved to ~/Desktop/extras/spirit-atlas-images/
