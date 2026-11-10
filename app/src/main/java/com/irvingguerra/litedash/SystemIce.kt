// SailDash 1.1 · diseño C: Ajustes → Sistema (catalogo/diagnostico, actualizaciones) y Mandos al volante
// (catalogo/mandos-volante). La lógica es la de siempre (Diagnostics, Updater, WheelKeys).
package com.irvingguerra.litedash

import android.Manifest
import android.content.Context
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.irvingguerra.litedash.core.ui.Icon
import com.irvingguerra.litedash.core.ui.ice.IceIcons
import com.irvingguerra.litedash.core.ui.ice.IceText
import com.irvingguerra.litedash.core.ui.ice.LocalIce
import com.irvingguerra.litedash.core.ui.ice.softGlow
import com.irvingguerra.litedash.feature.updater.Updater
import com.irvingguerra.litedash.feature.updater.Version
import java.text.SimpleDateFormat
import java.util.Date
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val SystemTabs = mutableStateMapOf<String, Int>()

/** Ajustes → Sistema en la hoja del diseño C: Diagnóstico, Actualizaciones y Acerca de. */
@Composable
fun SystemScreen(d: Dependencies, onSection: (SettingsSection) -> Unit, close: () -> Unit, onPanel: (Panel) -> Unit, extra: List<SettingsSection>, start: Int = 0) {
    // El aviso de versión nueva abre aquí en Actualizaciones.
    val tab = SystemTabs["system"] ?: start
    IceSettingsFrame(d, MenuStyle.SHEET, SettingsSection.SYSTEM, onSection, close, onPanel, extra) {
        SheetHeader(stringResource(R.string.system)) {
            IceSeg(
                listOf(stringResource(R.string.sys_tab_diag), stringResource(R.string.sys_tab_updates), stringResource(R.string.sys_tab_about)),
                tab, { SystemTabs["system"] = it }, style = SegStyle.SHEET,
            )
        }
        when (tab) {
            0 -> DiagnosticsC()
            1 -> UpdatesC(d.updater)
            else -> AboutC()
        }
    }
}

/** Abre Sistema en Actualizaciones (desde el aviso de versión nueva). */
internal fun openUpdatesC() {
    SystemTabs["system"] = 1
}

// ---------------------------------------------------------------- Diagnóstico (diagnostico.html)

@Composable
private fun BoxScope.DiagnosticsC() {
    val ice = LocalIce.current
    val context = LocalContext.current
    LaunchedEffect(Unit) { Diagnostics.check(context) }
    val report = Diagnostics.report
    var details by remember { mutableStateOf(false) }
    // Lo más importante (388 × 392)
    IceCard(Modifier.at(284, 100, 388, 392), radius = 18.dp) {
        Row(Modifier.at(18, 14, 356), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                IceText(stringResource(R.string.sys_important), ts(17f, 500, spacing = 0.01f, lh = 1.15f), Color.White)
                IceText(stringResource(if (Diagnostics.checking) R.string.sys_checking else R.string.sys_only_looks), ts(13f, display = false, lh = 1.35f), ice.ink2, modifier = Modifier.padding(top = 5.dp))
            }
            RoundBtn(SetIcons.refresh, { Diagnostics.check(context, again = true); CameraCheck.check(context) })
        }
        if (details && report != null) {
            Column(Modifier.at(18, 72, 354, 266).verticalScroll(rememberScrollState())) {
                for (section in report.sections) {
                    HLine()
                    IceText(section.title, ts(14f, 500), Color.White, modifier = Modifier.padding(top = 10.dp, bottom = 6.dp))
                    for (line in section.lines) IceText(line, ts(12f, display = false, lh = 1.35f), ice.ink2, maxLines = 3, modifier = Modifier.padding(bottom = 3.dp))
                }
            }
        } else Column(Modifier.at(18, 72, 354)) {
            for (row in summaryRows(context, report)) {
                HLine()
                Row(Modifier.height(54.dp).then(if (row.onClick != null) Modifier.clickable(onClick = row.onClick) else Modifier), verticalAlignment = Alignment.CenterVertically) {
                    Well(row.icon, row.tint, 40.dp, 20.dp)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        IceText(row.title, ts(15f, display = false, lh = 1.25f), ice.ink)
                        if (row.sub != null) IceText(row.sub, ts(13f, display = false, lh = 1.35f), ice.ink2)
                    }
                }
            }
        }
        // .more
        Column(Modifier.align(Alignment.BottomStart).at(18, 0, 354)) {
            HLine()
            Row(Modifier.height(46.dp).clickable(enabled = report != null) { details = !details }, verticalAlignment = Alignment.CenterVertically) {
                IceText(stringResource(if (details) R.string.sys_details_hide else R.string.sys_details), ts(15f, 500), Kc.ice)
                Spacer(Modifier.width(6.dp))
                Icon(SetIcons.chevronRight, Kc.ice, 18.dp)
            }
        }
    }
    RecorderC(report)
    UsbC(report)
}

private class SummaryRow(val icon: ImageVector, val tint: Color, val title: String, val sub: String?, val onClick: (() -> Unit)? = null)

/** Los renglones del resumen (lo mismo que el reporte, partido en título y una línea) y la cámara. */
@Composable
private fun summaryRows(context: Context, report: DiagReport?): List<SummaryRow> {
    val rows = ArrayList<SummaryRow>()
    val icons = listOf(SetIcons.monitor, SetIcons.split, SetIcons.sliders, IceIcons.apps)
    report?.summary?.take(4)?.forEachIndexed { i, v ->
        val (title, rest) = splitVerdict(v.text)
        val sub = when (i) {
            0 -> deviceLine(context)
            1 -> if (v.good == true) stringResource(R.string.sys_split_sub) else rest
            2 -> if (v.good == true) Regex("(\\d+)").find(v.text)?.value?.toIntOrNull()?.let { stringResource(R.string.sys_eq_sub, it) } ?: rest else rest
            else -> stringResource(R.string.sys_apps_sub)
        }
        rows += SummaryRow(icons.getOrElse(i) { SetIcons.info }, if (v.good == false) Kc.warn else if (i == 0) Kc.ice else Kc.mint, title, sub?.takeIf { it.isNotBlank() })
    }
    // SailDash: la cámara (Camera API): cuántas ve Android, si abren y su resolución.
    val askCamera = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { if (it) CameraCheck.check(context) }
    LaunchedEffect(Unit) { if (CameraCheck.results == null && CameraCheck.hasPermission(context)) CameraCheck.check(context) }
    val cams = CameraCheck.results
    val ok = cams?.filter { it.opened }.orEmpty()
    val check = { if (CameraCheck.hasPermission(context)) CameraCheck.check(context) else askCamera.launch(Manifest.permission.CAMERA) }
    val names = mapOf("back" to stringResource(R.string.cam_back), "front" to stringResource(R.string.cam_front))
    val external = stringResource(R.string.cam_external)
    fun facingName(f: String) = names[f] ?: external
    rows += when {
        CameraCheck.checking -> SummaryRow(SetIcons.camera, Kc.ice, stringResource(R.string.rev_view_checking), null)
        cams == null -> SummaryRow(SetIcons.camera, Kc.ice, stringResource(R.string.cam_row_unchecked), stringResource(R.string.cam_row_unchecked_sub), check)
        cams.isEmpty() -> SummaryRow(SetIcons.camera, Kc.warn, stringResource(R.string.cam_row_none), stringResource(R.string.cam_row_none_sub), check)
        ok.isEmpty() -> SummaryRow(SetIcons.camera, Kc.warn, stringResource(R.string.cam_row_fail, cams.size), cams.joinToString(" · ") { "${facingName(it.facing)} ${it.error ?: ""}".trim() }, check)
        else -> SummaryRow(
            SetIcons.camera, Kc.mint, stringResource(R.string.cam_row_ok, ok.size, ok.first().resolution),
            stringResource(R.string.cam_row_sub, cams.joinToString(" · ") { "${facingName(it.facing)} ${it.resolution}" }), check,
        )
    }
    return rows
}


/** "Tu pantalla tiene Android 7.1 (API 25). Más…" → ("Tu pantalla tiene Android 7.1 (API 25).", "Más…"). */
private fun splitVerdict(text: String): Pair<String, String?> {
    val cut = listOf(". ", ", con ", ", with ", " (").map { text.indexOf(it) }.filter { it > 0 }.minOrNull() ?: return text to null
    val title = text.substring(0, cut).trimEnd('.', ',', ';') + "."
    val rest = text.substring(cut).trimStart('.', ',', ' ').takeIf { it.length > 2 }
    return title to rest
}

/** "Allwinner T3L · 2 GB de RAM · 1024×600". */
@Composable
private fun deviceLine(context: Context): String {
    val m = context.resources.displayMetrics
    val gb = memoryMb(context) / 1024.0
    val ram = if (gb >= 1) String.format(currentLocale(), "%.0f GB", gb) else "${memoryMb(context)} MB"
    val hw = listOf(Build.HARDWARE, Build.BOARD).firstOrNull { !it.isNullOrBlank() && it != "unknown" }?.replaceFirstChar { it.uppercase() } ?: Build.MODEL
    return "$hw · ${stringResource(R.string.sys_ram, ram)} · ${maxOf(m.widthPixels, m.heightPixels)}×${minOf(m.widthPixels, m.heightPixels)}"
}

@Composable
private fun RecorderC(report: DiagReport?) {
    val ice = LocalIce.current
    val context = LocalContext.current
    val rec = DiagnosticsRecorder
    val version = rec.version
    IceCard(Modifier.at(684, 100, 294, 256), radius = 18.dp, hi = rec.recording) {
        Row(Modifier.at(18, 14, 262), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.then(if (rec.recording) Modifier.softGlow(Color(0xFFFF6B7F).copy(alpha = 0.5f), 6.dp) else Modifier).size(12.dp).clip(CircleShape)
                    .background(if (rec.recording) Color(0xFFFF6B7F) else ice.ink3),
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                IceText(stringResource(R.string.diagnostico_rec_title), ts(17f, 500, spacing = 0.01f, lh = 1.15f), Color.White)
                var left by remember { mutableLongStateOf(rec.remainingMs()) }
                if (rec.recording) LaunchedEffect(Unit) { while (true) { left = rec.remainingMs(); delay(1_000) } }
                IceText(
                    if (rec.recording) stringResource(R.string.sys_rec_running, DiagFormat.mmss(left)) else stringResource(R.string.sys_rec_idle),
                    ts(13f, display = false, lh = 1.35f), ice.ink2, modifier = Modifier.padding(top = 5.dp),
                )
            }
            // .stop / grabar
            Box(
                Modifier.size(44.dp).clip(CircleShape).background(Color(255, 107, 127).copy(alpha = 0.08f)).border(1.dp, Color(255, 140, 160).copy(alpha = 0.4f), CircleShape)
                    .clickable { if (rec.recording) rec.stop() else rec.start(context, report?.actions.orEmpty()) },
                contentAlignment = Alignment.Center,
            ) {
                if (rec.recording) Box(Modifier.size(12.dp).clip(RoundedCornerShape(2.dp)).background(Color(0xFFFF8597)))
                else Box(Modifier.size(14.dp).clip(CircleShape).background(Color(0xFFFF6B7F)))
            }
        }
        val shown = remember(version) { rec.newest(3) }
        Column(Modifier.at(18, 68, 258)) {
            if (shown.isEmpty()) IceText(stringResource(R.string.sys_rec_empty), ts(13f, display = false, lh = 1.4f), ice.ink3, maxLines = 4, modifier = Modifier.padding(top = 6.dp))
            shown.forEachIndexed { i, e ->
                key(e.id) {
                    HLine()
                    Column(Modifier.padding(top = 5.dp, bottom = 6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IceText(e.clock, ts(13f, display = false), ice.ink3)
                            Spacer(Modifier.width(8.dp))
                            val (fg, bg) = when (e.kind) {
                                DiagEventKind.VOLUME -> Color(0xFF8FF0CF) to Color(126, 240, 194).copy(alpha = 0.12f)
                                DiagEventKind.SETTING -> Color(0xFFBFE6FF) to Color(120, 190, 255).copy(alpha = 0.14f)
                                DiagEventKind.BROADCAST -> Color(0xFFFFD88A) to Color(255, 214, 120).copy(alpha = 0.12f)
                                DiagEventKind.INFO -> ice.ink2 to Color(170, 210, 255).copy(alpha = 0.08f)
                            }
                            Box(Modifier.height(20.dp).clip(RoundedCornerShape(10.dp)).background(bg).padding(horizontal = 8.dp), contentAlignment = Alignment.Center) {
                                IceText(stringResource(e.kind.label), ts(13f, 500, display = false), fg)
                            }
                        }
                        IceText(e.text, ts(13f, display = false, lh = 1.2f).copy(fontFamily = FontFamily.Monospace), if (i == 0) Color.White else Color(0xFFE6F2FF), modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }
        }
        if (rec.total > 0) IceText(stringResource(R.string.sys_rec_count, rec.total), ts(13f, display = false), ice.ink3, modifier = Modifier.align(Alignment.BottomStart).at(18, -12))
    }
}

@Composable
private fun UsbC(report: DiagReport?) {
    val ice = LocalIce.current
    val context = LocalContext.current
    val export = DiagnosticsExport
    IceCard(Modifier.at(684, 368, 294, 124), radius = 18.dp) {
        Row(Modifier.at(18, 14, 258), verticalAlignment = Alignment.CenterVertically) {
            Well(SetIcons.usb, Kc.ice)
            Spacer(Modifier.width(10.dp))
            Column {
                IceText(stringResource(R.string.diagnostico_export_title), ts(15f, display = false, lh = 1.25f), ice.ink)
                val r = export.results
                IceText(
                    export.progress ?: r.firstOrNull { it.error == null }?.path ?: stringResource(R.string.sys_usb_sub),
                    ts(13f, display = false, lh = 1.35f), ice.ink2, modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        val ready = report != null && !export.working && !Diagnostics.checking
        IceBtn(
            stringResource(if (export.working) R.string.diagnostico_export_working else R.string.diagnostico_export_button),
            { if (ready) export.start(context, report!!) },
            Modifier.align(Alignment.BottomStart).at(18, -14, 258), BtnKind.LIGHT, icon = SetIcons.download, enabled = ready,
        )
    }
}

// ---------------------------------------------------------------- Actualizaciones (actualizaciones.html)

private sealed interface UpdState {
    data object Idle : UpdState
    data object Checking : UpdState
    data object UpToDate : UpdState
    data object Error : UpdState
    data class Found(val v: Version) : UpdState
}

@Composable
private fun BoxScope.UpdatesC(a: Updater) {
    val ice = LocalIce.current
    val context = LocalContext.current
    val locale = currentLocale()
    val scope = rememberCoroutineScope()
    val confirm = Updater.confirm
    LaunchedEffect(confirm) {
        if (confirm == null) return@LaunchedEffect
        Updater.confirm = null
        runCatching { context.startActivity(confirm) }
    }
    var state by remember { mutableStateOf<UpdState>(a.found?.let { UpdState.Found(it) } ?: UpdState.Idle) }
    var lastCheck by remember { mutableLongStateOf(context.getSharedPreferences("updates", Context.MODE_PRIVATE).getLong("last_check", 0L)) }
    fun search() {
        if (!a.available || state == UpdState.Checking) return
        state = UpdState.Checking
        scope.launch {
            state = runCatching { a.checkForUpdate() }.fold({ v -> if (v == null) UpdState.UpToDate else UpdState.Found(v) }, { UpdState.Error })
            lastCheck = System.currentTimeMillis()
        }
    }
    // Tienes
    IceCard(Modifier.at(284, 100, 296, 392), radius = 18.dp) {
        Column(Modifier.at(20, 20, 256)) {
            CapLabel(stringResource(R.string.sys_you_have))
            Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.Bottom) {
                IceText(BuildConfig.VERSION_NAME, ts(56f, 200, spacing = -0.01f), Color.White, modifier = Modifier.softGlow(Color(150, 210, 255).copy(alpha = 0.10f), 12.dp))
                IceText("SailDash", ts(15f, display = false), ice.ink2, modifier = Modifier.padding(start = 10.dp, bottom = 6.dp))
            }
            val installed = remember { runCatching { context.packageManager.getPackageInfo(context.packageName, 0).lastUpdateTime }.getOrDefault(0L) }
            IceText(
                stringResource(R.string.sys_installed, BuildConfig.CHANNEL.replaceFirstChar { it.uppercase() }, SimpleDateFormat(if (locale.language == "es") "d 'de' MMM" else "MMM d", locale).format(Date(installed))),
                ts(13f, display = false, lh = 1.35f), ice.ink2, modifier = Modifier.padding(top = 10.dp),
            )
        }
        Column(Modifier.at(20, 150, 256)) {
            HLine()
            KeyValue(stringResource(R.string.sys_last_check), if (lastCheck == 0L) stringResource(R.string.sys_never) else lastCheckText(lastCheck))
            HLine()
            KeyValue(stringResource(R.string.sys_from), stringResource(if (a.available) R.string.sys_from_github else R.string.sys_from_none))
        }
        Row(Modifier.at(20, 276, 256).drawTopLine(ice.line2).padding(top = 12.dp)) {
            Icon(SetIcons.shield, Color(0xFF8FF0CF), 18.dp)
            Spacer(Modifier.width(10.dp))
            IceText(stringResource(R.string.sys_privacy), ts(13f, display = false, lh = 1.35f), ice.ink2, maxLines = 3)
        }
        IceBtn(
            stringResource(if (state == UpdState.Checking) R.string.checking else R.string.sys_search), ::search,
            Modifier.align(Alignment.BottomStart).at(20, -18, 256), BtnKind.SEC, icon = SetIcons.refresh, enabled = a.available && state != UpdState.Checking,
        )
    }
    // La nueva (o "estás al día")
    val found = (state as? UpdState.Found)?.v ?: (a.progress as? Updater.Progress.Downloading)?.v ?: (a.progress as? Updater.Progress.Installing)?.v
    IceCard(Modifier.at(592, 100, 386, 392), radius = 18.dp, hi = found != null) {
        if (found == null) {
            Column(Modifier.at(20, 20, 346)) {
                CapLabel(stringResource(R.string.sys_this_version))
                IceText(
                    stringResource(if (state == UpdState.UpToDate) R.string.sys_up_to_date else R.string.sys_any_new), ts(27f, 300, lh = 1.1f), Color.White, modifier = Modifier.padding(top = 10.dp),
                )
                IceText(
                    when (state) {
                        UpdState.UpToDate -> stringResource(R.string.sys_up_to_date_sub)
                        UpdState.Error -> stringResource(R.string.update_error)
                        else -> stringResource(R.string.sys_no_news)
                    },
                    ts(14f, display = false, lh = 1.4f), if (state == UpdState.Error) Kc.coral else ice.ink2, maxLines = 3, modifier = Modifier.padding(top = 10.dp),
                )
            }
            // Las novedades de la versión que tienes.
            Column(Modifier.at(20, 140, 346)) {
                CapLabel(stringResource(R.string.sys_brings))
                for (line in releaseNotes().filterNot { it.startsWith("###") }.take(5)) NoteLine(line)
            }
            return@IceCard
        }
        Row(Modifier.at(20, 20), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.softGlow(Color(0xFF8FD0FF).copy(alpha = 0.6f), 4.dp).size(8.dp).clip(CircleShape).background(Color(0xFFBFE6FF)))
            Spacer(Modifier.width(8.dp))
            CapLabel(stringResource(R.string.sys_found), color = Color(0xFFBFE6FF))
        }
        IceText(stringResource(R.string.sys_ready, found.name), ts(27f, 300, lh = 1.1f), Color.White, modifier = Modifier.at(20, 44, 346))
        IceText(stringResource(R.string.sys_size, decimalText(found.bytes / 1e6, 1, locale)), ts(13f, display = false, lh = 1.35f), ice.ink2, modifier = Modifier.at(20, 80))
        Column(Modifier.at(20, 112, 350)) {
            CapLabel(stringResource(R.string.sys_brings), Modifier.padding(bottom = 8.dp))
            for (line in releaseNotes(found.notes).filterNot { it.startsWith("###") }.take(4)) NoteLine(line)
        }
        // .vf: la huella.
        Row(
            Modifier.at(20, 236, 346, 54).clip(RoundedCornerShape(14.dp)).background(Color(126, 240, 194).copy(alpha = 0.07f))
                .border(1.dp, Color(126, 240, 194).copy(alpha = 0.30f), RoundedCornerShape(14.dp)).padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(SetIcons.shield, Color(0xFF8FF0CF), 24.dp)
            Spacer(Modifier.width(12.dp))
            Column {
                IceText(stringResource(if (found.checksums != null) R.string.sys_verified else R.string.sys_verify_note), ts(14f, display = false, lh = 1.25f), ice.ink)
                IceText("SHA-256", ts(13f, display = false).copy(fontFamily = FontFamily.Monospace), ice.ink2, modifier = Modifier.padding(top = 3.dp))
            }
        }
        val p = a.progress
        val percent = when (p) {
            is Updater.Progress.Downloading -> p.percent
            is Updater.Progress.Installing -> 100
            else -> 0
        }
        val mb = found.bytes / 1e6
        Row(Modifier.at(20, 300, 346)) {
            IceText(stringResource(R.string.sys_downloaded), ts(13f, display = false), ice.ink2, modifier = Modifier.weight(1f))
            IceText(stringResource(R.string.sys_of, "${decimalText(mb * percent / 100, 1, locale)} MB", "${decimalText(mb, 1, locale)} MB"), ts(13f, display = false), ice.ink2)
        }
        Box(Modifier.at(20, 318, 346, 3).clip(RoundedCornerShape(2.dp)).background(Color(190, 225, 255).copy(alpha = 0.14f))) {
            Box(Modifier.fillMaxWidth(percent / 100f).height(3.dp).background(Brush.horizontalGradient(listOf(Color(0xFF7CC6FF), Color(0xFFE3F4FF)))))
        }
        Row(Modifier.align(Alignment.BottomStart).at(20, -14, 346), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            IceBtn(stringResource(R.string.sys_later), { a.dismiss(); state = UpdState.Idle }, kind = BtnKind.GHOST)
            IceBtn(
                if (p is Updater.Progress.Downloading) stringResource(R.string.downloading, p.percent) else stringResource(R.string.sys_install), {
                    if (!a.canInstall()) a.requestInstallPermission() else if (p == null || p == Updater.Progress.Failed) a.downloadAndInstall(found)
                    else if (p is Updater.Progress.Installing) a.openWithInstaller(p.apk)
                },
                Modifier.weight(1f), BtnKind.LIGHT, icon = SetIcons.download,
            )
        }
    }
}

@Composable
private fun NoteLine(text: String) {
    Row(Modifier.height(24.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(SetIcons.check, Kc.ice, 16.dp)
        Spacer(Modifier.width(10.dp))
        IceText(text, ts(14f, display = false, lh = 1.2f), LocalIce.current.ink)
    }
}

@Composable
private fun KeyValue(k: String, v: String) {
    Column(Modifier.padding(vertical = 10.dp)) {
        IceText(k, ts(13f, display = false, lh = 1.35f), LocalIce.current.ink3)
        IceText(v, ts(15f, display = false, lh = 1.25f), LocalIce.current.ink, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun lastCheckText(ms: Long): String {
    val locale = currentLocale()
    val time = timeText(ms, locale)
    val sameDay = SimpleDateFormat("yyyyMMdd", locale).let { it.format(Date(ms)) == it.format(Date()) }
    return if (sameDay) stringResource(R.string.sys_today_at, time) else dateText(ms, locale) + ", " + time
}

private fun Modifier.drawTopLine(color: Color): Modifier = this.drawBehind { drawRect(color, size = Size(size.width, 1.dp.toPx())) }

// ---------------------------------------------------------------- Acerca de

@Composable
private fun BoxScope.AboutC() {
    val ice = LocalIce.current
    val context = LocalContext.current
    IceCard(Modifier.at(284, 100, 694, 392), radius = 18.dp) {
        Column(Modifier.at(20, 20, 300)) {
            CapLabel("SailDash")
            IceText(BuildConfig.VERSION_NAME, ts(56f, 200, spacing = -0.01f), Color.White, modifier = Modifier.padding(top = 12.dp))
            IceText(BuildConfig.CHANNEL, ts(13f, display = false), ice.ink2, modifier = Modifier.padding(top = 8.dp))
            CapLabel(stringResource(R.string.sys_about_screen), Modifier.padding(top = 24.dp))
            for (line in remember { screenLines(context) }) IceText(line, ts(13f, display = false, lh = 1.4f), ice.ink2, maxLines = 2, modifier = Modifier.padding(top = 6.dp))
        }
        VLine(Modifier.at(340, 20, 1, 352))
        Column(Modifier.at(364, 20, 310, 352).verticalScroll(rememberScrollState())) {
            CapLabel(stringResource(R.string.sys_brings))
            Spacer(Modifier.height(8.dp))
            for (line in releaseNotes()) {
                if (line.startsWith("###")) IceText(line.removePrefix("###").trim(), ts(15f, 500), Color.White, modifier = Modifier.padding(top = 10.dp, bottom = 4.dp))
                else IceText("• $line", ts(13f, display = false, lh = 1.4f), ice.ink2, maxLines = 4, modifier = Modifier.padding(bottom = 4.dp))
            }
            CapLabel(stringResource(R.string.sys_about_android), Modifier.padding(top = 16.dp, bottom = 8.dp))
            val shortcuts = listOf(
                R.string.sys_all to android.provider.Settings.ACTION_SETTINGS,
                R.string.sys_wifi to android.provider.Settings.ACTION_WIFI_SETTINGS,
                R.string.sys_bluetooth to android.provider.Settings.ACTION_BLUETOOTH_SETTINGS,
                R.string.sys_display to android.provider.Settings.ACTION_DISPLAY_SETTINGS,
                R.string.sys_date to android.provider.Settings.ACTION_DATE_SETTINGS,
            )
            for (row in shortcuts.chunked(2)) Row(Modifier.padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for ((name, action) in row) IceBtn(stringResource(name), {
                    runCatching { context.startActivity(android.content.Intent(action).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) }
                }, Modifier.weight(1f), height = 40.dp, fontSize = 14f, padding = 10.dp)
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

// ================================================================ Mandos al volante (mandos-volante.html)

@Composable
fun WheelScreen(d: Dependencies, onSection: (SettingsSection) -> Unit, close: () -> Unit, onPanel: (Panel) -> Unit, extra: List<SettingsSection>) {
    val ice = LocalIce.current
    val prefs = d.prefs
    val keys = d.wheel
    var waiting by remember { mutableStateOf(false) }
    var learned by remember { mutableStateOf<Int?>(null) }
    var choosing by remember { mutableStateOf<Int?>(null) }
    var left by remember { mutableIntStateOf(WAIT_SECONDS) }
    DisposableEffect(waiting) {
        keys.learning = if (waiting) { code -> learned = code; choosing = code; waiting = false } else null
        onDispose { keys.learning = null }
    }
    LaunchedEffect(waiting) {
        left = WAIT_SECONDS
        while (waiting && left > 0) { delay(1_000); left-- }
        if (waiting) waiting = false
    }
    IceSettingsFrame(d, MenuStyle.SHEET, SettingsSection.WHEEL, onSection, close, onPanel, extra) {
        SheetHeader(stringResource(R.string.wheel)) {
            IceBtn(stringResource(R.string.wh_factory), { prefs.wheelKeys = emptyMap() }, icon = SetIcons.refresh, padding = 18.dp)
        }
        // Botones aprendidos
        IceCard(Modifier.at(284, 100, 432, 392), radius = 18.dp) {
            Column(Modifier.at(18, 14, 396)) {
                IceText(stringResource(R.string.wh_learned), ts(17f, 500, spacing = 0.01f, lh = 1.15f), Color.White)
                IceText(stringResource(R.string.wh_learned_sub), ts(13f, display = false, lh = 1.35f), ice.ink2, modifier = Modifier.padding(top = 5.dp))
            }
            val list = prefs.wheelKeys.entries.sortedBy { it.key }
            Column(Modifier.at(18, 72, 396, 306).verticalScroll(rememberScrollState())) {
                if (list.isEmpty()) {
                    HLine()
                    IceText(stringResource(R.string.wh_none), ts(14f, display = false, lh = 1.4f), ice.ink3, maxLines = 3, modifier = Modifier.padding(top = 14.dp))
                }
                for ((code, stored) in list) {
                    val action = WheelAction.fromStored(stored) ?: WheelAction.NORMAL
                    HLine()
                    Row(Modifier.height(60.dp), verticalAlignment = Alignment.CenterVertically) {
                        KeyCap(code)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            IceText(stringResource(R.string.wh_button, keyLabel(code).lowercase(currentLocale())), ts(15f, display = false, lh = 1.25f), ice.ink)
                            IceText(stringResource(R.string.wh_key, android.view.KeyEvent.keyCodeToString(code).removePrefix("KEYCODE_")), ts(13f, display = false, lh = 1.35f), ice.ink2)
                        }
                        // .act: la acción, se toca para cambiarla.
                        Row(
                            Modifier.height(44.dp).clip(RoundedCornerShape(22.dp)).background(Color(120, 190, 255).copy(alpha = 0.10f))
                                .border(1.dp, Color(170, 215, 255).copy(alpha = 0.28f), RoundedCornerShape(22.dp)).clickable { choosing = code; learned = code }
                                .padding(start = 12.dp, end = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(action.icon(), Kc.ice, 18.dp)
                            Spacer(Modifier.width(7.dp))
                            IceText(stringResource(action.title), ts(15f, display = false), Color.White)
                            Spacer(Modifier.width(7.dp))
                            Icon(SetIcons.chevronDown, ice.ink3, 18.dp)
                        }
                    }
                }
            }
        }
        // Aprender botón (o elegir qué hace el que se aprendió)
        IceCard(Modifier.at(728, 100, 250, 392), radius = 18.dp, hi = true) {
            val code = choosing
            if (code != null) {
                CapLabel(stringResource(R.string.wh_pick), Modifier.align(Alignment.TopCenter).offset(y = 20.dp))
                IceText(stringResource(R.string.wh_got, keyLabel(code).lowercase(currentLocale())), ts(17f, 500), Color.White, modifier = Modifier.align(Alignment.TopCenter).offset(y = 44.dp))
                val current = prefs.wheelKeys[code]?.let(WheelAction::fromStored) ?: WheelAction.NORMAL
                Column(Modifier.at(14, 76, 222, 300).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (a in WheelAction.entries) IceBtn(stringResource(a.title), {
                        prefs.wheelKeys = if (a == WheelAction.NORMAL) prefs.wheelKeys - code else prefs.wheelKeys + (code to a.stored)
                        choosing = null
                        learned = null
                    }, Modifier.fillMaxWidth(), if (a == current) BtnKind.BLUE else BtnKind.PLAIN, icon = a.icon(), height = 40.dp, fontSize = 14f, padding = 12.dp)
                }
                return@IceCard
            }
            CapLabel(stringResource(R.string.wh_learn_cap), Modifier.align(Alignment.TopCenter).offset(y = 20.dp))
            // .ring
            Box(Modifier.align(Alignment.TopCenter).offset(y = 52.dp).size(164.dp).clickable { waiting = !waiting }, contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) {
                    val c = center
                    drawCircle(Color(190, 228, 255).copy(alpha = 0.35f), 81.dp.toPx(), c, style = Stroke(1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx()))))
                    drawCircle(Color(150, 210, 255).copy(alpha = 0.10f), 70.dp.toPx(), c)
                    drawCircle(Color(190, 228, 255).copy(alpha = 0.45f), 62.dp.toPx(), c, style = Stroke(1.5f.dp.toPx()))
                    drawCircle(Brush.radialGradient(listOf(Color(210, 238, 255).copy(alpha = 0.35f), Color(120, 190, 255).copy(alpha = 0.12f)), Offset(c.x - 8.dp.toPx(), c.y - 10.dp.toPx()), 50.dp.toPx()), 44.dp.toPx(), c)
                    drawCircle(Color(210, 238, 255).copy(alpha = 0.6f), 44.dp.toPx(), c, style = Stroke(1.dp.toPx()))
                    if (waiting) {
                        val r = 72.dp.toPx()
                        drawArc(
                            Brush.sweepGradient(listOf(Color.Transparent, Color(0xFFD8F0FF))), -90f, 360f * left / WAIT_SECONDS, false,
                            Offset(c.x - r, c.y - r), Size(2 * r, 2 * r), style = Stroke(3.dp.toPx(), cap = StrokeCap.Round),
                        )
                    }
                }
                Icon(SetIcons.wheelB, Color.White, 44.dp, Modifier.softGlow(Color(150, 210, 255).copy(alpha = 0.3f), 8.dp))
            }
            Column(Modifier.at(16, 236, 218), horizontalAlignment = Alignment.CenterHorizontally) {
                IceText(stringResource(if (waiting) R.string.wh_press else R.string.wh_idle), ts(19f, lh = 1.25f, align = androidx.compose.ui.text.style.TextAlign.Center), Color.White, maxLines = 2)
                IceText(
                    if (waiting) stringResource(R.string.wh_waiting, left) else stringResource(R.string.wh_idle_sub),
                    ts(13f, display = false, lh = 1.35f, align = androidx.compose.ui.text.style.TextAlign.Center), ice.ink2, maxLines = 2, modifier = Modifier.padding(top = 8.dp),
                )
            }
            IceBtn(
                stringResource(if (waiting) R.string.cancel else R.string.wheel_learn), { waiting = !waiting },
                Modifier.align(Alignment.BottomStart).at(18, -18, 214), if (waiting) BtnKind.GHOST else BtnKind.LIGHT, height = 44.dp,
            )
        }
    }
}

private const val WAIT_SECONDS = 10

/** La tecla dibujada (`.key`): el ícono de lo que es, o su nombre corto (MODE). */
@Composable
private fun KeyCap(code: Int) {
    val icon = when (code) {
        android.view.KeyEvent.KEYCODE_MEDIA_NEXT -> IceIcons.next
        android.view.KeyEvent.KEYCODE_MEDIA_PREVIOUS -> IceIcons.previous
        android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, android.view.KeyEvent.KEYCODE_HEADSETHOOK -> IceIcons.play
        android.view.KeyEvent.KEYCODE_VOLUME_UP -> SetIcons.volUp
        android.view.KeyEvent.KEYCODE_VOLUME_DOWN -> SetIcons.volDown
        android.view.KeyEvent.KEYCODE_CALL, android.view.KeyEvent.KEYCODE_ENDCALL -> IceIcons.phone
        else -> null
    }
    Box(
        Modifier.size(44.dp).clip(RoundedCornerShape(13.dp))
            .background(Brush.linearGradient(listOf(Color(30, 44, 66).copy(alpha = 0.95f), Color(12, 20, 34).copy(alpha = 0.95f))))
            .border(1.dp, Color(190, 225, 255).copy(alpha = 0.22f), RoundedCornerShape(13.dp)),
        contentAlignment = Alignment.Center,
    ) {
        if (icon != null) Icon(icon, Color(0xFFEAF4FF), 20.dp)
        else IceText(android.view.KeyEvent.keyCodeToString(code).removePrefix("KEYCODE_").take(5), ts(13f, 600), Color(0xFFEAF4FF))
    }
}

@Composable
private fun keyLabel(code: Int): String = WheelKeys.name(code)?.let { stringResource(it) }
    ?: android.view.KeyEvent.keyCodeToString(code).removePrefix("KEYCODE_").replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() }

private fun WheelAction.icon(): ImageVector = when (this) {
    WheelAction.NEXT -> IceIcons.next
    WheelAction.PREVIOUS -> IceIcons.previous
    WheelAction.PLAY_PAUSE -> IceIcons.play
    WheelAction.VOLUME_UP -> SetIcons.volUp
    WheelAction.VOLUME_DOWN -> SetIcons.volDown
    WheelAction.MUTE -> SetIcons.soundMute
    WheelAction.NAVIGATE -> SetIcons.map
    WheelAction.MUSIC -> IceIcons.music
    WheelAction.VOICE -> SetIcons.mic
    WheelAction.HOME -> IceIcons.home
    WheelAction.NORMAL -> SetIcons.reset
    WheelAction.NOTHING -> SetIcons.close
}
