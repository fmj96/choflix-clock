package com.choflix.clock.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** 정전 뒤 폰이 다시 켜져도 충전 제어가 이어지게. 시계 화면은 사용자가 열어야 한다. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) BatteryPlugMonitorService.start(context)
    }
}
