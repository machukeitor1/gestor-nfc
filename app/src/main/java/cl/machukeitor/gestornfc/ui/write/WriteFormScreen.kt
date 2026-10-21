package cl.machukeitor.gestornfc.ui.write

import android.content.Intent
import android.net.Uri
import android.util.Patterns
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import cl.machukeitor.gestornfc.nfc.WifiAuth
import cl.machukeitor.gestornfc.nfc.WifiConfig
import cl.machukeitor.gestornfc.nfc.WritePayload

@Composable
fun WriteFormScreen(type: WriteType, onBack: () -> Unit, onWrite: (WritePayload) -> Unit) {
    when (type) {
        WriteType.URL -> UrlForm(onBack, onWrite)
        WriteType.TEXT -> TextForm(onBack, onWrite)
        WriteType.WIFI -> WifiForm(onBack, onWrite)
        WriteType.CONTACT -> ContactForm(onBack, onWrite)
        WriteType.PHONE -> PhoneForm(onBack, onWrite)
        WriteType.SMS -> SmsForm(onBack, onWrite)
        WriteType.EMAIL -> EmailForm(onBack, onWrite)
        WriteType.LOCATION -> LocationForm(onBack, onWrite)
        WriteType.APP -> AppForm(onBack, onWrite)
        WriteType.ERASE -> EraseForm(onBack, onWrite)
        WriteType.SOCIAL, WriteType.PROTECT, WriteType.TEMPLATES -> Unit // tienen su propia pantalla
    }
}

/** Extrae "lat,lng" de un enlace de Google Maps o de un texto "‑36.6, ‑72.1". */
private fun parseCoordinates(text: String): Pair<Double, Double>? {
    val patterns = listOf(
        Regex("""@(-?\d+\.\d+),(-?\d+\.\d+)"""),
        Regex("""[?&](?:q|query|ll|destination)=(-?\d+\.\d+)(?:,|%2C)\s*(-?\d+\.\d+)"""),
        Regex("""^\s*(-?\d+(?:\.\d+)?)\s*,\s*(-?\d+(?:\.\d+)?)\s*$"""),
    )
    for (p in patterns) {
        val m = p.find(text) ?: continue
        val lat = m.groupValues[1].toDoubleOrNull() ?: continue
        val lng = m.groupValues[2].toDoubleOrNull() ?: continue
        if (lat in -90.0..90.0 && lng in -180.0..180.0) return lat to lng
    }
    return null
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LocationForm(onBack: () -> Unit, onWrite: (WritePayload) -> Unit) {
    var link by rememberSaveable { mutableStateOf("") }
    var lat by rememberSaveable { mutableStateOf("") }
    var lng by rememberSaveable { mutableStateOf("") }
    var label by rememberSaveable { mutableStateOf("") }
    var googleMaps by rememberSaveable { mutableStateOf(true) }

    val latValue = lat.replace(',', '.').toDoubleOrNull()?.takeIf { it in -90.0..90.0 }
    val lngValue = lng.replace(',', '.').toDoubleOrNull()?.takeIf { it in -180.0..180.0 }
    val payload = if (latValue != null && lngValue != null) {
        WritePayload.Location(latValue, lngValue, label, googleMaps)
    } else null
    val linkError = if (link.isNotBlank() && parseCoordinates(link) == null) {
        if (link.contains("goo.gl") || link.contains("maps.app")) "Los enlaces cortos no traen coordenadas: ábrelo y copia el enlace largo, o las coordenadas."
        else "No encontré coordenadas en el enlace"
    } else null

    FormScaffold("Ubicación", onBack, payload, onWrite) {
        FormField(
            link,
            {
                link = it
                parseCoordinates(it)?.let { (a, b) -> lat = a.toString(); lng = b.toString() }
            },
            "Pegar enlace de Google Maps (opcional)",
            keyboardType = KeyboardType.Uri,
            error = linkError,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FormField(
                lat, { lat = it }, "Latitud", placeholder = "-36.6066", keyboardType = KeyboardType.Decimal,
                modifier = Modifier.weight(1f),
                error = if (lat.isNotBlank() && latValue == null) "Entre -90 y 90" else null,
            )
            FormField(
                lng, { lng = it }, "Longitud", placeholder = "-72.1034", keyboardType = KeyboardType.Decimal,
                modifier = Modifier.weight(1f),
                error = if (lng.isNotBlank() && lngValue == null) "Entre -180 y 180" else null,
            )
        }
        FormField(label, { label = it }, "Nombre del lugar (opcional)")
        Text("Formato", style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = googleMaps, onClick = { googleMaps = true }, label = { Text("Google Maps (Android y iPhone)") })
            FilterChip(selected = !googleMaps, onClick = { googleMaps = false }, label = { Text("geo: (solo Android)") })
        }
        Text(
            "Tip: en Google Maps mantén presionado un punto y copia las coordenadas que aparecen arriba.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private class InstalledApp(val name: String, val packageName: String, val icon: ImageBitmap?)

@Composable
private fun AppForm(onBack: () -> Unit, onWrite: (WritePayload) -> Unit) {
    val context = LocalContext.current
    var apps by remember { mutableStateOf<List<InstalledApp>?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    var selectedPkg by rememberSaveable { mutableStateOf("") }
    var selectedName by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(Unit) {
        apps = withContext(Dispatchers.IO) {
            val pm = context.packageManager
            val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            pm.queryIntentActivities(intent, 0)
                .distinctBy { it.activityInfo.packageName }
                .filter { it.activityInfo.packageName != context.packageName }
                .map {
                    InstalledApp(
                        name = it.loadLabel(pm).toString(),
                        packageName = it.activityInfo.packageName,
                        icon = runCatching { it.loadIcon(pm).toBitmap(96, 96).asImageBitmap() }.getOrNull(),
                    )
                }
                .sortedBy { it.name.lowercase() }
        }
    }

    val pkgValid = Regex("""^[a-zA-Z][\w]*(\.[a-zA-Z_][\w]*)+$""").matches(selectedPkg.trim())
    val payload = if (pkgValid) WritePayload.App(selectedPkg.trim(), selectedName) else null

    FormScaffold("Abrir app", onBack, payload, onWrite) {
        Text(
            "Al acercar el tag se abre la app. Si no está instalada, se abre su ficha en Play Store. " +
                "Solo funciona en Android.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FormField(
            selectedPkg, { selectedPkg = it; selectedName = "" }, "Paquete de la app",
            placeholder = "com.whatsapp", keyboardType = KeyboardType.Uri,
            error = if (selectedPkg.isNotBlank() && !pkgValid) "Nombre de paquete no válido" else null,
        )
        FormField(query, { query = it }, "Buscar entre tus apps")
        val list = apps
        if (list == null) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
        } else {
            list.filter { query.isBlank() || it.name.contains(query, ignoreCase = true) }.take(60).forEach { app ->
                ListItem(
                    headlineContent = { Text(app.name) },
                    supportingContent = { Text(app.packageName, style = MaterialTheme.typography.bodySmall) },
                    leadingContent = {
                        app.icon?.let { Image(it, null, Modifier.size(40.dp)) } ?: Icon(Icons.Filled.Apps, null)
                    },
                    trailingContent = {
                        if (app.packageName == selectedPkg) Icon(Icons.Filled.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
                    },
                    modifier = Modifier.clickable { selectedPkg = app.packageName; selectedName = app.name },
                )
            }
        }
    }
}

@Composable
private fun EraseForm(onBack: () -> Unit, onWrite: (WritePayload) -> Unit) {
    FormScaffold(
        title = "Borrar / formatear",
        onBack = onBack,
        payload = WritePayload.Empty,
        onWrite = onWrite,
        allowTemplate = false,
        writeLabel = "Borrar tag",
    ) {
        Text("Deja el tag vacío, listo para volver a usarlo.", style = MaterialTheme.typography.bodyLarge)
        Text(
            "• Si el tag es nuevo y no tiene formato, lo formatea como NDEF.\n" +
                "• Si tiene contraseña, te la pedirá.\n" +
                "• No funciona en tags con bloqueo permanente.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun normalizeUrl(input: String): String? {
    val raw = input.trim()
    if (raw.isEmpty()) return null
    val url = if (raw.contains("://")) raw else "https://$raw"
    return url.takeIf { Patterns.WEB_URL.matcher(it).matches() }
}

private fun isEmail(s: String) = Patterns.EMAIL_ADDRESS.matcher(s.trim()).matches()

private fun cleanPhone(s: String) = s.filter { it.isDigit() || it == '+' }

@Composable
private fun UrlForm(onBack: () -> Unit, onWrite: (WritePayload) -> Unit) {
    var url by rememberSaveable { mutableStateOf("") }
    val normalized = normalizeUrl(url)
    FormScaffold("URL / Web", onBack, normalized?.let { WritePayload.Link("URL", it) }, onWrite) {
        FormField(
            url, { url = it }, "Dirección web",
            placeholder = "www.ejemplo.cl",
            keyboardType = KeyboardType.Uri,
            error = if (url.isNotBlank() && normalized == null) "No parece una URL válida" else null,
        )
        normalized?.let { Text("Se grabará: $it", style = MaterialTheme.typography.bodySmall) }
    }
}

@Composable
private fun TextForm(onBack: () -> Unit, onWrite: (WritePayload) -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    FormScaffold("Texto", onBack, text.takeIf { it.isNotBlank() }?.let { WritePayload.Text(it) }, onWrite) {
        FormField(text, { text = it }, "Texto", singleLine = false, minLines = 4)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WifiForm(onBack: () -> Unit, onWrite: (WritePayload) -> Unit) {
    var ssid by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var auth by rememberSaveable { mutableStateOf(WifiAuth.WPA2) }
    var showPassword by rememberSaveable { mutableStateOf(false) }

    val passwordError = when {
        auth == WifiAuth.OPEN -> null
        password.isEmpty() -> null
        password.length !in 8..63 -> "Debe tener entre 8 y 63 caracteres"
        else -> null
    }
    val valid = ssid.isNotBlank() && (auth == WifiAuth.OPEN || (password.length in 8..63))
    val payload = if (valid) WritePayload.Wifi(WifiConfig(ssid.trim(), password, auth)) else null

    FormScaffold("Wi‑Fi", onBack, payload, onWrite) {
        FormField(ssid, { ssid = it }, "Nombre de la red (SSID)")
        Text("Seguridad", style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            WifiAuth.entries.forEach {
                FilterChip(selected = auth == it, onClick = { auth = it }, label = { Text(it.label) })
            }
        }
        if (auth != WifiAuth.OPEN) {
            FormField(
                password, { password = it }, "Contraseña",
                error = passwordError,
                keyboardType = KeyboardType.Password,
                visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { showPassword = !showPassword }) {
                        Icon(if (showPassword) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, "Mostrar contraseña")
                    }
                },
            )
        }
        Text(
            "Al acercar el tag, Android 10+ e iPhone ofrecen conectarse a la red. " +
                "Ojo: cualquiera que lea el tag podrá ver la contraseña.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ContactForm(onBack: () -> Unit, onWrite: (WritePayload) -> Unit) {
    var first by rememberSaveable { mutableStateOf("") }
    var last by rememberSaveable { mutableStateOf("") }
    var phone by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var company by rememberSaveable { mutableStateOf("") }
    var title by rememberSaveable { mutableStateOf("") }
    var web by rememberSaveable { mutableStateOf("") }
    var address by rememberSaveable { mutableStateOf("") }

    val emailError = if (email.isNotBlank() && !isEmail(email)) "Email no válido" else null
    val valid = first.isNotBlank() && emailError == null
    val payload = if (valid) {
        WritePayload.Contact(first, last, cleanPhone(phone), email, company, title, web, address)
    } else null

    FormScaffold("Contacto", onBack, payload, onWrite) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FormField(first, { first = it }, "Nombre *", modifier = Modifier.weight(1f))
            FormField(last, { last = it }, "Apellido", modifier = Modifier.weight(1f))
        }
        FormField(phone, { phone = it }, "Teléfono", placeholder = "+56 9 1234 5678", keyboardType = KeyboardType.Phone)
        FormField(email, { email = it }, "Email", keyboardType = KeyboardType.Email, error = emailError)
        FormField(company, { company = it }, "Empresa")
        FormField(title, { title = it }, "Cargo")
        FormField(web, { web = it }, "Sitio web", keyboardType = KeyboardType.Uri)
        FormField(address, { address = it }, "Dirección")
        Text(
            "Mientras más campos, más espacio ocupa. Un NTAG213 alcanza para nombre, teléfono y email.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PhoneForm(onBack: () -> Unit, onWrite: (WritePayload) -> Unit) {
    var phone by rememberSaveable { mutableStateOf("") }
    val clean = cleanPhone(phone)
    val payload = if (clean.count { it.isDigit() } >= 6) WritePayload.Link("Teléfono", "tel:$clean") else null
    FormScaffold("Teléfono", onBack, payload, onWrite) {
        FormField(phone, { phone = it }, "Número", placeholder = "+56 9 1234 5678", keyboardType = KeyboardType.Phone)
    }
}

@Composable
private fun SmsForm(onBack: () -> Unit, onWrite: (WritePayload) -> Unit) {
    var phone by rememberSaveable { mutableStateOf("") }
    var message by rememberSaveable { mutableStateOf("") }
    val clean = cleanPhone(phone)
    val payload = if (clean.count { it.isDigit() } >= 6) {
        val body = if (message.isNotBlank()) "?body=${Uri.encode(message)}" else ""
        WritePayload.Link("SMS", "sms:$clean$body")
    } else null
    FormScaffold("SMS", onBack, payload, onWrite) {
        FormField(phone, { phone = it }, "Número", placeholder = "+56 9 1234 5678", keyboardType = KeyboardType.Phone)
        FormField(message, { message = it }, "Mensaje (opcional)", singleLine = false, minLines = 3)
    }
}

@Composable
private fun EmailForm(onBack: () -> Unit, onWrite: (WritePayload) -> Unit) {
    var to by rememberSaveable { mutableStateOf("") }
    var subject by rememberSaveable { mutableStateOf("") }
    var body by rememberSaveable { mutableStateOf("") }
    val payload = if (isEmail(to)) {
        val params = listOfNotNull(
            subject.takeIf { it.isNotBlank() }?.let { "subject=${Uri.encode(it)}" },
            body.takeIf { it.isNotBlank() }?.let { "body=${Uri.encode(it)}" },
        ).joinToString("&")
        WritePayload.Link("Email", "mailto:${to.trim()}" + if (params.isNotEmpty()) "?$params" else "")
    } else null
    FormScaffold("Email", onBack, payload, onWrite) {
        FormField(
            to, { to = it }, "Destinatario", keyboardType = KeyboardType.Email,
            error = if (to.isNotBlank() && !isEmail(to)) "Email no válido" else null,
        )
        FormField(subject, { subject = it }, "Asunto (opcional)")
        FormField(body, { body = it }, "Mensaje (opcional)", singleLine = false, minLines = 3)
    }
}
