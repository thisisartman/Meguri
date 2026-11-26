package io.github.thisisartman.collectibles.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import io.github.thisisartman.collectibles.data.ItemType

// Palette B, "seal gold" (owner's pick, 2026-11-06, #57): one Meguri accent, gold, for the main action and what is
// selected; type colours, dashed slots and seals unchanged. Contrast: Design/colour-2026-11-06/pairs*.csv, 53 pairs pass.

private val Light = lightColorScheme(
    primary = Color(0xFF8A5A00), onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFF4E6C8), onPrimaryContainer = Color(0xFF5C3C00),
    secondaryContainer = Color(0xFFF4E6C8), onSecondaryContainer = Color(0xFF5C3C00),
    secondary = Color(0xFF8A5A00), onSecondary = Color(0xFFFFFFFF),
    tertiary = Color(0xFF8A5A00), onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFF4E6C8), onTertiaryContainer = Color(0xFF5C3C00),
    inversePrimary = Color(0xFFE0B252),
    background = Color(0xFFFBF8F3), onBackground = Color(0xFF1D1D1F),
    surface = Color(0xFFFBF8F3), onSurface = Color(0xFF1D1D1F),
    surfaceVariant = Color(0xFFEFE9DF), onSurfaceVariant = Color(0xFF66666B),
    surfaceContainerLowest = Color(0xFFFFFFFF), surfaceContainerLow = Color(0xFFFFFFFF),
    surfaceContainer = Color(0xFFFFFFFF), surfaceContainerHigh = Color(0xFFFFFFFF), surfaceContainerHighest = Color(0xFFFFFFFF),
    outline = Color(0xFF8E8E94), outlineVariant = Color(0xFFE6E1D8),
    inverseSurface = Color(0xFF1D1D1F), inverseOnSurface = Color(0xFFFBF8F3),
)

private val Dark = darkColorScheme(
    primary = Color(0xFFE0B252), onPrimary = Color(0xFF1A1400),
    primaryContainer = Color(0xFF3A3120), onPrimaryContainer = Color(0xFFF3D9A0),
    secondaryContainer = Color(0xFF3A3120), onSecondaryContainer = Color(0xFFF3D9A0),
    secondary = Color(0xFFE0B252), onSecondary = Color(0xFF1A1400),
    tertiary = Color(0xFFE0B252), onTertiary = Color(0xFF1A1400),
    tertiaryContainer = Color(0xFF3A3120), onTertiaryContainer = Color(0xFFF3D9A0),
    inversePrimary = Color(0xFF8A5A00),
    background = Color(0xFF17151F), onBackground = Color(0xFFF1ECE2),
    surface = Color(0xFF17151F), onSurface = Color(0xFFF1ECE2),
    surfaceVariant = Color(0xFF33303D), onSurfaceVariant = Color(0xFFB3ACBD),
    surfaceContainerLowest = Color(0xFF221F2B), surfaceContainerLow = Color(0xFF221F2B),
    surfaceContainer = Color(0xFF221F2B), surfaceContainerHigh = Color(0xFF221F2B), surfaceContainerHighest = Color(0xFF2C2836),
    outline = Color(0xFF776F84), outlineVariant = Color(0xFF33303D),
    inverseSurface = Color(0xFFF1ECE2), inverseOnSurface = Color(0xFF17151F),
)

/** "Device colours" in More (#57): the phone's wallpaper colours (Android 12+) instead of Meguri's own. */
object AppLook {
    private const val PREFS = "look"
    private const val KEY = "device_colours"
    private val state = androidx.compose.runtime.mutableStateOf<Boolean?>(null)

    fun available(): Boolean = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S

    fun deviceColours(context: android.content.Context): Boolean =
        state.value ?: context.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE).getBoolean(KEY, false).also { state.value = it }

    fun setDeviceColours(context: android.content.Context, on: Boolean) {
        context.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE).edit().putBoolean(KEY, on).apply()
        state.value = on
    }

    /** Theme (owner, 2026-11-09): follow the phone, or always light, or always dark. */
    enum class Theme { PHONE, LIGHT, DARK }
    private const val THEME_KEY = "theme"
    private val themeState = androidx.compose.runtime.mutableStateOf<Theme?>(null)

    fun theme(context: android.content.Context): Theme = themeState.value
        ?: runCatching { Theme.valueOf(context.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE).getString(THEME_KEY, Theme.PHONE.name)!!) }
            .getOrDefault(Theme.PHONE).also { themeState.value = it }

    fun setTheme(context: android.content.Context, t: Theme) {
        context.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE).edit().putString(THEME_KEY, t.name).apply()
        themeState.value = t
    }
}

/** Whether the app is dark now: the phone's dark mode, unless Light or Dark is chosen in Appearance. */
val LocalAppDark = androidx.compose.runtime.staticCompositionLocalOf { false }

@Immutable
data class TypeColors(
    val manhole: Color, val goshuin: Color, val ekiStamp: Color, val ekitag: Color, val onSeal: Color, val dash: Color, val custom: Color,
    val dam: Color, val museum: Color, val roadside: Color,
    /** Places of interest (2026-11-15) */
    val place: Color = Color(0xFFB0306A),
) {
    /** A built-in kind's colour; your own collections use their chosen colour (see [colorOf]), this is the fallback. */
    fun of(type: ItemType): Color = when (type) {
        ItemType.MANHOLE_CARD -> manhole
        ItemType.GOSHUIN -> goshuin
        ItemType.EKI_STAMP -> ekiStamp
        ItemType.EKITAG -> ekitag
        ItemType.CUSTOM -> custom
        ItemType.DAM_CARD -> dam
        ItemType.MUSEUM_STAMP -> museum
        ItemType.ROADSIDE_STATION -> roadside
        ItemType.PLACE -> place
    }
}

// Station stamp red (owner, 2026-11-26, provisional): the stamp and its Ekitag share one tile, so they need two
// colours; Ekitag keeps the teal. One pair here, to change in one place. Contrast on the page: light 5.2:1 on
// #FBF8F3, dark 5.6:1 on #17151F (5.0:1 on cards #221F2B); clear of goshuin's vermilion and the place pink
private val StampRedLight = Color(0xFFC62845)
private val StampRedDark = Color(0xFFF25C6E)

private val LightTypes = TypeColors(Color(0xFF3A5BA8), Color(0xFFB83A1E), StampRedLight, Color(0xFF00806A), Color.White, Color(0xFF8E8E94), Color(0xFF7A5AA8), Color(0xFF5A6470), Color(0xFF5F6B12), Color(0xFFB85C00))
private val DarkTypes = TypeColors(Color(0xFF6A87D2), Color(0xFFE2603F), StampRedDark, Color(0xFF2EA88B), Color(0xFF16161A), Color(0xFF6E6E76), Color(0xFFA88AD2), Color(0xFF9AA6B2), Color(0xFFA8B84E), Color(0xFFE89A4A), place = Color(0xFFE0709E))

val LocalTypeColors = staticCompositionLocalOf { LightTypes }

/**
 * The app's outlined button. Material's default draws the border in outlineVariant, the divider colour
 * (about 1.2:1 on the page), and the label in grey, so real actions looked disabled (overnight review round 2).
 * Here the border is outline (3:1 or more) and the label is ink, with a 48 dp minimum height.
 */
@Composable
fun InkOutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    content: @Composable RowScope.() -> Unit,
) = OutlinedButton(
    onClick = onClick,
    modifier = modifier.heightIn(min = 48.dp),
    enabled = enabled,
    border = BorderStroke(1.dp, if (enabled) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.outlineVariant),
    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
    contentPadding = contentPadding,
    content = content,
)

/**
 * The app's filter chip. Material's unselected border is the divider colour (about 1.4:1) and the selected state
 * shows by fill alone (overnight review round 6). Here the border is the outline colour, the selected chip gets an
 * ink border and a check, and the target is 48 dp. [leadingIcon] (a type dot) stays beside the check.
 */
@Composable
fun InkFilterChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: (@Composable () -> Unit)? = null,
) = androidx.compose.material3.FilterChip(
    selected = selected,
    onClick = onClick,
    label = label,
    modifier = modifier.heightIn(min = 48.dp),
    leadingIcon = if (selected) {
        {
            androidx.compose.foundation.layout.Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                androidx.compose.material3.Icon(
                    androidx.compose.material.icons.Icons.Filled.Check, contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                if (leadingIcon != null) { androidx.compose.foundation.layout.Spacer(Modifier.width(4.dp)); leadingIcon() }
            }
        }
    } else leadingIcon,
    border = androidx.compose.material3.FilterChipDefaults.filterChipBorder(
        enabled = true, selected = selected,
        borderColor = MaterialTheme.colorScheme.outline,
        selectedBorderColor = MaterialTheme.colorScheme.primary,
        borderWidth = 1.dp, selectedBorderWidth = 1.dp,
    ),
)

/** Material's type scale with phrase-based line breaks: affects Japanese only, Latin text breaks as before. */
private val PhraseTypography: androidx.compose.material3.Typography = androidx.compose.material3.Typography().let { t ->
    fun androidx.compose.ui.text.TextStyle.phrase() = copy(
        lineBreak = androidx.compose.ui.text.style.LineBreak.Paragraph.copy(wordBreak = androidx.compose.ui.text.style.LineBreak.WordBreak.Phrase),
    )
    t.copy(
        displayLarge = t.displayLarge.phrase(), displayMedium = t.displayMedium.phrase(), displaySmall = t.displaySmall.phrase(),
        headlineLarge = t.headlineLarge.phrase(), headlineMedium = t.headlineMedium.phrase(), headlineSmall = t.headlineSmall.phrase(),
        titleLarge = t.titleLarge.phrase(), titleMedium = t.titleMedium.phrase(), titleSmall = t.titleSmall.phrase(),
        bodyLarge = t.bodyLarge.phrase(), bodyMedium = t.bodyMedium.phrase(), bodySmall = t.bodySmall.phrase(),
        labelLarge = t.labelLarge.phrase(), labelMedium = t.labelMedium.phrase(), labelSmall = t.labelSmall.phrase(),
    )
}

@Composable
fun CollectiblesTheme(content: @Composable () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val dark = when (AppLook.theme(context)) {
        AppLook.Theme.LIGHT -> false
        AppLook.Theme.DARK -> true
        AppLook.Theme.PHONE -> isSystemInDarkTheme()
    }
    // Type colours stay Meguri's own either way: colour means type
    val scheme = if (AppLook.available() && AppLook.deviceColours(context)) {
        if (dark) androidx.compose.material3.dynamicDarkColorScheme(context) else androidx.compose.material3.dynamicLightColorScheme(context)
    } else if (dark) Dark else Light
    // Status and navigation bar icons follow the app's theme, not the phone's (dark icons on Light)
    val view = androidx.compose.ui.platform.LocalView.current
    if (!view.isInEditMode) androidx.compose.runtime.SideEffect {
        val window = (view.context as? android.app.Activity)?.window ?: return@SideEffect
        androidx.core.view.WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = !dark
            isAppearanceLightNavigationBars = !dark
        }
    }
    CompositionLocalProvider(LocalTypeColors provides if (dark) DarkTypes else LightTypes, LocalAppDark provides dark) {
        MaterialTheme(colorScheme = scheme, typography = PhraseTypography, content = content)
    }
}
