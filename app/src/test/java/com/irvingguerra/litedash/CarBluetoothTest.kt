package com.irvingguerra.litedash

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CarBluetoothTest {
    @Test
    fun scoresCarBluetoothApps() {
        assertEquals(2, carBluetoothScore("com.jyt.btmusic", "Musica BT"))
        assertEquals(3, carBluetoothScore("com.car.settings", "Bluetooth"))
        assertEquals(1, carBluetoothScore("com.jyt.phone", "Teléfono"))
        assertEquals(0, carBluetoothScore("com.jyt.radio", "Radio"))
        assertTrue(carBluetoothScore("com.x.bt", "BT") > carBluetoothScore("com.x.tel", "Teléfono"))
    }
}
