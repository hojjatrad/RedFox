<div align="center">

# RedFox 🦊 — کلاینت VPN با هسته‌ی Xray

**نسخه‌ی فارسی و سفارشی‌شده، بر پایه‌ی [MSN-GUARD](https://github.com/mbm110/MSN-GUARD) — با اتصال به سرور و کانفیگ شخصی شما**

</div>

---

## این نسخه چه فرقی با نسخه‌ی اصلی دارد؟

| | توضیح |
|---|---|
| 🇮🇷 **کاملاً فارسی و راست‌چین** | همه‌ی متن‌های رابط، تنظیمات، نوتیفیکیشن و کاشی Quick Settings فارسی است؛ چیدمان RTL و فونت سیستمی فارسی |
| 🦊 **برند RedFox** | نام، لوگو (روباه قرمز)، آیکون و اسپلش اختصاصی |
| ⚙️ **هسته‌ی Xray** | به‌جای مسیرهای مبتنی بر Cloudflare WARP (MASQUE/WireGuard/WARP-on-WARP)، مسیر پیش‌فرض **Xray-core** است |
| 🔗 **لینک اشتراک (Subscription)** | می‌توانید لینک ساب پنل خود (مرزبان، X-UI، Marzban و…) را وارد کنید؛ همه‌ی کانفیگ‌ها خودکار خوانده و به‌روزرسانی می‌شوند |
| 📋 **کانفیگ تکی** | وارد کردن مستقیم لینک‌های `vless://`، `vmess://`، `trojan://`، `ss://` |
| 🎛 **همان دکمه و انیمیشن** | دکمه‌ی اوربیت، انیمیشن اتصال، کارت‌های وضعیت و حس‌وحال رابط دقیقاً مثل نسخه‌ی اصلی حفظ شده |
| 🧩 **مسیرهای یدکی** | Psiphon و Tor به‌عنوان مسیرهای جایگزین (بدون نیاز به سرور) داخل اپ باقی مانده‌اند |

> مسیرهای حذف‌شده از انتخابگر حالت: MASQUE، WireGuard و WARP-on-WARP (وابسته به Cloudflare WARP). اگر کانفیگ ذخیره‌شده‌ی نسخه‌ی قبلی روی این‌ها باشد، خودکار به Xray نگاشت می‌شود.

---

## راهنمای استفاده (برای کاربر نهایی)

۱. اپ را نصب کنید و مجوز VPN را بدهید.
۲. به **تنظیمات ← کانفیگ‌ها و اشتراک** بروید.
۳. **«افزودن لینک اشتراک (ساب)»** را بزنید و لینک ساب پنل خود را جای‌گذاری کنید؛ یا **«افزودن کانفیگ تکی»** را برای یک لینک `vless/vmess/trojan/ss`.
۴. کانفیگ دلخواهتان را **«انتخاب به‌عنوان فعال»** کنید.
۵. به صفحه‌ی اصلی برگردید و دایره را بزنید — همان انیمیشن اتصال همیشگی.

---

## ساخت از سورس

هسته‌ی Xray به‌صورت یک AAR بومی (Go mobile) ساخته و به اپ لینک می‌شود.

### ساده‌ترین راه: GitHub Actions
هر push روی `main` به‌صورت خودکار:
- `libxray.aar` را از [Xray-core](https://github.com/XTLS/Xray-core) می‌سازد،
- فایل‌های `geoip.dat` و `geosite.dat` را برای مسیریابی دامنه‌های ایران دانلود می‌کند،
- APK نهایی را به‌صورت Artifact و (در صورت تگ) به‌صورت Release منتشر می‌کند.

برای ساخت Release امضاشده، این Secretها را در مخزن تنظیم کنید:
`AETHERY_KEYSTORE_BASE64`، `AETHERY_KEYSTORE_PASSWORD`، `AETHERY_KEY_ALIAS`، `AETHERY_KEY_PASSWORD`.

### ساخت محلی
پیش‌نیازها: JDK 17، Android SDK 36 + NDK 26.3، CMake، Go 1.22+، Rust stable + `cargo-ndk`، و `gomobile`.

```bash
go install golang.org/x/mobile/cmd/gomobile@latest && gomobile init
rustup target add aarch64-linux-android armv7-linux-androideabi
cargo install cargo-ndk

# ساخت AAR هسته (یک‌بار، چند دقیقه):
./core/xray-mobile/build-aar.sh

# ساخت APK:
./gradlew assembleDebug -PtargetAbi=arm64-v8a,armeabi-v7a
```

خروجی در `app/build/outputs/apk/debug/`.

---

## معماری اتصال Xray

```
اپ‌ها → رابط TUN (VpnService) → tun2socks/lwIP (C)
      → SOCKS5 لوکال 127.0.0.1:1821 (هسته‌ی Xray، Go)
      → سرور شما (vless/vmess/trojan/ss) → اینترنت
```

فایل‌های مرتبط:
- `app/.../xray/XrayConfig.kt` — پارسر لینک‌های کانفیگ و اشتراک
- `app/.../xray/XrayProfileStore.kt` — ذخیره و مدیریت پروفایل‌ها
- `app/.../xray/XrayConfigBuilder.kt` — ساخت کانفیگ اجرایی Xray (inbound SOCKS + routing ایران)
- `app/.../xray/XrayManager.kt` — راه‌اندازی/توقف هسته
- `app/.../xray/XrayBridge.kt` — پل به AAR بومی
- `core/xray-mobile/` — شیم Go و اسکریپت ساخت AAR

---

## مجوز
نرم‌افزار آزاد، بر پایه‌ی MSN-GUARD با مجوز **AGPL-3.0**. هر تغییری در سورس باید تحت همین مجوز منتشر بماند.
