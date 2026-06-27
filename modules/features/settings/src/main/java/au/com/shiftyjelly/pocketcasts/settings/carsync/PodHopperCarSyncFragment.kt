package au.com.shiftyjelly.pocketcasts.settings.carsync

import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import au.com.shiftyjelly.pocketcasts.compose.AppThemeWithBackground
import au.com.shiftyjelly.pocketcasts.compose.bars.ThemedTopAppBar
import au.com.shiftyjelly.pocketcasts.compose.buttons.RowButton
import au.com.shiftyjelly.pocketcasts.compose.extensions.contentWithoutConsumedInsets
import au.com.shiftyjelly.pocketcasts.compose.theme
import au.com.shiftyjelly.pocketcasts.preferences.Settings
import au.com.shiftyjelly.pocketcasts.repositories.podhopper.SupabaseClient
import au.com.shiftyjelly.pocketcasts.settings.R
import au.com.shiftyjelly.pocketcasts.utils.extensions.pxToDp
import au.com.shiftyjelly.pocketcasts.views.fragments.BaseFragment
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@HiltViewModel
class PodHopperCarSyncViewModel @Inject constructor(
    private val supabaseClient: SupabaseClient,
) : ViewModel() {

    val isLoggedIn: StateFlow<Boolean> = supabaseClient.loginState
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), supabaseClient.isLoggedIn())

    private val _pairingState = MutableStateFlow(PairingState.Idle)
    val pairingState: StateFlow<PairingState> = _pairingState.asStateFlow()

    fun pair(rawCode: String) {
        val code = rawCode.trim().uppercase()
        if (code.isEmpty()) {
            _pairingState.value = PairingState.Error
            return
        }
        _pairingState.value = PairingState.Submitting
        viewModelScope.launch {
            val success = withContext(Dispatchers.IO) {
                try {
                    supabaseClient.approveCarPairing(code)
                    true
                } catch (e: Exception) {
                    false
                }
            }
            _pairingState.value = if (success) PairingState.Success else PairingState.Error
        }
    }

    enum class PairingState {
        Idle,
        Submitting,
        Success,
        Error,
    }
}

@AndroidEntryPoint
class PodHopperCarSyncFragment : BaseFragment() {

    @Inject
    lateinit var settings: Settings

    private val viewModel: PodHopperCarSyncViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ) = contentWithoutConsumedInsets {
        val bottomInset = settings.bottomInset.collectAsStateWithLifecycle(initialValue = 0)
        AppThemeWithBackground(theme.activeTheme) {
            PodHopperCarSyncScreen(
                viewModel = viewModel,
                bottomInset = bottomInset.value.pxToDp(LocalContext.current).dp,
                onBackPress = {
                    activity?.onBackPressedDispatcher?.onBackPressed()
                },
            )
        }
    }
}

@Composable
private fun PodHopperCarSyncScreen(
    viewModel: PodHopperCarSyncViewModel,
    bottomInset: Dp,
    onBackPress: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isLoggedIn by viewModel.isLoggedIn.collectAsStateWithLifecycle()
    val pairingState by viewModel.pairingState.collectAsStateWithLifecycle()
    var code by remember { mutableStateOf("") }

    Column(
        modifier = modifier
            .background(MaterialTheme.theme.colors.primaryUi02)
            .fillMaxHeight(),
    ) {
        ThemedTopAppBar(
            title = stringResource(R.string.podhopper_car_sync_title),
            onNavigationClick = onBackPress,
        )
        Column(
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = bottomInset + 16.dp),
        ) {
            if (!isLoggedIn) {
                Text(
                    text = stringResource(R.string.podhopper_car_sync_logged_out),
                    style = MaterialTheme.typography.body1,
                    color = MaterialTheme.theme.colors.primaryText01,
                )
            } else {
                Text(
                    text = stringResource(R.string.podhopper_car_sync_instructions),
                    style = MaterialTheme.typography.body1,
                    color = MaterialTheme.theme.colors.primaryText01,
                )
                Spacer(modifier = Modifier.height(20.dp))
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it.uppercase() },
                    label = { Text(stringResource(R.string.podhopper_car_sync_code_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(20.dp))
                RowButton(
                    text = stringResource(R.string.podhopper_car_sync_button),
                    enabled = pairingState != PodHopperCarSyncViewModel.PairingState.Submitting,
                    onClick = { viewModel.pair(code) },
                )
                val statusText = when (pairingState) {
                    PodHopperCarSyncViewModel.PairingState.Submitting -> stringResource(R.string.podhopper_car_sync_submitting)
                    PodHopperCarSyncViewModel.PairingState.Success -> stringResource(R.string.podhopper_car_sync_success)
                    PodHopperCarSyncViewModel.PairingState.Error -> stringResource(R.string.podhopper_car_sync_error)
                    PodHopperCarSyncViewModel.PairingState.Idle -> null
                }
                if (statusText != null) {
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.body2,
                        color = MaterialTheme.theme.colors.primaryText02,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
            }
        }
    }
}
