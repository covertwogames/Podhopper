package au.com.shiftyjelly.pocketcasts

import au.com.shiftyjelly.pocketcasts.analytics.SourceView
import au.com.shiftyjelly.pocketcasts.coroutines.di.ApplicationScope
import au.com.shiftyjelly.pocketcasts.repositories.playback.PlaybackService
import au.com.shiftyjelly.pocketcasts.repositories.podhopper.PodHopperSubscriptionSync
import au.com.shiftyjelly.pocketcasts.repositories.refresh.RefreshPodcastsTask
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import timber.log.Timber

@AndroidEntryPoint
class AutoPlaybackService : PlaybackService() {

    @Inject @ApplicationScope
    lateinit var applicationScope: CoroutineScope

    @Inject
    lateinit var podHopperSubscriptionSync: PodHopperSubscriptionSync

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

        Timber.d("Auto playback service created")
    }

    override fun onDestroy() {
        super.onDestroy()
        Timber.d("Auto playback service destroyed")

        // PodHopper: stop the subscription poll loop started in onCreate.
        podHopperSubscriptionSync.stopPeriodicSync()

        playbackManager.pause(transientLoss = false, sourceView = SourceView.AUTO_PAUSE)
    }
}
