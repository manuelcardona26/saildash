package com.irvingguerra.litedash

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EqCurveTest {
    /** El ecualizador de AOSP (NXP), el que traen casi todas las pantallas: 5 bandas de ±15 dB. */
    private val fiveBands = EqBands(listOf(60f, 230f, 910f, 3600f, 14000f), -15f, 15f)
    private val tenBands = EqBands(EQ_STANDARD_HZ, -15f, 15f)
    private val none = EqCaps()
    private val all = EqCaps(bassBoost = true, loudness = true, virtualizer = true)
    private val eps = 0.01f

    private fun curve(vararg points: Pair<Float, Float>) = EqCurve.of(points.map { EqPoint(it.first, it.second) })
    private fun preset(id: String) = EqPresets.byId(id)!!
    private fun boosted(db: Float) = EqSettings(curve = curve(*EQ_STANDARD_HZ.map { it to db }.toTypedArray()))

    @Test
    fun interpolatesInLogFrequency() {
        val c = curve(100f to 0f, 400f to 6f)
        // 200 Hz está a una octava de cada punto: justo a la mitad.
        assertEquals(3f, c.at(200f), eps)
        assertEquals(1.5f, c.at(141.42f), eps)
        assertEquals(0f, c.at(100f), eps)
        assertEquals(6f, c.at(400f), eps)
        // Afuera queda plana.
        assertEquals(0f, c.at(20f), eps)
        assertEquals(6f, c.at(16000f), eps)
        assertEquals(0f, EqCurve.FLAT.at(1000f), eps)
    }

    @Test
    fun presetsAreExactOnATenBandScreen() {
        for (p in EqPresets.all) {
            val mapped = EqMath.map(p.curve, tenBands)
            p.curve.points.forEachIndexed { i, point -> assertEquals("${p.id} en ${point.hz} Hz", point.db, mapped[i], 1e-4f) }
        }
    }

    @Test
    fun technoOnTheFiveRealBands() {
        // Las bandas de la pantalla no caen en las frecuencias estándar: se interpola en octavas.
        val mapped = EqMath.map(preset(EqPresets.TECHNO).curve, fiveBands)
        val expected = listOf(3.858f, -0.579f, -0.636f, 1.848f, 2.193f)
        expected.forEachIndexed { i, db -> assertEquals("banda ${fiveBands.hz[i]}", db, mapped[i], eps) }
    }

    @Test
    fun protectedBassCapsLowBands() {
        val out = EqMath.compute(boosted(12f), tenBands, none)
        // Subgrave +4, bajos +6, el resto +9 (lo que se oye, antes del margen).
        assertEquals(4f, out.heard[0], eps)
        assertEquals(6f, out.heard[1], eps)
        assertEquals(6f, out.heard[2], eps)
        for (i in 3 until 10) assertEquals(9f, out.heard[i], eps)
    }

    @Test
    fun headroomKeepsThePeakAtThreeDbAndLoudnessGivesItBack() {
        val out = EqMath.compute(boosted(12f), tenBands, all)
        assertEquals(6f, out.headroomDb, eps)
        assertEquals(3f, out.levels.max(), eps)
        assertEquals(600, out.loudnessMb)
        // Sin realce de volumen en la pantalla: el margen igual se deja (se baja el volumen, no distorsiona).
        val noLoudness = EqMath.compute(boosted(12f), tenBands, none)
        assertEquals(3f, noLoudness.levels.max(), eps)
        assertEquals(0, noLoudness.loudnessMb)
    }

    @Test
    fun withoutProtectionNothingIsCappedButTheScreenRange() {
        val out = EqMath.compute(boosted(12f).copy(protect = false), tenBands, all)
        assertEquals(0f, out.headroomDb, eps)
        assertEquals(0, out.loudnessMb)
        out.levels.forEach { assertEquals(12f, it, eps) }
        val wild = EqMath.compute(boosted(40f).copy(protect = false), tenBands, none)
        wild.levels.forEach { assertEquals(15f, it, eps) }
    }

    @Test
    fun clampsToTheScreenRange() {
        val narrow = fiveBands.copy(minDb = -3f, maxDb = 3f)
        for (p in EqPresets.all) for (protect in listOf(true, false)) {
            val out = EqMath.compute(EqSettings(protect = protect).withPreset(p), narrow, all)
            out.levels.forEach { assertTrue("${p.id}: $it", it in -3f..3f) }
        }
        assertEquals(1500.toShort(), EqMath.toMillibels(20f, fiveBands))
        assertEquals((-1500).toShort(), EqMath.toMillibels(-20f, fiveBands))
        assertEquals(346.toShort(), EqMath.toMillibels(3.456f, fiveBands))
        assertEquals(0.toShort(), EqMath.toMillibels(-0.004f, fiveBands))
    }

    @Test
    fun everyPresetStaysCleanWithProtection() {
        for (bands in listOf(fiveBands, tenBands)) for (p in EqPresets.all) {
            // Aun con los dos deslizadores al máximo.
            val s = EqSettings().withPreset(p).withBass(1f).withVoice(1f)
            for (caps in listOf(none, all)) {
                val out = EqMath.compute(s, bands, caps)
                val bassDb = out.bassStrength.toFloat() / EqMath.BASS_MAX * EqMath.BASS_DB_AT_MAX
                val peak = out.levels.indices.maxOf { out.levels[it] + bassDb * EqMath.lowShelf(bands.hz[it]) }
                assertTrue("${p.id} ${bands.hz.size} bandas $caps: pico $peak", peak <= EqMath.HEADROOM_DB + eps)
                out.heard.forEachIndexed { i, db -> assertTrue("${p.id}: $db", db <= EqMath.capAt(bands.hz[i]) + bassDb + eps) }
            }
        }
    }

    @Test
    fun bassBoostIsCappedWhenProtected() {
        val flat = EqSettings().withBass(1f)
        assertEquals(EqMath.BASS_PROTECTED_MAX, EqMath.compute(flat, fiveBands, all).bassStrength)
        assertEquals(EqMath.BASS_MAX, EqMath.compute(flat.copy(protect = false), fiveBands, all).bassStrength)
        // Bajo profundo ya sube ~5,8 dB en 60 Hz: entre los dos no pasan de +9 dB.
        val deep = EqSettings().withPreset(preset(EqPresets.DEEP_BASS)).withBass(1f)
        val out = EqMath.compute(deep, fiveBands, all)
        val low = EqMath.map(deep.curve, fiveBands)[0]
        assertEquals(5.81f, low, eps)
        assertTrue(out.bassStrength in 1 until EqMath.BASS_PROTECTED_MAX)
        assertTrue(low + out.bassStrength / 1000f * EqMath.BASS_DB_AT_MAX <= EqMath.LOW_BUDGET_DB + eps)
    }

    @Test
    fun bassWithoutBassBoostUsesTheCurve() {
        val out = EqMath.compute(EqSettings(protect = false).withBass(1f), fiveBands, none)
        assertEquals(0, out.bassStrength)
        assertEquals(5.1f, out.levels[0], 0.05f)
        assertTrue(out.levels[1] in 0.5f..1.5f)
        assertEquals(0f, out.levels[4], 0.05f)
    }

    @Test
    fun vocalClarityLiftsPresenceAndCutsMud() {
        val out = EqMath.compute(EqSettings(protect = false).withVoice(1f), fiveBands, none)
        assertEquals(0f, out.levels[0], 0.1f)
        assertEquals(-2.45f, out.levels[1], 0.05f)
        assertEquals(3.22f, out.levels[3], 0.05f)
        assertEquals(0f, out.levels[4], 0.1f)
    }

    @Test
    fun flatDoesNothing() {
        // Plano y sin extras: los efectos quedan apagados (no gastan CPU).
        assertTrue(EqMath.compute(EqSettings(), fiveBands, all).flat)
        assertTrue(EqMath.compute(EqSettings().withPreset(preset(EqPresets.FLAT)), fiveBands, all).flat)
        assertFalse(EqMath.compute(EqSettings().withPreset(preset(EqPresets.TECHNO)), fiveBands, all).flat)
        // El sonido envolvente solo si se pide y la pantalla lo trae.
        assertEquals(0, EqMath.compute(EqSettings(virtualizer = true), fiveBands, none).virtualizerStrength)
        assertEquals(EqMath.VIRTUALIZER_STRENGTH, EqMath.compute(EqSettings(virtualizer = true), fiveBands, all).virtualizerStrength)
    }

    @Test
    fun nightAndPodcastBringExtraLoudness() {
        val night = EqMath.compute(EqSettings().withPreset(preset(EqPresets.NIGHT)), fiveBands, all)
        assertTrue(night.loudnessMb >= 400)
        assertEquals(0, EqMath.compute(EqSettings().withPreset(preset(EqPresets.NIGHT)), fiveBands, none).loudnessMb)
        assertTrue(EqMath.compute(EqSettings().withPreset(preset(EqPresets.PODCAST)), fiveBands, all).loudnessMb >= 200)
    }

    @Test
    fun invalidScreensAreIgnored() {
        assertFalse(EqBands(listOf(60f), 0f, 0f).valid)
        assertFalse(EqBands(emptyList(), -15f, 15f).valid)
        assertFalse(EqBands(listOf(0f, 100f), -15f, 15f).valid)
        assertEquals(EqOutput.NONE, EqMath.compute(EqSettings(enabled = true), EqBands(listOf(60f), 5f, -5f), all))
    }

    @Test
    fun editingABandTurnsTheCurveIntoScreenBands() {
        val techno = EqSettings().withPreset(preset(EqPresets.TECHNO))
        val edited = techno.withBand(fiveBands, 2, 4f)
        assertTrue(edited.edited)
        assertEquals(EqPresets.TECHNO, edited.preset)
        assertEquals(fiveBands.hz, edited.curve.points.map { it.hz })
        assertEquals(4f, edited.curve.at(910f), eps)
        assertEquals(3.858f, edited.curve.at(60f), eps)
        // Volver a tocar el estilo deshace la edición.
        assertEquals(techno, edited.withPreset(preset(EqPresets.TECHNO)).copy(bass = techno.bass))
    }

    @Test
    fun slotsAndReset() {
        assertEquals("slot1", eqSlotId(0))
        assertEquals(2, eqSlotIndex("slot3"))
        assertNull(eqSlotIndex("slot4"))
        assertNull(eqSlotIndex("slot0"))
        assertNull(eqSlotIndex(EqPresets.TECHNO))
        assertNull(eqSlotIndex(null))
        val slot = EqSlot("Techno en carretera", preset(EqPresets.TECHNO).curve, 0.3f, 0.6f, 0f)
        val s = EqSettings(enabled = true, mode = EqMode.APPS).withSlot(1, slot)
        assertEquals(1, s.slot)
        assertEquals(0.3f, s.bass, 0f)
        assertFalse(s.edited)
        // En "Mis ajustes", mover un deslizador cuenta como editar; en un estilo de fábrica, no.
        assertTrue(s.withBass(0.5f).edited)
        assertFalse(EqSettings().withPreset(preset(EqPresets.ROCK)).withVoice(0.5f).edited)
        val reset = s.copy(protect = false, virtualizer = true).reset()
        assertEquals(EqSettings(enabled = true, mode = EqMode.APPS), reset)
        assertTrue(reset.protect)
    }

    @Test
    fun curvesSaveAndLoad() {
        val c = curve(60f to 3.5f, 230f to -1f, 14000f to 0f)
        assertEquals("60:3.5;230:-1;14000:0", c.encode())
        assertEquals(c, EqCurve.decode(c.encode()))
        for (p in EqPresets.all) assertEquals(p.curve, EqCurve.decode(p.curve.encode()))
        // Se ordena, se descarta lo malo y una frecuencia repetida queda una vez.
        assertEquals(curve(60f to 1f, 230f to -1f), EqCurve.decode("230:-1; x:2 ;60:1;60:5;-5:3;abc"))
        assertNull(EqCurve.decode(""))
        assertNull(EqCurve.decode(null))
        assertNull(EqCurve.decode("basura"))
        assertEquals("", EqCurve.FLAT.encode())
    }

    @Test
    fun sessionsSaveAndLoad() {
        val map = linkedMapOf(12 to "com.spotify.music", 34 to null)
        assertEquals("12:com.spotify.music,34:", EqSessions.encode(map))
        assertEquals(map, EqSessions.decode(EqSessions.encode(map)))
        assertEquals(linkedMapOf<Int, String?>(5 to null), EqSessions.decode("x,0:a,-3:b,5"))
        val many = (1..12).associateWith { "app$it" }
        val kept = EqSessions.decode(EqSessions.encode(many))
        assertEquals(EqSessions.MAX, kept.size)
        // Quedan las más nuevas.
        assertEquals((5..12).toList(), kept.keys.toList())
        assertTrue(EqSessions.decode(null).isEmpty())
    }

    @Test
    fun labels() {
        assertEquals("60", EqMath.freqLabel(60f))
        assertEquals("230", EqMath.freqLabel(230f))
        assertEquals("910", EqMath.freqLabel(910f))
        assertEquals("3.6k", EqMath.freqLabel(3600f))
        assertEquals("14k", EqMath.freqLabel(14000f))
        assertEquals("1k", EqMath.freqLabel(1000f))
        assertEquals("63", EqMath.freqLabel(62.5f))
        assertEquals("0", EqMath.dbLabel(0f))
        assertEquals("0", EqMath.dbLabel(0.2f))
        assertEquals("+3.5", EqMath.dbLabel(3.5f))
        assertEquals("−2", EqMath.dbLabel(-2f))
        assertEquals("−0.5", EqMath.dbLabel(-0.3f))
        assertEquals("+12", EqMath.dbLabel(12f))
        assertEquals(-12f to 12f, EqMath.editorRange(fiveBands))
        assertEquals(-6f to 6f, EqMath.editorRange(fiveBands.copy(minDb = -6f, maxDb = 6f)))
    }

    @Test
    fun findsTheFactorySoundApp() {
        assertTrue(EqFactoryApps.score("EQ", "com.tw.eq", true) > 0)
        assertTrue(EqFactoryApps.score("Sonido", "com.syu.sound", true) > 0)
        assertTrue(EqFactoryApps.score("Ecualizador", "com.nwd.android.eq", true) > 0)
        assertTrue(EqFactoryApps.score("Ajustes de sonido", "com.syu.settings", true) > 0)
        assertTrue(EqFactoryApps.score("MusicFX", "com.android.musicfx", true) > 0)
        assertTrue(EqFactoryApps.score("DSP", "com.hct.dsp", true) > 0)
        // Una palabra exacta gana sobre un pedazo de palabra.
        assertTrue(EqFactoryApps.score("EQ", "com.tw.eq", true) > EqFactoryApps.score("AudioTool", "com.x.audiotool", true))
        // Lo que no es: música, Bluetooth, grabadoras, radio, apps instaladas por el usuario…
        assertEquals(0, EqFactoryApps.score("Música BT", "com.syu.btmusic", true))
        assertEquals(0, EqFactoryApps.score("Bluetooth audio", "com.syu.bt", true))
        assertEquals(0, EqFactoryApps.score("Grabadora de sonido", "com.android.soundrecorder", true))
        assertEquals(0, EqFactoryApps.score("Radio", "com.syu.radio", true))
        assertEquals(0, EqFactoryApps.score("Ajustes", "com.android.settings", true))
        assertEquals(0, EqFactoryApps.score("Request Manager", "com.x.requests", true))
        assertEquals(0, EqFactoryApps.score("SoundCloud", "com.soundcloud.android", false))
        assertEquals(0, EqFactoryApps.score("Equalizer FX", "com.devdnua.equalizer", false))
    }

    // ---- Grupo Urbano: Reggaetón, Techno y Electrónica ----

    private fun db(p: EqPreset, hz: Float) = p.curve.at(hz)

    @Test
    fun urbanIsItsOwnGroup() {
        val ids = EqPresets.urban.map { it.id }
        assertEquals(listOf(EqPresets.REGGAETON, EqPresets.URBAN_TECHNO, EqPresets.ELECTRONICA), ids)
        // Aparte de los de siempre, pero se encuentran y se guardan igual.
        assertTrue(EqPresets.normal.none { it.id in ids })
        assertEquals(EqPresets.normal + EqPresets.urban, EqPresets.all)
        assertEquals(EqPresets.all.size, EqPresets.all.map { it.id }.distinct().size)
        for (p in EqPresets.urban) {
            assertEquals(p, EqPresets.byId(p.id))
            assertEquals(p.curve, EqCurve.decode(p.curve.encode()))
            assertEquals(0f, p.extraDb, eps)
        }
    }

    @Test
    fun reggaetonPunchesTheSubBassAndKeepsTheVoiceClear() {
        val r = preset(EqPresets.REGGAETON)
        // El golpe en ~60 Hz es lo más alto de toda la curva.
        assertEquals(EQ_STANDARD_HZ.maxOf { db(r, it) }, db(r, 62f), eps)
        assertTrue(db(r, 62f) >= 5f)
        // Medios un poco hundidos (sin exagerar)…
        assertTrue(db(r, 500f) < 0f && db(r, 500f) >= -3f)
        assertTrue(db(r, 1000f) < 0f)
        // …y la voz clara en 2–4 kHz, por encima de los medios.
        assertTrue(db(r, 2000f) > 1f && db(r, 4000f) > 2f)
        assertTrue(db(r, 3000f) > db(r, 1000f) + 3f)
    }

    @Test
    fun urbanTechnoHasATightKickAndCrispHats() {
        val t = preset(EqPresets.URBAN_TECHNO)
        // Bombo: 60–100 Hz arriba; el subgrave casi no se toca (seco, sin retumbe).
        assertTrue(db(t, 62f) >= 4f && db(t, 100f) >= 3.5f)
        assertTrue(db(t, 31f) < db(t, 62f) - 3f)
        // Medios-graves bajo control.
        assertTrue(db(t, 250f) < 0f && db(t, 400f) < 0f)
        // Hi-hats brillantes en 8–12 kHz.
        assertTrue(db(t, 8000f) >= 4f && db(t, 12000f) >= 3f)
        // Es distinto del Techno de siempre.
        assertFalse(t.curve == preset(EqPresets.TECHNO).curve)
    }

    @Test
    fun electronicaIsAWideSmileWithAiryHighs() {
        val e = preset(EqPresets.ELECTRONICA)
        val mids = db(e, 1000f)
        // Sonrisa: graves y agudos por encima de los medios, que son lo más bajo.
        assertTrue(mids < 0f)
        assertEquals(EQ_STANDARD_HZ.minOf { db(e, it) }, mids, eps)
        assertTrue(db(e, 62f) > mids + 5f && db(e, 16000f) > mids + 5f)
        // Aire: lo más alto de arriba está en 16 kHz.
        assertEquals(listOf(4000f, 8000f, 16000f).maxOf { db(e, it) }, db(e, 16000f), eps)
    }

    @Test
    fun urbanPresetsRespectProtectedBass() {
        for (bands in listOf(fiveBands, tenBands)) for (p in EqPresets.urban) {
            val s = EqSettings().withPreset(p)
            val out = EqMath.compute(s, bands, all)
            // Con Bajo protegido ninguna banda pasa su tope ni el pico pasa el margen.
            out.heard.forEachIndexed { i, v -> assertTrue("${p.id} ${bands.hz[i]} Hz: $v", v <= EqMath.capAt(bands.hz[i]) + eps) }
            assertTrue("${p.id}: ${out.levels.max()}", out.levels.max() <= EqMath.HEADROOM_DB + eps)
            // Y aun así el golpe se oye: los bajos quedan arriba de los medios.
            val low = out.heard[bands.hz.indexOfFirst { it >= 55f }]
            val mid = out.heard[bands.hz.indexOfFirst { it >= 900f }]
            assertTrue("${p.id}: bajo $low, medio $mid", low > mid)
        }
        // En la pantalla de 5 bandas, el 60 Hz del Reggaetón queda justo en el tope de los bajos.
        val reg = EqMath.compute(EqSettings().withPreset(preset(EqPresets.REGGAETON)), fiveBands, none)
        assertEquals(EqMath.LOW_CAP_DB, reg.heard[0], eps)
        // Sin protección el mismo estilo pide más de lo que el tope deja.
        val raw = EqMath.compute(EqSettings(protect = false).withPreset(preset(EqPresets.REGGAETON)), fiveBands, none)
        assertTrue(raw.heard[0] > EqMath.LOW_CAP_DB)
    }
}
