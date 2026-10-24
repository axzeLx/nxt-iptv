package com.nxt.iptv.ui

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import com.nxt.iptv.data.Prefs
import java.util.Locale

/** Applies the in-app language choice (English by default, Turkish optional). */
abstract class BaseActivity : Activity() {
    override fun attachBaseContext(base: Context) {
        val locale = Locale.forLanguageTag(Prefs(base).language)
        val config = Configuration(base.resources.configuration)
        config.setLocale(locale)
        super.attachBaseContext(base.createConfigurationContext(config))
    }
}
