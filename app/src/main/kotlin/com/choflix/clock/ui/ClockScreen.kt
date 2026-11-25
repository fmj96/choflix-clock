package com.choflix.clock.ui

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import kotlin.math.abs
import com.choflix.clock.data.Air
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Animatable
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
import androidx.compose.material.icons.outlined.SentimentVeryDissatisfied
import androidx.compose.material.icons.outlined.SentimentDissatisfied
import androidx.compose.material.icons.outlined.SentimentSatisfied
import androidx.compose.material.icons.outlined.SentimentVerySatisfied
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
 *  왼쪽: 날씨(큰 카드 — 지금 기온, 아래에 최저/최고, 누르면 주간 날씨) / 미세먼지 / 실내 온도·습도
 *  오른쪽 윗줄: 날짜 · (오른쪽 끝) 버튼(전구=기기, 해=밝기, 톱니바퀴=설정) · 와이파이·배터리
 *  오른쪽 아래: 시 카드(왼쪽 위 AM/PM) | 분 카드 — 숫자는 카드에 꽉 맞춰 늘린다.
 * 화면 크기는 시안(1000×450)을 기준으로 한 단위 u 로 비례해 잡는다.
 */
@Composable
fun ClockScreen(
    settings: Settings,
    night: Boolean,
    onOpenDevices: () -> Unit,
    onOpenWeather: () -> Unit,
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
    val air by HomeState.air.collectAsState()
    val battery by HomeState.battery.collectAsState()
    val online = rememberOnline()
    val update by Updater.available.collectAsState()

    val font = DigitFont.of(settings.font)
    val main = if (night && settings.dimAmber) NightAmber else Color(settings.color)
    // 카드는 밤에 더 어둡게 — 넓은 회색 면이 어두운 방에서 밝게 떠 보이지 않게.
    val card = if (night) Color(0xFF151515) else Color(0xFF2A2A2A)
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
            // ── 왼쪽: 날씨 / 미세먼지 / 실내 온도·습도 ──
            Column(Modifier.width(u * 226).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(u * 8)) {
                // 누르면 주간 날씨.
                Box(
                    Modifier.fillMaxWidth().weight(2.2f).clip(RoundedCornerShape(u * 20)).background(card)
                        .clickable(onClickLabel = "주간 날씨", onClick = onOpenWeather),
                ) {
                    Label("날씨", main, (u * 17).sp(), Modifier.padding(start = u * 12, top = u * 12))
                    Box(Modifier.align(Alignment.TopEnd).padding(top = u * 6, end = u * 8).size(u * 66)) {
                        weather?.let { WeatherIcon(it, Modifier.fillMaxSize(), main) }
                    }
                    FitText(
                        weather?.currentC?.roundToInt()?.toString() ?: "--", font, settings.infoWeight, main,
                        Modifier.padding(start = u * 14, end = u * 14, top = u * 78, bottom = u * 40).fillMaxSize(),
                        stretch = true,
                        // 크기는 "-20" 기준으로 고정 — 한 자리일 때 숫자가 옆으로 퍼지지 않게. 빼기는 작게.
                        reference = "-20",
                        minusScale = 0.6f,
                        // °C 를 숫자에 붙여 둘을 한 덩어리로 카드 가운데에 둔다.
                        suffix = "°C",
                    )
                    // 맨 아래 작게: 오늘 최저/최고.
                    val lo = weather?.minC?.roundToInt()
                    val hi = weather?.maxC?.roundToInt()
                    if (lo != null && hi != null) {
                        Text(
                            "최저 $lo° / 최고 $hi°", color = main.copy(alpha = 0.7f), fontSize = (u * 17).sp(),
                            fontWeight = FontWeight(font.nearest(settings.infoWeight)), maxLines = 1,
                            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = u * 10),
                        )
                    }
                }
                DustCard(air, font, settings.infoWeight, main, card, u, d, Modifier.weight(1f))
                // 소수점은 빼고 온도·습도를 한 줄에 — 글자는 칸에 맞춰 줄어든다.
                SmallCard(
                    "실내 온도 / 습도",
                    "${indoor?.temperatureC?.roundToInt() ?: "--"}°C / ${indoor?.humidity ?: "--"}%", "",
                    font, settings.infoWeight, main, card, u, d, Modifier.weight(1f),
                    small = setOf('°', 'C', '%'), half = setOf('/'),
                )
            }

            // ── 오른쪽: 윗줄과 플립 카드 ──
            Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(u * 10)) {
                // 윗줄도 아래 플립 카드처럼 반씩 나눈다: 왼쪽 반(시 카드 위)은 날짜·요일 상자.
                Row(Modifier.fillMaxWidth().height(u * 46), horizontalArrangement = Arrangement.spacedBy(u * 16)) {
                    // 날짜 상자: 폭은 시 카드와 같고 높이·모양·색은 버튼과 같게.
                    Box(
                        Modifier.weight(1f).align(Alignment.CenterVertically).height(u * 44)
                            .clip(RoundedCornerShape(u * 12)).background(card),
                        contentAlignment = Alignment.Center,
                    ) {
                        // 상자에 꽉 맞춰(비율 유지) 크게 — 위아래 여백은 조금만.
                        FitText(
                            "%04d-%02d-%02d %s".format(
                                now.year, now.monthValue, now.dayOfMonth,
                                now.dayOfWeek.getDisplayName(DateTextStyle.SHORT, Locale.ENGLISH).uppercase(Locale.ENGLISH),
                            ),
                            font, settings.infoWeight, main,
                            Modifier.fillMaxSize().padding(horizontal = u * 10, vertical = u * 7),
                        )
                    }
                    // 오른쪽 끝: 버튼(전구=기기, 해=밝기, 톱니바퀴=설정) 다음에 와이파이·배터리.
                    Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.CenterEnd) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(u * 10),
                        ) {
                            SquareButton(Icons.Outlined.Lightbulb, "기기", main, card, u, onOpenDevices)
                            SquareButton(Icons.Outlined.LightMode, "밝기", main, card, u) { showBrightness = true }
                            Box {
                                SquareButton(Icons.Outlined.Settings, "설정", main, card, u, onOpenSettings)
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

/** 미세먼지 카드: 등급(좋음·보통·나쁨·매우나쁨)과 오른쪽에 표정. */
@Composable
private fun DustCard(
    air: Air?, font: DigitFont, weight: Int, color: Color, card: Color,
    u: Dp, d: androidx.compose.ui.unit.Density, modifier: Modifier,
) {
    Box(modifier.fillMaxWidth().clip(RoundedCornerShape(u * 20)).background(card)) {
        Label("미세먼지", color, with(d) { (u * 15).toSp() }, Modifier.padding(start = u * 10, top = u * 8))
        Row(
            Modifier.fillMaxSize().padding(start = u * 12, end = u * 92, top = u * 24, bottom = u * 4),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val g = air?.grade
            Text(
                g?.let { DustNames[it] } ?: "--", color = color, fontSize = with(d) { (u * if (g == 3) 28 else 36).toSp() },
                fontFamily = font.family, fontWeight = FontWeight(font.nearest(weight)), maxLines = 1,
                modifier = Modifier.weight(1f),
            )
        }
        // 표정은 카드 높이를 거의 채우게 오른쪽에 크게.
        air?.grade?.let { g ->
            Icon(
                dustFace(g), null, tint = DustColors[g],
                modifier = Modifier.align(Alignment.CenterEnd).padding(end = u * 6).size(u * 84),
            )
        }
    }
}

internal val DustNames = listOf("좋음", "보통", "나쁨", "매우나쁨")

/** 표정 색은 글자 색 설정과 상관없이 고정 — 좋음 파랑, 보통 초록, 나쁨·매우나쁨 빨강. */
internal val DustColors = listOf(Color(0xFF4D8DFF), Color(0xFF5BE37D), WarnRed, WarnRed)

internal fun dustFace(grade: Int): ImageVector = when (grade) {
    0 -> Icons.Outlined.SentimentVerySatisfied
    1 -> Icons.Outlined.SentimentSatisfied
    2 -> Icons.Outlined.SentimentDissatisfied
    else -> Icons.Outlined.SentimentVeryDissatisfied
}

/** 실내 온도·습도 카드: 제목은 왼쪽 위, 숫자는 가운데(비율 유지). */
@Composable
private fun SmallCard(
    title: String, value: String, unit: String, font: DigitFont, weight: Int, color: Color, card: Color,
    u: Dp, d: androidx.compose.ui.unit.Density, modifier: Modifier,
    small: Set<Char> = emptySet(), half: Set<Char> = emptySet(),
) {
    Box(modifier.fillMaxWidth().clip(RoundedCornerShape(u * 20)).background(card)) {
        Label(title, color, with(d) { (u * 15).toSp() }, Modifier.padding(start = u * 10, top = u * 8))
        FitText(
            value, font, weight, color,
            Modifier.padding(start = u * 10, end = u * 10, top = u * 30, bottom = u * 10).fillMaxSize(),
            suffix = unit, small = small, half = half,
        )
    }
}

/** 플립 카드 하나: 숫자는 카드 안쪽 상자에 꽉 맞춰 늘리고, 가운데에 가로 갈라진 선. */
@Composable
private fun FlipCard(
    digits: String, corner: String?, font: DigitFont, weight: Int, color: Color, card: Color,
    u: Dp, d: androidx.compose.ui.unit.Density, modifier: Modifier,
) {
    // 숫자가 바뀌면 진짜 플립 시계처럼 넘긴다: 옛 숫자 위 반이 가운데 축으로 접혀 내려오고(0~½),
    // 이어서 새 숫자 아래 반이 펴지며 내려앉는다(½~1). 그동안 뒤에는 새 위 반과 옛 아래 반이 보인다.
    var shown by remember { mutableStateOf(digits) }
    var prev by remember { mutableStateOf(digits) }
    val flip = remember { Animatable(1f) }
    LaunchedEffect(digits) {
        if (digits == shown) return@LaunchedEffect
        prev = shown
        shown = digits
        flip.snapTo(0f)
        flip.animateTo(1f, tween(700, easing = FastOutSlowInEasing))
    }
    val p = flip.value

    Box(modifier.fillMaxHeight()) {
        val face = @Composable { text: String, top: Boolean, angle: Float ->
            FlipHalf(text, top, angle, font, weight, color, card, u)
        }
        face(shown, true, 0f)
        face(if (p < 1f) prev else shown, false, 0f)
        if (p < 0.5f) face(prev, true, -180f * p)
        else if (p < 1f) face(shown, false, 180f * (1f - p))

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

/** 플립 카드의 위 또는 아래 반쪽. angle 만큼 가운데 가로축으로 돌리고, 기울수록 어둡게 그린다. */
@Composable
private fun FlipHalf(
    digits: String, top: Boolean, angle: Float, font: DigitFont, weight: Int, color: Color, card: Color, u: Dp,
) {
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer {
                rotationX = angle
                cameraDistance = 14f * density
            }
            .drawWithContent {
                val half = size.height / 2f
                clipRect(top = if (top) 0f else half, bottom = if (top) half else size.height) {
                    this@drawWithContent.drawContent()
                    if (angle != 0f) drawRect(Color.Black.copy(alpha = 0.45f * abs(angle) / 90f))
                }
            }
            .clip(RoundedCornerShape(u * 26))
            .background(card)
    ) {
        FitText(
            digits, font, weight, color,
            Modifier.padding(start = u * 30, end = u * 30, top = u * 46, bottom = u * 30).fillMaxSize(),
            stretch = true,
        )
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
