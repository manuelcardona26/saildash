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
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import androidx.lifecycle.compose.LifecycleStartEffect
import com.irvingguerra.litedash.core.ui.IconButton
import com.irvingguerra.litedash.core.ui.Icons
import com.irvingguerra.litedash.core.ui.LiteDashTheme
import com.irvingguerra.litedash.core.ui.LocalColors
import com.irvingguerra.litedash.core.ui.ice.IceDark
import com.irvingguerra.litedash.core.ui.ice.IceFrame
import com.irvingguerra.litedash.core.ui.ice.IceText
import androidx.compose.foundation.layout.height
import com.irvingguerra.litedash.core.vehicle.Signal
import com.irvingguerra.litedash.core.vehicle.Source
import com.irvingguerra.litedash.feature.obd.ObdState
import kotlin.concurrent.thread
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

/** Sin un punto del GPS en este tiempo, la señal se perdió (túnel, parqueadero cubierto). */
private const val GPS_LOST_MS = 5_000L

/** Lo que el GPS se sostiene de más al pasar del inicio a esta pantalla y de regreso. */
private const val GPS_HOLD_MS = 3_000L

/** El pitido: cuánto dura y su volumen (0–100, sobre el volumen de la música). */
private const val BEEP_MS = 300
private const val BEEP_VOLUME = 80

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

        // SailDash 1.1: el modo galaxia (Galaxy.kt) a pantalla completa; un toque en cualquier parte la cierra.
        Box(
            Modifier
                .fillMaxSize()
                .background(c.background)
                .clickable(remember { MutableInteractionSource() }, indication = null, onClick = close),
        ) {
            IceFrame(Modifier.safeDrawingPadding()) {
                GalaxyScene(d, view, status, limit, miles, unit, askGps = if (!obdOn && !gps) askGps else null) { options = true }
            }
            if (options) SpeedOnlyOptions(settings, miles, unit) { options = false }
        }
    }
}

/**
 * Las opciones, en una tarjeta de vidrio oscuro encima (estilo C): la alerta (con su pitido) y abrirla
 * sola. Un toque afuera la cierra.
 */
@Composable
private fun SpeedOnlyOptions(settings: SpeedOnlySettings, miles: Boolean, unit: String, close: () -> Unit) {
    val ice = IceDark
    val off = stringResource(R.string.velocidad_off)
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(2, 4, 10).copy(alpha = 0.72f))
            .clickable(remember { MutableInteractionSource() }, indication = null, onClick = close)
            .safeDrawingPadding()
            .padding(16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .widthIn(max = 600.dp)
                .darkGlass(22.dp)
                .clip(RoundedCornerShape(22.dp))
                // Los toques dentro de la tarjeta no la cierran.
                .clickable(remember { MutableInteractionSource() }, indication = null) {}
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 22.dp),
        ) {
            IceText(stringResource(R.string.velocidad_title), iceStyle(26f, FontWeight.Light), Color.White)
            OptionRow(stringResource(R.string.velocidad_limit_title, unit)) {
                IceSegmented(
                    listOf<Pair<Int?, String>>(null to off) + SPEED_ONLY_LIMITS_KMH.map { it to speedOnlyLimitShown(it, miles).toString() },
                    settings.limitKmh,
                    settings::pickLimit,
                )
                IceText(stringResource(R.string.velocidad_limit_help), iceStyle(13f, display = false, lineHeight = 1.4f), ice.ink2, maxLines = 2, modifier = Modifier.padding(top = 8.dp))
            }
            if (settings.limitKmh != null) {
                Spacer(Modifier.height(6.dp))
                IceSwitchRow(stringResource(R.string.velocidad_beep), settings.beep, settings::turnBeep)
                IceText(stringResource(R.string.velocidad_beep_help), iceStyle(13f, display = false, lineHeight = 1.4f), ice.ink3, maxLines = 2)
            }
            OptionRow(stringResource(R.string.velocidad_auto_title, unit)) {
                IceSegmented(
                    listOf<Pair<Int?, String>>(null to off) + SPEED_ONLY_AUTO_KMH.map { it to speedOnlyAutoShown(it, miles).toString() },
                    settings.autoOpenKmh,
                    settings::pickAutoOpen,
                )
                IceText(stringResource(R.string.velocidad_auto_help), iceStyle(13f, display = false, lineHeight = 1.4f), ice.ink2, maxLines = 3, modifier = Modifier.padding(top = 8.dp))
            }
            Box(Modifier.align(Alignment.End).padding(top = 16.dp)) { IceButton(stringResource(R.string.done), close, primary = true) }
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
