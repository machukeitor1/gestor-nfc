package cl.machukeitor.gestornfc.ui

import android.nfc.NfcAdapter
import android.os.Build
import cl.machukeitor.gestornfc.i18n.tr

/** Zona de la parte trasera del teléfono donde está la antena NFC. */
enum class AntennaZone { TOP, MIDDLE, BOTTOM }

/** [y] = distancia de la antena al borde superior y [height] = alto del teléfono (mismas unidades). */
fun antennaZone(y: Int, height: Int): AntennaZone? = when {
    height <= 0 || y < 0 || y > height -> null
    y * 3 < height -> AntennaZone.TOP
    y * 3 < height * 2 -> AntennaZone.MIDDLE
    else -> AntennaZone.BOTTOM
}

/**
 * Desde Android 14 el sistema informa dónde está la antena NFC de este modelo.
 * Antes de eso (o si el fabricante no lo informa) devuelve null.
 */
fun nfcAntennaZone(adapter: NfcAdapter?): AntennaZone? {
    if (adapter == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return null
    val info = runCatching { adapter.nfcAntennaInfo }.getOrNull() ?: return null
    val antenna = info.availableNfcAntennas.firstOrNull() ?: return null
    return antennaZone(antenna.locationY, info.deviceHeight)
}

/** Frase para indicar dónde apoyar el tag, según la zona de la antena si se conoce. */
fun placementHint(zone: AntennaZone?): String = when (zone) {
    AntennaZone.TOP -> tr(
        "Apoya el tag en la parte trasera del teléfono, arriba.",
        "Hold the tag against the back of the phone, near the top.",
        "Encoste a tag na parte de trás do celular, em cima.",
    )
    AntennaZone.MIDDLE -> tr(
        "Apoya el tag en la parte trasera del teléfono, al centro.",
        "Hold the tag against the back of the phone, in the middle.",
        "Encoste a tag na parte de trás do celular, no centro.",
    )
    AntennaZone.BOTTOM -> tr(
        "Apoya el tag en la parte trasera del teléfono, abajo.",
        "Hold the tag against the back of the phone, near the bottom.",
        "Encoste a tag na parte de trás do celular, embaixo.",
    )
    null -> tr(
        "Apoya el tag en la parte trasera del teléfono (en muchos modelos, arriba, cerca de la cámara).",
        "Hold the tag against the back of the phone (on many models, near the camera at the top).",
        "Encoste a tag na parte de trás do celular (em muitos modelos, em cima, perto da câmera).",
    )
}
