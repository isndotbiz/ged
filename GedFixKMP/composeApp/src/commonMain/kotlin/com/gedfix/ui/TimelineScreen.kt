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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gedfix.models.*
import com.gedfix.ui.components.eventTypeColor
import com.gedfix.ui.components.eventTypeIcon
import com.gedfix.ui.theme.*
import com.gedfix.ui.theme.Spacing
import com.gedfix.viewmodel.AppViewModel

/**
 * Timeline view showing all events in chronological order.
 * Supports filtering by person, event type, and year range.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimelineScreen(appViewModel: AppViewModel) {
    var allEntries by remember { mutableStateOf(listOf<TimelineEntry>()) }
    var persons by remember { mutableStateOf(listOf<GedcomPerson>()) }
    var selectedPersonXref by remember { mutableStateOf<String?>(null) }
    var selectedEventType by remember { mutableStateOf<String?>(null) }
    var viewMode by remember { mutableStateOf(TimelineViewMode.YEAR) }
    var personDropdownExpanded by remember { mutableStateOf(false) }
    var eventTypeDropdownExpanded by remember { mutableStateOf(false) }

    // Load data
    LaunchedEffect(Unit) {
        allEntries = appViewModel.db.fetchAllEventsWithPersons()
        persons = appViewModel.db.fetchAllPersons()
    }

    // Reload when person filter changes
    LaunchedEffect(selectedPersonXref) {
        allEntries = if (selectedPersonXref != null) {
            appViewModel.db.fetchEventsForPersonTimeline(selectedPersonXref!!)
        } else {
            appViewModel.db.fetchAllEventsWithPersons()
        }
    }

    // Filter and sort entries
    val filteredEntries = remember(allEntries, selectedEventType) {
        val filtered = if (selectedEventType != null) {
            allEntries.filter { it.event.eventType == selectedEventType }
        } else {
            allEntries
        }
        // Sort by year, entries without year go to the end
        filtered.sortedBy { it.year ?: Int.MAX_VALUE }
    }

    // Group entries based on view mode
    val groupedEntries = remember(filteredEntries, viewMode) {
        when (viewMode) {
            TimelineViewMode.DECADE -> filteredEntries.groupBy { entry ->
                entry.decade?.let { "${it}s" } ?: "Unknown"
            }
            TimelineViewMode.YEAR -> filteredEntries.groupBy { entry ->
                entry.year?.toString() ?: "Unknown"
            }
            TimelineViewMode.CENTURY -> filteredEntries.groupBy { entry ->
                entry.year?.let { "${(it / 100) * 100}s" } ?: "Unknown"
            }
        }
    }

    // Year range for header
    val yearRange = remember(filteredEntries) {
        val years = filteredEntries.mapNotNull { it.year }
        if (years.isNotEmpty()) "${years.min()} to ${years.max()}" else ""
    }

    // Available event types for filter
    val eventTypes = remember(allEntries) {
        allEntries.map { it.event.eventType }.distinct().sorted()
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Timeline",
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onBackground
                )
                if (filteredEntries.isNotEmpty()) {
                    Text(
                        text = "${filteredEntries.size} events${if (yearRange.isNotEmpty()) " from $yearRange" else ""}",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Filters row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Person filter
            Box {
                OutlinedButton(
                    onClick = { personDropdownExpanded = true },
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = selectedPersonXref?.let { xref ->
                            persons.firstOrNull { it.xref == xref }?.displayName ?: "Person"
                        } ?: "All People",
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = 160.dp)
                    )
                    Text(" \u25BE", fontSize = 12.sp)
                }
                DropdownMenu(
                    expanded = personDropdownExpanded,
                    onDismissRequest = { personDropdownExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("All People") },
                        onClick = {
                            selectedPersonXref = null
                            personDropdownExpanded = false
                        }
                    )
                    HorizontalDivider()
                    for (person in persons.take(50)) {
                        DropdownMenuItem(
                            text = { Text(person.displayName, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            onClick = {
                                selectedPersonXref = person.xref
                                personDropdownExpanded = false
                            }
                        )
                    }
                }
            }

            // Event type filter
            Box {
                OutlinedButton(
                    onClick = { eventTypeDropdownExpanded = true },
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = selectedEventType?.let { GedcomEvent(
                            id = "", ownerXref = "", ownerType = "", eventType = it,
                            dateValue = "", place = "", description = ""
                        ).displayType } ?: "All Events",
                        fontSize = 13.sp
                    )
                    Text(" \u25BE", fontSize = 12.sp)
                }
                DropdownMenu(
                    expanded = eventTypeDropdownExpanded,
                    onDismissRequest = { eventTypeDropdownExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("All Events") },
                        onClick = {
                            selectedEventType = null
                            eventTypeDropdownExpanded = false
                        }
                    )
                    HorizontalDivider()
                    for (type in eventTypes) {
                        val displayType = GedcomEvent(
                            id = "", ownerXref = "", ownerType = "", eventType = type,
                            dateValue = "", place = "", description = ""
                        ).displayType
                        DropdownMenuItem(
                            text = {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(eventTypeIcon(type), color = eventTypeColor(type))
                                    Text(displayType)
                                }
                            },
                            onClick = {
                                selectedEventType = type
                                eventTypeDropdownExpanded = false
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // View mode toggle
            SingleChoiceSegmentedButtonRow {
                TimelineViewMode.entries.forEachIndexed { index, mode ->
                    SegmentedButton(
                        selected = viewMode == mode,
                        onClick = { viewMode = mode },
                        shape = SegmentedButtonDefaults.itemShape(
                            index = index,
                            count = TimelineViewMode.entries.size
                        )
                    ) {
                        Text(mode.label, fontSize = 12.sp)
                    }
                }
            }
        }

        // Clear filters hint
        if (selectedPersonXref != null || selectedEventType != null) {
            Surface(
                modifier = Modifier.clickable {
                    selectedPersonXref = null
                    selectedEventType = null
                },
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
            ) {
                Text(
                    text = "\u2715 Clear filters",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }

        if (filteredEntries.isEmpty()) {
            // Empty state
            Spacer(modifier = Modifier.height(40.dp))
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "\u231A",
                    fontSize = 48.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                )
                Text(
                    text = if (appViewModel.eventCount == 0) "No Events Yet"
                    else "No events match your filters",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (appViewModel.eventCount == 0) {
                    Text(
                        text = "Import a GEDCOM file to see the timeline",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }
        } else {
            // Timeline content
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                groupedEntries.forEach { (groupLabel, entries) ->
                    // Sticky year/decade header
                    item(key = "header_$groupLabel") {
                        TimelineGroupHeader(label = groupLabel, count = entries.size)
                    }

                    items(entries, key = { it.event.id }) { entry ->
                        TimelineEventRow(
                            entry = entry,
                            onPersonClick = {
                                appViewModel.selectedPersonXref = entry.personXref
                                appViewModel.selectedSection = SidebarSection.PEOPLE
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TimelineGroupHeader(label: String, count: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Year label
        Text(
            text = label,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.width(80.dp)
        )

        // Horizontal line
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.outlineVariant
        )

        // Count badge
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
        ) {
            Text(
                text = "$count",
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
            )
        }
    }
}

@Composable
private fun TimelineEventRow(
    entry: TimelineEntry,
    onPersonClick: () -> Unit
) {
    val color = eventTypeColor(entry.event.eventType)

    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        // Timeline rail: thin 2dp line + 10dp color-coded dot
        Column(
            modifier = Modifier.width(36.dp).padding(start = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .width(2.dp)
                    .height(8.dp)
                    .background(ConnectorColor)
            )
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(color)
            )
            Box(
                modifier = Modifier
                    .width(2.dp)
                    .height(8.dp)
                    .background(ConnectorColor)
            )
        }

        // Event card
        Surface(
            modifier = Modifier
                .weight(1f)
                .clickable(onClick = onPersonClick),
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Event type icon
                Surface(
                    shape = CircleShape,
                    color = color.copy(alpha = 0.15f),
                    modifier = Modifier.size(36.dp)
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        Text(
                            text = eventTypeIcon(entry.event.eventType),
                            fontSize = 16.sp,
                            color = color
                        )
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
                    // Person name
                    Text(
                        text = entry.personName.ifEmpty { entry.personXref },
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    // Event type + date
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = entry.event.displayType,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = color
                        )
                        if (entry.event.displayDate.isNotEmpty()) {
                            Text(
                                text = "\u2022",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = entry.event.displayDate,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Place
                    if (entry.event.place.isNotEmpty()) {
                        Text(
                            text = entry.event.place,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

private enum class TimelineViewMode(val label: String) {
    DECADE("Decade"),
    YEAR("Year"),
    CENTURY("Century")
}
