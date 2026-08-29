package uk.co.rodrunners.raffles.ui.nav

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import uk.co.rodrunners.raffles.data.repository.AuthState
import uk.co.rodrunners.raffles.payment.StripePaymentGateway
import uk.co.rodrunners.raffles.ui.screens.account.AccountScreen
import uk.co.rodrunners.raffles.ui.screens.account.NotificationPreferencesScreen
import uk.co.rodrunners.raffles.ui.screens.account.NotificationsScreen
import uk.co.rodrunners.raffles.ui.screens.account.PaymentMethodsScreen
import uk.co.rodrunners.raffles.ui.screens.account.PersonalDetailsScreen
import uk.co.rodrunners.raffles.ui.screens.account.SecurityScreen
import uk.co.rodrunners.raffles.ui.screens.admin.AdminScreen
import uk.co.rodrunners.raffles.ui.screens.credit.CreditScreen
import uk.co.rodrunners.raffles.ui.screens.game.GameScreen
import uk.co.rodrunners.raffles.ui.screens.spin.SpinScreen
import uk.co.rodrunners.raffles.ui.screens.admin.AdminUsersScreen
import uk.co.rodrunners.raffles.ui.screens.admin.CompetitionEditorScreen
import uk.co.rodrunners.raffles.ui.screens.admin.CouponsScreen
import uk.co.rodrunners.raffles.ui.screens.admin.InstantWinsScreen
import uk.co.rodrunners.raffles.ui.screens.auth.ForgotPasswordScreen
import uk.co.rodrunners.raffles.ui.screens.auth.LoginScreen
import uk.co.rodrunners.raffles.ui.screens.auth.RegisterScreen
import uk.co.rodrunners.raffles.ui.screens.auth.VerifyEmailScreen
import uk.co.rodrunners.raffles.ui.screens.auth.WelcomeScreen
import uk.co.rodrunners.raffles.ui.screens.checkout.CheckoutScreen
import uk.co.rodrunners.raffles.ui.screens.competitions.CompetitionsScreen
import uk.co.rodrunners.raffles.ui.screens.detail.CompetitionDetailScreen
import uk.co.rodrunners.raffles.ui.screens.favourites.FavouritesScreen
import uk.co.rodrunners.raffles.ui.screens.home.HomeScreen
import uk.co.rodrunners.raffles.ui.screens.legal.LegalScreen
import uk.co.rodrunners.raffles.ui.screens.legal.RulesScreen
import uk.co.rodrunners.raffles.ui.screens.orders.OrderDetailScreen
import uk.co.rodrunners.raffles.ui.screens.orders.OrdersScreen
import uk.co.rodrunners.raffles.ui.screens.results.ResultsScreen
import uk.co.rodrunners.raffles.ui.screens.search.SearchScreen
import uk.co.rodrunners.raffles.ui.screens.support.FaqScreen
import uk.co.rodrunners.raffles.ui.screens.support.SupportScreen
import uk.co.rodrunners.raffles.ui.screens.support.TicketThreadScreen
import uk.co.rodrunners.raffles.ui.screens.tickets.TicketsScreen

private const val ANIM = 220

/**
 * One graph for the whole app. Browsing stays open to signed-out visitors; only
 * routes touching money or personal data force a login first.
 */
@Composable
fun RootNavHost(
    navController: NavHostController,
    authState: AuthState,
    gateway: StripePaymentGateway,
    startDestination: String,
    modifier: Modifier = Modifier,
    onShare: (String) -> Unit,
) {
    val signedIn = authState is AuthState.SignedIn

    // A sign-out anywhere in the app returns the customer to the welcome screen
    // rather than leaving a signed-in screen on display.
    LaunchedEffect(signedIn) {
        if (!signedIn) {
            val current = navController.currentBackStackEntry?.destination?.route
            if (current != null && current in privateRoutes) {
                navController.navigate(Routes.WELCOME) {
                    popUpTo(navController.graph.id) { inclusive = true }
                }
            }
        }
    }

    fun requireSignIn(destination: () -> Unit) {
        if (signedIn) destination() else navController.navigate(Routes.LOGIN)
    }

    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier,
        enterTransition = { slideInHorizontally(tween(ANIM)) { it / 12 } + fadeIn(tween(ANIM)) },
        exitTransition = { fadeOut(tween(ANIM)) },
        popEnterTransition = { fadeIn(tween(ANIM)) },
        popExitTransition = { slideOutHorizontally(tween(ANIM)) { it / 12 } + fadeOut(tween(ANIM)) },
    ) {
        composable(Routes.WELCOME) {
            WelcomeScreen(
                heroImageUrl = null,
                onBrowse = {
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.WELCOME) { inclusive = true }
                    }
                },
                onLogin = { navController.navigate(Routes.LOGIN) },
            )
        }

        composable(Routes.LOGIN) {
            LoginScreen(
                onBack = { navController.popBackStack() },
                onLoggedIn = {
                    // Coming from the welcome screen there is nothing worth
                    // returning to; from anywhere else, go back to what they
                    // were doing before we asked them to log in.
                    if (navController.previousBackStackEntry?.destination?.route == Routes.WELCOME) {
                        navController.navigate(Routes.HOME) {
                            popUpTo(Routes.WELCOME) { inclusive = true }
                        }
                    } else {
                        navController.popBackStack()
                    }
                },
                onRegister = { navController.navigate(Routes.REGISTER) },
                onForgotPassword = { navController.navigate(Routes.FORGOT_PASSWORD) },
            )
        }

        composable(Routes.REGISTER) {
            RegisterScreen(
                onBack = { navController.popBackStack() },
                onRegistered = {
                    navController.navigate(Routes.VERIFY_EMAIL) {
                        popUpTo(Routes.LOGIN) { inclusive = true }
                    }
                },
                onOpenRules = { navController.navigate(Routes.legal("terms")) },
            )
        }

        composable(Routes.FORGOT_PASSWORD) {
            ForgotPasswordScreen(onBack = { navController.popBackStack() })
        }

        composable(Routes.VERIFY_EMAIL) {
            VerifyEmailScreen(
                email = (authState as? AuthState.SignedIn)?.email.orEmpty(),
                onVerified = {
                    navController.navigate(Routes.HOME) {
                        popUpTo(navController.graph.id) { inclusive = true }
                    }
                },
                onSignOut = {
                    navController.navigate(Routes.WELCOME) {
                        popUpTo(navController.graph.id) { inclusive = true }
                    }
                },
            )
        }

        composable(Routes.HOME) {
            HomeScreen(
                onOpenCompetition = { navController.navigate(Routes.competitionDetail(it)) },
                onOpenSearch = { navController.navigate(Routes.SEARCH) },
                onOpenNotifications = { requireSignIn { navController.navigate(Routes.NOTIFICATIONS) } },
                onOpenResults = { navController.navigate(Routes.RESULTS) },
                onOpenAllCompetitions = { navController.navigate(Routes.COMPETITIONS) },
                onOpenTickets = { requireSignIn { navController.navigate(Routes.TICKETS) } },
                onOpenGame = { navController.navigate(Routes.GAME) },
                onOpenSpin = { requireSignIn { navController.navigate(Routes.SPIN) } },
                onOpenCredit = { requireSignIn { navController.navigate(Routes.CREDIT) } },
            )
        }

        composable(Routes.COMPETITIONS) {
            CompetitionsScreen(
                onOpenCompetition = { navController.navigate(Routes.competitionDetail(it)) },
                onOpenSearch = { navController.navigate(Routes.SEARCH) },
            )
        }

        composable(Routes.RESULTS) {
            ResultsScreen(
                onOpenCompetition = { navController.navigate(Routes.competitionDetail(it)) },
                onBrowse = { navController.navigate(Routes.COMPETITIONS) },
            )
        }

        composable(Routes.ACCOUNT) {
            AccountScreen(
                onOpenAdminUsers = { navController.navigate(Routes.ADMIN_USERS) },
                onOpenCredit = { navController.navigate(Routes.CREDIT) },
                onOpenTickets = { navController.navigate(Routes.TICKETS) },
                onOpenOrders = { navController.navigate(Routes.ORDERS) },
                onOpenWins = { navController.navigate(Routes.RESULTS) },
                onOpenFavourites = { navController.navigate(Routes.FAVOURITES) },
                onOpenPaymentMethods = { navController.navigate(Routes.PAYMENT_METHODS) },
                onOpenDetails = { navController.navigate(Routes.PERSONAL_DETAILS) },
                onOpenNotifications = { navController.navigate(Routes.NOTIFICATIONS) },
                onOpenSecurity = { navController.navigate(Routes.SECURITY) },
                onOpenSupport = { navController.navigate(Routes.SUPPORT) },
                onOpenFaq = { navController.navigate(Routes.FAQ) },
                onOpenLegal = { navController.navigate(Routes.legal(it)) },
                onOpenAdmin = { navController.navigate(Routes.ADMIN) },
                onSignedOut = {
                    navController.navigate(Routes.WELCOME) {
                        popUpTo(navController.graph.id) { inclusive = true }
                    }
                },
                onSignIn = { navController.navigate(Routes.LOGIN) },
            )
        }

        composable(Routes.SEARCH) {
            SearchScreen(
                onBack = { navController.popBackStack() },
                onOpenCompetition = { navController.navigate(Routes.competitionDetail(it)) },
            )
        }

        composable(
            Routes.COMPETITION_DETAIL,
            arguments = listOf(navArgument("competitionId") { type = NavType.StringType }),
        ) {
            CompetitionDetailScreen(
                onBack = { navController.popBackStack() },
                onEnter = { id -> requireSignIn { navController.navigate(Routes.checkout(id)) } },
                onShare = onShare,
                onSignIn = { navController.navigate(Routes.LOGIN) },
            )
        }

        composable(
            Routes.CHECKOUT,
            arguments = listOf(navArgument("competitionId") { type = NavType.StringType }),
        ) { entry ->
            val competitionId = entry.arguments?.getString("competitionId").orEmpty()
            CheckoutScreen(
                gateway = gateway,
                onClose = { navController.popBackStack() },
                onOpenTickets = {
                    navController.navigate(Routes.TICKETS) {
                        popUpTo(Routes.HOME)
                    }
                },
                onOpenOrder = { navController.navigate(Routes.orderDetail(it)) },
                onOpenRules = { navController.navigate(Routes.rules(competitionId)) },
            )
        }

        composable(Routes.TICKETS) {
            TicketsScreen(
                onBack = { navController.popBackStack() },
                onOpenCompetition = { navController.navigate(Routes.competitionDetail(it)) },
                onBrowse = { navController.navigate(Routes.COMPETITIONS) },
            )
        }

        composable(Routes.ORDERS) {
            OrdersScreen(
                onBack = { navController.popBackStack() },
                onOpenOrder = { navController.navigate(Routes.orderDetail(it)) },
                onBrowse = { navController.navigate(Routes.COMPETITIONS) },
            )
        }

        composable(
            Routes.ORDER_DETAIL,
            arguments = listOf(navArgument("orderId") { type = NavType.StringType }),
        ) {
            OrderDetailScreen(
                onBack = { navController.popBackStack() },
                onOpenSupport = { navController.navigate(Routes.SUPPORT) },
            )
        }

        composable(Routes.FAVOURITES) {
            FavouritesScreen(
                onBack = { navController.popBackStack() },
                onOpenCompetition = { navController.navigate(Routes.competitionDetail(it)) },
                onBrowse = { navController.navigate(Routes.COMPETITIONS) },
            )
        }

        composable(Routes.PERSONAL_DETAILS) {
            PersonalDetailsScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.NOTIFICATION_PREFS) {
            NotificationPreferencesScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.PAYMENT_METHODS) {
            PaymentMethodsScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.SECURITY) {
            SecurityScreen(
                onBack = { navController.popBackStack() },
                onAccountDeleted = {
                    navController.navigate(Routes.WELCOME) {
                        popUpTo(navController.graph.id) { inclusive = true }
                    }
                },
            )
        }
        composable(Routes.NOTIFICATIONS) {
            NotificationsScreen(
                onBack = { navController.popBackStack() },
                onOpenLink = { link -> navigateDeepLink(navController, link) },
            )
        }

        composable(Routes.SUPPORT) {
            SupportScreen(
                orderId = null,
                onBack = { navController.popBackStack() },
                onOpenTicket = { navController.navigate(Routes.ticketThread(it)) },
                onOpenFaq = { navController.navigate(Routes.FAQ) },
            )
        }
        composable(
            Routes.TICKET_THREAD,
            arguments = listOf(navArgument("ticketId") { type = NavType.StringType }),
        ) {
            TicketThreadScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.FAQ) {
            FaqScreen(
                onBack = { navController.popBackStack() },
                onOpenSupport = { navController.navigate(Routes.SUPPORT) },
            )
        }

        composable(
            Routes.LEGAL,
            arguments = listOf(navArgument("documentId") { type = NavType.StringType }),
        ) {
            LegalScreen(onBack = { navController.popBackStack() })
        }
        composable(
            Routes.RULES,
            arguments = listOf(navArgument("competitionId") { type = NavType.StringType }),
        ) {
            RulesScreen(onBack = { navController.popBackStack() })
        }

        composable(Routes.ADMIN) {
            AdminScreen(
                onBack = { navController.popBackStack() },
                onOpenCompetition = { navController.navigate(Routes.competitionDetail(it)) },
                onNewCompetition = { navController.navigate(Routes.ADMIN_NEW_COMPETITION) },
                onEditCompetition = { navController.navigate(Routes.adminEditCompetition(it)) },
                onOpenInstantWins = { navController.navigate(Routes.adminInstantWins(it)) },
                onOpenAdminUsers = { navController.navigate(Routes.ADMIN_USERS) },
                onOpenCoupons = { navController.navigate(Routes.ADMIN_COUPONS) },
            )
        }

        composable(Routes.CREDIT) {
            CreditScreen(
                onBack = { navController.popBackStack() },
                onOpenSpin = { navController.navigate(Routes.SPIN) },
            )
        }

        composable(Routes.SPIN) {
            SpinScreen(onBack = { navController.popBackStack() })
        }

        composable(Routes.GAME) {
            GameScreen(onBack = { navController.popBackStack() })
        }

        composable(Routes.ADMIN_COUPONS) {
            CouponsScreen(onBack = { navController.popBackStack() })
        }

        composable(Routes.ADMIN_USERS) {
            AdminUsersScreen(onBack = { navController.popBackStack() })
        }

        composable(Routes.ADMIN_NEW_COMPETITION) {
            CompetitionEditorScreen(
                competitionId = null,
                onBack = { navController.popBackStack() },
                onSaved = { navController.popBackStack() },
            )
        }

        composable(Routes.ADMIN_INSTANT_WINS) { entry ->
            InstantWinsScreen(
                competitionId = entry.arguments?.getString("competitionId").orEmpty(),
                onBack = { navController.popBackStack() },
            )
        }

        composable(Routes.ADMIN_EDIT_COMPETITION) { entry ->
            CompetitionEditorScreen(
                competitionId = entry.arguments?.getString("competitionId"),
                onBack = { navController.popBackStack() },
                onSaved = { navController.popBackStack() },
            )
        }
    }
}

/** Routes that must not stay on screen once someone is signed out. */
private val privateRoutes = setOf(
    Routes.TICKETS,
    Routes.ORDERS,
    Routes.ORDER_DETAIL,
    Routes.FAVOURITES,
    Routes.PERSONAL_DETAILS,
    Routes.NOTIFICATION_PREFS,
    Routes.SECURITY,
    Routes.NOTIFICATIONS,
    Routes.PAYMENT_METHODS,
    Routes.CHECKOUT,
    Routes.ADMIN,
    Routes.SUPPORT,
    Routes.TICKET_THREAD,
)

/** Shared by push notifications and https links so both land on the same screen. */
fun navigateDeepLink(navController: NavHostController, link: String) {
    val target = when {
        link.startsWith("rrr://competition/") -> Routes.competitionDetail(link.substringAfterLast('/'))
        link.startsWith("rrr://orders/") -> Routes.orderDetail(link.substringAfterLast('/'))
        link.startsWith("rrr://results") -> Routes.RESULTS
        link.startsWith("rrr://tickets") -> Routes.TICKETS
        link.startsWith("rrr://support/") -> Routes.ticketThread(link.substringAfterLast('/'))
        link.contains("/competition/") -> Routes.competitionDetail(link.substringAfterLast('/'))
        link.contains("/results") -> Routes.RESULTS
        else -> Routes.HOME
    }
    navController.navigate(target) { launchSingleTop = true }
}
