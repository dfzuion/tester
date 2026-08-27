package uk.co.rodrunners.raffles.ui.screens.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import uk.co.rodrunners.raffles.core.Money
import uk.co.rodrunners.raffles.core.TimeFormat
import uk.co.rodrunners.raffles.data.model.InstantWinClaim
import uk.co.rodrunners.raffles.ui.components.Chip
import uk.co.rodrunners.raffles.ui.components.GoldButton
import uk.co.rodrunners.raffles.ui.components.QuietButton
import uk.co.rodrunners.raffles.ui.components.SectionHeader
import uk.co.rodrunners.raffles.ui.screens.auth.RrrTextField
import uk.co.rodrunners.raffles.ui.theme.Dimens
import uk.co.rodrunners.raffles.ui.theme.RrrColors
import uk.co.rodrunners.raffles.ui.theme.RrrShapes

private fun poundsToPence(text: String): Int {
    val v = text.replace("£", "").replace(",", "").trim().toDoubleOrNull() ?: return 0
    return Math.round(v * 100).toInt()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InstantWinsScreen(
    competitionId: String,
    onBack: () -> Unit,
    viewModel: InstantWinsViewModel = hiltViewModel(),
) {
    LaunchedEffect(competitionId) { viewModel.start(competitionId) }
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text("Instant wins", style = MaterialTheme.typography.titleLarge) },
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
            Modifier.fillMaxSize().padding(padding).imePadding(),
            contentPadding = PaddingValues(Dimens.gutter),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    "Each prize is sealed to one entry number, picked at random from the numbers " +
                        "nobody has bought yet. Not even this screen can show you which ones.",
                    style = MaterialTheme.typography.bodySmall,
                    color = RrrColors.Mist,
                )
            }

            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatTile("Still hidden", state.overview.unclaimedCount.toString(), Modifier.weight(1f))
                    StatTile("Prize value out", Money.format(state.overview.unclaimedValuePence), Modifier.weight(1f))
                }
            }

            item { Spacer(Modifier.height(8.dp)); SectionHeader("Add prizes") }
            item {
                RrrTextField(state.newPrizeName, viewModel::onName, "Prize name")
            }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    RrrTextField(
                        if (state.newPrizeValuePence == 0) "" else String.format("%.2f", state.newPrizeValuePence / 100.0),
                        { v -> viewModel.onValuePence(poundsToPence(v)) },
                        "Value (£)", modifier = Modifier.weight(1f), keyboardType = KeyboardType.Decimal,
                    )
                    RrrTextField(
                        state.newPrizeQuantity.toString(),
                        { v -> viewModel.onQuantity(v.filter { c -> c.isDigit() }.toIntOrNull() ?: 1) },
                        "How many", modifier = Modifier.weight(1f), keyboardType = KeyboardType.Number,
                    )
                }
            }
            item {
                GoldButton(
                    text = "Hide them in the raffle",
                    onClick = { viewModel.addPrizes() },
                    enabled = state.canAdd,
                    loading = state.busy,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            state.error?.let { err ->
                item { Text(err.message, style = MaterialTheme.typography.bodySmall, color = RrrColors.Danger) }
            }
            state.message?.let { msg ->
                item { Text(msg, style = MaterialTheme.typography.bodySmall, color = RrrColors.Success) }
            }

            if (state.overview.stock.isNotEmpty()) {
                item { Spacer(Modifier.height(8.dp)); SectionHeader("Waiting to be won") }
                items(state.overview.stock, key = { it.prizeName + it.valuePence }) { stock ->
                    Column(
                        Modifier.fillMaxWidth().clip(RrrShapes.large)
                            .background(RrrColors.Surface).padding(14.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(stock.prizeName, style = MaterialTheme.typography.titleMedium, color = RrrColors.Bone)
                                Text(
                                    "${stock.count} left · ${Money.format(stock.valuePence)} each",
                                    style = MaterialTheme.typography.labelSmall, color = RrrColors.Mist,
                                )
                            }
                            QuietButton("Remove one", onClick = { viewModel.removePrizes(stock.prizeName, 1) })
                        }
                    }
                }
            }

            if (state.overview.claims.isNotEmpty()) {
                item { Spacer(Modifier.height(8.dp)); SectionHeader("Won — to send out") }
                items(state.overview.claims, key = { it.id }) { claim ->
                    ClaimCard(claim) { status -> viewModel.setClaimStatus(claim.id, status) }
                }
            }

            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun ClaimCard(claim: InstantWinClaim, onStatus: (String) -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RrrShapes.large).background(RrrColors.Surface).padding(14.dp)
    ) {
        Text(claim.prizeName, style = MaterialTheme.typography.titleMedium, color = RrrColors.Bone)
        Text(
            "Entry ${claim.entryNumber} · ${claim.wonByName ?: "Customer"} · ${Money.format(claim.valuePence)}",
            style = MaterialTheme.typography.labelSmall, color = RrrColors.Mist,
        )
        claim.wonAtMillis?.let {
            Text("Won ${TimeFormat.full(it)}", style = MaterialTheme.typography.labelSmall, color = RrrColors.Slate)
        }
        Spacer(Modifier.height(10.dp))
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            InstantWinClaim.STATUSES.forEach { (key, label) ->
                Chip(label, claim.claimStatus == key, onClick = { onStatus(key) })
            }
        }
    }
}
