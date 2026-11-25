package cl.machukeitor.gestornfc.nfc

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Locale

/** Contenido a escribir, interpretación de registros NDEF y redes sociales. */
class ContentTest {

    private lateinit var savedLocale: Locale

    @Before fun setLocale() { savedLocale = Locale.getDefault(); Locale.setDefault(Locale("es", "CL")) }
    @After fun restoreLocale() { Locale.setDefault(savedLocale) }

    // --- vCard ---

    private fun contact(
        first: String = "Cristian", last: String = "Machuca", phone: String = "", email: String = "",
        company: String = "", title: String = "", website: String = "", address: String = "",
    ) = WritePayload.Contact(first, last, phone, email, company, title, website, address)

    @Test
    fun `vcard completa en orden`() {
        val v = contact(phone = " +56912345678 ", email = "c@ejemplo.cl", title = "Dev", website = "https://x.cl", address = "Calle 1; Chillán").toVcard()
        assertEquals(
            "BEGIN:VCARD\nVERSION:3.0\nN:Machuca;Cristian;;;\nFN:Cristian Machuca\nTITLE:Dev\n" +
                "TEL;TYPE=CELL:+56912345678\nEMAIL:c@ejemplo.cl\nURL:https://x.cl\nADR:;;Calle 1\\; Chillán;;;;\nEND:VCARD",
            v,
        )
    }

    @Test
    fun `vcard solo con apellido`() {
        val c = contact(first = "  ", last = "Machuca")
        assertEquals("Machuca", c.fullName)
        assertTrue(c.toVcard().contains("FN:Machuca\n"))
    }

    @Test
    fun `vcard escapa barra invertida`() {
        assertTrue(contact(company = "A\\B").toVcard().contains("ORG:A\\\\B\n"))
    }

    @Test
    fun `vcard con salto de linea no rompe la estructura`() {
        // Un salto de línea sin escapar crea una línea nueva que no es un campo vCard.
        val v = contact(address = "Calle 1\nChillán").toVcard()
        assertTrue(v.contains("ADR:;;Calle 1\\nChillán;;;;\n"))
        assertEquals("Cada línea debe tener la forma CLAVE:valor", 0, v.lines().count { !it.contains(':') })
    }

    @Test
    fun `vcard se interpreta al leer`() {
        val v = contact(company = "Empresa, Ltda", phone = "+569", address = "Calle 1\nChillán").toVcard()
        val info = NdefParser.describeVcard(v.toByteArray(), 100)
        assertEquals("Cristian Machuca", info.value)
        assertTrue(info.details.contains("Empresa" to "Empresa, Ltda"))
        assertTrue(info.details.contains("Teléfono" to "+569"))
        assertTrue(info.details.contains("Dirección" to "Calle 1 Chillán"))
    }

    // --- Ubicación ---

    @Test
    fun `ubicacion google maps con 6 decimales y punto decimal`() {
        Locale.setDefault(Locale.GERMANY) // usa coma decimal: no debe afectar
        assertEquals(
            "https://maps.google.com/?q=-36.606600,-72.103400",
            WritePayload.Location(-36.6066, -72.1034, "", googleMaps = true).uri,
        )
        assertEquals("geo:1.500000,2.000000?q=1.500000,2.000000", WritePayload.Location(1.5, 2.0, " ", googleMaps = false).uri)
    }

    @Test
    fun `resumenes`() {
        assertEquals("Ubicación: Casa", WritePayload.Location(1.0, 2.0, "Casa", true).summary)
        assertEquals("Ubicación: 1.000000,2.000000", WritePayload.Location(1.0, 2.0, "", true).summary)
        assertEquals("Abrir app: com.x", WritePayload.App("com.x", "").summary)
        assertEquals("Automatización: Linterna: alternar", WritePayload.Automation("", listOf(AutoAction.Torch(AutoAction.Toggle.TOGGLE))).summary)
        assertEquals("Wi‑Fi: Casa", WritePayload.Wifi(WifiConfig("Casa", "", WifiAuth.OPEN)).summary)
    }

    // --- Registro de texto NDEF ---

    private fun textPayload(lang: String, text: String, utf16: Boolean = false): ByteArray {
        val body = if (utf16) text.toByteArray(Charsets.UTF_16) else text.toByteArray(Charsets.UTF_8)
        val status = lang.length or if (utf16) 0x80 else 0
        return byteArrayOf(status.toByte()) + lang.toByteArray(Charsets.US_ASCII) + body
    }

    @Test
    fun `texto utf8 y utf16`() {
        val utf8 = NdefParser.describeText(textPayload("es", "Hola ñandú"), 20)
        assertEquals("Hola ñandú", utf8.value)
        assertEquals(listOf("Idioma" to "es"), utf8.details)
        assertEquals("Hola", NdefParser.describeText(textPayload("en", "Hola", utf16 = true), 20).value)
    }

    @Test
    fun `texto vacio`() {
        assertEquals("", NdefParser.describeText(ByteArray(0), 3).value)
    }

    @Test
    fun `texto con largo de idioma corrupto no revienta`() {
        // Byte de estado dice 63 bytes de idioma, pero el payload solo trae 3.
        val info = NdefParser.describeText(byteArrayOf(0x3F, 'e'.code.toByte(), 's'.code.toByte()), 5)
        assertEquals("Texto", info.kind)
    }

    // --- URIs ---

    @Test
    fun `tipo de uri`() {
        assertEquals("Teléfono", NdefParser.describeUri("tel:+56912345678", 10).kind)
        assertEquals("Email", NdefParser.describeUri("MAILTO:a@b.cl", 10).kind)
        assertEquals("SMS", NdefParser.describeUri("smsto:+569", 10).kind)
        assertEquals("Ubicación", NdefParser.describeUri("geo:1,2", 10).kind)
        assertEquals("Red social · Instagram", NdefParser.describeUri("https://instagram.com/x", 10).kind)
        assertEquals("URL", NdefParser.describeUri("https://example.com", 10).kind)
        assertEquals("URI", NdefParser.describeUri("ftp://x", 10).kind)
        assertEquals("tel:1", NdefParser.describeUri("tel:1", 10).openUri)
    }

    // --- Redes sociales ---

    @Test
    fun `todas las redes arman su url`() {
        val expected = mapOf(
            SocialNetwork.INSTAGRAM to "https://instagram.com/u",
            SocialNetwork.WHATSAPP to "https://wa.me/u",
            SocialNetwork.FACEBOOK to "https://facebook.com/u",
            SocialNetwork.TIKTOK to "https://tiktok.com/@u",
            SocialNetwork.X to "https://x.com/u",
            SocialNetwork.YOUTUBE to "https://youtube.com/@u",
            SocialNetwork.LINKEDIN to "https://linkedin.com/in/u",
            SocialNetwork.LINKEDIN_COMPANY to "https://linkedin.com/company/u",
            SocialNetwork.TELEGRAM to "https://t.me/u",
            SocialNetwork.THREADS to "https://threads.net/@u",
            SocialNetwork.SPOTIFY to "u",
            SocialNetwork.GITHUB to "https://github.com/u",
            SocialNetwork.TWITCH to "https://twitch.tv/u",
            SocialNetwork.PINTEREST to "https://pinterest.com/u",
            SocialNetwork.DISCORD to "https://discord.gg/u",
        )
        assertEquals(SocialNetwork.entries.toSet(), expected.keys)
        expected.forEach { (net, url) -> assertEquals(net.name, url, net.buildUrl("u")) }
    }

    @Test
    fun `whatsapp sin mensaje no agrega text`() {
        assertEquals("https://wa.me/56912345678", SocialNetwork.WHATSAPP.buildUrl("56912345678", "   "))
        assertEquals("El mensaje solo aplica a WhatsApp", "https://t.me/u", SocialNetwork.TELEGRAM.buildUrl("u", "hola"))
    }

    @Test
    fun `usuario desde url con mas segmentos`() {
        assertEquals("usuario", SocialNetwork.TIKTOK.normalize("https://www.tiktok.com/@usuario/video/7300000000000"))
        assertEquals("canal", SocialNetwork.YOUTUBE.normalize("https://www.youtube.com/@canal/videos"))
        assertEquals("usuario", SocialNetwork.X.normalize("https://x.com/usuario/status/123"))
        assertEquals("empresa", SocialNetwork.LINKEDIN_COMPANY.normalize("https://www.linkedin.com/company/empresa/about/"))
        assertEquals("cristian", SocialNetwork.LINKEDIN.normalize("https://linkedin.com/in/cristian/details/skills"))
    }

    @Test
    fun `usuario con url de fragmento o arroba con espacios`() {
        assertEquals("machukeitor", SocialNetwork.GITHUB.normalize("github.com/machukeitor#readme"))
        assertEquals("abc", SocialNetwork.THREADS.normalize("  @abc  "))
        assertNull(SocialNetwork.INSTAGRAM.normalize("https://instagram.com/"))
    }

    @Test
    fun `spotify link corto`() {
        assertEquals("https://spotify.link/abc", SocialNetwork.SPOTIFY.normalize(" https://spotify.link/abc "))
    }

    @Test
    fun `detecta subdominios y puertos pero no dominios parecidos`() {
        assertEquals(SocialNetwork.FACEBOOK, SocialNetwork.detect("https://m.facebook.com/x"))
        assertEquals(SocialNetwork.YOUTUBE, SocialNetwork.detect("https://youtu.be/abc"))
        assertEquals(SocialNetwork.GITHUB, SocialNetwork.detect("https://GITHUB.com:443/x"))
        assertNull(SocialNetwork.detect("https://instagram.com.evil.com/x"))
        assertNull(SocialNetwork.detect("https://notinstagram.com/x"))
    }

    @Test
    fun `codificacion para url`() {
        assertEquals("a%20b%26c%3Dd", SocialNetwork.encode("a b&c=d"))
    }

    @Test
    fun `nombre visible de linkedin empresa`() {
        assertEquals("LinkedIn Empresa", SocialNetwork.LINKEDIN_COMPANY.displayName)
        assertEquals("L", SocialNetwork.LINKEDIN_COMPANY.initial)
    }

    @Test
    fun `resumen de mensaje NDEF vacio y con varios registros`() {
        fun rec(kind: String, empty: Boolean = false) = NdefRecordInfo(kind, "v", sizeBytes = 1, empty = empty)
        fun info(vararg r: NdefRecordInfo) = NdefInfo("T2", 137, 0, true, true, r.toList(), null)
        assertEquals("Vacío", info().summary)
        assertEquals("Vacío", info(rec("x", empty = true)).summary)
        assertFalse(info(rec("x", empty = true)).hasContent)
        assertEquals("URL: v (+1)", info(rec("URL"), rec("App")).summary)
    }
}
