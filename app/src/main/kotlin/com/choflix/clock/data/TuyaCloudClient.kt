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

    /** 플러그 전원. 성공하면 true. */
    suspend fun setPlug(plugId: String, switchCode: String, on: Boolean): Boolean = withContext(Dispatchers.IO) {
        val body = JSONObject().put(
            "commands", JSONArray().put(JSONObject().put("code", switchCode).put("value", on))
        ).toString()
        request("POST", "/v1.0/devices/$plugId/commands", body) == true
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
