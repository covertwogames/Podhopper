package au.com.shiftyjelly.pocketcasts.repositories.refresh

import au.com.shiftyjelly.pocketcasts.models.entity.Podcast
import au.com.shiftyjelly.pocketcasts.repositories.podcast.FeedParser
import au.com.shiftyjelly.pocketcasts.servers.RefreshResponse
import au.com.shiftyjelly.pocketcasts.utils.log.LogBuffer
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

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
        val semaphore = Semaphore(MAX_CONCURRENT_FEED_REFRESHES)
        // Parse feeds in parallel with a bounded number running at once, so the network waits overlap
        // instead of stacking up one podcast at a time. Each result is collected first, then added to
        // the shared response sequentially below to keep that step single-threaded.
        val updates = runBlocking {
            podcasts.map { podcast ->
                async(Dispatchers.IO) {
                    semaphore.withPermit {
                        val feedUrl = podcast.podcastUrl
                        if (feedUrl.isNullOrBlank()) {
                            return@withPermit null
                        }
                        val parsed = feedParser.parse(feedUrl)
                        if (parsed == null) {
                            LogBuffer.i(LogBuffer.TAG_BACKGROUND_TASKS, "Refresh - skipped ${podcast.uuid}, feed unavailable")
                            return@withPermit null
                        }
                        if (parsed.episodes.isEmpty()) {
                            null
                        } else {
                            podcast.uuid to parsed.episodes
                        }
                    }
                }
            }.awaitAll()
        }
        for (update in updates) {
            if (update != null) {
                response.addUpdate(update.first, update.second)
            }
        }
        return response
    }

    companion object {
        private const val MAX_CONCURRENT_FEED_REFRESHES = 6
    }
}
