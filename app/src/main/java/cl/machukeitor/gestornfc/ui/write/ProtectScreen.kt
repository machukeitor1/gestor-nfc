package cl.machukeitor.gestornfc.ui.write

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.TextButton
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cl.machukeitor.gestornfc.TagJob
import cl.machukeitor.gestornfc.nfc.PasswordMode

private enum class ProtectAction(val label: String) {
    SET("Poner contraseña"),
    CHANGE("Cambiar contraseña"),
    REMOVE("Quitar contraseña"),
    LOCK("Bloqueo permanente"),
}

private const val LOCK_WORD = "BLOQUEAR"

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ProtectScreen(onBack: () -> Unit, onStart: (TagJob) -> Unit, onForgetPasswords: () -> Int) {
    val context = LocalContext.current
    var confirmForget by remember { mutableStateOf(false) }
    if (confirmForget) {
        AlertDialog(
            onDismissRequest = { confirmForget = false },
            title = { Text("¿Olvidar contraseñas guardadas?") },
            text = {
                Text(
                    "La app dejará de usar sola las contraseñas que guardaste. Los tags siguen protegidos: " +
                        "para modificarlos se te pedirá la contraseña. Sirve para comprobar que la protección funciona."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val n = onForgetPasswords()
                    Toast.makeText(context, "Se olvidaron $n contraseñas", Toast.LENGTH_SHORT).show()
                    confirmForget = false
                }) { Text("Olvidar") }
            },
            dismissButton = { TextButton(onClick = { confirmForget = false }) { Text("Cancelar") } },
        )
    }
    var action by rememberSaveable { mutableStateOf(ProtectAction.SET) }
    var mode by rememberSaveable { mutableStateOf(PasswordMode.TEXT) }
    var current by rememberSaveable { mutableStateOf("") }
    var new by rememberSaveable { mutableStateOf("") }
    var confirm by rememberSaveable { mutableStateOf("") }
    var keep by rememberSaveable { mutableStateOf(true) }
    var understood by rememberSaveable { mutableStateOf(false) }
    var lockWord by rememberSaveable { mutableStateOf("") }

    val currentBytes = mode.toBytes(current)
    val newBytes = mode.toBytes(new)
    val confirmError = if (confirm.isNotEmpty() && confirm != new) "No coincide" else null
    val newOk = newBytes != null && confirm == new

    val job: TagJob? = when (action) {
        ProtectAction.SET -> if (newOk) TagJob.SetPassword(newBytes!!, keep) else null
        ProtectAction.CHANGE -> if (currentBytes != null && newOk) TagJob.ChangePassword(currentBytes, newBytes!!, keep) else null
        ProtectAction.REMOVE -> currentBytes?.let { TagJob.RemovePassword(it) }
        // Doble confirmación: casilla + escribir la palabra.
        ProtectAction.LOCK -> if (understood && lockWord.trim().uppercase() == LOCK_WORD) TagJob.LockPermanent else null
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Proteger") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver") } },
            )
        },
        contentWindowInsets = WindowInsets(0),
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ProtectAction.entries.forEach {
                    FilterChip(selected = action == it, onClick = { action = it }, label = { Text(it.label) })
                }
            }

            if (action == ProtectAction.LOCK) {
                LockSection(understood, { understood = it }, lockWord, { lockWord = it })
            } else {
                Text(
                    "Protege la escritura: cualquiera puede leer el tag, pero solo quien sepa la contraseña puede modificarlo.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text("Tipo de contraseña", style = MaterialTheme.typography.labelLarge)
                PasswordModeSelector(mode) { mode = it; current = ""; new = ""; confirm = "" }

                if (action != ProtectAction.SET) {
                    PasswordField(current, { current = it }, "Contraseña actual", mode)
                }
                if (action != ProtectAction.REMOVE) {
                    PasswordField(new, { new = it }, "Nueva contraseña", mode)
                    PasswordField(confirm, { confirm = it }, "Repetir nueva contraseña", mode, extraError = confirmError)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = keep, onCheckedChange = { keep = it })
                        Text("Recordarla en este teléfono para ese tag")
                    }
                }

                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Importante", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "• Si olvidas la contraseña no podrás volver a modificar el tag.\n" +
                                "• Funciona con NTAG213/215/216 y MIFARE Ultralight EV1.\n" +
                                "• Con \"Texto\", otras apps (ej. NFC Tools) no reconocerán la misma contraseña; " +
                                "con \"Hex\" sí.\n" +
                                "• Es una protección básica (4 bytes): evita modificaciones casuales, no ataques dirigidos.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }

            Button(
                onClick = { job?.let(onStart) },
                enabled = job != null,
                modifier = Modifier.fillMaxWidth(),
                colors = if (action == ProtectAction.LOCK) {
                    ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                } else ButtonDefaults.buttonColors(),
            ) {
                Icon(Icons.Filled.Lock, null)
                Text(action.label, Modifier.padding(start = 8.dp))
            }
            TextButton(onClick = { confirmForget = true }, modifier = Modifier.fillMaxWidth()) {
                Text("Olvidar contraseñas guardadas en este teléfono")
            }
        }
    }
}

@Composable
private fun LockSection(
    understood: Boolean,
    onUnderstood: (Boolean) -> Unit,
    lockWord: String,
    onLockWord: (String) -> Unit,
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("⚠ Esto es irreversible", style = MaterialTheme.typography.titleSmall)
            Text(
                "El tag queda de solo lectura para siempre: nadie, ni tú, podrá volver a escribirlo, " +
                    "borrarlo ni ponerle contraseña. Verifica antes que el contenido sea el correcto.",
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                "Si el tag tiene contraseña y está guardada en este teléfono, se quita sola antes de bloquear. " +
                    "Si no está guardada, primero quítala con \"Quitar contraseña\".",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = understood, onCheckedChange = onUnderstood)
        Text("Entiendo que no se puede deshacer")
    }
    FormField(lockWord, onLockWord, "Escribe $LOCK_WORD para confirmar")
}
