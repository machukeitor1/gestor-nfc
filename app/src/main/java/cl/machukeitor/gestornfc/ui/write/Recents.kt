package cl.machukeitor.gestornfc.ui.write

import android.content.Context

/** Pone [route] primero, sin repetir, y deja como máximo [max]. */
fun pushRecent(current: List<String>, route: String, max: Int = RecentWrites.MAX): List<String> =
    (listOf(route) + current.filter { it != route }).take(max)

/** Tipos de escritura usados más recientemente, para mostrarlos arriba en "Escribir". */
object RecentWrites {
    const val MAX = 4
    private const val PREFS = "ui"
    private const val KEY = "recent_write"

    fun parse(saved: String?): List<WriteType> = saved.orEmpty().split(',')
        .mapNotNull { route -> WriteType.entries.firstOrNull { it.route == route } }
        .distinct().take(MAX)

    fun load(context: Context): List<WriteType> =
        parse(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null))

    fun record(context: Context, type: WriteType): List<WriteType> {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val routes = pushRecent(parse(prefs.getString(KEY, null)).map { it.route }, type.route)
        prefs.edit().putString(KEY, routes.joinToString(",")).apply()
        return parse(routes.joinToString(","))
    }
}
