package au.com.shiftyjelly.pocketcasts.repositories.appreview

import android.app.Activity
import au.com.shiftyjelly.pocketcasts.preferences.Settings
import au.com.shiftyjelly.pocketcasts.repositories.user.StatsManager
import com.google.android.play.core.ktx.launchReview
import com.google.android.play.core.ktx.requestReview
import com.google.android.play.core.review.ReviewInfo
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import timber.log.Timber
import com.google.android.play.core.review.ReviewManager as GoogleReviewManager

@Singleton
class AppReviewManagerImpl(
    private val settings: Settings,
    private val statsManager: StatsManager,
    private val googleManager: GoogleReviewManager,
    private val loopIdleDuration: Duration,
) : AppReviewManager {
    @Inject
    constructor(
        settings: Settings,
        statsManager: StatsManager,
        googleManager: GoogleReviewManager,
    ) : this(
        settings = settings,
        statsManager = statsManager,
        googleManager = googleManager,
        loopIdleDuration = 5.seconds,
    )

    private val signalChannel = Channel<AppReviewSignal>()
    override val showPromptSignal: Flow<AppReviewSignal> get() = signalChannel.receiveAsFlow()

    private val isMonitoring = AtomicBoolean()

    /**
     * Prompts the user for a Google Play in-app review based purely on how much they have
     * listened to. The first prompt is offered once total listening time reaches
     * [FIRST_THRESHOLD_SECS], and a second once it reaches [SECOND_THRESHOLD_SECS]. The user is
     * never prompted more than [MAX_PROMPTS] times. Google decides whether to actually display a
     * rating card, and will not re-prompt a user who has already reviewed.
     */
    override suspend fun monitorAppReviewReasons() {
        if (isMonitoring.getAndSet(true)) {
            return
        }
        while (true) {
            val promptCount = settings.appReviewPromptCount.value
            if (promptCount >= MAX_PROMPTS) {
                break
            }
            val thresholdSecs = if (promptCount == 0) FIRST_THRESHOLD_SECS else SECOND_THRESHOLD_SECS
            if (statsManager.totalListeningTimeSecs >= thresholdSecs) {
                val reviewInfo = runCatching { googleManager.requestReview() }.getOrElse { error ->
                    Timber.e(error, "Could not request review flow.")
                    null
                }
                if (reviewInfo != null) {
                    triggerPrompt(reviewInfo)
                }
            }
            delay(loopIdleDuration)
        }
    }

    override suspend fun launchReview(activity: Activity, reviewInfo: ReviewInfo) {
        runCatching {
            googleManager.launchReview(activity, reviewInfo)
        }.onFailure { error ->
            Timber.e(error, "Could not launch review flow.")
        }
    }

    suspend fun triggerPrompt(reviewInfo: ReviewInfo) {
        val result = suspendCancellableCoroutine { continuation ->
            val data = AppReviewSignalImpl(
                reviewInfo = reviewInfo,
                continuation = continuation,
            )
            if (signalChannel.trySend(data).isFailure) {
                continuation.resume(AppReviewSignal.Result.Ignored)
            }
        }
        if (result == AppReviewSignal.Result.Consumed) {
            val promptCount = settings.appReviewPromptCount.value
            settings.appReviewPromptCount.set(promptCount + 1, updateModifiedAt = false)
        }
    }

    companion object {
        private const val MAX_PROMPTS = 2
        private const val FIRST_THRESHOLD_SECS = 3L * 60 * 60
        private const val SECOND_THRESHOLD_SECS = 20L * 60 * 60
    }
}

private class AppReviewSignalImpl(
    override val reviewInfo: ReviewInfo,
    private val continuation: CancellableContinuation<AppReviewSignal.Result>,
) : AppReviewSignal {
    override fun consume() {
        if (continuation.isActive) {
            runCatching { continuation.resume(AppReviewSignal.Result.Consumed) }
        }
    }

    override fun ignore() {
        if (continuation.isActive) {
            runCatching { continuation.resume(AppReviewSignal.Result.Ignored) }
        }
    }

    override fun toString(): String {
        return "AppReviewSignalImpl(reviewInfo=$reviewInfo)"
    }
}
