package com.gedfix.models

import com.gedfix.db.DatabaseRepository
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Detects logical contradictions and impossible situations in the family tree.
 * Goes beyond basic validation to find cross-record contradictions.
 */
@OptIn(ExperimentalUuidApi::class)
class ContradictionDetector(private val db: DatabaseRepository) {

    data class Contradiction(
        val id: String,
        val severity: IssueSeverity,
        val category: String,
        val title: String,
        val detail: String,
        val personXrefs: List<String>,
        val suggestion: String
    )

    fun detectAll(): List<Contradiction> {
        val contradictions = mutableListOf<Contradiction>()
        val persons = db.fetchAllPersons()
        val families = db.fetchAllFamilies()

        for (person in persons) {
            val events = db.fetchEvents(person.xref)
            val birthEvent = events.firstOrNull { it.eventType == "BIRT" }
            val deathEvent = events.firstOrNull { it.eventType == "DEAT" }
            val birthYear = birthEvent?.let { GedcomParser.extractYear(it.dateValue) }
            val deathYear = deathEvent?.let { GedcomParser.extractYear(it.dateValue) }

            // Death before birth
            if (birthYear != null && deathYear != null && deathYear < birthYear) {
                contradictions.add(Contradiction(
                    id = Uuid.random().toString(),
                    severity = IssueSeverity.CRITICAL,
                    category = "Impossible Date",
                    title = "Death before birth: ${person.displayName}",
                    detail = "Born $birthYear, died $deathYear",
                    personXrefs = listOf(person.xref),
                    suggestion = "Check and correct the birth or death date"
                ))
            }

            // Impossibly old (>120 years)
            if (birthYear != null && deathYear != null && (deathYear - birthYear) > 120) {
                contradictions.add(Contradiction(
                    id = Uuid.random().toString(),
                    severity = IssueSeverity.WARNING,
                    category = "Unlikely Age",
                    title = "Lived ${deathYear - birthYear} years: ${person.displayName}",
                    detail = "Born $birthYear, died $deathYear (${deathYear - birthYear} years)",
                    personXrefs = listOf(person.xref),
                    suggestion = "Verify birth and death dates - unusually long lifespan"
                ))
            }

            // Events before birth or after death
            for (event in events) {
                if (event.eventType == "BIRT" || event.eventType == "DEAT") continue
                val eventYear = GedcomParser.extractYear(event.dateValue) ?: continue

                if (birthYear != null && eventYear < birthYear - 1) {
                    contradictions.add(Contradiction(
                        id = Uuid.random().toString(),
                        severity = IssueSeverity.CRITICAL,
                        category = "Impossible Date",
                        title = "${event.displayType} before birth: ${person.displayName}",
                        detail = "${event.displayType} in $eventYear, but born in $birthYear",
                        personXrefs = listOf(person.xref),
                        suggestion = "Check the ${event.displayType.lowercase()} date"
                    ))
                }

                if (deathYear != null && eventYear > deathYear + 1) {
                    contradictions.add(Contradiction(
                        id = Uuid.random().toString(),
                        severity = IssueSeverity.CRITICAL,
                        category = "Impossible Date",
                        title = "${event.displayType} after death: ${person.displayName}",
                        detail = "${event.displayType} in $eventYear, but died in $deathYear",
                        personXrefs = listOf(person.xref),
                        suggestion = "Check the ${event.displayType.lowercase()} date or death date"
                    ))
                }
            }

            // Marriage before age 14
            val spouseFamilies = db.fetchFamiliesAsSpouse(person.xref)
            for (fam in spouseFamilies) {
                val famEvents = db.fetchEvents(fam.xref)
                val marriageYear = famEvents.firstOrNull { it.eventType == "MARR" }
                    ?.let { GedcomParser.extractYear(it.dateValue) }

                if (birthYear != null && marriageYear != null && (marriageYear - birthYear) < 14) {
                    contradictions.add(Contradiction(
                        id = Uuid.random().toString(),
                        severity = IssueSeverity.WARNING,
                        category = "Unlikely Age",
                        title = "Married at age ${marriageYear - birthYear}: ${person.displayName}",
                        detail = "Born $birthYear, married $marriageYear",
                        personXrefs = listOf(person.xref),
                        suggestion = "Verify birth year or marriage date"
                    ))
                }
            }
        }

        // Child born before parent
        for (family in families) {
            val childLinks = db.fetchChildLinks(family.xref)
            val partner1 = if (family.partner1Xref.isNotEmpty()) db.fetchPerson(family.partner1Xref) else null
            val partner2 = if (family.partner2Xref.isNotEmpty()) db.fetchPerson(family.partner2Xref) else null

            val p1BirthYear = partner1?.let { p ->
                db.fetchEvents(p.xref).firstOrNull { it.eventType == "BIRT" }
                    ?.let { GedcomParser.extractYear(it.dateValue) }
            }
            val p2BirthYear = partner2?.let { p ->
                db.fetchEvents(p.xref).firstOrNull { it.eventType == "BIRT" }
                    ?.let { GedcomParser.extractYear(it.dateValue) }
            }

            for (link in childLinks) {
                val child = db.fetchPerson(link.childXref) ?: continue
                val childBirth = db.fetchEvents(child.xref).firstOrNull { it.eventType == "BIRT" }
                    ?.let { GedcomParser.extractYear(it.dateValue) } ?: continue

                if (p1BirthYear != null && childBirth <= p1BirthYear + 12) {
                    contradictions.add(Contradiction(
                        id = Uuid.random().toString(),
                        severity = IssueSeverity.CRITICAL,
                        category = "Impossible Relationship",
                        title = "Parent too young: ${partner1?.displayName}",
                        detail = "${partner1?.displayName} born $p1BirthYear, child ${child.displayName} born $childBirth (parent was ${childBirth - p1BirthYear})",
                        personXrefs = listOf(partner1?.xref ?: "", child.xref),
                        suggestion = "Check parent-child relationship or birth dates"
                    ))
                }

                if (p2BirthYear != null && childBirth <= p2BirthYear + 12) {
                    contradictions.add(Contradiction(
                        id = Uuid.random().toString(),
                        severity = IssueSeverity.CRITICAL,
                        category = "Impossible Relationship",
                        title = "Parent too young: ${partner2?.displayName}",
                        detail = "${partner2?.displayName} born $p2BirthYear, child ${child.displayName} born $childBirth (parent was ${childBirth - p2BirthYear})",
                        personXrefs = listOf(partner2?.xref ?: "", child.xref),
                        suggestion = "Check parent-child relationship or birth dates"
                    ))
                }

                // Parent died before child born (except mother within 9 months)
                val p1DeathYear = partner1?.let { p ->
                    db.fetchEvents(p.xref).firstOrNull { it.eventType == "DEAT" }
                        ?.let { GedcomParser.extractYear(it.dateValue) }
                }
                if (p1DeathYear != null && childBirth > p1DeathYear + 1) {
                    contradictions.add(Contradiction(
                        id = Uuid.random().toString(),
                        severity = IssueSeverity.WARNING,
                        category = "Impossible Relationship",
                        title = "Child born after parent's death: ${child.displayName}",
                        detail = "${partner1?.displayName} died $p1DeathYear, ${child.displayName} born $childBirth",
                        personXrefs = listOf(partner1?.xref ?: "", child.xref),
                        suggestion = "Verify death date of parent or birth date of child"
                    ))
                }
            }
        }

        return contradictions.sortedWith(compareBy({ it.severity }, { it.category }))
    }
}
