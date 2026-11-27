package com.choflix.clock.data

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import androidx.core.app.NotificationManagerCompat
import com.choflix.clock.service.MediaListener
import kotlinx.coroutines.flow.MutableStateFlow

/** 지금 재생 중인 곡. remote 면 크롬캐스트(구글TV)로 보내고 있는 중 — 볼륨도 TV 쪽 볼륨이다. */
data class Track(
    val title: String,
    val artist: String,
    val album: String,
    val art: Bitmap?,
    val playing: Boolean,
    val app: String,
    val remote: Boolean,
    val volume: Int,
    val maxVolume: Int,
)

/**
 * 폰에서 도는 음악 앱(애플뮤직 등)의 '지금 재생 중'을 읽고 조작한다. 안드로이드 표준 미디어 세션을
 * 쓰는데, 다른 앱의 세션을 보려면 사용자가 이 앱에 '알림 접근'을 허용해 줘야 한다([MediaListener]).
 * 애플뮤직이 TV로 캐스트 중이어도 세션은 폰에 남아 있어 그대로 조작된다.
 */
object NowPlaying {
    const val APPLE_MUSIC = "com.apple.android.music"

    val track = MutableStateFlow<Track?>(null)
    private var controller: MediaController? = null
    private var manager: MediaSessionManager? = null

    fun hasAccess(context: Context): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)

    private val sessionsChanged = MediaSessionManager.OnActiveSessionsChangedListener { list -> pick(list.orEmpty()) }

    private val callback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) = publish()
        override fun onPlaybackStateChanged(state: PlaybackState?) = publish()
        override fun onAudioInfoChanged(info: MediaController.PlaybackInfo) = publish()
        override fun onSessionDestroyed() {
            controller?.unregisterCallback(this)
            controller = null
            track.value = null
        }
    }

    /** 세션 목록을 듣기 시작한다. 권한이 없으면 아무것도 안 한다(메인 스레드에서 부를 것). */
    fun start(context: Context) {
        if (manager != null || !hasAccess(context)) return
        val m = context.getSystemService(MediaSessionManager::class.java) ?: return
        val comp = ComponentName(context, MediaListener::class.java)
        runCatching {
            m.addOnActiveSessionsChangedListener(sessionsChanged, comp)
            manager = m
            pick(m.getActiveSessions(comp))
        }
    }

    fun stop() {
        manager?.removeOnActiveSessionsChangedListener(sessionsChanged)
        manager = null
    }

    /** 애플뮤직을 먼저, 없으면 재생 중인 것, 그것도 없으면 맨 앞 세션. */
    private fun pick(list: List<MediaController>) {
        val c = list.firstOrNull { it.packageName == APPLE_MUSIC }
            ?: list.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
            ?: list.firstOrNull()
        if (c?.sessionToken == controller?.sessionToken) return publish()
        controller?.unregisterCallback(callback)
        controller = c
        c?.registerCallback(callback)
        publish()
    }

    private fun publish() {
        val c = controller ?: run { track.value = null; return }
        val md = c.metadata
        val info = c.playbackInfo
        track.value = Track(
            title = md?.getString(MediaMetadata.METADATA_KEY_TITLE).orEmpty(),
            artist = (md?.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: md?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)).orEmpty(),
            album = md?.getString(MediaMetadata.METADATA_KEY_ALBUM).orEmpty(),
            art = md?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART) ?: md?.getBitmap(MediaMetadata.METADATA_KEY_ART),
            playing = c.playbackState?.state == PlaybackState.STATE_PLAYING,
            app = c.packageName,
            remote = info?.playbackType == MediaController.PlaybackInfo.PLAYBACK_TYPE_REMOTE,
            volume = info?.currentVolume ?: 0,
            maxVolume = info?.maxVolume ?: 0,
        )
    }

    fun playPause() {
        val t = controller?.transportControls ?: return
        if (track.value?.playing == true) t.pause() else t.play()
    }

    /** 지금 재생 중인 앱의 '재생 중' 화면을 연다(앱이 세션에 걸어 둔 화면). 못 열면 false. */
    fun openPlayer(): Boolean {
        val pi = controller?.sessionActivity ?: return false
        return runCatching { pi.send() }.isSuccess
    }

    fun next() { controller?.transportControls?.skipToNext() }
    fun previous() { controller?.transportControls?.skipToPrevious() }

    /** 볼륨 한 칸. 캐스트 중이면 TV(스피커) 볼륨이 움직인다. */
    fun volume(up: Boolean) {
        controller?.adjustVolume(if (up) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER, 0)
    }
}
