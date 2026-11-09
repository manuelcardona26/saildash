package com.irvingguerra.litedash

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import kotlin.math.min
import kotlin.random.Random

/*
 * SailDash: tema Interestelar. Un fondo de espacio profundo con estrellas y un agujero negro con
 * su disco de acreción dorado. Arte propio (dibujado con formas), sin imágenes de ninguna película.
 *
 * Rendimiento: es estático. Va en su propia capa (graphicsLayer), así que Android lo dibuja una
 * vez y lo reutiliza; solo se vuelve a calcular si cambia el tamaño de la pantalla. No anima nada.
 */

private val SpaceTop = Color(0xFF070B16)
private val SpaceBottom = Color(0xFF020308)
private val Gold = Color(0xFFF7B955)
private val HotGold = Color(0xFFFFE3A6)
private val Ember = Color(0xFFE0782E)

/** Una estrella: posición en fracción de la pantalla, tamaño, brillo y color. */
private class Star(val x: Float, val y: Float, val r: Float, val color: Color)

/** Siempre las mismas estrellas (semilla fija): el cielo no "salta" al volver al inicio. */
private val Stars: List<Star> = Random(1977).let { rnd ->
    List(230) {
        val tint = when (rnd.nextInt(10)) {
            0 -> Color(0xFFFFD9A0)   // cálida
            1, 2 -> Color(0xFFB8D4FF) // azulada
            else -> Color.White
        }
        val big = rnd.nextInt(14) == 0
        Star(rnd.nextFloat(), rnd.nextFloat(), if (big) 1.3f + rnd.nextFloat() else 0.45f + rnd.nextFloat() * 0.7f, tint.copy(alpha = 0.25f + rnd.nextFloat() * 0.65f))
    }
}

@Composable
fun SpaceBackdrop(modifier: Modifier = Modifier) {
    Box(
        modifier
            .graphicsLayer()
            .drawWithCache {
                val w = size.width
                val h = size.height
                val base = Brush.verticalGradient(listOf(SpaceTop, SpaceBottom))
                // Dos nubes de gas muy tenues para que el negro no se vea plano.
                val nebulaBlue = Brush.radialGradient(listOf(Color(0x2A2B4C80), Color.Transparent), Offset(w * 0.18f, h * 0.78f), w * 0.45f)
                val nebulaViolet = Brush.radialGradient(listOf(Color(0x1C4A2766), Color.Transparent), Offset(w * 0.55f, h * 0.15f), w * 0.35f)

                // El agujero negro: a la derecha, a media altura (arriba van los avisos y el auto va al centro).
                val r = min(w, h) * 0.07f
                val center = Offset(w * 0.885f, h * 0.55f)
                val glow = Brush.radialGradient(
                    0f to Gold.copy(alpha = 0.20f), 0.45f to Ember.copy(alpha = 0.07f), 1f to Color.Transparent,
                    center = center, radius = r * 4.2f,
                )
                // El anillo que se curva por encima y por debajo (la luz del disco doblada por la gravedad).
                val halo = Brush.radialGradient(
                    0f to Color.Transparent, 0.60f to Color.Transparent,
                    0.70f to HotGold.copy(alpha = 0.85f), 0.80f to Gold.copy(alpha = 0.55f),
                    0.92f to Ember.copy(alpha = 0.18f), 1f to Color.Transparent,
                    center = center, radius = r * 1.75f,
                )
                val diskSize = Size(r * 5.6f, r * 1.0f)
                val diskTopLeft = Offset(center.x - diskSize.width / 2, center.y - diskSize.height / 2)
                val disk = Brush.horizontalGradient(
                    0f to Color.Transparent, 0.18f to Ember.copy(alpha = 0.55f), 0.38f to Gold,
                    0.5f to HotGold, 0.62f to Gold, 0.82f to Ember.copy(alpha = 0.55f), 1f to Color.Transparent,
                    startX = diskTopLeft.x, endX = diskTopLeft.x + diskSize.width,
                )
                val diskStroke = Stroke(width = r * 0.26f)
                val photonStroke = Stroke(width = (r * 0.035f).coerceAtLeast(1.2f))

                onDrawBehind {
                    drawRect(base)
                    drawRect(nebulaBlue)
                    drawRect(nebulaViolet)
                    for (s in Stars) drawCircle(s.color, s.r * density, Offset(s.x * w, s.y * h))

                    drawCircle(glow, r * 4.2f, center)
                    // Parte de atrás del disco, detrás de la sombra.
                    drawOval(disk, diskTopLeft, diskSize, style = diskStroke)
                    drawCircle(halo, r * 1.75f, center)
                    // La sombra (horizonte de eventos) y su anillo de fotones.
                    drawCircle(Color.Black, r * 1.04f, center)
                    drawCircle(HotGold.copy(alpha = 0.9f), r * 1.07f, center, style = photonStroke)
                    // Parte de adelante del disco: pasa por encima de la mitad de abajo de la sombra.
                    clipRect(top = center.y, bottom = center.y + diskSize.height) {
                        drawOval(disk, diskTopLeft, diskSize, style = diskStroke)
                    }
                }
            },
    )
}
