package cl.machukeitor.gestornfc.nfc

import android.net.Uri
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import java.util.Locale

/** Contenido a grabar en un tag. Cada tipo sabe convertirse en un mensaje NDEF. */
sealed interface WritePayload {
    /** Texto corto para mostrar al usuario ("URL: https://…"). */
    val summary: String
    fun toMessage(): NdefMessage

    val sizeBytes: Int get() = toMessage().byteArrayLength

    data class Link(val label: String, val uri: String) : WritePayload {
        override val summary get() = "$label: $uri"
        override fun toMessage() = NdefMessage(NdefRecord.createUri(uri))
    }

    data class Text(val text: String, val language: String = Locale.getDefault().language) : WritePayload {
        override val summary get() = "Texto: $text"
        override fun toMessage() = NdefMessage(NdefRecord.createTextRecord(language, text))
    }

    data class Wifi(val config: WifiConfig) : WritePayload {
        override val summary get() = "Wi‑Fi: ${config.ssid}"
        override fun toMessage() = NdefMessage(NdefRecord.createMime(WifiConfig.MIME, config.toPayload()))
    }

    data class Contact(
        val firstName: String,
        val lastName: String,
        val phone: String,
        val email: String,
        val company: String,
        val title: String,
        val website: String,
        val address: String,
    ) : WritePayload {
        val fullName get() = listOf(firstName, lastName).filter { it.isNotBlank() }.joinToString(" ")
        override val summary get() = "Contacto: $fullName"

        fun toVcard(): String = buildString {
            append("BEGIN:VCARD\nVERSION:3.0\n")
            append("N:${esc(lastName)};${esc(firstName)};;;\n")
            append("FN:${esc(fullName)}\n")
            if (company.isNotBlank()) append("ORG:${esc(company)}\n")
            if (title.isNotBlank()) append("TITLE:${esc(title)}\n")
            if (phone.isNotBlank()) append("TEL;TYPE=CELL:${phone.trim()}\n")
            if (email.isNotBlank()) append("EMAIL:${email.trim()}\n")
            if (website.isNotBlank()) append("URL:${website.trim()}\n")
            if (address.isNotBlank()) append("ADR:;;${esc(address)};;;;\n")
            append("END:VCARD")
        }

        override fun toMessage() = NdefMessage(NdefRecord.createMime("text/vcard", toVcard().toByteArray(Charsets.UTF_8)))

        private fun esc(s: String) = s.trim().replace("\\", "\\\\").replace(",", "\\,").replace(";", "\\;")
    }

    /**
     * [googleMaps] = true: enlace https de Google Maps (funciona en Android y iPhone).
     * false: URI "geo:" (solo Android, abre la app de mapas que el usuario elija).
     */
    data class Location(val lat: Double, val lng: Double, val label: String, val googleMaps: Boolean) : WritePayload {
        private val coords get() = "%.6f,%.6f".format(Locale.US, lat, lng)
        val uri get() = if (googleMaps) {
            "https://maps.google.com/?q=$coords"
        } else {
            "geo:$coords?q=$coords" + if (label.isNotBlank()) "(${Uri.encode(label.trim())})" else ""
        }
        override val summary get() = "Ubicación: " + label.ifBlank { coords }
        override fun toMessage() = NdefMessage(NdefRecord.createUri(uri))
    }

    /** Android Application Record: abre la app (o su ficha en Play Store si no está instalada). Solo Android. */
    data class App(val packageName: String, val appName: String) : WritePayload {
        override val summary get() = "Abrir app: " + appName.ifBlank { packageName }
        override fun toMessage() = NdefMessage(NdefRecord.createApplicationRecord(packageName))
    }

    /** Deja el tag vacío (formateado como NDEF, sin contenido). */
    data object Empty : WritePayload {
        override val summary get() = "Borrar contenido"
        override fun toMessage() = NdefMessage(NdefRecord(NdefRecord.TNF_EMPTY, null, null, null))
    }

    /** Mensaje NDEF ya armado: copias de otro tag, historial y plantillas. */
    class Raw(val bytes: ByteArray, override val summary: String) : WritePayload {
        override fun toMessage() = NdefMessage(bytes)
    }
}
