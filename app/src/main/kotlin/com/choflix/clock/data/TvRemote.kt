package com.choflix.clock.data

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.IOException
import java.io.File
import java.security.KeyFactory
import java.security.Signature
import java.security.cert.CertificateFactory
import java.security.spec.PKCS8EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import java.io.InputStream
import java.math.BigInteger
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.Principal
import java.security.PrivateKey
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import java.security.interfaces.RSAPublicKey
import java.util.Date
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.X509ExtendedKeyManager
import javax.net.ssl.X509TrustManager
import kotlin.coroutines.resume

/**
 * 구글 TV 셋탑을 리모컨처럼 조작한다 — 폰의 'Google TV 리모컨'이 쓰는 방식(Android TV Remote v2).
 *
 *  - 짝짓기(6467): TLS 로 붙어 TV 화면에 6자리 코드를 띄우고, 그 코드로 양쪽 공개키를 묶은 값을 보낸다.
 *  - 조작(6466): 같은 인증서로 붙어 인사를 주고받은 뒤 키 입력(전원 등)을 보낸다.
 *
 * 클라이언트 키는 앱 폴더에만 두고(키 저장소 AES 로 감쌈) 밖으로 보내지 않는다. 셋탑 인증서는 스스로
 * 서명한 것이라, 짝지을 때 본 인증서 지문과 같은지로 확인한다. 메시지는 길이(varint)+protobuf.
 */
object TvRemote {
    private const val OLD_ALIAS = "tv_remote_client"
    private const val WRAP_ALIAS = "tv_remote_wrap"
    private const val ALIAS = "client"
    private const val SERVICE = "_androidtvremote2._tcp"
    private const val REMOTE_PORT = 6466
    private const val PAIR_PORT = 6467
    private const val CLIENT_NAME = "Choflix Clock"

    private const val KEY_POWER = 26L
    private const val KEY_SLEEP = 223L

    data class Found(val name: String, val host: String)

    // ── 찾기 (mDNS) ──

    suspend fun discover(context: Context, ms: Long = 4000): List<Found> {
        val nsd = context.getSystemService(NsdManager::class.java) ?: return emptyList()
        val seen = mutableListOf<NsdServiceInfo>()
        val listener = object : NsdManager.DiscoveryListener {
            override fun onServiceFound(info: NsdServiceInfo) { synchronized(seen) { seen += info } }
            override fun onServiceLost(info: NsdServiceInfo) {}
            override fun onDiscoveryStarted(type: String) {}
            override fun onDiscoveryStopped(type: String) {}
            override fun onStartDiscoveryFailed(type: String, code: Int) {}
            override fun onStopDiscoveryFailed(type: String, code: Int) {}
        }
        nsd.discoverServices(SERVICE, NsdManager.PROTOCOL_DNS_SD, listener)
        try { delay(ms) } finally { runCatching { nsd.stopServiceDiscovery(listener) } }
        // 풀기(resolve)는 한 번에 하나씩만 된다.
        return synchronized(seen) { seen.distinctBy { it.serviceName } }.mapNotNull { resolve(nsd, it) }
    }

    @Suppress("DEPRECATION")
    private suspend fun resolve(nsd: NsdManager, info: NsdServiceInfo): Found? = withTimeoutOrNull(4000) {
        suspendCancellableCoroutine { cont ->
            nsd.resolveService(info, object : NsdManager.ResolveListener {
                override fun onServiceResolved(r: NsdServiceInfo) {
                    if (cont.isActive) cont.resume(r.host?.hostAddress?.let { Found(r.serviceName, it) })
                }
                override fun onResolveFailed(i: NsdServiceInfo, code: Int) {
                    if (cont.isActive) cont.resume(null)
                }
            })
        }
    }

    // ── 짝짓기 ──

    /** TV 화면에 코드를 띄운 채로 기다리는 짝짓기. [submit] 이나 [cancel] 로 끝낸다. */
    class Pairing internal constructor(private val context: Context, private val socket: SSLSocket, private val found: Found) {
        /** 맞으면 null, 코드가 틀리면 그 안내(다시 넣을 수 있다). 셋탑이 거절하면 예외. */
        suspend fun submit(code: String, settings: Settings): String? = withContext(Dispatchers.IO) {
            val c = code.trim().uppercase()
            if (!Regex("[0-9A-F]{6}").matches(c)) return@withContext "TV 에 뜬 6자리(0~9, A~F)를 넣어 주세요"
            val server = socket.session.peerCertificates[0] as X509Certificate
            val ck = client(context).cert.publicKey as RSAPublicKey
            val sk = server.publicKey as RSAPublicKey
            val md = MessageDigest.getInstance("SHA-256")
            listOf(ck.modulus, ck.publicExponent, sk.modulus, sk.publicExponent).forEach { md.update(unsigned(it)) }
            md.update(hex(c.substring(2)))
            val h = md.digest()
            // 코드 앞 두 자리는 이 값의 첫 바이트 — 틀린 코드를 셋탑에 보내기 전에 걸러진다.
            if ((h[0].toInt() and 0xFF) != c.substring(0, 2).toInt(16)) return@withContext "코드가 맞지 않습니다 — 다시 확인해 주세요"
            try {
                exchange(socket, pairing { msg(40) { bytes(1, h) } })
            } finally {
                runCatching { socket.close() }
            }
            settings.tvHost = found.host
            settings.tvName = found.name
            settings.tvCertSha = sha256(server.encoded)
            null
        }

        fun cancel() { runCatching { socket.close() } }
    }

    suspend fun startPairing(context: Context, found: Found): Pairing = withContext(Dispatchers.IO) {
        val app = context.applicationContext
        val s = open(app, found.host, PAIR_PORT, null)
        try {
            exchange(s, pairing { msg(10) { str(1, "atvremote"); str(2, CLIENT_NAME) } })
            // 입력 방식: 16진수 6자리, 우리가 넣는 쪽(INPUT).
            exchange(s, pairing { msg(20) { msg(1) { varint(1, 3); varint(2, 6) }; varint(3, 1) } })
            exchange(s, pairing { msg(30) { msg(1) { varint(1, 3); varint(2, 6) }; varint(2, 1) } })
        } catch (e: Exception) {
            runCatching { s.close() }
            throw e
        }
        Pairing(app, s, found)
    }

    private fun pairing(block: Pb.() -> Unit) = Pb().apply { varint(1, 2); varint(2, 200); block() }.toBytes()

    private fun exchange(s: SSLSocket, msg: ByteArray): Map<Int, List<Any>> {
        send(s, msg)
        val r = recv(s)
        val status = r.long(2)
        if (status != 200L) throw IOException(if (status == 402L) "셋탑이 코드를 거절했습니다" else "셋탑이 거절했습니다($status)")
        return r
    }

    // ── 조작 ──

    /** 셋탑을 대기로. 이미 꺼져 있으면 그대로 둔다. 결과 안내를 돌려준다. */
    suspend fun standby(context: Context, settings: Settings): String = withContext(Dispatchers.IO) {
        if (!settings.tvPaired) throw IOException("셋탑과 연결(짝짓기)되어 있지 않습니다")
        val s = try {
            open(context, settings.tvHost, REMOTE_PORT, settings.tvCertSha)
        } catch (e: IOException) {
            // IP 가 바뀌었을 수 있다(공유기 DHCP) — 이름으로 다시 찾는다.
            val f = discover(context).firstOrNull { it.name == settings.tvName } ?: throw e
            settings.tvHost = f.host
            open(context, f.host, REMOTE_PORT, settings.tvCertSha)
        }
        s.use { sock ->
            var on: Boolean? = null
            var ready = false
            val deadline = System.currentTimeMillis() + 6000
            sock.soTimeout = 1500
            while (System.currentTimeMillis() < deadline) {
                val m = try { recv(sock) } catch (_: SocketTimeoutException) { if (ready) break else continue }
                when {
                    1 in m -> send(sock, Pb().msg(1) {
                        varint(1, 622)
                        msg(2) { str(1, CLIENT_NAME); str(2, "Choflix"); varint(3, 1); str(4, "1"); str(5, "atvremote"); str(6, "1.0.0") }
                    }.toBytes())
                    2 in m -> {
                        send(sock, Pb().msg(2) { varint(1, 622) }.toBytes())
                        ready = true
                        if (on != null) break
                    }
                    8 in m -> send(sock, Pb().msg(9) { varint(1, m.sub(8).long(1) ?: 0) }.toBytes())
                    40 in m -> {
                        on = m.sub(40).long(1) == 1L
                        if (ready) break
                    }
                }
            }
            if (!ready) throw IOException("셋탑이 응답하지 않습니다")
            // 켜져 있다고 하면 전원 키(누를 때마다 켜짐/꺼짐이라 켜져 있을 때만). 아니면 잠자기 키 — 이미 자고 있으면
            // 아무 일도 없다. 캐스트로 음악만 나올 때 셋탑이 '꺼짐'으로 알려 와 그냥 두던 것(v1.6.x 실사용) 때문.
            val result = if (on == true) {
                key(sock, KEY_POWER); "셋탑을 대기로 바꿨습니다"
            } else {
                key(sock, KEY_SLEEP); "셋탑에 대기 명령을 보냈습니다"
            }
            Thread.sleep(500) // 끊기 전에 키가 건너가게.
            result
        }
    }

    private val bg = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** 화면에서 부를 때 — 기다리지 않고, 결과는 짧은 알림으로. */
    fun standbyAsync(context: Context, settings: Settings) {
        val app = context.applicationContext
        bg.launch {
            // 일시정지가 셋탑에 먼저 닿게 조금 기다린다.
            delay(800)
            val msg = runCatching { standby(app, settings) }.getOrElse { "셋탑 대기 실패: ${it.message}" }
            withContext(Dispatchers.Main) { Toast.makeText(app, msg, Toast.LENGTH_SHORT).show() }
        }
    }

    private fun key(s: SSLSocket, code: Long) =
        send(s, Pb().msg(10) { varint(1, code); varint(2, 3) }.toBytes()) // 3 = 짧게 한 번 누름

    // ── TLS ──

    /** 클라이언트 키·인증서. 키 저장소(하드웨어) 키는 셋탑이 고르는 서명 방식을 못 해 TLS 가 깨졌다(v1.6.0) —
     *  보통 RSA 키를 쓰고, 개인키는 키 저장소의 AES 키로 감싸 앱 폴더에만 둔다. */
    private class Client(val cert: X509Certificate, val key: PrivateKey)

    private var client: Client? = null

    @Synchronized
    private fun client(context: Context): Client {
        client?.let { return it }
        val dir = context.filesDir
        val certFile = File(dir, "tv_client.crt")
        val keyFile = File(dir, "tv_client.key")
        val loaded = runCatching {
            val cert = CertificateFactory.getInstance("X.509").generateCertificate(certFile.readBytes().inputStream()) as X509Certificate
            val key = KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(unwrap(keyFile.readBytes())))
            Client(cert, key)
        }.getOrNull()
        val c = loaded ?: run {
            val kp = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
            val der = selfSigned(kp)
            certFile.writeBytes(der)
            keyFile.writeBytes(wrap(kp.private.encoded))
            Client(CertificateFactory.getInstance("X.509").generateCertificate(der.inputStream()) as X509Certificate, kp.private)
        }
        // 예전(v1.6.0) 하드웨어 키는 지운다.
        runCatching { KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry(OLD_ALIAS) }
        client = c
        return c
    }

    private fun wrapKey(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(WRAP_ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(
                KeyGenParameterSpec.Builder(WRAP_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .build(),
            )
        }.generateKey()
    }

    private fun wrap(plain: ByteArray): ByteArray {
        val c = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, wrapKey()) }
        return byteArrayOf(c.iv.size.toByte()) + c.iv + c.doFinal(plain)
    }

    private fun unwrap(data: ByteArray): ByteArray {
        val n = data[0].toInt()
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, wrapKey(), GCMParameterSpec(128, data, 1, n))
        return c.doFinal(data, 1 + n, data.size - 1 - n)
    }

    /** 스스로 서명한 X.509 v3 인증서(CN=atvremote, SHA256withRSA) — DER 을 손으로 짠다. */
    private fun selfSigned(kp: java.security.KeyPair): ByteArray {
        fun tlv(tag: Int, body: ByteArray): ByteArray {
            val len = body.size
            val l = when {
                len < 0x80 -> byteArrayOf(len.toByte())
                len < 0x100 -> byteArrayOf(0x81.toByte(), len.toByte())
                else -> byteArrayOf(0x82.toByte(), (len shr 8).toByte(), len.toByte())
            }
            return byteArrayOf(tag.toByte()) + l + body
        }
        fun seq(vararg parts: ByteArray) = tlv(0x30, parts.fold(ByteArray(0)) { a, b -> a + b })
        val sha256Rsa = seq(byteArrayOf(0x06, 0x09, 0x2A, 0x86.toByte(), 0x48, 0x86.toByte(), 0xF7.toByte(), 0x0D, 0x01, 0x01, 0x0B), byteArrayOf(0x05, 0x00))
        val name = seq(tlv(0x31, seq(byteArrayOf(0x06, 0x03, 0x55, 0x04, 0x03), tlv(0x0C, "atvremote".toByteArray()))))
        val fmt = java.text.SimpleDateFormat("yyMMddHHmmss'Z'", java.util.Locale.US).apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }
        val now = System.currentTimeMillis()
        val validity = seq(
            tlv(0x17, fmt.format(Date(now - 24 * 3600_000L)).toByteArray()),
            tlv(0x17, fmt.format(Date(now + 20L * 365 * 24 * 3600_000L)).toByteArray()),
        )
        val serial = tlv(0x02, BigInteger.valueOf(now).toByteArray())
        val tbs = seq(tlv(0xA0, tlv(0x02, byteArrayOf(2))), serial, sha256Rsa, name, validity, name, kp.public.encoded)
        val sig = Signature.getInstance("SHA256withRSA").run { initSign(kp.private); update(tbs); sign() }
        return seq(tbs, sha256Rsa, tlv(0x03, byteArrayOf(0) + sig))
    }

    private fun open(context: Context, host: String, port: Int, pinnedSha: String?): SSLSocket {
        val c = client(context)
        val cert = c.cert
        val key = c.key
        val km = object : X509ExtendedKeyManager() {
            override fun getClientAliases(keyType: String?, issuers: Array<out Principal>?) = arrayOf(ALIAS)
            override fun chooseClientAlias(keyType: Array<out String>?, issuers: Array<out Principal>?, socket: Socket?) = ALIAS
            override fun getServerAliases(keyType: String?, issuers: Array<out Principal>?): Array<String>? = null
            override fun chooseServerAlias(keyType: String?, issuers: Array<out Principal>?, socket: Socket?): String? = null
            override fun getCertificateChain(alias: String?) = arrayOf(cert)
            override fun getPrivateKey(alias: String?) = key
        }
        val tm = object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
            override fun checkServerTrusted(chain: Array<out X509Certificate>, authType: String?) {
                if (pinnedSha != null && sha256(chain[0].encoded) != pinnedSha) {
                    throw CertificateException("셋탑 인증서가 바뀌었습니다 — 설정에서 다시 연결해 주세요")
                }
            }
            override fun getAcceptedIssuers() = arrayOf<X509Certificate>()
        }
        val ctx = SSLContext.getInstance("TLS").apply { init(arrayOf(km), arrayOf(tm), null) }
        val s = ctx.socketFactory.createSocket() as SSLSocket
        try {
            s.connect(InetSocketAddress(host, port), 5000)
            s.soTimeout = 10_000
            s.startHandshake()
        } catch (e: Exception) {
            runCatching { s.close() }
            throw if (e is IOException) e else IOException(e.message, e)
        }
        return s
    }

    // ── 길이 + protobuf ──

    private fun send(s: SSLSocket, msg: ByteArray) {
        val out = ByteArrayOutputStream()
        writeVarint(out, msg.size.toLong())
        out.write(msg)
        s.outputStream.apply { write(out.toByteArray()); flush() }
    }

    private fun recv(s: SSLSocket): Map<Int, List<Any>> {
        val input = s.inputStream
        val n = readVarint(input).toInt()
        if (n !in 0..65536) throw IOException("이상한 메시지 길이 $n")
        val b = ByteArray(n)
        DataInputStream(input).readFully(b)
        return parse(b)
    }

    /** 필드 번호 → 값들(varint 는 Long, 길이 있는 것은 ByteArray). */
    private fun parse(b: ByteArray): Map<Int, List<Any>> {
        val m = mutableMapOf<Int, MutableList<Any>>()
        val input = b.inputStream()
        while (input.available() > 0) {
            val tag = readVarint(input)
            val field = (tag ushr 3).toInt()
            val v: Any = when ((tag and 7).toInt()) {
                0 -> readVarint(input)
                2 -> ByteArray(readVarint(input).toInt()).also { input.read(it) }
                5 -> ByteArray(4).also { input.read(it) }
                1 -> ByteArray(8).also { input.read(it) }
                else -> throw IOException("모르는 형식")
            }
            m.getOrPut(field) { mutableListOf() } += v
        }
        return m
    }

    private fun Map<Int, List<Any>>.long(f: Int) = this[f]?.firstOrNull() as? Long
    private fun Map<Int, List<Any>>.sub(f: Int) = (this[f]?.firstOrNull() as? ByteArray)?.let(::parse) ?: emptyMap()

    private fun readVarint(input: InputStream): Long {
        var shift = 0
        var r = 0L
        while (true) {
            val b = input.read()
            if (b < 0) throw IOException("셋탑이 연결을 끊었습니다")
            r = r or ((b and 0x7F).toLong() shl shift)
            if (b and 0x80 == 0) return r
            shift += 7
        }
    }

    private fun writeVarint(out: ByteArrayOutputStream, value: Long) {
        var v = value
        while (v and 0x7FL.inv() != 0L) {
            out.write(((v and 0x7F) or 0x80).toInt())
            v = v ushr 7
        }
        out.write(v.toInt())
    }

    private class Pb {
        private val out = ByteArrayOutputStream()
        fun varint(f: Int, v: Long) { writeVarint(out, (f shl 3).toLong()); writeVarint(out, v) }
        fun varint(f: Int, v: Int) = varint(f, v.toLong())
        fun bytes(f: Int, b: ByteArray) { writeVarint(out, ((f shl 3) or 2).toLong()); writeVarint(out, b.size.toLong()); out.write(b) }
        fun str(f: Int, s: String) = bytes(f, s.toByteArray())
        fun msg(f: Int, block: Pb.() -> Unit): Pb { bytes(f, Pb().apply(block).toBytes()); return this }
        fun toBytes(): ByteArray = out.toByteArray()
    }

    /** 부호 없는 큰 수 바이트(앞의 0 은 뗀다) — 짝짓기 값 계산이 이 꼴을 쓴다. */
    private fun unsigned(n: BigInteger): ByteArray = n.toByteArray().dropWhile { it == 0.toByte() }.toByteArray()

    private fun hex(s: String) = ByteArray(s.length / 2) { s.substring(it * 2, it * 2 + 2).toInt(16).toByte() }

    private fun sha256(b: ByteArray) = MessageDigest.getInstance("SHA-256").digest(b).joinToString("") { "%02x".format(it) }
}
