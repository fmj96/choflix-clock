@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.choflix.clock.ui

import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.FlowRow
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
import com.choflix.clock.data.TvRemote
import com.choflix.clock.data.Updater
import com.choflix.clock.data.WeatherRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.res.painterResource
import com.choflix.clock.R

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

        var locked by remember { mutableStateOf(settings.settingsLocked) }
        // 새 비밀번호 정하기: 0 = 창 없음, 1 = 처음 입력, 2 = 한 번 더(첫 입력은 firstPin).
        var pinStep by remember { mutableStateOf(0) }
        var firstPin by remember { mutableStateOf("") }

        // 잠가 두었으면 설정을 열어 둔 채 자리를 떠도 남이 못 만지게 2분 뒤 시계로 돌아간다.
        var touchedAt by remember { mutableStateOf(System.currentTimeMillis()) }
        if (locked && pinStep == 0) {
            LaunchedEffect(touchedAt) {
                delay(LOCKED_IDLE_MS)
                onClose()
            }
        }

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
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            awaitPointerEvent(PointerEventPass.Initial)
                            touchedAt = System.currentTimeMillis()
                        }
                    }
                }
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 32.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // 머리: 로고(앱 아이콘) · 앱 이름 · 버전
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(52.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFF1B2A44)),
                    contentAlignment = Alignment.Center,
                ) {
                    // 아이콘 그림은 108 칸 가운데 60 칸만 쓴다 — 크게 그려 상자로 잘라 낸다.
                    Image(painterResource(R.drawable.ic_launcher_foreground), null, Modifier.requiredSize(80.dp))
                }
                Spacer(Modifier.width(14.dp))
                Text("Choflix Clock", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Spacer(Modifier.width(10.dp))
                Text(
                    "v${BuildConfig.VERSION_NAME}", fontSize = 16.sp, color = Color.White.copy(alpha = 0.6f),
                    modifier = Modifier.weight(1f),
                )
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

            Section("설정 잠금")
            Toggle("비밀번호로 잠그기 (아무나 설정을 바꾸지 못하게)", locked) { on ->
                if (on) { firstPin = ""; pinStep = 1 } else { settings.setPin(null); locked = false }
            }
            if (locked) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = { firstPin = ""; pinStep = 1 }) { Text("비밀번호 바꾸기") }
                    Text(
                        "잊어버리면 앱 정보 → 저장공간 → 데이터 지우기로 풀립니다(다른 설정도 지워짐).",
                        color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp, modifier = Modifier.weight(1f),
                    )
                }
            }
            if (pinStep != 0) {
                PinDialog(
                    if (pinStep == 1) "새 비밀번호" else "비밀번호 확인",
                    if (pinStep == 1) "설정 버튼을 누를 때 물어봅니다" else "한 번 더 넣어 주세요",
                    onDismiss = { pinStep = 0 },
                ) { pin ->
                    when {
                        pin.length < PIN_MIN -> "${PIN_MIN}자리 이상 넣어 주세요"
                        pinStep == 1 -> { firstPin = pin; pinStep = 2; null }
                        pin != firstPin -> { pinStep = 1; "처음 넣은 것과 다릅니다 — 다시 정해 주세요" }
                        else -> { settings.setPin(pin); locked = true; pinStep = 0; null }
                    }
                }
            }

            Section("셋탑 (구글 TV) 리모컨")
            TvSection(settings)

            Section("시계로 돌아오기")
            AutoReturnSection(settings)

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
    val installMsg by Updater.installStatus.collectAsState()
    androidx.compose.runtime.LaunchedEffect(installMsg) { if (installMsg.isNotEmpty()) status = installMsg }

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
                    }.onSuccess {
                        status = "설치 준비 중…"
                        Updater.installStatus.value = ""
                        runCatching { Updater.install(context, it) }.onFailure { e -> status = "설치 실패: ${e.message}" }
                    }
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
    var clockW by remember { mutableStateOf(settings.clockWeight) }
    var infoW by remember { mutableStateOf(settings.infoWeight) }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
                Text(
                    "10:30 -4°", color = Color.White, fontSize = 30.sp, fontFamily = f.family,
                    fontWeight = FontWeight(f.nearest(clockW)),
                )
                Text(f.label, color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp)
            }
        }
    }
    WeightPicker("시계 숫자 굵기", current, clockW) { clockW = it; settings.clockWeight = it }
    WeightPicker("날짜·기온·습도 굵기", current, infoW) { infoW = it; settings.infoWeight = it }
}

/** 고른 글꼴에 있는 굵기만 보여 준다(Bebas·Anton 은 한 가지뿐). */
@Composable
private fun WeightPicker(title: String, font: DigitFont, value: Int, onPick: (Int) -> Unit) {
    val shown = font.nearest(value)
    Text(title, color = Color.White.copy(alpha = 0.8f), fontSize = 14.sp)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        font.weights.forEach { w ->
            val selected = w == shown
            Column(
                Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (selected) Color(0xFF3D8BFF) else Color(0xFF1E2A40))
                    .clickable { onPick(w) }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("88", color = Color.White, fontSize = 24.sp, fontFamily = font.family, fontWeight = FontWeight(w))
                Text(WeightNames[w] ?: "$w", color = Color.White.copy(alpha = 0.85f), fontSize = 11.sp)
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
        value = max, onValueChange = { max = it }, valueRange = 0f..60f,
        onValueChangeFinished = { settings.dimMaxPercent = max.toInt() },
    )
    var overlay by remember { mutableStateOf(settings.dimOverlayPercent.toFloat()) }
    Text(
        "추가로 어둡게 ${overlay.toInt()}% — 밝기를 0%로 해도 밝으면, 화면 위에 검은 막을 덧씌워 더 어둡게 합니다",
        color = Color.White.copy(alpha = 0.8f), fontSize = 14.sp,
    )
    Slider(
        value = overlay, onValueChange = { overlay = it }, valueRange = 0f..90f,
        onValueChangeFinished = { settings.dimOverlayPercent = overlay.toInt() },
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

private const val LOCKED_IDLE_MS = 120_000L

/** 다른 앱에서 손대지 않으면 시계로 돌아오기 — 다른 앱 위에 표시 권한이 있어야 터치를 알 수 있다. */
@Composable
private fun AutoReturnSection(settings: Settings) {
    val context = LocalContext.current
    var enabled by remember { mutableStateOf(settings.autoReturn) }
    var minutes by remember { mutableStateOf(settings.autoReturnMin) }
    var allowed by remember { mutableStateOf(android.provider.Settings.canDrawOverlays(context)) }
    // 권한 화면에서 돌아오면 바로 반영되게 가끔 다시 본다.
    LaunchedEffect(Unit) {
        while (true) { allowed = android.provider.Settings.canDrawOverlays(context); delay(1000) }
    }

    Toggle("다른 앱에서 ${minutes}분 동안 조작이 없으면 시계로", enabled) { enabled = it; settings.autoReturn = it }
    if (!enabled) return
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(1, 3, 5, 10, 30).forEach { m ->
            val on = m == minutes
            OutlinedButton(
                onClick = { minutes = m; settings.autoReturnMin = m },
                border = androidx.compose.foundation.BorderStroke(1.dp, if (on) Color(0xFF3D8BFF) else Color.White.copy(alpha = 0.3f)),
            ) { Text("${m}분", color = if (on) Color(0xFF3D8BFF) else Color.White) }
        }
    }
    Text(
        "소리·영상이 나오는 중이거나 통화 중이면 기다립니다.\n" +
            "· '다른 앱 위에 표시'를 허용해야 다른 앱의 터치를 알 수 있습니다(누른 위치·내용은 모릅니다)\n" +
            "· 샤오미는 앱 정보 → 기타 권한에서 '백그라운드에서 팝업 창 표시'도 허용해 주세요",
        color = Color.White.copy(alpha = 0.8f), fontSize = 14.sp,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = {
            runCatching {
                context.startActivity(
                    Intent(android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:${context.packageName}"))
                )
            }
        }) { Text(if (allowed) "다른 앱 위에 표시 허용됨 ✓" else "다른 앱 위에 표시 허용하기") }
        OutlinedButton(onClick = {
            // 샤오미 '기타 권한' 화면 — 없는 폰이면 앱 정보로.
            runCatching {
                context.startActivity(
                    Intent("miui.intent.action.APP_PERM_EDITOR")
                        .putExtra("extra_pkgname", context.packageName)
                )
            }.onFailure {
                context.startActivity(
                    Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:${context.packageName}"))
                )
            }
        }) { Text("샤오미 기타 권한") }
    }
}

/** 셋탑과 짝짓기 — 한 번 해 두면 음악 막대를 닫을 때 셋탑도 대기로 보낼 수 있다. */
@Composable
private fun TvSection(settings: Settings) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var paired by remember { mutableStateOf(settings.tvPaired) }
    var standbyOnClose by remember { mutableStateOf(settings.tvStandbyOnClose) }
    var status by remember { mutableStateOf("") }
    var searching by remember { mutableStateOf(false) }
    var found by remember { mutableStateOf<List<TvRemote.Found>>(emptyList()) }
    var pairing by remember { mutableStateOf<TvRemote.Pairing?>(null) }
    var code by remember { mutableStateOf("") }
    var manualIp by remember { mutableStateOf("") }
    androidx.compose.runtime.DisposableEffect(Unit) { onDispose { pairing?.cancel() } }

    fun startPair(f: TvRemote.Found) {
        scope.launch {
            status = "셋탑에 연결 중…"
            runCatching { TvRemote.startPairing(context, f) }
                .onSuccess { pairing = it; code = ""; status = "TV 화면에 코드가 떴습니다" }
                .onFailure { status = "연결 실패: ${it.message}" }
        }
    }

    when {
        paired -> {
            Text("연결됨: ${settings.tvName} (${settings.tvHost})", color = Color.White.copy(alpha = 0.8f))
            Toggle("캐스트 중에 음악 막대를 닫으면 셋탑도 대기로", standbyOnClose) {
                standbyOnClose = it; settings.tvStandbyOnClose = it
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {
                    scope.launch {
                        status = "보내는 중…"
                        status = runCatching { TvRemote.standby(context, settings) }.getOrElse { "실패: ${it.message}" }
                    }
                }) { Text("대기 테스트") }
                OutlinedButton(onClick = { settings.forgetTv(); paired = false; status = "" }) { Text("연결 끊기") }
            }
        }
        pairing != null -> {
            Text("TV 화면에 뜬 6자리 코드를 넣어 주세요 (숫자와 A~F)", color = Color.White.copy(alpha = 0.8f))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    code, { code = it.uppercase().filter { c -> c.isLetterOrDigit() }.take(6) },
                    label = { Text("코드") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.Characters),
                    modifier = Modifier.weight(1f),
                )
                Button(onClick = {
                    val p = pairing ?: return@Button
                    scope.launch {
                        status = "확인 중…"
                        runCatching { p.submit(code, settings) }.fold(
                            { err ->
                                if (err == null) { pairing = null; paired = true; status = "셋탑과 연결했습니다" } else status = err
                            },
                            { pairing = null; status = "실패: ${it.message} — 다시 찾아 연결해 주세요" },
                        )
                    }
                }) { Text("확인") }
                OutlinedButton(onClick = { pairing?.cancel(); pairing = null; status = "" }) { Text("취소") }
            }
        }
        else -> {
            Text(
                "같은 와이파이의 구글 TV 셋탑을 찾아 한 번 짝지어 둡니다. 셋탑이 켜져 있어야 찾을 수 있고, " +
                    "TV 화면에 뜨는 코드를 넣으면 끝납니다.",
                color = Color.White.copy(alpha = 0.8f), fontSize = 14.sp,
            )
            OutlinedButton(enabled = !searching, onClick = {
                scope.launch {
                    searching = true; status = ""
                    found = runCatching { TvRemote.discover(context) }.getOrDefault(emptyList())
                    searching = false
                    if (found.isEmpty()) status = "찾지 못했습니다 — 셋탑이 켜져 있고 같은 와이파이인지 보거나, IP 를 직접 넣어 주세요"
                }
            }) { Text(if (searching) "찾는 중…" else "셋탑 찾기") }
            found.forEach { f ->
                OutlinedButton(onClick = { startPair(f) }) { Text("${f.name}   ${f.host}") }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    manualIp, { manualIp = it.trim() }, label = { Text("IP 직접 입력 (예: 192.168.0.20)") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri), modifier = Modifier.weight(1f),
                )
                OutlinedButton(enabled = manualIp.isNotEmpty(), onClick = { startPair(TvRemote.Found(manualIp, manualIp)) }) { Text("연결") }
            }
        }
    }
    if (status.isNotEmpty()) Text(status, color = Color(0xFF9CC9FF))
}
