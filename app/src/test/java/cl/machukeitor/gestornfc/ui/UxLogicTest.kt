package cl.machukeitor.gestornfc.ui

import cl.machukeitor.gestornfc.ui.write.RecentWrites
import cl.machukeitor.gestornfc.ui.write.WriteType
import cl.machukeitor.gestornfc.ui.write.chipUsage
import cl.machukeitor.gestornfc.ui.write.pushRecent
import cl.machukeitor.gestornfc.ui.write.smallestFittingChip
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Locale

class UxLogicTest {

    private lateinit var savedLocale: Locale

    @Before fun setLocale() { savedLocale = Locale.getDefault(); Locale.setDefault(Locale("es")) }
    @After fun restoreLocale() { Locale.setDefault(savedLocale) }

    // --- Antena NFC ---

    @Test
    fun `zona de la antena por tercios del alto`() {
        assertEquals(AntennaZone.TOP, antennaZone(20, 150))
        assertEquals(AntennaZone.TOP, antennaZone(49, 150))
        assertEquals(AntennaZone.MIDDLE, antennaZone(50, 150))
        assertEquals(AntennaZone.MIDDLE, antennaZone(99, 150))
        assertEquals(AntennaZone.BOTTOM, antennaZone(100, 150))
        assertEquals(AntennaZone.BOTTOM, antennaZone(150, 150))
    }

    @Test
    fun `datos de antena invalidos no dan zona`() {
        assertNull(antennaZone(10, 0))
        assertNull(antennaZone(-1, 150))
        assertNull(antennaZone(200, 150))
    }

    @Test
    fun `indicacion segun la zona`() {
        assertTrue(placementHint(AntennaZone.TOP).endsWith("arriba."))
        assertTrue(placementHint(AntennaZone.MIDDLE).endsWith("al centro."))
        assertTrue(placementHint(AntennaZone.BOTTOM).endsWith("abajo."))
        assertTrue("Sin datos: indicación general", placementHint(null).contains("cerca de la cámara"))
        assertNull("Sin adaptador no hay zona", nfcAntennaZone(null))
    }

    // --- Capacidad ---

    @Test
    fun `porcentaje de uso por chip redondeado hacia arriba`() {
        val u = chipUsage(137)
        assertEquals(listOf("NTAG213", "NTAG215", "NTAG216"), u.map { it.chip })
        assertEquals(100, u[0].percent)
        assertTrue(u[0].fits)
        assertEquals(28, u[1].percent) // 137/496 = 27,6 %
        assertFalse(chipUsage(138)[0].fits)
    }

    @Test
    fun `chip mas chico en que cabe`() {
        assertEquals("NTAG213", smallestFittingChip(20))
        assertEquals("NTAG215", smallestFittingChip(138))
        assertEquals("NTAG216", smallestFittingChip(868))
        assertNull(smallestFittingChip(869))
    }

    // --- Recientes ---

    @Test
    fun `reciente va primero sin repetir y con maximo`() {
        assertEquals(listOf("wifi"), pushRecent(emptyList(), "wifi"))
        assertEquals(listOf("url", "wifi", "text"), pushRecent(listOf("wifi", "url", "text"), "url"))
        assertEquals(listOf("a", "b", "c", "d"), pushRecent(listOf("b", "c", "d", "e"), "a"))
    }

    @Test
    fun `recientes guardados ignoran rutas desconocidas`() {
        assertEquals(listOf(WriteType.WIFI, WriteType.URL), RecentWrites.parse("wifi,ruta-vieja,url,wifi"))
        assertEquals(emptyList<WriteType>(), RecentWrites.parse(null))
        assertEquals(emptyList<WriteType>(), RecentWrites.parse(""))
    }
}
