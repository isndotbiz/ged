---
task: Media management + AI research + face auto-crop system
slug: 20260324-media-ai-face-system
effort: deep
phase: complete
progress: 0/42
mode: interactive
started: 2026-03-24T00:00:00Z
updated: 2026-03-24T00:05:00Z
---

## Context

Upgrade GedFix media management with auto face crop approval workflow, rich AI research system with expert genealogy prompts per provider/mode, image deduplication, and media cleanup tools. Fix 654 broken renamed files (DONE). Enhance the existing media page and AI chat.

### Risks
- Face auto-crop without detection just does center-top crop — may miss faces
- AI API calls from Tauri webview may be blocked by CORS
- Image hash dedup may false-positive on similar but different photos
- Bulk operations on 1000+ media items may freeze UI

## Criteria

### Auto Face Crop + Approval (media page)
- [ ] ISC-1: "Auto Crop All" button generates face crops for all uncropped photos
- [ ] ISC-2: Batch processing shows progress bar during auto-crop
- [ ] ISC-3: Approval grid shows each auto-cropped face with original side-by-side
- [ ] ISC-4: Approve button saves crop as PRIMARY for that person
- [ ] ISC-5: Reject button skips to next photo without saving
- [ ] ISC-6: Skip button moves to next without action
- [ ] ISC-7: Counter shows "N of M" progress through approval queue
- [ ] ISC-8: Only processes people who don't already have a PRIMARY photo

### AI Research System (ai-chat page)
- [ ] ISC-9: Research mode selector with 5+ modes (General, Record Analysis, Brick Wall, Source Evaluation, DNA, Name Variants)
- [ ] ISC-10: Each mode has a rich expert system prompt (100+ words, specific methodology)
- [ ] ISC-11: System prompt includes genealogy standards (GPS, Evidence Analysis)
- [ ] ISC-12: Conversation history sent to API (not just single message)
- [ ] ISC-13: Person context can be attached to chat (selected person's data sent as context)
- [ ] ISC-14: Provider/model shown in chat header with quick switch
- [ ] ISC-15: Chat supports markdown rendering in responses

### AI System Prompts (quality)
- [ ] ISC-16: General prompt covers genealogy research methodology
- [ ] ISC-17: Record Analysis prompt covers document transcription and extraction
- [ ] ISC-18: Brick Wall prompt covers breaking through research dead ends
- [ ] ISC-19: Source Evaluation prompt covers GPS proof standards
- [ ] ISC-20: DNA prompt covers genetic genealogy interpretation
- [ ] ISC-21: Name Variants prompt covers historical naming patterns

### Image Deduplication
- [ ] ISC-22: "Find Duplicates" button scans media for duplicates
- [ ] ISC-23: Duplicate detection uses file size + partial content hash
- [ ] ISC-24: Duplicate groups shown in review UI
- [ ] ISC-25: User can select which duplicate to keep
- [ ] ISC-26: Removing duplicate deletes DB entry (not file)

### Media Cleanup
- [ ] ISC-27: "Find Missing" button detects DB entries with missing files
- [ ] ISC-28: Missing file count shown with list
- [ ] ISC-29: "Clean Up" removes orphaned DB entries
- [ ] ISC-30: "Fix Extensions" fixes known broken patterns

### Enhanced Media Browser
- [ ] ISC-31: Surname group view shows media organized by surname folders
- [ ] ISC-32: Full-size image viewer overlay on click
- [ ] ISC-33: Image viewer shows person name, dates, file info
- [ ] ISC-34: Category counts shown on filter tabs

### Verification
- [ ] ISC-35: npm run check zero new errors
- [ ] ISC-36: npm run build passes
- [ ] ISC-37: cargo check passes
- [ ] ISC-38: 654 broken extension files fixed (DONE)
- [ ] ISC-39: Auto-crop uses existing Rust generate_thumbnail command
- [ ] ISC-40: AI prompts are genealogy-specific and methodologically sound
- [ ] ISC-41: Dedup doesn't delete actual files, only DB entries
- [ ] ISC-42: All new UI matches archival design system

## Decisions
- Use top-center crop heuristic for face auto-crop (no ML face detection needed)
- Use file size + first 4KB hash for dedup (fast, no full file reads)
- AI prompts based on Board for Certification of Genealogists standards
- Markdown rendering via simple regex replacement (no new dependencies)

## Verification
