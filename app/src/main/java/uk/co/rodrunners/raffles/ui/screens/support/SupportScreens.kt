package uk.co.rodrunners.raffles.ui.screens.support

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import uk.co.rodrunners.raffles.core.TimeFormat
import uk.co.rodrunners.raffles.data.model.SupportCategories
import uk.co.rodrunners.raffles.ui.components.Chip
import uk.co.rodrunners.raffles.ui.components.GoldButton
import uk.co.rodrunners.raffles.ui.components.SectionHeader
import uk.co.rodrunners.raffles.ui.components.StateHost
import uk.co.rodrunners.raffles.ui.components.StatusPill
import uk.co.rodrunners.raffles.ui.screens.auth.RrrTextField
import uk.co.rodrunners.raffles.ui.theme.Dimens
import uk.co.rodrunners.raffles.ui.theme.RrrColors
import uk.co.rodrunners.raffles.ui.theme.RrrShapes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SupportScreen(
    orderId: String?,
    onBack: () -> Unit,
    onOpenTicket: (String) -> Unit,
    onOpenFaq: () -> Unit,
    viewModel: SupportViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var category by remember { mutableStateOf(SupportCategories.all.first().key) }
    var subject by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }

    LaunchedEffect(state.createdTicketId) {
        state.createdTicketId?.let {
            subject = ""; body = ""
            viewModel.consumeCreated()
            onOpenTicket(it)
        }
    }

    Scaffold(
        containerColor = RrrColors.Ink,
        topBar = {
            TopAppBar(
                title = { Text("Help and support", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Go back", tint = RrrColors.Bone)
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
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = Dimens.gutter),
        ) {
            Spacer(Modifier.height(8.dp))
            Text(
                "Most questions are answered in the FAQs. If not, send us a message — " +
                    "we reply inside the app and by email.",
                style = MaterialTheme.typography.bodyMedium,
                color = RrrColors.Mist,
            )
            Spacer(Modifier.height(12.dp))
            uk.co.rodrunners.raffles.ui.components.QuietButton("Read the FAQs", onOpenFaq)

            if (state.tickets.isNotEmpty()) {
                Spacer(Modifier.height(28.dp))
                SectionHeader("Your messages")
                Spacer(Modifier.height(8.dp))
                state.tickets.forEach { ticket ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clip(RrrShapes.large)
                            .background(RrrColors.Surface)
                            .clickable { onOpenTicket(ticket.id) }
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(ticket.subject, style = MaterialTheme.typography.titleMedium, color = RrrColors.Bone)
                            Spacer(Modifier.height(4.dp))
                            Text(
                                TimeFormat.relative(ticket.updatedAtMillis),
                                style = MaterialTheme.typography.labelSmall,
                                color = RrrColors.Slate,
                            )
                        }
                        StatusPill(ticket.status)
                    }
                }
            }

            Spacer(Modifier.height(28.dp))
            SectionHeader("Send a message")
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                SupportCategories.all.take(3).forEach { c ->
                    Chip(c.label, category == c.key, onClick = { category = c.key })
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                SupportCategories.all.drop(3).forEach { c ->
                    Chip(c.label, category == c.key, onClick = { category = c.key })
                }
            }
            Spacer(Modifier.height(16.dp))
            RrrTextField(subject, { subject = it }, "Subject")
            Spacer(Modifier.height(12.dp))
            RrrTextField(body, { body = it }, "How can we help?", singleLine = false, minLines = 5)
            if (orderId != null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "This message will be linked to your order so we can look it up.",
                    style = MaterialTheme.typography.labelSmall,
                    color = RrrColors.Slate,
                )
            }
            state.error?.let {
                Spacer(Modifier.height(10.dp))
                Text(it.message, style = MaterialTheme.typography.bodySmall, color = RrrColors.Danger)
            }
            Spacer(Modifier.height(16.dp))
            GoldButton(
                "Send message",
                { viewModel.createTicket(category, subject.trim(), body.trim(), orderId) },
                Modifier.fillMaxWidth(),
                enabled = subject.trim().length >= 3 && body.trim().length >= 10 && !state.submitting,
                loading = state.submitting,
            )

            state.company?.let { company ->
                Spacer(Modifier.height(32.dp))
                Text(
                    "${company.registeredName}, ${company.registeredAddress}. " +
                        "Company number ${company.companyNumber}. Contact ${company.supportEmail}.",
                    style = MaterialTheme.typography.labelSmall,
                    color = RrrColors.Slate,
                )
            }
            Spacer(Modifier.height(48.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TicketThreadScreen(
    onBack: () -> Unit,
    viewModel: TicketThreadViewModel = hiltViewModel(),
) {
    val ticket by viewModel.ticket.collectAsStateWithLifecycle()
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val sending by viewModel.sending.collectAsStateWithLifecycle()
    var draft by remember { mutableStateOf("") }

    Scaffold(
        containerColor = RrrColors.Ink,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        ticket?.subject ?: "Message",
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Go back", tint = RrrColors.Bone)
                    }
                },
                actions = { ticket?.let { StatusPill(it.status); Spacer(Modifier.width(12.dp)) } },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = RrrColors.Ink,
                    titleContentColor = RrrColors.Bone,
                ),
            )
        },
        bottomBar = {
            Row(
                Modifier
                    .background(RrrColors.Ink)
                    .navigationBarsPadding()
                    .imePadding()
                    .padding(horizontal = Dimens.gutter, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.weight(1f)) {
                    RrrTextField(draft, { draft = it }, "Write a reply", singleLine = false, minLines = 1)
                }
                Spacer(Modifier.width(8.dp))
                IconButton(
                    onClick = { viewModel.reply(draft.trim()); draft = "" },
                    enabled = draft.isNotBlank() && !sending,
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Send,
                        "Send reply",
                        tint = if (draft.isNotBlank()) RrrColors.Gold else RrrColors.Slate,
                    )
                }
            }
        },
    ) { padding ->
        StateHost(state = messages, modifier = Modifier.padding(padding), onRetry = viewModel::load) { list ->
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(Dimens.gutter),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(list, key = { it.id }) { message ->
                    val mine = message.authorRole == "customer"
                    Column(
                        Modifier.fillMaxWidth(),
                        horizontalAlignment = if (mine) Alignment.End else Alignment.Start,
                    ) {
                        Box(
                            Modifier
                                .clip(RrrShapes.large)
                                .background(if (mine) RrrColors.GoldDim else RrrColors.Surface)
                                .padding(12.dp)
                        ) {
                            Text(
                                message.body,
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (mine) RrrColors.GoldBright else RrrColors.Bone,
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "${if (mine) "You" else "Rod Runners"} · ${TimeFormat.relative(message.createdAtMillis)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = RrrColors.Slate,
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FaqScreen(
    onBack: () -> Unit,
    onOpenSupport: () -> Unit,
    viewModel: FaqViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    var expanded by remember { mutableStateOf<String?>(null) }

    Scaffold(
        containerColor = RrrColors.Ink,
        topBar = {
            Column {
                TopAppBar(
                    title = { Text("FAQs", style = MaterialTheme.typography.titleLarge) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Go back", tint = RrrColors.Bone)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = RrrColors.Ink,
                        titleContentColor = RrrColors.Bone,
                    ),
                )
                Box(Modifier.padding(horizontal = Dimens.gutter, vertical = 8.dp)) {
                    RrrTextField(query, viewModel::setQuery, "Search the FAQs")
                }
            }
        },
    ) { padding ->
        StateHost(state = state, modifier = Modifier.padding(padding), onRetry = viewModel::load) { faqs ->
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(Dimens.gutter),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(faqs, key = { it.id }) { faq ->
                    val open = expanded == faq.id
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clip(RrrShapes.large)
                            .background(RrrColors.Surface)
                            .clickable { expanded = if (open) null else faq.id }
                            .padding(16.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                faq.question,
                                style = MaterialTheme.typography.titleMedium,
                                color = RrrColors.Bone,
                                modifier = Modifier.weight(1f),
                            )
                            Icon(
                                if (open) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                                contentDescription = null,
                                tint = RrrColors.Slate,
                            )
                        }
                        AnimatedVisibility(open) {
                            Column {
                                Spacer(Modifier.height(10.dp))
                                Text(faq.answer, style = MaterialTheme.typography.bodyMedium, color = RrrColors.Mist)
                            }
                        }
                    }
                }
                item {
                    Spacer(Modifier.height(20.dp))
                    GoldButton("Still stuck? Message us", onOpenSupport, Modifier.fillMaxWidth())
                    Spacer(Modifier.height(40.dp))
                }
            }
        }
    }
}
