package com.gedfix.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.gedfix.viewmodel.AppViewModel
import com.gedfix.viewmodel.PersonViewModel
import java.awt.Desktop
import java.net.URI

/**
 * Full-screen validation view showing all persons with their validation status.
 * Unvalidated persons are shown first, sorted by likelihood of finding sources.
 */
@Composable
fun ValidationScreen(
    appViewModel: AppViewModel,
    personViewModel: PersonViewModel
) {
    val totalPersons = appViewModel.personCount
    val validatedCount = appViewModel.validatedCount
    val unvalidatedCount = appViewModel.unvalidatedCount
    val percentage = appViewModel.validationPercentage

    var showOnlyUnvalidated by remember { mutableStateOf(true) }
    var selectedPersonXref by remember { mutableStateOf<String?>(null) }

    val allPersons = personViewModel.persons
    val displayPersons = if (showOnlyUnvalidated) {
        allPersons.filter { !it.isValidated }
    } else {
        allPersons.sortedBy { if (it.isValidated) 1 else 0 }
    }

    Row(modifier = Modifier.fillMaxSize()) {
        // List panel
        Column(
            modifier = Modifier
                .width(420.dp)
                .fillMaxHeight()
        ) {
            // Summary header
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Validation",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )

                // Summary text
                Text(
                    text = "$unvalidatedCount ${if (unvalidatedCount == 1) "person needs" else "people need"} validation \u2014 " +
                        "${String.format("%.0f", percentage)}% of your tree is validated",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Progress bar
                LinearProgressIndicator(
                    progress = { if (totalPersons > 0) validatedCount.toFloat() / totalPersons.toFloat() else 0f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = ValidatedColor,
                    trackColor = UnvalidatedBgColor,
                )

                // Counts row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "\u2713 $validatedCount validated",
                        fontSize = 12.sp,
                        color = ValidatedColor,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        "\u26A0 $unvalidatedCount unvalidated",
                        fontSize = 12.sp,
                        color = UnvalidatedColor,
                        fontWeight = FontWeight.Medium
                    )
                }

                // Filter toggle
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    FilterChip(
                        selected = showOnlyUnvalidated,
                        onClick = { showOnlyUnvalidated = true },
                        label = { Text("Unvalidated Only", fontSize = 12.sp) }
                    )
                    FilterChip(
                        selected = !showOnlyUnvalidated,
                        onClick = { showOnlyUnvalidated = false },
                        label = { Text("Show All", fontSize = 12.sp) }
                    )
                }
            }

            HorizontalDivider()

            // Person list
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(4.dp)
            ) {
                items(displayPersons, key = { it.id }) { person ->
                    val isSelected = selectedPersonXref == person.xref
                    val birthDate = personViewModel.fetchEvents(person.xref)
                        .firstOrNull { it.eventType == "BIRT" }?.dateValue ?: ""
                    val deathDate = personViewModel.fetchEvents(person.xref)
                        .firstOrNull { it.eventType == "DEAT" }?.dateValue ?: ""

                    ValidationPersonRow(
                        person = person,
                        isSelected = isSelected,
                        birthDate = birthDate,
                        deathDate = deathDate,
                        onClick = { selectedPersonXref = person.xref }
                    )
                }
            }
        }

        VerticalDivider()

        // Detail panel
        val selectedPerson = selectedPersonXref?.let { xref ->
            displayPersons.firstOrNull { it.xref == xref } ?: personViewModel.fetchPerson(xref)
        }
        if (selectedPerson != null) {
            ValidationDetailPanel(
                person = selectedPerson,
                personViewModel = personViewModel,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Select a Person",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Choose someone to see research suggestions.",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun ValidationPersonRow(
    person: GedcomPerson,
    isSelected: Boolean,
    birthDate: String,
    deathDate: String,
    onClick: () -> Unit
) {
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

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(6.dp),
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Avatar
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(sexBgColor),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = person.initials.ifEmpty { "?" },
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = sexColor
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = person.displayName.ifEmpty { "(Unknown)" },
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.sp
                    )
                    // Validation indicator
                    Text(
                        text = if (person.isValidated) "\u2713" else "\u26A0",
                        fontSize = 12.sp,
                        color = if (person.isValidated) ValidatedColor else UnvalidatedColor
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (birthDate.isNotEmpty()) {
                        Text("b. $birthDate", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (deathDate.isNotEmpty()) {
                        Text("d. $deathDate", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (birthDate.isEmpty() && deathDate.isEmpty()) {
                        Text("No dates", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                    }
                }
            }
        }
    }
}

@Composable
private fun ValidationDetailPanel(
    person: GedcomPerson,
    personViewModel: PersonViewModel,
    modifier: Modifier = Modifier
) {
    val events = personViewModel.fetchEvents(person.xref)
    val suggestions = ResearchSuggestionEngine.suggestionsFor(person, events)
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
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // Person header
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(sexBgColor),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = person.initials.ifEmpty { "?" },
                    fontSize = 18.sp,
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
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
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

                val birth = events.firstOrNull { it.eventType == "BIRT" }
                val death = events.firstOrNull { it.eventType == "DEAT" }
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    if (birth != null) {
                        Text(
                            "b. ${birth.dateValue}${if (birth.place.isNotEmpty()) ", ${birth.place}" else ""}",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (death != null) {
                        Text(
                            "d. ${death.dateValue}${if (death.place.isNotEmpty()) ", ${death.place}" else ""}",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Text(
                    "${person.sourceCount} source${if (person.sourceCount != 1) "s" else ""}, ${person.mediaCount} media",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        HorizontalDivider()

        // Research suggestions
        if (suggestions.isNotEmpty()) {
            Text(
                "Research Suggestions",
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                "Based on the data we have for this person, here are the best places to search for sources:",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            for ((index, suggestion) in suggestions.withIndex()) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            text = suggestion.icon,
                            fontSize = 24.sp
                        )
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(suggestion.title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Text(
                                suggestion.description,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                suggestion.source,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            )
                        }
                        Button(
                            onClick = {
                                try {
                                    Desktop.getDesktop().browse(URI(suggestion.url))
                                } catch (_: Exception) { }
                            }
                        ) {
                            Text("Search")
                        }
                    }
                }
            }
        } else {
            Text(
                "No research suggestions available. This person needs more identifying information (name, dates, or places) to generate useful search links.",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
