package com.gedfix.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
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
import kotlin.math.*

/**
 * Color mode options for the fan chart segments.
 */
enum class FanColorMode(val label: String) {
    SEX("By Sex"),
    SURNAME("By Surname"),
    COMPLETENESS("By Completeness")
}

/**
 * Interactive fan chart showing ancestors radiating outward in concentric arcs.
 * Center: root person. Each ring doubles the number of segments.
 * Uses Ahnentafel numbering: 1=root, 2=father, 3=mother, 4=paternal GF, etc.
 */
@Composable
fun FanChartScreen(viewModel: AppViewModel) {
    var rootXref by remember { mutableStateOf(viewModel.selectedPersonXref ?: "") }
    var generations by remember { mutableIntStateOf(4) }
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    var colorMode by remember { mutableStateOf(FanColorMode.SEX) }
    var showPersonPicker by remember { mutableStateOf(false) }
    var personSearch by remember { mutableStateOf("") }

    val db = viewModel.db
    val rootPerson = if (rootXref.isNotEmpty()) db.fetchPerson(rootXref) else null

    // Update root when selection changes
    LaunchedEffect(viewModel.selectedPersonXref) {
        viewModel.selectedPersonXref?.let {
            if (it != rootXref) rootXref = it
        }
    }

    // Build ancestor map using Ahnentafel numbering
    val ancestors = remember(rootXref, generations) {
        if (rootXref.isNotEmpty()) buildAncestorMap(rootXref, generations, db) else emptyMap()
    }

    // Build surname color map for surname mode
    val surnameColors = remember(ancestors) {
        val surnames = ancestors.values.filterNotNull().map { it.surname }.filter { it.isNotEmpty() }.distinct()
        val palette = listOf(
            Color(0xFF2196F3), Color(0xFFE91E63), Color(0xFF4CAF50), Color(0xFFFF9800),
            Color(0xFF9C27B0), Color(0xFF00BCD4), Color(0xFF795548), Color(0xFF607D8B),
            Color(0xFFFF5722), Color(0xFF3F51B5), Color(0xFF009688), Color(0xFFCDDC39),
            Color(0xFFFFC107), Color(0xFF8BC34A), Color(0xFF673AB7), Color(0xFFf44336)
        )
        surnames.mapIndexed { index, surname -> surname to palette[index % palette.size] }.toMap()
    }

    // Count how many ancestors are filled vs total possible
    val totalSlots = (1..generations).sumOf { 1 shl it }
    val filledSlots = ancestors.count { (key, value) -> key > 1 && value != null }

    if (rootXref.isEmpty() || rootPerson == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("\u25D4", fontSize = 48.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f))
                Text("Fan Chart", fontSize = 20.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    "Choose someone from the People list to view their ancestor fan chart",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }
        }
        return
    }

    // Track clicked segment for re-centering
    var clickedAhnentafel by remember { mutableIntStateOf(-1) }

    // Handle click on segment to re-center
    LaunchedEffect(clickedAhnentafel) {
        if (clickedAhnentafel > 1) {
            val person = ancestors[clickedAhnentafel]
            if (person != null) {
                rootXref = person.xref
                clickedAhnentafel = -1
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Toolbar
        Surface(modifier = Modifier.fillMaxWidth(), tonalElevation = 2.dp) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
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

                // Person picker button
                OutlinedButton(onClick = { showPersonPicker = true }) {
                    Text("Change Root")
                }

                Spacer(modifier = Modifier.weight(1f))

                // Color mode selector
                Text("Color:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                FanColorMode.entries.forEach { mode ->
                    FilterChip(
                        selected = colorMode == mode,
                        onClick = { colorMode = mode },
                        label = { Text(mode.label, fontSize = 11.sp) }
                    )
                }

                Text("|", color = MaterialTheme.colorScheme.outlineVariant)

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
                TextButton(onClick = { scale = (scale - 0.1f).coerceAtLeast(0.3f) }) { Text("-") }
                Text("${(scale * 100).toInt()}%", fontSize = 12.sp)
                TextButton(onClick = { scale = (scale + 0.1f).coerceAtMost(2.5f) }) { Text("+") }
                TextButton(onClick = { scale = 1.0f; offsetX = 0f; offsetY = 0f }) { Text("\u21BA") }
            }
        }

        HorizontalDivider()

        // Stats bar
        Surface(modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surfaceVariant) {
            Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                Text(
                    "$filledSlots of $totalSlots ancestors found (${if (totalSlots > 0) (filledSlots * 100 / totalSlots) else 0}% complete)",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Canvas fan chart
        val textMeasurer = rememberTextMeasurer()

        Box(modifier = Modifier.fillMaxSize()) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            scale = (scale * zoom).coerceIn(0.3f, 2.5f)
                            offsetX += pan.x
                            offsetY += pan.y
                        }
                    }
                    .pointerInput(generations, scale, offsetX, offsetY) {
                        detectTapGestures { tapOffset ->
                            // Convert tap to chart coordinates
                            val cx = size.width / 2f + offsetX
                            val cy = size.height / 2f + offsetY
                            val dx = tapOffset.x - cx
                            val dy = tapOffset.y - cy
                            val dist = sqrt(dx * dx + dy * dy)
                            val maxRadius = minOf(size.width, size.height) / 2f * 0.88f * scale
                            val centerRadius = maxRadius * 0.12f

                            if (dist < centerRadius) return@detectTapGestures // center circle, ignore

                            // Determine which ring/generation was tapped
                            val ringWidth = (maxRadius - centerRadius) / generations
                            val ring = ((dist - centerRadius) / ringWidth).toInt()
                            if (ring < 0 || ring >= generations) return@detectTapGestures

                            val gen = ring + 1
                            val segmentCount = 1 shl gen // 2^gen
                            // Angle from top (0 degrees = straight up)
                            var angle = atan2(dx.toDouble(), -dy.toDouble()) // radians from north
                            if (angle < 0) angle += 2 * PI
                            val segmentAngle = 2 * PI / segmentCount
                            val segmentIndex = (angle / segmentAngle).toInt().coerceIn(0, segmentCount - 1)

                            // Convert to Ahnentafel number
                            val ahnBase = 1 shl gen // 2^gen
                            val ahnNum = ahnBase + segmentIndex
                            clickedAhnentafel = ahnNum
                        }
                    }
            ) {
                translate(left = offsetX, top = offsetY) {
                    drawFanChart(
                        ancestors = ancestors,
                        generations = generations,
                        scale = scale,
                        colorMode = colorMode,
                        surnameColors = surnameColors,
                        textMeasurer = textMeasurer,
                        rootPerson = rootPerson
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
 * Build ancestor map using Ahnentafel numbering.
 * 1 = root, 2 = father, 3 = mother, 4 = paternal GF, 5 = paternal GM, etc.
 */
private fun buildAncestorMap(
    rootXref: String,
    maxGen: Int,
    db: DatabaseRepository
): Map<Int, GedcomPerson?> {
    val map = mutableMapOf<Int, GedcomPerson?>()

    fun fill(xref: String, ahnNum: Int, depth: Int) {
        val person = db.fetchPerson(xref)
        map[ahnNum] = person
        if (person != null && depth < maxGen) {
            val (father, mother) = db.fetchParents(xref)
            if (father != null) fill(father.xref, ahnNum * 2, depth + 1)
            if (mother != null) fill(mother.xref, ahnNum * 2 + 1, depth + 1)
        }
    }

    val root = db.fetchPerson(rootXref) ?: return map
    map[1] = root
    val (father, mother) = db.fetchParents(rootXref)
    if (father != null) fill(father.xref, 2, 1)
    if (mother != null) fill(mother.xref, 3, 1)
    return map
}

/**
 * Draw the complete fan chart on canvas.
 */
private fun DrawScope.drawFanChart(
    ancestors: Map<Int, GedcomPerson?>,
    generations: Int,
    scale: Float,
    colorMode: FanColorMode,
    surnameColors: Map<String, Color>,
    textMeasurer: TextMeasurer,
    rootPerson: GedcomPerson
) {
    val cx = size.width / 2f
    val cy = size.height / 2f
    val maxRadius = minOf(cx, cy) * 0.88f * scale
    val centerRadius = maxRadius * 0.12f
    val ringWidth = (maxRadius - centerRadius) / generations

    // Draw center circle for root person
    drawCenterCircle(rootPerson, cx, cy, centerRadius, textMeasurer)

    // Draw rings from innermost (gen 1 = parents) to outermost
    for (gen in 1..generations) {
        val innerR = centerRadius + ringWidth * (gen - 1)
        val outerR = centerRadius + ringWidth * gen
        val segmentCount = 1 shl gen // 2^gen
        val sweepAngle = 360f / segmentCount

        for (i in 0 until segmentCount) {
            val startAngle = -90f + i * sweepAngle // start from top
            val ahnNum = (1 shl gen) + i
            val person = ancestors[ahnNum]

            drawFanSegment(
                person = person,
                innerRadius = innerR,
                outerRadius = outerR,
                startAngle = startAngle,
                sweepAngle = sweepAngle,
                centerX = cx,
                centerY = cy,
                colorMode = colorMode,
                surnameColors = surnameColors,
                textMeasurer = textMeasurer,
                generation = gen,
                totalGenerations = generations
            )
        }
    }
}

/**
 * Draw the center circle for the root person.
 */
private fun DrawScope.drawCenterCircle(
    person: GedcomPerson,
    cx: Float,
    cy: Float,
    radius: Float,
    textMeasurer: TextMeasurer
) {
    val sexColor = when (person.sex) {
        "M" -> MaleColor
        "F" -> FemaleColor
        else -> UnknownGenderColor
    }

    // Filled circle background
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(sexColor.copy(alpha = 0.25f), sexColor.copy(alpha = 0.08f)),
            center = Offset(cx, cy),
            radius = radius
        ),
        radius = radius,
        center = Offset(cx, cy)
    )

    // Border
    drawCircle(
        color = sexColor.copy(alpha = 0.6f),
        radius = radius,
        center = Offset(cx, cy),
        style = Stroke(width = 2.5f)
    )

    // Name text
    val nameText = person.displayName.ifEmpty { "?" }
    val fontSize = (radius * 0.22f).coerceIn(8f, 16f)
    val measured = textMeasurer.measure(
        text = nameText,
        style = TextStyle(
            fontSize = fontSize.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF1C1B1F)
        ),
        maxLines = 2,
        constraints = androidx.compose.ui.unit.Constraints(maxWidth = (radius * 1.6f).toInt())
    )
    drawText(
        textLayoutResult = measured,
        topLeft = Offset(cx - measured.size.width / 2f, cy - measured.size.height / 2f)
    )
}

/**
 * Draw a single fan segment (arc) for one ancestor.
 */
private fun DrawScope.drawFanSegment(
    person: GedcomPerson?,
    innerRadius: Float,
    outerRadius: Float,
    startAngle: Float,
    sweepAngle: Float,
    centerX: Float,
    centerY: Float,
    colorMode: FanColorMode,
    surnameColors: Map<String, Color>,
    textMeasurer: TextMeasurer,
    generation: Int,
    totalGenerations: Int
) {
    val midRadius = (innerRadius + outerRadius) / 2f
    val midAngle = startAngle + sweepAngle / 2f
    val midAngleRad = Math.toRadians(midAngle.toDouble())

    if (person == null) {
        // Empty segment: dashed outline
        val path = createArcPath(centerX, centerY, innerRadius, outerRadius, startAngle, sweepAngle)
        drawPath(
            path = path,
            color = Color(0x30808080),
            style = Stroke(
                width = 1f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 4f))
            )
        )
        return
    }

    // Determine segment color based on mode
    val baseColor = when (colorMode) {
        FanColorMode.SEX -> when (person.sex) {
            "M" -> MaleColor
            "F" -> FemaleColor
            else -> UnknownGenderColor
        }
        FanColorMode.SURNAME -> surnameColors[person.surname] ?: UnknownGenderColor
        FanColorMode.COMPLETENESS -> {
            if (person.sourceCount > 0) Color(0xFF4CAF50) else Color(0xFFFF9800)
        }
    }

    // Gradient from inner to outer (lighter outward)
    val gradientStart = baseColor.copy(alpha = 0.35f)
    val gradientEnd = baseColor.copy(alpha = 0.15f)

    // Draw filled arc segment
    val path = createArcPath(centerX, centerY, innerRadius, outerRadius, startAngle, sweepAngle)

    // Gradient fill using radial gradient approximation - use solid color with alpha
    val fillAlpha = 0.30f - (generation * 0.03f).coerceAtMost(0.15f)
    drawPath(
        path = path,
        color = baseColor.copy(alpha = fillAlpha.coerceAtLeast(0.10f))
    )

    // Border
    drawPath(
        path = path,
        color = baseColor.copy(alpha = 0.5f),
        style = Stroke(width = 1.2f)
    )

    // Draw separator lines between segments for clarity
    val innerStartX = centerX + innerRadius * cos(Math.toRadians(startAngle.toDouble())).toFloat()
    val innerStartY = centerY + innerRadius * sin(Math.toRadians(startAngle.toDouble())).toFloat()
    val outerStartX = centerX + outerRadius * cos(Math.toRadians(startAngle.toDouble())).toFloat()
    val outerStartY = centerY + outerRadius * sin(Math.toRadians(startAngle.toDouble())).toFloat()
    drawLine(
        color = Color(0x40FFFFFF),
        start = Offset(innerStartX, innerStartY),
        end = Offset(outerStartX, outerStartY),
        strokeWidth = 0.5f
    )

    // Draw person name text in the segment
    val availableWidth = (outerRadius - innerRadius) * 0.85f
    val arcLength = midRadius * Math.toRadians(sweepAngle.toDouble()).toFloat()
    val maxTextWidth = minOf(availableWidth, arcLength * 0.9f)

    if (maxTextWidth > 20f) {
        // Calculate font size based on available space
        val fontSize = when {
            generation <= 2 -> 11f
            generation == 3 -> 9f
            generation == 4 -> 7.5f
            else -> 6.5f
        }

        // Determine what text to show based on space
        val displayText = if (arcLength > 60f) {
            person.displayName.ifEmpty { "?" }
        } else if (arcLength > 30f) {
            // Just initials + surname
            val initials = person.givenName.take(1)
            "$initials. ${person.surname}"
        } else {
            person.initials
        }

        val measured = textMeasurer.measure(
            text = displayText,
            style = TextStyle(
                fontSize = fontSize.sp,
                fontWeight = if (generation <= 2) FontWeight.Medium else FontWeight.Normal,
                color = Color(0xFF1C1B1F)
            ),
            maxLines = 1,
            constraints = androidx.compose.ui.unit.Constraints(maxWidth = maxTextWidth.toInt().coerceAtLeast(1))
        )

        // Position text at the midpoint of the segment
        val textX = centerX + midRadius * cos(midAngleRad).toFloat() - measured.size.width / 2f
        val textY = centerY + midRadius * sin(midAngleRad).toFloat() - measured.size.height / 2f

        drawText(
            textLayoutResult = measured,
            topLeft = Offset(textX, textY)
        )
    }
}

/**
 * Create a Path representing an arc segment (annular sector).
 */
private fun createArcPath(
    cx: Float,
    cy: Float,
    innerRadius: Float,
    outerRadius: Float,
    startAngle: Float,
    sweepAngle: Float
): Path {
    val path = Path()

    val startRad = Math.toRadians(startAngle.toDouble())
    val endRad = Math.toRadians((startAngle + sweepAngle).toDouble())

    // Outer arc: start point
    path.moveTo(
        cx + outerRadius * cos(startRad).toFloat(),
        cy + outerRadius * sin(startRad).toFloat()
    )

    // Outer arc
    path.arcTo(
        rect = androidx.compose.ui.geometry.Rect(
            cx - outerRadius, cy - outerRadius,
            cx + outerRadius, cy + outerRadius
        ),
        startAngleDegrees = startAngle,
        sweepAngleDegrees = sweepAngle,
        forceMoveTo = false
    )

    // Line to inner arc end point
    path.lineTo(
        cx + innerRadius * cos(endRad).toFloat(),
        cy + innerRadius * sin(endRad).toFloat()
    )

    // Inner arc (reverse direction)
    path.arcTo(
        rect = androidx.compose.ui.geometry.Rect(
            cx - innerRadius, cy - innerRadius,
            cx + innerRadius, cy + innerRadius
        ),
        startAngleDegrees = startAngle + sweepAngle,
        sweepAngleDegrees = -sweepAngle,
        forceMoveTo = false
    )

    path.close()
    return path
}
