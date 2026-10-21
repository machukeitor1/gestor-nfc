package cl.machukeitor.gestornfc.nfc

import android.nfc.FormatException
import android.nfc.NdefMessage
import android.nfc.Tag
import android.nfc.TagLostException
import android.nfc.tech.Ndef
import android.nfc.tech.NdefFormatable
import java.io.IOException

sealed interface WriteResult {
    /** [usedPassword]: el tag estaba protegido y hubo que autenticarse para escribir. */
    data class Success(val message: String, val usedPassword: Boolean = false) : WriteResult
    /** [tagLost]: se cortó la comunicación; basta con volver a acercar el tag. */
    data class Failure(val message: String, val tagLost: Boolean = false) : WriteResult
    /** El tag está protegido y no se entregó contraseña. */
    data object NeedsPassword : WriteResult
    data object WrongPassword : WriteResult
}

/** Escribe mensajes NDEF. Debe llamarse fuera del hilo principal. */
object TagWriter {

    /**
     * Si el tag tiene contraseña de escritura se usa [password] (o se devuelve [WriteResult.NeedsPassword]);
     * si no, se escribe de la forma normal de Android.
     */
    fun write(tag: Tag, message: NdefMessage, password: ByteArray?): WriteResult {
        val protection = try {
            Ntag.probe(tag)
        } catch (e: Exception) {
            null // no es NTAG o se perdió el tag: la escritura normal dará el error que corresponda
        }
        if (protection?.writeProtected == true) {
            return if (password == null) WriteResult.NeedsPassword else Ntag.writeNdef(tag, message, password)
        }
        return writeStandard(tag, message)
    }

    /** Bloqueo permanente: el tag queda de solo lectura para siempre. */
    fun makeReadOnly(tag: Tag): WriteResult {
        val protection = try { Ntag.probe(tag) } catch (e: Exception) { null }
        if (protection?.writeProtected == true) {
            return WriteResult.Failure("Este tag tiene contraseña. Quítala primero en \"Proteger\" y luego bloquéalo.")
        }
        val ndef = Ndef.get(tag)
            ?: return WriteResult.Failure("El tag no tiene formato NDEF. Escríbele algo antes de bloquearlo.")
        return try {
            ndef.use {
                it.connect()
                when {
                    !it.isWritable -> WriteResult.Failure("Este tag ya está bloqueado.")
                    !it.canMakeReadOnly() -> WriteResult.Failure("Este tipo de tag no se puede bloquear.")
                    it.makeReadOnly() -> WriteResult.Success("Tag bloqueado permanentemente. Ya no se podrá modificar.")
                    else -> WriteResult.Failure("El tag no aceptó el bloqueo.")
                }
            }
        } catch (e: TagLostException) {
            WriteResult.Failure("Se perdió el contacto con el tag. Mantenlo quieto e intenta de nuevo.", tagLost = true)
        } catch (e: SecurityException) {
            WriteResult.Failure("Se perdió el contacto con el tag. Aléjalo y vuelve a acercarlo.", tagLost = true)
        } catch (e: IOException) {
            WriteResult.Failure("No se pudo bloquear: ${e.message ?: "error de comunicación"}", tagLost = true)
        }
    }

    private fun writeStandard(tag: Tag, message: NdefMessage): WriteResult {
        val size = message.byteArrayLength
        return try {
            val ndef = Ndef.get(tag)
            if (ndef != null) {
                ndef.use {
                    it.connect()
                    when {
                        !it.isWritable -> WriteResult.Failure("El tag está bloqueado (solo lectura). No se puede escribir.")
                        size > it.maxSize -> WriteResult.Failure(
                            "No cabe: el contenido ocupa $size bytes y el tag admite ${it.maxSize}."
                        )
                        else -> {
                            it.writeNdefMessage(message)
                            WriteResult.Success("Se grabaron $size bytes de ${it.maxSize} disponibles.")
                        }
                    }
                }
            } else {
                val formatable = NdefFormatable.get(tag)
                    ?: return WriteResult.Failure("Este tag no admite NDEF, no se puede escribir con esta app.")
                formatable.use {
                    it.connect()
                    it.format(message)
                }
                WriteResult.Success("Se grabaron $size bytes.")
            }
        } catch (e: TagLostException) {
            WriteResult.Failure("Se perdió el contacto con el tag. Mantén el teléfono quieto sobre él e intenta de nuevo.", tagLost = true)
        } catch (e: SecurityException) {
            // "Tag is out of date": el tag se alejó y volvió a acercarse durante la escritura.
            WriteResult.Failure("Se perdió el contacto con el tag. Aléjalo y vuelve a acercarlo.", tagLost = true)
        } catch (e: FormatException) {
            WriteResult.Failure("El contenido no es un mensaje NDEF válido.")
        } catch (e: IOException) {
            WriteResult.Failure("No se pudo escribir: ${e.message ?: "error de comunicación"}", tagLost = true)
        }
    }
}
