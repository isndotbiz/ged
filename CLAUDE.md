# GedFix — GEDCOM Genealogy Tool

**Purpose:** Clean, deduplicate, and fix GEDCOM genealogy files. Monorepo with Tauri+SvelteKit desktop/mobile app, Chrome extension, and Python CLI.
**Web:** `gedfix.isn.biz` (Cloudflare Pages)

## Stack
- **Desktop/Mobile:** Tauri 2 + SvelteKit 5 + TypeScript + Tailwind CSS
- **Mobile:** Tauri mobile plugin (Android/iOS)
- **Rust backend:** Image processing, file I/O, Chrome extension bridge
- **Shared types:** `@gedfix/core` (packages/core — TypeScript)
- **Python CLI:** Python 3.11, pytest, `tools/cli/gedfix/`
- **DB:** `@tauri-apps/plugin-sql` (SQLite desktop), `sql.js` (web/browser mode)
- **Face detection:** `@vladmandic/face-api`

## Monorepo Structure
```
apps/desktop/         Tauri 2 + SvelteKit — all platforms
  src/                SvelteKit frontend (30+ routes)
  src-tauri/          Rust backend
  apps/chrome-ext/    Chrome Extension MV3
packages/core/        @gedfix/core — shared TypeScript types, schema, SQL
tools/cli/            Python CLI (gedfix package + pytest)
  gedfix/             Python library
  tests/              pytest suite
  scripts/            Processing and integrity scripts
archive/swift/        Reference only (not active)
archive/kmp/          Reference only (not active)
```

## Platform Status
macOS/Windows/Linux: Complete | Web (gedfix.isn.biz): Live | Android: APK building | iOS: CI wired, needs device test + Team ID | Chrome Ext: In progress

## Development Commands
```bash
# Run from apps/desktop/
pnpm dev                    # macOS desktop
pnpm dev:web                # browser mode (sql.js)
pnpm build                  # desktop build
pnpm build:web              # web build (Cloudflare Pages deploy)
pnpm -C packages/core build # rebuild @gedfix/core (do this first after core changes)
pnpm test                   # run tests
pnpm lint                   # ESLint
pnpm format                 # Prettier

# Mobile (from apps/desktop/)
npm run tauri android dev   # requires Android Studio + NDK 27
npm run tauri ios dev       # requires Xcode + macOS
```

## Python CLI
```bash
pip install -e tools/cli
gedfix --help
gedfix scan input.ged --report out/report.json
gedfix fix input.ged --out cleaned.ged --level standard|aggressive|ultra|comprehensive
gedfix dedupe input.ged --out deduped.ged --threshold 95.0
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
tools/cli/scripts/master_gedcom_workflow.sh data/master/roots_master.ged
```

## Key Notes
- Always rebuild `@gedfix/core` first when changing shared types
- Large data files gitignored: `.txt`, `.large`, `maps/`, `media/`, `out/`, `images/`
- Place geocoding cache: `apps/desktop/fact/places_cache.csv`
- Web mode uses `sql.js` (WASM SQLite) — no native plugin; conditional imports required
- iOS blocked on Apple Team ID — do not attempt App Store submission without it
- Spirit Atlas content previously in `images/` — now at `~/Desktop/extras/spirit-atlas-images/`
