package uk.co.rodrunners.raffles.ui.screens.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.Calendar
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import uk.co.rodrunners.raffles.core.AppError
import uk.co.rodrunners.raffles.core.Errors
import uk.co.rodrunners.raffles.data.model.CreditCoupon
import uk.co.rodrunners.raffles.data.repository.CreditRepository

data class CouponsState(
    val coupons: List<CreditCoupon> = emptyList(),
    val code: String = "",
    val valuePence: Int = 500,
    val maxRedemptions: Int = 100,
    val expiryDays: Int = 30,
    val grantEmail: String = "",
    val grantPence: Int = 500,
    val loading: Boolean = true,
    val busy: Boolean = false,
    val message: String? = null,
    val error: AppError? = null,
) {
    val canCreate: Boolean get() = code.trim().length >= 4 && valuePence > 0 && !busy
    val canGrant: Boolean get() = grantEmail.contains("@") && grantPence != 0 && !busy
}

@HiltViewModel
class CouponsViewModel @Inject constructor(
    private val credit: CreditRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(CouponsState())
    val state: StateFlow<CouponsState> = _state.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true) }
            runCatching { credit.coupons() }
                .onSuccess { list -> _state.update { it.copy(coupons = list, loading = false) } }
                .onFailure { t -> _state.update { it.copy(error = Errors.from(t), loading = false) } }
        }
    }

    fun onCode(v: String) = _state.update { it.copy(code = v.uppercase().filter { c -> c.isLetterOrDigit() || c == '-' }) }
    fun onValue(v: Int) = _state.update { it.copy(valuePence = v) }
    fun onMax(v: Int) = _state.update { it.copy(maxRedemptions = v) }
    fun onExpiryDays(v: Int) = _state.update { it.copy(expiryDays = v) }
    fun onGrantEmail(v: String) = _state.update { it.copy(grantEmail = v.trim()) }
    fun onGrantPence(v: Int) = _state.update { it.copy(grantPence = v) }

    fun create() {
        val s = _state.value
        if (!s.canCreate) return
        val expiry = if (s.expiryDays <= 0) null else Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, s.expiryDays)
        }.timeInMillis
        act {
            credit.createCoupon(s.code, s.valuePence, s.maxRedemptions, expiry)
            _state.update { it.copy(code = "") }
            "${s.code} created."
        }
    }

    fun toggle(coupon: CreditCoupon) = act {
        credit.setCouponActive(coupon.code, !coupon.active)
        if (coupon.active) "${coupon.code} switched off." else "${coupon.code} switched on."
    }

    fun grant() {
        val s = _state.value
        if (!s.canGrant) return
        act {
            credit.grantCredit(s.grantEmail, s.grantPence, "Manual adjustment")
            _state.update { it.copy(grantEmail = "") }
            "Credit adjusted for ${s.grantEmail}."
        }
    }

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
