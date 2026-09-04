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

/**
 * AstraWave premium visual foundation shared by phone, tablet and TV.
 * Keep feature screens on these tokens so web, Android and TV feel like one product.
 */
object AstraWaveColors {
    val Background = Color(0xFF050507)
    val BackgroundRaised = Color(0xFF09090E)
    val Surface = Color(0xFF0E0E14)
    val SurfaceRaised = Color(0xFF15131B)
    val SurfaceFocus = Color(0xFF241A31)
    val Glass = Color(0xCC0B0A10)
    val GlassRaised = Color(0xE616121E)
    val PrimaryText = Color(0xFFF6F3F9)
    val SecondaryText = Color(0xFFA39CAA)
    val TertiaryText = Color(0xFF746D79)
    val Accent = Color(0xFF9D62E8)
    val AccentStrong = Color(0xFFC38BFF)
    val AccentSoft = Color(0xFF6F3EB6)
    val Success = Color(0xFF62D6A0)
    val Warning = Color(0xFFD9A457)
    val Error = Color(0xFFFF6F83)
    val Live = Color(0xFFFF526C)
    val Divider = Color(0xFF27212D)
    val FocusRing = Color(0xFFEBD9FF)
    val PlayerBlack = Color(0xFF000000)
}

@Immutable
data class AstraWaveSpacing(
    val xxs: Dp = 4.dp,
    val xs: Dp = 8.dp,
    val sm: Dp = 12.dp,
    val md: Dp = 16.dp,
    val lg: Dp = 24.dp,
    val xl: Dp = 30.dp,
    val xxl: Dp = 40.dp,
    val section: Dp = 48.dp,
    val screenHorizontal: Dp = 20.dp,
    val tvScreenHorizontal: Dp = 52.dp,
)

@Immutable
data class AstraWaveSizing(
    val posterWidth: Dp = 156.dp,
    val posterHeight: Dp = 234.dp,
    val landscapeCardWidth: Dp = 276.dp,
    val compactCardWidth: Dp = 190.dp,
    val heroPhoneHeight: Dp = 420.dp,
    val heroTvHeight: Dp = 560.dp,
    val focusScale: Float = 1.045f,
)

@Immutable
data class AstraWaveElevation(
    val resting: Dp = 1.dp,
    val focused: Dp = 14.dp,
    val overlay: Dp = 24.dp,
)

@Immutable
data class AstraWaveOpacity(
    val disabled: Float = 0.38f,
    val secondary: Float = 0.74f,
    val scrim: Float = 0.72f,
    val heroGradient: Float = 0.90f,
)

@Immutable
data class AstraWaveMotion(
    val fastMs: Int = 100,
    val focusMs: Int = 145,
    val standardMs: Int = 210,
    val slowMs: Int = 320,
)

@Immutable
data class AstraWaveLayoutMetrics(
    val gridGutter: Dp = 12.dp,
    val tvGridGutter: Dp = 20.dp,
    val dialogMaxWidth: Dp = 620.dp,
    val contentMaxWidth: Dp = 1600.dp,
    val posterAspectRatio: Float = 2f / 3f,
    val backdropAspectRatio: Float = 16f / 9f,
    val squareAspectRatio: Float = 1f,
)

enum class AstraWaveDeviceClass {
    PHONE,
    TABLET,
    TV,
}

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
    displayLarge = TextStyle(fontSize = 46.sp, lineHeight = 48.sp, fontWeight = FontWeight.Black),
    headlineLarge = TextStyle(fontSize = 31.sp, lineHeight = 35.sp, fontWeight = FontWeight.Bold),
    headlineMedium = TextStyle(fontSize = 24.sp, lineHeight = 29.sp, fontWeight = FontWeight.Bold),
    titleLarge = TextStyle(fontSize = 20.sp, lineHeight = 25.sp, fontWeight = FontWeight.Bold),
    titleMedium = TextStyle(fontSize = 16.sp, lineHeight = 21.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 23.sp, fontWeight = FontWeight.Normal),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Normal),
    labelLarge = TextStyle(fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold),
    labelMedium = TextStyle(fontSize = 11.sp, lineHeight = 16.sp, fontWeight = FontWeight.Bold),
)

private val AstraWaveTabletTypography = Typography(
    displayLarge = TextStyle(fontSize = 52.sp, lineHeight = 55.sp, fontWeight = FontWeight.Black),
    headlineLarge = TextStyle(fontSize = 34.sp, lineHeight = 39.sp, fontWeight = FontWeight.Bold),
    headlineMedium = TextStyle(fontSize = 27.sp, lineHeight = 32.sp, fontWeight = FontWeight.Bold),
    titleLarge = TextStyle(fontSize = 22.sp, lineHeight = 27.sp, fontWeight = FontWeight.Bold),
    titleMedium = TextStyle(fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 17.sp, lineHeight = 24.sp, fontWeight = FontWeight.Normal),
    bodyMedium = TextStyle(fontSize = 15.sp, lineHeight = 21.sp, fontWeight = FontWeight.Normal),
    labelLarge = TextStyle(fontSize = 14.sp, lineHeight = 19.sp, fontWeight = FontWeight.SemiBold),
    labelMedium = TextStyle(fontSize = 12.sp, lineHeight = 17.sp, fontWeight = FontWeight.Bold),
)

private val AstraWaveTvTypography = Typography(
    displayLarge = TextStyle(fontSize = 62.sp, lineHeight = 64.sp, fontWeight = FontWeight.Black),
    headlineLarge = TextStyle(fontSize = 40.sp, lineHeight = 45.sp, fontWeight = FontWeight.Bold),
    headlineMedium = TextStyle(fontSize = 31.sp, lineHeight = 37.sp, fontWeight = FontWeight.Bold),
    titleLarge = TextStyle(fontSize = 25.sp, lineHeight = 31.sp, fontWeight = FontWeight.Bold),
    titleMedium = TextStyle(fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 19.sp, lineHeight = 27.sp, fontWeight = FontWeight.Normal),
    bodyMedium = TextStyle(fontSize = 16.sp, lineHeight = 23.sp, fontWeight = FontWeight.Normal),
    labelLarge = TextStyle(fontSize = 15.sp, lineHeight = 21.sp, fontWeight = FontWeight.SemiBold),
    labelMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Bold),
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
    val configuration = LocalConfiguration.current
    val isTv = (configuration.uiMode and Configuration.UI_MODE_TYPE_MASK) == Configuration.UI_MODE_TYPE_TELEVISION
    val deviceClass = when {
        isTv -> AstraWaveDeviceClass.TV
        configuration.screenWidthDp >= 600 -> AstraWaveDeviceClass.TABLET
        else -> AstraWaveDeviceClass.PHONE
    }

    val effectiveSpacing = when (deviceClass) {
        AstraWaveDeviceClass.PHONE -> spacing
        AstraWaveDeviceClass.TABLET -> spacing.copy(screenHorizontal = 30.dp, section = 52.dp)
        AstraWaveDeviceClass.TV -> spacing.copy(screenHorizontal = spacing.tvScreenHorizontal, section = 58.dp)
    }
    val effectiveSizing = when (deviceClass) {
        AstraWaveDeviceClass.PHONE -> sizing
        AstraWaveDeviceClass.TABLET -> sizing.copy(
            posterWidth = 174.dp,
            posterHeight = 261.dp,
            landscapeCardWidth = 300.dp,
            compactCardWidth = 200.dp,
        )
        AstraWaveDeviceClass.TV -> sizing.copy(
            posterWidth = 194.dp,
            posterHeight = 291.dp,
            landscapeCardWidth = 336.dp,
            compactCardWidth = 224.dp,
            focusScale = 1.055f,
        )
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
    val effectiveMotion = if (reducedMotion) {
        motion.copy(fastMs = 0, focusMs = 0, standardMs = 0, slowMs = 0)
    } else {
        motion
    }

    CompositionLocalProvider(
        LocalAstraWaveSpacing provides effectiveSpacing,
        LocalAstraWaveSizing provides effectiveSizing,
        LocalAstraWaveElevation provides elevation,
        LocalAstraWaveOpacity provides opacity,
        LocalAstraWaveMotion provides effectiveMotion,
        LocalAstraWaveLayout provides effectiveLayout,
        LocalAstraWaveDeviceClass provides deviceClass,
    ) {
        MaterialTheme(
            colorScheme = AstraWaveColorScheme,
            typography = typography,
            shapes = AstraWaveShapes,
            content = content,
        )
    }
}
