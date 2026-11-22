package com.moog.dialer.ui.theme

import android.content.Context
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

enum class MoogAppearanceMode(val storedValue: String) {
    SYSTEM("system"),
    LIGHT("light"),
    DARK("dark");

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

data class MoogColorTokens(
    val background: Color,
    val surface: Color,
    val label: Color,
    val secondaryLabel: Color,
    val separator: Color,
    val blue: Color,
    val green: Color,
    val red: Color,
    val fill: Color,
    val keyFill: Color
)

private val LightTokens = MoogColorTokens(
    background = Color(0xFFF2F2F7),
    surface = Color(0xFFFFFFFF),
    label = Color(0xFF000000),
    secondaryLabel = Color(0xFF3C3C43).copy(alpha = 0.60f),
    separator = Color(0xFF3C3C43).copy(alpha = 0.29f),
    blue = Color(0xFF007AFF),
    green = Color(0xFF34C759),
    red = Color(0xFFFF3B30),
    fill = Color(0xFFE9E9EB),
    keyFill = Color(0xFFE9E9EB)
)

private val DarkTokens = MoogColorTokens(
    background = Color(0xFF000000),
    surface = Color(0xFF1C1C1E),
    label = Color(0xFFFFFFFF),
    secondaryLabel = Color(0xFFEBEBF5).copy(alpha = 0.60f),
    separator = Color(0xFF545458).copy(alpha = 0.60f),
    blue = Color(0xFF0A84FF),
    green = Color(0xFF30D158),
    red = Color(0xFFFF453A),
    fill = Color(0xFF333333),
    keyFill = Color(0xFF333333)
)

private val LocalMoogColors = staticCompositionLocalOf { LightTokens }

object MoogColors {
    val background: Color @Composable get() = LocalMoogColors.current.background
    val surface: Color @Composable get() = LocalMoogColors.current.surface
    val label: Color @Composable get() = LocalMoogColors.current.label
    val secondaryLabel: Color @Composable get() = LocalMoogColors.current.secondaryLabel
    val separator: Color @Composable get() = LocalMoogColors.current.separator
    val blue: Color @Composable get() = LocalMoogColors.current.blue
    val green: Color @Composable get() = LocalMoogColors.current.green
    val red: Color @Composable get() = LocalMoogColors.current.red
    val fill: Color @Composable get() = LocalMoogColors.current.fill
    val keyFill: Color @Composable get() = LocalMoogColors.current.keyFill
}

private val MoogTypography = androidx.compose.material3.Typography(
    displayLarge = TextStyle(fontSize = 34.sp, lineHeight = 41.sp, fontWeight = FontWeight.Bold),
    headlineLarge = TextStyle(fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold),
    headlineMedium = TextStyle(fontSize = 24.sp, lineHeight = 30.sp, fontWeight = FontWeight.SemiBold),
    titleLarge = TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 17.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontSize = 15.sp, lineHeight = 21.sp),
    bodySmall = TextStyle(fontSize = 13.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
    labelMedium = TextStyle(fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium)
)

/**
 * Shared MOOG theme. Appearance is persisted as System/Light/Dark and defaults to System.
 * forceDark is reserved for the immersive in-call experience.
 */
@Composable
fun MoogTheme(
    forceDark: Boolean = false,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    var appearance by remember(context) { mutableStateOf(MoogAppearancePreferences.get(context)) }

    DisposableEffect(context) {
        val preferences = context.getSharedPreferences("moogs_dialer_preferences", Context.MODE_PRIVATE)
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == "appearance_mode") appearance = MoogAppearancePreferences.get(context)
        }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        onDispose { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    val dark = forceDark || when (appearance) {
        MoogAppearanceMode.SYSTEM -> isSystemInDarkTheme()
        MoogAppearanceMode.LIGHT -> false
        MoogAppearanceMode.DARK -> true
    }
    val tokens = if (dark) DarkTokens else LightTokens
    val scheme = if (dark) {
        darkColorScheme(
            primary = tokens.blue,
            onPrimary = Color.White,
            secondary = tokens.green,
            onSecondary = Color.Black,
            tertiary = tokens.red,
            background = tokens.background,
            onBackground = tokens.label,
            surface = tokens.surface,
            onSurface = tokens.label,
            surfaceVariant = tokens.fill,
            onSurfaceVariant = tokens.secondaryLabel,
            outline = tokens.separator,
            outlineVariant = tokens.separator,
            error = tokens.red
        )
    } else {
        lightColorScheme(
            primary = tokens.blue,
            onPrimary = Color.White,
            secondary = tokens.green,
            onSecondary = Color.White,
            tertiary = tokens.red,
            background = tokens.background,
            onBackground = tokens.label,
            surface = tokens.surface,
            onSurface = tokens.label,
            surfaceVariant = tokens.fill,
            onSurfaceVariant = tokens.secondaryLabel,
            outline = tokens.separator,
            outlineVariant = tokens.separator,
            error = tokens.red
        )
    }

    androidx.compose.runtime.CompositionLocalProvider(LocalMoogColors provides tokens) {
        MaterialTheme(colorScheme = scheme, typography = MoogTypography, content = content)
    }
}
