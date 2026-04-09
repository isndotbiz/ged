package com.gedfix.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Color
import com.gedfix.ui.components.GedFixCard
import com.gedfix.ui.components.SectionHeader
import com.gedfix.ui.components.StatCard
import com.gedfix.ui.components.eventTypeColor
import com.gedfix.ui.components.eventTypeIcon
import com.gedfix.ui.theme.*
import com.gedfix.viewmodel.AppViewModel

/**
 * Dashboard with stat cards, validation coverage, and charts.
 * Apple-polished: spacious, muted colors, generous whitespace.
 */
@Composable
fun OverviewScreen(viewModel: AppViewModel) {
    val topSurnames = viewModel.fetchTopSurnames(10)
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.xl)
    ) {
        // Page title
        Text(
            text = "Tree Overview",
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onBackground
        )

        // Stats cards row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            StatCard(
                title = "People",
                count = viewModel.personCount,
                iconText = "\u263A",
                color = StatPeopleColor,
                modifier = Modifier.weight(1f)
            )
            StatCard(
                title = "Families",
                count = viewModel.familyCount,
                iconText = "\u2302",
                color = StatFamiliesColor,
                modifier = Modifier.weight(1f)
            )
            StatCard(
                title = "Events",
                count = viewModel.eventCount,
                iconText = "\u2606",
                color = StatEventsColor,
                modifier = Modifier.weight(1f)
            )
            StatCard(
                title = "Places",
                count = viewModel.placeCount,
                iconText = "\u2316",
                color = StatPlacesColor,
                modifier = Modifier.weight(1f)
            )
            StatCard(
                title = "Sources",
                count = viewModel.sourceCount,
                iconText = "\u2261",
                color = StatSourcesColor,
                modifier = Modifier.weight(1f)
            )
            StatCard(
                title = "Media",
                count = viewModel.mediaCount,
                iconText = "\u25A3",
                color = StatMediaColor,
                modifier = Modifier.weight(1f)
            )
        }

        // Validation coverage card
        if (viewModel.personCount > 0) {
            GedFixCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Validation Coverage",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        "${String.format("%.0f", viewModel.validationPercentage)}%",
                        style = MaterialTheme.typography.headlineMedium,
                        color = if (viewModel.validationPercentage >= 80f) HealthGoodColor
                            else if (viewModel.validationPercentage >= 50f) HealthOkColor
                            else HealthBadColor
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Thin progress bar
                LinearProgressIndicator(
                    progress = { viewModel.validatedCount.toFloat() / viewModel.personCount.toFloat() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = HealthGoodColor,
                    trackColor = HealthGoodColor.copy(alpha = 0.10f),
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "\u2713 ${viewModel.validatedCount} validated",
                        style = MaterialTheme.typography.labelLarge,
                        color = HealthGoodColor
                    )
                    Surface(
                        onClick = {
                            viewModel.selectedSection = com.gedfix.models.SidebarSection.VALIDATION
                        },
                        shape = RoundedCornerShape(8.dp),
                        color = WarningColor.copy(alpha = 0.10f)
                    ) {
                        Text(
                            "\u26A0 ${viewModel.unvalidatedCount} need sources",
                            style = MaterialTheme.typography.labelLarge,
                            color = WarningColor,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        }

        // Event type breakdown
        if (viewModel.eventCount > 0) {
            val eventTypeCounts = viewModel.db.fetchEventTypeCounts()
            if (eventTypeCounts.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SectionHeader(title = "Event Types")
                    val maxEventCount = eventTypeCounts.firstOrNull()?.count ?: 1
                    for (etc in eventTypeCounts) {
                        val displayType = com.gedfix.models.GedcomEvent(
                            id = "", ownerXref = "", ownerType = "", eventType = etc.eventType,
                            dateValue = "", place = "", description = ""
                        ).displayType
                        val color = eventTypeColor(etc.eventType)
                        HorizontalBarRow(
                            label = displayType,
                            value = etc.count,
                            maxValue = maxEventCount,
                            barColor = color,
                            icon = eventTypeIcon(etc.eventType),
                            iconColor = color
                        )
                    }
                }
            }
        }

        // Decade distribution
        if (viewModel.eventCount > 0) {
            val decadeCounts = viewModel.db.fetchDecadeCounts()
            if (decadeCounts.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SectionHeader(title = "Events by Decade")
                    val maxDecadeCount = decadeCounts.maxOfOrNull { it.count } ?: 1
                    for (dc in decadeCounts) {
                        HorizontalBarRow(
                            label = "${dc.decade}s",
                            value = dc.count,
                            maxValue = maxDecadeCount,
                            barColor = ChartBarColor
                        )
                    }
                }
            }
        }

        // Top places
        if (viewModel.placeCount > 0) {
            val topPlaces = viewModel.db.fetchTopPlaces(15)
            if (topPlaces.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SectionHeader(title = "Top Places")
                    val maxPlaceCount = topPlaces.firstOrNull()?.second ?: 1
                    for ((placeName, count) in topPlaces) {
                        HorizontalBarRow(
                            label = placeName,
                            value = count,
                            maxValue = maxPlaceCount,
                            barColor = PlacesIconColor
                        )
                    }
                }
            }
        }

        // Source coverage
        if (viewModel.personCount > 0) {
            val sourced = viewModel.db.sourcedPersonCount()
            val unsourced = viewModel.db.unsourcedPersonCount()
            val total = sourced + unsourced
            if (total > 0) {
                GedFixCard(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Source Coverage",
                        style = MaterialTheme.typography.titleMedium
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Thin stacked bar
                    val sourcedFraction = sourced.toFloat() / total.toFloat()
                    Row(
                        modifier = Modifier.fillMaxWidth().height(16.dp)
                            .clip(RoundedCornerShape(8.dp))
                    ) {
                        if (sourcedFraction > 0f) {
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .weight(sourcedFraction)
                                    .background(ChartSourcedColor.copy(alpha = 0.7f))
                            )
                        }
                        if (sourcedFraction < 1f) {
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .weight(1f - sourcedFraction)
                                    .background(ChartUnsourcedColor.copy(alpha = 0.7f))
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.lg)
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier.size(10.dp)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(ChartSourcedColor.copy(alpha = 0.7f))
                            )
                            Text(
                                "$sourced sourced (${String.format("%.0f", sourcedFraction * 100)}%)",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier.size(10.dp)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(ChartUnsourcedColor.copy(alpha = 0.7f))
                            )
                            Text(
                                "$unsourced unsourced (${String.format("%.0f", (1f - sourcedFraction) * 100)}%)",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        // Top surnames with thin elegant bars
        if (topSurnames.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SectionHeader(title = "Top Surnames")

                val maxCount = topSurnames.firstOrNull()?.second ?: 1

                for ((name, count) in topSurnames) {
                    HorizontalBarRow(
                        label = name,
                        value = count,
                        maxValue = maxCount,
                        barColor = MaterialTheme.colorScheme.primary,
                        labelWidth = 120
                    )
                }
            }
        }

        // Empty state
        if (viewModel.personCount == 0) {
            Spacer(modifier = Modifier.height(Spacing.xxl))
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "No Tree Loaded",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Import a GEDCOM file to get started.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(Spacing.sm))
                Button(
                    onClick = { viewModel.showImportDialog = true },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Import GEDCOM...")
                }
            }
        }
    }
}

/**
 * Reusable horizontal bar chart row. Thin bars, elegant typography.
 */
@Composable
internal fun HorizontalBarRow(
    label: String,
    value: Int,
    maxValue: Int,
    barColor: Color,
    icon: String? = null,
    iconColor: Color? = null,
    labelWidth: Int = 140
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (icon != null) {
            Text(
                text = icon,
                fontSize = 13.sp,
                color = iconColor ?: barColor.copy(alpha = 0.7f),
                modifier = Modifier.width(18.dp)
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.width(labelWidth.dp),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .height(14.dp)
        ) {
            // Track
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(7.dp))
                    .background(barColor.copy(alpha = 0.06f))
            )
            // Fill
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(fraction = if (maxValue > 0) value.toFloat() / maxValue.toFloat() else 0f)
                    .clip(RoundedCornerShape(7.dp))
                    .background(barColor.copy(alpha = 0.5f))
            )
        }
        Text(
            text = value.toString(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(40.dp)
        )
    }
}
