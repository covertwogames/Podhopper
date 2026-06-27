package au.com.shiftyjelly.pocketcasts

import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.Fragment
import au.com.shiftyjelly.pocketcasts.compose.AutomotiveTheme
import au.com.shiftyjelly.pocketcasts.compose.extensions.contentWithoutConsumedInsets
import au.com.shiftyjelly.pocketcasts.compose.theme
import au.com.shiftyjelly.pocketcasts.settings.R as SR

/**
 * PodHopper: the car Privacy Policy screen. It renders the exact same policy strings the phone
 * uses (modules/features/settings podhopper_privacy_*), so the two can never drift apart, laid out
 * for the car with larger type and the same section order as the phone.
 */
class AutomotivePrivacyFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ) = contentWithoutConsumedInsets {
        AutomotiveTheme {
            PrivacyContent()
        }
    }
}

@Composable
private fun PrivacyContent() {
    val sections = listOf(
        SR.string.podhopper_privacy_short_heading to SR.string.podhopper_privacy_short_body,
        SR.string.podhopper_privacy_local_heading to SR.string.podhopper_privacy_local_body,
        SR.string.podhopper_privacy_sync_heading to SR.string.podhopper_privacy_sync_body,
        SR.string.podhopper_privacy_dont_heading to SR.string.podhopper_privacy_dont_body,
        SR.string.podhopper_privacy_security_heading to SR.string.podhopper_privacy_security_body,
        SR.string.podhopper_privacy_children_heading to SR.string.podhopper_privacy_children_body,
        SR.string.podhopper_privacy_changes_heading to SR.string.podhopper_privacy_changes_body,
        SR.string.podhopper_privacy_contact_heading to SR.string.podhopper_privacy_contact_body,
    )
    val scrollState = rememberScrollState()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(scrollState)
            .padding(horizontal = 48.dp, vertical = 40.dp),
    ) {
        Text(
            text = stringResource(SR.string.podhopper_privacy_title),
            fontSize = 34.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.theme.colors.primaryText01,
        )
        Text(
            text = stringResource(SR.string.podhopper_privacy_updated),
            fontSize = 20.sp,
            color = MaterialTheme.theme.colors.primaryText02,
            modifier = Modifier.padding(top = 6.dp),
        )
        Text(
            text = stringResource(SR.string.podhopper_privacy_intro),
            fontSize = 24.sp,
            lineHeight = 34.sp,
            color = MaterialTheme.theme.colors.primaryText01,
            modifier = Modifier.padding(top = 20.dp),
        )
        sections.forEach { (headingRes, bodyRes) ->
            Text(
                text = stringResource(headingRes),
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.theme.colors.primaryText01,
                modifier = Modifier.padding(top = 28.dp, bottom = 6.dp),
            )
            Text(
                text = stringResource(bodyRes),
                fontSize = 24.sp,
                lineHeight = 34.sp,
                color = MaterialTheme.theme.colors.primaryText02,
            )
        }
    }
}
