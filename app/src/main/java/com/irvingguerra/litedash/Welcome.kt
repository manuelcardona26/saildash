package com.irvingguerra.litedash

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.irvingguerra.litedash.core.ui.Icon
import com.irvingguerra.litedash.core.ui.ice.BrandTitle
import com.irvingguerra.litedash.core.ui.ice.IceBackdrop
import com.irvingguerra.litedash.core.ui.ice.IceButton
import com.irvingguerra.litedash.core.ui.ice.IceButtonKind
import com.irvingguerra.litedash.core.ui.ice.IceFrame
import com.irvingguerra.litedash.core.ui.ice.IceIcons
import com.irvingguerra.litedash.core.ui.ice.IcePill
import com.irvingguerra.litedash.core.ui.ice.IceSearchField
import com.irvingguerra.litedash.core.ui.ice.IceSwatch
import com.irvingguerra.litedash.core.ui.ice.IceText
import com.irvingguerra.litedash.core.ui.ice.IceText2
import com.irvingguerra.litedash.core.ui.ice.IceType
import com.irvingguerra.litedash.core.ui.ice.LocalIce
import com.irvingguerra.litedash.core.ui.ice.Outfit
import com.irvingguerra.litedash.core.ui.ice.RobotoText
import com.irvingguerra.litedash.core.ui.ice.glass
import com.irvingguerra.litedash.core.ui.ice.iceCard
import com.irvingguerra.litedash.core.ui.ice.softGlow
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Locale

/** ¿LiteDash ya es la pantalla de inicio? */
fun isHomeApp(context: Context): Boolean {
    // Android 10+: el rol del sistema. Sin esto, desde Android 11 las otras apps de
    // inicio no se ven y LiteDash creería que es la única.
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        runCatching { return context.getSystemService(RoleManager::class.java).isRoleHeld(RoleManager.ROLE_HOME) }
    }
    val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
    val resolved = context.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
    return resolved?.activityInfo?.packageName == context.packageName
}

/** Los colores del Sail para el primer paso: gris, blanco, plata y rojo (luz y fondo de la muestra, y el del carro). */
private data class SailColor(val name: Int, val light: Color, val deep: Color, val car: Color)

private val SAIL_COLORS = listOf(
    SailColor(R.string.p2_color_gray, Color(0xFFC9CFD6), Color(0xFF5B626B), Color(0xFF6B727B)),
    SailColor(R.string.p2_color_white, Color.White, Color(0xFFD5DBE3), Color(0xFFF2F4F7)),
    SailColor(R.string.p2_color_silver, Color(0xFFF2F5F8), Color(0xFF9AA3AD), Color(0xFFB8C0C9)),
    SailColor(R.string.p2_color_red, Color(0xFFFF8A8A), Color(0xFFA3161D), Color(0xFFB3202A)),
)

/** Las ciudades del segundo paso, en el orden del diseño (sus ids en pico_y_placa.json). */
private val WELCOME_CITIES = listOf("medellin", "bogota", "cali")

/**
 * Primer arranque (catalogo/bienvenida): tres pasos a la vista, todo con botones grandes y sin que
 * salga el teclado: 1) tu carro (nombre listo, la foto y el color), 2) pico y placa (ciudad y último
 * número de la placa) y 3) hacer SailDash la pantalla de inicio. El nombre "Otro" se escribe solo si
 * se toca. Todo se cambia después en Ajustes.
 */
@Composable
fun Welcome(d: Dependencies, revision: Int, onFinish: () -> Unit) {
    val prefs = d.prefs
    val ice = LocalIce.current
    var step by remember { mutableIntStateOf(prefs.welcomeStep.coerceIn(0, 2)) }
    fun goTo(n: Int) {
        step = n.coerceIn(0, 2)
        prefs.welcomeStep = step
    }
    Box(
        Modifier
            .fillMaxSize()
            .background(ice.bg)
            .clickable(remember { MutableInteractionSource() }, indication = null) {},
    ) {
        IceFrame(Modifier.safeDrawingPadding()) {
            IceBackdrop(R.drawable.bg_espacio)
            if (ice.dark) Image(painterResource(R.drawable.blackhole_small), null, Modifier.offset(470.dp, 40.dp).size(120.dp, 72.dp), alpha = 0.55f, contentScale = ContentScale.Fit)
            Hero(step)
            // El carro a la derecha, con su sombra y la etiqueta de cómo se llama.
            OwnerCarPicture(d, 548.dp, 4.dp, 440.dp, ground = listOf(520.dp, 120.dp, 500.dp, 170.dp))
            CarTag(prefs, Modifier.offset(676.dp, 236.dp))
            CarCard(d, Modifier.offset(28.dp, 278.dp).size(276.dp, 234.dp), state(0, step)) { goTo(0) }
            PlateCard(d, Modifier.offset(316.dp, 278.dp).size(408.dp, 234.dp), state(1, step)) { goTo(1) }
            HomeCard(revision, Modifier.offset(736.dp, 278.dp).size(260.dp, 234.dp), state(2, step)) { goTo(2) }
            BottomBar(step, onBack = { goTo(step - 1) }, onNext = { if (step < 2) goTo(step + 1) else onFinish() }, Modifier.align(Alignment.BottomStart))
        }
    }
}

private enum class StepState { DONE, CURRENT, PENDING }

private fun state(index: Int, step: Int) = when {
    index < step -> StepState.DONE
    index == step -> StepState.CURRENT
    else -> StepState.PENDING
}

/** `.hero` y `.prog`: la marca, "Bienvenido a SailDash", la ayuda y en qué paso vas. */
@Composable
private fun Hero(step: Int) {
    val ice = LocalIce.current
    Column(Modifier.offset(40.dp, 30.dp).width(470.dp)) {
        BrandTitle("Chevrolet Sail")
        val title = buildAnnotatedString {
            append(stringResource(R.string.p2_welcome_title))
            append(' ')
            withStyle(SpanStyle(brush = Brush.horizontalGradient(listOf(Color(0xFFE6F6FF), Color(0xFF8FD0FF))), fontWeight = FontWeight.Normal)) { append("SailDash") }
        }
        IceText(title, IceText2.h1.copy(fontSize = 44.sp, fontWeight = FontWeight.ExtraLight, letterSpacing = (-0.005).em, lineHeight = 1.05.em), ice.title, modifier = Modifier.padding(top = 26.dp))
        IceText(stringResource(R.string.p2_welcome_text), IceType.body.copy(fontSize = 16.sp, lineHeight = 1.45.em), ice.ink2, maxLines = 2, modifier = Modifier.padding(top = 14.dp).width(430.dp))
    }
    Row(Modifier.offset(40.dp, 206.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        for (i in 0..2) {
            val s = state(i, step)
            Box(
                Modifier
                    .then(
                        when (s) {
                            StepState.DONE -> Modifier.softGlow(Color(126, 240, 194).copy(alpha = 0.4f), 5.dp)
                            StepState.CURRENT -> Modifier.softGlow(ice.iceGlow.copy(alpha = 0.4f), 5.dp)
                            StepState.PENDING -> Modifier
                        },
                    )
                    .size(66.dp, 4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(
                        when (s) {
                            StepState.DONE -> Brush.horizontalGradient(listOf(Color(0xFF8FF0CF), Color(0xFF8FF0CF)))
                            StepState.CURRENT -> Brush.horizontalGradient(listOf(Color(0xFFD8F0FF), Color(0xFF7CC6FF)))
                            StepState.PENDING -> Brush.horizontalGradient(listOf(Color(200, 225, 255).copy(alpha = 0.18f), Color(200, 225, 255).copy(alpha = 0.18f)))
                        },
                    ),
            )
        }
        IceText(stringResource(R.string.p2_welcome_step, step + 1), IceType.small, ice.ink2, modifier = Modifier.padding(start = 6.dp))
    }
}

/** `.tag`: "Mi Sail · gris" sobre el carro. */
@Composable
private fun CarTag(prefs: Preferences, modifier: Modifier) {
    val ice = LocalIce.current
    val color = SAIL_COLORS.minByOrNull { distance(it.car, prefs.carColor) } ?: SAIL_COLORS[0]
    val shape = RoundedCornerShape(15.dp)
    Row(
        modifier.height(30.dp).clip(shape).background(Color(6, 12, 22).copy(alpha = 0.6f)).border(1.dp, ice.line, shape).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(Brush.radialGradient(listOf(color.light, color.deep))).border(1.dp, Color.White.copy(alpha = 0.35f), CircleShape))
        IceText(
            buildAnnotatedString {
                append(prefs.carName?.takeIf { it.isNotBlank() } ?: stringResource(R.string.p2_name_mine))
                withStyle(SpanStyle(color = ice.ink2, fontWeight = FontWeight.Normal)) { append(" · " + stringResource(color.name)) }
            },
            IceText2.button.copy(fontSize = 14.sp, letterSpacing = 0.em), Color.White,
        )
    }
}

private fun distance(a: Color, b: Color) = (a.red - b.red) * (a.red - b.red) + (a.green - b.green) * (a.green - b.green) + (a.blue - b.blue) * (a.blue - b.blue)

/** El vidrio de cada paso: el de ahora resaltado (`.st.c`), los demás normales. */
@Composable
private fun StepCard(modifier: Modifier, s: StepState, onTap: () -> Unit, end: Dp = 18.dp, content: @Composable () -> Unit) {
    val m = if (s == StepState.CURRENT) modifier
        .softGlow(Color(120, 190, 255).copy(alpha = 0.12f), 18.dp)
        .glass(LocalIce.current.glass, Color(170, 215, 255).copy(alpha = 0.45f), Color(235, 246, 255).copy(alpha = 0.18f), 20.dp)
    else modifier.iceCard(20.dp)
    Box(m.clickable(remember { MutableInteractionSource() }, indication = null, onClick = onTap).padding(start = 18.dp, end = end, top = 16.dp, bottom = 16.dp)) { content() }
}

/** `.sh`: el número (o la palomita), el título y lo de la derecha. */
@Composable
private fun StepHeader(number: Int, title: String, s: StepState, side: String?) {
    val ice = LocalIce.current
    Row(Modifier.fillMaxWidth().height(26.dp), verticalAlignment = Alignment.CenterVertically) {
        val badge = Modifier.size(26.dp).clip(CircleShape)
        when (s) {
            StepState.DONE -> Box(badge.background(Color(0xFF8FF0CF)), contentAlignment = Alignment.Center) { Icon(IceIcons.check, Color(0xFF062218), 15.dp) }
            StepState.CURRENT -> Box(
                Modifier.softGlow(Color(170, 220, 255).copy(alpha = 0.45f), 6.dp).then(badge).background(Brush.verticalGradient(listOf(Color.White, Color(0xFFBFE6FF)))),
                contentAlignment = Alignment.Center,
            ) { IceText(number.toString(), IceText2.button.copy(fontSize = 14.sp), Color(0xFF0A1322)) }
            StepState.PENDING -> Box(badge.border(1.5.dp, Color(190, 225, 255).copy(alpha = 0.45f), CircleShape), contentAlignment = Alignment.Center) {
                IceText(number.toString(), IceText2.button.copy(fontSize = 14.sp), ice.ink2)
            }
        }
        Spacer(Modifier.width(10.dp))
        IceText(title, IceText2.h3, Color.White, modifier = Modifier.weight(1f))
        val text = if (s == StepState.DONE) stringResource(R.string.p2_welcome_done) else side
        if (text != null) IceText(text, IceType.small, if (s == StepState.DONE) Color(0xFF8FF0CF) else ice.ink3)
    }
}

/** Paso 1: el nombre (Mi Sail, El Sail u otro), la foto realista o la tuya, y el color. */
@Composable
private fun CarCard(d: Dependencies, modifier: Modifier, s: StepState, onTap: () -> Unit) {
    val prefs = d.prefs
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val owner = remember { OwnerCar.of(context) }
    LaunchedEffect(owner) { owner.load() }
    val mine = stringResource(R.string.p2_name_mine)
    val the = stringResource(R.string.p2_name_the)
    // Para que el nombre de fábrica aparezca ya elegido (sin teclado).
    LaunchedEffect(Unit) { if (prefs.carName.isNullOrBlank()) prefs.carName = mine }
    var typing by remember { mutableStateOf(false) }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) scope.launch { owner.setFront(uri) }
    }
    val realistic = owner.image != null && !owner.custom
    // Como en el mockup, los botones pueden llegar casi al borde de la tarjeta.
    StepCard(modifier, s, onTap, end = 8.dp) {
        Column {
            Box(Modifier.padding(end = 10.dp)) { StepHeader(1, stringResource(R.string.your_car), s, null) }
            if (typing) {
                Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // El teclado sale solo si se toca el campo.
                    IceSearchField(prefs.carName ?: "", { prefs.carName = it.take(24).ifBlank { null } }, stringResource(R.string.my_car), Modifier.weight(1f), height = 44.dp, icon = IceIcons.pencil)
                    IcePill("", false, { typing = false }, icon = IceIcons.check, padding = 12.dp)
                }
            } else {
                Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val name = prefs.carName
                    IcePill(mine, name == mine, { prefs.carName = mine }, padding = 14.dp)
                    IcePill(the, name == the, { prefs.carName = the }, padding = 14.dp)
                    val other = name != null && name != mine && name != the
                    IcePill(if (other) name!!.take(8) else stringResource(R.string.p2_name_other), other, { typing = true }, icon = IceIcons.pencil, padding = 14.dp)
                }
            }
            Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (owner.hasPrivateAssets() || realistic) IcePill(stringResource(R.string.p2_photo_real), realistic, { scope.launch { owner.restoreFromAssets() } }, icon = IceIcons.sparkle, padding = 14.dp)
                IcePill(stringResource(R.string.p2_photo_mine), owner.custom, { pick.launch("image/*") }, icon = IceIcons.photo, padding = 14.dp)
            }
            Row(Modifier.padding(top = 10.dp, start = 4.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                val chosen = SAIL_COLORS.minByOrNull { distance(it.car, prefs.carColor) }
                for (c in SAIL_COLORS) IceSwatch(c.light, c.deep, on = c == chosen, onClick = { prefs.carColor = c.car }, checkColor = Color.White)
            }
        }
    }
}

/** Paso 2: la ciudad y el último número de la placa (botones grandes) y qué día le toca. */
@Composable
private fun PlateCard(d: Dependencies, modifier: Modifier, s: StepState, onTap: () -> Unit) {
    val ice = LocalIce.current
    val repo = d.picoPlaca
    StepCard(modifier, s, onTap) {
        Column {
            StepHeader(2, stringResource(R.string.p2_pico_title), s, stringResource(R.string.p2_pico_side))
            Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Column(Modifier.width(112.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (id in WELCOME_CITIES) {
                        val city = repo.rules.cities.firstOrNull { it.id == id } ?: continue
                        val on = repo.cityId == id
                        IcePill(city.name, on, { repo.pickCity(id); repo.turn(true); onTap() }, Modifier.fillMaxWidth(), icon = if (on) IceIcons.pin else null, padding = 12.dp)
                    }
                }
                Column {
                    IceText(stringResource(R.string.p2_pico_digit), IceText2.sub, ice.ink2)
                    Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        for (row in listOf(listOf(1, 2, 3, 4, 5), listOf(6, 7, 8, 9, 0))) Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            for (n in row) DigitKey(n, repo.digit == n) { repo.pickDigit(n); repo.turn(true); onTap() }
                        }
                    }
                    PicoResult(d, Modifier.padding(top = 10.dp))
                }
            }
        }
    }
}

/** `.dg span`: 44 × 44, Outfit 19; el elegido en blanco hielo con brillo. */
@Composable
private fun DigitKey(n: Int, on: Boolean, onClick: () -> Unit) {
    val ice = LocalIce.current
    val shape = RoundedCornerShape(13.dp)
    Box(
        Modifier
            .then(if (on) Modifier.softGlow(Color(170, 220, 255).copy(alpha = 0.35f), 10.dp) else Modifier)
            .size(44.dp)
            .clip(shape)
            .then(
                if (on) Modifier.background(Brush.verticalGradient(listOf(Color.White, Color(0xFFCDEAFF))))
                else Modifier.background(Color(170, 210, 255).copy(alpha = 0.05f)).border(1.dp, ice.line, shape),
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        IceText(n.toString(), androidx.compose.ui.text.TextStyle(fontFamily = Outfit, fontSize = 19.sp, fontWeight = if (on) FontWeight.Medium else FontWeight.Normal), if (on) Color(0xFF0A1322) else ice.ink)
    }
}

/** `.res`: "Lunes 5:00–20:00 · hoy sí" (el próximo día con pico y placa para esa placa). */
@Composable
private fun PicoResult(d: Dependencies, modifier: Modifier) {
    val repo = d.picoPlaca
    val city = repo.city ?: return
    if (repo.digit !in 0..9) return
    val next = remember(repo.cityId, repo.digit, repo.rules) { repo.upcoming(count = 1).firstOrNull() }
    val locale = currentLocale()
    val today = Day.today()
    val dayName = next?.let {
        val cal = Calendar.getInstance().apply { set(it.year, it.month - 1, it.day) }
        cal.getDisplayName(Calendar.DAY_OF_WEEK, Calendar.LONG, locale)?.replaceFirstChar { c -> c.titlecase(locale) }
    }
    val hours = city.hours.joinToString(", ") { "${PicoHours.hhmm(it.start)}–${PicoHours.hhmm(it.end)}" }
    val shape = RoundedCornerShape(15.dp)
    Row(
        modifier.height(30.dp).clip(shape).background(Color(6, 12, 22).copy(alpha = 0.45f)).border(1.dp, Color(255, 214, 120).copy(alpha = 0.32f), shape).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(IceIcons.plate, Color(0xFFFFD47A), 18.dp)
        IceText(
            buildAnnotatedString {
                append(if (dayName != null) "$dayName $hours · " else stringResource(R.string.p2_pico_none) + " · ")
                withStyle(SpanStyle(color = Color.White, fontWeight = FontWeight.Medium)) {
                    append(stringResource(if (next == today) R.string.p2_pico_today_yes else R.string.p2_pico_today_no))
                }
            },
            androidx.compose.ui.text.TextStyle(fontFamily = RobotoText, fontSize = 13.sp), Color(240, 232, 214).copy(alpha = 0.95f),
        )
    }
}

/** Paso 3: hacer SailDash la pantalla de inicio. */
@Composable
private fun HomeCard(revision: Int, modifier: Modifier, s: StepState, onTap: () -> Unit) {
    val ice = LocalIce.current
    val context = LocalContext.current
    val (request, returns) = rememberRequestHome()
    val already = remember(revision, returns) { isHomeApp(context) }
    StepCard(modifier, s, onTap) {
        Box(Modifier.fillMaxSize()) {
            Column {
                StepHeader(3, stringResource(R.string.p2_home_title), if (already && s != StepState.CURRENT) StepState.DONE else s, null)
                Box(
                    Modifier.padding(top = 14.dp).size(60.dp).clip(RoundedCornerShape(18.dp))
                        .background(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.14f), Color.White.copy(alpha = 0.03f)), center = Offset(21f, 18f)))
                        .border(1.dp, ice.line, RoundedCornerShape(18.dp)),
                    contentAlignment = Alignment.Center,
                ) { Icon(IceIcons.house, Color(0xFF8FF0CF), 30.dp) }
                IceText(stringResource(R.string.p2_home_text), androidx.compose.ui.text.TextStyle(fontFamily = RobotoText, fontSize = 14.sp, lineHeight = 1.4.em), ice.ink2, maxLines = 3, modifier = Modifier.padding(top = 12.dp))
            }
            IceButton(
                stringResource(if (already) R.string.p2_home_already else R.string.p2_home_make),
                { if (!already) request(); onTap() },
                Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
                icon = if (already) IceIcons.check else null,
                textColor = if (already) Color(0xFF8FF0CF) else null,
            )
        }
    }
}

/** `.wbar`: la nota de Ajustes, Atrás y Siguiente (en el último paso, Empezar). */
@Composable
private fun BottomBar(step: Int, onBack: () -> Unit, onNext: () -> Unit, modifier: Modifier) {
    val ice = LocalIce.current
    Row(
        modifier
            .fillMaxWidth()
            .height(72.dp)
            .drawBehind {
                drawRect(Brush.verticalGradient(listOf(Color(9, 15, 27).copy(alpha = 0.90f), Color(4, 8, 15).copy(alpha = 0.97f))))
                drawRect(Color(190, 225, 255).copy(alpha = 0.12f), size = androidx.compose.ui.geometry.Size(size.width, 1.dp.toPx()))
                drawRect(
                    Brush.horizontalGradient(0f to Color.Transparent, 0.3f to Color(190, 228, 255).copy(alpha = 0.45f), 0.7f to Color(190, 228, 255).copy(alpha = 0.45f), 1f to Color.Transparent),
                    size = androidx.compose.ui.geometry.Size(size.width, 1.dp.toPx()),
                )
            }
            .padding(start = 32.dp, end = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(IceIcons.info, ice.ink3, 18.dp)
        IceText(stringResource(R.string.welcome_change_later), IceType.body.copy(fontSize = 14.sp), ice.ink3, modifier = Modifier.weight(1f))
        if (step > 0) IceButton(stringResource(R.string.back), onBack, kind = IceButtonKind.GHOST)
        IceButton(
            stringResource(if (step < 2) R.string.next else R.string.p2_welcome_start), onNext,
            kind = IceButtonKind.PRIMARY, icon = IceIcons.chevronSmall, iconAfter = true, padding = 28.dp,
        )
    }
}

/**
 * Pide ser la pantalla de inicio: el diálogo del sistema (Android 10+) y, si la
 * pantalla no lo tiene, sus ajustes de inicio o de apps predeterminadas.
 * Devuelve la acción y un contador que sube al volver del diálogo.
 */
@Composable
fun rememberRequestHome(): Pair<() -> Unit, Int> {
    val context = LocalContext.current
    var returns by remember { mutableIntStateOf(0) }
    val requestRole = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { returns++ }
    val request = {
        val requested = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && runCatching {
            val roleManager = context.getSystemService(RoleManager::class.java)
            if (!roleManager.isRoleAvailable(RoleManager.ROLE_HOME)) return@runCatching false
            requestRole.launch(roleManager.createRequestRoleIntent(RoleManager.ROLE_HOME))
            true
        }.getOrDefault(false)
        if (!requested) {
            // "Apps predeterminadas" existe desde Android 7.
            val defaults = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS else null
            listOfNotNull(Settings.ACTION_HOME_SETTINGS, defaults, Settings.ACTION_SETTINGS)
                .firstOrNull { action ->
                    runCatching { context.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess
                }
        }
        Unit
    }
    return request to returns
}
