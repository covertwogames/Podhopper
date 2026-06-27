package au.com.shiftyjelly.pocketcasts

import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import au.com.shiftyjelly.pocketcasts.repositories.podhopper.SupabaseClient
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * PodHopper: drives the car (Android Automotive) sign-in screen. There are two ways in: a pairing
 * code that the phone approves, and a direct email and password fallback. Both end the same way, by
 * storing a PodHopper refresh token, which flips the app to signed-in and starts sync automatically.
 */
@HiltViewModel
class PodHopperCarPairingViewModel @Inject constructor(
    private val supabaseClient: SupabaseClient,
) : ViewModel() {

    sealed interface UiState {
        data object Starting : UiState
        data class ShowingCode(val code: String) : UiState
        data object Expired : UiState
        data object NetworkError : UiState
        data object EmailEntry : UiState
        data object EmailSubmitting : UiState
        data object EmailError : UiState
        data object Success : UiState
    }

    private val _uiState = MutableStateFlow<UiState>(UiState.Starting)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private var pollJob: Job? = null

    init {
        startPairing()
    }

    /** Requests a fresh pairing code and begins polling for the phone's approval. */
    fun startPairing() {
        pollJob?.cancel()
        _uiState.value = UiState.Starting
        pollJob = viewModelScope.launch {
            val code = try {
                withContext(Dispatchers.IO) { supabaseClient.startPairing(deviceName()) }
            } catch (e: Exception) {
                Timber.e(e, "PodHopper car pairing: could not start")
                _uiState.value = UiState.NetworkError
                return@launch
            }
            _uiState.value = UiState.ShowingCode(code)

            var consecutiveFailures = 0
            while (isActive) {
                delay(POLL_INTERVAL_MS)
                val tokenHash = try {
                    val result = withContext(Dispatchers.IO) { supabaseClient.pollPairing(code) }
                    consecutiveFailures = 0
                    result
                } catch (e: Exception) {
                    if (e.message?.contains("expired", ignoreCase = true) == true) {
                        Timber.i("PodHopper car pairing: code expired")
                        _uiState.value = UiState.Expired
                        return@launch
                    }
                    Timber.w(e, "PodHopper car pairing: poll failed")
                    consecutiveFailures++
                    if (consecutiveFailures >= MAX_POLL_FAILURES) {
                        _uiState.value = UiState.NetworkError
                        return@launch
                    }
                    null
                }
                if (tokenHash != null) {
                    val claimed = try {
                        withContext(Dispatchers.IO) { supabaseClient.claimPairingSession(tokenHash) }
                        true
                    } catch (e: Exception) {
                        Timber.e(e, "PodHopper car pairing: claim failed")
                        _uiState.value = UiState.NetworkError
                        false
                    }
                    if (claimed) {
                        _uiState.value = UiState.Success
                    }
                    return@launch
                }
            }
        }
    }

    /** Switches to the email and password fallback. */
    fun showEmailEntry() {
        pollJob?.cancel()
        _uiState.value = UiState.EmailEntry
    }

    /** Returns from the email fallback to a fresh pairing code. */
    fun backToPairing() {
        startPairing()
    }

    /** Signs in directly with an email and password (parked only, by nature of the car screen). */
    fun submitEmail(email: String, password: String) {
        pollJob?.cancel()
        _uiState.value = UiState.EmailSubmitting
        viewModelScope.launch {
            val ok = try {
                withContext(Dispatchers.IO) { supabaseClient.login(email.trim(), password) }
                supabaseClient.isLoggedIn()
            } catch (e: Exception) {
                Timber.e(e, "PodHopper car pairing: email sign-in failed")
                false
            }
            _uiState.value = if (ok) UiState.Success else UiState.EmailError
        }
    }

    private fun deviceName(): String {
        val manufacturer = Build.MANUFACTURER.orEmpty().trim()
        val model = Build.MODEL.orEmpty().trim()
        val combined = listOf(manufacturer, model).filter { it.isNotEmpty() }.joinToString(" ")
        return combined.ifBlank { "Car" }
    }

    override fun onCleared() {
        super.onCleared()
        pollJob?.cancel()
    }

    private companion object {
        const val POLL_INTERVAL_MS = 3000L
        const val MAX_POLL_FAILURES = 4
    }
}
