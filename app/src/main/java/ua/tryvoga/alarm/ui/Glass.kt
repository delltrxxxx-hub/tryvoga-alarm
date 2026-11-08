package ua.tryvoga.alarm.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ua.tryvoga.alarm.AlertLevel
import ua.tryvoga.alarm.UiStyle
import kotlin.math.cos
import kotlin.math.sin

/** Пружина «як у фізиці» — використовується для всіх інтерактивних анімацій. */
fun <T> bouncy() = spring<T>(dampingRatio = 0.55f, stiffness = Spring.StiffnessMediumLow)
fun <T> soft() = spring<T>(dampingRatio = 0.8f, stiffness = Spring.StiffnessLow)

@Composable
fun onBg(): Color = MaterialTheme.colorScheme.onBackground

@Composable
fun dimText(): Color = MaterialTheme.colorScheme.onSurfaceVariant

/** Фон: у Liquid Glass — живі кольорові «плями» світла, що повільно пливуть. */
@Composable
fun AppBackground(level: AlertLevel, content: @Composable BoxScope.() -> Unit) {
    val style = LocalUiStyle.current
    val dark = LocalIsDark.current
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        if (style == UiStyle.GLASS) {
            val t = rememberInfiniteTransition(label = "bg")
            val phase by t.animateFloat(0f, (2 * Math.PI).toFloat(), infiniteRepeatable(tween(26000, easing = androidx.compose.animation.core.LinearEasing)), label = "p")
            val pulse by t.animateFloat(0.6f, 1f, infiniteRepeatable(tween(1600, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "pulse")
            val alertColor by animateColorAsState(
                when (level) {
                    AlertLevel.RED -> AlertRed
                    AlertLevel.YELLOW -> AlertYellow
                    else -> Color.Transparent
                }, tween(800), label = "ac"
            )
            val base = if (dark) listOf(Color(0xFF0A0F2A), Color(0xFF160B33), Color(0xFF07131F))
            else listOf(Color(0xFFDCE6FF), Color(0xFFF3E6FF), Color(0xFFE1F6FF))
            val blobs = if (dark) listOf(Color(0xFF3B5BFF), Color(0xFFB146FF), Color(0xFF00C2D1), Color(0xFFFF4D8D))
            else listOf(Color(0xFF7FA4FF), Color(0xFFD59CFF), Color(0xFF7FE6F0), Color(0xFFFFA8C8))
            val a = if (dark) 0.55f else 0.65f
            Canvas(Modifier.fillMaxSize()) {
                drawRect(Brush.linearGradient(base, Offset.Zero, Offset(size.width, size.height)))
                val w = size.width; val h = size.height
                val pts = listOf(
                    Offset(w * (0.2f + 0.12f * cos(phase)), h * (0.18f + 0.06f * sin(phase))),
                    Offset(w * (0.85f + 0.1f * sin(phase * 1.3f)), h * (0.35f + 0.08f * cos(phase))),
                    Offset(w * (0.3f + 0.15f * sin(phase * 0.7f)), h * (0.72f + 0.07f * cos(phase * 1.1f))),
                    Offset(w * (0.8f + 0.1f * cos(phase * 0.9f)), h * (0.9f + 0.05f * sin(phase))),
                )
                pts.forEachIndexed { i, c ->
                    val r = w * (0.62f + 0.08f * i % 2)
                    drawCircle(Brush.radialGradient(listOf(blobs[i].copy(alpha = a), Color.Transparent), c, r), r, c)
                }
                if (alertColor.alpha > 0f) {
                    val c = Offset(w * 0.5f, 0f)
                    val r = w * 0.9f * pulse
                    drawCircle(Brush.radialGradient(listOf(alertColor.copy(alpha = 0.45f * alertColor.alpha), Color.Transparent), c, r), r, c)
                }
            }
        } else if (level.isAlert) {
            Box(
                Modifier.fillMaxWidth().height(220.dp)
                    .background(Brush.verticalGradient(listOf(level.color().copy(alpha = 0.18f), Color.Transparent)))
            )
        }
        content()
    }
}

/** Скляна панель: напівпрозора заливка + світлий градієнтний кант + пружне натискання. */
@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(28.dp),
    tint: Color = Color.Unspecified,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val style = LocalUiStyle.current
    val dark = LocalIsDark.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.965f else 1f, spring(dampingRatio = 0.45f, stiffness = 520f), label = "press")
    var m = modifier.graphicsLayer { scaleX = scale; scaleY = scale }.clip(shape)
    m = if (style == UiStyle.GLASS) {
        val fill = if (dark) Brush.linearGradient(listOf(Color.White.copy(alpha = 0.17f), Color.White.copy(alpha = 0.06f)))
        else Brush.linearGradient(listOf(Color.White.copy(alpha = 0.78f), Color.White.copy(alpha = 0.42f)))
        val border = Brush.linearGradient(
            listOf(
                Color.White.copy(alpha = if (dark) 0.5f else 1f),
                Color.White.copy(alpha = if (dark) 0.06f else 0.35f),
                Color.White.copy(alpha = if (dark) 0.25f else 0.7f),
            )
        )
        m.background(fill)
            .then(if (tint.isSpecified) Modifier.background(tint.copy(alpha = 0.16f)) else Modifier)
            .border(1.dp, border, shape)
    } else {
        val base = MaterialTheme.colorScheme.surfaceContainerHigh
        m.background(if (tint.isSpecified) tint.copy(alpha = 0.16f).compositeOver(base) else base)
    }
    if (onClick != null) m = m.clickable(interaction, LocalIndication.current, onClick = onClick)
    Box(m, content = content)
}

@Composable
fun GlassButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    primary: Boolean = false,
    color: Color = Color.Unspecified,
    enabled: Boolean = true,
) {
    val accent = if (color.isSpecified) color else MaterialTheme.colorScheme.primary
    GlassSurface(
        modifier.height(52.dp).graphicsLayer { alpha = if (enabled) 1f else 0.45f },
        shape = RoundedCornerShape(26.dp),
        tint = if (primary) accent else Color.Unspecified,
        onClick = if (enabled) onClick else null,
    ) {
        if (primary) Box(Modifier.fillMaxSize().background(accent.copy(alpha = 0.55f)))
        Row(Modifier.fillMaxSize().padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            val fg = if (primary) Color.White else onBg()
            if (icon != null) { Icon(icon, null, tint = fg, modifier = Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)) }
            Text(text, color = fg, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, maxLines = 1)
        }
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(text.uppercase(), modifier.padding(start = 8.dp, top = 18.dp, bottom = 8.dp), color = dimText(), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.2.sp)
}

/** Сегментований перемикач з «капсулою», що перескакує на пружині. */
@Composable
fun <T> Segmented(options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit, modifier: Modifier = Modifier) {
    GlassSurface(modifier.fillMaxWidth().height(46.dp), shape = RoundedCornerShape(23.dp)) {
        BoxWithConstraints(Modifier.fillMaxSize().padding(4.dp)) {
            val w: Dp = maxWidth / options.size
            val idx = options.indexOf(selected).coerceAtLeast(0)
            val off by animateDpAsState(w * idx, spring(dampingRatio = 0.62f, stiffness = Spring.StiffnessMediumLow), label = "seg")
            Box(Modifier.offset(x = off).width(w).fillMaxHeight().clip(RoundedCornerShape(19.dp)).background(MaterialTheme.colorScheme.primary))
            Row(Modifier.fillMaxSize()) {
                options.forEach { o ->
                    val sel = o == selected
                    val fg by animateColorAsState(if (sel) MaterialTheme.colorScheme.onPrimary else onBg(), label = "fg")
                    Box(
                        Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(19.dp)).clickable { onSelect(o) },
                        contentAlignment = Alignment.Center
                    ) {
                        androidx.compose.foundation.text.BasicText(
                            label(o), Modifier.padding(horizontal = 4.dp), maxLines = 1,
                            style = androidx.compose.ui.text.TextStyle(color = fg, fontWeight = FontWeight.SemiBold),
                            autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = 9.sp, maxFontSize = 13.sp, stepSize = 0.5.sp)
                        )
                    }
                }
            }
        }
    }
}

/** Горизонтальний ряд «таблеток» для вибору значення. */
@Composable
fun <T> ChoicePills(options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit, accent: Color = MaterialTheme.colorScheme.primary) {
    // Перенесення в новий рядок (нічого не обрізається) + відступи під пружне збільшення.
    @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
    androidx.compose.foundation.layout.FlowRow(
        Modifier.fillMaxWidth().padding(horizontal = 2.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        options.forEach { o ->
            val sel = o == selected
            val bg by animateColorAsState(if (sel) accent else Color.Transparent, label = "pill")
            val sc by animateFloatAsState(if (sel) 1.06f else 1f, bouncy(), label = "ps")
            GlassSurface(Modifier.graphicsLayer { scaleX = sc; scaleY = sc }, shape = RoundedCornerShape(18.dp), onClick = { onSelect(o) }) {
                Box(Modifier.background(bg).padding(horizontal = 14.dp, vertical = 9.dp)) {
                    Text(label(o), color = if (sel) Color.White else onBg(), fontSize = 14.sp, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

@Composable
fun SettingRow(
    icon: ImageVector?,
    title: String,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
    iconTint: Color = MaterialTheme.colorScheme.primary,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    Row(
        Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Box(Modifier.size(36.dp).clip(CircleShape).background(iconTint.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = iconTint, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(14.dp))
        }
        Column(Modifier.weight(1f).padding(end = 8.dp)) {
            Text(title, color = onBg(), fontSize = 16.sp, fontWeight = FontWeight.Medium)
            if (subtitle != null) Text(subtitle, color = dimText(), fontSize = 13.sp)
        }
        Row(verticalAlignment = Alignment.CenterVertically, content = trailing)
    }
}

@Composable
fun GlassSwitch(checked: Boolean, onChange: (Boolean) -> Unit, accent: Color = MaterialTheme.colorScheme.primary) {
    Switch(
        checked = checked, onCheckedChange = onChange,
        colors = SwitchDefaults.colors(
            checkedTrackColor = accent,
            checkedThumbColor = Color.White,
            uncheckedTrackColor = onBg().copy(alpha = 0.12f),
            uncheckedBorderColor = onBg().copy(alpha = 0.25f),
            uncheckedThumbColor = onBg().copy(alpha = 0.7f),
        )
    )
}

@Composable
fun StatusDot(level: AlertLevel, size: Dp = 10.dp) {
    val t = rememberInfiniteTransition(label = "dot")
    val s by t.animateFloat(1f, if (level.isAlert) 1.9f else 1f, infiniteRepeatable(tween(1100), RepeatMode.Restart), label = "ds")
    val a by t.animateFloat(0.6f, 0f, infiniteRepeatable(tween(1100), RepeatMode.Restart), label = "da")
    val c by animateColorAsState(level.color(), tween(600), label = "dc")
    Box(Modifier.size(size * 2), contentAlignment = Alignment.Center) {
        if (level.isAlert) Box(Modifier.size(size).graphicsLayer { scaleX = s; scaleY = s; alpha = a }.clip(CircleShape).background(c))
        Box(Modifier.size(size).clip(CircleShape).background(c))
    }
}
