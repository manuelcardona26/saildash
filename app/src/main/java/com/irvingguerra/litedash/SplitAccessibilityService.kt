package com.irvingguerra.litedash

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.lang.ref.WeakReference

/**
 * SailDash: el servicio de accesibilidad de la pantalla dividida (Android 7 a 12). En Android 7 una
 * app solo puede dividir la pantalla desde aquí, con la acción global "pantalla dividida", y es lo
 * único que hace. No pide eventos ni lee ventanas (res/xml/split_accessibility.xml): se queda
 * quieto, sin gastar CPU, hasta que [SplitLegacy] le pide dividir. Solo se prende en Android 7 a 12
 * (res/values-v24 y values-v32): en los demás ni aparece en Accesibilidad.
 */
class SplitAccessibilityService : AccessibilityService() {
    override fun onServiceConnected() {
        super.onServiceConnected()
        current = WeakReference(this)
        connected = true
    }

    // No pidió ningún tipo de evento: si alguno llegara, se ignora.
    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: Intent?): Boolean {
        forget()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        forget()
        super.onDestroy()
    }

    private fun forget() {
        if (current?.get() !== this) return
        current = null
        connected = false
    }

    companion object {
        // Débil: Android crea y destruye el servicio; aquí solo se le pide dividir mientras está conectado.
        @Volatile private var current: WeakReference<SplitAccessibilityService>? = null

        /** Conectado y listo (estado de Compose: Ajustes se redibuja solo al prenderlo o apagarlo). */
        var connected by mutableStateOf(false)
            private set

        /** El servicio conectado, o null si está apagado. */
        val instance: SplitAccessibilityService? get() = current?.get()

        /**
         * Divide la pantalla: la app de arriba queda acoplada a la izquierda (arriba, si la pantalla
         * está parada) y Android muestra las recientes en la otra mitad. Es un interruptor: si ya
         * estaba dividida, la une. false = el servicio está apagado o Android no aceptó la orden.
         */
        fun toggleSplit(): Boolean {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return false
            val service = instance ?: return false
            return runCatching { service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_TOGGLE_SPLIT_SCREEN) }.getOrDefault(false)
        }

        /** Se apaga solo (al apagar la pantalla dividida en SailDash): sin uso no tiene por qué estar prendido. */
        fun turnOff() {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return
            val service = instance ?: return
            runCatching { service.disableSelf() }
        }

        /** ¿Está prendido en Ajustes → Accesibilidad de Android? (Puede estarlo y aún no conectarse.) */
        fun enabledInSettings(context: Context): Boolean {
            val me = ComponentName(context, SplitAccessibilityService::class.java)
            val list = runCatching {
                Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            }.getOrNull() ?: return false
            return list.split(':').any { ComponentName.unflattenFromString(it) == me }
        }

        /** Abre Ajustes → Accesibilidad de Android, donde se prende (si la pantalla no lo trae, Ajustes). */
        fun openSettings(context: Context) {
            val accessibility = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            runCatching { context.startActivity(accessibility) }.onFailure {
                runCatching { context.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            }
        }
    }
}
