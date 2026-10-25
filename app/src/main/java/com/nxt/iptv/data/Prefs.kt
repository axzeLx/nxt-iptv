package com.nxt.iptv.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class Prefs(context: Context) {
    private val sp = context.applicationContext.getSharedPreferences("nxt", Context.MODE_PRIVATE)

    var language: String
        get() = sp.getString("lang", "en")!!
        set(v) = sp.edit().putString("lang", v).apply()

    /** When true, Up moves to the next channel in fullscreen (TV-remote style). */
    var invertChannelKeys: Boolean
        get() = sp.getBoolean("invert_keys", false)
        set(v) = sp.edit().putBoolean("invert_keys", v).apply()

    /** Hours without any remote input before asking "still watching?"; 0 = never ask. */
    var idleHours: Int
        get() = sp.getInt("idle_hours", 3)
        set(v) = sp.edit().putInt("idle_hours", v).apply()

    /** Show the "Recently watched" category and record into it. */
    var historyEnabled: Boolean
        get() = sp.getBoolean("hist_on", true)
        set(v) = sp.edit().putBoolean("hist_on", v).apply()

    /** How many recently watched channels to list. */
    var historySize: Int
        get() = sp.getInt("hist_size", 12)
        set(v) = sp.edit().putInt("hist_size", v).apply()

    /** A channel must stay on this long to count as watched (skips quick zapping). */
    var historyMinSeconds: Int
        get() = sp.getInt("hist_min_s", 10)
        set(v) = sp.edit().putInt("hist_min_s", v).apply()

    /** In fullscreen, Left jumps between the current and the previous channel. */
    var leftKeyPrevious: Boolean
        get() = sp.getBoolean("left_prev", true)
        set(v) = sp.edit().putBoolean("left_prev", v).apply()

    var lastPlaylist: String?
        get() = sp.getString("last_playlist", null)
        set(v) = sp.edit().putString("last_playlist", v).apply()

    /** Whether the built-in default playlist has been added once already. */
    internal var defaultsSeeded: Boolean
        get() = sp.getBoolean("seeded", false)
        set(v) = sp.edit().putBoolean("seeded", v).apply()

    internal var playlistsJson: String
        get() = sp.getString("playlists", "[]")!!
        set(v) = sp.edit().putString("playlists", v).apply()

    fun lastCategory(pid: String): String? = sp.getString("cat_$pid", null)
    fun setLastCategory(pid: String, key: String) = sp.edit().putString("cat_$pid", key).apply()

    fun lastChannel(pid: String): String? = sp.getString("ch_$pid", null)
    fun setLastChannel(pid: String, url: String) = sp.edit().putString("ch_$pid", url).apply()

    /** Favorite channel URLs in the order they were added. */
    fun favorites(pid: String): LinkedHashSet<String> {
        val arr = JSONArray(sp.getString("fav_$pid", "[]"))
        return LinkedHashSet<String>().apply { for (i in 0 until arr.length()) add(arr.getString(i)) }
    }

    fun setFavorites(pid: String, urls: Collection<String>) =
        sp.edit().putString("fav_$pid", JSONArray(urls).toString()).apply()

    /** Recently watched channel URLs with the time they were last watched, newest first. */
    fun history(pid: String): List<Pair<String, Long>> {
        val arr = JSONArray(sp.getString("hist_$pid", "[]"))
        return (0 until arr.length()).map { arr.getJSONObject(it) }.map { it.getString("u") to it.getLong("t") }
    }

    fun addHistory(pid: String, url: String, time: Long) {
        val list = listOf(url to time) + history(pid).filter { it.first != url }
        sp.edit().putString(
            "hist_$pid",
            JSONArray(list.take(MAX_HISTORY).map { JSONObject().put("u", it.first).put("t", it.second) }).toString(),
        ).apply()
    }

    fun clearAllHistory() {
        val edit = sp.edit()
        sp.all.keys.filter { it.startsWith("hist_") && it != "hist_on" && it != "hist_size" && it != "hist_min_s" }
            .forEach { edit.remove(it) }
        edit.apply()
    }

    fun clearPlaylist(pid: String) =
        sp.edit().remove("cat_$pid").remove("ch_$pid").remove("fav_$pid").remove("hist_$pid").apply()

    companion object {
        /** Stored entries; the visible count ([historySize]) can be at most this. */
        const val MAX_HISTORY = 30
    }
}
