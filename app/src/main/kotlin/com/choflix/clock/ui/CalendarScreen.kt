package com.choflix.clock.ui

import android.app.DatePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.choflix.clock.data.Event
import com.choflix.clock.data.Events
import com.choflix.clock.data.Holiday
import com.choflix.clock.data.Holidays
import com.choflix.clock.data.Settings
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

private val SunRed = Color(0xFFFF6B6B)
private val SatBlue = Color(0xFF6AA8FF)
private val EventDot = Color(0xFFFFC857)

/**
 * 달력: 이번 달(앞뒤로 넘기기), 공휴일은 빨강. 날짜를 누르면 오른쪽에 그날 공휴일·일정이 나오고
 * 일정·기념일을 넣고 고칠 수 있다. 30초 동안 손대지 않으면 시계로 돌아간다(입력 창이 열려 있을 땐 멈춘다).
 */
@Composable
fun CalendarScreen(settings: Settings, onClose: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val color = Color(settings.color)
    val today = remember { LocalDate.now() }
    var month by remember { mutableStateOf(YearMonth.from(today)) }
    var selected by remember { mutableStateOf(today) }
    var holidays by remember { mutableStateOf<List<Holiday>>(emptyList()) }
    val events by Events.all.collectAsState()
    /** 입력 창: null 이면 닫힘, id 0 이면 새 일정. */
    var editing by remember { mutableStateOf<Event?>(null) }

    var touchedAt by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(touchedAt, editing) {
        if (editing != null) return@LaunchedEffect
        delay(IDLE_MS)
        onClose()
    }

    LaunchedEffect(Unit) { Events.load(context) }
    // 앞뒤 달의 날짜도 칸에 보이므로 앞뒤 해의 공휴일까지.
    LaunchedEffect(month.year) {
        holidays = (month.year - 1..month.year + 1).flatMap { Holidays.of(context, it) }
    }

    fun holidaysOn(d: LocalDate) = holidays.filter { it.date == d }
    fun eventsOn(d: LocalDate) = events.filter { it.occursOn(d) }

    MaterialTheme(colorScheme = darkColorScheme()) {
        Row(
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
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // ── 왼쪽: 달력 ──
            Column(Modifier.weight(1.6f).fillMaxHeight()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onClose) { Icon(Icons.Filled.ArrowBack, "시계로", tint = color) }
                    IconButton(onClick = { month = month.minusMonths(1) }) { Icon(Icons.Filled.ChevronLeft, "이전 달", tint = color) }
                    Text("${month.year}년 ${month.monthValue}월", color = color, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    IconButton(onClick = { month = month.plusMonths(1) }) { Icon(Icons.Filled.ChevronRight, "다음 달", tint = color) }
                    Spacer(Modifier.weight(1f))
                    if (month != YearMonth.from(today)) {
                        OutlinedButton(onClick = { month = YearMonth.from(today); selected = today }) { Text("오늘") }
                    }
                }
                // 요일 머리(일요일부터).
                Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                    WEEK.forEach { w ->
                        Text(
                            w.getDisplayName(TextStyle.SHORT, Locale.KOREAN),
                            color = when (w) { DayOfWeek.SUNDAY -> SunRed; DayOfWeek.SATURDAY -> SatBlue; else -> color.copy(alpha = 0.6f) },
                            fontSize = 13.sp, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        )
                    }
                }
                val first = month.atDay(1)
                val start = first.minusDays((first.dayOfWeek.value % 7).toLong())
                Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    for (row in 0 until 6) {
                        Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                            for (col in 0 until 7) {
                                val d = start.plusDays((row * 7 + col).toLong())
                                DayCell(
                                    d, inMonth = d.monthValue == month.monthValue, today = d == today, selected = d == selected,
                                    holiday = holidaysOn(d).firstOrNull(), events = eventsOn(d), color = color,
                                    modifier = Modifier.weight(1f).fillMaxHeight(),
                                ) {
                                    selected = d
                                    if (d.monthValue != month.monthValue) month = YearMonth.from(d)
                                }
                            }
                        }
                    }
                }
            }

            // ── 오른쪽: 고른 날 ──
            Column(
                Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(16.dp)).background(Color(0xFF1C1C1C)).padding(14.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${selected.monthValue}월 ${selected.dayOfMonth}일 " +
                            selected.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.KOREAN),
                        color = color, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { editing = Event(0, selected, "", false) }) {
                        Icon(Icons.Filled.Add, "일정 추가", tint = color)
                    }
                }
                Spacer(Modifier.height(6.dp))
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    holidaysOn(selected).forEach {
                        Text("● ${it.name}", color = SunRed, fontSize = 17.sp)
                    }
                    val list = eventsOn(selected)
                    list.forEach { e ->
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Color(0xFF2A2A2A))
                                .clickable { editing = e }.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(Modifier.size(8.dp).background(EventDot, CircleShape))
                            Spacer(Modifier.width(8.dp))
                            Text(e.title, color = color, fontSize = 17.sp, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                            if (e.yearly) {
                                val years = selected.year - e.date.year
                                Text(if (years > 0) "매년 · ${years}주년" else "매년", color = color.copy(alpha = 0.5f), fontSize = 13.sp)
                            }
                        }
                    }
                    if (list.isEmpty() && holidaysOn(selected).isEmpty()) {
                        Text("일정이 없습니다 — + 를 눌러 추가", color = color.copy(alpha = 0.4f), fontSize = 15.sp)
                    }
                }
            }
        }

        editing?.let { e ->
            EventDialog(
                e,
                onSave = { saved ->
                    scope.launch {
                        Events.save(context, if (saved.id == 0L) saved.copy(id = System.currentTimeMillis()) else saved)
                        selected = saved.date
                        month = YearMonth.from(saved.date)
                    }
                    editing = null
                },
                onDelete = { scope.launch { Events.delete(context, e.id) }; editing = null },
                onDismiss = { editing = null; touchedAt = System.currentTimeMillis() },
            )
        }
    }
}

@Composable
private fun DayCell(
    d: LocalDate, inMonth: Boolean, today: Boolean, selected: Boolean, holiday: Holiday?, events: List<Event>,
    color: Color, modifier: Modifier, onClick: () -> Unit,
) {
    val dayColor = when {
        holiday != null || d.dayOfWeek == DayOfWeek.SUNDAY -> SunRed
        d.dayOfWeek == DayOfWeek.SATURDAY -> SatBlue
        else -> color
    }.let { if (inMonth) it else it.copy(alpha = 0.3f) }
    Column(
        modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (today) Color(0xFF3A3A3A) else Color(0xFF1C1C1C))
            .then(if (selected) Modifier.border(2.dp, color.copy(alpha = 0.8f), RoundedCornerShape(8.dp)) else Modifier)
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 2.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("${d.dayOfMonth}", color = dayColor, fontSize = 15.sp, fontWeight = if (today) FontWeight.Bold else FontWeight.Normal)
            Spacer(Modifier.weight(1f))
            if (events.isNotEmpty()) Box(Modifier.size(6.dp).background(EventDot.copy(alpha = if (inMonth) 1f else 0.3f), CircleShape))
        }
        // 칸 안에는 한 줄만: 공휴일 이름이 먼저, 없으면 첫 일정.
        val line = holiday?.name ?: events.firstOrNull()?.title
        if (line != null) {
            Text(
                line, color = (if (holiday != null) SunRed else color).copy(alpha = if (inMonth) 0.85f else 0.3f),
                fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, lineHeight = 11.sp,
            )
        }
    }
}

/** 일정·기념일 넣기/고치기. 날짜는 시스템 날짜 고르기 창으로 바꾼다. */
@Composable
private fun EventDialog(e: Event, onSave: (Event) -> Unit, onDelete: () -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var title by remember { mutableStateOf(e.title) }
    var date by remember { mutableStateOf(e.date) }
    var yearly by remember { mutableStateOf(e.yearly) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (e.id == 0L) "일정 추가" else "일정 고치기") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(title, { title = it }, label = { Text("내용") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("날짜", modifier = Modifier.width(56.dp))
                    OutlinedButton(onClick = {
                        DatePickerDialog(context, { _, y, m, dd -> date = LocalDate.of(y, m + 1, dd) }, date.year, date.monthValue - 1, date.dayOfMonth).show()
                    }) { Text("${date.year}-%02d-%02d".format(date.monthValue, date.dayOfMonth)) }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("매년 반복 (기념일·생일)")
                        Text("해마다 같은 날 표시하고 몇 주년인지 알려 줍니다", fontSize = 12.sp, color = Color.White.copy(alpha = 0.5f))
                    }
                    Switch(yearly, { yearly = it })
                }
            }
        },
        confirmButton = {
            TextButton(enabled = title.isNotBlank(), onClick = { onSave(e.copy(title = title.trim(), date = date, yearly = yearly)) }) { Text("저장") }
        },
        dismissButton = {
            Row {
                if (e.id != 0L) TextButton(onClick = onDelete) { Text("삭제", color = SunRed) }
                TextButton(onClick = onDismiss) { Text("취소") }
            }
        },
    )
}

private val WEEK = listOf(
    DayOfWeek.SUNDAY, DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
    DayOfWeek.THURSDAY, DayOfWeek.FRIDAY, DayOfWeek.SATURDAY,
)

private const val IDLE_MS = 30_000L
