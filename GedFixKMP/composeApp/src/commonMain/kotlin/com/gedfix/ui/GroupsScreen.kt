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
import com.gedfix.models.PersonGroup
import com.gedfix.ui.theme.*
import com.gedfix.viewmodel.AppViewModel
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
@Composable
fun GroupsScreen(appViewModel: AppViewModel) {
    var groups by remember { mutableStateOf(appViewModel.db.fetchAllGroups()) }
    var showEditor by remember { mutableStateOf(false) }
    var selectedGroup by remember { mutableStateOf<PersonGroup?>(null) }
    var groupMembers by remember { mutableStateOf<List<String>>(emptyList()) }

    fun refresh() {
        groups = appViewModel.db.fetchAllGroups()
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
                Text("Groups", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Text(
                    "Organize people into custom groups for research and analysis",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Button(onClick = { showEditor = true }) {
                Text("New Group")
            }
        }

        if (groups.isEmpty()) {
            Surface(shape = RoundedCornerShape(12.dp), tonalElevation = 1.dp, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("No groups yet", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Create groups to organize people (e.g., \"Civil War Veterans\", \"Unverified\", \"Brick Wall\").", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            for (group in groups) {
                val memberCount = appViewModel.db.groupMemberCount(group.id)
                GroupCard(
                    group = group,
                    memberCount = memberCount,
                    isSelected = selectedGroup?.id == group.id,
                    onClick = {
                        selectedGroup = if (selectedGroup?.id == group.id) null else group
                        if (selectedGroup != null) {
                            groupMembers = appViewModel.db.fetchMembersOfGroup(group.id)
                        }
                    },
                    onDelete = {
                        appViewModel.db.deleteGroup(group.id)
                        if (selectedGroup?.id == group.id) selectedGroup = null
                        refresh()
                    }
                )

                // Show members when expanded
                if (selectedGroup?.id == group.id && groupMembers.isNotEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                        modifier = Modifier.fillMaxWidth().padding(start = 16.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Members:", fontWeight = FontWeight.Medium, fontSize = 13.sp)
                            for (xref in groupMembers) {
                                val person = appViewModel.db.fetchPerson(xref)
                                if (person != null) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(person.displayName, fontSize = 13.sp)
                                        TextButton(onClick = {
                                            appViewModel.db.removeMemberFromGroup(group.id, xref)
                                            groupMembers = appViewModel.db.fetchMembersOfGroup(group.id)
                                            refresh()
                                        }) {
                                            Text("Remove", fontSize = 11.sp, color = CriticalColor)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
    }

    if (showEditor) {
        GroupEditorDialog(
            onDismiss = { showEditor = false },
            onSave = { name, description, color ->
                appViewModel.db.insertGroup(PersonGroup(
                    id = Uuid.random().toString(),
                    name = name,
                    color = color,
                    description = description,
                    createdAt = java.time.Instant.now().toString()
                ))
                refresh()
                showEditor = false
            }
        )
    }
}

@Composable
private fun GroupCard(
    group: PersonGroup,
    memberCount: Int,
    isSelected: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        tonalElevation = if (isSelected) 3.dp else 1.dp,
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (group.color.isNotEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = parseHexColor(group.color),
                        modifier = Modifier.size(24.dp)
                    ) {}
                }
                Column {
                    Text(group.name, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    if (group.description.isNotEmpty()) {
                        Text(group.description, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.08f)) {
                    Text("$memberCount", fontSize = 12.sp, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
                }
                TextButton(onClick = onDelete) {
                    Text("Delete", fontSize = 11.sp, color = CriticalColor)
                }
            }
        }
    }
}

@Composable
private fun GroupEditorDialog(
    onDismiss: () -> Unit,
    onSave: (name: String, description: String, color: String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var color by remember { mutableStateOf("") }

    val presetColors = listOf("#4A90D9", "#D94A8C", "#5AB078", "#E8944A", "#8E7CC3", "#5AAFA5", "#E05A4F", "#E8B44A")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New Group") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Group Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = description, onValueChange = { description = it }, label = { Text("Description (optional)") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
                Text("Color:", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (preset in presetColors) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = parseHexColor(preset),
                            modifier = Modifier.size(32.dp),
                            onClick = { color = preset }
                        ) {
                            if (color == preset) {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Text("\u2713", color = androidx.compose.ui.graphics.Color.White, fontSize = 14.sp)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onSave(name, description, color) }, enabled = name.isNotBlank()) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

private fun parseHexColor(hex: String): androidx.compose.ui.graphics.Color {
    return try {
        val clean = hex.removePrefix("#")
        val colorLong = clean.toLong(16) or 0xFF000000L
        androidx.compose.ui.graphics.Color(colorLong.toInt())
    } catch (_: Exception) {
        GroupsIconColor
    }
}
