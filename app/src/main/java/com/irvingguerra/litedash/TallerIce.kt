// SailDash 1.1 · diseño C: Ajustes → Taller (catalogo/taller-*). Sin OBD: viaje, odómetro por GPS,
// tanqueadas y bitácora. Con el OBD conectado: salud, códigos, sensores y gráficas (TallerObdIce.kt).
package com.irvingguerra.litedash

import com.irvingguerra.litedash.core.ui.ice.IconWell
import com.irvingguerra.litedash.core.ui.ice.IceIconButton
import com.irvingguerra.litedash.core.ui.ice.IceSegmented
import com.irvingguerra.litedash.core.ui.ice.IceToggle
import com.irvingguerra.litedash.core.ui.ice.IceDot
import com.irvingguerra.litedash.core.ui.ice.IceButton
import com.irvingguerra.litedash.core.ui.ice.IceButtonKind
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.irvingguerra.litedash.core.ui.Icon
import com.irvingguerra.litedash.core.ui.ice.IceIcons
import com.irvingguerra.litedash.core.ui.ice.IceText
import com.irvingguerra.litedash.core.ui.ice.LocalIce
import com.irvingguerra.litedash.core.ui.ice.glass
import com.irvingguerra.litedash.core.ui.ice.softGlow
import com.irvingguerra.litedash.feature.obd.ObdState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay

/** La pestaña elegida del Taller, con y sin OBD (solo en memoria, como las demás de Ajustes). */
private val TallerTabs = mutableStateMapOf<String, Int>()

/** Las partes del Taller que no necesitan OBD (también salen en "Más" con el OBD conectado). */
enum class GaragePart(val title: Int) {
    TRIP(R.string.taller_tab_trip), ODOMETER(R.string.taller_tab_odometer), FUEL(R.string.taller_tab_fuel), LOG(R.string.taller_tab_log),
}

/**
 * Ajustes → Taller en el diseño C (menú de taller-salud.html). Con el OBD conectado, las pestañas de
 * siempre (Salud, Códigos de falla, Sensores, Gráficas, Más); sin él, lo que se puede sin adaptador.
 */
@Composable
fun TallerScreen(
    d: Dependencies,
    onTires: () -> Unit,
) {
    val context = LocalContext.current
    val garage = remember { Garage.get(context) }
    // Con el módulo apagado ni se crea el escáner; con adaptador guardado, se conecta solo.
    val obd = rememberObd(d)
    val connected = obd?.state == ObdState.ON
    // El encabezado de toda la hoja de Ajustes, con la píldora del adaptador (o del GPS) a la derecha.
    SettingsHeader(stringResource(R.string.taller_title), trailing = { TallerPill(d, garage, connected, Modifier) })
    SettingsCanvas(CanvasSrc.TALLER) {
        if (connected) {
            val tabs = listOf(R.string.taller_tab_health, R.string.taller_tab_codes, R.string.taller_tab_sensors, R.string.taller_tab_charts, R.string.taller_tab_more)
            val tab = TallerTabs["obd"] ?: 0
            IceSegmented(tabs.mapIndexed { i, t -> i to stringResource(t) }, tab, { TallerTabs["obd"] = it }, Modifier.at(258, 72), height = 48.dp, padding = 18.dp)
            TallerObdPanel(d, tab, garage, onTires) { TallerTabs["obd"] = it }
        } else {
            val tabs = GaragePart.entries.map { it.title } + R.string.taller_tab_obd
            val tab = TallerTabs["gps"] ?: 0
            IceSegmented(tabs.mapIndexed { i, t -> i to stringResource(t) }, tab, { TallerTabs["gps"] = it }, Modifier.at(258, 72), height = 48.dp, padding = 18.dp)
            if (tab < GaragePart.entries.size) GaragePanel(d, garage, GaragePart.entries[tab]) else NoObdPanel(d)
        }
    }
}

/** Lo que va en el panel grande (`.panel`) para una parte sin OBD. */
@Composable
fun GaragePanel(d: Dependencies, garage: Garage, part: GaragePart) {
    // Lo que se escribe con el teclado propio va encima de todo el lienzo (no solo del panel).
    var pad by remember { mutableStateOf<PadRequest?>(null) }
    var fuelForm by remember { mutableStateOf(false) }
    var logForm by remember { mutableStateOf<LogKind?>(null) }
    TallerPanel {
        when (part) {
            GaragePart.TRIP -> TripPart(d, garage)
            GaragePart.ODOMETER -> OdometerPart(d, garage) { pad = it }
            GaragePart.FUEL -> FuelPart(garage) { fuelForm = true }
            GaragePart.LOG -> LogPart(garage) { logForm = it }
        }
    }
    if (fuelForm) FuelForm(garage, onPad = { pad = it }, close = { fuelForm = false })
    logForm?.let { kind -> LogForm(garage, kind, onPad = { pad = it }, close = { logForm = null }) }
    pad?.let { p ->
        var value by remember(p) { mutableStateOf(p.initial) }
        NumberPad(
            p.title, value, { value = it }, p.unit,
            onDone = { p.onDone(value); pad = null },
            onCancel = { pad = null },
            decimal = p.decimal, hint = p.hint, format = p.format,
        )
    }
}

/** Lo que se pide con el teclado de números. */
class PadRequest(
    val title: String,
    val unit: String,
    val initial: String = "",
    val decimal: Boolean = false,
    val hint: String? = null,
    val format: (String) -> String = { it },
    val onDone: (String) -> Unit,
)

/** El panel de vidrio del Taller (`.panel.glass`: de 118 dp bajo .main hasta el fondo). */
@Composable
fun TallerPanel(content: @Composable BoxScope.() -> Unit) {
    val ice = LocalIce.current
    Box(Modifier.at(258, 134, 750, 378).glass(ice.glass, ice.line, ice.sheen, 20.dp), content = content)
}

/** La píldora de arriba a la derecha: el adaptador OBD o el GPS. */
@Composable
private fun TallerPill(d: Dependencies, garage: Garage, connected: Boolean, modifier: Modifier) {
    val ice = LocalIce.current
    val now by produceState(System.currentTimeMillis()) { while (true) { delay(5_000); value = System.currentTimeMillis() } }
    val gpsAlive = now - garage.lastFixAt < 15_000
    val shape = RoundedCornerShape(17.dp)
    Row(
        modifier.height(34.dp).clip(shape).background(Color(170, 210, 255).copy(alpha = 0.06f)).border(1.dp, ice.line2, shape).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IceDot(if (connected || gpsAlive) Kc.mint else Kc.warn)
        Spacer(Modifier.width(8.dp))
        val st = ts(13f, display = false)
        if (connected) {
            IceText("ELM327", st, Color(0xFFEAF4FF))
            IceText(" " + stringResource(R.string.taller_pill_obd_on, obdTargetName(d.obd.target)), st, ice.ink2)
        } else {
            IceText(stringResource(R.string.taller_pill_gps), st, Color(0xFFEAF4FF))
            IceText(" " + stringResource(if (gpsAlive) R.string.taller_pill_gps_on else R.string.taller_pill_gps_idle), st, ice.ink2)
        }
    }
}

// ---------------------------------------------------------------- Viaje

@Composable
private fun BoxScope.TripPart(d: Dependencies, garage: Garage) {
    val ice = LocalIce.current
    val locale = currentLocale()
    val (granted, ask) = rememberGpsPermission(d)
    // Mientras se ve, el GPS se prende (como cualquier pieza de viaje); al salir se apaga.
    if (granted) KeepTripRunning(d.trip)
    val trip = garage.trip
    CapLabel(stringResource(R.string.taller_trip_now), Modifier.at(24, 24))
    BigNumber(decimalText(trip.km, 1, locale), "km", Modifier.at(24, 48))
    val since = if (trip.since == 0L) stringResource(R.string.taller_trip_waiting) else stringResource(R.string.taller_trip_since, timeText(trip.since, locale))
    IceText(since, ts(13f, display = false), ice.ink2, modifier = Modifier.at(24, 132))
    StatTileC(stringResource(R.string.taller_trip_time), SetIcons.timer, hoursMinutes(trip.movingMs), stringResource(R.string.taller_trip_moving), Modifier.at(24, 160, 166, 96))
    StatTileC(
        stringResource(R.string.taller_trip_average), IceIcons.gauge,
        trip.averageKmh?.let { decimalText(it, 0, locale) } ?: "—", stringResource(R.string.taller_trip_average_sub), Modifier.at(202, 160, 166, 96),
        unit = "km/h".takeIf { trip.averageKmh != null },
    )
    if (!granted) {
        IceText(stringResource(R.string.taller_gps_permission), ts(13f, display = false, lh = 1.35f), Kc.warn, maxLines = 2, modifier = Modifier.at(24, 268, 344))
        IceButton(stringResource(R.string.taller_gps_allow), ask, Modifier.at(24, 310, 220), IceButtonKind.BLUE, icon = SetIcons.pin, height = 48.dp)
    } else {
        IceButton(stringResource(R.string.taller_trip_new), garage::newTrip, Modifier.at(24, 310, 190), IceButtonKind.PLAIN, icon = SetIcons.reset, height = 48.dp)
    }
    VLine(Modifier.at(392, 24, 1, 330))

    val total = garage.total
    CapLabel(stringResource(R.string.taller_total), Modifier.at(416, 24))
    if (total.since != 0L) IceText(
        stringResource(R.string.taller_total_since, dateText(total.since, locale)), ts(13f, display = false), ice.ink3,
        modifier = Modifier.align(Alignment.TopEnd).at(-24, 24),
    )
    BigNumber(if (total.km < 100) decimalText(total.km, 1, locale) else groupThousands(total.km.toLong(), locale), "km", Modifier.at(416, 46), size = 44f)
    IceText(
        stringResource(R.string.taller_total_line, hoursMinutes(total.movingMs), total.averageKmh?.let { "${decimalText(it, 0, locale)} km/h" } ?: "—"),
        ts(13f, display = false), ice.ink2, modifier = Modifier.at(416, 104),
    )
    CapLabel(stringResource(R.string.taller_trips_before), Modifier.at(416, 142))
    val trips = garage.trips
    if (trips.isEmpty()) {
        IceText(stringResource(R.string.taller_trips_empty), ts(13f, display = false, lh = 1.4f), ice.ink3, maxLines = 3, modifier = Modifier.at(416, 168, 310))
    } else Column(Modifier.at(416, 164, 310, 140)) {
        for (t in trips.take(3)) {
            HLine()
            Row(Modifier.height(46.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    IceText(dateTimeText(t.since, locale), ts(14f, display = false), ice.ink)
                    IceText(
                        listOfNotNull(hoursMinutes(t.movingMs), t.averageKmh?.let { "${decimalText(it, 0, locale)} km/h" }).joinToString(" · "),
                        ts(13f, display = false), ice.ink2, modifier = Modifier.padding(top = 5.dp),
                    )
                }
                IceText("${decimalText(t.km, 1, locale)} km", ts(18f), Color.White)
            }
        }
    }
    IceButton(stringResource(R.string.taller_total_reset), garage::resetTotal, Modifier.at(416, 318), IceButtonKind.GHOST, height = 40.dp, fontSize = 14f)
}

// ---------------------------------------------------------------- Odómetro

@Composable
private fun BoxScope.OdometerPart(d: Dependencies, garage: Garage, onPad: (PadRequest) -> Unit) {
    val ice = LocalIce.current
    val locale = currentLocale()
    val odo = garage.odometer
    val padTitle = stringResource(R.string.taller_odo_pad)
    val padHint = stringResource(R.string.taller_odo_pad_hint)
    val askOdo = {
        onPad(PadRequest(padTitle, "km", odo?.km?.toLong()?.toString() ?: "", hint = padHint, format = ::groupDigits) { v -> FuelMath.parsePesos(v)?.let(garage::setOdometer) })
    }
    CapLabel(stringResource(R.string.taller_odo_title), Modifier.at(24, 24))
    if (odo == null) {
        IceText(stringResource(R.string.taller_odo_unset), ts(56f, 200), ice.ink3, modifier = Modifier.at(24, 52))
        IceText(stringResource(R.string.taller_odo_unset_help), ts(14f, display = false, lh = 1.4f), ice.ink2, maxLines = 4, modifier = Modifier.at(24, 126, 344))
        IceButton(stringResource(R.string.taller_odo_set), askOdo, Modifier.at(24, 310, 230), IceButtonKind.PRIMARY, icon = SetIcons.odometer, height = 48.dp)
    } else {
        BigNumber(groupThousands(odo.km.toLong(), locale), "km", Modifier.at(24, 48))
        IceText(
            stringResource(R.string.taller_odo_from, groupThousands(odo.baseKm.toLong(), locale), dateText(odo.setAt, locale), decimalText(odo.gpsKm * odo.factor, 1, locale)),
            ts(13f, display = false), ice.ink2, modifier = Modifier.at(24, 132),
        )
        if (odo.factor != 1.0) IceText(stringResource(R.string.taller_odo_factor, decimalText(odo.factor, 3, locale)), ts(13f, display = false), ice.ink3, modifier = Modifier.at(24, 152))
        IceTile(Modifier.at(24, 186, 344, 104)) {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                Icon(SetIcons.info, Kc.ice, 20.dp)
                Spacer(Modifier.width(10.dp))
                IceText(stringResource(R.string.taller_odo_note), ts(13f, display = false, lh = 1.4f), ice.ink2, maxLines = 4)
            }
        }
        IceButton(stringResource(R.string.taller_odo_fix), askOdo, Modifier.at(24, 310, 250), IceButtonKind.BLUE, icon = SetIcons.odometer, height = 48.dp)
    }
    VLine(Modifier.at(392, 24, 1, 330))

    // El cambio de aceite (Recordatorios) contra el odómetro: "faltan 400 km".
    CapLabel(stringResource(R.string.taller_oil), Modifier.at(416, 24))
    val oil = d.reminders.all.firstOrNull { it.type == ReminderType.SERVICE }
    val oilPad = stringResource(R.string.taller_oil_pad)
    val askOilKm = {
        onPad(PadRequest(oilPad, "km", oil?.km?.toString() ?: odo?.let { ((it.km / 1000).toLong() * 1000 + 5000).toString() } ?: "", format = ::groupDigits) { v ->
            val km = ReminderKm.parse(v) ?: return@PadRequest
            if (oil != null) d.reminders.remove(oil)
            d.reminders.add(ReminderType.SERVICE, oil?.date ?: Day.today().plusMonths(ReminderType.SERVICE.months), km)
        })
    }
    val left = if (oil?.km != null && odo != null) kmLeft(oil.km, odo.km) else null
    val color = when {
        left == null -> ice.ink3
        left < 0 -> Kc.coral
        left <= OIL_WARN_KM -> Kc.gold
        else -> Kc.mint
    }
    IceTile(Modifier.at(416, 48, 310, 150), tint = color.takeIf { left != null && left <= OIL_WARN_KM }) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IceDot(color)
                Spacer(Modifier.width(8.dp))
                IceText(stringResource(R.string.reminder_service), ts(14f, display = false), ice.ink2, modifier = Modifier.weight(1f))
                Icon(SetIcons.oil, ice.ink3, 18.dp)
            }
            when {
                oil == null -> {
                    IceText(stringResource(R.string.taller_oil_none), ts(24f), Color.White, modifier = Modifier.padding(top = 10.dp))
                    IceText(stringResource(R.string.taller_oil_none_help), ts(13f, display = false, lh = 1.35f), ice.ink2, maxLines = 2, modifier = Modifier.padding(top = 7.dp))
                }
                oil.km == null -> {
                    IceText(stringResource(R.string.taller_oil_no_km), ts(24f), Color.White, modifier = Modifier.padding(top = 10.dp))
                    IceText(stringResource(R.string.taller_oil_no_km_help), ts(13f, display = false, lh = 1.35f), ice.ink2, maxLines = 2, modifier = Modifier.padding(top = 7.dp))
                }
                else -> {
                    val km = groupThousands(oil.km.toLong(), locale)
                    IceText(
                        when {
                            left == null -> stringResource(R.string.taller_oil_at, km)
                            left >= 0 -> stringResource(R.string.taller_oil_left, groupThousands(left, locale))
                            else -> stringResource(R.string.taller_oil_over, groupThousands(-left, locale))
                        },
                        ts(26f), if (left == null) Color.White else color, modifier = Modifier.padding(top = 10.dp),
                    )
                    IceText(
                        if (left == null) stringResource(R.string.taller_oil_need_odo) else stringResource(R.string.taller_oil_at_date, km, shortDate(oil.date, locale)),
                        ts(13f, display = false), ice.ink2, modifier = Modifier.padding(top = 7.dp),
                    )
                    if (left != null) ProgressBar(((OIL_INTERVAL_KM - left).toFloat() / OIL_INTERVAL_KM).coerceIn(0f, 1f), color, Modifier.padding(top = 14.dp))
                }
            }
        }
    }
    IceButton(stringResource(R.string.taller_oil_set_km), askOilKm, Modifier.at(416, 210), IceButtonKind.PLAIN, icon = SetIcons.oil, height = 40.dp, fontSize = 14f)
    // El último servicio anotado en la bitácora.
    val last = garage.log.firstOrNull { it.kind == LogKind.SERVICE || it.kind == LogKind.OIL }
    CapLabel(stringResource(R.string.taller_last_service), Modifier.at(416, 268))
    IceText(
        last?.let { listOfNotNull(stringResource(it.kind.title()), shortDate(it.date, locale), it.km?.let { k -> "${groupThousands(k.toLong(), locale)} km" }).joinToString(" · ") }
            ?: stringResource(R.string.taller_last_service_none),
        ts(14f, display = false, lh = 1.35f), if (last != null) ice.ink else ice.ink3, maxLines = 2, modifier = Modifier.at(416, 292, 310),
    )
}

/** Cada cuánto se cambia el aceite (para la barrita): 5.000 km es lo común en el Sail con aceite mineral. */
private const val OIL_INTERVAL_KM = 5_000L

/** Menos que esto y el cambio de aceite sale en dorado. */
private const val OIL_WARN_KM = 500L

// ---------------------------------------------------------------- Tanqueadas

@Composable
private fun BoxScope.FuelPart(garage: Garage, onAdd: () -> Unit) {
    val ice = LocalIce.current
    val locale = currentLocale()
    val entries = garage.fuel
    val results = remember(entries) { FuelMath.results(entries) }
    val average = remember(entries) { FuelMath.average(entries) }
    CapLabel(stringResource(R.string.taller_fuel_economy), Modifier.at(24, 24))
    if (average != null) {
        BigNumber(decimalText(average.kmPerGallon, 1, locale), stringResource(R.string.taller_fuel_unit), Modifier.at(24, 48))
        IceText(stringResource(R.string.taller_fuel_cost_km, pesos(average.costPerKm, locale), results.size), ts(13f, display = false), ice.ink2, modifier = Modifier.at(24, 132))
    } else {
        BigNumber("—", stringResource(R.string.taller_fuel_unit), Modifier.at(24, 48))
        IceText(stringResource(R.string.taller_fuel_need_two), ts(13f, display = false), ice.ink2, modifier = Modifier.at(24, 132))
    }
    StatTileC(stringResource(R.string.taller_fuel_month), SetIcons.calendar, pesos(FuelMath.spentInMonth(entries, Day.today()), locale), null, Modifier.at(24, 160, 166, 96))
    StatTileC(stringResource(R.string.taller_fuel_gallon), IceIcons.fuel, entries.firstOrNull()?.let { pesos(it.pricePerGallon, locale) } ?: "—", null, Modifier.at(202, 160, 166, 96))
    IceButton(stringResource(R.string.taller_fuel_add), onAdd, Modifier.at(24, 310, 230), IceButtonKind.PRIMARY, icon = SetIcons.plus, height = 48.dp)
    VLine(Modifier.at(392, 24, 1, 330))
    CapLabel(stringResource(R.string.taller_fuel_history), Modifier.at(416, 24))
    if (entries.isEmpty()) {
        IceText(stringResource(R.string.taller_fuel_empty), ts(14f, display = false, lh = 1.4f), ice.ink3, maxLines = 5, modifier = Modifier.at(416, 52, 310))
    } else Column(Modifier.at(416, 46, 310, 316).verticalScroll(rememberScrollState())) {
        for (e in entries) {
            HLine()
            Row(Modifier.height(58.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    IceText(shortDate(e.date, locale) + if (e.full) "" else " · " + stringResource(R.string.taller_fuel_partial), ts(15f, display = false), ice.ink)
                    IceText(
                        stringResource(R.string.taller_fuel_row, decimalText(e.gallons, 1, locale), pesos(e.price, locale), groupThousands(e.km.toLong(), locale)),
                        ts(12f, display = false), ice.ink2, modifier = Modifier.padding(top = 5.dp),
                    )
                }
                results[e.id]?.let { r ->
                    Column(horizontalAlignment = Alignment.End) {
                        IceText("${decimalText(r.kmPerGallon, 1, locale)} km/gal", ts(15f), Kc.mint)
                        IceText("${pesos(r.costPerKm, locale)}/km", ts(12f, display = false), ice.ink2, modifier = Modifier.padding(top = 5.dp))
                    }
                }
                Box(Modifier.padding(start = 6.dp).size(36.dp).clip(RoundedCornerShape(18.dp)).clickable { garage.removeFuel(e) }, contentAlignment = Alignment.Center) {
                    Icon(SetIcons.trash, ice.ink3, 18.dp)
                }
            }
        }
    }
}

/** La ventana de una tanqueada nueva: galones, valor y km (con el teclado propio) y si fue tanque lleno. */
@Composable
private fun FuelForm(garage: Garage, onPad: (PadRequest) -> Unit, close: () -> Unit) {
    val ice = LocalIce.current
    val locale = currentLocale()
    var gallons by remember { mutableStateOf("") }
    var price by remember { mutableStateOf("") }
    var km by remember { mutableStateOf(garage.odometer?.km?.toLong()?.toString() ?: garage.fuel.firstOrNull()?.km?.toLong()?.toString() ?: "") }
    var full by remember { mutableStateOf(true) }
    val g = FuelMath.parseDecimal(gallons)
    val p = FuelMath.parsePesos(price)
    val k = FuelMath.parsePesos(km)
    FormSheet(stringResource(R.string.taller_fuel_new), close) {
        val galTitle = stringResource(R.string.taller_fuel_gallons)
        val priceTitle = stringResource(R.string.taller_fuel_price)
        val kmTitle = stringResource(R.string.taller_fuel_km)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FormField(galTitle, gallons.ifEmpty { null }, stringResource(R.string.taller_gal_unit), Modifier.weight(1f)) {
                onPad(PadRequest(galTitle, "gal", gallons, decimal = true) { gallons = it })
            }
            FormField(priceTitle, p?.let { pesos(it, locale) }, null, Modifier.weight(1f)) {
                onPad(PadRequest(priceTitle, "$", price, format = ::groupDigits) { price = it })
            }
        }
        Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            FormField(kmTitle, k?.let { groupThousands(it.toLong(), locale) }, "km", Modifier.weight(1f)) {
                onPad(PadRequest(kmTitle, "km", km, hint = null, format = ::groupDigits) { km = it })
            }
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IceText(stringResource(R.string.taller_fuel_full), ts(15f, display = false), ice.ink, modifier = Modifier.weight(1f))
                    IceToggle(full, onChange = { full = it })
                }
                IceText(stringResource(R.string.taller_fuel_full_help), ts(12f, display = false, lh = 1.35f), ice.ink2, maxLines = 2, modifier = Modifier.padding(top = 6.dp))
            }
        }
        IceText(
            if (g != null && p != null) stringResource(R.string.taller_fuel_per_gallon, pesos(p / g, locale)) else stringResource(R.string.taller_fuel_km_hint),
            ts(13f, display = false, lh = 1.35f), if (g != null && p != null) Kc.ice else ice.ink3, maxLines = 2, modifier = Modifier.padding(top = 14.dp),
        )
        Spacer(Modifier.weight(1f))
        FormButtons(close, enabled = g != null && p != null && k != null) {
            garage.addFuel(FuelEntry(System.currentTimeMillis(), Day.today(), g!!, p!!, k!!, full))
            close()
        }
    }
}

// ---------------------------------------------------------------- Bitácora

@Composable
private fun BoxScope.LogPart(garage: Garage, onAdd: (LogKind) -> Unit) {
    val ice = LocalIce.current
    val locale = currentLocale()
    CapLabel(stringResource(R.string.taller_log_add), Modifier.at(24, 24))
    Column(Modifier.at(24, 50, 344), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        for (row in LogKind.entries.chunked(2)) Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            for (k in row) IceTile(Modifier.weight(1f).height(84.dp), onClick = { onAdd(k) }) {
                Row(Modifier.fillMaxSize().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconWell(k.icon(), Kc.ice, 40.dp, 20.dp)
                    Spacer(Modifier.width(12.dp))
                    IceText(stringResource(k.title()), ts(16f, 500), Color.White, modifier = Modifier.weight(1f))
                    Icon(SetIcons.plus, ice.ink3, 18.dp)
                }
            }
        }
    }
    VLine(Modifier.at(392, 24, 1, 330))
    CapLabel(stringResource(R.string.taller_log_history), Modifier.at(416, 24))
    val log = garage.log
    if (log.isEmpty()) {
        IceText(stringResource(R.string.taller_log_empty), ts(14f, display = false, lh = 1.4f), ice.ink3, maxLines = 5, modifier = Modifier.at(416, 52, 310))
    } else Column(Modifier.at(416, 46, 310, 316).verticalScroll(rememberScrollState())) {
        for (e in log) {
            HLine()
            Row(Modifier.height(62.dp), verticalAlignment = Alignment.CenterVertically) {
                IconWell(e.kind.icon(), Kc.ice, 36.dp, 18.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    IceText(e.note.ifBlank { stringResource(e.kind.title()) }, ts(15f, display = false), ice.ink)
                    IceText(
                        listOfNotNull(
                            stringResource(e.kind.title()).takeIf { e.note.isNotBlank() }, shortDate(e.date, locale),
                            e.km?.let { "${groupThousands(it.toLong(), locale)} km" }, e.cost?.let { pesos(it, locale) },
                        ).joinToString(" · "),
                        ts(12f, display = false), ice.ink2, modifier = Modifier.padding(top = 5.dp),
                    )
                }
                Box(Modifier.size(36.dp).clip(RoundedCornerShape(18.dp)).clickable { garage.removeLog(e) }, contentAlignment = Alignment.Center) {
                    Icon(SetIcons.trash, ice.ink3, 18.dp)
                }
            }
        }
    }
}

@Composable
private fun LogForm(garage: Garage, kind: LogKind, onPad: (PadRequest) -> Unit, close: () -> Unit) {
    val ice = LocalIce.current
    val locale = currentLocale()
    var date by remember { mutableStateOf(Day.today()) }
    var km by remember { mutableStateOf(garage.odometer?.km?.toLong()?.toString() ?: "") }
    var cost by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    FormSheet(stringResource(R.string.taller_log_new, stringResource(kind.title())), close) {
        val kmTitle = stringResource(R.string.taller_fuel_km)
        val costTitle = stringResource(R.string.taller_log_cost)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            // La fecha: hoy, o unos días atrás con ‹ ›.
            Column(Modifier.weight(1f)) {
                IceText(stringResource(R.string.taller_log_date), ts(13f, display = false), ice.ink2)
                Row(Modifier.padding(top = 8.dp).height(56.dp), verticalAlignment = Alignment.CenterVertically) {
                    IceIconButton(SetIcons.back, { date = date.plusDays(-1) }, size = 40.dp, iconSize = 18.dp)
                    IceText(
                        if (date == Day.today()) stringResource(R.string.taller_today) else shortDate(date, locale),
                        ts(17f), Color.White, modifier = Modifier.weight(1f).padding(horizontal = 6.dp),
                    )
                    IceIconButton(SetIcons.chevronRight, { if (date < Day.today()) date = date.plusDays(1) }, size = 40.dp, iconSize = 18.dp)
                }
            }
            FormField(kmTitle, FuelMath.parsePesos(km)?.let { groupThousands(it.toLong(), locale) }, "km", Modifier.weight(1f)) {
                onPad(PadRequest(kmTitle, "km", km, format = ::groupDigits) { km = it })
            }
            FormField(costTitle, FuelMath.parsePesos(cost)?.let { pesos(it, locale) }, null, Modifier.weight(1f)) {
                onPad(PadRequest(costTitle, "$", cost, format = ::groupDigits) { cost = it })
            }
        }
        IceText(stringResource(R.string.taller_log_note), ts(13f, display = false), ice.ink2, modifier = Modifier.padding(top = 16.dp))
        val shape = RoundedCornerShape(14.dp)
        Box(
            Modifier.padding(top = 8.dp).fillMaxWidth().height(52.dp).clip(shape).background(Color(6, 12, 22).copy(alpha = 0.45f))
                .border(1.dp, ice.line, shape).padding(horizontal = 16.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            if (note.isEmpty()) IceText(stringResource(R.string.taller_log_note_hint), ts(15f, display = false), ice.ink3)
            BasicTextField(
                note, { note = it.take(80) }, Modifier.fillMaxWidth(),
                textStyle = ts(15f, display = false).copy(color = ice.ink), singleLine = true, cursorBrush = SolidColor(Kc.ice),
            )
        }
        Spacer(Modifier.weight(1f))
        FormButtons(close, enabled = true) {
            garage.addLog(LogEntry(System.currentTimeMillis(), kind, date, FuelMath.parsePesos(km)?.toInt(), note.trim(), FuelMath.parsePesos(cost)))
            close()
        }
    }
}

fun LogKind.title() = when (this) {
    LogKind.SERVICE -> R.string.taller_log_service
    LogKind.OIL -> R.string.taller_log_oil
    LogKind.TIRES -> R.string.taller_log_tires
    LogKind.BRAKES -> R.string.taller_log_brakes
    LogKind.BATTERY -> R.string.taller_log_battery
    LogKind.OTHER -> R.string.taller_log_other
}

fun LogKind.icon(): ImageVector = when (this) {
    LogKind.SERVICE -> SetIcons.wrenchB
    LogKind.OIL -> SetIcons.oil
    LogKind.TIRES -> SetIcons.tire
    LogKind.BRAKES -> SetIcons.brake
    LogKind.BATTERY -> IceIcons.battery
    LogKind.OTHER -> SetIcons.notebook
}

// ---------------------------------------------------------------- OBD sin conectar

@Composable
private fun NoObdPanel(d: Dependencies) {
    val ice = LocalIce.current
    TallerPanel {
        Column(Modifier.at(24, 24, 330)) {
            IconWell(IceIcons.plug, Kc.ice, 44.dp, 22.dp)
            IceText(stringResource(R.string.taller_obd_title), ts(20f, 500, lh = 1.2f), Color.White, maxLines = 2, modifier = Modifier.padding(top = 16.dp))
            IceText(stringResource(R.string.taller_obd_help), ts(13f, display = false, lh = 1.45f), ice.ink2, maxLines = 6, modifier = Modifier.padding(top = 10.dp))
            for (p in listOf(R.string.taller_obd_point1, R.string.taller_obd_point2, R.string.taller_obd_point3)) {
                Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    IceDot(Kc.ice, size = 6.dp)
                    Spacer(Modifier.width(10.dp))
                    IceText(stringResource(p), ts(14f, display = false), ice.ink)
                }
            }
        }
        VLine(Modifier.at(378, 24, 1, 330))
        Column(Modifier.at(402, 24, 324, 330)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    IceText(stringResource(R.string.taller_obd_module), ts(15f, display = false), ice.ink)
                    IceText(stringResource(R.string.taller_obd_module_help), ts(12f, display = false), ice.ink2, modifier = Modifier.padding(top = 5.dp))
                }
                IceToggle(d.obdModule, onChange = { d.obdModule = it })
            }
            // Con el módulo prendido, la conexión de siempre (buscar el adaptador, Bluetooth, Wi-Fi o USB).
            if (d.obdModule) Box(Modifier.padding(top = 14.dp).fillMaxWidth().weight(1f).verticalScroll(rememberScrollState())) {
                Connection(d, card = false)
            }
        }
    }
}

// ---------------------------------------------------------------- Piezas

/** Un número grande y delgado con su unidad al lado (`.big`). */
@Composable
fun BigNumber(value: String, unit: String, modifier: Modifier = Modifier, size: Float = 68f, color: Color = Color.White) {
    val ice = LocalIce.current
    Row(modifier, verticalAlignment = Alignment.Bottom) {
        IceText(value, ts(size, 200, spacing = -0.01f), color)
        IceText(unit, ts(20f, 300), ice.ink2, modifier = Modifier.padding(start = 8.dp, bottom = (size * 0.12f).dp))
    }
}

/** Tarjeta chica de un dato (`.card` de taller-salud): nombre con ícono, el valor y una línea. */
@Composable
fun StatTileC(name: String, icon: ImageVector, value: String, sub: String?, modifier: Modifier, unit: String? = null, tint: Color? = null, valueColor: Color = Color.White) {
    val ice = LocalIce.current
    IceTile(modifier, tint = tint) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IceText(name, ts(14f, display = false), ice.ink2, modifier = Modifier.weight(1f))
                Icon(icon, ice.ink3, 18.dp)
            }
            Row(Modifier.padding(top = 9.dp), verticalAlignment = Alignment.Bottom) {
                IceText(value, ts(24f, spacing = 0.005f), valueColor)
                if (unit != null) IceText(unit, ts(16f), ice.ink2, modifier = Modifier.padding(start = 4.dp, bottom = 1.dp))
            }
            if (sub != null) IceText(sub, ts(13f, display = false), ice.ink2, modifier = Modifier.padding(top = 8.dp))
        }
    }
}

/** Línea vertical fina entre columnas. */
@Composable
fun VLine(modifier: Modifier) {
    Box(modifier.background(LocalIce.current.line2))
}

/** Barrita de progreso hielo (`.bar i`). */
@Composable
fun ProgressBar(fraction: Float, color: Color, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(3.dp)).background(Color(190, 225, 255).copy(alpha = 0.14f))) {
        Box(
            Modifier.fillMaxWidth(fraction).height(5.dp).softGlow(color.copy(alpha = 0.4f), 4.dp).clip(RoundedCornerShape(3.dp))
                .background(Brush.horizontalGradient(listOf(color.copy(alpha = 0.75f), color))),
        )
    }
}

/** Ventana de vidrio centrada para un formulario corto (tanqueada, bitácora). */
@Composable
fun FormSheet(title: String, close: () -> Unit, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    // Encima de todo el lienzo (no achicada dentro de la hoja de Ajustes).
    Unscaled {
        Box(
            Modifier.fillMaxSize().background(Color(2, 5, 10).copy(alpha = 0.66f))
                .clickable(remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, indication = null, onClick = close),
        ) {
            IceCard(
                Modifier.at(192, 56, 640, 400).clip(RoundedCornerShape(24.dp)).background(Kc.sheetBase).clickable(remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, indication = null) {},
                radius = 24.dp, hi = true,
            ) {
                Column(Modifier.fillMaxSize().padding(22.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IceText(title, ts(20f, 500), Color.White, modifier = Modifier.weight(1f))
                        IceIconButton(SetIcons.close, close, size = 36.dp, iconSize = 18.dp)
                    }
                    Spacer(Modifier.height(18.dp))
                    content()
                }
            }
        }
    }

}

/** Un campo del formulario que se llena con el teclado propio: rótulo arriba y la caja con el valor. */
@Composable
fun FormField(label: String, value: String?, unit: String?, modifier: Modifier, onClick: () -> Unit) {
    val ice = LocalIce.current
    val shape = RoundedCornerShape(14.dp)
    Column(modifier) {
        IceText(label, ts(13f, display = false), ice.ink2)
        Row(
            Modifier.padding(top = 8.dp).fillMaxWidth().height(56.dp).clip(shape).background(Color(6, 12, 22).copy(alpha = 0.45f))
                .border(1.dp, ice.line, shape).clickable(onClick = onClick).padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IceText(value ?: stringResource(R.string.taller_tap_to_write), if (value != null) ts(20f, 300) else ts(14f, display = false), if (value != null) Color.White else ice.ink3, modifier = Modifier.weight(1f))
            if (unit != null && value != null) IceText(unit, ts(15f), ice.ink2)
        }
    }
}

@Composable
fun FormButtons(cancel: () -> Unit, enabled: Boolean, save: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        IceButton(stringResource(R.string.cancel), cancel, Modifier.weight(1f), IceButtonKind.PLAIN, height = 48.dp)
        IceButton(stringResource(R.string.taller_save), save, Modifier.weight(1f), IceButtonKind.PRIMARY, icon = SetIcons.check, height = 48.dp, enabled = enabled)
    }
}

/** "0:24" o "12 h 05 min". */
@Composable
fun hoursMinutes(ms: Long): String {
    val minutes = (ms / 60_000).toInt()
    return if (minutes < 600) "${minutes / 60}:${(minutes % 60).toString().padStart(2, '0')}" else stringResource(R.string.taller_hours_minutes, minutes / 60, minutes % 60)
}

/** "$ 160.000" (pesos, sin decimales). */
fun pesos(v: Double, locale: Locale) = "$ " + groupThousands(Math.round(v), locale)

/** Lo escrito en el teclado con puntos de mil: "85412" → "85.412". */
fun groupDigits(text: String): String = text.toLongOrNull()?.let { groupThousands(it, Locale.getDefault()) } ?: text

fun timeText(ms: Long, locale: Locale): String {
    val c = java.util.Calendar.getInstance().apply { timeInMillis = ms }
    val am = c.get(java.util.Calendar.AM_PM) == java.util.Calendar.AM
    val marker = if (locale.language == "es") (if (am) "a. m." else "p. m.") else (if (am) "AM" else "PM")
    return SimpleDateFormat("h:mm", locale).format(Date(ms)) + " " + marker
}

fun dateText(ms: Long, locale: Locale): String = SimpleDateFormat(if (locale.language == "es") "d MMM" else "MMM d", locale).format(Date(ms)).replace(".", "")

fun dateTimeText(ms: Long, locale: Locale): String = SimpleDateFormat(if (locale.language == "es") "EEE d MMM" else "EEE, MMM d", locale).format(Date(ms)).replace(".", "") + ", " + timeText(ms, locale)

fun shortDate(day: Day, locale: Locale): String = SimpleDateFormat(if (locale.language == "es") "d MMM yyyy" else "MMM d, yyyy", locale).format(day.toDate()).replace(".", "")

/** "Bluetooth", "Wi-Fi" o "USB" (cómo está conectado el adaptador). */
fun obdTargetName(t: com.irvingguerra.litedash.feature.obd.ObdTarget?) = when (t) {
    is com.irvingguerra.litedash.feature.obd.ObdTarget.Bluetooth -> "Bluetooth"
    is com.irvingguerra.litedash.feature.obd.ObdTarget.Ble -> "Bluetooth LE"
    is com.irvingguerra.litedash.feature.obd.ObdTarget.Wifi -> "Wi-Fi"
    is com.irvingguerra.litedash.feature.obd.ObdTarget.Usb -> "USB"
    null -> "OBD"
}
