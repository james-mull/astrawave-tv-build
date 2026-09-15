package com.astrawave.app.ui

import android.content.res.Configuration
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** AstraWave premium visual foundation shared by phone, tablet and TV. */
object AstraWaveColors {
    val Background = Color(0xFF05070B)
    val BackgroundRaised = Color(0xFF0A0D14)
    val Surface = Color(0xFF0F141D)
    val SurfaceRaised = Color(0xFF151B27)
    val SurfaceFocus = Color(0xFF20283A)
    val Glass = Color(0xE0070A10)
    val GlassRaised = Color(0xF0111621)
    val PrimaryText = Color(0xFFF9FAFF)
    val SecondaryText = Color(0xFFB4BDCE)
    val TertiaryText = Color(0xFF7E899E)
    val Accent = Color(0xFF8D7CFF)
    val AccentStrong = Color(0xFFC0B7FF)
    val AccentSoft = Color(0xFF5E50C7)
    val Success = Color(0xFF61D9A9)
    val Warning = Color(0xFFF1B963)
    val Error = Color(0xFFFF748A)
    val Live = Color(0xFFFF526F)
    val Divider = Color(0xFF263042)
    val FocusRing = Color(0xFFF4F0FF)
    val PlayerBlack = Color(0xFF000000)
    val HeroScrim = Color(0xE804070C)
    val Chip = Color(0xFF1A2130)
    val GuideNow = Color(0xFF9C8DFF)
    val GuideFuture = Color(0xFF18202D)
    val GuidePast = Color(0xFF10151D)
}

@Immutable
data class AstraWaveSpacing(
    val xxs: Dp = 4.dp,
    val xs: Dp = 8.dp,
    val sm: Dp = 12.dp,
    val md: Dp = 16.dp,
    val lg: Dp = 24.dp,
    val xl: Dp = 32.dp,
    val xxl: Dp = 44.dp,
    val section: Dp = 44.dp,
    val screenHorizontal: Dp = 16.dp,
    val tvScreenHorizontal: Dp = 52.dp,
)

@Immutable
data class AstraWaveSizing(
    val posterWidth: Dp = 144.dp,
    val posterHeight: Dp = 216.dp,
    val landscapeCardWidth: Dp = 268.dp,
    val compactCardWidth: Dp = 176.dp,
    val heroPhoneHeight: Dp = 390.dp,
    val heroTvHeight: Dp = 640.dp,
    val focusScale: Float = 1.008f,
)

@Immutable
data class AstraWaveElevation(
    val resting: Dp = 1.dp,
    val focused: Dp = 18.dp,
    val overlay: Dp = 28.dp,
)

@Immutable
data class AstraWaveOpacity(
    val disabled: Float = 0.38f,
    val secondary: Float = 0.78f,
    val scrim: Float = 0.82f,
    val heroGradient: Float = 0.94f,
)

@Immutable
data class AstraWaveMotion(
    val fastMs: Int = 85,
    val focusMs: Int = 115,
    val standardMs: Int = 190,
    val slowMs: Int = 280,
)

@Immutable
data class AstraWaveLayoutMetrics(
    val gridGutter: Dp = 10.dp,
    val tvGridGutter: Dp = 16.dp,
    val dialogMaxWidth: Dp = 680.dp,
    val contentMaxWidth: Dp = 1760.dp,
    val posterAspectRatio: Float = 2f / 3f,
    val backdropAspectRatio: Float = 16f / 9f,
    val squareAspectRatio: Float = 1f,
)

enum class AstraWaveDeviceClass { PHONE, TABLET, TV }

val LocalAstraWaveSpacing = staticCompositionLocalOf { AstraWaveSpacing() }
val LocalAstraWaveSizing = staticCompositionLocalOf { AstraWaveSizing() }
val LocalAstraWaveElevation = staticCompositionLocalOf { AstraWaveElevation() }
val LocalAstraWaveOpacity = staticCompositionLocalOf { AstraWaveOpacity() }
val LocalAstraWaveMotion = staticCompositionLocalOf { AstraWaveMotion() }
val LocalAstraWaveLayout = staticCompositionLocalOf { AstraWaveLayoutMetrics() }
val LocalAstraWaveDeviceClass = staticCompositionLocalOf { AstraWaveDeviceClass.PHONE }

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

private val AstraWavePhoneTypography = Typography(
    displayLarge = TextStyle(fontSize = 42.sp, lineHeight = 45.sp, fontWeight = FontWeight.Black, letterSpacing = (-0.9).sp),
    headlineLarge = TextStyle(fontSize = 28.sp, lineHeight = 32.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.4).sp),
    headlineMedium = TextStyle(fontSize = 22.sp, lineHeight = 27.sp, fontWeight = FontWeight.Bold),
    titleLarge = TextStyle(fontSize = 19.sp, lineHeight = 24.sp, fontWeight = FontWeight.Bold),
    titleMedium = TextStyle(fontSize = 16.sp, lineHeight = 21.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 15.sp, lineHeight = 22.sp, fontWeight = FontWeight.Normal),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Normal),
    labelLarge = TextStyle(fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Bold),
    labelMedium = TextStyle(fontSize = 11.sp, lineHeight = 16.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.42.sp),
)

private val AstraWaveTabletTypography = Typography(
    displayLarge = TextStyle(fontSize = 52.sp, lineHeight = 56.sp, fontWeight = FontWeight.Black, letterSpacing = (-1.15).sp),
    headlineLarge = TextStyle(fontSize = 33.sp, lineHeight = 38.sp, fontWeight = FontWeight.Bold),
    headlineMedium = TextStyle(fontSize = 27.sp, lineHeight = 32.sp, fontWeight = FontWeight.Bold),
    titleLarge = TextStyle(fontSize = 22.sp, lineHeight = 27.sp, fontWeight = FontWeight.Bold),
    titleMedium = TextStyle(fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 17.sp, lineHeight = 24.sp, fontWeight = FontWeight.Normal),
    bodyMedium = TextStyle(fontSize = 15.sp, lineHeight = 21.sp, fontWeight = FontWeight.Normal),
    labelLarge = TextStyle(fontSize = 14.sp, lineHeight = 19.sp, fontWeight = FontWeight.Bold),
    labelMedium = TextStyle(fontSize = 12.sp, lineHeight = 17.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.48.sp),
)

private val AstraWaveTvTypography = Typography(
    displayLarge = TextStyle(fontSize = 62.sp, lineHeight = 64.sp, fontWeight = FontWeight.Black, letterSpacing = (-1.5).sp),
    headlineLarge = TextStyle(fontSize = 38.sp, lineHeight = 43.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
    headlineMedium = TextStyle(fontSize = 30.sp, lineHeight = 35.sp, fontWeight = FontWeight.Bold),
    titleLarge = TextStyle(fontSize = 24.sp, lineHeight = 29.sp, fontWeight = FontWeight.Bold),
    titleMedium = TextStyle(fontSize = 19.sp, lineHeight = 25.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 18.sp, lineHeight = 26.sp, fontWeight = FontWeight.Normal),
    bodyMedium = TextStyle(fontSize = 15.sp, lineHeight = 22.sp, fontWeight = FontWeight.Normal),
    labelLarge = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Bold),
    labelMedium = TextStyle(fontSize = 13.sp, lineHeight = 19.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp),
)

private val AstraWavePhoneShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(7.dp),
    medium = RoundedCornerShape(10.dp),
    large = RoundedCornerShape(12.dp),
    extraLarge = RoundedCornerShape(16.dp),
)

private val AstraWaveTabletShapes = Shapes(
    extraSmall = RoundedCornerShape(7.dp),
    small = RoundedCornerShape(11.dp),
    medium = RoundedCornerShape(15.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(27.dp),
)

private val AstraWaveTvShapes = Shapes(
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
    val configuration = LocalConfiguration.current
    val isTv = (configuration.uiMode and Configuration.UI_MODE_TYPE_MASK) == Configuration.UI_MODE_TYPE_TELEVISION
    val deviceClass = when {
        isTv -> AstraWaveDeviceClass.TV
        configuration.screenWidthDp >= 600 -> AstraWaveDeviceClass.TABLET
        else -> AstraWaveDeviceClass.PHONE
    }

    val effectiveSpacing = when (deviceClass) {
        AstraWaveDeviceClass.PHONE -> spacing.copy(section = 30.dp)
        AstraWaveDeviceClass.TABLET -> spacing.copy(screenHorizontal = 28.dp, section = 54.dp)
        AstraWaveDeviceClass.TV -> spacing.copy(screenHorizontal = spacing.tvScreenHorizontal, section = 62.dp)
    }
    val effectiveSizing = when (deviceClass) {
        AstraWaveDeviceClass.PHONE -> sizing
        AstraWaveDeviceClass.TABLET -> sizing.copy(
            posterWidth = 168.dp,
            posterHeight = 252.dp,
            landscapeCardWidth = 304.dp,
            compactCardWidth = 196.dp,
            heroPhoneHeight = 490.dp,
        )
        AstraWaveDeviceClass.TV -> sizing.copy(
            posterWidth = 190.dp,
            posterHeight = 285.dp,
            landscapeCardWidth = 348.dp,
            compactCardWidth = 220.dp,
            heroTvHeight = 640.dp,
            focusScale = 1.012f,
        )
    }
    val effectiveLayout = when (deviceClass) {
        AstraWaveDeviceClass.PHONE -> layout
        AstraWaveDeviceClass.TABLET -> layout.copy(gridGutter = 13.dp)
        AstraWaveDeviceClass.TV -> layout.copy(gridGutter = layout.tvGridGutter)
    }
    val typography = when (deviceClass) {
        AstraWaveDeviceClass.PHONE -> AstraWavePhoneTypography
        AstraWaveDeviceClass.TABLET -> AstraWaveTabletTypography
        AstraWaveDeviceClass.TV -> AstraWaveTvTypography
    }
    val shapes = when (deviceClass) {
        AstraWaveDeviceClass.PHONE -> AstraWavePhoneShapes
        AstraWaveDeviceClass.TABLET -> AstraWaveTabletShapes
        AstraWaveDeviceClass.TV -> AstraWaveTvShapes
    }
    val effectiveMotion = if (reducedMotion) motion.copy(fastMs = 0, focusMs = 0, standardMs = 0, slowMs = 0) else motion

    CompositionLocalProvider(
        LocalAstraWaveSpacing provides effectiveSpacing,
        LocalAstraWaveSizing provides effectiveSizing,
        LocalAstraWaveElevation provides elevation,
        LocalAstraWaveOpacity provides opacity,
        LocalAstraWaveMotion provides effectiveMotion,
        LocalAstraWaveLayout provides effectiveLayout,
        LocalAstraWaveDeviceClass provides deviceClass,
    ) {
        MaterialTheme(colorScheme = AstraWaveColorScheme, typography = typography, shapes = shapes, content = content)
    }
}
