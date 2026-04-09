package com.gedfix.models

data class ResearchLogEntry(
    val id: String,
    val personXref: String = "",
    val repository: String = "",
    val searchTerms: String = "",
    val recordsViewed: String = "",
    val conclusion: String = "",
    val sourceXref: String = "",
    val resultType: ResearchResultType = ResearchResultType.NEGATIVE,
    val searchDate: String = "",
    val createdAt: String = ""
)

enum class ResearchResultType(val label: String) {
    POSITIVE("Found relevant records"),
    NEGATIVE("No relevant records found"),
    INCONCLUSIVE("Results inconclusive");

    companion object {
        fun fromString(s: String): ResearchResultType = entries.firstOrNull { it.name == s } ?: NEGATIVE
    }
}
