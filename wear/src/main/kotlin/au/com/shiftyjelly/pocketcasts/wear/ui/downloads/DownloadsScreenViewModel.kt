package au.com.shiftyjelly.pocketcasts.wear.ui.downloads

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import au.com.shiftyjelly.pocketcasts.preferences.Settings
import au.com.shiftyjelly.pocketcasts.repositories.podcast.EpisodeManager
import au.com.shiftyjelly.pocketcasts.wear.ui.component.EpisodeListUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class DownloadsScreenViewModel @Inject constructor(
    episodeManager: EpisodeManager,
    settings: Settings,
) : ViewModel() {

    val stateFlow: StateFlow<EpisodeListUiState> = episodeManager.findDownloadEpisodesFlow().map { episodes ->
        if (episodes.isEmpty()) {
            EpisodeListUiState.Empty
        } else {
            EpisodeListUiState.Loaded(episodes)
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(1000),
        initialValue = EpisodeListUiState.Loading,
    )

    val artworkConfiguration = settings.artworkConfiguration.flow
}
