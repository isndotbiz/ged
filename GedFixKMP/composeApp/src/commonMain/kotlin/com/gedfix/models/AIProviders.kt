package com.gedfix.models

/**
 * AI model definition for a specific model offered by a provider.
 */
data class AIModel(
    val id: String,
    val displayName: String,
    val description: String,
    val contextWindow: Int
)

/**
 * Supported AI providers with their connection details, model catalogs, and genealogy capabilities.
 * Each provider has a unique authentication scheme, API format, and set of strengths.
 */
enum class AIProvider(
    val displayName: String,
    val endpoint: String,
    val authHeaderName: String,
    val authPrefix: String,
    val supportsSystemPrompt: Boolean,
    val supportsVision: Boolean,
    val defaultModel: String,
    val availableModels: List<AIModel>,
    val description: String,
    val capabilities: List<String>
) {
    ANTHROPIC(
        displayName = "Anthropic (Claude)",
        endpoint = "https://api.anthropic.com/v1/messages",
        authHeaderName = "x-api-key",
        authPrefix = "",
        supportsSystemPrompt = true,
        supportsVision = true,
        defaultModel = "claude-sonnet-4-20250514",
        availableModels = listOf(
            AIModel("claude-sonnet-4-20250514", "Claude Sonnet 4", "Fast, smart, vision support", 200_000),
            AIModel("claude-haiku-4-5-20251001", "Claude Haiku 4.5", "Fastest, cheapest, good for simple tasks", 200_000),
            AIModel("claude-opus-4-6", "Claude Opus 4.6", "Most capable, best reasoning", 1_000_000),
        ),
        description = "Best overall for genealogy research. Excellent reasoning, document analysis, and source evaluation.",
        capabilities = listOf(
            "Document transcription (handwriting + print)",
            "Source credibility evaluation",
            "Relationship inference from narratives",
            "Research suggestions with reasoning",
            "Contradiction detection across sources",
            "Census/vital record data extraction",
            "Multi-language genealogy records",
            "Proof portfolio generation"
        )
    ),
    OPENAI(
        displayName = "OpenAI",
        endpoint = "https://api.openai.com/v1/chat/completions",
        authHeaderName = "Authorization",
        authPrefix = "Bearer ",
        supportsSystemPrompt = true,
        supportsVision = true,
        defaultModel = "gpt-4o",
        availableModels = listOf(
            AIModel("gpt-4o", "GPT-4o", "Fast multimodal, good at structured extraction", 128_000),
            AIModel("gpt-4o-mini", "GPT-4o Mini", "Cheapest, good for simple tasks", 128_000),
            AIModel("o3-mini", "o3-mini", "Advanced reasoning for complex problems", 200_000),
        ),
        description = "Strong at structured data extraction and image analysis. Good for census record parsing.",
        capabilities = listOf(
            "Census image data extraction",
            "Structured record parsing",
            "Name variant detection",
            "Date interpretation",
            "Photo analysis and description",
            "Obituary summarization"
        )
    ),
    GEMINI(
        displayName = "Google Gemini",
        endpoint = "https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent",
        authHeaderName = "x-goog-api-key",
        authPrefix = "",
        supportsSystemPrompt = true,
        supportsVision = true,
        defaultModel = "gemini-2.5-flash",
        availableModels = listOf(
            AIModel("gemini-2.5-pro", "Gemini 2.5 Pro", "Most capable, 1M context", 1_000_000),
            AIModel("gemini-2.5-flash", "Gemini 2.5 Flash", "Fast and cheap", 1_000_000),
        ),
        description = "Largest context window. Best for analyzing entire trees or large document sets at once.",
        capabilities = listOf(
            "Analyze entire GEDCOM files in one pass",
            "Cross-reference large document sets",
            "Multi-language translation",
            "Photo enhancement descriptions",
            "Timeline analysis across generations"
        )
    ),
    OPENROUTER(
        displayName = "OpenRouter",
        endpoint = "https://openrouter.ai/api/v1/chat/completions",
        authHeaderName = "Authorization",
        authPrefix = "Bearer ",
        supportsSystemPrompt = true,
        supportsVision = false,
        defaultModel = "meta-llama/llama-4-maverick",
        availableModels = listOf(
            AIModel("meta-llama/llama-4-maverick", "Llama 4 Maverick", "Open source, fast", 128_000),
            AIModel("deepseek/deepseek-chat-v3-0324", "DeepSeek V3", "Strong reasoning, very cheap", 128_000),
            AIModel("qwen/qwen-3-235b-a22b", "Qwen 3 235B", "Large, multilingual", 128_000),
            AIModel("anthropic/claude-sonnet-4", "Claude via OpenRouter", "Anthropic via OpenRouter", 200_000),
        ),
        description = "Access to 200+ models through one API. Good for experimentation and cost optimization.",
        capabilities = listOf(
            "Access to many model providers",
            "Cost optimization via model selection",
            "Fallback if primary provider is down",
            "Open source model access"
        )
    ),
    GROQ(
        displayName = "Groq",
        endpoint = "https://api.groq.com/openai/v1/chat/completions",
        authHeaderName = "Authorization",
        authPrefix = "Bearer ",
        supportsSystemPrompt = true,
        supportsVision = false,
        defaultModel = "llama-3.3-70b-versatile",
        availableModels = listOf(
            AIModel("llama-3.3-70b-versatile", "Llama 3.3 70B", "Fastest inference, free tier", 128_000),
            AIModel("deepseek-r1-distill-llama-70b", "DeepSeek R1 Distill", "Reasoning model, very fast", 128_000),
        ),
        description = "Fastest AI inference. Best for quick lookups, name matching, and simple queries where speed matters.",
        capabilities = listOf(
            "Instant name variant suggestions",
            "Quick date interpretation",
            "Fast batch processing",
            "Free tier available"
        )
    ),
    TOGETHER(
        displayName = "Together AI",
        endpoint = "https://api.together.xyz/v1/chat/completions",
        authHeaderName = "Authorization",
        authPrefix = "Bearer ",
        supportsSystemPrompt = true,
        supportsVision = false,
        defaultModel = "meta-llama/Llama-3.3-70B-Instruct-Turbo",
        availableModels = listOf(
            AIModel("meta-llama/Llama-3.3-70B-Instruct-Turbo", "Llama 3.3 70B Turbo", "Fast open source", 128_000),
            AIModel("deepseek-ai/DeepSeek-V3", "DeepSeek V3", "Strong reasoning", 128_000),
        ),
        description = "Open source models with good speed. Good alternative to Groq for batch processing.",
        capabilities = listOf(
            "Batch name normalization",
            "Place standardization",
            "Date format conversion",
            "Open source models"
        )
    ),
    DEEPSEEK(
        displayName = "DeepSeek (Direct)",
        endpoint = "https://api.deepseek.com/v1/chat/completions",
        authHeaderName = "Authorization",
        authPrefix = "Bearer ",
        supportsSystemPrompt = false,
        supportsVision = false,
        defaultModel = "deepseek-chat",
        availableModels = listOf(
            AIModel("deepseek-chat", "DeepSeek Chat V3", "General chat, supports system prompt", 128_000),
            AIModel("deepseek-reasoner", "DeepSeek Reasoner (R1)", "Advanced reasoning, NO system prompt", 128_000),
        ),
        description = "Cheapest AI provider. DeepSeek Reasoner excels at complex genealogical logic puzzles but does NOT support system prompts.",
        capabilities = listOf(
            "Complex relationship puzzles",
            "DNA relationship calculations",
            "Logic-heavy research questions",
            "Extremely low cost"
        )
    );

    companion object {
        fun fromName(name: String): AIProvider? = entries.firstOrNull { it.name == name }
    }
}

/**
 * Represents a single message in an AI chat conversation.
 */
data class AIChatMessage(
    val id: String,
    val provider: String,
    val model: String,
    val role: String,       // "user" or "assistant"
    val content: String,
    val personXref: String = "",
    val timestamp: String = ""
)

/**
 * Response wrapper from an AI provider call.
 */
data class AIResponse(
    val content: String,
    val model: String,
    val provider: AIProvider,
    val error: String? = null
)
