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
    val Background = Color(0xFF05060A)
    val BackgroundRaised = Color(0xFF090B11)
    val Surface = Color(0xFF10131B)
    val SurfaceRaised = Color(0xFF171B25)
    val SurfaceFocus = Color(0xFF222737)
    val Glass = Color(0xD9080A10)
    val GlassRaised = Color(0xED121722)
    val PrimaryText = Color(0xFFF8FAFF)
    val SecondaryText = Color(0xFFADB5C6)
    val TertiaryText = Color(0xFF747D91)
    val Accent = Color(0xFF8B7CFF)
    val AccentStrong = Color(0xFFB9AEFF)
    val AccentSoft = Color(0xFF5549B9)
    val Success = Color(0xFF58D5A5)
    val Warning = Color(0xFFF0B45D)
    val Error = Color(0xFFFF7086)
    val Live = Color(0xFFFF536F)
    val Divider = Color(0xFF242A37)
    val FocusRing = Color(0xFFF2EEFF)
    val PlayerBlack = Color(0xFF000000)
    val HeroScrim = Color(0xE605070C)
    val Chip = Color(0xFF1B2030)
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
    val section: Dp = 54.dp,
    val screenHorizontal: Dp = 20.dp,
    val tvScreenHorizontal: Dp = 56.dp,
)

@Immutable
data class AstraWaveSizing(
    val posterWidth: Dp = 156.dp,
    val posterHeight: Dp = 234.dp,
    val landscapeCardWidth: Dp = 284.dp,
    val compactCardWidth: Dp = 190.dp,
    val heroPhoneHeight: Dp = 430.dp,
    val heroTvHeight: Dp = 590.dp,
    val focusScale: Float = 1.035f,
)

@Immutable
data class AstraWaveElevation(
    val resting: Dp = 0.dp,
    val focused: Dp = 12.dp,
    val overlay: Dp = 22.dp,
)

@Immutable
data class AstraWaveOpacity(
    val disabled: Float = 0.38f,
    val secondary: Float = 0.76f,
    val scrim: Float = 0.78f,
    val heroGradient: Float = 0.92f,
)

@Immutable
data class AstraWaveMotion(
    val fastMs: Int = 90,
    val focusMs: Int = 130,
    val standardMs: Int = 200,
    val slowMs: Int = 300,
)

@Immutable
data class AstraWaveLayoutMetrics(
    val gridGutter: Dp = 12.dp,
    val tvGridGutter: Dp = 18.dp,
    val dialogMaxWidth: Dp = 660.dp,
    val contentMaxWidth: Dp = 1680.dp,
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
    displayLarge = TextStyle(fontSize = 48.sp, lineHeight = 50.sp, fontWeight = FontWeight.Black, letterSpacing = (-1.1).sp),
    headlineLarge = TextStyle(fontSize = 30.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.45).sp),
    headlineMedium = TextStyle(fontSize = 23.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold),
    titleLarge = TextStyle(fontSize = 20.sp, lineHeight = 25.sp, fontWeight = FontWeight.Bold),
    titleMedium = TextStyle(fontSize = 16.sp, lineHeight = 21.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 23.sp, fontWeight = FontWeight.Normal),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Normal),
    labelLarge = TextStyle(fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold),
    labelMedium = TextStyle(fontSize = 11.sp, lineHeight = 16.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.45.sp),
)

private val AstraWaveTabletTypography = Typography(
    displayLarge = TextStyle(fontSize = 54.sp, lineHeight = 57.sp, fontWeight = FontWeight.Black, letterSpacing = (-1.2).sp),
    headlineLarge = TextStyle(fontSize = 34.sp, lineHeight = 39.sp, fontWeight = FontWeight.Bold),
    headlineMedium = TextStyle(fontSize = 27.sp, lineHeight = 32.sp, fontWeight = FontWeight.Bold),
    titleLarge = TextStyle(fontSize = 22.sp, lineHeight = 27.sp, fontWeight = FontWeight.Bold),
    titleMedium = TextStyle(fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 17.sp, lineHeight = 24.sp, fontWeight = FontWeight.Normal),
    bodyMedium = TextStyle(fontSize = 15.sp, lineHeight = 21.sp, fontWeight = FontWeight.Normal),
    labelLarge = TextStyle(fontSize = 14.sp, lineHeight = 19.sp, fontWeight = FontWeight.SemiBold),
    labelMedium = TextStyle(fontSize = 12.sp, lineHeight = 17.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp),
)

private val AstraWaveTvTypography = Typography(
    displayLarge = TextStyle(fontSize = 64.sp, lineHeight = 66.sp, fontWeight = FontWeight.Black, letterSpacing = (-1.4).sp),
    headlineLarge = TextStyle(fontSize = 40.sp, lineHeight = 45.sp, fontWeight = FontWeight.Bold),
    headlineMedium = TextStyle(fontSize = 31.sp, lineHeight = 37.sp, fontWeight = FontWeight.Bold),
    titleLarge = TextStyle(fontSize = 25.sp, lineHeight = 31.sp, fontWeight = FontWeight.Bold),
    titleMedium = TextStyle(fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 19.sp, lineHeight = 27.sp, fontWeight = FontWeight.Normal),
    bodyMedium = TextStyle(fontSize = 16.sp, lineHeight = 23.sp, fontWeight = FontWeight.Normal),
    labelLarge = TextStyle(fontSize = 15.sp, lineHeight = 21.sp, fontWeight = FontWeight.SemiBold),
    labelMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.55.sp),
)

private val AstraWaveShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(11.dp),
    medium = RoundedCornerShape(15.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
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
        AstraWaveDeviceClass.PHONE -> spacing
        AstraWaveDeviceClass.TABLET -> spacing.copy(screenHorizontal = 30.dp, section = 58.dp)
        AstraWaveDeviceClass.TV -> spacing.copy(screenHorizontal = spacing.tvScreenHorizontal, section = 64.dp)
    }
    val effectiveSizing = when (deviceClass) {
        AstraWaveDeviceClass.PHONE -> sizing
        AstraWaveDeviceClass.TABLET -> sizing.copy(posterWidth = 174.dp, posterHeight = 261.dp, landscapeCardWidth = 308.dp, compactCardWidth = 200.dp)
        AstraWaveDeviceClass.TV -> sizing.copy(posterWidth = 194.dp, posterHeight = 291.dp, landscapeCardWidth = 344.dp, compactCardWidth = 224.dp, focusScale = 1.045f)
    }
    val effectiveLayout = when (deviceClass) {
        AstraWaveDeviceClass.PHONE -> layout
        AstraWaveDeviceClass.TABLET -> layout.copy(gridGutter = 14.dp)
        AstraWaveDeviceClass.TV -> layout.copy(gridGutter = layout.tvGridGutter)
    }
    val typography = when (deviceClass) {
        AstraWaveDeviceClass.PHONE -> AstraWavePhoneTypography
        AstraWaveDeviceClass.TABLET -> AstraWaveTabletTypography
        AstraWaveDeviceClass.TV -> AstraWaveTvTypography
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
        MaterialTheme(colorScheme = AstraWaveColorScheme, typography = typography, shapes = AstraWaveShapes, content = content)
    }
}
