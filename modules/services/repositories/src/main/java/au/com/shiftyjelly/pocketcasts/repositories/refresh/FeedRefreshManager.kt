package au.com.shiftyjelly.pocketcasts.repositories.refresh

import au.com.shiftyjelly.pocketcasts.models.entity.Podcast
import au.com.shiftyjelly.pocketcasts.repositories.podcast.FeedParser
import au.com.shiftyjelly.pocketcasts.servers.RefreshResponse
import au.com.shiftyjelly.pocketcasts.utils.log.LogBuffer
import javax.inject.Inject
import javax.inject.Singleton

/**
 * PodHopper client-side refresh.
 *
 * Re-parses each subscribed podcast's RSS feed on-device (no Pocket Casts server) and returns the
 * parsed episodes in the same [RefreshResponse] shape the existing refresh pipeline already
 * consumes. The pipeline's add step then inserts only episodes that are not already stored, matched
 * by the deterministic episode uuid, so existing, played, and in-progress episodes are left
 * untouched and nothing is duplicated.
 *
 * Each feed is parsed independently: a single feed that is unreachable or malformed is skipped and
 * logged, and the rest of the refresh continues.
 */
@Singleton
class FeedRefreshManager @Inject constructor(
    private val feedParser: FeedParser,
) {

    fun refreshPodcastsLocally(podcasts: List<Podcast>): RefreshResponse {
        val response = RefreshResponse()
        for (podcast in podcasts) {
            val feedUrl = podcast.podcastUrl
            if (feedUrl.isNullOrBlank()) {
                continue
            }
            val parsed = feedParser.parse(feedUrl)
            if (parsed == null) {
                LogBuffer.i(LogBuffer.TAG_BACKGROUND_TASKS, "Refresh - skipped ${podcast.uuid}, feed unavailable")
                continue
            }
            if (parsed.episodes.isNotEmpty()) {
                response.addUpdate(podcast.uuid, parsed.episodes)
            }
        }
        return response
    }
}
