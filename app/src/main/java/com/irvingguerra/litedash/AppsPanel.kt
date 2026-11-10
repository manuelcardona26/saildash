// Aplicaciones (diseño C: mock-C/apps y catalogo/apps-livianas): todas las apps con sus íconos de
// verdad y la pestaña Recomendadas con las apps que van bien en una pantalla de Android 7 y 2 GB.
package com.irvingguerra.litedash

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.core.net.toUri
import com.irvingguerra.litedash.core.ui.Icon
import com.irvingguerra.litedash.core.ui.ice.IceButton
import com.irvingguerra.litedash.core.ui.ice.IceButtonKind
import com.irvingguerra.litedash.core.ui.ice.IceIcons
import com.irvingguerra.litedash.core.ui.ice.IcePill
import com.irvingguerra.litedash.core.ui.ice.IceSearchField
import com.irvingguerra.litedash.core.ui.ice.IceSegmented
import com.irvingguerra.litedash.core.ui.ice.IceText
import com.irvingguerra.litedash.core.ui.ice.IceText2
import com.irvingguerra.litedash.core.ui.ice.IceType
import com.irvingguerra.litedash.core.ui.ice.IceWell
import com.irvingguerra.litedash.core.ui.ice.LocalIce
import com.irvingguerra.litedash.core.ui.ice.iceCard
import com.irvingguerra.litedash.core.ui.ice.iceSheet
import com.irvingguerra.litedash.feature.apps.App
import com.irvingguerra.litedash.feature.apps.AppIcon
import com.irvingguerra.litedash.feature.apps.normalize
import com.irvingguerra.litedash.feature.dashboard.TileTypes

/** Una app recomendada: sus paquetes posibles, para qué sirve y dónde conseguirla. */
@Immutable
private data class Recommended(
    val name: String,
    val packages: List<String>,
    val kind: Int,
    val text: Int,
    val icon: ImageVector,
    val colors: Pair<Color, Color>,
    val get: String,
    /** Palabras para reconocerla por el nombre si la pantalla la trae con otro paquete. */
    val words: List<String> = emptyList(),
)

private val RECOMMENDED = listOf(
    Recommended("NewPipe", listOf("org.schabi.newpipe"), R.string.p2_rec_newpipe_kind, R.string.p2_rec_newpipe, IceIcons.youtube, Color(0xFFFF5A50) to Color(0xFFCF1D18), "https://newpipe.net"),
    Recommended("Organic Maps", listOf("app.organicmaps"), R.string.p2_rec_maps_kind, R.string.p2_rec_maps, IceIcons.map, Color(0xFF47B985) to Color(0xFF17694A), "https://organicmaps.app"),
    Recommended("AutoKit", listOf("cn.manstep.phonemirrorBox", "com.carlinkit.autokit"), R.string.p2_rec_autokit_kind, R.string.p2_rec_autokit, IceIcons.carPlay, Color(0xFF4A95FF) to Color(0xFF1D5BD3), "market://search?q=AutoKit%20Carlinkit", listOf("autokit")),
    Recommended("Spotify", listOf("com.spotify.music"), R.string.p2_rec_spotify_kind, R.string.p2_rec_spotify, IceIcons.spotify, Color(0xFF2BE070) to Color(0xFF109A43), "market://details?id=com.spotify.music"),
)

private fun Recommended.find(apps: List<App>): App? =
    packages.firstNotNullOfOrNull { p -> apps.firstOrNull { it.pkg == p } }
        ?: apps.firstOrNull { a -> words.any { it in a.name.lowercase() } }

/** Lo de SailDash que se abre como una app más (con su ficha brillante del diseño). */
@Immutable
private data class Shortcut(val key: String, val name: Int, val icon: ImageVector, val colors: Pair<Color, Color>, val dark: Boolean = false)

private val SHORTCUTS = listOf(
    Shortcut("saildash:radio", R.string.dock_radio, IceIcons.radio, Color(0xFFFFB057) to Color(0xFFEC5F0C)),
    Shortcut("saildash:bt", R.string.p2_bt_music, IceIcons.musicNote, Color(0xFF7D97FF) to Color(0xFF3B55DC)),
    Shortcut("saildash:eq", R.string.p2_equalizer, IceIcons.sliders, Color(0xFFBBA4FF) to Color(0xFF7449EE)),
    Shortcut("saildash:workshop", R.string.workshop, IceIcons.wrench, Color(0xFFF783AC) to Color(0xFFC2255C)),
)

private enum class AppsTab { ALL, RECOMMENDED }

/** El cajón de apps del diseño C, con el dock abajo (Apps encendido). */
@Composable
internal fun AppsPanel(d: Dependencies, apps: List<App>, nav: ShellNav, close: () -> Unit) {
    val ice = LocalIce.current
    val context = LocalContext.current
    var tab by rememberSaveable { mutableStateOf(AppsTab.ALL) }
    var query by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf<App?>(null) }
    val hidden = d.prefs.hidden
    val visible = remember(apps, hidden) { apps.filter { it.key !in hidden } }
    val shortcuts = remember(d.obdModule) { SHORTCUTS.filter { it.key != "saildash:workshop" || d.obdModule } }
    val total = visible.size + shortcuts.size
    IceScreen(d, apps, Dock.APPS, nav) {
        Box(Modifier.offset(24.dp, 16.dp).size(976.dp, 496.dp).iceSheet()) {
            // .head: left 36, right 28, top 22, alto 48.
            Row(Modifier.offset(36.dp, 22.dp).size(912.dp, 48.dp), verticalAlignment = Alignment.CenterVertically) {
                IceText(stringResource(R.string.p2_apps_title), IceText2.h1, ice.title)
                if (tab == AppsTab.ALL) {
                    Spacer(Modifier.width(16.dp))
                    IceText(stringResource(R.string.p2_apps_count, total).uppercase(), IceType.small.copy(letterSpacing = 0.08.em), ice.ink3, modifier = Modifier.padding(top = 6.dp))
                }
                Spacer(Modifier.weight(1f))
                IceSegmented(
                    listOf(AppsTab.ALL to stringResource(R.string.p2_apps_all), AppsTab.RECOMMENDED to stringResource(R.string.p2_apps_recommended)),
                    tab, { tab = it; selected = null },
                    counts = listOf(total.toString(), RECOMMENDED.size.toString()),
                    height = 48.dp, padding = 20.dp, textStyle = IceText2.pill,
                )
                Spacer(Modifier.width(16.dp))
                IceSearchField(query, { query = it; if (it.isNotEmpty()) tab = AppsTab.ALL }, stringResource(R.string.p2_apps_search), Modifier.width(240.dp))
            }
            when (tab) {
                AppsTab.ALL -> AllApps(d, visible, shortcuts, query, selected, onSelect = { selected = it }) { key ->
                    when (key) {
                        "saildash:radio" -> nav.onExpand(Expanded.RADIO)
                        "saildash:bt" -> nav.onExpand(Expanded.BLUETOOTH)
                        "saildash:eq" -> nav.onPanel(Panel.Sound)
                        "saildash:workshop" -> nav.onPanel(Panel.Workshop)
                    }
                }
                AppsTab.RECOMMENDED -> RecommendedApps(d, apps)
            }
            val app = selected
            if (app != null && tab == AppsTab.ALL) AppActions(d, app, onDone = { selected = null }) {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:${app.pkg}".toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                runCatching { context.startActivity(intent) }
            }
        }
    }
}

/** `.grid`: cinco columnas de 128 dp de alto, con las de SailDash al final. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AllApps(d: Dependencies, apps: List<App>, shortcuts: List<Shortcut>, query: String, selected: App?, onSelect: (App) -> Unit, onShortcut: (String) -> Unit) {
    val context = LocalContext.current
    val filter = remember(query) { normalize(query.trim()) }
    val names = remember(apps) { apps.map { normalize(it.name) } }
    val list = remember(apps, names, filter) { if (filter.isEmpty()) apps else apps.filterIndexed { i, _ -> filter in names[i] } }
    val shortcutNames = shortcuts.map { stringResource(it.name) }
    val shown = remember(shortcuts, shortcutNames, filter) { shortcuts.filterIndexed { i, _ -> filter.isEmpty() || filter in normalize(shortcutNames[i]) } }
    LazyVerticalGrid(
        GridCells.Fixed(5),
        Modifier.offset(24.dp, 92.dp).size(928.dp, 388.dp),
        contentPadding = PaddingValues(bottom = 12.dp),
    ) {
        items(list, key = { it.key }) { app ->
            AppCell(app.name, subtitleOf(app), selected?.key == app.key, onLongClick = { onSelect(app) }, onClick = { d.apps.open(context, app) }) {
                AppIcon(d.apps, app, 66.dp)
            }
        }
        items(shown, key = { it.key }) { s ->
            AppCell(stringResource(s.name), null, false, onLongClick = null, onClick = { onShortcut(s.key) }) {
                GlossyTile(s.icon, s.colors, 66.dp, 20.dp, 34.dp)
            }
        }
    }
}

/** Lo que se pone chiquito debajo del nombre: CarPlay → la app que lo hace, YouTube → NewPipe. */
private fun subtitleOf(app: App): String? = when (app.pkg) {
    "org.schabi.newpipe" -> "YouTube"
    else -> null
}

/** `.app`: el ícono de 66 dp y el nombre (15) con su renglón chico (13); dejándolo presionado, sus acciones. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AppCell(name: String, sub: String?, marked: Boolean, onLongClick: (() -> Unit)?, onClick: () -> Unit, icon: @Composable () -> Unit) {
    val ice = LocalIce.current
    Column(
        Modifier
            .fillMaxWidth()
            .height(128.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(if (marked) Color(120, 190, 255).copy(alpha = 0.14f) else Color.Transparent)
            .combinedClickable(onLongClick = onLongClick, onClick = onClick)
            .padding(top = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        icon()
        IceText(name, IceType.body.copy(textAlign = TextAlign.Center), ice.ink, modifier = Modifier.padding(top = 12.dp, start = 6.dp, end = 6.dp))
        if (sub != null) IceText(sub, IceType.small, ice.ink3, modifier = Modifier.padding(top = 4.dp))
    }
}

/** `.tile`: la ficha brillante de color (degradado a 150°, brillo arriba y su sombra) con el ícono blanco. */
@Composable
internal fun GlossyTile(icon: ImageVector, colors: Pair<Color, Color>, size: Dp, radius: Dp, iconSize: Dp, iconColor: Color = Color.White) {
    Box(
        Modifier
            .size(size)
            .drawWithCache {
                val r = CornerRadius(radius.toPx())
                val fill = com.irvingguerra.litedash.core.ui.ice.cssLinearGradient(150f, this.size, listOf(0f to colors.first, 1f to colors.second))
                val gloss = Brush.verticalGradient(0f to Color.White.copy(alpha = 0.22f), 0.5f to Color.White.copy(alpha = 0f))
                val shadow = Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0f), Color.Black.copy(alpha = 0.28f)), startY = this.size.height * 0.6f, endY = this.size.height + 10.dp.toPx())
                onDrawBehind {
                    drawRoundRect(shadow, Offset(2.dp.toPx(), 6.dp.toPx()), androidx.compose.ui.geometry.Size(this.size.width - 4.dp.toPx(), this.size.height + 2.dp.toPx()), r)
                    drawRoundRect(fill, cornerRadius = r)
                    drawRoundRect(gloss, cornerRadius = r)
                    drawRoundRect(Color.White.copy(alpha = 0.35f), Offset(radius.toPx() / 2, 0f), androidx.compose.ui.geometry.Size(this.size.width - radius.toPx(), 1.dp.toPx()))
                }
            },
        contentAlignment = Alignment.Center,
    ) { Icon(icon, iconColor, iconSize) }
}

/** Dejando presionada una app: fijarla al dock de la barra, ponerla en el inicio, ocultarla o ver su información. */
@Composable
private fun AppActions(d: Dependencies, app: App, onDone: () -> Unit, onInfo: () -> Unit) {
    val ice = LocalIce.current
    val apps = d.apps.apps.orEmpty()
    val dock = d.prefs.dock ?: remember(apps) { dockFavorites(d, apps).map { it.key } }
    val hidden = d.prefs.hidden
    Row(
        Modifier.offset(24.dp, 420.dp).size(928.dp, 60.dp).iceCard().padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        IceText(app.name, IceText2.h3, ice.title, modifier = Modifier.weight(1f))
        val inDock = app.key in dock
        IceButton(stringResource(if (inDock) R.string.unpin_from_dock else R.string.pin_to_dock), {
            d.prefs.dock = if (inDock) dock - app.key else dock + app.key
            onDone()
        })
        IceButton(stringResource(R.string.add_to_home), { d.dashboard.addTile(TileTypes.APP, app.key); onDone() })
        val isHidden = app.key in hidden
        IceButton(stringResource(if (isHidden) R.string.show_app else R.string.hide_app), {
            d.prefs.hidden = if (isHidden) hidden - app.key else hidden + app.key
            onDone()
        })
        IceButton(stringResource(R.string.app_info), { onInfo(); onDone() }, kind = IceButtonKind.GHOST)
        com.irvingguerra.litedash.core.ui.ice.IceIconButton(IceIcons.close, onDone)
    }
}

/** Recomendadas (catalogo/apps-livianas): las cuatro fichas, por qué estas y lo que va por CarPlay. */
@Composable
private fun RecommendedApps(d: Dependencies, apps: List<App>) {
    val ice = LocalIce.current
    val context = LocalContext.current
    // .recs: left 24, right 24, top 88, alto 212, separadas 12.
    Row(Modifier.offset(24.dp, 88.dp).size(928.dp, 212.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        for (r in RECOMMENDED) {
            val app = r.find(apps)
            Box(Modifier.weight(1f).height(212.dp).iceCard(20.dp)) {
                Row(Modifier.offset(16.dp, 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    GlossyTile(r.icon, r.colors, 56.dp, 17.dp, 30.dp)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        IceText(r.name, IceText2.h3.copy(fontSize = IceText2.h3.fontSize * (18f / 17f), letterSpacing = 0.em), ice.title)
                        IceText(stringResource(r.kind), IceType.small, ice.ice, modifier = Modifier.padding(top = 4.dp))
                    }
                }
                IceText(stringResource(r.text), IceText2.para, ice.ink2, maxLines = 3, modifier = Modifier.offset(16.dp, 88.dp).padding(end = 32.dp))
                val shape = RoundedCornerShape(22.dp)
                val mint = Color(0xFF8FF0CF)
                Row(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .padding(start = 16.dp, end = 16.dp, bottom = 14.dp)
                        .fillMaxWidth()
                        .height(44.dp)
                        .clip(shape)
                        .then(
                            if (app != null) Modifier.background(Color(126, 240, 194).copy(alpha = 0.08f)).border(1.dp, Color(126, 240, 194).copy(alpha = 0.25f), shape)
                            else Modifier.border(1.dp, Color(169, 220, 255).copy(alpha = 0.3f), shape),
                        )
                        .clickable { if (app != null) d.apps.open(context, app) else openLink(context, r.get) },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                ) {
                    Icon(if (app != null) IceIcons.check else IceIcons.openExternal, if (app != null) mint else ice.ice, 18.dp)
                    IceText(stringResource(if (app != null) R.string.p2_rec_installed else R.string.p2_rec_get), IceText2.button.copy(fontSize = IceText2.seg.fontSize), if (app != null) mint else ice.ice)
                }
            }
        }
    }
    // .why: top 312, alto 96, con el pozo dorado.
    Box(Modifier.offset(24.dp, 312.dp).size(928.dp, 96.dp).iceCard(20.dp)) {
        IceWell(IceIcons.infoDot, Color(0xFFFFD88A), Modifier.offset(18.dp, 18.dp))
        Column(Modifier.offset(72.dp, 16.dp).padding(end = 90.dp)) {
            IceText(stringResource(R.string.p2_rec_why), IceText2.h3.copy(fontSize = IceText2.h3.fontSize * (16f / 17f)), ice.title)
            IceText(whyText(), IceText2.para.copy(lineHeight = 1.45.em), ice.ink2, maxLines = 3, modifier = Modifier.padding(top = 6.dp))
        }
    }
    // .via: lo que va mejor por CarPlay.
    Row(Modifier.offset(36.dp, 422.dp).height(46.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        IceText(stringResource(R.string.p2_rec_via), IceType.body.copy(fontSize = IceType.small.fontSize * (14f / 13f)), ice.ink2, modifier = Modifier.padding(end = 4.dp))
        val projection = projectionApp(apps)
        val open = { projection?.let { d.apps.open(context, it) } ?: Unit }
        IcePill("Waze", false, open, icon = IceIcons.waze, iconTint = ice.ice, padding = 18.dp)
        IcePill("Google Maps", false, open, icon = IceIcons.pin, iconTint = ice.ice, padding = 18.dp)
        IcePill("WhatsApp", false, open, icon = IceIcons.whatsApp, iconTint = ice.ice, padding = 18.dp)
    }
}

/** "Tu pantalla tiene **Android 7.1 y 2 GB de RAM**. …" con la parte resaltada en tinta. */
@Composable
private fun whyText(): AnnotatedString {
    val ice = LocalIce.current
    val before = stringResource(R.string.p2_rec_why_1)
    val strong = stringResource(R.string.p2_rec_why_2)
    val after = stringResource(R.string.p2_rec_why_3)
    return buildAnnotatedString {
        append(before)
        withStyle(SpanStyle(color = ice.ink)) { append(strong) }
        append(after)
    }
}

private fun openLink(context: Context, url: String) {
    val flags = Intent.FLAG_ACTIVITY_NEW_TASK
    val ok = runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()).addFlags(flags)) }.isSuccess
    if (!ok && url.startsWith("market://")) {
        val web = "https://play.google.com/store/" + url.removePrefix("market://")
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, web.toUri()).addFlags(flags)) }
    }
}
