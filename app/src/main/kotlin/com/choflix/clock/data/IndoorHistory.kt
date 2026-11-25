package com.choflix.clock.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 실내 온·습도 기록 — 서비스가 5분마다 받는 값을 폰에 쌓는다(API 호출이 늘지 않는다).
 * 줄마다 "시각(ms),온도,습도" — 모르는 값은 비워 둔다. 8일이 지난 줄은 지운다.
 * 기록이 모자란 앞부분은 그래프 화면이 Tuya 기록으로 한 번 채워 [merge] 로 넣는다.
 */
object IndoorHistory {
    private const val KEEP_MS = 8 * 24 * 3600_000L
    private val lock = Mutex()

    private fun file(context: Context) = File(context.filesDir, "indoor_history.csv")

    suspend fun add(context: Context, indoor: Indoor) {
        if (indoor.temperatureC == null && indoor.humidity == null) return
        merge(context, listOf(IndoorSample(System.currentTimeMillis(), indoor.temperatureC, indoor.humidity?.toDouble())))
    }

    suspend fun load(context: Context): List<IndoorSample> = lock.withLock { read(context) }

    /** 새 점들을 넣고 시간순으로 다시 쓴다. 같은 시각은 하나만. */
    suspend fun merge(context: Context, samples: List<IndoorSample>) = lock.withLock {
        if (samples.isEmpty()) return@withLock
        val cut = System.currentTimeMillis() - KEEP_MS
        val all = (read(context) + samples).filter { it.timeMs >= cut }
            .distinctBy { it.timeMs }.sortedBy { it.timeMs }
        withContext(Dispatchers.IO) {
            file(context).writeText(all.joinToString("") { "${it.timeMs},${it.temperatureC ?: ""},${it.humidity ?: ""}\n" })
        }
    }

    private suspend fun read(context: Context): List<IndoorSample> = withContext(Dispatchers.IO) {
        val f = file(context)
        if (!f.exists()) return@withContext emptyList()
        f.readLines().mapNotNull { line ->
            val p = line.split(',')
            if (p.size < 3) return@mapNotNull null
            IndoorSample(p[0].toLongOrNull() ?: return@mapNotNull null, p[1].toDoubleOrNull(), p[2].toDoubleOrNull())
        }
    }
}
