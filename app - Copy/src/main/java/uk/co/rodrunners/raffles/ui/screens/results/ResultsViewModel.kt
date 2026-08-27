package uk.co.rodrunners.raffles.ui.screens.results

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
import uk.co.rodrunners.raffles.data.model.Winner
import uk.co.rodrunners.raffles.data.repository.AuthRepository
import uk.co.rodrunners.raffles.data.repository.ResultsRepository

@HiltViewModel
class ResultsViewModel @Inject constructor(
    private val results: ResultsRepository,
    private val auth: AuthRepository,
) : ViewModel() {

    private val _state = MutableStateFlow<UiState<List<Winner>>>(UiState.Loading)
    val state: StateFlow<UiState<List<Winner>>> = _state.asStateFlow()

    private val _tab = MutableStateFlow(ResultsTab.RECENT)
    val tab: StateFlow<ResultsTab> = _tab.asStateFlow()

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private var job: Job? = null

    init { load() }

    fun setTab(tab: ResultsTab) {
        _tab.value = tab
        load()
    }

    fun setQuery(q: String) {
        _query.value = q
        if (_tab.value == ResultsTab.RECENT) load()
    }

    fun load() {
        job?.cancel()
        _state.value = UiState.Loading
        job = viewModelScope.launch {
            when (_tab.value) {
                ResultsTab.RECENT -> {
                    if (_query.value.isBlank()) {
                        results.latestWinners()
                            .catch { t -> _state.value = UiState.Error(Errors.from(t)) }
                            .collect { emit(it, "No results published yet", "Winning numbers appear here as soon as a raffle is drawn.") }
                    } else {
                        try {
                            emit(
                                results.searchResults(_query.value),
                                "Nothing matched \"${_query.value}\"",
                                "Try a prize name, a brand, or a winning entry number.",
                            )
                        } catch (t: Throwable) {
                            _state.value = UiState.Error(Errors.from(t))
                        }
                    }
                }
                ResultsTab.MINE -> {
                    val uid = auth.currentUid
                    if (uid == null) {
                        _state.value = UiState.Empty("Log in to see your wins", "Wins are tied to your account.", "Log in")
                        return@launch
                    }
                    results.myWins(uid)
                        .catch { t -> _state.value = UiState.Error(Errors.from(t)) }
                        .collect {
                            emit(it, "No wins yet", "When one of your entries is drawn, the prize shows up here.", "Browse raffles")
                        }
                }
            }
        }
    }

    private fun emit(list: List<Winner>, emptyTitle: String, emptyBody: String, action: String? = null) {
        _state.value = if (list.isEmpty()) UiState.Empty(emptyTitle, emptyBody, action)
        else UiState.Success(list)
    }
}
