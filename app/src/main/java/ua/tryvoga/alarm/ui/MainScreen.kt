package ua.tryvoga.alarm.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.HourglassBottom
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import ua.tryvoga.alarm.Alarm
import ua.tryvoga.alarm.AlarmScheduler
import ua.tryvoga.alarm.AlertLevel
import ua.tryvoga.alarm.Loc
import ua.tryvoga.alarm.LocStatus
import ua.tryvoga.alarm.Locations
import ua.tryvoga.alarm.SettingsRepo
import ua.tryvoga.alarm.Snapshot
import ua.tryvoga.alarm.UiStyle
import ua.tryvoga.alarm.formatCountdown

enum class Tab(val label: String, val icon: ImageVector) {
    ALARMS("Будильник", Icons.Rounded.Alarm),
    CLOCK("Годинник", Icons.Rounded.Public),
    TIMER("Таймер", Icons.Rounded.HourglassBottom),
    STOPWATCH("Секундомір", Icons.Rounded.Timer),
    ALERTS("Тривога", Icons.Rounded.Map),
    SETTINGS("Налаштування", Icons.Rounded.Settings),
}

@Composable
fun MainScreen(vm: MainViewModel) {
    val settings by vm.settings.collectAsState()
    val alarms by vm.alarms.collectAsState()
    val snap by vm.snapshot.collectAsState()
    var tab by rememberSaveable { mutableStateOf(Tab.ALARMS) }
    var showLoc by remember { mutableStateOf(false) }
    val loc = Locations.getOrDefault(settings.locationId)
    val status = snap?.statusOf(loc, settings.partialAs) ?: LocStatus.UNKNOWN
    val ctx = LocalContext.current
    val glass = isGlass()
    // кільце «до пробудження» на вкладці будильників згортається при прокручуванні
    var ringCollapsed by remember { mutableStateOf(false) }
    val showCountdownInHeader = !glass || tab != Tab.ALARMS || ringCollapsed

    AppBackground(status.shownLevel) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Header(
                loc, status, alarms, showCountdownInHeader,
                settingsSelected = tab == Tab.SETTINGS,
                onSettings = { tab = if (tab == Tab.SETTINGS) Tab.ALARMS else Tab.SETTINGS },
            ) { showLoc = true }
            Box(Modifier.weight(1f)) {
                AnimatedContent(
                    targetState = tab,
                    transitionSpec = {
                        if (glass) fadeIn(tween(160)) togetherWith fadeOut(tween(120))
                        else {
                            val dir = if (targetState.ordinal > initialState.ordinal) 1 else -1
                            (slideInHorizontally(spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow)) { it / 3 * dir } + fadeIn(tween(220))) togetherWith
                                (slideOutHorizontally(spring(dampingRatio = 0.9f, stiffness = Spring.StiffnessMedium)) { -it / 3 * dir } + fadeOut(tween(150)))
                        }
                    },
                    label = "tabs"
                ) { t ->
                    when (t) {
                        Tab.ALARMS -> AlarmsScreen(alarms, status.shownLevel) { ringCollapsed = it }
                        Tab.CLOCK -> WorldClockScreen(settings)
                        Tab.TIMER -> TimerScreen()
                        Tab.STOPWATCH -> StopwatchScreen()
                        Tab.ALERTS -> AlertsScreen(vm, settings, snap)
                        Tab.SETTINGS -> SettingsScreen(vm, settings) { showLoc = true }
                    }
                }
            }
        }
        if (glass) {
            // м'яке затухання вмісту під нижньою панеллю
            Box(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(150.dp)
                    .background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color.Transparent, MaterialTheme.colorScheme.background)))
            )
        }
        BottomNav(tab, { tab = it }, Modifier.align(Alignment.BottomCenter))
    }

    // Перевірка оновлень при запуску (можна вимкнути в Налаштуваннях).
    androidx.compose.runtime.LaunchedEffect(Unit) {
        if (SettingsRepo.settings.value.autoUpdate) ua.tryvoga.alarm.update.Updater.check(silent = true)
    }
    // Спершу — обов'язкові дозволи надійності, потім (якщо є) пропозиція оновлення.
    val gateShown = PermissionsGate()
    if (!gateShown) UpdateDialogHost()

    if (showLoc) {
        LocationSheet(loc, snap, settings.partialAs, onDismiss = { showLoc = false }) { l ->
            SettingsRepo.update(ctx) { it.copy(locationId = l.id) }
            showLoc = false
            vm.refreshNow()
        }
    }
}

@Composable
private fun Header(
    loc: Loc, status: LocStatus, alarms: List<Alarm>, showCountdown: Boolean,
    settingsSelected: Boolean = false, onSettings: () -> Unit = {}, onLoc: () -> Unit,
) {
    val now by produceState(System.currentTimeMillis()) {
        while (true) { value = System.currentTimeMillis(); delay(1000) }
    }
    val next = AlarmScheduler.nextAlarm(alarms, now)
    val shown = status.shownLevel
    if (isGlass()) {
      Box(Modifier.fillMaxWidth()) {
        // налаштування — шестерня праворуч угорі (у нижній панелі лишаються 5 вкладок)
        IconButton(onClick = onSettings, modifier = Modifier.align(Alignment.TopEnd).padding(top = 4.dp, end = 8.dp)) {
            Icon(Icons.Rounded.Settings, "Налаштування", tint = if (settingsSelected) onBg() else onBg().copy(alpha = 0.62f))
        }
        Column(Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 8.dp, start = 56.dp, end = 56.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            // територія: тонкий контур, без заливки
            val shape = RoundedCornerShape(12.dp)
            Row(
                Modifier.widthIn(max = 340.dp).clip(shape).border(1.dp, onBg().copy(alpha = 0.22f), shape)
                    .clickable(onClick = onLoc).padding(start = 12.dp, end = 8.dp, top = 7.dp, bottom = 7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Rounded.Place, null, tint = dimText(), modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(loc.short, color = onBg(), fontWeight = FontWeight.Medium, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                Icon(Icons.Rounded.KeyboardArrowDown, null, tint = dimText(), modifier = Modifier.size(18.dp))
            }
            // статус: крапка + слово кольором статусу, 15sp — головний сигнал екрана
            Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                StatusDot(shown, 9.dp)
                val c by animateColorAsState(shown.textColor(), tween(600), label = "sc")
                Text(status.title, color = c, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            }
            status.detail?.let { Text(it, color = dimText(), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center) }
            androidx.compose.animation.AnimatedVisibility(showCountdown, enter = fadeIn(tween(180)), exit = fadeOut(tween(120))) {
                Text(
                    if (next != null) "До пробудження ${formatCountdown(next.first - now)}" else "Немає активних будильників",
                    color = dimText(), fontSize = 13.sp, modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
      }
        return
    }
    Column(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 6.dp, start = 16.dp, end = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        GlassSurface(Modifier.widthIn(max = 340.dp), shape = RoundedCornerShape(22.dp), tint = if (shown.isAlert) shown.color() else Color.Unspecified, onClick = onLoc) {
            Row(Modifier.padding(start = 6.dp, end = 10.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                StatusDot(shown, 8.dp)
                Icon(Icons.Rounded.Place, null, tint = onBg(), modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text(loc.short, color = onBg(), fontWeight = FontWeight.SemiBold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                Icon(Icons.Rounded.KeyboardArrowDown, null, tint = dimText(), modifier = Modifier.size(18.dp))
            }
        }
        val statusColor by animateColorAsState(if (shown == AlertLevel.UNKNOWN) dimText() else shown.color(), label = "sc")
        Text(status.title, color = statusColor, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 4.dp))
        status.detail?.let { Text(it, color = dimText(), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center) }
        Text(
            if (next != null) "До пробудження: ${formatCountdown(next.first - now)}" else "Немає активних будильників",
            color = dimText(), fontSize = 13.sp
        )
    }
}

@Composable
private fun BottomNav(tab: Tab, onTab: (Tab) -> Unit, modifier: Modifier) {
    if (isGlass()) {
        val items = Tab.entries.filter { it != Tab.SETTINGS }
        val selIndex = items.indexOf(tab)
        // суцільна скляна панель; вибрана вкладка — біла іконка, білий підпис і лінія 24×3dp зверху
        GlassSurface(
            modifier.navigationBarsPadding().padding(horizontal = 12.dp, vertical = 10.dp).fillMaxWidth().height(70.dp),
            shape = RoundedCornerShape(18.dp)
        ) {
            // панель має бути майже непрозорою: під нею прокручується вміст, і він не повинен просвічувати крізь підписи
            Box(Modifier.fillMaxSize().background(if (LocalIsDark.current) Color(0xFF0B0B0C) else Color(0xFFF2F3F4)))
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val w = maxWidth / items.size
                val off by animateDpAsState(w * selIndex.coerceAtLeast(0), tween(180, easing = androidx.compose.animation.core.FastOutSlowInEasing), label = "nav")
                // індикатор усередині панелі, під підписом; на «Налаштуваннях» прихований
                val indAlpha by animateFloatAsState(if (selIndex >= 0) 1f else 0f, tween(160), label = "ind")
                Box(
                    Modifier.align(Alignment.BottomStart).offset(x = off + w / 2 - 12.dp, y = (-7).dp)
                        .graphicsLayer { alpha = indAlpha }.width(24.dp).height(3.dp).clip(RoundedCornerShape(2.dp)).background(onBg())
                )
                Row(Modifier.fillMaxSize()) {
                    items.forEach { t ->
                        val sel = t == tab
                        val c by animateColorAsState(if (sel) onBg() else onBg().copy(alpha = 0.62f), tween(160), label = "nc")
                        Column(
                            Modifier.weight(1f).fillMaxHeight()
                                .clickable(remember { MutableInteractionSource() }, null) { onTab(t) }
                                .padding(horizontal = 2.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(t.icon, t.label, tint = c, modifier = Modifier.size(22.dp))
                            Spacer(Modifier.height(4.dp))
                            Text(t.label, color = c, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1)
                            Spacer(Modifier.height(8.dp))
                        }
                    }
                }
            }
        }
        return
    }
    val items = Tab.entries
    GlassSurface(
        modifier.navigationBarsPadding().padding(horizontal = 10.dp, vertical = 10.dp).fillMaxWidth().height(70.dp),
        shape = RoundedCornerShape(35.dp)
    ) {
        if (LocalUiStyle.current == UiStyle.GLASS) {
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background.copy(alpha = 0.35f)))
        }
        BoxWithConstraints(Modifier.fillMaxSize().padding(5.dp)) {
            val w = maxWidth / items.size
            val off by animateDpAsState(w * tab.ordinal, spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMediumLow), label = "nav")
            Box(
                Modifier.offset(x = off).width(w).fillMaxHeight().clip(RoundedCornerShape(30.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.22f))
            )
            Row(Modifier.fillMaxSize()) {
                items.forEach { t ->
                    val sel = t == tab
                    val sc by animateFloatAsState(if (sel) 1.15f else 1f, bouncy(), label = "ic")
                    val c by animateColorAsState(if (sel) MaterialTheme.colorScheme.primary else dimText(), label = "nc")
                    Column(
                        Modifier.weight(1f).fillMaxHeight()
                            .clickable(remember { MutableInteractionSource() }, null) { onTab(t) }
                            .padding(horizontal = 3.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(t.icon, t.label, tint = c, modifier = Modifier.padding(4.dp).size(22.dp).graphicsLayer { scaleX = sc; scaleY = sc })
                        BasicText(
                            t.label, maxLines = 1,
                            style = TextStyle(color = c, fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal, textAlign = TextAlign.Center),
                            autoSize = TextAutoSize.StepBased(minFontSize = 7.sp, maxFontSize = 11.sp, stepSize = 0.5.sp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun sheetColor(): Color {
    val dark = LocalIsDark.current
    return if (LocalUiStyle.current == UiStyle.GLASS) {
        if (dark) Color(0xFF111214) else Color(0xFFF4F5F6)
    } else MaterialTheme.colorScheme.surfaceContainerHigh
}

/** Вибір території: область → район → громада, або пошук за назвою. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocationSheet(current: Loc, snap: Snapshot?, partialAs: AlertLevel, onDismiss: () -> Unit, onPick: (Loc) -> Unit) {
    var q by remember { mutableStateOf("") }
    var stack by remember { mutableStateOf(listOfNotNull(current.parent?.parent, current.parent).filter { it.children.isNotEmpty() }) }
    val parent = stack.lastOrNull()
    val list: List<Loc> = if (q.isNotBlank()) {
        val qq = q.trim()
        Locations.all().filter { it.name.contains(qq, ignoreCase = true) }
            .sortedWith(compareBy({ !it.name.startsWith(qq, true) }, { it.type.ordinal }, { it.name })).take(100)
    } else parent?.children ?: Locations.oblasts

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = sheetColor()
    ) {
        Column(Modifier.padding(horizontal = 16.dp)) {
            Text("Моя територія", color = onBg(), fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            Text("Оберіть громаду — тоді статус буде найточнішим (тривога в іншому районі області не заважатиме).", color = dimText(), fontSize = 13.sp)
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                q, { q = it }, Modifier.fillMaxWidth(),
                placeholder = { Text("Пошук: місто, громада, район") }, singleLine = true,
                leadingIcon = { Icon(Icons.Rounded.Search, null) }, shape = RoundedCornerShape(18.dp)
            )
            if (q.isBlank() && parent != null) {
                Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { stack = stack.dropLast(1) }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Назад", tint = onBg()) }
                    Text(parent.name, color = onBg(), fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), maxLines = 2)
                }
                LocRow(parent, snap, partialAs, current.id == parent.id, "Уся ${parent.typeTitle}", onPick = { onPick(parent) }, onOpen = null)
            }
            Spacer(Modifier.height(6.dp))
            LazyColumn(Modifier.fillMaxWidth().height(500.dp)) {
                items(list, key = { it.id }) { l ->
                    LocRow(
                        l, snap, partialAs, l.id == current.id,
                        subtitle = if (q.isNotBlank()) l.path.ifBlank { l.typeTitle } else null,
                        onPick = { onPick(l) },
                        onOpen = if (l.children.isNotEmpty()) ({ q = ""; stack = generateSequence(l) { it.parent }.toList().reversed() }) else null
                    )
                }
            }
        }
    }
}

@Composable
private fun LocRow(l: Loc, snap: Snapshot?, partialAs: AlertLevel, selected: Boolean, subtitle: String?, onPick: () -> Unit, onOpen: (() -> Unit)?) {
    val st = snap?.statusOf(l, partialAs) ?: LocStatus.UNKNOWN
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
            .background(if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f) else Color.Transparent)
            .clickable(onClick = onPick).padding(start = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        StatusDot(st.shownLevel, 7.dp)
        Spacer(Modifier.width(6.dp))
        Column(Modifier.weight(1f)) {
            Text(l.name, color = onBg(), fontSize = 15.sp)
            Text(subtitle ?: st.title, color = if (subtitle == null && st.shownLevel.isAlert) st.shownLevel.color() else dimText(), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (onOpen != null) {
            Box(
                Modifier.padding(start = 6.dp).size(44.dp).clip(CircleShape).background(onBg().copy(alpha = 0.06f)).clickable(onClick = onOpen),
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Rounded.ChevronRight, "Відкрити", tint = onBg()) }
        } else Spacer(Modifier.width(8.dp))
    }
}
