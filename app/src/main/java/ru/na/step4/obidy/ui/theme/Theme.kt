package ru.na.step4.obidy.ui.theme

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** Все цвета приложения: светлая и тёмная палитры отличаются только значениями. */
data class AppPalette(
    val forest: Color,
    val forestMuted: Color,
    val moss: Color,
    val sand: Color,
    val sandDeep: Color,
    val ink: Color,
    val inkSoft: Color,
    val amber: Color,
    val amberSoft: Color,
    val danger: Color
)

private val LightPalette = AppPalette(
    forest = Color(0xFF1B2E24),
    forestMuted = Color(0xFF2F4A3C),
    moss = Color(0xFF4A6B58),
    sand = Color(0xFFF3EFE6),
    sandDeep = Color(0xFFE6DFD0),
    ink = Color(0xFF1A1F1C),
    inkSoft = Color(0xFF4A524C),
    amber = Color(0xFFB8893D),
    amberSoft = Color(0xFFD4B06A),
    danger = Color(0xFF8B3A3A)
)

/**
 * Тёмная палитра обратна светлой: фон и текст меняются местами, акценты
 * осветляются — иначе зелёные надписи и кнопки не читаются на тёмном фоне.
 */
private val DarkPalette = AppPalette(
    forest = Color(0xFFB9D3C2),
    forestMuted = Color(0xFF33513F),
    moss = Color(0xFF8FB49C),
    sand = Color(0xFF121A15),
    sandDeep = Color(0xFF1D2821),
    ink = Color(0xFFE9EFE9),
    inkSoft = Color(0xFFAEB9B1),
    amber = Color(0xFFD9AC5E),
    amberSoft = Color(0xFFE7C77F),
    danger = Color(0xFFE0928F)
)

private const val THEME_PREFS = "app_theme"
private const val THEME_DARK_KEY = "dark"

/**
 * Тёмная тема. Состояние, поэтому переключение сразу перекрашивает экраны,
 * и обычное свойство — палитра читается и вне композиции.
 */
var darkTheme by mutableStateOf(false)
    private set

/** Читает сохранённый выбор. Вызывается один раз при старте приложения. */
fun initThemeMode(context: Context) {
    darkTheme = context.applicationContext
        .getSharedPreferences(THEME_PREFS, Context.MODE_PRIVATE)
        .getBoolean(THEME_DARK_KEY, false)
}

/** Включает или выключает тёмную тему и запоминает выбор. */
fun setDarkTheme(context: Context, enabled: Boolean) {
    context.applicationContext
        .getSharedPreferences(THEME_PREFS, Context.MODE_PRIVATE)
        .edit()
        .putBoolean(THEME_DARK_KEY, enabled)
        .apply()
    darkTheme = enabled
}

/**
 * Цвета текущей темы. Это обычные свойства, а не @Composable: палитра нужна
 * и внутри лямбд отрисовки (градиенты, Canvas), куда композицию не передать.
 */
val palette: AppPalette get() = if (darkTheme) DarkPalette else LightPalette

val Forest: Color get() = palette.forest
val ForestMuted: Color get() = palette.forestMuted
val Moss: Color get() = palette.moss
val Sand: Color get() = palette.sand
val SandDeep: Color get() = palette.sandDeep
val Ink: Color get() = palette.ink
val InkSoft: Color get() = palette.inkSoft
val Amber: Color get() = palette.amber
val AmberSoft: Color get() = palette.amberSoft
val Danger: Color get() = palette.danger

/** Фон окна до первого кадра Compose — совпадает с фоном выбранной темы. */
fun themeWindowColor(): Int = palette.sand.toArgb()

private val LightScheme = lightColorScheme(
    primary = LightPalette.forest,
    onPrimary = LightPalette.sand,
    primaryContainer = LightPalette.forestMuted,
    onPrimaryContainer = LightPalette.sand,
    secondary = LightPalette.amber,
    onSecondary = LightPalette.ink,
    secondaryContainer = LightPalette.amberSoft.copy(alpha = 0.35f),
    onSecondaryContainer = LightPalette.forest,
    tertiary = LightPalette.moss,
    background = LightPalette.sand,
    onBackground = LightPalette.ink,
    surface = LightPalette.sand,
    onSurface = LightPalette.ink,
    surfaceVariant = LightPalette.sandDeep,
    onSurfaceVariant = LightPalette.inkSoft,
    outline = LightPalette.moss.copy(alpha = 0.45f),
    error = LightPalette.danger,
    onError = LightPalette.sand
)

private val DarkScheme = darkColorScheme(
    primary = DarkPalette.forest,
    onPrimary = DarkPalette.sand,
    primaryContainer = DarkPalette.forestMuted,
    onPrimaryContainer = DarkPalette.forest,
    secondary = DarkPalette.amber,
    onSecondary = DarkPalette.sand,
    secondaryContainer = DarkPalette.amberSoft.copy(alpha = 0.35f),
    onSecondaryContainer = DarkPalette.amber,
    tertiary = DarkPalette.moss,
    background = DarkPalette.sand,
    onBackground = DarkPalette.ink,
    surface = DarkPalette.sand,
    onSurface = DarkPalette.ink,
    surfaceVariant = DarkPalette.sandDeep,
    onSurfaceVariant = DarkPalette.inkSoft,
    outline = DarkPalette.moss.copy(alpha = 0.45f),
    error = DarkPalette.danger,
    onError = DarkPalette.sand
)

private val Typography = androidx.compose.material3.Typography(
    displayLarge = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Bold,
        fontSize = 36.sp,
        lineHeight = 42.sp,
        letterSpacing = (-0.5).sp
    ),
    headlineLarge = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 28.sp,
        lineHeight = 34.sp
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Medium,
        fontSize = 20.sp,
        lineHeight = 26.sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        letterSpacing = 0.2.sp
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        letterSpacing = 0.3.sp
    )
)

@Composable
fun Step4Theme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkScheme else LightScheme,
        typography = Typography,
        content = content
    )
}
