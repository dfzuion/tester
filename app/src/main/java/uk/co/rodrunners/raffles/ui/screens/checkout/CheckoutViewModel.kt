package uk.co.rodrunners.raffles.ui.screens.checkout

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import uk.co.rodrunners.raffles.core.AppError
import uk.co.rodrunners.raffles.core.Errors
import uk.co.rodrunners.raffles.data.model.Competition
import uk.co.rodrunners.raffles.data.model.Order
import uk.co.rodrunners.raffles.data.model.PriceBreakdown
import uk.co.rodrunners.raffles.data.model.UserProfile
import uk.co.rodrunners.raffles.data.repository.AccountRepository
import uk.co.rodrunners.raffles.data.repository.AuthRepository
import uk.co.rodrunners.raffles.data.repository.CheckoutRepository
import uk.co.rodrunners.raffles.data.repository.CompetitionRepository
import uk.co.rodrunners.raffles.data.repository.EntryRepository
import uk.co.rodrunners.raffles.data.repository.OrderRepository

enum class CheckoutStep(val index: Int, val label: String) {
    REVIEW(0, "Your entries"),
    DETAILS(1, "Your details"),
    CONFIRM(2, "Confirm"),
    PAYMENT(3, "Payment"),
    COMPLETE(4, "Confirmation"),
}

sealed interface PaymentPhase {
    data object Idle : PaymentPhase
    data object CreatingOrder : PaymentPhase
    /** Stripe's sheet is open; the client secret belongs to a real PaymentIntent. */
    data class SheetReady(val clientSecret: String) : PaymentPhase
    /** Sheet finished. We are now waiting for the server to confirm, not assuming. */
    data object AwaitingConfirmation : PaymentPhase
    data class Confirmed(val order: Order) : PaymentPhase
    data class Failed(val error: AppError) : PaymentPhase
}

data class CheckoutState(
    val competition: Competition? = null,
    val profile: UserProfile? = null,
    val step: CheckoutStep = CheckoutStep.REVIEW,
    val quantity: Int = 1,
    val promoInput: String = "",
    val breakdown: PriceBreakdown? = null,
    val quoting: Boolean = false,
    val quoteError: AppError? = null,
    val ageConfirmed: Boolean = false,
    val rulesAccepted: Boolean = false,
    val phase: PaymentPhase = PaymentPhase.Idle,
    val orderId: String? = null,
    val alreadyHeld: Int = 0,
    val savingDetails: Boolean = false,
    val detailsError: AppError? = null,
    /** Whether the customer wants their balance put towards this order. */
    val useCredit: Boolean = true,
) {
    val creditAvailablePence: Int get() = profile?.creditBalancePence ?: 0

    /** Never more than the order is worth - the rest stays in the balance. */
    val creditToApplyPence: Int
        get() = if (!useCredit) 0
        else minOf(creditAvailablePence, breakdown?.totalPence ?: 0)

    val amountDuePence: Int get() = (breakdown?.totalPence ?: 0) - creditToApplyPence
    val paidEntirelyWithCredit: Boolean get() = breakdown != null && amountDuePence == 0

    /** Once the order exists, backing out would strand a reservation. */
    val canGoBack: Boolean
        get() = when (step) {
            CheckoutStep.REVIEW, CheckoutStep.COMPLETE -> true
            CheckoutStep.PAYMENT -> phase is PaymentPhase.Failed
            else -> true
        }

    val maxSelectable: Int
        get() = (competition?.let {
            minOf(it.entriesRemaining, it.perCustomerLimit - alreadyHeld)
        } ?: 1).coerceAtLeast(0)

    val canContinueFromReview: Boolean get() = breakdown != null && quantity in 1..maxSelectable && !quoting
    val canConfirm: Boolean get() = ageConfirmed && rulesAccepted
    val presetQuantities: List<Int>
        get() = listOf(1, 2, 5, 10, 25).filter { it <= maxSelectable }
}

@HiltViewModel
class CheckoutViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val competitions: CompetitionRepository,
    private val checkout: CheckoutRepository,
    private val orders: OrderRepository,
    private val entries: EntryRepository,
    private val accounts: AccountRepository,
    private val auth: AuthRepository,
) : ViewModel() {

    private val competitionId: String = checkNotNull(savedState["competitionId"])

    private val _state = MutableStateFlow(CheckoutState())
    val state: StateFlow<CheckoutState> = _state.asStateFlow()

    /** Stable for the whole checkout attempt so retries can't duplicate the order. */
    private var idempotencyKey: String = UUID.randomUUID().toString()
    private var quoteJob: Job? = null
    private var confirmationJob: Job? = null

    init { load() }

    fun load() {
        viewModelScope.launch {
            val uid = auth.currentUid
            val competition = competitions.competition(competitionId).first()
            val held = uid?.let { runCatching { entries.myEntryCountFor(it, competitionId) }.getOrDefault(0) } ?: 0
            val profile = uid?.let { runCatching { accounts.profile(it).first() }.getOrNull() }
            _state.value = _state.value.copy(
                competition = competition,
                profile = profile,
                alreadyHeld = held,
            )
            requestQuote()
        }
    }

    fun setQuantity(quantity: Int) {
        val capped = quantity.coerceIn(1, _state.value.maxSelectable.coerceAtLeast(1))
        _state.value = _state.value.copy(quantity = capped)
        requestQuote()
    }

    fun setUseCredit(v: Boolean) { _state.value = _state.value.copy(useCredit = v) }

    fun onPromoInput(code: String) {
        _state.value = _state.value.copy(promoInput = code.uppercase())
    }

    fun applyPromo() = requestQuote()

    fun clearPromo() {
        _state.value = _state.value.copy(promoInput = "")
        requestQuote()
    }

    /**
     * The total on screen is always whatever the server last returned. A stale
     * or absent quote blocks the continue button rather than falling back to a
     * locally computed figure.
     */
    private fun requestQuote() {
        quoteJob?.cancel()
        quoteJob = viewModelScope.launch {
            val s = _state.value
            _state.value = s.copy(quoting = true, quoteError = null)
            try {
                val breakdown = checkout.quote(
                    competitionId = competitionId,
                    quantity = s.quantity,
                    promoCode = s.promoInput.ifBlank { null },
                )
                _state.value = _state.value.copy(breakdown = breakdown, quoting = false)
            } catch (t: Throwable) {
                _state.value = _state.value.copy(
                    quoting = false,
                    quoteError = Errors.from(t),
                    breakdown = null,
                )
            }
        }
    }

    fun goTo(step: CheckoutStep) { _state.value = _state.value.copy(step = step) }
    fun back() {
        val current = _state.value.step
        val previous = CheckoutStep.entries.firstOrNull { it.index == current.index - 1 } ?: return
        _state.value = _state.value.copy(step = previous)
    }

    fun setAgeConfirmed(v: Boolean) { _state.value = _state.value.copy(ageConfirmed = v) }
    fun setRulesAccepted(v: Boolean) { _state.value = _state.value.copy(rulesAccepted = v) }

    fun updateDetails(displayName: String, phone: String) {
        val uid = auth.currentUid ?: return
        viewModelScope.launch {
            _state.value = _state.value.copy(savingDetails = true, detailsError = null)
            runCatching { accounts.updateDetails(uid, displayName, phone) }
                .onSuccess {
                    _state.value = _state.value.copy(
                        savingDetails = false,
                        step = CheckoutStep.CONFIRM,
                    )
                }
                .onFailure { t ->
                    _state.value = _state.value.copy(
                        savingDetails = false,
                        detailsError = Errors.from(t),
                    )
                }
        }
    }

    /** Creates the order server-side and reserves the entry numbers. */
    fun beginPayment() {
        val s = _state.value
        if (!s.canConfirm || s.breakdown == null) return
        viewModelScope.launch {
            _state.value = s.copy(phase = PaymentPhase.CreatingOrder, step = CheckoutStep.PAYMENT)
            try {
                val created = checkout.createOrder(
                    competitionId = competitionId,
                    quantity = s.quantity,
                    promoCode = s.promoInput.ifBlank { null },
                    creditToApplyPence = s.creditToApplyPence,
                    idempotencyKey = idempotencyKey,
                )
                val secret = created.clientSecret
                val coveredByCredit = created.paidWithCredit || secret == null
                _state.value = _state.value.copy(
                    orderId = created.orderId,
                    breakdown = created.breakdown,
                    phase = if (coveredByCredit) PaymentPhase.AwaitingConfirmation
                    else PaymentPhase.SheetReady(secret!!),
                )
                // Credit covered the lot, so there is no card sheet to show. The
                // server already settled it; wait on the order document exactly
                // as we would after a card payment rather than assuming success.
                if (coveredByCredit) onSheetCompleted()
            } catch (t: Throwable) {
                _state.value = _state.value.copy(phase = PaymentPhase.Failed(Errors.from(t)))
            }
        }
    }

    /**
     * Called when Stripe's sheet reports completion. This is NOT success: we now
     * watch the order document and wait for our own webhook to mark it paid.
     * If the app is killed here, the order is still completed server-side and
     * the customer's entries appear in My Tickets regardless.
     */
    fun onSheetCompleted() {
        val orderId = _state.value.orderId ?: return
        _state.value = _state.value.copy(phase = PaymentPhase.AwaitingConfirmation)
        confirmationJob?.cancel()
        confirmationJob = viewModelScope.launch {
            val confirmed = withTimeoutOrNull(90_000) {
                orders.order(orderId).first { it != null && it.paymentStatus != "pending" }
            }
            _state.value = when {
                confirmed == null -> _state.value.copy(
                    phase = PaymentPhase.Failed(
                        AppError(
                            title = "Still confirming",
                            message = "Your payment is being confirmed by the bank. Nothing further is needed — your entries will appear in My Tickets, and we'll email you when they do.",
                            retryable = false,
                        )
                    )
                )
                confirmed.isPaid -> _state.value.copy(
                    phase = PaymentPhase.Confirmed(confirmed),
                    step = CheckoutStep.COMPLETE,
                )
                else -> _state.value.copy(
                    phase = PaymentPhase.Failed(
                        AppError(
                            title = "Payment not completed",
                            message = confirmed.failureMessage
                                ?: "The payment didn't go through, so your entry numbers have been released. You haven't been charged.",
                        )
                    )
                )
            }
        }
    }

    fun onSheetCancelled() {
        // The reservation is left to expire server-side; the customer can retry.
        _state.value = _state.value.copy(phase = PaymentPhase.Idle, step = CheckoutStep.CONFIRM)
    }

    fun onSheetFailed(message: String) {
        _state.value = _state.value.copy(
            phase = PaymentPhase.Failed(AppError("Payment failed", message))
        )
    }

    /** A fresh key: a genuine second attempt, not a retry of the first. */
    fun retryPayment() {
        idempotencyKey = UUID.randomUUID().toString()
        _state.value = _state.value.copy(phase = PaymentPhase.Idle, step = CheckoutStep.CONFIRM, orderId = null)
    }
}
