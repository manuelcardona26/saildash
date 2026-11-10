package com.irvingguerra.litedash.core.ui.ice

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.irvingguerra.litedash.core.ui.Icon

/** Un botón del dock: [key] lo identifica al tocarlo; [separatorBefore] pone la rayita fina antes. */
@Immutable
data class DockEntry(val key: String, val icon: ImageVector, val label: String, val separatorBefore: Boolean = false)

/**
 * El dock de abajo (`.dock`): 72 dp, fondo casi negro con la línea de luz arriba, botones del mismo
 * ancho con ícono y nombre, separadores finos y el reloj a la derecha ([clock]). El de [active]
 * se ve blanco, con el ícono hielo y la marca brillante arriba.
 */
@Composable
fun IceDock(
    entries: List<DockEntry>,
    active: String?,
    onClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    clock: (@Composable () -> Unit)? = null,
) {
    val ice = LocalIce.current
    Row(
        modifier
            .fillMaxWidth()
            .height(IceDims.dock)
            .drawWithCache {
                val bg = Brush.verticalGradient(listOf(ice.dockTop, ice.dockBottom))
                val px = 1.dp.toPx()
                val edge = if (ice.dark) Color(190, 225, 255) else Color(40, 90, 150)
                val top = Brush.horizontalGradient(
                    0f to Color.Transparent, 0.3f to edge.copy(alpha = 0.45f), 0.7f to edge.copy(alpha = 0.45f), 1f to Color.Transparent,
                )
                onDrawBehind {
                    drawRect(bg)
                    // border-top de 1 dp y, encima, la línea de luz que se desvanece a los lados.
                    drawRect(edge.copy(alpha = 0.12f), size = Size(size.width, px))
                    drawRect(top, size = Size(size.width, px))
                }
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for (e in entries) {
            if (e.separatorBefore) DockSeparator()
            DockButton(e, e.key == active, Modifier.weight(1f)) { onClick(e.key) }
        }
        if (clock != null) {
            DockSeparator()
            Box(Modifier.width(140.dp).fillMaxHeight().padding(end = 24.dp), contentAlignment = Alignment.CenterEnd) { clock() }
        }
    }
}

@Composable
private fun DockSeparator() {
    val ice = LocalIce.current
    VDivider(28.dp, if (ice.dark) Color(190, 225, 255).copy(alpha = 0.14f) else ice.line)
}

@Composable
private fun DockButton(e: DockEntry, on: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val ice = LocalIce.current
    Box(
        modifier
            .fillMaxHeight()
            .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .then(if (on) Modifier.activeMark(ice) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(contentAlignment = Alignment.Center) {
                // drop-shadow 0 0 6px del ícono activo: un halo tenue detrás.
                if (on) Box(Modifier.size(24.dp).softGlow(ice.iceGlow.copy(alpha = 0.22f), 8.dp))
                Icon(e.icon, if (on) ice.ice else ice.dockInk, 24.dp)
            }
            IceText(e.label, IceType.dockLabel, if (on) ice.title else ice.dockInk, maxLines = 1)
        }
    }
}

/** La marca del activo: 2 dp arriba, del 30 % al 70 % del ancho, con brillo. */
private fun Modifier.activeMark(ice: IcePalette) = drawWithCache {
    val left = size.width * 0.3f
    val w = size.width * 0.4f
    val h = 2.dp.toPx()
    val mark = if (ice.dark) Color(0xFFBFE6FF) else ice.ice
    val line = Brush.horizontalGradient(listOf(Color.Transparent, mark, Color.Transparent), startX = left, endX = left + w)
    val glowH = 12.dp.toPx()
    val glow = Brush.radialGradient(
        listOf(ice.iceGlow.copy(alpha = 0.35f), Color.Transparent),
        center = Offset(size.width / 2f, 0f), radius = w / 2f,
    )
    onDrawBehind {
        // El brillo se aplasta en un óvalo bajo la marca (box-shadow 0 0 10px).
        drawRect(glow, Offset(left - 6.dp.toPx(), 0f), Size(w + 12.dp.toPx(), glowH))
        drawRoundRect(line, Offset(left, 0f), Size(w, h), CornerRadius(h))
    }
}

/** El reloj del dock (`.dock .clock`): hora delgada, a. m./p. m. chico y la fecha corta debajo. */
@Composable
fun DockClock(time: String, marker: String?, date: String) {
    val ice = LocalIce.current
    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Row {
            IceText(time, IceType.dockTime, ice.title, maxLines = 1, modifier = Modifier.alignByBaseline())
            if (marker != null) IceText(marker, IceType.dockMarker, ice.ink2, maxLines = 1, modifier = Modifier.alignByBaseline().padding(start = 4.dp))
        }
        IceText(date, IceType.dockDate, ice.ink2, maxLines = 1)
    }
}
