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
    val landscapeCardWidth: Dp = 260.dp,
    val compactCardWidth: Dp = 184.dp,
    val heroPhoneHeight: Dp = 360.dp,
    val heroTvHeight: Dp = 520.dp,
    val focusScale: Float = 1.055f,
)

@Immutable
data class AstraWaveElevation(
    val resting: Dp = 2.dp,
    val focused: Dp = 10.dp,
    val overlay: Dp = 18.dp,
)

@Immutable
data class AstraWaveOpacity(
    val disabled: Float = 0.38f,
    val secondary: Float = 0.74f,
    val scrim: Float = 0.68f,
    val heroGradient: Float = 0.84f,
)

@Immutable
data class AstraWaveMotion(
    val fastMs: Int = 110,
    val focusMs: Int = 150,
    val standardMs: Int = 190,
    val slowMs: Int = 280,
)

@Immutable
data class AstraWaveLayoutMetrics(
    val gridGutter: Dp = 12.dp,
    val tvGridGutter: Dp = 18.dp,
    val dialogMaxWidth: Dp = 520.dp,
    val contentMaxWidth: Dp = 1440.dp,
    val posterAspectRatio: Float = 2f / 3f,
    val backdropAspectRatio: Float = 16f / 9f,
    val squareAspectRatio: Float = 1f,
)

val LocalAstraWaveSpacing = staticCompositionLocalOf { AstraWaveSpacing() }
val LocalAstraWaveSizing = staticCompositionLocalOf { AstraWaveSizing() }
val LocalAstraWaveElevation = staticCompositionLocalOf { AstraWaveElevation() }
val LocalAstraWaveOpacity = staticCompositionLocalOf { AstraWaveOpacity() }
val LocalAstraWaveMotion = staticCompositionLocalOf { AstraWaveMotion() }
val LocalAstraWaveLayout = staticCompositionLocalOf { AstraWaveLayoutMetrics() }

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
    elevation: AstraWaveElevation = AstraWaveElevation(),
    opacity: AstraWaveOpacity = AstraWaveOpacity(),
    motion: AstraWaveMotion = AstraWaveMotion(),
    layout: AstraWaveLayoutMetrics = AstraWaveLayoutMetrics(),
    reducedMotion: Boolean = false,
    content: @Composable () -> Unit,
) {
    val effectiveMotion = if (reducedMotion) {
        motion.copy(fastMs = 0, focusMs = 0, standardMs = 0, slowMs = 0)
    } else {
        motion
    }

    CompositionLocalProvider(
        LocalAstraWaveSpacing provides spacing,
        LocalAstraWaveSizing provides sizing,
        LocalAstraWaveElevation provides elevation,
        LocalAstraWaveOpacity provides opacity,
        LocalAstraWaveMotion provides effectiveMotion,
        LocalAstraWaveLayout provides layout,
    ) {
        MaterialTheme(
            colorScheme = AstraWaveColorScheme,
            typography = AstraWaveTypography,
            shapes = AstraWaveShapes,
            content = content,
        )
    }
}
