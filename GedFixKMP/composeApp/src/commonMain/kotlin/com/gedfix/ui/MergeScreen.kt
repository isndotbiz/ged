package com.gedfix.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gedfix.models.*
import com.gedfix.ui.theme.*
import com.gedfix.viewmodel.AppViewModel

/**
 * Duplicate pair for merge review.
 */
data class DuplicatePair(
    val personA: GedcomPerson,
    val personB: GedcomPerson,
    val eventsA: List<GedcomEvent>,
    val eventsB: List<GedcomEvent>,
    val matchScore: Double
)

/**
 * Guided merge/deduplicate screen with three-panel layout.
 * Shows potential duplicates detected by TreeAnalyzer and allows
 * field-by-field merge decisions.
 */
@Composable
fun MergeScreen(viewModel: AppViewModel) {
    var duplicatePairs by remember { mutableStateOf<List<DuplicatePair>>(emptyList()) }
    var currentIndex by remember { mutableStateOf(0) }
    var isAnalyzing by remember { mutableStateOf(false) }
    var hasAnalyzed by remember { mutableStateOf(false) }
    var dismissedPairs by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var mergedPairs by remember { mutableStateOf<Set<Int>>(emptySet()) }

    // Merge field selections for current pair
    var selectedGivenName by remember { mutableStateOf("A") } // "A" or "B"
    var selectedSurname by remember { mutableStateOf("A") }
    var selectedSuffix by remember { mutableStateOf("A") }
    var selectedSex by remember { mutableStateOf("A") }
    var selectedIsLiving by remember { mutableStateOf("A") }
    var showMergeConfirm by remember { mutableStateOf(false) }

    val activePairs = duplicatePairs.indices.filter { it !in dismissedPairs && it !in mergedPairs }
    val currentPairIndex = activePairs.getOrNull(currentIndex)
    val currentPair = currentPairIndex?.let { duplicatePairs[it] }
    val scrollState = rememberScrollState()

    fun resetSelections() {
        selectedGivenName = "A"
        selectedSurname = "A"
        selectedSuffix = "A"
        selectedSex = "A"
        selectedIsLiving = "A"
    }

    fun runAnalysis() {
        isAnalyzing = true
        val persons = viewModel.db.fetchAllPersons()
        val families = viewModel.db.fetchAllFamilies()
        val events = viewModel.db.fetchAllEvents()
        val childLinks = viewModel.db.fetchAllChildLinksFlat()

        val analyzer = TreeAnalyzer(persons, families, events, childLinks)
        val issues = analyzer.analyze()

        // Extract duplicate pairs from issues
        val dupIssues = issues.filter { it.category == IssueCategory.POTENTIAL_DUPLICATE }
        val pairs = mutableListOf<DuplicatePair>()
        val personMap = persons.associateBy { it.xref }
        val eventMap = events.groupBy { it.ownerXref }

        for (issue in dupIssues) {
            val xrefA = issue.personXref ?: continue
            // Parse the second person from the detail text
            val personA = personMap[xrefA] ?: continue

            // Find the other person by searching for matching names in the detail
            val otherPerson = persons.firstOrNull { other ->
                other.xref != xrefA &&
                    issue.detail.contains(other.displayName) &&
                    issue.detail.contains(personA.displayName)
            } ?: continue

            // Avoid creating reverse duplicates
            val pairKey = listOf(xrefA, otherPerson.xref).sorted()
            if (pairs.any { listOf(it.personA.xref, it.personB.xref).sorted() == pairKey }) continue

            // Calculate match score
            val givenMatch = personA.givenName.equals(otherPerson.givenName, ignoreCase = true)
            val surnameMatch = personA.surname.equals(otherPerson.surname, ignoreCase = true)
            val evA = eventMap[xrefA] ?: emptyList()
            val evB = eventMap[otherPerson.xref] ?: emptyList()
            val birthA = evA.firstOrNull { it.eventType == "BIRT" }?.dateValue
            val birthB = evB.firstOrNull { it.eventType == "BIRT" }?.dateValue
            val birthMatch = birthA != null && birthB != null && birthA == birthB

            var score = 50.0
            if (givenMatch) score += 20.0
            if (surnameMatch) score += 15.0
            if (birthMatch) score += 15.0

            pairs.add(
                DuplicatePair(
                    personA = personA,
                    personB = otherPerson,
                    eventsA = evA,
                    eventsB = evB,
                    matchScore = score.coerceAtMost(100.0)
                )
            )
        }

        duplicatePairs = pairs
        viewModel.duplicateCount = pairs.size
        currentIndex = 0
        dismissedPairs = emptySet()
        mergedPairs = emptySet()
        resetSelections()
        isAnalyzing = false
        hasAnalyzed = true
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Merge Duplicates", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                if (hasAnalyzed) {
                    Text(
                        "${activePairs.size} potential duplicate${if (activePairs.size != 1) "s" else ""} remaining",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Button(
                onClick = { runAnalysis() },
                enabled = !isAnalyzing
            ) {
                Text(if (hasAnalyzed) "Re-analyze" else "Find Duplicates")
            }
        }

        if (isAnalyzing) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CircularProgressIndicator()
                    Text("Analyzing tree for duplicates...")
                }
            }
            return@Column
        }

        if (!hasAnalyzed) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        "\u21C4",
                        fontSize = 48.sp,
                        color = MergeIconColor
                    )
                    Text(
                        "Find and merge duplicate person records",
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "Click 'Find Duplicates' to scan your tree for potential duplicate persons based on name and birth date similarity.",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.widthIn(max = 400.dp)
                    )
                }
            }
            return@Column
        }

        if (activePairs.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "\u2713",
                        fontSize = 48.sp,
                        color = HealthGoodColor
                    )
                    Text(
                        "No duplicates remaining",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "All potential duplicates have been reviewed.",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            return@Column
        }

        if (currentPair == null) return@Column

        // Progress bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Reviewing ${currentIndex + 1} of ${activePairs.size}",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            LinearProgressIndicator(
                progress = { (currentIndex + 1).toFloat() / activePairs.size.toFloat() },
                modifier = Modifier.weight(1f).height(6.dp),
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = when {
                    currentPair.matchScore >= 80 -> MergeMatchBg
                    currentPair.matchScore >= 60 -> MergeConflictBg
                    else -> CitationUnknownBg
                }
            ) {
                Text(
                    "${currentPair.matchScore.toInt()}% match",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = when {
                        currentPair.matchScore >= 80 -> MergeMatchColor
                        currentPair.matchScore >= 60 -> MergeConflictColor
                        else -> CitationUnknownColor
                    },
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
        }

        // Three-panel merge interface
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Person A panel
            PersonMergePanel(
                title = "Person A",
                person = currentPair.personA,
                events = currentPair.eventsA,
                modifier = Modifier.weight(1f),
                scrollState = rememberScrollState()
            )

            // Merged Result panel
            MergedResultPanel(
                personA = currentPair.personA,
                personB = currentPair.personB,
                eventsA = currentPair.eventsA,
                eventsB = currentPair.eventsB,
                selectedGivenName = selectedGivenName,
                selectedSurname = selectedSurname,
                selectedSuffix = selectedSuffix,
                selectedSex = selectedSex,
                selectedIsLiving = selectedIsLiving,
                onSelectGivenName = { selectedGivenName = it },
                onSelectSurname = { selectedSurname = it },
                onSelectSuffix = { selectedSuffix = it },
                onSelectSex = { selectedSex = it },
                onSelectIsLiving = { selectedIsLiving = it },
                modifier = Modifier.weight(1f),
                scrollState = rememberScrollState()
            )

            // Person B panel
            PersonMergePanel(
                title = "Person B",
                person = currentPair.personB,
                events = currentPair.eventsB,
                modifier = Modifier.weight(1f),
                scrollState = rememberScrollState()
            )
        }

        // Action buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally)
        ) {
            // Auto-pick best
            OutlinedButton(onClick = {
                val a = currentPair.personA
                val b = currentPair.personB
                selectedGivenName = if (a.givenName.length >= b.givenName.length) "A" else "B"
                selectedSurname = if (a.surname.length >= b.surname.length) "A" else "B"
                selectedSuffix = if (a.suffix.isNotEmpty()) "A" else "B"
                selectedSex = if (a.sex != "U") "A" else "B"
                selectedIsLiving = "A" // Default to A
            }) {
                Text("Auto-pick Best")
            }

            // Not the Same Person
            OutlinedButton(
                onClick = {
                    dismissedPairs = dismissedPairs + currentPairIndex!!
                    if (currentIndex >= activePairs.size - 1) {
                        currentIndex = 0
                    }
                    resetSelections()
                    viewModel.duplicateCount = activePairs.size - 1
                }
            ) {
                Text("Not Same Person")
            }

            // Skip
            OutlinedButton(
                onClick = {
                    currentIndex = (currentIndex + 1) % activePairs.size
                    resetSelections()
                }
            ) {
                Text("Skip")
            }

            // Merge
            Button(
                onClick = { showMergeConfirm = true },
                colors = ButtonDefaults.buttonColors(containerColor = MergeIconColor)
            ) {
                Text("Merge")
            }
        }
    }

    // Merge confirmation dialog
    if (showMergeConfirm && currentPair != null) {
        val mergedPerson = buildMergedPerson(
            currentPair.personA, currentPair.personB,
            selectedGivenName, selectedSurname, selectedSuffix, selectedSex, selectedIsLiving
        )

        AlertDialog(
            onDismissRequest = { showMergeConfirm = false },
            title = { Text("Confirm Merge") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("This will merge:")
                    Text(
                        "  ${currentPair.personA.displayName} (${currentPair.personA.xref})",
                        fontWeight = FontWeight.Medium
                    )
                    Text("  + ${currentPair.personB.displayName} (${currentPair.personB.xref})")
                    Text("")
                    Text("Into: ${mergedPerson.displayName}", fontWeight = FontWeight.Bold)
                    Text(
                        "Keeping ${currentPair.personA.xref}, removing ${currentPair.personB.xref}.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "All events, family links, and citations from both records will be preserved.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.db.mergePersons(
                            keepXref = currentPair.personA.xref,
                            removeXref = currentPair.personB.xref,
                            mergedData = mergedPerson
                        )
                        mergedPairs = mergedPairs + currentPairIndex!!
                        if (currentIndex >= activePairs.size - 1) {
                            currentIndex = 0
                        }
                        resetSelections()
                        showMergeConfirm = false
                        viewModel.refreshCounts()
                        viewModel.duplicateCount = activePairs.size - 1
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MergeIconColor)
                ) {
                    Text("Merge")
                }
            },
            dismissButton = {
                TextButton(onClick = { showMergeConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun PersonMergePanel(
    title: String,
    person: GedcomPerson,
    events: List<GedcomEvent>,
    modifier: Modifier = Modifier,
    scrollState: androidx.compose.foundation.ScrollState
) {
    Surface(
        modifier = modifier.fillMaxHeight(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            HorizontalDivider()

            Text(person.displayName.ifEmpty { "(Unknown)" }, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Text(person.xref, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))

            Spacer(Modifier.height(4.dp))

            FieldRow("Given Name", person.givenName)
            FieldRow("Surname", person.surname)
            FieldRow("Suffix", person.suffix)
            FieldRow("Sex", person.sex)
            FieldRow("Living", if (person.isLiving) "Yes" else "No")

            if (events.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text("Events (${events.size})", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                for (event in events) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(event.displayType, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        Column {
                            if (event.dateValue.isNotEmpty()) {
                                Text(event.dateValue, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (event.place.isNotEmpty()) {
                                Text(event.place, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MergedResultPanel(
    personA: GedcomPerson,
    personB: GedcomPerson,
    eventsA: List<GedcomEvent>,
    eventsB: List<GedcomEvent>,
    selectedGivenName: String,
    selectedSurname: String,
    selectedSuffix: String,
    selectedSex: String,
    selectedIsLiving: String,
    onSelectGivenName: (String) -> Unit,
    onSelectSurname: (String) -> Unit,
    onSelectSuffix: (String) -> Unit,
    onSelectSex: (String) -> Unit,
    onSelectIsLiving: (String) -> Unit,
    modifier: Modifier = Modifier,
    scrollState: androidx.compose.foundation.ScrollState
) {
    Surface(
        modifier = modifier.fillMaxHeight(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Merged Result", fontSize = 14.sp, fontWeight = FontWeight.Bold,
                color = MergeIconColor)
            HorizontalDivider()

            MergeFieldSelector(
                label = "Given Name",
                valueA = personA.givenName,
                valueB = personB.givenName,
                selected = selectedGivenName,
                onSelect = onSelectGivenName
            )

            MergeFieldSelector(
                label = "Surname",
                valueA = personA.surname,
                valueB = personB.surname,
                selected = selectedSurname,
                onSelect = onSelectSurname
            )

            MergeFieldSelector(
                label = "Suffix",
                valueA = personA.suffix,
                valueB = personB.suffix,
                selected = selectedSuffix,
                onSelect = onSelectSuffix
            )

            MergeFieldSelector(
                label = "Sex",
                valueA = personA.sex,
                valueB = personB.sex,
                selected = selectedSex,
                onSelect = onSelectSex
            )

            MergeFieldSelector(
                label = "Living",
                valueA = if (personA.isLiving) "Yes" else "No",
                valueB = if (personB.isLiving) "Yes" else "No",
                selected = selectedIsLiving,
                onSelect = onSelectIsLiving
            )

            // Combined events
            Spacer(Modifier.height(8.dp))
            val allEvents = (eventsA + eventsB)
                .distinctBy { "${it.eventType}-${it.dateValue}-${it.place}" }
                .sortedBy { it.eventType }

            Text("Events (${allEvents.size} combined)", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Text(
                "All unique events from both records will be preserved.",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            for (event in allEvents) {
                val isFromA = eventsA.any { it.id == event.id }
                val isFromB = eventsB.any { it.id == event.id }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Text(event.displayType, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    Column(modifier = Modifier.weight(1f)) {
                        if (event.dateValue.isNotEmpty()) {
                            Text(event.dateValue, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (event.place.isNotEmpty()) {
                            Text(event.place, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Text(
                        if (isFromA && isFromB) "A+B" else if (isFromA) "A" else "B",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }
            }
        }
    }
}

@Composable
private fun MergeFieldSelector(
    label: String,
    valueA: String,
    valueB: String,
    selected: String,
    onSelect: (String) -> Unit
) {
    val isMatch = valueA.equals(valueB, ignoreCase = true)
    val bgColor = if (isMatch) MergeMatchBg else MergeConflictBg
    val labelColor = if (isMatch) MergeMatchColor else MergeConflictColor

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = labelColor)
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            // Option A
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .clickable { onSelect("A") }
                    .then(
                        if (selected == "A") Modifier.border(2.dp, MergeSelectedBorder, RoundedCornerShape(6.dp))
                        else Modifier
                    ),
                shape = RoundedCornerShape(6.dp),
                color = if (selected == "A") MergeSelectedBorder.copy(alpha = 0.1f) else bgColor
            ) {
                Text(
                    text = valueA.ifEmpty { "(empty)" },
                    fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                    color = if (valueA.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    else MaterialTheme.colorScheme.onSurface
                )
            }

            // Option B
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .clickable { onSelect("B") }
                    .then(
                        if (selected == "B") Modifier.border(2.dp, MergeSelectedBorder, RoundedCornerShape(6.dp))
                        else Modifier
                    ),
                shape = RoundedCornerShape(6.dp),
                color = if (selected == "B") MergeSelectedBorder.copy(alpha = 0.1f) else bgColor
            ) {
                Text(
                    text = valueB.ifEmpty { "(empty)" },
                    fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                    color = if (valueB.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    else MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
private fun FieldRow(label: String, value: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "$label:",
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            value.ifEmpty { "-" },
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

private fun buildMergedPerson(
    personA: GedcomPerson,
    personB: GedcomPerson,
    selectedGivenName: String,
    selectedSurname: String,
    selectedSuffix: String,
    selectedSex: String,
    selectedIsLiving: String
): GedcomPerson {
    return personA.copy(
        givenName = if (selectedGivenName == "A") personA.givenName else personB.givenName,
        surname = if (selectedSurname == "A") personA.surname else personB.surname,
        suffix = if (selectedSuffix == "A") personA.suffix else personB.suffix,
        sex = if (selectedSex == "A") personA.sex else personB.sex,
        isLiving = if (selectedIsLiving == "A") personA.isLiving else personB.isLiving
    )
}
