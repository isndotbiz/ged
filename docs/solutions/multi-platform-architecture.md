---
title: GedFix Multi-Platform Architecture
date: 2026-04-06
tags: [architecture, tauri, sveltekit, monorepo, cross-platform, chrome-extension]
problem: Single-platform desktop app needed to run on 7 platforms simultaneously
solution: pnpm monorepo with shared core package, Tauri 2 for desktop+mobile, SvelteKit static for web, Manifest V3 for Chrome
---

# Multi-Platform Architecture

## Problem
GedFix was a single Tauri 2 desktop app. Needed: macOS, Windows, Linux, Android, iOS, web, and Chrome extension — all from one codebase.

## Solution

### Monorepo Structure
```
ged/
├── packages/core/          # @gedfix/core — pure TS, no platform deps
│   └── src/
│       ├── types.ts        # All interfaces
│       ├── platform.ts     # PlatformDatabase + PlatformAdapter interfaces
│       ├── schema.ts       # createSchema(db) — all DDL
│       ├── queries.ts      # 127 query functions taking db as first param
│       └── dna-calculator.ts
├── gedfix-app/             # Tauri + SvelteKit app
│   ├── src/lib/db.ts       # Thin wrapper binding platform adapter to core queries
│   └── apps/chrome-ext/    # Chrome extension with esbuild
└── pnpm-workspace.yaml
```

### Platform Abstraction
```typescript
interface PlatformDatabase {
  select<T>(query: string, bindValues?: unknown[]): Promise<T>;
  execute(query: string, bindValues?: unknown[]): Promise<{ rowsAffected: number; lastInsertId?: number }>;
}
```
- Desktop: `@tauri-apps/plugin-sql` (SQLite)
- Web: `sql.js` in IndexedDB
- Chrome ext: `sql.js` in localStorage

### Chrome Extension ↔ Desktop Bridge
Tauri runs `tiny_http` on `127.0.0.1:19876`. Extension checks `/ping` every 30s, sends extracted records to `/import`. Falls back to local-only mode.

### Key Decisions
1. **Keep db.ts as wrapper** — routes don't change imports, just re-exports bound functions
2. **Classification runs after import, not on init** — was causing 2-3s startup delay
3. **Light theme default** — `[data-theme="dark"]` CSS selector for dark mode
4. **Inter font** — replaced Archivo Black + IBM Plex Sans for modern minimal aesthetic

## CI/CD
- `ci.yml` — Python tests + frontend check/test on every push
- `release.yml` — Multi-platform desktop + Android + web on tag push
- `deploy-web.yml` — Cloudflare Pages on main push
- `build-chrome-ext.yml` — Extension zip artifact on main push
