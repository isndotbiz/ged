package com.gedfix.models

/**
 * Version control models for tracking tree changes.
 * Every mutation to the tree creates a version entry, enabling
 * history browsing and revert-to-snapshot capability.
 */

data class TreeVersion(
    val id: String,
    val timestamp: String,          // ISO 8601
    val description: String,        // "Merged 3 duplicate persons", "Imported ancestry.ged"
    val changeType: ChangeType,     // IMPORT, EDIT, MERGE, DELETE, EXPORT, BULK_FIX
    val changedRecords: Int,        // how many records affected
    val gedcomSnapshot: String      // full GEDCOM snapshot for revert
) {
    val displayType: String
        get() = changeType.label

    val displayIcon: String
        get() = changeType.icon
}

enum class ChangeType(val label: String, val icon: String) {
    IMPORT("Import", "\u2B07"),           // Down arrow
    EDIT("Edit", "\u270E"),               // Pencil
    MERGE("Merge", "\u21C4"),             // Merge arrows
    DELETE("Delete", "\u2716"),           // X mark
    EXPORT("Export", "\u2B06"),           // Up arrow
    BULK_FIX("Bulk Fix", "\u2699");      // Gear

    companion object {
        fun fromString(value: String): ChangeType =
            entries.firstOrNull { it.name == value } ?: EDIT
    }
}
