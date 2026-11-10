package com.irvingguerra.litedash

/*
 * SailDash: la música en reversa, la parte que no depende de Android (se prueba en ReverseRulesTest).
 * Cuánto bajar y a cuánto volver, el antirrebote de la señal, cómo leer un aviso de reversa y cómo
 * elegir, entre los ajustes que cambiaron al meterla, el que la anuncia.
 */

/** Qué hacer con la música al meter reversa; [keep] = cuánto del volumen queda, en %. */
enum class ReverseMode(val stored: String, val keep: Int) {
    NONE("none", 100),
    LOWER("lower", 70),
    HALF("half", 50),
    MUTE("mute", 0);

    companion object {
        fun from(stored: String?) = entries.firstOrNull { it.stored == stored } ?: NONE
    }
}

object ReverseVolume {
    /** El "volumen puesto" cuando se silenció con ADJUST_MUTE (el nivel no cambia: se quita el silencio y ya). */
    const val MUTED = -1

    /**
     * El volumen en reversa a partir de [current] (de 0 al máximo del carro); null = no hay nada que
     * bajar. Bajar un poco o a la mitad nunca llega a 0: eso es silenciar.
     */
    fun lowered(current: Int, mode: ReverseMode): Int? {
        if (mode == ReverseMode.NONE || current <= 0) return null
        if (mode == ReverseMode.MUTE) return 0
        // En enteros: con decimales, 10 × 0.7 da 6.99… y bajaría un paso de más.
        val target = (current * mode.keep / 100).coerceAtLeast(1)
        return target.takeIf { it < current }
    }

    /**
     * Al quitar la reversa: a qué volumen volver. null = se deja como está: no se había bajado, o el
     * usuario lo movió en plena reversa (manda lo que él dejó).
     */
    fun restored(saved: Int, applied: Int, current: Int): Int? =
        if (applied >= 0 && saved > applied && current == applied) saved else null
}

/**
 * Antirrebote de la señal: un cambio cuenta solo si se mantiene. Al meter reversa, [ON_MS] (rápido:
 * la música baja junto con la cámara); al quitarla, [OFF_MS] (al maniobrar se pasa de reversa a
 * primera y de vuelta, y el volumen no debe subir y bajar a cada rato).
 */
class ReverseDebounce(active: Boolean = false) {
    /** La reversa confirmada. */
    var active = active
        private set
    private var pending: Boolean? = null
    private var since = 0L

    /** Un valor de la señal (al cambiar o al cumplirse el plazo): true si [active] cambió. */
    fun update(on: Boolean, nowMs: Long): Boolean {
        if (on == active) {
            pending = null
            return false
        }
        if (pending != on) {
            pending = on
            since = nowMs
        }
        if (nowMs - since < holdMs(on)) return false
        active = on
        pending = null
        return true
    }

    /** Cuánto falta para confirmar un cambio pendiente (null = no hay ninguno). */
    fun waitMs(nowMs: Long): Long? = pending?.let { (holdMs(it) - (nowMs - since)).coerceAtLeast(1) }

    companion object {
        const val ON_MS = 300L
        const val OFF_MS = 2_000L

        fun holdMs(on: Boolean) = if (on) ON_MS else OFF_MS
    }
}

/** Avisos (broadcasts) de reversa. */
object ReverseBroadcasts {
    /**
     * El aviso propio de SailDash, con el extra "state" (true/false): para una automatización (MacroDroid,
     * Tasker) o para probar por ADB: adb shell am broadcast -a co.saildash.action.REVERSE --ez state true
     */
    const val SAILDASH = "co.saildash.action.REVERSE"

    /**
     * Los avisos que se escuchan al aprender y, ya aprendida la reversa, mientras la función está prendida.
     * Android no tiene uno estándar y no encontramos uno documentado de estas pantallas (ni en el código):
     * por ahora va solo el de SailDash. Si el diagnóstico por ADB (PLAN §5) muestra que la tuya manda uno,
     * se agrega aquí con su nombre exacto.
     */
    val ACTIONS = listOf(SAILDASH)

    /** Extras donde las marcas suelen poner el estado (se buscan sin importar mayúsculas). */
    private val STATE_EXTRAS = listOf(
        "state", "reverse", "status", "on", "value", "enable", "enabled",
        "backcar", "back_car", "is_reverse", "isreverse", "extra_state", "extra_reverse",
    )
    private val ON_WORDS = setOf("ON", "START", "ENTER", "IN", "BEGIN", "OPEN")
    private val OFF_WORDS = setOf("OFF", "STOP", "EXIT", "OUT", "END", "CLOSE")

    /**
     * Si el aviso dice reversa puesta (true), quitada (false) o no se entiende (null). Primero un extra
     * de estado; si no hay, el final del nombre (…_ON, …_OFF, …_START…).
     */
    fun state(action: String, extras: Map<String, Any?>): Boolean? {
        val byName = extras.entries.associate { it.key.lowercase() to it.value }
        for (k in STATE_EXTRAS) flag(byName[k])?.let { return it }
        val tail = action.substringAfterLast('.').substringAfterLast('_').uppercase()
        return when (tail) {
            in ON_WORDS -> true
            in OFF_WORDS -> false
            else -> null
        }
    }

    /** Un sí o no como lo mandan las pantallas: booleano, número o texto. */
    fun flag(v: Any?): Boolean? = when (v) {
        is Boolean -> v
        is Number -> v.toDouble() != 0.0
        is String -> when (v.trim().lowercase()) {
            "1", "true", "on", "yes", "si", "sí" -> true
            "0", "false", "off", "no" -> false
            else -> null
        }
        else -> null
    }
}

/** De dónde sale la señal de reversa. */
sealed interface ReverseSource {
    /** Un ajuste del sistema ("system/nombre", "global/…" o "secure/…") que vale [onValue] en reversa. */
    data class Setting(val key: String, val onValue: String) : ReverseSource

    /** Un aviso de [ReverseBroadcasts.ACTIONS]. */
    data class Broadcast(val action: String) : ReverseSource
}

/** Un ajuste que cambió mientras se aprendía; más puntaje = más probable que sea la reversa. */
data class ReverseCandidate(val key: String, val onValue: String, val score: Int, val changes: Int, val named: Boolean)

/** Lo que sale de aprender: una señal, varias para que elija el usuario, o nada. */
sealed interface ReverseDecision {
    data class Learned(val source: ReverseSource) : ReverseDecision
    data class Choose(val options: List<ReverseSource>) : ReverseDecision
    data object Nothing : ReverseDecision
}

/**
 * Elegir el ajuste de la reversa entre los que cambiaron (como las luces en CarLights.kt, pero aquí
 * también cuenta lo que se vio cambiar mientras la cámara tapaba la pantalla).
 */
object ReverseLearning {
    /** Nombres que suelen tener los ajustes de reversa en las pantallas de carro. */
    private val WORDS = Regex("back|revers|rear|camera|gear|avm|park|rvc|rvs|backcar|car_back", RegexOption.IGNORE_CASE)
    /** Llevan "back" pero no son la reversa (pantalla, música, botones, respaldos). */
    private val FALSE_FRIENDS = Regex("back_?light|playback|callback|feedback|background|backup|fallback|rollback|loopback|back_?key", RegexOption.IGNORE_CASE)
    /**
     * Nunca: el volumen (SailDash lo cambia en reversa: se escucharía a sí mismo) y el freno de mano (se
     * suelta al arrancar: bajaría la música andando).
     */
    private val NEVER = Regex("volume|brake", RegexOption.IGNORE_CASE)
    /** Cosas que cambian solas o por otras razones: cuentan menos. */
    private val NOISY = Regex(
        "time|clock|alarm|battery|wifi|wlan|bluetooth|bt_|location|gps|network|signal|usb|media|music|sound|audio|source|mute|screen|light|lamp|night|dim",
        RegexOption.IGNORE_CASE,
    )
    /** Valores de sí o no: la reversa casi siempre es uno de estos. */
    private val FLAGS = setOf("0", "1", "true", "false", "on", "off", "yes", "no")
    /** Un ajuste que cambió más veces que esto mientras se aprendía no es la reversa (se mete una vez). */
    private const val MAX_CHANGES = 4
    private const val NAMED = 8
    private const val FLAG = 3
    private const val NOISE = 3
    const val MAX_OPTIONS = 8

    private val ORDER = compareByDescending<ReverseCandidate> { it.score }.thenBy { it.changes }.thenBy { it.key }

    private fun nameOf(key: String) = key.substringAfter('/')

    /** El nombre suena a reversa (sin contar los que engañan, como "backlight"). */
    fun named(key: String): Boolean = WORDS.containsMatchIn(FALSE_FRIENDS.replace(nameOf(key), " "))

    fun ignored(key: String): Boolean = NEVER.containsMatchIn(nameOf(key))

    /**
     * Los ajustes que cambiaron entre [before] (sin reversa) y [after] (unos segundos después de meterla),
     * más los que se vieron cambiar en el camino ([seen]: sus valores en orden, aunque después hayan vuelto).
     * El valor en reversa es el primero distinto al de antes: si la palanca pasó por R y luego por D, es R.
     */
    fun candidates(before: Map<String, String>, after: Map<String, String>, seen: Map<String, List<String>>): List<ReverseCandidate> {
        val keys = LinkedHashSet<String>()
        for ((k, v) in after) if (before[k] != v) keys += k
        keys += seen.keys
        return keys.mapNotNull { key ->
            if (ignored(key)) return@mapNotNull null
            val was = before[key].orEmpty()
            val values = seen[key].orEmpty()
            val on = values.firstOrNull { it != was } ?: after[key]?.takeIf { it != was } ?: return@mapNotNull null
            // Vacío no se distingue de "no existe".
            if (on.isEmpty()) return@mapNotNull null
            val changes = values.size.coerceAtLeast(1)
            if (changes > MAX_CHANGES) return@mapNotNull null
            val named = named(key)
            var score = 0
            if (named) score += NAMED
            if (was.lowercase() in FLAGS && on.lowercase() in FLAGS) score += FLAG
            if (NOISY.containsMatchIn(nameOf(key))) score -= NOISE
            ReverseCandidate(key, on, score, changes, named)
        }.sortedWith(ORDER)
    }

    /**
     * Qué aprender: un ajuste con nombre de reversa (si hay varios, el de mejor puntaje; empatados,
     * que elija el usuario); si no, un aviso de reversa que haya llegado ([heard]); si no, el único
     * ajuste que cambió (si no parece ruido). Con varios sin nombre, que elija el usuario.
     */
    fun decide(candidates: List<ReverseCandidate>, heard: String?): ReverseDecision {
        val sorted = candidates.sortedWith(ORDER)
        val named = sorted.filter { it.named }
        val others = sorted.filterNot { it.named }
        fun setting(c: ReverseCandidate) = ReverseSource.Setting(c.key, c.onValue)
        val options = (named.map(::setting) + listOfNotNull(heard?.let { ReverseSource.Broadcast(it) }) + others.map(::setting)).take(MAX_OPTIONS)
        return when {
            named.size == 1 || named.size > 1 && named[0].score > named[1].score -> ReverseDecision.Learned(setting(named[0]))
            named.isNotEmpty() -> ReverseDecision.Choose(options)
            heard != null -> ReverseDecision.Learned(ReverseSource.Broadcast(heard))
            others.size == 1 && others[0].score >= 0 -> ReverseDecision.Learned(setting(others[0]))
            others.isNotEmpty() -> ReverseDecision.Choose(options)
            else -> ReverseDecision.Nothing
        }
    }
}
