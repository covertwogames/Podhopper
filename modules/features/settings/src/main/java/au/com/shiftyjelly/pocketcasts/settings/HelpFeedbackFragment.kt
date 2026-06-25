package au.com.shiftyjelly.pocketcasts.settings

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.fragment.compose.content
import au.com.shiftyjelly.pocketcasts.compose.AppThemeWithBackground
import au.com.shiftyjelly.pocketcasts.compose.bars.ThemedTopAppBar
import au.com.shiftyjelly.pocketcasts.compose.theme
import au.com.shiftyjelly.pocketcasts.ui.helper.FragmentHostListener
import au.com.shiftyjelly.pocketcasts.views.fragments.BaseFragment
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber
import au.com.shiftyjelly.pocketcasts.localization.R as LR

@AndroidEntryPoint
class HelpFeedbackFragment : BaseFragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ) = content {
        AppThemeWithBackground(theme.activeTheme) {
            HelpFeedbackPage(
                onBackPress = { (activity as? FragmentHostListener)?.closeModal(this) },
            )
        }
    }
}

@Composable
private fun HelpFeedbackPage(
    onBackPress: () -> Unit,
) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.theme.colors.primaryUi02),
    ) {
        ThemedTopAppBar(
            title = stringResource(LR.string.settings_title_help),
            onNavigationClick = onBackPress,
        )
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
        ) {
            Image(
                painter = painterResource(
                    if (MaterialTheme.theme.isDark) {
                        R.drawable.podhopper_lockup_stacked_ondark
                    } else {
                        R.drawable.podhopper_lockup_stacked_onlight
                    },
                ),
                contentDescription = "PodHopper",
                modifier = Modifier
                    .padding(top = 56.dp)
                    .fillMaxWidth(0.6f)
                    .aspectRatio(900f / 590f),
            )

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text = "Thanks for using PodHopper!",
                style = MaterialTheme.typography.h6,
                color = MaterialTheme.theme.colors.primaryText01,
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "For additional information about the app, check out our website at:",
                style = MaterialTheme.typography.body1,
                color = MaterialTheme.theme.colors.primaryText01,
                textAlign = TextAlign.Center,
            )
            Text(
                text = "podhopper.app",
                style = MaterialTheme.typography.body1,
                color = MaterialTheme.theme.colors.primaryInteractive01,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .padding(top = 4.dp)
                    .clickable { openUrl("https://podhopper.app", context) },
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "If you have any questions or feedback about the app, please share it with us at:",
                style = MaterialTheme.typography.body1,
                color = MaterialTheme.theme.colors.primaryText01,
                textAlign = TextAlign.Center,
            )
            Text(
                text = "feedback@covertwogames.com",
                style = MaterialTheme.typography.body1,
                color = MaterialTheme.theme.colors.primaryInteractive01,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .padding(top = 4.dp)
                    .clickable { openEmail("feedback@covertwogames.com", context) },
            )

            Spacer(modifier = Modifier.height(56.dp))
        }
    }
}

private fun openUrl(url: String, context: Context) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
    } catch (e: Exception) {
        Timber.i("Failed to open url $url")
    }
}

private fun openEmail(address: String, context: Context) {
    try {
        context.startActivity(Intent(Intent.ACTION_SENDTO, "mailto:$address".toUri()))
    } catch (e: Exception) {
        Timber.i("Failed to open email $address")
    }
}
