package com.nxt.iptv.ui

import android.app.Activity
import android.app.AlertDialog
import com.nxt.iptv.R
import com.nxt.iptv.data.Prefs

object SettingsDialog {
    private val languages = arrayOf("en" to "English", "tr" to "Türkçe")

    fun show(activity: Activity) {
        val prefs = Prefs(activity)
        val langLabel = languages.firstOrNull { it.first == prefs.language }?.second ?: "English"
        val keysLabel = activity.getString(if (prefs.invertChannelKeys) R.string.keys_inverted else R.string.keys_normal)
        val items = arrayOf(
            "${activity.getString(R.string.language)}:  $langLabel",
            "${activity.getString(R.string.channel_keys)}:  $keysLabel",
        )
        AlertDialog.Builder(activity)
            .setTitle(R.string.settings)
            .setItems(items) { _, which ->
                when (which) {
                    0 -> chooseLanguage(activity, prefs)
                    1 -> {
                        prefs.invertChannelKeys = !prefs.invertChannelKeys
                        show(activity)
                    }
                }
            }
            .show()
    }

    private fun chooseLanguage(activity: Activity, prefs: Prefs) {
        val current = languages.indexOfFirst { it.first == prefs.language }.coerceAtLeast(0)
        AlertDialog.Builder(activity)
            .setTitle(R.string.language)
            .setSingleChoiceItems(languages.map { it.second }.toTypedArray(), current) { dialog, which ->
                dialog.dismiss()
                if (languages[which].first != prefs.language) {
                    prefs.language = languages[which].first
                    activity.recreate()
                }
            }
            .show()
    }
}
