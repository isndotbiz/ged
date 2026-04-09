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
import com.gedfix.models.ResearchLogEntry
import com.gedfix.models.ResearchResultType
import com.gedfix.ui.theme.*
import com.gedfix.viewmodel.AppViewModel
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
@Composable
fun ResearchLogScreen(appViewModel: AppViewModel) {
    var entries by remember { mutableStateOf(appViewModel.db.fetchAllResearchLogs()) }
    var showEditor by remember { mutableStateOf(false) }
    var filterType by remember { mutableStateOf<ResearchResultType?>(null) }

    fun refresh() {
        entries = appViewModel.db.fetchAllResearchLogs()
        appViewModel.refreshCounts()
    }

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
                Text("Research Log", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Text(
                    "Track searches, record sets examined, and conclusions drawn",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Button(onClick = { showEditor = true }) {
                Text("Add Entry")
            }
        }

        // Summary
        val positive = entries.count { it.resultType == ResearchResultType.POSITIVE }
        val negative = entries.count { it.resultType == ResearchResultType.NEGATIVE }
        val inconclusive = entries.count { it.resultType == ResearchResultType.INCONCLUSIVE }

        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Surface(shape = RoundedCornerShape(8.dp), color = ValidatedBgColor) {
                Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("$positive", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = ValidatedColor)
                    Text("Positive", fontSize = 12.sp, color = ValidatedColor)
                }
            }
            Surface(shape = RoundedCornerShape(8.dp), color = UnvalidatedBgColor) {
                Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("$negative", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = UnvalidatedColor)
                    Text("Negative", fontSize = 12.sp, color = UnvalidatedColor)
                }
            }
            Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)) {
                Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("$inconclusive", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text("Inconclusive", fontSize = 12.sp)
                }
            }
        }

        // Filter
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = filterType == null, onClick = { filterType = null }, label = { Text("All", fontSize = 12.sp) })
            for (rt in ResearchResultType.entries) {
                FilterChip(
                    selected = filterType == rt,
                    onClick = { filterType = if (filterType == rt) null else rt },
                    label = { Text(rt.label.split(" ").first(), fontSize = 12.sp) }
                )
            }
        }

        val filtered = if (filterType != null) entries.filter { it.resultType == filterType } else entries

        if (filtered.isEmpty()) {
            Surface(shape = RoundedCornerShape(12.dp), tonalElevation = 1.dp, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("No research log entries yet", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Track your research to document exhaustive searches per the Genealogical Proof Standard.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            for (entry in filtered) {
                ResearchLogCard(entry) {
                    appViewModel.db.deleteResearchLog(entry.id)
                    refresh()
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
    }

    if (showEditor) {
        ResearchLogEditorDialog(
            onDismiss = { showEditor = false },
            onSave = { entry ->
                appViewModel.db.insertResearchLog(entry)
                refresh()
                showEditor = false
            }
        )
    }
}

@Composable
private fun ResearchLogCard(entry: ResearchLogEntry, onDelete: () -> Unit) {
    val resultColor = when (entry.resultType) {
        ResearchResultType.POSITIVE -> ValidatedColor
        ResearchResultType.NEGATIVE -> UnvalidatedColor
        ResearchResultType.INCONCLUSIVE -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(shape = RoundedCornerShape(12.dp), tonalElevation = 1.dp, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(shape = RoundedCornerShape(12.dp), color = resultColor.copy(alpha = 0.15f)) {
                        Text(entry.resultType.label, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = resultColor, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
                    }
                    if (entry.searchDate.isNotEmpty()) {
                        Text(entry.searchDate, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                TextButton(onClick = onDelete) {
                    Text("Delete", fontSize = 11.sp, color = CriticalColor)
                }
            }

            if (entry.repository.isNotEmpty()) {
                Text("Repository: ${entry.repository}", fontWeight = FontWeight.Medium, fontSize = 13.sp)
            }
            if (entry.searchTerms.isNotEmpty()) {
                Text("Search: ${entry.searchTerms}", fontSize = 13.sp)
            }
            if (entry.recordsViewed.isNotEmpty()) {
                Text("Records: ${entry.recordsViewed}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (entry.conclusion.isNotEmpty()) {
                Text("Conclusion: ${entry.conclusion}", fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@OptIn(ExperimentalUuidApi::class)
@Composable
private fun ResearchLogEditorDialog(
    onDismiss: () -> Unit,
    onSave: (ResearchLogEntry) -> Unit
) {
    var repository by remember { mutableStateOf("") }
    var searchTerms by remember { mutableStateOf("") }
    var recordsViewed by remember { mutableStateOf("") }
    var conclusion by remember { mutableStateOf("") }
    var resultType by remember { mutableStateOf(ResearchResultType.NEGATIVE) }
    var searchDate by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New Research Log Entry") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(value = repository, onValueChange = { repository = it }, label = { Text("Repository/Source") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = searchTerms, onValueChange = { searchTerms = it }, label = { Text("Search Terms") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = recordsViewed, onValueChange = { recordsViewed = it }, label = { Text("Records Viewed") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
                OutlinedTextField(value = conclusion, onValueChange = { conclusion = it }, label = { Text("Conclusion") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
                OutlinedTextField(value = searchDate, onValueChange = { searchDate = it }, label = { Text("Search Date (YYYY-MM-DD)") }, singleLine = true, modifier = Modifier.fillMaxWidth())

                Text("Result:", fontWeight = FontWeight.Medium, fontSize = 14.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (rt in ResearchResultType.entries) {
                        FilterChip(
                            selected = resultType == rt,
                            onClick = { resultType = rt },
                            label = { Text(rt.name.lowercase().replaceFirstChar { it.uppercase() }, fontSize = 12.sp) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                onSave(ResearchLogEntry(
                    id = Uuid.random().toString(),
                    repository = repository,
                    searchTerms = searchTerms,
                    recordsViewed = recordsViewed,
                    conclusion = conclusion,
                    resultType = resultType,
                    searchDate = searchDate,
                    createdAt = java.time.Instant.now().toString()
                ))
            }) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
