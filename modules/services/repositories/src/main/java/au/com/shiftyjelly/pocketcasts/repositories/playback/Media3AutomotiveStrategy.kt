package au.com.shiftyjelly.pocketcasts.repositories.playback

import android.content.Context
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.CommandButton
import au.com.shiftyjelly.pocketcasts.models.entity.BaseEpisode
import au.com.shiftyjelly.pocketcasts.preferences.Settings
import au.com.shiftyjelly.pocketcasts.preferences.Settings.MediaNotificationControls

@UnstableApi
internal class Media3AutomotiveStrategy : AutomotiveSessionStrategy {

    override fun buildLayout(
        playbackManager: PlaybackManager,
        settings: Settings,
        context: Context,
        buildCustomActionButton: (MediaNotificationControls, BaseEpisode?) -> CommandButton?,
    ): AutomotiveSessionStrategy.ButtonLayout {
        val buttons = mutableListOf<CommandButton>()
        val currentEpisode = playbackManager.getCurrentEpisode()

        // PodHopper: skip-forward/back are no longer custom buttons here. They are now standard
        // seek-forward/back player commands (see PocketCastsForwardingPlayer), so the car draws and
        // maps them itself, including the hardware/steering-wheel keys, with no second set to
        // duplicate them. Only the other custom actions (speed, star, and so on) remain custom.
        val visibleCount = if (settings.customMediaActionsVisibility.value) MediaNotificationControls.MAX_VISIBLE_OPTIONS else 0
        settings.mediaControlItems.value.take(visibleCount).forEach { mediaControl ->
            buildCustomActionButton(mediaControl, currentEpisode)?.let(buttons::add)
        }

        return AutomotiveSessionStrategy.ButtonLayout(primaryButtons = buttons, overflowButtons = emptyList())
    }
}
