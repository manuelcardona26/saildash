// SailDash: ¿hay una cámara que Android pueda abrir? (Diagnóstico) y su imagen en vivo (vista de reversa).
// Nunca toca la cámara de reversa de fábrica: esa la dibuja el chip del carro, sin pasar por Android.
package com.irvingguerra.litedash

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.util.Size
import android.view.Surface
import android.view.TextureView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.edit
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Una cámara que Android ve: si se pudo abrir y el tamaño más grande que da para mostrar en pantalla. */
data class CameraProbe(val id: String, val facing: String, val opened: Boolean, val width: Int, val height: Int, val error: String?) {
    val resolution get() = if (width > 0) "${width}×$height" else "—"
}

/**
 * Revisa las cámaras: las lista, abre cada una un momento (y la cierra) y anota su resolución. Lo
 * último que se encontró se guarda: la Reversa sabe sin abrir nada si hay una cámara que sirva.
 */
object CameraCheck {
    var results by mutableStateOf<List<CameraProbe>?>(null)
        private set
    var checking by mutableStateOf(false)
        private set

    /** La primera que se pudo abrir (la de atrás primero), o null. */
    fun usable(context: Context): CameraProbe? = (results ?: loadSaved(context))?.filter { it.opened }?.sortedBy { if (it.facing == "back") 0 else 1 }?.firstOrNull()

    fun hasPermission(context: Context) = context.checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

    /** Desde el hilo principal; corre en otro hilo y cada cámara tiene a lo más 3 s para abrir. */
    fun check(context: Context) {
        if (checking) return
        val app = context.applicationContext
        checking = true
        val main = Handler(Looper.getMainLooper())
        Thread({
            val found = runCatching { probeAll(app) }.getOrElse { listOf(CameraProbe("-", "?", false, 0, 0, it.message ?: it.javaClass.simpleName)) }
            save(app, found)
            main.post {
                results = found
                checking = false
            }
        }, "saildash-camaras").start()
    }

    @SuppressLint("MissingPermission") // se revisa antes de abrir
    private fun probeAll(context: Context): List<CameraProbe> {
        val manager = context.getSystemService(CameraManager::class.java) ?: return emptyList()
        val thread = HandlerThread("saildash-camara").apply { start() }
        val handler = Handler(thread.looper)
        try {
            return manager.cameraIdList.map { id ->
                val ch = runCatching { manager.getCameraCharacteristics(id) }.getOrNull()
                val facing = when (ch?.get(CameraCharacteristics.LENS_FACING)) {
                    CameraCharacteristics.LENS_FACING_BACK -> "back"
                    CameraCharacteristics.LENS_FACING_FRONT -> "front"
                    else -> "external"
                }
                val size = ch?.let(::biggestPreview)
                if (!hasPermission(context)) return@map CameraProbe(id, facing, false, size?.width ?: 0, size?.height ?: 0, "permission")
                val latch = CountDownLatch(1)
                var opened = false
                var error: String? = null
                var device: CameraDevice? = null
                runCatching {
                    manager.openCamera(id, object : CameraDevice.StateCallback() {
                        override fun onOpened(camera: CameraDevice) {
                            opened = true
                            device = camera
                            latch.countDown()
                        }
                        override fun onDisconnected(camera: CameraDevice) {
                            camera.close()
                            latch.countDown()
                        }
                        override fun onError(camera: CameraDevice, e: Int) {
                            error = "error $e"
                            camera.close()
                            latch.countDown()
                        }
                    }, handler)
                }.onFailure { error = it.message ?: it.javaClass.simpleName; latch.countDown() }
                if (!latch.await(OPEN_TIMEOUT_MS, TimeUnit.MILLISECONDS)) error = "timeout"
                runCatching { device?.close() }
                CameraProbe(id, facing, opened, size?.width ?: 0, size?.height ?: 0, error)
            }
        } finally {
            thread.quitSafely()
        }
    }

    private fun biggestPreview(ch: CameraCharacteristics): Size? =
        ch.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)?.getOutputSizes(SurfaceTexture::class.java)?.maxByOrNull { it.width.toLong() * it.height }

    private fun save(context: Context, list: List<CameraProbe>) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
        putString(KEY, list.joinToString(";") { "${it.id},${it.facing},${if (it.opened) 1 else 0},${it.width},${it.height}" })
    }

    private fun loadSaved(context: Context): List<CameraProbe>? = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)?.let { text ->
        text.split(';').filter { it.isNotBlank() }.mapNotNull { row ->
            val f = row.split(',')
            runCatching { CameraProbe(f[0], f[1], f[2] == "1", f[3].toInt(), f[4].toInt(), null) }.getOrNull()
        }
    }

    /** El tamaño para mostrar: el más cercano a 1280 de ancho sin pasarse (la T3L no da para más). */
    fun previewSize(context: Context, id: String): Size? {
        val manager = context.getSystemService(CameraManager::class.java) ?: return null
        val sizes = runCatching { manager.getCameraCharacteristics(id).get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)?.getOutputSizes(SurfaceTexture::class.java) }.getOrNull() ?: return null
        return sizes.filter { it.width <= 1280 }.maxByOrNull { it.width.toLong() * it.height } ?: sizes.minByOrNull { it.width.toLong() * it.height }
    }

    const val PREFS = "reverse_view"
    private const val KEY = "cameras"
    private const val OPEN_TIMEOUT_MS = 3_000L
}

/**
 * La imagen en vivo de la cámara [cameraId] en un TextureView, llenando su caja sin deformarse
 * ([mirrored] = en espejo). Se abre al aparecer y se cierra al irse: no queda nada prendido.
 */
@SuppressLint("MissingPermission")
@Composable
fun CameraPreview(cameraId: String, mirrored: Boolean, modifier: Modifier = Modifier, onError: (String) -> Unit = {}) {
    val context = LocalContext.current
    val session = remember(cameraId) { PreviewSession(context.applicationContext, cameraId, onError) }
    DisposableEffect(session) { onDispose { session.close() } }
    AndroidView(
        factory = { ctx -> TextureView(ctx).also { session.attach(it) } },
        modifier = modifier,
        update = { view -> session.mirrored = mirrored; session.fit(view) },
    )
}

/** La cámara abierta para mostrarla: abrir, mostrar y cerrar en su propio hilo. */
private class PreviewSession(private val context: Context, private val id: String, private val onError: (String) -> Unit) {
    private val thread = HandlerThread("saildash-reversa").apply { start() }
    private val handler = Handler(thread.looper)
    private val main = Handler(Looper.getMainLooper())
    private var device: CameraDevice? = null
    private var capture: CameraCaptureSession? = null
    private var size: Size? = null
    private var view: TextureView? = null
    private var closed = false
    var mirrored = false

    fun attach(v: TextureView) {
        view = v
        v.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(st: SurfaceTexture, w: Int, h: Int) = open(st)
            override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, w: Int, h: Int) = fit(v)
            override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean { close(); return true }
            override fun onSurfaceTextureUpdated(st: SurfaceTexture) = Unit
        }
        if (v.isAvailable) v.surfaceTexture?.let(::open)
    }

    @SuppressLint("MissingPermission")
    private fun open(st: SurfaceTexture) {
        if (closed || device != null) return
        val s = CameraCheck.previewSize(context, id) ?: return onError("size")
        size = s
        st.setDefaultBufferSize(s.width, s.height)
        view?.let(::fit)
        val manager = context.getSystemService(CameraManager::class.java) ?: return onError("manager")
        runCatching {
            manager.openCamera(id, object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    if (closed) return camera.close()
                    device = camera
                    val surface = Surface(st)
                    @Suppress("DEPRECATION")
                    camera.createCaptureSession(listOf(surface), object : CameraCaptureSession.StateCallback() {
                        override fun onConfigured(s: CameraCaptureSession) {
                            if (closed) return s.close()
                            capture = s
                            runCatching {
                                val req = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply { addTarget(surface) }.build()
                                s.setRepeatingRequest(req, null, handler)
                            }.onFailure { e -> main.post { onError(e.message ?: "preview") } }
                        }
                        override fun onConfigureFailed(s: CameraCaptureSession) { main.post { onError("configure") } }
                    }, handler)
                }
                override fun onDisconnected(camera: CameraDevice) = camera.close()
                override fun onError(camera: CameraDevice, error: Int) {
                    camera.close()
                    main.post { onError("error $error") }
                }
            }, handler)
        }.onFailure { e -> onError(e.message ?: "open") }
    }

    /** Llena la caja recortando lo que sobra (como `object-fit: cover`) y voltea si va en espejo. */
    fun fit(v: TextureView) {
        val s = size ?: return
        val vw = v.width.toFloat()
        val vh = v.height.toFloat()
        if (vw <= 0f || vh <= 0f) return
        // El contenido ya viene derecho para la orientación natural de la pantalla.
        val ca = s.width.toFloat() / s.height
        val va = vw / vh
        var sx = if (ca > va) ca / va else 1f
        val sy = if (ca > va) 1f else va / ca
        if (mirrored) sx = -sx
        v.setTransform(Matrix().apply { setScale(sx, sy, vw / 2f, vh / 2f) })
    }

    fun close() {
        if (closed) return
        closed = true
        handler.post {
            runCatching { capture?.close() }
            runCatching { device?.close() }
            capture = null
            device = null
            thread.quitSafely()
        }
    }
}

/** Lo guardado de la vista de reversa (las líneas, por pantalla y cámara). */
object ReverseViewStore {
    fun load(context: Context, key: String): ReverseGuides =
        ReverseGuides.read(context.getSharedPreferences(CameraCheck.PREFS, Context.MODE_PRIVATE).getString(key, null)) ?: ReverseGuides()

    fun save(context: Context, key: String, g: ReverseGuides) = context.getSharedPreferences(CameraCheck.PREFS, Context.MODE_PRIVATE).edit { putString(key, g.write()) }
}
