package ua.tryvoga.alarm

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.media.ToneGenerator
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import ua.tryvoga.alarm.ui.AlarmActivity
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlin.coroutines.resume
import kotlin.math.max

class AlarmService : Service() {

    data class RingInfo(
        val alarmId: Int,
        val mode: Int,
        val title: String,
        val subtitle: String,
        val level: AlertLevel,
        val region: String,
        val volume: Int,
        val canSnooze: Boolean,
        val quieted: Boolean,
    )

    companion object {
        const val MODE_NORMAL = 0
        const val MODE_AFTER_CLEAR = 1
        const val MODE_TIMER = 2
        const val ACTION_START = "ua.tryvoga.alarm.START"
        const val ACTION_DISMISS = "ua.tryvoga.alarm.DISMISS"
        const val ACTION_SNOOZE = "ua.tryvoga.alarm.SNOOZE"
        private const val EXTRA_MODE = "mode"
        private const val NOTIF_CHECK = 1
        private const val NOTIF_RING = 2
        private const val RING_TIMEOUT = 10 * 60_000L

        private val _ringing = MutableStateFlow<RingInfo?>(null)
        val ringing: StateFlow<RingInfo?> = _ringing.asStateFlow()

        fun start(ctx: Context, id: Int, mode: Int) {
            val i = Intent(ctx, AlarmService::class.java).setAction(ACTION_START)
                .putExtra(AlarmScheduler.EXTRA_ID, id).putExtra(EXTRA_MODE, mode)
            ContextCompat.startForegroundService(ctx, i)
        }

        fun command(ctx: Context, action: String) {
            runCatching { ctx.startService(Intent(ctx, AlarmService::class.java).setAction(action)) }
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var job: Job? = null
    private var timeoutJob: Job? = null
    private var player: MediaPlayer? = null
    private var tts: TextToSpeech? = null
    @Volatile private var ttsReady = false
    private var vibrator: Vibrator? = null
    private var savedVolume = -1
    private var focusRequest: AudioFocusRequest? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var current: RingInfo? = null
    private var currentAlarm: Alarm? = null

    private val alarmAttrs: AudioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ALARM)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        SettingsRepo.init(this); AlarmRepo.init(this); TimerRepo.init(this)
        wakeLock = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "tryvoga:ring").apply { acquire(RING_TIMEOUT + 60_000) }
        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.let { t ->
                    val r = t.setLanguage(UK)
                    if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED) {
                        t.setLanguage(java.util.Locale.forLanguageTag("uk"))
                    }
                    t.setAudioAttributes(alarmAttrs)
                }
                ttsReady = true
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                goForeground(NOTIF_CHECK, checkingNotification())
                stopSound()
                job?.cancel(); timeoutJob?.cancel()
                val id = intent.getIntExtra(AlarmScheduler.EXTRA_ID, -1)
                val mode = intent.getIntExtra(EXTRA_MODE, MODE_NORMAL)
                job = scope.launch { handleStart(id, mode) }
            }
            ACTION_DISMISS -> dismiss()
            ACTION_SNOOZE -> snooze()
            else -> if (current == null) finish()
        }
        return START_NOT_STICKY
    }

    private suspend fun handleStart(id: Int, mode: Int) {
        Locations.init(this)
        val settings = SettingsRepo.settings.value
        val region = Locations.getOrDefault(settings.locationId)
        if (mode == MODE_TIMER) {
            ring(
                RingInfo(0, mode, "Таймер", "Час вийшов", AlertLevel.NONE, region.short, 100, false, false),
                100, true, null, false, null
            )
            return
        }
        val alarm = AlarmRepo.find(id) ?: run { finish(); return }
        currentAlarm = alarm

        val status = if (alarm.alertAware || alarm.announce || mode == MODE_AFTER_CLEAR)
            AlertApi.statusForAlarm(this) else LocStatus.UNKNOWN.copy(level = AlertLevel.NONE)
        val level = status.level

        if (mode == MODE_AFTER_CLEAR && alarm.alertAware && level.isAlert) {
            // Тривога відновилася до кінця затримки — чекаємо далі.
            WatchLogic.start(this, alarm, keepDeadline = true)
            Notifier.info(this, alarm.id, "Тривога відновилася", "Будильник ${alarm.timeText} знову чекає на відбій · ${region.short}", true)
            finish(); return
        }
        if (mode == MODE_AFTER_CLEAR) AlarmRepo.clearWatch(alarm.id)

        val quieted = alarm.alertAware && level.isAlert && mode != MODE_AFTER_CLEAR
        val vol: Int
        val vib: Boolean
        if (quieted) {
            if (level == AlertLevel.RED) { vol = alarm.redVolume; vib = alarm.redVibrate }
            else { vol = alarm.yellowVolume; vib = alarm.yellowVibrate }
        } else { vol = alarm.volume; vib = alarm.vibrate }

        if (quieted && vol == 0 && !vib) {
            Notifier.info(
                this, alarm.id, "Будильник ${alarm.timeText} приглушено",
                "${level.title} · ${region.short}." + if (alarm.wakeAfterAllClear) " Розбуджу після відбою." else ""
            )
            if (alarm.wakeAfterAllClear) WatchLogic.start(this, alarm)
            finish(); return
        }

        val subtitle = when {
            mode == MODE_AFTER_CLEAR -> "Відбій тривоги · ${region.short}"
            level.isAlert -> "${status.title} · ${region.short}"
            level == AlertLevel.UNKNOWN && alarm.alertAware -> "Статус тривоги невідомий"
            else -> alarm.label.ifBlank { "Час прокидатися" }
        }
        val title = alarm.label.ifBlank { "Будильник" }
        val speech = if (alarm.announce) buildSpeech(alarm, status, region, mode) else null
        ring(
            RingInfo(alarm.id, mode, title, subtitle, level, region.short, vol, true, quieted),
            vol, vib, alarm.ringtoneUri, alarm.gradual, speech
        )
        if (alarm.alertAware && mode == MODE_NORMAL) {
            scope.launch { monitorWhileRinging(alarm, level, region) }
        }
    }

    private fun volFor(alarm: Alarm, lvl: AlertLevel): Pair<Int, Boolean> = when (lvl) {
        AlertLevel.RED -> alarm.redVolume to alarm.redVibrate
        AlertLevel.YELLOW -> alarm.yellowVolume to alarm.yellowVibrate
        else -> alarm.volume to alarm.vibrate
    }

    /** Поки будильник дзвонить — кожні 15 с перевіряємо тривогу і одразу змінюємо гучність. */
    private suspend fun monitorWhileRinging(alarm: Alarm, startLevel: AlertLevel, region: Loc) {
        var cur = startLevel
        while (current != null) {
            delay(15_000)
            if (current == null) return
            val st = try {
                AlertApi.fetch(this).statusOf(region, SettingsRepo.settings.value.partialAs)
            } catch (_: Exception) { continue }
            val lvl = st.level
            if (lvl == cur || lvl == AlertLevel.UNKNOWN) continue
            cur = lvl
            val (vol, vib) = volFor(alarm, lvl)
            if (lvl.isAlert && vol == 0 && !vib) {
                Notifier.info(
                    this, alarm.id, "Будильник ${alarm.timeText} приглушено",
                    "Почалась ${lvl.title.lowercase()} · ${region.short}." + if (alarm.wakeAfterAllClear) " Розбуджу після відбою." else ""
                )
                if (alarm.wakeAfterAllClear) WatchLogic.start(this, alarm)
                finish(); return
            }
            vibrator?.cancel(); vibrator = null
            if (vib) startVibration()
            if (vol > 0) {
                setAlarmVolume(vol)
                if (player == null) startPlayer(alarm.ringtoneUri, false)
            } else {
                player?.let { runCatching { it.stop() }; it.release() }; player = null
            }
            val info = current?.copy(
                level = lvl, volume = vol, quieted = lvl.isAlert,
                subtitle = if (lvl.isAlert) "${st.title} · ${region.short}" else "Відбій тривоги · ${region.short}"
            ) ?: return
            current = info
            _ringing.value = info
            if (alarm.announce && vol > 0) {
                player?.setVolume(0.15f, 0.15f)
                speak(if (lvl.isAlert) "Увага! ${lvl.title}. ${region.name}." else "Відбій тривоги. ${region.name}.")
                player?.setVolume(1f, 1f)
            }
        }
    }

    private fun buildSpeech(alarm: Alarm, status: LocStatus, region: Loc, mode: Int): String {
        val level = status.level
        val kinds = status.types.filter { it != "AIR" }.joinToString(", ") { threatName(it) }
            .let { if (it.isNotBlank()) " Також: $it." else "" }
        val partial = if (!status.direct.isAlert && status.partial.isNotEmpty())
            " Тривога в частині території: ${status.partial.take(3).joinToString(", ") { it.first.short }}." else ""
        val now = LocalTime.now()
        val greet = when (now.hour) {
            in 4..11 -> "Доброго ранку!"
            in 12..17 -> "Добрий день!"
            else -> "Добрий вечір!"
        }
        val time = "Зараз ${now.hour} ${plural(now.hour, "година", "години", "годин")} " +
            "${now.minute} ${plural(now.minute, "хвилина", "хвилини", "хвилин")}."
        val alert = when {
            mode == MODE_AFTER_CLEAR -> "Відбій повітряної тривоги. ${region.name}. Можна прокидатися."
            level == AlertLevel.RED -> "Увага! Червона тривога. ${region.name}. Негайно пройдіть до укриття."
            level == AlertLevel.YELLOW -> "Увага! Жовта тривога. ${region.name}. Будьте уважні та обережні."
            level == AlertLevel.NONE -> "${region.name}: тривоги немає.$partial"
            else -> "Не вдалося отримати дані про тривогу."
        }
        val label = if (alarm.label.isNotBlank()) " ${alarm.label}." else ""
        return "$greet $time$label $alert" + if (level.isAlert) kinds else ""
    }

    private fun ring(info: RingInfo, vol: Int, vib: Boolean, uri: String?, gradual: Boolean, speech: String?) {
        current = info
        _ringing.value = info
        goForeground(NOTIF_RING, ringNotification(info))
        getSystemService(NotificationManager::class.java).cancel(NOTIF_CHECK)

        if (vib) startVibration()
        if (vol > 0) {
            setAlarmVolume(vol)
            requestFocus()
            scope.launch {
                if (speech != null) {
                    beeps()
                    speak(speech)
                }
                startPlayer(uri, gradual)
            }
        }
        timeoutJob = scope.launch {
            delay(RING_TIMEOUT)
            val a = currentAlarm
            if (a != null && current?.quieted == true && a.wakeAfterAllClear) {
                WatchLogic.start(this@AlarmService, a)
            } else if (a != null) {
                Notifier.info(this@AlarmService, a.id, "Пропущений будильник", "${a.timeText} · ${info.subtitle}")
            }
            finish()
        }
    }

    private suspend fun beeps() {
        val tg = runCatching { ToneGenerator(AudioManager.STREAM_ALARM, 90) }.getOrNull() ?: return
        repeat(3) {
            tg.startTone(ToneGenerator.TONE_PROP_BEEP2, 220)
            delay(420)
        }
        delay(250)
        tg.release()
    }

    private suspend fun speak(text: String) {
        withTimeoutOrNull(4000) { while (!ttsReady) delay(100) } ?: return
        val t = tts ?: return
        withTimeoutOrNull(30_000) {
            suspendCancellableCoroutine { cont ->
                t.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {}
                    override fun onDone(utteranceId: String?) { if (cont.isActive) cont.resume(Unit) }
                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) { if (cont.isActive) cont.resume(Unit) }
                })
                val r = t.speak(text, TextToSpeech.QUEUE_FLUSH, null, "alarm")
                if (r != TextToSpeech.SUCCESS && cont.isActive) cont.resume(Unit)
                cont.invokeOnCancellation { t.stop() }
            }
        }
        delay(400)
    }

    private fun startPlayer(uriStr: String?, gradual: Boolean) {
        val candidates = listOfNotNull(
            uriStr?.let { Uri.parse(it) },
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE),
        )
        for (u in candidates) {
            val p = MediaPlayer()
            try {
                p.setAudioAttributes(alarmAttrs)
                p.setDataSource(this, u)
                p.isLooping = true
                p.prepare()
                p.setVolume(if (gradual) 0.12f else 1f, if (gradual) 0.12f else 1f)
                p.start()
                player = p
                if (gradual) scope.launch {
                    var v = 0.12f
                    while (v < 1f && player === p) {
                        delay(1000); v = (v + 0.03f).coerceAtMost(1f)
                        runCatching { p.setVolume(v, v) }
                    }
                }
                return
            } catch (_: Exception) {
                p.release()
            }
        }
        // Запасний варіант — системний тон.
        scope.launch {
            val tg = runCatching { ToneGenerator(AudioManager.STREAM_ALARM, 100) }.getOrNull() ?: return@launch
            while (current != null) { tg.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 900); delay(1400) }
            tg.release()
        }
    }

    private fun setAlarmVolume(pct: Int) {
        val am = getSystemService(AudioManager::class.java)
        if (savedVolume < 0) savedVolume = am.getStreamVolume(AudioManager.STREAM_ALARM)
        val maxV = am.getStreamMaxVolume(AudioManager.STREAM_ALARM)
        val target = max(1, (maxV * pct + 50) / 100)
        runCatching { am.setStreamVolume(AudioManager.STREAM_ALARM, target, 0) }
    }

    private fun requestFocus() {
        val am = getSystemService(AudioManager::class.java)
        val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(alarmAttrs).build()
        focusRequest = req
        runCatching { am.requestAudioFocus(req) }
    }

    @Suppress("DEPRECATION")
    private fun startVibration() {
        val v = if (Build.VERSION.SDK_INT >= 31) getSystemService(VibratorManager::class.java).defaultVibrator
        else getSystemService(Vibrator::class.java)
        vibrator = v
        runCatching { v.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 700, 500, 700, 1200), 0), alarmAttrs) }
    }

    private fun stopSound() {
        player?.let { runCatching { it.stop() }; it.release() }
        player = null
        vibrator?.cancel(); vibrator = null
        tts?.stop()
        val am = getSystemService(AudioManager::class.java)
        if (savedVolume >= 0) { runCatching { am.setStreamVolume(AudioManager.STREAM_ALARM, savedVolume, 0) }; savedVolume = -1 }
        focusRequest?.let { runCatching { am.abandonAudioFocusRequest(it) } }; focusRequest = null
    }

    private fun dismiss() {
        val info = current
        if (info?.mode == MODE_TIMER) TimerRepo.reset(this)
        info?.let { if (it.alarmId > 0) WatchLogic.cancel(this, it.alarmId) }
        finish()
    }

    private fun snooze() {
        val info = current ?: return finish()
        if (info.mode == MODE_TIMER) { TimerRepo.reset(this); return finish() }
        val a = currentAlarm ?: AlarmRepo.find(info.alarmId) ?: return finish()
        val t = AlarmScheduler.snooze(this, a.id, a.snoozeMin)
        val at = java.time.Instant.ofEpochMilli(t).atZone(java.time.ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("HH:mm"))
        Notifier.info(this, a.id, "Будильник відкладено", "Задзвонить о $at", silent = true)
        finish()
    }

    private fun finish() {
        job?.cancel(); timeoutJob?.cancel()
        stopSound()
        current = null
        currentAlarm = null
        _ringing.value = null
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        stopSound()
        _ringing.value = null
        tts?.shutdown(); tts = null
        scope.cancel()
        wakeLock?.let { if (it.isHeld) it.release() }
        super.onDestroy()
    }

    // ---------- сповіщення ----------

    private fun goForeground(id: Int, n: Notification) {
        val type = if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK else 0
        runCatching { ServiceCompat.startForeground(this, id, n, type) }
    }

    private fun checkingNotification(): Notification =
        NotificationCompat.Builder(this, App.CH_SERVICE)
            .setSmallIcon(R.drawable.ic_stat_alarm)
            .setContentTitle("Будильник")
            .setContentText("Перевіряю статус повітряної тривоги…")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .build()

    private fun ringNotification(info: RingInfo): Notification {
        val full = PendingIntent.getActivity(
            this, 10,
            Intent(this, AlarmActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        fun svc(action: String, req: Int) = PendingIntent.getService(
            this, req, Intent(this, AlarmService::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val time = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))
        val b = NotificationCompat.Builder(this, App.CH_RING)
            .setSmallIcon(R.drawable.ic_stat_alarm)
            .setContentTitle("$time · ${info.title}")
            .setContentText(info.subtitle)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setFullScreenIntent(full, true)
            .setContentIntent(full)
        if (info.canSnooze) b.addAction(0, "Відкласти", svc(ACTION_SNOOZE, 11))
        b.addAction(0, "Вимкнути", svc(ACTION_DISMISS, 12))
        return b.build()
    }
}
