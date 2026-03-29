package com.gedfix.models

data class AlternateName(
    val id: String,
    val personXref: String,
    val givenName: String = "",
    val surname: String = "",
    val suffix: String = "",
    val nameType: String = "",
    val source: String = ""
) {
    val displayName: String
        get() = listOf(givenName, surname, suffix)
            .filter { it.isNotEmpty() }
            .joinToString(" ")
}

enum class NameType(val label: String) {
    BIRTH("Birth Name"),
    MARRIED("Married Name"),
    ALIAS("Alias/AKA"),
    RELIGIOUS("Religious Name"),
    IMMIGRANT("Immigrant Name"),
    NICKNAME("Nickname"),
    MAIDEN("Maiden Name"),
    PROFESSIONAL("Professional Name"),
    OTHER("Other");

    companion object {
        fun fromString(s: String): NameType = entries.firstOrNull { it.name == s } ?: OTHER
    }
}
