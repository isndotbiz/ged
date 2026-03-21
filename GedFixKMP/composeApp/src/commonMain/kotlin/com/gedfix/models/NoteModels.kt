package com.gedfix.models

/**
 * Research notes that can be attached to any entity (person, family, source).
 * Supports free-form content for genealogical research documentation.
 */

data class ResearchNote(
    val id: String,
    val ownerXref: String,      // person/family/source xref, or empty for global
    val ownerType: String,      // "INDI", "FAM", "SOUR", or "GLOBAL"
    val title: String,
    val content: String,
    val createdAt: String,      // ISO 8601
    val updatedAt: String       // ISO 8601
) {
    val isGlobal: Boolean get() = ownerXref.isEmpty() || ownerType == "GLOBAL"

    val displayOwnerType: String
        get() = when (ownerType) {
            "INDI" -> "Person"
            "FAM" -> "Family"
            "SOUR" -> "Source"
            "GLOBAL" -> "Global"
            else -> ownerType
        }

    val previewContent: String
        get() = if (content.length > 120) content.take(120) + "..." else content
}
