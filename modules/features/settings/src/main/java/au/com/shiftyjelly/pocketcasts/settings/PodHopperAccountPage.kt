package au.com.shiftyjelly.pocketcasts.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import au.com.shiftyjelly.pocketcasts.compose.bars.ThemedTopAppBar
import au.com.shiftyjelly.pocketcasts.compose.buttons.RowButton
import au.com.shiftyjelly.pocketcasts.compose.components.EmailAndPasswordFields
import au.com.shiftyjelly.pocketcasts.compose.components.TextH30
import au.com.shiftyjelly.pocketcasts.compose.components.TextP40
import au.com.shiftyjelly.pocketcasts.compose.theme
import au.com.shiftyjelly.pocketcasts.settings.viewmodel.PodHopperAccountViewModel
import au.com.shiftyjelly.pocketcasts.localization.R as LR

@Composable
fun PodHopperAccountPage(
    viewModel: PodHopperAccountViewModel,
    onBackPress: () -> Unit,
    bottomInset: Dp,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.theme.colors.primaryUi02),
    ) {
        ThemedTopAppBar(
            title = stringResource(LR.string.podhopper_account),
            bottomShadow = true,
            onNavigationClick = onBackPress,
        )
        Column(
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = bottomInset + 16.dp),
        ) {
            when (val currentState = state) {
                is PodHopperAccountViewModel.State.SignedIn -> {
                    SignedInContent(
                        email = currentState.email,
                        onSignOut = viewModel::signOut,
                    )
                }
                else -> {
                    SignInContent(
                        isSigningIn = currentState is PodHopperAccountViewModel.State.SigningIn,
                        errorMessage = (currentState as? PodHopperAccountViewModel.State.Error)?.message,
                        onSignIn = viewModel::signIn,
                    )
                }
            }
        }
    }
}

@Composable
private fun SignInContent(
    isSigningIn: Boolean,
    errorMessage: String?,
    onSignIn: (String, String) -> Unit,
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    Column {
        EmailAndPasswordFields(
            email = email,
            password = password,
            enabled = !isSigningIn,
            isCreatingAccount = false,
            showEmailError = false,
            showPasswordError = false,
            onConfirm = { onSignIn(email, password) },
            onUpdateEmail = { email = it },
            onUpdatePassword = { password = it },
        )
        if (errorMessage != null) {
            Spacer(modifier = Modifier.height(12.dp))
            TextP40(
                text = errorMessage,
                color = MaterialTheme.theme.colors.support05,
            )
        }
        Spacer(modifier = Modifier.height(24.dp))
        RowButton(
            text = stringResource(LR.string.podhopper_sign_in),
            enabled = !isSigningIn,
            onClick = { onSignIn(email, password) },
        )
    }
}

@Composable
private fun SignedInContent(
    email: String,
    onSignOut: () -> Unit,
) {
    Column {
        TextH30(text = stringResource(LR.string.podhopper_signed_in_as))
        Spacer(modifier = Modifier.height(4.dp))
        TextP40(
            text = email,
            color = MaterialTheme.theme.colors.primaryText02,
        )
        Spacer(modifier = Modifier.height(24.dp))
        RowButton(
            text = stringResource(LR.string.podhopper_sign_out),
            onClick = onSignOut,
        )
    }
}
