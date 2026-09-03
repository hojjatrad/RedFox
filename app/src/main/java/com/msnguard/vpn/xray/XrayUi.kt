package com.msnguard.vpn

import android.app.Dialog
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Typeface
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.msnguard.vpn.xray.XrayConfig
import com.msnguard.vpn.xray.XrayProfileStore
import com.msnguard.vpn.xray.XrayProfileStore.Subscription
import com.msnguard.vpn.xray.XraySubscription

/**
 * صفحه‌ی مدیریت کانفیگ‌ها و اشتراک‌های RedFox Xray.
 *
 * از همان ابزارهای UI بقیه‌ی برنامه استفاده می‌کند تا ظاهر و انیمیشن دقیقاً
 * مثل صفحات دیگر باشد: پس‌زمینه‌ی Canvas، ردیف‌های navRow، فونت و فاصله‌ها یکسان.
 *
 * ورودی‌ها:
 *  - لینک اشتراک (http/https): دانلود می‌شود، همه‌ی کانفیگ‌هایش اضافه می‌شود.
 *  - لینک کانفیگ تکی (vless/vmess/trojan/ss): مستقیم اضافه می‌شود.
 */
class XrayScreen(private val activity: MainActivity) {

    private val ctx: Context = activity
    fun dp(v: Int): Int = (v * activity.resources.displayMetrics.density).toInt()

    fun open() {
        val page = FrameLayout(ctx).apply { setBackgroundColor(activity.CANVAS); isClickable = true }
        val scroll = ScrollView(ctx).apply { isVerticalScrollBarEnabled = false }
        val content = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(16), dp(24), dp(24))
        }

        // header
        content.addView(LinearLayout(ctx).apply {
            gravity = Gravity.CENTER_VERTICAL
            addView(activity.createHeaderBackButton { close(page) }, LinearLayout.LayoutParams(dp(48), dp(56)))
            addView(activity.label("کانفیگ‌ها و اشتراک", 22f, activity.INK, TypefaceStyle.MEDIUM))
        })
        content.addView(activity.label("لینک اشتراک یا کانفیگ خود را وارد کنید", 13.5f, activity.MUTED),
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                .apply { leftMargin = dp(48); topMargin = dp(-8); bottomMargin = dp(20) })

        // دکمه‌های افزودن
        content.addView(activity.createSettingsButton("افزودن لینک اشتراک (ساب)") { promptSubscription() },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52)).apply { topMargin = dp(6) })
        content.addView(activity.createSettingsButton("افزودن کانفیگ تکی") { promptConfigLink() },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52)).apply { topMargin = dp(10) })
        content.addView(activity.createSettingsButton("به‌روزرسانی همه‌ی اشتراک‌ها") { refreshAll(content) },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52)).apply { topMargin = dp(10) })

        // فهرست اشتراک‌ها
        content.addView(activity.sectionLabel("اشتراک‌ها"),
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                .apply { topMargin = dp(26) })
        renderSubscriptions(content)

        // فهرست کانفیگ‌ها
        content.addView(activity.sectionLabel("کانفیگ‌ها"),
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                .apply { topMargin = dp(26) })
        renderProfiles(content)

        scroll.addView(content)
        page.addView(scroll, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
        ).apply { topMargin = dp(56) })
        page.addView(buildTopHeader(page), FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(48), Gravity.TOP
        ).apply { leftMargin = dp(24); rightMargin = dp(24); topMargin = dp(8) })

        page.setOnApplyWindowInsetsListener { _, insets ->
            (scroll.layoutParams as FrameLayout.LayoutParams).apply {
                topMargin = insets.systemWindowInsetTop + dp(56)
                bottomMargin = insets.systemWindowInsetBottom
                scroll.layoutParams = this
            }
            insets
        }

        activity.pageHost.addView(page, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        page.requestApplyInsets()
        activity.animatePageOpen(page)
        activity.staggerListItems(content)
    }

    private fun buildTopHeader(page: View): View =
        LinearLayout(ctx).apply { gravity = Gravity.CENTER_VERTICAL }

    private fun close(page: View) {
        activity.animatePageClose(page) { activity.pageHost.removeView(page) }
        activity.refreshSettingsRows()
    }

    private fun renderSubscriptions(content: LinearLayout) {
        val subs = XrayProfileStore.subscriptions(ctx)
        if (subs.isEmpty()) {
            content.addView(activity.label("هنوز اشتراکی اضافه نشده", 13f, activity.MUTED),
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                    .apply { topMargin = dp(8); leftMargin = dp(4) })
            return
        }
        subs.forEach { sub ->
            val count = XrayProfileStore.profiles(ctx).count { it.sourceSubscription == sub.url }
            val row = activity.navRow(sub.name, "$count کانفیگ") {
                activity.choiceDialog(
                    sub.name,
                    listOf("به‌روزرسانی" to { refreshSubscription(content, sub) },
                        "حذف" to { XrayProfileStore.removeSubscription(ctx, sub.url); toast("حذف شد"); reopen(content) }),
                )
            }
            content.addView(row, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(9) })
        }
    }

    private fun renderProfiles(content: LinearLayout) {
        val profiles = XrayProfileStore.profiles(ctx)
        if (profiles.isEmpty()) {
            content.addView(activity.label("هنوز کانفیگی وجود ندارد", 13f, activity.MUTED),
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                    .apply { topMargin = dp(8); leftMargin = dp(4) })
            return
        }
        val activeId = XrayProfileStore.active(ctx)?.id
        profiles.forEach { p ->
            val mark = if (p.id == activeId) "  ●" else ""
            val row = activity.navRow(p.remark + mark, p.tag) {
                activity.choiceDialog(
                    p.remark,
                    listOf(
                        "انتخاب به‌عنوان فعال" to {
                            XrayProfileStore.setActive(ctx, p.id); toast("فعال شد"); reopen(content)
                        },
                        "کپی" to { copyText(p.outboundJson); toast("کپی شد") },
                        "حذف" to { XrayProfileStore.delete(ctx, p.id); reopen(content) },
                    ),
                )
            }
            content.addView(row, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(9) })
        }
    }

    private fun reopen(content: LinearLayout) {
        // ساده: صفحه را دوباره بساز
        (content.parent as? View)?.let { v -> (v.parent as? View)?.let { root -> activity.pageHost.removeView(root) } }
        open()
    }

    private fun promptSubscription() {
        inputDialog("لینک اشتراک (http/https)", "https://…") { url ->
            if (url.startsWith("http")) {
                XrayProfileStore.addSubscription(ctx, url, url.substringAfter("://").take(38))
                doFetch(url, content = null)
            } else toast("لینک باید با http شروع شود")
        }
    }

    private fun promptConfigLink() {
        inputDialog("لینک کانفیگ", "vless://… , vmess://… , trojan://… , ss://…") { link ->
            val parsed = XrayConfig.parseInput(link, null)
            if (parsed.isEmpty()) {
                toast("کانفیگ شناخته نشد — لینک را بررسی کنید")
            } else {
                XrayProfileStore.merge(ctx, parsed, null)
                if (XrayProfileStore.active(ctx) == null) {
                    XrayProfileStore.setActive(ctx, parsed.first().id)
                }
                toast("${parsed.size} کانفیگ اضافه شد")
            }
        }
    }

    private fun refreshAll(content: LinearLayout) {
        val subs = XrayProfileStore.subscriptions(ctx)
        if (subs.isEmpty()) { toast("اشتراکی وجود ندارد"); return }
        toast("در حال به‌روزرسانی…")
        Thread {
            var total = 0
            subs.forEach { sub ->
                runCatching {
                    val res = XraySubscription.fetch(sub.url)
                    if (res.profiles.isNotEmpty()) {
                        XrayProfileStore.merge(ctx, res.profiles, sub.url)
                        total += res.profiles.size
                    }
                }
            }
            activity.runOnUiThread { toast("$total کانفیگ به‌روزرسانی شد"); reopen(content) }
        }.start()
    }

    private fun refreshSubscription(content: LinearLayout, sub: Subscription) {
        doFetch(sub.url, content)
    }

    private fun doFetch(url: String, content: LinearLayout?) {
        toast("در حال دریافت…")
        Thread {
            try {
                val res = XraySubscription.fetch(url)
                activity.runOnUiThread {
                    if (res.profiles.isEmpty()) toast("هیچ کانفیگی از این لینک خوانده نشد")
                    else {
                        XrayProfileStore.merge(ctx, res.profiles, url)
                        if (XrayProfileStore.active(ctx) == null) {
                            XrayProfileStore.setActive(ctx, res.profiles.first().id)
                        }
                        toast("${res.profiles.size} کانفیگ دریافت شد")
                        content?.let { reopen(it) }
                    }
                }
            } catch (e: Exception) {
                activity.runOnUiThread { toast("خطا: ${e.message}") }
            }
        }.start()
    }

    private fun inputDialog(title: String, hint: String, onOk: (String) -> Unit) {
        val dialog = Dialog(ctx).apply { requestWindowFeature(Window.FEATURE_NO_TITLE) }
        val field = EditText(ctx).apply {
            this.hint = hint
            setSingleLine(false)
            minLines = 1
            maxLines = 4
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            setTextColor(activity.INK)
            setHintTextColor(activity.MUTED)
            setPadding(dp(18), dp(14), dp(18), dp(14))
            background = activity.roundedBackground(activity.SURFACE_VARIANT, 16, activity.SURFACE_VARIANT)
        }
        val sheet = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(24), dp(24), dp(24))
            background = activity.roundedBackground(activity.SURFACE, 28, activity.SURFACE)
        }
        sheet.addView(TextView(ctx).apply {
            text = title
            setTextColor(activity.INK)
            textSize = 18f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            .apply { bottomMargin = dp(16) })
        // ردیف دکمه‌ی «جای‌گذاری از کلیپ‌بورد» برای راحتی
        val pasteRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.START
            setPadding(0, dp(10), 0, 0)
        }
        pasteRow.addView(activity.createSettingsButton("جای‌گذاری از کلیپ‌بورد") {
            val clip = (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
            val text = clip.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.text?.toString()
            if (!text.isNullOrBlank()) { field.setText(text.trim()); field.setSelection(field.text.length) }
            else toast("کلیپ‌بورد خالی است")
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(44)))
        sheet.addView(field, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        sheet.addView(pasteRow, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        val buttons = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
            setPadding(0, dp(18), 0, 0)
        }
        buttons.addView(activity.createSettingsButton("انصراف") { dialog.dismiss() },
            LinearLayout.LayoutParams(dp(110), dp(48)).apply { marginEnd = dp(8) })
        buttons.addView(activity.createSettingsButton("تأیید") {
            val v = field.text.toString().trim()
            if (v.isNotEmpty()) { dialog.dismiss(); onOk(v) } else toast("مقدار خالی است")
        }, LinearLayout.LayoutParams(dp(110), dp(48)))
        sheet.addView(buttons)
        dialog.setContentView(sheet)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.show()
    }

    private fun copyText(text: String) {
        val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(android.content.ClipData.newPlainText("redfox", text))
    }

    private fun toast(msg: String) {
        activity.runOnUiThread { Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show() }
    }
}
