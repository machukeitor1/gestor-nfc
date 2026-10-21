package cl.machukeitor.gestornfc.nfc

import android.nfc.NdefMessage
import android.nfc.Tag
import android.nfc.TagLostException
import android.nfc.tech.NfcA
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.security.MessageDigest

/**
 * Comandos directos para NTAG21x / MIFARE Ultralight EV1: identificar el chip,
 * poner/cambiar/quitar contraseña y escribir NDEF en tags protegidos.
 *
 * Mapa de memoria (las últimas páginas son de configuración):
 *   pages-4 = CFG0 (byte 3: AUTH0, página desde la que se exige contraseña)
 *   pages-3 = CFG1 (byte 0: ACCESS, bit 7 PROT = proteger también la lectura)
 *   pages-2 = PWD  (4 bytes, no se puede leer)
 *   pages-1 = PACK (2 bytes que el tag responde al autenticarse)
 */
object Ntag {

    /** [userEnd] = última página de datos de usuario. */
    data class Model(val name: String, val pages: Int, val hasConfig: Boolean, val userEnd: Int) {
        val cfg0 get() = pages - 4
        val cfg1 get() = pages - 3
        val pwdPage get() = pages - 2
        val packPage get() = pages - 1
        val userBytes get() = (userEnd - FIRST_USER_PAGE + 1) * 4
    }

    const val FIRST_USER_PAGE = 4
    /** AUTH0 = 4: se protege desde la primera página de datos (el contenido NDEF completo). */
    private const val PROTECT_FROM_PAGE = 0x04
    private const val AUTH0_DISABLED = 0xFF

    fun identify(v: ByteArray): Model? {
        if (v.size < 8) return null
        val type = v[2].u()
        val storage = v[6].u()
        return when (type) {
            0x04 -> when (storage) {
                0x0B -> Model("NTAG210", 20, true, 15)
                0x0E -> Model("NTAG212", 41, true, 35)
                0x0F -> Model("NTAG213", 45, true, 39)
                0x11 -> Model("NTAG215", 135, true, 129)
                0x13 -> Model("NTAG216", 231, true, 225)
                else -> Model("NTAG (desconocido)", 45, false, 39)
            }
            0x03 -> when (storage) {
                0x0B -> Model("MIFARE Ultralight EV1 (MF0UL11)", 20, true, 15)
                0x0E -> Model("MIFARE Ultralight EV1 (MF0UL21)", 41, true, 35)
                else -> Model("MIFARE Ultralight EV1", 20, false, 15)
            }
            else -> null
        }
    }

    /** Estado de protección de un tag. */
    data class Protection(val model: Model, val auth0: Int?) {
        /** auth0 == null: no se pudo leer la configuración (está protegida también para lectura). */
        val writeProtected get() = auth0 == null || auth0 <= model.userEnd
    }

    /** Devuelve null si el tag no es un NTAG21x / Ultralight EV1 con contraseña. */
    fun probe(tag: Tag): Protection? {
        Session(tag).use { s ->
            val model = s.model() ?: return null
            if (!model.hasConfig) return null
            val cfg = s.readOrNull(model.cfg0)
            return Protection(model, cfg?.get(3)?.u())
        }
    }

    fun setPassword(tag: Tag, pwd: ByteArray): WriteResult = withTag(tag) { s, model ->
        val cfg = s.readOrNull(model.cfg0)
            ?: return@withTag WriteResult.Failure("No se pudo leer la configuración del tag.")
        if (cfg[3].u() <= model.userEnd) {
            return@withTag WriteResult.Failure("Este tag ya tiene contraseña. Usa \"Cambiar contraseña\".")
        }
        val access = cfg[4].u()
        if (access and 0x40 != 0) {
            return@withTag WriteResult.Failure("La configuración de este tag está bloqueada de fábrica o permanentemente; no admite contraseña.")
        }
        s.write(model.pwdPage, pwd)
        s.write(model.packPage, byteArrayOf(0, 0, 0, 0))
        // Solo escritura: se apaga PROT (bit 7) y AUTHLIM = 0 (bits 0‑2, intentos ilimitados: nunca se bloquea).
        s.write(model.cfg1, byteArrayOf((access and 0x78).toByte(), cfg[5], cfg[6], cfg[7]))
        // AUTH0 al final: es lo que activa la protección.
        s.write(model.cfg0, byteArrayOf(cfg[0], cfg[1], cfg[2], PROTECT_FROM_PAGE.toByte()))
        WriteResult.Success("Contraseña activada. Desde ahora el tag solo se puede modificar con la contraseña.")
    }

    fun changePassword(tag: Tag, oldPwd: ByteArray, newPwd: ByteArray): WriteResult = withTag(tag) { s, model ->
        if (!s.auth(oldPwd)) return@withTag WriteResult.WrongPassword
        s.write(model.pwdPage, newPwd)
        WriteResult.Success("Contraseña cambiada.")
    }

    fun removePassword(tag: Tag, pwd: ByteArray): WriteResult = withTag(tag) { s, model ->
        if (!s.auth(pwd)) return@withTag WriteResult.WrongPassword
        val cfg = s.read(model.cfg0)
        s.write(model.cfg0, byteArrayOf(cfg[0], cfg[1], cfg[2], AUTH0_DISABLED.toByte()))
        s.write(model.pwdPage, byteArrayOf(-1, -1, -1, -1)) // valor de fábrica: FF FF FF FF
        WriteResult.Success("Contraseña quitada. El tag vuelve a estar libre.")
    }

    /** Escribe un mensaje NDEF página por página, autenticándose antes. */
    fun writeNdef(tag: Tag, message: NdefMessage, pwd: ByteArray): WriteResult = withTag(tag) { s, model ->
        val tlv = ndefTlv(message.toByteArray())
        if (tlv.size > model.userBytes) {
            return@withTag WriteResult.Failure(
                "No cabe: el contenido ocupa ${message.byteArrayLength} bytes y el tag admite ~${model.userBytes - 5}."
            )
        }
        if (!s.auth(pwd)) return@withTag WriteResult.WrongPassword
        val padded = tlv.copyOf((tlv.size + 3) / 4 * 4)
        val pages = padded.size / 4
        // Primero se marca el NDEF como vacío y la página 4 se escribe al final:
        // si se corta a medias, el tag queda vacío en vez de con datos corruptos.
        s.write(FIRST_USER_PAGE, byteArrayOf(0x03, 0x00, 0xFE.toByte(), 0x00))
        for (i in 1 until pages) s.write(FIRST_USER_PAGE + i, padded.copyOfRange(i * 4, i * 4 + 4))
        s.write(FIRST_USER_PAGE, padded.copyOfRange(0, 4))
        WriteResult.Success("Se grabaron ${message.byteArrayLength} bytes.", usedPassword = true)
    }

    private fun ndefTlv(ndef: ByteArray): ByteArray = ByteArrayOutputStream().apply {
        write(0x03)
        if (ndef.size < 0xFF) {
            write(ndef.size)
        } else {
            write(0xFF); write(ndef.size shr 8); write(ndef.size and 0xFF)
        }
        write(ndef)
        write(0xFE)
    }.toByteArray()

    private inline fun withTag(tag: Tag, block: (Session, Model) -> WriteResult): WriteResult = try {
        Session(tag).use { s ->
            val model = s.model()
            if (model == null || !model.hasConfig) {
                WriteResult.Failure("Este tag no admite contraseña. Funciona con NTAG213/215/216 y Ultralight EV1.")
            } else {
                block(s, model)
            }
        }
    } catch (e: TagLostException) {
        WriteResult.Failure("Se perdió el contacto con el tag. Mantenlo quieto e intenta de nuevo.", tagLost = true)
    } catch (e: SecurityException) {
        WriteResult.Failure("Se perdió el contacto con el tag. Aléjalo y vuelve a acercarlo.", tagLost = true)
    } catch (e: IOException) {
        WriteResult.Failure("Error de comunicación con el tag: ${e.message ?: "sin detalle"}", tagLost = true)
    }

    /** Conexión NfcA con los comandos que se usan aquí. */
    private class Session(private val tag: Tag) : AutoCloseable {
        private var nfcA: NfcA = NfcA.get(tag) ?: throw IOException("El tag no es NFC‑A")
        private var model: Model? = null

        init {
            nfcA.connect()
            nfcA.timeout = 500
        }

        fun model(): Model? {
            model?.let { return it }
            val v = try { nfcA.transceive(byteArrayOf(0x60)) } catch (e: IOException) { reconnect(); return null }
            return identify(v).also { model = it }
        }

        /** PWD_AUTH (0x1B). Tras un rechazo el tag queda inactivo, por eso se reconecta. */
        fun auth(pwd: ByteArray): Boolean {
            val ok = try {
                nfcA.transceive(byteArrayOf(0x1B) + pwd).size == 2 // respuesta = PACK
            } catch (e: TagLostException) {
                throw e
            } catch (e: IOException) {
                false
            }
            if (!ok) reconnect()
            return ok
        }

        fun read(page: Int): ByteArray {
            val r = nfcA.transceive(byteArrayOf(0x30, page.toByte()))
            if (r.size < 16) throw IOException("El tag rechazó la lectura de la página $page")
            return r
        }

        fun readOrNull(page: Int): ByteArray? = try {
            read(page)
        } catch (e: TagLostException) {
            throw e
        } catch (e: IOException) {
            reconnect(); null
        }

        /** WRITE (0xA2): una página de 4 bytes. El tag responde ACK (0x0A) o NAK. */
        fun write(page: Int, data: ByteArray) {
            val r = nfcA.transceive(byteArrayOf(0xA2.toByte(), page.toByte()) + data)
            if (r.size == 1 && (r[0].u() and 0x0F) != 0x0A) {
                throw IOException("El tag rechazó la escritura de la página $page (¿protegida?)")
            }
        }

        private fun reconnect() {
            runCatching { nfcA.close() }
            nfcA = NfcA.get(tag)
            nfcA.connect()
            nfcA.timeout = 500
        }

        override fun close() {
            runCatching { nfcA.close() }
        }
    }
}

/** Cómo escribe el usuario la contraseña. El chip siempre usa 4 bytes. */
enum class PasswordMode(val label: String) {
    TEXT("Texto / PIN"),
    HEX("Hex (8 caracteres)");

    /** Convierte lo escrito a los 4 bytes del chip, o null si no es válido. */
    fun toBytes(input: String): ByteArray? = when (this) {
        TEXT -> input.takeIf { it.length >= 4 }?.let {
            // El texto se pasa por SHA‑256 y se usan los primeros 4 bytes.
            MessageDigest.getInstance("SHA-256").digest(it.toByteArray(Charsets.UTF_8)).copyOf(4)
        }
        HEX -> input.trim().takeIf { it.length == 8 && it.all { c -> c.isDigit() || c.lowercaseChar() in 'a'..'f' } }
            ?.hexToBytes()
    }

    fun error(input: String): String? = when {
        input.isEmpty() -> null
        toBytes(input) != null -> null
        this == TEXT -> "Mínimo 4 caracteres"
        else -> "Deben ser exactamente 8 caracteres hex (0‑9, A‑F)"
    }
}
