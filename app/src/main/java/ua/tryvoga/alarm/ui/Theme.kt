package ua.tryvoga.alarm.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import ua.tryvoga.alarm.AlertLevel
import ua.tryvoga.alarm.AppSettings
import ua.tryvoga.alarm.DarkMode
import ua.tryvoga.alarm.UiStyle

val LocalUiStyle = staticCompositionLocalOf { UiStyle.GLASS }
val LocalIsDark = staticCompositionLocalOf { true }

val AlertRed = Color(0xFFFF453A)
val AlertYellow = Color(0xFFFFC50A)
val AlertGreen = Color(0xFF30D158)
val AlertGray = Color(0xFF8E8E93)

fun AlertLevel.color(): Color = when (this) {
    AlertLevel.RED -> AlertRed
    AlertLevel.YELLOW -> AlertYellow
    AlertLevel.NONE -> AlertGreen
    AlertLevel.UNKNOWN -> AlertGray
}

private val GlassDark = darkColorScheme(
    primary = Color(0xFF8AB4FF),
    onPrimary = Color(0xFF041A3F),
    secondary = Color(0xFFC4A1FF),
    tertiary = Color(0xFF7DE3F4),
    background = Color(0xFF070B1A),
    onBackground = Color(0xFFF2F4FF),
    surface = Color(0xFF111631),
    onSurface = Color(0xFFF2F4FF),
    onSurfaceVariant = Color(0xFFB7BDD8),
    surfaceContainerHigh = Color(0xFF1A2042),
    outline = Color(0x55FFFFFF),
)

private val GlassLight = lightColorScheme(
    primary = Color(0xFF2F5BEA),
    onPrimary = Color.White,
    secondary = Color(0xFF7C4DFF),
    tertiary = Color(0xFF0891B2),
    background = Color(0xFFE9EEFF),
    onBackground = Color(0xFF111528),
    surface = Color(0xFFF7F8FF),
    onSurface = Color(0xFF111528),
    onSurfaceVariant = Color(0xFF4F5672),
    surfaceContainerHigh = Color(0xFFFFFFFF),
    outline = Color(0x33000000),
)

private val M3Dark = darkColorScheme(primary = Color(0xFFA8C7FA), secondary = Color(0xFFC2C7CF), tertiary = Color(0xFFD7BDE4))
private val M3Light = lightColorScheme(primary = Color(0xFF3A5BA9), secondary = Color(0xFF575E71), tertiary = Color(0xFF715573))

@Composable
fun AppTheme(settings: AppSettings, content: @Composable () -> Unit) {
    val dark = when (settings.darkMode) {
        DarkMode.SYSTEM -> isSystemInDarkTheme()
        DarkMode.LIGHT -> false
        DarkMode.DARK -> true
    }
    val ctx = LocalContext.current
    val scheme = when {
        settings.style == UiStyle.GLASS -> if (dark) GlassDark else GlassLight
        settings.dynamicColor && Build.VERSION.SDK_INT >= 31 -> if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        else -> if (dark) M3Dark else M3Light
    }
    CompositionLocalProvider(LocalUiStyle provides settings.style, LocalIsDark provides dark) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
