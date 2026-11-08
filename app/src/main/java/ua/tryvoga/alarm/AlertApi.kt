package ua.tryvoga.alarm

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Безкоштовні публічні джерела без токена, обидва — з точністю до громади:
 *  1) siren.pp.ua/api/v3/alerts — дзеркало даних «Повітряна тривога» (ukrainealarm): рівні Red/Yellow, типи загроз;
 *  2) api.alerts.in.ua/v3/alerts/active.json — публічна стрічка сайту alerts.in.ua (резерв).
 */
object AlertApi {
    private const val SIREN = "https://siren.pp.ua/api/v3/alerts"
    private const val ALERTS_IN_UA = "https://api.alerts.in.ua/v3/alerts/active.json"

    private val _snapshot = MutableStateFlow<Snapshot?>(null)
    val snapshot: StateFlow<Snapshot?> = _snapshot.asStateFlow()

    fun cached(ctx: Context): Snapshot? {
        _snapshot.value?.let { return it }
        return AlertCache.load(ctx)?.also { _snapshot.value = it }
    }

    private fun get(url: String): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 7000
        c.readTimeout = 8000
        c.setRequestProperty("Accept", "application/json")
        c.setRequestProperty("User-Agent", "TryvogaAlarm/1.1 (Android)")
        try {
            val code = c.responseCode
            if (code !in 200..299) throw IOException("HTTP $code")
            return c.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            c.disconnect()
        }
    }

    private fun fromSiren(): Snapshot {
        val arr = JSONArray(get(SIREN))
        val m = HashMap<Int, Threat>()
        for (i in 0 until arr.length()) {
            val r = arr.getJSONObject(i)
            val id = Locations.normalize(r.optString("regionId").toIntOrNull() ?: continue)
            val alerts = r.optJSONArray("activeAlerts") ?: continue
            var lvl = AlertLevel.NONE
            val types = linkedSetOf<String>()
            for (j in 0 until alerts.length()) {
                val a = alerts.getJSONObject(j)
                types += a.optString("type", "AIR")
                val levels = a.optJSONArray("activeAlertLevels")
                if (levels == null || levels.length() == 0) lvl = maxLevel(lvl, AlertLevel.RED)
                else for (k in 0 until levels.length()) {
                    lvl = maxLevel(lvl, if (levels.getJSONObject(k).optString("alertLevel").equals("Yellow", true)) AlertLevel.YELLOW else AlertLevel.RED)
                }
            }
            if (lvl.isAlert) m[id] = Threat(lvl, types)
        }
        return Snapshot(m, System.currentTimeMillis(), "siren.pp.ua")
    }

    private fun fromAlertsInUa(): Snapshot {
        val arr = JSONObject(get(ALERTS_IN_UA)).getJSONArray("alerts")
        val m = HashMap<Int, Threat>()
        for (i in 0 until arr.length()) {
            val a = arr.getJSONObject(i)
            val id = Locations.normalize(a.optInt("luid", -1))
            if (id < 0) continue
            val lvl = if (a.optInt("al", 0) == 1) AlertLevel.YELLOW else AlertLevel.RED
            val type = when (a.optInt("at", 0)) { 0 -> "AIR"; 1 -> "ARTILLERY"; 2 -> "URBAN_FIGHTS"; else -> "OTHER" }
            val prev = m[id]
            m[id] = if (prev == null) Threat(lvl, setOf(type)) else Threat(maxLevel(prev.level, lvl), prev.types + type)
        }
        return Snapshot(m, System.currentTimeMillis(), "alerts.in.ua")
    }

    /** Свіжі дані: основне джерело, при збої — резервне. */
    suspend fun fetch(ctx: Context): Snapshot = withContext(Dispatchers.IO) {
        Locations.init(ctx)
        var last: Exception? = null
        for (src in listOf(::fromSiren, ::fromAlertsInUa)) {
            try {
                val s = src()
                AlertCache.save(ctx, s)
                _snapshot.value = s
                return@withContext s
            } catch (e: Exception) { last = e }
        }
        throw IOException("Немає зв'язку з серверами тривог" + (last?.message?.let { " ($it)" } ?: ""))
    }

    /** Для будильника: 3 спроби, далі — кеш не старший 15 хв, інакше «невідомо» (будильник дзвонить звичайно). */
    suspend fun statusForAlarm(ctx: Context): LocStatus {
        SettingsRepo.init(ctx); Locations.init(ctx)
        val s = SettingsRepo.settings.value
        val loc = Locations.getOrDefault(s.locationId)
        // Не довше ~10 с: будильник не повинен запізнюватися через погану мережу.
        val fresh = kotlinx.coroutines.withTimeoutOrNull(10_000) {
            var res: LocStatus? = null
            for (attempt in 0 until 3) {
                try { res = fetch(ctx).statusOf(loc, s.partialAs); break } catch (_: Exception) { if (attempt < 2) delay(1000) }
            }
            res
        }
        if (fresh != null) return fresh
        val c = AlertCache.load(ctx)
        return if (c != null && System.currentTimeMillis() - c.time < 15 * 60_000) c.statusOf(loc, s.partialAs) else LocStatus.UNKNOWN
    }

    fun cachedStatus(ctx: Context): LocStatus {
        SettingsRepo.init(ctx); Locations.init(ctx)
        val s = SettingsRepo.settings.value
        return cached(ctx)?.statusOf(Locations.getOrDefault(s.locationId), s.partialAs) ?: LocStatus.UNKNOWN
    }
}
