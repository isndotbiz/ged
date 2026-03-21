package com.gedfix.models

import java.net.URLEncoder

data class ResearchSuggestion(
    val title: String,
    val description: String,
    val url: String,
    val source: String,
    val icon: String,
    val priority: Int
)

object ResearchSuggestionEngine {

    fun suggestionsFor(person: GedcomPerson, events: List<GedcomEvent>): List<ResearchSuggestion> {
        val suggestions = mutableListOf<ResearchSuggestion>()
        val given = person.givenName.trim()
        val surname = person.surname.trim()
        if (given.isEmpty() && surname.isEmpty()) return suggestions

        val birthEvent = events.firstOrNull { it.eventType == "BIRT" }
        val deathEvent = events.firstOrNull { it.eventType == "DEAT" }
        val birthYear = birthEvent?.let { GedcomParser.extractYear(it.dateValue) }
        val deathYear = deathEvent?.let { GedcomParser.extractYear(it.dateValue) }
        val birthPlace = birthEvent?.place ?: ""
        val deathPlace = deathEvent?.place ?: ""

        val encodedGiven = urlEncode(given)
        val encodedSurname = urlEncode(surname)

        // 1. FamilySearch person search (always suggest)
        val fsUrl = buildString {
            append("https://www.familysearch.org/search/record/results?")
            append("q.givenName=$encodedGiven&q.surname=$encodedSurname")
            if (birthYear != null) {
                append("&q.birthLikeDate.from=${birthYear - 5}&q.birthLikeDate.to=${birthYear + 5}")
            }
            if (birthPlace.isNotEmpty()) {
                append("&q.birthLikePlace=${urlEncode(birthPlace)}")
            }
        }
        suggestions.add(
            ResearchSuggestion(
                title = "Search FamilySearch Records",
                description = "Search all historical records for $given $surname" +
                    if (birthYear != null) " (born ~$birthYear)" else "",
                url = fsUrl,
                source = "FamilySearch",
                icon = "\uD83C\uDF10",
                priority = 1
            )
        )

        // 2. FindAGrave (if death date or no birth date -- could be deceased)
        if (deathYear != null || !person.isLiving) {
            val fagUrl = buildString {
                append("https://www.findagrave.com/memorial/search?")
                append("firstname=$encodedGiven&lastname=$encodedSurname")
                if (birthYear != null) append("&birthyear=$birthYear")
                if (deathYear != null) append("&deathyear=$deathYear")
            }
            suggestions.add(
                ResearchSuggestion(
                    title = "Search Find A Grave",
                    description = "Look for burial records and memorial for $given $surname" +
                        if (deathPlace.isNotEmpty()) " near $deathPlace" else "",
                    url = fagUrl,
                    source = "Find A Grave",
                    icon = "\u26B0",
                    priority = if (deathYear != null) 2 else 4
                )
            )
        }

        // 3. Census records (if birth year known, suggest relevant US census years)
        if (birthYear != null) {
            val censusYears = listOf(1850, 1860, 1870, 1880, 1900, 1910, 1920, 1930, 1940, 1950)
            val relevantYears = censusYears.filter { year ->
                val age = year - birthYear
                age in 0..100
            }
            if (relevantYears.isNotEmpty()) {
                val yearList = relevantYears.joinToString(", ")
                val firstCensusYear = relevantYears.first()
                val censusUrl = buildString {
                    append("https://www.familysearch.org/search/record/results?")
                    append("q.givenName=$encodedGiven&q.surname=$encodedSurname")
                    append("&q.birthLikeDate.from=${birthYear - 3}&q.birthLikeDate.to=${birthYear + 3}")
                    append("&f.collectionId=1325221") // US Census collection
                }
                suggestions.add(
                    ResearchSuggestion(
                        title = "Search US Census Records",
                        description = "$given $surname may appear in the $yearList census" +
                            if (birthPlace.isNotEmpty()) " ($birthPlace area)" else "",
                        url = censusUrl,
                        source = "FamilySearch Census",
                        icon = "\uD83D\uDCCA",
                        priority = 2
                    )
                )
            }
        }

        // 4. Newspapers / Chronicling America (obituary search)
        val newsUrl = buildString {
            append("https://chroniclingamerica.loc.gov/search/pages/results/?")
            append("andtext=${urlEncode("$given $surname")}")
            if (deathYear != null) {
                append("&dateFilterType=yearRange&date1=${deathYear - 2}&date2=${deathYear + 2}")
            } else if (birthYear != null) {
                append("&dateFilterType=yearRange&date1=${birthYear + 40}&date2=${birthYear + 90}")
            }
        }
        suggestions.add(
            ResearchSuggestion(
                title = "Search Historic Newspapers",
                description = "Look for obituaries, marriage announcements, or news about $given $surname",
                url = newsUrl,
                source = "Chronicling America",
                icon = "\uD83D\uDCF0",
                priority = if (deathYear != null) 3 else 5
            )
        )

        // 5. FamilySearch family tree (always useful)
        val treeUrl = buildString {
            append("https://www.familysearch.org/tree/find/name?")
            append("self=$encodedGiven+$encodedSurname")
            if (birthYear != null) append("&birth=$birthYear")
        }
        suggestions.add(
            ResearchSuggestion(
                title = "Search FamilySearch Family Tree",
                description = "Check if other researchers have already documented $given $surname",
                url = treeUrl,
                source = "FamilySearch Tree",
                icon = "\uD83C\uDF33",
                priority = 3
            )
        )

        return suggestions.sortedBy { it.priority }
    }

    private fun urlEncode(value: String): String {
        return URLEncoder.encode(value, "UTF-8").replace("+", "%20")
    }
}
