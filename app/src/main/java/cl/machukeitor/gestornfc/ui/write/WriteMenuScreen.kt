package cl.machukeitor.gestornfc.ui.write

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.automirrored.filled.ShortText
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.RateReview
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import cl.machukeitor.gestornfc.i18n.tr
import cl.machukeitor.gestornfc.ui.S

/** [tool] = va en la sección "Herramientas" en vez de "Contenido". Textos en español, inglés y portugués. */
enum class WriteType(
    val route: String,
    private val titles: Triple<String, String, String>,
    private val descriptions: Triple<String, String, String>,
    val icon: ImageVector,
    val tool: Boolean = false,
) {
    URL("url", Triple("URL / Web", "URL / Web", "URL / Web"),
        Triple("Abre una página web", "Opens a web page", "Abre uma página web"), Icons.Filled.Link),
    SOCIAL("social", Triple("Redes sociales", "Social networks", "Redes sociais"),
        Triple("Instagram, WhatsApp, TikTok…", "Instagram, WhatsApp, TikTok…", "Instagram, WhatsApp, TikTok…"), Icons.Filled.Share),
    REVIEW("review", Triple("Reseña en Google", "Google review", "Avaliação no Google"),
        Triple("Que tus clientes evalúen tu empresa", "Let customers review your business", "Seus clientes avaliam sua empresa"),
        Icons.Filled.RateReview),
    WIFI("wifi", Triple("Wi‑Fi", "Wi‑Fi", "Wi‑Fi"),
        Triple("Conectarse a una red", "Connect to a network", "Conectar a uma rede"), Icons.Filled.Wifi),
    CONTACT("contact", Triple("Contacto", "Contact", "Contato"),
        Triple("Guardar una tarjeta de contacto", "Save a contact card", "Salvar um cartão de contato"), Icons.Filled.Person),
    TEXT("text", Triple("Texto", "Text", "Texto"),
        Triple("Un texto cualquiera", "Any text", "Um texto qualquer"), Icons.AutoMirrored.Filled.ShortText),
    PHONE("phone", Triple("Teléfono", "Phone", "Telefone"),
        Triple("Llamar a un número", "Call a number", "Ligar para um número"), Icons.Filled.Phone),
    SMS("sms", Triple("SMS", "SMS", "SMS"),
        Triple("Mensaje de texto prellenado", "Prefilled text message", "Mensagem de texto pré-preenchida"),
        Icons.AutoMirrored.Filled.Message),
    EMAIL("email", Triple("Email", "Email", "Email"),
        Triple("Correo prellenado", "Prefilled email", "Email pré-preenchido"), Icons.Filled.Email),
    LOCATION("location", Triple("Ubicación", "Location", "Localização"),
        Triple("Abrir un punto en el mapa", "Open a point on the map", "Abrir um ponto no mapa"), Icons.Filled.Place),
    APP("app", Triple("Abrir app", "Open app", "Abrir app"),
        Triple("Abre una app instalada (Android)", "Opens an installed app (Android)", "Abre um app instalado (Android)"),
        Icons.Filled.Apps),
    AUTOMATION("automation", Triple("Automatización", "Automation", "Automação"),
        Triple("Linterna, sonido, brillo, temporizador…", "Flashlight, sound, brightness, timer…", "Lanterna, som, brilho, temporizador…"),
        Icons.Filled.Bolt),
    TEMPLATES("templates", Triple("Plantillas", "Templates", "Modelos"),
        Triple("Contenidos guardados y escritura en serie", "Saved content and batch writing", "Conteúdos salvos e gravação em série"),
        Icons.Filled.Bookmarks, tool = true),
    PROTECT("protect", Triple("Proteger", "Protect", "Proteger"),
        Triple("Contraseña o bloqueo permanente", "Password or permanent lock", "Senha ou bloqueio permanente"),
        Icons.Filled.Lock, tool = true),
    ERASE("erase", Triple("Borrar / formatear", "Erase / format", "Apagar / formatar"),
        Triple("Dejar el tag vacío", "Leave the tag empty", "Deixar a tag vazia"), Icons.Filled.DeleteSweep, tool = true);

    val title get() = tr(titles.first, titles.second, titles.third)
    val description get() = tr(descriptions.first, descriptions.second, descriptions.third)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WriteMenuScreen(
    nfcBanner: @Composable () -> Unit,
    recents: List<WriteType>,
    onSelect: (WriteType) -> Unit,
    onAbout: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(tr("¿Qué quieres escribir?", "What do you want to write?", "O que você quer gravar?")) },
                actions = { IconButton(onClick = onAbout) { Icon(Icons.Filled.Info, S.about) } },
            )
        },
        contentWindowInsets = WindowInsets(0),
    ) { padding ->
        Column(Modifier.padding(padding)) {
            nfcBanner()
            LazyVerticalGrid(
                columns = GridCells.Adaptive(160.dp),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Los usados hace poco, arriba: evita recorrer las 15 tarjetas cada vez.
                if (recents.isNotEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) { SectionTitle(tr("Recientes", "Recent", "Recentes"), first = true) }
                    items(recents, key = { "recent_${it.route}" }) { WriteTypeCard(it, onSelect) }
                    item(span = { GridItemSpan(maxLineSpan) }) { SectionTitle(tr("Contenido", "Content", "Conteúdo")) }
                }
                items(WriteType.entries.filter { !it.tool }, key = { it.route }) { WriteTypeCard(it, onSelect) }
                item(span = { GridItemSpan(maxLineSpan) }) { SectionTitle(tr("Herramientas", "Tools", "Ferramentas")) }
                items(WriteType.entries.filter { it.tool }, key = { it.route }) { WriteTypeCard(it, onSelect) }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String, first: Boolean = false) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(top = if (first) 0.dp else 8.dp).semantics { heading() },
    )
}

@Composable
private fun WriteTypeCard(type: WriteType, onSelect: (WriteType) -> Unit) {
    Card(onClick = { onSelect(type) }, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(type.icon, null, Modifier.size(32.dp), tint = MaterialTheme.colorScheme.primary)
            Text(type.title, style = MaterialTheme.typography.titleMedium)
            Text(
                type.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
