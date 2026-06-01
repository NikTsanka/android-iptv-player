package com.iptv.player.data

/**
 * Tolerant extended-M3U parser (port of the desktop app's parser). Never throws on bad
 * input: unrecognised/broken lines are skipped, and a channel is only emitted once it has
 * both a name and a stream URL.
 */
object M3UParser {

    private val attrRegex = Regex("""([A-Za-z0-9_-]+)\s*=\s*"([^"]*)"""")
    private val extInfRegex = Regex("""^#EXTINF:\s*(-?\d+(\.\d+)?)?([^,]*),(.*)$""")

    fun parse(content: String): List<Channel> {
        val result = ArrayList<Channel>()

        var name: String? = null
        var group = "Uncategorized"
        var logo: String? = null
        var tvgId: String? = null
        var extGrp: String? = null
        var pending = false

        content.lineSequence().forEach forEach@{ raw ->
            val line = raw.trim()
            if (line.isEmpty()) return@forEach

            when {
                line.startsWith("#EXTM3U", ignoreCase = true) -> { /* header */ }

                line.startsWith("#EXTINF", ignoreCase = true) -> {
                    name = null; group = "Uncategorized"; logo = null; tvgId = null; extGrp = null

                    val m = extInfRegex.find(line)
                    val attrs: String
                    if (m != null) {
                        name = m.groupValues[4].trim()
                        attrs = m.groupValues[3]
                    } else {
                        val comma = line.indexOf(',')
                        name = if (comma >= 0) line.substring(comma + 1).trim() else "Unknown"
                        attrs = if (comma >= 0) line.substring(0, comma) else line
                    }

                    for (a in attrRegex.findAll(attrs)) {
                        val key = a.groupValues[1].lowercase()
                        val v = a.groupValues[2].trim()
                        when (key) {
                            "tvg-id" -> tvgId = v
                            "tvg-logo" -> logo = v.ifBlank { null }
                            "group-title" -> if (v.isNotBlank()) group = v
                            "tvg-name" -> if (name.isNullOrBlank()) name = v
                        }
                    }
                    if (name.isNullOrBlank()) name = "Unknown"
                    pending = true
                }

                line.startsWith("#EXTGRP", ignoreCase = true) -> {
                    val idx = line.indexOf(':')
                    if (idx in 0 until line.length - 1) extGrp = line.substring(idx + 1).trim()
                }

                line.startsWith("#") -> { /* other directive/comment */ }

                else -> {
                    // A non-comment line is the URL for the pending channel.
                    if (pending) {
                        val g = if (!extGrp.isNullOrBlank()) extGrp!! else group
                        val n = name
                        if (!n.isNullOrBlank()) {
                            result.add(
                                Channel(name = n, streamUrl = line, group = g, logoUrl = logo, tvgId = tvgId)
                            )
                        }
                        pending = false
                        extGrp = null
                    }
                }
            }
        }

        return result.mapIndexed { i, c -> c.copy(number = i + 1) }
    }
}
