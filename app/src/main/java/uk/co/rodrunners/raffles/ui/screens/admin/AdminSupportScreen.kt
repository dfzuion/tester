package uk.co.rodrunners.raffles.ui.screens.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import uk.co.rodrunners.raffles.data.model.SupportTicket
import uk.co.rodrunners.raffles.data.model.TicketMessage
import uk.co.rodrunners.raffles.ui.components.Chip
import uk.co.rodrunners.raffles.ui.components.GoldButton
import uk.co.rodrunners.raffles.ui.components.QuietButton
import uk.co.rodrunners.raffles.ui.components.SectionHeader
import uk.co.rodrunners.raffles.ui.screens.auth.RrrTextField
import uk.co.rodrunners.raffles.ui.theme.Dimens
import uk.co.rodrunners.raffles.ui.theme.RrrColors
import uk.co.rodrunners.raffles.ui.theme.RrrShapes

private val STAMP = SimpleDateFormat("d MMM, HH:mm", Locale.UK)

private fun stamp(millis: Long): String =
    if (millis == 0L) "" else STAMP.format(Date(millis))

private val FILTERS = listOf(
    "open" to "Waiting",
    "awaiting_customer" to "Answered",
    "closed" to "Closed",
    null to "All",
)

/**
 * Reading and answering support, from the phone.
 *
 * The website's control room grew the same thing at the same time. Both are
 * needed: the tickets arrive at all hours and the person answering them is
 * not always at a desk.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminSupportScreen(
    onBack: () -> Unit,
    viewModel: AdminSupportViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val open = state.openTicket

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text(if (open == null) "Support" else open.ticketId, color = RrrColors.Bone) },
                navigationIcon = {
                    IconButton(onClick = { if (open == null) onBack() else viewModel.close() }) {
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
        if (open == null) {
            TicketList(state, padding, viewModel)
        } else {
            TicketThread(state, open, padding, viewModel)
        }
    }
}

@Composable
private fun TicketList(
    state: AdminSupportState,
    padding: PaddingValues,
    viewModel: AdminSupportViewModel,
) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(horizontal = Dimens.gutter),
    ) {
        SectionHeader(title = "Support")

        Text(
            when {
                state.loading -> "Loading"
                state.waiting == 0 -> "Nothing waiting on a reply"
                state.waiting == 1 -> "One waiting on a reply"
                else -> "${state.waiting} waiting on a reply"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = RrrColors.Mist,
        )

        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FILTERS.forEach { (key, label) ->
                Chip(
                    text = label,
                    selected = state.filter == key,
                    onClick = { viewModel.filter(key) },
                )
            }
        }

        if (!state.loading && state.shown.isEmpty()) {
            Text(
                "Nothing here.",
                style = MaterialTheme.typography.bodyMedium,
                color = RrrColors.Mist,
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        LazyColumn(
            Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(state.shown, key = { it.id }) { ticket ->
                TicketRow(ticket) { viewModel.open(ticket) }
            }

            item { Spacer(Modifier.height(28.dp)) }
        }
    }
}

@Composable
private fun TicketRow(ticket: SupportTicket, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RrrShapes.medium)
            .background(RrrColors.Surface)
            .clickable(onClick = onClick)
            .padding(16.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Text(
                ticket.subject.ifBlank { "No subject" },
                style = MaterialTheme.typography.titleSmall,
                color = RrrColors.Bone,
                modifier = Modifier.weight(1f),
            )

            Text(
                ticket.statusLabel.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = if (ticket.status == "open") RrrColors.Khaki else RrrColors.Slate,
            )
        }

        Spacer(Modifier.height(6.dp))

        Text(
            listOfNotNull(
                ticket.userDisplayName,
                ticket.userEmail,
                ticket.category.replace('_', ' '),
                stamp(ticket.updatedAtMillis).ifBlank { null },
            ).joinToString(" · "),
            style = MaterialTheme.typography.labelSmall,
            color = RrrColors.Mist,
        )
    }
}

@Composable
private fun TicketThread(
    state: AdminSupportState,
    ticket: SupportTicket,
    padding: PaddingValues,
    viewModel: AdminSupportViewModel,
) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(horizontal = Dimens.gutter)
            .imePadding(),
    ) {
        SectionHeader(title = ticket.subject.ifBlank { "No subject" })

        Text(
            listOfNotNull(
                ticket.userDisplayName,
                ticket.userEmail,
                ticket.orderId?.let { "order $it" },
            ).joinToString(" · "),
            style = MaterialTheme.typography.labelSmall,
            color = RrrColors.Mist,
        )

        LazyColumn(
            Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item { Spacer(Modifier.height(6.dp)) }

            items(state.thread, key = { it.id }) { message ->
                MessageBubble(message, ticket.userDisplayName)
            }

            item { Spacer(Modifier.height(10.dp)) }
        }

        state.message?.let {
            Text(it, style = MaterialTheme.typography.labelSmall, color = RrrColors.Khaki)
            Spacer(Modifier.height(8.dp))
        }

        state.error?.let {
            Text(it.message, style = MaterialTheme.typography.labelSmall, color = RrrColors.Danger)
            Spacer(Modifier.height(8.dp))
        }

        RrrTextField(
            value = state.reply,
            onValueChange = viewModel::onReply,
            label = "Your reply",
            singleLine = false,
            minLines = 3,
        )

        Spacer(Modifier.height(10.dp))

        Row(
            Modifier
                .fillMaxWidth()
                .padding(bottom = 18.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            GoldButton(
                text = if (state.busy) "Sending" else "Send reply",
                onClick = { viewModel.send(false) },
                enabled = state.canSend,
                modifier = Modifier.weight(1f),
            )

            if (state.canSend) {
                QuietButton(
                    "Reply & close",
                    onClick = { viewModel.send(true) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun MessageBubble(message: TicketMessage, customerName: String) {
    val staff = message.authorRole == "staff"

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RrrShapes.medium)
            .background(if (staff) RrrColors.SurfaceRaised else RrrColors.Surface)
            .padding(14.dp),
    ) {
        Text(
            listOfNotNull(
                if (staff) "US" else customerName.uppercase(),
                stamp(message.createdAtMillis).ifBlank { null },
            ).joinToString(" · "),
            style = MaterialTheme.typography.labelSmall,
            color = if (staff) RrrColors.Khaki else RrrColors.Mist,
        )

        Spacer(Modifier.height(6.dp))

        Text(
            message.body,
            style = MaterialTheme.typography.bodyMedium,
            color = RrrColors.Bone,
        )
    }
}
