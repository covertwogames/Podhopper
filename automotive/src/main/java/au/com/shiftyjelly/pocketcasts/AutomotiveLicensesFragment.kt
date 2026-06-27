package au.com.shiftyjelly.pocketcasts

import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.Fragment
import au.com.shiftyjelly.pocketcasts.compose.AutomotiveTheme
import au.com.shiftyjelly.pocketcasts.compose.components.HorizontalDivider
import au.com.shiftyjelly.pocketcasts.compose.extensions.contentWithoutConsumedInsets
import au.com.shiftyjelly.pocketcasts.compose.theme
import au.com.shiftyjelly.pocketcasts.extensions.openUrl
import com.mikepenz.aboutlibraries.Libs
import com.mikepenz.aboutlibraries.entity.Library
import com.mikepenz.aboutlibraries.ui.compose.LibrariesContainer
import com.mikepenz.aboutlibraries.ui.compose.LibraryDefaults
import com.mikepenz.aboutlibraries.ui.compose.android.produceLibraries
import com.mikepenz.aboutlibraries.ui.compose.libraryColors
import com.mikepenz.aboutlibraries.ui.compose.util.author
import com.mikepenz.aboutlibraries.util.withContext

class AutomotiveLicensesFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ) = contentWithoutConsumedInsets {
        AutomotiveTheme {
            LicensesPage()
        }
    }

    @Composable
    private fun LicensesPage(modifier: Modifier = Modifier) {
        Column(modifier = modifier.fillMaxSize()) {
            OpenSourceAttribution()
            LibrariesContainer(
                showAuthor = true,
                showVersion = false,
                showLicenseBadges = false,
                colors = LibraryDefaults.libraryColors(
                    libraryContentColor = MaterialTheme.theme.colors.primaryText01,
                ),
                libraries = produceLibraries { context ->
                    val libs = Libs.Builder().withContext(context).build()
                    // without displaying the artifact id the libraries seem to appear twice
                    libs.copy(libraries = libs.libraries.distinctBy { "${it.name}##${it.author}" })
                }.value,
                onLibraryClick = { library: Library ->
                    val website = library.website ?: return@LibrariesContainer
                    openUrl(website)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 16.dp),
            )
        }
    }

    @Composable
    private fun OpenSourceAttribution() {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 48.dp, vertical = 24.dp),
        ) {
            Text(
                text = "Built on open source",
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.theme.colors.primaryText01,
            )
            Text(
                text = "PodHopper is based on Pocket Casts by Automattic, used under the Mozilla Public License 2.0.",
                fontSize = 22.sp,
                lineHeight = 32.sp,
                color = MaterialTheme.theme.colors.primaryText02,
                modifier = Modifier.padding(top = 10.dp),
            )
        }
        HorizontalDivider()
    }
}
