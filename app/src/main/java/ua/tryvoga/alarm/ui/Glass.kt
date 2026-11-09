package ua.tryvoga.alarm.ui

import android.provider.Settings
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ua.tryvoga.alarm.AlertLevel
import ua.tryvoga.alarm.UiStyle

/*
 * «Кристал циферблата»: однотонний фон, тонкі скляні панелі з бліком по верхньому краю,
 * колір — лише в статусі тривоги. Рух — «тік»: коротко й точно, без відскоків.
 * Режим Material 3 зберігає свою стару поведінку.
 */

@Composable
fun isGlass(): Boolean = LocalUiStyle.current == UiStyle.GLASS

/** Пружина: у склі — критично демпфована (без відскоку), у Material 3 — м'яка. */
@Composable
fun <T> bouncy(): SpringSpec<T> =
    if (isGlass()) spring(dampingRatio = 1f, stiffness = Spring.StiffnessMediumLow)
    else spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMediumLow)

@Composable
fun <T> soft(): SpringSpec<T> = spring(dampingRatio = if (isGlass()) 1f else 0.8f, stiffness = Spring.StiffnessLow)

@Composable
fun onBg(): Color = MaterialTheme.colorScheme.onBackground

@Composable
fun dimText(): Color = MaterialTheme.colorScheme.onSurfaceVariant

/** Колір статусу для ТЕКСТУ: на світлому склі жовтий/зелений/червоний темніші, щоб читалися. */
@Composable
fun AlertLevel.textColor(): Color {
    if (LocalIsDark.current || !isGlass()) return if (this == AlertLevel.UNKNOWN) dimText() else color()
    return when (this) {
        AlertLevel.RED -> Color(0xFFD70015)
        AlertLevel.YELLOW -> Color(0xFF8A6A00)
        AlertLevel.NONE -> Color(0xFF1E7A35)
        AlertLevel.UNKNOWN -> dimText()
    }
}

/** Системне «прибрати анімації» — тоді нічого не «дихає». */
@Composable
fun reduceMotion(): Boolean {
    val ctx = LocalContext.current
    return remember {
        runCatching { Settings.Global.getFloat(ctx.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }.getOrDefault(false)
    }
}

/**
 * Фон. Скло: однотонний, нерухомий. Під час тривоги — смуга рифленого скла вгорі,
 * за якою світиться колір статусу (як світло за дверима з рифленим склом).
 */
@Composable
fun AppBackground(level: AlertLevel, content: @Composable BoxScope.() -> Unit) {
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        if (isGlass()) {
            FlutedAlertLight(level, Modifier.fillMaxWidth().height(250.dp))
        } else if (level.isAlert) {
            Box(
                Modifier.fillMaxWidth().height(220.dp)
                    .background(Brush.verticalGradient(listOf(level.color().copy(alpha = 0.18f), Color.Transparent)))
            )
        }
        content()
    }
}

/** Рифлене скло зі світлом статусу. Без тривоги — нічого не малює. */
@Composable
fun FlutedAlertLight(level: AlertLevel, modifier: Modifier = Modifier) {
    val dark = LocalIsDark.current
    val target = when (level) {
        AlertLevel.RED -> AlertRed.copy(alpha = if (dark) 0.35f else 0.22f)
        AlertLevel.YELLOW -> AlertYellow.copy(alpha = if (dark) 0.30f else 0.20f)
        else -> Color.Transparent
    }
    val light by animateColorAsState(target, tween(800), label = "light")
    if (light.alpha <= 0.001f) return
    val ribPx = with(LocalDensity.current) { 6.dp.toPx() }
    val bg = MaterialTheme.colorScheme.background
    Canvas(modifier) {
        // світло позаду скла: найсильніше вгорі, гасне донизу
        drawRect(Brush.verticalGradient(listOf(light, light.copy(alpha = light.alpha * 0.55f), Color.Transparent)))
        // вертикальні ребра: світла грань → тінь, період 6dp
        drawRect(
            Brush.horizontalGradient(
                0f to Color.White.copy(alpha = if (dark) 0.07f else 0.18f),
                0.45f to Color.Transparent,
                1f to Color.Black.copy(alpha = if (dark) 0.22f else 0.06f),
                startX = 0f, endX = ribPx, tileMode = TileMode.Repeated
            ),
            alpha = (light.alpha * 2.4f).coerceAtMost(1f)
        )
        // м'який перехід у фон знизу
        drawRect(Brush.verticalGradient(0.6f to Color.Transparent, 1f to bg))
    }
}

/**
 * Скляна панель. Скло: заливка 8% (темна) / 72% (світла), обводка 1dp, блік по верхньому краю
 * і легкий внутрішній градієнт зверху. Натискання — лише прозорість, без масштабування.
 */
@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(16.dp),
    tint: Color = Color.Unspecified,
    onClick: (() -> Unit)? = null,
    elevated: Boolean = false,
    content: @Composable BoxScope.() -> Unit,
) {
    val glass = isGlass()
    val dark = LocalIsDark.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    var m = modifier
    if (glass) {
        val a by animateFloatAsState(if (pressed) 0.7f else 1f, tween(90), label = "press")
        val fill = if (dark) Color.White.copy(alpha = if (elevated) 0.12f else 0.08f) else Color.White.copy(alpha = if (elevated) 0.86f else 0.72f)
        val edge = if (dark) Color.White.copy(alpha = 0.12f) else Color.Black.copy(alpha = 0.11f)
        val sheen = Color.White.copy(alpha = if (dark) 0.22f else 0.9f)
        m = m.graphicsLayer { alpha = a }.clip(shape).background(fill)
            .then(if (tint.isSpecified) Modifier.background(tint.copy(alpha = if (dark) 0.10f else 0.08f)) else Modifier)
            .drawWithContent {
                // внутрішнє світло зверху (24dp) — відчуття товщини скла
                drawRect(Brush.verticalGradient(0f to Color.White.copy(alpha = if (dark) 0.06f else 0.25f), 1f to Color.Transparent, endY = 24.dp.toPx()))
                drawContent()
                // блік по верхньому краю: яскравий у центрі, гасне до боків
                drawLine(
                    Brush.horizontalGradient(listOf(Color.Transparent, sheen, Color.Transparent)),
                    Offset(size.width * 0.08f, 0.5.dp.toPx()), Offset(size.width * 0.92f, 0.5.dp.toPx()), strokeWidth = 1.dp.toPx()
                )
            }
            .border(1.dp, edge, shape)
    } else {
        val scale by animateFloatAsState(if (pressed) 0.97f else 1f, spring(dampingRatio = 0.5f, stiffness = 500f), label = "press")
        val base = MaterialTheme.colorScheme.surfaceContainerHigh
        m = m.graphicsLayer { scaleX = scale; scaleY = scale }.clip(shape)
            .background(if (tint.isSpecified) tint.copy(alpha = 0.16f).compositeOver(base) else base)
    }
    if (onClick != null) m = m.clickable(interaction, LocalIndication.current, onClick = onClick)
    Box(m, content = content)
}

/** Кнопка. Скло: головна — суцільна (білим на чорному / чорним на світлому), решта — контур. */
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
    if (isGlass()) {
        val interaction = remember { MutableInteractionSource() }
        val pressed by interaction.collectIsPressedAsState()
        val a by animateFloatAsState(if (!enabled) 0.4f else if (pressed) 0.7f else 1f, tween(90), label = "btn")
        val shape = RoundedCornerShape(12.dp)
        val fillColor = when {
            !primary -> Color.Transparent
            color.isSpecified -> color
            else -> MaterialTheme.colorScheme.primary
        }
        val fg = when {
            !primary -> onBg()
            color.isSpecified -> Color.White
            else -> MaterialTheme.colorScheme.onPrimary
        }
        Row(
            modifier.height(50.dp).graphicsLayer { alpha = a }.clip(shape).background(fillColor)
                .then(if (!primary) Modifier.border(1.dp, onBg().copy(alpha = 0.28f), shape) else Modifier)
                .clickable(interaction, LocalIndication.current, enabled = enabled, onClick = onClick)
                .padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center
        ) {
            if (icon != null) { Icon(icon, null, tint = fg, modifier = Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)) }
            Text(text, color = fg, fontWeight = FontWeight.Medium, fontSize = 15.sp, maxLines = 1)
        }
        return
    }
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
    if (isGlass()) {
        Text(text, modifier.padding(start = 4.dp, top = 22.dp, bottom = 8.dp), color = dimText(), fontSize = 13.sp, fontWeight = FontWeight.Medium)
    } else {
        Text(text.uppercase(), modifier.padding(start = 8.dp, top = 18.dp, bottom = 8.dp), color = dimText(), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.2.sp)
    }
}

/** Сегменти. Скло: вибраний — підкреслення 2dp, без «капсули». */
@Composable
fun <T> Segmented(options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit, modifier: Modifier = Modifier) {
    val glass = isGlass()
    GlassSurface(modifier.fillMaxWidth().height(46.dp), shape = RoundedCornerShape(if (glass) 12.dp else 23.dp)) {
        BoxWithConstraints(Modifier.fillMaxSize().padding(if (glass) 0.dp else 4.dp)) {
            val w: Dp = maxWidth / options.size
            val idx = options.indexOf(selected).coerceAtLeast(0)
            val off by animateDpAsState(
                w * idx,
                if (glass) tween(180, easing = FastOutSlowInEasing) else spring(dampingRatio = 0.62f, stiffness = Spring.StiffnessMediumLow),
                label = "seg"
            )
            if (glass) {
                Box(Modifier.align(Alignment.BottomStart).offset(x = off + w / 2 - 14.dp).width(28.dp).height(2.dp).background(onBg()))
            } else {
                Box(Modifier.offset(x = off).width(w).fillMaxHeight().clip(RoundedCornerShape(19.dp)).background(MaterialTheme.colorScheme.primary))
            }
            Row(Modifier.fillMaxSize()) {
                options.forEach { o ->
                    val sel = o == selected
                    val fg by animateColorAsState(
                        when {
                            glass && sel -> onBg()
                            glass -> dimText()
                            sel -> MaterialTheme.colorScheme.onPrimary
                            else -> onBg()
                        }, label = "fg"
                    )
                    Box(Modifier.weight(1f).fillMaxHeight().clickable { onSelect(o) }, contentAlignment = Alignment.Center) {
                        androidx.compose.foundation.text.BasicText(
                            label(o), Modifier.padding(horizontal = 4.dp), maxLines = 1,
                            style = TextStyle(color = fg, fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Medium),
                            autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = 10.sp, maxFontSize = 14.sp, stepSize = 0.5.sp)
                        )
                    }
                }
            }
        }
    }
}

/** Вибір значення «таблетками». Скло: контур 1dp, вибране — суцільне, без збільшення. */
@Composable
fun <T> ChoicePills(options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit, accent: Color = MaterialTheme.colorScheme.primary) {
    val glass = isGlass()
    @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
    androidx.compose.foundation.layout.FlowRow(
        Modifier.fillMaxWidth().padding(horizontal = 2.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        options.forEach { o ->
            val sel = o == selected
            if (glass) {
                // у склі колір — лише для статусу; звичайний вибір — інверсія
                val selFill = MaterialTheme.colorScheme.primary
                val selFg = MaterialTheme.colorScheme.onPrimary
                val bg by animateColorAsState(if (sel) selFill else Color.Transparent, tween(180), label = "pill")
                val shape = RoundedCornerShape(12.dp)
                Box(
                    Modifier.clip(shape).background(bg)
                        .border(1.dp, if (sel) Color.Transparent else onBg().copy(alpha = 0.22f), shape)
                        .clickable { onSelect(o) }.padding(horizontal = 14.dp, vertical = 9.dp)
                ) { Text(label(o), color = if (sel) selFg else onBg(), fontSize = 14.sp, fontWeight = FontWeight.Medium) }
            } else {
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
    val glass = isGlass()
    Row(
        Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = if (glass) 14.dp else 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            if (glass) {
                // без кольорових «бульбашок»: колір лишаємо лише статусам
                val isStatus = iconTint == AlertRed || iconTint == AlertGreen || iconTint == AlertYellow
                Icon(icon, null, tint = if (isStatus) iconTint else dimText(), modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(16.dp))
            } else {
                Box(Modifier.size(36.dp).clip(CircleShape).background(iconTint.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
                    Icon(icon, null, tint = iconTint, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(14.dp))
            }
        }
        Column(Modifier.weight(1f).padding(end = 8.dp)) {
            Text(title, color = onBg(), fontSize = 16.sp, fontWeight = if (glass) FontWeight.Normal else FontWeight.Medium)
            if (subtitle != null) Text(subtitle, color = dimText(), fontSize = 13.sp)
        }
        Row(verticalAlignment = Alignment.CenterVertically, content = trailing)
    }
}

/** Тонка лінія-роздільник усередині скляної панелі. */
@Composable
fun GlassDivider(startIndent: Dp = 16.dp) {
    Box(Modifier.padding(start = startIndent).fillMaxWidth().height(1.dp).background(onBg().copy(alpha = if (isGlass()) 0.08f else 0.08f)))
}

@Composable
fun GlassSwitch(checked: Boolean, onChange: (Boolean) -> Unit, accent: Color = MaterialTheme.colorScheme.primary) {
    if (isGlass()) {
        Switch(
            checked = checked, onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedTrackColor = MaterialTheme.colorScheme.primary,
                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                checkedBorderColor = Color.Transparent,
                uncheckedTrackColor = Color.Transparent,
                uncheckedBorderColor = onBg().copy(alpha = 0.35f),
                uncheckedThumbColor = onBg().copy(alpha = 0.6f),
            )
        )
        return
    }
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

/**
 * Крапка статусу. Скло: під час тривоги лише повільно «дихає» прозорістю (2.4 с), без збільшення;
 * «немає даних» — порожниста. Пауза дихання через [paused].
 */
@Composable
fun StatusDot(level: AlertLevel, size: Dp = 10.dp, paused: Boolean = false) {
    val c by animateColorAsState(level.color(), tween(600), label = "dc")
    if (isGlass()) {
        val still = paused || reduceMotion() || !level.isAlert
        val t = rememberInfiniteTransition(label = "dot")
        val breathe by t.animateFloat(1f, 0.35f, infiniteRepeatable(tween(1200, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "br")
        Box(Modifier.size(size + 8.dp), contentAlignment = Alignment.Center) {
            if (level == AlertLevel.UNKNOWN) {
                Box(Modifier.size(size).border(1.5.dp, c, CircleShape))
            } else {
                Box(Modifier.size(size).graphicsLayer { alpha = if (still) 1f else breathe }.clip(CircleShape).background(c))
            }
        }
        return
    }
    val t = rememberInfiniteTransition(label = "dot")
    val s by t.animateFloat(1f, if (level.isAlert) 1.9f else 1f, infiniteRepeatable(tween(1100), RepeatMode.Restart), label = "ds")
    val a by t.animateFloat(0.6f, 0f, infiniteRepeatable(tween(1100), RepeatMode.Restart), label = "da")
    Box(Modifier.size(size * 2), contentAlignment = Alignment.Center) {
        if (level.isAlert) Box(Modifier.size(size).graphicsLayer { scaleX = s; scaleY = s; alpha = a }.clip(CircleShape).background(c))
        Box(Modifier.size(size).clip(CircleShape).background(c))
    }
}
