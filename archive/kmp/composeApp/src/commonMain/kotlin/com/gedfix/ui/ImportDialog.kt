package com.gedfix.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Import dialog - accepts GEDCOM text content and imports it.
 * On desktop, this would integrate with file picker; here we provide a text area
 * as a cross-platform fallback. Platform-specific file pickers can be layered on top.
 */
@Composable
fun ImportDialog(
    onDismiss: () -> Unit,
    onImport: (String) -> Unit,
    isImporting: Boolean,
    error: String?
) {
    var gedcomText by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Import GEDCOM File", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text(
                    "Paste GEDCOM 5.5.1 content below or use your platform's file picker.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.width(500.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = gedcomText,
                    onValueChange = { gedcomText = it },
                    label = { Text("GEDCOM Content") },
                    modifier = Modifier.fillMaxWidth().height(200.dp),
                    maxLines = 20,
                    placeholder = { Text("0 HEAD\n1 GEDC\n2 VERS 5.5.1\n0 @I1@ INDI\n1 NAME John /Smith/\n...") }
                )

                if (isImporting) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Text("Importing...", fontSize = 14.sp)
                    }
                }

                if (error != null) {
                    Text(
                        text = "Error: $error",
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 13.sp
                    )
                }

                // Line count preview
                if (gedcomText.isNotEmpty()) {
                    val lineCount = gedcomText.lines().size
                    Text(
                        text = "$lineCount lines of GEDCOM data",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onImport(gedcomText) },
                enabled = gedcomText.isNotEmpty() && !isImporting
            ) {
                Text("Import")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
