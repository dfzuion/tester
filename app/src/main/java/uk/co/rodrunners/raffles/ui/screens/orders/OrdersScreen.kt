package uk.co.rodrunners.raffles.ui.screens.orders

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import uk.co.rodrunners.raffles.core.Money
import uk.co.rodrunners.raffles.core.TimeFormat
import uk.co.rodrunners.raffles.data.model.Order
import uk.co.rodrunners.raffles.ui.components.PrizeImage
import uk.co.rodrunners.raffles.ui.components.QuietButton
import uk.co.rodrunners.raffles.ui.components.StateHost
import uk.co.rodrunners.raffles.ui.components.StatusPill
import uk.co.rodrunners.raffles.ui.theme.Dimens
import uk.co.rodrunners.raffles.ui.theme.RrrColors
import uk.co.rodrunners.raffles.ui.theme.RrrShapes
import uk.co.rodrunners.raffles.ui.theme.RrrType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrdersScreen(
    onBack: () -> Unit,
    onOpenOrder: (String) -> Unit,
    onBrowse: () -> Unit,
    viewModel: OrdersViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text("My orders", style = MaterialTheme.typography.titleLarge) },
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
        StateHost(
            state = state,
            modifier = Modifier.padding(padding),
            onRetry = viewModel::load,
            onEmptyAction = onBrowse,
        ) { orders ->
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = Dimens.gutter, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(Dimens.cardGap),
            ) {
                items(orders, key = { it.id }) { order ->
                    OrderRow(order) { onOpenOrder(order.id) }
                }
            }
        }
    }
}

@Composable
private fun OrderRow(order: Order, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RrrShapes.large)
            .background(RrrColors.Surface)
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PrizeImage(order.competitionImageUrl, null, Modifier.size(56.dp).clip(RrrShapes.medium))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                order.competitionTitle,
                style = MaterialTheme.typography.titleMedium,
                color = RrrColors.Bone,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "${order.orderNumber} · ${TimeFormat.date(order.createdAtMillis)} · ${order.quantity} entries",
                style = MaterialTheme.typography.labelSmall,
                color = RrrColors.Mist,
            )
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(Money.format(order.totalPence), style = RrrType.NumericSmall, color = RrrColors.Bone)
            Spacer(Modifier.height(6.dp))
            StatusPill(if (order.isPaid) "live" else order.paymentStatus)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrderDetailScreen(
    onBack: () -> Unit,
    onOpenSupport: (String) -> Unit,
    viewModel: OrderDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text("Order", style = MaterialTheme.typography.titleLarge) },
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
        StateHost(state = state, modifier = Modifier.padding(padding), onRetry = viewModel::load) { order ->
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(Dimens.gutter)
            ) {
                Text(order.orderNumber, style = MaterialTheme.typography.headlineSmall, color = RrrColors.Bone)
                Spacer(Modifier.height(4.dp))
                Text(
                    TimeFormat.full(order.createdAtMillis),
                    style = MaterialTheme.typography.bodySmall,
                    color = RrrColors.Mist,
                )
                Spacer(Modifier.height(16.dp))
                StatusPill(if (order.isPaid) "live" else order.paymentStatus)

                Spacer(Modifier.height(28.dp))
                Text(order.competitionTitle, style = MaterialTheme.typography.titleLarge, color = RrrColors.Bone)

                Spacer(Modifier.height(20.dp))
                order.breakdown?.let { b ->
                    DetailLine("Entry price", Money.format(b.unitPricePence))
                    DetailLine("Quantity", b.quantity.toString())
                    if (b.bundleDiscountPence > 0) DetailLine("Bundle discount", "−${Money.format(b.bundleDiscountPence)}")
                    if (b.promoDiscountPence > 0) DetailLine("Code ${b.promoCode}", "−${Money.format(b.promoDiscountPence)}")
                    if (b.feePence > 0) DetailLine("Booking fee", Money.format(b.feePence))
                }
                DetailLine("Payment status", order.statusLabel)
                if (order.refundedPence > 0) DetailLine("Refunded", Money.format(order.refundedPence))
                HorizontalDivider(thickness = 1.dp, color = RrrColors.Hairline)
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Total", style = MaterialTheme.typography.titleMedium, color = RrrColors.Bone)
                    Text(Money.format(order.totalPence), style = RrrType.Numeric, color = RrrColors.Gold)
                }

                if (order.hasInstantWins) {
                    Spacer(Modifier.height(28.dp))
                    Text("Instant wins", style = MaterialTheme.typography.titleMedium, color = RrrColors.Bone)
                    Spacer(Modifier.height(8.dp))
                    order.instantWins.forEach { win ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(win.prizeName, style = MaterialTheme.typography.bodyMedium, color = RrrColors.Bone)
                            Text(
                                "No. ${win.entryNumber} · ${Money.format(win.valuePence)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = RrrColors.Gold,
                            )
                        }
                    }
                }

                if (order.entryNumbers.isNotEmpty()) {
                    Spacer(Modifier.height(28.dp))
                    Text("Entry numbers", style = MaterialTheme.typography.titleMedium, color = RrrColors.Bone)
                    Spacer(Modifier.height(12.dp))
                    Text(
                        order.entryNumbers.joinToString(", "),
                        style = MaterialTheme.typography.bodyMedium,
                        color = RrrColors.Gold,
                    )
                }

                Spacer(Modifier.height(32.dp))
                QuietButton("Problem with this order?", onClick = { onOpenSupport(order.id) })
                Spacer(Modifier.height(40.dp))
            }
        }
    }
}

@Composable
private fun DetailLine(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = RrrColors.Mist)
        Text(value, style = MaterialTheme.typography.bodyMedium, color = RrrColors.Bone)
    }
}
