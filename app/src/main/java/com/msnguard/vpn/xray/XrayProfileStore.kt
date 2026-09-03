package com.msnguard.vpn.xray

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/**
 * ذخیره‌سازی پروفایل‌های Xray (کانفیگ‌های واردشده و اشتراک‌ها).
 *
 * روی همان SharedPreferences برنامه نگهداری می‌شود تا چرخه‌ی حیات ساده بماند؛
 * حجم هر کانفیگ کم است و حتی چند هزار کانفیگ اشتراک هم در حد مجاز پیش‌فرض‌هاست.
 */
object XrayProfileStore {

    private const val PREFS = "redfox_xray"
    private const val KEY_PROFILES = "profiles"
    private const val KEY_ACTIVE = "active_id"
    private const val KEY_SUBSCRIPTIONS = "subscriptions"

    data class Subscription(
        val url: String,
        val name: String,
        val updatedAt: Long,
    ) {
        fun toJson() = JSONObject().apply {
            put("url", url); put("name", name); put("updated", updatedAt)
        }
        companion object {
            fun fromJson(o: JSONObject) = Subscription(
                o.getString("url"), o.optString("name", o.getString("url")), o.optLong("updated", 0L)
            )
        }
    }

    private fun prefs(ctx: Context): SharedPreferences =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun profiles(ctx: Context): MutableList<XrayProfile> {
        val raw = prefs(ctx).getString(KEY_PROFILES, null) ?: return mutableListOf()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { XrayProfile.fromJson(arr.getJSONObject(it)) }.toMutableList()
        } catch (e: Exception) { mutableListOf() }
    }

    fun active(ctx: Context): XrayProfile? {
        val id = prefs(ctx).getString(KEY_ACTIVE, null) ?: return profiles(ctx).firstOrNull()
        return profiles(ctx).firstOrNull { it.id == id }
    }

    fun setActive(ctx: Context, id: String) {
        prefs(ctx).edit().putString(KEY_ACTIVE, id).apply()
    }

    fun save(ctx: Context, list: List<XrayProfile>) {
        val arr = JSONArray()
        list.forEach { arr.put(it.toJson()) }
        prefs(ctx).edit().putString(KEY_PROFILES, arr.toString()).apply()
    }

    /**
     * کانفیگ‌های جدید را اضافه می‌کند؛ تکراری‌ها (با همان remark+مقصد) حذف می‌شوند.
     * کانفیگ‌هایی که از همین اشتراک آمده بودند جایگزین می‌شوند، کانفیگ‌های دستی دست‌نخورده می‌مانند.
     */
    fun merge(ctx: Context, newOnes: List<XrayProfile>, subscriptionUrl: String?) {
        val current = profiles(ctx)
        val result = ArrayList<XrayProfile>()
        // keep entries that did NOT come from this subscription, and drop dupes
        for (p in current) {
            if (subscriptionUrl != null && p.sourceSubscription == subscriptionUrl) continue
            if (newOnes.none { it.remark == p.remark && it.outboundJson == p.outboundJson }) {
                result.add(p)
            }
        }
        result.addAll(newOnes)
        save(ctx, result)
    }

    fun delete(ctx: Context, id: String) {
        save(ctx, profiles(ctx).filterNot { it.id == id })
    }

    fun subscriptions(ctx: Context): MutableList<Subscription> {
        val raw = prefs(ctx).getString(KEY_SUBSCRIPTIONS, null) ?: return mutableListOf()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { Subscription.fromJson(arr.getJSONObject(it)) }.toMutableList()
        } catch (e: Exception) { mutableListOf() }
    }

    fun addSubscription(ctx: Context, url: String, name: String) {
        val subs = subscriptions(ctx)
        if (subs.none { it.url == url }) {
            subs.add(Subscription(url.trim(), name.ifBlank { url.trim().take(40) }, System.currentTimeMillis()))
            val arr = JSONArray()
            subs.forEach { arr.put(it.toJson()) }
            prefs(ctx).edit().putString(KEY_SUBSCRIPTIONS, arr.toString()).apply()
        }
    }

    fun removeSubscription(ctx: Context, url: String) {
        val subs = subscriptions(ctx).filterNot { it.url == url }
        val arr = JSONArray()
        subs.forEach { arr.put(it.toJson()) }
        prefs(ctx).edit().putString(KEY_SUBSCRIPTIONS, arr.toString()).apply()
        // profiles fetched from this sub go too
        save(ctx, profiles(ctx).filterNot { it.sourceSubscription == url })
    }
}
