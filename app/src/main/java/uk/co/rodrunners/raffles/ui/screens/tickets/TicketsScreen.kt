package uk.co.rodrunners.raffles.ui.screens.tickets

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import uk.co.rodrunners.raffles.core.TimeFormat
import uk.co.rodrunners.raffles.data.model.TicketGroup
import uk.co.rodrunners.raffles.data.model.TicketState
import uk.co.rodrunners.raffles.ui.components.CountdownText
import uk.co.rodrunners.raffles.ui.components.PrizeImage
import uk.co.rodrunners.raffles.ui.components.StateHost
import uk.co.rodrunners.raffles.ui.components.StatusPill
import uk.co.rodrunners.raffles.ui.components.ViewPill
import uk.co.rodrunners.raffles.ui.screens.auth.RrrTextField
import uk.co.rodrunners.raffles.ui.theme.Dimens
import uk.co.rodrunners.raffles.ui.theme.RrrColors
import uk.co.rodrunners.raffles.ui.theme.RrrShapes
import uk.co.rodrunners.raffles.ui.theme.RrrType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TicketsScreen(
    onBack: () -> Unit,
    onOpenCompetition: (String) -> Unit,
    onBrowse: () -> Unit,
    viewModel: TicketsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val filter by viewModel.filter.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            Column {
                TopAppBar(
                    title = { Text("My tickets", style = MaterialTheme.typography.titleLarge) },
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
                TabRow(
                    selectedTabIndex = TicketState.entries.indexOf(filter),
                    containerColor = Color.Transparent,
                    indicator = { positions ->
                        TabRowDefaults.SecondaryIndicator(
                            Modifier.tabIndicatorOffset(positions[TicketState.entries.indexOf(filter)]),
                            height = 2.dp,
                            color = RrrColors.Gold,
                        )
                    },
                    divider = {},
                ) {
                    TicketState.entries.forEach { s ->
                        Tab(
                            selected = s == filter,
                            onClick = { viewModel.setFilter(s) },
                            text = {
                                Text(
                                    s.label,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (s == filter) RrrColors.Gold else RrrColors.Slate,
                                )
                            },
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                Box(Modifier.padding(horizontal = Dimens.gutter)) {
                    RrrTextField(
                        value = query,
                        onValueChange = viewModel::setQuery,
                        label = "Search by raffle or entry number",
                    )
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
        ) { groups ->
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = Dimens.gutter, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(Dimens.cardGap),
            ) {
                items(groups, key = { it.competitionId }) { group ->
                    TicketCard(group) { onOpenCompetition(group.competitionId) }
                }
            }
        }
    }
}

@Composable
private fun TicketCard(group: TicketGroup, onOpen: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RrrShapes.large)
            .background(RrrColors.Surface)
            .clickable(onClick = onOpen)
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PrizeImage(group.imageUrl, null, Modifier.size(64.dp).clip(RrrShapes.medium))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    group.competitionTitle,
                    style = MaterialTheme.typography.titleMedium,
                    color = RrrColors.Bone,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "${group.entryNumbers.size} ${if (group.entryNumbers.size == 1) "entry" else "entries"} · " +
                        TimeFormat.date(group.purchasedAtMillis),
                    style = MaterialTheme.typography.labelSmall,
                    color = RrrColors.Mist,
                )
            }
            Spacer(Modifier.width(8.dp))
            when (group.state) {
                TicketState.WON -> StatusPill("won")
                TicketState.NOT_WON -> StatusPill("not_won")
                TicketState.ACTIVE -> ViewPill(onClick = onOpen)
            }
        }

        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            group.entryNumbers.take(6).forEach { number ->
                Box(
                    Modifier
                        .weight(1f)
                        .clip(RrrShapes.small)
                        .background(
                            if (number == group.winningEntryNumber) RrrColors.GoldDim else RrrColors.SurfaceRaised
                        )
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "$number",
                        style = RrrType.NumericSmall,
                        color = if (number == group.winningEntryNumber) RrrColors.GoldBright else RrrColors.Mist,
                    )
                }
            }
            if (group.entryNumbers.size > 6) {
                Box(
                    Modifier.weight(1f).padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "+${group.entryNumbers.size - 6}",
                        style = MaterialTheme.typography.labelSmall,
                        color = RrrColors.Slate,
                    )
                }
            }
        }

        if (group.state == TicketState.ACTIVE && group.closesAtMillis > 0) {
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Draws in ", style = MaterialTheme.typography.labelSmall, color = RrrColors.Slate)
                CountdownText(group.closesAtMillis, showIcon = false, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}
