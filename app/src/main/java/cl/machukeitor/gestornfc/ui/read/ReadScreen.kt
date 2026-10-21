package cl.machukeitor.gestornfc.ui.read

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.remember
import cl.machukeitor.gestornfc.ui.write.NameDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cl.machukeitor.gestornfc.nfc.InfoSection
import cl.machukeitor.gestornfc.nfc.NdefInfo
import cl.machukeitor.gestornfc.nfc.NdefRecordInfo
import cl.machukeitor.gestornfc.nfc.TagInfo
import cl.machukeitor.gestornfc.ui.EmptyState
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReadScreen(
    info: TagInfo?,
    readCount: Int,
    reading: Boolean,
    error: String?,
    onClear: () -> Unit,
    onAbout: () -> Unit,
    onCopy: (NdefInfo) -> Unit,
    onSaveTemplate: (NdefInfo, String) -> Unit,
    nfcBanner: @Composable () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Leer tag") },
                actions = {
                    if (info != null || error != null) {
                        TextButton(onClick = onClear) {
                            Icon(Icons.Filled.Refresh, null, Modifier.size(18.dp))
                            Text("Leer otro", Modifier.padding(start = 6.dp))
                        }
                    }
                    IconButton(onClick = onAbout) { Icon(Icons.Filled.Info, "Acerca de") }
                },
            )
        },
        contentWindowInsets = WindowInsets(0),
    ) { padding ->
        Column(Modifier.padding(padding)) {
            nfcBanner()
            if (reading) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Text(
                    "Leyendo… no muevas el tag",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
            error?.let {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                ) {
                    Text(it, Modifier.padding(16.dp))
                }
            }
            if (info == null) {
                EmptyState(
                    icon = { Icon(Icons.Filled.Nfc, null, Modifier.size(96.dp), tint = MaterialTheme.colorScheme.primary) },
                    title = "Acerca un tag",
                    subtitle = "Apóyalo en la parte trasera del teléfono para ver toda su información.",
                )
            } else {
                TagDetails(info, readCount, onCopy, onSaveTemplate)
            }
        }
    }
}

@Composable
private fun TagDetails(
    info: TagInfo,
    readCount: Int,
    onCopy: (NdefInfo) -> Unit,
    onSaveTemplate: (NdefInfo, String) -> Unit,
) {
    LazyColumn(
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { SummaryCard(info, readCount) }
        info.ndef?.let { ndef -> item { NdefCard(ndef, onCopy, onSaveTemplate) } }
            ?: item { InfoCard(InfoSection("NDEF", listOf("Estado" to "Este tag no tiene formato NDEF (o el teléfono no lo reconoce)"))) }
        items(info.sections) { InfoCard(it) }
        if (info.memory.isNotEmpty() || info.memoryNote != null) item { MemoryCard(info) }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun SummaryCard(info: TagInfo, readCount: Int) {
    val clipboard = LocalClipboardManager.current
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(info.chip, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(info.manufacturer, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (info.writeProtected) {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("🔒 Protegido con contraseña de escritura", fontWeight = FontWeight.Bold)
                        Text(
                            if (info.passwordSaved) {
                                "La contraseña está guardada en este teléfono: por eso aquí puedes escribirlo " +
                                    "sin que te la pida. Otros teléfonos no podrán modificarlo."
                            } else {
                                "Para modificarlo se pedirá la contraseña."
                            },
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("UID", style = MaterialTheme.typography.labelMedium)
                    Text(info.uid, fontFamily = FontFamily.Monospace)
                }
                IconButton(onClick = { clipboard.setText(AnnotatedString(info.uid.replace(":", ""))) }) {
                    Icon(Icons.Filled.ContentCopy, "Copiar UID")
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                info.technologies.forEach { AssistChip(onClick = {}, label = { Text(it) }) }
            }
            Text(
                "Lectura #$readCount · " + DateFormat.getTimeInstance().format(Date(info.scannedAt)),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun NdefCard(ndef: NdefInfo, onCopy: (NdefInfo) -> Unit, onSaveTemplate: (NdefInfo, String) -> Unit) {
    var askName by remember { mutableStateOf(false) }
    if (askName) {
        NameDialog(
            title = "Guardar como plantilla",
            initial = ndef.summary.substringAfter(": ").take(40),
            onDismiss = { askName = false },
            onConfirm = { onSaveTemplate(ndef, it); askName = false },
        )
    }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Contenido (NDEF)", style = MaterialTheme.typography.titleMedium)
            KeyValue("Tipo", ndef.type)
            KeyValue("Escritura", if (ndef.writable) "Permitida" else "Bloqueado (solo lectura)")
            KeyValue("Se puede bloquear", if (ndef.canMakeReadOnly) "Sí" else "No")
            Text("Espacio usado: ${ndef.usedSize} de ${ndef.maxSize} bytes", style = MaterialTheme.typography.bodySmall)
            LinearProgressIndicator(
                progress = { if (ndef.maxSize > 0) ndef.usedSize.toFloat() / ndef.maxSize else 0f },
                modifier = Modifier.fillMaxWidth(),
            )
            if (ndef.records.isEmpty()) {
                Text("El tag está vacío.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            ndef.records.forEachIndexed { i, record ->
                HorizontalDivider()
                RecordView(i + 1, record)
            }
            if (ndef.records.any { it.kind != "Vacío" } && ndef.raw != null) {
                HorizontalDivider()
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { onCopy(ndef) }, modifier = Modifier.weight(1f)) {
                        Text("Copiar a otro tag")
                    }
                    OutlinedButton(onClick = { askName = true }, modifier = Modifier.weight(1f)) {
                        Text("Guardar plantilla")
                    }
                }
            }
        }
    }
}

@Composable
private fun RecordView(index: Int, record: NdefRecordInfo) {
    val uriHandler = LocalUriHandler.current
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            "Registro $index · ${record.kind} · ${record.sizeBytes} B",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        SelectionContainer { Text(record.value, style = MaterialTheme.typography.bodyLarge) }
        record.details.forEach { (k, v) -> KeyValue(k, v) }
        record.openUri?.let { uri ->
            TextButton(onClick = { runCatching { uriHandler.openUri(uri) } }) {
                Icon(Icons.AutoMirrored.Filled.OpenInNew, null, Modifier.size(18.dp))
                Text("Abrir", Modifier.padding(start = 6.dp))
            }
        }
    }
}

@Composable
private fun InfoCard(section: InfoSection) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(section.title, style = MaterialTheme.typography.titleMedium)
            section.items.forEach { (k, v) -> KeyValue(k, v) }
        }
    }
}

@Composable
private fun MemoryCard(info: TagInfo) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Memoria (${info.memory.size} páginas)", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                IconButton(onClick = { expanded = !expanded }) {
                    Icon(if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, if (expanded) "Ocultar" else "Mostrar")
                }
            }
            info.memoryNote?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
            if (expanded) {
                info.memory.forEach { row ->
                    Row {
                        Text(
                            "%03d  %s  %s".format(row.page, row.hex, row.ascii),
                            fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.bodySmall,
                        )
                        row.note?.let {
                            Text(
                                "  $it",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun KeyValue(key: String, value: String) {
    Row {
        Text(
            key,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.4f),
        )
        SelectionContainer(Modifier.weight(0.6f)) {
            Text(value, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
