package com.gedfix.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.gedfix.models.SidebarSection
import com.gedfix.viewmodel.AppViewModel
import com.gedfix.viewmodel.PersonViewModel

/**
 * Main screen with NavigationRail-style sidebar + content area.
 * Desktop uses permanent sidebar; mobile would use NavigationBar (future).
 */
@Composable
fun MainScreen(
    appViewModel: AppViewModel,
    personViewModel: PersonViewModel
) {
    Row(modifier = Modifier.fillMaxSize()) {
        // Sidebar
        Surface(
            modifier = Modifier.fillMaxHeight(),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 1.dp
        ) {
            SidebarContent(viewModel = appViewModel)
        }

        // Vertical divider
        VerticalDivider()

        // Content area
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            when (appViewModel.selectedSection) {
                SidebarSection.OVERVIEW -> OverviewScreen(appViewModel)
                SidebarSection.ISSUES -> IssuesScreen(appViewModel)
                SidebarSection.PEOPLE -> PersonListScreen(appViewModel, personViewModel)
                SidebarSection.PEDIGREE -> PedigreeScreen(appViewModel)
                SidebarSection.FAMILIES -> FamilyListScreen(appViewModel)
                SidebarSection.PLACES -> PlaceListScreen(appViewModel)
                SidebarSection.SOURCES -> SourceListScreen(appViewModel)
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
