package com.irvingguerra.litedash

import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

/*
 * SailDash 1.1 · la carretera del diseño C (mock-C/carretera.html), sin Android ni Compose para poder
 * probarla: la proyección del plano de la carretera a la pantalla y cuánto avanzan las rayas con la
 * velocidad. Todo en dp del lienzo de 1024 × 600 ([com.irvingguerra.litedash.core.ui.ice.IceFrame]).
 *
 * El mockup arma la carretera con CSS 3D: un plano de 1000 × 3000 px (`.plane`) con el borde de abajo en
 * la pantalla y acostado 88° hacia el fondo (`rotateX(88deg)`), visto con `perspective: 300px` desde el
 * punto de fuga. Aquí se hace la misma cuenta: un punto del plano a [u] px de su orilla izquierda y [v]
 * px de su borde de abajo cae en
 *   f = P / (P + v·sen 88°)
 *   x = fx + (izquierda + u − fx) · f
 *   y = fy + (abajo − v·cos 88° − fy) · f
 */
internal class RoadProjection(
    /** El punto de fuga (perspective-origin) en el lienzo. */
    val vanishX: Float,
    val vanishY: Float,
    /** Donde queda el borde de abajo del plano (y en el lienzo). */
    val bottom: Float,
    /** La orilla izquierda del plano (x en el lienzo; el plano mide [width]). */
    val left: Float,
    val width: Float = 1000f,
    /** La distancia de la cámara (`perspective`). */
    val perspective: Float = 300f,
    tiltDeg: Float = 88f,
) {
    private val sinT = sin(Math.toRadians(tiltDeg.toDouble())).toFloat()
    private val cosT = cos(Math.toRadians(tiltDeg.toDouble())).toFloat()

    /** Cuánto se achica lo que está a [v] px del borde de abajo (1 en el borde, tiende a 0 a lo lejos). */
    fun scale(v: Float): Float = perspective / (perspective + v * sinT)

    fun x(u: Float, v: Float): Float = vanishX + (left + u - vanishX) * scale(v)

    fun y(v: Float): Float = vanishY + (bottom - v * cosT - vanishY) * scale(v)

    /** Hasta dónde sube la carretera infinita: el horizonte (y en el lienzo). */
    val horizon: Float get() = vanishY - perspective * cosT / sinT

    /** Lo que vale [v] en la fila [y] del lienzo (la inversa de [y]); para y arriba del horizonte, infinito. */
    fun vAt(y: Float): Float {
        // y − fy = (abajo − fy − v·cos)·P / (P + v·sen) → v = P·(abajo − y) / ((y − fy)·sen + P·cos)
        val den = (y - vanishY) * sinT + perspective * cosT
        return if (den <= 0f) Float.POSITIVE_INFINITY else perspective * (bottom - y) / den
    }
}

/** La carretera de la página Carretera, tal cual el mockup (escena de 186 a 528, fuga en 512, 206.5). */
internal val CarreteraRoad = RoadProjection(vanishX = 512f, vanishY = 206.5f, bottom = 528f, left = 12f)

/** Píxeles del plano por metro de carretera: una raya y su hueco (180 px) son 9 m, como en una vía real. */
internal const val ROAD_PX_PER_M = 20f

/** El periodo común de las rayas (180 px) y los reflectores (360 px): la fase se lleva módulo esto. */
internal const val ROAD_PHASE_PERIOD = 360f

/** Por debajo de esto el carro está quieto (el GPS "camina" un poco): nada se anima. */
internal const val ROAD_MOVING_KMH = 1f

/** Los cuadros por segundo de la carretera, a lo más (el T3L no da para más sin gastar). */
internal const val ROAD_FPS = 30

/** Cuántos px del plano avanzan las rayas en [dtSec] segundos a [kmh]. */
internal fun roadAdvance(kmh: Float, dtSec: Float): Float {
    if (kmh.isNaN() || kmh <= 0f || dtSec <= 0f) return 0f
    return kmh / 3.6f * ROAD_PX_PER_M * dtSec
}

/** La fase nueva (0 ≤ fase < [ROAD_PHASE_PERIOD]) después de [dtSec] segundos a [kmh]. */
internal fun roadPhase(phase: Float, kmh: Float, dtSec: Float): Float {
    // Un salto largo (la app volvió del fondo) no debe brincar: a lo más un cuarto de segundo.
    val next = phase + roadAdvance(kmh, dtSec.coerceAtMost(0.25f))
    return next - floor(next / ROAD_PHASE_PERIOD) * ROAD_PHASE_PERIOD
}

/** Si la carretera se anima: se ve y el carro anda. Quieto o tapado, no corre nada. */
internal fun roadAnimating(kmh: Float?, visible: Boolean): Boolean = visible && kmh != null && kmh >= ROAD_MOVING_KMH

/**
 * Los tramos encendidos de una línea que se repite a lo largo del plano (`repeating-linear-gradient`
 * de [on] px encendidos cada [period] px, que en CSS empieza [offset] px antes del borde de abajo y baja
 * [phase] px): pares (desde, hasta) en px del borde de abajo, recortados a 0..[far]. [out] se reutiliza.
 * Devuelve cuántos números escribió (el doble de los tramos).
 */
internal fun roadSpans(phase: Float, period: Float, on: Float, offset: Float, far: Float, out: FloatArray): Int {
    // Encendido donde (offset − v − phase) mod period < on: tramos que empiezan en offset − phase − on + k·period.
    val first = offset - phase - on
    var start = first - floor(first / period) * period - period
    var n = 0
    while (start < far && n + 1 < out.size) {
        val a = maxOf(start, 0f)
        val b = minOf(start + on, far)
        if (b > a) {
            out[n++] = a
            out[n++] = b
        }
        start += period
    }
    return n
}
