package com.gedfix.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gedfix.models.GedcomPlace
import com.gedfix.ui.theme.*
import com.gedfix.viewmodel.AppViewModel
import kotlin.math.*

@Composable
fun MapsScreen(appViewModel: AppViewModel) {
    val places = remember { appViewModel.db.fetchAllPlaces() }
    val geocodedPlaces = remember { places.filter { it.latitude != null && it.longitude != null } }
    var selectedPlace by remember { mutableStateOf<GedcomPlace?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Text("Geographic Map", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Text(
            "${geocodedPlaces.size} of ${places.size} places have coordinates",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (geocodedPlaces.isEmpty()) {
            Surface(shape = RoundedCornerShape(12.dp), tonalElevation = 1.dp, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("No geocoded places", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Places need latitude/longitude coordinates to appear on the map.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Use the gedfix CLI with place geocoding to add coordinates.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            // Map canvas
            Surface(
                shape = RoundedCornerShape(12.dp),
                tonalElevation = 1.dp,
                modifier = Modifier.fillMaxWidth().height(500.dp)
            ) {
                Box(modifier = Modifier.padding(8.dp)) {
                    PlaceMapCanvas(
                        places = geocodedPlaces,
                        onPlaceClick = { selectedPlace = it }
                    )
                }
            }

            // Selected place detail
            if (selectedPlace != null) {
                val sp = selectedPlace!!
                Surface(shape = RoundedCornerShape(12.dp), tonalElevation = 1.dp, modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(sp.name, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Text("Lat: ${sp.latitude}, Lon: ${sp.longitude}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("${sp.eventCount} events at this location", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            // Top places list
            Surface(shape = RoundedCornerShape(12.dp), tonalElevation = 1.dp, modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(20.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Places by Event Count", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                    for (place in geocodedPlaces.sortedByDescending { it.eventCount }.take(20)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(place.name, fontSize = 13.sp, modifier = Modifier.weight(1f))
                            Surface(shape = RoundedCornerShape(12.dp), color = PlacesIconColor.copy(alpha = 0.10f)) {
                                Text("${place.eventCount}", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = PlacesIconColor, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Simple Mercator projection canvas that plots place dots.
 * No external map library required.
 */
@Composable
private fun PlaceMapCanvas(
    places: List<GedcomPlace>,
    onPlaceClick: (GedcomPlace) -> Unit
) {
    val dotColor = PlacesIconColor
    val bgColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
    val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)

    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height

        // Background
        drawRect(bgColor)

        // Grid lines (every 30 degrees)
        for (lon in -180..180 step 30) {
            val x = ((lon + 180.0) / 360.0 * w).toFloat()
            drawLine(gridColor, Offset(x, 0f), Offset(x, h), strokeWidth = 0.5f)
        }
        for (lat in -90..90 step 30) {
            val y = ((90.0 - lat) / 180.0 * h).toFloat()
            drawLine(gridColor, Offset(0f, y), Offset(w, y), strokeWidth = 0.5f)
        }

        // Plot places
        for (place in places) {
            val lat = place.latitude ?: continue
            val lon = place.longitude ?: continue

            val x = ((lon + 180.0) / 360.0 * w).toFloat()
            val y = ((90.0 - lat) / 180.0 * h).toFloat()

            // Size by event count
            val radius = (3.0 + ln(place.eventCount.toDouble() + 1.0) * 3.0).toFloat()

            drawCircle(
                color = dotColor.copy(alpha = 0.3f),
                radius = radius + 2f,
                center = Offset(x, y)
            )
            drawCircle(
                color = dotColor,
                radius = radius,
                center = Offset(x, y)
            )
        }
    }
}
