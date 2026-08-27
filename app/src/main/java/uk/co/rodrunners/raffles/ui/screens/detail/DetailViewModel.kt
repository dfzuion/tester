package uk.co.rodrunners.raffles.ui.screens.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import uk.co.rodrunners.raffles.core.Errors
import uk.co.rodrunners.raffles.core.UiState
import uk.co.rodrunners.raffles.data.model.Competition
import uk.co.rodrunners.raffles.data.model.RulesDocument
import uk.co.rodrunners.raffles.data.model.Winner
import uk.co.rodrunners.raffles.data.repository.AuthRepository
import uk.co.rodrunners.raffles.data.repository.CompetitionRepository
import uk.co.rodrunners.raffles.data.repository.EntryRepository
import uk.co.rodrunners.raffles.data.repository.FavouritesRepository
import uk.co.rodrunners.raffles.data.repository.ResultsRepository

data class CompetitionDetail(
    val competition: Competition,
    val rules: RulesDocument? = null,
    val myEntryCount: Int = 0,
    val previousWinner: Winner? = null,
    val isFavourite: Boolean = false,
    val signedIn: Boolean = false,
) {
    val remainingAllowance: Int
        get() = (competition.maxEntriesPerCustomer - myEntryCount).coerceAtLeast(0)
    val atPersonalLimit: Boolean get() = remainingAllowance == 0
}

@HiltViewModel
class DetailViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val competitions: CompetitionRepository,
    private val entries: EntryRepository,
    private val results: ResultsRepository,
    private val favourites: FavouritesRepository,
    private val auth: AuthRepository,
) : ViewModel() {

    private val competitionId: String = checkNotNull(savedState["competitionId"])

    private val _state = MutableStateFlow<UiState<CompetitionDetail>>(UiState.Loading)
    val state: StateFlow<UiState<CompetitionDetail>> = _state.asStateFlow()

    // Fetched once per screen rather than on every snapshot; entriesSold ticks
    // often and neither of these changes with it.
    private var rules: RulesDocument? = null
    private var rulesLoadedFor: String? = null
    private var myEntryCount: Int = 0
    private var previousWinner: Winner? = null

    private var watcher: Job? = null

    init { load() }

    fun load() {
        val uid = auth.currentUid
        _state.value = UiState.Loading

        // Retrying must not leave the previous snapshot listener attached.
        watcher?.cancel()
        watcher = viewModelScope.launch {
            if (uid != null) {
                myEntryCount = runCatching { entries.myEntryCountFor(uid, competitionId) }.getOrDefault(0)
            }

            val favouriteIds = if (uid != null) favourites.favouriteIds(uid) else flowOf(emptySet())

            combine(competitions.competition(competitionId), favouriteIds) { competition, favourites ->
                competition to favourites
            }
                .catch { t -> _state.value = UiState.Error(Errors.from(t)) }
                .collect { (competition, favouriteSet) ->
                    if (competition == null) {
                        _state.value = UiState.Empty(
                            title = "Raffle unavailable",
                            body = "This raffle has been removed or isn't published yet.",
                        )
                        return@collect
                    }

                    val rulesId = competition.rulesId
                    if (rulesId != null && rulesLoadedFor != rulesId) {
                        rules = runCatching { competitions.rules(rulesId) }.getOrNull()
                        rulesLoadedFor = rulesId
                    }
                    if (competition.status == "drawn" && previousWinner == null) {
                        previousWinner = runCatching { results.winnerFor(competitionId) }.getOrNull()
                    }

                    _state.value = UiState.Success(
                        CompetitionDetail(
                            competition = competition,
                            rules = rules,
                            myEntryCount = myEntryCount,
                            previousWinner = previousWinner,
                            isFavourite = favouriteSet.contains(competitionId),
                            signedIn = uid != null,
                        )
                    )
                }
        }
    }

    /**
     * Optimistic: the row flips immediately and the favourites listener confirms
     * it a moment later, so a slow connection doesn't make the tap feel dead.
     */
    fun toggleFavourite() {
        val uid = auth.currentUid ?: return
        val current = (_state.value as? UiState.Success)?.data ?: return
        val target = !current.isFavourite
        _state.value = UiState.Success(current.copy(isFavourite = target))
        viewModelScope.launch {
            runCatching { favourites.toggle(uid, competitionId, target) }
                .onFailure {
                    val now = _state.value
                    if (now is UiState.Success) {
                        _state.value = UiState.Success(now.data.copy(isFavourite = !target))
                    }
                }
        }
    }
}
