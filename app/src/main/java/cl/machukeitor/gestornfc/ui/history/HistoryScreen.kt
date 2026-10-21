package cl.machukeitor.gestornfc.ui.history

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Contactless
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cl.machukeitor.gestornfc.data.HistoryEntry
import cl.machukeitor.gestornfc.data.HistoryType
import cl.machukeitor.gestornfc.ui.EmptyState
import cl.machukeitor.gestornfc.ui.write.FormField
import cl.machukeitor.gestornfc.ui.write.NameDialog
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    entries: List<HistoryEntry>,
    onAbout: () -> Unit,
    onEdit: (Long, String, String) -> Unit,
    onDelete: (Long) -> Unit,
    onClear: () -> Unit,
    onWriteAgain: (HistoryEntry) -> Unit,
    onSaveTemplate: (HistoryEntry, String) -> Unit,
) {
    var filter by rememberSaveable { mutableStateOf<HistoryType?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    var selectedId by rememberSaveable { mutableStateOf<Long?>(null) }
    var confirmClear by remember { mutableStateOf(false) }

    val visible = entries.filter { e ->
        (filter == null || e.type == filter) &&
            (query.isBlank() || listOf(e.name, e.summary, e.uid, e.note, e.chip).any { it.contains(query, ignoreCase = true) })
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Historial") },
                actions = {
                    if (entries.isNotEmpty()) {
                        IconButton(onClick = { confirmClear = true }) { Icon(Icons.Filled.DeleteForever, "Borrar historial") }
                    }
                    IconButton(onClick = onAbout) { Icon(Icons.Filled.Info, "Acerca de") }
                },
            )
        },
        contentWindowInsets = WindowInsets(0),
    ) { padding ->
        if (entries.isEmpty()) {
            Column(Modifier.padding(padding)) {
                EmptyState(
                    icon = { Icon(Icons.Filled.History, null, Modifier.size(96.dp), tint = MaterialTheme.colorScheme.primary) },
                    title = "Sin registros todavía",
                    subtitle = "Aquí aparecerán los tags que leas, escribas o protejas.",
                )
            }
            return@Scaffold
        }
        LazyColumn(Modifier.padding(padding), contentPadding = PaddingValues(bottom = 16.dp)) {
            item {
                Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    FormField(query, { query = it }, "Buscar (nombre, contenido, UID…)")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = filter == null, onClick = { filter = null }, label = { Text("Todos") })
                        HistoryType.entries.forEach { t ->
                            FilterChip(selected = filter == t, onClick = { filter = t }, label = { Text(t.label) })
                        }
                    }
                }
            }
            items(visible, key = { it.id }) { entry ->
                ListItem(
                    headlineContent = { Text(entry.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    supportingContent = {
                        Text(
                            listOf(entry.type.label, entry.chip.ifBlank { null }, formatDate(entry.time))
                                .filterNotNull().joinToString(" · ") +
                                if (entry.name.isNotBlank()) "\n${entry.summary}" else "",
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    leadingContent = { Icon(iconFor(entry.type), null, tint = MaterialTheme.colorScheme.primary) },
                    modifier = Modifier.clickable { selectedId = entry.id },
                )
                HorizontalDivider()
            }
        }
    }

    entries.firstOrNull { it.id == selectedId }?.let { entry ->
        HistoryDetailDialog(
            entry = entry,
            onDismiss = { selectedId = null },
            onSave = { name, note -> onEdit(entry.id, name, note); selectedId = null },
            onDelete = { onDelete(entry.id); selectedId = null },
            onWriteAgain = { onWriteAgain(entry); selectedId = null },
            onSaveTemplate = { onSaveTemplate(entry, it) },
        )
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("¿Borrar todo el historial?") },
            text = { Text("Se eliminarán ${entries.size} registros. Las plantillas no se tocan.") },
            confirmButton = { TextButton(onClick = { onClear(); confirmClear = false }) { Text("Borrar") } },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancelar") } },
        )
    }
}

private fun iconFor(type: HistoryType) = when (type) {
    HistoryType.READ -> Icons.Filled.Contactless
    HistoryType.WRITE -> Icons.Filled.Edit
    HistoryType.PROTECT -> Icons.Filled.Lock
}

private fun formatDate(time: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(time))

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HistoryDetailDialog(
    entry: HistoryEntry,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit,
    onDelete: () -> Unit,
    onWriteAgain: () -> Unit,
    onSaveTemplate: (String) -> Unit,
) {
    var name by rememberSaveable(entry.id) { mutableStateOf(entry.name) }
    var note by rememberSaveable(entry.id) { mutableStateOf(entry.note) }
    var askTemplate by remember { mutableStateOf(false) }
    // Solo se puede reescribir si hay contenido NDEF con algo más que un registro vacío.
    val canWrite = entry.ndef != null && entry.ndef.size > 3

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(iconFor(entry.type), null) },
        title = { Text(entry.type.label) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SelectionContainer { Text(entry.summary, style = MaterialTheme.typography.bodyLarge) }
                Text(formatDate(entry.time), style = MaterialTheme.typography.bodySmall)
                if (entry.chip.isNotBlank()) Text("Chip: ${entry.chip}", style = MaterialTheme.typography.bodySmall)
                if (entry.uid.isNotBlank()) {
                    Text("UID: ${entry.uid}", style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                }
                FormField(name, { name = it }, "Nombre del tag", placeholder = "Ej: Llavero casa")
                FormField(note, { note = it }, "Nota", singleLine = false, minLines = 2)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (canWrite) {
                        TextButton(onClick = onWriteAgain) { Text("Escribir en otro tag") }
                        TextButton(onClick = { askTemplate = true }) { Text("Plantilla") }
                    }
                    TextButton(onClick = onDelete) { Text("Eliminar", color = MaterialTheme.colorScheme.error) }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(name, note) }) { Text("Guardar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cerrar") } },
    )

    if (askTemplate) {
        NameDialog(
            title = "Guardar como plantilla",
            initial = name.ifBlank { entry.summary.substringAfter(": ").take(40) },
            onDismiss = { askTemplate = false },
            onConfirm = { onSaveTemplate(it); askTemplate = false },
        )
    }
}
