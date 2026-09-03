#!/usr/bin/env python3
"""Second pass: remaining UI strings + MSN-GUARD -> RedFox rebrand in UI text."""
import sys, io

M = {
    # rebrand in user-visible text
    "MSN-GUARD log": "گزارش RedFox",
    "All apps use MSN-GUARD": "همه‌ی برنامه‌ها از RedFox استفاده می‌کنند",
    "Choose how MSN-GUARD connects": "انتخاب نحوه‌ی اتصال RedFox",
    "Choose which apps use MSN-GUARD. Changes apply next connection.": "انتخاب کنید کدام برنامه‌ها از RedFox استفاده کنند. تغییرات در اتصال بعدی اعمال می‌شوند.",
    "SECURED BY MSN-GUARD": "محافظت‌شده با RedFox",
    "MSN-GUARD is running": "RedFox در حال اجراست",
    # main screen
    "TAP TO CONNECT": "برای اتصال بزنید",
    "Connect": "اتصال",
    "Disconnecting": "در حال قطع‌کردن",
    "Disconnect": "قطع کردن",
    "Reconnect": "اتصال مجدد",
    "CONNECTING": "در حال اتصال",
    "CURRENT": "فعلی",
    "CHAINED": "زنجیره‌ای",
    "EXIT NODE": "گره خروج",
    "YOUR IP": "IP شما",
    "ROUTING": "مسیریابی",
    "SESSION": "نشست",
    "Scanning": "در حال اسکن",
    "Starting": "در حال شروع",
    "Tunnel": "تونل",
    "No data yet": "هنوز داده‌ای رد نشده",
    "No connection events yet": "هنوز رویداد اتصالی ثبت نشده",
    "No apps selected for tunnel. Connection aborted for safety.": "هیچ برنامه‌ای برای تونل انتخاب نشده. برای ایمنی اتصال لغو شد.",
    "Selected apps are no longer installed. Connection aborted.": "برنامه‌های انتخاب‌شده دیگر نصب نیستند. اتصال لغو شد.",
    "IP unavailable, tap to retry": "IP در دسترس نیست، برای تلاش مجدد بزنید",
    "Ping connection": "پینگ اتصال",
    "Ping unavailable": "پینگ ناموجود",
    "Could not connect on this carrier. Try Wi-Fi or another SIM.": "روی این اپراتور اتصال ممکن نشد. وای‌فای یا سیم‌کارت دیگری را امتحان کنید.",
    "Check the server and try again": "سرور را بررسی کنید و دوباره تلاش کنید",
    "Block all traffic if the tunnel drops": "اگر تونل قطع شد، کل ترافیک مسدود شود",
    "Reconnect automatically if the tunnel drops": "در صورت قطع تونل، خودکار وصل شود",
    "Auto reconnect": "اتصال مجدد خودکار",
    "Checking that traffic really passes": "در حال بررسی عبور واقعی ترافیک",
    "Measuring the tunnel exit address": "در حال اندازه‌گیری آدرس خروجی تونل",
    "Could not start whole-device routing": "مسیریابی کل دستگاه شروع نشد",
    "Could not start device routing": "مسیریابی دستگاه شروع نشد",
    "device routing stopped": "مسیریابی دستگاه متوقف شد",
    "Native tunnel stopped unexpectedly": "تونل بومی به‌طور غیرمنتظره متوقف شد",
    "Aether tunnel already running": "تونل از قبل در حال اجراست",
    "already running": "از قبل در حال اجراست",
    "Contents hidden": "محتوا پنهان است",
    # mode screen
    "Germany": "آلمان",
    # perf / scan
    "Scanner options": "گزینه‌های اسکنر",
    "Scale scan concurrency and buffers to match your hardware": "تنظیم همزمانی و بافر اسکن متناسب با سخت‌افزار شما",
    "Detect hardware and scale accordingly": "تشخیص خودکار سخت‌افزار و تنظیم متناسب",
    "Desktop and powerful devices": "رایانه و دستگاه‌های قدرتمند",
    "Routers and constrained devices": "روترها و دستگاه‌های محدود",
    "DESKTOP ONLY": "فقط رایانه",
    "Quiet, patient probing": "کاوش آرام و صبورانه",
    "Lower overhead on mild filtering": "سربار کمتر در فیلترینگ ملایم",
    "Recommended filtering resistance": "مقاومت توصیه‌شده در برابر فیلترینگ",
    "Start a new scan every connection": "هر اتصال یک اسکن تازه",
    "IP VERSION": "نسخه‌ی IP",
    "JUNK COUNT (JC)": "تعداد بسته‌ی جعلی (JC)",
    "JUNK MIN (JMIN)": "حداقل جعلی (JMIN)",
    "Packet pattern is too long": "الگوی بسته بیش از حد طولانی است",
    "Use numeric IP:port": "از IP:port عددی استفاده کنید",
    # tor screen
    "How Tor reaches the network. Auto tries each method until one works.": "نحوه‌ی دسترسی تور به شبکه؛ خودکار هر روش را تا موفقیت امتحان می‌کند.",
    "Starting Tor…": "در حال شروع تور…",
    "Tor could not connect over ": "تور از این مسیر وصل نشد: ",
    "Tor could not connect with any method on this network": "تور با هیچ روشی روی این شبکه وصل نشد",
    "for when neither exit IP is accepted": "برای زمانی که هیچ IP خروجی پذیرفته نمی‌شود",
    "any method": "هر روش",
    # logs / misc
    "connecting": "در حال اتصال",
    "connected": "متصل",
    "disconnected": "قطع",
    "disconnect": "قطع",
    "disconnect to change": "برای تغییر، اتصال را قطع کنید",
    "cannot be changed right now": "الان قابل تغییر نیست",
    "does not apply to this transport": "برای این مسیر اعمال نمی‌شود",
    "does not apply": "اعمال نمی‌شود",
    "include": "شامل",
    "fronted first": "ابتدا روش پوشش‌دار",
    "auto transport": "مسیر خودکار",
    "cannot tell": "قابل تشخیص نیست",
    "count": "تعداد",
    "country": "کشور",
    "config": "کانفیگ",
    "connected via ": "متصل از طریق ",
    "in-proxy (peer relay)": "این‌پروکسی (رله‌ی همتا)",
}

for path in sys.argv[1:]:
    with io.open(path, encoding="utf-8") as f:
        src = f.read()
    for en, fa in M.items():
        src = src.replace('"%s"' % en, '"%s"' % fa)
    with io.open(path, "w", encoding="utf-8") as f:
        f.write(src)
    print("updated", path)
