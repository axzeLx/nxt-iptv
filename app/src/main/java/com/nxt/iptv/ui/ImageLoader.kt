package com.nxt.iptv.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import android.view.View
import android.widget.ImageView
import com.nxt.iptv.data.Http
import java.io.File
import java.security.MessageDigest
import java.util.Collections
import java.util.concurrent.LinkedBlockingDeque
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/** Tiny channel-logo loader: memory + disk cache, newest request first so fast scrolling stays snappy. */
object ImageLoader {
    private val memory = object : LruCache<String, Bitmap>((Runtime.getRuntime().maxMemory() / 16).toInt()) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }
    private val failed = Collections.synchronizedSet(HashSet<String>())
    private val main = Handler(Looper.getMainLooper())
    private val pool = ThreadPoolExecutor(3, 3, 30, TimeUnit.SECONDS, object : LinkedBlockingDeque<Runnable>() {
        override fun offer(e: Runnable) = offerFirst(e)
    })
    private var diskDir: File? = null

    fun init(context: Context) {
        if (diskDir == null) diskDir = File(context.cacheDir, "logos").apply { mkdirs() }
    }

    /** Loads [url] into [view]; the view is GONE when there is no logo so rows don't keep an empty gap. */
    fun load(url: String?, view: ImageView, sizePx: Int) {
        view.tag = url
        if (url.isNullOrBlank() || url in failed) {
            view.setImageDrawable(null)
            view.visibility = View.GONE
            return
        }
        view.visibility = View.VISIBLE
        memory.get(url)?.let {
            view.setImageBitmap(it)
            return
        }
        view.setImageDrawable(null)
        pool.execute {
            if (view.tag != url) return@execute // row was recycled before we got to it
            val bitmap = try {
                fetch(url, sizePx)
            } catch (_: Throwable) {
                null
            }
            main.post {
                if (bitmap == null) failed.add(url) else memory.put(url, bitmap)
                if (view.tag == url) {
                    if (bitmap != null) view.setImageBitmap(bitmap) else view.visibility = View.GONE
                }
            }
        }
    }

    private fun fetch(url: String, sizePx: Int): Bitmap? {
        val file = diskDir?.let { File(it, md5(url)) }
        if (file == null || !file.exists()) {
            val bytes = Http.open(url, readTimeoutMs = 10_000).use { it.readBytes() }
            file?.writeBytes(bytes)
            return decode(bytes, sizePx)
        }
        return decode(file.readBytes(), sizePx)
    }

    private fun decode(bytes: ByteArray, sizePx: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0) return null
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= sizePx) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
    }

    private fun md5(s: String): String =
        MessageDigest.getInstance("MD5").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }
}
