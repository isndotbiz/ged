package com.gedfix.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gedfix.models.GedcomPerson
import com.gedfix.ui.theme.*

/**
 * Polished avatar component with consistent styling.
 * Soft tinted circle with initials, optional validation badge.
 */
@Composable
fun PersonAvatar(
    person: GedcomPerson,
    size: Dp = 40.dp,
    showBadge: Boolean = true
) {
    val color = avatarColor(person.sex)
    val bgColor = color.copy(alpha = 0.12f)
    val fontSize = when {
        size >= 64.dp -> 22.sp
        size >= 40.dp -> 14.sp
        else -> 11.sp
    }

    Box {
        Surface(
            shape = CircleShape,
            color = bgColor,
            modifier = Modifier.size(size)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = person.initials.ifEmpty { "?" },
                    fontSize = fontSize,
                    fontWeight = FontWeight.Bold,
                    color = color
                )
            }
        }

        // Small validation badge in bottom-right
        if (showBadge && person.isValidated) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = 2.dp, y = 2.dp)
                    .size(if (size >= 64.dp) 18.dp else 14.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(1.5.dp)
                    .clip(CircleShape)
                    .background(ValidatedColor),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "\u2713",
                    fontSize = if (size >= 64.dp) 10.sp else 8.sp,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

/**
 * Return the muted avatar color for a given sex code.
 */
fun avatarColor(sex: String): Color = when (sex) {
    "M" -> MaleColor
    "F" -> FemaleColor
    else -> UnknownGenderColor
}
