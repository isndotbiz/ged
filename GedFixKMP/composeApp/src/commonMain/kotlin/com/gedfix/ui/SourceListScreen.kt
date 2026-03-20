package com.gedfix.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gedfix.viewmodel.AppViewModel

/**
 * Source list with search - displays title, author, publisher, xref.
 */
@Composable
fun SourceListScreen(viewModel: AppViewModel) {
    var searchText by remember { mutableStateOf("") }

    val allSources = viewModel.db.fetchAllSources()
    val sources = if (searchText.isEmpty()) allSources
    else allSources.filter {
        it.title.contains(searchText, ignoreCase = true) ||
            it.author.contains(searchText, ignoreCase = true)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Search
        OutlinedTextField(
            value = searchText,
            onValueChange = { searchText = it },
            label = { Text("Search sources") },
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            singleLine = true
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)
        ) {
            items(sources, key = { it.id }) { source ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = source.title.ifEmpty { "(Untitled)" },
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.sp
                    )

                    if (source.author.isNotEmpty()) {
                        Text(
                            text = "\u263A ${source.author}",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (source.publisher.isNotEmpty()) {
                        Text(
                            text = "\u2302 ${source.publisher}",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Text(
                        text = source.xref,
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                }

                HorizontalDivider()
            }
        }
    }
}
