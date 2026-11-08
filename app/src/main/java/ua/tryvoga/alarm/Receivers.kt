package ua.tryvoga.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import ua.tryvoga.alarm.AlarmScheduler.EXTRA_ID
import ua.tryvoga.alarm.widget.WidgetUpdater

private val receiverScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

private fun initAll(ctx: Context) {
    SettingsRepo.init(ctx); AlarmRepo.init(ctx); TimerRepo.init(ctx)
}

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val ctx = context.applicationContext
        initAll(ctx)
        val id = intent.getIntExtra(EXTRA_ID, -1)
        when (intent.action) {
            AlarmScheduler.ACTION_FIRE -> {
                val a = AlarmRepo.find(id) ?: return
                AlarmService.start(ctx, id, AlarmService.MODE_NORMAL)
                if (a.days.isEmpty()) AlarmRepo.upsert(ctx, a.copy(enabled = false))
                else AlarmScheduler.rescheduleAll(ctx)
            }
            AlarmScheduler.ACTION_SNOOZE_FIRE -> {
                AlarmRepo.clearSnooze(id)
                AlarmService.start(ctx, id, AlarmService.MODE_NORMAL)
                WidgetUpdater.update(ctx)
            }
            AlarmScheduler.ACTION_WATCH -> {
                val pending = goAsync()
                receiverScope.launch {
                    try { WatchLogic.check(ctx, id) } finally { pending.finish() }
                }
            }
            AlarmScheduler.ACTION_AFTER_CLEAR -> {
                AlarmRepo.setClearAt(id, 0)
                AlarmService.start(ctx, id, AlarmService.MODE_AFTER_CLEAR)
            }
            AlarmScheduler.ACTION_TIMER -> AlarmService.start(ctx, 0, AlarmService.MODE_TIMER)
        }
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val ctx = context.applicationContext
        initAll(ctx)
        AlarmScheduler.rescheduleAll(ctx)
        TimerRepo.reschedule(ctx)
        val now = System.currentTimeMillis()
        for (a in AlarmRepo.alarms.value) {
            val clearAt = AlarmRepo.clearAt(a.id)
            when {
                clearAt > now -> AlarmScheduler.scheduleAfterClear(ctx, a.id, clearAt)
                AlarmRepo.watchDeadline(a.id) > now -> AlarmScheduler.scheduleWatch(ctx, a.id, now + 30_000)
            }
        }
        WidgetUpdater.update(ctx)
    }
}
