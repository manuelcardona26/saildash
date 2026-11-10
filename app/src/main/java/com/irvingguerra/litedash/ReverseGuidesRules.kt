// SailDash: las líneas guía de la vista de reversa (0,5 m, 1 m y 2 m). Cuentas puras, sin Android:
// se prueban en el PC (ReverseGuidesTest).
package com.irvingguerra.litedash

import java.util.Locale
import kotlin.math.abs

/**
 * Dónde van las tres líneas en la imagen, como fracción del alto (0 = arriba, 1 = abajo): [near]
 * a 0,5 m (roja, la más abajo), [mid] a 1 m (amarilla) y [far] a 2 m (verde, la más arriba).
 * [halfWidth] es la mitad del ancho de la línea de 0,5 m (fracción del ancho); las otras se angostan
 * con la perspectiva. [mirrored] = la imagen va en espejo.
 */
data class ReverseGuides(
    val near: Float = 0.86f,
    val mid: Float = 0.66f,
    val far: Float = 0.50f,
    val halfWidth: Float = 0.34f,
    val mirrored: Boolean = false,
    /** false = las de fábrica de SailDash, sin calibrar todavía. */
    val calibrated: Boolean = false,
) {
    /** La altura de cada línea en orden: 0,5 m, 1 m y 2 m. */
    val ys get() = listOf(near, mid, far)

    /**
     * Mueve la línea [index] (0 = 0,5 m, 1 = 1 m, 2 = 2 m) a [y]: nunca se cruza con sus vecinas
     * (queda a [MIN_GAP] de cada una) ni se sale de la imagen.
     */
    fun drag(index: Int, y: Float): ReverseGuides {
        val lower = when (index) { 0 -> 1f - EDGE; 1 -> near - MIN_GAP; else -> mid - MIN_GAP }
        val upper = when (index) { 0 -> mid + MIN_GAP; 1 -> far + MIN_GAP; else -> EDGE }
        val v = y.coerceIn(upper, lower)
        return when (index) {
            0 -> copy(near = v)
            1 -> copy(mid = v)
            else -> copy(far = v)
        }
    }

    /**
     * La perspectiva del piso: con la cámara mirando al suelo, la altura en la imagen sigue
     * y = horizonte + k / distancia. Se ajusta con las tres líneas (mínimos cuadrados en 1/d) y
     * sirve para el ancho de cada línea y para saber a qué distancia queda cualquier altura.
     */
    fun perspective(): Perspective = Perspective.fit(listOf(0.5f to near, 1f to mid, 2f to far))

    /** Mitad del ancho de la línea a [meters] (se angosta como 1/d desde la de 0,5 m). */
    fun halfWidthAt(meters: Float): Float {
        val p = perspective()
        val base = p.yAt(0.5f) - p.horizon
        if (base <= 0f) return halfWidth
        return (halfWidth * (p.yAt(meters) - p.horizon) / base).coerceIn(0.02f, 0.5f)
    }

    fun write(): String = String.format(Locale.US, "%.4f,%.4f,%.4f,%.4f,%d,%d", near, mid, far, halfWidth, if (mirrored) 1 else 0, if (calibrated) 1 else 0)

    companion object {
        /** Lo mínimo entre dos líneas (fracción del alto). */
        const val MIN_GAP = 0.03f

        /** Lo que se deja libre arriba y abajo. */
        const val EDGE = 0.02f

        val DISTANCES = listOf(0.5f, 1f, 2f)

        fun read(text: String?): ReverseGuides? = runCatching {
            val f = text!!.split(',')
            val g = ReverseGuides(f[0].toFloat(), f[1].toFloat(), f[2].toFloat(), f[3].toFloat(), f[4] == "1", f.getOrNull(5) == "1")
            // Lo guardado debe estar en orden y adentro de la imagen.
            if (g.near in 0f..1f && g.mid < g.near && g.far < g.mid && g.far >= 0f) g else null
        }.getOrNull()

        /** La llave de lo guardado: una calibración por pantalla (tamaño en píxeles) y cámara. */
        fun key(widthPx: Int, heightPx: Int, cameraId: String) = "guides_${widthPx}x${heightPx}_$cameraId"
    }
}

/** y = [horizon] + [k] / d: la altura (fracción) de lo que está a d metros detrás del carro. */
data class Perspective(val horizon: Float, val k: Float) {
    fun yAt(meters: Float) = horizon + k / meters

    /** A qué distancia está lo que se ve a la altura [y]; null por encima del horizonte. */
    fun metersAt(y: Float): Float? = if (y > horizon + 1e-4f && k > 0f) k / (y - horizon) else null

    companion object {
        /** Ajuste de mínimos cuadrados de y contra 1/d con los puntos (d, y). */
        fun fit(points: List<Pair<Float, Float>>): Perspective {
            val xs = points.map { 1f / it.first }
            val ys = points.map { it.second }
            val mx = xs.average().toFloat()
            val my = ys.average().toFloat()
            var sxy = 0f
            var sxx = 0f
            for (i in xs.indices) {
                sxy += (xs[i] - mx) * (ys[i] - my)
                sxx += (xs[i] - mx) * (xs[i] - mx)
            }
            val k = if (abs(sxx) < 1e-9f) 0f else sxy / sxx
            return Perspective(my - k * mx, k)
        }
    }
}

/** "≈ 0,5 m", "≈ 1 m" (con la coma de la región). */
fun guideLabel(meters: Float, locale: Locale): String {
    val n = if (meters % 1f == 0f) String.format(locale, "%.0f", meters) else String.format(locale, "%.1f", meters)
    return "≈ $n m"
}
