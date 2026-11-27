package com.choflix.clock.service

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.provider.Settings as SystemSettings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import com.choflix.clock.ClockActivity
import com.choflix.clock.data.Settings

/**
 * 다른 앱을 쓰다 한동안 손대지 않으면 시계로 돌아온다.
 *
 * 다른 앱의 터치는 볼 수 없으므로, 화면 구석에 1픽셀짜리 투명 창(다른 앱 위에 표시 권한)을 띄워
 * 그 바깥을 누를 때 오는 ACTION_OUTSIDE 만 받는다 — 누른 위치·내용은 오지 않는다.
 * 시계가 앞에 있을 때는 창을 내린다. 소리·영상이 나오거나 통화 중이면 기다린다.
 */
class AutoReturn(private val context: Context, private val settings: Settings) {
    private val handler = Handler(Looper.getMainLooper())
    private val wm = context.getSystemService(WindowManager::class.java)
    private val audio = context.getSystemService(AudioManager::class.java)
    private var probe: View? = null

    private val tick = object : Runnable {
        override fun run() {
            check()
            handler.postDelayed(this, CHECK_MS)
        }
    }

    fun start() = handler.post(tick)

    fun stop() {
        handler.removeCallbacks(tick)
        detach()
    }

    private fun check() {
        val on = settings.autoReturn && SystemSettings.canDrawOverlays(context)
        if (!on || clockInFront) {
            detach()
            return
        }
        attach()
        val busy = audio.isMusicActive ||
            audio.mode == AudioManager.MODE_IN_CALL || audio.mode == AudioManager.MODE_IN_COMMUNICATION
        if (busy) touchedAt = System.currentTimeMillis()
        if (System.currentTimeMillis() - touchedAt >= settings.autoReturnMin * 60_000L) {
            touchedAt = System.currentTimeMillis()
            runCatching {
                context.startActivity(
                    Intent(context, ClockActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT),
                )
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun attach() {
        if (probe != null) return
        val v = View(context).apply {
            setOnTouchListener { _, e ->
                if (e.actionMasked == MotionEvent.ACTION_OUTSIDE) touchedAt = System.currentTimeMillis()
                false
            }
        }
        val lp = WindowManager.LayoutParams(
            1, 1,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT,
        ).apply { gravity = Gravity.TOP or Gravity.START }
        if (runCatching { wm.addView(v, lp) }.isSuccess) probe = v
    }

    private fun detach() {
        probe?.let { runCatching { wm.removeView(it) } }
        probe = null
    }

    companion object {
        private const val CHECK_MS = 15_000L

        /** 시계 화면이 앞에 있는지 — ClockActivity 가 onResume/onPause 에서 바꾼다. */
        @Volatile var clockInFront = false
            set(v) {
                field = v
                // 시계를 떠난 순간부터 센다.
                if (!v) touchedAt = System.currentTimeMillis()
            }

        @Volatile private var touchedAt = System.currentTimeMillis()
    }
}
