// Ajustes: secciones, menú, pestañas y la tarjeta compartida (diseño C, catalogo/ajustes).
package com.irvingguerra.litedash

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.irvingguerra.litedash.core.ui.Icon
import com.irvingguerra.litedash.core.ui.Segmented
import com.irvingguerra.litedash.core.ui.ice.IceIcons
import com.irvingguerra.litedash.core.ui.ice.IceText
import com.irvingguerra.litedash.core.ui.ice.IceText2
import com.irvingguerra.litedash.core.ui.ice.LocalIce
import com.irvingguerra.litedash.core.ui.ice.iceCard
import com.irvingguerra.litedash.core.ui.ice.iceSelected
import com.irvingguerra.litedash.core.ui.ice.iceSheet
import com.irvingguerra.litedash.core.ui.ice.softGlow
import com.irvingguerra.litedash.feature.apps.App

/** Secciones de Ajustes en el orden del menú: cada cosa vive en un solo lugar. */
enum class SettingsSection(val title: Int) {
    CAR(R.string.your_car), APPEARANCE(R.string.appearance),
    // SailDash: el ecualizador de las apps (EqScreen.kt).
    SOUND(R.string.ecualizador_section),
    APPS(R.string.apps_section), WORKSHOP(R.string.workshop),
    TIRES(R.string.tires), WHEEL(R.string.wheel), MODULES(R.string.modules), SYSTEM(R.string.system),
}

/**
 * Las secciones de un módulo apagado no salen en el menú (Llantas sin fuente); la que ya está
 * abierta sí (al prender Llantas se entra a elegir su fuente). Taller sale siempre (como en el
 * diseño): con el OBD apagado dice cómo prenderlo.
 */
private fun SettingsSection.shown(d: Dependencies, current: SettingsSection) = this == current || when (this) {
    SettingsSection.TIRES -> d.hasTpmsSource
    else -> true
}

/** Los íconos del menú (los del mockup de Ajustes). */
private fun SettingsSection.icon() = when (this) {
    SettingsSection.CAR -> IceIcons.car
    SettingsSection.APPEARANCE -> IceIcons.palette
    SettingsSection.SOUND -> IceIcons.speaker
    SettingsSection.APPS -> IceIcons.apps
    SettingsSection.WORKSHOP -> IceIcons.wrench
    SettingsSection.TIRES -> IceIcons.tire
    SettingsSection.WHEEL -> IceIcons.steering
    SettingsSection.MODULES -> IceIcons.modules
    SettingsSection.SYSTEM -> IceIcons.chip
}

/**
 * Ajustes en el diseño C (catalogo/ajustes): la hoja de vidrio sobre el espacio, el menú de
 * secciones a la izquierda y las tarjetas a la derecha, con el dock abajo (Ajustes encendido).
 */
@Composable
internal fun SettingsPanel(
    d: Dependencies,
    apps: List<App>,
    nav: ShellNav,
    revision: Int,
    onEdit: () -> Unit,
    onTemplates: () -> Unit,
    start: SettingsSection = SettingsSection.CAR,
    /** Las llantas a pantalla completa (desde la tarjeta de Salud). */
    onTires: () -> Unit = {},
) {
    var section by rememberSaveable { mutableStateOf(start) }
    LaunchedEffect(start) { section = start }
    IceScreen(d, apps, Dock.SETTINGS, nav) {
        // .sheet: left 24, right 24, top 16, bottom 88.
        Box(Modifier.offset(24.dp, 16.dp).size(976.dp, 496.dp).iceSheet()) {
            SettingsMenu(d, section) { section = it }
            Box(Modifier.offset(236.dp, 0.dp).size(740.dp, 496.dp)) {
                SettingsHeader(stringResource(section.title), if (section == SettingsSection.SOUND) ({ EqHeaderTrailing() }) else null)
                // Las tarjetas: desde 84 dp, 24 dp a la izquierda y 22 a la derecha.
                Column(
                    Modifier
                        .offset(0.dp, 84.dp)
                        .size(740.dp, 412.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(start = 24.dp, end = 22.dp, bottom = 22.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    when (section) {
                        SettingsSection.APPEARANCE -> Appearance(d, revision, onEdit, onTemplates, onTires = { section = SettingsSection.TIRES })
                        SettingsSection.CAR -> CarSettings(d)
                        SettingsSection.APPS -> AppsSettings(d)
                        SettingsSection.WHEEL -> {
                            SettingsCard(help = stringResource(R.string.wheel_help)) { WheelSettings(d.wheel, d.prefs) }
                            SettingsCard(stringResource(R.string.wheel_assigned)) { WheelAssigned(d.prefs) }
                        }
                        // Con el OBD apagado el escáner ni se crea: se dice cómo prenderlo.
                        SettingsSection.WORKSHOP -> if (d.obdModule) Workshop(d, onTires) else SettingsCard(
                            stringResource(R.string.workshop), stringResource(R.string.settings_workshop_off),
                        ) { com.irvingguerra.litedash.core.ui.Button(stringResource(R.string.modules), { section = SettingsSection.MODULES }) }
                        SettingsSection.TIRES -> TiresSettings(d)
                        SettingsSection.SOUND -> EqSettingsSection()
                        SettingsSection.MODULES -> Modules(d, revision, open = { section = it })
                        SettingsSection.SYSTEM -> SystemSettings(d)
                    }
                }
            }
        }
    }
}

/** `.menu`: el título "Ajustes" y las secciones; la elegida en hielo con su punto brillante. */
@Composable
private fun SettingsMenu(d: Dependencies, section: SettingsSection, onPick: (SettingsSection) -> Unit) {
    val ice = LocalIce.current
    Column(
        Modifier
            .width(236.dp)
            .fillMaxHeight()
            .clip(RoundedCornerShape(topStart = 26.dp, bottomStart = 26.dp))
            .drawWithCache {
                val bg = Brush.verticalGradient(listOf(Color(4, 8, 16).copy(alpha = 0.32f), Color(4, 8, 16).copy(alpha = 0.18f)))
                val line = ice.line2
                onDrawBehind {
                    if (ice.dark) drawRect(bg)
                    drawRect(line, Offset(size.width - 1.dp.toPx(), 0f), Size(1.dp.toPx(), size.height))
                }
            }
            .verticalScroll(rememberScrollState())
            .padding(start = 14.dp, end = 12.dp, top = 22.dp),
    ) {
        // .menu h1: 300 28, margen 4 0 16 12.
        IceText(stringResource(R.string.settings), IceText2.h1, ice.title, modifier = Modifier.padding(start = 12.dp, top = 4.dp, bottom = 16.dp))
        for (s in SettingsSection.entries) if (s.shown(d, section)) {
            MenuEntry(s.icon(), stringResource(s.title), s == section) { onPick(s) }
            Spacer(Modifier.height(2.dp))
        }
    }
}

/** `.mi`: 46 dp, ícono de 22 y el texto; la elegida en hielo con su punto a la derecha. */
@Composable
private fun MenuEntry(icon: ImageVector, text: String, selected: Boolean, onClick: () -> Unit) {
    val ice = LocalIce.current
    val shape = RoundedCornerShape(15.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .height(46.dp)
            .then(if (selected) Modifier.iceSelected(15.dp, Color(170, 215, 255).copy(alpha = 0.36f), 0.95f) else Modifier)
            .clip(shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (selected && ice.dark) Box(Modifier.size(20.dp).softGlow(ice.iceGlow.copy(alpha = 0.22f), 6.dp))
            Icon(icon, if (selected) (if (ice.dark) Color(0xFFD8F0FF) else ice.ice2) else ice.ink3, 22.dp)
        }
        Spacer(Modifier.width(13.dp))
        IceText(text, IceText2.lbl, if (selected) ice.title else if (ice.dark) Color(222, 236, 252).copy(alpha = 0.80f) else ice.ink2, maxLines = 1, modifier = Modifier.weight(1f))
        if (selected) Box(
            Modifier
                .softGlow(Color(0xFF8FD0FF).copy(alpha = 0.6f), 4.dp)
                .size(6.dp)
                .clip(CircleShape)
                .background(if (ice.dark) Color(0xFFBFE6FF) else ice.ice),
        )
    }
}

/** `.mh`: "AJUSTES" chico, el nombre de la sección y "Se guarda solo" a la derecha. */
@Composable
private fun SettingsHeader(title: String, trailing: (@Composable androidx.compose.foundation.layout.RowScope.() -> Unit)?) {
    val ice = LocalIce.current
    Row(Modifier.offset(24.dp, 20.dp).size(694.dp, 48.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(if (trailing == null) Modifier.weight(1f) else Modifier, verticalArrangement = Arrangement.spacedBy(7.dp)) {
            IceText(stringResource(R.string.settings).uppercase(), IceText2.cap, ice.ink3)
            IceText(title, IceText2.h2, ice.title)
        }
        if (trailing != null) {
            trailing()
            return@Row
        }
        Icon(IceIcons.check, if (ice.dark) Color(0xFF8FF0CF) else ice.mint, 16.dp)
        Spacer(Modifier.width(7.dp))
        IceText(stringResource(R.string.settings_saved_auto), IceText2.sub, ice.ink3)
    }
}

/**
 * Pestañas de una sección con muchas partes (para no hacer tanto scroll). Devuelve la
 * elegida; se recuerda por sección ([key]) mientras LiteDash siga abierto (solo en memoria).
 */
@Composable
internal fun tabs(titles: List<Int>, key: String): Int {
    val selected = TabMemory[key] ?: 0
    // El mismo `.seg` del diseño C: una pestaña a la vez.
    Segmented(titles.mapIndexed { i, t -> i to stringResource(t) }, selected, { TabMemory[key] = it }, Modifier.horizontalScroll(rememberScrollState()))
    return selected
}

/** Las pestañas de Ajustes → Sistema ([SystemTab]). */
internal const val SYSTEM_TABS = "system"

/** Ajustes → Sistema abre en Actualizaciones (desde el aviso de versión nueva). */
internal fun openUpdates() = selectTab(SYSTEM_TABS, SystemTab.UPDATES.ordinal)

/**
 * La pestaña elegida en cada sección (al cambiar de sección, o cerrar y abrir Ajustes, sigue ahí).
 * Solo en memoria: no se guarda, así que sus claves pueden cambiar sin migrar nada.
 */
private val TabMemory = mutableStateMapOf<String, Int>()

/** Cambia a otra pestaña de la sección [key] desde su contenido (p. ej. de Salud a Códigos). */
internal fun selectTab(key: String, index: Int) {
    TabMemory[key] = index
}

/**
 * Tarjeta de Ajustes (`.card` del catálogo): vidrio de 18 dp, el título en `.h3`, los controles y
 * la ayuda en `.sub` abajo.
 */
@Composable
internal fun SettingsCard(
    title: String? = null,
    help: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val ice = LocalIce.current
    Column(
        Modifier.fillMaxWidth().iceCard().padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (title != null) IceText(title, IceText2.h3, ice.title, maxLines = 2)
        content()
        if (help != null) IceText(help, IceText2.sub, ice.ink2, maxLines = 6)
    }
}
