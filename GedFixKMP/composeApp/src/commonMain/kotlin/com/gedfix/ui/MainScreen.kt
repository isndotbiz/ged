package com.gedfix.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.gedfix.models.SidebarSection
import com.gedfix.viewmodel.AIViewModel
import com.gedfix.viewmodel.AppViewModel
import com.gedfix.viewmodel.PersonViewModel

/**
 * Main screen: Apple-style sidebar (220dp) + content area.
 * Subtle 0.5dp divider at low alpha between sidebar and content.
 * Content area has generous 24dp padding via individual screens.
 */
@Composable
fun MainScreen(
    appViewModel: AppViewModel,
    personViewModel: PersonViewModel
) {
    val aiViewModel = remember { AIViewModel(appViewModel.db) }

    Row(modifier = Modifier.fillMaxSize()) {
        // Sidebar - fixed 220dp width, no elevation
        Surface(
            modifier = Modifier
                .fillMaxHeight()
                .width(220.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            SidebarContent(viewModel = appViewModel)
        }

        // Subtle divider
        VerticalDivider(
            thickness = 0.5.dp,
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
        )

        // Content area
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            when (appViewModel.selectedSection) {
                SidebarSection.OVERVIEW -> OverviewScreen(appViewModel)
                SidebarSection.HANDLE_EVERYTHING -> HandleEverythingScreen(appViewModel)
                SidebarSection.SEARCH -> SearchScreen(appViewModel)
                SidebarSection.TIMELINE -> TimelineScreen(appViewModel)
                SidebarSection.ISSUES -> IssuesScreen(appViewModel)
                SidebarSection.PEOPLE -> PersonListScreen(appViewModel, personViewModel, aiViewModel)
                SidebarSection.PEDIGREE -> PedigreeScreen(appViewModel)
                SidebarSection.FAN_CHART -> FanChartScreen(appViewModel)
                SidebarSection.DESCENDANT_CHART -> DescendantChartScreen(appViewModel)
                SidebarSection.RELATIONSHIPS -> RelationshipScreen(appViewModel)
                SidebarSection.FAMILIES -> FamilyListScreen(appViewModel)
                SidebarSection.PLACES -> PlaceListScreen(appViewModel)
                SidebarSection.SOURCES -> SourceListScreen(appViewModel)
                SidebarSection.MEDIA -> MediaGalleryScreen(appViewModel)
                SidebarSection.MERGE -> MergeScreen(appViewModel)
                SidebarSection.VALIDATION -> ValidationScreen(appViewModel, personViewModel)
                SidebarSection.REPORTS -> ReportsScreen(appViewModel)
                SidebarSection.BOOKMARKS -> BookmarksScreen(appViewModel)
                SidebarSection.NOTES -> NotesScreen(appViewModel)
                SidebarSection.TASKS -> TasksScreen(appViewModel)
                SidebarSection.VERSION_HISTORY -> VersionHistoryScreen(appViewModel)
                SidebarSection.IMAGE_DEDUPE -> ImageDedupeScreen(appViewModel)
                SidebarSection.CLEANUP -> CleanupScreen(appViewModel)
                SidebarSection.CLOUD_SYNC -> CloudSyncScreen(appViewModel)
                SidebarSection.AI_CHAT -> AIChatScreen(appViewModel, aiViewModel)
                SidebarSection.AI_SETTINGS -> AISettingsScreen(aiViewModel)
                SidebarSection.SETTINGS -> SettingsScreen(appViewModel)
            }
        }
    }

    // Import dialog
    if (appViewModel.showImportDialog) {
        ImportDialog(
            onDismiss = { appViewModel.showImportDialog = false },
            onImport = { text -> appViewModel.importGedcom(text) },
            isImporting = appViewModel.isImporting,
            error = appViewModel.importError
        )
    }
}
