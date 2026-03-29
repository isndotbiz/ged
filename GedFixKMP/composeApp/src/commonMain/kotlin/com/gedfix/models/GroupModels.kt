package com.gedfix.models

data class PersonGroup(
    val id: String,
    val name: String,
    val color: String = "",
    val description: String = "",
    val createdAt: String = ""
)

data class GroupMember(
    val groupId: String,
    val personXref: String,
    val addedAt: String = ""
)
