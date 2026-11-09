package com.irvingguerra.litedash

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReverseRulesTest {
    private fun setting(key: String, on: String) = ReverseDecision.Learned(ReverseSource.Setting(key, on))

    @Test
    fun bajaSegunLaOpcion() {
        assertEquals(10, ReverseVolume.lowered(15, ReverseMode.LOWER))
        assertEquals(7, ReverseVolume.lowered(15, ReverseMode.HALF))
        assertEquals(0, ReverseVolume.lowered(15, ReverseMode.MUTE))
        assertNull(ReverseVolume.lowered(15, ReverseMode.NONE))
        // En enteros: 10 × 70 % es 7, no 6 (con decimales 0.7 daría 6.99…).
        assertEquals(7, ReverseVolume.lowered(10, ReverseMode.LOWER))
        assertEquals(21, ReverseVolume.lowered(30, ReverseMode.LOWER))
        assertEquals(1, ReverseVolume.lowered(2, ReverseMode.HALF))
    }

    @Test
    fun bajarNoEsSilenciar() {
        // En silencio o en 1 no hay nada que bajar: bajar nunca llega a 0.
        assertNull(ReverseVolume.lowered(0, ReverseMode.HALF))
        assertNull(ReverseVolume.lowered(0, ReverseMode.MUTE))
        assertNull(ReverseVolume.lowered(1, ReverseMode.LOWER))
        assertNull(ReverseVolume.lowered(1, ReverseMode.HALF))
        assertEquals(0, ReverseVolume.lowered(1, ReverseMode.MUTE))
    }

    @Test
    fun devuelveSoloSiNadieLoMovio() {
        assertEquals(15, ReverseVolume.restored(saved = 15, applied = 10, current = 10))
        // Lo subió o lo bajó en plena reversa: manda lo suyo.
        assertNull(ReverseVolume.restored(saved = 15, applied = 10, current = 12))
        assertNull(ReverseVolume.restored(saved = 15, applied = 10, current = 4))
        // Silenciado: se devuelve quitando el silencio, no con el nivel.
        assertNull(ReverseVolume.restored(saved = 15, applied = ReverseVolume.MUTED, current = 0))
    }

    @Test
    fun opcionesGuardadas() {
        assertEquals(ReverseMode.HALF, ReverseMode.from("half"))
        assertEquals(ReverseMode.MUTE, ReverseMode.from("mute"))
        assertEquals(ReverseMode.NONE, ReverseMode.from(null))
        assertEquals(ReverseMode.NONE, ReverseMode.from("otra"))
    }

    @Test
    fun antirreboteAlMeterYAlQuitar() {
        val d = ReverseDebounce(active = false)
        assertFalse(d.update(true, 0))
        assertEquals(ReverseDebounce.ON_MS, d.waitMs(0))
        assertFalse(d.update(true, 299))
        assertTrue(d.update(true, 300))
        assertTrue(d.active)
        assertNull(d.waitMs(300))
        // Maniobra: reversa → primera → reversa en menos de 2 s; la música no sube.
        assertFalse(d.update(false, 1_000))
        assertEquals(ReverseDebounce.OFF_MS, d.waitMs(1_000))
        assertFalse(d.update(true, 2_500))
        assertNull(d.waitMs(2_500))
        assertTrue(d.active)
        // Quitada de verdad: se confirma a los 2 s.
        assertFalse(d.update(false, 5_000))
        assertFalse(d.update(false, 6_999))
        assertTrue(d.update(false, 7_000))
        assertFalse(d.active)
    }

    @Test
    fun unaSenalQueTitilaNoCuenta() {
        val d = ReverseDebounce(active = false)
        // Cambia cada 100 ms: nunca se queda quieta 300 ms.
        for (t in 0L..2_000L step 100) assertFalse(d.update(t / 100 % 2 == 0L, t))
        assertFalse(d.active)
    }

    @Test
    fun arrancaEnReversaSiLaMusicaQuedoBaja() {
        // La app se cerró en plena reversa (música bajada): al volver ya no hay reversa → se devuelve.
        val d = ReverseDebounce(active = true)
        assertFalse(d.update(false, 0))
        assertTrue(d.update(false, ReverseDebounce.OFF_MS))
        assertFalse(d.active)
    }

    @Test
    fun leeLosAvisos() {
        assertEquals(true, ReverseBroadcasts.state(ReverseBroadcasts.SAILDASH, mapOf("state" to true)))
        assertEquals(false, ReverseBroadcasts.state(ReverseBroadcasts.SAILDASH, mapOf("state" to false)))
        assertEquals(true, ReverseBroadcasts.state("com.marca.EVENT", mapOf("Status" to 1)))
        assertEquals(false, ReverseBroadcasts.state("com.marca.EVENT", mapOf("reverse" to "0")))
        assertEquals(true, ReverseBroadcasts.state("com.marca.EVENT", mapOf("backcar" to "on")))
        assertEquals(true, ReverseBroadcasts.state("com.marca.ACTION_BACKCAR_ON", emptyMap()))
        assertEquals(false, ReverseBroadcasts.state("com.marca.backcar_stop", emptyMap()))
        assertNull(ReverseBroadcasts.state("com.marca.REVERSE", emptyMap()))
        assertNull(ReverseBroadcasts.state(ReverseBroadcasts.SAILDASH, mapOf("otra" to 1)))
        assertNull(ReverseBroadcasts.state(ReverseBroadcasts.SAILDASH, mapOf("state" to "quizás")))
        assertTrue(ReverseBroadcasts.SAILDASH in ReverseBroadcasts.ACTIONS)
    }

    @Test
    fun eligeElAjusteQueSuenaAReversa() {
        val before = mapOf(
            "system/reverse_status" to "0",
            "system/screen_brightness" to "120",
            "system/volume_music_speaker" to "10",
            "global/wifi_on" to "1",
        )
        val after = mapOf(
            "system/reverse_status" to "1",
            "system/screen_brightness" to "80",
            "system/volume_music_speaker" to "5",
            "global/wifi_on" to "1",
        )
        val list = ReverseLearning.candidates(before, after, emptyMap())
        // El volumen nunca (SailDash lo cambia: se escucharía a sí mismo); lo que no cambió, tampoco.
        assertEquals(listOf("system/reverse_status", "system/screen_brightness"), list.map { it.key })
        assertEquals(setting("system/reverse_status", "1"), ReverseLearning.decide(list, null))
    }

    @Test
    fun aunqueSeHayaQuitadoAntesDeLaSegundaFoto() {
        // Se vio pasar a 1 y volver a 0 antes de la segunda foto: igual es la reversa, con 1.
        val list = ReverseLearning.candidates(
            before = mapOf("system/backcar" to "0"),
            after = mapOf("system/backcar" to "0"),
            seen = mapOf("system/backcar" to listOf("1", "0")),
        )
        assertEquals(setting("system/backcar", "1"), ReverseLearning.decide(list, null))
    }

    @Test
    fun elValorEsElDeLaReversaNoElDeDespues() {
        // Palanca P → R → D mientras se aprendía: la reversa es R.
        val list = ReverseLearning.candidates(
            before = mapOf("global/car_gear" to "P"),
            after = mapOf("global/car_gear" to "D"),
            seen = mapOf("global/car_gear" to listOf("R", "D")),
        )
        assertEquals("R", list.single().onValue)
    }

    @Test
    fun unAjusteNuevoTambienCuenta() {
        // Algunas pantallas crean el ajuste la primera vez que entra la reversa.
        val list = ReverseLearning.candidates(emptyMap(), mapOf("system/rvs_state" to "1"), emptyMap())
        assertEquals(setting("system/rvs_state", "1"), ReverseLearning.decide(list, null))
    }

    @Test
    fun nombresQueEnganan() {
        assertTrue(ReverseLearning.named("system/backcar_state"))
        assertTrue(ReverseLearning.named("system/persist_rvc_on"))
        assertTrue(ReverseLearning.named("secure/rear_camera"))
        assertTrue(ReverseLearning.named("global/car_gear"))
        assertFalse(ReverseLearning.named("system/screen_backlight"))
        assertFalse(ReverseLearning.named("system/bt_playback_state"))
        assertFalse(ReverseLearning.named("secure/backup_enabled"))
        assertFalse(ReverseLearning.named("system/mcu_state"))
        // El freno de mano se suelta al arrancar: nunca es la reversa.
        assertTrue(ReverseLearning.ignored("system/parking_brake"))
        assertTrue(ReverseLearning.ignored("system/volume_music"))
        assertFalse(ReverseLearning.ignored("system/reverse_state"))
    }

    @Test
    fun loQueCambiaSinPararEsRuido() {
        val list = ReverseLearning.candidates(
            before = mapOf("system/camera_frames" to "0"),
            after = mapOf("system/camera_frames" to "6"),
            seen = mapOf("system/camera_frames" to listOf("1", "2", "3", "4", "5", "6")),
        )
        assertTrue(list.isEmpty())
    }

    @Test
    fun conVariosConNombreGanaElSiONo() {
        // El de 0/1 le gana al que no es un sí o no.
        val list = ReverseLearning.candidates(
            before = mapOf("system/reverse_on" to "0", "system/camera_app" to "launcher"),
            after = mapOf("system/reverse_on" to "1", "system/camera_app" to "avm"),
            seen = emptyMap(),
        )
        assertEquals(setting("system/reverse_on", "1"), ReverseLearning.decide(list, null))
    }

    @Test
    fun empatadosPregunta() {
        val list = ReverseLearning.candidates(
            before = mapOf("system/reverse_on" to "0", "system/backcar" to "0"),
            after = mapOf("system/reverse_on" to "1", "system/backcar" to "1"),
            seen = emptyMap(),
        )
        val choose = ReverseLearning.decide(list, null) as ReverseDecision.Choose
        assertEquals(
            listOf(ReverseSource.Setting("system/backcar", "1"), ReverseSource.Setting("system/reverse_on", "1")),
            choose.options,
        )
    }

    @Test
    fun sinNombreDeReversa() {
        // Uno solo que pasó de 0 a 1: ese.
        val one = ReverseLearning.candidates(mapOf("system/mcu_state_7" to "0"), mapOf("system/mcu_state_7" to "1"), emptyMap())
        assertEquals(setting("system/mcu_state_7", "1"), ReverseLearning.decide(one, null))
        // Uno solo que parece ruido (brillo): mejor que elija el usuario.
        val noisy = ReverseLearning.candidates(mapOf("system/screen_brightness" to "120"), mapOf("system/screen_brightness" to "80"), emptyMap())
        assertTrue(ReverseLearning.decide(noisy, null) is ReverseDecision.Choose)
        // Varios: que elija el usuario.
        val two = ReverseLearning.candidates(
            before = mapOf("system/a" to "0", "system/b" to "x"),
            after = mapOf("system/a" to "1", "system/b" to "y"),
            seen = emptyMap(),
        )
        assertTrue(ReverseLearning.decide(two, null) is ReverseDecision.Choose)
        // Un aviso de reversa le gana a los ajustes sin nombre.
        assertEquals(
            ReverseDecision.Learned(ReverseSource.Broadcast(ReverseBroadcasts.SAILDASH)),
            ReverseLearning.decide(two, ReverseBroadcasts.SAILDASH),
        )
        // Nada cambió: la pantalla no avisa.
        assertEquals(ReverseDecision.Nothing, ReverseLearning.decide(emptyList(), null))
    }

    @Test
    fun unAjusteConNombreLeGanaAlAviso() {
        // Un ajuste se puede volver a leer (sirve aunque la app se reinicie en plena reversa): mejor que el aviso.
        val list = ReverseLearning.candidates(mapOf("system/backcar" to "0"), mapOf("system/backcar" to "1"), emptyMap())
        assertEquals(setting("system/backcar", "1"), ReverseLearning.decide(list, ReverseBroadcasts.SAILDASH))
    }

    @Test
    fun ajusteGuardado() {
        assertEquals(ReverseKey("system", "backcar_state"), ReverseKey.fromStored("system/backcar_state"))
        assertEquals("global/car_gear", ReverseKey.fromStored("global/car_gear")?.stored)
        assertNull(ReverseKey.fromStored(null))
        assertNull(ReverseKey.fromStored("backcar"))
        assertNull(ReverseKey.fromStored("/backcar"))
        assertNull(ReverseKey.fromStored("system/"))
    }

    @Test
    fun noMasDeOchoOpciones() {
        val before = (1..12).associate { "system/k$it" to "a" }
        val after = (1..12).associate { "system/k$it" to "b" }
        val choose = ReverseLearning.decide(ReverseLearning.candidates(before, after, emptyMap()), null) as ReverseDecision.Choose
        assertEquals(ReverseLearning.MAX_OPTIONS, choose.options.size)
    }
}
