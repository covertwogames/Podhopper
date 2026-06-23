package au.com.shiftyjelly.pocketcasts.search

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.fragment.app.viewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import au.com.shiftyjelly.pocketcasts.analytics.SourceView
import au.com.shiftyjelly.pocketcasts.compose.AppThemeWithBackground
import au.com.shiftyjelly.pocketcasts.compose.extensions.setContentWithViewCompositionStrategy
import au.com.shiftyjelly.pocketcasts.preferences.Settings
import au.com.shiftyjelly.pocketcasts.ui.helper.FragmentHostListener
import au.com.shiftyjelly.pocketcasts.utils.extensions.pxToDp
import au.com.shiftyjelly.pocketcasts.views.fragments.BaseFragment
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch

/**
 * Shows a feed preview (artwork, title, description, episodes) for a podcast that may not be
 * subscribed yet. Subscribing persists the feed and opens the real podcast page. This is the screen
 * both the search results and the Discover grid open on tap.
 */
@AndroidEntryPoint
class FeedPreviewFragment : BaseFragment() {

    @Inject
    lateinit var settings: Settings

    private val viewModel: FeedPreviewViewModel by viewModels()

    private val feedUrl: String
        get() = arguments?.getString(ARG_FEED_URL).orEmpty()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return ComposeView(requireContext()).apply {
            setContentWithViewCompositionStrategy {
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                val bottomInset by settings.bottomInset.collectAsStateWithLifecycle(initialValue = 0)
                AppThemeWithBackground(theme.activeTheme) {
                    FeedPreviewPage(
                        state = state,
                        onSubscribe = viewModel::subscribe,
                        onRetry = viewModel::retry,
                        onBack = { activity?.onBackPressedDispatcher?.onBackPressed() },
                        bottomInset = bottomInset.pxToDp(LocalContext.current).dp,
                    )
                }
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (savedInstanceState == null) {
            viewModel.load(feedUrl)
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.openPodcast.collect { uuid ->
                (activity as? FragmentHostListener)?.openPodcastPage(uuid, SourceView.DISCOVER.key)
            }
        }
    }

    companion object {
        private const val ARG_FEED_URL = "arg_feed_url"

        fun newInstance(feedUrl: String): FeedPreviewFragment {
            return FeedPreviewFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_FEED_URL, feedUrl)
                }
            }
        }
    }
}
