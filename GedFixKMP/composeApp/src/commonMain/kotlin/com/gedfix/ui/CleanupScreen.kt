package com.gedfix.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gedfix.services.CleanupReport
import com.gedfix.services.CloudStorageService
import com.gedfix.services.FileCleanupService
import com.gedfix.ui.theme.*
import com.gedfix.viewmodel.AppViewModel

/**
 * Cleanup dashboard for finding and resolving data quality issues.
 */
@Composable
fun CleanupScreen(appViewModel: AppViewModel) {
    var report by remember { mutableStateOf<CleanupReport?>(null) }
    var isAnalyzing by remember { mutableStateOf(false) }
    var actionResult by remember { mutableStateOf<String?>(null) }
    val cleanupService = remember { FileCleanupService(appViewModel.db) }

    Column(
        modifier = Modifier.fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Cleanup", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Text(
                    "Find and fix data quality issues",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Button(
                onClick = {
                    isAnalyzing = true
                    report = cleanupService.analyze()
                    isAnalyzing = false
                },
                enabled = !isAnalyzing
            ) {
                Text(if (report == null) "Analyze" else "Re-analyze")
            }
        }

        actionResult?.let { result ->
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = ValidatedBgColor,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = result,
                    modifier = Modifier.padding(12.dp),
                    color = ValidatedColor,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        if (isAnalyzing) {
            Box(
                modifier = Modifier.fillMaxWidth().height(200.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Analyzing database...")
                }
            }
        } else if (report == null) {
            Box(
                modifier = Modifier.fillMaxWidth().height(300.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("\u2702", fontSize = 48.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "Click 'Analyze' to scan for cleanup opportunities",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            val r = report!!

            if (!r.hasIssues) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = ValidatedBgColor,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("\u2714", fontSize = 48.sp, color = ValidatedColor)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Everything looks clean!",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = ValidatedColor
                        )
                    }
                }
            } else {
                // Summary
                Text(
                    "${r.totalIssues} issues found",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
                if (r.totalSavingsEstimate > 0) {
                    Text(
                        "Estimated savings: ${CloudStorageService.formatBytes(r.totalSavingsEstimate)}",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Orphaned Media
                CleanupCategory(
                    title = "Orphaned Media Records",
                    description = "Database records referencing files that no longer exist on disk",
                    count = r.orphanedMedia.size,
                    icon = "\u25A3",
                    iconColor = CriticalColor,
                    onClean = if (r.orphanedMedia.isNotEmpty()) {
                        {
                            val cleaned = cleanupService.cleanOrphanedMedia()
                            actionResult = "Removed $cleaned orphaned media records"
                            report = cleanupService.analyze()
                            appViewModel.refreshCounts()
                        }
                    } else null
                )

                // Duplicate Images
                CleanupCategory(
                    title = "Duplicate Images",
                    description = "Images that appear to be duplicates (identical hash, same name, or similar size)",
                    count = r.duplicateGroups.size,
                    icon = "\u229A",
                    iconColor = WarningColor,
                    onClean = null // handled by Image Dedupe screen
                )

                // Empty Person Records
                CleanupCategory(
                    title = "Empty Person Records",
                    description = "Persons with no name, no events, and no family connections",
                    count = r.emptyPersons.size,
                    icon = "\u263A",
                    iconColor = WarningColor,
                    onClean = if (r.emptyPersons.isNotEmpty()) {
                        {
                            val cleaned = cleanupService.cleanEmptyPersons()
                            actionResult = "Removed $cleaned empty person records"
                            report = cleanupService.analyze()
                            appViewModel.refreshCounts()
                        }
                    } else null
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
private fun CleanupCategory(
    title: String,
    description: String,
    count: Int,
    icon: String,
    iconColor: androidx.compose.ui.graphics.Color,
    onClean: (() -> Unit)?
) {
    var showConfirm by remember { mutableStateOf(false) }

    Surface(
        shape = RoundedCornerShape(12.dp),
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(text = icon, fontSize = 28.sp, color = iconColor)

            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    text = description,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = if (count == 0) "None found" else "$count found",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (count == 0) ValidatedColor else WarningColor
                )
            }

            if (onClean != null && count > 0) {
                Button(
                    onClick = { showConfirm = true },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Clean")
                }
            }
        }
    }

    if (showConfirm && onClean != null) {
        AlertDialog(
            onDismissRequest = { showConfirm = false },
            title = { Text("Clean $title?") },
            text = { Text("This will remove $count records. This action cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = { showConfirm = false; onClean() },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Clean") }
            },
            dismissButton = {
                OutlinedButton(onClick = { showConfirm = false }) { Text("Cancel") }
            }
        )
    }
}
