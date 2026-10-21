package cl.machukeitor.gestornfc.ui.write

data class Country(val flag: String, val name: String, val code: String)

/** Países para el selector de WhatsApp. Chile primero; el resto, Latinoamérica y algunos frecuentes. */
val COUNTRIES = listOf(
    Country("🇨🇱", "Chile", "56"),
    Country("🇦🇷", "Argentina", "54"),
    Country("🇵🇪", "Perú", "51"),
    Country("🇧🇴", "Bolivia", "591"),
    Country("🇨🇴", "Colombia", "57"),
    Country("🇻🇪", "Venezuela", "58"),
    Country("🇪🇨", "Ecuador", "593"),
    Country("🇺🇾", "Uruguay", "598"),
    Country("🇵🇾", "Paraguay", "595"),
    Country("🇧🇷", "Brasil", "55"),
    Country("🇲🇽", "México", "52"),
    Country("🇨🇷", "Costa Rica", "506"),
    Country("🇵🇦", "Panamá", "507"),
    Country("🇩🇴", "Rep. Dominicana", "1"),
    Country("🇨🇺", "Cuba", "53"),
    Country("🇭🇹", "Haití", "509"),
    Country("🇺🇸", "Estados Unidos / Canadá", "1"),
    Country("🇪🇸", "España", "34"),
    Country("🇮🇹", "Italia", "39"),
    Country("🇫🇷", "Francia", "33"),
    Country("🇩🇪", "Alemania", "49"),
    Country("🇬🇧", "Reino Unido", "44"),
    Country("🇨🇳", "China", "86"),
)
