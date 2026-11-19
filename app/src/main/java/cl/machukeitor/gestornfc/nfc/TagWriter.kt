package cl.machukeitor.gestornfc.nfc

import android.nfc.FormatException
import android.nfc.NdefMessage
import android.nfc.Tag
import android.nfc.TagLostException
import android.nfc.tech.Ndef
import android.nfc.tech.NdefFormatable
import cl.machukeitor.gestornfc.i18n.tr
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
            return WriteResult.Failure(
                tr(
                    "Este tag tiene contraseña. Quítala primero en \"Proteger\" y luego bloquéalo.",
                    "This tag has a password. Remove it first in \"Protect\" and then lock it.",
                    "Esta tag tem senha. Remova-a primeiro em \"Proteger\" e depois bloqueie.",
                )
            )
        }
        val ndef = Ndef.get(tag) ?: return WriteResult.Failure(
            tr(
                "El tag no tiene formato NDEF. Escríbele algo antes de bloquearlo.",
                "The tag isn't NDEF formatted. Write something to it before locking it.",
                "A tag não está formatada como NDEF. Grave algo antes de bloqueá-la.",
            )
        )
        return try {
            ndef.use {
                it.connect()
                when {
                    !it.isWritable -> WriteResult.Failure(
                        tr("Este tag ya está bloqueado.", "This tag is already locked.", "Esta tag já está bloqueada.")
                    )
                    !it.canMakeReadOnly() -> WriteResult.Failure(
                        tr("Este tipo de tag no se puede bloquear.", "This type of tag can't be locked.", "Este tipo de tag não pode ser bloqueado.")
                    )
                    it.makeReadOnly() -> WriteResult.Success(
                        tr(
                            "Tag bloqueado permanentemente. Ya no se podrá modificar.",
                            "Tag permanently locked. It can no longer be changed.",
                            "Tag bloqueada permanentemente. Não poderá mais ser modificada.",
                        )
                    )
                    else -> WriteResult.Failure(
                        tr("El tag no aceptó el bloqueo.", "The tag didn't accept the lock.", "A tag não aceitou o bloqueio.")
                    )
                }
            }
        } catch (e: TagLostException) {
            WriteResult.Failure(Msg.tagLostHold, tagLost = true)
        } catch (e: SecurityException) {
            WriteResult.Failure(Msg.tagLostAway, tagLost = true)
        } catch (e: IOException) {
            WriteResult.Failure(Msg.commError(e.message), tagLost = true)
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
                        !it.isWritable -> WriteResult.Failure(
                            tr(
                                "El tag está bloqueado (solo lectura). No se puede escribir.",
                                "The tag is locked (read-only). It can't be written.",
                                "A tag está bloqueada (somente leitura). Não é possível gravar.",
                            )
                        )
                        size > it.maxSize -> WriteResult.Failure(Msg.doesNotFit(size, it.maxSize))
                        else -> {
                            it.writeNdefMessage(message)
                            WriteResult.Success(Msg.written(size, it.maxSize))
                        }
                    }
                }
            } else {
                val formatable = NdefFormatable.get(tag) ?: return WriteResult.Failure(
                    tr(
                        "Este tag no admite NDEF, no se puede escribir con esta app.",
                        "This tag doesn't support NDEF; it can't be written with this app.",
                        "Esta tag não suporta NDEF; não pode ser gravada com este app.",
                    )
                )
                formatable.use {
                    it.connect()
                    it.format(message)
                }
                WriteResult.Success(Msg.written(size))
            }
        } catch (e: TagLostException) {
            WriteResult.Failure(Msg.tagLostHold, tagLost = true)
        } catch (e: SecurityException) {
            // "Tag is out of date": el tag se alejó y volvió a acercarse durante la escritura.
            WriteResult.Failure(Msg.tagLostAway, tagLost = true)
        } catch (e: FormatException) {
            WriteResult.Failure(
                tr("El contenido no es un mensaje NDEF válido.", "The content isn't a valid NDEF message.", "O conteúdo não é uma mensagem NDEF válida.")
            )
        } catch (e: IOException) {
            WriteResult.Failure(Msg.commError(e.message), tagLost = true)
        }
    }
}
