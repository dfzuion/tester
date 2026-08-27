package uk.co.rodrunners.raffles.ui.screens.orders

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import uk.co.rodrunners.raffles.core.Errors
import uk.co.rodrunners.raffles.core.UiState
import uk.co.rodrunners.raffles.data.model.Order
import uk.co.rodrunners.raffles.data.repository.AuthRepository
import uk.co.rodrunners.raffles.data.repository.OrderRepository

@HiltViewModel
class OrdersViewModel @Inject constructor(
    private val orders: OrderRepository,
    private val auth: AuthRepository,
) : ViewModel() {

    private val _state = MutableStateFlow<UiState<List<Order>>>(UiState.Loading)
    val state: StateFlow<UiState<List<Order>>> = _state.asStateFlow()

    private var watcher: Job? = null

    init { load() }

    fun load() {
        val uid = auth.currentUid ?: run {
            _state.value = UiState.Empty("Log in to see your orders", "Order history is tied to your account.", "Log in")
            return
        }
        watcher?.cancel()
        watcher = viewModelScope.launch {
            orders.myOrders(uid)
                .catch { t -> _state.value = UiState.Error(Errors.from(t)) }
                .collect { list ->
                    _state.value = if (list.isEmpty()) {
                        UiState.Empty(
                            "No orders yet",
                            "Every entry you buy is recorded here with its numbers and receipt.",
                            "Browse raffles",
                        )
                    } else UiState.Success(list)
                }
        }
    }
}

@HiltViewModel
class OrderDetailViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val orders: OrderRepository,
) : ViewModel() {

    private val orderId: String = checkNotNull(savedState["orderId"])

    private val _state = MutableStateFlow<UiState<Order>>(UiState.Loading)
    val state: StateFlow<UiState<Order>> = _state.asStateFlow()

    private var watcher: Job? = null

    init { load() }

    fun load() {
        watcher?.cancel()
        watcher = viewModelScope.launch {
            orders.order(orderId)
                .catch { t -> _state.value = UiState.Error(Errors.from(t)) }
                .collect { order ->
                    _state.value = order?.let { UiState.Success(it) }
                        ?: UiState.Empty("Order not found", "This order isn't available on your account.")
                }
        }
    }
}
