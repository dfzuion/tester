package uk.co.rodrunners.raffles.ui.screens.account

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.AdminPanelSettings
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.ConfirmationNumber
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Redeem
import androidx.compose.material.icons.outlined.PrivacyTip
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import uk.co.rodrunners.raffles.ui.components.GoldRule
import uk.co.rodrunners.raffles.ui.components.PrizeImage
import uk.co.rodrunners.raffles.ui.components.SectionHeader
import uk.co.rodrunners.raffles.ui.theme.Dimens
import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.ExperimentalFoundationApi
import uk.co.rodrunners.raffles.BuildConfig
import uk.co.rodrunners.raffles.core.Money
import uk.co.rodrunners.raffles.ui.theme.RrrColors
import uk.co.rodrunners.raffles.ui.theme.RrrType

data class AccountAction(
    val label: String,
    val icon: ImageVector,
    val onClick: () -> Unit,
    val trailing: String? = null,
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun AccountScreen(
    onOpenAdminUsers: () -> Unit = {},
    onOpenCredit: () -> Unit = {},
    onOpenTickets: () -> Unit,
    onOpenOrders: () -> Unit,
    onOpenWins: () -> Unit,
    onOpenFavourites: () -> Unit,
    onOpenPaymentMethods: () -> Unit,
    onOpenDetails: () -> Unit,
    onOpenNotifications: () -> Unit,
    onOpenSecurity: () -> Unit,
    onOpenSupport: () -> Unit,
    onOpenFaq: () -> Unit,
    onOpenLegal: (String) -> Unit,
    onOpenAdmin: () -> Unit,
    onSignedOut: () -> Unit,
    onSignIn: () -> Unit,
    viewModel: AccountViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val profile = state.profile

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text("Account", style = MaterialTheme.typography.titleLarge) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = RrrColors.Bone,
                ),
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            if (profile == null) {
                Column(
                    Modifier.fillMaxWidth().padding(Dimens.gutter),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Spacer(Modifier.height(40.dp))
                    GoldRule(width = 28.dp)
                    Spacer(Modifier.height(16.dp))
                    Text("You're browsing as a guest", style = MaterialTheme.typography.titleLarge, color = RrrColors.Bone)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Log in to buy entries, see your numbers and check your results.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = RrrColors.Mist,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                    Spacer(Modifier.height(24.dp))
                    uk.co.rodrunners.raffles.ui.components.GoldButton(
                        "Log in or register", onSignIn, Modifier.fillMaxWidth()
                    )
                }
            } else {
                ProfileHeader(profile.displayName, profile.email.orEmpty(), profile.photoUrl)

                if (!state.emailVerified) {
                    Spacer(Modifier.height(8.dp))
                    Box(Modifier.padding(horizontal = Dimens.gutter)) {
                        uk.co.rodrunners.raffles.ui.components.DemoDataBanner()
                    }
                }

                Spacer(Modifier.height(24.dp))
                SectionHeader("Your raffles", Modifier.padding(horizontal = Dimens.gutter))
                Spacer(Modifier.height(8.dp))
                AccountGroup(
                    listOf(
                        AccountAction("My tickets", Icons.Outlined.ConfirmationNumber, onOpenTickets),
                        AccountAction("My orders", Icons.Outlined.ReceiptLong, onOpenOrders),
                        AccountAction("My wins", Icons.Outlined.EmojiEvents, onOpenWins),
                        AccountAction("Saved raffles", Icons.Outlined.Bookmark, onOpenFavourites),
                        AccountAction(
                            "My credit",
                            Icons.Outlined.Redeem,
                            onOpenCredit,
                            trailing = Money.format(profile.creditBalancePence),
                        ),
                    )
                )

                Spacer(Modifier.height(24.dp))
                SectionHeader("Your account", Modifier.padding(horizontal = Dimens.gutter))
                Spacer(Modifier.height(8.dp))
                AccountGroup(
                    listOf(
                        AccountAction("Personal details", Icons.Outlined.Person, onOpenDetails),
                        AccountAction("Payment methods", Icons.Outlined.CreditCard, onOpenPaymentMethods),
                        AccountAction(
                            "Notifications", Icons.Outlined.Notifications, onOpenNotifications,
                            trailing = if (state.unreadCount > 0) "${state.unreadCount} new" else null,
                        ),
                        AccountAction("Password and security", Icons.Outlined.Lock, onOpenSecurity),
                    )
                )

                Spacer(Modifier.height(24.dp))
                SectionHeader("Help and legal", Modifier.padding(horizontal = Dimens.gutter))
                Spacer(Modifier.height(8.dp))
                AccountGroup(
                    listOfNotNull(
                        AccountAction("Help and support", Icons.AutoMirrored.Outlined.HelpOutline, onOpenSupport),
                        AccountAction("FAQs", Icons.Outlined.Description, onOpenFaq),
                        AccountAction("Terms and conditions", Icons.Outlined.Description, onClick = { onOpenLegal("terms") }),
                        AccountAction("Privacy policy", Icons.Outlined.PrivacyTip, onClick = { onOpenLegal("privacy") }),
                        AccountAction("Company information", Icons.Outlined.Description, onClick = { onOpenLegal("company") }),
                        if (state.isAdmin) AccountAction("Admin", Icons.Outlined.AdminPanelSettings, onOpenAdmin) else null,
                    )
                )

                Spacer(Modifier.height(24.dp))
                AccountGroup(
                    listOf(
                        AccountAction("Log out", Icons.AutoMirrored.Outlined.Logout, onClick = {
                            viewModel.signOut()
                            onSignedOut()
                        })
                    )
                )

                profile.referralCode?.let { code ->
                    Spacer(Modifier.height(24.dp))
                    Column(Modifier.padding(horizontal = Dimens.gutter)) {
                        Text("Your referral code", style = MaterialTheme.typography.labelSmall, color = RrrColors.Slate)
                        Spacer(Modifier.height(4.dp))
                        Text(code, style = RrrType.Numeric, color = RrrColors.Gold)
                    }
                }
                // The very first administrator has to get in somehow, and the
                // Admin entry above only appears once you already are one.
                // Long-pressing the version line opens the Administrators screen,
                // which is itself useless without the setup key from Secret
                // Manager - so this reveals nothing to a curious customer.
                Spacer(Modifier.height(28.dp))
                Text(
                    "Version ${BuildConfig.VERSION_NAME}",
                    style = MaterialTheme.typography.labelSmall,
                    color = RrrColors.Slate,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Dimens.gutter)
                        .combinedClickable(
                            onClick = {},
                            onLongClick = onOpenAdminUsers,
                        ),
                    textAlign = TextAlign.Center,
                )

                Spacer(Modifier.height(40.dp))
            }
        }
    }
}

@Composable
private fun ProfileHeader(name: String, email: String, photoUrl: String?) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(84.dp)
                .clip(CircleShape)
                .background(RrrColors.SurfaceRaised),
            contentAlignment = Alignment.Center,
        ) {
            if (photoUrl != null) {
                PrizeImage(photoUrl, null, Modifier.fillMaxSize())
            } else {
                Text(
                    name.take(1).uppercase(),
                    style = MaterialTheme.typography.headlineMedium,
                    color = RrrColors.Gold,
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(name, style = MaterialTheme.typography.titleLarge, color = RrrColors.Bone)
        Text(email, style = MaterialTheme.typography.bodySmall, color = RrrColors.Mist)
    }
}

@Composable
fun AccountGroup(actions: List<AccountAction>) {
    Column(
        Modifier
            .padding(horizontal = Dimens.gutter)
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(RrrColors.Surface)
    ) {
        actions.forEachIndexed { index, action ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable(onClick = action.onClick)
                    .defaultMinSize(minHeight = Dimens.minTouchTarget)
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(action.icon, contentDescription = null, tint = RrrColors.Mist, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(14.dp))
                Text(
                    action.label,
                    style = MaterialTheme.typography.bodyLarge,
                    color = RrrColors.Bone,
                    modifier = Modifier.weight(1f),
                )
                action.trailing?.let {
                    Text(it, style = MaterialTheme.typography.labelSmall, color = RrrColors.Gold)
                    Spacer(Modifier.width(8.dp))
                }
                Icon(
                    Icons.AutoMirrored.Filled.ArrowForwardIos,
                    contentDescription = null,
                    tint = RrrColors.Slate,
                    modifier = Modifier.size(14.dp),
                )
            }
            if (index != actions.lastIndex) {
                HorizontalDivider(
                    Modifier.padding(start = 50.dp),
                    thickness = 1.dp,
                    color = RrrColors.Hairline,
                )
            }
        }
    }
}
