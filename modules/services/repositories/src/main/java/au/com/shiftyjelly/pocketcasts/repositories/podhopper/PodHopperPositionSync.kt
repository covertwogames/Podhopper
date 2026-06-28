package au.com.shiftyjelly.pocketcasts.repositories.podhopper

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import au.com.shiftyjelly.pocketcasts.coroutines.di.ApplicationScope
import au.com.shiftyjelly.pocketcasts.models.entity.BaseEpisode
import au.com.shiftyjelly.pocketcasts.models.entity.PodcastEpisode
import au.com.shiftyjelly.pocketcasts.models.type.EpisodePlayingStatus
import au.com.shiftyjelly.pocketcasts.preferences.Settings
import au.com.shiftyjelly.pocketcasts.repositories.playback.PlaybackManager
import au.com.shiftyjelly.pocketcasts.repositories.podcast.EpisodeManager
import au.com.shiftyjelly.pocketcasts.repositories.podcast.PodcastManager
import au.com.shiftyjelly.pocketcasts.utils.log.LogBuffer
import dagger.Lazy
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject

/**
 * Syncs playback position and completion across devices (phone and car) through Supabase.
 *
 * This restores the cross device resume behaviour that worked in the AntennaPod build, with no
 * dependency on Pocket Casts servers. Two pulls (when the playback service starts, and when the
 * app comes to the foreground) and four pushes (a periodic sample while playing, an immediate push
 * on pause, an immediate push on service shutdown, and an explicit completion push on natural
 * finish or manual mark-as-played) keep both surfaces in step.
 *
 * Completion is an explicit fact, not a guess. A finish writes completed=true to the row; the
 * receiver runs a real local mark-as-played (removes from Up Next and auto-archives per the
 * podcast's settings), so finishing on one device removes the episode on the other.
 *
 * Protection rules, both about not destroying newer local state:
 *  - A remote change older than this device's own last change to the episode is ignored, so a
 *    stale row can never rewind your progress or undo a newer local completion.
 *  - The episode actively playing on this device right now is never overwritten.
 *
 * Reliability: the pull drains every page in one open, advances its cursor only after a page is
 * accounted for, and parks rows for episodes not yet in the local database in a small retry list
 * (so a position is never lost just because its feed had not refreshed yet). A fresh sign-in
 * starts from "now" instead of replaying the whole history.
 *
 * Install id, the last pull cursor, and the retry list live in a dedicated SharedPreferences file
 * so nothing here touches the shared app Settings.
 */
@Singleton
class PodHopperPositionSync @Inject constructor(
    private val supabaseClient: SupabaseClient,
    private val episodeManager: EpisodeManager,
    private val podcastManager: PodcastManager,
    private val playbackManager: Lazy<PlaybackManager>,
    private val settings: Settings,
    @ApplicationContext private val context: Context,
    @ApplicationScope private val applicationScope: CoroutineScope,
) {
    @Volatile
    private var lastPushAttemptMs = 0L

    // Episodes the sync is applying remotely right now. The push hooks consult this so the local
    // changes a remote apply makes (a mark-as-played in particular) are not echoed back as a push.
    private val applyingUuids: MutableSet<String> = ConcurrentHashMap.newKeySet()

    init {
        observeSignIn()
    }

    /** True while the sync is applying a remote change to this episode. Push hooks skip when true. */
    fun isApplyingRemote(uuid: String): Boolean = applyingUuids.contains(uuid)

    /**
     * Pulls the latest cross-device positions the instant sign-in completes, so resume points are
     * correct as soon as a freshly signed-in user starts playing. Watch login state, drop the
     * startup value, and react only to the transition into the signed-in state.
     */
    private fun observeSignIn() {
        supabaseClient.loginState
            .distinctUntilChanged()
            .drop(1)
            .onEach { loggedIn ->
                if (loggedIn) {
                    pullLatestPositions()
                }
            }
            .launchIn(applicationScope)
    }

    /**
     * Push a single episode position to Supabase. Throttled to one push every
     * [MIN_PUSH_INTERVAL_MS] unless [immediate] is true (pause and shutdown). The completed column
     * is deliberately omitted so a position sample can never un-complete an episode another flow
     * just marked finished (Supabase merge-duplicates leaves omitted columns untouched).
     */
    fun pushPosition(episode: BaseEpisode, positionMs: Int, durationMs: Int, immediate: Boolean) {
        if (!supabaseClient.isLoggedIn()) {
            return
        }
        if (positionMs <= 0) {
            return
        }
        val now = System.currentTimeMillis()
        if (!immediate && now - lastPushAttemptMs < MIN_PUSH_INTERVAL_MS) {
            return
        }
        lastPushAttemptMs = now

        val episodeKey = episode.uuid
        val episodeUrl = episode.downloadUrl
        val positionSec = positionMs / 1000
        val totalSec = durationMs / 1000

        applicationScope.launch(Dispatchers.IO) {
            try {
                val userId = supabaseClient.getUserId() ?: return@launch
                val feedUrl = feedUrlForEpisode(episode)
                val row = JSONObject()
                row.put("user_id", userId)
                row.put("episode_key", episodeKey)
                row.put("episode_url", episodeUrl ?: JSONObject.NULL)
                row.put("position_sec", positionSec)
                row.put("total_sec", totalSec)
                row.put("feed_url", feedUrl ?: JSONObject.NULL)
                row.put("device_id", getOrCreateInstallId())
                row.put("device_name", Build.MODEL)
                row.put("updated_at_ms", now)
                supabaseClient.upsert(TABLE_PLAYBACK_STATE, "user_id,episode_key", JSONArray().put(row))
            } catch (e: Exception) {
                LogBuffer.i(LogBuffer.TAG_PLAYBACK, "PodHopper position push failed, will retry next cycle: ${e.message}")
            }
        }
    }

    /**
     * Push the current episode's live position. Used for the shutdown push, where the caller does
     * not have the position to hand. Reads the live playback state from PlaybackManager.
     */
    fun pushCurrentPosition(immediate: Boolean) {
        if (!supabaseClient.isLoggedIn()) {
            return
        }
        val manager = playbackManager.get()
        val episode = manager.getCurrentEpisode() ?: return
        val state = manager.playbackStateRelay.blockingFirst()
        if (state.episodeUuid != episode.uuid) {
            return
        }
        pushPosition(episode, state.positionMs, state.durationMs, immediate)
    }

    /**
     * Push an explicit completion. Called on natural finish and on manual mark-as-played. Skipped
     * when this completion is itself the result of a remote apply (the echo guard), so a synced
     * completion is not bounced straight back to the backend.
     */
    fun pushCompletion(episode: BaseEpisode) {
        if (!supabaseClient.isLoggedIn()) {
            return
        }
        if (isApplyingRemote(episode.uuid)) {
            return
        }
        val durationMs = episode.durationMs
        val totalSec = if (durationMs > 0) durationMs / 1000 else episode.playedUpToMs / 1000
        val episodeKey = episode.uuid
        val episodeUrl = episode.downloadUrl
        val now = System.currentTimeMillis()

        applicationScope.launch(Dispatchers.IO) {
            try {
                val userId = supabaseClient.getUserId() ?: return@launch
                val feedUrl = feedUrlForEpisode(episode)
                val row = JSONObject()
                row.put("user_id", userId)
                row.put("episode_key", episodeKey)
                row.put("episode_url", episodeUrl ?: JSONObject.NULL)
                row.put("position_sec", totalSec)
                row.put("total_sec", totalSec)
                row.put("completed", true)
                row.put("feed_url", feedUrl ?: JSONObject.NULL)
                row.put("device_id", getOrCreateInstallId())
                row.put("device_name", Build.MODEL)
                row.put("updated_at_ms", now)
                supabaseClient.upsert(TABLE_PLAYBACK_STATE, "user_id,episode_key", JSONArray().put(row))
            } catch (e: Exception) {
                LogBuffer.i(LogBuffer.TAG_PLAYBACK, "PodHopper completion push failed, will retry on next finish: ${e.message}")
            }
        }
    }

    /**
     * The RSS feed url of the episode's podcast, or null for non podcast episodes. Carried in each
     * push so another device can fetch and add the podcast (as not subscribed) on demand when it
     * adopts an episode it does not have locally yet. Blocking; call off the main thread.
     */
    private fun feedUrlForEpisode(episode: BaseEpisode): String? {
        val podcastEpisode = episode as? PodcastEpisode ?: return null
        return podcastManager.findPodcastByUuidBlocking(podcastEpisode.podcastUuid)?.podcastUrl
    }

    /**
     * Pull positions and completions newer than our cursor that were written by other devices, and
     * apply them locally. Drains every page so one open fully catches up. Safe to call from any
     * thread; the work runs on the IO dispatcher.
     */
    fun pullLatestPositions(adoptCurrentEpisode: Boolean = false) {
        if (!supabaseClient.isLoggedIn()) {
            return
        }
        applicationScope.launch(Dispatchers.IO) {
            try {
                val installId = getOrCreateInstallId()
                var cursor = prefs().getLong(PREF_LAST_PULL_MS, FIRST_SYNC_SENTINEL)
                if (cursor == FIRST_SYNC_SENTINEL) {
                    // First sync on this device: adopt "now" so we resume current state instead of
                    // replaying the entire history oldest-first over many opens.
                    cursor = System.currentTimeMillis()
                    prefs().edit().putLong(PREF_LAST_PULL_MS, cursor).apply()
                }

                var adoptCandidate: AdoptCandidate? = null
                while (true) {
                    val query = "select=episode_key,position_sec,total_sec,completed,updated_at_ms,device_id,feed_url" +
                        "&updated_at_ms=gt.$cursor" +
                        "&device_id=neq.$installId" +
                        "&order=updated_at_ms.asc" +
                        "&limit=${PodHopperConfig.PULL_PAGE_LIMIT}"
                    val rows = supabaseClient.select(TABLE_PLAYBACK_STATE, query)
                    val count = rows.length()
                    if (count == 0) {
                        break
                    }
                    val result = applyRows(rows)
                    result.latestInProgress?.let { candidate ->
                        if (adoptCandidate == null || candidate.updatedAtMs > adoptCandidate.updatedAtMs) {
                            adoptCandidate = candidate
                        }
                    }
                    if (result.maxTs > cursor) {
                        // Advance past everything in this page. Rows we could not apply yet are not
                        // lost: they were parked for retry, so moving the cursor is safe.
                        cursor = result.maxTs
                        prefs().edit().putLong(PREF_LAST_PULL_MS, cursor).apply()
                    } else {
                        // Cursor did not advance (should not happen given the gt filter); stop
                        // rather than risk looping on the same page.
                        break
                    }
                    if (count < PodHopperConfig.PULL_PAGE_LIMIT) {
                        break
                    }
                }

                retryParkedRows()

                // Switch the player to the most recently played episode from another device, but only
                // for the foreground and service-start pulls (not, say, opening a podcast page), and
                // only when the user has left the setting on.
                if (adoptCurrentEpisode) {
                    adoptCandidate?.let { maybeAdoptLatest(it) }
                }
            } catch (e: Exception) {
                LogBuffer.i(LogBuffer.TAG_PLAYBACK, "PodHopper position pull failed: ${e.message}")
            }
        }
    }

    private data class AdoptCandidate(val episodeKey: String, val feedUrl: String?, val updatedAtMs: Long)

    private data class ApplyResult(val maxTs: Long, val latestInProgress: AdoptCandidate?)

    /**
     * Switches the player to [candidate] if the auto-switch setting is on. If the episode is not on
     * this device yet, its podcast is fetched and added (as not subscribed) from the feed url the
     * row carried, which makes the episode exist locally so it can be loaded. The actual player
     * switch, with its own do-not-interrupt guards, lives in PlaybackManager.
     */
    private suspend fun maybeAdoptLatest(candidate: AdoptCandidate) {
        if (!settings.autoSwitchPlayerToCurrentPodcast.value) {
            return
        }
        var episode = episodeManager.findByUuid(candidate.episodeKey)
        if (episode == null && !candidate.feedUrl.isNullOrBlank()) {
            podcastManager.addFeedUrlAsUnsubscribed(candidate.feedUrl)
            episode = episodeManager.findByUuid(candidate.episodeKey)
        }
        val target = episode ?: return
        playbackManager.get().adoptCurrentEpisodeFromSync(target)
    }

    /**
     * Pulls this episode's latest cross-device position and applies it BEFORE playback reads the
     * resume point, so a play (fresh or resume) starts from the synced position. Time-bounded by
     * [PLAY_PULL_TIMEOUT_MS] so a slow or offline network cannot hang playback. The device_id
     * filter means a row only comes back when another device wrote it most recently, so this never
     * fights this device's own latest position.
     */
    suspend fun applyRemotePositionBeforePlay(episode: BaseEpisode): PlayPullResult {
        if (!supabaseClient.isLoggedIn()) {
            return PlayPullResult.NONE
        }
        return try {
            val outcome = withTimeoutOrNull(PLAY_PULL_TIMEOUT_MS) {
                withContext(Dispatchers.IO) {
                    val installId = getOrCreateInstallId()
                    val query = "select=position_sec,total_sec,updated_at_ms,device_id" +
                        "&episode_key=eq.${episode.uuid}" +
                        "&device_id=neq.$installId" +
                        "&limit=1"
                    val rows = supabaseClient.select(TABLE_PLAYBACK_STATE, query)
                    if (rows.length() == 0) {
                        PlayPullResult.NONE
                    } else {
                        val positionSec = rows.getJSONObject(0).optInt("position_sec", -1)
                        if (positionSec < 0) {
                            PlayPullResult.NONE
                        } else {
                            episodeManager.updatePlayedUpToBlocking(episode, positionSec.toDouble(), forceUpdate = true)
                            PlayPullResult.APPLIED
                        }
                    }
                }
            }
            if (outcome == null) {
                LogBuffer.i(LogBuffer.TAG_PLAYBACK, "PodHopper play-pull timed out, playing from local position")
                PlayPullResult.FAILED
            } else {
                outcome
            }
        } catch (e: Exception) {
            LogBuffer.i(LogBuffer.TAG_PLAYBACK, "PodHopper play-pull failed: ${e.message}")
            PlayPullResult.FAILED
        }
    }

    enum class PlayPullResult {
        APPLIED,
        NONE,
        FAILED,
    }

    /** Applies every row in a page, parking those whose episode is not local yet. Returns the
     *  highest updated_at_ms seen, so the caller can advance the cursor past the whole page. */
    private suspend fun applyRows(rows: JSONArray): ApplyResult {
        var maxTs = 0L
        var latestInProgress: AdoptCandidate? = null
        for (i in 0 until rows.length()) {
            val row = rows.getJSONObject(i)
            val updatedAtMs = row.optLong("updated_at_ms", 0L)
            if (updatedAtMs > maxTs) {
                maxTs = updatedAtMs
            }
            val episodeKey = row.optString("episode_key")
            if (episodeKey.isEmpty()) {
                continue
            }
            val positionSec = row.optInt("position_sec", -1)
            val totalSec = row.optInt("total_sec", 0)
            val completed = row.optBoolean("completed", false)
            applyOrPark(episodeKey, positionSec, totalSec, completed, updatedAtMs)

            // Track the most recent in-progress episode as the candidate to switch the player to. A
            // finished episode is not a now-playing, so completions are skipped.
            val isCompletion = completed || (totalSec > 0 && positionSec >= totalSec)
            if (!isCompletion && (latestInProgress == null || updatedAtMs > latestInProgress.updatedAtMs)) {
                val feedUrl = row.optString("feed_url").takeIf { it.isNotEmpty() }
                latestInProgress = AdoptCandidate(episodeKey, feedUrl, updatedAtMs)
            }
        }
        return ApplyResult(maxTs, latestInProgress)
    }

    private suspend fun applyOrPark(episodeKey: String, positionSec: Int, totalSec: Int, completed: Boolean, remoteTs: Long) {
        val episode = episodeManager.findByUuid(episodeKey)
        if (episode == null) {
            // Not in the local database yet (feed not refreshed). Park it; retried on the next pull
            // or once a refresh brings the episode in, so the position is never silently dropped.
            parkRow(episodeKey, positionSec, totalSec, completed, remoteTs)
            return
        }
        applyOne(episode, positionSec, totalSec, completed, remoteTs)
    }

    private fun applyOne(episode: BaseEpisode, positionSec: Int, totalSec: Int, completed: Boolean, remoteTs: Long) {
        // Guard 1: never apply a remote change older than this device's own last change. This stops
        // a stale row from rewinding progress or undoing a newer local completion.
        val localModified = maxOf(episode.playedUpToModified ?: 0L, episode.playingStatusModified ?: 0L)
        if (localModified > remoteTs) {
            return
        }

        // Guard 2: never overwrite the episode actively playing on this device right now.
        val manager = playbackManager.get()
        if (episode.uuid == manager.getCurrentEpisode()?.uuid && manager.isPlaying()) {
            return
        }

        val isCompletion = completed || (totalSec > 0 && positionSec >= totalSec)
        if (isCompletion) {
            // A real local completion: removes from Up Next and auto-archives per the podcast's
            // settings, so finishing elsewhere removes it here. Guarded so this apply is not echoed
            // back to the backend as a fresh completion push.
            applyingUuids.add(episode.uuid)
            try {
                episodeManager.markAsPlayedBlocking(episode, manager, podcastManager)
            } finally {
                applyingUuids.remove(episode.uuid)
            }
        } else if (positionSec >= 0) {
            episodeManager.updatePlayedUpToBlocking(episode, positionSec.toDouble(), forceUpdate = true)
            if (episode.playingStatus == EpisodePlayingStatus.NOT_PLAYED) {
                // Keep status and position consistent: a synced mid-episode position is in progress,
                // not "not played".
                episodeManager.updatePlayingStatusBlocking(episode, EpisodePlayingStatus.IN_PROGRESS)
            }
        }
    }

    private fun parkRow(episodeKey: String, positionSec: Int, totalSec: Int, completed: Boolean, remoteTs: Long) {
        try {
            val parked = readParked()
            val entry = JSONObject()
            entry.put("p", positionSec)
            entry.put("t", totalSec)
            entry.put("c", completed)
            entry.put("u", remoteTs)
            parked.put(episodeKey, entry)

            // Bound the list: if it grows past the cap, drop the oldest entries by remote timestamp.
            if (parked.length() > MAX_PARKED) {
                val keys = parked.keys().asSequence().toList()
                val oldestFirst = keys.sortedBy { parked.getJSONObject(it).optLong("u", 0L) }
                val toDrop = parked.length() - MAX_PARKED
                oldestFirst.take(toDrop).forEach { parked.remove(it) }
            }
            writeParked(parked)
        } catch (e: Exception) {
            LogBuffer.i(LogBuffer.TAG_PLAYBACK, "PodHopper park failed: ${e.message}")
        }
    }

    private suspend fun retryParkedRows() {
        val parked = try {
            readParked()
        } catch (e: Exception) {
            return
        }
        if (parked.length() == 0) {
            return
        }
        var changed = false
        val keys = parked.keys().asSequence().toList()
        for (episodeKey in keys) {
            val episode = episodeManager.findByUuid(episodeKey) ?: continue
            val entry = parked.getJSONObject(episodeKey)
            applyOne(
                episode = episode,
                positionSec = entry.optInt("p", -1),
                totalSec = entry.optInt("t", 0),
                completed = entry.optBoolean("c", false),
                remoteTs = entry.optLong("u", 0L),
            )
            parked.remove(episodeKey)
            changed = true
        }
        if (changed) {
            writeParked(parked)
        }
    }

    private fun readParked(): JSONObject {
        val raw = prefs().getString(PREF_PARKED, null) ?: return JSONObject()
        return try {
            JSONObject(raw)
        } catch (e: Exception) {
            JSONObject()
        }
    }

    private fun writeParked(parked: JSONObject) {
        prefs().edit().putString(PREF_PARKED, parked.toString()).apply()
    }

    private fun getOrCreateInstallId(): String {
        val prefs = prefs()
        val existing = prefs.getString(PREF_INSTALL_ID, null)
        if (existing != null) {
            return existing
        }
        val generated = UUID.randomUUID().toString()
        prefs.edit().putString(PREF_INSTALL_ID, generated).apply()
        return generated
    }

    private fun prefs(): SharedPreferences {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }

    /**
     * Resets this device's position sync bookkeeping so a future account starts clean. Clears the
     * pull cursor and any parked rows, which sends the cursor back to the first-sync sentinel. The
     * install id is kept, since it identifies the device, not the account. Does not touch any
     * episode, podcast, or playback data on the device.
     */
    fun clearLocalSyncState() {
        prefs().edit()
            .remove(PREF_LAST_PULL_MS)
            .remove(PREF_PARKED)
            .apply()
    }

    companion object {
        private const val TABLE_PLAYBACK_STATE = "playback_state"
        private const val PREF_NAME = "podhopper_position_sync"
        private const val PREF_INSTALL_ID = "install_id"
        private const val PREF_LAST_PULL_MS = "last_pull_ms"
        private const val PREF_PARKED = "parked_rows"
        private const val MIN_PUSH_INTERVAL_MS = 4000L
        private const val PLAY_PULL_TIMEOUT_MS = 5000L
        private const val FIRST_SYNC_SENTINEL = -1L
        private const val MAX_PARKED = 500
    }
}
