package cl.machukeitor.gestornfc.nfc

import android.nfc.Tag
import android.nfc.tech.IsoDep
import android.nfc.tech.MifareClassic
import android.nfc.tech.MifareUltralight
import android.nfc.tech.Ndef
import android.nfc.tech.NfcA
import android.nfc.tech.NfcF
import android.nfc.tech.NfcV
import java.io.IOException

/**
 * Lee todo lo que se puede saber de un tag, al estilo NXP TagInfo.
 * Debe llamarse fuera del hilo principal (hace I/O con el tag).
 */
object TagInspector {

    fun inspect(tag: Tag): TagInfo {
        val uidBytes = tag.id ?: ByteArray(0)
        val techs = tag.techList.map { it.substringAfterLast('.') }
        val sections = mutableListOf<InfoSection>()

        var chip = "Desconocido"
        var memory = emptyList<MemoryRow>()
        var memoryNote: String? = null
        var writeProtected = false

        // NDEF: se usa el mensaje que Android ya leyó al detectar el tag (sin I/O extra).
        val ndefInfo = Ndef.get(tag)?.let { ndef ->
            val message = ndef.cachedNdefMessage
            NdefInfo(
                type = ndefTypeName(ndef.type),
                maxSize = ndef.maxSize,
                usedSize = message?.byteArrayLength ?: 0,
                writable = ndef.isWritable,
                canMakeReadOnly = ndef.canMakeReadOnly(),
                records = message?.records?.map(NdefParser::describe).orEmpty(),
                raw = message?.toByteArray(),
            )
        }

        NfcA.get(tag)?.let { nfcA ->
            sections += InfoSection(
                "ISO 14443‑3A (NfcA)",
                listOf(
                    "ATQA" to nfcA.atqa.reversedArray().toHex(""),
                    "SAK" to "%02X".format(nfcA.sak.toInt()),
                    "Transceive máx." to "${nfcA.maxTransceiveLength} bytes",
                ),
            )
            if (nfcA.sak.toInt() == 0x00 && IsoDep.get(tag) == null) {
                val result = UltralightReader(tag, nfcA, uidBytes).read()
                chip = result.chip
                sections += result.sections
                memory = result.memory
                memoryNote = result.memoryNote
                writeProtected = result.writeProtected
            }
        }

        MifareClassic.get(tag)?.let { mc ->
            chip = when (mc.type) {
                MifareClassic.TYPE_CLASSIC -> "MIFARE Classic"
                MifareClassic.TYPE_PLUS -> "MIFARE Plus"
                MifareClassic.TYPE_PRO -> "MIFARE Pro"
                else -> "MIFARE Classic (compatible)"
            } + " " + sizeLabel(mc.size)
            sections += InfoSection(
                "MIFARE Classic",
                listOf(
                    "Memoria" to "${mc.size} bytes",
                    "Sectores" to mc.sectorCount.toString(),
                    "Bloques" to mc.blockCount.toString(),
                    "Nota" to "Los datos están protegidos por claves de sector. " +
                        "Si el teléfono no tiene chip NFC de NXP, puede que no lo lea.",
                ),
            )
        }
        if (MifareClassic.get(tag) == null && "MifareClassic" !in techs &&
            NfcA.get(tag)?.sak?.toInt()?.let { it == 0x08 || it == 0x18 } == true
        ) {
            chip = "MIFARE Classic (este teléfono no lo soporta)"
        }

        IsoDep.get(tag)?.let { iso ->
            if (chip == "Desconocido") chip = "ISO 14443‑4 (tarjeta inteligente)"
            sections += InfoSection(
                "ISO 14443‑4 (IsoDep)",
                buildList {
                    iso.historicalBytes?.let { add("Historical bytes" to it.toHex(" ")) }
                    iso.hiLayerResponse?.let { add("Hi‑layer response" to it.toHex(" ")) }
                    add("APDU extendido" to if (iso.isExtendedLengthApduSupported) "Sí" else "No")
                },
            )
        }

        NfcV.get(tag)?.let { v ->
            if (chip == "Desconocido") chip = "ISO 15693 (NFC‑V)"
            sections += InfoSection(
                "ISO 15693 (NfcV)",
                listOf("DSFID" to "%02X".format(v.dsfId.toInt()), "Response flags" to "%02X".format(v.responseFlags.toInt())),
            )
        }

        NfcF.get(tag)?.let { f ->
            if (chip == "Desconocido") chip = "FeliCa (NFC‑F)"
            sections += InfoSection(
                "FeliCa (NfcF)",
                listOf("Manufacturer" to f.manufacturer.toHex(" "), "System code" to f.systemCode.toHex(" ")),
            )
        }

        if (chip == "Desconocido" && MifareUltralight.get(tag) != null) chip = "MIFARE Ultralight (compatible)"

        return TagInfo(
            uid = uidBytes.toHex(),
            chip = chip,
            manufacturer = manufacturerName(uidBytes),
            technologies = techs,
            sections = sections,
            ndef = ndefInfo,
            memory = memory,
            memoryNote = memoryNote,
            writeProtected = writeProtected,
        )
    }

    private fun sizeLabel(bytes: Int) = when (bytes) {
        MifareClassic.SIZE_MINI -> "Mini"
        MifareClassic.SIZE_1K -> "1K"
        MifareClassic.SIZE_2K -> "2K"
        MifareClassic.SIZE_4K -> "4K"
        else -> "$bytes B"
    }

    private fun ndefTypeName(type: String) = when (type) {
        Ndef.NFC_FORUM_TYPE_1 -> "NFC Forum Type 1"
        Ndef.NFC_FORUM_TYPE_2 -> "NFC Forum Type 2"
        Ndef.NFC_FORUM_TYPE_3 -> "NFC Forum Type 3"
        Ndef.NFC_FORUM_TYPE_4 -> "NFC Forum Type 4"
        Ndef.MIFARE_CLASSIC -> "MIFARE Classic"
        else -> type
    }

    /** Fabricante según el primer byte del UID (ISO/IEC 7816‑6). Solo vale para UIDs de 7 u 8 bytes. */
    private fun manufacturerName(uid: ByteArray): String {
        if (uid.size < 7) return "No determinable (UID de ${uid.size} bytes)"
        return when (uid[0].u()) {
            0x02 -> "STMicroelectronics"
            0x04 -> "NXP Semiconductors"
            0x05 -> "Infineon Technologies"
            0x07 -> "Texas Instruments"
            0x16 -> "EM Microelectronic"
            0x28 -> "Sony"
            else -> "Desconocido (0x%02X)".format(uid[0].u())
        }
    }
}

/** Lectura a bajo nivel de NTAG21x / MIFARE Ultralight con comandos directos. */
private class UltralightReader(private val tag: Tag, private var nfcA: NfcA, private val uid: ByteArray) {

    class Result(
        val chip: String,
        val sections: List<InfoSection>,
        val memory: List<MemoryRow>,
        val memoryNote: String?,
        val writeProtected: Boolean = false,
    )

    fun read(): Result {
        val sections = mutableListOf<InfoSection>()
        try {
            nfcA.connect()
            nfcA.timeout = 500

            val version = transceiveOrNull(byteArrayOf(0x60))
            val model = version?.let { Ntag.identify(it) }
            if (version == null) reconnect()

            if (version != null && version.size >= 8) {
                sections += InfoSection(
                    "GET_VERSION",
                    listOf(
                        "Respuesta" to version.toHex(" "),
                        "Fabricante" to if (version[1].u() == 0x04) "NXP" else "0x%02X".format(version[1].u()),
                        "Tipo" to when (version[2].u()) { 0x03 -> "Ultralight"; 0x04 -> "NTAG"; else -> "0x%02X".format(version[2].u()) },
                        "Memoria" to "~${1 shl (version[6].u() shr 1)} bytes",
                    ),
                )
            }

            // Firma de originalidad NXP (solo chips que responden GET_VERSION).
            if (version != null) {
                val sig = transceiveOrNull(byteArrayOf(0x3C, 0x00))
                if (sig == null) reconnect()
                sections += InfoSection(
                    "Originalidad",
                    if (sig != null && sig.size == 32) {
                        val ok = NxpSignature.verify(uid, sig)
                        listOf(
                            "Firma NXP" to if (ok) "✔ Válida: chip NXP original" else "✘ No válida: probablemente un clon",
                            "Firma" to sig.toHex(""),
                        )
                    } else {
                        listOf("Firma NXP" to "El chip no entregó firma (puede ser un clon)")
                    },
                )
            }

            val totalPages = model?.pages ?: if (MifareUltralight.get(tag)?.type == MifareUltralight.TYPE_ULTRALIGHT_C) 48 else 16
            val pages = readPages(totalPages)
            val memoryNote = if (pages.size < totalPages)
                "Se leyeron ${pages.size} de $totalPages páginas; el resto está protegido con contraseña."
            else null

            sections += lockSection(pages)
            if (model?.hasConfig == true) configSection(pages, model)?.let { sections += it }

            return Result(
                chip = model?.name ?: version?.let { "Desconocido (versión ${it.toHex(" ")})" }
                    ?: if (MifareUltralight.get(tag)?.type == MifareUltralight.TYPE_ULTRALIGHT_C) "MIFARE Ultralight C" else "MIFARE Ultralight",
                sections = sections,
                memory = pages.mapIndexed { i, p -> MemoryRow(i, p.toHex(" "), ascii(p), pageNote(i, totalPages, model)) },
                memoryNote = memoryNote,
                // Si no se pudo leer la configuración es porque está protegida (también contra lectura).
                writeProtected = model?.hasConfig == true &&
                    (pages.size <= model.cfg0 || pages[model.cfg0][3].u() <= model.userEnd),
            )
        } catch (e: Exception) {
            // IOException si se pierde el tag; SecurityException ("Tag is out of date") si se alejó y volvió.
            return Result("Tipo 2 (lectura interrumpida)", sections, emptyList(), "Se perdió el contacto con el tag. Mantenlo quieto hasta que termine.")
        } finally {
            runCatching { nfcA.close() }
        }
    }

    /** Lee de 4 en 4 páginas (comando READ 0x30) hasta el final o hasta un error. */
    private fun readPages(total: Int): List<ByteArray> {
        val pages = mutableListOf<ByteArray>()
        var page = 0
        while (page < total) {
            val data = transceiveOrNull(byteArrayOf(0x30, page.toByte()))
            if (data == null || data.size < 16) {
                reconnect()
                break
            }
            for (i in 0 until 4) {
                if (page + i < total) pages += data.copyOfRange(i * 4, i * 4 + 4)
            }
            page += 4
        }
        return pages
    }

    private fun lockSection(pages: List<ByteArray>): InfoSection {
        val items = mutableListOf<Pair<String, String>>()
        if (pages.size > 2) {
            val lock0 = pages[2][2].u()
            val lock1 = pages[2][3].u()
            items += "Bytes de bloqueo estático" to "%02X %02X".format(lock0, lock1)
            items += "Páginas 3‑15 bloqueadas" to if (lock0 == 0 && lock1 == 0) "Ninguna" else "Sí (parcial o total)"
        }
        if (pages.size > 3) {
            val cc = pages[3]
            items += "Capability Container" to cc.toHex(" ")
            items += "Acceso de escritura (CC)" to if (cc[3].u() == 0x00) "Libre" else "Solo lectura (0x%02X)".format(cc[3].u())
        }
        return InfoSection("Bloqueo", items)
    }

    private fun configSection(pages: List<ByteArray>, model: Ntag.Model): InfoSection? {
        val cfg0 = model.pages - 4
        val cfg1 = model.pages - 3
        val dynLock = model.pages - 5
        if (pages.size <= cfg1) return InfoSection(
            "Configuración",
            listOf("Estado" to "No se pudo leer la configuración (protegida con contraseña)"),
        )
        val auth0 = pages[cfg0][3].u()
        val access = pages[cfg1][0].u()
        val prot = access and 0x80 != 0
        val cfgLock = access and 0x40 != 0
        val passwordOn = auth0 < model.pages
        return InfoSection(
            "Configuración",
            buildList {
                if (model.name.startsWith("NTAG21") && model.name != "NTAG210" && model.name != "NTAG212")
                    add("Bloqueo dinámico" to pages[dynLock].copyOfRange(0, 3).toHex(" "))
                add("AUTH0" to "0x%02X".format(auth0))
                add(
                    "Contraseña" to if (passwordOn)
                        "Activa desde la página $auth0 (${if (prot) "protege lectura y escritura" else "protege solo escritura"})"
                    else "Sin contraseña"
                )
                add("Configuración bloqueada" to if (cfgLock) "Sí (permanente)" else "No")
            },
        )
    }

    private fun pageNote(page: Int, total: Int, model: Ntag.Model?): String? = when {
        page <= 1 -> "UID"
        page == 2 -> "UID / bloqueo"
        page == 3 -> "Capability Container"
        model?.hasConfig != true -> null
        page == total - 5 && model.name.startsWith("NTAG21") && model.name != "NTAG210" && model.name != "NTAG212" -> "Bloqueo dinámico"
        page == total - 4 -> "CFG0 (AUTH0)"
        page == total - 3 -> "CFG1 (ACCESS)"
        page == total - 2 -> "Contraseña (no legible)"
        page == total - 1 -> "PACK"
        else -> null
    }

    private fun ascii(bytes: ByteArray) =
        String(CharArray(bytes.size) { val c = bytes[it].u(); if (c in 0x20..0x7E) c.toChar() else '·' })

    private fun transceiveOrNull(cmd: ByteArray): ByteArray? = try {
        val r = nfcA.transceive(cmd)
        if (r.size <= 1) null else r // 1 byte = NAK
    } catch (e: IOException) {
        null
    }
    // SecurityException / IllegalStateException no se atrapan aquí: significan que el tag ya no está
    // y deben cortar la lectura completa (las maneja read()).

    /** Tras un NAK o timeout el tag queda inactivo: hay que reconectar. */
    private fun reconnect() {
        runCatching { nfcA.close() }
        nfcA = NfcA.get(tag)
        nfcA.connect()
        nfcA.timeout = 500
    }
}
