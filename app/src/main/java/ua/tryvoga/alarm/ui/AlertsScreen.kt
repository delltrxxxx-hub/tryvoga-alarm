package ua.tryvoga.alarm.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.json.JSONObject
import ua.tryvoga.alarm.AlertLevel
import ua.tryvoga.alarm.AppSettings
import ua.tryvoga.alarm.Loc
import ua.tryvoga.alarm.Locations
import ua.tryvoga.alarm.SettingsRepo
import ua.tryvoga.alarm.Snapshot
import ua.tryvoga.alarm.Threat
import ua.tryvoga.alarm.threatName
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.max
import kotlin.math.min

// ===================== дані карти =====================

class MapShape(val id: Int, val rings: List<FloatArray>, val label: Offset) {
    val path: Path = Path().apply {
        rings.forEach { r ->
            moveTo(r[0], r[1])
            var i = 2
            while (i < r.size) { lineTo(r[i], r[i + 1]); i += 2 }
            close()
        }
    }
    fun contains(x: Float, y: Float): Boolean = rings.any { r ->
        var inside = false
        var j = r.size - 2
        var i = 0
        while (i < r.size) {
            val xi = r[i]; val yi = r[i + 1]; val xj = r[j]; val yj = r[j + 1]
            if ((yi > y) != (yj > y) && x < (xj - xi) * (y - yi) / (yj - yi) + xi) inside = !inside
            j = i; i += 2
        }
        inside
    }
}

/** Межі областей: geoBoundaries (CC BY 4.0), спрощено. */
object MapData {
    var w = 1000f; private set
    var h = 700f; private set
    var shapes: List<MapShape> = emptyList(); private set

    fun init(ctx: Context) {
        if (shapes.isNotEmpty()) return
        val o = JSONObject(ctx.assets.open("ukraine_map.json").bufferedReader().use { it.readText() })
        w = o.getDouble("w").toFloat(); h = o.getDouble("h").toFloat()
        val arr = o.getJSONArray("o")
        val list = ArrayList<MapShape>()
        for (i in 0 until arr.length()) {
            val s = arr.getJSONObject(i)
            val p = s.getJSONArray("p")
            val rings = (0 until p.length()).map { k ->
                val a = p.getJSONArray(k); FloatArray(a.length()) { a.getDouble(it).toFloat() }
            }
            list += MapShape(s.getInt("id"), rings, Offset(s.getDouble("lx").toFloat(), s.getDouble("ly").toFloat()))
        }
        // Київ малюємо останнім — поверх Київської області
        shapes = list.sortedBy { if (it.id == 31) 1 else 0 }
    }
}

// ===================== екран =====================

@Composable
fun AlertsScreen(vm: MainViewModel, settings: AppSettings, snap: Snapshot?) {
    val ctx = LocalContext.current
    var mode by rememberSaveable { mutableIntStateOf(0) }
    var selected by rememberSaveable { mutableStateOf<Int?>(null) }
    val loading by vm.loading.collectAsState()
    val error by vm.error.collectAsState()
    val myLoc = Locations.getOrDefault(settings.locationId)
    val active = Locations.oblasts.count { o -> snap?.let { s -> s.oblastFill(o.id).let { it.first.isAlert || it.second.isAlert } } == true }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        GlassSurface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp)) {
            Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Тривога в $active з ${Locations.oblasts.size} регіонів", color = onBg(), fontWeight = FontWeight.SemiBold)
                    val t = snap?.time?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm:ss")) }
                    Text(if (t != null) "Оновлено о $t · ${snap.source}" else "Завантаження…", color = dimText(), fontSize = 12.sp)
                    error?.let { Text(it, color = AlertRed, fontSize = 12.sp, maxLines = 2) }
                }
                if (loading) CircularProgressIndicator(Modifier.padding(10.dp).size(22.dp), strokeWidth = 2.dp)
                else IconButton(onClick = { vm.refreshNow() }) { Icon(Icons.Rounded.Refresh, "Оновити", tint = onBg()) }
            }
        }
        Spacer(Modifier.height(10.dp))
        Segmented(listOf(0, 1), mode, { if (it == 0) "Карта" else "Список тривог" }, { mode = it })
        Spacer(Modifier.height(10.dp))

        if (mode == 0) {
            Box(Modifier.weight(1f).padding(bottom = 96.dp)) {
                GlassSurface(Modifier.fillMaxSize()) {
                    UkraineMap(snap, selected, myLoc.oblast.id, { selected = it }, Modifier.fillMaxSize())
                    Legend(Modifier.align(Alignment.TopStart).padding(12.dp))
                    IconButton(
                        onClick = { runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://alerts.in.ua/"))) } },
                        modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)
                    ) { Icon(Icons.AutoMirrored.Rounded.OpenInNew, "Відкрити alerts.in.ua", tint = dimText()) }
                    Text("Двічі торкніться — скинути масштаб", Modifier.align(Alignment.BottomCenter).padding(bottom = 10.dp), color = dimText(), fontSize = 11.sp)
                }
                androidx.compose.animation.AnimatedVisibility(
                    selected != null, Modifier.align(Alignment.BottomCenter),
                    enter = slideInVertically(spring(0.7f, 400f)) { it } + fadeIn(), exit = slideOutVertically { it } + fadeOut()
                ) {
                    selected?.let { id -> Locations.get(id)?.let { o -> OblastCard(o, snap, myLoc, { selected = null }) { l -> SettingsRepo.update(ctx) { it.copy(locationId = l.id) }; vm.refreshNow() } } }
                }
            }
        } else {
            val groups = Locations.oblasts.mapNotNull { o -> snap?.inside(o.id)?.takeIf { it.isNotEmpty() }?.let { o to it } }
                .sortedWith(compareByDescending<Pair<Loc, List<Pair<Loc, Threat>>>> { g -> g.second.any { it.first.id == g.first.id } }.thenBy { it.first.name })
            LazyColumn(contentPadding = PaddingValues(bottom = 110.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (snap != null && groups.isEmpty()) item {
                    GlassSurface(Modifier.fillMaxWidth(), tint = AlertGreen) {
                        Text("Зараз тривог немає по всій Україні", Modifier.padding(18.dp), color = onBg(), fontWeight = FontWeight.SemiBold)
                    }
                }
                items(groups, key = { it.first.id }) { (o, list) ->
                    val (own, part) = snap!!.oblastFill(o.id)
                    val lvl = if (own.isAlert) own else part
                    GlassSurface(Modifier.fillMaxWidth().animateItem(), shape = RoundedCornerShape(20.dp), tint = lvl.color()) {
                        Column(Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                StatusDot(lvl, 7.dp)
                                Spacer(Modifier.width(6.dp))
                                Text(o.name, color = onBg(), fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                                Text(if (own.isAlert) "уся область" else "частково", color = lvl.color(), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }
                            list.filter { it.first.id != o.id }.take(12).forEach { (l, t) -> ThreatLine(l, t) }
                            if (list.size > 13) Text("…та ще ${list.size - 13}", color = dimText(), fontSize = 12.sp, modifier = Modifier.padding(start = 26.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ThreatLine(l: Loc, t: Threat) {
    Row(Modifier.padding(start = 26.dp, top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(t.level.color()))
        Spacer(Modifier.width(8.dp))
        Column {
            Text(l.short, color = onBg(), fontSize = 14.sp)
            Text(t.types.joinToString(", ") { threatName(it) }, color = dimText(), fontSize = 11.sp)
        }
    }
}

@Composable
private fun Legend(modifier: Modifier) {
    Column(modifier.clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.background.copy(alpha = 0.45f)).padding(8.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        listOf(AlertRed to "Червона", AlertYellow to "Жовта", AlertRed.copy(alpha = 0.35f) to "Частково").forEach { (c, t) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).clip(RoundedCornerShape(3.dp)).background(c))
                Spacer(Modifier.width(6.dp))
                Text(t, color = onBg(), fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun OblastCard(o: Loc, snap: Snapshot?, myLoc: Loc, onClose: () -> Unit, onPick: (Loc) -> Unit) {
    val list = snap?.inside(o.id).orEmpty()
    val (own, part) = snap?.oblastFill(o.id) ?: (AlertLevel.UNKNOWN to AlertLevel.NONE)
    val lvl = if (own.isAlert) own else part
    GlassSurface(Modifier.fillMaxWidth().padding(8.dp), tint = if (lvl.isAlert) lvl.color() else Color.Unspecified) {
        // Щільна підкладка: карта під карткою не має просвічувати, інакше текст зливається.
        val panel = if (LocalIsDark.current) Color(0xFF111118).copy(alpha = 0.94f) else Color.White.copy(alpha = 0.96f)
        Column(Modifier.fillMaxWidth().background(panel).padding(14.dp).heightIn(max = 260.dp).verticalScroll(rememberScrollState())) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusDot(if (lvl.isAlert) lvl else own, 7.dp)
                Spacer(Modifier.width(6.dp))
                Column(Modifier.weight(1f)) {
                    Text(o.name, color = onBg(), fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
                    Text(
                        when {
                            own.isAlert -> own.title + " на всю область"
                            part.isAlert -> "Тривога в частині області"
                            own == AlertLevel.UNKNOWN -> "Немає даних"
                            else -> "Тривоги немає"
                        }, color = if (lvl.isAlert) lvl.color() else AlertGreen, fontSize = 13.sp, fontWeight = FontWeight.SemiBold
                    )
                }
                IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, "Закрити", tint = dimText()) }
            }
            list.filter { it.first.id != o.id }.forEach { (l, t) -> ThreatLine(l, t) }
            if (myLoc.oblast.id != o.id) {
                Spacer(Modifier.height(10.dp))
                GlassButton("Зробити моєю територією", { onPick(o) }, Modifier.fillMaxWidth(), Icons.Rounded.Place)
            }
        }
    }
}

// ===================== сама карта =====================

@Composable
fun UkraineMap(snap: Snapshot?, selected: Int?, myOblast: Int, onSelect: (Int?) -> Unit, modifier: Modifier) {
    val ctx = LocalContext.current
    remember { MapData.init(ctx); Locations.init(ctx); 0 }
    val shapes = MapData.shapes
    val zoom = remember { Animatable(1f) }
    val panX = remember { Animatable(0f) }
    val panY = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val pulseT = rememberInfiniteTransition(label = "map")
    val pulse by pulseT.animateFloat(0.55f, 0.85f, infiniteRepeatable(tween(1300), RepeatMode.Reverse), label = "pulse")
    val dark = LocalIsDark.current
    val glass = LocalUiStyle.current == ua.tryvoga.alarm.UiStyle.GLASS
    val base = when {
        glass && dark -> Color.White.copy(alpha = 0.10f)
        glass -> Color.White.copy(alpha = 0.6f)
        else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
    }
    val stroke = onBg().copy(alpha = 0.35f)
    val primary = MaterialTheme.colorScheme.primary
    val fg = onBg()
    val measurer = rememberTextMeasurer()
    val labels = remember { shapes.associate { s -> s.id to (Locations.get(s.id)?.name ?: "").replace(" область", "").replace("Автономна Республіка ", "АР ") } }

    BoxWithConstraints(modifier) {
        val wPx = constraints.maxWidth.toFloat()
        val hPx = constraints.maxHeight.toFloat()
        val fit = min(wPx / MapData.w, hPx / MapData.h) * 0.94f
        val ox = (wPx - MapData.w * fit) / 2
        val oy = (hPx - MapData.h * fit) / 2

        fun toMap(p: Offset): Offset {
            val s = fit * zoom.value
            return Offset((p.x - ox - panX.value) / s, (p.y - oy - panY.value) / s)
        }

        Canvas(
            Modifier.fillMaxSize()
                .pointerInput(Unit) {
                    detectTransformGestures { centroid, pan, z, _ ->
                        scope.launch {
                            val old = zoom.value
                            val nz = (old * z).coerceIn(1f, 7f)
                            // масштаб навколо точки між пальцями
                            val cx = centroid.x - ox; val cy = centroid.y - oy
                            val nx = cx - (cx - panX.value) * (nz / old) + pan.x
                            val ny = cy - (cy - panY.value) * (nz / old) + pan.y
                            zoom.snapTo(nz)
                            val lim = max(wPx, hPx) * (nz - 1f) + 80f
                            panX.snapTo(nx.coerceIn(-lim, lim / 4)); panY.snapTo(ny.coerceIn(-lim, lim / 4))
                        }
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures(
                        onDoubleTap = {
                            scope.launch { launch { zoom.animateTo(1f, spring(0.6f, 300f)) }; launch { panX.animateTo(0f, spring(0.6f, 300f)) }; panY.animateTo(0f, spring(0.6f, 300f)) }
                        },
                        onTap = { p ->
                            val m = toMap(p)
                            val hit = shapes.lastOrNull { it.contains(m.x, m.y) }
                            onSelect(hit?.id?.takeIf { it != selected })
                        }
                    )
                }
        ) {
            val s = fit * zoom.value
            withTransform({
                translate(ox + panX.value, oy + panY.value)
                scale(s, s, pivot = Offset.Zero)
            }) {
                for (sh in shapes) {
                    val (own, part) = snap?.oblastFill(sh.id) ?: (AlertLevel.NONE to AlertLevel.NONE)
                    drawPath(sh.path, base, style = Fill)
                    when {
                        own.isAlert -> drawPath(sh.path, own.color().copy(alpha = pulse), style = Fill)
                        part.isAlert -> drawPath(sh.path, part.color().copy(alpha = 0.32f), style = Fill)
                    }
                    drawPath(sh.path, stroke, style = Stroke(width = 1.2f / s))
                }
                shapes.firstOrNull { it.id == myOblast }?.let { drawPath(it.path, primary, style = Stroke(width = 3f / s)) }
                shapes.filter { it.id == selected }.forEach { drawPath(it.path, fg, style = Stroke(width = 3.5f / s)) }
            }
            // підписи — коли достатньо великий масштаб
            if (zoom.value > 1.6f) {
                for (sh in shapes) {
                    if (sh.id == 31) continue
                    val text = labels[sh.id] ?: continue
                    val lay = measurer.measure(text, TextStyle(color = fg, fontSize = 10.sp, fontWeight = FontWeight.Medium))
                    val px = ox + panX.value + sh.label.x * s - lay.size.width / 2
                    val py = oy + panY.value + sh.label.y * s - lay.size.height / 2
                    if (px > -200 && px < wPx && py > -50 && py < hPx) drawText(lay, topLeft = Offset(px, py))
                }
            }
        }
    }
}
