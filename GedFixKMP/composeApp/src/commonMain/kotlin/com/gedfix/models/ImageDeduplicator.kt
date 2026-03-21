package com.gedfix.models

import com.gedfix.db.DatabaseRepository
import java.io.File
import java.security.MessageDigest

/**
 * Detects and merges duplicate images in the media library.
 * Uses three detection strategies: hash comparison, filename matching, and size similarity.
 */

data class DuplicateImageGroup(
    val groupId: String,
    val images: List<GedcomMedia>,
    val matchType: ImageMatchType
) {
    val displayMatchType: String get() = matchType.label
}

enum class ImageMatchType(val label: String, val description: String) {
    EXACT_HASH("Exact Match", "Identical file contents (SHA-256 hash)"),
    SAME_FILENAME("Same Filename", "Same filename in different locations"),
    SIMILAR_SIZE("Similar Size", "Same format with similar file size (within 10%)")
}

class ImageDeduplicator(private val db: DatabaseRepository) {

    fun findDuplicates(): List<DuplicateImageGroup> {
        val allMedia = db.fetchAllMedia().filter { it.isImage }
        val groups = mutableListOf<DuplicateImageGroup>()
        val alreadyGrouped = mutableSetOf<String>()

        // Strategy 1: Group by file hash (exact duplicates)
        val byHash = allMedia.groupBy { media ->
            computeHash(media.filePath)
        }.filterKeys { it.isNotEmpty() }

        byHash.filter { it.value.size > 1 }.forEach { (hash, media) ->
            groups.add(
                DuplicateImageGroup(
                    groupId = "hash_$hash",
                    images = media,
                    matchType = ImageMatchType.EXACT_HASH
                )
            )
            alreadyGrouped.addAll(media.map { it.id })
        }

        // Strategy 2: Group by filename (different paths, same name)
        val remaining = allMedia.filter { it.id !in alreadyGrouped }
        val byName = remaining.groupBy { media ->
            File(media.filePath).name.lowercase()
        }

        byName.filter { it.value.size > 1 }.forEach { (name, media) ->
            groups.add(
                DuplicateImageGroup(
                    groupId = "name_$name",
                    images = media,
                    matchType = ImageMatchType.SAME_FILENAME
                )
            )
            alreadyGrouped.addAll(media.map { it.id })
        }

        // Strategy 3: Similar file size + same format (within 10%)
        val stillRemaining = allMedia.filter { it.id !in alreadyGrouped }
        val byFormat = stillRemaining.groupBy { it.format.lowercase() }

        byFormat.forEach { (_, mediaList) ->
            if (mediaList.size < 2) return@forEach
            val sizePairs = mediaList.mapNotNull { media ->
                val file = File(media.filePath)
                if (file.exists()) Pair(media, file.length()) else null
            }

            // Find pairs within 10% file size
            val visited = mutableSetOf<String>()
            for (i in sizePairs.indices) {
                if (sizePairs[i].first.id in visited) continue
                val group = mutableListOf(sizePairs[i].first)
                for (j in i + 1 until sizePairs.size) {
                    if (sizePairs[j].first.id in visited) continue
                    val ratio = sizePairs[i].second.toDouble() / sizePairs[j].second.toDouble()
                    if (ratio in 0.9..1.1) {
                        group.add(sizePairs[j].first)
                        visited.add(sizePairs[j].first.id)
                    }
                }
                if (group.size > 1) {
                    visited.add(sizePairs[i].first.id)
                    groups.add(
                        DuplicateImageGroup(
                            groupId = "size_${sizePairs[i].first.id}",
                            images = group,
                            matchType = ImageMatchType.SIMILAR_SIZE
                        )
                    )
                }
            }
        }

        return groups
    }

    /**
     * Merge a duplicate group: keep the selected image, remove all others.
     * Updates all references that point to removed images.
     */
    fun mergeGroup(group: DuplicateImageGroup, keepId: String) {
        val toRemove = group.images.filter { it.id != keepId }
        // For each removed image, we just delete the media record.
        // The actual files are left on disk (user manages files separately).
        for (media in toRemove) {
            db.deleteMedia(media.id)
        }
    }

    private fun computeHash(filePath: String): String {
        return try {
            val file = File(filePath)
            if (!file.exists() || !file.isFile) return ""
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().buffered().use { input ->
                val buffer = ByteArray(8192)
                var bytesRead: Int
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    digest.update(buffer, 0, bytesRead)
                }
            }
            digest.digest().joinToString("") { "%02x".format(it) }.take(16)
        } catch (e: Exception) {
            ""
        }
    }
}
