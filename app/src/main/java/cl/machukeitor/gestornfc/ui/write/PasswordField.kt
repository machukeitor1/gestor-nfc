package cl.machukeitor.gestornfc.ui.write

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import cl.machukeitor.gestornfc.nfc.PasswordMode

/** Selector Texto/Hex. Afecta a todos los campos de contraseña de la pantalla. */
@Composable
fun PasswordModeSelector(mode: PasswordMode, onChange: (PasswordMode) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PasswordMode.entries.forEach {
            FilterChip(selected = mode == it, onClick = { onChange(it) }, label = { Text(it.label) })
        }
    }
}

/** Campo de contraseña con botón para mostrarla y validación según el modo. */
@Composable
fun PasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    mode: PasswordMode,
    extraError: String? = null,
) {
    var visible by rememberSaveable { mutableStateOf(false) }
    Column {
        FormField(
            value = value,
            onValueChange = { onValueChange(if (mode == PasswordMode.HEX) it.uppercase().take(8) else it) },
            label = label,
            placeholder = if (mode == PasswordMode.HEX) "1A2B3C4D" else null,
            error = mode.error(value) ?: extraError,
            keyboardType = if (mode == PasswordMode.HEX) KeyboardType.Ascii else KeyboardType.Password,
            visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = { visible = !visible }) {
                    Icon(if (visible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, "Mostrar contraseña")
                }
            },
        )
    }
}
