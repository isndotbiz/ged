package com.gedfix.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gedfix.db.DatabaseRepository
import com.gedfix.models.*
import com.gedfix.viewmodel.AppViewModel

/**
 * Reports hub screen with multiple genealogy report types.
 * Each report generates a formatted text preview that can be copied or exported.
 */

enum class ReportType(val label: String, val description: String) {
    INDIVIDUAL_SUMMARY("Individual Summary", "Full profile of a selected person"),
    FAMILY_GROUP_SHEET("Family Group Sheet", "Standard family group sheet format"),
    DESCENDANT_REPORT("Descendant Report", "All descendants of a selected person"),
    ANCESTOR_REPORT("Ancestor Report", "Ahnentafel ancestor list"),
    UNVALIDATED_PERSONS("Unvalidated Persons", "People without any sources")
}

@Composable
fun ReportsScreen(appViewModel: AppViewModel) {
    val db = appViewModel.db
    var selectedReport by remember { mutableStateOf(ReportType.INDIVIDUAL_SUMMARY) }
    var selectedPersonXref by remember { mutableStateOf<String?>(null) }
    var selectedFamilyXref by remember { mutableStateOf<String?>(null) }
    var reportText by remember { mutableStateOf("") }
    var showPersonPicker by remember { mutableStateOf(false) }
    var showFamilyPicker by remember { mutableStateOf(false) }
    var personSearch by remember { mutableStateOf("") }
    val clipboardManager = LocalClipboardManager.current
    var copiedFeedback by remember { mutableStateOf(false) }

    Row(modifier = Modifier.fillMaxSize()) {
        // Left panel: report selector
        Surface(
            modifier = Modifier.width(280.dp).fillMaxHeight(),
            tonalElevation = 1.dp
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Reports",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                ReportType.entries.forEach { type ->
                    val isSelected = selectedReport == type
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                selectedReport = type
                                reportText = ""
                                selectedPersonXref = null
                                selectedFamilyXref = null
                            },
                        shape = RoundedCornerShape(8.dp),
                        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surface
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = type.label,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                fontSize = 14.sp,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                                else MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = type.description,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                }
            }
        }

        VerticalDivider()

        // Right panel: report content
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp)
        ) {
            Text(
                text = selectedReport.label,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            // Person/family selector for applicable reports
            when (selectedReport) {
                ReportType.INDIVIDUAL_SUMMARY, ReportType.DESCENDANT_REPORT, ReportType.ANCESTOR_REPORT -> {
                    PersonSelectorRow(
                        db = db,
                        selectedPersonXref = selectedPersonXref,
                        personSearch = personSearch,
                        showPicker = showPersonPicker,
                        onSearchChange = { personSearch = it },
                        onTogglePicker = { showPersonPicker = !showPersonPicker },
                        onSelect = { xref ->
                            selectedPersonXref = xref
                            showPersonPicker = false
                            reportText = generateReport(selectedReport, db, xref, null)
                        }
                    )
                }
                ReportType.FAMILY_GROUP_SHEET -> {
                    FamilySelectorRow(
                        db = db,
                        selectedFamilyXref = selectedFamilyXref,
                        showPicker = showFamilyPicker,
                        onTogglePicker = { showFamilyPicker = !showFamilyPicker },
                        onSelect = { xref ->
                            selectedFamilyXref = xref
                            showFamilyPicker = false
                            reportText = generateReport(selectedReport, db, null, xref)
                        }
                    )
                }
                ReportType.UNVALIDATED_PERSONS -> {
                    // No selector needed - auto-generate
                    LaunchedEffect(selectedReport) {
                        reportText = generateReport(selectedReport, db, null, null)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Action buttons
            if (reportText.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        clipboardManager.setText(AnnotatedString(reportText))
                        copiedFeedback = true
                    }) {
                        Text(if (copiedFeedback) "Copied!" else "Copy to Clipboard")
                    }

                    // Reset feedback after a moment
                    LaunchedEffect(copiedFeedback) {
                        if (copiedFeedback) {
                            kotlinx.coroutines.delay(2000)
                            copiedFeedback = false
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }

            // Report preview
            if (reportText.isNotEmpty()) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    shape = RoundedCornerShape(8.dp),
                    tonalElevation = 1.dp
                ) {
                    SelectionContainer {
                        Text(
                            text = reportText,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp,
                            lineHeight = 18.sp,
                            modifier = Modifier
                                .padding(16.dp)
                                .verticalScroll(rememberScrollState())
                        )
                    }
                }
            } else if (selectedReport != ReportType.UNVALIDATED_PERSONS) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Select a ${if (selectedReport == ReportType.FAMILY_GROUP_SHEET) "family" else "person"} to generate the report.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun PersonSelectorRow(
    db: DatabaseRepository,
    selectedPersonXref: String?,
    personSearch: String,
    showPicker: Boolean,
    onSearchChange: (String) -> Unit,
    onTogglePicker: () -> Unit,
    onSelect: (String) -> Unit
) {
    val selectedPerson = selectedPersonXref?.let { db.fetchPerson(it) }

    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Person:", fontWeight = FontWeight.SemiBold)
            Text(
                text = selectedPerson?.displayName ?: "(none selected)",
                color = if (selectedPerson != null) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedButton(onClick = onTogglePicker) {
                Text(if (showPicker) "Close" else "Select Person")
            }
        }

        if (showPicker) {
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = personSearch,
                onValueChange = onSearchChange,
                label = { Text("Search persons...") },
                modifier = Modifier.fillMaxWidth(0.5f),
                singleLine = true
            )
            Spacer(modifier = Modifier.height(4.dp))

            val persons = db.fetchPersons(search = personSearch)
            Surface(
                modifier = Modifier.fillMaxWidth(0.5f).heightIn(max = 300.dp),
                shape = RoundedCornerShape(8.dp),
                tonalElevation = 2.dp
            ) {
                LazyColumn {
                    items(persons.take(50)) { person ->
                        Text(
                            text = "${person.displayName} (${person.xref})",
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelect(person.xref) }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FamilySelectorRow(
    db: DatabaseRepository,
    selectedFamilyXref: String?,
    showPicker: Boolean,
    onTogglePicker: () -> Unit,
    onSelect: (String) -> Unit
) {
    val families = remember { db.fetchAllFamilies() }

    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Family:", fontWeight = FontWeight.SemiBold)
            Text(
                text = selectedFamilyXref?.let { xref ->
                    val fam = families.firstOrNull { it.xref == xref }
                    if (fam != null) {
                        val p1 = db.fetchPerson(fam.partner1Xref)?.displayName ?: "?"
                        val p2 = db.fetchPerson(fam.partner2Xref)?.displayName ?: "?"
                        "$p1 & $p2 ($xref)"
                    } else xref
                } ?: "(none selected)",
                color = if (selectedFamilyXref != null) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedButton(onClick = onTogglePicker) {
                Text(if (showPicker) "Close" else "Select Family")
            }
        }

        if (showPicker) {
            Spacer(modifier = Modifier.height(8.dp))
            Surface(
                modifier = Modifier.fillMaxWidth(0.5f).heightIn(max = 300.dp),
                shape = RoundedCornerShape(8.dp),
                tonalElevation = 2.dp
            ) {
                LazyColumn {
                    items(families) { family ->
                        val p1 = db.fetchPerson(family.partner1Xref)?.displayName ?: "?"
                        val p2 = db.fetchPerson(family.partner2Xref)?.displayName ?: "?"
                        Text(
                            text = "$p1 & $p2 (${family.xref})",
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelect(family.xref) }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }
    }
}

// MARK: - Report Generation

private fun generateReport(
    type: ReportType,
    db: DatabaseRepository,
    personXref: String?,
    familyXref: String?
): String {
    return when (type) {
        ReportType.INDIVIDUAL_SUMMARY -> generateIndividualSummary(db, personXref ?: return "")
        ReportType.FAMILY_GROUP_SHEET -> generateFamilyGroupSheet(db, familyXref ?: return "")
        ReportType.DESCENDANT_REPORT -> generateDescendantReport(db, personXref ?: return "")
        ReportType.ANCESTOR_REPORT -> generateAncestorReport(db, personXref ?: return "")
        ReportType.UNVALIDATED_PERSONS -> generateUnvalidatedReport(db)
    }
}

private fun generateIndividualSummary(db: DatabaseRepository, xref: String): String {
    val person = db.fetchPerson(xref) ?: return "Person not found."
    val events = db.fetchEvents(xref)
    val spouseFamilies = db.fetchFamiliesAsSpouse(xref)
    val childFamilies = db.fetchFamiliesAsChild(xref)

    val sb = StringBuilder()
    sb.appendLine("=" .repeat(60))
    sb.appendLine("INDIVIDUAL SUMMARY")
    sb.appendLine("=".repeat(60))
    sb.appendLine()
    sb.appendLine("Name:    ${person.displayName}")
    sb.appendLine("Xref:    ${person.xref}")
    sb.appendLine("Sex:     ${when(person.sex) { "M" -> "Male"; "F" -> "Female"; else -> "Unknown" }}")
    sb.appendLine("Living:  ${if (person.isLiving) "Yes" else "No"}")
    sb.appendLine()

    // Events
    if (events.isNotEmpty()) {
        sb.appendLine("-".repeat(40))
        sb.appendLine("EVENTS")
        sb.appendLine("-".repeat(40))
        for (event in events) {
            val datePart = if (event.dateValue.isNotEmpty()) event.dateValue else "unknown date"
            val placePart = if (event.place.isNotEmpty()) ", ${event.place}" else ""
            sb.appendLine("  ${event.displayType}: $datePart$placePart")
        }
        sb.appendLine()
    }

    // Parents
    if (childFamilies.isNotEmpty()) {
        sb.appendLine("-".repeat(40))
        sb.appendLine("PARENTS")
        sb.appendLine("-".repeat(40))
        for (fam in childFamilies) {
            val father = if (fam.partner1Xref.isNotEmpty()) db.fetchPerson(fam.partner1Xref)?.displayName ?: "Unknown" else "Unknown"
            val mother = if (fam.partner2Xref.isNotEmpty()) db.fetchPerson(fam.partner2Xref)?.displayName ?: "Unknown" else "Unknown"
            sb.appendLine("  Father: $father")
            sb.appendLine("  Mother: $mother")
        }
        sb.appendLine()
    }

    // Spouses and children
    if (spouseFamilies.isNotEmpty()) {
        sb.appendLine("-".repeat(40))
        sb.appendLine("FAMILIES")
        sb.appendLine("-".repeat(40))
        for (fam in spouseFamilies) {
            val spouseXref = if (fam.partner1Xref == xref) fam.partner2Xref else fam.partner1Xref
            val spouse = if (spouseXref.isNotEmpty()) db.fetchPerson(spouseXref)?.displayName ?: "Unknown" else "Unknown"
            sb.appendLine("  Spouse: $spouse")

            val famEvents = db.fetchEvents(fam.xref)
            val marriage = famEvents.firstOrNull { it.eventType == "MARR" }
            if (marriage != null) {
                val datePart = if (marriage.dateValue.isNotEmpty()) marriage.dateValue else "unknown date"
                val placePart = if (marriage.place.isNotEmpty()) ", ${marriage.place}" else ""
                sb.appendLine("  Marriage: $datePart$placePart")
            }

            val children = db.fetchChildLinks(fam.xref)
            if (children.isNotEmpty()) {
                sb.appendLine("  Children:")
                for (child in children) {
                    val childPerson = db.fetchPerson(child.childXref)
                    val birth = childPerson?.let { db.fetchBirthEvent(it.xref) }
                    val birthStr = if (birth != null && birth.dateValue.isNotEmpty()) " (b. ${birth.dateValue})" else ""
                    sb.appendLine("    - ${childPerson?.displayName ?: child.childXref}$birthStr")
                }
            }
            sb.appendLine()
        }
    }

    // Sources
    sb.appendLine("Sources: ${person.sourceCount}")
    sb.appendLine("Media:   ${person.mediaCount}")

    return sb.toString()
}

private fun generateFamilyGroupSheet(db: DatabaseRepository, familyXref: String): String {
    val families = db.fetchAllFamilies()
    val family = families.firstOrNull { it.xref == familyXref } ?: return "Family not found."

    val sb = StringBuilder()
    sb.appendLine("=".repeat(60))
    sb.appendLine("FAMILY GROUP SHEET")
    sb.appendLine("=".repeat(60))
    sb.appendLine()

    // Father
    sb.appendLine("HUSBAND/PARTNER 1")
    sb.appendLine("-".repeat(40))
    if (family.partner1Xref.isNotEmpty()) {
        writePersonBrief(sb, db, family.partner1Xref)
    } else {
        sb.appendLine("  (Unknown)")
    }
    sb.appendLine()

    // Mother
    sb.appendLine("WIFE/PARTNER 2")
    sb.appendLine("-".repeat(40))
    if (family.partner2Xref.isNotEmpty()) {
        writePersonBrief(sb, db, family.partner2Xref)
    } else {
        sb.appendLine("  (Unknown)")
    }
    sb.appendLine()

    // Marriage
    val famEvents = db.fetchEvents(familyXref)
    val marriage = famEvents.firstOrNull { it.eventType == "MARR" }
    if (marriage != null) {
        sb.appendLine("MARRIAGE")
        sb.appendLine("-".repeat(40))
        sb.appendLine("  Date:  ${marriage.dateValue.ifEmpty { "Unknown" }}")
        sb.appendLine("  Place: ${marriage.place.ifEmpty { "Unknown" }}")
        sb.appendLine()
    }

    // Children
    val childLinks = db.fetchChildLinks(familyXref)
    if (childLinks.isNotEmpty()) {
        sb.appendLine("CHILDREN")
        sb.appendLine("-".repeat(40))
        for ((index, link) in childLinks.withIndex()) {
            sb.appendLine("  ${index + 1}.")
            writePersonBrief(sb, db, link.childXref, indent = "     ")
            sb.appendLine()
        }
    }

    return sb.toString()
}

private fun writePersonBrief(sb: StringBuilder, db: DatabaseRepository, xref: String, indent: String = "  ") {
    val person = db.fetchPerson(xref)
    if (person == null) {
        sb.appendLine("${indent}($xref - not found)")
        return
    }
    sb.appendLine("${indent}Name:  ${person.displayName}")
    sb.appendLine("${indent}Sex:   ${when(person.sex) { "M" -> "Male"; "F" -> "Female"; else -> "Unknown" }}")

    val birth = db.fetchBirthEvent(xref)
    if (birth != null) {
        sb.appendLine("${indent}Birth: ${birth.dateValue.ifEmpty { "Unknown" }}${if (birth.place.isNotEmpty()) ", ${birth.place}" else ""}")
    }

    val death = db.fetchDeathEvent(xref)
    if (death != null) {
        sb.appendLine("${indent}Death: ${death.dateValue.ifEmpty { "Unknown" }}${if (death.place.isNotEmpty()) ", ${death.place}" else ""}")
    }
}

private fun generateDescendantReport(db: DatabaseRepository, rootXref: String): String {
    val person = db.fetchPerson(rootXref) ?: return "Person not found."

    val sb = StringBuilder()
    sb.appendLine("=".repeat(60))
    sb.appendLine("DESCENDANT REPORT")
    sb.appendLine("=".repeat(60))
    sb.appendLine("Root: ${person.displayName} (${person.xref})")
    sb.appendLine()

    val visited = mutableSetOf<String>()
    writeDescendants(sb, db, rootXref, generation = 1, indent = "", visited = visited)

    if (visited.size <= 1) {
        sb.appendLine("  No descendants found.")
    }

    return sb.toString()
}

private fun writeDescendants(
    sb: StringBuilder,
    db: DatabaseRepository,
    xref: String,
    generation: Int,
    indent: String,
    visited: MutableSet<String>
) {
    if (xref in visited) return
    visited.add(xref)

    val person = db.fetchPerson(xref) ?: return
    val birth = db.fetchBirthEvent(xref)
    val death = db.fetchDeathEvent(xref)

    val birthStr = if (birth != null && birth.dateValue.isNotEmpty()) "b. ${birth.dateValue}" else ""
    val deathStr = if (death != null && death.dateValue.isNotEmpty()) "d. ${death.dateValue}" else ""
    val lifeSpan = listOf(birthStr, deathStr).filter { it.isNotEmpty() }.joinToString(", ")
    val lifeSpanFmt = if (lifeSpan.isNotEmpty()) " ($lifeSpan)" else ""

    sb.appendLine("${indent}$generation. ${person.displayName}$lifeSpanFmt")

    // Find families where this person is a spouse
    val spouseFamilies = db.fetchFamiliesAsSpouse(xref)
    for (fam in spouseFamilies) {
        val spouseXref = if (fam.partner1Xref == xref) fam.partner2Xref else fam.partner1Xref
        if (spouseXref.isNotEmpty()) {
            val spouse = db.fetchPerson(spouseXref)
            val marriage = db.fetchEvents(fam.xref).firstOrNull { it.eventType == "MARR" }
            val marriageStr = if (marriage != null && marriage.dateValue.isNotEmpty()) " m. ${marriage.dateValue}" else ""
            sb.appendLine("${indent}   sp. ${spouse?.displayName ?: spouseXref}$marriageStr")
        }

        val children = db.fetchChildLinks(fam.xref)
        for (child in children) {
            writeDescendants(sb, db, child.childXref, generation + 1, "$indent   ", visited)
        }
    }
}

private fun generateAncestorReport(db: DatabaseRepository, rootXref: String): String {
    val person = db.fetchPerson(rootXref) ?: return "Person not found."

    val sb = StringBuilder()
    sb.appendLine("=".repeat(60))
    sb.appendLine("ANCESTOR REPORT (Ahnentafel)")
    sb.appendLine("=".repeat(60))
    sb.appendLine()

    // Build ancestor map using Ahnentafel numbering
    // 1 = root, 2 = father, 3 = mother, 4 = paternal GF, etc.
    data class AncestorEntry(val number: Int, val xref: String)

    val queue = ArrayDeque<AncestorEntry>()
    val results = mutableListOf<Pair<Int, GedcomPerson?>>()
    val visited = mutableSetOf<String>()

    queue.add(AncestorEntry(1, rootXref))

    while (queue.isNotEmpty()) {
        val entry = queue.removeFirst()
        if (entry.xref in visited) continue
        visited.add(entry.xref)

        val p = db.fetchPerson(entry.xref)
        results.add(Pair(entry.number, p))

        if (p != null) {
            val (father, mother) = db.fetchParents(entry.xref)
            if (father != null) {
                queue.add(AncestorEntry(entry.number * 2, father.xref))
            }
            if (mother != null) {
                queue.add(AncestorEntry(entry.number * 2 + 1, mother.xref))
            }
        }
    }

    results.sortBy { it.first }

    for ((number, p) in results) {
        if (p == null) continue
        val generation = when {
            number == 1 -> "Self"
            number <= 3 -> "Parents"
            number <= 7 -> "Grandparents"
            number <= 15 -> "Great-Grandparents"
            number <= 31 -> "2x Great-Grandparents"
            else -> "Gen ${Integer.toBinaryString(number).length - 1}"
        }

        val birth = db.fetchBirthEvent(p.xref)
        val death = db.fetchDeathEvent(p.xref)
        val birthStr = if (birth != null && birth.dateValue.isNotEmpty()) "b. ${birth.dateValue}" else ""
        val deathStr = if (death != null && death.dateValue.isNotEmpty()) "d. ${death.dateValue}" else ""
        val lifeSpan = listOf(birthStr, deathStr).filter { it.isNotEmpty() }.joinToString(", ")

        sb.appendLine("  $number. ${p.displayName}")
        if (lifeSpan.isNotEmpty()) {
            sb.appendLine("      $lifeSpan")
        }
        sb.appendLine("      [$generation]")
    }

    val maxGen = results.maxOfOrNull { Integer.toBinaryString(it.first).length } ?: 0
    sb.appendLine()
    sb.appendLine("Total ancestors found: ${results.size - 1}")
    sb.appendLine("Generations traced: ${maxGen - 1}")

    return sb.toString()
}

private fun generateUnvalidatedReport(db: DatabaseRepository): String {
    val persons = db.fetchUnvalidatedPersons()

    val sb = StringBuilder()
    sb.appendLine("=".repeat(60))
    sb.appendLine("UNVALIDATED PERSONS REPORT")
    sb.appendLine("=".repeat(60))
    sb.appendLine("Persons without sources: ${persons.size}")
    sb.appendLine()

    if (persons.isEmpty()) {
        sb.appendLine("All persons have at least one source. Great work!")
        return sb.toString()
    }

    sb.appendLine(String.format("%-12s %-30s %-6s %-8s", "Xref", "Name", "Sex", "Living"))
    sb.appendLine("-".repeat(60))

    for (person in persons) {
        sb.appendLine(
            String.format(
                "%-12s %-30s %-6s %-8s",
                person.xref,
                person.displayName.take(30),
                person.sex,
                if (person.isLiving) "Yes" else "No"
            )
        )
    }

    sb.appendLine()
    sb.appendLine("Total: ${persons.size} persons need source citations.")

    return sb.toString()
}
