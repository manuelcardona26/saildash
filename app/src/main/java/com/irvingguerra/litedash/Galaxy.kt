package com.irvingguerra.litedash

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.irvingguerra.litedash.core.ui.Icon
import com.irvingguerra.litedash.core.ui.ice.IceDark
import com.irvingguerra.litedash.core.ui.ice.IceText
import com.irvingguerra.litedash.feature.car.CarStill
import com.irvingguerra.litedash.feature.car.REAR_TURN

/*
 * SailDash 1.1 · el modo galaxia: la cara nueva de "Solo velocidad" (su lógica y sus límites siguen en
 * SpeedOnly.kt / SpeedOnlyRules.kt). De noche, a pantalla completa: Gargantúa con la Vía Láctea doblada
 * alrededor (bg_galaxia, una sola imagen), la carretera que corre hacia él, tu Sail de espaldas
 * manejando, la velocidad enorme y delgada, la hora chiquita y de dónde sale el dato.
 *
 * Barato: el fondo es una imagen, lo quieto de la carretera se pinta una vez ([RoadScene]) y solo las
 * rayas avanzan, a lo más a 30 cuadros por segundo y solo con el carro andando.
 */

/** La carretera del modo galaxia: se pierde justo en la sombra del agujero negro (512, 180 del fondo). */
internal val GalaxiaRoad = RoadProjection(vanishX = 512f, vanishY = 190.5f, bottom = 600f, left = 12f)

internal val GalaxiaSpec = RoadSpec(
    projection = GalaxiaRoad,
    top = 186f,
    bottom = 600f,
    // Nace en el disco de luz del agujero y se hace firme hacia abajo.
    fade = listOf(0f to 0f, 14f to 0f, 70f to 0.45f, 210f to 1f),
    far = 3000f,
    bands = listOf(
        // El asfalto deja ver un poco de la galaxia: de noche, en el espacio.
        RoadStripe(0f, 1000f) to listOf(0f to Color(5, 8, 16, 0), 0.14f to Color(6, 10, 20).copy(alpha = 0.86f), 0.86f to Color(6, 10, 20).copy(alpha = 0.86f), 1f to Color(5, 8, 16, 0)),
        RoadStripe(300f, 700f) to listOf(0f to Color(110, 185, 255, 0), 0.12f to Color(110, 185, 255).copy(alpha = 0.12f), 0.5f to Color(110, 185, 255).copy(alpha = 0.03f), 0.88f to Color(110, 185, 255).copy(alpha = 0.12f), 1f to Color(110, 185, 255, 0)),
        // El brillo de las rayas, con el dorado del disco de acreción.
        RoadStripe(268f, 332f) to WARM_GLOW,
        RoadStripe(668f, 732f) to WARM_GLOW,
    ),
    edges = listOf(RoadStripe(100f, 104f), RoadStripe(896f, 900f)),
    edgeColor = Color(200, 232, 255).copy(alpha = 0.6f),
    dashes = listOf(RoadStripe(295f, 304f), RoadStripe(696f, 705f)),
    dashColor = Color(255, 246, 232).copy(alpha = 0.95f),
    posts = listOf(RoadStripe(60f, 72f), RoadStripe(928f, 940f)),
    postColor = Color(255, 200, 140).copy(alpha = 0.8f),
    sideColor = Color(3, 5, 12),
    sideTop = 250f,
    sideBottom = 600f,
    sideWidth = 260f,
    sideFadeIn = 140f,
)

private val WARM_GLOW get() = listOf(0f to Color(255, 190, 120, 0), 0.5f to Color(255, 205, 150).copy(alpha = 0.30f), 1f to Color(255, 190, 120, 0))

/**
 * El modo galaxia, a pantalla completa en el lienzo de 1024 × 600. [view] y [status] son los de Solo
 * velocidad; [limit] es la alerta (en la unidad que se ve); [askGps] no null = falta el permiso.
 * [onOptions] abre la alerta (el toque en la campana no cierra la pantalla).
 */
@Composable
internal fun GalaxyScene(
    d: Dependencies,
    view: State<SpeedOnlyView>,
    status: State<SpeedOnlyStatus>,
    limit: Int?,
    miles: Boolean,
    unit: String,
    askGps: (() -> Unit)?,
    onOptions: () -> Unit,
) {
    val phase = rememberRoadPhase(d, visible = true)
    Box(Modifier.fillMaxSize()) {
        Image(painterResource(R.drawable.bg_galaxia), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        // Un poco más oscuro abajo, donde va la carretera (sin tapar a Gargantúa).
        Box(Modifier.fillMaxSize().drawWithCache {
            val shade = Brush.verticalGradient(0.45f to Color(2, 4, 10, 0), 1f to Color(2, 4, 10).copy(alpha = 0.55f))
            onDrawBehind { drawRect(shade) }
        })
        RoadScene(GalaxiaSpec, { phase.floatValue }, Modifier.fillMaxSize())
        GalaxyCar(d)
        GalaxySpeed(view, limit?.let { speedOnlyFraction(it, miles) }, unit, askGps)
        // Arriba: la hora chiquita a la izquierda y de dónde sale el dato a la derecha.
        IceText(rememberClock().full, iceStyle(24f, FontWeight.Light), Color(222, 236, 252).copy(alpha = 0.78f), modifier = Modifier.offset(32.dp, 30.dp))
        SourceChip(status, Modifier.align(Alignment.TopEnd).offset(x = (-32).dp, y = 24.dp))
        AlertPill(view, limit, unit, Modifier.offset(32.dp, 532.dp), onOptions)
        IceText(
            stringResource(R.string.velocidad_tap_to_exit), iceStyle(13f, display = false), Color(196, 216, 238).copy(alpha = 0.5f),
            modifier = Modifier.align(Alignment.BottomEnd).offset(x = (-32).dp, y = (-30).dp),
        )
    }
}

/** Tu Sail de espaldas en el carril de en medio, con su sombra, sus calaveras y la luz hacia el agujero. */
@Composable
private fun GalaxyCar(d: Dependencies) {
    val context = LocalContext.current
    val owner = remember { OwnerCar.of(context) }
    LaunchedEffect(owner) { owner.load() }
    // La luz de los faros sobre el asfalto, hacia el agujero negro.
    Box(Modifier.offset(412.dp, 268.dp).size(200.dp, 170.dp).drawWithCache {
        val w = size.width
        val h = size.height
        val beam = Path().apply { moveTo(w * 0.24f, h); lineTo(w * 0.76f, h); lineTo(w * 0.55f, 0f); lineTo(w * 0.45f, 0f); close() }
        val brush = Brush.verticalGradient(0f to Color(235, 244, 255, 0), 1f to Color(235, 244, 255).copy(alpha = 0.16f))
        onDrawBehind { drawPath(beam, brush) }
    })
    Box(Modifier.offset(362.dp, 500.dp).size(300.dp, 110.dp).drawWithCache {
        val c = Offset(size.width / 2, size.height * 0.4f)
        val r = size.width / 2
        val brush = Brush.radialGradient(0f to Color(255, 40, 40).copy(alpha = 0.22f), 0.7f to Color(255, 40, 40, 0), center = c, radius = r)
        onDrawBehind { scale(1f, size.height / size.width, pivot = c) { drawCircle(brush, r, c) } }
    })
    Box(Modifier.offset(407.dp, 556.dp).size(210.dp, 40.dp).drawWithCache {
        val c = Offset(size.width / 2, size.height / 2)
        val r = size.width / 2
        val brush = Brush.radialGradient(0f to Color.Black.copy(alpha = 0.85f), 1f to Color.Transparent, center = c, radius = r)
        onDrawBehind { scale(1f, size.height / size.width, pivot = c) { drawCircle(brush, r, c) } }
    })
    val rear = owner.rear
    if (rear != null) {
        val w = 172.dp
        val h = w * (rear.height.toFloat() / rear.width)
        Image(rear.asImageBitmap(), null, Modifier.offset(426.dp, 584.dp - h).size(w, h), contentScale = ContentScale.FillBounds)
        // Las calaveras, a la altura de las de la foto (como .tail de la Carretera, a esta escala).
        val k = 172f / 132f
        for (x in listOf(443f, 543f)) {
            val cx = 426f + (x + 19f - 446f) * k
            val cy = 584f - h.value + (383f + 19f - 303f) * k
            Box(Modifier.offset((cx - 25f).dp, (cy - 25f).dp).size(50.dp).drawWithCache {
                val r = size.width / 2
                val brush = Brush.radialGradient(
                    0f to Color(255, 110, 100).copy(alpha = 0.7f), 0.4f to Color(255, 40, 40).copy(alpha = 0.25f), 0.7f to Color(255, 40, 40, 0),
                    center = Offset(r, r), radius = r * 1.4142f,
                )
                onDrawBehind { drawCircle(brush, r) }
            })
        }
    } else if (owner.loaded) {
        val pkg = d.cars.pkg(d.prefs.carModel)
        if (pkg != null) CarStill(d.cars, pkg, d.prefs.carPaint, Modifier.offset(382.dp, 410.dp).size(260.dp, 190.dp), turn = REAR_TURN, reflection = false)
        else Canvas(Modifier.offset(302.dp, 420.dp).size(420.dp, 180.dp)) { drawRear(d.prefs.carColor) }
    }
}

/**
 * La velocidad enorme y delgada a la izquierda, con su unidad y una línea fina que se llena (la rayita es
 * la alerta). Rojo por encima del límite; apagado sin dato. Solo esto se recompone al cambiar el número.
 */
@Composable
private fun GalaxySpeed(view: State<SpeedOnlyView>, limitFraction: Float?, unit: String, askGps: (() -> Unit)?) {
    val ice = IceDark
    Column(Modifier.offset(48.dp, 150.dp).width(380.dp)) {
        if (askGps != null) {
            IceText(stringResource(R.string.velocidad_permission_help), iceStyle(18f, FontWeight.Light, lineHeight = 1.3f), ice.ink2, maxLines = 3)
            Box(
                Modifier.padding(top = 18.dp).clip(RoundedCornerShape(24.dp)).background(Color(170, 210, 255).copy(alpha = 0.12f))
                    .border(1.dp, ice.line, RoundedCornerShape(24.dp)).clickable(onClick = askGps).padding(horizontal = 22.dp, vertical = 14.dp),
            ) { IceText(stringResource(R.string.velocidad_enable_gps), iceStyle(16f, FontWeight.Medium), Color.White) }
            return@Column
        }
        val v = view.value
        val color = when {
            v.over -> ice.danger
            v.dim -> ice.ink3
            else -> ice.speed
        }
        val glow = Shadow(if (v.over) Color(255, 90, 80).copy(alpha = 0.45f) else Color(150, 210, 255).copy(alpha = 0.35f), Offset.Zero, 40f)
        IceText(v.text, iceStyle(200f, FontWeight.ExtraLight, spacing = -0.03f).copy(shadow = glow), color)
        IceText(unit, iceStyle(26f, FontWeight.Light, spacing = 0.06f), if (v.over) ice.danger.copy(alpha = 0.8f) else ice.ink2, modifier = Modifier.offset(y = (-16).dp).padding(start = 10.dp))
        // La línea fina: llena hasta la velocidad, con la rayita de la alerta.
        Canvas(Modifier.padding(start = 12.dp, top = 6.dp).size(300.dp, 12.dp).graphicsLayer()) {
            val y = size.height / 2
            val h = 2.dp.toPx()
            drawRoundRect(Color(190, 225, 255).copy(alpha = 0.14f), Offset(0f, y - h / 2), Size(size.width, h), CornerRadius(h))
            val f = v.fraction
            if (f > 0.003f) {
                val w = size.width * f
                val fill = if (v.over) Brush.horizontalGradient(listOf(Color(0xFFFF9A8E), ice.danger), endX = w) else Brush.horizontalGradient(listOf(Color(0xFF5AA9FF), Color(0xFFE6F6FF)), endX = w)
                drawRoundRect(fill, Offset(0f, y - h / 2), Size(w, h), CornerRadius(h))
                drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.6f), Color.Transparent), Offset(w, y), 9.dp.toPx()), 9.dp.toPx(), Offset(w, y))
                drawCircle(Color.White, 3.dp.toPx(), Offset(w, y))
            }
            if (limitFraction != null) {
                val x = size.width * limitFraction
                drawLine(Color(255, 160, 150).copy(alpha = 0.85f), Offset(x, 0f), Offset(x, size.height), strokeWidth = 1.5f.dp.toPx())
            }
        }
    }
}

/** El dato: punto verde con GPS u OBD, "Buscando GPS…" y lo demás en su color. */
@Composable
private fun SourceChip(status: State<SpeedOnlyStatus>, modifier: Modifier) {
    val ice = IceDark
    val s = status.value
    val text = when (s) {
        SpeedOnlyStatus.OBD -> "OBD"
        SpeedOnlyStatus.CAN -> "CAN"
        SpeedOnlyStatus.GPS -> "GPS"
        SpeedOnlyStatus.OBD_WAITING -> stringResource(R.string.velocidad_obd_waiting)
        SpeedOnlyStatus.GPS_SEARCHING -> stringResource(R.string.velocidad_searching)
        SpeedOnlyStatus.GPS_LOST -> stringResource(R.string.velocidad_gps_lost)
        SpeedOnlyStatus.NO_PERMISSION -> stringResource(R.string.velocidad_no_permission)
    }
    val dot = when (s) {
        SpeedOnlyStatus.OBD, SpeedOnlyStatus.CAN, SpeedOnlyStatus.GPS -> Color(0xFF7EF0C2)
        SpeedOnlyStatus.GPS_LOST, SpeedOnlyStatus.NO_PERMISSION -> ice.amber
        else -> ice.ink3
    }
    val shape = RoundedCornerShape(17.dp)
    Row(
        modifier.height(34.dp).clip(shape).background(Color(6, 12, 22).copy(alpha = 0.55f)).border(1.dp, ice.line, shape).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(dot))
        Spacer(Modifier.width(8.dp))
        IceText(text, iceStyle(13f, FontWeight.Medium, spacing = if (s.ok) 0.18f else 0.02f), if (s.ok) ice.ink2 else dot)
    }
}

/**
 * Abajo a la izquierda: pasado el límite, "Vas por encima de 80 km/h" en rojo; si no, la alerta elegida
 * con su campana. Un toque abre las opciones.
 */
@Composable
private fun AlertPill(view: State<SpeedOnlyView>, limit: Int?, unit: String, modifier: Modifier, onClick: () -> Unit) {
    val ice = IceDark
    val over = view.value.over && limit != null
    val shape = RoundedCornerShape(19.dp)
    Row(
        modifier
            .height(38.dp)
            .clip(shape)
            .background(if (over) Color(60, 14, 14).copy(alpha = 0.6f) else Color(6, 12, 22).copy(alpha = 0.5f))
            .border(1.dp, if (over) ice.danger.copy(alpha = 0.55f) else ice.line, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(PageIcons.bell, if (over) ice.danger else if (limit != null) ice.ice else ice.ink3, 18.dp)
        Spacer(Modifier.width(10.dp))
        val text = when {
            over -> stringResource(R.string.galaxy_over, limit!!, unit)
            limit != null -> stringResource(R.string.velocidad_alert_on, limit, unit)
            else -> stringResource(R.string.velocidad_alert_off)
        }
        IceText(text, iceStyle(14f, if (over) FontWeight.Medium else FontWeight.Normal), if (over) Color(0xFFFFB4AC) else ice.ink2)
    }
}
