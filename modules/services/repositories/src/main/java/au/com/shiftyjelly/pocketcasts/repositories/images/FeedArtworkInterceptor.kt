package au.com.shiftyjelly.pocketcasts.repositories.images

import au.com.shiftyjelly.pocketcasts.models.db.dao.PodcastDao
import coil3.intercept.Interceptor
import coil3.request.ImageResult
import javax.inject.Inject

/**
 * Resolves podcast artwork to the feed's own image when one is stored locally.
 *
 * Pocket Casts builds every podcast artwork url from the podcast id and points it at its image
 * server, which has no entry for feed podcasts, so they fall back to the placeholder. Every artwork
 * request flows through this one interceptor, so it is the single place to swap that id-based url
 * for the real feed image url that the feed parser saved on the podcast. This fixes artwork on all
 * surfaces at once: library, podcast page, player, episode screens, widgets, notifications and Auto.
 *
 * Search results for podcasts that are not subscribed yet are not affected here (they pass a direct
 * iTunes artwork url, not an id-based one); this only rewrites the id-based Pocket Casts urls.
 */
class FeedArtworkInterceptor @Inject constructor(
    private val podcastDao: PodcastDao,
) : Interceptor {
    override suspend fun intercept(chain: Interceptor.Chain): ImageResult {
        val uuid = (chain.request.data as? String)?.let(::podcastUuidFromArtworkUrl)
        if (uuid != null) {
            val feedArtwork = podcastDao.findThumbnailUrlByUuid(uuid)
            if (!feedArtwork.isNullOrBlank()) {
                val request = chain.request.newBuilder().data(feedArtwork).build()
                return chain.withRequest(request).proceed()
            }
        }
        return chain.proceed()
    }

    private fun podcastUuidFromArtworkUrl(url: String): String? {
        // Pocket Casts podcast artwork url shape: .../discover/images/webp/<size>/<uuid>.webp
        if (!url.contains("/discover/images/webp/")) {
            return null
        }
        val fileName = url.substringAfterLast('/')
        if (!fileName.endsWith(".webp")) {
            return null
        }
        return fileName.removeSuffix(".webp").takeIf { it.isNotBlank() }
    }
}
