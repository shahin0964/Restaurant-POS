package com.restaurant.pos.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density

private val LightColorScheme = lightColorScheme(
  primary = BrandPrimary,
  onPrimary = Color.White,
  primaryContainer = Color(0xFFFFEBE8),
  onPrimaryContainer = BrandPrimary,
  secondary = BrandAccent,
  onSecondary = Color.White,
  secondaryContainer = Color(0xFFFFF0E6),
  onSecondaryContainer = BrandAccent,
  tertiary = BrandBrightRed,
  onTertiary = Color.White,
  tertiaryContainer = Color(0xFFFFF5F0),
  onTertiaryContainer = BrandBrightRed,
  background = Color(0xFFFFF8F5),
  onBackground = Color(0xFF1A1A1A),
  surface = Color(0xFFFFFFFF),
  onSurface = Color(0xFF1A1A1A),
  surfaceVariant = Color(0xFFFFF1EB),
  onSurfaceVariant = Color(0xFF666666),
  surfaceTint = BrandPrimary,
  outline = Color(0xFFEEEEEE),
  error = StatusCancelled,
  onError = Color.White
)

private val DarkColorScheme = darkColorScheme(
  primary = BrandPrimary,
  onPrimary = Color.White,
  primaryContainer = Color(0xFF3E1212),
  onPrimaryContainer = Color(0xFFFFB4AB),
  secondary = BrandAccent,
  onSecondary = Color.White,
  secondaryContainer = Color(0xFF3F2100),
  onSecondaryContainer = Color(0xFFFFDCBE),
  tertiary = BrandBrightRed,
  onTertiary = Color.White,
  tertiaryContainer = Color(0xFF3C1410),
  onTertiaryContainer = Color(0xFFFFDAD4),
  background = Color(0xFF121212),
  onBackground = Color(0xFFF5F5F5),
  surface = Color(0xFF1E1E1E),
  onSurface = Color(0xFFF5F5F5),
  surfaceVariant = Color(0xFF2B2523),
  onSurfaceVariant = Color(0xFFB3B3B3),
  surfaceTint = BrandPrimary,
  outline = Color(0xFF333333),
  error = StatusCancelled,
  onError = Color.White
)

@Composable
fun DynamicRestaurantTheme(
  themeMode: String = "system",
  content: @Composable () -> Unit,
) {
  val isDark = when (themeMode) {
    "dark" -> true
    "light" -> false
    else -> isSystemInDarkTheme()
  }
  val appColors = if (isDark) DarkAppThemeColors else LightAppThemeColors
  val colorScheme = if (isDark) DarkColorScheme else LightColorScheme

  val currentDensity = LocalDensity.current
  val compactDensity = Density(
    density = currentDensity.density * 0.91f,
    fontScale = (currentDensity.fontScale.coerceIn(0.80f, 1.05f)) * 0.94f
  )

  CompositionLocalProvider(
    LocalDensity provides compactDensity,
    LocalAppColors provides appColors
  ) {
    MaterialTheme(
      colorScheme = colorScheme,
      typography = Typography,
      content = content
    )
  }
}


