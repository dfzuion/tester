package uk.co.rodrunners.raffles.ui.screens.account

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import uk.co.rodrunners.raffles.core.TimeFormat
import uk.co.rodrunners.raffles.data.model.Address
import uk.co.rodrunners.raffles.data.model.NotificationCategories
import uk.co.rodrunners.raffles.ui.components.EmptyState
import uk.co.rodrunners.raffles.ui.components.GoldButton
import uk.co.rodrunners.raffles.ui.components.OutlineButton
import uk.co.rodrunners.raffles.ui.components.QuietButton
import uk.co.rodrunners.raffles.ui.components.SectionHeader
import uk.co.rodrunners.raffles.ui.screens.auth.PasswordField
import uk.co.rodrunners.raffles.ui.screens.auth.RrrTextField
import uk.co.rodrunners.raffles.ui.theme.Dimens
import uk.co.rodrunners.raffles.ui.theme.RrrColors
import uk.co.rodrunners.raffles.ui.theme.RrrShapes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SubScreenScaffold(
    title: String,
    onBack: () -> Unit,
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text(title, style = MaterialTheme.typography.titleLarge) },
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
        content = content,
    )
}

/** Personal details and the address a prize would be posted to. */
@Composable
fun PersonalDetailsScreen(
    onBack: () -> Unit,
    viewModel: AccountViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val profile = state.profile

    var name by remember(profile?.displayName) { mutableStateOf(profile?.displayName.orEmpty()) }
    var phone by remember(profile?.phone) { mutableStateOf(profile?.phone.orEmpty()) }
    var line1 by remember(profile?.address) { mutableStateOf(profile?.address?.line1.orEmpty()) }
    var line2 by remember(profile?.address) { mutableStateOf(profile?.address?.line2.orEmpty()) }
    var town by remember(profile?.address) { mutableStateOf(profile?.address?.town.orEmpty()) }
    var county by remember(profile?.address) { mutableStateOf(profile?.address?.county.orEmpty()) }
    var postcode by remember(profile?.address) { mutableStateOf(profile?.address?.postcode.orEmpty()) }

    SubScreenScaffold("Personal details", onBack) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = Dimens.gutter),
        ) {
            Spacer(Modifier.height(8.dp))
            RrrTextField(name, { name = it }, "Full name")
            Spacer(Modifier.height(12.dp))
            RrrTextField(profile?.email.orEmpty(), {}, "Email address (used to log in)")
            Spacer(Modifier.height(12.dp))
            RrrTextField(phone, { phone = it }, "Phone number", keyboardType = KeyboardType.Phone)
            Spacer(Modifier.height(20.dp))
            GoldButton(
                "Save details",
                { viewModel.updateDetails(name, phone) },
                Modifier.fillMaxWidth(),
                enabled = name.trim().length >= 2 && !state.busy,
                loading = state.busy,
            )

            Spacer(Modifier.height(32.dp))
            SectionHeader("Delivery address")
            Spacer(Modifier.height(4.dp))
            Text(
                "Where a prize would be sent if you win.",
                style = MaterialTheme.typography.bodySmall,
                color = RrrColors.Mist,
            )
            Spacer(Modifier.height(16.dp))
            RrrTextField(line1, { line1 = it }, "Address line 1")
            Spacer(Modifier.height(12.dp))
            RrrTextField(line2, { line2 = it }, "Address line 2 (optional)")
            Spacer(Modifier.height(12.dp))
            RrrTextField(town, { town = it }, "Town or city")
            Spacer(Modifier.height(12.dp))
            RrrTextField(county, { county = it }, "County (optional)")
            Spacer(Modifier.height(12.dp))
            RrrTextField(postcode, { postcode = it.uppercase() }, "Postcode")
            Spacer(Modifier.height(20.dp))
            GoldButton(
                "Save address",
                {
                    viewModel.updateAddress(
                        Address(
                            line1 = line1.trim(),
                            line2 = line2.trim().ifBlank { null },
                            town = town.trim(),
                            county = county.trim().ifBlank { null },
                            postcode = postcode.trim(),
                        )
                    )
                },
                Modifier.fillMaxWidth(),
                enabled = line1.isNotBlank() && town.isNotBlank() && postcode.isNotBlank() && !state.busy,
            )

            state.message?.let {
                Spacer(Modifier.height(12.dp))
                Text(it, style = MaterialTheme.typography.bodySmall, color = RrrColors.Success)
            }
            state.error?.let {
                Spacer(Modifier.height(12.dp))
                Text(it.message, style = MaterialTheme.typography.bodySmall, color = RrrColors.Danger)
            }
            Spacer(Modifier.height(40.dp))
        }
    }
}

@Composable
fun NotificationPreferencesScreen(
    onBack: () -> Unit,
    viewModel: AccountViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val prefs = state.profile?.notificationPreferences.orEmpty()

    SubScreenScaffold("Notifications", onBack) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Dimens.gutter),
        ) {
            Spacer(Modifier.height(8.dp))
            Text(
                "Choose what we send you. Draw results for raffles you've entered are always sent — " +
                    "you'll never be left wondering whether you won.",
                style = MaterialTheme.typography.bodyMedium,
                color = RrrColors.Mist,
            )
            Spacer(Modifier.height(20.dp))
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RrrShapes.large)
                    .background(RrrColors.Surface)
            ) {
                NotificationCategories.all.forEachIndexed { index, category ->
                    Row(
                        Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(category.title, style = MaterialTheme.typography.bodyLarge, color = RrrColors.Bone)
                            Spacer(Modifier.height(2.dp))
                            Text(category.description, style = MaterialTheme.typography.labelSmall, color = RrrColors.Mist)
                        }
                        Spacer(Modifier.width(12.dp))
                        Switch(
                            checked = prefs[category.key] ?: category.defaultOn,
                            onCheckedChange = { viewModel.setNotificationPreference(category.key, it) },
                            enabled = !category.mandatory,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = RrrColors.Ink,
                                checkedTrackColor = RrrColors.Gold,
                                uncheckedThumbColor = RrrColors.Slate,
                                uncheckedTrackColor = RrrColors.SurfaceRaised,
                            ),
                        )
                    }
                    if (index != NotificationCategories.all.lastIndex) {
                        HorizontalDivider(thickness = 1.dp, color = RrrColors.Hairline)
                    }
                }
            }
            Spacer(Modifier.height(40.dp))
        }
    }
}

/** Inbox. Every push is mirrored here so nothing is missed. */
@Composable
fun NotificationsScreen(
    onBack: () -> Unit,
    onOpenLink: (String) -> Unit,
    viewModel: AccountViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    SubScreenScaffold("Your notifications", onBack) { padding ->
        if (state.notifications.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding)) {
                EmptyState(
                    title = "Nothing yet",
                    body = "Draw results, reminders and offers land here.",
                )
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(Dimens.gutter),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(state.notifications, key = { it.id }) { n ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RrrShapes.large)
                            .background(RrrColors.Surface)
                            .clickable {
                                viewModel.markRead(n.id)
                                n.deepLink?.let(onOpenLink)
                            }
                            .padding(14.dp),
                    ) {
                        Box(
                            Modifier
                                .padding(top = 6.dp)
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(if (n.read) RrrColors.Hairline else RrrColors.Gold)
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(n.title, style = MaterialTheme.typography.titleMedium, color = RrrColors.Bone)
                            Spacer(Modifier.height(4.dp))
                            Text(n.body, style = MaterialTheme.typography.bodyMedium, color = RrrColors.Mist)
                            Spacer(Modifier.height(6.dp))
                            Text(
                                TimeFormat.relative(n.createdAtMillis),
                                style = MaterialTheme.typography.labelSmall,
                                color = RrrColors.Slate,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SecurityScreen(
    onBack: () -> Unit,
    onAccountDeleted: () -> Unit,
    viewModel: AccountViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var current by remember { mutableStateOf("") }
    var next by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var showDelete by remember { mutableStateOf(false) }
    var deletePassword by remember { mutableStateOf("") }
    var deletedEntries by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(state.message) {
        if (state.message != null) { current = ""; next = ""; confirm = "" }
    }

    SubScreenScaffold("Password and security", onBack) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = Dimens.gutter),
        ) {
            Spacer(Modifier.height(8.dp))
            SectionHeader("Change password")
            Spacer(Modifier.height(12.dp))
            PasswordField(current, { current = it }, "Current password")
            Spacer(Modifier.height(12.dp))
            PasswordField(next, { next = it }, "New password")
            Spacer(Modifier.height(12.dp))
            PasswordField(confirm, { confirm = it }, "Confirm new password")
            if (next.isNotEmpty() && next.length < 8) {
                Spacer(Modifier.height(6.dp))
                Text("Use at least 8 characters.", style = MaterialTheme.typography.labelSmall, color = RrrColors.Warning)
            }
            Spacer(Modifier.height(16.dp))
            GoldButton(
                "Update password",
                { viewModel.changePassword(current, next) },
                Modifier.fillMaxWidth(),
                enabled = current.isNotBlank() && next.length >= 8 && next == confirm && !state.busy,
                loading = state.busy,
            )
            state.message?.let {
                Spacer(Modifier.height(10.dp))
                Text(it, style = MaterialTheme.typography.bodySmall, color = RrrColors.Success)
            }
            state.error?.let {
                Spacer(Modifier.height(10.dp))
                Text(it.message, style = MaterialTheme.typography.bodySmall, color = RrrColors.Danger)
            }

            Spacer(Modifier.height(40.dp))
            SectionHeader("Delete your account")
            Spacer(Modifier.height(8.dp))
            Text(
                "Your profile and personal details are removed. Entries you've already bought stay on the " +
                    "raffle record so past results remain accurate, but they're no longer linked to you by name.",
                style = MaterialTheme.typography.bodyMedium,
                color = RrrColors.Mist,
            )
            Spacer(Modifier.height(16.dp))
            OutlineButton("Delete my account", { showDelete = true }, danger = true)
            Spacer(Modifier.height(48.dp))
        }
    }

    if (showDelete) {
        AlertDialog(
            onDismissRequest = { showDelete = false },
            containerColor = RrrColors.Surface,
            titleContentColor = RrrColors.Bone,
            textContentColor = RrrColors.Mist,
            title = { Text("Delete account?") },
            text = {
                Column {
                    Text("This can't be undone. Enter your password to confirm.")
                    Spacer(Modifier.height(16.dp))
                    PasswordField(deletePassword, { deletePassword = it }, "Password")
                    state.error?.let {
                        Spacer(Modifier.height(8.dp))
                        Text(it.message, style = MaterialTheme.typography.bodySmall, color = RrrColors.Danger)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = deletePassword.isNotBlank() && !state.busy,
                    onClick = {
                        viewModel.deleteAccount(deletePassword) { retained ->
                            deletedEntries = retained
                            showDelete = false
                            onAccountDeleted()
                        }
                    },
                ) { Text("Delete", color = RrrColors.Danger) }
            },
            dismissButton = {
                TextButton(onClick = { showDelete = false }) { Text("Cancel", color = RrrColors.Mist) }
            },
        )
    }

    deletedEntries?.let { retained ->
        AlertDialog(
            onDismissRequest = { deletedEntries = null },
            containerColor = RrrColors.Surface,
            title = { Text("Account deleted", color = RrrColors.Bone) },
            text = {
                Text(
                    "$retained entries remain on the raffle records, anonymised.",
                    color = RrrColors.Mist,
                )
            },
            confirmButton = {
                TextButton(onClick = { deletedEntries = null }) { Text("Close", color = RrrColors.Gold) }
            },
        )
    }
}

/** Cards live in Stripe, so this explains where to manage them rather than faking a wallet. */
@Composable
fun PaymentMethodsScreen(onBack: () -> Unit) {
    SubScreenScaffold("Payment methods", onBack) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = Dimens.gutter)
                .verticalScroll(rememberScrollState()),
        ) {
            Spacer(Modifier.height(8.dp))
            Text(
                "We don't store card details. Payments are handled by Stripe, and your saved cards appear " +
                    "inside the payment sheet at checkout — you can add or remove them there.",
                style = MaterialTheme.typography.bodyMedium,
                color = RrrColors.Mist,
            )
            Spacer(Modifier.height(20.dp))
            Text(
                "Google Pay is offered automatically if it's set up on this device.",
                style = MaterialTheme.typography.bodyMedium,
                color = RrrColors.Mist,
            )
            Spacer(Modifier.height(24.dp))
            QuietButton("Back to account", onBack)
        }
    }
}
