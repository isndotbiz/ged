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
    var isLiving: Boolean,
    val sourceCount: Int = 0,
    val mediaCount: Int = 0,
    val isValidated: Boolean = false
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

// MARK: - Media

data class GedcomMedia(
    val id: String,
    val xref: String,          // @M123@ or empty for inline
    val ownerXref: String,     // person/family xref this media belongs to
    val filePath: String,      // FILE tag value
    val format: String,        // FORM tag value (jpg, png, pdf, etc.)
    val title: String,         // TITL tag value
    val description: String    // NOTE under OBJE
) {
    val isImage: Boolean
        get() = format.lowercase() in setOf("jpg", "jpeg", "png", "gif", "bmp", "tiff", "tif", "webp")

    val isPdf: Boolean
        get() = format.lowercase() == "pdf"

    val displayTitle: String
        get() = title.ifEmpty { filePath.substringAfterLast('/').substringAfterLast('\\').ifEmpty { "(Untitled)" } }

    val formatBadge: String
        get() = format.uppercase().ifEmpty { "FILE" }

    val placeholderIcon: String
        get() = when {
            isImage -> "\uD83D\uDCF7" // Camera
            isPdf -> "\uD83D\uDCC4"   // Page
            else -> "\uD83D\uDCC1"    // Folder
        }
}

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

// MARK: - Global Search Results

data class GlobalSearchResults(
    val persons: List<GedcomPerson> = emptyList(),
    val places: List<GedcomPlace> = emptyList(),
    val sources: List<GedcomSource> = emptyList(),
    val events: List<GedcomEvent> = emptyList()
) {
    val totalCount: Int get() = persons.size + places.size + sources.size + events.size
    val isEmpty: Boolean get() = totalCount == 0
}

// MARK: - Timeline Entry

data class TimelineEntry(
    val event: GedcomEvent,
    val personName: String,
    val personXref: String
) {
    val year: Int? get() {
        val yearRegex = Regex("""\b(\d{4})\b""")
        return yearRegex.find(event.dateValue)?.groupValues?.get(1)?.toIntOrNull()
    }

    val decade: Int? get() = year?.let { (it / 10) * 10 }
}

// MARK: - Statistics Models

data class EventTypeCount(
    val eventType: String,
    val count: Int
)

data class DecadeCount(
    val decade: Int,
    val count: Int
)

// MARK: - Parse Result

data class GedcomParseResult(
    val persons: List<GedcomPerson>,
    val families: List<GedcomFamily>,
    val childLinks: List<GedcomChildLink>,
    val events: List<GedcomEvent>,
    val places: List<GedcomPlace>,
    val sources: List<GedcomSource>,
    val media: List<GedcomMedia>,
    val lineCount: Int
)

// MARK: - Enums

enum class PersonSort {
    SURNAME, GIVEN_NAME
}

enum class SidebarSection(val label: String) {
    OVERVIEW("Overview"),
    SEARCH("Search"),
    TIMELINE("Timeline"),
    ISSUES("Issues"),
    PEOPLE("People"),
    PEDIGREE("Pedigree"),
    FAN_CHART("Fan Chart"),
    DESCENDANT_CHART("Descendants"),
    RELATIONSHIPS("Relationships"),
    FAMILIES("Families"),
    PLACES("Places"),
    SOURCES("Sources"),
    MEDIA("Media"),
    MERGE("Merge"),
    VALIDATION("Validation"),
    REPORTS("Reports"),
    BOOKMARKS("Bookmarks"),
    NOTES("Notes"),
    TASKS("Tasks"),
    VERSION_HISTORY("Version History"),
    IMAGE_DEDUPE("Image Dedupe"),
    CLEANUP("Cleanup"),
    CLOUD_SYNC("Cloud Sync"),
    AI_CHAT("AI Chat"),
    AI_SETTINGS("AI Settings"),
    SETTINGS("Settings");

    val iconName: String
        get() = when (this) {
            OVERVIEW -> "dashboard"
            SEARCH -> "search"
            TIMELINE -> "timeline"
            ISSUES -> "warning"
            PEOPLE -> "people"
            PEDIGREE -> "tree"
            FAN_CHART -> "fan"
            DESCENDANT_CHART -> "descendants"
            RELATIONSHIPS -> "relationship"
            FAMILIES -> "house"
            PLACES -> "pin"
            SOURCES -> "book"
            MEDIA -> "camera"
            MERGE -> "merge"
            VALIDATION -> "verified_user"
            REPORTS -> "document"
            BOOKMARKS -> "bookmark"
            NOTES -> "note"
            TASKS -> "checklist"
            VERSION_HISTORY -> "history"
            IMAGE_DEDUPE -> "image_dedupe"
            CLEANUP -> "cleanup"
            CLOUD_SYNC -> "cloud"
            AI_CHAT -> "chat"
            AI_SETTINGS -> "ai_settings"
            SETTINGS -> "gear"
        }
}

// MARK: - Settings Enums

enum class DateDisplayFormat(val label: String) {
    RAW("GEDCOM Raw"),
    HUMAN_READABLE("Human Readable"),
    LOCALE("Locale Default")
}

enum class NameDisplayFormat(val label: String) {
    GIVEN_SURNAME("Given Surname"),
    SURNAME_GIVEN_COMMA("Surname, Given"),
    SURNAME_GIVEN("Surname Given")
}
