package com.gedfix.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.gedfix.models.GedcomPerson
import com.gedfix.ui.theme.*

/**
 * Reusable person card with PersonAvatar, name, and dates.
 * Apple-polished styling.
 */
@Composable
fun PersonCard(
    person: GedcomPerson,
    birthDate: String = "",
    deathDate: String = "",
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        PersonAvatar(person = person, size = 36.dp, showBadge = true)

        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = person.displayName.ifEmpty { "(Unknown)" },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (person.isLiving) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = LivingBadgeBg
                    ) {
                        Text(
                            text = "\u25CF",
                            style = MaterialTheme.typography.labelSmall,
                            color = LivingBadgeColor,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                        )
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (birthDate.isNotEmpty()) {
                    Text(
                        text = "b. $birthDate",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (deathDate.isNotEmpty()) {
                    Text(
                        text = "d. $deathDate",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
