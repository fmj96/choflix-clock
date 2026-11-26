package com.choflix.clock.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate

/** 공휴일 하루. */
data class Holiday(val date: LocalDate, val name: String)

/**
 * 우리나라 공휴일 — Nager.Date(키 없음)에서 해마다 한 번 받아 폰에 둔다.
 * 설날·추석 같은 음력 공휴일과 대체공휴일까지 들어 있다.
 */
object Holidays {
    private val cache = mutableMapOf<Int, List<Holiday>>()

    suspend fun of(context: Context, year: Int): List<Holiday> = withContext(Dispatchers.IO) {
        cache[year]?.let { return@withContext it }
        val f = File(context.filesDir, "holidays_$year.json")
        // 받아 둔 지 30일이 안 됐으면 그대로 쓴다(임시공휴일이 늦게 정해지기도 해 가끔 새로 받는다).
        val fresh = f.exists() && System.currentTimeMillis() - f.lastModified() < 30L * 24 * 3600_000
        val text = (if (fresh) f.readText() else null)
            ?: runCatching { download(year) }.getOrNull()?.also { f.writeText(it) }
            ?: f.takeIf { it.exists() }?.readText()
            ?: return@withContext emptyList()
        val list = runCatching {
            val arr = JSONArray(text)
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                val d = runCatching { LocalDate.parse(o.optString("date")) }.getOrNull() ?: return@mapNotNull null
                Holiday(d, o.optString("localName").ifBlank { o.optString("name") })
            }
        }.getOrDefault(emptyList())
        list.also { if (it.isNotEmpty()) cache[year] = it }
    }

    private fun download(year: Int): String? {
        val conn = (URL("https://date.nager.at/api/v3/PublicHolidays/$year/KR").openConnection() as HttpURLConnection).apply {
            connectTimeout = 8000
            readTimeout = 8000
        }
        return try {
            if (conn.responseCode == 200) conn.inputStream.bufferedReader().use { it.readText() } else null
        } finally {
            conn.disconnect()
        }
    }
}

/** 개인 일정·기념일. yearly 면 해마다 같은 월·일에 나온다(등록한 해부터). */
data class Event(val id: Long, val date: LocalDate, val title: String, val yearly: Boolean) {
    fun occursOn(d: LocalDate): Boolean =
        if (yearly) d.monthValue == date.monthValue && d.dayOfMonth == date.dayOfMonth && d.year >= date.year
        else d == date

    /** [from] 이후(그날 포함) 처음 돌아오는 날. 지나간 한 번짜리면 null. */
    fun nextFrom(from: LocalDate): LocalDate? {
        if (!yearly) return date.takeIf { !it.isBefore(from) }
        if (!date.isBefore(from)) return date
        // 2/29 는 윤년이 아니면 2/28 로(withYear 가 맞춰 준다).
        val thisYear = date.withYear(from.year)
        return if (!thisYear.isBefore(from)) thisYear else date.withYear(from.year + 1)
    }
}

/** 일정은 폰 안(events.json)에만 둔다 — 공개 저장소·서버로 보내지 않는다. */
object Events {
    val all = MutableStateFlow<List<Event>>(emptyList())
    private var loaded = false

    private fun file(context: Context) = File(context.filesDir, "events.json")

    suspend fun load(context: Context) = withContext(Dispatchers.IO) {
        if (loaded) return@withContext
        val f = file(context)
        all.value = if (!f.exists()) emptyList() else runCatching {
            val arr = JSONArray(f.readText())
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                Event(
                    o.optLong("id"),
                    LocalDate.parse(o.optString("date")),
                    o.optString("title"),
                    o.optBoolean("yearly"),
                )
            }
        }.getOrDefault(emptyList())
        loaded = true
    }

    suspend fun save(context: Context, e: Event) = write(context, all.value.filter { it.id != e.id } + e)

    suspend fun delete(context: Context, id: Long) = write(context, all.value.filter { it.id != id })

    private suspend fun write(context: Context, list: List<Event>) = withContext(Dispatchers.IO) {
        val sorted = list.sortedWith(compareBy({ it.date.monthValue }, { it.date.dayOfMonth }, { it.title }))
        val arr = JSONArray()
        sorted.forEach {
            arr.put(JSONObject().put("id", it.id).put("date", it.date.toString()).put("title", it.title).put("yearly", it.yearly))
        }
        file(context).writeText(arr.toString())
        all.value = sorted
    }
}
