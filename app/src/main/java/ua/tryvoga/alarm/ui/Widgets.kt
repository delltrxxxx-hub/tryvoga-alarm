package ua.tryvoga.alarm.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.min

/**
 * Барабан для вибору числа свайпом: нескінченна прокрутка, прилипання до центру (snap),
 * 3D-нахил і масштаб елементів, тактильний відгук на кожному кроці.
 */
@Composable
fun WheelPicker(
    count: Int,
    value: Int,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    itemHeight: Dp = 58.dp,
    visible: Int = 5,
    fontSize: TextUnit = 44.sp,
    label: (Int) -> String = { "%02d".format(it) },
) {
    val loops = 1000
    val total = count * loops
    val startIndex = remember { total / 2 - (total / 2) % count + value }
    val state = rememberLazyListState(initialFirstVisibleItemIndex = startIndex - visible / 2)
    val fling = rememberSnapFlingBehavior(lazyListState = state)
    val haptic = LocalHapticFeedback.current
    val onChange by rememberUpdatedState(onValueChange)
    val center by remember {
        derivedStateOf {
            val info = state.layoutInfo
            val mid = (info.viewportStartOffset + info.viewportEndOffset) / 2
            info.visibleItemsInfo.minByOrNull { abs(it.offset + it.size / 2 - mid) }?.index ?: startIndex
        }
    }
    var last by remember { mutableStateOf(value) }
    LaunchedEffect(center) {
        val v = center % count
        if (v != last) {
            last = v
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onChange(v)
        }
    }
    val primary = MaterialTheme.colorScheme.primary
    val fg = MaterialTheme.colorScheme.onBackground
    Box(modifier.height(itemHeight * visible), contentAlignment = Alignment.Center) {
        Box(
            Modifier.fillMaxWidth().height(itemHeight).clip(RoundedCornerShape(18.dp))
                .background(primary.copy(alpha = 0.14f))
        )
        LazyColumn(state = state, flingBehavior = fling, modifier = Modifier.fillMaxSize()) {
            items(total) { i ->
                Box(
                    Modifier.height(itemHeight).fillMaxWidth().graphicsLayer {
                        val info = state.layoutInfo
                        val item = info.visibleItemsInfo.firstOrNull { it.index == i }
                        if (item != null) {
                            val mid = (info.viewportStartOffset + info.viewportEndOffset) / 2f
                            val d = (item.offset + item.size / 2f - mid) / (item.size * (visible / 2f))
                            val ad = abs(d).coerceIn(0f, 1f)
                            alpha = 1f - ad * 0.8f
                            scaleX = 1f - ad * 0.28f
                            scaleY = scaleX
                            rotationX = -d * 50f
                        }
                    },
                    contentAlignment = Alignment.Center
                ) {
                    Text(label(i % count), fontSize = fontSize, fontWeight = FontWeight.Light, color = fg)
                }
            }
        }
    }
}

/** Стрілочний годинник; секундна стрілка «клацає» на пружині. */
@Composable
fun AnalogClock(modifier: Modifier = Modifier, zone: ZoneId = ZoneId.systemDefault(), showSeconds: Boolean = true) {
    var now by remember { mutableStateOf(ZonedDateTime.now(zone)) }
    LaunchedEffect(zone) {
        while (true) {
            now = ZonedDateTime.now(zone)
            delay(1000 - System.currentTimeMillis() % 1000)
        }
    }
    val sec = remember { Animatable(now.second * 6f) }
    LaunchedEffect(now.second) {
        val base = floor(sec.value / 360f) * 360f
        var t = base + now.second * 6f
        if (t < sec.value - 1f) t += 360f
        sec.animateTo(t, spring(dampingRatio = 0.32f, stiffness = 700f))
    }
    val fg = MaterialTheme.colorScheme.onBackground
    val accent = AlertRed
    val primary = MaterialTheme.colorScheme.primary
    val glass = LocalUiStyle.current == ua.tryvoga.alarm.UiStyle.GLASS
    Canvas(modifier) {
        val r = min(size.width, size.height) / 2f
        val c = Offset(size.width / 2, size.height / 2)
        drawCircle(
            if (glass) Brush.linearGradient(listOf(Color.White.copy(alpha = 0.22f), Color.White.copy(alpha = 0.05f)))
            else Brush.linearGradient(listOf(primary.copy(alpha = 0.16f), primary.copy(alpha = 0.08f))),
            r, c
        )
        drawCircle(Color.White.copy(alpha = if (glass) 0.4f else 0f), r, c, style = androidx.compose.ui.graphics.drawscope.Stroke(1.5f))
        for (i in 0 until 60) {
            val big = i % 5 == 0
            rotate(i * 6f, c) {
                drawLine(
                    fg.copy(alpha = if (big) 0.9f else 0.3f),
                    Offset(c.x, c.y - r * 0.92f), Offset(c.x, c.y - r * (if (big) 0.8f else 0.87f)),
                    strokeWidth = if (big) r * 0.025f else r * 0.01f, cap = StrokeCap.Round
                )
            }
        }
        val h = (now.hour % 12 + now.minute / 60f) * 30f
        val m = (now.minute + now.second / 60f) * 6f
        rotate(h, c) { drawLine(fg, c, Offset(c.x, c.y - r * 0.48f), r * 0.06f, StrokeCap.Round) }
        rotate(m, c) { drawLine(fg.copy(alpha = 0.9f), c, Offset(c.x, c.y - r * 0.72f), r * 0.04f, StrokeCap.Round) }
        if (showSeconds) rotate(sec.value, c) {
            drawLine(accent, Offset(c.x, c.y + r * 0.15f), Offset(c.x, c.y - r * 0.8f), r * 0.015f, StrokeCap.Round)
        }
        drawCircle(accent, r * 0.045f, c)
    }
}
