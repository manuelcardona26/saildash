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

    /** La estrella de la emisora guardada (trazo y un relleno tenue, como `fill: rgba(…, .35)`). */
    val starFilled = ImageVector.Builder(defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f).addPath(
        addPathNodes("M12 3.6l2.6 5.3 5.8.8-4.2 4.1 1 5.8L12 16.9l-5.2 2.7 1-5.8-4.2-4.1 5.8-.8z"),
        fill = SolidColor(Color.Black), fillAlpha = 0.35f, stroke = SolidColor(Color.Black), strokeLineWidth = 1.7f,
        strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
    ).build()
    val pauseBars = icon("M8.5 5.5v13M15.5 5.5v13", stroke = 3.4f)
    val shuffle = icon("M3.5 7h3.2c4.6 0 6 10 10.6 10h3.2M3.5 17h3.2c1.6 0 2.7-1.2 3.6-2.8M13.7 9.8C14.6 8.2 15.7 7 17.3 7h3.2M18 4.5 20.5 7 18 9.5M18 14.5l2.5 2.5-2.5 2.5")
    val repeat = icon("M4.5 11V9.5a3 3 0 0 1 3-3h11.5", "M16.5 4l2.5 2.5L16.5 9", "M19.5 13v1.5a3 3 0 0 1-3 3H5", "M7.5 20 5 17.5 7.5 15")
    val usb = icon(rect(7f, 9.5f, 10f, 11.5f, 2f), "M9 9.5V3.5h6v6", "M11 6.2h.01M13 6.2h.01")
    val openExternal = icon("M13.5 4.5h6v6", "M19.5 4.5 11 13", "M17.5 14v4a2 2 0 0 1-2 2h-9.5a2 2 0 0 1-2-2V8.5a2 2 0 0 1 2-2h4")
    val close = icon("M6.5 6.5l11 11M17.5 6.5l-11 11")
    val check = icon("M5 12.5l4.2 4.2L19 7", stroke = 2.2f)
    val plus = icon("M12 5v14M5 12h14")
    val sliders = icon("M6 3.8v6.6M6 15v5.2M12 3.8v2.2M12 10.6v9.6M18 3.8v9.6M18 18v2.2", circle(6f, 12.7f, 2.3f), circle(12f, 8.3f, 2.3f), circle(18f, 15.7f, 2.3f))
    val seekBack = icon("M5 5.5v13", both = listOf("M19 7v10l-6.5-5z", "M13 7v10L6.5 12z"))
    val seekForward = icon("M19 5.5v13", both = listOf("M5 7v10l6.5-5z", "M11 7v10l6.5-5z"))
    val searchPlus = icon(circle(10.8f, 10.8f, 6.3f), "M15.5 15.5 20.5 20.5", "M8 10.8h5.6M10.8 8v5.6")
    val refresh = icon("M19.5 12a7.5 7.5 0 1 1-2.2-5.3", "M19.5 4.5v4h-4")
    val smartphone = icon(rect(7f, 2.8f, 10f, 18.4f, 2.6f), "M10.5 5.6h3", fills = listOf(circle(12f, 18f, 0.6f)))
    val pencil = icon("M4.5 19.5l1-4L16 5a2.1 2.1 0 0 1 3 3L8.5 18.5z", "M14 7l3 3")
    val sparkle = icon("M11 3.5l1.7 5 5 1.7-5 1.7L11 17l-1.7-5.1-5-1.7 5-1.7z", "M18.5 15l.7 2 2 .7-2 .7-.7 2-.7-2-2-.7 2-.7z")
    val photo = icon(rect(3.5f, 4.5f, 17f, 15f, 2.2f), circle(9f, 10f, 1.8f), "M20.5 15.5l-5-5-8.5 9")
    val info = icon(circle(12f, 12f, 8.5f), "M12 11v5.5M12 7.8h.01")
    val infoDot = icon(circle(12f, 12f, 8.5f), "M12 11v5.5", fills = listOf(circle(12f, 7.8f, 0.6f)))
    val pin = icon("M12 21s-6.8-6-6.8-11.3a6.8 6.8 0 0 1 13.6 0C18.8 15 12 21 12 21z", circle(12f, 9.7f, 2.4f))
    val trash = icon("M4.5 7h15", "M9.5 7V4.8h5V7", "M6.5 7l1 12.2a1.6 1.6 0 0 0 1.6 1.3h5.8a1.6 1.6 0 0 0 1.6-1.3l1-12.2")
    val folder = icon("M3.5 7.5a2 2 0 0 1 2-2h4l2 2h7a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2h-13a2 2 0 0 1-2-2z")
    val disc = icon(circle(12f, 12f, 8.5f), circle(12f, 12f, 2.4f))
    val user = icon(circle(12f, 8.5f, 3.8f), "M4.8 20a7.2 7.2 0 0 1 14.4 0")
    val chevronSmall = icon("M9.5 6l6 6-6 6")
    val back = icon("M14.5 6 8.5 12l6 6")
    // Ajustes: el menú.
    val car = icon("M5.5 12.5 7.2 8a2 2 0 0 1 1.9-1.3h5.8A2 2 0 0 1 16.8 8l1.7 4.5", rect(3.5f, 12.5f, 17f, 5f, 1.6f), "M6 17.5v2M18 17.5v2", circle(7.4f, 15f, 0.6f), circle(16.6f, 15f, 0.6f))
    val palette = icon(
        "M12 3.5a8.5 8.5 0 1 0 0 17c1.2 0 1.8-.8 1.8-1.7 0-1.3-1-1.6-1-2.6 0-.9.7-1.5 1.6-1.5h2a3.6 3.6 0 0 0 3.6-3.6c0-4.2-3.6-7.6-8-7.6z",
        circle(7.8f, 11.2f, 1f), circle(10.6f, 7.5f, 1f), circle(15.1f, 7.9f, 1f),
    )
    val speaker = icon("M4 9.5h3.5L12 5.5v13l-4.5-4H4z", "M15.5 9a4 4 0 0 1 0 6M18 6.5a7.5 7.5 0 0 1 0 11")
    val wrench = icon("M14.6 6.4a1 1 0 0 0 0 1.4l1.6 1.6a1 1 0 0 0 1.4 0l3.4-3.4a5.6 5.6 0 0 1-7.4 7.4l-6.5 6.5a2 2 0 0 1-2.9-2.9l6.5-6.5a5.6 5.6 0 0 1 7.4-7.4z")
    val steering = icon(circle(12f, 12f, 8.5f), circle(12f, 12f, 2.2f), "M3.7 10.6c2.7-.7 5.4-1 8.3-1s5.6.3 8.3 1", "M10.4 13.8 8 20M13.6 13.8 16 20")
    val modules = icon(rect(3.5f, 3.5f, 7f, 7f, 1.6f), rect(13.5f, 3.5f, 7f, 7f, 1.6f), rect(3.5f, 13.5f, 7f, 7f, 1.6f), "M17 13.5v7M13.5 17h7")
    val chip = icon(
        rect(5.5f, 5.5f, 13f, 13f, 2.5f), rect(9.25f, 9.25f, 5.5f, 5.5f, 1f),
        "M9.5 2.8v2.7M14.5 2.8v2.7M9.5 18.5v2.7M14.5 18.5v2.7M2.8 9.5h2.7M2.8 14.5h2.7M18.5 9.5h2.7M18.5 14.5h2.7",
    )
    val tire = icon(circle(12f, 12f, 8.5f), circle(12f, 12f, 3.5f), "M12 3.5v5M12 15.5v5M3.5 12h5M15.5 12h5")
    val headlights = icon("M11 6.5c-3.5 0-6 2.4-6 5.5s2.5 5.5 6 5.5c1 0 1.5-.4 1.5-1.5V8c0-1.1-.5-1.5-1.5-1.5z", "M15.5 7.5h4M15.5 10.5h4M15.5 13.5h4M15.5 16.5h4")
    val moonSmall = icon("M19.5 14.5A8 8 0 0 1 9.5 4.5a8 8 0 1 0 10 10z")
    val sunSmall = icon(circle(12f, 12f, 4f), "M12 2.8v2M12 19.2v2M2.8 12h2M19.2 12h2M5.5 5.5l1.4 1.4M17.1 17.1l1.4 1.4M5.5 18.5l1.4-1.4M17.1 6.9l1.4-1.4")
    // Apps (los de las fichas del cajón).
    val map = icon("M3.5 6.8 9 4.6l6 2.2 5.5-2.2v12.6L15 19.4l-6-2.2-5.5 2.2z", "M9 4.6v12.6M15 6.8v12.6")
    val youtube = icon(rect(2.8f, 5.8f, 18.4f, 12.4f, 3.6f), "M10.2 9.4v5.2l4.4-2.6z")
    val waze = icon(
        "M6.2 16.6C4.6 16.3 3.6 15.3 3.6 14c1-.3 1.4-1 1.4-2.4C5 7.4 8.6 4.2 12.8 4.2c4.5 0 7.8 3.4 7.8 7.5s-3.4 7.4-7.8 7.4c-1 0-2-.1-2.8-.4",
        "M10.2 13.6c1.2 1.1 3.2 1.1 4.4 0", circle(10.4f, 9.8f, 0.5f), circle(15f, 9.8f, 0.5f), circle(7.8f, 19f, 1.6f), circle(16.6f, 18.6f, 1.6f),
    )
    val camera = icon("M3.5 8.6a2 2 0 0 1 2-2h2.1L9 4.6h6l1.4 2h2.1a2 2 0 0 1 2 2v8.6a2 2 0 0 1-2 2h-13a2 2 0 0 1-2-2z", circle(12f, 12.6f, 3.4f))
    val pulse = icon("M3 12.5h3.8l2.1-5.2 4.2 10.4 2.1-5.2H21")
    val musicNote = icon("M9 17.5V6l10-2.2v11.5", circle(6.6f, 17.5f, 2.4f), circle(16.6f, 15.3f, 2.4f), "M9 9.6l10-2.2")
    val plateBig = icon(rect(2.6f, 6.4f, 18.8f, 11.2f, 2f), "M6.2 12h4.4M13.4 12h4.4", "M5.2 8.8h1.2M17.6 8.8h1.2")

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
