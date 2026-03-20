package com.gedfix.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gedfix.models.GedcomEvent

/**
 * Edit event dialog (type picker, date, place, description).
 */
@Composable
fun EventEditorDialog(
    event: GedcomEvent?,
    ownerXref: String,
    ownerType: String,
    onSave: (eventType: String, dateValue: String, place: String, description: String) -> Unit,
    onDismiss: () -> Unit,
    onDelete: (() -> Unit)?
) {
    var eventType by remember { mutableStateOf(event?.eventType ?: "BIRT") }
    var dateValue by remember { mutableStateOf(event?.dateValue ?: "") }
    var place by remember { mutableStateOf(event?.place ?: "") }
    var description by remember { mutableStateOf(event?.description ?: "") }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf(false) }

    val isNew = event == null

    val eventTypes = listOf(
        "BIRT" to "Birth",
        "DEAT" to "Death",
        "MARR" to "Marriage",
        "BURI" to "Burial",
        "CHR" to "Christening",
        "BAPM" to "Baptism",
        "RESI" to "Residence",
        "CENS" to "Census",
        "IMMI" to "Immigration",
        "EMIG" to "Emigration",
        "NATU" to "Naturalization",
        "GRAD" to "Graduation",
        "RETI" to "Retirement",
        "PROB" to "Probate",
        "WILL" to "Will",
        "DIV" to "Divorce"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isNew) "New Event" else "Edit Event") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.width(400.dp)
            ) {
                // Event type dropdown
                Text("Event Type", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = it }
                ) {
                    OutlinedTextField(
                        value = eventTypes.firstOrNull { it.first == eventType }?.second ?: eventType,
                        onValueChange = {},
                        readOnly = true,
                        modifier = Modifier.menuAnchor().fillMaxWidth(),
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) }
                    )
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        for ((code, label) in eventTypes) {
                            DropdownMenuItem(
                                text = { Text(label) },
                                onClick = {
                                    eventType = code
                                    expanded = false
                                }
                            )
                        }
                    }
                }

                // Details
                Text("Details", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                OutlinedTextField(
                    value = dateValue,
                    onValueChange = { dateValue = it },
                    label = { Text("Date (e.g. 15 MAR 1892)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = place,
                    onValueChange = { place = it },
                    label = { Text("Place") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description / Notes") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!isNew && onDelete != null) {
                    TextButton(
                        onClick = { showDeleteConfirm = true },
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text("Delete")
                    }
                }
                Button(onClick = {
                    onSave(eventType, dateValue.trim(), place.trim(), description.trim())
                }) {
                    Text(if (isNew) "Add" else "Save")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )

    // Delete confirmation
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete Event?") },
            text = { Text("This event will be permanently removed.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        onDelete?.invoke()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
