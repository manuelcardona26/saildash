// SailDash: Ajustes → Sistema → Diagnóstico. Para descubrir, sin PC, cómo maneja tu pantalla la
// radio, el ecualizador, la reversa, las luces y el encendido.
package com.irvingguerra.litedash

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.Process
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.irvingguerra.litedash.core.ui.Button
import com.irvingguerra.litedash.core.ui.Icon
import com.irvingguerra.litedash.core.ui.Icons
import com.irvingguerra.litedash.core.ui.Label
import com.irvingguerra.litedash.core.ui.LocalColors
import com.irvingguerra.litedash.core.ui.Radius
import com.irvingguerra.litedash.core.ui.Typography
import kotlinx.coroutines.delay

/** Cuántos eventos se ven en pantalla (todos van al archivo). */
private const val SHOWN_EVENTS = 60

/**
 * El reporte de la pantalla: se arma la primera vez que se abre la pestaña (en otro hilo, con
 * prioridad baja para no trabar nada) y queda en memoria mientras SailDash siga abierto.
 */
internal object Diagnostics {
    private val main = Handler(Looper.getMainLooper())

    var report by mutableStateOf<DiagReport?>(null)
        private set
    var checking by mutableStateOf(false)
        private set

    /** Apps revisadas (hechas, total); null mientras va con lo demás. */
    var progress by mutableStateOf<Pair<Int, Int>?>(null)
        private set
    var failed by mutableStateOf<String?>(null)
        private set

    /** Revisa la pantalla; [again] = aunque ya haya un reporte. Desde el hilo principal. */
    fun check(context: Context, again: Boolean = false) {
        if (checking || (report != null && !again)) return
        val localized = contextWithLanguage(context.applicationContext)
        val multiWindow = context.diagActivity()?.let { if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) it.isInMultiWindowMode else null }
        checking = true
        progress = null
        failed = null
        Thread({
            Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND)
            val result = runCatching {
                DiagnosticsCollector.collect(localized, multiWindow) { done, total -> main.post { progress = done to total } }
            }
            main.post {
                result.onSuccess { report = it }.onFailure { failed = it.message ?: it.javaClass.simpleName }
                checking = false
                progress = null
            }
        }, "saildash-diagnostico").start()
    }
}

/** La actividad detrás de un contexto de Compose (para saber si está en pantalla dividida). */
private fun Context.diagActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext?.diagActivity()
    else -> null
}

/**
 * Ajustes → Sistema → Diagnóstico: lo más importante, grabar eventos, guardar en la USB y el
 * detalle. Al entrar se revisa la pantalla una vez; lo demás solo corre si se toca.
 */
@Composable
internal fun DiagnosticsSettings() {
    val context = LocalContext.current
    LaunchedEffect(Unit) { Diagnostics.check(context) }
    val report = Diagnostics.report
    SummaryCard(report)
    RecorderCard(report)
    ExportCard(report)
    if (report == null) return
    // El detalle técnico es largo (y en la T3L dibujarlo cuesta): solo si se pide.
    var details by remember { mutableStateOf(false) }
    Button(
        stringResource(if (details) R.string.diagnostico_details_hide else R.string.diagnostico_details_show),
        { details = !details },
    )
    if (details) {
        for (section in report.sections) SectionCard(section)
        AppsCard(report)
    }
}

/** Lo más importante: Android real, pantalla dividida, ecualizador y apps de fábrica. */
@Composable
private fun SummaryCard(report: DiagReport?) {
    val c = LocalColors.current
    val context = LocalContext.current
    SettingsCard(stringResource(R.string.diagnostico_title), stringResource(R.string.diagnostico_intro)) {
        val checking = Diagnostics.checking
        if (checking) {
            val p = Diagnostics.progress
            Label(
                if (p == null) stringResource(R.string.diagnostico_checking) else stringResource(R.string.diagnostico_checking_apps, p.first, p.second),
                Typography.body,
                c.accent,
            )
        } else if (report != null) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                for (v in report.summary) VerdictRow(v)
            }
        }
        Diagnostics.failed?.let { Label(stringResource(R.string.diagnostico_check_failed, it), Typography.small, c.danger) }
        if (!checking) Button(stringResource(R.string.diagnostico_recheck), { Diagnostics.check(context, again = true) })
    }
}

@Composable
private fun VerdictRow(v: DiagVerdict) {
    val c = LocalColors.current
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(
            when (v.good) {
                true -> Icons.done
                false -> Icons.warning
                null -> Icons.info
            },
            when (v.good) {
                true -> c.accent
                false -> c.warning
                null -> c.textMuted
            },
            22.dp,
        )
        Label(v.text, Typography.body, modifier = Modifier.weight(1f))
    }
}

/** Grabar eventos: Grabar o Detener, lo que falta y lo que va llegando (lo más nuevo arriba). */
@Composable
private fun RecorderCard(report: DiagReport?) {
    val c = LocalColors.current
    val context = LocalContext.current
    val recorder = DiagnosticsRecorder
    SettingsCard(stringResource(R.string.diagnostico_rec_title), stringResource(R.string.diagnostico_rec_help)) {
        Label(stringResource(R.string.diagnostico_rec_steps), Typography.body)
        val version = recorder.version
        val total = remember(version) { recorder.total }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            if (recorder.recording) {
                Button(stringResource(R.string.diagnostico_rec_stop), { recorder.stop() }, primary = true)
                var left by remember { mutableLongStateOf(recorder.remainingMs()) }
                // Solo mientras graba y se ve: un número por segundo.
                LaunchedEffect(Unit) {
                    while (true) {
                        left = recorder.remainingMs()
                        delay(1_000)
                    }
                }
                Label(stringResource(R.string.diagnostico_rec_running, DiagFormat.mmss(left)), Typography.body, c.accent, maxLines = 1)
            } else {
                Button(stringResource(R.string.diagnostico_rec_start), { recorder.start(context, report?.actions.orEmpty()) }, primary = true)
            }
            if (total > 0) Label(stringResource(R.string.diagnostico_rec_count, total), Typography.small, c.textMuted, maxLines = 1)
        }
        val shown = remember(version) { recorder.newest(SHOWN_EVENTS) }
        if (shown.isEmpty()) {
            Label(stringResource(R.string.diagnostico_rec_empty), Typography.small, c.textMuted)
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                // Con su id: al llegar uno nuevo, los demás no se vuelven a medir.
                for (e in shown) key(e.id) { EventRow(e) }
                if (total > SHOWN_EVENTS) Label(stringResource(R.string.diagnostico_rec_shown, SHOWN_EVENTS), Typography.small, c.textMuted)
            }
        }
    }
}

@Composable
private fun EventRow(e: DiagEvent) {
    val c = LocalColors.current
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Label(e.clock, Typography.small, c.textMuted, maxLines = 1)
        Label(stringResource(e.kind.label), Typography.small, if (e.kind == DiagEventKind.INFO) c.textMuted else c.accent, maxLines = 1)
        Label(e.text, Typography.small, maxLines = 4, modifier = Modifier.weight(1f))
    }
}

/** Guardar en la USB: el botón, lo que va haciendo y, al final, dónde quedó cada cosa. */
@Composable
private fun ExportCard(report: DiagReport?) {
    val c = LocalColors.current
    val context = LocalContext.current
    val export = DiagnosticsExport
    SettingsCard(stringResource(R.string.diagnostico_export_title), stringResource(R.string.diagnostico_export_help)) {
        val ready = report != null && !export.working && !Diagnostics.checking
        Button(
            stringResource(if (export.working) R.string.diagnostico_export_working else R.string.diagnostico_export_button),
            { if (report != null && !export.working && !Diagnostics.checking) export.start(context, report) },
            Modifier.graphicsLayer { alpha = if (ready) 1f else 0.45f },
            primary = true,
        )
        if (report == null) Label(stringResource(R.string.diagnostico_export_wait), Typography.small, c.textMuted)
        export.progress?.let { Label(it, Typography.body, c.accent) }
        val results = export.results
        if (results.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                for (r in results) {
                    if (r.error != null) {
                        Label(stringResource(R.string.diagnostico_export_failed, r.place, r.error), Typography.small, c.danger)
                    } else {
                        Label(stringResource(R.string.diagnostico_export_ok, r.place, r.files, DiagFormat.size(r.bytes)), Typography.body)
                        Label(r.path, Typography.small, c.textMuted)
                    }
                }
            }
            if (results.any { it.error == null }) Label(stringResource(R.string.diagnostico_export_done, context.packageName), Typography.body, c.accent)
            if (results.none { it.removable && it.error == null }) Label(stringResource(R.string.diagnostico_export_no_usb), Typography.small, c.warning)
        }
    }
}

/** Una parte del detalle (equipo, propiedades, memoria…): renglones técnicos. */
@Composable
private fun SectionCard(section: DiagSection) {
    SettingsCard(section.title) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            for (line in section.lines) Label(line, Typography.small)
        }
    }
}

/** Las apps de fábrica: una por renglón (las clave resaltadas); al tocar una se ve su detalle. */
@Composable
private fun AppsCard(report: DiagReport) {
    val c = LocalColors.current
    val open = remember { mutableStateMapOf<String, Boolean>() }
    SettingsCard(stringResource(R.string.diagnostico_sec_apps) + " (${report.apps.size})", stringResource(R.string.diagnostico_apps_help)) {
        if (report.apps.isEmpty()) Label(stringResource(R.string.diagnostico_apps_none), Typography.small, c.textMuted)
        Column {
            for (app in report.apps) key(app.pkg) {
                val expanded = open[app.pkg] == true
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(Radius.inner))
                        .clickable { open[app.pkg] = !expanded }
                        .padding(horizontal = 8.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(if (expanded) Icons.minus else Icons.plus, if (app.key) c.accent else c.textMuted, 18.dp)
                        Label(
                            app.label,
                            Typography.body.copy(fontWeight = if (app.key) FontWeight.SemiBold else FontWeight.Normal),
                            if (app.key) c.accent else c.text,
                            maxLines = 1,
                        )
                        Label(app.pkg, Typography.small, c.textMuted, maxLines = 1, modifier = Modifier.weight(1f, fill = false))
                    }
                    if (expanded) for (line in app.details) Label(line, Typography.small, c.textMuted)
                }
            }
        }
    }
}
