package com.irvingguerra.litedash

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin

class RoadMathTest {
    private val p = CarreteraRoad

    @Test fun `el borde de abajo del plano queda donde lo pone el mockup`() {
        // v = 0: sin achicarse, en y = 528 y la x tal cual (izquierda 12 + u).
        assertEquals(1f, p.scale(0f), 1e-6f)
        assertEquals(528f, p.y(0f), 1e-4f)
        assertEquals(12f + 295f, p.x(295f, 0f), 1e-4f)
        assertEquals(12f + 705f, p.x(705f, 0f), 1e-4f)
    }

    @Test fun `la proyeccion es la del CSS 3D (rotateX 88 con perspectiva 300)`() {
        // Un punto a v = 1000 px: z = −v·sen 88°, y = 528 − v·cos 88°, visto desde (512, 206.5) a 300 px.
        val v = 1000f
        val s = sin(Math.toRadians(88.0)).toFloat()
        val c = cos(Math.toRadians(88.0)).toFloat()
        val f = 300f / (300f + v * s)
        assertEquals(f, p.scale(v), 1e-6f)
        assertEquals(206.5f + (528f - v * c - 206.5f) * f, p.y(v), 1e-3f)
        assertEquals(512f + (12f + 100f - 512f) * f, p.x(100f, v), 1e-3f)
    }

    @Test fun `lo lejano se junta en el horizonte, justo debajo del agujero negro`() {
        assertEquals(196.0f, p.horizon, 0.1f)
        // El final del plano (3000 px) cae un poco abajo del horizonte; más lejos, más cerca de él.
        assertTrue(p.y(3000f) > p.horizon)
        assertTrue(p.y(30000f) - p.horizon < p.y(3000f) - p.horizon)
        // Todo lo de en medio (u = 500) se queda en la x de fuga.
        assertEquals(512f, p.x(500f, 2000f), 1e-3f)
    }

    @Test fun `vAt es la inversa de y`() {
        for (v in listOf(0f, 12f, 180f, 999f, 2500f)) assertEquals(v, p.vAt(p.y(v)), v * 1e-3f + 1e-2f)
        assertEquals(Float.POSITIVE_INFINITY, p.vAt(150f))
    }

    @Test fun `la fase avanza con la velocidad real`() {
        // 36 km/h = 10 m/s = 200 px del plano por segundo.
        assertEquals(200f, roadAdvance(36f, 1f), 1e-3f)
        // 62 km/h: unos 344 px/s, casi lo del mockup (360 px/s).
        assertEquals(344.4f, roadAdvance(62f, 1f), 0.1f)
        assertEquals(0f, roadAdvance(0f, 1f), 0f)
        assertEquals(0f, roadAdvance(Float.NaN, 1f), 0f)
        assertEquals(0f, roadAdvance(-5f, 1f), 0f)
    }

    @Test fun `la fase da la vuelta y no brinca al volver del fondo`() {
        assertEquals(10f, roadPhase(350f, 36f, 0.1f), 1e-3f)
        // Un cuadro de 33 ms a 100 km/h.
        val step = roadPhase(0f, 100f, 1f / 30f)
        assertEquals(100f / 3.6f * ROAD_PX_PER_M / 30f, step, 1e-3f)
        // 10 s sin cuadros (pantalla apagada): cuenta a lo más un cuarto de segundo.
        assertEquals(roadAdvance(100f, 0.25f), roadPhase(0f, 100f, 10f), 1e-3f)
        val any = roadPhase(359f, 180f, 0.25f)
        assertTrue(any >= 0f && any < ROAD_PHASE_PERIOD)
    }

    @Test fun `solo se anima viéndose y andando`() {
        assertTrue(roadAnimating(62f, visible = true))
        assertFalse(roadAnimating(62f, visible = false))
        assertFalse(roadAnimating(0f, visible = true))
        assertFalse(roadAnimating(0.5f, visible = true))
        assertFalse(roadAnimating(null, visible = true))
    }

    @Test fun `las rayas son las del repeating-linear-gradient del mockup`() {
        val out = FloatArray(64)
        // Fase 0: encendido donde (3200 − v) mod 180 < 70 → tramos (3200 − 70 − 180k, 3200 − 180k).
        val n = roadSpans(0f, 180f, 70f, 3200f, 3000f, out)
        assertEquals(0, n % 2)
        for (i in 0 until n step 2) {
            val mid = (out[i] + out[i + 1]) / 2f
            val t = ((3200f - mid) % 180f + 180f) % 180f
            assertTrue("tramo ${out[i]}–${out[i + 1]}", t < 70f)
            assertTrue(out[i] >= 0f && out[i + 1] <= 3000f)
        }
        // El primero empieza en 3200 − 70 − 17·180 = 70 y va hasta 140; el anterior queda detrás de ti.
        assertEquals(70f, out[0], 1e-3f)
        assertEquals(140f, out[1], 1e-3f)
        assertEquals(250f, out[2], 1e-3f)
    }

    @Test fun `al avanzar la fase las rayas vienen hacia ti`() {
        val a = FloatArray(64)
        val b = FloatArray(64)
        roadSpans(0f, 180f, 70f, 3200f, 3000f, a)
        roadSpans(30f, 180f, 70f, 3200f, 3000f, b)
        // La de v = 70 pasa a v = 40: 30 px más cerca.
        assertEquals(a[0] - 30f, b[0], 1e-3f)
        // Una vuelta entera de la fase deja todo igual.
        val c = FloatArray(64)
        val n = roadSpans(ROAD_PHASE_PERIOD, 180f, 70f, 3200f, 3000f, c)
        for (i in 0 until n) assertEquals(a[i], c[i], 1e-2f)
    }
}
