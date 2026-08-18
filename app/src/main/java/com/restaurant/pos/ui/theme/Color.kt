package com.restaurant.pos.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// Master Color Palette (Red + Orange Theme)
val BrandPrimary = Color(0xFFFF1A1A)       // Strong Vibrant Red (#FF1A1A)
val BrandPrimaryDark = Color(0xFFD00000)   // Dark / Pressed Red (#D00000)
val BrandAccent = Color(0xFFFF7A00)        // Bright Orange Accent (#FF7A00)
val BrandHighlight = Color(0xFFFF9500)     // Bright Orange Highlight (#FF9500)
val BrandBrightRed = Color(0xFFFF3333)     // Bright Red (#FF3333)
val BrandGlow = Color(0xFFFF7A00)          // Soft Red-Orange Glow

data class AppThemeColors(
    val background: Color,
    val surface: Color,
    val surfaceVariant: Color,
    val inputBackground: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val borderOutline: Color,
    val isDark: Boolean
)

val LightAppThemeColors = AppThemeColors(
    background = Color(0xFFFFF8F5),     // Light warm background canvas (#FFF8F5)
    surface = Color(0xFFFFFFFF),        // Clean White card background (#FFFFFF)
    surfaceVariant = Color(0xFFFFF1EB), // Soft Red/Orange container tint
    inputBackground = Color(0xFFFAFAFA),// Light Input Fields
    textPrimary = Color(0xFF1A1A1A),    // Dark charcoal text (#1A1A1A)
    textSecondary = Color(0xFF666666),  // Soft dark gray text (#666666)
    textMuted = Color(0xFF9E9E9E),      // Muted placeholder gray
    borderOutline = Color(0xFFEEEEEE),  // Light subtle border
    isDark = false
)

val DarkAppThemeColors = AppThemeColors(
    background = Color(0xFF121212),     // Deep dark background (#121212)
    surface = Color(0xFF1E1E1E),        // Dark card / surface (#1E1E1E)
    surfaceVariant = Color(0xFF2B2523), // Dark surface with subtle warm tint
    inputBackground = Color(0xFF282828),// Dark input fields
    textPrimary = Color(0xFFF5F5F5),    // Crisp light text for readability (#F5F5F5)
    textSecondary = Color(0xFFB3B3B3),  // Soft light gray text (#B3B3B3)
    textMuted = Color(0xFF757575),      // Muted text (#757575)
    borderOutline = Color(0xFF333333),  // Dark subtle border (#333333)
    isDark = true
)

val LocalAppColors = staticCompositionLocalOf { LightAppThemeColors }

// Dynamic Theme References
val DarkBackground: Color
    @Composable get() = LocalAppColors.current.background

val DarkSurface: Color
    @Composable get() = LocalAppColors.current.surface

val DarkSurfaceVariant: Color
    @Composable get() = LocalAppColors.current.surfaceVariant

val DarkInputBackground: Color
    @Composable get() = LocalAppColors.current.inputBackground

val LightBackground: Color
    @Composable get() = if (LocalAppColors.current.isDark) LocalAppColors.current.surfaceVariant else Color(0xFFFFF8F5)

val LightSurface: Color
    @Composable get() = LocalAppColors.current.surface

val LightSurfaceVariant: Color
    @Composable get() = LocalAppColors.current.surfaceVariant

val LightInputBackground: Color
    @Composable get() = LocalAppColors.current.inputBackground

val TextPrimary: Color
    @Composable get() = LocalAppColors.current.textPrimary

val TextSecondary: Color
    @Composable get() = LocalAppColors.current.textSecondary

val TextMuted: Color
    @Composable get() = LocalAppColors.current.textMuted

val CurrencyGold = Color(0xFFFF1A1A)       // Vibrant Red Action Color (#FF1A1A)
val CurrencyOrange = Color(0xFFFF7A00)     // Bright Orange Accent (#FF7A00)

val BorderOutline: Color
    @Composable get() = LocalAppColors.current.borderOutline

// Status Colors
val StatusPending = Color(0xFFFF7A00)     // Orange (#FF7A00)
val StatusPreparing = Color(0xFF2196F3)   // Blue
val StatusReady = Color(0xFF10B981)       // Green
val StatusCompleted = Color(0xFF059669)   // Dark Green
val StatusCancelled = Color(0xFFFF1A1A)   // Red



