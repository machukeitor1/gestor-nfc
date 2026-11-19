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
import cl.machukeitor.gestornfc.i18n.tr
import cl.machukeitor.gestornfc.nfc.PasswordMode
import cl.machukeitor.gestornfc.ui.S

private enum class ProtectAction {
    SET, CHANGE, REMOVE, LOCK;

    val label: String
        get() = when (this) {
            SET -> tr("Poner contraseña", "Set password", "Definir senha")
            CHANGE -> tr("Cambiar contraseña", "Change password", "Alterar senha")
            REMOVE -> tr("Quitar contraseña", "Remove password", "Remover senha")
            LOCK -> tr("Bloqueo permanente", "Permanent lock", "Bloqueio permanente")
        }
}

private val LOCK_WORD get() = tr("BLOQUEAR", "LOCK", "BLOQUEAR")

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ProtectScreen(onBack: () -> Unit, onStart: (TagJob) -> Unit, onForgetPasswords: () -> Int) {
    val context = LocalContext.current
    var confirmForget by remember { mutableStateOf(false) }
    if (confirmForget) {
        AlertDialog(
            onDismissRequest = { confirmForget = false },
            title = { Text(tr("¿Olvidar contraseñas guardadas?", "Forget saved passwords?", "Esquecer senhas salvas?")) },
            text = {
                Text(
                    tr(
                        "La app dejará de usar sola las contraseñas que guardaste. Los tags siguen protegidos: para modificarlos se te pedirá la contraseña. Sirve para comprobar que la protección funciona.",
                        "The app will stop using your saved passwords on its own. Tags stay protected: you'll be asked for the password to change them. Useful to check that the protection works.",
                        "O app deixará de usar sozinho as senhas que você salvou. As tags continuam protegidas: a senha será pedida para modificá-las. Serve para verificar que a proteção funciona.",
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val n = onForgetPasswords()
                    Toast.makeText(context, tr("Se olvidaron $n contraseñas", "$n passwords forgotten", "$n senhas esquecidas"), Toast.LENGTH_SHORT).show()
                    confirmForget = false
                }) { Text(tr("Olvidar", "Forget", "Esquecer")) }
            },
            dismissButton = { TextButton(onClick = { confirmForget = false }) { Text(S.cancel) } },
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
    val confirmError = if (confirm.isNotEmpty() && confirm != new) tr("No coincide", "Doesn't match", "Não confere") else null
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
                title = { Text(WriteType.PROTECT.title) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, S.back) } },
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
                    tr(
                        "Protege la escritura: cualquiera puede leer el tag, pero solo quien sepa la contraseña puede modificarlo.",
                        "Protects writing: anyone can read the tag, but only someone with the password can change it.",
                        "Protege a escrita: qualquer pessoa pode ler a tag, mas só quem souber a senha pode modificá-la.",
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(tr("Tipo de contraseña", "Password type", "Tipo de senha"), style = MaterialTheme.typography.labelLarge)
                PasswordModeSelector(mode) { mode = it; current = ""; new = ""; confirm = "" }

                if (action != ProtectAction.SET) {
                    PasswordField(current, { current = it }, tr("Contraseña actual", "Current password", "Senha atual"), mode)
                }
                if (action != ProtectAction.REMOVE) {
                    PasswordField(new, { new = it }, tr("Nueva contraseña", "New password", "Nova senha"), mode)
                    PasswordField(confirm, { confirm = it }, tr("Repetir nueva contraseña", "Repeat new password", "Repetir nova senha"), mode, extraError = confirmError)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = keep, onCheckedChange = { keep = it })
                        Text(tr("Recordarla en este teléfono para ese tag", "Remember it on this phone for that tag", "Lembrar neste celular para essa tag"))
                    }
                }

                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(tr("Importante", "Important", "Importante"), style = MaterialTheme.typography.titleSmall)
                        Text(
                            tr(
                                "• Si olvidas la contraseña no podrás volver a modificar el tag.\n• Funciona con NTAG213/215/216 y MIFARE Ultralight EV1.\n• Con \"Texto\", otras apps (ej. NFC Tools) no reconocerán la misma contraseña; con \"Hex\" sí.\n• Es una protección básica (4 bytes): evita modificaciones casuales, no ataques dirigidos.",
                                "• If you forget the password you won't be able to change the tag again.\n• Works with NTAG213/215/216 and MIFARE Ultralight EV1.\n• With \"Text\", other apps (e.g. NFC Tools) won't recognize the same password; with \"Hex\" they will.\n• It's basic protection (4 bytes): it stops casual changes, not targeted attacks.",
                                "• Se você esquecer a senha, não poderá modificar a tag novamente.\n• Funciona com NTAG213/215/216 e MIFARE Ultralight EV1.\n• Com \"Texto\", outros apps (ex. NFC Tools) não reconhecerão a mesma senha; com \"Hex\" sim.\n• É uma proteção básica (4 bytes): evita modificações casuais, não ataques direcionados.",
                            ),
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
                Text(tr("Olvidar contraseñas guardadas en este teléfono", "Forget passwords saved on this phone", "Esquecer senhas salvas neste celular"))
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
            Text("⚠ " + tr("Esto es irreversible", "This can't be undone", "Isto é irreversível"), style = MaterialTheme.typography.titleSmall)
            Text(
                tr(
                    "El tag queda de solo lectura para siempre: nadie, ni tú, podrá volver a escribirlo, borrarlo ni ponerle contraseña. Verifica antes que el contenido sea el correcto.",
                    "The tag becomes read-only forever: nobody, not even you, will be able to write, erase or password-protect it again. Check the content is correct first.",
                    "A tag fica somente leitura para sempre: ninguém, nem você, poderá gravá-la, apagá-la ou colocar senha. Verifique antes se o conteúdo está correto.",
                ),
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                tr(
                    "Si el tag tiene contraseña y está guardada en este teléfono, se quita sola antes de bloquear. Si no está guardada, primero quítala con \"Quitar contraseña\".",
                    "If the tag has a password saved on this phone, it's removed automatically before locking. If it isn't saved, remove it first with \"Remove password\".",
                    "Se a tag tiver senha salva neste celular, ela é removida automaticamente antes de bloquear. Se não estiver salva, remova-a primeiro com \"Remover senha\".",
                ),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = understood, onCheckedChange = onUnderstood)
        Text(tr("Entiendo que no se puede deshacer", "I understand this can't be undone", "Entendo que não pode ser desfeito"))
    }
    FormField(lockWord, onLockWord, tr("Escribe $LOCK_WORD para confirmar", "Type $LOCK_WORD to confirm", "Digite $LOCK_WORD para confirmar"))
}
