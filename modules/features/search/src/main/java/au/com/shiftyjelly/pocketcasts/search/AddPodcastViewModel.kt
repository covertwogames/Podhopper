package au.com.shiftyjelly.pocketcasts.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import au.com.shiftyjelly.pocketcasts.repositories.podcast.PodcastManager
import au.com.shiftyjelly.pocketcasts.repositories.search.ItunesTopListLoader
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Backs the Discover landing and its full grid. Loads the iTunes top podcasts chart for the
 * device's country and, when a tile is tapped, resolves that chart entry to its real RSS feed url,
 * adds it as a not subscribed podcast, and opens the real podcast page so it can be played without
 * following it.
 */
@HiltViewModel
class AddPodcastViewModel @Inject constructor(
    private val topListLoader: ItunesTopListLoader,
    private val podcastManager: PodcastManager,
) : ViewModel() {

    sealed interface UiState {
        data object Loading : UiState
        data object Error : UiState
        data class Loaded(val podcasts: List<ItunesTopListLoader.TopPodcast>) : UiState
    }

    private val _uiState = MutableStateFlow<UiState>(UiState.Loading)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _openPodcast = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val openPodcast: SharedFlow<String> = _openPodcast.asSharedFlow()

    private val _resolveFailed = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val resolveFailed: SharedFlow<Unit> = _resolveFailed.asSharedFlow()

    private var limit: Int = SUGGESTIONS_LIMIT

    fun load(limit: Int) {
        this.limit = limit
        _uiState.value = UiState.Loading
        viewModelScope.launch {
            val list = topListLoader.loadTopList(deviceCountry(), limit)
            _uiState.value = if (list.isEmpty()) UiState.Error else UiState.Loaded(list)
        }
    }

    fun retry() = load(limit)

    fun onTileClick(item: ItunesTopListLoader.TopPodcast) {
        viewModelScope.launch {
            val feedUrl = topListLoader.resolveFeedUrl(item.lookupUrl)
            if (feedUrl.isNullOrBlank()) {
                _resolveFailed.tryEmit(Unit)
                return@launch
            }
            openFeedAsUnsubscribed(feedUrl)
        }
    }

    fun onAddByUrl(feedUrl: String) {
        viewModelScope.launch {
            openFeedAsUnsubscribed(feedUrl)
        }
    }

    private suspend fun openFeedAsUnsubscribed(feedUrl: String) {
        val uuid = podcastManager.addFeedUrlAsUnsubscribed(feedUrl)
        if (uuid != null) {
            _openPodcast.tryEmit(uuid)
        } else {
            _resolveFailed.tryEmit(Unit)
        }
    }

    private fun deviceCountry(): String {
        val country = Locale.getDefault().country
        return country.ifBlank { "US" }
    }

    companion object {
        const val SUGGESTIONS_LIMIT = 12
        const val FULL_LIMIT = 40
    }
}
