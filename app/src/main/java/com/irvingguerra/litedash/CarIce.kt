// SailDash 1.1 · diseño C: Ajustes → Tu carro → Reversa, Pico y placa y Recordatorios
// (catalogo/reversa, pico-y-placa, recordatorios). La lógica es la de siempre (ReverseWatcher,
// PicoPlacaRepository, ReminderRepository); aquí cambia cómo se ve y se suma la vista de reversa.
package com.irvingguerra.litedash

import com.irvingguerra.litedash.core.ui.ice.iceSelected
import com.irvingguerra.litedash.core.ui.ice.IconWell
import com.irvingguerra.litedash.core.ui.ice.IceIconButton
import com.irvingguerra.litedash.core.ui.ice.IceToggle
import com.irvingguerra.litedash.core.ui.ice.IceButton
import com.irvingguerra.litedash.core.ui.ice.IceButtonKind
import android.Manifest
import android.graphics.BitmapFactory
import android.os.SystemClock
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.irvingguerra.litedash.core.ui.Icon
import com.irvingguerra.litedash.core.ui.ice.IceIcons
import com.irvingguerra.litedash.core.ui.ice.IceText
import com.irvingguerra.litedash.core.ui.ice.LocalIce
import com.irvingguerra.litedash.core.ui.ice.glass
import com.irvingguerra.litedash.core.ui.ice.softGlow
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Las subpáginas de Tu carro en el diseño C (las pestañas de siempre: 2, 3 y 4). */
const val CAR_TAB_REMINDERS = 2
const val CAR_TAB_PICO = 3
const val CAR_TAB_REVERSE = 4

/** Tu carro → [tab] en el diseño C; [back] vuelve a Tu carro. */
@Composable
fun CarSubpage(d: Dependencies, tab: Int, back: () -> Unit) {
    SettingsCanvas(CanvasSrc.CARRO) {
        when (tab) {
            CAR_TAB_REVERSE -> ReversePage(back) { calibrate -> reverseView = calibrate }
            CAR_TAB_PICO -> PicoPage(d.picoPlaca, back)
            else -> RemindersPage(d, back)
        }
    }
}

/**
 * La vista de reversa abierta desde Tu carro → Reversa (true = calibrando). Ajustes la dibuja encima
 * de todo, dock incluido ([ReverseViewHost]): es la imagen de la cámara a pantalla completa.
 */
internal var reverseView by mutableStateOf<Boolean?>(null)

@Composable
internal fun ReverseViewHost() {
    reverseView?.let { calibrate -> ReverseView(startCalibrating = calibrate) { reverseView = null } }
}

// ================================================================ Reversa

@Composable
private fun BoxScope.ReversePage(back: () -> Unit, openView: (Boolean) -> Unit) {
    val ice = LocalIce.current
    val context = LocalContext.current
    val r = remember { ReverseWatcher.get(context) }
    androidx.compose.runtime.DisposableEffect(r) { onDispose { r.clearResult() } }
    SubHeader(stringResource(R.string.your_car), stringResource(R.string.reversa_title), back)
    // Lo de abajo se desliza: arriba, igual que el diseño; más abajo, la vista de reversa.
    Box(Modifier.at(266, 76, 742, 436).verticalScroll(rememberScrollState())) {
        Box(Modifier.fillMaxWidth().height(668.dp)) {
            // .c1 Música al meter reversa
            IceCard(Modifier.at(0, 0, 742, 194), radius = 20.dp) {
                Row(Modifier.at(16, 14, 710), verticalAlignment = Alignment.CenterVertically) {
                    IceText(stringResource(R.string.reversa_music), ts(17f, 500, lh = 1.2f), ice.ink, modifier = Modifier.weight(1f))
                    IceText(stringResource(R.string.rev_apps), ts(13f, display = false), ice.ink2)
                }
                Row(Modifier.at(16, 46, 710, 76), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val opts = listOf(
                        Triple(ReverseMode.NONE, R.string.reversa_none, R.string.rev_none_sub),
                        Triple(ReverseMode.LOWER, R.string.reversa_lower, R.string.rev_lower_sub),
                        Triple(ReverseMode.HALF, R.string.reversa_half, R.string.rev_half_sub),
                        Triple(ReverseMode.MUTE, R.string.reversa_mute, R.string.rev_mute_sub),
                    )
                    for ((mode, title, sub) in opts) {
                        val on = r.mode == mode
                        val shape = RoundedCornerShape(16.dp)
                        Box(
                            Modifier.weight(1f).fillMaxHeight().clip(shape)
                                .then(if (on) Modifier.iceSelected(16.dp, Color(170, 215, 255).copy(alpha = 0.45f)) else Modifier.background(Color(170, 210, 255).copy(alpha = 0.05f)).border(1.dp, ice.line2, shape))
                                .clickable { r.pick(mode) },
                        ) {
                            Row(Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(when (mode) { ReverseMode.NONE -> SetIcons.soundA; ReverseMode.LOWER -> SetIcons.soundLower; ReverseMode.HALF -> SetIcons.soundHalf; ReverseMode.MUTE -> SetIcons.soundMute }, if (on) Color(0xFFD8F0FF) else ice.ink3, 24.dp)
                                Spacer(Modifier.width(10.dp))
                                Column {
                                    IceText(stringResource(title), ts(15f, 500, lh = 1.1f), if (on) Color.White else ice.ink)
                                    IceText(stringResource(sub), ts(13f, display = false), ice.ink2, modifier = Modifier.padding(top = 6.dp))
                                }
                            }
                            if (on) Box(Modifier.align(Alignment.TopEnd).offset((-10).dp, 10.dp).softGlow(Color(0xFF8FD0FF).copy(alpha = 0.6f), 4.dp).size(8.dp).clip(CircleShape).background(Color(0xFFBFE6FF)))
                        }
                    }
                }
                HLine(Modifier.at(16, 136, 710))
                Row(Modifier.at(16, 137, 710, 48), verticalAlignment = Alignment.CenterVertically) {
                    Icon(SetIcons.history, Kc.ice, 22.dp)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        IceText(stringResource(R.string.reversa_restore), ts(15f, display = false, lh = 1.2f), ice.ink)
                        IceText(stringResource(R.string.rev_restore_help), ts(13f, display = false, lh = 1.4f), ice.ink2, modifier = Modifier.padding(top = 3.dp))
                    }
                    IceToggle(r.restore, onChange = r::changeRestore)
                }
            }
            // .c2 Aprender la reversa
            IceCard(Modifier.at(0, 206, 440, 230), radius = 20.dp) { LearnCard(r) }
            // .c3 Probar
            IceCard(Modifier.at(452, 206, 290, 230), radius = 20.dp) { TestCard(r) }
            // La vista de reversa de SailDash (solo si Android tiene una cámara que sirva).
            IceCard(Modifier.at(0, 448, 742, 208), radius = 20.dp) { ReverseViewCard(openView) }
        }
    }
}

@Composable
private fun BoxScope.LearnCard(r: ReverseWatcher) {
    val ice = LocalIce.current
    val s = r.learn
    Row(Modifier.at(18, 14, 404, 26), verticalAlignment = Alignment.CenterVertically) {
        IceText(stringResource(R.string.reversa_learn), ts(17f, 500, lh = 1.2f), ice.ink, modifier = Modifier.weight(1f))
        when {
            s is ReverseLearnState.Listening -> {
                var left by remember(s.untilMs) { mutableIntStateOf(secondsUntil(s.untilMs)) }
                LaunchedEffect(s.untilMs) { while (left > 0) { delay(1_000); left = secondsUntil(s.untilMs) } }
                Tag(stringResource(R.string.rev_listening_tag, left), Kc.ice)
            }
            r.source != null || s is ReverseLearnState.Learned -> Tag(stringResource(R.string.rev_learned), Kc.mint)
            else -> Tag(stringResource(R.string.rev_missing), Color(0xFFFFD98C))
        }
    }
    when (s) {
        is ReverseLearnState.Choose -> Column(Modifier.at(18, 52, 404, 110).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            IceText(stringResource(R.string.reversa_choose), ts(13f, display = false, lh = 1.35f), ice.ink2, maxLines = 3)
            for (o in s.options) IceButton(describeSource(o), { r.choose(o) }, Modifier.fillMaxWidth(), height = 38.dp, fontSize = 13f, kind = IceButtonKind.PLAIN)
        }
        ReverseLearnState.Nothing -> IceText(stringResource(R.string.reversa_nothing), ts(13f, display = false, lh = 1.4f), Kc.coral, maxLines = 5, modifier = Modifier.at(18, 52, 404))
        is ReverseLearnState.Learned -> IceText(stringResource(R.string.reversa_learned, describeSource(s.source)), ts(14f, display = false, lh = 1.4f), Kc.mint, maxLines = 4, modifier = Modifier.at(18, 56, 404))
        else -> {
            // .steps: los tres pasos con su línea.
            Box(Modifier.at(18 + 13, 52 + 14, 1, 86).background(Brush.verticalGradient(listOf(Kc.ice, Kc.ice.copy(alpha = 0.15f)))))
            val steps = listOf(R.string.rev_step1_plain to null, R.string.rev_step2 to R.string.rev_step2_sub, R.string.rev_step3 to null)
            steps.forEachIndexed { i, (t, sub) ->
                Row(Modifier.at(18, 52 + i * 38, 404, 38), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.softGlow(Color(150, 210, 255).copy(alpha = 0.3f), 5.dp).size(28.dp).clip(CircleShape)
                            .background(Brush.linearGradient(listOf(Color(0xFFE6F6FF), Color(0xFF9FD6FF)))),
                        contentAlignment = Alignment.Center,
                    ) { IceText("${i + 1}", ts(14f, 500), Color(0xFF06101E)) }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        IceText(stringResource(t), ts(14f, display = false, lh = 1.3f), ice.ink)
                        if (sub != null) IceText(stringResource(sub), ts(14f, display = false, lh = 1.3f), ice.ink2)
                    }
                }
            }
        }
    }
    Row(Modifier.align(Alignment.BottomStart).at(18, -14, 404), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        when (s) {
            is ReverseLearnState.Listening, is ReverseLearnState.Choose -> IceButton(stringResource(R.string.cancel), r::cancelLearning, Modifier.weight(1f), height = 48.dp, kind = IceButtonKind.PLAIN)
            ReverseLearnState.Nothing -> IceButton(stringResource(R.string.reversa_retry), r::startLearning, Modifier.weight(1f), IceButtonKind.PRIMARY, height = 48.dp)
            else -> {
                IceButton(stringResource(if (r.source != null) R.string.reversa_relearn else R.string.reversa_learn), r::startLearning, Modifier.weight(1f), IceButtonKind.PRIMARY, icon = SetIcons.gearR, height = 48.dp)
                if (r.source != null) IceButton(stringResource(R.string.reversa_forget), r::forget, height = 48.dp, kind = IceButtonKind.PLAIN)
            }
        }
    }
}

@Composable
private fun BoxScope.TestCard(r: ReverseWatcher) {
    val ice = LocalIce.current
    val context = LocalContext.current
    IceText(stringResource(R.string.rev_test_title), ts(17f, 500, lh = 1.2f), ice.ink, modifier = Modifier.at(16, 14))
    // La cola del carro (la foto privada de "tu carro", si está) con el brillo rojo de las luces.
    val rear = remember {
        runCatching {
            val f = File(File(context.filesDir, "car"), "rear.png")
            if (f.exists()) BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = 4 })?.asImageBitmap() else null
        }.getOrNull()
    }
    Row(Modifier.at(16, 46, 258, 72), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(64.dp, 72.dp), contentAlignment = Alignment.Center) {
            Box(Modifier.align(Alignment.BottomCenter).offset(y = 6.dp).size(84.dp, 14.dp).drawBehind {
                drawOval(Brush.radialGradient(listOf(Color(255, 60, 60).copy(alpha = 0.35f), Color.Transparent)))
            })
            if (rear != null) Image(rear, null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
            else Icon(SetIcons.carB, ice.ink2, 48.dp)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            for ((label, frac) in listOf(R.string.rev_normal to 0.8f, R.string.rev_reverse to r.mode.keep / 100f * 0.8f)) Row(verticalAlignment = Alignment.CenterVertically) {
                IceText(stringResource(label), ts(13f, display = false), ice.ink2, modifier = Modifier.width(58.dp))
                Box(Modifier.weight(1f).height(5.dp).clip(RoundedCornerShape(3.dp)).background(Color(190, 225, 255).copy(alpha = 0.14f))) {
                    Box(
                        Modifier.fillMaxWidth(frac.coerceAtLeast(0.001f)).fillMaxHeight().clip(RoundedCornerShape(3.dp))
                            .background(Brush.horizontalGradient(listOf(Color(0xFF7CC6FF), Color(0xFFE3F4FF)))),
                    )
                }
            }
        }
    }
    Column(Modifier.at(16, 128, 258)) {
        IceText(stringResource(R.string.rev_chip_note_b), ts(13f, 500, display = false, lh = 1.38f), ice.ink)
        IceText(stringResource(R.string.rev_chip_note), ts(13f, display = false, lh = 1.38f), ice.ink2)
    }
    IceButton(
        stringResource(if (r.testing) R.string.reversa_testing else R.string.rev_test_btn), r::test,
        Modifier.align(Alignment.BottomStart).at(16, -14, 258), icon = IceIcons.play, height = 44.dp, enabled = r.mode != ReverseMode.NONE, kind = IceButtonKind.PLAIN,
    )
}

private fun secondsUntil(untilMs: Long) = ((untilMs - SystemClock.elapsedRealtime() + 999) / 1_000).toInt().coerceAtLeast(0)

@Composable
private fun describeSource(s: ReverseSource): String = when (s) {
    is ReverseSource.Setting -> "${s.key} = ${s.onValue}"
    is ReverseSource.Broadcast -> stringResource(R.string.reversa_broadcast, s.action)
}

/** La tarjeta de la vista de reversa: con cámara, abrir y calibrar; sin ella, qué sí sirve. */
@Composable
private fun BoxScope.ReverseViewCard(openView: (Boolean) -> Unit) {
    val ice = LocalIce.current
    val context = LocalContext.current
    val askCamera = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok -> if (ok) CameraCheck.check(context) }
    val check = { if (CameraCheck.hasPermission(context)) CameraCheck.check(context) else askCamera.launch(Manifest.permission.CAMERA) }
    // Se revisa sola una vez (con permiso); después se usa lo guardado.
    LaunchedEffect(Unit) { if (CameraCheck.results == null && CameraCheck.hasPermission(context)) CameraCheck.check(context) }
    val cam = CameraCheck.usable(context)
    Row(Modifier.at(18, 16, 706, 26), verticalAlignment = Alignment.CenterVertically) {
        Icon(SetIcons.camera, Kc.ice, 22.dp)
        Spacer(Modifier.width(10.dp))
        IceText(stringResource(R.string.rev_view_title), ts(17f, 500, lh = 1.2f), ice.ink, modifier = Modifier.weight(1f))
        when {
            CameraCheck.checking -> Tag(stringResource(R.string.rev_view_checking), Kc.ice)
            cam != null -> Tag(stringResource(R.string.rev_view_ready, cam.resolution), Kc.mint)
            else -> Tag(stringResource(R.string.rev_view_none_title), Color(0xFFFFD98C))
        }
    }
    if (cam != null) {
        val key = guidesKey(cam.id)
        val guides = remember(key) { ReverseViewStore.load(context, key) }
        IceText(stringResource(R.string.rev_view_help), ts(14f, display = false, lh = 1.4f), ice.ink2, maxLines = 3, modifier = Modifier.at(18, 54, 440))
        // Un dibujo chiquito de las tres líneas.
        GuidesSketch(Modifier.at(480, 52, 244, 82).clip(RoundedCornerShape(12.dp)).background(Color(6, 12, 22).copy(alpha = 0.6f)).border(1.dp, ice.line2, RoundedCornerShape(12.dp)), guides)
        Row(Modifier.align(Alignment.BottomStart).at(18, -16, 706), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            IceButton(stringResource(R.string.rev_view_open), { openView(false) }, Modifier.width(250.dp), IceButtonKind.PRIMARY, icon = SetIcons.camera, height = 48.dp)
            IceButton(stringResource(R.string.rev_view_calibrate), { openView(true) }, height = 48.dp, kind = IceButtonKind.PLAIN)
            Spacer(Modifier.weight(1f))
            IceText(stringResource(if (guides.calibrated) R.string.rev_view_calibrated else R.string.rev_view_not_calibrated), ts(13f, display = false), if (guides.calibrated) Kc.mint else ice.ink3)
        }
    } else {
        IceText(stringResource(R.string.rev_view_none), ts(14f, display = false, lh = 1.4f), ice.ink, maxLines = 2, modifier = Modifier.at(18, 52, 706))
        IceText(stringResource(R.string.rev_view_factory), ts(13f, display = false, lh = 1.4f), ice.ink2, maxLines = 3, modifier = Modifier.at(18, 96, 706))
        Row(Modifier.align(Alignment.BottomStart).at(18, -16, 706), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            IceButton(stringResource(if (CameraCheck.checking) R.string.rev_view_checking else R.string.rev_view_check), check, icon = SetIcons.refresh, height = 44.dp, kind = IceButtonKind.SECONDARY, enabled = !CameraCheck.checking)
            if (!CameraCheck.hasPermission(context)) IceText(stringResource(R.string.rev_view_permission), ts(13f, display = false), ice.ink3)
        }
    }
}

/** La llave de las líneas guardadas: esta pantalla (en píxeles) y esta cámara. */
@Composable
private fun guidesKey(cameraId: String): String {
    val m = LocalContext.current.resources.displayMetrics
    return ReverseGuides.key(maxOf(m.widthPixels, m.heightPixels), minOf(m.widthPixels, m.heightPixels), cameraId)
}

private val GuideColors = listOf(Color(0xFFFF5A50), Color(0xFFFFC94D), Color(0xFF5BE39A))

/** Las tres líneas como se ven en la cámara (rojo a 0,5 m, amarillo a 1 m y verde a 2 m) con sus lados. */
@Composable
private fun GuidesSketch(modifier: Modifier, g: ReverseGuides, labels: Boolean = false, active: Int? = null) {
    val locale = currentLocale()
    val density = LocalDensity.current
    Box(modifier) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val stroke = (if (labels) 5.dp else 2.dp).toPx()
            val ends = ReverseGuides.DISTANCES.mapIndexed { i, d ->
                val half = g.halfWidthAt(d) * w
                Triple(Offset(w / 2 - half, g.ys[i] * h), Offset(w / 2 + half, g.ys[i] * h), GuideColors[i])
            }
            // Los lados: de la línea roja a la verde, cada tramo del color de su zona.
            for (i in 0 until 2) {
                val (a1, b1, c) = ends[i]
                val (a2, b2, _) = ends[i + 1]
                drawLine(c.copy(alpha = 0.8f), a1, a2, stroke * 0.7f, StrokeCap.Round)
                drawLine(c.copy(alpha = 0.8f), b1, b2, stroke * 0.7f, StrokeCap.Round)
            }
            ends.forEachIndexed { i, (a, b, c) ->
                if (active == i) drawLine(Color.White.copy(alpha = 0.35f), Offset(0f, a.y), Offset(w, a.y), 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 6.dp.toPx())))
                drawLine(c, a, b, stroke, StrokeCap.Round)
            }
        }
        if (labels) ReverseGuides.DISTANCES.forEachIndexed { i, d ->
            val half = g.halfWidthAt(d)
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val x = maxWidth * (0.5f + half) + 12.dp
                val y = maxHeight * g.ys[i] - 15.dp
                Box(
                    Modifier.offset(x, y).height(30.dp).clip(RoundedCornerShape(15.dp)).background(Color(4, 8, 16).copy(alpha = 0.72f))
                        .border(1.dp, GuideColors[i].copy(alpha = 0.7f), RoundedCornerShape(15.dp)).padding(horizontal = 12.dp),
                    contentAlignment = Alignment.Center,
                ) { IceText(guideLabel(d, locale), ts(16f, 500), GuideColors[i]) }
            }
        }
    }
}

/**
 * La vista de reversa de SailDash a pantalla completa: la imagen de la cámara (en espejo si se pide)
 * con las tres líneas. Calibrando, cada línea se arrastra hasta su marca y se guarda para esta
 * pantalla. Se abre solo desde aquí: nunca reemplaza ni tapa la cámara de fábrica.
 */
@Composable
fun ReverseView(startCalibrating: Boolean, close: () -> Unit) {
    val context = LocalContext.current
    val ice = LocalIce.current
    val cam = CameraCheck.usable(context) ?: return close()
    val key = guidesKey(cam.id)
    var guides by remember(key) { mutableStateOf(ReverseViewStore.load(context, key)) }
    var calibrating by remember { mutableStateOf(startCalibrating) }
    var dragging by remember { mutableStateOf<Int?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val askCamera = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok -> if (!ok) close() }
    LaunchedEffect(Unit) { if (!CameraCheck.hasPermission(context)) askCamera.launch(Manifest.permission.CAMERA) }
    androidx.activity.compose.BackHandler(onBack = close)
    Box(
        Modifier.fillMaxSize().background(Color.Black).clickable(remember { MutableInteractionSource() }, indication = null) {},
    ) {
        if (CameraCheck.hasPermission(context)) CameraPreview(cam.id, guides.mirrored, Modifier.fillMaxSize()) { error = it }
        // Las líneas; calibrando, cada una se arrastra (la más cercana al dedo).
        GuidesSketch(
            Modifier.fillMaxSize().then(
                if (calibrating) Modifier.pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onDragStart = { p ->
                            val y = p.y / size.height
                            dragging = guides.ys.withIndex().minByOrNull { kotlin.math.abs(it.value - y) }?.index
                        },
                        onDragEnd = { dragging = null },
                        onDragCancel = { dragging = null },
                    ) { change, _ ->
                        val i = dragging ?: return@detectVerticalDragGestures
                        guides = guides.drag(i, change.position.y / size.height)
                    }
                } else Modifier,
            ),
            guides, labels = true, active = dragging,
        )
        IceFrameOverlay {
            // Arriba: cerrar, espejo y calibrar (sobre la imagen, en vidrio oscuro).
            Row(Modifier.at(16, 16, 992, 52), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                IceIconButton(SetIcons.close, close, tint = Color.White, size = 52.dp, iconSize = 24.dp, fill = Color(4, 8, 16).copy(alpha = 0.6f), border = ice.line)
                Spacer(Modifier.weight(1f))
                IceButton(stringResource(R.string.rev_view_mirror), { guides = guides.copy(mirrored = !guides.mirrored); ReverseViewStore.save(context, key, guides) }, kind = if (guides.mirrored) IceButtonKind.BLUE else IceButtonKind.PLAIN, icon = SetIcons.mirror, height = 48.dp)
                if (!calibrating) IceButton(stringResource(R.string.rev_view_calibrate), { calibrating = true }, height = 48.dp, icon = SetIcons.ruler, kind = IceButtonKind.PLAIN)
            }
            if (calibrating) IceCard(Modifier.at(16, 80, 992, 76).clip(RoundedCornerShape(20.dp)).background(Color(4, 8, 16).copy(alpha = 0.7f)), radius = 20.dp) {
                Row(Modifier.fillMaxSize().padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(SetIcons.ruler, Kc.ice, 24.dp)
                    Spacer(Modifier.width(12.dp))
                    IceText(stringResource(R.string.rev_cal_help), ts(15f, display = false, lh = 1.35f), Color.White, maxLines = 2, modifier = Modifier.weight(1f))
                    Spacer(Modifier.width(12.dp))
                    IceButton(stringResource(R.string.rev_cal_reset), { guides = ReverseGuides(mirrored = guides.mirrored) }, height = 48.dp, kind = IceButtonKind.PLAIN)
                    Spacer(Modifier.width(10.dp))
                    IceButton(stringResource(R.string.rev_cal_save), {
                        guides = guides.copy(calibrated = true)
                        ReverseViewStore.save(context, key, guides)
                        calibrating = false
                    }, kind = IceButtonKind.PRIMARY, icon = SetIcons.check, height = 48.dp)
                }
            }
            error?.let { IceText(stringResource(R.string.rev_view_error, it), ts(15f, display = false), Kc.coral, modifier = Modifier.at(16, 80)) }
        }
    }
}

/** El lienzo de 1024 × 600 encima de algo a pantalla completa (para poner botones con las medidas del diseño). */
@Composable
internal fun IceFrameOverlay(content: @Composable BoxScope.() -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val s = minOf(maxWidth.value / 1024f, maxHeight.value / 600f)
        val d = LocalDensity.current
        androidx.compose.runtime.CompositionLocalProvider(LocalDensity provides androidx.compose.ui.unit.Density(d.density * s, d.fontScale)) {
            Box(Modifier.size(1024.dp, 600.dp), content = content)
        }
    }
}

// ================================================================ Pico y placa

@Composable
private fun BoxScope.PicoPage(repo: PicoPlacaRepository, back: () -> Unit) {
    val ice = LocalIce.current
    val locale = currentLocale()
    val scope = rememberCoroutineScope()
    SubHeader(stringResource(R.string.your_car), stringResource(R.string.pico_title), back) {
        IceText(stringResource(R.string.pp_notify), ts(13f, display = false, lh = 1.4f), ice.ink)
        IceToggle(repo.enabled, onChange = repo::turn)
    }
    // .c1 Ciudad y dígito
    IceCard(Modifier.at(266, 76, 742, 140), radius = 20.dp) {
        Row(Modifier.at(20, 16, 702, 48), verticalAlignment = Alignment.CenterVertically) {
            CapLabel(stringResource(R.string.pico_city), Modifier.width(150.dp), spacing = 0.16f)
            val cities = repo.rules.cities.take(3).map { it.id to it.name } + (PicoPlacaRepository.CUSTOM to stringResource(R.string.pico_custom_city))
            Row(
                Modifier.weight(1f).height(46.dp).clip(RoundedCornerShape(23.dp)).background(Color(4, 9, 18).copy(alpha = 0.55f)).border(1.dp, ice.line2, RoundedCornerShape(23.dp)).padding(3.dp),
            ) {
                for ((id, name) in cities) {
                    val on = repo.cityId == id
                    Box(
                        Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(20.dp))
                            .then(if (on) Modifier.softGlow(Color(150, 210, 255).copy(alpha = 0.25f), 8.dp).background(Brush.linearGradient(listOf(Color(0xFFE6F6FF), Color(0xFF9FD6FF))), RoundedCornerShape(20.dp)) else Modifier)
                            .clickable { if (!repo.enabled) repo.turn(true); repo.pickCity(id) },
                        contentAlignment = Alignment.Center,
                    ) { IceText(name, ts(15f, if (on) 500 else 400, display = false), if (on) Color(0xFF06101E) else ice.ink2) }
                }
            }
        }
        Row(Modifier.at(20, 76, 702, 48), verticalAlignment = Alignment.CenterVertically) {
            IceText(stringResource(R.string.pp_digit).uppercase(locale), ts(13f, 500, spacing = 0.16f, lh = 1.35f), ice.ink3, maxLines = 2, modifier = Modifier.width(150.dp))
            val city = repo.city
            val pair = city?.let { c -> upcomingDays(repo, c, 1).firstOrNull()?.let { c.digitsOn(it.first) } }.orEmpty()
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (dg in listOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 0)) {
                    val on = repo.digit == dg
                    val shape = RoundedCornerShape(14.dp)
                    var m = Modifier.weight(1f).height(46.dp).clip(shape)
                    m = when {
                        on -> m.softGlow(Color(255, 200, 100).copy(alpha = 0.3f), 8.dp).background(Brush.linearGradient(listOf(Color(0xFFFFE7AD), Color(0xFFFFC861))), shape).border(1.dp, Color(0xFFFFE2A0), shape)
                        dg in pair -> m.background(Color(170, 210, 255).copy(alpha = 0.06f)).border(1.dp, Color(255, 214, 120).copy(alpha = 0.35f), shape)
                        else -> m.background(Color(170, 210, 255).copy(alpha = 0.06f)).border(1.dp, ice.line2, shape)
                    }
                    Box(m.clickable { if (!repo.enabled) repo.turn(true); repo.pickDigit(dg) }, contentAlignment = Alignment.Center) {
                        IceText("$dg", ts(20f, if (on) 500 else 400), if (on) Color(0xFF06101E) else if (dg in pair) Color(0xFFFFE1A4) else ice.ink)
                    }
                }
            }
        }
    }
    // .c2 Próximos días
    IceCard(Modifier.at(266, 228, 362, 284), radius = 20.dp) {
        val city = repo.city
        val days = if (city != null && repo.digit in 0..9 || repo.cityId == PicoPlacaRepository.CUSTOM) upcomingDays(repo, city, 5) else emptyList()
        Row(Modifier.at(18, 14, 326), verticalAlignment = Alignment.Bottom) {
            IceText(stringResource(R.string.pp_next), ts(17f, 500, lh = 1.2f), ice.ink, modifier = Modifier.weight(1f))
            days.firstOrNull()?.let { (day, _) ->
                val digits = city?.digitsOn(day)?.sorted().orEmpty()
                if (digits.size >= 2) IceText(
                    stringResource(R.string.pp_day_rule, SimpleDateFormat("EEEE", locale).format(day.toDate()).replaceFirstChar { it.uppercase(locale) }, stringResource(R.string.pp_and, digits.dropLast(1).joinToString(", "), digits.last())),
                    ts(13f, display = false), ice.ink3,
                )
            }
        }
        Column(Modifier.at(18, 44, 326)) {
            if (days.isEmpty()) IceText(stringResource(if (repo.enabled) R.string.pp_none else R.string.pp_off), ts(14f, display = false, lh = 1.4f), ice.ink2, maxLines = 3)
            val today = Day.today()
            for ((day, holiday) in days) {
                Row(Modifier.height(34.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    val isToday = day == today
                    val label = SimpleDateFormat(if (locale.language == "es") "EEE d MMM" else "EEE MMM d", locale).format(day.toDate()).replace(".", "")
                    IceText(
                        label, ts(16f, if (isToday) 500 else 400).copy(textDecoration = if (holiday) androidx.compose.ui.text.style.TextDecoration.LineThrough else null),
                        if (holiday) ice.ink3 else if (isToday) Color(0xFFFFE1A4) else ice.ink, modifier = Modifier.width(96.dp),
                    )
                    Spacer(Modifier.width(12.dp))
                    if (isToday) Tag(stringResource(R.string.pp_today), Color(0xFF2A1C00), dot = false, height = 22.dp, borderAlpha = 0f, bg = Color(0xFFFFD47A))
                    if (holiday) Tag(stringResource(R.string.pp_holiday), Color(0xFF9DF2D0), dot = false, height = 22.dp, fill = 0.12f, borderAlpha = 0.3f)
                    Spacer(Modifier.weight(1f))
                    if (!holiday) IceText(city?.hours?.joinToString(", ") ?: "", ts(13f, display = false), ice.ink2)
                }
                HLine()
            }
        }
        Row(Modifier.align(Alignment.BottomStart).at(18, -14, 326), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                city?.to?.let {
                    IceText(stringResource(R.string.pp_valid), ts(13f, display = false, lh = 1.35f), ice.ink2)
                    IceText(shortDate(it, locale), ts(13f, 500, display = false, lh = 1.35f), ice.ink)
                }
            }
            var checking by remember { mutableStateOf(false) }
            if (repo.remoteAvailable) IceButton(stringResource(if (checking) R.string.pico_checking else R.string.pp_search), {
                if (!checking) scope.launch { checking = true; repo.refresh(); checking = false }
            }, icon = SetIcons.refresh, padding = 18.dp, kind = IceButtonKind.PLAIN)
        }
    }
    // .c3 Así sale al encender
    IceCard(Modifier.at(640, 228, 368, 284), radius = 20.dp) {
        Row(Modifier.at(16, 14, 336), verticalAlignment = Alignment.CenterVertically) {
            IceText(stringResource(R.string.pp_preview), ts(17f, 500, lh = 1.2f), ice.ink, modifier = Modifier.weight(1f))
            IceText(timeText(System.currentTimeMillis(), locale).uppercase(locale), ts(13f, 500, spacing = 0.1f), ice.ink3)
        }
        Box(Modifier.at(16, 46, 336, 178).clip(RoundedCornerShape(16.dp)).background(Color(0xFF060A12)).border(1.dp, ice.line, RoundedCornerShape(16.dp))) {
            Image(painterResource(R.drawable.bg_espacio), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop, alignment = androidx.compose.ui.BiasAlignment(0.4f, -0.4f))
            Image(painterResource(R.drawable.blackhole_small), null, Modifier.offset((-16).dp, 56.dp).size(150.dp, 92.dp), contentScale = ContentScale.Fit, alpha = 0.55f)
            Row(Modifier.offset(14.dp, 12.dp), verticalAlignment = Alignment.Bottom) {
                IceText("0", ts(54f, 200), Color(0xFFF4FAFF))
                IceText("km/h", ts(13f, 300), ice.ink2, modifier = Modifier.padding(start = 6.dp, bottom = 4.dp))
            }
            // .ban: el aviso del inicio.
            val hours = repo.city?.hours?.joinToString(", ") ?: "5:00–20:00"
            Box(
                Modifier.align(Alignment.BottomCenter).offset(y = (-10).dp).size(316.dp, 98.dp).clip(RoundedCornerShape(16.dp))
                    .background(Brush.linearGradient(listOf(Color(60, 44, 12).copy(alpha = 0.96f), Color(26, 22, 14).copy(alpha = 0.96f))))
                    .border(1.dp, Color(255, 214, 120).copy(alpha = 0.45f), RoundedCornerShape(16.dp)).padding(10.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(36.dp).clip(CircleShape).background(Color(255, 212, 122).copy(alpha = 0.12f)).border(1.dp, Color(255, 214, 120).copy(alpha = 0.3f), CircleShape), contentAlignment = Alignment.Center) {
                        Icon(IceIcons.plate, Color(0xFFFFD47A), 20.dp)
                    }
                    Spacer(Modifier.width(9.dp))
                    IceText(stringResource(R.string.pico_today, hours), ts(15f, 500, lh = 1.15f), Color.White)
                }
                Row(Modifier.align(Alignment.BottomStart).padding(start = 45.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    IceText(if (repo.digit in 0..9) stringResource(R.string.pp_plate, repo.digit) else "", ts(13f, display = false, lh = 1.2f), Color(255, 232, 190).copy(alpha = 0.85f), modifier = Modifier.weight(1f))
                    Box(Modifier.height(44.dp).clip(RoundedCornerShape(22.dp)).background(Color(0xFFFFD47A)).border(1.dp, Color(0xFFFFE2A0), RoundedCornerShape(22.dp)).padding(horizontal = 16.dp), contentAlignment = Alignment.Center) {
                        IceText(stringResource(R.string.pico_ok), ts(13f, 500), Color(0xFF2A1C00))
                    }
                }
            }
        }
        Row(Modifier.align(Alignment.BottomStart).at(16, -14, 336)) {
            Icon(SetIcons.info, Kc.ice, 18.dp, Modifier.padding(top = 1.dp))
            Spacer(Modifier.width(10.dp))
            IceText(stringResource(R.string.pp_note), ts(13f, display = false, lh = 1.4f), ice.ink2, maxLines = 2)
        }
    }
}

/** Los próximos [count] días que tocaría (con los festivos que no aplican, marcados con true). */
private fun upcomingDays(repo: PicoPlacaRepository, city: PicoCity?, count: Int): List<Pair<Day, Boolean>> {
    val c = city ?: return emptyList()
    val any = c.copy(holidays = true)
    val custom = repo.cityId == PicoPlacaRepository.CUSTOM
    fun hits(x: PicoCity, day: Day) = if (custom) x.digitsOn(day).isNotEmpty() else x.restricted(day, repo.digit)
    return generateSequence(Day.today()) { it.plusDays(1) }.take(90)
        .mapNotNull { day -> when { hits(c, day) -> day to false; hits(any, day) && ColombiaHolidays.isHoliday(day) -> day to true; else -> null } }
        .take(count).toList()
}

// ================================================================ Recordatorios

@Composable
private fun BoxScope.RemindersPage(d: Dependencies, back: () -> Unit) {
    val ice = LocalIce.current
    val locale = currentLocale()
    val repo = d.reminders
    val garage = Garage.get(LocalContext.current)
    var adding by remember { mutableStateOf(false) }
    var pad by remember { mutableStateOf<PadRequest?>(null) }
    SubHeader(stringResource(R.string.your_car), stringResource(R.string.reminders), back) {
        IceButton(stringResource(R.string.rc_add), { adding = true }, icon = SetIcons.plus, padding = 18.dp, kind = IceButtonKind.PLAIN)
    }
    // .list
    IceCard(Modifier.at(266, 76, 742, 374), radius = 20.dp) {
        if (repo.all.isEmpty()) IceText(stringResource(R.string.rc_empty), ts(15f, display = false, lh = 1.45f), ice.ink2, maxLines = 3, modifier = Modifier.at(24, 24, 690))
        Column(Modifier.at(18, 6, 706, 362).verticalScroll(rememberScrollState())) {
            repo.all.forEachIndexed { i, r ->
                val days = Day.today().daysUntil(r.date).toInt()
                val kmLeftNow = r.km?.let { km -> garage.odometer?.let { kmLeft(km, it.km) } }
                val urgent = days <= 0 || (kmLeftNow != null && kmLeftNow <= 0)
                val soon = !urgent && (days <= 7 || (kmLeftNow != null && kmLeftNow <= 500))
                val tone = when {
                    urgent -> Color(0xFFFF9EA8)
                    soon -> Color(0xFFFFD47A)
                    else -> Kc.ice
                }
                Row(Modifier.height(60.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconWell(r.type.icon(), if (urgent || soon) tone else Kc.ice, 42.dp, 22.dp)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        IceText(stringResource(r.type.title), ts(17f, 500, lh = 1.1f), ice.ink)
                        val date = shortDate(r.date, locale)
                        val repeat = when {
                            r.type.months <= 0 -> stringResource(R.string.rc_no_repeat)
                            r.type.renewal == ReminderRenewal.FROM_DUE -> stringResource(R.string.rc_yearly_same)
                            r.type.months == 12L -> stringResource(R.string.rc_yearly)
                            else -> stringResource(R.string.rc_every_months, r.type.months.toInt())
                        }
                        val sub = androidx.compose.ui.text.buildAnnotatedString {
                            val b = androidx.compose.ui.text.SpanStyle(color = ice.ink, fontWeight = androidx.compose.ui.text.font.FontWeight.Medium)
                            if (r.type == ReminderType.INSURANCE) append(stringResource(R.string.rc_due_on) + " ")
                            pushStyle(b); append(date); pop()
                            if (r.km != null) {
                                append(" " + stringResource(R.string.rc_or) + " ")
                                pushStyle(b); append(stringResource(R.string.recordatorios_at_km, ReminderKm.format(r.km, locale))); pop()
                            } else append(" · $repeat")
                        }
                        IceText(sub, ts(13f, display = false, lh = 1.2f), ice.ink2, modifier = Modifier.padding(top = 6.dp))
                    }
                    DueChip(dueText(days, kmLeftNow, locale), if (urgent) DueTone.RED else if (soon) DueTone.AMBER else if (days <= 31) DueTone.ICE else DueTone.NONE)
                    Spacer(Modifier.width(14.dp))
                    val doneTitle = if (r.type.hasKm) stringResource(r.type.kmQuestion) else ""
                    IceButton(
                        stringResource(R.string.reminder_done), {
                            if (r.type.hasKm) pad = PadRequest(doneTitle, "km", r.km?.let { (it + 5000).toString() } ?: "", format = ::groupDigits) { v -> repo.done(r, ReminderKm.parse(v)) }
                            else repo.done(r)
                        },
                        Modifier.width(112.dp), if (i == 0 && urgent) IceButtonKind.PRIMARY else IceButtonKind.PLAIN, icon = SetIcons.check, height = 44.dp, padding = 12.dp,
                    )
                }
                if (i < repo.all.lastIndex) HLine()
            }
        }
    }
    // .strip: lo que sale en el inicio.
    Row(
        Modifier.at(266, 460, 742, 52).glass(ice.glass, ice.line, ice.sheen, 26.dp).padding(start = 18.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CapLabel(stringResource(R.string.rc_in_home), spacing = 0.16f)
        Spacer(Modifier.width(14.dp))
        val next = repo.due().firstOrNull() ?: repo.all.firstOrNull()
        if (next != null) {
            val days = Day.today().daysUntil(next.date).toInt()
            val left = next.km?.let { km -> garage.odometer?.let { kmLeft(km, it.km) } }
            Row(
                Modifier.height(36.dp).clip(RoundedCornerShape(18.dp)).background(Color(6, 12, 22).copy(alpha = 0.55f)).border(1.dp, Color(255, 214, 120).copy(alpha = 0.32f), RoundedCornerShape(18.dp))
                    .padding(start = 10.dp, end = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(next.type.icon(), Color(0xFFFFD47A), 18.dp)
                Spacer(Modifier.width(8.dp))
                val st = ts(14f, display = false)
                IceText(stringResource(next.type.title) + " · ", st, Color(240, 232, 214).copy(alpha = 0.92f))
                IceText(dueText(days, left, locale), ts(14f, 500, display = false), Color.White)
                next.km?.let { IceText(" · " + stringResource(R.string.recordatorios_at_km, ReminderKm.format(it, locale)), st, Color(240, 232, 214).copy(alpha = 0.92f)) }
            }
        } else IceText(stringResource(R.string.rc_nothing_home), ts(14f, display = false), ice.ink2)
        Spacer(Modifier.weight(1f))
        IceText(stringResource(R.string.rc_days_before), ts(13f, display = false), ice.ink2, modifier = Modifier.padding(end = 10.dp))
    }
    if (adding) AddReminder(repo, garage, onPad = { pad = it }) { adding = false }
    pad?.let { p ->
        var value by remember(p) { mutableStateOf(p.initial) }
        NumberPad(p.title, value, { value = it }, p.unit, onDone = { p.onDone(value); pad = null }, onCancel = { pad = null }, hint = p.hint, format = p.format)
    }
}

/** "vence hoy", "faltan 3 días", "faltan 400 km" (lo que llegue primero), "faltan 2 meses". */
@Composable
private fun dueText(days: Int, kmLeft: Long?, locale: java.util.Locale): String {
    if (kmLeft != null && kmLeft <= 1_000 && (days > 7 || kmLeft <= 0)) {
        return if (kmLeft >= 0) stringResource(R.string.rc_km_left, groupThousands(kmLeft, locale)) else stringResource(R.string.taller_oil_over, groupThousands(-kmLeft, locale))
    }
    return when {
        days == 0 -> stringResource(R.string.reminder_today)
        days < 0 -> pluralStringResource(R.plurals.reminder_overdue, -days, -days)
        days < 45 -> pluralStringResource(R.plurals.reminder_in_days, days, days)
        days < 365 * 2 -> (days / 30).let { pluralStringResource(R.plurals.rc_in_months, it, it) }
        else -> (days / 365).let { pluralStringResource(R.plurals.rc_in_years, it, it) }
    }
}

private enum class DueTone { RED, AMBER, ICE, NONE }

@Composable
private fun DueChip(text: String, tone: DueTone) {
    val ice = LocalIce.current
    val (fg, bg, border) = when (tone) {
        DueTone.RED -> Triple(Color(0xFFFFB4BB), Color(255, 100, 115).copy(alpha = 0.13f), Color(255, 130, 140).copy(alpha = 0.45f))
        DueTone.AMBER -> Triple(Color(0xFFFFD98C), Color(255, 200, 100).copy(alpha = 0.11f), Color(255, 214, 120).copy(alpha = 0.40f))
        DueTone.ICE -> Triple(Kc.ice, Color(150, 210, 255).copy(alpha = 0.08f), Color(170, 215, 255).copy(alpha = 0.28f))
        DueTone.NONE -> Triple(ice.ink2, Color(170, 210, 255).copy(alpha = 0.05f), ice.line2)
    }
    Row(
        Modifier.width(136.dp).height(30.dp).clip(RoundedCornerShape(14.dp)).background(bg).border(1.dp, border, RoundedCornerShape(14.dp)).padding(horizontal = 10.dp),
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(fg))
        Spacer(Modifier.width(6.dp))
        IceText(text, ts(13f, 500, display = false), fg)
    }
}

/** Agregar: qué (fichas), su km si lleva, y el día en el calendario (tocar el día guarda). */
@Composable
private fun AddReminder(repo: ReminderRepository, garage: Garage, onPad: (PadRequest) -> Unit, close: () -> Unit) {
    val ice = LocalIce.current
    val locale = currentLocale()
    var type by remember { mutableStateOf<ReminderType?>(null) }
    var km by remember { mutableStateOf("") }
    FormSheet(type?.let { stringResource(R.string.rc_pick_date, stringResource(it.title)) } ?: stringResource(R.string.rc_pick_type), close) {
        val t = type
        if (t == null) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                for (row in ReminderType.entries.chunked(3)) Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    for (rt in row) IceTile(Modifier.weight(1f).height(70.dp), onClick = { type = rt; km = garage.odometer?.let { ((it.km / 1000).toLong() * 1000 + 5000).toString() } ?: "" }) {
                        Row(Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            IconWell(rt.icon(), Kc.ice, 36.dp, 18.dp)
                            Spacer(Modifier.width(10.dp))
                            IceText(stringResource(rt.title), ts(14f, 500, lh = 1.15f), Color.White, maxLines = 2)
                        }
                    }
                    repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        } else Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            if (t.hasKm) Column(Modifier.width(190.dp)) {
                val title = stringResource(R.string.rc_next_km)
                FormField(title, FuelMath.parsePesos(km)?.let { groupThousands(it.toLong(), locale) }, "km", Modifier.fillMaxWidth()) {
                    onPad(PadRequest(title, "km", km, format = ::groupDigits) { km = it })
                }
            }
            // Las seis semanas caben sin deslizar (la última no se corta).
            Box(Modifier.weight(1f).height(300.dp)) {
                DatePicker(onPick = { day -> repo.add(t, day, ReminderKm.parse(km)); close() }, cell = 38.dp, gap = 2.dp)
            }
        }
    }
}

private fun ReminderType.icon() = when (this) {
    ReminderType.INSURANCE, ReminderType.FULL_COVERAGE -> SetIcons.shieldR
    ReminderType.SERVICE -> SetIcons.oilR
    ReminderType.INSPECTION -> SetIcons.gaugeR
    ReminderType.EXTINGUISHER -> SetIcons.extinguisher
    ReminderType.TAX -> SetIcons.receipt
    ReminderType.PLATES -> SetIcons.idCard
    ReminderType.TIRE_ROTATION -> SetIcons.tire
    ReminderType.OTHER -> SetIcons.notebook
}
