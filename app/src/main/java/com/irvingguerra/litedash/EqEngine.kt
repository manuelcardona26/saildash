// SailDash: el motor del ecualizador (efectos de Android en todo el sonido o app por app).
package com.irvingguerra.litedash

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.audiofx.AudioEffect
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.media.audiofx.Virtualizer
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import java.util.Locale
import java.util.UUID
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

private const val TAG = "SailDash-EQ"

/** Prioridad de nuestros efectos: le gana a las apps que usan la normal (0), como el ecualizador propio de Spotify. */
private const val EQ_PRIORITY = 1

/** Cómo va el ecualizador (la línea de estado de Ajustes → Sonido). */
enum class EqState { OFF, STARTING, GLOBAL, APPS, UNSUPPORTED }

/** Por qué no se pudo (o por qué Automático quedó app por app). */
enum class EqReason { NO_EFFECT, DENIED, GLOBAL_FAILED, NO_CONTROL, FAILED }

data class EqStatus(
    val state: EqState = EqState.OFF,
    /** Las apps (su nombre) a las que se les está aplicando, en app por app. */
    val apps: List<String> = emptyList(),
    val reason: EqReason? = null,
    /** Detalle técnico del último error (Detalles técnicos). */
    val detail: String? = null,
    /** Las apps que avisaron que están sonando ("Spotify #1234"). */
    val sessions: List<String> = emptyList(),
)

/** Lo que trae la pantalla: las bandas de su ecualizador y qué otros efectos sirven. */
data class EqDevice(
    val bands: EqBands,
    /** Para cada banda de [bands] (de grave a agudo), su número en el ecualizador de Android. */
    val order: List<Short>,
    val caps: EqCaps,
    /** Los efectos de sonido que reporta Android (para Detalles técnicos). */
    val effects: List<String>,
)

/** La app de sonido de fábrica (o el panel de efectos de Android) y cómo abrirla. */
class EqFactoryApp(val label: String?, val intent: Intent)

/** Lo que salió de buscarla: [app] null = no hay. */
class EqFactoryLookup(val app: EqFactoryApp?)

/**
 * SailDash: el motor del ecualizador. Vive con la app (un launcher casi nunca se cierra) y pone los
 * efectos de Android: Equalizer (con las bandas que traiga la pantalla), BassBoost, LoudnessEnhancer
 * (recupera el volumen que quita Bajo protegido) y, si se pide, Virtualizer. Va:
 * - en todo el sonido (sesión 0, la mezcla de salida: obsoleto en Android nuevo pero suele servir en
 *   las pantallas de carro), que cubre también CarPlay y las apps que no avisan; o
 * - app por app: Spotify, NewPipe y casi todos los reproductores avisan cuando abren y cierran su
 *   sesión de audio (ACTION_OPEN/CLOSE_AUDIO_EFFECT_CONTROL_SESSION).
 * Nunca las dos cosas: esa app quedaría ecualizada dos veces (y distorsionaría).
 *
 * Rendimiento: apagado no hace nada (ni escucha avisos). Prendido solo trabaja cuando algo cambia:
 * un hilo propio que se apaga solo, los cambios del dedo se juntan (máximo ~12 por segundo) y a
 * Android solo se le manda lo que cambió. Con la curva plana los efectos quedan apagados.
 */
object EqEngine {
    /** Los cambios del dedo se aplican a lo más cada 80 ms: se oyen mientras se arrastra, sin saturar la T3L. */
    private const val APPLY_EVERY_MS = 80L
    /** Se guarda cuando se deja de mover (un solo guardado por arrastre). */
    private const val SAVE_AFTER_MS = 500L
    private const val NAME_MAX = 30

    private val main = Handler(Looper.getMainLooper())
    /** Un solo hilo para tocar los efectos, en orden; sin trabajo se apaga a los 20 s. */
    private val worker = ThreadPoolExecutor(0, 1, 20, TimeUnit.SECONDS, LinkedBlockingQueue()) { r -> Thread(r, "ecualizador") }

    @Volatile private var app: Context? = null
    private val started = AtomicBoolean(false)
    private val applyQueued = AtomicBoolean(false)
    /** Lo último que pidió la pantalla (también mientras se arrastra): es lo que aplica el hilo. */
    @Volatile private var latest = EqSettings()
    @Volatile private var store: EqStore? = null
    private var loaded = false
    /** Solo en el hilo principal. */
    private var applyPosted = false

    // Para Ajustes → Sonido (Compose).
    var settings by mutableStateOf(EqSettings())
        private set
    var status by mutableStateOf(EqStatus())
        private set
    /** Las bandas de la pantalla; null mientras se leen o si no hay ecualizador ([unsupported]). */
    var device by mutableStateOf<EqDevice?>(null)
        private set
    var unsupported by mutableStateOf<EqReason?>(null)
        private set
    var slots by mutableStateOf<List<EqSlot?>>(List(EQ_SLOTS) { null })
        private set
    /** La app de sonido de fábrica, buscada una sola vez ([findFactory]). */
    @Volatile var factory: EqFactoryLookup? = null
        private set

    // Solo en el hilo del ecualizador.
    @Volatile private var dev: EqDevice? = null
    private var probeTried = false
    private var probeReason: EqReason? = null
    private var probeDetail: String? = null
    private var global: EqEffects? = null
    private var globalError: EqReason? = null
    private var globalDetail: String? = null
    private val attached = LinkedHashMap<Int, EqEffects>()
    private val known = LinkedHashMap<Int, String?>()
    private val labels = HashMap<String, String>()
    private var wasEnabled = false
    private var lastMode: EqMode? = null
    private var receiversOn: Boolean? = null
    private var dynamicReceiver: EqSessionReceiver? = null
    private var lastStatus: EqStatus? = null

    /** Al abrir la app (LiteDashApp): carga lo guardado y, si está prendido, lo pone. Barato: todo va en su hilo. */
    fun start(context: Context) {
        if (app == null) app = context.applicationContext
        if (!started.compareAndSet(false, true)) return
        worker.execute {
            ensureLoaded()
            restoreSessions()
            reconcile(latest)
            logOutput(latest)
        }
    }

    /** Para Ajustes → Sonido: ya con lo guardado. */
    fun get(context: Context): EqEngine {
        start(context)
        ensureLoaded()
        return this
    }

    /** Un cambio hecho (un estilo, un interruptor, soltar el dedo): se aplica y se guarda. Hilo principal. */
    fun update(s: EqSettings) {
        settings = s
        latest = s
        scheduleApply()
        main.removeCallbacks(saveTask)
        main.postDelayed(saveTask, SAVE_AFTER_MS)
    }

    /** Mientras se arrastra: se oye, pero no se guarda ni se redibuja toda la pantalla. Hilo principal. */
    fun preview(s: EqSettings) {
        latest = s
        scheduleApply()
    }

    /** Guarda cómo suena ahora en "Mis ajustes" [index] (nombre vacío = el de fábrica) y lo deja elegido. */
    fun saveSlot(index: Int, name: String) {
        if (index !in 0 until EQ_SLOTS) return
        val s = settings
        val slot = EqSlot(name.trim().take(NAME_MAX), s.curve, s.bass, s.voice, s.extraDb)
        slots = slots.toMutableList().also { it[index] = slot }
        update(s.copy(preset = eqSlotId(index), edited = false))
        worker.execute {
            ensureLoaded()
            store?.saveSlot(index, slot)
        }
    }

    /** Lee las bandas de la pantalla si aún no se saben (al abrir Ajustes → Sonido). */
    fun probe() {
        if (device != null) return
        worker.execute {
            ensureLoaded()
            val ctx = app ?: return@execute
            if (dev == null) probeDevice(ctx)
        }
    }

    /** Una app empezó a sonar en [session] (aviso de Android); [done] se cierra al terminar. */
    fun open(context: Context, session: Int, pkg: String?, done: BroadcastReceiver.PendingResult?) {
        start(context)
        worker.execute {
            try {
                ensureLoaded()
                if (!latest.enabled || session <= 0) return@execute
                known.remove(session)
                known[session] = pkg
                // Algunas apps no avisan al terminar: la más vieja sale.
                while (known.size > EqSessions.MAX) {
                    val oldest = known.keys.first()
                    known.remove(oldest)
                    attached.remove(oldest)?.release()
                }
                saveSessions()
                Log.i(TAG, "Sesión $session abierta (${pkg ?: "?"})")
                // Cada app que empieza a sonar es otra oportunidad para todo el sonido (pudo fallar
                // al encender la pantalla, con el audio de Android aún arrancando).
                if (latest.mode != EqMode.APPS && global == null && globalError != null) {
                    globalError = null
                    globalDetail = null
                }
                reconcile(latest)
            } finally {
                done?.finish()
            }
        }
    }

    /** La app terminó su sesión: se sueltan sus efectos. */
    fun close(context: Context, session: Int, done: BroadcastReceiver.PendingResult?) {
        start(context)
        worker.execute {
            try {
                ensureLoaded()
                val had = known.remove(session) != null
                attached.remove(session)?.release()
                if (had) {
                    saveSessions()
                    Log.i(TAG, "Sesión $session cerrada")
                    if (latest.enabled && wasEnabled) publishStatus(latest)
                }
            } finally {
                done?.finish()
            }
        }
    }

    /**
     * Busca la app de sonido de fábrica (una vez): la app del sistema con ícono cuyo nombre o paquete
     * suene a sonido o ecualizador ([EqFactoryApps]); si no hay, el panel de efectos de Android.
     * Fuera del hilo principal: lee la lista de apps.
     */
    fun findFactory(context: Context): EqFactoryLookup {
        factory?.let { return it }
        val pm = context.packageManager
        val own = context.packageName
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val best = runCatching { pm.queryIntentActivities(launcher, 0) }.getOrDefault(emptyList())
            .mapNotNull { ri ->
                val info = ri.activityInfo ?: return@mapNotNull null
                if (info.packageName == own) return@mapNotNull null
                val label = runCatching { ri.loadLabel(pm).toString() }.getOrDefault("")
                val flags = info.applicationInfo?.flags ?: 0
                val system = flags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
                Triple(info, label, EqFactoryApps.score(label, info.packageName, system)).takeIf { it.third > 0 }
            }
            .maxByOrNull { it.third }
        val found = if (best != null) {
            val (info, label) = best
            EqFactoryApp(
                label.ifBlank { null },
                Intent(launcher).setClassName(info.packageName, info.name)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED),
            )
        } else {
            // El panel de efectos de Android (MusicFX o el que traiga la pantalla), si alguien lo abre.
            val panel = Intent(AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL)
                .putExtra(AudioEffect.EXTRA_PACKAGE_NAME, own)
                .putExtra(AudioEffect.EXTRA_AUDIO_SESSION, 0)
                .putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MUSIC)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            val handlers = runCatching { pm.queryIntentActivities(panel, 0) }.getOrDefault(emptyList())
                .filter { it.activityInfo != null && it.activityInfo.packageName != own }
            if (handlers.isEmpty()) null
            else EqFactoryApp(if (handlers.size == 1) runCatching { handlers[0].loadLabel(pm).toString() }.getOrNull() else null, panel)
        }
        Log.i(TAG, "Sonido de fábrica: ${found?.label ?: found?.intent?.action ?: "no se encontró"}")
        return EqFactoryLookup(found).also { factory = it }
    }

    // ——— Hilo principal: juntar los cambios ———

    private fun scheduleApply() {
        if (applyPosted) return
        applyPosted = true
        main.postDelayed(applyTask, APPLY_EVERY_MS)
    }

    private val applyTask = Runnable {
        applyPosted = false
        // Si el hilo va atrasado no se encola otro: el que está en cola aplica lo último.
        if (applyQueued.compareAndSet(false, true)) worker.execute {
            applyQueued.set(false)
            ensureLoaded()
            reconcile(latest)
        }
    }

    private val saveTask = Runnable {
        val s = settings
        worker.execute {
            ensureLoaded()
            store?.save(s)
            logOutput(s)
        }
    }

    // ——— Hilo del ecualizador ———

    @Synchronized
    private fun ensureLoaded() {
        if (loaded) return
        val ctx = app ?: return
        val st = EqStore(ctx)
        val s = st.load()
        val cached = st.device()
        val saved = st.slots()
        store = st
        dev = cached
        latest = s
        settings = s
        slots = saved
        device = cached
        loaded = true
    }

    /** Deja los efectos como pide [s]. */
    private fun reconcile(s: EqSettings) {
        val ctx = app ?: return
        if (s.enabled != wasEnabled || s.mode != lastMode) {
            // Al prender o al cambiar de modo se vuelve a intentar todo (también leer la pantalla).
            globalError = null
            globalDetail = null
            if (s.enabled != wasEnabled) probeTried = false
        }
        lastMode = s.mode
        if (!s.enabled) {
            releaseAll()
            if (known.isNotEmpty()) {
                known.clear()
                saveSessions()
            }
            wasEnabled = false
            syncReceivers(ctx, false)
            publish(EqStatus(EqState.OFF))
            return
        }
        if (!wasEnabled) {
            wasEnabled = true
            publish(EqStatus(EqState.STARTING))
        }
        syncReceivers(ctx, true)
        val d = dev ?: if (probeTried) null else probeDevice(ctx)
        if (d == null) {
            releaseAll()
            publish(EqStatus(EqState.UNSUPPORTED, reason = probeReason ?: EqReason.NO_EFFECT, detail = probeDetail))
            return
        }
        // Si todo el sonido ya falló, no se reintenta en cada cambio (el dedo pide ~12 por segundo):
        // solo al prender, al cambiar de modo o cuando una app empieza a sonar.
        if (s.mode == EqMode.APPS) releaseGlobal() else if (global == null && globalError == null) openGlobal(d)
        global?.let { g ->
            releaseSessions()
            global = applyTo(g, s, d)
            if (global == null) {
                globalError = EqReason.FAILED
                Log.w(TAG, "Todo el sonido dejó de responder")
            }
        }
        if (global == null) {
            if (s.mode == EqMode.GLOBAL) {
                releaseSessions()
                publish(EqStatus(EqState.UNSUPPORTED, reason = globalError ?: EqReason.GLOBAL_FAILED, detail = globalDetail, sessions = sessionNames()))
                return
            }
            attachSessions(s, d)
        }
        publishStatus(s)
    }

    private fun openGlobal(d: EqDevice) {
        val g = EqEffects(0, null, null)
        val err = g.open(d.caps)
        when {
            err != null -> {
                globalError = EqReason.GLOBAL_FAILED
                globalDetail = err
            }
            !g.hasControl() -> {
                globalError = EqReason.NO_CONTROL
                globalDetail = "sesión 0 sin control"
            }
            else -> {
                global = g
                learn(g)
                Log.i(TAG, "Todo el sonido: listo")
                return
            }
        }
        g.release()
        Log.w(TAG, "Todo el sonido no se pudo: $globalDetail")
    }

    private fun attachSessions(s: EqSettings, d: EqDevice) {
        var forgot = false
        for ((session, pkg) in known.entries.toList()) {
            if (session in attached) continue
            val e = EqEffects(session, pkg, labelOf(pkg))
            val err = e.open(d.caps)
            if (err == null) {
                attached[session] = e
                learn(e)
                Log.i(TAG, "App por app: ${e.label ?: session} ($session)")
            } else {
                // Casi siempre es una sesión que ya terminó sin avisar: se olvida.
                e.release()
                known.remove(session)
                forgot = true
                Log.w(TAG, "Sesión $session: $err")
            }
        }
        for (session in attached.keys.toList()) {
            val again = applyTo(attached.getValue(session), s, d)
            if (again != null) {
                attached[session] = again
            } else {
                attached.remove(session)
                known.remove(session)
                forgot = true
            }
        }
        if (forgot) saveSessions()
    }

    /** Aplica [s]; si los efectos se cayeron (p. ej. se reinició el audio de Android), se rehacen una vez. */
    private fun applyTo(e: EqEffects, s: EqSettings, d: EqDevice): EqEffects? {
        if (e.apply(s)) return e
        Log.w(TAG, "Se rehacen los efectos de la sesión ${e.session}")
        e.release()
        val again = EqEffects(e.session, e.pkg, e.label)
        if (again.open(d.caps) == null && again.apply(s)) return again
        again.release()
        return null
    }

    /** Si un ecualizador de verdad trae otras bandas que las guardadas (otra versión de la pantalla), se actualizan. */
    private fun learn(e: EqEffects) {
        val d = dev ?: return
        val b = e.bands ?: return
        if (b == d.bands && e.order == d.order) return
        val updated = d.copy(bands = b, order = e.order)
        dev = updated
        store?.saveDevice(updated)
        main.post { device = updated }
    }

    /**
     * Lee qué trae la pantalla sin tocar el sonido: en una sesión de prueba (nueva, sin nada sonando)
     * se crea el ecualizador, se leen sus bandas, se prueban los demás efectos y se suelta todo. Si la
     * pantalla no deja usar esa sesión, se leen en la de todo el sonido, sin prender ni cambiar nada.
     */
    // Virtualizer quedó obsoleto en el Android más nuevo, pero en la 7.1 de la pantalla es el que hay.
    @Suppress("DEPRECATION")
    private fun probeDevice(ctx: Context): EqDevice? {
        probeTried = true
        val all = runCatching { AudioEffect.queryEffects() }.getOrNull()?.filterNotNull().orEmpty()
        val types = all.mapNotNull { it.type }.toSet()
        // Si Android no dice qué efectos hay, se prueba igual.
        fun has(type: UUID) = all.isEmpty() || type in types
        if (!has(AudioEffect.EFFECT_TYPE_EQUALIZER)) return probeFailed(EqReason.NO_EFFECT, "Android no reporta ecualizador (${all.size} efectos)")
        val audio = ctx.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        val generated = runCatching { audio?.generateAudioSessionId() ?: -1 }.getOrDefault(-1)
        var problem = "sin sesión de prueba"
        var found: Pair<EqEffects, Int>? = null
        for (session in listOf(generated, 0)) {
            if (session < 0) continue
            val test = EqEffects(session, null, null)
            val err = test.open(EqCaps())
            if (err == null && test.bands != null) {
                found = test to session
                break
            }
            // Sin apagar: en la sesión 0 el efecto podría ser de otra app.
            test.release(disable = false)
            problem = err ?: "sin bandas"
        }
        val (test, session) = found ?: return probeFailed(EqReason.DENIED, problem)
        val bands = test.bands ?: return probeFailed(EqReason.DENIED, "sin bandas")
        val caps = EqCaps(
            bassBoost = has(AudioEffect.EFFECT_TYPE_BASS_BOOST) && works({ BassBoost(EQ_PRIORITY, session) }) { it.strengthSupported },
            loudness = has(AudioEffect.EFFECT_TYPE_LOUDNESS_ENHANCER) && works({ LoudnessEnhancer(session) }) { true },
            virtualizer = has(AudioEffect.EFFECT_TYPE_VIRTUALIZER) && works({ Virtualizer(EQ_PRIORITY, session) }) { it.strengthSupported },
        )
        test.release(disable = false)
        val relevant = setOf(
            AudioEffect.EFFECT_TYPE_EQUALIZER, AudioEffect.EFFECT_TYPE_BASS_BOOST,
            AudioEffect.EFFECT_TYPE_LOUDNESS_ENHANCER, AudioEffect.EFFECT_TYPE_VIRTUALIZER,
        )
        val names = all.filter { it.type in relevant }.map { "${it.name} (${it.implementor})" }.distinct()
        val d = EqDevice(bands, test.order, caps, names)
        dev = d
        probeReason = null
        probeDetail = null
        store?.saveDevice(d)
        main.post {
            device = d
            unsupported = null
        }
        Log.i(TAG, "Pantalla: ${bands.hz.size} bandas (${bands.hz.joinToString { EqMath.freqLabel(it) }} Hz), ${bands.minDb}..${bands.maxDb} dB, $caps")
        return d
    }

    private fun probeFailed(reason: EqReason, detail: String): EqDevice? {
        probeReason = reason
        probeDetail = detail
        main.post { unsupported = reason }
        Log.w(TAG, "Sin ecualizador de Android: $detail")
        return null
    }

    private inline fun <T : AudioEffect> works(make: () -> T, test: (T) -> Boolean): Boolean = try {
        val fx = make()
        try { test(fx) } finally { fx.release() }
    } catch (t: Throwable) {
        false
    }

    private fun releaseGlobal() {
        global?.release()
        global = null
    }

    private fun releaseSessions() {
        attached.values.forEach { it.release() }
        attached.clear()
    }

    private fun releaseAll() {
        releaseGlobal()
        releaseSessions()
    }

    private fun publishStatus(s: EqSettings) {
        publish(
            if (global != null) {
                EqStatus(EqState.GLOBAL, sessions = sessionNames())
            } else {
                EqStatus(
                    EqState.APPS,
                    apps = attached.values.map { it.label ?: it.pkg ?: "#${it.session}" }.distinct(),
                    reason = globalError.takeIf { s.mode == EqMode.AUTO },
                    detail = globalDetail,
                    sessions = sessionNames(),
                )
            },
        )
    }

    private fun sessionNames() = known.map { (id, pkg) -> "${labelOf(pkg) ?: "?"} #$id" }

    private fun publish(st: EqStatus) {
        if (st == lastStatus) return
        lastStatus = st
        main.post { status = st }
    }

    /**
     * Los avisos de las apps solo se escuchan con el ecualizador prendido. Hasta Android 7.1 basta el
     * receptor del manifiesto (despierta a SailDash si estaba cerrado); desde Android 8 ese aviso ya
     * no llega al manifiesto, así que además se escucha mientras la app corre.
     */
    private fun syncReceivers(ctx: Context, on: Boolean) {
        if (receiversOn == on) return
        receiversOn = on
        val pm = ctx.packageManager
        val component = ComponentName(ctx, EqSessionReceiver::class.java)
        val wanted = if (on) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DEFAULT
        runCatching { if (pm.getComponentEnabledSetting(component) != wanted) pm.setComponentEnabledSetting(component, wanted, PackageManager.DONT_KILL_APP) }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        if (on && dynamicReceiver == null) {
            val r = EqSessionReceiver()
            val filter = IntentFilter().apply {
                addAction(AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION)
                addAction(AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION)
            }
            if (runCatching { ContextCompat.registerReceiver(ctx, r, filter, ContextCompat.RECEIVER_EXPORTED) }.isSuccess) dynamicReceiver = r
        } else if (!on) {
            dynamicReceiver?.let { r -> runCatching { ctx.unregisterReceiver(r) } }
            dynamicReceiver = null
        }
    }

    /** Las sesiones que seguían abiertas cuando Android cerró SailDash (si es el mismo encendido). */
    private fun restoreSessions() {
        if (!latest.enabled) return
        val saved = store?.sessions(bootStamp()).orEmpty()
        if (saved.isEmpty()) return
        known.putAll(saved)
        Log.i(TAG, "Sesiones que seguían abiertas: ${saved.keys}")
    }

    private fun saveSessions() {
        store?.saveSessions(known, bootStamp())
    }

    /** Este encendido de la pantalla: tras reiniciar, los números de sesión ya son de otras apps. */
    private fun bootStamp(): String {
        val boots = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            app?.let { runCatching { Settings.Global.getInt(it.contentResolver, Settings.Global.BOOT_COUNT) }.getOrNull() }
        } else {
            null
        }
        return "${boots ?: "-"}/${Build.FINGERPRINT}"
    }

    private fun labelOf(pkg: String?): String? {
        if (pkg.isNullOrBlank()) return null
        labels[pkg]?.let { return it }
        val pm = app?.packageManager ?: return pkg
        val label = runCatching { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() }.getOrNull()?.takeIf { it.isNotBlank() } ?: pkg
        labels[pkg] = label
        return label
    }

    /** Una línea en el registro (logcat SailDash-EQ) con lo que quedó aplicado: para revisar en la pantalla. */
    private fun logOutput(s: EqSettings) {
        if (!s.enabled) {
            Log.i(TAG, "Apagado")
            return
        }
        val d = dev ?: return
        val out = EqMath.compute(s, d.bands, d.caps)
        val where = if (global != null) "todo el sonido" else "app por app (${attached.size})"
        Log.i(
            TAG,
            "Aplicado en $where: estilo=${s.preset}${if (s.edited) "*" else ""} " +
                "bandas=${out.levels.map { EqMath.toMillibels(it, d.bands) }} mB refuerzo=${out.bassStrength} " +
                "volumen=+${out.loudnessMb} mB margen=${String.format(Locale.US, "%.1f", out.headroomDb)} dB protegido=${s.protect}",
        )
    }
}

/**
 * Avisos de las apps de música: abrieron o cerraron su sesión de audio. Apagado en el manifiesto
 * mientras el ecualizador esté apagado ([EqEngine] lo prende).
 */
class EqSessionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val session = intent.getIntExtra(AudioEffect.EXTRA_AUDIO_SESSION, AudioEffect.ERROR_BAD_VALUE)
        // 0 es todo el sonido (eso lo maneja SailDash) y un número negativo no es válido.
        if (session <= 0) return
        when (intent.action) {
            AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION ->
                EqEngine.open(context, session, intent.getStringExtra(AudioEffect.EXTRA_PACKAGE_NAME), goAsync())
            AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION -> EqEngine.close(context, session, goAsync())
        }
    }
}

/** Los efectos de una sesión de audio (0 = todo el sonido). Solo se usan en el hilo del ecualizador. */
// Virtualizer quedó obsoleto en el Android más nuevo, pero en la 7.1 de la pantalla es el que hay.
@Suppress("DEPRECATION")
private class EqEffects(val session: Int, val pkg: String?, val label: String?) {
    private var eq: Equalizer? = null
    private var bass: BassBoost? = null
    private var loud: LoudnessEnhancer? = null
    private var virt: Virtualizer? = null
    private var caps = EqCaps()
    var bands: EqBands? = null
        private set
    var order: List<Short> = emptyList()
        private set
    // Lo último que se le mandó a Android, para mandar solo lo que cambia (MIN_VALUE y -1 = aún nada).
    private var levels = ShortArray(0)
    private var eqOn: Boolean? = null
    private var bassNow = -1
    private var loudNow = -1
    private var virtNow = -1

    /** Crea el ecualizador y lee sus bandas. null = listo; si no, qué pasó. */
    fun open(deviceCaps: EqCaps): String? {
        val e = try {
            Equalizer(EQ_PRIORITY, session)
        } catch (t: Throwable) {
            return describe(t)
        }
        return try {
            val n = e.numberOfBands.toInt()
            val range = e.bandLevelRange
            val centers = (0 until n).map { it.toShort() to e.getCenterFreq(it.toShort()) / 1000f }.sortedBy { it.second }
            val b = if (range.size >= 2) EqBands(centers.map { it.second }, range[0] / 100f, range[1] / 100f) else null
            if (b == null || !b.valid) {
                e.release()
                "bandas raras: $n, rango ${range.joinToString()}"
            } else {
                eq = e
                bands = b
                order = centers.map { it.first }
                caps = deviceCaps
                levels = ShortArray(n) { Short.MIN_VALUE }
                null
            }
        } catch (t: Throwable) {
            runCatching { e.release() }
            describe(t)
        }
    }

    fun hasControl() = runCatching { eq?.hasControl() == true }.getOrDefault(false)

    /** Aplica [s]. false = Android ya no responde (hay que rehacerlos). */
    fun apply(s: EqSettings): Boolean {
        val e = eq ?: return false
        val b = bands ?: return false
        return try {
            var out = EqMath.compute(s, b, caps)
            // Lo demás se crea la primera vez que hace falta; si no se puede, se calcula sin eso.
            if (out.bassStrength > 0 && bass == null) {
                bass = create { BassBoost(EQ_PRIORITY, session) }?.let { fx -> if (runCatching { fx.strengthSupported }.getOrDefault(false)) fx else null.also { fx.release() } }
                if (bass == null) {
                    caps = caps.copy(bassBoost = false)
                    out = EqMath.compute(s, b, caps)
                }
            }
            if (out.loudnessMb > 0 && loud == null) {
                loud = create { LoudnessEnhancer(session) }
                if (loud == null) {
                    caps = caps.copy(loudness = false)
                    out = EqMath.compute(s, b, caps)
                }
            }
            if (out.virtualizerStrength > 0 && virt == null) {
                virt = create { Virtualizer(EQ_PRIORITY, session) }?.let { fx -> if (runCatching { fx.strengthSupported }.getOrDefault(false)) fx else null.also { fx.release() } }
                if (virt == null) {
                    caps = caps.copy(virtualizer = false)
                    out = EqMath.compute(s, b, caps)
                }
            }
            // Primero se apaga lo que sobra, luego los valores y al final se prende: sin saltos de volumen.
            if (!out.eqActive && eqOn != false) {
                ok(e.setEnabled(false))
                eqOn = false
            }
            if (out.bassStrength == 0 && bassNow != 0) {
                bass?.let { ok(it.setEnabled(false)) }
                bassNow = 0
            }
            if (out.loudnessMb == 0 && loudNow != 0) {
                loud?.let { ok(it.setEnabled(false)) }
                loudNow = 0
            }
            if (out.virtualizerStrength == 0 && virtNow != 0) {
                virt?.let { ok(it.setEnabled(false)) }
                virtNow = 0
            }
            for (k in out.levels.indices) {
                val mb = EqMath.toMillibels(out.levels[k], b)
                if (levels[k] != mb) {
                    e.setBandLevel(order[k], mb)
                    levels[k] = mb
                }
            }
            bass?.let { fx ->
                if (out.bassStrength > 0 && out.bassStrength != bassNow) {
                    fx.setStrength(out.bassStrength.toShort())
                    if (bassNow <= 0) ok(fx.setEnabled(true))
                    bassNow = out.bassStrength
                }
            }
            loud?.let { fx ->
                if (out.loudnessMb > 0 && out.loudnessMb != loudNow) {
                    fx.setTargetGain(out.loudnessMb)
                    if (loudNow <= 0) ok(fx.setEnabled(true))
                    loudNow = out.loudnessMb
                }
            }
            virt?.let { fx ->
                if (out.virtualizerStrength > 0 && out.virtualizerStrength != virtNow) {
                    fx.setStrength(out.virtualizerStrength.toShort())
                    if (virtNow <= 0) ok(fx.setEnabled(true))
                    virtNow = out.virtualizerStrength
                }
            }
            if (out.eqActive && eqOn != true) {
                ok(e.setEnabled(true))
                eqOn = true
            }
            true
        } catch (t: Throwable) {
            Log.w(TAG, "Sesión $session: ${describe(t)}")
            false
        }
    }

    /**
     * Apaga y suelta todo: el sonido de esa sesión queda como venía. [disable] = false solo para
     * leer las bandas (no se prendió nada, y en la sesión 0 el efecto podría ser de otra app).
     */
    fun release(disable: Boolean = true) {
        for (fx in listOfNotNull(virt, loud, bass, eq)) {
            if (disable) runCatching { fx.setEnabled(false) }
            runCatching { fx.release() }
        }
        virt = null
        loud = null
        bass = null
        eq = null
    }

    private fun <T : AudioEffect> create(make: () -> T): T? = try {
        make()
    } catch (t: Throwable) {
        Log.w(TAG, "Sesión $session: ${describe(t)}")
        null
    }

    /** Android contesta con un número: distinto de SUCCESS es que algo falló (p. ej. otra app tomó el control). */
    private fun ok(status: Int) {
        if (status != AudioEffect.SUCCESS) throw IllegalStateException("Android respondió $status")
    }
}

private fun describe(t: Throwable) = "${t.javaClass.simpleName}: ${t.message}"

/** Los ajustes del ecualizador en su propio archivo (no en Preferences). */
private class EqStore(context: Context) {
    private val p: SharedPreferences = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun load(): EqSettings {
        val preset = p.getString(K_PRESET, null) ?: EqPresets.FLAT
        val edited = p.getBoolean(K_EDITED, false)
        // Un estilo sin tocar sale del código: si una versión nueva lo afina, se oye el nuevo.
        val builtin = EqPresets.byId(preset).takeIf { !edited }
        return EqSettings(
            enabled = p.getBoolean(K_ENABLED, false),
            protect = p.getBoolean(K_PROTECT, true),
            preset = preset,
            edited = edited,
            curve = builtin?.curve ?: EqCurve.decode(p.getString(K_CURVE, null)) ?: EqCurve.FLAT,
            bass = p.getFloat(K_BASS, 0f).coerceIn(0f, 1f),
            voice = p.getFloat(K_VOICE, 0f).coerceIn(0f, 1f),
            extraDb = builtin?.extraDb ?: p.getFloat(K_EXTRA, 0f),
            virtualizer = p.getBoolean(K_VIRTUALIZER, false),
            mode = EqMode.parse(p.getString(K_MODE, null)),
        )
    }

    fun save(s: EqSettings) {
        p.edit()
            .putBoolean(K_ENABLED, s.enabled)
            .putBoolean(K_PROTECT, s.protect)
            .putString(K_PRESET, s.preset)
            .putBoolean(K_EDITED, s.edited)
            .putString(K_CURVE, s.curve.encode())
            .putFloat(K_BASS, s.bass)
            .putFloat(K_VOICE, s.voice)
            .putFloat(K_EXTRA, s.extraDb)
            .putBoolean(K_VIRTUALIZER, s.virtualizer)
            .putString(K_MODE, s.mode.name)
            .commit()
    }

    fun slots(): List<EqSlot?> = List(EQ_SLOTS) { i ->
        EqCurve.decode(p.getString("slot${i}_curve", null))?.let { curve ->
            EqSlot(
                p.getString("slot${i}_name", null).orEmpty(), curve,
                p.getFloat("slot${i}_bass", 0f).coerceIn(0f, 1f), p.getFloat("slot${i}_voice", 0f).coerceIn(0f, 1f), p.getFloat("slot${i}_extra", 0f),
            )
        }
    }

    fun saveSlot(i: Int, slot: EqSlot) {
        p.edit()
            .putString("slot${i}_name", slot.name)
            // Una curva sin puntos (después de Restablecer) se guarda como plana: si no, el espacio quedaría vacío.
            .putString("slot${i}_curve", slot.curve.encode().ifEmpty { "1000:0" })
            .putFloat("slot${i}_bass", slot.bass)
            .putFloat("slot${i}_voice", slot.voice)
            .putFloat("slot${i}_extra", slot.extraDb)
            .commit()
    }

    /** Las bandas leídas antes, si la pantalla sigue con el mismo sistema. */
    fun device(): EqDevice? {
        if (p.getString(K_DEV_BUILD, null) != Build.FINGERPRINT) return null
        val hz = floats(p.getString(K_DEV_HZ, null)) ?: return null
        val range = floats(p.getString(K_DEV_RANGE, null))?.takeIf { it.size == 2 } ?: return null
        val order = p.getString(K_DEV_ORDER, null)?.split(',')?.mapNotNull { it.trim().toShortOrNull() }?.takeIf { it.size == hz.size } ?: return null
        val bands = EqBands(hz, range[0], range[1]).takeIf { it.valid } ?: return null
        val caps = p.getString(K_DEV_CAPS, null).orEmpty().split(',').toSet()
        val effects = p.getString(K_DEV_EFFECTS, null).orEmpty().split('|').filter { it.isNotBlank() }
        return EqDevice(bands, order, EqCaps("bass" in caps, "loud" in caps, "virt" in caps), effects)
    }

    fun saveDevice(d: EqDevice) {
        val caps = listOfNotNull("bass".takeIf { d.caps.bassBoost }, "loud".takeIf { d.caps.loudness }, "virt".takeIf { d.caps.virtualizer })
        p.edit()
            .putString(K_DEV_BUILD, Build.FINGERPRINT)
            .putString(K_DEV_HZ, d.bands.hz.joinToString(","))
            .putString(K_DEV_RANGE, "${d.bands.minDb},${d.bands.maxDb}")
            .putString(K_DEV_ORDER, d.order.joinToString(","))
            .putString(K_DEV_CAPS, caps.joinToString(","))
            .putString(K_DEV_EFFECTS, d.effects.joinToString("|"))
            .commit()
    }

    fun sessions(boot: String): Map<Int, String?> {
        // Otro encendido (o el reloj desde el encendido va por detrás): esos números ya no son de nadie.
        if (p.getString(K_SESSIONS_BOOT, null) != boot || p.getLong(K_SESSIONS_UPTIME, Long.MAX_VALUE) > SystemClock.elapsedRealtime()) return emptyMap()
        return EqSessions.decode(p.getString(K_SESSIONS, null))
    }

    fun saveSessions(sessions: Map<Int, String?>, boot: String) {
        p.edit()
            .putString(K_SESSIONS, EqSessions.encode(sessions))
            .putString(K_SESSIONS_BOOT, boot)
            .putLong(K_SESSIONS_UPTIME, SystemClock.elapsedRealtime())
            .commit()
    }

    private fun floats(text: String?) = text?.split(',')?.mapNotNull { it.trim().toFloatOrNull() }?.takeIf { it.isNotEmpty() }

    private companion object {
        const val FILE = "ecualizador"
        const val K_ENABLED = "enabled"
        const val K_PROTECT = "protect"
        const val K_PRESET = "preset"
        const val K_EDITED = "edited"
        const val K_CURVE = "curve"
        const val K_BASS = "bass"
        const val K_VOICE = "voice"
        const val K_EXTRA = "extra"
        const val K_VIRTUALIZER = "virtualizer"
        const val K_MODE = "mode"
        const val K_DEV_BUILD = "device_build"
        const val K_DEV_HZ = "device_hz"
        const val K_DEV_RANGE = "device_range"
        const val K_DEV_ORDER = "device_order"
        const val K_DEV_CAPS = "device_caps"
        const val K_DEV_EFFECTS = "device_effects"
        const val K_SESSIONS = "sessions"
        const val K_SESSIONS_BOOT = "sessions_boot"
        const val K_SESSIONS_UPTIME = "sessions_uptime"
    }
}
