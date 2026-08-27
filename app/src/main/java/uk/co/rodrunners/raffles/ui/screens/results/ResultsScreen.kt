package uk.co.rodrunners.raffles.ui.screens.results

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.Companion.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import uk.co.rodrunners.raffles.core.TimeFormat
import uk.co.rodrunners.raffles.data.model.Winner
import uk.co.rodrunners.raffles.ui.components.PrizeImage
import uk.co.rodrunners.raffles.ui.components.StateHost
import uk.co.rodrunners.raffles.ui.components.StatusPill
import uk.co.rodrunners.raffles.ui.screens.auth.RrrTextField
import uk.co.rodrunners.raffles.ui.theme.Dimens
import uk.co.rodrunners.raffles.ui.theme.RrrColors
import uk.co.rodrunners.raffles.ui.theme.RrrShapes
import uk.co.rodrunners.raffles.ui.theme.RrrType

enum class ResultsTab(val label: String) { RECENT("Recent wins"), MINE("My wins") }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultsScreen(
    onOpenCompetition: (String) -> Unit,
    onBrowse: () -> Unit,
    viewModel: ResultsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val tab by viewModel.tab.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()

    Scaffold(
        containerColor = RrrColors.Ink,
        topBar = {
            Column {
                TopAppBar(
                    title = { Text("Results", style = MaterialTheme.typography.titleLarge) },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = RrrColors.Ink,
                        titleContentColor = RrrColors.Bone,
                    ),
                )
                TabRow(
                    selectedTabIndex = ResultsTab.entries.indexOf(tab),
                    containerColor = RrrColors.Ink,
                    divider = {},
                    indicator = { positions ->
                        TabRowDefaults.SecondaryIndicator(
                            Modifier.tabIndicatorOffset(positions[ResultsTab.entries.indexOf(tab)]),
                            height = 2.dp,
                            color = RrrColors.Gold,
                        )
                    },
                ) {
                    ResultsTab.entries.forEach { t ->
                        Tab(
                            selected = t == tab,
                            onClick = { viewModel.setTab(t) },
                            text = {
                                Text(
                                    t.label,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (t == tab) RrrColors.Gold else RrrColors.Slate,
                                )
                            },
                        )
                    }
                }
                if (tab == ResultsTab.RECENT) {
                    Spacer(Modifier.height(12.dp))
                    Box(Modifier.padding(horizontal = Dimens.gutter)) {
                        RrrTextField(
                            value = query,
                            onValueChange = viewModel::setQuery,
                            label = "Search prize or winning number",
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        },
    ) { padding ->
        StateHost(
            state = state,
            modifier = Modifier.padding(padding),
            onRetry = viewModel::load,
            onEmptyAction = onBrowse,
        ) { winners ->
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = Dimens.gutter, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(Dimens.cardGap),
            ) {
                items(winners, key = { it.id }) { winner ->
                    ResultCard(
                        winner = winner,
                        isMine = tab == ResultsTab.MINE,
                        onClick = { onOpenCompetition(winner.competitionId) },
                    )
                }
                item {
                    Text(
                        "Every drawn raffle is published here with its winning number. " +
                            "Draw records, including the eligible entry count, are retained for each one.",
                        style = MaterialTheme.typography.labelSmall,
                        color = RrrColors.Slate,
                        modifier = Modifier.padding(vertical = 24.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun ResultCard(winner: Winner, isMine: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RrrShapes.large)
            .background(RrrColors.Surface)
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PrizeImage(winner.prizeImageUrl, null, Modifier.size(60.dp).clip(RrrShapes.medium))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                winner.competitionTitle,
                style = MaterialTheme.typography.titleMedium,
                color = RrrColors.Bone,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                if (isMine) "You won with entry ${winner.winningEntryNumber}"
                else "Won by ${winner.winnerDisplayName} — entry ${winner.winningEntryNumber}",
                style = MaterialTheme.typography.labelSmall,
                color = if (isMine) RrrColors.Gold else RrrColors.Mist,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                "Drawn ${TimeFormat.date(winner.drawnAtMillis)}",
                style = MaterialTheme.typography.labelSmall,
                color = RrrColors.Slate,
            )
        }
        if (isMine) {
            StatusPill("won")
        } else {
            Text(
                "#${winner.winningEntryNumber}",
                style = RrrType.NumericSmall,
                color = RrrColors.Gold,
            )
        }
    }
}
