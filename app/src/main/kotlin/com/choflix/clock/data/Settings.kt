package com.choflix.clock.data

import android.content.Context
import android.content.SharedPreferences
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * 앱 설정. 기기 안(SharedPreferences)에만 둔다 — Tuya 키처럼 남에게 보이면 안 되는 값도
 * 여기 들어가므로 저장소나 서버로 내보내지 않는다.
 */
class Settings(context: Context) {
    private val p: SharedPreferences =
        context.applicationContext.getSharedPreferences("clock", Context.MODE_PRIVATE)

    // ── 시계 ──
    var use24h: Boolean
        get() = p.getBoolean("use24h", false)
        set(v) = p.edit().putBoolean("use24h", v).apply()

    // ── 화면 ──
    /** 글자·아이콘 색(ARGB). 기본 흰색. */
    var color: Long
        get() = p.getLong("color", 0xFFFFFFFF)
        set(v) = p.edit().putLong("color", v).apply()
    /** 숫자 글꼴([com.choflix.clock.ui.DigitFont] 이름). */
    var font: String
        get() = p.getString("font", "Condensed") ?: "Condensed"
        set(v) = p.edit().putString("font", v).apply()

    /** 시계 숫자 굵기(300~900). */
    var clockWeight: Int
        get() = p.getInt("clockWeight", 600)
        set(v) = p.edit().putInt("clockWeight", v).apply()
    /** 날짜·기온·습도 숫자 굵기(300~900). */
    var infoWeight: Int
        get() = p.getInt("infoWeight", 600)
        set(v) = p.edit().putInt("infoWeight", v).apply()
    /** 밝기 버튼으로 손수 정한 밝기(0~100, 폰 밝기 막대 기준). -1 이면 자동(조도 센서). */
    var manualBrightness: Int
        get() = p.getInt("manualBrightness", -1)
        set(v) = p.edit().putInt("manualBrightness", v).apply()

    // ── 시간대별 자동 밝기(디머) ──
    var dimEnabled: Boolean
        get() = p.getBoolean("dimEnabled", true)
        set(v) = p.edit().putBoolean("dimEnabled", v).apply()
    /** 시작·끝 시각(0~23시). 시작이 끝보다 크면 자정을 넘긴다(예: 22시 ~ 6시). */
    var dimStart: Int
        get() = p.getInt("dimStart", 0)
        set(v) = p.edit().putInt("dimStart", v).apply()
    var dimEnd: Int
        get() = p.getInt("dimEnd", 6)
        set(v) = p.edit().putInt("dimEnd", v).apply()
    /** 그 시간대의 밝기 상한(%, 폰 밝기 막대 기준). */
    var dimMaxPercent: Int
        get() = p.getInt("dimMaxPercent", 15)
        set(v) = p.edit().putInt("dimMaxPercent", v).apply()
    /** 화면 밝기를 최저로 내려도 밝을 때 — 그 시간대에 화면 위에 덧씌우는 검은 막의 진하기(%). */
    var dimOverlayPercent: Int
        get() = p.getInt("dimOverlayPercent", 0)
        set(v) = p.edit().putInt("dimOverlayPercent", v).apply()
    /**
     * 그 시간대에 글자를 눈부심 적은 엠버색으로. 기본은 끔 — 켜 두면 그 시간대에는 고른 색 대신
     * 엠버가 보여 '색을 바꿔도 안 바뀐다'로 보였다(v0.5.0 실사용).
     */
    var dimAmber: Boolean
        get() = p.getBoolean("dimAmber2", false)
        set(v) = p.edit().putBoolean("dimAmber2", v).apply()

    fun isDimHour(hour: Int): Boolean {
        if (!dimEnabled || dimStart == dimEnd) return false
        return if (dimStart < dimEnd) hour in dimStart until dimEnd else hour >= dimStart || hour < dimEnd
    }

    // ── 날씨 위치. 비어 있으면 IP 로 대략 찾는다. ──
    var place: String
        get() = p.getString("place", "") ?: ""
        set(v) = p.edit().putString("place", v).apply()
    var lat: Double?
        get() = p.getString("lat", null)?.toDoubleOrNull()
        set(v) = p.edit().putString("lat", v?.toString()).apply()
    var lon: Double?
        get() = p.getString("lon", null)?.toDoubleOrNull()
        set(v) = p.edit().putString("lon", v?.toString()).apply()

    // ── Tuya Cloud ──
    var tuyaRegion: String
        get() = p.getString("tuyaRegion", TuyaRegion.entries.first().name) ?: TuyaRegion.entries.first().name
        set(v) = p.edit().putString("tuyaRegion", v).apply()
    var tuyaAccessId: String
        get() = p.getString("tuyaAccessId", "") ?: ""
        set(v) = p.edit().putString("tuyaAccessId", v.trim()).apply()
    var tuyaSecret: String
        get() = p.getString("tuyaSecret", "") ?: ""
        set(v) = p.edit().putString("tuyaSecret", v.trim()).apply()
    var sensorDeviceId: String
        get() = p.getString("sensorDeviceId", "") ?: ""
        set(v) = p.edit().putString("sensorDeviceId", v.trim()).apply()
    var plugDeviceId: String
        get() = p.getString("plugDeviceId", "") ?: ""
        set(v) = p.edit().putString("plugDeviceId", v.trim()).apply()
    /** 플러그 전원 명령 코드. 대부분 switch_1, 일부 제품은 switch. */
    var plugSwitchCode: String
        get() = p.getString("plugSwitchCode", "switch_1") ?: "switch_1"
        set(v) = p.edit().putString("plugSwitchCode", v.trim().ifEmpty { "switch_1" }).apply()

    // ── 배터리 구간 제어 ──
    var chargeControl: Boolean
        get() = p.getBoolean("chargeControl", false)
        set(v) = p.edit().putBoolean("chargeControl", v).apply()
    var lowPercent: Int
        get() = p.getInt("lowPercent", 40)
        set(v) = p.edit().putInt("lowPercent", v).apply()
    var highPercent: Int
        get() = p.getInt("highPercent", 50)
        set(v) = p.edit().putInt("highPercent", v).apply()
    var maxTempC: Int
        get() = p.getInt("maxTempC", 38)
        set(v) = p.edit().putInt("maxTempC", v).apply()

    // ── 다른 앱에서 시계로 돌아오기 ──
    var autoReturn: Boolean
        get() = p.getBoolean("autoReturn", true)
        set(v) = p.edit().putBoolean("autoReturn", v).apply()
    /** 손대지 않고 기다리는 시간(분). */
    var autoReturnMin: Int
        get() = p.getInt("autoReturnMin", 5)
        set(v) = p.edit().putInt("autoReturnMin", v).apply()

    // ── 셋탑(구글 TV) 리모컨 — 같은 집 안 주소·인증서 지문만 둔다 ──
    var tvHost: String
        get() = p.getString("tvHost", "") ?: ""
        set(v) = p.edit().putString("tvHost", v).apply()
    var tvName: String
        get() = p.getString("tvName", "") ?: ""
        set(v) = p.edit().putString("tvName", v).apply()
    var tvCertSha: String
        get() = p.getString("tvCertSha", "") ?: ""
        set(v) = p.edit().putString("tvCertSha", v).apply()
    val tvPaired: Boolean
        get() = tvHost.isNotEmpty() && tvCertSha.isNotEmpty()

    fun forgetTv() = p.edit().remove("tvHost").remove("tvName").remove("tvCertSha").apply()

    val tuyaConfigured: Boolean
        get() = tuyaAccessId.isNotEmpty() && tuyaSecret.isNotEmpty()

    // ── 설정 잠금 ──
    /** 설정 화면 비밀번호. 숫자 그대로가 아니라 소금(salt)을 친 해시만 둔다. 비어 있으면 잠금 꺼짐. */
    val settingsLocked: Boolean
        get() = !p.getString("pinHash", null).isNullOrEmpty()

    fun setPin(pin: String?) {
        if (pin.isNullOrEmpty()) {
            p.edit().remove("pinHash").remove("pinSalt").apply()
            return
        }
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }.joinToString("") { "%02x".format(it) }
        p.edit().putString("pinSalt", salt).putString("pinHash", hash(salt, pin)).apply()
    }

    fun checkPin(pin: String): Boolean {
        val h = p.getString("pinHash", null) ?: return true
        return hash(p.getString("pinSalt", "") ?: "", pin) == h
    }

    private fun hash(salt: String, pin: String): String =
        MessageDigest.getInstance("SHA-256").digest("$salt:$pin".toByteArray()).joinToString("") { "%02x".format(it) }
}

/** Tuya 개발자 플랫폼 데이터센터. 프로젝트를 만들 때 고른 곳과 같아야 한다. */
enum class TuyaRegion(val label: String, val host: String) {
    WesternAmerica("미국 서부", "https://openapi.tuyaus.com"),
    EasternAmerica("미국 동부", "https://openapi-ueaz.tuyaus.com"),
    CentralEurope("중부 유럽", "https://openapi.tuyaeu.com"),
    WesternEurope("서부 유럽", "https://openapi-weaz.tuyaeu.com"),
    China("중국", "https://openapi.tuyacn.com"),
    India("인도", "https://openapi.tuyain.com"),
}
