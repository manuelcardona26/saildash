package com.irvingguerra.litedash.core.ui.ice

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.irvingguerra.litedash.core.ui.R
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/*
 * SailDash 1.1 · diseño C "Hielo y órbita": los tokens de mock-C/common.css en Compose.
 * 1 px del mockup = 1 dp (la pantalla del Sail es de 160 dpi). Todo lo visual nuevo sale de aquí.
 */

/** Outfit (OFL, de github.com/google/fonts) en instancias fijas: Android 7 no entiende la fuente variable. */
val Outfit = FontFamily(
    Font(R.font.outfit_extralight, FontWeight.ExtraLight),
    Font(R.font.outfit_light, FontWeight.Light),
    Font(R.font.outfit_regular, FontWeight.Normal),
    Font(R.font.outfit_medium, FontWeight.Medium),
    Font(R.font.outfit_semibold, FontWeight.SemiBold),
)

/** Roboto, la del sistema (el --text del diseño). */
val RobotoText: FontFamily = FontFamily.SansSerif

/**
 * Los colores del diseño C. [dark] es el de los mockups (noche, sobre el espacio); [light] es su
 * pareja de día. Se leen con [LocalIce]; los componentes nunca escriben un color suelto.
 */
@Immutable
data class IcePalette(
    val dark: Boolean,
    /** --bg: el fondo detrás de todo. */
    val bg: Color,
    /** --ink: texto principal. */
    val ink: Color,
    /** --ink-2: texto secundario. */
    val ink2: Color,
    /** --ink-3: rótulos tenues. */
    val ink3: Color,
    /** --ice: el acento hielo. */
    val ice: Color,
    /** --ice-2: hielo profundo. */
    val ice2: Color,
    /** --ice-glow: el brillo alrededor de lo activo. */
    val iceGlow: Color,
    /** --line: borde fino de vidrio. */
    val line: Color,
    /** --line-2: borde más tenue (pozos de íconos, botones redondos). */
    val line2: Color,
    /** --glass: el relleno del vidrio (160°, de arriba-izquierda a abajo-derecha). */
    val glass: List<Pair<Float, Color>>,
    /** --glass-strong. */
    val glassStrong: List<Pair<Float, Color>>,
    /** El reflejo de 1 dp en el borde de arriba del vidrio (inset 0 1px 0). */
    val sheen: Color,
    /** Texto de títulos grandes sobre el vidrio (#fff en el oscuro). */
    val title: Color,
    /** El número de la velocidad (#f4faff). */
    val speed: Color,
    /** Verde menta: ubicación y el pozo de Casa. */
    val mint: Color,
    /** Dorado: favoritos. */
    val gold: Color,
    /** Ámbar del pico y placa. */
    val amber: Color,
    /** Rojo de alerta. */
    val danger: Color,
    /** El fondo del dock (de arriba a abajo). */
    val dockTop: Color,
    val dockBottom: Color,
    /** El texto e íconos del dock sin elegir. */
    val dockInk: Color,
    /** Relleno de los mosaicos activos (Wi-Fi, Bluetooth) a 135°. */
    val tileOn: List<Pair<Float, Color>>,
    val tileOnBorder: Color,
)

/** Las del mockup, tal cual. */
val IceDark = IcePalette(
    dark = true,
    bg = Color(0xFF04070E),
    ink = Color(0xFFEEF6FF),
    ink2 = Color(214, 230, 248).copy(alpha = 0.68f),
    ink3 = Color(196, 216, 238).copy(alpha = 0.56f),
    ice = Color(0xFFA9DCFF),
    ice2 = Color(0xFF6FBFFF),
    iceGlow = Color(150, 210, 255).copy(alpha = 0.55f),
    line = Color(190, 225, 255).copy(alpha = 0.16f),
    line2 = Color(190, 225, 255).copy(alpha = 0.09f),
    glass = listOf(
        0f to Color(170, 210, 255).copy(alpha = 0.115f),
        0.55f to Color(120, 160, 215).copy(alpha = 0.05f),
        1f to Color(90, 130, 190).copy(alpha = 0.035f),
    ),
    glassStrong = listOf(
        0f to Color(170, 210, 255).copy(alpha = 0.16f),
        1f to Color(110, 150, 210).copy(alpha = 0.07f),
    ),
    sheen = Color(235, 246, 255).copy(alpha = 0.10f),
    title = Color.White,
    speed = Color(0xFFF4FAFF),
    mint = Color(0xFF7EF0C2),
    gold = Color(0xFFFFD88A),
    amber = Color(0xFFFFD47A),
    danger = Color(0xFFFF7A6B),
    dockTop = Color(9, 15, 27).copy(alpha = 0.90f),
    dockBottom = Color(4, 8, 15).copy(alpha = 0.97f),
    dockInk = Color(222, 236, 252).copy(alpha = 0.78f),
    tileOn = listOf(0f to Color(120, 190, 255).copy(alpha = 0.30f), 1f to Color(90, 150, 235).copy(alpha = 0.12f)),
    tileOnBorder = Color(170, 215, 255).copy(alpha = 0.34f),
)

/** La pareja de día: papel hielo, tinta azul noche y vidrio blanco. */
val IceLight = IcePalette(
    dark = false,
    bg = Color(0xFFE6EDF6),
    ink = Color(0xFF0C1828),
    ink2 = Color(20, 40, 66).copy(alpha = 0.70f),
    ink3 = Color(30, 52, 80).copy(alpha = 0.56f),
    ice = Color(0xFF2E86D4),
    ice2 = Color(0xFF1C6CBC),
    iceGlow = Color(60, 140, 220).copy(alpha = 0.35f),
    line = Color(40, 80, 130).copy(alpha = 0.16f),
    line2 = Color(40, 80, 130).copy(alpha = 0.09f),
    glass = listOf(
        0f to Color.White.copy(alpha = 0.78f),
        0.55f to Color.White.copy(alpha = 0.58f),
        1f to Color(235, 243, 252).copy(alpha = 0.52f),
    ),
    glassStrong = listOf(0f to Color.White.copy(alpha = 0.9f), 1f to Color(235, 243, 252).copy(alpha = 0.7f)),
    sheen = Color.White.copy(alpha = 0.9f),
    title = Color(0xFF07111F),
    speed = Color(0xFF0A1626),
    mint = Color(0xFF14A877),
    gold = Color(0xFFC98A12),
    amber = Color(0xFFB7790A),
    danger = Color(0xFFD93A2B),
    dockTop = Color(246, 249, 253).copy(alpha = 0.94f),
    dockBottom = Color(230, 238, 247).copy(alpha = 0.98f),
    dockInk = Color(20, 40, 66).copy(alpha = 0.78f),
    tileOn = listOf(0f to Color(90, 160, 235).copy(alpha = 0.30f), 1f to Color(90, 150, 235).copy(alpha = 0.14f)),
    tileOnBorder = Color(60, 130, 210).copy(alpha = 0.34f),
)

/** El tema hielo de ahora (lo pone [com.irvingguerra.litedash.core.ui.LiteDashTheme]). */
val LocalIce = staticCompositionLocalOf { IceDark }

/** Medidas del diseño (dp). */
object IceDims {
    /** --r: el radio de las tarjetas de vidrio. */
    val radius = 20.dp
    /** Radio de las fichas (destinos, mosaicos). */
    val chipRadius = 18.dp
    /** El dock de abajo. */
    val dock = 72.dp
    /** Botones redondos (contraste, flechas de página). */
    val iconButton = 44.dp
    /** El lienzo del diseño: todo se mide sobre 1024 × 600. */
    const val FRAME_W = 1024f
    const val FRAME_H = 600f
}

/** Texto con line-height 1 como en el CSS: el alto de la línea es el tamaño de la letra, centrada. */
private fun style(family: FontFamily, weight: FontWeight, size: Float, spacing: Float = 0f, lineHeight: Float = 1f) = TextStyle(
    fontFamily = family,
    fontWeight = weight,
    fontSize = size.sp,
    letterSpacing = spacing.em,
    lineHeight = lineHeight.em,
    lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
    platformStyle = PlatformTextStyle(includeFontPadding = false),
)

/** Los estilos de texto del diseño (sin color: lo pone quien dibuja, de [IcePalette]).
 *
 * Android dibuja la letra chica más delgada que Chrome en Windows (donde se hicieron los mockups): la
 * marca, el lugar y la hora del dock van un peso arriba del CSS para verse igual. */
object IceType {
    /** .brand: CHEVROLET SAIL, espaciado ancho. */
    val brand = style(Outfit, FontWeight.SemiBold, 13f, 0.42f)
    /** .loc: COMUNA 3. */
    val location = style(Outfit, FontWeight.SemiBold, 13f, 0.16f)
    /** Rótulo de sección en mayúsculas (como .brand pero más corto). */
    val section = style(Outfit, FontWeight.Medium, 12f, 0.24f)
    /** La velocidad enorme y delgada. */
    val speed = style(Outfit, FontWeight.ExtraLight, 128f, -0.02f)
    val speedUnit = style(Outfit, FontWeight.Light, 22f, 0.04f)
    /** Título de ficha (Casa, Trabajo). */
    val chipTitle = style(Outfit, FontWeight.Medium, 17f, lineHeight = 1.1f)
    val chipSub = style(RobotoText, FontWeight.Normal, 13f, lineHeight = 1.2f)
    /** Título de mosaico (Wi-Fi, Bluetooth). */
    val tileTitle = style(Outfit, FontWeight.Medium, 16f)
    val small = style(RobotoText, FontWeight.Normal, 13f)
    val body = style(RobotoText, FontWeight.Normal, 15f)
    /** Número mediano (telemetría). */
    val value = style(Outfit, FontWeight.Normal, 18f)
    val valueUnit = style(Outfit, FontWeight.Normal, 15f)
    /** Temperatura del clima. */
    val temp = style(Outfit, FontWeight.Light, 44f)
    val condition = style(Outfit, FontWeight.Light, 26f)
    /** Título de canción. */
    val songTitle = style(Outfit, FontWeight.Medium, 20f, lineHeight = 1.15f)
    val songArtist = style(RobotoText, FontWeight.Normal, 15f, lineHeight = 1.2f)
    /** Título grande de página (Carro, Carretera…). */
    val pageTitle = style(Outfit, FontWeight.Light, 34f, 0.02f)
    /** Las del dock. */
    val dockLabel = style(RobotoText, FontWeight.Normal, 13f)
    val dockTime = style(Outfit, FontWeight.Normal, 25f, 0.01f)
    val dockMarker = style(Outfit, FontWeight.Normal, 15f, 0.04f)
    val dockDate = style(RobotoText, FontWeight.Normal, 13f, 0.03f)
}

/**
 * Un degradado lineal como el de CSS (`linear-gradient(<ángulo>deg, …)`) para una caja de [size]:
 * 0° va hacia arriba, 90° a la derecha y 160° casi hacia abajo; el largo es el que da CSS.
 */
fun cssLinearGradient(angleDeg: Float, size: Size, stops: List<Pair<Float, Color>>, origin: Offset = Offset.Zero): Brush {
    val a = Math.toRadians(angleDeg.toDouble())
    val dx = sin(a).toFloat()
    val dy = -cos(a).toFloat()
    val half = (abs(size.width * sin(a)) + abs(size.height * cos(a))).toFloat() / 2f
    val c = Offset(origin.x + size.width / 2f, origin.y + size.height / 2f)
    return Brush.linearGradient(
        *stops.toTypedArray(),
        start = Offset(c.x - dx * half, c.y - dy * half),
        end = Offset(c.x + dx * half, c.y + dy * half),
    )
}
