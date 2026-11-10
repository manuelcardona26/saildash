package com.irvingguerra.litedash.core.ui.ice

import android.annotation.SuppressLint
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.TextUnitType
import kotlin.math.roundToInt

/**
 * Texto que se acomoda como en el CSS de los mockups, para copiar sus medidas tal cual:
 * - la caja mide `font-size × line-height` (con `line-height: 1`, justo el tamaño de la letra) y la
 *   línea base cae donde la pone el navegador (la mitad del sobrante arriba y abajo);
 * - el espaciado entre letras empieza en la primera letra (Android le pone media separación antes).
 * Sin esto, un texto de 128 sp queda 16 dp más abajo que en el mockup.
 */
@SuppressLint("ModifierParameter")
@Composable
fun IceText(
    text: String,
    style: TextStyle,
    color: Color = LocalIce.current.ink,
    maxLines: Int = 1,
    modifier: Modifier = Modifier,
) {
    BasicText(text, modifier.cssLine(style), style = style.copy(color = color), maxLines = maxLines, overflow = TextOverflow.Ellipsis)
}

/** Igual que [IceText] con estilos por tramos (p. ej. la hora en negrita dentro de una frase). */
@SuppressLint("ModifierParameter")
@Composable
fun IceText(
    text: AnnotatedString,
    style: TextStyle,
    color: Color = LocalIce.current.ink,
    maxLines: Int = 1,
    modifier: Modifier = Modifier,
) {
    BasicText(text, modifier.cssLine(style), style = style.copy(color = color), maxLines = maxLines, overflow = TextOverflow.Ellipsis)
}

/**
 * La caja y la línea base de CSS para un texto de una línea con [style]: alto = tamaño × alto de
 * línea; línea base = (alto + (ascendente − descendente) × tamaño) / 2, con las medidas de la fuente
 * (Outfit: 1.0 y 0.26; Roboto: 0.928 y 0.244).
 */
fun Modifier.cssLine(style: TextStyle): Modifier = layout { measurable, constraints ->
    val p = measurable.measure(constraints.copy(minHeight = 0, maxHeight = Constraints.Infinity))
    val size = style.fontSize.toPx()
    val factor = if (style.lineHeight.type == TextUnitType.Em) style.lineHeight.value else 1f
    val box = (size * factor).roundToInt()
    val halfDiff = if (style.fontFamily == Outfit) 0.37f else 0.342f
    val wantedBaseline = box / 2f + halfDiff * size
    val baseline = p[FirstBaseline].takeIf { it != androidx.compose.ui.layout.AlignmentLine.Unspecified } ?: box
    val spacing = if (style.letterSpacing.type == TextUnitType.Em) style.letterSpacing.value * size else 0f
    // Más líneas: la caja crece con lo que de verdad ocupan.
    val height = if (p.height > box * 1.6f) p.height else box
    layout(p.width, height.coerceIn(constraints.minHeight, constraints.maxHeight)) {
        p.place((-spacing / 2f).roundToInt(), (wantedBaseline - baseline).roundToInt())
    }
}
