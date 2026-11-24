package com.choflix.clock.ui

import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.material3.Slider
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.border
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
import androidx.compose.ui.platform.LocalContext
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
import com.choflix.clock.data.Updater
import com.choflix.clock.data.WeatherRepository
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(settings: Settings, onClose: () -> Unit) {
    MaterialTheme(colorScheme = darkColorScheme()) {
        val scope = rememberCoroutineScope()
        val context = LocalContext.current
        val ignoringOptimizations = remember {
            context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName)
        }
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

            Section("글자·아이콘 색")
            ColorPicker(settings)

            Section("숫자 글꼴")
            FontPicker(settings)

            Section("시간대별 자동 밝기")
            DimmerSection(settings)

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

            Section("업데이트")
            UpdateSection()

            Section("계속 켜 두기 (샤오미·삼성 등)")
            Text(
                "폰이 절전을 위해 이 앱을 몰래 끄면 충전 제어가 멈춥니다. 아래 두 가지를 해 두세요.\n" +
                    "· 배터리 최적화에서 빼기\n" +
                    "· 앱 정보 → 샤오미는 '자동 시작' 켜기, '배터리 절약'을 '제한 없음'으로\n" +
                    "· 최근 앱 화면에서 이 앱을 길게 눌러 잠금(자물쇠)",
                color = Color.White.copy(alpha = 0.8f), fontSize = 14.sp,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {
                    runCatching {
                        context.startActivity(
                            Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                                Uri.parse("package:${context.packageName}"))
                        )
                    }
                }) { Text(if (ignoringOptimizations) "배터리 최적화 제외됨 ✓" else "배터리 최적화에서 빼기") }
                OutlinedButton(onClick = {
                    context.startActivity(
                        Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.parse("package:${context.packageName}"))
                    )
                }) { Text("앱 정보 열기") }
            }

            HorizontalDivider(Modifier.padding(top = 12.dp))
            Text(
                "Choflix Clock v${BuildConfig.VERSION_NAME} · github.com/fmj96/choflix-clock\n" +
                    "시계 오른쪽 위 전구는 기기 켜기/끄기, 톱니바퀴는 이 설정입니다.",
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

/** 깃허브 릴리스에서 새 버전 확인 → 받기 → 설치 화면. */
@Composable
private fun UpdateSection() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val available by Updater.available.collectAsState()
    var status by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }

    androidx.compose.runtime.LaunchedEffect(Unit) {
        status = "확인 중…"
        status = runCatching { Updater.check() }.fold(
            { if (it == null) "최신 버전입니다 (v${BuildConfig.VERSION_NAME})" else "" },
            { "확인 실패: ${it.message}" },
        )
    }

    available?.let { r ->
        Text(
            "새 버전 v${r.version} — ${r.title}",
            color = Color(0xFF9CC9FF), fontWeight = FontWeight.Bold,
        )
        if (r.notes.isNotBlank()) {
            Text(r.notes.take(600), color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp)
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        val r = available
        if (r != null) {
            Button(enabled = !busy, onClick = {
                if (!Updater.canInstall(context)) {
                    status = "이 앱의 '출처를 알 수 없는 앱 설치'를 허용한 뒤 다시 눌러 주세요"
                    Updater.openInstallPermission(context)
                    return@Button
                }
                busy = true
                scope.launch {
                    runCatching {
                        Updater.download(context, r) { p ->
                            status = if (p < 0) "받는 중…" else "받는 중… ${(p * 100).toInt()}%"
                        }
                    }.onSuccess { status = "설치 화면을 엽니다"; Updater.install(context, it) }
                        .onFailure { status = "받기 실패: ${it.message}" }
                    busy = false
                }
            }) { Text("v${r.version} 설치") }
        }
        OutlinedButton(enabled = !busy, onClick = {
            status = "확인 중…"
            scope.launch {
                status = runCatching { Updater.check() }.fold(
                    { if (it == null) "최신 버전입니다 (v${BuildConfig.VERSION_NAME})" else "" },
                    { "확인 실패: ${it.message}" },
                )
            }
        }) { Text("다시 확인") }
        if (status.isNotEmpty()) Text(status, color = Color.White.copy(alpha = 0.8f), fontSize = 14.sp)
    }
}

@Composable
private fun ColorPicker(settings: Settings) {
    var current by remember { mutableStateOf(settings.color) }
    if (settings.dimAmber && settings.isDimHour(java.time.LocalTime.now().hour)) {
        Text(
            "지금은 자동 밝기 시간대라 시계가 엠버색으로 보입니다. 고른 색을 바로 보려면 아래 '엠버색' 항목을 끄세요.",
            color = Color(0xFFFFB054), fontSize = 13.sp,
        )
    }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        ColorPresets.forEach { (name, argb) ->
            val selected = current == argb
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color(argb))
                        .border(if (selected) 3.dp else 1.dp, if (selected) Color(0xFF3D8BFF) else Color.White.copy(alpha = 0.3f), CircleShape)
                        .clickable { current = argb; settings.color = argb },
                )
                Text(name, color = Color.White.copy(alpha = if (selected) 1f else 0.6f), fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun FontPicker(settings: Settings) {
    var current by remember { mutableStateOf(DigitFont.of(settings.font)) }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        DigitFont.entries.forEach { f ->
            val selected = f == current
            Column(
                Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .border(if (selected) 2.dp else 1.dp, if (selected) Color(0xFF3D8BFF) else Color.White.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                    .clickable { current = f; settings.font = f.name }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("10:30", style = f.style(30.sp, Color.White))
                Text(f.label, color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun DimmerSection(settings: Settings) {
    var enabled by remember { mutableStateOf(settings.dimEnabled) }
    var start by remember { mutableStateOf(settings.dimStart) }
    var end by remember { mutableStateOf(settings.dimEnd) }
    var max by remember { mutableStateOf(settings.dimMaxPercent.toFloat()) }
    var amber by remember { mutableStateOf(settings.dimAmber) }

    Toggle("정해진 시간대에 화면을 어둡게", enabled) { enabled = it; settings.dimEnabled = it }
    if (!enabled) return
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("시작", color = Color.White)
        HourStepper(start) { start = it; settings.dimStart = it }
        Text("~  끝", color = Color.White)
        HourStepper(end) { end = it; settings.dimEnd = it }
    }
    Text(
        "밝기 상한 ${max.toInt()}% — 이 시간대에는 조도 센서가 밝다고 해도 이보다 밝아지지 않습니다",
        color = Color.White.copy(alpha = 0.8f), fontSize = 14.sp,
    )
    Slider(
        value = max, onValueChange = { max = it }, valueRange = 1f..60f,
        onValueChangeFinished = { settings.dimMaxPercent = max.toInt() },
    )
    Toggle("이 시간대에는 글자를 엠버색(눈부심 적음)으로", amber) { amber = it; settings.dimAmber = it }
}

/** 0~23시를 −/+ 로 고른다. */
@Composable
private fun HourStepper(hour: Int, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedButton(onClick = { onChange((hour + 23) % 24) }, modifier = Modifier.size(44.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) { Text("−") }
        Text("%02d시".format(hour), color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 10.dp))
        OutlinedButton(onClick = { onChange((hour + 1) % 24) }, modifier = Modifier.size(44.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) { Text("+") }
    }
}
