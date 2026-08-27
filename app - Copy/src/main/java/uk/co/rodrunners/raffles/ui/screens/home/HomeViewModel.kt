package uk.co.rodrunners.raffles.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import uk.co.rodrunners.raffles.core.Errors
import uk.co.rodrunners.raffles.core.UiState
import uk.co.rodrunners.raffles.data.model.Banner
import uk.co.rodrunners.raffles.data.model.Competition
import uk.co.rodrunners.raffles.data.model.TicketGroup
import uk.co.rodrunners.raffles.data.model.Winner
import uk.co.rodrunners.raffles.data.repository.AuthRepository
import uk.co.rodrunners.raffles.data.repository.CompetitionRepository
import uk.co.rodrunners.raffles.data.repository.ContentRepository
import uk.co.rodrunners.raffles.data.repository.EntryRepository
import uk.co.rodrunners.raffles.data.repository.FavouritesRepository
import uk.co.rodrunners.raffles.data.repository.ResultsRepository

data class HomeContent(
    val banners: List<Banner> = emptyList(),
    val featured: Competition? = null,
    val live: List<Competition> = emptyList(),
    val endingSoon: List<Competition> = emptyList(),
    val newest: List<Competition> = emptyList(),
    val recentlyCompleted: List<Competition> = emptyList(),
    val latestWinners: List<Winner> = emptyList(),
    val myActiveTickets: List<TicketGroup> = emptyList(),
    val favouriteIds: Set<String> = emptySet(),
    val displayName: String? = null,
    val showsDemoData: Boolean = false,
) {
    /**
     * "Recommended" is deliberately simple and explainable: raffles in the same
     * categories the customer has already entered, that they don't already hold
     * entries in. No opaque scoring, nothing that nudges spend.
     */
    val recommended: List<Competition> get() {
        val enteredIds = myActiveTickets.map { it.competitionId }.toSet()
        if (enteredIds.isEmpty()) return emptyList()
        val enteredCategories = live.filter { it.id in enteredIds }.map { it.category }.toSet()
        return live.filter { it.category in enteredCategories && it.id !in enteredIds }.take(6)
    }
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val competitions: CompetitionRepository,
    private val results: ResultsRepository,
    private val content: ContentRepository,
    private val entries: EntryRepository,
    private val favourites: FavouritesRepository,
    private val auth: AuthRepository,
) : ViewModel() {

    private val _state = MutableStateFlow<UiState<HomeContent>>(UiState.Loading)
    val state: StateFlow<UiState<HomeContent>> = _state.asStateFlow()

    private val banners = MutableStateFlow<List<Banner>>(emptyList())

    init {
        load()
        observe()
    }

    fun load() {
        viewModelScope.launch {
            runCatching { content.banners().banners.filter { it.active } }
                .onSuccess { banners.value = it }
        }
    }

    private fun observe() {
        val uid = auth.currentUid
        viewModelScope.launch {
            combine(
                competitions.liveCompetitions(),
                competitions.endingSoon(),
                competitions.newest(),
                competitions.completed(limit = 6),
                results.latestWinners(limit = 6),
            ) { live, ending, newest, completed, winners ->
                HomeContent(
                    featured = live.firstOrNull { it.featured } ?: live.firstOrNull(),
                    live = live,
                    endingSoon = ending,
                    newest = newest,
                    recentlyCompleted = completed,
                    latestWinners = winners,
                    showsDemoData = live.any { it.isDemo } || completed.any { it.isDemo },
                )
            }.combine(banners) { home, b -> home.copy(banners = b) }
                .combine(
                    if (uid != null) entries.myEntries(uid) else flowOf(emptyList())
                ) { home, myEntries ->
                    home.copy(myActiveTickets = entries.groupTickets(myEntries).filter { !it.isSettled })
                }
                .combine(
                    if (uid != null) favourites.favouriteIds(uid) else flowOf(emptySet())
                ) { home, favs -> home.copy(favouriteIds = favs) }
                .catch { t -> _state.value = UiState.Error(Errors.from(t)) }
                .collect { home ->
                    _state.value = if (home.live.isEmpty() && home.recentlyCompleted.isEmpty()) {
                        UiState.Empty(
                            title = "No raffles open right now",
                            body = "New raffles go live most weeks. Turn on notifications and we'll tell you the moment the next one opens.",
                            actionLabel = "See past results",
                        )
                    } else {
                        UiState.Success(home)
                    }
                }
        }
    }

    fun toggleFavourite(competitionId: String, makeFavourite: Boolean) {
        val uid = auth.currentUid ?: return
        viewModelScope.launch {
            runCatching { favourites.toggle(uid, competitionId, makeFavourite) }
        }
    }
}
