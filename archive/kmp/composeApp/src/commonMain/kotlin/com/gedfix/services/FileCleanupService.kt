package com.gedfix.services

import com.gedfix.db.DatabaseRepository
import com.gedfix.models.DuplicateImageGroup
import com.gedfix.models.GedcomMedia
import com.gedfix.models.ImageDeduplicator
import java.io.File

/**
 * Analyzes the tree database for cleanup opportunities:
 * orphaned media, unreferenced files, empty records, and duplicates.
 */

data class CleanupReport(
    val orphanedMedia: List<GedcomMedia>,       // media in DB but file doesn't exist
    val unreferencedFiles: List<String>,          // files on disk not referenced in DB
    val duplicateGroups: List<DuplicateImageGroup>,
    val emptyPersons: List<String>,               // person xrefs with no events and no family links
    val totalSavingsEstimate: Long                // bytes that could be freed
) {
    val totalIssues: Int
        get() = orphanedMedia.size + unreferencedFiles.size + duplicateGroups.size + emptyPersons.size

    val hasIssues: Boolean get() = totalIssues > 0
}

class FileCleanupService(private val db: DatabaseRepository) {

    /**
     * Analyze the database and filesystem for cleanup opportunities.
     */
    fun analyze(): CleanupReport {
        val orphaned = findOrphanedMedia()
        val unreferenced = findUnreferencedFiles()
        val duplicates = ImageDeduplicator(db).findDuplicates()
        val empty = findEmptyPersons()
        val savings = estimateSavings(orphaned, unreferenced, duplicates)

        return CleanupReport(
            orphanedMedia = orphaned,
            unreferencedFiles = unreferenced,
            duplicateGroups = duplicates,
            emptyPersons = empty,
            totalSavingsEstimate = savings
        )
    }

    /**
     * Find media records in DB where the file no longer exists on disk.
     */
    fun findOrphanedMedia(): List<GedcomMedia> {
        return db.fetchAllMedia().filter { media ->
            media.filePath.isNotEmpty() && !File(media.filePath).exists()
        }
    }

    /**
     * Find empty persons: no events, not in any family as spouse or child.
     */
    fun findEmptyPersons(): List<String> {
        val allPersons = db.fetchAllPersons()
        return allPersons.filter { person ->
            val events = db.fetchEvents(person.xref)
            val spouseFamilies = db.fetchFamiliesAsSpouse(person.xref)
            val childFamilies = db.fetchFamiliesAsChild(person.xref)
            events.isEmpty() && spouseFamilies.isEmpty() && childFamilies.isEmpty() &&
                    person.givenName.isEmpty() && person.surname.isEmpty()
        }.map { it.xref }
    }

    /**
     * Clean orphaned media records (DB records with no file).
     */
    fun cleanOrphanedMedia(): Int {
        val orphaned = findOrphanedMedia()
        orphaned.forEach { db.deleteMedia(it.id) }
        return orphaned.size
    }

    /**
     * Clean empty person records (no events, no families, no name).
     */
    fun cleanEmptyPersons(): Int {
        val empty = findEmptyPersons()
        empty.forEach { db.deletePerson(it) }
        return empty.size
    }

    private fun findUnreferencedFiles(): List<String> {
        // This would require knowing the media base directory
        // For now, return empty - this can be configured later
        return emptyList()
    }

    private fun estimateSavings(
        orphaned: List<GedcomMedia>,
        unreferenced: List<String>,
        duplicates: List<DuplicateImageGroup>
    ): Long {
        var total = 0L
        // Estimate based on duplicate files that could be removed
        for (group in duplicates) {
            val fileSizes = group.images.mapNotNull { media ->
                val file = File(media.filePath)
                if (file.exists()) file.length() else null
            }
            if (fileSizes.size > 1) {
                // All but the largest could be removed
                total += fileSizes.sorted().dropLast(1).sum()
            }
        }
        // Unreferenced file sizes
        for (path in unreferenced) {
            val file = File(path)
            if (file.exists()) total += file.length()
        }
        return total
    }
}
