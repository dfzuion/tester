package uk.co.rodrunners.raffles.ui.screens.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import uk.co.rodrunners.raffles.core.Errors
import uk.co.rodrunners.raffles.core.UiState
import uk.co.rodrunners.raffles.data.model.Competition
import uk.co.rodrunners.raffles.data.repository.CompetitionRepository

@OptIn(FlowPreview::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val competitions: CompetitionRepository,
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _state = MutableStateFlow<UiState<List<Competition>>>(
        UiState.Empty(
            "What are you after?",
            "Search live raffles by prize, brand or category.",
        )
    )
    val state: StateFlow<UiState<List<Competition>>> = _state.asStateFlow()

    private val _suggestions = MutableStateFlow(listOf("Rods", "Reels", "Bivvies", "Bite alarms", "Bundles"))
    val suggestions: StateFlow<List<String>> = _suggestions.asStateFlow()

    init {
        viewModelScope.launch {
            _query.debounce(280).distinctUntilChanged().collectLatest { q -> run(q) }
        }
    }

    fun setQuery(q: String) { _query.value = q }

    fun retry() = run(_query.value)

    private fun run(q: String) {
        val needle = q.trim()
        if (needle.length < 2) {
            _state.value = UiState.Empty(
                "What are you after?",
                "Search live raffles by prize, brand or category.",
            )
            return
        }
        viewModelScope.launch {
            _state.value = UiState.Loading
            try {
                val results = competitions.search(needle)
                _state.value = if (results.isEmpty()) {
                    UiState.Empty(
                        "Nothing matched \"$needle\"",
                        "Try a brand name like Nash or Daiwa, or browse all live raffles.",
                        "Browse raffles",
                    )
                } else UiState.Success(results)
            } catch (t: Throwable) {
                _state.value = UiState.Error(Errors.from(t))
            }
        }
    }
}
