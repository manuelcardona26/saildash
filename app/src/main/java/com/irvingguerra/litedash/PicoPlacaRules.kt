package com.irvingguerra.litedash

import org.json.JSONArray
import org.json.JSONObject

/*
 * SailDash: pico y placa de Colombia. Aquí solo la lógica (sin Android ni Compose) para poder
 * probarla: las reglas de cada ciudad, los festivos y "¿tengo pico y placa este día?".
 */

/** Un horario de restricción en minutos del día: [start, end). */
data class PicoHours(val start: Int, val end: Int) {
    fun contains(minute: Int) = minute in start until end
    override fun toString() = "${hhmm(start)}–${hhmm(end)}"

    companion object {
        fun hhmm(m: Int) = "%d:%02d".format(m / 60, m % 60)

        /** "06:00-21:00" → 360..1260; null si no se entiende. */
        fun parse(text: String): PicoHours? {
            val parts = text.split('-').map { it.trim() }
            if (parts.size != 2) return null
            val a = minutes(parts[0]) ?: return null
            val b = minutes(parts[1]) ?: return null
            return if (b > a) PicoHours(a, b) else null
        }

        fun minutes(text: String): Int? {
            val p = text.trim().split(':')
            val h = p.getOrNull(0)?.toIntOrNull() ?: return null
            val m = p.getOrNull(1)?.toIntOrNull() ?: 0
            return if (h in 0..24 && m in 0..59) (h * 60 + m).coerceAtMost(24 * 60) else null
        }
    }
}

/**
 * Regla de una ciudad. Dos esquemas:
 * - por día de la semana ([days]: 1 = lunes … 6 = sábado → dígitos), como Medellín o Cali;
 * - por fecha par o impar ([even]/[odd] → dígitos), como Bogotá.
 * [from]/[to]: vigencia publicada; pasada la fecha, la app avisa que hay que actualizar.
 */
data class PicoCity(
    val id: String,
    val name: String,
    val hours: List<PicoHours>,
    val days: Map<Int, Set<Int>> = emptyMap(),
    val even: Set<Int>? = null,
    val odd: Set<Int>? = null,
    val from: Day? = null,
    val to: Day? = null,
    /** ¿Aplica en festivos? En Colombia casi nunca. */
    val holidays: Boolean = false,
    val source: String? = null,
) {
    /** Los dígitos con restricción ese día (vacío = nadie). */
    fun digitsOn(day: Day): Set<Int> {
        val dow = weekday(day)
        if (dow == 0) return emptySet()
        if (!holidays && ColombiaHolidays.isHoliday(day)) return emptySet()
        if (even != null || odd != null) {
            if (dow == 6) return days[6] ?: emptySet()
            return (if (day.day % 2 == 0) even else odd) ?: emptySet()
        }
        return days[dow] ?: emptySet()
    }

    fun restricted(day: Day, digit: Int) = digit in digitsOn(day)

    fun outdated(today: Day) = to != null && today > to
}

data class PicoRules(val version: Int, val updated: String, val cities: List<PicoCity>) {
    fun city(id: String?) = cities.firstOrNull { it.id == id }

    companion object {
        val EMPTY = PicoRules(0, "", emptyList())

        fun parse(text: String): PicoRules {
            val o = JSONObject(text)
            val list = o.getJSONArray("cities")
            val cities = (0 until list.length()).mapNotNull { i -> runCatching { city(list.getJSONObject(i)) }.getOrNull() }
            return PicoRules(o.optInt("version", 0), o.optString("updated"), cities)
        }

        private fun digits(a: JSONArray?): Set<Int>? = a?.let { (0 until it.length()).map { i -> it.getInt(i) % 10 }.toSet() }

        private fun city(o: JSONObject): PicoCity {
            val hours = digitsHours(o.getJSONArray("hours"))
            val days = mutableMapOf<Int, Set<Int>>()
            o.optJSONObject("days")?.let { d -> for (k in d.keys()) k.toIntOrNull()?.let { days[it] = digits(d.getJSONArray(k)) ?: emptySet() } }
            return PicoCity(
                id = o.getString("id"),
                name = o.getString("name"),
                hours = hours,
                days = days,
                even = digits(o.optJSONArray("even")),
                odd = digits(o.optJSONArray("odd")),
                from = o.optString("from").takeIf { it.isNotBlank() }?.let { Day.parse(it) },
                to = o.optString("to").takeIf { it.isNotBlank() }?.let { Day.parse(it) },
                holidays = o.optBoolean("holidays", false),
                source = o.optString("source").takeIf { it.isNotBlank() },
            )
        }

        private fun digitsHours(a: JSONArray) = (0 until a.length()).mapNotNull { PicoHours.parse(a.getString(it)) }
    }
}

/** 0 = domingo, 1 = lunes … 6 = sábado (1970-01-01 fue jueves). */
fun weekday(day: Day): Int = (day.epochDay + 4).mod(7L).toInt()

/**
 * Festivos de Colombia (Ley 51 de 1983, "Ley Emiliani"): los fijos, los que se corren al lunes
 * y los que dependen de la Semana Santa. Se calculan, así que sirven para cualquier año.
 */
object ColombiaHolidays {
    private val cache = HashMap<Int, Set<Day>>()

    @Synchronized
    fun of(year: Int): Set<Day> = cache.getOrPut(year) { compute(year) }

    fun isHoliday(day: Day) = day in of(day.year)

    private fun compute(year: Int): Set<Day> {
        val fixed = listOf(1 to 1, 5 to 1, 7 to 20, 8 to 7, 12 to 8, 12 to 25).map { (m, d) -> Day(year, m, d) }
        val moved = listOf(1 to 6, 3 to 19, 6 to 29, 8 to 15, 10 to 12, 11 to 1, 11 to 11).map { (m, d) -> nextMonday(Day(year, m, d)) }
        val easter = easter(year)
        val holy = listOf(easter.plusDays(-3), easter.plusDays(-2))
        // Ascensión (+39), Corpus Christi (+60) y Sagrado Corazón (+68), corridos al lunes.
        val easterMoved = listOf(39L, 60L, 68L).map { nextMonday(easter.plusDays(it)) }
        return (fixed + moved + holy + easterMoved).toSet()
    }

    /** El mismo día si es lunes; si no, el lunes siguiente. */
    fun nextMonday(day: Day): Day = day.plusDays(((8 - weekday(day)) % 7).toLong())

    /** Domingo de Pascua (algoritmo de Meeus/Jones/Butcher, calendario gregoriano). */
    fun easter(year: Int): Day {
        val a = year % 19
        val b = year / 100
        val c = year % 100
        val d = b / 4
        val e = b % 4
        val f = (b + 8) / 25
        val g = (b - f + 1) / 3
        val h = (19 * a + b - d - g + 15) % 30
        val i = c / 4
        val k = c % 4
        val l = (32 + 2 * e + 2 * i - h - k) % 7
        val m = (a + 11 * h + 22 * l) / 451
        val month = (h + l - 7 * m + 114) / 31
        val day = (h + l - 7 * m + 114) % 31 + 1
        return Day(year, month, day)
    }
}
