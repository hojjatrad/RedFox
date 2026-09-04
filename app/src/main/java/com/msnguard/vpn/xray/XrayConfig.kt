package com.msnguard.vpn.xray

import android.net.Uri
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLDecoder

/**
 * یک کانفیگ ورودی کاربر — یا از لینک اشتراک یا یک لینک کانفیگ تکی.
 *
 * [outboundJson] خروجی کاملِ بخش `outbounds[0]` برای هسته‌ی Xray است؛
 * همه‌ی پروتکل‌ها (vless/vmess/trojan/shadowsocks) به همین یک شکل تبدیل
 * می‌شوند تا بقیه‌ی برنامه چیزی درباره‌ی نوع پروتکل نداند.
 */
data class XrayProfile(
    val id: String,
    val tag: String,
    val remark: String,
    val outboundJson: String,
    val sourceSubscription: String? = null,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("tag", tag)
        put("remark", remark)
        put("outbound", JSONObject(outboundJson))
        if (sourceSubscription != null) put("sub", sourceSubscription)
    }

    companion object {
        fun fromJson(o: JSONObject): XrayProfile = XrayProfile(
            id = o.optString("id"),
            tag = o.optString("tag"),
            remark = o.optString("remark"),
            outboundJson = o.getJSONObject("outbound").toString(),
            sourceSubscription = if (o.has("sub")) o.getString("sub") else null,
        )
    }
}

/**
 * لینک‌های کانفیگ (vless://، vmess://، trojan://، ss://) را به outbounds
 * هسته‌ی Xray تبدیل می‌کند. عمداً ساده و متعارف نوشته شده تا با هر کانفیگ
 * استانداردِ پنل‌ها (Marzban، مرزبان، X-UI، sing-box و…) کار کند.
 */
object XrayConfig {

    /**
     * یک خط کانفیگ یا محتوای اشتراک را می‌گیرد و فهرست پروفایل برمی‌گرداند.
     * محتوای اشتراک ممکن است base64 باشد یا متن ساده؛ هر خط غیرخالی یک کانفیگ است.
     */
    fun parseInput(raw: String, subscriptionUrl: String? = null): List<XrayProfile> {
        val text = raw.trim()
        if (text.isEmpty()) return emptyList()

        // Subscription bodies are commonly one big base64 blob.
        val decoded = tryDecodeBase64Blob(text) ?: text
        val out = ArrayList<XrayProfile>()
        decoded.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && it.contains("://") }
            .forEach { line ->
                parseOne(line, subscriptionUrl)?.let(out::add)
            }
        return out
    }

    private fun tryDecodeBase64Blob(text: String): String? {
        if (text.contains("://")) return null // already plain links
        return try {
            val clean = text.replace("\n", "").replace("\r", "").trim()
            val bytes = Base64.decode(clean, Base64.DEFAULT or Base64.URL_SAFE)
            val s = String(bytes, Charsets.UTF_8)
            if (s.contains("://")) s else null
        } catch (e: Exception) {
            null
        }
    }

    private fun parseOne(link: String, sub: String?): XrayProfile? = try {
        when {
            link.startsWith("vless://") -> parseVless(link, sub)
            link.startsWith("vmess://") -> parseVmess(link, sub)
            link.startsWith("trojan://") -> parseTrojan(link, sub)
            link.startsWith("ss://") -> parseShadowsocks(link, sub)
            else -> null
        }
    } catch (e: Exception) {
        null
    }

    private fun remarkFrom(uri: Uri, fallback: String): String {
        val frag = uri.fragment ?: return fallback
        return try {
            URLDecoder.decode(frag, "UTF-8").ifBlank { fallback }
        } catch (e: Exception) {
            fallback
        }
    }

    private fun streamSettings(
        network: String,
        security: String,
        sni: String?,
        fp: String?,
        host: String?,
        path: String?,
        serviceName: String?,
        alpn: String?,
    ): JSONObject = JSONObject().apply {
        put("network", network)
        put("security", security)
        if (security == "tls" || security == "reality") {
            put("tlsSettings", JSONObject().apply {
                if (!sni.isNullOrBlank()) put("serverName", sni)
                if (!fp.isNullOrBlank()) put("fingerprint", fp)
                if (!alpn.isNullOrBlank()) {
                    val arr = JSONArray()
                    alpn.split(",").filter { it.isNotBlank() }.forEach { arr.put(it.trim()) }
                    put("alpn", arr)
                }
                if (security == "reality") {
                    put("realitySettings", JSONObject()) // publicKey/shortId filled by caller
                }
            })
        }
        when (network) {
            "ws" -> put("wsSettings", JSONObject().apply {
                if (!path.isNullOrBlank()) put("path", path)
                if (!host.isNullOrBlank()) put("headers", JSONObject().put("Host", host))
            })
            "grpc" -> put("grpcSettings", JSONObject().apply {
                put("serviceName", serviceName ?: "")
            })
            "tcp" -> {
                // header type http over tcp
                if (!host.isNullOrBlank() || !path.isNullOrBlank()) {
                    put("tcpSettings", JSONObject().apply {
                        put("header", JSONObject().apply {
                            put("type", "http")
                            if (!host.isNullOrBlank()) {
                                val req = JSONObject()
                                val hosts = JSONArray()
                                host.split(",").forEach { hosts.put(it.trim()) }
                                req.put("Host", hosts)
                                if (!path.isNullOrBlank()) req.put("Path", path)
                                put("request", req)
                            }
                        })
                    })
                }
            }
            "h2" -> put("httpSettings", JSONObject().apply {
                if (!path.isNullOrBlank()) put("path", path)
                if (!host.isNullOrBlank()) {
                    val hosts = JSONArray()
                    host.split(",").forEach { hosts.put(it.trim()) }
                    put("host", hosts)
                }
            })
        }
    }

    private fun parseVless(link: String, sub: String?): XrayProfile? {
        val uri = Uri.parse(link)
        val uuid = uri.userInfo ?: return null
        val host = uri.host ?: return null
        val port = if (uri.port > 0) uri.port else 443
        val remark = remarkFrom(uri, "VLESS $host:$port")

        val q = uri.queryParameterNames.associateWith { uri.getQueryParameter(it) ?: "" }
        val type = q["type"] ?: "tcp"
        val security = q["security"] ?: if (port == 443) "tls" else "none"
        val sni = q["sni"]
        val fp = q["fp"]
        val wsHost = q["host"]
        val path = q["path"]
        val serviceName = q["serviceName"]
        val alpn = q["alpn"]
        val pbk = q["pbk"]
        val sid = q["sid"]
        val flow = q["flow"]

        val outbound = JSONObject().apply {
            put("protocol", "vless")
            put("settings", JSONObject().apply {
                put("vnext", JSONArray().put(JSONObject().apply {
                    put("address", host)
                    put("port", port)
                    put("users", JSONArray().put(JSONObject().apply {
                        put("id", uuid)
                        put("encryption", q["encryption"] ?: "none")
                        if (!flow.isNullOrBlank()) put("flow", flow)
                    }))
                }))
            })
            val ss = streamSettings(type, security, sni, fp, wsHost, path, serviceName, alpn)
            if (security == "reality" && !pbk.isNullOrBlank()) {
                val tls = ss.getJSONObject("tlsSettings")
                val rs = tls.getJSONObject("realitySettings").apply {
                    put("publicKey", pbk)
                    if (!sid.isNullOrBlank()) put("shortId", sid)
                }
                tls.put("realitySettings", rs)
            }
            put("streamSettings", ss)
        }
        return XrayProfile(newId(), "proxy", remark, outbound.toString(), sub)
    }

    private fun parseVmess(link: String, sub: String?): XrayProfile? {
        val json = link.removePrefix("vmess://")
        val decoded = String(Base64.decode(json, Base64.DEFAULT or Base64.URL_SAFE), Charsets.UTF_8)
        val o = JSONObject(decoded)
        val host = o.optString("add")
        val port = o.optInt("port", 443)
        if (host.isBlank()) return null
        val remark = o.optString("ps").ifBlank { "VMess $host:$port" }
        val net = o.optString("net", "tcp").ifBlank { "tcp" }
        val tls = if (o.optString("tls") == "tls") "tls" else "none"
        val outbound = JSONObject().apply {
            put("protocol", "vmess")
            put("settings", JSONObject().apply {
                put("vnext", JSONArray().put(JSONObject().apply {
                    put("address", host)
                    put("port", port)
                    put("users", JSONArray().put(JSONObject().apply {
                        put("id", o.optString("id"))
                        put("alterId", o.optInt("aid", 0))
                        put("security", o.optString("scy", "auto").ifBlank { "auto" })
                    }))
                }))
            })
            put("streamSettings", streamSettings(
                network = net,
                security = tls,
                sni = o.optString("sni").ifBlank { null },
                fp = o.optString("fp").ifBlank { null },
                host = o.optString("host").ifBlank { null },
                path = o.optString("path").ifBlank { null },
                serviceName = o.optString("path").ifBlank { null },
                alpn = o.optString("alpn").ifBlank { null },
            ))
        }
        return XrayProfile(newId(), "proxy", remark, outbound.toString(), sub)
    }

    private fun parseTrojan(link: String, sub: String?): XrayProfile? {
        val uri = Uri.parse(link)
        val password = uri.userInfo ?: return null
        val host = uri.host ?: return null
        val port = if (uri.port > 0) uri.port else 443
        val remark = remarkFrom(uri, "Trojan $host:$port")
        val sni = uri.getQueryParameter("sni")
        val fp = uri.getQueryParameter("fp")
        val type = uri.getQueryParameter("type") ?: "tcp"
        val wsHost = uri.getQueryParameter("host")
        val path = uri.getQueryParameter("path")
        val serviceName = uri.getQueryParameter("serviceName")
        val alpn = uri.getQueryParameter("alpn")
        val outbound = JSONObject().apply {
            put("protocol", "trojan")
            put("settings", JSONObject().apply {
                put("servers", JSONArray().put(JSONObject().apply {
                    put("address", host)
                    put("port", port)
                    put("password", password)
                }))
            })
            put("streamSettings", streamSettings(type, "tls", sni, fp, wsHost, path, serviceName, alpn))
        }
        return XrayProfile(newId(), "proxy", remark, outbound.toString(), sub)
    }

    private fun parseShadowsocks(link: String, sub: String?): XrayProfile? {
        // ss://base64(method:password)@host:port#remark  OR  ss://base64(method:password@host:port)#remark
        val after = link.removePrefix("ss://")
        val main = after.substringBefore("#")
        val remark = after.substringAfter("#", "Shadowsocks").let {
            try { URLDecoder.decode(it, "UTF-8") } catch (e: Exception) { it }
        }
        val (method, password, host, port) = if (main.contains("@")) {
            val uri = Uri.parse("ss://$main")
            val userPart = uri.userInfo ?: return null
            val decodedUser = try {
                String(Base64.decode(userPart, Base64.DEFAULT or Base64.URL_SAFE or Base64.NO_PADDING), Charsets.UTF_8)
            } catch (e: Exception) { userPart }
            val mp = decodedUser.split(":", limit = 2)
            val pw = if (mp.size == 2) mp[1] else ""
            Quad(mp[0], pw, uri.host ?: "", if (uri.port > 0) uri.port else 8388)
        } else {
            val decoded = String(Base64.decode(main, Base64.DEFAULT or Base64.URL_SAFE or Base64.NO_PADDING), Charsets.UTF_8)
            val at = decoded.lastIndexOf("@")
            val creds = decoded.substring(0, at)
            val hp = decoded.substring(at + 1)
            val mp = creds.split(":", limit = 2)
            val hostPort = hp.split(":")
            Quad(mp[0], if (mp.size == 2) mp[1] else "", hostPort[0], hostPort.getOrNull(1)?.toIntOrNull() ?: 8388)
        }
        if (host.isBlank()) return null
        val outbound = JSONObject().apply {
            put("protocol", "shadowsocks")
            put("settings", JSONObject().apply {
                put("servers", JSONArray().put(JSONObject().apply {
                    put("address", host)
                    put("port", port)
                    put("method", method)
                    put("password", password)
                }))
            })
            put("streamSettings", JSONObject().put("network", "tcp"))
        }
        return XrayProfile(newId(), "proxy", remark, outbound.toString(), sub)
    }

    private data class Quad<out A, out B, out C, out D>(
        val a: A, val b: B, val c: C, val d: D
    )

    private fun newId(): String =
        java.util.UUID.randomUUID().toString().replace("-", "").take(16)
}
