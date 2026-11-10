package com.irvingguerra.litedash

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SplitLegacyTest {
    private val organic = "app.organicmaps"
    private val osmandPlus = "net.osmand.plus"
    private val osmand = "net.osmand"
    private val waze = "com.waze"
    private val gmaps = "com.google.android.apps.maps"
    private val autokit = "cn.manstep.phonemirrorBox"
    private val spotify = "com.spotify.music"
    private val newpipe = "org.schabi.newpipe"
    private val youtube = "com.google.android.youtube"

    @Test
    fun leftDefaultIsFirstInstalledMap() {
        assertEquals(organic, SplitPair.left(setOf(waze, gmaps, organic, spotify), chosen = null))
        assertEquals(osmandPlus, SplitPair.left(setOf(osmand, osmandPlus), chosen = null))
        assertEquals(osmand, SplitPair.left(setOf(gmaps, osmand), chosen = null))
        assertEquals(waze, SplitPair.left(setOf(autokit, gmaps, waze), chosen = null))
        assertEquals(gmaps, SplitPair.left(setOf(autokit, gmaps), chosen = null))
        // Sin mapas, AutoKit (CarPlay).
        assertEquals(autokit, SplitPair.left(setOf(autokit, spotify), chosen = null))
        assertNull(SplitPair.left(setOf(spotify, newpipe), chosen = null))
        assertNull(SplitPair.left(emptySet(), chosen = null))
    }

    @Test
    fun rightDefaultIsSpotifyNewPipeYouTubeThenYourMusic() {
        assertEquals(spotify, SplitPair.right(setOf(youtube, newpipe, spotify), chosen = null, music = null))
        assertEquals(newpipe, SplitPair.right(setOf(youtube, newpipe), chosen = null, music = null))
        assertEquals(youtube, SplitPair.right(setOf(youtube, organic), chosen = null, music = null))
        // Ninguna de las tres: la app de música elegida en SailDash, si está instalada.
        assertEquals("com.syu.music", SplitPair.right(setOf("com.syu.music", organic), chosen = null, music = "com.syu.music"))
        assertNull(SplitPair.right(setOf(organic), chosen = null, music = "com.syu.music"))
        assertNull(SplitPair.right(setOf(organic), chosen = null, music = null))
    }

    @Test
    fun chosenAppWinsWhileInstalled() {
        val installed = setOf(organic, waze, spotify, newpipe)
        assertEquals(waze, SplitPair.left(installed, chosen = waze))
        assertEquals(newpipe, SplitPair.right(installed, chosen = newpipe, music = null))
        // La elegida se desinstaló: vuelve la de fábrica.
        assertEquals(organic, SplitPair.left(installed, chosen = gmaps))
        assertEquals(spotify, SplitPair.right(installed, chosen = youtube, music = null))
    }

    @Test
    fun neverTheSameAppInBothHalves() {
        val installed = setOf(organic, spotify, newpipe)
        val left = SplitPair.left(installed, chosen = spotify)
        assertEquals(spotify, left)
        // La de la derecha también era Spotify: pasa a la siguiente.
        val right = SplitPair.right(installed, chosen = spotify, music = null, avoid = left)
        assertEquals(newpipe, right)
        assertNotEquals(left, right)
        // Al abrir un mapa, su pareja nunca es él mismo.
        assertEquals(organic, SplitPair.left(installed, chosen = null, avoid = spotify))
        assertNull(SplitPair.right(setOf(spotify), chosen = null, music = null, avoid = spotify))
    }

    @Test
    fun sideOfTheOpenedApp() {
        val maps = setOf("com.mapswithme.maps.pro")
        val music = setOf("deezer.android.app")
        assertEquals(SplitSide.LEFT, SplitPair.sideOf(waze, left = organic, right = spotify, maps = maps, music = music))
        assertEquals(SplitSide.LEFT, SplitPair.sideOf("com.mapswithme.maps.pro", organic, spotify, maps, music))
        assertEquals(SplitSide.RIGHT, SplitPair.sideOf(youtube, organic, spotify, maps, music))
        assertEquals(SplitSide.RIGHT, SplitPair.sideOf("deezer.android.app", organic, spotify, maps, music))
        // Ni mapa ni música: se abre normal.
        assertNull(SplitPair.sideOf("com.whatsapp", organic, spotify, maps, music))
        // AutoKit solo se divide si es la de la izquierda.
        assertNull(SplitPair.sideOf(autokit, organic, spotify, maps, music))
        assertEquals(SplitSide.LEFT, SplitPair.sideOf(autokit, autokit, spotify, maps, music))
        // Lo elegido manda sobre las listas: YouTube a la izquierda, un mapa a la derecha.
        assertEquals(SplitSide.LEFT, SplitPair.sideOf(youtube, youtube, spotify))
        assertEquals(SplitSide.RIGHT, SplitPair.sideOf(gmaps, organic, gmaps))
    }

    @Test
    fun launchOnAndroid7And8() {
        // Recién dividida: la izquierda normal (luego se acopla) y la derecha en la pila de pantalla completa.
        assertEquals(SplitLaunch(adjacent = false, stack = null), SplitPair.launch(SplitSide.LEFT, 25, already = false, canPickStack = true))
        assertEquals(SplitLaunch(adjacent = true, stack = SplitPair.STACK_FULLSCREEN), SplitPair.launch(SplitSide.RIGHT, 25, already = false, canPickStack = true))
        // Ya dividida: cada una directo a su pila.
        assertEquals(SplitLaunch(adjacent = true, stack = SplitPair.STACK_DOCKED), SplitPair.launch(SplitSide.LEFT, 25, already = true, canPickStack = true))
        assertEquals(SplitLaunch(adjacent = true, stack = SplitPair.STACK_FULLSCREEN), SplitPair.launch(SplitSide.RIGHT, 27, already = true, canPickStack = true))
    }

    @Test
    fun launchOnAndroid7WithoutPickingTheStack() {
        // Recién dividida el foco lo tienen las recientes: "al lado" mandaría la derecha a la izquierda.
        assertEquals(SplitLaunch(adjacent = false, stack = null), SplitPair.launch(SplitSide.RIGHT, 24, already = false, canPickStack = false))
        // Ya dividida, con la izquierda recién abierta (tiene el foco): "al lado" es la derecha.
        assertEquals(SplitLaunch(adjacent = true, stack = null), SplitPair.launch(SplitSide.RIGHT, 24, already = true, canPickStack = false))
        assertEquals(SplitLaunch(adjacent = true, stack = null), SplitPair.launch(SplitSide.LEFT, 24, already = true, canPickStack = false))
    }

    @Test
    fun launchOnAndroid9To12() {
        // Sin pilas: la derecha cae sola en la mitad libre (aunque se diga que sí se puede escoger).
        for (sdk in 28..31) {
            assertEquals(SplitLaunch(adjacent = false, stack = null), SplitPair.launch(SplitSide.RIGHT, sdk, already = false, canPickStack = true))
            assertEquals(SplitLaunch(adjacent = false, stack = null), SplitPair.launch(SplitSide.RIGHT, sdk, already = true, canPickStack = false))
            assertEquals(SplitLaunch(adjacent = true, stack = null), SplitPair.launch(SplitSide.LEFT, sdk, already = true, canPickStack = true))
            assertEquals(SplitLaunch(adjacent = false, stack = null), SplitPair.launch(SplitSide.LEFT, sdk, already = false, canPickStack = false))
        }
    }
}
