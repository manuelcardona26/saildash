package com.irvingguerra.litedash

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import com.irvingguerra.litedash.core.ui.Button
import com.irvingguerra.litedash.core.ui.IconButton
import com.irvingguerra.litedash.core.ui.Icons
import com.irvingguerra.litedash.core.ui.Label
import com.irvingguerra.litedash.core.ui.LocalColors
import com.irvingguerra.litedash.core.ui.Radius
import com.irvingguerra.litedash.core.ui.Typography
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

/** Desde cuándo se cuenta el siguiente al marcar uno como hecho. */
enum class ReminderRenewal {
    /** Desde el día en que se hace: un cambio de aceite o una técnico-mecánica hechos antes de tiempo adelantan el siguiente. */
    FROM_DONE,

    /** Desde que vencía o, si ya estaba vencido, desde hoy: el SOAT renovado a tiempo arranca cuando se acaba el anterior. */
    FROM_DUE_OR_TODAY,

    /** Siempre desde que vencía: el impuesto tiene la misma fecha límite cada año, aunque se pague tarde. */
    FROM_DUE,
}

/**
 * Trámites y cuidados del carro, con los nombres de Colombia. [stored] es lo que se guarda: el de los
 * que venían de LiteDash no cambia, así que las listas guardadas antes se siguen leyendo igual.
 * [months] es cada cuánto se repite al marcarlo como hecho (0 = no se repite y se quita) y [renewal],
 * desde cuándo se cuenta. Los que tienen [kmLabel] también llevan el kilometraje del próximo.
 */
enum class ReminderType(
    val stored: String,
    val title: Int,
    val months: Long,
    val renewal: ReminderRenewal = ReminderRenewal.FROM_DONE,
    /** "Próximo cambio a los ___ km" (0 = no lleva km). */
    val kmLabel: Int = 0,
    /** La pregunta del aviso al marcarlo como hecho: "¿A los cuántos km es el próximo cambio?". */
    val kmQuestion: Int = 0,
) {
    SERVICE("service", R.string.reminder_service, 6, kmLabel = R.string.recordatorios_km_service, kmQuestion = R.string.recordatorios_ask_km_service),
    INSPECTION("inspection", R.string.reminder_verification, 12),
    TAX("tax", R.string.reminder_tax, 12, ReminderRenewal.FROM_DUE),
    INSURANCE("insurance", R.string.reminder_insurance, 12, ReminderRenewal.FROM_DUE_OR_TODAY),
    PLATES("plates", R.string.reminder_plates, 0),
    EXTINGUISHER("extinguisher", R.string.recordatorios_extinguisher, 12),
    FULL_COVERAGE("full_coverage", R.string.recordatorios_full_coverage, 12, ReminderRenewal.FROM_DUE_OR_TODAY),
    TIRE_ROTATION("tire_rotation", R.string.recordatorios_tire_rotation, 6, kmLabel = R.string.recordatorios_km_rotation, kmQuestion = R.string.recordatorios_ask_km_rotation),
    OTHER("other", R.string.reminder_other, 0),
    ;

    val hasKm get() = kmLabel != 0

    /** La fecha del siguiente si el que vencía el [due] se marca como hecho el [today]; null = no se repite. */
    fun next(due: Day, today: Day): Day? {
        if (months <= 0) return null
        return when (renewal) {
            ReminderRenewal.FROM_DONE -> today.plusMonths(months)
            ReminderRenewal.FROM_DUE_OR_TODAY -> maxOf(due, today).plusMonths(months)
            // Si se pagó con más de un periodo de atraso, el siguiente es el primero que todavía no pasa.
            ReminderRenewal.FROM_DUE -> generateSequence(1L) { it + 1 }.map { due.plusMonths(months * it) }.first { it.isAfter(today) }
        }
    }

    companion object {
        fun fromStored(value: String?) = entries.firstOrNull { it.stored == value }
    }
}

/** Un recordatorio; [km] es el kilometraje del próximo (cambio de aceite, rotación), si se puso. */
data class Reminder(val id: Long, val type: ReminderType, val date: Day, val km: Int? = null) {
    /** Cómo queda al marcarlo como hecho el [today], con el [km] del próximo; null = se quita. */
    fun done(km: Int?, today: Day): Reminder? = type.next(date, today)?.let { copy(date = it, km = km.takeIf { type.hasKm }) }
}

/** El kilometraje escrito a mano: "85.000", "85000" o "85,000 km" son lo mismo. */
object ReminderKm {
    const val MAX_DIGITS = 7
    const val MAX = 9_999_999L

    /** Solo cuentan los números; vacío, cero o más de [MAX_DIGITS] cifras = sin km (null). */
    fun parse(text: String): Int? {
        val digits = text.filter { it in '0'..'9' }
        if (digits.isEmpty() || digits.length > MAX_DIGITS) return null
        return digits.toInt().takeIf { it > 0 }
    }

    /** 85000 → "85.000" en español (como se escribe en Colombia) y "85,000" en inglés. */
    fun format(km: Int, locale: Locale): String {
        val separator = if (locale.language == "es") "." else ","
        return km.toString().reversed().chunked(3).joinToString(separator).reversed()
    }
}

/**
 * La lista guardada: [{"id", "type", "date", "km"?}]. "km" es opcional y nuevo en SailDash: las
 * listas de antes se leen igual y una versión vieja simplemente lo ignora. Lo que esta versión no
 * entiende (un tipo de una versión más nueva, una fecha dañada) se vuelve a guardar tal cual en vez
 * de borrarlo. Sin Android: se prueba en el PC.
 */
object ReminderJson {
    class Parsed(val reminders: List<Reminder>, val unknown: List<Any>)

    fun parse(text: String?): Parsed {
        val array = runCatching { JSONArray(text ?: "[]") }.getOrNull() ?: return Parsed(emptyList(), emptyList())
        val reminders = ArrayList<Reminder>()
        val unknown = ArrayList<Any>()
        for (i in 0 until array.length()) {
            val raw = array.opt(i) ?: continue
            val reminder = (raw as? JSONObject)?.let(::read)
            if (reminder != null) reminders += reminder else unknown += raw
        }
        return Parsed(reminders.sortedBy { it.date }, unknown)
    }

    fun write(reminders: List<Reminder>, unknown: List<Any> = emptyList()): String {
        val array = JSONArray()
        for (r in reminders) {
            val o = JSONObject().put(ID, r.id).put(TYPE, r.type.stored).put(DATE, r.date.toString())
            // Sin km no se escribe la clave: queda igual que una lista de antes.
            r.km?.let { o.put(KM, it) }
            array.put(o)
        }
        for (raw in unknown) array.put(raw)
        return array.toString()
    }

    private fun read(o: JSONObject): Reminder? {
        val type = ReminderType.fromStored(o.optString(TYPE)) ?: return null
        return runCatching {
            val km = o.optLong(KM, 0L).takeIf { it in 1..ReminderKm.MAX }?.toInt()
            Reminder(o.getLong(ID), type, Day.parse(o.getString(DATE)), km)
        }.getOrNull()
    }

    private const val ID = "id"
    private const val TYPE = "type"
    private const val DATE = "date"
    private const val KM = "km"
}

/** Recordatorios guardados en el dispositivo; nada corre en segundo plano: se revisan al ver el inicio. */
class ReminderRepository(context: Context) {
    private val app = context.applicationContext
    private val store = context.getSharedPreferences(STORE, Context.MODE_PRIVATE)
    private val loaded = ReminderJson.parse(store.getString(KEY, null))

    /** Lo que esta versión no entiende: se vuelve a guardar tal cual. */
    private val unknown = loaded.unknown

    var all by mutableStateOf(loaded.reminders)
        private set

    /**
     * Los que vencen en los próximos [DAYS_BEFORE] días o ya vencieron, el más próximo primero.
     * SailDash: también los que llevan km y les faltan [KM_BEFORE] o menos con el odómetro por GPS.
     */
    fun due(today: Day = Day.today(), odometerKm: Double? = runCatching { Garage.get(app).odometer?.km }.getOrNull()) = all.filter {
        !it.date.isAfter(today.plusDays(DAYS_BEFORE)) || (it.km != null && odometerKm != null && kmLeft(it.km, odometerKm) <= KM_BEFORE)
    }.sortedBy { it.date }

    fun add(type: ReminderType, date: Day, km: Int? = null) =
        save(all + Reminder(System.currentTimeMillis(), type, date, km.takeIf { type.hasKm }))

    fun remove(r: Reminder) = save(all - r)

    /** Hecho: si se repite, pasa a la siguiente fecha (con el [km] del próximo, si se puso); si no, se quita. */
    fun done(r: Reminder, km: Int? = null, today: Day = Day.today()) = save(all.mapNotNull { if (it == r) it.done(km, today) else it })

    private fun save(list: List<Reminder>) {
        all = list.sortedBy { it.date }
        store.edit { putString(KEY, ReminderJson.write(all, unknown)) }
    }

    private companion object {
        const val STORE = "reminders"
        const val KEY = "list"
        const val DAYS_BEFORE = 7L
        const val KM_BEFORE = 500L
    }
}

@Composable
private fun formatDate(date: Day): String {
    val locale = currentLocale()
    return DateFormat.getDateInstance(DateFormat.LONG, locale).format(date.toDate())
}

/** "vence hoy", "faltan 3 días", "venció hace 2 días". */
@Composable
private fun whenText(date: Day): String {
    val days = Day.today().daysUntil(date).toInt()
    return when {
        days == 0 -> stringResource(R.string.reminder_today)
        days > 0 -> pluralStringResource(R.plurals.reminder_in_days, days, days)
        else -> pluralStringResource(R.plurals.reminder_overdue, -days, -days)
    }
}

/** "a los 85.000 km" y, con el odómetro por GPS puesto, "faltan 400 km" (null si no tiene km). */
@Composable
private fun kmText(r: Reminder): String? {
    val km = r.km ?: return null
    val at = stringResource(R.string.recordatorios_at_km, ReminderKm.format(km, currentLocale()))
    val odo = Garage.get(LocalContext.current).odometer ?: return at
    val left = kmLeft(km, odo.km)
    return if (left >= 0) "$at · " + stringResource(R.string.rc_km_left, ReminderKm.format(left.toInt(), currentLocale())) else at
}

/** Esconde el teclado y suelta el campo (al guardar o cancelar). */
@Composable
private fun rememberKeyboardCloser(): () -> Unit {
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    return { focus.clearFocus(); keyboard?.hide() }
}

/**
 * Aviso en el inicio del recordatorio más próximo: "Cambio de aceite · faltan 3 días · a los 85.000 km".
 * "Hecho" lo pasa a la siguiente fecha; si lleva km, antes pregunta el del próximo (se puede dejar vacío).
 */
@Composable
fun ReminderBanner(repo: ReminderRepository) {
    val r = repo.due().firstOrNull() ?: return
    var askingKm by remember(r) { mutableStateOf(false) }
    var km by remember(r) { mutableStateOf("") }
    val closeKeyboard = rememberKeyboardCloser()
    if (askingKm) {
        Notice(stringResource(r.type.kmQuestion), Icons.bell) {
            KmField(km, { km = it })
            Button(stringResource(R.string.recordatorios_save), { closeKeyboard(); repo.done(r, ReminderKm.parse(km)) }, primary = true)
            Button(stringResource(R.string.cancel), { closeKeyboard(); askingKm = false })
        }
        return
    }
    Notice(listOfNotNull(stringResource(r.type.title), whenText(r.date), kmText(r)).joinToString(" · "), Icons.bell) {
        Button(stringResource(R.string.reminder_done), { if (r.type.hasKm) askingKm = true else repo.done(r) }, primary = true)
    }
}

/**
 * Ajustes → Tu carro: la lista y cómo agregar uno (tipo, su fecha en un calendario y, si lleva, el km
 * del próximo). "Hecho" en la lista sirve para cuando se hizo antes de que saliera el aviso: muestra
 * la fecha siguiente antes de guardar, para que un toque sin querer no cambie nada.
 */
@Composable
fun ReminderSettings(repo: ReminderRepository) {
    val c = LocalColors.current
    var picking by remember { mutableStateOf<ReminderType?>(null) }
    var finishing by remember { mutableStateOf<Reminder?>(null) }
    // El km que se está escribiendo (al agregar o al marcar como hecho; nunca los dos a la vez).
    var km by remember { mutableStateOf("") }
    val closeKeyboard = rememberKeyboardCloser()
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        for (r in repo.all) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f)) {
                    Label(stringResource(r.type.title), Typography.subtitle, maxLines = 1)
                    Label(listOfNotNull(formatDate(r.date), whenText(r.date), kmText(r)).joinToString(" · "), Typography.small, c.textMuted, maxLines = 2)
                }
                if (r.type.months > 0 && finishing != r) {
                    Button(stringResource(R.string.reminder_done), { picking = null; km = ""; finishing = r })
                }
                Button(stringResource(R.string.remove), { if (finishing == r) finishing = null; repo.remove(r) })
            }
            if (finishing == r) {
                Column(Modifier.padding(start = 12.dp, bottom = 6.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    r.type.next(r.date, Day.today())?.let { Label(stringResource(R.string.recordatorios_next, formatDate(it)), Typography.body) }
                    if (r.type.hasKm) KmRow(r.type, km) { km = it }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(stringResource(R.string.recordatorios_save), { closeKeyboard(); repo.done(r, ReminderKm.parse(km)); km = ""; finishing = null }, primary = true)
                        Button(stringResource(R.string.cancel), { closeKeyboard(); km = ""; finishing = null })
                    }
                }
            }
        }
        val type = picking
        if (type == null) {
            Label(stringResource(R.string.reminder_add), Typography.label, c.textMuted, modifier = Modifier.padding(top = 6.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                for (t in ReminderType.entries) Button(stringResource(t.title), { finishing = null; km = ""; picking = t })
            }
        } else {
            Label(stringResource(R.string.recordatorios_pick_date, stringResource(type.title)), Typography.label, c.textMuted, modifier = Modifier.padding(top = 6.dp))
            // El km va arriba del calendario: se escribe primero (tocar el día guarda) y queda espacio
            // debajo para subirlo sobre el teclado.
            if (type.hasKm) KmRow(type, km) { km = it }
            DatePicker(onPick = { closeKeyboard(); repo.add(type, it, ReminderKm.parse(km)); km = ""; picking = null })
            Button(stringResource(R.string.cancel), { closeKeyboard(); km = ""; picking = null })
        }
    }
}

/** "Próximo cambio a los [      ] km". */
@Composable
private fun KmRow(type: ReminderType, km: String, onChange: (String) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Label(stringResource(type.kmLabel), Typography.body)
        KmField(km, onChange, lift = true)
    }
}

private val FieldHeight = 56.dp

/** Lo que mide de sobra el teclado en una pantalla acostada de 600 px (Android 7 no avisa cuánto mide). */
private val KeyboardRoom = 300.dp

/**
 * El km del próximo, con el teclado de números; vacío dice "opcional". [lift] (en Ajustes): al
 * tocarlo, la página se corre para dejarlo arriba del teclado, que en esta pantalla tapa la mitad de
 * abajo sin que la app se entere.
 */
@Composable
private fun KmField(value: String, onChange: (String) -> Unit, lift: Boolean = false) {
    val c = LocalColors.current
    val shape = RoundedCornerShape(Radius.card)
    val requester = remember { BringIntoViewRequester() }
    val scope = rememberCoroutineScope()
    val room = with(LocalDensity.current) { (FieldHeight + KeyboardRoom).toPx() }
    Row(
        Modifier
            .width(150.dp)
            .height(FieldHeight)
            .bringIntoViewRequester(requester)
            .clip(shape)
            .background(c.surface)
            .border(1.dp, c.line, shape)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) Label(stringResource(R.string.recordatorios_km_optional), Typography.body, c.textMuted, 1)
            BasicTextField(
                value,
                { onChange(it.filter { ch -> ch in '0'..'9' }.take(ReminderKm.MAX_DIGITS)) },
                Modifier.fillMaxWidth().onFocusChanged { if (lift && it.isFocused) scope.launch { requester.bringIntoView(Rect(0f, 0f, 1f, room)) } },
                textStyle = Typography.body.copy(color = c.text),
                singleLine = true,
                cursorBrush = SolidColor(c.accent),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
            )
        }
        Label(stringResource(R.string.recordatorios_km), Typography.body, c.textMuted, 1, Modifier.padding(start = 8.dp))
    }
}

/** Calendario dentro de la tarjeta (sin ventanas emergentes): un mes a la vista, ‹ › para cambiarlo. */
@Composable
internal fun DatePicker(onPick: (Day) -> Unit) {
    val c = LocalColors.current
    val locale = currentLocale()
    val today = remember { Day.today() }
    var month by remember { mutableStateOf(Day(today.year, today.month, 1)) }
    val title = remember(month, locale) { SimpleDateFormat("LLLL yyyy", locale).format(month.toDate()).replaceFirstChar { it.uppercase(locale) } }
    // Primer día de la semana según la región (domingo en Colombia y México, lunes en España).
    val firstWeekday = remember(locale) { Calendar.getInstance(locale).firstDayOfWeek }
    val weekdays = remember(locale, firstWeekday) {
        val narrow = SimpleDateFormat("EEEEE", locale)
        // 1970-01-04 fue domingo: de ahí se cuentan los siete.
        (0 until 7).map { i -> narrow.format(Day.ofEpochDay(3L + (firstWeekday - 1 + i) % 7).toDate()) }
    }
    val length = (month.plusMonths(1).epochDay - month.epochDay).toInt()
    // Casillas vacías antes del día 1 (1970-01-01 fue jueves = 5 en Calendar).
    val lead = ((month.epochDay + 4).mod(7L).toInt() + 1 - firstWeekday).mod(7)
    Column(Modifier.widthIn(max = 420.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(Icons.chevron, { month = month.plusMonths(-1) }, Modifier.graphicsLayer(scaleX = -1f), size = 48.dp)
            Label(title, Typography.subtitle, maxLines = 1, modifier = Modifier.weight(1f).padding(horizontal = 8.dp))
            IconButton(Icons.chevron, { month = month.plusMonths(1) }, size = 48.dp)
        }
        Row(Modifier.fillMaxWidth()) {
            for (w in weekdays) Label(w, Typography.small.copy(textAlign = TextAlign.Center), c.textMuted, 1, Modifier.weight(1f))
        }
        for (week in 0 until (lead + length + 6) / 7) {
            Row(Modifier.fillMaxWidth()) {
                for (col in 0 until 7) {
                    val n = week * 7 + col - lead + 1
                    Box(Modifier.weight(1f).height(48.dp), contentAlignment = Alignment.Center) {
                        if (n in 1..length) {
                            val day = Day(month.year, month.month, n)
                            val isToday = day == today
                            Box(
                                Modifier.size(44.dp).clip(CircleShape)
                                    .background(if (isToday) c.surfaceHigh else Color.Transparent)
                                    .clickable { onPick(day) },
                                contentAlignment = Alignment.Center,
                            ) { Label("$n", Typography.body, if (day < today) c.textMuted else c.text, 1) }
                        }
                    }
                }
            }
        }
    }
}
