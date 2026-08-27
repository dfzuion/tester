package uk.co.rodrunners.raffles.ui.screens.competitions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import uk.co.rodrunners.raffles.core.Errors
import uk.co.rodrunners.raffles.core.UiState
import uk.co.rodrunners.raffles.data.model.Competition
import uk.co.rodrunners.raffles.data.model.CompetitionSort
import uk.co.rodrunners.raffles.data.model.CompetitionTab
import uk.co.rodrunners.raffles.data.repository.AuthRepository
import uk.co.rodrunners.raffles.data.repository.CompetitionRepository
import uk.co.rodrunners.raffles.data.repository.FavouritesRepository

data class CatalogueState(
    val items: List<Competition> = emptyList(),
    val favouriteIds: Set<String> = emptySet(),
    val endReached: Boolean = false,
    val loadingMore: Boolean = false,
)

@HiltViewModel
class CompetitionsViewModel @Inject constructor(
    private val repo: CompetitionRepository,
    private val favourites: FavouritesRepository,
    private val auth: AuthRepository,
) : ViewModel() {

    private val _state = MutableStateFlow<UiState<CatalogueState>>(UiState.Loading)
    val state: StateFlow<UiState<CatalogueState>> = _state.asStateFlow()

    private val _tab = MutableStateFlow(CompetitionTab.LIVE)
    val tab: StateFlow<CompetitionTab> = _tab.asStateFlow()

    private val _sort = MutableStateFlow(CompetitionSort.ENDING_SOON)
    val sort: StateFlow<CompetitionSort> = _sort.asStateFlow()

    private val _category = MutableStateFlow<String?>(null)
    val category: StateFlow<String?> = _category.asStateFlow()

    private var cursor: Any? = null

    init {
        refresh()
        observeFavourites()
    }

    fun selectTab(tab: CompetitionTab) {
        if (_tab.value == tab) return
        _tab.value = tab
        if (tab == CompetitionTab.NEW) _sort.value = CompetitionSort.NEWEST
        if (tab == CompetitionTab.ENDING_SOON) _sort.value = CompetitionSort.ENDING_SOON
        refresh()
    }

    fun selectSort(sort: CompetitionSort) {
        _sort.value = sort
        refresh()
    }

    fun selectCategory(category: String?) {
        _category.value = category
        refresh()
    }

    fun refresh() {
        cursor = null
        _state.value = UiState.Loading
        loadPage(replace = true)
    }

    /** Called when the list gets within a few rows of the bottom. */
    fun loadMore() {
        val current = _state.value
        if (current !is UiState.Success) return
        if (current.data.endReached || current.data.loadingMore) return
        _state.value = UiState.Success(current.data.copy(loadingMore = true))
        loadPage(replace = false)
    }

    private fun loadPage(replace: Boolean) {
        viewModelScope.launch {
            try {
                val status = when (_tab.value) {
                    CompetitionTab.COMPLETED -> "drawn"
                    else -> "live"
                }
                val (page, next) = repo.page(
                    status = status,
                    category = _category.value,
                    sort = _sort.value,
                    afterValue = cursor,
                )
                cursor = next

                val existing = (_state.value as? UiState.Success)?.data
                val merged = if (replace) page else (existing?.items.orEmpty() + page)
                val favs = existing?.favouriteIds
                    ?: auth.currentUid?.let { runCatching { favourites.favouriteIds(it).first() }.getOrNull() }
                    ?: emptySet()

                _state.value = if (merged.isEmpty()) {
                    UiState.Empty(
                        title = emptyTitle(),
                        body = emptyBody(),
                        actionLabel = if (_category.value != null) "Clear filter" else null,
                    )
                } else {
                    UiState.Success(
                        CatalogueState(
                            items = merged,
                            favouriteIds = favs,
                            endReached = page.size < 12,
                            loadingMore = false,
                        )
                    )
                }
            } catch (t: Throwable) {
                _state.value = UiState.Error(Errors.from(t))
            }
        }
    }

    private fun observeFavourites() {
        val uid = auth.currentUid ?: return
        viewModelScope.launch {
            favourites.favouriteIds(uid).collect { favs ->
                val current = _state.value
                if (current is UiState.Success) {
                    _state.value = UiState.Success(current.data.copy(favouriteIds = favs))
                }
            }
        }
    }

    fun toggleFavourite(competitionId: String, makeFavourite: Boolean) {
        val uid = auth.currentUid ?: return
        viewModelScope.launch { runCatching { favourites.toggle(uid, competitionId, makeFavourite) } }
    }

    fun clearFilter() {
        _category.value = null
        refresh()
    }

    private fun emptyTitle() = when (_tab.value) {
        CompetitionTab.COMPLETED -> "No completed raffles yet"
        else -> if (_category.value != null) "Nothing in that category right now" else "No raffles open"
    }

    private fun emptyBody() = when (_tab.value) {
        CompetitionTab.COMPLETED -> "Once a raffle is drawn, it moves here with its winning number."
        else -> if (_category.value != null) "Try another category, or check back — new raffles go live most weeks."
        else "New raffles go live most weeks. Turn on notifications and we'll let you know."
    }
}
