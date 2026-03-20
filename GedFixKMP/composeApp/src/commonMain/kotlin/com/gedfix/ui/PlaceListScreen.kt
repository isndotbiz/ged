package com.gedfix.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gedfix.ui.theme.PlacesIconColor
import com.gedfix.viewmodel.AppViewModel

/**
 * Place list with event counts and search functionality.
 */
@Composable
fun PlaceListScreen(viewModel: AppViewModel) {
    var searchText by remember { mutableStateOf("") }

    val allPlaces = viewModel.db.fetchAllPlaces()
    val places = if (searchText.isEmpty()) allPlaces
    else allPlaces.filter { it.name.contains(searchText, ignoreCase = true) }

    Column(modifier = Modifier.fillMaxSize()) {
        // Search
        OutlinedTextField(
            value = searchText,
            onValueChange = { searchText = it },
            label = { Text("Search places") },
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            singleLine = true
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)
        ) {
            items(places, key = { it.id }) { place ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "\u2316",
                        fontSize = 20.sp,
                        color = PlacesIconColor
                    )

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = place.name,
                            fontWeight = FontWeight.Medium,
                            fontSize = 14.sp
                        )
                        Text(
                            text = "${place.eventCount} event${if (place.eventCount == 1) "" else "s"}",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = PlacesIconColor.copy(alpha = 0.1f)
                    ) {
                        Text(
                            text = place.eventCount.toString(),
                            fontSize = 12.sp,
                            color = PlacesIconColor,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }

                HorizontalDivider()
            }
        }
    }
}
