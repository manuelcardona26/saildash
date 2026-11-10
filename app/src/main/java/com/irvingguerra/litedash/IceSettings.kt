// SailDash 1.1 · diseño C: una sola hoja de Ajustes. Las secciones dibujadas sobre el lienzo de
// 1024 × 600 (Taller, las subpáginas de Tu carro, Sistema, Mandos) van dentro de la parte derecha de
// la hoja, con el mismo menú, el mismo encabezado y el mismo dock que el resto.
package com.irvingguerra.litedash

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp

/**
 * De dónde a dónde del lienzo se dibujó cada pantalla en su mockup (x, y, ancho, alto, en dp): eso se
 * acomoda, parejo y sin deformar, en la parte derecha de la hoja, debajo del encabezado.
 */
enum class CanvasSrc(val x: Float, val y: Float, val w: Float, val h: Float) {
    /** taller-*.html: las pestañas (y = 72) y el panel grande, de .main (258) al borde. */
    TALLER(258f, 72f, 750f, 440f),
    /** reversa, pico-y-placa, recordatorios: lo de debajo del encabezado (y = 76). */
    CARRO(266f, 76f, 742f, 436f),
    /** ajustes, diagnostico, mandos-volante: ya medidas sobre la hoja (tal cual). */
    SHEET(0f, 0f, 1024f, 600f),
}

/** La parte derecha de la hoja, debajo del encabezado: x 260 a 1000 (con 20 dp a los lados), y 94 a 504. */
private const val AREA_X = 260f
private const val AREA_W = 740f
private const val AREA_Y = 94f
private const val AREA_H = 410f
private const val AREA_SIDE = 20f

/** Cómo quedó acomodado el lienzo de adentro: escala [s] y su esquina en ([dx], [dy]) dp del de afuera. */
@Immutable
internal class CanvasMap(val s: Float, val dx: Float, val dy: Float)

internal val LocalCanvasMap = staticCompositionLocalOf<CanvasMap?> { null }

/**
 * Dibuja [content] (medido sobre el lienzo de 1024 × 600, como en su mockup) dentro de la hoja de
 * Ajustes. Achica todo parejo (densidad, no una imagen: el texto sigue nítido y los toques caen
 * donde se ve). Lo que deba ir a pantalla completa (ventanas, teclado de números) usa [Unscaled].
 */
@Composable
internal fun SettingsCanvas(src: CanvasSrc, content: @Composable BoxScope.() -> Unit) {
    if (src == CanvasSrc.SHEET) {
        Box(Modifier.fillMaxSize(), content = content)
        return
    }
    val s = minOf((AREA_W - 2 * AREA_SIDE) / src.w, AREA_H / src.h)
    val dx = AREA_X + (AREA_W - src.w * s) / 2 - src.x * s
    val dy = AREA_Y - src.y * s
    val outer = LocalDensity.current
    Box(
        Modifier.fillMaxSize().layout { m, c ->
            val p = m.measure(Constraints.fixed((1024 * s).dp.roundToPx(), (600 * s).dp.roundToPx()))
            layout(c.maxWidth, c.maxHeight) { p.place(dx.dp.roundToPx(), dy.dp.roundToPx()) }
        },
    ) {
        CompositionLocalProvider(
            LocalDensity provides Density(outer.density * s, outer.fontScale),
            LocalCanvasMap provides CanvasMap(s, dx, dy),
        ) { Box(Modifier.fillMaxSize(), content = content) }
    }
}

/**
 * Deshace lo de [SettingsCanvas] para [content]: vuelve al lienzo entero de 1024 × 600 y a su
 * tamaño de siempre (encabezado, ventanas encima, teclado de números).
 */
@Composable
internal fun Unscaled(content: @Composable BoxScope.() -> Unit) {
    val map = LocalCanvasMap.current
    if (map == null) {
        Box(Modifier.fillMaxSize(), content = content)
        return
    }
    val inner = LocalDensity.current
    CompositionLocalProvider(
        LocalDensity provides Density(inner.density / map.s, inner.fontScale),
        LocalCanvasMap provides null,
    ) {
        Box(
            Modifier.layout { m, c ->
                val p = m.measure(Constraints.fixed(1024.dp.roundToPx(), 600.dp.roundToPx()))
                layout(c.maxWidth, c.maxHeight) { p.place(-map.dx.dp.roundToPx(), -map.dy.dp.roundToPx()) }
            },
            content = content,
        )
    }
}

/**
 * Dibuja [section] si es de las medidas sobre el lienzo y devuelve true; si no, false (y Ajustes
 * pone sus tarjetas de siempre, con el mismo encabezado).
 */
@Composable
internal fun iceSection(d: Dependencies, section: SettingsSection, onTires: () -> Unit): Boolean {
    when (section) {
        SettingsSection.WORKSHOP -> TallerScreen(d, onTires)
        // Tu carro: Recordatorios, Pico y placa y Reversa son subpáginas; Mi carro y Modelos, tarjetas.
        SettingsSection.CAR -> {
            val tab = currentTab("car")
            if (tab !in CAR_TAB_REMINDERS..CAR_TAB_REVERSE) return false
            CarSubpage(d, tab) { selectTab("car", 0) }
        }
        SettingsSection.SYSTEM -> SystemScreen(d)
        SettingsSection.WHEEL -> WheelScreen(d)
        else -> return false
    }
    return true
}
