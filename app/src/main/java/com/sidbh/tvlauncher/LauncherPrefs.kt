package com.sidbh.tvlauncher

import android.content.Context

/** Small launcher settings: recents, category overrides, first-run flag and the weather city. */
class LauncherPrefs(context: Context) {
    private val p = context.getSharedPreferences("launcher", Context.MODE_PRIVATE)

    fun lastUsed(): Map<String, Long> = buildMap {
        for ((k, v) in p.all) if (k.startsWith(USED) && v is Long) put(k.removePrefix(USED), v)
    }

    fun markLaunched(id: String) {
        p.edit().putLong(USED + id, System.currentTimeMillis()).apply()
    }

    fun overrides(): Map<String, Category> = buildMap {
        for ((k, v) in p.all) {
            if (!k.startsWith(CAT) || v !is String) continue
            Category.entries.firstOrNull { it.name == v }?.let { put(k.removePrefix(CAT), it) }
        }
    }

    fun setOverride(id: String, category: Category) {
        p.edit().putString(CAT + id, category.name).apply()
    }

    var seeded: Boolean
        get() = p.getBoolean("seeded", false)
        set(value) = p.edit().putBoolean("seeded", value).apply()

    var homeStyle: HomeStyle
        get() = HomeStyle.entries.firstOrNull { it.name == p.getString("home_style", null) } ?: HomeStyle.Classic
        // commit(), not apply(): a tiny write, and it must be on disk even if the TV is switched off right after choosing.
        set(value) {
            p.edit().putString("home_style", value.name).commit()
        }

    // One switch for the sounds of the Arcade and Retro styles and the Mancap game.
    var arcadeSound: Boolean
        get() = p.getBoolean("arcade_sound", true)
        set(value) = p.edit().putBoolean("arcade_sound", value).apply()

    var chompHigh: Int
        get() = p.getInt("pac_high", 0)
        set(value) = p.edit().putInt("pac_high", value).apply()

    val city: String get() = p.getString("city", "").orEmpty()

    fun saveCity(name: String, lat: Double, lon: Double) {
        p.edit().putString("city", name).putString("lat", lat.toString()).putString("lon", lon.toString()).apply()
    }

    fun coords(): Pair<Double, Double>? {
        val lat = p.getString("lat", null)?.toDoubleOrNull() ?: return null
        val lon = p.getString("lon", null)?.toDoubleOrNull() ?: return null
        return lat to lon
    }

    fun saveWeather(w: Weather) {
        p.edit().putInt("wx_temp", w.tempC).putString("wx_summary", w.summary).apply()
    }

    fun cachedWeather(): Weather? {
        if (city.isEmpty() || !p.contains("wx_temp")) return null
        return Weather(city, p.getInt("wx_temp", 0), p.getString("wx_summary", "").orEmpty())
    }

    private companion object {
        const val USED = "used:"
        const val CAT = "cat:"
    }
}
