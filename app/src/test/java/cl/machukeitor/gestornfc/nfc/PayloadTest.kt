package cl.machukeitor.gestornfc.nfc

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PayloadTest {

    @Test
    fun `wifi ida y vuelta`() {
        val original = WifiConfig("Casa Chillán", "clave-segura-123", WifiAuth.WPA2)
        assertEquals(original, WifiConfig.parse(original.toPayload()))
    }

    @Test
    fun `wifi abierta no lleva clave`() {
        val parsed = WifiConfig.parse(WifiConfig("Libre", "ignorada", WifiAuth.OPEN).toPayload())
        assertEquals(WifiAuth.OPEN, parsed?.auth)
        assertEquals("", parsed?.password)
    }

    @Test
    fun `wifi con datos corruptos no revienta`() {
        assertNull(WifiConfig.parse(byteArrayOf(0x10, 0x45, 0x00, 0x50)))
    }

    @Test
    fun `vcard escapa comas y punto y coma`() {
        val vcard = WritePayload.Contact(
            firstName = "Cristian", lastName = "Machuca", phone = "+56912345678", email = "c@ejemplo.cl",
            company = "Empresa, Ltda", title = "", website = "", address = "",
        ).toVcard()
        assertTrue(vcard.startsWith("BEGIN:VCARD\nVERSION:3.0\n"))
        assertTrue(vcard.contains("N:Machuca;Cristian;;;"))
        assertTrue(vcard.contains("FN:Cristian Machuca"))
        assertTrue(vcard.contains("ORG:Empresa\\, Ltda"))
        assertFalse("Campos vacíos no se incluyen", vcard.contains("TITLE:"))
        assertTrue(vcard.endsWith("END:VCARD"))
    }

    @Test
    fun `hex ida y vuelta`() {
        val bytes = byteArrayOf(0x04, 0xA2.toByte(), 0x00, 0xFF.toByte())
        assertEquals("04:A2:00:FF", bytes.toHex())
        assertArrayEquals(bytes, "04:a2:00:ff".hexToBytes())
    }

    @Test
    fun `coordenadas desde distintos formatos`() {
        assertEquals(-36.6066 to -72.1034, Coordinates.parse("-36.6066, -72.1034"))
        assertEquals(
            -36.6066 to -72.1034,
            Coordinates.parse("https://www.google.com/maps/place/Chill%C3%A1n/@-36.6066,-72.1034,14z"),
        )
        assertEquals(-36.6 to -72.1, Coordinates.parse("https://maps.google.com/?q=-36.6,-72.1"))
        assertNull(Coordinates.parse("https://maps.app.goo.gl/abc"))
        assertNull("Fuera de rango", Coordinates.parse("95.0, 10.0"))
    }

    @Test
    fun `contrasena de texto da siempre los mismos 4 bytes`() {
        val a = PasswordMode.TEXT.toBytes("miclave123")
        assertNotNull(a)
        assertEquals(4, a!!.size)
        assertArrayEquals(a, PasswordMode.TEXT.toBytes("miclave123"))
        assertNull("Mínimo 4 caracteres", PasswordMode.TEXT.toBytes("abc"))
    }

    @Test
    fun `contrasena hex exige 8 caracteres validos`() {
        assertArrayEquals(byteArrayOf(0x1A, 0x2B, 0x3C, 0x4D), PasswordMode.HEX.toBytes("1A2B3C4D"))
        assertNull(PasswordMode.HEX.toBytes("1A2B3C"))
        assertNull(PasswordMode.HEX.toBytes("1A2B3C4G"))
    }
}
