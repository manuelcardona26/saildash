// SailDash 1.1 · diseño C: el modo estacionado (catalogo/estacionado-y-noche): el reloj grande y tenue
// sobre el espacio casi negro y, a la derecha, el tema Día y noche con cómo se sabe que es de noche.
package com.irvingguerra.litedash

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.irvingguerra.litedash.core.ui.Icon
import com.irvingguerra.litedash.core.ui.ice.BrandTitle
import com.irvingguerra.litedash.core.ui.ice.IceFrame
import com.irvingguerra.litedash.core.ui.ice.IceIcons
import com.irvingguerra.litedash.core.ui.ice.IceText
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date

/** El reloj del modo estacionado en el diseño C. Un toque afuera de las opciones lo quita (lo pone quien lo usa). */
@Composable
fun ParkedC(d: Dependencies) {
    val context = LocalContext.current
    val locale = currentLocale()
    val (time, marker, _) = dockTime()
    val now = rememberMinute()
    IceFrame(Modifier.background(Color(0xFF010205))) {
        // .dim: el espacio apenas, con el oscurecido alrededor del reloj.
        Image(painterResource(R.drawable.bg_espacio), null, Modifier.fillMaxSize().alpha(0.22f), contentScale = ContentScale.Crop)
        Box(
            Modifier.fillMaxSize().drawWithCache {
                val c = Offset(size.width * 0.32f, size.height * 0.5f)
                val b = Brush.radialGradient(listOf(Color(1, 2, 5, 0), Color(1, 2, 5).copy(alpha = 0.85f)), c, size.width * 0.75f)
                onDrawBehind { drawRect(b) }
            },
        )
        BrandTitle(d.prefs.carName?.takeIf { it.isNotBlank() } ?: "Chevrolet Sail", Modifier.offset(66.dp, 40.dp).alpha(0.55f))
        // .clk
        Column(Modifier.offset(56.dp, 92.dp)) {
            Row(verticalAlignment = Alignment.Bottom) {
                IceText(time, ts(212f, 200, spacing = -0.03f), Color(206, 226, 246).copy(alpha = 0.66f))
                if (marker != null) IceText(marker, ts(34f, 300, spacing = 0.04f), Color(170, 196, 224).copy(alpha = 0.5f), modifier = Modifier.padding(start = 16.dp, bottom = 24.dp))
            }
            val date = remember(now, locale) {
                SimpleDateFormat(if (locale.language == "es") "EEEE d 'de' MMMM" else "EEEE, MMMM d", locale).format(now).replaceFirstChar { it.uppercase(locale) }
            }
            IceText(date, ts(30f, 300), Color(196, 216, 238).copy(alpha = 0.55f), modifier = Modifier.padding(top = 14.dp, start = 10.dp))
            val w = d.weather.weather
            if (w != null && d.weather.active) Row(Modifier.padding(top = 22.dp, start = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                val ink = Color(196, 216, 238).copy(alpha = 0.5f)
                val sky = skyOf(w.code)
                Icon(iceWeatherIcon(sky, w.day), ink, 30.dp)
                IceText("${w.temperature}°", ts(26f, 300), ink)
                Box(Modifier.size(1.dp, 22.dp).background(Color(196, 216, 238).copy(alpha = 0.2f)))
                IceText(stringResource(if (w.code in 51..57) R.string.weather_drizzle else textOf(sky)), ts(26f, 300), ink)
                rememberPlaceName(d)?.let {
                    Box(Modifier.size(1.dp, 22.dp).background(Color(196, 216, 238).copy(alpha = 0.2f)))
                    IceText(it, ts(26f, 300), ink)
                }
            }
        }
        // .tap
        Row(Modifier.offset(66.dp, 534.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(SetIcons.hand, Color(170, 196, 224).copy(alpha = 0.42f), 20.dp)
            Spacer(Modifier.width(10.dp))
            IceText(stringResource(R.string.park_hint), ts(13f, display = false, lh = 1.3f), Color(170, 196, 224).copy(alpha = 0.42f), maxLines = 2)
        }
        // .vline
        Box(Modifier.offset(668.dp, 56.dp).size(1.dp, 488.dp).background(Brush.verticalGradient(listOf(Color.Transparent, Color(190, 225, 255).copy(alpha = 0.14f), Color.Transparent))))
        // .side: Día y noche.
        Column(Modifier.offset(692.dp, 88.dp).width(316.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(IceIcons.moon, Kc.ice.copy(alpha = 0.7f), 22.dp)
                Spacer(Modifier.width(10.dp))
                IceText(stringResource(R.string.park_daynight), ts(20f, lh = 1.2f), Color(230, 240, 252).copy(alpha = 0.8f))
            }
            IceText(stringResource(R.string.park_daynight_help), ts(13f, display = false, lh = 1.4f), Color(196, 216, 238).copy(alpha = 0.52f), maxLines = 2, modifier = Modifier.padding(top = 8.dp))
            DayThumb(d, Modifier.padding(top = 14.dp))
            IceText(stringResource(R.string.park_when).uppercase(locale), ts(13f, 500, spacing = 0.16f), Color(196, 216, 238).copy(alpha = 0.48f), modifier = Modifier.padding(top = 16.dp))
            // .seg: cómo se sabe que es de noche (se puede cambiar sin quitar el reloj).
            Row(
                Modifier.padding(top = 10.dp).height(44.dp).clip(RoundedCornerShape(22.dp)).background(Color(170, 210, 255).copy(alpha = 0.04f))
                    .border(1.dp, Color(190, 225, 255).copy(alpha = 0.10f), RoundedCornerShape(22.dp)).padding(3.dp),
            ) {
                for ((src, label) in listOf(NightSource.SUN to R.string.park_sun, NightSource.SENSOR to R.string.park_sensor, NightSource.LIGHTS to R.string.park_lights)) {
                    val on = d.prefs.nightSource == src
                    Box(
                        Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(19.dp))
                            .then(if (on) Modifier.background(Color(120, 190, 255).copy(alpha = 0.16f)).border(1.dp, Color(170, 215, 255).copy(alpha = 0.28f), RoundedCornerShape(19.dp)) else Modifier)
                            .clickable {
                                d.prefs.nightSource = src
                                if (d.prefs.theme != ThemeMode.SUN) d.prefs.theme = ThemeMode.SUN
                            },
                        contentAlignment = Alignment.Center,
                    ) { IceText(stringResource(label), ts(13f, display = false), if (on) Color(0xFFCFE9FF) else Color(196, 216, 238).copy(alpha = 0.55f)) }
                }
            }
            val nightAt = remember(now) {
                val (lat, lon) = Daylight.place(context)
                val (_, sunset) = Daylight.sunTimes(Calendar.getInstance(), lat, lon)
                val cal = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, (sunset / 60).toInt()); set(Calendar.MINUTE, (sunset % 60).toInt()) }
                timeText(cal.timeInMillis, locale)
            }
            val place = d.prefs.placeName
            IceText(
                if (place != null) stringResource(R.string.park_night_from_in, place, nightAt) else stringResource(R.string.park_night_from, nightAt),
                ts(13f, display = false, lh = 1.4f), Color(196, 216, 238).copy(alpha = 0.52f), modifier = Modifier.padding(top = 10.dp),
            )
        }
    }
}

/** El inicio en su tema de día, en chiquito (.thumb): un dibujo, no una captura. */
@Composable
private fun DayThumb(d: Dependencies, modifier: Modifier) {
    val owner = OwnerCar.of(LocalContext.current).image
    Box(modifier.size(316.dp, 185.dp).clip(RoundedCornerShape(14.dp)).border(1.dp, Color(190, 225, 255).copy(alpha = 0.22f), RoundedCornerShape(14.dp))) {
        Box(
            Modifier.requiredSize(1024.dp, 600.dp).offset(354.dp, 207.5.dp).graphicsLayer(scaleX = 0.30859f, scaleY = 0.30859f, transformOrigin = TransformOrigin(0f, 0f))
                .background(Brush.verticalGradient(listOf(Color(0xFFF4F8FC), Color(0xFFE3ECF5), Color(0xFFD6E1EC)))),
        ) {
            BrandTitle(d.prefs.carName?.takeIf { it.isNotBlank() } ?: "Chevrolet Sail", Modifier.offset(34.dp, 34.dp))
            Row(Modifier.offset(30.dp, 66.dp), verticalAlignment = Alignment.Bottom) {
                IceText("0", ts(128f, 200), Color(0xFF0F1D31))
                IceText("km/h", ts(22f, 300), Color(0xFF5A6B82), modifier = Modifier.padding(start = 12.dp, bottom = 20.dp))
            }
            owner?.let { Image(it.bitmap.asImageBitmap(), null, Modifier.offset(40.dp, 150.dp).width(520.dp), contentScale = ContentScale.FillWidth) }
            val card = Modifier.clip(RoundedCornerShape(20.dp)).background(Color.White.copy(alpha = 0.88f)).border(1.dp, Color(20, 50, 90).copy(alpha = 0.08f), RoundedCornerShape(20.dp))
            Box(Modifier.offset(592.dp, 16.dp).size(364.dp, 140.dp).clip(RoundedCornerShape(20.dp)).background(Brush.linearGradient(listOf(Color(0xFFD6EBFF), Color(0xFFF5FAFF))))) {
                d.weather.weather?.let { w ->
                    Row(Modifier.offset(18.dp, 50.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(iceWeatherIcon(skyOf(w.code), true), Color(0xFF2F6FAE), 40.dp)
                        Spacer(Modifier.width(12.dp))
                        IceText("${w.temperature}°", ts(44f, 300), Color(0xFF0E1A2B))
                    }
                }
            }
            for (x in listOf(592, 780)) Box(Modifier.offset(x.dp, 168.dp).size(176.dp, 58.dp).clip(RoundedCornerShape(20.dp)).background(Brush.linearGradient(listOf(Color(0xFF3D97E8), Color(0xFF62B2F5)))))
            Box(Modifier.offset(592.dp, 238.dp).size(364.dp, 46.dp).then(card))
            Box(Modifier.offset(592.dp, 298.dp).size(364.dp, 210.dp).then(card)) {
                Box(Modifier.offset(151.dp, 132.dp).size(62.dp).clip(CircleShape).background(Color(0xFF0F1D31)))
            }
            for ((x, w) in listOf(32 to 190, 232 to 160, 402 to 88, 500 to 72)) Box(Modifier.offset(x.dp, 436.dp).size(w.dp, 72.dp).then(card))
            Box(Modifier.offset(0.dp, 528.dp).size(1024.dp, 72.dp).background(Color(0xFFFBFDFF)))
        }
        // .lab
        Row(
            Modifier.offset(10.dp, 10.dp).height(26.dp).clip(RoundedCornerShape(13.dp)).background(Color.White.copy(alpha = 0.9f)).padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(IceIcons.sun, Color(0xFFE19A1C), 16.dp)
            Spacer(Modifier.width(6.dp))
            IceText(stringResource(R.string.park_day_look), ts(13f, 500, display = false), Color(0xFF0D2340))
        }
    }
}
