package com.gedfix.services

import com.gedfix.db.DatabaseRepository
import com.gedfix.models.GedcomExporter
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Cloud storage integration for syncing media files and backups.
 * Works with local filesystem cloud sync folders (iCloud Drive, Google Drive, Dropbox, OneDrive).
 * Does NOT use cloud APIs directly - relies on desktop sync clients.
 */

enum class CloudProvider(
    val displayName: String,
    val icon: String,
    val defaultPath: String,
    val description: String
) {
    ICLOUD(
        "iCloud Drive",
        "\u2601",
        "~/Library/Mobile Documents/com~apple~CloudDocs/GedFix/",
        "Apple ecosystem. Auto-syncs on Mac/iPhone/iPad."
    ),
    GOOGLE_DRIVE(
        "Google Drive",
        "\u2601",
        "~/Google Drive/GedFix/",
        "Cross-platform. 15GB free."
    ),
    DROPBOX(
        "Dropbox",
        "\u2601",
        "~/Dropbox/GedFix/",
        "Reliable sync. 2GB free."
    ),
    ONEDRIVE(
        "OneDrive",
        "\u2601",
        "~/OneDrive/GedFix/",
        "Microsoft ecosystem. 5GB free."
    ),
    LOCAL(
        "Local Folder",
        "\uD83D\uDCC1",
        "~/Documents/GedFix/",
        "No cloud sync. Local storage only."
    );

    fun resolvedPath(): String = defaultPath.replace("~", System.getProperty("user.home"))
}

data class SyncResult(
    val filesTransferred: Int,
    val totalSize: Long,
    val success: Boolean,
    val log: String
)

class CloudStorageService(private val db: DatabaseRepository) {
    var activeProvider: CloudProvider = CloudProvider.LOCAL
    var customPath: String = ""
    var autoSync: Boolean = false
    var autoBackup: Boolean = false

    val basePath: String
        get() {
            val path = if (customPath.isNotEmpty()) customPath else activeProvider.resolvedPath()
            return if (path.endsWith("/")) path else "$path/"
        }

    val mediaPath: String get() = "${basePath}media/"
    val backupPath: String get() = "${basePath}backups/"

    fun loadSettings(settings: Map<String, String>) {
        activeProvider = settings["cloudProvider"]?.let { name ->
            CloudProvider.entries.firstOrNull { it.name == name }
        } ?: CloudProvider.LOCAL
        customPath = settings["cloudCustomPath"] ?: ""
        autoSync = settings["cloudAutoSync"]?.toBooleanStrictOrNull() ?: false
        autoBackup = settings["cloudAutoBackup"]?.toBooleanStrictOrNull() ?: false
    }

    fun saveSettings() {
        db.setSetting("cloudProvider", activeProvider.name)
        db.setSetting("cloudCustomPath", customPath)
        db.setSetting("cloudAutoSync", autoSync.toString())
        db.setSetting("cloudAutoBackup", autoBackup.toString())
    }

    /**
     * Verify that the target folder exists and is writable.
     */
    fun testConnection(): Pair<Boolean, String> {
        return try {
            val dir = File(basePath)
            if (!dir.exists()) {
                dir.mkdirs()
            }
            if (dir.canWrite()) {
                Pair(true, "Folder exists and is writable: $basePath")
            } else {
                Pair(false, "Folder is not writable: $basePath")
            }
        } catch (e: Exception) {
            Pair(false, "Error: ${e.message}")
        }
    }

    /**
     * Export GEDCOM backup to the backup path with timestamp.
     */
    fun backup(): Pair<Boolean, String> {
        return try {
            val dir = File(backupPath)
            if (!dir.exists()) dir.mkdirs()

            val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
            val filename = "gedfix_backup_$timestamp.ged"
            val exporter = GedcomExporter(db)
            val gedcom = exporter.export()

            File(backupPath, filename).writeText(gedcom)
            Pair(true, "Backup saved: $filename (${gedcom.length} chars)")
        } catch (e: Exception) {
            Pair(false, "Backup failed: ${e.message}")
        }
    }

    /**
     * Get size of the media folder in bytes.
     */
    fun mediaFolderSize(): Long {
        val dir = File(mediaPath)
        if (!dir.exists()) return 0L
        return dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    }

    /**
     * Get size of the backup folder in bytes.
     */
    fun backupFolderSize(): Long {
        val dir = File(backupPath)
        if (!dir.exists()) return 0L
        return dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    }

    /**
     * List all backup files.
     */
    fun listBackups(): List<File> {
        val dir = File(backupPath)
        if (!dir.exists()) return emptyList()
        return dir.listFiles()?.filter { it.extension == "ged" }?.sortedByDescending { it.lastModified() }
            ?: emptyList()
    }

    companion object {
        fun formatBytes(bytes: Long): String {
            return when {
                bytes < 1024 -> "$bytes B"
                bytes < 1024 * 1024 -> "${bytes / 1024} KB"
                bytes < 1024 * 1024 * 1024 -> "${"%.1f".format(bytes / (1024.0 * 1024.0))} MB"
                else -> "${"%.2f".format(bytes / (1024.0 * 1024.0 * 1024.0))} GB"
            }
        }
    }
}
