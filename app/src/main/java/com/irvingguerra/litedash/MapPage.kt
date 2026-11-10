package com.irvingguerra.litedash

import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleStartEffect
import com.irvingguerra.litedash.core.ui.Icon
import com.irvingguerra.litedash.core.ui.ice.BrandTitle
import com.irvingguerra.litedash.core.ui.ice.IceIcons
import com.irvingguerra.litedash.core.ui.ice.IceText
import com.irvingguerra.litedash.core.ui.ice.LocalIce
import com.irvingguerra.litedash.core.ui.ice.LocationLabel
import com.irvingguerra.litedash.core.ui.ice.glass
import com.irvingguerra.litedash.core.vehicle.Signal
import com.irvingguerra.litedash.feature.apps.App
import com.irvingguerra.litedash.feature.map.MapCanvas
import com.irvingguerra.litedash.feature.map.MapDetail
import com.irvingguerra.litedash.feature.map.MapLook
import com.irvingguerra.litedash.feature.map.MapStyle
import com.irvingguerra.litedash.feature.map.MapView
import com.irvingguerra.litedash.feature.map.MapZoom
import com.irvingguerra.litedash.feature.map.MinimalPalette

/*
 * SailDash 1.1 · la página Mapa del diseño C (catalogo/mapa): el mapa de siempre (feature/map, las mismas
 * teselas y su caché) a pantalla completa en los colores de hielo, y encima la velocidad, "¿A dónde
 * vamos?" con Casa, Trabajo y Favorito, los botones redondos, el zoom y la música. El GPS y las descargas
 * corren solo mientras la página se ve.
 */

/** Dónde va tu posición en el mapa (fracción de 1024 × 528): 560, 300 como en el mockup. */
private val PUCK_ANCHOR = Offset(560f / 1024f, 300f / 528f)

/** El mapa termina donde empieza el dock. */
private val MAP_HEIGHT = 528.dp

@Composable
fun MapaPage(d: Dependencies, apps: List<App>, visible: Boolean, actions: PageActions) {
    val ice = LocalIce.current
    // Los botones de zoom, capas y pantalla completa: de esta página, no del widget del inicio viejo.
    var zoom by rememberSaveable { mutableStateOf(MapZoom.AUTO) }
    var tilted by rememberSaveable { mutableStateOf(false) }
    var clean by rememberSaveable { mutableStateOf(false) }
    val look = MapLook(view = if (tilted) MapView.TILTED else MapView.FLAT, zoom = zoom, style = MapStyle.DARK, detail = MapDetail.MINIMAL)
    Box(Modifier.fillMaxSize().background(Color(0xFF0A1019))) {
        IceMap(d, look, visible, Modifier.size(1024.dp, MAP_HEIGHT)) { clean = false }
        // .map::after: más oscuro arriba y abajo, para que se lean las tarjetas.
        Box(Modifier.size(1024.dp, MAP_HEIGHT).drawBehind {
            val bg = Color(4, 7, 14)
            drawRect(Brush.verticalGradient(0f to bg.copy(alpha = 0.55f), 0.24f to bg.copy(alpha = 0f), 0.7f to bg.copy(alpha = 0f), 1f to bg.copy(alpha = 0.6f)))
        })
        if (!clean) {
            SpeedCard(d, Modifier.offset(16.dp, 16.dp).size(236.dp, 192.dp), actions.onSpeed)
            SearchPill(d, apps, Modifier.offset(268.dp, 16.dp).size(496.dp, 64.dp), actions.onPanel)
            QuickButtons(d, apps) { clean = true }
            MapControl(PageIcons.layers, Modifier.offset(896.dp, 328.dp)) { tilted = !tilted }
            MapControl(PageIcons.locate, Modifier.offset(956.dp, 328.dp), tint = ice.ice) { zoom = MapZoom.AUTO }
            MapControl(PageIcons.plus, Modifier.offset(896.dp, 388.dp)) { zoom = closer(zoom) }
            MapControl(PageIcons.minus, Modifier.offset(956.dp, 388.dp)) { zoom = farther(zoom) }
            MapMusic(d, Modifier.offset(16.dp, 384.dp).size(352.dp, 128.dp), actions.onMusic)
        } else {
            // Sin tarjetas: solo el botón para volver a verlas.
            QuickButton(PageIcons.fullscreen, Modifier.offset(956.dp, 22.dp), on = true) { clean = false }
        }
        Attribution(Modifier.align(Alignment.TopEnd).offset(x = (-16).dp, y = 503.dp))
    }
}

/** Un zoom más cerca (el automático pasa al más cercano). */
private fun closer(z: MapZoom) = when (z) {
    MapZoom.AUTO, MapZoom.MID, MapZoom.NEAR -> MapZoom.NEAR
    MapZoom.FAR -> MapZoom.MID
}

/** Un zoom más lejos (el automático pasa al más lejano). */
private fun farther(z: MapZoom) = when (z) {
    MapZoom.AUTO, MapZoom.MID, MapZoom.FAR -> MapZoom.FAR
    MapZoom.NEAR -> MapZoom.MID
}

/**
 * El mapa en vivo, en los colores de hielo: el GPS y las teselas corren solo mientras [visible] y la
 * app está al frente. Sin permiso, el botón para darlo; sin posición todavía, el aviso de siempre.
 */
@Composable
private fun IceMap(d: Dependencies, look: MapLook, visible: Boolean, modifier: Modifier, onTap: () -> Unit) {
    val context = LocalContext.current
    val ice = LocalIce.current
    val density = LocalDensity.current.density
    val (granted, ask) = rememberGpsPermission(d)
    if (visible) KeepTripRunning(d.trip)
    val store = remember(density) { newTileStore(context, MapDetail.MINIMAL, MinimalPalette.ICE, density) }
    val covered = LocalHomeCovered.current
    LifecycleStartEffect(store, visible, covered) {
        if (visible && !covered) store.resume()
        onStopOrDispose { store.pause() }
    }
    val position = d.trip.position
    val auto = rememberSignal(d, Signal.SPEED)
    val kmh = if (look.zoom == MapZoom.AUTO) auto.value else null
    Box(modifier) {
        when {
            !granted -> Box(Modifier.align(Alignment.Center).offset(y = 30.dp).clip(RoundedCornerShape(24.dp)).background(Color(170, 210, 255).copy(alpha = 0.1f)).border(1.dp, ice.line, RoundedCornerShape(24.dp)).clickable(onClick = ask).padding(horizontal = 22.dp, vertical = 14.dp)) {
                IceText(stringResource(R.string.map_enable_gps), iceStyle(16f, FontWeight.Medium), ice.title)
            }
            position == null -> IceText(stringResource(R.string.map_waiting), iceStyle(16f), ice.ink2, modifier = Modifier.align(Alignment.Center).offset(y = 30.dp))
            else -> MapCanvas(
                store, position.latitude, position.longitude, d.trip.bearing, kmh, look, Modifier.fillMaxSize(),
                onTap = onTap, anchor = PUCK_ANCHOR, puck = { at, bearing -> drawIcePuck(at, bearing) }, attribution = false,
            )
        }
    }
}

/** Tu posición como en el mockup: dos discos de hielo y la flecha blanca con su borde. */
private fun DrawScope.drawIcePuck(at: Offset, bearing: Float?) {
    val u = 1.dp.toPx()
    drawCircle(Color(111, 191, 255).copy(alpha = 0.10f), 46 * u, at)
    drawCircle(Color(111, 191, 255).copy(alpha = 0.20f), 26 * u, at)
    drawCircle(Color(190, 230, 255).copy(alpha = 0.55f), 26 * u, at, style = Stroke(1.2f * u))
    if (bearing == null) {
        drawCircle(Color.White, 7 * u, at)
        return
    }
    val arrow = Path().apply {
        moveTo(at.x, at.y - 15 * u)
        lineTo(at.x + 11 * u, at.y + 12 * u)
        lineTo(at.x, at.y + 6 * u)
        lineTo(at.x - 11 * u, at.y + 12 * u)
        close()
    }
    rotate(bearing, at) {
        // drop-shadow(0 0 6px rgba(150,210,255,.9)), sin desenfoque: un halo tenue.
        drawCircle(Brush.radialGradient(listOf(Color(150, 210, 255).copy(alpha = 0.45f), Color.Transparent), at, 20 * u), 20 * u, at)
        drawPath(arrow, Color.White)
        drawPath(arrow, Color(0xFFBFE6FF), style = Stroke(1.5f * u, join = StrokeJoin.Round))
    }
}

/** Los créditos de los datos del mapa (los piden sus licencias); tocarlos abre su página. */
@Composable
private fun Attribution(modifier: Modifier) {
    val uri = LocalUriHandler.current
    IceText(
        "© OpenStreetMap", iceStyle(13f, display = false), Color(196, 216, 238).copy(alpha = 0.5f),
        modifier = modifier.clickable { runCatching { uri.openUri("https://www.openstreetmap.org/copyright") } },
    )
}

// ---------------------------------------------------------------- tarjetas

/** .spd: la marca, la velocidad grande, el lugar y el clima. Un toque abre el modo galaxia. */
@Composable
private fun SpeedCard(d: Dependencies, modifier: Modifier, onSpeed: () -> Unit) {
    val ice = LocalIce.current
    val miles = d.prefs.units.miles
    val shown = rememberShownSpeed(d, miles)
    val place = rememberPlaceName(d)
    Box(modifier.darkGlass(22.dp).clip(RoundedCornerShape(22.dp)).clickable(onClick = onSpeed).padding(horizontal = 18.dp, vertical = 16.dp)) {
        Column {
            BrandTitle(d.prefs.carName?.takeIf { it.isNotBlank() } ?: "Chevrolet Sail")
            Row(Modifier.padding(top = 12.dp)) {
                val glow = Shadow(Color(150, 210, 255).copy(alpha = 0.25f), Offset.Zero, 24f)
                IceText(shown.value.toString(), iceStyle(96f, FontWeight.ExtraLight, spacing = -0.02f).copy(shadow = glow), ice.speed, modifier = Modifier.alignByBaseline())
                Spacer(Modifier.width(10.dp))
                IceText(stringResource(if (miles) R.string.speed_unit_mph else R.string.speed_unit_kmh), iceStyle(18f, FontWeight.Light), ice.ink2, modifier = Modifier.alignByBaseline())
            }
        }
        Row(Modifier.align(Alignment.BottomStart).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            if (place != null) LocationLabel(place, Modifier.weight(1f)) else Spacer(Modifier.weight(1f))
            WeatherInline(d, 22.dp, iceStyle(20f, FontWeight.Light), 6.dp)
        }
    }
}

/** .srch: "¿A dónde vamos?" (abre tu app de mapas) y las fichas Casa, Trabajo y Favorito. */
@Composable
private fun SearchPill(d: Dependencies, apps: List<App>, modifier: Modifier, onPanel: (Panel) -> Unit) {
    val context = LocalContext.current
    val ice = LocalIce.current
    val map = mapApp(d, apps)
    fun go(address: String?) {
        if (address != null) navigateTo(context, map, address) else onPanel(Panel.MusicSettings)
    }
    Row(
        modifier.darkGlass(32.dp).clip(RoundedCornerShape(32.dp)).padding(start = 20.dp, end = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(Modifier.weight(1f).height(64.dp).clickable { openNavigation(context, d.apps, map) }, verticalAlignment = Alignment.CenterVertically) {
            Icon(IceIcons.search, ice.ice, 24.dp)
            Spacer(Modifier.width(10.dp))
            IceText(stringResource(R.string.map_where_to), iceStyle(18f), ice.ink2)
        }
        Box(Modifier.size(1.dp, 28.dp).background(ice.line))
        Spacer(Modifier.width(10.dp))
        PlaceChip(IceIcons.house, Color(0xFF8FF0CF), stringResource(R.string.place_home)) { go(d.prefs.homeAddress) }
        Spacer(Modifier.width(10.dp))
        PlaceChip(IceIcons.briefcase, ice.ice, stringResource(R.string.place_work)) { go(d.prefs.workAddress) }
        Spacer(Modifier.width(10.dp))
        PlaceChip(IceIcons.star, Color(0xFFFFD88A), null) { go(d.prefs.favoriteAddress) }
    }
}

/** .ch: ficha redonda de 44 dp de alto con ícono y nombre (sin nombre, redonda). */
@Composable
private fun PlaceChip(icon: ImageVector, tint: Color, label: String?, onClick: () -> Unit) {
    val ice = LocalIce.current
    val shape = RoundedCornerShape(22.dp)
    Row(
        Modifier
            .height(44.dp)
            .then(if (label == null) Modifier.width(44.dp) else Modifier)
            .clip(shape)
            .background(Color(170, 210, 255).copy(alpha = 0.07f))
            .border(1.dp, ice.line2, shape)
            .clickable(onClick = onClick)
            .then(if (label != null) Modifier.padding(start = 10.dp, end = 14.dp) else Modifier),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(icon, tint, 20.dp)
        if (label != null) {
            Spacer(Modifier.width(8.dp))
            IceText(label, iceStyle(15f, FontWeight.Medium), ice.ink)
        }
    }
}

/** .qb: Wi-Fi y Bluetooth (encendidos si están conectados), WhatsApp y pantalla completa. */
@Composable
private fun QuickButtons(d: Dependencies, apps: List<App>, onClean: () -> Unit) {
    val context = LocalContext.current
    val links = rememberLinks()
    QuickButton(if (links.wifi) IceIcons.wifi else IceIcons.wifiOff, Modifier.offset(776.dp, 22.dp), on = links.wifi) { openSystemSettings(context, Settings.ACTION_WIFI_SETTINGS) }
    QuickButton(IceIcons.bluetooth, Modifier.offset(836.dp, 22.dp), on = links.bluetooth) { openSystemSettings(context, Settings.ACTION_BLUETOOTH_SETTINGS) }
    QuickButton(IceIcons.whatsApp, Modifier.offset(896.dp, 22.dp), tint = Color(0xFF7EE2A8)) {
        d.apps.firstOf(apps, "com.whatsapp", "com.whatsapp.w4b")?.let { d.apps.open(context, it) }
    }
    QuickButton(PageIcons.fullscreen, Modifier.offset(956.dp, 22.dp), onClick = onClean)
}

/** Botón redondo de 52 dp: vidrio oscuro o, [on], azul encendido con su brillo. */
@Composable
private fun QuickButton(icon: ImageVector, modifier: Modifier, on: Boolean = false, tint: Color = Color(0xFFE6F3FF), onClick: () -> Unit) {
    val m = if (on) modifier.size(52.dp).drawWithCache {
        val r = size.width / 2
        val glow = Brush.radialGradient(0f to Color(110, 180, 255).copy(alpha = 0.45f), (r / (r + 16.dp.toPx())) to Color(110, 180, 255).copy(alpha = 0.3f), 1f to Color.Transparent, center = Offset(r, r), radius = r + 16.dp.toPx())
        onDrawBehind { drawCircle(glow, r + 16.dp.toPx()) }
    }.glass(listOf(0f to Color(80, 160, 240).copy(alpha = 0.85f), 1f to Color(50, 110, 200).copy(alpha = 0.85f)), Color(190, 230, 255).copy(alpha = 0.6f), Color(235, 246, 255).copy(alpha = 0.10f), 26.dp, angle = 135f)
    else modifier.size(52.dp).darkGlass(26.dp)
    Box(m.clip(CircleShape).clickable(onClick = onClick), contentAlignment = Alignment.Center) { Icon(icon, tint, 24.dp) }
}

/** .ctl: botón cuadrado redondeado de 52 dp (capas, ubicarte, acercar, alejar). */
@Composable
private fun MapControl(icon: ImageVector, modifier: Modifier, tint: Color = Color(0xFFE6F3FF), radius: Dp = 16.dp, onClick: () -> Unit) {
    Box(modifier.size(52.dp).darkGlass(radius).clip(RoundedCornerShape(radius)).clickable(onClick = onClick), contentAlignment = Alignment.Center) { Icon(icon, tint, 24.dp) }
}

/** .mus: carátula de 96, la app (SPOTIFY), título, artista y los controles. */
@Composable
private fun MapMusic(d: Dependencies, modifier: Modifier, onExpand: () -> Unit) {
    val ice = LocalIce.current
    val repo = rememberMusic(d)
    val song = repo.song
    Box(modifier.darkGlass(22.dp).clip(RoundedCornerShape(22.dp)).clickable(onClick = if (repo.needsAccess) repo::requestPermission else onExpand)) {
        SongArt(song, RoundedCornerShape(14.dp), Modifier.offset(16.dp, 16.dp).size(96.dp))
        Column(Modifier.padding(start = 128.dp, top = 16.dp, end = 14.dp)) {
            val pkg = song?.pkg.orEmpty()
            val spotify = pkg == SPOTIFY_PKG
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(if (spotify || pkg.isBlank()) IceIcons.spotify else IceIcons.music, if (spotify || pkg.isBlank()) Color(0xFF5FE08F) else ice.ice, 16.dp)
                Spacer(Modifier.width(6.dp))
                IceText(
                    (if (pkg.isNotBlank()) repo.appName(pkg) else repo.preferredName().ifBlank { "Spotify" }).uppercase(),
                    iceStyle(13f, FontWeight.Medium, spacing = 0.14f), if (spotify || pkg.isBlank()) Color(0xFF5FE08F) else ice.ice,
                )
            }
            IceText(song?.title?.ifBlank { repo.appName(song.pkg) } ?: stringResource(R.string.music_idle), iceStyle(18f, FontWeight.Medium, lineHeight = 1.1f), Color.White, modifier = Modifier.padding(top = 7.dp))
            IceText(
                song?.artist ?: if (repo.needsAccess) stringResource(R.string.tap_to_enable) else stringResource(R.string.music_idle_hint),
                // Hasta donde empieza el play (como en el mockup, el artista va a su izquierda).
                iceStyle(13f, display = false), ice.ink2, modifier = Modifier.padding(top = 4.dp).widthIn(max = 76.dp),
            )
        }
        // .c: left 122, right 14, bottom 10, alto 48: anterior, play de 48 y siguiente, repartidos.
        Row(
            Modifier.align(Alignment.BottomStart).padding(start = 122.dp, end = 14.dp, bottom = 10.dp).fillMaxWidth().height(48.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BareButton(IceIcons.previous, { repo.previous() }, iconSize = 24.dp)
            WhitePlay(song?.playing == true, 48.dp, 24.dp, { repo.toggle() }, glow = 18.dp)
            BareButton(IceIcons.next, { repo.next() }, iconSize = 24.dp)
        }
    }
}
