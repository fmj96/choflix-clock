package com.choflix.clock.ui

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.CastConnected
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.VolumeDown
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.outlined.MusicNote
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.choflix.clock.data.NowPlaying
import com.choflix.clock.data.TvRemote
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import com.choflix.clock.data.Settings
import kotlinx.coroutines.delay

/**
 * 음악: 이 폰의 애플뮤직(또는 다른 음악 앱)이 지금 트는 곡과 재생·멈춤·이전·다음·볼륨.
 * TV로 보내기(캐스트)는 애플뮤직 재생 화면의 캐스트 버튼에서 구글TV를 고르면 되고, 그 뒤에도
 * 여기서 그대로 조작된다. 1분 동안 손대지 않으면 시계로 돌아간다.
 */
@Composable
fun MusicScreen(settings: Settings, onClose: () -> Unit) {
    val context = LocalContext.current
    val color = Color(settings.color)
    val track by NowPlaying.track.collectAsState()
    var access by remember { mutableStateOf(NowPlaying.hasAccess(context)) }

    var touchedAt by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(touchedAt) {
        delay(IDLE_MS)
        onClose()
    }
    // 알림 접근 설정에서 허용하고 돌아오면 바로 붙는다.
    LaunchedEffect(Unit) {
        while (true) {
            access = NowPlaying.hasAccess(context)
            if (access) NowPlaying.start(context)
            delay(1000)
        }
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
            Text("음악", color = color, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            if (track?.remote == true) {
                Text("  · TV로 재생 중", color = color.copy(alpha = 0.6f), fontSize = 16.sp)
            }
            Spacer(Modifier.weight(1f))
            Chip("애플뮤직 열기", color) { openAppleMusic(context) }
        }

        if (!access) {
            Column(
                Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    "지금 재생 중인 곡을 보려면 이 앱에 '알림 접근'을 허용해 주세요.\n알림 내용은 읽지 않고 음악 정보만 씁니다.",
                    color = color.copy(alpha = 0.8f), fontSize = 18.sp,
                )
                Spacer(Modifier.height(16.dp))
                Chip("알림 접근 허용하러 가기", color) {
                    runCatching {
                        context.startActivity(
                            Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }
                }
            }
            return@Column
        }

        val t = track
        if (t == null || t.title.isEmpty()) {
            Column(
                Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("재생 중인 음악이 없습니다", color = color.copy(alpha = 0.8f), fontSize = 20.sp)
                Spacer(Modifier.height(8.dp))
                Text(
                    "애플뮤직에서 곡을 틀고, 재생 화면의 캐스트 버튼에서 구글TV를 고르면 TV 스피커로 나옵니다.",
                    color = color.copy(alpha = 0.5f), fontSize = 15.sp,
                )
            }
            return@Column
        }

        Row(
            Modifier.fillMaxSize().padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(28.dp),
        ) {
            // 앨범 그림: 높이를 채우는 정사각형.
            Box(
                Modifier.fillMaxHeight().aspectRatio(1f).clip(RoundedCornerShape(20.dp)).background(Color(0xFF2A2A2A)),
                contentAlignment = Alignment.Center,
            ) {
                if (t.art != null) {
                    Image(t.art.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                } else {
                    Icon(Icons.Outlined.MusicNote, null, tint = color.copy(alpha = 0.4f), modifier = Modifier.size(96.dp))
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(t.title, color = color, fontSize = 34.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(t.artist, color = color.copy(alpha = 0.8f), fontSize = 22.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (t.album.isNotEmpty()) {
                    Text(t.album, color = color.copy(alpha = 0.5f), fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.height(20.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    RoundButton(Icons.Filled.SkipPrevious, "이전 곡", color, 64.dp) { NowPlaying.previous() }
                    RoundButton(if (t.playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, "재생/멈춤", color, 84.dp) { NowPlaying.playPause() }
                    RoundButton(Icons.Filled.SkipNext, "다음 곡", color, 64.dp) { NowPlaying.next() }
                    Spacer(Modifier.width(18.dp))
                    RoundButton(Icons.Filled.VolumeDown, "소리 작게", color, 56.dp) { NowPlaying.volume(false) }
                    if (t.maxVolume > 0) {
                        Text("${t.volume * 100 / t.maxVolume}%", color = color.copy(alpha = 0.7f), fontSize = 18.sp)
                    }
                    RoundButton(Icons.Filled.VolumeUp, "소리 크게", color, 56.dp) { NowPlaying.volume(true) }
                }
            }
        }
    }
}

/** 애플뮤직을 연다. 깔려 있지 않으면 플레이스토어. */
internal fun openAppleMusic(context: Context) {
    if (NowPlaying.openPlayer()) return
    val i = context.packageManager.getLaunchIntentForPackage(NowPlaying.APPLE_MUSIC)
        ?: Intent(Intent.ACTION_VIEW, android.net.Uri.parse("market://details?id=${NowPlaying.APPLE_MUSIC}"))
    runCatching { context.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

/**
 * 시계 화면 아래에 겹쳐 띄우는 음악 막대(음표 버튼으로 열고 닫는다). 밑의 시·분이 비쳐 보이게
 * 반투명. 크기는 부르는 쪽이 정하고, 안은 그 높이 [h] 에 맞춰 그린다.
 * [onOpenFull] 은 알림 접근 허용 안내가 있는 큰 음악 화면으로 간다.
 */
@Composable
internal fun MusicBar(color: Color, h: Dp, onOpenFull: () -> Unit, modifier: Modifier) {
    val context = LocalContext.current
    val track by NowPlaying.track.collectAsState()
    var access by remember { mutableStateOf(NowPlaying.hasAccess(context)) }
    LaunchedEffect(Unit) {
        while (true) {
            access = NowPlaying.hasAccess(context)
            if (access) NowPlaying.start(context)
            delay(2000)
        }
    }
    val settings = remember { Settings(context) }
    // 캐스트 버튼 확인 창: true = 켤까요, false = 끌까요, null = 닫힘.
    var castAsk by remember { mutableStateOf<Boolean?>(null) }
    when (castAsk) {
        true -> ConfirmDialog(
            "셋탑 스피커로 듣기",
            "애플뮤직 재생 화면 아래의 캐스트 아이콘을 누르고 'U+tv Soundbar' 를 고르면 됩니다.\n\n" +
                if (settings.tvPaired) "셋탑 스피커를 켜고 애플뮤직으로 갈까요?"
                else "셋탑 리모컨이 연결되어 있지 않아 셋탑은 켜지 않습니다(설정 → 셋탑 리모컨). 애플뮤직으로 갈까요?",
            onConfirm = {
                castAsk = null
                if (settings.tvPaired) TvRemote.wakeAsync(context, settings)
                openAppleMusic(context)
            },
            onDismiss = { castAsk = null },
        )
        false -> CastOffDialog(settings, onDone = { castAsk = null }, onDismiss = { castAsk = null }, castOnly = true)
        null -> {}
    }
    val pad = h * 0.1f
    Row(
        modifier.clip(RoundedCornerShape(h * 0.2f)).background(Color(0xFF141414).copy(alpha = 0.5f))
            .padding(horizontal = pad, vertical = pad),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(pad),
    ) {
        val t = track
        val textSp = with(androidx.compose.ui.platform.LocalDensity.current) { (h * 0.24f).toSp() }
        val subSp = with(androidx.compose.ui.platform.LocalDensity.current) { (h * 0.18f).toSp() }
        when {
            !access -> {
                Text("음악 정보를 보려면 알림 접근 허용이 필요합니다", color = color, fontSize = subSp, modifier = Modifier.weight(1f))
                Chip("허용하러 가기", color, onOpenFull)
            }
            t == null || t.title.isEmpty() -> {
                Icon(Icons.Outlined.MusicNote, null, tint = color.copy(alpha = 0.5f), modifier = Modifier.size(h * 0.5f))
                Text("재생 중인 음악이 없습니다", color = color.copy(alpha = 0.8f), fontSize = textSp, modifier = Modifier.weight(1f))
                Chip("애플뮤직 열기", color) { openAppleMusic(context) }
            }
            else -> {
                // 그림·제목을 누르면 애플뮤직 앱으로 — 곡 고르기·캐스트는 거기서 한다.
                Row(
                    Modifier.weight(1f).clickable(onClickLabel = "애플뮤직 열기") { openAppleMusic(context) },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(pad),
                ) {
                    Box(
                        Modifier.size(h - pad * 2).clip(RoundedCornerShape(h * 0.1f)).background(Color(0xFF2A2A2A)),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (t.art != null) Image(t.art.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                        else Icon(Icons.Outlined.MusicNote, null, tint = color.copy(alpha = 0.4f), modifier = Modifier.size(h * 0.4f))
                    }
                    Column(Modifier.weight(1f)) {
                        Text(t.title, color = color, fontSize = textSp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            t.artist, color = color.copy(alpha = 0.7f), fontSize = subSp,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                // 캐스트: 연결돼 있으면(세션이 원격) 분홍으로 켜짐, 아니면 옅게. 누르면 셋탑 스피커를 켤지/끌지 묻는다.
                // 켜기는 셋탑을 깨우고 애플뮤직을 연다 — 애플뮤직은 셋탑을 안드로이드 '미디어 출력' 창에 내놓지 않아
                // (폰 스피커만 보임, v1.5.2 실사용) 캐스트 기기는 애플뮤직 안에서 골라야 한다.
                RoundButton(
                    if (t.remote) Icons.Filled.CastConnected else Icons.Filled.Cast, "캐스트",
                    if (t.remote) color else color.copy(alpha = 0.45f), h * 0.48f,
                    active = if (t.remote) distinctFrom(color, Color(0xFFFF5A78), Color(0xFFFFB3C1)) else null,
                ) { castAsk = !t.remote }
                RoundButton(Icons.Filled.SkipPrevious, "이전 곡", color, h * 0.55f) { NowPlaying.previous() }
                RoundButton(if (t.playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, "재생/멈춤", color, h * 0.72f) { NowPlaying.playPause() }
                RoundButton(Icons.Filled.SkipNext, "다음 곡", color, h * 0.55f) { NowPlaying.next() }
                Spacer(Modifier.width(pad * 0.5f))
                RoundButton(Icons.Filled.VolumeDown, "소리 작게", color, h * 0.48f) { NowPlaying.volume(false) }
                if (t.maxVolume > 0) {
                    Text("${t.volume * 100 / t.maxVolume}%", color = color.copy(alpha = 0.7f), fontSize = subSp)
                }
                RoundButton(Icons.Filled.VolumeUp, "소리 크게", color, h * 0.48f) { NowPlaying.volume(true) }
            }
        }
    }
}

@Composable
private fun RoundButton(icon: ImageVector, label: String, color: Color, size: Dp, active: Color? = null, onClick: () -> Unit) {
    // active 가 있으면 그 색 테두리·아이콘(음표 버튼처럼 켜짐 표시).
    var m = Modifier.size(size).clip(CircleShape).background(Color(0xFF2A2A2A))
    if (active != null) m = m.border(2.dp, active, CircleShape)
    Box(
        m.clickable(onClickLabel = label, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, label, tint = active ?: color, modifier = Modifier.size(size * 0.55f))
    }
}

@Composable
private fun Chip(label: String, color: Color, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(10.dp)).background(Color(0xFF2A2A2A)).clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text(label, color = color, fontSize = 16.sp)
    }
}

private const val IDLE_MS = 60_000L

/**
 * 셋탑 스피커를 끌까요? — 확인이면 음악을 멈추고 셋탑을 대기로.
 * [onMusicOnly] 를 주면(음표 버튼에서) '음악만 종료' 버튼도 둔다 — 음악만 멈추고 셋탑은 그대로.
 * [castOnly] 면(캐스트 버튼에서) '캐스트만 해제' 버튼도 둔다 — 캐스트를 끊는 건 애플뮤직만 할 수 있어
 * 방법을 알려 주고 애플뮤직 재생 화면을 연다.
 */
@Composable
internal fun CastOffDialog(
    settings: Settings, onDone: () -> Unit, onDismiss: () -> Unit,
    onMusicOnly: (() -> Unit)? = null, castOnly: Boolean = false,
) {
    val context = LocalContext.current
    MaterialTheme(colorScheme = darkColorScheme()) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("셋탑 스피커 끄기") },
            text = {
                Text(
                    if (settings.tvPaired) "음악을 멈추고 셋탑 스피커도 끌까요?"
                    else "셋탑 리모컨이 연결되어 있지 않아 음악만 멈춥니다(설정 → 셋탑 리모컨). 멈출까요?",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    NowPlaying.pause()
                    if (settings.tvPaired) TvRemote.standbyAsync(context, settings)
                    onDone()
                }) { Text(if (settings.tvPaired) "셋탑도 끄기" else "확인") }
            },
            dismissButton = {
                Row {
                    if (onMusicOnly != null && settings.tvPaired) {
                        TextButton(onClick = { NowPlaying.pause(); onMusicOnly() }) { Text("음악만 종료") }
                    }
                    if (castOnly) {
                        TextButton(onClick = {
                            onDismiss()
                            android.widget.Toast.makeText(
                                context, "애플뮤직 아래 캐스트 아이콘 → '연결 해제'(또는 이 휴대전화)를 누르세요",
                                android.widget.Toast.LENGTH_LONG,
                            ).show()
                            openAppleMusic(context)
                        }) { Text("캐스트만 해제") }
                    }
                    TextButton(onClick = onDismiss) { Text("취소") }
                }
            },
        )
    }
}

@Composable
internal fun ConfirmDialog(title: String, text: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    MaterialTheme(colorScheme = darkColorScheme()) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(title) },
            text = { Text(text) },
            confirmButton = { TextButton(onClick = onConfirm) { Text("확인") } },
            dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } },
        )
    }
}
