# GedFix — Genealogy Platform

Cross-platform genealogy app + data tooling.

## Apps

| App | Stack | Status |
|-----|-------|--------|
| macOS | Tauri 2 + SvelteKit 5 | ✅ Complete |
| Windows | Tauri 2 + SvelteKit 5 | ✅ Complete |
| Linux | Tauri 2 + SvelteKit 5 | ✅ Complete |
| Android | Tauri 2 mobile | 🔧 In progress |
| iOS | Tauri 2 mobile | 🔧 In progress |
| Web | SvelteKit 5 (VITE_WEB=true) | ✅ Live at gedfix.isn.biz |
| Chrome Extension | MV3 + side panel | 🔧 In progress |

## Structure

```
apps/
  desktop/        # Tauri + SvelteKit — mac/win/linux/android/iOS
    apps/
      chrome-ext/ # Chrome extension (bridges to desktop via port 19876)
packages/
  core/           # @gedfix/core — shared TypeScript types, schema, queries
tools/
  cli/            # Python CLI — GEDCOM cleaning, deduplication, validation
archive/
  swift/          # Swift/SwiftUI macOS experiment (reference)
  kmp/            # Kotlin Multiplatform experiment (reference)
data/             # Genealogy data files (local, mostly gitignored)
docs/             # Methodology and processing documentation
```

## Development

```bash
# Desktop app (macOS)
pnpm dev

# Web app
pnpm dev:web

# Python CLI
pip install -e tools/cli
gedfix --help

# Chrome extension
cd apps/desktop/apps/chrome-ext && npm run build
```

## Release

Push a tag `app-v*` to trigger the release workflow. Builds macOS, Windows, Linux, Android, iOS, and web simultaneously.
