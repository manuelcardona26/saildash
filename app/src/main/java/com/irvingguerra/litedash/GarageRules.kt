// SailDash: el Taller sin OBD. Las cuentas puras (sin Android) del computador de viaje, el odómetro
// aproximado por GPS, las tanqueadas y la bitácora: se prueban en el PC (GarageRulesTest).
package com.irvingguerra.litedash

import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.roundToLong

/**
 * Lo juntado con el GPS desde [since]: metros y tiempo en movimiento. Solo cuentan los tramos que
 * TripRepository ya acepta (punto bueno y en movimiento), así que parado no suma ni tiempo.
 */
data class TripTotals(
    val meters: Double = 0.0,
    val movingMs: Long = 0L,
    /** Desde cuándo se cuenta (ms desde 1970). */
    val since: Long = 0L,
    /** El último tramo que contó (para saber si ya es otro viaje). */
    val lastMoveAt: Long = 0L,
) {
    val km get() = meters / 1000.0

    /** km/h en movimiento; null si todavía no hay con qué promediar (menos de un minuto o de 100 m). */
    val averageKmh: Double?
        get() = if (movingMs >= MIN_AVERAGE_MS && meters >= MIN_AVERAGE_M) km / (movingMs / 3_600_000.0) else null

    /** Suma un tramo de [meters] que tardó [dtMs] (el tiempo solo cuenta si fue seguido, ver [TripMath.movingTime]). */
    fun add(meters: Double, dtMs: Long, now: Long): TripTotals = copy(
        meters = this.meters + meters.coerceAtLeast(0.0),
        movingMs = movingMs + TripMath.movingTime(dtMs),
        since = if (since == 0L) now else since,
        lastMoveAt = now,
    )

    companion object {
        const val MIN_AVERAGE_MS = 60_000L
        const val MIN_AVERAGE_M = 100.0
    }
}

object TripMath {
    /** Entre dos puntos del GPS más separados que esto (un túnel, la app en otra pantalla) no se cuenta el tiempo. */
    const val MAX_GAP_MS = 10_000L

    /** Tanto tiempo sin moverse y el siguiente movimiento ya es otro viaje (salir al otro día, volver del trabajo). */
    const val NEW_TRIP_AFTER_MS = 2 * 3_600_000L

    /** Un tramo más largo que esto entre dos puntos no es real (el GPS saltó): no se suma. */
    const val MAX_STEP_M = 2_000.0

    fun movingTime(dtMs: Long): Long = if (dtMs in 1..MAX_GAP_MS) dtMs else 0L

    /** El viaje de ahora ya se terminó (pasó [NEW_TRIP_AFTER_MS] sin moverse) cuando llega un tramo en [now]. */
    fun isNewTrip(trip: TripTotals, now: Long): Boolean = trip.lastMoveAt != 0L && now - trip.lastMoveAt >= NEW_TRIP_AFTER_MS
}

/**
 * El odómetro aproximado: el dueño escribe una vez lo que marca el tablero ([baseKm]) y SailDash le
 * suma lo que mide el GPS ([gpsKm], solo mientras el GPS ya está prendido por otra cosa). Al
 * corregirlo, si ya midió bastante, aprende cuánto se queda corto o largo el GPS ([factor]).
 */
data class Odometer(val baseKm: Double, val gpsKm: Double = 0.0, val factor: Double = 1.0, val setAt: Long = 0L) {
    val km get() = baseKm + gpsKm * factor

    fun add(meters: Double) = copy(gpsKm = gpsKm + meters.coerceAtLeast(0.0) / 1000.0)

    /** El tablero dice [realKm]: ese es el nuevo punto de partida (y, si se puede, un [factor] mejor). */
    fun correct(realKm: Double, now: Long): Odometer {
        val measured = realKm - baseKm
        val learned = if (gpsKm >= MIN_KM_TO_LEARN && measured > 0) (measured / gpsKm).coerceIn(MIN_FACTOR, MAX_FACTOR) else factor
        return Odometer(realKm.coerceAtLeast(0.0), 0.0, learned, now)
    }

    companion object {
        /** Con menos GPS que esto la diferencia es más error de lectura que del GPS. */
        const val MIN_KM_TO_LEARN = 50.0
        const val MIN_FACTOR = 0.9
        const val MAX_FACTOR = 1.1
        const val MAX_KM = 9_999_999.0
    }
}

/** Una tanqueada: cuándo, cuántos galones, cuánto se pagó (pesos) y el kilometraje; [full] = tanque lleno. */
data class FuelEntry(val id: Long, val date: Day, val gallons: Double, val price: Double, val km: Double, val full: Boolean = true) {
    val pricePerGallon get() = if (gallons > 0) price / gallons else 0.0
}

/** Lo que rindió una tanqueada: km recorridos desde el tanque lleno anterior, km/galón y pesos por km. */
data class FuelResult(val km: Double, val kmPerGallon: Double, val costPerKm: Double)

object FuelMath {
    /**
     * Para cada tanqueada con tanque lleno que tenga otra llena antes: los km desde esa anterior, entre
     * los galones y la plata de todas las tanqueadas de en medio (incluida esta). Así una tanqueada a
     * medias no da un número loco: se suma a la siguiente llena. Las demás no salen en el mapa.
     */
    fun results(entries: List<FuelEntry>): Map<Long, FuelResult> {
        val sorted = entries.sortedWith(compareBy({ it.km }, { it.date }))
        val out = HashMap<Long, FuelResult>()
        var lastFull: FuelEntry? = null
        var gallons = 0.0
        var price = 0.0
        for (e in sorted) {
            if (lastFull != null) {
                gallons += e.gallons
                price += e.price
            }
            if (e.full) {
                val from = lastFull
                val km = from?.let { e.km - it.km } ?: 0.0
                if (from != null && km > 0 && gallons > 0) out[e.id] = FuelResult(km, km / gallons, price / km)
                lastFull = e
                gallons = 0.0
                price = 0.0
            }
        }
        return out
    }

    /** El promedio de todo lo que se puede medir: km totales entre galones totales (no el promedio de promedios). */
    fun average(entries: List<FuelEntry>): FuelResult? {
        val r = results(entries).values
        val km = r.sumOf { it.km }
        if (km <= 0) return null
        val gallons = r.sumOf { it.km / it.kmPerGallon }
        val cost = r.sumOf { it.km * it.costPerKm }
        return FuelResult(km, km / gallons, cost / km)
    }

    /** Lo que se gastó en gasolina en el mes de [day]. */
    fun spentInMonth(entries: List<FuelEntry>, day: Day) = entries.filter { it.date.year == day.year && it.date.month == day.month }.sumOf { it.price }

    /** Galones escritos a mano: "10,5", "10.5" o "10" (coma o punto como decimal). */
    fun parseDecimal(text: String): Double? = text.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it > 0 && it.isFinite() }

    /** Pesos: "120.000", "120000" o "$120,000" son lo mismo (los separadores de mil no cuentan). */
    fun parsePesos(text: String): Double? = text.filter { it in '0'..'9' }.takeIf { it.isNotEmpty() && it.length <= 9 }?.toDouble()?.takeIf { it > 0 }
}

/** Qué se le hizo al carro (la bitácora). [stored] es lo que se guarda: no se cambia. */
enum class LogKind(val stored: String) {
    SERVICE("service"), OIL("oil"), TIRES("tires"), BRAKES("brakes"), BATTERY("battery"), OTHER("other");

    companion object {
        fun fromStored(v: String?) = entries.firstOrNull { it.stored == v }
    }
}

/** Una anotación de la bitácora: qué, cuándo, a qué kilometraje y, si se quiere, una nota y lo que costó. */
data class LogEntry(val id: Long, val kind: LogKind, val date: Day, val km: Int?, val note: String = "", val cost: Double? = null)

/**
 * Lo guardado del Taller sin OBD, en JSON (SharedPreferences). Lo que no se entiende se ignora sin
 * borrar lo demás.
 */
object GarageJson {
    fun writeTrip(t: TripTotals): String = JSONObject()
        .put("m", t.meters).put("ms", t.movingMs).put("since", t.since).put("last", t.lastMoveAt).toString()

    fun readTrip(text: String?): TripTotals? = runCatching {
        val o = JSONObject(text ?: return null)
        TripTotals(o.getDouble("m"), o.getLong("ms"), o.optLong("since"), o.optLong("last"))
    }.getOrNull()

    fun writeTrips(list: List<TripTotals>): String = JSONArray().apply { list.forEach { put(JSONObject(writeTrip(it))) } }.toString()

    fun readTrips(text: String?): List<TripTotals> = runCatching {
        val a = JSONArray(text ?: return emptyList())
        (0 until a.length()).mapNotNull { readTrip(a.optJSONObject(it)?.toString()) }
    }.getOrDefault(emptyList())

    fun writeOdometer(o: Odometer): String = JSONObject().put("base", o.baseKm).put("gps", o.gpsKm).put("f", o.factor).put("at", o.setAt).toString()

    fun readOdometer(text: String?): Odometer? = runCatching {
        val o = JSONObject(text ?: return null)
        Odometer(o.getDouble("base"), o.optDouble("gps", 0.0), o.optDouble("f", 1.0), o.optLong("at"))
    }.getOrNull()

    fun writeFuel(list: List<FuelEntry>): String = JSONArray().apply {
        for (e in list) put(JSONObject().put("id", e.id).put("date", e.date.toString()).put("gal", e.gallons).put("price", e.price).put("km", e.km).put("full", e.full))
    }.toString()

    fun readFuel(text: String?): List<FuelEntry> = readArray(text) { o ->
        FuelEntry(o.getLong("id"), Day.parse(o.getString("date")), o.getDouble("gal"), o.getDouble("price"), o.getDouble("km"), o.optBoolean("full", true))
    }

    fun writeLog(list: List<LogEntry>): String = JSONArray().apply {
        for (e in list) {
            val o = JSONObject().put("id", e.id).put("kind", e.kind.stored).put("date", e.date.toString())
            e.km?.let { o.put("km", it) }
            if (e.note.isNotEmpty()) o.put("note", e.note)
            e.cost?.let { o.put("cost", it) }
            put(o)
        }
    }.toString()

    fun readLog(text: String?): List<LogEntry> = readArray(text) { o ->
        val kind = LogKind.fromStored(o.optString("kind")) ?: return@readArray null
        LogEntry(
            o.getLong("id"), kind, Day.parse(o.getString("date")),
            o.optInt("km", 0).takeIf { it > 0 }, o.optString("note", ""),
            if (o.has("cost")) o.getDouble("cost") else null,
        )
    }

    private fun <T> readArray(text: String?, read: (JSONObject) -> T?): List<T> {
        val a = runCatching { JSONArray(text ?: "[]") }.getOrNull() ?: return emptyList()
        return (0 until a.length()).mapNotNull { i -> a.optJSONObject(i)?.let { runCatching { read(it) }.getOrNull() } }
    }
}

/** "faltan 400 km" del cambio de aceite: lo que falta hasta [dueKm] con el odómetro en [odometerKm] (negativo = ya pasó). */
fun kmLeft(dueKm: Int, odometerKm: Double): Long = (dueKm - odometerKm).roundToLong()
