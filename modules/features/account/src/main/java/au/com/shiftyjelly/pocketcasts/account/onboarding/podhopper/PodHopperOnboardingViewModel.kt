package au.com.shiftyjelly.pocketcasts.account.onboarding.podhopper

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import au.com.shiftyjelly.pocketcasts.repositories.podhopper.AuthRejectedException
import au.com.shiftyjelly.pocketcasts.repositories.podhopper.SupabaseClient
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Drives the PodHopper first-run login, signup, and password-recovery screens. It is a thin
 * wrapper over the same Supabase client the rest of the app uses, so a session created here is
 * the session sync relies on. Field validation lives in the UI; this view model only performs the
 * network calls and maps the outcome to a state the screens render.
 */
@HiltViewModel
class PodHopperOnboardingViewModel @Inject constructor(
    private val supabaseClient: SupabaseClient,
) : ViewModel() {

    enum class ErrorKind {
        InvalidCredentials,
        LoginFailed,
        SignupFailed,
        RecoverFailed,
        MissingFields,
    }

    sealed interface AuthState {
        data object Idle : AuthState
        data object Busy : AuthState

        // A live session now exists, so the flow can advance to the notifications step.
        data object Authenticated : AuthState

        // Signup succeeded but the project requires email confirmation before a session is issued.
        data object ConfirmEmail : AuthState

        // A password reset email was sent.
        data object RecoverSent : AuthState

        data class Error(val kind: ErrorKind, val detail: String?) : AuthState
    }

    private val _authState = MutableStateFlow<AuthState>(AuthState.Idle)
    val authState: StateFlow<AuthState> = _authState

    fun isSignedIn(): Boolean = supabaseClient.isLoggedIn()

    fun resetState() {
        _authState.value = AuthState.Idle
    }

    fun signIn(email: String, password: String) {
        val trimmedEmail = email.trim()
        if (trimmedEmail.isEmpty() || password.isEmpty()) {
            _authState.value = AuthState.Error(ErrorKind.MissingFields, null)
            return
        }
        _authState.value = AuthState.Busy
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    supabaseClient.login(trimmedEmail, password)
                }
                _authState.value = AuthState.Authenticated
            } catch (e: AuthRejectedException) {
                _authState.value = AuthState.Error(ErrorKind.InvalidCredentials, null)
            } catch (e: Exception) {
                _authState.value = AuthState.Error(ErrorKind.LoginFailed, describe(e))
            }
        }
    }

    fun signUp(email: String, password: String) {
        val trimmedEmail = email.trim()
        if (trimmedEmail.isEmpty() || password.isEmpty()) {
            _authState.value = AuthState.Error(ErrorKind.MissingFields, null)
            return
        }
        _authState.value = AuthState.Busy
        viewModelScope.launch {
            try {
                val sessionActive = withContext(Dispatchers.IO) {
                    supabaseClient.signUp(trimmedEmail, password)
                }
                _authState.value = if (sessionActive) AuthState.Authenticated else AuthState.ConfirmEmail
            } catch (e: Exception) {
                _authState.value = AuthState.Error(ErrorKind.SignupFailed, describe(e))
            }
        }
    }

    fun recoverPassword(email: String) {
        val trimmedEmail = email.trim()
        if (trimmedEmail.isEmpty()) {
            _authState.value = AuthState.Error(ErrorKind.MissingFields, null)
            return
        }
        _authState.value = AuthState.Busy
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    supabaseClient.recoverPassword(trimmedEmail)
                }
                _authState.value = AuthState.RecoverSent
            } catch (e: Exception) {
                _authState.value = AuthState.Error(ErrorKind.RecoverFailed, describe(e))
            }
        }
    }

    private fun describe(e: Throwable): String {
        val builder = StringBuilder()
        var current: Throwable? = e
        var depth = 0
        while (current != null && depth < 4) {
            if (builder.isNotEmpty()) {
                builder.append(" <- ")
            }
            builder.append(current.javaClass.simpleName)
            val message = current.message
            if (message != null) {
                builder.append(": ").append(message)
            }
            current = current.cause
            depth++
        }
        return builder.toString()
    }
}
