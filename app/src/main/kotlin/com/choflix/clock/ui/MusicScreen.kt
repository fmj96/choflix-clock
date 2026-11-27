package com.choflix.clock.ui

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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

/**
 * 캐스트 버튼. 남의 앱(애플뮤직)의 캐스트를 이 앱이 대신 걸 수는 없다 — 안드로이드의 '미디어 출력'
 * 창(재생 중인 앱의 소리를 어디로 보낼지 고르는 시스템 창)을 그 앱 이름으로 연다.
 *  1. 시스템 UI 의 미디어 출력 창(안드로이드 12~) — MIUI 는 설정 쪽 패널이 없어 이게 먼저다.
 *  2. 설정의 미디어 출력 패널(안드로이드 10~11 순정).
 *  3. 둘 다 없으면 애플뮤직 재생 화면(캐스트 버튼이 있는 곳).
 */
internal fun openCast(context: Context) {
    val pkg = NowPlaying.track.value?.app ?: NowPlaying.APPLE_MUSIC
    val dialog = Intent("com.android.systemui.action.LAUNCH_MEDIA_OUTPUT_DIALOG")
        .setPackage("com.android.systemui")
        .putExtra("package_name", pkg)
    if (context.packageManager.queryBroadcastReceivers(dialog, 0).isNotEmpty()) {
        context.sendBroadcast(dialog)
        return
    }
    val panel = Intent("android.settings.panel.action.MEDIA_OUTPUT")
        .putExtra("com.android.settings.panel.extra.PACKAGE_NAME", pkg)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    if (runCatching { context.startActivity(panel) }.isFailure) openAppleMusic(context)
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
    val pad = h * 0.1f
    Row(
        modifier.clip(RoundedCornerShape(h * 0.2f)).background(Color(0xFF141414).copy(alpha = 0.72f))
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
                            t.artist + if (t.remote) "  · TV" else "", color = color.copy(alpha = 0.7f), fontSize = subSp,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                // 캐스트: 연결돼 있으면(세션이 원격) 꽉 찬 그림. 셋탑이 대기로 가도 애플뮤직이 연결로 남겨 두기도 한다.
                RoundButton(if (t.remote) Icons.Filled.CastConnected else Icons.Filled.Cast, "캐스트", color, h * 0.48f) { openCast(context) }
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
private fun RoundButton(icon: ImageVector, label: String, color: Color, size: Dp, onClick: () -> Unit) {
    Box(
        Modifier.size(size).clip(CircleShape).background(Color(0xFF2A2A2A)).clickable(onClickLabel = label, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, label, tint = color, modifier = Modifier.size(size * 0.55f))
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
