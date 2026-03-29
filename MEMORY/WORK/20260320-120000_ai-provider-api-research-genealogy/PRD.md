---
task: Research AI provider APIs for genealogy app integration
slug: 20260320-120000_ai-provider-api-research-genealogy
effort: extended
phase: complete
progress: 18/18
mode: interactive
started: 2026-03-20T12:00:00-05:00
updated: 2026-03-20T12:05:00-05:00
---

## Context

User needs a complete API integration spec for 7 AI providers (Anthropic, OpenAI, Google Gemini, OpenRouter, Groq, Together AI, DeepSeek direct) for use in a genealogy application. Each provider needs endpoint URLs, auth methods, request formats, system prompt support, model recommendations per genealogy task, pricing, vision support, streaming support, and best practices. The deliverable is a comprehensive reference document with exact JSON request formats and a pricing comparison table.

### Risks
- Some models (gpt-4o, o3-mini) may be deprecated/superseded but still API-accessible
- DeepSeek reasoner lacks system prompt support requiring workaround documentation
- Pricing changes frequently; document notes date of research
- OpenRouter pricing varies by underlying provider

## Criteria

- [x] ISC-1: Anthropic API endpoint and auth headers documented
- [x] ISC-2: OpenAI API endpoint and auth headers documented
- [x] ISC-3: Google Gemini API endpoint and auth headers documented
- [x] ISC-4: DeepSeek API endpoint and auth headers documented
- [x] ISC-5: OpenRouter API endpoint and auth headers documented
- [x] ISC-6: Groq API endpoint and auth headers documented
- [x] ISC-7: Together AI API endpoint and auth headers documented
- [x] ISC-8: System prompt support documented for all seven providers
- [x] ISC-9: DeepSeek reasoner workaround for missing system prompt documented
- [x] ISC-10: Context window sizes listed for every model
- [x] ISC-11: Pricing per million tokens documented for all models
- [x] ISC-12: Vision and image support status per provider documented
- [x] ISC-13: Streaming support status per provider documented
- [x] ISC-14: Exact JSON request format shown for each provider
- [x] ISC-15: Best model recommendation per genealogy task type provided
- [x] ISC-16: System prompt best practices from official docs summarized
- [x] ISC-17: Pricing comparison table included
- [x] ISC-18: Rate limits documented per provider
- [x] ISC-A-1: Anti: No unverified or speculative pricing claims

## Verification

- ISC-1 through ISC-7: Each provider section has endpoint URL and auth headers with exact header names
- ISC-8: Summary table at end shows system prompt support for all 9 model entries across 7 providers
- ISC-9: Dedicated section with 8-step workaround and example JSON for deepseek-reasoner
- ISC-10: Every model table includes context window column
- ISC-11: Pricing comparison table with 18 rows sorted by input cost
- ISC-12: Vision column in every model table plus feature matrix
- ISC-13: Streaming column in every model table plus feature matrix
- ISC-14: JSON request examples for each provider including vision variants
- ISC-15: Four genealogy task tables (transcription, research, analysis, name matching) with ranked recommendations
- ISC-16: Four provider-specific best practices sections citing official documentation
- ISC-17: Comprehensive pricing comparison table in Section 8
- ISC-18: Rate limit info for each provider (detailed for Groq, referenced for others)
- ISC-A-1: All pricing sourced from official docs and third-party aggregators; research date noted
