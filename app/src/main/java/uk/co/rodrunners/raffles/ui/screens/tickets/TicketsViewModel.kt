package uk.co.rodrunners.raffles.ui.screens.tickets

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import uk.co.rodrunners.raffles.core.Errors
import uk.co.rodrunners.raffles.core.UiState
import uk.co.rodrunners.raffles.data.model.TicketGroup
import uk.co.rodrunners.raffles.data.model.TicketState
import uk.co.rodrunners.raffles.data.repository.AuthRepository
import uk.co.rodrunners.raffles.data.repository.EntryRepository

@HiltViewModel
class TicketsViewModel @Inject constructor(
    private val entries: EntryRepository,
    private val auth: AuthRepository,
) : ViewModel() {

    private val _all = MutableStateFlow<List<TicketGroup>>(emptyList())
    private val _filter = MutableStateFlow(TicketState.ACTIVE)
    val filter: StateFlow<TicketState> = _filter.asStateFlow()

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _state = MutableStateFlow<UiState<List<TicketGroup>>>(UiState.Loading)
    val state: StateFlow<UiState<List<TicketGroup>>> = _state.asStateFlow()

    private var watcher: Job? = null

    init { load() }

    fun load() {
        val uid = auth.currentUid
        if (uid == null) {
            _state.value = UiState.Empty(
                "Log in to see your tickets",
                "Your entry numbers are tied to your account.",
                "Log in",
            )
            return
        }
        watcher?.cancel()
        watcher = viewModelScope.launch {
            entries.myEntries(uid)
                .catch { t -> _state.value = UiState.Error(Errors.from(t)) }
                .collect { list ->
                    _all.value = entries.groupTickets(list)
                    applyFilters()
                }
        }
    }

    fun setFilter(state: TicketState) { _filter.value = state; applyFilters() }
    fun setQuery(q: String) { _query.value = q; applyFilters() }

    private fun applyFilters() {
        val needle = _query.value.trim().lowercase()
        val filtered = _all.value
            .filter { it.state == _filter.value }
            .filter { group ->
                needle.isBlank() ||
                    group.competitionTitle.lowercase().contains(needle) ||
                    group.entryNumbers.any { it.toString() == needle }
            }
        _state.value = if (filtered.isEmpty()) {
            UiState.Empty(
                title = when (_filter.value) {
                    TicketState.ACTIVE -> if (needle.isBlank()) "No live entries" else "No matching tickets"
                    TicketState.WON -> "No wins yet"
                    TicketState.NOT_WON -> "Nothing here yet"
                },
                body = when (_filter.value) {
                    TicketState.ACTIVE -> "Entries you hold in raffles that haven't been drawn appear here."
                    TicketState.WON -> "When one of your numbers is drawn, the prize shows up here straight away."
                    TicketState.NOT_WON -> "Raffles you entered that have been drawn will be listed here."
                },
                actionLabel = if (_filter.value == TicketState.ACTIVE) "Browse raffles" else null,
            )
        } else {
            UiState.Success(filtered)
        }
    }
}
