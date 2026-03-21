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
import com.gedfix.models.DateDisplayFormat
import com.gedfix.models.NameDisplayFormat
import com.gedfix.viewmodel.AppViewModel
import com.gedfix.viewmodel.SettingsViewModel

/**
 * Settings screen with General, Privacy, Display, and About sections.
 * Settings are persisted in the SQLDelight settings table.
 */
@Composable
fun SettingsScreen(appViewModel: AppViewModel) {
    val settingsVm = remember { SettingsViewModel(appViewModel.db) }

    LaunchedEffect(Unit) {
        settingsVm.load()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Text(
            text = "Settings",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        // GENERAL
        SettingsSection(title = "General") {
            SliderSetting(
                label = "Living person threshold (years)",
                value = settingsVm.livingThreshold.toFloat(),
                valueRange = 80f..130f,
                steps = 10,
                displayValue = "${settingsVm.livingThreshold} years",
                onValueChange = {
                    settingsVm.livingThreshold = it.toInt()
                    settingsVm.save()
                }
            )

            ToggleSetting(
                label = "Auto-save database",
                description = "Automatically save changes as they are made",
                checked = settingsVm.autoSave,
                onCheckedChange = {
                    settingsVm.autoSave = it
                    settingsVm.save()
                }
            )
        }

        // PRIVACY
        SettingsSection(title = "Privacy") {
            ToggleSetting(
                label = "Filter living persons in exports by default",
                description = "Omit details of living persons when exporting GEDCOM files",
                checked = settingsVm.filterLivingDefault,
                onCheckedChange = {
                    settingsVm.filterLivingDefault = it
                    settingsVm.save()
                }
            )

            ToggleSetting(
                label = "Show living person details in UI",
                description = "Display full details of living persons in the application",
                checked = settingsVm.showLivingDetails,
                onCheckedChange = {
                    settingsVm.showLivingDetails = it
                    settingsVm.save()
                }
            )
        }

        // DISPLAY
        SettingsSection(title = "Display") {
            DropdownSetting(
                label = "Date format",
                options = DateDisplayFormat.entries.map { it.label },
                selectedIndex = DateDisplayFormat.entries.indexOf(settingsVm.dateFormat),
                onSelect = { index ->
                    settingsVm.dateFormat = DateDisplayFormat.entries[index]
                    settingsVm.save()
                }
            )

            DropdownSetting(
                label = "Name format",
                options = NameDisplayFormat.entries.map { it.label },
                selectedIndex = NameDisplayFormat.entries.indexOf(settingsVm.nameFormat),
                onSelect = { index ->
                    settingsVm.nameFormat = NameDisplayFormat.entries[index]
                    settingsVm.save()
                }
            )

            ToggleSetting(
                label = "Show xref IDs in person list",
                description = "Display GEDCOM cross-reference IDs alongside names (for power users)",
                checked = settingsVm.showXrefIds,
                onCheckedChange = {
                    settingsVm.showXrefIds = it
                    settingsVm.save()
                }
            )
        }

        // ABOUT
        SettingsSection(title = "About") {
            InfoRow("App version", "1.0.0")
            InfoRow("Persons", appViewModel.personCount.toString())
            InfoRow("Families", appViewModel.familyCount.toString())
            InfoRow("Events", appViewModel.eventCount.toString())
            InfoRow("Places", appViewModel.placeCount.toString())
            InfoRow("Sources", appViewModel.sourceCount.toString())
            InfoRow("Validated", "${appViewModel.validatedCount} of ${appViewModel.personCount}")
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = title,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            content()
        }
    }
}

@Composable
private fun ToggleSetting(
    label: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = label, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Text(text = description, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

@Composable
private fun SliderSetting(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    displayValue: String,
    onValueChange: (Float) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = label, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Text(text = displayValue, fontSize = 14.sp, color = MaterialTheme.colorScheme.primary)
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun DropdownSetting(
    label: String,
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f)
        )

        Box {
            OutlinedButton(onClick = { expanded = true }) {
                Text(options.getOrElse(selectedIndex) { "Select..." })
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                options.forEachIndexed { index, option ->
                    DropdownMenuItem(
                        text = { Text(option) },
                        onClick = {
                            onSelect(index)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text = value, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}
