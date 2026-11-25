package com.choflix.clock.ui

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.BatteryStd
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import com.choflix.clock.data.BatteryInfo
import com.choflix.clock.data.HomeState
import com.choflix.clock.data.Settings
import com.choflix.clock.data.Updater
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.time.format.TextStyle as DateTextStyle
import java.util.Locale
import kotlin.math.roundToInt

/**
 * 플립 시계 배치(시안 D).
 *  왼쪽: 바깥 온도(큰 카드) / 실내 온도 / 습도
 *  오른쪽 윗줄: 날짜 · (오른쪽 끝) 버튼(전구=기기, 해=밝기, 톱니바퀴=설정) · 와이파이·배터리
 *  오른쪽 아래: 시 카드(왼쪽 위 AM/PM) | 분 카드 — 숫자는 카드에 꽉 맞춰 늘린다.
 * 화면 크기는 시안(1000×450)을 기준으로 한 단위 u 로 비례해 잡는다.
 */
@Composable
fun ClockScreen(
    settings: Settings,
    night: Boolean,
    onOpenDevices: () -> Unit,
    onOpenSettings: () -> Unit,
    onBrightness: () -> Unit,
) {
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
    val main = if (night && settings.dimAmber) NightAmber else Color(settings.color)
    // 카드는 밤에 더 어둡게 — 넓은 회색 면이 어두운 방에서 밝게 떠 보이지 않게.
    val card = if (night) Color(0xFF151515) else Color(0xFF2A2A2A)
    val button = if (night) Color(0xFF202020) else Color(0xFF3A3A3A)
    var showBrightness by remember { mutableStateOf(false) }

    // 번인 방지: 1분마다 화면 전체를 반지름 16dp 원 위의 다음 자리로 옮긴다(8자리 순환).
    var step by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { while (true) { delay(60_000); step = (step + 1) % 8 } }
    val dx by animateDpAsState(SHIFTS[step].first, tween(3000), label = "dx")
    val dy by animateDpAsState(SHIFTS[step].second, tween(3000), label = "dy")

    BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black)) {
        val d = LocalDensity.current
        val u = minOf(maxWidth / 1000f, maxHeight / 450f)
        fun Dp.sp(): TextUnit = with(d) { toSp() }

        Row(
            Modifier
                .fillMaxSize()
                .offset(dx, dy)
                .padding(start = u * 20, end = u * 24, top = u * 16, bottom = u * 18),
            horizontalArrangement = Arrangement.spacedBy(u * 12),
        ) {
            // ── 왼쪽: 바깥 온도 / 실내 온도 / 습도 ──
            Column(Modifier.width(u * 226).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(u * 8)) {
                Box(Modifier.fillMaxWidth().weight(2.2f).clip(RoundedCornerShape(u * 20)).background(card)) {
                    Label("실외 온도", main, (u * 17).sp(), Modifier.padding(start = u * 12, top = u * 12))
                    Box(Modifier.align(Alignment.TopEnd).padding(top = u * 8, end = u * 10).size(u * 44)) {
                        weather?.let { WeatherIcon(it, Modifier.fillMaxSize(), main) }
                    }
                    FitText(
                        weather?.currentC?.roundToInt()?.toString() ?: "--", font, settings.infoWeight, main,
                        Modifier.padding(start = u * 14, end = u * 46, top = u * 60, bottom = u * 14).fillMaxSize(),
                        stretch = true,
                        // 크기는 "-20" 기준으로 고정 — 한 자리일 때 숫자가 옆으로 퍼지지 않게. 빼기는 작게.
                        reference = "-20",
                        minusScale = 0.6f,
                    )
                    Text(
                        "°C", color = main, fontSize = (u * 30).sp(), fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.align(Alignment.TopEnd).padding(top = u * 56, end = u * 12),
                    )
                }
                SmallCard("실내 온도", indoor?.temperatureC?.let { "%.1f".format(Locale.US, it) } ?: "--.-", "°C",
                    font, settings.infoWeight, main, card, u, d, Modifier.weight(1f))
                SmallCard("실내 습도", indoor?.humidity?.toString() ?: "--", "%",
                    font, settings.infoWeight, main, card, u, d, Modifier.weight(1f))
            }

            // ── 오른쪽: 윗줄과 플립 카드 ──
            Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(u * 10)) {
                Box(Modifier.fillMaxWidth().height(u * 46)) {
                    Text(
                        "${now.monthValue}월 ${now.dayOfMonth}일 " +
                            now.dayOfWeek.getDisplayName(DateTextStyle.FULL, Locale.KOREAN),
                        color = main, fontSize = (u * 32).sp(), fontFamily = font.family,
                        fontWeight = FontWeight(font.nearest(settings.infoWeight)), maxLines = 1,
                        modifier = Modifier.align(Alignment.CenterStart),
                    )
                    // 오른쪽 끝: 버튼(전구=기기, 해=밝기, 톱니바퀴=설정) 다음에 와이파이·배터리.
                    Row(
                        Modifier.align(Alignment.CenterEnd),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(u * 10),
                    ) {
                        SquareButton(Icons.Outlined.Lightbulb, "기기", main, button, u, onOpenDevices)
                        SquareButton(Icons.Outlined.LightMode, "밝기", main, button, u) { showBrightness = true }
                        Box {
                            SquareButton(Icons.Outlined.Settings, "설정", main, button, u, onOpenSettings)
                            // 새 버전이 있으면 빨간 점.
                            if (update != null) {
                                Box(Modifier.align(Alignment.TopEnd).padding(u * 4).size(u * 10).background(WarnRed, CircleShape))
                            }
                        }
                        Spacer(Modifier.width(u * 8))
                        Icon(
                            if (online) Icons.Filled.Wifi else Icons.Filled.WifiOff, null,
                            tint = if (online) main else WarnRed, modifier = Modifier.size(u * 24),
                        )
                        BatteryBadge(battery, main, WarnRed, u * 26, d)
                    }
                }
                Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(u * 16)) {
                    val hour = if (settings.use24h) now.hour else (now.hour % 12).let { if (it == 0) 12 else it }
                    FlipCard(
                        "%02d".format(hour), if (settings.use24h) null else if (now.hour < 12) "AM" else "PM",
                        font, settings.clockWeight, main, card, u, d, Modifier.weight(1f),
                    )
                    FlipCard("%02d".format(now.minute), null, font, settings.clockWeight, main, card, u, d, Modifier.weight(1f))
                }
            }
        }

        // 야간 '추가로 어둡게' — 화면 밝기 최저보다 더 어둡게 검은 막을 덧씌운다.
        if (night && settings.dimOverlayPercent > 0) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = settings.dimOverlayPercent / 100f)))
        }

        if (showBrightness) {
            BrightnessPanel(settings, main, u, d, onChange = onBrightness, onClose = { showBrightness = false })
        }
    }
}

@Composable
private fun Label(text: String, color: Color, size: TextUnit, modifier: Modifier) {
    Text(text, color = color.copy(alpha = 0.6f), fontSize = size, maxLines = 1, modifier = modifier)
}

/** 실내 온도·습도 카드: 제목은 왼쪽 위, 숫자는 가운데(비율 유지). */
@Composable
private fun SmallCard(
    title: String, value: String, unit: String, font: DigitFont, weight: Int, color: Color, card: Color,
    u: Dp, d: androidx.compose.ui.unit.Density, modifier: Modifier,
) {
    Box(modifier.fillMaxWidth().clip(RoundedCornerShape(u * 20)).background(card)) {
        Label(title, color, with(d) { (u * 15).toSp() }, Modifier.padding(start = u * 10, top = u * 8))
        FitText(
            value, font, weight, color,
            Modifier.padding(start = u * 10, end = u * 10, top = u * 30, bottom = u * 10).fillMaxSize(),
            suffix = unit,
        )
    }
}

/** 플립 카드 하나: 숫자는 카드 안쪽 상자에 꽉 맞춰 늘리고, 가운데에 가로 갈라진 선. */
@Composable
private fun FlipCard(
    digits: String, corner: String?, font: DigitFont, weight: Int, color: Color, card: Color,
    u: Dp, d: androidx.compose.ui.unit.Density, modifier: Modifier,
) {
    Box(modifier.fillMaxHeight().clip(RoundedCornerShape(u * 26)).background(card)) {
        FitText(
            digits, font, weight, color,
            Modifier.padding(start = u * 30, end = u * 30, top = u * 46, bottom = u * 30).fillMaxSize(),
            stretch = true,
        )
        Box(Modifier.align(Alignment.Center).fillMaxWidth().height(u * 4).background(Color.Black))
        if (corner != null) {
            Text(
                corner, color = color.copy(alpha = 0.75f), fontSize = with(d) { (u * 24).toSp() },
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = u * 14, top = u * 10),
            )
        }
    }
}

@Composable
private fun SquareButton(icon: ImageVector, label: String, tint: Color, bg: Color, u: Dp, onClick: () -> Unit) {
    Box(
        Modifier
            .size(width = u * 60, height = u * 44)
            .clip(RoundedCornerShape(u * 12))
            .background(bg)
            .clickable(onClickLabel = label, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, label, tint = tint, modifier = Modifier.size(u * 26))
    }
}

/**
 * 밝기 버튼: 막대로 바로 조절한다(폰 밝기 막대와 같은 눈금). '자동'을 누르면 다시 조도 센서를 따른다.
 * 손대지 않고 6초가 지나거나 바깥을 누르면 닫힌다.
 */
@Composable
private fun BrightnessPanel(
    settings: Settings, color: Color, u: Dp, d: androidx.compose.ui.unit.Density,
    onChange: () -> Unit, onClose: () -> Unit,
) {
    var value by remember { mutableFloatStateOf(settings.manualBrightness.takeIf { it >= 0 }?.toFloat() ?: 50f) }
    var auto by remember { mutableStateOf(settings.manualBrightness < 0) }
    var touched by remember { mutableIntStateOf(0) }
    LaunchedEffect(touched) { delay(6000); onClose() }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClose),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .width(u * 620)
                .clip(RoundedCornerShape(u * 24))
                .background(Color(0xFF2A2A2A))
                // 판 안을 누를 때는 닫히지 않게.
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { touched++ }
                .padding(horizontal = u * 28, vertical = u * 20),
            verticalArrangement = Arrangement.spacedBy(u * 10),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.LightMode, null, tint = color, modifier = Modifier.size(u * 30))
                Spacer(Modifier.width(u * 12))
                Text(
                    if (auto) "밝기 · 자동" else "밝기 ${value.roundToInt()}%",
                    color = color, fontSize = with(d) { (u * 28).toSp() }, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                Box(
                    Modifier
                        .clip(RoundedCornerShape(u * 12))
                        .background(if (auto) Color(0xFF4D8DFF) else Color(0xFF3A3A3A))
                        .clickable {
                            touched++
                            auto = !auto
                            settings.manualBrightness = if (auto) -1 else value.roundToInt()
                            onChange()
                        }
                        .padding(horizontal = u * 18, vertical = u * 10),
                ) {
                    Text("자동", color = Color.White, fontSize = with(d) { (u * 22).toSp() }, fontWeight = FontWeight.SemiBold)
                }
            }
            Slider(
                value = value,
                onValueChange = {
                    touched++
                    value = it
                    auto = false
                    settings.manualBrightness = it.roundToInt()
                    onChange()
                },
                valueRange = 0f..100f,
                colors = SliderDefaults.colors(thumbColor = color, activeTrackColor = color),
            )
        }
    }
}

/** 배터리 잔량. 충전 제어가 어긋나거나 과열이면 빨갛게. */
@Composable
private fun BatteryBadge(b: BatteryInfo, color: Color, warn: Color, size: Dp, d: androidx.compose.ui.unit.Density) {
    if (b.percent < 0) return
    val c = if (b.mismatch || b.overheat) warn else color
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            if (b.plugged) Icons.Filled.BatteryChargingFull else Icons.Filled.BatteryStd, null,
            tint = c, modifier = Modifier.size(size),
        )
        Text(
            "${b.percent}%" + (if (b.overheat) " ${b.tempC.roundToInt()}°" else ""),
            color = c, fontSize = with(d) { (size * 0.8f).toSp() }, fontWeight = FontWeight.SemiBold,
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

private val SHIFTS = listOf(
    0.dp to 0.dp, 16.dp to 0.dp, 11.dp to 11.dp, 0.dp to 16.dp,
    (-11).dp to 11.dp, (-16).dp to 0.dp, (-11).dp to (-11).dp, 0.dp to (-16).dp,
)
