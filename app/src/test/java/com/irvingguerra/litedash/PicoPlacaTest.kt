package com.irvingguerra.litedash

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PicoPlacaTest {
    private val rules = PicoRules.parse(File("src/main/res/raw/pico_y_placa.json").readText())

    @Test
    fun colombianHolidays2025() {
        // Calendario oficial de festivos 2025.
        val expected = listOf(
            "2025-01-01", "2025-01-06", "2025-03-24", "2025-04-17", "2025-04-18", "2025-05-01",
            "2025-06-02", "2025-06-23", "2025-06-30", "2025-07-20", "2025-08-07", "2025-08-18",
            "2025-10-13", "2025-11-03", "2025-11-17", "2025-12-08", "2025-12-25",
        ).map { Day.parse(it) }.toSet()
        assertEquals(expected, ColombiaHolidays.of(2025))
    }

    @Test
    fun easter() {
        assertEquals(Day(2025, 4, 20), ColombiaHolidays.easter(2025))
        assertEquals(Day(2026, 4, 5), ColombiaHolidays.easter(2026))
    }

    @Test
    fun bogotaEvenOdd() {
        val bogota = rules.city("bogota")!!
        // Martes 3 nov 2026 (impar): circulan 1–5, restricción 6–0.
        assertTrue(bogota.restricted(Day(2026, 11, 3), 7))
        assertFalse(bogota.restricted(Day(2026, 11, 3), 2))
        // Viernes 6 nov 2026 (par): circulan 6–0, restricción 1–5.
        assertTrue(bogota.restricted(Day(2026, 11, 6), 2))
        assertFalse(bogota.restricted(Day(2026, 11, 6), 7))
        // Lunes festivo 16 nov 2026 y domingo 8 nov: nadie.
        assertTrue(bogota.digitsOn(Day(2026, 11, 16)).isEmpty())
        assertTrue(bogota.digitsOn(Day(2026, 11, 8)).isEmpty())
    }

    @Test
    fun medellinWeekdays() {
        val med = rules.city("medellin")!!
        // Lunes 9 nov 2026: 5 y 8.
        assertEquals(setOf(5, 8), med.digitsOn(Day(2026, 11, 9)))
        // Martes 10 nov 2026: 1 y 4.
        assertEquals(setOf(1, 4), med.digitsOn(Day(2026, 11, 10)))
        assertFalse(med.outdated(Day(2026, 11, 9)))
        assertTrue(med.outdated(Day(2027, 2, 1)))
    }

    @Test
    fun hours() {
        assertEquals(PicoHours(360, 1260), PicoHours.parse("06:00-21:00"))
        assertEquals("6:00–21:00", PicoHours(360, 1260).toString())
        assertEquals(null, PicoHours.parse("21:00-06:00"))
    }
}
