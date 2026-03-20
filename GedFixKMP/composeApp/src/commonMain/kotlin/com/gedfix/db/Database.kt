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

        for (person in result.persons) {
            queries.insertPerson(
                id = person.id,
                xref = person.xref,
                givenName = person.givenName,
                surname = person.surname,
                suffix = person.suffix,
                sex = person.sex,
                isLiving = if (person.isLiving) 1L else 0L
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
            isLiving = if (person.isLiving) 1L else 0L
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
    isLiving = isLiving != 0L
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
