package au.com.shiftyjelly.pocketcasts.ui.helper

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import au.com.shiftyjelly.pocketcasts.payment.SubscriptionTier
import au.com.shiftyjelly.pocketcasts.preferences.Settings
import au.com.shiftyjelly.pocketcasts.preferences.model.AppIconSetting
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import au.com.shiftyjelly.pocketcasts.images.R as IR
import au.com.shiftyjelly.pocketcasts.localization.R as LR
import com.automattic.eventhorizon.AppIconType as EventHorizonAppIconType

@Singleton
class AppIcon @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: Settings,
) {

    // All PodHopper icons are free. The analyticsValue reuses existing EventHorizon
    // enum values purely as harmless tracking placeholders, since that enum is generated
    // and cannot have new values added here.
    enum class AppIconType(
        internal val setting: AppIconSetting,
        @StringRes val labelId: Int,
        @DrawableRes val settingsIcon: Int,
        val tier: SubscriptionTier?,
        @DrawableRes val launcherIcon: Int,
        val aliasName: String,
        val analyticsValue: EventHorizonAppIconType,
    ) {
        DEFAULT(
            setting = AppIconSetting.DEFAULT,
            labelId = LR.string.podhopper_app_icon_green,
            settingsIcon = IR.drawable.ic_appicon_ph_green,
            tier = null,
            launcherIcon = IR.mipmap.ic_launcher,
            aliasName = ".ui.MainActivity_0",
            analyticsValue = EventHorizonAppIconType.Default,
        ),
        MIDNIGHT(
            setting = AppIconSetting.MIDNIGHT,
            labelId = LR.string.podhopper_app_icon_midnight,
            settingsIcon = IR.drawable.ic_appicon_ph_midnight,
            tier = null,
            launcherIcon = IR.mipmap.ic_launcher_midnight,
            aliasName = ".ui.MainActivity_1",
            analyticsValue = EventHorizonAppIconType.Dark,
        ),
        LIGHT(
            setting = AppIconSetting.LIGHT,
            labelId = LR.string.podhopper_app_icon_light,
            settingsIcon = IR.drawable.ic_appicon_ph_light,
            tier = null,
            launcherIcon = IR.mipmap.ic_launcher_light,
            aliasName = ".ui.MainActivity_2",
            analyticsValue = EventHorizonAppIconType.RoundLight,
        ),
        INK(
            setting = AppIconSetting.INK,
            labelId = LR.string.podhopper_app_icon_ink,
            settingsIcon = IR.drawable.ic_appicon_ph_ink,
            tier = null,
            launcherIcon = IR.mipmap.ic_launcher_ink,
            aliasName = ".ui.MainActivity_3",
            analyticsValue = EventHorizonAppIconType.RoundDark,
        ),
        SLATE(
            setting = AppIconSetting.SLATE,
            labelId = LR.string.podhopper_app_icon_slate,
            settingsIcon = IR.drawable.ic_appicon_ph_slate,
            tier = null,
            launcherIcon = IR.mipmap.ic_launcher_slate,
            aliasName = ".ui.MainActivity_4",
            analyticsValue = EventHorizonAppIconType.Indigo,
        ),
        COSMIC(
            setting = AppIconSetting.COSMIC,
            labelId = LR.string.podhopper_app_icon_cosmic,
            settingsIcon = IR.drawable.ic_appicon_ph_cosmic,
            tier = null,
            launcherIcon = IR.mipmap.ic_launcher_cosmic,
            aliasName = ".ui.MainActivity_5",
            analyticsValue = EventHorizonAppIconType.Rose,
        ),
        COSMIC_NOIR(
            setting = AppIconSetting.COSMIC_NOIR,
            labelId = LR.string.podhopper_app_icon_cosmic_noir,
            settingsIcon = IR.drawable.ic_appicon_ph_cosmic_noir,
            tier = null,
            launcherIcon = IR.mipmap.ic_launcher_cosmic_noir,
            aliasName = ".ui.MainActivity_6",
            analyticsValue = EventHorizonAppIconType.PocketCats,
        ),
        ;

        companion object {
            fun fromSetting(setting: AppIconSetting) = when (setting) {
                AppIconSetting.DEFAULT -> DEFAULT
                AppIconSetting.MIDNIGHT -> MIDNIGHT
                AppIconSetting.LIGHT -> LIGHT
                AppIconSetting.INK -> INK
                AppIconSetting.SLATE -> SLATE
                AppIconSetting.COSMIC -> COSMIC
                AppIconSetting.COSMIC_NOIR -> COSMIC_NOIR
            }
        }
    }

    var activeAppIcon: AppIconType = AppIconType.fromSetting(settings.appIcon.value)
        set(value) {
            field = value
            settings.appIcon.set(value.setting, updateModifiedAt = false)
        }

    val allAppIconTypes get() = AppIconType.entries

    fun enableSelectedAlias(selectedIconType: AppIconType) {
        val classPath = "au.com.shiftyjelly.pocketcasts"
        AppIconType.entries.forEach { iconType ->
            val componentName = ComponentName(context.packageName, "$classPath${iconType.aliasName}")
            // Exactly one alias is ever enabled, including the default, so the launcher
            // shows a single icon that swaps in place instead of leaving a duplicate.
            val enabledFlag = if (selectedIconType == iconType) {
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            } else {
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            }

            context.packageManager.setComponentEnabledSetting(
                componentName,
                enabledFlag,
                PackageManager.DONT_KILL_APP,
            )
        }
    }
}
