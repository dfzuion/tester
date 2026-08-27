package uk.co.rodrunners.raffles.ui.screens.favourites

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import uk.co.rodrunners.raffles.data.model.Competition
import uk.co.rodrunners.raffles.data.repository.AuthRepository
import uk.co.rodrunners.raffles.data.repository.CompetitionRepository
import uk.co.rodrunners.raffles.data.repository.FavouritesRepository
import uk.co.rodrunners.raffles.ui.components.CompetitionRow
import uk.co.rodrunners.raffles.ui.components.StateHost
import uk.co.rodrunners.raffles.ui.theme.Dimens
import uk.co.rodrunners.raffles.ui.theme.RrrColors

@HiltViewModel
class FavouritesViewModel @Inject constructor(
    private val favourites: FavouritesRepository,
    private val competitions: CompetitionRepository,
    private val auth: AuthRepository,
) : ViewModel() {

    private val _state = MutableStateFlow<UiState<List<Competition>>>(UiState.Loading)
    val state: StateFlow<UiState<List<Competition>>> = _state.asStateFlow()

    private var watcher: Job? = null

    init { load() }

    fun load() {
        val uid = auth.currentUid ?: run {
            _state.value = UiState.Empty("Log in to save raffles", "Saved raffles are tied to your account.", "Log in")
            return
        }
        watcher?.cancel()
        watcher = viewModelScope.launch {
            favourites.favouriteIds(uid)
                .catch { t -> _state.value = UiState.Error(Errors.from(t)) }
                .collect { ids ->
                    if (ids.isEmpty()) {
                        _state.value = UiState.Empty(
                            "Nothing saved yet",
                            "Tap the bookmark on any raffle to keep an eye on it here.",
                            "Browse raffles",
                        )
                        return@collect
                    }
                    val list = runCatching { competitions.byIds(ids.toList()) }.getOrDefault(emptyList())
                    _state.value = UiState.Success(list)
                }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavouritesScreen(
    onBack: () -> Unit,
    onOpenCompetition: (String) -> Unit,
    onBrowse: () -> Unit,
    viewModel: FavouritesViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        containerColor = RrrColors.Ink,
        topBar = {
            TopAppBar(
                title = { Text("Saved raffles", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Go back", tint = RrrColors.Bone)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = RrrColors.Ink,
                    titleContentColor = RrrColors.Bone,
                ),
            )
        },
    ) { padding ->
        StateHost(
            state = state,
            modifier = Modifier.padding(padding),
            onRetry = viewModel::load,
            onEmptyAction = onBrowse,
        ) { list ->
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = Dimens.gutter, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(Dimens.cardGap),
            ) {
                items(list, key = { it.id }) { c ->
                    CompetitionRow(c, onClick = { onOpenCompetition(c.id) })
                }
            }
        }
    }
}
