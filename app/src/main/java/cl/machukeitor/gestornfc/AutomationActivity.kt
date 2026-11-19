package cl.machukeitor.gestornfc

import android.app.Activity
import android.content.Intent
import android.nfc.NdefMessage
import android.nfc.NfcAdapter
import android.os.Build
import android.os.Bundle
import android.os.Parcelable
import android.widget.Toast
import cl.machukeitor.gestornfc.i18n.tr
import cl.machukeitor.gestornfc.nfc.AutoAction
import cl.machukeitor.gestornfc.nfc.AutomationRunner

/**
 * Pantalla invisible que Android abre al acercar un tag de automatización con la app cerrada
 * (o en segundo plano): ejecuta las acciones, muestra un resumen y se cierra.
 */
class AutomationActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handle(intent)
        finish()
    }

    private fun handle(intent: Intent) {
        if (intent.action != NfcAdapter.ACTION_NDEF_DISCOVERED) return
        val record = ndefMessages(intent)
            .flatMap { it.records.asList() }
            .firstOrNull { String(it.type, Charsets.US_ASCII).lowercase() == AutoAction.NDEF_TYPE }
            ?: return
        val (name, actions) = runCatching { AutoAction.fromJson(String(record.payload, Charsets.UTF_8)) }
            .getOrElse {
                val msg = tr("automatización no válida", "invalid automation", "automação inválida")
                Toast.makeText(this, "Gestor NFC: $msg", Toast.LENGTH_LONG).show()
                return
            }
        val results = AutomationRunner(this).run(actions)
        val title = name.ifBlank { tr("Automatización", "Automation", "Automação") }
        Toast.makeText(this, "$title\n" + results.joinToString("\n"), Toast.LENGTH_LONG).show()
    }

    private fun ndefMessages(intent: Intent): List<NdefMessage> {
        val raw: Array<out Parcelable>? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES, NdefMessage::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES)
        }
        return raw?.filterIsInstance<NdefMessage>().orEmpty()
    }
}
