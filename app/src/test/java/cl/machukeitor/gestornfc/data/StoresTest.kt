package cl.machukeitor.gestornfc.data

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class StoresTest {

    @get:Rule val tmp = TemporaryFolder()

    private fun historyFile() = File(tmp.root, "history.json")

    @Test
    fun `historial se guarda y se vuelve a leer`() {
        val store = HistoryStore(historyFile())
        store.add(HistoryType.WRITE, "04:A2", "NTAG213", "URL: https://x.cl", byteArrayOf(1, 2, 3))
        store.add(HistoryType.READ, "04:B3", "NTAG215", "Vacío", null)

        val reopened = HistoryStore(historyFile()).items.value
        assertEquals(2, reopened.size)
        assertEquals("Lo más nuevo primero", "04:B3", reopened[0].uid)
        assertEquals(HistoryType.WRITE, reopened[1].type)
        assertArrayEquals(byteArrayOf(1, 2, 3), reopened[1].ndef)
    }

    @Test
    fun `nombre del tag se hereda y se puede editar`() {
        val store = HistoryStore(historyFile())
        store.add(HistoryType.READ, "04:A2", "", "a", null)
        store.edit(store.items.value[0].id, "  Llavero casa ", " nota ")
        store.add(HistoryType.READ, "04:A2", "", "b", null)
        assertEquals("Llavero casa", store.items.value[0].name)
        assertEquals("Llavero casa", store.items.value[0].title)
        assertEquals("nota", store.items.value[1].note)
    }

    @Test
    fun `historial guarda como maximo 500 entradas`() {
        val store = HistoryStore(historyFile())
        repeat(505) { store.add(HistoryType.READ, "u$it", "", "s", null) }
        assertEquals(500, HistoryStore(historyFile()).items.value.size)
    }

    @Test
    fun `archivo danado se aparta y no se pierde`() {
        val file = historyFile()
        file.writeText("[{\"id\":1,\"time\":2,\"type\":\"READ\"") // JSON cortado a la mitad
        val store = HistoryStore(file)
        assertTrue(store.items.value.isEmpty())
        val corrupt = File(file.path + ".corrupt")
        assertTrue("El contenido dañado se conserva aparte", corrupt.exists())
        assertTrue(corrupt.readText().startsWith("[{\"id\":1"))

        store.add(HistoryType.READ, "u", "", "s", null)
        assertEquals(1, HistoryStore(file).items.value.size)
        assertTrue("La copia dañada no se sobrescribe", corrupt.readText().startsWith("[{\"id\":1"))
    }

    @Test
    fun `temporal de un corte anterior se ignora`() {
        val file = historyFile()
        HistoryStore(file).add(HistoryType.READ, "u", "", "s", null)
        File(file.path + ".tmp").writeText("[basura")
        assertEquals(1, HistoryStore(file).items.value.size)
        assertFalse(File(file.path + ".tmp").exists())
    }

    @Test
    fun `guardar no deja archivos temporales`() {
        HistoryStore(historyFile()).add(HistoryType.READ, "u", "", "s", null)
        assertEquals(listOf("history.json"), tmp.root.list()!!.sorted())
    }

    @Test
    fun `deshacer borrado del historial vuelve a su lugar`() {
        val store = HistoryStore(historyFile())
        listOf("a", "b", "c").forEach { store.add(HistoryType.READ, it, "", it, null); Thread.sleep(2) }
        val middle = store.items.value[1]
        val removed = store.delete(middle.id)
        assertEquals(listOf(middle), removed)
        assertEquals(listOf("c", "a"), store.items.value.map { it.uid })

        store.restore(removed)
        assertEquals(listOf("c", "b", "a"), HistoryStore(historyFile()).items.value.map { it.uid })
    }

    @Test
    fun `deshacer no pisa lo agregado mientras tanto ni duplica`() {
        val store = HistoryStore(historyFile())
        store.add(HistoryType.READ, "viejo", "", "s", null)
        val removed = store.clear()
        Thread.sleep(2)
        store.add(HistoryType.READ, "nuevo", "", "s", null)
        store.restore(removed)
        store.restore(removed) // tocar "Deshacer" dos veces no duplica
        assertEquals(listOf("nuevo", "viejo"), store.items.value.map { it.uid })
    }

    @Test
    fun `borrar id inexistente no devuelve nada`() {
        val store = HistoryStore(historyFile())
        store.add(HistoryType.READ, "u", "", "s", null)
        assertTrue(store.delete(-1).isEmpty())
        assertEquals(1, store.items.value.size)
    }

    @Test
    fun `deshacer borrado de plantilla mantiene el orden de creacion`() {
        val store = TemplateStore(File(tmp.root, "templates.json"))
        listOf("uno", "dos", "tres").forEach { store.add(it, "", byteArrayOf(1)); Thread.sleep(2) }
        val removed = store.delete(store.items.value[1].id)
        assertEquals("dos", removed.single().name)
        store.restore(removed)
        assertEquals(listOf("uno", "dos", "tres"), store.items.value.map { it.name })
    }

    @Test
    fun `plantillas agregar, renombrar y borrar`() {
        val file = File(tmp.root, "templates.json")
        val store = TemplateStore(file)
        store.add(" Casa ", "URL: x", byteArrayOf(9))
        store.add("Oficina", "Wi‑Fi: y", byteArrayOf(8))
        val id = store.items.value[0].id
        store.rename(id, " Hogar ")
        store.delete(store.items.value[1].id)

        val reopened = TemplateStore(file).items.value
        assertEquals(1, reopened.size)
        assertEquals("Hogar", reopened[0].name)
        assertArrayEquals(byteArrayOf(9), reopened[0].ndef)
    }
}
