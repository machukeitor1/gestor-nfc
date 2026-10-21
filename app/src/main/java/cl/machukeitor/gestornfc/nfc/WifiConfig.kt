package cl.machukeitor.gestornfc.nfc

import java.io.ByteArrayOutputStream

enum class WifiAuth(val label: String, val authType: Int, val encryptionType: Int) {
    WPA2("WPA2", 0x0020, 0x0008),
    WPA_WPA2("WPA/WPA2", 0x0022, 0x000C),
    OPEN("Abierta (sin contraseña)", 0x0001, 0x0001);

    companion object {
        fun fromAuthType(value: Int): WifiAuth = when {
            value == 0x0001 -> OPEN
            value and 0x0002 != 0 -> WPA_WPA2
            else -> WPA2
        }
    }
}

/**
 * Registro Wi‑Fi en formato WSC (Wi‑Fi Simple Config), MIME "application/vnd.wfa.wsc".
 * Es el formato que Android (10+) e iOS entienden para ofrecer conectarse a la red.
 */
data class WifiConfig(val ssid: String, val password: String, val auth: WifiAuth) {

    fun toPayload(): ByteArray {
        val credential = ByteArrayOutputStream().apply {
            tlv(NETWORK_INDEX, byteArrayOf(1))
            tlv(SSID, ssid.toByteArray(Charsets.UTF_8))
            tlv(AUTH_TYPE, short(auth.authType))
            tlv(ENCRYPTION_TYPE, short(auth.encryptionType))
            tlv(NETWORK_KEY, if (auth == WifiAuth.OPEN) ByteArray(0) else password.toByteArray(Charsets.UTF_8))
            tlv(MAC_ADDRESS, ByteArray(6) { 0xFF.toByte() })
        }.toByteArray()
        return ByteArrayOutputStream().apply {
            tlv(VERSION, byteArrayOf(0x10))
            tlv(CREDENTIAL, credential)
        }.toByteArray()
    }

    companion object {
        const val MIME = "application/vnd.wfa.wsc"

        private const val VERSION = 0x104A
        private const val CREDENTIAL = 0x100E
        private const val NETWORK_INDEX = 0x1026
        private const val SSID = 0x1045
        private const val AUTH_TYPE = 0x1003
        private const val ENCRYPTION_TYPE = 0x100F
        private const val NETWORK_KEY = 0x1027
        private const val MAC_ADDRESS = 0x1020

        fun parse(payload: ByteArray): WifiConfig? {
            var ssid: String? = null
            var key = ""
            var auth = WifiAuth.WPA2
            fun walk(data: ByteArray) {
                var i = 0
                while (i + 4 <= data.size) {
                    val type = (data[i].u() shl 8) or data[i + 1].u()
                    val len = (data[i + 2].u() shl 8) or data[i + 3].u()
                    if (i + 4 + len > data.size) return
                    val value = data.copyOfRange(i + 4, i + 4 + len)
                    when (type) {
                        CREDENTIAL -> walk(value)
                        SSID -> ssid = String(value, Charsets.UTF_8)
                        NETWORK_KEY -> key = String(value, Charsets.UTF_8)
                        AUTH_TYPE -> if (len == 2) auth = WifiAuth.fromAuthType((value[0].u() shl 8) or value[1].u())
                    }
                    i += 4 + len
                }
            }
            walk(payload)
            return ssid?.let { WifiConfig(it, key, auth) }
        }

        private fun short(v: Int) = byteArrayOf((v shr 8).toByte(), v.toByte())

        private fun ByteArrayOutputStream.tlv(type: Int, value: ByteArray) {
            write(short(type))
            write(short(value.size))
            write(value)
        }
    }
}
