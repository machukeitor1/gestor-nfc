package cl.machukeitor.gestornfc

import android.app.Application
import android.nfc.Tag
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import cl.machukeitor.gestornfc.data.HistoryStore
import cl.machukeitor.gestornfc.data.HistoryType
import cl.machukeitor.gestornfc.data.Template
import cl.machukeitor.gestornfc.data.TemplateStore
import cl.machukeitor.gestornfc.nfc.Ntag
import cl.machukeitor.gestornfc.nfc.PasswordStore
import cl.machukeitor.gestornfc.nfc.TagInfo
import cl.machukeitor.gestornfc.nfc.TagInspector
import cl.machukeitor.gestornfc.nfc.TagWriter
import cl.machukeitor.gestornfc.nfc.WritePayload
import cl.machukeitor.gestornfc.nfc.WriteResult
import cl.machukeitor.gestornfc.nfc.toHex
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Operación que se ejecuta al acercar un tag. */
sealed interface TagJob {
    val summary: String

    /**
     * [password]: contraseña ingresada para un tag protegido; [remember]: guardarla para ese tag.
     * [series]: tras cada escritura se sigue esperando el siguiente tag.
     * [protectWith]: después de escribir, se protege el tag con esta contraseña.
     */
    data class Write(
        val payload: WritePayload,
        val password: ByteArray? = null,
        val remember: Boolean = false,
        val series: Boolean = false,
        val protectWith: ByteArray? = null,
    ) : TagJob {
        override val summary get() = payload.summary
    }

    data class SetPassword(val password: ByteArray, val remember: Boolean) : TagJob {
        override val summary get() = "Poner contraseña de escritura"
    }

    data class ChangePassword(val old: ByteArray, val new: ByteArray, val remember: Boolean) : TagJob {
        override val summary get() = "Cambiar contraseña"
    }

    data class RemovePassword(val password: ByteArray) : TagJob {
        override val summary get() = "Quitar contraseña"
    }

    data object LockPermanent : TagJob {
        override val summary get() = "Bloqueo permanente (solo lectura)"
    }
}

sealed interface WriteState {
    data object Idle : WriteState
    data class Waiting(val job: TagJob) : WriteState
    /** El tag tiene contraseña: hay que pedirla. [wrong] = el intento anterior falló. */
    data class NeedsPassword(val job: TagJob.Write, val wrong: Boolean) : WriteState
    data class Done(val job: TagJob, val result: WriteResult) : WriteState
}

data class UiState(
    val lastRead: TagInfo? = null,
    /** Aumenta con cada lectura; la UI lo usa para saltar a la pestaña "Leer". */
    val readCount: Int = 0,
    val reading: Boolean = false,
    val readError: String? = null,
    val write: WriteState = WriteState.Idle,
    /** Escritura en serie: tags escritos y último error (si el último intento falló). */
    val seriesCount: Int = 0,
    val seriesError: String? = null,
)

class NfcViewModel(app: Application) : AndroidViewModel(app) {

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private val passwords = PasswordStore(app)
    private val historyStore = HistoryStore(app)
    private val templateStore = TemplateStore(app)

    val history = historyStore.items
    val templates = templateStore.items

    /** Último tag detectado: permite reintentar con contraseña sin tener que alejarlo y acercarlo. */
    @Volatile private var lastTag: Tag? = null

    /**
     * Llamado desde el hilo del lector NFC (no el principal), por eso aquí se puede hacer I/O.
     * Nunca debe lanzar excepciones: si una escapa, Android deja de entregar tags hasta reabrir la app.
     */
    fun onTagDiscovered(tag: Tag) {
        Log.d(TAG, "Tag detectado: UID ${tag.id?.toHex() ?: "?"}, modo ${_state.value.write.javaClass.simpleName}")
        lastTag = tag
        when (val write = _state.value.write) {
            is WriteState.Waiting -> runJob(tag, write.job)
            is WriteState.NeedsPassword -> Unit // se espera a que el usuario escriba la contraseña
            else -> read(tag)
        }
    }

    private fun read(tag: Tag) {
        _state.update { it.copy(reading = true, readError = null) }
        try {
            val info = TagInspector.inspect(tag).let {
                it.copy(passwordSaved = it.writeProtected && passwords.get(tag.id?.toHex("").orEmpty()) != null)
            }
            _state.update { it.copy(lastRead = info, readCount = it.readCount + 1, reading = false) }
            historyStore.add(
                HistoryType.READ,
                uid = info.uid,
                chip = info.chip,
                summary = info.ndef?.summary ?: "Sin formato NDEF",
                ndef = info.ndef?.raw,
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error al leer", e)
            _state.update {
                it.copy(
                    reading = false,
                    readCount = it.readCount + 1,
                    readError = "No se pudo leer el tag. Mantenlo quieto hasta que termine y vuelve a intentar.",
                )
            }
        }
    }

    private fun runJob(tag: Tag, job: TagJob) {
        val uid = tag.id?.toHex("").orEmpty()
        val result = try {
            when (job) {
                is TagJob.Write -> write(tag, uid, job)
                is TagJob.SetPassword -> Ntag.setPassword(tag, job.password)
                is TagJob.ChangePassword -> Ntag.changePassword(tag, job.old, job.new)
                is TagJob.RemovePassword -> Ntag.removePassword(tag, job.password)
                TagJob.LockPermanent -> lockPermanent(tag, uid)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error en ${job.summary}", e)
            WriteResult.Failure("Error inesperado: ${e.message ?: e.javaClass.simpleName}")
        }

        if (result is WriteResult.Success) {
            rememberPassword(uid, job)
            val pretty = tag.id?.toHex().orEmpty()
            when (job) {
                is TagJob.Write -> historyStore.add(HistoryType.WRITE, pretty, "", job.summary, job.payload.toMessage().toByteArray())
                else -> historyStore.add(HistoryType.PROTECT, pretty, "", job.summary, null)
            }
        }

        val next: WriteState = when {
            result is WriteResult.NeedsPassword && job is TagJob.Write -> WriteState.NeedsPassword(job, wrong = false)
            result is WriteResult.WrongPassword && job is TagJob.Write -> {
                // Si la contraseña recordada ya no sirve (la cambiaron con otra app), se olvida.
                if (job.password == null) passwords.remove(uid)
                WriteState.NeedsPassword(job.copy(password = null), wrong = true)
            }
            result is WriteResult.WrongPassword ->
                WriteState.Done(job, WriteResult.Failure("La contraseña actual no es correcta."))
            // En serie se sigue esperando el siguiente tag; los errores se muestran en el mismo diálogo.
            job is TagJob.Write && job.series -> {
                _state.update {
                    if (result is WriteResult.Success) it.copy(seriesCount = it.seriesCount + 1, seriesError = null)
                    else it.copy(seriesError = (result as? WriteResult.Failure)?.message)
                }
                WriteState.Waiting(job.copy(password = null))
            }
            else -> WriteState.Done(job, result)
        }
        _state.update { it.copy(write = next) }
    }

    /** Escribe y, si se pidió, protege el tag con contraseña en el mismo toque. */
    private fun write(tag: Tag, uid: String, job: TagJob.Write): WriteResult {
        val stored = if (job.password == null) passwords.get(uid) else null
        val result = TagWriter.write(tag, job.payload.toMessage(), job.password ?: stored).let {
            // Que quede claro que el tag sigue protegido aunque no se haya pedido la contraseña.
            if (it is WriteResult.Success && it.usedPassword) {
                it.copy(
                    message = it.message + if (stored != null) {
                        "\n🔒 El tag está protegido: se usó la contraseña guardada en este teléfono."
                    } else {
                        "\n🔒 El tag sigue protegido con su contraseña."
                    }
                )
            } else it
        }
        val protect = job.protectWith ?: return result
        if (result !is WriteResult.Success) return result
        val protection = try {
            Ntag.probe(tag)
        } catch (e: Exception) {
            return WriteResult.Success(result.message + "\nNo se pudo proteger: se perdió el contacto con el tag.")
        }
        return when {
            protection == null -> WriteResult.Success(result.message + "\nEste tag no admite contraseña: quedó sin proteger.")
            protection.writeProtected -> result // ya estaba protegido
            else -> when (val p = Ntag.setPassword(tag, protect)) {
                is WriteResult.Success -> {
                    passwords.put(uid, protect)
                    WriteResult.Success(result.message + "\nTag protegido con contraseña.")
                }
                else -> WriteResult.Success(
                    result.message + "\nNo se pudo proteger: " + ((p as? WriteResult.Failure)?.message ?: "error")
                )
            }
        }
    }

    /**
     * Bloqueo permanente. Si el tag tiene contraseña y está guardada en el teléfono,
     * primero se quita (el chip no deja bloquear con la protección activa).
     */
    private fun lockPermanent(tag: Tag, uid: String): WriteResult {
        val protection = try { Ntag.probe(tag) } catch (e: Exception) { null }
        var removed = false
        if (protection?.writeProtected == true) {
            val pwd = passwords.get(uid) ?: return WriteResult.Failure(
                "Este tag tiene contraseña de escritura y no está guardada en este teléfono. " +
                    "Quítala primero en Proteger → Quitar contraseña, y luego bloquéalo."
            )
            when (val r = Ntag.removePassword(tag, pwd)) {
                is WriteResult.Success -> removed = true
                WriteResult.WrongPassword -> return WriteResult.Failure(
                    "La contraseña guardada para este tag ya no es válida. Quítala en Proteger con la contraseña correcta."
                )
                else -> return r
            }
        }
        val result = TagWriter.makeReadOnly(tag)
        return if (removed && result is WriteResult.Success) {
            result.copy(message = "Se quitó la contraseña y el tag quedó bloqueado permanentemente (solo lectura).")
        } else result
    }

    /** Olvida todas las contraseñas guardadas; devuelve cuántas había. */
    fun forgetPasswords(): Int = passwords.clear()

    private fun rememberPassword(uid: String, job: TagJob) {
        when (job) {
            is TagJob.Write -> if (job.remember && job.password != null) passwords.put(uid, job.password)
            is TagJob.SetPassword -> if (job.remember) passwords.put(uid, job.password)
            is TagJob.ChangePassword -> if (job.remember) passwords.put(uid, job.new) else passwords.remove(uid)
            is TagJob.RemovePassword -> passwords.remove(uid)
            TagJob.LockPermanent -> passwords.remove(uid) // ya no tiene contraseña
        }
    }

    /** El usuario ingresó la contraseña: se reintenta con el mismo tag si sigue cerca. */
    fun submitPassword(password: ByteArray, remember: Boolean) {
        val needs = _state.value.write as? WriteState.NeedsPassword ?: return
        val job = needs.job.copy(password = password, remember = remember)
        _state.update { it.copy(write = WriteState.Waiting(job)) }
        val tag = lastTag ?: return
        viewModelScope.launch(Dispatchers.IO) {
            runJob(tag, job)
            // Si el tag ya no estaba, se vuelve a esperar: al acercarlo se escribe con la contraseña.
            val done = _state.value.write as? WriteState.Done
            if ((done?.result as? WriteResult.Failure)?.tagLost == true) {
                _state.update { it.copy(write = WriteState.Waiting(job)) }
            }
        }
    }

    /** Limpia la pantalla "Leer" para esperar el siguiente tag. */
    fun clearRead() = _state.update { it.copy(lastRead = null, readError = null) }

    fun startWrite(payload: WritePayload) = startJob(TagJob.Write(payload))

    fun startJob(job: TagJob) = _state.update {
        it.copy(write = WriteState.Waiting(job), seriesCount = 0, seriesError = null)
    }

    fun dismissWrite() = _state.update { it.copy(write = WriteState.Idle) }

    fun retryWrite() {
        val done = _state.value.write as? WriteState.Done ?: return
        // Al repetir una escritura no se reutiliza la contraseña escrita a mano (puede ser otro tag).
        startJob(if (done.job is TagJob.Write) done.job.copy(password = null, remember = false) else done.job)
    }

    // --- Historial ---

    fun editHistory(id: Long, name: String, note: String) = historyStore.edit(id, name, note)

    fun deleteHistory(id: Long) = historyStore.delete(id)

    fun clearHistory() = historyStore.clear()

    // --- Plantillas ---

    fun saveTemplate(name: String, payload: WritePayload) =
        templateStore.add(name, payload.summary, payload.toMessage().toByteArray())

    fun saveTemplate(name: String, summary: String, ndef: ByteArray) = templateStore.add(name, summary, ndef)

    fun renameTemplate(id: Long, name: String) = templateStore.rename(id, name)

    fun deleteTemplate(id: Long) = templateStore.delete(id)

    fun writeTemplate(template: Template, series: Boolean, protectWith: ByteArray?) = startJob(
        TagJob.Write(
            payload = WritePayload.Raw(template.ndef, "${template.name} · ${template.summary}"),
            series = series,
            protectWith = protectWith,
        )
    )

    private companion object {
        const val TAG = "GestorNFC"
    }
}
