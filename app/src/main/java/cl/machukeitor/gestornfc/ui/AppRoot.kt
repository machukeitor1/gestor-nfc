package cl.machukeitor.gestornfc.ui

import android.content.Intent
import android.nfc.NfcAdapter
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Contactless
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import cl.machukeitor.gestornfc.ui.write.RecentWrites
import kotlinx.coroutines.launch
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import cl.machukeitor.gestornfc.NfcViewModel
import cl.machukeitor.gestornfc.TagJob
import cl.machukeitor.gestornfc.WriteState
import cl.machukeitor.gestornfc.i18n.tr
import cl.machukeitor.gestornfc.nfc.PasswordMode
import cl.machukeitor.gestornfc.nfc.SocialNetwork
import cl.machukeitor.gestornfc.nfc.WritePayload
import cl.machukeitor.gestornfc.nfc.WriteResult
import cl.machukeitor.gestornfc.ui.history.HistoryScreen
import cl.machukeitor.gestornfc.ui.read.ReadScreen
import cl.machukeitor.gestornfc.ui.write.LocalSaveTemplate
import cl.machukeitor.gestornfc.ui.write.TemplatesScreen
import cl.machukeitor.gestornfc.ui.write.PasswordField
import cl.machukeitor.gestornfc.ui.write.PasswordModeSelector
import cl.machukeitor.gestornfc.ui.write.ProtectScreen
import cl.machukeitor.gestornfc.ui.write.SocialFormScreen
import cl.machukeitor.gestornfc.ui.write.SocialListScreen
import cl.machukeitor.gestornfc.ui.write.WriteFormScreen
import cl.machukeitor.gestornfc.ui.write.WriteMenuScreen
import cl.machukeitor.gestornfc.ui.write.WriteType

private const val ROUTE_READ = "read"
private const val ROUTE_WRITE = "write"
private const val ROUTE_HISTORY = "history"
private const val ROUTE_ABOUT = "about"

@Composable
fun AppRoot(viewModel: NfcViewModel, nfcAdapter: NfcAdapter?) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val history by viewModel.history.collectAsStateWithLifecycle()
    val templates by viewModel.templates.collectAsStateWithLifecycle()
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val route = backStack?.destination?.route.orEmpty()
    val context = LocalContext.current
    val openAbout = { navController.navigate(ROUTE_ABOUT) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val hint = remember(nfcAdapter) { placementHint(nfcAntennaZone(nfcAdapter)) }
    var recents by remember { mutableStateOf(RecentWrites.load(context)) }

    /** Muestra "[message] · Deshacer"; si el usuario lo toca, se ejecuta [undo]. */
    fun offerUndo(message: String, undo: () -> Unit) {
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            val result = snackbar.showSnackbar(message, actionLabel = tr("Deshacer", "Undo", "Desfazer"), duration = SnackbarDuration.Long)
            if (result == SnackbarResult.ActionPerformed) undo()
        }
    }

    // Al leer un tag (sin escritura pendiente): desde las pestañas principales se salta a "Leer";
    // desde un formulario se avisa con un mensaje, para no perder lo que se estaba escribiendo.
    // handledRead sobrevive a la rotación: al recrear la pantalla no se repite el aviso ni el salto.
    var handledRead by rememberSaveable { mutableIntStateOf(state.readCount) }
    LaunchedEffect(state.readCount) {
        if (state.readCount == handledRead) return@LaunchedEffect
        handledRead = state.readCount
        if (route == ROUTE_READ) return@LaunchedEffect
        if (route in TAB_ROUTES || route.isEmpty()) {
            navController.switchTab(ROUTE_READ)
            return@LaunchedEffect
        }
        val what = state.lastRead?.ndef?.summary ?: state.lastRead?.chip
        val message = if (state.readError != null || what == null) {
            tr("No se pudo leer el tag", "Couldn't read the tag", "Não foi possível ler a tag")
        } else {
            tr("Tag leído", "Tag read", "Tag lida") + ": $what"
        }
        snackbar.currentSnackbarData?.dismiss()
        val result = snackbar.showSnackbar(message, actionLabel = tr("Ver", "View", "Ver"), duration = SnackbarDuration.Long)
        if (result == SnackbarResult.ActionPerformed) navController.switchTab(ROUTE_READ)
    }

    val saveTemplate: (String, WritePayload) -> Unit = { name, payload ->
        viewModel.saveTemplate(name, payload)
        Toast.makeText(context, templateSaved(name), Toast.LENGTH_SHORT).show()
    }

    CompositionLocalProvider(LocalSaveTemplate provides saveTemplate) {
    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = route == ROUTE_READ,
                    onClick = { navController.switchTab(ROUTE_READ) },
                    icon = { Icon(Icons.Filled.Contactless, null) },
                    label = { Text(tr("Leer", "Read", "Ler")) },
                )
                NavigationBarItem(
                    selected = route.startsWith(ROUTE_WRITE),
                    onClick = { navController.switchTab(ROUTE_WRITE) },
                    icon = { Icon(Icons.Filled.Edit, null) },
                    label = { Text(tr("Escribir", "Write", "Gravar")) },
                )
                NavigationBarItem(
                    selected = route == ROUTE_HISTORY,
                    onClick = { navController.switchTab(ROUTE_HISTORY) },
                    icon = { Icon(Icons.Filled.History, null) },
                    label = { Text(tr("Historial", "History", "Histórico")) },
                )
            }
        },
    ) { padding ->
        Column(Modifier.padding(bottom = padding.calculateBottomPadding())) {
            NavHost(navController, startDestination = ROUTE_READ, modifier = Modifier.weight(1f)) {
                composable(ROUTE_READ) {
                    ReadScreen(
                        info = state.lastRead,
                        readCount = state.readCount,
                        reading = state.reading,
                        error = state.readError,
                        onClear = viewModel::clearRead,
                        onAbout = openAbout,
                        onCopy = { ndef ->
                            viewModel.startWrite(WritePayload.Raw(ndef.raw!!, tr("Copia", "Copy", "Cópia") + " · ${ndef.summary}"))
                        },
                        onSaveTemplate = { ndef, name ->
                            viewModel.saveTemplate(name, ndef.summary, ndef.raw!!)
                            Toast.makeText(context, templateSaved(name), Toast.LENGTH_SHORT).show()
                        },
                        nfcBanner = { NfcStatusBanner(nfcAdapter) },
                        placementHint = hint,
                    )
                }
                composable(ROUTE_WRITE) {
                    WriteMenuScreen(
                        nfcBanner = { NfcStatusBanner(nfcAdapter) },
                        recents = recents,
                        onSelect = {
                            recents = RecentWrites.record(context, it)
                            navController.navigate("$ROUTE_WRITE/${it.route}")
                        },
                        onAbout = openAbout,
                    )
                }
                composable(ROUTE_HISTORY) {
                    HistoryScreen(
                        entries = history,
                        onAbout = openAbout,
                        onEdit = viewModel::editHistory,
                        onDelete = { id ->
                            val removed = viewModel.deleteHistory(id)
                            offerUndo(tr("Registro borrado", "Entry deleted", "Registro apagado")) { viewModel.restoreHistory(removed) }
                        },
                        onClear = {
                            val removed = viewModel.clearHistory()
                            offerUndo(tr("Historial borrado", "History cleared", "Histórico apagado")) { viewModel.restoreHistory(removed) }
                        },
                        onWriteAgain = { e -> e.ndef?.let { viewModel.startWrite(WritePayload.Raw(it, e.title)) } },
                        onSaveTemplate = { e, name ->
                            e.ndef?.let { viewModel.saveTemplate(name, e.summary, it) }
                            Toast.makeText(context, templateSaved(name), Toast.LENGTH_SHORT).show()
                        },
                    )
                }
                composable(ROUTE_ABOUT) {
                    AboutScreen(onBack = { navController.popBackStack() })
                }
                composable("$ROUTE_WRITE/${WriteType.TEMPLATES.route}") {
                    TemplatesScreen(
                        templates = templates,
                        onBack = { navController.popBackStack() },
                        onWrite = viewModel::writeTemplate,
                        onRename = viewModel::renameTemplate,
                        onDelete = { id ->
                            val removed = viewModel.deleteTemplate(id)
                            val name = removed.firstOrNull()?.name.orEmpty()
                            offerUndo(tr("Plantilla \"$name\" borrada", "Template \"$name\" deleted", "Modelo \"$name\" apagado")) {
                                viewModel.restoreTemplates(removed)
                            }
                        },
                    )
                }
                composable("$ROUTE_WRITE/social") {
                    SocialListScreen(
                        onBack = { navController.popBackStack() },
                        onSelect = { navController.navigate("$ROUTE_WRITE/social/${it.name}") },
                    )
                }
                composable(
                    "$ROUTE_WRITE/social/{net}",
                    arguments = listOf(navArgument("net") { type = NavType.StringType }),
                ) { entry ->
                    val net = SocialNetwork.valueOf(entry.arguments?.getString("net")!!)
                    SocialFormScreen(net, onBack = { navController.popBackStack() }, onWrite = viewModel::startWrite)
                }
                composable("$ROUTE_WRITE/${WriteType.PROTECT.route}") {
                    ProtectScreen(
                        onBack = { navController.popBackStack() },
                        onStart = viewModel::startJob,
                        onForgetPasswords = viewModel::forgetPasswords,
                    )
                }
                val ownScreen = setOf(WriteType.SOCIAL, WriteType.PROTECT, WriteType.TEMPLATES)
                WriteType.entries.filter { it !in ownScreen }.forEach { type ->
                    composable("$ROUTE_WRITE/${type.route}") {
                        WriteFormScreen(type, onBack = { navController.popBackStack() }, onWrite = viewModel::startWrite)
                    }
                }
            }
        }
    }
    }

    WriteDialog(
        state = state.write,
        seriesCount = state.seriesCount,
        seriesError = state.seriesError,
        onDismiss = viewModel::dismissWrite,
        onRetry = viewModel::retryWrite,
        onPassword = viewModel::submitPassword,
        placementHint = hint,
    )
}

private val TAB_ROUTES = setOf(ROUTE_READ, ROUTE_WRITE, ROUTE_HISTORY, ROUTE_ABOUT)

private val cancelLabel get() = tr("Cancelar", "Cancel", "Cancelar")

private fun templateSaved(name: String) =
    tr("Plantilla \"$name\" guardada", "Template \"$name\" saved", "Modelo \"$name\" salvo")

private fun NavHostController.switchTab(route: String) = navigate(route) {
    popUpTo(graph.findStartDestination().id) { saveState = true }
    launchSingleTop = true
    restoreState = true
}

@Composable
private fun WriteDialog(
    state: WriteState,
    seriesCount: Int,
    seriesError: String?,
    onDismiss: () -> Unit,
    onRetry: () -> Unit,
    onPassword: (ByteArray, Boolean) -> Unit,
    placementHint: String,
) {
    // Vibración distinta para éxito y error (la de "tag detectado" la hace MainActivity).
    val haptic = LocalHapticFeedback.current
    LaunchedEffect(state) {
        if (state is WriteState.Done) {
            haptic.performHapticFeedback(
                if (state.result is WriteResult.Success) HapticFeedbackType.Confirm else HapticFeedbackType.Reject
            )
        }
    }
    LaunchedEffect(seriesCount) { if (seriesCount > 0) haptic.performHapticFeedback(HapticFeedbackType.Confirm) }
    LaunchedEffect(seriesError) { if (seriesError != null) haptic.performHapticFeedback(HapticFeedbackType.Reject) }

    when (state) {
        WriteState.Idle -> Unit
        is WriteState.Waiting -> {
            val series = (state.job as? TagJob.Write)?.series == true
            AlertDialog(
                onDismissRequest = {}, // no se cierra tocando fuera: evita cancelar por accidente
                icon = { Icon(Icons.Filled.Nfc, null, Modifier.size(48.dp)) },
                title = { Text(if (series) tr("Escritura en serie", "Batch writing", "Gravação em série") else tr("Acerca el tag", "Tap the tag", "Aproxime a tag")) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (series) {
                            Text(
                                tr("$seriesCount escritos", "$seriesCount written", "$seriesCount gravadas"),
                                style = MaterialTheme.typography.headlineMedium,
                                color = MaterialTheme.colorScheme.primary,
                                // TalkBack anuncia cada tag escrito sin que haya que mirar la pantalla.
                                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                            )
                            Text(
                                tr(
                                    "Acerca el siguiente tag. Retira cada uno al sentir la vibración.",
                                    "Tap the next tag. Remove each one when you feel the vibration.",
                                    "Aproxime a próxima tag. Retire cada uma ao sentir a vibração.",
                                )
                            )
                            seriesError?.let {
                                Text(tr("Último intento", "Last attempt", "Última tentativa") + ": $it", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                            }
                        } else {
                            Text(
                                placementHint + " " + tr(
                                    "No lo muevas hasta que termine.",
                                    "Don't move it until it finishes.",
                                    "Não a mova até terminar.",
                                )
                            )
                        }
                        Text(state.job.summary, style = MaterialTheme.typography.bodySmall)
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                    }
                },
                confirmButton = {},
                dismissButton = { TextButton(onClick = onDismiss) { Text(if (series) tr("Terminar", "Finish", "Concluir") else cancelLabel) } },
            )
        }
        // key: tras un intento fallido el diálogo se recrea con el campo vacío.
        is WriteState.NeedsPassword -> key(state) { PasswordDialog(state.wrong, onDismiss, onPassword) }
        is WriteState.Done -> {
            val result = state.result
            val ok = result is WriteResult.Success
            val isWrite = state.job is TagJob.Write
            AlertDialog(
                onDismissRequest = onDismiss,
                icon = {
                    if (ok) Icon(Icons.Filled.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
                    else Icon(Icons.Filled.Error, null, tint = MaterialTheme.colorScheme.error)
                },
                title = {
                    Text(
                        when {
                            ok && isWrite -> tr("¡Tag escrito!", "Tag written!", "Tag gravada!")
                            ok -> tr("Listo", "Done", "Pronto")
                            isWrite -> tr("No se pudo escribir", "Couldn't write", "Não foi possível gravar")
                            else -> tr("No se pudo completar", "Couldn't complete", "Não foi possível concluir")
                        }
                    )
                },
                text = {
                    Text(
                        when (result) {
                            is WriteResult.Success -> result.message + if (isWrite) "\n\n${state.job.summary}" else ""
                            is WriteResult.Failure -> result.message
                            WriteResult.NeedsPassword, WriteResult.WrongPassword -> tr("Contraseña incorrecta.", "Wrong password.", "Senha incorreta.")
                        }
                    )
                },
                confirmButton = {
                    TextButton(onClick = onRetry) {
                        Text(
                            when {
                                ok && isWrite -> tr("Escribir otro igual", "Write another one", "Gravar outra igual")
                                ok -> tr("Repetir en otro tag", "Repeat on another tag", "Repetir em outra tag")
                                else -> tr("Reintentar", "Retry", "Tentar de novo")
                            }
                        )
                    }
                },
                dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Cerrar", "Close", "Fechar")) } },
            )
        }
    }
}

@Composable
private fun PasswordDialog(wrong: Boolean, onDismiss: () -> Unit, onPassword: (ByteArray, Boolean) -> Unit) {
    var mode by rememberSaveable { mutableStateOf(PasswordMode.TEXT) }
    var value by rememberSaveable { mutableStateOf("") }
    var keep by rememberSaveable { mutableStateOf(true) }
    val bytes = mode.toBytes(value)
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Filled.Lock, null) },
        title = { Text(tr("Este tag tiene contraseña", "This tag has a password", "Esta tag tem senha")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    tr(
                        "Para modificarlo, ingresa la contraseña de escritura.",
                        "To change it, enter the write password.",
                        "Para modificá-la, digite a senha de escrita.",
                    )
                )
                PasswordModeSelector(mode) { mode = it; value = "" }
                PasswordField(
                    value, { value = it }, tr("Contraseña", "Password", "Senha"), mode,
                    extraError = if (wrong && value.isEmpty()) tr("Contraseña incorrecta, intenta de nuevo", "Wrong password, try again", "Senha incorreta, tente de novo") else null,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = keep, onCheckedChange = { keep = it })
                    Text(tr("Recordar para este tag", "Remember for this tag", "Lembrar para esta tag"))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { bytes?.let { onPassword(it, keep) } }, enabled = bytes != null) {
                Text(tr("Desbloquear y escribir", "Unlock and write", "Desbloquear e gravar"))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(cancelLabel) } },
    )
}

/** Aviso si el teléfono no tiene NFC o si está apagado. */
@Composable
fun NfcStatusBanner(nfcAdapter: NfcAdapter?) {
    val context = LocalContext.current
    var enabled by remember { mutableStateOf(nfcAdapter?.isEnabled == true) }
    LifecycleResumeEffect(nfcAdapter) {
        enabled = nfcAdapter?.isEnabled == true
        onPauseOrDispose {}
    }
    if (nfcAdapter != null && enabled) return

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        modifier = Modifier.fillMaxWidth().padding(16.dp),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Warning, null)
            Box(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(
                    if (nfcAdapter == null) tr("Este teléfono no tiene NFC.", "This phone has no NFC.", "Este celular não tem NFC.")
                    else tr("El NFC está desactivado.", "NFC is turned off.", "O NFC está desativado.")
                )
            }
            if (nfcAdapter != null) {
                TextButton(onClick = { context.startActivity(Intent(Settings.ACTION_NFC_SETTINGS)) }) {
                    Text(tr("Activar", "Turn on", "Ativar"))
                }
            }
        }
    }
}

@Composable
fun EmptyState(icon: @Composable () -> Unit, title: String, subtitle: String) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        icon()
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 16.dp))
        Text(
            subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}
