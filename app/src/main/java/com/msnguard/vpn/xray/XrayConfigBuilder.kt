package com.msnguard.vpn.xray

import org.json.JSONArray
import org.json.JSONObject

/**
 * کانفیگ کامل اجرایی هسته‌ی Xray را از روی پروفایل انتخاب‌شده می‌سازد.
 *
 * شکل کلی:
 *   - inbound: یک SOCKS5 لوکال روی 127.0.0.1 با پشتیبانی UDP (همان چیزی که
 *     tun2socks به آن وصل می‌شود)
 *   - outbound[0]: پروکسی واقعی کاربر (vless/vmess/trojan/ss)
 *   - outbound[1]: freedom مستقیم (برای رفع‌خطا/مسیرهای مستقیم)
 *   - routing: دامنه‌های ایران مستقیم، تبلیغات بلاک، بقیه از پروکسی
 *   - dns: شبهه‌برانگیز نباشد؛ سرورهای عمومی
 */
object XrayConfigBuilder {

    const val SOCKS_PORT = 1821
    private const val SOCKS_LISTEN = "127.0.0.1"

    fun build(profile: XrayProfile, socksPort: Int = SOCKS_PORT): String {
        val outbound = JSONObject(profile.outboundJson)
        outbound.put("tag", "proxy")

        val freedom = JSONObject().apply {
            put("tag", "direct")
            put("protocol", "freedom")
            put("settings", JSONObject().put("domainStrategy", "UseIP"))
        }
        val block = JSONObject().apply {
            put("tag", "block")
            put("protocol", "blackhole")
        }

        val config = JSONObject()
        config.put("log", JSONObject().apply {
            put("loglevel", "warning")
        })
        config.put("dns", JSONObject().apply {
            put("servers", JSONArray()
                .put("1.1.1.1")
                .put("8.8.8.8")
                .put("https://78.157.42.100/dns-query"))
        })
        config.put("inbounds", JSONArray().put(JSONObject().apply {
            put("tag", "socks-in")
            put("listen", SOCKS_LISTEN)
            put("port", socksPort)
            put("protocol", "socks")
            put("settings", JSONObject().apply {
                put("auth", "noauth")
                put("udp", true)
            })
            put("sniffing", JSONObject().apply {
                put("enabled", true)
                put("destOverride", JSONArray().put("http").put("tls").put("quic"))
            })
        }))
        config.put("outbounds", JSONArray().put(outbound).put(freedom).put(block))
        config.put("routing", buildRouting())
        return config.toString(2)
    }

    private fun buildRouting(): JSONObject {
        val rules = JSONArray()

        // Private / loopback مستقیم
        rules.put(JSONObject().apply {
            put("type", "field")
            put("ip", JSONArray().put("geoip:private"))
            put("outboundTag", "direct")
        })

        // دامنه‌های ایرانی مستقیم (سرعت بیشتر و فشار کمتر روی سرور)
        rules.put(JSONObject().apply {
            put("type", "field")
            put("domain", JSONArray().put("geosite:category-ir").put("geosite:ir"))
            put("outboundTag", "direct")
        })
        rules.put(JSONObject().apply {
            put("type", "field")
            put("ip", JSONArray().put("geoip:ir"))
            put("outboundTag", "direct")
        })

        // تبلیغات و ردیاب‌ها بلاک
        rules.put(JSONObject().apply {
            put("type", "field")
            put("domain", JSONArray().put("geosite:category-ads-all"))
            put("outboundTag", "block")
        })

        return JSONObject().apply {
            put("domainStrategy", "IPIfNonMatch")
            put("rules", rules)
        }
    }
}
