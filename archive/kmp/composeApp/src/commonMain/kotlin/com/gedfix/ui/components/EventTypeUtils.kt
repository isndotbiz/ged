package com.gedfix.ui.components

import androidx.compose.ui.graphics.Color
import com.gedfix.ui.theme.*

/**
 * Shared utility functions for event type display across all screens.
 */
fun eventTypeIcon(eventType: String): String = when (eventType) {
    "BIRT" -> "\u2605"    // Star (birth)
    "DEAT" -> "\u2020"    // Dagger (death)
    "MARR" -> "\u2665"    // Heart (marriage)
    "BURI" -> "\u271D"    // Cross (burial)
    "CHR", "BAPM" -> "\u2022" // Dot (christening)
    "RESI", "CENS" -> "\u2302" // House (residence)
    "IMMI", "EMIG" -> "\u2192" // Arrow (migration)
    "DIV" -> "\u2194"     // Left-right arrow (divorce)
    else -> "\u2606"      // Empty star (other)
}

fun eventTypeColor(eventType: String): Color = when (eventType) {
    "BIRT" -> BirthEventColor
    "DEAT" -> DeathEventColor
    "MARR" -> MarriageEventColor
    "BURI" -> BurialEventColor
    "CHR", "BAPM" -> ReligiousEventColor
    "RESI", "CENS" -> ResidenceEventColor
    "IMMI", "EMIG" -> MigrationEventColor
    else -> DefaultEventColor
}
