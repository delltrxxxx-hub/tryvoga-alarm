package ua.tryvoga.alarm.ui

import android.os.SystemClock
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import ua.tryvoga.alarm.AppSettings
import ua.tryvoga.alarm.SettingsRepo
import ua.tryvoga.alarm.StopwatchRepo
import ua.tryvoga.alarm.TimerRepo
import ua.tryvoga.alarm.UK
import ua.tryvoga.alarm.safeZone
import java.time.Duration
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.min

private fun fmtHms(ms: Long): String {
    val s = (ms + 999) / 1000
    val h = s / 3600; val m = (s % 3600) / 60; val sec = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%02d:%02d".format(m, sec)
}

// ===================== ТАЙМЕР =====================

@Composable
fun TimerScreen() {
    val ctx = LocalContext.current
    val st by TimerRepo.state.collectAsState()
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(st.running) { while (st.running) { now = System.currentTimeMillis(); delay(50) } }

    AnimatedContent(
        st.active,
        transitionSpec = { (scaleIn(spring(0.6f, 300f), 0.85f) + fadeIn()) togetherWith (scaleOut(targetScale = 1.1f) + fadeOut()) },
        label = "timer"
    ) { active ->
        if (!active) {
            var h by rememberSaveable { mutableIntStateOf((st.totalMs / 3_600_000).toInt()) }
            var m by rememberSaveable { mutableIntStateOf(((st.totalMs / 60_000) % 60).toInt()) }
            var s by rememberSaveable { mutableIntStateOf(((st.totalMs / 1000) % 60).toInt()) }
            var key by remember { mutableIntStateOf(0) }
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                GlassSurface(Modifier.fillMaxWidth()) {
                    androidx.compose.runtime.key(key) {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                                WheelPicker(24, h, { h = it }, fontSize = 40.sp); Text("год", color = dimText())
                            }
                            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                                WheelPicker(60, m, { m = it }, fontSize = 40.sp); Text("хв", color = dimText())
                            }
                            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                                WheelPicker(60, s, { s = it }, fontSize = 40.sp); Text("сек", color = dimText())
                            }
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
                ChoicePills(listOf(1, 3, 5, 10, 15, 30, 60), -1, { if (it == 60) "1 год" else "$it хв" }, {
                    h = it / 60; m = it % 60; s = 0; key++
                })
                Spacer(Modifier.height(24.dp))
                val total = (h * 3600L + m * 60L + s) * 1000L
                GlassButton("Старт", { TimerRepo.start(ctx, total) }, Modifier.fillMaxWidth(0.6f), Icons.Rounded.PlayArrow, primary = true, enabled = total > 0)
                Spacer(Modifier.height(120.dp))
            }
        } else {
            val remaining = st.remaining(now)
            val progress by animateFloatAsState(
                if (st.totalMs > 0) remaining.toFloat() / st.totalMs else 0f,
                spring(dampingRatio = 1f, stiffness = 200f), label = "p"
            )
            val primary = MaterialTheme.colorScheme.primary
            val sec = MaterialTheme.colorScheme.secondary
            val track = onBg().copy(alpha = 0.1f)
            Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.fillMaxWidth(0.82f).aspectRatio(1f), contentAlignment = Alignment.Center) {
                    GlassSurface(Modifier.fillMaxSize(), shape = androidx.compose.foundation.shape.CircleShape) {}
                    Canvas(Modifier.fillMaxSize().padding(14.dp)) {
                        val stroke = 16.dp.toPx()
                        drawArc(track, 0f, 360f, false, style = Stroke(stroke))
                        drawArc(
                            Brush.sweepGradient(listOf(primary, sec, primary)), -90f, 360f * progress, false,
                            style = Stroke(stroke, cap = StrokeCap.Round)
                        )
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(fmtHms(remaining), color = onBg(), fontSize = 54.sp, fontWeight = FontWeight.Light)
                        Text(if (st.running) "залишилось" else "на паузі", color = dimText())
                    }
                }
                Spacer(Modifier.height(28.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    GlassButton("Скинути", { TimerRepo.reset(ctx) }, Modifier.weight(1f), Icons.Rounded.Refresh)
                    GlassButton(
                        if (st.running) "Пауза" else "Далі",
                        { if (st.running) TimerRepo.pause(ctx) else TimerRepo.resume(ctx) },
                        Modifier.weight(1f), if (st.running) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, primary = true
                    )
                }
                Spacer(Modifier.height(12.dp))
                GlassButton("+1 хв", { TimerRepo.addMinute(ctx) }, Modifier.fillMaxWidth(0.5f), Icons.Rounded.Add)
            }
        }
    }
}

// ===================== СЕКУНДОМІР =====================

@Composable
fun StopwatchScreen() {
    val st by StopwatchRepo.state.collectAsState()
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(st.running) { while (st.running) { withFrameMillis { }; now = SystemClock.elapsedRealtime() } }
    val elapsed = st.elapsed(if (st.running) now else SystemClock.elapsedRealtime())
    val primary = MaterialTheme.colorScheme.primary
    val fg = onBg()

    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.fillMaxWidth(0.72f).aspectRatio(1f), contentAlignment = Alignment.Center) {
            GlassSurface(Modifier.fillMaxSize(), shape = androidx.compose.foundation.shape.CircleShape) {}
            Canvas(Modifier.fillMaxSize().padding(10.dp)) {
                val r = min(size.width, size.height) / 2f
                val c = Offset(size.width / 2, size.height / 2)
                for (i in 0 until 120) {
                    val big = i % 10 == 0
                    rotate(i * 3f, c) {
                        drawLine(fg.copy(alpha = if (big) 0.8f else 0.25f), Offset(c.x, c.y - r), Offset(c.x, c.y - r * (if (big) 0.88f else 0.93f)), if (big) 4f else 2f, StrokeCap.Round)
                    }
                }
                val angle = (elapsed % 60_000) / 60_000f * 360f
                rotate(angle, c) {
                    drawLine(primary, Offset(c.x, c.y + r * 0.12f), Offset(c.x, c.y - r * 0.95f), 5f, StrokeCap.Round)
                }
                drawCircle(primary, 9f, c)
                // мала шкала хвилин
                val mc = Offset(c.x, c.y + r * 0.42f)
                drawCircle(fg.copy(alpha = 0.2f), r * 0.16f, mc, style = Stroke(2f))
                rotate((elapsed % 1_800_000) / 1_800_000f * 360f, mc) {
                    drawLine(fg, mc, Offset(mc.x, mc.y - r * 0.14f), 3f, StrokeCap.Round)
                }
            }
            val cs = (elapsed / 10) % 100
            val s = (elapsed / 1000) % 60
            val m = elapsed / 60_000
            Text("%02d:%02d,%02d".format(m, s, cs), color = onBg(), fontSize = 40.sp, fontWeight = FontWeight.Light, fontFamily = FontFamily.Monospace, modifier = Modifier.padding(bottom = 40.dp))
        }
        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            GlassButton(
                if (st.running) "Коло" else "Скинути",
                { if (st.running) StopwatchRepo.lap() else StopwatchRepo.reset() },
                Modifier.weight(1f), if (st.running) Icons.Rounded.Flag else Icons.Rounded.Refresh,
                enabled = st.running || elapsed > 0
            )
            GlassButton(
                if (st.running) "Стоп" else "Старт", { StopwatchRepo.toggle() }, Modifier.weight(1f),
                if (st.running) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, primary = true,
                color = if (st.running) AlertRed else Color.Unspecified
            )
        }
        Spacer(Modifier.height(12.dp))
        val laps = st.laps
        val diffs = laps.mapIndexed { i, t -> t - (if (i == 0) 0 else laps[i - 1]) }
        val best = diffs.minOrNull(); val worst = diffs.maxOrNull()
        LazyColumn(contentPadding = PaddingValues(bottom = 110.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            itemsIndexed(diffs.reversed(), key = { i, _ -> diffs.size - i }) { i, dms ->
                val n = diffs.size - i
                val color = when {
                    diffs.size < 2 -> onBg()
                    dms == best -> AlertGreen
                    dms == worst -> AlertRed
                    else -> onBg()
                }
                GlassSurface(Modifier.fillMaxWidth().animateItem(), shape = RoundedCornerShape(18.dp)) {
                    Row(Modifier.padding(horizontal = 18.dp, vertical = 12.dp)) {
                        Text("Коло $n", color = dimText(), modifier = Modifier.weight(1f))
                        Text("+" + fmtLap(dms), color = color, fontFamily = FontFamily.Monospace)
                        Spacer(Modifier.width(16.dp))
                        Text(fmtLap(laps[n - 1]), color = onBg(), fontFamily = FontFamily.Monospace)
                    }
                }
            }
        }
    }
}

private fun fmtLap(ms: Long) = "%02d:%02d,%02d".format(ms / 60_000, (ms / 1000) % 60, (ms / 10) % 100)

// ===================== СВІТОВИЙ ЧАС =====================

val CITIES = listOf(
    "Europe/Kyiv" to "Київ", "Europe/Warsaw" to "Варшава", "Europe/London" to "Лондон", "Europe/Berlin" to "Берлін",
    "Europe/Paris" to "Париж", "Europe/Rome" to "Рим", "Europe/Madrid" to "Мадрид", "Europe/Prague" to "Прага",
    "Europe/Vienna" to "Відень", "Europe/Lisbon" to "Лісабон", "Europe/Istanbul" to "Стамбул", "Europe/Chisinau" to "Кишинів",
    "Europe/Vilnius" to "Вільнюс", "Asia/Tbilisi" to "Тбілісі", "Asia/Dubai" to "Дубай", "Asia/Kolkata" to "Делі",
    "Asia/Shanghai" to "Пекін", "Asia/Tokyo" to "Токіо", "Asia/Seoul" to "Сеул", "Australia/Sydney" to "Сідней",
    "America/New_York" to "Нью-Йорк", "America/Toronto" to "Торонто", "America/Chicago" to "Чикаго",
    "America/Los_Angeles" to "Лос-Анджелес", "America/Sao_Paulo" to "Сан-Паулу", "America/Mexico_City" to "Мехіко",
    "Africa/Cairo" to "Каїр", "Pacific/Honolulu" to "Гонолулу",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorldClockScreen(settings: AppSettings) {
    val ctx = LocalContext.current
    var adding by remember { mutableStateOf(false) }
    var now by remember { mutableStateOf(ZonedDateTime.now()) }
    LaunchedEffect(Unit) { while (true) { now = ZonedDateTime.now(); delay(1000) } }
    val local = ZoneId.systemDefault()

    LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 120.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                AnalogClock(Modifier.size(230.dp))
                Spacer(Modifier.height(12.dp))
                Text(now.format(DateTimeFormatter.ofPattern("HH:mm:ss")), color = onBg(), fontSize = 40.sp, fontWeight = FontWeight.Light)
                Text(now.format(DateTimeFormatter.ofPattern("EEEE, d MMMM", UK)).replaceFirstChar { it.uppercase() }, color = dimText())
            }
        }
        items(settings.worldZones, key = { it }) { id ->
            val z = safeZone(id)
            val t = ZonedDateTime.now(z).withSecond(now.second)
            val diffH = Duration.ofSeconds((t.offset.totalSeconds - now.offset.totalSeconds).toLong()).toMinutes() / 60f
            val day = when {
                t.toLocalDate().isAfter(now.toLocalDate()) -> "Завтра"
                t.toLocalDate().isBefore(now.toLocalDate()) -> "Вчора"
                else -> "Сьогодні"
            }
            val diffText = if (diffH == 0f) "як тут" else (if (diffH > 0) "+" else "") + (if (diffH % 1f == 0f) diffH.toInt().toString() else "%.1f".format(diffH)) + " год"
            GlassSurface(Modifier.fillMaxWidth().animateItem()) {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    AnalogClock(Modifier.size(48.dp), z, showSeconds = false)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(CITIES.firstOrNull { it.first == id }?.second ?: id.substringAfter('/'), color = onBg(), fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                        Text("$day, $diffText", color = dimText(), fontSize = 13.sp)
                    }
                    Text(t.format(DateTimeFormatter.ofPattern("HH:mm")), color = onBg(), fontSize = 34.sp, fontWeight = FontWeight.Light)
                    Icon(Icons.Rounded.Close, "Прибрати", tint = dimText(), modifier = Modifier.padding(start = 8.dp).size(20.dp).clickable {
                        SettingsRepo.update(ctx) { s -> s.copy(worldZones = s.worldZones - id) }
                    })
                }
            }
        }
        item {
            GlassButton("Додати місто", { adding = true }, Modifier.fillMaxWidth(), Icons.Rounded.Add)
        }
    }

    if (adding) {
        ModalBottomSheet(onDismissRequest = { adding = false }, sheetState = rememberModalBottomSheetState(true), containerColor = sheetColor()) {
            LazyColumn(Modifier.height(520.dp).padding(horizontal = 16.dp)) {
                items(CITIES.filter { it.first !in settings.worldZones }) { (id, name) ->
                    val t = ZonedDateTime.now(safeZone(id))
                    Row(
                        Modifier.fillMaxWidth().clickable {
                            SettingsRepo.update(ctx) { s -> s.copy(worldZones = s.worldZones + id) }; adding = false
                        }.padding(vertical = 14.dp, horizontal = 8.dp)
                    ) {
                        Text(name, color = onBg(), fontSize = 17.sp, modifier = Modifier.weight(1f))
                        Text(t.format(DateTimeFormatter.ofPattern("HH:mm")), color = dimText(), fontSize = 17.sp)
                    }
                }
            }
        }
    }
}

@Suppress("unused")
private val unusedSize = Size.Zero
