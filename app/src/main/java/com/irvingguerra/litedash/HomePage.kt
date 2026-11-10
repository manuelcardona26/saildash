package com.irvingguerra.litedash

import android.provider.Settings
import android.text.format.DateFormat
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.irvingguerra.litedash.core.ui.Icon
import com.irvingguerra.litedash.core.ui.ice.BrandTitle
import com.irvingguerra.litedash.core.ui.ice.GlassCard
import com.irvingguerra.litedash.core.ui.ice.IceChip
import com.irvingguerra.litedash.core.ui.ice.IceDark
import com.irvingguerra.litedash.core.ui.ice.IceIconButton
import com.irvingguerra.litedash.core.ui.ice.IceIconChip
import com.irvingguerra.litedash.core.ui.ice.IceIcons
import com.irvingguerra.litedash.core.ui.ice.IcePalette
import com.irvingguerra.litedash.core.ui.ice.IceText
import com.irvingguerra.litedash.core.ui.ice.IceType
import com.irvingguerra.litedash.core.ui.ice.LocalIce
import com.irvingguerra.litedash.core.ui.ice.LocationLabel
import com.irvingguerra.litedash.core.ui.ice.Pill
import com.irvingguerra.litedash.core.ui.ice.VDivider
import com.irvingguerra.litedash.core.ui.ice.cssLinearGradient
import com.irvingguerra.litedash.core.ui.ice.glass
import com.irvingguerra.litedash.core.ui.ice.iceGlass
import com.irvingguerra.litedash.core.ui.ice.softGlow
import com.irvingguerra.litedash.core.vehicle.Signal
import com.irvingguerra.litedash.core.vehicle.watch
import com.irvingguerra.litedash.feature.apps.App
import com.irvingguerra.litedash.feature.car.CarDrawing
import com.irvingguerra.litedash.feature.car.CarStill
import com.irvingguerra.litedash.feature.music.Song
import com.irvingguerra.litedash.feature.music.rememberPosition
import com.irvingguerra.litedash.feature.obd.ObdState
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/*
 * SailDash 1.1 · la página Inicio del diseño C (mock-C/resumen.html), medida por medida: cada
 * offset/size de aquí es el left/top/width del HTML en dp, dentro del lienzo de 1024 × 600 ([IceFrame]).
 */

/** Lo que el Inicio pide al inicio de afuera (paneles, la música en grande, otra página). */
class InicioActions(
    val onPanel: (Panel) -> Unit,
    val onMusic: () -> Unit,
    val onCar: () -> Unit,
    /** La tarjeta de Bluetooth (dejando presionado el mosaico de Bluetooth). */
    val onBluetooth: () -> Unit = {},
)

/**
 * La página Inicio. [visible] = se ve ahora (página elegida, sin nada encima): solo entonces se anima
 * el satélite (y solo con el carro en movimiento).
 */
@Composable
fun InicioPage(d: Dependencies, apps: List<App>, revision: Int, visible: Boolean, actions: InicioActions) {
    val ice = LocalIce.current
    Box(Modifier.fillMaxSize()) {
        // Fondo: el espacio con su viñeta, el agujero negro, la sombra del piso y la luz de los faros, en una sola imagen.
        if (ice.dark) Image(painterResource(R.drawable.bg_inicio), null, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
        else com.irvingguerra.litedash.core.ui.ice.IceBackdrop(R.drawable.bg_espacio)

        CarHero(d, actions.onCar)
        SpeedBlock(d, visible)
        Header(d)
        Destinations(d, apps, actions)
        WeatherCard(d, Modifier.offset(592.dp, 16.dp).size(364.dp, 140.dp)) { actions.onPanel(Panel.Settings) }
        ConnectionTiles(Modifier.offset(592.dp, 168.dp).size(364.dp, 58.dp), actions.onBluetooth)
        TelemetryPill(d, Modifier.offset(592.dp, 238.dp).size(364.dp, 46.dp)) { actions.onPanel(Panel.Workshop) }
        MusicCard(d, revision, Modifier.offset(592.dp, 298.dp).size(364.dp, 210.dp), actions.onMusic)
    }
}

// ---------------------------------------------------------------- encabezado

@Composable
private fun Header(d: Dependencies) {
    val ice = LocalIce.current
    val place = rememberPlaceName(d)
    // .hdr: left 34, right 452 (ancho 538), top 20, alto 44; marca a la izquierda, contraste y lugar a la derecha.
    Row(Modifier.offset(34.dp, 20.dp).size(538.dp, 44.dp), verticalAlignment = Alignment.CenterVertically) {
        BrandTitle(d.prefs.carName?.takeIf { it.isNotBlank() } ?: DEFAULT_CAR_NAME, Modifier.weight(1f))
        IceIconButton(IceIcons.contrast, onClick = { d.prefs.theme = if (ice.dark) ThemeMode.LIGHT else ThemeMode.DARK })
        if (place != null) {
            Spacer(Modifier.width(14.dp))
            LocationLabel(place)
        }
    }
}

/** El carro del dueño se llama así de fábrica (Ajustes → Tu carro lo cambia). */
private const val DEFAULT_CAR_NAME = "Chevrolet Sail"

// ---------------------------------------------------------------- velocidad y órbita

/** El satélite está aquí con el carro quieto (donde lo dibuja el mockup: x 96, y 19.8 de la órbita). */
private val SAT_REST = Math.toDegrees(atan2(19.8 / 30.0, 96.0 / 128.0)).toFloat()

/** Una vuelta del satélite en 24 s (como la animación del mockup) a lo más 30 cuadros por segundo. */
private const val SAT_PERIOD_S = 24f
private const val FRAME_MS = 33L

/** Más rápido que esto el carro va andando y el satélite gira. */
private const val MOVING_KMH = 3f

@Composable
private fun SpeedBlock(d: Dependencies, visible: Boolean) {
    val ice = LocalIce.current
    val obdOn = rememberObd(d)?.state == ObdState.ON
    val (gps, askGps) = rememberGpsPermission(d)
    // Sin OBD, el GPS publica la velocidad en el bus mientras el Inicio se ve.
    if (!obdOn && gps && visible) KeepTripRunning(d.trip)
    val moving by remember { derivedStateOf { (d.bus.value(Signal.SPEED) ?: 0f) > MOVING_KMH } }
    // El ángulo se lee solo al dibujar: girar no vuelve a armar la pantalla.
    val angle = remember { mutableFloatStateOf(SAT_REST) }
    LaunchedEffect(visible && moving) {
        if (!(visible && moving)) return@LaunchedEffect
        var last = withFrameNanos { it }
        while (true) {
            delay(FRAME_MS)
            val now = withFrameNanos { it }
            angle.floatValue = (angle.floatValue + (now - last) / 1e9f * 360f / SAT_PERIOD_S) % 360f
            last = now
        }
    }
    val orbit = Modifier.offset((-40).dp, 40.dp).size(360.dp, 180.dp)
    Canvas(orbit) { drawOrbit(ice, front = false, angle.floatValue) }
    Row(
        Modifier
            .offset(30.dp, 56.dp)
            .clickable(remember { MutableInteractionSource() }, indication = null, enabled = !obdOn && !gps, onClick = askGps),
    ) {
        SpeedNumber(d)
    }
    Canvas(orbit) { drawOrbit(ice, front = true, angle.floatValue) }
}

/** El número y su unidad, en su propio bloque: cada lectura nueva solo vuelve a escribir esto. */
@Composable
private fun SpeedNumber(d: Dependencies) {
    val ice = LocalIce.current
    val miles = d.prefs.units.miles
    val kmh = d.bus.watch(Signal.SPEED)
    val shown = ((kmh ?: 0f).toMiles(miles)).roundToInt().coerceAtLeast(0)
    val glow = if (ice.dark) Shadow(Color(150, 210, 255).copy(alpha = 0.25f), Offset.Zero, 28f) else null
    Row {
        IceText(shown.toString(), IceType.speed.copy(shadow = glow), ice.speed, maxLines = 1, modifier = Modifier.alignByBaseline())
        Spacer(Modifier.width(12.dp))
        IceText(
            stringResource(if (miles) R.string.speed_unit_mph else R.string.speed_unit_kmh), IceType.speedUnit, ice.ink2,
            maxLines = 1, modifier = Modifier.alignByBaseline(),
        )
    }
}

/**
 * La órbita del mockup (svg.orbit, viewBox 360 × 180): dos elipses inclinadas −12° alrededor de
 * (150, 96); la mitad de atrás pasa detrás del número y la de adelante encima, con el satélite.
 */
private fun DrawScope.drawOrbit(ice: IcePalette, front: Boolean, angle: Float) {
    val k = size.width / 360f
    val tint = if (ice.dark) Color(0xFFBFE6FF) else ice.ice
    val tint2 = if (ice.dark) Color(0xFF9FD4FF) else ice.ice2
    val og = Brush.horizontalGradient(
        0f to tint.copy(alpha = 0f), 0.35f to tint.copy(alpha = 0.55f), 0.75f to tint.copy(alpha = 0.35f), 1f to tint.copy(alpha = 0f),
        startX = -128f, endX = 128f,
    )
    val og2 = Brush.horizontalGradient(
        0f to tint2.copy(alpha = 0f), 0.5f to tint2.copy(alpha = 0.22f), 1f to tint2.copy(alpha = 0f),
        startX = -176f, endX = 176f,
    )
    withTransform({
        scale(k, k, pivot = Offset.Zero)
        translate(150f, 96f)
        rotate(-12f, pivot = Offset.Zero)
    }) {
        val start = if (front) 0f else 180f
        drawArc(og, start, 180f, false, Offset(-128f, -30f), Size(256f, 60f), style = Stroke(if (front) 1.3f else 1.1f))
        drawArc(
            og2, start, 180f, false, Offset(-176f, -46f), Size(352f, 92f),
            style = Stroke(1f, pathEffect = if (front) null else PathEffect.dashPathEffect(floatArrayOf(2f, 5f))),
        )
        val t = Math.toRadians(angle.toDouble())
        val s = sin(t).toFloat()
        if ((s >= 0f) == front) {
            val p = Offset(128f * cos(t).toFloat(), 30f * s)
            val glow = Brush.radialGradient(
                0f to Color.White, 0.35f to Color(0xFFCFEEFF).copy(alpha = 0.9f), 1f to Color(0xFF9FD4FF).copy(alpha = 0f),
                center = p, radius = 9f,
            )
            drawCircle(glow, 9f, p)
            drawCircle(Color.White, 2.2f, p)
        }
    }
}

// ---------------------------------------------------------------- el carro

@Composable
private fun CarHero(d: Dependencies, onTap: () -> Unit) {
    val context = LocalContext.current
    val ice = LocalIce.current
    val owner = remember { OwnerCar.of(context) }
    LaunchedEffect(owner) { owner.load() }
    val tap = Modifier.clickable(remember { MutableInteractionSource() }, indication = null, onClick = onTap)
    // El escenario (svg.stage): la mitad de atrás de las elipses va detrás del carro.
    Canvas(Modifier.offset(28.dp, 306.dp).size(550.dp, 140.dp)) { drawStage(ice, front = false) }
    val image = owner.image
    if (image != null) {
        // .car: left 30, top 170, ancho 543 (el alto, el de la foto).
        val w = 543.dp
        Image(
            image.bitmap.asImageBitmap(), stringResource(R.string.my_car_fallback),
            Modifier.offset(30.dp, 170.dp).size(w, w * image.ratio).then(tap),
            contentScale = ContentScale.FillBounds,
        )
    } else if (owner.loaded) {
        // Sin foto: el auto 3D del catálogo (quieto) o el dibujo de LiteDash, en el mismo lugar.
        val pkg = d.cars.pkg(d.prefs.carModel)
        val m = Modifier.offset(60.dp, 190.dp).size(480.dp, 250.dp).then(tap)
        if (pkg != null) CarStill(d.cars, pkg, d.prefs.carPaint, m) else Box(m, contentAlignment = Alignment.Center) { CarDrawing(d.prefs.carColor, Modifier.fillMaxWidth()) }
    }
    Canvas(Modifier.offset(28.dp, 306.dp).size(550.dp, 140.dp)) { drawStage(ice, front = true) }
    // La luz de los faros (.bloom), solo de noche: de front.json, sobre la foto.
    if (image != null && ice.dark) for ((i, h) in image.headlights.withIndex()) {
        val r = h.glow * 543f
        val box = (r * 1.9f).coerceIn(40f, 200f)
        val cx = 30f + h.x * 543f
        val cy = 170f + h.y * 543f
        Box(
            Modifier
                .offset((cx - box / 2).dp, (cy - box / 2).dp)
                .size(box.dp)
                .drawWithCache {
                    val brush = Brush.radialGradient(
                        0f to Color(255, 244, 222).copy(alpha = 0.42f), 0.35f to Color(255, 236, 205).copy(alpha = 0.16f), 0.70f to Color(255, 236, 205).copy(alpha = 0f),
                        center = Offset(size.width / 2f, size.height / 2f), radius = size.width * 0.7071f,
                    )
                    val alpha = if (i > 0 && r > 70f) 0.75f else 1f
                    onDrawBehind { drawRect(brush, alpha = alpha) }
                },
        )
    }
}

/** svg.stage (550 × 140): la elipse del piso (centro 275, 70; 260 × 52) y la punteada de adentro. */
private fun DrawScope.drawStage(ice: IcePalette, front: Boolean) {
    val k = size.width / 550f
    val a = if (ice.dark) Color(0xFFA9DCFF) else ice.ice
    val b = if (ice.dark) Color(0xFFD9F0FF) else ice.ice2
    fun stops(x0: Float, x1: Float) = Brush.horizontalGradient(
        0f to a.copy(alpha = 0f), 0.2f to a.copy(alpha = 0.35f), 0.5f to b.copy(alpha = 0.55f), 0.8f to a.copy(alpha = 0.35f), 1f to a.copy(alpha = 0f),
        startX = x0, endX = x1,
    )
    scale(k, k, pivot = Offset.Zero) {
        if (!front) {
            drawArc(stops(15f, 535f), 180f, 180f, false, Offset(15f, 18f), Size(520f, 104f), style = Stroke(1f), alpha = 0.55f)
        } else {
            drawArc(stops(15f, 535f), 0f, 180f, false, Offset(15f, 18f), Size(520f, 104f), style = Stroke(1.2f))
            drawArc(
                stops(50f, 500f), 0f, 180f, false, Offset(50f, 42f), Size(450f, 80f),
                style = Stroke(1f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(1.5f, 6f))), alpha = 0.6f,
            )
        }
    }
}

// ---------------------------------------------------------------- destinos

@Composable
private fun Destinations(d: Dependencies, apps: List<App>, actions: InicioActions) {
    val context = LocalContext.current
    val ice = LocalIce.current
    val map = mapApp(d, apps)
    val setUp = stringResource(R.string.place_tap_to_set)
    fun go(address: String?) {
        if (address != null) navigateTo(context, map, address) else actions.onPanel(Panel.MusicSettings)
    }
    val mint = if (ice.dark) Color(0xFF8FF0CF) else ice.mint
    // .chips: left 32, top 436, alto 72, separadas 10: Casa 190, Trabajo 160, Favorito 88 y Buscar 72.
    Row(Modifier.offset(32.dp, 436.dp).size(540.dp, 72.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        IceChip(stringResource(R.string.place_home), d.prefs.homeAddress ?: setUp, IceIcons.house, mint, { go(d.prefs.homeAddress) }, Modifier.width(190.dp))
        IceChip(stringResource(R.string.place_work), d.prefs.workAddress ?: setUp, IceIcons.briefcase, ice.ice, { go(d.prefs.workAddress) }, Modifier.width(160.dp))
        IceIconChip(IceIcons.star, ice.gold, stringResource(R.string.place_favorite), { go(d.prefs.favoriteAddress) }, Modifier.width(88.dp))
        IceIconChip(IceIcons.search, ice.ink, null, { openNavigation(context, d.apps, map) }, Modifier.width(72.dp))
    }
}

// ---------------------------------------------------------------- clima y pico y placa

@Composable
private fun WeatherCard(d: Dependencies, modifier: Modifier, onSettings: () -> Unit) {
    val ice = LocalIce.current
    val repo = d.weather
    val enable = rememberEnableWeather(repo)
    val weather = repo.weather
    val now = rememberMinute()
    val locale = currentLocale()
    val sky = weather?.let { skyOf(it.code) }
    val drizzle = weather != null && weather.code in 51..57
    val night = weather?.day == false
    val skyImage = when (sky) {
        null -> R.drawable.sky_nublado
        Sky.RAIN, Sky.STORM -> R.drawable.sky_llovizna
        Sky.CLEAR, Sky.PARTLY -> if (night) R.drawable.sky_noche else R.drawable.sky_despejado
        Sky.CLOUDY, Sky.FOG, Sky.SNOW -> R.drawable.sky_nublado
    }
    val shape = RoundedCornerShape(20.dp)
    val tap = if (!repo.active) enable else onSettings
    Box(
        modifier
            .clip(shape)
            .clickable(onClick = tap),
    ) {
        Image(painterResource(skyImage), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        // linear-gradient(160deg, rgba(170,215,255,.10), transparent 60%) encima del cielo, y el borde del vidrio.
        Box(Modifier.fillMaxSize().glass(listOf(0f to Color(170, 215, 255).copy(alpha = 0.10f), 0.6f to Color.Transparent), ice.line, ice.sheen, 20.dp))
        val cardInk = Color(230, 240, 252).copy(alpha = 0.82f)
        val date = remember(now, locale) { capitalize(SimpleDateFormat(if (locale.language == "es") "EEEE d 'de' MMMM" else "EEEE, MMMM d", locale).format(now), locale) }
        IceText(date, IceType.body, cardInk, maxLines = 1, modifier = Modifier.offset(18.dp, 16.dp))
        val today = weather?.days?.firstOrNull()
        if (today != null) IceText(
            stringResource(R.string.weather_max_min, today.max, today.min), IceType.small, IceDark.ink2,
            maxLines = 1, modifier = Modifier.align(Alignment.TopEnd).padding(top = 16.dp, end = 18.dp),
        )
        // .main: 12 dp debajo de la fecha; ícono de 40, temperatura y el cielo.
        Row(Modifier.offset(18.dp, 43.dp).height(44.dp), verticalAlignment = Alignment.CenterVertically) {
            if (weather != null && sky != null) {
                Icon(iceWeatherIcon(sky, weather.day), Color(0xFFD6ECFF), 40.dp)
                Spacer(Modifier.width(12.dp))
                IceText("${weather.temperature}°", IceType.temp, Color.White, maxLines = 1)
                Spacer(Modifier.width(18.dp))
                IceText(stringResource(if (drizzle) R.string.weather_drizzle else textOf(sky)), IceType.condition, Color(0xFFEAF4FF), maxLines = 1)
            } else {
                Icon(IceIcons.cloud, Color(0xFFD6ECFF), 40.dp)
                Spacer(Modifier.width(12.dp))
                Column {
                    IceText(stringResource(if (repo.active) R.string.weather_waiting else R.string.weather_off), IceType.chipTitle, Color.White, maxLines = 1)
                    if (!repo.active) IceText(stringResource(R.string.tap_to_enable), IceType.small, IceDark.ink2, maxLines = 1, modifier = Modifier.padding(top = 4.dp))
                }
            }
        }
        PicoPill(d.picoPlaca, Modifier.align(Alignment.BottomStart).padding(start = 18.dp, bottom = 14.dp)) { onSettings() }
    }
}

/** El ícono del clima en el trazo de la tarjeta (1.5). */
private fun iceWeatherIcon(sky: Sky, day: Boolean) = when (sky) {
    Sky.CLEAR -> if (day) IceIcons.sun else IceIcons.moon
    Sky.PARTLY -> if (day) IceIcons.partly else IceIcons.moon
    Sky.CLOUDY -> IceIcons.cloud
    Sky.FOG -> IceIcons.fog
    Sky.RAIN -> IceIcons.rain
    Sky.STORM -> IceIcons.storm
    Sky.SNOW -> IceIcons.snow
}

private fun capitalize(text: String, locale: Locale) = text.replaceFirstChar { if (it.isLowerCase()) it.titlecase(locale) else it.toString() }

/** La píldora del pico y placa (.pyp) dentro del clima; nada si hoy (o mañana, desde las 5 p. m.) no hay. */
@Composable
private fun PicoPill(repo: PicoPlacaRepository, modifier: Modifier, onClick: () -> Unit) {
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(repo) {
        repo.refreshQuietly()
        while (true) {
            delay(60_000 - System.currentTimeMillis() % 60_000)
            tick++
        }
    }
    val status = remember(tick, repo.enabled, repo.cityId, repo.digit, repo.rules, repo.customDays, repo.customHours) { repo.status() } ?: return
    val ice = LocalIce.current
    val text = stringResource(
        when {
            status.now -> R.string.pico_pill_now
            status.today -> R.string.pico_pill_today
            else -> R.string.pico_pill_tomorrow
        },
    )
    val hours = status.hours.joinToString(", ")
    Pill(
        modifier.clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick),
        height = 28.dp,
        background = Color(6, 12, 22).copy(alpha = 0.45f),
        border = (if (status.now) ice.danger else Color(255, 214, 120)).copy(alpha = if (status.now) 0.6f else 0.28f),
    ) {
        Spacer(Modifier.width(10.dp))
        Icon(IceIcons.plate, if (status.now) ice.danger else IceDark.amber, 18.dp)
        Spacer(Modifier.width(8.dp))
        val line = buildAnnotatedString {
            append(text)
            append(' ')
            withStyle(SpanStyle(color = Color.White, fontWeight = FontWeight.Medium)) { append(hours) }
        }
        IceText(line, IceType.small, Color(240, 232, 214).copy(alpha = 0.92f))
        Spacer(Modifier.width(12.dp))
    }
}

// ---------------------------------------------------------------- Wi-Fi y Bluetooth

@Composable
private fun ConnectionTiles(modifier: Modifier, onBluetooth: () -> Unit) {
    val context = LocalContext.current
    val links = rememberLinks()
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        LinkTile(
            if (links.wifi) IceIcons.wifi else IceIcons.wifiOff, stringResource(R.string.wifi_title),
            stringResource(if (links.wifi) R.string.wifi_connected else R.string.wifi_disconnected), links.wifi,
            Modifier.weight(1f),
        ) { openSystemSettings(context, Settings.ACTION_WIFI_SETTINGS) }
        LinkTile(
            IceIcons.bluetooth, stringResource(R.string.bt_title),
            if (links.bluetooth) links.bluetoothName ?: stringResource(R.string.bt_tile_connected) else stringResource(R.string.bt_disconnected), links.bluetooth,
            Modifier.weight(1f),
            onLongClick = onBluetooth,
        ) { openSystemSettings(context, Settings.ACTION_BLUETOOTH_SETTINGS) }
    }
}

/** Mosaico de conexión (.tile): prendido, vidrio azul con brillo y punto; apagado, vidrio normal. */
@Composable
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
private fun LinkTile(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String, on: Boolean, modifier: Modifier, onLongClick: (() -> Unit)? = null, onClick: () -> Unit) {
    val ice = LocalIce.current
    val shape = RoundedCornerShape(18.dp)
    val surface = if (on) Modifier.glass(ice.tileOn, ice.tileOnBorder, if (ice.dark) Color(235, 246, 255).copy(alpha = 0.18f) else ice.sheen, 18.dp, angle = 135f)
    else Modifier.iceGlass(18.dp)
    Row(
        modifier.fillMaxSize().then(surface).clip(shape).combinedClickable(onLongClick = onLongClick, onClick = onClick).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val iconColor = if (on) (if (ice.dark) Color(0xFFD8F0FF) else ice.ice2) else ice.ink3
        Box(contentAlignment = Alignment.Center) {
            if (on && ice.dark) Box(Modifier.size(22.dp).softGlow(ice.iceGlow.copy(alpha = 0.25f), 6.dp))
            Icon(icon, iconColor, 24.dp)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            IceText(title, IceType.tileTitle, ice.title, maxLines = 1)
            IceText(subtitle, IceType.small, if (ice.dark) Color(222, 238, 255).copy(alpha = if (on) 0.78f else 0.6f) else ice.ink2, maxLines = 1, modifier = Modifier.padding(top = 5.dp))
        }
        if (on) Box(Modifier.softGlow(Color(0xFF8FD0FF).copy(alpha = 0.6f), 6.dp).size(8.dp).clip(CircleShape).background(if (ice.dark) Color(0xFFBFE6FF) else ice.ice))
    }
}

// ---------------------------------------------------------------- telemetría

/**
 * La píldora de datos (.tel). Con el OBD conectado: temperatura del motor, voltaje y gasolina. Sin
 * OBD, lo que de verdad se sabe por GPS (km del viaje y promedio) y "Conecta OBD"; nunca inventa.
 */
@Composable
private fun TelemetryPill(d: Dependencies, modifier: Modifier, onObd: () -> Unit) {
    val ice = LocalIce.current
    val obdOn = rememberObd(d)?.state == ObdState.ON
    val locale = currentLocale()
    GlassCard(modifier, radius = 23.dp) {
        Row(Modifier.fillMaxSize().padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceAround) {
            if (obdOn) {
                val units = d.prefs.units
                val coolant = d.bus.watch(Signal.COOLANT)
                val volts = d.bus.watch(Signal.VOLTAGE)
                val fuel = d.bus.watch(Signal.FUEL)
                TeleItem(IceIcons.thermometer, coolant?.toFahrenheit(units.fahrenheit)?.roundToInt()?.toString() ?: DASH, if (units.fahrenheit) "°F" else "°C")
                VDivider(18.dp)
                TeleItem(IceIcons.battery, volts?.let { String.format(locale, "%.1f", it) } ?: DASH, "V")
                VDivider(18.dp)
                TeleItem(IceIcons.fuel, fuel?.roundToInt()?.toString() ?: DASH, "%")
            } else {
                val miles = d.prefs.units.miles
                val trip = d.trip
                val km = (trip.meters / 1000.0).toFloat().toMiles(miles)
                val hours = if (trip.startedAt == 0L) 0.0 else (System.currentTimeMillis() - trip.startedAt) / 3_600_000.0
                val avg = if (trip.meters >= 100 && hours > 1.0 / 60) (trip.meters / 1000.0 / hours).toFloat().toMiles(miles).roundToInt().toString() else DASH
                TeleItem(IceIcons.route, String.format(locale, "%.1f", km), if (miles) "mi" else stringResource(R.string.tele_trip_km))
                VDivider(18.dp)
                TeleItem(IceIcons.gauge, avg, if (miles) "mph" else stringResource(R.string.tele_avg))
                VDivider(18.dp)
                Row(
                    Modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick = onObd).padding(horizontal = 4.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(IceIcons.plug, ice.ice, 20.dp)
                    Spacer(Modifier.width(8.dp))
                    IceText(stringResource(R.string.tele_connect_obd), IceType.small, ice.ink2, maxLines = 1)
                }
            }
        }
    }
}

private const val DASH = "–"

@Composable
private fun TeleItem(icon: androidx.compose.ui.graphics.vector.ImageVector, value: String, unit: String) {
    val ice = LocalIce.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, ice.ink3, 20.dp)
        Spacer(Modifier.width(8.dp))
        // .tel div: alineados al centro (no a la línea base) y la unidad 4 dp más cerca (margin-left: -4px).
        IceText(value, IceType.value, if (ice.dark) Color(0xFFF2F8FF) else ice.ink, maxLines = 1)
        Spacer(Modifier.width(4.dp))
        IceText(unit, IceType.valueUnit, ice.ink2, maxLines = 1)
    }
}

// ---------------------------------------------------------------- música

@Composable
private fun MusicCard(d: Dependencies, revision: Int, modifier: Modifier, onExpand: () -> Unit) {
    val ice = LocalIce.current
    val repo = d.music
    DisposableEffect(repo) {
        repo.use()
        onDispose { repo.release() }
    }
    val needsAccess = remember(revision, repo.needsAccess) { repo.needsAccess && !repo.hasPermission().also { if (it) repo.recheck() } }
    val song = repo.song
    GlassCard(modifier, onClick = if (needsAccess) repo::requestPermission else onExpand) {
        Artwork(song, Modifier.offset(18.dp, 18.dp).size(88.dp))
        Column(Modifier.padding(start = 124.dp, top = 26.dp, end = 18.dp)) {
            if (song == null) {
                IceText(stringResource(R.string.music_idle), IceType.songTitle, ice.title, maxLines = 1)
                IceText(
                    if (needsAccess) stringResource(R.string.tap_to_enable) else repo.preferredName().ifBlank { stringResource(R.string.music_idle_hint) },
                    IceType.songArtist, ice.ink2, maxLines = 1, modifier = Modifier.padding(top = 6.dp),
                )
            } else {
                IceText(song.title.ifBlank { repo.appName(song.pkg) }, IceType.songTitle, ice.title, maxLines = 1)
                IceText(song.artist, IceType.songArtist, ice.ink2, maxLines = 1, modifier = Modifier.padding(top = 6.dp))
                if (song.pkg.isNotBlank()) Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    val spotify = song.pkg == SPOTIFY
                    Icon(if (spotify) IceIcons.spotify else IceIcons.music, if (spotify) Color(0xFF1ED760) else ice.ice, 16.dp)
                    Spacer(Modifier.width(6.dp))
                    IceText(repo.appName(song.pkg), IceType.small, ice.ink3, maxLines = 1)
                }
            }
        }
        Progress(song, Modifier.offset(18.dp, 124.dp).width(328.dp))
        // .ctrl: abajo a 14 dp, 62 de alto, centrados y separados 44.
        Row(
            Modifier.align(Alignment.BottomCenter).padding(bottom = 14.dp).height(62.dp),
            horizontalArrangement = Arrangement.spacedBy(44.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ControlButton(IceIcons.previous) { repo.previous() }
            PlayButton(song?.playing == true) { repo.toggle() }
            ControlButton(IceIcons.next) { repo.next() }
        }
    }
}

private const val SPOTIFY = "com.spotify.music"

/** La carátula de 88 dp; sin carátula, el agujero negro del mockup (.art) dibujado. */
@Composable
internal fun Artwork(song: Song?, modifier: Modifier) {
    val shape = RoundedCornerShape(14.dp)
    val bmp = song?.artwork
    Box(modifier.clip(shape)) {
        if (bmp != null) Image(bmp.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else Box(Modifier.fillMaxSize().drawWithCache {
            val c = Offset(size.width / 2f, size.height * 0.56f)
            val dp = density
            val base = cssLinearGradient(160f, size, listOf(0f to Color(0xFF1C3352), 0.7f to Color(0xFF0A1222), 1f to Color(0xFF0A1222)))
            val ring = Brush.radialGradient(
                0f to Color.Transparent, (17f / 24f) to Color.Transparent, (18.5f / 24f) to Color(255, 206, 140).copy(alpha = 0.9f), 1f to Color(255, 170, 90).copy(alpha = 0f),
                center = c, radius = 24f * dp,
            )
            val disk = Brush.radialGradient(
                0f to Color(255, 214, 150).copy(alpha = 0.95f), 0.6f to Color(255, 170, 90).copy(alpha = 0.35f), 1f to Color(255, 170, 90).copy(alpha = 0f),
                center = c, radius = 46f * dp,
            )
            onDrawBehind {
                drawRect(base)
                drawCircle(ring, 24f * dp, c)
                // El disco: un óvalo de 46 × 9.
                scale(1f, 9f / 46f, pivot = c) { drawCircle(disk, 46f * dp, c) }
                drawCircle(Color.Black, 17.5f * dp, c)
            }
        })
        Box(Modifier.fillMaxSize().border(1.dp, Color.White.copy(alpha = 0.12f), shape))
    }
}

/** Tiempo, barra y duración (.prog); sin duración conocida, la barra vacía. */
@Composable
private fun Progress(song: Song?, modifier: Modifier) {
    val ice = LocalIce.current
    val pos = song?.let { rememberPosition(it).value } ?: 0L
    val dur = song?.durationMs ?: 0L
    val f = if (dur > 0) (pos.toFloat() / dur).coerceIn(0f, 1f) else 0f
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        IceText(if (dur > 0) time(pos) else "0:00", IceType.small, ice.ink3, maxLines = 1)
        Spacer(Modifier.width(10.dp))
        Box(
            Modifier
                .weight(1f)
                .height(9.dp)
                .drawWithCache {
                    val h = 3.dp.toPx()
                    val y = (size.height - h) / 2f
                    val track = if (ice.dark) Color(190, 225, 255).copy(alpha = 0.14f) else ice.line
                    val fillW = size.width * f
                    val fill = Brush.horizontalGradient(listOf(Color(0xFF7CC6FF), Color(0xFFE3F4FF)), endX = fillW.coerceAtLeast(1f))
                    val knob = 4.5.dp.toPx()
                    val glow = Brush.radialGradient(listOf(Color(190, 230, 255).copy(alpha = 0.7f), Color.Transparent), Offset(fillW, size.height / 2f), knob * 2.6f)
                    onDrawBehind {
                        drawRoundRect(track, Offset(0f, y), Size(size.width, h), androidx.compose.ui.geometry.CornerRadius(h))
                        if (dur > 0) {
                            drawRoundRect(fill, Offset(0f, y), Size(fillW, h), androidx.compose.ui.geometry.CornerRadius(h))
                            drawCircle(glow, knob * 2.6f, Offset(fillW, size.height / 2f))
                            drawCircle(Color.White, knob, Offset(fillW, size.height / 2f))
                        }
                    }
                },
        )
        Spacer(Modifier.width(10.dp))
        IceText(if (dur > 0) time(dur) else "0:00", IceType.small, ice.ink3, maxLines = 1)
    }
}

private fun time(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, s / 60 % 60, s % 60) else "%d:%02d".format(s / 60, s % 60)
}

@Composable
private fun ControlButton(icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    val ice = LocalIce.current
    Box(Modifier.size(48.dp).clip(CircleShape).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, if (ice.dark) Color(0xFFEAF4FF) else ice.ink, 26.dp)
    }
}

/** El play blanco de 62 dp con su aro y su brillo (.ctrl .play). */
@Composable
private fun PlayButton(playing: Boolean, onClick: () -> Unit) {
    val ice = LocalIce.current
    Box(
        Modifier
            .size(62.dp)
            .drawWithCache {
                val r = size.width / 2f
                val glow = Brush.radialGradient(
                    0f to Color(170, 220, 255).copy(alpha = 0.45f), (r / (r + 24.dp.toPx())) to Color(170, 220, 255).copy(alpha = 0.2f),
                    ((r + 9.dp.toPx()) / (r + 24.dp.toPx())) to Color(170, 220, 255).copy(alpha = 0.06f), 1f to Color.Transparent,
                    center = Offset(size.width / 2f, size.height / 2f), radius = r + 24.dp.toPx(),
                )
                val ring = Color(190, 228, 255).copy(alpha = 0.08f)
                onDrawBehind {
                    if (ice.dark) drawCircle(glow, r + 24.dp.toPx())
                    drawCircle(ring, r + 6.dp.toPx())
                }
            }
            .clip(CircleShape)
            .background(if (ice.dark) Color.White else ice.ink)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        val tint = if (ice.dark) Color(0xFF0A1322) else Color.White
        if (playing) Icon(IceIcons.pause, tint, 28.dp)
        else Icon(IceIcons.play, tint, 28.dp, Modifier.offset(x = 1.5.dp))
    }
}

/** Hora "2:52" y su marca (a. m./p. m.) aparte, como en el dock; con reloj de 24 h, sin marca. */
@Composable
fun dockTime(): Triple<String, String?, String> {
    val context = LocalContext.current
    val now = rememberMinute()
    val locale = currentLocale()
    val h24 = DateFormat.is24HourFormat(context)
    val am = stringResource(R.string.am_marker)
    val pm = stringResource(R.string.pm_marker)
    return remember(now, locale, h24, am, pm) {
        val clock = SimpleDateFormat(if (h24) "H:mm" else "h:mm", locale).format(now)
        val marker = if (h24) null else if (Calendar.getInstance().apply { time = now }.get(Calendar.AM_PM) == Calendar.AM) am else pm
        val date = SimpleDateFormat(if (locale.language == "es") "EEE, d MMM" else "EEE, MMM d", locale).format(now).replace(".", "")
        Triple(clock, marker, date)
    }
}
