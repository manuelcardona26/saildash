// La música en grande del diseño C (catalogo/musica): de dónde suena (Spotify, Bluetooth o USB), lo
// que suena con su carátula y controles, la biblioteca de la USB al lado y tu Sail a la derecha.
package com.irvingguerra.litedash

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.irvingguerra.litedash.core.ui.Icon
import com.irvingguerra.litedash.core.ui.ice.BrandTitle
import com.irvingguerra.litedash.core.ui.ice.IceButton
import com.irvingguerra.litedash.core.ui.ice.IceButtonKind
import com.irvingguerra.litedash.core.ui.ice.IceIconButton
import com.irvingguerra.litedash.core.ui.ice.IceIcons
import com.irvingguerra.litedash.core.ui.ice.IcePill
import com.irvingguerra.litedash.core.ui.ice.IceSegmented
import com.irvingguerra.litedash.core.ui.ice.IceText
import com.irvingguerra.litedash.core.ui.ice.IceText2
import com.irvingguerra.litedash.core.ui.ice.IceType
import com.irvingguerra.litedash.core.ui.ice.IceWell
import com.irvingguerra.litedash.core.ui.ice.LocalIce
import com.irvingguerra.litedash.core.ui.ice.LocationLabel
import com.irvingguerra.litedash.core.ui.ice.IceBackdrop
import com.irvingguerra.litedash.core.ui.ice.Outfit
import com.irvingguerra.litedash.core.ui.ice.iceCard
import com.irvingguerra.litedash.core.ui.ice.iceSelected
import com.irvingguerra.litedash.core.ui.ice.iceSheet
import com.irvingguerra.litedash.core.ui.ice.softGlow
import com.irvingguerra.litedash.core.vehicle.Signal
import com.irvingguerra.litedash.core.vehicle.watch
import com.irvingguerra.litedash.feature.music.Drive
import com.irvingguerra.litedash.feature.music.Drives
import com.irvingguerra.litedash.feature.music.MusicMode
import com.irvingguerra.litedash.feature.music.MusicRepository
import com.irvingguerra.litedash.feature.music.Song
import com.irvingguerra.litedash.feature.music.rememberPosition
import com.irvingguerra.litedash.feature.player.FolderPicker
import com.irvingguerra.litedash.feature.player.Library
import com.irvingguerra.litedash.feature.player.Player
import com.irvingguerra.litedash.feature.player.PlayerSource
import com.irvingguerra.litedash.feature.player.Track
import com.irvingguerra.litedash.feature.player.R as PlayerR
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/**
 * La música en grande sobre las páginas del inicio (el dock queda abajo). [close] la cierra;
 * [onSound] abre Ajustes → Sonido (la tarjeta del estilo de sonido).
 */
@Composable
internal fun MusicPanel(d: Dependencies, close: () -> Unit, onSound: () -> Unit) {
    val ice = LocalIce.current
    val repo = d.music
    DisposableEffect(repo) {
        repo.use()
        onDispose { repo.release() }
    }
    Box(Modifier.size(1024.dp, 528.dp).background(ice.bg).clickable(remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, indication = null) {}) {
        IceBackdrop(R.drawable.bg_espacio)
        if (ice.dark) androidx.compose.foundation.Image(
            androidx.compose.ui.res.painterResource(R.drawable.blackhole_small), null,
            Modifier.offset(770.dp, 58.dp).size(150.dp, 90.dp).let { m -> m.then(Modifier.drawBehind { }) },
            alpha = 0.65f,
        )
        // .mp: la hoja de 640 × 492.
        Box(Modifier.offset(24.dp, 16.dp).size(640.dp, 492.dp).iceSheet()) {
            SourceRow(d, close)
            // .vline: la raya que separa lo que suena de la biblioteca.
            Box(
                Modifier.offset(330.dp, 84.dp).size(1.dp, 386.dp).background(
                    Brush.verticalGradient(0f to Color(190, 225, 255).copy(alpha = 0f), 0.2f to Color(190, 225, 255).copy(alpha = 0.14f), 0.8f to Color(190, 225, 255).copy(alpha = 0.14f), 1f to Color(190, 225, 255).copy(alpha = 0f)),
                ),
            )
            NowPlayingC(repo, Modifier.offset(20.dp, 80.dp).size(292.dp, 394.dp))
            MusicLibrary(repo, Modifier.offset(350.dp, 80.dp).size(270.dp, 394.dp))
        }
        // El lado del carro: marca y lugar, la velocidad, tu Sail y el estilo de sonido.
        Row(Modifier.offset(690.dp, 24.dp).width(304.dp), verticalAlignment = Alignment.CenterVertically) {
            BrandTitle(d.prefs.carName?.takeIf { it.isNotBlank() } ?: "Chevrolet Sail")
            Spacer(Modifier.weight(1f))
            rememberPlaceName(d)?.let { LocationLabel(it) }
        }
        SpeedSmall(d, Modifier.offset(690.dp, 62.dp))
        OwnerCarPicture(d, 646.dp, 156.dp, 390.dp, ground = listOf(650.dp, 300.dp, 420.dp, 150.dp), stage = listOf(660.dp, 256.dp, 360.dp, 100.dp))
        SoundCard(Modifier.offset(684.dp, 436.dp).size(316.dp, 72.dp), onSound)
    }
}

/** La velocidad chica del lado del carro (`.spd`: 200 64 y "km/h" 300 17). */
@Composable
private fun SpeedSmall(d: Dependencies, modifier: Modifier) {
    val ice = LocalIce.current
    val miles = d.prefs.units.miles
    val kmh = d.bus.watch(Signal.SPEED)
    val shown = ((kmh ?: 0f).toMiles(miles)).roundToInt().coerceAtLeast(0)
    Row(modifier) {
        IceText(
            shown.toString(),
            IceType.speed.copy(fontSize = 64.sp, shadow = Shadow(Color(150, 210, 255).copy(alpha = 0.25f), blurRadius = 22f)),
            ice.speed, modifier = Modifier.alignByBaseline(),
        )
        Spacer(Modifier.width(8.dp))
        IceText(stringResource(if (miles) R.string.speed_unit_mph else R.string.speed_unit_kmh), IceType.speedUnit.copy(fontSize = 17.sp), ice.ink2, modifier = Modifier.alignByBaseline())
    }
}

/** `.top`: Spotify, Bluetooth y USB (el método de la música, uno a la vez), abrir la app y cerrar. */
@Composable
private fun SourceRow(d: Dependencies, close: () -> Unit) {
    val repo = d.music
    val ice = LocalIce.current
    val appName = repo.preferredName().ifBlank { "Spotify" }
    Row(Modifier.offset(20.dp, 18.dp).size(600.dp, 44.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        IcePill(appName, repo.mode == MusicMode.APP, { repo.mode = MusicMode.APP }, icon = IceIcons.spotify, iconTint = Color(0xFF1ED760), padding = 18.dp)
        IcePill(stringResource(R.string.p2_src_bluetooth), repo.mode == MusicMode.BLUETOOTH, { repo.mode = MusicMode.BLUETOOTH }, icon = IceIcons.bluetooth, padding = 18.dp)
        IcePill("USB", repo.mode == MusicMode.FILES, { repo.mode = MusicMode.FILES }, icon = IceIcons.usb, padding = 18.dp)
        Spacer(Modifier.weight(1f))
        val app = repo.song?.pkg?.ifEmpty { null } ?: repo.modeApp
        if (app != null && !repo.isOwn(app)) IceIconButton(IceIcons.openExternal, { repo.openApp() }, iconSize = 22.dp)
        IceIconButton(IceIcons.close, close, iconSize = 22.dp, tint = ice.ink2)
    }
}

/** `.np`: carátula de 176, título, artista, de dónde, la barra y los controles. */
@Composable
private fun NowPlayingC(repo: MusicRepository, modifier: Modifier) {
    val ice = LocalIce.current
    val song = repo.song
    Box(modifier) {
        Artwork(song, Modifier.offset(58.dp, 4.dp).size(176.dp))
        val track = (Player.state.current as? Track)?.takeIf { repo.mode == MusicMode.FILES && song != null }
        val title = song?.title?.takeIf { it.isNotBlank() }?.let(::cleanTitle) ?: repo.starting?.let { stringResource(R.string.p2_music_opening, it) } ?: stringResource(R.string.music_idle)
        IceText(title, IceType.songTitle.copy(fontSize = 22.sp, textAlign = TextAlign.Center), ice.title, modifier = Modifier.offset(0.dp, 198.dp).width(292.dp))
        val artist = track?.let { listOf(artistOf(it, ""), albumOf(it, "")).filter { p -> p.isNotBlank() }.joinToString(" · ") }?.takeIf { it.isNotBlank() }
            ?: song?.artist?.takeIf { it.isNotBlank() } ?: song?.app ?: stringResource(R.string.p2_music_pick)
        IceText(artist, IceType.songArtist.copy(textAlign = TextAlign.Center), ice.ink2, modifier = Modifier.offset(0.dp, 228.dp).width(292.dp))
        Row(Modifier.offset(0.dp, 254.dp).width(292.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            val (icon, where) = when (repo.mode) {
                MusicMode.FILES -> IceIcons.usb to listOfNotNull("USB", (Player.state.current as? Track)?.folder?.takeIf { it.isNotBlank() }?.replace("/", " › ")).joinToString(" › ")
                MusicMode.BLUETOOTH -> IceIcons.bluetooth to (repo.bluetoothDevice ?: stringResource(R.string.p2_src_bluetooth))
                MusicMode.APP -> IceIcons.spotify to (song?.app?.takeIf { it.isNotBlank() } ?: repo.preferredName().ifBlank { "Spotify" })
            }
            Icon(icon, ice.ice, 16.dp)
            Spacer(Modifier.width(6.dp))
            IceText(where, IceType.small, ice.ink3)
        }
        TimeBarC(repo, song, Modifier.offset(4.dp, 284.dp).width(284.dp))
        Controls(repo, song, Modifier.offset(0.dp, 318.dp).width(292.dp))
    }
}

/** `.prog`: los tiempos a los lados y la barra de 3 dp con su perilla de 9; se toca o arrastra para adelantar. */
@Composable
private fun TimeBarC(repo: MusicRepository, song: Song?, modifier: Modifier) {
    val ice = LocalIce.current
    val duration = song?.durationMs ?: 0L
    val pos = if (song != null) rememberPosition(song).value else 0L
    var drag by remember { mutableStateOf<Float?>(null) }
    val fraction = drag ?: if (duration > 0) (pos.toFloat() / duration).coerceIn(0f, 1f) else 0f
    Row(modifier.height(20.dp), verticalAlignment = Alignment.CenterVertically) {
        IceText(formatTime(if (drag != null) (fraction * duration).toLong() else pos), IceType.small, ice.ink3)
        Spacer(Modifier.width(10.dp))
        val seekable = song?.seekable == true && duration > 0
        Box(
            Modifier
                .weight(1f)
                .height(20.dp)
                .then(
                    if (seekable) Modifier
                        .pointerInput(song) { detectTapGestures { o -> repo.seekTo((o.x / size.width * duration).toLong()) } }
                        .pointerInput(song) {
                            detectHorizontalDragGestures(
                                onDragStart = { o -> drag = (o.x / size.width).coerceIn(0f, 1f) },
                                onHorizontalDrag = { ch, dx -> ch.consume(); drag = ((drag ?: 0f) + dx / size.width).coerceIn(0f, 1f) },
                                onDragEnd = { drag?.let { repo.seekTo((it * duration).toLong()) }; drag = null },
                                onDragCancel = { drag = null },
                            )
                        }
                    else Modifier,
                )
                .drawBehind {
                    val h = 3.dp.toPx()
                    val cy = size.height / 2
                    val x = size.width * fraction
                    drawRoundRect(Color(190, 225, 255).copy(alpha = 0.14f), Offset(0f, cy - h / 2), Size(size.width, h), CornerRadius(h / 2))
                    drawRoundRect(Color(150, 210, 255).copy(alpha = 0.22f), Offset(0f, cy - h * 1.5f), Size(x, h * 3), CornerRadius(h * 1.5f))
                    drawRoundRect(Brush.horizontalGradient(listOf(Color(0xFF7CC6FF), Color(0xFFE3F4FF)), endX = x.coerceAtLeast(1f)), Offset(0f, cy - h / 2), Size(x, h), CornerRadius(h / 2))
                    drawCircle(Color(190, 230, 255).copy(alpha = 0.4f), 6.5.dp.toPx(), Offset(x, cy))
                    drawCircle(Color.White, 4.5.dp.toPx(), Offset(x, cy))
                },
        )
        Spacer(Modifier.width(10.dp))
        IceText(if (duration > 0) formatTime(duration) else "--:--", IceType.small, ice.ink3)
    }
}

/** `.ctrl`: aleatorio, anterior, play de 62 dp con su brillo, siguiente y repetir. */
@Composable
private fun Controls(repo: MusicRepository, song: Song?, modifier: Modifier) {
    val ice = LocalIce.current
    val playing = song?.playing == true
    // Aleatorio y repetir: los de la app (Spotify los publica) o, con la USB, el reproductor propio.
    val shuffleAction = song?.actions?.firstOrNull { "shuffle" in it.id.lowercase() || "aleator" in it.name.lowercase() || "shuffle" in it.name.lowercase() }
    val repeatAction = song?.actions?.firstOrNull { "repeat" in it.id.lowercase() || "repet" in it.name.lowercase() || "repeat" in it.name.lowercase() }
    val files = repo.mode == MusicMode.FILES
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
        SmallControl(IceIcons.shuffle, on = files && UsbShuffle.on, enabled = files || shuffleAction != null) {
            if (shuffleAction != null) repo.action(shuffleAction) else if (files) UsbShuffle.toggle()
        }
        BigControl(IceIcons.previous) { repo.previous() }
        Box(
            Modifier
                .softGlow(Color(170, 220, 255).copy(alpha = 0.35f), 16.dp)
                .size(62.dp)
                .clip(CircleShape)
                .background(Color.White)
                .clickable { repo.toggle() },
            contentAlignment = Alignment.Center,
        ) { Icon(if (playing) IceIcons.pauseBars else IceIcons.play, Color(0xFF0A1322), if (playing) 26.dp else 28.dp) }
        BigControl(IceIcons.next) { repo.next() }
        // La USB repite la lista sola (al acabar vuelve a la primera).
        SmallControl(IceIcons.repeat, on = files, enabled = files || repeatAction != null) { repeatAction?.let(repo::action) }
    }
}

@Composable
private fun BigControl(icon: ImageVector, onClick: () -> Unit) {
    Box(Modifier.size(48.dp).clip(CircleShape).clickable(onClick = onClick), contentAlignment = Alignment.Center) { Icon(icon, Color(0xFFEAF4FF), 26.dp) }
}

@Composable
private fun SmallControl(icon: ImageVector, on: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val ice = LocalIce.current
    Box(Modifier.size(48.dp).clip(CircleShape).clickable(enabled = enabled, onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, if (on) ice.ice else ice.ink3.copy(alpha = if (enabled) ice.ink3.alpha else ice.ink3.alpha * 0.5f), 22.dp)
    }
}

/** Aleatorio de la USB: baraja lo que falta de la lista (lo que suena sigue donde va). */
private object UsbShuffle {
    var on by mutableStateOf(false)
        private set

    fun toggle() {
        on = !on
        val s = Player.state
        val current = s.current ?: return
        if (s.source != PlayerSource.USB) return
        val rest = s.queue.filterIndexed { i, _ -> i != s.index }
        val queue = if (on) listOf(current) + rest.shuffled() else s.queue.sortedBy { (it as? Track)?.let { t -> t.folder + "/" + t.file } ?: it.key }
        if (on) Player.play(PlayerSource.USB, queue, 0) else Player.play(PlayerSource.USB, queue, queue.indexOf(current).coerceAtLeast(0))
    }
}

private enum class LibGroup(val title: Int) { FOLDERS(R.string.p2_lib_folders), ARTISTS(R.string.p2_lib_artists), ALBUMS(R.string.p2_lib_albums) }

/** Dónde se quedó la biblioteca (sobrevive a cerrar la música). */
private object LibMemory {
    var group by mutableStateOf(LibGroup.ARTISTS)
    var inside by mutableStateOf<String?>(null)
    var asked = false
}

/** `.lib`: Carpetas / Artistas / Álbumes, la miga de pan y las filas (USB). Con otro método, cómo cambiar a USB. */
@Composable
private fun MusicLibrary(repo: MusicRepository, modifier: Modifier) {
    val ice = LocalIce.current
    val context = LocalContext.current
    Column(modifier) {
        val memory = LibMemory
        IceSegmented(LibGroup.entries.map { it to stringResource(it.title) }, memory.group, { memory.group = it; memory.inside = null }, Modifier.fillMaxWidth(), fill = true, textStyle = IceText2.pill, padding = 4.dp)
        var permission by remember { mutableStateOf(Library.hasPermission(context)) }
        val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permission = it[Library.permission] == true }
        if (!permission) {
            LaunchedEffect(Unit) {
                if (!memory.asked && repo.mode == MusicMode.FILES) {
                    memory.asked = true
                    ask.launch((listOf(Library.permission) + Library.noticePermission).toTypedArray())
                }
            }
            LibNote(stringResource(PlayerR.string.library_no_permission)) {
                IceButton(stringResource(PlayerR.string.library_grant), { ask.launch((listOf(Library.permission) + Library.noticePermission).toTypedArray()) }, kind = IceButtonKind.PRIMARY)
            }
            return@Column
        }
        var mounts by remember { mutableIntStateOf(0) }
        DisposableEffect(Unit) {
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(c: Context, i: Intent) {
                    Drives.changed()
                    mounts++
                }
            }
            ContextCompat.registerReceiver(context, receiver, Drives.mediaFilter(scanner = true), ContextCompat.RECEIVER_EXPORTED)
            onDispose { runCatching { context.unregisterReceiver(receiver) } }
        }
        val loaded by produceState<Pair<Drive?, List<Track>>?>(null, mounts) {
            value = withContext(Dispatchers.IO) { Library.find(context) }
        }
        val (drive, tracks) = loaded ?: return@Column LibNote(stringResource(PlayerR.string.library_loading)) {}
        if (drive == null || tracks.isEmpty()) {
            // Sin memoria (o una que la pantalla no deja leer): elegir la carpeta con el selector de Android.
            LibNote(if (drive == null) stringResource(R.string.p2_lib_no_usb) else stringResource(PlayerR.string.library_unreadable, drive.name)) {
                FolderPicker(stringResource(PlayerR.string.library_pick_folder), onChosen = { mounts++ })
            }
            return@Column
        }
        val group = memory.group
        val groups = remember(tracks, group) {
            when (group) {
                LibGroup.FOLDERS -> tracks.groupBy { it.folder.ifBlank { drive.name } }
                LibGroup.ARTISTS -> tracks.groupBy { artistOf(it, drive.name) }
                LibGroup.ALBUMS -> tracks.groupBy { albumOf(it, drive.name) }
            }.toSortedMap(String.CASE_INSENSITIVE_ORDER)
        }
        val inside = memory.inside?.takeIf { it in groups }
        // .crumb: USB › la memoria … y cuántos hay.
        Row(Modifier.padding(start = 10.dp, end = 8.dp, top = 14.dp, bottom = 8.dp).fillMaxWidth().clickable(enabled = inside != null) { memory.inside = null }, verticalAlignment = Alignment.CenterVertically) {
            Icon(IceIcons.usb, ice.ice, 16.dp)
            Spacer(Modifier.width(6.dp))
            IceText("USB", IceType.small, ice.ink3)
            Icon(IceIcons.chevronSmall, ice.ink3, 14.dp, Modifier.padding(horizontal = 6.dp))
            IceText(inside ?: drive.name, IceType.small, ice.ink3, modifier = Modifier.weight(1f))
            val count = if (inside != null) groups[inside].orEmpty().size else groups.size
            IceText(
                if (inside != null) androidx.compose.ui.res.pluralStringResource(R.plurals.p2_lib_songs_n, count, count) else when (group) {
                    LibGroup.FOLDERS -> stringResource(R.string.p2_lib_n_folders, count)
                    LibGroup.ARTISTS -> stringResource(R.string.p2_lib_n_artists, count)
                    LibGroup.ALBUMS -> stringResource(R.string.p2_lib_n_albums, count)
                },
                IceType.small, ice.ink3,
            )
        }
        val playing = Player.state.current?.key
        if (inside != null) {
            val list = groups[inside].orEmpty()
            LazyColumn(Modifier.fillMaxWidth().weight(1f)) {
                items(list, key = { it.key }) { t ->
                    LibRow(cleanTitle(t.title), listOf(artistOf(t, drive.name), albumOf(t, drive.name)).distinct().joinToString(" · "), initials(cleanTitle(t.title)), t.key == playing) {
                        repo.mode = MusicMode.FILES
                        Player.play(PlayerSource.USB, list, list.indexOf(t))
                    }
                }
            }
        } else {
            val current = (Player.state.current as? Track)?.let { t ->
                when (group) {
                    LibGroup.FOLDERS -> t.folder.ifBlank { drive.name }
                    LibGroup.ARTISTS -> artistOf(t, drive.name)
                    LibGroup.ALBUMS -> albumOf(t, drive.name)
                }
            }
            LazyColumn(Modifier.fillMaxWidth().weight(1f)) {
                items(groups.keys.toList(), key = { it }) { name ->
                    val songs = groups[name].orEmpty()
                    val sub = if (group == LibGroup.ARTISTS) {
                        val albums = songs.map { albumOf(it, drive.name) }.distinct().size
                        androidx.compose.ui.res.pluralStringResource(R.plurals.p2_lib_albums_n, albums, albums) + " · " + androidx.compose.ui.res.pluralStringResource(R.plurals.p2_lib_songs_n, songs.size, songs.size)
                    } else androidx.compose.ui.res.pluralStringResource(R.plurals.p2_lib_songs_n, songs.size, songs.size)
                    LibRow(name, sub, initials(name), name == current, chevron = true) { memory.inside = name }
                }
            }
        }
    }
}

/**
 * El artista de una canción: el de sus datos o, si la memoria no los trae (carpeta leída por el
 * sistema de documentos), la primera carpeta ("Artista/Álbum/canción.mp3", como se acostumbra).
 */
private fun artistOf(t: Track, drive: String) = t.artist.ifBlank { t.folder.substringBefore('/').ifBlank { drive } }

/** El álbum: el de sus datos o la última carpeta. */
private fun albumOf(t: Track, drive: String) = t.album.ifBlank { t.folder.substringAfterLast('/').ifBlank { drive } }

@Composable
private fun LibNote(text: String, action: @Composable () -> Unit) {
    val ice = LocalIce.current
    Column(Modifier.padding(start = 10.dp, end = 10.dp, top = 18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Icon(IceIcons.usb, ice.ink3, 28.dp)
        IceText(text, IceText2.para, ice.ink2, maxLines = 5)
        action()
    }
}

/** Colores de los círculos de las filas (como en el mockup), por el nombre. */
private val AVATARS = listOf(
    Color(0xFFE0664A) to Color(0xFF7A2A6A), Color(0xFFF2B84B) to Color(0xFFC0632B), Color(0xFF58C4A8) to Color(0xFF1D6B6E),
    Color(0xFF7D97FF) to Color(0xFF3B55DC), Color(0xFFBBA4FF) to Color(0xFF7449EE),
)

private fun initials(name: String): String {
    val words = name.split(' ', '-', '_').filter { it.isNotBlank() && it.first().isLetterOrDigit() }
    // Una sola palabra ("ChocQuibTown"): sus mayúsculas.
    if (words.size == 1) {
        val caps = words[0].filter { it.isUpperCase() }.take(2)
        if (caps.length == 2) return caps
    }
    return words.take(2).joinToString("") { it.first().uppercase() }.ifEmpty { "♪" }
}

/** "01 Soy Yo" → "Soy Yo": sin el número de pista del nombre del archivo. */
private fun cleanTitle(title: String) = title.replace(Regex("""^\s*\d{1,3}\s*[-._]?\s+"""), "").ifBlank { title }

/** `.row`: 54 dp, el círculo de 38 con las iniciales, el nombre (16) y el renglón (13); la que suena, en hielo con sus barras. */
@Composable
private fun LibRow(title: String, sub: String, initials: String, selected: Boolean, chevron: Boolean = false, onClick: () -> Unit) {
    val ice = LocalIce.current
    val colors = AVATARS[(title.hashCode() and 0x7fffffff) % AVATARS.size]
    Row(
        Modifier
            .fillMaxWidth()
            .height(54.dp)
            .then(if (selected) Modifier.iceSelected(14.dp, Color(170, 215, 255).copy(alpha = 0.26f), 0.72f) else Modifier)
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(38.dp).clip(CircleShape)
                .background(Brush.linearGradient(listOf(colors.first, colors.second)))
                .border(1.dp, Color.White.copy(alpha = 0.14f), CircleShape),
            contentAlignment = Alignment.Center,
        ) { IceText(initials, IceText2.button.copy(fontSize = 14.sp, letterSpacing = 0.em), Color.White) }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            IceText(title, IceText2.rowTitle, if (selected) ice.title else ice.ink)
            IceText(sub, IceType.small, ice.ink3, modifier = Modifier.padding(top = 4.dp))
        }
        if (selected) PlayingBars()
        else if (chevron) Icon(IceIcons.chevronSmall, ice.ink3, 18.dp)
    }
}

/** `.bars`: las cuatro barritas de "sonando" (quietas: nada se anima sin necesidad). */
@Composable
private fun PlayingBars() {
    Row(Modifier.height(16.dp), horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.Bottom) {
        for (h in listOf(9, 16, 6, 12)) Box(
            Modifier.size(3.dp, h.dp).clip(RoundedCornerShape(2.dp)).background(Brush.verticalGradient(listOf(Color(0xFFE3F4FF), Color(0xFF7CC6FF)))),
        )
    }
}

/** `.snd`: el estilo de sonido que está puesto, con el atajo a Ajustes → Sonido. */
@Composable
internal fun SoundCard(modifier: Modifier, onClick: () -> Unit) {
    val ice = LocalIce.current
    val (title, sub) = eqSummary()
    Row(
        modifier.iceCard().clip(RoundedCornerShape(18.dp)).clickable(onClick = onClick).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IceWell(IceIcons.sliders, ice.ice)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            IceText(title, IceText2.rowTitle.copy(fontWeight = FontWeight.Medium, fontFamily = Outfit), ice.ink)
            IceText(sub, IceType.small, ice.ink2, modifier = Modifier.padding(top = 4.dp))
        }
        Icon(IceIcons.chevronSmall, ice.ink3, 20.dp)
    }
}

private fun formatTime(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, s / 60 % 60, s % 60) else "%d:%02d".format(s / 60, s % 60)
}
