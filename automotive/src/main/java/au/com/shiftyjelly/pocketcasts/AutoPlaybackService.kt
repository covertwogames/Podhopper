package au.com.shiftyjelly.pocketcasts

import au.com.shiftyjelly.pocketcasts.analytics.SourceView
import au.com.shiftyjelly.pocketcasts.coroutines.di.ApplicationScope
import au.com.shiftyjelly.pocketcasts.repositories.playback.PlaybackService
import au.com.shiftyjelly.pocketcasts.repositories.podhopper.PodHopperSubscriptionSync
import au.com.shiftyjelly.pocketcasts.repositories.refresh.RefreshPodcastsTask
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import timber.log.Timber

@AndroidEntryPoint
class AutoPlaybackService : PlaybackService() {

    @Inject @ApplicationScope
    lateinit var applicationScope: CoroutineScope

    @Inject
    lateinit var podHopperSubscriptionSync: PodHopperSubscriptionSync

    private var reconcileJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        settings.setAutomotiveConnectedToMediaSession(false)

        RefreshPodcastsTask.runNow(this, applicationScope)

        // PodHopper: keep the car's subscriptions fresh for the whole session. The phone runs this
        // poll loop from MainActivity; the car has no equivalent activity, so anchor it to the media
        // service, which lives for the listening session. The first tick runs immediately (an inbound
        // subscription pull on service start), then once every 30s. Idempotent and a no-op while
        // signed out, so calling it here unconditionally is safe.
        podHopperSubscriptionSync.startPeriodicSync()

        // PodHopper: while the car media session is alive, reconcile the now-playing window to the
        // freshest across devices every 30s. This covers the "sitting in the car, paused, and the
        // phone played something" case, where no connect or browse callback fires. The reconcile is
        // quiet and login-gated, and never changes the episode while actively playing.
        reconcileJob = applicationScope.launch {
            while (isActive) {
                delay(NOW_PLAYING_RECONCILE_INTERVAL_MS)
                playbackManager.reconcileNowPlaying()
            }
        }

        Timber.d("Auto playback service created")
    }

    override fun onDestroy() {
        super.onDestroy()
        Timber.d("Auto playback service destroyed")

        // PodHopper: stop the subscription poll loop started in onCreate.
        podHopperSubscriptionSync.stopPeriodicSync()

        // PodHopper: stop the now-playing reconcile loop started in onCreate.
        reconcileJob?.cancel()
        reconcileJob = null

        playbackManager.pause(transientLoss = false, sourceView = SourceView.AUTO_PAUSE)
    }

    companion object {
        private const val NOW_PLAYING_RECONCILE_INTERVAL_MS = 30_000L
    }
}
