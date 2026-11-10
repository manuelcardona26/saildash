package com.irvingguerra.litedash.core.ui.ice

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp

/**
 * El lienzo del diseño: adentro todo mide como en los mockups (1024 × 600 dp). En la pantalla del
 * Sail (1024 × 600 a 160 dpi) es 1:1; si hay menos lugar (barra de Android visible) o más, se escala
 * parejo y se centra, con el fondo [IcePalette.bg] alrededor. Así las pantallas nuevas se escriben con
 * las medidas exactas del HTML (left/top en dp) sin pensar en el tamaño real.
 */
@Composable
fun IceFrame(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val ice = LocalIce.current
    BoxWithConstraints(modifier.fillMaxSize().background(ice.bg), contentAlignment = Alignment.Center) {
        val s = minOf(maxWidth.value / IceDims.FRAME_W, maxHeight.value / IceDims.FRAME_H)
        val d = LocalDensity.current
        CompositionLocalProvider(LocalDensity provides Density(d.density * s, d.fontScale)) {
            Box(Modifier.size(IceDims.FRAME_W.dp, IceDims.FRAME_H.dp).clipToBounds(), content = content)
        }
    }
}

/**
 * Fondo de página: una sola imagen ([image], un drawable de 1024 × 600) a todo el lienzo y, con
 * [vignette], el oscurecido de `.space::after` (más oscuro abajo, donde va el dock). En el tema claro
 * no se usa la imagen: va el papel hielo con un degradado suave.
 */
@Composable
fun IceBackdrop(image: Int, modifier: Modifier = Modifier, vignette: Boolean = true) {
    val ice = LocalIce.current
    if (!ice.dark) {
        Box(modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFFF1F6FC), Color(0xFFDCE6F2)))))
        return
    }
    Box(modifier.fillMaxSize()) {
        Image(painterResource(image), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        if (vignette) Box(Modifier.fillMaxSize().spaceVignette())
    }
}

/** El oscurecido de common.css: radial al 30 % / 55 % y lineal de arriba a abajo. */
fun Modifier.spaceVignette(): Modifier = drawWithCache {
    val bg = Color(4, 7, 14)
    // radial-gradient(120% 90% at 30% 55%, …): un óvalo con radios de 1.2 × ancho y 0.9 × alto.
    val c = Offset(size.width * 0.30f, size.height * 0.55f)
    val rx = size.width * 1.2f
    val ry = size.height * 0.9f
    val radial = Brush.radialGradient(
        0f to bg.copy(alpha = 0f), 0.7f to bg.copy(alpha = 0.35f), 1f to bg.copy(alpha = 0.7f),
        center = c, radius = rx,
    )
    val linear = Brush.verticalGradient(
        0f to bg.copy(alpha = 0.15f), 0.30f to bg.copy(alpha = 0f), 0.88f to bg.copy(alpha = 0.55f), 1f to bg.copy(alpha = 0.55f),
    )
    onDrawBehind {
        scale(1f, ry / rx, pivot = c) { drawRect(radial, Offset(-size.width, -size.height * 3), androidx.compose.ui.geometry.Size(size.width * 3, size.height * 7)) }
        drawRect(linear)
    }
}
