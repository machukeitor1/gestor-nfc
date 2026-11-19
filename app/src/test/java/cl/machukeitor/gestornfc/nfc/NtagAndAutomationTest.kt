package cl.machukeitor.gestornfc.nfc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NtagAndAutomationTest {

    private fun version(type: Int, storage: Int) =
        byteArrayOf(0x00, 0x04, type.toByte(), 0x02, 0x01, 0x00, storage.toByte(), 0x03)

    @Test
    fun `identifica los NTAG por GET_VERSION`() {
        val ntag213 = Ntag.identify(version(0x04, 0x0F))!!
        assertEquals("NTAG213", ntag213.name)
        assertEquals(45, ntag213.pages)
        assertEquals(0x29, ntag213.cfg0)
        assertEquals(144, ntag213.userBytes)

        val ntag215 = Ntag.identify(version(0x04, 0x11))!!
        assertEquals(0x83, ntag215.cfg0)
        assertEquals(504, ntag215.userBytes)

        val ntag216 = Ntag.identify(version(0x04, 0x13))!!
        assertEquals(0xE3, ntag216.cfg0)
        assertEquals(888, ntag216.userBytes)
    }

    @Test
    fun `chip desconocido o respuesta corta`() {
        assertNull(Ntag.identify(version(0x07, 0x0F)))
        assertNull(Ntag.identify(byteArrayOf(0x00, 0x04)))
    }

    @Test
    fun `proteccion segun AUTH0`() {
        val model = Ntag.identify(version(0x04, 0x0F))!!
        assertTrue(Ntag.Protection(model, 0x04).writeProtected)
        assertFalse(Ntag.Protection(model, 0xFF).writeProtected)
        assertTrue("Config ilegible = protegida", Ntag.Protection(model, null).writeProtected)
    }

    @Test
    fun `firma NXP rechaza datos invalidos`() {
        val uid = byteArrayOf(0x04, 0x11, 0x22, 0x33, 0x44, 0x55, 0x66)
        assertFalse(NxpSignature.verify(uid, ByteArray(32)))
        assertFalse(NxpSignature.verify(uid, ByteArray(16) { 1 }))
        assertFalse(NxpSignature.verify(uid, ByteArray(32) { 0x55 }))
    }

    @Test
    fun `automatizacion ida y vuelta`() {
        val actions = listOf(
            AutoAction.Torch(AutoAction.Toggle.TOGGLE),
            AutoAction.RingerMode(AutoAction.Ringer.SILENT),
            AutoAction.Volume(AutoAction.Stream.MEDIA, 30),
            AutoAction.Brightness(80),
            AutoAction.Timer(10, "Té"),
            AutoAction.OpenApp("com.spotify.music"),
            AutoAction.OpenUrl("https://github.com/machukeitor1"),
            AutoAction.WifiPanel,
            AutoAction.BluetoothSettings,
        )
        val (name, parsed) = AutoAction.fromJson(AutoAction.toJson("Modo cine", actions))
        assertEquals("Modo cine", name)
        assertEquals(actions, parsed)
    }

    @Test
    fun `automatizacion ignora acciones desconocidas y corrige rangos`() {
        val json = """{"n":"x","a":[{"t":"futuro"},{"t":"volume","s":"media","p":250},{"t":"timer","min":0}]}"""
        val (_, parsed) = AutoAction.fromJson(json)
        assertEquals(listOf(AutoAction.Volume(AutoAction.Stream.MEDIA, 100)), parsed)
    }

    @Test
    fun `automatizacion es compacta para un NTAG213`() {
        val json = AutoAction.toJson("Casa", listOf(AutoAction.Torch(AutoAction.Toggle.TOGGLE), AutoAction.WifiPanel))
        assertTrue("JSON de ${json.length} bytes", json.length < 80)
    }
}
