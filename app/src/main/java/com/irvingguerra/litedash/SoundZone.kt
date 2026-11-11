// SailDash: Ajustes → Sonido → Zona de sonido (diseño A "Cabina"): para quién suena el carro.
package com.irvingguerra.litedash

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.irvingguerra.litedash.core.ui.ice.IceButton
import com.irvingguerra.litedash.core.ui.ice.IceButtonKind
import com.irvingguerra.litedash.core.ui.ice.IceIcons
import com.irvingguerra.litedash.core.ui.ice.iceSelected
import com.irvingguerra.litedash.core.ui.ice.IceSegmented
import com.irvingguerra.litedash.core.ui.ice.IceText
import com.irvingguerra.litedash.core.ui.ice.IceText2
import com.irvingguerra.litedash.core.ui.ice.LocalIce
import com.irvingguerra.litedash.core.ui.ice.Outfit
import com.irvingguerra.litedash.core.ui.ice.iceCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/** Pasos a cada lado del centro (como el balance de las pantallas de carro: 7 y 7). */
const val ZONE_STEPS = 7

/**
 * Para quién suena: [balance] de −7 (solo izquierda, el conductor) a +7 (solo derecha, el copiloto)
 * y [fader] de −7 (solo adelante) a +7 (solo atrás). 0 y 0 = todo el carro parejo.
 */
data class SoundZone(val balance: Int = 0, val fader: Int = 0) {
    fun clamped() = SoundZone(balance.coerceIn(-ZONE_STEPS, ZONE_STEPS), fader.coerceIn(-ZONE_STEPS, ZONE_STEPS))

    /** Cuánto suena cada parlante (0 a 1): delantero izq., delantero der., trasero izq., trasero der. */
    fun levels(): FloatArray {
        val left = if (balance <= 0) 1f else 1f - balance / ZONE_STEPS.toFloat()
        val right = if (balance >= 0) 1f else 1f + balance / ZONE_STEPS.toFloat()
        val front = if (fader <= 0) 1f else 1f - fader / ZONE_STEPS.toFloat()
        val rear = if (fader >= 0) 1f else 1f + fader / ZONE_STEPS.toFloat()
        return floatArrayOf(left * front, right * front, left * rear, right * rear)
    }

    /** El atajo que es exactamente esta zona (o null: es una propia, "Mi zona"). */
    fun preset(): ZonePreset? = ZonePreset.entries.firstOrNull { it.zone == this }
}

/** Los atajos. En Colombia el conductor va a la izquierda. */
enum class ZonePreset(val zone: SoundZone) {
    ALL(SoundZone(0, 0)),
    DRIVER(SoundZone(-ZONE_STEPS, -ZONE_STEPS)),
    PASSENGER(SoundZone(ZONE_STEPS, -ZONE_STEPS)),
    FRONT(SoundZone(0, -ZONE_STEPS)),
    REAR(SoundZone(0, ZONE_STEPS)),
}

/**
 * Guarda la zona elegida y "Mi zona" (el último punto propio que se arrastró).
 *
 * Aplicarla: el balance y el adelante/atrás no los hace Android (solo manda dos canales) sino el
 * chip de sonido del carro (la MCU). Hasta saber cómo le habla la app Sonido de fábrica (lo dice el
 * Diagnóstico), [direct] es false y la pantalla ofrece abrir la de fábrica para ponerla igual.
 */
object SoundZoneStore {
    private const val PREFS = "sound_zone"
    private var loaded = false
    private var prefs: android.content.SharedPreferences? = null

    var zone by mutableStateOf(SoundZone())
        private set
    var mine by mutableStateOf<SoundZone?>(null)
        private set

    /** true cuando SailDash la puede mandar directo al chip de sonido (todavía no en esta pantalla). */
    val direct: Boolean get() = false

    fun load(context: Context): SoundZoneStore {
        if (loaded) return this
        val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs = p
        zone = SoundZone(p.getInt("balance", 0), p.getInt("fader", 0)).clamped()
        mine = if (p.contains("mine_balance")) SoundZone(p.getInt("mine_balance", 0), p.getInt("mine_fader", 0)).clamped() else null
        loaded = true
        return this
    }

    /** Elige [z]; si no es un atajo, queda también como "Mi zona". */
    fun choose(z: SoundZone) {
        val c = z.clamped()
        zone = c
        val e = prefs?.edit() ?: return
        e.putInt("balance", c.balance).putInt("fader", c.fader)
        if (c.preset() == null) {
            mine = c
            e.putInt("mine_balance", c.balance).putInt("mine_fader", c.fader)
        }
        e.apply()
    }
}

/** Las pestañas de Ajustes → Sonido. */
internal const val SOUND_TABS = "sound"
internal const val SOUND_TAB_EQ = 0
internal const val SOUND_TAB_ZONE = 1

/** "Ecualizador | Zona de sonido" (`.seg` a la derecha del título). */
@Composable
internal fun SoundTabs() {
    IceSegmented(
        listOf(SOUND_TAB_EQ to stringResource(R.string.zona_tab_eq), SOUND_TAB_ZONE to stringResource(R.string.zona_tab_zone)),
        currentTab(SOUND_TABS),
        { selectTab(SOUND_TABS, it) },
        height = 42.dp,
        padding = 15.dp,
    )
}

/** Izq 4 / Der 4 / Centro. */
@Composable
private fun balanceText(b: Int) = when {
    b < 0 -> stringResource(R.string.zona_left, -b)
    b > 0 -> stringResource(R.string.zona_right, b)
    else -> stringResource(R.string.zona_center)
}

/** Adel 3 / Atrás 3 / Centro. */
@Composable
private fun faderText(f: Int) = when {
    f < 0 -> stringResource(R.string.zona_front, -f)
    f > 0 -> stringResource(R.string.zona_rear, f)
    else -> stringResource(R.string.zona_center)
}

/**
 * La pestaña Zona de sonido: a la izquierda el Sail visto desde arriba con sus cuatro parlantes y el
 * punto que se arrastra; a la derecha los atajos, cómo quedó y cómo aplicarla.
 */
@Composable
internal fun SoundZoneSection() {
    val context = LocalContext.current
    val store = remember { SoundZoneStore.load(context) }
    var dragging by remember { mutableStateOf<SoundZone?>(null) }
    val shown = dragging ?: store.zone
    Row(Modifier.fillMaxWidth().height(388.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        CabinStage(shown, { dragging = it }, { dragging = null; store.choose(it) }, Modifier.width(330.dp).fillMaxSize())
        ZonePanel(store, shown, Modifier.weight(1f).fillMaxSize())
    }
}

@Composable
private fun ZonePanel(store: SoundZoneStore, shown: SoundZone, modifier: Modifier) {
    val context = LocalContext.current
    val ice = LocalIce.current
    val current = shown.preset()
    val mine = store.mine
    Column(modifier.iceCard().padding(horizontal = 16.dp, vertical = 14.dp)) {
        IceText(stringResource(R.string.zona_who).uppercase(), IceText2.cap.copy(fontSize = 11.sp), ice.ink3)
        Spacer(Modifier.height(10.dp))
        val items: List<Triple<String, SoundZone?, Boolean>> = listOf(
            Triple(stringResource(R.string.zona_all), ZonePreset.ALL.zone, current == ZonePreset.ALL),
            Triple(stringResource(R.string.zona_driver), ZonePreset.DRIVER.zone, current == ZonePreset.DRIVER),
            Triple(stringResource(R.string.zona_passenger), ZonePreset.PASSENGER.zone, current == ZonePreset.PASSENGER),
            Triple(stringResource(R.string.zona_front_only), ZonePreset.FRONT.zone, current == ZonePreset.FRONT),
            Triple(stringResource(R.string.zona_rear_only), ZonePreset.REAR.zone, current == ZonePreset.REAR),
            Triple(stringResource(R.string.zona_mine), mine, current == null),
        )
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            for (row in items.chunked(2)) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for ((name, z, on) in row) ZoneChip(name, (z ?: shown).levels(), on, enabled = z != null, Modifier.weight(1f)) { z?.let { store.choose(it) } }
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ZoneReadout(stringResource(R.string.zona_balance), balanceText(shown.balance), Modifier.weight(1f))
            ZoneReadout(stringResource(R.string.zona_fader), faderText(shown.fader), Modifier.weight(1f))
        }
        Spacer(Modifier.weight(1f))
        if (store.direct) {
            IceText(stringResource(R.string.zona_direct), IceText2.sub, ice.ink3, maxLines = 2)
        } else {
            val lookup by produceState(EqEngine.factory) {
                if (value == null) value = withContext(Dispatchers.IO) { EqEngine.findFactory(context.applicationContext) }
            }
            val app = lookup?.app
            IceText(stringResource(R.string.zona_factory_help), IceText2.sub.copy(fontSize = 12.sp), ice.ink3, maxLines = 2)
            Spacer(Modifier.height(8.dp))
            IceButton(
                if (app == null) stringResource(R.string.p2_eq_fx_none) else stringResource(R.string.zona_factory_open),
                { app?.let { runCatching { context.startActivity(it.intent) } } },
                Modifier.fillMaxWidth(),
                kind = IceButtonKind.PLAIN,
                icon = IceIcons.openExternal,
                height = 42.dp,
                padding = 12.dp,
                enabled = app != null,
            )
        }
    }
}

/** Un atajo (`.chip` de 48 dp): el iconito de la cabina y el nombre; el elegido en hielo. */
@Composable
private fun ZoneChip(name: String, levels: FloatArray, on: Boolean, enabled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val ice = LocalIce.current
    val shape = RoundedCornerShape(16.dp)
    val surface = if (on) Modifier.iceSelected(16.dp, Color(200, 232, 255).copy(alpha = 0.5f))
    else Modifier.clip(shape).background(if (ice.dark) Color(160, 205, 255).copy(alpha = 0.06f) else Color.White.copy(alpha = 0.55f)).border(1.dp, ice.line, shape)
    Row(
        modifier.height(48.dp).then(surface).clip(shape).clickable(enabled = enabled, onClick = onClick).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        ZoneMini(levels, on, dim = !enabled)
        IceText(name, IceText2.pill, if (on) ice.title else if (enabled) ice.ink else ice.ink3, maxLines = 1)
    }
}

@Composable
private fun ZoneReadout(label: String, value: String, modifier: Modifier) {
    val ice = LocalIce.current
    Column(
        modifier
            .iceCard(radius = 14.dp)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        IceText(label.uppercase(), IceText2.cap.copy(fontSize = 10.sp, letterSpacing = 0.12.em), ice.ink3, maxLines = 1)
        Spacer(Modifier.height(6.dp))
        IceText(value, IceText2.h2.copy(fontSize = 22.sp), ice.title, maxLines = 1)
    }
}

/** El iconito de 24 dp de cada atajo: la cabina en cuatro cuadros, encendidos según suena cada parlante. */
@Composable
private fun ZoneMini(levels: FloatArray, on: Boolean, dim: Boolean) {
    val ice = LocalIce.current
    val lit = if (ice.dark) Color(0xFFBFE6FF) else ice.ice2
    val off = (if (ice.dark) Color(200, 230, 255) else ice.ink3).copy(alpha = 0.14f)
    Canvas(Modifier.size(24.dp)) {
        val r = 5.dp.toPx()
        drawRoundRect(lit.copy(alpha = if (on) 0.55f else 0.35f), Offset(0.75.dp.toPx(), 0.75.dp.toPx()), Size(size.width - 1.5.dp.toPx(), size.height - 1.5.dp.toPx()), CornerRadius(r), style = Stroke(1.dp.toPx()))
        val cell = 8.dp.toPx()
        val pad = 3.5.dp.toPx()
        val gap = size.width - 2 * pad - 2 * cell
        for (i in 0 until 4) {
            val x = pad + (i % 2) * (cell + gap)
            val y = pad + (i / 2) * (cell + gap)
            val l = if (dim) 0f else levels[i]
            drawRoundRect(off, Offset(x, y), Size(cell, cell), CornerRadius(2.dp.toPx()))
            if (l > 0.02f) drawRoundRect(lit.copy(alpha = 0.25f + 0.75f * l), Offset(x, y), Size(cell, cell), CornerRadius(2.dp.toPx()))
        }
    }
}

// ---------------- La cabina ----------------

/** Medidas del lienzo de la cabina (dp), como el mockup A: 330 × 388. */
private const val STAGE_W = 330f
private const val CAR_X = 82f
private const val CAR_Y = 2f
private const val CAR_S = 0.91f
private val CAR_CX = CAR_X + 100f * CAR_S
private const val DOT_CY = 222f
private const val DOT_RX = 56f
private const val DOT_RY = 84f
private val SPEAKERS = listOf(Offset(CAR_CX - 82f, 150f), Offset(CAR_CX + 82f, 150f), Offset(CAR_CX - 82f, 300f), Offset(CAR_CX + 82f, 300f))

/** El Sail desde arriba en un lienzo de 200 × 400 (trompa arriba). */
private object SailTop {
    private fun p(d: String) = PathParser().parsePathString(d).toPath()
    val body = p("M100 6C150 6 176 18 180 52L186 120C188 150 188 250 186 300L182 362C178 388 150 396 100 396C50 396 22 388 18 362L14 300C12 250 12 150 14 120L20 52C24 18 50 6 100 6Z")
    val mirrors = p("M2 128l14 -6v20l-14 -4zM198 128l-14 -6v20l14 -4z")
    val windshield = p("M36 104C60 92 140 92 164 104L156 150C130 142 70 142 44 150Z")
    val rearGlass = p("M46 330C70 338 130 338 154 330L160 360C140 372 60 372 40 360Z")
    val hood = p("M30 60C60 46 140 46 170 60")
}

internal fun zoneToStage(z: SoundZone) = Offset(
    CAR_CX + z.balance / ZONE_STEPS.toFloat() * DOT_RX,
    DOT_CY + z.fader / ZONE_STEPS.toFloat() * DOT_RY,
)

internal fun stageToZone(x: Float, y: Float) = SoundZone(
    ((x - CAR_CX) / DOT_RX * ZONE_STEPS).roundToInt(),
    ((y - DOT_CY) / DOT_RY * ZONE_STEPS).roundToInt(),
).clamped()

@Composable
private fun CabinStage(zone: SoundZone, onDrag: (SoundZone) -> Unit, onDone: (SoundZone) -> Unit, modifier: Modifier) {
    val ice = LocalIce.current
    val measurer = rememberTextMeasurer()
    val labelStyle = TextStyle(fontFamily = Outfit, fontSize = 12.sp, color = Color(225, 240, 255))
    val hintStyle = TextStyle(fontFamily = Outfit, fontSize = 11.sp, letterSpacing = 0.18.em, color = ice.ink3)
    val names = listOf(stringResource(R.string.zona_spk_fl), stringResource(R.string.zona_spk_fr), stringResource(R.string.zona_spk_rl), stringResource(R.string.zona_spk_rr))
    val hint = stringResource(R.string.zona_drag).uppercase()
    val levels = zone.levels()
    val dot = zoneToStage(zone)
    Box(modifier.iceCard()) {
        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    val k = 1.dp.toPx()
                    fun at(o: Offset) = stageToZone(o.x / k, o.y / k)
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        down.consume()
                        var z = at(down.position)
                        onDrag(z)
                        try {
                            while (true) {
                                val finger = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                                if (!finger.pressed) break
                                finger.consume()
                                val next = at(finger.position)
                                if (next != z) { z = next; onDrag(z) }
                            }
                        } finally {
                            onDone(z)
                        }
                    }
                },
        ) {
            val k = 1.dp.toPx()
            // Brillo de fondo.
            drawCircle(Brush.radialGradient(listOf(Color(110, 170, 240).copy(alpha = 0.12f), Color.Transparent), Offset(size.width / 2, size.height / 2), size.minDimension * 0.6f))
            // El carro.
            translate(CAR_X * k, CAR_Y * k) {
                scale(CAR_S * k, Offset.Zero) { drawSail(this) }
            }
            // Guías del punto.
            val d = Offset(dot.x * k, dot.y * k)
            val guide = Color(190, 230, 255).copy(alpha = 0.22f)
            val dash = PathEffect.dashPathEffect(floatArrayOf(3 * k, 5 * k))
            drawLine(guide, Offset(d.x, 40 * k), Offset(d.x, 366 * k), 1 * k, pathEffect = dash)
            drawLine(guide, Offset(CAR_CX * k - 80 * k, d.y), Offset(CAR_CX * k + 80 * k, d.y), 1 * k, pathEffect = dash)
            // Parlantes.
            SPEAKERS.forEachIndexed { i, s ->
                val c = Offset(s.x * k, s.y * k)
                val l = levels[i]
                val a = 0.25f + 0.75f * l
                drawCircle(Color(140, 205, 255).copy(alpha = 0.12f * l), (18 + 16 * l) * k, c)
                drawCircle(Color(0xFF0B1626), 11 * k, c)
                drawCircle(Color(190, 230, 255).copy(alpha = a), 11 * k, c, style = Stroke(2 * k))
                drawCircle(Color(190, 230, 255).copy(alpha = a), 4.5f * k, c)
                val text = measurer.measure(names[i], labelStyle.copy(color = labelStyle.color.copy(alpha = 0.45f + 0.5f * l)))
                val left = i % 2 == 0
                val tx = if (left) c.x - 20 * k - text.size.width else c.x + 20 * k
                drawText(text, topLeft = Offset(tx, c.y - text.size.height / 2f))
            }
            // El punto de sonido.
            drawCircle(Brush.radialGradient(listOf(Color.White, Color(0xFFBFE6FF), Color(0x006FBFFF)), d, 46 * k), 46 * k, d, alpha = 0.55f)
            drawCircle(Color(200, 235, 255).copy(alpha = 0.5f), 24 * k, d, style = Stroke(1.2f * k))
            drawCircle(Color(0xFFEAF6FF), 15 * k, d)
            drawCircle(Color.White, 15 * k, d, style = Stroke(2 * k))
            // "Arrastra el punto".
            val h = measurer.measure(hint, hintStyle)
            drawText(h, topLeft = Offset((size.width - h.size.width) / 2f, size.height - h.size.height - 8 * k))
        }
    }
}

private fun drawSail(s: DrawScope) = with(s) {
    // Medidas en unidades del dibujo (200 × 400); el lienzo ya va escalado.
    val edge = Color(200, 230, 255)
    drawPath(SailTop.body, Brush.horizontalGradient(listOf(Color(0xFF1B2A40), Color(0xFF2A3D58), Color(0xFF1B2A40)), 0f, 200f))
    drawPath(SailTop.body, edge.copy(alpha = 0.55f), style = Stroke(1.6f))
    drawPath(SailTop.mirrors, Color(0xFF22344D))
    drawPath(SailTop.mirrors, edge.copy(alpha = 0.45f), style = Stroke(1.2f))
    val glass = Brush.verticalGradient(listOf(Color(159, 210, 255).copy(alpha = 0.35f), Color(76, 127, 184).copy(alpha = 0.12f)), 92f, 150f)
    drawPath(SailTop.windshield, glass)
    drawPath(SailTop.windshield, edge.copy(alpha = 0.35f), style = Stroke(1f))
    drawPath(SailTop.rearGlass, Brush.verticalGradient(listOf(Color(159, 210, 255).copy(alpha = 0.3f), Color(76, 127, 184).copy(alpha = 0.1f)), 330f, 372f))
    drawPath(SailTop.rearGlass, edge.copy(alpha = 0.3f), style = Stroke(1f))
    drawPath(SailTop.hood, edge.copy(alpha = 0.25f), style = Stroke(1f))
    // Cabina, asientos y timón (a la izquierda).
    drawRoundRect(Color(5, 10, 20).copy(alpha = 0.55f), Offset(38f, 150f), Size(124f, 182f), CornerRadius(16f))
    drawRoundRect(edge.copy(alpha = 0.12f), Offset(38f, 150f), Size(124f, 182f), CornerRadius(16f), style = Stroke(1f))
    for (x in listOf(50f, 104f)) {
        drawRoundRect(Color(160, 200, 240).copy(alpha = 0.10f), Offset(x, 166f), Size(46f, 56f), CornerRadius(12f))
        drawRoundRect(edge.copy(alpha = 0.28f), Offset(x, 166f), Size(46f, 56f), CornerRadius(12f), style = Stroke(1.3f))
        drawRoundRect(edge.copy(alpha = 0.28f), Offset(x + 4f, 210f), Size(38f, 9f), CornerRadius(4f), style = Stroke(1f))
    }
    drawRoundRect(Color(160, 200, 240).copy(alpha = 0.10f), Offset(48f, 258f), Size(104f, 58f), CornerRadius(14f))
    drawRoundRect(edge.copy(alpha = 0.28f), Offset(48f, 258f), Size(104f, 58f), CornerRadius(14f), style = Stroke(1.3f))
    drawCircle(edge.copy(alpha = 0.45f), 17f, Offset(73f, 160f), style = Stroke(2.4f))
    drawLine(edge.copy(alpha = 0.15f), Offset(100f, 20f), Offset(100f, 380f), 1f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 6f)))
}

/** Lo de la derecha del título en Zona de sonido: solo las pestañas. */
@Composable
internal fun RowScope.ZoneHeaderTrailing() {
    Spacer(Modifier.weight(1f))
    SoundTabs()
}

