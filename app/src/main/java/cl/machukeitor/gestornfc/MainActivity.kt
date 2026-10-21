package cl.machukeitor.gestornfc

import android.nfc.NfcAdapter
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import cl.machukeitor.gestornfc.ui.AppRoot
import cl.machukeitor.gestornfc.ui.theme.GestorNfcTheme

private const val SPLASH_MIN_MS = 800L

class MainActivity : ComponentActivity() {

    private val viewModel: NfcViewModel by viewModels()
    private var nfcAdapter: NfcAdapter? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        // La app carga tan rápido que el splash apenas se vería: se mantiene un mínimo.
        val start = SystemClock.uptimeMillis()
        installSplashScreen().setKeepOnScreenCondition { SystemClock.uptimeMillis() - start < SPLASH_MIN_MS }
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        nfcAdapter = NfcAdapter.getDefaultAdapter(this)
        setContent {
            GestorNfcTheme {
                AppRoot(viewModel = viewModel, nfcAdapter = nfcAdapter)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Modo lector: mientras la app está abierta, todos los tags llegan aquí
        // (y Android no abre otras apps ni el navegador al acercar uno).
        nfcAdapter?.enableReaderMode(
            this,
            { tag ->
                vibrate()
                viewModel.onTagDiscovered(tag)
            },
            NfcAdapter.FLAG_READER_NFC_A or
                NfcAdapter.FLAG_READER_NFC_B or
                NfcAdapter.FLAG_READER_NFC_F or
                NfcAdapter.FLAG_READER_NFC_V,
            Bundle().apply { putInt(NfcAdapter.EXTRA_READER_PRESENCE_CHECK_DELAY, 250) },
        )
    }

    override fun onPause() {
        super.onPause()
        nfcAdapter?.disableReaderMode(this)
    }

    /** Vibración corta para confirmar que se detectó un tag. */
    private fun vibrate() {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Vibrator::class.java)
        }
        vibrator?.vibrate(VibrationEffect.createOneShot(60, VibrationEffect.DEFAULT_AMPLITUDE))
    }
}
