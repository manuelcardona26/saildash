package com.irvingguerra.litedash

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.irvingguerra.litedash.core.ui.ice.Outfit
import com.irvingguerra.litedash.core.ui.ice.RobotoText
import androidx.compose.ui.unit.dp
import com.irvingguerra.litedash.core.ui.Icon
import com.irvingguerra.litedash.core.ui.ice.IceIcons
import com.irvingguerra.litedash.core.ui.ice.IceText
import com.irvingguerra.litedash.core.ui.ice.LocalIce
import com.irvingguerra.litedash.core.ui.ice.cssLinearGradient
import com.irvingguerra.litedash.core.vehicle.Signal
import com.irvingguerra.litedash.feature.music.MusicRepository
import com.irvingguerra.litedash.feature.music.Song
import kotlin.math.roundToInt

/*
 * SailDash 1.1 · piezas que comparten las páginas Carro, Carretera y Mapa del diseño C: la carátula
 * chica, el play blanco, el vidrio oscuro flotante y el clima de una línea.
 */

/** La música mientras la página se ve (pide las sesiones de medios y las suelta al salir). */
@Composable
fun rememberMusic(d: Dependencies): MusicRepository {
    val repo = d.music
    DisposableEffect(repo) {
        repo.use()
        onDispose { repo.release() }
    }
    return repo
}

/**
 * La carátula (`.art`) de [song]; sin carátula, el agujero negro del mockup dibujado a escala: el
 * hueco, el anillo y el disco, sobre el azul noche. [shape] = redonda o con esquinas.
 */
@Composable
fun SongArt(song: Song?, shape: Shape, modifier: Modifier) {
    val bmp = song?.artwork
    Box(modifier.clip(shape)) {
        if (bmp != null) Image(bmp.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else Box(Modifier.fillMaxSize().drawWithCache {
            // Medidas del de 40 px (.art de Carro y Carretera): hueco 8, anillo 9–12, disco 22 × 4.5.
            val k = size.width / 40f
            val c = Offset(size.width / 2f, size.height * 0.56f)
            val base = cssLinearGradient(160f, size, listOf(0f to Color(0xFF1C3352), 0.7f to Color(0xFF0A1222), 1f to Color(0xFF0A1222)))
            val ring = Brush.radialGradient(
                0f to Color.Transparent, (8f / 12f) to Color.Transparent, (9f / 12f) to Color(255, 206, 140).copy(alpha = 0.9f), 1f to Color(255, 170, 90).copy(alpha = 0f),
                center = c, radius = 12f * k,
            )
            val disk = Brush.radialGradient(
                0f to Color(255, 214, 150).copy(alpha = 0.95f), 0.6f to Color(255, 170, 90).copy(alpha = 0.3f), 1f to Color(255, 170, 90).copy(alpha = 0f),
                center = c, radius = 22f * k,
            )
            onDrawBehind {
                drawRect(base)
                drawCircle(ring, 12f * k, c)
                scale(1f, 4.5f / 22f, pivot = c) { drawCircle(disk, 22f * k, c) }
                drawCircle(Color.Black, 8.25f * k, c)
            }
        })
    }
}

/** El play blanco con su brillo (`.play`): [size] de diámetro, ícono de [icon]; pausa si [playing]. */
@Composable
fun WhitePlay(playing: Boolean, size: Dp, icon: Dp, onClick: () -> Unit, modifier: Modifier = Modifier, glow: Dp = 16.dp) {
    val ice = LocalIce.current
    Box(
        modifier
            .size(size)
            .drawWithCache {
                val r = this.size.width / 2f
                val g = glow.toPx()
                // box-shadow: 0 0 16px rgba(170,220,255,.4), sin desenfoque.
                // Una sombra de 16 px con desenfoque llega tenue al borde y se apaga rápido.
                val brush = Brush.radialGradient(
                    0f to Color(170, 220, 255).copy(alpha = 0.3f), (r / (r + g)) to Color(170, 220, 255).copy(alpha = 0.2f),
                    ((r + g * 0.4f) / (r + g)) to Color(170, 220, 255).copy(alpha = 0.07f), 1f to Color.Transparent,
                    center = Offset(r, r), radius = r + g,
                )
                onDrawBehind { if (ice.dark) drawCircle(brush, r + g) }
            }
            .clip(CircleShape)
            .background(if (ice.dark) Color.White else ice.ink)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        val tint = if (ice.dark) Color(0xFF0A1322) else Color.White
        if (playing) Icon(IceIcons.pause, tint, icon)
        else Icon(IceIcons.play, tint, icon, Modifier.offset(x = 1.5.dp))
    }
}

/** Botón sin fondo de 44 dp (`.b`): anterior, siguiente, aleatorio. */
@Composable
fun BareButton(icon: ImageVector, onClick: () -> Unit, tint: Color = LocalIce.current.let { if (it.dark) Color(0xFFEAF4FF) else it.ink }, size: Dp = 44.dp, iconSize: Dp = 22.dp) {
    Box(Modifier.size(size).clip(CircleShape).clickable(onClick = onClick), contentAlignment = Alignment.Center) { Icon(icon, tint, iconSize) }
}

/**
 * El vidrio oscuro de lo que flota sobre el mapa o la carretera (`.fl`, `.mbar`): degradado a 160° de
 * [top] a [bottom], borde fino y el reflejo de arriba. Sin desenfoque.
 */
fun Modifier.darkGlass(radius: Dp, top: Color = Color(28, 42, 64).copy(alpha = 0.94f), bottom: Color = Color(10, 16, 28).copy(alpha = 0.95f), border: Color = Color(190, 225, 255).copy(alpha = 0.16f)): Modifier = drawWithCache {
    val r = radius.toPx().coerceAtMost(minOf(size.width, size.height) / 2f)
    val px = 1.dp.toPx()
    val outline = Path().apply { addRoundRect(RoundRect(0f, 0f, size.width, size.height, CornerRadius(r))) }
    val shifted = Path().apply { addRoundRect(RoundRect(0f, px, size.width, size.height + px, CornerRadius(r))) }
    val sheen = Path().apply { op(outline, shifted, PathOperation.Difference) }
    val brush = cssLinearGradient(160f, size, listOf(0f to top, 1f to bottom))
    // La sombra de abajo (0 12px 30px rgba(0,0,0,.55)), como un óvalo tenue que asoma abajo.
    val shadow = Brush.verticalGradient(0f to Color.Black.copy(alpha = 0.28f), 1f to Color.Transparent, startY = size.height - r, endY = size.height + 14.dp.toPx())
    onDrawBehind {
        drawRoundRect(shadow, Offset(r * 0.3f, size.height - r), Size(size.width - r * 0.6f, r + 14.dp.toPx()), CornerRadius(r))
        drawPath(outline, brush)
        drawPath(sheen, Color(235, 246, 255).copy(alpha = 0.10f))
        drawRoundRect(border, Offset(px / 2, px / 2), Size(size.width - px, size.height - px), CornerRadius((r - px / 2).coerceAtLeast(0f)), style = Stroke(px))
    }
}

/** El ícono del clima (trazo 1.5) para el cielo de ahora. */
fun weatherIcon(sky: Sky, day: Boolean): ImageVector = when (sky) {
    Sky.CLEAR -> if (day) IceIcons.sun else IceIcons.moon
    Sky.PARTLY -> if (day) IceIcons.partly else IceIcons.moon
    Sky.CLOUDY -> IceIcons.cloud
    Sky.FOG -> IceIcons.fog
    Sky.RAIN -> IceIcons.rain
    Sky.STORM -> IceIcons.storm
    Sky.SNOW -> IceIcons.snow
}

/** El clima en una línea (`.wx`): ícono de [iconSize] y la temperatura; sin clima, nada. */
@Composable
fun WeatherInline(d: Dependencies, iconSize: Dp, style: TextStyle, gap: Dp, modifier: Modifier = Modifier) {
    val w = d.weather.weather ?: return
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(weatherIcon(skyOf(w.code), w.day), Color(0xFFD6ECFF), iconSize)
        Spacer(Modifier.width(gap))
        IceText("${w.temperature}°", style, Color.White, maxLines = 1)
    }
}

/** Los datos del motor del OBD (null = no se sabe): temperatura, voltaje y gasolina. */
class EngineData(val coolant: Float?, val volts: Float?, val fuel: Float?)

/** Lee del bus lo del motor (y avisa que se está mostrando, para que el OBD lo pregunte). */
@Composable
fun rememberEngine(d: Dependencies): EngineData = EngineData(
    rememberSignal(d, Signal.COOLANT).value,
    rememberSignal(d, Signal.VOLTAGE).value,
    rememberSignal(d, Signal.FUEL).value,
)

/** Un número con [decimals] decimales en la configuración regional, o "–" si no se sabe. */
fun numberOrDash(value: Float?, decimals: Int, locale: java.util.Locale): String =
    if (value == null || value.isNaN()) DASH_TEXT
    else if (decimals == 0) value.roundToInt().toString()
    else String.format(locale, "%.${decimals}f", value)

/** Lo que se ve cuando un dato no se sabe. */
const val DASH_TEXT = "–"

/**
 * Un estilo de texto como los del CSS de los mockups (`font: <peso> <tamaño>px/<alto> var(--display)`):
 * Outfit si [display], si no Roboto; alto de línea 1 y espaciado en em, igual que [IceType].
 */
fun iceStyle(size: Float, weight: FontWeight = FontWeight.Normal, display: Boolean = true, spacing: Float = 0f, lineHeight: Float = 1f) = TextStyle(
    fontFamily = if (display) Outfit else RobotoText,
    fontWeight = weight,
    fontSize = size.sp,
    letterSpacing = spacing.em,
    lineHeight = lineHeight.em,
    lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
    platformStyle = PlatformTextStyle(includeFontPadding = false),
)

/**
 * La velocidad que se ve (entera, en la unidad del usuario) como estado derivado del bus: quien la lee
 * se vuelve a armar solo cuando cambia el número entero, no con cada décima del GPS. Mientras exista,
 * avisa al bus que se está mostrando (para que el OBD la pregunte).
 */
@Composable
fun rememberShownSpeed(d: Dependencies, miles: Boolean): State<Int> {
    DisposableEffect(d.bus) {
        d.bus.want(Signal.SPEED)
        onDispose { d.bus.unwant(Signal.SPEED) }
    }
    return remember(d.bus, miles) { derivedStateOf { speedOnlyShown(d.bus.value(Signal.SPEED) ?: 0f, miles) } }
}

/**
 * Una señal del bus como estado derivado (y avisando que se muestra). En las páginas del inicio, leer el
 * bus directo en la composición (VehicleBus.watch) no siempre volvía a armar la pantalla al llegar un
 * dato nuevo; derivado sí, y además solo cuando cambia.
 */
@Composable
fun rememberSignal(d: Dependencies, signal: String): State<Float?> {
    DisposableEffect(d.bus, signal) {
        d.bus.want(signal)
        onDispose { d.bus.unwant(signal) }
    }
    return remember(d.bus, signal) { derivedStateOf { d.bus.value(signal) } }
}
