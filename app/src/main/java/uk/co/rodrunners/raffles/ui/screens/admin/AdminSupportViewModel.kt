package uk.co.rodrunners.raffles.ui.screens.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import uk.co.rodrunners.raffles.core.AppError
import uk.co.rodrunners.raffles.core.Errors
import uk.co.rodrunners.raffles.data.model.SupportTicket
import uk.co.rodrunners.raffles.data.model.TicketMessage
import uk.co.rodrunners.raffles.data.repository.SupportRepository

/**
 * The support inbox, from the other side of the counter.
 *
 * Customers have been able to write in since the app shipped and nobody could
 * read what they sent, because no screen showed it. This is that screen's
 * state.
 */
data class AdminSupportState(
    val tickets: List<SupportTicket> = emptyList(),
    /** null means every status. */
    val filter: String? = "open",
    val openTicket: SupportTicket? = null,
    val thread: List<TicketMessage> = emptyList(),
    val reply: String = "",
    val loading: Boolean = true,
    val busy: Boolean = false,
    val message: String? = null,
    val error: AppError? = null,
) {
    val canSend: Boolean get() = openTicket != null && reply.trim().length >= 2 && !busy
    val waiting: Int get() = tickets.count { it.status == "open" }
    val shown: List<SupportTicket>
        get() = if (filter == null) tickets else tickets.filter { it.status == filter }
}

@HiltViewModel
class AdminSupportViewModel @Inject constructor(
    private val support: SupportRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(AdminSupportState())
    val state: StateFlow<AdminSupportState> = _state.asStateFlow()

    /** Held so switching filter or ticket cancels the listener it replaces. */
    private var listJob: Job? = null
    private var threadJob: Job? = null

    init { watch() }

    /** One listener over everything; the filter is applied where it is shown. */
    fun watch() {
        listJob?.cancel()
        _state.update { it.copy(loading = true, error = null) }

        listJob = viewModelScope.launch {
            support.allTickets()
                .catch { e -> _state.update { it.copy(loading = false, error = Errors.from(e)) } }
                .collect { rows -> _state.update { it.copy(tickets = rows, loading = false) } }
        }
    }

    fun filter(status: String?) = _state.update { it.copy(filter = status) }

    fun open(ticket: SupportTicket) {
        threadJob?.cancel()
        _state.update { it.copy(openTicket = ticket, thread = emptyList(), reply = "", message = null) }

        threadJob = viewModelScope.launch {
            support.messages(ticket.id)
                .catch { e -> _state.update { it.copy(error = Errors.from(e)) } }
                .collect { msgs -> _state.update { it.copy(thread = msgs) } }
        }
    }

    fun close() {
        threadJob?.cancel()
        _state.update { it.copy(openTicket = null, thread = emptyList(), reply = "") }
    }

    fun onReply(text: String) = _state.update { it.copy(reply = text) }

    fun send(closeTicket: Boolean) {
        val current = _state.value
        val ticket = current.openTicket ?: return
        if (!current.canSend) return

        _state.update { it.copy(busy = true, message = null, error = null) }

        viewModelScope.launch {
            runCatching { support.reply(ticket.id, current.reply.trim(), closeTicket) }
                .onSuccess {
                    // The thread is live, so the new message arrives on its own.
                    _state.update {
                        it.copy(
                            busy = false,
                            reply = "",
                            message = if (closeTicket) "Replied and closed." else "Replied. They have been told.",
                            openTicket = if (closeTicket) null else it.openTicket,
                            thread = if (closeTicket) emptyList() else it.thread,
                        )
                    }
                }
                .onFailure { e -> _state.update { it.copy(busy = false, error = Errors.from(e)) } }
        }
    }

    fun dismiss() = _state.update { it.copy(message = null, error = null) }
}
