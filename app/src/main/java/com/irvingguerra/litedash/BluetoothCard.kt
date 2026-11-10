// La tarjeta rápida de Bluetooth (catalogo/bluetooth): el celular conectado, reconectarlo, lo que
// suena por Bluetooth, los otros teléfonos guardados y emparejar uno nuevo (en los ajustes de Android).
package com.irvingguerra.litedash

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.irvingguerra.litedash.core.ui.Icon
import com.irvingguerra.litedash.core.ui.ice.IceButton
import com.irvingguerra.litedash.core.ui.ice.IceDot
import com.irvingguerra.litedash.core.ui.ice.IceIconButton
import com.irvingguerra.litedash.core.ui.ice.IceIcons
import com.irvingguerra.litedash.core.ui.ice.IceText
import com.irvingguerra.litedash.core.ui.ice.IceText2
import com.irvingguerra.litedash.core.ui.ice.IceToggle
import com.irvingguerra.litedash.core.ui.ice.IceType
import com.irvingguerra.litedash.core.ui.ice.IceWell
import com.irvingguerra.litedash.core.ui.ice.LocalIce
import com.irvingguerra.litedash.core.ui.ice.glass
import com.irvingguerra.litedash.core.ui.ice.iceCard
import com.irvingguerra.litedash.core.ui.ice.iceSelected
import com.irvingguerra.litedash.core.ui.ice.softGlow
import com.irvingguerra.litedash.feature.music.SourceKind
import com.irvingguerra.litedash.feature.music.rememberPosition

/** La tarjeta encima del inicio oscurecido (left 400, top 16, 600 × 492). Tocar afuera la cierra. */
@Composable
internal fun BluetoothCard(d: Dependencies, close: () -> Unit) {
    val ice = LocalIce.current
    val context = LocalContext.current
    val repo = d.music
    DisposableEffect(repo) {
        repo.use()
        onDispose { repo.release() }
    }
    val adapter = remember { context.getSystemService(BluetoothManager::class.java)?.adapter }
    // Se relee al cambiar el estado del Bluetooth o al conectarse un aparato.
    var tick by remember { mutableIntStateOf(0) }
    DisposableEffect(adapter) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) { tick++ }
        }
        val filter = IntentFilter().apply {
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
            addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
        }
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_EXPORTED)
        onDispose { runCatching { context.unregisterReceiver(receiver) } }
    }
    val links = rememberLinks()
    val on = remember(tick) { runCatching { adapter?.isEnabled == true }.getOrDefault(false) }
    val phone = repo.bluetoothDevice ?: links.bluetoothName?.takeIf { links.bluetooth }
    val connected = links.bluetooth || repo.bluetoothDevice != null
    val bonded = remember(tick, on) { bondedDevices(adapter) }
    val others = bonded.filter { name(it) != phone }.take(2)

    Box(
        Modifier
            .size(1024.dp, 528.dp)
            // .dim: oscurece el inicio de atrás (más hacia la derecha).
            .background(Brush.horizontalGradient(0f to Color(3, 6, 12).copy(alpha = 0.70f), 0.45f to Color(3, 6, 12).copy(alpha = 0.82f)))
            .clickable(remember { MutableInteractionSource() }, indication = null, onClick = close),
    ) {
        Column(
            Modifier
                .offset(400.dp, 16.dp)
                .size(600.dp, 492.dp)
                .glass(
                    listOf(0f to Color(32, 52, 82).copy(alpha = 0.97f), 0.6f to Color(14, 24, 42).copy(alpha = 0.98f), 1f to Color(10, 18, 32).copy(alpha = 0.98f)),
                    Color(190, 225, 255).copy(alpha = 0.2f), Color(235, 246, 255).copy(alpha = 0.12f), 26.dp, angle = 165f,
                )
                .clickable(remember { MutableInteractionSource() }, indication = null) {}
                .padding(horizontal = 22.dp, vertical = 20.dp),
        ) {
            // .qh: el pozo, "Bluetooth", el interruptor y cerrar.
            Row(Modifier.fillMaxWidth().height(44.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                IceWell(IceIcons.bluetooth, ice.ice, size = 40.dp)
                IceText(stringResource(R.string.bt_title), IceText2.h2.copy(fontSize = 24.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Normal), Color.White, modifier = Modifier.weight(1f))
                IceToggle(on, onChange = { want -> toggleBluetooth(context, adapter, want); tick++ })
                IceIconButton(IceIcons.close, close, Modifier.padding(start = 6.dp), iconSize = 22.dp)
            }
            // .dev: el celular conectado (o ninguno) y Reconectar.
            Row(
                Modifier.padding(top = 14.dp).fillMaxWidth().height(70.dp)
                    .then(if (connected) Modifier.iceSelected(18.dp) else Modifier.iceCard())
                    .padding(start = 16.dp, end = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (connected) Box(Modifier.size(24.dp).softGlow(ice.iceGlow.copy(alpha = 0.25f), 6.dp))
                    Icon(IceIcons.smartphone, if (connected) Color(0xFFD8F0FF) else ice.ink3, 30.dp)
                }
                Column(Modifier.weight(1f)) {
                    IceText(phone ?: stringResource(R.string.p2_bt_no_phone), IceText2.h3.copy(fontSize = 18.sp), Color.White)
                    Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        IceDot(if (connected) Color(0xFFBFE6FF) else Color(0xFFFFC56B))
                        Spacer(Modifier.width(7.dp))
                        IceText(
                            stringResource(if (connected) R.string.p2_bt_connected else if (on) R.string.p2_bt_waiting else R.string.p2_bt_off),
                            IceType.small, Color(222, 238, 255).copy(alpha = 0.8f),
                        )
                    }
                }
                IceButton(
                    stringResource(R.string.p2_bt_reconnect),
                    { reconnect(context, d, adapter, bonded.firstOrNull { name(it) == phone } ?: bonded.firstOrNull()) },
                    icon = IceIcons.refresh, height = 48.dp, padding = 16.dp, textColor = Color(0xFFE6F4FF),
                )
            }
            NowPlayingBt(d, phone, Modifier.padding(top = 12.dp).fillMaxWidth().height(160.dp))
            if (others.isNotEmpty()) {
                IceText(stringResource(R.string.p2_bt_others).uppercase(), IceText2.cap.copy(letterSpacing = androidx.compose.ui.unit.TextUnit(0.16f, androidx.compose.ui.unit.TextUnitType.Em)), ice.ink3, modifier = Modifier.padding(start = 2.dp, top = 14.dp, bottom = 8.dp))
                for (dev in others.take(1)) {
                    Row(
                        Modifier.fillMaxWidth().height(56.dp).clip(RoundedCornerShape(16.dp)).background(Color(6, 12, 22).copy(alpha = 0.4f))
                            .border(1.dp, ice.line2, RoundedCornerShape(16.dp)).padding(start = 14.dp, end = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(IceIcons.smartphone, ice.ink2, 24.dp)
                        Column(Modifier.weight(1f)) {
                            IceText(name(dev), IceText2.rowTitle, ice.ink)
                            IceText(stringResource(R.string.p2_bt_paired_idle), IceType.small, ice.ink3, modifier = Modifier.padding(top = 4.dp))
                        }
                        IceButton(stringResource(R.string.p2_bt_connect), { reconnect(context, d, adapter, dev) }, padding = 16.dp)
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            IceButton(
                stringResource(R.string.p2_bt_pair_new),
                { openSystemSettings(context, Settings.ACTION_BLUETOOTH_SETTINGS) },
                Modifier.fillMaxWidth(), icon = IceIcons.plus, height = 48.dp,
            )
        }
    }
}

/** `.now`: lo que suena por Bluetooth: carátula, título, de qué celular, la barra y los controles. */
@Composable
private fun NowPlayingBt(d: Dependencies, phone: String?, modifier: Modifier) {
    val ice = LocalIce.current
    val repo = d.music
    val song = repo.song
    val fromBt = song != null && (song.source == SourceKind.BLUETOOTH || repo.mode == com.irvingguerra.litedash.feature.music.MusicMode.BLUETOOTH)
    Box(modifier.iceCard()) {
        Artwork(song.takeIf { fromBt }, Modifier.offset(16.dp, 16.dp).size(96.dp))
        Box(
            Modifier.offset(92.dp, 92.dp).size(28.dp).clip(CircleShape).background(Color(0xFF16263C)).border(1.dp, Color(190, 225, 255).copy(alpha = 0.3f), CircleShape),
            contentAlignment = Alignment.Center,
        ) { Icon(IceIcons.bluetooth, ice.ice, 16.dp) }
        Column(Modifier.offset(128.dp, 20.dp).padding(end = 144.dp)) {
            IceText(if (fromBt) song!!.title.ifBlank { stringResource(R.string.music_idle) } else stringResource(R.string.p2_bt_nothing), androidx.compose.ui.text.TextStyle(fontFamily = com.irvingguerra.litedash.core.ui.ice.Outfit, fontWeight = androidx.compose.ui.text.font.FontWeight.Medium, fontSize = 20.sp), Color.White)
            val by = if (fromBt) listOfNotNull(song!!.artist.takeIf { it.isNotBlank() }, phone?.let { stringResource(R.string.p2_bt_from, it) }).joinToString(" · ") else stringResource(R.string.p2_bt_nothing_help)
            IceText(by, IceType.songArtist, ice.ink2, modifier = Modifier.padding(top = 5.dp))
        }
        if (fromBt && song != null) {
            val duration = song.durationMs
            val pos = rememberPosition(song).value
            val f = if (duration > 0) (pos.toFloat() / duration).coerceIn(0f, 1f) else 0f
            Row(Modifier.offset(128.dp, 72.dp).width(428.dp), verticalAlignment = Alignment.CenterVertically) {
                IceText(fmt(pos), IceType.small, ice.ink3)
                Spacer(Modifier.width(10.dp))
                Box(Modifier.weight(1f).height(16.dp).drawBehind {
                    val h = 3.dp.toPx()
                    val cy = size.height / 2
                    val x = size.width * f
                    drawRoundRect(Color(190, 225, 255).copy(alpha = 0.14f), Offset(0f, cy - h / 2), Size(size.width, h), CornerRadius(h / 2))
                    drawRoundRect(Brush.horizontalGradient(listOf(Color(0xFF7CC6FF), Color(0xFFE3F4FF)), endX = x.coerceAtLeast(1f)), Offset(0f, cy - h / 2), Size(x, h), CornerRadius(h / 2))
                    drawCircle(Color(190, 230, 255).copy(alpha = 0.4f), 6.5.dp.toPx(), Offset(x, cy))
                    drawCircle(Color.White, 4.5.dp.toPx(), Offset(x, cy))
                })
                Spacer(Modifier.width(10.dp))
                IceText(if (duration > 0) fmt(duration) else "--:--", IceType.small, ice.ink3)
            }
        }
        Row(Modifier.align(Alignment.BottomCenter).offset(56.dp, (-8).dp), horizontalArrangement = Arrangement.spacedBy(34.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(48.dp).clip(CircleShape).clickable { if (!fromBt) repo.mode = com.irvingguerra.litedash.feature.music.MusicMode.BLUETOOTH; repo.previous() }, contentAlignment = Alignment.Center) { Icon(IceIcons.previous, Color(0xFFEAF4FF), 26.dp) }
            Box(
                Modifier.softGlow(Color(170, 220, 255).copy(alpha = 0.35f), 14.dp).size(54.dp).clip(CircleShape).background(Color.White)
                    .clickable { if (!fromBt) repo.mode = com.irvingguerra.litedash.feature.music.MusicMode.BLUETOOTH; repo.toggle() },
                contentAlignment = Alignment.Center,
            ) { Icon(if (fromBt && song?.playing == true) IceIcons.pauseBars else IceIcons.play, Color(0xFF0A1322), 26.dp) }
            Box(Modifier.size(48.dp).clip(CircleShape).clickable { if (!fromBt) repo.mode = com.irvingguerra.litedash.feature.music.MusicMode.BLUETOOTH; repo.next() }, contentAlignment = Alignment.Center) { Icon(IceIcons.next, Color(0xFFEAF4FF), 26.dp) }
        }
    }
}

private fun fmt(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(s / 60, s % 60)
}

@SuppressLint("MissingPermission")
private fun bondedDevices(adapter: BluetoothAdapter?): List<BluetoothDevice> =
    runCatching { adapter?.bondedDevices?.toList().orEmpty() }.getOrDefault(emptyList())

@SuppressLint("MissingPermission")
private fun name(dev: BluetoothDevice): String = runCatching { dev.name?.takeIf { it.isNotBlank() } ?: dev.address }.getOrDefault(dev.address)

/** Prender: el diálogo de Android. Apagar: directo (hasta Android 12 se deja); si no, sus ajustes. */
@SuppressLint("MissingPermission")
@Suppress("DEPRECATION")
private fun toggleBluetooth(context: Context, adapter: BluetoothAdapter?, on: Boolean) {
    val flags = Intent.FLAG_ACTIVITY_NEW_TASK
    if (adapter == null) return openSystemSettings(context, Settings.ACTION_BLUETOOTH_SETTINGS)
    if (on) {
        runCatching { context.startActivity(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE).addFlags(flags)) }
            .onFailure { openSystemSettings(context, Settings.ACTION_BLUETOOTH_SETTINGS) }
    } else if (runCatching { adapter.disable() }.getOrDefault(false) != true) {
        openSystemSettings(context, Settings.ACTION_BLUETOOTH_SETTINGS)
    }
}

/**
 * Volver a conectar [device] para llamadas y música: en las pantallas con chip propio, su app de
 * Bluetooth (ahí se reconecta); con el Bluetooth de Android, el perfil de audio (A2DP) se lo pide
 * directo y, si no deja, se abren sus ajustes.
 */
@SuppressLint("MissingPermission")
private fun reconnect(context: Context, d: Dependencies, adapter: BluetoothAdapter?, device: BluetoothDevice?) {
    if (adapter == null || !d.music.bluetoothBuiltIn) {
        if (d.music.bluetoothApp() != null) return d.music.openBluetoothApp()
        return openSystemSettings(context, Settings.ACTION_BLUETOOTH_SETTINGS)
    }
    if (device == null) return openSystemSettings(context, Settings.ACTION_BLUETOOTH_SETTINGS)
    val ok = runCatching {
        adapter.getProfileProxy(context, object : BluetoothProfile.ServiceListener {
            override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
                val done = runCatching { proxy.javaClass.getMethod("connect", BluetoothDevice::class.java).invoke(proxy, device) as? Boolean }.getOrNull() == true
                adapter.closeProfileProxy(profile, proxy)
                if (!done) openSystemSettings(context, Settings.ACTION_BLUETOOTH_SETTINGS)
            }

            override fun onServiceDisconnected(profile: Int) = Unit
        }, BluetoothProfile.A2DP)
    }.getOrDefault(false)
    if (!ok) openSystemSettings(context, Settings.ACTION_BLUETOOTH_SETTINGS)
}
