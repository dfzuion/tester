package uk.co.rodrunners.raffles.ui.screens.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import uk.co.rodrunners.raffles.core.AppError
import uk.co.rodrunners.raffles.core.Errors
import uk.co.rodrunners.raffles.data.model.AdminUser
import uk.co.rodrunners.raffles.data.repository.AdminRepository

data class AdminUsersState(
    val admins: List<AdminUser> = emptyList(),
    val email: String = "",
    val role: String = "admin",
    val bootstrapKey: String = "",
    val needsBootstrap: Boolean = false,
    val loading: Boolean = true,
    val busy: Boolean = false,
    val message: String? = null,
    val error: AppError? = null,
) {
    val canGrant: Boolean get() = email.contains("@") && !busy
}

@HiltViewModel
class AdminUsersViewModel @Inject constructor(
    private val admin: AdminRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(AdminUsersState())
    val state: StateFlow<AdminUsersState> = _state.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true) }
            runCatching { admin.adminUsers() }
                .onSuccess { list -> _state.update { it.copy(admins = list, loading = false, needsBootstrap = false) } }
                .onFailure { t ->
                    // A permission error here usually means this project has no
                    // administrator yet, so offer the one-time setup instead.
                    _state.update {
                        it.copy(loading = false, needsBootstrap = true, error = Errors.from(t))
                    }
                }
        }
    }

    fun onEmail(v: String) = _state.update { it.copy(email = v.trim(), error = null) }
    fun onRole(v: String) = _state.update { it.copy(role = v) }
    fun onBootstrapKey(v: String) = _state.update { it.copy(bootstrapKey = v, error = null) }

    fun bootstrap() = act { admin.bootstrapFirstAdmin(_state.value.bootstrapKey.trim()) }

    fun grant() {
        val s = _state.value
        if (!s.canGrant) return
        act { admin.grantAdmin(s.email, s.role).also { _state.update { st -> st.copy(email = "") } } }
    }

    fun revoke(uid: String) = act { admin.revokeAdmin(uid); "Access removed." }

    fun clearMessage() = _state.update { it.copy(message = null, error = null) }

    private fun act(block: suspend () -> String) {
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null, message = null) }
            try {
                val msg = block()
                _state.update { it.copy(busy = false, message = msg) }
                load()
            } catch (t: Throwable) {
                _state.update { it.copy(busy = false, error = Errors.from(t)) }
            }
        }
    }
}
