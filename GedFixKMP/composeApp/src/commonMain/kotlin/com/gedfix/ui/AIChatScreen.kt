package com.gedfix.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gedfix.models.AIProvider
import com.gedfix.ui.theme.*
import com.gedfix.viewmodel.AIViewModel
import com.gedfix.viewmodel.AppViewModel
import kotlinx.coroutines.launch

/**
 * AI Chat interface for conversing with AI providers about genealogy research.
 * Supports provider/model switching, quick prompts, and person context injection.
 */
@Composable
fun AIChatScreen(
    appViewModel: AppViewModel,
    aiViewModel: AIViewModel
) {
    LaunchedEffect(Unit) {
        aiViewModel.load()
    }

    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    // Auto-scroll to bottom when new messages arrive
    LaunchedEffect(aiViewModel.chatMessages.size) {
        if (aiViewModel.chatMessages.isNotEmpty()) {
            listState.animateScrollToItem(aiViewModel.chatMessages.size - 1)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Toolbar with provider/model selectors
        ChatToolbar(aiViewModel = aiViewModel)

        HorizontalDivider()

        // Chat messages area
        if (aiViewModel.chatMessages.isEmpty() && !aiViewModel.isLoading) {
            // Empty state with quick prompts
            EmptyChatState(
                onQuickPrompt = { prompt ->
                    inputText = ""
                    aiViewModel.sendMessage(prompt)
                },
                hasApiKey = aiViewModel.getApiKey(aiViewModel.activeProvider).isNotBlank()
            )
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 12.dp)
            ) {
                items(aiViewModel.chatMessages, key = { it.id }) { message ->
                    ChatBubble(
                        content = message.content,
                        isUser = message.role == "user",
                        model = if (message.role == "assistant") message.model else null
                    )
                }

                // Loading indicator
                if (aiViewModel.isLoading) {
                    item {
                        Row(
                            modifier = Modifier.padding(start = 8.dp, top = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp
                            )
                            Text(
                                "Thinking...",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Error display
                aiViewModel.chatError?.let { error ->
                    item {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = CriticalColor.copy(alpha = 0.1f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = error,
                                fontSize = 13.sp,
                                color = CriticalColor,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }
                }
            }
        }

        HorizontalDivider()

        // Input area
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = inputText,
                onValueChange = { inputText = it },
                placeholder = { Text("Ask about your genealogy research...") },
                modifier = Modifier
                    .weight(1f)
                    .onKeyEvent { event ->
                        if (event.type == KeyEventType.KeyDown && event.key == Key.Enter && !event.isShiftPressed) {
                            if (inputText.isNotBlank() && !aiViewModel.isLoading) {
                                val msg = inputText
                                inputText = ""
                                aiViewModel.sendMessage(msg)
                            }
                            true
                        } else false
                    },
                maxLines = 4
            )

            Button(
                onClick = {
                    if (inputText.isNotBlank() && !aiViewModel.isLoading) {
                        val msg = inputText
                        inputText = ""
                        aiViewModel.sendMessage(msg)
                    }
                },
                enabled = inputText.isNotBlank() && !aiViewModel.isLoading
            ) {
                Text("Send")
            }
        }
    }
}

@Composable
private fun ChatToolbar(aiViewModel: AIViewModel) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "AI Chat",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )

            // Provider selector
            var providerExpanded by remember { mutableStateOf(false) }
            Box {
                OutlinedButton(
                    onClick = { providerExpanded = true },
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(aiViewModel.activeProvider.displayName, fontSize = 13.sp)
                }
                DropdownMenu(
                    expanded = providerExpanded,
                    onDismissRequest = { providerExpanded = false }
                ) {
                    for (provider in AIProvider.entries) {
                        DropdownMenuItem(
                            text = { Text(provider.displayName, fontSize = 13.sp) },
                            onClick = {
                                aiViewModel.switchActiveProvider(provider)
                                providerExpanded = false
                            }
                        )
                    }
                }
            }

            // Model selector
            var modelExpanded by remember { mutableStateOf(false) }
            Box {
                OutlinedButton(
                    onClick = { modelExpanded = true },
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    val modelName = aiViewModel.activeProvider.availableModels
                        .firstOrNull { it.id == aiViewModel.activeModel }?.displayName
                        ?: aiViewModel.activeModel
                    Text(modelName, fontSize = 13.sp)
                }
                DropdownMenu(
                    expanded = modelExpanded,
                    onDismissRequest = { modelExpanded = false }
                ) {
                    for (model in aiViewModel.activeProvider.availableModels) {
                        DropdownMenuItem(
                            text = { Text(model.displayName, fontSize = 13.sp) },
                            onClick = {
                                aiViewModel.setSelectedModel(aiViewModel.activeProvider, model.id)
                                modelExpanded = false
                            }
                        )
                    }
                }
            }
        }

        // Clear chat button
        if (aiViewModel.chatMessages.isNotEmpty()) {
            TextButton(onClick = { aiViewModel.clearChat() }) {
                Text("Clear Chat", fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun EmptyChatState(
    onQuickPrompt: (String) -> Unit,
    hasApiKey: Boolean
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "\u2604",
            fontSize = 48.sp,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        Text(
            text = "AI Genealogy Assistant",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        if (!hasApiKey) {
            Text(
                text = "Configure an API key in AI Settings to get started.",
                fontSize = 14.sp,
                color = WarningColor,
                modifier = Modifier.padding(bottom = 24.dp)
            )
        } else {
            Text(
                text = "Ask questions about genealogy research, records, or your family tree.",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 24.dp)
            )
        }

        // Quick prompts
        Text(
            text = "Quick Prompts",
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        val quickPrompts = listOf(
            "What records should I search for someone born in the 1800s in Ireland?",
            "How do I evaluate conflicting birth dates across census records?",
            "Explain the Genealogical Proof Standard and how to apply it.",
            "What are common name variants for German immigrant surnames?",
            "How do I calculate shared DNA centiMorgans for half-cousins?",
            "What should I look for in a probate record?"
        )

        for (prompt in quickPrompts) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth(0.7f)
                    .padding(vertical = 4.dp),
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                onClick = { onQuickPrompt(prompt) }
            ) {
                Text(
                    text = prompt,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(12.dp),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
private fun ChatBubble(
    content: String,
    isUser: Boolean,
    model: String? = null
) {
    val alignment = if (isUser) Alignment.End else Alignment.Start
    val bgColor = if (isUser) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val textColor = if (isUser) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = alignment
    ) {
        Surface(
            shape = RoundedCornerShape(
                topStart = 12.dp,
                topEnd = 12.dp,
                bottomStart = if (isUser) 12.dp else 4.dp,
                bottomEnd = if (isUser) 4.dp else 12.dp
            ),
            color = bgColor,
            modifier = Modifier.widthIn(max = 600.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = content,
                    fontSize = 14.sp,
                    color = textColor,
                    lineHeight = 20.sp
                )
                if (model != null) {
                    Text(
                        text = model,
                        fontSize = 10.sp,
                        color = textColor.copy(alpha = 0.5f),
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }
    }
}
