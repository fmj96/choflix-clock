package com.choflix.clock.data

import kotlinx.coroutines.flow.MutableStateFlow

data class BatteryInfo(
    val percent: Int = -1,
    val tempC: Double = 0.0,
    /** 실제로 전원이 들어오고 있는지(충전기 쪽에서 본 사실). */
    val plugged: Boolean = false,
    /** 구간 제어가 원하는 플러그 상태. 제어를 끄면 null. */
    val wantPlugOn: Boolean? = null,
    /**
     * 플러그를 꺼 달라고 했는데 몇 분째 계속 전원이 들어오는(또는 반대) 상태 — 와이파이·Tuya·
     * 플러그 중 어딘가 끊겼다는 뜻이다. 화면에 경고를 띄운다.
     */
    val mismatch: Boolean = false,
    val overheat: Boolean = false,
)

/** 서비스가 모아 오고 화면이 그리는 값. 같은 프로세스라 전역 하나로 충분하다. */
object HomeState {
    val weather = MutableStateFlow<Weather?>(null)
    val place = MutableStateFlow("")
    val air = MutableStateFlow<Air?>(null)
    /** 날짜별 미세먼지 예보(주간 날씨 화면). */
    val airDaily = MutableStateFlow<Map<java.time.LocalDate, Air>>(emptyMap())
    val indoor = MutableStateFlow<Indoor?>(null)
    val battery = MutableStateFlow(BatteryInfo())
    /** 마지막 Tuya 오류(설정 화면에 보여준다). 성공하면 지운다. */
    val tuyaError = MutableStateFlow<String?>(null)
    /** 기기 화면의 Tuya 기기 목록. 다시 열 때 바로 보이게 남겨 둔다. */
    val devices = MutableStateFlow<List<TuyaDevice>>(emptyList())
    /** 설정이 바뀌어 날씨·센서를 바로 다시 불러야 할 때 1씩 늘린다. */
    val refresh = MutableStateFlow(0)
}
