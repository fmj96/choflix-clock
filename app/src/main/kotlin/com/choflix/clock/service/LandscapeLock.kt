package com.choflix.clock.service

import android.content.Context
import android.content.pm.ActivityInfo
import android.graphics.PixelFormat
import android.provider.Settings as SystemSettings
import android.view.Gravity
import android.view.View
import android.view.WindowManager

/**
 * 다른 앱(애플뮤직)을 가로로 돌린다. 휴대폰 애플뮤직은 세로만 되는데, 맨 위 창이 원하는 방향을
 * 화면이 따르므로 1픽셀 투명 창(다른 앱 위에 표시)에 '가로'를 걸어 둔다. 터치는 그대로 지나간다.
 * 시계로 돌아오면 푼다(ClockActivity.onResume).
 */
object LandscapeLock {
    private var view: View? = null

    fun hold(context: Context) {
        if (view != null || !SystemSettings.canDrawOverlays(context)) return
        val app = context.applicationContext
        val wm = app.getSystemService(WindowManager::class.java)
        val v = View(app)
        val lp = WindowManager.LayoutParams(
            1, 1,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            screenOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        }
        if (runCatching { wm.addView(v, lp) }.isSuccess) view = v
    }

    fun release(context: Context) {
        val v = view ?: return
        runCatching { context.applicationContext.getSystemService(WindowManager::class.java).removeView(v) }
        view = null
    }
}
