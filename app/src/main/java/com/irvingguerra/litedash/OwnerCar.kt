package com.irvingguerra.litedash

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.irvingguerra.litedash.core.ui.Button
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

/**
 * SailDash: la foto de "tu carro" (PNG con transparencia) para el inicio.
 *
 * Vive en el almacenamiento interno: files/car/front_floor.png (de frente, con su piso y reflejo),
 * front.json (dónde están los faros, opcional) y rear.png. En el primer arranque se copian desde los
 * assets "private/car/" **si existen**: esa carpeta está en .gitignore, así el APK que se compila en
 * el PC del dueño trae su Sail (un render de otra persona que no puede ir al repositorio público) y el
 * de GitHub no. Sin foto, el inicio usa el auto 3D del catálogo o el dibujo de LiteDash.
 *
 * Nada de esto corre en Application.onCreate: se hace la primera vez que el inicio pide la imagen,
 * fuera del hilo principal.
 */
class OwnerCar private constructor(private val context: Context) {
    private val dir get() = File(context.filesDir, DIR)
    private val front get() = File(dir, FRONT)
    private val frontJson get() = File(dir, FRONT_JSON)
    private val lock = Mutex()

    /** La foto de frente lista para dibujar; null = no hay (o todavía no se lee: ver [loaded]). */
    var image by mutableStateOf<OwnerCarImage?>(null)
        private set

    /** La foto de espaldas (files/car/rear.png) para la Carretera y el modo galaxia; null = no hay. */
    var rear by mutableStateOf<Bitmap?>(null)
        private set

    /** Ya se buscó la foto (con o sin resultado): hasta entonces el inicio no pinta el respaldo. */
    var loaded by mutableStateOf(false)
        private set

    /** Lee la foto (copiándola de los assets privados la primera vez). Llamarla varias veces no repite el trabajo. */
    suspend fun load() = lock.withLock {
        if (loaded) return@withLock
        image = withContext(Dispatchers.IO) {
            seedFromAssets()
            read()
        }
        rear = withContext(Dispatchers.IO) { readRear() }
        loaded = true
    }

    /** Cambia la foto de frente por la que eligió el usuario ([uri], PNG con transparencia). false = no se pudo leer. */
    suspend fun setFront(uri: Uri): Boolean = lock.withLock {
        val ok = withContext(Dispatchers.IO) {
            runCatching {
                dir.mkdirs()
                val tmp = File(dir, "$FRONT.tmp")
                context.contentResolver.openInputStream(uri)!!.use { input -> tmp.outputStream().use { input.copyTo(it) } }
                // Que sea una imagen de verdad antes de reemplazar la anterior.
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(tmp.path, bounds)
                require(bounds.outWidth > 0 && bounds.outHeight > 0)
                tmp.renameTo(front) || error("no se pudo guardar")
                // Los faros eran de la foto anterior.
                frontJson.delete()
            }.isSuccess
        }
        if (ok) image = withContext(Dispatchers.IO) { read() }
        loaded = true
        ok
    }

    /** Quita la foto (vuelve el auto 3D o el dibujo). */
    suspend fun clearFront() = lock.withLock {
        withContext(Dispatchers.IO) {
            front.delete()
            frontJson.delete()
        }
        image = null
        loaded = true
    }

    /** Hay foto de los assets privados para reponer (solo en el APK compilado con ellos). */
    fun hasPrivateAssets(): Boolean = runCatching { context.assets.list(ASSETS)?.contains(FRONT) == true }.getOrDefault(false)

    /** Vuelve a poner la foto de los assets privados (Ajustes → Tu carro → reponer). */
    suspend fun restoreFromAssets() = lock.withLock {
        image = withContext(Dispatchers.IO) {
            copyAssets(overwrite = true)
            read()
        }
        rear = withContext(Dispatchers.IO) { readRear() }
        loaded = true
    }

    /** La primera vez (sin marca de copiado) trae lo de assets/private/car, si el APK lo trae. */
    private fun seedFromAssets() {
        val marker = File(dir, SEEDED)
        if (marker.exists()) return
        copyAssets(overwrite = false)
        dir.mkdirs()
        marker.createNewFile()
    }

    private fun copyAssets(overwrite: Boolean) {
        val names = runCatching { context.assets.list(ASSETS)?.toList() }.getOrNull().orEmpty()
        if (names.isEmpty()) return
        dir.mkdirs()
        for (name in names) {
            val target = File(dir, name)
            if (target.exists() && !overwrite) continue
            runCatching { context.assets.open("$ASSETS/$name").use { input -> target.outputStream().use { input.copyTo(it) } } }
        }
    }

    private fun read(): OwnerCarImage? {
        val file = front.takeIf { it.exists() } ?: return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        if (bounds.outWidth <= 0) return null
        // En el inicio se ve a 543 dp de ancho: más de 2× es memoria de sobra (2 GB de RAM).
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= MAX_WIDTH) sample *= 2
        val bitmap = BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
        val lights = runCatching { headlights(frontJson.readText(), bounds.outWidth.toFloat()) }.getOrDefault(emptyList())
        return OwnerCarImage(bitmap, lights)
    }

    /** La de espaldas (sin piso), tal cual: es chica (unos 300 px de ancho). */
    private fun readRear(): Bitmap? {
        val file = File(dir, REAR).takeIf { it.exists() } ?: return null
        return runCatching { BitmapFactory.decodeFile(file.path) }.getOrNull()
    }

    companion object {
        private const val DIR = "car"
        private const val REAR = "rear.png"
        private const val FRONT = "front_floor.png"
        private const val FRONT_JSON = "front.json"
        private const val SEEDED = ".seeded"
        private const val ASSETS = "private/car"
        private const val MAX_WIDTH = 1100

        @SuppressLint("StaticFieldLeak") // es el contexto de la aplicación
        @Volatile private var instance: OwnerCar? = null

        fun of(context: Context): OwnerCar = instance ?: synchronized(this) {
            instance ?: OwnerCar(context.applicationContext).also { instance = it }
        }

        /**
         * Los faros de front.json (el de car/out del diseño): los de la imagen con piso
         * (`floor.headlights`), en fracción del ancho de la foto ([width] px) para no depender del tamaño.
         */
        fun headlights(json: String, width: Float): List<Headlight> {
            val root = JSONObject(json)
            val floor = root.optJSONObject("floor")
            val list = floor?.optJSONArray("headlights") ?: return emptyList()
            val w = floor.optJSONArray("size")?.optDouble(0)?.toFloat()?.takeIf { it > 0 } ?: width
            return (0 until list.length()).map { i ->
                val h = list.getJSONObject(i)
                Headlight(
                    x = h.getDouble("x").toFloat() / w,
                    y = h.getDouble("y").toFloat() / w,
                    glow = h.optDouble("r_glow", 40.0).toFloat() / w,
                )
            }
        }
    }
}

/** La foto del carro y sus faros. */
class OwnerCarImage(val bitmap: Bitmap, val headlights: List<Headlight>) {
    /** Alto ÷ ancho de la foto. */
    val ratio get() = bitmap.height.toFloat() / bitmap.width
}

/** Un faro: centro y radio del brillo, todo en fracción del ANCHO de la foto. */
data class Headlight(val x: Float, val y: Float, val glow: Float)

/**
 * Ajustes → Tu carro → Mi carro: la foto de tu carro para el Inicio (PNG con fondo transparente, de
 * frente). Elegir otra, quitarla (vuelve el auto 3D o el dibujo) o reponer la que trae el APK.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun OwnerCarSettings() {
    val context = LocalContext.current
    val owner = remember { OwnerCar.of(context) }
    LaunchedEffect(owner) { owner.load() }
    val scope = rememberCoroutineScope()
    val failed = stringResource(R.string.owner_car_error)
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) scope.launch {
            if (!owner.setFront(uri)) Toast.makeText(context, failed, Toast.LENGTH_SHORT).show()
        }
    }
    SettingsCard(stringResource(R.string.owner_car_title), stringResource(R.string.owner_car_help)) {
        owner.image?.let { img ->
            Image(img.bitmap.asImageBitmap(), null, Modifier.fillMaxWidth().height(160.dp), contentScale = ContentScale.Fit)
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(stringResource(R.string.owner_car_pick), { pick.launch("image/*") }, primary = true)
            if (owner.image != null) Button(stringResource(R.string.owner_car_remove), { scope.launch { owner.clearFront() } })
            if (owner.hasPrivateAssets()) Button(stringResource(R.string.owner_car_restore), { scope.launch { owner.restoreFromAssets() } })
        }
    }
}
