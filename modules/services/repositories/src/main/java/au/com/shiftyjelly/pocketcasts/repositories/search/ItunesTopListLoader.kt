package au.com.shiftyjelly.pocketcasts.repositories.search

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import timber.log.Timber

/**
 * Loads the public iTunes "top podcasts" chart for the Discover page, and resolves a chart entry
 * to its real RSS feed url.
 *
 * This is a direct reconstruction of AntennaPod's ItunesTopListLoader, trimmed to iTunes only.
 * The chart feed (Apple's marketing RSS) does not hand back the podcast's RSS url. Each entry only
 * carries an iTunes numeric id, so the real feed url is resolved with a second "lookup" call when
 * the user acts on a tile. No API key is required for either call.
 *
 * A fixed buffer of [FETCH_LIMIT] entries is always fetched, then any entry on the local blocklist
 * is removed, the remainder is shuffled, and only the requested number is returned. The buffer is
 * what lets a removed entry be backfilled so the grid stays full, and the shuffle is what keeps the
 * result from reading as a straight top-down ranking.
 */
@Singleton
class ItunesTopListLoader @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    data class TopPodcast(
        val title: String,
        val author: String,
        val imageUrl: String?,
        /** Apple's numeric show id. Stable across renames, so it is what the blocklist matches on. */
        val itunesId: String,
        /** iTunes lookup url for this entry. Resolve it with [resolveFeedUrl] to get the RSS url. */
        val lookupUrl: String,
    )

    private val httpClient = OkHttpClient()

    // Normalized-title SHA-256 hashes of podcasts to keep out of suggestions. Loaded once from an
    // optional asset; if the asset is not bundled, nothing is filtered.
    private val blockedHashes: Set<String> by lazy { loadBlockedHashes() }

    /**
     * Loads up to [limit] top podcasts for [country] (a two letter ISO country code), with blocked
     * entries removed and the rest shuffled. Falls back to the US chart if the requested country's
     * chart cannot be fetched. Returns an empty list on failure rather than throwing.
     */
    suspend fun loadTopList(country: String, limit: Int): List<TopPodcast> = withContext(Dispatchers.IO) {
        val fetched = fetchWithFallback(country, FETCH_LIMIT)
        val visible = fetched.filterNot { isBlocked(it) }
        visible.shuffled().take(limit)
    }

    private fun fetchWithFallback(country: String, limit: Int): List<TopPodcast> {
        val primary = fetchChart(country, limit)
        if (primary != null) {
            return primary
        }
        if (!country.equals("US", ignoreCase = true)) {
            val fallback = fetchChart("US", limit)
            if (fallback != null) {
                return fallback
            }
        }
        return emptyList()
    }

    private fun fetchChart(country: String, limit: Int): List<TopPodcast>? {
        return try {
            val url = "https://itunes.apple.com/$country/rss/toppodcasts/limit=$limit/explicit=true/json"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "PodHopper")
                .build()
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Timber.e("ItunesTopListLoader: HTTP ${response.code} for $url")
                    return null
                }
                parseChart(response.body.string())
            }
        } catch (e: Exception) {
            Timber.e(e, "ItunesTopListLoader: could not load chart for $country")
            null
        }
    }

    private fun parseChart(jsonString: String): List<TopPodcast> {
        val feed = JSONObject(jsonString).optJSONObject("feed") ?: return emptyList()
        val entries = feed.optJSONArray("entry") ?: return emptyList()
        val results = mutableListOf<TopPodcast>()
        for (i in 0 until entries.length()) {
            val entry = entries.optJSONObject(i) ?: continue
            val title = entry.optJSONObject("im:name")?.optString("label").orEmpty()
            if (title.isBlank()) {
                continue
            }
            val itunesId = entry.optJSONObject("id")?.optJSONObject("attributes")?.optString("im:id").orEmpty()
            if (itunesId.isBlank()) {
                continue
            }
            results.add(
                TopPodcast(
                    title = title,
                    author = entry.optJSONObject("im:artist")?.optString("label").orEmpty(),
                    imageUrl = largestImage(entry),
                    itunesId = itunesId,
                    lookupUrl = "https://itunes.apple.com/lookup?id=$itunesId",
                ),
            )
        }
        return results
    }

    private fun largestImage(entry: JSONObject): String? {
        val images = entry.optJSONArray("im:image") ?: return null
        var chosen: String? = null
        for (i in 0 until images.length()) {
            val image = images.optJSONObject(i) ?: continue
            val height = image.optJSONObject("attributes")?.optString("height")?.toIntOrNull() ?: 0
            if (height >= 100) {
                return image.optString("label").ifBlank { null }
            }
            chosen = image.optString("label").ifBlank { chosen }
        }
        return chosen
    }

    /**
     * Resolves an iTunes [lookupUrl] (from [TopPodcast.lookupUrl]) to the podcast's real RSS feed
     * url. Returns null if the lookup fails or the entry has no feed url.
     */
    suspend fun resolveFeedUrl(lookupUrl: String): String? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(lookupUrl)
                .header("User-Agent", "PodHopper")
                .build()
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Timber.e("ItunesTopListLoader: HTTP ${response.code} for $lookupUrl")
                    return@use null
                }
                val results = JSONObject(response.body.string()).optJSONArray("results")
                val first = results?.optJSONObject(0) ?: return@use null
                first.optString("feedUrl").ifBlank { null }
            }
        } catch (e: Exception) {
            Timber.e(e, "ItunesTopListLoader: lookup failed for $lookupUrl")
            null
        }
    }

    private fun loadBlockedHashes(): Set<String> {
        return try {
            context.assets.open(BLOCKLIST_ASSET).bufferedReader().useLines { lines ->
                lines.map { it.trim() }
                    .filter { it.isNotEmpty() && !it.startsWith("#") }
                    .map { it.lowercase() }
                    .toSet()
            }
        } catch (e: Exception) {
            // No blocklist asset bundled, so suggestions are shown unfiltered.
            emptySet()
        }
    }

    private fun isBlocked(podcast: TopPodcast): Boolean {
        val idHash = sha256Hex(podcast.itunesId.trim())
        val titleHash = sha256Hex(podcast.title.lowercase().replace(Regex("[^a-z0-9]"), ""))
        return blockedHashes.contains(idHash) || blockedHashes.contains(titleHash)
    }

    private fun sha256Hex(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { byte -> "%02x".format(byte.toInt() and 0xFF) }
    }

    companion object {
        private const val FETCH_LIMIT = 60
        private const val BLOCKLIST_ASSET = "discover_blocklist.txt"
    }
}
