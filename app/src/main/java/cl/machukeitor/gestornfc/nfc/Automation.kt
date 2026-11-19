package cl.machukeitor.gestornfc.nfc

import cl.machukeitor.gestornfc.i18n.tr
import org.json.JSONArray
import org.json.JSONObject

/**
 * Acciones que un tag puede ejecutar en el teléfono que lo lee (con Gestor NFC instalado).
 * Se guardan como JSON compacto en un registro NDEF externo "machukeitor.cl:auto".
 */
sealed interface AutoAction {
    val label: String

    enum class Toggle(val key: String, private val es: String, private val en: String, private val pt: String) {
        ON("on", "Encender", "Turn on", "Ligar"),
        OFF("off", "Apagar", "Turn off", "Desligar"),
        TOGGLE("toggle", "Alternar", "Toggle", "Alternar");

        val label get() = tr(es, en, pt)
    }

    enum class Ringer(val key: String, private val es: String, private val en: String, private val pt: String) {
        NORMAL("normal", "Normal", "Normal", "Normal"),
        VIBRATE("vibrate", "Vibrar", "Vibrate", "Vibrar"),
        SILENT("silent", "Silencio", "Silent", "Silencioso");

        val label get() = tr(es, en, pt)
    }

    enum class Stream(val key: String, private val es: String, private val en: String, private val pt: String) {
        MEDIA("media", "Multimedia", "Media", "Mídia"),
        RING("ring", "Timbre", "Ringtone", "Toque"),
        ALARM("alarm", "Alarma", "Alarm", "Alarme");

        val label get() = tr(es, en, pt)
    }

    data class Torch(val mode: Toggle) : AutoAction {
        override val label get() = tr("Linterna", "Flashlight", "Lanterna") + ": ${mode.label.lowercase()}"
    }

    data class RingerMode(val mode: Ringer) : AutoAction {
        override val label get() = tr("Sonido", "Sound", "Som") + ": ${mode.label.lowercase()}"
    }

    data class Volume(val stream: Stream, val percent: Int) : AutoAction {
        override val label get() = tr("Volumen", "Volume", "Volume") + " ${stream.label.lowercase()}: $percent%"
    }

    data class Brightness(val percent: Int) : AutoAction {
        override val label get() = tr("Brillo", "Brightness", "Brilho") + ": $percent%"
    }

    data class Timer(val minutes: Int, val message: String) : AutoAction {
        override val label get() = tr("Temporizador", "Timer", "Temporizador") + ": $minutes min" +
            if (message.isNotBlank()) " ($message)" else ""
    }

    data class OpenApp(val packageName: String) : AutoAction {
        override val label get() = tr("Abrir app", "Open app", "Abrir app") + ": $packageName"
    }

    data class OpenUrl(val url: String) : AutoAction {
        override val label get() = tr("Abrir", "Open", "Abrir") + ": $url"
    }

    data object WifiPanel : AutoAction {
        override val label get() = tr("Abrir panel de Wi‑Fi", "Open Wi‑Fi panel", "Abrir painel de Wi‑Fi")
    }

    data object BluetoothSettings : AutoAction {
        override val label get() = tr("Abrir ajustes de Bluetooth", "Open Bluetooth settings", "Abrir configurações de Bluetooth")
    }

    companion object {
        const val DOMAIN = "machukeitor.cl"
        const val TYPE = "auto"
        /** Tipo NDEF completo, tal como lo ve Android (siempre en minúsculas). */
        const val NDEF_TYPE = "$DOMAIN:$TYPE"

        fun toJson(name: String, actions: List<AutoAction>): String = JSONObject()
            .put("n", name)
            .put("a", JSONArray().apply { actions.forEach { put(it.toJson()) } })
            .toString()

        /** Devuelve (nombre, acciones). Ignora las acciones que no reconoce (versiones futuras). */
        fun fromJson(json: String): Pair<String, List<AutoAction>> {
            val o = JSONObject(json)
            val arr = o.optJSONArray("a") ?: JSONArray()
            val actions = List(arr.length()) { arr.optJSONObject(it)?.let(::actionFromJson) }.filterNotNull()
            return o.optString("n") to actions
        }

        fun listToJson(actions: List<AutoAction>): String =
            JSONArray().apply { actions.forEach { put(it.toJson()) } }.toString()

        fun listFromJson(json: String): List<AutoAction> = runCatching {
            val arr = JSONArray(json)
            List(arr.length()) { arr.optJSONObject(it)?.let(::actionFromJson) }.filterNotNull()
        }.getOrDefault(emptyList())

        private fun AutoAction.toJson(): JSONObject = when (this) {
            is Torch -> JSONObject().put("t", "torch").put("m", mode.key)
            is RingerMode -> JSONObject().put("t", "ringer").put("m", mode.key)
            is Volume -> JSONObject().put("t", "volume").put("s", stream.key).put("p", percent)
            is Brightness -> JSONObject().put("t", "bright").put("p", percent)
            is Timer -> JSONObject().put("t", "timer").put("min", minutes).apply { if (message.isNotBlank()) put("l", message) }
            is OpenApp -> JSONObject().put("t", "app").put("p", packageName)
            is OpenUrl -> JSONObject().put("t", "url").put("u", url)
            WifiPanel -> JSONObject().put("t", "wifi")
            BluetoothSettings -> JSONObject().put("t", "bt")
        }

        private fun actionFromJson(o: JSONObject): AutoAction? = when (o.optString("t")) {
            "torch" -> Toggle.entries.firstOrNull { it.key == o.optString("m") }?.let(::Torch)
            "ringer" -> Ringer.entries.firstOrNull { it.key == o.optString("m") }?.let(::RingerMode)
            "volume" -> Stream.entries.firstOrNull { it.key == o.optString("s") }
                ?.let { Volume(it, o.optInt("p").coerceIn(0, 100)) }
            "bright" -> Brightness(o.optInt("p").coerceIn(0, 100))
            "timer" -> o.optInt("min").takeIf { it > 0 }?.let { Timer(it, o.optString("l")) }
            "app" -> o.optString("p").takeIf { it.isNotBlank() }?.let(::OpenApp)
            "url" -> o.optString("u").takeIf { it.isNotBlank() }?.let(::OpenUrl)
            "wifi" -> WifiPanel
            "bt" -> BluetoothSettings
            else -> null
        }
    }
}
