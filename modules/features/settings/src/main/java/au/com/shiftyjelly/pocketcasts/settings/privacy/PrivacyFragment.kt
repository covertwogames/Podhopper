package au.com.shiftyjelly.pocketcasts.settings.privacy

import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import au.com.shiftyjelly.pocketcasts.compose.AppThemeWithBackground
import au.com.shiftyjelly.pocketcasts.compose.bars.ThemedTopAppBar
import au.com.shiftyjelly.pocketcasts.compose.extensions.contentWithoutConsumedInsets
import au.com.shiftyjelly.pocketcasts.compose.theme
import au.com.shiftyjelly.pocketcasts.preferences.Settings
import au.com.shiftyjelly.pocketcasts.settings.R
import au.com.shiftyjelly.pocketcasts.utils.extensions.pxToDp
import au.com.shiftyjelly.pocketcasts.views.fragments.BaseFragment
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import au.com.shiftyjelly.pocketcasts.localization.R as LR

@AndroidEntryPoint
class PrivacyFragment : BaseFragment() {

    @Inject
    lateinit var settings: Settings

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ) = contentWithoutConsumedInsets {
        val bottomInset = settings.bottomInset.collectAsStateWithLifecycle(initialValue = 0)
        AppThemeWithBackground(theme.activeTheme) {
            PrivacyPolicyScreen(
                bottomInset = bottomInset.value.pxToDp(LocalContext.current).dp,
                onBackPress = {
                    activity?.onBackPressedDispatcher?.onBackPressed()
                },
            )
        }
    }
}

@Composable
private fun PrivacyPolicyScreen(
    bottomInset: Dp,
    onBackPress: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sections = listOf(
        R.string.podhopper_privacy_short_heading to R.string.podhopper_privacy_short_body,
        R.string.podhopper_privacy_local_heading to R.string.podhopper_privacy_local_body,
        R.string.podhopper_privacy_sync_heading to R.string.podhopper_privacy_sync_body,
        R.string.podhopper_privacy_dont_heading to R.string.podhopper_privacy_dont_body,
        R.string.podhopper_privacy_security_heading to R.string.podhopper_privacy_security_body,
        R.string.podhopper_privacy_children_heading to R.string.podhopper_privacy_children_body,
        R.string.podhopper_privacy_changes_heading to R.string.podhopper_privacy_changes_body,
        R.string.podhopper_privacy_contact_heading to R.string.podhopper_privacy_contact_body,
    )
    Column(
        modifier = modifier
            .background(MaterialTheme.theme.colors.primaryUi02)
            .fillMaxHeight(),
    ) {
        ThemedTopAppBar(
            title = stringResource(LR.string.settings_title_privacy),
            onNavigationClick = onBackPress,
        )
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = bottomInset + 16.dp),
        ) {
            item {
                Text(
                    text = stringResource(R.string.podhopper_privacy_title),
                    style = MaterialTheme.typography.h6,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.theme.colors.primaryText01,
                )
            }
            item {
                Text(
                    text = stringResource(R.string.podhopper_privacy_updated),
                    style = MaterialTheme.typography.body2,
                    color = MaterialTheme.theme.colors.primaryText02,
                    modifier = Modifier.padding(top = 4.dp, bottom = 16.dp),
                )
            }
            item {
                Text(
                    text = stringResource(R.string.podhopper_privacy_intro),
                    style = MaterialTheme.typography.body1,
                    color = MaterialTheme.theme.colors.primaryText01,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }
            items(sections) { (headingRes, bodyRes) ->
                Column {
                    Text(
                        text = stringResource(headingRes),
                        style = MaterialTheme.typography.subtitle1,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.theme.colors.primaryText01,
                        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
                    )
                    Text(
                        text = stringResource(bodyRes),
                        style = MaterialTheme.typography.body1,
                        color = MaterialTheme.theme.colors.primaryText02,
                    )
                }
            }
        }
    }
}
