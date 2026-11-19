package cl.machukeitor.gestornfc.nfc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SocialNetworkTest {

    @Test
    fun `usuario con arroba se limpia`() {
        assertEquals("machukeitor", SocialNetwork.INSTAGRAM.normalize("@machukeitor"))
    }

    @Test
    fun `url completa se reduce al usuario`() {
        assertEquals("machukeitor", SocialNetwork.INSTAGRAM.normalize("https://www.instagram.com/machukeitor/?hl=es"))
        assertEquals("machukeitor", SocialNetwork.INSTAGRAM.normalize("instagram.com/machukeitor"))
        assertEquals("cristian", SocialNetwork.LINKEDIN.normalize("https://linkedin.com/in/cristian/"))
    }

    @Test
    fun `usuario vacio o con espacios no es valido`() {
        assertNull(SocialNetwork.TIKTOK.normalize("   "))
        assertNull(SocialNetwork.TIKTOK.normalize("dos palabras"))
    }

    @Test
    fun `whatsapp deja solo digitos y exige largo minimo`() {
        assertEquals("56912345678", SocialNetwork.WHATSAPP.normalize("+56 9 1234-5678"))
        assertNull(SocialNetwork.WHATSAPP.normalize("1234"))
    }

    @Test
    fun `whatsapp codifica el mensaje con espacios como %20`() {
        assertEquals(
            "https://wa.me/56912345678?text=Hola%20%C2%BFc%C3%B3mo%20est%C3%A1s%3F",
            SocialNetwork.WHATSAPP.buildUrl("56912345678", "Hola ¿cómo estás?"),
        )
    }

    @Test
    fun `urls por red`() {
        assertEquals("https://tiktok.com/@abc", SocialNetwork.TIKTOK.buildUrl("abc"))
        assertEquals("https://t.me/abc", SocialNetwork.TELEGRAM.buildUrl("abc"))
        assertEquals("https://discord.gg/xyz", SocialNetwork.DISCORD.buildUrl("xyz"))
    }

    @Test
    fun `spotify exige enlace de spotify`() {
        assertNull(SocialNetwork.SPOTIFY.normalize("https://example.com/x"))
        assertEquals(
            "https://open.spotify.com/playlist/123",
            SocialNetwork.SPOTIFY.normalize("https://open.spotify.com/playlist/123"),
        )
    }

    @Test
    fun `detecta la red por dominio`() {
        assertEquals(SocialNetwork.INSTAGRAM, SocialNetwork.detect("https://www.instagram.com/x"))
        assertEquals(SocialNetwork.X, SocialNetwork.detect("https://twitter.com/x"))
        assertEquals(SocialNetwork.WHATSAPP, SocialNetwork.detect("https://wa.me/569"))
        assertNull(SocialNetwork.detect("https://example.com"))
        assertNull(SocialNetwork.detect("no es url"))
    }
}
