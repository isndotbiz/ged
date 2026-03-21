package com.gedfix.services

import com.gedfix.db.DatabaseRepository
import com.gedfix.models.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

/**
 * Core AI service that sends chat requests to any supported provider.
 * Handles the different API formats for Anthropic, OpenAI-compatible, Gemini, and DeepSeek.
 * API keys are loaded from the database via the settings/aiApiKeys table.
 */
class AIService(private val db: DatabaseRepository) {

    /**
     * Send a chat message to the specified AI provider and return the response.
     * Automatically selects the correct API format based on the provider.
     */
    suspend fun chat(
        provider: AIProvider,
        apiKey: String,
        model: String,
        systemPrompt: String,
        userMessage: String
    ): AIResponse {
        if (apiKey.isBlank()) {
            return AIResponse(
                content = "",
                model = model,
                provider = provider,
                error = "No API key configured for ${provider.displayName}"
            )
        }

        return try {
            val responseBody = when (provider) {
                AIProvider.ANTHROPIC -> sendAnthropicRequest(provider, apiKey, model, systemPrompt, userMessage)
                AIProvider.GEMINI -> sendGeminiRequest(provider, apiKey, model, systemPrompt, userMessage)
                AIProvider.DEEPSEEK -> sendDeepSeekRequest(provider, apiKey, model, systemPrompt, userMessage)
                else -> sendOpenAICompatibleRequest(provider, apiKey, model, systemPrompt, userMessage)
            }

            val content = when (provider) {
                AIProvider.ANTHROPIC -> parseAnthropicResponse(responseBody)
                AIProvider.GEMINI -> parseGeminiResponse(responseBody)
                else -> parseOpenAIResponse(responseBody)
            }

            AIResponse(content = content, model = model, provider = provider)
        } catch (e: Exception) {
            AIResponse(
                content = "",
                model = model,
                provider = provider,
                error = "${provider.displayName} error: ${e.message}"
            )
        }
    }

    /**
     * Send a simple test message to verify the API key works.
     */
    suspend fun testConnection(provider: AIProvider, apiKey: String, model: String): AIResponse {
        return chat(
            provider = provider,
            apiKey = apiKey,
            model = model,
            systemPrompt = "You are a helpful assistant. Respond with exactly: Connection successful.",
            userMessage = "Hello, please confirm the connection is working."
        )
    }

    // -- Anthropic Messages API --

    private suspend fun sendAnthropicRequest(
        provider: AIProvider,
        apiKey: String,
        model: String,
        systemPrompt: String,
        userMessage: String
    ): String {
        val body = buildString {
            append("{")
            append("\"model\":${jsonString(model)},")
            append("\"max_tokens\":4096,")
            append("\"system\":${jsonString(systemPrompt)},")
            append("\"messages\":[{\"role\":\"user\",\"content\":${jsonString(userMessage)}}]")
            append("}")
        }

        val headers = mapOf(
            provider.authHeaderName to "${provider.authPrefix}$apiKey",
            "Content-Type" to "application/json",
            "anthropic-version" to "2023-06-01"
        )

        return httpPost(provider.endpoint, headers, body)
    }

    private fun parseAnthropicResponse(json: String): String {
        // Extract content[0].text from Anthropic response
        val textStart = json.indexOf("\"text\"")
        if (textStart == -1) {
            // Check for error
            val errorMsg = extractJsonStringValue(json, "message")
            if (errorMsg != null) throw RuntimeException(errorMsg)
            throw RuntimeException("Unexpected Anthropic response format")
        }
        return extractJsonStringValue(json.substring(textStart), "text")
            ?: throw RuntimeException("Could not parse Anthropic response text")
    }

    // -- Gemini API --

    private suspend fun sendGeminiRequest(
        provider: AIProvider,
        apiKey: String,
        model: String,
        systemPrompt: String,
        userMessage: String
    ): String {
        val endpoint = provider.endpoint.replace("{model}", model)

        val body = buildString {
            append("{")
            append("\"system_instruction\":{\"parts\":[{\"text\":${jsonString(systemPrompt)}}]},")
            append("\"contents\":[{\"parts\":[{\"text\":${jsonString(userMessage)}}]}]")
            append("}")
        }

        val headers = mapOf(
            provider.authHeaderName to apiKey,
            "Content-Type" to "application/json"
        )

        return httpPost(endpoint, headers, body)
    }

    private fun parseGeminiResponse(json: String): String {
        // Extract candidates[0].content.parts[0].text
        val textStart = json.indexOf("\"text\"")
        if (textStart == -1) {
            val errorMsg = extractJsonStringValue(json, "message")
            if (errorMsg != null) throw RuntimeException(errorMsg)
            throw RuntimeException("Unexpected Gemini response format")
        }
        return extractJsonStringValue(json.substring(textStart), "text")
            ?: throw RuntimeException("Could not parse Gemini response text")
    }

    // -- OpenAI-compatible API (OpenAI, Groq, Together, OpenRouter) --

    private suspend fun sendOpenAICompatibleRequest(
        provider: AIProvider,
        apiKey: String,
        model: String,
        systemPrompt: String,
        userMessage: String
    ): String {
        val body = buildString {
            append("{")
            append("\"model\":${jsonString(model)},")
            append("\"messages\":[")
            append("{\"role\":\"system\",\"content\":${jsonString(systemPrompt)}},")
            append("{\"role\":\"user\",\"content\":${jsonString(userMessage)}}")
            append("]")
            append("}")
        }

        val headers = buildMap {
            put(provider.authHeaderName, "${provider.authPrefix}$apiKey")
            put("Content-Type", "application/json")
            if (provider == AIProvider.OPENROUTER) {
                put("HTTP-Referer", "https://gedfix.app")
                put("X-Title", "GedFix")
            }
        }

        return httpPost(provider.endpoint, headers, body)
    }

    private fun parseOpenAIResponse(json: String): String {
        // Extract choices[0].message.content
        val contentStart = json.indexOf("\"content\"")
        if (contentStart == -1) {
            val errorMsg = extractJsonStringValue(json, "message")
            if (errorMsg != null) throw RuntimeException(errorMsg)
            throw RuntimeException("Unexpected OpenAI-compatible response format")
        }
        // Find the content value after "message" object
        // We need to find the content field that's inside the message object, not any other content field
        val messageIdx = json.indexOf("\"message\"")
        if (messageIdx != -1) {
            val messageSection = json.substring(messageIdx)
            return extractJsonStringValue(messageSection, "content")
                ?: throw RuntimeException("Could not parse response content")
        }
        return extractJsonStringValue(json.substring(contentStart), "content")
            ?: throw RuntimeException("Could not parse response content")
    }

    // -- DeepSeek (special handling for Reasoner which has no system prompt) --

    private suspend fun sendDeepSeekRequest(
        provider: AIProvider,
        apiKey: String,
        model: String,
        systemPrompt: String,
        userMessage: String
    ): String {
        val isReasoner = model.contains("reasoner", ignoreCase = true)

        val body = if (isReasoner) {
            // DeepSeek Reasoner does NOT support system prompts.
            // Embed the system instructions as a prefix in the user message.
            val combinedMessage = "[INSTRUCTIONS]\n$systemPrompt\n[/INSTRUCTIONS]\n\n$userMessage"
            buildString {
                append("{")
                append("\"model\":${jsonString(model)},")
                append("\"messages\":[")
                append("{\"role\":\"user\",\"content\":${jsonString(combinedMessage)}}")
                append("]")
                append("}")
            }
        } else {
            // DeepSeek Chat V3 supports system prompts normally
            buildString {
                append("{")
                append("\"model\":${jsonString(model)},")
                append("\"messages\":[")
                append("{\"role\":\"system\",\"content\":${jsonString(systemPrompt)}},")
                append("{\"role\":\"user\",\"content\":${jsonString(userMessage)}}")
                append("]")
                append("}")
            }
        }

        val headers = mapOf(
            provider.authHeaderName to "${provider.authPrefix}$apiKey",
            "Content-Type" to "application/json"
        )

        return httpPost(provider.endpoint, headers, body)
    }

    // -- HTTP Client --

    private suspend fun httpPost(url: String, headers: Map<String, String>, body: String): String {
        return withContext(Dispatchers.IO) {
            val conn = URL(url).openConnection() as HttpURLConnection
            try {
                conn.requestMethod = "POST"
                conn.doOutput = true
                conn.connectTimeout = 30_000
                conn.readTimeout = 120_000

                headers.forEach { (k, v) -> conn.setRequestProperty(k, v) }

                conn.outputStream.use { out ->
                    out.write(body.toByteArray(Charsets.UTF_8))
                    out.flush()
                }

                val responseCode = conn.responseCode
                val stream = if (responseCode in 200..299) conn.inputStream else conn.errorStream

                val responseText = BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { reader ->
                    reader.readText()
                }

                if (responseCode !in 200..299) {
                    val errorMsg = extractJsonStringValue(responseText, "message")
                        ?: extractJsonStringValue(responseText, "error")
                        ?: "HTTP $responseCode"
                    throw RuntimeException("API error ($responseCode): $errorMsg")
                }

                responseText
            } finally {
                conn.disconnect()
            }
        }
    }

    // -- JSON helpers (minimal, no external dependency) --

    private fun jsonString(value: String): String {
        val escaped = value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
        return "\"$escaped\""
    }

    /**
     * Extract a string value for a given key from a JSON fragment.
     * This is a minimal parser that handles the common cases without requiring
     * a full JSON library dependency.
     */
    private fun extractJsonStringValue(json: String, key: String): String? {
        val keyPattern = "\"$key\""
        val keyIdx = json.indexOf(keyPattern)
        if (keyIdx == -1) return null

        // Find the colon after the key
        val colonIdx = json.indexOf(':', keyIdx + keyPattern.length)
        if (colonIdx == -1) return null

        // Find the opening quote of the value
        val openQuote = json.indexOf('"', colonIdx + 1)
        if (openQuote == -1) return null

        // Find the closing quote, handling escaped quotes
        var i = openQuote + 1
        val sb = StringBuilder()
        while (i < json.length) {
            val c = json[i]
            if (c == '\\' && i + 1 < json.length) {
                val next = json[i + 1]
                when (next) {
                    '"' -> sb.append('"')
                    '\\' -> sb.append('\\')
                    'n' -> sb.append('\n')
                    'r' -> sb.append('\r')
                    't' -> sb.append('\t')
                    '/' -> sb.append('/')
                    else -> {
                        sb.append('\\')
                        sb.append(next)
                    }
                }
                i += 2
            } else if (c == '"') {
                return sb.toString()
            } else {
                sb.append(c)
                i++
            }
        }
        return null
    }
}
