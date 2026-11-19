package cl.machukeitor.gestornfc.nfc

import cl.machukeitor.gestornfc.i18n.tr

/** Mensajes que se repiten en varias operaciones con el tag. */
object Msg {
    val tagLostHold
        get() = tr(
            "Se perdió el contacto con el tag. Mantenlo quieto e intenta de nuevo.",
            "Lost contact with the tag. Hold it still and try again.",
            "O contato com a tag foi perdido. Mantenha-a parada e tente de novo.",
        )

    val tagLostAway
        get() = tr(
            "Se perdió el contacto con el tag. Aléjalo y vuelve a acercarlo.",
            "Lost contact with the tag. Move it away and bring it closer again.",
            "O contato com a tag foi perdido. Afaste-a e aproxime de novo.",
        )

    fun commError(detail: String?) = tr(
        "Error de comunicación con el tag",
        "Communication error with the tag",
        "Erro de comunicação com a tag",
    ) + ": " + (detail ?: tr("sin detalle", "no details", "sem detalhes"))

    fun doesNotFit(bytes: Int, capacity: Int) = tr(
        "No cabe: el contenido ocupa $bytes bytes y el tag admite $capacity.",
        "Doesn't fit: the content takes $bytes bytes and the tag holds $capacity.",
        "Não cabe: o conteúdo ocupa $bytes bytes e a tag comporta $capacity.",
    )

    fun written(bytes: Int, capacity: Int? = null) = if (capacity != null) tr(
        "Se grabaron $bytes bytes de $capacity disponibles.",
        "Wrote $bytes of $capacity available bytes.",
        "Foram gravados $bytes de $capacity bytes disponíveis.",
    ) else tr("Se grabaron $bytes bytes.", "Wrote $bytes bytes.", "Foram gravados $bytes bytes.")
}
