package com.nxt.iptv.data

/** A saved playlist. [source] is an http(s) URL, a content:// URI or an absolute file path. */
data class Playlist(
    val id: String,
    val name: String,
    val source: String,
    val updated: Long,
    val count: Int,
) {
    val isRemote: Boolean
        get() = source.startsWith("http://", true) || source.startsWith("https://", true)
}

class Channel(
    /** 1-based position in the playlist, shown to the user and used for number-key zapping. */
    val number: Int,
    val name: String,
    /** Quality tag stripped from the name, e.g. "720p"; shown under the preview instead. */
    val quality: String?,
    val url: String,
    val logo: String?,
    /** Group title as found in the playlist; empty when the entry has none. */
    val group: String,
    val userAgent: String?,
    val referrer: String?,
)

class PlaylistData(val channels: List<Channel>) {
    /** Group names in first-appearance order. */
    val groups: List<String>
    val byGroup: Map<String, List<Channel>>

    init {
        val map = LinkedHashMap<String, ArrayList<Channel>>()
        for (c in channels) map.getOrPut(c.group) { ArrayList() }.add(c)
        byGroup = map
        groups = map.keys.toList()
    }
}
