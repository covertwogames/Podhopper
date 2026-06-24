package au.com.shiftyjelly.pocketcasts

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import au.com.shiftyjelly.pocketcasts.ui.helper.AppIcon
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * After the app is updated, re-asserts the user's saved icon so its alias is the single
 * enabled launcher component. This protects against an older build having left a different
 * alias enabled state behind, which the update would otherwise preserve. On a normal update
 * where the saved icon already matches the enabled alias this is a no-op.
 */
@AndroidEntryPoint
class AppIconUpdateReceiver : BroadcastReceiver() {

    @Inject lateinit var appIcon: AppIcon

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            appIcon.enableSelectedAlias(appIcon.activeAppIcon)
        }
    }
}
