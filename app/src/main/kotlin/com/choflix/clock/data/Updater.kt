package com.choflix.clock.data

import android.content.Context
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings as AndroidSettings
import com.choflix.clock.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

data class Release(val version: String, val title: String, val notes: String, val apkUrl: String)

/**
 * 앱 안 업데이트 — 깃허브 릴리스(공개 저장소)에서 지금보다 새 버전을 찾아 받아 설치 화면을 띄운다.
 * 미리보기(prerelease)도 포함한다: 지금은 모든 릴리스가 미리보기다.
 */
object Updater {
    private const val API = "https://api.github.com/repos/fmj96/choflix-clock/releases?per_page=10"

    /** 새 버전이 있으면 그 릴리스(시계의 톱니바퀴에 점을 찍는 데도 쓴다). */
    val available = MutableStateFlow<Release?>(null)

    suspend fun check(): Release? = withContext(Dispatchers.IO) {
        val conn = (URL(API).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 10_000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "ChoflixClock")
        }
        val arr = try {
            if (conn.responseCode != 200) return@withContext null
            JSONArray(conn.inputStream.bufferedReader().use { it.readText() })
        } finally {
            conn.disconnect()
        }
        val newest = (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            if (o.optBoolean("draft")) return@mapNotNull null
            val assets = o.optJSONArray("assets") ?: return@mapNotNull null
            val apk = (0 until assets.length()).mapNotNull { assets.optJSONObject(it) }
                .firstOrNull { it.optString("name").endsWith(".apk") } ?: return@mapNotNull null
            Release(
                version = o.optString("tag_name").removePrefix("v"),
                title = o.optString("name"),
                notes = o.optString("body"),
                apkUrl = apk.optString("browser_download_url"),
            )
        }.maxWithOrNull { a, b -> compare(a.version, b.version) }
        (newest?.takeIf { compare(it.version, BuildConfig.VERSION_NAME) > 0 }).also { available.value = it }
    }

    /** "0.10.2" 처럼 점으로 나뉜 숫자를 자리별로 비교한다. */
    fun compare(a: String, b: String): Int {
        val x = a.split('.').map { it.toIntOrNull() ?: 0 }
        val y = b.split('.').map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(x.size, y.size)) {
            val c = (x.getOrElse(i) { 0 }).compareTo(y.getOrElse(i) { 0 })
            if (c != 0) return c
        }
        return 0
    }

    /** APK 를 받아 둔다. progress 는 0~1(크기를 모르면 -1). */
    suspend fun download(context: Context, r: Release, progress: (Float) -> Unit): File = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }   // 지난번 받은 것은 지운다
        val out = File(dir, "ChoflixClock-v${r.version}.apk")
        var url = URL(r.apkUrl)
        // 깃허브 내려받기 주소는 다른 호스트로 넘겨준다(리다이렉트) — 직접 따라간다.
        var conn: HttpURLConnection
        var hops = 0
        while (true) {
            conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout = 30_000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "ChoflixClock")
            }
            val code = conn.responseCode
            if (code in 300..399 && hops++ < 5) {
                url = URL(url, conn.getHeaderField("Location"))
                conn.disconnect()
                continue
            }
            if (code != 200) { conn.disconnect(); throw IllegalStateException("HTTP $code") }
            break
        }
        try {
            val total = conn.contentLengthLong
            conn.inputStream.use { input ->
                out.outputStream().use { output ->
                    val buf = ByteArray(64 * 1024)
                    var done = 0L
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        output.write(buf, 0, n)
                        done += n
                        progress(if (total > 0) done.toFloat() / total else -1f)
                    }
                }
            }
        } finally {
            conn.disconnect()
        }
        out
    }

    /** 이 앱이 다른 앱(=새 버전)을 설치해도 되는지. 안 되면 [openInstallPermission] 으로 보낸다. */
    fun canInstall(context: Context): Boolean =
        Build.VERSION.SDK_INT < 26 || context.packageManager.canRequestPackageInstalls()

    fun openInstallPermission(context: Context) {
        context.startActivity(
            Intent(AndroidSettings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    /** 설치 진행·실패 메시지(설정 화면이 보여 준다). */
    val installStatus = MutableStateFlow("")

    /**
     * 앱이 직접 설치한다(PackageInstaller 세션). APK 파일을 설치 화면에 넘기는 방식(ACTION_VIEW)은
     * MIUI 가 자기 보안 설치기로 가로채 검사·차단한다 — 세션 방식은 확인 창 하나만 뜬다.
     * 결과는 [InstallReceiver] 가 받는다. 같은 서명이라 설정은 그대로 남는다.
     */
    fun install(context: Context, apk: File) {
        val pi = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
            setSize(apk.length())
            if (Build.VERSION.SDK_INT >= 31) setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
        }
        val id = pi.createSession(params)
        pi.openSession(id).use { session ->
            session.openWrite("base.apk", 0, apk.length()).use { out ->
                apk.inputStream().use { it.copyTo(out) }
                session.fsync(out)
            }
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
            val callback = PendingIntent.getBroadcast(
                context, id, Intent(context, InstallReceiver::class.java), flags,
            )
            session.commit(callback.intentSender)
        }
    }
}
