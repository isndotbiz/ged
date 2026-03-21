package com.gedfix.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gedfix.models.GedcomPerson
import com.gedfix.ui.theme.*

/**
 * Reusable person card with initials avatar, name, and dates.
 */
@Composable
fun PersonCard(
    person: GedcomPerson,
    birthDate: String = "",
    deathDate: String = "",
    modifier: Modifier = Modifier
) {
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

    Row(
        modifier = modifier.padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Avatar circle with initials
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(sexBgColor),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = person.initials.ifEmpty { "?" },
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = sexColor
            )
        }

        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = person.displayName.ifEmpty { "(Unknown)" },
                    fontWeight = FontWeight.Medium,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (person.isLiving) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = LivingBadgeBg
                    ) {
                        Text(
                            text = "\u25CF",
                            fontSize = 8.sp,
                            color = LivingBadgeColor,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                        )
                    }
                }
                // Validation badge
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = if (person.isValidated) ValidatedBgColor else UnvalidatedBgColor
                ) {
                    Text(
                        text = if (person.isValidated) "\u2713" else "\u26A0",
                        fontSize = 9.sp,
                        color = if (person.isValidated) ValidatedColor else UnvalidatedColor,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (birthDate.isNotEmpty()) {
                    Text(
                        text = "b. $birthDate",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (deathDate.isNotEmpty()) {
                    Text(
                        text = "d. $deathDate",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
