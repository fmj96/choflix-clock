package com.choflix.clock.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.choflix.clock.BuildConfig
import com.choflix.clock.data.HomeState
import com.choflix.clock.data.Place
import com.choflix.clock.data.Settings
import com.choflix.clock.data.TuyaCloudClient
import com.choflix.clock.data.TuyaRegion
import com.choflix.clock.data.WeatherRepository
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(settings: Settings, onClose: () -> Unit) {
    MaterialTheme(colorScheme = darkColorScheme()) {
        val scope = rememberCoroutineScope()
        val battery by HomeState.battery.collectAsState()
        val tuyaError by HomeState.tuyaError.collectAsState()

        var use24h by remember { mutableStateOf(settings.use24h) }
        var place by remember { mutableStateOf(settings.place) }
        var query by remember { mutableStateOf("") }
        var results by remember { mutableStateOf<List<Place>>(emptyList()) }

        var region by remember { mutableStateOf(settings.tuyaRegion) }
        var accessId by remember { mutableStateOf(settings.tuyaAccessId) }
        var secret by remember { mutableStateOf(settings.tuyaSecret) }
        var sensorId by remember { mutableStateOf(settings.sensorDeviceId) }
        var plugId by remember { mutableStateOf(settings.plugDeviceId) }
        var switchCode by remember { mutableStateOf(settings.plugSwitchCode) }
        var testResult by remember { mutableStateOf("") }

        var control by remember { mutableStateOf(settings.chargeControl) }
        var low by remember { mutableStateOf(settings.lowPercent.toString()) }
        var high by remember { mutableStateOf(settings.highPercent.toString()) }
        var maxTemp by remember { mutableStateOf(settings.maxTempC.toString()) }

        fun saveTuya() {
            settings.tuyaRegion = region
            settings.tuyaAccessId = accessId
            settings.tuyaSecret = secret
            settings.sensorDeviceId = sensorId
            settings.plugDeviceId = plugId
            settings.plugSwitchCode = switchCode
            HomeState.refresh.value++
        }

        Column(
            Modifier
                .fillMaxSize()
                .background(Color(0xFF0B1424))
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 32.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("설정", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.weight(1f))
                Button(onClick = onClose) { Text("시계로") }
            }

            Section("시계")
            Toggle("24시간 표시", use24h) { use24h = it; settings.use24h = it }

            Section("날씨 위치")
            Text(
                "지금: " + (settings.place.ifEmpty { "자동(인터넷 주소로 대략)" }),
                color = Color.White.copy(alpha = 0.8f),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(query, { query = it }, label = { Text("지역 이름 (예: 수원)") },
                    singleLine = true, modifier = Modifier.weight(1f))
                Spacer(Modifier.width(8.dp))
                Button(onClick = { scope.launch { results = WeatherRepository.searchPlace(query) } }) { Text("검색") }
                Spacer(Modifier.width(8.dp))
                OutlinedButton(onClick = {
                    settings.place = ""; settings.lat = null; settings.lon = null
                    place = ""; results = emptyList(); HomeState.refresh.value++
                }) { Text("자동") }
            }
            results.forEach { p ->
                Text(
                    p.name, color = Color.White,
                    modifier = Modifier.fillMaxWidth().clickable {
                        settings.place = p.name; settings.lat = p.lat; settings.lon = p.lon
                        place = p.name; results = emptyList(); HomeState.refresh.value++
                    }.padding(vertical = 8.dp),
                )
            }

            Section("Tuya Cloud (실내 온·습도, 스마트 플러그)")
            RegionPicker(region) { region = it }
            Field("Access ID", accessId) { accessId = it }
            Field("Access Secret", secret, secret = true) { secret = it }
            Field("온습도 센서 Device ID", sensorId) { sensorId = it }
            Field("스마트 플러그 Device ID", plugId) { plugId = it }
            Field("플러그 스위치 코드", switchCode) { switchCode = it }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { saveTuya(); testResult = "저장했습니다" }) { Text("저장") }
                OutlinedButton(onClick = {
                    saveTuya()
                    testResult = "확인 중…"
                    scope.launch {
                        val c = TuyaCloudClient.from(settings)
                        testResult = when {
                            c == null -> "Access ID와 Secret을 넣어 주세요"
                            sensorId.isBlank() -> "센서 Device ID가 없습니다"
                            else -> runCatching { c.indoor(sensorId) }.fold(
                                { "센서 OK: ${it.temperatureC ?: "?"}°C, ${it.humidity ?: "?"}%" },
                                { "실패: ${it.message}" },
                            )
                        }
                    }
                }) { Text("센서 시험") }
                OutlinedButton(onClick = { saveTuya(); plugTest(settings, true, scope) { testResult = it } }) { Text("플러그 켜기") }
                OutlinedButton(onClick = { saveTuya(); plugTest(settings, false, scope) { testResult = it } }) { Text("플러그 끄기") }
            }
            if (testResult.isNotEmpty()) Text(testResult, color = Color(0xFF9CC9FF))
            tuyaError?.let { Text("최근 오류: $it", color = Color(0xFFFF8A8A)) }

            Section("배터리 구간 충전")
            Text(
                if (battery.percent < 0) "배터리 정보 없음"
                else "지금 ${battery.percent}% · ${"%.1f".format(battery.tempC)}°C · " +
                    (if (battery.plugged) "전원 들어옴" else "전원 없음") +
                    (if (battery.mismatch) " · 플러그가 명령을 따르지 않습니다!" else ""),
                color = if (battery.mismatch || battery.overheat) Color(0xFFFF8A8A) else Color.White.copy(alpha = 0.8f),
            )
            Toggle("구간 충전 켜기 (플러그로 충전을 켜고 끔)", control) { control = it; settings.chargeControl = it }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumberField("충전 시작 %", low, Modifier.weight(1f)) {
                    low = it; it.toIntOrNull()?.takeIf { v -> v in 5..95 }?.let { v -> settings.lowPercent = v }
                }
                NumberField("충전 멈춤 %", high, Modifier.weight(1f)) {
                    high = it; it.toIntOrNull()?.takeIf { v -> v in 10..100 }?.let { v -> settings.highPercent = v }
                }
                NumberField("과열 차단 °C", maxTemp, Modifier.weight(1f)) {
                    maxTemp = it; it.toIntOrNull()?.takeIf { v -> v in 30..50 }?.let { v -> settings.maxTempC = v }
                }
            }

            HorizontalDivider(Modifier.padding(top = 12.dp))
            Text(
                "Choflix Clock v${BuildConfig.VERSION_NAME} · github.com/fmj96/choflix-clock\n" +
                    "시계 화면을 길게 누르면 이 설정이 열립니다.",
                color = Color.White.copy(alpha = 0.5f), fontSize = 13.sp,
            )
            Spacer(Modifier.height(40.dp))
        }
    }
}

private fun plugTest(settings: Settings, on: Boolean, scope: kotlinx.coroutines.CoroutineScope, show: (String) -> Unit) {
    val c = TuyaCloudClient.from(settings) ?: return show("Access ID와 Secret을 넣어 주세요")
    if (settings.plugDeviceId.isBlank()) return show("플러그 Device ID가 없습니다")
    show("보내는 중…")
    scope.launch {
        show(
            runCatching { c.setPlug(settings.plugDeviceId, settings.plugSwitchCode, on) }.fold(
                { if (it) "플러그 ${if (on) "켜짐" else "꺼짐"} 명령 성공" else "플러그가 거절했습니다" },
                { "실패: ${it.message}" },
            )
        )
    }
}

@Composable
private fun Section(title: String) {
    Text(title, color = Color(0xFF3D8BFF), fontWeight = FontWeight.Bold, fontSize = 17.sp,
        modifier = Modifier.padding(top = 14.dp))
}

@Composable
private fun Toggle(label: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(label, color = Color.White, modifier = Modifier.weight(1f))
        Switch(value, onChange)
    }
}

@Composable
private fun Field(label: String, value: String, secret: Boolean = false, onChange: (String) -> Unit) {
    OutlinedTextField(
        value, onChange, label = { Text(label) }, singleLine = true,
        visualTransformation = if (secret) PasswordVisualTransformation()
            else androidx.compose.ui.text.input.VisualTransformation.None,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun NumberField(label: String, value: String, modifier: Modifier, onChange: (String) -> Unit) {
    OutlinedTextField(
        value, onChange, label = { Text(label) }, singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = modifier,
    )
}

@Composable
private fun RegionPicker(current: String, onPick: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val label = runCatching { TuyaRegion.valueOf(current).label }.getOrDefault(current)
    Box {
        OutlinedButton(onClick = { open = true }) { Text("데이터센터: $label") }
        DropdownMenu(open, { open = false }) {
            TuyaRegion.entries.forEach { r ->
                DropdownMenuItem(text = { Text(r.label) }, onClick = { onPick(r.name); open = false })
            }
        }
    }
}
