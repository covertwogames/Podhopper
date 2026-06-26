package au.com.shiftyjelly.pocketcasts.views.helper

import android.content.Context
import android.content.Intent
import au.com.shiftyjelly.pocketcasts.models.entity.Podcast
import au.com.shiftyjelly.pocketcasts.models.entity.PodcastEpisode

/**
 * PodHopper: replaces the Pocket Casts "reimagine" share flow (styled cards, platform bar,
 * clip editor, pca.st short links) with a plain Android share sheet that hands off a generic
 * link. A podcast shares its feed URL; an episode shares its media URL plus the feed URL to
 * subscribe.
 */
object PlainShare {

    fun sharePodcast(context: Context, podcast: Podcast) {
        val feedUrl = podcast.podcastUrl?.takeIf { it.isNotBlank() }
        val text = buildString {
            append("Check out ${podcast.title}")
            if (feedUrl != null) {
                append(": $feedUrl")
            }
        }
        send(context, text)
    }

    fun shareEpisode(context: Context, podcast: Podcast, episode: PodcastEpisode) {
        val mediaUrl = episode.downloadUrl?.takeIf { it.isNotBlank() }
        val feedUrl = podcast.podcastUrl?.takeIf { it.isNotBlank() }
        val text = buildString {
            append("Listen to ${episode.title} from ${podcast.title}")
            if (mediaUrl != null) {
                append(": $mediaUrl")
            }
            if (feedUrl != null) {
                append("\n\nSubscribe to their show at: $feedUrl")
            }
        }
        send(context, text)
    }

    private fun send(context: Context, text: String) {
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        val chooser = Intent.createChooser(sendIntent, null).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    }
}
