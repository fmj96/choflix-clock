package com.choflix.clock.ui

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import com.choflix.clock.R
import com.choflix.clock.data.HomeState
import com.choflix.clock.data.Settings
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.time.format.TextStyle as DateTextStyle
import java.util.Locale
import kotlin.math.roundToInt

/** 세그먼트 숫자(DSEG7 Modern). '!' 는 숫자 한 칸 너비의 빈칸이다. */
val Segment = FontFamily(Font(R.font.dseg7_modern_bold))

private val Red = Color(0xFFE5414B)
private val Blue = Color(0xFF3D8BFF)
private val Amber = Color(0xFFD98A3C)
private val AmberDim = Color(0xFF9A5A22)

/**
 * 사진 속 LED 탁상시계 모양.
 *  왼쪽: TODAY · 날씨 그림 · 오늘 최저—최고(빨강)
 *  오른쪽: 오전/오후 · 큰 시각 · 요일 · 배터리 / 와이파이 · 날짜 | INDOOR 기온 습도
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

    // 새벽에는 흰색 대신 눈부심 적은 엠버, 빨강도 어둡게.
    val main = if (night) Amber else Color.White
    val warm = if (night) AmberDim else Red
    val accent = if (night) AmberDim else Blue

    // 번인 방지: 1분마다 시계 전체를 반지름 16dp 원 위의 다음 자리로 옮긴다(8자리 순환).
    var step by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) { while (true) { delay(60_000); step = (step + 1) % 8 } }
    val dx by animateDpAsState(SHIFTS[step].first, tween(3000), label = "dx")
    val dy by animateDpAsState(SHIFTS[step].second, tween(3000), label = "dy")

    // 콜론 깜빡임(1초 주기).
    val blink by rememberInfiniteTransition(label = "colon").animateFloat(
        1f, 0.15f, infiniteRepeatable(tween(500), RepeatMode.Reverse), label = "colonA"
    )

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(
                if (night) Brush.linearGradient(listOf(Color.Black, Color.Black))
                else Brush.linearGradient(listOf(Color(0xFF0E1A2E), Color(0xFF02040A)))
            ),
    ) {
        val d = LocalDensity.current
        val h = maxHeight - 40.dp
        fun Dp.sp(): TextUnit = with(d) { toSp() }

        Row(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 28.dp, vertical = 20.dp)
                .offset(dx, dy),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // ── 왼쪽: 오늘 날씨 ──
            BoxWithConstraints(Modifier.weight(0.3f).fillMaxHeight()) {
                val lo = weather?.minC?.roundToInt()?.toString() ?: "--"
                val hi = weather?.maxC?.roundToInt()?.toString() ?: "--"
                // 폭에 맞춘다: 숫자(0.816em씩) + °C 두 개와 가운데 줄(합쳐 약 1.8em). 영하 두 자리도 들어가게.
                val leftW = maxWidth
                val tempSize = minOf(h * 0.17f, leftW / ((lo.length + hi.length) * 0.816f + 1.8f))
                Column(
                    Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("TODAY", color = main, fontSize = (h * 0.06f).sp(), fontWeight = FontWeight.Bold)
                    Box(Modifier.size(minOf(h * 0.42f, leftW * 0.8f)), contentAlignment = Alignment.Center) {
                        weather?.let { WeatherIcon(it, Modifier.fillMaxSize(), main, night) }
                    }
                    Row(verticalAlignment = Alignment.Bottom) {
                        Temp(lo, warm, tempSize, d)
                        Box(
                            Modifier.padding(horizontal = tempSize * 0.12f).padding(bottom = tempSize * 0.42f)
                                .width(tempSize * 0.4f).height(2.dp).background(warm)
                        )
                        Temp(hi, warm, tempSize, d)
                    }
                }
            }

            // 가운데 무지개 세로줄.
            Box(
                Modifier.padding(horizontal = 20.dp).width(2.dp).fillMaxHeight(0.92f).background(
                    if (night) Brush.verticalGradient(listOf(AmberDim, AmberDim))
                    else Brush.verticalGradient(
                        listOf(Color(0xFFFF5A5A), Color(0xFFFFC83D), Color(0xFF4CD964), Color(0xFF3D8BFF), Color(0xFFB06CFF))
                    )
                )
            )

            // ── 오른쪽: 시각 ──
            BoxWithConstraints(Modifier.weight(0.7f).fillMaxHeight()) {
                val w = maxWidth
                // 큰 시각 "10:30" 은 숫자 넷(0.816em씩)과 콜론(0.2em) = 3.46em. 옆의 AM/PM·요일 칸을
                // 빼고 남는 폭에 맞춘다 — 폰 가로 화면은 생각보다 좁다(Redmi Note 10 Pro 약 870dp).
                val side = if (settings.use24h) h * 0.17f + 12.dp else h * 0.30f + 22.dp
                val bigSize = minOf(h * 0.40f, (w - side) / 3.5f)
                // 아래 줄 숫자는 날짜 4 + 실내 기온 3 + 습도 2 = 9칸(점은 폭이 없다). 아이콘·줄·단위 몫을 빼고 맞춘다.
                val smallSize = minOf(h * 0.15f, (w - h * 0.2f - 150.dp) / 7.4f)
                Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
                    Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.fillMaxHeight().padding(top = h * 0.12f), contentAlignment = Alignment.TopStart) {
                            if (!settings.use24h) {
                                Text(
                                    if (now.hour < 12) "AM" else "PM", color = main,
                                    fontSize = (h * 0.07f).sp(), fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                        Spacer(Modifier.width(10.dp))
                        val hour = if (settings.use24h) now.hour else (now.hour % 12).let { if (it == 0) 12 else it }
                        // 12시간제의 한 자리 시는 앞자리를 비운다('!' = 빈 숫자 칸) — 자리가 흔들리지 않게.
                        val hh = if (settings.use24h) "%02d".format(hour) else hour.toString().padStart(2, '!')
                        val big = TextStyle(fontFamily = Segment, fontSize = bigSize.sp(), color = main)
                        Text(hh, style = big, maxLines = 1, softWrap = false)
                        Text(":", style = big, modifier = Modifier.alpha(blink), maxLines = 1, softWrap = false)
                        Text("%02d".format(now.minute), style = big, maxLines = 1, softWrap = false)
                        Spacer(Modifier.width(12.dp))
                        // 위쪽은 모서리 아이콘(전구·톱니바퀴) 자리라 요일을 그 아래로 내린다.
                        Column(
                            Modifier.fillMaxHeight().padding(top = maxOf(h * 0.1f, 56.dp), bottom = h * 0.1f),
                            verticalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                now.dayOfWeek.getDisplayName(DateTextStyle.SHORT, Locale.US).uppercase(),
                                color = main, fontSize = (h * 0.07f).sp(), fontWeight = FontWeight.Bold,
                            )
                            BatteryBadge(battery, main, warm, h * 0.06f, d)
                        }
                    }

                    Row(
                        Modifier.height(h * 0.24f),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            if (online) Icons.Filled.Wifi else Icons.Filled.WifiOff, null,
                            tint = if (online) accent else warm, modifier = Modifier.size(h * 0.08f),
                        )
                        Spacer(Modifier.width(12.dp))
                        val small = TextStyle(fontFamily = Segment, fontSize = smallSize.sp(), color = main)
                        Text("%2d.%02d".format(now.monthValue, now.dayOfMonth).replace(' ', '!'), style = small, maxLines = 1)
                        Box(Modifier.padding(horizontal = 14.dp).width(1.dp).fillMaxHeight(0.8f).background(accent))
                        Text(
                            "INDOOR", color = accent, fontSize = (h * 0.035f).sp(), fontWeight = FontWeight.Bold,
                            maxLines = 1, modifier = Modifier.vertical().padding(end = 6.dp),
                        )
                        val t = indoor?.temperatureC
                        Text(t?.let { "%.1f".format(Locale.US, it) } ?: "--.-", style = small, maxLines = 1)
                        UnitLabel("°C", main, h, d)
                        Spacer(Modifier.width(14.dp))
                        Text(indoor?.humidity?.let { "%2d".format(it).replace(' ', '!') } ?: "--", style = small, maxLines = 1)
                        UnitLabel("%", main, h, d)
                    }
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
                Icon(Icons.Outlined.Settings, "설정", tint = main.copy(alpha = 0.45f), modifier = Modifier.size(26.dp))
            }
        }
    }
}

@Composable
private fun Temp(c: String, color: Color, size: Dp, d: androidx.compose.ui.unit.Density) {
    Row(verticalAlignment = Alignment.Top) {
        Text(
            c, maxLines = 1, softWrap = false,
            style = TextStyle(fontFamily = Segment, fontSize = with(d) { size.toSp() }, color = color),
        )
        Text("°C", color = color, fontSize = with(d) { (size * 0.3f).toSp() }, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun UnitLabel(u: String, color: Color, h: Dp, d: androidx.compose.ui.unit.Density) {
    Text(
        u, color = color, fontSize = with(d) { (h * 0.06f).toSp() }, fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 4.dp, bottom = h * 0.06f),
    )
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
