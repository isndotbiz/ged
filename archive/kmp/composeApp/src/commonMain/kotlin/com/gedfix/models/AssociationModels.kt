package com.gedfix.models

data class Association(
    val id: String,
    val person1Xref: String,
    val person2Xref: String,
    val relationshipType: String = "",
    val description: String = "",
    val createdAt: String = ""
)

enum class AssociationType(val label: String) {
    NEIGHBOR("Neighbor"),
    FRIEND("Friend"),
    WITNESS("Witness"),
    GODPARENT("Godparent"),
    EMPLOYER("Employer"),
    EMPLOYEE("Employee"),
    BOARDER("Boarder"),
    GUARDIAN("Guardian"),
    WARD("Ward"),
    BUSINESS_PARTNER("Business Partner"),
    FELLOW_SOLDIER("Fellow Soldier"),
    FELLOW_PASSENGER("Fellow Passenger"),
    OTHER("Other");

    companion object {
        fun fromString(s: String): AssociationType = entries.firstOrNull { it.name == s } ?: OTHER
    }
}
