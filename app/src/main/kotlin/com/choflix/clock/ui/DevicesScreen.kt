package com.choflix.clock.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.choflix.clock.data.HomeState
import com.choflix.clock.data.Settings
import com.choflix.clock.data.TuyaCloudClient
import com.choflix.clock.data.TuyaDevice
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val OnColor = Color(0xFF3D8BFF)
private val CardColor = Color(0xFF14223A)

/**
 * Tuya 기기 바로 켜기/끄기. 시계 오른쪽 위 전구를 누르면 열린다.
 * [IDLE_MS] 동안 손대지 않으면 저절로 시계로 돌아간다.
 */
@Composable
fun DevicesScreen(settings: Settings, onClose: () -> Unit) {
    val scope = rememberCoroutineScope()
    val devices by HomeState.devices.collectAsState()
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    /** 명령을 보내 놓고 답을 기다리는 스위치("기기id/코드"). */
    val pending = remember { mutableStateMapOf<String, Boolean>() }

    // 손댈 때마다 시계를 새로 건다. 이벤트를 소비하지 않고 엿보기만 한다(Initial 단계).
    var touchedAt by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(touchedAt) {
        delay(IDLE_MS)
        onClose()
    }

    fun reload() {
        val c = TuyaCloudClient.from(settings) ?: run { error = "설정에서 Tuya Access ID와 Secret을 먼저 넣어 주세요"; return }
        loading = true
        scope.launch {
            runCatching { c.devices() }
                // 켜고 끌 수도, 읽을 값도 없는 기기(IR 리모컨으로 만든 가상 기기 등)는 뺀다.
                .onSuccess { l ->
                    HomeState.devices.value = l.filter { it.switches.isNotEmpty() || it.temperatureC != null }
                    error = null
                }
                .onFailure { error = "기기 목록을 못 받았습니다: ${it.message}" }
            loading = false
        }
    }
    LaunchedEffect(Unit) { reload() }

    fun toggle(d: TuyaDevice, code: String, on: Boolean) {
        val c = TuyaCloudClient.from(settings) ?: return
        val key = "${d.id}/$code"
        if (pending.containsKey(key)) return
        pending[key] = on
        scope.launch {
            val ok = runCatching { c.setPlug(d.id, code, on) }
                .onFailure { error = "${d.name}: ${it.message}" }
                .getOrDefault(false)
            if (ok) {
                error = null
                // 기기에 닿기까지 잠깐 걸린다 — 조금 있다 실제 상태를 다시 읽어 맞춘다.
                delay(800)
                val sw = runCatching { c.switches(d.id) }.getOrNull()
                HomeState.devices.value = HomeState.devices.value.map {
                    if (it.id != d.id) it
                    else it.copy(switches = sw ?: it.switches.map { s -> if (s.code == code) s.copy(on = on) else s })
                }
            }
            pending.remove(key)
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF0B1424))
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        awaitPointerEvent(PointerEventPass.Initial)
                        touchedAt = System.currentTimeMillis()
                    }
                }
            }
            .padding(horizontal = 24.dp, vertical = 16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) { Icon(Icons.Filled.ArrowBack, "시계로", tint = Color.White) }
            Text("기기", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            Text("30초 동안 조작이 없으면 시계로 돌아갑니다", color = Color.White.copy(alpha = 0.4f), fontSize = 13.sp)
            Spacer(Modifier.width(8.dp))
            if (loading) CircularProgressIndicator(Modifier.size(24.dp), color = OnColor, strokeWidth = 2.dp)
            else IconButton(onClick = { reload() }) { Icon(Icons.Filled.Refresh, "새로고침", tint = Color.White) }
        }
        error?.let { Text(it, color = Color(0xFFFF8A8A), modifier = Modifier.padding(vertical = 6.dp)) }
        if (devices.isEmpty() && !loading && error == null) {
            Text("등록된 기기가 없습니다", color = Color.White.copy(alpha = 0.6f), modifier = Modifier.padding(16.dp))
        }

        LazyVerticalGrid(
            columns = GridCells.Adaptive(200.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(top = 8.dp),
        ) {
            items(devices, key = { it.id }) { d ->
                DeviceCard(d, pending) { code, on -> toggle(d, code, on) }
            }
        }
    }
}

@Composable
private fun DeviceCard(d: TuyaDevice, pending: Map<String, Boolean>, onToggle: (String, Boolean) -> Unit) {
    // 스위치가 하나면 카드 전체가 버튼. 여러 구(멀티탭 등)면 구마다 작은 버튼.
    val single = d.switches.singleOrNull()
    val anyOn = d.switches.any { pending["${d.id}/${it.code}"] ?: it.on }
    val shape = RoundedCornerShape(16.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .height(130.dp)
            .clip(shape)
            .background(if (anyOn && d.online) OnColor.copy(alpha = 0.28f) else CardColor)
            .border(1.dp, if (anyOn && d.online) OnColor else Color.White.copy(alpha = 0.08f), shape)
            .then(
                if (single != null && d.online) Modifier.clickable {
                    onToggle(single.code, !(pending["${d.id}/${single.code}"] ?: single.on))
                } else Modifier
            )
            .padding(14.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (d.switches.isEmpty()) Icons.Filled.Sensors else Icons.Filled.PowerSettingsNew, null,
                tint = if (anyOn && d.online) OnColor else Color.White.copy(alpha = 0.6f),
                modifier = Modifier.size(26.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                d.name, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
                maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
        }
        when {
            !d.online -> Text("오프라인", color = Color(0xFFFF8A8A), fontSize = 14.sp)
            d.switches.isEmpty() -> Text(
                listOfNotNull(
                    d.temperatureC?.let { "%.1f°C".format(java.util.Locale.US, it) },
                    d.humidity?.let { "$it%" },
                ).joinToString("  "),
                color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold,
            )
            single != null -> {
                val busy = pending.containsKey("${d.id}/${single.code}")
                val on = pending["${d.id}/${single.code}"] ?: single.on
                Text(
                    (if (on) "켜짐" else "꺼짐") + (if (busy) " …" else ""),
                    color = if (on) OnColor else Color.White.copy(alpha = 0.6f),
                    fontSize = 22.sp, fontWeight = FontWeight.Bold,
                )
            }
            else -> Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                d.switches.forEachIndexed { i, s ->
                    val on = pending["${d.id}/${s.code}"] ?: s.on
                    Box(
                        Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (on) OnColor else Color.White.copy(alpha = 0.12f))
                            .clickable { onToggle(s.code, !on) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("${i + 1}", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

private const val IDLE_MS = 30_000L
