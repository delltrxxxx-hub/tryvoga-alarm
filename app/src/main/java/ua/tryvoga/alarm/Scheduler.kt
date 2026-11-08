package ua.tryvoga.alarm

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import ua.tryvoga.alarm.ui.MainActivity
import ua.tryvoga.alarm.widget.WidgetUpdater

object AlarmScheduler {
    const val ACTION_FIRE = "ua.tryvoga.alarm.FIRE"
    const val ACTION_SNOOZE_FIRE = "ua.tryvoga.alarm.SNOOZE_FIRE"
    const val ACTION_WATCH = "ua.tryvoga.alarm.WATCH"
    const val ACTION_AFTER_CLEAR = "ua.tryvoga.alarm.AFTER_CLEAR"
    const val ACTION_TIMER = "ua.tryvoga.alarm.TIMER"
    const val EXTRA_ID = "id"

    private const val REQ_SNOOZE = 100_000
    private const val REQ_WATCH = 200_000
    private const val REQ_CLEAR = 300_000
    private const val REQ_TIMER = 900_001

    private fun am(ctx: Context) = ctx.getSystemService(AlarmManager::class.java)

    private fun op(ctx: Context, action: String, id: Int, req: Int): PendingIntent =
        PendingIntent.getBroadcast(
            ctx, req,
            Intent(ctx, AlarmReceiver::class.java).setAction(action).putExtra(EXTRA_ID, id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    fun canExact(ctx: Context): Boolean = Build.VERSION.SDK_INT < 31 || am(ctx).canScheduleExactAlarms()

    /** setAlarmClock — найнадійніший спосіб: працює в Doze і не відкладається системою. */
    private fun setClock(ctx: Context, at: Long, op: PendingIntent) {
        val am = am(ctx)
        val show = PendingIntent.getActivity(
            ctx, 1, Intent(ctx, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        if (!canExact(ctx)) am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, op)
        else am.setAlarmClock(AlarmManager.AlarmClockInfo(at, show), op)
    }

    fun rescheduleAll(ctx: Context) {
        AlarmRepo.init(ctx)
        val now = System.currentTimeMillis()
        for (a in AlarmRepo.alarms.value) {
            val p = op(ctx, ACTION_FIRE, a.id, a.id)
            if (a.enabled) setClock(ctx, a.nextTriggerMillis(now), p) else am(ctx).cancel(p)
            val s = AlarmRepo.snoozeUntil(a.id)
            if (s > now) setClock(ctx, s, op(ctx, ACTION_SNOOZE_FIRE, a.id, a.id + REQ_SNOOZE))
            else if (s > 0) AlarmRepo.clearSnooze(a.id)
        }
        WidgetUpdater.update(ctx)
    }

    fun cancelAll(ctx: Context, id: Int) {
        val am = am(ctx)
        am.cancel(op(ctx, ACTION_FIRE, id, id))
        am.cancel(op(ctx, ACTION_SNOOZE_FIRE, id, id + REQ_SNOOZE))
        am.cancel(op(ctx, ACTION_WATCH, id, id + REQ_WATCH))
        am.cancel(op(ctx, ACTION_AFTER_CLEAR, id, id + REQ_CLEAR))
    }

    fun snooze(ctx: Context, id: Int, minutes: Int): Long {
        val t = System.currentTimeMillis() + minutes * 60_000L
        AlarmRepo.setSnooze(id, t)
        setClock(ctx, t, op(ctx, ACTION_SNOOZE_FIRE, id, id + REQ_SNOOZE))
        WidgetUpdater.update(ctx)
        return t
    }

    fun scheduleWatch(ctx: Context, id: Int, at: Long) = setClock(ctx, at, op(ctx, ACTION_WATCH, id, id + REQ_WATCH))
    fun cancelWatch(ctx: Context, id: Int) {
        am(ctx).cancel(op(ctx, ACTION_WATCH, id, id + REQ_WATCH))
        am(ctx).cancel(op(ctx, ACTION_AFTER_CLEAR, id, id + REQ_CLEAR))
    }
    fun scheduleAfterClear(ctx: Context, id: Int, at: Long) = setClock(ctx, at, op(ctx, ACTION_AFTER_CLEAR, id, id + REQ_CLEAR))

    fun scheduleTimer(ctx: Context, at: Long) = setClock(ctx, at, op(ctx, ACTION_TIMER, 0, REQ_TIMER))
    fun cancelTimer(ctx: Context) = am(ctx).cancel(op(ctx, ACTION_TIMER, 0, REQ_TIMER))

    /** Найближчий будильник: час спрацювання та сам будильник. */
    fun nextAlarm(list: List<Alarm>, now: Long = System.currentTimeMillis()): Pair<Long, Alarm>? {
        var best: Pair<Long, Alarm>? = null
        for (a in list) {
            if (a.enabled) {
                val t = a.nextTriggerMillis(now)
                if (best == null || t < best.first) best = t to a
            }
            val s = AlarmRepo.snoozeUntil(a.id)
            if (s > now && (best == null || s < best.first)) best = s to a
        }
        return best
    }
}

/** «Розбудити після відбою»: щохвилини перевіряємо статус, поки тривога не скінчиться. */
object WatchLogic {
    /** Як часто перевіряти тривогу, поки будильник чекає на відбій. */
    const val WATCH_INTERVAL = 15_000L

    fun start(ctx: Context, alarm: Alarm, keepDeadline: Boolean = false) {
        val now = System.currentTimeMillis()
        val existing = AlarmRepo.watchDeadline(alarm.id)
        val deadline = if (keepDeadline && existing > now) existing else now + alarm.maxWaitMin * 60_000L
        AlarmRepo.setWatch(alarm.id, deadline)
        AlarmScheduler.scheduleWatch(ctx, alarm.id, now + WATCH_INTERVAL)
    }

    fun cancel(ctx: Context, id: Int) {
        AlarmRepo.clearWatch(id)
        AlarmScheduler.cancelWatch(ctx, id)
        Notifier.cancel(ctx, id)
    }

    suspend fun check(ctx: Context, id: Int) {
        val deadline = AlarmRepo.watchDeadline(id)
        val alarm = AlarmRepo.find(id)
        if (deadline == 0L || alarm == null) return
        Locations.init(ctx)
        val region = Locations.getOrDefault(SettingsRepo.settings.value.locationId)
        val lvl = AlertApi.statusForAlarm(ctx).level
        val now = System.currentTimeMillis()
        ua.tryvoga.alarm.widget.WidgetUpdater.update(ctx)
        when {
            lvl == AlertLevel.NONE -> {
                AlarmRepo.clearWatch(id)
                if (alarm.allClearDelayMin <= 0) {
                    AlarmService.start(ctx, id, AlarmService.MODE_AFTER_CLEAR)
                } else {
                    val at = now + alarm.allClearDelayMin * 60_000L
                    AlarmRepo.setWatch(id, deadline) // зберігаємо, щоб не загубити, якщо тривога відновиться
                    AlarmRepo.setClearAt(id, at)
                    AlarmScheduler.scheduleAfterClear(ctx, id, at)
                    Notifier.info(
                        ctx, id, "Відбій тривоги · ${region.short}",
                        "Будильник ${alarm.timeText} задзвонить через ${alarm.allClearDelayMin} хв"
                    )
                }
            }
            now > deadline -> {
                AlarmRepo.clearWatch(id)
                Notifier.info(ctx, id, "Тривога триває надто довго", "Будильник ${alarm.timeText} не задзвонив: ${lvl.title.lowercase()} · ${region.short}")
            }
            else -> {
                AlarmScheduler.scheduleWatch(ctx, id, now + WATCH_INTERVAL)
                Notifier.info(
                    ctx, id, "Будильник ${alarm.timeText} чекає на відбій",
                    "${lvl.title} · ${region.short}. Розбуджу після відбою.", silent = true
                )
            }
        }
    }
}

object Notifier {
    fun info(ctx: Context, id: Int, title: String, text: String, silent: Boolean = false) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        val pi = PendingIntent.getActivity(ctx, 2, Intent(ctx, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val n = NotificationCompat.Builder(ctx, App.CH_INFO)
            .setSmallIcon(R.drawable.ic_stat_alarm)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(pi)
            .setOnlyAlertOnce(silent)
            .setSilent(silent)
            .setAutoCancel(true)
            .build()
        runCatching { nm.notify(5000 + id, n) }
    }

    fun cancel(ctx: Context, id: Int) {
        ctx.getSystemService(NotificationManager::class.java).cancel(5000 + id)
    }
}
