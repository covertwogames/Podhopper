package au.com.shiftyjelly.pocketcasts.repositories.search

import au.com.shiftyjelly.pocketcasts.models.to.ImprovedSearchResultItem
import au.com.shiftyjelly.pocketcasts.models.to.SearchAutoCompleteItem
import au.com.shiftyjelly.pocketcasts.repositories.podcast.FeedParser
import javax.inject.Inject

class ImprovedSearchManagerImpl @Inject constructor(
    private val itunesFeedSearcher: ItunesFeedSearcher,
    private val feedParser: FeedParser,
) : ImprovedSearchManager {
    override suspend fun autoCompleteSearch(term: String): List<SearchAutoCompleteItem> {
        // PodHopper: no Pocket Casts autocomplete server. As-you-type suggestions are not fetched
        // over the network; the full iTunes search runs when the query is submitted (combinedSearch).
        // Locally subscribed podcasts still appear as suggestions through the search handler.
        return emptyList()
    }

    override suspend fun combinedSearch(term: String): List<ImprovedSearchResultItem> {
        // PodHopper: results come from the iTunes directory as real RSS feed URLs, not the
        // Pocket Casts search server. The deterministic uuid here matches the id the feed engine
        // assigns on subscribe, so the already followed tick lines up correctly.
        return itunesFeedSearcher.search(term).map { result ->
            ImprovedSearchResultItem.PodcastItem(
                uuid = feedParser.podcastUuidForFeed(result.feedUrl),
                title = result.title,
                author = result.author,
                isFollowed = false,
                feedUrl = result.feedUrl,
                imageUrl = result.imageUrl,
            )
        }
    }
}
