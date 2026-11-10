package com.irvingguerra.litedash

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.provider.Settings

/*
 * SailDash: el Bluetooth "del carro". En muchas pantallas chinas el celular se vincula en una app
 * propia (Teléfono, Música BT…), no en los ajustes de Android. Los botones de Bluetooth del inicio,
 * del Carro y del Mapa abren esa app; solo si no hay ninguna, los ajustes de Android.
 */

/** Abre la app de Bluetooth de la pantalla: la enlazada en Ajustes, la que se adivina o, al final, Android. */
fun openCarBluetooth(context: Context, d: Dependencies) {
    if (d.music.bluetoothApp() != null) return d.music.openBluetoothApp()
    val pkg = guessCarBluetoothApp(context)
    val launch = pkg?.let { context.packageManager.getLaunchIntentForPackage(it) }
    if (launch != null && runCatching { context.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess) return
    openSystemSettings(context, Settings.ACTION_BLUETOOTH_SETTINGS)
}

/**
 * Entre las apps de fábrica que se pueden abrir, la de Bluetooth del carro, sin guardarla: así un error
 * no se queda pegado (se corrige eligiéndola en Ajustes → Apps → Música → Bluetooth).
 */
fun guessCarBluetoothApp(context: Context): String? {
    val pm = context.packageManager
    val apps = runCatching {
        pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
    }.getOrDefault(emptyList())
    val candidates = apps.mapNotNull { ri ->
        val info = ri.activityInfo?.applicationInfo ?: return@mapNotNull null
        val pkg = info.packageName
        if (pkg == context.packageName || pkg in NOT_CAR_BT) return@mapNotNull null
        if (info.flags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) == 0) return@mapNotNull null
        val label = runCatching { ri.loadLabel(pm).toString() }.getOrDefault("")
        pkg to carBluetoothScore(pkg, label)
    }
    return candidates.filter { it.second > 0 }.maxByOrNull { it.second }?.first
}

/** Puntos por nombre: "bluetooth" pesa más que "bt", y "teléfono" (donde se vincula en muchas) menos. */
internal fun carBluetoothScore(pkg: String, label: String): Int {
    val words = (pkg.split('.') + label.split(Regex("[^\\p{L}\\p{N}]+"))).map { it.lowercase() }.filter { it.isNotEmpty() }.toSet()
    return when {
        "bluetooth" in words -> 3
        "bt" in words || words.any { it.startsWith("bt") && it.length <= 6 } -> 2
        words.any { it in PHONE_WORDS } -> 1
        else -> 0
    }
}

private val PHONE_WORDS = setOf("teléfono", "telefono", "phone", "dialer")

/** Apps de Android que dicen "bluetooth" o "teléfono" pero no son las del carro. */
private val NOT_CAR_BT = setOf(
    "com.android.settings", "com.android.bluetooth", "com.android.dialer", "com.android.phone",
    "com.android.contacts", "com.google.android.dialer", "com.zjinnova.zlink", "cn.manstep.phonemirrorBox",
)
