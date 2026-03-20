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
import com.gedfix.ui.components.StatCard
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
