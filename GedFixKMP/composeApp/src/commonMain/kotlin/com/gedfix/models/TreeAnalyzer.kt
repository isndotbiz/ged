package com.gedfix.models

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Tree consistency checker - detects issues in genealogy data.
 * Ported from Swift TreeAnalyzer.swift.
 * All 15+ issue types: date issues, relationship issues, data quality, duplicates.
 */
class TreeAnalyzer(
    private val persons: List<GedcomPerson>,
    private val families: List<GedcomFamily>,
    private val events: List<GedcomEvent>,
    private val childLinks: List<GedcomChildLink>,
    private val currentYear: Int = 2026
) {

    // Lookup tables built once
    private val personByXref: Map<String, GedcomPerson> = persons.associateBy { it.xref }
    private val eventsByOwner: Map<String, List<GedcomEvent>> = events.groupBy { it.ownerXref }
    private val childLinksByFamily: Map<String, List<GedcomChildLink>> = childLinks.groupBy { it.familyXref }
    private val childLinksByChild: Map<String, List<GedcomChildLink>> = childLinks.groupBy { it.childXref }
    private val familiesByXref: Map<String, GedcomFamily> = families.associateBy { it.xref }

    private val spouseXrefs: Set<String> = buildSet {
        for (fam in families) {
            if (fam.partner1Xref.isNotEmpty()) add(fam.partner1Xref)
            if (fam.partner2Xref.isNotEmpty()) add(fam.partner2Xref)
        }
    }
    private val childXrefSet: Set<String> = childLinks.map { it.childXref }.toSet()

    /**
     * Run all analysis checks and return collected issues, sorted by severity.
     */
    fun analyze(): List<TreeIssue> {
        val issues = mutableListOf<TreeIssue>()

        issues.addAll(checkDateIssues())
        issues.addAll(checkFactsAfterDeath())
        issues.addAll(checkFactsBeforeBirth())
        issues.addAll(checkMarriedTooYoung())
        issues.addAll(checkRelationshipIssues())
        issues.addAll(checkLargeSpouseAgeDifference())
        issues.addAll(checkDiedTooYoungToBeSpouse())
        issues.addAll(checkDataQuality())
        issues.addAll(checkMarriedNameAsMaidenName())
        issues.addAll(checkInconsistentSurnameSpelling())
        issues.addAll(checkInconsistentPlaceSpelling())
        issues.addAll(checkDuplicates())

        return issues.sortedBy { it.severity.ordinal }
    }

    // MARK: - Date Checks

    private fun checkDateIssues(): List<TreeIssue> {
        val issues = mutableListOf<TreeIssue>()

        for (person in persons) {
            val personEvents = eventsByOwner[person.xref] ?: emptyList()
            val birthYear = extractFirstYear(personEvents, "BIRT")
            val deathYear = extractFirstYear(personEvents, "DEAT")

            // Born after death
            if (birthYear != null && deathYear != null && birthYear > deathYear) {
                issues.add(createTreeIssue(
                    category = IssueCategory.DATE_ISSUE,
                    severity = IssueSeverity.CRITICAL,
                    personXref = person.xref,
                    title = "Born after death",
                    detail = "${person.displayName} has birth year $birthYear but death year $deathYear.",
                    suggestion = "Check and correct the birth or death date."
                ))
            }

            // Future dates
            for (event in personEvents) {
                val year = GedcomParser.extractYear(event.dateValue)
                if (year != null && year > currentYear) {
                    issues.add(createTreeIssue(
                        category = IssueCategory.DATE_ISSUE,
                        severity = IssueSeverity.CRITICAL,
                        personXref = person.xref,
                        title = "Future date on ${event.displayType}",
                        detail = "${person.displayName} has ${event.displayType} in year $year, which is in the future.",
                        suggestion = "Verify and correct this date."
                    ))
                }
            }

            // Missing birth date
            if (birthYear == null) {
                issues.add(createTreeIssue(
                    category = IssueCategory.DATE_ISSUE,
                    severity = IssueSeverity.INFO,
                    personXref = person.xref,
                    title = "Missing birth date",
                    detail = "${person.displayName} has no birth date recorded.",
                    suggestion = "Add a birth date if known."
                ))
            }

            // Missing death date for people born > 110 years ago
            if (birthYear != null && (currentYear - birthYear) > 110 && deathYear == null && !person.isLiving) {
                val hasDeath = personEvents.any { it.eventType == "DEAT" || it.eventType == "BURI" }
                if (!hasDeath) {
                    issues.add(createTreeIssue(
                        category = IssueCategory.DATE_ISSUE,
                        severity = IssueSeverity.WARNING,
                        personXref = person.xref,
                        title = "Missing death date (born $birthYear)",
                        detail = "${person.displayName} was born in $birthYear but has no death record. Likely deceased.",
                        suggestion = "Add a death date or mark as deceased."
                    ))
                }
            }
        }

        // Family-level date checks
        for (family in families) {
            val familyEvents = eventsByOwner[family.xref] ?: emptyList()
            val marriageYear = extractFirstYear(familyEvents, "MARR")

            val children = childLinksByFamily[family.xref] ?: emptyList()
            val childBirthYears = mutableListOf<Pair<String, Int>>()

            for (link in children) {
                val childEvents = eventsByOwner[link.childXref] ?: emptyList()
                val by = extractFirstYear(childEvents, "BIRT")
                if (by != null) {
                    val childName = personByXref[link.childXref]?.displayName ?: link.childXref
                    childBirthYears.add(Pair(childName, by))
                }
            }

            // Check partners married before birth
            for (partnerXref in listOf(family.partner1Xref, family.partner2Xref)) {
                if (partnerXref.isEmpty()) continue
                val partner = personByXref[partnerXref] ?: continue
                val partnerEvents = eventsByOwner[partnerXref] ?: emptyList()
                val partnerBirth = extractFirstYear(partnerEvents, "BIRT")

                if (partnerBirth != null && marriageYear != null && marriageYear < partnerBirth) {
                    issues.add(createTreeIssue(
                        category = IssueCategory.DATE_ISSUE,
                        severity = IssueSeverity.CRITICAL,
                        personXref = partnerXref,
                        familyXref = family.xref,
                        title = "Married before birth",
                        detail = "${partner.displayName} was born in $partnerBirth but married in $marriageYear.",
                        suggestion = "Check marriage and birth dates."
                    ))
                }

                // Child born before parent
                if (partnerBirth != null) {
                    for ((childName, childBirth) in childBirthYears) {
                        if (childBirth <= partnerBirth) {
                            issues.add(createTreeIssue(
                                category = IssueCategory.DATE_ISSUE,
                                severity = IssueSeverity.CRITICAL,
                                personXref = partnerXref,
                                familyXref = family.xref,
                                title = "Child born before parent",
                                detail = "$childName born in $childBirth, but parent ${partner.displayName} born in $partnerBirth.",
                                suggestion = "Verify birth dates of parent and child."
                            ))
                        }
                    }
                }
            }

            // Children born with large gap (> 30 years)
            val sortedChildYears = childBirthYears.sortedBy { it.second }
            for (i in 1 until sortedChildYears.size) {
                val gap = sortedChildYears[i].second - sortedChildYears[i - 1].second
                if (gap > 30) {
                    issues.add(createTreeIssue(
                        category = IssueCategory.DATE_ISSUE,
                        severity = IssueSeverity.WARNING,
                        familyXref = family.xref,
                        title = "Large gap between children",
                        detail = "${sortedChildYears[i - 1].first} (born ${sortedChildYears[i - 1].second}) and ${sortedChildYears[i].first} (born ${sortedChildYears[i].second}) are $gap years apart.",
                        suggestion = "Verify these children belong to the same family."
                    ))
                }
            }
        }

        return issues
    }

    // MARK: - Relationship Checks

    private fun checkRelationshipIssues(): List<TreeIssue> {
        val issues = mutableListOf<TreeIssue>()

        for (person in persons) {
            val isChild = person.xref in childXrefSet
            val isSpouse = person.xref in spouseXrefs

            // Missing parents
            if (!isChild) {
                issues.add(createTreeIssue(
                    category = IssueCategory.RELATIONSHIP_ISSUE,
                    severity = IssueSeverity.INFO,
                    personXref = person.xref,
                    title = "No parents linked",
                    detail = "${person.displayName} is not linked to any family as a child.",
                    suggestion = "Add parent information if known."
                ))
            }

            // Orphaned records
            if (!isChild && !isSpouse) {
                issues.add(createTreeIssue(
                    category = IssueCategory.RELATIONSHIP_ISSUE,
                    severity = IssueSeverity.WARNING,
                    personXref = person.xref,
                    title = "Orphaned record",
                    detail = "${person.displayName} is not connected to any family as child or spouse.",
                    suggestion = "Link this person to the appropriate family."
                ))
            }

            // Missing spouse (has marriage events but no FAMS link)
            val personEvents = eventsByOwner[person.xref] ?: emptyList()
            val hasMarriageHint = personEvents.any { it.eventType == "MARR" || it.eventType == "DIV" }
            if (hasMarriageHint && !isSpouse) {
                issues.add(createTreeIssue(
                    category = IssueCategory.RELATIONSHIP_ISSUE,
                    severity = IssueSeverity.WARNING,
                    personXref = person.xref,
                    title = "Marriage event but no spouse",
                    detail = "${person.displayName} has marriage/divorce events but is not linked as a spouse in any family.",
                    suggestion = "Link this person to the correct family record."
                ))
            }
        }

        // Circular relationship detection
        issues.addAll(detectCircularRelationships())

        return issues
    }

    private fun detectCircularRelationships(): List<TreeIssue> {
        val issues = mutableListOf<TreeIssue>()
        val reportedCircles = mutableSetOf<String>()

        for (person in persons) {
            val visited = mutableSetOf<String>()
            val stack = ArrayDeque<String>()
            stack.addLast(person.xref)

            while (stack.isNotEmpty()) {
                val current = stack.removeLast()

                if (current in visited) {
                    if (current == person.xref && visited.size > 1 && current !in reportedCircles) {
                        reportedCircles.add(current)
                        issues.add(createTreeIssue(
                            category = IssueCategory.RELATIONSHIP_ISSUE,
                            severity = IssueSeverity.CRITICAL,
                            personXref = person.xref,
                            title = "Circular relationship detected",
                            detail = "${person.displayName} appears to be their own ancestor through a chain of parent links.",
                            suggestion = "Review parent-child links for errors in the ancestor chain."
                        ))
                    }
                    continue
                }
                visited.add(current)

                // Find parents of current
                val familyLinks = childLinksByChild[current] ?: emptyList()
                for (link in familyLinks) {
                    val family = familiesByXref[link.familyXref] ?: continue
                    if (family.partner1Xref.isNotEmpty()) stack.addLast(family.partner1Xref)
                    if (family.partner2Xref.isNotEmpty()) stack.addLast(family.partner2Xref)
                }
            }
        }

        return issues
    }

    // MARK: - Data Quality Checks

    private fun checkDataQuality(): List<TreeIssue> {
        val issues = mutableListOf<TreeIssue>()
        val suspiciousRegex = Regex("[0-9@#\$%^&*()_+=\\[\\]{}|\\\\<>~`]")

        for (person in persons) {
            val personEvents = eventsByOwner[person.xref] ?: emptyList()

            // Missing sex/gender
            if (person.sex == "U" || person.sex.isEmpty()) {
                issues.add(createTreeIssue(
                    category = IssueCategory.DATA_QUALITY,
                    severity = IssueSeverity.INFO,
                    personXref = person.xref,
                    title = "Missing gender",
                    detail = "${person.displayName} has no sex/gender recorded.",
                    suggestion = "Set the sex field to M or F."
                ))
            }

            // Names with suspicious characters
            val fullName = "${person.givenName} ${person.surname}"
            if (suspiciousRegex.containsMatchIn(fullName)) {
                issues.add(createTreeIssue(
                    category = IssueCategory.DATA_QUALITY,
                    severity = IssueSeverity.WARNING,
                    personXref = person.xref,
                    title = "Suspicious characters in name",
                    detail = "${person.displayName} contains unusual characters.",
                    suggestion = "Review and clean up the name field.",
                    isAutoFixable = true
                ))
            }

            // Empty name
            if (person.givenName.isEmpty() && person.surname.isEmpty()) {
                issues.add(createTreeIssue(
                    category = IssueCategory.DATA_QUALITY,
                    severity = IssueSeverity.WARNING,
                    personXref = person.xref,
                    title = "Missing name",
                    detail = "Person ${person.xref} has no given name or surname.",
                    suggestion = "Add a name for this person."
                ))
            }

            // Duplicate facts (same event type with same date)
            val eventsByType = personEvents
                .filter { it.dateValue.isNotEmpty() }
                .groupBy { it.eventType }
            for ((_, typeEvents) in eventsByType) {
                val dateGroups = typeEvents.groupBy { it.dateValue }
                for ((date, dupes) in dateGroups) {
                    if (dupes.size > 1) {
                        val displayType = dupes[0].displayType
                        issues.add(createTreeIssue(
                            category = IssueCategory.DATA_QUALITY,
                            severity = IssueSeverity.WARNING,
                            personXref = person.xref,
                            title = "Duplicate $displayType fact",
                            detail = "${person.displayName} has ${dupes.size} $displayType events with date $date.",
                            suggestion = "Remove the duplicate $displayType record.",
                            isAutoFixable = true
                        ))
                    }
                }
            }

            // Missing sources on key events
            val keyEventTypes = setOf("BIRT", "DEAT")
            for (event in personEvents) {
                if (event.eventType in keyEventTypes && event.dateValue.isEmpty() && event.place.isEmpty()) {
                    issues.add(createTreeIssue(
                        category = IssueCategory.DATA_QUALITY,
                        severity = IssueSeverity.INFO,
                        personXref = person.xref,
                        title = "Sparse ${event.displayType} record",
                        detail = "${person.displayName} has a ${event.displayType} event with no date and no place.",
                        suggestion = "Add date and place details for this event."
                    ))
                }
            }

            // Place formatting inconsistencies
            val placesUsed = personEvents.map { it.place }.filter { it.isNotEmpty() }
            for (place in placesUsed) {
                if (!place.contains(",") && place.length > 3) {
                    issues.add(createTreeIssue(
                        category = IssueCategory.DATA_QUALITY,
                        severity = IssueSeverity.INFO,
                        personXref = person.xref,
                        title = "Incomplete place format",
                        detail = "Place \"$place\" on ${person.displayName} may be missing locality details (no commas found).",
                        suggestion = "Use hierarchical format: City, County, State, Country."
                    ))
                    break // Only flag once per person
                }
            }
        }

        return issues
    }

    // MARK: - Duplicate Detection

    private fun checkDuplicates(): List<TreeIssue> {
        val issues = mutableListOf<TreeIssue>()
        val reportedPairs = mutableSetOf<String>()

        data class PersonRecord(
            val person: GedcomPerson,
            val birthYear: Int?,
            val normalizedGiven: String,
            val normalizedSurname: String
        )

        val records = persons.map { person ->
            val personEvents = eventsByOwner[person.xref] ?: emptyList()
            val by = extractFirstYear(personEvents, "BIRT")
            PersonRecord(
                person = person,
                birthYear = by,
                normalizedGiven = person.givenName.lowercase().trim(),
                normalizedSurname = person.surname.lowercase().trim()
            )
        }

        // Group by surname for efficiency
        val bySurname = records.groupBy { it.normalizedSurname }

        for ((_, group) in bySurname) {
            if (group.isEmpty()) continue
            for (i in group.indices) {
                for (j in (i + 1) until group.size) {
                    val a = group[i]
                    val b = group[j]

                    // Same given name (exact or fuzzy)
                    val givenMatch = a.normalizedGiven == b.normalizedGiven ||
                        (a.normalizedGiven.length > 2 && b.normalizedGiven.length > 2 &&
                            levenshteinSimilarity(a.normalizedGiven, b.normalizedGiven) > 0.80)

                    if (!givenMatch) continue

                    // Birth year within 5 years
                    val yearMatch = if (a.birthYear != null && b.birthYear != null) {
                        abs(a.birthYear - b.birthYear) <= 5
                    } else {
                        // If either has no birth year, still flag if names match exactly
                        a.normalizedGiven == b.normalizedGiven
                    }

                    if (!yearMatch) continue

                    val pairKey = listOf(a.person.xref, b.person.xref).sorted().joinToString("-")
                    if (pairKey in reportedPairs) continue
                    reportedPairs.add(pairKey)

                    val yearDetail = if (a.birthYear != null && b.birthYear != null) {
                        " (born ${a.birthYear} vs ${b.birthYear})"
                    } else {
                        ""
                    }

                    issues.add(createTreeIssue(
                        category = IssueCategory.POTENTIAL_DUPLICATE,
                        severity = IssueSeverity.WARNING,
                        personXref = a.person.xref,
                        title = "Possible duplicate",
                        detail = "${a.person.displayName} and ${b.person.displayName} have similar names$yearDetail.",
                        suggestion = "Review and merge if these are the same person."
                    ))
                }
            }
        }

        return issues
    }

    // MARK: - Fact After Death / Before Birth Checks

    /**
     * Check #1: Fact occurring after death.
     * Any non-death event with a date after the person's death year.
     */
    private fun checkFactsAfterDeath(): List<TreeIssue> {
        val issues = mutableListOf<TreeIssue>()
        val excludedTypes = setOf("DEAT", "BURI", "PROB", "WILL") // These can legitimately be at/after death

        for (person in persons) {
            val personEvents = eventsByOwner[person.xref] ?: emptyList()
            val deathYear = extractFirstYear(personEvents, "DEAT") ?: continue

            for (event in personEvents) {
                if (event.eventType in excludedTypes) continue
                if (event.eventType == "BIRT") continue
                val eventYear = GedcomParser.extractYear(event.dateValue) ?: continue

                if (eventYear > deathYear) {
                    issues.add(createTreeIssue(
                        category = IssueCategory.DATE_ISSUE,
                        severity = IssueSeverity.WARNING,
                        personXref = person.xref,
                        title = "${event.displayType} after death",
                        detail = "${person.displayName} has ${event.displayType} in $eventYear but died in $deathYear.",
                        suggestion = "Verify the ${event.displayType.lowercase()} date or the death date."
                    ))
                }
            }
        }

        return issues
    }

    /**
     * Check #2: Fact occurring before birth.
     * Any non-birth event with a date before the person's birth year.
     */
    private fun checkFactsBeforeBirth(): List<TreeIssue> {
        val issues = mutableListOf<TreeIssue>()

        for (person in persons) {
            val personEvents = eventsByOwner[person.xref] ?: emptyList()
            val birthYear = extractFirstYear(personEvents, "BIRT") ?: continue

            for (event in personEvents) {
                if (event.eventType == "BIRT") continue
                val eventYear = GedcomParser.extractYear(event.dateValue) ?: continue

                if (eventYear < birthYear) {
                    issues.add(createTreeIssue(
                        category = IssueCategory.DATE_ISSUE,
                        severity = IssueSeverity.WARNING,
                        personXref = person.xref,
                        title = "${event.displayType} before birth",
                        detail = "${person.displayName} has ${event.displayType} in $eventYear but was born in $birthYear.",
                        suggestion = "Verify the ${event.displayType.lowercase()} date or the birth date."
                    ))
                }
            }
        }

        return issues
    }

    // MARK: - Marriage Age Checks

    /**
     * Check #4: Married too young.
     * Person's marriage date is before their 14th birthday.
     */
    private fun checkMarriedTooYoung(): List<TreeIssue> {
        val issues = mutableListOf<TreeIssue>()

        for (family in families) {
            val familyEvents = eventsByOwner[family.xref] ?: emptyList()
            val marriageYear = extractFirstYear(familyEvents, "MARR") ?: continue
            val marriageDateStr = familyEvents.firstOrNull { it.eventType == "MARR" }?.dateValue ?: ""

            for (partnerXref in listOf(family.partner1Xref, family.partner2Xref)) {
                if (partnerXref.isEmpty()) continue
                val partner = personByXref[partnerXref] ?: continue
                val partnerEvents = eventsByOwner[partnerXref] ?: emptyList()
                val birthYear = extractFirstYear(partnerEvents, "BIRT") ?: continue

                val ageAtMarriage = marriageYear - birthYear
                if (ageAtMarriage < 14) {
                    issues.add(createTreeIssue(
                        category = IssueCategory.DATE_ISSUE,
                        severity = IssueSeverity.WARNING,
                        personXref = partnerXref,
                        familyXref = family.xref,
                        title = "Married too young",
                        detail = "${partner.displayName} (born $birthYear) was only about $ageAtMarriage when married${if (marriageDateStr.isNotEmpty()) " on $marriageDateStr" else " in $marriageYear"}.",
                        suggestion = "Verify the birth date and marriage date."
                    ))
                }
            }
        }

        return issues
    }

    // MARK: - Spouse Relationship Checks

    /**
     * Check #3: Large spouse age difference (> 30 years).
     */
    private fun checkLargeSpouseAgeDifference(): List<TreeIssue> {
        val issues = mutableListOf<TreeIssue>()

        for (family in families) {
            if (family.partner1Xref.isEmpty() || family.partner2Xref.isEmpty()) continue

            val partner1 = personByXref[family.partner1Xref] ?: continue
            val partner2 = personByXref[family.partner2Xref] ?: continue

            val p1Events = eventsByOwner[family.partner1Xref] ?: emptyList()
            val p2Events = eventsByOwner[family.partner2Xref] ?: emptyList()

            val p1Birth = extractFirstYear(p1Events, "BIRT") ?: continue
            val p2Birth = extractFirstYear(p2Events, "BIRT") ?: continue

            val ageDiff = abs(p1Birth - p2Birth)
            if (ageDiff > 30) {
                issues.add(createTreeIssue(
                    category = IssueCategory.RELATIONSHIP_ISSUE,
                    severity = IssueSeverity.INFO,
                    familyXref = family.xref,
                    title = "Large spouse age difference",
                    detail = "${partner1.displayName} ($p1Birth) and ${partner2.displayName} ($p2Birth) are $ageDiff years apart.",
                    suggestion = "Verify birth dates for both spouses."
                ))
            }
        }

        return issues
    }

    /**
     * Check #5: Died too young to be a spouse.
     * Person died before age 14 but has a FAMS link.
     */
    private fun checkDiedTooYoungToBeSpouse(): List<TreeIssue> {
        val issues = mutableListOf<TreeIssue>()

        for (person in persons) {
            if (person.xref !in spouseXrefs) continue

            val personEvents = eventsByOwner[person.xref] ?: emptyList()
            val birthYear = extractFirstYear(personEvents, "BIRT") ?: continue
            val deathYear = extractFirstYear(personEvents, "DEAT") ?: continue

            val ageAtDeath = deathYear - birthYear
            if (ageAtDeath < 14) {
                issues.add(createTreeIssue(
                    category = IssueCategory.RELATIONSHIP_ISSUE,
                    severity = IssueSeverity.WARNING,
                    personXref = person.xref,
                    title = "Died too young to be a spouse",
                    detail = "${person.displayName} (born $birthYear, died $deathYear) lived only about $ageAtDeath years but is listed as a spouse.",
                    suggestion = "Verify the birth/death dates or the family link."
                ))
            }
        }

        return issues
    }

    // MARK: - Name & Place Consistency Checks

    /**
     * Check #6: Married name entered as maiden name.
     * Wife's surname matches husband's surname.
     */
    private fun checkMarriedNameAsMaidenName(): List<TreeIssue> {
        val issues = mutableListOf<TreeIssue>()

        for (family in families) {
            if (family.partner1Xref.isEmpty() || family.partner2Xref.isEmpty()) continue

            val husband = personByXref[family.partner1Xref] ?: continue
            val wife = personByXref[family.partner2Xref] ?: continue

            if (husband.surname.isEmpty() || wife.surname.isEmpty()) continue

            if (husband.surname.equals(wife.surname, ignoreCase = true)) {
                issues.add(createTreeIssue(
                    category = IssueCategory.DATA_QUALITY,
                    severity = IssueSeverity.INFO,
                    personXref = wife.xref,
                    familyXref = family.xref,
                    title = "Possible married name as maiden name",
                    detail = "Maiden name '${wife.surname}' of ${wife.displayName} is the same as last name of her husband ${husband.displayName}.",
                    suggestion = "If '${wife.surname}' is her married name, enter her birth surname instead."
                ))
            }
        }

        return issues
    }

    /**
     * Check #7: Inconsistent last name spelling.
     * Groups similar surnames and flags potential spelling variations.
     */
    private fun checkInconsistentSurnameSpelling(): List<TreeIssue> {
        val issues = mutableListOf<TreeIssue>()

        // Group persons by surname (non-empty only)
        val surnameGroups = persons
            .filter { it.surname.isNotEmpty() }
            .groupBy { it.surname.trim() }
            .filter { it.value.size >= 1 }

        val surnames = surnameGroups.keys.toList()
        val reportedPairs = mutableSetOf<String>()

        for (i in surnames.indices) {
            for (j in (i + 1) until surnames.size) {
                val a = surnames[i]
                val b = surnames[j]

                // Skip if identical
                if (a.equals(b, ignoreCase = true)) continue

                val pairKey = listOf(a.lowercase(), b.lowercase()).sorted().joinToString("|")
                if (pairKey in reportedPairs) continue

                // Check Levenshtein distance <= 2 or soundex match
                val aLower = a.lowercase()
                val bLower = b.lowercase()
                val maxLen = max(aLower.length, bLower.length)
                val distance = ((1.0 - levenshteinSimilarity(aLower, bLower)) * maxLen).toInt()
                val soundexMatch = soundex(aLower) == soundex(bLower)

                if (distance <= 2 || soundexMatch) {
                    reportedPairs.add(pairKey)
                    val countA = surnameGroups[a]?.size ?: 0
                    val countB = surnameGroups[b]?.size ?: 0

                    issues.add(createTreeIssue(
                        category = IssueCategory.DATA_QUALITY,
                        severity = IssueSeverity.INFO,
                        title = "Inconsistent surname spelling",
                        detail = "'$a' appears $countA time${if (countA != 1) "s" else ""}, '$b' appears $countB time${if (countB != 1) "s" else ""} — these may be the same family name.",
                        suggestion = "Consider standardizing to a single spelling."
                    ))
                }
            }
        }

        return issues
    }

    /**
     * Check #8: Inconsistent place name spelling.
     * Groups similar places and flags potential variations.
     */
    private fun checkInconsistentPlaceSpelling(): List<TreeIssue> {
        val issues = mutableListOf<TreeIssue>()

        // Collect all unique places from events
        val placeUsage = mutableMapOf<String, Int>()
        for (event in events) {
            if (event.place.isNotEmpty()) {
                placeUsage[event.place] = (placeUsage[event.place] ?: 0) + 1
            }
        }

        val places = placeUsage.keys.toList()
        val reportedPairs = mutableSetOf<String>()

        for (i in places.indices) {
            for (j in (i + 1) until places.size) {
                val a = places[i]
                val b = places[j]

                // Skip exact matches
                if (a == b) continue

                // Case-insensitive match is an easy catch
                if (a.equals(b, ignoreCase = true)) {
                    val pairKey = listOf(a.lowercase(), b.lowercase()).sorted().joinToString("|")
                    if (pairKey in reportedPairs) continue
                    reportedPairs.add(pairKey)

                    issues.add(createTreeIssue(
                        category = IssueCategory.DATA_QUALITY,
                        severity = IssueSeverity.INFO,
                        title = "Inconsistent place spelling",
                        detail = "'$a' (${placeUsage[a]} use${if ((placeUsage[a] ?: 0) != 1) "s" else ""}) and '$b' (${placeUsage[b]} use${if ((placeUsage[b] ?: 0) != 1) "s" else ""}) appear to be the same place.",
                        suggestion = "Standardize to a single spelling."
                    ))
                    continue
                }

                // For longer place names, use similarity check
                val aLower = a.lowercase()
                val bLower = b.lowercase()
                val similarity = levenshteinSimilarity(aLower, bLower)

                // Only flag very similar places (> 90% similar) to reduce noise
                if (similarity > 0.90 && aLower != bLower) {
                    val pairKey = listOf(aLower, bLower).sorted().joinToString("|")
                    if (pairKey in reportedPairs) continue
                    reportedPairs.add(pairKey)

                    issues.add(createTreeIssue(
                        category = IssueCategory.DATA_QUALITY,
                        severity = IssueSeverity.INFO,
                        title = "Inconsistent place spelling",
                        detail = "'$a' (${placeUsage[a]} use${if ((placeUsage[a] ?: 0) != 1) "s" else ""}) and '$b' (${placeUsage[b]} use${if ((placeUsage[b] ?: 0) != 1) "s" else ""}) look like the same place with different spelling.",
                        suggestion = "Standardize to a single place name format."
                    ))
                }
            }
        }

        return issues
    }

    // MARK: - Helpers

    private fun extractFirstYear(events: List<GedcomEvent>, type: String): Int? {
        return events
            .filter { it.eventType == type }
            .mapNotNull { GedcomParser.extractYear(it.dateValue) }
            .firstOrNull()
    }

    /**
     * Simple Levenshtein-based similarity (0.0 to 1.0)
     */
    private fun levenshteinSimilarity(a: String, b: String): Double {
        val aChars = a.toCharArray()
        val bChars = b.toCharArray()
        val aLen = aChars.size
        val bLen = bChars.size

        if (aLen == 0 && bLen == 0) return 1.0
        if (aLen == 0 || bLen == 0) return 0.0

        var prev = IntArray(bLen + 1) { it }
        var curr = IntArray(bLen + 1)

        for (i in 1..aLen) {
            curr[0] = i
            for (j in 1..bLen) {
                val cost = if (aChars[i - 1] == bChars[j - 1]) 0 else 1
                curr[j] = minOf(prev[j] + 1, curr[j - 1] + 1, prev[j - 1] + cost)
            }
            val temp = prev
            prev = curr
            curr = temp
        }

        val maxLen = max(aLen, bLen)
        return 1.0 - prev[bLen].toDouble() / maxLen.toDouble()
    }

    /**
     * Simple Soundex implementation for phonetic surname matching.
     * Returns a 4-character code (letter + 3 digits).
     */
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
            else -> '0' // a, e, i, o, u, h, w, y
        }

        val result = StringBuilder()
        result.append(first)

        var lastCode = charCode(clean[0])
        for (i in 1 until clean.length) {
            if (result.length >= 4) break
            val code = charCode(clean[i])
            if (code != '0' && code != lastCode) {
                result.append(code)
            }
            lastCode = code
        }

        while (result.length < 4) result.append('0')
        return result.toString()
    }
}
