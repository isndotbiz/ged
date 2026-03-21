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
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gedfix.models.ResearchTask
import com.gedfix.models.TaskPriority
import com.gedfix.models.TaskStatus
import com.gedfix.ui.theme.*
import com.gedfix.viewmodel.AppViewModel
import java.time.Instant

/**
 * Research task management screen with filtering by status and priority.
 */
@Composable
fun TasksScreen(appViewModel: AppViewModel) {
    var tasks by remember { mutableStateOf(appViewModel.db.fetchAllTasks()) }
    var filterStatus by remember { mutableStateOf<TaskStatus?>(null) }
    var showEditor by remember { mutableStateOf(false) }
    var editingTask by remember { mutableStateOf<ResearchTask?>(null) }

    val displayTasks = if (filterStatus == null) tasks
    else tasks.filter { it.status == filterStatus }

    val todoCnt = tasks.count { it.status == TaskStatus.TODO }
    val inProgressCnt = tasks.count { it.status == TaskStatus.IN_PROGRESS }
    val doneCnt = tasks.count { it.status == TaskStatus.DONE }

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
                Text("Research Tasks", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Text(
                    "$todoCnt to do, $inProgressCnt in progress, $doneCnt done",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Button(onClick = {
                editingTask = null
                showEditor = true
            }) {
                Text("+ New Task")
            }
        }

        // Filter chips
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = filterStatus == null,
                onClick = { filterStatus = null },
                label = { Text("All (${tasks.size})") }
            )
            FilterChip(
                selected = filterStatus == TaskStatus.TODO,
                onClick = { filterStatus = if (filterStatus == TaskStatus.TODO) null else TaskStatus.TODO },
                label = { Text("To Do ($todoCnt)") }
            )
            FilterChip(
                selected = filterStatus == TaskStatus.IN_PROGRESS,
                onClick = { filterStatus = if (filterStatus == TaskStatus.IN_PROGRESS) null else TaskStatus.IN_PROGRESS },
                label = { Text("In Progress ($inProgressCnt)") }
            )
            FilterChip(
                selected = filterStatus == TaskStatus.DONE,
                onClick = { filterStatus = if (filterStatus == TaskStatus.DONE) null else TaskStatus.DONE },
                label = { Text("Done ($doneCnt)") }
            )
        }

        if (displayTasks.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("\u2610", fontSize = 48.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        if (tasks.isEmpty()) "No tasks yet" else "No matching tasks",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(displayTasks) { task ->
                    TaskCard(
                        task = task,
                        personName = task.personXref.let { xref ->
                            if (xref.isNotEmpty()) appViewModel.db.fetchPerson(xref)?.displayName else null
                        },
                        onStatusChange = { newStatus ->
                            val updated = task.copy(
                                status = newStatus,
                                completedAt = if (newStatus == TaskStatus.DONE) Instant.now().toString() else ""
                            )
                            appViewModel.db.insertTask(updated)
                            tasks = appViewModel.db.fetchAllTasks()
                            appViewModel.refreshCounts()
                        },
                        onEdit = {
                            editingTask = task
                            showEditor = true
                        },
                        onDelete = {
                            appViewModel.db.deleteTask(task.id)
                            tasks = appViewModel.db.fetchAllTasks()
                            appViewModel.refreshCounts()
                        }
                    )
                }
            }
        }
    }

    if (showEditor) {
        TaskEditorDialog(
            task = editingTask,
            onDismiss = { showEditor = false },
            onSave = { title, description, priority ->
                val now = Instant.now().toString()
                val task = ResearchTask(
                    id = editingTask?.id ?: kotlin.uuid.Uuid.random().toString(),
                    personXref = editingTask?.personXref ?: "",
                    title = title,
                    description = description,
                    status = editingTask?.status ?: TaskStatus.TODO,
                    priority = priority,
                    dueDate = editingTask?.dueDate ?: "",
                    createdAt = editingTask?.createdAt ?: now,
                    completedAt = editingTask?.completedAt ?: ""
                )
                appViewModel.db.insertTask(task)
                tasks = appViewModel.db.fetchAllTasks()
                appViewModel.refreshCounts()
                showEditor = false
            }
        )
    }
}

@Composable
private fun TaskCard(
    task: ResearchTask,
    personName: String?,
    onStatusChange: (TaskStatus) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val priorityColor = when (task.priority) {
        TaskPriority.HIGH -> TaskHighColor
        TaskPriority.MEDIUM -> TaskMediumColor
        TaskPriority.LOW -> TaskLowColor
    }
    val isDone = task.status == TaskStatus.DONE

    Surface(
        shape = RoundedCornerShape(12.dp),
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Status checkbox
            Checkbox(
                checked = isDone,
                onCheckedChange = { checked ->
                    onStatusChange(if (checked) TaskStatus.DONE else TaskStatus.TODO)
                }
            )

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = task.title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    textDecoration = if (isDone) TextDecoration.LineThrough else TextDecoration.None,
                    color = if (isDone) MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.onSurface
                )
                if (task.description.isNotEmpty()) {
                    Text(
                        text = task.description,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    Text(
                        text = task.priority.icon + " " + task.priority.label,
                        fontSize = 11.sp,
                        color = priorityColor,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = task.status.icon + " " + task.status.label,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (personName != null) {
                        Text(
                            text = personName,
                            fontSize = 11.sp,
                            color = PeopleIconColor
                        )
                    }
                }
            }

            Column {
                TextButton(onClick = onEdit) { Text("Edit", fontSize = 12.sp) }
                TextButton(
                    onClick = onDelete,
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("Delete", fontSize = 12.sp) }
            }
        }
    }
}

@Composable
private fun TaskEditorDialog(
    task: ResearchTask?,
    onDismiss: () -> Unit,
    onSave: (title: String, description: String, priority: TaskPriority) -> Unit
) {
    var title by remember { mutableStateOf(task?.title ?: "") }
    var description by remember { mutableStateOf(task?.description ?: "") }
    var priority by remember { mutableStateOf(task?.priority ?: TaskPriority.MEDIUM) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (task == null) "New Task" else "Edit Task") },
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
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 5
                )
                Text("Priority", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TaskPriority.entries.forEach { p ->
                        FilterChip(
                            selected = priority == p,
                            onClick = { priority = p },
                            label = { Text(p.label) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(title, description, priority) },
                enabled = title.isNotEmpty()
            ) { Text("Save") }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
