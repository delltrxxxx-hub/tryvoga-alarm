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
fun AlarmsScreen(alarms: List<Alarm>) {
    val ctx = LocalContext.current
    var editing by remember { mutableStateOf<Alarm?>(null) }
    var isNew by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 190.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            if (alarms.isEmpty()) item {
                GlassSurface(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Rounded.AlarmOn, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(48.dp))
                        Spacer(Modifier.height(8.dp))
                        Text("Будильників ще немає", color = onBg(), fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                        Text("Натисніть «+», щоб додати. Кожен будильник можна прив'язати до режиму тривоги.", color = dimText(), fontSize = 14.sp)
                    }
                }
            }
            items(alarms, key = { it.id }) { a ->
                AlarmCard(a, Modifier.animateItem(), onToggle = { on ->
                    AlarmRepo.upsert(ctx, a.copy(enabled = on))
                    if (on) Toast.makeText(ctx, "Задзвонить через ${formatCountdown(a.nextTriggerMillis() - System.currentTimeMillis())}", Toast.LENGTH_SHORT).show()
                }) { editing = a; isNew = false }
            }
        }
        GlassSurface(
            Modifier.align(Alignment.BottomEnd).padding(end = 22.dp, bottom = 104.dp).size(64.dp),
            shape = CircleShape, tint = MaterialTheme.colorScheme.primary,
            onClick = { editing = Alarm(id = AlarmRepo.newId(), hour = 7, minute = 0); isNew = true }
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
                            Icon(Icons.Rounded.Snooze, null, tint = MaterialTheme.colorScheme.primary)
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
            GlassSurface(Modifier.fillMaxWidth(), tint = if (d.alertAware) AlertRed else Color.Unspecified) {
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
private fun Divider() = HorizontalDivider(Modifier.padding(start = 66.dp), color = onBg().copy(alpha = 0.08f))

@Composable
private fun VolumeRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, value: Int, color: Color, allowZero: Boolean = true, onChange: (Int) -> Unit) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = color)
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
            Icon(Icons.Rounded.Vibration, null, tint = color, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Text("Вібрація", color = onBg(), modifier = Modifier.weight(1f))
            GlassSwitch(vibrate, onVibrate, color)
        }
    }
}
