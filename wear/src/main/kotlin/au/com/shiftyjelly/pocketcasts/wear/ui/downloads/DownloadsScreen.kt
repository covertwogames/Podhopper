package au.com.shiftyjelly.pocketcasts.wear.ui.downloads

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import au.com.shiftyjelly.pocketcasts.models.entity.PodcastEpisode
import au.com.shiftyjelly.pocketcasts.preferences.model.ArtworkConfiguration.Element
import au.com.shiftyjelly.pocketcasts.wear.ui.component.EpisodeListScreen
import com.google.android.horologist.compose.layout.ScalingLazyColumnState
import au.com.shiftyjelly.pocketcasts.localization.R as LR

object DownloadsScreen {
    const val ROUTE = "downloads_screen"
}

@Composable
fun DownloadsScreen(
    columnState: ScalingLazyColumnState,
    viewModel: DownloadsScreenViewModel = hiltViewModel(),
    onItemClick: (PodcastEpisode) -> Unit,
) {
    val uiState by viewModel.stateFlow.collectAsState()
    val artworkConfiguration by viewModel.artworkConfiguration.collectAsState()

    EpisodeListScreen(
        columnState = columnState,
        uiState = uiState,
        title = LR.string.downloads,
        useEpisodeArtwork = artworkConfiguration.useEpisodeArtwork(Element.Downloads),
        onItemClick = onItemClick,
    )
}
