package com.sidbh.tvlauncher

import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class Weather(val city: String, val tempC: Int, val summary: String)

data class HourPoint(val hour: Int, val tempC: Int, val summary: String)
data class DayPoint(val date: String, val highC: Int, val lowC: Int, val summary: String)

/** Full-screen weather page. Fetched only when that page is opened. */
data class WeatherDetail(
    val city: String,
    val tempC: Int,
    val feelsC: Int,
    val humidity: Int,
    val windKmh: Int,
    val summary: String,
    val hours: List<HourPoint>,
    val days: List<DayPoint>,
)

/** Open-Meteo: free, no API key. All calls block, so run them off the main thread. */
class WeatherRepository(private val prefs: LauncherPrefs) {
    fun cached(): Weather? = prefs.cachedWeather()

    /** Looks [query] up and stores the best match's coordinates. */
    fun resolveCity(query: String): Boolean {
        val name = query.trim()
        if (name.isEmpty()) return false
        val body = get("https://geocoding-api.open-meteo.com/v1/search?count=1&name=" + URLEncoder.encode(name, "UTF-8"))
            ?: return false
        return try {
            val hit = JSONObject(body).optJSONArray("results")?.optJSONObject(0) ?: return false
            prefs.saveCity(hit.getString("name"), hit.getDouble("latitude"), hit.getDouble("longitude"))
            true
        } catch (_: JSONException) {
            false
        }
    }

    fun refresh(): Weather? {
        val (lat, lon) = prefs.coords() ?: return null
        val body = get("https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon&current=temperature_2m,weather_code")
            ?: return null
        return try {
            val current = JSONObject(body).getJSONObject("current")
            Weather(prefs.city, Math.round(current.getDouble("temperature_2m")).toInt(), describe(current.getInt("weather_code")))
                .also(prefs::saveWeather)
        } catch (_: JSONException) {
            null
        }
    }

    /** Current conditions, the next 8 hours and 5 days for the saved city. Blocking. */
    fun detail(): WeatherDetail? {
        val (lat, lon) = prefs.coords() ?: return null
        val body = get(
            "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon" +
                "&current=temperature_2m,apparent_temperature,relative_humidity_2m,weather_code,wind_speed_10m" +
                "&hourly=temperature_2m,weather_code&daily=weather_code,temperature_2m_max,temperature_2m_min" +
                "&timezone=auto&forecast_days=5",
        ) ?: return null
        return try {
            val root = JSONObject(body)
            val cur = root.getJSONObject("current")
            val hourly = root.getJSONObject("hourly")
            val times = hourly.getJSONArray("time")
            val temps = hourly.getJSONArray("temperature_2m")
            val codes = hourly.getJSONArray("weather_code")
            // Times are local ISO strings, so the current hour compares as text.
            val now = cur.getString("time").substring(0, 13)
            val start = (0 until times.length()).firstOrNull { times.getString(it).substring(0, 13) >= now } ?: 0
            val hours = (start until minOf(start + 8, times.length())).map {
                HourPoint(times.getString(it).substring(11, 13).toInt(), Math.round(temps.getDouble(it)).toInt(), describe(codes.getInt(it)))
            }
            val daily = root.getJSONObject("daily")
            val dates = daily.getJSONArray("time")
            val dayCodes = daily.getJSONArray("weather_code")
            val highs = daily.getJSONArray("temperature_2m_max")
            val lows = daily.getJSONArray("temperature_2m_min")
            val days = (0 until dates.length()).map {
                DayPoint(
                    dates.getString(it), Math.round(highs.getDouble(it)).toInt(),
                    Math.round(lows.getDouble(it)).toInt(), describe(dayCodes.getInt(it)),
                )
            }
            WeatherDetail(
                city = prefs.city,
                tempC = Math.round(cur.getDouble("temperature_2m")).toInt(),
                feelsC = Math.round(cur.getDouble("apparent_temperature")).toInt(),
                humidity = cur.getInt("relative_humidity_2m"),
                windKmh = Math.round(cur.getDouble("wind_speed_10m")).toInt(),
                summary = describe(cur.getInt("weather_code")),
                hours = hours,
                days = days,
            )
        } catch (_: JSONException) {
            null
        }
    }

    /**
     * Sets the city from this connection's public IP (city-level, no permission needed; a TV has no GPS).
     * Only runs when the user asks. Blocking.
     */
    fun locateByIp(): Boolean {
        val body = get("https://ipwho.is/?fields=success,city,latitude,longitude") ?: return false
        return try {
            val o = JSONObject(body)
            if (!o.optBoolean("success") || o.optString("city").isBlank()) return false
            prefs.saveCity(o.getString("city"), o.getDouble("latitude"), o.getDouble("longitude"))
            true
        } catch (_: JSONException) {
            false
        }
    }

    private fun get(url: String): String? = try {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 8_000
        conn.readTimeout = 8_000
        try {
            if (conn.responseCode == 200) conn.inputStream.bufferedReader().readText() else null
        } finally {
            conn.disconnect()
        }
    } catch (_: IOException) {
        null
    }

    // WMO weather interpretation codes, grouped.
    private fun describe(code: Int) = when (code) {
        0 -> "Clear"
        1, 2 -> "Partly cloudy"
        3 -> "Cloudy"
        45, 48 -> "Fog"
        in 51..57 -> "Drizzle"
        in 61..67, in 80..82 -> "Rain"
        in 71..77, 85, 86 -> "Snow"
        in 95..99 -> "Storm"
        else -> "Weather"
    }
}
