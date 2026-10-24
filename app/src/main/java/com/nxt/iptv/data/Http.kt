package com.nxt.iptv.data

import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.GZIPInputStream

object Http {
    const val USER_AGENT = "VLC/3.0.21 LibVLC/3.0.21"

    /** Opens [url] following redirects, including http <-> https hops that HttpURLConnection refuses. */
    fun open(url: String, userAgent: String = USER_AGENT, readTimeoutMs: Int = 30_000): InputStream {
        var current = url
        repeat(8) {
            val c = URL(current).openConnection() as HttpURLConnection
            c.instanceFollowRedirects = false
            c.connectTimeout = 15_000
            c.readTimeout = readTimeoutMs
            c.setRequestProperty("User-Agent", userAgent)
            c.setRequestProperty("Accept-Encoding", "gzip")
            val code = c.responseCode
            if (code in 300..399) {
                val location = c.getHeaderField("Location")
                c.disconnect()
                if (location.isNullOrEmpty()) throw IOException("HTTP $code")
                current = URL(URL(current), location).toString()
                return@repeat
            }
            if (code !in 200..299) {
                c.disconnect()
                throw IOException("HTTP $code")
            }
            val stream = c.inputStream
            return if ("gzip".equals(c.contentEncoding, true)) GZIPInputStream(stream) else stream
        }
        throw IOException("Too many redirects")
    }
}
