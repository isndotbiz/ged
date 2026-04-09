package com.gedfix.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gedfix.models.DuplicateImageGroup
import com.gedfix.models.ImageDeduplicator
import com.gedfix.models.ImageMatchType
import com.gedfix.ui.theme.*
import com.gedfix.viewmodel.AppViewModel

/**
 * Image deduplication screen for finding and merging duplicate media files.
 */
@Composable
fun ImageDedupeScreen(appViewModel: AppViewModel) {
    var isScanning by remember { mutableStateOf(false) }
    var groups by remember { mutableStateOf<List<DuplicateImageGroup>?>(null) }
    var mergedCount by remember { mutableStateOf(0) }
    val deduplicator = remember { ImageDeduplicator(appViewModel.db) }

    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Image Deduplication", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Text(
                    "Find and merge duplicate images in your tree",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Button(
                onClick = {
                    isScanning = true
                    groups = deduplicator.findDuplicates()
                    isScanning = false
                },
                enabled = !isScanning
            ) {
                Text(if (groups == null) "Scan for Duplicates" else "Rescan")
            }
        }

        if (mergedCount > 0) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = ValidatedBgColor,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Merged $mergedCount duplicate groups",
                    modifier = Modifier.padding(12.dp),
                    color = ValidatedColor,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        if (isScanning) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Scanning media files...")
                }
            }
        } else if (groups == null) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("\u229A", fontSize = 48.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "Click 'Scan for Duplicates' to begin",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "Scans by file hash, filename, and file size similarity.",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else if (groups!!.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("\u2714", fontSize = 48.sp, color = ValidatedColor)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "No duplicates found",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium,
                        color = ValidatedColor
                    )
                }
            }
        } else {
            Text(
                "${groups!!.size} duplicate groups found",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )

            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(groups!!) { group ->
                    DuplicateGroupCard(
                        group = group,
                        onMerge = { keepId ->
                            deduplicator.mergeGroup(group, keepId)
                            mergedCount++
                            groups = deduplicator.findDuplicates()
                            appViewModel.refreshCounts()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun DuplicateGroupCard(
    group: DuplicateImageGroup,
    onMerge: (keepId: String) -> Unit
) {
    var selectedKeepId by remember { mutableStateOf(group.images.firstOrNull()?.id ?: "") }

    val matchColor = when (group.matchType) {
        ImageMatchType.EXACT_HASH -> CriticalColor
        ImageMatchType.SAME_FILENAME -> WarningColor
        ImageMatchType.SIMILAR_SIZE -> InfoColor
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = group.displayMatchType,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = matchColor
                    )
                    Text(
                        text = "${group.images.size} images",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Button(
                    onClick = { onMerge(selectedKeepId) },
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)
                ) {
                    Text("Merge", fontSize = 12.sp)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            group.images.forEach { media ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    RadioButton(
                        selected = selectedKeepId == media.id,
                        onClick = { selectedKeepId = media.id }
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = media.displayTitle,
                            fontSize = 13.sp,
                            fontWeight = if (selectedKeepId == media.id) FontWeight.SemiBold else FontWeight.Normal
                        )
                        Text(
                            text = media.filePath,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                    Text(
                        text = media.formatBadge,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (selectedKeepId.isNotEmpty()) {
                Text(
                    text = "Will keep: ${group.images.find { it.id == selectedKeepId }?.displayTitle}",
                    fontSize = 11.sp,
                    color = ValidatedColor,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}
