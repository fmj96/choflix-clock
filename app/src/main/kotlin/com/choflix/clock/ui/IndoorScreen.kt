package com.choflix.clock.ui

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.choflix.clock.data.HomeState
import com.choflix.clock.data.IndoorHistory
import com.choflix.clock.data.IndoorSample
import com.choflix.clock.data.Settings
import com.choflix.clock.data.TuyaCloudClient
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.ZoneId
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

private val HumColor = Color(0xFF6AD0FF)

/**
 * 실내 온도·습도 그래프(24시간 / 7일). 폰에 쌓은 기록으로 그리고, 앞부분이 모자라면 Tuya 에 남은
 * 센서 기록으로 한 번 채워 폰 기록에 합쳐 둔다. 30초 동안 손대지 않으면 시계로 돌아간다.
 */
@Composable
fun IndoorScreen(settings: Settings, onClose: () -> Unit) {
    val context = LocalContext.current
    val indoor by HomeState.indoor.collectAsState()
    val color = Color(settings.color)
    var days by remember { mutableStateOf(1) }
    var samples by remember { mutableStateOf<List<IndoorSample>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var note by remember { mutableStateOf("") }

    var touchedAt by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(touchedAt) {
        delay(IDLE_MS)
        onClose()
    }

    LaunchedEffect(days) {
        loading = true
        val now = System.currentTimeMillis()
        val from = now - days * 24 * 3600_000L
        var local = IndoorHistory.load(context)
        // 폰 기록이 기간 앞쪽(1시간 넘게)을 못 덮으면 Tuya 기록으로 채운다.
        val first = local.firstOrNull { it.timeMs >= from }?.timeMs ?: now
        val id = settings.sensorDeviceId
        val client = TuyaCloudClient.from(settings)
        if (first - from > 3600_000L && client != null && id.isNotEmpty()) {
            runCatching { client.indoorHistory(id, from, first) }
                .onSuccess {
                    IndoorHistory.merge(context, it)
                    local = IndoorHistory.load(context)
                    note = if (it.isEmpty()) "Tuya 에 남은 기록이 없어 이 앱이 쌓은 기록만 보여 줍니다" else ""
                }
                .onFailure { note = "Tuya 기록을 못 받았습니다(${it.message}) — 이 앱이 쌓은 기록만 보여 줍니다" }
        }
        samples = local.filter { it.timeMs >= from }
        loading = false
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
            Text("실내 온도 / 습도", color = color, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(16.dp))
            Text(
                "${indoor?.temperatureC?.let { "%.1f".format(it) } ?: "--"}°C", color = color, fontSize = 20.sp,
            )
            Spacer(Modifier.width(10.dp))
            Text("${indoor?.humidity ?: "--"}%", color = HumColor, fontSize = 20.sp)
            Spacer(Modifier.weight(1f))
            RangeChip("24시간", days == 1, color) { days = 1 }
            Spacer(Modifier.width(8.dp))
            RangeChip("7일", days == 7, color) { days = 7 }
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 12.dp)) {
            Text(note.ifEmpty { "30초 동안 조작이 없으면 시계로 돌아갑니다" }, color = color.copy(alpha = 0.4f), fontSize = 13.sp)
        }
        Spacer(Modifier.height(6.dp))
        Box(Modifier.weight(1f).padding(bottom = 4.dp)) {
            when {
                loading -> CircularProgressIndicator(Modifier.align(Alignment.Center).size(32.dp), color = color)
                samples.size < 2 -> Text(
                    "아직 기록이 없습니다 — 5분마다 쌓입니다", color = color.copy(alpha = 0.6f), fontSize = 18.sp,
                    modifier = Modifier.align(Alignment.Center),
                )
                else -> Chart(samples, days, color, Modifier.fillMaxSize())
            }
        }
    }
}

@Composable
private fun RangeChip(label: String, selected: Boolean, color: Color, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) Color(0xFF3A3A3A) else Color(0xFF1A1A1A))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text(label, color = if (selected) color else color.copy(alpha = 0.5f), fontSize = 16.sp)
    }
}

/** 왼쪽 축 = 온도(글자 색), 오른쪽 축 = 습도(하늘색). 가로는 지금부터 거꾸로 days 일. */
@Composable
private fun Chart(samples: List<IndoorSample>, days: Int, color: Color, modifier: Modifier) {
    val temps = samples.mapNotNull { s -> s.temperatureC?.let { s.timeMs to it } }
    val hums = samples.mapNotNull { s -> s.humidity?.let { s.timeMs to it } }
    val label = remember { Paint(Paint.ANTI_ALIAS_FLAG) }
    val zone = ZoneId.systemDefault()

    Canvas(modifier) {
        val end = System.currentTimeMillis()
        val start = end - days * 24 * 3600_000L
        val left = 56.dp.toPx()
        val right = size.width - 56.dp.toPx()
        val top = 10.dp.toPx()
        val bottom = size.height - 26.dp.toPx()
        label.textSize = 13.sp.toPx()

        fun x(t: Long) = left + (right - left) * ((t - start).toFloat() / (end - start))
        // 축 범위: 값보다 조금 넓게, 보기 좋은 정수로.
        val tLo = floor((temps.minOfOrNull { it.second } ?: 20.0) - 1).toFloat()
        val tHi = ceil((temps.maxOfOrNull { it.second } ?: 25.0) + 1).toFloat()
        val hLo = (floor((hums.minOfOrNull { it.second } ?: 40.0) / 5) * 5 - 5).toFloat().coerceAtLeast(0f)
        val hHi = (ceil((hums.maxOfOrNull { it.second } ?: 60.0) / 5) * 5 + 5).toFloat().coerceAtMost(100f)
        fun yT(v: Double) = bottom - (bottom - top) * ((v.toFloat() - tLo) / (tHi - tLo))
        fun yH(v: Double) = bottom - (bottom - top) * ((v.toFloat() - hLo) / (hHi - hLo))

        // 가로 눈금 4칸 + 양쪽 축 숫자.
        val grid = Color.White.copy(alpha = 0.12f)
        for (i in 0..4) {
            val y = top + (bottom - top) * i / 4f
            drawLine(grid, Offset(left, y), Offset(right, y), 1.dp.toPx())
            val tv = tHi - (tHi - tLo) * i / 4f
            val hv = hHi - (hHi - hLo) * i / 4f
            text(label, "%.1f°".format(tv), left - 6.dp.toPx(), y, color, Paint.Align.RIGHT)
            text(label, "${hv.roundToInt()}%", right + 6.dp.toPx(), y, HumColor, Paint.Align.LEFT)
        }
        // 세로 눈금: 24시간은 3시간마다(시각), 7일은 자정마다(날짜).
        val step = if (days == 1) 3 * 3600_000L else 24 * 3600_000L
        var t = Instant.ofEpochMilli(start).atZone(zone).let {
            if (days == 1) it.withMinute(0).withSecond(0).withNano(0).plusHours((3 - it.hour % 3).toLong())
            else it.toLocalDate().plusDays(1).atStartOfDay(zone)
        }.toInstant().toEpochMilli()
        while (t < end) {
            val xx = x(t)
            drawLine(grid, Offset(xx, top), Offset(xx, bottom), 1.dp.toPx())
            val z = Instant.ofEpochMilli(t).atZone(zone)
            val s = if (days == 1) "${z.hour}시" else "${z.monthValue}/${z.dayOfMonth}"
            text(label, s, xx, bottom + 18.dp.toPx(), color.copy(alpha = 0.6f), Paint.Align.CENTER)
            t += step
        }

        line(temps, ::x, ::yT, color)
        line(hums, ::x, ::yH, HumColor)
    }
}

/** 점들을 잇는다. 2시간 넘게 끊긴 곳은 잇지 않는다(앱·센서가 꺼져 있던 때). 센서는 값이 바뀔 때만 보고하기도 해 넉넉히. */
private fun DrawScope.line(
    pts: List<Pair<Long, Double>>, x: (Long) -> Float, y: (Double) -> Float, c: Color,
) {
    if (pts.size < 2) return
    val path = Path()
    var prev: Long? = null
    for ((t, v) in pts) {
        if (prev == null || t - prev > 2 * 3600_000L) path.moveTo(x(t), y(v)) else path.lineTo(x(t), y(v))
        prev = t
    }
    drawPath(path, c, style = Stroke(width = 2.5.dp.toPx()))
}

private fun DrawScope.text(p: Paint, s: String, x: Float, y: Float, c: Color, align: Paint.Align) {
    p.color = c.toArgb()
    p.textAlign = align
    drawContext.canvas.nativeCanvas.drawText(s, x, y + p.textSize / 3f, p)
}

private const val IDLE_MS = 30_000L
