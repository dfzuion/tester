package uk.co.rodrunners.raffles.ui.screens.credit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import uk.co.rodrunners.raffles.core.AppError
import uk.co.rodrunners.raffles.core.Errors
import uk.co.rodrunners.raffles.data.model.CreditEntry
import uk.co.rodrunners.raffles.data.model.UserProfile
import uk.co.rodrunners.raffles.data.repository.AccountRepository
import uk.co.rodrunners.raffles.data.repository.AuthRepository
import uk.co.rodrunners.raffles.data.repository.CreditRepository

data class CreditState(
    val profile: UserProfile? = null,
    val ledger: List<CreditEntry> = emptyList(),
    val couponCode: String = "",
    val redeeming: Boolean = false,
    val message: String? = null,
    val error: AppError? = null,
) {
    val balancePence: Int get() = profile?.creditBalancePence ?: 0
    val canRedeem: Boolean get() = couponCode.trim().length >= 4 && !redeeming
}

@HiltViewModel
class CreditViewModel @Inject constructor(
    private val credit: CreditRepository,
    private val accounts: AccountRepository,
    private val auth: AuthRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(CreditState())
    val state: StateFlow<CreditState> = _state.asStateFlow()

    init {
        val uid = auth.currentUid
        if (uid != null) {
            viewModelScope.launch {
                accounts.profile(uid)
                    .catch { t -> _state.update { it.copy(error = Errors.from(t)) } }
                    .collect { p -> _state.update { it.copy(profile = p) } }
            }
            viewModelScope.launch {
                credit.ledger(uid)
                    .catch { t -> _state.update { it.copy(error = Errors.from(t)) } }
                    .collect { rows -> _state.update { it.copy(ledger = rows) } }
            }
        }
    }

    fun onCode(v: String) = _state.update { it.copy(couponCode = v.uppercase(), error = null, message = null) }

    fun redeem() {
        val s = _state.value
        if (!s.canRedeem) return
        viewModelScope.launch {
            _state.update { it.copy(redeeming = true, error = null, message = null) }
            try {
                val added = credit.redeemCoupon(s.couponCode).first
                _state.update {
                    it.copy(
                        redeeming = false,
                        couponCode = "",
                        message = "£%.2f added to your balance.".format(added / 100.0),
                    )
                }
            } catch (t: Throwable) {
                _state.update { it.copy(redeeming = false, error = Errors.from(t)) }
            }
        }
    }

    fun clearMessage() = _state.update { it.copy(message = null, error = null) }
}
