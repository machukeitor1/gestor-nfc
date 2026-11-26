package cl.machukeitor.gestornfc.data

import android.content.Context
import android.util.Log
import cl.machukeitor.gestornfc.i18n.tr
import cl.machukeitor.gestornfc.nfc.hexToBytes
import cl.machukeitor.gestornfc.nfc.toHex
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption

enum class HistoryType(private val es: String, private val en: String, private val pt: String) {
    READ("Lectura", "Read", "Leitura"),
    WRITE("Escritura", "Write", "Gravação"),
    PROTECT("Protección", "Protection", "Proteção");

    val label get() = tr(es, en, pt)
}

data class HistoryEntry(
    val id: Long,
    val time: Long,
    val type: HistoryType,
    val uid: String,
    val chip: String,
    val summary: String,
    /** Nombre que le pone el usuario ("Llavero casa"). */
    val name: String = "",
    val note: String = "",
    /** Mensaje NDEF leído o escrito; permite volver a escribirlo en otro tag. */
    val ndef: ByteArray? = null,
) {
    val title get() = name.ifBlank { summary }
}

data class Template(
    val id: Long,
    val name: String,
    val summary: String,
    val ndef: ByteArray,
    val created: Long,
)

/**
 * Lista persistida en un archivo JSON del almacenamiento privado de la app.
 * Pensada para pocos cientos de elementos: se reescribe completa en cada cambio.
 *
 * Escritura atómica: se escribe un archivo temporal y se renombra, así un corte a mitad de camino
 * (app cerrada, batería) nunca deja el archivo a medias. Si al abrir el archivo está dañado,
 * se aparta como "<nombre>.corrupt" en vez de sobrescribirlo, para no perder los datos.
 */
open class JsonListStore<T>(
    private val file: File,
    private val toJson: (T) -> JSONObject,
    private val fromJson: (JSONObject) -> T,
    private val maxItems: Int = Int.MAX_VALUE,
) {
    private val _items = MutableStateFlow(load())
    val items: StateFlow<List<T>> = _items.asStateFlow()

    private fun load(): List<T> {
        // Un temporal que quedó de un corte anterior no llegó a reemplazar al archivo: se descarta.
        tmpFile().delete()
        if (!file.exists()) return emptyList()
        return try {
            JSONArray(file.readText()).let { arr -> List(arr.length()) { fromJson(arr.getJSONObject(it)) } }
        } catch (e: Exception) {
            Log.e("GestorNFC", "No se pudo leer ${file.name}; se guarda aparte como .corrupt", e)
            runCatching {
                Files.move(file.toPath(), File(file.path + ".corrupt").toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
            emptyList()
        }
    }

    private fun tmpFile() = File(file.path + ".tmp")

    /** Aplica el cambio, lo guarda y devuelve la lista que había antes. */
    @Synchronized
    protected fun update(transform: (List<T>) -> List<T>): List<T> {
        val previous = _items.value
        val list = transform(previous).take(maxItems)
        _items.value = list
        try {
            val tmp = tmpFile()
            FileOutputStream(tmp).use { out ->
                out.write(JSONArray().apply { list.forEach { put(toJson(it)) } }.toString().toByteArray(Charsets.UTF_8))
                out.fd.sync() // que llegue al disco antes de reemplazar el archivo
            }
            Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } catch (e: Exception) {
            Log.e("GestorNFC", "No se pudo guardar ${file.name}", e)
        }
        return previous
    }

    /**
     * Quita los elementos que cumplen [predicate] y los devuelve, para poder deshacer con [restore].
     */
    protected fun removeWhere(predicate: (T) -> Boolean): List<T> =
        update { list -> list.filterNot(predicate) }.filter(predicate)

    /**
     * Deshace un borrado: vuelve a poner [removed] sin pisar lo que se agregó entretanto
     * (por ejemplo, un tag leído mientras se mostraba "Deshacer").
     */
    protected fun restore(removed: List<T>, id: (T) -> Long, order: Comparator<T>) {
        if (removed.isEmpty()) return
        update { list ->
            val present = list.map(id).toSet()
            (list + removed.filter { id(it) !in present }).sortedWith(order)
        }
    }
}

class HistoryStore(file: File) : JsonListStore<HistoryEntry>(
    file,
    toJson = { e ->
        JSONObject()
            .put("id", e.id).put("time", e.time).put("type", e.type.name)
            .put("uid", e.uid).put("chip", e.chip).put("summary", e.summary)
            .put("name", e.name).put("note", e.note)
            .apply { e.ndef?.let { put("ndef", it.toHex("")) } }
    },
    fromJson = { o ->
        HistoryEntry(
            id = o.getLong("id"),
            time = o.getLong("time"),
            type = runCatching { HistoryType.valueOf(o.getString("type")) }.getOrDefault(HistoryType.READ),
            uid = o.optString("uid"),
            chip = o.optString("chip"),
            summary = o.optString("summary"),
            name = o.optString("name"),
            note = o.optString("note"),
            ndef = o.optString("ndef").takeIf { it.isNotEmpty() }?.let { runCatching { it.hexToBytes() }.getOrNull() },
        )
    },
    maxItems = 500,
) {
    constructor(context: Context) : this(File(context.filesDir, "history.json"))

    /** Las entradas nuevas van primero. */
    fun add(type: HistoryType, uid: String, chip: String, summary: String, ndef: ByteArray?) {
        val now = System.currentTimeMillis()
        // Si el tag ya tiene nombre en el historial, la nueva entrada lo hereda.
        val knownName = items.value.firstOrNull { it.uid == uid && it.name.isNotBlank() }?.name.orEmpty()
        update { listOf(HistoryEntry(System.nanoTime(), now, type, uid, chip, summary, knownName, "", ndef)) + it }
    }

    fun edit(id: Long, name: String, note: String) { update { list ->
        list.map { if (it.id == id) it.copy(name = name.trim(), note = note.trim()) else it } }
    }

    /** Devuelve lo borrado, para "Deshacer". */
    fun delete(id: Long): List<HistoryEntry> = removeWhere { it.id == id }

    /** Devuelve lo borrado, para "Deshacer". */
    fun clear(): List<HistoryEntry> = removeWhere { true }

    /** Lo más nuevo primero. */
    fun restore(removed: List<HistoryEntry>) =
        restore(removed, { it.id }, compareByDescending<HistoryEntry> { it.time }.thenByDescending { it.id })
}

class TemplateStore(file: File) : JsonListStore<Template>(
    file,
    toJson = { t ->
        JSONObject()
            .put("id", t.id).put("name", t.name).put("summary", t.summary)
            .put("ndef", t.ndef.toHex("")).put("created", t.created)
    },
    fromJson = { o ->
        Template(o.getLong("id"), o.getString("name"), o.optString("summary"), o.getString("ndef").hexToBytes(), o.optLong("created"))
    },
) {
    constructor(context: Context) : this(File(context.filesDir, "templates.json"))

    fun add(name: String, summary: String, ndef: ByteArray) {
        val now = System.currentTimeMillis()
        update { it + Template(System.nanoTime(), name.trim(), summary, ndef, now) }
    }

    fun rename(id: Long, name: String) { update { list -> list.map { if (it.id == id) it.copy(name = name.trim()) else it } } }

    /** Devuelve lo borrado, para "Deshacer". */
    fun delete(id: Long): List<Template> = removeWhere { it.id == id }

    /** En orden de creación. */
    fun restore(removed: List<Template>) =
        restore(removed, { it.id }, compareBy<Template> { it.created }.thenBy { it.id })
}
