package cl.machukeitor.gestornfc.nfc

fun ByteArray.toHex(separator: String = ":"): String =
    joinToString(separator) { "%02X".format(it) }

fun String.hexToBytes(): ByteArray {
    val clean = filter { !it.isWhitespace() && it != ':' }
    require(clean.length % 2 == 0) { "Hex inválido" }
    return ByteArray(clean.length / 2) { clean.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
}

fun Byte.u(): Int = toInt() and 0xFF
