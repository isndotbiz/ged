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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gedfix.models.*
import com.gedfix.ui.components.eventTypeColor
import com.gedfix.ui.components.eventTypeIcon
import com.gedfix.ui.theme.*
import com.gedfix.viewmodel.AppViewModel

/**
 * Global search screen that searches across all data types:
 * persons, places, sources, and events.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    appViewModel: AppViewModel,
    onPersonSelected: (String) -> Unit = {}
) {
    var searchQuery by remember { mutableStateOf("") }
    var results by remember { mutableStateOf(GlobalSearchResults()) }
    var recentSearches by remember { mutableStateOf(listOf<String>()) }
    var hasSearched by remember { mutableStateOf(false) }

    // Debounced search: re-run when query changes
    LaunchedEffect(searchQuery) {
        if (searchQuery.length >= 2) {
            kotlinx.coroutines.delay(300)
            results = appViewModel.db.globalSearch(searchQuery)
            hasSearched = true
            // Add to recent searches (keep last 10, no duplicates)
            if (searchQuery.isNotBlank() && searchQuery !in recentSearches) {
                recentSearches = (listOf(searchQuery) + recentSearches).take(10)
            }
        } else {
            results = GlobalSearchResults()
            hasSearched = false
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header
        Text(
            text = "Search",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )

        // Search field
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Search people, places, sources, events...") },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            leadingIcon = {
                Text("\u2315", fontSize = 18.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    Surface(
                        modifier = Modifier.clickable { searchQuery = ""; hasSearched = false },
                        color = MaterialTheme.colorScheme.surface
                    ) {
                        Text(
                            "\u2715",
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(4.dp)
                        )
                    }
                }
            }
        )

        // Keyboard shortcut hint
        Text(
            text = "Tip: Use Cmd+F to jump to search",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (!hasSearched && searchQuery.length < 2) {
            // Empty / initial state
            if (recentSearches.isNotEmpty()) {
                Text(
                    text = "Recent Searches",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )
                for (recent in recentSearches) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { searchQuery = recent },
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text("\u231A", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(recent, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
            } else {
                // True empty state
                Spacer(modifier = Modifier.height(60.dp))
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "\u2315",
                        fontSize = 48.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    )
                    Text(
                        text = "Search across your entire family tree",
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Find people, places, sources, and events",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }
        } else if (hasSearched && results.isEmpty) {
            // No results
            Spacer(modifier = Modifier.height(40.dp))
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "No results found",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Try a different search term",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        } else if (hasSearched) {
            // Results header
            Text(
                text = "${results.totalCount} results found",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Results list
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // People results
                if (results.persons.isNotEmpty()) {
                    item {
                        SearchCategoryHeader(
                            icon = "\u263A",
                            title = "People",
                            count = results.persons.size,
                            color = PeopleIconColor
                        )
                    }
                    items(results.persons) { person ->
                        SearchResultCard(
                            icon = "\u263A",
                            iconColor = if (person.sex == "M") MaleColor else if (person.sex == "F") FemaleColor else UnknownGenderColor,
                            primaryText = person.displayName,
                            secondaryText = if (person.isLiving) "Living" else "",
                            onClick = {
                                appViewModel.selectedPersonXref = person.xref
                                appViewModel.selectedSection = SidebarSection.PEOPLE
                                onPersonSelected(person.xref)
                            }
                        )
                    }
                }

                // Places results
                if (results.places.isNotEmpty()) {
                    item {
                        SearchCategoryHeader(
                            icon = "\u2316",
                            title = "Places",
                            count = results.places.size,
                            color = PlacesIconColor
                        )
                    }
                    items(results.places) { place ->
                        SearchResultCard(
                            icon = "\u2316",
                            iconColor = PlacesIconColor,
                            primaryText = place.name,
                            secondaryText = "${place.eventCount} events",
                            onClick = {
                                appViewModel.selectedSection = SidebarSection.PLACES
                            }
                        )
                    }
                }

                // Sources results
                if (results.sources.isNotEmpty()) {
                    item {
                        SearchCategoryHeader(
                            icon = "\u2261",
                            title = "Sources",
                            count = results.sources.size,
                            color = SourcesIconColor
                        )
                    }
                    items(results.sources) { source ->
                        SearchResultCard(
                            icon = "\u2261",
                            iconColor = SourcesIconColor,
                            primaryText = source.title.ifEmpty { "(Untitled)" },
                            secondaryText = source.author,
                            onClick = {
                                appViewModel.selectedSection = SidebarSection.SOURCES
                            }
                        )
                    }
                }

                // Events results
                if (results.events.isNotEmpty()) {
                    item {
                        SearchCategoryHeader(
                            icon = "\u2606",
                            title = "Events",
                            count = results.events.size,
                            color = StatEventsColor
                        )
                    }
                    items(results.events) { event ->
                        SearchResultCard(
                            icon = eventTypeIcon(event.eventType),
                            iconColor = eventTypeColor(event.eventType),
                            primaryText = "${event.displayType}: ${event.displayDate}",
                            secondaryText = event.place,
                            onClick = {
                                // Navigate to the person who owns this event
                                if (event.ownerType == "INDI") {
                                    appViewModel.selectedPersonXref = event.ownerXref
                                    appViewModel.selectedSection = SidebarSection.PEOPLE
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchCategoryHeader(
    icon: String,
    title: String,
    count: Int,
    color: androidx.compose.ui.graphics.Color
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(text = icon, fontSize = 16.sp, color = color)
        Text(
            text = title,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = color.copy(alpha = 0.15f)
        ) {
            Text(
                text = count.toString(),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = color,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
            )
        }
    }
}

@Composable
private fun SearchResultCard(
    icon: String,
    iconColor: androidx.compose.ui.graphics.Color,
    primaryText: String,
    secondaryText: String,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = icon,
                fontSize = 18.sp,
                color = iconColor
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = primaryText,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (secondaryText.isNotEmpty()) {
                    Text(
                        text = secondaryText,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Text(
                text = "\u203A",
                fontSize = 18.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
        }
    }
}

