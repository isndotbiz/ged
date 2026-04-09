package com.gedfix.models

/**
 * Bookmark model for quick-access to frequently viewed persons.
 */

data class PersonBookmark(
    val id: String,
    val personXref: String,
    val label: String,          // custom label, or auto-filled with person name
    val createdAt: String       // ISO 8601
)
