package com.irvingguerra.litedash

import com.irvingguerra.litedash.core.ui.ice.IceIcons.circle
import com.irvingguerra.litedash.core.ui.ice.IceIcons.icon

/**
 * Los íconos de las páginas Carro, Carretera y Mapa, del modo galaxia y de Personalizar medidor que no
 * están en [com.irvingguerra.litedash.core.ui.ice.IceIcons], copiados del SVG de sus mockups.
 */
object PageIcons {
    val shuffle = icon("M3.5 7h3.2c4.6 0 6 10 10.6 10h3.2M3.5 17h3.2c1.6 0 2.7-1.2 3.6-2.8M13.7 9.8C14.6 8.2 15.7 7 17.3 7h3.2M18 4.5 20.5 7 18 9.5M18 14.5l2.5 2.5-2.5 2.5")
    val fullscreen = icon("M4 9V4h5M20 9V4h-5M4 15v5h5M20 15v5h-5")
    val layers = icon("M12 3.5 21 8.5l-9 5-9-5z", "M3 12.5l9 5 9-5M3 16.5l9 5 9-5")
    val locate = icon(circle(12f, 12f, 6.5f), "M12 2.5v3M12 18.5v3M2.5 12h3M18.5 12h3", fills = listOf(circle(12f, 12f, 2.2f)))
    val plus = icon("M12 5v14M5 12h14")
    val minus = icon("M5 12h14")
    val close = icon("M6.5 6.5l11 11M17.5 6.5l-11 11")
    val bell = icon("M6 16.5V11a6 6 0 0 1 12 0v5.5l1.5 2h-15z", "M10 20.5a2 2 0 0 0 4 0")
}
