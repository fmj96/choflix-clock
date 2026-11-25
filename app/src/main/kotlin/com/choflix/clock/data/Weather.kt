package com.choflix.clock.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.util.Locale

/** 오늘 날씨. 기온은 섭씨. */
data class Weather(
    val currentC: Double,
    val minC: Double?,
    val maxC: Double?,
    /** WMO 날씨 코드(open-meteo 기준). 모르면 -1. */
    val code: Int,
    val isDay: Boolean,
    /** 지금 내리는 비·눈의 양(mm). 코드가 '맑음' 이어도 이게 있으면 비가 이긴다. */
    val precipitation: Double = 0.0,
    val cloudPercent: Int = 0,
)

/** 지금 미세먼지(㎍/㎥). */
data class Air(val pm10: Double, val pm25: Double) {
    /**
     * 우리나라 환경부 기준 등급 — 0 좋음, 1 보통, 2 나쁨, 3 매우나쁨. 둘 중 나쁜 쪽을 따른다.
     *  PM10 : 30 / 80 / 150,  PM2.5 : 15 / 35 / 75
     */
    val grade: Int
        get() {
            fun g(v: Double, a: Int, b: Int, c: Int) = when { v <= a -> 0; v <= b -> 1; v <= c -> 2; else -> 3 }
            return maxOf(g(pm10, 30, 80, 150), g(pm25, 15, 35, 75))
        }
}

data class Place(val name: String, val lat: Double, val lon: Double)

/**
 * 키가 필요 없는 곳만 쓴다.
 *  - open-meteo : 현재 기온·날씨 코드와 오늘 최저/최고.
 *  - met.no     : open-meteo 가 통째로 응답하지 않는 날이 있어(연결 시간 초과) 대신 쓴다.
 *  - open-meteo 대기질 : 미세먼지(PM10, PM2.5).
 *  - ipwho.is   : 위치를 정하지 않았을 때 IP 로 대략적인 도시.
 */
object WeatherRepository {

    private const val TIMEOUT_MS = 8000
    private const val UA = "ChoflixClock/1.0 (+https://github.com/fmj96/choflix-clock)"

    suspend fun fetch(lat: Double, lon: Double): Weather? = withContext(Dispatchers.IO) {
        runCatching { openMeteo(lat, lon) }.getOrNull() ?: runCatching { metNo(lat, lon) }.getOrNull()
    }

    /** open-meteo 대기질(키 없음). */
    suspend fun fetchAir(lat: Double, lon: Double): Air? = withContext(Dispatchers.IO) {
        runCatching {
            val cur = getJson(
                "https://air-quality-api.open-meteo.com/v1/air-quality?latitude=$lat&longitude=$lon&current=pm10,pm2_5"
            )?.optJSONObject("current") ?: return@runCatching null
            val pm10 = cur.optDouble("pm10")
            val pm25 = cur.optDouble("pm2_5")
            if (pm10.isNaN() || pm25.isNaN()) null else Air(pm10, pm25)
        }.getOrNull()
    }

    private fun openMeteo(lat: Double, lon: Double): Weather? {
        val json = getJson(
            "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon" +
                "&current=temperature_2m,weather_code,is_day,precipitation,cloud_cover" +
                "&daily=temperature_2m_min,temperature_2m_max&forecast_days=1&timezone=auto"
        ) ?: return null
        val cur = json.optJSONObject("current") ?: return null
        if (!cur.has("temperature_2m")) return null
        val daily = json.optJSONObject("daily")
        return Weather(
            currentC = cur.getDouble("temperature_2m"),
            minC = daily?.optJSONArray("temperature_2m_min")?.optDouble(0)?.takeIf { !it.isNaN() },
            maxC = daily?.optJSONArray("temperature_2m_max")?.optDouble(0)?.takeIf { !it.isNaN() },
            code = cur.optInt("weather_code", -1),
            isDay = cur.optInt("is_day", 1) == 1,
            precipitation = cur.optDouble("precipitation", 0.0).let { if (it.isNaN()) 0.0 else it },
            cloudPercent = cur.optInt("cloud_cover", 0),
        )
    }

    /**
     * 노르웨이 기상청 예보(이름을 밝힌 User-Agent 필수). 가장 가까운 시각의 값을 쓰고,
     * 날씨 기호 이름(clearsky_night 등)을 WMO 코드로 옮겨 화면 쪽은 그대로 둔다.
     * 오늘 최저/최고는 오늘 날짜에 든 시각별 기온 중에서 고른다.
     */
    private fun metNo(lat: Double, lon: Double): Weather? {
        val json = getJson(
            "https://api.met.no/weatherapi/locationforecast/2.0/compact" +
                "?lat=${"%.4f".format(Locale.US, lat)}&lon=${"%.4f".format(Locale.US, lon)}"
        ) ?: return null
        val series = json.optJSONObject("properties")?.optJSONArray("timeseries") ?: return null
        val now = series.optJSONObject(0)?.optJSONObject("data") ?: return null
        val inst = now.optJSONObject("instant")?.optJSONObject("details") ?: return null
        if (!inst.has("air_temperature")) return null

        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val temps = (0 until series.length()).mapNotNull { i ->
            val o = series.optJSONObject(i) ?: return@mapNotNull null
            val day = runCatching { OffsetDateTime.parse(o.optString("time")).atZoneSameInstant(zone).toLocalDate() }
                .getOrNull()
            if (day != today) return@mapNotNull null
            o.optJSONObject("data")?.optJSONObject("instant")?.optJSONObject("details")
                ?.optDouble("air_temperature")?.takeIf { !it.isNaN() }
        }

        val next = now.optJSONObject("next_1_hours")
        val symbol = next?.optJSONObject("summary")?.optString("symbol_code").orEmpty()
        val base = symbol.substringBefore('_')
        val code = when {
            "thunder" in base -> 95
            base == "clearsky" -> 0
            base == "fair" -> 1
            base == "partlycloudy" -> 2
            base == "cloudy" -> 3
            base == "fog" -> 45
            "sleet" in base -> 66
            "snowshowers" in base -> 85
            "snow" in base -> if (base.startsWith("heavy")) 75 else if (base.startsWith("light")) 71 else 73
            "rainshowers" in base -> 80
            "rain" in base -> if (base.startsWith("heavy")) 65 else if (base.startsWith("light")) 61 else 63
            else -> -1
        }
        return Weather(
            currentC = inst.getDouble("air_temperature"),
            minC = temps.minOrNull(),
            maxC = temps.maxOrNull(),
            code = code,
            isDay = when {
                symbol.endsWith("_night") -> false
                symbol.endsWith("_day") -> true
                else -> java.time.LocalTime.now().hour in 6..18
            },
            precipitation = next?.optJSONObject("details")?.optDouble("precipitation_amount", 0.0)
                ?.takeIf { !it.isNaN() } ?: 0.0,
            cloudPercent = inst.optDouble("cloud_area_fraction", 0.0).let { if (it.isNaN()) 0 else it.toInt() },
        )
    }

    /** 위치를 정하지 않았을 때 — 이 기기의 IP 로 대략적인 도시. 위치 권한이 필요 없다. */
    suspend fun locateByIp(): Place? = withContext(Dispatchers.IO) {
        runCatching {
            val json = getJson("https://ipwho.is/") ?: return@runCatching null
            if (!json.optBoolean("success", false)) return@runCatching null
            val lat = json.optDouble("latitude").takeIf { !it.isNaN() } ?: return@runCatching null
            val lon = json.optDouble("longitude").takeIf { !it.isNaN() } ?: return@runCatching null
            Place(json.optString("city").ifBlank { json.optString("region") }, lat, lon)
        }.getOrNull()
    }

    /** 설정 화면의 지역 검색. 한국어 이름으로도 찾는다(예: "수원"). */
    suspend fun searchPlace(query: String): List<Place> = withContext(Dispatchers.IO) {
        runCatching {
            val q = URLEncoder.encode(query.trim(), "UTF-8")
            val json = getJson("https://geocoding-api.open-meteo.com/v1/search?name=$q&count=8&language=ko")
                ?: return@runCatching emptyList()
            val arr = json.optJSONArray("results") ?: return@runCatching emptyList()
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                val name = listOf(o.optString("name"), o.optString("admin1"), o.optString("country"))
                    .filter { it.isNotBlank() }.distinct().joinToString(", ")
                Place(name, o.optDouble("latitude"), o.optDouble("longitude"))
            }
        }.getOrDefault(emptyList())
    }

    private fun getJson(url: String): JSONObject? {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            setRequestProperty("User-Agent", UA)
        }
        return try {
            if (conn.responseCode != 200) null
            else JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
        } catch (e: Exception) {
            null
        } finally {
            conn.disconnect()
        }
    }
}
