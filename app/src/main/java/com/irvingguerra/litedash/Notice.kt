package com.irvingguerra.litedash

import com.irvingguerra.litedash.core.ui.Radius
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.irvingguerra.litedash.core.ui.Icon
import com.irvingguerra.litedash.core.ui.Label
import com.irvingguerra.litedash.core.ui.LocalColors
import com.irvingguerra.litedash.core.ui.Typography
import com.irvingguerra.litedash.core.ui.ice.IceText2
import com.irvingguerra.litedash.core.ui.ice.IceWell
import com.irvingguerra.litedash.core.ui.ice.LocalIce
import com.irvingguerra.litedash.core.ui.ice.iceCard
import com.irvingguerra.litedash.core.ui.ice.glass

private val NOTICE_FILL = listOf(0f to Color(32, 52, 82).copy(alpha = 0.96f), 0.6f to Color(14, 24, 42).copy(alpha = 0.97f), 1f to Color(10, 18, 32).copy(alpha = 0.97f))

/**
 * Aviso arriba del tablero (inicio, versión nueva, recordatorio): texto y sus
 * botones. En angosto (teléfono parado) el texto no cabe junto a los botones y va arriba. El ícono va
 * de [iconColor] (p. ej. rojo o ámbar de advertencia) o, sin él, del color de la interfaz.
 */
@Composable
fun Notice(text: String, icon: ImageVector? = null, iconColor: Color? = null, buttons: @Composable () -> Unit) {
    val c = LocalColors.current
    val box = Modifier
        .fillMaxWidth()
        .padding(horizontal = 16.dp, vertical = 4.dp)
        // Diseño C: el aviso es la tarjeta de vidrio casi opaca de las tarjetas rápidas (se lee sobre lo que haya).
        .then(if (LocalIce.current.dark) Modifier.glass(NOTICE_FILL, Color(190, 225, 255).copy(alpha = 0.22f), Color(235, 246, 255).copy(alpha = 0.12f), 18.dp, angle = 165f) else Modifier.iceCard(radius = 18.dp))
        .padding(start = 18.dp, end = 10.dp, top = 10.dp, bottom = 10.dp)
    val label = @Composable { m: Modifier ->
        Row(m, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (icon != null) IceWell(icon, iconColor ?: LocalIce.current.ice, size = 40.dp, iconSize = 22.dp)
            Label(text, IceText2.lbl, LocalIce.current.ink, maxLines = 2)
        }
    }
    BoxWithConstraints {
        if (maxWidth < 560.dp) {
            Column(box, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                label(Modifier)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { buttons() }
            }
        } else {
            Row(box, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                label(Modifier.weight(1f))
                buttons()
            }
        }
    }
}
