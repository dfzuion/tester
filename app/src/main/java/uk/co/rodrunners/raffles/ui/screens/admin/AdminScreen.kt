package uk.co.rodrunners.raffles.ui.screens.admin

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import uk.co.rodrunners.raffles.BuildConfig
import uk.co.rodrunners.raffles.core.Money
import uk.co.rodrunners.raffles.core.TimeFormat
import uk.co.rodrunners.raffles.data.model.Competition
import uk.co.rodrunners.raffles.ui.components.GoldButton
import uk.co.rodrunners.raffles.ui.components.QuietButton
import uk.co.rodrunners.raffles.ui.components.OutlineButton
import uk.co.rodrunners.raffles.ui.components.SectionHeader
import uk.co.rodrunners.raffles.ui.components.StatusPill
import uk.co.rodrunners.raffles.ui.theme.Dimens
import uk.co.rodrunners.raffles.ui.theme.RrrColors
import uk.co.rodrunners.raffles.ui.theme.RrrShapes
import uk.co.rodrunners.raffles.ui.theme.RrrType

/**
 * Each action calls a Cloud Function that re-checks the admin claim and writes
 * an audit entry. The UI has no privileged access of its own.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminScreen(
    onBack: () -> Unit,
    onOpenCompetition: (String) -> Unit,
    onNewCompetition: () -> Unit = {},
    onEditCompetition: (String) -> Unit = {},
    onOpenInstantWins: (String) -> Unit = {},
    onOpenAdminUsers: () -> Unit = {},
    onOpenCoupons: () -> Unit = {},
    viewModel: AdminViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var confirming by remember { mutableStateOf<Pair<String, Competition>?>(null) }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text("Admin", style = MaterialTheme.typography.titleLarge) },
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
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(Dimens.gutter),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            state.stats?.let { stats ->
                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        StatTile("Live raffles", stats.liveCompetitions.toString(), Modifier.weight(1f))
                        StatTile("Entries sold", stats.entriesSold.toString(), Modifier.weight(1f))
                    }
                }
                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        StatTile("Revenue", Money.format(stats.revenuePence), Modifier.weight(1f))
                        StatTile("Drawn", stats.completedCompetitions.toString(), Modifier.weight(1f))
                    }
                }
                if (stats.failedPayments > 0) {
                    item {
                        Text(
                            "${stats.failedPayments} failed payment(s) recorded — reserved numbers from these were released automatically.",
                            style = MaterialTheme.typography.bodySmall,
                            color = RrrColors.Warning,
                        )
                    }
                }
            }

            item {
                Spacer(Modifier.height(8.dp))
                SectionHeader("Raffles")
            }

            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    QuietButton("Administrators", onClick = onOpenAdminUsers)
                    QuietButton("Credit and coupons", onClick = onOpenCoupons)
                }
            }

            item {
                GoldButton(
                    text = "New raffle",
                    onClick = onNewCompetition,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            items(state.competitions, key = { it.id }) { competition ->
                AdminCompetitionCard(
                    competition = competition,
                    busy = state.busyCompetitionId == competition.id,
                    onOpen = { onOpenCompetition(competition.id) },
                    onEdit = { onEditCompetition(competition.id) },
                    onInstantWins = { onOpenInstantWins(competition.id) },
                    onAction = { action -> confirming = action to competition },
                )
            }

            if (BuildConfig.ALLOW_DEMO_SEED) {
                item {
                    Spacer(Modifier.height(20.dp))
                    SectionHeader("Demo data")
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Seeds demonstration raffles flagged isDemo. The function refuses to run against a " +
                            "production project, so this cannot pollute live data.",
                        style = MaterialTheme.typography.bodySmall,
                        color = RrrColors.Mist,
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlineButton("Seed demo data", viewModel::seedDemoData)
                    Spacer(Modifier.height(40.dp))
                }
            }
        }
    }

    confirming?.let { (action, competition) ->
        AlertDialog(
            onDismissRequest = { confirming = null },
            containerColor = RrrColors.Surface,
            title = { Text(confirmTitle(action), color = RrrColors.Bone) },
            text = { Text(confirmBody(action, competition), color = RrrColors.Mist) },
            confirmButton = {
                TextButton(onClick = {
                    when (action) {
                        "publish" -> viewModel.setStatus(competition.id, "live")
                        "pause" -> viewModel.setStatus(competition.id, "paused")
                        "close" -> viewModel.close(competition.id)
                        "draw" -> viewModel.draw(competition.id)
                        "publish_result" -> viewModel.publish(competition.id)
                    }
                    confirming = null
                }) { Text("Confirm", color = RrrColors.Gold) }
            },
            dismissButton = {
                TextButton(onClick = { confirming = null }) { Text("Cancel", color = RrrColors.Mist) }
            },
        )
    }

    state.lastDraw?.let { draw ->
        AlertDialog(
            onDismissRequest = viewModel::clearMessage,
            containerColor = RrrColors.Surface,
            title = { Text("Draw complete", color = RrrColors.Bone) },
            text = {
                Column {
                    Text("Winning entry ${draw.winningEntryNumber}", color = RrrColors.Gold, style = RrrType.Numeric)
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "${draw.eligibleEntryCount} eligible entries. Seed hash ${draw.seedHash.take(16)}… recorded in the audit log. " +
                            "The result is not visible to customers until you publish it.",
                        color = RrrColors.Mist,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = viewModel::clearMessage) { Text("Close", color = RrrColors.Gold) }
            },
        )
    }

    state.error?.let { error ->
        AlertDialog(
            onDismissRequest = viewModel::clearMessage,
            containerColor = RrrColors.Surface,
            title = { Text(error.title, color = RrrColors.Bone) },
            text = { Text(error.message, color = RrrColors.Mist) },
            confirmButton = {
                TextButton(onClick = viewModel::clearMessage) { Text("Close", color = RrrColors.Gold) }
            },
        )
    }
}

private fun confirmTitle(action: String) = when (action) {
    "publish" -> "Make this raffle live?"
    "pause" -> "Pause entries?"
    "close" -> "Close entries?"
    "draw" -> "Draw the winner?"
    else -> "Publish the result?"
}

private fun confirmBody(action: String, competition: Competition) = when (action) {
    "publish" -> "${competition.title} becomes visible and enterable by customers."
    "pause" -> "Existing entries stand, but no new ones can be bought."
    "close" -> "No further entries can be bought for ${competition.title}. This can't be reversed."
    "draw" -> "A winner is selected from ${competition.entriesSold} sold entries using a server-generated random seed. " +
        "The draw is recorded permanently and can't be re-run."
    else -> "Customers will see the winner and the winning number, and everyone who entered gets a notification."
}

@Composable
internal fun StatTile(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(RrrShapes.large)
            .background(RrrColors.Surface)
            .padding(16.dp)
    ) {
        Text(value, style = RrrType.Numeric, color = RrrColors.Gold)
        Spacer(Modifier.height(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = RrrColors.Mist)
    }
}

@Composable
private fun AdminCompetitionCard(
    competition: Competition,
    busy: Boolean,
    onOpen: () -> Unit,
    onEdit: () -> Unit = {},
    onInstantWins: () -> Unit = {},
    onAction: (String) -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RrrShapes.large)
            .background(RrrColors.Surface)
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(competition.title, style = MaterialTheme.typography.titleMedium, color = RrrColors.Bone)
                Spacer(Modifier.height(4.dp))
                Text(
                    "${competition.entriesSold}/${competition.maxEntries} sold · " +
                        Money.format(competition.entriesSold * competition.entryPricePence) + " gross",
                    style = MaterialTheme.typography.labelSmall,
                    color = RrrColors.Mist,
                )
                if (competition.closesAtMillis > 0) {
                    Text(
                        "Closes ${TimeFormat.full(competition.closesAtMillis)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = RrrColors.Slate,
                    )
                }
            }
            StatusPill(competition.status)
        }

        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            when (competition.status) {
                "draft", "scheduled" -> OutlineButton("Make live", { onAction("publish") }, enabled = !busy)
                "live" -> {
                    OutlineButton("Pause", { onAction("pause") }, enabled = !busy)
                    OutlineButton("Close entries", { onAction("close") }, enabled = !busy)
                }
                "paused" -> OutlineButton("Resume", { onAction("publish") }, enabled = !busy)
                "closed" -> GoldButton("Draw winner", { onAction("draw") }, enabled = !busy)
                "drawn" -> GoldButton("Publish result", { onAction("publish_result") }, enabled = !busy)
                else -> Unit
            }
            OutlineButton("Edit", onEdit, enabled = !busy)
            OutlineButton("Instant wins", onInstantWins, enabled = !busy)
            OutlineButton("View", onOpen, enabled = !busy)
        }
    }
}
