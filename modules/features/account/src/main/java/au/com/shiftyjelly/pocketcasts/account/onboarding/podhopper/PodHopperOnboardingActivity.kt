package au.com.shiftyjelly.pocketcasts.account.onboarding.podhopper

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import au.com.shiftyjelly.pocketcasts.compose.AppThemeWithBackground
import au.com.shiftyjelly.pocketcasts.preferences.Settings
import au.com.shiftyjelly.pocketcasts.ui.theme.Theme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * PodHopper's own first-run flow, shown in place of the Pocket Casts onboarding. It hosts the
 * welcome / login / signup / notifications screens, asks for the notification permission when the
 * user opts in, and marks initial onboarding complete so it never shows again. Reaching the end by
 * any path (sign in, sign up, or skip) returns to the app on the Podcasts tab.
 */
@AndroidEntryPoint
class PodHopperOnboardingActivity : AppCompatActivity() {

    @Inject
    lateinit var theme: Theme

    @Inject
    lateinit var settings: Settings

    private val viewModel by viewModels<PodHopperOnboardingViewModel>()

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            // Proceed regardless of grant or deny; the checkbox is intent, the OS makes the call.
            finishOnboarding()
        }

    @SuppressLint("SourceLockedOrientationActivity")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
        theme.setupThemeForConfig(this, resources.configuration)
        enableEdgeToEdge()

        val loginOnly = intent.getBooleanExtra(EXTRA_LOGIN_ONLY, false)
        val startSignedIn = !loginOnly && viewModel.isSignedIn()

        setContent {
            AppThemeWithBackground(theme.activeTheme) {
                PodHopperOnboardingFlow(
                    viewModel = viewModel,
                    startSignedIn = startSignedIn,
                    loginOnly = loginOnly,
                    onExitApp = { if (loginOnly) finish() else finishAffinity() },
                    onGetStarted = { receiveNotifications -> onGetStarted(receiveNotifications) },
                    onLoginComplete = {
                        setResult(RESULT_OK)
                        finish()
                    },
                )
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        theme.setupThemeForConfig(this, resources.configuration)
    }

    private fun onGetStarted(receiveNotifications: Boolean) {
        val needsRequest = receiveNotifications &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        if (needsRequest) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            finishOnboarding()
        }
    }

    private fun finishOnboarding() {
        // Mark the in-app notification prompt as handled so the Podcasts tab does not ask again,
        // and record that first-run onboarding is done so this screen never shows again.
        settings.notificationsPromptAcknowledged.set(value = true, updateModifiedAt = true)
        settings.setHasDoneInitialOnboarding()
        setResult(RESULT_OK)
        finish()
    }

    companion object {
        private const val EXTRA_LOGIN_ONLY = "login_only"

        fun newInstance(context: Context, loginOnly: Boolean = false): Intent {
            return Intent(context, PodHopperOnboardingActivity::class.java)
                .putExtra(EXTRA_LOGIN_ONLY, loginOnly)
        }
    }
}
