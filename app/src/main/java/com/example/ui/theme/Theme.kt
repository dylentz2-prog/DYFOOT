package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val DyfootDarkColorScheme = darkColorScheme(
    primary = NeonLime,
    onPrimary = MidnightBlack,
    primaryContainer = NeonLimeContainer,
    onPrimaryContainer = NeonLime,
    secondary = InfoCyan,
    onSecondary = MidnightBlack,
    secondaryContainer = InfoCyanContainer,
    onSecondaryContainer = CrispWhite,
    tertiary = VipAmber,
    onTertiary = MidnightBlack,
    tertiaryContainer = VipAmberContainer,
    onTertiaryContainer = VipAmber,
    background = MidnightBlack,
    onBackground = CrispWhite,
    surface = MidnightElevated,
    onSurface = CrispWhite,
    surfaceVariant = MidnightCard,
    onSurfaceVariant = SlateText,
    surfaceContainerLowest = MidnightBlack,
    surfaceContainerLow = MidnightElevated,
    surfaceContainer = MidnightCard,
    surfaceContainerHigh = MidnightCardAlt,
    outline = MidnightBorder,
    outlineVariant = MidnightBorder,
    error = LiveRed,
    onError = CrispWhite,
    errorContainer = LiveRedContainer,
    onErrorContainer = LiveRed
)

val DyfootShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(30.dp)
)

@Composable
fun DyfootTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DyfootDarkColorScheme,
        typography = DyfootTypography,
        shapes = DyfootShapes,
        content = content
    )
}
