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
import com.gedfix.services.CloudProvider
import com.gedfix.services.CloudStorageService
import com.gedfix.services.GitSyncService
import com.gedfix.ui.theme.*
import com.gedfix.viewmodel.AppViewModel

/**
 * Cloud sync and backup configuration screen.
 * Supports cloud storage providers and optional git sync for power users.
 */
@Composable
fun CloudSyncScreen(appViewModel: AppViewModel) {
    val cloudService = remember {
        CloudStorageService(appViewModel.db).also {
            it.loadSettings(appViewModel.db.getAllSettings())
        }
    }

    var selectedProvider by remember { mutableStateOf(cloudService.activeProvider) }
    var customPath by remember { mutableStateOf(cloudService.customPath) }
    var autoSync by remember { mutableStateOf(cloudService.autoSync) }
    var autoBackup by remember { mutableStateOf(cloudService.autoBackup) }
    var testResult by remember { mutableStateOf<Pair<Boolean, String>?>(null) }
    var backupResult by remember { mutableStateOf<String?>(null) }
    var gitAvailable by remember { mutableStateOf(GitSyncService.isAvailable()) }
    var gitRepoPath by remember { mutableStateOf(appViewModel.db.getSetting("gitRepoPath") ?: "") }
    var gitAutoCommit by remember { mutableStateOf(appViewModel.db.getSetting("gitAutoCommit")?.toBooleanStrictOrNull() ?: false) }

    fun saveCloudSettings() {
        cloudService.activeProvider = selectedProvider
        cloudService.customPath = customPath
        cloudService.autoSync = autoSync
        cloudService.autoBackup = autoBackup
        cloudService.saveSettings()
    }

    Column(
        modifier = Modifier.fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Text("Cloud Sync & Backup", fontSize = 24.sp, fontWeight = FontWeight.Bold)

        // Cloud Provider Selection
        Surface(
            shape = RoundedCornerShape(12.dp),
            tonalElevation = 1.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    "Cloud Provider",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                CloudProvider.entries.forEach { provider ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        RadioButton(
                            selected = selectedProvider == provider,
                            onClick = {
                                selectedProvider = provider
                                saveCloudSettings()
                            }
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "${provider.icon} ${provider.displayName}",
                                fontSize = 14.sp,
                                fontWeight = if (selectedProvider == provider) FontWeight.SemiBold else FontWeight.Normal
                            )
                            Text(
                                text = provider.description,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = customPath,
                    onValueChange = {
                        customPath = it
                        saveCloudSettings()
                    },
                    label = { Text("Custom path override (optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Text(
                    text = "Current path: ${cloudService.basePath}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }

        // Connection Test & Sync
        Surface(
            shape = RoundedCornerShape(12.dp),
            tonalElevation = 1.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    "Sync & Backup",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = {
                        testResult = cloudService.testConnection()
                    }) {
                        Text("Test Connection")
                    }
                    Button(onClick = {
                        val result = cloudService.backup()
                        backupResult = result.second
                    }) {
                        Text("Backup Now")
                    }
                }

                testResult?.let { (ok, msg) ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = msg,
                        fontSize = 13.sp,
                        color = if (ok) ValidatedColor else CriticalColor,
                        fontWeight = FontWeight.Medium
                    )
                }

                backupResult?.let { msg ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = msg,
                        fontSize = 13.sp,
                        color = ValidatedColor,
                        fontWeight = FontWeight.Medium
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Auto settings
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Auto-backup on save", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        Text("Create GEDCOM backup whenever data changes", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = autoBackup, onCheckedChange = {
                        autoBackup = it
                        saveCloudSettings()
                    })
                }

                // Storage info
                Spacer(modifier = Modifier.height(8.dp))
                val mediaSize = cloudService.mediaFolderSize()
                val backupSize = cloudService.backupFolderSize()
                val backups = cloudService.listBackups()
                Text(
                    "Media folder: ${CloudStorageService.formatBytes(mediaSize)}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "Backups: ${backups.size} files (${CloudStorageService.formatBytes(backupSize)})",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Git Sync (Power Users)
        Surface(
            shape = RoundedCornerShape(12.dp),
            tonalElevation = 1.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Git Sync (Power Users)",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = if (gitAvailable) "git available" else "git not found",
                        fontSize = 12.sp,
                        color = if (gitAvailable) ValidatedColor else WarningColor
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (!gitAvailable) {
                    Text(
                        "Git is not available on your system. Install git to enable version control sync.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    OutlinedTextField(
                        value = gitRepoPath,
                        onValueChange = {
                            gitRepoPath = it
                            appViewModel.db.setSetting("gitRepoPath", it)
                        },
                        label = { Text("Git repository path") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Auto-commit on changes", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                            Text("Automatically git commit when tree data changes", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = gitAutoCommit, onCheckedChange = {
                            gitAutoCommit = it
                            appViewModel.db.setSetting("gitAutoCommit", it.toString())
                        })
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (gitRepoPath.isNotEmpty()) {
                            val isRepo = GitSyncService.isRepo(gitRepoPath)
                            if (!isRepo) {
                                OutlinedButton(onClick = {
                                    GitSyncService.init(gitRepoPath)
                                    GitSyncService.writeGitignore(gitRepoPath)
                                }) {
                                    Text("Init Repository")
                                }
                            } else {
                                OutlinedButton(onClick = {
                                    GitSyncService.commit(gitRepoPath, "GedFix: manual sync")
                                }) {
                                    Text("Commit Now")
                                }
                                if (GitSyncService.hasRemote(gitRepoPath)) {
                                    OutlinedButton(onClick = {
                                        GitSyncService.push(gitRepoPath)
                                    }) {
                                        Text("Push")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}
