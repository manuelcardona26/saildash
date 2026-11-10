package com.irvingguerra.litedash

import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.irvingguerra.litedash.core.vehicle.Signal
import kotlinx.coroutines.delay
import kotlin.math.atan2
import kotlin.math.roundToInt

/*
 * SailDash 1.1 · la carretera en perspectiva del diseño C, dibujada en un Canvas: lo que no se mueve
 * (asfalto, carril, orillas, los desvanecidos) se pinta UNA vez en dos imágenes por tamaño, y en cada
 * cuadro solo van las rayas y los reflectores (unas 50 figuras chicas). La proyección está precalculada
 * en [RoadProjection] (RoadMath.kt).
 *
 * Rendimiento: la fase avanza con la velocidad del bus a lo más a [ROAD_FPS] cuadros por segundo, solo
 * mientras se ve y el carro anda; parado o tapado no corre nada. La fase se lee solo al dibujar: nada se
 * recompone.
 */

/** Una línea a lo largo del plano: de [from] a [to] px de la orilla izquierda. */
@Immutable
internal class RoadStripe(val from: Float, val to: Float)

/** Un degradado a lo ancho de una franja del plano: (posición 0–1 de la franja, color). */
internal typealias Across = List<Pair<Float, Color>>

/**
 * Cómo se ve una carretera (todas las medidas en dp del lienzo de 1024 × 600 y en px del plano, como
 * el `.plane` del mockup).
 */
@Immutable
internal class RoadSpec(
    val projection: RoadProjection,
    /** La escena (`.scene`): de [top] a [bottom] en el lienzo; lo de arriba se desvanece con [fade]. */
    val top: Float,
    val bottom: Float,
    /** La máscara de arriba de la escena: (px desde [top], opacidad), como el mask-image del CSS. */
    val fade: List<Pair<Float, Float>>,
    /** Hasta dónde llega el plano (px desde el borde de abajo). */
    val far: Float,
    /** Franjas con degradado a lo ancho: (desde, hasta, colores). Van en orden (lo de abajo primero). */
    val bands: List<Pair<RoadStripe, Across>>,
    /** Orillas continuas (de un color). */
    val edges: List<RoadStripe>,
    val edgeColor: Color,
    /** Las rayas del carril: dónde, color y patrón (encendido, periodo, desfase del CSS). */
    val dashes: List<RoadStripe>,
    val dashColor: Color,
    val dashOn: Float = 70f,
    val dashPeriod: Float = 180f,
    val dashOffset: Float = 3200f,
    /** Los reflectores de las orillas. */
    val posts: List<RoadStripe>,
    val postColor: Color,
    val postOn: Float = 16f,
    val postPeriod: Float = 360f,
    val postOffset: Float = 3400f,
    /** `.sidefade`: los lados oscurecidos encima de todo (de [sideTop] a [sideBottom], [sideWidth] de ancho). */
    val sideColor: Color?,
    val sideTop: Float = 150f,
    val sideBottom: Float = 528f,
    val sideWidth: Float = 300f,
    val sideFadeIn: Float = 90f,
)

/** La de la página Carretera, tal cual mock-C/carretera.html. */
internal val CarreteraSpec = RoadSpec(
    projection = CarreteraRoad,
    top = 186f,
    bottom = 528f,
    fade = listOf(0f to 0f, 30f to 0f, 80f to 0.55f, 160f to 1f),
    far = 3000f,
    bands = listOf(
        // .asphalt: todo el ancho, más oscuro al centro.
        RoadStripe(0f, 1000f) to listOf(0f to Color(9, 14, 25, 0), 0.12f to Color(11, 17, 30).copy(alpha = 0.9f), 0.88f to Color(11, 17, 30).copy(alpha = 0.9f), 1f to Color(9, 14, 25, 0)),
        // .lanefill: el carril de en medio, un azul tenue.
        RoadStripe(300f, 700f) to listOf(0f to Color(110, 185, 255, 0), 0.12f to Color(110, 185, 255).copy(alpha = 0.10f), 0.5f to Color(110, 185, 255).copy(alpha = 0.04f), 0.88f to Color(110, 185, 255).copy(alpha = 0.10f), 1f to Color(110, 185, 255, 0)),
        // .glowline: el brillo a los lados de las rayas.
        RoadStripe(270f, 330f) to GLOW,
        RoadStripe(670f, 730f) to GLOW,
    ),
    edges = listOf(RoadStripe(100f, 104f), RoadStripe(896f, 900f)),
    edgeColor = Color(200, 232, 255).copy(alpha = 0.55f),
    dashes = listOf(RoadStripe(295f, 304f), RoadStripe(696f, 705f)),
    dashColor = Color(240, 248, 255).copy(alpha = 0.95f),
    posts = listOf(RoadStripe(60f, 72f), RoadStripe(928f, 940f)),
    postColor = Color(170, 222, 255).copy(alpha = 0.7f),
    sideColor = Color(4, 7, 14),
)

private val GLOW: Across get() = listOf(0f to Color(120, 195, 255, 0), 0.5f to Color(140, 205, 255).copy(alpha = 0.35f), 1f to Color(120, 195, 255, 0))

/**
 * La fase de la carretera (px del plano, 0 ≤ fase < [ROAD_PHASE_PERIOD]) que avanza con la velocidad del
 * bus mientras [visible] y el carro anda, a lo más a [ROAD_FPS] cuadros por segundo. Se lee al dibujar.
 */
@Composable
internal fun rememberRoadPhase(d: Dependencies, visible: Boolean, demoKmh: Float? = null): MutableFloatState {
    val phase = remember { mutableFloatStateOf(0f) }
    val moving by remember(demoKmh) { derivedStateOf { roadAnimating(demoKmh ?: d.bus.value(Signal.SPEED), true) } }
    LaunchedEffect(visible && moving, demoKmh) {
        if (!(visible && moving)) return@LaunchedEffect
        val frameMs = 1000L / ROAD_FPS
        var last = withFrameNanos { it }
        while (true) {
            delay(frameMs)
            val now = withFrameNanos { it }
            phase.floatValue = roadPhase(phase.floatValue, demoKmh ?: d.bus.value(Signal.SPEED) ?: 0f, (now - last) / 1e9f)
            last = now
        }
    }
    return phase
}

/**
 * La carretera de [spec] sobre todo el lienzo (1024 × 600 dp). En su propia capa: al avanzar la fase solo
 * se vuelve a pintar esto.
 */
@Composable
internal fun RoadScene(spec: RoadSpec, phase: () -> Float, modifier: Modifier = Modifier) {
    Spacer(
        modifier
            .graphicsLayer()
            .drawWithCache {
                val k = size.width / 1024f
                val base = sceneImage(spec, k, density)
                val overlay = spec.sideColor?.let { sideImage(spec, it, k, density) }
                val path = Path()
                val spans = FloatArray(64)
                onDrawBehind {
                    drawImage(base, Offset(0f, (spec.top * k).roundToInt().toFloat()))
                    val p = phase()
                    for (s in spec.posts) drawMarks(spec, s, p, spec.postPeriod, spec.postOn, spec.postOffset, spec.postColor, k, path, spans)
                    for (s in spec.dashes) drawMarks(spec, s, p, spec.dashPeriod, spec.dashOn, spec.dashOffset, spec.dashColor, k, path, spans)
                    if (overlay != null) drawImage(overlay, Offset(0f, (spec.sideTop * k).roundToInt().toFloat()))
                }
            },
    )
}

/** Las rayas (o reflectores) de la franja [s] con la fase [p]: cada tramo encendido, ya en perspectiva. */
private fun DrawScope.drawMarks(spec: RoadSpec, s: RoadStripe, p: Float, period: Float, on: Float, offset: Float, color: Color, k: Float, path: Path, spans: FloatArray) {
    val pr = spec.projection
    val n = roadSpans(p, period, on, offset, spec.far, spans)
    var i = 0
    while (i < n) {
        val v0 = spans[i]
        val v1 = spans[i + 1]
        i += 2
        val y0 = pr.y(v0)
        val y1 = pr.y(v1)
        val alpha = fadeAt(spec, (y0 + y1) / 2f - spec.top)
        if (alpha <= 0.01f) continue
        path.reset()
        path.moveTo(pr.x(s.from, v0) * k, y0 * k)
        path.lineTo(pr.x(s.to, v0) * k, y0 * k)
        path.lineTo(pr.x(s.to, v1) * k, y1 * k)
        path.lineTo(pr.x(s.from, v1) * k, y1 * k)
        path.close()
        drawPath(path, color, alpha = alpha)
    }
}

/** La opacidad de la máscara de arriba a [y] px de lo alto de la escena. */
private fun fadeAt(spec: RoadSpec, y: Float): Float {
    val stops = spec.fade
    if (y <= stops.first().first) return stops.first().second
    for (i in 1 until stops.size) {
        val (y1, a1) = stops[i]
        if (y <= y1) {
            val (y0, a0) = stops[i - 1]
            return a0 + (a1 - a0) * (y - y0) / (y1 - y0)
        }
    }
    return stops.last().second
}

/** Pinta en una imagen nueva de [w] × [h] px. */
private fun image(w: Int, h: Int, density: Float, block: DrawScope.() -> Unit): ImageBitmap {
    val bmp = ImageBitmap(w.coerceAtLeast(1), h.coerceAtLeast(1))
    CanvasDrawScope().draw(Density(density), LayoutDirection.Ltr, Canvas(bmp), Size(w.toFloat(), h.toFloat()), block)
    return bmp
}

/**
 * Lo quieto de la escena: cada franja como un trapecio que va del borde de abajo a lo lejos, con su
 * degradado a lo ancho. Las líneas de igual u se juntan en un punto (el de fuga del plano): un degradado
 * de barrido centrado ahí, con cada parada en el ángulo de su u, es exactamente el degradado del CSS.
 */
private fun sceneImage(spec: RoadSpec, k: Float, density: Float): ImageBitmap {
    val pr = spec.projection
    val top = spec.top
    val w = (1024f * k).roundToInt()
    val h = ((spec.bottom - top) * k).roundToInt()
    return image(w, h, density) {
        val path = Path()
        val far = spec.far
        // A dónde llegan todas las líneas de igual u: la x de fuga y el horizonte.
        val hub = Offset(pr.vanishX * k, (pr.horizon - top) * k)
        fun trapezoid(from: Float, to: Float) {
            path.reset()
            path.moveTo(pr.x(from, 0f) * k, (pr.y(0f) - top) * k)
            path.lineTo(pr.x(to, 0f) * k, (pr.y(0f) - top) * k)
            path.lineTo(pr.x(to, far) * k, (pr.y(far) - top) * k)
            path.lineTo(pr.x(from, far) * k, (pr.y(far) - top) * k)
            path.close()
        }
        for ((s, colors) in spec.bands) {
            // El ángulo (0–1 de la vuelta, como el barrido) de cada parada, de menor a mayor.
            val stops = colors.map { (f, c) ->
                val u = s.from + (s.to - s.from) * f
                val a = atan2((pr.y(0f) - top) * k - hub.y, pr.x(u, 0f) * k - hub.x)
                (((Math.toDegrees(a.toDouble()) + 360.0) % 360.0) / 360.0).toFloat() to c
            }.sortedBy { it.first }
            trapezoid(s.from, s.to)
            drawPath(path, Brush.sweepGradient(*stops.toTypedArray(), center = hub))
        }
        for (s in spec.edges) {
            trapezoid(s.from, s.to)
            drawPath(path, spec.edgeColor)
        }
        // La máscara de arriba (mask-image): lo de lejos se pierde en el cielo.
        val fade = spec.fade.map { (y, a) -> (y / (spec.bottom - top)).coerceIn(0f, 1f) to Color.Black.copy(alpha = a) }
        drawRect(Brush.verticalGradient(*fade.toTypedArray(), startY = 0f, endY = h.toFloat()), blendMode = BlendMode.DstIn)
    }
}

/** `.sidefade`: los dos lados oscurecidos (degradado de lado y su máscara de arriba), una vez por tamaño. */
private fun sideImage(spec: RoadSpec, color: Color, k: Float, density: Float): ImageBitmap {
    val w = (1024f * k).roundToInt()
    val h = ((spec.sideBottom - spec.sideTop) * k).roundToInt()
    return image(w, h, density) {
        val sw = spec.sideWidth * k
        val stops = arrayOf(0f to color.copy(alpha = 0.92f), 0.5f to color.copy(alpha = 0.5f), 1f to color.copy(alpha = 0f))
        drawRect(Brush.horizontalGradient(*stops, startX = 0f, endX = sw), Offset.Zero, Size(sw, h.toFloat()))
        drawRect(Brush.horizontalGradient(*stops, startX = w.toFloat(), endX = w - sw), Offset(w - sw, 0f), Size(sw, h.toFloat()))
        drawRect(
            Brush.verticalGradient(0f to Color.Transparent, 1f to Color.Black, startY = 0f, endY = spec.sideFadeIn * k),
            blendMode = BlendMode.DstIn,
        )
    }
}
