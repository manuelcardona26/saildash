package com.irvingguerra.litedash

import android.content.Context
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.Process
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/** Cómo quedó cada memoria: dónde, cuántos archivos y cuánto pesan, o el error. */
internal data class DiagExportResult(
    val place: String,
    val path: String,
    val removable: Boolean,
    val files: Int,
    val bytes: Long,
    val error: String?,
)

/**
 * "Guardar en la USB": el reporte, los eventos grabados y los APK de las apps clave en
 * "SailDash-diagnostico/<fecha>/" dentro de cada memoria que da getExternalFilesDirs: la interna y,
 * en casi todas las pantallas, la USB. Es la carpeta propia de SailDash: no pide permisos.
 */
internal object DiagnosticsExport {
    const val FOLDER = "SailDash-diagnostico"
    /** Un APK más grande que esto no se copia (las de fábrica pesan mucho menos). */
    private const val MAX_FILE = 60L * 1024 * 1024
    /** Tope por memoria, para no llenar la interna. */
    private const val MAX_TOTAL = 400L * 1024 * 1024
    /** Lo que se deja libre en cada memoria. */
    private const val SPARE = 20L * 1024 * 1024
    private const val BUFFER = 64 * 1024
    /** Carpetas de código por procesador junto al APK (Android 5 a 7: oat/arm/App.odex). */
    private val ISAS = setOf("arm", "arm64", "x86", "x86_64", "mips")

    private val main = Handler(Looper.getMainLooper())

    var working by mutableStateOf(false)
        private set

    /** Lo que está haciendo ahora ("Copiando Radio (2 de 5)…"). */
    var progress by mutableStateOf<String?>(null)
        private set

    var results by mutableStateOf<List<DiagExportResult>>(emptyList())
        private set

    /** Guarda en otro hilo (sigue aunque se salga de Ajustes). Si está grabando, primero detiene la grabación. */
    fun start(context: Context, report: DiagReport) {
        if (working) return
        DiagnosticsRecorder.stop()
        val events = DiagnosticsRecorder.all()
        val localized = contextWithLanguage(context.applicationContext)
        working = true
        progress = null
        results = emptyList()
        Thread({
            Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND)
            val out = runCatching { export(localized, report, events) { text -> main.post { progress = text } } }
                .getOrElse { listOf(DiagExportResult("?", "", false, 0, 0, it.message ?: it.javaClass.simpleName)) }
            main.post {
                results = out
                progress = null
                working = false
            }
        }, "saildash-diagnostico-usb").start()
    }

    private fun export(context: Context, report: DiagReport, events: List<DiagEvent>, say: (String) -> Unit): List<DiagExportResult> {
        val now = System.currentTimeMillis()
        val text = fileText(context, report, events, now)
        val settings = report.settingsDump.joinToString("\n", postfix = "\n")
        val keyApps = report.apps.filter { it.key }
        val results = ArrayList<DiagExportResult>()
        val dirs = context.getExternalFilesDirs(null).filterNotNull()
        if (dirs.isEmpty()) return listOf(DiagExportResult("?", "", false, 0, 0, context.getString(R.string.diagnostico_export_nowhere)))
        for (base in dirs) {
            val removable = runCatching { Environment.isExternalStorageRemovable(base) }.getOrDefault(false)
            val place = context.getString(if (removable) R.string.diagnostico_place_usb else R.string.diagnostico_place_internal)
            results += try {
                val folder = File(base, "$FOLDER/${DiagFormat.stamp(now)}")
                if (!folder.isDirectory && !folder.mkdirs()) throw IOException(folder.path)
                say(context.getString(R.string.diagnostico_export_writing, place))
                val copy = Copier(context, folder)
                copy.text("reporte.txt", text)
                copy.text("ajustes.txt", settings)
                for ((i, app) in keyApps.withIndex()) {
                    say(context.getString(R.string.diagnostico_export_copying, app.label, i + 1, keyApps.size))
                    val apk = File(app.sourceDir)
                    copy.file(apk, "apks/${app.pkg}.apk")
                    for (code in oatFiles(apk)) copy.file(code, "apks/${app.pkg}.${code.parentFile?.name}.${code.extension}")
                }
                // Para leer los recursos de las apps de sistema hace falta framework-res.apk; si su código
                // viene aparte (odex), también el de Android (boot*.oat).
                say(context.getString(R.string.diagnostico_export_framework))
                framework(copy, keyApps.any { it.codeApart })
                // El reporte otra vez, ahora con la lista de lo que se copió (por si se corta antes, ya había uno).
                copy.text("reporte.txt", text + DiagText.report(emptyList(), listOf(DiagSection(context.getString(R.string.diagnostico_copy_title), copy.log))), count = false)
                DiagExportResult(place, folder.absolutePath, removable, copy.files, copy.bytes, null)
            } catch (e: Exception) {
                DiagExportResult(place, base.absolutePath, removable, 0, 0, e.message ?: e.javaClass.simpleName)
            }
        }
        return results
    }

    /** El reporte completo en texto: resumen, cada parte, las apps con su detalle y los eventos grabados. */
    private fun fileText(context: Context, report: DiagReport, events: List<DiagEvent>, now: Long): String {
        val header = listOf(
            context.getString(R.string.diagnostico_file_title, DiagFormat.date(now)),
            "SailDash ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) · ${BuildConfig.CHANNEL} · ${context.packageName}",
        )
        val summary = DiagSection(context.getString(R.string.diagnostico_summary), report.summary.map { "• ${it.text}" })
        val apps = DiagSection(
            context.getString(R.string.diagnostico_sec_apps) + " (${report.apps.size})",
            report.apps.flatMap { app -> listOf((if (app.key) "★ " else "") + "${app.label} — ${app.pkg}") + app.details.map { "    $it" } },
        )
        val kinds = DiagEventKind.entries.associateWith { context.getString(it.label) }
        val recorded = DiagSection(
            context.getString(R.string.diagnostico_events_title) + " (${events.size})",
            events.map { DiagFormat.event(it.time, kinds.getValue(it.kind), it.text) },
        )
        val actions = DiagSection(context.getString(R.string.diagnostico_actions_title) + " (${report.actions.size})", report.actions)
        return DiagText.report(header, listOf(summary) + report.sections + apps + recorded + actions)
    }

    /** framework-res.apk (y los demás "-res.apk") y, si hace falta, el código de Android por procesador. */
    private fun framework(copy: Copier, code: Boolean) {
        val dir = File("/system/framework")
        dir.listFiles()?.filter { it.isFile && it.name.endsWith("-res.apk") }?.forEach { copy.file(it, "framework/${it.name}") }
        if (!code) return
        dir.listFiles()?.filter { it.isDirectory && it.name in ISAS }?.forEach { isa ->
            isa.listFiles()?.filter { it.isFile && it.name.startsWith("boot") && it.extension == "oat" }
                ?.forEach { copy.file(it, "framework/${isa.name}/${it.name}") }
        }
    }

    /** El código aparte de una app de sistema: oat/arm/App.odex (Android 7) o arm/App.odex (Android 5) junto al APK. */
    private fun oatFiles(apk: File): List<File> {
        val dir = apk.parentFile ?: return emptyList()
        val base = apk.nameWithoutExtension
        return listOf(File(dir, "oat"), dir)
            .flatMap { root -> root.listFiles()?.filter { it.isDirectory && it.name in ISAS }.orEmpty() }
            .flatMap { isa -> isa.listFiles()?.filter { it.isFile && it.nameWithoutExtension == base && it.extension in setOf("odex", "vdex") }.orEmpty() }
    }

    /** Copia a una carpeta con su tope y anota qué pasó con cada archivo. */
    private class Copier(private val context: Context, private val folder: File) {
        var files = 0
            private set
        var bytes = 0L
            private set
        val log = ArrayList<String>()

        fun text(name: String, content: String, count: Boolean = true) {
            val data = content.toByteArray(Charsets.UTF_8)
            write(File(folder, name)) { it.write(data) }
            if (count) {
                files++
                bytes += data.size
            }
        }

        fun file(src: File, relative: String) {
            val size = runCatching { src.length() }.getOrDefault(0L)
            when {
                !src.canRead() -> log += context.getString(R.string.diagnostico_copy_unreadable, relative)
                size > MAX_FILE -> log += context.getString(R.string.diagnostico_copy_big, relative, DiagFormat.size(size))
                bytes + size > MAX_TOTAL || folder.usableSpace < size + SPARE -> log += context.getString(R.string.diagnostico_copy_full, relative)
                else -> try {
                    val target = File(folder, relative)
                    target.parentFile?.mkdirs()
                    write(target) { out -> src.inputStream().use { it.copyTo(out, BUFFER) } }
                    files++
                    bytes += size
                    log += "$relative · ${DiagFormat.size(size)}"
                } catch (e: Exception) {
                    log += context.getString(R.string.diagnostico_copy_failed, relative, e.message ?: e.javaClass.simpleName)
                }
            }
        }

        /** Escribe y obliga a que llegue a la memoria: la USB se puede sacar apenas diga "Listo". */
        private fun write(file: File, body: (FileOutputStream) -> Unit) {
            FileOutputStream(file).use { out ->
                body(out)
                out.flush()
                runCatching { out.fd.sync() }
            }
        }
    }
}
