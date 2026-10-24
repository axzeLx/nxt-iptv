package com.nxt.iptv.data

import java.io.BufferedReader

object M3uParser {
    private val attrRegex = Regex("([A-Za-z0-9_-]+)=\"([^\"]*)\"")
    private val qualityRegex = Regex("\\s*[(\\[](\\d{3,4}[pi]|4K|UHD|FHD|HD|SD)[)\\]]", RegexOption.IGNORE_CASE)

    fun parse(reader: BufferedReader): PlaylistData {
        val channels = ArrayList<Channel>(1024)
        var name: String? = null
        var logo: String? = null
        var group: String? = null
        var userAgent: String? = null
        var referrer: String? = null

        reader.forEachLine { raw ->
            val line = raw.trim()
            when {
                line.isEmpty() -> Unit
                line.startsWith("#EXTINF", ignoreCase = true) -> {
                    logo = null; group = null; userAgent = null; referrer = null
                    val comma = titleComma(line)
                    val attrs = if (comma > 0) line.substring(0, comma) else line
                    var tvgName: String? = null
                    for (m in attrRegex.findAll(attrs)) {
                        val value = m.groupValues[2].trim()
                        when (m.groupValues[1].lowercase()) {
                            "tvg-logo" -> logo = value.ifEmpty { null }
                            "group-title" -> group = value
                            "tvg-name" -> tvgName = value
                        }
                    }
                    val title = if (comma >= 0) line.substring(comma + 1).trim() else ""
                    name = title.ifEmpty { tvgName }
                }
                line.startsWith("#EXTGRP:", ignoreCase = true) -> {
                    if (group.isNullOrEmpty()) group = line.substring(8).trim()
                }
                line.startsWith("#EXTVLCOPT:", ignoreCase = true) -> {
                    val opt = line.substring(11).trim()
                    val eq = opt.indexOf('=')
                    if (eq > 0) {
                        val value = opt.substring(eq + 1).trim()
                        when (opt.substring(0, eq).trim().lowercase()) {
                            "http-user-agent" -> userAgent = value
                            "http-referrer", "http-referer" -> referrer = value
                        }
                    }
                }
                line.startsWith("#") -> Unit
                else -> {
                    // Kodi-style headers: url|User-Agent=x&Referer=y
                    var url = line
                    val pipe = line.indexOf('|')
                    if (pipe > 0) {
                        url = line.substring(0, pipe)
                        for (part in line.substring(pipe + 1).split('&')) {
                            val eq = part.indexOf('=')
                            if (eq <= 0) continue
                            val value = part.substring(eq + 1)
                            when (part.substring(0, eq).lowercase()) {
                                "user-agent" -> userAgent = value
                                "referer", "referrer" -> referrer = value
                            }
                        }
                    }
                    val number = channels.size + 1
                    val rawName = name ?: "Channel $number"
                    val quality = qualityRegex.find(rawName)?.groupValues?.get(1)
                    val cleanName = rawName.replace(qualityRegex, "").replace(Regex("\\s{2,}"), " ").trim()
                    channels.add(
                        Channel(
                            number = number,
                            name = cleanName.ifEmpty { rawName },
                            quality = quality,
                            url = url,
                            logo = logo,
                            group = group?.trim().orEmpty(),
                            userAgent = userAgent,
                            referrer = referrer,
                        )
                    )
                    name = null; logo = null; group = null; userAgent = null; referrer = null
                }
            }
        }
        return PlaylistData(channels)
    }

    /** Index of the comma separating attributes from the title, ignoring commas inside quotes. */
    private fun titleComma(line: String): Int {
        var inQuotes = false
        for (i in line.indices) {
            when (line[i]) {
                '"' -> inQuotes = !inQuotes
                ',' -> if (!inQuotes) return i
            }
        }
        return -1
    }
}
