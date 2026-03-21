package com.gedfix.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.gedfix.models.GedcomMedia
import com.gedfix.ui.theme.MediaIconColor
import java.awt.Desktop
import java.io.File

/**
 * Full-screen media viewer dialog with zoom, navigation, and metadata.
 */
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun MediaViewerDialog(
    media: GedcomMedia,
    allMedia: List<GedcomMedia>,
    ownerName: String,
    onDismiss: () -> Unit,
    onNavigate: (GedcomMedia) -> Unit,
    onNavigateToPerson: (String) -> Unit
) {
    val currentIndex = allMedia.indexOf(media)
    val hasPrev = currentIndex > 0
    val hasNext = currentIndex < allMedia.size - 1

    var zoomLevel by remember(media.id) { mutableStateOf(1f) }
    val image = remember(media.filePath) { loadFullImage(media) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // Top bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Title and format
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = media.displayTitle,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MediaIconColor.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = media.formatBadge,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = MediaIconColor,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }

                    // Close button
                    TextButton(onClick = onDismiss) {
                        Text("\u2715 Close")
                    }
                }

                HorizontalDivider()

                // Main content area
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f)
                ) {
                    // Image area
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .onPointerEvent(PointerEventType.Scroll) { event ->
                                val scrollDelta = event.changes.firstOrNull()?.scrollDelta?.y ?: 0f
                                zoomLevel = (zoomLevel - scrollDelta * 0.1f).coerceIn(0.5f, 5f)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        if (image != null) {
                            Image(
                                bitmap = image,
                                contentDescription = media.displayTitle,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .graphicsLayer(
                                        scaleX = zoomLevel,
                                        scaleY = zoomLevel
                                    ),
                                contentScale = ContentScale.Fit
                            )
                        } else {
                            // Placeholder for non-image or missing files
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text(
                                    text = media.placeholderIcon,
                                    fontSize = 72.sp
                                )
                                Text(
                                    text = if (media.isImage) "Image not found at path" else "Preview not available",
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = media.filePath,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                )
                            }
                        }

                        // Navigation arrows
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .align(Alignment.CenterStart)
                                .padding(horizontal = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            if (hasPrev) {
                                FilledTonalButton(
                                    onClick = { onNavigate(allMedia[currentIndex - 1]) }
                                ) {
                                    Text("\u2190")
                                }
                            } else {
                                Spacer(Modifier.width(1.dp))
                            }
                            if (hasNext) {
                                FilledTonalButton(
                                    onClick = { onNavigate(allMedia[currentIndex + 1]) }
                                ) {
                                    Text("\u2192")
                                }
                            }
                        }

                        // Zoom controls
                        Row(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            FilledTonalButton(
                                onClick = { zoomLevel = (zoomLevel - 0.25f).coerceIn(0.5f, 5f) },
                                modifier = Modifier.size(36.dp),
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Text("-", fontSize = 16.sp)
                            }
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant
                            ) {
                                Text(
                                    text = "${(zoomLevel * 100).toInt()}%",
                                    fontSize = 12.sp,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)
                                )
                            }
                            FilledTonalButton(
                                onClick = { zoomLevel = (zoomLevel + 0.25f).coerceIn(0.5f, 5f) },
                                modifier = Modifier.size(36.dp),
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Text("+", fontSize = 16.sp)
                            }
                        }
                    }

                    // Side panel with metadata
                    Surface(
                        modifier = Modifier
                            .width(280.dp)
                            .fillMaxHeight(),
                        color = MaterialTheme.colorScheme.surface
                    ) {
                        Column(
                            modifier = Modifier
                                .verticalScroll(rememberScrollState())
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Text(
                                "Details",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold
                            )

                            // Title
                            if (media.title.isNotEmpty()) {
                                MetadataRow("Title", media.title)
                            }

                            // File path
                            MetadataRow("File", media.filePath)

                            // Format
                            if (media.format.isNotEmpty()) {
                                MetadataRow("Format", media.format.uppercase())
                            }

                            // Xref
                            if (media.xref.isNotEmpty()) {
                                MetadataRow("GEDCOM Ref", media.xref)
                            }

                            // Description
                            if (media.description.isNotEmpty()) {
                                MetadataRow("Description", media.description)
                            }

                            HorizontalDivider()

                            // Owner
                            if (ownerName.isNotEmpty()) {
                                Text(
                                    "Attached To",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                TextButton(
                                    onClick = { onNavigateToPerson(media.ownerXref) }
                                ) {
                                    Text(
                                        "\u263A $ownerName",
                                        fontSize = 14.sp
                                    )
                                }
                            }

                            HorizontalDivider()

                            // Open in Finder button
                            OutlinedButton(
                                onClick = {
                                    try {
                                        val file = File(media.filePath)
                                        if (file.exists()) {
                                            Desktop.getDesktop().open(file.parentFile ?: file)
                                        }
                                    } catch (_: Exception) { }
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Open in Finder")
                            }

                            // Position indicator
                            if (allMedia.size > 1) {
                                Text(
                                    "${currentIndex + 1} of ${allMedia.size}",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MetadataRow(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

private fun loadFullImage(media: GedcomMedia): ImageBitmap? {
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
