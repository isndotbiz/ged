package com.gedfix.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gedfix.models.SidebarSection
import com.gedfix.ui.theme.*
import com.gedfix.viewmodel.AppViewModel

/**
 * Sidebar with navigation items: Overview, Issues, People, Pedigree, Families, Places, Sources.
 */
@Composable
fun SidebarContent(
    viewModel: AppViewModel,
    modifier: Modifier = Modifier
) {
    val sections = SidebarSection.entries

    LazyColumn(
        modifier = modifier
            .width(240.dp)
            .fillMaxHeight()
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        item {
            Text(
                text = "GedFix",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 16.dp)
            )
        }

        item {
            Text(
                text = "TREE",
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
            )
        }

        // Tree sections (core navigation)
        val treeSections = sections.filter {
            it != SidebarSection.AI_CHAT && it != SidebarSection.AI_SETTINGS && it != SidebarSection.SETTINGS &&
            it != SidebarSection.BOOKMARKS && it != SidebarSection.NOTES && it != SidebarSection.TASKS &&
            it != SidebarSection.VERSION_HISTORY && it != SidebarSection.IMAGE_DEDUPE &&
            it != SidebarSection.CLEANUP && it != SidebarSection.CLOUD_SYNC
        }
        items(treeSections) { section ->
            val isSelected = viewModel.selectedSection == section
            val iconColor = sectionIconColor(section, viewModel)
            val badge = sectionBadge(section, viewModel)

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { viewModel.selectedSection = section },
                shape = RoundedCornerShape(8.dp),
                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surface
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = sectionIcon(section),
                        fontSize = 16.sp,
                        color = iconColor
                    )
                    Text(
                        text = section.label,
                        fontSize = 14.sp,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    if (badge.isNotEmpty()) {
                        Text(
                            text = badge,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // Research section header
        item {
            Text(
                text = "RESEARCH",
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
            )
        }

        val researchSections = listOf(
            SidebarSection.BOOKMARKS, SidebarSection.NOTES, SidebarSection.TASKS
        )
        items(researchSections) { section ->
            val isSelected = viewModel.selectedSection == section
            val iconColor = sectionIconColor(section, viewModel)
            val badge = sectionBadge(section, viewModel)

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { viewModel.selectedSection = section },
                shape = RoundedCornerShape(8.dp),
                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surface
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = sectionIcon(section),
                        fontSize = 16.sp,
                        color = iconColor
                    )
                    Text(
                        text = section.label,
                        fontSize = 14.sp,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    if (badge.isNotEmpty()) {
                        Text(
                            text = badge,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // Data Quality section header
        item {
            Text(
                text = "DATA QUALITY",
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
            )
        }

        val dataQualitySections = listOf(
            SidebarSection.VERSION_HISTORY, SidebarSection.IMAGE_DEDUPE,
            SidebarSection.CLEANUP, SidebarSection.CLOUD_SYNC
        )
        items(dataQualitySections) { section ->
            val isSelected = viewModel.selectedSection == section
            val iconColor = sectionIconColor(section, viewModel)
            val badge = sectionBadge(section, viewModel)

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { viewModel.selectedSection = section },
                shape = RoundedCornerShape(8.dp),
                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surface
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = sectionIcon(section),
                        fontSize = 16.sp,
                        color = iconColor
                    )
                    Text(
                        text = section.label,
                        fontSize = 14.sp,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    if (badge.isNotEmpty()) {
                        Text(
                            text = badge,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // AI section header
        item {
            Text(
                text = "AI",
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
            )
        }

        val aiSections = listOf(SidebarSection.AI_CHAT, SidebarSection.AI_SETTINGS)
        items(aiSections) { section ->
            val isSelected = viewModel.selectedSection == section
            val iconColor = sectionIconColor(section, viewModel)
            val badge = sectionBadge(section, viewModel)

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { viewModel.selectedSection = section },
                shape = RoundedCornerShape(8.dp),
                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surface
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = sectionIcon(section),
                        fontSize = 16.sp,
                        color = iconColor
                    )
                    Text(
                        text = section.label,
                        fontSize = 14.sp,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    if (badge.isNotEmpty()) {
                        Text(
                            text = badge,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // Settings section (always last)
        item {
            val isSelected = viewModel.selectedSection == SidebarSection.SETTINGS
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { viewModel.selectedSection = SidebarSection.SETTINGS },
                shape = RoundedCornerShape(8.dp),
                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surface
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = sectionIcon(SidebarSection.SETTINGS),
                        fontSize = 16.sp,
                        color = sectionIconColor(SidebarSection.SETTINGS, viewModel)
                    )
                    Text(
                        text = SidebarSection.SETTINGS.label,
                        fontSize = 14.sp,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

private fun sectionIcon(section: SidebarSection): String = when (section) {
    SidebarSection.OVERVIEW -> "\u2302"  // House
    SidebarSection.SEARCH -> "\u2315"    // Magnifying glass
    SidebarSection.TIMELINE -> "\u231A"  // Clock
    SidebarSection.ISSUES -> "\u26A0"    // Warning
    SidebarSection.PEOPLE -> "\u263A"    // Person
    SidebarSection.PEDIGREE -> "\u2042"  // Tree-like
    SidebarSection.FAN_CHART -> "\u25D4"  // Circle with fill (fan)
    SidebarSection.DESCENDANT_CHART -> "\u2193"  // Down arrow (descendants)
    SidebarSection.RELATIONSHIPS -> "\u2194"  // Left-right arrow (relationship)
    SidebarSection.FAMILIES -> "\u2665"  // Heart
    SidebarSection.PLACES -> "\u2316"    // Pin
    SidebarSection.SOURCES -> "\u2261"   // Book-like
    SidebarSection.MEDIA -> "\u25A3"     // Image
    SidebarSection.MERGE -> "\u21C4"     // Merge arrows
    SidebarSection.VALIDATION -> "\u2611" // Checkmark box
    SidebarSection.REPORTS -> "\u2637"   // Document
    SidebarSection.BOOKMARKS -> "\u2605" // Star
    SidebarSection.NOTES -> "\u2709"     // Note/envelope
    SidebarSection.TASKS -> "\u2610"     // Checkbox
    SidebarSection.VERSION_HISTORY -> "\u21BA" // Circular arrow (history)
    SidebarSection.IMAGE_DEDUPE -> "\u229A"    // Circled ring (dedupe)
    SidebarSection.CLEANUP -> "\u2702"         // Scissors (cleanup)
    SidebarSection.CLOUD_SYNC -> "\u2601"      // Cloud
    SidebarSection.AI_CHAT -> "\u2604"   // Chat bubble
    SidebarSection.AI_SETTINGS -> "\u2318" // AI gear
    SidebarSection.SETTINGS -> "\u2699"  // Gear
}

private fun sectionIconColor(section: SidebarSection, vm: AppViewModel) = when (section) {
    SidebarSection.OVERVIEW -> OverviewIconColor
    SidebarSection.SEARCH -> SearchIconColor
    SidebarSection.TIMELINE -> TimelineIconColor
    SidebarSection.ISSUES -> if (vm.issueCount > 0) IssuesActiveColor else IssuesIconColor
    SidebarSection.PEOPLE -> PeopleIconColor
    SidebarSection.PEDIGREE -> PedigreeIconColor
    SidebarSection.FAN_CHART -> FanChartIconColor
    SidebarSection.DESCENDANT_CHART -> DescendantChartIconColor
    SidebarSection.RELATIONSHIPS -> RelationshipIconColor
    SidebarSection.FAMILIES -> FamiliesIconColor
    SidebarSection.PLACES -> PlacesIconColor
    SidebarSection.SOURCES -> SourcesIconColor
    SidebarSection.MEDIA -> MediaIconColor
    SidebarSection.MERGE -> MergeIconColor
    SidebarSection.VALIDATION -> ValidationIconColor
    SidebarSection.REPORTS -> ReportsIconColor
    SidebarSection.BOOKMARKS -> BookmarksIconColor
    SidebarSection.NOTES -> NotesIconColor
    SidebarSection.TASKS -> TasksIconColor
    SidebarSection.VERSION_HISTORY -> VersionHistoryIconColor
    SidebarSection.IMAGE_DEDUPE -> ImageDedupeIconColor
    SidebarSection.CLEANUP -> CleanupIconColor
    SidebarSection.CLOUD_SYNC -> CloudSyncIconColor
    SidebarSection.AI_CHAT -> AIChatIconColor
    SidebarSection.AI_SETTINGS -> AISettingsIconColor
    SidebarSection.SETTINGS -> SettingsIconColor
}

private fun sectionBadge(section: SidebarSection, vm: AppViewModel): String = when (section) {
    SidebarSection.OVERVIEW -> ""
    SidebarSection.SEARCH -> ""
    SidebarSection.TIMELINE -> ""
    SidebarSection.ISSUES -> if (vm.issueCount > 0) vm.issueCount.toString() else ""
    SidebarSection.PEOPLE -> if (vm.personCount > 0) vm.personCount.toString() else ""
    SidebarSection.PEDIGREE -> ""
    SidebarSection.FAN_CHART -> ""
    SidebarSection.DESCENDANT_CHART -> ""
    SidebarSection.RELATIONSHIPS -> ""
    SidebarSection.FAMILIES -> if (vm.familyCount > 0) vm.familyCount.toString() else ""
    SidebarSection.PLACES -> if (vm.placeCount > 0) vm.placeCount.toString() else ""
    SidebarSection.SOURCES -> if (vm.sourceCount > 0) vm.sourceCount.toString() else ""
    SidebarSection.MEDIA -> if (vm.mediaCount > 0) vm.mediaCount.toString() else ""
    SidebarSection.MERGE -> if (vm.duplicateCount > 0) vm.duplicateCount.toString() else ""
    SidebarSection.VALIDATION -> if (vm.unvalidatedCount > 0) vm.unvalidatedCount.toString() else ""
    SidebarSection.REPORTS -> ""
    SidebarSection.BOOKMARKS -> if (vm.bookmarkCount > 0) vm.bookmarkCount.toString() else ""
    SidebarSection.NOTES -> if (vm.noteCount > 0) vm.noteCount.toString() else ""
    SidebarSection.TASKS -> if (vm.pendingTaskCount > 0) vm.pendingTaskCount.toString() else ""
    SidebarSection.VERSION_HISTORY -> if (vm.versionCount > 0) vm.versionCount.toString() else ""
    SidebarSection.IMAGE_DEDUPE -> ""
    SidebarSection.CLEANUP -> ""
    SidebarSection.CLOUD_SYNC -> ""
    SidebarSection.AI_CHAT -> ""
    SidebarSection.AI_SETTINGS -> ""
    SidebarSection.SETTINGS -> ""
}
