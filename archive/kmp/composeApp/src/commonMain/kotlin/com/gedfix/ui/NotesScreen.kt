package com.gedfix.ui

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
import com.gedfix.models.ResearchNote
import com.gedfix.ui.theme.*
import com.gedfix.viewmodel.AppViewModel
import java.time.Instant

/**
 * Global notes screen showing all research notes across the tree.
 * Supports creating, editing, and deleting notes.
 */
@Composable
fun NotesScreen(appViewModel: AppViewModel) {
    var notes by remember { mutableStateOf(appViewModel.db.fetchAllNotes()) }
    var searchQuery by remember { mutableStateOf("") }
    var showEditor by remember { mutableStateOf(false) }
    var editingNote by remember { mutableStateOf<ResearchNote?>(null) }

    val displayNotes = if (searchQuery.isEmpty()) notes
    else appViewModel.db.searchNotes(searchQuery)

    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Notes", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Text(
                    "${notes.size} research notes",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Button(onClick = {
                editingNote = null
                showEditor = true
            }) {
                Text("+ New Note")
            }
        }

        // Search
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            label = { Text("Search notes...") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        if (displayNotes.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("\u2709", fontSize = 48.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        if (searchQuery.isEmpty()) "No notes yet" else "No matching notes",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (searchQuery.isEmpty()) {
                        Text(
                            "Create research notes to track your genealogy findings.",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(displayNotes) { note ->
                    NoteCard(
                        note = note,
                        personName = note.ownerXref.let { xref ->
                            if (xref.isNotEmpty()) appViewModel.db.fetchPerson(xref)?.displayName ?: xref
                            else null
                        },
                        onEdit = {
                            editingNote = note
                            showEditor = true
                        },
                        onDelete = {
                            appViewModel.db.deleteNote(note.id)
                            notes = appViewModel.db.fetchAllNotes()
                            appViewModel.refreshCounts()
                        }
                    )
                }
            }
        }
    }

    // Note editor dialog
    if (showEditor) {
        NoteEditorDialog(
            note = editingNote,
            onDismiss = { showEditor = false },
            onSave = { title, content, ownerXref, ownerType ->
                val now = Instant.now().toString()
                val note = ResearchNote(
                    id = editingNote?.id ?: kotlin.uuid.Uuid.random().toString(),
                    ownerXref = ownerXref,
                    ownerType = ownerType,
                    title = title,
                    content = content,
                    createdAt = editingNote?.createdAt ?: now,
                    updatedAt = now
                )
                appViewModel.db.insertNote(note)
                notes = appViewModel.db.fetchAllNotes()
                appViewModel.refreshCounts()
                showEditor = false
            }
        )
    }
}

@Composable
private fun NoteCard(
    note: ResearchNote,
    personName: String?,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }

    Surface(
        shape = RoundedCornerShape(12.dp),
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = note.title.ifEmpty { "(Untitled)" },
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (personName != null) {
                        Text(
                            text = "${note.displayOwnerType}: $personName",
                            fontSize = 12.sp,
                            color = PeopleIconColor,
                            fontWeight = FontWeight.Medium
                        )
                    } else if (note.isGlobal) {
                        Text(
                            text = "Global Note",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Text(
                    text = formatTimestamp(note.updatedAt),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (note.content.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = note.previewContent,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onEdit) { Text("Edit") }
                TextButton(
                    onClick = { showDeleteConfirm = true },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("Delete") }
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete Note?") },
            text = { Text("This will permanently delete \"${note.title.ifEmpty { "(Untitled)" }}\".") },
            confirmButton = {
                Button(
                    onClick = { showDeleteConfirm = false; onDelete() },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Delete") }
            },
            dismissButton = {
                OutlinedButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun NoteEditorDialog(
    note: ResearchNote?,
    onDismiss: () -> Unit,
    onSave: (title: String, content: String, ownerXref: String, ownerType: String) -> Unit
) {
    var title by remember { mutableStateOf(note?.title ?: "") }
    var content by remember { mutableStateOf(note?.content ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (note == null) "New Note" else "Edit Note") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    label = { Text("Content") },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 200.dp),
                    maxLines = 20
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(title, content, note?.ownerXref ?: "", note?.ownerType ?: "GLOBAL")
                },
                enabled = title.isNotEmpty() || content.isNotEmpty()
            ) { Text("Save") }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

private fun formatTimestamp(iso: String): String {
    return if (iso.length >= 10) iso.substring(0, 10) else iso
}
