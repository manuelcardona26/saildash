// SailDash: Ajustes → Sonido (el ecualizador de las apps), en el diseño C (catalogo/ecualizador).
package com.irvingguerra.litedash

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.irvingguerra.litedash.core.ui.Button
import com.irvingguerra.litedash.core.ui.Icon
import com.irvingguerra.litedash.core.ui.Label
import com.irvingguerra.litedash.core.ui.LocalColors
import com.irvingguerra.litedash.core.ui.Segmented
import com.irvingguerra.litedash.core.ui.SwitchRow
import com.irvingguerra.litedash.core.ui.TextInput
import com.irvingguerra.litedash.core.ui.Typography
import com.irvingguerra.litedash.core.ui.ice.IceButton
import com.irvingguerra.litedash.core.ui.ice.IceIcons
import com.irvingguerra.litedash.core.ui.ice.IcePill
import com.irvingguerra.litedash.core.ui.ice.IceSlider
import com.irvingguerra.litedash.core.ui.ice.IceText
import com.irvingguerra.litedash.core.ui.ice.IceText2
import com.irvingguerra.litedash.core.ui.ice.IceToggle
import com.irvingguerra.litedash.core.ui.ice.IceType
import com.irvingguerra.litedash.core.ui.ice.LocalIce
import com.irvingguerra.litedash.core.ui.ice.Outfit
import com.irvingguerra.litedash.core.ui.ice.RobotoText
import com.irvingguerra.litedash.core.ui.ice.iceCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.abs
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
 * Ajustes → Sonido, como el mockup: arriba cómo va y el interruptor; la tarjeta con los estilos (y el
 * grupo Urbano) y la curva que se arrastra; Bajo protegido y las dos barras; y el atajo al de fábrica
 * para la radio y el Bluetooth. Debajo, "Mis ajustes" y lo avanzado.
 */
@Composable
internal fun EqSettingsSection() {
    if (currentTab(SOUND_TABS) == SOUND_TAB_ZONE) return SoundZoneSection()
    val context = LocalContext.current
    val ice = LocalIce.current
    val eq = remember { EqEngine.get(context) }
    // Las bandas de la pantalla (una vez; quedan guardadas para la próxima).
    LaunchedEffect(Unit) { eq.probe() }
    val s = eq.settings
    val device = eq.device

    if (device != null) {
        val active = s.enabled
        // .cv: los estilos y la curva.
        Column(Modifier.fillMaxWidth().iceCard().padding(horizontal = 16.dp, vertical = 14.dp)) {
            EqPresetChips(s, eq.slots) { eq.update(it.copy(enabled = true)) }
            Spacer(Modifier.height(10.dp))
            EqEditor(s, device, { eq.preview(it) }, { eq.update(it) }, dim = !active)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            // .q: Bajo protegido y las dos barras.
            Column(Modifier.width(396.dp).height(164.dp).iceCard().padding(horizontal = 20.dp, vertical = 6.dp)) {
                QuickRow(divider = true) {
                    Column(Modifier.weight(1f)) {
                        IceText(stringResource(R.string.ecualizador_protect), IceText2.rowTitle, ice.ink)
                        IceText(stringResource(R.string.p2_eq_protect_sub), IceType.small, ice.ink3, modifier = Modifier.padding(top = 3.dp))
                    }
                    IceToggle(s.protect, onChange = { eq.update(s.copy(protect = it)) })
                }
                QuickRow(divider = true) {
                    IceText(stringResource(R.string.ecualizador_bass), IceText2.rowTitle, ice.ink, modifier = Modifier.width(150.dp))
                    IceSlider(s.bass, { eq.update(s.withBass(it)) }, Modifier.weight(1f), onPreview = { eq.preview(s.withBass(it)) })
                    IceText((s.bass * 100).roundToInt().toString(), IceText2.rowTitle.copy(fontSize = 15.sp, textAlign = androidx.compose.ui.text.style.TextAlign.End), ice.ice, modifier = Modifier.width(40.dp))
                }
                QuickRow(divider = false) {
                    IceText(stringResource(R.string.ecualizador_voice), IceText2.rowTitle, ice.ink, modifier = Modifier.width(150.dp))
                    IceSlider(s.voice, { eq.update(s.withVoice(it)) }, Modifier.weight(1f), onPreview = { eq.preview(s.withVoice(it)) })
                    IceText((s.voice * 100).roundToInt().toString(), IceText2.rowTitle.copy(fontSize = 15.sp, textAlign = androidx.compose.ui.text.style.TextAlign.End), ice.ice, modifier = Modifier.width(40.dp))
                }
            }
            // .fx: la radio FM y el Bluetooth van por el de fábrica.
            FactoryCard(Modifier.width(288.dp).height(164.dp))
        }
        SettingsCard(stringResource(R.string.ecualizador_slots_title), stringResource(R.string.ecualizador_slots_help)) { EqSaveSlots(eq) }
        SettingsCard(stringResource(R.string.ecualizador_advanced)) { EqAdvanced(s, device, eq) }
    } else if (eq.unsupported == null) {
        SettingsCard { Label(stringResource(R.string.ecualizador_loading), Typography.body, LocalColors.current.textMuted) }
        FactoryCard(Modifier.fillMaxWidth().height(164.dp))
    } else {
        FactoryCard(Modifier.fillMaxWidth().height(164.dp))
    }
}

/**
 * Lo de la derecha del título en Ajustes → Sonido (`.mh`): "Ecualizador de las apps · Activo en todo
 * el sonido" y el interruptor.
 */
@Composable
internal fun androidx.compose.foundation.layout.RowScope.EqHeaderTrailing() {
    val context = LocalContext.current
    val ice = LocalIce.current
    val eq = remember { EqEngine.get(context) }
    val s = eq.settings
    if (currentTab(SOUND_TABS) == SOUND_TAB_ZONE) return ZoneHeaderTrailing()
    // Con las pestañas al lado no cabe "Ecualizador de las apps ·": solo cómo va y el interruptor.
    EqStatusLine(Modifier.weight(1f).padding(start = 14.dp, top = 22.dp), lead = false)
    IceToggle(s.enabled, onChange = { eq.update(s.copy(enabled = it)) })
    Spacer(Modifier.width(14.dp))
    SoundTabs()
}

/** `.qr`: renglón de 54 dp con la raya de abajo. */
@Composable
private fun QuickRow(divider: Boolean, content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    val line = LocalIce.current.line2
    Row(
        Modifier
            .fillMaxWidth()
            .height(54.dp)
            .then(if (divider) Modifier.drawBehindLine(line) else Modifier),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        content = content,
    )
}

private fun Modifier.drawBehindLine(color: Color) = this.then(
    Modifier.drawBehind { drawRect(color, Offset(0f, size.height - 1.dp.toPx()), androidx.compose.ui.geometry.Size(size.width, 1.dp.toPx())) },
)

/** `.fx`: por qué la radio y el Bluetooth no pasan por aquí, y el botón al ecualizador de fábrica. */
@Composable
private fun FactoryCard(modifier: Modifier) {
    val context = LocalContext.current
    val ice = LocalIce.current
    val lookup by produceState(EqEngine.factory) {
        if (value == null) value = withContext(Dispatchers.IO) { EqEngine.findFactory(context.applicationContext) }
    }
    val app = lookup?.app
    Box(modifier.iceCard().padding(horizontal = 18.dp, vertical = 16.dp)) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(IceIcons.radio, Color(0xFFFFC56B), 22.dp)
                Spacer(Modifier.width(10.dp))
                IceText(stringResource(R.string.p2_eq_fx_title), IceText2.h3.copy(fontSize = 16.sp), ice.title)
            }
            IceText(stringResource(R.string.p2_eq_fx_text), IceText2.para, ice.ink2, maxLines = 3, modifier = Modifier.padding(top = 9.dp))
        }
        IceButton(
            if (lookup == null) stringResource(R.string.ecualizador_factory_searching) else if (app == null) stringResource(R.string.p2_eq_fx_none) else stringResource(R.string.p2_eq_fx_open),
            { app?.let { runCatching { context.startActivity(it.intent) } } },
            Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
            icon = IceIcons.openExternal,
            height = 46.dp,
            padding = 12.dp,
            enabled = app != null,
        )
    }
}

/** Cómo va: "Ecualizador de las apps · Activo en todo el sonido" (lo bueno en menta; lo malo en rojo). */
@Composable
private fun EqStatusLine(modifier: Modifier, lead: Boolean = true) {
    val ice = LocalIce.current
    val st = EqEngine.status
    val unsupported = EqEngine.unsupported
    val (text, color) = when {
        st.state == EqState.OFF && unsupported != null ->
            stringResource(R.string.ecualizador_status_unsupported, stringResource(eqReasonText(unsupported))) to ice.danger
        st.state == EqState.OFF -> stringResource(R.string.p2_eq_off) to ice.ink3
        st.state == EqState.STARTING -> stringResource(R.string.ecualizador_status_starting) to ice.ink2
        st.state == EqState.GLOBAL -> stringResource(R.string.p2_eq_global) to Color(0xFF8FF0CF)
        st.state == EqState.APPS && st.apps.isEmpty() -> stringResource(R.string.ecualizador_status_apps_waiting) to Color(0xFF8FF0CF)
        st.state == EqState.APPS -> stringResource(R.string.ecualizador_status_apps, st.apps.joinToString(", ")) to Color(0xFF8FF0CF)
        else -> stringResource(R.string.ecualizador_status_unsupported, stringResource(eqReasonText(st.reason))) to ice.danger
    }
    val leadText = stringResource(R.string.p2_eq_lead)
    IceText(
        buildAnnotatedString {
            if (lead) {
                append(leadText)
                append(" · ")
            }
            withStyle(SpanStyle(color = color)) { append(text.trimEnd('.')) }
        },
        IceText2.sub, ice.ink2, maxLines = 1, modifier = modifier.padding(end = 12.dp),
    )
}

private fun eqReasonText(reason: EqReason?) = when (reason) {
    EqReason.NO_EFFECT -> R.string.ecualizador_reason_no_effect
    EqReason.DENIED -> R.string.ecualizador_reason_denied
    EqReason.GLOBAL_FAILED -> R.string.ecualizador_reason_global
    EqReason.NO_CONTROL -> R.string.ecualizador_reason_control
    EqReason.FAILED, null -> R.string.ecualizador_reason_failed
}

internal fun eqPresetName(id: String) = when (id) {
    EqPresets.TECHNO -> R.string.p2_eq_techno
    EqPresets.DEEP_BASS -> R.string.ecualizador_preset_deep_bass
    EqPresets.VOICE -> R.string.ecualizador_preset_voice
    EqPresets.ROCK -> R.string.ecualizador_preset_rock
    EqPresets.PODCAST -> R.string.p2_eq_podcast
    EqPresets.NIGHT -> R.string.ecualizador_preset_night
    EqPresets.REGGAETON -> R.string.p2_eq_reggaeton
    EqPresets.URBAN_TECHNO -> R.string.p2_eq_techno
    EqPresets.ELECTRONICA -> R.string.p2_eq_electronica
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

/** Para la tarjeta de sonido de la música: "Sonido: Techno" y "Bajo protegido · voz clara". */
@Composable
internal fun eqSummary(): Pair<String, String> {
    val context = LocalContext.current
    val eq = remember { EqEngine.get(context) }
    val s = eq.settings
    val name = if (s.enabled) eqCurrentName(s) else stringResource(R.string.p2_eq_off_short)
    val parts = buildList {
        if (s.enabled && s.protect) add(stringResource(R.string.p2_eq_protect_short))
        if (s.enabled && s.voice > 0.05f) add(stringResource(R.string.p2_eq_voice_short))
        if (s.enabled && s.bass > 0.05f) add(stringResource(R.string.p2_eq_bass_short))
        if (!s.enabled) add(stringResource(R.string.p2_eq_tap_to_turn_on))
    }
    return stringResource(R.string.p2_eq_sound_title, name) to parts.joinToString(" · ")
}

/**
 * `.chips`: los estilos de siempre y "Mis ajustes" en una fila (se desliza), y el grupo Urbano
 * aparte debajo. El elegido, en hielo.
 */
@Composable
private fun EqPresetChips(s: EqSettings, slots: List<EqSlot?>, onPick: (EqSettings) -> Unit) {
    val ice = LocalIce.current
    val chip = IceText2.pill.copy(fontSize = 14.sp)
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        for (p in EqPresets.normal) IcePill(stringResource(eqPresetName(p.id)), s.preset == p.id && !s.edited, { onPick(s.withPreset(p)) }, padding = 14.dp, textStyle = chip)
        // "Mis ajustes": el último guardado (o el primero que haya); sin ninguno, nada que elegir todavía.
        val saved = slots.withIndex().filter { it.value != null }
        val mine = s.slot != null && !s.edited
        IcePill(stringResource(R.string.p2_eq_mine), mine, {
            val pick = saved.firstOrNull { it.index == s.slot } ?: saved.firstOrNull()
            if (pick != null) onPick(s.withSlot(pick.index, pick.value!!))
        }, padding = 14.dp, textStyle = chip)
    }
    Spacer(Modifier.height(8.dp))
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        IceText(stringResource(R.string.p2_eq_urban).uppercase(), IceText2.cap.copy(fontSize = 12.sp), ice.ink3, modifier = Modifier.padding(start = 4.dp, end = 6.dp))
        for (p in EqPresets.urban) IcePill(stringResource(eqPresetName(p.id)), s.preset == p.id && !s.edited, { onPick(s.withPreset(p)) }, padding = 14.dp, textStyle = chip, height = 40.dp)
        if (s.edited) IceText(stringResource(R.string.ecualizador_edited, eqCurrentName(s)), IceType.small, ice.ink3, modifier = Modifier.padding(start = 8.dp).clickable { onPick(s.reset().copy(enabled = s.enabled)) })
    }
}

/** El rango que se dibuja: ±12 dB como el mockup (o el de la pantalla si es menor). */
private const val GRAPH_DB = 12f

/**
 * `svg.graph` (682 × 150 a escala del ancho): rejilla de ±12 dB con sus rótulos, la curva brillante con
 * relleno, un punto blanco por cada banda real de la pantalla (lo que suena, con su halo) y su valor;
 * con Bajo protegido, la raya dorada del "tope" y, punteado, a dónde se pidió. Se arrastra cada punto
 * hacia arriba o abajo desde su columna: mientras, se oye ([onPreview]); al soltar, se guarda ([onChange]).
 */
@Composable
private fun EqEditor(s: EqSettings, device: EqDevice, onPreview: (EqSettings) -> Unit, onChange: (EqSettings) -> Unit, dim: Boolean) {
    val bands = device.bands
    val n = bands.hz.size
    val range = remember(bands) { EqMath.editorRange(bands) }
    val lo = maxOf(range.first, -GRAPH_DB)
    val hi = minOf(range.second, GRAPH_DB)
    var dragging by remember { mutableStateOf<EqSettings?>(null) }
    var active by remember { mutableIntStateOf(-1) }
    val shown = dragging ?: s
    val base = remember(shown.curve, bands) { EqMath.map(shown.curve, bands) }
    val heard = remember(shown, device) { EqMath.compute(shown, bands, device.caps).heard }
    val current by rememberUpdatedState(s)
    val preview by rememberUpdatedState(onPreview)
    val change by rememberUpdatedState(onChange)
    val measurer = rememberTextMeasurer()
    val gridStyle = TextStyle(fontFamily = RobotoText, fontSize = 13.sp, color = Color(196, 216, 238).copy(alpha = 0.5f))
    val valueStyle = TextStyle(fontFamily = Outfit, fontSize = 14.sp, color = Color(0xFFEEF6FF))
    val capStyle = TextStyle(fontFamily = RobotoText, fontSize = 13.sp, color = Color(0xFFFFD88A).copy(alpha = 0.9f))
    val tope = stringResource(R.string.p2_eq_cap)
    // x de cada banda: de 92 a 590 sobre 682 (como el mockup), escalado al ancho.
    fun xOf(i: Int, w: Float): Float {
        val k = w / 682f
        return if (n == 1) w / 2 else (92f + i * (498f / (n - 1))) * k
    }
    Column(Modifier.fillMaxWidth()) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(150.dp)
                .pointerInput(bands, lo, hi) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        down.consume()
                        val band = (0 until n).minByOrNull { abs(xOf(it, size.width.toFloat()) - down.position.x) } ?: 0
                        var moved = false
                        try {
                            while (true) {
                                val finger = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                                if (!finger.pressed) break
                                finger.consume()
                                if (!moved && abs(finger.position.y - down.position.y) < viewConfiguration.touchSlop) continue
                                moved = true
                                active = band
                                val k = size.height / 150f
                                val db = EqMath.snap(hi - (finger.position.y - 12f * k) / (126f * k) * (hi - lo)).coerceIn(lo, hi)
                                val next = (dragging ?: current).withBand(bands, band, db)
                                if (next != dragging) {
                                    dragging = next
                                    preview(next)
                                }
                            }
                        } finally {
                            dragging?.let { change(it) }
                            dragging = null
                            active = -1
                        }
                    }
                },
        ) {
            val k = size.height / 150f
            val left = 40f * size.width / 682f
            fun y(db: Float) = 12f * k + (hi - db.coerceIn(lo, hi)) / (hi - lo) * 126f * k
            val line = Color(0xFFBFE1FF)
            // La rejilla: ±12, ±6 (tenues) y el 0 punteado; los rótulos a la izquierda.
            for (g in listOf(12f, 6f, -6f, -12f)) if (g in lo..hi) drawLine(line.copy(alpha = 0.07f), Offset(left, y(g)), Offset(size.width, y(g)), 1.dp.toPx())
            drawLine(line.copy(alpha = 0.22f), Offset(left, y(0f)), Offset(size.width, y(0f)), 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 5.dp.toPx())))
            for (g in listOf(12f, 6f, 0f, -6f, -12f)) if (g in lo..hi) {
                val t = measurer.measure(EqMath.dbLabel(g), gridStyle)
                drawText(t, topLeft = Offset(0f, y(g) - t.size.height / 2f))
            }
            for (i in 0 until n) drawLine(line.copy(alpha = 0.06f), Offset(xOf(i, size.width), 8f * k), Offset(xOf(i, size.width), 142f * k), 1.dp.toPx())
            // Lo que se ve en cada banda: con Bajo protegido, el tope; si no, la curva.
            val dots = (0 until n).map { i -> if (s.protect) minOf(base[i], EqMath.capAt(bands.hz[i])) else base[i] }
            val pts = dots.mapIndexed { i, v -> Offset(xOf(i, size.width), y(v)) }
            val x0 = 44f * size.width / 682f
            val x1 = 668f * size.width / 682f
            val curve = smoothCurve(pts, x0, x1)
            val alpha = if (dim) 0.4f else 1f
            // Relleno hasta el 0 (lo que de verdad suena, con bajos y voz).
            val heardPts = heard.mapIndexed { i, v -> Offset(xOf(i, size.width), y(v)) }
            val fill = smoothCurve(heardPts, x0, x1).apply {
                lineTo(x1, y(0f)); lineTo(x0, y(0f)); close()
            }
            drawPath(fill, Brush.verticalGradient(listOf(Color(0xFF7CC6FF).copy(alpha = 0.38f * alpha), Color(0xFF7CC6FF).copy(alpha = 0.02f)), startY = y(hi), endY = y(0f)))
            // El tope de Bajo protegido donde la curva lo toca.
            if (s.protect) for (i in 0 until n) {
                val cap = EqMath.capAt(bands.hz[i])
                if (base[i] < cap - 0.05f) continue
                val xi = xOf(i, size.width)
                val half = if (n > 1) (xOf(1, size.width) - xOf(0, size.width)) / 2f + 4f * k else 60f * k
                drawLine(Color(0xFFFFD88A).copy(alpha = 0.7f), Offset(maxOf(x0, xi - half), y(cap)), Offset(xi + half, y(cap)), 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx())))
                val t = measurer.measure(tope, capStyle)
                drawText(t, topLeft = Offset(xi + 22f * k, y(cap) - 5.6f * k - t.size.height))
                if (base[i] > cap + 0.25f) drawCircle(
                    Color(0xFFFFD88A).copy(alpha = 0.75f), 9.dp.toPx(), Offset(xi, y(base[i])),
                    style = Stroke(1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 3.dp.toPx()))),
                )
            }
            // La curva brillante (el brillo: la misma más ancha y tenue debajo).
            val glow = Brush.horizontalGradient(listOf(Color(0xFF7CC6FF), Color(0xFFE3F4FF), Color(0xFF9FD4FF)))
            drawPath(curve, glow, alpha = 0.22f * alpha, style = Stroke(8.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            drawPath(curve, glow, alpha = alpha, style = Stroke(2.6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            pts.forEachIndexed { i, p ->
                drawCircle(Color(0xFFA9DCFF).copy(alpha = if (i == active) 0.3f else 0.14f), 15.dp.toPx(), p)
                drawCircle(Color.White.copy(alpha = if (dim) 0.6f else 1f), 10.dp.toPx(), p)
                val v = dots[i]
                val capped = s.protect && base[i] >= EqMath.capAt(bands.hz[i]) - 0.05f
                val t = measurer.measure(EqMath.dbLabel(v).replace('.', ','), valueStyle)
                val below = v < 0f || capped
                val ty = if (below) p.y + 32f * k - t.size.height * 0.8f else p.y - 22f * k - t.size.height * 0.8f
                drawText(t, topLeft = Offset(p.x - t.size.width / 2f, ty))
            }
        }
        // .flab: la frecuencia de cada banda ("60 Hz", "3,6 kHz").
        Box(Modifier.fillMaxWidth().height(28.dp).padding(top = 8.dp)) {
            androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth()) {
                val w = maxWidth.value
                for (i in 0 until n) {
                    val hz = bands.hz[i]
                    val (num, unit) = if (hz >= 1000f) EqMath.freqLabel(hz).removeSuffix("k").replace('.', ',') to "kHz" else EqMath.freqLabel(hz) to "Hz"
                    val x = xOf(i, w)
                    Box(Modifier.offset((x - 45f).dp).width(90.dp), contentAlignment = Alignment.TopCenter) {
                        IceText(
                            buildAnnotatedString {
                                append(num)
                                append(' ')
                                withStyle(SpanStyle(color = LocalIce.current.ink3)) { append(unit) }
                            },
                            IceType.small, LocalIce.current.ink2,
                        )
                    }
                }
            }
        }
    }
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
