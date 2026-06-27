package au.com.shiftyjelly.pocketcasts.search

import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
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
 * The Discover tab. In landing mode it shows a search bar, a suggestions grid from the iTunes top
 * list, a Discover more link to the full grid, and an add by RSS url row. In full grid mode it
 * shows the larger grid on its own screen. Tapping a tile resolves its real feed url, adds it as a
 * not subscribed podcast, and opens the real podcast page; the search bar opens the existing remote
 * search.
 */
@AndroidEntryPoint
class AddPodcastFragment : BaseFragment() {

    @Inject
    lateinit var settings: Settings

    private val viewModel: AddPodcastViewModel by viewModels()

    private val mode: AddPodcastMode
        get() = if (arguments?.getBoolean(ARG_FULL_GRID) == true) AddPodcastMode.FULL_GRID else AddPodcastMode.LANDING

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return ComposeView(requireContext()).apply {
            setContentWithViewCompositionStrategy {
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                val bottomInset by settings.bottomInset.collectAsStateWithLifecycle(initialValue = 0)
                AppThemeWithBackground(theme.activeTheme) {
                    AddPodcastPage(
                        mode = mode,
                        state = state,
                        onSearchBarClick = ::openSearch,
                        onTileClick = viewModel::onTileClick,
                        onDiscoverMoreClick = ::openFullGrid,
                        onAddByUrlClick = ::showAddByUrlDialog,
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
            val limit = if (mode == AddPodcastMode.FULL_GRID) {
                AddPodcastViewModel.FULL_LIMIT
            } else {
                AddPodcastViewModel.SUGGESTIONS_LIMIT
            }
            viewModel.load(limit)
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.openPodcast.collect { uuid ->
                (activity as? FragmentHostListener)?.openPodcastPage(uuid, SourceView.DISCOVER.key)
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.resolveFailed.collect {
                Toast.makeText(requireContext(), "Could not open this podcast.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun openSearch() {
        val host = activity as? FragmentHostListener ?: return
        host.addFragment(
            SearchFragment.newInstance(
                floating = true,
                onlySearchRemote = true,
                source = SourceView.DISCOVER,
            ),
            onTop = true,
        )
    }

    private fun openFullGrid() {
        val host = activity as? FragmentHostListener ?: return
        host.addFragment(newInstance(fullGrid = true), onTop = true)
    }

    private fun showAddByUrlDialog() {
        val context = requireContext()
        val input = EditText(context).apply {
            inputType = InputType.TYPE_TEXT_VARIATION_URI
            hint = "https://example.com/feed.xml"
            setPadding(48, 24, 48, 24)
        }
        AlertDialog.Builder(context)
            .setTitle("Add podcast by RSS address")
            .setView(input)
            .setPositiveButton("Add") { _, _ ->
                val url = input.text.toString().trim()
                if (url.startsWith("http://", ignoreCase = true) || url.startsWith("https://", ignoreCase = true)) {
                    openByUrl(url)
                } else {
                    Toast.makeText(context, "Enter a feed url starting with http or https.", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun openByUrl(url: String) {
        val context = requireContext()
        val progress = AlertDialog.Builder(context)
            .setView(
                ProgressBar(context).apply {
                    isIndeterminate = true
                    setPadding(48, 48, 48, 48)
                },
            )
            .setCancelable(false)
            .create()
        progress.show()
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val uuid = viewModel.addByUrl(url)
                if (uuid != null) {
                    (activity as? FragmentHostListener)?.openPodcastPage(uuid, SourceView.DISCOVER.key)
                } else {
                    Toast.makeText(context, "Could not open this podcast.", Toast.LENGTH_SHORT).show()
                }
            } finally {
                progress.dismiss()
            }
        }
    }

    companion object {
        private const val ARG_FULL_GRID = "arg_full_grid"

        fun newInstance(fullGrid: Boolean = false): AddPodcastFragment {
            return AddPodcastFragment().apply {
                arguments = Bundle().apply {
                    putBoolean(ARG_FULL_GRID, fullGrid)
                }
            }
        }
    }
}
