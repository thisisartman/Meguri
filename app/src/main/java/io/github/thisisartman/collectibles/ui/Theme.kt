package io.github.thisisartman.collectibles.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import io.github.thisisartman.collectibles.data.ItemType

// Tokens from docs/DESIGN.md §4 (contrast and colour-blind checks recorded there).
// Colour means type only; buttons are neutral; collected vs still-to-get is shape, never colour.

private val Light = lightColorScheme(
    primary = Color(0xFF1D1D1F), onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE6E1D8), onPrimaryContainer = Color(0xFF1D1D1F),
    secondaryContainer = Color(0xFFE6E1D8), onSecondaryContainer = Color(0xFF1D1D1F),
    background = Color(0xFFFBF8F3), onBackground = Color(0xFF1D1D1F),
    surface = Color(0xFFFBF8F3), onSurface = Color(0xFF1D1D1F),
    surfaceVariant = Color(0xFFEFE9DF), onSurfaceVariant = Color(0xFF66666B),
    surfaceContainerLowest = Color(0xFFFFFFFF), surfaceContainerLow = Color(0xFFFFFFFF),
    surfaceContainer = Color(0xFFFFFFFF), surfaceContainerHigh = Color(0xFFFFFFFF), surfaceContainerHighest = Color(0xFFFFFFFF),
    outline = Color(0xFF8E8E94), outlineVariant = Color(0xFFE6E1D8),
    inverseSurface = Color(0xFF1D1D1F), inverseOnSurface = Color(0xFFFBF8F3),
)

private val Dark = darkColorScheme(
    primary = Color(0xFFECECEF), onPrimary = Color(0xFF16161A),
    primaryContainer = Color(0xFF33333A), onPrimaryContainer = Color(0xFFECECEF),
    secondaryContainer = Color(0xFF33333A), onSecondaryContainer = Color(0xFFECECEF),
    background = Color(0xFF16161A), onBackground = Color(0xFFECECEF),
    surface = Color(0xFF16161A), onSurface = Color(0xFFECECEF),
    surfaceVariant = Color(0xFF33333A), onSurfaceVariant = Color(0xFFA3A3AB),
    surfaceContainerLowest = Color(0xFF222227), surfaceContainerLow = Color(0xFF222227),
    surfaceContainer = Color(0xFF222227), surfaceContainerHigh = Color(0xFF222227), surfaceContainerHighest = Color(0xFF2A2A30),
    outline = Color(0xFF6E6E76), outlineVariant = Color(0xFF33333A),
    inverseSurface = Color(0xFFECECEF), inverseOnSurface = Color(0xFF16161A),
)

@Immutable
data class TypeColors(val manhole: Color, val goshuin: Color, val ekiStamp: Color, val ekitag: Color, val onSeal: Color, val dash: Color) {
    fun of(type: ItemType): Color = when (type) {
        ItemType.MANHOLE_CARD -> manhole
        ItemType.GOSHUIN -> goshuin
        ItemType.EKI_STAMP -> ekiStamp
        ItemType.EKITAG -> ekitag
    }
}

private val LightTypes = TypeColors(Color(0xFF3A5BA8), Color(0xFFB83A1E), Color(0xFF00806A), Color(0xFF00806A), Color.White, Color(0xFF8E8E94))
private val DarkTypes = TypeColors(Color(0xFF6A87D2), Color(0xFFE2603F), Color(0xFF2EA88B), Color(0xFF2EA88B), Color(0xFF16161A), Color(0xFF6E6E76))

val LocalTypeColors = staticCompositionLocalOf { LightTypes }

@Composable
fun CollectiblesTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    CompositionLocalProvider(LocalTypeColors provides if (dark) DarkTypes else LightTypes) {
        MaterialTheme(colorScheme = if (dark) Dark else Light, content = content)
    }
}
