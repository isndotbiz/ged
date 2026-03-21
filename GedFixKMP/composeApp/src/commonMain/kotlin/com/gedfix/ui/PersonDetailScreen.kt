package com.gedfix.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gedfix.models.GedcomPerson
import com.gedfix.models.ResearchSuggestionEngine
import com.gedfix.ui.theme.*
import com.gedfix.viewmodel.PersonViewModel
import java.awt.Desktop
import java.net.URI

/**
 * Person detail view with events, families, parents, edit/delete buttons.
 */
@Composable
fun PersonDetailScreen(
    person: GedcomPerson,
    personViewModel: PersonViewModel,
    modifier: Modifier = Modifier
) {
    val events = personViewModel.fetchEvents(person.xref)
    val spouseFamilies = personViewModel.fetchFamiliesAsSpouse(person.xref)
    val parentFamilies = personViewModel.fetchFamiliesAsChild(person.xref)
    val scrollState = rememberScrollState()

    val sexColor = when (person.sex) {
        "M" -> MaleColor
        "F" -> FemaleColor
        else -> UnknownGenderColor
    }
    val sexBgColor = when (person.sex) {
        "M" -> MaleBgColor
        "F" -> FemaleBgColor
        else -> UnknownGenderBgColor
    }

    Column(
        modifier = modifier
            .verticalScroll(scrollState)
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // Header with toolbar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            // Person info
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(sexBgColor),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = person.initials.ifEmpty { "?" },
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = sexColor
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = person.displayName.ifEmpty { "(Unknown)" },
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold
                        )
                        if (person.isLiving) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = LivingBadgeBg
                            ) {
                                Text(
                                    text = "Living",
                                    fontSize = 12.sp,
                                    color = LivingBadgeColor,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }
                        // Validation badge
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (person.isValidated) ValidatedBgColor else UnvalidatedBgColor
                        ) {
                            Text(
                                text = if (person.isValidated) "\u2713 Validated" else "\u26A0 Needs Source",
                                fontSize = 12.sp,
                                color = if (person.isValidated) ValidatedColor else UnvalidatedColor,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }

                    // Source and media counts
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text(
                            "${person.sourceCount} source${if (person.sourceCount != 1) "s" else ""}",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            "${person.mediaCount} media",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        val birth = events.firstOrNull { it.eventType == "BIRT" }
                        val death = events.firstOrNull { it.eventType == "DEAT" }
                        if (birth != null) {
                            Text(
                                "b. ${birth.dateValue}${if (birth.place.isNotEmpty()) ", ${birth.place}" else ""}",
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (death != null) {
                            Text(
                                "d. ${death.dateValue}${if (death.place.isNotEmpty()) ", ${death.place}" else ""}",
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Text(
                        text = person.xref,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }
            }

            // Edit/Delete buttons
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { personViewModel.showEditPersonDialog = true }) {
                    Text("Edit")
                }
                OutlinedButton(
                    onClick = { personViewModel.showDeleteConfirm = true },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = CriticalColor)
                ) {
                    Text("Delete")
                }
            }
        }

        HorizontalDivider()

        // Validation callout for unvalidated persons
        if (!person.isValidated) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = UnvalidatedBgColor
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "\u26A0 No Source Citations",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = UnvalidatedColor
                    )
                    Text(
                        "This person has no source citations. Add a source to validate this record and improve your tree's research quality.",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            // Research suggestions
            val suggestions = ResearchSuggestionEngine.suggestionsFor(person, events)
            if (suggestions.isNotEmpty()) {
                var expandedResearch by remember { mutableStateOf(false) }
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Research Suggestions",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            TextButton(onClick = { expandedResearch = !expandedResearch }) {
                                Text(if (expandedResearch) "Collapse" else "Expand (${suggestions.size})")
                            }
                        }

                        if (expandedResearch) {
                            for ((index, suggestion) in suggestions.withIndex()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Text(
                                        text = suggestion.icon,
                                        fontSize = 16.sp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Text(suggestion.title, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                                        Text(
                                            suggestion.description,
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            suggestion.source,
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                        )
                                    }
                                    OutlinedButton(
                                        onClick = {
                                            try {
                                                Desktop.getDesktop().browse(URI(suggestion.url))
                                            } catch (_: Exception) { }
                                        }
                                    ) {
                                        Text("Search", fontSize = 12.sp)
                                    }
                                }
                                if (index < suggestions.size - 1) {
                                    HorizontalDivider()
                                }
                            }
                        }
                    }
                }
            }
        }

        // Events section
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Events", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    TextButton(onClick = { personViewModel.showAddEventDialog = true }) {
                        Text("+ Add Event")
                    }
                }

                if (events.isEmpty()) {
                    Text(
                        "No events recorded.",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                for ((index, event) in events.withIndex()) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            text = eventTypeIcon(event.eventType),
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.primary
                        )

                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(event.displayType, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                            if (event.dateValue.isNotEmpty()) {
                                Text(event.dateValue, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (event.place.isNotEmpty()) {
                                Text("\u2316 ${event.place}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }

                        TextButton(onClick = { personViewModel.editingEvent = event }) {
                            Text("\u270E", fontSize = 14.sp)
                        }
                    }

                    if (index < events.size - 1) {
                        HorizontalDivider(modifier = Modifier.padding(start = 36.dp))
                    }
                }
            }
        }

        // Spouse families
        if (spouseFamilies.isNotEmpty()) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("\u2665 Families", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)

                    for (family in spouseFamilies) {
                        val spouseXref = if (family.partner1Xref == person.xref) family.partner2Xref else family.partner1Xref
                        val spouse = personViewModel.fetchPerson(spouseXref)
                        val marriageEvents = personViewModel.fetchEvents(family.xref).filter { it.eventType == "MARR" }
                        val childLinks = personViewModel.fetchChildLinks(family.xref)

                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            if (spouse != null) {
                                Text("Spouse: ${spouse.displayName}", fontWeight = FontWeight.Medium)
                            }
                            for (marr in marriageEvents) {
                                Text(
                                    "Married ${marr.dateValue}${if (marr.place.isNotEmpty()) " in ${marr.place}" else ""}",
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (childLinks.isNotEmpty()) {
                                Text("Children:", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                for (link in childLinks) {
                                    val child = personViewModel.fetchPerson(link.childXref)
                                    if (child != null) {
                                        Text(
                                            "  \u2192 ${child.displayName}",
                                            fontSize = 13.sp,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Parents
        if (parentFamilies.isNotEmpty()) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Parents", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    for (family in parentFamilies) {
                        val father = personViewModel.fetchPerson(family.partner1Xref)
                        val mother = personViewModel.fetchPerson(family.partner2Xref)
                        if (father != null) {
                            Text("Father: ${father.displayName}", color = MaleColor)
                        }
                        if (mother != null) {
                            Text("Mother: ${mother.displayName}", color = FemaleColor)
                        }
                    }
                }
            }
        }
    }

    // Edit person dialog
    if (personViewModel.showEditPersonDialog) {
        PersonEditorDialog(
            person = person,
            onSave = { givenName, surname, suffix, sex, isLiving ->
                val updated = person.copy(
                    givenName = givenName.trim(),
                    surname = surname.trim(),
                    suffix = suffix.trim(),
                    sex = sex,
                    isLiving = isLiving
                )
                personViewModel.updatePerson(updated)
            },
            onDismiss = { personViewModel.showEditPersonDialog = false }
        )
    }

    // Delete confirmation
    if (personViewModel.showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { personViewModel.showDeleteConfirm = false },
            title = { Text("Delete Person?") },
            text = { Text("This will permanently delete ${person.displayName} and remove them from all families. Their events will also be deleted.") },
            confirmButton = {
                TextButton(
                    onClick = { personViewModel.deletePerson(person.xref) },
                    colors = ButtonDefaults.textButtonColors(contentColor = CriticalColor)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { personViewModel.showDeleteConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Add event dialog
    if (personViewModel.showAddEventDialog) {
        EventEditorDialog(
            event = null,
            ownerXref = person.xref,
            ownerType = "INDI",
            onSave = { eventType, dateValue, place, description ->
                personViewModel.createEvent(person.xref, "INDI", eventType, dateValue, place, description)
            },
            onDismiss = { personViewModel.showAddEventDialog = false },
            onDelete = null
        )
    }

    // Edit event dialog
    personViewModel.editingEvent?.let { event ->
        EventEditorDialog(
            event = event,
            ownerXref = person.xref,
            ownerType = "INDI",
            onSave = { eventType, dateValue, place, description ->
                val updated = event.copy(
                    eventType = eventType,
                    dateValue = dateValue.trim(),
                    place = place.trim(),
                    description = description.trim()
                )
                personViewModel.updateEvent(updated)
            },
            onDismiss = { personViewModel.editingEvent = null },
            onDelete = { personViewModel.deleteEvent(event.id) }
        )
    }
}

private fun eventTypeIcon(type: String): String = when (type) {
    "BIRT" -> "\u2740"  // Flower (birth)
    "DEAT" -> "\u2620"  // Skull (death)
    "MARR" -> "\u2665"  // Heart
    "BURI" -> "\u271D"  // Cross
    "CHR", "BAPM" -> "\u2741" // Droplet-like
    "RESI", "CENS" -> "\u2302" // House
    "IMMI", "EMIG" -> "\u2708" // Airplane
    "NATU" -> "\u2691"  // Flag
    "DIV" -> "\u2194"   // Arrows
    else -> "\u2606"    // Star
}
