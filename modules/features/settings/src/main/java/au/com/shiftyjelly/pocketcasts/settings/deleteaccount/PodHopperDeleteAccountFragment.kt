package au.com.shiftyjelly.pocketcasts.settings.deleteaccount

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewModelScope
import au.com.shiftyjelly.pocketcasts.compose.AppThemeWithBackground
import au.com.shiftyjelly.pocketcasts.compose.bars.ThemedTopAppBar
import au.com.shiftyjelly.pocketcasts.compose.buttons.RowButton
import au.com.shiftyjelly.pocketcasts.compose.extensions.contentWithoutConsumedInsets
import au.com.shiftyjelly.pocketcasts.compose.theme
import au.com.shiftyjelly.pocketcasts.preferences.Settings
import au.com.shiftyjelly.pocketcasts.repositories.podhopper.PodHopperPositionSync
import au.com.shiftyjelly.pocketcasts.repositories.podhopper.PodHopperSubscriptionSync
import au.com.shiftyjelly.pocketcasts.repositories.podhopper.SupabaseClient
import au.com.shiftyjelly.pocketcasts.settings.R
import au.com.shiftyjelly.pocketcasts.ui.helper.FragmentHostListener
import au.com.shiftyjelly.pocketcasts.utils.extensions.pxToDp
import au.com.shiftyjelly.pocketcasts.views.fragments.BaseFragment
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import au.com.shiftyjelly.pocketcasts.views.R as VR

@HiltViewModel
class PodHopperDeleteAccountViewModel @Inject constructor(
    private val supabaseClient: SupabaseClient,
    private val positionSync: PodHopperPositionSync,
    private val subscriptionSync: PodHopperSubscriptionSync,
) : ViewModel() {

    enum class DeleteState { Idle, Deleting, Error }

    private val _deleteState = MutableStateFlow(DeleteState.Idle)
    val deleteState: StateFlow<DeleteState> = _deleteState.asStateFlow()

    private val _accountDeleted = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val accountDeleted: SharedFlow<Unit> = _accountDeleted.asSharedFlow()

    fun deleteAccount() {
        if (_deleteState.value == DeleteState.Deleting) {
            return
        }
        _deleteState.value = DeleteState.Deleting
        viewModelScope.launch {
            val deleted = withContext(Dispatchers.IO) {
                try {
                    // Only the server account is deleted here. The signed-in check keeps this
                    // graceful if there is no account to delete.
                    if (supabaseClient.isLoggedIn()) {
                        supabaseClient.deleteAccount()
                    }
                    // Sign out and forget this device's sync bookkeeping so a future account starts
                    // clean. No on-device podcast, episode, or download data is touched.
                    supabaseClient.logout()
                    positionSync.clearLocalSyncState()
                    subscriptionSync.clearLocalSyncState()
                    true
                } catch (e: Exception) {
                    false
                }
            }
            if (deleted) {
                _accountDeleted.tryEmit(Unit)
            } else {
                _deleteState.value = DeleteState.Error
            }
        }
    }
}

@AndroidEntryPoint
class PodHopperDeleteAccountFragment : BaseFragment() {

    @Inject
    lateinit var settings: Settings

    private val viewModel: PodHopperDeleteAccountViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ) = contentWithoutConsumedInsets {
        val bottomInset = settings.bottomInset.collectAsStateWithLifecycle(initialValue = 0)
        AppThemeWithBackground(theme.activeTheme) {
            PodHopperDeleteAccountScreen(
                viewModel = viewModel,
                bottomInset = bottomInset.value.pxToDp(LocalContext.current).dp,
                onBackPress = {
                    activity?.onBackPressedDispatcher?.onBackPressed()
                },
            )
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.accountDeleted.collect {
                val host = activity as? FragmentHostListener
                host?.closeToRoot()
                host?.openTab(VR.id.navigation_podcasts)
            }
        }
    }
}

@Composable
private fun PodHopperDeleteAccountScreen(
    viewModel: PodHopperDeleteAccountViewModel,
    bottomInset: Dp,
    onBackPress: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val deleteState by viewModel.deleteState.collectAsStateWithLifecycle()
    val deleting = deleteState == PodHopperDeleteAccountViewModel.DeleteState.Deleting

    Column(
        modifier = modifier
            .background(MaterialTheme.theme.colors.primaryUi02)
            .fillMaxHeight(),
    ) {
        ThemedTopAppBar(
            title = stringResource(R.string.podhopper_delete_account_title),
            onNavigationClick = onBackPress,
        )
        Column(
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = bottomInset + 16.dp),
        ) {
            Text(
                text = stringResource(R.string.podhopper_delete_account_warning_heading),
                style = MaterialTheme.typography.h6,
                color = MaterialTheme.theme.colors.support05,
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.podhopper_delete_account_warning_body),
                style = MaterialTheme.typography.body1,
                color = MaterialTheme.theme.colors.primaryText01,
            )
            Spacer(modifier = Modifier.height(28.dp))
            RowButton(
                text = stringResource(R.string.podhopper_delete_account_confirm),
                enabled = !deleting,
                colors = ButtonDefaults.buttonColors(backgroundColor = MaterialTheme.theme.colors.support05),
                onClick = { viewModel.deleteAccount() },
            )
            RowButton(
                text = stringResource(R.string.podhopper_delete_account_cancel),
                enabled = !deleting,
                onClick = onBackPress,
            )
            if (deleting) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.podhopper_delete_account_deleting),
                    style = MaterialTheme.typography.body2,
                    color = MaterialTheme.theme.colors.primaryText01,
                )
            }
            if (deleteState == PodHopperDeleteAccountViewModel.DeleteState.Error) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.podhopper_delete_account_error),
                    style = MaterialTheme.typography.body2,
                    color = MaterialTheme.theme.colors.support05,
                )
            }
        }
    }
}
