package ua.tryvoga.alarm

import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Locale

val UK: Locale = Locale.forLanguageTag("uk-UA")

enum class AlertLevel {
    NONE, YELLOW, RED, UNKNOWN;

    val isAlert: Boolean get() = this == YELLOW || this == RED
    val title: String
        get() = when (this) {
            NONE -> "Тривоги немає"
            YELLOW -> "Жовта тривога"
            RED -> "Червона тривога"
            UNKNOWN -> "Немає даних"
        }
}

enum class UiStyle { GLASS, MATERIAL }
enum class DarkMode { SYSTEM, LIGHT, DARK }

val DAY_SHORT = listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Нд")

data class Alarm(
    val id: Int,
    val hour: Int,
    val minute: Int,
    val enabled: Boolean = true,
    /** 1 = понеділок … 7 = неділя. Порожньо — одноразовий. */
    val days: Set<Int> = emptySet(),
    val label: String = "",
    val ringtoneUri: String? = null,
    val volume: Int = 100,
    val vibrate: Boolean = true,
    val gradual: Boolean = true,
    val announce: Boolean = true,
    val snoozeMin: Int = 10,
    // --- режим тривоги ---
    val alertAware: Boolean = true,
    val redVolume: Int = 0,
    val redVibrate: Boolean = false,
    val yellowVolume: Int = 30,
    val yellowVibrate: Boolean = true,
    val wakeAfterAllClear: Boolean = true,
    val allClearDelayMin: Int = 5,
    val maxWaitMin: Int = 180,
) {
    val timeText: String get() = "%02d:%02d".format(hour, minute)

    fun nextTrigger(from: LocalDateTime = LocalDateTime.now()): LocalDateTime {
        var cand = from.withHour(hour).withMinute(minute).withSecond(0).withNano(0)
        if (!cand.isAfter(from)) cand = cand.plusDays(1)
        if (days.isEmpty()) return cand
        var guard = 0
        while (cand.dayOfWeek.value !in days && guard++ < 8) cand = cand.plusDays(1)
        return cand
    }

    fun nextTriggerMillis(fromMillis: Long = System.currentTimeMillis()): Long {
        val zone = ZoneId.systemDefault()
        val from = LocalDateTime.ofInstant(Instant.ofEpochMilli(fromMillis), zone)
        return nextTrigger(from).atZone(zone).toInstant().toEpochMilli()
    }

    fun daysText(): String = when {
        days.isEmpty() -> "Одноразово"
        days.size == 7 -> "Щодня"
        days == setOf(1, 2, 3, 4, 5) -> "Будні"
        days == setOf(6, 7) -> "Вихідні"
        else -> days.sorted().joinToString(" ") { DAY_SHORT[it - 1] }
    }

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id); put("hour", hour); put("minute", minute); put("enabled", enabled)
        put("days", JSONArray(days.toList())); put("label", label)
        ringtoneUri?.let { put("ringtone", it) }
        put("volume", volume); put("vibrate", vibrate); put("gradual", gradual)
        put("announce", announce); put("snooze", snoozeMin)
        put("alertAware", alertAware); put("redVolume", redVolume); put("redVibrate", redVibrate)
        put("yellowVolume", yellowVolume); put("yellowVibrate", yellowVibrate)
        put("wakeAfter", wakeAfterAllClear); put("clearDelay", allClearDelayMin); put("maxWait", maxWaitMin)
    }

    companion object {
        private val D = Alarm(0, 0, 0)
        fun fromJson(o: JSONObject): Alarm {
            val arr = o.optJSONArray("days")
            val days = if (arr == null) emptySet() else (0 until arr.length()).map { arr.getInt(it) }.toSet()
            return Alarm(
                id = o.getInt("id"),
                hour = o.getInt("hour"),
                minute = o.getInt("minute"),
                enabled = o.optBoolean("enabled", true),
                days = days,
                label = o.optString("label", ""),
                ringtoneUri = if (o.has("ringtone")) o.getString("ringtone") else null,
                volume = o.optInt("volume", D.volume),
                vibrate = o.optBoolean("vibrate", D.vibrate),
                gradual = o.optBoolean("gradual", D.gradual),
                announce = o.optBoolean("announce", D.announce),
                snoozeMin = o.optInt("snooze", D.snoozeMin),
                alertAware = o.optBoolean("alertAware", D.alertAware),
                redVolume = o.optInt("redVolume", D.redVolume),
                redVibrate = o.optBoolean("redVibrate", D.redVibrate),
                yellowVolume = o.optInt("yellowVolume", D.yellowVolume),
                yellowVibrate = o.optBoolean("yellowVibrate", D.yellowVibrate),
                wakeAfterAllClear = o.optBoolean("wakeAfter", D.wakeAfterAllClear),
                allClearDelayMin = o.optInt("clearDelay", D.allClearDelayMin),
                maxWaitMin = o.optInt("maxWait", D.maxWaitMin),
            )
        }
    }
}

data class AppSettings(
    val style: UiStyle = UiStyle.GLASS,
    val darkMode: DarkMode = DarkMode.SYSTEM,
    val dynamicColor: Boolean = false,
    /** id області / району / громади (нумерація ukrainealarm / alerts.in.ua). */
    val locationId: Int = 31,
    /** Як трактувати тривогу лише в частині обраної території (напр. в одному районі області). */
    val partialAs: AlertLevel = AlertLevel.NONE,
    val worldZones: List<String> = listOf("Europe/London", "America/New_York", "Asia/Tokyo"),
    /** Перевіряти оновлення на GitHub при кожному запуску. */
    val autoUpdate: Boolean = true,
    /** Справжнє «рідке скло» (розмиття й заломлення) під нижньою панеллю. Вимикається саме, якщо телефон не тягне. */
    val liquidFx: Boolean = true,
)

fun formatCountdown(ms: Long): String {
    if (ms <= 0) return "зараз"
    val totalMin = (ms + 59_999) / 60_000
    val d = totalMin / 1440
    val h = (totalMin % 1440) / 60
    val m = totalMin % 60
    return buildString {
        if (d > 0) append("$d д ")
        if (h > 0) append("$h год ")
        if (m > 0 || (d == 0L && h == 0L)) append("$m хв")
    }.trim()
}

fun plural(n: Int, one: String, few: String, many: String): String {
    val n10 = n % 10
    val n100 = n % 100
    return when {
        n10 == 1 && n100 != 11 -> one
        n10 in 2..4 && n100 !in 12..14 -> few
        else -> many
    }
}

fun safeZone(id: String): ZoneId = runCatching { ZoneId.of(id) }.getOrElse {
    if (id == "Europe/Kyiv") runCatching { ZoneId.of("Europe/Kiev") }.getOrDefault(ZoneId.systemDefault())
    else ZoneId.systemDefault()
}
