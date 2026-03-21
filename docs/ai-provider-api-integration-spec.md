# AI Provider API Integration Spec for Genealogy Application

**Research Date:** March 20, 2026
**Purpose:** Complete integration reference for 7 AI providers in a genealogy application context.

> **Note:** Pricing and rate limits change frequently. Verify current rates at each provider's pricing page before production deployment.

---

## Table of Contents

1. [Anthropic (Claude)](#1-anthropic-claude)
2. [OpenAI](#2-openai)
3. [Google Gemini](#3-google-gemini)
4. [OpenRouter](#4-openrouter)
5. [Groq](#5-groq)
6. [Together AI](#6-together-ai)
7. [DeepSeek (Direct)](#7-deepseek-direct)
8. [Pricing Comparison Table](#8-pricing-comparison-table)
9. [Model Recommendations by Genealogy Task](#9-model-recommendations-by-genealogy-task)
10. [System Prompt Best Practices](#10-system-prompt-best-practices)
11. [Genealogy-Specific System Prompt Template](#11-genealogy-specific-system-prompt-template)

---

## 1. Anthropic (Claude)

### Endpoint
```
POST https://api.anthropic.com/v1/messages
```

### Authentication
```
x-api-key: YOUR_ANTHROPIC_API_KEY
anthropic-version: 2023-06-01
content-type: application/json
```

Note: Uses `x-api-key` header (NOT Bearer token). The `anthropic-version` header is required.

### Models

| Model ID | Context Window | Input $/1M | Output $/1M | Vision | Streaming |
|----------|---------------|------------|-------------|--------|-----------|
| `claude-sonnet-4-20250514` | 200K (1M with beta header) | $3.00 | $15.00 | Yes | Yes |
| `claude-haiku-4-5-20251001` | 200K | $1.00 | $5.00 | Yes | Yes |
| `claude-opus-4-6` | 1M | $5.00 | $25.00 | Yes | Yes |

**Long context pricing:** Requests exceeding 200K input tokens on Sonnet are charged at $6.00 input / $22.50 output per 1M tokens. Sonnet 4.5 requires the `context-1m-2025-08-07` beta header for >200K requests (tier 4+ organizations).

### System Prompt Support
**Yes** -- first-class `system` parameter in the request body.

### Rate Limits
Organized into usage tiers that increase automatically. View current limits in the Anthropic Console. Limits include RPM (requests per minute) and TPM (tokens per minute) per model.

### Request Format
```json
{
  "model": "claude-sonnet-4-20250514",
  "max_tokens": 4096,
  "system": "You are an expert genealogist assistant specializing in historical record analysis, name standardization, and family relationship mapping.",
  "messages": [
    {
      "role": "user",
      "content": "Analyze this census record and extract family relationships: [record text]"
    }
  ],
  "stream": false
}
```

### Request with Vision
```json
{
  "model": "claude-sonnet-4-20250514",
  "max_tokens": 4096,
  "system": "You are an expert at transcribing historical genealogy documents.",
  "messages": [
    {
      "role": "user",
      "content": [
        {
          "type": "image",
          "source": {
            "type": "base64",
            "media_type": "image/jpeg",
            "data": "BASE64_ENCODED_IMAGE"
          }
        },
        {
          "type": "text",
          "text": "Transcribe this historical document. Extract all names, dates, and locations."
        }
      ]
    }
  ]
}
```

---

## 2. OpenAI

### Endpoint
```
POST https://api.openai.com/v1/chat/completions
```

### Authentication
```
Authorization: Bearer YOUR_OPENAI_API_KEY
Content-Type: application/json
```

### Models

| Model ID | Context Window | Input $/1M | Output $/1M | Vision | Streaming |
|----------|---------------|------------|-------------|--------|-----------|
| `gpt-4o` | 128K | $5.00 | $20.00 | Yes | Yes |
| `gpt-4o-mini` | 128K | $0.60 | $2.40 | Yes | Yes |
| `o3-mini` | 200K | $1.10 | $4.40 | No | Yes |

**Note:** gpt-4o has been retired from ChatGPT (Feb 2026) but API access remains unchanged. Newer models include gpt-4.1 ($2.00/$8.00, 1M context) and gpt-5.4 series. Consider gpt-4.1 as a direct successor.

### System Prompt Support
**Yes** -- uses `role: "system"` (or `role: "developer"` for reasoning models like o3-mini). Starting with o1-2024-12-17, reasoning models use "developer" messages instead of "system" messages.

### Rate Limits
Tier-based system. Limits vary by model and organization tier. Check dashboard at platform.openai.com.

### Request Format
```json
{
  "model": "gpt-4o",
  "messages": [
    {
      "role": "system",
      "content": "You are an expert genealogist assistant specializing in historical record analysis, name standardization, and family relationship mapping."
    },
    {
      "role": "user",
      "content": "Analyze this census record and extract family relationships: [record text]"
    }
  ],
  "max_tokens": 4096,
  "stream": false
}
```

### Request for o3-mini (Reasoning Model)
```json
{
  "model": "o3-mini",
  "messages": [
    {
      "role": "developer",
      "content": "You are an expert genealogist assistant. Reason step by step through genealogical evidence to establish family connections."
    },
    {
      "role": "user",
      "content": "Given these three records, determine if they refer to the same individual: [records]"
    }
  ],
  "max_tokens": 4096
}
```

### Request with Vision
```json
{
  "model": "gpt-4o",
  "messages": [
    {
      "role": "system",
      "content": "You are an expert at transcribing historical genealogy documents."
    },
    {
      "role": "user",
      "content": [
        {
          "type": "text",
          "text": "Transcribe this historical document. Extract all names, dates, and locations."
        },
        {
          "type": "image_url",
          "image_url": {
            "url": "data:image/jpeg;base64,BASE64_ENCODED_IMAGE"
          }
        }
      ]
    }
  ],
  "max_tokens": 4096
}
```

---

## 3. Google Gemini

### Endpoint
```
POST https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent?key=YOUR_API_KEY
```

Streaming endpoint:
```
POST https://generativelanguage.googleapis.com/v1beta/models/{model}:streamGenerateContent?key=YOUR_API_KEY
```

### Authentication
API key passed as query parameter `key=YOUR_API_KEY`, or via header `x-goog-api-key: YOUR_API_KEY`.

### Models

| Model ID | Context Window | Input $/1M | Output $/1M | Vision | Streaming |
|----------|---------------|------------|-------------|--------|-----------|
| `gemini-2.5-pro` | 1M | $1.25 (<=200K) / $2.50 (>200K) | $10.00 (<=200K) / $15.00 (>200K) | Yes | Yes |
| `gemini-2.5-flash` | 1M | $0.30 | $2.50 | Yes | Yes |

**Free tier:** Both models have a free tier with rate-limited access. Flash is free of charge for standard usage.

**Batch pricing (50% off):** Pro: $0.625/$5.00; Flash: $0.15/$1.25 per 1M tokens.

### System Prompt Support
**Yes** -- uses `systemInstruction` field in the request body.

### Rate Limits
Free tier has restrictive limits. Paid tier limits scale with usage. Preview models may have more restrictive limits.

### Request Format
```json
{
  "systemInstruction": {
    "parts": [
      {
        "text": "You are an expert genealogist assistant specializing in historical record analysis, name standardization, and family relationship mapping."
      }
    ]
  },
  "contents": [
    {
      "role": "user",
      "parts": [
        {
          "text": "Analyze this census record and extract family relationships: [record text]"
        }
      ]
    }
  ],
  "generationConfig": {
    "maxOutputTokens": 4096,
    "temperature": 0.7
  }
}
```

### Request with Vision
```json
{
  "systemInstruction": {
    "parts": [
      {
        "text": "You are an expert at transcribing historical genealogy documents."
      }
    ]
  },
  "contents": [
    {
      "role": "user",
      "parts": [
        {
          "inlineData": {
            "mimeType": "image/jpeg",
            "data": "BASE64_ENCODED_IMAGE"
          }
        },
        {
          "text": "Transcribe this historical document. Extract all names, dates, and locations."
        }
      ]
    }
  ]
}
```

---

## 4. OpenRouter

### Endpoint
```
POST https://openrouter.ai/api/v1/chat/completions
```

### Authentication
```
Authorization: Bearer YOUR_OPENROUTER_API_KEY
Content-Type: application/json
```

OpenAI-compatible API format. Works with OpenAI SDK by changing the base URL.

### Models

| Model ID | Context Window | Input $/1M | Output $/1M | Vision | Streaming |
|----------|---------------|------------|-------------|--------|-----------|
| `meta-llama/llama-4-maverick` | 1M | $0.15 | $0.60 | Yes | Yes |
| `deepseek/deepseek-chat-v3-0324` | 128K | $0.32 | $0.89 | No | Yes |
| `qwen/qwen-3-235b-a22b` | 131K | $0.455 | $1.82 | No | Yes |

**Note on model IDs:** OpenRouter uses `provider/model-name` format. The deepseek-chat-v3-0324 is the March 2024 snapshot; the latest is `deepseek/deepseek-v3.2` at $0.26/$0.38. Qwen3 also has a newer `qwen/qwen3-235b-a22b-2507` variant at $0.071/$0.10.

### System Prompt Support
**Yes** -- standard OpenAI-compatible `role: "system"` in messages array. Support depends on the underlying model; all listed models support system prompts.

### Rate Limits
Depends on plan (Free, Pay-as-you-go, Enterprise) and underlying provider capacity.

### Request Format
```json
{
  "model": "meta-llama/llama-4-maverick",
  "messages": [
    {
      "role": "system",
      "content": "You are an expert genealogist assistant specializing in historical record analysis, name standardization, and family relationship mapping."
    },
    {
      "role": "user",
      "content": "Analyze this census record and extract family relationships: [record text]"
    }
  ],
  "max_tokens": 4096,
  "stream": false
}
```

---

## 5. Groq

### Endpoint
```
POST https://api.groq.com/openai/v1/chat/completions
```

### Authentication
```
Authorization: Bearer YOUR_GROQ_API_KEY
Content-Type: application/json
```

OpenAI-compatible API format.

### Models

| Model ID | Context Window | Input $/1M | Output $/1M | Vision | Streaming |
|----------|---------------|------------|-------------|--------|-----------|
| `llama-3.3-70b-versatile` | 131K | $0.59 | $0.79 | No | Yes |
| `deepseek-r1-distill-llama-70b` | 131K | ~$0.59 | ~$0.79 | No | Yes |

**Important:** Groq deprecated `deepseek-r1-distill-llama-70b` on September 2, 2025 in favor of `llama-3.3-70b-versatile` or `openai/gpt-oss-120b`. Check current model availability before integrating.

**Speed advantage:** Groq's LPU inference engine provides ultra-fast inference (fastest available), making it ideal for latency-sensitive genealogy tasks like autocomplete suggestions.

### System Prompt Support
**Yes** -- standard OpenAI-compatible `role: "system"` in messages array.

### Rate Limits

| Tier | llama-3.3-70b-versatile |
|------|------------------------|
| Free | 30 RPM, 12K TPM, 100K TPD |
| Developer | ~1K RPM, ~300K TPM (with 25% cost discount) |
| Enterprise | Custom limits |

### Request Format
```json
{
  "model": "llama-3.3-70b-versatile",
  "messages": [
    {
      "role": "system",
      "content": "You are an expert genealogist assistant specializing in historical record analysis, name standardization, and family relationship mapping."
    },
    {
      "role": "user",
      "content": "Compare these two name records and determine if they likely refer to the same person: [records]"
    }
  ],
  "max_tokens": 4096,
  "stream": false
}
```

---

## 6. Together AI

### Endpoint
```
POST https://api.together.xyz/v1/chat/completions
```

### Authentication
```
Authorization: Bearer YOUR_TOGETHER_API_KEY
Content-Type: application/json
```

OpenAI-compatible API format.

### Models

| Model ID | Context Window | Input $/1M | Output $/1M | Vision | Streaming |
|----------|---------------|------------|-------------|--------|-----------|
| `meta-llama/Llama-3.3-70B-Instruct-Turbo` | 131K | $0.88 | $0.88 | No | Yes |
| `deepseek-ai/DeepSeek-V3` | 128K | $0.60 | $1.70 | No | Yes |

**Note:** The DeepSeek-V3 pricing shown is for the V3.1 version. The original V3-0324 may have different pricing. Together AI also offers free-tier variants of some models (e.g., `deepseek-ai/DeepSeek-R1-Distill-Llama-70B-free`).

### System Prompt Support
**Yes** -- standard OpenAI-compatible `role: "system"` in messages array.

### Rate Limits
Varies by plan. Serverless models have per-model rate limits. Check Together AI dashboard.

### Request Format
```json
{
  "model": "meta-llama/Llama-3.3-70B-Instruct-Turbo",
  "messages": [
    {
      "role": "system",
      "content": "You are an expert genealogist assistant specializing in historical record analysis, name standardization, and family relationship mapping."
    },
    {
      "role": "user",
      "content": "Suggest research strategies for finding the parents of [ancestor name] born circa [year] in [location]."
    }
  ],
  "max_tokens": 4096,
  "stream": false
}
```

---

## 7. DeepSeek (Direct)

### Endpoint
```
POST https://api.deepseek.com/chat/completions
```

Alternative (OpenAI-compatible):
```
POST https://api.deepseek.com/v1/chat/completions
```

### Authentication
```
Authorization: Bearer YOUR_DEEPSEEK_API_KEY
Content-Type: application/json
```

### Models

| Model ID | Context Window | Input $/1M (cache miss) | Input $/1M (cache hit) | Output $/1M | Vision | Streaming |
|----------|---------------|------------------------|----------------------|-------------|--------|-----------|
| `deepseek-chat` | 128K | $0.28 | $0.028 | $0.42 | No | Yes |
| `deepseek-reasoner` | 128K | $0.28 | $0.028 | $0.42 | No | Yes |

Both models are modes of DeepSeek-V3.2. `deepseek-chat` is the non-thinking mode; `deepseek-reasoner` is the thinking mode.

**Cache discount:** Cache hits are 90% cheaper than cache misses. DeepSeek uses hard disk caching automatically.

### System Prompt Support

| Model | System Prompt | Notes |
|-------|--------------|-------|
| `deepseek-chat` | **Yes** | Standard `role: "system"` in messages. Optimized for system prompt instruction following. |
| `deepseek-reasoner` | **NO** | Does NOT support system prompts. All instructions must go in the user message. Ignores temperature, top_p, and penalties. Only max_tokens is effective. |

### Rate Limits
Check DeepSeek platform dashboard. Pricing and limits may vary.

### Request Format (deepseek-chat)
```json
{
  "model": "deepseek-chat",
  "messages": [
    {
      "role": "system",
      "content": "You are an expert genealogist assistant specializing in historical record analysis, name standardization, and family relationship mapping."
    },
    {
      "role": "user",
      "content": "Analyze this GEDCOM record and identify data quality issues: [record]"
    }
  ],
  "max_tokens": 4096,
  "stream": false
}
```

### Request Format (deepseek-reasoner -- NO system prompt)
```json
{
  "model": "deepseek-reasoner",
  "messages": [
    {
      "role": "user",
      "content": "You are an expert genealogist assistant specializing in historical record analysis and family relationship mapping.\n\nGiven the following three records, reason step by step to determine if they refer to the same individual. Consider name variations, date ranges, and geographic proximity:\n\n[records]"
    }
  ],
  "max_tokens": 8192
}
```

**Workaround details:** Embed all system-level instructions at the beginning of the first user message. Keep instructions minimal and explicit. Avoid few-shot prompting (direct, clear instructions with explicit output requirements work better with R1-family models). The model expects strictly interleaved user/assistant messages -- no successive messages with the same role.

---

## 8. Pricing Comparison Table

All prices per 1 million tokens (USD). Sorted by input cost ascending.

| Provider | Model | Input $/1M | Output $/1M | Context | Vision |
|----------|-------|-----------|-------------|---------|--------|
| DeepSeek | deepseek-chat (cache hit) | $0.028 | $0.42 | 128K | No |
| OpenRouter | qwen/qwen3-235b-a22b-2507 | $0.071 | $0.10 | 262K | No |
| OpenRouter | meta-llama/llama-4-maverick | $0.15 | $0.60 | 1M | Yes |
| DeepSeek | deepseek-chat (cache miss) | $0.28 | $0.42 | 128K | No |
| DeepSeek | deepseek-reasoner (cache miss) | $0.28 | $0.42 | 128K | No |
| Google | gemini-2.5-flash | $0.30 | $2.50 | 1M | Yes |
| OpenRouter | deepseek/deepseek-chat-v3-0324 | $0.32 | $0.89 | 128K | No |
| OpenRouter | qwen/qwen-3-235b-a22b | $0.455 | $1.82 | 131K | No |
| Groq | llama-3.3-70b-versatile | $0.59 | $0.79 | 131K | No |
| OpenAI | gpt-4o-mini | $0.60 | $2.40 | 128K | Yes |
| Together | deepseek-ai/DeepSeek-V3 | $0.60 | $1.70 | 128K | No |
| Together | meta-llama/Llama-3.3-70B-Instruct-Turbo | $0.88 | $0.88 | 131K | No |
| Anthropic | claude-haiku-4-5-20251001 | $1.00 | $5.00 | 200K | Yes |
| OpenAI | o3-mini | $1.10 | $4.40 | 200K | No |
| Google | gemini-2.5-pro (<=200K) | $1.25 | $10.00 | 1M | Yes |
| Anthropic | claude-sonnet-4-20250514 | $3.00 | $15.00 | 200K | Yes |
| OpenAI | gpt-4o | $5.00 | $20.00 | 128K | Yes |
| Anthropic | claude-opus-4-6 | $5.00 | $25.00 | 1M | Yes |

### Cost Optimization Strategies

1. **Prompt caching:** DeepSeek (90% discount), Anthropic (prompt caching available), Google (context caching available)
2. **Batch processing:** Google Gemini offers 50% off batch pricing; OpenAI has batch API at 50% discount
3. **Free tiers:** Google Gemini Flash has free tier; Groq has free tier (30 RPM); OpenRouter has free models
4. **Tiered approach:** Use cheap models (DeepSeek, Groq) for high-volume tasks, expensive models (Claude Opus, Gemini Pro) for complex analysis

---

## 9. Model Recommendations by Genealogy Task

### Document Transcription (OCR/handwriting from scanned records)

| Rank | Model | Why |
|------|-------|-----|
| 1 | `claude-opus-4-6` | Best vision + largest context for multi-page documents |
| 2 | `gemini-2.5-pro` | Strong vision, 1M context, good at structured extraction |
| 3 | `gpt-4o` | Strong vision capabilities, well-tested for OCR tasks |
| 4 | `gemini-2.5-flash` | Cost-effective vision with 1M context |

**Key requirement:** Vision/image support is mandatory. Only Anthropic, OpenAI (gpt-4o), Google Gemini, and Llama 4 Maverick (via OpenRouter) support vision.

### Research Suggestions (finding records, suggesting next steps)

| Rank | Model | Why |
|------|-------|-----|
| 1 | `claude-sonnet-4-20250514` | Excellent instruction following, strong reasoning |
| 2 | `gemini-2.5-flash` | Cost-effective, 1M context for large family trees |
| 3 | `deepseek-chat` | Extremely cheap, good instruction following |
| 4 | `llama-3.3-70b-versatile` (Groq) | Ultra-fast for real-time suggestions |

### Record Analysis (extracting structured data from text records)

| Rank | Model | Why |
|------|-------|-----|
| 1 | `gemini-2.5-pro` | Best for structured extraction, strong reasoning |
| 2 | `claude-sonnet-4-20250514` | Precise instruction following, XML tag handling |
| 3 | `deepseek-chat` | Very cheap, good at structured tasks |
| 4 | `gpt-4o-mini` | Cost-effective for batch processing |

### Name Matching (fuzzy matching, phonetic variations, cross-cultural names)

| Rank | Model | Why |
|------|-------|-----|
| 1 | `o3-mini` | Reasoning model excels at logical comparison tasks |
| 2 | `deepseek-reasoner` | Step-by-step reasoning at very low cost |
| 3 | `claude-haiku-4-5-20251001` | Fast, cheap, good at classification tasks |
| 4 | `llama-3.3-70b-versatile` (Groq) | Ultra-fast for real-time matching |

**Note on name matching:** For high-volume name matching, consider using `rapidfuzz` (already in your stack) for initial fuzzy matching, and only escalating ambiguous cases to LLM-based analysis. This hybrid approach dramatically reduces cost.

---

## 10. System Prompt Best Practices

### Anthropic (Claude) -- from docs.anthropic.com

**Key principles:**
- **Be clear and direct.** Claude responds well to explicit instructions. Think of it as a brilliant new employee who lacks context on your norms.
- **Give Claude a role** in the system prompt to focus behavior: `"You are a helpful genealogy research assistant specializing in..."`
- **Use XML tags** to structure complex prompts: `<instructions>`, `<context>`, `<documents>`, `<examples>`
- **Long documents at the top.** Place GEDCOM data and records near the top of the prompt, above queries and instructions. Queries at the end improve quality by up to 30%.
- **Provide 3-5 examples** wrapped in `<example>` tags for consistent output formatting.
- **Ground responses in quotes.** For document analysis, ask Claude to quote relevant parts before answering.
- Claude 4.6 is more concise and direct than previous models. If you want verbose output, explicitly request it.
- Claude 4.6 is more responsive to system prompts than previous models -- dial back aggressive language like "CRITICAL" and "MUST."

**Source:** [Claude 4 Best Practices](https://platform.claude.com/docs/en/docs/build-with-claude/prompt-engineering/claude-4-best-practices)

### OpenAI -- from platform.openai.com

**Key principles:**
- Use `role: "developer"` (not "system") for reasoning models (o3-mini).
- **Keep prompts simple and direct.** Models excel at brief, clear instructions.
- **Use delimiters** (markdown, XML tags, section titles) to separate distinct input parts.
- **Combine markdown + XML tags** to communicate hierarchy and logical boundaries.
- **Pin model versions** in production (e.g., `gpt-4o-2024-08-06`) for consistent behavior.
- Use the `tools` field for tool definitions rather than injecting them into prompts (2% performance improvement).
- Developer messages define rules and business logic (like a function definition); user messages provide inputs (like arguments).

**Source:** [OpenAI Prompt Engineering Guide](https://developers.openai.com/api/docs/guides/prompt-engineering)

### Google Gemini -- from ai.google.dev

**Key principles:**
- Use `systemInstruction` field (separate from messages) for persistent instructions.
- **Define agent persona** with name, role, and preferred characteristics.
- **Put conversational rules in execution order** in the system instruction.
- **Place data before instructions.** For large documents, put GEDCOM data first, then ask questions.
- **Anchor reasoning** with "Based on the information above..." phrasing.
- Leverage the 1M context window -- provide all relevant data upfront rather than summarizing.
- **Use context caching** for repeated analysis of the same GEDCOM file (saves costs significantly).

**Source:** [Gemini Long Context Guide](https://ai.google.dev/gemini-api/docs/long-context)

### DeepSeek -- Handling Models Without System Prompts

**deepseek-chat:** Standard system prompt support, optimized for instruction following.

**deepseek-reasoner (R1 family) workaround:**
1. Move ALL instructions into the first `user` message.
2. Keep instructions minimal and explicit.
3. Use direct, clear instructions with explicit output requirements.
4. Avoid few-shot prompting (less effective with R1).
5. Do NOT use temperature, top_p, or penalty parameters (ignored by R1).
6. Only `max_tokens` is effective for controlling output length.
7. Ensure strictly interleaved user/assistant messages (no successive same-role messages).
8. Prefix user message with persona and task instructions, then provide the actual query.

**Example pattern:**
```
[INSTRUCTIONS AND PERSONA]
\n\n
[ACTUAL QUERY WITH DATA]
```

**Source:** [DeepSeek Reasoning Model Guide](https://api-docs.deepseek.com/guides/reasoning_model)

---

## 11. Genealogy-Specific System Prompt Template

No provider offers genealogy-specific fine-tuning. The following system prompt template is designed for genealogy tasks across all providers:

```
You are an expert genealogist and historical records analyst with deep knowledge of:
- GEDCOM 5.5.1 format and data standards
- Historical naming conventions across cultures (patronymics, anglicization, Ellis Island name changes)
- Date formats across eras and regions (Julian/Gregorian calendar transition, Quaker dates)
- Historical place name changes and jurisdictional boundaries
- Census record interpretation (US, UK, Canadian federal censuses)
- Vital records analysis (birth, marriage, death certificates)
- Immigration and naturalization records
- Church and parish records

When analyzing records:
1. Always note the source, date, and location of each record
2. Flag inconsistencies between records (date conflicts, name spelling variations)
3. Use standardized date format: DD MMM YYYY (e.g., 15 Mar 1847)
4. Distinguish between facts (directly stated in records) and inferences (deduced from evidence)
5. Rate confidence levels: Certain (direct evidence), Probable (strong indirect evidence), Possible (circumstantial), Speculative (limited evidence)
6. Consider phonetic variations when matching names (Soundex, NYSIIS, Metaphone patterns)
7. Account for common transcription errors in historical records (misread letters, transposed digits)

Output structured data in the requested format. When uncertain, state your uncertainty explicitly rather than guessing.
```

### Adapting for deepseek-reasoner (no system prompt)

Prepend the above template directly to the user message:

```
You are an expert genealogist and historical records analyst with deep knowledge of:
[...full template above...]

---

Now, analyze the following records and determine if they refer to the same individual:
[actual query and data]
```

---

## Summary of System Prompt Support

| Provider | Model | System Prompt | Method |
|----------|-------|--------------|--------|
| Anthropic | All Claude models | Yes | `system` field in request body |
| OpenAI | gpt-4o, gpt-4o-mini | Yes | `role: "system"` in messages |
| OpenAI | o3-mini | Yes | `role: "developer"` in messages |
| Google | All Gemini models | Yes | `systemInstruction` field in request body |
| OpenRouter | All models | Yes | `role: "system"` in messages (OpenAI-compatible) |
| Groq | All models | Yes | `role: "system"` in messages (OpenAI-compatible) |
| Together AI | All models | Yes | `role: "system"` in messages (OpenAI-compatible) |
| DeepSeek | deepseek-chat | Yes | `role: "system"` in messages |
| DeepSeek | deepseek-reasoner | **NO** | Embed instructions in user message |

---

## Provider Feature Matrix

| Feature | Anthropic | OpenAI | Google | OpenRouter | Groq | Together | DeepSeek |
|---------|-----------|--------|--------|------------|------|----------|----------|
| OpenAI-Compatible | No | Yes | No | Yes | Yes | Yes | Yes |
| Vision Support | Yes | gpt-4o only | Yes | Model-dependent | Limited | Model-dependent | No |
| Streaming | Yes | Yes | Yes | Yes | Yes | Yes | Yes |
| Batch API | Yes (50% off) | Yes (50% off) | Yes (50% off) | No | No | No | No |
| Prompt Caching | Yes | No | Yes | No | Yes (free) | No | Yes (90% off) |
| Function/Tool Calling | Yes | Yes | Yes | Model-dependent | Model-dependent | Model-dependent | Yes (chat only) |
| Structured Output | Yes | Yes | Yes | Model-dependent | No | No | Yes (chat only) |
