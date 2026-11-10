package com.irvingguerra.litedash.core.ui.ice

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.irvingguerra.litedash.core.ui.Icon

/*
 * Las piezas del diseño C, para usarlas en cualquier pantalla nueva. Ninguna usa blur ni
 * RenderEffect (Android 7 y un T3L): el vidrio es un degradado con borde de 1 dp y un reflejo
 * arriba, todo calculado una vez por tamaño (drawWithCache).
 */

/**
 * El vidrio de common.css (`.glass`): relleno [fill] con degradado a [angle]° como en CSS, borde
 * de 1 dp [border] por dentro y el reflejo [sheen] de 1 dp en el borde de arriba
 * (`inset 0 1px 0`). [radius] es el de las esquinas.
 */
fun Modifier.glass(
    fill: List<Pair<Float, Color>>,
    border: Color,
    sheen: Color,
    radius: Dp,
    angle: Float = 160f,
): Modifier = drawWithCache {
    val r = radius.toPx().coerceAtMost(minOf(size.width, size.height) / 2f)
    val px = 1.dp.toPx()
    val outline = Path().apply { addRoundRect(RoundRect(0f, 0f, size.width, size.height, CornerRadius(r))) }
    val shifted = Path().apply { addRoundRect(RoundRect(0f, px, size.width, size.height + px, CornerRadius(r))) }
    // El reflejo: lo que queda del contorno al bajarlo 1 dp (una media luna fina arriba).
    val sheenPath = Path().apply { op(outline, shifted, PathOperation.Difference) }
    val brush = cssLinearGradient(angle, size, fill)
    val half = px / 2f
    onDrawBehind {
        drawPath(outline, brush)
        if (sheen.alpha > 0f) drawPath(sheenPath, sheen)
        if (border.alpha > 0f) drawRoundRect(
            border, Offset(half, half), Size(size.width - px, size.height - px),
            CornerRadius((r - half).coerceAtLeast(0f)), style = Stroke(px),
        )
    }
}

/** El vidrio con los colores del tema: [strong] es `--glass-strong`. */
@Composable
fun Modifier.iceGlass(radius: Dp = IceDims.radius, strong: Boolean = false): Modifier {
    val ice = LocalIce.current
    return glass(if (strong) ice.glassStrong else ice.glass, ice.line, ice.sheen, radius)
}

/**
 * Tarjeta de vidrio (`.glass`): degradado, borde fino de luz y reflejo arriba. Con [onClick] se
 * puede tocar (el toque se recorta a sus esquinas).
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    radius: Dp = IceDims.radius,
    strong: Boolean = false,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    var m = modifier.iceGlass(radius, strong)
    if (onClick != null) m = m.clip(RoundedCornerShape(radius)).clickable(onClick = onClick)
    Box(m, content = content)
}

/**
 * Un brillo suave alrededor (lo que en CSS es `box-shadow: 0 0 [blur] color`), sin blur: un óvalo
 * con degradado radial que sale [blur] más allá de la pieza. Va detrás de lo que se dibuje.
 */
fun Modifier.softGlow(color: Color, blur: Dp): Modifier = drawWithCache {
    val b = blur.toPx()
    val w = size.width + 2 * b
    val h = size.height + 2 * b
    val radius = maxOf(w, h) / 2f
    // La pieza llena el centro: el color pleno hasta donde empieza el desvanecido.
    val inner = (minOf(size.width, size.height) / 2f / radius).coerceIn(0f, 0.9f)
    val c = Offset(size.width / 2f, size.height / 2f)
    val brush = Brush.radialGradient(0f to color, inner to color.copy(alpha = color.alpha * 0.55f), 1f to Color.Transparent, center = c, radius = radius)
    onDrawBehind {
        scale(w / (2 * radius), h / (2 * radius), pivot = c) { drawCircle(brush, radius, c) }
    }
}

/** Pozo redondo de un ícono (`.chip .well`): luz arriba a la izquierda y borde tenue. */
@Composable
fun IconWell(icon: ImageVector, tint: Color, size: Dp = 40.dp, iconSize: Dp = 24.dp, modifier: Modifier = Modifier) {
    val ice = LocalIce.current
    val light = if (ice.dark) Color.White else Color(0xFF6A9BD0)
    Box(
        modifier
            .size(size)
            .drawWithCache {
                val brush = Brush.radialGradient(
                    0f to light.copy(alpha = 0.14f), 1f to light.copy(alpha = 0.03f),
                    center = Offset(this.size.width * 0.35f, this.size.height * 0.30f),
                    // `circle at 35% 30%` llega hasta la esquina más lejana.
                    radius = Offset(this.size.width * 0.65f, this.size.height * 0.70f).getDistance(),
                )
                onDrawBehind { drawCircle(brush) }
            }
            .border(1.dp, ice.line2, CircleShape),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, tint, iconSize) }
}

/** Botón redondo de 44 dp (`.iconbtn`): fondo hielo muy tenue y borde fino. */
@Composable
fun IceIconButton(
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = IceDims.iconButton,
    iconSize: Dp = 24.dp,
    tint: Color = LocalIce.current.ink2,
) {
    val ice = LocalIce.current
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(if (ice.dark) Color(170, 210, 255).copy(alpha = 0.06f) else Color.White.copy(alpha = 0.6f))
            .border(1.dp, ice.line2, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, tint, iconSize) }
}

/**
 * Flecha de página (`.arrow`): 44 dp, vidrio fuerte y el chevrón de trazo 2. [left] = ‹.
 */
@Composable
fun PageArrow(left: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val ice = LocalIce.current
    val fill = if (ice.dark) listOf(0f to Color(170, 210, 255).copy(alpha = 0.14f), 1f to Color(110, 150, 210).copy(alpha = 0.05f)) else ice.glassStrong
    Box(
        modifier
            .size(IceDims.iconButton)
            .glass(fill, ice.line, Color.Transparent, IceDims.iconButton / 2)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(if (left) IceIcons.chevronLeft else IceIcons.chevronRight, if (ice.dark) Color(225, 240, 255).copy(alpha = 0.8f) else ice.ink2, 20.dp)
    }
}

/**
 * Las dos flechas de página a los lados, a 262 dp de arriba y 12 dp del borde (como en todos los
 * mockups). Va dentro del lienzo de 1024 × 600; null = esa flecha no se ve.
 */
@Composable
fun BoxScope.PageArrows(onPrevious: (() -> Unit)?, onNext: (() -> Unit)?) {
    if (onPrevious != null) PageArrow(true, onPrevious, Modifier.align(Alignment.TopStart).offset(12.dp, 262.dp))
    if (onNext != null) PageArrow(false, onNext, Modifier.align(Alignment.TopEnd).offset((-12).dp, 262.dp))
}

/** Los puntos de página (`.pager`): 6 dp, el de ahora de 20 dp con degradado hielo y brillo. */
@Composable
fun PageDots(count: Int, current: Int, modifier: Modifier = Modifier) {
    val ice = LocalIce.current
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(count) { i ->
            val on = i == current
            Box(
                Modifier
                    .then(if (on) Modifier.softGlow(ice.iceGlow.copy(alpha = ice.iceGlow.alpha * 0.6f), 6.dp) else Modifier)
                    .size(if (on) 20.dp else 6.dp, 6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(
                        if (on) Brush.horizontalGradient(listOf(Color(0xFFD8F0FF), Color(0xFF7CC6FF)))
                        else Brush.linearGradient(listOf(DotOff(ice), DotOff(ice))),
                    ),
            )
        }
    }
}

private fun DotOff(ice: IcePalette) = if (ice.dark) Color(200, 225, 255).copy(alpha = 0.25f) else Color(40, 80, 130).copy(alpha = 0.22f)

/**
 * El título de marca (`.brand`): la barrita hielo con brillo y el texto en mayúsculas espaciadas.
 */
@Composable
fun BrandTitle(text: String, modifier: Modifier = Modifier) {
    val ice = LocalIce.current
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .softGlow(ice.iceGlow.copy(alpha = 0.35f), 6.dp)
                .size(3.dp, 15.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Brush.verticalGradient(listOf(Color(0xFFD8F0FF), Color(0xFF6FBFFF)))),
        )
        Spacer(Modifier.width(10.dp))
        IceText(text.uppercase(), IceType.brand, if (ice.dark) Color(232, 242, 255).copy(alpha = 0.86f) else ice.ink, maxLines = 1)
    }
}

/** Rótulo de sección: mayúsculas espaciadas y tenues (lo de arriba de cada grupo). */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier, color: Color = LocalIce.current.ink3, style: TextStyle = IceType.section) {
    IceText(text.uppercase(), style, color, maxLines = 1, modifier = modifier)
}

/** La ubicación (`.loc`): punto menta con brillo y el nombre en mayúsculas. */
@Composable
fun LocationLabel(text: String, modifier: Modifier = Modifier) {
    val ice = LocalIce.current
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.softGlow(ice.mint.copy(alpha = 0.5f), 5.dp).size(7.dp).clip(CircleShape).background(ice.mint))
        Spacer(Modifier.width(8.dp))
        IceText(text.uppercase(), IceType.location, ice.ink2, maxLines = 1)
    }
}

/**
 * Ficha de vidrio con pozo e ícono, título y renglón chico (`.chip` de destinos). [width] fija como
 * en el diseño; sin [subtitle], el título va solo.
 */
@Composable
fun IceChip(
    title: String,
    subtitle: String?,
    icon: ImageVector,
    tint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val ice = LocalIce.current
    GlassCard(modifier.height(72.dp), radius = IceDims.chipRadius, onClick = onClick) {
        Row(Modifier.padding(start = 14.dp, end = 4.dp).align(Alignment.CenterStart), verticalAlignment = Alignment.CenterVertically) {
            IconWell(icon, tint)
            Spacer(Modifier.width(12.dp))
            Column {
                IceText(title, IceType.chipTitle, ice.ink, maxLines = 1)
                if (subtitle != null) IceText(subtitle, IceType.chipSub, ice.ink2, maxLines = 1, modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}

/** Ficha angosta con el ícono arriba y el texto abajo (`.chip.v`, Favorito); sin texto, solo el ícono (`.chip.q`). */
@Composable
fun IceIconChip(icon: ImageVector, tint: Color, label: String?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val ice = LocalIce.current
    GlassCard(modifier.height(72.dp), radius = IceDims.chipRadius, onClick = onClick) {
        Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, tint, 24.dp)
            if (label != null) IceText(label, IceType.small, ice.ink, maxLines = 1, modifier = Modifier.padding(top = 6.dp))
        }
    }
}

/**
 * Píldora genérica: alto [height], esquinas redondas por completo, fondo [background] y borde
 * [border] (el pico y placa, etiquetas de estado).
 */
@Composable
fun Pill(
    modifier: Modifier = Modifier,
    height: Dp = 28.dp,
    background: Color = LocalIce.current.line2,
    border: Color = LocalIce.current.line,
    content: @Composable RowScope.() -> Unit,
) {
    val shape = RoundedCornerShape(height / 2)
    Row(
        modifier.height(height).clip(shape).background(background).border(1.dp, border, shape),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

/** Separador vertical fino (entre datos de una píldora, o en el dock). */
@Composable
fun VDivider(height: Dp, color: Color = LocalIce.current.line, modifier: Modifier = Modifier) {
    Box(modifier.size(1.dp, height).drawBehind { drawRect(color) })
}
