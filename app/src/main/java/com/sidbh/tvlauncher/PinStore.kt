package com.sidbh.tvlauncher

import android.content.Context

/** Pinned (favorite) item ids, kept in pin order. */
class PinStore(context: Context) {
    private val prefs = context.getSharedPreferences("pins", Context.MODE_PRIVATE)

    fun load(): List<String> =
        prefs.getString(KEY, "").orEmpty().split(SEP).filter { it.isNotEmpty() }

    fun toggle(id: String): List<String> {
        val current = load()
        return replace(if (id in current) current - id else current + id)
    }

    fun replace(ids: List<String>): List<String> {
        prefs.edit().putString(KEY, ids.joinToString(SEP)).apply()
        return ids
    }

    // Ids are package names or "input:<id>"; neither contains a comma.
    private companion object {
        const val KEY = "pinned"
        const val SEP = ","
    }
}
