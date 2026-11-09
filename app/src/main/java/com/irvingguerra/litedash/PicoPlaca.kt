package com.irvingguerra.litedash

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import com.irvingguerra.litedash.core.ui.Button
import com.irvingguerra.litedash.core.ui.Https
import com.irvingguerra.litedash.core.ui.Icons
import com.irvingguerra.litedash.core.ui.Label
import com.irvingguerra.litedash.core.ui.LocalColors
import com.irvingguerra.litedash.core.ui.Segmented
import com.irvingguerra.litedash.core.ui.SwitchRow
import com.irvingguerra.litedash.core.ui.TextInput
import com.irvingguerra.litedash.core.ui.Typography
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Calendar

/** Lo que el inicio debe decir hoy sobre el pico y placa (null = nada). */
data class PicoStatus(
    /** true = hoy; false = mañana. */
    val today: Boolean,
    /** Ahora mismo está en horario de restricción. */
    val now: Boolean,
    val hours: List<PicoHours>,
    val city: String,
    /** Las reglas pasaron su fecha de vigencia: hay que actualizarlas. */
    val outdated: Boolean,
)

/**
 * SailDash: pico y placa. Las reglas vienen dentro de la app (res/raw/pico_y_placa.json) y, si
 * hay repositorio configurado, se bajan las más nuevas de GitHub ([remoteUrl]) para no tener que
 * reinstalar cuando la alcaldía cambia la rotación. Nada corre en segundo plano: se revisa al
 * ver el inicio, que es justo lo que pasa al encender el carro.
 */
class PicoPlacaRepository(private val context: Context, private val remoteUrl: String) {
    private val store = context.getSharedPreferences(STORE, Context.MODE_PRIVATE)
    private val cached get() = File(context.filesDir, CACHE_FILE)

    val remoteAvailable get() = remoteUrl.isNotBlank()

    var enabled by mutableStateOf(store.getBoolean(KEY_ENABLED, false))
        private set
    var cityId by mutableStateOf(store.getString(KEY_CITY, null))
        private set
    /** Último dígito de la placa, 0–9; -1 = sin elegir. */
    var digit by mutableIntStateOf(store.getInt(KEY_DIGIT, -1))
        private set
    /** "Otra ciudad": los días (1 = lunes … 6 = sábado) y el horario que el usuario puso a mano. */
    var customDays by mutableStateOf(store.getString(KEY_CUSTOM_DAYS, "")!!.split(',').mapNotNull { it.toIntOrNull() }.toSet())
        private set
    var customHours by mutableStateOf(PicoHours.parse(store.getString(KEY_CUSTOM_HOURS, "06:00-21:00")!!) ?: PicoHours(360, 1260))
        private set
    /** El aviso que se cerró con "Entendido" (fecha + hoy/mañana): no vuelve a salir ese día. */
    private var dismissed by mutableStateOf(store.getString(KEY_DISMISSED, null))

    var rules by mutableStateOf(load())
        private set

    fun turn(on: Boolean) { enabled = on; store.edit { putBoolean(KEY_ENABLED, on) } }
    fun pickCity(id: String) { cityId = id; store.edit { putString(KEY_CITY, id) } }
    fun pickDigit(d: Int) { digit = d; store.edit { putInt(KEY_DIGIT, d) } }
    fun toggleCustomDay(day: Int) {
        customDays = if (day in customDays) customDays - day else customDays + day
        store.edit { putString(KEY_CUSTOM_DAYS, customDays.sorted().joinToString(",")) }
    }
    fun pickCustomHours(h: PicoHours) { customHours = h; store.edit { putString(KEY_CUSTOM_HOURS, "${PicoHours.hhmm(h.start)}-${PicoHours.hhmm(h.end)}") } }

    /** La regla elegida: una ciudad del archivo o la hecha a mano ("Otra ciudad"). */
    val city: PicoCity?
        get() = when (val id = cityId) {
            null -> null
            CUSTOM -> PicoCity(CUSTOM, context.getString(R.string.pico_custom_city), listOf(customHours), days = customDays.associateWith { (0..9).toSet() })
            else -> rules.city(id)
        }

    /** ¿Ya está todo para avisar? En "Otra ciudad" el dígito no hace falta. */
    val ready get() = enabled && city != null && (cityId == CUSTOM || digit in 0..9)

    private fun restricted(c: PicoCity, day: Day) = if (c.id == CUSTOM) c.digitsOn(day).isNotEmpty() else c.restricted(day, digit)

    /** Qué decir en el inicio ahora: hoy (si no ha terminado el horario) o, desde las 17:00, mañana. */
    fun status(now: Calendar = Calendar.getInstance()): PicoStatus? {
        if (!ready) return null
        val c = city ?: return null
        val today = Day(now.get(Calendar.YEAR), now.get(Calendar.MONTH) + 1, now.get(Calendar.DAY_OF_MONTH))
        val minute = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
        val end = c.hours.maxOfOrNull { it.end } ?: return null
        if (restricted(c, today) && minute < end) {
            return PicoStatus(true, c.hours.any { it.contains(minute) }, c.hours, c.name, c.outdated(today))
        }
        val tomorrow = today.plusDays(1)
        if (minute >= EVENING && restricted(c, tomorrow)) return PicoStatus(false, false, c.hours, c.name, c.outdated(tomorrow))
        return null
    }

    /** Los próximos [count] días con restricción (para Ajustes). */
    fun upcoming(from: Day = Day.today(), count: Int = 4): List<Day> {
        val c = city ?: return emptyList()
        if (cityId != CUSTOM && digit !in 0..9) return emptyList()
        return generateSequence(from) { it.plusDays(1) }.take(60).filter { restricted(c, it) }.take(count).toList()
    }

    fun dismissKey(s: PicoStatus) = "${Day.today()}:${s.today}"
    fun isDismissed(s: PicoStatus) = dismissed == dismissKey(s)
    fun dismiss(s: PicoStatus) { dismissed = dismissKey(s); store.edit { putString(KEY_DISMISSED, dismissed) } }

    /** Baja las reglas de GitHub si son más nuevas. true = cambiaron. Fuera del hilo principal. */
    suspend fun refresh(): Boolean = withContext(Dispatchers.IO) {
        if (!remoteAvailable) return@withContext false
        val text = runCatching { Https.get(context, remoteUrl).run { try { inputStream.bufferedReader().readText() } finally { disconnect() } } }.getOrNull()
            ?: return@withContext false
        val fresh = runCatching { PicoRules.parse(text) }.getOrNull() ?: return@withContext false
        store.edit { putLong(KEY_LAST_CHECK, System.currentTimeMillis()) }
        if (fresh.cities.isEmpty() || fresh.version < rules.version) return@withContext false
        if (fresh.version == rules.version && fresh.updated == rules.updated) return@withContext false
        cached.writeText(text)
        withContext(Dispatchers.Main) { rules = fresh }
        true
    }

    /** Revisión en silencio, a lo más cada [CHECK_EVERY_MS]. */
    suspend fun refreshQuietly() {
        if (!enabled || !remoteAvailable) return
        if (System.currentTimeMillis() - store.getLong(KEY_LAST_CHECK, 0L) < CHECK_EVERY_MS) return
        refresh()
    }

    /** Las del archivo bajado si son más nuevas que las que trae la app; si no, las de la app. */
    private fun load(): PicoRules {
        val bundled = runCatching {
            context.resources.openRawResource(R.raw.pico_y_placa).bufferedReader().use { PicoRules.parse(it.readText()) }
        }.getOrDefault(PicoRules.EMPTY)
        val downloaded = runCatching { if (cached.exists()) PicoRules.parse(cached.readText()) else null }.getOrNull()
        return if (downloaded != null && downloaded.version >= bundled.version && downloaded.cities.isNotEmpty()) downloaded else bundled
    }

    companion object {
        const val CUSTOM = "custom"
        private const val STORE = "pico_placa"
        private const val CACHE_FILE = "pico_y_placa.json"
        private const val KEY_ENABLED = "enabled"
        private const val KEY_CITY = "city"
        private const val KEY_DIGIT = "digit"
        private const val KEY_CUSTOM_DAYS = "custom_days"
        private const val KEY_CUSTOM_HOURS = "custom_hours"
        private const val KEY_DISMISSED = "dismissed"
        private const val KEY_LAST_CHECK = "last_check"
        private const val CHECK_EVERY_MS = 3L * 24 * 60 * 60 * 1000
        /** Desde esta hora (17:00) se avisa del pico y placa de mañana. */
        private const val EVENING = 17 * 60
    }
}

/** Aviso en el inicio: "Hoy tienes pico y placa · 6:00–21:00". Se revisa cada minuto mientras se ve. */
@Composable
fun PicoPlacaBanner(repo: PicoPlacaRepository) {
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        repo.refreshQuietly()
        while (true) { delay(60_000); tick++ }
    }
    val status = remember(tick, repo.enabled, repo.cityId, repo.digit, repo.rules, repo.customDays, repo.customHours) { repo.status() } ?: return
    if (repo.isDismissed(status) && !status.now) return
    val c = LocalColors.current
    val hours = status.hours.joinToString(", ")
    val text = when {
        status.now -> stringResource(R.string.pico_now, hours)
        status.today -> stringResource(R.string.pico_today, hours)
        else -> stringResource(R.string.pico_tomorrow, hours)
    }
    Notice(text, Icons.warning, if (status.now) c.danger else c.warning) {
        if (!status.now) Button(stringResource(R.string.pico_ok), { repo.dismiss(status) }, primary = true)
    }
}

/** Ajustes → Tu auto → Pico y placa. */
@Composable
fun PicoPlacaSettings(repo: PicoPlacaRepository) {
    val c = LocalColors.current
    val scope = rememberCoroutineScope()
    var checking by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<String?>(null) }
    val changed = stringResource(R.string.pico_rules_updated)
    val same = stringResource(R.string.pico_rules_same)

    SwitchRow(stringResource(R.string.pico_enable), repo.enabled, repo::turn, stringResource(R.string.pico_enable_help))
    if (!repo.enabled) return

    Label(stringResource(R.string.pico_city), Typography.label, c.textMuted)
    val options = repo.rules.cities.map { it.id to it.name } + (PicoPlacaRepository.CUSTOM to stringResource(R.string.pico_custom_city))
    Segmented(options, repo.cityId ?: "", { repo.pickCity(it) })

    if (repo.cityId == PicoPlacaRepository.CUSTOM) {
        Label(stringResource(R.string.pico_custom_days), Typography.label, c.textMuted)
        val names = listOf(R.string.pico_mon, R.string.pico_tue, R.string.pico_wed, R.string.pico_thu, R.string.pico_fri, R.string.pico_sat)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            names.forEachIndexed { i, n -> Button(stringResource(n), { repo.toggleCustomDay(i + 1) }, primary = (i + 1) in repo.customDays) }
        }
        Label(stringResource(R.string.pico_custom_hours), Typography.label, c.textMuted)
        var start by remember { mutableStateOf(PicoHours.hhmm(repo.customHours.start)) }
        var end by remember { mutableStateOf(PicoHours.hhmm(repo.customHours.end)) }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TextInput(start, { start = it.take(5); PicoHours.parse("$start-$end")?.let(repo::pickCustomHours) }, "06:00", Modifier.width(110.dp))
            Label("–", Typography.subtitle)
            TextInput(end, { end = it.take(5); PicoHours.parse("$start-$end")?.let(repo::pickCustomHours) }, "21:00", Modifier.width(110.dp))
        }
    } else {
        Label(stringResource(R.string.pico_digit), Typography.label, c.textMuted)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            for (d in listOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 0)) Button("$d", { repo.pickDigit(d) }, primary = repo.digit == d)
        }
    }

    val city = repo.city ?: return
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Label(stringResource(R.string.pico_hours, city.hours.joinToString(", ")), Typography.body)
        val next = repo.upcoming()
        if (next.isNotEmpty()) {
            Label(stringResource(R.string.pico_next, next.joinToString(" · ") { shortDate(it) }), Typography.body, c.accent)
        } else if (repo.cityId == PicoPlacaRepository.CUSTOM || repo.digit in 0..9) {
            Label(stringResource(R.string.pico_none_soon), Typography.body, c.textMuted)
        }
        val today = Day.today()
        if (city.outdated(today)) Label(stringResource(R.string.pico_outdated), Typography.small, c.danger)
        else city.to?.let { Label(stringResource(R.string.pico_valid_until, shortDate(it)), Typography.small, c.textMuted) }
    }

    if (repo.remoteAvailable) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Button(stringResource(if (checking) R.string.pico_checking else R.string.pico_check), {
            if (!checking) scope.launch {
                checking = true
                result = if (repo.refresh()) changed else same
                checking = false
            }
        })
        result?.let { Label(it, Typography.small, c.textMuted) }
    }
}

/** "lun 9 nov". */
private fun shortDate(d: Day): String =
    java.text.SimpleDateFormat("EEE d MMM", java.util.Locale("es", "CO")).format(d.toDate())
