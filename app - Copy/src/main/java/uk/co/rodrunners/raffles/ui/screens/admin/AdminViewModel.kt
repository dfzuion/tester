package uk.co.rodrunners.raffles.ui.screens.admin

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
import uk.co.rodrunners.raffles.data.model.Competition
import uk.co.rodrunners.raffles.data.repository.AdminRepository
import uk.co.rodrunners.raffles.data.repository.DashboardStats
import uk.co.rodrunners.raffles.data.repository.DrawOutcome

data class AdminState(
    val stats: DashboardStats? = null,
    val competitions: List<Competition> = emptyList(),
    val busyCompetitionId: String? = null,
    val message: String? = null,
    val error: AppError? = null,
    val lastDraw: DrawOutcome? = null,
    val loading: Boolean = true,
)

@HiltViewModel
class AdminViewModel @Inject constructor(
    private val admin: AdminRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(AdminState())
    val state: StateFlow<AdminState> = _state.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true)
            runCatching { admin.dashboard() }
                .onSuccess { _state.value = _state.value.copy(stats = it) }
                .onFailure { _state.value = _state.value.copy(error = Errors.from(it)) }
            runCatching { admin.manageableCompetitions() }
                .onSuccess { _state.value = _state.value.copy(competitions = it, loading = false) }
                .onFailure { _state.value = _state.value.copy(error = Errors.from(it), loading = false) }
        }
    }

    fun setStatus(competitionId: String, status: String) =
        act(competitionId) { admin.setStatus(competitionId, status); "Status set to $status." }

    fun close(competitionId: String) =
        act(competitionId) { admin.close(competitionId, reason = null); "Entries closed." }

    /**
     * The draw itself happens server-side: the client only asks for it and shows
     * the recorded outcome, including the seed hash written to the audit trail.
     */
    fun draw(competitionId: String) =
        act(competitionId) {
            val outcome = admin.draw(competitionId, publishImmediately = false)
            _state.value = _state.value.copy(lastDraw = outcome)
            "Drawn: entry ${outcome.winningEntryNumber} from ${outcome.eligibleEntryCount} eligible entries."
        }

    fun publish(competitionId: String) =
        act(competitionId) { admin.publishResult(competitionId); "Result published to customers." }

    fun seedDemoData() =
        act(null) { admin.seedDemoData(); "Demo data seeded." }

    fun clearMessage() { _state.value = _state.value.copy(message = null, error = null, lastDraw = null) }

    private fun act(competitionId: String?, block: suspend () -> String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(busyCompetitionId = competitionId, error = null, message = null)
            try {
                val msg = block()
                _state.value = _state.value.copy(busyCompetitionId = null, message = msg)
                runCatching { admin.dashboard() }.onSuccess {
                    _state.value = _state.value.copy(stats = it)
                }
                runCatching { admin.manageableCompetitions() }.onSuccess {
                    _state.value = _state.value.copy(competitions = it)
                }
            } catch (t: Throwable) {
                _state.value = _state.value.copy(busyCompetitionId = null, error = Errors.from(t))
            }
        }
    }
}
