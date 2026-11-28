package com.choflix.clock

import android.Manifest
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import com.choflix.clock.data.Settings
import com.choflix.clock.service.AutoReturn
import com.choflix.clock.service.LandscapeLock
import com.choflix.clock.service.BatteryPlugMonitorService
import com.choflix.clock.data.NowPlaying
import com.choflix.clock.ui.ClockScreen
import com.choflix.clock.ui.PinDialog
import com.choflix.clock.ui.MusicScreen
import com.choflix.clock.ui.DevicesScreen
import com.choflix.clock.ui.SettingsScreen
import com.choflix.clock.ui.CalendarScreen
import com.choflix.clock.ui.IndoorScreen
import com.choflix.clock.ui.WeekScreen
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalTime
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.pow

/**
 * 가로 전체화면 시계. 화면을 끄지 않고, 조도 센서로 밝기를 스스로 맞춘다.
 * 위쪽 가운데 버튼: 전구는 기기 화면, 해는 밝기 바로 조절, 톱니바퀴는 설정.
 */
class ClockActivity : ComponentActivity(), SensorEventListener {

    private lateinit var settings: Settings
    private var sensorManager: SensorManager? = null
    private var lightSensor: Sensor? = null

    /** 조도를 지수 이동 평균으로 누른 값(0~1). 손 그림자 한 번에 화면이 껌벅이지 않게. */
    private var smoothed = -1f
    private var applied = -2f

    /** 설정한 야간 시간대(기본 0~6시)면 밝기 상한·엠버 톤. */
    private var night by mutableStateOf(false)
    private var showSettings by mutableStateOf(false)
    private var showDevices by mutableStateOf(false)
    private var showWeek by mutableStateOf(false)
    private var showIndoor by mutableStateOf(false)
    private var showCalendar by mutableStateOf(false)
    private var showMusic by mutableStateOf(false)
    /** 설정이 잠겨 있을 때 톱니바퀴를 누르면 비밀번호부터 묻는다. */
    private var askPin by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settings = Settings(this)
        night = isNight()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        hideSystemBars()

        sensorManager = getSystemService(SensorManager::class.java)
        lightSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_LIGHT)

        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
        BatteryPlugMonitorService.start(this)

        // 새벽 구간에 들어서고 나갈 때 밝기 상한을 다시 건다(조도가 그대로면 센서 이벤트가 안 온다).
        lifecycleScope.launch {
            while (true) {
                delay(30_000)
                val n = isNight()
                if (n != night) { night = n; applyBrightness() }
            }
        }

        setContent {
            BackHandler(enabled = showSettings || showDevices || showWeek || showIndoor || showCalendar || showMusic) {
                showSettings = false; showDevices = false; showWeek = false; showIndoor = false; showCalendar = false
                showMusic = false
            }
            when {
                showSettings -> SettingsScreen(settings, onClose = {
                    showSettings = false
                    hideSystemBars()
                    // 자동 밝기 시간대·상한을 바꿨을 수 있다 — 바로 다시 건다.
                    night = isNight(); applied = -2f; applyBrightness()
                })
                showDevices -> DevicesScreen(settings, onClose = { showDevices = false })
                showWeek -> WeekScreen(settings, onClose = { showWeek = false })
                showIndoor -> IndoorScreen(settings, onClose = { showIndoor = false })
                showCalendar -> CalendarScreen(settings, onClose = { showCalendar = false; hideSystemBars() })
                showMusic -> MusicScreen(settings, onClose = { showMusic = false })
                else -> {
                    ClockScreen(
                        settings, night = night,
                        onOpenDevices = { showDevices = true },
                        onOpenWeather = { showWeek = true },
                        onOpenIndoor = { showIndoor = true },
                        onOpenCalendar = { showCalendar = true },
                        onOpenMusic = { showMusic = true },
                        onOpenSettings = { if (settings.settingsLocked) askPin = true else showSettings = true },
                        onBrightness = { applied = -2f; applyBrightness() },
                    )
                    if (askPin) {
                        PinDialog(
                            "설정 잠금", "비밀번호를 넣어 주세요",
                            onDismiss = { askPin = false; hideSystemBars() },
                        ) { pin ->
                            if (settings.checkPin(pin)) {
                                askPin = false; showSettings = true
                                null
                            } else "비밀번호가 틀렸습니다"
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        AutoReturn.clockInFront = true
        LandscapeLock.release(this)
        hideSystemBars()
        // 음표 버튼이 '재생 중'을 보이려면 곡 정보를 듣고 있어야 한다(알림 접근을 허용했을 때만 붙는다).
        NowPlaying.start(this)
        lightSensor?.let { sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL) }
        applyBrightness()
    }

    override fun onPause() {
        sensorManager?.unregisterListener(this)
        AutoReturn.clockInFront = false
        super.onPause()
    }

    override fun onSensorChanged(event: SensorEvent) {
        val lux = event.values.firstOrNull() ?: return
        // 로그 눈금: 어두운 방(0~10 lux)에서 조금씩, 밝은 낮(1000 lux 이상)에서 최대.
        val frac = (log10(lux + 1f) / 3f).coerceIn(0f, 1f).toDouble().pow(1.5).toFloat()
        val target = 0.02f + 0.98f * frac
        smoothed = if (smoothed < 0) target else smoothed + ALPHA * (target - smoothed)
        applyBrightness()
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun applyBrightness() {
        // 밝기 버튼으로 손수 정했으면 그 값이 먼저다(자동·야간 상한보다 앞선다).
        val manual = settings.manualBrightness
        if (manual >= 0) {
            val m = Math.pow(manual / 100.0, 2.2).toFloat().coerceAtLeast(0.004f)
            if (abs(m - applied) < 0.002f) return
            applied = m
            window.attributes = window.attributes.apply { screenBrightness = m }
            return
        }
        // 센서가 없는 기기는 시스템 밝기를 따르고, 새벽에만 낮춘다.
        var b = if (smoothed < 0) -1f else smoothed
        // 설정의 %는 폰 밝기 막대 기준(눈에 보이는 밝기)이다. 창 밝기 값은 실제 빛의 양이라
        // 그대로 쓰면 15%가 막대 절반쯤으로 밝게 보였다(실사용) — 감마 2.2 로 바꿔 넣는다.
        // 0 은 일부 기기에서 백라이트를 아예 끄므로 아주 작은 값에서 멈춘다.
        val cap = Math.pow(settings.dimMaxPercent / 100.0, 2.2).toFloat().coerceAtLeast(0.004f)
        if (night) b = if (b < 0) cap else b.coerceAtMost(cap)
        if (abs(b - applied) < 0.01f) return
        applied = b
        window.attributes = window.attributes.apply { screenBrightness = b }
    }

    private fun hideSystemBars() {
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    private fun isNight() = settings.isDimHour(LocalTime.now().hour)

    private companion object {
        const val ALPHA = 0.2f
    }
}
