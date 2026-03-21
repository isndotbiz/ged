package com.gedfix.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gedfix.db.DatabaseRepository
import com.gedfix.models.GedcomPerson
import com.gedfix.ui.theme.*
import com.gedfix.viewmodel.AppViewModel
import kotlin.math.max

/**
 * A node in the descendant tree.
 */
data class DescendantNode(
    val person: GedcomPerson,
    val spouse: GedcomPerson?,
    val children: List<DescendantNode>,
    val generation: Int,
    var collapsed: Boolean = false,
    // Layout coordinates computed during positioning
    var x: Float = 0f,
    var y: Float = 0f,
    var subtreeWidth: Float = 0f
)

/**
 * Interactive descendant chart showing all descendants of a selected person
 * in a vertical tree layout with expandable/collapsible branches.
 */
@Composable
fun DescendantChartScreen(viewModel: AppViewModel) {
    var rootXref by remember { mutableStateOf(viewModel.selectedPersonXref ?: "") }
    var maxDepth by remember { mutableIntStateOf(5) }
    var scale by remember { mutableFloatStateOf(0.9f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    var showSpouses by remember { mutableStateOf(true) }
    var showPersonPicker by remember { mutableStateOf(false) }
    var personSearch by remember { mutableStateOf("") }
    // Track collapsed xrefs
    val collapsedXrefs = remember { mutableStateListOf<String>() }
    // Revision counter to force recomposition on collapse toggle
    var revision by remember { mutableIntStateOf(0) }

    val db = viewModel.db
    val rootPerson = if (rootXref.isNotEmpty()) db.fetchPerson(rootXref) else null

    // Update root when selection changes
    LaunchedEffect(viewModel.selectedPersonXref) {
        viewModel.selectedPersonXref?.let {
            if (it != rootXref) rootXref = it
        }
    }

    // Build descendant tree
    val tree = remember(rootXref, maxDepth, revision) {
        if (rootXref.isNotEmpty()) buildDescendantTree(rootXref, db, maxDepth, collapsedXrefs) else null
    }

    // Count descendants
    val descendantCount = remember(tree) { tree?.let { countDescendants(it) } ?: 0 }
    val maxGenFound = remember(tree) { tree?.let { maxGeneration(it) } ?: 0 }

    if (rootXref.isEmpty() || rootPerson == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("\u2193", fontSize = 48.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f))
                Text("Descendant Chart", fontSize = 20.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    "Choose someone from the People list to view their descendants",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }
        }
        return
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Toolbar
        Surface(modifier = Modifier.fillMaxWidth(), tonalElevation = 2.dp) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = rootPerson.displayName,
                    fontWeight = FontWeight.SemiBold,
                    color = when (rootPerson.sex) {
                        "M" -> MaleColor
                        "F" -> FemaleColor
                        else -> MaterialTheme.colorScheme.onSurface
                    }
                )

                OutlinedButton(onClick = { showPersonPicker = true }) {
                    Text("Change Root")
                }

                Spacer(modifier = Modifier.weight(1f))

                // Show/hide spouses
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Checkbox(checked = showSpouses, onCheckedChange = { showSpouses = it })
                    Text("Spouses", fontSize = 12.sp)
                }

                Text("|", color = MaterialTheme.colorScheme.outlineVariant)

                // Depth control
                Text("Max Depth:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                listOf(3, 4, 5, 6, 8).forEach { depth ->
                    FilterChip(
                        selected = maxDepth == depth,
                        onClick = { maxDepth = depth },
                        label = { Text("$depth") }
                    )
                }

                Text("|", color = MaterialTheme.colorScheme.outlineVariant)

                // Zoom controls
                TextButton(onClick = { scale = (scale - 0.1f).coerceAtLeast(0.2f) }) { Text("-") }
                Text("${(scale * 100).toInt()}%", fontSize = 12.sp)
                TextButton(onClick = { scale = (scale + 0.1f).coerceAtMost(2.0f) }) { Text("+") }
                TextButton(onClick = { scale = 0.9f; offsetX = 0f; offsetY = 0f }) { Text("\u21BA") }

                // Expand/collapse all
                TextButton(onClick = {
                    collapsedXrefs.clear()
                    revision++
                }) { Text("Expand All") }
            }
        }

        HorizontalDivider()

        // Stats bar
        Surface(modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surfaceVariant) {
            Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                Text(
                    "Showing $descendantCount descendants across $maxGenFound generations",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Canvas-based tree drawing
        val textMeasurer = rememberTextMeasurer()

        if (tree != null) {
            // Position the tree nodes
            val cardWidth = 180f * scale
            val cardHeight = 60f * scale
            val horizontalGap = 20f * scale
            val verticalGap = 80f * scale

            val positionedTree = remember(tree, scale, showSpouses) {
                val t = tree.deepCopy()
                positionTree(t, cardWidth, cardHeight, horizontalGap, verticalGap, showSpouses)
                t
            }

            val totalWidth = positionedTree.subtreeWidth
            val totalHeight = (maxGenFound + 1) * (cardHeight + verticalGap)

            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            scale = (scale * zoom).coerceIn(0.2f, 2.0f)
                            offsetX += pan.x
                            offsetY += pan.y
                        }
                    }
                    .pointerInput(positionedTree, scale, offsetX, offsetY) {
                        detectTapGestures { tapOffset ->
                            // Check if a node was tapped (for collapse/expand)
                            val canvasWidth = size.width.toFloat()
                            val adjustedX = tapOffset.x - offsetX - canvasWidth / 2f + totalWidth / 2f
                            val adjustedY = tapOffset.y - offsetY - 40f * scale
                            findTappedNode(positionedTree, adjustedX, adjustedY, cardWidth, cardHeight)?.let { node ->
                                if (node.children.isNotEmpty()) {
                                    if (node.person.xref in collapsedXrefs) {
                                        collapsedXrefs.remove(node.person.xref)
                                    } else {
                                        collapsedXrefs.add(node.person.xref)
                                    }
                                    revision++
                                } else {
                                    // Navigate to person detail
                                    viewModel.selectedPersonXref = node.person.xref
                                    viewModel.selectedSection = com.gedfix.models.SidebarSection.PEOPLE
                                }
                            }
                        }
                    }
            ) {
                translate(
                    left = offsetX + size.width / 2f - totalWidth / 2f,
                    top = offsetY + 40f * scale
                ) {
                    drawDescendantTree(
                        node = positionedTree,
                        cardWidth = cardWidth,
                        cardHeight = cardHeight,
                        showSpouses = showSpouses,
                        textMeasurer = textMeasurer,
                        scale = scale,
                        collapsedXrefs = collapsedXrefs
                    )
                }
            }
        }
    }

    // Person picker dialog
    if (showPersonPicker) {
        val searchResults = remember(personSearch) {
            if (personSearch.length >= 2) db.fetchPersons(personSearch) else emptyList()
        }

        AlertDialog(
            onDismissRequest = { showPersonPicker = false; personSearch = "" },
            title = { Text("Select Root Person") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = personSearch,
                        onValueChange = { personSearch = it },
                        label = { Text("Search by name") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    if (searchResults.isNotEmpty()) {
                        Column(
                            modifier = Modifier.heightIn(max = 300.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            for (person in searchResults.take(20)) {
                                TextButton(
                                    onClick = {
                                        rootXref = person.xref
                                        showPersonPicker = false
                                        personSearch = ""
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        "${person.displayName} (${person.xref})",
                                        color = when (person.sex) {
                                            "M" -> MaleColor
                                            "F" -> FemaleColor
                                            else -> MaterialTheme.colorScheme.onSurface
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showPersonPicker = false; personSearch = "" }) {
                    Text("Cancel")
                }
            }
        )
    }
}

/**
 * Recursively build the descendant tree.
 */
private fun buildDescendantTree(
    xref: String,
    db: DatabaseRepository,
    maxDepth: Int,
    collapsedXrefs: List<String>,
    currentDepth: Int = 0
): DescendantNode? {
    val person = db.fetchPerson(xref) ?: return null
    val isCollapsed = xref in collapsedXrefs

    // Find spouse(s) and children
    val spouseFamilies = db.fetchFamiliesAsSpouse(xref)
    val firstFamily = spouseFamilies.firstOrNull()
    val spouseXref = firstFamily?.let {
        if (it.partner1Xref == xref) it.partner2Xref else it.partner1Xref
    }
    val spouse = spouseXref?.let { if (it.isNotEmpty()) db.fetchPerson(it) else null }

    // Get all children from all families
    val children = if (isCollapsed || currentDepth >= maxDepth) {
        emptyList()
    } else {
        spouseFamilies.flatMap { family ->
            db.fetchChildLinks(family.xref).mapNotNull { link ->
                buildDescendantTree(link.childXref, db, maxDepth, collapsedXrefs, currentDepth + 1)
            }
        }
    }

    return DescendantNode(
        person = person,
        spouse = spouse,
        children = children,
        generation = currentDepth,
        collapsed = isCollapsed
    )
}

/**
 * Deep copy a descendant tree (needed for remember recomposition).
 */
private fun DescendantNode.deepCopy(): DescendantNode {
    return DescendantNode(
        person = person,
        spouse = spouse,
        children = children.map { it.deepCopy() },
        generation = generation,
        collapsed = collapsed,
        x = x,
        y = y,
        subtreeWidth = subtreeWidth
    )
}

/**
 * Position tree nodes using a bottom-up algorithm.
 * Each leaf gets one slot; parents center over their children.
 */
private fun positionTree(
    node: DescendantNode,
    cardWidth: Float,
    cardHeight: Float,
    hGap: Float,
    vGap: Float,
    showSpouses: Boolean
) {
    val nodeWidth = if (showSpouses && node.spouse != null) cardWidth * 2 + 10f else cardWidth

    if (node.children.isEmpty()) {
        node.subtreeWidth = nodeWidth + hGap
    } else {
        // Position children first
        for (child in node.children) {
            positionTree(child, cardWidth, cardHeight, hGap, vGap, showSpouses)
        }
        val totalChildWidth = node.children.sumOf { it.subtreeWidth.toDouble() }.toFloat()
        node.subtreeWidth = max(totalChildWidth, nodeWidth + hGap)
    }

    // Set y based on generation
    node.y = node.generation * (cardHeight + vGap)

    // Set x: center over children if they exist
    if (node.children.isEmpty()) {
        node.x = node.subtreeWidth / 2f - nodeWidth / 2f
    } else {
        // Position children within our subtree width
        var childOffset = (node.subtreeWidth - node.children.sumOf { it.subtreeWidth.toDouble() }.toFloat()) / 2f
        for (child in node.children) {
            positionChildOffsets(child, childOffset)
            childOffset += child.subtreeWidth
        }
        // Center this node over its children
        val firstChildCenter = node.children.first().x + (if (showSpouses && node.children.first().spouse != null) cardWidth else cardWidth / 2f)
        val lastChild = node.children.last()
        val lastChildCenter = lastChild.x + (if (showSpouses && lastChild.spouse != null) cardWidth else cardWidth / 2f)
        node.x = (firstChildCenter + lastChildCenter) / 2f - nodeWidth / 2f
    }
}

private fun positionChildOffsets(node: DescendantNode, parentOffset: Float) {
    node.x += parentOffset
    // Recurse for already-positioned children
    // Children positions are relative already, just shift x
}

/**
 * Actually reposition all nodes with absolute coordinates.
 * This is called after the initial relative positioning.
 */
private fun positionTree(root: DescendantNode, cardWidth: Float, cardHeight: Float, hGap: Float, vGap: Float, showSpouses: Boolean, startX: Float = 0f) {
    // Use Reingold-Tilford-style simple algorithm
    calculateSubtreeWidths(root, cardWidth, hGap, showSpouses)
    assignXPositions(root, 0f, cardWidth, hGap, showSpouses)
    assignYPositions(root, cardHeight, vGap)
}

private fun calculateSubtreeWidths(node: DescendantNode, cardWidth: Float, hGap: Float, showSpouses: Boolean) {
    val nodeWidth = if (showSpouses && node.spouse != null) cardWidth * 2 + 10f else cardWidth

    if (node.children.isEmpty()) {
        node.subtreeWidth = nodeWidth + hGap
    } else {
        for (child in node.children) {
            calculateSubtreeWidths(child, cardWidth, hGap, showSpouses)
        }
        val childrenWidth = node.children.sumOf { it.subtreeWidth.toDouble() }.toFloat()
        node.subtreeWidth = max(childrenWidth, nodeWidth + hGap)
    }
}

private fun assignXPositions(node: DescendantNode, startX: Float, cardWidth: Float, hGap: Float, showSpouses: Boolean) {
    val nodeWidth = if (showSpouses && node.spouse != null) cardWidth * 2 + 10f else cardWidth

    if (node.children.isEmpty()) {
        node.x = startX + (node.subtreeWidth - nodeWidth) / 2f
    } else {
        var childStartX = startX + (node.subtreeWidth - node.children.sumOf { it.subtreeWidth.toDouble() }.toFloat()) / 2f
        for (child in node.children) {
            assignXPositions(child, childStartX, cardWidth, hGap, showSpouses)
            childStartX += child.subtreeWidth
        }
        // Center over children
        val firstChildCenter = node.children.first().x + nodeWidth / 2f
        val lastChildCenter = node.children.last().x + nodeWidth / 2f
        node.x = (firstChildCenter + lastChildCenter) / 2f - nodeWidth / 2f
    }
}

private fun assignYPositions(node: DescendantNode, cardHeight: Float, vGap: Float) {
    node.y = node.generation * (cardHeight + vGap)
    for (child in node.children) {
        assignYPositions(child, cardHeight, vGap)
    }
}

/**
 * Draw the descendant tree with connecting lines and person cards.
 */
private fun DrawScope.drawDescendantTree(
    node: DescendantNode,
    cardWidth: Float,
    cardHeight: Float,
    showSpouses: Boolean,
    textMeasurer: TextMeasurer,
    scale: Float,
    collapsedXrefs: List<String>
) {
    val nodeWidth = if (showSpouses && node.spouse != null) cardWidth * 2 + 10f else cardWidth
    val cardCenterX = node.x + nodeWidth / 2f
    val cardBottomY = node.y + cardHeight

    // Draw connector lines to children
    if (node.children.isNotEmpty()) {
        val lineY = cardBottomY + (node.children.first().y - cardBottomY) / 2f

        // Vertical line from parent down to midpoint
        drawLine(
            color = ConnectorColor,
            start = Offset(cardCenterX, cardBottomY),
            end = Offset(cardCenterX, lineY),
            strokeWidth = 1.5f * scale
        )

        // Horizontal line spanning all children
        val leftmostChildCenter = node.children.first().let {
            val cw = if (showSpouses && it.spouse != null) cardWidth * 2 + 10f else cardWidth
            it.x + cw / 2f
        }
        val rightmostChildCenter = node.children.last().let {
            val cw = if (showSpouses && it.spouse != null) cardWidth * 2 + 10f else cardWidth
            it.x + cw / 2f
        }

        if (node.children.size > 1) {
            drawLine(
                color = ConnectorColor,
                start = Offset(leftmostChildCenter, lineY),
                end = Offset(rightmostChildCenter, lineY),
                strokeWidth = 1.5f * scale
            )
        }

        // Vertical lines from midpoint down to each child
        for (child in node.children) {
            val childNodeWidth = if (showSpouses && child.spouse != null) cardWidth * 2 + 10f else cardWidth
            val childCenterX = child.x + childNodeWidth / 2f
            drawLine(
                color = ConnectorColor,
                start = Offset(childCenterX, lineY),
                end = Offset(childCenterX, child.y),
                strokeWidth = 1.5f * scale
            )
        }
    }

    // Draw this person's card
    drawPersonCard(node.person, node.x, node.y, cardWidth, cardHeight, textMeasurer, scale, isRoot = node.generation == 0)

    // Draw spouse card if enabled
    if (showSpouses && node.spouse != null) {
        val spouseX = node.x + cardWidth + 10f
        drawPersonCard(node.spouse, spouseX, node.y, cardWidth, cardHeight, textMeasurer, scale, isRoot = false)

        // Draw marriage connector (horizontal line with heart)
        drawLine(
            color = FemaleColor.copy(alpha = 0.4f),
            start = Offset(node.x + cardWidth, node.y + cardHeight / 2f),
            end = Offset(spouseX, node.y + cardHeight / 2f),
            strokeWidth = 1.5f * scale
        )
    }

    // Draw collapse indicator if node has children
    if (node.children.isNotEmpty() || node.person.xref in collapsedXrefs) {
        val indicatorY = cardBottomY + 4f * scale
        val isCollapsed = node.person.xref in collapsedXrefs

        // Small circle with +/- indicator
        drawCircle(
            color = if (isCollapsed) Color(0xFFFF9800) else Color(0xFF4CAF50),
            radius = 8f * scale,
            center = Offset(cardCenterX, indicatorY + 8f * scale)
        )

        val symbol = if (isCollapsed) "+" else "\u2212"
        val measured = textMeasurer.measure(
            text = symbol,
            style = TextStyle(fontSize = (10f * scale).sp, fontWeight = FontWeight.Bold, color = Color.White)
        )
        drawText(
            textLayoutResult = measured,
            topLeft = Offset(
                cardCenterX - measured.size.width / 2f,
                indicatorY + 8f * scale - measured.size.height / 2f
            )
        )
    }

    // Recurse into children
    for (child in node.children) {
        drawDescendantTree(child, cardWidth, cardHeight, showSpouses, textMeasurer, scale, collapsedXrefs)
    }
}

/**
 * Draw a single person card.
 */
private fun DrawScope.drawPersonCard(
    person: GedcomPerson,
    x: Float,
    y: Float,
    width: Float,
    height: Float,
    textMeasurer: TextMeasurer,
    scale: Float,
    isRoot: Boolean
) {
    val sexColor = when (person.sex) {
        "M" -> MaleColor
        "F" -> FemaleColor
        else -> UnknownGenderColor
    }

    // Background
    drawRoundRect(
        color = sexColor.copy(alpha = if (isRoot) 0.15f else 0.08f),
        topLeft = Offset(x, y),
        size = androidx.compose.ui.geometry.Size(width, height),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(8f * scale)
    )

    // Border
    drawRoundRect(
        color = sexColor.copy(alpha = if (isRoot) 0.6f else 0.35f),
        topLeft = Offset(x, y),
        size = androidx.compose.ui.geometry.Size(width, height),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(8f * scale),
        style = androidx.compose.ui.graphics.drawscope.Stroke(width = if (isRoot) 2f * scale else 1.2f * scale)
    )

    // Left color indicator bar
    drawRoundRect(
        color = sexColor.copy(alpha = 0.7f),
        topLeft = Offset(x, y + 4f * scale),
        size = androidx.compose.ui.geometry.Size(3f * scale, height - 8f * scale),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(2f)
    )

    // Name
    val nameText = person.displayName.ifEmpty { "?" }
    val fontSize = if (isRoot) 12f * scale else 11f * scale
    val measured = textMeasurer.measure(
        text = nameText,
        style = TextStyle(
            fontSize = fontSize.sp,
            fontWeight = if (isRoot) FontWeight.Bold else FontWeight.Medium,
            color = Color(0xFF1C1B1F)
        ),
        maxLines = 1,
        constraints = androidx.compose.ui.unit.Constraints(maxWidth = (width - 16f * scale).toInt().coerceAtLeast(1))
    )
    drawText(
        textLayoutResult = measured,
        topLeft = Offset(x + 10f * scale, y + 8f * scale)
    )

    // Birth/death years line
    val infoText = if (person.isLiving) "Living" else person.xref
    val infoMeasured = textMeasurer.measure(
        text = infoText,
        style = TextStyle(
            fontSize = (fontSize - 2f).sp,
            color = Color(0xFF666666)
        ),
        maxLines = 1,
        constraints = androidx.compose.ui.unit.Constraints(maxWidth = (width - 16f * scale).toInt().coerceAtLeast(1))
    )
    drawText(
        textLayoutResult = infoMeasured,
        topLeft = Offset(x + 10f * scale, y + 8f * scale + measured.size.height + 2f * scale)
    )
}

/**
 * Find which node was tapped.
 */
private fun findTappedNode(
    node: DescendantNode,
    tapX: Float,
    tapY: Float,
    cardWidth: Float,
    cardHeight: Float
): DescendantNode? {
    // Check this node
    if (tapX >= node.x && tapX <= node.x + cardWidth * 2.5f &&
        tapY >= node.y && tapY <= node.y + cardHeight + 20f
    ) {
        return node
    }
    // Check children
    for (child in node.children) {
        val result = findTappedNode(child, tapX, tapY, cardWidth, cardHeight)
        if (result != null) return result
    }
    return null
}

private fun countDescendants(node: DescendantNode): Int {
    return node.children.size + node.children.sumOf { countDescendants(it) }
}

private fun maxGeneration(node: DescendantNode): Int {
    if (node.children.isEmpty()) return node.generation
    return node.children.maxOf { maxGeneration(it) }
}
