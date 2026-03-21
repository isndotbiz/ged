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
import com.gedfix.ui.components.StatCard
import com.gedfix.ui.components.eventTypeColor
import com.gedfix.ui.components.eventTypeIcon
import com.gedfix.ui.theme.*
import com.gedfix.viewmodel.AppViewModel

/**
 * Dashboard with stat cards and top surnames chart.
 */
@Composable
fun OverviewScreen(viewModel: AppViewModel) {
    val topSurnames = viewModel.fetchTopSurnames(10)
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Text(
            text = "Tree Overview",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )

        // Stats cards row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
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
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Validation Coverage",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            "${String.format("%.0f", viewModel.validationPercentage)}%",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (viewModel.validationPercentage >= 80f) HealthGoodColor
                                else if (viewModel.validationPercentage >= 50f) HealthOkColor
                                else HealthBadColor
                        )
                    }

                    LinearProgressIndicator(
                        progress = { viewModel.validatedCount.toFloat() / viewModel.personCount.toFloat() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = HealthGoodColor,
                        trackColor = HealthGoodColor.copy(alpha = 0.15f),
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            "\u2713 ${viewModel.validatedCount} validated",
                            fontSize = 13.sp,
                            color = HealthGoodColor,
                            fontWeight = FontWeight.Medium
                        )
                        Surface(
                            onClick = {
                                viewModel.selectedSection = com.gedfix.models.SidebarSection.VALIDATION
                            },
                            shape = RoundedCornerShape(8.dp),
                            color = WarningColor.copy(alpha = 0.15f)
                        ) {
                            Text(
                                "\u26A0 ${viewModel.unvalidatedCount} need sources",
                                fontSize = 13.sp,
                                color = WarningColor,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }
        }

        // Event type breakdown
        if (viewModel.eventCount > 0) {
            val eventTypeCounts = viewModel.db.fetchEventTypeCounts()
            if (eventTypeCounts.isNotEmpty()) {
                HorizontalDivider()
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Event Type Breakdown",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
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
                HorizontalDivider()
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Events by Decade",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
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
                HorizontalDivider()
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Top Places",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
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
                HorizontalDivider()
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Source Coverage",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold
                        )

                        // Stacked bar
                        val sourcedFraction = sourced.toFloat() / total.toFloat()
                        Row(
                            modifier = Modifier.fillMaxWidth().height(24.dp)
                                .clip(RoundedCornerShape(6.dp))
                        ) {
                            if (sourcedFraction > 0f) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .weight(sourcedFraction)
                                        .background(ChartSourcedColor)
                                )
                            }
                            if (sourcedFraction < 1f) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .weight(1f - sourcedFraction)
                                        .background(ChartUnsourcedColor)
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(24.dp)
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier.size(12.dp)
                                        .clip(RoundedCornerShape(3.dp))
                                        .background(ChartSourcedColor)
                                )
                                Text(
                                    "$sourced sourced (${String.format("%.0f", sourcedFraction * 100)}%)",
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier.size(12.dp)
                                        .clip(RoundedCornerShape(3.dp))
                                        .background(ChartUnsourcedColor)
                                )
                                Text(
                                    "$unsourced unsourced (${String.format("%.0f", (1f - sourcedFraction) * 100)}%)",
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }

        // Top surnames
        if (topSurnames.isNotEmpty()) {
            HorizontalDivider()

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Top Surnames",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground
                )

                val maxCount = topSurnames.firstOrNull()?.second ?: 1

                for ((name, count) in topSurnames) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = name,
                            fontWeight = FontWeight.Medium,
                            fontSize = 14.sp,
                            modifier = Modifier.width(120.dp),
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(20.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .fillMaxWidth(fraction = count.toFloat() / maxCount.toFloat())
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.7f))
                            )
                        }

                        Text(
                            text = count.toString(),
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.width(40.dp)
                        )
                    }
                }
            }
        }

        // Empty state
        if (viewModel.personCount == 0) {
            Spacer(modifier = Modifier.height(40.dp))
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "No Tree Loaded",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Import a GEDCOM file to get started.",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                Button(onClick = { viewModel.showImportDialog = true }) {
                    Text("Import GEDCOM...")
                }
            }
        }
    }
}

/**
 * Reusable horizontal bar chart row for statistics display.
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
                fontSize = 14.sp,
                color = iconColor ?: barColor,
                modifier = Modifier.width(20.dp)
            )
        }
        Text(
            text = label,
            fontWeight = FontWeight.Medium,
            fontSize = 13.sp,
            modifier = Modifier.width(labelWidth.dp),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .height(18.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(fraction = if (maxValue > 0) value.toFloat() / maxValue.toFloat() else 0f)
                    .clip(RoundedCornerShape(4.dp))
                    .background(barColor.copy(alpha = 0.7f))
            )
        }
        Text(
            text = value.toString(),
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(40.dp)
        )
    }
}
