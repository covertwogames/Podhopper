package au.com.shiftyjelly.pocketcasts.repositories.appreview

import android.app.Activity
import com.google.android.play.core.review.ReviewInfo
import kotlinx.coroutines.flow.Flow

interface AppReviewManager {
    val showPromptSignal: Flow<AppReviewSignal>

    suspend fun monitorAppReviewReasons()

    suspend fun launchReview(activity: Activity, reviewInfo: ReviewInfo)
}

interface AppReviewSignal {
    val reviewInfo: ReviewInfo

    fun consume()

    fun ignore()

    enum class Result {
        Consumed,
        Ignored,
    }
}
