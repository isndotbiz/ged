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
 * Apple-style sidebar navigation.
 * - No background color on items (transparent)
 * - Selected item has subtle rounded highlight (Finder-like)
 * - Section headers in small caps, muted
 * - Icons at 16dp, consistent spacing
 * - Badge counts in small pills
 */
@Composable
fun SidebarContent(
    viewModel: AppViewModel,
    modifier: Modifier = Modifier
) {
    val sections = SidebarSection.entries

    LazyColumn(
        modifier = modifier
            .fillMaxHeight()
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(1.dp)
    ) {
        // App title
        item {
            Text(
                text = "GedFix",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 16.dp)
            )
        }

        // TREE section
        item { SidebarSectionHeader("Tree") }

        val treeSections = sections.filter {
            it != SidebarSection.AI_CHAT && it != SidebarSection.AI_SETTINGS && it != SidebarSection.SETTINGS &&
            it != SidebarSection.BOOKMARKS && it != SidebarSection.NOTES && it != SidebarSection.TASKS &&
            it != SidebarSection.VERSION_HISTORY && it != SidebarSection.IMAGE_DEDUPE &&
            it != SidebarSection.CLEANUP && it != SidebarSection.CLOUD_SYNC
        }
        items(treeSections) { section ->
            SidebarNavItem(
                section = section,
                isSelected = viewModel.selectedSection == section,
                iconColor = sectionIconColor(section, viewModel),
                badge = sectionBadge(section, viewModel),
                onClick = { viewModel.selectedSection = section }
            )
        }

        // RESEARCH section
        item { SidebarSectionHeader("Research") }

        val researchSections = listOf(
            SidebarSection.BOOKMARKS, SidebarSection.NOTES, SidebarSection.TASKS
        )
        items(researchSections) { section ->
            SidebarNavItem(
                section = section,
                isSelected = viewModel.selectedSection == section,
                iconColor = sectionIconColor(section, viewModel),
                badge = sectionBadge(section, viewModel),
                onClick = { viewModel.selectedSection = section }
            )
        }

        // DATA QUALITY section
        item { SidebarSectionHeader("Data Quality") }

        val dataQualitySections = listOf(
            SidebarSection.VERSION_HISTORY, SidebarSection.IMAGE_DEDUPE,
            SidebarSection.CLEANUP, SidebarSection.CLOUD_SYNC
        )
        items(dataQualitySections) { section ->
            SidebarNavItem(
                section = section,
                isSelected = viewModel.selectedSection == section,
                iconColor = sectionIconColor(section, viewModel),
                badge = sectionBadge(section, viewModel),
                onClick = { viewModel.selectedSection = section }
            )
        }

        // AI section
        item { SidebarSectionHeader("AI") }

        val aiSections = listOf(SidebarSection.AI_CHAT, SidebarSection.AI_SETTINGS)
        items(aiSections) { section ->
            SidebarNavItem(
                section = section,
                isSelected = viewModel.selectedSection == section,
                iconColor = sectionIconColor(section, viewModel),
                badge = sectionBadge(section, viewModel),
                onClick = { viewModel.selectedSection = section }
            )
        }

        // Settings (always last)
        item {
            Spacer(modifier = Modifier.height(4.dp))
            SidebarNavItem(
                section = SidebarSection.SETTINGS,
                isSelected = viewModel.selectedSection == SidebarSection.SETTINGS,
                iconColor = sectionIconColor(SidebarSection.SETTINGS, viewModel),
                badge = "",
                onClick = { viewModel.selectedSection = SidebarSection.SETTINGS }
            )
        }
    }
}

/**
 * Section header: understated, small caps, muted.
 */
@Composable
private fun SidebarSectionHeader(title: String) {
    Text(
        text = title.uppercase(),
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.8.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp)
    )
}

/**
 * Single sidebar nav item: Finder-style.
 * No background when unselected. Subtle rounded highlight when selected.
 */
@Composable
private fun SidebarNavItem(
    section: SidebarSection,
    isSelected: Boolean,
    iconColor: androidx.compose.ui.graphics.Color,
    badge: String,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        color = if (isSelected)
            MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
        else
            MaterialTheme.colorScheme.surface.copy(alpha = 0f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = sectionIcon(section),
                fontSize = 15.sp,
                color = if (isSelected) MaterialTheme.colorScheme.primary else iconColor.copy(alpha = 0.7f)
            )
            Text(
                text = section.label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                color = if (isSelected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            if (badge.isNotEmpty()) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.08f)
                ) {
                    Text(
                        text = badge,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
        }
    }
}

private fun sectionIcon(section: SidebarSection): String = when (section) {
    SidebarSection.OVERVIEW -> "\u2302"
    SidebarSection.SEARCH -> "\u2315"
    SidebarSection.TIMELINE -> "\u231A"
    SidebarSection.ISSUES -> "\u26A0"
    SidebarSection.PEOPLE -> "\u263A"
    SidebarSection.PEDIGREE -> "\u2042"
    SidebarSection.FAN_CHART -> "\u25D4"
    SidebarSection.DESCENDANT_CHART -> "\u2193"
    SidebarSection.RELATIONSHIPS -> "\u2194"
    SidebarSection.FAMILIES -> "\u2665"
    SidebarSection.PLACES -> "\u2316"
    SidebarSection.SOURCES -> "\u2261"
    SidebarSection.MEDIA -> "\u25A3"
    SidebarSection.MERGE -> "\u21C4"
    SidebarSection.VALIDATION -> "\u2611"
    SidebarSection.REPORTS -> "\u2637"
    SidebarSection.BOOKMARKS -> "\u2605"
    SidebarSection.NOTES -> "\u2709"
    SidebarSection.TASKS -> "\u2610"
    SidebarSection.VERSION_HISTORY -> "\u21BA"
    SidebarSection.IMAGE_DEDUPE -> "\u229A"
    SidebarSection.CLEANUP -> "\u2702"
    SidebarSection.CLOUD_SYNC -> "\u2601"
    SidebarSection.AI_CHAT -> "\u2604"
    SidebarSection.AI_SETTINGS -> "\u2318"
    SidebarSection.SETTINGS -> "\u2699"
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
