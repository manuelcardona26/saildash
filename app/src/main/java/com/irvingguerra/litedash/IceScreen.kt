package com.irvingguerra.litedash

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.irvingguerra.litedash.core.ui.ice.DockClock
import com.irvingguerra.litedash.core.ui.ice.DockEntry
import com.irvingguerra.litedash.core.ui.ice.IceBackdrop
import com.irvingguerra.litedash.core.ui.ice.IceDock
import com.irvingguerra.litedash.core.ui.ice.IceFrame
import com.irvingguerra.litedash.core.ui.ice.IceIcons
import com.irvingguerra.litedash.core.ui.ice.LocalIce
import com.irvingguerra.litedash.core.ui.ice.iceShade
import com.irvingguerra.litedash.feature.apps.App

/*
 * SailDash 1.1 · el marco de las pantallas del diseño C que no son páginas del inicio (Ajustes,
 * Aplicaciones, Radio FM, Bienvenida): el espacio de fondo, el oscurecido (.shade) y el dock abajo
 * con su botón activo. Todas miden sobre el lienzo de 1024 × 600 (IceFrame).
 */

/** Los botones del dock (sus llaves). */
internal object Dock {
    const val HOME = "home"
    const val APPS = "apps"
    const val PHONE = "phone"
    const val CARPLAY = "carplay"
    const val WHATSAPP = "whatsapp"
    const val RADIO = "radio"
    const val SPOTIFY = "spotify"
    const val SETTINGS = "settings"
}

/**
 * A dónde lleva cada cosa desde cualquier pantalla con dock: abrir un panel, la música o la radio
 * en grande (o la tarjeta de Bluetooth), y volver a la primera página del inicio.
 */
@Immutable
internal class ShellNav(
    val onPanel: (Panel) -> Unit,
    val onExpand: (Expanded?) -> Unit,
    val onHome: () -> Unit,
)

/** El dock del diseño C con su reloj, el botón [active] encendido. */
@Composable
internal fun ShellDock(d: Dependencies, apps: List<App>, active: String?, nav: ShellNav, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val (time, marker, date) = dockTime()
    IceDock(
        entries = dockEntries(),
        active = active,
        onClick = { key -> onDock(context, d, apps, key, nav) },
        onLongClick = { key ->
            // Teléfono, dejándolo presionado: la tarjeta de Bluetooth (el celular conectado).
            if (key == Dock.PHONE) nav.onExpand(Expanded.BLUETOOTH)
        },
        modifier = modifier,
        clock = { DockClock(time, marker, date) },
    )
}

/**
 * Pantalla del diseño C con dock: fondo [background] (una imagen), oscurecido si [shade], el
 * agujero negro chico si se da [blackHole] (left, top, ancho, opacidad, como `.bh`), el contenido
 * sobre el lienzo y el dock con [active] encendido. Atrapa los toques para que no pasen al inicio.
 */
@Composable
internal fun IceScreen(
    d: Dependencies,
    apps: List<App>,
    active: String?,
    nav: ShellNav,
    background: Int = R.drawable.bg_espacio,
    shade: Boolean = true,
    blackHole: BlackHole? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val ice = LocalIce.current
    Box(
        Modifier
            .fillMaxSize()
            .background(ice.bg)
            .clickable(remember { MutableInteractionSource() }, indication = null) {},
    ) {
        IceFrame(Modifier.safeDrawingPadding()) {
            IceBackdrop(background)
            if (shade && ice.dark) Box(Modifier.fillMaxSize().iceShade())
            if (blackHole != null && ice.dark) Image(
                painterResource(R.drawable.blackhole_small), null,
                Modifier.offset(blackHole.left, blackHole.top).size(blackHole.width, blackHole.width * 0.6f).alpha(blackHole.alpha),
                contentScale = ContentScale.Fit,
            )
            content()
            ShellDock(d, apps, active, nav, Modifier.align(Alignment.BottomStart))
        }
    }
}

/** El agujero negro chico de los mockups (`.bh`): posición, ancho y opacidad. */
@Immutable
internal data class BlackHole(val left: Dp, val top: Dp, val width: Dp, val alpha: Float)

@Composable
internal fun dockEntries(): List<DockEntry> {
    val home = stringResource(R.string.dock_home)
    val apps = stringResource(R.string.dock_apps)
    val phone = stringResource(R.string.dock_phone)
    val carplay = stringResource(R.string.dock_carplay)
    val whatsapp = stringResource(R.string.dock_whatsapp)
    val radio = stringResource(R.string.dock_radio)
    val spotify = stringResource(R.string.dock_spotify)
    val settings = stringResource(R.string.dock_settings)
    return remember(home, apps, phone, carplay, whatsapp, radio, spotify, settings) {
        listOf(
            DockEntry(Dock.HOME, IceIcons.home, home),
            DockEntry(Dock.APPS, IceIcons.apps, apps),
            DockEntry(Dock.PHONE, IceIcons.phone, phone, separatorBefore = true),
            DockEntry(Dock.CARPLAY, IceIcons.carPlay, carplay),
            DockEntry(Dock.WHATSAPP, IceIcons.whatsApp, whatsapp),
            DockEntry(Dock.RADIO, IceIcons.radio, radio, separatorBefore = true),
            DockEntry(Dock.SPOTIFY, IceIcons.spotify, spotify),
            DockEntry(Dock.SETTINGS, IceIcons.settings, settings, separatorBefore = true),
        )
    }
}

/** Lo que hace cada botón del dock. */
internal fun onDock(context: Context, d: Dependencies, apps: List<App>, key: String, nav: ShellNav) {
    fun open(app: App?, name: String): Boolean {
        if (app != null) d.apps.open(context, app) else missing(context, name)
        return app != null
    }
    when (key) {
        Dock.HOME -> nav.onHome()
        Dock.APPS -> nav.onPanel(Panel.Drawer)
        // La app de Bluetooth de la pantalla (llamadas del celular); si no hay, el marcador de Android.
        Dock.PHONE -> d.music.bluetoothApp()?.let { pkg -> d.apps.firstOf(apps, pkg) }?.let { d.apps.open(context, it) } ?: openDialer(context)
        // AutoKit / ZLink / la que la pantalla traiga (la misma detección de la pieza de proyección).
        Dock.CARPLAY -> projectionApp(apps)?.let { d.apps.open(context, it) } ?: run {
            missing(context, context.getString(R.string.dock_carplay))
            nav.onPanel(Panel.Drawer)
        }
        Dock.WHATSAPP -> open(d.apps.firstOf(apps, "com.whatsapp", "com.whatsapp.w4b"), context.getString(R.string.dock_whatsapp))
        // La Radio FM del diseño C (la de la pantalla, con sus emisoras guardadas en SailDash).
        Dock.RADIO -> nav.onExpand(Expanded.RADIO)
        // Spotify; si no está, la app de música elegida o la primera conocida; si no, la música en grande.
        Dock.SPOTIFY -> (
            d.apps.firstOf(apps, "com.spotify.music")
                ?: d.music.preferred?.let { d.apps.firstOf(apps, it) }
                ?: d.apps.firstOf(apps, *MUSIC_PACKAGES)
            )?.let { d.apps.open(context, it) } ?: nav.onExpand(Expanded.MUSIC)
        Dock.SETTINGS -> nav.onPanel(Panel.Settings)
    }
}

internal fun missing(context: Context, name: String) {
    Toast.makeText(context, context.getString(R.string.dock_missing, name), Toast.LENGTH_SHORT).show()
}
