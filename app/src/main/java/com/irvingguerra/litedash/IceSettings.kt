// SailDash 1.1 · diseño C: qué secciones de Ajustes ya se dibujan en el estilo nuevo (a pantalla
// completa, con su menú y el dock). Las demás siguen en SettingsPanel mientras se rehacen.
package com.irvingguerra.litedash

import androidx.compose.runtime.Composable

/**
 * Dibuja [section] en el diseño C si ya está hecha y devuelve true; si no, false (y SettingsPanel
 * dibuja la de siempre).
 */
@Composable
internal fun iceSection(
    d: Dependencies,
    section: SettingsSection,
    onSection: (SettingsSection) -> Unit,
    close: () -> Unit,
    onPanel: (Panel) -> Unit,
    onTires: () -> Unit,
    extra: List<SettingsSection>,
): Boolean {
    when (section) {
        SettingsSection.WORKSHOP -> TallerScreen(d, onSection, close, onPanel, onTires, extra)
        // Tu carro: Recordatorios, Pico y placa y Reversa ya van en el diseño C; Mi carro y Modelos todavía no.
        SettingsSection.CAR -> {
            val tab = currentTab("car")
            if (tab !in CAR_TAB_REMINDERS..CAR_TAB_REVERSE) return false
            CarSubpage(d, tab, onSection, close, onPanel, extra) { selectTab("car", 0) }
        }
        SettingsSection.SYSTEM -> SystemScreen(d, onSection, close, onPanel, extra)
        SettingsSection.WHEEL -> WheelScreen(d, onSection, close, onPanel, extra)
        else -> return false
    }
    return true
}
