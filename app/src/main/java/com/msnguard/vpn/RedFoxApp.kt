package com.msnguard.vpn

import android.app.Application
import android.content.Context
import android.content.res.Configuration
import java.util.Locale

/**
 * RedFox ships Persian-only. The whole UI is translated and laid out RTL, so we
 * pin the locale to fa-IR at the process level instead of relying on the system
 * language — a phone set to English would otherwise render a half-English app.
 *
 * Both [attachBaseContext] (wraps every component's context) and the locale
 * assignment in [onCreate] (covers code that reads Locale.getDefault(), e.g.
 * number/date formatting) are needed.
 */
class RedFoxApp : Application() {

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(forcePersian(base))
    }

    override fun onCreate() {
        super.onCreate()
        Locale.setDefault(PERSIAN)
    }

    companion object {
        val PERSIAN: Locale = Locale("fa", "IR")

        fun forcePersian(context: Context): Context {
            Locale.setDefault(PERSIAN)
            val config = Configuration(context.resources.configuration)
            config.setLocale(PERSIAN)
            config.setLayoutDirection(PERSIAN)
            return context.createConfigurationContext(config)
        }
    }
}
