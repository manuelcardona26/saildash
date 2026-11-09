package com.irvingguerra.litedash

import android.annotation.SuppressLint
import android.app.Activity
import android.app.ActivityManager
import android.app.ActivityOptions
import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.annotation.ChecksSdkIntAtLeast
import androidx.annotation.RequiresApi
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.edit
import com.irvingguerra.litedash.core.ui.Button
import com.irvingguerra.litedash.core.ui.Card
import com.irvingguerra.litedash.core.ui.Icon
import com.irvingguerra.litedash.core.ui.IconButton
import com.irvingguerra.litedash.core.ui.Icons
import com.irvingguerra.litedash.core.ui.Label
import com.irvingguerra.litedash.core.ui.LocalColors
import com.irvingguerra.litedash.core.ui.SwitchRow
import com.irvingguerra.litedash.core.ui.Typography
import com.irvingguerra.litedash.feature.apps.App
import com.irvingguerra.litedash.feature.apps.rememberApps
import java.lang.reflect.Method
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * SailDash: pantalla dividida en Android 7 a 12 (API 24–31). Ahí el inicio no puede ser una mitad
 * (eso es de la 12L, [SplitScreen]): se ponen dos apps lado a lado, el mapa a la izquierda y la
 * música a la derecha, y la barra del medio se arrastra para cambiar el tamaño. Android 7 solo deja
 * dividir desde un servicio de accesibilidad ([SplitAccessibilityService]); lo demás es abrir cada
 * app a su tiempo:
 * 1. Se abre la de la izquierda y se espera a que tape el inicio (con tope de tiempo).
 * 2. El servicio divide: la de arriba queda acoplada a la izquierda y Android muestra las recientes.
 * 3. Se abre la de la derecha en la mitad libre.
 * Si ya estaba dividida, cada una se manda directo a su mitad. Nada corre si no se pide dividir.
 */
object SplitLegacy {
    /** Esta forma de dividir es la de Android 7 a 12; antes no hay pantalla dividida y desde la 12L la hace [SplitScreen]. */
    @get:ChecksSdkIntAtLeast(api = Build.VERSION_CODES.N)
    val applies: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && Build.VERSION.SDK_INT < Build.VERSION_CODES.S_V2

    /** Lo que falta para dividir, dicho antes de empezar (en un diálogo o en Ajustes). */
    enum class Problem(@StringRes val title: Int, @StringRes val text: Int) {
        /** El servicio de accesibilidad está apagado. */
        SERVICE_OFF(R.string.split_problem_service_title, R.string.split_problem_service),
        /** No hay app para alguna de las dos mitades. */
        NO_APPS(R.string.split_problem_apps_title, R.string.split_problem_apps),
    }

    // Tiempos pensados para una pantalla lenta (T3L): abrir un mapa en frío tarda segundos.
    /** Lo más que se espera a que la izquierda tape el inicio (si no, se divide igual: ya es la de arriba). */
    private const val COVER_MS = 5_000L
    /** Un respiro tras taparlo, para que termine de aparecer. */
    private const val SETTLE_MS = 300L
    /** Si el inicio no estaba al frente no hay cómo saber cuándo apareció: se espera esto. */
    private const val BLIND_MS = 1_500L
    /** Android 7–8: cada cuánto se pregunta si ya se dividió, y cuántas veces (3 s en total). */
    private const val DOCK_STEP_MS = 250L
    private const val DOCK_TRIES = 12
    /** Ya dividida: un respiro para que aparezcan las recientes en la otra mitad. */
    private const val RECENTS_MS = 400L
    /** Android 9–12: no se puede preguntar si ya se dividió; se espera esto. */
    private const val DOCK_BLIND_MS = 1_500L
    /** Ya estaba dividida: entre mandar la izquierda a su mitad y abrir la derecha. */
    private const val ADJACENT_MS = 500L
    /** Android 7–8: tras abrir la derecha, cuándo revisar que la división siga (una app que no se deja la deshace). */
    private const val VERIFY_MS = 2_000L

    /** Android 7–8: no hay división (WindowManager.DOCKED_INVALID). */
    private const val DOCKED_NONE = -1

    private val scope by lazy { MainScope() }
    private var job: Job? = null
    private var store: SplitLegacyStore? = null
    /** El aviso de "prende el servicio" al abrir un mapa sale una vez por encendido, no cada vez. */
    private var hinted = false

    /** Lo elegido para cada mitad (se lee la primera vez que se pide; solo en el hilo principal). */
    fun store(context: Context): SplitLegacyStore =
        store ?: SplitLegacyStore(context.applicationContext).also { store = it }

    /**
     * ¿Esta pantalla deja poner dos apps lado a lado? Lo apaga el fabricante (o tener poca memoria).
     * En Android 7, "Forzar actividades para que cambien de tamaño" (Opciones de desarrollador) la
     * prende aunque el fabricante la haya apagado; desde Android 8 ya no.
     */
    @SuppressLint("DiscouragedApi") // Los ajustes del sistema no tienen constante pública: se buscan por nombre.
    fun supported(context: Context): Boolean {
        if (!applies) return false
        val r = Resources.getSystem()
        fun config(name: String): Boolean {
            val id = r.getIdentifier(name, "bool", "android")
            return id == 0 || r.getBoolean(id)
        }
        val lowRam = context.getSystemService(ActivityManager::class.java)?.isLowRamDevice == true
        val multiWindow = !lowRam && config("config_supportsMultiWindow")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) return multiWindow && config("config_supportsSplitScreenMultiWindow")
        val forced = runCatching { Settings.Global.getInt(context.contentResolver, FORCE_RESIZABLE, 0) != 0 }.getOrDefault(false)
        return multiWindow || forced
    }

    /** "Forzar actividades para que cambien de tamaño" (Settings.Global, oculta). */
    private const val FORCE_RESIZABLE = "force_resizable_activities"

    /**
     * "Dividir" (barra lateral) y "Probar" (Ajustes): las dos apps elegidas, lado a lado. null = ya
     * empezó; si no, lo que falta para poder hacerlo.
     */
    fun splitPair(context: Context): Problem? {
        if (!applies) return null
        if (!SplitAccessibilityService.connected) return Problem.SERVICE_OFF
        val d = dependencies(context)
        val store = store(context)
        val installed = installed(context, d, store)
        val left = SplitPair.left(installed, store.left) ?: return Problem.NO_APPS
        val right = SplitPair.right(installed, store.right, d.music.preferred, avoid = left) ?: return Problem.NO_APPS
        val pm = context.packageManager
        val leftIntent = pm.getLaunchIntentForPackage(left) ?: return Problem.NO_APPS
        val rightIntent = pm.getLaunchIntentForPackage(right) ?: return Problem.NO_APPS
        begin(context, leftIntent, rightIntent, appName(context, d, left), appName(context, d, right), onFail = null)
        return null
    }

    /**
     * [SplitScreen.open] en Android 7 a 12: con la pantalla dividida prendida, el mapa o la música
     * que se abre desde SailDash va a su mitad y su pareja a la otra. false = que se abra normal.
     */
    fun open(context: Context, intent: Intent, pkg: String): Boolean {
        if (!applies) return false
        val d = dependencies(context)
        val store = store(context)
        if (!d.prefs.splitScreen || !store.autoPair) return false
        if (context.resources.configuration.orientation != Configuration.ORIENTATION_LANDSCAPE) return false
        if (!supported(context)) return false
        val installed = installed(context, d, store)
        // La pareja de ahora (lo elegido o lo de fábrica) también cuenta: AutoKit, si es la de la izquierda.
        val left = SplitPair.left(installed, store.left)
        val right = SplitPair.right(installed, store.right, d.music.preferred, avoid = left)
        val side = sideOf(context, d, pkg, left, right) ?: return false
        if (!SplitAccessibilityService.connected) {
            // Sin el servicio se abre normal, y se dice una vez cómo prenderlo.
            if (!hinted) toast(context, R.string.split_hint_service)
            hinted = true
            return false
        }
        val partner = when (side) {
            SplitSide.LEFT -> SplitPair.right(installed, store.right, d.music.preferred, avoid = pkg)
            SplitSide.RIGHT -> SplitPair.left(installed, store.left, avoid = pkg)
        } ?: return false
        val partnerIntent = context.packageManager.getLaunchIntentForPackage(partner)
            ?.addFlags(Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED) ?: return false
        val tapped = appName(context, d, pkg)
        val other = appName(context, d, partner)
        if (side == SplitSide.LEFT) {
            begin(context, intent, partnerIntent, tapped, other, onFail = null)
        } else {
            // Si no se pudo dividir, al menos se abre la que tocaste.
            val app = context.applicationContext
            begin(context, partnerIntent, intent, other, tapped, onFail = { runCatching { app.startActivity(Intent(intent).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } })
        }
        return true
    }

    /**
     * De las apps que pueden ir en una mitad (las de fábrica, las elegidas y tu música), las
     * instaladas: de la lista de apps ya cargada si la hay (sin preguntarle a Android).
     */
    private fun installed(context: Context, d: Dependencies, store: SplitLegacyStore): Set<String> {
        val candidates = SplitPair.LEFT_APPS + SplitPair.RIGHT_APPS + listOfNotNull(store.left, store.right, d.music.preferred)
        d.apps.apps?.let { list ->
            val packages = list.mapTo(HashSet()) { it.pkg }
            return candidates.filterTo(HashSet()) { it in packages }
        }
        val pm = context.packageManager
        return candidates.filterTo(HashSet()) { pm.getLaunchIntentForPackage(it) != null }
    }

    /** El lado de [pkg]: la pareja de ahora, los mapas y la música conocidos y, desde Android 8, su categoría. */
    private fun sideOf(context: Context, d: Dependencies, pkg: String, left: String?, right: String?): SplitSide? {
        val maps = NAVIGATION_PACKAGES.toSet() + listOfNotNull(d.prefs.mapApp?.substringBefore('/'))
        val music = MUSIC_PACKAGES.toSet() + listOfNotNull(d.music.preferred)
        SplitPair.sideOf(pkg, left, right, maps, music)?.let { return it }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return null
        return when (runCatching { context.packageManager.getApplicationInfo(pkg, 0).category }.getOrNull()) {
            ApplicationInfo.CATEGORY_MAPS -> SplitSide.LEFT
            ApplicationInfo.CATEGORY_AUDIO, ApplicationInfo.CATEGORY_VIDEO -> SplitSide.RIGHT
            else -> null
        }
    }

    /** Arranca la división en el hilo principal (una a la vez: la nueva cancela la anterior). */
    private fun begin(context: Context, left: Intent, right: Intent, leftName: String, rightName: String, onFail: (() -> Unit)?) {
        if (!applies) return
        val app = context.applicationContext as Application
        val already = isSplit(context.findActivity())
        job?.cancel()
        job = scope.launch { split(app, already, left, right, leftName, rightName, onFail) }
    }

    @RequiresApi(Build.VERSION_CODES.N)
    private suspend fun split(app: Application, already: Boolean, left: Intent, right: Intent, leftName: String, rightName: String, onFail: (() -> Unit)?) {
        val sdk = Build.VERSION.SDK_INT
        val canPick = launchStack != null
        if (already) {
            // Ya dividida: cada una directo a su mitad, sin tocar el interruptor (la uniría).
            if (!startIn(app, left, SplitPair.launch(SplitSide.LEFT, sdk, already = true, canPick))) return toast(app, R.string.split_problem_open, leftName)
            delay(ADJACENT_MS)
            if (!startIn(app, right, SplitPair.launch(SplitSide.RIGHT, sdk, already = true, canPick))) toast(app, R.string.split_problem_open, rightName)
            return
        }
        // 1. La izquierda, a pantalla completa; se espera a que tape el inicio.
        val home = HomeWatch(app)
        try {
            val wasInFront = MainActivity.inFront
            if (!startIn(app, left, SplitPair.launch(SplitSide.LEFT, sdk, already = false, canPick))) return toast(app, R.string.split_problem_open, leftName)
            if (wasInFront) home.awaitCovered(COVER_MS) else delay(BLIND_MS)
            delay(SETTLE_MS)
        } finally {
            home.close()
        }
        // Volviste al inicio mientras abría (o la app no salió): no se divide nada.
        if (MainActivity.inFront) return
        // 2. Dividir: la de arriba (la izquierda) queda acoplada.
        if (!SplitAccessibilityService.toggleSplit()) {
            toast(app, R.string.split_hint_service)
            return onFail?.invoke() ?: Unit
        }
        val docked = awaitDocked()
        if (docked == false) {
            toast(app, R.string.split_failed, leftName)
            return onFail?.invoke() ?: Unit
        }
        // 3. La derecha, en la mitad libre.
        if (!startIn(app, right, SplitPair.launch(SplitSide.RIGHT, sdk, already = false, canPick))) return toast(app, R.string.split_problem_open, rightName)
        // Android 7–8: una app que no se deja dividir deshace la división al abrirse; se dice cuál fue.
        if (docked == true) {
            delay(VERIFY_MS)
            if (dockedSide() == DOCKED_NONE) toast(app, R.string.split_failed, rightName)
        }
    }

    /**
     * Espera a que Android acople la izquierda. true = dividida; false = no se dividió (la app no se
     * deja o la pantalla no lo hace); null = no se puede saber (Android 9–12: se espera un rato fijo).
     * Solo corre justo después de pedir dividir y se rinde a los 3 segundos.
     */
    private suspend fun awaitDocked(): Boolean? {
        if (dockedSide() == null) {
            delay(DOCK_BLIND_MS)
            return null
        }
        repeat(DOCK_TRIES) {
            delay(DOCK_STEP_MS)
            val side = dockedSide() ?: return null
            if (side != DOCKED_NONE) {
                delay(RECENTS_MS)
                return true
            }
        }
        return false
    }

    /** ¿Ya está dividida? Android 7–8 lo dice; después, el inicio está en una mitad. */
    private fun isSplit(activity: Activity?): Boolean {
        dockedSide()?.let { return it != DOCKED_NONE }
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && activity?.isInMultiWindowMode == true
    }

    /** Abre [intent] como dice [launch]; false = no se pudo. */
    @RequiresApi(Build.VERSION_CODES.N)
    private fun startIn(context: Context, intent: Intent, launch: SplitLaunch): Boolean {
        val i = Intent(intent).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (launch.adjacent) i.addFlags(Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT)
        val options = launch.stack?.let(::stackOptions)
        return runCatching { context.startActivity(i, options) }.isSuccess
    }

    /*
     * Android 7 y 8 no dicen en público si la pantalla está dividida ni dejan escoger la mitad al
     * abrir una app; sus funciones ocultas sí (en esas versiones Android no las bloquea). Desde
     * Android 9 ni se intenta. Si algo falla, se sigue sin ellas.
     */

    /** El WindowManager del sistema (IWindowManager) y su getDockedStackSide(); se busca una vez. */
    private val dockedSideCall: Pair<Any, Method>? by lazy { findDockedSideCall() }

    @SuppressLint("PrivateApi", "DiscouragedPrivateApi", "BlockedPrivateApi", "SoonBlockedPrivateApi")
    private fun findDockedSideCall(): Pair<Any, Method>? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) return null
        return runCatching {
            val wm = Class.forName("android.view.WindowManagerGlobal").getMethod("getWindowManagerService").invoke(null)!!
            wm to wm.javaClass.getMethod("getDockedStackSide")
        }.getOrNull()
    }

    /** Android 7–8: de qué lado está la app acoplada ([DOCKED_NONE] = sin división); null = no se puede saber. */
    private fun dockedSide(): Int? {
        val (wm, method) = dockedSideCall ?: return null
        return runCatching { method.invoke(wm) as Int }.getOrNull()
    }

    /** ActivityOptions.setLaunchStackId(int) de Android 7–8; se busca una vez. */
    private val launchStack: Method? by lazy { findLaunchStack() }

    @SuppressLint("PrivateApi", "DiscouragedPrivateApi", "BlockedPrivateApi", "SoonBlockedPrivateApi")
    private fun findLaunchStack(): Method? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) return null
        return runCatching { ActivityOptions::class.java.getMethod("setLaunchStackId", Int::class.javaPrimitiveType) }.getOrNull()
    }

    /** Opciones para abrir en la pila [stack] (Android 7–8); null = sin poder escogerla. */
    private fun stackOptions(stack: Int): Bundle? {
        val method = launchStack ?: return null
        return runCatching { ActivityOptions.makeBasic().also { method.invoke(it, stack) }.toBundle() }.getOrNull()
    }

    private fun dependencies(context: Context) = (context.applicationContext as LiteDashApp).dependencies

    /** El nombre de la app como sale en el cajón (o el de Android). */
    private fun appName(context: Context, d: Dependencies, pkg: String): String =
        d.apps.apps?.firstOrNull { it.pkg == pkg }?.name
            ?: runCatching { context.packageManager.run { getApplicationLabel(getApplicationInfo(pkg, 0)).toString() } }.getOrDefault(pkg)

    /** Con otra app al frente, un aviso corto de Android es lo único que se ve (en el idioma de SailDash). */
    private fun toast(context: Context, @StringRes text: Int, vararg args: Any) {
        val app = context.applicationContext
        val message = contextWithLanguage(app).getString(text, *args)
        runCatching { Toast.makeText(app, message, Toast.LENGTH_LONG).show() }
    }
}

/**
 * Mira el inicio mientras se abre la izquierda: cuándo quedó tapado. Se registra antes de abrirla
 * (para no perder un cierre rápido) y se quita en cuanto termina la espera.
 */
private class HomeWatch(private val app: Application) : Application.ActivityLifecycleCallbacks {
    private val covered = CompletableDeferred<Unit>()

    init {
        app.registerActivityLifecycleCallbacks(this)
    }

    /** Espera a que el inicio quede tapado (onStop), a lo más [ms]. */
    suspend fun awaitCovered(ms: Long) {
        withTimeoutOrNull(ms) { covered.await() }
    }

    fun close() = app.unregisterActivityLifecycleCallbacks(this)

    override fun onActivityStopped(activity: Activity) {
        if (activity is MainActivity) covered.complete(Unit)
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
    override fun onActivityStarted(activity: Activity) = Unit
    override fun onActivityResumed(activity: Activity) = Unit
    override fun onActivityPaused(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
    override fun onActivityDestroyed(activity: Activity) = Unit
}

/** La actividad detrás de [this] (Compose da la actividad; la música, la app). */
private fun Context.findActivity(): Activity? {
    var c: Context? = this
    while (c != null) {
        if (c is Activity) return c
        c = (c as? ContextWrapper)?.baseContext
    }
    return null
}

/** Lo elegido para cada mitad, en su propio archivo de ajustes ("split_legacy"). */
class SplitLegacyStore(context: Context) {
    private val store = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    /** Paquete de la app de la izquierda; null = la de fábrica ([SplitPair.left]). */
    var left by mutableStateOf(store.getString(KEY_LEFT, null))
        private set
    /** Paquete de la app de la derecha; null = la de fábrica ([SplitPair.right]). */
    var right by mutableStateOf(store.getString(KEY_RIGHT, null))
        private set
    /** Al tocar un mapa o la música en SailDash, abrir también su pareja (de fábrica, sí). */
    var autoPair by mutableStateOf(store.getBoolean(KEY_AUTO_PAIR, true))
        private set

    fun pickLeft(pkg: String) { left = pkg; store.edit { putString(KEY_LEFT, pkg) } }
    fun pickRight(pkg: String) { right = pkg; store.edit { putString(KEY_RIGHT, pkg) } }
    fun turnAutoPair(on: Boolean) { autoPair = on; store.edit { putBoolean(KEY_AUTO_PAIR, on) } }

    companion object {
        private const val FILE = "split_legacy"
        private const val KEY_LEFT = "left"
        private const val KEY_RIGHT = "right"
        private const val KEY_AUTO_PAIR = "auto_pair"
    }
}

/**
 * Ajustes → Apps → Pantalla dividida en Android 7 a 12 (debajo de su interruptor): abrir la pareja
 * al tocar un mapa o la música, el servicio de accesibilidad, la app de cada mitad y Probar.
 */
@Composable
fun SplitLegacyOptions(d: Dependencies) {
    val c = LocalColors.current
    val context = LocalContext.current
    val store = remember { SplitLegacy.store(context) }
    val apps = rememberApps(d.apps).orEmpty()

    SwitchRow(stringResource(R.string.split_legacy_auto), store.autoPair, store::turnAutoPair, stringResource(R.string.split_legacy_auto_help))
    SplitServiceRow()

    // Lo que se ve marcado es lo que de verdad se abre: lo elegido o, si no, lo de fábrica.
    val installed = remember(apps) { apps.mapTo(HashSet()) { it.pkg } }
    val left = SplitPair.left(installed, store.left)
    val right = SplitPair.right(installed, store.right, d.music.preferred, avoid = left)
    val mapApp = d.prefs.mapApp?.substringBefore('/')
    val leftOffer = remember(apps, store.left, mapApp) {
        offer(apps, listOfNotNull(store.left) + SplitPair.LEFT_APPS + NAVIGATION_PACKAGES + listOfNotNull(mapApp))
    }
    val rightOffer = remember(apps, store.right, d.music.preferred) {
        offer(apps, listOfNotNull(store.right) + SplitPair.RIGHT_APPS + MUSIC_PACKAGES + listOfNotNull(d.music.preferred))
    }
    HalfPicker(R.string.split_pair_left, apps, leftOffer, left, store::pickLeft)
    HalfPicker(R.string.split_pair_right, apps, rightOffer, right, store::pickRight)

    var problem by remember { mutableStateOf<SplitLegacy.Problem?>(null) }
    // Lo que faltó la última vez, mientras siga faltando (al prender el servicio se quita solo).
    val missing = problem?.takeIf { it != SplitLegacy.Problem.SERVICE_OFF || !SplitAccessibilityService.connected }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Button(stringResource(R.string.split_test), { problem = SplitLegacy.splitPair(context) }, primary = true)
        missing?.let { Label(stringResource(it.text), Typography.small, c.warning, modifier = Modifier.weight(1f)) }
    }
    Label(stringResource(R.string.split_drag_tip), Typography.small, c.textMuted)
    Label(stringResource(R.string.split_force_tip), Typography.small, c.textMuted)
}

/** Las de [packages] que están instaladas, una vez cada una y en ese orden. */
private fun offer(apps: List<App>, packages: List<String>): List<App> =
    packages.distinct().mapNotNull { pkg -> apps.firstOrNull { it.pkg == pkg } }

/** El servicio de accesibilidad: si está activo y, si no, el botón para ir a prenderlo. */
@Composable
private fun SplitServiceRow() {
    val c = LocalColors.current
    val context = LocalContext.current
    val on = SplitAccessibilityService.connected
    // Prendido en Android pero sin conectarse todavía (raro): se dice distinto que apagado.
    val waiting = remember(on) { !on && SplitAccessibilityService.enabledInSettings(context) }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(if (on) Icons.done else Icons.warning, if (on) c.accent else c.warning, 24.dp)
        Column(Modifier.weight(1f)) {
            Label(stringResource(R.string.split_service_title), Typography.body)
            val status = when {
                on -> R.string.split_service_on
                waiting -> R.string.split_service_waiting
                else -> R.string.split_service_off
            }
            Label(stringResource(status), Typography.small, c.textMuted)
        }
        if (!on) Button(stringResource(R.string.split_service_open), { SplitAccessibilityService.openSettings(context) }, primary = true)
    }
    Label(stringResource(R.string.split_service_why), Typography.small, c.textMuted)
}

/** La app de una mitad: las conocidas que hay instaladas y "Otra app…" para buscar cualquiera. */
@Composable
private fun HalfPicker(@StringRes title: Int, apps: List<App>, offered: List<App>, selected: String?, onPick: (String) -> Unit) {
    val c = LocalColors.current
    var other by remember { mutableStateOf(false) }
    Label(stringResource(title), Typography.label, c.textMuted)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        for (app in offered) Button(app.name, { onPick(app.pkg); other = false }, primary = app.pkg == selected)
        Button(stringResource(R.string.music_other_app), { other = !other }, primary = other)
    }
    if (offered.isEmpty() && apps.isNotEmpty()) Label(stringResource(R.string.split_pair_none), Typography.small, c.warning)
    if (other) AppSearch(apps, selected) { onPick(it); other = false }
}

/**
 * Botón Dividir de la barra lateral: solo en Android 7 a 12, con la pantalla dividida prendida y si
 * la pantalla la permite. Si falta algo (el servicio, las apps), lo explica en un diálogo.
 */
@Composable
fun SplitRailButton(prefs: Preferences, size: Dp) {
    if (!SplitLegacy.applies || !prefs.splitScreen) return
    val context = LocalContext.current
    if (!remember { SplitLegacy.supported(context) }) return
    var problem by remember { mutableStateOf<SplitLegacy.Problem?>(null) }
    // Con su nombre debajo, en chico: el ícono solo no se entiende a la primera.
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(SplitIcon, { problem = SplitLegacy.splitPair(context) }, size = size)
        Label(stringResource(R.string.split_rail), Typography.small.copy(fontSize = 11.sp), LocalColors.current.textMuted, maxLines = 1)
    }
    problem?.let { SplitProblemDialog(it) { problem = null } }
}

@Composable
private fun SplitProblemDialog(problem: SplitLegacy.Problem, onClose: () -> Unit) {
    val c = LocalColors.current
    val context = LocalContext.current
    Dialog(onDismissRequest = onClose) {
        Card(Modifier.widthIn(max = 520.dp)) {
            Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Label(stringResource(problem.title), Typography.subtitle)
                Label(stringResource(problem.text), Typography.body, c.textMuted)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    val service = problem == SplitLegacy.Problem.SERVICE_OFF
                    if (service) Button(stringResource(R.string.split_service_open), { SplitAccessibilityService.openSettings(context); onClose() }, primary = true)
                    Button(stringResource(R.string.split_close), onClose, primary = !service)
                }
            }
        }
    }
}

/** Ícono de Dividir: la pantalla con la barra del medio (mismo trazo que los de core.ui). */
private val SplitIcon: ImageVector by lazy {
    val b = ImageVector.Builder(defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
    for (path in listOf("M4 5h16a1 1 0 0 1 1 1v12a1 1 0 0 1 -1 1h-16a1 1 0 0 1 -1 -1v-12a1 1 0 0 1 1 -1z", "M12 5v14")) {
        b.addPath(addPathNodes(path), stroke = SolidColor(Color.Black), strokeLineWidth = 2f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round)
    }
    b.build()
}
