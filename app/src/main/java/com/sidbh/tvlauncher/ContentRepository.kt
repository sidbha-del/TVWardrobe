package com.sidbh.tvlauncher

import android.util.Xml
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.time.OffsetDateTime

/**
 * Builds the Discover row from public YouTube channel feeds (no key, no login): trailers from the
 * streaming apps installed on the TV, interleaved with sports highlights.
 */
class ContentRepository {
    private class Channel(val id: String, val source: String, val kind: CardKind, val appPackage: String? = null)

    suspend fun load(isInstalled: (String) -> Boolean): List<Card> {
        // A channel tied to an app is only worth showing if that app is on the TV.
        val channels = CHANNELS.filter { it.appPackage == null || isInstalled(it.appPackage) }
        val cards = coroutineScope {
            channels.map { channel -> async(Dispatchers.IO) { fetch(channel) } }.awaitAll()
        }.flatten().distinctBy { it.id }

        val trailers = cards.filter { it.kind == CardKind.Trailer }.sortedByDescending { it.publishedMs }
        val sports = cards.filter { it.kind == CardKind.Sport }.sortedByDescending { it.publishedMs }
        return buildList {
            var i = 0
            while (size < MAX_CARDS && (i < trailers.size || i < sports.size)) {
                trailers.getOrNull(i)?.let { add(it) }
                if (size < MAX_CARDS) sports.getOrNull(i)?.let { add(it) }
                i++
            }
        }
    }

    /** One bad channel just drops out of the row. */
    private fun fetch(channel: Channel): List<Card> = try {
        val conn = URL("https://www.youtube.com/feeds/videos.xml?channel_id=${channel.id}").openConnection() as HttpURLConnection
        conn.connectTimeout = 8_000
        conn.readTimeout = 8_000
        try {
            if (conn.responseCode == 200) conn.inputStream.use { parse(it, channel) } else emptyList()
        } finally {
            conn.disconnect()
        }
    } catch (_: IOException) {
        emptyList()
    } catch (_: XmlPullParserException) {
        emptyList()
    }

    private fun parse(stream: InputStream, channel: Channel): List<Card> {
        val parser = Xml.newPullParser()
        parser.setInput(stream, null)
        val cards = mutableListOf<Card>()
        var inEntry = false
        var videoId: String? = null
        var title: String? = null
        var published = 0L
        var link: String? = null

        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> {
                    // The parser is not namespace-aware, so "yt:videoId" arrives with its prefix.
                    when (parser.name.substringAfter(':')) {
                        "entry" -> {
                            inEntry = true
                            videoId = null
                            title = null
                            published = 0L
                            link = null
                        }
                        "videoId" -> if (inEntry) videoId = parser.nextText()
                        "title" -> if (inEntry) title = parser.nextText()
                        "published" -> if (inEntry) published = parseTime(parser.nextText())
                        "link" -> if (inEntry && parser.getAttributeValue(null, "rel") == "alternate") {
                            link = parser.getAttributeValue(null, "href")
                        }
                    }
                }
                XmlPullParser.END_TAG -> if (parser.name == "entry") {
                    inEntry = false
                    val id = videoId
                    val name = title
                    if (id != null && !name.isNullOrBlank() && link?.contains("/shorts/") != true) {
                        cards += Card(
                            id = id,
                            title = name.trim(),
                            source = channel.source,
                            kind = channel.kind,
                            publishedMs = published,
                            imageUrl = "https://i.ytimg.com/vi/$id/mqdefault.jpg",
                            appPackage = channel.appPackage,
                        )
                    }
                }
            }
            event = parser.next()
        }
        return cards.sortedByDescending { it.publishedMs }.take(PER_CHANNEL)
    }

    // Feeds use "+00:00" offsets, which Instant.parse rejects on older Android runtimes.
    private fun parseTime(text: String): Long =
        runCatching { OffsetDateTime.parse(text.trim()).toInstant().toEpochMilli() }.getOrDefault(0L)

    private companion object {
        const val PER_CHANNEL = 4
        const val MAX_CARDS = 24

        private const val NETFLIX = "com.netflix.ninja"
        private const val PRIME = "com.amazon.amazonvideo.livingroom"
        private const val HOTSTAR = "in.startv.hotstar"
        private const val SONYLIV = "com.sonyliv"
        private const val FANCODE = "com.fancode.tv"

        val CHANNELS = listOf(
            Channel("UCWOA1ZGywLbqmigxE4Qlvuw", "Netflix", CardKind.Trailer, NETFLIX),
            Channel("UC4zWG9LccdWGUlF77LZ8toA", "Prime Video", CardKind.Trailer, PRIME),
            Channel("UCQJWtTnAHhEG5w4uN0udnUQ", "Prime Video", CardKind.Trailer, PRIME),
            Channel("UC0PTktRYpZXb6On0_zFKWIg", "JioHotstar", CardKind.Trailer, HOTSTAR),
            Channel("UCOQNJjhXwvAScuELTT_i7cQ", "Sony LIV", CardKind.Trailer, SONYLIV),
            Channel("UC2iihojySb58j_Q4zL7VI5w", "Star Sports", CardKind.Sport),
            Channel("UCt2JXOLNxqry7B_4rRZME3Q", "ICC", CardKind.Sport),
            Channel("UC_WKb6N9iTGc77hxwXLDrbA", "Sony Sports", CardKind.Sport),
            Channel("UCiWLfSweyRNmLpgEHekhoAg", "ESPN", CardKind.Sport),
            Channel("UCG5qGWdu8nIRZqJ_GgDwQ-w", "Premier League", CardKind.Sport),
            Channel("UCF10AG_t1AYW3mlmX7g1VJA", "FanCode", CardKind.Sport, FANCODE),
        )
    }
}
