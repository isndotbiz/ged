package com.gedfix.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gedfix.models.*
import com.gedfix.ui.components.eventTypeIcon
import com.gedfix.ui.theme.*
import com.gedfix.viewmodel.AIViewModel
import com.gedfix.viewmodel.PersonViewModel
import java.awt.Desktop
import java.net.URI

/**
 * Person detail view with events, families, parents, edit/delete buttons.
 */
@Composable
fun PersonDetailScreen(
    person: GedcomPerson,
    personViewModel: PersonViewModel,
    aiViewModel: AIViewModel? = null,
    modifier: Modifier = Modifier
) {
    val events = personViewModel.fetchEvents(person.xref)
    val spouseFamilies = personViewModel.fetchFamiliesAsSpouse(person.xref)
    val parentFamilies = personViewModel.fetchFamiliesAsChild(person.xref)
    val citations = personViewModel.fetchCitationsForPerson(person.xref)
    val sources = personViewModel.fetchAllSources()
    var showCitationDialog by remember { mutableStateOf(false) }
    var citationEventId by remember { mutableStateOf("") }
    var editingCitation by remember { mutableStateOf<Citation?>(null) }
    val scrollState = rememberScrollState()

    val sexColor = when (person.sex) {
        "M" -> MaleColor
        "F" -> FemaleColor
        else -> UnknownGenderColor
    }
    val sexBgColor = when (person.sex) {
        "M" -> MaleBgColor
        "F" -> FemaleBgColor
        else -> UnknownGenderBgColor
    }

    Column(
        modifier = modifier
            .verticalScroll(scrollState)
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // Header with toolbar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            // Person info
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(sexBgColor),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = person.initials.ifEmpty { "?" },
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = sexColor
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = person.displayName.ifEmpty { "(Unknown)" },
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold
                        )
                        if (person.isLiving) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = LivingBadgeBg
                            ) {
                                Text(
                                    text = "Living",
                                    fontSize = 12.sp,
                                    color = LivingBadgeColor,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }
                        // Validation badge
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (person.isValidated) ValidatedBgColor else UnvalidatedBgColor
                        ) {
                            Text(
                                text = if (person.isValidated) "\u2713 Validated" else "\u26A0 Needs Source",
                                fontSize = 12.sp,
                                color = if (person.isValidated) ValidatedColor else UnvalidatedColor,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }

                    // Source and media counts
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text(
                            "${person.sourceCount} source${if (person.sourceCount != 1) "s" else ""}",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            "${person.mediaCount} media",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        val birth = events.firstOrNull { it.eventType == "BIRT" }
                        val death = events.firstOrNull { it.eventType == "DEAT" }
                        if (birth != null) {
                            Text(
                                "b. ${birth.dateValue}${if (birth.place.isNotEmpty()) ", ${birth.place}" else ""}",
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (death != null) {
                            Text(
                                "d. ${death.dateValue}${if (death.place.isNotEmpty()) ", ${death.place}" else ""}",
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Text(
                        text = person.xref,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }
            }

            // Edit/Delete buttons
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { personViewModel.showEditPersonDialog = true }) {
                    Text("Edit")
                }
                OutlinedButton(
                    onClick = { personViewModel.showDeleteConfirm = true },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = CriticalColor)
                ) {
                    Text("Delete")
                }
            }
        }

        HorizontalDivider()

        // AI Analysis section (only shown when AI is configured)
        if (aiViewModel != null) {
            AskAISection(person = person, events = events, aiViewModel = aiViewModel)
        }

        // Validation callout for unvalidated persons
        if (!person.isValidated) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = UnvalidatedBgColor
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "\u26A0 No Source Citations",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = UnvalidatedColor
                    )
                    Text(
                        "This person has no source citations. Add a source to validate this record and improve your tree's research quality.",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            // Research suggestions
            val suggestions = ResearchSuggestionEngine.suggestionsFor(person, events)
            if (suggestions.isNotEmpty()) {
                var expandedResearch by remember { mutableStateOf(false) }
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Research Suggestions",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            TextButton(onClick = { expandedResearch = !expandedResearch }) {
                                Text(if (expandedResearch) "Collapse" else "Expand (${suggestions.size})")
                            }
                        }

                        if (expandedResearch) {
                            for ((index, suggestion) in suggestions.withIndex()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Text(
                                        text = suggestion.icon,
                                        fontSize = 16.sp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Text(suggestion.title, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                                        Text(
                                            suggestion.description,
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            suggestion.source,
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                        )
                                    }
                                    OutlinedButton(
                                        onClick = {
                                            try {
                                                Desktop.getDesktop().browse(URI(suggestion.url))
                                            } catch (_: Exception) { }
                                        }
                                    ) {
                                        Text("Search", fontSize = 12.sp)
                                    }
                                }
                                if (index < suggestions.size - 1) {
                                    HorizontalDivider()
                                }
                            }
                        }
                    }
                }
            }
        }

        // Photos & Media section
        PersonMediaSection(person = person, personViewModel = personViewModel)

        // Events section
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Events", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    TextButton(onClick = { personViewModel.showAddEventDialog = true }) {
                        Text("+ Add Event")
                    }
                }

                if (events.isEmpty()) {
                    Text(
                        "No events recorded.",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                for ((index, event) in events.withIndex()) {
                    val eventCitations = citations.filter { it.eventId == event.id }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            text = eventTypeIcon(event.eventType),
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.primary
                        )

                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(event.displayType, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                                if (eventCitations.isNotEmpty()) {
                                    val bestQuality = eventCitations.minByOrNull { it.quality.ordinal }?.quality
                                    val badgeColor = when (bestQuality) {
                                        CitationQuality.PRIMARY -> CitationPrimaryColor
                                        CitationQuality.SECONDARY -> CitationSecondaryColor
                                        CitationQuality.QUESTIONABLE -> CitationQuestionableColor
                                        else -> CitationUnknownColor
                                    }
                                    val badgeBg = when (bestQuality) {
                                        CitationQuality.PRIMARY -> CitationPrimaryBg
                                        CitationQuality.SECONDARY -> CitationSecondaryBg
                                        CitationQuality.QUESTIONABLE -> CitationQuestionableBg
                                        else -> CitationUnknownBg
                                    }
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = badgeBg
                                    ) {
                                        Text(
                                            "${eventCitations.size} cite${if (eventCitations.size != 1) "s" else ""}",
                                            fontSize = 10.sp,
                                            color = badgeColor,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                            if (event.dateValue.isNotEmpty()) {
                                Text(event.dateValue, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (event.place.isNotEmpty()) {
                                Text("\u2316 ${event.place}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }

                        TextButton(onClick = {
                            citationEventId = event.id
                            showCitationDialog = true
                        }) {
                            Text("+Cite", fontSize = 11.sp)
                        }
                        TextButton(onClick = { personViewModel.editingEvent = event }) {
                            Text("\u270E", fontSize = 14.sp)
                        }
                    }

                    if (index < events.size - 1) {
                        HorizontalDivider(modifier = Modifier.padding(start = 36.dp))
                    }
                }
            }
        }

        // Spouse families
        if (spouseFamilies.isNotEmpty()) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("\u2665 Families", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)

                    for (family in spouseFamilies) {
                        val spouseXref = if (family.partner1Xref == person.xref) family.partner2Xref else family.partner1Xref
                        val spouse = personViewModel.fetchPerson(spouseXref)
                        val marriageEvents = personViewModel.fetchEvents(family.xref).filter { it.eventType == "MARR" }
                        val childLinks = personViewModel.fetchChildLinks(family.xref)

                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            if (spouse != null) {
                                Text("Spouse: ${spouse.displayName}", fontWeight = FontWeight.Medium)
                            }
                            for (marr in marriageEvents) {
                                Text(
                                    "Married ${marr.dateValue}${if (marr.place.isNotEmpty()) " in ${marr.place}" else ""}",
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (childLinks.isNotEmpty()) {
                                Text("Children:", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                for (link in childLinks) {
                                    val child = personViewModel.fetchPerson(link.childXref)
                                    if (child != null) {
                                        Text(
                                            "  \u2192 ${child.displayName}",
                                            fontSize = 13.sp,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Parents
        if (parentFamilies.isNotEmpty()) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Parents", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    for (family in parentFamilies) {
                        val father = personViewModel.fetchPerson(family.partner1Xref)
                        val mother = personViewModel.fetchPerson(family.partner2Xref)
                        if (father != null) {
                            Text("Father: ${father.displayName}", color = MaleColor)
                        }
                        if (mother != null) {
                            Text("Mother: ${mother.displayName}", color = FemaleColor)
                        }
                    }
                }
            }
        }

        // Citations section
        if (citations.isNotEmpty()) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Citations (${citations.size})",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        TextButton(onClick = {
                            citationEventId = ""
                            showCitationDialog = true
                        }) {
                            Text("+ Add Citation")
                        }
                    }

                    for ((index, citation) in citations.withIndex()) {
                        val qualityColor = when (citation.quality) {
                            CitationQuality.PRIMARY -> CitationPrimaryColor
                            CitationQuality.SECONDARY -> CitationSecondaryColor
                            CitationQuality.QUESTIONABLE -> CitationQuestionableColor
                            CitationQuality.UNKNOWN -> CitationUnknownColor
                        }
                        val qualityBg = when (citation.quality) {
                            CitationQuality.PRIMARY -> CitationPrimaryBg
                            CitationQuality.SECONDARY -> CitationSecondaryBg
                            CitationQuality.QUESTIONABLE -> CitationQuestionableBg
                            CitationQuality.UNKNOWN -> CitationUnknownBg
                        }
                        val sourceTitle = sources.firstOrNull { it.xref == citation.sourceXref }?.title ?: citation.sourceXref
                        val eventLabel = events.firstOrNull { it.id == citation.eventId }?.displayType ?: ""

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            // Quality indicator
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = qualityBg
                            ) {
                                Text(
                                    when (citation.quality) {
                                        CitationQuality.PRIMARY -> "\u2713"
                                        CitationQuality.SECONDARY -> "\u25CB"
                                        CitationQuality.QUESTIONABLE -> "?"
                                        CitationQuality.UNKNOWN -> "-"
                                    },
                                    fontSize = 12.sp,
                                    color = qualityColor,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }

                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Text(
                                    sourceTitle.ifEmpty { "(No source)" },
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 13.sp
                                )
                                if (eventLabel.isNotEmpty()) {
                                    Text(
                                        "Event: $eventLabel",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (citation.page.isNotEmpty()) {
                                    Text(
                                        "p. ${citation.page}",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (citation.text.isNotEmpty()) {
                                    Text(
                                        citation.text,
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 2
                                    )
                                }
                                Text(
                                    citation.quality.display,
                                    fontSize = 10.sp,
                                    color = qualityColor
                                )
                            }

                            TextButton(onClick = {
                                editingCitation = citation
                                citationEventId = citation.eventId
                            }) {
                                Text("\u270E", fontSize = 14.sp)
                            }
                        }

                        if (index < citations.size - 1) {
                            HorizontalDivider(modifier = Modifier.padding(start = 36.dp))
                        }
                    }
                }
            }
        }
    }

    // Citation editor dialog
    if (showCitationDialog || editingCitation != null) {
        CitationEditorDialog(
            citation = editingCitation,
            personXref = person.xref,
            eventId = citationEventId,
            sources = sources,
            onSave = { citation ->
                personViewModel.saveCitation(citation)
                showCitationDialog = false
                editingCitation = null
            },
            onDismiss = {
                showCitationDialog = false
                editingCitation = null
            },
            onDelete = if (editingCitation != null) {
                {
                    personViewModel.deleteCitation(editingCitation!!.id)
                    editingCitation = null
                }
            } else null
        )
    }

    // Edit person dialog
    if (personViewModel.showEditPersonDialog) {
        PersonEditorDialog(
            person = person,
            onSave = { givenName, surname, suffix, sex, isLiving ->
                val updated = person.copy(
                    givenName = givenName.trim(),
                    surname = surname.trim(),
                    suffix = suffix.trim(),
                    sex = sex,
                    isLiving = isLiving
                )
                personViewModel.updatePerson(updated)
            },
            onDismiss = { personViewModel.showEditPersonDialog = false }
        )
    }

    // Delete confirmation
    if (personViewModel.showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { personViewModel.showDeleteConfirm = false },
            title = { Text("Delete Person?") },
            text = { Text("This will permanently delete ${person.displayName} and remove them from all families. Their events will also be deleted.") },
            confirmButton = {
                TextButton(
                    onClick = { personViewModel.deletePerson(person.xref) },
                    colors = ButtonDefaults.textButtonColors(contentColor = CriticalColor)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { personViewModel.showDeleteConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Add event dialog
    if (personViewModel.showAddEventDialog) {
        EventEditorDialog(
            event = null,
            ownerXref = person.xref,
            ownerType = "INDI",
            onSave = { eventType, dateValue, place, description ->
                personViewModel.createEvent(person.xref, "INDI", eventType, dateValue, place, description)
            },
            onDismiss = { personViewModel.showAddEventDialog = false },
            onDelete = null
        )
    }

    // Edit event dialog
    personViewModel.editingEvent?.let { event ->
        EventEditorDialog(
            event = event,
            ownerXref = person.xref,
            ownerType = "INDI",
            onSave = { eventType, dateValue, place, description ->
                val updated = event.copy(
                    eventType = eventType,
                    dateValue = dateValue.trim(),
                    place = place.trim(),
                    description = description.trim()
                )
                personViewModel.updateEvent(updated)
            },
            onDismiss = { personViewModel.editingEvent = null },
            onDelete = { personViewModel.deleteEvent(event.id) }
        )
    }
}

@Composable
private fun AskAISection(
    person: GedcomPerson,
    events: List<com.gedfix.models.GedcomEvent>,
    aiViewModel: AIViewModel
) {
    var aiResponse by remember(person.xref) { mutableStateOf<String?>(null) }
    var aiError by remember(person.xref) { mutableStateOf<String?>(null) }
    var isLoading by remember(person.xref) { mutableStateOf(false) }
    var expanded by remember(person.xref) { mutableStateOf(false) }

    val hasApiKey = aiViewModel.getApiKey(aiViewModel.activeProvider).isNotBlank()

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = AIChatIconColor.copy(alpha = 0.08f)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("\u2604", fontSize = 16.sp)
                    Text(
                        "Ask AI",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = AIChatIconColor
                    )
                    Text(
                        "(${aiViewModel.activeProvider.displayName})",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (!hasApiKey) {
                    Text(
                        "No API key",
                        fontSize = 12.sp,
                        color = WarningColor
                    )
                }
            }

            // Quick action buttons
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                val queries = listOf(
                    "Analyze completeness" to "Analyze this person's record completeness. What information is missing? What should be researched next?",
                    "Research steps" to "Suggest specific research steps for this person. What records should I search and where?",
                    "Find contradictions" to "Look for any contradictions or inconsistencies in this person's record. Are there any data quality concerns?",
                    "Evaluate evidence" to "Evaluate the evidence for this person's key life events. How well-supported are the dates and places?"
                )

                for ((label, query) in queries) {
                    OutlinedButton(
                        onClick = {
                            isLoading = true
                            aiError = null
                            expanded = true
                            aiViewModel.askAboutPerson(
                                person = person,
                                events = events,
                                query = query,
                                onResult = { result ->
                                    aiResponse = result
                                    isLoading = false
                                },
                                onError = { error ->
                                    aiError = error
                                    isLoading = false
                                }
                            )
                        },
                        enabled = hasApiKey && !isLoading,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(label, fontSize = 12.sp)
                    }
                }
            }

            // Response area
            if (expanded && (isLoading || aiResponse != null || aiError != null)) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                if (isLoading) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 2.dp
                        )
                        Text(
                            "Analyzing with ${aiViewModel.activeProvider.displayName}...",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                aiError?.let { error ->
                    Text(
                        text = error,
                        fontSize = 13.sp,
                        color = CriticalColor
                    )
                }

                aiResponse?.let { response ->
                    Text(
                        text = response,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        lineHeight = 20.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun PersonMediaSection(
    person: GedcomPerson,
    personViewModel: PersonViewModel
) {
    val mediaItems = remember(person.xref) {
        personViewModel.fetchMediaForOwner(person.xref)
    }
    var selectedMedia by remember { mutableStateOf<GedcomMedia?>(null) }

    if (mediaItems.isNotEmpty()) {
        val imageCount = mediaItems.count { it.isImage }
        val docCount = mediaItems.count { !it.isImage }

        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "\uD83D\uDCF7 Photos & Media",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MediaIconColor.copy(alpha = 0.15f)
                        ) {
                            val countText = buildList {
                                if (imageCount > 0) add("$imageCount photo${if (imageCount != 1) "s" else ""}")
                                if (docCount > 0) add("$docCount document${if (docCount != 1) "s" else ""}")
                            }.joinToString(", ")
                            Text(
                                text = countText,
                                fontSize = 11.sp,
                                color = MediaIconColor,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                }

                // Horizontal scrolling row of thumbnails
                androidx.compose.foundation.lazy.LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(mediaItems.size) { index ->
                        val media = mediaItems[index]
                        Surface(
                            modifier = Modifier
                                .size(100.dp)
                                .clip(RoundedCornerShape(8.dp)),
                            color = MaterialTheme.colorScheme.surface,
                            onClick = { selectedMedia = media }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                val thumbnail = remember(media.filePath) {
                                    loadImageFromFile(media)
                                }
                                if (thumbnail != null) {
                                    androidx.compose.foundation.Image(
                                        bitmap = thumbnail,
                                        contentDescription = media.displayTitle,
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                    )
                                } else {
                                    Text(
                                        text = media.placeholderIcon,
                                        fontSize = 28.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                    )
                                }
                                // Format badge
                                Surface(
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .padding(4.dp),
                                    shape = RoundedCornerShape(4.dp),
                                    color = MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.7f)
                                ) {
                                    Text(
                                        text = media.formatBadge,
                                        fontSize = 8.sp,
                                        color = MaterialTheme.colorScheme.inverseOnSurface,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Media viewer dialog
        selectedMedia?.let { media ->
            MediaViewerDialog(
                media = media,
                allMedia = mediaItems,
                ownerName = person.displayName,
                onDismiss = { selectedMedia = null },
                onNavigate = { selectedMedia = it },
                onNavigateToPerson = { }
            )
        }
    }
}

private fun loadImageFromFile(media: GedcomMedia): androidx.compose.ui.graphics.ImageBitmap? {
    if (!media.isImage) return null
    return try {
        val file = java.io.File(media.filePath)
        if (file.exists() && file.isFile) {
            file.inputStream().buffered().use { stream ->
                androidx.compose.ui.res.loadImageBitmap(stream)
            }
        } else null
    } catch (_: Exception) {
        null
    }
}

// eventTypeIcon moved to com.gedfix.ui.components.EventTypeUtils
