package com.gedfix.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gedfix.models.GedcomPerson
import com.gedfix.models.PersonSort
import com.gedfix.ui.theme.*
import com.gedfix.viewmodel.AppViewModel
import com.gedfix.viewmodel.PersonViewModel

/**
 * Searchable/sortable person list with avatar initials.
 * Split view: list on left, detail on right.
 */
@Composable
fun PersonListScreen(
    appViewModel: AppViewModel,
    personViewModel: PersonViewModel
) {
    val persons = personViewModel.persons

    Row(modifier = Modifier.fillMaxSize()) {
        // List panel
        Column(
            modifier = Modifier
                .width(340.dp)
                .fillMaxHeight()
        ) {
            // Search + toolbar
            Row(
                modifier = Modifier.fillMaxWidth().padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = personViewModel.searchText,
                    onValueChange = { personViewModel.searchText = it },
                    label = { Text("Search people") },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
                IconButton(onClick = { personViewModel.showNewPersonDialog = true }) {
                    Text("+", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                }
            }

            // Sort toggle
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                FilterChip(
                    selected = personViewModel.sortBy == PersonSort.SURNAME,
                    onClick = { personViewModel.sortBy = PersonSort.SURNAME },
                    label = { Text("Surname", fontSize = 12.sp) }
                )
                FilterChip(
                    selected = personViewModel.sortBy == PersonSort.GIVEN_NAME,
                    onClick = { personViewModel.sortBy = PersonSort.GIVEN_NAME },
                    label = { Text("Given Name", fontSize = 12.sp) }
                )
            }

            HorizontalDivider()

            // Person list
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(4.dp)
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

        VerticalDivider()

        // Detail panel
        val selectedPerson = personViewModel.selectedPerson
        if (selectedPerson != null) {
            PersonDetailScreen(
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
                        text = "Choose someone from the list to see their details.",
                        fontSize = 14.sp,
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

@Composable
private fun PersonRow(
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
                    if (person.isLiving) {
                        Text(
                            text = "\u25CF",
                            fontSize = 8.sp,
                            color = LivingBadgeColor
                        )
                    }
                    Text(
                        text = if (person.isValidated) "\u2713" else "\u26A0",
                        fontSize = 10.sp,
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
                }
            }
        }
    }
}
