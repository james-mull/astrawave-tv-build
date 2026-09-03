package com.astrawave.app.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * AstraWave visual foundation shared by phone, tablet, TV and future Nuvio overlays.
 * Keep screens on these tokens rather than introducing one-off colors/spacing.
 */
object AstraWaveColors {
    val Background = Color(0xFF07090D)
    val BackgroundRaised = Color(0xFF0D1118)
    val Surface = Color(0xFF121824)
    val SurfaceRaised = Color(0xFF192231)
    val SurfaceFocus = Color(0xFF243047)
    val PrimaryText = Color(0xFFF7F9FC)
    val SecondaryText = Color(0xFFAAB3C2)
    val TertiaryText = Color(0xFF747E8E)
    val Accent = Color(0xFF8B6CFF)
    val AccentStrong = Color(0xFFA58CFF)
    val Success = Color(0xFF43D99A)
    val Warning = Color(0xFFFFC857)
    val Error = Color(0xFFFF667A)
    val Live = Color(0xFFFF4D67)
    val Divider = Color(0xFF252E3D)
    val FocusRing = Color(0xFFE2DBFF)
}

@Immutable
data class AstraWaveSpacing(
    val xxs: Dp = 4.dp,
    val xs: Dp = 8.dp,
    val sm: Dp = 12.dp,
    val md: Dp = 16.dp,
    val lg: Dp = 22.dp,
    val xl: Dp = 28.dp,
    val xxl: Dp = 36.dp,
    val section: Dp = 44.dp,
    val screenHorizontal: Dp = 22.dp,
    val tvScreenHorizontal: Dp = 48.dp,
)

@Immutable
data class AstraWaveSizing(
    val posterWidth: Dp = 154.dp,
    val posterHeight: Dp = 224.dp,
    val heroPhoneHeight: Dp = 360.dp,
    val heroTvHeight: Dp = 520.dp,
    val focusScale: Float = 1.055f,
)

val LocalAstraWaveSpacing = staticCompositionLocalOf { AstraWaveSpacing() }
val LocalAstraWaveSizing = staticCompositionLocalOf { AstraWaveSizing() }

private val AstraWaveColorScheme = darkColorScheme(
    primary = AstraWaveColors.Accent,
    onPrimary = Color.White,
    primaryContainer = AstraWaveColors.SurfaceFocus,
    onPrimaryContainer = AstraWaveColors.PrimaryText,
    secondary = AstraWaveColors.AccentStrong,
    background = AstraWaveColors.Background,
    onBackground = AstraWaveColors.PrimaryText,
    surface = AstraWaveColors.Surface,
    onSurface = AstraWaveColors.PrimaryText,
    surfaceVariant = AstraWaveColors.SurfaceRaised,
    onSurfaceVariant = AstraWaveColors.SecondaryText,
    outline = AstraWaveColors.Divider,
    error = AstraWaveColors.Error,
)

private val AstraWaveTypography = Typography(
    displayLarge = TextStyle(fontSize = 42.sp, lineHeight = 46.sp, fontWeight = FontWeight.Black),
    headlineLarge = TextStyle(fontSize = 30.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold),
    headlineMedium = TextStyle(fontSize = 24.sp, lineHeight = 29.sp, fontWeight = FontWeight.Bold),
    titleLarge = TextStyle(fontSize = 20.sp, lineHeight = 25.sp, fontWeight = FontWeight.Bold),
    titleMedium = TextStyle(fontSize = 16.sp, lineHeight = 21.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 23.sp, fontWeight = FontWeight.Normal),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Normal),
    labelLarge = TextStyle(fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold),
    labelMedium = TextStyle(fontSize = 11.sp, lineHeight = 16.sp, fontWeight = FontWeight.Bold),
)

private val AstraWaveShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(30.dp),
)

@Composable
fun AstraWaveTheme(
    spacing: AstraWaveSpacing = AstraWaveSpacing(),
    sizing: AstraWaveSizing = AstraWaveSizing(),
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalAstraWaveSpacing provides spacing,
        LocalAstraWaveSizing provides sizing,
    ) {
        MaterialTheme(
            colorScheme = AstraWaveColorScheme,
            typography = AstraWaveTypography,
            shapes = AstraWaveShapes,
            content = content,
        )
    }
}
