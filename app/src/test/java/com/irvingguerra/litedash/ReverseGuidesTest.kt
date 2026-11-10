package com.irvingguerra.litedash

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

/** Las líneas guía de la vista de reversa y su calibración. */
class ReverseGuidesTest {
    @Test fun dragKeepsTheOrderAndAGap() {
        val g = ReverseGuides(near = 0.8f, mid = 0.6f, far = 0.4f)
        // La de 1 m no puede pasar por debajo de la de 0,5 m ni por encima de la de 2 m.
        assertEquals(0.8f - ReverseGuides.MIN_GAP, g.drag(1, 0.95f).mid, 1e-6f)
        assertEquals(0.4f + ReverseGuides.MIN_GAP, g.drag(1, 0.1f).mid, 1e-6f)
        assertEquals(0.7f, g.drag(1, 0.7f).mid, 1e-6f)
        // Ni salirse de la imagen.
        assertEquals(1f - ReverseGuides.EDGE, g.drag(0, 2f).near, 1e-6f)
        assertEquals(ReverseGuides.EDGE, g.drag(2, -1f).far, 1e-6f)
    }

    @Test fun perspectiveFitsAnExactPinhole() {
        // Una cámara de verdad: y = 0,35 + 0,25 / d.
        val g = ReverseGuides(near = 0.35f + 0.25f / 0.5f, mid = 0.35f + 0.25f / 1f, far = 0.35f + 0.25f / 2f)
        val p = g.perspective()
        assertEquals(0.35f, p.horizon, 1e-4f)
        assertEquals(0.25f, p.k, 1e-4f)
        assertEquals(1.5f, p.metersAt(p.yAt(1.5f))!!, 1e-3f)
        assertNull(p.metersAt(0.2f))
    }

    @Test fun linesNarrowWithDistance() {
        val g = ReverseGuides(near = 0.85f, mid = 0.6f, far = 0.475f, halfWidth = 0.4f)
        val w05 = g.halfWidthAt(0.5f)
        val w1 = g.halfWidthAt(1f)
        val w2 = g.halfWidthAt(2f)
        assertEquals(0.4f, w05, 1e-4f)
        assertTrue(w05 > w1 && w1 > w2)
        // Con una perspectiva exacta, a doble distancia la mitad de ancho.
        assertEquals(w1 / 2f, w2, 1e-3f)
    }

    @Test fun savedTextRoundTrips() {
        val g = ReverseGuides(0.81f, 0.63f, 0.47f, 0.3f, mirrored = true, calibrated = true)
        assertEquals(g, ReverseGuides.read(g.write()))
        // Lo dañado o en desorden no se usa.
        assertNull(ReverseGuides.read("0.5,0.6,0.7,0.3,0,1"))
        assertNull(ReverseGuides.read("basura"))
        assertNull(ReverseGuides.read(null))
    }

    @Test fun keyIsPerScreenAndCamera() {
        assertEquals("guides_1024x600_0", ReverseGuides.key(1024, 600, "0"))
    }

    @Test fun labels() {
        val es = Locale.forLanguageTag("es-CO")
        assertEquals("≈ 0,5 m", guideLabel(0.5f, es))
        assertEquals("≈ 1 m", guideLabel(1f, es))
        assertEquals("≈ 2 m", guideLabel(2f, Locale.US))
    }
}
