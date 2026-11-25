package cl.machukeitor.gestornfc.nfc

import cl.machukeitor.gestornfc.i18n.Lang
import cl.machukeitor.gestornfc.i18n.currentLang
import cl.machukeitor.gestornfc.i18n.tr
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.math.BigInteger
import java.util.Locale

/** Pruebas de la lógica pura: hex, coordenadas, Wi‑Fi, NTAG, contraseñas, automatizaciones e idiomas. */
class CoreLogicTest {

    private lateinit var savedLocale: Locale

    @Before fun saveLocale() { savedLocale = Locale.getDefault() }
    @After fun restoreLocale() { Locale.setDefault(savedLocale) }

    // --- Hex ---

    @Test
    fun `hex vacio, sin separador y con espacios`() {
        assertEquals("", ByteArray(0).toHex())
        assertEquals("04A2", byteArrayOf(0x04, 0xA2.toByte()).toHex(""))
        assertArrayEquals(byteArrayOf(0x0A, 0x0B), " 0a 0b ".hexToBytes())
        assertArrayEquals(ByteArray(0), "".hexToBytes())
    }

    @Test(expected = IllegalArgumentException::class)
    fun `hex de largo impar falla`() {
        "ABC".hexToBytes()
    }

    @Test(expected = NumberFormatException::class)
    fun `hex con caracteres invalidos falla`() {
        "ZZ".hexToBytes()
    }

    @Test
    fun `byte sin signo`() {
        assertEquals(255, 0xFF.toByte().u())
        assertEquals(128, 0x80.toByte().u())
        assertEquals(0, 0.toByte().u())
    }

    // --- Coordenadas ---

    @Test
    fun `coordenadas en otros parametros de google maps`() {
        assertEquals(-33.45 to -70.66, Coordinates.parse("https://www.google.com/maps/search/?api=1&query=-33.45,-70.66"))
        assertEquals(-33.45 to -70.66, Coordinates.parse("https://maps.google.com/?ll=-33.45%2C-70.66"))
        assertEquals(-33.45 to -70.66, Coordinates.parse("https://www.google.com/maps/dir/?api=1&destination=-33.45,-70.66"))
    }

    @Test
    fun `coordenadas enteras y con espacios`() {
        assertEquals(10.0 to 20.0, Coordinates.parse("  10 , 20  "))
        assertEquals(-90.0 to 180.0, Coordinates.parse("-90,180"))
    }

    @Test
    fun `coordenadas invalidas`() {
        assertNull("Longitud fuera de rango", Coordinates.parse("10.0, 181.0"))
        assertNull(Coordinates.parse(""))
        assertNull(Coordinates.parse("Chillán, Chile"))
        assertNull("Tres números no son coordenadas", Coordinates.parse("1, 2, 3"))
    }

    // --- Wi‑Fi ---

    @Test
    fun `wifi wpa-wpa2 y ssid con emoji ida y vuelta`() {
        val original = WifiConfig("Casa 🏠 Ñuñoa", "pässwörd", WifiAuth.WPA_WPA2)
        assertEquals(original, WifiConfig.parse(original.toPayload()))
    }

    @Test
    fun `wifi tipo de autenticacion`() {
        assertEquals(WifiAuth.OPEN, WifiAuth.fromAuthType(0x0001))
        assertEquals(WifiAuth.WPA2, WifiAuth.fromAuthType(0x0020))
        assertEquals(WifiAuth.WPA_WPA2, WifiAuth.fromAuthType(0x0022))
        assertEquals("WPA personal se trata como mixta", WifiAuth.WPA_WPA2, WifiAuth.fromAuthType(0x0002))
    }

    @Test
    fun `wifi vacio o truncado no revienta`() {
        assertNull(WifiConfig.parse(ByteArray(0)))
        val payload = WifiConfig("Red", "clave1234", WifiAuth.WPA2).toPayload()
        // Cortado a la mitad: el TLV de credencial queda incompleto y se ignora.
        assertNull(WifiConfig.parse(payload.copyOf(payload.size / 2)))
    }

    @Test
    fun `wifi payload empieza con version 1_0`() {
        val payload = WifiConfig("R", "k", WifiAuth.OPEN).toPayload()
        assertArrayEquals(byteArrayOf(0x10, 0x4A, 0x00, 0x01, 0x10), payload.copyOf(5))
    }

    // --- NTAG ---

    private fun version(type: Int, storage: Int) =
        byteArrayOf(0x00, 0x04, type.toByte(), 0x02, 0x01, 0x00, storage.toByte(), 0x03)

    @Test
    fun `identifica NTAG210, NTAG212 y Ultralight EV1`() {
        val ntag210 = Ntag.identify(version(0x04, 0x0B))!!
        assertEquals("NTAG210", ntag210.name)
        assertEquals(48, ntag210.userBytes)
        assertEquals(0x10, ntag210.cfg0)

        val ntag212 = Ntag.identify(version(0x04, 0x0E))!!
        assertEquals(128, ntag212.userBytes)
        assertEquals(0x25, ntag212.cfg0)

        val ul11 = Ntag.identify(version(0x03, 0x0B))!!
        assertTrue(ul11.name.contains("MF0UL11"))
        assertEquals(0x10, ul11.cfg0)
        assertEquals(0x11, ul11.cfg1)
        assertEquals(0x12, ul11.pwdPage)
        assertEquals(0x13, ul11.packPage)

        val ul21 = Ntag.identify(version(0x03, 0x0E))!!
        assertEquals(0x25, ul21.cfg0)
    }

    @Test
    fun `paginas de configuracion de NTAG216 segun datasheet`() {
        val m = Ntag.identify(version(0x04, 0x13))!!
        assertEquals(0xE3, m.cfg0)
        assertEquals(0xE4, m.cfg1)
        assertEquals(0xE5, m.pwdPage)
        assertEquals(0xE6, m.packPage)
    }

    @Test
    fun `tamano desconocido no ofrece contrasena`() {
        assertFalse(Ntag.identify(version(0x04, 0x99))!!.hasConfig)
        assertFalse(Ntag.identify(version(0x03, 0x99))!!.hasConfig)
    }

    @Test
    fun `proteccion en el limite de AUTH0`() {
        val m = Ntag.identify(version(0x04, 0x0F))!! // NTAG213, userEnd = 0x27
        assertTrue("AUTH0 en la última página de usuario protege", Ntag.Protection(m, 0x27).writeProtected)
        assertFalse("AUTH0 en la configuración no protege el contenido", Ntag.Protection(m, 0x28).writeProtected)
    }

    @Test
    fun `TLV NDEF usa largo corto hasta 254 bytes y largo de 3 bytes desde 255`() {
        val short = Ntag.ndefTlv(ByteArray(254))
        assertEquals(0x03, short[0].u())
        assertEquals(254, short[1].u())
        assertEquals(254 + 3, short.size)
        assertEquals(0xFE, short.last().u())

        val long = Ntag.ndefTlv(ByteArray(300))
        assertEquals(0xFF, long[1].u())
        assertEquals(300, (long[2].u() shl 8) or long[3].u())
        assertEquals(300 + 5, long.size)
        assertEquals(0xFE, long.last().u())
    }

    // --- Firma NXP: las constantes de la curva deben ser coherentes ---

    private fun field(name: String): Any = NxpSignature::class.java.getDeclaredField(name).apply { isAccessible = true }.get(NxpSignature)

    private fun onCurve(point: Any): Boolean {
        val p = field("P") as BigInteger
        val a = field("A") as BigInteger
        val b = BigInteger("E87579C11079F43DD824993C2CEE5ED3", 16) // secp128r1
        val x = point.javaClass.getDeclaredMethod("getX").invoke(point) as BigInteger
        val y = point.javaClass.getDeclaredMethod("getY").invoke(point) as BigInteger
        return y.pow(2).mod(p) == x.pow(3).add(a.multiply(x)).add(b).mod(p)
    }

    @Test
    fun `punto generador y clave publica de NXP estan en la curva`() {
        assertTrue("G", onCurve(field("G")))
        assertTrue("Clave pública NTAG", onCurve(field("NTAG_PUBLIC_KEY")))
    }

    @Test
    fun `firma con r o s fuera de rango se rechaza`() {
        val uid = byteArrayOf(0x04, 0x11, 0x22, 0x33, 0x44, 0x55, 0x66)
        val ff = ByteArray(32) { 0xFF.toByte() } // r = s = 2^128-1 > N
        assertFalse(NxpSignature.verify(uid, ff))
        assertFalse("s = 0", NxpSignature.verify(uid, ByteArray(16) { 1 } + ByteArray(16)))
    }

    // --- Contraseñas ---

    @Test
    fun `contrasena hex acepta minusculas y espacios alrededor`() {
        assertArrayEquals(byteArrayOf(0xAB.toByte(), 0xCD.toByte(), 0xEF.toByte(), 0x01), PasswordMode.HEX.toBytes(" abcdef01 "))
    }

    @Test
    fun `contrasenas distintas dan bytes distintos`() {
        assertFalse(PasswordMode.TEXT.toBytes("clave1")!!.contentEquals(PasswordMode.TEXT.toBytes("clave2")!!))
    }

    @Test
    fun `mensaje de error de contrasena`() {
        Locale.setDefault(Locale("es", "CL"))
        assertNull("Campo vacío no muestra error", PasswordMode.TEXT.error(""))
        assertNull(PasswordMode.TEXT.error("1234"))
        assertEquals("Mínimo 4 caracteres", PasswordMode.TEXT.error("12"))
        assertTrue(PasswordMode.HEX.error("XYZ")!!.contains("8 caracteres hex"))
    }

    // --- Automatizaciones ---

    @Test
    fun `lista de acciones ida y vuelta`() {
        val actions = listOf(AutoAction.Brightness(0), AutoAction.Timer(5, ""), AutoAction.RingerMode(AutoAction.Ringer.VIBRATE))
        assertEquals(actions, AutoAction.listFromJson(AutoAction.listToJson(actions)))
    }

    @Test
    fun `lista de acciones con json invalido queda vacia`() {
        assertEquals(emptyList<AutoAction>(), AutoAction.listFromJson("no es json"))
        assertEquals(emptyList<AutoAction>(), AutoAction.listFromJson("{}"))
    }

    @Test
    fun `temporizador sin mensaje no guarda la etiqueta`() {
        assertFalse(AutoAction.toJson("", listOf(AutoAction.Timer(3, " "))).contains("\"l\""))
    }

    @Test
    fun `automatizacion sin acciones o con valores invalidos`() {
        val (name, none) = AutoAction.fromJson("""{"n":"vacía"}""")
        assertEquals("vacía", name)
        assertTrue(none.isEmpty())

        val json = """{"a":[{"t":"bright","p":-20},{"t":"torch","m":"blink"},{"t":"app","p":""},{"t":"url","u":""},{"t":"timer","min":-1},{"t":"wifi"}]}"""
        val (noName, parsed) = AutoAction.fromJson(json)
        assertEquals("", noName)
        assertEquals(listOf(AutoAction.Brightness(0), AutoAction.WifiPanel), parsed)
    }

    @Test(expected = org.json.JSONException::class)
    fun `automatizacion con json roto lanza excepcion (los llamadores usan runCatching)`() {
        AutoAction.fromJson("{roto")
    }

    @Test
    fun `tipo NDEF de la automatizacion`() {
        assertEquals("machukeitor.cl:auto", AutoAction.NDEF_TYPE)
    }

    // --- Idiomas ---

    @Test
    fun `idioma segun el telefono, ingles por defecto`() {
        Locale.setDefault(Locale("es", "CL")); assertEquals(Lang.ES, currentLang)
        Locale.setDefault(Locale("pt", "BR")); assertEquals(Lang.PT, currentLang)
        Locale.setDefault(Locale.ENGLISH); assertEquals(Lang.EN, currentLang)
        Locale.setDefault(Locale.FRANCE); assertEquals("Otros idiomas → inglés", "en", tr("es", "en", "pt"))
    }

    @Test
    fun `etiquetas de acciones traducidas`() {
        Locale.setDefault(Locale("es"))
        assertEquals("Volumen multimedia: 30%", AutoAction.Volume(AutoAction.Stream.MEDIA, 30).label)
        assertEquals("Temporizador: 10 min (Té)", AutoAction.Timer(10, "Té").label)
        Locale.setDefault(Locale.ENGLISH)
        assertEquals("Flashlight: toggle", AutoAction.Torch(AutoAction.Toggle.TOGGLE).label)
        Locale.setDefault(Locale("pt"))
        assertEquals("Som: silencioso", AutoAction.RingerMode(AutoAction.Ringer.SILENT).label)
    }

    @Test
    fun `mensajes de escritura`() {
        Locale.setDefault(Locale("es"))
        assertEquals("Se grabaron 20 bytes de 137 disponibles.", Msg.written(20, 137))
        assertEquals("Se grabaron 20 bytes.", Msg.written(20))
        assertTrue(Msg.commError(null).endsWith(": sin detalle"))
        Locale.setDefault(Locale.ENGLISH)
        assertEquals("Doesn't fit: the content takes 600 bytes and the tag holds 137.", Msg.doesNotFit(600, 137))
    }
}
