package cl.machukeitor.gestornfc.nfc

import android.app.NotificationManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.provider.AlarmClock
import android.provider.Settings
import android.util.Log
import cl.machukeitor.gestornfc.i18n.tr

/** Ejecuta acciones de automatización. Devuelve una línea de resultado por acción. */
class AutomationRunner(private val context: Context) {

    private val prefs = context.getSharedPreferences("automation_state", Context.MODE_PRIVATE)

    fun run(actions: List<AutoAction>): List<String> = actions.map { action ->
        try {
            runOne(action)
        } catch (e: Exception) {
            Log.e("GestorNFC", "Error en ${action.label}", e)
            "✘ ${action.label}: ${e.message ?: "error"}"
        }
    }

    private fun runOne(action: AutoAction): String = when (action) {
        is AutoAction.Torch -> torch(action.mode)
        is AutoAction.RingerMode -> ringer(action.mode)
        is AutoAction.Volume -> volume(action.stream, action.percent)
        is AutoAction.Brightness -> brightness(action.percent)
        is AutoAction.Timer -> timer(action)
        is AutoAction.OpenApp -> openApp(action.packageName)
        is AutoAction.OpenUrl -> start(Intent(Intent.ACTION_VIEW, Uri.parse(action.url)), action.label)
        AutoAction.WifiPanel -> start(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) Intent(Settings.Panel.ACTION_WIFI)
            else Intent(Settings.ACTION_WIFI_SETTINGS),
            action.label,
        )
        AutoAction.BluetoothSettings -> start(Intent(Settings.ACTION_BLUETOOTH_SETTINGS), action.label)
    }

    private fun torch(mode: AutoAction.Toggle): String {
        val cm = context.getSystemService(CameraManager::class.java)
        val id = cm.cameraIdList.firstOrNull {
            cm.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
        } ?: return "✘ " + tr("Este teléfono no tiene linterna", "This phone has no flashlight", "Este celular não tem lanterna")
        // Android no permite consultar el estado directamente: se recuerda el último que pusimos.
        val on = when (mode) {
            AutoAction.Toggle.ON -> true
            AutoAction.Toggle.OFF -> false
            AutoAction.Toggle.TOGGLE -> !prefs.getBoolean(KEY_TORCH, false)
        }
        cm.setTorchMode(id, on)
        prefs.edit().putBoolean(KEY_TORCH, on).apply()
        return "✔ " + AutoAction.Torch(if (on) AutoAction.Toggle.ON else AutoAction.Toggle.OFF).label
    }

    private fun ringer(mode: AutoAction.Ringer): String {
        val nm = context.getSystemService(NotificationManager::class.java)
        if (!nm.isNotificationPolicyAccessGranted) {
            start(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS), "")
            return "✘ " + tr(
                "Sonido: activa el permiso \"No molestar\" para Gestor NFC y vuelve a acercar el tag",
                "Sound: allow \"Do Not Disturb\" access for Gestor NFC and tap the tag again",
                "Som: ative a permissão \"Não perturbe\" para o Gestor NFC e aproxime a tag de novo",
            )
        }
        val am = context.getSystemService(AudioManager::class.java)
        am.ringerMode = when (mode) {
            AutoAction.Ringer.NORMAL -> AudioManager.RINGER_MODE_NORMAL
            AutoAction.Ringer.VIBRATE -> AudioManager.RINGER_MODE_VIBRATE
            AutoAction.Ringer.SILENT -> AudioManager.RINGER_MODE_SILENT
        }
        return "✔ " + AutoAction.RingerMode(mode).label
    }

    private fun volume(stream: AutoAction.Stream, percent: Int): String {
        val am = context.getSystemService(AudioManager::class.java)
        val type = when (stream) {
            AutoAction.Stream.MEDIA -> AudioManager.STREAM_MUSIC
            AutoAction.Stream.RING -> AudioManager.STREAM_RING
            AutoAction.Stream.ALARM -> AudioManager.STREAM_ALARM
        }
        val max = am.getStreamMaxVolume(type)
        return try {
            am.setStreamVolume(type, max * percent / 100, AudioManager.FLAG_SHOW_UI)
            "✔ " + AutoAction.Volume(stream, percent).label
        } catch (e: SecurityException) {
            // Bajar el timbre a 0 cambia el modo "No molestar": requiere ese permiso.
            start(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS), "")
            "✘ " + tr(
                "Volumen: activa el permiso \"No molestar\" para Gestor NFC",
                "Volume: allow \"Do Not Disturb\" access for Gestor NFC",
                "Volume: ative a permissão \"Não perturbe\" para o Gestor NFC",
            )
        }
    }

    private fun brightness(percent: Int): String {
        if (!Settings.System.canWrite(context)) {
            start(
                Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:${context.packageName}")),
                "",
            )
            return "✘ " + tr(
                "Brillo: permite a Gestor NFC \"modificar ajustes del sistema\" y vuelve a acercar el tag",
                "Brightness: allow Gestor NFC to \"modify system settings\" and tap the tag again",
                "Brilho: permita que o Gestor NFC \"modifique as configurações do sistema\" e aproxime a tag de novo",
            )
        }
        val resolver = context.contentResolver
        Settings.System.putInt(resolver, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
        Settings.System.putInt(resolver, Settings.System.SCREEN_BRIGHTNESS, (percent * 255 / 100).coerceIn(1, 255))
        return "✔ " + AutoAction.Brightness(percent).label
    }

    private fun timer(action: AutoAction.Timer): String {
        val intent = Intent(AlarmClock.ACTION_SET_TIMER)
            .putExtra(AlarmClock.EXTRA_LENGTH, action.minutes * 60)
            .putExtra(AlarmClock.EXTRA_SKIP_UI, true)
            .apply { if (action.message.isNotBlank()) putExtra(AlarmClock.EXTRA_MESSAGE, action.message) }
        return start(intent, action.label)
    }

    private fun openApp(pkg: String): String {
        val launch = context.packageManager.getLaunchIntentForPackage(pkg)
            ?: Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$pkg"))
        return start(launch, AutoAction.OpenApp(pkg).label)
    }

    private fun start(intent: Intent, label: String): String = try {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        "✔ $label"
    } catch (e: ActivityNotFoundException) {
        "✘ $label: " + tr("no hay ninguna app que pueda hacerlo", "no app can handle it", "nenhum app consegue fazer isso")
    }

    private companion object {
        const val KEY_TORCH = "torch_on"
    }
}
