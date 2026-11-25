package com.choflix.clock.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.math.pow

data class Indoor(val temperatureC: Double?, val humidity: Int?)

class TuyaException(message: String) : Exception(message)

/** 실내 기록 한 점. 온도와 습도는 따로 보고되므로 한쪽만 있을 수 있다. */
data class IndoorSample(val timeMs: Long, val temperatureC: Double?, val humidity: Double?)

private val TEMP_CODES = listOf("va_temperature", "temp_current", "temp_indoor")
private val HUM_CODES = listOf("va_humidity", "humidity_value", "humidity_indoor")
private const val MAX_PAGES = 15

data class TuyaSwitch(val code: String, val on: Boolean)

data class TuyaDevice(
    val id: String,
    val name: String,
    val category: String,
    val online: Boolean,
    /** 켜고 끌 수 있는 스위치들. 비어 있으면 센서 같은 읽기 전용 기기. */
    val switches: List<TuyaSwitch>,
    /** 온습도 센서면 그 값(목록에 딸려 온 상태로 어림한 것). */
    val temperatureC: Double? = null,
    val humidity: Int? = null,
)

/**
 * Tuya Cloud OpenAPI 를 앱에서 바로 부른다(중간 서버 없음).
 *
 * 서명: HMAC-SHA256(client_id [+ access_token] + t + nonce + stringToSign, secret) 대문자 hex.
 * stringToSign = 메서드 \n 본문 SHA256 \n (서명할 헤더 — 쓰지 않아 빈 줄) \n 경로?정렬된쿼리
 */
class TuyaCloudClient(
    private val host: String,
    private val accessId: String,
    private val secret: String,
) {
    private var token: String? = null
    private var tokenExpiresAt = 0L
    private val tokenLock = Mutex()
    /** 기기별 상태 코드의 소수점 자리(scale). 사양을 한 번만 묻고 기억한다. */
    private val scales = mutableMapOf<String, Map<String, Int>>()

    suspend fun indoor(sensorId: String): Indoor = withContext(Dispatchers.IO) {
        val status = request("GET", "/v1.0/devices/$sensorId/status").let {
            it as? JSONArray ?: throw TuyaException("센서 상태 형식이 다릅니다")
        }
        val scale = scales.getOrPut(sensorId) { runCatching { specScales(sensorId) }.getOrDefault(emptyMap()) }
        var temp: Double? = null
        var hum: Int? = null
        for (i in 0 until status.length()) {
            val o = status.optJSONObject(i) ?: continue
            val code = o.optString("code")
            val v = o.optDouble("value").takeIf { !it.isNaN() } ?: continue
            // 제품마다 이름이 다르다: va_temperature / temp_current, va_humidity / humidity_value.
            when {
                temp == null && (code == "va_temperature" || code == "temp_current" || code == "temp_indoor") ->
                    temp = scaled(v, scale[code], guessScale = if (v > 60) 1 else 0)
                hum == null && (code == "va_humidity" || code == "humidity_value" || code == "humidity_indoor") ->
                    hum = scaled(v, scale[code], guessScale = if (v > 100) 1 else 0).toInt()
            }
        }
        Indoor(temp, hum)
    }

    /**
     * 센서가 보고한 온·습도 기록(시간순) — Tuya 에 남은 만큼(보통 7일). 한 번에 100개씩 넘겨 받는다.
     * 새 경로(v2 report-logs)가 막혀 있으면 예전 경로(v1 logs)로.
     */
    suspend fun indoorHistory(sensorId: String, fromMs: Long, toMs: Long): List<IndoorSample> = withContext(Dispatchers.IO) {
        val scale = scales.getOrPut(sensorId) { runCatching { specScales(sensorId) }.getOrDefault(emptyMap()) }
        val codes = (TEMP_CODES + HUM_CODES).joinToString(",")
        val logs = runCatching {
            pages { key ->
                val r = request(
                    "GET",
                    "/v2.0/cloud/thing/$sensorId/report-logs?codes=$codes&start_time=$fromMs&end_time=$toMs&size=100" +
                        (key?.let { "&last_row_key=$it" } ?: ""),
                ) as? JSONObject
                Triple(r?.optJSONArray("logs"), r?.optBoolean("has_more") == true, r?.optString("last_row_key"))
            }
        }.getOrElse {
            pages { key ->
                val r = request(
                    "GET",
                    "/v1.0/devices/$sensorId/logs?type=7&codes=$codes&start_time=$fromMs&end_time=$toMs&size=100" +
                        (key?.let { "&start_row_key=$it" } ?: ""),
                ) as? JSONObject
                Triple(r?.optJSONArray("logs"), r?.optBoolean("has_next") == true, r?.optString("next_row_key"))
            }
        }
        logs.mapNotNull { o ->
            val code = o.optString("code")
            val v = o.optString("value").toDoubleOrNull() ?: return@mapNotNull null
            val t = o.optLong("event_time").takeIf { it > 0 } ?: return@mapNotNull null
            when (code) {
                in TEMP_CODES -> IndoorSample(t, scaled(v, scale[code], guessScale = if (v > 60) 1 else 0), null)
                in HUM_CODES -> IndoorSample(t, null, scaled(v, scale[code], guessScale = if (v > 100) 1 else 0))
                else -> null
            }
        }.sortedBy { it.timeMs }
    }

    /** 쪽 나눔을 따라가며 모은다. 호출 한도를 생각해 최대 [MAX_PAGES] 쪽. */
    private suspend fun pages(fetch: suspend (String?) -> Triple<JSONArray?, Boolean, String?>): List<JSONObject> {
        val out = mutableListOf<JSONObject>()
        var key: String? = null
        repeat(MAX_PAGES) {
            val (arr, more, next) = fetch(key)
            arr ?: throw TuyaException("기록 형식이 다릅니다")
            for (i in 0 until arr.length()) arr.optJSONObject(i)?.let(out::add)
            if (!more || next.isNullOrEmpty()) return out
            key = next
        }
        return out
    }

    /** 플러그 전원. 성공하면 true. */
    suspend fun setPlug(plugId: String, switchCode: String, on: Boolean): Boolean = withContext(Dispatchers.IO) {
        val body = JSONObject().put(
            "commands", JSONArray().put(JSONObject().put("code", switchCode).put("value", on))
        ).toString()
        request("POST", "/v1.0/devices/$plugId/commands", body) == true
    }

    /** 단일 상태 조회 — 버튼을 누른 뒤 실제로 바뀌었는지 다시 읽을 때. */
    suspend fun switches(deviceId: String): List<TuyaSwitch> = withContext(Dispatchers.IO) {
        switchesOf(request("GET", "/v1.0/devices/$deviceId/status") as? JSONArray)
    }

    /**
     * 클라우드 프로젝트에 연결한 스마트라이프 계정의 기기 전부. 100개씩 끊어 받는다.
     * 새 프로젝트에서 이 API 가 막혀 있으면(권한 없음) 프로젝트 기기 목록 API 로 다시 묻는다.
     */
    suspend fun devices(): List<TuyaDevice> = withContext(Dispatchers.IO) {
        val out = mutableListOf<TuyaDevice>()
        try {
            var lastRowKey = ""
            do {
                val r = request(
                    "GET",
                    "/v1.0/iot-01/associated-users/devices?size=100" +
                        (if (lastRowKey.isNotEmpty()) "&last_row_key=$lastRowKey" else ""),
                ) as? JSONObject ?: break
                val arr = r.optJSONArray("devices") ?: break
                for (i in 0 until arr.length()) arr.optJSONObject(i)?.let { out += device(it) }
                lastRowKey = r.optString("last_row_key")
            } while (r.optBoolean("has_more") && lastRowKey.isNotEmpty())
        } catch (e: TuyaException) {
            if (out.isNotEmpty()) return@withContext out   // 중간 쪽에서 끊겼으면 받은 데까지
            val arr = request("GET", "/v2.0/cloud/thing/device?page_size=20") as? JSONArray ?: throw e
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val id = o.optString("id")
                // 이 목록에는 상태가 없다 — 한 대씩 따로 읽는다.
                val sw = runCatching { switches(id) }.getOrDefault(emptyList())
                out += TuyaDevice(id, o.optString("customName").ifEmpty { o.optString("name") },
                    o.optString("category"), o.optBoolean("isOnline", true), sw)
            }
        }
        out.sortedWith(compareByDescending<TuyaDevice> { it.switches.isNotEmpty() }.thenBy { it.name })
    }

    private fun device(o: JSONObject): TuyaDevice {
        val status = o.optJSONArray("status")
        val (t, h) = climateOf(status)
        return TuyaDevice(
            id = o.optString("id"),
            name = o.optString("name"),
            category = o.optString("category"),
            online = o.optBoolean("online", true),
            switches = switchesOf(status),
            temperatureC = t,
            humidity = h,
        )
    }

    /** 상태 목록 속 온·습도. 소수점 자리(scale)는 사양을 묻지 않고 값 크기로 어림한다. */
    private fun climateOf(status: JSONArray?): Pair<Double?, Int?> {
        if (status == null) return null to null
        var t: Double? = null
        var h: Int? = null
        for (i in 0 until status.length()) {
            val o = status.optJSONObject(i) ?: continue
            val v = o.optDouble("value").takeIf { !it.isNaN() } ?: continue
            when (o.optString("code")) {
                "va_temperature", "temp_current", "temp_indoor" -> if (t == null) t = if (v > 60) v / 10 else v
                "va_humidity", "humidity_value", "humidity_indoor" -> if (h == null) h = (if (v > 100) v / 10 else v).toInt()
            }
        }
        return t to h
    }

    /** 상태 목록에서 켜고 끌 수 있는 것만(switch, switch_1.., switch_led 처럼 참/거짓 값). */
    private fun switchesOf(status: JSONArray?): List<TuyaSwitch> {
        if (status == null) return emptyList()
        return (0 until status.length()).mapNotNull { i ->
            val o = status.optJSONObject(i) ?: return@mapNotNull null
            val code = o.optString("code")
            val v = o.opt("value")
            if (code.startsWith("switch") && v is Boolean) TuyaSwitch(code, v) else null
        }.sortedBy { it.code }
    }

    private fun scaled(v: Double, scale: Int?, guessScale: Int): Double =
        v / 10.0.pow(scale ?: guessScale)

    private suspend fun specScales(deviceId: String): Map<String, Int> {
        val spec = request("GET", "/v1.0/devices/$deviceId/specifications") as? JSONObject ?: return emptyMap()
        val arr = spec.optJSONArray("status") ?: return emptyMap()
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val values = runCatching { JSONObject(o.optString("values")) }.getOrNull() ?: return@mapNotNull null
            if (!values.has("scale")) null else o.optString("code") to values.optInt("scale")
        }.toMap()
    }

    private suspend fun accessToken(): String = tokenLock.withLock {
        val now = System.currentTimeMillis()
        token?.takeIf { now < tokenExpiresAt }?.let { return it }
        val r = call("GET", "/v1.0/token?grant_type=1", "", null) as? JSONObject
            ?: throw TuyaException("토큰 응답이 비었습니다")
        val t = r.optString("access_token").ifEmpty { throw TuyaException("토큰이 없습니다") }
        // 만료 1분 전에 미리 새로 받는다.
        tokenExpiresAt = now + (r.optLong("expire_time", 3600) - 60).coerceAtLeast(60) * 1000
        token = t
        t
    }

    private suspend fun request(method: String, path: String, body: String = ""): Any? {
        return try {
            call(method, path, body, accessToken())
        } catch (e: TuyaException) {
            // 토큰이 서버 쪽에서 먼저 끊긴 경우(1010) 한 번만 새로 받아 다시 시도한다.
            if (e.message?.startsWith("1010") == true) {
                tokenLock.withLock { token = null }
                call(method, path, body, accessToken())
            } else throw e
        }
    }

    private fun call(method: String, path: String, body: String, accessToken: String?): Any? {
        val t = System.currentTimeMillis().toString()
        val nonce = UUID.randomUUID().toString()
        val stringToSign = listOf(method, sha256(body), "", sortedUrl(path)).joinToString("\n")
        val sign = hmac(accessId + (accessToken ?: "") + t + nonce + stringToSign)

        val conn = (URL(host + path).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 10_000
            readTimeout = 10_000
            setRequestProperty("client_id", accessId)
            setRequestProperty("sign", sign)
            setRequestProperty("t", t)
            setRequestProperty("nonce", nonce)
            setRequestProperty("sign_method", "HMAC-SHA256")
            if (accessToken != null) setRequestProperty("access_token", accessToken)
            if (body.isNotEmpty()) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                outputStream.use { it.write(body.toByteArray()) }
            }
        }
        try {
            val text = (if (conn.responseCode in 200..299) conn.inputStream else conn.errorStream)
                ?.bufferedReader()?.use { it.readText() } ?: throw TuyaException("HTTP ${conn.responseCode}")
            val json = JSONObject(text)
            if (!json.optBoolean("success", false)) {
                throw TuyaException("${json.optInt("code")} ${json.optString("msg")}")
            }
            return json.opt("result")
        } finally {
            conn.disconnect()
        }
    }

    /** 쿼리는 키 순서로 정렬해야 서명이 맞는다. */
    private fun sortedUrl(path: String): String {
        val q = path.substringAfter('?', "")
        if (q.isEmpty()) return path
        return path.substringBefore('?') + "?" + q.split('&').sortedBy { it.substringBefore('=') }.joinToString("&")
    }

    private fun sha256(s: String): String =
        MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }

    private fun hmac(s: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret.toByteArray(), "HmacSHA256"))
        return mac.doFinal(s.toByteArray()).joinToString("") { "%02X".format(it) }
    }

    companion object {
        fun from(s: Settings): TuyaCloudClient? {
            if (!s.tuyaConfigured) return null
            val region = runCatching { TuyaRegion.valueOf(s.tuyaRegion) }.getOrDefault(TuyaRegion.entries.first())
            return TuyaCloudClient(region.host, s.tuyaAccessId, s.tuyaSecret)
        }
    }
}
