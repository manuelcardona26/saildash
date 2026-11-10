package com.irvingguerra.litedash

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.irvingguerra.litedash.core.ui.ice.DockClock
import com.irvingguerra.litedash.core.ui.ice.DockEntry
import com.irvingguerra.litedash.core.ui.ice.IceDims
import com.irvingguerra.litedash.core.ui.ice.IceDock
import com.irvingguerra.litedash.core.ui.ice.IceFrame
import com.irvingguerra.litedash.core.ui.ice.IceIcons
import com.irvingguerra.litedash.core.ui.ice.LocalIce
import com.irvingguerra.litedash.core.ui.ice.PageArrows
import com.irvingguerra.litedash.core.ui.ice.PageDots
import com.irvingguerra.litedash.feature.apps.App
import kotlinx.coroutines.launch

/*
 * SailDash 1.1 · el inicio del diseño C: páginas a pantalla completa que se deslizan (Inicio, Carro,
 * Carretera, Mapa), flechas a los lados, puntos de página y el dock de abajo. Reemplaza la barra
 * lateral y la cuadrícula de LiteDash (que sigue al "Acomodar el inicio" desde Ajustes).
 */

/** Las páginas del inicio, en orden. Para agregar una: aquí y en [HomeShell] (su contenido). */
enum class HomePage(val title: Int, val background: Int) {
    INICIO(R.string.page_home, R.drawable.bg_inicio),
    CARRO(R.string.page_car, R.drawable.bg_espacio),
    CARRETERA(R.string.page_road, R.drawable.bg_espacio),
    MAPA(R.string.page_map, R.drawable.bg_espacio),
}

/**
 * El inicio nuevo. [covered] = algo tapa el inicio (estacionado, llantas): lo que gasta descansa.
 * [page] es la página que se ve (la guarda el inicio). [expanded] es la música o la radio en grande (encima de las páginas, con el dock visible).
 * [notices] son los avisos de siempre (versión nueva, motor, llantas…), arriba al centro.
 */
@Composable
fun HomeShell(
    d: Dependencies,
    apps: List<App>,
    revision: Int,
    page: Int,
    onPage: (Int) -> Unit,
    covered: Boolean,
    expanded: Expanded?,
    onExpand: (Expanded?) -> Unit,
    onPanel: (Panel) -> Unit,
    notices: @Composable ColumnScope.() -> Unit,
) {
    val ice = LocalIce.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pages = HomePage.entries
    // La página vive afuera ([page]): abrir Ajustes o el cajón saca esto de la pantalla y, al volver,
    // sigue en la misma; el botón de inicio la regresa a la primera.
    val pager = rememberPagerState(initialPage = page) { pages.size }
    LaunchedEffect(pager) { snapshotFlow { pager.settledPage }.collect { onPage(it) } }
    LaunchedEffect(page) { if (pager.settledPage != page && !pager.isScrollInProgress) pager.scrollToPage(page) }
    fun goTo(page: Int) {
        scope.launch {
            val target = page.mod(pages.size)
            // Dar la vuelta (de la primera a la última) es un salto; entre vecinas, deslizando.
            if (kotlin.math.abs(target - pager.currentPage) > 1) pager.scrollToPage(target) else pager.animateScrollToPage(target)
        }
    }
    val inicio = remember {
        InicioActions(
            onPanel = onPanel,
            onMusic = { onExpand(Expanded.MUSIC) },
            onCar = { goTo(HomePage.CARRO.ordinal) },
            onBluetooth = { onExpand(Expanded.BLUETOOTH) },
        )
    }
    // Carro, Carretera y Mapa: los paneles, la música en grande y el modo galaxia (Solo velocidad).
    val pageActions = remember {
        PageActions(
            onPanel = onPanel,
            onMusic = { onExpand(Expanded.MUSIC) },
            onSpeed = { openSpeedOnly(d) { onPanel(Panel.SpeedOnly) } },
        )
    }
    Box(Modifier.fillMaxSize().background(ice.bg)) {
        IceFrame(Modifier.safeDrawingPadding()) {
            HorizontalPager(pager, Modifier.fillMaxSize(), beyondViewportPageCount = 0, key = { it }) { index ->
                // Se ve ahora (elegida, quieta y sin nada encima): solo entonces anima y escucha el GPS.
                val visible = !covered && expanded == null && pager.settledPage == index && !pager.isScrollInProgress
                when (pages[index]) {
                    HomePage.INICIO -> InicioPage(d, apps, revision, visible = visible, actions = inicio)
                    HomePage.CARRO -> CarroPage(d, apps, visible, pageActions)
                    HomePage.CARRETERA -> CarreteraPage(d, visible, pageActions)
                    HomePage.MAPA -> MapaPage(d, apps, visible, pageActions)
                }
            }
            // La música o la radio en grande, sobre las páginas (el dock sigue abajo); la tarjeta de
            // Bluetooth, encima del inicio oscurecido.
            val close = { onExpand(null) }
            when (expanded) {
                null -> {
                    PageArrows(onPrevious = { goTo(pager.currentPage - 1) }, onNext = { goTo(pager.currentPage + 1) })
                    // .pager: a 80 dp del fondo (encima del dock), centrados.
                    PageDots(pages.size, pager.currentPage, Modifier.align(Alignment.TopCenter).offset(y = 514.dp))
                }
                Expanded.BLUETOOTH -> BluetoothCard(d, close)
                Expanded.MUSIC -> MusicPanel(d, close, onSound = { onPanel(Panel.Sound) })
                Expanded.RADIO -> if (d.music.radioInternet) Box(
                    Modifier.size(IceDims.FRAME_W.dp, (IceDims.FRAME_H.dp - IceDims.dock)).background(ice.bg).padding(16.dp),
                ) { RadioBig(d, close) { onPanel(Panel.MusicSettings) } } else RadioScreen(d, close, onSound = { onPanel(Panel.Sound) })
                else -> Box(
                    Modifier.size(IceDims.FRAME_W.dp, (IceDims.FRAME_H.dp - IceDims.dock)).background(ice.bg).padding(16.dp),
                ) { MusicBig(d, close) }
            }
            val nav = remember(onPanel, onExpand) {
                ShellNav(onPanel = onPanel, onExpand = onExpand, onHome = { onExpand(null); goTo(0) })
            }
            ShellDock(
                d, apps,
                active = if (expanded == Expanded.RADIO && !d.music.radioInternet) Dock.RADIO else Dock.HOME,
                nav = nav,
                modifier = Modifier.align(Alignment.BottomStart),
            )
            Column(Modifier.align(Alignment.TopCenter).padding(top = 72.dp).width(640.dp), content = notices)
        }
    }
}

/**
 * El dock del inicio para las pantallas que lo llevan encima de un panel (Personalizar medidor): igual
 * que el de las páginas; Inicio cierra el panel ([onHome]).
 */
@Composable
fun HomeDock(d: Dependencies, apps: List<App>, onPanel: (Panel) -> Unit, onExpand: (Expanded?) -> Unit, onHome: () -> Unit, modifier: Modifier = Modifier) {
    val nav = remember(onPanel, onExpand, onHome) { ShellNav(onPanel = onPanel, onExpand = onExpand, onHome = onHome) }
    ShellDock(d, apps, active = Dock.HOME, nav = nav, modifier = modifier)
}
