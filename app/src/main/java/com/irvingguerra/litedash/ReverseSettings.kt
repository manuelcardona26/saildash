// Ajustes → Tu auto → Reversa: qué hacer con la música, probarlo y enseñarle a SailDash la reversa.
package com.irvingguerra.litedash

import android.os.SystemClock
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.irvingguerra.litedash.core.ui.Button
import com.irvingguerra.litedash.core.ui.Label
import com.irvingguerra.litedash.core.ui.LocalColors
import com.irvingguerra.litedash.core.ui.Segmented
import com.irvingguerra.litedash.core.ui.SwitchRow
import com.irvingguerra.litedash.core.ui.Typography
import kotlinx.coroutines.delay

/** La tarjeta de la reversa: la opción de la música y, si se toca la música, probar y aprender la señal. */
@Composable
internal fun ReverseSettings() {
    val context = LocalContext.current
    val r = remember { ReverseWatcher.get(context) }
    val c = LocalColors.current
    // El "¡Listo!" o el "No cambió nada" se ven una vez: al salir de la pestaña se limpian.
    DisposableEffect(r) { onDispose { r.clearResult() } }

    Label(stringResource(R.string.reversa_music), Typography.label, c.textMuted)
    Segmented(
        listOf(
            ReverseMode.NONE to stringResource(R.string.reversa_none),
            ReverseMode.LOWER to stringResource(R.string.reversa_lower),
            ReverseMode.HALF to stringResource(R.string.reversa_half),
            ReverseMode.MUTE to stringResource(R.string.reversa_mute),
        ),
        r.mode,
        r::pick,
        Modifier.horizontalScroll(rememberScrollState()),
    )
    if (r.mode == ReverseMode.NONE) return

    SwitchRow(stringResource(R.string.reversa_restore), r.restore, r::changeRestore, stringResource(R.string.reversa_restore_help))
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Button(stringResource(if (r.testing) R.string.reversa_testing else R.string.reversa_test), r::test)
        Label(stringResource(R.string.reversa_test_help), Typography.small, c.textMuted, modifier = Modifier.weight(1f))
    }
    if (remember { r.volumeFixed() }) Label(stringResource(R.string.reversa_fixed), Typography.small, c.danger)

    Label(stringResource(R.string.reversa_signal), Typography.label, c.textMuted)
    LearnReverse(r)
}

/** Aprender la señal: el estado, escuchar (con la cámara tapando todo), elegir entre varios o volver a intentar. */
@Composable
private fun LearnReverse(r: ReverseWatcher) {
    val c = LocalColors.current
    val gap = Arrangement.spacedBy(10.dp)
    when (val s = r.learn) {
        is ReverseLearnState.Listening -> {
            var left by remember(s.untilMs) { mutableIntStateOf(secondsLeft(s.untilMs)) }
            // Solo mientras se escucha (unos segundos): la cuenta regresiva.
            LaunchedEffect(s.untilMs) {
                while (left > 0) {
                    delay(1_000)
                    left = secondsLeft(s.untilMs)
                }
            }
            Label(stringResource(if (s.noticed) R.string.reversa_noticed else R.string.reversa_listening, left), Typography.body, c.accent)
            Button(stringResource(R.string.cancel), r::cancelLearning)
        }
        is ReverseLearnState.Choose -> {
            Label(stringResource(R.string.reversa_choose), Typography.body)
            FlowRow(horizontalArrangement = gap, verticalArrangement = gap) {
                for (o in s.options) Button(describe(o), { r.choose(o) })
                Button(stringResource(R.string.cancel), r::cancelLearning)
            }
        }
        ReverseLearnState.Nothing -> {
            Label(stringResource(R.string.reversa_nothing), Typography.body, c.danger)
            FlowRow(horizontalArrangement = gap, verticalArrangement = gap) {
                Button(stringResource(R.string.reversa_retry), r::startLearning, primary = true)
                Button(stringResource(R.string.cancel), r::cancelLearning)
            }
        }
        ReverseLearnState.Idle, is ReverseLearnState.Learned -> {
            val source = r.source
            when {
                s is ReverseLearnState.Learned -> Label(stringResource(R.string.reversa_learned, describe(s.source)), Typography.body, c.accent)
                source != null -> Label(
                    stringResource(if (r.reversing) R.string.reversa_now_on else R.string.reversa_now_off, describe(source)),
                    Typography.body,
                    if (r.reversing) c.accent else c.textMuted,
                )
                else -> Label(stringResource(R.string.reversa_not_learned), Typography.small, c.textMuted)
            }
            FlowRow(horizontalArrangement = gap, verticalArrangement = gap) {
                Button(stringResource(if (source != null) R.string.reversa_relearn else R.string.reversa_learn), r::startLearning, primary = source == null)
                if (source != null) Button(stringResource(R.string.reversa_forget), r::forget)
            }
        }
    }
}

/** Segundos que faltan (redondeado hacia arriba) hasta [untilMs]. */
private fun secondsLeft(untilMs: Long) = ((untilMs - SystemClock.elapsedRealtime() + 999) / 1_000).toInt().coerceAtLeast(0)

/** "system/backcar_state = 1" o "aviso co.saildash.action.REVERSE". */
@Composable
private fun describe(s: ReverseSource): String = when (s) {
    is ReverseSource.Setting -> "${s.key} = ${s.onValue}"
    is ReverseSource.Broadcast -> stringResource(R.string.reversa_broadcast, s.action)
}
