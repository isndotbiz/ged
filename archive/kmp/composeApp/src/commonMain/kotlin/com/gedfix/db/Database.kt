package com.gedfix.db

import app.cash.sqldelight.db.SqlDriver
import com.gedfix.models.*
import java.time.Instant

/**
 * Platform-specific SQLite driver factory.
 * Each platform (desktop, android, ios) provides its own implementation.
 */
expect class DriverFactory {
    fun createDriver(): SqlDriver
}

/**
 * Cross-platform database service wrapping SQLDelight-generated queries.
 */
class DatabaseRepository(driverFactory: DriverFactory) {
    private val driver: SqlDriver = driverFactory.createDriver()
    private val database: GedFixDatabase = GedFixDatabase(driver)
    private val queries = database.gedFixDatabaseQueries

    // MARK: - Import

    fun importParseResult(result: GedcomParseResult) {
        // Clear existing data
        queries.deleteAllEvents()
        queries.deleteAllChildLinks()
        queries.deleteAllFamilies()
        queries.deleteAllPersons()
        queries.deleteAllPlaces()
        queries.deleteAllSources()
        queries.deleteAllMedia()
        queries.deleteAllCitations()

        for (person in result.persons) {
            queries.insertPerson(
                id = person.id,
                xref = person.xref,
                givenName = person.givenName,
                surname = person.surname,
                suffix = person.suffix,
                sex = person.sex,
                isLiving = if (person.isLiving) 1L else 0L,
                sourceCount = person.sourceCount.toLong(),
                mediaCount = person.mediaCount.toLong(),
                personColor = person.personColor,
                proofStatus = person.proofStatus.name
            )
        }
        for (family in result.families) {
            queries.insertFamily(
                id = family.id,
                xref = family.xref,
                partner1Xref = family.partner1Xref,
                partner2Xref = family.partner2Xref
            )
        }
        for (link in result.childLinks) {
            queries.insertChildLink(
                familyXref = link.familyXref,
                childXref = link.childXref,
                childOrder = link.childOrder.toLong()
            )
        }
        for (event in result.events) {
            queries.insertEvent(
                id = event.id,
                ownerXref = event.ownerXref,
                ownerType = event.ownerType,
                eventType = event.eventType,
                dateValue = event.dateValue,
                place = event.place,
                description = event.description
            )
        }
        for (place in result.places) {
            queries.insertPlace(
                id = place.id,
                name = place.name,
                normalized = place.normalized,
                latitude = place.latitude,
                longitude = place.longitude,
                eventCount = place.eventCount.toLong()
            )
        }
        for (source in result.sources) {
            queries.insertSource(
                id = source.id,
                xref = source.xref,
                title = source.title,
                author = source.author,
                publisher = source.publisher,
                repository = source.repository
            )
        }
        for (media in result.media) {
            queries.insertMedia(
                id = media.id,
                xref = media.xref,
                ownerXref = media.ownerXref,
                filePath = media.filePath,
                format = media.format,
                title = media.title,
                description = media.description
            )
        }
    }

    // MARK: - Counts

    fun personCount(): Int = queries.countPersons().executeAsOne().toInt()
    fun familyCount(): Int = queries.countFamilies().executeAsOne().toInt()
    fun eventCount(): Int = queries.countEvents().executeAsOne().toInt()
    fun placeCount(): Int = queries.countPlaces().executeAsOne().toInt()
    fun sourceCount(): Int = queries.countSources().executeAsOne().toInt()

    // MARK: - Person queries

    fun fetchAllPersons(): List<GedcomPerson> {
        return queries.selectAllPersons().executeAsList().map { it.toModel() }
    }

    fun fetchPersons(search: String = "", sortBy: PersonSort = PersonSort.SURNAME): List<GedcomPerson> {
        return if (search.isEmpty()) {
            queries.selectAllPersons().executeAsList().map { it.toModel() }
        } else {
            val pattern = "%$search%"
            queries.searchPersons(pattern, pattern).executeAsList().map { it.toModel() }
        }
    }

    fun fetchPerson(byXref: String): GedcomPerson? {
        return queries.selectPersonByXref(byXref).executeAsOneOrNull()?.toModel()
    }

    fun insertPerson(person: GedcomPerson) {
        queries.insertPerson(
            id = person.id,
            xref = person.xref,
            givenName = person.givenName,
            surname = person.surname,
            suffix = person.suffix,
            sex = person.sex,
            isLiving = if (person.isLiving) 1L else 0L,
            sourceCount = person.sourceCount.toLong(),
            mediaCount = person.mediaCount.toLong(),
            personColor = person.personColor,
            proofStatus = person.proofStatus.name
        )
    }

    fun updatePerson(person: GedcomPerson) {
        queries.updatePerson(
            givenName = person.givenName,
            surname = person.surname,
            suffix = person.suffix,
            sex = person.sex,
            isLiving = if (person.isLiving) 1L else 0L,
            personColor = person.personColor,
            proofStatus = person.proofStatus.name,
            xref = person.xref
        )
    }

    fun deletePerson(xref: String) {
        queries.deleteEventsForOwner(xref)
        queries.deleteChildLinksForChild(xref)
        // Delete families where this person is a partner
        val partnerFamilies = queries.selectFamiliesAsSpouse(xref, xref).executeAsList()
        for (family in partnerFamilies) {
            queries.deleteChildLinksForFamily(family.xref)
            queries.deleteEventsForOwner(family.xref)
            queries.deleteFamily(family.xref)
        }
        queries.deletePerson(xref)
    }

    // MARK: - Family queries

    fun fetchAllFamilies(): List<GedcomFamily> {
        return queries.selectAllFamilies().executeAsList().map { it.toModel() }
    }

    fun fetchFamiliesAsSpouse(xref: String): List<GedcomFamily> {
        return queries.selectFamiliesAsSpouse(xref, xref).executeAsList().map { it.toModel() }
    }

    fun fetchFamiliesAsChild(xref: String): List<GedcomFamily> {
        val childFamilyXrefs = queries.selectChildLinksForChild(xref).executeAsList().map { it.familyXref }
        return childFamilyXrefs.mapNotNull { familyXref ->
            queries.selectFamilyByXref(familyXref).executeAsOneOrNull()?.toModel()
        }
    }

    fun createFamily(partner1Xref: String, partner2Xref: String): GedcomFamily {
        val maxXref = queries.selectMaxFamilyXref().executeAsOneOrNull()
        val maxNum = if (maxXref != null) {
            maxXref.replace("@F", "").replace("@", "").toIntOrNull() ?: 0
        } else 0
        val newXref = "@F${maxNum + 1}@"
        val family = GedcomFamily(
            id = kotlin.uuid.Uuid.random().toString(),
            xref = newXref,
            partner1Xref = partner1Xref,
            partner2Xref = partner2Xref
        )
        queries.insertFamily(family.id, family.xref, family.partner1Xref, family.partner2Xref)
        return family
    }

    // MARK: - Event queries

    fun fetchEvents(forXref: String): List<GedcomEvent> {
        return queries.selectEventsForOwner(forXref).executeAsList().map { it.toModel() }
    }

    fun fetchAllEvents(): List<GedcomEvent> {
        return queries.selectAllEvents().executeAsList().map { it.toModel() }
    }

    fun insertEvent(event: GedcomEvent) {
        queries.insertEvent(
            id = event.id,
            ownerXref = event.ownerXref,
            ownerType = event.ownerType,
            eventType = event.eventType,
            dateValue = event.dateValue,
            place = event.place,
            description = event.description
        )
    }

    fun updateEvent(event: GedcomEvent) {
        queries.updateEvent(
            eventType = event.eventType,
            dateValue = event.dateValue,
            place = event.place,
            description = event.description,
            id = event.id
        )
    }

    fun deleteEvent(id: String) {
        queries.deleteEvent(id)
    }

    // MARK: - Child Link queries

    fun fetchChildLinks(forFamily: String): List<GedcomChildLink> {
        return queries.selectChildLinksForFamily(forFamily).executeAsList().map {
            GedcomChildLink(familyXref = it.familyXref, childXref = it.childXref, childOrder = it.childOrder.toInt())
        }
    }

    fun fetchAllChildLinks(): List<GedcomChildLink> {
        // Use selectChildLinksForFamily with a broad query - fall back to fetching from all families
        val families = queries.selectAllFamilies().executeAsList()
        return families.flatMap { family ->
            queries.selectChildLinksForFamily(family.xref).executeAsList().map {
                GedcomChildLink(familyXref = it.familyXref, childXref = it.childXref, childOrder = it.childOrder.toInt())
            }
        }
    }

    fun addChildToFamily(familyXref: String, childXref: String) {
        val existing = queries.selectChildLinksForFamily(familyXref).executeAsList()
        val maxOrder = existing.maxOfOrNull { it.childOrder } ?: 0
        queries.insertChildLink(familyXref, childXref, maxOrder + 1)
    }

    fun removeChildFromFamily(familyXref: String, childXref: String) {
        queries.deleteChildLink(familyXref, childXref)
    }

    // MARK: - Place queries

    fun fetchAllPlaces(): List<GedcomPlace> {
        return queries.selectAllPlaces().executeAsList().map { it.toModel() }
    }

    // MARK: - Source queries

    fun fetchAllSources(): List<GedcomSource> {
        return queries.selectAllSources().executeAsList().map { it.toModel() }
    }

    // MARK: - Validation queries

    fun validatedPersonCount(): Int = queries.countValidatedPersons().executeAsOne().toInt()
    fun unvalidatedPersonCount(): Int = queries.countUnvalidatedPersons().executeAsOne().toInt()

    fun fetchUnvalidatedPersons(): List<GedcomPerson> {
        return queries.selectUnvalidatedPersons().executeAsList().map { it.toModel() }
    }

    fun fetchValidatedPersons(): List<GedcomPerson> {
        return queries.selectValidatedPersons().executeAsList().map { it.toModel() }
    }

    // MARK: - Top Surnames

    fun fetchTopSurnames(limit: Int = 10): List<Pair<String, Int>> {
        return queries.selectTopSurnames(limit.toLong()).executeAsList().map {
            Pair(it.surname, it.cnt.toInt())
        }
    }

    // MARK: - Next xref

    fun nextPersonXref(): String {
        val maxXref = queries.selectMaxPersonXref().executeAsOneOrNull()
        val maxNum = if (maxXref != null) {
            maxXref.replace("@I", "").replace("@", "").toIntOrNull() ?: 0
        } else 0
        return "@I${maxNum + 1}@"
    }

    // MARK: - Global Search

    fun globalSearch(query: String): GlobalSearchResults {
        val pattern = "%$query%"
        return GlobalSearchResults(
            persons = queries.searchPersons(pattern, pattern).executeAsList().map { it.toModel() },
            places = queries.searchPlaces(pattern).executeAsList().map { it.toModel() },
            sources = queries.searchSources(pattern, pattern).executeAsList().map { it.toModel() },
            events = queries.searchEvents(pattern, pattern).executeAsList().map { it.toModel() }
        )
    }

    // MARK: - Timeline queries

    fun fetchAllEventsWithPersons(): List<TimelineEntry> {
        return queries.selectEventsWithPersonName().executeAsList().map { row ->
            TimelineEntry(
                event = GedcomEvent(
                    id = row.id,
                    ownerXref = row.ownerXref,
                    ownerType = row.ownerType,
                    eventType = row.eventType,
                    dateValue = row.dateValue,
                    place = row.place,
                    description = row.description
                ),
                personName = listOf(row.personGivenName ?: "", row.personSurname ?: "")
                    .filter { it.isNotEmpty() }.joinToString(" "),
                personXref = row.ownerXref
            )
        }
    }

    fun fetchEventsForPersonTimeline(xref: String): List<TimelineEntry> {
        return queries.selectEventsForPersonTimeline(xref).executeAsList().map { row ->
            TimelineEntry(
                event = GedcomEvent(
                    id = row.id,
                    ownerXref = row.ownerXref,
                    ownerType = row.ownerType,
                    eventType = row.eventType,
                    dateValue = row.dateValue,
                    place = row.place,
                    description = row.description
                ),
                personName = listOf(row.personGivenName ?: "", row.personSurname ?: "")
                    .filter { it.isNotEmpty() }.joinToString(" "),
                personXref = row.ownerXref
            )
        }
    }

    // MARK: - Statistics queries

    fun fetchEventTypeCounts(): List<EventTypeCount> {
        return queries.countEventsByType().executeAsList().map {
            EventTypeCount(eventType = it.eventType, count = it.cnt.toInt())
        }
    }

    fun fetchDecadeCounts(): List<DecadeCount> {
        return queries.countEventsByDecade().executeAsList().mapNotNull { row ->
            val decade = row.decade
            if (decade != null && decade > 0) {
                DecadeCount(decade = decade.toInt(), count = row.cnt.toInt())
            } else null
        }
    }

    fun fetchTopPlaces(limit: Int = 20): List<Pair<String, Int>> {
        return queries.selectTopPlaces().executeAsList().map {
            Pair(it.name, it.eventCount.toInt())
        }
    }

    fun sourcedPersonCount(): Int = queries.countSourcedPersons().executeAsOne().toInt()
    fun unsourcedPersonCount(): Int = queries.countUnsourcedPersons().executeAsOne().toInt()

    // MARK: - Pedigree queries

    fun fetchParents(ofXref: String): Pair<GedcomPerson?, GedcomPerson?> {
        val families = fetchFamiliesAsChild(ofXref)
        val family = families.firstOrNull() ?: return Pair(null, null)
        val father = if (family.partner1Xref.isEmpty()) null else fetchPerson(family.partner1Xref)
        val mother = if (family.partner2Xref.isEmpty()) null else fetchPerson(family.partner2Xref)
        return Pair(father, mother)
    }

    fun fetchBirthEvent(forXref: String): GedcomEvent? {
        return fetchEvents(forXref).firstOrNull { it.eventType == "BIRT" }
    }

    fun fetchDeathEvent(forXref: String): GedcomEvent? {
        return fetchEvents(forXref).firstOrNull { it.eventType == "DEAT" }
    }

    // MARK: - All child links (for export)

    fun fetchAllChildLinksFlat(): List<GedcomChildLink> {
        return queries.selectAllChildLinks().executeAsList().map {
            GedcomChildLink(familyXref = it.familyXref, childXref = it.childXref, childOrder = it.childOrder.toInt())
        }
    }

    // MARK: - Settings queries

    fun getSetting(key: String): String? {
        return queries.selectSetting(key).executeAsOneOrNull()
    }

    fun setSetting(key: String, value: String) {
        queries.insertSetting(key, value)
    }

    fun getAllSettings(): Map<String, String> {
        return queries.selectAllSettings().executeAsList().associate { it.key to it.value_ }
    }

    // MARK: - Media queries

    fun mediaCount(): Int = queries.countMedia().executeAsOne().toInt()

    fun fetchAllMedia(): List<GedcomMedia> {
        return queries.selectAllMedia().executeAsList().map { it.toModel() }
    }

    fun fetchMediaForOwner(ownerXref: String): List<GedcomMedia> {
        return queries.selectMediaForOwner(ownerXref).executeAsList().map { it.toModel() }
    }

    fun searchMedia(query: String): List<GedcomMedia> {
        val pattern = "%$query%"
        return queries.searchMedia(pattern, pattern).executeAsList().map { it.toModel() }
    }

    // MARK: - AI Chat History

    fun insertAIChatMessage(msg: com.gedfix.models.AIChatMessage) {
        queries.insertAIChatMessage(
            id = msg.id,
            provider = msg.provider,
            model = msg.model,
            role = msg.role,
            content = msg.content,
            personXref = msg.personXref,
            timestamp = msg.timestamp
        )
    }

    fun fetchAIChatHistory(): List<com.gedfix.models.AIChatMessage> {
        return queries.selectAllAIChatHistory().executeAsList().map {
            com.gedfix.models.AIChatMessage(
                id = it.id,
                provider = it.provider,
                model = it.model,
                role = it.role,
                content = it.content,
                personXref = it.personXref,
                timestamp = it.timestamp
            )
        }
    }

    fun fetchAIChatForPerson(personXref: String): List<com.gedfix.models.AIChatMessage> {
        return queries.selectAIChatForPerson(personXref).executeAsList().map {
            com.gedfix.models.AIChatMessage(
                id = it.id,
                provider = it.provider,
                model = it.model,
                role = it.role,
                content = it.content,
                personXref = it.personXref,
                timestamp = it.timestamp
            )
        }
    }

    fun clearAIChatHistory() {
        queries.deleteAllAIChatHistory()
    }

    // MARK: - Citation queries

    fun citationCount(): Int = queries.countCitations().executeAsOne().toInt()

    fun fetchCitationsForPerson(personXref: String): List<com.gedfix.models.Citation> {
        return queries.selectCitationsForPerson(personXref).executeAsList().map { it.toCitationModel() }
    }

    fun fetchCitationsForSource(sourceXref: String): List<com.gedfix.models.Citation> {
        return queries.selectCitationsForSource(sourceXref).executeAsList().map { it.toCitationModel() }
    }

    fun fetchCitationsForEvent(eventId: String): List<com.gedfix.models.Citation> {
        return queries.selectCitationsForEvent(eventId).executeAsList().map { it.toCitationModel() }
    }

    fun citationCountForSource(sourceXref: String): Int {
        return queries.countCitationsForSource(sourceXref).executeAsOne().toInt()
    }

    fun insertCitation(citation: com.gedfix.models.Citation) {
        queries.insertCitation(
            id = citation.id,
            sourceXref = citation.sourceXref,
            personXref = citation.personXref,
            eventId = citation.eventId,
            page = citation.page,
            quality = citation.quality.name,
            text = citation.text,
            note = citation.note
        )
    }

    fun deleteCitation(id: String) {
        queries.deleteCitation(id)
    }

    fun deleteAllCitations() {
        queries.deleteAllCitations()
    }

    // MARK: - Media delete

    fun deleteMedia(id: String) {
        queries.deleteMediaById(id)
    }

    // MARK: - Version Control

    fun insertVersion(version: com.gedfix.models.TreeVersion) {
        queries.insertVersion(
            id = version.id,
            timestamp = version.timestamp,
            description = version.description,
            changeType = version.changeType.toString(),
            changedRecords = version.changedRecords.toLong(),
            gedcomSnapshot = version.gedcomSnapshot
        )
    }

    fun fetchAllVersions(): List<com.gedfix.models.TreeVersion> {
        return queries.selectAllVersions().executeAsList().map { it.toVersionModel() }
    }

    fun fetchVersionById(id: String): com.gedfix.models.TreeVersion? {
        return queries.selectVersionById(id).executeAsOneOrNull()?.toVersionModel()
    }

    fun versionCount(): Int = queries.countVersions().executeAsOne().toInt()

    fun deleteOldVersions(keepCount: Int) {
        queries.deleteOldVersions(keepCount.toLong())
    }

    /**
     * Record a version snapshot after any tree mutation.
     */
    fun recordVersion(description: String, changeType: com.gedfix.models.ChangeType, changedRecords: Int) {
        val snapshot = GedcomExporter(this).export()
        val version = com.gedfix.models.TreeVersion(
            id = kotlin.uuid.Uuid.random().toString(),
            timestamp = java.time.Instant.now().toString(),
            description = description,
            changeType = changeType,
            changedRecords = changedRecords,
            gedcomSnapshot = snapshot
        )
        insertVersion(version)
    }

    // MARK: - Notes

    fun insertNote(note: ResearchNote) {
        queries.insertNote(
            id = note.id,
            ownerXref = note.ownerXref,
            ownerType = note.ownerType,
            title = note.title,
            content = note.content,
            createdAt = note.createdAt,
            updatedAt = note.updatedAt
        )
    }

    fun fetchNotesForOwner(ownerXref: String): List<ResearchNote> {
        return queries.selectNotesForOwner(ownerXref).executeAsList().map { it.toNoteModel() }
    }

    fun fetchAllNotes(): List<ResearchNote> {
        return queries.selectAllNotes().executeAsList().map { it.toNoteModel() }
    }

    fun noteCount(): Int = queries.countNotes().executeAsOne().toInt()

    fun noteCountForOwner(ownerXref: String): Int = queries.countNotesForOwner(ownerXref).executeAsOne().toInt()

    fun deleteNote(id: String) {
        queries.deleteNote(id)
    }

    fun searchNotes(query: String): List<ResearchNote> {
        val pattern = "%$query%"
        return queries.searchNotes(pattern, pattern).executeAsList().map { it.toNoteModel() }
    }

    // MARK: - Research Tasks

    fun insertTask(task: com.gedfix.models.ResearchTask) {
        queries.insertTask(
            id = task.id,
            personXref = task.personXref,
            title = task.title,
            description = task.description,
            status = task.status.name,
            priority = task.priority.name,
            dueDate = task.dueDate,
            createdAt = task.createdAt,
            completedAt = task.completedAt
        )
    }

    fun fetchAllTasks(): List<com.gedfix.models.ResearchTask> {
        return queries.selectAllTasks().executeAsList().map { it.toTaskModel() }
    }

    fun fetchTasksForPerson(personXref: String): List<com.gedfix.models.ResearchTask> {
        return queries.selectTasksForPerson(personXref).executeAsList().map { it.toTaskModel() }
    }

    fun fetchTasksByStatus(status: com.gedfix.models.TaskStatus): List<com.gedfix.models.ResearchTask> {
        return queries.selectTasksByStatus(status.name).executeAsList().map { it.toTaskModel() }
    }

    fun taskCount(): Int = queries.countTasks().executeAsOne().toInt()

    fun pendingTaskCount(): Int = queries.countPendingTasks().executeAsOne().toInt()

    fun deleteTask(id: String) {
        queries.deleteTask(id)
    }

    // MARK: - Bookmarks

    fun insertBookmark(bookmark: PersonBookmark) {
        queries.insertBookmark(
            id = bookmark.id,
            personXref = bookmark.personXref,
            label = bookmark.label,
            createdAt = bookmark.createdAt
        )
    }

    fun fetchAllBookmarks(): List<PersonBookmark> {
        return queries.selectAllBookmarks().executeAsList().map {
            PersonBookmark(
                id = it.id,
                personXref = it.personXref,
                label = it.label,
                createdAt = it.createdAt
            )
        }
    }

    fun isBookmarked(personXref: String): Boolean {
        return queries.isBookmarked(personXref).executeAsOne() > 0
    }

    fun deleteBookmark(id: String) {
        queries.deleteBookmark(id)
    }

    fun removeBookmarkByPerson(personXref: String) {
        queries.deleteBookmarkByPerson(personXref)
    }

    fun bookmarkCount(): Int = queries.countBookmarks().executeAsOne().toInt()

    // MARK: - Handle Everything

    fun updateMediaFilePath(id: String, filePath: String, title: String) {
        queries.updateMediaFilePath(filePath, title, id)
    }

    fun updateMediaOwner(id: String, ownerXref: String) {
        queries.updateMediaOwner(ownerXref, id)
    }

    fun updateEventPlace(oldPlace: String, newPlace: String) {
        queries.updateEventPlace(newPlace, oldPlace)
    }

    fun updatePersonSurname(oldSurname: String, newSurname: String) {
        queries.updatePersonSurname(newSurname, oldSurname)
    }

    fun insertHandleEverythingRun(run: com.gedfix.services.HandleEverythingRun) {
        queries.insertHandleEverythingRun(
            id = run.id,
            timestamp = run.timestamp,
            backupPath = run.backupPath,
            versionId = run.versionId,
            report = run.report,
            fileRenames = run.fileRenames,
            fixesApplied = run.fixesApplied.toLong(),
            fixesNeedReview = run.fixesNeedReview.toLong(),
            duplicatesRemoved = run.duplicatesRemoved.toLong(),
            filesRenamed = run.filesRenamed.toLong(),
            spaceSavedBytes = run.spaceSavedBytes
        )
    }

    fun fetchAllHandleEverythingRuns(): List<com.gedfix.services.HandleEverythingRun> {
        return queries.selectAllHandleEverythingRuns().executeAsList().map {
            com.gedfix.services.HandleEverythingRun(
                id = it.id,
                timestamp = it.timestamp,
                backupPath = it.backupPath,
                versionId = it.versionId,
                report = it.report,
                fileRenames = it.fileRenames,
                fixesApplied = it.fixesApplied.toInt(),
                fixesNeedReview = it.fixesNeedReview.toInt(),
                duplicatesRemoved = it.duplicatesRemoved.toInt(),
                filesRenamed = it.filesRenamed.toInt(),
                spaceSavedBytes = it.spaceSavedBytes
            )
        }
    }

    fun fetchHandleEverythingRunById(id: String): com.gedfix.services.HandleEverythingRun? {
        return queries.selectHandleEverythingRunById(id).executeAsOneOrNull()?.let {
            com.gedfix.services.HandleEverythingRun(
                id = it.id,
                timestamp = it.timestamp,
                backupPath = it.backupPath,
                versionId = it.versionId,
                report = it.report,
                fileRenames = it.fileRenames,
                fixesApplied = it.fixesApplied.toInt(),
                fixesNeedReview = it.fixesNeedReview.toInt(),
                duplicatesRemoved = it.duplicatesRemoved.toInt(),
                filesRenamed = it.filesRenamed.toInt(),
                spaceSavedBytes = it.spaceSavedBytes
            )
        }
    }

    fun handleEverythingRunCount(): Int = queries.countHandleEverythingRuns().executeAsOne().toInt()

    // MARK: - Groups

    fun insertGroup(group: PersonGroup) {
        queries.insertGroup(
            id = group.id,
            name = group.name,
            color = group.color,
            description = group.description,
            createdAt = group.createdAt
        )
    }

    fun fetchAllGroups(): List<PersonGroup> {
        return queries.selectAllGroups().executeAsList().map { it.toGroupModel() }
    }

    fun groupCount(): Int = queries.countGroups().executeAsOne().toInt()

    fun deleteGroup(id: String) {
        queries.deleteGroupMembersForGroup(id)
        queries.deleteGroup(id)
    }

    fun addMemberToGroup(groupId: String, personXref: String) {
        queries.insertGroupMember(groupId, personXref, java.time.Instant.now().toString())
    }

    fun removeMemberFromGroup(groupId: String, personXref: String) {
        queries.deleteGroupMember(groupId, personXref)
    }

    fun fetchMembersOfGroup(groupId: String): List<String> {
        return queries.selectMembersOfGroup(groupId).executeAsList()
    }

    fun fetchGroupsForPerson(personXref: String): List<PersonGroup> {
        return queries.selectGroupsForPerson(personXref).executeAsList().map { it.toGroupModel() }
    }

    fun groupMemberCount(groupId: String): Int {
        return queries.countGroupMembers(groupId).executeAsOne().toInt()
    }

    // MARK: - Associations

    fun insertAssociation(assoc: Association) {
        queries.insertAssociation(
            id = assoc.id,
            person1Xref = assoc.person1Xref,
            person2Xref = assoc.person2Xref,
            relationshipType = assoc.relationshipType,
            description = assoc.description,
            createdAt = assoc.createdAt
        )
    }

    fun fetchAssociationsForPerson(personXref: String): List<Association> {
        return queries.selectAssociationsForPerson(personXref, personXref).executeAsList().map {
            Association(
                id = it.id,
                person1Xref = it.person1Xref,
                person2Xref = it.person2Xref,
                relationshipType = it.relationshipType,
                description = it.description,
                createdAt = it.createdAt
            )
        }
    }

    fun associationCount(): Int = queries.countAssociations().executeAsOne().toInt()

    fun deleteAssociation(id: String) {
        queries.deleteAssociation(id)
    }

    // MARK: - Alternate Names

    fun insertAlternateName(name: AlternateName) {
        queries.insertAlternateName(
            id = name.id,
            personXref = name.personXref,
            givenName = name.givenName,
            surname = name.surname,
            suffix = name.suffix,
            nameType = name.nameType,
            source = name.source
        )
    }

    fun fetchAlternateNamesForPerson(personXref: String): List<AlternateName> {
        return queries.selectAlternateNamesForPerson(personXref).executeAsList().map {
            AlternateName(
                id = it.id,
                personXref = it.personXref,
                givenName = it.givenName,
                surname = it.surname,
                suffix = it.suffix,
                nameType = it.nameType,
                source = it.source
            )
        }
    }

    fun deleteAlternateName(id: String) {
        queries.deleteAlternateName(id)
    }

    // MARK: - Research Log

    fun insertResearchLog(entry: ResearchLogEntry) {
        queries.insertResearchLog(
            id = entry.id,
            personXref = entry.personXref,
            repository = entry.repository,
            searchTerms = entry.searchTerms,
            recordsViewed = entry.recordsViewed,
            conclusion = entry.conclusion,
            sourceXref = entry.sourceXref,
            resultType = entry.resultType.name,
            searchDate = entry.searchDate,
            createdAt = entry.createdAt
        )
    }

    fun fetchAllResearchLogs(): List<ResearchLogEntry> {
        return queries.selectAllResearchLogs().executeAsList().map {
            ResearchLogEntry(
                id = it.id,
                personXref = it.personXref,
                repository = it.repository,
                searchTerms = it.searchTerms,
                recordsViewed = it.recordsViewed,
                conclusion = it.conclusion,
                sourceXref = it.sourceXref,
                resultType = ResearchResultType.fromString(it.resultType),
                searchDate = it.searchDate,
                createdAt = it.createdAt
            )
        }
    }

    fun fetchResearchLogsForPerson(personXref: String): List<ResearchLogEntry> {
        return queries.selectResearchLogsForPerson(personXref).executeAsList().map {
            ResearchLogEntry(
                id = it.id,
                personXref = it.personXref,
                repository = it.repository,
                searchTerms = it.searchTerms,
                recordsViewed = it.recordsViewed,
                conclusion = it.conclusion,
                sourceXref = it.sourceXref,
                resultType = ResearchResultType.fromString(it.resultType),
                searchDate = it.searchDate,
                createdAt = it.createdAt
            )
        }
    }

    fun researchLogCount(): Int = queries.countResearchLogs().executeAsOne().toInt()

    fun deleteResearchLog(id: String) {
        queries.deleteResearchLog(id)
    }

    // MARK: - Merge operations

    /**
     * Merge two persons: keep one, transfer all data from removed to kept, then delete removed.
     * This is a critical operation - it moves events, family links, citations, and child links.
     */
    fun mergePersons(keepXref: String, removeXref: String, mergedData: GedcomPerson) {
        // 1. Update the kept person with merged data
        queries.updatePerson(
            givenName = mergedData.givenName,
            surname = mergedData.surname,
            suffix = mergedData.suffix,
            sex = mergedData.sex,
            isLiving = if (mergedData.isLiving) 1L else 0L,
            personColor = mergedData.personColor,
            proofStatus = mergedData.proofStatus.name,
            xref = keepXref
        )

        // 2. Move all events from removed person to kept person
        queries.updateEventsOwner(keepXref, removeXref)

        // 3. Update all family links (HUSB/WIFE) from removed to kept
        queries.updateFamilyPartner1(keepXref, removeXref)
        queries.updateFamilyPartner2(keepXref, removeXref)

        // 4. Update all child links from removed to kept
        queries.updateChildLinkChild(keepXref, removeXref)

        // 5. Update all citations from removed to kept
        queries.updateCitationsPersonXref(keepXref, removeXref)

        // 6. Delete the removed person record
        queries.deletePerson(removeXref)
    }
}

// MARK: - Row-to-Model mapping extensions

@OptIn(kotlin.uuid.ExperimentalUuidApi::class)
private fun com.gedfix.db.Person.toModel(): GedcomPerson = GedcomPerson(
    id = id,
    xref = xref,
    givenName = givenName,
    surname = surname,
    suffix = suffix,
    sex = sex,
    isLiving = isLiving != 0L,
    sourceCount = sourceCount.toInt(),
    mediaCount = mediaCount.toInt(),
    isValidated = sourceCount > 0,
    personColor = personColor,
    proofStatus = ProofStatus.fromString(proofStatus)
)

private fun com.gedfix.db.Family.toModel(): GedcomFamily = GedcomFamily(
    id = id,
    xref = xref,
    partner1Xref = partner1Xref,
    partner2Xref = partner2Xref
)

private fun com.gedfix.db.Event.toModel(): GedcomEvent = GedcomEvent(
    id = id,
    ownerXref = ownerXref,
    ownerType = ownerType,
    eventType = eventType,
    dateValue = dateValue,
    place = place,
    description = description
)

private fun com.gedfix.db.Place.toModel(): GedcomPlace = GedcomPlace(
    id = id,
    name = name,
    normalized = normalized,
    latitude = latitude,
    longitude = longitude,
    eventCount = eventCount.toInt()
)

private fun com.gedfix.db.Source.toModel(): GedcomSource = GedcomSource(
    id = id,
    xref = xref,
    title = title,
    author = author,
    publisher = publisher,
    repository = repository
)

private fun com.gedfix.db.Media.toModel(): GedcomMedia = GedcomMedia(
    id = id,
    xref = xref,
    ownerXref = ownerXref,
    filePath = filePath,
    format = format,
    title = title,
    description = description
)

private fun com.gedfix.db.Citation.toCitationModel(): com.gedfix.models.Citation = com.gedfix.models.Citation(
    id = id,
    sourceXref = sourceXref,
    personXref = personXref,
    eventId = eventId,
    page = page,
    quality = com.gedfix.models.CitationQuality.fromString(quality),
    text = text,
    note = note
)

private fun com.gedfix.db.TreeVersion.toVersionModel(): com.gedfix.models.TreeVersion = com.gedfix.models.TreeVersion(
    id = id,
    timestamp = timestamp,
    description = description,
    changeType = com.gedfix.models.ChangeType.fromString(changeType),
    changedRecords = changedRecords.toInt(),
    gedcomSnapshot = gedcomSnapshot
)

private fun com.gedfix.db.Note.toNoteModel(): ResearchNote = ResearchNote(
    id = id,
    ownerXref = ownerXref,
    ownerType = ownerType,
    title = title,
    content = content,
    createdAt = createdAt,
    updatedAt = updatedAt
)

private fun com.gedfix.db.CustomGroup.toGroupModel(): PersonGroup = PersonGroup(
    id = id,
    name = name,
    color = color,
    description = description,
    createdAt = createdAt
)

private fun com.gedfix.db.ResearchTask.toTaskModel(): com.gedfix.models.ResearchTask = com.gedfix.models.ResearchTask(
    id = id,
    personXref = personXref,
    title = title,
    description = description,
    status = com.gedfix.models.TaskStatus.fromString(status),
    priority = com.gedfix.models.TaskPriority.fromString(priority),
    dueDate = dueDate,
    createdAt = createdAt,
    completedAt = completedAt
)
