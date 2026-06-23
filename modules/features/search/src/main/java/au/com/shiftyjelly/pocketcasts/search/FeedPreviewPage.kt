package au.com.shiftyjelly.pocketcasts.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import au.com.shiftyjelly.pocketcasts.compose.bars.ThemedTopAppBar
import au.com.shiftyjelly.pocketcasts.compose.buttons.RowButton
import au.com.shiftyjelly.pocketcasts.compose.components.PodcastImage
import au.com.shiftyjelly.pocketcasts.compose.components.TextC70
import au.com.shiftyjelly.pocketcasts.compose.components.TextH30
import au.com.shiftyjelly.pocketcasts.compose.components.TextP50

@Composable
fun FeedPreviewPage(
    state: FeedPreviewViewModel.UiState,
    onSubscribe: () -> Unit,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    bottomInset: Dp,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        val toolbarTitle = (state as? FeedPreviewViewModel.UiState.Loaded)?.title
        ThemedTopAppBar(
            title = toolbarTitle,
            onNavigationClick = onBack,
        )
        when (state) {
            is FeedPreviewViewModel.UiState.Loading -> CenteredProgress()
            is FeedPreviewViewModel.UiState.Error -> ErrorState(onRetry = onRetry)
            is FeedPreviewViewModel.UiState.Loaded -> LoadedState(
                state = state,
                onSubscribe = onSubscribe,
                bottomInset = bottomInset,
            )
        }
    }
}

@Composable
private fun CenteredProgress() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun ErrorState(onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        TextP50(
            text = "This feed could not be loaded.",
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(16.dp))
        RowButton(
            text = "Retry",
            onClick = onRetry,
            includePadding = false,
        )
    }
}

@Composable
private fun LoadedState(
    state: FeedPreviewViewModel.UiState.Loaded,
    onSubscribe: () -> Unit,
    bottomInset: Dp,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = bottomInset + 16.dp),
    ) {
        item {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                PodcastImage(
                    uuid = "",
                    imageUrl = state.imageUrl,
                    imageSize = 140.dp,
                )
                Spacer(modifier = Modifier.height(12.dp))
                TextH30(
                    text = state.title,
                    textAlign = TextAlign.Center,
                    maxLines = 3,
                )
                if (state.author.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    TextC70(
                        text = state.author,
                        maxLines = 1,
                        isUpperCase = false,
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                RowButton(
                    text = subscribeButtonText(state),
                    onClick = onSubscribe,
                    enabled = !state.subscribing,
                    includePadding = false,
                )
            }
        }
        if (state.description.isNotBlank()) {
            item {
                Spacer(modifier = Modifier.height(16.dp))
                TextP50(text = state.description)
            }
        }
        if (state.episodes.isNotEmpty()) {
            item {
                Spacer(modifier = Modifier.height(24.dp))
                TextH30(text = "Episodes")
                Spacer(modifier = Modifier.height(8.dp))
            }
            items(items = state.episodes) { episode ->
                Column(modifier = Modifier.padding(vertical = 8.dp)) {
                    TextP50(text = episode.title, maxLines = 2)
                    Spacer(modifier = Modifier.height(2.dp))
                    TextC70(text = episode.date, isUpperCase = false)
                }
            }
        }
    }
}

private fun subscribeButtonText(state: FeedPreviewViewModel.UiState.Loaded): String = when {
    state.subscribing -> "Subscribing..."
    state.isSubscribed -> "Go to podcast"
    else -> "Subscribe"
}
