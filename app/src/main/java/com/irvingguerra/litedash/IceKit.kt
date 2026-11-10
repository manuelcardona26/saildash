// SailDash 1.1 · diseño C: las piezas de Ajustes que comparten Taller, Tu carro y Sistema (botones,
// pestañas, interruptor, tarjetas, el menú de Ajustes en sus tres formas, el dock y el teclado de números).
package com.irvingguerra.litedash

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.irvingguerra.litedash.core.ui.Icon
import com.irvingguerra.litedash.core.ui.ice.DockClock
import com.irvingguerra.litedash.core.ui.ice.IceBackdrop
import com.irvingguerra.litedash.core.ui.ice.IceDock
import com.irvingguerra.litedash.core.ui.ice.IceFrame
import com.irvingguerra.litedash.core.ui.ice.IceIcons
import com.irvingguerra.litedash.core.ui.ice.IceText
import com.irvingguerra.litedash.core.ui.ice.LocalIce
import com.irvingguerra.litedash.core.ui.ice.Outfit
import com.irvingguerra.litedash.core.ui.ice.RobotoText
import com.irvingguerra.litedash.core.ui.ice.cssLinearGradient
import com.irvingguerra.litedash.core.ui.ice.glass
import com.irvingguerra.litedash.core.ui.ice.softGlow
import com.irvingguerra.litedash.feature.apps.rememberApps

/** Colores sueltos de los mockups de Ajustes (estados, acentos). */
object Kc {
    val mint = Color(0xFF8FF0CF)
    val warn = Color(0xFFFFC66B)
    val bad = Color(0xFFFF7D73)
    val gold = Color(0xFFFFD88A)
    val coral = Color(0xFFFF8F86)
    val rose = Color(0xFFFF9FB4)
    val ice = Color(0xFFA9DCFF)
    val iceDeep = Color(0xFF6FBFFF)
    val white = Color.White
    val inkSoft = Color(222, 236, 252).copy(alpha = 0.72f)
    /** Lo de abajo de las ventanas (el vidrio solo deja ver lo de atrás). */
    val sheetBase = Color(0xFF0A111D)
}

/** Un texto con la caja de CSS (line-height en [lh]); [display] = Outfit (si no, Roboto). */
fun ts(size: Float, weight: Int = 400, display: Boolean = true, spacing: Float = 0f, lh: Float = 1f, align: TextAlign = TextAlign.Unspecified) = TextStyle(
    fontFamily = if (display) Outfit else RobotoText,
    fontWeight = FontWeight(weight),
    fontSize = size.sp,
    letterSpacing = spacing.em,
    lineHeight = lh.em,
    textAlign = align,
    lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
    platformStyle = PlatformTextStyle(includeFontPadding = false),
)

/** Pone algo en [x],[y] (dp del lienzo o de su caja) con su tamaño, como `position:absolute`. */
fun Modifier.at(x: Number, y: Number, w: Number? = null, h: Number? = null): Modifier {
    var m = offset(x.toFloat().dp, y.toFloat().dp)
    if (w != null && h != null) m = m.size(w.toFloat().dp, h.toFloat().dp)
    else if (w != null) m = m.width(w.toFloat().dp)
    else if (h != null) m = m.height(h.toFloat().dp)
    return m
}

/**
 * Ícono a partir del SVG del mockup tal cual (`<path d>`, `<circle>`, `<rect>`; con `fill="currentColor"`
 * también se rellena). Se arma una vez (en un `val`): así copiar un ícono del HTML es pegar su SVG.
 */
fun svgIcon(svg: String, stroke: Float = 1.7f): ImageVector {
    val b = ImageVector.Builder(defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
    val black = SolidColor(Color.Black)
    val tag = Regex("<(path|circle|rect|ellipse)\\s([^>]*)/?>")
    fun attr(a: String, name: String) = Regex("(?<![\\w-])$name=\"([^\"]*)\"").find(a)?.groupValues?.get(1)
    fun num(a: String, name: String) = attr(a, name)?.toFloatOrNull() ?: 0f
    for (m in tag.findAll(svg)) {
        val a = m.groupValues[2]
        val d = when (m.groupValues[1]) {
            "path" -> attr(a, "d") ?: continue
            "circle" -> IceIcons.circle(num(a, "cx"), num(a, "cy"), num(a, "r"))
            "ellipse" -> {
                val cx = num(a, "cx"); val cy = num(a, "cy"); val rx = num(a, "rx"); val ry = num(a, "ry")
                "M${cx - rx} ${cy}a$rx $ry 0 1 0 ${2 * rx} 0a$rx $ry 0 1 0 ${-2 * rx} 0z"
            }
            else -> IceIcons.rect(num(a, "x"), num(a, "y"), num(a, "width"), num(a, "height"), attr(a, "rx")?.toFloatOrNull() ?: 0f)
        }
        val filled = attr(a, "fill") == "currentColor"
        val noStroke = attr(a, "stroke") == "none"
        val sw = attr(a, "stroke-width")?.toFloatOrNull() ?: stroke
        b.addPath(
            addPathNodes(d),
            fill = if (filled) black else null,
            stroke = if (noStroke) null else black,
            strokeLineWidth = sw, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
        )
    }
    return b.build()
}

/** El punto de estado (`.kdot`): 8 dp con su brillo. */
@Composable
fun KDot(color: Color, size: Dp = 8.dp, modifier: Modifier = Modifier) {
    Box(modifier.softGlow(color.copy(alpha = 0.55f), 5.dp).size(size).clip(CircleShape).background(color))
}

/** Rótulo en mayúsculas espaciadas (`.lbl` / `.cap`): Outfit 500 13 con 0.2em. */
@Composable
fun CapLabel(text: String, modifier: Modifier = Modifier, spacing: Float = 0.2f, color: Color = LocalIce.current.ink3, size: Float = 13f) {
    IceText(text.uppercase(currentLocale()), ts(size, 500, spacing = spacing), color, modifier = modifier)
}

/** Las formas de botón de los mockups de Ajustes. */
enum class BtnKind {
    /** `.btn`: vidrio hielo tenue con borde fino. */
    PLAIN,
    /** `.btn.pri` del Taller: degradado azul hielo con borde claro. */
    BLUE,
    /** `.btn.pri` de Sistema y Tu carro: blanco hielo con letra oscura y halo. */
    LIGHT,
    /** `.btn.danger`: coral. */
    DANGER,
    /** `.btn.sec`: letra hielo con borde hielo. */
    SEC,
    /** `.btn.ghost`: tenue. */
    GHOST,
}

/** Botón redondeado de Ajustes; [height] 44 o 48 según el mockup. */
@Composable
fun IceBtn(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    kind: BtnKind = BtnKind.PLAIN,
    icon: ImageVector? = null,
    height: Dp = 44.dp,
    fontSize: Float = 15f,
    enabled: Boolean = true,
    padding: Dp = 20.dp,
) {
    val shape = RoundedCornerShape(height / 2)
    val (fill, border, ink) = when (kind) {
        BtnKind.PLAIN -> Triple(Brush.linearGradient(listOf(Color(170, 210, 255, 18), Color(170, 210, 255, 18))), LocalIce.current.line, Color(0xFFEAF4FF))
        BtnKind.BLUE -> Triple(null, Color(170, 215, 255).copy(alpha = 0.40f), Color.White)
        BtnKind.LIGHT -> Triple(Brush.verticalGradient(listOf(Color.White, Color(0xFFCDEAFF))), Color(220, 242, 255).copy(alpha = 0.9f), Color(0xFF0A1322))
        BtnKind.DANGER -> Triple(null, Color(255, 125, 115).copy(alpha = 0.42f), Color(0xFFFFD3CF))
        BtnKind.SEC -> Triple(Brush.linearGradient(listOf(Color(170, 210, 255, 13), Color(170, 210, 255, 13))), Kc.ice.copy(alpha = 0.32f), Kc.ice)
        BtnKind.GHOST -> Triple(Brush.linearGradient(listOf(Color(170, 210, 255, 8), Color(170, 210, 255, 8))), LocalIce.current.line2, LocalIce.current.ink2)
    }
    var m = modifier
    if (kind == BtnKind.LIGHT) m = m.softGlow(Color(170, 220, 255).copy(alpha = 0.30f), 12.dp)
    m = m.height(height).clip(shape)
    m = when (kind) {
        BtnKind.BLUE -> m.drawWithCache {
            val b = cssLinearGradient(135f, size, listOf(0f to Color(120, 190, 255).copy(alpha = 0.34f), 1f to Color(90, 150, 235).copy(alpha = 0.14f)))
            onDrawBehind { drawRect(b); drawRect(Color(235, 246, 255).copy(alpha = 0.2f), size = Size(size.width, 1.dp.toPx())) }
        }
        BtnKind.DANGER -> m.drawWithCache {
            val b = cssLinearGradient(135f, size, listOf(0f to Color(255, 110, 100).copy(alpha = 0.20f), 1f to Color(255, 110, 100).copy(alpha = 0.06f)))
            onDrawBehind { drawRect(b) }
        }
        else -> m.background(fill!!)
    }
    m = m.border(1.dp, border, shape)
    if (enabled) m = m.clickable(onClick = onClick)
    Row(m.padding(horizontal = padding), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
        if (icon != null) {
            Icon(icon, ink.copy(alpha = if (enabled) ink.alpha else 0.4f), 20.dp)
            Spacer(Modifier.width(9.dp))
        }
        IceText(text, ts(fontSize, 500), ink.copy(alpha = if (enabled) ink.alpha else 0.4f))
    }
}

/** El interruptor (`.sw`/`.tg`): 54 × 32, azul hielo prendido. */
@Composable
fun IceSwitch(on: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val ice = LocalIce.current
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier
            .then(if (on) Modifier.softGlow(Color(120, 195, 255).copy(alpha = 0.35f), 8.dp) else Modifier)
            .size(54.dp, 32.dp)
            .clip(shape)
            .background(if (on) Brush.horizontalGradient(listOf(Color(0xFF4AA6F0), Color(0xFF8FD0FF))) else SolidColor(Color(190, 225, 255).copy(alpha = 0.14f)))
            .border(1.dp, if (on) Color(200, 236, 255).copy(alpha = 0.7f) else ice.line, shape)
            .clickable { onChange(!on) },
    ) {
        Box(
            Modifier.offset(if (on) 25.dp else 3.dp, 3.dp).size(24.dp).clip(CircleShape)
                .background(if (on) Color.White else Color(220, 235, 252).copy(alpha = 0.7f)),
        )
    }
}

/** Las dos formas de las pestañas (`.seg`): la del Taller (48 dp, Outfit) y la de la hoja de Ajustes (44 dp, Roboto). */
enum class SegStyle { TALLER, SHEET }

/** Pestañas en una píldora; [onSelect] con el índice. */
@Composable
fun IceSeg(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier, style: SegStyle = SegStyle.TALLER, itemPadding: Dp? = null, equal: Boolean = false) {
    val ice = LocalIce.current
    val h = if (style == SegStyle.TALLER) 48.dp else 44.dp
    val pad = if (style == SegStyle.TALLER) 4.dp else 3.dp
    val r = h / 2
    val bg = if (style == SegStyle.TALLER) Color(150, 195, 245).copy(alpha = 0.05f) else Color(6, 12, 22).copy(alpha = 0.45f)
    val border = if (style == SegStyle.TALLER) ice.line2 else ice.line
    Row(
        modifier.height(h).clip(RoundedCornerShape(r)).background(bg).border(1.dp, border, RoundedCornerShape(r)).padding(pad),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        options.forEachIndexed { i, label ->
            val on = i == selected
            val ir = r - pad
            var m: Modifier = if (equal) Modifier.weight(1f) else Modifier
            m = m.fillMaxHeight().clip(RoundedCornerShape(ir))
            if (on) m = m.segOn(ir)
            Box(
                m.clickable { onSelect(i) }.padding(horizontal = itemPadding ?: if (style == SegStyle.TALLER) 18.dp else 16.dp),
                contentAlignment = Alignment.Center,
            ) {
                val st = if (style == SegStyle.TALLER) ts(15f) else ts(14f, display = false)
                IceText(label, st, if (on) Color.White else if (style == SegStyle.TALLER) Kc.inkSoft else ice.ink2)
            }
        }
    }
}

/** El relleno de lo elegido (`span.on`, `.it.on`, `.op.on`): degradado hielo a 135°, borde de luz por dentro y reflejo arriba. */
fun Modifier.segOn(radius: Dp, alpha: Float = 0.30f, border: Float = 0.34f, sheen: Float = 0.18f): Modifier = drawWithCache {
    val b = cssLinearGradient(135f, size, listOf(0f to Color(120, 190, 255).copy(alpha = alpha), 1f to Color(90, 150, 235).copy(alpha = alpha * 0.4f)))
    val r = CornerRadius(radius.toPx())
    val px = 1.dp.toPx()
    onDrawBehind {
        drawRoundRect(b, cornerRadius = r)
        drawRoundRect(Color(170, 215, 255).copy(alpha = border), Offset(px / 2, px / 2), Size(size.width - px, size.height - px), r, style = androidx.compose.ui.graphics.drawscope.Stroke(px))
        drawRect(Color(235, 246, 255).copy(alpha = sheen), Offset(radius.toPx() * 0.6f, px), Size(size.width - radius.toPx() * 1.2f, px))
    }
}

/** Una tarjeta de vidrio en [x],[y] de su caja, con [w]×[h] (`.card.glass`); [hi] = la resaltada. */
@Composable
fun IceCard(
    modifier: Modifier = Modifier,
    radius: Dp = 18.dp,
    hi: Boolean = false,
    tint: Color? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val ice = LocalIce.current
    val fill = when {
        tint != null -> listOf(0f to tint.copy(alpha = 0.11f), 1f to tint.copy(alpha = 0.02f))
        hi -> listOf(0f to Color(150, 205, 255).copy(alpha = 0.17f), 0.6f to Color(110, 160, 230).copy(alpha = 0.07f), 1f to Color(90, 130, 190).copy(alpha = 0.04f))
        else -> ice.glass
    }
    val border = when {
        tint != null -> tint.copy(alpha = 0.32f)
        hi -> Color(170, 215, 255).copy(alpha = 0.42f)
        else -> ice.line
    }
    Box(modifier.glass(fill, border, ice.sheen, radius), content = content)
}

/** Una tarjeta chica de dato (`.card` del Taller): degradado más tenue y borde `--line-2`. */
@Composable
fun IceTile(modifier: Modifier = Modifier, tint: Color? = null, onClick: (() -> Unit)? = null, content: @Composable BoxScope.() -> Unit) {
    val ice = LocalIce.current
    val fill = if (tint != null) listOf(0f to tint.copy(alpha = 0.10f), 1f to tint.copy(alpha = 0.02f))
    else listOf(0f to Color(170, 210, 255).copy(alpha = 0.08f), 1f to Color(120, 160, 215).copy(alpha = 0.03f))
    var m = modifier.glass(fill, tint?.copy(alpha = 0.30f) ?: ice.line2, Color.Transparent, 16.dp)
    if (onClick != null) m = m.clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick)
    Box(m, content = content)
}

/** Pozo redondo con ícono (`.well`): 44 dp, luz arriba a la izquierda. */
@Composable
fun Well(icon: ImageVector, tint: Color = Kc.ice, size: Dp = 44.dp, iconSize: Dp = 22.dp, modifier: Modifier = Modifier) {
    val ice = LocalIce.current
    Box(
        modifier.size(size).drawWithCache {
            val brush = Brush.radialGradient(
                0f to Color.White.copy(alpha = 0.14f), 1f to Color.White.copy(alpha = 0.03f),
                center = Offset(this.size.width * 0.35f, this.size.height * 0.30f), radius = this.size.width * 0.75f,
            )
            onDrawBehind { drawCircle(brush) }
        }.border(1.dp, ice.line2, CircleShape),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, tint, iconSize) }
}

/** Ficha chica de estado (`.st`, "Falta enseñarle", "Hoy"): fondo de su color y punto opcional. */
@Composable
fun Tag(text: String, color: Color, modifier: Modifier = Modifier, dot: Boolean = true, height: Dp = 26.dp, fontSize: Float = 13f, fill: Float = 0.10f, borderAlpha: Float = 0.35f, bg: Color? = null) {
    val shape = RoundedCornerShape(height / 2)
    Row(
        modifier.height(height).clip(shape).background(bg ?: color.copy(alpha = fill)).border(1.dp, color.copy(alpha = borderAlpha), shape).padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (dot) {
            Box(Modifier.size(7.dp).clip(CircleShape).background(color))
            Spacer(Modifier.width(6.dp))
        }
        IceText(text, ts(fontSize, 500, display = false), color)
    }
}

/** Línea fina horizontal (`border-top: 1px solid var(--line-2)`). */
@Composable
fun HLine(modifier: Modifier = Modifier, color: Color = LocalIce.current.line2) {
    Box(modifier.fillMaxWidth().height(1.dp).background(color))
}

/** Botón redondo de 44 con borde (volver, recargar). */
@Composable
fun RoundBtn(icon: ImageVector, onClick: () -> Unit, modifier: Modifier = Modifier, tint: Color = LocalIce.current.ink2, size: Dp = 44.dp, iconSize: Dp = 20.dp, border: Color = LocalIce.current.line2, fill: Color = Color(170, 210, 255).copy(alpha = 0.06f)) {
    Box(
        modifier.size(size).clip(CircleShape).background(fill).border(1.dp, border, CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, tint, iconSize) }
}

/** Los íconos de Ajustes que no están en IceIcons (copiados de los mockups del catálogo). */
object SetIcons {
    // Menú, forma del Taller (taller-*.html).
    val carA = svgIcon("""<path d="M3.5 15.5v-3l2.2-4.6A2 2 0 0 1 7.5 6.8h9a2 2 0 0 1 1.8 1.1l2.2 4.6v3a1 1 0 0 1-1 1h-15a1 1 0 0 1-1-1z"/><path d="M3.5 12.5h17"/><path d="M6 16.5v1.8M18 16.5v1.8"/><circle cx="7.5" cy="14.3" r=".6"/><circle cx="16.5" cy="14.3" r=".6"/>""")
    val layoutA = svgIcon("""<rect x="3.5" y="4" width="17" height="16" rx="2.4"/><path d="M3.5 9.5h17M10 9.5V20"/>""")
    val soundA = svgIcon("""<path d="M4 9.5h3.2L12 5.5v13l-4.8-4H4z"/><path d="M15.5 9a4.2 4.2 0 0 1 0 6M18.2 6.5a8 8 0 0 1 0 11"/>""")
    val wrenchA = svgIcon("""<path d="M14.7 6.3a4 4 0 0 0 5 5L12 19a2.1 2.1 0 0 1-3-3z" /><path d="M14.7 6.3 17.5 3.5l.5 2.5 2.5.5-2.8 2.8"/>""")
    val wheelA = svgIcon("""<circle cx="12" cy="12" r="8.5"/><circle cx="12" cy="12" r="2.3"/><path d="M3.8 10.5c3 .8 5.2.8 6 1.5M20.2 10.5c-3 .8-5.2.8-6 1.5M12 14.3v6"/>""")
    val modulesA = svgIcon("""<path d="M4 7h10M18 7h2M4 17h4M12 17h8"/><circle cx="16" cy="7" r="2.2"/><circle cx="10" cy="17" r="2.2"/>""")
    val chipA = svgIcon("""<rect x="6" y="6" width="12" height="12" rx="2"/><path d="M9.5 9.5h5v5h-5z"/><path d="M9 3.5V6M15 3.5V6M9 18v2.5M15 18v2.5M3.5 9H6M3.5 15H6M18 9h2.5M18 15h2.5"/>""")

    // Menú, forma de Tu carro (reversa, pico-y-placa, recordatorios).
    val carB = svgIcon("""<path d="M4 16.5V12l2-5.2A1.8 1.8 0 0 1 7.7 5.6h8.6a1.8 1.8 0 0 1 1.7 1.2L20 12v4.5"/><path d="M3.5 12h17v4.5a1 1 0 0 1-1 1H4.5a1 1 0 0 1-1-1z"/><path d="M5.5 17.5v2M18.5 17.5v2"/><circle cx="7.5" cy="14.6" r=".9"/><circle cx="16.5" cy="14.6" r=".9"/>""")
    val brushB = svgIcon("""<path d="M4 20.5c3.2 0 5-1.6 5-4a2.6 2.6 0 0 0-2.6-2.6C4.6 13.9 4 15.6 4 17.3z"/><path d="M9.4 14.6 19.6 4.4a1.4 1.4 0 0 1 2 2L11.4 16.6"/>""")
    val wrenchB = svgIcon("""<path d="M14.7 6.3a1 1 0 0 0 0 1.4l1.6 1.6a1 1 0 0 0 1.4 0l3.8-3.8a6 6 0 0 1-7.9 7.9l-6.9 6.9a2.1 2.1 0 0 1-3-3l6.9-6.9a6 6 0 0 1 7.9-7.9z"/>""")
    val wheelB = svgIcon("""<circle cx="12" cy="12" r="8.5"/><circle cx="12" cy="12" r="2.4"/><path d="M3.8 10.5c2.7-.6 5.4-.9 8.2-.9s5.5.3 8.2.9M10 14l-3 6.4M14 14l3 6.4"/>""")
    val modulesB = svgIcon("""<rect x="3.5" y="3.5" width="7" height="7" rx="1.6"/><rect x="13.5" y="3.5" width="7" height="7" rx="1.6"/><rect x="3.5" y="13.5" width="7" height="7" rx="1.6"/><path d="M17 13.5v7M13.5 17h7"/>""")
    val chipB = svgIcon("""<rect x="6" y="6" width="12" height="12" rx="2"/><rect x="9.5" y="9.5" width="5" height="5" rx=".8"/><path d="M9.5 3v3M14.5 3v3M9.5 18v3M14.5 18v3M3 9.5h3M3 14.5h3M18 9.5h3M18 14.5h3"/>""")

    // Menú, forma de la hoja (ajustes, diagnostico, actualizaciones, mandos-volante).
    val carC = svgIcon("""<path d="M5.5 12.5 7.2 8a2 2 0 0 1 1.9-1.3h5.8A2 2 0 0 1 16.8 8l1.7 4.5"/><rect x="3.5" y="12.5" width="17" height="5" rx="1.6"/><path d="M6 17.5v2M18 17.5v2"/><circle cx="7.4" cy="15" r=".6"/><circle cx="16.6" cy="15" r=".6"/>""")
    val paletteC = svgIcon("""<path d="M12 3.5a8.5 8.5 0 1 0 0 17c1.2 0 1.8-.8 1.8-1.7 0-1.3-1-1.6-1-2.6 0-.9.7-1.5 1.6-1.5h2a3.6 3.6 0 0 0 3.6-3.6c0-4.2-3.6-7.6-8-7.6z"/><circle cx="7.8" cy="11.2" r="1"/><circle cx="10.6" cy="7.5" r="1"/><circle cx="15.1" cy="7.9" r="1"/>""")
    val soundC = svgIcon("""<path d="M4 9.5h3.5L12 5.5v13l-4.5-4H4z"/><path d="M15.5 9a4 4 0 0 1 0 6M18 6.5a7.5 7.5 0 0 1 0 11"/>""")
    val wrenchC = svgIcon("""<path d="M14.6 6.4a1 1 0 0 0 0 1.4l1.6 1.6a1 1 0 0 0 1.4 0l3.4-3.4a5.6 5.6 0 0 1-7.4 7.4l-6.5 6.5a2 2 0 0 1-2.9-2.9l6.5-6.5a5.6 5.6 0 0 1 7.4-7.4z"/>""")
    val wheelC = svgIcon("""<circle cx="12" cy="12" r="8.5"/><circle cx="12" cy="12" r="2.2"/><path d="M3.7 10.6c2.7-.7 5.4-1 8.3-1s5.6.3 8.3 1"/><path d="M10.4 13.8 8 20M13.6 13.8 16 20"/>""")
    val chipC = svgIcon("""<rect x="5.5" y="5.5" width="13" height="13" rx="2.5"/><rect x="9.25" y="9.25" width="5.5" height="5.5" rx="1"/><path d="M9.5 2.8v2.7M14.5 2.8v2.7M9.5 18.5v2.7M14.5 18.5v2.7M2.8 9.5h2.7M2.8 14.5h2.7M18.5 9.5h2.7M18.5 14.5h2.7"/>""")
    val tire = svgIcon("""<circle cx="12" cy="12" r="8.5"/><circle cx="12" cy="12" r="3.5"/><path d="M12 3.5v5M12 15.5v5M3.5 12h5M15.5 12h5"/>""")

    // Sueltos.
    val back = svgIcon("""<path d="M14.5 6 8.5 12l6 6"/>""", stroke = 2f)
    val chevronRight = svgIcon("""<path d="M9.5 6l6 6-6 6"/>""")
    val chevronDown = svgIcon("""<path d="M6.5 9.5 12 15l5.5-5.5"/>""")
    val refresh = svgIcon("""<path d="M19.5 11A7.5 7.5 0 0 0 6 6.6L4.5 8"/><path d="M4.5 3.8V8h4.2"/><path d="M4.5 13A7.5 7.5 0 0 0 18 17.4l1.5-1.4"/><path d="M19.5 20.2V16h-4.2"/>""")
    val reset = svgIcon("""<path d="M4.5 12a7.5 7.5 0 1 0 2.2-5.3"/><path d="M4.2 4.5v4.2h4.2"/>""")
    val history = svgIcon("""<path d="M4.5 12a7.5 7.5 0 1 0 2.2-5.3"/><path d="M4.2 4.5v4.2h4.2"/><path d="M12 8v4.3l2.8 1.7"/>""")
    val check = svgIcon("""<path d="M5 12.5l4.5 4.5L19 7.5"/>""", stroke = 2f)
    val plus = svgIcon("""<path d="M12 5v14M5 12h14"/>""")
    val close = svgIcon("""<path d="M6 6l12 12M18 6 6 18"/>""")
    val trash = svgIcon("""<path d="M4.5 7h15"/><path d="M9.5 7V5a1 1 0 0 1 1-1h3a1 1 0 0 1 1 1v2"/><path d="M6.5 7l.9 12a1.5 1.5 0 0 0 1.5 1.4h6.2a1.5 1.5 0 0 0 1.5-1.4l.9-12"/>""")
    val warn = svgIcon("""<path d="M12 4 21 19.5H3z"/><path d="M12 10v4.5"/><circle cx="12" cy="17" r=".5"/>""")
    val info = svgIcon("""<circle cx="12" cy="12" r="8.5"/><path d="M12 11v5"/><circle cx="12" cy="8" r=".6"/>""")
    val timer = svgIcon("""<circle cx="12" cy="13" r="7.5"/><path d="M12 9v4l2.5 2"/><path d="M9.5 3h5"/>""")
    val download = svgIcon("""<path d="M12 4v11"/><path d="M7.5 10.5 12 15l4.5-4.5"/><path d="M4.5 19.5h15"/>""")
    val usb = svgIcon("""<rect x="7.5" y="9.5" width="9" height="11" rx="2"/><path d="M9.5 9.5V3.5h5v6"/><path d="M11 6.2h.01M13 6.2h.01"/>""")
    val shield = svgIcon("""<path d="M12 3.5 5 6v5.5c0 4.4 3 7.6 7 9 4-1.4 7-4.6 7-9V6z"/><path d="M9 12l2.2 2.2L15.5 10"/>""")
    val camera = svgIcon("""<path d="M4 8.5a2 2 0 0 1 2-2h2l1.5-2h5L16 6.5h2a2 2 0 0 1 2 2v9a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2z"/><circle cx="12" cy="13" r="3.6"/>""")
    val engine = svgIcon("""<path d="M3.5 10.5v5M3.5 13h2.5M6 9.5h2.5V7.5h6v2H17l1.5 2h2v4h-2L17 17.5H8.5L6 15z"/><path d="M9.5 7.5V5.5h4"/>""")
    val odometer = svgIcon("""<path d="M4.6 17a8.4 8.4 0 1 1 14.8 0"/><path d="M12 13.6l3.6-4.2"/><circle cx="12" cy="14" r="1"/><path d="M8.5 17.5h7"/>""")
    val oil = svgIcon("""<path d="M3.5 10.5h4l1.5-2h6l5-2v2.5l-4 5.5H5a1.5 1.5 0 0 1-1.5-1.5z"/><path d="M19.5 15.5c0 .9-.6 1.5-1.2 1.5s-1.3-.6-1.3-1.5.6-1.8 1.3-2.8c.6 1 1.2 1.9 1.2 2.8z"/><path d="M7.5 8.5V6.5h3"/>""")
    val brake = svgIcon("""<circle cx="12" cy="12" r="5.5"/><circle cx="12" cy="12" r="1.6"/><path d="M5.2 6.3a9 9 0 0 0 0 11.4M18.8 6.3a9 9 0 0 1 0 11.4"/>""")
    val notebook = svgIcon("""<rect x="5" y="3.5" width="14" height="17" rx="2"/><path d="M9 3.5v17"/><path d="M12 8h4M12 11.5h4"/>""")
    val pin = svgIcon("""<path d="M12 20.5s-6.2-5.6-6.2-10.6a6.2 6.2 0 0 1 12.4 0c0 5-6.2 10.6-6.2 10.6z"/><circle cx="12" cy="9.9" r="2.2"/>""")
    val backspace = svgIcon("""<path d="M9 5.5h10.5a1.5 1.5 0 0 1 1.5 1.5v10a1.5 1.5 0 0 1-1.5 1.5H9L3.5 12z"/><path d="M11.5 9.5l5 5M16.5 9.5l-5 5"/>""")
    val calendar = svgIcon("""<rect x="3.5" y="5" width="17" height="15.5" rx="2.2"/><path d="M3.5 10h17M8 3v4M16 3v4"/>""")
    val bluetooth = svgIcon("""<path d="M7 7.5l10 9-5 4.5V3l5 4.5-10 9"/>""")
    val hand = svgIcon("""<path d="M8 13V6.5a1.5 1.5 0 0 1 3 0V12M11 11V5a1.5 1.5 0 0 1 3 0v6M14 11V6.5a1.5 1.5 0 0 1 3 0V14a6.5 6.5 0 0 1-6.5 6.5c-2.2 0-3.6-.9-4.8-2.6L3.6 15a1.5 1.5 0 0 1 2.4-1.8L8 15"/>""")

    // Reversa (reversa.html) y Recordatorios (recordatorios.html).
    // Sistema (diagnostico.html) y Mandos (mandos-volante.html).
    val monitor = svgIcon("""<rect x="3" y="4" width="18" height="12.5" rx="2.2"/><path d="M8.5 20.5h7M12 16.5v4"/>""")
    val split = svgIcon("""<rect x="3.5" y="5" width="17" height="14" rx="2.2"/><path d="M12 5v14"/>""")
    val sliders = svgIcon("""<path d="M6 3.8v6.6M6 15v5.2M12 3.8v2.2M12 10.6v9.6M18 3.8v9.6M18 18v2.2"/><circle cx="6" cy="12.7" r="2.3"/><circle cx="12" cy="8.3" r="2.3"/><circle cx="18" cy="15.7" r="2.3"/>""")
    val volUp = svgIcon("""<path d="M4 9.5h3.5L12 5.5v13l-4.5-4H4z"/><path d="M15.5 12h5M18 9.5v5"/>""")
    val volDown = svgIcon("""<path d="M4 9.5h3.5L12 5.5v13l-4.5-4H4z"/><path d="M15.5 12h5"/>""")
    val map = svgIcon("""<path d="M3.5 6.5 9 4l6 2.5L20.5 4v13.5L15 20l-6-2.5-5.5 2.5z"/><path d="M9 4v13.5M15 6.5V20"/>""")
    val mic = svgIcon("""<rect x="9" y="3.5" width="6" height="11" rx="3"/><path d="M5.5 11.5a6.5 6.5 0 0 0 13 0M12 18v2.5"/>""")
    val undo = svgIcon("""<path d="M9 14 4.5 9.5 9 5"/><path d="M4.5 9.5h10a5 5 0 0 1 0 10H11"/>""")
    val soundLower = svgIcon("""<path d="M4 9.5h3.5L12 5.5v13l-4.5-4H4z"/><path d="M15.5 9a4.2 4.2 0 0 1 0 6"/><path d="M18.5 12h2.5"/>""")
    val soundHalf = svgIcon("""<path d="M4 9.5h3.5L12 5.5v13l-4.5-4H4z"/><path d="M15.5 10.5a2.2 2.2 0 0 1 0 3"/>""")
    val soundMute = svgIcon("""<path d="M4 9.5h3.5L12 5.5v13l-4.5-4H4z"/><path d="M15.5 9.5l5 5M20.5 9.5l-5 5"/>""")
    val gearR = svgIcon("""<rect x="4" y="3.5" width="16" height="17" rx="3"/><path d="M9.5 16.5V7.5h3a2.6 2.6 0 0 1 0 5.2h-3M12.5 12.7l2.6 3.8"/>""")
    val mirror = svgIcon("""<path d="M12 3.5v17"/><path d="M9 7 4 12l5 5z"/><path d="M15 7l5 5-5 5z"/>""")
    val ruler = svgIcon("""<rect x="2.5" y="8" width="19" height="8" rx="1.6"/><path d="M6.5 8v3M10.5 8v4M14.5 8v3M18.5 8v4"/>""")
    val shieldR = svgIcon("""<path d="M12 3.2 19 6v5.6c0 4.4-2.9 8-7 9.2-4.1-1.2-7-4.8-7-9.2V6z"/><path d="M8.8 12.2l2.2 2.2 4.2-4.4"/>""")
    val oilR = svgIcon("""<path d="M3.5 10.5h3l2-2h6l5.5-2v3l-3 1.5-3.5 6H6.5a3 3 0 0 1-3-3z"/><path d="M20.5 13.5c0 1-.6 1.8-1.3 1.8s-1.3-.8-1.3-1.8.6-1.8 1.3-3c.7 1.2 1.3 2 1.3 3z"/>""")
    val gaugeR = svgIcon("""<path d="M4.2 16.5a8.5 8.5 0 1 1 15.6 0"/><path d="M12 14.5l4-4.5"/><circle cx="12" cy="14.5" r="1.4"/><path d="M6.8 11.5h1M12 6.6v1"/>""")
    val extinguisher = svgIcon("""<path d="M9 8.5h6v11a1.5 1.5 0 0 1-1.5 1.5h-3A1.5 1.5 0 0 1 9 19.5z"/><path d="M10.5 8.5V6.5h3v2M12 6.5V4.5h3.5l2.5-1.5M15.5 4.5 18 7"/><path d="M9 13h6"/>""")
    val receipt = svgIcon("""<path d="M6 3.5h12v17l-2-1.4-2 1.4-2-1.4-2 1.4-2-1.4-2 1.4z"/><path d="M9 8h6M9 11.5h6M9 15h3.5"/>""")
    val idCard = svgIcon("""<rect x="3" y="5.5" width="18" height="13" rx="2.2"/><circle cx="8.6" cy="11" r="2"/><path d="M5.6 16a3.2 3.2 0 0 1 6 0M14 10h4M14 13.5h3"/>""")
}

/** Las tres formas del menú de Ajustes que traen los mockups (cada pantalla trae la suya). */
enum class MenuStyle {
    /** taller-*: columna de vidrio de 226 dp a la izquierda. */
    TALLER,
    /** reversa, pico-y-placa, recordatorios: columna de 232 dp con el engranaje junto a "Ajustes". */
    CARRO,
    /** ajustes, diagnostico, actualizaciones, mandos-volante: una hoja grande con el menú adentro. */
    SHEET,
}

private val MenuSections = listOf(
    SettingsSection.CAR, SettingsSection.APPEARANCE, SettingsSection.SOUND, SettingsSection.APPS,
    SettingsSection.WORKSHOP, SettingsSection.WHEEL, SettingsSection.MODULES, SettingsSection.SYSTEM,
)

private fun SettingsSection.menuIcon(style: MenuStyle): ImageVector = when (style) {
    MenuStyle.TALLER -> when (this) {
        SettingsSection.CAR -> SetIcons.carA
        SettingsSection.APPEARANCE -> SetIcons.layoutA
        SettingsSection.SOUND -> SetIcons.soundA
        SettingsSection.APPS -> IceIcons.apps
        SettingsSection.WORKSHOP -> SetIcons.wrenchA
        SettingsSection.WHEEL -> SetIcons.wheelA
        SettingsSection.MODULES -> SetIcons.modulesA
        SettingsSection.TIRES -> SetIcons.tire
        SettingsSection.SYSTEM -> SetIcons.chipA
    }
    MenuStyle.CARRO -> when (this) {
        SettingsSection.CAR -> SetIcons.carB
        SettingsSection.APPEARANCE -> SetIcons.brushB
        SettingsSection.SOUND -> SetIcons.soundA
        SettingsSection.APPS -> IceIcons.apps
        SettingsSection.WORKSHOP -> SetIcons.wrenchB
        SettingsSection.WHEEL -> SetIcons.wheelB
        SettingsSection.MODULES -> SetIcons.modulesB
        SettingsSection.TIRES -> SetIcons.tire
        SettingsSection.SYSTEM -> SetIcons.chipB
    }
    MenuStyle.SHEET -> when (this) {
        SettingsSection.CAR -> SetIcons.carC
        SettingsSection.APPEARANCE -> SetIcons.paletteC
        SettingsSection.SOUND -> SetIcons.soundC
        SettingsSection.APPS -> IceIcons.apps
        SettingsSection.WORKSHOP -> SetIcons.wrenchC
        SettingsSection.WHEEL -> SetIcons.wheelC
        SettingsSection.MODULES -> SetIcons.modulesB
        SettingsSection.TIRES -> SetIcons.tire
        SettingsSection.SYSTEM -> SetIcons.chipC
    }
}

/**
 * Ajustes en el diseño C: el espacio de fondo, el menú de secciones ([style], la forma de su
 * mockup), el dock con Ajustes elegido y [content] encima, medido sobre el lienzo de 1024 × 600.
 * [extraSections] son las que salen además de las ocho del diseño (Llantas, con fuente elegida).
 */
@Composable
fun IceSettingsFrame(
    d: Dependencies,
    style: MenuStyle,
    section: SettingsSection,
    onSection: (SettingsSection) -> Unit,
    close: () -> Unit,
    onPanel: (Panel) -> Unit,
    extraSections: List<SettingsSection> = emptyList(),
    content: @Composable BoxScope.() -> Unit,
) {
    val ice = LocalIce.current
    Box(
        Modifier.fillMaxSize().background(ice.bg)
            // Atrapa los toques para que no lleguen al inicio de abajo.
            .clickable(remember { MutableInteractionSource() }, indication = null) {},
    ) {
        IceFrame(Modifier.safeDrawingPadding()) {
            IceBackdrop(R.drawable.bg_espacio)
            val sections = MenuSections + extraSections
            when (style) {
                MenuStyle.SHEET -> {
                    // .shade: el espacio más oscuro detrás de la hoja.
                    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(4, 7, 14).copy(alpha = 0.35f), Color(4, 7, 14).copy(alpha = 0.72f)))))
                    Box(
                        Modifier.at(24, 16, 976, 496).glass(
                            listOf(0f to Color(150, 195, 250).copy(alpha = 0.10f), 0.6f to Color(90, 130, 190).copy(alpha = 0.045f), 1f to Color(70, 110, 170).copy(alpha = 0.03f)),
                            ice.line, ice.sheen, 26.dp, angle = 170f,
                        ),
                    ) {
                        SheetMenu(sections, section, onSection)
                    }
                }
                else -> SideMenu(style, sections, section, onSection)
            }
            content()
            val (time, marker, date) = dockTime()
            val context = LocalContext.current
            val apps = rememberApps(d.apps).orEmpty()
            IceDock(
                entries = dockEntries(),
                active = "settings",
                onClick = { key ->
                    when (key) {
                        "settings" -> Unit
                        "home" -> close()
                        else -> onDock(context, d, apps, key, onPanel = { close(); onPanel(it) }, onExpand = { close() }, onHome = close)
                    }
                },
                modifier = Modifier.align(Alignment.BottomStart),
                clock = { DockClock(time, marker, date) },
            )
        }
    }
}


/** La columna de secciones de [MenuStyle.TALLER] y [MenuStyle.CARRO]. */
@Composable
private fun SideMenu(style: MenuStyle, sections: List<SettingsSection>, current: SettingsSection, onSection: (SettingsSection) -> Unit) {
    val ice = LocalIce.current
    val taller = style == MenuStyle.TALLER
    val w = if (taller) 226 else 232
    Box(Modifier.at(16, 16, w, 496).glass(ice.glass, ice.line, ice.sheen, 20.dp)) {
        Column(Modifier.fillMaxSize().padding(start = 10.dp, end = 10.dp, top = if (taller) 16.dp else 14.dp)) {
            if (taller) {
                IceText(stringResource(R.string.dock_settings), ts(25f, 300), Color.White, modifier = Modifier.padding(start = 14.dp, top = 2.dp).height(34.dp).wrapContentSize(Alignment.CenterStart))
                Spacer(Modifier.height(12.dp))
            } else {
                Row(Modifier.padding(start = 10.dp, end = 10.dp, top = 4.dp, bottom = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(IceIcons.settings, Kc.ice, 22.dp)
                    Spacer(Modifier.width(10.dp))
                    IceText(stringResource(R.string.dock_settings), ts(24f), Color.White)
                }
            }
            val itemH = if (taller) 50.dp else 48.dp
            for (s in sections) {
                val on = s == current
                Box(
                    Modifier.fillMaxWidth().height(itemH).clip(RoundedCornerShape(14.dp))
                        .then(if (on) Modifier.segOn(14.dp, 0.28f, if (taller) 0.32f else 0.30f, if (taller) 0.16f else 0.14f) else Modifier)
                        .clickable { onSection(s) },
                ) {
                    if (on) Box(
                        Modifier.align(Alignment.CenterStart).offset((-1).dp).size(3.dp, itemH - 26.dp).softGlow(ice.iceGlow.copy(alpha = 0.35f), 4.dp)
                            .clip(RoundedCornerShape(2.dp)).background(Brush.verticalGradient(listOf(Color(0xFFD8F0FF), Color(0xFF6FBFFF)))),
                    )
                    Row(Modifier.fillMaxSize().padding(horizontal = if (taller) 13.dp else 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(s.menuIcon(style), if (on) (if (taller) Color(0xFFD8F0FF) else Kc.ice) else ice.ink3, 22.dp)
                        Spacer(Modifier.width(if (taller) 13.dp else 12.dp))
                        val st = if (taller) ts(16f) else ts(15f, if (on) 500 else 400, display = false)
                        IceText(stringResource(s.menuTitle()), st, if (on) Color.White else if (taller) Color(226, 238, 252).copy(alpha = 0.80f) else Color(222, 236, 252).copy(alpha = 0.82f))
                    }
                }
                Spacer(Modifier.height(4.dp))
            }
        }
    }
}

/** El menú dentro de la hoja ([MenuStyle.SHEET]). */
@Composable
private fun BoxScope.SheetMenu(sections: List<SettingsSection>, current: SettingsSection, onSection: (SettingsSection) -> Unit) {
    val ice = LocalIce.current
    Box(
        Modifier.width(236.dp).fillMaxHeight().clip(RoundedCornerShape(topStart = 26.dp, bottomStart = 26.dp))
            .background(Brush.verticalGradient(listOf(Color(4, 8, 16).copy(alpha = 0.32f), Color(4, 8, 16).copy(alpha = 0.18f))))
            .drawBehind { drawRect(ice.line2, Offset(size.width - 1.dp.toPx(), 0f), Size(1.dp.toPx(), size.height)) },
    ) {
        Column(Modifier.fillMaxSize().padding(start = 14.dp, end = 12.dp, top = 22.dp)) {
            IceText(stringResource(R.string.dock_settings), ts(28f, 300, spacing = 0.01f), Color.White, modifier = Modifier.padding(start = 12.dp, top = 4.dp, bottom = 16.dp))
            for (s in sections) {
                val on = s == current
                Row(
                    Modifier.fillMaxWidth().height(46.dp).clip(RoundedCornerShape(15.dp))
                        .then(if (on) Modifier.segOn(15.dp, 0.30f, 0.36f, 0.18f) else Modifier)
                        .clickable { onSection(s) }.padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(s.menuIcon(MenuStyle.SHEET), if (on) Color(0xFFD8F0FF) else ice.ink3, 22.dp)
                    Spacer(Modifier.width(13.dp))
                    IceText(stringResource(s.menuTitle()), ts(15f, display = false), if (on) Color.White else Color(222, 236, 252).copy(alpha = 0.80f), modifier = Modifier.weight(1f))
                    if (on) Box(Modifier.softGlow(Color(0xFF8FD0FF).copy(alpha = 0.6f), 4.dp).size(6.dp).clip(CircleShape).background(Color(0xFFBFE6FF)))
                }
                Spacer(Modifier.height(2.dp))
            }
        }
    }
}

/** El nombre de la sección en el menú del diseño C ("Tu carro", "Inicio y apariencia"). */
fun SettingsSection.menuTitle(): Int = title

/**
 * El encabezado de las subpáginas de Tu carro (`.shdr`): botón de volver, el rótulo de la sección
 * arriba y el título; [trailing] a la derecha.
 */
@Composable
fun SubHeader(crumb: String, title: String, onBack: () -> Unit, trailing: @Composable RowScope.() -> Unit = {}) {
    val ice = LocalIce.current
    Row(Modifier.at(266, 16, 742, 48), verticalAlignment = Alignment.CenterVertically) {
        RoundBtn(SetIcons.back, onBack, tint = ice.ink, border = ice.line, fill = Color(170, 210, 255).copy(alpha = 0.07f))
        Spacer(Modifier.width(14.dp))
        Column {
            CapLabel(crumb, spacing = 0.16f)
            IceText(title, ts(26f), Color.White, modifier = Modifier.padding(top = 6.dp))
        }
        Spacer(Modifier.weight(1f))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp), content = trailing)
    }
}

/** El encabezado dentro de la hoja (`.mh`): "AJUSTES" chiquito, el título y [trailing] a la derecha. */
@Composable
fun SheetHeader(title: String, crumb: String = stringResource(R.string.dock_settings), trailing: @Composable RowScope.() -> Unit = {}) {
    // .main empieza en 24 + 236; .mh a 24 de ahí y 20 de arriba de la hoja.
    Row(Modifier.at(284, 36, 694, 48), verticalAlignment = Alignment.CenterVertically) {
        Column {
            CapLabel(crumb)
            IceText(title, ts(26f, 300, spacing = 0.01f), Color.White, modifier = Modifier.padding(top = 7.dp))
        }
        Spacer(Modifier.weight(1f))
        Row(verticalAlignment = Alignment.CenterVertically, content = trailing)
    }
}

/**
 * Teclado de números propio (sin el de Android, que en esta pantalla tapa media pantalla): una
 * ventana de vidrio con el valor, la unidad, las teclas grandes y Guardar. [decimal] deja escribir
 * una coma.
 */
@Composable
fun NumberPad(
    title: String,
    value: String,
    onValue: (String) -> Unit,
    unit: String,
    onDone: () -> Unit,
    onCancel: () -> Unit,
    decimal: Boolean = false,
    maxDigits: Int = 7,
    hint: String? = null,
    format: (String) -> String = { it },
) {
    val ice = LocalIce.current
    Box(
        Modifier.fillMaxSize().background(Color(2, 5, 10).copy(alpha = 0.72f))
            .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onCancel),
    ) {
        IceCard(
            Modifier.at(262, 40, 500, 448).clip(RoundedCornerShape(24.dp)).background(Kc.sheetBase)
                .clickable(remember { MutableInteractionSource() }, indication = null) {},
            radius = 24.dp, hi = true,
        ) {
            Column(Modifier.fillMaxSize().padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IceText(title, ts(18f, 500), Color.White, modifier = Modifier.weight(1f))
                    RoundBtn(SetIcons.close, onCancel, size = 36.dp, iconSize = 18.dp)
                }
                Row(
                    Modifier.padding(top = 12.dp).fillMaxWidth().height(64.dp).clip(RoundedCornerShape(16.dp))
                        .background(Color(6, 12, 22).copy(alpha = 0.5f)).border(1.dp, ice.line, RoundedCornerShape(16.dp)).padding(horizontal = 18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IceText(if (value.isEmpty()) "0" else format(value), ts(36f, 300), if (value.isEmpty()) ice.ink3 else Color.White, modifier = Modifier.weight(1f))
                    IceText(unit, ts(18f), ice.ink2)
                }
                IceText(hint ?: "", ts(13f, display = false, lh = 1.3f), ice.ink2, maxLines = 2, modifier = Modifier.padding(top = 8.dp).height(17.dp))
                val keys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", if (decimal) "," else "", "0", "<")
                Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (row in keys.chunked(3)) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (k in row) {
                            val shape = RoundedCornerShape(14.dp)
                            Box(
                                Modifier.weight(1f).height(48.dp).clip(shape)
                                    .then(if (k.isEmpty()) Modifier else Modifier.background(Color(170, 210, 255).copy(alpha = 0.07f)).border(1.dp, ice.line2, shape).clickable {
                                        onValue(
                                            when {
                                                k == "<" -> value.dropLast(1)
                                                k == "," -> if (value.contains(',')) value else (value.ifEmpty { "0" } + ",")
                                                value.filter { it.isDigit() }.length >= maxDigits -> value
                                                value.contains(',') && value.substringAfter(',').length >= 2 -> value
                                                value == "0" -> k
                                                else -> value + k
                                            },
                                        )
                                    }),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (k == "<") Icon(SetIcons.backspace, ice.ink2, 24.dp) else if (k.isNotEmpty()) IceText(k, ts(24f, 300), Color.White)
                            }
                        }
                    }
                }
                Spacer(Modifier.weight(1f))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    IceBtn(stringResource(R.string.cancel), onCancel, Modifier.weight(1f), BtnKind.PLAIN)
                    IceBtn(stringResource(R.string.taller_save), onDone, Modifier.weight(1f), BtnKind.LIGHT, icon = SetIcons.check, enabled = value.isNotEmpty())
                }
            }
        }
    }
}

/** Miles con punto ("85.412") en español y coma en inglés. */
fun groupThousands(n: Long, locale: java.util.Locale): String = ReminderKm.format(n.toInt(), locale).let { if (n < 0) "-$it" else it }

/** "12,4" con [decimals] y la coma de la región. */
fun decimalText(v: Double, decimals: Int, locale: java.util.Locale): String = String.format(locale, "%.${decimals}f", v)
