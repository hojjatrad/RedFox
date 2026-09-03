package com.msnguard.vpn.xray

/**
 * پل ثابت به هسته‌ی بومی Xray (Go mobile، بسته‌ی `libxray.aar`).
 *
 * این سه تابع توسط AAR ساخته‌شده در فاز بیلد پیاده‌سازی می‌شوند
 * (نگاه کنید به `core/xray-mobile/` و گردش‌کار GitHub). امضای آن‌ها با
 * shim مربوطه هم‌خوان است؛ اگر AAR موجود نباشد، تلاش برای بارگذاری کلاس
 * به‌جای کرش، استثنای روشن برمی‌گرداند.
 */
object XrayBridge {

    private const val BRIDGE_CLASS = "com.redfox.xray.XrayCore"

    private val bridge: Any? by lazy {
        try {
            val cls = Class.forName(BRIDGE_CLASS)
            cls.getDeclaredField("INSTANCE").get(null) // Kotlin object
        } catch (e: ClassNotFoundException) {
            null
        } catch (e: Throwable) {
            try { Class.forName(BRIDGE_CLASS) } catch (_: Throwable) { null }
        }
    }

    fun isAvailable(): Boolean = bridge != null

    /** کانفیگ JSON را اجرا می‌کند. خروجی true یعنی هسته بالا آمد. */
    fun startConfig(jsonConfig: String, assetsDir: String): Boolean {
        val b = bridge ?: throw IllegalStateException(
            "هسته‌ی Xray در این بیلد موجود نیست (libxray.aar ساخته نشده)"
        )
        val method = b.javaClass.getMethod(
            "startConfig", String::class.java, String::class.java
        )
        return method.invoke(b, jsonConfig, assetsDir) as? Boolean ?: false
    }

    /** تا توقف هسته مسدود می‌ماند (روی رشته‌ی فراخوان اجرا شود). */
    fun awaitExit() {
        val b = bridge ?: return
        runCatching { b.javaClass.getMethod("awaitExit").invoke(b) }
    }

    fun stop() {
        val b = bridge ?: return
        runCatching { b.javaClass.getMethod("stop").invoke(b) }
    }
}
