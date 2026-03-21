package com.gedfix.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gedfix.models.AIProvider
import com.gedfix.ui.theme.*
import com.gedfix.viewmodel.AIViewModel
import com.gedfix.viewmodel.ConnectionTestResult

/**
 * AI provider configuration screen.
 * Shows each provider as an expandable card with API key, model selection,
 * capabilities, and connection test functionality.
 */
@Composable
fun AISettingsScreen(aiViewModel: AIViewModel) {
    LaunchedEffect(Unit) {
        aiViewModel.load()
    }

    val configuredCount = aiViewModel.configuredProviderCount()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // Header
        Text(
            text = "AI Settings",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 4.dp)
        )

        // Active provider selector
        Surface(
            shape = RoundedCornerShape(12.dp),
            tonalElevation = 1.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = "Active Provider",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Text(
                    text = "$configuredCount of ${AIProvider.entries.size} providers configured",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                var providerDropdownExpanded by remember { mutableStateOf(false) }
                val configuredProviders = AIProvider.entries.filter { aiViewModel.getApiKey(it).isNotBlank() }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box {
                        OutlinedButton(onClick = { providerDropdownExpanded = true }) {
                            Text(aiViewModel.activeProvider.displayName)
                        }
                        DropdownMenu(
                            expanded = providerDropdownExpanded,
                            onDismissRequest = { providerDropdownExpanded = false }
                        ) {
                            for (provider in AIProvider.entries) {
                                val hasKey = aiViewModel.getApiKey(provider).isNotBlank()
                                DropdownMenuItem(
                                    text = {
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(provider.displayName)
                                            if (!hasKey) {
                                                Text(
                                                    "(no key)",
                                                    fontSize = 11.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    },
                                    onClick = {
                                        aiViewModel.switchActiveProvider(provider)
                                        providerDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    Text(
                        text = "Model: ${aiViewModel.activeModel}",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Provider cards
        for (provider in AIProvider.entries) {
            ProviderCard(provider = provider, aiViewModel = aiViewModel)
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
private fun ProviderCard(
    provider: AIProvider,
    aiViewModel: AIViewModel
) {
    var expanded by remember { mutableStateOf(false) }
    var apiKeyText by remember(provider) { mutableStateOf(aiViewModel.getApiKey(provider)) }
    var showApiKey by remember { mutableStateOf(false) }

    val hasKey = apiKeyText.isNotBlank()
    val testResult = aiViewModel.getTestResult(provider)
    val isTesting = aiViewModel.testingProvider == provider

    val statusColor = when {
        testResult == ConnectionTestResult.SUCCESS -> AIProviderConnected
        testResult == ConnectionTestResult.FAILURE -> AIProviderError
        hasKey -> AIProviderConnected.copy(alpha = 0.5f)
        else -> AIProviderDisconnected
    }

    val statusText = when {
        isTesting -> "Testing..."
        testResult == ConnectionTestResult.SUCCESS -> "Connected"
        testResult == ConnectionTestResult.FAILURE -> "Error"
        hasKey -> "Configured"
        else -> "No API Key"
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth(),
        onClick = { expanded = !expanded }
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            // Header row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = provider.displayName,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        // Status indicator
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = statusColor.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = statusText,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = statusColor,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                    Text(
                        text = provider.description,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                Text(
                    text = if (expanded) "\u25B2" else "\u25BC",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Expandable detail
            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier.padding(top = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // API Key field
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = apiKeyText,
                            onValueChange = { newValue ->
                                apiKeyText = newValue
                                aiViewModel.setApiKey(provider, newValue)
                            },
                            label = { Text("API Key") },
                            visualTransformation = if (showApiKey) VisualTransformation.None
                                else PasswordVisualTransformation(),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = { showApiKey = !showApiKey }) {
                            Text(if (showApiKey) "Hide" else "Show", fontSize = 12.sp)
                        }
                    }

                    // Model selector
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("Model:", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        var modelDropdown by remember { mutableStateOf(false) }
                        val selectedModel = aiViewModel.getSelectedModel(provider)

                        Box {
                            OutlinedButton(onClick = { modelDropdown = true }) {
                                val modelDisplay = provider.availableModels
                                    .firstOrNull { it.id == selectedModel }?.displayName
                                    ?: selectedModel
                                Text(modelDisplay, fontSize = 13.sp)
                            }
                            DropdownMenu(
                                expanded = modelDropdown,
                                onDismissRequest = { modelDropdown = false }
                            ) {
                                for (model in provider.availableModels) {
                                    DropdownMenuItem(
                                        text = {
                                            Column {
                                                Text(model.displayName, fontWeight = FontWeight.Medium, fontSize = 13.sp)
                                                Text(
                                                    "${model.description} (${formatContextWindow(model.contextWindow)})",
                                                    fontSize = 11.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        },
                                        onClick = {
                                            aiViewModel.setSelectedModel(provider, model.id)
                                            modelDropdown = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // Test connection button
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { aiViewModel.testConnection(provider) },
                            enabled = hasKey && !isTesting
                        ) {
                            if (isTesting) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(14.dp),
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                            }
                            Text(if (isTesting) "Testing..." else "Test Connection", fontSize = 13.sp)
                        }
                    }

                    // Capabilities
                    Text(
                        text = "Capabilities",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                    for (capability in provider.capabilities) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("\u2713", fontSize = 12.sp, color = AIProviderConnected)
                            Text(capability, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    // Provider metadata
                    Row(
                        modifier = Modifier.padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        if (provider.supportsVision) {
                            Text(
                                "Vision: Yes",
                                fontSize = 11.sp,
                                color = AIProviderConnected
                            )
                        }
                        if (!provider.supportsSystemPrompt) {
                            Text(
                                "System prompt: No (uses user prefix)",
                                fontSize = 11.sp,
                                color = WarningColor
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun formatContextWindow(tokens: Int): String {
    return when {
        tokens >= 1_000_000 -> "${tokens / 1_000_000}M tokens"
        tokens >= 1_000 -> "${tokens / 1_000}K tokens"
        else -> "$tokens tokens"
    }
}
