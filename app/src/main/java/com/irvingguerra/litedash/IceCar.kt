package com.irvingguerra.litedash

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.irvingguerra.litedash.core.ui.ice.LocalIce
import com.irvingguerra.litedash.feature.car.CarDrawing

/*
 * Tu Sail (la foto de OwnerCar) en las pantallas del catálogo que lo ponen a un lado (Música,
 * Bienvenida): la sombra del piso (.ground), la foto (.car) y la luz de sus faros (.bloom), con las
 * medidas de cada mockup. Sin foto, el dibujo de LiteDash del color del carro.
 */

/** El carro con su ancho [width] y su esquina en ([left], [top]); [ground] = la sombra (left, top, ancho, alto). */
@Composable
internal fun OwnerCarPicture(d: Dependencies, left: Dp, top: Dp, width: Dp, ground: List<Dp>? = null, stage: List<Dp>? = null) {
    val context = LocalContext.current
    val ice = LocalIce.current
    val owner = remember { OwnerCar.of(context) }
    LaunchedEffect(owner) { owner.load() }
    if (ground != null && ice.dark) Box(
        Modifier.offset(ground[0], ground[1]).size(ground[2], ground[3]).drawWithCache {
            val c = Offset(size.width / 2, size.height / 2)
            val brush = Brush.radialGradient(
                0f to Color(1, 3, 7).copy(alpha = 0.8f), 0.55f to Color(1, 3, 7).copy(alpha = 0.45f), 1f to Color(1, 3, 7).copy(alpha = 0f),
                center = c, radius = size.width / 2,
            )
            onDrawBehind { scale(1f, size.height / size.width, pivot = c) { drawCircle(brush, size.width / 2, c) } }
        },
    )
    if (stage != null) Box(
        Modifier.offset(stage[0], stage[1]).size(stage[2], stage[3]).drawWithCache {
            val a = if (ice.dark) Color(0xFFA9DCFF) else ice.ice
            val b = if (ice.dark) Color(0xFFD9F0FF) else ice.ice2
            val brush = Brush.horizontalGradient(0f to a.copy(alpha = 0f), 0.5f to b.copy(alpha = 0.55f), 1f to a.copy(alpha = 0f))
            val k = size.width / 360f
            onDrawBehind {
                drawOval(brush, Offset(8f * k, 16f * k), Size(344f * k, 68f * k), style = Stroke(1.1.dp.toPx()))
            }
        },
    )
    val image = owner.image
    if (image != null) {
        Image(
            image.bitmap.asImageBitmap(), stringResource(R.string.my_car_fallback),
            Modifier.offset(left, top).size(width, width * image.ratio),
            contentScale = ContentScale.FillBounds,
        )
        if (ice.dark) for ((i, h) in image.headlights.withIndex()) {
            val w = width.value
            val r = h.glow * w
            val box = (r * 1.9f).coerceIn(30f, 160f)
            val cx = left.value + h.x * w
            val cy = top.value + h.y * w
            Box(
                Modifier
                    .offset((cx - box / 2).dp, (cy - box / 2).dp)
                    .size(box.dp)
                    .drawWithCache {
                        val brush = Brush.radialGradient(
                            0f to Color(255, 244, 222).copy(alpha = 0.42f), 0.35f to Color(255, 236, 205).copy(alpha = 0.16f), 0.70f to Color(255, 236, 205).copy(alpha = 0f),
                            center = Offset(size.width / 2f, size.height / 2f), radius = size.width * 0.7071f,
                        )
                        val alpha = if (i > 0 && r > 50f) 0.75f else 1f
                        onDrawBehind { drawRect(brush, alpha = alpha) }
                    },
            )
        }
    } else if (owner.loaded) {
        CarDrawing(d.prefs.carColor, Modifier.offset(left, top + width * 0.12f).size(width, width * 0.5f))
    }
}
