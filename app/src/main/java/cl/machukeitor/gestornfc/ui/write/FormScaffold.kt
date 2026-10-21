package cl.machukeitor.gestornfc.ui.write

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import cl.machukeitor.gestornfc.nfc.WritePayload

/** Capacidad NDEF útil aproximada de los chips más comunes. */
private val CHIP_CAPACITIES = listOf("NTAG213" to 137, "NTAG215" to 496, "NTAG216" to 868)

/** Guarda un contenido como plantilla (nombre, contenido). Lo provee AppRoot. */
val LocalSaveTemplate = staticCompositionLocalOf<(String, WritePayload) -> Unit> { { _, _ -> } }

/**
 * Estructura común de todos los formularios: barra con "volver", campos,
 * vista previa del tamaño, botón "Escribir en tag" y "Guardar como plantilla".
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FormScaffold(
    title: String,
    onBack: () -> Unit,
    payload: WritePayload?,
    onWrite: (WritePayload) -> Unit,
    extraPreview: @Composable ColumnScope.() -> Unit = {},
    allowTemplate: Boolean = true,
    writeLabel: String = "Escribir en tag",
    content: @Composable ColumnScope.() -> Unit,
) {
    val saveTemplate = LocalSaveTemplate.current
    var askName by remember { mutableStateOf(false) }
    if (askName && payload != null) {
        NameDialog(
            title = "Guardar como plantilla",
            initial = payload.summary.substringAfter(": ").take(40),
            onDismiss = { askName = false },
            onConfirm = { saveTemplate(it, payload); askName = false },
        )
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver") }
                },
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
            content()
            SizePreview(payload)
            extraPreview()
            Button(
                onClick = { payload?.let(onWrite) },
                enabled = payload != null,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Filled.Nfc, null)
                Text(writeLabel, Modifier.padding(start = 8.dp))
            }
            if (allowTemplate) {
                OutlinedButton(
                    onClick = { askName = true },
                    enabled = payload != null,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Filled.BookmarkAdd, null)
                    Text("Guardar como plantilla", Modifier.padding(start = 8.dp))
                }
            }
        }
    }
}

/** Diálogo para pedir un nombre (plantillas, historial). */
@Composable
fun NameDialog(title: String, initial: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var name by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { FormField(name, { name = it }, "Nombre") },
        confirmButton = {
            TextButton(onClick = { onConfirm(name) }, enabled = name.isNotBlank()) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

@Composable
private fun SizePreview(payload: WritePayload?) {
    val size = payload?.sizeBytes ?: return
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Ocupa $size bytes", style = MaterialTheme.typography.titleSmall)
            Text(
                CHIP_CAPACITIES.joinToString("   ") { (chip, cap) -> "$chip ${if (size <= cap) "✔" else "✘"}" },
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

/** Campo de texto con mensaje de error opcional. */
@Composable
fun FormField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    error: String? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    singleLine: Boolean = true,
    minLines: Int = 1,
    prefix: String? = null,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    trailingIcon: @Composable (() -> Unit)? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = if (placeholder != null) { { Text(placeholder) } } else null,
        prefix = if (prefix != null) { { Text(prefix) } } else null,
        isError = error != null,
        supportingText = if (error != null) { { Text(error) } } else null,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        singleLine = singleLine,
        minLines = minLines,
        visualTransformation = visualTransformation,
        trailingIcon = trailingIcon,
        modifier = modifier.fillMaxWidth(),
    )
}
