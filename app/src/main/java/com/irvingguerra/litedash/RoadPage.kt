package com.irvingguerra.litedash

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.irvingguerra.litedash.core.ui.Icon
import com.irvingguerra.litedash.core.ui.ice.BrandTitle
import com.irvingguerra.litedash.core.ui.ice.GlassCard
import com.irvingguerra.litedash.core.ui.ice.IceBackdrop
import com.irvingguerra.litedash.core.ui.ice.IceIcons
import com.irvingguerra.litedash.core.ui.ice.IcePalette
import com.irvingguerra.litedash.core.ui.ice.IceText
import com.irvingguerra.litedash.core.ui.ice.LocalIce
import com.irvingguerra.litedash.core.ui.ice.LocationLabel
import com.irvingguerra.litedash.core.vehicle.Signal
import com.irvingguerra.litedash.feature.car.CarStill
import com.irvingguerra.litedash.feature.car.REAR_TURN
import com.irvingguerra.litedash.feature.obd.ObdState
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/*
 * SailDash 1.1 · la página Carretera del diseño C (mock-C/carretera.html y .gif): la carretera en
 * perspectiva hacia el agujero negro, con las rayas y los reflectores avanzando a la velocidad de verdad,
 * tu Sail de espaldas, la velocidad grande con su arco, el viaje, la telemetría y la música en una barra.
 */

/** El arco de la velocidad llega hasta aquí (62 km/h llenan el 34 % del arco en el mockup). */
private const val GAUGE_MAX_KMH = 180f
private const val GAUGE_MAX_MPH = 110f

/** Del arco: empieza abajo a la izquierda (150°) y da 240°, con 18 marcas (una larga cada 3). */
private const val ARC_START = 150f
private const val ARC_SWEEP = 240f
private const val ARC_TICKS = 18

/** La página Carretera. [visible] = se ve ahora: solo entonces corren el GPS y la carretera. */
@Composable
fun CarreteraPage(d: Dependencies, visible: Boolean, actions: PageActions) {
    val ice = LocalIce.current
    val obdOn = rememberObd(d)?.state == ObdState.ON
    val (gps, askGps) = rememberGpsPermission(d)
    // Sin OBD, el GPS da la velocidad para que las rayas avancen (y los datos del viaje).
    if (!obdOn && gps && visible) KeepTripRunning(d.trip)
    val phase = rememberRoadPhase(d, visible)
    Box(Modifier.fillMaxSize()) {
        IceBackdrop(R.drawable.bg_espacio)
        if (ice.dark) {
            Image(
                painterResource(R.drawable.blackhole_small), null,
                Modifier.offset(447.dp, 126.dp).size(130.dp, 80.dp).alpha(0.6f), contentScale = ContentScale.Fit,
            )
            // .horizon: el resplandor donde la carretera se pierde.
            Box(Modifier.offset(0.dp, 120.dp).size(1024.dp, 150.dp).drawWithCache {
                val c = Offset(size.width / 2, size.height * 0.52f)
                val rx = size.width * 0.38f
                val brush = Brush.radialGradient(0f to Color(140, 200, 255).copy(alpha = 0.13f), 0.7f to Color(140, 200, 255).copy(alpha = 0f), center = c, radius = rx)
                onDrawBehind { scale(1f, size.height * 0.5f / rx, pivot = c) { drawCircle(brush, rx, c) } }
            })
        }
        RoadScene(CarreteraSpec, { phase.floatValue }, Modifier.fillMaxSize())
        RearCar(d, Modifier)
        Header(d)
        SpeedGauge(d, actions.onSpeed, if (!obdOn && !gps) askGps else null)
        TripRow(d, Modifier.offset(52.dp, 336.dp).width(224.dp))
        Telemetry(d, Modifier.offset(730.dp, 92.dp).width(222.dp)) { actions.onPanel(Panel.Workshop) }
        MusicBar(d, Modifier.offset(306.dp, 452.dp).size(412.dp, 58.dp), actions.onMusic)
    }
}

// ---------------------------------------------------------------- el carro de espaldas

/** Tu Sail de espaldas (rear.png) con su sombra, la luz de los faros hacia adelante y las calaveras. */
@Composable
private fun RearCar(d: Dependencies, modifier: Modifier) {
    val context = LocalContext.current
    val ice = LocalIce.current
    val owner = remember { OwnerCar.of(context) }
    LaunchedEffect(owner) { owner.load() }
    Box(modifier.fillMaxSize()) {
        if (ice.dark) {
            // .beam (432, 214; 160 × 110): la luz de los faros sobre el asfalto, hacia adelante.
            Box(Modifier.offset(432.dp, 214.dp).size(160.dp, 110.dp).drawWithCache {
                val w = size.width
                val h = size.height
                val beam = Path().apply {
                    moveTo(w * 0.30f, h); lineTo(w * 0.70f, h); lineTo(w * 0.56f, 0f); lineTo(w * 0.44f, 0f); close()
                }
                val brush = Brush.verticalGradient(0f to Color(235, 244, 255, 0), 1f to Color(235, 244, 255).copy(alpha = 0.20f))
                onDrawBehind { drawPath(beam, brush) }
            })
            // .redglow (392, 392; 240 × 90): el rojo de las calaveras en el piso.
            Box(Modifier.offset(392.dp, 392.dp).size(240.dp, 90.dp).drawWithCache {
                val c = Offset(size.width / 2, size.height * 0.4f)
                val r = size.width / 2
                val brush = Brush.radialGradient(0f to Color(255, 40, 40).copy(alpha = 0.24f), 0.7f to Color(255, 40, 40, 0), center = c, radius = r)
                onDrawBehind { scale(1f, size.height / size.width, pivot = c) { drawCircle(brush, r, c) } }
            })
        }
        // .shadow (432, 426; 160 × 34).
        Box(Modifier.offset(432.dp, 426.dp).size(160.dp, 34.dp).drawWithCache {
            val c = Offset(size.width / 2, size.height / 2)
            val r = size.width / 2
            val brush = Brush.radialGradient(0f to Color.Black.copy(alpha = 0.8f), 1f to Color.Transparent, center = c, radius = r)
            onDrawBehind { scale(1f, size.height / size.width, pivot = c) { drawCircle(brush, r, c) } }
        })
        val rear = owner.rear
        if (rear != null) {
            // .rear: left 446, top 303, ancho 132.
            val w = 132.dp
            Image(rear.asImageBitmap(), stringResource(R.string.my_car_fallback), Modifier.offset(446.dp, 303.dp).size(w, w * (rear.height.toFloat() / rear.width)), contentScale = ContentScale.FillBounds)
            if (ice.dark) for (x in listOf(443, 543)) TailLight(Modifier.offset(x.dp, 383.dp))
        } else if (owner.loaded) {
            // Sin foto: el auto 3D de espaldas (como la carretera de LiteDash) o su dibujo sencillo.
            val pkg = d.cars.pkg(d.prefs.carModel)
            val m = Modifier.offset(402.dp, 296.dp).size(220.dp, 160.dp)
            if (pkg != null) CarStill(d.cars, pkg, d.prefs.carPaint, m, turn = REAR_TURN, reflection = false)
            else Canvas(Modifier.offset(332.dp, 300.dp).size(360.dp, 160.dp)) { drawRear(d.prefs.carColor) }
        }
    }
}

/** `.tail`: el brillo de una calavera (38 dp). */
@Composable
private fun TailLight(modifier: Modifier) {
    Box(modifier.size(38.dp).drawWithCache {
        val r = size.width / 2
        val brush = Brush.radialGradient(
            0f to Color(255, 110, 100).copy(alpha = 0.7f), 0.4f to Color(255, 40, 40).copy(alpha = 0.25f), 0.7f to Color(255, 40, 40, 0),
            center = Offset(r, r), radius = r * 1.4142f,
        )
        onDrawBehind { drawCircle(brush, r) }
    })
}

// ---------------------------------------------------------------- encabezado

@Composable
private fun Header(d: Dependencies) {
    val place = rememberPlaceName(d)
    // .hdr: left 30, right 30, top 16, alto 44, separados 22: marca, lugar, …, pico y placa, clima.
    Row(Modifier.offset(30.dp, 16.dp).size(964.dp, 44.dp), verticalAlignment = Alignment.CenterVertically) {
        BrandTitle(d.prefs.carName?.takeIf { it.isNotBlank() } ?: "Chevrolet Sail")
        if (place != null) {
            Spacer(Modifier.width(22.dp))
            LocationLabel(place)
        }
        Spacer(Modifier.weight(1f))
        PicoPill(d.picoPlaca, Modifier.padding(end = 22.dp), height = 30.dp) {}
        WeatherInline(d, 26.dp, iceStyle(22f, FontWeight.Light), 8.dp)
    }
}

// ---------------------------------------------------------------- la velocidad

/**
 * svg.gauge (34, 74; 260 × 250) y .spd: el arco de 240° con sus marcas y el satélite en la punta, y el
 * número grande. Un toque abre el modo galaxia; sin permiso de GPS ni OBD, lo pide ([askGps]).
 */
@Composable
private fun SpeedGauge(d: Dependencies, onSpeed: () -> Unit, askGps: (() -> Unit)?) {
    val ice = LocalIce.current
    val miles = d.prefs.units.miles
    val shown = rememberShownSpeed(d, miles).value
    val fraction = (shown / if (miles) GAUGE_MAX_MPH else GAUGE_MAX_KMH).coerceIn(0f, 1f)
    Canvas(Modifier.offset(34.dp, 74.dp).size(260.dp, 250.dp)) { drawRoadGauge(ice, fraction) }
    Column(
        Modifier
            .offset(34.dp, 146.dp)
            .width(260.dp)
            .clickable(remember { MutableInteractionSource() }, indication = null, onClick = askGps ?: onSpeed),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val glow = if (ice.dark) Shadow(Color(150, 210, 255).copy(alpha = 0.3f), Offset.Zero, 24f) else null
        IceText(shown.toString(), iceStyle(88f, FontWeight.ExtraLight, spacing = -0.02f).copy(shadow = glow, textAlign = TextAlign.Center), ice.speed)
        IceText(
            stringResource(if (askGps != null) R.string.tap_to_enable else if (miles) R.string.speed_unit_mph else R.string.speed_unit_kmh),
            iceStyle(18f, FontWeight.Light, spacing = 0.06f), ice.ink2, modifier = Modifier.padding(top = 4.dp),
        )
    }
}

/** El arco del mockup (viewBox 260 × 250, centro 130, 124; radio 96) lleno hasta [f] (0–1). */
private fun DrawScope.drawRoadGauge(ice: IcePalette, f: Float) {
    val k = size.width / 260f
    scale(k, k, pivot = Offset.Zero) {
        val c = Offset(130f, 124f)
        // El brillo de adentro (radio 90) y la órbita punteada inclinada −16°.
        drawCircle(Brush.radialGradient(0f to Color(0xFF9FD4FF).copy(alpha = 0.10f), 1f to Color(0xFF9FD4FF).copy(alpha = 0f), center = c, radius = 90f), 90f, c)
        rotate(-16f, c) {
            drawOval(
                Color(0xFFBFE6FF).copy(alpha = 0.22f), Offset(c.x - 126f, c.y - 33f), Size(252f, 66f),
                style = Stroke(1f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(2f, 5f))),
            )
        }
        val box = Size(192f, 192f)
        val tl = Offset(c.x - 96f, c.y - 96f)
        drawArc(Color(190, 225, 255).copy(alpha = 0.13f), ARC_START, ARC_SWEEP, false, tl, box, style = Stroke(5f, cap = StrokeCap.Round))
        if (f > 0.003f) {
            val sweep = ARC_SWEEP * f
            // drop-shadow(0 0 6px): un trazo ancho y tenue debajo, sin desenfoque.
            drawArc(Color(140, 205, 255).copy(alpha = 0.18f), ARC_START, sweep, false, tl, box, style = Stroke(13f, cap = StrokeCap.Round))
            // Del azul hielo al blanco, siguiendo el arco hasta la punta.
            val end = (ARC_START + sweep) / 360f
            val brush = Brush.sweepGradient(0f to Color(0xFFE6F6FF), (ARC_START - 6f) / 360f to Color(0xFF5AA9FF), end.coerceAtMost(1f) to Color(0xFFE6F6FF), 1f to Color(0xFFE6F6FF), center = c)
            drawArc(brush, ARC_START, sweep, false, tl, box, style = Stroke(5f, cap = StrokeCap.Round))
        }
        // Las marcas, por fuera: largas (106–114) cada 3, cortas (106–110); encendidas hasta la velocidad.
        for (i in 0..ARC_TICKS) {
            val a = ARC_START + ARC_SWEEP * i / ARC_TICKS
            val long = i % 3 == 0
            val rad = Math.toRadians(a.toDouble())
            val dir = Offset(cos(rad).toFloat(), sin(rad).toFloat())
            val lit = i.toFloat() / ARC_TICKS <= f + 0.001f
            drawLine(
                Color(0xFFCFEAFF).copy(alpha = if (lit) 0.7f else 0.28f), c + dir * 106f, c + dir * (if (long) 114f else 110f),
                strokeWidth = if (long) 1.4f else 1f,
            )
        }
        // El satélite en la punta.
        val a = Math.toRadians((ARC_START + ARC_SWEEP * f).toDouble())
        val p = c + Offset(cos(a).toFloat(), sin(a).toFloat()) * 96f
        drawCircle(Brush.radialGradient(0f to Color.White, 0.35f to Color(0xFFCFEEFF).copy(alpha = 0.9f), 1f to Color(0xFF9FD4FF).copy(alpha = 0f), center = p, radius = 13f), 13f, p)
        drawCircle(Color.White, 3.2f, p)
    }
}

// ---------------------------------------------------------------- el viaje

/** .trip: VIAJE, TIEMPO y PROM., repartidos en 224 dp. Lo que no se sabe va con "–". */
@Composable
private fun TripRow(d: Dependencies, modifier: Modifier) {
    val trip = d.trip
    val miles = d.prefs.units.miles
    val locale = currentLocale()
    // El tiempo avanza solo, una vez por minuto.
    val minutes by produceState(0L, trip.startedAt) {
        while (true) {
            value = if (trip.startedAt == 0L) 0L else (System.currentTimeMillis() - trip.startedAt) / 60_000
            delay(60_000 - System.currentTimeMillis() % 60_000)
        }
    }
    val km = (trip.meters / 1000.0).toFloat().toMiles(miles)
    val hours = minutes / 60.0
    val avg = if (trip.meters >= 100 && minutes >= 1) (trip.meters / 1000.0 / hours).toFloat().toMiles(miles).roundToInt().toString() else DASH_TEXT
    val speedUnit = stringResource(if (miles) R.string.speed_unit_mph else R.string.speed_unit_kmh)
    Row(modifier, horizontalArrangement = Arrangement.SpaceBetween) {
        TripStat(stringResource(R.string.road_trip_label), if (trip.startedAt == 0L) DASH_TEXT else String.format(locale, "%.1f", km), if (miles) "mi" else "km")
        TripStat(stringResource(R.string.road_time_label), if (trip.startedAt == 0L) DASH_TEXT else minutes.toString(), stringResource(R.string.road_minutes))
        TripStat(stringResource(R.string.road_avg_label), avg, speedUnit)
    }
}

@Composable
private fun TripStat(label: String, value: String, unit: String) {
    val ice = LocalIce.current
    Column {
        IceText(label.uppercase(), iceStyle(13f, display = false, spacing = 0.04f), ice.ink3)
        Row(Modifier.padding(top = 6.dp)) {
            IceText(value, iceStyle(20f), if (ice.dark) Color(0xFFEEF6FF) else ice.ink, modifier = Modifier.alignByBaseline())
            Spacer(Modifier.width(3.dp))
            IceText(unit, iceStyle(14f), ice.ink2, modifier = Modifier.alignByBaseline())
        }
    }
}

// ---------------------------------------------------------------- telemetría

/** .telem: motor, batería y tanque, cada uno con su barrita. Sin OBD, "–" y las barras vacías. */
@Composable
private fun Telemetry(d: Dependencies, modifier: Modifier, onClick: () -> Unit) {
    val ice = LocalIce.current
    val locale = currentLocale()
    val engine = rememberEngine(d)
    val units = d.prefs.units
    val consumption = rememberSignal(d, Signal.CONSUMPTION).value
    GlassCard(modifier, onClick = onClick) {
        Column(Modifier.padding(horizontal = 18.dp, vertical = 6.dp)) {
            val temp = engine.coolant
            TeleRow(
                IceIcons.thermometer, numberOrDash(temp?.toFahrenheit(units.fahrenheit), 0, locale), if (units.fahrenheit) "°F" else "°C",
                stringResource(R.string.road_engine), temp?.let { (it - 50f) / 66f },
            )
            TeleDivider(ice)
            val volts = engine.volts
            TeleRow(
                IceIcons.battery, numberOrDash(volts, 1, locale), "V",
                stringResource(if (volts != null && volts >= CHARGING_V) R.string.road_charging else R.string.road_battery), volts?.let { (it - 11f) / 4f },
            )
            TeleDivider(ice)
            val fuel = engine.fuel
            // La autonomía solo con el consumo real del OBD (nunca inventada).
            val range = if (fuel != null && consumption != null && consumption > 0f) "~${(fuel / 100f * SAIL_TANK_L * consumption).toMiles(units.miles).roundToInt()} ${if (units.miles) "MI" else "KM"}" else stringResource(R.string.road_tank)
            TeleRow(IceIcons.fuel, numberOrDash(fuel, 0, locale), "%", range, fuel?.let { it / 100f })
        }
    }
}

/** Con el motor prendido el alternador carga: más de esto es "Cargando". */
private const val CHARGING_V = 13.2f

/** El tanque del Chevrolet Sail (litros). */
private const val SAIL_TANK_L = 42f

@Composable
private fun TeleDivider(ice: IcePalette) = Box(Modifier.fillMaxWidth().height(1.dp).drawBehind { drawRect(ice.line2) })

@Composable
private fun TeleRow(icon: ImageVector, value: String, unit: String, label: String, fraction: Float?) {
    val ice = LocalIce.current
    Column(Modifier.padding(vertical = 14.dp)) {
        Row(Modifier.fillMaxWidth().height(22.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, ice.ink3, 20.dp)
            Spacer(Modifier.width(10.dp))
            Row {
                IceText(value, iceStyle(22f), ice.title, modifier = Modifier.alignByBaseline())
                Spacer(Modifier.width(2.dp))
                IceText(unit, iceStyle(15f), ice.ink2, modifier = Modifier.alignByBaseline())
            }
            Spacer(Modifier.weight(1f))
            IceText(label.uppercase(), iceStyle(13f, display = false, spacing = 0.05f), ice.ink3)
        }
        val f = (fraction ?: 0f).coerceIn(0f, 1f)
        Box(Modifier.padding(top = 12.dp).fillMaxWidth().height(3.dp).drawWithCache {
            val track = Color(190, 225, 255).copy(alpha = 0.12f)
            val w = size.width * f
            val fill = Brush.horizontalGradient(listOf(Color(0xFF5AA9FF), Color(0xFFDFF3FF)), endX = w.coerceAtLeast(1f))
            val r = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx())
            onDrawBehind {
                drawRoundRect(track, cornerRadius = r)
                if (w > 0.5f) {
                    drawRoundRect(Color(150, 210, 255).copy(alpha = 0.25f), Offset(0f, -1.5f.dp.toPx()), Size(w, size.height + 3.dp.toPx()), r)
                    drawRoundRect(fill, size = Size(w, size.height), cornerRadius = r)
                }
            }
        })
    }
}

// ---------------------------------------------------------------- música

/** .mbar: la música en una barra redonda (carátula, título, artista, anterior, play y siguiente). */
@Composable
private fun MusicBar(d: Dependencies, modifier: Modifier, onExpand: () -> Unit) {
    val ice = LocalIce.current
    val repo = rememberMusic(d)
    val song = repo.song
    Row(
        modifier
            .darkGlass(29.dp, Color(40, 60, 90).copy(alpha = 0.92f), Color(14, 22, 38).copy(alpha = 0.94f))
            .clickable(remember { MutableInteractionSource() }, indication = null, onClick = if (repo.needsAccess) repo::requestPermission else onExpand)
            .padding(start = 9.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SongArt(song, CircleShape, Modifier.size(40.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            IceText(song?.title?.ifBlank { repo.appName(song.pkg) } ?: stringResource(R.string.music_idle), iceStyle(15f, FontWeight.Medium, lineHeight = 1.1f), Color.White)
            IceText(
                song?.artist ?: if (repo.needsAccess) stringResource(R.string.tap_to_enable) else repo.preferredName().ifBlank { stringResource(R.string.music_idle_hint) },
                iceStyle(13f, display = false), ice.ink2, modifier = Modifier.padding(top = 3.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        BareButton(IceIcons.previous, { repo.previous() })
        Spacer(Modifier.width(12.dp))
        WhitePlay(song?.playing == true, 44.dp, 20.dp, { repo.toggle() })
        Spacer(Modifier.width(12.dp))
        BareButton(IceIcons.next, { repo.next() })
    }
}
