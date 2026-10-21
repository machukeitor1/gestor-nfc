package cl.machukeitor.gestornfc.nfc

import android.net.Uri

/**
 * Redes sociales soportadas. Cada una arma su URL a partir de lo que escribe el usuario,
 * así el tag funciona en cualquier teléfono (abre la app si está instalada, si no el navegador).
 */
enum class SocialNetwork(
    val displayName: String,
    val color: Long,
    val fieldLabel: String,
    val placeholder: String,
    private val domains: List<String>,
    private val build: (String) -> String,
) {
    INSTAGRAM("Instagram", 0xFFE1306C, "Usuario", "tu_usuario", listOf("instagram.com"), { "https://instagram.com/$it" }),
    WHATSAPP("WhatsApp", 0xFF25D366, "Número", "912345678", listOf("wa.me", "whatsapp.com"), { "https://wa.me/$it" }),
    FACEBOOK("Facebook", 0xFF1877F2, "Usuario o página", "tu.pagina", listOf("facebook.com", "fb.com"), { "https://facebook.com/$it" }),
    TIKTOK("TikTok", 0xFF010101, "Usuario", "tu_usuario", listOf("tiktok.com"), { "https://tiktok.com/@$it" }),
    X("X (Twitter)", 0xFF000000, "Usuario", "tu_usuario", listOf("x.com", "twitter.com"), { "https://x.com/$it" }),
    YOUTUBE("YouTube", 0xFFFF0000, "Canal (@handle)", "tucanal", listOf("youtube.com", "youtu.be"), { "https://youtube.com/@$it" }),
    LINKEDIN("LinkedIn", 0xFF0A66C2, "Usuario del perfil", "tu-nombre", listOf("linkedin.com"), { "https://linkedin.com/in/$it" }),
    LINKEDIN_COMPANY("LinkedIn Empresa", 0xFF0A66C2, "Empresa", "tu-empresa", emptyList(), { "https://linkedin.com/company/$it" }),
    TELEGRAM("Telegram", 0xFF26A5E4, "Usuario", "tu_usuario", listOf("t.me", "telegram.me"), { "https://t.me/$it" }),
    THREADS("Threads", 0xFF000000, "Usuario", "tu_usuario", listOf("threads.net", "threads.com"), { "https://threads.net/@$it" }),
    SPOTIFY("Spotify", 0xFF1DB954, "Enlace de Spotify", "https://open.spotify.com/…", listOf("spotify.com"), { it }),
    GITHUB("GitHub", 0xFF24292F, "Usuario", "tu_usuario", listOf("github.com"), { "https://github.com/$it" }),
    TWITCH("Twitch", 0xFF9146FF, "Canal", "tu_canal", listOf("twitch.tv"), { "https://twitch.tv/$it" }),
    PINTEREST("Pinterest", 0xFFE60023, "Usuario", "tu_usuario", listOf("pinterest.com", "pin.it"), { "https://pinterest.com/$it" }),
    DISCORD("Discord", 0xFF5865F2, "Código de invitación", "abc123", listOf("discord.gg", "discord.com"), { "https://discord.gg/$it" });

    val initial: String get() = displayName.take(1)

    /**
     * Limpia lo que escribió el usuario: quita '@', espacios y, si pegó una URL completa,
     * extrae el usuario. Devuelve null si queda vacío o no es válido.
     */
    fun normalize(input: String): String? {
        val raw = input.trim()
        if (raw.isEmpty()) return null
        return when (this) {
            SPOTIFY -> raw.takeIf { it.startsWith("https://open.spotify.com/") || it.startsWith("https://spotify.link/") }
            WHATSAPP -> raw.filter { it.isDigit() }.takeIf { it.length >= 8 }
            else -> {
                val fromUrl = if (raw.contains("/")) {
                    Uri.parse(if (raw.startsWith("http")) raw else "https://$raw")
                        .pathSegments.lastOrNull { it.isNotBlank() && it != "in" && it != "company" }
                } else raw
                fromUrl?.removePrefix("@")?.trim()?.takeIf { it.isNotEmpty() && it.none(Char::isWhitespace) }
            }
        }
    }

    /** URL final. Para WhatsApp [extra] es el mensaje opcional. */
    fun buildUrl(value: String, extra: String = ""): String {
        val base = build(value)
        return if (this == WHATSAPP && extra.isNotBlank()) "$base?text=${Uri.encode(extra.trim())}" else base
    }

    companion object {
        fun detect(url: String): SocialNetwork? {
            val host = runCatching { Uri.parse(url).host }.getOrNull()?.lowercase()?.removePrefix("www.") ?: return null
            return entries.firstOrNull { net -> net.domains.any { host == it || host.endsWith(".$it") } }
        }
    }
}
