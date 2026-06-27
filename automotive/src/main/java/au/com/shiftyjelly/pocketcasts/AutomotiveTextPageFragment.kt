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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.Fragment
import au.com.shiftyjelly.pocketcasts.compose.AutomotiveTheme
import au.com.shiftyjelly.pocketcasts.compose.extensions.contentWithoutConsumedInsets
import au.com.shiftyjelly.pocketcasts.compose.theme

/**
 * PodHopper: a simple scrollable read-only page for the car About area (Privacy, Help and
 * Feedback). Takes a heading and a body, so one screen serves both. Reached while parked from the
 * About page, so a plain text layout that scrolls on any screen size is all it needs to be.
 */
class AutomotiveTextPageFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ) = contentWithoutConsumedInsets {
        AutomotiveTheme {
            TextPage(
                heading = arguments?.getString(ARG_HEADING).orEmpty(),
                body = arguments?.getString(ARG_BODY).orEmpty(),
            )
        }
    }

    companion object {
        private const val ARG_HEADING = "heading"
        private const val ARG_BODY = "body"

        fun newInstance(heading: String, body: String): AutomotiveTextPageFragment {
            return AutomotiveTextPageFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_HEADING, heading)
                    putString(ARG_BODY, body)
                }
            }
        }
    }
}

@Composable
private fun TextPage(heading: String, body: String) {
    val scrollState = rememberScrollState()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(scrollState)
            .padding(horizontal = 48.dp, vertical = 40.dp),
    ) {
        Text(
            text = heading,
            fontSize = 34.sp,
            fontWeight = FontWeight(600),
            color = MaterialTheme.theme.colors.primaryText01,
        )
        Text(
            text = body,
            fontSize = 26.sp,
            lineHeight = 38.sp,
            color = MaterialTheme.theme.colors.primaryText02,
            modifier = Modifier.padding(top = 24.dp),
        )
    }
}
