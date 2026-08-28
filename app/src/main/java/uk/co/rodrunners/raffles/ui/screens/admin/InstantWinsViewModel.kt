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
import uk.co.rodrunners.raffles.data.model.InstantWinStock
import uk.co.rodrunners.raffles.data.repository.AdminRepository
import uk.co.rodrunners.raffles.data.repository.InstantWinOverview

data class InstantWinsState(
    val competitionId: String = "",
    val overview: InstantWinOverview = InstantWinOverview(),
    val newPrizeName: String = "",
    val newPrizeValuePence: Int = 0,
    val newPrizeQuantity: Int = 1,
    val newPrizeType: String = "item",
    val loading: Boolean = true,
    val busy: Boolean = false,
    val message: String? = null,
    val error: AppError? = null,
) {
    val canAdd: Boolean
        get() = newPrizeName.trim().length >= 2 && newPrizeQuantity in 1..5000 && !busy &&
            (newPrizeType != "credit" || newPrizeValuePence > 0)
}

@HiltViewModel
class InstantWinsViewModel @Inject constructor(
    private val admin: AdminRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(InstantWinsState())
    val state: StateFlow<InstantWinsState> = _state.asStateFlow()

    fun start(competitionId: String) {
        if (_state.value.competitionId == competitionId) return
        _state.update { it.copy(competitionId = competitionId) }
        refresh()
    }

    fun refresh() {
        val id = _state.value.competitionId
        if (id.isBlank()) return
        viewModelScope.launch {
            _state.update { it.copy(loading = true) }
            runCatching { admin.instantWins(id) }
                .onSuccess { o -> _state.update { it.copy(overview = o, loading = false) } }
                .onFailure { t -> _state.update { it.copy(error = Errors.from(t), loading = false) } }
        }
    }

    fun onName(v: String) = _state.update { it.copy(newPrizeName = v, error = null) }
    fun onValuePence(v: Int) = _state.update { it.copy(newPrizeValuePence = v) }
    fun onQuantity(v: Int) = _state.update { it.copy(newPrizeQuantity = v) }
    fun onPrizeType(v: String) = _state.update { it.copy(newPrizeType = v) }

    fun addPrizes() {
        val s = _state.value
        if (!s.canAdd) return
        act {
            val added = admin.addInstantWins(
                s.competitionId,
                listOf(
                    InstantWinStock(
                        prizeName = s.newPrizeName.trim(),
                        valuePence = s.newPrizeValuePence,
                        count = s.newPrizeQuantity,
                        prizeType = s.newPrizeType,
                    )
                ),
            )
            _state.update {
                it.copy(newPrizeName = "", newPrizeValuePence = 0, newPrizeQuantity = 1, newPrizeType = "item")
            }
            "$added prize${if (added == 1) "" else "s"} hidden among the unsold numbers."
        }
    }

    fun removePrizes(prizeName: String, count: Int) = act {
        admin.removeInstantWins(_state.value.competitionId, prizeName, count)
        "Removed."
    }

    fun setClaimStatus(instantWinId: String, status: String) = act {
        admin.setInstantWinClaimStatus(instantWinId, status)
        "Claim updated."
    }

    fun clearMessage() = _state.update { it.copy(message = null, error = null) }

    private fun act(block: suspend () -> String) {
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null, message = null) }
            try {
                val msg = block()
                _state.update { it.copy(busy = false, message = msg) }
                refresh()
            } catch (t: Throwable) {
                _state.update { it.copy(busy = false, error = Errors.from(t)) }
            }
        }
    }
}
