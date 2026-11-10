package com.irvingguerra.litedash

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FmStationsTest {
    @Test
    fun readsTheFrequencyTheRadioReports() {
        assertEquals(989, FmStations.fromText("FM 98.9"))
        assertEquals(1029, FmStations.fromText("Radioacktiva 102,9 MHz"))
        assertEquals(875, FmStations.fromText("87.5"))
        assertEquals(1080, FmStations.fromText("FM 108.0"))
        assertEquals(915, FmStations.fromText("FM 91.50"))
        // Lo que no es FM, o está fuera de la banda, o no dice frecuencia: nada (nunca se inventa).
        assertNull(FmStations.fromText("AM 1040"))
        assertNull(FmStations.fromText("AM 10.4"))
        assertNull(FmStations.fromText("FM 120.5"))
        assertNull(FmStations.fromText("Olímpica Stereo"))
        assertNull(FmStations.fromText(""))
        assertNull(FmStations.fromText(null))
        assertNull(FmStations.fromText("1.989.5"))
    }

    @Test
    fun formatsWithADot() {
        assertEquals("98.9", FmStations.format(989))
        assertEquals("102.9", FmStations.format(1029))
        assertEquals("87.5", FmStations.format(875))
        assertEquals(875, FmStations.clamp(800))
        assertEquals(1080, FmStations.clamp(1200))
    }

    @Test
    fun savesRenamesAndDeletes() {
        var list = emptyList<FmStation>()
        list = FmStations.add(list, FmStation(1029, "Radioacktiva"))
        list = FmStations.add(list, FmStation(889, " La Mega "))
        list = FmStations.add(list, FmStation(989, ""))
        assertEquals(listOf(889, 989, 1029), list.map { it.tenths })
        assertEquals("La Mega", list.first().name)
        // La misma frecuencia otra vez: no se repite, cambia el nombre.
        list = FmStations.add(list, FmStation(989, "Olímpica"))
        assertEquals(3, list.size)
        assertEquals("Olímpica", FmStations.at(list, 989)?.name)
        list = FmStations.rename(list, 1029, "La X")
        assertEquals("La X", FmStations.at(list, 1029)?.name)
        list = FmStations.remove(list, 889)
        assertEquals(listOf(989, 1029), list.map { it.tenths })
        assertNull(FmStations.at(list, 889))
    }

    @Test
    fun survivesTheRoundTripWithAnyName() {
        val list = listOf(FmStation(889, "La Mega"), FmStation(989, "Olímpica: 100% | éxitos"), FmStation(1049, ""))
        assertEquals(list, FmStations.parse(FmStations.encode(list)))
        assertEquals(emptyList<FmStation>(), FmStations.parse(null))
        // Lo dañado se salta; lo repetido y desordenado se arregla.
        assertEquals(listOf(FmStation(899, "Los 40"), FmStation(939, "")), FmStations.parse("939:|basura|899:Los 40|2000:x|939:otra"))
    }

    @Test
    fun keepsALimit() {
        var list = emptyList<FmStation>()
        for (i in 0 until FmStations.LIMIT + 5) list = FmStations.add(list, FmStation(FmStations.MIN + i * 5, ""))
        assertEquals(FmStations.LIMIT, list.size)
    }

    @Test
    fun scanMergesWithoutLosingNames() {
        val saved = listOf(FmStation(989, "Olímpica"), FmStation(1029, ""))
        val found = listOf(FmStation(889, "MEGA"), FmStation(989, "OLIMPICA"), FmStation(1029, "RADIOACK"), FmStation(889, "MEGA"))
        val out = FmStations.merge(saved, found)
        assertEquals(listOf(889, 989, 1029), out.map { it.tenths })
        assertEquals("Olímpica", FmStations.at(out, 989)?.name)
        assertEquals("RADIOACK", FmStations.at(out, 1029)?.name)
        assertEquals("MEGA", FmStations.at(out, 889)?.name)
    }

    @Test
    fun scanStopsAfterAFullTurn() {
        // Empieza en 98.9 y busca hacia arriba.
        assertFalse(FmStations.scanDone(989, emptyList(), 1029))
        assertFalse(FmStations.scanDone(989, listOf(1029), 1049))
        // Al pasar de 108 vuelve abajo: sigue hasta llegar otra vez a donde empezó.
        assertFalse(FmStations.scanDone(989, listOf(1029, 1049), 889))
        assertFalse(FmStations.scanDone(989, listOf(1029, 1049, 889), 939))
        assertTrue(FmStations.scanDone(989, listOf(1029, 1049, 889, 939), 989))
        assertTrue(FmStations.scanDone(989, listOf(1029, 1049, 889, 939), 1029))
        // Si la radio se queda en la misma, también se para.
        assertTrue(FmStations.scanDone(989, emptyList(), 989))
        assertTrue(FmStations.scanDone(989, listOf(1029), 1029))
    }
}
