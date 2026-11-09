package ua.tryvoga.alarm

import android.content.Context
import android.content.SharedPreferences
import android.os.SystemClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import ua.tryvoga.alarm.widget.WidgetUpdater
import kotlin.math.max

object AlarmRepo {
    private lateinit var prefs: SharedPreferences
    private val _alarms = MutableStateFlow<List<Alarm>>(emptyList())
    val alarms: StateFlow<List<Alarm>> = _alarms.asStateFlow()

    @Synchronized
    fun init(ctx: Context) {
        if (::prefs.isInitialized) return
        prefs = ctx.applicationContext.getSharedPreferences("alarms", Context.MODE_PRIVATE)
        _alarms.value = runCatching {
            val arr = JSONArray(prefs.getString("list", "[]"))
            (0 until arr.length()).map { Alarm.fromJson(arr.getJSONObject(it)) }
        }.getOrDefault(emptyList()).sortedBy { it.hour * 60 + it.minute }
    }

    private fun persist(list: List<Alarm>) {
        val sorted = list.sortedBy { it.hour * 60 + it.minute }
        _alarms.value = sorted
        prefs.edit().putString("list", JSONArray(sorted.map { it.toJson() }).toString()).apply()
    }

    fun find(id: Int): Alarm? = _alarms.value.firstOrNull { it.id == id }
    fun newId(): Int = (_alarms.value.maxOfOrNull { it.id } ?: 0) + 1

    fun upsert(ctx: Context, a: Alarm) {
        init(ctx)
        persist(_alarms.value.filter { it.id != a.id } + a)
        if (!a.enabled) clearSnooze(a.id)
        AlarmScheduler.rescheduleAll(ctx)
    }

    fun delete(ctx: Context, id: Int) {
        init(ctx)
        persist(_alarms.value.filter { it.id != id })
        clearSnooze(id); clearWatch(id)
        AlarmScheduler.cancelAll(ctx, id)
        AlarmScheduler.rescheduleAll(ctx)
    }

    fun snoozeUntil(id: Int): Long = if (::prefs.isInitialized) prefs.getLong("snooze_$id", 0) else 0
    fun setSnooze(id: Int, t: Long) = prefs.edit().putLong("snooze_$id", t).apply()
    fun clearSnooze(id: Int) = prefs.edit().remove("snooze_$id").apply()

    fun watchDeadline(id: Int): Long = prefs.getLong("watch_$id", 0)
    fun setWatch(id: Int, deadline: Long) = prefs.edit().putLong("watch_$id", deadline).apply()
    fun clearWatch(id: Int) = prefs.edit().remove("watch_$id").remove("clear_at_$id").apply()
    fun clearAt(id: Int): Long = prefs.getLong("clear_at_$id", 0)
    fun setClearAt(id: Int, t: Long) = prefs.edit().putLong("clear_at_$id", t).apply()
}

object SettingsRepo {
    private lateinit var prefs: SharedPreferences
    private val _settings = MutableStateFlow(AppSettings())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    @Synchronized
    fun init(ctx: Context) {
        if (::prefs.isInitialized) return
        prefs = ctx.applicationContext.getSharedPreferences("settings", Context.MODE_PRIVATE)
        val d = AppSettings()
        _settings.value = AppSettings(
            style = runCatching { UiStyle.valueOf(prefs.getString("style", d.style.name)!!) }.getOrDefault(d.style),
            darkMode = runCatching { DarkMode.valueOf(prefs.getString("dark", d.darkMode.name)!!) }.getOrDefault(d.darkMode),
            dynamicColor = prefs.getBoolean("dynamic", d.dynamicColor),
            locationId = Locations.normalize(prefs.getInt("location", prefs.getInt("region", d.locationId))),
            partialAs = runCatching { AlertLevel.valueOf(prefs.getString("partial2", d.partialAs.name)!!) }.getOrDefault(d.partialAs),
            worldZones = prefs.getString("zones", null)?.split(",")?.filter { it.isNotBlank() } ?: d.worldZones,
            autoUpdate = prefs.getBoolean("autoUpdate", d.autoUpdate),
            liquidFx = prefs.getBoolean("liquidFx", d.liquidFx),
        )
    }

    fun update(ctx: Context, f: (AppSettings) -> AppSettings) {
        init(ctx)
        val s = f(_settings.value)
        _settings.value = s
        prefs.edit()
            .putString("style", s.style.name)
            .putString("dark", s.darkMode.name)
            .putBoolean("dynamic", s.dynamicColor)
            .putInt("location", s.locationId)
            .putString("partial2", s.partialAs.name)
            .putString("zones", s.worldZones.joinToString(","))
            .putBoolean("autoUpdate", s.autoUpdate)
            .putBoolean("liquidFx", s.liquidFx)
            .apply()
        WidgetUpdater.update(ctx)
    }
}

/** Кеш останнього знімка тривог (для віджета й на випадок відсутності мережі). */
object AlertCache {
    private fun prefs(ctx: Context) = ctx.applicationContext.getSharedPreferences("alerts", Context.MODE_PRIVATE)
    fun save(ctx: Context, s: Snapshot) = prefs(ctx).edit().putString("snap", s.toJson()).apply()
    fun load(ctx: Context): Snapshot? = prefs(ctx).getString("snap", null)?.let { Snapshot.fromJson(it) }
}

data class TimerState(
    val totalMs: Long = 5 * 60_000L,
    val endAt: Long = 0,
    val pausedRemaining: Long = 0,
    val running: Boolean = false,
) {
    val active: Boolean get() = running || pausedRemaining > 0
    fun remaining(now: Long = System.currentTimeMillis()): Long = if (running) max(0, endAt - now) else pausedRemaining
}

object TimerRepo {
    private lateinit var prefs: SharedPreferences
    private val _state = MutableStateFlow(TimerState())
    val state: StateFlow<TimerState> = _state.asStateFlow()

    @Synchronized
    fun init(ctx: Context) {
        if (::prefs.isInitialized) return
        prefs = ctx.applicationContext.getSharedPreferences("timer", Context.MODE_PRIVATE)
        _state.value = TimerState(
            prefs.getLong("total", 5 * 60_000L), prefs.getLong("end", 0),
            prefs.getLong("paused", 0), prefs.getBoolean("running", false)
        )
    }

    private fun set(ctx: Context, s: TimerState) {
        _state.value = s
        prefs.edit().putLong("total", s.totalMs).putLong("end", s.endAt)
            .putLong("paused", s.pausedRemaining).putBoolean("running", s.running).apply()
        if (s.running) AlarmScheduler.scheduleTimer(ctx, s.endAt) else AlarmScheduler.cancelTimer(ctx)
    }

    fun start(ctx: Context, total: Long) = set(ctx, TimerState(total, System.currentTimeMillis() + total, 0, true))
    fun pause(ctx: Context) { val s = _state.value; set(ctx, s.copy(running = false, pausedRemaining = max(1, s.remaining()))) }
    fun resume(ctx: Context) { val s = _state.value; set(ctx, s.copy(running = true, endAt = System.currentTimeMillis() + s.pausedRemaining, pausedRemaining = 0)) }
    fun reset(ctx: Context) = set(ctx, TimerState(totalMs = _state.value.totalMs))
    fun addMinute(ctx: Context) {
        val s = _state.value
        if (s.running) set(ctx, s.copy(endAt = s.endAt + 60_000, totalMs = s.totalMs + 60_000))
        else set(ctx, s.copy(pausedRemaining = s.pausedRemaining + 60_000, totalMs = s.totalMs + 60_000))
    }
    fun reschedule(ctx: Context) { val s = _state.value; if (s.running) AlarmScheduler.scheduleTimer(ctx, s.endAt) }
}

data class StopwatchState(
    val running: Boolean = false,
    val startedAt: Long = 0,
    val accumulated: Long = 0,
    val laps: List<Long> = emptyList(),
) {
    fun elapsed(now: Long = SystemClock.elapsedRealtime()): Long = if (running) accumulated + (now - startedAt) else accumulated
}

object StopwatchRepo {
    private val _state = MutableStateFlow(StopwatchState())
    val state: StateFlow<StopwatchState> = _state.asStateFlow()
    fun toggle() {
        val s = _state.value
        val now = SystemClock.elapsedRealtime()
        _state.value = if (s.running) s.copy(running = false, accumulated = s.elapsed(now)) else s.copy(running = true, startedAt = now)
    }
    fun lap() { val s = _state.value; _state.value = s.copy(laps = s.laps + s.elapsed()) }
    fun reset() { _state.value = StopwatchState() }
}
