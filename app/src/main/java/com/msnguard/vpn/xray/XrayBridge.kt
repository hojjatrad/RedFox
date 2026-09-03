package com.msnguard.vpn.xray

/**
 * پل ثابت به هسته‌ی بومی Xray (Go mobile، AAR ساخته‌شده در core/xray-mobile).
 *
 * دستور ساخت:
 *   gomobile bind -javapkg=com.redfox -target=android/arm64,android/arm .
 * روی بسته‌ی Go به نام `xray`، که کلاس‌های زیر را تولید می‌کند:
 *
 *   com.redfox.xray.Xraycore     (نوعِ export‌شده‌ی XrayCore؛ gomobile
 *                                  نام نوع را lower-camel می‌کند)
 *
 * نمونه‌ی سراسریِ متغیر `Xray` به‌صورت فیلد استاتیک `xray` روی همین کلاس
 * در دسترس است. متدها نیز به lower-camel تبدیل می‌شوند:
 *   StartConfig(String,String) bool  → startConfig(String,String): boolean
 *   AwaitExit()                      → awaitExit()
 *   Stop()                           → stop()
 *
 * اگر AAR در بیلد موجود نباشد، [isAvailable] false می‌شود و پیام خطای روشن
 * به‌جای کرش برمی‌گردد.
 */
object XrayBridge {

    private const val BRIDGE_CLASS = "com.redfox.xray.Xraycore"

    private val bridge: Any? by lazy {
        try {
            val cls = Class.forName(BRIDGE_CLASS)
            // گومتغییر سراسری `Xray` به فیلد استاتیکِ هم‌نامِ lower-camel تبدیل می‌شود.
            try {
                cls.getDeclaredField("xray").get(null)
            } catch (e: NoSuchFieldException) {
                // بعضی نسخه‌های gomobile یک INSTANCE می‌سازند؛ هر دو را امتحان کن.
                try {
                    cls.getDeclaredField("INSTANCE").get(null)
                } catch (_: Throwable) {
                    cls.getDeclaredConstructor().newInstance()
                }
            }
        } catch (e: Throwable) {
            null
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
        return (method.invoke(b, jsonConfig, assetsDir) as? Boolean) ?: false
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
