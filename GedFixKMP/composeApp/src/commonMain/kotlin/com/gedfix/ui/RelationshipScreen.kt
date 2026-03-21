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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gedfix.models.GedcomPerson
import com.gedfix.models.Relationship
import com.gedfix.models.RelationshipCalculator
import com.gedfix.ui.theme.*
import com.gedfix.viewmodel.AppViewModel

/**
 * Relationship calculator screen with two person pickers and
 * a visual path showing how two people are related.
 */
@Composable
fun RelationshipScreen(viewModel: AppViewModel) {
    val db = viewModel.db

    var personAXref by remember { mutableStateOf(viewModel.selectedPersonXref ?: "") }
    var personBXref by remember { mutableStateOf("") }
    var result by remember { mutableStateOf<Relationship?>(null) }
    var notFound by remember { mutableStateOf(false) }
    var isCalculating by remember { mutableStateOf(false) }

    // Person picker state
    var showPickerA by remember { mutableStateOf(false) }
    var showPickerB by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    val personA = if (personAXref.isNotEmpty()) db.fetchPerson(personAXref) else null
    val personB = if (personBXref.isNotEmpty()) db.fetchPerson(personBXref) else null

    // Update personA when global selection changes
    LaunchedEffect(viewModel.selectedPersonXref) {
        viewModel.selectedPersonXref?.let {
            if (it != personAXref) {
                personAXref = it
                result = null
                notFound = false
            }
        }
    }

    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // Header
        Text("Relationship Calculator", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Text(
            "Select two people to discover how they are related in your family tree.",
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // Person selection cards
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Person A card
            PersonSelectionCard(
                label = "Person A",
                person = personA,
                onSelectClick = { showPickerA = true },
                modifier = Modifier.weight(1f)
            )

            // Connector between the two
            Box(
                modifier = Modifier
                    .padding(top = 40.dp)
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "\u2194",
                    fontSize = 20.sp,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    fontWeight = FontWeight.Bold
                )
            }

            // Person B card
            PersonSelectionCard(
                label = "Person B",
                person = personB,
                onSelectClick = { showPickerB = true },
                modifier = Modifier.weight(1f)
            )
        }

        // Calculate button
        Button(
            onClick = {
                if (personAXref.isNotEmpty() && personBXref.isNotEmpty()) {
                    isCalculating = true
                    val calcResult = RelationshipCalculator.calculate(personAXref, personBXref, db)
                    result = calcResult
                    notFound = calcResult == null
                    isCalculating = false
                }
            },
            enabled = personAXref.isNotEmpty() && personBXref.isNotEmpty() && !isCalculating,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        ) {
            if (isCalculating) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text("Calculate Relationship")
        }

        // Result display
        if (notFound) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.errorContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "No Relationship Found",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Text(
                        "These two people do not appear to be related within the data available in this tree (searched up to 15 generations).",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        result?.let { rel ->
            // Relationship name in large text
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        rel.description,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        "${rel.generationsUp} generation${if (rel.generationsUp != 1) "s" else ""} up, " +
                                "${rel.generationsDown} generation${if (rel.generationsDown != 1) "s" else ""} down",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    )
                }
            }

            // Common ancestor
            rel.commonAncestorXref?.let { ancestorXref ->
                val ancestor = db.fetchPerson(ancestorXref)
                if (ancestor != null && ancestorXref != personAXref && ancestorXref != personBXref) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                "Common Ancestor",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                val ancColor = when (ancestor.sex) {
                                    "M" -> MaleColor
                                    "F" -> FemaleColor
                                    else -> UnknownGenderColor
                                }
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(ancColor.copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        ancestor.initials,
                                        fontWeight = FontWeight.Bold,
                                        color = ancColor
                                    )
                                }
                                Column {
                                    Text(
                                        ancestor.displayName,
                                        fontWeight = FontWeight.Medium,
                                        color = ancColor
                                    )
                                    Text(
                                        ancestor.xref,
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Path visualization
            if (rel.path.size > 2) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            "Relationship Path",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        for ((index, xref) in rel.path.withIndex()) {
                            val pathPerson = db.fetchPerson(xref)
                            val isEndpoint = index == 0 || index == rel.path.size - 1
                            val isCommonAncestor = xref == rel.commonAncestorXref

                            val personColor = when (pathPerson?.sex) {
                                "M" -> MaleColor
                                "F" -> FemaleColor
                                else -> UnknownGenderColor
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.padding(vertical = 4.dp)
                            ) {
                                // Step indicator
                                Box(
                                    modifier = Modifier
                                        .size(if (isEndpoint || isCommonAncestor) 32.dp else 24.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (isCommonAncestor) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                                            else if (isEndpoint) personColor.copy(alpha = 0.2f)
                                            else MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = if (isCommonAncestor) "\u2605" else "${index + 1}",
                                        fontSize = if (isEndpoint || isCommonAncestor) 14.sp else 11.sp,
                                        fontWeight = if (isEndpoint || isCommonAncestor) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isCommonAncestor) MaterialTheme.colorScheme.primary
                                        else if (isEndpoint) personColor
                                        else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                Column {
                                    Text(
                                        pathPerson?.displayName ?: xref,
                                        fontWeight = if (isEndpoint || isCommonAncestor) FontWeight.SemiBold else FontWeight.Normal,
                                        fontSize = if (isEndpoint || isCommonAncestor) 14.sp else 13.sp,
                                        color = personColor
                                    )
                                    if (isCommonAncestor) {
                                        Text(
                                            "Common Ancestor",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }

                            // Arrow between steps
                            if (index < rel.path.size - 1) {
                                val direction = if (index < rel.generationsUp) "\u2191" else "\u2193"
                                Text(
                                    text = "  $direction",
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                    modifier = Modifier.padding(start = 12.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Person picker dialogs
    if (showPickerA) {
        PersonPickerDialog(
            title = "Select Person A",
            currentSearch = searchQuery,
            onSearchChange = { searchQuery = it },
            db = db,
            onSelect = { person ->
                personAXref = person.xref
                showPickerA = false
                searchQuery = ""
                result = null
                notFound = false
            },
            onDismiss = { showPickerA = false; searchQuery = "" }
        )
    }

    if (showPickerB) {
        PersonPickerDialog(
            title = "Select Person B",
            currentSearch = searchQuery,
            onSearchChange = { searchQuery = it },
            db = db,
            onSelect = { person ->
                personBXref = person.xref
                showPickerB = false
                searchQuery = ""
                result = null
                notFound = false
            },
            onDismiss = { showPickerB = false; searchQuery = "" }
        )
    }
}

@Composable
private fun PersonSelectionCard(
    label: String,
    person: GedcomPerson?,
    onSelectClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                label,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (person != null) {
                val sexColor = when (person.sex) {
                    "M" -> MaleColor
                    "F" -> FemaleColor
                    else -> UnknownGenderColor
                }
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(sexColor.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        person.initials,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = sexColor
                    )
                }
                Text(
                    person.displayName,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    color = when (person.sex) {
                        "M" -> MaleColor
                        "F" -> FemaleColor
                        else -> MaterialTheme.colorScheme.onSurface
                    }
                )
                Text(
                    person.xref,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("?", fontSize = 24.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(
                    "Not selected",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
            }

            OutlinedButton(onClick = onSelectClick) {
                Text(if (person != null) "Change" else "Select")
            }
        }
    }
}

@Composable
private fun PersonPickerDialog(
    title: String,
    currentSearch: String,
    onSearchChange: (String) -> Unit,
    db: com.gedfix.db.DatabaseRepository,
    onSelect: (GedcomPerson) -> Unit,
    onDismiss: () -> Unit
) {
    val searchResults = remember(currentSearch) {
        if (currentSearch.length >= 2) db.fetchPersons(currentSearch) else emptyList()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = currentSearch,
                    onValueChange = onSearchChange,
                    label = { Text("Search by name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                if (searchResults.isNotEmpty()) {
                    Column(
                        modifier = Modifier.heightIn(max = 300.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        for (person in searchResults.take(20)) {
                            TextButton(
                                onClick = { onSelect(person) },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    "${person.displayName} (${person.xref})",
                                    color = when (person.sex) {
                                        "M" -> MaleColor
                                        "F" -> FemaleColor
                                        else -> MaterialTheme.colorScheme.onSurface
                                    }
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
