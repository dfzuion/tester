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
import uk.co.rodrunners.raffles.data.model.AdminUser
import uk.co.rodrunners.raffles.ui.components.Chip
import uk.co.rodrunners.raffles.ui.components.GoldButton
import uk.co.rodrunners.raffles.ui.components.QuietButton
import uk.co.rodrunners.raffles.ui.components.SectionHeader
import uk.co.rodrunners.raffles.ui.screens.auth.RrrTextField
import uk.co.rodrunners.raffles.ui.theme.Dimens
import uk.co.rodrunners.raffles.ui.theme.RrrColors
import uk.co.rodrunners.raffles.ui.theme.RrrShapes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminUsersScreen(
    onBack: () -> Unit,
    viewModel: AdminUsersViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text("Administrators", style = MaterialTheme.typography.titleLarge) },
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
            if (state.needsBootstrap) {
                item { SectionHeader("First-time setup") }
                item {
                    Text(
                        "This project has no administrator yet. Enter the setup key from your " +
                            "Firebase secrets (ADMIN_BOOTSTRAP_KEY) to make this account Super Admin. " +
                            "It only works once.",
                        style = MaterialTheme.typography.bodySmall,
                        color = RrrColors.Mist,
                    )
                }
                item {
                    RrrTextField(state.bootstrapKey, viewModel::onBootstrapKey, "Setup key")
                }
                item {
                    GoldButton(
                        text = "Make me Super Admin",
                        onClick = { viewModel.bootstrap() },
                        enabled = state.bootstrapKey.length >= 8 && !state.busy,
                        loading = state.busy,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                item { Spacer(Modifier.height(12.dp)) }
            }

            item { SectionHeader("Give someone access") }
            item {
                Text(
                    "They need to have signed up in the app first — you're promoting an existing account.",
                    style = MaterialTheme.typography.bodySmall,
                    color = RrrColors.Mist,
                )
            }
            item {
                RrrTextField(state.email, viewModel::onEmail, "Their email address", keyboardType = KeyboardType.Email)
            }
            item {
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    AdminUser.ROLES.forEach { (key, label) ->
                        Chip(label, state.role == key, onClick = { viewModel.onRole(key) })
                    }
                }
            }
            item {
                GoldButton(
                    text = "Grant access",
                    onClick = { viewModel.grant() },
                    enabled = state.canGrant,
                    loading = state.busy,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            state.message?.let { msg ->
                item { Text(msg, style = MaterialTheme.typography.bodySmall, color = RrrColors.Success) }
            }
            state.error?.let { err ->
                item { Text(err.message, style = MaterialTheme.typography.bodySmall, color = RrrColors.Danger) }
            }

            if (state.admins.isNotEmpty()) {
                item { Spacer(Modifier.height(8.dp)); SectionHeader("Who has access") }
                items(state.admins, key = { it.uid }) { user ->
                    Column(
                        Modifier.fillMaxWidth().clip(RrrShapes.large)
                            .background(RrrColors.Surface).padding(14.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    user.displayName ?: user.email ?: user.uid,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = RrrColors.Bone,
                                )
                                Text(
                                    "${user.roleLabel}${if (user.active) "" else " · removed"}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (user.active) RrrColors.Gold else RrrColors.Slate,
                                )
                            }
                            if (user.active) {
                                QuietButton("Remove", onClick = { viewModel.revoke(user.uid) })
                            }
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}
