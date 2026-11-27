package com.taigashinohara.migakeru

import android.content.SharedPreferences
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import org.json.JSONObject

/**
 * v0.13.0: the sky feed for 今日の空. Open-Meteo daily forecast — no key,
 * no account, facts only. Everything here is fail-soft: any error means
 * "no sky today" and the app behaves exactly as before.
 *
 * The cache is a single prefs string so tests (and the UI) can read the
 * day's facts synchronously: `yyyy-MM-dd|code|tmax|tmin|uv|precip|wind`.
 */
object SkyWeather {

    private val dateFmt: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE

    const val CACHE_KEY = "sky_cache"
    const val CACHE_AT_KEY = "sky_cache_at"

    /** Fresh enough: same day and fetched within the last 3 hours. */
    private const val FRESH_MS = 3 * 60 * 60 * 1000L

    fun cacheString(date: LocalDate, sky: MigakeruLogic.DaySky): String =
        listOf(
            date.format(dateFmt),
            sky.code.toString(),
            sky.tempMax.toString(),
            sky.tempMin.toString(),
            sky.uvMax.toString(),
            sky.precipProb.toString(),
            sky.windMaxKmh.toString(),
        ).joinToString("|")

    fun parseCache(raw: String?): Pair<LocalDate, MigakeruLogic.DaySky>? {
        if (raw.isNullOrEmpty()) return null
        return runCatching {
            val p = raw.split("|")
            val date = LocalDate.parse(p[0], dateFmt)
            val sky = MigakeruLogic.DaySky(
                code = p[1].toInt(),
                tempMax = p[2].toDouble(),
                tempMin = p[3].toDouble(),
                uvMax = p[4].toDouble(),
                precipProb = p[5].toInt(),
                windMaxKmh = p[6].toDouble(),
            )
            date to sky
        }.getOrNull()
    }

    fun readCache(prefs: SharedPreferences): Pair<LocalDate, MigakeruLogic.DaySky>? =
        parseCache(prefs.getString(CACHE_KEY, null))

    fun isFresh(prefs: SharedPreferences, today: LocalDate): Boolean {
        val cached = readCache(prefs) ?: return false
        if (cached.first != today) return false
        val at = prefs.getLong(CACHE_AT_KEY, 0L)
        return System.currentTimeMillis() - at < FRESH_MS
    }

    fun writeCache(prefs: SharedPreferences, date: LocalDate, sky: MigakeruLogic.DaySky) {
        prefs.edit()
            .putString(CACHE_KEY, cacheString(date, sky))
            .putLong(CACHE_AT_KEY, System.currentTimeMillis())
            .apply()
    }

    fun url(lat: Double, lon: Double): String =
        "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon" +
            "&daily=weather_code,temperature_2m_max,temperature_2m_min,uv_index_max," +
            "precipitation_probability_max,wind_speed_10m_max" +
            "&timezone=Asia%2FTokyo&forecast_days=1"

    /** Parse the Open-Meteo daily block (first entry = today). */
    fun parseResponse(json: String): MigakeruLogic.DaySky? = runCatching {
        val daily = JSONObject(json).getJSONObject("daily")
        fun arr(name: String) = daily.getJSONArray(name)
        fun dbl(name: String): Double =
            if (arr(name).isNull(0)) 0.0 else arr(name).getDouble(0)
        fun int(name: String): Int =
            if (arr(name).isNull(0)) 0 else arr(name).getInt(0)
        MigakeruLogic.DaySky(
            code = int("weather_code"),
            tempMax = dbl("temperature_2m_max"),
            tempMin = dbl("temperature_2m_min"),
            uvMax = dbl("uv_index_max"),
            precipProb = int("precipitation_probability_max"),
            windMaxKmh = dbl("wind_speed_10m_max"),
        )
    }.getOrNull()

    /** Blocking fetch — call off the main thread. Null on any failure. */
    fun fetch(lat: Double, lon: Double): MigakeruLogic.DaySky? = runCatching {
        val conn = URL(url(lat, lon)).openConnection() as HttpURLConnection
        conn.connectTimeout = 8000
        conn.readTimeout = 8000
        try {
            if (conn.responseCode != 200) return null
            val text = conn.inputStream.bufferedReader().use { it.readText() }
            parseResponse(text)
        } finally {
            conn.disconnect()
        }
    }.getOrNull()
}
