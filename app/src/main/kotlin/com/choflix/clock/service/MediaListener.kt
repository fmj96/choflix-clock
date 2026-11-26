package com.choflix.clock.service

import android.service.notification.NotificationListenerService
import com.choflix.clock.data.NowPlaying

/**
 * 알림은 읽지 않는다 — 다른 앱(애플뮤직)의 미디어 세션을 보려면 '알림 접근'을 허용받은
 * 리스너가 있어야 해서 둔 자리다. 시스템이 붙여 주면 곡 정보를 듣기 시작한다.
 */
class MediaListener : NotificationListenerService() {
    override fun onListenerConnected() = NowPlaying.start(this)
    override fun onListenerDisconnected() = NowPlaying.stop()
}
