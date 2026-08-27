package uk.co.rodrunners.raffles.ui.screens.checkout

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stripe.android.paymentsheet.rememberPaymentSheet
import uk.co.rodrunners.raffles.core.Money
import uk.co.rodrunners.raffles.data.model.PriceBreakdown
import uk.co.rodrunners.raffles.payment.PaymentOutcome
import uk.co.rodrunners.raffles.payment.StripePaymentGateway
import uk.co.rodrunners.raffles.payment.toOutcome
import uk.co.rodrunners.raffles.ui.components.Chip
import uk.co.rodrunners.raffles.ui.components.GoldButton
import uk.co.rodrunners.raffles.ui.components.GoldRule
import uk.co.rodrunners.raffles.ui.components.OutlineButton
import uk.co.rodrunners.raffles.ui.components.PrizeImage
import uk.co.rodrunners.raffles.ui.components.QuietButton
import uk.co.rodrunners.raffles.ui.screens.auth.ConsentRow
import uk.co.rodrunners.raffles.ui.screens.auth.RrrTextField
import uk.co.rodrunners.raffles.ui.theme.Dimens
import uk.co.rodrunners.raffles.ui.theme.RrrColors
import uk.co.rodrunners.raffles.ui.theme.RrrShapes
import uk.co.rodrunners.raffles.ui.theme.RrrType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CheckoutScreen(
    gateway: StripePaymentGateway,
    onClose: () -> Unit,
    onOpenTickets: () -> Unit,
    onOpenOrder: (String) -> Unit,
    onOpenRules: () -> Unit,
    viewModel: CheckoutViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    // Swallow the system back gesture while the payment is in flight, for the
    // same reason the toolbar arrow is disabled.
    BackHandler(enabled = !state.canGoBack) { }

    val paymentSheet = rememberPaymentSheet { result ->
        when (val outcome = result.toOutcome()) {
            is PaymentOutcome.SheetCompleted -> viewModel.onSheetCompleted()
            is PaymentOutcome.Cancelled -> viewModel.onSheetCancelled()
            is PaymentOutcome.Failed -> viewModel.onSheetFailed(outcome.message)
        }
    }

    // Present Stripe's sheet the moment the server hands back a client secret.
    val phase = state.phase
    LaunchedEffect(phase) {
        if (phase is PaymentPhase.SheetReady) {
            paymentSheet.presentWithPaymentIntent(
                paymentIntentClientSecret = phase.clientSecret,
                configuration = gateway.configuration(
                    customerName = state.profile?.displayName,
                    email = state.profile?.email,
                ),
            )
        }
    }

    Scaffold(
        containerColor = RrrColors.Ink,
        topBar = {
            TopAppBar(
                title = { Text(state.step.label, style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (state.step == CheckoutStep.REVIEW || state.step == CheckoutStep.COMPLETE) onClose()
                            else viewModel.back()
                        },
                        enabled = state.canGoBack,
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            "Go back",
                            tint = if (state.canGoBack) RrrColors.Bone else RrrColors.Slate,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = RrrColors.Ink,
                    titleContentColor = RrrColors.Bone,
                ),
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
        ) {
            StepIndicator(state.step, Modifier.padding(horizontal = Dimens.gutter, vertical = 8.dp))

            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Dimens.gutter),
            ) {
                when (state.step) {
                    CheckoutStep.REVIEW -> ReviewStep(state, viewModel)
                    CheckoutStep.DETAILS -> DetailsStep(state, viewModel)
                    CheckoutStep.CONFIRM -> ConfirmStep(state, viewModel, onOpenRules)
                    CheckoutStep.PAYMENT -> PaymentStep(state, viewModel)
                    CheckoutStep.COMPLETE -> CompleteStep(state, onOpenTickets, onOpenOrder)
                }
                Spacer(Modifier.height(24.dp))
            }

            CheckoutFooter(state, viewModel, onClose)
        }
    }
}

@Composable
private fun StepIndicator(step: CheckoutStep, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        CheckoutStep.entries.forEach { s ->
            Box(
                Modifier
                    .weight(1f)
                    .height(3.dp)
                    .clip(RrrShapes.extraSmall)
                    .background(if (s.index <= step.index) RrrColors.Gold else RrrColors.Hairline)
            )
        }
    }
}

@Composable
private fun ReviewStep(state: CheckoutState, viewModel: CheckoutViewModel) {
    val competition = state.competition ?: return
    Spacer(Modifier.height(8.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        PrizeImage(
            competition.heroImageUrl, null,
            Modifier.size(64.dp).clip(RrrShapes.medium),
        )
        Spacer(Modifier.width(12.dp))
        Column {
            Text(competition.title, style = MaterialTheme.typography.titleMedium, color = RrrColors.Bone)
            Text(
                "${competition.entriesRemaining} entries left",
                style = MaterialTheme.typography.labelSmall,
                color = RrrColors.Mist,
            )
        }
    }

    Spacer(Modifier.height(28.dp))
    Text("How many entries?", style = MaterialTheme.typography.titleMedium, color = RrrColors.Bone)
    Spacer(Modifier.height(12.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        state.presetQuantities.forEach { q ->
            Chip(
                text = q.toString(),
                selected = state.quantity == q,
                onClick = { viewModel.setQuantity(q) },
            )
        }
    }
    Spacer(Modifier.height(12.dp))
    var custom by remember { mutableStateOf("") }
    RrrTextField(
        value = custom,
        onValueChange = { input ->
            custom = input.filter { it.isDigit() }.take(4)
            custom.toIntOrNull()?.let(viewModel::setQuantity)
        },
        label = "Or enter a quantity (max ${state.maxSelectable})",
        keyboardType = KeyboardType.Number,
    )
    if (state.alreadyHeld > 0) {
        Spacer(Modifier.height(8.dp))
        Text(
            "You already hold ${state.alreadyHeld} entries in this raffle. The limit is ${competition.maxEntriesPerCustomer}.",
            style = MaterialTheme.typography.labelSmall,
            color = RrrColors.Mist,
        )
    }

    Spacer(Modifier.height(28.dp))
    PromoField(state, viewModel)

    Spacer(Modifier.height(24.dp))
    PriceSummary(state)
}

@Composable
private fun PromoField(state: CheckoutState, viewModel: CheckoutViewModel) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f)) {
            RrrTextField(
                value = state.promoInput,
                onValueChange = viewModel::onPromoInput,
                label = "Discount code (optional)",
            )
        }
        Spacer(Modifier.width(8.dp))
        OutlineButton(
            text = if (state.breakdown?.promoCode != null) "Remove" else "Apply",
            onClick = { if (state.breakdown?.promoCode != null) viewModel.clearPromo() else viewModel.applyPromo() },
            enabled = state.promoInput.isNotBlank() && !state.quoting,
        )
    }
}

/** Figures come from the server's breakdown. No quote, no total. */
@Composable
private fun PriceSummary(state: CheckoutState) {
    val b: PriceBreakdown? = state.breakdown
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RrrShapes.large)
            .background(RrrColors.Surface)
            .padding(16.dp)
    ) {
        if (state.quoting) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = RrrColors.Gold)
                Spacer(Modifier.width(10.dp))
                Text("Checking price…", style = MaterialTheme.typography.bodySmall, color = RrrColors.Mist)
            }
            return@Column
        }
        if (b == null) {
            Text(
                state.quoteError?.message ?: "We couldn't price this basket. Try again in a moment.",
                style = MaterialTheme.typography.bodySmall,
                color = RrrColors.Danger,
            )
            return@Column
        }

        SummaryLine("Entry price", Money.format(b.unitPricePence))
        SummaryLine("Quantity", b.quantity.toString())
        if (b.bundleDiscountPence > 0) {
            SummaryLine(
                b.bundleLabel?.let { "Bundle ($it)" } ?: "Bundle discount",
                "−${Money.format(b.bundleDiscountPence)}",
                valueColour = RrrColors.Success,
            )
        }
        SummaryLine("Subtotal", Money.format(b.subtotalPence - b.bundleDiscountPence))
        if (b.promoDiscountPence > 0) {
            SummaryLine("Code ${b.promoCode}", "−${Money.format(b.promoDiscountPence)}", valueColour = RrrColors.Success)
        }
        if (b.feePence > 0) SummaryLine("Booking fee", Money.format(b.feePence))

        Spacer(Modifier.height(10.dp))
        HorizontalDivider(thickness = 1.dp, color = RrrColors.Hairline)
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Total", style = MaterialTheme.typography.titleMedium, color = RrrColors.Bone)
            Text(
                Money.format(b.totalPence),
                style = RrrType.Numeric,
                color = RrrColors.Gold,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun SummaryLine(
    label: String,
    value: String,
    valueColour: androidx.compose.ui.graphics.Color = RrrColors.Bone,
) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 5.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = RrrColors.Mist)
        Text(value, style = MaterialTheme.typography.bodyMedium, color = valueColour)
    }
}

@Composable
private fun DetailsStep(state: CheckoutState, viewModel: CheckoutViewModel) {
    var name by remember { mutableStateOf(state.profile?.displayName.orEmpty()) }
    var phone by remember { mutableStateOf(state.profile?.phone.orEmpty()) }

    Spacer(Modifier.height(8.dp))
    Text(
        "We use these to contact you if you win. Your card details are handled by Stripe and never reach us.",
        style = MaterialTheme.typography.bodyMedium,
        color = RrrColors.Mist,
    )
    Spacer(Modifier.height(20.dp))
    RrrTextField(name, { name = it }, "Your name")
    Spacer(Modifier.height(12.dp))
    RrrTextField(phone, { phone = it }, "Phone number", keyboardType = KeyboardType.Phone)
    Spacer(Modifier.height(12.dp))
    RrrTextField(state.profile?.email.orEmpty(), {}, "Email address (from your account)")
    state.detailsError?.let {
        Spacer(Modifier.height(12.dp))
        Text(it.message, style = MaterialTheme.typography.bodySmall, color = RrrColors.Danger)
    }
    Spacer(Modifier.height(20.dp))
    GoldButton(
        text = "Save and continue",
        onClick = { viewModel.updateDetails(name, phone) },
        enabled = name.trim().length >= 2 && !state.savingDetails,
        loading = state.savingDetails,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun ConfirmStep(state: CheckoutState, viewModel: CheckoutViewModel, onOpenRules: () -> Unit) {
    val c = state.competition ?: return
    Spacer(Modifier.height(8.dp))
    PriceSummary(state)
    Spacer(Modifier.height(24.dp))
    Text("Before you pay", style = MaterialTheme.typography.titleMedium, color = RrrColors.Bone)
    Spacer(Modifier.height(12.dp))
    ConsentRow(
        checked = state.ageConfirmed,
        onCheckedChange = viewModel::setAgeConfirmed,
        text = "I am ${c.minimumAge} or over and eligible to enter under the rules for this raffle.",
    )
    ConsentRow(
        checked = state.rulesAccepted,
        onCheckedChange = viewModel::setRulesAccepted,
        text = "I've read the competition rules, including how the winner is drawn.",
        actionLabel = "Read",
        onAction = onOpenRules,
    )
    Spacer(Modifier.height(16.dp))
    Text(
        "Your entry numbers are issued once the payment clears, and appear in My Tickets. " +
            "If the payment doesn't complete, the numbers are released and you aren't charged.",
        style = MaterialTheme.typography.bodySmall,
        color = RrrColors.Slate,
    )
}

@Composable
private fun PaymentStep(state: CheckoutState, viewModel: CheckoutViewModel) {
    Spacer(Modifier.height(40.dp))
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        when (val phase = state.phase) {
            is PaymentPhase.CreatingOrder -> {
                CircularProgressIndicator(color = RrrColors.Gold)
                Spacer(Modifier.height(20.dp))
                Text("Reserving your entry numbers…", style = MaterialTheme.typography.bodyMedium, color = RrrColors.Mist)
            }
            is PaymentPhase.SheetReady -> {
                CircularProgressIndicator(color = RrrColors.Gold)
                Spacer(Modifier.height(20.dp))
                Text("Opening secure payment…", style = MaterialTheme.typography.bodyMedium, color = RrrColors.Mist)
            }
            is PaymentPhase.AwaitingConfirmation -> {
                CircularProgressIndicator(color = RrrColors.Gold)
                Spacer(Modifier.height(20.dp))
                Text("Confirming with your bank", style = MaterialTheme.typography.titleMedium, color = RrrColors.Bone)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Don't close the app. We're waiting for the payment to be confirmed on our side before issuing your numbers.",
                    style = MaterialTheme.typography.bodySmall,
                    color = RrrColors.Mist,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
            is PaymentPhase.Failed -> {
                Text(phase.error.title, style = MaterialTheme.typography.titleLarge, color = RrrColors.Bone)
                Spacer(Modifier.height(10.dp))
                Text(
                    phase.error.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = RrrColors.Mist,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
                if (phase.error.retryable) {
                    Spacer(Modifier.height(24.dp))
                    OutlineButton("Try payment again", viewModel::retryPayment)
                }
            }
            else -> {
                CircularProgressIndicator(color = RrrColors.Gold)
            }
        }
    }
}

@Composable
private fun CompleteStep(
    state: CheckoutState,
    onOpenTickets: () -> Unit,
    onOpenOrder: (String) -> Unit,
) {
    val order = (state.phase as? PaymentPhase.Confirmed)?.order ?: return
    Spacer(Modifier.height(24.dp))
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        GoldRule(width = 32.dp)
        Spacer(Modifier.height(16.dp))
        Text("You're in the draw", style = MaterialTheme.typography.headlineSmall, color = RrrColors.Bone)
        Spacer(Modifier.height(8.dp))
        Text(
            "Order ${order.orderNumber} — ${Money.format(order.totalPence)} paid",
            style = MaterialTheme.typography.bodyMedium,
            color = RrrColors.Mist,
        )
    }

    Spacer(Modifier.height(28.dp))
    Text("Your entry numbers", style = MaterialTheme.typography.titleMedium, color = RrrColors.Bone)
    Spacer(Modifier.height(12.dp))
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RrrShapes.large)
            .background(RrrColors.Surface)
            .padding(16.dp)
    ) {
        order.entryNumbers.chunked(5).forEach { row ->
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { number ->
                    Box(
                        Modifier
                            .weight(1f)
                            .clip(RrrShapes.small)
                            .background(RrrColors.SurfaceRaised)
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("$number", style = RrrType.NumericSmall, color = RrrColors.Gold)
                    }
                }
                repeat(5 - row.size) { Box(Modifier.weight(1f)) }
            }
        }
    }

    Spacer(Modifier.height(20.dp))
    Text(
        "A confirmation email is on its way. You'll get a notification when this raffle is drawn, win or lose.",
        style = MaterialTheme.typography.bodySmall,
        color = RrrColors.Mist,
    )
    Spacer(Modifier.height(24.dp))
    GoldButton("View my tickets", onOpenTickets, Modifier.fillMaxWidth())
    Spacer(Modifier.height(8.dp))
    QuietButton("See order details") { onOpenOrder(order.id) }
}

@Composable
private fun CheckoutFooter(
    state: CheckoutState,
    viewModel: CheckoutViewModel,
    onClose: () -> Unit,
) {
    Column(
        Modifier
            .background(RrrColors.Ink)
            .navigationBarsPadding()
            .padding(horizontal = Dimens.gutter, vertical = 12.dp)
    ) {
        when (state.step) {
            CheckoutStep.REVIEW -> GoldButton(
                text = state.breakdown?.let { "Continue — ${Money.format(it.totalPence)}" } ?: "Continue",
                onClick = { viewModel.goTo(CheckoutStep.DETAILS) },
                enabled = state.canContinueFromReview,
                loading = state.quoting,
                modifier = Modifier.fillMaxWidth(),
            )
            CheckoutStep.CONFIRM -> GoldButton(
                text = state.breakdown?.let { "Pay ${Money.format(it.totalPence)}" } ?: "Pay",
                onClick = viewModel::beginPayment,
                enabled = state.canConfirm && state.breakdown != null,
                modifier = Modifier.fillMaxWidth(),
            )
            CheckoutStep.COMPLETE -> QuietButton("Back to raffles", onClose)
            else -> Unit
        }
    }
}
