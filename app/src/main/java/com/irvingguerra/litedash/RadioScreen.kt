// Radio FM del diseño C (catalogo/radio): la frecuencia en grande, el dial que se arrastra, ±0.1,
// buscar y las emisoras guardadas. Sintoniza con el sintonizador de fábrica (perfiles TopWay/NWD de
// FactoryRadio) cuando la pantalla lo deja; si no, por la app de la radio con las teclas de buscar, y
// lo dice. Nunca muestra como sintonizado lo que la radio no dijo.
package com.irvingguerra.litedash

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.irvingguerra.litedash.core.ui.Icon
import com.irvingguerra.litedash.core.ui.ice.BrandTitle
import com.irvingguerra.litedash.core.ui.ice.IceBackdrop
import com.irvingguerra.litedash.core.ui.ice.IceButton
import com.irvingguerra.litedash.core.ui.ice.IceButtonKind
import com.irvingguerra.litedash.core.ui.ice.IceDot
import com.irvingguerra.litedash.core.ui.ice.IceIconButton
import com.irvingguerra.litedash.core.ui.ice.IceIcons
import com.irvingguerra.litedash.core.ui.ice.IceSearchField
import com.irvingguerra.litedash.core.ui.ice.IceText
import com.irvingguerra.litedash.core.ui.ice.IceText2
import com.irvingguerra.litedash.core.ui.ice.IceType
import com.irvingguerra.litedash.core.ui.ice.LocalIce
import com.irvingguerra.litedash.core.ui.ice.Outfit
import com.irvingguerra.litedash.core.ui.ice.RobotoText
import com.irvingguerra.litedash.core.ui.ice.glass
import com.irvingguerra.litedash.core.ui.ice.iceCard
import com.irvingguerra.litedash.core.ui.ice.iceGlass
import com.irvingguerra.litedash.core.ui.ice.iceSelected
import com.irvingguerra.litedash.core.ui.ice.softGlow
import com.irvingguerra.litedash.feature.music.MusicRepository
import com.irvingguerra.litedash.feature.music.SourceKind
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs
import kotlin.math.roundToInt

/** Píxeles (dp) del dial por cada 0.1 MHz (76 por MHz, como el mockup). */
private const val DIAL_STEP = 7.6f

/** Lo que más se espera a que la radio diga la frecuencia nueva después de buscar. */
private const val SEEK_WAIT_MS = 2_500L

/** Cuántas búsquedas como mucho para llegar a una emisora o recorrer toda la banda. */
private const val MAX_SEEKS = 40

/** Las emisoras guardadas, en el teléfono (solo de SailDash). */
private class FmStore(context: Context) {
    private val prefs = context.getSharedPreferences("radio_fm", Context.MODE_PRIVATE)
    var stations by mutableStateOf(FmStations.parse(prefs.getString("stations", null)))
        private set
    /** La última frecuencia que la radio dijo de verdad (para abrir el dial ahí). */
    var last: Int? = prefs.getInt("last", 0).takeIf { it in FmStations.MIN..FmStations.MAX }
        set(v) {
            field = v
            prefs.edit().putInt("last", v ?: 0).apply()
        }

    fun set(list: List<FmStation>) {
        stations = list
        prefs.edit().putString("stations", FmStations.encode(list)).apply()
    }
}

/** La frecuencia que la radio está diciendo (su sesión, su aviso o el sintonizador de fábrica); null si no dice. */
private fun MusicRepository.tunedFm(): Int? {
    val s = radio.song ?: return null
    return FmStations.fromText(s.title) ?: FmStations.fromText(s.artist)
}

/** El nombre que da la radio (RDS), si no es la frecuencia misma. */
private fun MusicRepository.rdsName(): String {
    val s = radio.song ?: return ""
    return listOf(s.title, s.artist).firstOrNull { it.isNotBlank() && FmStations.fromText(it) == null && !it.equals("FM", true) }.orEmpty()
}

/**
 * La Radio FM a pantalla completa (encima de las páginas; el dock queda abajo con Radio FM encendido).
 * [onSound] abre Ajustes → Sonido.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun RadioScreen(d: Dependencies, close: () -> Unit, onSound: () -> Unit) {
    val ice = LocalIce.current
    val context = LocalContext.current
    val repo = d.music
    DisposableEffect(repo) {
        repo.use()
        onDispose { repo.release() }
    }
    val store = remember { FmStore(context.applicationContext) }
    val scope = rememberCoroutineScope()
    val tuned = repo.tunedFm()
    val tuner = repo.radioTunerConnected
    val linked = repo.sourceApps[SourceKind.RADIO] != null
    // Lo que la radio dijo de verdad se recuerda (para abrir el dial ahí la próxima vez).
    LaunchedEffect(tuned) { if (tuned != null) store.last = tuned }
    // El cursor del dial: sigue a la radio; arrastrarlo o ±0.1 lo mueve para ir a otra.
    var cursor by remember { mutableIntStateOf(tuned ?: store.last ?: store.stations.firstOrNull()?.tenths ?: 989) }
    var dragging by remember { mutableStateOf(false) }
    var job by remember { mutableStateOf<Job?>(null) }
    val busy = job?.isActive == true
    LaunchedEffect(tuned, dragging, busy) { if (tuned != null && !dragging && !busy) cursor = tuned }
    val currentTuned by rememberUpdatedState(tuned)
    val note = stringResource(R.string.p2_radio_note)
    fun honest() = Toast.makeText(context, note, Toast.LENGTH_SHORT).show()

    /** Despierta la radio si no suena nada (abre su app si no hay a quién mandarle las teclas) y busca. */
    fun seek(up: Boolean) {
        if (!tuner && !linked) return honest()
        if (repo.radio.song == null) repo.resumeRadio()
        if (up) repo.radio.next() else repo.radio.previous()
    }

    /** Espera a que la radio diga una frecuencia distinta de [from]; null si no dice nada a tiempo. */
    suspend fun nextReport(from: Int?): Int? = withTimeoutOrNull(SEEK_WAIT_MS) {
        snapshotFlow { currentTuned }.first { it != null && it != from }
    }

    /**
     * Ir a [target] con el sintonizador que sí dice su frecuencia: busca hacia ese lado hasta llegar o
     * pasarse (busca emisoras con señal, así que queda en la más cercana). Sin eso, se avisa y no se mueve.
     */
    fun goTo(target: Int) {
        val from = currentTuned
        if (!tuner || from == null) {
            honest()
            return
        }
        if (from == target) return
        job?.cancel()
        job = scope.launch {
            val up = target > from
            var at: Int = from
            repeat(MAX_SEEKS) {
                if (up) repo.radio.next() else repo.radio.previous()
                val now = nextReport(at) ?: return@launch
                // Dio la vuelta a la banda o ya llegó (o se pasó): ahí se queda.
                if ((up && (now >= target || now < at)) || (!up && (now <= target || now > at))) return@launch
                at = now
            }
        }
    }

    /** "Buscar y guardar todas": recorre la banda hacia arriba con el sintonizador y guarda lo que suena. */
    fun scanAll() {
        val start = currentTuned
        if (!tuner || start == null) return honest()
        job?.cancel()
        job = scope.launch {
            val seen = ArrayList<Int>()
            val found = ArrayList<FmStation>()
            var at: Int = start
            repeat(MAX_SEEKS) {
                repo.radio.next()
                val now = nextReport(at) ?: return@repeat
                if (FmStations.scanDone(start, seen, now)) {
                    store.set(FmStations.merge(store.stations, found))
                    return@launch
                }
                delay(400)
                seen += now
                found += FmStation(now, repo.rdsName())
                at = now
            }
            store.set(FmStations.merge(store.stations, found))
        }
    }

    var editing by remember { mutableStateOf<FmStation?>(null) }
    val stations = store.stations
    Box(
        Modifier.size(1024.dp, 528.dp).background(ice.bg).clickable(remember { MutableInteractionSource() }, indication = null) {},
    ) {
        IceBackdrop(R.drawable.bg_espacio)
        if (ice.dark) androidx.compose.foundation.Image(
            androidx.compose.ui.res.painterResource(R.drawable.blackhole_small), null,
            Modifier.offset(437.dp, 30.dp).size(150.dp, 90.dp), alpha = 0.28f,
        )
        // .hdr: RADIO FM y, a la derecha, cómo va y el atajo a Sonido.
        Row(Modifier.offset(34.dp, 20.dp).size(956.dp, 44.dp), verticalAlignment = Alignment.CenterVertically) {
            BrandTitle(stringResource(R.string.dock_radio))
            Spacer(Modifier.weight(1f))
            val playing = repo.radio.song?.playing == true
            IceDot(if (playing) Color(0xFF7EF0C2) else Color(0xFFFFC56B), size = 7.dp)
            Spacer(Modifier.width(8.dp))
            IceText(
                stringResource(if (busy) R.string.p2_radio_searching else if (playing) R.string.p2_radio_on_air else R.string.p2_radio_paused).uppercase(),
                IceType.location, ice.ink2,
            )
            Spacer(Modifier.width(12.dp))
            // Como el mockup, sin botón de cerrar: se sale con Inicio en el dock (o atrás).
            IceIconButton(IceIcons.sliders, onSound, iconSize = 22.dp, tint = ice.ink2)
        }
        // .tune: buscar, −0.1, la frecuencia, +0.1, buscar (centrados en 24…1000, de 70 a 240).
        Row(Modifier.offset(24.dp, 70.dp).size(976.dp, 170.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            SeekButton(stringResource(R.string.p2_radio_seek), back = true) { seek(up = false) }
            StepButton("−0.1") { cursor = FmStations.clamp(cursor - 1); goTo(cursor) }
            Column(Modifier.width(430.dp).clickable(remember { MutableInteractionSource() }, indication = null) { repo.radio.toggle() }, horizontalAlignment = Alignment.CenterHorizontally) {
                Row(verticalAlignment = Alignment.Bottom) {
                    val showsTuned = tuned != null && tuned == cursor
                    IceText(
                        FmStations.format(cursor),
                        IceType.speed.copy(shadow = Shadow(Color(150, 210, 255).copy(alpha = 0.3f), blurRadius = 30f)),
                        if (showsTuned || tuned == null) ice.speed else ice.ink2,
                        modifier = Modifier.alignByBaseline(),
                    )
                    Spacer(Modifier.width(10.dp))
                    IceText("MHz", IceType.speedUnit.copy(fontSize = 24.sp), ice.ink2, modifier = Modifier.alignByBaseline())
                }
                val saved = FmStations.at(stations, cursor)
                val name = saved?.name?.takeIf { it.isNotBlank() } ?: repo.rdsName().takeIf { tuned == cursor && it.isNotBlank() }
                Row(Modifier.padding(top = 8.dp).height(22.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (saved != null) {
                        Icon(IceIcons.starFilled, Color(0xFFFFD88A), 20.dp)
                        Spacer(Modifier.width(8.dp))
                    }
                    IceText(
                        name ?: if (tuned != null && tuned != cursor) stringResource(R.string.p2_radio_playing_at, FmStations.format(tuned)) else "",
                        IceText2.rowTitle.copy(fontSize = 20.sp), ice.ink,
                    )
                }
            }
            StepButton("+0.1") { cursor = FmStations.clamp(cursor + 1); goTo(cursor) }
            SeekButton(stringResource(R.string.p2_radio_seek), back = false) { seek(up = true) }
        }
        // .dial: se arrastra; al soltar, a la emisora más cercana con el sintonizador.
        Dial(
            cursor, stations, note = null,
            onDrag = { dragging = true; cursor = it },
            onRelease = { dragging = false; goTo(cursor) },
            modifier = Modifier.offset(24.dp, 252.dp).size(976.dp, 116.dp),
        )
        // .mine: Mis emisoras, cuántas y "Buscar y guardar todas".
        Row(Modifier.offset(34.dp, 384.dp).size(966.dp, 44.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            IceText(stringResource(R.string.p2_radio_mine), IceText2.rowTitle.copy(fontSize = 20.sp), ice.title)
            IceText(androidx.compose.ui.res.pluralStringResource(R.plurals.p2_radio_saved, stations.size, stations.size).uppercase(), IceType.small.copy(letterSpacing = 0.06.em), ice.ink3)
            // La nota honesta: sin el sintonizador de fábrica no se puede ir directo a una frecuencia.
            if (!tuner) Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                Icon(IceIcons.infoDot, Color(0xFFFFD47A).copy(alpha = 0.85f), 16.dp)
                Spacer(Modifier.width(6.dp))
                IceText(note, IceType.small, Color(0xFFFFD47A).copy(alpha = 0.85f), modifier = Modifier.weight(1f))
            }
            if (tuner) Spacer(Modifier.weight(1f))
            IceButton(stringResource(R.string.p2_radio_scan), { scanAll() }, icon = IceIcons.searchPlus, height = 48.dp, padding = 22.dp)
        }
        // .st: las guardadas (la que suena en hielo) y "Guardar".
        Row(
            Modifier.offset(24.dp, 438.dp).size(976.dp, 70.dp).horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            val width = if (stations.size <= 7) ((976 - 10 * stations.size - 82) / stations.size.coerceAtLeast(1)).coerceAtMost(118).dp else 118.dp
            for (st in stations) {
                val on = st.tenths == cursor
                Column(
                    Modifier
                        .width(width)
                        .height(70.dp)
                        .then(if (on) Modifier.iceSelected(18.dp) else Modifier.iceGlass(18.dp))
                        .clip(RoundedCornerShape(18.dp))
                        .combinedClickable(onLongClick = { editing = st }, onClick = { cursor = st.tenths; goTo(st.tenths) })
                        .padding(start = 14.dp, top = 13.dp),
                ) {
                    IceText(FmStations.format(st.tenths), IceText2.rowTitle.copy(fontSize = 22.sp, lineHeight = 1.em), Color.White)
                    IceText(st.name.ifBlank { "—" }, IceType.small, if (on) Color(0xFFDFF1FF) else ice.ink2, modifier = Modifier.padding(top = 8.dp))
                }
            }
            // Guardar la del dial (con el nombre que da la radio); luego se le puede cambiar.
            Column(
                Modifier
                    .width(82.dp)
                    .height(70.dp)
                    .iceGlass(18.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .clickable {
                        val st = FmStation(cursor, if (tuned == cursor) repo.rdsName() else "")
                        store.set(FmStations.add(stations, st))
                        editing = FmStations.at(store.stations, cursor)
                    },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically),
            ) {
                Icon(IceIcons.plus, ice.ice, 22.dp)
                IceText(stringResource(R.string.p2_radio_save), IceType.small, ice.ice)
            }
        }
        val e = editing
        if (e != null) StationEditor(
            e,
            onSave = { name -> store.set(FmStations.rename(store.stations, e.tenths, name)); editing = null },
            onDelete = { store.set(FmStations.remove(store.stations, e.tenths)); editing = null },
            onClose = { editing = null },
        )
    }
}

/** `.seek`: 150 × 56, con las flechas dobles hielo. */
@Composable
private fun SeekButton(text: String, back: Boolean, onClick: () -> Unit) {
    val ice = LocalIce.current
    val shape = RoundedCornerShape(28.dp)
    Row(
        Modifier
            .padding(horizontal = 26.dp)
            .size(150.dp, 56.dp)
            .clip(shape)
            .background(Color(170, 210, 255).copy(alpha = 0.06f))
            .border(1.dp, ice.line2, shape)
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
    ) {
        if (back) Icon(IceIcons.seekBack, ice.ice, 22.dp)
        IceText(text, IceType.body, ice.ink)
        if (!back) Icon(IceIcons.seekForward, ice.ice, 22.dp)
    }
}

/** `.step`: el botón redondo de 72 dp con −0.1 / +0.1. */
@Composable
private fun StepButton(text: String, onClick: () -> Unit) {
    val ice = LocalIce.current
    Box(
        Modifier
            .size(72.dp)
            .glass(listOf(0f to Color(170, 210, 255).copy(alpha = 0.14f), 1f to Color(110, 150, 210).copy(alpha = 0.05f)), ice.line, Color(235, 246, 255).copy(alpha = 0.12f), 36.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { IceText(text, IceText2.rowTitle.copy(fontSize = 20.sp), Color.White) }
}

/**
 * `.dial`: la regla de la banda (raya cada 0.1, media cada 0.5, larga y con número cada 1 MHz), las
 * guardadas como puntos con su nombre arriba, la aguja brillante y la perilla al centro. Arrastrarla
 * mueve la frecuencia (de a 0.1); [note] (si hay) va arriba en lugar de "‹ arrastra ›".
 */
@Composable
private fun Dial(cursor: Int, stations: List<FmStation>, note: String?, onDrag: (Int) -> Unit, onRelease: () -> Unit, modifier: Modifier) {
    val ice = LocalIce.current
    val measurer = rememberTextMeasurer()
    val numStyle = TextStyle(fontFamily = Outfit, fontSize = 15.sp, color = Color(214, 230, 248).copy(alpha = 0.8f))
    val nameStyle = TextStyle(fontFamily = RobotoText, fontSize = 13.sp, color = Color(207, 228, 250).copy(alpha = 0.75f))
    val current by rememberUpdatedState(cursor)
    var acc by remember { mutableStateOf(0f) }
    Box(
        modifier
            .iceGlass(20.dp)
            .clip(RoundedCornerShape(20.dp))
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { acc = 0f },
                    onHorizontalDrag = { ch, dx ->
                        ch.consume()
                        acc -= dx / density
                        val steps = (acc / DIAL_STEP).toInt()
                        if (steps != 0) {
                            acc -= steps * DIAL_STEP
                            onDrag(FmStations.clamp(current + steps))
                        }
                    },
                    onDragEnd = onRelease,
                    onDragCancel = onRelease,
                )
            },
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
                .drawWithCache {
                    val k = density
                    val fade = Brush.horizontalGradient(0f to Color.Transparent, 0.14f to Color.Black, 0.86f to Color.Black, 1f to Color.Transparent)
                    onDrawBehind {
                        val center = size.width / 2f
                        val first = cursor - ((center / k) / DIAL_STEP).toInt() - 1
                        val last = cursor + ((center / k) / DIAL_STEP).toInt() + 1
                        for (t in first..last) {
                            if (t < FmStations.MIN || t > FmStations.MAX) continue
                            val x = center + (t - cursor) * DIAL_STEP * k
                            val (y0, y1, a, w) = when {
                                t % 10 == 0 -> Quad(38f, 74f, 0.75f, 1.6f)
                                t % 5 == 0 -> Quad(46f, 70f, 0.45f, 1.2f)
                                else -> Quad(52f, 66f, 0.22f, 1f)
                            }
                            drawLine(Color(0xFFCFE6FF).copy(alpha = a), Offset(x, y0 * k), Offset(x, y1 * k), w * k)
                            if (t % 10 == 0) {
                                val label = measurer.measure((t / 10).toString(), numStyle)
                                drawText(label, topLeft = Offset(x - label.size.width / 2f, 92f * k - label.size.height * 0.78f))
                            }
                        }
                        for (s in stations) {
                            val x = center + (s.tenths - cursor) * DIAL_STEP * k
                            if (x < -40 * k || x > size.width + 40 * k) continue
                            drawCircle(Color(0xFFA9DCFF).copy(alpha = 0.18f), 7f * k, Offset(x, 26f * k))
                            drawCircle(Color(0xFFA9DCFF), 3.2f * k, Offset(x, 26f * k))
                            if (s.name.isNotBlank() && abs(s.tenths - cursor) > 2) {
                                val label = measurer.measure(s.name, nameStyle)
                                drawText(label, topLeft = Offset(x - label.size.width / 2f, 17f * k - label.size.height * 0.78f))
                            }
                        }
                        drawRect(fade, blendMode = BlendMode.DstIn)
                    }
                },
        )
        // .needle y .knob.
        Box(
            Modifier.align(Alignment.TopCenter).offset(y = 28.dp).size(2.dp, 78.dp)
                .softGlow(Color(150, 210, 255).copy(alpha = 0.5f), 8.dp)
                .background(Brush.verticalGradient(0f to Color(220, 242, 255).copy(alpha = 0f), 0.22f to Color(0xFFE3F4FF), 0.78f to Color(0xFFE3F4FF), 1f to Color(220, 242, 255).copy(alpha = 0f))),
        )
        Box(
            Modifier.align(Alignment.Center).softGlow(Color(190, 230, 255).copy(alpha = 0.55f), 12.dp).size(30.dp).clip(CircleShape).background(Color.White)
                .border(6.dp, Color(190, 228, 255).copy(alpha = 0.12f), CircleShape),
        )
        IceText(
            note ?: stringResource(R.string.p2_radio_drag),
            IceType.small.copy(textAlign = TextAlign.Center),
            if (note != null) Color(0xFFFFD47A).copy(alpha = 0.85f) else ice.ink3,
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 8.dp).fillMaxWidth(),
        )
    }
}

private data class Quad(val a: Float, val b: Float, val c: Float, val d: Float)

/** Cambiarle el nombre a una emisora o borrarla (el teclado sale solo al tocar el nombre). */
@Composable
private fun StationEditor(station: FmStation, onSave: (String) -> Unit, onDelete: () -> Unit, onClose: () -> Unit) {
    val ice = LocalIce.current
    var name by remember(station) { mutableStateOf(station.name) }
    Box(
        Modifier.fillMaxSize().background(Color(3, 6, 12).copy(alpha = 0.6f)).clickable(remember { MutableInteractionSource() }, indication = null, onClick = onClose),
    ) {
        Column(
            Modifier.align(Alignment.Center).width(520.dp)
                .glass(listOf(0f to Color(32, 52, 82).copy(alpha = 0.97f), 0.6f to Color(14, 24, 42).copy(alpha = 0.98f), 1f to Color(10, 18, 32).copy(alpha = 0.98f)), Color(190, 225, 255).copy(alpha = 0.2f), Color(235, 246, 255).copy(alpha = 0.12f), 22.dp, angle = 165f)
                .clickable(remember { MutableInteractionSource() }, indication = null) {}.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IceText(FmStations.format(station.tenths) + " MHz", IceText2.h3.copy(fontSize = 22.sp), ice.title, modifier = Modifier.weight(1f))
                IceIconButton(IceIcons.close, onClose)
            }
            IceSearchField(name, { name = it.take(24) }, stringResource(R.string.p2_radio_name_hint), Modifier.fillMaxWidth(), icon = IceIcons.pencil)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                IceButton(stringResource(R.string.p2_radio_delete), onDelete, kind = IceButtonKind.GHOST, icon = IceIcons.trash)
                Spacer(Modifier.weight(1f))
                IceButton(stringResource(R.string.p2_radio_rename), { onSave(name) }, kind = IceButtonKind.PRIMARY)
            }
        }
    }
}
