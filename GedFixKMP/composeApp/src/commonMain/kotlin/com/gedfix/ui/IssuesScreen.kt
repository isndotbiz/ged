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
import com.gedfix.models.*
import com.gedfix.ui.components.HealthScoreIndicator
import com.gedfix.ui.theme.*
import com.gedfix.ui.theme.Spacing
import com.gedfix.viewmodel.AppViewModel

/**
 * Tree consistency checker dashboard with health score, category cards, issue list.
 */
@Composable
fun IssuesScreen(viewModel: AppViewModel) {
    var issues by remember { mutableStateOf<List<TreeIssue>>(emptyList()) }
    var isAnalyzing by remember { mutableStateOf(false) }
    var hasAnalyzed by remember { mutableStateOf(false) }
    var searchText by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf<IssueCategory?>(null) }
    var selectedSeverity by remember { mutableStateOf<IssueSeverity?>(null) }
    val dismissedIssueIDs = remember { mutableStateListOf<String>() }

    val activeIssues = issues.filter { it.id !in dismissedIssueIDs }

    val filteredIssues = activeIssues.filter { issue ->
        val catMatch = selectedCategory == null || issue.category == selectedCategory
        val sevMatch = selectedSeverity == null || issue.severity == selectedSeverity
        val textMatch = searchText.isEmpty() ||
            issue.title.contains(searchText, ignoreCase = true) ||
            issue.detail.contains(searchText, ignoreCase = true) ||
            (issue.personXref?.contains(searchText, ignoreCase = true) == true)
        catMatch && sevMatch && textMatch
    }

    val healthScore = run {
        val total = viewModel.personCount + viewModel.familyCount
        if (total > 0) {
            (1.0 - activeIssues.size.toDouble() / total.toDouble()).coerceIn(0.0, 1.0) * 100.0
        } else 100.0
    }

    fun runAnalysis() {
        isAnalyzing = true
        dismissedIssueIDs.clear()
        val db = viewModel.db
        val persons = db.fetchAllPersons()
        val families = db.fetchAllFamilies()
        val events = db.fetchAllEvents()
        val childLinks = db.fetchAllChildLinks()
        val analyzer = TreeAnalyzer(persons, families, events, childLinks)
        val result = analyzer.analyze()
        issues = result
        hasAnalyzed = true
        isAnalyzing = false
        viewModel.issueCount = result.size
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        // Header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column {
                    Text(
                        text = "Tree Consistency Checker",
                        style = MaterialTheme.typography.headlineLarge
                    )
                    if (hasAnalyzed) {
                        Text(
                            text = "${activeIssues.size} issues found",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Button(
                    onClick = { runAnalysis() },
                    enabled = !isAnalyzing && viewModel.personCount > 0,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    if (isAnalyzing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Analyzing...")
                    } else {
                        Text(if (hasAnalyzed) "Re-Analyze" else "Analyze Tree")
                    }
                }
            }
        }

        if (hasAnalyzed) {
            // Summary cards
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Health score
                    Surface(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            HealthScoreIndicator(score = healthScore)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Health", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    // Category cards
                    for (category in IssueCategory.entries) {
                        val count = activeIssues.count { it.category == category }
                        val isSelected = selectedCategory == category
                        val catColor = categoryColor(category)

                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .clickable {
                                    selectedCategory = if (isSelected) null else category
                                },
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) catColor.copy(alpha = 0.15f)
                            else MaterialTheme.colorScheme.surfaceVariant,
                            border = if (isSelected) androidx.compose.foundation.BorderStroke(2.dp, catColor) else null
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = categoryIcon(category),
                                    fontSize = 24.sp,
                                    color = catColor
                                )
                                Text(
                                    text = count.toString(),
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = category.label,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }

            // Search bar
            item {
                OutlinedTextField(
                    value = searchText,
                    onValueChange = { searchText = it },
                    label = { Text("Search issues...") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }

            // Filter info
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (selectedCategory != null || selectedSeverity != null) {
                        TextButton(onClick = {
                            selectedCategory = null
                            selectedSeverity = null
                        }) {
                            Text("Clear Filters")
                        }
                    } else {
                        Spacer(modifier = Modifier)
                    }
                    Text(
                        text = "${filteredIssues.size} of ${activeIssues.size} shown",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Issues grouped by category
            for (category in IssueCategory.entries) {
                val categoryIssues = filteredIssues.filter { it.category == category }
                if (categoryIssues.isNotEmpty()) {
                    item {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = categoryIcon(category),
                                color = categoryColor(category)
                            )
                            Text(
                                text = category.label,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = categoryColor(category).copy(alpha = 0.15f)
                            ) {
                                Text(
                                    text = categoryIssues.size.toString(),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }

                    items(categoryIssues, key = { it.id }) { issue ->
                        IssueRow(
                            issue = issue,
                            onDismiss = { dismissedIssueIDs.add(issue.id) },
                            onNavigateToPerson = { xref ->
                                viewModel.selectedPersonXref = xref
                                viewModel.selectedSection = SidebarSection.PEOPLE
                            }
                        )
                    }
                }
            }
        } else {
            // Empty state
            item {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(top = 60.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (viewModel.personCount == 0) {
                        Text("No Tree Loaded", fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("Import a GEDCOM file first, then analyze it for issues.", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        Text("Ready to Analyze", fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            "Click \"Analyze Tree\" to check ${viewModel.personCount} people and ${viewModel.familyCount} families for consistency issues.",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(onClick = { runAnalysis() }) {
                            Text("Analyze Tree")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun IssueRow(
    issue: TreeIssue,
    onDismiss: () -> Unit,
    onNavigateToPerson: (String) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            Text(
                text = severityIcon(issue.severity),
                color = severityColor(issue.severity),
                fontSize = 16.sp
            )

            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = issue.title, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                    if (issue.isAutoFixable) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = AutoFixBadgeBg
                        ) {
                            Text(
                                text = "Auto-fixable",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = AutoFixBadgeColor,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                            )
                        }
                    }
                }
                Text(
                    text = issue.detail,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("\u2728", fontSize = 10.sp)
                    Text(
                        text = issue.suggestion,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (issue.personXref != null) {
                    TextButton(
                        onClick = { onNavigateToPerson(issue.personXref) },
                        contentPadding = PaddingValues(4.dp)
                    ) {
                        Text("\u263A", fontSize = 14.sp)
                    }
                }
                TextButton(
                    onClick = onDismiss,
                    contentPadding = PaddingValues(4.dp)
                ) {
                    Text("\u2715", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

private fun categoryColor(category: IssueCategory) = when (category) {
    IssueCategory.DATE_ISSUE -> DateIssueColor
    IssueCategory.RELATIONSHIP_ISSUE -> RelationshipIssueColor
    IssueCategory.DATA_QUALITY -> DataQualityColor
    IssueCategory.POTENTIAL_DUPLICATE -> DuplicateColor
}

private fun categoryIcon(category: IssueCategory) = when (category) {
    IssueCategory.DATE_ISSUE -> "\u2757"         // Red exclamation
    IssueCategory.RELATIONSHIP_ISSUE -> "\u26A0"  // Warning
    IssueCategory.DATA_QUALITY -> "\u2714"        // Checkmark
    IssueCategory.POTENTIAL_DUPLICATE -> "\u263A"  // Person
}

private fun severityColor(severity: IssueSeverity) = when (severity) {
    IssueSeverity.CRITICAL -> CriticalColor
    IssueSeverity.WARNING -> WarningColor
    IssueSeverity.INFO -> InfoColor
}

private fun severityIcon(severity: IssueSeverity) = when (severity) {
    IssueSeverity.CRITICAL -> "\u2716" // X
    IssueSeverity.WARNING -> "\u26A0"  // Warning
    IssueSeverity.INFO -> "\u2139"     // Info
}
