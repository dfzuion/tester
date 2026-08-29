package uk.co.rodrunners.raffles.ui.screens.credit

import androidx.compose.foundation.background
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import uk.co.rodrunners.raffles.core.Money
import uk.co.rodrunners.raffles.core.TimeFormat
import uk.co.rodrunners.raffles.ui.components.GoldButton
import uk.co.rodrunners.raffles.ui.components.QuietButton
import uk.co.rodrunners.raffles.ui.components.SectionHeader
import uk.co.rodrunners.raffles.ui.screens.auth.RrrTextField
import uk.co.rodrunners.raffles.ui.theme.Dimens
import uk.co.rodrunners.raffles.ui.theme.RrrColors
import uk.co.rodrunners.raffles.ui.theme.RrrShapes
import uk.co.rodrunners.raffles.ui.theme.RrrType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreditScreen(
    onBack: () -> Unit,
    onOpenSpin: () -> Unit = {},
    viewModel: CreditViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text("My credit", style = MaterialTheme.typography.titleLarge) },
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
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RrrShapes.large)
                        .background(RrrColors.GoldDim)
                        .padding(vertical = 26.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        "BALANCE",
                        style = RrrType.Eyebrow,
                        color = RrrColors.GoldBright,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        Money.format(state.balancePence),
                        style = MaterialTheme.typography.displayLarge,
                        color = RrrColors.Bone,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Spend it on entries at checkout",
                        style = MaterialTheme.typography.bodySmall,
                        color = RrrColors.Mist,
                    )
                }
            }

            item { Spacer(Modifier.height(4.dp)); SectionHeader("Free credit") }
            item {
                QuietButton("Take today's spin", onOpenSpin)
            }

            item { Spacer(Modifier.height(4.dp)); SectionHeader("Got a code?") }
            item {
                RrrTextField(state.couponCode, viewModel::onCode, "Coupon code")
            }
            item {
                GoldButton(
                    text = "Add credit",
                    onClick = { viewModel.redeem() },
                    enabled = state.canRedeem,
                    loading = state.redeeming,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            state.message?.let { msg ->
                item { Text(msg, style = MaterialTheme.typography.bodyMedium, color = RrrColors.Success) }
            }
            state.error?.let { err ->
                item { Text(err.message, style = MaterialTheme.typography.bodySmall, color = RrrColors.Danger) }
            }

            item { Spacer(Modifier.height(8.dp)); SectionHeader("History") }
            if (state.ledger.isEmpty()) {
                item {
                    Text(
                        "Nothing yet. Credit shows up here when you redeem a code, win it as an instant prize, or a friend you referred makes their first order.",
                        style = MaterialTheme.typography.bodySmall,
                        color = RrrColors.Mist,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp),
                    )
                }
            }
            items(state.ledger, key = { it.id }) { entry ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RrrShapes.medium)
                        .background(RrrColors.Surface)
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(entry.reasonLabel, style = MaterialTheme.typography.titleMedium, color = RrrColors.Bone)
                        Text(
                            entry.description,
                            style = MaterialTheme.typography.labelSmall,
                            color = RrrColors.Mist,
                        )
                        if (entry.createdAtMillis > 0) {
                            Text(
                                TimeFormat.full(entry.createdAtMillis),
                                style = MaterialTheme.typography.labelSmall,
                                color = RrrColors.Slate,
                            )
                        }
                    }
                    Text(
                        (if (entry.isCredit) "+" else "") + Money.format(entry.deltaPence),
                        style = RrrType.Numeric,
                        color = if (entry.isCredit) RrrColors.Gold else RrrColors.Mist,
                    )
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}
