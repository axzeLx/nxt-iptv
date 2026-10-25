package com.nxt.iptv.ui

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import com.nxt.iptv.R
import com.nxt.iptv.data.Prefs

object SettingsDialog {
    private val languages = arrayOf("en" to "English", "tr" to "Türkçe")

    /** Android's "High contrast text" switch; it turns colored text white and adds dark outlines. */
    private const val HIGH_CONTRAST = "high_text_contrast_enabled"

    /** Accessibility screens to try, for TVs (like Whale OS) that hide them from their own menus. */
    private val accessibilityScreens = listOf(
        Intent("android.settings.ACCESSIBILITY_TV_OEM_LINK"),
        Intent().setClassName("com.android.tv.settings", "com.android.tv.settings.oemlink.AccessibilitySettingsActivity"),
        Intent().setClassName("com.android.tv.settings", "com.android.tv.settings.accessibility.AccessibilityActivity"),
        Intent().setClassName("com.android.tv.settings", "com.android.tv.settings.system.AccessibilityActivity"),
        Intent().setClassName("com.android.settings", "com.android.settings.Settings\$AccessibilitySettingsActivity"),
        // Last: the generic action, which some TV builds route to a do-nothing stub.
        Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS),
    )

    fun show(activity: Activity) {
        val prefs = Prefs(activity)
        val langLabel = languages.firstOrNull { it.first == prefs.language }?.second ?: "English"
        val keysLabel = activity.getString(if (prefs.invertChannelKeys) R.string.keys_inverted else R.string.keys_normal)
        val contrastLabel = activity.getString(if (isHighContrast(activity)) R.string.on else R.string.off)
        val items = arrayOf(
            "${activity.getString(R.string.language)}:  $langLabel",
            "${activity.getString(R.string.channel_keys)}:  $keysLabel",
            "${activity.getString(R.string.idle_check)}:  ${idleLabel(activity, prefs.idleHours)}",
            "${activity.getString(R.string.high_contrast)}:  $contrastLabel",
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
                    2 -> chooseIdleHours(activity, prefs)
                    3 -> toggleHighContrast(activity)
                }
            }
            .show()
    }

    private val idleChoices = intArrayOf(1, 2, 3, 4, 0)

    private fun idleLabel(activity: Activity, hours: Int) =
        if (hours == 0) activity.getString(R.string.off) else activity.getString(R.string.idle_after_hours, hours)

    private fun chooseIdleHours(activity: Activity, prefs: Prefs) {
        val current = idleChoices.indexOf(prefs.idleHours).coerceAtLeast(0)
        AlertDialog.Builder(activity)
            .setTitle(R.string.idle_check)
            .setSingleChoiceItems(idleChoices.map { idleLabel(activity, it) }.toTypedArray(), current) { dialog, which ->
                dialog.dismiss()
                prefs.idleHours = idleChoices[which]
                show(activity)
            }
            .show()
    }

    private fun isHighContrast(activity: Activity): Boolean =
        runCatching { Settings.Secure.getInt(activity.contentResolver, HIGH_CONTRAST, 0) == 1 }.getOrDefault(false)

    private fun toggleHighContrast(activity: Activity) {
        val target = if (isHighContrast(activity)) 0 else 1
        // Works when the TV grants WRITE_SECURE_SETTINGS, as some vendor builds do.
        val written = runCatching { Settings.Secure.putInt(activity.contentResolver, HIGH_CONTRAST, target) }
            .getOrDefault(false)
        if (written && (if (isHighContrast(activity)) 1 else 0) == target) {
            Toast.makeText(activity, R.string.high_contrast_changed, Toast.LENGTH_SHORT).show()
            show(activity)
            return
        }
        // Otherwise hand over to the system accessibility screen, even if the TV menu hides it.
        val candidates = accessibilityScreens.filter { intent ->
            val target = activity.packageManager.resolveActivity(intent, 0)?.activityInfo
            target != null && !target.packageName.contains("stub", ignoreCase = true)
        } + Intent(Settings.ACTION_SETTINGS)
        openFirstWorking(activity, candidates, 0)
    }

    /**
     * Some screens start and immediately close themselves (e.g. "not supported in current flavor"),
     * so after each launch we check whether we really lost the screen before trying the next one.
     */
    private fun openFirstWorking(activity: Activity, intents: List<Intent>, index: Int) {
        if (activity.isFinishing) return
        if (index >= intents.size) {
            Toast.makeText(activity, R.string.high_contrast_unavailable, Toast.LENGTH_LONG).show()
            return
        }
        val started = runCatching {
            activity.startActivity(Intent(intents[index]).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }.isSuccess
        if (!started) return openFirstWorking(activity, intents, index + 1)
        activity.window.decorView.postDelayed({
            if (activity.hasWindowFocus()) {
                openFirstWorking(activity, intents, index + 1)
            } else {
                Toast.makeText(activity, R.string.high_contrast_manual, Toast.LENGTH_LONG).show()
            }
        }, 1_500)
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
