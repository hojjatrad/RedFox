package com.msnguard.vpn.xray

import android.content.Context
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * لینک اشتراک را می‌گیرد، محتوای آن را (متن یا base64) دانلود می‌کند و به
 * پروفایل تبدیل می‌کند. روی رشته‌ی فراخوان اجرا شود؛ شبکه است.
 */
object XraySubscription {

    data class FetchResult(
        val profiles: List<XrayProfile>,
        val rawCount: Int,
    )

    fun fetch(url: String): FetchResult {
        val conn = (URL(url.trim()).openConnection() as HttpURLConnection).apply {
            connectTimeout = 20_000
            readTimeout = 25_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "RedFox/1.0")
        }
        try {
            val code = conn.responseCode
            if (code !in 200..299) throw RuntimeException("HTTP $code")
            val body = conn.inputStream.bufferedReader().readText()
            val profiles = XrayConfig.parseInput(body, url.trim())
            return FetchResult(profiles, body.lineSequence().count { it.contains("://") })
        } finally {
            conn.disconnect()
        }
    }
}

/**
 * راه‌انداز و کنترل‌کننده‌ی هسته‌ی Xray.
 *
 * هسته از طریق AAR بومی (libxray.so / Go-mobile) با همین سه تابع در دسترس است:
 *   - XrayCore.startConfig(jsonConfig, assetsDir): Boolean
 *   - XrayCore.stop()
 *   - XrayCore.isRunning(): Boolean  (اختیاری)
 *
 * این کلاس آن پلِ نازک Kotlin است: فایل کانفیگ را می‌نویسد، هسته را روی رشته‌ی
 * خودش اجرا می‌کند، و پورت SOCKS را تا آماده‌شدن پول می‌کند. هیچ دانشی درباره‌ی
 * نوع پروتکل ندارد؛ همه‌چیز در [XrayConfigBuilder] ساخته شده.
 */
object XrayManager {

    @Volatile private var running = false
    @Volatile private var thread: Thread? = null

    /** پورت SOCKS که هسته روی آن گوش می‌دهد و tun2socks به آن وصل می‌شود. */
    const val SOCKS_PORT = XrayConfigBuilder.SOCKS_PORT

    fun isRunning(): Boolean = running

    /**
     * @return true اگر هسته با موفقیت بالا آمد و پورت SOCKS آماده شد.
     */
    fun start(context: Context, profile: XrayProfile): Boolean {
        if (running) stop()

        val configJson = XrayConfigBuilder.build(profile, SOCKS_PORT)
        val configFile = File(context.filesDir, "xray_config.json")
        configFile.writeText(configJson)
        val assetDir = File(context.filesDir, "xray_assets").apply { mkdirs() }

        // geoip/geosite برای قواعد مسیریابی ایران — اگر موجود نباشد هسته با
        // هشدار بالا می‌آید و فقط قواعد geosite/geoip نادیده گرفته می‌شوند.
        listOf("geoip.dat", "geosite.dat").forEach { name ->
            val out = File(assetDir, name)
            if (!out.exists()) {
                runCatching {
                    context.assets.open("xray/$name").use { input ->
                        out.outputStream().use { input.copyTo(it) }
                    }
                }
            }
        }

        val latch = java.util.concurrent.CountDownLatch(1)
        var started = false

        thread = Thread({
            try {
                val ok = XrayBridge.startConfig(configJson, assetDir.absolutePath)
                started = ok
                running = ok
                latch.countDown()
                if (ok) {
                    // هسته‌ی Go این تابع را تا زمان stop مسدود نگه می‌دارد.
                    XrayBridge.awaitExit()
                }
            } catch (e: Throwable) {
                started = false
                running = false
                latch.countDown()
            } finally {
                running = false
            }
        }, "xray-core")
        thread?.start()
        latch.await(8, java.util.concurrent.TimeUnit.SECONDS)

        if (!started) return false
        // صبر کن پورت SOCKS واقعاً گوش بدهد (tun2socks باید به چیزی وصل شود).
        return waitForPort(SOCKS_PORT, 6_000)
    }

    fun stop() {
        runCatching { XrayBridge.stop() }
        running = false
        thread?.interrupt()
        thread = null
    }

    private fun waitForPort(port: Int, timeoutMs: Long): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            try {
                java.net.Socket().use { s ->
                    s.connect(java.net.InetSocketAddress("127.0.0.1", port), 300)
                    return true
                }
            } catch (e: Exception) {
                Thread.sleep(150)
            }
        }
        return false
    }
}
