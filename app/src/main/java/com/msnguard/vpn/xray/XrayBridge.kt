package com.msnguard.vpn.xray

/**
 * پل ثابت به هسته‌ی بومی Xray (Go mobile AAR ساخته‌شده در core/xray-mobile).
 *
 * خروجی واقعی gomobile (تأییدشده روی AAR ساخته‌شده):
 *   - کلاس نوع:  com.redfox.xray.XrayCore
 *       متدهای native: startConfig(String,String):boolean ، awaitExit() ، stop()
 *   - نمونه‌ی سراسری متغیر Go به‌صورت:  com.redfox.xray.Xray.getXray() -> XrayCore
 *
 * اگر AAR موجود نباشد [isAvailable] false می‌شود و پیام خطای روشن برمی‌گردد.
 */
object XrayBridge {

    private const val TYPE_CLASS = "com.redfox.xray.XrayCore"
    private const val HOLDER_CLASS = "com.redfox.xray.Xray"

    private val instance: Any? by lazy {
        try {
            val holder = Class.forName(HOLDER_CLASS)
            holder.getMethod("getXray").invoke(null)
        } catch (e: Throwable) {
            try {
                // Fallback: construct directly (new instance, still functional).
                Class.forName(TYPE_CLASS).getDeclaredConstructor().newInstance()
            } catch (_: Throwable) {
                null
            }
        }
    }

    fun isAvailable(): Boolean = instance != null

    /** کانفیگ JSON را اجرا می‌کند. true یعنی هسته بالا آمد. */
    fun startConfig(jsonConfig: String, assetsDir: String): Boolean {
        val b = instance ?: throw IllegalStateException(
            "هسته‌ی Xray در این بیلد موجود نیست (xray.aar بارگذاری نشد)"
        )
        val m = b.javaClass.getMethod("startConfig", String::class.java, String::class.java)
        return (m.invoke(b, jsonConfig, assetsDir) as? Boolean) ?: false
    }

    /** تا توقف هسته مسدود می‌ماند (روی رشته‌ی فراخوان اجرا شود). */
    fun awaitExit() {
        val b = instance ?: return
        runCatching { b.javaClass.getMethod("awaitExit").invoke(b) }
    }

    fun stop() {
        val b = instance ?: return
        runCatching { b.javaClass.getMethod("stop").invoke(b) }
    }
}
