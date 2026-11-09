package ua.tryvoga.alarm.ui

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AlarmOff
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Snooze
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import ua.tryvoga.alarm.AlarmService
import ua.tryvoga.alarm.AlertLevel
import ua.tryvoga.alarm.SettingsRepo
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

class AlarmActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (android.os.Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        SettingsRepo.init(this)
        setContent {
            val s by SettingsRepo.settings.collectAsState()
            val info by AlarmService.ringing.collectAsState()
            var seen by remember { mutableStateOf(false) }
            LaunchedEffect(info) {
                if (info != null) seen = true
                else { delay(if (seen) 300 else 3000); if (AlarmService.ringing.value == null) finish() }
            }
            AppTheme(s) {
                info?.let { RingScreen(it, { AlarmService.command(this, AlarmService.ACTION_DISMISS) }, { AlarmService.command(this, AlarmService.ACTION_SNOOZE) }) }
                    ?: AppBackground(AlertLevel.NONE) {}
            }
        }
    }
}

@Composable
private fun RingScreen(info: AlarmService.RingInfo, onDismiss: () -> Unit, onSnooze: () -> Unit) {
    var now by remember { mutableStateOf(LocalTime.now()) }
    LaunchedEffect(Unit) { while (true) { now = LocalTime.now(); delay(1000) } }
    val t = rememberInfiniteTransition(label = "ring")
    val wobble by t.animateFloat(-3f, 3f, infiniteRepeatable(tween(140), RepeatMode.Reverse), label = "w")
    val breathe by t.animateFloat(0.96f, 1.04f, infiniteRepeatable(tween(1400), RepeatMode.Reverse), label = "b")

    AppBackground(if (info.mode == AlarmService.MODE_AFTER_CLEAR) AlertLevel.NONE else info.level) {
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(40.dp))
            Text(info.title, color = onBg(), fontSize = 22.sp, fontWeight = FontWeight.Medium)
            Text(
                now.format(DateTimeFormatter.ofPattern("HH:mm")), color = onBg(), fontSize = 96.sp, fontWeight = FontWeight.Thin,
                modifier = Modifier.graphicsLayer { scaleX = breathe; scaleY = breathe }
            )
            Spacer(Modifier.height(12.dp))
            val accent = when {
                info.mode == AlarmService.MODE_AFTER_CLEAR -> AlertGreen
                info.level.isAlert -> info.level.color()
                else -> MaterialTheme.colorScheme.primary
            }
            GlassSurface(tint = accent, shape = RoundedCornerShape(22.dp)) {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Shield, null, tint = accent)
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(info.subtitle, color = onBg(), fontWeight = FontWeight.SemiBold)
                        if (info.quieted) Text(if (info.volume == 0) "Лише вібрація через тривогу" else "Гучність знижено до ${info.volume}%", color = dimText(), fontSize = 13.sp)
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            Box(Modifier.size(140.dp).graphicsLayer { rotationZ = wobble }, contentAlignment = Alignment.Center) {
                Box(Modifier.size(140.dp).graphicsLayer { scaleX = breathe * 1.1f; scaleY = breathe * 1.1f; alpha = 0.3f }.clip(CircleShape).background(accent))
                GlassSurface(Modifier.size(110.dp), shape = CircleShape, tint = accent) {
                    Icon(Icons.Rounded.AlarmOff, null, tint = onBg(), modifier = Modifier.size(52.dp).align(Alignment.Center))
                }
            }
            Spacer(Modifier.weight(1f))
            if (info.canSnooze) {
                GlassButton("Відкласти", onSnooze, Modifier.fillMaxWidth(0.7f), Icons.Rounded.Snooze)
                Spacer(Modifier.height(16.dp))
            }
            SlideToDismiss(accent, onDismiss)
            Spacer(Modifier.height(20.dp))
        }
    }
}

/** Повзунок «Проведіть, щоб вимкнути»: при недотягуванні повертається на пружині. */
@Composable
private fun SlideToDismiss(accent: Color, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    val offset = remember { Animatable(0f) }
    val density = LocalDensity.current
    GlassSurface(Modifier.fillMaxWidth().height(72.dp), shape = RoundedCornerShape(36.dp)) {
        BoxWithConstraints(Modifier.fillMaxSize().padding(6.dp)) {
            val thumb = 60.dp
            val maxPx = with(density) { (maxWidth - thumb).toPx() }
            Text(
                "Проведіть, щоб вимкнути  ›››", Modifier.align(Alignment.Center).graphicsLayer { alpha = 1f - (offset.value / maxPx).coerceIn(0f, 1f) },
                color = dimText(), fontWeight = FontWeight.Medium
            )
            Box(
                Modifier.offset { IntOffset(offset.value.roundToInt(), 0) }.size(thumb).clip(CircleShape).background(accent)
                    .pointerInput(maxPx) {
                        detectHorizontalDragGestures(
                            onDragEnd = {
                                scope.launch {
                                    if (offset.value > maxPx * 0.7f) { offset.animateTo(maxPx, spring()); onDismiss() }
                                    else offset.animateTo(0f, spring(dampingRatio = 0.4f, stiffness = 300f))
                                }
                            },
                            onHorizontalDrag = { change, drag ->
                                change.consume()
                                scope.launch { offset.snapTo((offset.value + drag).coerceIn(0f, maxPx)) }
                            }
                        )
                    },
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Rounded.AlarmOff, null, tint = Color.White) }
        }
    }
}

@Suppress("unused")
private val unusedArr = Arrangement.Center
