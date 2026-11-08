package ua.tryvoga.alarm

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

enum class LocType { OBLAST, RAION, HROMADA }

class Loc(val id: Int, val name: String, val type: LocType, val parent: Loc?) {
    val children = mutableListOf<Loc>()
    val short: String get() = shortName(name)
    val oblast: Loc get() = parent?.oblast ?: this
    /** «Чернігівська обл. › Чернігівський р-н» */
    val path: String get() = generateSequence(parent) { it.parent }.toList().reversed().joinToString(" › ") { it.short }
    fun isDescendantOf(other: Loc): Boolean {
        var p = parent
        while (p != null) { if (p.id == other.id) return true; p = p.parent }
        return false
    }
    val typeTitle: String get() = when (type) { LocType.OBLAST -> "область"; LocType.RAION -> "район"; LocType.HROMADA -> "громада" }
}

fun shortName(n: String): String = n
    .replace(" територіальна громада", " громада")
    .replace(" міська громада", " міська гр.")
    .replace(" область", " обл.")
    .replace(" район", " р-н")

/** Дерево Область → Район → Громада (з assets/regions.json, дані ukrainealarm / siren.pp.ua). */
object Locations {
    @Volatile private var byId: Map<Int, Loc> = emptyMap()
    var oblasts: List<Loc> = emptyList(); private set

    fun init(ctx: Context) {
        if (byId.isNotEmpty()) return
        synchronized(this) {
            if (byId.isNotEmpty()) return
            val txt = ctx.applicationContext.assets.open("regions.json").bufferedReader(Charsets.UTF_8).use { it.readText() }
            val arr = JSONArray(txt)
            val map = HashMap<Int, Loc>(2000)
            val tops = ArrayList<Loc>()
            fun parse(o: JSONObject, parent: Loc?): Loc {
                val t = when (o.optString("t")) { "o" -> LocType.OBLAST; "r" -> LocType.RAION; else -> LocType.HROMADA }
                val l = Loc(o.getInt("i"), o.getString("n"), t, parent)
                map[l.id] = l
                o.optJSONArray("c")?.let { c -> for (i in 0 until c.length()) l.children += parse(c.getJSONObject(i), l) }
                return l
            }
            // спершу області з дітьми, потім «окремі» міста без дітей (якщо їх ще немає в дереві)
            val objs = (0 until arr.length()).map { arr.getJSONObject(it) }
            objs.filter { it.has("c") }.forEach { tops += parse(it, null) }
            objs.filter { !it.has("c") }.forEach { o -> if (!map.containsKey(o.getInt("i"))) tops += parse(o, null) }
            // український алфавітний порядок (І, Ї, Є, Ґ — на своїх місцях)
            val coll = java.text.Collator.getInstance(UK)
            fun sortRec(l: Loc) { l.children.sortWith { a, b -> coll.compare(a.name, b.name) }; l.children.forEach { sortRec(it) } }
            tops.forEach { sortRec(it) }
            byId = map
            oblasts = tops.filter { it.children.isNotEmpty() || it.id == 31 || it.id == 9999 }
                .sortedWith { a, b -> coll.compare(a.name.replace("м. ", "яя "), b.name.replace("м. ", "яя ")) }
        }
    }

    fun normalize(id: Int): Int = when (id) { 29, 30 -> 9999; else -> id }
    fun get(id: Int): Loc? = byId[normalize(id)]
    fun getOrDefault(id: Int): Loc = get(id) ?: byId[31] ?: oblasts.first()
    fun all(): Collection<Loc> = byId.values
}

// ---------------- статуси ----------------

val THREAT_NAMES = mapOf(
    "AIR" to "повітряна тривога",
    "ARTILLERY" to "загроза артобстрілу",
    "URBAN_FIGHTS" to "загроза вуличних боїв",
    "CHEMICAL" to "хімічна загроза",
    "NUCLEAR" to "радіаційна загроза",
    "INFO" to "інформаційне повідомлення",
)

fun threatName(t: String) = THREAT_NAMES[t] ?: "загроза"

fun maxLevel(a: AlertLevel, b: AlertLevel): AlertLevel = when {
    a == AlertLevel.RED || b == AlertLevel.RED -> AlertLevel.RED
    a == AlertLevel.YELLOW || b == AlertLevel.YELLOW -> AlertLevel.YELLOW
    a == AlertLevel.NONE || b == AlertLevel.NONE -> AlertLevel.NONE
    else -> AlertLevel.UNKNOWN
}

data class Threat(val level: AlertLevel, val types: Set<String>)

data class Snapshot(val threats: Map<Int, Threat>, val time: Long, val source: String) {
    fun toJson(): String {
        val a = JSONObject()
        threats.forEach { (id, t) -> a.put(id.toString(), (if (t.level == AlertLevel.RED) "R" else "Y") + ":" + t.types.joinToString(",")) }
        return JSONObject().put("t", time).put("src", source).put("a", a).toString()
    }

    /** Статус території: тривога в ній самій або в «батьках» (район, область) + часткові тривоги всередині. */
    fun statusOf(loc: Loc, partialAs: AlertLevel): LocStatus {
        var lvl = AlertLevel.NONE
        val types = linkedSetOf<String>()
        var n: Loc? = loc
        while (n != null) {
            threats[n.id]?.let { lvl = maxLevel(lvl, it.level); types += it.types }
            n = n.parent
        }
        val partial = ArrayList<Pair<Loc, Threat>>()
        var pl = AlertLevel.NONE
        if (lvl == AlertLevel.NONE) {
            for ((id, t) in threats) {
                val l = Locations.get(id) ?: continue
                if (l.isDescendantOf(loc)) { partial += l to t; pl = maxLevel(pl, t.level) }
            }
        }
        val eff = when {
            lvl != AlertLevel.NONE -> lvl
            partial.isNotEmpty() && partialAs != AlertLevel.NONE -> partialAs
            else -> AlertLevel.NONE
        }
        return LocStatus(eff, lvl, types, partial, pl)
    }

    /** Для карти: рівень області цілком і рівень «часткових» тривог у ній. */
    fun oblastFill(oblastId: Int): Pair<AlertLevel, AlertLevel> {
        val o = Locations.get(oblastId) ?: return AlertLevel.NONE to AlertLevel.NONE
        val own = threats[o.id]?.level ?: AlertLevel.NONE
        var part = AlertLevel.NONE
        if (own == AlertLevel.NONE) for ((id, t) in threats) {
            val l = Locations.get(id) ?: continue
            if (l.isDescendantOf(o)) part = maxLevel(part, t.level)
        }
        return own to part
    }

    /** Активні тривоги в межах області (сама область, райони, громади). */
    fun inside(oblastId: Int): List<Pair<Loc, Threat>> {
        val o = Locations.get(oblastId) ?: return emptyList()
        return threats.mapNotNull { (id, t) -> Locations.get(id)?.takeIf { it.id == o.id || it.isDescendantOf(o) }?.let { it to t } }
            .sortedWith(compareBy({ it.first.type.ordinal }, { it.first.name }))
    }

    companion object {
        fun fromJson(s: String): Snapshot? = runCatching {
            val o = JSONObject(s)
            val a = o.getJSONObject("a")
            val m = HashMap<Int, Threat>()
            a.keys().forEach { k ->
                val v = a.getString(k)
                val lvl = if (v.startsWith("R")) AlertLevel.RED else AlertLevel.YELLOW
                m[k.toInt()] = Threat(lvl, v.substringAfter(':').split(',').filter { it.isNotBlank() }.toSet())
            }
            Snapshot(m, o.getLong("t"), o.optString("src"))
        }.getOrNull()
    }
}

data class LocStatus(
    /** Рівень, який впливає на будильник. */
    val level: AlertLevel,
    /** Тривога безпосередньо на цій території (або на всю область/район). */
    val direct: AlertLevel,
    val types: Set<String>,
    val partial: List<Pair<Loc, Threat>>,
    val partialLevel: AlertLevel,
) {
    val title: String
        get() = when {
            level == AlertLevel.UNKNOWN -> "Немає даних"
            direct.isAlert -> direct.title
            partial.isNotEmpty() -> "Частково: " + partialLevel.title.lowercase()
            else -> "Тривоги немає"
        }
    val detail: String?
        get() = when {
            direct.isAlert && types.isNotEmpty() -> types.joinToString(", ") { threatName(it) }.replaceFirstChar { it.uppercase() }
            partial.isNotEmpty() -> "У межах території: " + partial.take(3).joinToString(", ") { it.first.short } +
                if (partial.size > 3) " та ще ${partial.size - 3}" else ""
            else -> null
        }
    /** Колір для індикаторів (часткова — колір часткової тривоги). */
    val shownLevel: AlertLevel get() = if (level == AlertLevel.UNKNOWN) AlertLevel.UNKNOWN else if (direct.isAlert) direct else if (partial.isNotEmpty()) partialLevel else AlertLevel.NONE

    companion object {
        val UNKNOWN = LocStatus(AlertLevel.UNKNOWN, AlertLevel.UNKNOWN, emptySet(), emptyList(), AlertLevel.NONE)
    }
}
