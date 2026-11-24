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
import com.choflix.clock.service.BatteryPlugMonitorService
import com.choflix.clock.ui.ClockScreen
import com.choflix.clock.ui.DevicesScreen
import com.choflix.clock.ui.SettingsScreen
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalTime
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.pow

/**
 * 가로 전체화면 시계. 화면을 끄지 않고, 조도 센서로 밝기를 스스로 맞춘다.
 * 아무 데나 길게 누르면 설정.
 */
class ClockActivity : ComponentActivity(), SensorEventListener {

    private lateinit var settings: Settings
    private var sensorManager: SensorManager? = null
    private var lightSensor: Sensor? = null

    /** 조도를 지수 이동 평균으로 누른 값(0~1). 손 그림자 한 번에 화면이 껌벅이지 않게. */
    private var smoothed = -1f
    private var applied = -2f

    /** 새벽(00~06시)이면 엠버 톤·밝기 상한. */
    private var night by mutableStateOf(isNight())
    private var showSettings by mutableStateOf(false)
    private var showDevices by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settings = Settings(this)
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
            BackHandler(enabled = showSettings || showDevices) { showSettings = false; showDevices = false }
            when {
                showSettings -> SettingsScreen(settings, onClose = { showSettings = false; hideSystemBars() })
                showDevices -> DevicesScreen(settings, onClose = { showDevices = false })
                else -> ClockScreen(
                    settings, night = night,
                    onOpenDevices = { showDevices = true },
                    onOpenSettings = { showSettings = true },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        hideSystemBars()
        lightSensor?.let { sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL) }
        applyBrightness()
    }

    override fun onPause() {
        sensorManager?.unregisterListener(this)
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
        // 센서가 없는 기기는 시스템 밝기를 따르고, 새벽에만 낮춘다.
        var b = if (smoothed < 0) -1f else smoothed
        if (night) b = if (b < 0) NIGHT_CAP else b.coerceAtMost(NIGHT_CAP)
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

    private fun isNight() = LocalTime.now().hour < 6

    private companion object {
        const val ALPHA = 0.2f
        const val NIGHT_CAP = 0.15f
    }
}
