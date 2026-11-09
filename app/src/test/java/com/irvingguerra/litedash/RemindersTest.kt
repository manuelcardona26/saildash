package com.irvingguerra.litedash

import com.irvingguerra.litedash.ReminderType.EXTINGUISHER
import com.irvingguerra.litedash.ReminderType.FULL_COVERAGE
import com.irvingguerra.litedash.ReminderType.INSPECTION
import com.irvingguerra.litedash.ReminderType.INSURANCE
import com.irvingguerra.litedash.ReminderType.OTHER
import com.irvingguerra.litedash.ReminderType.PLATES
import com.irvingguerra.litedash.ReminderType.SERVICE
import com.irvingguerra.litedash.ReminderType.TAX
import com.irvingguerra.litedash.ReminderType.TIRE_ROTATION
import java.util.Locale
import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Recordatorios: la lista guardada (de antes y con km), las fechas siguientes y el kilometraje. */
class RemindersTest {
    private val today = Day(2026, 11, 9)

    @Test
    fun lasListasDeAntesSeLeenIgual() {
        // Tal cual las escribía LiteDash: sin "km".
        val old = """[
            {"id":1730000000000,"type":"service","date":"2026-12-01"},
            {"id":2,"type":"inspection","date":"2026-11-20"},
            {"id":3,"type":"tax","date":"2027-07-10"},
            {"id":4,"type":"insurance","date":"2027-03-05"},
            {"id":5,"type":"plates","date":"2030-01-15"},
            {"id":6,"type":"other","date":"2026-11-10"}
        ]"""
        val parsed = ReminderJson.parse(old)
        // Ordenados por fecha, el más próximo primero.
        assertEquals(listOf(OTHER, INSPECTION, SERVICE, INSURANCE, TAX, PLATES), parsed.reminders.map { it.type })
        assertEquals(Reminder(1730000000000, SERVICE, Day(2026, 12, 1)), parsed.reminders.first { it.id == 1730000000000 })
        assertTrue(parsed.reminders.all { it.km == null })
        assertTrue(parsed.unknown.isEmpty())

        // Al guardarlos otra vez quedan como antes: sin la clave "km".
        val written = JSONArray(ReminderJson.write(parsed.reminders))
        for (i in 0 until written.length()) {
            val o = written.getJSONObject(i)
            assertFalse(o.has("km"))
            assertEquals(setOf("id", "type", "date"), o.keys().asSequence().toSet())
        }
        assertEquals(parsed.reminders, ReminderJson.parse(written.toString()).reminders)
    }

    @Test
    fun loQueMigraLaVersionVieja() {
        // Los nombres en español de antes de la 1.4.47 pasan por LegacyNames y se leen bien.
        val migrated = LegacyNames.reminders("""[{"id":7,"tipo":"verificacion","fecha":"2026-12-01"},{"id":8,"tipo":"servicio","fecha":"2027-01-15"}]""")
        assertEquals(listOf(INSPECTION, SERVICE), ReminderJson.parse(migrated).reminders.map { it.type })
    }

    @Test
    fun elKmVaYVuelve() {
        val list = listOf(Reminder(10, SERVICE, Day(2026, 11, 12), 85_000), Reminder(11, INSURANCE, Day(2027, 1, 1)))
        val text = ReminderJson.write(list)
        val array = JSONArray(text)
        assertEquals(85_000, array.getJSONObject(0).getInt("km"))
        assertFalse(array.getJSONObject(1).has("km"))
        // Una versión vieja lee lo de siempre (id, type, date) y no ve el km.
        assertEquals(10L, array.getJSONObject(0).getLong("id"))
        assertEquals("service", array.getJSONObject(0).getString("type"))
        assertEquals("2026-11-12", array.getJSONObject(0).getString("date"))
        assertEquals(list, ReminderJson.parse(text).reminders)
    }

    @Test
    fun unKmRaroNoRompeNada() {
        val parsed = ReminderJson.parse(
            """[
                {"id":1,"type":"service","date":"2026-12-01","km":"85000"},
                {"id":2,"type":"service","date":"2026-12-02","km":0},
                {"id":3,"type":"service","date":"2026-12-03","km":-5},
                {"id":4,"type":"service","date":"2026-12-04","km":12345678901},
                {"id":5,"type":"service","date":"2026-12-05","km":"mucho"}
            ]""",
        )
        assertEquals(listOf(85_000, null, null, null, null), parsed.reminders.map { it.km })
    }

    @Test
    fun loQueNoSeEntiendeNoSeBorra() {
        val text = """[
            {"id":1,"type":"service","date":"2026-12-01","km":90000},
            {"id":2,"type":"teletransporte","date":"2027-01-01"},
            {"id":3,"type":"tax","date":"2026-02-30"},
            {"type":"insurance","date":"2027-01-01"},
            "raro"
        ]"""
        val parsed = ReminderJson.parse(text)
        assertEquals(listOf(Reminder(1, SERVICE, Day(2026, 12, 1), 90_000)), parsed.reminders)
        assertEquals(4, parsed.unknown.size)
        // Se guarda de vuelta tal cual, después de los que sí se entienden.
        val again = JSONArray(ReminderJson.write(parsed.reminders, parsed.unknown))
        assertEquals(5, again.length())
        assertEquals("teletransporte", again.getJSONObject(1).getString("type"))
        assertEquals("2026-02-30", again.getJSONObject(2).getString("date"))
        assertFalse(again.getJSONObject(3).has("id"))
        assertEquals("raro", again.getString(4))
    }

    @Test
    fun textoRotoEsListaVacia() {
        for (bad in listOf(null, "", "no es json", "{}", "[1, 2")) {
            val parsed = ReminderJson.parse(bad)
            assertTrue("$bad", parsed.reminders.isEmpty())
            assertTrue("$bad", parsed.unknown.isEmpty())
        }
    }

    @Test
    fun lasClavesGuardadasNoCambian() {
        assertEquals(
            listOf("service", "inspection", "tax", "insurance", "plates", "extinguisher", "full_coverage", "tire_rotation", "other"),
            ReminderType.entries.map { it.stored },
        )
        assertEquals(OTHER, ReminderType.entries.last())
        assertEquals(INSPECTION, ReminderType.fromStored("inspection"))
        assertNull(ReminderType.fromStored("verificacion"))
        // Cada cuánto se repiten (meses; 0 = no se repite).
        assertEquals(
            listOf(6L, 12L, 12L, 12L, 0L, 12L, 12L, 6L, 0L),
            listOf(SERVICE, INSPECTION, TAX, INSURANCE, PLATES, EXTINGUISHER, FULL_COVERAGE, TIRE_ROTATION, OTHER).map { it.months },
        )
        assertEquals(setOf(SERVICE, TIRE_ROTATION), ReminderType.entries.filter { it.hasKm }.toSet())
    }

    @Test
    fun laFechaSiguiente() {
        // Cambio de aceite y rotación: desde el día en que se hacen, antes o después de la fecha.
        assertEquals(Day(2027, 5, 9), SERVICE.next(Day(2026, 11, 12), today))
        assertEquals(Day(2027, 5, 9), SERVICE.next(Day(2026, 10, 1), today))
        assertEquals(Day(2027, 5, 9), TIRE_ROTATION.next(Day(2026, 11, 14), today))
        // Técnico-mecánica y extintor: un año desde que se hacen.
        assertEquals(Day(2027, 11, 9), INSPECTION.next(Day(2026, 11, 14), today))
        assertEquals(Day(2027, 11, 9), EXTINGUISHER.next(Day(2026, 10, 30), today))
        // SOAT y todo riesgo renovados antes: arrancan cuando se acaba el anterior; ya vencidos: desde hoy.
        assertEquals(Day(2027, 11, 12), INSURANCE.next(Day(2026, 11, 12), today))
        assertEquals(Day(2027, 11, 9), INSURANCE.next(Day(2026, 10, 1), today))
        assertEquals(Day(2027, 11, 15), FULL_COVERAGE.next(Day(2026, 11, 15), today))
        // Impuesto: la misma fecha límite cada año, aunque se pague antes, tarde o con un año de atraso.
        assertEquals(Day(2027, 7, 10), TAX.next(Day(2026, 7, 10), Day(2026, 7, 1)))
        assertEquals(Day(2027, 7, 10), TAX.next(Day(2026, 7, 10), today))
        assertEquals(Day(2027, 7, 10), TAX.next(Day(2025, 7, 10), today))
        // Hecho el mismo día en que vence, todos quedan como en LiteDash: la fecha más sus meses.
        for (t in ReminderType.entries.filter { it.months > 0 }) assertEquals("$t", today.plusMonths(t.months), t.next(today, today))
        // La licencia y Otro no se repiten.
        assertNull(PLATES.next(today, today))
        assertNull(OTHER.next(today, today))
        // 29 de febrero más un año: 28 de febrero.
        assertEquals(Day(2029, 2, 28), INSURANCE.next(Day(2028, 2, 29), Day(2028, 2, 20)))
    }

    @Test
    fun marcarComoHecho() {
        val oil = Reminder(1, SERVICE, Day(2026, 11, 12), 85_000)
        assertEquals(Reminder(1, SERVICE, Day(2027, 5, 9), 90_000), oil.done(90_000, today))
        // Sin km nuevo, el viejo no se queda: era el de este cambio.
        assertEquals(Reminder(1, SERVICE, Day(2027, 5, 9)), oil.done(null, today))
        // El SOAT no lleva km aunque llegue uno.
        assertEquals(Reminder(2, INSURANCE, Day(2027, 11, 12)), Reminder(2, INSURANCE, Day(2026, 11, 12)).done(50_000, today))
        // La licencia se quita.
        assertNull(Reminder(3, PLATES, Day(2026, 11, 12)).done(null, today))
    }

    @Test
    fun elKilometraje() {
        assertEquals(85_000, ReminderKm.parse("85000"))
        assertEquals(85_000, ReminderKm.parse("85.000"))
        assertEquals(85_000, ReminderKm.parse(" 85,000 km "))
        assertEquals(9_999_999, ReminderKm.parse("9999999"))
        assertNull(ReminderKm.parse(""))
        assertNull(ReminderKm.parse("0"))
        assertNull(ReminderKm.parse("km"))
        assertNull(ReminderKm.parse("12345678"))

        val colombia = Locale.forLanguageTag("es-CO")
        assertEquals("85.000", ReminderKm.format(85_000, colombia))
        assertEquals("1.234.567", ReminderKm.format(1_234_567, colombia))
        assertEquals("5.000", ReminderKm.format(5_000, Locale.forLanguageTag("es-MX")))
        assertEquals("500", ReminderKm.format(500, colombia))
        assertEquals("85,000", ReminderKm.format(85_000, Locale.ENGLISH))
    }
}
