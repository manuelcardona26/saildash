package com.irvingguerra.litedash

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OwnerCarTest {
    // Lo que trae car/out/sail_front.json del diseño (recortado): faros de la imagen con piso de 842 px.
    private val json = """
        {"floor": {"size": [842, 562], "headlights": [
            {"x": 94.5, "y": 188.4, "r_core": 17.8, "r_glow": 63.3},
            {"x": 302.6, "y": 194.3, "r_core": 41.0, "r_glow": 151.1}
        ]}}
    """.trimIndent()

    @Test
    fun headlightsAreFractionsOfTheFloorImageWidth() {
        val lights = OwnerCar.headlights(json, 842f)
        assertEquals(2, lights.size)
        // En el Inicio la foto mide 543 dp desde (30, 170): el faro izquierdo cae en (91, 291) como la .bloom.a del mockup.
        assertEquals(91f, 30f + lights[0].x * 543f, 1f)
        assertEquals(291.5f, 170f + lights[0].y * 543f, 1f)
        assertEquals(225f, 30f + lights[1].x * 543f, 1f)
        assertTrue(lights[1].glow > lights[0].glow)
    }

    @Test
    fun noFloorMeansNoHeadlights() {
        assertTrue(OwnerCar.headlights("""{"car": {}}""", 700f).isEmpty())
    }
}
