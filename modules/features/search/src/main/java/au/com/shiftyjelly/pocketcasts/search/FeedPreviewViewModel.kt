package au.com.shiftyjelly.pocketcasts.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import au.com.shiftyjelly.pocketcasts.repositories.podcast.FeedParser
import au.com.shiftyjelly.pocketcasts.repositories.podcast.PodcastManager
import dagger.hilt.android.lifecycle.HiltViewModel
import java.text.SimpleDateFormat
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Backs the feed preview screen. It parses a feed url into a transient podcast and episode list
 * without writing anything to the database, which is what lets a not yet subscribed podcast be
 * shown before the user commits. Pressing subscribe is the only thing that persists the feed.
 */
@HiltViewModel
class FeedPreviewViewModel @Inject constructor(
    private val feedParser: FeedParser,
    private val podcastManager: PodcastManager,
) : ViewModel() {

    sealed interface UiState {
        data object Loading : UiState
        data object Error : UiState
        data class Loaded(
            val podcastUuid: String,
            val title: String,
            val author: String,
            val description: String,
            val imageUrl: String?,
            val episodes: List<Episode>,
            val isSubscribed: Boolean,
            val subscribing: Boolean,
        ) : UiState
    }

    data class Episode(
        val uuid: String,
        val title: String,
        val date: String,
    )

    private val _uiState = MutableStateFlow<UiState>(UiState.Loading)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _openPodcast = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val openPodcast: SharedFlow<String> = _openPodcast.asSharedFlow()

    private var feedUrl: String? = null

    fun load(feedUrl: String) {
        this.feedUrl = feedUrl
        _uiState.value = UiState.Loading
        viewModelScope.launch {
            val parsed = withContext(Dispatchers.IO) { feedParser.parse(feedUrl) }
            if (parsed == null) {
                _uiState.value = UiState.Error
                return@launch
            }
            val uuid = parsed.podcast.uuid
            val alreadySubscribed = podcastManager.findPodcastByUuid(uuid)?.isSubscribed == true
            val dateFormat = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
            _uiState.value = UiState.Loaded(
                podcastUuid = uuid,
                title = parsed.podcast.title,
                author = parsed.podcast.author,
                description = parsed.podcast.podcastDescription,
                imageUrl = parsed.podcast.thumbnailUrl,
                episodes = parsed.episodes.map { episode ->
                    Episode(
                        uuid = episode.uuid,
                        title = episode.title,
                        date = dateFormat.format(episode.publishedDate),
                    )
                },
                isSubscribed = alreadySubscribed,
                subscribing = false,
            )
        }
    }

    fun retry() {
        feedUrl?.let { load(it) }
    }

    fun subscribe() {
        val url = feedUrl ?: return
        val current = _uiState.value
        if (current !is UiState.Loaded || current.subscribing || current.isSubscribed) {
            if (current is UiState.Loaded && current.isSubscribed) {
                _openPodcast.tryEmit(current.podcastUuid)
            }
            return
        }
        _uiState.value = current.copy(subscribing = true)
        viewModelScope.launch {
            podcastManager.subscribeToFeedUrl(url)
            val latest = _uiState.value
            if (latest is UiState.Loaded) {
                _uiState.value = latest.copy(subscribing = false, isSubscribed = true)
            }
            _openPodcast.tryEmit(current.podcastUuid)
        }
    }
}
