package com.moog.dialer.ui.theme

import android.content.Context
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

enum class MoogAppearanceMode(val storedValue: String) {
    SYSTEM("system"),
    LIGHT("light"),
    DARK("dark"),
    DYNAMIC("dynamic");

    companion object {
        fun fromStoredValue(value: String?): MoogAppearanceMode =
            entries.firstOrNull { it.storedValue == value } ?: SYSTEM
    }
}

object MoogAppearancePreferences {
    private const val PREFS = "moogs_dialer_preferences"
    private const val KEY_APPEARANCE = "appearance_mode"

    fun get(context: Context): MoogAppearanceMode =
        MoogAppearanceMode.fromStoredValue(
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_APPEARANCE, "system")
        )

    fun set(context: Context, mode: MoogAppearanceMode) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_APPEARANCE, mode.storedValue)
            .apply()
    }
}

/**
 * Signature accent options:
 * - Analog Amber (default): warm amber over ink, inspired by classic synth controls.
 * - Signal Teal: deep teal over cool graphite for a quieter studio aesthetic.
 *
 * Both sets keep call green and destructive red semantically separate from the brand accent.
 */
object MoogAccentPalettes {
    val analogAmberLight = Color(0xFF8A4B00)
    val analogAmberDark = Color(0xFFFFB547)
    val signalTealLight = Color(0xFF006B66)
    val signalTealDark = Color(0xFF55D6C8)
}

data class MoogColorTokens(
    val background: Color,
    val surface: Color,
    val label: Color,
    val secondaryLabel: Color,
    val separator: Color,
    val accent: Color,
    val green: Color,
    val red: Color,
    val fill: Color,
    val keyFill: Color
) {
    /** Compatibility alias for existing screens; this is now the MOOG signature accent, not blue. */
    val blue: Color get() = accent
}

private val LightTokens = MoogColorTokens(
    background = Color(0xFFF5F3EE),
    surface = Color(0xFFFFFEFA),
    label = Color(0xFF171714),
    secondaryLabel = Color(0xFF4C4A43),
    separator = Color(0xFF77736A).copy(alpha = 0.42f),
    accent = MoogAccentPalettes.analogAmberLight,
    green = Color(0xFF187A3D),
    red = Color(0xFFB3261E),
    fill = Color(0xFFEAE6DD),
    keyFill = Color(0xFFEAE6DD)
)

private val DarkTokens = MoogColorTokens(
    background = Color(0xFF111210),
    surface = Color(0xFF20221E),
    label = Color(0xFFF5F3EC),
    secondaryLabel = Color(0xFFC3C0B6),
    separator = Color(0xFF777A70).copy(alpha = 0.62f),
    accent = MoogAccentPalettes.analogAmberDark,
    green = Color(0xFF168047),
    red = Color(0xFFFF6B60),
    fill = Color(0xFF30332D),
    keyFill = Color(0xFF30332D)
)

private val LocalMoogColors = staticCompositionLocalOf { LightTokens }

object MoogColors {
    val background: Color @Composable get() = LocalMoogColors.current.background
    val surface: Color @Composable get() = LocalMoogColors.current.surface
    val label: Color @Composable get() = LocalMoogColors.current.label
    val secondaryLabel: Color @Composable get() = LocalMoogColors.current.secondaryLabel
    val separator: Color @Composable get() = LocalMoogColors.current.separator
    val accent: Color @Composable get() = LocalMoogColors.current.accent
    /** Compatibility alias; new UI should prefer accent. */
    val blue: Color @Composable get() = accent
    val green: Color @Composable get() = LocalMoogColors.current.green
    val red: Color @Composable get() = LocalMoogColors.current.red
    val fill: Color @Composable get() = LocalMoogColors.current.fill
    val keyFill: Color @Composable get() = LocalMoogColors.current.keyFill
}

private val MoogDisplayFont = FontFamily(
    android.graphics.Typeface.create("sans-serif-rounded", android.graphics.Typeface.NORMAL)
)

private val MoogTypography = androidx.compose.material3.Typography(
    displayLarge = TextStyle(fontFamily = MoogDisplayFont, fontSize = 34.sp, lineHeight = 41.sp, fontWeight = FontWeight.Bold),
    headlineLarge = TextStyle(fontFamily = MoogDisplayFont, fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold),
    headlineMedium = TextStyle(fontFamily = MoogDisplayFont, fontSize = 24.sp, lineHeight = 30.sp, fontWeight = FontWeight.SemiBold),
    titleLarge = TextStyle(fontFamily = MoogDisplayFont, fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 17.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontSize = 15.sp, lineHeight = 21.sp),
    bodySmall = TextStyle(fontSize = 13.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
    labelMedium = TextStyle(fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium)
)

/**
 * Shared MOOG theme. Appearance is persisted as System/Light/Dark/Dynamic and defaults to System.
 * forceDark is reserved for the immersive in-call experience.
 */
@Composable
fun MoogTheme(
    forceDark: Boolean = false,
    content: @Composable () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var appearance by remember(context) { mutableStateOf(MoogAppearancePreferences.get(context)) }

    DisposableEffect(context) {
        val preferences = context.getSharedPreferences("moogs_dialer_preferences", Context.MODE_PRIVATE)
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == "appearance_mode") appearance = MoogAppearancePreferences.get(context)
        }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        onDispose { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    val systemDark = androidx.compose.foundation.isSystemInDarkTheme()
    val dark = forceDark || when (appearance) {
        MoogAppearanceMode.SYSTEM, MoogAppearanceMode.DYNAMIC -> systemDark
        MoogAppearanceMode.LIGHT -> false
        MoogAppearanceMode.DARK -> true
    }
    val dynamicAvailable = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val dynamicScheme = if (appearance == MoogAppearanceMode.DYNAMIC && dynamicAvailable && !forceDark) {
        if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else null

    val baseTokens = if (dark) DarkTokens else LightTokens
    val tokens = dynamicScheme?.let {
        baseTokens.copy(
            background = it.background,
            surface = it.surface,
            label = it.onBackground,
            secondaryLabel = it.onSurfaceVariant,
            separator = it.outline,
            accent = it.primary,
            fill = it.surfaceVariant,
            keyFill = it.surfaceVariant
        )
    } ?: baseTokens

    val scheme = dynamicScheme ?: if (dark) {
        darkColorScheme(
            primary = tokens.accent,
            onPrimary = Color(0xFF211507),
            secondary = tokens.green,
            onSecondary = Color(0xFF071B0E),
            tertiary = tokens.red,
            onTertiary = Color.White,
            background = tokens.background,
            onBackground = tokens.label,
            surface = tokens.surface,
            onSurface = tokens.label,
            surfaceVariant = tokens.fill,
            onSurfaceVariant = tokens.secondaryLabel,
            outline = tokens.separator,
            outlineVariant = tokens.separator,
            error = tokens.red,
            onError = Color.White
        )
    } else {
        lightColorScheme(
            primary = tokens.accent,
            onPrimary = Color.White,
            secondary = tokens.green,
            onSecondary = Color.White,
            tertiary = tokens.red,
            onTertiary = Color.White,
            background = tokens.background,
            onBackground = tokens.label,
            surface = tokens.surface,
            onSurface = tokens.label,
            surfaceVariant = tokens.fill,
            onSurfaceVariant = tokens.secondaryLabel,
            outline = tokens.separator,
            outlineVariant = tokens.separator,
            error = tokens.red,
            onError = Color.White
        )
    }

    androidx.compose.runtime.CompositionLocalProvider(LocalMoogColors provides tokens) {
        MaterialTheme(colorScheme = scheme, typography = MoogTypography, content = content)
    }
}
