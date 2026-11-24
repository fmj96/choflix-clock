package com.choflix.clock.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.BatteryManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import com.choflix.clock.ClockActivity
import com.choflix.clock.R
import com.choflix.clock.data.BatteryInfo
import com.choflix.clock.data.HomeState
import com.choflix.clock.data.Settings
import com.choflix.clock.data.TuyaCloudClient
import com.choflix.clock.data.Updater
import com.choflix.clock.data.WeatherRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

/**
 * 시계가 켜져 있는 내내 도는 포그라운드 서비스.
 *
 *  - 배터리를 40~50% 사이에 묶어 둔다(스마트 플러그 ON/OFF). 38°C 이상이면 잔량과 상관없이 끈다.
 *  - 실내 온·습도(5분)와 바깥 날씨(30분)를 받아 [HomeState] 에 넣는다.
 *
 * 플러그 명령은 원하는 상태가 바뀔 때 한 번 보내고, 그 뒤 실제 충전 여부가 따라오지 않으면
 * 2분마다 다시 보낸다. 5분 넘게 어긋나면 화면에 경고를 띄운다(플러그가 안 꺼지면 결국 만충).
 */
class BatteryPlugMonitorService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var settings: Settings
    private val plugLock = Mutex()

    private var lastWant: Boolean? = null
    private var lastSentAt = 0L
    private var mismatchSince = 0L

    private var client: TuyaCloudClient? = null
    private var clientKey = ""

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = onBattery(intent)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        settings = Settings(this)
        startInForeground()
        registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))?.let(::onBattery)

        // 배터리 방송은 잔량·온도가 바뀔 때만 온다 — 명령 재시도와 어긋남 판정은 1분마다 따로 본다.
        scope.launch(Dispatchers.Main) {
            while (true) {
                delay(60_000)
                registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))?.let(::onBattery)
            }
        }
        // 실내 온·습도는 5분마다. Tuya 무료(체험판) API 는 한 달 호출 수에 한도가 있다 — 2분이면
        // 센서만으로 한 달 2만 번이 넘어 한도에 닿을 수 있다. 실내 기온은 그렇게 빨리 변하지 않는다.
        scope.launch { loop(5 * 60_000L) { pollIndoor() } }
        scope.launch { loop(30 * 60_000L) { pollWeather() } }
        // 새 버전 확인은 6시간마다 — 있으면 시계의 톱니바퀴에 빨간 점이 뜬다.
        scope.launch { while (true) { runCatching { Updater.check() }; delay(6 * 3600_000L) } }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        unregisterReceiver(batteryReceiver)
        scope.cancel()
        super.onDestroy()
    }

    /** 주기마다, 또는 설정이 바뀌었다는 신호([HomeState.refresh])가 오면 바로 한 번 더. */
    private suspend fun loop(periodMs: Long, block: suspend () -> Unit) {
        while (true) {
            runCatching { block() }.onFailure { Log.w(TAG, "poll", it) }
            val seen = HomeState.refresh.value
            withTimeoutOrNull(periodMs) { HomeState.refresh.first { it != seen } }
        }
    }

    private fun tuya(): TuyaCloudClient? {
        val key = listOf(settings.tuyaRegion, settings.tuyaAccessId, settings.tuyaSecret).joinToString("|")
        if (key != clientKey) {
            client = TuyaCloudClient.from(settings)
            clientKey = key
        }
        return client
    }

    private suspend fun pollIndoor() {
        val c = tuya() ?: return
        val id = settings.sensorDeviceId.ifEmpty { return }
        runCatching { c.indoor(id) }
            .onSuccess { HomeState.indoor.value = it; HomeState.tuyaError.value = null }
            .onFailure { HomeState.tuyaError.value = "온습도: ${it.message}" }
    }

    private suspend fun pollWeather() {
        var lat = settings.lat
        var lon = settings.lon
        if (lat == null || lon == null) {
            val p = WeatherRepository.locateByIp() ?: return
            lat = p.lat; lon = p.lon
            HomeState.place.value = p.name
        } else {
            HomeState.place.value = settings.place
        }
        WeatherRepository.fetch(lat, lon)?.let { HomeState.weather.value = it }
    }

    private fun onBattery(intent: Intent) {
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
        val percent = if (level >= 0 && scale > 0) level * 100 / scale else -1
        val tempC = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) / 10.0
        val plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0
        val overheat = tempC >= settings.maxTempC

        if (!settings.chargeControl || percent < 0) {
            lastWant = null; mismatchSince = 0
            publish(percent, tempC, plugged, null, overheat)
            return
        }

        // 히스테리시스: 하한 이하면 켜고 상한 이상이면 끈다. 그 사이는 앞의 결정을 유지한다.
        // 처음 켰을 때 사이 구간이면 지금 충전 중인지를 그대로 이어 간다.
        val want = when {
            overheat -> false
            percent >= settings.highPercent -> false
            percent <= settings.lowPercent -> true
            else -> lastWant ?: plugged
        }

        val now = System.currentTimeMillis()
        if (want == plugged) mismatchSince = 0 else if (mismatchSince == 0L) mismatchSince = now
        // 어긋나면 2분마다 다시 보내되, 5분 넘게 안 맞으면(플러그가 꺼져 있거나 오프라인) 15분 간격으로
        // 늦춘다 — 한 달 API 호출 한도를 재시도로 다 쓰지 않게.
        val gap = if (mismatchSince != 0L && now - mismatchSince > 5 * 60_000) 15 * 60_000 else 2 * 60_000
        val retry = want != plugged && now - lastSentAt > gap
        if (want != lastWant || retry) {
            lastWant = want
            lastSentAt = now
            scope.launch { sendPlug(want) }
        }
        publish(percent, tempC, plugged, want, overheat)
    }

    private suspend fun sendPlug(on: Boolean) = plugLock.withLock {
        val c = tuya() ?: run { HomeState.tuyaError.value = "Tuya 키가 없습니다"; return@withLock }
        val id = settings.plugDeviceId.ifEmpty { HomeState.tuyaError.value = "플러그 ID가 없습니다"; return@withLock }
        runCatching { c.setPlug(id, settings.plugSwitchCode, on) }
            .onSuccess { if (it) HomeState.tuyaError.value = null }
            .onFailure { HomeState.tuyaError.value = "플러그: ${it.message}" }
    }

    private fun publish(percent: Int, tempC: Double, plugged: Boolean, want: Boolean?, overheat: Boolean) {
        val mismatch = want != null && mismatchSince != 0L &&
            System.currentTimeMillis() - mismatchSince > 5 * 60_000
        val info = BatteryInfo(percent, tempC, plugged, want, mismatch, overheat)
        if (info != HomeState.battery.value) {
            HomeState.battery.value = info
            getSystemService(NotificationManager::class.java).notify(NOTIF_ID, notification(info))
        }
    }

    private fun startInForeground() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "배터리 관리", NotificationManager.IMPORTANCE_LOW)
        )
        val n = notification(HomeState.battery.value)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIF_ID, n)
        }
    }

    private fun notification(b: BatteryInfo): Notification {
        val text = when {
            b.percent < 0 -> "시계 실행 중"
            b.wantPlugOn == null -> "배터리 ${b.percent}% · ${"%.1f".format(b.tempC)}°C · 충전 제어 꺼짐"
            else -> "배터리 ${b.percent}% · ${"%.1f".format(b.tempC)}°C · 플러그 ${if (b.wantPlugOn) "ON" else "OFF"}" +
                (if (b.mismatch) " · 플러그 응답 없음!" else "")
        }
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, ClockActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        return Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle("Choflix Clock")
            .setContentText(text)
            .setContentIntent(open)
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val TAG = "ClockService"
        private const val CHANNEL = "battery"
        private const val NOTIF_ID = 1

        fun start(context: Context) {
            context.startForegroundService(Intent(context, BatteryPlugMonitorService::class.java))
        }
    }
}
