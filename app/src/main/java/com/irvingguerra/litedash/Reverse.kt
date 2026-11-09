package com.irvingguerra.litedash

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.media.AudioManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.core.content.edit

/** Un ajuste del sistema que se puede leer y escuchar ("system/nombre", "global/…" o "secure/…"). */
internal data class ReverseKey(val table: String, val name: String) {
    val stored get() = "$table/$name"

    fun uri(): Uri = when (table) {
        "global" -> Settings.Global.getUriFor(name)
        "secure" -> Settings.Secure.getUriFor(name)
        else -> Settings.System.getUriFor(name)
    }

    fun read(context: Context): String? = runCatching {
        val r = context.contentResolver
        when (table) {
            "global" -> Settings.Global.getString(r, name)
            "secure" -> Settings.Secure.getString(r, name)
            else -> Settings.System.getString(r, name)
        }
    }.getOrNull()

    companion object {
        private val TABLES = setOf("system", "global", "secure")

        fun fromStored(v: String?): ReverseKey? {
            val i = v?.indexOf('/') ?: return null
            if (i <= 0 || i == v.length - 1) return null
            return ReverseKey(v.substring(0, i), v.substring(i + 1))
        }

        /** El ajuste de un aviso de cambio (content://settings/system/nombre); null si es de toda la tabla. */
        fun fromUri(uri: Uri?): ReverseKey? {
            val path = uri?.takeIf { it.authority == Settings.AUTHORITY }?.pathSegments ?: return null
            if (path.size != 2 || path[0] !in TABLES) return null
            return ReverseKey(path[0], path[1])
        }
    }
}

/** En qué va "Aprender la reversa" (lo muestra Ajustes). */
sealed interface ReverseLearnState {
    data object Idle : ReverseLearnState

    /** Escuchando hasta [untilMs] (SystemClock.elapsedRealtime); [noticed] = ya cambió algo con nombre de reversa. */
    data class Listening(val untilMs: Long, val noticed: Boolean) : ReverseLearnState

    /** Cambiaron varios ajustes: que elija el usuario. */
    data class Choose(val options: List<ReverseSource>) : ReverseLearnState

    /** Recién aprendida (para el "¡Listo!"). */
    data class Learned(val source: ReverseSource) : ReverseLearnState

    data object Nothing : ReverseLearnState
}

/**
 * SailDash: la música en reversa. La cámara la maneja la MCU y queda encima de todo: SailDash no la toca.
 * Solo baja un poco, a la mitad o silencia la música de Android (STREAM_MUSIC: Spotify, AutoKit, NewPipe,
 * USB) mientras dura la reversa, y la devuelve al quitarla.
 *
 * Android no trae un aviso estándar de reversa: cada pantalla la escribe a su manera en un ajuste del
 * sistema. Se aprende como las luces (CarLights.kt): una foto de los ajustes sin reversa, lo que cambia al
 * meterla y otra foto unos segundos después. Como la cámara tapa la pantalla, todo eso pasa solo, aquí en
 * el proceso de la app (no en la pantalla de Ajustes). Ya aprendida, solo se escucha ese ajuste: sin
 * revisar cada rato y solo con la función prendida.
 *
 * Todo corre en el hilo principal (son lecturas y llamadas cortas). El volumen de antes se guarda en disco
 * antes de bajarlo: si Android cierra la app en plena reversa, al volver a abrirse se devuelve.
 */
class ReverseWatcher private constructor(private val app: Context) {
    private val main = Handler(Looper.getMainLooper())
    private val store by lazy { app.getSharedPreferences(STORE, Context.MODE_PRIVATE) }
    private val audio by lazy { app.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    private var loaded = false

    /** Qué hacer con la música al meter reversa. */
    var mode by mutableStateOf(ReverseMode.NONE)
        private set

    /** Devolver el volumen al quitar la reversa. */
    var restore by mutableStateOf(true)
        private set

    /** La señal aprendida (null = todavía no). */
    var source by mutableStateOf<ReverseSource?>(null)
        private set

    /** La reversa confirmada ahora mismo (solo se sabe mientras se escucha). */
    var reversing by mutableStateOf(false)
        private set

    /** "Probar" en curso. */
    var testing by mutableStateOf(false)
        private set

    var learn by mutableStateOf<ReverseLearnState>(ReverseLearnState.Idle)
        private set

    private var observer: ContentObserver? = null
    private var receiver: BroadcastReceiver? = null
    private var watchedKey: ReverseKey? = null
    private var debounce = ReverseDebounce()
    /** Lo último que dijo un aviso de reversa (un aviso no se puede volver a leer como un ajuste). */
    private var broadcastOn = false
    private var session: LearnSession? = null

    private val recheck = Runnable { refresh() }
    /** Un aviso de "reversa puesta" sin su "quitada": pasado un rato se da por quitada (que la música no quede baja). */
    private val broadcastExpired = Runnable {
        broadcastOn = false
        refresh()
    }
    private val endTest = Runnable {
        testing = false
        // Si justo entró la reversa de verdad, la música sigue baja hasta que se quite.
        if (!debounce.active) giveBack(force = true)
    }

    /** Escuchar solo con la función prendida, la señal aprendida y sin estar aprendiendo. */
    private val wanted get() = mode != ReverseMode.NONE && source != null && learn !is ReverseLearnState.Listening

    private val applied get() = store.getBoolean(KEY_APPLIED, false)

    private fun ensureLoaded() {
        if (loaded) return
        loaded = true
        mode = ReverseMode.from(store.getString(KEY_MODE, null))
        restore = store.getBoolean(KEY_RESTORE, true)
        val setting = store.getString(KEY_SETTING, null)
        val onValue = store.getString(KEY_ON_VALUE, null)
        val action = store.getString(KEY_BROADCAST, null)
        source = when {
            setting != null && onValue != null && ReverseKey.fromStored(setting) != null -> ReverseSource.Setting(setting, onValue)
            action != null -> ReverseSource.Broadcast(action)
            else -> null
        }
    }

    /** Al abrir la app: si una prueba o la función apagada dejaron la música baja, se devuelve; y a escuchar. */
    private fun resume() {
        ensureLoaded()
        if (session != null) return
        if (applied && (store.getBoolean(KEY_TEST, false) || !wanted)) giveBack(force = true)
        sync()
    }

    fun pick(m: ReverseMode) {
        ensureLoaded()
        if (m == mode) return
        mode = m
        store.edit { putString(KEY_MODE, m.stored) }
        // "No tocar": deja de escuchar y, si la música quedó baja, la devuelve.
        if (m == ReverseMode.NONE) giveBack(force = true)
        sync()
    }

    fun changeRestore(on: Boolean) {
        restore = on
        store.edit { putBoolean(KEY_RESTORE, on) }
    }

    /** Esta pantalla no deja cambiar el volumen de Android (todo lo hace el chip de sonido). */
    fun volumeFixed(): Boolean = runCatching { audio.isVolumeFixed }.getOrDefault(false)

    /** Vuelve a armar la escucha según los ajustes. */
    private fun sync() {
        unwatch()
        if (wanted) watch()
    }

    private fun watch() {
        // Si la música quedó baja por una reversa (la app se cerró en plena reversa), se parte de "en
        // reversa": si ya se quitó, se devuelve tras el antirrebote. La de una prueba la devuelve la prueba.
        debounce = ReverseDebounce(active = applied && !store.getBoolean(KEY_TEST, false))
        val key = (source as? ReverseSource.Setting)?.let { ReverseKey.fromStored(it.key) }
        if (key != null) {
            val o = object : ContentObserver(main) {
                override fun onChange(selfChange: Boolean) = refresh()
            }
            if (runCatching { app.contentResolver.registerContentObserver(key.uri(), false, o) }.isSuccess) {
                observer = o
                watchedKey = key
            }
        }
        val r = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) = heard(intent)
        }
        // Los avisos vienen de otras apps de la pantalla: el receptor debe aceptarlos.
        if (runCatching { ContextCompat.registerReceiver(app, r, reverseFilter(), ContextCompat.RECEIVER_EXPORTED) }.isSuccess) receiver = r
        refresh()
    }

    private fun unwatch() {
        observer?.let { runCatching { app.contentResolver.unregisterContentObserver(it) } }
        receiver?.let { runCatching { app.unregisterReceiver(it) } }
        observer = null
        receiver = null
        watchedKey = null
        main.removeCallbacks(recheck)
        main.removeCallbacks(broadcastExpired)
        broadcastOn = false
        debounce = ReverseDebounce()
        reversing = false
    }

    /** Lee la señal y, si ya se mantuvo lo suficiente, baja o devuelve la música. Sin revisar cada rato: solo al cambiar. */
    private fun refresh() {
        if (observer == null && receiver == null) return
        val key = watchedKey
        val on = (key != null && key.read(app) == (source as? ReverseSource.Setting)?.onValue) || broadcastOn
        val now = SystemClock.elapsedRealtime()
        if (debounce.update(on, now)) {
            if (debounce.active) lower(test = false) else giveBack(force = false)
        }
        reversing = debounce.active
        main.removeCallbacks(recheck)
        debounce.waitMs(now)?.let { main.postDelayed(recheck, it) }
    }

    private fun heard(intent: Intent) {
        val on = ReverseBroadcasts.state(intent.action ?: return, extrasOf(intent)) ?: return
        broadcastOn = on
        main.removeCallbacks(broadcastExpired)
        if (on) main.postDelayed(broadcastExpired, BROADCAST_MAX_MS)
        refresh()
    }

    /**
     * Baja la música según la opción. El volumen de antes se guarda primero: si Android cierra la app en
     * plena reversa, al volver se devuelve igual. Si ya está baja (una prueba, o no se alcanzó a devolver), nada.
     */
    private fun lower(test: Boolean) {
        val m = mode
        if (m == ReverseMode.NONE || applied) return
        runCatching {
            // Ya en silencio: no hay nada que bajar (ni que devolver después).
            if (audio.isStreamMute(MUSIC)) return
            val current = audio.getStreamVolume(MUSIC)
            val target = ReverseVolume.lowered(current, m) ?: return
            if (m == ReverseMode.MUTE) {
                markLowered(current, ReverseVolume.MUTED, test)
                audio.adjustStreamVolume(MUSIC, AudioManager.ADJUST_MUTE, 0)
                // Algunas pantallas no silencian así: entonces a 0 (y se devuelve como al bajar).
                if (!audio.isStreamMute(MUSIC) && audio.getStreamVolume(MUSIC) > 0) {
                    markLowered(current, 0, test)
                    audio.setStreamVolume(MUSIC, 0, 0)
                }
            } else {
                markLowered(current, target, test)
                audio.setStreamVolume(MUSIC, target, 0)
            }
        }
    }

    private fun markLowered(saved: Int, set: Int, test: Boolean) =
        store.edit { putBoolean(KEY_APPLIED, true).putInt(KEY_SAVED, saved).putInt(KEY_SET, set).putBoolean(KEY_TEST, test) }

    /**
     * Devuelve la música como estaba si la opción lo pide (o siempre con [force]: la prueba, apagar la
     * función, aprender otra vez). Si el usuario movió el volumen en plena reversa, se deja el suyo.
     */
    private fun giveBack(force: Boolean) {
        if (!applied) return
        if (force || restore || store.getBoolean(KEY_TEST, false)) runCatching {
            val set = store.getInt(KEY_SET, ReverseVolume.MUTED)
            if (set == ReverseVolume.MUTED) {
                // Si ya le quitaron el silencio (subiendo el volumen), se deja.
                if (audio.isStreamMute(MUSIC)) audio.adjustStreamVolume(MUSIC, AudioManager.ADJUST_UNMUTE, 0)
            } else {
                ReverseVolume.restored(store.getInt(KEY_SAVED, -1), set, audio.getStreamVolume(MUSIC))
                    ?.let { audio.setStreamVolume(MUSIC, it, 0) }
            }
        }
        store.edit { putBoolean(KEY_APPLIED, false).remove(KEY_TEST) }
    }

    /** "Probar": la música queda 3 segundos como en reversa y vuelve (siempre vuelve, aunque el interruptor esté apagado). */
    fun test() {
        ensureLoaded()
        if (testing || mode == ReverseMode.NONE || debounce.active) return
        testing = true
        lower(test = true)
        main.postDelayed(endTest, TEST_MS)
    }

    /** "Aprender la reversa": foto de los ajustes ahora (sin reversa) y a anotar lo que cambie al meterla. */
    fun startLearning() {
        ensureLoaded()
        session?.stop()
        session = null
        unwatch()
        // Mientras se aprende no se toca el volumen (y si quedó bajo, se devuelve).
        giveBack(force = true)
        session = LearnSession().also { it.begin() }
    }

    fun cancelLearning() {
        session?.stop()
        session = null
        learn = ReverseLearnState.Idle
        sync()
    }

    /** Eligió uno de los ajustes que cambiaron. */
    fun choose(s: ReverseSource) {
        save(s)
        learn = ReverseLearnState.Learned(s)
        sync()
    }

    fun forget() {
        session?.stop()
        session = null
        unwatch()
        giveBack(force = true)
        save(null)
        learn = ReverseLearnState.Idle
    }

    /** Al salir de la pestaña: el "¡Listo!" o el "No cambió nada" ya se vieron. */
    fun clearResult() {
        if (learn is ReverseLearnState.Learned || learn is ReverseLearnState.Nothing) learn = ReverseLearnState.Idle
    }

    private fun save(s: ReverseSource?) {
        source = s
        store.edit {
            remove(KEY_SETTING).remove(KEY_ON_VALUE).remove(KEY_BROADCAST)
            when (s) {
                is ReverseSource.Setting -> putString(KEY_SETTING, s.key).putString(KEY_ON_VALUE, s.onValue)
                is ReverseSource.Broadcast -> putString(KEY_BROADCAST, s.action)
                null -> Unit
            }
        }
    }

    /** Todos los ajustes de la pantalla (las tres tablas) con su valor, para comparar dos momentos. */
    private fun snapshot(): Map<String, String> {
        val all = HashMap<String, String>()
        for ((table, uri) in tables()) {
            runCatching {
                app.contentResolver.query(uri, arrayOf("name", "value"), null, null, null)?.use { c ->
                    while (c.moveToNext()) c.getString(0)?.let { all["$table/$it"] = c.getString(1) ?: "" }
                }
            }
        }
        return all
    }

    /**
     * Una vuelta de "Aprender la reversa". Corre en el proceso de la app, no en la pantalla: con la reversa
     * puesta la cámara tapa todo, así que lo que cambia se anota solo y la segunda foto se toma sola, unos
     * segundos después de ver un ajuste con nombre de reversa (o al acabarse el tiempo).
     */
    private inner class LearnSession {
        private val before = snapshot()
        /** Los valores que fue tomando cada ajuste que cambió, en orden. */
        private val seen = HashMap<String, MutableList<String>>()
        /** Un aviso de reversa que llegó diciendo "puesta". */
        private var heardAction: String? = null
        private var deadline = 0L
        private val finish = Runnable { done() }

        private val recorder = object : ContentObserver(main) {
            override fun onChange(selfChange: Boolean, uri: Uri?) = record(uri)
        }

        private val listener = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val action = intent.action ?: return
                if (ReverseBroadcasts.state(action, extrasOf(intent)) == true) {
                    heardAction = action
                    noticed()
                }
            }
        }

        fun begin() {
            for ((_, uri) in tables()) runCatching { app.contentResolver.registerContentObserver(uri, true, recorder) }
            runCatching { ContextCompat.registerReceiver(app, listener, reverseFilter(), ContextCompat.RECEIVER_EXPORTED) }
            deadline = SystemClock.elapsedRealtime() + LEARN_MS
            main.postDelayed(finish, LEARN_MS)
            learn = ReverseLearnState.Listening(deadline, noticed = false)
        }

        private fun record(uri: Uri?) {
            val key = ReverseKey.fromUri(uri) ?: return
            val stored = key.stored
            if (ReverseLearning.ignored(stored)) return
            val values = seen.getOrPut(stored) { ArrayList() }
            // Uno que cambia sin parar es ruido: con unos cuantos valores basta para saberlo.
            if (values.size > MAX_VALUES) return
            val value = key.read(app).orEmpty()
            values += value
            // Termina antes solo con un cambio de verdad de un ajuste con nombre de reversa (no con uno que titila).
            if (ReverseLearning.named(stored) && values.size <= 2 && value != before[stored].orEmpty()) noticed()
        }

        /** Cambió algo que suena a reversa: unos segundos más para que se asiente y se termina. */
        private fun noticed() {
            val soon = SystemClock.elapsedRealtime() + SETTLE_MS
            if (soon < deadline) {
                deadline = soon
                main.removeCallbacks(finish)
                main.postDelayed(finish, SETTLE_MS)
            }
            learn = ReverseLearnState.Listening(deadline, noticed = true)
        }

        fun stop() {
            main.removeCallbacks(finish)
            runCatching { app.contentResolver.unregisterContentObserver(recorder) }
            runCatching { app.unregisterReceiver(listener) }
        }

        private fun done() {
            stop()
            if (session !== this) return
            session = null
            val decision = ReverseLearning.decide(ReverseLearning.candidates(before, snapshot(), seen), heardAction)
            learn = when (decision) {
                is ReverseDecision.Learned -> {
                    save(decision.source)
                    ReverseLearnState.Learned(decision.source)
                }
                is ReverseDecision.Choose -> ReverseLearnState.Choose(decision.options)
                ReverseDecision.Nothing -> ReverseLearnState.Nothing
            }
            sync()
        }
    }

    companion object {
        private const val STORE = "reversa"
        private const val KEY_MODE = "mode"
        private const val KEY_RESTORE = "restore"
        private const val KEY_SETTING = "setting"
        private const val KEY_ON_VALUE = "on_value"
        private const val KEY_BROADCAST = "broadcast"
        /** SailDash bajó la música y todavía no la devuelve; con cuánto había ([KEY_SAVED]) y cuánto puso ([KEY_SET]). */
        private const val KEY_APPLIED = "applied"
        private const val KEY_SAVED = "saved_volume"
        private const val KEY_SET = "set_volume"
        /** Lo bajó "Probar" (siempre se devuelve). */
        private const val KEY_TEST = "applied_by_test"
        private const val MUSIC = AudioManager.STREAM_MUSIC
        /** Cuánto se escucha al aprender si no cambia nada con nombre de reversa. */
        private const val LEARN_MS = 20_000L
        /** Desde que cambió algo con nombre de reversa hasta la segunda foto (con la reversa todavía puesta). */
        private const val SETTLE_MS = 3_000L
        private const val TEST_MS = 3_000L
        private const val BROADCAST_MAX_MS = 5 * 60_000L
        private const val MAX_VALUES = 8

        @Volatile
        private var instance: ReverseWatcher? = null

        private fun tables() = listOf(
            "system" to Settings.System.CONTENT_URI,
            "global" to Settings.Global.CONTENT_URI,
            "secure" to Settings.Secure.CONTENT_URI,
        )

        private fun reverseFilter() = IntentFilter().apply { ReverseBroadcasts.ACTIONS.forEach(::addAction) }

        /** Los extras de un aviso (uno raro de otra app no debe tumbar SailDash). */
        @Suppress("DEPRECATION")
        private fun extrasOf(intent: Intent): Map<String, Any?> = runCatching {
            val b = intent.extras ?: return@runCatching emptyMap<String, Any?>()
            b.keySet().associateWith { b.get(it) }
        }.getOrDefault(emptyMap())

        private fun of(context: Context): ReverseWatcher =
            instance ?: synchronized(this) { instance ?: ReverseWatcher(context.applicationContext).also { instance = it } }

        /** Para Ajustes (en el hilo principal). */
        fun get(context: Context): ReverseWatcher = of(context).also { it.ensureLoaded() }

        /**
         * Al abrir la app (LiteDashApp.onCreate): se arma y lee sus ajustes fuera del hilo principal y, solo
         * si la función está prendida y aprendida, se pone a escuchar. Apagada no queda nada corriendo.
         */
        fun start(context: Context) {
            val app = context.applicationContext
            Thread({
                val w = of(app)
                runCatching { w.store.all }
                w.main.post { w.resume() }
            }, "reversa").start()
        }
    }
}
