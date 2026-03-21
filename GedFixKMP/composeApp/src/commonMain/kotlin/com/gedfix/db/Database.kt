package com.gedfix.db

import app.cash.sqldelight.db.SqlDriver
import com.gedfix.models.*

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
                mediaCount = person.mediaCount.toLong()
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
            mediaCount = person.mediaCount.toLong()
        )
    }

    fun updatePerson(person: GedcomPerson) {
        queries.updatePerson(
            givenName = person.givenName,
            surname = person.surname,
            suffix = person.suffix,
            sex = person.sex,
            isLiving = if (person.isLiving) 1L else 0L,
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
    isValidated = sourceCount > 0
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
