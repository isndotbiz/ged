package com.gedfix.models

import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * A single parsed GEDCOM line.
 */
private data class GedLine(
    val level: Int,
    val xref: String?, // e.g. @I123@
    val tag: String,
    val value: String
)

/**
 * Line-oriented GEDCOM 5.5.1 parser.
 * Ported from Swift GedcomParser.swift / gedfix/checker.py
 */
@OptIn(ExperimentalUuidApi::class)
object GedcomParser {

    // MARK: - Public API

    fun parse(text: String, currentYear: Int = 2026): GedcomParseResult {
        // Handle BOM
        val cleaned = if (text.startsWith("\uFEFF")) text.removePrefix("\uFEFF") else text

        val rawLines = cleaned.lines()
        val parsedLines = rawLines.mapNotNull { parseLine(it) }
        val records = groupRecords(parsedLines)

        val persons = mutableListOf<GedcomPerson>()
        val families = mutableListOf<GedcomFamily>()
        val childLinks = mutableListOf<GedcomChildLink>()
        val events = mutableListOf<GedcomEvent>()
        val placesDict = mutableMapOf<String, GedcomPlace>()
        val sources = mutableListOf<GedcomSource>()
        val media = mutableListOf<GedcomMedia>()

        for (record in records) {
            val header = record.firstOrNull() ?: continue

            when {
                header.tag == "INDI" && header.xref != null -> {
                    val (person, personEvents, personMedia) = parseINDI(xref = header.xref, lines = record)
                    persons.add(person)
                    for (evt in personEvents) {
                        events.add(evt)
                        if (evt.place.isNotEmpty()) {
                            trackPlace(evt.place, placesDict)
                        }
                    }
                    media.addAll(personMedia)
                }
                header.tag == "FAM" && header.xref != null -> {
                    val (family, links, famEvents) = parseFAM(xref = header.xref, lines = record)
                    families.add(family)
                    childLinks.addAll(links)
                    for (evt in famEvents) {
                        events.add(evt)
                        if (evt.place.isNotEmpty()) {
                            trackPlace(evt.place, placesDict)
                        }
                    }
                }
                header.tag == "SOUR" && header.xref != null -> {
                    val source = parseSOUR(xref = header.xref, lines = record)
                    sources.add(source)
                }
                header.tag == "OBJE" && header.xref != null -> {
                    val mediaItem = parseOBJE(xref = header.xref, ownerXref = "", lines = record)
                    media.add(mediaItem)
                }
            }
        }

        // Detect living persons
        for (i in persons.indices) {
            val xref = persons[i].xref
            val personEvents = events.filter { it.ownerXref == xref }
            val hasDeath = personEvents.any { it.eventType == "DEAT" || it.eventType == "BURI" }

            if (hasDeath) {
                persons[i] = persons[i].copy(isLiving = false)
            } else {
                val birthYear = personEvents
                    .filter { it.eventType == "BIRT" }
                    .mapNotNull { extractYear(it.dateValue) }
                    .firstOrNull()

                persons[i] = if (birthYear != null && (currentYear - birthYear) > 110) {
                    persons[i].copy(isLiving = false)
                } else {
                    persons[i].copy(isLiving = true)
                }
            }
        }

        return GedcomParseResult(
            persons = persons.toList(),
            families = families.toList(),
            childLinks = childLinks.toList(),
            events = events.toList(),
            places = placesDict.values.toList(),
            sources = sources.toList(),
            media = media.toList(),
            lineCount = rawLines.size
        )
    }

    // MARK: - Line Parsing

    private fun parseLine(raw: String): GedLine? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null

        val parts = trimmed.split(" ", limit = 3)
        if (parts.size < 2) return null

        val level = parts[0].toIntOrNull() ?: return null
        val second = parts[1]

        // Check if second token is an xref (@X@)
        if (second.startsWith("@") && second.endsWith("@") && parts.size >= 3) {
            val xref = second
            val remaining = parts[2]
            val tagParts = remaining.split(" ", limit = 2)
            val tag = tagParts[0]
            val value = if (tagParts.size >= 2) tagParts[1] else ""
            return GedLine(level = level, xref = xref, tag = tag, value = value)
        }

        val tag = second
        val value = if (parts.size >= 3) parts[2] else ""
        return GedLine(level = level, xref = null, tag = tag, value = value)
    }

    // MARK: - Record Grouping

    private fun groupRecords(lines: List<GedLine>): List<List<GedLine>> {
        val records = mutableListOf<List<GedLine>>()
        var current = mutableListOf<GedLine>()

        for (line in lines) {
            if (line.level == 0) {
                if (current.isNotEmpty()) {
                    records.add(current.toList())
                }
                current = mutableListOf(line)
            } else {
                current.add(line)
            }
        }
        if (current.isNotEmpty()) {
            records.add(current.toList())
        }
        return records
    }

    // MARK: - INDI Parsing

    private fun parseINDI(xref: String, lines: List<GedLine>): Triple<GedcomPerson, List<GedcomEvent>, List<GedcomMedia>> {
        var givenName = ""
        var surname = ""
        var suffix = ""
        var sex = "U"
        val events = mutableListOf<GedcomEvent>()
        val mediaItems = mutableListOf<GedcomMedia>()
        var sourCount = 0
        var obiCount = 0

        var currentTag: String? = null
        var currentDate = ""
        var currentPlace = ""

        // Track inline OBJE sub-tags
        var inObje = false
        var objeFile = ""
        var objeForm = ""
        var objeTitl = ""
        var objeNote = ""

        for (line in lines.drop(1)) {
            if (line.level == 1) {
                // Flush previous event
                currentTag?.let { tag ->
                    if (isEventTag(tag)) {
                        events.add(makeEvent(owner = xref, ownerType = "INDI", type = tag, date = currentDate, place = currentPlace))
                    }
                }
                // Flush previous inline OBJE
                if (inObje && objeFile.isNotEmpty()) {
                    mediaItems.add(GedcomMedia(
                        id = Uuid.random().toString(),
                        xref = "",
                        ownerXref = xref,
                        filePath = objeFile,
                        format = objeForm,
                        title = objeTitl,
                        description = objeNote
                    ))
                }
                inObje = false
                objeFile = ""
                objeForm = ""
                objeTitl = ""
                objeNote = ""

                currentTag = line.tag
                currentDate = ""
                currentPlace = ""

                when (line.tag) {
                    "NAME" -> {
                        val parsed = parseName(line.value)
                        if (givenName.isEmpty()) {
                            givenName = parsed.first
                            surname = parsed.second
                            suffix = parsed.third
                        }
                    }
                    "SEX" -> {
                        sex = line.value.trim()
                    }
                    "SOUR" -> sourCount++
                    "OBJE" -> {
                        obiCount++
                        inObje = true
                    }
                }
            } else if (line.level == 2) {
                if (inObje) {
                    when (line.tag) {
                        "FILE" -> objeFile = line.value
                        "FORM" -> objeForm = line.value
                        "TITL" -> objeTitl = line.value
                        "NOTE" -> objeNote = line.value
                    }
                } else {
                    when (line.tag) {
                        "DATE" -> currentDate = line.value
                        "PLAC" -> currentPlace = line.value
                        "GIVN" -> if (givenName.isEmpty()) givenName = line.value
                        "SURN" -> if (surname.isEmpty()) surname = line.value
                        "NSFX" -> if (suffix.isEmpty()) suffix = line.value
                        "SOUR" -> sourCount++
                        "OBJE" -> obiCount++
                    }
                }
            }
        }

        // Flush last event
        currentTag?.let { tag ->
            if (isEventTag(tag)) {
                events.add(makeEvent(owner = xref, ownerType = "INDI", type = tag, date = currentDate, place = currentPlace))
            }
        }
        // Flush last inline OBJE
        if (inObje && objeFile.isNotEmpty()) {
            mediaItems.add(GedcomMedia(
                id = Uuid.random().toString(),
                xref = "",
                ownerXref = xref,
                filePath = objeFile,
                format = objeForm,
                title = objeTitl,
                description = objeNote
            ))
        }

        val person = GedcomPerson(
            id = Uuid.random().toString(),
            xref = xref,
            givenName = givenName.trim(),
            surname = surname.trim(),
            suffix = suffix.trim(),
            sex = sex,
            isLiving = false, // set later
            sourceCount = sourCount,
            mediaCount = obiCount,
            isValidated = sourCount > 0
        )
        return Triple(person, events, mediaItems)
    }

    // MARK: - FAM Parsing

    private fun parseFAM(xref: String, lines: List<GedLine>): Triple<GedcomFamily, List<GedcomChildLink>, List<GedcomEvent>> {
        var husbXref = ""
        var wifeXref = ""
        val children = mutableListOf<String>()
        val events = mutableListOf<GedcomEvent>()

        var currentTag: String? = null
        var currentDate = ""
        var currentPlace = ""

        for (line in lines.drop(1)) {
            if (line.level == 1) {
                // Flush event
                currentTag?.let { tag ->
                    if (isEventTag(tag)) {
                        events.add(makeEvent(owner = xref, ownerType = "FAM", type = tag, date = currentDate, place = currentPlace))
                    }
                }
                currentTag = line.tag
                currentDate = ""
                currentPlace = ""

                when (line.tag) {
                    "HUSB" -> husbXref = line.value
                    "WIFE" -> wifeXref = line.value
                    "CHIL" -> children.add(line.value)
                }
            } else if (line.level == 2) {
                if (line.tag == "DATE") currentDate = line.value
                if (line.tag == "PLAC") currentPlace = line.value
            }
        }

        currentTag?.let { tag ->
            if (isEventTag(tag)) {
                events.add(makeEvent(owner = xref, ownerType = "FAM", type = tag, date = currentDate, place = currentPlace))
            }
        }

        val family = GedcomFamily(
            id = Uuid.random().toString(),
            xref = xref,
            partner1Xref = husbXref,
            partner2Xref = wifeXref
        )

        val links = children.mapIndexed { i, childXref ->
            GedcomChildLink(familyXref = xref, childXref = childXref, childOrder = i)
        }

        return Triple(family, links, events)
    }

    // MARK: - SOUR Parsing

    private fun parseSOUR(xref: String, lines: List<GedLine>): GedcomSource {
        var title = ""
        var author = ""
        var publisher = ""
        var repository = ""

        for (line in lines.drop(1)) {
            if (line.level == 1) {
                when (line.tag) {
                    "TITL" -> title = line.value
                    "AUTH" -> author = line.value
                    "PUBL" -> publisher = line.value
                    "REPO" -> repository = line.value
                }
            }
        }

        return GedcomSource(
            id = Uuid.random().toString(),
            xref = xref,
            title = title,
            author = author,
            publisher = publisher,
            repository = repository
        )
    }

    // MARK: - OBJE Parsing

    private fun parseOBJE(xref: String, ownerXref: String, lines: List<GedLine>): GedcomMedia {
        var filePath = ""
        var format = ""
        var title = ""
        var description = ""

        for (line in lines.drop(1)) {
            if (line.level == 1) {
                when (line.tag) {
                    "FILE" -> filePath = line.value
                    "FORM" -> format = line.value
                    "TITL" -> title = line.value
                    "NOTE" -> description = line.value
                }
            } else if (line.level == 2) {
                when (line.tag) {
                    "FORM" -> if (format.isEmpty()) format = line.value
                    "TITL" -> if (title.isEmpty()) title = line.value
                }
            }
        }

        return GedcomMedia(
            id = Uuid.random().toString(),
            xref = xref,
            ownerXref = ownerXref,
            filePath = filePath,
            format = format,
            title = title,
            description = description
        )
    }

    // MARK: - Helpers

    private fun parseName(raw: String): Triple<String, String, String> {
        // GEDCOM name format: "Given /Surname/ Suffix"
        val parts = raw.split("/")
        if (parts.size >= 2) {
            val given = parts[0].trim()
            val surname = parts[1].trim()
            val suffix = if (parts.size >= 3) parts[2].trim() else ""
            return Triple(given, surname, suffix)
        }
        return Triple(raw.trim(), "", "")
    }

    private val eventTags = setOf(
        "BIRT", "DEAT", "MARR", "BURI", "CHR", "BAPM", "RESI", "CENS",
        "IMMI", "EMIG", "NATU", "GRAD", "RETI", "PROB", "WILL", "DIV",
        "EVEN", "CREM", "ADOP", "CONF", "FCOM", "ORDN", "ANUL"
    )

    private fun isEventTag(tag: String): Boolean = tag in eventTags

    private fun makeEvent(owner: String, ownerType: String, type: String, date: String, place: String): GedcomEvent {
        return GedcomEvent(
            id = Uuid.random().toString(),
            ownerXref = owner,
            ownerType = ownerType,
            eventType = type,
            dateValue = date.trim(),
            place = place.trim(),
            description = ""
        )
    }

    private fun trackPlace(name: String, dict: MutableMap<String, GedcomPlace>) {
        val existing = dict[name]
        if (existing != null) {
            dict[name] = existing.copy(eventCount = existing.eventCount + 1)
        } else {
            dict[name] = GedcomPlace(
                id = Uuid.random().toString(),
                name = name,
                normalized = name,
                eventCount = 1
            )
        }
    }

    fun extractYear(dateString: String): Int? {
        val regex = Regex("(\\d{4})")
        val match = regex.find(dateString) ?: return null
        return match.groupValues[1].toIntOrNull()
    }
}
