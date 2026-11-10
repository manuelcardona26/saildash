package com.irvingguerra.litedash.core.ui.ice

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.irvingguerra.litedash.core.ui.Icon
import kotlin.math.roundToInt

/*
 * Los controles de los mockups del catálogo (`.btn`, `.tg`, `.seg`, `.pill`, `.search`, `.slider`,
 * `.card`, `.sheet`), con las medidas del CSS. Todas las pantallas nuevas (y los controles de siempre
 * de core.ui, que ahora los usan) se ven así.
 */

/** Los estilos de texto de las pantallas del catálogo (`.h1`, `.h3`, `.cap`, `.lbl`, `.sub`…). */
object IceText2 {
    private fun s(family: androidx.compose.ui.text.font.FontFamily, w: FontWeight, size: Float, spacing: Float = 0f, line: Float = 1f) =
        IceType.body.copy(fontFamily = family, fontWeight = w, fontSize = size.sp, letterSpacing = spacing.em, lineHeight = line.em)

    /** `.h1`: Ajustes, Aplicaciones (300 28 Outfit). */
    val h1 = s(Outfit, FontWeight.Light, 28f, 0.01f)
    /** El título de la sección en Ajustes (`.mh b`, 300 26). */
    val h2 = s(Outfit, FontWeight.Light, 26f, 0.01f)
    /** `.h3`: título de tarjeta (500 17 Outfit). */
    val h3 = s(Outfit, FontWeight.Medium, 17f, 0.01f, 1.15f)
    /** `.cap`: rótulo en mayúsculas espaciadas (500 13, .2em). */
    val cap = s(Outfit, FontWeight.Medium, 13f, 0.2f)
    /** `.lbl`: el renglón de un ajuste (Roboto 15). */
    val lbl = s(RobotoText, FontWeight.Normal, 15f, line = 1.25f)
    /** `.sub`: la ayuda debajo (Roboto 13). */
    val sub = s(RobotoText, FontWeight.Normal, 13f, line = 1.35f)
    /** Párrafo de ayuda (Roboto 13, 1.4). */
    val para = s(RobotoText, FontWeight.Normal, 13f, line = 1.4f)
    /** `.btn`: 500 15 Outfit. */
    val button = s(Outfit, FontWeight.Medium, 15f, 0.01f)
    /** `.seg span`: Roboto 14. */
    val seg = s(RobotoText, FontWeight.Normal, 14f)
    /** `.pill`: Roboto 15. */
    val pill = s(RobotoText, FontWeight.Normal, 15f)
    /** El nombre de una fila (`.row .n`, 400 16 Outfit). */
    val rowTitle = s(Outfit, FontWeight.Normal, 16f, line = 1.1f)
}

/** Las tres clases de `.btn`: principal (blanco hielo), secundario (borde hielo) y discreto. */
enum class IceButtonKind { PRIMARY, SECONDARY, GHOST }

/**
 * `.btn`: 44 dp de alto, redondo, Outfit 500 15. [icon] va antes del texto ([iconAfter] = después,
 * como la flecha de "Siguiente"). [fill] = ocupa el ancho que le den, con el texto centrado.
 */
@Composable
fun IceButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    kind: IceButtonKind = IceButtonKind.SECONDARY,
    icon: ImageVector? = null,
    iconAfter: Boolean = false,
    height: Dp = 44.dp,
    padding: Dp = 20.dp,
    enabled: Boolean = true,
    textColor: Color? = null,
) {
    val ice = LocalIce.current
    val shape = RoundedCornerShape(height / 2)
    val fg = textColor ?: when (kind) {
        IceButtonKind.PRIMARY -> Color(0xFF0A1322)
        IceButtonKind.SECONDARY -> ice.ice
        IceButtonKind.GHOST -> ice.ink2
    }
    val surface = when (kind) {
        IceButtonKind.PRIMARY -> Modifier
            .softGlow(Color(170, 220, 255).copy(alpha = if (ice.dark) 0.30f else 0.2f), 14.dp)
            .clip(shape)
            .background(Brush.verticalGradient(listOf(Color.White, Color(0xFFCDEAFF))))
            .then(if (ice.dark) Modifier else Modifier.border(1.dp, ice.line, shape))
        IceButtonKind.SECONDARY -> Modifier
            .clip(shape)
            .background(if (ice.dark) Color(170, 210, 255).copy(alpha = 0.05f) else Color.White.copy(alpha = 0.55f))
            .border(1.dp, if (ice.dark) Color(169, 220, 255).copy(alpha = 0.32f) else ice.ice.copy(alpha = 0.4f), shape)
        IceButtonKind.GHOST -> Modifier
            .clip(shape)
            .background(if (ice.dark) Color(170, 210, 255).copy(alpha = 0.03f) else Color.White.copy(alpha = 0.4f))
            .border(1.dp, ice.line2, shape)
    }
    Row(
        modifier
            .height(height)
            .then(surface)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = padding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp, Alignment.CenterHorizontally),
    ) {
        val color = if (enabled) fg else fg.copy(alpha = fg.alpha * 0.45f)
        if (icon != null && !iconAfter) Icon(icon, color, 20.dp)
        IceText(text, IceText2.button, color, maxLines = 1)
        if (icon != null && iconAfter) Icon(icon, color, 20.dp)
    }
}

/**
 * `.tg`: el interruptor de 54 × 32 dp (apagado: vidrio con la bolita tenue; prendido: degradado
 * hielo con brillo y la bolita blanca a la derecha). Con [onChange] se toca solo; sin él, lo toca su renglón.
 */
@Composable
fun IceToggle(checked: Boolean, modifier: Modifier = Modifier, onChange: ((Boolean) -> Unit)? = null) {
    val ice = LocalIce.current
    val shape = RoundedCornerShape(16.dp)
    val base = if (checked) {
        modifier
            .softGlow(Color(150, 210, 255).copy(alpha = 0.32f), 9.dp)
            .size(54.dp, 32.dp)
            .clip(shape)
            .background(Brush.horizontalGradient(listOf(Color(0xFF5FB3F5), Color(0xFFA9DCFF))))
            .border(1.dp, Color(210, 238, 255).copy(alpha = 0.6f), shape)
    } else {
        modifier
            .size(54.dp, 32.dp)
            .clip(shape)
            .background(if (ice.dark) Color(190, 225, 255).copy(alpha = 0.12f) else Color(40, 80, 130).copy(alpha = 0.12f))
            .border(1.dp, ice.line, shape)
    }
    Box(base.then(if (onChange != null) Modifier.clickable { onChange(!checked) } else Modifier)) {
        Box(
            Modifier
                .offset(if (checked) 26.dp else 4.dp, 4.dp)
                .size(22.dp)
                .clip(CircleShape)
                .background(if (checked) Color.White else if (ice.dark) Color(220, 236, 255).copy(alpha = 0.6f) else Color.White),
        )
    }
}

/** El relleno de lo elegido (`.on` de `.seg`, `.pill`, `.mi`): degradado hielo a 135°. */
private fun selectedFill(alphaA: Float, alphaB: Float) = listOf(0f to Color(120, 190, 255).copy(alpha = alphaA), 1f to Color(90, 150, 235).copy(alpha = alphaB))

/**
 * Lo elegido en hielo: degradado a 135°, borde [border], reflejo arriba y esquinas [radius].
 * (`.seg span.on`, `.pill.on`, `.mi.on`, `.row.sel`).
 */
@Composable
fun Modifier.iceSelected(radius: Dp, border: Color = Color(170, 215, 255).copy(alpha = 0.34f), strength: Float = 1f): Modifier {
    val ice = LocalIce.current
    val fill = if (ice.dark) selectedFill(0.32f * strength, 0.13f * strength) else listOf(0f to Color(90, 160, 235).copy(alpha = 0.26f), 1f to Color(90, 150, 235).copy(alpha = 0.12f))
    return glass(fill, if (ice.dark) border else ice.tileOnBorder, if (ice.dark) Color(235, 246, 255).copy(alpha = 0.18f) else ice.sheen, radius, angle = 135f)
}

/**
 * `.seg`: opciones excluyentes en una cápsula oscura de 44 dp; la elegida, en hielo. [icons] (si
 * hay) van antes de cada texto. [fill] = las opciones se reparten el ancho (`flex:1`).
 */
@Composable
fun <T> IceSegmented(
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    icons: List<ImageVector?> = emptyList(),
    counts: List<String?> = emptyList(),
    height: Dp = 44.dp,
    fill: Boolean = false,
    textStyle: androidx.compose.ui.text.TextStyle = IceText2.seg,
    padding: Dp = 16.dp,
    check: Boolean = false,
) {
    val ice = LocalIce.current
    val shape = RoundedCornerShape(height / 2)
    Row(
        modifier
            .height(height)
            .clip(shape)
            .background(if (ice.dark) Color(6, 12, 22).copy(alpha = 0.45f) else Color.White.copy(alpha = 0.55f))
            .border(1.dp, ice.line, shape)
            .padding(3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        options.forEachIndexed { i, (value, text) ->
            val on = value == selected
            val inner = (height - 6.dp) / 2
            Row(
                (if (fill) Modifier.weight(1f) else Modifier)
                    .fillMaxHeight()
                    .then(if (on) Modifier.iceSelected(inner, Color(170, 215, 255).copy(alpha = 0.32f), 1.06f) else Modifier)
                    .clip(RoundedCornerShape(inner))
                    .clickable { onSelect(value) }
                    .padding(horizontal = padding),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp, Alignment.CenterHorizontally),
            ) {
                val color = if (on) ice.title else ice.ink2
                icons.getOrNull(i)?.let { Icon(it, if (on) (if (ice.dark) Color(0xFFD8F0FF) else ice.ice2) else ice.ink2, 18.dp) }
                if (check && on) Icon(IceIcons.check, if (ice.dark) Color(0xFFD8F0FF) else ice.ice2, 16.dp)
                IceText(text, textStyle, color, maxLines = 1)
                counts.getOrNull(i)?.let { IceText(it, IceType.small, if (on) Color(0xFFCFE8FF) else ice.ink3, maxLines = 1) }
            }
        }
    }
}

/**
 * `.pill`: 44 dp, redonda, Roboto 15; [on] en hielo. [icon] va antes ([iconTint] si no es el de siempre).
 */
@Composable
fun IcePill(
    text: String,
    on: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    iconTint: Color? = null,
    height: Dp = 44.dp,
    padding: Dp = 16.dp,
    textStyle: androidx.compose.ui.text.TextStyle = IceText2.pill,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    val ice = LocalIce.current
    val shape = RoundedCornerShape(height / 2)
    val surface = if (on) Modifier.iceSelected(height / 2, Color(170, 215, 255).copy(alpha = 0.45f))
    else Modifier
        .clip(shape)
        .background(if (ice.dark) Color(170, 210, 255).copy(alpha = 0.05f) else Color.White.copy(alpha = 0.55f))
        .border(1.dp, ice.line, shape)
    Row(
        modifier.height(height).then(surface).clip(shape).clickable(onClick = onClick).padding(horizontal = padding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (icon != null) Icon(icon, iconTint ?: if (on) (if (ice.dark) Color(0xFFD8F0FF) else ice.ice2) else ice.ink2, 18.dp)
        IceText(text, textStyle, if (on) ice.title else ice.ink, maxLines = 1)
        trailing?.invoke(this)
    }
}

/**
 * `.search`: campo redondo oscuro de 48 dp con la lupa hielo. El teclado sale solo al tocarlo
 * (nunca solo: en la pantalla del carro tapa media vista).
 */
@Composable
fun IceSearchField(
    value: String,
    onChange: (String) -> Unit,
    hint: String,
    modifier: Modifier = Modifier,
    height: Dp = 48.dp,
    icon: ImageVector? = IceIcons.search,
) {
    val ice = LocalIce.current
    val shape = RoundedCornerShape(height / 2)
    Row(
        modifier
            .height(height)
            .clip(shape)
            .background(if (ice.dark) Color(6, 12, 22).copy(alpha = 0.5f) else Color.White.copy(alpha = 0.7f))
            .border(1.dp, ice.line, shape)
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, ice.ice, 24.dp)
            Spacer(Modifier.width(12.dp))
        }
        val style = IceType.body.copy(fontSize = 16.sp)
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (value.isEmpty()) IceText(hint, style, ice.ink3, maxLines = 1)
            BasicTextField(
                value, onChange, Modifier.fillMaxWidth(),
                textStyle = style.copy(color = ice.ink),
                singleLine = true,
                cursorBrush = SolidColor(ice.ice),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            )
        }
    }
}

/**
 * `.slider`: riel de 3 dp, lo lleno en degradado hielo con brillo y la perilla blanca de 22 dp con
 * su halo. El área del dedo es de [touch] de alto. [onPreview] mientras se arrastra, [onChange] al soltar.
 */
@Composable
fun IceSlider(
    value: Float,
    onChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    onPreview: (Float) -> Unit = {},
    touch: Dp = 44.dp,
    steps: Int = 100,
) {
    val ice = LocalIce.current
    val preview by rememberUpdatedState(onPreview)
    val change by rememberUpdatedState(onChange)
    var dragged by remember { mutableStateOf<Float?>(null) }
    val shown = (dragged ?: value).coerceIn(0f, 1f)
    Box(
        modifier
            .height(touch)
            .pointerInput(steps) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    down.consume()
                    fun at(x: Float) = ((x / size.width).coerceIn(0f, 1f) * steps).roundToInt() / steps.toFloat()
                    var v = at(down.position.x)
                    dragged = v
                    preview(v)
                    try {
                        while (true) {
                            val finger = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                            if (!finger.pressed) break
                            finger.consume()
                            val next = at(finger.position.x)
                            if (next != v) {
                                v = next
                                dragged = v
                                preview(v)
                            }
                        }
                    } finally {
                        change(v)
                        dragged = null
                    }
                }
            }
            .drawWithCache {
                val track = 3.dp.toPx()
                val cy = size.height / 2
                val x = shown * size.width
                val rail = if (ice.dark) Color(190, 225, 255).copy(alpha = 0.14f) else Color(40, 80, 130).copy(alpha = 0.16f)
                val fill = Brush.horizontalGradient(listOf(Color(0xFF7CC6FF), Color(0xFFE3F4FF)), endX = x.coerceAtLeast(1f))
                val glow = Color(150, 210, 255).copy(alpha = 0.25f)
                val knob = 11.dp.toPx()
                onDrawBehind {
                    drawRoundRect(rail, Offset(0f, cy - track / 2), Size(size.width, track), CornerRadius(track / 2))
                    drawRoundRect(glow, Offset(0f, cy - track * 1.5f), Size(x, track * 3), CornerRadius(track * 1.5f))
                    drawRoundRect(fill, Offset(0f, cy - track / 2), Size(x, track), CornerRadius(track / 2))
                    drawCircle(Color(190, 228, 255).copy(alpha = 0.10f), knob + 5.dp.toPx(), Offset(x, cy))
                    drawCircle(Color(190, 230, 255).copy(alpha = 0.35f), knob + 2.dp.toPx(), Offset(x, cy))
                    drawCircle(Color.White, knob, Offset(x, cy))
                }
            },
    )
}

/** `.sw`: la muestra redonda de un color (44 dp) con luz arriba a la izquierda; [on] lleva el anillo y la palomita. */
@Composable
fun IceSwatch(light: Color, deep: Color, on: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, size: Dp = 44.dp, checkColor: Color = Color(0xFF0A1322)) {
    val ice = LocalIce.current
    val ring = if (ice.dark) Color(0xFFEAF6FF) else ice.ice2
    Box(
        modifier
            .then(if (on) Modifier.softGlow(Color(170, 220, 255).copy(alpha = 0.32f), 12.dp) else Modifier)
            .size(size)
            .drawWithCache {
                val r = this.size.minDimension / 2
                val c = Offset(this.size.width / 2, this.size.height / 2)
                val brush = Brush.radialGradient(
                    0f to light, 0.7f to deep, 1f to deep,
                    center = Offset(this.size.width * 0.35f, this.size.height * 0.30f),
                    radius = Offset(this.size.width * 0.65f, this.size.height * 0.70f).getDistance(),
                )
                onDrawBehind {
                    if (on) {
                        drawCircle(ring, r + 5.dp.toPx(), c)
                        drawCircle(ice.bg, r + 3.dp.toPx(), c)
                    }
                    drawCircle(brush, r, c)
                    drawCircle(Color.White.copy(alpha = 0.4f), r - 0.5.dp.toPx(), c, style = Stroke(1.dp.toPx()))
                }
            }
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { if (on) Icon(IceIcons.check, checkColor, 20.dp) }
}

/** `.card`: vidrio de 18 dp; [hi] = la resaltada (más clara, borde hielo y brillo). */
@Composable
fun Modifier.iceCard(radius: Dp = 18.dp, hi: Boolean = false): Modifier {
    val ice = LocalIce.current
    if (!hi || !ice.dark) return iceGlass(radius)
    val fill = listOf(0f to Color(150, 205, 255).copy(alpha = 0.17f), 0.6f to Color(110, 160, 230).copy(alpha = 0.07f), 1f to Color(90, 130, 190).copy(alpha = 0.04f))
    return softGlow(Color(120, 190, 255).copy(alpha = 0.10f), 16.dp)
        .glass(fill, Color(170, 215, 255).copy(alpha = 0.42f), Color(235, 246, 255).copy(alpha = 0.18f), radius)
}

/** `.sheet`: la hoja grande (Ajustes, Aplicaciones, Música) de 26 dp con su degradado a 170°. */
@Composable
fun Modifier.iceSheet(radius: Dp = 26.dp): Modifier {
    val ice = LocalIce.current
    val fill = if (ice.dark) listOf(
        0f to Color(150, 195, 250).copy(alpha = 0.10f),
        0.6f to Color(90, 130, 190).copy(alpha = 0.045f),
        1f to Color(70, 110, 170).copy(alpha = 0.03f),
    ) else ice.glass
    return glass(fill, ice.line, ice.sheen, radius, angle = 170f)
}

/** `.shade`: oscurece el espacio detrás de una hoja (de .35 arriba a .72 abajo). */
fun Modifier.iceShade(top: Float = 0.35f, bottom: Float = 0.72f): Modifier = drawBehind {
    drawRect(Brush.verticalGradient(listOf(Color(4, 7, 14).copy(alpha = top), Color(4, 7, 14).copy(alpha = bottom))))
}

/** `.dot`: el punto de estado de 8 dp con su brillo. */
@Composable
fun IceDot(color: Color, modifier: Modifier = Modifier, size: Dp = 8.dp) {
    Box(modifier.softGlow(color.copy(alpha = 0.5f), 5.dp).size(size).clip(CircleShape).background(color))
}

/** `.well`: pozo redondo de 44 dp con el ícono de 22 dp en [tint]. */
@Composable
fun IceWell(icon: ImageVector, tint: Color = LocalIce.current.ice, modifier: Modifier = Modifier, size: Dp = 44.dp, iconSize: Dp = 22.dp) =
    IconWell(icon, tint, size, iconSize, modifier)

/** Un renglón tocable con el contenido centrado verticalmente (para listas dentro de tarjetas). */
@Composable
fun IceRowBox(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, content: @Composable BoxScope.() -> Unit) {
    Box(modifier.then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier).padding(0.dp), contentAlignment = Alignment.CenterStart, content = content)
}
