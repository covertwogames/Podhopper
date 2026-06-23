package au.com.shiftyjelly.pocketcasts.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import au.com.shiftyjelly.pocketcasts.compose.bars.ThemedTopAppBar
import au.com.shiftyjelly.pocketcasts.compose.buttons.RowButton
import au.com.shiftyjelly.pocketcasts.compose.components.PodcastImage
import au.com.shiftyjelly.pocketcasts.compose.components.TextC70
import au.com.shiftyjelly.pocketcasts.compose.components.TextH10
import au.com.shiftyjelly.pocketcasts.compose.components.TextH40
import au.com.shiftyjelly.pocketcasts.compose.components.TextP50
import au.com.shiftyjelly.pocketcasts.compose.theme
import au.com.shiftyjelly.pocketcasts.repositories.search.ItunesTopListLoader
import au.com.shiftyjelly.pocketcasts.images.R as IR

enum class AddPodcastMode { LANDING, FULL_GRID }

private const val GRID_COLUMNS = 4

@Composable
fun AddPodcastPage(
    mode: AddPodcastMode,
    state: AddPodcastViewModel.UiState,
    onSearchBarClick: () -> Unit,
    onTileClick: (ItunesTopListLoader.TopPodcast) -> Unit,
    onDiscoverMoreClick: () -> Unit,
    onAddByUrlClick: () -> Unit,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    bottomInset: Dp,
) {
    when (mode) {
        AddPodcastMode.LANDING -> LandingContent(
            state = state,
            onSearchBarClick = onSearchBarClick,
            onTileClick = onTileClick,
            onDiscoverMoreClick = onDiscoverMoreClick,
            onAddByUrlClick = onAddByUrlClick,
            onRetry = onRetry,
            bottomInset = bottomInset,
        )

        AddPodcastMode.FULL_GRID -> FullGridContent(
            state = state,
            onTileClick = onTileClick,
            onRetry = onRetry,
            onBack = onBack,
            bottomInset = bottomInset,
        )
    }
}

@Composable
private fun LandingContent(
    state: AddPodcastViewModel.UiState,
    onSearchBarClick: () -> Unit,
    onTileClick: (ItunesTopListLoader.TopPodcast) -> Unit,
    onDiscoverMoreClick: () -> Unit,
    onAddByUrlClick: () -> Unit,
    onRetry: () -> Unit,
    bottomInset: Dp,
) {
    val gap = 8.dp
    val tileSize = computeTileSize(gap)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.statusBars)
            .verticalScroll(rememberScrollState())
            .padding(bottom = bottomInset + 16.dp),
    ) {
        Spacer(modifier = Modifier.height(8.dp))
        TextH10(
            text = "Add podcast",
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        SearchBar(onClick = onSearchBarClick)
        Spacer(modifier = Modifier.height(16.dp))
        when (state) {
            is AddPodcastViewModel.UiState.Loading -> GridLoading()
            is AddPodcastViewModel.UiState.Error -> GridError(onRetry = onRetry)
            is AddPodcastViewModel.UiState.Loaded -> SuggestionsGrid(
                podcasts = state.podcasts,
                tileSize = tileSize,
                gap = gap,
                onTileClick = onTileClick,
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        DiscoverMoreRow(onClick = onDiscoverMoreClick)
        Spacer(modifier = Modifier.height(4.dp))
        AddByUrlRow(onClick = onAddByUrlClick)
    }
}

@Composable
private fun FullGridContent(
    state: AddPodcastViewModel.UiState,
    onTileClick: (ItunesTopListLoader.TopPodcast) -> Unit,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    bottomInset: Dp,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        ThemedTopAppBar(
            title = "Discover",
            onNavigationClick = onBack,
        )
        when (state) {
            is AddPodcastViewModel.UiState.Loading -> GridLoading()
            is AddPodcastViewModel.UiState.Error -> GridError(onRetry = onRetry)
            is AddPodcastViewModel.UiState.Loaded -> {
                val gap = 8.dp
                val tileSize = computeTileSize(gap)
                LazyVerticalGrid(
                    columns = GridCells.Fixed(GRID_COLUMNS),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = bottomInset + 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(gap),
                    verticalArrangement = Arrangement.spacedBy(gap),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(items = state.podcasts) { item ->
                        FeedTile(item = item, size = tileSize, onClick = { onTileClick(item) })
                    }
                }
            }
        }
    }
}

@Composable
private fun computeTileSize(gap: Dp): Dp {
    val screenWidth = LocalConfiguration.current.screenWidthDp.dp
    return (screenWidth - 16.dp * 2 - gap * (GRID_COLUMNS - 1)) / GRID_COLUMNS
}

@Composable
private fun SearchBar(onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.theme.colors.primaryUi02,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clickable { onClick() },
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
        ) {
            Icon(
                painter = painterResource(IR.drawable.ic_search),
                contentDescription = null,
                tint = MaterialTheme.theme.colors.primaryText02,
            )
            Spacer(modifier = Modifier.width(12.dp))
            TextP50(
                text = "Search podcast...",
                color = MaterialTheme.theme.colors.primaryText02,
            )
        }
    }
}

@Composable
private fun SuggestionsGrid(
    podcasts: List<ItunesTopListLoader.TopPodcast>,
    tileSize: Dp,
    gap: Dp,
    onTileClick: (ItunesTopListLoader.TopPodcast) -> Unit,
) {
    Column(
        modifier = Modifier.padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(gap),
    ) {
        podcasts.chunked(GRID_COLUMNS).forEach { rowItems ->
            Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                rowItems.forEach { item ->
                    FeedTile(item = item, size = tileSize, onClick = { onTileClick(item) })
                }
            }
        }
    }
}

@Composable
private fun FeedTile(
    item: ItunesTopListLoader.TopPodcast,
    size: Dp,
    onClick: () -> Unit,
) {
    PodcastImage(
        uuid = "",
        imageUrl = item.imageUrl,
        imageSize = size,
        modifier = Modifier.clickable { onClick() },
    )
}

@Composable
private fun DiscoverMoreRow(onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        TextC70(
            text = "Popular podcasts",
            isUpperCase = false,
            modifier = Modifier.weight(1f),
        )
        TextH40(
            text = "Discover more",
            color = MaterialTheme.theme.colors.primaryInteractive01,
            modifier = Modifier
                .clickable { onClick() }
                .padding(8.dp),
        )
    }
}

@Composable
private fun AddByUrlRow(onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 16.dp),
    ) {
        Icon(
            painter = painterResource(IR.drawable.ic_search),
            contentDescription = null,
            tint = MaterialTheme.theme.colors.primaryText01,
        )
        Spacer(modifier = Modifier.width(16.dp))
        TextH40(text = "Add podcast by RSS address")
    }
}

@Composable
private fun GridLoading() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun GridError(onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        TextP50(
            text = "Podcasts could not be loaded.",
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
