package uk.co.rodrunners.raffles.ui.screens.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import uk.co.rodrunners.raffles.core.AppError
import uk.co.rodrunners.raffles.core.Errors
import uk.co.rodrunners.raffles.data.repository.AccountRepository
import uk.co.rodrunners.raffles.data.repository.AuthRepository
import uk.co.rodrunners.raffles.data.repository.AuthState

data class AuthFormState(
    val email: String = "",
    val password: String = "",
    val confirmPassword: String = "",
    val displayName: String = "",
    val referralCode: String = "",
    val ageConfirmed: Boolean = false,
    val rulesAccepted: Boolean = false,
    val marketingOptIn: Boolean = false,
    val submitting: Boolean = false,
    val error: AppError? = null,
    val info: String? = null,
) {
    val emailValid: Boolean get() = android.util.Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches()
    val passwordValid: Boolean get() = password.length >= 8 &&
        password.any { it.isDigit() } && password.any { it.isLetter() }
    val canLogIn: Boolean get() = emailValid && password.isNotBlank() && !submitting
    val canRegister: Boolean get() = emailValid && passwordValid &&
        password == confirmPassword && displayName.trim().length >= 2 &&
        ageConfirmed && rulesAccepted && !submitting
}

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val auth: AuthRepository,
    private val accounts: AccountRepository,
) : ViewModel() {

    private val _form = MutableStateFlow(AuthFormState())
    val form: StateFlow<AuthFormState> = _form.asStateFlow()

    val authState: StateFlow<AuthState> = auth.authState()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AuthState.Loading)

    fun onEmail(v: String) = _form.update { it.copy(email = v, error = null) }
    fun onPassword(v: String) = _form.update { it.copy(password = v, error = null) }
    fun onConfirmPassword(v: String) = _form.update { it.copy(confirmPassword = v, error = null) }
    fun onDisplayName(v: String) = _form.update { it.copy(displayName = v, error = null) }
    fun onReferralCode(v: String) = _form.update { it.copy(referralCode = v.uppercase()) }
    fun onAgeConfirmed(v: Boolean) = _form.update { it.copy(ageConfirmed = v) }
    fun onRulesAccepted(v: Boolean) = _form.update { it.copy(rulesAccepted = v) }
    fun onMarketing(v: Boolean) = _form.update { it.copy(marketingOptIn = v) }
    fun clearMessages() = _form.update { it.copy(error = null, info = null) }

    fun logIn(onSuccess: () -> Unit) {
        val f = _form.value
        if (!f.canLogIn) return
        submit {
            auth.signIn(f.email, f.password)
            runCatching { accounts.syncPushToken() }
            onSuccess()
        }
    }

    fun register(onSuccess: () -> Unit) {
        val f = _form.value
        if (!f.canRegister) return
        submit {
            auth.register(
                email = f.email,
                password = f.password,
                displayName = f.displayName,
                ageConfirmed = f.ageConfirmed,
                marketingOptIn = f.marketingOptIn,
                referredBy = f.referralCode.ifBlank { null },
            )
            runCatching { accounts.syncPushToken() }
            onSuccess()
        }
    }

    /** Called with the ID token that Credential Manager returned. */
    fun signInWithGoogle(idToken: String, onSuccess: () -> Unit) = submit {
        auth.signInWithGoogle(idToken)
        runCatching { accounts.syncPushToken() }
        onSuccess()
    }

    fun showError(title: String, message: String) = _form.update {
        it.copy(error = AppError(title, message, retryable = false), submitting = false)
    }

    fun sendPasswordReset() {
        val f = _form.value
        if (!f.emailValid) {
            _form.update { it.copy(error = AppError("Check the address", "Enter the email address on your account.", retryable = false)) }
            return
        }
        submit {
            auth.sendPasswordReset(f.email)
            _form.update {
                it.copy(info = "If that address has an account, a reset link is on its way. It expires in one hour.")
            }
        }
    }

    fun resendVerification() = submit {
        auth.resendVerification()
        _form.update { it.copy(info = "Verification email sent. Check your junk folder too.") }
    }

    fun refreshVerification(onVerified: () -> Unit) = submit {
        if (auth.reloadUser()) onVerified()
        else _form.update { it.copy(info = "Not verified yet. Open the link in the email, then tap again.") }
    }

    fun signOut() = auth.signOut()

    private fun submit(block: suspend () -> Unit) {
        viewModelScope.launch {
            _form.update { it.copy(submitting = true, error = null, info = null) }
            try {
                block()
            } catch (t: Throwable) {
                _form.update { it.copy(error = Errors.from(t)) }
            } finally {
                _form.update { it.copy(submitting = false) }
            }
        }
    }
}

private inline fun <T> MutableStateFlow<T>.update(block: (T) -> T) {
    value = block(value)
}
