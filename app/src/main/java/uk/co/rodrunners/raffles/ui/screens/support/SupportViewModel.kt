package uk.co.rodrunners.raffles.ui.screens.support

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
import uk.co.rodrunners.raffles.core.AppError
import uk.co.rodrunners.raffles.core.Errors
import uk.co.rodrunners.raffles.core.UiState
import uk.co.rodrunners.raffles.data.model.CompanyInfo
import uk.co.rodrunners.raffles.data.model.FaqItem
import uk.co.rodrunners.raffles.data.model.SupportTicket
import uk.co.rodrunners.raffles.data.model.TicketMessage
import uk.co.rodrunners.raffles.data.repository.AuthRepository
import uk.co.rodrunners.raffles.data.repository.ContentRepository
import uk.co.rodrunners.raffles.data.repository.SupportRepository

data class SupportHubState(
    val tickets: List<SupportTicket> = emptyList(),
    val company: CompanyInfo? = null,
    val submitting: Boolean = false,
    val error: AppError? = null,
    val createdTicketId: String? = null,
)

@HiltViewModel
class SupportViewModel @Inject constructor(
    private val support: SupportRepository,
    private val content: ContentRepository,
    private val auth: AuthRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(SupportHubState())
    val state: StateFlow<SupportHubState> = _state.asStateFlow()

    private var ticketsWatcher: Job? = null

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            runCatching { content.company() }.getOrNull()?.let {
                _state.value = _state.value.copy(company = it)
            }
        }
        val uid = auth.currentUid ?: return
        ticketsWatcher?.cancel()
        ticketsWatcher = viewModelScope.launch {
            support.myTickets(uid)
                .catch { t -> _state.value = _state.value.copy(error = Errors.from(t)) }
                .collect { list -> _state.value = _state.value.copy(tickets = list, error = null) }
        }
    }

    fun createTicket(category: String, subject: String, body: String, orderId: String?) {
        viewModelScope.launch {
            _state.value = _state.value.copy(submitting = true, error = null)
            try {
                val id = support.createTicket(category, subject, body, orderId)
                _state.value = _state.value.copy(submitting = false, createdTicketId = id)
            } catch (t: Throwable) {
                _state.value = _state.value.copy(submitting = false, error = Errors.from(t))
            }
        }
    }

    fun consumeCreated() { _state.value = _state.value.copy(createdTicketId = null) }
}

@HiltViewModel
class TicketThreadViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val support: SupportRepository,
) : ViewModel() {

    private val ticketId: String = checkNotNull(savedState["ticketId"])

    private val _ticket = MutableStateFlow<SupportTicket?>(null)
    val ticket: StateFlow<SupportTicket?> = _ticket.asStateFlow()

    private val _messages = MutableStateFlow<UiState<List<TicketMessage>>>(UiState.Loading)
    val messages: StateFlow<UiState<List<TicketMessage>>> = _messages.asStateFlow()

    private val _sending = MutableStateFlow(false)
    val sending: StateFlow<Boolean> = _sending.asStateFlow()

    private val watchers = mutableListOf<Job>()

    init { load() }

    fun load() {
        watchers.forEach { it.cancel() }
        watchers.clear()
        watchers += viewModelScope.launch {
            support.ticket(ticketId).catch { }.collect { _ticket.value = it }
        }
        watchers += viewModelScope.launch {
            support.messages(ticketId)
                .catch { t -> _messages.value = UiState.Error(Errors.from(t)) }
                .collect { _messages.value = UiState.Success(it) }
        }
    }

    fun reply(body: String) {
        if (body.isBlank()) return
        viewModelScope.launch {
            _sending.value = true
            runCatching { support.reply(ticketId, body) }
            _sending.value = false
        }
    }
}

@HiltViewModel
class FaqViewModel @Inject constructor(
    private val content: ContentRepository,
) : ViewModel() {

    private val _state = MutableStateFlow<UiState<List<FaqItem>>>(UiState.Loading)
    val state: StateFlow<UiState<List<FaqItem>>> = _state.asStateFlow()

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private var all: List<FaqItem> = emptyList()

    init { load() }

    fun load() {
        viewModelScope.launch {
            try {
                all = content.faqs()
                filter()
            } catch (t: Throwable) {
                _state.value = UiState.Error(Errors.from(t))
            }
        }
    }

    fun setQuery(q: String) { _query.value = q; filter() }

    private fun filter() {
        val needle = _query.value.trim().lowercase()
        val list = if (needle.isBlank()) all else all.filter {
            it.question.lowercase().contains(needle) || it.answer.lowercase().contains(needle)
        }
        _state.value = if (list.isEmpty()) {
            UiState.Empty(
                if (needle.isBlank()) "No FAQs yet" else "Nothing matched that",
                "Try different wording, or send us a message and we'll answer directly.",
            )
        } else UiState.Success(list)
    }
}
