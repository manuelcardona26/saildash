package com.irvingguerra.litedash

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.irvingguerra.litedash.core.ui.Icon
import com.irvingguerra.litedash.core.ui.LocalColors
import com.irvingguerra.litedash.core.ui.LocalPlainCard
import com.irvingguerra.litedash.core.ui.ice.IceBackdrop
import com.irvingguerra.litedash.core.ui.ice.IceFrame
import com.irvingguerra.litedash.core.ui.ice.IceText
import com.irvingguerra.litedash.core.ui.ice.LocalIce
import com.irvingguerra.litedash.core.ui.ice.glass
import com.irvingguerra.litedash.core.ui.ice.iceGlass
import com.irvingguerra.litedash.feature.dashboard.TileTypes
import kotlinx.coroutines.delay

/** Valores de muestra para la vista previa: sube, baja y llega a la zona roja. */
private val DEMO = listOf(0.36f, 0.52f, 0.86f, 0.34f, 0.97f, 0.62f)

/**
 * Personalizar un velocímetro, un medidor o la velocidad de la carretera ([tileId]), en el estilo C
 * (catalogo/velocimetros): a la izquierda, grande, cómo queda (con valores de muestra que se mueven) y a
 * la derecha, en vidrio, el estilo, el aro, el color, hasta dónde llega, marcas, números y zona roja. En
 * la carretera, primero lo del viaje y cómo va la velocidad. Cada cambio se guarda al momento. [dock] es
 * el dock del inicio, abajo (como en el mockup).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CustomizeGaugePanel(d: Dependencies, tileId: String, close: () -> Unit, onPickSignal: () -> Unit, dock: (@Composable (Modifier) -> Unit)? = null) {
    val ice = LocalIce.current
    val tile = d.dashboard.tiles.firstOrNull { it.id == tileId }
    if (tile == null) {
        // Cerrar escribe estado: fuera de la composición.
        LaunchedEffect(Unit) { close() }
        return
    }
    val road = tile.type == TileTypes.ROAD
    val roadLook = if (road) RoadLook.parse(tile.data) else null
    val speedometer = tile.type == TileTypes.SPEEDOMETER || road
    val signal = signalOf(tile.data)
    val spec = gaugeOf(signal)
    val look = GaugeLook.parse(tile.data)
    val units = d.prefs.units
    var applied by remember { mutableStateOf(false) }
    fun set(new: GaugeLook) {
        applied = false
        d.dashboard.setData(tileId, roadLook?.copy(gauge = new)?.encode() ?: new.encode(signal))
    }
    // Al cambiar de estilo o de aro, los que lucen con su degradado lo toman si seguía el color de fábrica.
    fun pickLook(new: GaugeLook) = set(new.copy(color = new.suggestedColor()?.takeIf { look.color == GaugeColor.ACCENT } ?: look.color))
    fun setRoad(new: RoadLook) {
        applied = false
        d.dashboard.setData(tileId, new.encode())
    }

    var demo by remember { mutableFloatStateOf(DEMO[0]) }
    LaunchedEffect(Unit) {
        var i = 0
        while (true) {
            delay(900)
            i = (i + 1) % DEMO.size
            demo = DEMO[i]
        }
    }

    Box(Modifier.fillMaxSize().background(ice.bg).clickable(enabled = false) {}) {
        IceFrame(Modifier.safeDrawingPadding()) {
            IceBackdrop(R.drawable.bg_espacio)
            // .hd: el título y la píldora de qué se está personalizando.
            Row(Modifier.offset(28.dp, 20.dp).height(44.dp), verticalAlignment = Alignment.CenterVertically) {
                IceText(stringResource(if (road) R.string.road_customize else R.string.gauge_customize), iceStyle(28f, FontWeight.Light), ice.title)
                Spacer(Modifier.width(14.dp))
                val pill = RoundedCornerShape(17.dp)
                Box(Modifier.height(34.dp).clip(pill).background(Color(170, 210, 255).copy(alpha = 0.06f)).border(1.dp, ice.line2, pill).padding(horizontal = 14.dp), contentAlignment = Alignment.Center) {
                    IceText(
                        stringResource(if (road) R.string.gauge_panel_road else if (speedometer) R.string.gauge_panel_speedometer else R.string.gauge_panel_gauge),
                        iceStyle(13f, display = false), ice.ink2,
                    )
                }
            }
            // .x: cerrar (48, arriba a la derecha).
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = (-24).dp, y = 18.dp)
                    .size(48.dp)
                    .glass(listOf(0f to Color(170, 210, 255).copy(alpha = 0.14f), 1f to Color(110, 150, 210).copy(alpha = 0.05f)), ice.line, Color.Transparent, 24.dp)
                    .clip(CircleShape)
                    .clickable(onClick = close),
                contentAlignment = Alignment.Center,
            ) { Icon(PageIcons.close, if (ice.dark) Color(0xFFEAF4FF) else ice.ink, 24.dp) }

            // .gauge: la vista previa grande (24, 76; 440 × 440), sin tarjeta.
            Box(Modifier.offset(24.dp, 76.dp).size(440.dp, if (dock != null) 440.dp else 500.dp)) {
                CompositionLocalProvider(LocalPlainCard provides true) {
                    if (road) {
                        val demoKmh = demo * look.speedMax(miles = false)
                        RoadView(
                            d, d.cars.pkg(d.prefs.carModel), onClick = {}, look = roadLook ?: RoadLook(), demoKmh = demoKmh,
                            onTires = if (d.hasTpmsSource && showsTires(d, tile)) ({}) else null,
                        )
                    } else if (speedometer) {
                        val max = look.speedMax(units.miles).toFloat()
                        SpeedGaugeCard(demo * max, units.miles, "GPS", look, d.prefs.carColor, speedFront(d, look))
                    } else if (spec != null) {
                        SpecGaugeCard(spec, spec.min + (spec.max - spec.min) * demo, units, look, d.prefs.carColor)
                    }
                }
            }

            // .ctl: los controles en vidrio (left 486, right 24, desde 76 hasta arriba del dock).
            Column(
                Modifier
                    .offset(486.dp, 76.dp)
                    .width(514.dp)
                    .heightIn(max = if (dock != null) 420.dp else 508.dp)
                    .iceGlass()
                    .clip(RoundedCornerShape(20.dp))
                    .verticalScroll(rememberScrollState())
                    .padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 20.dp),
            ) {
                if (roadLook != null) {
                    RoadOptions(d, tile, roadLook, ::setRoad)
                    // Solo el número u oculta: no hay velocímetro que personalizar.
                    if (roadLook.speed != RoadSpeed.GAUGE) return@Column
                }
                IceText(stringResource(R.string.gauge_customize_help), iceStyle(13f, display = false, lineHeight = 1.4f), ice.ink2, maxLines = 3)
                OptionRow(stringResource(R.string.gauge_style)) {
                    IceSegmented(
                        GaugeStyle.entries.map { it to stringResource(it.title) },
                        look.style,
                        // La aguja se lee con números: se prenden solos la primera vez.
                        { pickLook(look.copy(style = it, numbers = look.numbers || it == GaugeStyle.NEEDLE)) },
                        compact = true,
                    )
                }
                // El aro de la Esfera: liso, neón o cuadros.
                if (look.style == GaugeStyle.DIAL) OptionRow(stringResource(R.string.gauge_rim)) {
                    IceSegmented(GaugeRim.entries.map { it to stringResource(it.title) }, look.rim, { pickLook(look.copy(rim = it)) })
                }
                OptionRow(stringResource(R.string.gauge_color)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        for (color in GaugeColor.entries) IceSwatch(color, d.prefs.carColor, d.prefs.accentColor == null, color == look.color) { set(look.copy(color = color)) }
                    }
                    val name = if (look.color == GaugeColor.ACCENT && d.prefs.accentColor == null) stringResource(R.string.gauge_ice) else stringResource(look.color.title)
                    val selected = stringResource(R.string.gauge_selected, name)
                    IceText(
                        buildAnnotatedString {
                            val i = selected.indexOf(name)
                            if (i < 0) append(selected) else {
                                append(selected.substring(0, i))
                                withStyle(SpanStyle(color = ice.title)) { append(name) }
                                append(selected.substring(i + name.length))
                            }
                        },
                        iceStyle(13f, display = false), ice.ink2, modifier = Modifier.padding(top = 10.dp),
                    )
                }
                if (speedometer) OptionRow(stringResource(R.string.gauge_max)) {
                    val unit = stringResource(if (units.miles) R.string.unit_mph else R.string.unit_kmh)
                    IceSegmented(
                        speedMaxOptions(units.miles).sorted().map { it to "$it $unit" },
                        look.speedMax(units.miles),
                        // Se guarda en km/h (al cambiar a mph se ve su pareja); el de fábrica no se guarda.
                        { set(look.copy(max = speedMaxToStore(it, units.miles))) },
                    )
                }
                Spacer(Modifier.height(16.dp))
                IceSwitchRow(stringResource(R.string.gauge_ticks), look.ticks) { set(look.copy(ticks = it)) }
                IceSwitchRow(stringResource(R.string.gauge_numbers), look.numbers) { set(look.copy(numbers = it)) }
                IceSwitchRow(stringResource(R.string.gauge_redline), look.redline) { set(look.copy(redline = it)) }
                // Solo el velocímetro (no la carretera, que ya lleva el auto). "Tu auto", si hay uno instalado.
                if (tile.type == TileTypes.SPEEDOMETER) OptionRow(stringResource(R.string.gauge_front)) {
                    val hasCar = d.cars.pkg(d.prefs.carModel) != null
                    IceSegmented(
                        GaugeFront.entries.filter { it != GaugeFront.CAR || hasCar || look.front == it }.map { it to stringResource(it.title) },
                        look.front,
                        { set(look.copy(front = it)) },
                    )
                    if (look.front == GaugeFront.IMAGE) {
                        Spacer(Modifier.height(10.dp))
                        ImageEditor(
                            d, tile.id, look.image, stringResource(R.string.gauge_front_image_empty),
                            placeholder = false,
                            onRemoved = { set(look.copy(image = "")) },
                        ) { set(look.copy(image = it)) }
                    }
                }
                FlowRow(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (!speedometer) com.irvingguerra.litedash.core.ui.ice.IceButton(stringResource(R.string.gauge_change_signal), onPickSignal, kind = com.irvingguerra.litedash.core.ui.ice.IceButtonKind.PLAIN, height = 48.dp)
                    com.irvingguerra.litedash.core.ui.ice.IceButton(stringResource(R.string.gauge_apply_all), kind = com.irvingguerra.litedash.core.ui.ice.IceButtonKind.BLUE, height = 48.dp, onClick = {
                        // Mismo estilo en todos; cada uno conserva su dato y, los velocímetros, hasta dónde llegan
                        // (la carretera, además, cómo va su velocidad). Una sola escritura del acomodo para todos.
                        d.dashboard.changeData(
                            d.dashboard.tiles
                                .filter { it.type == TileTypes.SPEEDOMETER || it.type == TileTypes.GAUGE || it.type == TileTypes.ROAD }
                                .associate {
                                    // Lo del frente solo va en los velocímetros y cada uno conserva su imagen (nunca dos
                                    // comparten un PNG). Desde un medidor o la carretera, su frente tampoco cambia.
                                    val current = GaugeLook.parse(it.data)
                                    val speedo = it.type == TileTypes.SPEEDOMETER
                                    val own = look.copy(
                                        max = current.max,
                                        front = when {
                                            !speedo -> GaugeFront.NONE
                                            tile.type == TileTypes.SPEEDOMETER -> look.front
                                            else -> current.front
                                        },
                                        image = if (speedo) current.image else "",
                                    )
                                    it.id to if (it.type == TileTypes.ROAD) RoadLook.parse(it.data).copy(gauge = own).encode() else own.encode(signalOf(it.data))
                                },
                        )
                        applied = true
                    })
                }
                if (applied) IceText(stringResource(R.string.gauge_applied), iceStyle(13f, display = false), ice.ice, modifier = Modifier.padding(top = 10.dp))
            }
            dock?.invoke(Modifier.align(Alignment.BottomStart))
        }
    }
}

/** Lo de la carretera: los datos del viaje, las llantas y cómo va la velocidad. */
@Composable
private fun ColumnScope.RoadOptions(d: Dependencies, tile: com.irvingguerra.litedash.feature.dashboard.Tile, roadLook: RoadLook, setRoad: (RoadLook) -> Unit) {
    OptionRow(stringResource(R.string.tile_trip), first = true) {
        for (item in RoadTrip.entries) {
            IceSwitchRow(stringResource(item.title), item in roadLook.trip) { on ->
                setRoad(roadLook.copy(trip = if (on) roadLook.trip + item else roadLook.trip - item))
            }
        }
    }
    // La presión de cada llanta junto a su rueda (solo con Llantas prendido).
    if (d.hasTpmsSource) OptionRow(stringResource(R.string.tile_tires)) {
        IceSwitchRow(stringResource(R.string.tires_show_pressure), showsTires(d, tile)) { setRoad(roadLook.copy(tires = it)) }
    }
    OptionRow(stringResource(R.string.road_speed)) {
        // Prendida o apagada como lo del viaje; prendida, número o velocímetro.
        val shown = roadLook.speed != RoadSpeed.HIDDEN
        IceSwitchRow(stringResource(R.string.road_speed_show), shown) { on -> setRoad(roadLook.copy(speed = if (on) RoadSpeed.TEXT else RoadSpeed.HIDDEN)) }
        if (shown) IceSegmented(RoadSpeed.entries.mapNotNull { s -> s.title?.let { s to stringResource(it) } }, roadLook.speed, { setRoad(roadLook.copy(speed = it)) })
    }
    Spacer(Modifier.height(14.dp))
}

/** Una sección (`.row`): el rótulo en mayúsculas (`.lbl`) y debajo lo suyo. */
@Composable
internal fun OptionRow(label: String, first: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    val ice = LocalIce.current
    Column(Modifier.padding(top = if (first) 0.dp else 14.dp)) {
        IceText(label.uppercase(), iceStyle(13f, FontWeight.Medium, spacing = 0.2f), ice.ink3, modifier = Modifier.padding(bottom = 8.dp))
        content()
    }
}

/**
 * El segmentado del diseño C (`.seg`): píldora tenue de 46 dp con las opciones repartidas; la elegida en
 * vidrio azul con su filo. [compact] = letra de 14 y menos aire (para seis opciones).
 */
@Composable
fun <T> IceSegmented(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit, compact: Boolean = false) {
    val ice = LocalIce.current
    val outer = RoundedCornerShape(24.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .height(46.dp)
            .clip(outer)
            .background(Color(150, 195, 245).copy(alpha = 0.05f))
            .border(1.dp, ice.line2, outer)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        for ((value, text) in options) {
            val on = value == selected
            val shape = RoundedCornerShape(20.dp)
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxSize()
                    .then(
                        if (on) Modifier.glass(
                            listOf(0f to Color(120, 190, 255).copy(alpha = 0.30f), 1f to Color(90, 150, 235).copy(alpha = 0.12f)),
                            Color(170, 215, 255).copy(alpha = 0.34f), Color(235, 246, 255).copy(alpha = 0.18f), 20.dp, angle = 135f,
                        ) else Modifier,
                    )
                    .clip(shape)
                    .clickable { onSelect(value) },
                contentAlignment = Alignment.Center,
            ) {
                IceText(text, iceStyle(if (compact) 13.5f else 14f), if (on) ice.title else (if (ice.dark) Color(222, 236, 252).copy(alpha = 0.72f) else ice.ink2))
            }
        }
    }
}

/** Un color para elegir (`.sw i`, 44 dp); el elegido con su anillo claro y su brillo. */
@Composable
private fun IceSwatch(color: GaugeColor, car: Color, iceAccent: Boolean, selected: Boolean, onClick: () -> Unit) {
    val c = LocalColors.current
    val ramp = c.ramp(color, car)
    val brush = when {
        color == GaugeColor.ACCENT && iceAccent -> Brush.linearGradient(listOf(Color(0xFFE3F4FF), Color(0xFF6FBFFF)))
        color == GaugeColor.DYNAMIC -> Brush.sweepGradient(ramp + ramp.first())
        ramp.size == 1 -> SolidColor(ramp[0])
        else -> Brush.linearGradient(ramp)
    }
    Box(
        Modifier
            .size(44.dp)
            .drawWithCache {
                val r = size.width / 2
                val ring = Color(0xFFD8F0FF)
                val glow = Brush.radialGradient(0f to Color(150, 210, 255).copy(alpha = 0.6f), ((r + 5.dp.toPx()) / (r + 14.dp.toPx())) to Color(150, 210, 255).copy(alpha = 0.35f), 1f to Color.Transparent, center = Offset(r, r), radius = r + 14.dp.toPx())
                onDrawBehind {
                    if (selected) {
                        // box-shadow: 0 0 0 3px #04070e, 0 0 0 5px #d8f0ff, 0 0 14px …
                        drawCircle(glow, r + 14.dp.toPx())
                        drawCircle(ring, r + 5.dp.toPx())
                        drawCircle(Color(0xFF04070E), r + 3.dp.toPx())
                    }
                }
            }
            .clip(CircleShape)
            .background(brush)
            .border(1.dp, Color.White.copy(alpha = 0.14f), CircleShape)
            .clickable(onClick = onClick),
    )
}

/** Un interruptor del diseño C (`.sw`, 54 × 32) con su texto a la izquierda; toda la fila se toca. */
@Composable
internal fun IceSwitchRow(text: String, on: Boolean, onChange: (Boolean) -> Unit) {
    val ice = LocalIce.current
    Row(
        Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(12.dp)).clickable { onChange(!on) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IceText(text, iceStyle(15f), ice.ink, modifier = Modifier.weight(1f))
        com.irvingguerra.litedash.core.ui.ice.IceToggle(on)
    }
}

