package cl.machukeitor.gestornfc.nfc

import android.content.Context

/**
 * Contraseñas recordadas por tag (clave = UID). Se guardan en el almacenamiento privado de la app,
 * así al reescribir un tag propio no hay que volver a escribirla.
 */
class PasswordStore(context: Context) {

    private val prefs = context.getSharedPreferences("tag_passwords", Context.MODE_PRIVATE)

    fun get(uid: String): ByteArray? = prefs.getString(uid, null)?.let { runCatching { it.hexToBytes() }.getOrNull() }

    fun put(uid: String, pwd: ByteArray) = prefs.edit().putString(uid, pwd.toHex("")).apply()

    fun remove(uid: String) = prefs.edit().remove(uid).apply()

    /** Borra todas; devuelve cuántas había. */
    fun clear(): Int {
        val count = prefs.all.size
        prefs.edit().clear().apply()
        return count
    }
}
