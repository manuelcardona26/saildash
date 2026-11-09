package com.irvingguerra.litedash

import com.irvingguerra.litedash.core.vehicle.Source
import kotlin.math.roundToInt

/*
 * SailDash: "Solo velocidad". Aquí solo la lógica (sin Android ni Compose) para poder probarla:
 * el número que se ve, el arco, la alerta de velocidad, el pitido y cuándo se abre sola.
 */

/** Los límites para la alerta, en km/h, y su pareja en mph (en la misma posición). */
internal val SPEED_ONLY_LIMITS_KMH = listOf(60, 80, 100, 120)
private val SPEED_ONLY_LIMITS_MPH = listOf(40, 50, 65, 75)

/** Las velocidades para que se abra sola, en km/h, y su pareja en mph. */
internal val SPEED_ONLY_AUTO_KMH = listOf(20, 40, 60, 80)
private val SPEED_ONLY_AUTO_MPH = listOf(15, 25, 40, 50)

/** Hasta dónde llega el arco: 160 km/h o 100 mph (el Sail casi nunca pasa de ahí). */
private const val ARC_MAX_KMH = 160
private const val ARC_MAX_MPH = 100

/** Menos que esto es el GPS "caminando" con el carro quieto: se ve 0. */
private const val CREEP_KMH = 2f

/** El pitido vuelve a sonar solo después de bajar esto por debajo del límite (el GPS baila ±1). */
private const val BEEP_REARM = 3

/** Para volver a abrirse sola, antes hay que bajar a menos de esta parte de la velocidad elegida. */
private const val REARM_FRACTION = 0.5f

/** Lo guardado si es una de las [options]; si no (o 0), null = apagado. */
internal fun speedOnlyOption(stored: Int, options: List<Int>): Int? = stored.takeIf { it in options }

/** El número que se ve: entero, en km/h o mph. Parado, 0 (el GPS no se queda quieto del todo). */
internal fun speedOnlyShown(kmh: Float, miles: Boolean): Int {
    if (kmh.isNaN() || kmh < CREEP_KMH) return 0
    return kmh.toMiles(miles).roundToInt().coerceAtMost(999)
}

/** Un valor guardado en km/h en la unidad que se ve: su pareja de [mph] o, si no la tiene, convertido. */
private fun paired(kmh: Int, miles: Boolean, kmhOptions: List<Int>, mph: List<Int>): Int =
    if (!miles) kmh else mph.getOrNull(kmhOptions.indexOf(kmh)) ?: (kmh / KM_PER_MILE).roundToInt()

/** El límite de la alerta (guardado en km/h) en la unidad que se ve: 80 km/h ↔ 50 mph. */
internal fun speedOnlyLimitShown(kmh: Int, miles: Boolean): Int = paired(kmh, miles, SPEED_ONLY_LIMITS_KMH, SPEED_ONLY_LIMITS_MPH)

/** La velocidad para abrirse sola (guardada en km/h) en la unidad que se ve: 40 km/h ↔ 25 mph. */
internal fun speedOnlyAutoShown(kmh: Int, miles: Boolean): Int = paired(kmh, miles, SPEED_ONLY_AUTO_KMH, SPEED_ONLY_AUTO_MPH)

/** La misma en km/h exactos, para compararla con el bus (25 mph = 40.2 km/h). */
internal fun speedOnlyAutoThresholdKmh(kmh: Int, miles: Boolean): Float =
    if (miles) speedOnlyAutoShown(kmh, true) * KM_PER_MILE else kmh.toFloat()

/** Qué tanto del arco va lleno con [shown] (en la unidad que se ve), de 0 a 1. */
internal fun speedOnlyFraction(shown: Int, miles: Boolean): Float =
    (shown.toFloat() / (if (miles) ARC_MAX_MPH else ARC_MAX_KMH)).coerceIn(0f, 1f)

/**
 * Lo que se ve en la pantalla. Es igual (equals) mientras no cambie el número entero: con 61.2 y
 * 61.4 km/h no se redibuja nada.
 */
internal data class SpeedOnlyView(
    /** null = todavía sin dato (se ve "—"). */
    val number: Int?,
    /** Qué tanto del arco va lleno (0–1). */
    val fraction: Float,
    /** Va por encima del límite de la alerta: el número se pone rojo. */
    val over: Boolean,
    /** Sin dato o con el GPS perdido: el número se ve apagado (y nunca rojo). */
    val dim: Boolean,
) {
    val text: String get() = number?.toString() ?: "—"
}

/** [kmh] del bus (null = nadie lo sabe); [limit] ya en la unidad que se ve (null = sin alerta); [stale] = GPS perdido. */
internal fun speedOnlyView(kmh: Float?, miles: Boolean, limit: Int?, stale: Boolean = false): SpeedOnlyView {
    if (kmh == null) return SpeedOnlyView(null, 0f, over = false, dim = true)
    val shown = speedOnlyShown(kmh, miles)
    return SpeedOnlyView(shown, speedOnlyFraction(shown, miles), over = !stale && limit != null && shown > limit, dim = stale)
}

/** De dónde sale lo que se ve (arriba a la derecha). [ok] = hay velocidad fresca. */
internal enum class SpeedOnlyStatus(val ok: Boolean) {
    OBD(true),
    CAN(true),
    GPS(true),
    /** El OBD está conectado pero todavía no da la velocidad. */
    OBD_WAITING(false),
    GPS_SEARCHING(false),
    /** El GPS dejó de dar puntos (túnel, parqueadero): se ve la última velocidad, apagada. */
    GPS_LOST(false),
    NO_PERMISSION(false),
}

internal fun speedOnlyStatus(source: Source?, obdOn: Boolean, gpsAllowed: Boolean, gpsStale: Boolean): SpeedOnlyStatus = when (source) {
    Source.OBD -> SpeedOnlyStatus.OBD
    Source.CAN -> SpeedOnlyStatus.CAN
    Source.GPS -> if (gpsStale) SpeedOnlyStatus.GPS_LOST else SpeedOnlyStatus.GPS
    null -> when {
        obdOn -> SpeedOnlyStatus.OBD_WAITING
        !gpsAllowed -> SpeedOnlyStatus.NO_PERMISSION
        else -> SpeedOnlyStatus.GPS_SEARCHING
    }
}

/**
 * Un solo pitido al pasarse de [limit] (en la unidad que se ve); el siguiente, solo después de
 * bajar [BEEP_REARM] por debajo. Así no pita a cada rato si la velocidad baila alrededor del límite.
 */
internal class SpeedOnlyBeep(private val limit: Int) {
    private var armed = true

    /** [shown] = el número que se ve (null = sin dato o GPS perdido: no cuenta). true = pitar ahora. */
    fun update(shown: Int?): Boolean {
        if (shown == null) return false
        if (shown <= limit - BEEP_REARM) armed = true
        if (!armed || shown <= limit) return false
        armed = false
        return true
    }
}

/**
 * Cuándo abrir "Solo velocidad" sola: al pasar de [thresholdKmh] después de haber ido a menos de
 * la mitad. Así no se abre de una al volver al inicio ya rápido, ni a cada rato si la velocidad
 * baila alrededor; si la cierras, vuelve a abrirse la próxima vez que bajes y pases otra vez.
 */
internal class SpeedOnlyTrigger(private val thresholdKmh: Float) {
    private var armed = false

    /** [kmh] del bus (null = sin dato: no cambia nada). true = abrir ahora. */
    fun update(kmh: Float?): Boolean {
        if (kmh == null || kmh.isNaN()) return false
        if (kmh < thresholdKmh * REARM_FRACTION) {
            armed = true
        } else if (armed && kmh >= thresholdKmh) {
            armed = false
            return true
        }
        return false
    }
}
