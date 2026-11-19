package cl.machukeitor.gestornfc.ui.write

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import cl.machukeitor.gestornfc.i18n.tr
import cl.machukeitor.gestornfc.nfc.WritePayload

/** Hosts de Google que aceptamos como enlace de reseña (o de la ficha de la empresa). */
private val REVIEW_HOSTS = listOf("g.page", "search.google.com", "google.com", "www.google.com", "maps.app.goo.gl", "goo.gl")

/** Un Place ID de Google: empieza por "ChIJ" (lo habitual) y solo tiene letras, números, - y _. */
private val PLACE_ID = Regex("""^[A-Za-z0-9_-]{20,}$""")

/**
 * Convierte lo que pega el usuario en el enlace final:
 * - Place ID → formulario de reseña de Google.
 * - Enlace de Google (p. ej. "g.page/r/…/review" del Perfil de Empresa) → se usa tal cual.
 */
internal fun reviewUrl(input: String): String? {
    val raw = input.trim()
    if (raw.isEmpty()) return null
    if (PLACE_ID.matches(raw)) return "https://search.google.com/local/writereview?placeid=$raw"
    val url = if (raw.startsWith("http")) raw else "https://$raw"
    val host = url.substringAfter("://").substringBefore('/').substringBefore('?').lowercase()
    return url.takeIf { REVIEW_HOSTS.any { h -> host == h || host.endsWith(".$h") } }
}

@Composable
fun ReviewForm(onBack: () -> Unit, onWrite: (WritePayload) -> Unit) {
    var business by rememberSaveable { mutableStateOf("") }
    var input by rememberSaveable { mutableStateOf("") }
    val uriHandler = LocalUriHandler.current

    val url = reviewUrl(input)
    val label = tr("Reseña en Google", "Google review", "Avaliação no Google") +
        if (business.isNotBlank()) " · ${business.trim()}" else ""
    val payload = url?.let { WritePayload.Link(label, it) }

    FormScaffold(
        title = WriteType.REVIEW.title,
        onBack = onBack,
        payload = payload,
        onWrite = onWrite,
        extraPreview = {
            url?.let {
                Text(tr("Se grabará", "Will write", "Será gravado") + ": $it", style = MaterialTheme.typography.bodySmall)
                OutlinedButton(onClick = { runCatching { uriHandler.openUri(it) } }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.AutoMirrored.Filled.OpenInNew, null, Modifier.size(18.dp))
                    Text(tr("Probar enlace", "Test link", "Testar link"), Modifier.padding(start = 8.dp))
                }
            }
        },
    ) {
        Text(
            tr(
                "Al acercar el teléfono al tag, el cliente llega directo a la ventana para dejar estrellas y un comentario. Funciona en Android y iPhone, sin instalar nada.",
                "When customers tap the tag, they land right on the window to leave stars and a comment. Works on Android and iPhone, nothing to install.",
                "Ao aproximar o celular da tag, o cliente vai direto à janela para deixar estrelas e um comentário. Funciona em Android e iPhone, sem instalar nada.",
            ),
            style = MaterialTheme.typography.bodyMedium,
        )
        FormField(
            business, { business = it },
            tr("Nombre de la empresa (opcional)", "Business name (optional)", "Nome da empresa (opcional)"),
        )
        FormField(
            input, { input = it },
            tr("Enlace de reseñas o Place ID", "Review link or Place ID", "Link de avaliações ou Place ID"),
            placeholder = "https://g.page/r/…/review",
            keyboardType = KeyboardType.Uri,
            singleLine = false,
            error = if (input.isNotBlank() && url == null) {
                tr(
                    "Pega un enlace de Google o un Place ID",
                    "Paste a Google link or a Place ID",
                    "Cole um link do Google ou um Place ID",
                )
            } else null,
        )
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(tr("¿Dónde lo consigo?", "Where do I get it?", "Onde consigo?"), style = MaterialTheme.typography.titleSmall)
                Text(
                    tr(
                        "1. Recomendado: en tu Perfil de Empresa de Google (Google Business Profile) toca \"Pedir reseñas\" y copia el enlace (g.page/r/…/review).\n" +
                            "2. O busca tu empresa en el \"Place ID Finder\" de Google y copia el ID (suele empezar con ChIJ).",
                        "1. Recommended: in your Google Business Profile tap \"Ask for reviews\" and copy the link (g.page/r/…/review).\n" +
                            "2. Or find your business in Google's \"Place ID Finder\" and copy the ID (usually starts with ChIJ).",
                        "1. Recomendado: no seu Perfil da Empresa no Google (Google Business Profile) toque em \"Pedir avaliações\" e copie o link (g.page/r/…/review).\n" +
                            "2. Ou procure sua empresa no \"Place ID Finder\" do Google e copie o ID (geralmente começa com ChIJ).",
                    ),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}
