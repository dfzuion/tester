package uk.co.rodrunners.raffles.ui.screens.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import uk.co.rodrunners.raffles.core.AppError
import uk.co.rodrunners.raffles.core.Errors
import uk.co.rodrunners.raffles.data.model.Address
import uk.co.rodrunners.raffles.data.model.AppNotification
import uk.co.rodrunners.raffles.data.model.UserProfile
import uk.co.rodrunners.raffles.data.repository.AccountRepository
import uk.co.rodrunners.raffles.data.repository.AuthRepository

data class AccountState(
    val profile: UserProfile? = null,
    val notifications: List<AppNotification> = emptyList(),
    val isAdmin: Boolean = false,
    val emailVerified: Boolean = true,
    val busy: Boolean = false,
    val message: String? = null,
    val error: AppError? = null,
) {
    val unreadCount: Int get() = notifications.count { !it.read }
}

@HiltViewModel
class AccountViewModel @Inject constructor(
    private val accounts: AccountRepository,
    private val auth: AuthRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(AccountState())
    val state: StateFlow<AccountState> = _state.asStateFlow()

    init {
        observeProfile()
        observeNotifications()
        observeClaims()
    }

    private fun observeProfile() {
        val uid = auth.currentUid ?: return
        viewModelScope.launch {
            accounts.profile(uid).collect { profile ->
                _state.value = _state.value.copy(profile = profile)
            }
        }
    }

    private fun observeNotifications() {
        val uid = auth.currentUid ?: return
        viewModelScope.launch {
            accounts.notifications(uid).collect { list ->
                _state.value = _state.value.copy(notifications = list)
            }
        }
    }

    private fun observeClaims() {
        viewModelScope.launch {
            auth.authState().collect { s ->
                if (s is uk.co.rodrunners.raffles.data.repository.AuthState.SignedIn) {
                    _state.value = _state.value.copy(isAdmin = s.isAdmin, emailVerified = s.emailVerified)
                }
            }
        }
    }

    fun updateDetails(displayName: String, phone: String) {
        val uid = auth.currentUid ?: return
        act { accounts.updateDetails(uid, displayName, phone); "Details saved." }
    }

    fun updateAddress(address: Address) {
        val uid = auth.currentUid ?: return
        act { accounts.updateAddress(uid, address); "Address saved." }
    }

    fun setNotificationPreference(category: String, enabled: Boolean) {
        val uid = auth.currentUid ?: return
        act { accounts.setNotificationPreference(uid, category, enabled); null }
    }

    fun markRead(notificationId: String) {
        val uid = auth.currentUid ?: return
        viewModelScope.launch { runCatching { accounts.markNotificationRead(uid, notificationId) } }
    }

    fun changePassword(current: String, new: String) =
        act { auth.changePassword(current, new); "Password changed." }

    /**
     * Deletion re-authenticates first, then hands off to a Cloud Function that
     * anonymises the profile while leaving the entry ledger intact; results for
     * past raffles must stay accurate.
     */
    fun deleteAccount(password: String, onDeleted: (Int) -> Unit) =
        act {
            auth.reauthenticate(password)
            onDeleted(accounts.deleteAccount())
            null
        }

    fun signOut() = auth.signOut()

    fun clearMessage() { _state.value = _state.value.copy(message = null, error = null) }

    private fun act(block: suspend () -> String?) {
        viewModelScope.launch {
            _state.value = _state.value.copy(busy = true, error = null, message = null)
            try {
                val msg = block()
                _state.value = _state.value.copy(busy = false, message = msg)
            } catch (t: Throwable) {
                _state.value = _state.value.copy(busy = false, error = Errors.from(t))
            }
        }
    }
}
