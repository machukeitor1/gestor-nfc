package cl.machukeitor.gestornfc.nfc

/** Resultado de leer un tag: todo lo que muestra la pantalla "Leer". */
data class TagInfo(
    val uid: String,
    val chip: String,
    val manufacturer: String,
    val technologies: List<String>,
    val sections: List<InfoSection>,
    val ndef: NdefInfo?,
    val memory: List<MemoryRow>,
    val memoryNote: String?,
    /** NTAG21x / Ultralight EV1 con contraseña que protege el contenido. */
    val writeProtected: Boolean = false,
    /** La contraseña de este tag está guardada en el teléfono (la app la usa sola al escribir). */
    val passwordSaved: Boolean = false,
    val scannedAt: Long = System.currentTimeMillis(),
)

data class InfoSection(val title: String, val items: List<Pair<String, String>>)

data class NdefInfo(
    val type: String,
    val maxSize: Int,
    val usedSize: Int,
    val writable: Boolean,
    val canMakeReadOnly: Boolean,
    val records: List<NdefRecordInfo>,
    /** Mensaje NDEF tal cual, para copiarlo a otro tag o guardarlo. */
    val raw: ByteArray?,
) {
    /** Resumen de una línea: "URL: https://…", "Vacío"… */
    val summary: String
        get() = records.firstOrNull { it.kind != "Vacío" }
            ?.let { "${it.kind}: ${it.value}" + if (records.size > 1) " (+${records.size - 1})" else "" }
            ?: "Vacío"
}

data class NdefRecordInfo(
    val kind: String,
    val value: String,
    val details: List<Pair<String, String>> = emptyList(),
    /** URI que se puede abrir desde la app (web, teléfono, mapa…), si aplica. */
    val openUri: String? = null,
    val sizeBytes: Int,
)

/** Una página de memoria (4 bytes) de tags tipo NTAG / Ultralight. */
data class MemoryRow(val page: Int, val hex: String, val ascii: String, val note: String?)
