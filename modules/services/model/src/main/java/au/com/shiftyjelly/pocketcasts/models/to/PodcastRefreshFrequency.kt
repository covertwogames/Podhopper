package au.com.shiftyjelly.pocketcasts.models.to

import androidx.annotation.StringRes
import au.com.shiftyjelly.pocketcasts.localization.R

/**
 * How often PodHopper refreshes subscribed podcasts in the background. Backed by an index stored in
 * settings. [Off] cancels the periodic refresh entirely; every other value is the interval in hours.
 */
sealed class PodcastRefreshFrequency(
    val index: Int,
    val hours: Long,
    @StringRes val stringRes: Int,
) {
    val isOn: Boolean
        get() = this != Off

    companion object {
        val all: List<PodcastRefreshFrequency>
            get() = listOf(Off, Hours1, Hours3, Hours6, Hours12, Hours24)

        val default: PodcastRefreshFrequency
            get() = Hours1

        fun fromIndex(index: Int): PodcastRefreshFrequency? = all.find { it.index == index }
    }

    data object Off : PodcastRefreshFrequency(0, 0, R.string.settings_refresh_frequency_off)
    data object Hours1 : PodcastRefreshFrequency(1, 1, R.string.settings_refresh_frequency_1_hour)
    data object Hours3 : PodcastRefreshFrequency(2, 3, R.string.settings_refresh_frequency_3_hours)
    data object Hours6 : PodcastRefreshFrequency(3, 6, R.string.settings_refresh_frequency_6_hours)
    data object Hours12 : PodcastRefreshFrequency(4, 12, R.string.settings_refresh_frequency_12_hours)
    data object Hours24 : PodcastRefreshFrequency(5, 24, R.string.settings_refresh_frequency_24_hours)
}
