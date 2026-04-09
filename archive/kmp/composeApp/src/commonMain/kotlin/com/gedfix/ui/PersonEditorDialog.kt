package com.gedfix.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gedfix.models.GedcomPerson

/**
 * Edit person dialog (name, sex, living status).
 */
@Composable
fun PersonEditorDialog(
    person: GedcomPerson?,
    onSave: (givenName: String, surname: String, suffix: String, sex: String, isLiving: Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    var givenName by remember { mutableStateOf(person?.givenName ?: "") }
    var surname by remember { mutableStateOf(person?.surname ?: "") }
    var suffix by remember { mutableStateOf(person?.suffix ?: "") }
    var sex by remember { mutableStateOf(person?.sex ?: "U") }
    var isLiving by remember { mutableStateOf(person?.isLiving ?: false) }
    var validationError by remember { mutableStateOf<String?>(null) }

    val isNew = person == null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isNew) "New Person" else "Edit Person") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.width(360.dp)
            ) {
                // Name fields
                Text("Name", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                OutlinedTextField(
                    value = givenName,
                    onValueChange = { givenName = it },
                    label = { Text("Given Name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = surname,
                    onValueChange = { surname = it },
                    label = { Text("Surname") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = suffix,
                    onValueChange = { suffix = it },
                    label = { Text("Suffix (Jr., Sr., III, etc.)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                // Sex picker
                Text("Sex", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("M" to "Male", "F" to "Female", "U" to "Unknown").forEach { (code, label) ->
                        FilterChip(
                            selected = sex == code,
                            onClick = { sex = code },
                            label = { Text(label) }
                        )
                    }
                }

                // Living status
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Checkbox(
                        checked = isLiving,
                        onCheckedChange = { isLiving = it }
                    )
                    Text("Living Person", fontSize = 14.sp)
                }

                // Validation error
                if (validationError != null) {
                    Text(
                        text = validationError!!,
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 13.sp
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val trimGiven = givenName.trim()
                    val trimSurname = surname.trim()
                    if (trimGiven.isEmpty() && trimSurname.isEmpty()) {
                        validationError = "At least a given name or surname is required."
                        return@Button
                    }
                    validationError = null
                    onSave(trimGiven, trimSurname, suffix.trim(), sex, isLiving)
                },
                enabled = givenName.trim().isNotEmpty() || surname.trim().isNotEmpty()
            ) {
                Text(if (isNew) "Create" else "Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
