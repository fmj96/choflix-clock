package com.choflix.clock.ui

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.BatteryStd
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import com.choflix.clock.data.HomeState
import com.choflix.clock.data.Settings
import com.choflix.clock.data.Updater
import androidx.compose.foundation.shape.CircleShape
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.time.format.TextStyle as DateTextStyle
import java.util.Locale
import kotlin.math.roundToInt

/**
 * LED 탁상시계 모양.
 *  왼쪽: 날짜·요일 / 날씨 그림 + 바깥 기온 / INDOOR 실내 기온·습도
 *  오른쪽: 윗줄 AM/PM·와이파이·배터리, 그 아래 큰 시각
 * 글자·아이콘은 모두 설정한 한 가지 색(기본 흰색). 경고만 빨강.
 */
@Composable
fun ClockScreen(settings: Settings, night: Boolean, onOpenDevices: () -> Unit, onOpenSettings: () -> Unit) {
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = LocalDateTime.now()
            delay(1000L - System.currentTimeMillis() % 1000L)
        }
    }

    val weather by HomeState.weather.collectAsState()
    val indoor by HomeState.indoor.collectAsState()
    val battery by HomeState.battery.collectAsState()
    val online = rememberOnline()
    val update by Updater.available.collectAsState()

    val font = DigitFont.of(settings.font)
    // 야간 자동 밝기 구간에는 (설정에 따라) 눈부심 적은 엠버로.
    val main = if (night && settings.dimAmber) NightAmber else Color(settings.color)
    val soft = main.copy(alpha = 0.55f)

    // 번인 방지: 1분마다 시계 전체를 반지름 16dp 원 위의 다음 자리로 옮긴다(8자리 순환).
    var step by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) { while (true) { delay(60_000); step = (step + 1) % 8 } }
    val dx by animateDpAsState(SHIFTS[step].first, tween(3000), label = "dx")
    val dy by animateDpAsState(SHIFTS[step].second, tween(3000), label = "dy")

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(
                if (night) Brush.linearGradient(listOf(Color.Black, Color.Black))
                else Brush.linearGradient(listOf(Color(0xFF0E1A2E), Color(0xFF02040A)))
            ),
    ) {
        val d = LocalDensity.current
        val h = maxHeight - 20.dp
        fun Dp.sp(): TextUnit = with(d) { toSp() }

        Row(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp, vertical = 10.dp)
                .offset(dx, dy),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // ── 왼쪽: 날짜·요일 / 날씨 그림 + 바깥 기온 / 실내 기온·습도 ──
            BoxWithConstraints(Modifier.weight(0.38f).fillMaxHeight()) {
                val leftW = maxWidth
                // 모든 크기는 '높이 상한'과 '칸 폭에 들어가는 크기' 중 작은 쪽. 글자 폭은 글꼴별 em.
                val date = "%d.%02d".format(now.monthValue, now.dayOfMonth)
                val dateSize = minOf(h * 0.19f, leftW / (font.em(date) + 1.9f))
                val outside = weather?.currentC?.roundToInt()?.toString() ?: "--"
                // 날씨 그림은 작게, 바깥 기온은 크게.
                val iconSize = minOf(h * 0.22f, leftW * 0.28f)
                val outSize = minOf(h * 0.40f, (leftW - iconSize - 8.dp) / (font.em(outside) + 0.45f))
                val inTemp = indoor?.temperatureC?.let { "%.1f".format(Locale.US, it) } ?: "--.-"
                val inHum = indoor?.humidity?.toString() ?: "--"
                val inSize = minOf(h * 0.24f, (leftW - h * 0.06f - 8.dp) / (font.em(inTemp) + font.em(inHum) + 1.1f))
                Column(
                    Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceEvenly,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(date, style = font.style(dateSize.sp(), main), maxLines = 1)
                        Spacer(Modifier.width(dateSize * 0.3f))
                        Text(
                            now.dayOfWeek.getDisplayName(DateTextStyle.SHORT, Locale.US).uppercase(),
                            color = main, fontSize = (dateSize * 0.72f).sp(), fontWeight = FontWeight.Bold, maxLines = 1,
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(iconSize), contentAlignment = Alignment.Center) {
                            weather?.let { WeatherIcon(it, Modifier.fillMaxSize(), main) }
                        }
                        Spacer(Modifier.width(8.dp))
                        Reading(outside, "°C", main, outSize, font, d)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "INDOOR", color = soft, fontSize = (h * 0.035f).sp(), fontWeight = FontWeight.Bold,
                            maxLines = 1, modifier = Modifier.vertical().padding(end = 6.dp),
                        )
                        Reading(inTemp, "°C", main, inSize, font, d)
                        Spacer(Modifier.width(inSize * 0.25f))
                        Reading(inHum, "%", main, inSize, font, d)
                    }
                }
            }

            // 가운데 세로줄.
            Box(Modifier.padding(horizontal = 10.dp).width(2.dp).fillMaxHeight(0.9f).background(soft))

            // ── 오른쪽: 윗줄(AM/PM · 와이파이 · 배터리)과 큰 시각 ──
            Column(Modifier.weight(0.62f).fillMaxHeight()) {
                Row(
                    // 오른쪽 끝은 모서리의 전구·톱니바퀴 자리(48dp 두 개)라 비워 둔다.
                    Modifier.fillMaxWidth().height(h * 0.12f).padding(end = 100.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (!settings.use24h) {
                        Text(
                            if (now.hour < 12) "AM" else "PM", color = main,
                            fontSize = (h * 0.075f).sp(), fontWeight = FontWeight.Bold,
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    Icon(
                        if (online) Icons.Filled.Wifi else Icons.Filled.WifiOff, null,
                        tint = if (online) main else WarnRed, modifier = Modifier.size(h * 0.06f),
                    )
                    Spacer(Modifier.width(12.dp))
                    BatteryBadge(battery, main, WarnRed, h * 0.06f, d)
                }
                BoxWithConstraints(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    // 폭과 높이 중 먼저 닿는 쪽에 맞춘다.
                    val bigSize = minOf(maxHeight, maxWidth / font.em("88:88"))
                    val hour = if (settings.use24h) now.hour else (now.hour % 12).let { if (it == 0) 12 else it }
                    val hh = if (settings.use24h) "%02d".format(hour) else hour.toString()
                    // 콜론은 깜빡이지 않는다(사용자 요청).
                    Text("$hh:%02d".format(now.minute), style = font.style(bigSize.sp(), main), maxLines = 1, softWrap = false)
                }
            }
        }

        // 오른쪽 위 모서리: 전구(기기 켜기/끄기)와 톱니바퀴(설정). 화면 아무 데나 스쳐도
        // 열리지 않게 이 둘만 버튼으로 둔다. 번인 방지 이동도 같이 받는다.
        Row(Modifier.align(Alignment.TopEnd).padding(4.dp).offset(dx, dy)) {
            IconButton(onClick = onOpenDevices, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Outlined.Lightbulb, "기기", tint = main.copy(alpha = 0.45f), modifier = Modifier.size(26.dp))
            }
            IconButton(onClick = onOpenSettings, modifier = Modifier.size(48.dp)) {
                Box {
                    Icon(Icons.Outlined.Settings, "설정", tint = main.copy(alpha = 0.45f), modifier = Modifier.size(26.dp))
                    // 새 버전이 있으면 빨간 점.
                    if (update != null) {
                        Box(Modifier.align(Alignment.TopEnd).size(8.dp).background(WarnRed, CircleShape))
                    }
                }
            }
        }
    }
}

/** 숫자 + 작은 단위(°C, %). */
@Composable
private fun Reading(value: String, unit: String, color: Color, size: Dp, font: DigitFont, d: androidx.compose.ui.unit.Density) {
    Row(verticalAlignment = Alignment.Top) {
        Text(
            value, maxLines = 1, softWrap = false,
            style = font.style(with(d) { size.toSp() }, color),
        )
        Text(unit, color = color, fontSize = with(d) { (size * 0.32f).toSp() }, fontWeight = FontWeight.Bold)
    }
}

/** 사진의 알람 종 자리에 배터리. 충전 제어가 어긋나거나 과열이면 빨갛게. */
@Composable
private fun BatteryBadge(
    b: com.choflix.clock.data.BatteryInfo, color: Color, warn: Color, size: Dp, d: androidx.compose.ui.unit.Density,
) {
    if (b.percent < 0) return
    val c = if (b.mismatch || b.overheat) warn else color
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            if (b.plugged) Icons.Filled.BatteryChargingFull else Icons.Filled.BatteryStd, null,
            tint = c, modifier = Modifier.size(size),
        )
        Text(
            "${b.percent}%" + (if (b.overheat) " ${b.tempC.roundToInt()}°" else ""),
            color = c, fontSize = with(d) { (size * 0.7f).toSp() },
        )
    }
}

/** 인터넷이 되는 네트워크에 붙어 있는지. */
@Composable
private fun rememberOnline(): Boolean {
    val context = LocalContext.current
    var online by remember { mutableStateOf(true) }
    DisposableEffect(Unit) {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        fun check() = cm.getNetworkCapabilities(cm.activeNetwork)
            ?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        online = check()
        val cb = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) { online = true }
            override fun onLost(network: Network) { online = check() }
        }
        cm.registerDefaultNetworkCallback(cb)
        onDispose { cm.unregisterNetworkCallback(cb) }
    }
    return online
}

/** 글자를 왼쪽으로 90° 눕힌다. rotate 와 달리 차지하는 자리도 가로·세로를 바꿔 잡는다. */
private fun Modifier.vertical() = this
    .layout { measurable, constraints ->
        val p = measurable.measure(constraints.copy(minWidth = 0, maxWidth = Constraints.Infinity))
        layout(p.height, p.width) { p.place(-(p.width - p.height) / 2, (p.width - p.height) / 2) }
    }
    .rotate(-90f)

private val SHIFTS = listOf(
    0.dp to 0.dp, 16.dp to 0.dp, 11.dp to 11.dp, 0.dp to 16.dp,
    (-11).dp to 11.dp, (-16).dp to 0.dp, (-11).dp to (-11).dp, 0.dp to (-16).dp,
)
