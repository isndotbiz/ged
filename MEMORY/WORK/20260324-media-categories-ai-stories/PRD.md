---
task: Expand media categories and build AI story generator
slug: 20260324-media-categories-ai-stories
effort: deep
phase: complete
progress: 26/26
mode: interactive
started: 2026-03-24T00:00:00-07:00
updated: 2026-03-24T00:10:00-07:00
---

## Context

Two workstreams: (1) Expand media categories with hierarchical Photo/Document subcategories. (2) Build new AI Story Generator page for biographical narratives with cost estimation, batch mode, and progress tracking. Research shows OpenRouter Maverick ($0.19/120 stories) and Groq free tier are best value; Claude Haiku for quality.

## Criteria

### Media Categories Expansion
- [ ] ISC-1: Photos split into People, Single, Married, Family/Group subcategories
- [ ] ISC-2: Documents split into Ship Manifests, Census, Marriage Docs, Wills, Histories
- [ ] ISC-3: Category hierarchy renders as grouped tabs
- [ ] ISC-4: Categorize function uses filename/title heuristics for all new types
- [ ] ISC-5: Category counts display next to labels

### AI Story Generator — New Route
- [ ] ISC-6: New route /stories exists with sidebar nav entry
- [ ] ISC-7: Story mode selector: Single Person vs Family/Multi-Gen
- [ ] ISC-8: Person/family selector from database
- [ ] ISC-9: Provider selector using existing AI settings infrastructure
- [ ] ISC-10: Cost estimate shown before generation based on provider pricing
- [ ] ISC-11: Generate button triggers API call with genealogy narrative prompt
- [ ] ISC-12: Progress bar during generation
- [ ] ISC-13: Generated story displays with markdown rendering
- [ ] ISC-14: Story saved to database for later viewing
- [ ] ISC-15: Source citations included in generated narrative
- [ ] ISC-16: Historical context enrichment in system prompt
- [ ] ISC-17: Batch mode: select multiple people, show total cost estimate
- [ ] ISC-18: Batch progress bar with per-person tracking
- [ ] ISC-19: Time estimate shown based on provider speed
- [ ] ISC-20: Stories list view showing previously generated stories
- [ ] ISC-21: Archival design tokens used throughout

### Database Support
- [ ] ISC-22: Stories table created in database
- [ ] ISC-23: Story CRUD functions in db.ts

### Navigation
- [ ] ISC-24: Stories nav item added to sidebar under AI section
- [ ] ISC-25: Stories icon is book/scroll themed

### Build
- [ ] ISC-26: npm run build passes

## Decisions

- Use existing AI provider infrastructure from ai-chat (provider selection, API keys, endpoints)
- Store stories in SQLite via stories table (id, personXref, familyXref, storyType, title, content, provider, model, createdAt)
- Cost calculation based on research: token counts × provider pricing table
- Prompt template includes person data + events + historical context instructions

## Verification
