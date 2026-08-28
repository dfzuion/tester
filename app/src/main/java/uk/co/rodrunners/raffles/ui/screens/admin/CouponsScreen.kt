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

private fun penceToPounds(pence: Int): String =
    if (pence == 0) "" else String.format("%.2f", pence / 100.0)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CouponsScreen(
    onBack: () -> Unit,
    viewModel: CouponsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text("Credit and coupons", style = MaterialTheme.typography.titleLarge) },
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
            item { SectionHeader("New coupon") }
            item {
                Text(
                    "Each customer can use a code once. Credit buys entries and can't be cashed out.",
                    style = MaterialTheme.typography.bodySmall,
                    color = RrrColors.Mist,
                )
            }
            item { RrrTextField(state.code, viewModel::onCode, "Code (e.g. WELCOME5)") }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    RrrTextField(
                        penceToPounds(state.valuePence),
                        { v -> viewModel.onValue(poundsToPence(v)) },
                        "Value (£)", modifier = Modifier.weight(1f), keyboardType = KeyboardType.Decimal,
                    )
                    RrrTextField(
                        if (state.maxRedemptions == 0) "" else state.maxRedemptions.toString(),
                        { v -> viewModel.onMax(v.filter { c -> c.isDigit() }.toIntOrNull() ?: 0) },
                        "Max uses", modifier = Modifier.weight(1f), keyboardType = KeyboardType.Number,
                        supportingText = "0 = unlimited",
                    )
                }
            }
            item {
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    listOf(7 to "1 week", 30 to "1 month", 90 to "3 months", 0 to "No expiry").forEach { (days, label) ->
                        Chip(label, state.expiryDays == days, onClick = { viewModel.onExpiryDays(days) })
                    }
                }
            }
            item {
                GoldButton(
                    text = "Create coupon",
                    onClick = { viewModel.create() },
                    enabled = state.canCreate,
                    loading = state.busy,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            item { Spacer(Modifier.height(8.dp)); SectionHeader("Give a customer credit") }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    RrrTextField(
                        state.grantEmail, viewModel::onGrantEmail, "Their email",
                        modifier = Modifier.weight(1.4f), keyboardType = KeyboardType.Email,
                    )
                    RrrTextField(
                        penceToPounds(state.grantPence),
                        { v -> viewModel.onGrantPence(poundsToPence(v)) },
                        "Amount (£)", modifier = Modifier.weight(1f), keyboardType = KeyboardType.Decimal,
                    )
                }
            }
            item {
                QuietButton("Add the credit", onClick = { viewModel.grant() })
            }

            state.message?.let { msg ->
                item { Text(msg, style = MaterialTheme.typography.bodySmall, color = RrrColors.Success) }
            }
            state.error?.let { err ->
                item { Text(err.message, style = MaterialTheme.typography.bodySmall, color = RrrColors.Danger) }
            }

            item { Spacer(Modifier.height(8.dp)); SectionHeader("Existing codes") }
            items(state.coupons, key = { it.code }) { coupon ->
                Column(
                    Modifier.fillMaxWidth().clip(RrrShapes.large)
                        .background(RrrColors.Surface).padding(14.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(coupon.code, style = MaterialTheme.typography.titleMedium, color = RrrColors.Bone)
                            Text(
                                "${Money.format(coupon.valuePence)} · used ${coupon.redemptionsUsed}" +
                                    (coupon.remaining?.let { " of ${coupon.maxRedemptions}" } ?: "") +
                                    " · ${coupon.statusLabel}",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (coupon.active) RrrColors.Mist else RrrColors.Slate,
                            )
                        }
                        QuietButton(
                            if (coupon.active) "Switch off" else "Switch on",
                            onClick = { viewModel.toggle(coupon) },
                        )
                    }
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}
