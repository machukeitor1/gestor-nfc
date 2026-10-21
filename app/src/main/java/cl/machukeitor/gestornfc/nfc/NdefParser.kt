package cl.machukeitor.gestornfc.nfc

import android.nfc.FormatException
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import java.nio.charset.Charset

/** Traduce registros NDEF a algo legible para la pantalla "Leer". */
object NdefParser {

    fun describe(record: NdefRecord): NdefRecordInfo {
        val size = record.toByteArray().size
        return when (record.tnf) {
            NdefRecord.TNF_EMPTY -> NdefRecordInfo("Vacío", "(registro vacío)", sizeBytes = size)

            NdefRecord.TNF_WELL_KNOWN -> when {
                record.type.contentEquals(NdefRecord.RTD_URI) -> describeUri(record.toUri()?.toString().orEmpty(), size)
                record.type.contentEquals(NdefRecord.RTD_TEXT) -> describeText(record.payload, size)
                record.type.contentEquals(NdefRecord.RTD_SMART_POSTER) -> describeSmartPoster(record.payload, size)
                else -> unknown(record, size)
            }

            NdefRecord.TNF_ABSOLUTE_URI -> describeUri(String(record.type, Charsets.UTF_8), size)

            NdefRecord.TNF_MIME_MEDIA -> {
                val mime = String(record.type, Charsets.US_ASCII).lowercase()
                when (mime) {
                    WifiConfig.MIME -> describeWifi(record.payload, size)
                    "text/vcard", "text/x-vcard" -> describeVcard(record.payload, size)
                    else -> NdefRecordInfo(
                        kind = "MIME",
                        value = mime,
                        details = listOf("Datos" to previewPayload(record.payload)),
                        sizeBytes = size,
                    )
                }
            }

            NdefRecord.TNF_EXTERNAL_TYPE -> {
                val type = String(record.type, Charsets.US_ASCII)
                if (type == "android.com:pkg") {
                    val pkg = String(record.payload, Charsets.US_ASCII)
                    NdefRecordInfo(
                        kind = "Abrir app",
                        value = pkg,
                        openUri = "https://play.google.com/store/apps/details?id=$pkg",
                        sizeBytes = size,
                    )
                } else {
                    NdefRecordInfo("Externo", type, listOf("Datos" to previewPayload(record.payload)), sizeBytes = size)
                }
            }

            else -> unknown(record, size)
        }
    }

    private fun describeUri(uri: String, size: Int): NdefRecordInfo {
        val lower = uri.lowercase()
        val kind = when {
            lower.startsWith("tel:") -> "Teléfono"
            lower.startsWith("mailto:") -> "Email"
            lower.startsWith("sms:") || lower.startsWith("smsto:") -> "SMS"
            lower.startsWith("geo:") -> "Ubicación"
            lower.startsWith("http") -> SocialNetwork.detect(uri)?.let { "Red social · ${it.displayName}" } ?: "URL"
            else -> "URI"
        }
        return NdefRecordInfo(kind, uri, openUri = uri, sizeBytes = size)
    }

    private fun describeText(payload: ByteArray, size: Int): NdefRecordInfo {
        if (payload.isEmpty()) return NdefRecordInfo("Texto", "", sizeBytes = size)
        val status = payload[0].u()
        val charset: Charset = if (status and 0x80 == 0) Charsets.UTF_8 else Charsets.UTF_16
        val langLength = status and 0x3F
        val lang = String(payload, 1, langLength, Charsets.US_ASCII)
        val text = String(payload, 1 + langLength, payload.size - 1 - langLength, charset)
        return NdefRecordInfo("Texto", text, listOf("Idioma" to lang), sizeBytes = size)
    }

    private fun describeSmartPoster(payload: ByteArray, size: Int): NdefRecordInfo {
        val inner = try {
            NdefMessage(payload).records.map { describe(it) }
        } catch (e: FormatException) {
            emptyList()
        }
        val uri = inner.firstOrNull { it.openUri != null }
        return NdefRecordInfo(
            kind = "Smart Poster",
            value = uri?.value ?: "(sin URL)",
            details = inner.filter { it !== uri }.map { it.kind to it.value },
            openUri = uri?.openUri,
            sizeBytes = size,
        )
    }

    private fun describeWifi(payload: ByteArray, size: Int): NdefRecordInfo {
        val wifi = WifiConfig.parse(payload)
        return NdefRecordInfo(
            kind = "Wi‑Fi",
            value = wifi?.ssid ?: "(no se pudo interpretar)",
            details = buildList {
                if (wifi != null) {
                    add("Seguridad" to wifi.auth.label)
                    if (wifi.password.isNotEmpty()) add("Contraseña" to wifi.password)
                }
            },
            sizeBytes = size,
        )
    }

    private fun describeVcard(payload: ByteArray, size: Int): NdefRecordInfo {
        val text = String(payload, Charsets.UTF_8)
        val fields = text.lines().mapNotNull { line ->
            val idx = line.indexOf(':')
            if (idx <= 0) return@mapNotNull null
            val key = line.substring(0, idx).substringBefore(';').uppercase()
            val value = line.substring(idx + 1).replace("\\,", ",").replace("\\;", ";")
            val label = when (key) {
                "FN" -> "Nombre"
                "TEL" -> "Teléfono"
                "EMAIL" -> "Email"
                "ORG" -> "Empresa"
                "TITLE" -> "Cargo"
                "URL" -> "Web"
                "ADR" -> "Dirección"
                "NOTE" -> "Nota"
                else -> null
            }
            label?.let { it to value.trim(';').replace(";;", " ").replace(';', ' ').trim() }
        }
        val name = fields.firstOrNull { it.first == "Nombre" }?.second ?: "(sin nombre)"
        return NdefRecordInfo("Contacto", name, fields.filter { it.first != "Nombre" }, sizeBytes = size)
    }

    private fun unknown(record: NdefRecord, size: Int) = NdefRecordInfo(
        kind = "Desconocido",
        value = "TNF ${record.tnf}, tipo ${String(record.type, Charsets.US_ASCII)}",
        details = listOf("Datos" to previewPayload(record.payload)),
        sizeBytes = size,
    )

    private fun previewPayload(payload: ByteArray): String {
        val printable = payload.all { it.u() in 0x20..0x7E || it.u() == 0x0A || it.u() == 0x0D }
        return if (printable) String(payload, Charsets.US_ASCII) else payload.take(64).toByteArray().toHex(" ") +
            if (payload.size > 64) " …" else ""
    }
}
