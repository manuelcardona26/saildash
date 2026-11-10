// SailDash: el Taller sin OBD (el dueño no tiene adaptador). Computador de viaje y odómetro con lo que
// ya mide el GPS, tanqueadas y bitácora del carro, guardados en el equipo.
package com.irvingguerra.litedash

import android.annotation.SuppressLint
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit

/**
 * Lo del Taller sin OBD. No prende nada: [onSegment] lo llama [TripRepository] con cada tramo que el
 * GPS ya midió (mientras el mapa, el velocímetro o el viaje lo tienen prendido), y con eso suben el
 * viaje, el total y el odómetro. Se guarda cada tanto y al apagarse el GPS ([flush]).
 */
class Garage private constructor(context: Context) {
    private val store = context.getSharedPreferences(STORE, Context.MODE_PRIVATE)

    /** El viaje de ahora (se parte solo tras 2 h sin moverse, o con "Nuevo viaje"). */
    var trip by mutableStateOf(GarageJson.readTrip(store.getString(TRIP, null)) ?: TripTotals())
        private set

    /** Todo lo medido desde [TripTotals.since] (o desde que se reinició). */
    var total by mutableStateOf(GarageJson.readTrip(store.getString(TOTAL, null)) ?: TripTotals())
        private set

    /** Los viajes terminados, el más nuevo primero (a lo más [MAX_TRIPS]). */
    var trips by mutableStateOf(GarageJson.readTrips(store.getString(TRIPS, null)))
        private set

    /** null = todavía no se puso el kilometraje del tablero. */
    var odometer by mutableStateOf(GarageJson.readOdometer(store.getString(ODOMETER, null)))
        private set

    var fuel by mutableStateOf(GarageJson.readFuel(store.getString(FUEL, null)).sortedByDescending { it.km })
        private set

    var log by mutableStateOf(GarageJson.readLog(store.getString(LOG, null)).sortedByDescending { it.date })
        private set

    /** El GPS mandó un punto hace poco (para la píldora "GPS · midiendo"). */
    var lastFixAt by mutableStateOf(0L)
        private set

    private var lastSave = 0L

    /** Un punto del GPS (cuente o no): solo para saber si el GPS está vivo. */
    fun onFix(now: Long) {
        lastFixAt = now
    }

    /** Un tramo medido de [meters] que tardó [dtMs]. Los saltos del GPS (más de 2 km de una) no cuentan. */
    fun onSegment(meters: Double, dtMs: Long, now: Long) {
        if (meters <= 0 || meters > TripMath.MAX_STEP_M) return
        if (TripMath.isNewTrip(trip, now)) finishTrip()
        trip = trip.add(meters, dtMs, now)
        total = total.add(meters, dtMs, now)
        odometer = odometer?.add(meters)
        if (now - lastSave >= SAVE_EVERY_MS) save(now)
    }

    /** Guarda ya lo que se lleva (el GPS se apagó o la app se va). */
    fun flush() = save(System.currentTimeMillis())

    private fun save(now: Long) {
        lastSave = now
        store.edit {
            putString(TRIP, GarageJson.writeTrip(trip))
            putString(TOTAL, GarageJson.writeTrip(total))
            odometer?.let { putString(ODOMETER, GarageJson.writeOdometer(it)) }
        }
    }

    private fun finishTrip() {
        if (trip.meters >= MIN_TRIP_M) {
            trips = (listOf(trip) + trips).take(MAX_TRIPS)
            store.edit { putString(TRIPS, GarageJson.writeTrips(trips)) }
        }
        trip = TripTotals()
    }

    /** "Nuevo viaje": el de ahora pasa a la lista y se empieza de cero. */
    fun newTrip() {
        finishTrip()
        flush()
    }

    /** Empieza el total de cero (no toca el odómetro ni los viajes). */
    fun resetTotal() {
        total = TripTotals(since = System.currentTimeMillis())
        flush()
    }

    /** Lo que marca el tablero: la primera vez lo pone; después corrige (y aprende cuánto se equivoca el GPS). */
    fun setOdometer(km: Double) {
        val now = System.currentTimeMillis()
        odometer = odometer?.correct(km, now) ?: Odometer(km, setAt = now)
        flush()
    }

    fun addFuel(e: FuelEntry) = saveFuel((fuel + e).sortedByDescending { it.km })

    fun removeFuel(e: FuelEntry) = saveFuel(fuel - e)

    private fun saveFuel(list: List<FuelEntry>) {
        fuel = list
        store.edit { putString(FUEL, GarageJson.writeFuel(list)) }
    }

    fun addLog(e: LogEntry) = saveLog((log + e).sortedByDescending { it.date })

    fun removeLog(e: LogEntry) = saveLog(log - e)

    private fun saveLog(list: List<LogEntry>) {
        log = list
        store.edit { putString(LOG, GarageJson.writeLog(list)) }
    }

    companion object {
        private const val STORE = "garage"
        private const val TRIP = "trip"
        private const val TOTAL = "total"
        private const val TRIPS = "trips"
        private const val ODOMETER = "odometer"
        private const val FUEL = "fuel"
        private const val LOG = "log"
        private const val SAVE_EVERY_MS = 20_000L
        private const val MAX_TRIPS = 12
        private const val MIN_TRIP_M = 200.0

        @SuppressLint("StaticFieldLeak") // es el de la aplicación
        @Volatile private var instance: Garage? = null

        fun get(context: Context): Garage = instance ?: synchronized(this) {
            instance ?: Garage(context.applicationContext).also { instance = it }
        }
    }
}
