package cl.machukeitor.gestornfc.ui.write

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import cl.machukeitor.gestornfc.i18n.tr
import cl.machukeitor.gestornfc.nfc.AutoAction
import cl.machukeitor.gestornfc.ui.S
import cl.machukeitor.gestornfc.nfc.AutomationRunner
import cl.machukeitor.gestornfc.nfc.WritePayload

/** Acciones que se pueden agregar, con su valor inicial (función: los textos siguen el idioma actual). */
private fun actionTemplates(): List<Pair<String, () -> AutoAction>> = listOf(
    tr("Linterna", "Flashlight", "Lanterna") to { AutoAction.Torch(AutoAction.Toggle.TOGGLE) },
    tr("Modo de sonido", "Sound mode", "Modo de som") to { AutoAction.RingerMode(AutoAction.Ringer.VIBRATE) },
    tr("Volumen", "Volume", "Volume") to { AutoAction.Volume(AutoAction.Stream.MEDIA, 50) },
    tr("Brillo", "Brightness", "Brilho") to { AutoAction.Brightness(50) },
    tr("Temporizador", "Timer", "Temporizador") to { AutoAction.Timer(5, "") },
    tr("Abrir app", "Open app", "Abrir app") to { AutoAction.OpenApp("") },
    tr("Abrir enlace", "Open link", "Abrir link") to { AutoAction.OpenUrl("https://") },
    tr("Panel de Wi‑Fi", "Wi‑Fi panel", "Painel de Wi‑Fi") to { AutoAction.WifiPanel },
    tr("Ajustes de Bluetooth", "Bluetooth settings", "Configurações de Bluetooth") to { AutoAction.BluetoothSettings },
)

private fun AutoAction.isValid() = when (this) {
    is AutoAction.OpenApp -> AutoAction.isPackageName(packageName)
    is AutoAction.OpenUrl -> AutoAction.isWebUrl(url)
    is AutoAction.Timer -> minutes in 1..AutoAction.MAX_TIMER_MINUTES
    else -> true
}

@Composable
fun AutomationForm(onBack: () -> Unit, onWrite: (WritePayload) -> Unit) {
    val context = LocalContext.current
    var name by rememberSaveable { mutableStateOf("") }
    // Se guarda como JSON para sobrevivir a la rotación de pantalla.
    var json by rememberSaveable { mutableStateOf("[]") }
    val actions = AutoAction.listFromJson(json)
    fun update(list: List<AutoAction>) { json = AutoAction.listToJson(list) }

    val payload = if (actions.isNotEmpty() && actions.all { it.isValid() }) {
        WritePayload.Automation(name, actions)
    } else null

    FormScaffold(
        title = WriteType.AUTOMATION.title,
        onBack = onBack,
        payload = payload,
        onWrite = onWrite,
        extraPreview = {
            OutlinedButton(
                onClick = {
                    val results = AutomationRunner(context).run(actions)
                    Toast.makeText(context, results.joinToString("\n"), Toast.LENGTH_LONG).show()
                },
                enabled = payload != null,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Filled.PlayArrow, null)
                Text(tr("Probar en este teléfono", "Test on this phone", "Testar neste celular"), Modifier.padding(start = 8.dp))
            }
        },
    ) {
        Text(
            tr(
                "Al acercar el tag, el teléfono ejecuta estas acciones en orden. Necesita Gestor NFC instalado; si no lo tiene, abre su ficha en Play Store.",
                "When the tag is tapped, the phone runs these actions in order. It needs Gestor NFC installed; if not, its Play Store page opens.",
                "Ao aproximar a tag, o celular executa estas ações em ordem. Precisa do Gestor NFC instalado; se não tiver, abre a página na Play Store.",
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FormField(
            name, { name = it }, tr("Nombre (opcional)", "Name (optional)", "Nome (opcional)"),
            placeholder = tr("Modo cine", "Movie mode", "Modo cinema"),
        )

        actions.forEachIndexed { i, action ->
            ActionCard(
                index = i,
                action = action,
                onChange = { new -> update(actions.toMutableList().also { it[i] = new }) },
                onRemove = { update(actions.toMutableList().also { it.removeAt(i) }) },
            )
        }

        AddActionButton { update(actions + it) }

        Text(
            tr(
                "Android no permite que las apps enciendan el Wi‑Fi o el Bluetooth directamente: esas acciones abren el panel para que lo hagas con un toque. Sonido y brillo piden un permiso la primera vez.",
                "Android doesn't let apps turn Wi‑Fi or Bluetooth on directly: those actions open the panel so you can do it with one tap. Sound and brightness ask for a permission the first time.",
                "O Android não permite que apps liguem o Wi‑Fi ou o Bluetooth diretamente: essas ações abrem o painel para você fazer com um toque. Som e brilho pedem uma permissão na primeira vez.",
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun AddActionButton(onAdd: (AutoAction) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Filled.Add, null)
            Text(tr("Agregar acción", "Add action", "Adicionar ação"), Modifier.padding(start = 8.dp))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            actionTemplates().forEach { (label, create) ->
                DropdownMenuItem(text = { Text(label) }, onClick = { onAdd(create()); open = false })
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ActionCard(index: Int, action: AutoAction, onChange: (AutoAction) -> Unit, onRemove: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${index + 1}. ${action.label}",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f).semantics { heading() },
                )
                IconButton(onClick = onRemove) { Icon(Icons.Filled.Close, tr("Quitar acción", "Remove action", "Remover ação") + " ${index + 1}") }
            }
            when (action) {
                is AutoAction.Torch -> FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AutoAction.Toggle.entries.forEach {
                        FilterChip(action.mode == it, { onChange(AutoAction.Torch(it)) }, { Text(it.label) })
                    }
                }
                is AutoAction.RingerMode -> FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AutoAction.Ringer.entries.forEach {
                        FilterChip(action.mode == it, { onChange(AutoAction.RingerMode(it)) }, { Text(it.label) })
                    }
                }
                is AutoAction.Volume -> {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AutoAction.Stream.entries.forEach {
                            FilterChip(action.stream == it, { onChange(action.copy(stream = it)) }, { Text(it.label) })
                        }
                    }
                    PercentSlider(action.percent) { onChange(action.copy(percent = it)) }
                }
                is AutoAction.Brightness -> PercentSlider(action.percent) { onChange(AutoAction.Brightness(it)) }
                is AutoAction.Timer -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FormField(
                        action.minutes.toString(),
                        { v -> onChange(action.copy(minutes = v.filter(Char::isDigit).take(4).toIntOrNull() ?: 0)) },
                        tr("Minutos", "Minutes", "Minutos"),
                        keyboardType = KeyboardType.Number,
                        modifier = Modifier.weight(0.4f),
                        error = if (action.minutes !in 1..1440) tr("1 a 1440", "1 to 1440", "1 a 1440") else null,
                    )
                    FormField(
                        action.message, { onChange(action.copy(message = it)) }, tr("Etiqueta", "Label", "Rótulo"),
                        modifier = Modifier.weight(0.6f),
                    )
                }
                is AutoAction.OpenApp -> FormField(
                    action.packageName, { onChange(AutoAction.OpenApp(it.trim())) }, tr("Paquete de la app", "App package", "Pacote do app"),
                    placeholder = "com.spotify.music", keyboardType = KeyboardType.Uri,
                    error = if (action.packageName.isNotEmpty() && !action.isValid()) S.packageInvalid else null,
                )
                is AutoAction.OpenUrl -> FormField(
                    action.url, { onChange(AutoAction.OpenUrl(it.trim())) }, tr("Enlace", "Link", "Link"),
                    keyboardType = KeyboardType.Uri,
                    error = if (!action.isValid()) tr("Escribe un enlace completo (https://…)", "Enter a full link (https://…)", "Digite um link completo (https://…)") else null,
                )
                AutoAction.WifiPanel, AutoAction.BluetoothSettings -> Unit
            }
        }
    }
}

@Composable
private fun PercentSlider(value: Int, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Slider(
            value = value.toFloat(),
            onValueChange = { onChange(it.toInt()) },
            valueRange = 0f..100f,
            steps = 19,
            modifier = Modifier.weight(1f),
        )
        Text("$value%", Modifier.padding(start = 8.dp))
    }
}
