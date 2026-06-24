package au.com.shiftyjelly.pocketcasts.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.AlertDialog
import androidx.compose.material.MaterialTheme
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import au.com.shiftyjelly.pocketcasts.compose.buttons.RowButton
import au.com.shiftyjelly.pocketcasts.compose.components.TextH50
import au.com.shiftyjelly.pocketcasts.compose.components.TextP40
import au.com.shiftyjelly.pocketcasts.compose.theme
import au.com.shiftyjelly.pocketcasts.localization.R as LR

/**
 * The PodHopper account section at the top of the Profile tab. Logged out, it shows a single
 * "Login / Create Account" button that opens the PodHopper login flow. Logged in, it shows the
 * account email and a "Logout" button that asks for confirmation before clearing the session.
 */
data class PodHopperAccountState(
    val isSignedIn: Boolean,
    val email: String?,
)

@Composable
fun PodHopperProfileHeader(
    account: PodHopperAccountState,
    onLoginClick: () -> Unit,
    onLogoutClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showLogoutConfirm by remember { mutableStateOf(false) }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (account.isSignedIn) {
            TextP40(
                text = stringResource(LR.string.podhopper_profile_logged_in_as),
                color = MaterialTheme.theme.colors.primaryText02,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            TextH50(
                text = account.email.orEmpty(),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            RowButton(
                text = stringResource(LR.string.podhopper_profile_logout),
                onClick = { showLogoutConfirm = true },
                includePadding = false,
            )
        } else {
            RowButton(
                text = stringResource(LR.string.podhopper_profile_login),
                onClick = onLoginClick,
                includePadding = false,
            )
        }
    }

    if (showLogoutConfirm) {
        AlertDialog(
            onDismissRequest = { showLogoutConfirm = false },
            text = {
                TextP40(text = stringResource(LR.string.podhopper_logout_confirm_message))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showLogoutConfirm = false
                        onLogoutClick()
                    },
                ) {
                    TextP40(
                        text = stringResource(LR.string.podhopper_logout_confirm_yes),
                        color = MaterialTheme.theme.colors.primaryInteractive01,
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showLogoutConfirm = false },
                ) {
                    TextP40(
                        text = stringResource(LR.string.podhopper_logout_confirm_no),
                        color = MaterialTheme.theme.colors.primaryInteractive01,
                    )
                }
            },
            backgroundColor = MaterialTheme.theme.colors.primaryUi01,
        )
    }
}
