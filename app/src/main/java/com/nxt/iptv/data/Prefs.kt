package com.nxt.iptv.data

import android.content.Context
import org.json.JSONArray

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

    fun clearPlaylist(pid: String) =
        sp.edit().remove("cat_$pid").remove("ch_$pid").remove("fav_$pid").apply()
}
