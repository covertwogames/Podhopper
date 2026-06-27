package au.com.shiftyjelly.pocketcasts

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.Button
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import au.com.shiftyjelly.pocketcasts.compose.AppTheme
import au.com.shiftyjelly.pocketcasts.compose.extensions.setContentWithViewCompositionStrategy
import au.com.shiftyjelly.pocketcasts.compose.theme
import au.com.shiftyjelly.pocketcasts.databinding.FragmentAutomotiveSettingsBinding
import au.com.shiftyjelly.pocketcasts.preferences.Settings
import au.com.shiftyjelly.pocketcasts.repositories.podhopper.SupabaseClient
import au.com.shiftyjelly.pocketcasts.views.fragments.BaseFragment
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import au.com.shiftyjelly.pocketcasts.localization.R as LR

@AndroidEntryPoint
class AutomotiveSettingsFragment : BaseFragment() {
    @Inject
    lateinit var supabaseClient: SupabaseClient

    @Inject
    lateinit var settings: Settings

    private lateinit var binding: FragmentAutomotiveSettingsBinding

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        binding = FragmentAutomotiveSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.setupUserView()

        // Close settings once signed in, to meet the car sign-in UX (return to the media UI, which
        // now shows the user's podcasts). Only the signed-out to signed-in transition closes the
        // screen; signing out from here leaves it open so the user can sign in again.
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                var wasSignedIn = supabaseClient.isLoggedIn()
                supabaseClient.loginState.collect { signedIn ->
                    if (!wasSignedIn && signedIn) {
                        requireActivity().finish()
                    }
                    wasSignedIn = signedIn
                }
            }
        }
    }

    private fun FragmentAutomotiveSettingsBinding.setupUserView() {
        val accountFlow = combine(
            supabaseClient.loginState,
            settings.podhopperEmail.flow,
        ) { signedIn, email -> CarAccountState(signedIn, email) }
        val initialState = CarAccountState(supabaseClient.isLoggedIn(), settings.podhopperEmail.value)
        userView.setContentWithViewCompositionStrategy {
            val state by accountFlow.collectAsState(initialState)
            AppTheme(theme.activeTheme) {
                CarAccountHeader(
                    state = state,
                    onSignIn = { launchPairing() },
                    onSignOut = { signOut() },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }

    private fun launchPairing() {
        startActivity(Intent(requireContext(), PodHopperCarPairingActivity::class.java))
    }

    private fun signOut() {
        viewLifecycleOwner.lifecycleScope.launch {
            withContext(Dispatchers.IO) { supabaseClient.logout() }
        }
    }
}

private data class CarAccountState(val signedIn: Boolean, val email: String)

@Composable
private fun CarAccountHeader(
    state: CarAccountState,
    onSignIn: () -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top,
        modifier = modifier.padding(horizontal = 32.dp, vertical = 24.dp),
    ) {
        val signedInWithEmail = state.signedIn && state.email.isNotBlank()
        Text(
            text = if (signedInWithEmail) {
                stringResource(LR.string.podhopper_car_account_signed_in, state.email)
            } else {
                stringResource(LR.string.podhopper_car_account_signed_out)
            },
            fontSize = 26.sp,
            color = MaterialTheme.theme.colors.primaryText01,
        )
        Spacer(Modifier.height(20.dp))
        if (state.signedIn) {
            Button(onClick = onSignOut, modifier = Modifier.widthIn(min = 240.dp)) {
                Text(text = stringResource(LR.string.podhopper_car_account_sign_out), fontSize = 22.sp)
            }
        } else {
            Button(onClick = onSignIn, modifier = Modifier.widthIn(min = 240.dp)) {
                Text(text = stringResource(LR.string.podhopper_car_account_sign_in), fontSize = 22.sp)
            }
        }
    }
}
