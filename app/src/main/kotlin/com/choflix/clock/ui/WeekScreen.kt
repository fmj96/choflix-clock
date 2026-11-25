package com.choflix.clock.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.choflix.clock.data.DayForecast
import com.choflix.clock.data.HomeState
import com.choflix.clock.data.Settings
import com.choflix.clock.data.Weather
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

/** 주간 날씨: 오늘부터 7일. 30초 동안 손대지 않으면 시계로 돌아간다. */
@Composable
fun WeekScreen(settings: Settings, onClose: () -> Unit) {
    val weather by HomeState.weather.collectAsState()
    val place by HomeState.place.collectAsState()
    val color = Color(settings.color)
    val font = DigitFont.of(settings.font)

    // 손댈 때마다 시계를 새로 건다(기기 화면과 같은 방식).
    var touchedAt by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(touchedAt) {
        delay(IDLE_MS)
        onClose()
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        awaitPointerEvent(PointerEventPass.Initial)
                        touchedAt = System.currentTimeMillis()
                    }
                }
            }
            .padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) { Icon(Icons.Filled.ArrowBack, "시계로", tint = color) }
            Text("주간 날씨", color = color, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            if (place.isNotEmpty()) {
                Text("  $place", color = color.copy(alpha = 0.6f), fontSize = 16.sp, maxLines = 1)
            }
            Spacer(Modifier.weight(1f))
            Text("30초 동안 조작이 없으면 시계로 돌아갑니다", color = color.copy(alpha = 0.4f), fontSize = 13.sp)
        }
        Spacer(Modifier.height(8.dp))

        val days = weather?.daily.orEmpty()
        if (days.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("주간 예보를 아직 받지 못했습니다", color = color.copy(alpha = 0.6f), fontSize = 18.sp)
            }
            return@Column
        }
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val today = LocalDate.now()
            days.take(7).forEach { d ->
                DayCard(d, today, font, settings.infoWeight, color, Modifier.weight(1f).fillMaxHeight())
            }
        }
    }
}

@Composable
private fun DayCard(d: DayForecast, today: LocalDate, font: DigitFont, weight: Int, color: Color, modifier: Modifier) {
    val isToday = d.date == today
    Column(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (isToday) Color(0xFF3A3A3A) else Color(0xFF2A2A2A))
            .padding(vertical = 10.dp, horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        val name = when (d.date) {
            today -> "오늘"
            today.plusDays(1) -> "내일"
            else -> d.date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.KOREAN)
        }
        Text(name, color = color, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text("${d.date.monthValue}/${d.date.dayOfMonth}", color = color.copy(alpha = 0.6f), fontSize = 14.sp)
        // 하루 예보에는 지금 강수량·구름양이 없다 — 날씨 코드만으로 낮 그림을 그린다.
        WeatherIcon(
            Weather(currentC = d.maxC ?: 0.0, minC = d.minC, maxC = d.maxC, code = d.code, isDay = true),
            Modifier.size(56.dp), color,
        )
        Text(
            d.maxC?.let { "${it.roundToInt()}°" } ?: "--", color = color, fontSize = 30.sp,
            fontFamily = font.family, fontWeight = FontWeight(font.nearest(weight)),
        )
        Text(
            d.minC?.let { "${it.roundToInt()}°" } ?: "--", color = color.copy(alpha = 0.6f), fontSize = 24.sp,
            fontFamily = font.family, fontWeight = FontWeight(font.nearest(weight)),
        )
        Text(
            d.rainChance?.let { "☂ $it%" } ?: "", fontSize = 15.sp,
            color = if ((d.rainChance ?: 0) >= 50) Color(0xFF6AD0FF) else color.copy(alpha = 0.6f),
        )
    }
}

private const val IDLE_MS = 30_000L
