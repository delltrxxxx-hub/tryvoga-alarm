package ua.tryvoga.alarm.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.view.View
import android.widget.RemoteViews
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import ua.tryvoga.alarm.AlarmRepo
import ua.tryvoga.alarm.AlarmScheduler
import ua.tryvoga.alarm.AlertApi
import ua.tryvoga.alarm.AlertLevel
import ua.tryvoga.alarm.R
import ua.tryvoga.alarm.Locations
import ua.tryvoga.alarm.UK
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import ua.tryvoga.alarm.SettingsRepo
import ua.tryvoga.alarm.ui.MainActivity

private val widgetScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

abstract class BaseClockWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, mgr: AppWidgetManager, ids: IntArray) {
        val ctx = context.applicationContext
        WidgetUpdater.update(ctx)
        val pending = goAsync()
        widgetScope.launch {
            try {
                runCatching { AlertApi.fetch(ctx) }
                WidgetUpdater.update(ctx)
            } finally { pending.finish() }
        }
    }
}

class DigitalClockWidget : BaseClockWidget()
class AnalogClockWidget : BaseClockWidget()
class AlarmsListWidget : BaseClockWidget()
class CountdownWidget : BaseClockWidget()

object WidgetUpdater {
    fun update(ctx: Context) {
        runCatching {
            val mgr = AppWidgetManager.getInstance(ctx) ?: return
            for ((cls, layout) in listOf(
                DigitalClockWidget::class.java to R.layout.widget_digital,
                AnalogClockWidget::class.java to R.layout.widget_analog,
                AlarmsListWidget::class.java to R.layout.widget_alarms,
                CountdownWidget::class.java to R.layout.widget_countdown,
            )) {
                val ids = mgr.getAppWidgetIds(ComponentName(ctx, cls))
                if (ids.isEmpty()) continue
                mgr.updateAppWidget(ids, build(ctx, layout))
            }
            scheduleMidnight(ctx)
        }
    }

    /** Оновити дату на віджеті одразу після півночі. */
    private fun scheduleMidnight(ctx: Context) {
        val at = LocalDate.now().plusDays(1).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli() + 5_000
        val i = Intent(ctx, DigitalClockWidget::class.java).setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, IntArray(0))
        val pi = PendingIntent.getBroadcast(ctx, 77, i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        ctx.getSystemService(android.app.AlarmManager::class.java).set(android.app.AlarmManager.RTC, at, pi)
    }

    private val ROWS = listOf(
        intArrayOf(R.id.a1, R.id.a1_time, R.id.a1_days, R.id.a1_dot),
        intArrayOf(R.id.a2, R.id.a2_time, R.id.a2_days, R.id.a2_dot),
        intArrayOf(R.id.a3, R.id.a3_time, R.id.a3_days, R.id.a3_dot),
        intArrayOf(R.id.a4, R.id.a4_time, R.id.a4_days, R.id.a4_dot),
    )

    /** Список «Мої будильники»: увімкнені — першими, у порядку найближчого спрацювання. */
    private fun fillAlarmRows(rv: RemoteViews) {
        val now = System.currentTimeMillis()
        val list = AlarmRepo.alarms.value.sortedWith(
            compareBy({ !it.enabled }, { if (it.enabled) it.nextTriggerMillis(now) else (it.hour * 60 + it.minute).toLong() })
        )
        rv.setViewVisibility(R.id.empty, if (list.isEmpty()) View.VISIBLE else View.GONE)
        ROWS.forEachIndexed { i, ids ->
            val a = list.getOrNull(i)
            if (a == null) { rv.setViewVisibility(ids[0], View.GONE); return@forEachIndexed }
            rv.setViewVisibility(ids[0], View.VISIBLE)
            rv.setTextViewText(ids[1], a.timeText)
            rv.setTextColor(ids[1], if (a.enabled) 0xFFFFFFFF.toInt() else 0x66FFFFFF)
            val parts = listOfNotNull(a.label.takeIf { it.isNotBlank() }, a.daysText(), if (a.alertAware) "з режимом тривоги" else null)
            rv.setTextViewText(ids[2], if (a.enabled) parts.joinToString(" · ") else "Вимкнено · " + parts.joinToString(" · "))
            rv.setInt(ids[3], "setColorFilter", if (a.enabled) 0xFF30D158.toInt() else 0x55FFFFFF)
        }
    }

    private fun build(ctx: Context, layout: Int): RemoteViews {
        AlarmRepo.init(ctx); SettingsRepo.init(ctx)
        val rv = RemoteViews(ctx.packageName, layout)
        val now = System.currentTimeMillis()
        val next = AlarmScheduler.nextAlarm(AlarmRepo.alarms.value, now)
        if (next != null) {
            val diff = next.first - now
            rv.setViewVisibility(R.id.countdown, View.VISIBLE)
            rv.setChronometer(R.id.countdown, SystemClock.elapsedRealtime() + diff, null, true)
            rv.setChronometerCountDown(R.id.countdown, true)
            val a = next.second
            rv.setTextViewText(
                R.id.next_label,
                when (layout) {
                    R.layout.widget_alarms -> "Наступний о ${java.time.Instant.ofEpochMilli(next.first).atZone(java.time.ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm, EEEE", UK))}" +
                        (a.label.takeIf { it.isNotBlank() }?.let { " · $it" } ?: "")
                    R.layout.widget_countdown -> "о ${java.time.Instant.ofEpochMilli(next.first).atZone(java.time.ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm"))}"
                    else -> "До пробудження (${a.timeText})"
                }
            )
        } else {
            rv.setViewVisibility(R.id.countdown, View.GONE)
            rv.setTextViewText(R.id.next_label, "Будильників немає")
        }
        if (layout == R.layout.widget_alarms) fillAlarmRows(rv)
        val loc = Locations.getOrDefault(SettingsRepo.settings.value.locationId)
        val st = AlertApi.cachedStatus(ctx)
        val lvl = st.shownLevel
        val color = when (lvl) {
            AlertLevel.RED -> 0xFFFF453A.toInt()
            AlertLevel.YELLOW -> 0xFFFFC50A.toInt()
            AlertLevel.NONE -> 0xFF30D158.toInt()
            AlertLevel.UNKNOWN -> 0xFF8E8E93.toInt()
        }
        rv.setInt(R.id.alert_dot, "setColorFilter", color)
        rv.setTextViewText(R.id.alert_status, "${loc.short}: ${st.title.lowercase()}")
        val today = LocalDate.now()
        when (layout) {
            R.layout.widget_analog -> {
                rv.setTextViewText(R.id.day, today.format(DateTimeFormatter.ofPattern("EEEE", UK)).replaceFirstChar { it.uppercase() })
                rv.setTextViewText(R.id.date, today.format(DateTimeFormatter.ofPattern("d MMMM", UK)))
            }
            R.layout.widget_digital ->
                rv.setTextViewText(R.id.date, today.format(DateTimeFormatter.ofPattern("EEEE, d MMMM", UK)).replaceFirstChar { it.uppercase() })
        }
        val pi = PendingIntent.getActivity(ctx, 0, Intent(ctx, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        rv.setOnClickPendingIntent(R.id.root, pi)
        return rv
    }
}
