package au.com.shiftyjelly.pocketcasts.settings.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import au.com.shiftyjelly.pocketcasts.preferences.Settings
import au.com.shiftyjelly.pocketcasts.repositories.podhopper.SupabaseClient
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@HiltViewModel
class PodHopperAccountViewModel @Inject constructor(
    private val supabaseClient: SupabaseClient,
    private val settings: Settings,
) : ViewModel() {

    sealed class State {
        object SignedOut : State()
        object SigningIn : State()
        data class SignedIn(val email: String) : State()
        data class Error(val message: String) : State()
    }

    private val _state = MutableStateFlow(initialState())
    val state: StateFlow<State> = _state

    private fun initialState(): State {
        return if (supabaseClient.isLoggedIn()) {
            State.SignedIn(settings.podhopperEmail.value)
        } else {
            State.SignedOut
        }
    }

    fun signIn(email: String, password: String) {
        val trimmedEmail = email.trim()
        if (trimmedEmail.isEmpty() || password.isEmpty()) {
            return
        }
        _state.value = State.SigningIn
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    supabaseClient.login(trimmedEmail, password)
                }
                _state.value = State.SignedIn(trimmedEmail)
            } catch (e: Exception) {
                _state.value = State.Error(e.message ?: "Sign in failed")
            }
        }
    }

    fun signOut() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                supabaseClient.logout()
            }
            _state.value = State.SignedOut
        }
    }
}
