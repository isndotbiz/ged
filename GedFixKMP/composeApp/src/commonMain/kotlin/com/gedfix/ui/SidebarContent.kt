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

        items(sections) { section ->
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
    }
}

private fun sectionIcon(section: SidebarSection): String = when (section) {
    SidebarSection.OVERVIEW -> "\u2302" // House
    SidebarSection.ISSUES -> "\u26A0"   // Warning
    SidebarSection.PEOPLE -> "\u263A"   // Person
    SidebarSection.PEDIGREE -> "\u2042" // Tree-like
    SidebarSection.FAMILIES -> "\u2665" // Heart
    SidebarSection.PLACES -> "\u2316"   // Pin
    SidebarSection.SOURCES -> "\u2261"  // Book-like
}

private fun sectionIconColor(section: SidebarSection, vm: AppViewModel) = when (section) {
    SidebarSection.OVERVIEW -> OverviewIconColor
    SidebarSection.ISSUES -> if (vm.issueCount > 0) IssuesActiveColor else IssuesIconColor
    SidebarSection.PEOPLE -> PeopleIconColor
    SidebarSection.PEDIGREE -> PedigreeIconColor
    SidebarSection.FAMILIES -> FamiliesIconColor
    SidebarSection.PLACES -> PlacesIconColor
    SidebarSection.SOURCES -> SourcesIconColor
}

private fun sectionBadge(section: SidebarSection, vm: AppViewModel): String = when (section) {
    SidebarSection.OVERVIEW -> ""
    SidebarSection.ISSUES -> if (vm.issueCount > 0) vm.issueCount.toString() else ""
    SidebarSection.PEOPLE -> if (vm.personCount > 0) vm.personCount.toString() else ""
    SidebarSection.PEDIGREE -> ""
    SidebarSection.FAMILIES -> if (vm.familyCount > 0) vm.familyCount.toString() else ""
    SidebarSection.PLACES -> if (vm.placeCount > 0) vm.placeCount.toString() else ""
    SidebarSection.SOURCES -> if (vm.sourceCount > 0) vm.sourceCount.toString() else ""
}
