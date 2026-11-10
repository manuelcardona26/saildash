package com.irvingguerra.litedash

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/*
 * SailDash: diagnóstico de tu pantalla (Ajustes → Sistema → Diagnóstico). Aquí va solo lo que no
 * necesita Android, para probarlo en el PC: qué apps son de fábrica, qué cambió en los ajustes,
 * cómo se escriben los avisos y cómo queda el reporte en texto.
 */

/** Una parte del reporte: su título y sus renglones ("clave: valor"). */
internal data class DiagSection(val title: String, val lines: List<String>)

/** Un renglón del resumen: [good] true = bien, false = ojo, null = solo informa. */
internal data class DiagVerdict(val text: String, val good: Boolean?)

/** Las palabras de los renglones técnicos, en el idioma de la app (las pruebas usan las de español). */
internal class DiagWords(
    val yes: String,
    val no: String,
    val missing: String,
    val empty: String,
    val hidden: String,
    val notThere: String,
    val deleted: String,
    val sticky: String,
    val unreadable: String,
    /** "y %1$d más". */
    val moreFormat: String,
    val system: String,
    val updated: String,
    val disabled: String,
    val version: String,
    val opens: String,
    val activities: String,
    val services: String,
    val receivers: String,
    val providers: String,
    val permission: String,
    val codeInside: String,
    val codeApart: String,
) {
    fun yn(value: Boolean) = if (value) yes else no
    fun more(count: Int) = String.format(Locale.US, moreFormat, count)
}

/** Una app de fábrica (o que suena a radio, sonido, carro…) como la cuenta el reporte. */
internal data class DiagApp(
    val label: String,
    val pkg: String,
    val system: Boolean,
    /** Clave: radio, sonido, carro, reversa, Bluetooth o MCU. Se estudia a fondo y su APK se copia. */
    val key: Boolean,
    val sourceDir: String,
    /** Su código no viene en el APK (viene "odex" aparte): para leerla hace falta también eso. */
    val codeApart: Boolean,
    /** Lo demás, ya escrito: versión, APK, qué abre, sus partes exportadas y lo de su manifiesto. */
    val details: List<String>,
)

/** Qué tanto importa una app para el diagnóstico. */
internal enum class DiagAppKind { NORMAL, KEY }

/**
 * Qué apps instaladas se muestran. Las de fábrica casi siempre son de sistema y con nombres de la
 * marca o de lo que hacen ("com.xxx.radio", "Sonido"); lo de Google y lo interno de Android sobra.
 */
internal object DiagApps {
    /** Palabras que valen en cualquier parte del paquete o del nombre (en inglés y como las muestra la pantalla). */
    private val WORDS = listOf(
        "radio", "tuner", "blue", "mcu", "equal", "sound", "audio", "dsp", "revers", "camera", "avm",
        "setting", "launcher", "music", "media", "nwd", "syu", "fyt", "jyt", "hct", "choice", "kgl", "zxw",
        "microntek", "autochips", "goodocom", "carbit", "zlink", "manstep", "easyconn",
        "sonido", "musica", "música", "ecualiz", "ajuste", "camara", "cámara", "coche", "carro", "mi auto", "emisora", "volante", "fabrica", "fábrica",
    )

    /** Palabras cortas: solo al empezar un pedazo del nombre ("com.tw.bt", "canbus", "FmRadio"). */
    private val SHORT = listOf("fm", "bt", "car", "can", "eq", "back", "rear")

    /** Las clave: radio, sonido y ecualizador, carro y CAN, reversa y cámara, Bluetooth, MCU, encendido y luces. */
    private val KEY_WORDS = listOf(
        "radio", "tuner", "sound", "audio", "equal", "dsp", "revers", "camera", "avm", "blue", "mcu", "canbus",
        "light", "illum", "service", "factory",
        "sonido", "ecualiz", "emisora", "camara", "cámara", "coche", "carro", "mi auto", "volante", "fabrica", "fábrica",
    )
    private val KEY_SHORT = listOf("fm", "bt", "car", "can", "eq", "back", "rear", "acc", "boot")

    /** Marcas de pantallas de carro: aunque vengan como app normal, son de fábrica y clave. */
    private val BRANDS = listOf("nwd", "syu", "fyt", "jyt", "hct", "kgl", "zxw", "microntek", "autochips", "goodocom")

    /** Pedazos que empiezan como una palabra corta pero son otra cosa ("backup", "carrier"…). */
    private val NOT_SHORT = listOf("backup", "background", "carrier", "cancel", "canvas", "card", "carousel", "access", "account", "accel", "accent", "bootanim")

    /** Lo de Google y lo interno de Android: nunca se muestra. */
    private val NOISE = listOf(
        "com.google.", "com.android.providers.", "com.android.vending", "com.android.chrome",
        "com.android.inputmethod", "com.android.internal.", "com.android.cts", "com.android.server.",
    )

    /**
     * Apps de serie de Android (AOSP): se muestran solo si suenan a algo útil (Ajustes, MusicFX…) y
     * nunca son clave. Una "com.android.fmradio" no es de serie: la puso el fabricante.
     */
    private val STOCK = setOf(
        "com.android.backupconfirm", "com.android.bluetooth", "com.android.bluetoothmidiservice", "com.android.browser",
        "com.android.calculator2", "com.android.calendar", "com.android.calllogbackup", "com.android.camera", "com.android.camera2",
        "com.android.captiveportallogin", "com.android.carrierconfig", "com.android.carrierdefaultapp", "com.android.cellbroadcastreceiver",
        "com.android.certinstaller", "com.android.companiondevicemanager", "com.android.contacts", "com.android.defcontainer",
        "com.android.deskclock", "com.android.development", "com.android.dialer", "com.android.documentsui", "com.android.egg",
        "com.android.email", "com.android.emergency", "com.android.exchange", "com.android.externalstorage", "com.android.gallery",
        "com.android.gallery3d", "com.android.htmlviewer", "com.android.inputdevices", "com.android.keychain", "com.android.launcher",
        "com.android.launcher2", "com.android.launcher3", "com.android.location.fused", "com.android.managedprovisioning",
        "com.android.messaging", "com.android.mms", "com.android.mms.service", "com.android.mtp", "com.android.music",
        "com.android.musicfx", "com.android.nfc", "com.android.onetimeinitializer", "com.android.packageinstaller",
        "com.android.pacprocessor", "com.android.permissioncontroller", "com.android.phone", "com.android.printspooler",
        "com.android.protips", "com.android.provision", "com.android.proxyhandler", "com.android.quicksearchbox",
        "com.android.retaildemo", "com.android.settings", "com.android.sharedstoragebackup", "com.android.shell",
        "com.android.smspush", "com.android.soundrecorder", "com.android.statementservice", "com.android.stk",
        "com.android.storagemanager", "com.android.systemui", "com.android.traceur", "com.android.vpndialogs",
        "com.android.webview", "com.android.localtransport", "com.android.simappdialog", "com.android.dynsystem",
        "com.android.networkstack", "com.android.apps.tag", "com.android.basicsmsreceiver", "com.android.hotspot2",
        "com.android.bips", "com.android.bookmarkprovider", "com.android.facelock", "com.android.sdksetup",
        "com.android.gesture.builder", "com.android.widgetpreview", "com.android.customlocale2", "com.android.smoketest",
        "com.android.musicvis", "com.android.galaxy4", "com.android.magicsmoke", "com.android.noisefield", "com.android.phasebeam",
    )

    fun noise(pkg: String) = pkg == "android" || NOISE.any { pkg.startsWith(it) }

    fun stock(pkg: String) = pkg in STOCK || pkg.startsWith("android.") || pkg.startsWith("com.android.dreams.") ||
        pkg.startsWith("com.android.wallpaper") || pkg.startsWith("com.android.printservice")

    /** null = no se muestra. [label] puede ir vacío (en las de serie no se carga: es lo lento). */
    fun classify(pkg: String, label: String, system: Boolean): DiagAppKind? {
        if (noise(pkg)) return null
        val word = matches(WORDS, SHORT, pkg, label)
        if (stock(pkg)) return if (word) DiagAppKind.NORMAL else null
        if (!system && !word) return null
        val key = matches(KEY_WORDS + BRANDS, KEY_SHORT, pkg, label) && (system || BRANDS.any { it in pkg.lowercase(Locale.ROOT) })
        return if (key) DiagAppKind.KEY else DiagAppKind.NORMAL
    }

    private fun matches(words: List<String>, short: List<String>, pkg: String, label: String): Boolean {
        val text = "$pkg $label".lowercase(Locale.ROOT)
        if (words.any { it in text }) return true
        return tokens("$pkg $label").any { t -> NOT_SHORT.none { t.startsWith(it) } && short.any { t.startsWith(it) } }
    }

    /** "cn.manstep.phonemirrorBox" → cn, manstep, phonemirror, box. */
    fun tokens(text: String): List<String> =
        text.replace(CAMEL, "$1 $2").lowercase(Locale.ROOT).split(SEPARATORS).filter { it.isNotEmpty() }

    private val CAMEL = Regex("([a-z])([A-Z])")
    private val SEPARATORS = Regex("[^\\p{L}\\p{N}]+")
}

/** Ajustes del sistema: qué cambió entre dos fotos y cuáles mostrar. */
internal object DiagSettings {
    private const val MAX_VALUE = 200

    /** Lo que cambió entre dos fotos ("tabla/clave" → valor): (clave, antes, después) en orden de clave; null = no estaba. */
    fun changes(before: Map<String, String>, after: Map<String, String>): List<Triple<String, String?, String?>> =
        (before.keys + after.keys).toSortedSet().mapNotNull { k ->
            val a = before[k]
            val b = after[k]
            if (a == b) null else Triple(k, a, b)
        }

    /** "system/backlight: 0 → 1". */
    fun line(key: String, before: String?, after: String?, w: DiagWords): String =
        "$key: ${show(before, w.notThere, w)} → ${show(after, w.deleted, w)}"

    private fun show(value: String?, absent: String, w: DiagWords) = when {
        value == null -> absent
        value.isEmpty() -> w.empty
        else -> DiagText.clip(value, MAX_VALUE)
    }

    /** Nombres que suenan a radio, luces, reversa, encendido o MCU (los que se ven en pantalla; al archivo van todos). */
    private val INTERESTING = Regex(
        "radio|tuner|freq|(^|_)fm|fm_|mcu|equal|(^|_)eq(_|$)|bass|treble|dsp|(^|_)bt(_|$)|bluetooth|(^|_)car_|canbus|(^|_)can(_|$)|" +
            "backcar|back_car|backlight|revers|rear|camera|illum|lamp|dimm|headl|(^|_)ill(_|$)|(^|_)light|night|(^|[_.])acc([_.]|on|off|$)|" +
            "ignit|amplif|nwd|syu|fyt|jyt|hct|kgl|zxw|microntek|steer|wheel",
        RegexOption.IGNORE_CASE,
    )

    /** Datos personales o identificadores: su valor no se escribe. */
    private val PRIVATE = Regex(
        "android_id|serial|imei|meid|mac_addr|bluetooth_address|bluetooth_name|device_name|password|passwd|token|secret|account|ssid",
        RegexOption.IGNORE_CASE,
    )

    fun interesting(key: String) = INTERESTING.containsMatchIn(key.substringAfter('/'))

    fun redact(key: String, value: String, hidden: String) = if (PRIVATE.containsMatchIn(key)) hidden else value
}

/** Cómo se escribe cada cosa en el reporte y en la lista de eventos. */
internal object DiagFormat {
    private const val MAX_VALUE = 120
    private const val MAX_EXTRAS = 30
    private const val MAX_ITEMS = 32
    private const val MAX_LINE = 700

    /** Los extras de un aviso: {estado=1, nombre="FM 91.1"}. */
    fun extras(map: Map<String, Any?>, w: DiagWords): String {
        if (map.isEmpty()) return "{}"
        val shown = map.entries.sortedBy { it.key }.take(MAX_EXTRAS).joinToString(", ") { (k, v) -> "$k=${value(v)}" }
        val rest = map.size - MAX_EXTRAS
        return "{" + shown + (if (rest > 0) ", " + w.more(rest) else "") + "}"
    }

    /** Un valor de un extra: textos entre comillas (se ve si va vacío), listas y mapas por dentro. */
    fun value(v: Any?): String = DiagText.clip(
        when (v) {
            null -> "null"
            is CharSequence -> "\"$v\""
            is IntArray -> items(v.toList())
            is LongArray -> items(v.toList())
            is ByteArray -> items(v.toList())
            is ShortArray -> items(v.toList())
            is FloatArray -> items(v.toList())
            is DoubleArray -> items(v.toList())
            is BooleanArray -> items(v.toList())
            is CharArray -> items(v.toList())
            is Array<*> -> items(v.toList())
            is Collection<*> -> items(v.toList())
            is Map<*, *> -> v.entries.joinToString(", ", "{", "}") { "${it.key}=${value(it.value)}" }
            else -> v.toString()
        },
        MAX_VALUE,
    )

    private fun items(list: List<Any?>): String =
        list.take(MAX_ITEMS).joinToString(", ", "[", if (list.size > MAX_ITEMS) ", …]" else "]") { value(it) }

    /** "com.fyt.boot.ACCON {state=1}"; [extras] null = no se pudieron leer. */
    fun broadcast(action: String, extras: Map<String, Any?>?, sticky: Boolean, w: DiagWords): String = DiagText.clip(
        buildString {
            append(action)
            if (sticky) append(' ').append(w.sticky)
            append(' ').append(if (extras == null) w.unreadable else extras(extras, w))
        },
        MAX_LINE,
    )

    /** "MUSIC: 7 → 8 · VOICE_CALL 4/5, MUSIC 8/15…" ([streams]: nombre, volumen, máximo). */
    fun volume(type: Int, prev: Int?, now: Int?, streams: List<Triple<String, Int, Int>>): String = buildString {
        append(streamName(type)).append(": ").append(prev ?: "?").append(" → ").append(now ?: "?")
        if (streams.isNotEmpty()) append(" · ").append(streams.joinToString(", ") { (n, v, m) -> "$n $v/$m" })
    }

    /** Los tipos de volumen de Android (AudioManager.STREAM_*). */
    fun streamName(type: Int): String = when (type) {
        0 -> "VOICE_CALL"
        1 -> "SYSTEM"
        2 -> "RING"
        3 -> "MUSIC"
        4 -> "ALARM"
        5 -> "NOTIFICATION"
        6 -> "BLUETOOTH_SCO"
        7 -> "SYSTEM_ENFORCED"
        8 -> "DTMF"
        9 -> "TTS"
        10 -> "ACCESSIBILITY"
        else -> "STREAM_$type"
    }

    /** Las salidas y entradas de sonido (AudioDeviceInfo.TYPE_*): FM_TUNER o BUS dicen mucho de cómo va el audio. */
    fun deviceType(type: Int): String = when (type) {
        1 -> "BUILTIN_EARPIECE"
        2 -> "BUILTIN_SPEAKER"
        3 -> "WIRED_HEADSET"
        4 -> "WIRED_HEADPHONES"
        5 -> "LINE_ANALOG"
        6 -> "LINE_DIGITAL"
        7 -> "BLUETOOTH_SCO"
        8 -> "BLUETOOTH_A2DP"
        9 -> "HDMI"
        10 -> "HDMI_ARC"
        11 -> "USB_DEVICE"
        12 -> "USB_ACCESSORY"
        13 -> "DOCK"
        14 -> "FM"
        15 -> "BUILTIN_MIC"
        16 -> "FM_TUNER"
        17 -> "TV_TUNER"
        18 -> "TELEPHONY"
        19 -> "AUX_LINE"
        20 -> "IP"
        21 -> "BUS"
        22 -> "USB_HEADSET"
        23 -> "HEARING_AID"
        24 -> "BUILTIN_SPEAKER_SAFE"
        25 -> "REMOTE_SUBMIX"
        26 -> "BLE_HEADSET"
        27 -> "BLE_SPEAKER"
        29 -> "HDMI_EARC"
        30 -> "BLE_BROADCAST"
        31 -> "DOCK_ANALOG"
        else -> "TYPE_$type"
    }

    /** El nombre de un tipo de efecto de sonido (su UUID, como lo da AudioEffect.queryEffects). */
    fun effectType(uuid: String): String = when (uuid.lowercase(Locale.ROOT)) {
        "0bed4300-ddd6-11db-8f34-0002a5d5c51b" -> "Equalizer"
        "0634f220-ddd4-11db-a0fc-0002a5d5c51b" -> "BassBoost"
        "37cc2c00-dddd-11db-8577-0002a5d5c51b" -> "Virtualizer"
        "47382d60-ddd8-11db-bf3a-0002a5d5c51b" -> "PresetReverb"
        "c2e5d5f0-94bd-4763-9cac-4e234d06839e" -> "EnvironmentalReverb"
        "fe3199be-aed0-413f-87bb-11260eb63cf1" -> "LoudnessEnhancer"
        "7261676f-6d75-6973-6372-70726f636573" -> "DynamicsProcessing"
        "e46b26a0-dddd-11db-8afd-0002a5d5c51b" -> "Visualizer"
        "7b491460-8d4d-11e0-bd61-0002a5d5c51b" -> "AcousticEchoCanceler"
        "0a8abfe0-654c-11e0-ba26-0002a5d5c51b" -> "AutomaticGainControl"
        "58b4b260-8e06-11e0-aa8e-0002a5d5c51b" -> "NoiseSuppressor"
        else -> uuid
    }

    /** "actividades exportadas (3): .Main, .Radio, .Ajustes" (con tope: "y 12 más"). */
    fun list(title: String, items: List<String>, w: DiagWords, max: Int = 40): String {
        val shown = items.take(max).joinToString(", ")
        val rest = items.size - max
        return "$title (${items.size}): $shown" + if (rest > 0) ", " + w.more(rest) else ""
    }

    /** "2.4 MB" o "1.2 GB". */
    fun size(bytes: Long): String {
        val mb = bytes / (1024.0 * 1024.0)
        return if (mb >= 1024) String.format(Locale.US, "%.1f GB", mb / 1024) else String.format(Locale.US, "%.1f MB", mb)
    }

    /** "2:05" (lo que le queda a la grabación). */
    fun mmss(ms: Long): String {
        val s = (ms.coerceAtLeast(0) + 999) / 1000
        return String.format(Locale.US, "%d:%02d", s / 60, s % 60)
    }

    /** "14:31:02". */
    fun clock(ms: Long, zone: TimeZone = TimeZone.getDefault()): String =
        SimpleDateFormat("HH:mm:ss", Locale.US).apply { timeZone = zone }.format(Date(ms))

    /** "2026-11-09 14:31". */
    fun date(ms: Long, zone: TimeZone = TimeZone.getDefault()): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).apply { timeZone = zone }.format(Date(ms))

    /** La carpeta de cada vez que se guarda: "2026-11-09_1431". */
    fun stamp(ms: Long, zone: TimeZone = TimeZone.getDefault()): String =
        SimpleDateFormat("yyyy-MM-dd_HHmm", Locale.US).apply { timeZone = zone }.format(Date(ms))

    /** Un evento en una línea: "14:31:02 · ajuste · system/backlight: 0 → 1". */
    fun event(time: Long, kind: String, text: String, zone: TimeZone = TimeZone.getDefault()) = "${clock(time, zone)} · $kind · $text"
}

/** Versiones de Android: lo que de verdad tiene la pantalla, diga lo que diga su menú. */
internal object DiagVersion {
    /** El número de Android de cada API; null si no se conoce. */
    fun name(sdk: Int): String? = when (sdk) {
        21 -> "5.0"
        22 -> "5.1"
        23 -> "6.0"
        24 -> "7.0"
        25 -> "7.1"
        26 -> "8.0"
        27 -> "8.1"
        28 -> "9"
        29 -> "10"
        30 -> "11"
        31 -> "12"
        32 -> "12L"
        33 -> "13"
        34 -> "14"
        35 -> "15"
        36 -> "16"
        else -> null
    }

    /** La versión que muestra la pantalla no es la de su API ("12" con API 25: un número puesto por el fabricante). */
    fun fake(release: String, sdk: Int): Boolean {
        val real = name(sdk) ?: return false
        val shown = release.trim().takeWhile { it.isDigit() }
        return shown.isNotEmpty() && shown != real.takeWhile { it.isDigit() }
    }
}

/** ¿Hay pantalla dividida? Android 7 la trae, pero el fabricante la puede apagar. */
internal enum class DiagSplit {
    OLD_ANDROID, YES, FORCED, NO;

    companion object {
        /**
         * Como lo decide Android 7: sin config_supportsMultiWindow (o con poca RAM) no hay, salvo que en
         * Opciones de desarrollador se fuerce ("force_resizable_activities", vale desde el siguiente arranque).
         */
        fun of(sdk: Int, lowRam: Boolean, config: Boolean?, forceResizable: Boolean): DiagSplit = when {
            sdk < 24 -> OLD_ANDROID
            !lowRam && config != false -> YES
            forceResizable -> FORCED
            else -> NO
        }
    }
}

/** El reporte en texto y lo que se saca del texto de AppProbe. */
internal object DiagText {
    private const val MANIFEST_PREFIX = "manifiesto:"

    /** Los nombres del manifiesto de una app, del texto de AppProbe ("manifiesto: a, b, c"). */
    fun manifestNames(probe: String): List<String> =
        probe.lineSequence().map { it.trim() }.firstOrNull { it.startsWith(MANIFEST_PREFIX) }
            ?.removePrefix(MANIFEST_PREFIX)?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty()

    /**
     * De esos nombres, los que parecen avisos (acciones): con ".action." o ".intent." o con la última
     * parte en mayúsculas ("com.marca.BACKCAR_ON"). Las clases ("RadioService") y los permisos no.
     */
    fun actionCandidates(names: List<String>): List<String> = names.filter { n ->
        val last = n.substringAfterLast('.')
        !n.contains(".permission.") && last.isNotEmpty() &&
            (n.contains(".action.") || n.contains(".intent.") || (last.any { it.isUpperCase() } && last.none { it.isLowerCase() }))
    }.distinct()

    /** El reporte en texto: encabezado y cada parte con su título ("== Equipo y Android =="). */
    fun report(header: List<String>, sections: List<DiagSection>): String = buildString {
        for (h in header) append(h).append(NEWLINE)
        for (s in sections) {
            append(NEWLINE).append("== ").append(s.title).append(" ==").append(NEWLINE)
            for (l in s.lines) append(l).append(NEWLINE)
        }
    }

    private const val NEWLINE = "\n"

    /** Corta un texto largo con "…". */
    fun clip(text: String, max: Int): String = if (text.length <= max) text else text.take(max - 1) + "…"
}

/** Lista con tope: al pasarse se botan los más viejos (y se cuentan aparte). */
internal class DiagLog<T>(private val max: Int) {
    private val items = ArrayDeque<T>()

    var dropped = 0
        private set

    val size get() = items.size

    /** Todos los que han llegado, también los que ya se botaron. */
    val total get() = items.size + dropped

    fun add(item: T) {
        items.addLast(item)
        while (items.size > max) {
            items.removeFirst()
            dropped++
        }
    }

    fun clear() {
        items.clear()
        dropped = 0
    }

    /** Los [n] más nuevos, el más nuevo primero (como se ven en pantalla). */
    fun newest(n: Int): List<T> = items.takeLast(n).asReversed()

    /** Todos, del más viejo al más nuevo (como van al archivo). */
    fun all(): List<T> = items.toList()
}
