package com.irvingguerra.litedash

import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.core.content.edit
import androidx.lifecycle.compose.LifecycleStartEffect
import com.irvingguerra.litedash.core.ui.Button
import com.irvingguerra.litedash.core.ui.Icon
import com.irvingguerra.litedash.core.ui.IconButton
import com.irvingguerra.litedash.core.ui.Icons
import com.irvingguerra.litedash.core.ui.Label
import com.irvingguerra.litedash.core.ui.LiteDashTheme
import com.irvingguerra.litedash.core.ui.LocalColors
import com.irvingguerra.litedash.core.ui.Radius
import com.irvingguerra.litedash.core.ui.Segmented
import com.irvingguerra.litedash.core.ui.SwitchRow
import com.irvingguerra.litedash.core.ui.Typography
import com.irvingguerra.litedash.core.vehicle.Signal
import com.irvingguerra.litedash.core.vehicle.Source
import com.irvingguerra.litedash.feature.obd.ObdState
import kotlin.concurrent.thread
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterNotNull

/*
 * SailDash: "Solo velocidad". La velocidad en grande, a pantalla completa y sin nada más que distraiga:
 * el número, un arco delgado, la hora y de dónde sale el dato. Siempre en oscuro (de noche no
 * deslumbra). La lógica que se prueba está en SpeedOnlyRules.kt.
 *
 * Rendimiento: el GPS es el mismo de las piezas (TripRepository) y corre solo mientras esta pantalla se
 * ve; se redibuja solo cuando cambia el número que se ve, y en reposo no anima nada.
 */

/** Lo que tarda el arco en llegar al número nuevo (solo al cambiar). */
private const val ARC_MS = 250

/** Sin un punto del GPS en este tiempo, la señal se perdió (túnel, parqueadero cubierto). */
private const val GPS_LOST_MS = 5_000L

/** Lo que el GPS se sostiene de más al pasar del inicio a esta pantalla y de regreso. */
private const val GPS_HOLD_MS = 3_000L

/** El pitido: cuánto dura y su volumen (0–100, sobre el volumen de la música). */
private const val BEEP_MS = 300
private const val BEEP_VOLUME = 80

/** Del arco: empieza abajo a la izquierda y da 240°, como el velocímetro. */
private const val START_ANGLE = 150f
private const val SWEEP = 240f

/**
 * Los ajustes de "Solo velocidad", en su propio archivo de preferencias. Son estado de Compose: lo que
 * los usa se redibuja al cambiarlos.
 */
class SpeedOnlySettings private constructor(context: Context) {
    private val store = context.getSharedPreferences(STORE, Context.MODE_PRIVATE)

    /** La alerta: el límite en km/h (uno de [SPEED_ONLY_LIMITS_KMH]); null = sin alerta (de fábrica). */
    var limitKmh by mutableStateOf(speedOnlyOption(store.getInt(KEY_LIMIT, 0), SPEED_ONLY_LIMITS_KMH))
        private set

    /** Un pitido al pasarse del límite. Apagado de fábrica: solo se pone rojo. */
    var beep by mutableStateOf(store.getBoolean(KEY_BEEP, false))
        private set

    /** Abrirse sola al pasar de esta velocidad, en km/h (uno de [SPEED_ONLY_AUTO_KMH]); null = nunca (de fábrica). */
    var autoOpenKmh by mutableStateOf(speedOnlyOption(store.getInt(KEY_AUTO_OPEN, 0), SPEED_ONLY_AUTO_KMH))
        private set

    fun pickLimit(kmh: Int?) {
        limitKmh = kmh
        store.edit { putInt(KEY_LIMIT, kmh ?: 0) }
    }

    fun turnBeep(on: Boolean) {
        beep = on
        store.edit { putBoolean(KEY_BEEP, on) }
    }

    fun pickAutoOpen(kmh: Int?) {
        autoOpenKmh = kmh
        store.edit { putInt(KEY_AUTO_OPEN, kmh ?: 0) }
    }

    companion object {
        private const val STORE = "speed_only"
        private const val KEY_LIMIT = "limit_kmh"
        private const val KEY_BEEP = "beep"
        private const val KEY_AUTO_OPEN = "auto_open_kmh"

        @Volatile private var instance: SpeedOnlySettings? = null

        /** Uno solo para toda la app: lo usan la pantalla y el inicio (que la abre sola). */
        fun get(context: Context): SpeedOnlySettings =
            instance ?: synchronized(this) { instance ?: SpeedOnlySettings(context.applicationContext).also { instance = it } }
    }
}

@Composable
internal fun rememberSpeedOnlySettings(): SpeedOnlySettings {
    val context = LocalContext.current
    return remember { SpeedOnlySettings.get(context) }
}

/** El botón de la barra lateral que abre "Solo velocidad" (con su nombre, para TalkBack). */
@Composable
fun SpeedOnlyButton(onClick: () -> Unit, size: Dp) {
    val name = stringResource(R.string.velocidad_title)
    IconButton(Icons.speed, onClick, Modifier.semantics { contentDescription = name }, size = size)
}

/** Abre "Solo velocidad" ([show] cambia el panel del inicio) sin que la velocidad se vaya un momento. */
fun openSpeedOnly(d: Dependencies, show: () -> Unit) {
    holdGpsBriefly(d)
    show()
}

private val mainHandler by lazy { Handler(Looper.getMainLooper()) }

/**
 * Al pasar del inicio a esta pantalla (y de regreso), las piezas sueltan el GPS justo antes de que la
 * otra lo pida: Android lo apagaba y lo volvía a prender, y la velocidad se iba un par de segundos. Si el
 * GPS ya está dando la velocidad, se sostiene [GPS_HOLD_MS] más con el mismo contador de
 * [TripRepository]. Nunca lo prende si estaba apagado.
 */
private fun holdGpsBriefly(d: Dependencies) {
    if (d.bus.source(Signal.SPEED) != Source.GPS) return
    d.trip.acquire()
    mainHandler.postDelayed({ d.trip.release() }, GPS_HOLD_MS)
}

/**
 * "Abrir «Solo velocidad» al pasar de X": solo mira la velocidad que ya hay en el bus (velocímetro, mapa,
 * carretera, Viaje u OBD); no prende el GPS ni le pide nada nuevo al OBD, y con la opción apagada no hace
 * nada. [idle] = el inicio tal cual, sin nada encima. Solo con la app al frente; al volver, empieza de cero
 * (no se abre de una si ya venías rápido).
 */
@Composable
fun SpeedOnlyAutoOpen(d: Dependencies, idle: Boolean, open: () -> Unit) {
    val kmh = rememberSpeedOnlySettings().autoOpenKmh ?: return
    val threshold = speedOnlyAutoThresholdKmh(kmh, d.prefs.units.miles)
    var started by remember { mutableStateOf(false) }
    LifecycleStartEffect(Unit) {
        started = true
        onStopOrDispose { started = false }
    }
    if (!idle || !started) return
    val latest by rememberUpdatedState(open)
    LaunchedEffect(threshold) {
        val trigger = SpeedOnlyTrigger(threshold)
        // Sin sondeo: solo despierta cuando cambia la velocidad del bus.
        snapshotFlow { d.bus.value(Signal.SPEED) }.collect { if (trigger.update(it)) openSpeedOnly(d) { latest() } }
    }
}

/**
 * La pantalla "Solo velocidad", un panel del inicio. Un toque en cualquier parte (o atrás) la cierra; el
 * botón de abajo a la izquierda abre sus opciones: la alerta de velocidad y abrirla sola.
 */
@Composable
fun SpeedOnlyPanel(d: Dependencies, close: () -> Unit) {
    val settings = rememberSpeedOnlySettings()
    // Siempre en colores oscuros, como el modo estacionado: de noche no deslumbra.
    LiteDashTheme(dark = true, scale = 1f, accent = d.prefs.accentColor) {
        val c = LocalColors.current
        // La velocidad sale de donde la saca el velocímetro: del OBD si está conectado; si no, del GPS
        // (con su permiso), que corre solo mientras esta pantalla se ve y la app está al frente.
        val obdOn = rememberObd(d)?.state == ObdState.ON
        val (gps, askGps) = rememberGpsPermission(d)
        if (!obdOn && gps) KeepTripRunning(d.trip)
        DisposableEffect(d.bus) {
            d.bus.want(Signal.SPEED)
            onDispose { d.bus.unwant(Signal.SPEED) }
        }
        // Al cerrar (toque, atrás o inicio), las piezas del inicio retoman el GPS sin que se apague un
        // momento. Va después de KeepTripRunning: lo de más abajo se suelta primero.
        DisposableEffect(Unit) { onDispose { holdGpsBriefly(d) } }
        KeepScreenOn()

        val miles = d.prefs.units.miles
        val limit = settings.limitKmh?.let { speedOnlyLimitShown(it, miles) }
        val gpsStale = rememberGpsStale(d.trip)
        // Cambia solo cuando cambia lo que se ve, no con cada décima del GPS: el resto no se recompone.
        val view = remember(miles, limit) {
            derivedStateOf {
                val stale = gpsStale.value && d.bus.source(Signal.SPEED) == Source.GPS
                speedOnlyView(d.bus.value(Signal.SPEED), miles, limit, stale)
            }
        }
        val status = remember(obdOn, gps) {
            derivedStateOf { speedOnlyStatus(d.bus.source(Signal.SPEED), obdOn, gps, gpsStale.value) }
        }
        if (settings.beep && limit != null) BeepOverLimit(view, limit)
        var options by remember { mutableStateOf(false) }
        BackHandler(enabled = options) { options = false }
        val unit = speedUnit(miles)

        Box(
            Modifier
                .fillMaxSize()
                .background(c.background)
                .clickable(remember { MutableInteractionSource() }, indication = null, onClick = close),
        ) {
            Box(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 24.dp, vertical = 16.dp)) {
                if (!obdOn && !gps) AskGps(askGps, Modifier.align(Alignment.Center))
                else SpeedDial(view, limit?.let { speedOnlyFraction(it, miles) }, unit, Modifier.fillMaxSize())
                Row(Modifier.fillMaxWidth().align(Alignment.TopCenter), verticalAlignment = Alignment.CenterVertically) {
                    SmallClock()
                    Spacer(Modifier.weight(1f))
                    SourceStatus(status)
                }
                Row(Modifier.fillMaxWidth().align(Alignment.BottomCenter), verticalAlignment = Alignment.CenterVertically) {
                    AlertChip(limit, unit) { options = true }
                    Spacer(Modifier.weight(1f))
                    Label(stringResource(R.string.velocidad_tap_to_exit), Typography.small, c.textMuted, maxLines = 1)
                }
            }
            if (options) SpeedOnlyOptions(settings, miles, unit) { options = false }
        }
    }
}

/**
 * El número enorme con su unidad, dentro de un arco delgado que se llena con la velocidad; la rayita
 * marca el límite de la alerta. Solo el número se recompone (y solo cuando cambia); el arco se redibuja
 * sin recomponer y se mueve un instante al cambiar: en reposo no corre nada.
 */
@Composable
private fun SpeedDial(view: State<SpeedOnlyView>, limitFraction: Float?, unit: String, modifier: Modifier) {
    val c = LocalColors.current
    val density = LocalDensity.current
    val fill = remember { Animatable(0f) }
    LaunchedEffect(view) {
        snapshotFlow { view.value.fraction }.collectLatest { fill.animateTo(it, tween(ARC_MS)) }
    }
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val stroke = (minOf(maxWidth, maxHeight) * 0.016f).coerceIn(4.dp, 10.dp)
        // Como el velocímetro: el centro un poco abajo, porque lo abierto del arco (con la unidad) queda abajo.
        val radius = (minOf(maxWidth / 2, maxHeight * 0.575f) * 0.96f - stroke).coerceAtLeast(8.dp)
        val drop = radius * 0.12f
        val number = radius * 0.82f
        val unitSize = (number * 0.12f).coerceAtLeast(16.dp)
        // En su propia capa: al moverse el arco no se vuelve a pintar el número.
        Canvas(Modifier.fillMaxSize().graphicsLayer()) {
            val r = radius.toPx()
            val s = stroke.toPx()
            val mid = Offset(size.width / 2, size.height / 2 + drop.toPx())
            val topLeft = Offset(mid.x - r, mid.y - r)
            val box = Size(r * 2, r * 2)
            val line = Stroke(s, cap = StrokeCap.Round)
            drawArc(c.surfaceHigh, START_ANGLE, SWEEP, false, topLeft, box, style = line)
            val v = view.value
            val f = fill.value
            if (f > 0.002f) {
                val color = when {
                    v.over -> c.danger
                    v.dim -> c.textMuted
                    else -> c.accent
                }
                drawArc(color, START_ANGLE, SWEEP * f, false, topLeft, box, style = line)
            }
            if (limitFraction != null) {
                val a = Math.toRadians((START_ANGLE + SWEEP * limitFraction).toDouble())
                val dir = Offset(cos(a).toFloat(), sin(a).toFloat())
                drawLine(c.text.copy(alpha = 0.55f), mid + dir * (r - s * 1.6f), mid + dir * (r + s * 1.6f), strokeWidth = s * 0.4f, cap = StrokeCap.Round)
            }
        }
        SpeedNumber(view, with(density) { number.toSp() }, Modifier.offset(y = drop))
        Label(
            unit,
            Typography.title.copy(fontSize = with(density) { unitSize.toSp() }),
            c.textMuted,
            maxLines = 1,
            modifier = Modifier.offset(y = drop + number * 0.455f + unitSize * 0.6f),
        )
    }
}

/** El número: rojo por encima del límite, apagado sin dato o con el GPS perdido. */
@Composable
private fun SpeedNumber(view: State<SpeedOnlyView>, size: TextUnit, modifier: Modifier) {
    val c = LocalColors.current
    val v = view.value
    val color = when {
        v.over -> c.danger
        v.dim -> c.textMuted
        else -> c.text
    }
    // Delgado y grande: se lee de lejos y de noche prende menos pantalla.
    Label(v.text, Typography.clock.copy(fontSize = size, fontWeight = FontWeight.Light, letterSpacing = (-0.02).em), color, maxLines = 1, modifier = modifier)
}

/** Sin OBD y sin permiso de ubicación: el botón para darlo (lo demás de la pantalla la cierra). */
@Composable
private fun AskGps(ask: () -> Unit, modifier: Modifier) {
    val c = LocalColors.current
    Column(modifier.widthIn(max = 520.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Icon(Icons.location, c.accent, 48.dp)
        Label(stringResource(R.string.velocidad_permission_help), Typography.subtitle.copy(textAlign = TextAlign.Center), c.text)
        Button(stringResource(R.string.velocidad_enable_gps), ask, primary = true)
    }
}

/** La hora, chiquita (un repintado por minuto, solo de ella). */
@Composable
private fun SmallClock() {
    Label(rememberClock().full, Typography.title, LocalColors.current.textMuted, maxLines = 1)
}

/** De dónde sale la velocidad: OBD, GPS, "Buscando GPS…"… */
@Composable
private fun SourceStatus(status: State<SpeedOnlyStatus>) {
    val c = LocalColors.current
    val s = status.value
    val car = s == SpeedOnlyStatus.OBD || s == SpeedOnlyStatus.CAN || s == SpeedOnlyStatus.OBD_WAITING
    val color = when (s) {
        SpeedOnlyStatus.OBD, SpeedOnlyStatus.CAN, SpeedOnlyStatus.GPS -> c.accent
        SpeedOnlyStatus.GPS_LOST, SpeedOnlyStatus.NO_PERMISSION -> c.warning
        SpeedOnlyStatus.OBD_WAITING, SpeedOnlyStatus.GPS_SEARCHING -> c.textMuted
    }
    val text = when (s) {
        SpeedOnlyStatus.OBD -> "OBD"
        SpeedOnlyStatus.CAN -> "CAN"
        SpeedOnlyStatus.GPS -> "GPS"
        SpeedOnlyStatus.OBD_WAITING -> stringResource(R.string.velocidad_obd_waiting)
        SpeedOnlyStatus.GPS_SEARCHING -> stringResource(R.string.velocidad_searching)
        SpeedOnlyStatus.GPS_LOST -> stringResource(R.string.velocidad_gps_lost)
        SpeedOnlyStatus.NO_PERMISSION -> stringResource(R.string.velocidad_no_permission)
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(if (car) Icons.engine else Icons.location, color, 20.dp)
        Label(text, Typography.body, if (s.ok) c.textMuted else color, maxLines = 1)
    }
}

/** Abajo a la izquierda: la alerta elegida; un toque abre las opciones. */
@Composable
private fun AlertChip(limit: Int?, unit: String, onClick: () -> Unit) {
    val c = LocalColors.current
    Row(
        Modifier
            .clip(RoundedCornerShape(Radius.inner))
            .background(c.surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(Icons.bell, if (limit != null) c.accent else c.textMuted, 22.dp)
        Label(
            if (limit != null) stringResource(R.string.velocidad_alert_on, limit, unit) else stringResource(R.string.velocidad_alert_off),
            Typography.body,
            c.textMuted,
            maxLines = 1,
        )
    }
}

/** Las opciones, en una tarjeta encima: la alerta (con su pitido) y abrirla sola. Un toque afuera la cierra. */
@Composable
private fun SpeedOnlyOptions(settings: SpeedOnlySettings, miles: Boolean, unit: String, close: () -> Unit) {
    val c = LocalColors.current
    val off = stringResource(R.string.velocidad_off)
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.7f))
            .clickable(remember { MutableInteractionSource() }, indication = null, onClick = close)
            .safeDrawingPadding()
            .padding(16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .widthIn(max = 620.dp)
                .clip(RoundedCornerShape(Radius.card))
                .background(c.surface)
                // Los toques dentro de la tarjeta no la cierran.
                .clickable(remember { MutableInteractionSource() }, indication = null) {}
                .verticalScroll(rememberScrollState())
                .padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Label(stringResource(R.string.velocidad_title), Typography.title, maxLines = 1)
            Label(stringResource(R.string.velocidad_limit_title, unit), Typography.label, c.textMuted, maxLines = 1)
            Segmented(
                listOf<Pair<Int?, String>>(null to off) + SPEED_ONLY_LIMITS_KMH.map { it to speedOnlyLimitShown(it, miles).toString() },
                settings.limitKmh,
                settings::pickLimit,
            )
            Label(stringResource(R.string.velocidad_limit_help), Typography.small, c.textMuted)
            if (settings.limitKmh != null) {
                SwitchRow(stringResource(R.string.velocidad_beep), settings.beep, settings::turnBeep, stringResource(R.string.velocidad_beep_help))
            }
            Label(stringResource(R.string.velocidad_auto_title, unit), Typography.label, c.textMuted, maxLines = 1)
            Segmented(
                listOf<Pair<Int?, String>>(null to off) + SPEED_ONLY_AUTO_KMH.map { it to speedOnlyAutoShown(it, miles).toString() },
                settings.autoOpenKmh,
                settings::pickAutoOpen,
            )
            Label(stringResource(R.string.velocidad_auto_help), Typography.small, c.textMuted)
            Button(stringResource(R.string.done), close, Modifier.align(Alignment.End), primary = true)
        }
    }
}

/** Mientras se ve, la pantalla no se apaga sola: es para ir manejando. */
@Composable
private fun KeepScreenOn() {
    val view = LocalView.current
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
}

/**
 * true = el GPS lleva [GPS_LOST_MS] sin dar un punto (túnel, parqueadero cubierto) y la velocidad del bus
 * es la última que dio. No le pide nada al GPS: solo mira los puntos que ya llegan a [TripRepository.position].
 */
@Composable
private fun rememberGpsStale(trip: TripRepository): State<Boolean> {
    val stale = remember { mutableStateOf(false) }
    LaunchedEffect(trip) {
        snapshotFlow { trip.position?.takeIf { it.provider == LocationManager.GPS_PROVIDER } }
            .filterNotNull()
            .collectLatest { fix ->
                val age = ageMs(fix)
                stale.value = age >= GPS_LOST_MS
                if (age < GPS_LOST_MS) {
                    delay(GPS_LOST_MS - age)
                    stale.value = true
                }
            }
    }
    return stale
}

/** Hace cuánto llegó el punto (0 si el sistema no lo dice). */
private fun ageMs(l: Location): Long =
    if (l.elapsedRealtimeNanos <= 0L) 0L else ((SystemClock.elapsedRealtimeNanos() - l.elapsedRealtimeNanos) / 1_000_000).coerceAtLeast(0L)

/** Con "Pitido al pasarte": uno al pasar del límite (ver [SpeedOnlyBeep]). */
@Composable
private fun BeepOverLimit(view: State<SpeedOnlyView>, limit: Int) {
    LaunchedEffect(view, limit) {
        val beep = SpeedOnlyBeep(limit)
        snapshotFlow { view.value }.collect { v -> if (beep.update(v.number.takeUnless { v.dim })) playBeep() }
    }
}

/** Un pitido doble y corto por el sonido de las apps. No pide el audio: Spotify o AutoKit siguen sonando. */
private fun playBeep() {
    thread(name = "speed-beep") {
        runCatching {
            val tone = ToneGenerator(AudioManager.STREAM_MUSIC, BEEP_VOLUME)
            try {
                tone.startTone(ToneGenerator.TONE_PROP_BEEP2, BEEP_MS)
                Thread.sleep(BEEP_MS + 150L)
            } finally {
                tone.release()
            }
        }
    }
}
