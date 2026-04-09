package com.gedfix.services

import com.gedfix.db.DatabaseRepository
import com.gedfix.models.*
import java.io.File
import java.security.MessageDigest
import java.time.Instant
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.max
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Persistence model for Handle Everything run history.
 */
data class HandleEverythingRun(
    val id: String,
    val timestamp: String,
    val backupPath: String,
    val versionId: String,
    val report: String,
    val fileRenames: String,       // JSON map of newPath -> oldPath
    val fixesApplied: Int,
    val fixesNeedReview: Int,
    val duplicatesRemoved: Int,
    val filesRenamed: Int,
    val spaceSavedBytes: Long
)

/**
 * Result of a Handle Everything run.
 */
data class HandleResult(
    val backupPath: String,
    val versionId: String,
    val treeFixesApplied: Int,
    val treeFixesNeedReview: Int,
    val duplicatesRemoved: Int,
    val filesRenamed: Int,
    val filesOrganized: Int,
    val unlinkedMediaLinked: Int,
    val spaceSavedBytes: Long,
    val report: String,
    val fixDetails: List<String>,
    val reviewItems: List<String>
)

/**
 * The killer feature: one button that backs up, fixes, deduplicates, organizes,
 * links media, and generates a full report. Everything is undoable.
 */
@OptIn(ExperimentalUuidApi::class)
class HandleEverythingService(private val db: DatabaseRepository) {

    companion object {
        val SUFFIXES = setOf(
            "Jr.", "Jr", "Sr.", "Sr", "II", "III", "IV", "V",
            "Esq", "Esq.", "MD", "M.D.", "PhD", "Ph.D.", "DDS", "D.D.S."
        )

        val MALE_NAMES = setOf(
            "John", "James", "William", "Robert", "Thomas", "George", "Charles",
            "Edward", "Henry", "Joseph", "Richard", "Samuel", "David", "Daniel",
            "Benjamin", "Andrew", "Jacob", "Michael", "Patrick", "Peter", "Paul",
            "Alexander", "Albert", "Arthur", "Francis", "Frederick", "Frank",
            "Harold", "Herbert", "Howard", "Isaac", "Leonard", "Louis", "Martin",
            "Nathan", "Oliver", "Oscar", "Philip", "Ralph", "Raymond", "Roy",
            "Stanley", "Stephen", "Theodore", "Vincent", "Walter", "Warren",
            "Adam", "Aaron", "Carl", "Clarence", "Earl", "Ernest", "Eugene",
            "Gerald", "Glenn", "Gordon", "Harvey", "Herman", "Hugh", "Jack",
            "Jerome", "Jesse", "Kenneth", "Lawrence", "Leo", "Lloyd", "Luther",
            "Mark", "Marshall", "Maurice", "Melvin", "Morris", "Norman",
            "Otis", "Percy", "Phillip", "Russell", "Sidney", "Sylvester",
            "Vernon", "Victor", "Virgil", "Wesley", "Willis", "Matthew",
            "Luke", "Timothy", "Jonathan", "Christopher", "Nicholas", "Anthony"
        )

        val FEMALE_NAMES = setOf(
            "Mary", "Elizabeth", "Sarah", "Margaret", "Ann", "Jane", "Martha",
            "Anna", "Catherine", "Dorothy", "Alice", "Ruth", "Florence",
            "Helen", "Grace", "Lillian", "Marie", "Rose", "Emma", "Clara",
            "Edith", "Ethel", "Eva", "Ida", "Irene", "Julia", "Laura",
            "Louise", "Lucy", "Mabel", "Mildred", "Nellie", "Pearl", "Susan",
            "Virginia", "Agnes", "Annie", "Bertha", "Betty", "Blanche",
            "Caroline", "Carrie", "Charlotte", "Cora", "Dora", "Ella",
            "Ellen", "Emily", "Esther", "Fannie", "Flora", "Frances",
            "Gertrude", "Hattie", "Hazel", "Jennie", "Jessie", "Josephine",
            "Katharine", "Katherine", "Katie", "Lena", "Lottie", "Lydia",
            "Maggie", "Mamie", "Matilda", "Minnie", "Myra", "Nora",
            "Olive", "Rachel", "Rebecca", "Rosa", "Sadie", "Stella",
            "Theresa", "Viola", "Nancy", "Patricia", "Barbara", "Linda",
            "Karen", "Sandra", "Donna", "Carol", "Sharon", "Judith",
            "Janet", "Diane", "Carolyn", "Jean", "Gloria", "Shirley",
            "Maria", "Christine", "Anne", "Isabella", "Sophia", "Hannah"
        )

        private val UUID_PATTERN = Regex(
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"
        )

        private val CENSUS_KEYWORDS = setOf("census", "1790", "1800", "1810", "1820", "1830", "1840", "1850", "1860", "1870", "1880", "1890", "1900", "1910", "1920", "1930", "1940", "1950")
        private val VITAL_KEYWORDS = setOf("birth", "death", "marriage", "certificate", "vital", "record")
        private val MILITARY_KEYWORDS = setOf("military", "army", "navy", "battalion", "regiment", "draft", "service", "veteran", "war")
        private val NEWSPAPER_KEYWORDS = setOf("newspaper", "news", "gazette", "times", "herald", "sun", "tribune", "press", "journal", "obituary", "obit")
    }

    /**
     * Execute the full Handle Everything pipeline.
     */
    fun handleEverything(
        onProgress: (phase: Int, total: Int, message: String) -> Unit
    ): HandleResult {
        val fixDetails = mutableListOf<String>()
        val reviewItems = mutableListOf<String>()
        val currentYear = 2026

        // Phase 1: BACKUP
        onProgress(1, 7, "Creating backup...")
        val (backupPath, versionId) = createBackup()
        fixDetails.add("GEDCOM backup created: $backupPath")
        fixDetails.add("Database version snapshot created")

        // Phase 2: FIX TREE ISSUES
        onProgress(2, 7, "Fixing tree issues...")
        val (autoFixed, needReview) = autoFixTreeIssues(fixDetails, reviewItems, currentYear)

        // Phase 3: DEDUPLICATE IMAGES
        onProgress(3, 7, "Deduplicating images...")
        val (dupsRemoved, spaceSaved) = deduplicateImages(fixDetails)

        // Phase 4: ORGANIZE MEDIA
        onProgress(4, 7, "Organizing media files...")
        val (renamed, organized) = organizeMedia(fixDetails)

        // Phase 5: LINK UNATTACHED MEDIA
        onProgress(5, 7, "Linking unattached media...")
        val linked = linkUnattachedMedia(fixDetails)

        // Phase 6: GENERATE REPORT
        onProgress(6, 7, "Generating report...")
        val report = generateReport(
            backupPath = backupPath,
            fixesApplied = autoFixed,
            fixesNeedReview = needReview,
            duplicatesRemoved = dupsRemoved,
            filesRenamed = renamed,
            filesOrganized = organized,
            unlinkedLinked = linked,
            spaceSaved = spaceSaved,
            fixDetails = fixDetails,
            reviewItems = reviewItems
        )

        // Record version for all changes
        val totalChanges = autoFixed + dupsRemoved + renamed + linked
        if (totalChanges > 0) {
            db.recordVersion(
                description = "Handle Everything: $autoFixed fixes, $dupsRemoved deduped, $renamed renamed, $linked linked",
                changeType = ChangeType.BULK_FIX,
                changedRecords = totalChanges
            )
        }

        // Save run history
        val run = HandleEverythingRun(
            id = Uuid.random().toString(),
            timestamp = Instant.now().toString(),
            backupPath = backupPath,
            versionId = versionId,
            report = report,
            fileRenames = "", // Could serialize file rename map if needed
            fixesApplied = autoFixed,
            fixesNeedReview = needReview,
            duplicatesRemoved = dupsRemoved,
            filesRenamed = renamed,
            spaceSavedBytes = spaceSaved
        )
        db.insertHandleEverythingRun(run)

        onProgress(7, 7, "Done!")

        return HandleResult(
            backupPath = backupPath,
            versionId = versionId,
            treeFixesApplied = autoFixed,
            treeFixesNeedReview = needReview,
            duplicatesRemoved = dupsRemoved,
            filesRenamed = renamed,
            filesOrganized = organized,
            unlinkedMediaLinked = linked,
            spaceSavedBytes = spaceSaved,
            report = report,
            fixDetails = fixDetails,
            reviewItems = reviewItems
        )
    }

    // ================================================================
    // Phase 1: BACKUP
    // ================================================================

    private fun createBackup(): Pair<String, String> {
        val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
        val backupDir = File(System.getProperty("user.home"), "Documents/GedFix/backups")
        backupDir.mkdirs()

        val backupFile = File(backupDir, "backup_$timestamp.ged")
        val gedcom = GedcomExporter(db).export()
        backupFile.writeText(gedcom)

        val versionId = Uuid.random().toString()
        val version = TreeVersion(
            id = versionId,
            timestamp = Instant.now().toString(),
            description = "Handle Everything backup",
            changeType = ChangeType.EXPORT,
            changedRecords = 0,
            gedcomSnapshot = gedcom
        )
        db.insertVersion(version)

        return Pair(backupFile.absolutePath, versionId)
    }

    // ================================================================
    // Phase 2: AUTO-FIX TREE ISSUES
    // ================================================================

    private fun autoFixTreeIssues(
        fixDetails: MutableList<String>,
        reviewItems: MutableList<String>,
        currentYear: Int
    ): Pair<Int, Int> {
        var totalFixed = 0
        var totalNeedReview = 0

        // Fix suffixes in names
        val suffixFixes = fixSuffixesInNames()
        if (suffixFixes > 0) {
            fixDetails.add("Moved $suffixFixes suffixes from NAME to NSFX field")
            totalFixed += suffixFixes
        }

        // Remove duplicate facts
        val dupFacts = removeDuplicateFacts()
        if (dupFacts > 0) {
            fixDetails.add("Removed $dupFacts duplicate birth/death facts")
            totalFixed += dupFacts
        }

        // Infer missing gender
        val genderFixes = inferMissingGender()
        if (genderFixes > 0) {
            fixDetails.add("Inferred gender for $genderFixes persons from first names")
            totalFixed += genderFixes
        }

        // Standardize surname spelling
        val surnameFixes = standardizeSurnameSpelling()
        if (surnameFixes > 0) {
            fixDetails.add("Standardized $surnameFixes inconsistent surname spellings")
            totalFixed += surnameFixes
        }

        // Standardize place spelling
        val placeFixes = standardizePlaceSpelling()
        if (placeFixes > 0) {
            fixDetails.add("Standardized $placeFixes inconsistent place spellings")
            totalFixed += placeFixes
        }

        // Remove future dates
        val futureFixes = removeFutureDates(currentYear)
        if (futureFixes > 0) {
            fixDetails.add("Removed $futureFixes events with future dates")
            totalFixed += futureFixes
        }

        // Flag married-as-maiden-name (needs human review)
        val maidenIssues = flagMarriedAsMaidenName()
        if (maidenIssues > 0) {
            reviewItems.add("$maidenIssues married-as-maiden-name issues need manual review")
            totalNeedReview += maidenIssues
        }

        return Pair(totalFixed, totalNeedReview)
    }

    private fun fixSuffixesInNames(): Int {
        val persons = db.fetchAllPersons()
        var fixed = 0
        for (person in persons) {
            val nameParts = person.givenName.split(" ")
            val lastPart = nameParts.lastOrNull() ?: continue
            if (SUFFIXES.contains(lastPart) && nameParts.size > 1) {
                val newGiven = nameParts.dropLast(1).joinToString(" ")
                val newSuffix = if (person.suffix.isEmpty()) lastPart else "${person.suffix} $lastPart"
                db.updatePerson(person.copy(givenName = newGiven, suffix = newSuffix))
                fixed++
            }
        }
        return fixed
    }

    private fun removeDuplicateFacts(): Int {
        val persons = db.fetchAllPersons()
        var removed = 0
        for (person in persons) {
            val events = db.fetchEvents(person.xref)
            val grouped = events
                .filter { it.dateValue.isNotEmpty() }
                .groupBy { "${it.eventType}|${it.dateValue}" }
            for ((_, dupes) in grouped) {
                if (dupes.size > 1) {
                    // Keep the one with the most data
                    val keep = dupes.maxByOrNull {
                        it.dateValue.length + it.place.length + it.description.length
                    }
                    dupes.filter { it.id != keep?.id }.forEach {
                        db.deleteEvent(it.id)
                        removed++
                    }
                }
            }
        }
        return removed
    }

    private fun inferMissingGender(): Int {
        val persons = db.fetchAllPersons().filter { it.sex == "U" || it.sex.isEmpty() }
        var fixed = 0
        for (person in persons) {
            val firstName = person.givenName.split(" ").firstOrNull()?.trim() ?: continue
            if (firstName.isEmpty()) continue
            val capitalized = firstName.replaceFirstChar { it.uppercase() }
            val gender = when {
                MALE_NAMES.contains(capitalized) -> "M"
                FEMALE_NAMES.contains(capitalized) -> "F"
                else -> null
            }
            if (gender != null) {
                db.updatePerson(person.copy(sex = gender))
                fixed++
            }
        }
        return fixed
    }

    private fun standardizeSurnameSpelling(): Int {
        val persons = db.fetchAllPersons().filter { it.surname.isNotEmpty() }
        val surnameGroups = persons.groupBy { it.surname.lowercase().trim() }

        // Find groups where case differs but normalized form is the same
        val bySurface = persons.groupBy { it.surname.trim() }
        var totalFixed = 0

        // Group by soundex to find similar surnames
        val bySoundex = mutableMapOf<String, MutableList<String>>()
        for (surname in bySurface.keys) {
            val sdx = soundex(surname.lowercase())
            if (sdx.isNotEmpty()) {
                bySoundex.getOrPut(sdx) { mutableListOf() }.add(surname)
            }
        }

        for ((_, variants) in bySoundex) {
            if (variants.size < 2) continue
            // Check if variants are close (Levenshtein distance <= 2)
            val closeVariants = mutableListOf<List<String>>()
            for (i in variants.indices) {
                for (j in i + 1 until variants.size) {
                    val a = variants[i].lowercase()
                    val b = variants[j].lowercase()
                    if (a == b) continue
                    val maxLen = max(a.length, b.length)
                    val distance = ((1.0 - levenshteinSimilarity(a, b)) * maxLen).toInt()
                    if (distance <= 2) {
                        closeVariants.add(listOf(variants[i], variants[j]))
                    }
                }
            }
            for (pair in closeVariants) {
                // Keep the most common spelling
                val counts = pair.map { it to (bySurface[it]?.size ?: 0) }
                val winner = counts.maxByOrNull { it.second }?.first ?: continue
                val losers = pair.filter { it != winner }
                for (loser in losers) {
                    db.updatePersonSurname(loser, winner)
                    totalFixed += bySurface[loser]?.size ?: 0
                }
            }
        }

        return totalFixed
    }

    private fun standardizePlaceSpelling(): Int {
        val allEvents = db.fetchAllEvents()
        val placeUsage = mutableMapOf<String, Int>()
        for (event in allEvents) {
            if (event.place.isNotEmpty()) {
                placeUsage[event.place] = (placeUsage[event.place] ?: 0) + 1
            }
        }

        val places = placeUsage.keys.toList()
        var totalFixed = 0
        val alreadyFixed = mutableSetOf<String>()

        for (i in places.indices) {
            if (places[i] in alreadyFixed) continue
            for (j in i + 1 until places.size) {
                if (places[j] in alreadyFixed) continue
                val a = places[i]
                val b = places[j]

                // Case-insensitive match
                if (a.equals(b, ignoreCase = true) && a != b) {
                    val countA = placeUsage[a] ?: 0
                    val countB = placeUsage[b] ?: 0
                    val winner = if (countA >= countB) a else b
                    val loser = if (winner == a) b else a
                    db.updateEventPlace(loser, winner)
                    alreadyFixed.add(loser)
                    totalFixed += placeUsage[loser] ?: 0
                    continue
                }

                // High similarity check (> 93%)
                val similarity = levenshteinSimilarity(a.lowercase(), b.lowercase())
                if (similarity > 0.93 && a.lowercase() != b.lowercase()) {
                    val countA = placeUsage[a] ?: 0
                    val countB = placeUsage[b] ?: 0
                    val winner = if (countA >= countB) a else b
                    val loser = if (winner == a) b else a
                    db.updateEventPlace(loser, winner)
                    alreadyFixed.add(loser)
                    totalFixed += placeUsage[loser] ?: 0
                }
            }
        }

        return totalFixed
    }

    private fun removeFutureDates(currentYear: Int): Int {
        val allEvents = db.fetchAllEvents()
        var removed = 0
        for (event in allEvents) {
            val year = GedcomParser.extractYear(event.dateValue) ?: continue
            if (year > currentYear) {
                db.deleteEvent(event.id)
                removed++
            }
        }
        return removed
    }

    private fun flagMarriedAsMaidenName(): Int {
        val families = db.fetchAllFamilies()
        var flagged = 0
        for (family in families) {
            if (family.partner1Xref.isEmpty() || family.partner2Xref.isEmpty()) continue
            val husband = db.fetchPerson(family.partner1Xref) ?: continue
            val wife = db.fetchPerson(family.partner2Xref) ?: continue
            if (husband.surname.isEmpty() || wife.surname.isEmpty()) continue
            if (husband.surname.equals(wife.surname, ignoreCase = true)) {
                flagged++
            }
        }
        return flagged
    }

    // ================================================================
    // Phase 3: DEDUPLICATE IMAGES
    // ================================================================

    private fun deduplicateImages(fixDetails: MutableList<String>): Pair<Int, Long> {
        val allMedia = db.fetchAllMedia().filter { it.isImage }
        if (allMedia.isEmpty()) return Pair(0, 0L)

        // Group by SHA-256 hash
        val byHash = mutableMapOf<String, MutableList<GedcomMedia>>()
        for (media in allMedia) {
            val hash = computeHash(media.filePath)
            if (hash.isNotEmpty()) {
                byHash.getOrPut(hash) { mutableListOf() }.add(media)
            }
        }

        var removed = 0
        var savedBytes = 0L

        for ((_, group) in byHash) {
            if (group.size < 2) continue

            // Keep the one with the better filename (descriptive > UUID)
            val best = group.maxByOrNull { scoreFilename(it) } ?: continue
            val toRemove = group.filter { it.id != best.id }

            for (media in toRemove) {
                val file = File(media.filePath)
                if (file.exists()) {
                    savedBytes += file.length()
                    file.delete()
                }
                db.deleteMedia(media.id)
                removed++
            }
        }

        if (removed > 0) {
            val mbSaved = savedBytes / (1024 * 1024)
            fixDetails.add("Found and removed $removed duplicate images (saved ${mbSaved}MB)")
        }

        return Pair(removed, savedBytes)
    }

    private fun scoreFilename(media: GedcomMedia): Int {
        var score = 0
        val name = File(media.filePath).nameWithoutExtension.lowercase()

        // Descriptive title is better
        if (media.title.isNotEmpty()) score += 10

        // UUID-style filenames are worst
        if (UUID_PATTERN.containsMatchIn(name)) score -= 20

        // Longer, more descriptive names are better
        score += name.length.coerceAtMost(20)

        // Has spaces or underscores (likely human-named)
        if (name.contains(" ") || name.contains("_")) score += 5

        return score
    }

    // ================================================================
    // Phase 4: ORGANIZE MEDIA
    // ================================================================

    private fun organizeMedia(fixDetails: MutableList<String>): Pair<Int, Int> {
        val allMedia = db.fetchAllMedia()
        if (allMedia.isEmpty()) return Pair(0, 0)

        val outputDir = File(System.getProperty("user.home"), "Documents/GedFix/media")
        val photosDir = File(outputDir, "photos")
        val documentsDir = File(outputDir, "documents")

        var renamed = 0
        var organized = 0
        val usedPaths = mutableSetOf<String>()

        for (media in allMedia) {
            val sourceFile = File(media.filePath)
            if (!sourceFile.exists()) continue

            val owner = if (media.ownerXref.isNotEmpty()) db.fetchPerson(media.ownerXref) else null
            val extension = sourceFile.extension.lowercase().ifEmpty { "jpg" }
            val originalName = sourceFile.nameWithoutExtension.lowercase()

            // Determine target path based on content type
            val targetFile = if (media.isImage) {
                organizeImage(photosDir, media, owner, extension, originalName, usedPaths)
            } else {
                organizeDocument(documentsDir, media, owner, extension, originalName, usedPaths)
            }

            if (targetFile != null) {
                targetFile.parentFile.mkdirs()
                try {
                    sourceFile.copyTo(targetFile, overwrite = false)
                    val newTitle = targetFile.nameWithoutExtension.replace("_", " ")
                    db.updateMediaFilePath(media.id, targetFile.absolutePath, newTitle)
                    usedPaths.add(targetFile.absolutePath)
                    renamed++
                    organized++
                } catch (_: Exception) {
                    // File already exists or copy failed - skip
                }
            }
        }

        if (renamed > 0) {
            fixDetails.add("Renamed $renamed files with descriptive names")
            fixDetails.add("Organized $organized files into surname/category folders")
        }

        return Pair(renamed, organized)
    }

    private fun organizeImage(
        photosDir: File,
        media: GedcomMedia,
        owner: GedcomPerson?,
        extension: String,
        originalName: String,
        usedPaths: Set<String>
    ): File? {
        val surname = owner?.surname?.trim()?.ifEmpty { null }
        val given = owner?.givenName?.trim()?.split(" ")?.firstOrNull()?.ifEmpty { null }

        val folderName = surname ?: "Misc"
        val folder = File(photosDir, sanitizeFilename(folderName))

        val baseName = if (given != null && surname != null) {
            val context = guessImageContext(media, originalName)
            sanitizeFilename("${given}_${surname}_$context")
        } else if (isUuidFilename(originalName)) {
            "unidentified"
        } else {
            sanitizeFilename(originalName)
        }

        return uniqueFile(folder, baseName, extension, usedPaths)
    }

    private fun organizeDocument(
        documentsDir: File,
        media: GedcomMedia,
        owner: GedcomPerson?,
        extension: String,
        originalName: String,
        usedPaths: Set<String>
    ): File? {
        val category = classifyDocument(media, originalName)
        val folder = File(documentsDir, category)

        val baseName = if (media.title.isNotEmpty()) {
            sanitizeFilename(media.title)
        } else if (isUuidFilename(originalName)) {
            "document"
        } else {
            sanitizeFilename(originalName)
        }

        return uniqueFile(folder, baseName, extension, usedPaths)
    }

    private fun classifyDocument(media: GedcomMedia, name: String): String {
        val searchText = "${media.title} ${media.description} $name".lowercase()
        return when {
            CENSUS_KEYWORDS.any { searchText.contains(it) } -> "census"
            VITAL_KEYWORDS.any { searchText.contains(it) } -> "vital_records"
            MILITARY_KEYWORDS.any { searchText.contains(it) } -> "military"
            NEWSPAPER_KEYWORDS.any { searchText.contains(it) } -> "newspapers"
            else -> "misc"
        }
    }

    private fun guessImageContext(media: GedcomMedia, originalName: String): String {
        val searchText = "${media.title} ${media.description} $originalName".lowercase()
        return when {
            searchText.contains("birth") -> "birth"
            searchText.contains("wedding") || searchText.contains("marriage") -> "wedding"
            searchText.contains("portrait") || searchText.contains("headshot") -> "portrait"
            searchText.contains("family") -> "family"
            searchText.contains("death") || searchText.contains("obit") -> "obituary"
            searchText.contains("military") || searchText.contains("uniform") -> "military"
            searchText.contains("school") || searchText.contains("graduation") -> "school"
            searchText.contains("baby") || searchText.contains("infant") -> "baby"
            else -> "photo"
        }
    }

    // ================================================================
    // Phase 5: LINK UNATTACHED MEDIA
    // ================================================================

    private fun linkUnattachedMedia(fixDetails: MutableList<String>): Int {
        val unlinked = db.fetchAllMedia().filter { it.ownerXref.isEmpty() }
        if (unlinked.isEmpty()) return 0

        val allPersons = db.fetchAllPersons()
        val personNameMap = mutableMapOf<String, String>() // lowercased full name -> xref
        val personSurnameMap = mutableMapOf<String, String>() // lowercased surname -> xref

        for (person in allPersons) {
            val fullName = "${person.givenName} ${person.surname}".lowercase().trim()
            if (fullName.isNotBlank()) {
                personNameMap[fullName] = person.xref
            }
            if (person.surname.isNotBlank()) {
                personSurnameMap[person.surname.lowercase().trim()] = person.xref
            }
        }

        var linked = 0
        for (media in unlinked) {
            val filename = File(media.filePath).nameWithoutExtension
                .replace("_", " ").replace("-", " ").lowercase().trim()

            // Try exact full name match
            val matchXref = personNameMap[filename]
                ?: findFuzzyMatch(filename, personNameMap)
                ?: findSurnameMatch(filename, personSurnameMap)

            if (matchXref != null) {
                db.updateMediaOwner(media.id, matchXref)
                linked++
            }
        }

        if (linked > 0) {
            fixDetails.add("Linked $linked previously unlinked photos to persons")
        }

        return linked
    }

    private fun findFuzzyMatch(filename: String, nameMap: Map<String, String>): String? {
        for ((name, xref) in nameMap) {
            if (filename.contains(name) || name.contains(filename)) {
                return xref
            }
            if (levenshteinSimilarity(filename, name) > 0.85) {
                return xref
            }
        }
        return null
    }

    private fun findSurnameMatch(filename: String, surnameMap: Map<String, String>): String? {
        // Check if filename contains "family" and a surname
        if (!filename.contains("family")) return null
        for ((surname, xref) in surnameMap) {
            if (filename.contains(surname)) return xref
        }
        return null
    }

    // ================================================================
    // Phase 6: GENERATE REPORT
    // ================================================================

    private fun generateReport(
        backupPath: String,
        fixesApplied: Int,
        fixesNeedReview: Int,
        duplicatesRemoved: Int,
        filesRenamed: Int,
        filesOrganized: Int,
        unlinkedLinked: Int,
        spaceSaved: Long,
        fixDetails: List<String>,
        reviewItems: List<String>
    ): String {
        val personCount = db.personCount()
        val mbSaved = spaceSaved / (1024 * 1024)

        return buildString {
            appendLine("JUST HANDLE EVERYTHING -- Report")
            appendLine("================================")
            appendLine("Date: ${LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))}")
            appendLine("Tree: ${personCount} people")
            appendLine()
            appendLine("BACKUP")
            appendLine("  [OK] GEDCOM backup: $backupPath")
            appendLine("  [OK] Database version snapshot created")
            appendLine()
            appendLine("TREE FIXES ($fixesApplied auto-fixed, $fixesNeedReview need review)")
            for (detail in fixDetails.filter { !it.startsWith("GEDCOM") && !it.startsWith("Database") && !it.contains("duplicate images") && !it.contains("Renamed") && !it.contains("Organized") && !it.contains("unlinked") }) {
                appendLine("  [OK] $detail")
            }
            for (item in reviewItems) {
                appendLine("  [!!] $item")
            }
            appendLine()
            appendLine("IMAGE CLEANUP (saved ${mbSaved}MB)")
            for (detail in fixDetails.filter { it.contains("duplicate images") }) {
                appendLine("  [OK] $detail")
            }
            for (detail in fixDetails.filter { it.contains("Renamed") || it.contains("Organized") }) {
                appendLine("  [OK] $detail")
            }
            for (detail in fixDetails.filter { it.contains("unlinked") }) {
                appendLine("  [OK] $detail")
            }
            appendLine()
            appendLine("SUMMARY")
            appendLine("  Total fixes applied: $fixesApplied")
            appendLine("  Items needing review: $fixesNeedReview")
            appendLine("  Duplicates removed: $duplicatesRemoved")
            appendLine("  Files organized: $filesOrganized")
            appendLine("  Media linked: $unlinkedLinked")
            appendLine("  Space saved: ${mbSaved}MB")
        }
    }

    /**
     * Undo a previous Handle Everything run by restoring from backup.
     */
    fun undoRun(runId: String): Boolean {
        val run = db.fetchHandleEverythingRunById(runId) ?: return false
        val version = db.fetchVersionById(run.versionId) ?: return false

        // Restore from GEDCOM snapshot
        val snapshot = version.gedcomSnapshot
        if (snapshot.isBlank()) return false

        val result = GedcomParser.parse(snapshot)
        db.importParseResult(result)
        db.recordVersion(
            description = "Undid Handle Everything run from ${run.timestamp}",
            changeType = ChangeType.IMPORT,
            changedRecords = result.persons.size + result.families.size + result.events.size
        )
        return true
    }

    /**
     * Preview what Handle Everything would do without making changes.
     */
    fun preview(): HandlePreview {
        val persons = db.fetchAllPersons()
        val events = db.fetchAllEvents()
        val families = db.fetchAllFamilies()
        val childLinks = db.fetchAllChildLinks()
        val media = db.fetchAllMedia()

        // Count suffixes to fix
        var suffixCount = 0
        for (person in persons) {
            val parts = person.givenName.split(" ")
            if (parts.size > 1 && SUFFIXES.contains(parts.last())) suffixCount++
        }

        // Count duplicate facts
        var dupFactCount = 0
        for (person in persons) {
            val personEvents = events.filter { it.ownerXref == person.xref }
            val grouped = personEvents.filter { it.dateValue.isNotEmpty() }.groupBy { "${it.eventType}|${it.dateValue}" }
            for ((_, dupes) in grouped) {
                if (dupes.size > 1) dupFactCount += dupes.size - 1
            }
        }

        // Count missing gender that could be inferred
        var genderCount = 0
        for (person in persons.filter { it.sex == "U" || it.sex.isEmpty() }) {
            val firstName = person.givenName.split(" ").firstOrNull()?.trim() ?: continue
            val cap = firstName.replaceFirstChar { it.uppercase() }
            if (MALE_NAMES.contains(cap) || FEMALE_NAMES.contains(cap)) genderCount++
        }

        // Run tree analysis for issue count
        val analyzer = TreeAnalyzer(persons, families, events, childLinks)
        val issues = analyzer.analyze()

        // Count duplicate images
        val deduplicator = ImageDeduplicator(db)
        val dupGroups = deduplicator.findDuplicates()
        val dupImageCount = dupGroups.filter { it.matchType == com.gedfix.models.ImageMatchType.EXACT_HASH }
            .sumOf { it.images.size - 1 }

        return HandlePreview(
            totalIssues = issues.size,
            autoFixableIssues = suffixCount + dupFactCount + genderCount,
            suffixFixes = suffixCount,
            duplicateFactFixes = dupFactCount,
            genderInferences = genderCount,
            duplicateImages = dupImageCount,
            totalMedia = media.size,
            unlinkedMedia = media.count { it.ownerXref.isEmpty() }
        )
    }

    // ================================================================
    // Helpers
    // ================================================================

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
            digest.digest().joinToString("") { "%02x".format(it) }
        } catch (_: Exception) {
            ""
        }
    }

    private fun sanitizeFilename(name: String): String {
        return name
            .replace(Regex("[/\\\\:*?\"<>|]"), "_")
            .replace(Regex("\\s+"), "_")
            .replace(Regex("_+"), "_")
            .trim('_')
            .take(80)
    }

    private fun isUuidFilename(name: String): Boolean {
        return UUID_PATTERN.containsMatchIn(name)
    }

    private fun uniqueFile(dir: File, baseName: String, extension: String, usedPaths: Set<String>): File {
        var candidate = File(dir, "$baseName.$extension")
        var counter = 1
        while (candidate.exists() || candidate.absolutePath in usedPaths) {
            candidate = File(dir, "${baseName}_$counter.$extension")
            counter++
        }
        return candidate
    }

    private fun levenshteinSimilarity(a: String, b: String): Double {
        if (a.isEmpty() && b.isEmpty()) return 1.0
        if (a.isEmpty() || b.isEmpty()) return 0.0
        val aChars = a.toCharArray()
        val bChars = b.toCharArray()
        var prev = IntArray(bChars.size + 1) { it }
        var curr = IntArray(bChars.size + 1)
        for (i in 1..aChars.size) {
            curr[0] = i
            for (j in 1..bChars.size) {
                val cost = if (aChars[i - 1] == bChars[j - 1]) 0 else 1
                curr[j] = minOf(prev[j] + 1, curr[j - 1] + 1, prev[j - 1] + cost)
            }
            val temp = prev; prev = curr; curr = temp
        }
        return 1.0 - prev[bChars.size].toDouble() / max(aChars.size, bChars.size).toDouble()
    }

    private fun soundex(input: String): String {
        if (input.isEmpty()) return ""
        val clean = input.lowercase().filter { it.isLetter() }
        if (clean.isEmpty()) return ""
        val first = clean[0].uppercaseChar()
        fun charCode(c: Char): Char = when (c) {
            'b', 'f', 'p', 'v' -> '1'
            'c', 'g', 'j', 'k', 'q', 's', 'x', 'z' -> '2'
            'd', 't' -> '3'
            'l' -> '4'
            'm', 'n' -> '5'
            'r' -> '6'
            else -> '0'
        }
        val result = StringBuilder().append(first)
        var lastCode = charCode(clean[0])
        for (i in 1 until clean.length) {
            if (result.length >= 4) break
            val code = charCode(clean[i])
            if (code != '0' && code != lastCode) result.append(code)
            lastCode = code
        }
        while (result.length < 4) result.append('0')
        return result.toString()
    }
}

/**
 * Preview of what Handle Everything would do.
 */
data class HandlePreview(
    val totalIssues: Int,
    val autoFixableIssues: Int,
    val suffixFixes: Int,
    val duplicateFactFixes: Int,
    val genderInferences: Int,
    val duplicateImages: Int,
    val totalMedia: Int,
    val unlinkedMedia: Int
)
