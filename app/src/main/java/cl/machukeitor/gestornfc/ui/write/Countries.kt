package cl.machukeitor.gestornfc.ui.write

import cl.machukeitor.gestornfc.i18n.tr
import java.util.Locale

/** [iso]: código ISO 3166 del país (para elegir por defecto el del teléfono). */
data class Country(val flag: String, val iso: String, val code: String, val es: String, val en: String, val pt: String) {
    val localName get() = tr(es, en, pt)
}

/** Países para el selector de WhatsApp. Chile primero; el resto, Latinoamérica y algunos frecuentes. */
val COUNTRIES = listOf(
    Country("🇨🇱", "CL", "56", "Chile", "Chile", "Chile"),
    Country("🇦🇷", "AR", "54", "Argentina", "Argentina", "Argentina"),
    Country("🇵🇪", "PE", "51", "Perú", "Peru", "Peru"),
    Country("🇧🇴", "BO", "591", "Bolivia", "Bolivia", "Bolívia"),
    Country("🇨🇴", "CO", "57", "Colombia", "Colombia", "Colômbia"),
    Country("🇻🇪", "VE", "58", "Venezuela", "Venezuela", "Venezuela"),
    Country("🇪🇨", "EC", "593", "Ecuador", "Ecuador", "Equador"),
    Country("🇺🇾", "UY", "598", "Uruguay", "Uruguay", "Uruguai"),
    Country("🇵🇾", "PY", "595", "Paraguay", "Paraguay", "Paraguai"),
    Country("🇧🇷", "BR", "55", "Brasil", "Brazil", "Brasil"),
    Country("🇲🇽", "MX", "52", "México", "Mexico", "México"),
    Country("🇨🇷", "CR", "506", "Costa Rica", "Costa Rica", "Costa Rica"),
    Country("🇵🇦", "PA", "507", "Panamá", "Panama", "Panamá"),
    Country("🇩🇴", "DO", "1", "Rep. Dominicana", "Dominican Rep.", "Rep. Dominicana"),
    Country("🇨🇺", "CU", "53", "Cuba", "Cuba", "Cuba"),
    Country("🇭🇹", "HT", "509", "Haití", "Haiti", "Haiti"),
    Country("🇺🇸", "US", "1", "Estados Unidos / Canadá", "United States / Canada", "Estados Unidos / Canadá"),
    Country("🇪🇸", "ES", "34", "España", "Spain", "Espanha"),
    Country("🇵🇹", "PT", "351", "Portugal", "Portugal", "Portugal"),
    Country("🇮🇹", "IT", "39", "Italia", "Italy", "Itália"),
    Country("🇫🇷", "FR", "33", "Francia", "France", "França"),
    Country("🇩🇪", "DE", "49", "Alemania", "Germany", "Alemanha"),
    Country("🇬🇧", "GB", "44", "Reino Unido", "United Kingdom", "Reino Unido"),
    Country("🇨🇳", "CN", "86", "China", "China", "China"),
)

/** País del teléfono si está en la lista; si no, Chile. */
fun defaultCountryIndex(): Int =
    COUNTRIES.indexOfFirst { it.iso == Locale.getDefault().country }.takeIf { it >= 0 } ?: 0
