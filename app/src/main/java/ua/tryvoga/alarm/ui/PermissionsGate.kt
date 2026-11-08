package ua.tryvoga.alarm.ui

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AlarmOn
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import ua.tryvoga.alarm.AlarmScheduler

/** Дозволи, без яких будильник може не спрацювати або спрацювати тихо. */
enum class ReliabilityPerm(val title: String, val why: String, val icon: ImageVector) {
    NOTIFICATIONS("Сповіщення", "Щоб показати будильник і кнопки «Вимкнути / Відкласти»", Icons.Rounded.Notifications),
    EXACT("Точні будильники", "Щоб будильник дзвонив хвилина в хвилину, а не із запізненням", Icons.Rounded.AlarmOn),
    FULL_SCREEN("Повноекранний будильник", "Щоб будильник з'являвся поверх заблокованого екрана", Icons.Rounded.Fullscreen),
    BATTERY("Без обмежень батареї", "Щоб система не «приспала» застосунок і перевірку тривоги вночі", Icons.Rounded.BatteryChargingFull),
}

object Reliability {
    fun granted(ctx: Context, p: ReliabilityPerm): Boolean = when (p) {
        ReliabilityPerm.NOTIFICATIONS -> NotificationManagerCompat.from(ctx).areNotificationsEnabled()
        ReliabilityPerm.EXACT -> AlarmScheduler.canExact(ctx)
        ReliabilityPerm.FULL_SCREEN -> Build.VERSION.SDK_INT < 34 || ctx.getSystemService(NotificationManager::class.java).canUseFullScreenIntent()
        ReliabilityPerm.BATTERY -> ctx.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(ctx.packageName)
    }

    fun missing(ctx: Context): List<ReliabilityPerm> = ReliabilityPerm.entries.filter { !granted(ctx, it) }

    /** Відкрити системний екран для конкретного дозволу. */
    fun openSettings(ctx: Context, p: ReliabilityPerm) {
        val pkg = Uri.parse("package:${ctx.packageName}")
        val intent = when (p) {
            ReliabilityPerm.NOTIFICATIONS -> Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName)
            ReliabilityPerm.EXACT -> if (Build.VERSION.SDK_INT >= 31) Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, pkg) else null
            ReliabilityPerm.FULL_SCREEN -> if (Build.VERSION.SDK_INT >= 34) Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, pkg) else null
            @Suppress("BatteryLife")
            ReliabilityPerm.BATTERY -> Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, pkg)
        } ?: return
        runCatching { ctx.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            .onFailure { runCatching { ctx.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkg).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }
    }
}

/** Лічильник, що збільшується щоразу, коли користувач повертається в застосунок (після системних налаштувань). */
@Composable
fun rememberResumeTick(): Int {
    var tick by remember { mutableIntStateOf(0) }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) tick++ }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }
    return tick
}

/**
 * Обов'язкове вікно при запуску: показується, доки не надано всі дозволи надійності.
 * Повертає true, якщо вікно зараз на екрані.
 */
@Composable
fun PermissionsGate(): Boolean {
    val ctx = LocalContext.current
    val tick = rememberResumeTick()
    var skippedThisLaunch by rememberSaveable { mutableStateOf(false) }
    val missing = remember(tick) { Reliability.missing(ctx) }
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        // якщо відмовили в системному діалозі — ведемо в налаштування сповіщень
        if (!it) Reliability.openSettings(ctx, ReliabilityPerm.NOTIFICATIONS)
    }
    if (missing.isEmpty() || skippedThisLaunch) return false

    Dialog(onDismissRequest = {}, properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)) {
        GlassSurface(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(sheetColor())) {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(56.dp).clip(CircleShape).background(AlertRed.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Shield, null, tint = AlertRed, modifier = Modifier.size(30.dp))
                }
                Spacer(Modifier.height(10.dp))
                Text("Щоб будильник точно спрацював", color = onBg(), fontSize = 20.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
                Text(
                    "Дайте ці дозволи один раз. Без них Android може затримати або заглушити будильник.",
                    color = dimText(), fontSize = 14.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 6.dp)
                )
                Spacer(Modifier.height(14.dp))
                ReliabilityPerm.entries.forEach { p ->
                    val ok = p !in missing
                    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(38.dp).clip(CircleShape).background((if (ok) AlertGreen else AlertRed).copy(alpha = 0.16f)),
                            contentAlignment = Alignment.Center
                        ) { Icon(p.icon, null, tint = if (ok) AlertGreen else AlertRed, modifier = Modifier.size(20.dp)) }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(p.title, color = onBg(), fontSize = 15.sp, fontWeight = FontWeight.Medium)
                            Text(p.why, color = dimText(), fontSize = 12.sp)
                            if (!ok) {
                                Spacer(Modifier.height(6.dp))
                                GlassButton("Дозволити", {
                                    if (p == ReliabilityPerm.NOTIFICATIONS && Build.VERSION.SDK_INT >= 33 &&
                                        ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                                    ) notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                    else Reliability.openSettings(ctx, p)
                                }, Modifier.fillMaxWidth(), primary = true)
                            }
                        }
                        if (ok) {
                            Spacer(Modifier.width(8.dp))
                            Icon(Icons.Rounded.CheckCircle, "Дозволено", tint = AlertGreen, modifier = Modifier.size(28.dp))
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = { skippedThisLaunch = true }) {
                    Text("Пропустити цього разу (не рекомендовано)", color = dimText(), fontSize = 12.sp)
                }
            }
        }
    }
    return true
}
