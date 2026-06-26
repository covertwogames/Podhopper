package au.com.shiftyjelly.pocketcasts.repositories.extensions

import au.com.shiftyjelly.pocketcasts.models.entity.Podcast
import au.com.shiftyjelly.pocketcasts.repositories.images.PodcastImage

fun Podcast.getArtworkUrl(size: Int): String {
    // PodHopper: feed podcasts carry their own artwork url from the RSS feed on thumbnail_url. Use it
    // directly instead of the Pocket Casts image server, which has no entry for off-catalog feeds and
    // returns 404. This mirrors PocketCastsImageRequestFactory.create(podcast) on the phone and the
    // AntennaPod fork, both of which use the real feed image url for now-playing and browse artwork.
    val feedArtwork = thumbnailUrl
    return when {
        uuid == Podcast.userPodcast.uuid -> feedArtwork ?: ""
        !feedArtwork.isNullOrBlank() -> feedArtwork
        else -> PodcastImage.getArtworkUrl(size = size, uuid = uuid, isWearOS = false)
    }
}
