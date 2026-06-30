package au.com.shiftyjelly.pocketcasts.repositories.playback

import android.content.Context
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.CommandButton
import au.com.shiftyjelly.pocketcasts.models.entity.BaseEpisode
import au.com.shiftyjelly.pocketcasts.preferences.Settings
import au.com.shiftyjelly.pocketcasts.preferences.Settings.MediaNotificationControls
import au.com.shiftyjelly.pocketcasts.localization.R as LR

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

        // PodHopper: declare seek-back and seek-forward buttons backed by the standard seek player
        // commands. Fed to the session through setMediaButtonPreferences (see MediaSessionManager),
        // these land in the car's backward and forward transport slots, so the car renders them and
        // maps its hardware/steering-wheel keys to whatever sits in those slots. The forwarding
        // player routes both commands to the configured skip amounts. The slot for each button is
        // resolved automatically from its standard icon and command, so no slot is set explicitly.
        buttons.add(
            CommandButton.Builder(CommandButton.ICON_SKIP_BACK)
                .setPlayerCommand(Player.COMMAND_SEEK_BACK)
                .setDisplayName(context.getString(LR.string.skip_back))
                .build(),
        )
        buttons.add(
            CommandButton.Builder(CommandButton.ICON_SKIP_FORWARD)
                .setPlayerCommand(Player.COMMAND_SEEK_FORWARD)
                .setDisplayName(context.getString(LR.string.skip_forward))
                .build(),
        )

        val visibleCount = if (settings.customMediaActionsVisibility.value) MediaNotificationControls.MAX_VISIBLE_OPTIONS else 0
        settings.mediaControlItems.value.take(visibleCount).forEach { mediaControl ->
            buildCustomActionButton(mediaControl, currentEpisode)?.let(buttons::add)
        }

        return AutomotiveSessionStrategy.ButtonLayout(primaryButtons = buttons, overflowButtons = emptyList())
    }
}
