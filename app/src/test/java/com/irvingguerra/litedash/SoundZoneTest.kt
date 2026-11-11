package com.irvingguerra.litedash

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SoundZoneTest {
    @Test fun centerPlaysEverywhere() = assertArrayEquals(floatArrayOf(1f, 1f, 1f, 1f), SoundZone().levels(), 0.001f)

    @Test fun driverOnlyFrontLeft() = assertArrayEquals(floatArrayOf(1f, 0f, 0f, 0f), ZonePreset.DRIVER.zone.levels(), 0.001f)

    @Test fun passengerOnlyFrontRight() = assertArrayEquals(floatArrayOf(0f, 1f, 0f, 0f), ZonePreset.PASSENGER.zone.levels(), 0.001f)

    @Test fun rearMutesFront() = assertArrayEquals(floatArrayOf(0f, 0f, 1f, 1f), ZonePreset.REAR.zone.levels(), 0.001f)

    @Test fun halfwayLowersTheOtherSide() {
        val l = SoundZone(balance = -7, fader = 0).levels()
        assertArrayEquals(floatArrayOf(1f, 0f, 1f, 0f), l, 0.001f)
        assertEquals(0.5f, SoundZone(balance = 0, fader = -4).levels()[2], 0.08f)
    }

    @Test fun clampsOutOfRange() = assertEquals(SoundZone(7, -7), SoundZone(20, -30).clamped())

    @Test fun presetsAreRecognized() {
        assertEquals(ZonePreset.FRONT, SoundZone(0, -7).preset())
        assertNull(SoundZone(-4, -3).preset())
    }

    @Test fun stageRoundTrip() {
        for (b in -7..7) for (f in -7..7) {
            val z = SoundZone(b, f)
            val o = zoneToStage(z)
            assertEquals(z, stageToZone(o.x, o.y))
        }
    }

    @Test fun draggingOutsideTheCarStaysAtTheEdge() = assertEquals(SoundZone(-7, 7), stageToZone(-500f, 2000f))
}
