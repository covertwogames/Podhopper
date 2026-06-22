package au.com.shiftyjelly.pocketcasts.repositories.podcast

import au.com.shiftyjelly.pocketcasts.models.entity.Podcast
import au.com.shiftyjelly.pocketcasts.models.entity.PodcastEpisode
import au.com.shiftyjelly.pocketcasts.models.type.EpisodePlayingStatus
import dev.stalla.PodcastRssParser
import dev.stalla.model.Episode
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.time.Instant
import java.time.temporal.TemporalAccessor
import java.util.Date
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * PodHopper's client-side RSS feed engine.
 *
 * Fetches a podcast's RSS feed directly over the network (no Pocket Casts server involved)
 * and maps it into the same [Podcast] and [PodcastEpisode] database rows the rest of the app
 * already lists and plays. Once the rows exist locally, the episode list and player just work.
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

    private val httpClient = OkHttpClient()

    /** Deterministic podcast id derived from the feed URL. */
    fun podcastUuidForFeed(feedUrl: String): String =
        UUID.nameUUIDFromBytes(("podhopper-feed:" + feedUrl.trim()).toByteArray()).toString()

    /** Deterministic episode id derived from its RSS guid. */
    private fun episodeUuidFor(guid: String): String =
        UUID.nameUUIDFromBytes(("podhopper-episode:" + guid.trim()).toByteArray()).toString()

    /**
     * Download and parse the feed at [feedUrl]. Returns null if the feed could not be fetched
     * or parsed. Runs blocking, so call it off the main thread.
     */
    fun parse(feedUrl: String): ParsedFeed? {
        val url = feedUrl.trim()
        val parsed = try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "PodHopper")
                .build()
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Timber.e("FeedParser: HTTP ${response.code} for $url")
                    return null
                }
                PodcastRssParser.parse(response.body.byteStream())
            }
        } catch (e: Exception) {
            Timber.e(e, "FeedParser: could not fetch or parse $url")
            return null
        } ?: return null

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

        episodes.maxByOrNull { it.publishedDate }?.let { latest ->
            podcast.latestEpisodeUuid = latest.uuid
            podcast.latestEpisodeDate = latest.publishedDate
        }

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
}
