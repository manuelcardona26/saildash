package com.irvingguerra.litedash

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.media.AudioManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.Process
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.irvingguerra.litedash.feature.music.BroadcastMeta

/** Qué se oyó al grabar ([label]: cómo se llama en pantalla y en el archivo). */
internal enum class DiagEventKind(val label: Int) {
    SETTING(R.string.diagnostico_kind_setting),
    BROADCAST(R.string.diagnostico_kind_broadcast),
    VOLUME(R.string.diagnostico_kind_volume),
    INFO(R.string.diagnostico_kind_info),
}

/** Un evento grabado; [time] es la hora del reloj. */
internal class DiagEvent(val id: Long, val time: Long, val kind: DiagEventKind, val text: String) {
    val clock: String by lazy { DiagFormat.clock(time) }
}

/** El aviso de Android cuando cambia un volumen (oculto, pero llega a cualquier app que lo escuche). */
private const val VOLUME_CHANGED = "android.media.VOLUME_CHANGED_ACTION"

/**
 * Los avisos que se escuchan al grabar, en una sola lista: los que ya usa SailDash (encendido,
 * música Bluetooth de varias marcas, radio NWD), los de Android que dicen algo del carro y los
 * conocidos de otras marcas de pantallas. Muchos no existen en la tuya: esos nunca llegan y no
 * cuestan nada. A estos se suman los que declaran las apps clave de tu pantalla en su manifiesto.
 */
internal val DIAG_ACTIONS: List<String> = (
    listOf(
        // Android: pantalla, corriente (en algunas es el ACC), apagado, audífonos, modo carro y teclas
        "android.intent.action.SCREEN_ON", "android.intent.action.SCREEN_OFF", "android.intent.action.USER_PRESENT",
        "android.intent.action.ACTION_POWER_CONNECTED", "android.intent.action.ACTION_POWER_DISCONNECTED",
        "android.intent.action.ACTION_SHUTDOWN", "android.intent.action.REBOOT", "android.intent.action.DOCK_EVENT",
        "android.intent.action.HEADSET_PLUG", "android.media.AUDIO_BECOMING_NOISY", "android.media.RINGER_MODE_CHANGED",
        "android.media.STREAM_MUTE_CHANGED_ACTION", "android.media.MASTER_MUTE_CHANGED_ACTION",
        "android.media.STREAM_DEVICES_CHANGED_ACTION", "android.app.action.ENTER_CAR_MODE", "android.app.action.EXIT_CAR_MODE",
        "android.intent.action.CONFIGURATION_CHANGED", "android.intent.action.CLOSE_SYSTEM_DIALOGS", "android.intent.action.MEDIA_BUTTON",
        // Bluetooth: encendido, conexión, música (A2DP/AVRCP) y llamadas (manos libres)
        "android.bluetooth.adapter.action.STATE_CHANGED", "android.bluetooth.device.action.ACL_CONNECTED",
        "android.bluetooth.device.action.ACL_DISCONNECTED", "android.bluetooth.a2dp.profile.action.CONNECTION_STATE_CHANGED",
        "android.bluetooth.a2dp.profile.action.PLAYING_STATE_CHANGED", "android.bluetooth.a2dp-sink.profile.action.CONNECTION_STATE_CHANGED",
        "android.bluetooth.a2dp-sink.profile.action.PLAYING_STATE_CHANGED",
        "android.bluetooth.avrcp-controller.profile.action.CONNECTION_STATE_CHANGED",
        "android.bluetooth.headsetclient.profile.action.CONNECTION_STATE_CHANGED",
        "android.bluetooth.headsetclient.profile.action.AG_CALL_CHANGED",
        // Órdenes de música que mandan los botones de muchas pantallas
        "com.android.music.musicservicecommand", "com.android.music.musicservicecommand.togglepause",
        "com.android.music.musicservicecommand.pause", "com.android.music.musicservicecommand.next",
        "com.android.music.musicservicecommand.previous",
        // Encendido y apagado (ACC) de otras marcas: FYT, GLSX, Microntek/HCT y Autochips
        "com.fyt.boot.ACCOFF", "com.glsx.boot.ACCOFF", "com.cayboy.action.ACC_OFF", "com.carboy.action.ACC_OFF",
        "autochips.intent.action.QB_POWERON", "autochips.intent.action.QB_POWEROFF",
        "autochips.intent.action.PREQB_POWERON", "autochips.intent.action.PREQB_POWEROFF",
        // Microntek (MTCB/MTCD): teclas de la MCU y del volante, luces y arranque
        "com.microntek.irkeyDown", "com.microntek.irkeyUp", "com.microntek.light", "com.microntek.bootcheck",
        "com.microntek.startApp", "com.microntek.canbusdisplay",
        // HCT: música Bluetooth
        "hct.btmusic.play", "hct.btmusic.pause", "hct.btmusic.playpause", "hct.btmusic.next", "hct.btmusic.prev",
        // Radio NWD (los mismos del perfil de radio de SailDash)
        "com.nwd.action.ACTION_SEARCH_UP", "com.nwd.action.ACTION_SEARCH_DOWN", "com.nwd.action.ACTION_REQUEST_CHANGE_SOURCE",
        "com.nwd.action.ACTION_CHANGE_SOURCE", "com.nwd.action.ACTION_APP_IN_OUT", "com.nwd.android.ACTION_EXIT_ARM_FM_RAIDO",
    ) + BootLaunch.ACTIONS + BroadcastMeta.ACTIONS
    ).distinct()

/**
 * "Grabar eventos": mientras graba escucha los cambios de las tres tablas de ajustes, los avisos de
 * [DIAG_ACTIONS] y los del volumen, para ver qué hace la pantalla al poner la reversa, prender las
 * luces o cambiar de emisora. Vive aparte de la pantalla de Ajustes (se puede salir a la app Sonido
 * y volver), se detiene sola a los [DURATION_MS] y al detenerse no deja nada registrado.
 */
internal object DiagnosticsRecorder {
    const val DURATION_MS = 3 * 60 * 1000L
    private const val MAX_EVENTS = 1_000
    /** Cada cuánto se redibuja la lista como mucho (hay pantallas que escriben ajustes sin parar). */
    private const val REDRAW_MS = 250L

    private val main = Handler(Looper.getMainLooper())
    // Lo de aquí abajo solo se toca en el hilo principal.
    private val log = DiagLog<DiagEvent>(MAX_EVENTS)
    private var nextId = 0L
    private var session: DiagSession? = null
    /** Sube en cada grabación: lo que llegue tarde de una anterior se bota. */
    private var generation = 0
    private var redrawPending = false
    private var endsAt = 0L
    private var finishedText = ""
    private var timeoutText = ""

    var recording by mutableStateOf(false)
        private set

    /** Sube cuando hay eventos nuevos: la lista en pantalla se vuelve a leer. */
    var version by mutableIntStateOf(0)
        private set

    val total get() = log.total

    fun remainingMs() = (endsAt - SystemClock.elapsedRealtime()).coerceAtLeast(0)

    /** Los [n] más nuevos, el más nuevo primero. */
    fun newest(n: Int) = log.newest(n)

    /** Todos, del más viejo al más nuevo. */
    fun all() = log.all()

    private val autoStop = Runnable { stop(timeout = true) }

    /** Empieza a grabar (desde el hilo principal). [extraActions]: avisos propios de las apps de la pantalla. */
    fun start(context: Context, extraActions: List<String>) {
        if (recording) return
        val app = context.applicationContext
        val localized = contextWithLanguage(app)
        finishedText = localized.getString(R.string.diagnostico_rec_finished)
        timeoutText = localized.getString(R.string.diagnostico_rec_timeout)
        log.clear()
        generation++
        val mine = generation
        recording = true
        endsAt = SystemClock.elapsedRealtime() + DURATION_MS
        session = DiagSession(app, diagWords(localized), (DIAG_ACTIONS + extraActions).distinct()) { kind, text -> post(mine, kind, text) }
            .also { it.open { settings, actions -> localized.getString(R.string.diagnostico_rec_started, settings, actions) } }
        main.postDelayed(autoStop, DURATION_MS)
        version++
    }

    /** Detiene todo: deja de escuchar ajustes y avisos, y el hilo de la grabación termina. */
    fun stop(timeout: Boolean = false) {
        if (!recording) return
        main.removeCallbacks(autoStop)
        recording = false
        generation++
        session?.close()
        session = null
        add(DiagEventKind.INFO, if (timeout) timeoutText else finishedText, System.currentTimeMillis())
        redraw()
    }

    /** Desde el hilo de la grabación: el evento entra en el hilo principal (si sigue siendo la misma grabación). */
    private fun post(from: Int, kind: DiagEventKind, text: String) {
        val time = System.currentTimeMillis()
        main.post {
            if (from != generation) return@post
            add(kind, text, time)
            redraw()
        }
    }

    private fun add(kind: DiagEventKind, text: String, time: Long) {
        log.add(DiagEvent(nextId++, time, kind, text))
    }

    private fun redraw() {
        if (redrawPending) return
        redrawPending = true
        main.postDelayed({
            redrawPending = false
            version++
        }, REDRAW_MS)
    }
}

/**
 * Una grabación: su propio hilo (leer los ajustes no frena la pantalla), una foto de los ajustes
 * para saber qué valía antes, tres observadores (uno por tabla) y un receptor de avisos.
 */
private class DiagSession(
    private val context: Context,
    private val words: DiagWords,
    private val actions: List<String>,
    private val emit: (DiagEventKind, String) -> Unit,
) {
    private val thread = HandlerThread("saildash-diagnostico", Process.THREAD_PRIORITY_BACKGROUND).apply { start() }
    private val handler = Handler(thread.looper)
    // Solo en el hilo de la grabación.
    private val snapshot = HashMap<String, String>()
    private val observers = ArrayList<ContentObserver>()
    private var receiver: BroadcastReceiver? = null
    private val audio by lazy { context.getSystemService(AudioManager::class.java) }

    fun open(started: (settings: Int, actions: Int) -> String) {
        handler.post {
            snapshot.putAll(DiagSettingsTables.readAll(context))
            for ((table, uri) in DiagSettingsTables.TABLES) {
                val observer = object : ContentObserver(handler) {
                    override fun onChange(selfChange: Boolean, uri: Uri?) = changed(table, uri)
                }
                runCatching {
                    context.contentResolver.registerContentObserver(uri, true, observer)
                    observers += observer
                }
            }
            val filter = IntentFilter().apply { (actions + VOLUME_CHANGED).forEach(::addAction) }
            val r = object : BroadcastReceiver() {
                override fun onReceive(c: Context, intent: Intent) = heard(intent, isInitialStickyBroadcast)
            }
            // Avisos de otras apps (la MCU, la radio): el receptor debe aceptar los de fuera.
            runCatching {
                ContextCompat.registerReceiver(context, r, filter, null, handler, ContextCompat.RECEIVER_EXPORTED)
                receiver = r
            }
            emit(DiagEventKind.INFO, started(snapshot.size, filter.countActions()))
        }
    }

    fun close() {
        handler.post {
            for (o in observers) runCatching { context.contentResolver.unregisterContentObserver(o) }
            observers.clear()
            receiver?.let { runCatching { context.unregisterReceiver(it) } }
            receiver = null
            snapshot.clear()
            thread.quitSafely()
        }
    }

    /** Cambió algo en una tabla: "content://settings/system/<clave>" trae la clave; sin ella, se compara la tabla entera. */
    private fun changed(table: String, uri: Uri?) {
        val segments = uri?.pathSegments.orEmpty()
        if (segments.size >= 2 && segments[0] == table) {
            val name = segments[1]
            val key = "$table/$name"
            // Si Android no deja leerla, no se sabe qué pasó: mejor no decir nada.
            val now = DiagSettingsTables.read(context, table, name).getOrElse { return }
            val before = snapshot[key]
            if (now == before) return
            if (now == null) snapshot.remove(key) else snapshot[key] = now
            log(key, before, now)
        } else {
            val fresh = DiagSettingsTables.readTable(context, table)
            // Vacía = no se pudo leer (no es que se borró todo).
            if (fresh.isEmpty()) return
            val old = snapshot.filterKeys { it.startsWith("$table/") }
            for ((key, before, after) in DiagSettings.changes(old, fresh)) log(key, before, after)
            old.keys.forEach(snapshot::remove)
            snapshot.putAll(fresh)
        }
    }

    private fun log(key: String, before: String?, after: String?) {
        fun safe(v: String?) = v?.let { DiagSettings.redact(key, it, words.hidden) }
        emit(DiagEventKind.SETTING, DiagSettings.line(key, safe(before), safe(after), words))
    }

    private fun heard(intent: Intent, sticky: Boolean) {
        val action = intent.action ?: return
        val extras = intent.extras.readable()
        if (action == VOLUME_CHANGED) {
            val e = extras.orEmpty()
            val streams = audio?.let { DiagnosticsCollector.streamVolumes(it) }.orEmpty()
            val type = (e["android.media.EXTRA_VOLUME_STREAM_TYPE"] as? Int) ?: -1
            emit(
                DiagEventKind.VOLUME,
                DiagFormat.volume(type, e["android.media.EXTRA_PREV_VOLUME_STREAM_VALUE"] as? Int, e["android.media.EXTRA_VOLUME_STREAM_VALUE"] as? Int, streams),
            )
            return
        }
        val data = intent.dataString?.let { " data=$it" }.orEmpty()
        emit(DiagEventKind.BROADCAST, DiagFormat.broadcast(action, extras, sticky, words) + data)
    }
}

/**
 * Los extras de un aviso como mapa (los Bundle de adentro también); null si no se pueden leer: en
 * Android 7, un extra con una clase propia de la otra app hace fallar la lectura de todos.
 */
@Suppress("DEPRECATION") // Bundle.get(String): los avisos traen de todo
private fun Bundle?.readable(depth: Int = 0): Map<String, Any?>? {
    val b = this ?: return emptyMap()
    return runCatching {
        b.keySet().associateWith { k ->
            when (val v = b.get(k)) {
                is Bundle -> if (depth < 2) v.readable(depth + 1) ?: v.toString() else v.toString()
                else -> v
            }
        }
    }.getOrNull()
}
