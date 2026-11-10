// SailDash 1.1 · diseño C: el Taller con el OBD conectado (catalogo/taller-salud, taller-codigos, graficas).
// Las cuentas son las de siempre (Health.kt, Workshop.kt, WorkshopAdvanced.kt); aquí solo cambia cómo se ve.
package com.irvingguerra.litedash

import android.os.SystemClock
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.irvingguerra.litedash.core.ui.Icon
import com.irvingguerra.litedash.core.ui.ice.IceIcons
import com.irvingguerra.litedash.core.ui.ice.IceText
import com.irvingguerra.litedash.core.ui.ice.LocalIce
import com.irvingguerra.litedash.core.ui.ice.glass
import com.irvingguerra.litedash.core.ui.ice.softGlow
import com.irvingguerra.litedash.core.vehicle.Signal
import com.irvingguerra.litedash.core.vehicle.watch
import com.irvingguerra.litedash.feature.car.CarTop
import com.irvingguerra.litedash.feature.obd.AccelState
import com.irvingguerra.litedash.feature.obd.AccelTimer
import com.irvingguerra.litedash.feature.obd.EngineReport
import com.irvingguerra.litedash.feature.obd.HealthLevel
import com.irvingguerra.litedash.feature.obd.SystemHealth
import com.irvingguerra.litedash.feature.obd.Zone
import com.irvingguerra.litedash.feature.obd.describe
import com.irvingguerra.litedash.feature.obd.issues
import com.irvingguerra.litedash.feature.obd.zoneOf
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** La parte de "Más" que está abierta (null = la lista). */
private var moreOpen by mutableStateOf<Int?>(null)

/** El panel de la pestaña [tab] con el OBD conectado: Salud, Códigos de falla, Sensores, Gráficas, Más. */
@Composable
fun BoxScope.TallerObdPanel(d: Dependencies, tab: Int, garage: Garage, onTires: () -> Unit, onTab: (Int) -> Unit) {
    val obd = d.obd
    // El reporte se lee una vez y lo comparten Salud y Códigos (el último leído se ve de una vez).
    val scope = rememberCoroutineScope()
    var report by remember { mutableStateOf(obd.lastReport) }
    var readAt by remember { mutableStateOf(0L) }
    var reading by remember { mutableStateOf(true) }
    val job = remember { mutableStateOf<Job?>(null) }
    fun read() {
        if (job.value?.isActive == true) return
        job.value = scope.launch {
            reading = true
            report = obd.report() ?: report
            readAt = SystemClock.elapsedRealtime()
            reading = false
        }
    }
    LaunchedEffect(Unit) { read() }
    when (tab) {
        0 -> HealthPanel(d, report, readAt) { onTab(1) }
        1 -> CodesPanels(d, report, reading, ::read) { report = it }
        2 -> SensorsPanel(d)
        3 -> ChartPanels(d)
        else -> MorePanel(d, garage)
    }
}

// ---------------------------------------------------------------- Salud (taller-salud.html)

private val HealthOrder = listOf(Zone.ENGINE, Zone.INTAKE, Zone.COOLING, Zone.BATTERY, Zone.TRANSMISSION, Zone.EXHAUST)

private fun HealthLevel.color() = when (this) {
    HealthLevel.GOOD -> Kc.mint
    HealthLevel.WARN -> Kc.warn
    HealthLevel.FAULT -> Kc.bad
    HealthLevel.UNKNOWN -> Color(196, 216, 238).copy(alpha = 0.56f)
}

@Composable
private fun HealthPanel(d: Dependencies, report: EngineReport?, readAt: Long, onCodes: () -> Unit) {
    val ice = LocalIce.current
    val health = rememberHealth(d, report, connected = true)
    TallerPanel {
        // El carro desde arriba con su órbita y los puntos donde algo pide atención.
        Canvas(Modifier.at(4, 6, 224, 366)) {
            val brush = Brush.verticalGradient(listOf(Color(0x00BFE6FF), Color(0x59BFE6FF), Color(0x00BFE6FF)))
            val c = Offset(size.width / 2, size.height / 2)
            drawOval(brush, Offset(c.x - 104.dp.toPx(), c.y - size.height * 0.479f), Size(208.dp.toPx(), size.height * 0.958f), style = Stroke(1.dp.toPx()))
            drawOval(
                brush, Offset(c.x - 92.dp.toPx(), c.y - size.height * 0.432f), Size(184.dp.toPx(), size.height * 0.864f),
                alpha = 0.7f, style = Stroke(1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(1.5f.dp.toPx(), 6.dp.toPx()))),
            )
        }
        val marks = health.filter { it.level == HealthLevel.WARN || it.level == HealthLevel.FAULT }.map {
            ProblemMark(it.zone.x, it.zone.y, it.level.color(), pulsing = it.level == HealthLevel.FAULT)
        }
        Box(Modifier.at(14, 12, 212, 354), contentAlignment = Alignment.Center) {
            Box(Modifier.size(151.dp, 356.dp)) {
                CarTop(d.cars, d.cars.pkg(d.prefs.carModel), d.prefs.carColor, Modifier.fillMaxSize())
                ProblemMarks(marks, Modifier.fillMaxSize())
            }
        }
        // .sum
        CapLabel(stringResource(R.string.health_title), Modifier.at(246, 18))
        val ago by produceState(0, readAt) { while (true) { value = ((SystemClock.elapsedRealtime() - readAt) / 1000).toInt(); delay(1_000) } }
        IceText(
            listOfNotNull(d.prefs.carName?.takeIf { it.isNotBlank() } ?: "Sail 1.4", if (readAt > 0) stringResource(R.string.taller_read_ago, ago) else null).joinToString(" · "),
            ts(13f, display = false), ice.ink3, modifier = Modifier.align(Alignment.TopEnd).at(-20, 18),
        )
        val issues = health.issues()
        val faults = health.count { it.level == HealthLevel.FAULT }
        val warns = health.count { it.level == HealthLevel.WARN }
        Row(Modifier.at(246, 40), verticalAlignment = Alignment.Bottom) {
            IceText(
                if (issues == 0) stringResource(R.string.health_all_good) else pluralStringResource(R.plurals.health_issues, issues, issues),
                ts(30f, 300), if (issues == 0) Color(0xFF9FF3D6) else if (faults > 0) Color(0xFFFFB3AD) else Color(0xFFFFCF85),
            )
            if (issues > 0) IceText(stringResource(R.string.taller_issues_detail, faults, warns), ts(14f, display = false), ice.ink2, modifier = Modifier.padding(start = 12.dp, bottom = 3.dp))
        }
        // .grid: 2 columnas × 3 filas, de 80 dp abajo hasta 14 del fondo.
        val byZone = health.associateBy { it.zone }
        val cellW = (750 - 246 - 18 - 10) / 2f
        val cellH = (378 - 80 - 14 - 20) / 3f
        HealthOrder.forEachIndexed { i, zone ->
            val h = byZone[zone] ?: return@forEachIndexed
            HealthCard(d, h, report, Modifier.at(246 + (i % 2) * (cellW + 10), 80 + (i / 2) * (cellH + 10), cellW, cellH), onCodes)
        }
    }
}

@Composable
private fun HealthCard(d: Dependencies, h: SystemHealth, report: EngineReport?, modifier: Modifier, onCodes: () -> Unit) {
    val ice = LocalIce.current
    val locale = currentLocale()
    val color = h.level.color()
    val signal = when (h.zone) {
        Zone.COOLING -> Signal.COOLANT
        Zone.BATTERY -> Signal.VOLTAGE
        Zone.ENGINE -> Signal.RPM
        else -> null
    }
    val live = if (signal != null) d.bus.watch(signal) else null
    val value = live ?: report?.voltage?.takeIf { h.zone == Zone.BATTERY }
    val bad = h.level == HealthLevel.WARN || h.level == HealthLevel.FAULT
    val hasCodes = h.codes.isNotEmpty() || h.pendingCodes.isNotEmpty()
    IceTile(modifier, tint = if (bad) color else null, onClick = if (hasCodes) onCodes else null) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 11.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                KDot(color)
                Spacer(Modifier.width(8.dp))
                IceText(stringResource(zoneName(h.zone)), ts(14f, display = false), ice.ink2, modifier = Modifier.weight(1f))
                Icon(h.zone.icon(), ice.ink3, 18.dp)
            }
            // El valor: con falla o atención, la palabra en su color; bien, el dato en vivo (número + unidad) o "Bien".
            Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.Bottom) {
                when {
                    bad -> IceText(stringResource(if (h.level == HealthLevel.FAULT) R.string.health_fault else R.string.health_warn), ts(24f), if (h.level == HealthLevel.FAULT) Color(0xFFFF9A91) else Color(0xFFFFCF85))
                    value != null && signal != null -> {
                        val (num, unit) = splitUnit(sensorText(signal, value, d.prefs.units, locale))
                        IceText(num, ts(24f, spacing = 0.005f), Color.White)
                        if (unit.isNotEmpty()) IceText(unit, ts(16f), ice.ink2, modifier = Modifier.padding(start = 4.dp, bottom = 1.dp))
                    }
                    h.level == HealthLevel.UNKNOWN -> IceText(stringResource(R.string.health_unknown), ts(24f), ice.ink3)
                    else -> IceText(stringResource(R.string.health_good), ts(24f), Color(0xFF9FF3D6))
                }
            }
            val detail = when {
                h.codes.isNotEmpty() -> h.codes.joinToString(" · ") + " · " + describeShort(h.codes.first())
                h.pendingCodes.isNotEmpty() -> h.pendingCodes.joinToString(" · ") + " · " + stringResource(R.string.health_pending)
                h.level == HealthLevel.WARN && h.alert == null -> stringResource(R.string.health_mil)
                h.zone == Zone.ENGINE -> stringResource(R.string.taller_idle).takeIf { (value ?: 0f) in 500f..1100f }
                h.zone == Zone.COOLING -> stringResource(R.string.taller_fan_note)
                h.zone == Zone.BATTERY -> stringResource(R.string.taller_charging).takeIf { (value ?: 0f) >= 13.2f }
                h.zone == Zone.TRANSMISSION -> stringResource(R.string.taller_no_codes_saved)
                h.zone == Zone.EXHAUST -> report?.readiness?.let { r -> stringResource(R.string.health_monitors, r.monitors.count { it.complete }, r.monitors.size) }
                else -> null
            }
            if (detail != null) IceText(
                detail, ts(13f, display = false), if (h.level == HealthLevel.FAULT) Color(255, 160, 152).copy(alpha = 0.92f) else if (bad) Color(255, 207, 133).copy(alpha = 0.9f) else ice.ink2,
                modifier = Modifier.padding(top = 7.dp),
            )
        }
        // .spark: la línea de los últimos 90 s, arriba a la derecha.
        if (signal != null && !bad) {
            val series = rememberSeries(d, signal)
            Sparkline(series, Color(200, 230, 255).copy(alpha = 0.55f), Modifier.align(Alignment.TopEnd).offset((-16).dp, 34.dp).size(78.dp, 24.dp))
        }
    }
}

/** "780 rpm" → ("780", "rpm"); "14,1 V" → ("14,1", "V"). */
private fun splitUnit(text: String): Pair<String, String> {
    val i = text.indexOfFirst { !(it.isDigit() || it == ',' || it == '.' || it == '-' || it == '−') }
    return if (i <= 0) text to "" else text.substring(0, i).trim() to text.substring(i).trim()
}

private fun Zone.icon(): ImageVector = when (this) {
    Zone.ENGINE -> SetIcons.engine
    Zone.INTAKE -> IceIcons.fuel
    Zone.COOLING -> IceIcons.thermometer
    Zone.BATTERY -> IceIcons.battery
    Zone.TRANSMISSION -> SetIcons.carA
    Zone.EXHAUST -> SetIcons.warn
    else -> SetIcons.info
}

/** Lo que dice un código en corto (la primera parte de su descripción). */
@Composable
private fun describeShort(code: String): String = describe(code, currentLocale().language == "es").substringBefore(' ').substringBefore(',').lowercase(currentLocale())

// ---------------------------------------------------------------- Códigos (taller-codigos.html)

@Composable
private fun CodesPanels(d: Dependencies, report: EngineReport?, reading: Boolean, onRead: () -> Unit, onCleared: (EngineReport?) -> Unit) {
    val ice = LocalIce.current
    val scope = rememberCoroutineScope()
    val spanish = currentLocale().language == "es"
    var confirm by remember { mutableIntStateOf(0) }
    var cleared by remember { mutableStateOf<Boolean?>(null) }
    val codes = report?.let {
        (it.stored.map { c -> c to CodeKind.STORED } + it.permanent.map { c -> c to CodeKind.PERMANENT } + it.pending.map { c -> c to CodeKind.PENDING })
            .groupBy({ it.first }, { it.second }).toList()
    }.orEmpty()
    // .codes: 458 de ancho desde el borde de .main.
    Box(Modifier.at(258, 134, 458, 378).glass(ice.glass, ice.line, ice.sheen, 20.dp)) {
        CapLabel(stringResource(R.string.taller_codes_count, codes.size), Modifier.at(18, 17))
        Column(Modifier.at(18, 30, 422, 236).verticalScroll(rememberScrollState())) {
            if (codes.isEmpty()) {
                IceTile(Modifier.padding(top = 10.dp).fillMaxWidth().height(84.dp), tint = Kc.mint) {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                        IceText(stringResource(if (reading && report == null) R.string.obd_reading else R.string.obd_no_codes), ts(20f, 500), Color(0xFF9FF3D6))
                        IceText(stringResource(R.string.taller_no_codes_saved), ts(13f, display = false), ice.ink2, modifier = Modifier.padding(top = 8.dp))
                    }
                }
            }
            for ((code, kinds) in codes) {
                val pending = kinds.all { it == CodeKind.PENDING }
                val tint = if (pending) Kc.warn else Kc.bad
                IceTile(Modifier.padding(top = 10.dp).fillMaxWidth(), tint = tint) {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IceText(code, ts(20f, 500, spacing = 0.04f), if (pending) Color(0xFFFFCF85) else Color(0xFFFF9A91))
                            Spacer(Modifier.width(10.dp))
                            for (k in kinds) {
                                Tag(stringResource(k.label), if (pending) Color(0xFFFFD99C) else Color(0xFFFFB3AD), dot = false, height = 24.dp, fill = 0.16f, borderAlpha = 0f)
                                Spacer(Modifier.width(6.dp))
                            }
                            Spacer(Modifier.weight(1f))
                            IceText(stringResource(zoneName(zoneOf(code))), ts(13f, display = false), ice.ink3)
                        }
                        IceText(describe(code, spanish), ts(16f, lh = 1.2f), Color.White, maxLines = 1, modifier = Modifier.padding(top = 7.dp))
                        IceText(
                            stringResource(
                                when {
                                    CodeKind.STORED in kinds -> R.string.taller_code_stored_do
                                    CodeKind.PERMANENT in kinds -> R.string.taller_code_permanent_do
                                    else -> R.string.taller_code_pending_do
                                },
                            ),
                            ts(13f, display = false, lh = 1.3f), ice.ink2, modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }
        }
        // .confirm: borrar con dos pasos (apaga la luz pero reinicia los monitores).
        val canClear = report != null && (report.stored.isNotEmpty() || report.pending.isNotEmpty() || report.readiness?.mil == true)
        IceTile(Modifier.align(Alignment.BottomStart).at(18, -14, 422, 122), tint = Kc.bad.takeIf { confirm > 0 }) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(SetIcons.trash, Color(0xFFFF9A91), 19.dp)
                    Spacer(Modifier.width(8.dp))
                    IceText(stringResource(R.string.taller_clear_q), ts(15f, 500), Color.White, modifier = Modifier.weight(1f))
                    IceText(stringResource(R.string.taller_clear_step, if (confirm == 2) 2 else 1), ts(13f, display = false), ice.ink3)
                }
                IceText(
                    cleared?.let { stringResource(if (it) R.string.obd_cleared else R.string.obd_not_cleared) }
                        ?: stringResource(if (confirm == 2) R.string.taller_clear_p2 else R.string.taller_clear_p1),
                    ts(13f, display = false, lh = 1.35f), if (cleared == false) Kc.coral else ice.ink2, maxLines = 1, modifier = Modifier.padding(top = 6.dp),
                )
                Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    when (confirm) {
                        0 -> {
                            IceBtn(stringResource(R.string.obd_read_again), onRead, Modifier.weight(1f), icon = SetIcons.refresh)
                            IceBtn(stringResource(R.string.obd_clear), { confirm = 1 }, Modifier.weight(1f), BtnKind.DANGER, enabled = canClear)
                        }
                        1 -> {
                            IceBtn(stringResource(R.string.cancel), { confirm = 0 }, Modifier.weight(1f))
                            IceBtn(stringResource(R.string.obd_clear_sure), { confirm = 2 }, Modifier.weight(1f), BtnKind.DANGER)
                        }
                        else -> {
                            IceBtn(stringResource(R.string.cancel), { confirm = 0 }, Modifier.weight(1f))
                            IceBtn(stringResource(R.string.obd_clear_final), {
                                confirm = 0
                                scope.launch {
                                    cleared = d.obd.clearCodes()
                                    onCleared(d.obd.report())
                                }
                            }, Modifier.weight(1f), BtnKind.DANGER)
                        }
                    }
                }
            }
        }
    }
    // .insp: la prueba de gases (luz del motor y monitores).
    Box(Modifier.at(728, 134, 280, 378).glass(ice.glass, ice.line, ice.sheen, 20.dp)) {
        CapLabel(stringResource(R.string.taller_gases), Modifier.at(18, 17))
        val readiness = report?.readiness
        if (readiness == null) {
            IceText(stringResource(if (reading) R.string.obd_reading else R.string.obd_no_readiness), ts(14f, display = false, lh = 1.4f), ice.ink2, maxLines = 3, modifier = Modifier.at(18, 48, 244))
            return@Box
        }
        val pass = !readiness.mil && readiness.incomplete.size <= 1
        Row(Modifier.at(18, 42), verticalAlignment = Alignment.CenterVertically) {
            val tint = if (pass) Kc.mint else Color(0xFFFF9A91)
            Box(
                Modifier.size(46.dp).clip(CircleShape).background(tint.copy(alpha = 0.10f)).border(1.dp, tint.copy(alpha = 0.4f), CircleShape),
                contentAlignment = Alignment.Center,
            ) { Icon(if (pass) SetIcons.check else SetIcons.warn, tint, 22.dp) }
            Spacer(Modifier.width(12.dp))
            Column {
                IceText(stringResource(R.string.taller_gases_q), ts(14f, lh = 1.25f), ice.ink2, maxLines = 2)
                IceText(stringResource(if (pass) R.string.taller_gases_yes else R.string.taller_gases_no), ts(25f, 300), if (pass) Color(0xFF9FF3D6) else Color(0xFFFFB3AD), modifier = Modifier.padding(top = 5.dp))
            }
        }
        val milColor = if (readiness.mil) Kc.bad else Kc.mint
        Row(
            Modifier.at(18, 118, 244, 44).clip(RoundedCornerShape(14.dp)).background(milColor.copy(alpha = 0.08f)).border(1.dp, milColor.copy(alpha = 0.3f), RoundedCornerShape(14.dp)).padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            KDot(milColor)
            Spacer(Modifier.width(10.dp))
            IceText(stringResource(R.string.taller_mil), ts(14f, display = false), Color(0xFFE6F1FF), modifier = Modifier.weight(1f))
            IceText(stringResource(if (readiness.mil) R.string.taller_mil_on else R.string.taller_mil_off), ts(14f, 500, display = false), if (readiness.mil) Color(0xFFFFB3AD) else Color(0xFF9FF3D6))
        }
        Column(Modifier.at(18, 168, 244, 196).verticalScroll(rememberScrollState())) {
            readiness.monitors.forEachIndexed { i, m ->
                Row(Modifier.height(30.dp), verticalAlignment = Alignment.CenterVertically) {
                    KDot(if (m.complete) Kc.mint else Kc.warn)
                    Spacer(Modifier.width(10.dp))
                    IceText(stringResource(monitorName(m.name)), ts(14f, display = false), Color(0xFFE6F1FF), modifier = Modifier.weight(1f))
                    IceText(stringResource(if (m.complete) R.string.obd_ready else R.string.obd_not_ready), ts(13f, display = false), if (m.complete) ice.ink3 else Color(0xFFFFD99C))
                }
                if (i < readiness.monitors.lastIndex) HLine()
            }
        }
    }
}

// ---------------------------------------------------------------- Sensores

@Composable
private fun SensorsPanel(d: Dependencies) {
    val ice = LocalIce.current
    val locale = currentLocale()
    val readable = readableSignals(d)
    val order = listOf(Signal.RPM, Signal.COOLANT, Signal.VOLTAGE, Signal.SPEED, Signal.LOAD, Signal.CONSUMPTION, Signal.OIL, Signal.BOOST)
    val signals = remember(readable) { readable.sortedBy { order.indexOf(it).let { i -> if (i < 0) order.size else i } } }
    TallerPanel {
        CapLabel(stringResource(R.string.taller_sensors_live, signals.size), Modifier.at(20, 18))
        Column(Modifier.at(20, 44, 710, 320).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            for (row in signals.chunked(3)) Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                for (s in row) key(s) {
                    val value = d.bus.watch(s)
                    val series = rememberSeries(d, s)
                    IceTile(Modifier.weight(1f).height(96.dp)) {
                        Column(Modifier.padding(horizontal = 16.dp, vertical = 11.dp)) {
                            IceText(stringResource(sensorName(s)), ts(14f, display = false), ice.ink2)
                            val (num, unit) = splitUnit(value?.let { sensorText(s, it, d.prefs.units, locale) } ?: "—")
                            Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.Bottom) {
                                IceText(num, ts(26f, 300), Color.White)
                                if (unit.isNotEmpty()) IceText(unit, ts(15f), ice.ink2, modifier = Modifier.padding(start = 4.dp, bottom = 1.dp))
                            }
                        }
                        Sparkline(series, Kc.ice.copy(alpha = 0.7f), Modifier.align(Alignment.BottomEnd).offset((-14).dp, (-14).dp).size(84.dp, 26.dp))
                    }
                }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

// ---------------------------------------------------------------- Gráficas y cronómetro (graficas.html)

@Composable
private fun ChartPanels(d: Dependencies) {
    val ice = LocalIce.current
    val locale = currentLocale()
    val units = d.prefs.units
    val signals = readableSignals(d)
    val chosen = remember { mutableStateListOf<String>() }
    LaunchedEffect(signals) { if (chosen.isEmpty()) chosen += listOf(Signal.RPM, Signal.SPEED).filter { it in signals } }
    var touch by remember { mutableStateOf<Float?>(null) }
    val colors = listOf(Kc.ice, Kc.gold)
    Box(Modifier.at(258, 134, 474, 378).glass(ice.glass, ice.line, ice.sheen, 20.dp)) {
        CapLabel(stringResource(R.string.taller_live), Modifier.at(20, 15))
        val series = chosen.map { s -> s to key(s) { rememberSeries(d, s) } }
        Column(Modifier.at(20, 41, 434), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            series.forEachIndexed { i, (s, points) ->
                Row(Modifier.height(19.dp), verticalAlignment = Alignment.CenterVertically) {
                    KDot(colors[i])
                    Spacer(Modifier.width(10.dp))
                    IceText(stringResource(sensorName(s)), ts(15f, display = false), Color(0xFFEAF4FF), modifier = Modifier.weight(1f))
                    val min = points.minOrNull()
                    val max = points.maxOrNull()
                    if (min != null && max != null) IceText(stringResource(R.string.obd_chart_range, sensorText(s, min, units, locale), sensorText(s, max, units, locale)), ts(13f, display = false), ice.ink3)
                    val shown = touch?.let { at -> points.getOrNull((at * (SERIES_POINTS - 1)).roundToInt() - (SERIES_POINTS - points.size)) } ?: points.lastOrNull()
                    IceText(shown?.let { sensorText(s, it, units, locale) } ?: "—", ts(19f, 500, align = androidx.compose.ui.text.style.TextAlign.End), colors[i], modifier = Modifier.width(110.dp))
                }
            }
        }
        // .plot: 130 de alto a 94 de arriba.
        Canvas(
            Modifier.at(20, 94, 434, 130).pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    touch = (down.position.x / size.width).coerceIn(0f, 1f)
                    while (true) {
                        val change = awaitPointerEvent().changes.firstOrNull() ?: break
                        if (!change.pressed) break
                        touch = (change.position.x / size.width).coerceIn(0f, 1f)
                    }
                    touch = null
                }
            },
        ) {
            val grid = Color(190, 225, 255).copy(alpha = 0.07f)
            for (k in 0..3) drawLine(grid, Offset(0f, size.height * k / 3), Offset(size.width, size.height * k / 3), 1.dp.toPx())
            series.forEachIndexed { i, (_, points) ->
                if (points.size < 2) return@forEachIndexed
                val lo = points.min()
                val hi = points.max().let { if (it - lo < 1e-3f) lo + 1f else it }
                fun yOf(v: Float) = size.height * (1 - (v - lo) / (hi - lo)) * 0.86f + size.height * 0.07f
                val path = seriesPath(points, size.width, SERIES_POINTS, ::yOf)
                if (i == 0) {
                    val veil = androidx.compose.ui.graphics.Path().apply {
                        addPath(path); lineTo(size.width, size.height); lineTo(size.width * (SERIES_POINTS - points.size) / (SERIES_POINTS - 1), size.height); close()
                    }
                    drawPath(veil, Brush.verticalGradient(listOf(colors[0].copy(alpha = 0.18f), Color.Transparent)))
                }
                drawPath(path, colors[i], style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                val end = Offset(size.width, yOf(points.last()))
                drawCircle(colors[i], 4.dp.toPx(), end)
                drawCircle(Color.White, 4.dp.toPx(), end, style = Stroke(1.2f.dp.toPx()))
                touch?.let { at -> points.getOrNull((at * (SERIES_POINTS - 1)).roundToInt() - (SERIES_POINTS - points.size)) }?.let { v ->
                    drawCircle(colors[i], 5.dp.toPx(), Offset(size.width * touch!!, yOf(v)))
                }
            }
            touch?.let { drawLine(Color(196, 216, 238).copy(alpha = 0.5f), Offset(size.width * it, 0f), Offset(size.width * it, size.height), 1.dp.toPx()) }
        }
        Row(Modifier.at(20, 231, 434)) {
            IceText(stringResource(R.string.taller_ago_90), ts(13f, display = false), ice.ink3)
            Spacer(Modifier.weight(1f))
            IceText(stringResource(R.string.taller_ago_45), ts(13f, display = false), ice.ink3)
            Spacer(Modifier.weight(1f))
            IceText(stringResource(R.string.taller_now), ts(13f, display = false), ice.ink3)
        }
        // .chips2: los datos para elegir, 3 por fila.
        Column(Modifier.align(Alignment.BottomStart).at(20, -14, 434), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            for (row in signals.take(6).chunked(3)) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (s in row) {
                    val idx = chosen.indexOf(s)
                    val on = idx >= 0
                    val shape = RoundedCornerShape(22.dp)
                    var m = Modifier.weight(1f).height(44.dp).clip(shape)
                    m = if (on && idx == 1) m.background(Brush.linearGradient(listOf(Color(255, 216, 138).copy(alpha = 0.24f), Color(255, 190, 90).copy(alpha = 0.08f)))).border(1.dp, Color(255, 216, 138).copy(alpha = 0.40f), shape)
                    else if (on) m.segOn(22.dp, 0.30f, 0.36f, 0.18f)
                    else m.background(Color(170, 210, 255).copy(alpha = 0.04f)).border(1.dp, ice.line2, shape)
                    Row(
                        m.clickable {
                            when {
                                on -> chosen.remove(s)
                                chosen.size < 2 -> chosen += s
                                else -> { chosen.removeAt(0); chosen += s }
                            }
                        },
                        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (on) {
                            Box(Modifier.size(8.dp).clip(CircleShape).background(colors[idx]))
                            Spacer(Modifier.width(7.dp))
                        }
                        IceText(stringResource(sensorName(s)), ts(13f), if (on) Color.White else Color(222, 236, 252).copy(alpha = 0.8f))
                    }
                }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
    AccelTimerPanel(d, Modifier.at(744, 134, 264, 378))
}

/** El cronómetro de 0 a 100 (.timer): el dial con el avance, el estado y los tiempos. */
@Composable
private fun AccelTimerPanel(d: Dependencies, modifier: Modifier) {
    val ice = LocalIce.current
    val locale = currentLocale()
    val units = d.prefs.units
    val target = if (units.miles) 60f * KM_PER_MILE else 100f
    val timer = remember(target) { AccelTimer(target) }
    var state by remember { mutableStateOf(timer.state) }
    var previous by remember { mutableStateOf<Float?>(null) }
    var tick by remember { mutableIntStateOf(0) }
    val speed = d.bus.watch(Signal.SPEED)
    LaunchedEffect(speed) {
        if (speed == null) return@LaunchedEffect
        val before = state
        timer.update(SystemClock.elapsedRealtime(), speed)
        state = timer.state
        (state as? AccelState.Done)?.let { done ->
            if (before !is AccelState.Done) previous = done.seconds.takeIf { previous != it }?.let { previous } ?: previous
            if (d.prefs.bestAccel <= 0f || done.seconds < d.prefs.bestAccel) d.prefs.bestAccel = done.seconds
        }
    }
    LaunchedEffect(state) { while (state is AccelState.Running) { tick++; delay(50) } }
    val shown = when (val s = state) {
        is AccelState.Running -> { tick; (SystemClock.elapsedRealtime() - s.startMs) / 1000f }
        is AccelState.Done -> s.seconds
        else -> d.prefs.bestAccel.takeIf { it > 0f } ?: 0f
    }
    Box(modifier.glass(ice.glass, ice.line, ice.sheen, 20.dp)) {
        CapLabel(stringResource(R.string.taller_timer), Modifier.at(18, 15))
        Row(Modifier.at(18, 41), verticalAlignment = Alignment.CenterVertically) {
            Icon(SetIcons.timer, Kc.ice, 24.dp)
            Spacer(Modifier.width(10.dp))
            IceText(stringResource(if (units.miles) R.string.accel_title_mph else R.string.accel_title), ts(18f), Color.White)
        }
        // .dial: el anillo con lo que va de la velocidad hacia la meta.
        val progress = ((speed ?: 0f) / target).coerceIn(0f, 1f).let { if (state is AccelState.Done) 1f else it }
        Box(Modifier.align(Alignment.TopCenter).offset(y = 70.dp).size(164.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val r = size.minDimension / 2 - 3.dp.toPx()
                drawCircle(Color(190, 225, 255).copy(alpha = 0.10f), r, style = Stroke(2.dp.toPx()))
                drawCircle(Color(190, 225, 255).copy(alpha = 0.22f), r - 10.dp.toPx(), style = Stroke(1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(1.dp.toPx(), 5.dp.toPx()))))
                drawArc(
                    Brush.sweepGradient(listOf(Color(0xFF6FBFFF), Color(0xFFD8F0FF), Color(0xFF6FBFFF))), -90f, 360f * progress.coerceAtLeast(0.001f), false,
                    Offset(center.x - r, center.y - r), Size(2 * r, 2 * r), style = Stroke(2.5f.dp.toPx(), cap = StrokeCap.Round),
                )
                val a = Math.toRadians((-90 + 360 * progress).toDouble())
                drawCircle(Color.White, 4.dp.toPx(), Offset(center.x + r * kotlin.math.cos(a).toFloat(), center.y + r * kotlin.math.sin(a).toFloat()))
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                IceText(String.format(locale, "%.2f", shown), ts(44f, 200), Color(0xFFF4FAFF), modifier = Modifier.softGlow(Color(150, 210, 255).copy(alpha = 0.12f), 10.dp))
                IceText(stringResource(R.string.taller_seconds), ts(13f, display = false, spacing = 0.06f), ice.ink2, modifier = Modifier.padding(top = 5.dp))
            }
        }
        val stColor = when (state) {
            AccelState.WaitStop -> Kc.warn
            AccelState.Ready -> Kc.mint
            is AccelState.Running -> Kc.ice
            is AccelState.Done -> Kc.mint
        }
        Row(
            Modifier.at(16, 244, 232, 34).clip(RoundedCornerShape(17.dp)).background(stColor.copy(alpha = 0.10f)).border(1.dp, stColor.copy(alpha = 0.32f), RoundedCornerShape(17.dp)),
            horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
        ) {
            KDot(stColor)
            Spacer(Modifier.width(8.dp))
            IceText(
                stringResource(
                    when (state) {
                        AccelState.WaitStop -> R.string.accel_wait_stop
                        AccelState.Ready -> R.string.accel_ready
                        is AccelState.Running -> R.string.accel_running
                        is AccelState.Done -> R.string.accel_done
                    },
                ),
                ts(13f, display = false), Color(0xFFFFE2A8).takeIf { state == AccelState.WaitStop } ?: Color.White,
            )
        }
        Row(Modifier.at(18, 290, 228)) {
            IceText(stringResource(R.string.taller_best) + " ", ts(13f, display = false), ice.ink2)
            IceText(if (d.prefs.bestAccel > 0f) String.format(locale, "%.2f s", d.prefs.bestAccel) else "—", ts(13f, display = false), Color.White)
            Spacer(Modifier.weight(1f))
            IceText(stringResource(R.string.taller_previous) + " ", ts(13f, display = false), ice.ink2)
            IceText(previous?.let { String.format(locale, "%.2f s", it) } ?: "—", ts(13f, display = false), Color.White)
        }
        IceBtn(stringResource(R.string.accel_reset), { timer.reset(); state = timer.state }, Modifier.align(Alignment.BottomStart).at(20, -14, 224), icon = SetIcons.refresh)
    }
}

// ---------------------------------------------------------------- Más

@Composable
private fun BoxScope.MorePanel(d: Dependencies, garage: Garage) {
    val ice = LocalIce.current
    val locale = currentLocale()
    val open = moreOpen
    if (open != null) {
        if (open < GaragePart.entries.size) GaragePanel(d, garage, GaragePart.entries[open]) else TallerPanel {
            Column(Modifier.at(24, 20, 702, 340).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        IceText(stringResource(R.string.engine_alerts), ts(15f, display = false), ice.ink)
                        IceText(stringResource(R.string.engine_alerts_help), ts(12f, display = false, lh = 1.35f), ice.ink2, maxLines = 2, modifier = Modifier.padding(top = 5.dp))
                    }
                    IceSwitch(d.prefs.engineAlerts, { d.prefs.engineAlerts = it })
                }
                Connection(d)
                TripsCard(d)
            }
        }
        // Volver a la lista de "Más".
        IceBtn(stringResource(R.string.taller_more_back), { moreOpen = null }, Modifier.align(Alignment.TopEnd).at(-16, 72), BtnKind.GHOST, icon = SetIcons.back, height = 40.dp, fontSize = 14f, padding = 16.dp)
        return
    }
    TallerPanel {
        CapLabel(stringResource(R.string.taller_more_title), Modifier.at(24, 20))
        val odo = garage.odometer
        val fuel = remember(garage.fuel) { FuelMath.average(garage.fuel) }
        val items = listOf(
            Triple(SetIcons.pin, R.string.taller_tab_trip, "${decimalText(garage.trip.km, 1, locale)} km"),
            Triple(SetIcons.odometer, R.string.taller_tab_odometer, odo?.let { "${groupThousands(it.km.toLong(), locale)} km" } ?: stringResource(R.string.taller_odo_unset)),
            Triple(IceIcons.fuel, R.string.taller_tab_fuel, fuel?.let { "${decimalText(it.kmPerGallon, 1, locale)} km/gal" } ?: "—"),
            Triple(SetIcons.notebook, R.string.taller_tab_log, stringResource(R.string.taller_log_count, garage.log.size)),
            Triple(IceIcons.plug, R.string.taller_tab_connection, stringResource(R.string.taller_more_connection_sub)),
        )
        Column(Modifier.at(24, 48, 702), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            for (row in items.withIndex().chunked(3)) Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                for ((i, item) in row) IceTile(Modifier.weight(1f).height(140.dp), onClick = { moreOpen = i }) {
                    Column(Modifier.padding(16.dp)) {
                        Well(item.first, Kc.ice, 40.dp, 20.dp)
                        IceText(stringResource(item.second), ts(18f, 500), Color.White, modifier = Modifier.padding(top = 14.dp))
                        IceText(item.third, ts(13f, display = false, lh = 1.3f), ice.ink2, maxLines = 2, modifier = Modifier.padding(top = 7.dp))
                    }
                }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}
