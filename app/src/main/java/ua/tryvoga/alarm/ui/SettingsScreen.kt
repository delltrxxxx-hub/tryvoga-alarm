package ua.tryvoga.alarm.ui

import android.app.NotificationManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.speech.tts.TextToSpeech
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AlarmOn
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.launch
import ua.tryvoga.alarm.Alarm
import ua.tryvoga.alarm.AlarmRepo
import ua.tryvoga.alarm.AlarmScheduler
import ua.tryvoga.alarm.AlertApi
import ua.tryvoga.alarm.AlertLevel
import ua.tryvoga.alarm.AppSettings
import ua.tryvoga.alarm.DarkMode
import ua.tryvoga.alarm.Locations
import ua.tryvoga.alarm.SettingsRepo
import ua.tryvoga.alarm.UK
import ua.tryvoga.alarm.UiStyle
import java.time.LocalTime

@Composable
fun SettingsScreen(vm: MainViewModel, s: AppSettings, onRegion: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var testResult by remember { mutableStateOf<String?>(null) }
    var resumeTick by remember { mutableIntStateOf(0) }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) resumeTick++ }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }
    var tts by remember { mutableStateOf<TextToSpeech?>(null) }
    DisposableEffect(Unit) { onDispose { tts?.shutdown() } }

    fun update(f: (AppSettings) -> AppSettings) = SettingsRepo.update(ctx, f)

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
        SectionTitle("Вигляд")
        GlassSurface(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp)) {
                Row { Icon(Icons.Rounded.Palette, null, tint = androidx.compose.material3.MaterialTheme.colorScheme.primary); Spacer(Modifier.width(10.dp)); Text("Стиль", color = onBg(), fontSize = 16.sp) }
                Spacer(Modifier.height(10.dp))
                Segmented(listOf(UiStyle.GLASS, UiStyle.MATERIAL), s.style, { if (it == UiStyle.GLASS) "Liquid Glass" else "Material 3" }, { v -> update { it.copy(style = v) } })
                Spacer(Modifier.height(14.dp))
                Text("Тема", color = onBg(), fontSize = 16.sp)
                Spacer(Modifier.height(10.dp))
                Segmented(listOf(DarkMode.SYSTEM, DarkMode.LIGHT, DarkMode.DARK), s.darkMode, {
                    when (it) { DarkMode.SYSTEM -> "Як у системі"; DarkMode.LIGHT -> "Світла"; DarkMode.DARK -> "Темна" }
                }, { v -> update { it.copy(darkMode = v) } })
                AnimatedVisibility(s.style == UiStyle.MATERIAL && Build.VERSION.SDK_INT >= 31) {
                    Row(Modifier.padding(top = 12.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Text("Кольори Material You (з шпалер)", color = onBg(), modifier = Modifier.weight(1f))
                        GlassSwitch(s.dynamicColor, { v -> update { it.copy(dynamicColor = v) } })
                    }
                }
            }
        }

        SectionTitle("Територія і дані тривог")
        GlassSurface(Modifier.fillMaxWidth()) {
            Column {
                val loc = Locations.getOrDefault(s.locationId)
                SettingRow(Icons.Rounded.Place, "Моя територія", (loc.path.takeIf { it.isNotBlank() }?.let { "$it › " } ?: "") + loc.short, onClick = onRegion) {
                    Icon(Icons.Rounded.ChevronRight, null, tint = dimText())
                }
                HorizontalDivider(color = onBg().copy(alpha = 0.08f))
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Icon(Icons.Rounded.CloudDone, null, tint = AlertGreen); Spacer(Modifier.width(10.dp))
                        Text("Джерело даних — безкоштовно", color = onBg(), fontSize = 16.sp)
                    }
                    Text(
                        "Основне: siren.pp.ua (дані офіційного застосунку «Повітряна тривога»). Резервне: публічна стрічка alerts.in.ua. " +
                            "Токен не потрібен. Точність — до громади, з рівнями червона / жовта та типом загрози.",
                        color = dimText(), fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp)
                    )
                    Spacer(Modifier.height(10.dp))
                    GlassButton("Перевірити зв'язок", {
                        testResult = "Перевіряю…"
                        scope.launch {
                            testResult = try {
                                val m = AlertApi.fetch(ctx)
                                "✓ Працює (${m.source}). Активних тривог: ${m.threats.size}"
                            } catch (e: Exception) { "✗ ${e.message}" }
                        }
                    }, Modifier.fillMaxWidth(), primary = true)
                    testResult?.let { Text(it, color = if (it.startsWith("✓")) AlertGreen else dimText(), modifier = Modifier.padding(top = 8.dp)) }
                }
                HorizontalDivider(color = onBg().copy(alpha = 0.08f))
                Column(Modifier.padding(16.dp)) {
                    Row { Icon(Icons.Rounded.Warning, null, tint = AlertYellow); Spacer(Modifier.width(10.dp)); Text("Тривога лише в частині території", color = onBg(), fontSize = 16.sp) }
                    Text("Наприклад, обрано область, а тривога тільки в одному її районі. Як це враховувати будильнику?", color = dimText(), fontSize = 13.sp)
                    Spacer(Modifier.height(10.dp))
                    Segmented(listOf(AlertLevel.NONE, AlertLevel.YELLOW, AlertLevel.RED), s.partialAs, {
                        when (it) { AlertLevel.YELLOW -> "Як жовта"; AlertLevel.RED -> "Як червона"; else -> "Не зважати" }
                    }, { v -> update { it.copy(partialAs = v) } })
                }
            }
        }

        SectionTitle("Голос")
        GlassSurface(Modifier.fillMaxWidth()) {
            Column {
                SettingRow(Icons.Rounded.RecordVoiceOver, "Перевірити озвучку", "Так звучатиме оголошення будильника", onClick = {
                    val now = LocalTime.now()
                    val text = "Доброго ранку! Зараз ${now.hour} ${ua.tryvoga.alarm.plural(now.hour, "година", "години", "годин")} ${now.minute} ${ua.tryvoga.alarm.plural(now.minute, "хвилина", "хвилини", "хвилин")}. " +
                        "${Locations.getOrDefault(s.locationId).name}: ${AlertApi.cachedStatus(ctx).title.lowercase()}."
                    val existing = tts
                    if (existing != null) existing.speak(text, TextToSpeech.QUEUE_FLUSH, null, "test")
                    else {
                        var t: TextToSpeech? = null
                        t = TextToSpeech(ctx) { st ->
                            if (st == TextToSpeech.SUCCESS) {
                                val r = t?.setLanguage(UK)
                                if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED)
                                    Toast.makeText(ctx, "Українська мова синтезу не встановлена — встановіть її в налаштуваннях TTS", Toast.LENGTH_LONG).show()
                                t?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "test")
                            }
                        }
                        tts = t
                    }
                }) {}
                HorizontalDivider(color = onBg().copy(alpha = 0.08f))
                SettingRow(null, "Налаштування синтезу мовлення", "Встановіть голос «Українська» (Google TTS)", onClick = {
                    runCatching { ctx.startActivity(Intent("com.android.settings.TTS_SETTINGS")) }
                }) { Icon(Icons.Rounded.ChevronRight, null, tint = dimText()) }
            }
        }

        SectionTitle("Надійність спрацювання")
        @Suppress("UNUSED_EXPRESSION") resumeTick
        val exact = AlarmScheduler.canExact(ctx)
        val notif = NotificationManagerCompat.from(ctx).areNotificationsEnabled()
        val fullScreen = Build.VERSION.SDK_INT < 34 || ctx.getSystemService(NotificationManager::class.java).canUseFullScreenIntent()
        val battery = ctx.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(ctx.packageName)
        GlassSurface(Modifier.fillMaxWidth()) {
            Column {
                PermRow(Icons.Rounded.AlarmOn, "Точні будильники", exact) {
                    if (Build.VERSION.SDK_INT >= 31) ctx.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${ctx.packageName}")))
                }
                PermRow(Icons.Rounded.Notifications, "Сповіщення", notif) {
                    ctx.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName))
                }
                PermRow(Icons.Rounded.Fullscreen, "Повноекранний будильник", fullScreen) {
                    if (Build.VERSION.SDK_INT >= 34) ctx.startActivity(Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:${ctx.packageName}")))
                }
                PermRow(Icons.Rounded.BatteryChargingFull, "Без обмежень батареї", battery) {
                    @Suppress("BatteryLife")
                    ctx.startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${ctx.packageName}")))
                }
                HorizontalDivider(color = onBg().copy(alpha = 0.08f))
                SettingRow(Icons.Rounded.AlarmOn, "Тестовий будильник", "Задзвонить через 1 хвилину з поточними налаштуваннями тривоги", onClick = {
                    val t = LocalTime.now().plusMinutes(1)
                    AlarmRepo.upsert(ctx, Alarm(id = AlarmRepo.newId(), hour = t.hour, minute = t.minute, label = "Тест"))
                    Toast.makeText(ctx, "Тестовий будильник на ${"%02d:%02d".format(t.hour, t.minute)}", Toast.LENGTH_SHORT).show()
                }) {}
            }
        }

        SectionTitle("Оновлення")
        val upd by ua.tryvoga.alarm.update.Updater.state.collectAsState()
        GlassSurface(Modifier.fillMaxWidth()) {
            Column {
                SettingRow(Icons.Rounded.SystemUpdate, "Перевіряти оновлення при запуску", "Нова версія з GitHub — одним дотиком «Оновити»") {
                    GlassSwitch(s.autoUpdate, { v -> update { it.copy(autoUpdate = v) } })
                }
                HorizontalDivider(color = onBg().copy(alpha = 0.08f))
                SettingRow(
                    null, "Перевірити зараз",
                    when (val u = upd) {
                        is ua.tryvoga.alarm.update.UpdateState.Checking -> "Перевіряю…"
                        is ua.tryvoga.alarm.update.UpdateState.UpToDate -> "У вас остання версія ${ua.tryvoga.alarm.update.Updater.currentVersion}"
                        is ua.tryvoga.alarm.update.UpdateState.Error -> u.message
                        is ua.tryvoga.alarm.update.UpdateState.Available -> "Доступна версія ${u.info.version}"
                        else -> "Поточна версія ${ua.tryvoga.alarm.update.Updater.currentVersion}"
                    },
                    onClick = { scope.launch { ua.tryvoga.alarm.update.Updater.check() } }
                ) { Icon(Icons.Rounded.ChevronRight, null, tint = dimText()) }
            }
        }

        SectionTitle("Про застосунок")
        GlassSurface(Modifier.fillMaxWidth()) {
            SettingRow(Icons.Rounded.Info, "Тривожний Будильник ${ua.tryvoga.alarm.update.Updater.currentVersion}", "Дані: siren.pp.ua («Повітряна тривога»), alerts.in.ua. Межі областей: geoBoundaries (CC BY 4.0). Завжди дотримуйтеся офіційних сигналів оповіщення.") {}
        }
        Spacer(Modifier.height(120.dp))
    }
}

@Composable
private fun PermRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, ok: Boolean, onFix: () -> Unit) {
    SettingRow(icon, title, if (ok) "Дозволено" else "Натисніть, щоб дозволити", onClick = if (ok) null else ({ runCatching(onFix) }), iconTint = if (ok) AlertGreen else AlertRed) {
        if (ok) Icon(Icons.Rounded.CheckCircle, null, tint = AlertGreen) else Icon(Icons.Rounded.ChevronRight, null, tint = dimText())
    }
}
