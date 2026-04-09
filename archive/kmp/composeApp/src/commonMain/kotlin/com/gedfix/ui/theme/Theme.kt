package com.gedfix.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Apple-polished light color scheme.
 * Muted, not saturated. Think Apple Notes meets Finder.
 */
private val GedFixLightColors = lightColorScheme(
    primary = Color(0xFF007AFF),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD6EAFF),
    onPrimaryContainer = Color(0xFF002D5E),
    secondary = Color(0xFF5856D6),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE8E7FF),
    onSecondaryContainer = Color(0xFF1C1B5E),
    tertiary = Color(0xFF34C759),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFD4F5DD),
    onTertiaryContainer = Color(0xFF00391A),
    error = Color(0xFFFF3B30),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFF8F8FA),
    onBackground = Color(0xFF1C1C1E),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1C1C1E),
    surfaceVariant = Color(0xFFF2F2F7),
    onSurfaceVariant = Color(0xFF636366),
    outline = Color(0xFFE5E5EA),
    outlineVariant = Color(0xFFD1D1D6),
    inverseSurface = Color(0xFF2C2C2E),
    inverseOnSurface = Color(0xFFF2F2F7),
    surfaceTint = Color(0xFF007AFF)
)

/**
 * Apple-polished dark color scheme.
 * Native macOS dark mode feel.
 */
private val GedFixDarkColors = darkColorScheme(
    primary = Color(0xFF0A84FF),
    onPrimary = Color.White,
    primaryContainer = Color(0xFF003A70),
    onPrimaryContainer = Color(0xFFD6EAFF),
    secondary = Color(0xFF5E5CE6),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF2E2D6E),
    onSecondaryContainer = Color(0xFFE8E7FF),
    tertiary = Color(0xFF30D158),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFF004D20),
    onTertiaryContainer = Color(0xFFD4F5DD),
    error = Color(0xFFFF453A),
    onError = Color.White,
    errorContainer = Color(0xFF5C0008),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF1C1C1E),
    onBackground = Color(0xFFF2F2F7),
    surface = Color(0xFF2C2C2E),
    onSurface = Color(0xFFF2F2F7),
    surfaceVariant = Color(0xFF3A3A3C),
    onSurfaceVariant = Color(0xFFAEAEB2),
    outline = Color(0xFF48484A),
    outlineVariant = Color(0xFF3A3A3C),
    inverseSurface = Color(0xFFF2F2F7),
    inverseOnSurface = Color(0xFF2C2C2E),
    surfaceTint = Color(0xFF0A84FF)
)

/**
 * Custom typography scale. Apple SF-inspired sizing and weights.
 */
val GedFixTypography = Typography(
    headlineLarge = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        letterSpacing = (-0.5).sp,
        lineHeight = 34.sp
    ),
    headlineMedium = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp
    ),
    titleLarge = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 24.sp
    ),
    titleMedium = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 22.sp
    ),
    bodyLarge = TextStyle(
        fontSize = 15.sp,
        lineHeight = 22.sp
    ),
    bodyMedium = TextStyle(
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    bodySmall = TextStyle(
        fontSize = 12.sp,
        lineHeight = 16.sp
    ),
    labelLarge = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        letterSpacing = 0.1.sp
    ),
    labelMedium = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp
    ),
    labelSmall = TextStyle(
        fontSize = 11.sp,
        letterSpacing = 0.5.sp
    )
)

/**
 * Rounded shapes following Apple HIG corner radii.
 */
val GedFixShapes = Shapes(
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(24.dp)
)

@Composable
fun GedFixTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) GedFixDarkColors else GedFixLightColors

    MaterialTheme(
        colorScheme = colorScheme,
        typography = GedFixTypography,
        shapes = GedFixShapes,
        content = content
    )
}
