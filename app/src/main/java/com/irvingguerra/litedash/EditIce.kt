// SailDash 1.1 · diseño C: lo de encima al acomodar el inicio (catalogo/personalizar-inicio): la píldora
// "Editando el inicio · deshacer · Listo" y la hoja de abajo para agregar widgets y elegir plantilla.
// Mover, agrandar y quitar siguen siendo los de siempre (Dashboard).
package com.irvingguerra.litedash

import com.irvingguerra.litedash.core.ui.ice.IceIconButton
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.irvingguerra.litedash.core.ui.Icon
import com.irvingguerra.litedash.core.ui.ice.IceText
import com.irvingguerra.litedash.core.ui.ice.LocalIce
import com.irvingguerra.litedash.core.ui.ice.glass
import com.irvingguerra.litedash.core.ui.ice.softGlow
import com.irvingguerra.litedash.feature.dashboard.TileTypes
import com.irvingguerra.litedash.feature.dashboard.size

/**
 * La píldora y la hoja, encima del inicio mientras se acomoda. [start] son las piezas como estaban
 * al entrar ("deshacer" vuelve ahí); [onDone] sale y guarda (ya se guarda al mover).
 */
@Composable
fun EditChrome(
    d: Dependencies,
    start: List<com.irvingguerra.litedash.feature.dashboard.Tile>,
    onDone: () -> Unit,
    onPickApp: () -> Unit,
    onPickGauge: () -> Unit,
    onTiresSettings: () -> Unit,
) {
    val ice = LocalIce.current
    var sheet by remember { mutableStateOf(true) }
    var noRoom by remember { mutableStateOf(false) }
    // El lienzo deja pasar los toques al tablero de abajo salvo en la píldora y la hoja.
    IceFrameOverlay {
        // .pill: centrada arriba.
        Row(
            Modifier.align(Alignment.TopCenter).offset(y = 9.dp).height(48.dp)
                .glass(listOf(0f to Color(16, 26, 42).copy(alpha = 0.92f), 1f to Color(8, 14, 26).copy(alpha = 0.92f)), ice.line, ice.sheen, 24.dp)
                .padding(start = 20.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IceText(stringResource(R.string.edit_title), ts(15f, 500), Color.White)
            Spacer(Modifier.width(14.dp))
            Box(Modifier.size(40.dp).clip(CircleShape).clickable(enabled = d.dashboard.tiles != start) { d.dashboard.change(start) }, contentAlignment = Alignment.Center) {
                Icon(SetIcons.undo, if (d.dashboard.tiles != start) ice.ink2 else ice.ink3.copy(alpha = 0.3f), 22.dp)
            }
            if (!sheet) Box(Modifier.size(40.dp).clip(CircleShape).clickable { sheet = true }, contentAlignment = Alignment.Center) { Icon(SetIcons.plus, ice.ink2, 22.dp) }
            Spacer(Modifier.width(6.dp))
            Box(
                Modifier.height(40.dp).clip(RoundedCornerShape(20.dp)).background(Brush.verticalGradient(listOf(Color.White, Color(0xFFCDEAFF))))
                    .clickable(onClick = onDone).padding(horizontal = 18.dp),
                contentAlignment = Alignment.Center,
            ) { IceText(stringResource(R.string.done), ts(15f, 500), Color(0xFF0A1322)) }
        }
        if (!sheet) return@IceFrameOverlay
        // .sheet: de 300 dp hasta abajo, esquinas de arriba redondas.
        Box(
            Modifier.offset(y = 300.dp).size(1024.dp, 300.dp).clip(RoundedCornerShape(26.dp)).background(Kc.sheetBase)
                .glass(listOf(0f to Color(14, 22, 36).copy(alpha = 0.97f), 1f to Color(6, 10, 18).copy(alpha = 0.98f)), ice.line, ice.sheen, 26.dp)
                .clickable(remember { MutableInteractionSource() }, indication = null) {},
        ) {
            Box(Modifier.align(Alignment.TopCenter).offset(y = 9.dp).size(44.dp, 4.dp).clip(RoundedCornerShape(2.dp)).background(Color(190, 225, 255).copy(alpha = 0.3f)))
            Row(Modifier.at(28, 26, 968), verticalAlignment = Alignment.CenterVertically) {
                IceText(stringResource(R.string.edit_add), ts(24f, 300), Color.White)
                Spacer(Modifier.width(14.dp))
                IceText(stringResource(if (noRoom) R.string.no_room else R.string.edit_add_sub), ts(13f, display = false), if (noRoom) Kc.coral else ice.ink2, modifier = Modifier.weight(1f))
                IceIconButton(SetIcons.close, { sheet = false }, size = 44.dp, iconSize = 20.dp, tint = ice.ink2)
            }
            // Los widgets, de lado (los que quepan; se desliza para ver los demás).
            Row(Modifier.at(24, 70, 976, 92).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                for (spec in TileSpecs.filter { offered(d, it.type) }) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            Modifier.size(113.dp, 66.dp).glass(ice.glass, ice.line, ice.sheen, 14.dp).clip(RoundedCornerShape(14.dp)).clickable {
                                noRoom = false
                                when (spec.type) {
                                    TileTypes.APP -> onPickApp()
                                    TileTypes.GAUGE -> onPickGauge()
                                    TileTypes.TIRES -> {
                                        var picking = false
                                        if (d.addTiresTile { picking = true }) { if (picking) onTiresSettings() } else noRoom = true
                                    }
                                    else -> if (!d.dashboard.addTile(spec.type)) noRoom = true
                                }
                            },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(iconOf(spec.type), Color(0xFFEAF4FF), 26.dp)
                            Box(
                                Modifier.align(Alignment.TopEnd).offset((-6).dp, 6.dp).softGlow(Color(150, 210, 255).copy(alpha = 0.3f), 4.dp).size(20.dp).clip(CircleShape)
                                    .background(Color(0xFFD8F0FF)),
                                contentAlignment = Alignment.Center,
                            ) { Icon(SetIcons.plus, Color(0xFF0A1322), 14.dp) }
                        }
                        IceText(stringResource(spec.name), ts(14f, display = false), ice.ink, modifier = Modifier.padding(top = 10.dp))
                    }
                }
            }
            // Plantillas.
            Row(Modifier.at(28, 186, 968), verticalAlignment = Alignment.CenterVertically) {
                CapLabel(stringResource(R.string.edit_templates))
                Spacer(Modifier.weight(1f))
                IceText(stringResource(R.string.edit_templates_note), ts(13f, display = false), ice.ink2)
            }
            val grid = d.dashboard.grid
            if (grid != null) Row(Modifier.at(24, 208, 976, 76).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                for (p in TemplateTab.CAR.templates.filter { grid.size in it.sizes }) {
                    Row(
                        Modifier.size(183.dp, 76.dp).glass(ice.glass, ice.line, ice.sheen, 16.dp).clip(RoundedCornerShape(16.dp))
                            .clickable { d.dashboard.template(p, TileTypes.CAR) }.padding(horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.width(84.dp).clip(RoundedCornerShape(8.dp)).border(1.dp, ice.line2, RoundedCornerShape(8.dp))) {
                            TemplateThumbnail(p, grid, TileTypes.CAR, d.dashboard.hidden)
                        }
                        Spacer(Modifier.width(10.dp))
                        IceText(stringResource(nameOf(p)), ts(14f, display = false, lh = 1.2f), Color.White, maxLines = 2)
                    }
                }
            }
        }
    }
}
