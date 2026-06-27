package au.com.shiftyjelly.pocketcasts

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Button
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModelProvider
import au.com.shiftyjelly.pocketcasts.compose.AutomotiveTheme
import au.com.shiftyjelly.pocketcasts.compose.theme
import au.com.shiftyjelly.pocketcasts.ui.extensions.getThemeDrawable
import au.com.shiftyjelly.pocketcasts.PodHopperCarPairingViewModel.UiState
import dagger.hilt.android.AndroidEntryPoint
import au.com.shiftyjelly.pocketcasts.localization.R as LR
import au.com.shiftyjelly.pocketcasts.ui.R as UR

/**
 * PodHopper: the car (Android Automotive) sign-in screen. Reached while parked, either from the
 * media browser's "Sign In" prompt or from car Settings. Shows a pairing code the phone approves,
 * with a direct email and password fallback. Closes itself once signed in.
 */
@AndroidEntryPoint
class PodHopperCarPairingActivity : AppCompatActivity() {

    private lateinit var viewModel: PodHopperCarPairingViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel = ViewModelProvider(this)[PodHopperCarPairingViewModel::class.java]

        val composeView = ComposeView(this).apply {
            setContent {
                AutomotiveTheme {
                    val state by viewModel.uiState.collectAsState()
                    LaunchedEffect(state) {
                        if (state is UiState.Success) {
                            finish()
                        }
                    }
                    PairingScreen(
                        state = state,
                        onRetry = { viewModel.startPairing() },
                        onShowEmail = { viewModel.showEmailEntry() },
                        onSubmitEmail = { email, password -> viewModel.submitEmail(email, password) },
                        onBackToPairing = { viewModel.backToPairing() },
                    )
                }
            }
        }
        setContentView(composeView)
    }
}

@Composable
private fun PairingScreen(
    state: UiState,
    onRetry: () -> Unit,
    onShowEmail: () -> Unit,
    onSubmitEmail: (String, String) -> Unit,
    onBackToPairing: () -> Unit,
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top,
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 48.dp, vertical = 40.dp),
    ) {
        Image(
            painter = painterResource(context.getThemeDrawable(UR.attr.logo_title_vertical)),
            contentDescription = stringResource(LR.string.podhopper_car_pairing_title),
            modifier = Modifier.size(width = 200.dp, height = 120.dp),
        )
        Spacer(Modifier.height(32.dp))

        when (state) {
            is UiState.Starting -> {
                CircularProgressIndicator(color = MaterialTheme.theme.colors.primaryInteractive01)
            }

            is UiState.ShowingCode -> {
                CodeContent(code = state.code, onShowEmail = onShowEmail)
            }

            is UiState.Expired -> {
                MessageContent(
                    message = stringResource(LR.string.podhopper_car_pairing_expired),
                    primaryLabel = stringResource(LR.string.podhopper_car_pairing_retry),
                    onPrimary = onRetry,
                    onShowEmail = onShowEmail,
                )
            }

            is UiState.NetworkError -> {
                MessageContent(
                    message = stringResource(LR.string.podhopper_car_pairing_error),
                    primaryLabel = stringResource(LR.string.podhopper_car_pairing_retry),
                    onPrimary = onRetry,
                    onShowEmail = onShowEmail,
                )
            }

            is UiState.EmailEntry -> {
                EmailContent(
                    isError = false,
                    isSubmitting = false,
                    onSubmit = onSubmitEmail,
                    onBack = onBackToPairing,
                )
            }

            is UiState.EmailSubmitting -> {
                EmailContent(
                    isError = false,
                    isSubmitting = true,
                    onSubmit = onSubmitEmail,
                    onBack = onBackToPairing,
                )
            }

            is UiState.EmailError -> {
                EmailContent(
                    isError = true,
                    isSubmitting = false,
                    onSubmit = onSubmitEmail,
                    onBack = onBackToPairing,
                )
            }

            is UiState.Success -> {
                Text(
                    text = stringResource(LR.string.podhopper_car_pairing_success),
                    fontSize = 28.sp,
                    color = MaterialTheme.theme.colors.primaryText01,
                )
            }
        }
    }
}

@Composable
private fun CodeContent(code: String, onShowEmail: () -> Unit) {
    Text(
        text = stringResource(LR.string.podhopper_car_pairing_instructions),
        fontSize = 26.sp,
        color = MaterialTheme.theme.colors.primaryText01,
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(32.dp))
    Text(
        text = stringResource(LR.string.podhopper_car_pairing_code_label),
        fontSize = 22.sp,
        color = MaterialTheme.theme.colors.primaryText02,
    )
    Spacer(Modifier.height(8.dp))
    Text(
        text = code,
        fontSize = 64.sp,
        fontWeight = FontWeight(700),
        letterSpacing = 12.sp,
        color = MaterialTheme.theme.colors.primaryInteractive01,
    )
    Spacer(Modifier.height(24.dp))
    Text(
        text = stringResource(LR.string.podhopper_car_pairing_waiting),
        fontSize = 22.sp,
        color = MaterialTheme.theme.colors.primaryText02,
    )
    Spacer(Modifier.height(40.dp))
    Text(
        text = stringResource(LR.string.podhopper_car_pairing_email_prompt),
        fontSize = 20.sp,
        color = MaterialTheme.theme.colors.primaryText02,
    )
    TextButton(onClick = onShowEmail) {
        Text(
            text = stringResource(LR.string.podhopper_car_pairing_email_button),
            fontSize = 24.sp,
            color = MaterialTheme.theme.colors.primaryInteractive01,
        )
    }
}

@Composable
private fun MessageContent(
    message: String,
    primaryLabel: String,
    onPrimary: () -> Unit,
    onShowEmail: () -> Unit,
) {
    Text(
        text = message,
        fontSize = 26.sp,
        color = MaterialTheme.theme.colors.primaryText01,
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(32.dp))
    Button(
        onClick = onPrimary,
        modifier = Modifier.widthIn(min = 280.dp),
    ) {
        Text(text = primaryLabel, fontSize = 24.sp)
    }
    Spacer(Modifier.height(16.dp))
    TextButton(onClick = onShowEmail) {
        Text(
            text = stringResource(LR.string.podhopper_car_pairing_email_button),
            fontSize = 22.sp,
            color = MaterialTheme.theme.colors.primaryInteractive01,
        )
    }
}

@Composable
private fun EmailContent(
    isError: Boolean,
    isSubmitting: Boolean,
    onSubmit: (String, String) -> Unit,
    onBack: () -> Unit,
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    Text(
        text = stringResource(LR.string.podhopper_car_email_title),
        fontSize = 28.sp,
        fontWeight = FontWeight(600),
        color = MaterialTheme.theme.colors.primaryText01,
    )
    Spacer(Modifier.height(24.dp))
    OutlinedTextField(
        value = email,
        onValueChange = { email = it },
        singleLine = true,
        enabled = !isSubmitting,
        label = { Text(stringResource(LR.string.podhopper_car_email_address_hint)) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(16.dp))
    OutlinedTextField(
        value = password,
        onValueChange = { password = it },
        singleLine = true,
        enabled = !isSubmitting,
        label = { Text(stringResource(LR.string.podhopper_car_email_password_hint)) },
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        modifier = Modifier.fillMaxWidth(),
    )
    if (isError) {
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(LR.string.podhopper_car_email_error),
            fontSize = 20.sp,
            color = MaterialTheme.theme.colors.support05,
            modifier = Modifier.fillMaxWidth(),
        )
    }
    Spacer(Modifier.height(24.dp))
    if (isSubmitting) {
        CircularProgressIndicator(color = MaterialTheme.theme.colors.primaryInteractive01)
    } else {
        Button(
            onClick = { onSubmit(email, password) },
            enabled = email.isNotBlank() && password.isNotBlank(),
            modifier = Modifier.widthIn(min = 280.dp),
        ) {
            Text(text = stringResource(LR.string.podhopper_car_email_submit), fontSize = 24.sp)
        }
        Spacer(Modifier.height(16.dp))
        TextButton(onClick = onBack) {
            Text(
                text = stringResource(LR.string.podhopper_car_email_back),
                fontSize = 22.sp,
                color = MaterialTheme.theme.colors.primaryInteractive01,
            )
        }
    }
}
