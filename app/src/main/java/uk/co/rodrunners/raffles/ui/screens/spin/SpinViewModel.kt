package uk.co.rodrunners.raffles.ui.screens.spin

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
import uk.co.rodrunners.raffles.data.model.SpinOutcome
import uk.co.rodrunners.raffles.data.repository.AuthRepository
import uk.co.rodrunners.raffles.data.repository.CreditRepository

data class SpinState(
    val checked: Boolean = false,
    val today: SpinOutcome? = null,
    val spinning: Boolean = false,
    val revealed: SpinOutcome? = null,
    val error: AppError? = null,
) {
    val used: Boolean get() = today != null
}

@HiltViewModel
class SpinViewModel @Inject constructor(
    private val credit: CreditRepository,
    private val auth: AuthRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(SpinState())
    val state: StateFlow<SpinState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        val uid = auth.currentUid
        if (uid == null) {
            _state.update { it.copy(checked = true) }
            return
        }
        viewModelScope.launch {
            runCatching { credit.todaysSpin(uid) }
                .onSuccess { spin -> _state.update { it.copy(checked = true, today = spin) } }
                .onFailure { t -> _state.update { it.copy(checked = true, error = Errors.from(t)) } }
        }
    }

    fun spin() {
        if (_state.value.spinning || _state.value.used) return
        _state.update { it.copy(spinning = true, error = null) }
        viewModelScope.launch {
            runCatching { credit.spinDailyWheel() }
                .onSuccess { outcome ->
                    _state.update {
                        it.copy(
                            spinning = false,
                            today = outcome,
                            revealed = if (outcome.alreadySpun) null else outcome,
                        )
                    }
                }
                .onFailure { t ->
                    _state.update { it.copy(spinning = false, error = Errors.from(t)) }
                }
        }
    }
}
