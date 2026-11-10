// SailDash: Ajustes → Sonido (el ecualizador de las apps).
package com.irvingguerra.litedash

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.irvingguerra.litedash.core.ui.Button
import com.irvingguerra.litedash.core.ui.Label
import com.irvingguerra.litedash.core.ui.LocalColors
import com.irvingguerra.litedash.core.ui.Segmented
import com.irvingguerra.litedash.core.ui.SwitchRow
import com.irvingguerra.litedash.core.ui.TextInput
import com.irvingguerra.litedash.core.ui.Typography
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** Ícono de Sonido en el menú de Ajustes: una bocina con sus ondas (el mismo trazo que los de core/ui). */
internal val EqSoundIcon: ImageVector = ImageVector.Builder(defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
    .addPath(
        addPathNodes("M4 9.5h3.5l5 -4.5v14l-5 -4.5h-3.5z M16 9a4 4 0 0 1 0 6 M18.5 6.5a7.5 7.5 0 0 1 0 11"),
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
    )
    .build()

/**
 * Ajustes → Sonido. Arriba, prenderlo y cómo va; luego el estilo con la curva que se arrastra, los
 * ajustes rápidos, "Mis ajustes", qué suena con él (con el atajo al de fábrica para la radio y el
 * Bluetooth) y lo avanzado.
 */
@Composable
internal fun EqSettingsSection() {
    val context = LocalContext.current
    val c = LocalColors.current
    val eq = remember { EqEngine.get(context) }
    // Las bandas de la pantalla (una vez; quedan guardadas para la próxima).
    LaunchedEffect(Unit) { eq.probe() }
    val s = eq.settings
    val device = eq.device

    SettingsCard {
        SwitchRow(stringResource(R.string.ecualizador_enable), s.enabled, { eq.update(s.copy(enabled = it)) }, stringResource(R.string.ecualizador_enable_help))
        EqStatusLine()
    }
    if (s.enabled && device != null) {
        SettingsCard(stringResource(R.string.ecualizador_style), stringResource(R.string.ecualizador_curve_help)) {
            EqPresetChips(s, eq.slots) { eq.update(it) }
            EqEditor(s, device, { eq.preview(it) }, { eq.update(it) })
            EqCurveFooter(s, device) { eq.update(it) }
        }
        SettingsCard(stringResource(R.string.ecualizador_quick)) {
            SwitchRow(stringResource(R.string.ecualizador_protect), s.protect, { eq.update(s.copy(protect = it)) }, stringResource(R.string.ecualizador_protect_help))
            EqSlider(stringResource(R.string.ecualizador_bass), s.bass, { eq.preview(s.withBass(it)) }, { eq.update(s.withBass(it)) })
            EqSlider(stringResource(R.string.ecualizador_voice), s.voice, { eq.preview(s.withVoice(it)) }, { eq.update(s.withVoice(it)) })
            Label(stringResource(R.string.ecualizador_voice_help), Typography.small, c.textMuted)
        }
        SettingsCard(stringResource(R.string.ecualizador_slots_title), stringResource(R.string.ecualizador_slots_help)) { EqSaveSlots(eq) }
    } else if (s.enabled && eq.unsupported == null) {
        SettingsCard { Label(stringResource(R.string.ecualizador_loading), Typography.body, c.textMuted) }
    }
    SettingsCard(stringResource(R.string.ecualizador_explain_title)) { EqFactory() }
    if (s.enabled && device != null) SettingsCard(stringResource(R.string.ecualizador_advanced)) { EqAdvanced(s, device, eq) }
}

/** Cómo va: en todo el sonido, en qué apps, apagado o por qué no se puede. */
@Composable
private fun EqStatusLine() {
    val c = LocalColors.current
    val st = EqEngine.status
    val unsupported = EqEngine.unsupported
    val (text, color) = when {
        // Apagado pero ya se sabe que la pantalla no lo deja: se dice de una vez.
        st.state == EqState.OFF && unsupported != null ->
            stringResource(R.string.ecualizador_status_unsupported, stringResource(eqReasonText(unsupported))) to c.danger
        st.state == EqState.OFF -> stringResource(R.string.ecualizador_status_off) to c.textMuted
        st.state == EqState.STARTING -> stringResource(R.string.ecualizador_status_starting) to c.textMuted
        st.state == EqState.GLOBAL -> stringResource(R.string.ecualizador_status_global) to c.accent
        st.state == EqState.APPS && st.apps.isEmpty() -> stringResource(R.string.ecualizador_status_apps_waiting) to c.accent
        st.state == EqState.APPS -> stringResource(R.string.ecualizador_status_apps, st.apps.joinToString(", ")) to c.accent
        else -> stringResource(R.string.ecualizador_status_unsupported, stringResource(eqReasonText(st.reason))) to c.danger
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(12.dp).clip(CircleShape).background(color))
        Label(text, Typography.body, modifier = Modifier.weight(1f))
    }
    if (st.state == EqState.APPS && st.reason != null) Label(stringResource(R.string.ecualizador_status_fallback), Typography.small, c.textMuted)
}

private fun eqReasonText(reason: EqReason?) = when (reason) {
    EqReason.NO_EFFECT -> R.string.ecualizador_reason_no_effect
    EqReason.DENIED -> R.string.ecualizador_reason_denied
    EqReason.GLOBAL_FAILED -> R.string.ecualizador_reason_global
    EqReason.NO_CONTROL -> R.string.ecualizador_reason_control
    EqReason.FAILED, null -> R.string.ecualizador_reason_failed
}

private fun eqPresetName(id: String) = when (id) {
    EqPresets.TECHNO -> R.string.ecualizador_preset_techno
    EqPresets.DEEP_BASS -> R.string.ecualizador_preset_deep_bass
    EqPresets.VOICE -> R.string.ecualizador_preset_voice
    EqPresets.ROCK -> R.string.ecualizador_preset_rock
    EqPresets.PODCAST -> R.string.ecualizador_preset_podcast
    EqPresets.NIGHT -> R.string.ecualizador_preset_night
    else -> R.string.ecualizador_preset_flat
}

/** El nombre de "Mis ajustes" [index]: el que le pusieron o "Mis ajustes 1". */
@Composable
private fun eqSlotName(index: Int, slot: EqSlot?) =
    slot?.name?.takeIf { it.isNotBlank() } ?: stringResource(R.string.ecualizador_slot_default, index + 1)

/** De dónde salió la curva: el estilo o "Mis ajustes". */
@Composable
private fun eqCurrentName(s: EqSettings): String {
    val slot = s.slot
    return if (slot != null) eqSlotName(slot, EqEngine.slots.getOrNull(slot)) else stringResource(eqPresetName(s.preset))
}

/** Los estilos y los "Mis ajustes" guardados: un toque y listo. */
@Composable
private fun EqPresetChips(s: EqSettings, slots: List<EqSlot?>, onPick: (EqSettings) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        for (p in EqPresets.all) Button(stringResource(eqPresetName(p.id)), { onPick(s.withPreset(p)) }, primary = s.preset == p.id && !s.edited)
        slots.forEachIndexed { i, slot ->
            if (slot != null) Button(eqSlotName(i, slot), { onPick(s.withSlot(i, slot)) }, primary = s.preset == eqSlotId(i) && !s.edited)
        }
    }
}

/** Debajo de la curva: el aviso de Bajo protegido, Restablecer y de dónde salió si se editó. */
@Composable
private fun EqCurveFooter(s: EqSettings, device: EqDevice, onChange: (EqSettings) -> Unit) {
    val c = LocalColors.current
    val capped = s.protect && device.bands.hz.any { s.curve.at(it) > EqMath.capAt(it) + 0.05f }
    if (capped) Label(stringResource(R.string.ecualizador_capped), Typography.small, c.warning)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Button(stringResource(R.string.ecualizador_reset), { onChange(s.reset()) })
        if (s.edited) Label(stringResource(R.string.ecualizador_edited, eqCurrentName(s)), Typography.small, c.textMuted, modifier = Modifier.weight(1f))
    }
}

/** Alto de la curva (sin las etiquetas de arriba y abajo). */
private val EditorHeight = 240.dp
/** A la izquierda van los dB de la rejilla. */
private val EditorGutter = 44.dp
private val EditorPadY = 16.dp

/**
 * La curva: un punto por cada banda que trae la pantalla (sus frecuencias reales), que se arrastra
 * hacia arriba o hacia abajo desde cualquier parte de su columna (blanco grande para el dedo). Un
 * toque solo no cambia nada. Mientras se arrastra se oye ([onPreview]) y al soltar se guarda
 * ([onChange]). Lo de color es lo que de verdad suena (con bajos, voz y Bajo protegido).
 */
@Composable
private fun EqEditor(s: EqSettings, device: EqDevice, onPreview: (EqSettings) -> Unit, onChange: (EqSettings) -> Unit) {
    val c = LocalColors.current
    val bands = device.bands
    val n = bands.hz.size
    val range = remember(bands) { EqMath.editorRange(bands) }
    val lo = range.first
    val hi = range.second
    // Mientras se arrastra, la curva vive aquí (solo se redibuja la curva, no toda la pantalla).
    var dragging by remember { mutableStateOf<EqSettings?>(null) }
    var active by remember { mutableIntStateOf(-1) }
    val shown = dragging ?: s
    val base = remember(shown.curve, bands) { EqMath.map(shown.curve, bands) }
    val heard = remember(shown, device) { EqMath.compute(shown, bands, device.caps).heard }
    val current by rememberUpdatedState(s)
    val preview by rememberUpdatedState(onPreview)
    val change by rememberUpdatedState(onChange)
    val measurer = rememberTextMeasurer()
    val gridStyle = Typography.small.copy(color = c.textMuted)
    val gridLabels = remember(lo, hi, gridStyle) {
        gridSteps(lo, hi).filter { it.roundToInt() % 6 == 0 }.map { it to measurer.measure(EqMath.dbLabel(it), gridStyle) }
    }
    val valueStyle = (if (n > 7) Typography.small else Typography.body).copy(fontWeight = FontWeight.SemiBold)

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        // Arriba, cuánto sube o baja cada banda.
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(EditorGutter)) { Label("dB", Typography.small, c.textMuted, 1) }
            for (i in 0 until n) Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Label(EqMath.dbLabel(base[i]), valueStyle, if (i == active) c.accent else c.text, 1)
            }
        }
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(EditorHeight)
                .pointerInput(bands, lo, hi) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        down.consume()
                        val gutter = EditorGutter.toPx()
                        val padY = EditorPadY.toPx()
                        val column = (size.width - gutter) / n
                        val band = ((down.position.x - gutter) / column).toInt().coerceIn(0, n - 1)
                        var moved = false
                        try {
                            while (true) {
                                val finger = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                                if (!finger.pressed) break
                                finger.consume()
                                // Un toque no cambia nada: hay que arrastrar (así no se mueve sin querer).
                                if (!moved && abs(finger.position.y - down.position.y) < viewConfiguration.touchSlop) continue
                                moved = true
                                active = band
                                val plot = size.height - 2 * padY
                                val db = EqMath.snap(hi - (finger.position.y - padY) / plot * (hi - lo)).coerceIn(lo, hi)
                                val next = (dragging ?: current).withBand(bands, band, db)
                                if (next != dragging) {
                                    dragging = next
                                    preview(next)
                                }
                            }
                        } finally {
                            // Al soltar (o si el gesto se corta) queda lo último que se oyó.
                            dragging?.let { change(it) }
                            dragging = null
                            active = -1
                        }
                    }
                },
        ) {
            val gutter = EditorGutter.toPx()
            val padY = EditorPadY.toPx()
            val plot = size.height - 2 * padY
            val column = (size.width - gutter) / n
            fun y(db: Float) = padY + (hi - db.coerceIn(lo, hi)) / (hi - lo) * plot
            fun x(i: Int) = gutter + column * (i + 0.5f)
            if (active in 0 until n) {
                drawRoundRect(c.surfaceHigh, Offset(gutter + column * active, 0f), Size(column, size.height), CornerRadius(10.dp.toPx()))
            }
            for (g in gridSteps(lo, hi)) {
                val zero = g == 0f
                drawLine(if (zero) c.textMuted else c.line, Offset(gutter, y(g)), Offset(size.width, y(g)), strokeWidth = (if (zero) 1.5f else 1f).dp.toPx())
            }
            for ((g, text) in gridLabels) drawText(text, topLeft = Offset(gutter - text.size.width - 8.dp.toPx(), y(g) - text.size.height / 2f))
            // Lo que suena: relleno suave hasta el 0.
            val fill = smoothCurve(heard.mapIndexed { i, v -> Offset(x(i), y(v)) }, gutter, size.width)
            fill.lineTo(size.width, y(0f))
            fill.lineTo(gutter, y(0f))
            fill.close()
            drawPath(fill, c.accent.copy(alpha = 0.18f))
            // La curva que se arrastra y sus puntos.
            val points = base.mapIndexed { i, v -> Offset(x(i), y(v)) }
            drawPath(smoothCurve(points, gutter, size.width), c.accent, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            points.forEachIndexed { i, p ->
                if (i == active) {
                    drawCircle(c.accent, 16.dp.toPx(), p)
                } else {
                    drawCircle(c.accent, 12.dp.toPx(), p)
                    drawCircle(c.surface, 8.dp.toPx(), p)
                }
            }
        }
        // Abajo, la frecuencia de cada banda.
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(EditorGutter)) { Label("Hz", Typography.small, c.textMuted, 1) }
            for (i in 0 until n) Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Label(EqMath.freqLabel(bands.hz[i]), Typography.small, c.textMuted, 1)
            }
        }
    }
}

/** Las líneas de la rejilla: cada 3 dB. */
private fun gridSteps(lo: Float, hi: Float): List<Float> {
    val out = ArrayList<Float>()
    var g = ceil(lo / 3f) * 3f
    while (g <= hi + 0.001f) {
        out += g
        g += 3f
    }
    return out
}

/**
 * Curva suave por los puntos, plana hasta los bordes. Cúbica monótona (Fritsch–Carlson): entre dos
 * puntos no inventa subidas ni bajadas que el ecualizador no hace.
 */
private fun smoothCurve(points: List<Offset>, left: Float, right: Float): Path {
    val path = Path()
    if (points.isEmpty()) return path
    val n = points.size
    path.moveTo(left, points[0].y)
    path.lineTo(points[0].x, points[0].y)
    if (n > 1) {
        val d = FloatArray(n - 1) { (points[it + 1].y - points[it].y) / (points[it + 1].x - points[it].x) }
        val m = FloatArray(n) { i -> if (i == 0 || i == n - 1 || d[i - 1] * d[i] <= 0f) 0f else (d[i - 1] + d[i]) / 2f }
        for (i in 0 until n - 1) {
            if (d[i] == 0f) {
                m[i] = 0f
                m[i + 1] = 0f
                continue
            }
            val a = m[i] / d[i]
            val b = m[i + 1] / d[i]
            val h = a * a + b * b
            if (h > 9f) {
                val t = 3f / sqrt(h)
                m[i] = t * a * d[i]
                m[i + 1] = t * b * d[i]
            }
        }
        for (i in 0 until n - 1) {
            val p0 = points[i]
            val p1 = points[i + 1]
            val dx = (p1.x - p0.x) / 3f
            path.cubicTo(p0.x + dx, p0.y + m[i] * dx, p1.x - dx, p1.y - m[i + 1] * dx, p1.x, p1.y)
        }
    }
    path.lineTo(right, points[n - 1].y)
    return path
}

/**
 * Deslizador de 0 a 100 % con área grande para el dedo (un toque también lo pone ahí). Mientras se
 * arrastra llega [onPreview] y al soltar [onChange].
 */
@Composable
private fun EqSlider(title: String, value: Float, onPreview: (Float) -> Unit, onChange: (Float) -> Unit) {
    val c = LocalColors.current
    val preview by rememberUpdatedState(onPreview)
    val change by rememberUpdatedState(onChange)
    var dragged by remember { mutableStateOf<Float?>(null) }
    val shown = dragged ?: value
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Label(title, Typography.body, modifier = Modifier.weight(1f))
            Label("${(shown * 100).roundToInt()} %", Typography.body.copy(fontWeight = FontWeight.SemiBold), if (shown > 0f) c.accent else c.textMuted, 1)
        }
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(52.dp)
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        down.consume()
                        val knob = 14.dp.toPx()
                        fun at(x: Float) = (((x - knob) / (size.width - 2 * knob)).coerceIn(0f, 1f) * 100).roundToInt() / 100f
                        var v = at(down.position.x)
                        dragged = v
                        preview(v)
                        try {
                            while (true) {
                                val finger = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                                if (!finger.pressed) break
                                finger.consume()
                                val next = at(finger.position.x)
                                if (next != v) {
                                    v = next
                                    dragged = v
                                    preview(v)
                                }
                            }
                        } finally {
                            change(v)
                            dragged = null
                        }
                    }
                },
        ) {
            val knob = 14.dp.toPx()
            val track = 8.dp.toPx()
            val cy = size.height / 2
            val x = knob + shown.coerceIn(0f, 1f) * (size.width - 2 * knob)
            drawRoundRect(c.surfaceHigh, Offset(knob, cy - track / 2), Size(size.width - 2 * knob, track), CornerRadius(track / 2))
            drawRoundRect(c.accent, Offset(knob, cy - track / 2), Size(x - knob, track), CornerRadius(track / 2))
            drawCircle(c.accent, knob, Offset(x, cy))
            drawCircle(Color.White, knob - 4.dp.toPx(), Offset(x, cy))
        }
    }
}

/** "Mis ajustes": elegir el espacio, ponerle nombre y guardar cómo suena ahora. */
@Composable
private fun EqSaveSlots(eq: EqEngine) {
    val c = LocalColors.current
    val slots = eq.slots
    var target by remember { mutableIntStateOf(eq.settings.slot ?: slots.indexOfFirst { it == null }.takeIf { it >= 0 } ?: 0) }
    var name by remember(target) { mutableStateOf(slots.getOrNull(target)?.name.orEmpty()) }
    var saved by remember { mutableStateOf(false) }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        for (i in 0 until EQ_SLOTS) {
            val title = eqSlotName(i, slots.getOrNull(i))
            Button(
                if (slots.getOrNull(i) == null) stringResource(R.string.ecualizador_slot_empty, title) else title,
                { target = i; saved = false },
                primary = i == target,
            )
        }
    }
    TextInput(name, { name = it.take(30); saved = false }, stringResource(R.string.ecualizador_slot_name_hint), Modifier.fillMaxWidth())
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        val shownName = name.trim().ifEmpty { stringResource(R.string.ecualizador_slot_default, target + 1) }
        Button(stringResource(R.string.ecualizador_save_to, shownName), { eq.saveSlot(target, name); saved = true }, primary = true)
        if (saved) Label(stringResource(R.string.ecualizador_saved), Typography.body, c.accent)
    }
}

/** Qué suena con este ecualizador y el atajo al de fábrica (radio FM y Bluetooth de la pantalla). */
@Composable
private fun EqFactory() {
    val context = LocalContext.current
    val c = LocalColors.current
    Label(stringResource(R.string.ecualizador_explain), Typography.body)
    Label(stringResource(R.string.ecualizador_explain_tip), Typography.small, c.textMuted)
    val lookup by produceState(EqEngine.factory) {
        if (value == null) value = withContext(Dispatchers.IO) { EqEngine.findFactory(context.applicationContext) }
    }
    val found = lookup
    val app = found?.app
    when {
        found == null -> Label(stringResource(R.string.ecualizador_factory_searching), Typography.small, c.textMuted)
        app == null -> Label(stringResource(R.string.ecualizador_factory_none), Typography.small, c.textMuted)
        else -> Button(
            app.label?.let { stringResource(R.string.ecualizador_factory_open_named, it) } ?: stringResource(R.string.ecualizador_factory_open),
            { runCatching { context.startActivity(app.intent) } },
            primary = true,
        )
    }
}

/** Dónde se aplica, sonido envolvente y los detalles técnicos (para revisar la pantalla). */
@Composable
private fun EqAdvanced(s: EqSettings, device: EqDevice, eq: EqEngine) {
    val c = LocalColors.current
    Label(stringResource(R.string.ecualizador_mode), Typography.label, c.textMuted)
    Segmented(
        listOf(
            EqMode.AUTO to stringResource(R.string.ecualizador_mode_auto),
            EqMode.GLOBAL to stringResource(R.string.ecualizador_mode_global),
            EqMode.APPS to stringResource(R.string.ecualizador_mode_apps),
        ),
        s.mode,
        { eq.update(s.copy(mode = it)) },
        check = true,
    )
    Label(stringResource(R.string.ecualizador_mode_help), Typography.small, c.textMuted)
    if (device.caps.virtualizer) {
        SwitchRow(stringResource(R.string.ecualizador_virtualizer), s.virtualizer, { eq.update(s.copy(virtualizer = it)) }, stringResource(R.string.ecualizador_virtualizer_help))
    }
    var open by remember { mutableStateOf(false) }
    Button(stringResource(if (open) R.string.ecualizador_details_hide else R.string.ecualizador_details_show), { open = !open })
    if (open) EqDetails(s, device)
}

@Composable
private fun EqDetails(s: EqSettings, device: EqDevice) {
    val c = LocalColors.current
    val st = EqEngine.status
    val b = device.bands
    val out = remember(s, device) { EqMath.compute(s, b, device.caps) }
    val small = Typography.small
    Label(stringResource(R.string.ecualizador_details_bands, b.hz.joinToString(" · ") { EqMath.freqLabel(it) }), small, c.textMuted)
    Label(stringResource(R.string.ecualizador_details_range, EqMath.dbLabel(b.minDb), EqMath.dbLabel(b.maxDb)), small, c.textMuted)
    Label(stringResource(R.string.ecualizador_details_effects, device.effects.joinToString(", ").ifEmpty { "—" }), small, c.textMuted)
    Label(stringResource(R.string.ecualizador_details_output, signed(-out.headroomDb), signed(out.loudnessMb / 100f), out.bassStrength), small, c.textMuted)
    val sessions = st.sessions.joinToString(", ").ifEmpty { stringResource(R.string.ecualizador_details_none) }
    Label(stringResource(R.string.ecualizador_details_sessions, sessions), small, c.textMuted)
    st.detail?.let { Label(stringResource(R.string.ecualizador_details_error, it), small, c.textMuted) }
}

/** "+0.9" / "−1.5". */
private fun signed(v: Float): String {
    val text = String.format(Locale.US, "%.1f", abs(v))
    return if (text == "0.0") "0" else (if (v > 0) "+" else "−") + text
}
