package com.irvingguerra.litedash

import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticsTest {
    private val w = DiagWords(
        yes = "sí", no = "no", missing = "no existe", empty = "(vacío)", hidden = "(oculto)",
        notThere = "(no estaba)", deleted = "(se borró)", sticky = "(el último guardado)", unreadable = "(extras ilegibles)",
        moreFormat = "y %1\$d más", system = "de sistema", updated = "actualizada", disabled = "apagada", version = "versión",
        opens = "abre", activities = "actividades exportadas", services = "servicios exportados",
        receivers = "receptores exportados", providers = "proveedores exportados", permission = "permiso",
        codeInside = "código dentro del APK", codeApart = "código aparte (odex)",
    )
    private val utc = TimeZone.getTimeZone("UTC")

    // --- Qué apps se muestran ---

    @Test
    fun googleAndAndroidInternalsAreHidden() {
        assertNull(DiagApps.classify("com.google.android.gms", "Servicios de Google Play", true))
        assertNull(DiagApps.classify("com.android.providers.media", "Almacenamiento de medios", true))
        assertNull(DiagApps.classify("android", "Sistema Android", true))
        assertNull(DiagApps.classify("com.android.vending", "Play Store", true))
    }

    @Test
    fun stockAndroidOnlyWhenUsefulAndNeverKey() {
        assertEquals(DiagAppKind.NORMAL, DiagApps.classify("com.android.settings", "", true))
        assertEquals(DiagAppKind.NORMAL, DiagApps.classify("com.android.musicfx", "", true))
        assertEquals(DiagAppKind.NORMAL, DiagApps.classify("com.android.bluetooth", "", true))
        assertNull(DiagApps.classify("com.android.systemui", "", true))
        // "backup" y "carrier" no cuentan como "back" ni "car".
        assertNull(DiagApps.classify("com.android.backupconfirm", "", true))
        assertNull(DiagApps.classify("com.android.carrierconfig", "", true))
    }

    @Test
    fun vendorRadioUnderAndroidNameIsKey() {
        // No es de serie: la puso el fabricante.
        assertEquals(DiagAppKind.KEY, DiagApps.classify("com.android.fmradio", "Radio FM", true))
    }

    @Test
    fun factoryAppsAreKeyByWhatTheyDo() {
        assertEquals(DiagAppKind.KEY, DiagApps.classify("com.tw.bt", "Bluetooth", true))
        assertEquals(DiagAppKind.KEY, DiagApps.classify("com.example.backcar", "Reversa", true))
        assertEquals(DiagAppKind.KEY, DiagApps.classify("com.syu.ms", "Servicio", true))
        assertEquals(DiagAppKind.KEY, DiagApps.classify("com.xyz.audioset", "Sonido", true))
        assertEquals(DiagAppKind.KEY, DiagApps.classify("com.xyz.canbus", "Info del coche", true))
        // De sistema pero sin nada clave: se muestra igual.
        assertEquals(DiagAppKind.NORMAL, DiagApps.classify("com.allwinner.filemanager", "Archivos", true))
    }

    @Test
    fun userAppsOnlyWhenTheyMatchAndKeyOnlyForBrands() {
        assertEquals(DiagAppKind.NORMAL, DiagApps.classify("com.spotify.music", "Spotify", false))
        assertEquals(DiagAppKind.NORMAL, DiagApps.classify("cn.manstep.phonemirrorBox", "AutoKit", false))
        assertNull(DiagApps.classify("org.schabi.newpipe", "NewPipe", false))
        // Una app de la marca instalada como normal sigue siendo de fábrica.
        assertEquals(DiagAppKind.KEY, DiagApps.classify("com.jyt.radio", "Radio", false))
    }

    @Test
    fun tokensSplitPackagesAndCamelCase() {
        assertEquals(listOf("cn", "manstep", "phonemirror", "box"), DiagApps.tokens("cn.manstep.phonemirrorBox"))
        assertEquals(listOf("com", "x", "fm", "radio", "música", "bt"), DiagApps.tokens("com.x.FmRadio Música BT"))
    }

    // --- Ajustes ---

    @Test
    fun settingsChangesInKeyOrder() {
        val before = mapOf("system/a" to "1", "system/b" to "x", "global/c" to "on")
        val after = mapOf("system/a" to "2", "system/b" to "x", "secure/d" to "")
        assertEquals(
            listOf(Triple("global/c", "on", null), Triple("secure/d", null, ""), Triple("system/a", "1", "2")),
            DiagSettings.changes(before, after),
        )
        assertTrue(DiagSettings.changes(before, before).isEmpty())
    }

    @Test
    fun settingsLine() {
        assertEquals("system/backlight: 0 → 1", DiagSettings.line("system/backlight", "0", "1", w))
        assertEquals("system/x: (no estaba) → 5", DiagSettings.line("system/x", null, "5", w))
        assertEquals("global/y: (vacío) → (se borró)", DiagSettings.line("global/y", "", null, w))
        val long = "a".repeat(500)
        assertEquals(200, DiagSettings.line("k", long, "b", w).substringAfter("k: ").substringBefore(" →").length)
    }

    @Test
    fun interestingAndPrivateSettings() {
        assertTrue(DiagSettings.interesting("system/nwd_radio_current_freq"))
        assertTrue(DiagSettings.interesting("system/backcar_state"))
        assertTrue(DiagSettings.interesting("system/acc_on"))
        assertTrue(DiagSettings.interesting("system/mcu_version"))
        assertFalse(DiagSettings.interesting("secure/accessibility_enabled"))
        assertFalse(DiagSettings.interesting("system/screen_brightness"))
        assertEquals("(oculto)", DiagSettings.redact("secure/android_id", "abc123", "(oculto)"))
        assertEquals("(oculto)", DiagSettings.redact("secure/bluetooth_address", "00:11", "(oculto)"))
        assertEquals("1", DiagSettings.redact("system/backcar_state", "1", "(oculto)"))
    }

    // --- Avisos y volumen ---

    @Test
    fun extrasSortedQuotedAndNested() {
        val extras = mapOf<String, Any?>("state" to 1, "name" to "FM 91.1", "empty" to "", "list" to intArrayOf(1, 2), "inner" to mapOf("k" to true), "nada" to null)
        assertEquals("{empty=\"\", inner={k=true}, list=[1, 2], nada=null, name=\"FM 91.1\", state=1}", DiagFormat.extras(extras, w))
        assertEquals("{}", DiagFormat.extras(emptyMap(), w))
    }

    @Test
    fun extrasAreCapped() {
        val extras = (1..35).associate { "k%02d".format(it) to it }
        val text = DiagFormat.extras(extras, w)
        assertTrue(text.endsWith(", y 5 más}"))
        assertTrue(text.contains("k30=30"))
        assertFalse(text.contains("k31="))
        assertEquals("[" + List(32) { 0 }.joinToString(", ") + ", …]", DiagFormat.value(List(40) { 0 }))
        // Un valor larguísimo se corta.
        assertEquals(120, DiagFormat.value("x".repeat(300)).length)
    }

    @Test
    fun broadcastLine() {
        assertEquals("com.fyt.boot.ACCON {state=1}", DiagFormat.broadcast("com.fyt.boot.ACCON", mapOf("state" to 1), false, w))
        assertEquals("android.intent.action.HEADSET_PLUG (el último guardado) {}", DiagFormat.broadcast("android.intent.action.HEADSET_PLUG", emptyMap(), true, w))
        assertEquals("com.x.RADIO (extras ilegibles)", DiagFormat.broadcast("com.x.RADIO", null, false, w))
    }

    @Test
    fun volumeLine() {
        val streams = listOf(Triple("VOICE_CALL", 4, 5), Triple("MUSIC", 8, 15))
        assertEquals("MUSIC: 7 → 8 · VOICE_CALL 4/5, MUSIC 8/15", DiagFormat.volume(3, 7, 8, streams))
        assertEquals("STREAM_42: ? → ?", DiagFormat.volume(42, null, null, emptyList()))
    }

    @Test
    fun audioNames() {
        assertEquals("MUSIC", DiagFormat.streamName(3))
        assertEquals("FM_TUNER", DiagFormat.deviceType(16))
        assertEquals("BUS", DiagFormat.deviceType(21))
        assertEquals("Equalizer", DiagFormat.effectType("0BED4300-DDD6-11DB-8F34-0002A5D5C51B"))
        assertEquals("LoudnessEnhancer", DiagFormat.effectType("fe3199be-aed0-413f-87bb-11260eb63cf1"))
        assertEquals("1234", DiagFormat.effectType("1234"))
    }

    // --- Formatos ---

    @Test
    fun listsSizesAndTimes() {
        assertEquals("receptores exportados (3): .A, .B, y 1 más", DiagFormat.list("receptores exportados", listOf(".A", ".B", ".C"), w, max = 2))
        assertEquals("2.5 MB", DiagFormat.size(2_621_440))
        assertEquals("1.5 GB", DiagFormat.size(1_610_612_736))
        assertEquals("3:00", DiagFormat.mmss(DiagnosticsRecorder.DURATION_MS))
        assertEquals("0:01", DiagFormat.mmss(1))
        assertEquals("0:00", DiagFormat.mmss(-5))
        val t = 1_794_234_662_000L // 2026-11-09 14:31:02 UTC
        assertEquals("14:31:02", DiagFormat.clock(t, utc))
        assertEquals("2026-11-09 14:31", DiagFormat.date(t, utc))
        assertEquals("2026-11-09_1431", DiagFormat.stamp(t, utc))
        assertEquals("14:31:02 · ajuste · system/x: 0 → 1", DiagFormat.event(t, "ajuste", "system/x: 0 → 1", utc))
    }

    // --- Android real y pantalla dividida ---

    @Test
    fun realAndroidVersion() {
        assertEquals("7.1", DiagVersion.name(25))
        assertEquals("12L", DiagVersion.name(32))
        assertNull(DiagVersion.name(99))
        // Lo que muestran muchas pantallas chinas: "12" con API 25.
        assertTrue(DiagVersion.fake("12", 25))
        assertFalse(DiagVersion.fake("7.1.2", 25))
        assertFalse(DiagVersion.fake("7.1", 25))
        assertFalse(DiagVersion.fake("12", 32))
        assertFalse(DiagVersion.fake("", 25))
        assertFalse(DiagVersion.fake("12", 99))
    }

    @Test
    fun splitScreenVerdict() {
        assertEquals(DiagSplit.OLD_ANDROID, DiagSplit.of(23, lowRam = false, config = true, forceResizable = false))
        assertEquals(DiagSplit.YES, DiagSplit.of(25, lowRam = false, config = true, forceResizable = false))
        assertEquals(DiagSplit.YES, DiagSplit.of(25, lowRam = false, config = null, forceResizable = false))
        assertEquals(DiagSplit.NO, DiagSplit.of(25, lowRam = false, config = false, forceResizable = false))
        assertEquals(DiagSplit.NO, DiagSplit.of(25, lowRam = true, config = true, forceResizable = false))
        assertEquals(DiagSplit.FORCED, DiagSplit.of(25, lowRam = false, config = false, forceResizable = true))
    }

    // --- Lo que dicen los manifiestos y el reporte ---

    @Test
    fun manifestNamesAndActions() {
        val probe = "\n    exportados: r:.Boot, s:.RadioService\n    manifiesto: com.jyt.radio.RadioService, com.jyt.action.BACKCAR_ON, " +
            "com.jyt.radio.action.freq_changed, com.jyt.permission.RADIO, com.jyt.intent.ACC, com.jyt.radio.FM, com.jyt.action.BACKCAR_ON"
        val names = DiagText.manifestNames(probe)
        assertEquals("com.jyt.radio.RadioService", names.first())
        assertEquals(7, names.size)
        assertEquals(
            listOf("com.jyt.action.BACKCAR_ON", "com.jyt.radio.action.freq_changed", "com.jyt.intent.ACC", "com.jyt.radio.FM"),
            DiagText.actionCandidates(names),
        )
        assertTrue(DiagText.manifestNames("sin nada").isEmpty())
    }

    @Test
    fun reportTextHasHeaderAndSections() {
        val text = DiagText.report(
            listOf("Diagnóstico", "SailDash 1.0.0"),
            listOf(DiagSection("Equipo y Android", listOf("Build.MODEL: A9")), DiagSection("Eventos grabados (0)", emptyList())),
        )
        assertEquals("Diagnóstico\nSailDash 1.0.0\n\n== Equipo y Android ==\nBuild.MODEL: A9\n\n== Eventos grabados (0) ==\n", text)
        assertEquals("abc", DiagText.clip("abc", 3))
        assertEquals("ab…", DiagText.clip("abcd", 3))
    }

    // --- La lista de eventos ---

    @Test
    fun cappedLogKeepsNewest() {
        val log = DiagLog<Int>(3)
        for (i in 1..5) log.add(i)
        assertEquals(listOf(3, 4, 5), log.all())
        assertEquals(listOf(5, 4), log.newest(2))
        assertEquals(listOf(5, 4, 3), log.newest(10))
        assertEquals(3, log.size)
        assertEquals(2, log.dropped)
        assertEquals(5, log.total)
        log.clear()
        assertEquals(0, log.total)
        assertTrue(log.newest(5).isEmpty())
    }
}
