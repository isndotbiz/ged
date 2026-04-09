package com.gedfix.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.gedfix.db.DatabaseRepository
import com.gedfix.models.*
import com.gedfix.services.AIService
import kotlinx.coroutines.*
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * ViewModel managing AI provider configuration, chat history, and AI interactions.
 * Persists API keys and model selections in the database settings table.
 * Chat history is stored in the aiChatHistory table.
 */
@OptIn(ExperimentalUuidApi::class)
class AIViewModel(private val db: DatabaseRepository) {
    private val aiService = AIService(db)
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    // -- Provider configuration state --
    var activeProvider by mutableStateOf(AIProvider.ANTHROPIC)
    var activeModel by mutableStateOf(AIProvider.ANTHROPIC.defaultModel)

    // API keys per provider (keyed by provider name)
    private val apiKeys = mutableMapOf<String, String>()
    private val selectedModels = mutableMapOf<String, String>()

    // -- Connection test state --
    var testingProvider by mutableStateOf<AIProvider?>(null)
        private set
    var testResults = mutableMapOf<String, ConnectionTestResult>()

    // -- Chat state --
    val chatMessages = mutableStateListOf<AIChatMessage>()
    var isLoading by mutableStateOf(false)
        private set
    var chatError by mutableStateOf<String?>(null)
        private set
    var currentPersonXref by mutableStateOf<String?>(null)

    // -- Settings accessors --

    fun getApiKey(provider: AIProvider): String {
        return apiKeys[provider.name] ?: ""
    }

    fun setApiKey(provider: AIProvider, key: String) {
        apiKeys[provider.name] = key
        db.setSetting("ai_key_${provider.name}", key)
    }

    fun getSelectedModel(provider: AIProvider): String {
        return selectedModels[provider.name] ?: provider.defaultModel
    }

    fun setSelectedModel(provider: AIProvider, modelId: String) {
        selectedModels[provider.name] = modelId
        db.setSetting("ai_model_${provider.name}", modelId)
        if (provider == activeProvider) {
            activeModel = modelId
        }
    }

    fun switchActiveProvider(provider: AIProvider) {
        activeProvider = provider
        activeModel = getSelectedModel(provider)
        db.setSetting("ai_active_provider", provider.name)
    }

    fun configuredProviderCount(): Int {
        return AIProvider.entries.count { getApiKey(it).isNotBlank() }
    }

    // -- Load persisted state --

    fun load() {
        val settings = db.getAllSettings()

        // Load API keys
        for (provider in AIProvider.entries) {
            val key = settings["ai_key_${provider.name}"] ?: ""
            apiKeys[provider.name] = key

            val model = settings["ai_model_${provider.name}"] ?: provider.defaultModel
            selectedModels[provider.name] = model
        }

        // Load active provider
        val activeName = settings["ai_active_provider"] ?: AIProvider.ANTHROPIC.name
        activeProvider = AIProvider.fromName(activeName) ?: AIProvider.ANTHROPIC
        activeModel = getSelectedModel(activeProvider)

        // Load chat history
        loadChatHistory()
    }

    // -- Connection testing --

    fun testConnection(provider: AIProvider) {
        val key = getApiKey(provider)
        val model = getSelectedModel(provider)
        testingProvider = provider
        testResults[provider.name] = ConnectionTestResult.TESTING

        scope.launch {
            val result = aiService.testConnection(provider, key, model)
            withContext(Dispatchers.Main) {
                testResults = testResults.toMutableMap().apply {
                    this[provider.name] = if (result.error == null) {
                        ConnectionTestResult.SUCCESS
                    } else {
                        ConnectionTestResult.FAILURE
                    }
                }
                testingProvider = null
            }
        }
    }

    fun getTestResult(provider: AIProvider): ConnectionTestResult {
        return testResults[provider.name] ?: ConnectionTestResult.NONE
    }

    // -- Chat operations --

    fun sendMessage(userMessage: String, personContext: String = "") {
        if (userMessage.isBlank() || isLoading) return

        val apiKey = getApiKey(activeProvider)
        val model = activeModel
        val systemPrompt = GenealogyPrompts.forProvider(activeProvider)

        // Build full prompt with person context if available
        val fullMessage = if (personContext.isNotBlank()) {
            "Context about the person being discussed:\n$personContext\n\n$userMessage"
        } else {
            userMessage
        }

        // Add user message to chat
        val userMsg = AIChatMessage(
            id = Uuid.random().toString(),
            provider = activeProvider.name,
            model = model,
            role = "user",
            content = userMessage,
            personXref = currentPersonXref ?: "",
            timestamp = currentTimestamp()
        )
        chatMessages.add(userMsg)
        saveChatMessage(userMsg)

        isLoading = true
        chatError = null

        scope.launch {
            val response = aiService.chat(
                provider = activeProvider,
                apiKey = apiKey,
                model = model,
                systemPrompt = systemPrompt,
                userMessage = fullMessage
            )

            withContext(Dispatchers.Main) {
                isLoading = false

                if (response.error != null) {
                    chatError = response.error
                } else {
                    val assistantMsg = AIChatMessage(
                        id = Uuid.random().toString(),
                        provider = activeProvider.name,
                        model = model,
                        role = "assistant",
                        content = response.content,
                        personXref = currentPersonXref ?: "",
                        timestamp = currentTimestamp()
                    )
                    chatMessages.add(assistantMsg)
                    saveChatMessage(assistantMsg)
                }
            }
        }
    }

    /**
     * Send a quick AI query about a specific person (used from PersonDetailScreen, etc.)
     * Returns the response via the provided callback.
     */
    fun askAboutPerson(
        person: GedcomPerson,
        events: List<GedcomEvent>,
        query: String,
        onResult: (String?) -> Unit,
        onError: (String) -> Unit
    ) {
        val apiKey = getApiKey(activeProvider)
        val model = activeModel
        val systemPrompt = GenealogyPrompts.forProvider(activeProvider)

        val personContext = buildPersonContext(person, events)
        val fullMessage = "Context about the person:\n$personContext\n\n$query"

        scope.launch {
            val response = aiService.chat(
                provider = activeProvider,
                apiKey = apiKey,
                model = model,
                systemPrompt = systemPrompt,
                userMessage = fullMessage
            )

            withContext(Dispatchers.Main) {
                if (response.error != null) {
                    onError(response.error)
                } else {
                    onResult(response.content)
                }
            }
        }
    }

    fun clearChat() {
        chatMessages.clear()
        chatError = null
        // Clear from DB
        db.setSetting("ai_chat_cleared", "true")
    }

    // -- Helpers --

    fun buildPersonContext(person: GedcomPerson, events: List<GedcomEvent>): String {
        return buildString {
            appendLine("Person: ${person.displayName} (${person.xref})")
            appendLine("Sex: ${person.sex}")
            appendLine("Living: ${person.isLiving}")
            appendLine("Sources: ${person.sourceCount}, Media: ${person.mediaCount}")
            if (events.isNotEmpty()) {
                appendLine("Events:")
                for (event in events) {
                    append("  - ${event.displayType}")
                    if (event.dateValue.isNotEmpty()) append(": ${event.dateValue}")
                    if (event.place.isNotEmpty()) append(" at ${event.place}")
                    if (event.description.isNotEmpty()) append(" (${event.description})")
                    appendLine()
                }
            }
        }
    }

    private fun saveChatMessage(msg: AIChatMessage) {
        try {
            db.insertAIChatMessage(msg)
        } catch (_: Exception) {
            // Table may not exist yet on first run; silently skip
        }
    }

    private fun loadChatHistory() {
        try {
            val history = db.fetchAIChatHistory()
            chatMessages.clear()
            chatMessages.addAll(history)
        } catch (_: Exception) {
            // Table may not exist yet
        }
    }

    private fun currentTimestamp(): String {
        return java.time.Instant.now().toString()
    }
}

enum class ConnectionTestResult {
    NONE, TESTING, SUCCESS, FAILURE
}
