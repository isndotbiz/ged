package com.gedfix.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gedfix.services.HandleEverythingRun
import com.gedfix.services.HandleEverythingService
import com.gedfix.services.HandlePreview
import com.gedfix.services.HandleResult
import com.gedfix.ui.theme.*
import com.gedfix.viewmodel.AppViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The killer feature screen: JUST HANDLE EVERYTHING.
 * Big, bold, confident UI with progress tracking, report display, and undo capability.
 */
@Composable
fun HandleEverythingScreen(appViewModel: AppViewModel) {
    val scope = rememberCoroutineScope()
    val service = remember { HandleEverythingService(appViewModel.db) }

    var state by remember { mutableStateOf<HEState>(HEState.Idle) }
    var preview by remember { mutableStateOf<HandlePreview?>(null) }
    var result by remember { mutableStateOf<HandleResult?>(null) }
    var progressPhase by remember { mutableIntStateOf(0) }
    var progressTotal by remember { mutableIntStateOf(7) }
    var progressMessage by remember { mutableStateOf("") }
    var previousRuns by remember { mutableStateOf<List<HandleEverythingRun>>(emptyList()) }
    var showUndoConfirm by remember { mutableStateOf<String?>(null) }
    var undoResult by remember { mutableStateOf<String?>(null) }

    // Load preview and history on first display
    LaunchedEffect(Unit) {
        withContext(Dispatchers.Default) {
            preview = service.preview()
            previousRuns = appViewModel.db.fetchAllHandleEverythingRuns()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // Header
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "\u2728",
                fontSize = 40.sp,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Just Handle Everything",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "One click to backup, fix, deduplicate, organize, and report.",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }

        when (state) {
            HEState.Idle -> {
                // Preview section
                preview?.let { p ->
                    PreviewSection(p)
                }

                // THE BUTTON
                Spacer(modifier = Modifier.height(8.dp))
                TheButton(
                    enabled = appViewModel.personCount > 0,
                    onClick = {
                        state = HEState.Running
                        scope.launch {
                            withContext(Dispatchers.Default) {
                                result = service.handleEverything { phase, total, message ->
                                    progressPhase = phase
                                    progressTotal = total
                                    progressMessage = message
                                }
                            }
                            appViewModel.refreshCounts()
                            previousRuns = appViewModel.db.fetchAllHandleEverythingRuns()
                            state = HEState.Complete
                        }
                    }
                )

                if (appViewModel.personCount == 0) {
                    Text(
                        text = "Import a GEDCOM file first to use this feature.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            HEState.Running -> {
                ProgressSection(progressPhase, progressTotal, progressMessage)
            }

            HEState.Complete -> {
                result?.let { r ->
                    ResultSection(r)

                    Spacer(modifier = Modifier.height(16.dp))

                    // Run again button
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = {
                                state = HEState.Idle
                                result = null
                                scope.launch {
                                    withContext(Dispatchers.Default) {
                                        preview = service.preview()
                                    }
                                }
                            }
                        ) {
                            Text("Back to Overview")
                        }
                    }
                }
            }
        }

        // Previous runs history
        if (previousRuns.isNotEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))
            PreviousRunsSection(
                runs = previousRuns,
                onUndo = { runId -> showUndoConfirm = runId }
            )
        }

        // Undo result message
        undoResult?.let { msg ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = InfoColor.copy(alpha = 0.1f))
            ) {
                Text(
                    text = msg,
                    modifier = Modifier.padding(16.dp),
                    color = InfoColor
                )
            }
        }
    }

    // Undo confirmation dialog
    showUndoConfirm?.let { runId ->
        AlertDialog(
            onDismissRequest = { showUndoConfirm = null },
            title = { Text("Undo Handle Everything?") },
            text = { Text("This will restore the tree to its state before this run. All fixes, renames, and deduplication will be reversed.") },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            val success = withContext(Dispatchers.Default) {
                                service.undoRun(runId)
                            }
                            undoResult = if (success) {
                                appViewModel.refreshCounts()
                                previousRuns = appViewModel.db.fetchAllHandleEverythingRuns()
                                "Successfully restored tree from backup."
                            } else {
                                "Failed to undo. Backup may not be available."
                            }
                            showUndoConfirm = null
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CriticalColor)
                ) {
                    Text("Undo Everything")
                }
            },
            dismissButton = {
                TextButton(onClick = { showUndoConfirm = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

private enum class HEState { Idle, Running, Complete }

// ================================================================
// Preview Section
// ================================================================

@Composable
private fun PreviewSection(preview: HandlePreview) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "What will happen",
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
            )

            PreviewRow("Tree issues detected", preview.totalIssues.toString(), WarningColor)
            PreviewRow("Auto-fixable issues", preview.autoFixableIssues.toString(), HealthGoodColor)

            if (preview.suffixFixes > 0) {
                PreviewDetail("Suffixes to move to NSFX", preview.suffixFixes)
            }
            if (preview.duplicateFactFixes > 0) {
                PreviewDetail("Duplicate facts to remove", preview.duplicateFactFixes)
            }
            if (preview.genderInferences > 0) {
                PreviewDetail("Genders to infer from names", preview.genderInferences)
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))

            PreviewRow("Duplicate images (exact hash)", preview.duplicateImages.toString(), ImageDedupeIconColor)
            PreviewRow("Total media files", preview.totalMedia.toString(), MediaIconColor)
            PreviewRow("Unlinked media", preview.unlinkedMedia.toString(), WarningColor)
        }
    }
}

@Composable
private fun PreviewRow(label: String, value: String, accentColor: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = accentColor.copy(alpha = 0.12f)
        ) {
            Text(
                text = value,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = accentColor,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
            )
        }
    }
}

@Composable
private fun PreviewDetail(label: String, count: Int) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        )
        Text(
            text = count.toString(),
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        )
    }
}

// ================================================================
// THE BUTTON
// ================================================================

@Composable
private fun TheButton(enabled: Boolean, onClick: () -> Unit) {
    val infiniteTransition = rememberInfiniteTransition(label = "glow")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowAlpha"
    )

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        Button(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier
                .width(320.dp)
                .height(56.dp)
                .alpha(if (enabled) glowAlpha else 0.5f),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = HandleEverythingIconColor,
                contentColor = Color.White
            ),
            elevation = ButtonDefaults.buttonElevation(
                defaultElevation = 4.dp,
                pressedElevation = 1.dp
            )
        ) {
            Text(
                text = "\u2728  JUST HANDLE EVERYTHING",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp
            )
        }
    }
}

// ================================================================
// Progress Section
// ================================================================

@Composable
private fun ProgressSection(phase: Int, total: Int, message: String) {
    val progress = if (total > 0) phase.toFloat() / total.toFloat() else 0f

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Animated sparkle
            val infiniteTransition = rememberInfiniteTransition(label = "spin")
            val rotation by infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = 360f,
                animationSpec = infiniteRepeatable(
                    animation = tween(2000, easing = LinearEasing)
                ),
                label = "rotation"
            )

            Text(
                text = "\u2728",
                fontSize = 36.sp
            )

            Text(
                text = "Working...",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )

            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().height(8.dp),
                color = HandleEverythingIconColor,
                trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f),
                drawStopIndicator = {}
            )

            Text(
                text = message,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Text(
                text = "Phase $phase of $total",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )

            // Phase checklist
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                val phases = listOf(
                    "Creating backup",
                    "Fixing tree issues",
                    "Deduplicating images",
                    "Organizing media files",
                    "Linking unattached media",
                    "Generating report",
                    "Done"
                )
                phases.forEachIndexed { index, phaseName ->
                    val phaseNum = index + 1
                    val icon = when {
                        phaseNum < phase -> "[OK]"
                        phaseNum == phase -> "[..]"
                        else -> "[  ]"
                    }
                    val color = when {
                        phaseNum < phase -> HealthGoodColor
                        phaseNum == phase -> HandleEverythingIconColor
                        else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    }
                    Text(
                        text = "$icon  $phaseName",
                        fontSize = 13.sp,
                        fontWeight = if (phaseNum == phase) FontWeight.SemiBold else FontWeight.Normal,
                        color = color
                    )
                }
            }
        }
    }
}

// ================================================================
// Result Section
// ================================================================

@Composable
private fun ResultSection(result: HandleResult) {
    // Success header
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = HealthGoodColor.copy(alpha = 0.08f)
        )
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(text = "[OK]", fontSize = 32.sp, color = HealthGoodColor)
            Text(
                text = "Everything Handled!",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = HealthGoodColor
            )
        }
    }

    // Stats cards
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ResultStatCard(
            label = "Fixes Applied",
            value = result.treeFixesApplied.toString(),
            color = HealthGoodColor,
            modifier = Modifier.weight(1f)
        )
        ResultStatCard(
            label = "Need Review",
            value = result.treeFixesNeedReview.toString(),
            color = WarningColor,
            modifier = Modifier.weight(1f)
        )
        ResultStatCard(
            label = "Deduped",
            value = result.duplicatesRemoved.toString(),
            color = ImageDedupeIconColor,
            modifier = Modifier.weight(1f)
        )
        ResultStatCard(
            label = "Organized",
            value = result.filesOrganized.toString(),
            color = MediaIconColor,
            modifier = Modifier.weight(1f)
        )
    }

    // Full report
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Full Report",
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            Text(
                text = result.report,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 18.sp
            )
        }
    }

    // Fix details
    if (result.fixDetails.isNotEmpty()) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = HealthGoodColor.copy(alpha = 0.04f)
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "Changes Made",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                for (detail in result.fixDetails) {
                    Text(
                        text = "[OK]  $detail",
                        fontSize = 13.sp,
                        color = HealthGoodColor
                    )
                }
            }
        }
    }

    // Review items
    if (result.reviewItems.isNotEmpty()) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = WarningColor.copy(alpha = 0.06f)
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "Needs Manual Review",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = WarningColor,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                for (item in result.reviewItems) {
                    Text(
                        text = "[!!]  $item",
                        fontSize = 13.sp,
                        color = WarningColor
                    )
                }
            }
        }
    }
}

@Composable
private fun ResultStatCard(
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = color.copy(alpha = 0.08f)
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = value,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = color
            )
            Text(
                text = label,
                fontSize = 11.sp,
                color = color.copy(alpha = 0.8f)
            )
        }
    }
}

// ================================================================
// Previous Runs Section
// ================================================================

@Composable
private fun PreviousRunsSection(
    runs: List<HandleEverythingRun>,
    onUndo: (String) -> Unit
) {
    Text(
        text = "Run History",
        fontSize = 18.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(bottom = 8.dp)
    )

    for (run in runs.take(5)) {
        Card(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
            )
        ) {
            Row(
                modifier = Modifier.padding(16.dp).fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = formatTimestamp(run.timestamp),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "${run.fixesApplied} fixes, ${run.duplicatesRemoved} deduped, ${run.filesRenamed} renamed",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                OutlinedButton(
                    onClick = { onUndo(run.id) },
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = CriticalColor
                    )
                ) {
                    Text("Undo", fontSize = 12.sp)
                }
            }
        }
    }
}

private fun formatTimestamp(iso: String): String {
    return try {
        val instant = java.time.Instant.parse(iso)
        val local = java.time.LocalDateTime.ofInstant(instant, java.time.ZoneId.systemDefault())
        local.format(java.time.format.DateTimeFormatter.ofPattern("MMM d, yyyy  h:mm a"))
    } catch (_: Exception) {
        iso
    }
}
