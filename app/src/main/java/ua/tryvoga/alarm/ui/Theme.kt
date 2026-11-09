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

// «Кристал циферблата»: чорне скло, білі лінії; колір — лише статуси тривоги (див. AlertRed тощо).
private val GlassDark = darkColorScheme(
    primary = Color(0xFFFFFFFF),
    onPrimary = Color(0xFF000000),
    secondary = Color(0x99FFFFFF),
    onSecondary = Color(0xFF000000),
    tertiary = Color(0xFFFFFFFF),
    background = Color(0xFF000000),
    onBackground = Color(0xFFFFFFFF),
    surface = Color(0xFF0E0F10),
    onSurface = Color(0xFFFFFFFF),
    onSurfaceVariant = Color(0xB3FFFFFF), // 70%
    surfaceVariant = Color(0xFF1A1B1D),
    surfaceContainerHigh = Color(0xFF141517),
    outline = Color(0x33FFFFFF),
    outlineVariant = Color(0x1FFFFFFF),
)

private val GlassLight = lightColorScheme(
    primary = Color(0xFF0B0C0E),
    onPrimary = Color(0xFFFFFFFF),
    secondary = Color(0x990B0C0E),
    onSecondary = Color(0xFFFFFFFF),
    tertiary = Color(0xFF0B0C0E),
    background = Color(0xFFE8E9EB),
    onBackground = Color(0xFF0B0C0E),
    surface = Color(0xFFF4F5F6),
    onSurface = Color(0xFF0B0C0E),
    onSurfaceVariant = Color(0xB30B0C0E), // 70%
    surfaceVariant = Color(0xFFDCDDE0),
    surfaceContainerHigh = Color(0xFFF4F5F6),
    outline = Color(0x330B0C0E),
    outlineVariant = Color(0x140B0C0E),
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
