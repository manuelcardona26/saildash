package com.irvingguerra.litedash

import android.content.Context
import android.content.Intent
import android.location.Geocoder
import android.location.Location
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleStartEffect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.Date
import java.util.Locale

/** Wi-Fi conectado, audio Bluetooth conectado y el nombre de ese aparato (si Android lo dice). */
@Immutable
data class Links(val wifi: Boolean, val bluetooth: Boolean, val bluetoothName: String?)

/**
 * El estado real de Wi-Fi y Bluetooth para los mosaicos del inicio. Escucha al sistema solo mientras
 * se ve (nada de revisar cada rato) y sin permisos: el Bluetooth se nota por el audio conectado (tu
 * celular), y su nombre es el del aparato de audio.
 */
@Composable
fun rememberLinks(): Links {
    val context = LocalContext.current
    var links by remember { mutableStateOf(Links(wifi = false, bluetooth = false, bluetoothName = null)) }
    LifecycleStartEffect(Unit) {
        val connectivity = context.getSystemService(ConnectivityManager::class.java)
        val audio = context.getSystemService(AudioManager::class.java)
        fun wifiNow(lost: Network? = null): Boolean = runCatching {
            // Android 6 y 7 no avisan cómo está al registrarse: siempre se lee la red activa.
            connectivity.activeNetwork?.takeIf { it != lost }?.let(connectivity::getNetworkCapabilities)
                ?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        }.getOrDefault(false)
        fun btNow(): AudioDeviceInfo? = audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS).firstOrNull {
            it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP || it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO
        }
        fun update(lost: Network? = null) {
            val bt = btNow()
            links = Links(wifiNow(lost), bt != null, bt?.productName?.toString()?.takeIf { it.isNotBlank() })
        }
        val netCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(n: Network) = update()
            override fun onCapabilitiesChanged(n: Network, caps: NetworkCapabilities) = update()
            override fun onLost(n: Network) = update(lost = n)
        }
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) connectivity.registerDefaultNetworkCallback(netCallback)
            else connectivity.registerNetworkCallback(NetworkRequest.Builder().addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET).build(), netCallback)
        }
        val audioCallback = object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(added: Array<out AudioDeviceInfo>) = update()
            override fun onAudioDevicesRemoved(removed: Array<out AudioDeviceInfo>) = update()
        }
        audio.registerAudioDeviceCallback(audioCallback, null)
        update()
        onStopOrDispose {
            runCatching { connectivity.unregisterNetworkCallback(netCallback) }
            audio.unregisterAudioDeviceCallback(audioCallback)
        }
    }
    return links
}

/** Abre una pantalla de ajustes de Android ([action]); si la pantalla no la trae, los ajustes generales. */
fun openSystemSettings(context: Context, action: String) {
    val flags = Intent.FLAG_ACTIVITY_NEW_TASK
    runCatching { context.startActivity(Intent(action).addFlags(flags)) }
        .onFailure { runCatching { context.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(flags)) } }
}

/**
 * El nombre del lugar donde está el carro (barrio o comuna; si no, la ciudad) para el inicio. Se lee
 * con el Geocoder de Android (si la pantalla lo trae) a partir de la posición del GPS que ya tiene el
 * viaje ([TripRepository.position]); se pregunta de nuevo solo al moverse más de [PLACE_EVERY_M] y se
 * guarda para verlo al instante la próxima vez. Sin Geocoder ni posición, el último que se vio (o null).
 */
@Composable
fun rememberPlaceName(d: Dependencies): String? {
    val context = LocalContext.current
    val position = d.trip.position
    var asked by remember { mutableStateOf<Location?>(null) }
    LaunchedEffect(position) {
        val here = position ?: return@LaunchedEffect
        val last = asked
        if (last != null && last.distanceTo(here) < PLACE_EVERY_M) return@LaunchedEffect
        asked = here
        val name = withContext(Dispatchers.IO) { placeOf(context, here) } ?: return@LaunchedEffect
        if (name != d.prefs.placeName) d.prefs.placeName = name
    }
    return d.prefs.placeName
}

@Suppress("DEPRECATION") // la versión con callback es de Android 13; aquí ya va fuera del hilo principal
private fun placeOf(context: Context, at: Location): String? = runCatching {
    if (!Geocoder.isPresent()) return null
    val a = Geocoder(context, Locale.getDefault()).getFromLocation(at.latitude, at.longitude, 1)?.firstOrNull() ?: return null
    (a.subLocality ?: a.locality ?: a.subAdminArea)?.trim()?.takeIf { it.isNotEmpty() }
}.getOrNull()

private const val PLACE_EVERY_M = 400f

/**
 * La hora de ahora, al minuto: un repintado por minuto (y al volver la app al frente, porque con la
 * pantalla dormida la espera no avanza).
 */
@Composable
fun rememberMinute(): Date {
    val starts = rememberStarts()
    val now by produceState(Date(), starts) {
        value = Date()
        while (true) {
            delay(60_000 - System.currentTimeMillis() % 60_000)
            value = Date()
        }
    }
    return now
}
