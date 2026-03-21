package com.gedfix.ui

import androidx.compose.foundation.clickable
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
import com.gedfix.models.PersonBookmark
import com.gedfix.models.SidebarSection
import com.gedfix.ui.theme.*
import com.gedfix.viewmodel.AppViewModel

/**
 * Bookmarks screen for quick access to frequently viewed persons.
 */
@Composable
fun BookmarksScreen(appViewModel: AppViewModel) {
    var bookmarks by remember { mutableStateOf(appViewModel.db.fetchAllBookmarks()) }

    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header
        Text("Bookmarks", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Text(
            "${bookmarks.size} bookmarked persons",
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (bookmarks.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("\u2605", fontSize = 48.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "No bookmarks yet",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "Bookmark persons from the People list for quick access.",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(bookmarks) { bookmark ->
                    val person = appViewModel.db.fetchPerson(bookmark.personXref)

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        tonalElevation = 1.dp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (person != null) {
                                    appViewModel.selectedPersonXref = person.xref
                                    appViewModel.selectedSection = SidebarSection.PEOPLE
                                }
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = "\u2605",
                                fontSize = 20.sp,
                                color = BookmarksIconColor
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = person?.displayName ?: bookmark.personXref,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                if (bookmark.label.isNotEmpty()) {
                                    Text(
                                        text = bookmark.label,
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (person != null) {
                                    val birth = appViewModel.db.fetchBirthEvent(person.xref)
                                    if (birth != null && birth.dateValue.isNotEmpty()) {
                                        Text(
                                            text = "b. ${birth.dateValue}",
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                            TextButton(
                                onClick = {
                                    appViewModel.db.removeBookmarkByPerson(bookmark.personXref)
                                    bookmarks = appViewModel.db.fetchAllBookmarks()
                                    appViewModel.refreshCounts()
                                },
                                colors = ButtonDefaults.textButtonColors(
                                    contentColor = MaterialTheme.colorScheme.error
                                )
                            ) {
                                Text("Remove")
                            }
                        }
                    }
                }
            }
        }
    }
}
