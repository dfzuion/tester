package uk.co.rodrunners.raffles.ui.screens.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import uk.co.rodrunners.raffles.ui.components.Chip
import uk.co.rodrunners.raffles.ui.components.CompetitionRow
import uk.co.rodrunners.raffles.ui.components.StateHost
import uk.co.rodrunners.raffles.ui.screens.auth.RrrTextField
import uk.co.rodrunners.raffles.ui.theme.Dimens
import uk.co.rodrunners.raffles.ui.theme.RrrColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    onBack: () -> Unit,
    onOpenCompetition: (String) -> Unit,
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val suggestions by viewModel.suggestions.collectAsStateWithLifecycle()
    val focus = remember { FocusRequester() }

    LaunchedEffect(Unit) { focus.requestFocus() }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            Column {
                TopAppBar(
                    title = { Text("Search", style = MaterialTheme.typography.titleLarge) },
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
                Box(Modifier.padding(horizontal = Dimens.gutter)) {
                    RrrTextField(
                        value = query,
                        onValueChange = viewModel::setQuery,
                        label = "Rod, reel, bivvy, brand…",
                        modifier = Modifier.focusRequester(focus),
                    )
                }
                if (suggestions.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    Row(
                        Modifier.padding(horizontal = Dimens.gutter),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        suggestions.forEach { s -> Chip(s, false, onClick = { viewModel.setQuery(s) }) }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        },
    ) { padding ->
        StateHost(state = state, modifier = Modifier.padding(padding), onRetry = viewModel::retry) { results ->
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = Dimens.gutter, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(Dimens.cardGap),
            ) {
                items(results, key = { it.id }) { competition ->
                    CompetitionRow(competition, onClick = { onOpenCompetition(competition.id) })
                }
            }
        }
    }
}
