package com.irvingguerra.litedash

import android.provider.Settings
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.irvingguerra.litedash.core.ui.Icon
import com.irvingguerra.litedash.core.ui.ice.BrandTitle
import com.irvingguerra.litedash.core.ui.ice.GlassCard
import com.irvingguerra.litedash.core.ui.ice.IceBackdrop
import com.irvingguerra.litedash.core.ui.ice.IceIcons
import com.irvingguerra.litedash.core.ui.ice.IceText
import com.irvingguerra.litedash.core.ui.ice.IcePalette
import com.irvingguerra.litedash.core.ui.ice.LocalIce
import com.irvingguerra.litedash.core.ui.ice.LocationLabel
import com.irvingguerra.litedash.core.ui.ice.VDivider
import com.irvingguerra.litedash.core.ui.ice.glass
import com.irvingguerra.litedash.feature.apps.App
import com.irvingguerra.litedash.feature.car.CarDrawing
import com.irvingguerra.litedash.feature.car.CarStill

/*
 * SailDash 1.1 · la página Carro del diseño C (mock-C/carro.html), medida por medida: el Sail grande
 * sobre su escenario de órbitas, los rótulos con su línea guía (Wi-Fi, Bluetooth y el motor, con datos
 * de verdad o "–") y abajo la música y la navegación. Cada offset/size es el left/top/width del HTML.
 */

/** Lo que la página Carro pide afuera (paneles, la música en grande, el modo galaxia). */
class PageActions(
    val onPanel: (Panel) -> Unit,
    val onMusic: () -> Unit,
    val onSpeed: () -> Unit,
)

/** La página Carro. [visible] = se ve ahora (sin nada encima): solo entonces se escucha el GPS. */
@Composable
fun CarroPage(d: Dependencies, apps: List<App>, visible: Boolean, actions: PageActions) {
    val ice = LocalIce.current
    Box(Modifier.fillMaxSize()) {
        IceBackdrop(R.drawable.bg_espacio)
        // .bh: el agujero negro arriba a la izquierda.
        if (ice.dark) Image(
            painterResource(R.drawable.blackhole_small), null,
            Modifier.offset(58.dp, 74.dp).size(140.dp, 86.dp).alpha(0.62f), contentScale = ContentScale.Fit,
        )
        CarStage(d)
        Guides(ice)
        Callouts(d, actions)
        TopBar(d, visible, actions.onSpeed)
        // .cards: left 112, right 112, top 404, alto 104, separadas 14; la música ocupa 1.5 y navegar 1.
        Row(Modifier.offset(112.dp, 404.dp).size(800.dp, 104.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            MusicPanel(d, Modifier.weight(1.5f).fillMaxHeight(), actions.onMusic)
            NavPanel(d, apps, Modifier.weight(1f).fillMaxHeight(), actions.onPanel)
        }
    }
}

// ---------------------------------------------------------------- arriba

@Composable
private fun TopBar(d: Dependencies, visible: Boolean, onSpeed: () -> Unit) {
    val place = rememberPlaceName(d)
    // .top: left 28, right 28, top 14, alto 44; velocidad y lugar | marca | clima.
    Row(Modifier.offset(28.dp, 14.dp).size(968.dp, 44.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            SmallSpeed(d, visible, onSpeed)
            Spacer(Modifier.width(16.dp))
            VDivider(20.dp)
            if (place != null) {
                Spacer(Modifier.width(16.dp))
                LocationLabel(place)
            }
        }
        BrandTitle(d.prefs.carName?.takeIf { it.isNotBlank() } ?: "Chevrolet Sail")
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
            WeatherInline(d, 26.dp, iceStyle(22f, FontWeight.Light), 10.dp)
        }
    }
}

/**
 * La velocidad chiquita de arriba (`.spd`): "0 km/h". Sin OBD, el GPS publica la velocidad mientras la
 * página se ve. Un toque abre el modo galaxia.
 */
@Composable
fun SmallSpeed(d: Dependencies, visible: Boolean, onClick: () -> Unit) {
    val ice = LocalIce.current
    val obdOn = rememberObd(d)?.state == com.irvingguerra.litedash.feature.obd.ObdState.ON
    val (gps, _) = rememberGpsPermission(d)
    if (!obdOn && gps && visible) KeepTripRunning(d.trip)
    val miles = d.prefs.units.miles
    val shown = rememberShownSpeed(d, miles)
    Row(Modifier.clickable(remember { MutableInteractionSource() }, indication = null, onClick = onClick)) {
        IceText(shown.value.toString(), iceStyle(22f, FontWeight.Light), ice.title, modifier = Modifier.alignByBaseline())
        Spacer(Modifier.width(5.dp))
        IceText(stringResource(if (miles) R.string.speed_unit_mph else R.string.speed_unit_kmh), iceStyle(14f), ice.ink2, modifier = Modifier.alignByBaseline())
    }
}

// ---------------------------------------------------------------- el carro y su escenario

@Composable
private fun CarStage(d: Dependencies) {
    val context = LocalContext.current
    val ice = LocalIce.current
    val owner = remember { OwnerCar.of(context) }
    LaunchedEffect(owner) { owner.load() }
    // .ground (170, 262; 690 × 200): la sombra del piso; .pool (133, 252; 330 × 120): la luz de los faros.
    Box(Modifier.offset(170.dp, 262.dp).size(690.dp, 200.dp).drawWithCache {
        val c = Offset(size.width / 2, size.height / 2)
        val ground = Color(1, 3, 7)
        val brush = Brush.radialGradient(0f to ground.copy(alpha = if (ice.dark) 0.8f else 0.18f), 0.55f to ground.copy(alpha = if (ice.dark) 0.45f else 0.1f), 1f to ground.copy(alpha = 0f), center = c, radius = size.width / 2)
        onDrawBehind { scale(1f, size.height / size.width, pivot = c) { drawCircle(brush, size.width / 2, c) } }
    })
    val image = owner.image
    if (image != null && ice.dark) Box(Modifier.offset(133.dp, 252.dp).size(330.dp, 120.dp).drawWithCache {
        val c = Offset(size.width / 2, size.height / 2)
        val brush = Brush.radialGradient(0f to Color(255, 236, 200).copy(alpha = 0.14f), 0.7f to Color(255, 236, 200).copy(alpha = 0f), center = c, radius = size.width / 2)
        onDrawBehind { scale(1f, size.height / size.width, pivot = c) { drawCircle(brush, size.width / 2, c) } }
    })
    val stage = Modifier.offset(180.dp, 262.dp).size(664.dp, 170.dp)
    Canvas(stage) { drawCarStage(ice, front = false) }
    if (image != null) {
        // .car: left 217, top 90, ancho 589 (el alto, el de la foto).
        val w = 589.dp
        Image(image.bitmap.asImageBitmap(), stringResource(R.string.my_car_fallback), Modifier.offset(217.dp, 90.dp).size(w, w * image.ratio), contentScale = ContentScale.FillBounds)
    } else if (owner.loaded) {
        // Sin foto: el auto 3D del catálogo (quieto) o el dibujo de LiteDash, en el mismo lugar.
        val pkg = d.cars.pkg(d.prefs.carModel)
        val m = Modifier.offset(232.dp, 96.dp).size(560.dp, 300.dp)
        if (pkg != null) CarStill(d.cars, pkg, d.prefs.carPaint, m) else Box(m, contentAlignment = Alignment.Center) { CarDrawing(d.prefs.carColor, Modifier.fillMaxWidth()) }
    }
    // .bloom: la luz de los faros, solo de noche (de front.json, sobre la foto).
    if (image != null && ice.dark) for ((i, h) in image.headlights.withIndex()) {
        val r = h.glow * 589f
        val box = (r * 2.08f).coerceIn(40f, 200f)
        val cx = 217f + h.x * 589f
        val cy = 90f + h.y * 589f
        Box(
            Modifier
                .offset((cx - box / 2).dp, (cy - box / 2).dp)
                .size(box.dp)
                .drawWithCache {
                    val brush = Brush.radialGradient(
                        0f to Color(255, 244, 222).copy(alpha = 0.42f), 0.35f to Color(255, 236, 205).copy(alpha = 0.16f), 0.70f to Color(255, 236, 205).copy(alpha = 0f),
                        center = Offset(size.width / 2f, size.height / 2f), radius = size.width * 0.7071f,
                    )
                    val alpha = if (i > 0) 0.75f else 1f
                    onDrawBehind { drawCircle(brush, size.width / 2f, alpha = alpha) }
                },
        )
    }
    Canvas(stage) { drawCarStage(ice, front = true) }
}

/** svg.stage (664 × 170): la elipse del piso (centro 332, 85; 320 × 64) y la punteada de adentro (332, 98; 280 × 50). */
private fun DrawScope.drawCarStage(ice: IcePalette, front: Boolean) {
    val k = size.width / 664f
    val a = if (ice.dark) Color(0xFFA9DCFF) else ice.ice
    val b = if (ice.dark) Color(0xFFD9F0FF) else ice.ice2
    fun stops(x0: Float, x1: Float) = Brush.horizontalGradient(
        0f to a.copy(alpha = 0f), 0.2f to a.copy(alpha = 0.35f), 0.5f to b.copy(alpha = 0.6f), 0.8f to a.copy(alpha = 0.35f), 1f to a.copy(alpha = 0f),
        startX = x0, endX = x1,
    )
    scale(k, k, pivot = Offset.Zero) {
        if (!front) {
            drawArc(stops(12f, 652f), 180f, 180f, false, Offset(12f, 21f), Size(640f, 128f), style = Stroke(1f), alpha = 0.5f)
        } else {
            drawArc(stops(12f, 652f), 0f, 180f, false, Offset(12f, 21f), Size(640f, 128f), style = Stroke(1.2f))
            drawArc(
                stops(52f, 612f), 0f, 180f, false, Offset(52f, 48f), Size(560f, 100f),
                style = Stroke(1f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(1.5f, 6f))), alpha = 0.6f,
            )
        }
    }
}

/** svg.guides: las líneas guía de los rótulos y su punto en el carro (en el lienzo de 1024 × 600). */
@Composable
private fun Guides(ice: IcePalette) {
    val line = if (ice.dark) Color(190, 228, 255).copy(alpha = 0.6f) else ice.ice2.copy(alpha = 0.6f)
    val ring = if (ice.dark) Color(0xFFBFE6FF) else ice.ice2
    val dot = if (ice.dark) Color.White else ice.ice2
    Canvas(Modifier.size(1024.dp, 600.dp)) {
        val u = size.width / 1024f
        fun guide(x0: Float, y0: Float, x1: Float, y1: Float) {
            drawLine(line, Offset(x0 * u, y0 * u), Offset(x1 * u, y1 * u), strokeWidth = u)
            drawCircle(ring, 4.5f * u, Offset(x1 * u, y1 * u), style = Stroke(1.2f * u))
            drawCircle(dot, 1.6f * u, Offset(x1 * u, y1 * u))
        }
        guide(283f, 184f, 283f, 208f)
        guide(486f, 100f, 486f, 158f)
        guide(716f, 134f, 716f, 176f)
        guide(770f, 208f, 736f, 208f)
    }
}

// ---------------------------------------------------------------- rótulos

@Composable
private fun Callouts(d: Dependencies, actions: PageActions) {
    val context = LocalContext.current
    val ice = LocalIce.current
    val links = rememberLinks()
    val locale = currentLocale()
    Callout(stringResource(R.string.wifi_title), Modifier.offset(279.dp, 142.dp), onClick = { openSystemSettings(context, Settings.ACTION_WIFI_SETTINGS) }) {
        Icon(if (links.wifi) IceIcons.wifi else IceIcons.wifiOff, ice.ice, 20.dp)
        Spacer(Modifier.width(8.dp))
        CalloutValue(stringResource(if (links.wifi) R.string.wifi_connected else R.string.wifi_disconnected))
    }
    // .lab.btn: el contraste (día y noche), redondo de 48 sobre su línea guía.
    Box(
        Modifier
            .offset(462.dp, 52.dp)
            .size(48.dp)
            .glass(listOf(0f to Color(170, 210, 255).copy(alpha = 0.16f), 1f to Color(110, 150, 210).copy(alpha = 0.06f)), ice.line, Color.Transparent, 24.dp)
            .clip(CircleShape)
            .clickable { d.prefs.theme = if (ice.dark) ThemeMode.LIGHT else ThemeMode.DARK },
        contentAlignment = Alignment.Center,
    ) { Icon(IceIcons.contrast, ice.title, 24.dp) }
    Callout(stringResource(R.string.bt_title), Modifier.offset(712.dp, 92.dp), onClick = { openSystemSettings(context, Settings.ACTION_BLUETOOTH_SETTINGS) }) {
        Icon(IceIcons.bluetooth, ice.ice, 20.dp)
        Spacer(Modifier.width(8.dp))
        CalloutValue(if (links.bluetooth) links.bluetoothName ?: stringResource(R.string.bt_tile_connected) else stringResource(R.string.bt_disconnected))
    }
    val engine = rememberEngine(d)
    val units = d.prefs.units
    Callout(stringResource(R.string.car_engine_trio), Modifier.offset(778.dp, 186.dp), onClick = { actions.onPanel(Panel.Workshop) }) {
        val temp = engine.coolant?.toFahrenheit(units.fahrenheit)
        CalloutValue("${numberOrDash(temp, 0, locale)} ${if (units.fahrenheit) "°F" else "°C"}")
        CalloutDot()
        CalloutValue("${numberOrDash(engine.volts, 1, locale)} V")
        CalloutDot()
        CalloutValue("${numberOrDash(engine.fuel, 0, locale)} %")
    }
}

/** Un rótulo (`.lab`): la etiqueta tenue arriba y, 6 dp abajo, el valor con su ícono. */
@Composable
private fun Callout(key: String, modifier: Modifier, onClick: () -> Unit, value: @Composable RowScope.() -> Unit) {
    val ice = LocalIce.current
    Column(modifier.clickable(remember { MutableInteractionSource() }, indication = null, onClick = onClick)) {
        IceText(key, iceStyle(13f, display = false, spacing = 0.06f), ice.ink3)
        Row(Modifier.padding(top = 6.dp).height(20.dp), verticalAlignment = Alignment.CenterVertically, content = value)
    }
}

@Composable
private fun CalloutValue(text: String) = IceText(text, iceStyle(18f), LocalIce.current.title)

/** El punto que separa los datos del motor (gap 8 a cada lado). */
@Composable
private fun CalloutDot() {
    Spacer(Modifier.width(8.dp))
    IceText("·", iceStyle(18f), LocalIce.current.ink3)
    Spacer(Modifier.width(8.dp))
}

// ---------------------------------------------------------------- música y navegación

/** .mus: la canción arriba (carátula, título, artista y la app) y los controles abajo. */
@Composable
private fun MusicPanel(d: Dependencies, modifier: Modifier, onExpand: () -> Unit) {
    val ice = LocalIce.current
    val repo = rememberMusic(d)
    val song = repo.song
    GlassCard(modifier) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().weight(1f).clickable(remember { MutableInteractionSource() }, indication = null, onClick = if (repo.needsAccess) repo::requestPermission else onExpand).padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SongArt(song, RoundedCornerShape(10.dp), Modifier.size(40.dp))
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    IceText(song?.title?.ifBlank { repo.appName(song.pkg) } ?: stringResource(R.string.music_idle), iceStyle(16f, FontWeight.Medium, lineHeight = 1.1f), ice.title)
                    IceText(
                        song?.artist ?: if (repo.needsAccess) stringResource(R.string.tap_to_enable) else repo.preferredName().ifBlank { stringResource(R.string.music_idle_hint) },
                        iceStyle(13f, display = false), ice.ink2, modifier = Modifier.padding(top = 4.dp),
                    )
                }
                val pkg = song?.pkg.orEmpty()
                if (pkg.isNotBlank()) {
                    val spotify = pkg == SPOTIFY_PKG
                    Icon(if (spotify) IceIcons.spotify else IceIcons.music, if (spotify) Color(0xFF1ED760) else ice.ice, 22.dp)
                }
            }
            Box(Modifier.fillMaxWidth().height(1.dp).drawBehind { drawRect(ice.line2) })
            // .r2: alto 50, repartidos (space-around) con 30 dp a los lados.
            Row(Modifier.fillMaxWidth().height(50.dp).padding(horizontal = 30.dp), horizontalArrangement = Arrangement.SpaceAround, verticalAlignment = Alignment.CenterVertically) {
                BareButton(PageIcons.shuffle, onExpand, ice.ink3, iconSize = 24.dp)
                BareButton(IceIcons.previous, { repo.previous() }, iconSize = 24.dp)
                WhitePlay(song?.playing == true, 44.dp, 22.dp, { repo.toggle() })
                BareButton(IceIcons.next, { repo.next() }, iconSize = 24.dp)
            }
        }
    }
}

const val SPOTIFY_PKG = "com.spotify.music"

/** .nav: "Navegar" arriba y Casa | Trabajo abajo. */
@Composable
private fun NavPanel(d: Dependencies, apps: List<App>, modifier: Modifier, onPanel: (Panel) -> Unit) {
    val context = LocalContext.current
    val ice = LocalIce.current
    val map = mapApp(d, apps)
    fun go(address: String?) {
        if (address != null) navigateTo(context, map, address) else onPanel(Panel.MusicSettings)
    }
    val setUp = stringResource(R.string.place_tap_to_set)
    GlassCard(modifier) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().weight(1f).clickable { openNavigation(context, d.apps, map) }.padding(horizontal = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(IceIcons.search, ice.ice, 24.dp)
                Spacer(Modifier.width(12.dp))
                IceText(stringResource(R.string.car_navigate), iceStyle(17f), ice.ink2)
            }
            Box(Modifier.fillMaxWidth().height(1.dp).drawBehind { drawRect(ice.line2) })
            Row(Modifier.fillMaxWidth().height(50.dp)) {
                // Como en el CSS (flex con nowrap): Casa toma lo que mide su texto y Trabajo, lo que queda.
                Place(IceIcons.house, if (ice.dark) Color(0xFF8FF0CF) else ice.mint, stringResource(R.string.place_home), d.prefs.homeAddress ?: setUp, Modifier.widthIn(max = 200.dp)) { go(d.prefs.homeAddress) }
                Box(Modifier.width(1.dp).fillMaxHeight().drawBehind { drawRect(ice.line2) })
                Place(IceIcons.briefcase, ice.ice, stringResource(R.string.place_work), d.prefs.workAddress ?: setUp, Modifier.weight(1f)) { go(d.prefs.workAddress) }
            }
        }
    }
}

@Composable
private fun Place(icon: ImageVector, tint: Color, title: String, subtitle: String, modifier: Modifier, onClick: () -> Unit) {
    val ice = LocalIce.current
    Row(modifier.fillMaxHeight().clickable(onClick = onClick).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, tint, 22.dp)
        Spacer(Modifier.width(10.dp))
        Column {
            IceText(title, iceStyle(15f, FontWeight.Medium), ice.title)
            IceText(subtitle, iceStyle(13f, display = false), ice.ink2, modifier = Modifier.padding(top = 3.dp))
        }
    }
}

/** Un borde redondo fino (para los botones flotantes). */
fun Modifier.ring(color: Color, size: Dp = 1.dp) = border(size, color, CircleShape)
