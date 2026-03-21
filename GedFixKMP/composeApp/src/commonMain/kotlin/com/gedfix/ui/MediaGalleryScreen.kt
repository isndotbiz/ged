package com.gedfix.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gedfix.models.GedcomMedia
import com.gedfix.ui.theme.*
import com.gedfix.ui.theme.Spacing
import com.gedfix.viewmodel.AppViewModel
import java.io.File

/**
 * Media gallery screen with grid layout, search, and filtering.
 */

enum class MediaFilter(val label: String) {
    ALL("All"),
    PHOTOS("Photos"),
    DOCUMENTS("Documents")
}

@Composable
fun MediaGalleryScreen(viewModel: AppViewModel) {
    var searchText by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf(MediaFilter.ALL) }
    var selectedMedia by remember { mutableStateOf<GedcomMedia?>(null) }
    var allMedia by remember { mutableStateOf<List<GedcomMedia>>(emptyList()) }

    LaunchedEffect(searchText) {
        allMedia = if (searchText.isEmpty()) {
            viewModel.db.fetchAllMedia()
        } else {
            viewModel.db.searchMedia(searchText)
        }
    }

    val filteredMedia = allMedia.filter { media ->
        when (selectedFilter) {
            MediaFilter.ALL -> true
            MediaFilter.PHOTOS -> media.isImage
            MediaFilter.DOCUMENTS -> !media.isImage
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Media Gallery",
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "${allMedia.size} items",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Search bar and filter row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = searchText,
                onValueChange = { searchText = it },
                placeholder = { Text("Search by title or filename...") },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )

            // Filter chips
            for (filter in MediaFilter.entries) {
                val isSelected = selectedFilter == filter
                FilterChip(
                    selected = isSelected,
                    onClick = { selectedFilter = filter },
                    label = { Text(filter.label) }
                )
            }
        }

        // Count badges
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            val imageCount = allMedia.count { it.isImage }
            val docCount = allMedia.count { !it.isImage }
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MediaIconColor.copy(alpha = 0.15f)
            ) {
                Text(
                    text = "$imageCount photos",
                    fontSize = 12.sp,
                    color = MediaIconColor,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = SourcesIconColor.copy(alpha = 0.15f)
            ) {
                Text(
                    text = "$docCount documents",
                    fontSize = 12.sp,
                    color = SourcesIconColor,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
        }

        // Grid
        if (filteredMedia.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "\uD83D\uDCF7",
                        fontSize = 48.sp
                    )
                    Text(
                        text = if (allMedia.isEmpty()) "No Media Found" else "No Matching Media",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = if (allMedia.isEmpty())
                            "Import a GEDCOM file with media references to see them here."
                        else
                            "Try adjusting your search or filter.",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 180.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(filteredMedia) { media ->
                    MediaGridItem(
                        media = media,
                        ownerName = resolveOwnerName(media, viewModel),
                        onClick = { selectedMedia = media }
                    )
                }
            }
        }
    }

    // Media viewer dialog
    selectedMedia?.let { media ->
        MediaViewerDialog(
            media = media,
            allMedia = filteredMedia,
            ownerName = resolveOwnerName(media, viewModel),
            onDismiss = { selectedMedia = null },
            onNavigate = { selectedMedia = it },
            onNavigateToPerson = { xref ->
                selectedMedia = null
                viewModel.selectedPersonXref = xref
                viewModel.selectedSection = com.gedfix.models.SidebarSection.PEOPLE
            }
        )
    }
}

@Composable
private fun MediaGridItem(
    media: GedcomMedia,
    ownerName: String,
    onClick: () -> Unit
) {
    val thumbnail = remember(media.filePath) { loadThumbnail(media) }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Column {
            // Thumbnail area
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp)
                    .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                if (thumbnail != null) {
                    androidx.compose.foundation.Image(
                        bitmap = thumbnail,
                        contentDescription = media.displayTitle,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    // Placeholder icon
                    Text(
                        text = media.placeholderIcon,
                        fontSize = 40.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                }

                // Format badge in top-right corner
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp),
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.8f)
                ) {
                    Text(
                        text = media.formatBadge,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.inverseOnSurface,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            // Info below thumbnail
            Column(
                modifier = Modifier.padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = media.displayTitle,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (ownerName.isNotEmpty()) {
                    Text(
                        text = ownerName,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

private fun loadThumbnail(media: GedcomMedia): ImageBitmap? {
    if (!media.isImage) return null
    return try {
        val file = File(media.filePath)
        if (file.exists() && file.isFile) {
            file.inputStream().buffered().use { stream ->
                androidx.compose.ui.res.loadImageBitmap(stream)
            }
        } else null
    } catch (_: Exception) {
        null
    }
}

private fun resolveOwnerName(media: GedcomMedia, viewModel: AppViewModel): String {
    if (media.ownerXref.isEmpty()) return ""
    val person = viewModel.db.fetchPerson(media.ownerXref)
    return person?.displayName ?: ""
}
