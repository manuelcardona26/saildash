// Ajustes → Inicio y apariencia, arriba: las tarjetas del mockup (catalogo/ajustes): tema, fondo
// espacial, color de acento y widgets y páginas. Lo demás (barras, unidades…) sigue debajo.
package com.irvingguerra.litedash

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.irvingguerra.litedash.core.ui.Icon
import com.irvingguerra.litedash.core.ui.ice.IceButton
import com.irvingguerra.litedash.core.ui.ice.IceIcons
import com.irvingguerra.litedash.core.ui.ice.IceSegmented
import com.irvingguerra.litedash.core.ui.ice.IceSwatch
import com.irvingguerra.litedash.core.ui.ice.IceText
import com.irvingguerra.litedash.core.ui.ice.IceText2
import com.irvingguerra.litedash.core.ui.ice.IceToggle
import com.irvingguerra.litedash.core.ui.ice.LocalIce
import com.irvingguerra.litedash.core.ui.ice.iceCard
import com.irvingguerra.litedash.core.ui.ice.softGlow
import java.text.SimpleDateFormat
import java.util.Calendar

/** Los widgets del Inicio del diseño C (velocidad, carro, clima, Wi-Fi, Bluetooth, datos, música, Casa, Trabajo, Favorito y buscar). */
private const val HOME_WIDGETS = 11

/** Los colores de acento del diseño: hielo (el de fábrica), menta, oro y rosa (luz, fondo). */
private val ACCENT_SWATCHES = listOf(
    null to (Color(0xFFE9F7FF) to Color(0xFF8FD0FF)),
    Color(0xFF6FE0B8) to (Color(0xFFE2FFF4) to Color(0xFF6FE0B8)),
    Color(0xFFF5C25E) to (Color(0xFFFFF3D6) to Color(0xFFF5C25E)),
    Color(0xFFF2839D) to (Color(0xFFFFE3EA) to Color(0xFFF2839D)),
)

/** Las cuatro tarjetas de arriba de Inicio y apariencia, con las medidas del mockup (694 dp de ancho). */
@Composable
internal fun AppearanceHero(d: Dependencies, onEdit: () -> Unit) {
    val ice = LocalIce.current
    val prefs = d.prefs
    // Tema (694 × 206).
    Box(Modifier.size(694.dp, 206.dp).iceCard()) {
        Column(Modifier.offset(18.dp, 16.dp)) {
            IceText(stringResource(R.string.p2_theme), IceText2.h3, ice.title)
            IceText(stringResource(R.string.p2_theme_help), IceText2.sub, ice.ink2, modifier = Modifier.padding(top = 5.dp))
        }
        Row(Modifier.align(Alignment.TopEnd).padding(top = 12.dp, end = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            IceText(stringResource(R.string.p2_changes_by), IceText2.sub, ice.ink3)
            Spacer(Modifier.width(12.dp))
            IceSegmented(
                listOf(NightSource.SUN to stringResource(R.string.p2_by_sun), NightSource.LIGHTS to stringResource(R.string.p2_by_lights)),
                if (prefs.nightSource == NightSource.LIGHTS) NightSource.LIGHTS else NightSource.SUN,
                { prefs.nightSource = it },
                icons = listOf(IceIcons.sunSmall, IceIcons.headlights),
                padding = 14.dp,
            )
        }
        val sunset = rememberSunset()
        ThemeOption(Modifier.offset(18.dp, 70.dp), ThemeLook.DARK, stringResource(R.string.p2_theme_ice), stringResource(R.string.p2_theme_ice_help), prefs.theme == ThemeMode.DARK) { prefs.theme = ThemeMode.DARK }
        ThemeOption(Modifier.offset(241.dp, 70.dp), ThemeLook.LIGHT, stringResource(R.string.p2_theme_light), stringResource(R.string.p2_theme_light_help), prefs.theme == ThemeMode.LIGHT) { prefs.theme = ThemeMode.LIGHT }
        ThemeOption(
            Modifier.offset(464.dp, 70.dp), ThemeLook.AUTO, stringResource(R.string.p2_theme_auto),
            stringResource(R.string.p2_theme_auto_help, sunset), prefs.theme == ThemeMode.SUN || prefs.theme == ThemeMode.SYSTEM,
        ) { prefs.theme = ThemeMode.SUN }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        // Fondo espacial (300 × 82).
        Row(Modifier.size(300.dp, 82.dp).iceCard().clip(RoundedCornerShape(18.dp)).clickable { prefs.spaceBackdrop = !prefs.spaceBackdrop }.padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                IceText(stringResource(R.string.p2_space), IceText2.lbl, ice.ink)
                IceText(stringResource(R.string.p2_space_help), IceText2.sub, ice.ink2, modifier = Modifier.padding(top = 2.dp))
            }
            IceToggle(prefs.spaceBackdrop)
        }
        // Color de acento (382 × 82).
        Row(Modifier.size(382.dp, 82.dp).iceCard().padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Column(Modifier.weight(1f)) {
                IceText(stringResource(R.string.p2_accent), IceText2.lbl, ice.ink)
                IceText(stringResource(R.string.p2_accent_help), IceText2.sub, ice.ink2, modifier = Modifier.padding(top = 2.dp))
            }
            val chosen = prefs.accentColor
            for ((color, look) in ACCENT_SWATCHES) {
                IceSwatch(look.first, look.second, on = chosen == color, onClick = { prefs.accentColor = color })
            }
        }
    }
    // Widgets y páginas (694 × 80).
    Row(Modifier.size(694.dp, 80.dp).iceCard().padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f)) {
            IceText(stringResource(R.string.p2_widgets), IceText2.lbl, ice.ink)
            IceText(stringResource(R.string.p2_widgets_count, HomePage.entries.size, HOME_WIDGETS), IceText2.sub, ice.ink2, modifier = Modifier.padding(top = 2.dp))
        }
        PageThumb(true) {
            Bar(5, 5, 26, 20)
            Bar(35, 5, 24, 9, Color(120, 190, 255).copy(alpha = 0.45f))
            Bar(35, 17, 24, 8)
            Bar(35, 28, 24, 10)
        }
        PageThumb(false) {
            Bar(8, 7, 48, 22)
            Bar(8, 32, 22, 6)
        }
        PageThumb(false) {
            Bar(20, 6, 24, 14)
            Bar(6, 24, 52, 3, Color(190, 228, 255).copy(alpha = 0.4f))
            Bar(6, 31, 52, 3, Color(190, 228, 255).copy(alpha = 0.25f))
        }
        PageThumb(false) {
            Box(
                Modifier.offset(14.dp, 8.dp).size(36.dp, 24.dp).drawWithContent {
                    drawArc(Color(150, 210, 255).copy(alpha = 0.5f), 180f, 180f, false, style = androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx()))
                },
            )
        }
        IceButton(stringResource(R.string.p2_edit_home), onEdit, Modifier.padding(start = 6.dp), icon = IceIcons.pencil)
    }
}

private enum class ThemeLook { DARK, LIGHT, AUTO }

/** `.opt`: la vista previa de 211 × 76 con el carro, el nombre y la línea de abajo; la elegida con borde hielo y palomita. */
@Composable
private fun ThemeOption(modifier: Modifier, look: ThemeLook, name: String, help: String, selected: Boolean, onClick: () -> Unit) {
    val ice = LocalIce.current
    val shape = RoundedCornerShape(13.dp)
    Column(modifier.width(211.dp).clip(RoundedCornerShape(13.dp)).clickable(onClick = onClick)) {
        Box(
            Modifier
                .then(if (selected) Modifier.softGlow(Color(150, 210, 255).copy(alpha = 0.22f), 12.dp) else Modifier)
                .size(211.dp, 76.dp)
                .clip(shape)
                .border(if (selected) 1.5.dp else 1.dp, if (selected) Color(0xFFBFE6FF) else ice.line, shape),
        ) {
            when (look) {
                ThemeLook.DARK -> MiniHome(dark = true)
                ThemeLook.LIGHT -> MiniHome(dark = false)
                ThemeLook.AUTO -> {
                    MiniHome(dark = true)
                    // La mitad clara cortada en diagonal (polygon 58% 0, 100% 0, 100% 100%, 42% 100%).
                    Box(
                        Modifier.fillMaxSize().drawWithContent {
                            val p = Path().apply {
                                moveTo(size.width * 0.58f, 0f); lineTo(size.width, 0f); lineTo(size.width, size.height); lineTo(size.width * 0.42f, size.height); close()
                            }
                            clipPath(p) { this@drawWithContent.drawContent() }
                            drawLine(Color.White.copy(alpha = 0.85f), Offset(size.width * 0.58f, -6f), Offset(size.width * 0.42f, size.height + 6f), 1.5.dp.toPx())
                        },
                    ) { MiniHome(dark = false) }
                    Box(
                        Modifier.offset(8.dp, 36.dp).size(26.dp).clip(CircleShape).background(Color(6, 12, 24).copy(alpha = 0.85f)).border(1.dp, Color(190, 228, 255).copy(alpha = 0.4f), CircleShape),
                        contentAlignment = Alignment.Center,
                    ) { Icon(IceIcons.moonSmall, Color(0xFFBFE6FF), 16.dp) }
                    Box(
                        Modifier.offset(177.dp, 36.dp).size(26.dp).clip(CircleShape).background(Color.White),
                        contentAlignment = Alignment.Center,
                    ) { Icon(IceIcons.sunSmall, Color(0xFFE3A21A), 16.dp) }
                }
            }
        }
        Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            IceText(name, IceText2.button, ice.title)
            if (selected) {
                Spacer(Modifier.width(8.dp))
                Box(Modifier.size(20.dp).clip(CircleShape).background(Color(0xFFBFE6FF)), contentAlignment = Alignment.Center) {
                    Icon(IceIcons.check, Color(0xFF0A1322), 14.dp)
                }
            }
        }
        IceText(help, IceText2.sub, ice.ink2, modifier = Modifier.padding(top = 5.dp))
    }
}

/** `.mini`: el inicio en chiquito (fondo, tu carro, tres tarjetas y el dock). */
@Composable
private fun MiniHome(dark: Boolean) {
    val context = LocalContext.current
    val owner = remember { OwnerCar.of(context) }
    LaunchedEffect(owner) { owner.load() }
    Box(Modifier.fillMaxSize().then(if (dark) Modifier else Modifier.background(Brush.linearGradient(listOf(Color(0xFFF4F8FC), Color(0xFFDFE7F1)))))) {
        if (dark) Image(painterResource(R.drawable.bg_espacio), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        owner.image?.let { img ->
            Image(img.bitmap.asImageBitmap(), null, Modifier.offset(6.dp, 14.dp).size(92.dp, 92.dp * img.ratio), contentScale = ContentScale.FillBounds)
        }
        val card = if (dark) Color(170, 210, 255).copy(alpha = 0.16f) else Color.White
        val mid = if (dark) Color(120, 190, 255).copy(alpha = 0.38f) else Color(0xFFCFE6FB)
        val border = if (dark) Color(190, 225, 255).copy(alpha = 0.25f) else Color.Transparent
        MiniBar(Modifier.align(Alignment.TopEnd).padding(top = 7.dp, end = 8.dp), 22.dp, card, border)
        MiniBar(Modifier.align(Alignment.TopEnd).padding(top = 33.dp, end = 8.dp), 12.dp, mid, border)
        MiniBar(Modifier.align(Alignment.TopEnd).padding(top = 49.dp, end = 8.dp), 12.dp, card, border)
        Box(
            Modifier.align(Alignment.BottomStart).fillMaxWidth().height(9.dp)
                .background(if (dark) Color(6, 10, 20).copy(alpha = 0.95f) else Color.White)
                .drawWithContent { drawContent(); drawRect(if (dark) Color(190, 228, 255).copy(alpha = 0.3f) else Color(0xFFD0DBE7), size = androidx.compose.ui.geometry.Size(size.width, 1.dp.toPx())) },
        )
    }
}

@Composable
private fun MiniBar(modifier: Modifier, height: Dp, color: Color, border: Color) {
    val shape = RoundedCornerShape(5.dp)
    Box(modifier.size(86.dp, height).clip(shape).background(color).then(if (border.alpha > 0f) Modifier.border(1.dp, border, shape) else Modifier))
}

/** `.pg`: miniatura de una página del inicio (64 × 44). */
@Composable
private fun PageThumb(on: Boolean, content: @Composable BoxScope.() -> Unit) {
    val shape = RoundedCornerShape(9.dp)
    Box(
        Modifier
            .then(if (on) Modifier.softGlow(Color(150, 210, 255).copy(alpha = 0.25f), 8.dp) else Modifier)
            .size(64.dp, 44.dp)
            .clip(shape)
            .background(Color(6, 12, 22).copy(alpha = 0.55f))
            .border(if (on) 1.5.dp else 1.dp, if (on) Color(0xFFBFE6FF) else LocalIce.current.line, shape),
        content = content,
    )
}

@Composable
private fun Bar(x: Int, y: Int, w: Int, h: Int, color: Color = Color(170, 210, 255).copy(alpha = 0.22f)) {
    Box(Modifier.offset(x.dp, y.dp).size(w.dp, h.dp).clip(RoundedCornerShape(3.dp)).background(color))
}

/** "5:43 p. m.": a qué hora se mete el sol hoy aquí. */
@Composable
private fun rememberSunset(): String {
    val context = LocalContext.current
    val locale = currentLocale()
    return remember(locale) {
        val now = Calendar.getInstance()
        val (lat, lon) = Daylight.place(context)
        val minutes = Daylight.sunTimes(now, lat, lon).second.toInt()
        val at = (now.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, (minutes / 60).coerceIn(0, 23))
            set(Calendar.MINUTE, minutes % 60)
        }
        SimpleDateFormat("h:mm a", locale).format(at.time).replace("p.m.", "p. m.").replace("a.m.", "a. m.")
    }
}

/** El aire entre las tarjetas del diseño y las pestañas de abajo. */
@Composable
internal fun AppearanceGap() = Spacer(Modifier.height(8.dp))
