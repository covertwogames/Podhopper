package au.com.shiftyjelly.pocketcasts.repositories.podcast

import android.util.Xml
import au.com.shiftyjelly.pocketcasts.models.entity.Podcast
import au.com.shiftyjelly.pocketcasts.models.entity.PodcastEpisode
import au.com.shiftyjelly.pocketcasts.models.type.EpisodePlayingStatus
import dev.stalla.PodcastRssParser
import dev.stalla.model.Episode
import okhttp3.OkHttpClient
import okhttp3.Request
import org.xmlpull.v1.XmlPullParser
import timber.log.Timber
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.text.ParseException
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.temporal.TemporalAccessor
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * PodHopper's client-side RSS feed engine.
 *
 * Fetches a podcast's RSS feed directly over the network (no Pocket Casts server involved)
 * and maps it into the same [Podcast] and [PodcastEpisode] database rows the rest of the app
 * already lists and plays. Once the rows exist locally, the episode list and player just work.
 *
 * Parsing happens in two passes. The strict library parser (Stalla) runs first because it is
 * well tested for spec correct feeds. When it rejects a feed (some real world hosts omit
 * elements the RSS spec marks as required), a lenient pull parser takes over and extracts
 * whatever is present, which mirrors how AntennaPod and the podcast directories read those
 * same feeds.
 *
 * Ids are derived deterministically: the podcast id comes from the feed URL, and each episode
 * id comes from its RSS guid. That means the same feed always produces the same podcast id and
 * the same episode always produces the same episode id, which keeps the local database stable
 * across refreshes and lines up with Supabase sync later.
 */
@Singleton
class FeedParser @Inject constructor() {

    data class ParsedFeed(
        val podcast: Podcast,
        val episodes: List<PodcastEpisode>,
    )

    /**
     * Outcome of a feed fetch. [Failure.reason] carries a short human readable cause (an HTTP
     * status like "HTTP 403", or an exception summary) so the caller can show what went wrong
     * instead of a generic message.
     */
    sealed interface FeedResult {
        data class Success(val feed: ParsedFeed) : FeedResult
        data class Failure(val reason: String) : FeedResult
    }

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .callTimeout(15, TimeUnit.SECONDS)
        .build()

    /** Deterministic podcast id derived from the feed URL. */
    fun podcastUuidForFeed(feedUrl: String): String =
        UUID.nameUUIDFromBytes(("podhopper-feed:" + feedUrl.trim()).toByteArray()).toString()

    /** Deterministic episode id derived from its RSS guid. */
    private fun episodeUuidFor(guid: String): String =
        UUID.nameUUIDFromBytes(("podhopper-episode:" + guid.trim()).toByteArray()).toString()

    /**
     * Download and parse the feed at [feedUrl], returning null on any failure. Kept for callers
     * that only care whether parsing succeeded. Runs blocking, so call it off the main thread.
     */
    fun parse(feedUrl: String): ParsedFeed? = (fetch(feedUrl) as? FeedResult.Success)?.feed

    /**
     * Download and parse the feed at [feedUrl], returning a [FeedResult] that names the cause on
     * failure. Runs blocking, so call it off the main thread.
     */
    fun fetch(feedUrl: String): FeedResult {
        val url = feedUrl.trim()
        val bytes = try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .header("Accept", ACCEPT)
                .build()
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Timber.e("FeedParser: HTTP ${response.code} for $url")
                    return FeedResult.Failure("HTTP ${response.code}")
                }
                response.body.bytes()
            }
        } catch (e: Exception) {
            Timber.e(e, "FeedParser: could not fetch $url")
            return FeedResult.Failure("${e.javaClass.simpleName}: ${e.message ?: "no detail"}")
        }

        // Pass one: strict library parser for spec correct feeds.
        val strict = try {
            PodcastRssParser.parse(ByteArrayInputStream(bytes))
        } catch (e: Exception) {
            Timber.w(e, "FeedParser: strict parse failed, trying lenient for $url")
            null
        }
        if (strict != null) {
            return FeedResult.Success(buildFromStrict(strict, url))
        }

        // Pass two: lenient parser for feeds the strict parser turned away.
        val lenient = try {
            parseLeniently(ByteArrayInputStream(bytes), url)
        } catch (e: Exception) {
            Timber.e(e, "FeedParser: lenient parse failed for $url")
            null
        }
        if (lenient != null) {
            return FeedResult.Success(lenient)
        }

        return FeedResult.Failure("Feed format not recognized")
    }

    private fun buildFromStrict(parsed: dev.stalla.model.Podcast, url: String): ParsedFeed {
        val podcastUuid = podcastUuidForFeed(url)
        val artwork = parsed.itunes?.image?.href ?: parsed.image?.url

        val podcast = Podcast(
            uuid = podcastUuid,
            title = parsed.title,
            podcastUrl = url,
            podcastDescription = parsed.description,
            author = parsed.itunes?.author ?: "",
            thumbnailUrl = artwork,
            addedDate = Date(),
            isSubscribed = true,
        )

        val episodes = parsed.episodes.mapNotNull { item -> mapEpisode(item, podcastUuid) }
        applyLatestEpisode(podcast, episodes)
        return ParsedFeed(podcast = podcast, episodes = episodes)
    }

    private fun mapEpisode(item: Episode, podcastUuid: String): PodcastEpisode? {
        val audioUrl = item.enclosure.url
        if (audioUrl.isBlank()) return null

        val guid = item.guid?.guid?.takeIf { it.isNotBlank() } ?: audioUrl
        val published = item.pubDate?.let { toDate(it) } ?: Date()
        val durationSecs = item.itunes?.duration?.rawDuration?.seconds?.toDouble() ?: 0.0

        return PodcastEpisode(
            uuid = episodeUuidFor(guid),
            publishedDate = published,
            podcastUuid = podcastUuid,
            title = item.title,
            episodeDescription = item.description ?: "",
            downloadUrl = audioUrl,
            sizeInBytes = item.enclosure.length,
            fileType = item.enclosure.type.essence,
            duration = durationSecs,
            playingStatus = EpisodePlayingStatus.NOT_PLAYED,
        )
    }

    private fun toDate(temporal: TemporalAccessor): Date? = try {
        Date.from(Instant.from(temporal))
    } catch (e: Exception) {
        null
    }

    /**
     * Streams the feed and pulls out whatever recognizable fields are present, treating missing
     * elements as simply absent rather than as a reason to reject the feed. Returns null only if
     * nothing usable (no title and no playable episodes) could be found.
     */
    private fun parseLeniently(input: InputStream, url: String): ParsedFeed? {
        val podcastUuid = podcastUuidForFeed(url)
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(input, null)

        var channelTitle = ""
        var channelDescription = ""
        var channelAuthor = ""
        var itunesImage: String? = null
        var rssImageUrl: String? = null

        val episodes = mutableListOf<PodcastEpisode>()

        var inItem = false
        var inImage = false
        var itemTitle = ""
        var itemDescription = ""
        var itemGuid = ""
        var itemPubDate = ""
        var itemDuration = ""
        var itemEnclosureUrl = ""
        var itemEnclosureLength = 0L
        var itemEnclosureType = ""

        val text = StringBuilder()

        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> {
                    val name = (parser.name ?: "").lowercase()
                    val local = name.substringAfter(':')
                    text.setLength(0)
                    when {
                        local == "item" -> {
                            inItem = true
                            itemTitle = ""
                            itemDescription = ""
                            itemGuid = ""
                            itemPubDate = ""
                            itemDuration = ""
                            itemEnclosureUrl = ""
                            itemEnclosureLength = 0L
                            itemEnclosureType = ""
                        }
                        local == "image" && !inItem -> {
                            val href = attr(parser, "href")
                            if (!href.isNullOrBlank()) {
                                if (itunesImage == null) itunesImage = href.trim()
                            } else {
                                inImage = true
                            }
                        }
                        local == "enclosure" && inItem -> {
                            itemEnclosureUrl = attr(parser, "url")?.trim().orEmpty()
                            itemEnclosureType = attr(parser, "type")?.trim().orEmpty()
                            itemEnclosureLength = attr(parser, "length")?.trim()?.toLongOrNull() ?: 0L
                        }
                    }
                }

                XmlPullParser.TEXT -> text.append(parser.text)

                XmlPullParser.END_TAG -> {
                    val name = (parser.name ?: "").lowercase()
                    val local = name.substringAfter(':')
                    val prefix = if (name.contains(':')) name.substringBefore(':') else ""
                    val value = text.toString().trim()
                    text.setLength(0)

                    if (inItem) {
                        when {
                            local == "item" -> {
                                if (itemEnclosureUrl.isNotBlank()) {
                                    val guid = itemGuid.ifBlank { itemEnclosureUrl }
                                    episodes.add(
                                        PodcastEpisode(
                                            uuid = episodeUuidFor(guid),
                                            publishedDate = parsePubDate(itemPubDate) ?: Date(),
                                            podcastUuid = podcastUuid,
                                            title = itemTitle,
                                            episodeDescription = itemDescription,
                                            downloadUrl = itemEnclosureUrl,
                                            sizeInBytes = itemEnclosureLength,
                                            fileType = itemEnclosureType,
                                            duration = parseDuration(itemDuration),
                                            playingStatus = EpisodePlayingStatus.NOT_PLAYED,
                                        ),
                                    )
                                }
                                inItem = false
                            }
                            local == "title" -> if (itemTitle.isBlank()) itemTitle = value
                            local == "description" || (prefix == "itunes" && local == "summary") ->
                                if (itemDescription.isBlank()) itemDescription = value
                            prefix == "content" && local == "encoded" ->
                                if (itemDescription.isBlank()) itemDescription = value
                            local == "guid" -> if (itemGuid.isBlank()) itemGuid = value
                            local == "pubdate" -> if (itemPubDate.isBlank()) itemPubDate = value
                            prefix == "itunes" && local == "duration" ->
                                if (itemDuration.isBlank()) itemDuration = value
                        }
                    } else {
                        when {
                            local == "image" -> inImage = false
                            inImage && local == "url" -> if (rssImageUrl == null) rssImageUrl = value
                            !inImage && local == "title" -> if (channelTitle.isBlank()) channelTitle = value
                            !inImage && (local == "description" || (prefix == "itunes" && local == "summary")) ->
                                if (channelDescription.isBlank()) channelDescription = value
                            !inImage && prefix == "itunes" && local == "author" ->
                                if (channelAuthor.isBlank()) channelAuthor = value
                        }
                    }
                }
            }
            event = parser.next()
        }

        if (channelTitle.isBlank() && episodes.isEmpty()) {
            return null
        }

        val podcast = Podcast(
            uuid = podcastUuid,
            title = channelTitle,
            podcastUrl = url,
            podcastDescription = channelDescription,
            author = channelAuthor,
            thumbnailUrl = itunesImage ?: rssImageUrl,
            addedDate = Date(),
            isSubscribed = true,
        )
        applyLatestEpisode(podcast, episodes)
        return ParsedFeed(podcast = podcast, episodes = episodes)
    }

    private fun applyLatestEpisode(podcast: Podcast, episodes: List<PodcastEpisode>) {
        episodes.maxByOrNull { it.publishedDate }?.let { latest ->
            podcast.latestEpisodeUuid = latest.uuid
            podcast.latestEpisodeDate = latest.publishedDate
        }
    }

    private fun attr(parser: XmlPullParser, name: String): String? {
        for (i in 0 until parser.attributeCount) {
            val attrName = parser.getAttributeName(i)
            if (attrName.equals(name, ignoreCase = true) ||
                attrName.substringAfter(':').equals(name, ignoreCase = true)
            ) {
                return parser.getAttributeValue(i)
            }
        }
        return null
    }

    private fun parsePubDate(value: String): Date? {
        if (value.isBlank()) return null
        for (pattern in PUB_DATE_FORMATS) {
            try {
                return SimpleDateFormat(pattern, Locale.US).parse(value)
            } catch (e: ParseException) {
                // Try the next pattern.
            }
        }
        return null
    }

    private fun parseDuration(value: String): Double {
        if (value.isBlank()) return 0.0
        val parts = value.split(":")
        return try {
            when (parts.size) {
                3 -> parts[0].toLong() * 3600 + parts[1].toLong() * 60 + parts[2].toDouble()
                2 -> parts[0].toLong() * 60 + parts[1].toDouble()
                1 -> parts[0].toDouble()
                else -> 0.0
            }
        } catch (e: NumberFormatException) {
            0.0
        }
    }

    companion object {
        // A plain, well formed product token. Some hosts (for example FlightCast) reject a bare
        // one word agent, but they accept a normal "Name/Version" token, which is also what the
        // AntennaPod client this engine is modeled on sends.
        private const val USER_AGENT = "PodHopper/1.0"
        private const val ACCEPT = "application/rss+xml, application/atom+xml, application/xml;q=0.9, text/xml;q=0.8, */*;q=0.5"

        private val PUB_DATE_FORMATS = listOf(
            "EEE, dd MMM yyyy HH:mm:ss Z",
            "EEE, dd MMM yyyy HH:mm:ss zzz",
            "EEE, dd MMM yyyy HH:mm Z",
            "EEE, dd MMM yyyy HH:mm zzz",
            "dd MMM yyyy HH:mm:ss Z",
            "dd MMM yyyy HH:mm:ss zzz",
            "yyyy-MM-dd'T'HH:mm:ssZ",
            "yyyy-MM-dd'T'HH:mm:ss'Z'",
        )
    }
}
