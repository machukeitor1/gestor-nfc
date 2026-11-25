package cl.machukeitor.gestornfc.nfc

import cl.machukeitor.gestornfc.i18n.tr
import java.net.URLEncoder

/**
 * Redes sociales soportadas. Cada una arma su URL a partir de lo que escribe el usuario,
 * así el tag funciona en cualquier teléfono (abre la app si está instalada, si no el navegador).
 */
enum class SocialNetwork(
    private val brand: String,
    val color: Long,
    private val domains: List<String>,
    private val build: (String) -> String,
) {
    INSTAGRAM("Instagram", 0xFFE1306C, listOf("instagram.com"), { "https://instagram.com/$it" }),
    WHATSAPP("WhatsApp", 0xFF25D366, listOf("wa.me", "whatsapp.com"), { "https://wa.me/$it" }),
    FACEBOOK("Facebook", 0xFF1877F2, listOf("facebook.com", "fb.com"), { "https://facebook.com/$it" }),
    TIKTOK("TikTok", 0xFF010101, listOf("tiktok.com"), { "https://tiktok.com/@$it" }),
    X("X (Twitter)", 0xFF000000, listOf("x.com", "twitter.com"), { "https://x.com/$it" }),
    YOUTUBE("YouTube", 0xFFFF0000, listOf("youtube.com", "youtu.be"), { "https://youtube.com/@$it" }),
    LINKEDIN("LinkedIn", 0xFF0A66C2, listOf("linkedin.com"), { "https://linkedin.com/in/$it" }),
    LINKEDIN_COMPANY("LinkedIn", 0xFF0A66C2, emptyList(), { "https://linkedin.com/company/$it" }),
    TELEGRAM("Telegram", 0xFF26A5E4, listOf("t.me", "telegram.me"), { "https://t.me/$it" }),
    THREADS("Threads", 0xFF000000, listOf("threads.net", "threads.com"), { "https://threads.net/@$it" }),
    SPOTIFY("Spotify", 0xFF1DB954, listOf("spotify.com"), { it }),
    GITHUB("GitHub", 0xFF24292F, listOf("github.com"), { "https://github.com/$it" }),
    TWITCH("Twitch", 0xFF9146FF, listOf("twitch.tv"), { "https://twitch.tv/$it" }),
    PINTEREST("Pinterest", 0xFFE60023, listOf("pinterest.com", "pin.it"), { "https://pinterest.com/$it" }),
    DISCORD("Discord", 0xFF5865F2, listOf("discord.gg", "discord.com"), { "https://discord.gg/$it" });

    val displayName: String
        get() = if (this == LINKEDIN_COMPANY) "LinkedIn " + tr("Empresa", "Company", "Empresa") else brand

    /** Etiqueta del campo que se pide (usuario, canal, número…). */
    val fieldLabel: String
        get() = when (this) {
            WHATSAPP -> tr("Número", "Number", "Número")
            FACEBOOK -> tr("Usuario o página", "Username or page", "Usuário ou página")
            YOUTUBE -> tr("Canal (@handle)", "Channel (@handle)", "Canal (@handle)")
            LINKEDIN -> tr("Usuario del perfil", "Profile username", "Usuário do perfil")
            LINKEDIN_COMPANY -> tr("Empresa", "Company", "Empresa")
            SPOTIFY -> tr("Enlace de Spotify", "Spotify link", "Link do Spotify")
            TWITCH -> tr("Canal", "Channel", "Canal")
            DISCORD -> tr("Código de invitación", "Invite code", "Código de convite")
            else -> tr("Usuario", "Username", "Usuário")
        }

    val placeholder: String
        get() = when (this) {
            WHATSAPP -> "912345678"
            FACEBOOK -> tr("tu.pagina", "your.page", "sua.pagina")
            YOUTUBE -> tr("tucanal", "yourchannel", "seucanal")
            LINKEDIN -> tr("tu-nombre", "your-name", "seu-nome")
            LINKEDIN_COMPANY -> tr("tu-empresa", "your-company", "sua-empresa")
            SPOTIFY -> "https://open.spotify.com/…"
            TWITCH -> tr("tu_canal", "your_channel", "seu_canal")
            DISCORD -> "abc123"
            else -> tr("tu_usuario", "your_username", "seu_usuario")
        }

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
                    // Primer segmento de la ruta sin query ni fragmento: "instagram.com/usuario/?hl=es" → "usuario",
                    // "tiktok.com/@usuario/video/123" → "usuario".
                    raw.substringAfter("://").substringAfter('/', "")
                        .substringBefore('?').substringBefore('#')
                        .split('/')
                        .firstOrNull { it.isNotBlank() && it != "in" && it != "company" }
                } else raw
                fromUrl?.removePrefix("@")?.trim()?.takeIf { it.isNotEmpty() && it.none(Char::isWhitespace) }
            }
        }
    }

    /** URL final. Para WhatsApp [extra] es el mensaje opcional. */
    fun buildUrl(value: String, extra: String = ""): String {
        val base = build(value)
        return if (this == WHATSAPP && extra.isNotBlank()) "$base?text=${encode(extra.trim())}" else base
    }

    companion object {
        /** Codificación para URL con espacios como %20 (wa.me no entiende "+"). */
        fun encode(text: String): String = URLEncoder.encode(text, "UTF-8").replace("+", "%20")

        fun detect(url: String): SocialNetwork? {
            val host = url.substringAfter("://", "").substringBefore('/').substringBefore('?')
                .substringBefore(':').lowercase().removePrefix("www.").ifEmpty { return null }
            return entries.firstOrNull { net -> net.domains.any { host == it || host.endsWith(".$it") } }
        }
    }
}
