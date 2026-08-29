package uk.co.rodrunners.raffles.ui.screens.game

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import uk.co.rodrunners.raffles.data.model.LeaderboardWeek
import uk.co.rodrunners.raffles.data.repository.AuthRepository
import uk.co.rodrunners.raffles.data.repository.GameRepository

/**
 * The board beside the game. The game itself is deliberately not in here -
 * it runs at frame rate off a canvas and putting sixty state writes a second
 * through a ViewModel would buy nothing.
 */
@HiltViewModel
class GameViewModel @Inject constructor(
    private val game: GameRepository,
    private val auth: AuthRepository,
) : ViewModel() {

    private val _board = MutableStateFlow(LeaderboardWeek())
    val board: StateFlow<LeaderboardWeek> = _board.asStateFlow()

    private val _note = MutableStateFlow("")
    val note: StateFlow<String> = _note.asStateFlow()

    val signedIn: Boolean get() = auth.currentUid != null

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            runCatching { game.leaderboard() }.onSuccess { _board.value = it }
        }
    }

    /**
     * Sends a landed fish to the board. Signed out there is nobody to pay, so
     * it says so rather than failing quietly and leaving someone wondering why
     * they never appear.
     */
    fun record(species: String, weightLb: Float) {
        if (!signedIn) {
            _note.value = "Log in to put your catches on the weekly board."
            return
        }

        viewModelScope.launch {
            runCatching { game.submitCatch(species, weightLb) }
                .onSuccess { improved ->
                    _note.value = if (improved) "On the board - that is your best of the week." else ""
                    refresh()
                }
                .onFailure { _note.value = it.message ?: "Could not reach the board." }
        }
    }
}
