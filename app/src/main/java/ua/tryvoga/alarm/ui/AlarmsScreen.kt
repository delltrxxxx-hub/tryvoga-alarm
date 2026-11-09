package ua.tryvoga.alarm.ui

import android.app.Activity
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.border
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.drawText
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AlarmOn
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.automirrored.rounded.Label
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Snooze
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.IntentCompat
import ua.tryvoga.alarm.Alarm
import ua.tryvoga.alarm.AlarmRepo
import ua.tryvoga.alarm.DAY_SHORT
import ua.tryvoga.alarm.formatCountdown

@Composable
fun AlarmsScreen(alarms: List<Alarm>, level: ua.tryvoga.alarm.AlertLevel = ua.tryvoga.alarm.AlertLevel.UNKNOWN, onRingCollapsed: (Boolean) -> Unit = {}) {
    val ctx = LocalContext.current
    var editing by remember { mutableStateOf<Alarm?>(null) }
    var isNew by remember { mutableStateOf(false) }
    val glass = isGlass()
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    val density = androidx.compose.ui.platform.LocalDensity.current
    val collapsed by remember {
        androidx.compose.runtime.derivedStateOf {
            listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > with(density) { 24.dp.toPx() }
        }
    }
    androidx.compose.runtime.LaunchedEffect(collapsed, glass) { onRingCollapsed(glass && collapsed) }
    androidx.compose.runtime.DisposableEffect(Unit) { onDispose { onRingCollapsed(false) } }
    val openNew = { editing = Alarm(id = AlarmRepo.newId(), hour = 7, minute = 0); isNew = true }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 190.dp),
            verticalArrangement = Arrangement.spacedBy(if (glass) 0.dp else 12.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            if (glass) item(key = "ring") {
                WakeRing(alarms, level, Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 20.dp))
            }
            if (alarms.isEmpty()) item {
                if (glass) {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Будильників немає", color = onBg(), fontSize = 17.sp, fontWeight = FontWeight.Medium)
                        Text(
                            "Додайте перший — і вкажіть, як йому поводитися під час тривоги.",
                            color = dimText(), fontSize = 14.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                } else GlassSurface(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Rounded.AlarmOn, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(48.dp))
                        Spacer(Modifier.height(8.dp))
                        Text("Будильників ще немає", color = onBg(), fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                        Text("Натисніть «+», щоб додати. Кожен будильник можна прив'язати до режиму тривоги.", color = dimText(), fontSize = 14.sp)
                    }
                }
            }
            itemsIndexed(alarms, key = { _, a -> a.id }) { i, a ->
                val onToggle = { on: Boolean ->
                    AlarmRepo.upsert(ctx, a.copy(enabled = on))
                    if (on) Toast.makeText(ctx, "Задзвонить через ${formatCountdown(a.nextTriggerMillis() - System.currentTimeMillis())}", Toast.LENGTH_SHORT).show()
                }
                if (glass) AlarmRow(a, i, alarms.size, Modifier.animateItem(), onToggle) { editing = a; isNew = false }
                else AlarmCard(a, Modifier.animateItem(), onToggle = onToggle) { editing = a; isNew = false }
            }
        }
        if (glass) {
            // «+»: один стиль — контурне коло 1.5dp, як лінії циферблата
            val interaction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
            Box(
                Modifier.align(Alignment.BottomEnd).padding(end = 20.dp, bottom = 100.dp).size(56.dp).clip(CircleShape)
                    .background(MaterialTheme.colorScheme.background)
                    .border(1.5.dp, onBg().copy(alpha = 0.6f), CircleShape)
                    .clickable(interaction, androidx.compose.foundation.LocalIndication.current) { openNew() },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.Add, "Новий будильник", tint = onBg(), modifier = Modifier.size(26.dp))
            }
        } else GlassSurface(
            Modifier.align(Alignment.BottomEnd).padding(end = 22.dp, bottom = 104.dp).size(64.dp),
            shape = CircleShape, tint = MaterialTheme.colorScheme.primary,
            onClick = openNew
        ) {
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Add, "Додати", tint = Color.White, modifier = Modifier.size(32.dp))
            }
        }
    }

    editing?.let { a ->
        AlarmEditorSheet(
            a, isNew,
            onDismiss = { editing = null },
            onSave = {
                val saved = it.copy(enabled = true)
                AlarmRepo.upsert(ctx, saved)
                Toast.makeText(ctx, "Задзвонить через ${formatCountdown(saved.nextTriggerMillis() - System.currentTimeMillis())}", Toast.LENGTH_SHORT).show()
                editing = null
            },
            onDelete = { AlarmRepo.delete(ctx, a.id); editing = null }
        )
    }
}

/**
 * Кільце «до пробудження» — головне на екрані. Циферблат на 24 години: дуга від «зараз»
 * до наступного будильника, у центрі — скільки лишилось. Дуга «доростає» за 300 мс.
 */
@Composable
private fun WakeRing(alarms: List<Alarm>, level: ua.tryvoga.alarm.AlertLevel, modifier: Modifier) {
    val now by androidx.compose.runtime.produceState(System.currentTimeMillis()) {
        while (true) { value = System.currentTimeMillis(); kotlinx.coroutines.delay(1000) }
    }
    val next = ua.tryvoga.alarm.AlarmScheduler.nextAlarm(alarms, now)
    val remaining = next?.let { it.first - now } ?: 0L
    val zone = java.time.ZoneId.systemDefault()
    val nowT = java.time.Instant.ofEpochMilli(now).atZone(zone)
    val startDeg = ((nowT.hour * 60 + nowT.minute) / 1440f) * 360f
    // мінімум 6°, щоб коротка дуга не виглядала як збій
    val targetSweep = if (next != null) ((remaining / 86_400_000f) * 360f).coerceIn(6f, 360f) else 0f
    val sweep = remember { androidx.compose.animation.core.Animatable(0f) }
    var growing by remember { mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect((targetSweep / 2f).toInt()) {
        // крок дуги з тіком на кожні ~2°; великі зміни (новий будильник) — плавно 300 мс
        if (kotlin.math.abs(sweep.value - targetSweep) > 3f) {
            growing = true
            sweep.animateTo(targetSweep, androidx.compose.animation.core.tween(300, easing = androidx.compose.animation.core.FastOutSlowInEasing))
            growing = false
        } else sweep.snapTo(targetSweep)
    }
    val fg = onBg()
    val track = onBg().copy(alpha = 0.20f)
    val tick = onBg().copy(alpha = 0.38f)
    val h = remaining / 3_600_000
    val m = (remaining / 60_000) % 60
    val desc = if (next != null) "До пробудження $h ${ua.tryvoga.alarm.plural(h.toInt(), "година", "години", "годин")} $m ${ua.tryvoga.alarm.plural(m.toInt(), "хвилина", "хвилини", "хвилин")}"
    else if (alarms.isEmpty()) "Будильників немає" else "Усі будильники вимкнені"

    val measurer = androidx.compose.ui.text.rememberTextMeasurer()
    val labelStyle = androidx.compose.ui.text.TextStyle(color = onBg().copy(alpha = 0.5f), fontSize = 11.sp)
    Box(modifier.semantics(mergeDescendants = true) { contentDescription = desc }, contentAlignment = Alignment.Center) {
        androidx.compose.foundation.Canvas(Modifier.size(196.dp)) {
            // підписи годин 0 / 6 / 12 / 18 — щоб циферблат читався як доба
            val rl = size.minDimension / 2 - 2.dp.toPx()
            val cc = androidx.compose.ui.geometry.Offset(size.width / 2, size.height / 2)
            listOf(0 to "0", 6 to "6", 12 to "12", 18 to "18").forEach { (hr, s) ->
                val lay = measurer.measure(s, labelStyle)
                val ang = Math.toRadians((hr * 15f - 90f).toDouble())
                val px = cc.x + (rl - lay.size.width / 2f) * kotlin.math.cos(ang).toFloat() - lay.size.width / 2f
                val py = cc.y + (rl - lay.size.height / 2f) * kotlin.math.sin(ang).toFloat() - lay.size.height / 2f
                drawText(lay, topLeft = androidx.compose.ui.geometry.Offset(px, py))
            }
        }
        androidx.compose.foundation.Canvas(Modifier.size(168.dp)) {
            val stroke = 4.dp.toPx()
            val r = size.minDimension / 2 - stroke
            val c = androidx.compose.ui.geometry.Offset(size.width / 2, size.height / 2)
            // години: 24 риски, кожна шоста — довша (0, 6, 12, 18)
            for (i in 0 until 24) {
                val major = i % 6 == 0
                rotate(i * 15f, c) {
                    drawLine(tick, androidx.compose.ui.geometry.Offset(c.x, c.y - r + stroke * 1.6f),
                        androidx.compose.ui.geometry.Offset(c.x, c.y - r + stroke * (if (major) 3.6f else 2.4f)),
                        strokeWidth = if (major) 1.5.dp.toPx() else 1.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round)
                }
            }
            drawCircle(track, r, c, style = androidx.compose.ui.graphics.drawscope.Stroke(1.5.dp.toPx()))
            if (sweep.value > 0f) {
                drawArc(fg, startDeg - 90f, sweep.value, false,
                    topLeft = androidx.compose.ui.geometry.Offset(c.x - r, c.y - r),
                    size = androidx.compose.ui.geometry.Size(r * 2, r * 2),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(stroke, cap = androidx.compose.ui.graphics.StrokeCap.Round))
                // мітка «зараз» — маленька точка на початку дуги
                val a = Math.toRadians((startDeg - 90f).toDouble())
                drawCircle(fg, 3.dp.toPx(), androidx.compose.ui.geometry.Offset(c.x + r * kotlin.math.cos(a).toFloat(), c.y + r * kotlin.math.sin(a).toFloat()))
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (level.isAlert || level == ua.tryvoga.alarm.AlertLevel.UNKNOWN) StatusDot(level, 8.dp, paused = growing)
            Text(
                if (next != null) "%d:%02d".format(h, m) else "—",
                color = fg, fontSize = 48.sp, fontWeight = FontWeight.Light,
                style = androidx.compose.ui.text.TextStyle(fontFeatureSettings = "tnum")
            )
            Text(
                when {
                    next != null -> "до " + java.time.Instant.ofEpochMilli(next.first).atZone(zone)
                        .format(java.time.format.DateTimeFormatter.ofPattern("HH:mm · EEE", ua.tryvoga.alarm.UK))
                    alarms.isEmpty() -> ""
                    else -> "усі вимкнені"
                },
                color = dimText(), fontSize = 13.sp
            )
        }
    }
}

/** Рядок будильника на спільній скляній панелі (перший/останній — із заокругленням). */
@Composable
private fun AlarmRow(a: Alarm, index: Int, count: Int, modifier: Modifier, onToggle: (Boolean) -> Unit, onClick: () -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    val alpha by animateFloatAsState(if (a.enabled) 1f else 0.45f, androidx.compose.animation.core.tween(180), label = "a")
    Column(modifier) {
        GlassSurface(Modifier.fillMaxWidth(), shape = shape, onClick = onClick) {
            Row(Modifier.padding(start = 18.dp, end = 12.dp, top = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).graphicsLayer { this.alpha = alpha }) {
                    Text(
                        a.timeText, color = onBg(), fontSize = 40.sp, fontWeight = FontWeight.Light,
                        style = androidx.compose.ui.text.TextStyle(fontFeatureSettings = "tnum")
                    )
                    Text(listOf(a.label, a.daysText()).filter { it.isNotBlank() }.joinToString(" · "), color = dimText(), fontSize = 14.sp)
                    if (a.alertAware) {
                        fun v(vol: Int, vib: Boolean) = if (vol == 0) (if (vib) "вібро" else "тиша") else "$vol%"
                        Text(
                            "Тривога: червона — ${v(a.redVolume, a.redVibrate)}, жовта — ${v(a.yellowVolume, a.yellowVibrate)}",
                            color = dimText(), fontSize = 13.sp, modifier = Modifier.padding(top = 2.dp)
                        )
                        if (a.wakeAfterAllClear) Text("Розбудить після відбою", color = dimText(), fontSize = 13.sp)
                    }
                }
                GlassSwitch(a.enabled, onToggle)
            }
        }
        if (index < count - 1) Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun AlarmCard(a: Alarm, modifier: Modifier, onToggle: (Boolean) -> Unit, onClick: () -> Unit) {
    val alpha by animateFloatAsState(if (a.enabled) 1f else 0.5f, soft(), label = "a")
    GlassSurface(modifier.fillMaxWidth(), onClick = onClick) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 14.dp).graphicsLayer { this.alpha = alpha }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(a.timeText, color = onBg(), fontSize = 52.sp, fontWeight = FontWeight.Light)
                    Text(
                        listOf(a.label, a.daysText()).filter { it.isNotBlank() }.joinToString(" · "),
                        color = dimText(), fontSize = 14.sp
                    )
                }
                GlassSwitch(a.enabled, onToggle)
            }
            if (a.alertAware) {
                Spacer(Modifier.height(10.dp))
                @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
                androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    MiniChip(AlertRed, if (a.redVolume == 0) (if (a.redVibrate) "Вібро" else "Без звуку") else "${a.redVolume}%")
                    MiniChip(AlertYellow, if (a.yellowVolume == 0) (if (a.yellowVibrate) "Вібро" else "Без звуку") else "${a.yellowVolume}%")
                    if (a.wakeAfterAllClear) MiniChip(AlertGreen, "Після відбою")
                    if (a.announce) MiniChip(MaterialTheme.colorScheme.primary, "Голос")
                }
            }
        }
    }
}

@Composable
private fun MiniChip(color: Color, text: String) {
    Row(
        Modifier.clip(RoundedCornerShape(10.dp)).background(color.copy(alpha = 0.18f)).padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(5.dp))
        Text(text, color = onBg(), fontSize = 11.sp, fontWeight = FontWeight.Medium)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlarmEditorSheet(alarm: Alarm, isNew: Boolean, onDismiss: () -> Unit, onSave: (Alarm) -> Unit, onDelete: () -> Unit) {
    val ctx = LocalContext.current
    var d by remember { mutableStateOf(alarm) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        if (res.resultCode == Activity.RESULT_OK) {
            val uri = res.data?.let { IntentCompat.getParcelableExtra(it, RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java) }
            d = d.copy(ringtoneUri = uri?.toString())
        }
    }
    val ringtoneName = remember(d.ringtoneUri) {
        runCatching {
            val u = d.ringtoneUri?.let { Uri.parse(it) } ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            val t = RingtoneManager.getRingtone(ctx, u)?.getTitle(ctx)
            if (d.ringtoneUri == null) "Стандартна" + (t?.substringAfter('(', "")?.substringBefore(')')?.takeIf { it.isNotBlank() }?.let { " ($it)" } ?: "") else t
        }.getOrNull() ?: "Стандартна"
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = sheetColor()
    ) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).navigationBarsPadding()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onDismiss) { Text("Скасувати") }
                Text(if (isNew) "Новий будильник" else "Будильник", Modifier.weight(1f), color = onBg(), fontWeight = FontWeight.SemiBold, fontSize = 17.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                TextButton(onClick = { onSave(d) }) { Text("Зберегти", fontWeight = FontWeight.Bold) }
            }

            // --- час свайпом ---
            GlassSurface(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp, horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically) {
                    WheelPicker(24, d.hour, { d = d.copy(hour = it) }, Modifier.weight(1f), fontSize = 52.sp, itemHeight = 64.dp)
                    Text(":", color = onBg(), fontSize = 52.sp, fontWeight = FontWeight.Light)
                    WheelPicker(60, d.minute, { d = d.copy(minute = it) }, Modifier.weight(1f), fontSize = 52.sp, itemHeight = 64.dp)
                }
            }
            Text(
                "Задзвонить через ${formatCountdown(d.nextTriggerMillis() - System.currentTimeMillis())}",
                Modifier.fillMaxWidth().padding(top = 8.dp), color = dimText(), fontSize = 13.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )

            // --- дні тижня ---
            SectionTitle("Повторювати")
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                (1..7).forEach { day ->
                    val sel = day in d.days
                    val bg by animateColorAsState(if (sel) MaterialTheme.colorScheme.primary else onBg().copy(alpha = 0.08f), label = "day")
                    val sc by animateFloatAsState(if (sel) 1.1f else 1f, bouncy(), label = "ds")
                    Box(
                        Modifier.size(42.dp).graphicsLayer { scaleX = sc; scaleY = sc }.clip(CircleShape).background(bg)
                            .clickable { d = d.copy(days = if (sel) d.days - day else d.days + day) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(DAY_SHORT[day - 1], color = if (sel) MaterialTheme.colorScheme.onPrimary else onBg(), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            ChoicePills(
                listOf("Одноразово" to emptySet(), "Будні" to setOf(1, 2, 3, 4, 5), "Вихідні" to setOf(6, 7), "Щодня" to (1..7).toSet()),
                listOf("Одноразово" to emptySet(), "Будні" to setOf(1, 2, 3, 4, 5), "Вихідні" to setOf(6, 7), "Щодня" to (1..7).toSet()).firstOrNull { it.second == d.days } ?: ("" to emptySet<Int>()),
                { it.first }, { d = d.copy(days = it.second) }
            )

            SectionTitle("Назва")
            OutlinedTextField(
                d.label, { d = d.copy(label = it) }, Modifier.fillMaxWidth(),
                placeholder = { Text("Наприклад: На роботу") }, singleLine = true,
                leadingIcon = { Icon(Icons.AutoMirrored.Rounded.Label, null) }, shape = RoundedCornerShape(18.dp)
            )

            // --- звук ---
            SectionTitle("Звук")
            GlassSurface(Modifier.fillMaxWidth()) {
                Column {
                    SettingRow(Icons.Rounded.MusicNote, "Мелодія", ringtoneName, onClick = {
                        val i = Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
                            .putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
                            .putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "Мелодія будильника")
                            .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                            .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                            .putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, d.ringtoneUri?.let { Uri.parse(it) })
                        runCatching { picker.launch(i) }
                    })
                    Divider()
                    VolumeRow(Icons.AutoMirrored.Rounded.VolumeUp, "Гучність без тривоги", d.volume, MaterialTheme.colorScheme.primary, allowZero = false) { d = d.copy(volume = it) }
                    Divider()
                    SettingRow(Icons.Rounded.GraphicEq, "Плавне наростання", "Звук поступово гучнішає") { GlassSwitch(d.gradual, { d = d.copy(gradual = it) }) }
                    Divider()
                    SettingRow(Icons.Rounded.Vibration, "Вібрація") { GlassSwitch(d.vibrate, { d = d.copy(vibrate = it) }) }
                    Divider()
                    SettingRow(Icons.Rounded.RecordVoiceOver, "Голосове оголошення", "Сигнал, потім голос: час і яка зараз тривога") {
                        GlassSwitch(d.announce, { d = d.copy(announce = it) })
                    }
                    Divider()
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Snooze, null, tint = if (isGlass()) dimText() else MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(10.dp))
                            Text("Відкласти на", color = onBg(), fontSize = 16.sp)
                        }
                        Spacer(Modifier.height(8.dp))
                        ChoicePills(listOf(5, 10, 15, 20, 30), d.snoozeMin, { "$it хв" }, { d = d.copy(snoozeMin = it) })
                    }
                }
            }

            // --- режим тривоги ---
            SectionTitle("Режим тривоги")
            GlassSurface(Modifier.fillMaxWidth(), tint = if (d.alertAware && !isGlass()) AlertRed else Color.Unspecified) {
                Column {
                    SettingRow(Icons.Rounded.Shield, "Враховувати повітряну тривогу", "Тихіше або без звуку під час тривоги на вашій території", iconTint = AlertRed) {
                        GlassSwitch(d.alertAware, { d = d.copy(alertAware = it) }, AlertRed)
                    }
                    AnimatedVisibility(d.alertAware, enter = expandVertically(bouncy()) + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                        Column {
                            Divider()
                            LevelBlock("Червона тривога", "ракетна небезпека", AlertRed, d.redVolume, d.redVibrate,
                                { d = d.copy(redVolume = it) }, { d = d.copy(redVibrate = it) })
                            Divider()
                            LevelBlock("Жовта тривога", "загроза БпЛА / часткова", AlertYellow, d.yellowVolume, d.yellowVibrate,
                                { d = d.copy(yellowVolume = it) }, { d = d.copy(yellowVibrate = it) })
                            Divider()
                            SettingRow(Icons.Rounded.NotificationsActive, "Розбудити після відбою", "Якщо будильник промовчав через тривогу — задзвонить після відбою", iconTint = AlertGreen) {
                                GlassSwitch(d.wakeAfterAllClear, { d = d.copy(wakeAfterAllClear = it) }, AlertGreen)
                            }
                            AnimatedVisibility(d.wakeAfterAllClear, enter = expandVertically(bouncy()) + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                                Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
                                    Text("Через скільки після відбою", color = dimText(), fontSize = 13.sp)
                                    Spacer(Modifier.height(6.dp))
                                    ChoicePills(listOf(0, 5, 10, 15, 30), d.allClearDelayMin, { if (it == 0) "Одразу" else "$it хв" }, { d = d.copy(allClearDelayMin = it) }, AlertGreen)
                                    Spacer(Modifier.height(12.dp))
                                    Text("Чекати на відбій не довше ніж", color = dimText(), fontSize = 13.sp)
                                    Spacer(Modifier.height(6.dp))
                                    ChoicePills(listOf(60, 120, 180, 300, 480), d.maxWaitMin, { "${it / 60} год" }, { d = d.copy(maxWaitMin = it) }, AlertGreen)
                                }
                            }
                        }
                    }
                }
            }

            if (!isNew) {
                Spacer(Modifier.height(20.dp))
                GlassButton("Видалити будильник", onDelete, Modifier.fillMaxWidth(), Icons.Rounded.Delete, primary = true, color = AlertRed)
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun Divider() = HorizontalDivider(Modifier.padding(start = if (isGlass()) 54.dp else 66.dp), color = onBg().copy(alpha = 0.08f))

@Composable
private fun VolumeRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, value: Int, color: Color, allowZero: Boolean = true, onChange: (Int) -> Unit) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = if (isGlass() && color == MaterialTheme.colorScheme.primary) dimText() else color)
            Spacer(Modifier.width(10.dp))
            Text(title, color = onBg(), fontSize = 16.sp, modifier = Modifier.weight(1f))
            Text(if (value == 0) "Без звуку" else "$value%", color = color, fontWeight = FontWeight.SemiBold)
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { onChange(((it / 10).toInt() * 10).coerceAtLeast(if (allowZero) 0 else 10)) },
            valueRange = 0f..100f, steps = 9,
            colors = SliderDefaults.colors(thumbColor = color, activeTrackColor = color, inactiveTrackColor = color.copy(alpha = 0.2f), activeTickColor = Color.White.copy(alpha = 0.7f), inactiveTickColor = color.copy(alpha = 0.6f))
        )
    }
}

@Composable
private fun LevelBlock(title: String, sub: String, color: Color, volume: Int, vibrate: Boolean, onVolume: (Int) -> Unit, onVibrate: (Boolean) -> Unit) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(12.dp).clip(CircleShape).background(color))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(title, color = onBg(), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Text(sub, color = dimText(), fontSize = 12.sp)
            }
            Text(if (volume == 0) "Без звуку" else "$volume%", color = color, fontWeight = FontWeight.SemiBold)
        }
        Slider(
            value = volume.toFloat(),
            onValueChange = { onVolume((it / 10).toInt() * 10) },
            valueRange = 0f..100f, steps = 9,
            colors = SliderDefaults.colors(thumbColor = color, activeTrackColor = color, inactiveTrackColor = color.copy(alpha = 0.2f), activeTickColor = Color.White.copy(alpha = 0.7f), inactiveTickColor = color.copy(alpha = 0.6f))
        )
        ChoicePills(listOf(0, 20, 50, 100), volume, { if (it == 0) "Тихо" else "$it%" }, onVolume, color)
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Vibration, null, tint = if (!isGlass() || vibrate) color else AlertGray, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Text("Вібрація", color = onBg(), modifier = Modifier.weight(1f))
            GlassSwitch(vibrate, onVibrate, color)
        }
    }
}
