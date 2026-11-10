package com.irvingguerra.litedash

import com.irvingguerra.litedash.core.vehicle.Source
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeedOnlyTest {
    @Test
    fun numeroEnteroYParadoEsCero() {
        assertEquals(0, speedOnlyShown(0f, miles = false))
        // El GPS "camina" con el carro quieto: menos de 2 km/h se ve 0.
        assertEquals(0, speedOnlyShown(1.6f, miles = false))
        assertEquals(3, speedOnlyShown(2.5f, miles = false))
        assertEquals(60, speedOnlyShown(60.4f, miles = false))
        assertEquals(61, speedOnlyShown(60.5f, miles = false))
        // 100 km/h = 62.1 mph.
        assertEquals(62, speedOnlyShown(100f, miles = true))
        // Datos raros no rompen la pantalla.
        assertEquals(0, speedOnlyShown(Float.NaN, miles = false))
        assertEquals(0, speedOnlyShown(-5f, miles = false))
        assertEquals(999, speedOnlyShown(5000f, miles = false))
    }

    @Test
    fun sinDatoEsGuionApagado() {
        val v = speedOnlyView(null, miles = false, limit = 80)
        assertNull(v.number)
        assertEquals("—", v.text)
        assertTrue(v.dim)
        assertFalse(v.over)
        assertEquals(0f, v.fraction, 0f)
    }

    @Test
    fun rojoSoloPorEncimaDelLimite() {
        // Se ve 80: justo en el límite todavía no es rojo.
        assertFalse(speedOnlyView(80.4f, miles = false, limit = 80).over)
        assertTrue(speedOnlyView(81f, miles = false, limit = 80).over)
        assertFalse(speedOnlyView(150f, miles = false, limit = null).over)
        assertEquals("81", speedOnlyView(81f, miles = false, limit = 80).text)
        // En mph el límite de 80 km/h es 50 mph: 82 km/h = 51 mph ya es rojo.
        assertTrue(speedOnlyView(82f, miles = true, limit = speedOnlyLimitShown(80, miles = true)).over)
    }

    @Test
    fun conElGpsPerdidoSeVeApagadoYNuncaRojo() {
        val v = speedOnlyView(95f, miles = false, limit = 80, stale = true)
        assertEquals(95, v.number)
        assertTrue(v.dim)
        assertFalse(v.over)
    }

    @Test
    fun igualMientrasNoCambieElNumero() {
        // 61.2 y 61.4 km/h se ven igual: la pantalla no se vuelve a dibujar.
        assertEquals(speedOnlyView(61.2f, miles = false, limit = 80), speedOnlyView(61.4f, miles = false, limit = 80))
        assertFalse(speedOnlyView(61.2f, miles = false, limit = 80) == speedOnlyView(61.6f, miles = false, limit = 80))
    }

    @Test
    fun arcoHasta160KmhO100Mph() {
        assertEquals(0.5f, speedOnlyView(80f, miles = false, limit = null).fraction, 0.001f)
        assertEquals(1f, speedOnlyView(200f, miles = false, limit = null).fraction, 0f)
        assertEquals(0f, speedOnlyView(1f, miles = false, limit = null).fraction, 0f)
        assertEquals(0.5f, speedOnlyFraction(50, miles = true), 0.001f)
    }

    @Test
    fun limitesEnParejasKmhMph() {
        assertEquals(80, speedOnlyLimitShown(80, miles = false))
        assertEquals(40, speedOnlyLimitShown(60, miles = true))
        assertEquals(50, speedOnlyLimitShown(80, miles = true))
        assertEquals(75, speedOnlyLimitShown(120, miles = true))
        // Uno que no está en la lista se convierte.
        assertEquals(56, speedOnlyLimitShown(90, miles = true))
        assertEquals(25, speedOnlyAutoShown(40, miles = true))
        assertEquals(40f, speedOnlyAutoThresholdKmh(40, miles = false), 0f)
        // 25 mph = 40.2 km/h.
        assertEquals(40.23f, speedOnlyAutoThresholdKmh(40, miles = true), 0.01f)
    }

    @Test
    fun guardadoDesconocidoEsApagado() {
        assertNull(speedOnlyOption(0, SPEED_ONLY_LIMITS_KMH))
        assertNull(speedOnlyOption(75, SPEED_ONLY_LIMITS_KMH))
        assertEquals(100, speedOnlyOption(100, SPEED_ONLY_LIMITS_KMH))
        assertEquals(20, speedOnlyOption(20, SPEED_ONLY_AUTO_KMH))
        assertNull(speedOnlyOption(30, SPEED_ONLY_AUTO_KMH))
    }

    @Test
    fun pitaUnaSolaVezAlPasarse() {
        val beep = SpeedOnlyBeep(80)
        assertFalse(beep.update(79))
        assertFalse(beep.update(80))
        assertTrue(beep.update(81))
        // Sigue por encima o baila alrededor del límite: no vuelve a pitar.
        assertFalse(beep.update(85))
        assertFalse(beep.update(79))
        assertFalse(beep.update(81))
        // Sin dato (o GPS perdido) no cuenta.
        assertFalse(beep.update(null))
        // Ya bajó de verdad (77 o menos): la próxima vez sí pita.
        assertFalse(beep.update(77))
        assertTrue(beep.update(82))
    }

    @Test
    fun alAbrirYaPasadoPitaDeUna() {
        assertTrue(SpeedOnlyBeep(60).update(72))
    }

    @Test
    fun seAbreSolaAlPasarDespuesDeIrDespacio() {
        val t = SpeedOnlyTrigger(40f)
        // Al volver al inicio ya rápido no se abre de una.
        assertFalse(t.update(70f))
        assertFalse(t.update(50f))
        // Bajó a menos de la mitad (20) y volvió a pasar de 40: se abre una vez.
        assertFalse(t.update(15f))
        assertFalse(t.update(39f))
        assertTrue(t.update(41f))
        assertFalse(t.update(60f))
        // Bailando entre 30 y 45 no se vuelve a abrir.
        assertFalse(t.update(30f))
        assertFalse(t.update(45f))
        // Sin dato no cambia nada.
        assertFalse(t.update(null))
        assertFalse(t.update(Float.NaN))
        assertFalse(t.update(0f))
        assertFalse(t.update(null))
        assertTrue(t.update(42f))
    }

    @Test
    fun estadoDeLaFuente() {
        assertEquals(SpeedOnlyStatus.OBD, speedOnlyStatus(Source.OBD, obdOn = true, gpsAllowed = false, gpsStale = true))
        assertEquals(SpeedOnlyStatus.CAN, speedOnlyStatus(Source.CAN, obdOn = false, gpsAllowed = true, gpsStale = false))
        assertEquals(SpeedOnlyStatus.GPS, speedOnlyStatus(Source.GPS, obdOn = false, gpsAllowed = true, gpsStale = false))
        assertEquals(SpeedOnlyStatus.GPS_LOST, speedOnlyStatus(Source.GPS, obdOn = false, gpsAllowed = true, gpsStale = true))
        assertEquals(SpeedOnlyStatus.GPS_SEARCHING, speedOnlyStatus(null, obdOn = false, gpsAllowed = true, gpsStale = false))
        assertEquals(SpeedOnlyStatus.OBD_WAITING, speedOnlyStatus(null, obdOn = true, gpsAllowed = true, gpsStale = false))
        assertEquals(SpeedOnlyStatus.NO_PERMISSION, speedOnlyStatus(null, obdOn = false, gpsAllowed = false, gpsStale = false))
        assertTrue(SpeedOnlyStatus.GPS.ok)
        assertFalse(SpeedOnlyStatus.GPS_LOST.ok)
    }
}
