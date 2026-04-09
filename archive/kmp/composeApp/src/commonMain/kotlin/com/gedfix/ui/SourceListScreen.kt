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
import com.gedfix.ui.theme.*
import com.gedfix.viewmodel.AppViewModel

/**
 * Source list with search - displays title, author, publisher, xref, and citation count.
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
        // Header with total citation count
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Sources",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
            val totalCitations = viewModel.citationCount
            if (totalCitations > 0) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = CitationPrimaryBg
                ) {
                    Text(
                        "$totalCitations citation${if (totalCitations != 1) "s" else ""} total",
                        fontSize = 12.sp,
                        color = CitationPrimaryColor,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }
        }

        // Search
        OutlinedTextField(
            value = searchText,
            onValueChange = { searchText = it },
            label = { Text("Search sources") },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            singleLine = true
        )

        Spacer(Modifier.height(8.dp))

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)
        ) {
            items(sources, key = { it.id }) { source ->
                val citationCount = remember(source.xref) {
                    viewModel.db.citationCountForSource(source.xref)
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = source.title.ifEmpty { "(Untitled)" },
                            fontWeight = FontWeight.Medium,
                            fontSize = 14.sp,
                            modifier = Modifier.weight(1f)
                        )
                        if (citationCount > 0) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = SourcesIconColor.copy(alpha = 0.15f)
                            ) {
                                Text(
                                    "$citationCount cite${if (citationCount != 1) "s" else ""}",
                                    fontSize = 11.sp,
                                    color = SourcesIconColor,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }

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
