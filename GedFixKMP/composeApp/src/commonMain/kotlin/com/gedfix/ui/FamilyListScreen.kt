package com.gedfix.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.gedfix.models.GedcomFamily
import com.gedfix.ui.components.eventTypeIcon
import com.gedfix.ui.theme.*
import com.gedfix.viewmodel.AppViewModel

/**
 * Family list and detail view - shows partner pairs with children.
 */
@Composable
fun FamilyListScreen(viewModel: AppViewModel) {
    val db = viewModel.db
    val families = db.fetchAllFamilies()
    var selectedFamily by remember { mutableStateOf<GedcomFamily?>(null) }

    Row(modifier = Modifier.fillMaxSize()) {
        // List panel
        LazyColumn(
            modifier = Modifier.width(340.dp).fillMaxHeight(),
            contentPadding = PaddingValues(4.dp)
        ) {
            items(families, key = { it.id }) { family ->
                val isSelected = selectedFamily?.id == family.id
                val p1 = db.fetchPerson(family.partner1Xref)
                val p2 = db.fetchPerson(family.partner2Xref)
                val childCount = db.fetchChildLinks(family.xref).size

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { selectedFamily = family },
                    shape = RoundedCornerShape(6.dp),
                    color = if (isSelected) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surface
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (p1 != null) {
                                Text(p1.displayName, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                            }
                            if (family.partner1Xref.isNotEmpty() && family.partner2Xref.isNotEmpty()) {
                                Text("\u2665", fontSize = 10.sp, color = FemaleColor)
                            }
                            if (p2 != null) {
                                Text(p2.displayName, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                            }
                        }
                        if (childCount > 0) {
                            Text(
                                "$childCount child${if (childCount == 1) "" else "ren"}",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        VerticalDivider()

        // Detail panel
        val family = selectedFamily
        if (family != null) {
            FamilyDetail(family, viewModel, modifier = Modifier.fillMaxSize())
        } else {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Select a Family", fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Choose a family to see details.", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun FamilyDetail(
    family: GedcomFamily,
    viewModel: AppViewModel,
    modifier: Modifier = Modifier
) {
    val db = viewModel.db
    val p1 = db.fetchPerson(family.partner1Xref)
    val p2 = db.fetchPerson(family.partner2Xref)
    val events = db.fetchEvents(family.xref)
    val childLinks = db.fetchChildLinks(family.xref)
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .verticalScroll(scrollState)
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Text("Family", fontSize = 28.sp, fontWeight = FontWeight.Bold)

        // Partners
        Row(
            horizontalArrangement = Arrangement.spacedBy(32.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (p1 != null) {
                PartnerCard(p1)
            }
            Text("\u2665", fontSize = 32.sp, color = FemaleColor)
            if (p2 != null) {
                PartnerCard(p2)
            }
        }

        // Events
        if (events.isNotEmpty()) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Events", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    for (event in events) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(eventTypeIcon(event.eventType), color = MaterialTheme.colorScheme.primary)
                            Text("${event.displayType}: ${event.dateValue}")
                            if (event.place.isNotEmpty()) {
                                Text("in ${event.place}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }

        // Children
        if (childLinks.isNotEmpty()) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Children (${childLinks.size})", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    for (link in childLinks) {
                        val child = db.fetchPerson(link.childXref)
                        if (child != null) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val childColor = when (child.sex) {
                                    "M" -> MaleColor
                                    "F" -> FemaleColor
                                    else -> UnknownGenderColor
                                }
                                val childBg = when (child.sex) {
                                    "M" -> MaleBgColor
                                    "F" -> FemaleBgColor
                                    else -> UnknownGenderBgColor
                                }
                                Box(
                                    modifier = Modifier.size(28.dp).clip(CircleShape).background(childBg),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(child.initials, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = childColor)
                                }
                                Text(child.displayName, fontSize = 14.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PartnerCard(person: com.gedfix.models.GedcomPerson) {
    val sexColor = when (person.sex) {
        "M" -> MaleColor
        "F" -> FemaleColor
        else -> UnknownGenderColor
    }
    val sexBg = when (person.sex) {
        "M" -> MaleBgColor
        "F" -> FemaleBgColor
        else -> UnknownGenderBgColor
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(
            modifier = Modifier.size(56.dp).clip(CircleShape).background(sexBg),
            contentAlignment = Alignment.Center
        ) {
            Text(person.initials, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = sexColor)
        }
        Text(person.displayName, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
    }
}

// eventTypeIcon moved to com.gedfix.ui.components.EventTypeUtils
