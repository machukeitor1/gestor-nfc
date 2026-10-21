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
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Place
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
import androidx.compose.ui.unit.dp

/** [tool] = va en la sección "Herramientas" en vez de "Contenido". */
enum class WriteType(
    val route: String,
    val title: String,
    val description: String,
    val icon: ImageVector,
    val tool: Boolean = false,
) {
    URL("url", "URL / Web", "Abre una página web", Icons.Filled.Link),
    SOCIAL("social", "Redes sociales", "Instagram, WhatsApp, TikTok…", Icons.Filled.Share),
    WIFI("wifi", "Wi‑Fi", "Conectarse a una red", Icons.Filled.Wifi),
    CONTACT("contact", "Contacto", "Guardar una tarjeta de contacto", Icons.Filled.Person),
    TEXT("text", "Texto", "Un texto cualquiera", Icons.AutoMirrored.Filled.ShortText),
    PHONE("phone", "Teléfono", "Llamar a un número", Icons.Filled.Phone),
    SMS("sms", "SMS", "Mensaje de texto prellenado", Icons.AutoMirrored.Filled.Message),
    EMAIL("email", "Email", "Correo prellenado", Icons.Filled.Email),
    LOCATION("location", "Ubicación", "Abrir un punto en el mapa", Icons.Filled.Place),
    APP("app", "Abrir app", "Abre una app instalada (Android)", Icons.Filled.Apps),
    TEMPLATES("templates", "Plantillas", "Contenidos guardados y escritura en serie", Icons.Filled.Bookmarks, tool = true),
    PROTECT("protect", "Proteger", "Contraseña o bloqueo permanente", Icons.Filled.Lock, tool = true),
    ERASE("erase", "Borrar / formatear", "Dejar el tag vacío", Icons.Filled.DeleteSweep, tool = true),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WriteMenuScreen(nfcBanner: @Composable () -> Unit, onSelect: (WriteType) -> Unit, onAbout: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("¿Qué quieres escribir?") },
                actions = { IconButton(onClick = onAbout) { Icon(Icons.Filled.Info, "Acerca de") } },
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
                items(WriteType.entries.filter { !it.tool }) { WriteTypeCard(it, onSelect) }
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        "Herramientas",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                items(WriteType.entries.filter { it.tool }) { WriteTypeCard(it, onSelect) }
            }
        }
    }
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
