package com.gedfix.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gedfix.models.ContradictionDetector
import com.gedfix.models.IssueSeverity
import com.gedfix.ui.theme.*
import com.gedfix.viewmodel.AppViewModel

@Composable
fun ContradictionsScreen(appViewModel: AppViewModel) {
    var contradictions by remember { mutableStateOf<List<ContradictionDetector.Contradiction>>(emptyList()) }
    var isAnalyzing by remember { mutableStateOf(false) }
    var hasRun by remember { mutableStateOf(false) }
    var filterCategory by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Contradiction Detector", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Text(
                    "Finds impossible dates, conflicting facts, and logical impossibilities",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Button(
                onClick = {
                    isAnalyzing = true
                    val detector = ContradictionDetector(appViewModel.db)
                    contradictions = detector.detectAll()
                    isAnalyzing = false
                    hasRun = true
                },
                enabled = !isAnalyzing
            ) {
                if (isAnalyzing) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(if (isAnalyzing) "Analyzing..." else "Run Analysis")
            }
        }

        if (hasRun) {
            // Summary
            val critical = contradictions.count { it.severity == IssueSeverity.CRITICAL }
            val warning = contradictions.count { it.severity == IssueSeverity.WARNING }
            val info = contradictions.count { it.severity == IssueSeverity.INFO }

            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                SummaryChip("Critical", critical, CriticalColor)
                SummaryChip("Warning", warning, WarningColor)
                SummaryChip("Info", info, InfoColor)
                SummaryChip("Total", contradictions.size, MaterialTheme.colorScheme.primary)
            }

            // Category filter
            val categories = contradictions.map { it.category }.distinct()
            if (categories.size > 1) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = filterCategory == null,
                        onClick = { filterCategory = null },
                        label = { Text("All", fontSize = 12.sp) }
                    )
                    for (cat in categories) {
                        FilterChip(
                            selected = filterCategory == cat,
                            onClick = { filterCategory = if (filterCategory == cat) null else cat },
                            label = { Text(cat, fontSize = 12.sp) }
                        )
                    }
                }
            }

            // Contradiction list
            val filtered = if (filterCategory != null) {
                contradictions.filter { it.category == filterCategory }
            } else contradictions

            if (filtered.isEmpty()) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    tonalElevation = 1.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("No contradictions found", fontSize = 16.sp, fontWeight = FontWeight.Medium)
                        Text("Your tree data is logically consistent.", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                for (contradiction in filtered) {
                    ContradictionCard(contradiction)
                }
            }
        } else {
            Surface(
                shape = RoundedCornerShape(12.dp),
                tonalElevation = 1.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Click \"Run Analysis\" to scan your tree", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Checks for: impossible dates, age impossibilities, parent-child conflicts, event timeline issues", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
private fun SummaryChip(label: String, count: Int, color: androidx.compose.ui.graphics.Color) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = color.copy(alpha = 0.10f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(count.toString(), fontWeight = FontWeight.Bold, fontSize = 16.sp, color = color)
            Text(label, fontSize = 12.sp, color = color)
        }
    }
}

@Composable
private fun ContradictionCard(contradiction: ContradictionDetector.Contradiction) {
    val severityColor = when (contradiction.severity) {
        IssueSeverity.CRITICAL -> CriticalColor
        IssueSeverity.WARNING -> WarningColor
        IssueSeverity.INFO -> InfoColor
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = severityColor.copy(alpha = 0.15f)
                ) {
                    Text(
                        contradiction.severity.label,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = severityColor,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.08f)
                ) {
                    Text(
                        contradiction.category,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }
            Text(contradiction.title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Text(contradiction.detail, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                "Suggestion: ${contradiction.suggestion}",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}
