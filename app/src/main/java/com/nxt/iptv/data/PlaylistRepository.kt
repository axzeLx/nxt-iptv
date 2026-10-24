package com.nxt.iptv.data

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.UUID

class PlaylistRepository(context: Context) {
    private val app = context.applicationContext
    private val prefs = Prefs(app)

    fun all(): List<Playlist> {
        val arr = JSONArray(prefs.playlistsJson)
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            Playlist(
                id = o.getString("id"),
                name = o.getString("name"),
                source = o.getString("source"),
                updated = o.optLong("updated"),
                count = o.optInt("count"),
            )
        }
    }

    fun get(id: String): Playlist? = all().firstOrNull { it.id == id }

    fun newId(): String = UUID.randomUUID().toString().substring(0, 8)

    fun save(p: Playlist) {
        val list = all().toMutableList()
        val i = list.indexOfFirst { it.id == p.id }
        if (i >= 0) list[i] = p else list.add(p)
        write(list)
    }

    fun delete(id: String) {
        write(all().filter { it.id != id })
        cacheFile(id).delete()
        prefs.clearPlaylist(id)
        if (prefs.lastPlaylist == id) prefs.lastPlaylist = null
        forget(id)
    }

    /** Adds the bundled default playlist on first launch only, so deleting it sticks. */
    fun seedDefaults() {
        if (prefs.defaultsSeeded) return
        prefs.defaultsSeeded = true
        if (all().isNotEmpty()) return
        save(Playlist(id = newId(), name = DEFAULT_NAME, source = DEFAULT_URL, updated = 0, count = 0))
    }

    fun hasCache(id: String) = cacheFile(id).exists()

    /**
     * Downloads (or copies) the playlist source into the local cache and parses it.
     * Blocking; call off the main thread. Returns the saved playlist with its new channel count.
     */
    fun fetch(p: Playlist): Playlist {
        val target = cacheFile(p.id)
        val tmp = File(target.path + ".tmp")
        tmp.parentFile?.mkdirs()
        try {
            openSource(p.source).use { input -> tmp.outputStream().use { input.copyTo(it, 64 * 1024) } }
            val data = parse(tmp)
            if (data.channels.isEmpty()) throw EmptyPlaylistException()
            target.delete()
            if (!tmp.renameTo(target)) throw IOException("Could not save playlist")
            remember(p.id, data)
            val updated = p.copy(updated = System.currentTimeMillis(), count = data.channels.size)
            save(updated)
            return updated
        } finally {
            tmp.delete()
        }
    }

    /** Parsed channels for [id], from memory if it was the last one loaded. Blocking. */
    fun load(id: String): PlaylistData {
        synchronized(lock) { if (memoId == id) memoData?.let { return it } }
        if (!hasCache(id)) {
            fetch(get(id) ?: throw IOException("Playlist not found"))
            synchronized(lock) { return memoData!! }
        }
        val data = parse(cacheFile(id))
        remember(id, data)
        return data
    }

    /** Drops the in-memory copy so the next [load] re-reads the cache. */
    fun forget(id: String) = synchronized(lock) {
        if (memoId == id) { memoId = null; memoData = null }
    }

    private fun write(list: List<Playlist>) {
        prefs.playlistsJson = JSONArray(list.map {
            JSONObject()
                .put("id", it.id).put("name", it.name).put("source", it.source)
                .put("updated", it.updated).put("count", it.count)
        }).toString()
    }

    private fun remember(id: String, data: PlaylistData) = synchronized(lock) {
        memoId = id; memoData = data
    }

    private fun parse(file: File): PlaylistData =
        file.inputStream().bufferedReader(Charsets.UTF_8).use { M3uParser.parse(it) }

    private fun openSource(source: String): InputStream = when {
        source.startsWith("http://", true) || source.startsWith("https://", true) -> Http.open(source)
        source.startsWith("content://", true) || source.startsWith("file://", true) ->
            app.contentResolver.openInputStream(Uri.parse(source)) ?: throw IOException("Cannot open file")
        else -> File(source).inputStream()
    }

    private fun cacheFile(id: String) = File(app.filesDir, "playlists/$id.m3u")

    class EmptyPlaylistException : IOException("Empty playlist")

    companion object {
        private const val DEFAULT_NAME = "Türkiye (iptv-org)"
        private const val DEFAULT_URL = "https://iptv-org.github.io/iptv/countries/tr.m3u"

        // The most recently parsed playlist, so going back and forth between screens is instant.
        private val lock = Any()
        private var memoId: String? = null
        private var memoData: PlaylistData? = null
    }
}
