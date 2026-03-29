# Canonical Stack Policy

**Updated:** 2026-03-05
**Scope:** Repository-wide policy for RAG/orchestrator/data-plane integrations.

## Canonical Runtime Targets

- **Canonical data plane:** TrueNAS Apps (Docker) for PostgreSQL + pgvector + RAG API.
- **Compute workers:** xeon/win/macbook may run crawlers, embedding workers, and orchestration.
- **Non-canonical local instances:** permitted for development only; they must not be treated as production source-of-truth.

## Security Baseline (Required)

- `/v1/search`, `/v1/ingest`, and `/v1/chat/stream` require authentication (`x-api-key` and/or access gateway controls).
- Never publish open/anonymous search in production.
- Never commit raw secrets; use `op://` references and runtime injection.
- Redact sensitive infrastructure identifiers in shared docs.

## Data Governance

- `.jsonl` is canonical for ingestion/source artifacts.
- `.llm.txt` is optional convenience output only.
- Cross-machine collection state must be reconciled before declaring production truth.

## Evidence Requirements

- Record health, collection counts, and embedding dimensions for canonical services.
- Track rate limits and request audit logging for exposed endpoints.
- Keep machine-by-machine verification notes in the central Asystem state docs.
