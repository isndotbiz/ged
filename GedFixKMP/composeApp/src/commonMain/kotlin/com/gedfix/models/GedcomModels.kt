package com.gedfix.models

import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

// MARK: - Person

data class GedcomPerson(
    val id: String,
    val xref: String,
    var givenName: String,
    var surname: String,
    var suffix: String,
    var sex: String, // M, F, U
    var isLiving: Boolean
) {
    val displayName: String
        get() = listOf(givenName, surname, suffix)
            .filter { it.isNotEmpty() }
            .joinToString(" ")

    val initials: String
        get() {
            val g = givenName.firstOrNull()?.uppercase() ?: ""
            val s = surname.firstOrNull()?.uppercase() ?: ""
            return "$g$s"
        }
}

// MARK: - Family

data class GedcomFamily(
    val id: String,
    val xref: String,
    val partner1Xref: String, // husband/partner1
    val partner2Xref: String  // wife/partner2
)

// MARK: - Child Link

data class GedcomChildLink(
    val familyXref: String,
    val childXref: String,
    val childOrder: Int
)

// MARK: - Event

data class GedcomEvent(
    val id: String,
    val ownerXref: String,  // person or family xref
    val ownerType: String,  // "INDI" or "FAM"
    var eventType: String,  // BIRT, DEAT, MARR, BURI, CHR, etc.
    var dateValue: String,  // raw GEDCOM date string
    var place: String,
    var description: String
) {
    val displayDate: String
        get() = if (dateValue.isEmpty()) "" else dateValue

    val displayType: String
        get() = when (eventType) {
            "BIRT" -> "Birth"
            "DEAT" -> "Death"
            "MARR" -> "Marriage"
            "BURI" -> "Burial"
            "CHR" -> "Christening"
            "BAPM" -> "Baptism"
            "RESI" -> "Residence"
            "CENS" -> "Census"
            "IMMI" -> "Immigration"
            "EMIG" -> "Emigration"
            "NATU" -> "Naturalization"
            "GRAD" -> "Graduation"
            "RETI" -> "Retirement"
            "PROB" -> "Probate"
            "WILL" -> "Will"
            "DIV" -> "Divorce"
            else -> eventType
        }

    val eventIcon: String
        get() = when (eventType) {
            "BIRT" -> "child"
            "DEAT" -> "leaf"
            "MARR" -> "heart"
            "BURI" -> "cross"
            "CHR", "BAPM" -> "droplet"
            "RESI", "CENS" -> "house"
            "IMMI", "EMIG" -> "airplane"
            "NATU" -> "flag"
            "DIV" -> "arrows"
            else -> "calendar"
        }
}

// MARK: - Place

data class GedcomPlace(
    val id: String,
    val name: String,
    val normalized: String,
    val latitude: Double? = null,
    val longitude: Double? = null,
    var eventCount: Int
)

// MARK: - Source

data class GedcomSource(
    val id: String,
    val xref: String,
    val title: String,
    val author: String,
    val publisher: String,
    val repository: String
)

// MARK: - Issue Models

data class TreeIssue(
    val id: String,
    val category: IssueCategory,
    val severity: IssueSeverity,
    val personXref: String? = null,
    val familyXref: String? = null,
    val title: String,
    val detail: String,
    val suggestion: String,
    val isAutoFixable: Boolean = false
)

@OptIn(ExperimentalUuidApi::class)
fun createTreeIssue(
    category: IssueCategory,
    severity: IssueSeverity,
    personXref: String? = null,
    familyXref: String? = null,
    title: String,
    detail: String,
    suggestion: String,
    isAutoFixable: Boolean = false
): TreeIssue = TreeIssue(
    id = Uuid.random().toString(),
    category = category,
    severity = severity,
    personXref = personXref,
    familyXref = familyXref,
    title = title,
    detail = detail,
    suggestion = suggestion,
    isAutoFixable = isAutoFixable
)

enum class IssueCategory(val label: String) {
    DATE_ISSUE("Date Issues"),
    RELATIONSHIP_ISSUE("Relationship Issues"),
    DATA_QUALITY("Data Quality"),
    POTENTIAL_DUPLICATE("Potential Duplicates");

    val iconName: String
        get() = when (this) {
            DATE_ISSUE -> "calendar_warning"
            RELATIONSHIP_ISSUE -> "people_slash"
            DATA_QUALITY -> "checkmark_seal"
            POTENTIAL_DUPLICATE -> "people_fill"
        }
}

enum class IssueSeverity(val label: String) : Comparable<IssueSeverity> {
    CRITICAL("Critical"),
    WARNING("Warning"),
    INFO("Info");

    val iconName: String
        get() = when (this) {
            CRITICAL -> "error"
            WARNING -> "warning"
            INFO -> "info"
        }
}

// MARK: - Parse Result

data class GedcomParseResult(
    val persons: List<GedcomPerson>,
    val families: List<GedcomFamily>,
    val childLinks: List<GedcomChildLink>,
    val events: List<GedcomEvent>,
    val places: List<GedcomPlace>,
    val sources: List<GedcomSource>,
    val lineCount: Int
)

// MARK: - Enums

enum class PersonSort {
    SURNAME, GIVEN_NAME
}

enum class SidebarSection(val label: String) {
    OVERVIEW("Overview"),
    ISSUES("Issues"),
    PEOPLE("People"),
    PEDIGREE("Pedigree"),
    FAMILIES("Families"),
    PLACES("Places"),
    SOURCES("Sources");

    val iconName: String
        get() = when (this) {
            OVERVIEW -> "dashboard"
            ISSUES -> "warning"
            PEOPLE -> "people"
            PEDIGREE -> "tree"
            FAMILIES -> "house"
            PLACES -> "pin"
            SOURCES -> "book"
        }
}
