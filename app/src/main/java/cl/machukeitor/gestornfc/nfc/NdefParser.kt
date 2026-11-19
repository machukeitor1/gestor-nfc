package cl.machukeitor.gestornfc.nfc

import android.nfc.FormatException
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import cl.machukeitor.gestornfc.i18n.tr
import java.nio.charset.Charset

/** Traduce registros NDEF a algo legible para la pantalla "Leer". */
object NdefParser {

    private val data get() = tr("Datos", "Data", "Dados")

    fun describe(record: NdefRecord): NdefRecordInfo {
        val size = record.toByteArray().size
        return when (record.tnf) {
            NdefRecord.TNF_EMPTY -> NdefRecordInfo(
                tr("Vacío", "Empty", "Vazio"),
                tr("(registro vacío)", "(empty record)", "(registro vazio)"),
                sizeBytes = size,
                empty = true,
            )

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
                        details = listOf(data to previewPayload(record.payload)),
                        sizeBytes = size,
                    )
                }
            }

            NdefRecord.TNF_EXTERNAL_TYPE -> {
                val type = String(record.type, Charsets.US_ASCII)
                if (type.lowercase() == AutoAction.NDEF_TYPE) {
                    val json = String(record.payload, Charsets.UTF_8)
                    val (name, actions) = runCatching { AutoAction.fromJson(json) }.getOrDefault("" to emptyList())
                    NdefRecordInfo(
                        kind = tr("Automatización", "Automation", "Automação"),
                        value = name.ifBlank { tr("(sin nombre)", "(no name)", "(sem nome)") },
                        details = actions.mapIndexed { i, a -> tr("Acción", "Action", "Ação") + " ${i + 1}" to a.label },
                        sizeBytes = size,
                        automationJson = json,
                    )
                } else if (type == "android.com:pkg") {
                    val pkg = String(record.payload, Charsets.US_ASCII)
                    NdefRecordInfo(
                        kind = tr("Abrir app", "Open app", "Abrir app"),
                        value = pkg,
                        openUri = "https://play.google.com/store/apps/details?id=$pkg",
                        sizeBytes = size,
                    )
                } else {
                    NdefRecordInfo(
                        tr("Externo", "External", "Externo"), type,
                        listOf(data to previewPayload(record.payload)), sizeBytes = size,
                    )
                }
            }

            else -> unknown(record, size)
        }
    }

    private fun describeUri(uri: String, size: Int): NdefRecordInfo {
        val lower = uri.lowercase()
        val kind = when {
            lower.startsWith("tel:") -> tr("Teléfono", "Phone", "Telefone")
            lower.startsWith("mailto:") -> "Email"
            lower.startsWith("sms:") || lower.startsWith("smsto:") -> "SMS"
            lower.startsWith("geo:") -> tr("Ubicación", "Location", "Localização")
            lower.startsWith("http") -> SocialNetwork.detect(uri)
                ?.let { tr("Red social", "Social network", "Rede social") + " · ${it.displayName}" } ?: "URL"
            else -> "URI"
        }
        return NdefRecordInfo(kind, uri, openUri = uri, sizeBytes = size)
    }

    private fun describeText(payload: ByteArray, size: Int): NdefRecordInfo {
        val kind = tr("Texto", "Text", "Texto")
        if (payload.isEmpty()) return NdefRecordInfo(kind, "", sizeBytes = size)
        val status = payload[0].u()
        val charset: Charset = if (status and 0x80 == 0) Charsets.UTF_8 else Charsets.UTF_16
        val langLength = status and 0x3F
        val lang = String(payload, 1, langLength, Charsets.US_ASCII)
        val text = String(payload, 1 + langLength, payload.size - 1 - langLength, charset)
        return NdefRecordInfo(kind, text, listOf(tr("Idioma", "Language", "Idioma") to lang), sizeBytes = size)
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
            value = uri?.value ?: tr("(sin URL)", "(no URL)", "(sem URL)"),
            details = inner.filter { it !== uri }.map { it.kind to it.value },
            openUri = uri?.openUri,
            sizeBytes = size,
        )
    }

    private fun describeWifi(payload: ByteArray, size: Int): NdefRecordInfo {
        val wifi = WifiConfig.parse(payload)
        return NdefRecordInfo(
            kind = "Wi‑Fi",
            value = wifi?.ssid ?: tr("(no se pudo interpretar)", "(could not be read)", "(não foi possível interpretar)"),
            details = buildList {
                if (wifi != null) {
                    add(tr("Seguridad", "Security", "Segurança") to wifi.auth.label)
                    if (wifi.password.isNotEmpty()) add(tr("Contraseña", "Password", "Senha") to wifi.password)
                }
            },
            sizeBytes = size,
        )
    }

    private fun describeVcard(payload: ByteArray, size: Int): NdefRecordInfo {
        val text = String(payload, Charsets.UTF_8)
        var name: String? = null
        val fields = text.lines().mapNotNull { line ->
            val idx = line.indexOf(':')
            if (idx <= 0) return@mapNotNull null
            val key = line.substring(0, idx).substringBefore(';').uppercase()
            val value = line.substring(idx + 1).replace("\\,", ",").replace("\\;", ";")
                .trim(';').replace(";;", " ").replace(';', ' ').trim()
            if (key == "FN") {
                name = value
                return@mapNotNull null
            }
            val label = when (key) {
                "TEL" -> tr("Teléfono", "Phone", "Telefone")
                "EMAIL" -> "Email"
                "ORG" -> tr("Empresa", "Company", "Empresa")
                "TITLE" -> tr("Cargo", "Job title", "Cargo")
                "URL" -> "Web"
                "ADR" -> tr("Dirección", "Address", "Endereço")
                "NOTE" -> tr("Nota", "Note", "Nota")
                else -> null
            }
            label?.let { it to value }
        }
        return NdefRecordInfo(
            tr("Contacto", "Contact", "Contato"),
            name ?: tr("(sin nombre)", "(no name)", "(sem nome)"),
            fields,
            sizeBytes = size,
        )
    }

    private fun unknown(record: NdefRecord, size: Int) = NdefRecordInfo(
        kind = tr("Desconocido", "Unknown", "Desconhecido"),
        value = "TNF ${record.tnf}, " + tr("tipo", "type", "tipo") + " ${String(record.type, Charsets.US_ASCII)}",
        details = listOf(data to previewPayload(record.payload)),
        sizeBytes = size,
    )

    private fun previewPayload(payload: ByteArray): String {
        val printable = payload.all { it.u() in 0x20..0x7E || it.u() == 0x0A || it.u() == 0x0D }
        return if (printable) String(payload, Charsets.US_ASCII) else payload.take(64).toByteArray().toHex(" ") +
            if (payload.size > 64) " …" else ""
    }
}
