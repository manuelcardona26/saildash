package com.irvingguerra.litedash

import android.app.ActivityManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.res.Resources
import android.hardware.display.DisplayManager
import android.media.AudioManager
import android.media.audiofx.AudioEffect
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.provider.Settings
import android.util.DisplayMetrics
import android.view.Display
import com.irvingguerra.litedash.feature.music.AppProbe
import java.io.File
import java.lang.reflect.Method
import java.util.Locale
import java.util.zip.ZipFile

/** Todo lo que se averiguó de la pantalla. Se arma una vez, fuera del hilo principal. */
internal class DiagReport(
    val summary: List<DiagVerdict>,
    /** Equipo, propiedades, memoria, pantalla dividida, sonido y ajustes (en pantalla y en el archivo). */
    val sections: List<DiagSection>,
    val apps: List<DiagApp>,
    /** Todos los ajustes ("system/clave = valor", sin lo privado): solo van al archivo. */
    val settingsDump: List<String>,
    /** Avisos que declaran las apps clave en su manifiesto: también se escuchan al grabar. */
    val actions: List<String>,
    val words: DiagWords,
    val createdAt: Long,
)

/** Las palabras de los renglones técnicos en el idioma de [context]. */
internal fun diagWords(context: Context) = DiagWords(
    yes = context.getString(R.string.diagnostico_yes),
    no = context.getString(R.string.diagnostico_no),
    missing = context.getString(R.string.diagnostico_missing),
    empty = context.getString(R.string.diagnostico_empty),
    hidden = context.getString(R.string.diagnostico_hidden),
    notThere = context.getString(R.string.diagnostico_not_there),
    deleted = context.getString(R.string.diagnostico_deleted),
    sticky = context.getString(R.string.diagnostico_sticky),
    unreadable = context.getString(R.string.diagnostico_unreadable),
    moreFormat = context.getString(R.string.diagnostico_more),
    system = context.getString(R.string.diagnostico_word_system),
    updated = context.getString(R.string.diagnostico_word_updated),
    disabled = context.getString(R.string.diagnostico_word_disabled),
    version = context.getString(R.string.diagnostico_word_version),
    opens = context.getString(R.string.diagnostico_word_opens),
    activities = context.getString(R.string.diagnostico_word_activities),
    services = context.getString(R.string.diagnostico_word_services),
    receivers = context.getString(R.string.diagnostico_word_receivers),
    providers = context.getString(R.string.diagnostico_word_providers),
    permission = context.getString(R.string.diagnostico_word_permission),
    codeInside = context.getString(R.string.diagnostico_word_code_inside),
    codeApart = context.getString(R.string.diagnostico_word_code_apart),
)

/**
 * Las tres tablas de ajustes del sistema (como en CarLights): ahí muchas pantallas escriben la
 * radio, las luces o el encendido con nombres propios. Se leen sin permisos.
 */
internal object DiagSettingsTables {
    val TABLES = listOf("system" to Settings.System.CONTENT_URI, "global" to Settings.Global.CONTENT_URI, "secure" to Settings.Secure.CONTENT_URI)

    /** Todos los ajustes: "tabla/clave" → valor. */
    fun readAll(context: Context): Map<String, String> {
        val all = HashMap<String, String>()
        for ((table, _) in TABLES) all.putAll(readTable(context, table))
        return all
    }

    fun readTable(context: Context, table: String): Map<String, String> {
        val uri = TABLES.firstOrNull { it.first == table }?.second ?: return emptyMap()
        val all = HashMap<String, String>()
        runCatching {
            context.contentResolver.query(uri, arrayOf("name", "value"), null, null, null)?.use { c ->
                while (c.moveToNext()) c.getString(0)?.let { all["$table/$it"] = c.getString(1) ?: "" }
            }
        }
        return all
    }

    /** Un ajuste; falla si Android no deja leerlo (no es lo mismo que "no existe", que es null). */
    fun read(context: Context, table: String, name: String): Result<String?> = runCatching {
        val r = context.contentResolver
        when (table) {
            "global" -> Settings.Global.getString(r, name)
            "secure" -> Settings.Secure.getString(r, name)
            else -> Settings.System.getString(r, name)
        }
    }
}

/** Arma el reporte. Lento (lee las apps y sus APK): siempre fuera del hilo principal. */
internal object DiagnosticsCollector {
    private const val MB = 1024L * 1024L
    private const val MAX_SETTINGS_SHOWN = 60
    private const val MAX_ACTIONS = 300

    /**
     * Propiedades del sistema que se leen (una lista fija, nunca todas): las de Android y las que
     * usan Allwinner y otras marcas de pantallas. Las que tu pantalla no tenga salen vacías.
     */
    private val PROPERTIES = listOf(
        "ro.board.platform", "ro.product.model", "ro.product.board", "ro.product.device", "ro.product.name",
        "ro.product.manufacturer", "ro.product.brand", "ro.hardware", "ro.boot.hardware", "ro.product.cpu.abi",
        "ro.build.display.id", "ro.build.version.release", "ro.build.version.sdk", "ro.build.version.incremental",
        "ro.build.date", "ro.build.type", "ro.build.tags", "ro.build.user", "ro.build.host", "ro.build.characteristics",
        "ro.sf.lcd_density", "ro.config.low_ram", "dalvik.vm.heapgrowthlimit", "dalvik.vm.heapsize", "ro.zygote",
        "ro.product.locale", "ro.debuggable", "ro.secure", "ro.adb.secure", "service.adb.tcp.port", "persist.adb.tcp.port",
        "ro.kernel.qemu",
        // Allwinner y pantallas de carro (FYT, MTCD…): cada marca usa los suyos.
        "ro.product.firmware", "ro.sys.cputype", "ro.sw.version", "ro.fota.version", "ro.fyt.uiid", "ro.fyt.platform",
        "ro.product.customer", "ro.product.mcu", "ro.mcu.version", "sys.mcu.version", "persist.sys.mcu.version",
        "persist.sys.platform", "ro.car.version",
    )

    fun collect(context: Context, inMultiWindow: Boolean?, progress: (done: Int, total: Int) -> Unit): DiagReport {
        val w = diagWords(context)
        val sdk = Build.VERSION.SDK_INT
        val lowRam = context.getSystemService(ActivityManager::class.java)?.isLowRamDevice == true
        val split = splitInfo(context, w, lowRam, inMultiWindow)
        val audio = audioInfo(context, w)
        val settings = DiagSettingsTables.readAll(context)
        val apps = vendorApps(context, w, progress)

        val sections = listOf(
            DiagSection(context.getString(R.string.diagnostico_sec_device), deviceLines()),
            DiagSection(context.getString(R.string.diagnostico_sec_props), propertyLines(context)),
            DiagSection(context.getString(R.string.diagnostico_sec_memory), memoryLines(context, w)),
            DiagSection(context.getString(R.string.diagnostico_sec_split), split.second),
            DiagSection(context.getString(R.string.diagnostico_sec_audio), audio.lines),
            DiagSection(context.getString(R.string.diagnostico_sec_settings), settingsLines(context, settings, w)),
        )

        val keyApps = apps.filter { it.key }
        val summary = buildList {
            val name = DiagVersion.name(sdk)
            add(DiagVerdict(context.getString(R.string.diagnostico_android, name ?: "?", sdk), null))
            if (name != null && DiagVersion.fake(Build.VERSION.RELEASE, sdk)) {
                add(DiagVerdict(context.getString(R.string.diagnostico_android_fake, Build.VERSION.RELEASE, name), false))
            }
            add(
                when (split.first) {
                    DiagSplit.YES -> DiagVerdict(context.getString(R.string.diagnostico_split_yes), true)
                    DiagSplit.FORCED -> DiagVerdict(context.getString(R.string.diagnostico_split_forced), true)
                    DiagSplit.NO -> DiagVerdict(context.getString(R.string.diagnostico_split_no), false)
                    DiagSplit.OLD_ANDROID -> DiagVerdict(context.getString(R.string.diagnostico_split_old), false)
                },
            )
            val bands = audio.bands
            if (bands != null) {
                add(DiagVerdict(context.getString(R.string.diagnostico_eq_yes, bands), true))
                if (audio.busy) add(DiagVerdict(context.getString(R.string.diagnostico_eq_busy), null))
            } else {
                add(DiagVerdict(context.getString(R.string.diagnostico_eq_no), false))
            }
            add(DiagVerdict(context.getString(R.string.diagnostico_apps_found, apps.size, keyApps.size), null))
        }

        // Lo que escuchan las apps clave según su manifiesto: así la grabación oye también los avisos propios de tu marca.
        val actions = keyApps.flatMap { app -> DiagText.actionCandidates(app.details.flatMap { DiagText.manifestNames(it) }) }
            .distinct().take(MAX_ACTIONS)

        val dump = settings.keys.sorted().map { k -> "$k = ${DiagSettings.redact(k, settings.getValue(k), w.hidden)}" }
        return DiagReport(summary, sections, apps, dump, actions, w, System.currentTimeMillis())
    }

    // --- Equipo y Android ---

    private fun deviceLines(): List<String> = listOf(
        "Build.MANUFACTURER: ${Build.MANUFACTURER}",
        "Build.BRAND: ${Build.BRAND}",
        "Build.MODEL: ${Build.MODEL}",
        "Build.DEVICE: ${Build.DEVICE}",
        "Build.PRODUCT: ${Build.PRODUCT}",
        "Build.BOARD: ${Build.BOARD}",
        "Build.HARDWARE: ${Build.HARDWARE}",
        "Build.VERSION.RELEASE: ${Build.VERSION.RELEASE}",
        "Build.VERSION.SDK_INT: ${Build.VERSION.SDK_INT}" + (DiagVersion.name(Build.VERSION.SDK_INT)?.let { " (Android $it)" } ?: ""),
        "Build.VERSION.INCREMENTAL: ${Build.VERSION.INCREMENTAL}",
        "Build.VERSION.SECURITY_PATCH: ${Build.VERSION.SECURITY_PATCH}",
        "Build.DISPLAY: ${Build.DISPLAY}",
        "Build.ID: ${Build.ID} · TYPE: ${Build.TYPE} · TAGS: ${Build.TAGS}",
        "Build.TIME: ${DiagFormat.date(Build.TIME)}",
        "Build.FINGERPRINT: ${Build.FINGERPRINT}",
        "Build.SUPPORTED_ABIS: ${Build.SUPPORTED_ABIS.joinToString()}",
        "Build.BOOTLOADER: ${Build.BOOTLOADER}",
        "os.arch: ${System.getProperty("os.arch")} · kernel: ${System.getProperty("os.version")}",
    )

    // --- Propiedades del sistema ---

    /** android.os.SystemProperties.get por reflexión (es parte oculta de Android); null si no se puede. */
    private val getProperty: Method? by lazy {
        runCatching { Class.forName("android.os.SystemProperties").getMethod("get", String::class.java) }.getOrNull()
    }

    private fun property(key: String): String? = runCatching { getProperty?.invoke(null, key) as? String }.getOrNull()

    private fun propertyLines(context: Context): List<String> {
        val values = PROPERTIES.map { it to property(it).orEmpty() }
        val lines = values.filter { it.second.isNotEmpty() }.map { (k, v) -> "$k: $v" }.toMutableList()
        val empty = values.filter { it.second.isEmpty() }.map { it.first }
        if (empty.isNotEmpty()) lines += context.getString(R.string.diagnostico_props_empty, empty.joinToString())
        return lines
    }

    // --- Memoria y pantalla ---

    private fun memoryLines(context: Context, w: DiagWords): List<String> {
        val lines = ArrayList<String>()
        context.getSystemService(ActivityManager::class.java)?.let { am ->
            val mi = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
            lines += "MemoryInfo: total ${mi.totalMem / MB} MB · avail ${mi.availMem / MB} MB · threshold ${mi.threshold / MB} MB · lowMemory ${w.yn(mi.lowMemory)}"
            lines += "isLowRamDevice: ${w.yn(am.isLowRamDevice)} · memoryClass ${am.memoryClass} MB · largeMemoryClass ${am.largeMemoryClass} MB"
        }
        lines += "CPU: ${Runtime.getRuntime().availableProcessors()} · cpu0 max ${cpuMaxMhz()?.let { "$it MHz" } ?: "?"}"
        val m = context.resources.displayMetrics
        lines += "DisplayMetrics: ${m.widthPixels}×${m.heightPixels} px · densityDpi ${m.densityDpi} · density ${m.density} · xdpi ${m.xdpi} · ydpi ${m.ydpi}"
        runCatching {
            val display = context.getSystemService(DisplayManager::class.java)?.getDisplay(Display.DEFAULT_DISPLAY)
            if (display != null) {
                val real = DisplayMetrics()
                @Suppress("DEPRECATION") // getRealMetrics: la de reemplazo es de Android 11
                display.getRealMetrics(real)
                lines += "Display: ${real.widthPixels}×${real.heightPixels} px · ${display.refreshRate} Hz"
            }
        }
        val conf = context.resources.configuration
        lines += "Configuration: ${conf.screenWidthDp}×${conf.screenHeightDp} dp · smallestWidth ${conf.smallestScreenWidthDp} dp · fontScale ${conf.fontScale}"
        runCatching {
            val stat = StatFs(Environment.getDataDirectory().path)
            lines += "/data: avail ${DiagFormat.size(stat.availableBytes)} · total ${DiagFormat.size(stat.totalBytes)}"
        }
        return lines
    }

    /** La frecuencia máxima del primer núcleo, si Android deja leerla. */
    private fun cpuMaxMhz(): Long? = runCatching {
        File("/sys/devices/system/cpu/cpu0/cpufreq/cpuinfo_max_freq").readText().trim().toLong() / 1000
    }.getOrNull()

    // --- Pantalla dividida ---

    private fun splitInfo(context: Context, w: DiagWords, lowRam: Boolean, inMultiWindow: Boolean?): Pair<DiagSplit, List<String>> {
        val system = Resources.getSystem()
        fun bool(name: String): Boolean? {
            val id = system.getIdentifier(name, "bool", "android")
            return if (id == 0) null else runCatching { system.getBoolean(id) }.getOrNull()
        }
        val config = bool("config_supportsMultiWindow")
        val pm = context.packageManager
        val force = global(context, "force_resizable_activities")
        val freeform = global(context, "enable_freeform_support")
        val verdict = DiagSplit.of(Build.VERSION.SDK_INT, lowRam, config, force == "1")
        fun show(b: Boolean?) = b?.let { w.yn(it) } ?: w.missing
        val lines = listOf(
            "config_supportsMultiWindow: ${show(config)}",
            "config_supportsSplitScreenMultiWindow: ${show(bool("config_supportsSplitScreenMultiWindow"))}",
            "config_freeformWindowManagement: ${show(bool("config_freeformWindowManagement"))}",
            "FEATURE_FREEFORM_WINDOW_MANAGEMENT: ${w.yn(pm.hasSystemFeature("android.software.freeform_window_management"))}",
            "FEATURE_PICTURE_IN_PICTURE: ${w.yn(pm.hasSystemFeature("android.software.picture_in_picture"))}",
            "force_resizable_activities: ${force ?: w.missing}",
            "enable_freeform_support: ${freeform ?: w.missing}",
            "isLowRamDevice: ${w.yn(lowRam)}",
            "isInMultiWindowMode: ${show(inMultiWindow)}",
        )
        return verdict to lines
    }

    private fun global(context: Context, name: String): String? =
        runCatching { Settings.Global.getString(context.contentResolver, name) }.getOrNull()

    // --- Sonido ---

    private class AudioResult(val lines: List<String>, val bands: Int?, val busy: Boolean)

    private fun audioInfo(context: Context, w: DiagWords): AudioResult {
        val lines = ArrayList<String>()
        context.getSystemService(AudioManager::class.java)?.let { am ->
            runCatching {
                lines += "AudioManager: " + streamVolumes(am).joinToString(" · ") { (n, v, m) -> "$n $v/$m" }
                lines += "isVolumeFixed ${w.yn(am.isVolumeFixed)} · mode ${am.mode} · isMusicActive ${w.yn(am.isMusicActive)}"
                lines += "OUTPUT_SAMPLE_RATE ${am.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE)} · " +
                    "OUTPUT_FRAMES_PER_BUFFER ${am.getProperty(AudioManager.PROPERTY_OUTPUT_FRAMES_PER_BUFFER)}"
                lines += "getDevices(OUTPUTS): " + am.getDevices(AudioManager.GET_DEVICES_OUTPUTS).joinToString { d ->
                    DiagFormat.deviceType(d.type) + (d.productName?.toString()?.takeIf { it.isNotBlank() }?.let { " \"$it\"" } ?: "")
                }
                lines += "getDevices(INPUTS): " + am.getDevices(AudioManager.GET_DEVICES_INPUTS).joinToString { d ->
                    DiagFormat.deviceType(d.type) + (d.productName?.toString()?.takeIf { it.isNotBlank() }?.let { " \"$it\"" } ?: "")
                }
            }
        }
        val effects = runCatching { AudioEffect.queryEffects() }.getOrNull()
        lines += "AudioEffect.queryEffects: ${effects?.size ?: "error"}"
        effects?.forEach { d ->
            lines += "  ${DiagFormat.effectType(d.type.toString())} · \"${d.name}\" · ${d.implementor} · ${d.connectMode} · uuid ${d.uuid}"
        }
        // Prueba en vivo sobre la salida de todo el sonido (sesión 0): se crea, se lee y se suelta de una vez.
        // Prioridad 0 y sin cambiar nada: si otra app ya lo usa, manda la otra.
        var bands: Int? = null
        var busy = false
        lines += effect("Equalizer(0, 0)", { Equalizer(0, 0) }) { eq ->
            val n = eq.numberOfBands.toInt()
            val range = eq.bandLevelRange
            val centers = (0 until n).map { eq.getCenterFreq(it.toShort()) / 1000 }
            val levels = (0 until n).map { eq.getBandLevel(it.toShort()) }
            val presets = (0 until eq.numberOfPresets).mapNotNull { runCatching { eq.getPresetName(it.toShort()) }.getOrNull() }
            // Sin bandas no sirve de ecualizador.
            bands = n.takeIf { it > 0 }
            busy = eq.enabled || !eq.hasControl()
            "bands $n · ${centers.joinToString()} Hz · range ${range.getOrNull(0)}..${range.getOrNull(1)} mB · levels ${levels.joinToString()} · " +
                "presets ${presets.joinToString().ifEmpty { "-" }} · enabled ${w.yn(eq.enabled)} · hasControl ${w.yn(eq.hasControl())}"
        }
        lines += effect("BassBoost(0, 0)", { BassBoost(0, 0) }) { bb ->
            "strengthSupported ${w.yn(bb.strengthSupported)} · strength ${bb.roundedStrength} · enabled ${w.yn(bb.enabled)} · hasControl ${w.yn(bb.hasControl())}"
        }
        lines += effect("LoudnessEnhancer(0)", { LoudnessEnhancer(0) }) { le ->
            "targetGain ${le.targetGain} mB · enabled ${w.yn(le.enabled)} · hasControl ${w.yn(le.hasControl())}"
        }
        return AudioResult(lines, bands, busy)
    }

    /** Crea un efecto, lo describe y lo suelta pase lo que pase: "Equalizer(0, 0): ok · …" o el error. */
    private fun <T : AudioEffect> effect(name: String, create: () -> T, describe: (T) -> String): String {
        var effect: T? = null
        return try {
            val e = create().also { effect = it }
            "$name: ok · ${describe(e)} · \"${e.descriptor.name}\""
        } catch (t: Throwable) {
            "$name: error · ${t.javaClass.simpleName}: ${t.message}"
        } finally {
            effect?.let { runCatching { it.release() } }
        }
    }

    /** Volumen de cada tipo de sonido: (nombre, volumen, máximo). */
    internal fun streamVolumes(am: AudioManager): List<Triple<String, Int, Int>> =
        listOf(AudioManager.STREAM_VOICE_CALL, AudioManager.STREAM_SYSTEM, AudioManager.STREAM_RING, AudioManager.STREAM_MUSIC, AudioManager.STREAM_ALARM, AudioManager.STREAM_NOTIFICATION)
            .mapNotNull { s -> runCatching { Triple(DiagFormat.streamName(s), am.getStreamVolume(s), am.getStreamMaxVolume(s)) }.getOrNull() }

    // --- Ajustes ---

    private fun settingsLines(context: Context, settings: Map<String, String>, w: DiagWords): List<String> {
        val shown = settings.keys.filter { DiagSettings.interesting(it) }.sorted()
        val lines = ArrayList<String>()
        lines += context.getString(R.string.diagnostico_settings_count, settings.size)
        if (shown.isEmpty()) lines += context.getString(R.string.diagnostico_settings_none)
        for (k in shown.take(MAX_SETTINGS_SHOWN)) lines += "$k = ${DiagText.clip(DiagSettings.redact(k, settings.getValue(k), w.hidden), 80)}"
        if (shown.size > MAX_SETTINGS_SHOWN) lines += w.more(shown.size - MAX_SETTINGS_SHOWN)
        return lines
    }

    // --- Apps de fábrica ---

    private fun vendorApps(context: Context, w: DiagWords, progress: (Int, Int) -> Unit): List<DiagApp> {
        val pm = context.packageManager
        @Suppress("DEPRECATION") // getInstalledPackages(Int): la de PackageInfoFlags es de Android 13
        val installed = runCatching { pm.getInstalledPackages(0) }.getOrDefault(emptyList())
        val picked = ArrayList<Triple<PackageInfo, String, Boolean>>()
        for (info in installed) {
            val ai = info.applicationInfo ?: continue
            val pkg = info.packageName ?: continue
            if (pkg == context.packageName || DiagApps.noise(pkg)) continue
            val system = ai.flags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
            // Las de serie se deciden por el paquete: cargar el nombre de cada app es lo lento.
            val stock = DiagApps.stock(pkg)
            val label = if (stock) "" else label(pm, ai, pkg)
            val kind = DiagApps.classify(pkg, label, system) ?: continue
            picked += Triple(info, if (stock) label(pm, ai, pkg) else label, kind == DiagAppKind.KEY)
        }
        // Primero las clave; luego por nombre.
        picked.sortWith(compareBy<Triple<PackageInfo, String, Boolean>> { !it.third }.thenBy { it.second.lowercase(Locale.ROOT) })
        return picked.mapIndexed { i, (info, label, key) ->
            progress(i + 1, picked.size)
            describeApp(context, pm, info, label, key, w)
        }
    }

    private fun label(pm: PackageManager, ai: ApplicationInfo, pkg: String) =
        runCatching { pm.getApplicationLabel(ai).toString() }.getOrDefault(pkg).ifBlank { pkg }

    private fun describeApp(context: Context, pm: PackageManager, info: PackageInfo, label: String, key: Boolean, w: DiagWords): DiagApp {
        val ai = info.applicationInfo
        val pkg = info.packageName
        val flags = ai?.flags ?: 0
        val system = flags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
        val enabled = ai?.enabled ?: true
        val code = versionCode(info)
        val apk = ai?.sourceDir.orEmpty()
        val lines = ArrayList<String>()
        lines += buildString {
            append(w.version).append(' ').append(info.versionName ?: "?").append(" (").append(code).append(')')
            if (system) append(" · ").append(w.system)
            if (flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP != 0) append(" · ").append(w.updated)
            if (!enabled) append(" · ").append(w.disabled)
        }
        val bytes = runCatching { File(apk).length() }.getOrDefault(0L)
        // ¿El código viene dentro del APK? Si no (odex), para estudiarla hace falta también lo de su carpeta oat.
        val dex = if (key) runCatching { ZipFile(apk).use { it.getEntry("classes.dex") != null } }.getOrNull() else null
        lines += "apk: $apk · ${DiagFormat.size(bytes)}" + (dex?.let { " · " + if (it) w.codeInside else w.codeApart } ?: "")
        info.sharedUserId?.let { lines += "sharedUserId: $it" }
        runCatching { pm.getLaunchIntentForPackage(pkg)?.component?.className }.getOrNull()?.let { lines += "${w.opens}: ${short(it, pkg)}" }
        lines += components(pm, pkg, w)
        if (key) {
            // Lo que deja ver su APK: sobre todo los nombres de su manifiesto (las acciones que escucha).
            // Sus partes exportadas ya van arriba, con más detalle.
            AppProbe.describe(context, pkg).lines().map { it.trim() }
                .filter { it.isNotEmpty() && !it.startsWith("exportados:") }
                .forEach { lines += it }
        }
        return DiagApp(label, pkg, system, key, apk, dex == false, lines)
    }

    @Suppress("DEPRECATION") // versionCode: el largo (longVersionCode) es de Android 9
    private fun versionCode(info: PackageInfo): Long =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) info.longVersionCode else info.versionCode.toLong()

    /** Las partes exportadas (las que otra app como SailDash puede abrir o llamar), por tipo. */
    @Suppress("DEPRECATION") // getPackageInfo(String, Int): la de PackageInfoFlags es de Android 13
    private fun components(pm: PackageManager, pkg: String, w: DiagWords): List<String> {
        val all = PackageManager.GET_ACTIVITIES or PackageManager.GET_SERVICES or PackageManager.GET_RECEIVERS or PackageManager.GET_PROVIDERS
        val full = runCatching { pm.getPackageInfo(pkg, all) }.getOrNull()
        // Una app enorme puede no caber de una sola vez: entonces por partes.
        fun part(flag: Int): PackageInfo? = full ?: runCatching { pm.getPackageInfo(pkg, flag) }.getOrNull()
        fun perm(p: String?) = p?.let { " [${w.permission} $it]" }.orEmpty()
        val activities = part(PackageManager.GET_ACTIVITIES)?.activities.orEmpty().filter { it.exported }.map { short(it.name, pkg) + perm(it.permission) }
        val services = part(PackageManager.GET_SERVICES)?.services.orEmpty().filter { it.exported }.map { short(it.name, pkg) + perm(it.permission) }
        val receivers = part(PackageManager.GET_RECEIVERS)?.receivers.orEmpty().filter { it.exported }.map { short(it.name, pkg) + perm(it.permission) }
        val providers = part(PackageManager.GET_PROVIDERS)?.providers.orEmpty().filter { it.exported }
            .map { short(it.name, pkg) + " {${it.authority}}" + perm(it.readPermission) }
        return listOf(w.activities to activities, w.services to services, w.receivers to receivers, w.providers to providers)
            .filter { it.second.isNotEmpty() }
            .map { (title, items) -> DiagFormat.list(title, items, w) }
    }

    /** "com.marca.radio.RadioService" → ".RadioService" dentro de su propio paquete. */
    private fun short(name: String, pkg: String) = if (name.startsWith("$pkg.")) name.removePrefix(pkg) else name
}
