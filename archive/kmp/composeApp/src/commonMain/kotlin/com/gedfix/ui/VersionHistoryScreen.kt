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
import com.gedfix.models.ChangeType
import com.gedfix.models.GedcomParser
import com.gedfix.models.TreeVersion
import com.gedfix.ui.theme.*
import com.gedfix.viewmodel.AppViewModel

/**
 * Version history screen showing a timeline of all tree changes.
 * Users can browse history and revert to previous snapshots.
 */
@Composable
fun VersionHistoryScreen(appViewModel: AppViewModel) {
    var versions by remember { mutableStateOf(appViewModel.db.fetchAllVersions()) }
    var showRevertDialog by remember { mutableStateOf<TreeVersion?>(null) }
    var selectedVersion by remember { mutableStateOf<TreeVersion?>(null) }

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
                Text(
                    text = "Version History",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${versions.size} versions recorded",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (versions.size > 50) {
                OutlinedButton(onClick = {
                    appViewModel.db.deleteOldVersions(50)
                    versions = appViewModel.db.fetchAllVersions()
                    appViewModel.refreshCounts()
                }) {
                    Text("Keep Last 50")
                }
            }
        }

        if (versions.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "\u21BA",
                        fontSize = 48.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "No version history yet",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Versions are created automatically when you import, edit, or merge data.",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(versions) { version ->
                    VersionCard(
                        version = version,
                        isSelected = selectedVersion?.id == version.id,
                        onSelect = { selectedVersion = if (selectedVersion?.id == version.id) null else version },
                        onRevert = { showRevertDialog = version }
                    )
                }
            }
        }
    }

    // Revert confirmation dialog
    showRevertDialog?.let { version ->
        AlertDialog(
            onDismissRequest = { showRevertDialog = null },
            title = { Text("Revert to Version?") },
            text = {
                Column {
                    Text("This will replace all current data with the snapshot from:")
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = version.description,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = version.timestamp,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "This action cannot be undone. A new version snapshot will be created before reverting.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        // Record current state before revert
                        appViewModel.db.recordVersion(
                            description = "Pre-revert snapshot",
                            changeType = ChangeType.EDIT,
                            changedRecords = appViewModel.personCount
                        )
                        // Re-import from snapshot
                        val snapshot = version.gedcomSnapshot
                        if (snapshot.isNotEmpty()) {
                            try {
                                val result = GedcomParser.parse(snapshot)
                                appViewModel.db.importParseResult(result)
                                appViewModel.db.recordVersion(
                                    description = "Reverted to: ${version.description}",
                                    changeType = ChangeType.EDIT,
                                    changedRecords = result.persons.size
                                )
                                appViewModel.refreshCounts()
                                versions = appViewModel.db.fetchAllVersions()
                            } catch (_: Exception) {
                                // Revert failed - snapshot may be invalid
                            }
                        }
                        showRevertDialog = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Revert")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showRevertDialog = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun VersionCard(
    version: TreeVersion,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onRevert: () -> Unit
) {
    val typeColor = when (version.changeType) {
        ChangeType.IMPORT -> StatPeopleColor
        ChangeType.EDIT -> OverviewIconColor
        ChangeType.MERGE -> MergeIconColor
        ChangeType.DELETE -> CriticalColor
        ChangeType.EXPORT -> SourcesIconColor
        ChangeType.BULK_FIX -> ValidationIconColor
    }

    Surface(
        onClick = onSelect,
        shape = RoundedCornerShape(12.dp),
        tonalElevation = if (isSelected) 3.dp else 1.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = version.displayIcon,
                        fontSize = 20.sp,
                        color = typeColor
                    )
                    Column {
                        Text(
                            text = version.description,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(
                                text = version.displayType,
                                fontSize = 12.sp,
                                color = typeColor,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "${version.changedRecords} records",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                Text(
                    text = formatTimestamp(version.timestamp),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (isSelected && version.gedcomSnapshot.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    OutlinedButton(onClick = onRevert) {
                        Text("Revert to This Version")
                    }
                }
            }
        }
    }
}

private fun formatTimestamp(iso: String): String {
    // Simple display: show date portion
    return if (iso.length >= 10) iso.substring(0, 10) else iso
}
