package cl.machukeitor.gestornfc.ui.write

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReviewUrlTest {

    @Test
    fun `place id arma el formulario de resena`() {
        assertEquals(
            "https://search.google.com/local/writereview?placeid=ChIJN1t_tDeuEmsRUsoyG83frY4",
            reviewUrl(" ChIJN1t_tDeuEmsRUsoyG83frY4 "),
        )
    }

    @Test
    fun `enlace del perfil de empresa se usa tal cual`() {
        assertEquals("https://g.page/r/CabcDEF123/review", reviewUrl("https://g.page/r/CabcDEF123/review"))
        assertEquals("https://g.page/r/CabcDEF123/review", reviewUrl("g.page/r/CabcDEF123/review"))
    }

    @Test
    fun `rechaza enlaces que no son de google`() {
        assertNull(reviewUrl("https://example.com/review"))
        assertNull(reviewUrl("hola"))
        assertNull(reviewUrl(""))
    }

    @Test
    fun `enlace corto de maps se acepta`() {
        assertEquals("https://maps.app.goo.gl/abc123", reviewUrl("maps.app.goo.gl/abc123"))
    }

    @Test
    fun `dominios que imitan a google se rechazan`() {
        assertNull(reviewUrl("https://g.page.evil.com/r/x/review"))
        assertNull(reviewUrl("https://evilg.page/r/x/review"))
        assertNull(reviewUrl("https://g.page@evil.com/review"))
        assertNull("Place ID demasiado corto", reviewUrl("ChIJabc"))
    }
}
