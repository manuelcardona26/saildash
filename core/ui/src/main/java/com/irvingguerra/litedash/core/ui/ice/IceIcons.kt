package com.irvingguerra.litedash.core.ui.ice

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * Los íconos de los mockups C, copiados de sus SVG: cuadrícula de 24, trazo de 1.7 con puntas y
 * uniones redondas (`.ico` de common.css). Se pintan con el color de quien los usa
 * ([IceIcon] o `Icon(…, color)` de core.ui): el negro de aquí es solo la máscara.
 */
object IceIcons {
    // ---- Dock ----
    /** Inicio: cuatro bloques de distinto alto. */
    val home = icon(rect(3.5f, 3.5f, 7f, 9f, 1.6f), rect(13.5f, 3.5f, 7f, 5f, 1.6f), rect(13.5f, 11.5f, 7f, 9f, 1.6f), rect(3.5f, 15.5f, 7f, 5f, 1.6f))
    /** Apps: nueve puntos. */
    val apps = icon(*listOf(5.5f, 12f, 18.5f).flatMap { y -> listOf(5.5f, 12f, 18.5f).map { x -> circle(x, y, 1.4f) } }.toTypedArray())
    val phone = icon("M6.2 3.8h2.6l1.6 4.1-2 1.3a10.6 10.6 0 0 0 6.4 6.4l1.3-2 4.1 1.6v2.6a2 2 0 0 1-2.1 2A16.2 16.2 0 0 1 4.2 5.9a2 2 0 0 1 2-2.1z")
    /** CarPlay: la pantalla con el triángulo de play. */
    val carPlay = icon(rect(3f, 4f, 18f, 12.5f, 2.2f), "M8.5 20.5h7M12 16.5v4", "M10.3 7.8v5l4.2-2.5z")
    val whatsApp = icon(
        "M20.2 11.6a8.4 8.4 0 0 1-12.4 7.4L3.6 20.4l1.4-4a8.4 8.4 0 1 1 15.2-4.8z",
        "M9 8.6c.2 2.9 3.2 6 6.2 6.3l1-1.5-1.8-.9-.8.8a4.6 4.6 0 0 1-2.3-2.3l.8-.8-.9-1.8z",
    )
    val radio = icon(rect(3f, 8.5f, 18f, 12f, 2.2f), "M7 8.5l10-4.5", circle(15.5f, 14.5f, 2.6f), "M6.5 12.5h3.5M6.5 16h3.5")
    val spotify = icon(circle(12f, 12f, 9f), "M7.3 9.4c3.2-1 6.7-.7 9.6.9M7.9 12.4c2.6-.7 5.3-.5 7.6.8M8.5 15.3c2-.5 4-.3 5.7.6")
    val settings = icon(
        circle(12f, 12f, 3f),
        "M19.4 15a1.6 1.6 0 0 0 .3 1.8l.1.1a2 2 0 1 1-2.8 2.8l-.1-.1a1.6 1.6 0 0 0-1.8-.3 1.6 1.6 0 0 0-1 1.5v.2a2 2 0 1 1-4 0v-.1a1.6 1.6 0 0 0-1-1.5 1.6 1.6 0 0 0-1.8.3l-.1.1a2 2 0 1 1-2.8-2.8l.1-.1a1.6 1.6 0 0 0 .3-1.8 1.6 1.6 0 0 0-1.5-1H3a2 2 0 1 1 0-4h.1a1.6 1.6 0 0 0 1.5-1 1.6 1.6 0 0 0-.3-1.8l-.1-.1a2 2 0 1 1 2.8-2.8l.1.1a1.6 1.6 0 0 0 1.8.3H9a1.6 1.6 0 0 0 1-1.5V3a2 2 0 1 1 4 0v.1a1.6 1.6 0 0 0 1 1.5 1.6 1.6 0 0 0 1.8-.3l.1-.1a2 2 0 1 1 2.8 2.8l-.1.1a1.6 1.6 0 0 0-.3 1.8V9a1.6 1.6 0 0 0 1.5 1h.2a2 2 0 1 1 0 4h-.1a1.6 1.6 0 0 0-1.5 1z",
    )

    // ---- Inicio ----
    /** Contraste (día/noche): círculo con la mitad rellena. */
    val contrast = icon(circle(12f, 12f, 8.5f), fills = listOf("M12 3.5v17a8.5 8.5 0 0 0 0-17z"))
    val house = icon("M3.5 11 12 4l8.5 7", "M5.5 9.5V20h13V9.5", "M10 20v-5.5h4V20")
    val briefcase = icon(rect(3f, 7f, 18f, 13f, 2.2f), "M9 7V5.2A1.2 1.2 0 0 1 10.2 4h3.6A1.2 1.2 0 0 1 15 5.2V7", "M3 12.5h18")
    val star = icon("M12 3.6l2.6 5.3 5.8.8-4.2 4.1 1 5.8L12 16.9l-5.2 2.7 1-5.8-4.2-4.1 5.8-.8z")
    val search = icon(circle(10.8f, 10.8f, 6.3f), "M15.5 15.5 20.5 20.5")
    /** Placa (pico y placa). */
    val plate = icon(rect(2.5f, 6.5f, 19f, 11f, 2f), "M6 12h4.5M13.5 12H18")
    val wifi = icon("M2.5 9a14 14 0 0 1 19 0", "M5.6 12.3a9.5 9.5 0 0 1 12.8 0", "M8.8 15.6a5 5 0 0 1 6.4 0", circle(12f, 19f, 0.9f))
    /** Wi-Fi apagado: el abanico tachado. */
    val wifiOff = icon("M2.5 9a14 14 0 0 1 19 0", "M5.6 12.3a9.5 9.5 0 0 1 12.8 0", "M8.8 15.6a5 5 0 0 1 6.4 0", circle(12f, 19f, 0.9f), "M4 4l16 16")
    val bluetooth = icon("M7 7.5l10 9-5 4.5V3l5 4.5-10 9")
    val thermometer = icon("M10 14.2V5a2 2 0 1 1 4 0v9.2a4 4 0 1 1-4 0z", "M12 9.5v7")
    val battery = icon(rect(3f, 7.5f, 18f, 12f, 2f), "M6.5 7.5V5.5h3v2M14.5 7.5V5.5h3v2", "M6.5 13.5h3.5M14 13.5h3.5M15.75 11.75v3.5")
    val fuel = icon(
        "M4.5 20.5V5.5a1.5 1.5 0 0 1 1.5-1.5h7a1.5 1.5 0 0 1 1.5 1.5v15",
        "M3.5 20.5h12", "M7 8h5",
        "M14.5 11h1.8a1.7 1.7 0 0 1 1.7 1.7v3.6a1.5 1.5 0 0 0 3 0V8.4L18.4 5.8",
    )
    /** Distancia del viaje: la carretera en perspectiva. */
    val route = icon("M8.5 3.5 4 20.5", "M15.5 3.5l4.5 17", "M12 4.5v2.5M12 10.5v3M12 17v3")
    /** Velocidad promedio: el arco del velocímetro con su aguja. */
    val gauge = icon("M4.6 17a8.4 8.4 0 1 1 14.8 0", "M12 13.6l3.6-4.2", circle(12f, 14f, 1f))
    /** El conector del OBD (para "Conecta OBD"). */
    val plug = icon("M9 3.5v4M15 3.5v4", "M6.5 7.5h11v3.2a5.5 5.5 0 0 1-11 0z", "M12 16.2v4.3")
    val location = icon("M12 20.5s-6.2-5.6-6.2-10.6a6.2 6.2 0 0 1 12.4 0c0 5-6.2 10.6-6.2 10.6z", circle(12f, 9.9f, 2.2f))
    val music = icon("M9 17.5V5.5l10-2v12", circle(6.5f, 17.5f, 2.5f), circle(16.5f, 15.5f, 2.5f))

    // ---- Clima (en la tarjeta van a trazo 1.5) ----
    val rain = icon("M7 15.5a4.3 4.3 0 0 1-.6-8.6 5.8 5.8 0 0 1 11 1.6 3.5 3.5 0 0 1 .4 7z", "M8.5 18.5l-.9 2M12.5 18.5l-.9 2M16.5 18.5l-.9 2", stroke = 1.5f)
    val cloud = icon("M7 18a4.3 4.3 0 0 1-.6-8.6 5.8 5.8 0 0 1 11 1.6 3.5 3.5 0 0 1 .4 7z", stroke = 1.5f)
    val sun = icon(
        circle(12f, 12f, 4f),
        "M12 3v1.8M12 19.2V21M3 12h1.8M19.2 12H21M5.6 5.6l1.3 1.3M17.1 17.1l1.3 1.3M5.6 18.4l1.3-1.3M17.1 6.9l1.3-1.3",
        stroke = 1.5f,
    )
    val moon = icon("M19.5 14.6A8 8 0 1 1 9.4 4.5a6.4 6.4 0 0 0 10.1 10.1z", stroke = 1.5f)
    val partly = icon(
        "M8.5 3.8v1.2M3.6 8.7h1.2M5 5.2l.85.85M12 5.2l-.85.85",
        "M5.4 11.6a3.6 3.6 0 0 1 6.4-3.2",
        "M9 20a3.6 3.6 0 0 1-.5-7.2 4.9 4.9 0 0 1 9.3 1.4 2.95 2.95 0 0 1 .3 5.8z",
        stroke = 1.5f,
    )
    val storm = icon("M7 15.5a4.3 4.3 0 0 1-.6-8.6 5.8 5.8 0 0 1 11 1.6 3.5 3.5 0 0 1 .4 7z", "M12.6 13.5l-2 3.6h3l-2 3.6", stroke = 1.5f)
    val fog = icon("M4 9.5h16M3 13h18M5 16.5h14M8 20h8", stroke = 1.5f)
    val snow = icon("M7 15.5a4.3 4.3 0 0 1-.6-8.6 5.8 5.8 0 0 1 11 1.6 3.5 3.5 0 0 1 .4 7z", circle(8.5f, 19.5f, 0.5f), circle(12f, 20.5f, 0.5f), circle(15.5f, 19.5f, 0.5f), stroke = 1.5f)

    // ---- Música ----
    val previous = icon("M6.5 5.5v13", both = listOf("M18 6.3v11.4a.6.6 0 0 1-.9.5L9.4 12.5a.6.6 0 0 1 0-1l7.7-5.7a.6.6 0 0 1 .9.5z"))
    val next = icon("M17.5 5.5v13", both = listOf("M6 6.3v11.4a.6.6 0 0 0 .9.5l7.7-5.7a.6.6 0 0 0 0-1L6.9 5.8a.6.6 0 0 0-.9.5z"))
    val play = icon(both = listOf("M8 5.6v12.8a.8.8 0 0 0 1.2.7l10.2-6.4a.8.8 0 0 0 0-1.4L9.2 4.9A.8.8 0 0 0 8 5.6z"))
    val pause = icon(both = listOf(rect(6.5f, 5f, 3.6f, 14f, 1f), rect(13.9f, 5f, 3.6f, 14f, 1f)))

    // ---- Navegación entre páginas (trazo 2) ----
    val chevronLeft = icon("M14.5 6 8.5 12l6 6", stroke = 2f)
    val chevronRight = icon("M9.5 6l6 6-6 6", stroke = 2f)

    /** Un círculo de SVG como trazo de ruta (`<circle cx cy r>`). */
    fun circle(cx: Float, cy: Float, r: Float) = "M${cx - r} ${cy}a$r $r 0 1 0 ${2 * r} 0a$r $r 0 1 0 ${-2 * r} 0z"

    /** Un `<rect x y width height rx>` de SVG como ruta. */
    fun rect(x: Float, y: Float, w: Float, h: Float, r: Float): String {
        val iw = w - 2 * r
        val ih = h - 2 * r
        return "M${x + r} ${y}h${iw}a$r $r 0 0 1 $r ${r}v${ih}a$r $r 0 0 1 ${-r} ${r}h${-iw}a$r $r 0 0 1 ${-r} ${-r}v${-ih}a$r $r 0 0 1 $r ${-r}z"
    }

    /**
     * Arma un ícono: [strokes] solo trazo, [fills] solo relleno y [both] trazo y relleno (como los
     * triángulos de play del diseño, que llevan `fill="currentColor"` además del trazo).
     */
    fun icon(vararg strokes: String, fills: List<String> = emptyList(), both: List<String> = emptyList(), stroke: Float = 1.7f): ImageVector {
        val b = ImageVector.Builder(defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
        val black = SolidColor(Color.Black)
        for (t in strokes) b.addPath(addPathNodes(t), stroke = black, strokeLineWidth = stroke, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round)
        for (t in both) b.addPath(addPathNodes(t), fill = black, stroke = black, strokeLineWidth = stroke, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round)
        for (t in fills) b.addPath(addPathNodes(t), fill = black)
        return b.build()
    }
}
