package cl.machukeitor.gestornfc.ui.write

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cl.machukeitor.gestornfc.data.Template
import cl.machukeitor.gestornfc.nfc.PasswordMode
import cl.machukeitor.gestornfc.ui.EmptyState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TemplatesScreen(
    templates: List<Template>,
    onBack: () -> Unit,
    onWrite: (Template, series: Boolean, protectWith: ByteArray?) -> Unit,
    onRename: (Long, String) -> Unit,
    onDelete: (Long) -> Unit,
) {
    var seriesFor by remember { mutableStateOf<Template?>(null) }
    var renameFor by remember { mutableStateOf<Template?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Plantillas") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver") } },
            )
        },
        contentWindowInsets = WindowInsets(0),
    ) { padding ->
        if (templates.isEmpty()) {
            Column(Modifier.padding(padding)) {
                EmptyState(
                    icon = { Icon(Icons.Filled.Bookmarks, null, Modifier.size(96.dp), tint = MaterialTheme.colorScheme.primary) },
                    title = "No hay plantillas",
                    subtitle = "En cualquier formulario de Escribir usa \"Guardar como plantilla\", " +
                        "o guárdala desde el Historial.",
                )
            }
            return@Scaffold
        }
        LazyColumn(
            Modifier.padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(templates, key = { it.id }) { t ->
                TemplateCard(
                    template = t,
                    onWrite = { onWrite(t, false, null) },
                    onSeries = { seriesFor = t },
                    onRename = { renameFor = t },
                    onDelete = { onDelete(t.id) },
                )
            }
        }
    }

    seriesFor?.let { t ->
        SeriesDialog(
            template = t,
            onDismiss = { seriesFor = null },
            onStart = { pwd -> onWrite(t, true, pwd); seriesFor = null },
        )
    }
    renameFor?.let { t ->
        NameDialog(
            title = "Renombrar plantilla",
            initial = t.name,
            onDismiss = { renameFor = null },
            onConfirm = { onRename(t.id, it); renameFor = null },
        )
    }
}

@Composable
private fun TemplateCard(
    template: Template,
    onWrite: () -> Unit,
    onSeries: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(template.name, style = MaterialTheme.typography.titleMedium)
                    Text(
                        template.summary,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text("${template.ndef.size} bytes", style = MaterialTheme.typography.labelSmall)
                }
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "Opciones") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("Renombrar") }, onClick = { menu = false; onRename() })
                        DropdownMenuItem(
                            text = { Text("Eliminar", color = MaterialTheme.colorScheme.error) },
                            onClick = { menu = false; onDelete() },
                        )
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onWrite, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Filled.Nfc, null, Modifier.size(18.dp))
                    Text("Escribir", Modifier.padding(start = 6.dp))
                }
                OutlinedButton(onClick = onSeries, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Filled.Repeat, null, Modifier.size(18.dp))
                    Text("En serie", Modifier.padding(start = 6.dp))
                }
            }
        }
    }
}

/** Configura la escritura en serie: opcionalmente proteger cada tag con contraseña. */
@Composable
private fun SeriesDialog(template: Template, onDismiss: () -> Unit, onStart: (ByteArray?) -> Unit) {
    var protect by rememberSaveable { mutableStateOf(false) }
    var mode by rememberSaveable { mutableStateOf(PasswordMode.TEXT) }
    var pwd by rememberSaveable { mutableStateOf("") }
    var confirm by rememberSaveable { mutableStateOf("") }
    val bytes = mode.toBytes(pwd)
    val ready = !protect || (bytes != null && pwd == confirm)

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Filled.Repeat, null) },
        title = { Text("Escribir en serie") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Se grabará \"${template.name}\" en cada tag que acerques, uno tras otro, " +
                        "hasta que presiones Terminar.",
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = protect, onCheckedChange = { protect = it })
                    Text("Proteger cada tag con contraseña")
                }
                if (protect) {
                    PasswordModeSelector(mode) { mode = it; pwd = ""; confirm = "" }
                    PasswordField(pwd, { pwd = it }, "Contraseña", mode)
                    PasswordField(
                        confirm, { confirm = it }, "Repetir contraseña", mode,
                        extraError = if (confirm.isNotEmpty() && confirm != pwd) "No coincide" else null,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onStart(if (protect) bytes else null) }, enabled = ready) { Text("Comenzar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}
