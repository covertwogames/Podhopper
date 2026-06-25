package au.com.shiftyjelly.pocketcasts.repositories.podhopper

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import au.com.shiftyjelly.pocketcasts.coroutines.di.ApplicationScope
import au.com.shiftyjelly.pocketcasts.models.entity.BaseEpisode
import au.com.shiftyjelly.pocketcasts.repositories.playback.PlaybackManager
import au.com.shiftyjelly.pocketcasts.repositories.podcast.EpisodeManager
import au.com.shiftyjelly.pocketcasts.repositories.podcast.PodcastManager
import au.com.shiftyjelly.pocketcasts.utils.log.LogBuffer
import dagger.Lazy
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
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
 * Syncs playback position across devices (phone and car) through Supabase.
 *
 * This restores the cross device resume behaviour that worked in the AntennaPod build.
 * Two pulls (when the playback service starts, and when the app comes to the foreground)
 * and three pushes (a periodic sample while playing, an immediate push on pause, and an
 * immediate push on service shutdown) keep both surfaces in step.
 *
 * Protection rules:
 *  - A pull never overwrites the episode that is currently playing on this device.
 *  - A position within the last [SMART_MARK_AS_PLAYED_MS] of the end marks the episode played.
 *
 * Install id and the last pull timestamp live in a dedicated SharedPreferences file so that
 * nothing here touches the shared app Settings.
 */
@Singleton
class PodHopperPositionSync @Inject constructor(
    private val supabaseClient: SupabaseClient,
    private val episodeManager: EpisodeManager,
    private val podcastManager: PodcastManager,
    private val playbackManager: Lazy<PlaybackManager>,
    @ApplicationContext private val context: Context,
    @ApplicationScope private val applicationScope: CoroutineScope,
) {
    @Volatile
    private var lastPushAttemptMs = 0L

    init {
        observeSignIn()
    }

    /**
     * Pulls the latest cross-device positions the instant sign-in completes, so resume points are
     * correct as soon as a freshly signed-in user starts playing, instead of waiting for the next
     * foreground pull. Same approach as the subscription sync: watch login state, drop the startup
     * value, and react only to the transition into the signed-in state. A position pull is
     * idempotent, so no in-flight guard is needed here.
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
     * [MIN_PUSH_INTERVAL_MS] unless [immediate] is true (pause and shutdown).
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
                val row = JSONObject()
                row.put("user_id", userId)
                row.put("episode_key", episodeKey)
                row.put("episode_url", episodeUrl ?: JSONObject.NULL)
                row.put("position_sec", positionSec)
                row.put("total_sec", totalSec)
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
     * Push the current episode's live position. Used for the shutdown push, where the caller
     * does not have the position to hand. Reads the live playback state from PlaybackManager.
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
     * Pull positions newer than our last pull that were written by other devices, and apply
     * them locally. Safe to call from any thread; the work runs on the IO dispatcher.
     */
    fun pullLatestPositions() {
        if (!supabaseClient.isLoggedIn()) {
            return
        }
        applicationScope.launch(Dispatchers.IO) {
            try {
                val installId = getOrCreateInstallId()
                val lastPull = prefs().getLong(PREF_LAST_PULL_MS, 0L)
                val query = "select=episode_key,position_sec,total_sec,updated_at_ms,device_id" +
                    "&updated_at_ms=gt.$lastPull" +
                    "&device_id=neq.$installId" +
                    "&order=updated_at_ms.asc" +
                    "&limit=${PodHopperConfig.PULL_PAGE_LIMIT}"
                val rows = supabaseClient.select(TABLE_PLAYBACK_STATE, query)
                val newest = applyRemotePositions(rows, lastPull)
                if (newest > lastPull) {
                    prefs().edit().putLong(PREF_LAST_PULL_MS, newest).apply()
                }
            } catch (e: Exception) {
                LogBuffer.i(LogBuffer.TAG_PLAYBACK, "PodHopper position pull failed: ${e.message}")
            }
        }
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

    private suspend fun applyRemotePositions(rows: JSONArray, lastPull: Long): Long {
        var newest = lastPull
        val manager = playbackManager.get()
        val currentUuid = manager.getCurrentEpisode()?.uuid
        val currentlyPlaying = manager.isPlaying()

        for (i in 0 until rows.length()) {
            val row = rows.getJSONObject(i)
            val updatedAtMs = row.optLong("updated_at_ms", 0L)
            if (updatedAtMs > newest) {
                newest = updatedAtMs
            }

            val episodeKey = row.optString("episode_key")
            if (episodeKey.isEmpty()) {
                continue
            }
            val positionSec = row.optInt("position_sec", -1)
            if (positionSec < 0) {
                continue
            }

            val episode = episodeManager.findByUuid(episodeKey) ?: continue

            // Never overwrite the episode that is actively playing on this device.
            if (episode.uuid == currentUuid && currentlyPlaying) {
                continue
            }

            val durationMs = episode.durationMs
            val almostEnded = durationMs > 0 && positionSec * 1000 >= durationMs - SMART_MARK_AS_PLAYED_MS
            if (almostEnded) {
                episodeManager.markAsPlayedBlocking(episode, manager, podcastManager)
            } else {
                episodeManager.updatePlayedUpToBlocking(episode, positionSec.toDouble(), forceUpdate = true)
            }
        }
        return newest
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

    companion object {
        private const val TABLE_PLAYBACK_STATE = "playback_state"
        private const val PREF_NAME = "podhopper_position_sync"
        private const val PREF_INSTALL_ID = "install_id"
        private const val PREF_LAST_PULL_MS = "last_pull_ms"
        private const val MIN_PUSH_INTERVAL_MS = 4000L
        private const val SMART_MARK_AS_PLAYED_MS = 20000
        private const val PLAY_PULL_TIMEOUT_MS = 3000L
    }
}
