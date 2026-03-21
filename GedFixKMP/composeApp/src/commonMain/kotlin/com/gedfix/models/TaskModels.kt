package com.gedfix.models

/**
 * Research task management for tracking genealogy to-do items.
 * Tasks can be associated with specific persons or be global.
 */

data class ResearchTask(
    val id: String,
    val personXref: String,     // empty for global tasks
    val title: String,
    val description: String,
    val status: TaskStatus,
    val priority: TaskPriority,
    val dueDate: String,        // ISO 8601 date or empty
    val createdAt: String,      // ISO 8601
    val completedAt: String     // ISO 8601 or empty
) {
    val isOverdue: Boolean
        get() = dueDate.isNotEmpty() && status != TaskStatus.DONE &&
                dueDate < createdAt // simplified comparison; timestamps are ISO so string compare works

    val displayStatus: String get() = status.label
    val displayPriority: String get() = priority.label
}

enum class TaskStatus(val label: String, val icon: String) {
    TODO("To Do", "\u2610"),            // Empty checkbox
    IN_PROGRESS("In Progress", "\u23F3"), // Hourglass
    DONE("Done", "\u2611");              // Checked checkbox

    companion object {
        fun fromString(value: String): TaskStatus =
            entries.firstOrNull { it.name == value } ?: TODO
    }
}

enum class TaskPriority(val label: String, val icon: String) {
    LOW("Low", "\u2193"),           // Down arrow
    MEDIUM("Medium", "\u2501"),     // Horizontal line
    HIGH("High", "\u2191");         // Up arrow

    companion object {
        fun fromString(value: String): TaskPriority =
            entries.firstOrNull { it.name == value } ?: MEDIUM
    }
}
