package com.irvingguerra.litedash.core.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect

/**
 * Proporción (ancho / alto) de la marca: una caja ancha y baja (como el disco de un agujero
 * negro visto de lado). Se conserva el nombre de LiteDash para no tocar a quien la usa.
 */
const val MORSE_ASPECT = 184f / 36f

/**
 * SailDash: la marca es un agujero negro con su disco dorado, dibujado sin imágenes. Reemplaza
 * la marca —·· de LiteDash (su nombre y su logo no están cubiertos por la licencia del código).
 * [color] es el anillo de luz alrededor de la sombra; [light], el dorado del disco.
 */
@Composable
fun MorseMark(modifier: Modifier = Modifier, color: Color = LocalColors.current.text, light: Color = Brand.Amber) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val center = Offset(w / 2f, h / 2f)
        val r = h * 0.34f
        val diskSize = Size(w * 0.96f, h * 0.40f)
        val diskTopLeft = Offset(center.x - diskSize.width / 2f, center.y - diskSize.height / 2f)
        val disk = Brush.horizontalGradient(
            0f to light.copy(alpha = 0f), 0.25f to light.copy(alpha = 0.75f), 0.5f to light,
            0.75f to light.copy(alpha = 0.75f), 1f to light.copy(alpha = 0f),
            startX = diskTopLeft.x, endX = diskTopLeft.x + diskSize.width,
        )
        val diskStroke = Stroke(width = h * 0.11f)
        // Atrás del disco, el halo, la sombra con su anillo de luz y, adelante, la mitad de abajo del disco.
        drawOval(disk, diskTopLeft, diskSize, style = diskStroke)
        drawCircle(light.copy(alpha = 0.55f), r * 1.32f, center, style = Stroke(width = r * 0.28f))
        drawCircle(Color.Black, r, center)
        drawCircle(color.copy(alpha = 0.9f), r * 1.04f, center, style = Stroke(width = (r * 0.08f).coerceAtLeast(1f)))
        clipRect(top = center.y, bottom = h) { drawOval(disk, diskTopLeft, diskSize, style = diskStroke) }
    }
}
