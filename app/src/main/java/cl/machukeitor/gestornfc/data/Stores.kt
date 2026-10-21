package cl.machukeitor.gestornfc.data

import android.content.Context
import android.util.Log
import cl.machukeitor.gestornfc.nfc.hexToBytes
import cl.machukeitor.gestornfc.nfc.toHex
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

enum class HistoryType(val label: String) { READ("Lectura"), WRITE("Escritura"), PROTECT("Protección") }

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
 */
open class JsonListStore<T>(
    context: Context,
    fileName: String,
    private val toJson: (T) -> JSONObject,
    private val fromJson: (JSONObject) -> T,
    private val maxItems: Int = Int.MAX_VALUE,
) {
    private val file = File(context.filesDir, fileName)
    private val _items = MutableStateFlow(load())
    val items: StateFlow<List<T>> = _items.asStateFlow()

    private fun load(): List<T> = try {
        if (!file.exists()) emptyList()
        else JSONArray(file.readText()).let { arr -> List(arr.length()) { fromJson(arr.getJSONObject(it)) } }
    } catch (e: Exception) {
        Log.e("GestorNFC", "No se pudo leer ${file.name}", e)
        emptyList()
    }

    @Synchronized
    protected fun update(transform: (List<T>) -> List<T>) {
        val list = transform(_items.value).take(maxItems)
        _items.value = list
        try {
            file.writeText(JSONArray().apply { list.forEach { put(toJson(it)) } }.toString())
        } catch (e: Exception) {
            Log.e("GestorNFC", "No se pudo guardar ${file.name}", e)
        }
    }
}

class HistoryStore(context: Context) : JsonListStore<HistoryEntry>(
    context, "history.json",
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
    /** Las entradas nuevas van primero. */
    fun add(type: HistoryType, uid: String, chip: String, summary: String, ndef: ByteArray?) {
        val now = System.currentTimeMillis()
        // Si el tag ya tiene nombre en el historial, la nueva entrada lo hereda.
        val knownName = items.value.firstOrNull { it.uid == uid && it.name.isNotBlank() }?.name.orEmpty()
        update { listOf(HistoryEntry(System.nanoTime(), now, type, uid, chip, summary, knownName, "", ndef)) + it }
    }

    fun edit(id: Long, name: String, note: String) = update { list ->
        list.map { if (it.id == id) it.copy(name = name.trim(), note = note.trim()) else it }
    }

    fun delete(id: Long) = update { list -> list.filter { it.id != id } }

    fun clear() = update { emptyList() }
}

class TemplateStore(context: Context) : JsonListStore<Template>(
    context, "templates.json",
    toJson = { t ->
        JSONObject()
            .put("id", t.id).put("name", t.name).put("summary", t.summary)
            .put("ndef", t.ndef.toHex("")).put("created", t.created)
    },
    fromJson = { o ->
        Template(o.getLong("id"), o.getString("name"), o.optString("summary"), o.getString("ndef").hexToBytes(), o.optLong("created"))
    },
) {
    fun add(name: String, summary: String, ndef: ByteArray) {
        val now = System.currentTimeMillis()
        update { it + Template(System.nanoTime(), name.trim(), summary, ndef, now) }
    }

    fun rename(id: Long, name: String) = update { list -> list.map { if (it.id == id) it.copy(name = name.trim()) else it } }

    fun delete(id: Long) = update { list -> list.filter { it.id != id } }
}
