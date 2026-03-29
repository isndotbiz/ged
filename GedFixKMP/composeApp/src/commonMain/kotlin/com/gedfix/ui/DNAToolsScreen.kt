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
import com.gedfix.models.DNACalculator
import com.gedfix.models.DNARelationshipPrediction
import com.gedfix.ui.theme.*
import com.gedfix.viewmodel.AppViewModel

@Composable
fun DNAToolsScreen(appViewModel: AppViewModel) {
    var sharedCM by remember { mutableStateOf("") }
    var predictions by remember { mutableStateOf<List<DNARelationshipPrediction>>(emptyList()) }
    var selectedRelationship by remember { mutableStateOf<String?>(null) }
    var lookupResult by remember { mutableStateOf<DNACalculator.RelationshipRange?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Text("DNA Tools", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Text(
            "Relationship prediction using Shared cM Project v4 data (Blaine Bettinger, 60K+ relationships)",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // cM to Relationship Predictor
        Surface(
            shape = RoundedCornerShape(12.dp),
            tonalElevation = 1.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Shared cM to Relationship", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                Text("Enter the amount of shared DNA (in centiMorgans) to predict possible relationships.", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = sharedCM,
                        onValueChange = { sharedCM = it.filter { c -> c.isDigit() || c == '.' } },
                        label = { Text("Shared cM") },
                        singleLine = true,
                        modifier = Modifier.width(200.dp)
                    )
                    Button(
                        onClick = {
                            val cm = sharedCM.toDoubleOrNull()
                            if (cm != null) {
                                predictions = DNACalculator.predictRelationships(cm)
                            }
                        },
                        enabled = sharedCM.toDoubleOrNull() != null && sharedCM.toDoubleOrNull()!! > 0
                    ) {
                        Text("Predict")
                    }
                }

                if (predictions.isNotEmpty()) {
                    Text("Possible Relationships:", fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 8.dp))
                    for (pred in predictions) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = when (pred.probability) {
                                "Very likely" -> ValidatedBgColor
                                "Likely" -> AutoFixBadgeBg
                                "Possible" -> UnvalidatedBgColor
                                else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(pred.relationship, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                                    Text(
                                        "Avg: ${pred.averageCM.toInt()} cM (range: ${pred.minCM.toInt()}-${pred.maxCM.toInt()} cM)",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = when (pred.probability) {
                                        "Very likely" -> ValidatedColor.copy(alpha = 0.15f)
                                        "Likely" -> AutoFixBadgeColor.copy(alpha = 0.15f)
                                        "Possible" -> UnvalidatedColor.copy(alpha = 0.15f)
                                        else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.08f)
                                    }
                                ) {
                                    Text(
                                        pred.probability,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = when (pred.probability) {
                                            "Very likely" -> ValidatedColor
                                            "Likely" -> AutoFixBadgeColor
                                            "Possible" -> UnvalidatedColor
                                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                            }
                        }
                    }
                } else if (sharedCM.isNotEmpty() && sharedCM.toDoubleOrNull() != null) {
                    Text(
                        "No matching relationships found for ${sharedCM} cM",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        }

        // Relationship to Expected cM Lookup
        Surface(
            shape = RoundedCornerShape(12.dp),
            tonalElevation = 1.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Relationship to Expected cM", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                Text("Select a known relationship to see expected shared DNA amounts.", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

                var dropdownExpanded by remember { mutableStateOf(false) }
                Box {
                    OutlinedButton(onClick = { dropdownExpanded = true }) {
                        Text(selectedRelationship ?: "Select relationship...")
                    }
                    DropdownMenu(
                        expanded = dropdownExpanded,
                        onDismissRequest = { dropdownExpanded = false }
                    ) {
                        for (rel in DNACalculator.allRelationships()) {
                            DropdownMenuItem(
                                text = { Text(rel.label) },
                                onClick = {
                                    selectedRelationship = rel.label
                                    lookupResult = rel
                                    dropdownExpanded = false
                                }
                            )
                        }
                    }
                }

                if (lookupResult != null) {
                    val r = lookupResult!!
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(r.label, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                                Column {
                                    Text("Average", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text("${r.averageCM.toInt()} cM", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                                }
                                Column {
                                    Text("Range", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text("${r.minCM.toInt()} - ${r.maxCM.toInt()} cM", fontWeight = FontWeight.Medium, fontSize = 18.sp)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Reference Table
        Surface(
            shape = RoundedCornerShape(12.dp),
            tonalElevation = 1.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Shared cM Reference Table", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                Text("Based on the Shared cM Project v4 by Blaine Bettinger", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

                Spacer(modifier = Modifier.height(8.dp))

                // Header
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Text("Relationship", fontWeight = FontWeight.SemiBold, fontSize = 12.sp, modifier = Modifier.weight(1f))
                    Text("Avg cM", fontWeight = FontWeight.SemiBold, fontSize = 12.sp, modifier = Modifier.width(70.dp))
                    Text("Min", fontWeight = FontWeight.SemiBold, fontSize = 12.sp, modifier = Modifier.width(60.dp))
                    Text("Max", fontWeight = FontWeight.SemiBold, fontSize = 12.sp, modifier = Modifier.width(60.dp))
                }

                HorizontalDivider()

                for (rel in DNACalculator.allRelationships()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(rel.label, fontSize = 12.sp, modifier = Modifier.weight(1f))
                        Text("${rel.averageCM.toInt()}", fontSize = 12.sp, fontWeight = FontWeight.Medium, modifier = Modifier.width(70.dp))
                        Text("${rel.minCM.toInt()}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(60.dp))
                        Text("${rel.maxCM.toInt()}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(60.dp))
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}
