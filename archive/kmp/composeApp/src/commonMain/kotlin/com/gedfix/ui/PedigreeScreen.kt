package com.gedfix.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gedfix.models.GedcomPerson
import com.gedfix.models.GedcomParser
import com.gedfix.ui.theme.*
import com.gedfix.viewmodel.AppViewModel

/**
 * Interactive pedigree chart using Canvas-based drawing.
 * Supports 3-5 generations with zoom controls.
 */
@Composable
fun PedigreeScreen(viewModel: AppViewModel) {
    var rootXref by remember { mutableStateOf(viewModel.selectedPersonXref ?: "") }
    var generations by remember { mutableStateOf(4) }
    var scale by remember { mutableStateOf(1.0f) }
    var offsetX by remember { mutableStateOf(0f) }
    var offsetY by remember { mutableStateOf(0f) }
    val history = remember { mutableStateListOf<String>() }

    val db = viewModel.db
    val rootPerson = if (rootXref.isNotEmpty()) db.fetchPerson(rootXref) else null

    // Update root when selection changes
    LaunchedEffect(viewModel.selectedPersonXref) {
        viewModel.selectedPersonXref?.let {
            if (it != rootXref) {
                rootXref = it
            }
        }
    }

    if (rootXref.isEmpty() || rootPerson == null) {
        // Empty state
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("\u2592", fontSize = 48.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f))
                Text("Select a Person", fontSize = 20.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    "Choose someone from the People list to view their pedigree chart",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }
        }
        return
    }

    // Build pedigree data structure
    data class PedigreeNode(
        val person: GedcomPerson?,
        val father: PedigreeNode?,
        val mother: PedigreeNode?
    )

    fun buildTree(xref: String?, depth: Int): PedigreeNode? {
        if (xref == null || xref.isEmpty() || depth >= generations) return null
        val person = db.fetchPerson(xref)
        if (person == null && depth > 0) return PedigreeNode(null, null, null)
        val (father, mother) = if (xref.isNotEmpty()) db.fetchParents(xref) else Pair(null, null)
        return PedigreeNode(
            person = person,
            father = buildTree(father?.xref, depth + 1),
            mother = buildTree(mother?.xref, depth + 1)
        )
    }

    val tree = remember(rootXref, generations) { buildTree(rootXref, 0) }

    Column(modifier = Modifier.fillMaxSize()) {
        // Toolbar
        Surface(
            modifier = Modifier.fillMaxWidth(),
            tonalElevation = 2.dp
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Back button
                TextButton(
                    onClick = {
                        history.removeLastOrNull()?.let { rootXref = it }
                    },
                    enabled = history.isNotEmpty()
                ) {
                    Text("\u2190 Back")
                }

                Text("|", color = MaterialTheme.colorScheme.outlineVariant)

                // Root person name
                Text(
                    text = rootPerson.displayName,
                    fontWeight = FontWeight.SemiBold,
                    color = when (rootPerson.sex) {
                        "M" -> MaleColor
                        "F" -> FemaleColor
                        else -> MaterialTheme.colorScheme.onSurface
                    }
                )

                Spacer(modifier = Modifier.weight(1f))

                // Generation picker
                Text("Generations:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                listOf(3, 4, 5).forEach { gen ->
                    FilterChip(
                        selected = generations == gen,
                        onClick = { generations = gen },
                        label = { Text("$gen") }
                    )
                }

                Text("|", color = MaterialTheme.colorScheme.outlineVariant)

                // Zoom controls
                TextButton(onClick = { scale = (scale - 0.1f).coerceAtLeast(0.4f) }) { Text("-") }
                Text("${(scale * 100).toInt()}%", fontSize = 12.sp)
                TextButton(onClick = { scale = (scale + 0.1f).coerceAtMost(2.0f) }) { Text("+") }
                TextButton(onClick = { scale = 1.0f; offsetX = 0f; offsetY = 0f }) { Text("\u21BA") }
            }
        }

        HorizontalDivider()

        // Generation labels
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Row(modifier = Modifier.padding(horizontal = 32.dp, vertical = 6.dp)) {
                for (gen in 0 until generations) {
                    Text(
                        text = generationLabel(gen),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // Canvas-based pedigree chart
        val textMeasurer = rememberTextMeasurer()

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale = (scale * zoom).coerceIn(0.4f, 2.0f)
                        offsetX += pan.x
                        offsetY += pan.y
                    }
                }
        ) {
            translate(left = offsetX, top = offsetY) {
                drawPedigreeTree(
                    tree = tree,
                    x = 40f * scale,
                    y = size.height / 2,
                    depth = 0,
                    maxDepth = generations,
                    scale = scale,
                    textMeasurer = textMeasurer
                )
            }
        }
    }
}

private fun DrawScope.drawPedigreeTree(
    tree: Any?, // PedigreeNode
    x: Float,
    y: Float,
    depth: Int,
    maxDepth: Int,
    scale: Float,
    textMeasurer: TextMeasurer
) {
    // Card dimensions scaled by generation
    val cardWidth = when (depth) {
        0 -> 220f
        1 -> 200f
        2 -> 180f
        3 -> 160f
        else -> 140f
    } * scale

    val cardHeight = when (depth) {
        0 -> 80f
        1 -> 72f
        2 -> 64f
        else -> 56f
    } * scale

    val fontSize = when (depth) {
        0 -> 14f
        1 -> 13f
        2 -> 12f
        else -> 11f
    } * scale

    // Draw card background
    val person = extractPerson(tree)
    val sexColor = when (person?.sex) {
        "M" -> MaleColor
        "F" -> FemaleColor
        else -> UnknownGenderColor
    }
    val bgColor = sexColor.copy(alpha = 0.08f)
    val borderColor = sexColor.copy(alpha = 0.35f)

    // Background
    drawRoundRect(
        color = bgColor,
        topLeft = Offset(x, y - cardHeight / 2),
        size = Size(cardWidth, cardHeight),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(10f * scale)
    )

    // Border
    drawRoundRect(
        color = borderColor,
        topLeft = Offset(x, y - cardHeight / 2),
        size = Size(cardWidth, cardHeight),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(10f * scale),
        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5f * scale)
    )

    // Name text
    val nameText = person?.displayName?.ifEmpty { "(Unknown)" } ?: "?"
    val textStyle = TextStyle(
        fontSize = fontSize.sp,
        fontWeight = if (depth == 0) FontWeight.Bold else FontWeight.Medium,
        color = Color(0xFF1C1B1F)
    )
    val measuredText = textMeasurer.measure(
        text = nameText,
        style = textStyle,
        maxLines = 1,
        constraints = androidx.compose.ui.unit.Constraints(maxWidth = (cardWidth - 20 * scale).toInt())
    )
    drawText(
        textLayoutResult = measuredText,
        topLeft = Offset(x + 10 * scale, y - cardHeight / 4)
    )

    // Date text for birth/death years
    if (person != null) {
        val dateText = buildDateRange(person)
        if (dateText.isNotEmpty()) {
            val dateStyle = TextStyle(
                fontSize = (fontSize - 2) .sp,
                color = Color(0xFF666666)
            )
            val measuredDate = textMeasurer.measure(
                text = dateText,
                style = dateStyle,
                maxLines = 1,
                constraints = androidx.compose.ui.unit.Constraints(maxWidth = (cardWidth - 20 * scale).toInt())
            )
            drawText(
                textLayoutResult = measuredDate,
                topLeft = Offset(x + 10 * scale, y + 4 * scale)
            )
        }
    }

    // Draw connector lines and recurse into parent subtrees
    if (depth < maxDepth - 1) {
        val gap = 24f * scale
        val verticalSpacing = calculateVerticalSpacing(depth, maxDepth, scale)

        // Horizontal connector from card
        val connectorStartX = x + cardWidth
        val connectorEndX = connectorStartX + gap
        drawLine(
            color = ConnectorColor,
            start = Offset(connectorStartX, y),
            end = Offset(connectorEndX, y),
            strokeWidth = 1f * scale
        )

        val fatherY = y - verticalSpacing / 2
        val motherY = y + verticalSpacing / 2

        // Vertical bracket
        drawLine(
            color = ConnectorColor,
            start = Offset(connectorEndX, fatherY),
            end = Offset(connectorEndX, motherY),
            strokeWidth = 1f * scale
        )

        // Horizontal connector to father
        drawLine(
            color = ConnectorColor,
            start = Offset(connectorEndX, fatherY),
            end = Offset(connectorEndX + gap / 2, fatherY),
            strokeWidth = 1f * scale
        )

        // Horizontal connector to mother
        drawLine(
            color = ConnectorColor,
            start = Offset(connectorEndX, motherY),
            end = Offset(connectorEndX + gap / 2, motherY),
            strokeWidth = 1f * scale
        )

        // Recurse
        val father = extractFather(tree)
        val mother = extractMother(tree)

        drawPedigreeTree(
            tree = father,
            x = connectorEndX + gap / 2,
            y = fatherY,
            depth = depth + 1,
            maxDepth = maxDepth,
            scale = scale,
            textMeasurer = textMeasurer
        )

        drawPedigreeTree(
            tree = mother,
            x = connectorEndX + gap / 2,
            y = motherY,
            depth = depth + 1,
            maxDepth = maxDepth,
            scale = scale,
            textMeasurer = textMeasurer
        )
    }
}

private fun calculateVerticalSpacing(depth: Int, maxDepth: Int, scale: Float): Float {
    // Each generation doubles the vertical space needed
    val remainingGens = maxDepth - depth - 1
    val baseSpacing = 70f * scale
    var spacing = baseSpacing
    for (i in 0 until remainingGens) {
        spacing *= 2
    }
    return spacing
}

// Helper functions to avoid type issues with recursive data class
private fun extractPerson(tree: Any?): GedcomPerson? {
    if (tree == null) return null
    // Use reflection-free approach: the tree is built as nested maps
    return try {
        val field = tree::class.java.getDeclaredField("person")
        field.isAccessible = true
        field.get(tree) as? GedcomPerson
    } catch (_: Exception) { null }
}

private fun extractFather(tree: Any?): Any? {
    if (tree == null) return null
    return try {
        val field = tree::class.java.getDeclaredField("father")
        field.isAccessible = true
        field.get(tree)
    } catch (_: Exception) { null }
}

private fun extractMother(tree: Any?): Any? {
    if (tree == null) return null
    return try {
        val field = tree::class.java.getDeclaredField("mother")
        field.isAccessible = true
        field.get(tree)
    } catch (_: Exception) { null }
}

private fun buildDateRange(person: GedcomPerson): String {
    // Simple placeholder - would need birth/death events
    return if (person.isLiving) "Living" else ""
}

private fun generationLabel(gen: Int): String = when (gen) {
    0 -> "Root"
    1 -> "Parents"
    2 -> "Grandparents"
    3 -> "Great-Grandparents"
    4 -> "2x Great-Grandparents"
    else -> "Gen $gen"
}
