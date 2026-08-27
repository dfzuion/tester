package uk.co.rodrunners.raffles.ui.screens.legal

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import uk.co.rodrunners.raffles.data.model.RulesDocument
import uk.co.rodrunners.raffles.data.repository.CompetitionRepository
import uk.co.rodrunners.raffles.ui.components.StateHost
import uk.co.rodrunners.raffles.ui.theme.Dimens
import uk.co.rodrunners.raffles.ui.theme.RrrColors

@HiltViewModel
class RulesViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val competitions: CompetitionRepository,
) : ViewModel() {

    private val competitionId: String = checkNotNull(savedState["competitionId"])

    private val _state = MutableStateFlow<UiState<RulesDocument>>(UiState.Loading)
    val state: StateFlow<UiState<RulesDocument>> = _state.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            _state.value = UiState.Loading
            try {
                val competition = competitions.competition(competitionId).first()
                val rulesId = competition?.rulesId
                val doc = rulesId?.let { competitions.rules(it) }
                _state.value = doc?.let { UiState.Success(it) }
                    ?: UiState.Empty(
                        "Rules not published",
                        "The rules for this raffle haven't been published yet. Entries can't be bought until they are.",
                    )
            } catch (t: Throwable) {
                _state.value = UiState.Error(Errors.from(t))
            }
        }
    }
}

/** Renders the rules exactly as published against this raffle; nothing is generated here. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RulesScreen(
    onBack: () -> Unit,
    viewModel: RulesViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text("Competition rules", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Go back", tint = RrrColors.Bone)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = RrrColors.Bone,
                ),
            )
        },
    ) { padding ->
        StateHost(state = state, modifier = Modifier.padding(padding), onRetry = viewModel::load) { doc ->
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Dimens.gutter),
            ) {
                Spacer(Modifier.height(8.dp))
                Text("Version ${doc.version}", style = MaterialTheme.typography.labelSmall, color = RrrColors.Slate)
                if (doc.legalReviewRequired) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "Placeholder copy pending legal review — replace before launch.",
                        style = MaterialTheme.typography.labelSmall,
                        color = RrrColors.Warning,
                    )
                }
                Spacer(Modifier.height(20.dp))
                doc.sections.forEach { section ->
                    Text(section.heading, style = MaterialTheme.typography.titleMedium, color = RrrColors.Bone)
                    Spacer(Modifier.height(6.dp))
                    Text(section.body, style = MaterialTheme.typography.bodyMedium, color = RrrColors.Mist)
                    Spacer(Modifier.height(20.dp))
                }
                doc.note?.let {
                    Text(it, style = MaterialTheme.typography.labelSmall, color = RrrColors.Slate)
                }
                Spacer(Modifier.height(40.dp))
            }
        }
    }
}
