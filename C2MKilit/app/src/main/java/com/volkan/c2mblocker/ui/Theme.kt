package com.volkan.c2mblocker.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val LightColors = lightColorScheme(
    primary = Color(0xFF2563EB), onPrimary = Color.White,
    primaryContainer = Color(0xFFDCE6FF), onPrimaryContainer = Color(0xFF0B2A6B),
    secondary = Color(0xFF475569), onSecondary = Color.White,
    secondaryContainer = Color(0xFFE2E8F0), onSecondaryContainer = Color(0xFF1E293B),
    tertiary = Color(0xFF047857), onTertiary = Color.White,
    tertiaryContainer = Color(0xFFD1FAE5), onTertiaryContainer = Color(0xFF064E3B),
    error = Color(0xFFDC2626), onError = Color.White,
    errorContainer = Color(0xFFFEE2E2), onErrorContainer = Color(0xFF7F1D1D),
    background = Color(0xFFF8FAFC), onBackground = Color(0xFF0F172A),
    surface = Color(0xFFF8FAFC), onSurface = Color(0xFF0F172A),
    surfaceVariant = Color(0xFFE9EFF8), onSurfaceVariant = Color(0xFF475569),
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color.White,
    surfaceContainer = Color(0xFFF1F5F9), surfaceContainerHigh = Color(0xFFE9EFF8),
    surfaceContainerHighest = Color(0xFFE2E8F0),
    outline = Color(0xFFCBD5E1), outlineVariant = Color(0xFFE2E8F0),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF8AB4FF), onPrimary = Color(0xFF0A2A66),
    primaryContainer = Color(0xFF1E3A8A), onPrimaryContainer = Color(0xFFDCE6FF),
    secondary = Color(0xFF94A3B8), onSecondary = Color(0xFF0F172A),
    secondaryContainer = Color(0xFF1E293B), onSecondaryContainer = Color(0xFFE2E8F0),
    tertiary = Color(0xFF34D399), onTertiary = Color(0xFF003824),
    tertiaryContainer = Color(0xFF064E3B), onTertiaryContainer = Color(0xFFD1FAE5),
    error = Color(0xFFF87171), onError = Color(0xFF450A0A),
    errorContainer = Color(0xFF7F1D1D), onErrorContainer = Color(0xFFFEE2E2),
    background = Color(0xFF0B1020), onBackground = Color(0xFFE2E8F0),
    surface = Color(0xFF0B1020), onSurface = Color(0xFFE2E8F0),
    surfaceVariant = Color(0xFF1E2638), onSurfaceVariant = Color(0xFF9AA8BD),
    surfaceContainerLowest = Color(0xFF080C18), surfaceContainerLow = Color(0xFF121829),
    surfaceContainer = Color(0xFF161D30), surfaceContainerHigh = Color(0xFF1C2438),
    surfaceContainerHighest = Color(0xFF242D44),
    outline = Color(0xFF3A4660), outlineVariant = Color(0xFF232C40),
)

/**
 * Marka renkleri: logodaki maviden yeşile geçiş. Kahraman kartları bu gradyanı kullanır;
 * üstlerindeki beyaz yazı iki temada da en az 4.5:1 kontrast sağlar.
 */
object Brand {
    val Blue = Color(0xFF1D4ED8)
    val Teal = Color(0xFF0F766E)
    val AlertRed = Color(0xFFB91C1C)
    val AlertOrange = Color(0xFF9A3412)
    val OnHero = Color(0xFFF8FAFC)
    val OnHeroMuted = Color(0xFFEDF2F7)
    val HeroChip = Color(0x29FFFFFF)
    val active get() = Brush.linearGradient(listOf(Blue, Teal))
    val alert get() = Brush.linearGradient(listOf(AlertRed, AlertOrange))
}

private val AppTypography = Typography().let { t ->
    t.copy(
        headlineLarge = t.headlineLarge.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
        headlineMedium = t.headlineMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.25).sp),
        displaySmall = t.displaySmall.copy(fontWeight = FontWeight.Bold, letterSpacing = (-1).sp),
        titleLarge = t.titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = t.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = t.labelLarge.copy(fontWeight = FontWeight.SemiBold),
    )
}

private val AppShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun C2MTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        typography = AppTypography,
        shapes = AppShapes,
        content = content,
    )
}
