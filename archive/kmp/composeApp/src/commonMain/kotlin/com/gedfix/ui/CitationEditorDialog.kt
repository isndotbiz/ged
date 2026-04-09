package com.gedfix.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.gedfix.models.*
import com.gedfix.ui.theme.*

/**
 * Dialog for creating/editing a citation with template support.
 * Allows selecting a source, picking a template, filling in fields,
 * and setting quality level.
 */
@Composable
fun CitationEditorDialog(
    citation: Citation?,
    personXref: String,
    eventId: String,
    sources: List<GedcomSource>,
    onSave: (Citation) -> Unit,
    onDismiss: () -> Unit,
    onDelete: (() -> Unit)? = null
) {
    val isNew = citation == null

    var selectedSourceXref by remember { mutableStateOf(citation?.sourceXref ?: "") }
    var selectedTemplateId by remember { mutableStateOf<String?>(null) }
    var page by remember { mutableStateOf(citation?.page ?: "") }
    var quality by remember { mutableStateOf(citation?.quality ?: CitationQuality.UNKNOWN) }
    var text by remember { mutableStateOf(citation?.text ?: "") }
    var note by remember { mutableStateOf(citation?.note ?: "") }
    var templateFieldValues by remember { mutableStateOf<Map<String, String>>(emptyMap()) }

    var sourceDropdownExpanded by remember { mutableStateOf(false) }
    var categoryDropdownExpanded by remember { mutableStateOf(false) }
    var templateDropdownExpanded by remember { mutableStateOf(false) }
    var selectedCategory by remember { mutableStateOf<String?>(null) }

    val selectedTemplate = selectedTemplateId?.let { CitationTemplates.byId(it) }
    val scrollState = rememberScrollState()

    // Generate preview text from template
    val previewText = if (selectedTemplate != null && templateFieldValues.isNotEmpty()) {
        selectedTemplate.format(templateFieldValues)
    } else {
        text
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier.width(560.dp).heightIn(max = 700.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Title
                Text(
                    text = if (isNew) "Add Citation" else "Edit Citation",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )

                Column(
                    modifier = Modifier.weight(1f, fill = false).verticalScroll(scrollState),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Source selector
                    Text("Source", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    ExposedDropdownMenuBox(
                        expanded = sourceDropdownExpanded,
                        onExpandedChange = { sourceDropdownExpanded = it }
                    ) {
                        OutlinedTextField(
                            value = sources.firstOrNull { it.xref == selectedSourceXref }?.title ?: "(Select a source)",
                            onValueChange = {},
                            readOnly = true,
                            modifier = Modifier.menuAnchor().fillMaxWidth(),
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = sourceDropdownExpanded) },
                            singleLine = true
                        )
                        ExposedDropdownMenu(
                            expanded = sourceDropdownExpanded,
                            onDismissRequest = { sourceDropdownExpanded = false }
                        ) {
                            for (source in sources) {
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text(source.title.ifEmpty { "(Untitled)" }, fontSize = 13.sp)
                                            if (source.author.isNotEmpty()) {
                                                Text(source.author, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                        }
                                    },
                                    onClick = {
                                        selectedSourceXref = source.xref
                                        sourceDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    HorizontalDivider()

                    // Template selector
                    Text("Citation Template (Optional)", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)

                    // Category dropdown
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ExposedDropdownMenuBox(
                            expanded = categoryDropdownExpanded,
                            onExpandedChange = { categoryDropdownExpanded = it },
                            modifier = Modifier.weight(1f)
                        ) {
                            OutlinedTextField(
                                value = selectedCategory ?: "(Category)",
                                onValueChange = {},
                                readOnly = true,
                                modifier = Modifier.menuAnchor().fillMaxWidth(),
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryDropdownExpanded) },
                                singleLine = true,
                                textStyle = LocalTextStyle.current.copy(fontSize = 13.sp)
                            )
                            ExposedDropdownMenu(
                                expanded = categoryDropdownExpanded,
                                onDismissRequest = { categoryDropdownExpanded = false }
                            ) {
                                for (category in CitationTemplates.categories) {
                                    DropdownMenuItem(
                                        text = { Text(category, fontSize = 13.sp) },
                                        onClick = {
                                            selectedCategory = category
                                            selectedTemplateId = null
                                            templateFieldValues = emptyMap()
                                            categoryDropdownExpanded = false
                                        }
                                    )
                                }
                            }
                        }

                        // Template dropdown (filtered by category)
                        if (selectedCategory != null) {
                            ExposedDropdownMenuBox(
                                expanded = templateDropdownExpanded,
                                onExpandedChange = { templateDropdownExpanded = it },
                                modifier = Modifier.weight(1f)
                            ) {
                                OutlinedTextField(
                                    value = selectedTemplate?.name ?: "(Template)",
                                    onValueChange = {},
                                    readOnly = true,
                                    modifier = Modifier.menuAnchor().fillMaxWidth(),
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = templateDropdownExpanded) },
                                    singleLine = true,
                                    textStyle = LocalTextStyle.current.copy(fontSize = 13.sp)
                                )
                                ExposedDropdownMenu(
                                    expanded = templateDropdownExpanded,
                                    onDismissRequest = { templateDropdownExpanded = false }
                                ) {
                                    for (template in CitationTemplates.byCategory(selectedCategory!!)) {
                                        DropdownMenuItem(
                                            text = { Text(template.name, fontSize = 13.sp) },
                                            onClick = {
                                                selectedTemplateId = template.id
                                                templateFieldValues = emptyMap()
                                                templateDropdownExpanded = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Template fields
                    if (selectedTemplate != null) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    "Template Fields",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                for (field in selectedTemplate.fields) {
                                    OutlinedTextField(
                                        value = templateFieldValues[field.name] ?: "",
                                        onValueChange = { value ->
                                            templateFieldValues = templateFieldValues + (field.name to value)
                                        },
                                        label = {
                                            Text(
                                                "${field.label}${if (field.required) " *" else ""}",
                                                fontSize = 12.sp
                                            )
                                        },
                                        placeholder = { Text(field.placeholder, fontSize = 12.sp) },
                                        modifier = Modifier.fillMaxWidth(),
                                        singleLine = true,
                                        textStyle = LocalTextStyle.current.copy(fontSize = 13.sp)
                                    )
                                }

                                // Apply template button
                                OutlinedButton(
                                    onClick = {
                                        text = selectedTemplate.format(templateFieldValues)
                                    },
                                    modifier = Modifier.align(Alignment.End)
                                ) {
                                    Text("Apply to Citation Text", fontSize = 12.sp)
                                }
                            }
                        }
                    }

                    HorizontalDivider()

                    // Page/Volume
                    OutlinedTextField(
                        value = page,
                        onValueChange = { page = it },
                        label = { Text("Page / Volume / Item") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    // Quality selector
                    Text("Source Quality", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        for (q in CitationQuality.entries) {
                            val isSelected = quality == q
                            val qualityColor = when (q) {
                                CitationQuality.PRIMARY -> CitationPrimaryColor
                                CitationQuality.SECONDARY -> CitationSecondaryColor
                                CitationQuality.QUESTIONABLE -> CitationQuestionableColor
                                CitationQuality.UNKNOWN -> CitationUnknownColor
                            }
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { quality = q }
                                    .then(
                                        if (isSelected) Modifier.border(
                                            2.dp,
                                            qualityColor,
                                            RoundedCornerShape(8.dp)
                                        ) else Modifier
                                    ),
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSelected) qualityColor.copy(alpha = 0.1f)
                                else MaterialTheme.colorScheme.surfaceVariant
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    RadioButton(
                                        selected = isSelected,
                                        onClick = { quality = q },
                                        colors = RadioButtonDefaults.colors(selectedColor = qualityColor)
                                    )
                                    Text(q.display, fontSize = 13.sp)
                                }
                            }
                        }
                    }

                    // Direct text
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it },
                        label = { Text("Citation Text") },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 80.dp),
                        maxLines = 5
                    )

                    // Note
                    OutlinedTextField(
                        value = note,
                        onValueChange = { note = it },
                        label = { Text("Researcher's Note") },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 3
                    )

                    // Preview
                    if (previewText.isNotEmpty()) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp).fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text("Preview", fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(
                                    previewText,
                                    fontSize = 13.sp,
                                    fontStyle = FontStyle.Italic,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }

                // Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
                ) {
                    if (!isNew && onDelete != null) {
                        TextButton(
                            onClick = onDelete,
                            colors = ButtonDefaults.textButtonColors(contentColor = CriticalColor)
                        ) {
                            Text("Delete")
                        }
                        Spacer(Modifier.weight(1f))
                    }
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    Button(
                        onClick = {
                            val id = citation?.id ?: kotlin.uuid.Uuid.random().toString()
                            onSave(
                                Citation(
                                    id = id,
                                    sourceXref = selectedSourceXref,
                                    personXref = personXref,
                                    eventId = eventId,
                                    page = page.trim(),
                                    quality = quality,
                                    text = text.trim(),
                                    note = note.trim()
                                )
                            )
                        }
                    ) {
                        Text(if (isNew) "Add Citation" else "Save")
                    }
                }
            }
        }
    }
}
