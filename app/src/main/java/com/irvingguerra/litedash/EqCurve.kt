package com.irvingguerra.litedash

import java.util.Locale
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/*
 * SailDash: ecualizador. Aquí solo la lógica (sin Android ni Compose) para poder probarla: las
 * curvas y los estilos, cómo se pasan a las bandas que de verdad trae la pantalla (casi siempre 5,
 * nunca se supone cuántas) y el "Bajo protegido" (topes y margen para que no distorsione).
 */

/** Un punto de la curva: la frecuencia (Hz) y cuánto se sube o se baja ahí (dB). */
data class EqPoint(val hz: Float, val db: Float)

/**
 * Una curva: puntos de grave a agudo. Entre dos puntos se interpola en escala logarítmica de
 * frecuencia, que es como oye el oído (de 60 a 120 Hz hay lo mismo que de 1 a 2 kHz: una octava);
 * antes del primero y después del último queda plana. Se crea con [of], que la deja en orden.
 */
class EqCurve private constructor(val points: List<EqPoint>) {

    /** Cuántos dB pide la curva en [hz]. */
    fun at(hz: Float): Float {
        val p = points
        if (p.isEmpty()) return 0f
        if (!(hz > p.first().hz)) return p.first().db
        if (hz >= p.last().hz) return p.last().db
        var i = 1
        while (p[i].hz < hz) i++
        val a = p[i - 1]
        val b = p[i]
        val t = ln(hz / a.hz) / ln(b.hz / a.hz)
        return a.db + (b.db - a.db) * t
    }

    /** "60:3.5;230:-1": así se guarda. */
    fun encode(): String = points.joinToString(";") { "${number(it.hz)}:${number(it.db)}" }

    override fun equals(other: Any?) = other is EqCurve && other.points == points
    override fun hashCode() = points.hashCode()
    override fun toString() = encode()

    companion object {
        /** Sin puntos: 0 dB en todas partes. */
        val FLAT = EqCurve(emptyList())

        /** En orden de grave a agudo y sin lo inválido (si una frecuencia se repite, queda la primera). */
        fun of(points: List<EqPoint>) = EqCurve(
            points.filter { it.hz.isFinite() && it.hz > 0f && it.db.isFinite() }.sortedBy { it.hz }.distinctBy { it.hz },
        )

        /** Lo contrario de [encode]; null si no queda ningún punto que sirva. */
        fun decode(text: String?): EqCurve? {
            if (text.isNullOrBlank()) return null
            val points = text.split(';').mapNotNull { part ->
                val hz = part.substringBefore(':').trim().toFloatOrNull()
                val db = part.substringAfter(':', "").trim().toFloatOrNull()
                if (hz != null && db != null) EqPoint(hz, db) else null
            }
            return of(points).takeIf { it.points.isNotEmpty() }
        }

        private fun number(v: Float): String =
            if (v == 0f) "0" else String.format(Locale.US, "%.2f", v).trimEnd('0').trimEnd('.')
    }
}

/** Las frecuencias con que se definen los estilos: las de un ecualizador de 10 bandas. */
val EQ_STANDARD_HZ = listOf(31f, 62f, 125f, 250f, 500f, 1000f, 2000f, 4000f, 8000f, 16000f)

/** Un estilo de fábrica: su curva en las 10 frecuencias estándar y, si lo pide, volumen extra. */
class EqPreset(val id: String, gains: List<Float>, val extraDb: Float = 0f) {
    val curve = EqCurve.of(EQ_STANDARD_HZ.zip(gains) { hz, db -> EqPoint(hz, db) })
}

/**
 * Los estilos, pensados para un carro que ya trae bajos fuertes: el subgrave (31 Hz) casi no se
 * toca porque los parlantes de puerta no lo dan y solo distorsionan, el bajo sube con medida y los
 * 250–500 Hz (el "barro") bajan un poco para que las voces se oigan claras.
 */
object EqPresets {
    const val FLAT = "flat"
    const val TECHNO = "techno"
    const val DEEP_BASS = "deep_bass"
    const val VOICE = "voice"
    const val ROCK = "rock"
    const val PODCAST = "podcast"
    const val NIGHT = "night"

    // Grupo Urbano: para la música que más suena en el carro. Van aparte de los de siempre.
    const val REGGAETON = "urban_reggaeton"
    const val URBAN_TECHNO = "urban_techno"
    const val ELECTRONICA = "urban_electronica"

    //                             31   62  125   250   500    1k    2k   4k   8k  16k
    val normal = listOf(
        EqPreset(FLAT, listOf(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f)),
        // Bombo hondo pero controlado, agudos nítidos (hi-hats) y la voz presente.
        EqPreset(TECHNO, listOf(1f, 4f, 2.5f, -1f, -1.5f, -0.5f, 1f, 2f, 3f, 2f)),
        EqPreset(DEEP_BASS, listOf(2f, 6f, 4f, 1f, -1f, -1f, 0f, 1f, 1.5f, 1f)),
        // Presencia (1–4 kHz) arriba y barro (200–400 Hz) abajo.
        EqPreset(VOICE, listOf(-3f, -2f, -0.5f, -2.5f, -1.5f, 1f, 3f, 3.5f, 1.5f, 0f)),
        EqPreset(ROCK, listOf(1f, 3f, 2f, 0f, -1f, 0f, 1.5f, 3f, 3f, 2f)),
        // Sin retumbe y con un poco de volumen parejo para oír bien entre el ruido del carro.
        EqPreset(PODCAST, listOf(-6f, -4f, -1.5f, -1f, 0f, 1.5f, 3f, 2.5f, 0f, -1.5f), extraDb = 2f),
        // A volumen bajo el oído pierde bajos y agudos (curva de sonoridad): se compensan.
        EqPreset(NIGHT, listOf(2f, 4f, 2.5f, 0.5f, 0f, 0f, 0f, 1f, 2.5f, 3f), extraDb = 4f),
    )

    /**
     * Urbano. Pensados con Bajo protegido prendido: el golpe va en 62 Hz (lo que dan los parlantes de
     * puerta), el subgrave de 31 Hz sube poco porque ahí distorsiona, y los 250–500 Hz bajan para
     * que la voz y los platillos no se tapen.
     */
    //                             31   62  125   250   500    1k    2k   4k   8k  16k
    val urban = listOf(
        // Reggaetón: el dembow con golpe en 60 Hz, medios un poco hundidos y la voz clara en 2–4 kHz.
        EqPreset(REGGAETON, listOf(2.5f, 6.5f, 3f, -0.5f, -2f, -1f, 2f, 3f, 1f, 0.5f)),
        // Techno: bombo seco en 60–100 Hz, los medios-graves bajo control y los hi-hats brillantes en 8–12 kHz.
        EqPreset(URBAN_TECHNO, listOf(0.5f, 5f, 4f, -2f, -2.5f, -1f, 0f, 1.5f, 4.5f, 3f)),
        // Electrónica: sonrisa ancha (graves y agudos arriba, medios abajo) con aire arriba de 10 kHz.
        EqPreset(ELECTRONICA, listOf(2f, 5f, 3.5f, 0.5f, -1.5f, -2f, -1f, 1.5f, 3.5f, 5f)),
    )

    /** Todos: los de siempre y los urbanos. */
    val all = normal + urban

    fun byId(id: String?) = all.firstOrNull { it.id == id }
}

/** "Mis ajustes": lo que se guarda para volver con un toque. Nombre vacío = el de fábrica ("Mis ajustes 1"). */
data class EqSlot(val name: String, val curve: EqCurve, val bass: Float, val voice: Float, val extraDb: Float)

/** Cuántos "Mis ajustes" hay. */
const val EQ_SLOTS = 3

/** "slot1" … "slot3" (lo que va en [EqSettings.preset]). */
fun eqSlotId(index: Int) = "slot${index + 1}"

/** El número (0…) de "Mis ajustes" de [id]; null si es un estilo de fábrica o no existe. */
fun eqSlotIndex(id: String?): Int? =
    id?.takeIf { it.startsWith("slot") }?.removePrefix("slot")?.toIntOrNull()?.minus(1)?.takeIf { it in 0 until EQ_SLOTS }

/**
 * Dónde se pone el ecualizador: [AUTO] (en todo el sonido si la pantalla lo deja; si no, app por
 * app), [GLOBAL] (solo todo el sonido) o [APPS] (en cada app que avisa que está sonando). Nunca los
 * dos a la vez: la app quedaría ecualizada dos veces.
 */
enum class EqMode {
    AUTO, GLOBAL, APPS;

    companion object {
        fun parse(name: String?) = entries.firstOrNull { it.name == name } ?: AUTO
    }
}

/** Todo lo que el usuario elige en Ajustes → Sonido (lo que se guarda). */
data class EqSettings(
    val enabled: Boolean = false,
    /** "Bajo protegido": topes en los bajos y margen para que no distorsione. Prendido de fábrica. */
    val protect: Boolean = true,
    /** El estilo ([EqPresets]) o "Mis ajustes N" ([eqSlotId]) de donde salió la curva. */
    val preset: String = EqPresets.FLAT,
    /** Se cambió la curva (o, en "Mis ajustes", los deslizadores) después de elegirlo. */
    val edited: Boolean = false,
    val curve: EqCurve = EqCurve.FLAT,
    /** Refuerzo de bajos, de 0 a 1. */
    val bass: Float = 0f,
    /** Claridad de voz, de 0 a 1. */
    val voice: Float = 0f,
    /** Volumen extra (dB) que trae el estilo (Noche, Podcast); va con el realce de volumen de Android. */
    val extraDb: Float = 0f,
    /** Sonido envolvente (Virtualizer): apagado de fábrica. */
    val virtualizer: Boolean = false,
    val mode: EqMode = EqMode.AUTO,
) {
    /** El número de "Mis ajustes" elegido, o null. */
    val slot get() = eqSlotIndex(preset)

    fun withPreset(p: EqPreset) = copy(preset = p.id, edited = false, curve = p.curve, extraDb = p.extraDb)

    fun withSlot(index: Int, s: EqSlot) =
        copy(preset = eqSlotId(index), edited = false, curve = s.curve, bass = s.bass, voice = s.voice, extraDb = s.extraDb)

    /** La banda [index] de [bands] en [db]: la curva pasa a ser una por banda de la pantalla. */
    fun withBand(bands: EqBands, index: Int, db: Float) =
        copy(curve = EqCurve.of(bands.hz.mapIndexed { i, hz -> EqPoint(hz, if (i == index) db else curve.at(hz)) }), edited = true)

    fun withBass(value: Float) = copy(bass = value.coerceIn(0f, 1f), edited = edited || slot != null)
    fun withVoice(value: Float) = copy(voice = value.coerceIn(0f, 1f), edited = edited || slot != null)

    /** "Restablecer": plano, sin extras y con Bajo protegido; sigue prendido (o apagado) y en el mismo modo. */
    fun reset() = EqSettings(enabled = enabled, mode = mode)
}

/** Las bandas que trae la pantalla, de grave a agudo, y cuánto deja subir o bajar cada una (dB). */
data class EqBands(val hz: List<Float>, val minDb: Float, val maxDb: Float) {
    val valid get() = hz.isNotEmpty() && hz.all { it.isFinite() && it > 0f } && minDb.isFinite() && maxDb.isFinite() && maxDb > minDb
}

/** Qué otros efectos de Android sirven en la pantalla. */
data class EqCaps(val bassBoost: Boolean = false, val loudness: Boolean = false, val virtualizer: Boolean = false)

/** Lo que se le manda a Android para una curva. */
data class EqOutput(
    /** dB de cada banda (en el orden de [EqBands.hz]), ya dentro del rango de la pantalla. */
    val levels: List<Float>,
    /** Lo que se oye en cada banda: [levels] más el margen recuperado y el refuerzo de bajos (para dibujar). */
    val heard: List<Float>,
    /** Fuerza del refuerzo de bajos de Android (BassBoost), 0–1000; 0 = apagado. */
    val bassStrength: Int,
    /** Realce de volumen (LoudnessEnhancer) en milibelios; 0 = apagado. */
    val loudnessMb: Int,
    /** Fuerza del sonido envolvente, 0–1000; 0 = apagado. */
    val virtualizerStrength: Int,
    /** Cuánto se bajó toda la curva para dejar margen (Bajo protegido). */
    val headroomDb: Float,
) {
    val eqActive get() = levels.any { (it * 100).roundToInt() != 0 }

    /** Nada que hacer: los efectos pueden quedar apagados (no gastan nada). */
    val flat get() = !eqActive && bassStrength == 0 && loudnessMb == 0 && virtualizerStrength == 0

    companion object {
        val NONE = EqOutput(emptyList(), emptyList(), 0, 0, 0, 0f)
    }
}

object EqMath {
    /** Bajos (≤ 160 Hz): con Bajo protegido suben máximo +6 dB. */
    const val LOW_HZ = 160f
    const val LOW_CAP_DB = 6f
    /** Subgrave (≤ 45 Hz): los parlantes del carro casi no lo dan; máximo +4 dB. */
    const val SUB_HZ = 45f
    const val SUB_CAP_DB = 4f
    /** El resto: máximo +9 dB. */
    const val MAX_CAP_DB = 9f
    /** Con Bajo protegido, los bajos de la curva más el refuerzo de bajos no pasan de +9 dB juntos. */
    const val LOW_BUDGET_DB = 9f
    /** Con Bajo protegido, toda la curva se baja hasta que lo que más sube quede en +3 dB. */
    const val HEADROOM_DB = 3f
    /** BassBoost de Android: 0–1000; con Bajo protegido, máximo 400. */
    const val BASS_MAX = 1000
    const val BASS_PROTECTED_MAX = 400
    /** Cuánto sube el BassBoost al máximo (el de AOSP: 15 dB). Es una estimación para el margen. */
    const val BASS_DB_AT_MAX = 15f
    /** Sin BassBoost, el refuerzo de bajos se hace con la curva: hasta +6 dB abajo, desde ~120 Hz. */
    const val BASS_SHELF_DB = 6f
    const val BASS_SHELF_HZ = 120f
    /** Claridad de voz: presencia (~2,5 kHz, de 1 a 4 kHz) +4 dB y barro (~300 Hz) −3 dB. */
    const val PRESENCE_HZ = 2500f
    const val PRESENCE_OCTAVES = 0.8f
    const val PRESENCE_DB = 4f
    const val MUD_HZ = 300f
    const val MUD_OCTAVES = 0.6f
    const val MUD_DB = 3f
    /** Tope del realce de volumen (margen recuperado + volumen extra del estilo). */
    const val MAX_LOUDNESS_DB = 10f
    const val VIRTUALIZER_STRENGTH = 400
    /** El editor muestra de −12 a +12 dB (o menos, si la pantalla no deja tanto). */
    const val EDITOR_DB = 12f

    private val LN2 = ln(2f)

    /** La curva en las frecuencias de [bands] (interpolando en escala logarítmica). */
    fun map(curve: EqCurve, bands: EqBands): List<Float> = bands.hz.map { curve.at(it) }

    /** Tope de Bajo protegido en [hz]. */
    fun capAt(hz: Float) = when {
        hz <= SUB_HZ -> SUB_CAP_DB
        hz <= LOW_HZ -> LOW_CAP_DB
        else -> MAX_CAP_DB
    }

    /** Cuánto alcanza el refuerzo de bajos en [hz] (1 abajo, ~0,5 en 120 Hz, casi 0 desde 400 Hz). */
    fun lowShelf(hz: Float): Float = 1f / (1f + exp(2.5f * ln(hz / BASS_SHELF_HZ)))

    /** Campana en escala de octavas: 1 en [center], ~0,6 a [octaves] de distancia. */
    fun bell(hz: Float, center: Float, octaves: Float): Float {
        val d = ln(hz / center) / LN2 / octaves
        return exp(-0.5f * d * d)
    }

    /**
     * Lo que hay que mandarle a Android: la curva de [s] en las bandas de [bands], más la
     * claridad de voz y el refuerzo de bajos (con BassBoost si [caps] lo trae; si no, con la curva).
     * Con Bajo protegido: topes por banda ([capAt]), presupuesto de bajos ([LOW_BUDGET_DB]) y
     * margen: todo se baja hasta que el pico quede en [HEADROOM_DB] y el realce de volumen lo
     * recupera sin saturar. Al final cada banda queda dentro del rango de la pantalla.
     */
    fun compute(s: EqSettings, bands: EqBands, caps: EqCaps): EqOutput {
        if (!bands.valid) return EqOutput.NONE
        val hz = bands.hz
        val bass = s.bass.coerceIn(0f, 1f)
        val voice = s.voice.coerceIn(0f, 1f)
        val want = hz.map { f ->
            var g = s.curve.at(f) + voice * (PRESENCE_DB * bell(f, PRESENCE_HZ, PRESENCE_OCTAVES) - MUD_DB * bell(f, MUD_HZ, MUD_OCTAVES))
            if (!caps.bassBoost) g += bass * BASS_SHELF_DB * lowShelf(f)
            if (s.protect) min(g, capAt(f)) else g
        }
        val strengthMax = if (s.protect) BASS_PROTECTED_MAX else BASS_MAX
        var strength = if (caps.bassBoost) (bass * strengthMax).roundToInt() else 0
        if (s.protect && strength > 0) {
            val low = want.filterIndexed { i, _ -> hz[i] <= LOW_HZ }.maxOrNull() ?: want.first()
            val room = LOW_BUDGET_DB - max(0f, low)
            strength = min(strength, floor(room / BASS_DB_AT_MAX * BASS_MAX).toInt().coerceAtLeast(0))
        }
        val bassDb = strength.toFloat() / BASS_MAX * BASS_DB_AT_MAX
        val peak = want.indices.maxOf { want[it] + bassDb * lowShelf(hz[it]) }
        val headroom = if (s.protect) max(0f, peak - HEADROOM_DB) else 0f
        val levels = want.map { (it - headroom).coerceIn(bands.minDb, bands.maxDb) }
        val heard = levels.indices.map { levels[it] + headroom + bassDb * lowShelf(hz[it]) }
        val loudness = if (caps.loudness) (headroom + s.extraDb).coerceIn(0f, MAX_LOUDNESS_DB) else 0f
        val virtualizer = if (caps.virtualizer && s.virtualizer) VIRTUALIZER_STRENGTH else 0
        return EqOutput(levels, heard, strength, (loudness * 100).roundToInt(), virtualizer, headroom)
    }

    /** dB → milibelios para Android, dentro del rango de la pantalla. */
    fun toMillibels(db: Float, bands: EqBands): Short {
        val lo = (bands.minDb * 100).roundToInt().coerceAtLeast(Short.MIN_VALUE.toInt())
        val hi = (bands.maxDb * 100).roundToInt().coerceAtMost(Short.MAX_VALUE.toInt())
        return (db * 100).roundToInt().coerceIn(lo, hi).toShort()
    }

    /** De dónde a dónde va el editor (dB): ±12, o lo que deje la pantalla si es menos. */
    fun editorRange(bands: EqBands): Pair<Float, Float> {
        val lo = max(bands.minDb, -EDITOR_DB)
        val hi = min(bands.maxDb, EDITOR_DB)
        return if (hi - lo >= 1f) lo to hi else bands.minDb to bands.maxDb
    }

    /** A medio dB: así se arrastra (sin decimales raros). */
    fun snap(db: Float) = (db * 2).roundToInt() / 2f

    /** "60", "230", "3.6k", "14k". */
    fun freqLabel(hz: Float): String {
        if (hz < 999.5f) return hz.roundToInt().toString()
        val k = (hz / 100f).roundToInt() / 10f
        return if (k >= 10f || k % 1f == 0f) "${k.roundToInt()}k" else "${k}k"
    }

    /** "+3.5", "0", "−2" (a medio dB). */
    fun dbLabel(db: Float): String {
        val r = snap(db)
        if (r == 0f) return "0"
        val n = abs(r)
        return (if (r > 0) "+" else "−") + (if (n % 1f == 0f) n.roundToInt().toString() else n.toString())
    }
}

/** Las sesiones de audio abiertas (número → app) que se guardan por si Android cierra SailDash. */
object EqSessions {
    /** Máximo de apps a la vez: la más vieja sale (algunas no avisan cuando terminan). */
    const val MAX = 8

    fun encode(sessions: Map<Int, String?>): String = sessions.entries.joinToString(",") { "${it.key}:${it.value.orEmpty()}" }

    fun decode(text: String?): LinkedHashMap<Int, String?> {
        val out = LinkedHashMap<Int, String?>()
        text?.split(',')?.forEach { part ->
            val id = part.substringBefore(':').trim().toIntOrNull() ?: return@forEach
            if (id > 0) out[id] = part.substringAfter(':', "").trim().ifEmpty { null }
        }
        while (out.size > MAX) out.remove(out.keys.first())
        return out
    }
}

/**
 * ¿Es esta app la de sonido de fábrica (la que maneja el ecualizador del chip del carro)? Puntaje:
 * 0 = no. Solo apps del sistema (las de fábrica); una palabra exacta en el nombre o en el paquete
 * ("Sonido", "EQ", com.xx.eq) vale más que un pedazo ("AudioSettings"). Se descartan la música, el
 * Bluetooth, la radio, las grabadoras y parecidas, que también dicen "audio" o "sound".
 */
object EqFactoryApps {
    private val strong = setOf(
        "eq", "equalizer", "equaliser", "ecualizador", "sonido", "sound", "sounds", "audio", "dsp",
        "musicfx", "audiofx", "soundfx", "effect", "effects", "efecto", "efectos",
        "soundeffect", "soundeffects", "audioeffect", "audioeffects", "soundsettings", "audiosettings",
    )
    private val weak = Regex("sonido|sound|audio|equal|ecualiz|dsp|effect|efecto|(^|[^a-z])eq|eq($|[^a-z])")
    private val excluded = Regex("record|grabad|bluetooth|(^|[^a-z])bt($|[^a-z])|music(?!fx)|m[uú]sica|player|reproductor|radio|video|ringtone|camera|c[aá]mara|cloud")
    private val words = Regex("[^\\p{L}\\p{N}]+")

    fun score(label: String, pkg: String, system: Boolean): Int {
        if (!system) return 0
        val l = label.lowercase(Locale.ROOT)
        val p = pkg.lowercase(Locale.ROOT)
        if (excluded.containsMatchIn(l) || excluded.containsMatchIn(p)) return 0
        var score = 0
        if (l.split(words).any { it in strong }) score += 10
        if (p.split('.', '_', '-').any { it in strong }) score += 6
        if (score == 0) {
            if (weak.containsMatchIn(l)) score += 3
            if (weak.containsMatchIn(p)) score += 2
        }
        return score
    }
}
