package com.irvingguerra.litedash

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** El Taller sin OBD: viaje, odómetro por GPS, tanqueadas y bitácora. */
class GarageRulesTest {
    private val day = Day(2026, 11, 9)

    // ---------------------------------------------------------------- Viaje

    @Test fun tripAddsMetersAndOnlyContinuousTime() {
        var t = TripTotals()
        t = t.add(500.0, 1_000, now = 10_000)
        t = t.add(500.0, 1_000, now = 11_000)
        // Un hueco de 30 s (túnel, GPS apagado): la distancia cuenta, el tiempo no.
        t = t.add(400.0, 30_000, now = 41_000)
        assertEquals(1_400.0, t.meters, 1e-9)
        assertEquals(2_000L, t.movingMs)
        assertEquals(10_000L, t.since)
        assertEquals(41_000L, t.lastMoveAt)
    }

    @Test fun averageNeedsAMinuteAndAHundredMeters() {
        val short = TripTotals(meters = 50.0, movingMs = 120_000)
        assertNull(short.averageKmh)
        val quick = TripTotals(meters = 900.0, movingMs = 30_000)
        assertNull(quick.averageKmh)
        // 30 km en 30 min = 60 km/h.
        val ok = TripTotals(meters = 30_000.0, movingMs = 30 * 60_000L)
        assertEquals(60.0, ok.averageKmh!!, 1e-9)
    }

    @Test fun negativeStepsDoNotSubtract() {
        val t = TripTotals().add(-20.0, 1_000, 5)
        assertEquals(0.0, t.meters, 0.0)
    }

    @Test fun newTripAfterTwoHoursStill() {
        val t = TripTotals(meters = 1_000.0, lastMoveAt = 1_000)
        assertFalse(TripMath.isNewTrip(t, 1_000 + TripMath.NEW_TRIP_AFTER_MS - 1))
        assertTrue(TripMath.isNewTrip(t, 1_000 + TripMath.NEW_TRIP_AFTER_MS))
        // Un viaje vacío nunca "termina".
        assertFalse(TripMath.isNewTrip(TripTotals(), Long.MAX_VALUE))
    }

    // ---------------------------------------------------------------- Odómetro

    @Test fun odometerAccumulatesGps() {
        var o = Odometer(85_000.0)
        o = o.add(1_500.0).add(500.0)
        assertEquals(85_002.0, o.km, 1e-9)
    }

    @Test fun correctionStartsOverAndLearnsTheGpsError() {
        // 100 km de GPS, pero el tablero dice que fueron 102: el GPS se queda corto un 2 %.
        val o = Odometer(85_000.0, gpsKm = 100.0)
        val fixed = o.correct(85_102.0, now = 7)
        assertEquals(85_102.0, fixed.km, 1e-9)
        assertEquals(0.0, fixed.gpsKm, 0.0)
        assertEquals(1.02, fixed.factor, 1e-9)
        assertEquals(7L, fixed.setAt)
        // Lo siguiente ya se suma con el ajuste.
        assertEquals(85_102.0 + 10.2, fixed.add(10_000.0).km, 1e-9)
    }

    @Test fun correctionWithLittleGpsKeepsTheFactor() {
        // Con poco GPS medido, la diferencia es error de lectura: no se aprende nada.
        val o = Odometer(85_000.0, gpsKm = 10.0, factor = 1.01)
        assertEquals(1.01, o.correct(85_030.0, 0).factor, 1e-9)
    }

    @Test fun learnedFactorIsClamped() {
        // Si alguien escribe mal el número, el ajuste no se vuelve loco.
        val o = Odometer(85_000.0, gpsKm = 100.0)
        assertEquals(Odometer.MAX_FACTOR, o.correct(86_000.0, 0).factor, 1e-9)
        assertEquals(1.0, o.correct(84_000.0, 0).factor, 1e-9)
    }

    @Test fun oilChangeKmLeft() {
        assertEquals(400L, kmLeft(85_400, 85_000.4))
        assertEquals(-120L, kmLeft(85_000, 85_120.0))
    }

    // ---------------------------------------------------------------- Tanqueadas

    private fun fill(id: Long, km: Double, gallons: Double, price: Double, full: Boolean = true) = FuelEntry(id, day, gallons, price, km, full)

    @Test fun economyBetweenFullTanks() {
        val entries = listOf(
            fill(1, 84_000.0, 9.0, 140_000.0),
            fill(2, 84_400.0, 10.0, 160_000.0),
        )
        val r = FuelMath.results(entries)
        assertNull(r[1])
        val second = r[2]!!
        assertEquals(400.0, second.km, 1e-9)
        assertEquals(40.0, second.kmPerGallon, 1e-9)
        assertEquals(400.0, second.costPerKm, 1e-9)
    }

    @Test fun partialFillAddsToTheNextFullOne() {
        val entries = listOf(
            fill(1, 84_000.0, 9.0, 140_000.0),
            fill(2, 84_200.0, 4.0, 64_000.0, full = false),
            fill(3, 84_500.0, 8.5, 136_000.0),
        )
        val r = FuelMath.results(entries)
        assertNull(r[2])
        // 500 km con 12,5 galones = 40 km/gal; 200.000 pesos / 500 km = 400 por km.
        assertEquals(40.0, r[3]!!.kmPerGallon, 1e-9)
        assertEquals(400.0, r[3]!!.costPerKm, 1e-9)
    }

    @Test fun orderDoesNotMatter() {
        val a = fill(1, 84_000.0, 9.0, 140_000.0)
        val b = fill(2, 84_400.0, 10.0, 160_000.0)
        assertEquals(FuelMath.results(listOf(a, b)), FuelMath.results(listOf(b, a)))
    }

    @Test fun averageIsTotalKmOverTotalGallons() {
        val entries = listOf(
            fill(1, 84_000.0, 9.0, 140_000.0),
            fill(2, 84_400.0, 10.0, 150_000.0),
            fill(3, 84_700.0, 10.0, 170_000.0),
        )
        val avg = FuelMath.average(entries)!!
        assertEquals(700.0, avg.km, 1e-9)
        assertEquals(35.0, avg.kmPerGallon, 1e-9)
        assertEquals(320_000.0 / 700.0, avg.costPerKm, 1e-9)
        assertNull(FuelMath.average(entries.take(1)))
    }

    @Test fun pricePerGallon() {
        assertEquals(15_000.0, fill(1, 0.0, 10.0, 150_000.0).pricePerGallon, 1e-9)
    }

    @Test fun spentThisMonth() {
        val other = FuelEntry(9, Day(2026, 10, 30), 10.0, 99_000.0, 1.0)
        val list = listOf(fill(1, 0.0, 9.0, 140_000.0), fill(2, 10.0, 9.0, 100_000.0), other)
        assertEquals(240_000.0, FuelMath.spentInMonth(list, day), 1e-9)
    }

    @Test fun parsesWhatPeopleType() {
        assertEquals(10.5, FuelMath.parseDecimal("10,5")!!, 1e-9)
        assertEquals(10.5, FuelMath.parseDecimal("10.5")!!, 1e-9)
        assertNull(FuelMath.parseDecimal("0"))
        assertNull(FuelMath.parseDecimal("abc"))
        assertEquals(120_000.0, FuelMath.parsePesos("$120.000")!!, 1e-9)
        assertEquals(120_000.0, FuelMath.parsePesos("120,000")!!, 1e-9)
        assertNull(FuelMath.parsePesos(""))
    }

    // ---------------------------------------------------------------- Lo guardado

    @Test fun jsonRoundTrips() {
        val trip = TripTotals(1234.5, 60_000, 10, 20)
        assertEquals(trip, GarageJson.readTrip(GarageJson.writeTrip(trip)))
        assertEquals(listOf(trip, TripTotals()), GarageJson.readTrips(GarageJson.writeTrips(listOf(trip, TripTotals()))))
        val odo = Odometer(85_000.0, 12.5, 1.02, 99)
        assertEquals(odo, GarageJson.readOdometer(GarageJson.writeOdometer(odo)))
        val fuel = listOf(fill(1, 84_000.0, 9.5, 140_000.0, full = false))
        assertEquals(fuel, GarageJson.readFuel(GarageJson.writeFuel(fuel)))
        val log = listOf(
            LogEntry(1, LogKind.BRAKES, day, 85_000, "Pastillas", 180_000.0),
            LogEntry(2, LogKind.OIL, day, null),
        )
        assertEquals(log, GarageJson.readLog(GarageJson.writeLog(log)))
    }

    @Test fun brokenJsonIsIgnored() {
        assertNull(GarageJson.readTrip("{nada"))
        assertNull(GarageJson.readOdometer(null))
        assertEquals(emptyList<FuelEntry>(), GarageJson.readFuel("no es json"))
        // Un renglón dañado no se lleva a los demás.
        val text = """[{"id":1,"kind":"oil","date":"2026-11-09"},{"id":2,"kind":"cohete","date":"2026-11-09"},{"id":3}]"""
        assertEquals(listOf(LogEntry(1, LogKind.OIL, day, null)), GarageJson.readLog(text))
    }
}
