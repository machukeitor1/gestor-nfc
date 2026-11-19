package cl.machukeitor.gestornfc.i18n

import java.util.Locale

enum class Lang { ES, EN, PT }

/**
 * Idioma según el del teléfono (o el elegido para la app en Android 13+):
 * español y portugués tal cual; cualquier otro idioma, inglés.
 */
val currentLang: Lang
    get() = when (Locale.getDefault().language) {
        "es" -> Lang.ES
        "pt" -> Lang.PT
        else -> Lang.EN
    }

/** Texto en los tres idiomas de la app; devuelve el que corresponde al idioma actual. */
fun tr(es: String, en: String, pt: String): String = when (currentLang) {
    Lang.ES -> es
    Lang.EN -> en
    Lang.PT -> pt
}
