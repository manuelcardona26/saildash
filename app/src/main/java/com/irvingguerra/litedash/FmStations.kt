package com.irvingguerra.litedash

import java.util.Locale

/*
 * SailDash: las emisoras de Radio FM que guarda el dueño (frecuencia y nombre). Solo la lógica, sin
 * Android, para poder probarla: se guardan en el teléfono como texto ("989:Olímpica|1029:Radioacktiva").
 */

/** Una emisora: la frecuencia en décimas de MHz (989 = 98.9) y su nombre ("" si no tiene). */
data class FmStation(val tenths: Int, val name: String)

object FmStations {
    /** La banda FM de Colombia: 87.5 a 108.0 MHz. */
    const val MIN = 875
    const val MAX = 1080

    /** Cuántas se guardan como mucho (las que caben en dos filas de botones). */
    const val LIMIT = 30

    /** "98.9" (siempre con punto, como en el dial). */
    fun format(tenths: Int): String = String.format(Locale.US, "%d.%d", tenths / 10, tenths % 10)

    fun clamp(tenths: Int) = tenths.coerceIn(MIN, MAX)

    /**
     * La frecuencia FM que dice un texto de la radio ("FM 98.9", "98,9 MHz", "Olímpica 98.9"); null si
     * no dice ninguna o no es de FM. Lo que la pantalla reporta de verdad, nunca lo que se supone.
     */
    fun fromText(text: String?): Int? {
        if (text.isNullOrBlank()) return null
        if (Regex("\\bAM\\b", RegexOption.IGNORE_CASE).containsMatchIn(text)) return null
        for (m in Regex("(?<![\\d.,])(\\d{2,3})[.,](\\d{1,2})(?![\\d])").findAll(text)) {
            val whole = m.groupValues[1].toInt()
            val frac = m.groupValues[2]
            val tenths = whole * 10 + frac.first().digitToInt()
            if (tenths in MIN..MAX) return tenths
        }
        return null
    }

    /** Lo guardado; lo que no se entienda se salta. Ordenadas de menor a mayor, sin repetir frecuencia. */
    fun parse(text: String?): List<FmStation> {
        if (text.isNullOrBlank()) return emptyList()
        val list = text.split('|').mapNotNull { part ->
            val tenths = part.substringBefore(':').trim().toIntOrNull() ?: return@mapNotNull null
            if (tenths !in MIN..MAX) return@mapNotNull null
            FmStation(tenths, unescape(part.substringAfter(':', "")))
        }
        return list.distinctBy { it.tenths }.sortedBy { it.tenths }
    }

    fun encode(list: List<FmStation>): String = list.joinToString("|") { "${it.tenths}:${escape(it.name)}" }

    /** Guarda [station] (si ya estaba esa frecuencia, le cambia el nombre). */
    fun add(list: List<FmStation>, station: FmStation): List<FmStation> {
        val s = station.copy(tenths = clamp(station.tenths), name = clean(station.name))
        val rest = list.filter { it.tenths != s.tenths }
        if (rest.size >= LIMIT) return list
        return (rest + s).sortedBy { it.tenths }
    }

    fun rename(list: List<FmStation>, tenths: Int, name: String): List<FmStation> =
        list.map { if (it.tenths == tenths) it.copy(name = clean(name)) else it }

    fun remove(list: List<FmStation>, tenths: Int): List<FmStation> = list.filter { it.tenths != tenths }

    /** La guardada en [tenths], si hay. */
    fun at(list: List<FmStation>, tenths: Int?): FmStation? = list.firstOrNull { it.tenths == tenths }

    /**
     * Lo que encontró "Buscar y guardar todas" ([found], en el orden en que sonaron) junto a lo
     * guardado: las que ya estaban se quedan con su nombre; las nuevas, con el que dio la radio.
     */
    fun merge(list: List<FmStation>, found: List<FmStation>): List<FmStation> {
        var out = list
        for (f in found.distinctBy { it.tenths }) {
            val old = at(out, f.tenths)
            out = if (old != null) {
                if (old.name.isBlank() && f.name.isNotBlank()) rename(out, f.tenths, f.name) else out
            } else add(out, f)
        }
        return out
    }

    /**
     * ¿La búsqueda ya dio la vuelta? La radio busca hacia arriba y al llegar a 108 vuelve a 87.5:
     * se para al repetir una frecuencia ya vista o al pasar otra vez por donde empezó.
     */
    fun scanDone(start: Int, seen: List<Int>, next: Int): Boolean {
        if (next in seen || next == start) return true
        val last = seen.lastOrNull() ?: start
        // Dio la vuelta (de arriba a abajo) y ya llegó o pasó el inicio.
        return next < last && next >= start || (last < start && next >= start)
    }

    private fun clean(name: String) = name.replace('\n', ' ').trim().take(24)

    private fun escape(s: String) = s.replace("%", "%25").replace("|", "%7C").replace(":", "%3A")
    private fun unescape(s: String) = s.replace("%3A", ":").replace("%7C", "|").replace("%25", "%")
}
