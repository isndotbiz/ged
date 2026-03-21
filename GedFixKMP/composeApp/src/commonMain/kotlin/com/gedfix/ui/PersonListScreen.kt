package com.gedfix.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gedfix.models.GedcomPerson
import com.gedfix.models.PersonSort
import com.gedfix.ui.components.PersonAvatar
import com.gedfix.ui.theme.*
import com.gedfix.viewmodel.AIViewModel
import com.gedfix.viewmodel.AppViewModel
import com.gedfix.viewmodel.PersonViewModel

/**
 * Searchable/sortable person list with split view.
 * Apple-polished: PersonAvatar, indented dividers, clean search.
 */
@Composable
fun PersonListScreen(
    appViewModel: AppViewModel,
    personViewModel: PersonViewModel,
    aiViewModel: AIViewModel? = null
) {
    val persons = personViewModel.persons

    Row(modifier = Modifier.fillMaxSize()) {
        // List panel
        Column(
            modifier = Modifier
                .width(340.dp)
                .fillMaxHeight()
        ) {
            // Search bar (not floating, at top)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.md, vertical = Spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = personViewModel.searchText,
                    onValueChange = { personViewModel.searchText = it },
                    placeholder = { Text("Search people...", style = MaterialTheme.typography.bodyMedium) },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp)
                )
                IconButton(onClick = { personViewModel.showNewPersonDialog = true }) {
                    Text("+", fontSize = 20.sp, fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary)
                }
            }

            // Sort toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.md, vertical = Spacing.xs),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                FilterChip(
                    selected = personViewModel.sortBy == PersonSort.SURNAME,
                    onClick = { personViewModel.sortBy = PersonSort.SURNAME },
                    label = { Text("Surname", style = MaterialTheme.typography.labelMedium) },
                    shape = RoundedCornerShape(8.dp)
                )
                FilterChip(
                    selected = personViewModel.sortBy == PersonSort.GIVEN_NAME,
                    onClick = { personViewModel.sortBy = PersonSort.GIVEN_NAME },
                    label = { Text("Given Name", style = MaterialTheme.typography.labelMedium) },
                    shape = RoundedCornerShape(8.dp)
                )
            }

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f),
                thickness = 0.5.dp
            )

            // Person list
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(Spacing.xs)
            ) {
                items(persons, key = { it.id }) { person ->
                    val isSelected = personViewModel.selectedPersonId == person.id
                    PersonRow(
                        person = person,
                        isSelected = isSelected,
                        birthDate = personViewModel.fetchEvents(person.xref)
                            .firstOrNull { it.eventType == "BIRT" }?.dateValue ?: "",
                        deathDate = personViewModel.fetchEvents(person.xref)
                            .firstOrNull { it.eventType == "DEAT" }?.dateValue ?: "",
                        onClick = { personViewModel.selectedPersonId = person.id }
                    )
                }
            }
        }

        // Subtle divider
        VerticalDivider(
            thickness = 0.5.dp,
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
        )

        // Detail panel
        val selectedPerson = personViewModel.selectedPerson
        if (selectedPerson != null) {
            PersonDetailScreen(
                person = selectedPerson,
                personViewModel = personViewModel,
                aiViewModel = aiViewModel,
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
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Choose someone from the list to see their details.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    // New person dialog
    if (personViewModel.showNewPersonDialog) {
        PersonEditorDialog(
            person = null,
            onSave = { givenName, surname, suffix, sex, isLiving ->
                personViewModel.createPerson(givenName, surname, suffix, sex, isLiving)
                appViewModel.refreshCounts()
            },
            onDismiss = { personViewModel.showNewPersonDialog = false }
        )
    }
}

/**
 * Person row: PersonAvatar + name + dates.
 * Indented dividers (Apple list style). Subtle blue highlight when selected.
 */
@Composable
private fun PersonRow(
    person: GedcomPerson,
    isSelected: Boolean,
    birthDate: String,
    deathDate: String,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(8.dp),
        color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
        else MaterialTheme.colorScheme.surface.copy(alpha = 0f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            PersonAvatar(person = person, size = 36.dp, showBadge = true)

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = person.displayName.ifEmpty { "(Unknown)" },
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium
                    )
                    if (person.isLiving) {
                        Text(
                            text = "\u25CF",
                            fontSize = 7.sp,
                            color = LivingBadgeColor
                        )
                    }
                }
                if (birthDate.isNotEmpty() || deathDate.isNotEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (birthDate.isNotEmpty()) {
                            Text(
                                "b. $birthDate",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (deathDate.isNotEmpty()) {
                            Text(
                                "d. $deathDate",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}
