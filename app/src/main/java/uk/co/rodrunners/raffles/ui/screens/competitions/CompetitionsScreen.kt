package uk.co.rodrunners.raffles.ui.screens.competitions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.Companion.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import uk.co.rodrunners.raffles.data.model.Competition
import uk.co.rodrunners.raffles.data.model.CompetitionSort
import uk.co.rodrunners.raffles.data.model.CompetitionTab
import uk.co.rodrunners.raffles.ui.components.ChipRow
import uk.co.rodrunners.raffles.ui.components.CompetitionRow
import uk.co.rodrunners.raffles.ui.components.ShimmerBlock
import uk.co.rodrunners.raffles.ui.components.StateHost
import uk.co.rodrunners.raffles.ui.theme.Dimens
import uk.co.rodrunners.raffles.ui.theme.RrrColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompetitionsScreen(
    onOpenCompetition: (String) -> Unit,
    onOpenSearch: () -> Unit,
    viewModel: CompetitionsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val tab by viewModel.tab.collectAsStateWithLifecycle()
    val sort by viewModel.sort.collectAsStateWithLifecycle()
    val category by viewModel.category.collectAsStateWithLifecycle()
    var sortMenuOpen by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    // Page in more rows a little before the customer reaches the bottom.
    val shouldLoadMore by remember {
        derivedStateOf {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            val total = listState.layoutInfo.totalItemsCount
            total > 0 && last >= total - 3
        }
    }
    LaunchedEffect(listState) {
        snapshotFlow { shouldLoadMore }.collect { if (it) viewModel.loadMore() }
    }

    Scaffold(
        containerColor = RrrColors.Ink,
        topBar = {
            Column {
                TopAppBar(
                    title = { Text("Raffles", style = MaterialTheme.typography.titleLarge) },
                    actions = {
                        IconButton(onClick = onOpenSearch) {
                            Icon(Icons.Outlined.Search, "Search raffles", tint = RrrColors.Bone)
                        }
                        IconButton(onClick = { sortMenuOpen = true }) {
                            Icon(Icons.Outlined.FilterList, "Sort raffles", tint = RrrColors.Bone)
                        }
                        DropdownMenu(
                            expanded = sortMenuOpen,
                            onDismissRequest = { sortMenuOpen = false },
                        ) {
                            CompetitionSort.entries.forEach { option ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            option.label,
                                            color = if (option == sort) RrrColors.Gold else RrrColors.Bone,
                                        )
                                    },
                                    onClick = {
                                        viewModel.selectSort(option)
                                        sortMenuOpen = false
                                    },
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = RrrColors.Ink,
                        titleContentColor = RrrColors.Bone,
                    ),
                )
                ScrollableTabRow(
                    selectedTabIndex = CompetitionTab.entries.indexOf(tab),
                    containerColor = RrrColors.Ink,
                    contentColor = RrrColors.Gold,
                    edgePadding = Dimens.gutter,
                    divider = {},
                    indicator = { positions ->
                        TabRowDefaults.SecondaryIndicator(
                            Modifier.tabIndicatorOffset(positions[CompetitionTab.entries.indexOf(tab)]),
                            height = 2.dp,
                            color = RrrColors.Gold,
                        )
                    },
                ) {
                    CompetitionTab.entries.forEach { t ->
                        Tab(
                            selected = t == tab,
                            onClick = { viewModel.selectTab(t) },
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
                Spacer(Modifier.height(12.dp))
                ChipRow(
                    items = Competition.CATEGORIES.map { it.first },
                    selected = category,
                    label = { key -> Competition.CATEGORIES.first { it.first == key }.second },
                    onSelect = viewModel::selectCategory,
                )
                Spacer(Modifier.height(4.dp))
            }
        },
    ) { padding ->
        StateHost(
            state = state,
            modifier = Modifier.padding(padding),
            onRetry = viewModel::refresh,
            onEmptyAction = viewModel::clearFilter,
        ) { catalogue ->
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = Dimens.gutter, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(Dimens.cardGap),
            ) {
                items(catalogue.items, key = { it.id }) { competition ->
                    CompetitionRow(
                        competition = competition,
                        onClick = { onOpenCompetition(competition.id) },
                        isFavourite = catalogue.favouriteIds.contains(competition.id),
                        onToggleFavourite = {
                            viewModel.toggleFavourite(
                                competition.id,
                                !catalogue.favouriteIds.contains(competition.id),
                            )
                        },
                    )
                }
                if (catalogue.loadingMore) {
                    item { ShimmerBlock(Modifier.fillMaxWidth().height(108.dp)) }
                }
                if (catalogue.endReached && catalogue.items.size > 6) {
                    item {
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 24.dp),
                            horizontalArrangement = Arrangement.Center,
                        ) {
                            Text(
                                "That's everything currently open.",
                                style = MaterialTheme.typography.labelSmall,
                                color = RrrColors.Slate,
                            )
                        }
                    }
                }
            }
        }
    }
}
