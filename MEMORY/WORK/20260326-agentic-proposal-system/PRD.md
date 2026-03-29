---
task: Implement agentic proposal system + rules engine + research agents
slug: 20260326-agentic-proposal-system
effort: deep
phase: complete
progress: 0/40
mode: interactive
started: 2026-03-26T00:00:00Z
updated: 2026-03-26T00:05:00Z
---

## Context

Implement the council-approved architecture: proposal + change_log + quality_rule tables in SQLite, a research agent engine that uses configured AI providers to autonomously find information and create proposals, a proposals review page for approval/rejection, and a rules engine that prevents the tree from getting worse.

## Criteria

### Schema (db.ts)
- [ ] ISC-1: proposal table with typed per-field columns (entity_type, entity_id, field_name, old_value, new_value)
- [ ] ISC-2: proposal has confidence, reasoning, evidence_source, agent_run_id
- [ ] ISC-3: proposal has status CHECK constraint (pending/approved/rejected/superseded)
- [ ] ISC-4: change_log table with append-only audit trail (proposal_id, actor, old_value, new_value, undone_at)
- [ ] ISC-5: quality_rule table with name, description, rule_type, condition, is_active
- [ ] ISC-6: agent_run table tracking each research session (id, provider, model, person_xref, status, started_at, completed_at)
- [ ] ISC-7: Indexes on proposal(entity_id), proposal(status), change_log(proposal_id), agent_run(person_xref)
- [ ] ISC-8: Default quality rules seeded on first run

### Types (types.ts)
- [ ] ISC-9: Proposal interface with all fields
- [ ] ISC-10: ChangeLogEntry interface
- [ ] ISC-11: QualityRule interface
- [ ] ISC-12: AgentRun interface

### DB Functions (db.ts)
- [ ] ISC-13: insertProposal, getProposals (by status/entity/agent_run)
- [ ] ISC-14: approveProposal — applies change to canonical table + writes change_log
- [ ] ISC-15: rejectProposal — sets status to rejected
- [ ] ISC-16: undoChange — restores old_value from change_log, sets undone_at
- [ ] ISC-17: validateProposal — checks against quality_rules, returns pass/fail with reasons
- [ ] ISC-18: getAgentRuns, insertAgentRun, updateAgentRun
- [ ] ISC-19: getQualityRules, toggleRule, insertRule

### Research Agent Engine (lib/research-agent.ts)
- [ ] ISC-20: runResearchAgent(personXref) — orchestrates a research session
- [ ] ISC-21: Agent reads person data + events + family context
- [ ] ISC-22: Agent calls configured AI provider with genealogy research prompt
- [ ] ISC-23: AI response parsed into structured proposals (field changes)
- [ ] ISC-24: Each proposal validated against quality rules before saving
- [ ] ISC-25: Agent run tracked with start/end/status
- [ ] ISC-26: Progress callback for UI updates

### Proposals Page (routes/proposals/+page.svelte)
- [ ] ISC-27: Shows all pending proposals grouped by agent run
- [ ] ISC-28: Each proposal shows old_value vs new_value diff
- [ ] ISC-29: Confidence score displayed with color coding
- [ ] ISC-30: Evidence source shown with reasoning
- [ ] ISC-31: Approve/Reject buttons per proposal
- [ ] ISC-32: Bulk approve/reject for entire agent run
- [ ] ISC-33: Undo button on approved changes (via change_log)
- [ ] ISC-34: Quality rule violations shown as warnings

### Rules Management
- [ ] ISC-35: Default rules: no deleting fields with citations, no confidence < 0.5 without evidence, no future dates, birth before death
- [ ] ISC-36: Rules visible in settings or proposals page
- [ ] ISC-37: Rules can be toggled on/off

### Navigation + Integration
- [ ] ISC-38: "Proposals" link in sidebar under AI section with pending count badge
- [ ] ISC-39: "Research This Person" button accessible from people page or AI chat

### Verification
- [ ] ISC-40: npm run build passes

## Decisions
- Per-field proposals as council agreed
- Quality rules are code-based checks, not SQL expressions (simpler, safer)
- Research agent uses a structured prompt that returns JSON proposals
- Proposals page grouped by agent_run for batch review
