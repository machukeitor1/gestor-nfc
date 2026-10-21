package cl.machukeitor.gestornfc.ui.write

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import cl.machukeitor.gestornfc.nfc.SocialNetwork
import cl.machukeitor.gestornfc.nfc.WritePayload

@Composable
fun NetworkBadge(net: SocialNetwork, size: Int = 40) {
    Box(
        Modifier.size(size.dp).clip(CircleShape).background(Color(net.color)),
        contentAlignment = Alignment.Center,
    ) {
        Text(net.initial, color = Color.White, fontWeight = FontWeight.Bold)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SocialListScreen(onBack: () -> Unit, onSelect: (SocialNetwork) -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Redes sociales") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver") } },
            )
        },
        contentWindowInsets = WindowInsets(0),
    ) { padding ->
        LazyColumn(Modifier.padding(padding), contentPadding = PaddingValues(vertical = 8.dp)) {
            items(SocialNetwork.entries) { net ->
                ListItem(
                    headlineContent = { Text(net.displayName) },
                    leadingContent = { NetworkBadge(net) },
                    trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null) },
                    modifier = Modifier.clickable { onSelect(net) },
                )
            }
        }
    }
}

@Composable
private fun CountryPicker(selected: Int, enabled: Boolean, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier) {
        OutlinedButton(
            onClick = { expanded = true },
            enabled = enabled,
            contentPadding = PaddingValues(horizontal = 12.dp),
            modifier = Modifier.fillMaxHeight(),
        ) {
            val country = COUNTRIES.getOrNull(selected)
            Text(if (country != null) "${country.flag} +${country.code}" else "🌐 Otro")
            Icon(Icons.Filled.ArrowDropDown, "Elegir país")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            COUNTRIES.forEachIndexed { i, c ->
                DropdownMenuItem(
                    text = { Text("${c.flag}  ${c.name}  +${c.code}") },
                    onClick = { onSelect(i); expanded = false },
                )
            }
            DropdownMenuItem(
                text = { Text("🌐  Otro (escribir código)") },
                onClick = { onSelect(-1); expanded = false },
            )
        }
    }
}

@Composable
fun SocialFormScreen(net: SocialNetwork, onBack: () -> Unit, onWrite: (WritePayload) -> Unit) {
    var value by rememberSaveable { mutableStateOf("") }
    /** Índice en [COUNTRIES]; -1 = "Otro" (código escrito a mano). */
    var countryIndex by rememberSaveable { mutableIntStateOf(0) }
    var customCode by rememberSaveable { mutableStateOf("") }
    var message by rememberSaveable { mutableStateOf("") }
    val uriHandler = LocalUriHandler.current

    // Un número que empieza con + o 00 ya trae su código de país: se ignora el selector.
    val international = value.trimStart().let { it.startsWith("+") || it.startsWith("00") }
    val normalized = when (net) {
        SocialNetwork.WHATSAPP -> {
            val digits = value.filter { it.isDigit() }
            val code = if (countryIndex >= 0) COUNTRIES[countryIndex].code else customCode
            val full = when {
                international -> digits.removePrefix("00")
                // Si pegaron el número completo con el mismo código, no se repite.
                digits.startsWith(code) && digits.length >= code.length + 8 -> digits
                // El 0 inicial (prefijo nacional en algunos países) no va en wa.me.
                else -> code + digits.trimStart('0')
            }
            if (code.isEmpty() && !international) null else net.normalize(full)
        }
        else -> net.normalize(value)
    }
    val url = normalized?.let { net.buildUrl(it, message) }
    val payload = url?.let { WritePayload.Link(net.displayName, it) }

    FormScaffold(
        title = net.displayName,
        onBack = onBack,
        payload = payload,
        onWrite = onWrite,
        extraPreview = {
            url?.let {
                Text("Se grabará: $it", style = MaterialTheme.typography.bodySmall)
                OutlinedButton(onClick = { runCatching { uriHandler.openUri(it) } }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.AutoMirrored.Filled.OpenInNew, null, Modifier.size(18.dp))
                    Text("Probar enlace", Modifier.padding(start = 8.dp))
                }
            }
        },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            NetworkBadge(net, 48)
            Text(
                if (net == SocialNetwork.SPOTIFY) "Pega el enlace de tu perfil, playlist o canción."
                else "Escribe tu usuario o pega el enlace completo; la app lo limpia.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        if (net == SocialNetwork.WHATSAPP) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CountryPicker(
                    selected = countryIndex,
                    enabled = !international,
                    onSelect = { countryIndex = it },
                    modifier = Modifier.padding(top = 8.dp).height(56.dp),
                )
                FormField(
                    value, { value = it }, net.fieldLabel,
                    placeholder = net.placeholder, keyboardType = KeyboardType.Phone, modifier = Modifier.weight(1f),
                )
            }
            if (countryIndex < 0 && !international) {
                FormField(
                    customCode, { customCode = it.filter(Char::isDigit).take(4) }, "Código de país",
                    prefix = "+", keyboardType = KeyboardType.Number, modifier = Modifier.width(160.dp),
                )
            }
            if (international) {
                Text(
                    "El número ya trae código de país, se usará tal cual.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            FormField(message, { message = it }, "Mensaje inicial (opcional)", singleLine = false, minLines = 2)
        } else {
            FormField(
                value, { value = it }, net.fieldLabel,
                placeholder = net.placeholder,
                keyboardType = if (net == SocialNetwork.SPOTIFY) KeyboardType.Uri else KeyboardType.Text,
                prefix = if (net == SocialNetwork.SPOTIFY || net == SocialNetwork.DISCORD) null else "@",
                error = if (value.isNotBlank() && normalized == null) "No es válido" else null,
            )
        }
    }
}
