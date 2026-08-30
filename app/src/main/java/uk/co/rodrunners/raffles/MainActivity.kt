package uk.co.rodrunners.raffles

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import uk.co.rodrunners.raffles.data.repository.AccountRepository
import uk.co.rodrunners.raffles.data.repository.AuthRepository
import uk.co.rodrunners.raffles.data.repository.AuthState
import uk.co.rodrunners.raffles.payment.StripePaymentGateway
import uk.co.rodrunners.raffles.ui.nav.RootNavHost
import uk.co.rodrunners.raffles.ui.nav.Routes
import uk.co.rodrunners.raffles.ui.nav.RrrBottomBar
import uk.co.rodrunners.raffles.ui.nav.bottomTabs
import uk.co.rodrunners.raffles.ui.nav.navigateDeepLink
import uk.co.rodrunners.raffles.ui.theme.RodRunnersTheme
import uk.co.rodrunners.raffles.ui.theme.RrrColors

/** Holds what the shell needs: who is signed in, and whether this device's push token is registered. */
@HiltViewModel
class ShellViewModel @Inject constructor(
    auth: AuthRepository,
    private val accounts: AccountRepository,
) : ViewModel() {

    val authState: StateFlow<AuthState> =
        auth.authState().stateIn(viewModelScope, SharingStarted.Eagerly, AuthState.Loading)

    private var tokenSyncedFor: String? = null

    /** Safe to call repeatedly; the token is written once per signed-in account. */
    fun syncPushToken(uid: String) {
        if (tokenSyncedFor == uid) return
        viewModelScope.launch {
            runCatching { accounts.syncPushToken() }.onSuccess { tokenSyncedFor = uid }
        }
    }
}

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var gateway: StripePaymentGateway

    private var pendingDeepLink: String? = null
    private var navController: NavHostController? = null
    private var authResolved = false

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* declining is fine */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        splash.setKeepOnScreenCondition { !authResolved }
        pendingDeepLink = intent?.data?.toString()

        setContent {
            RodRunnersTheme {
                val shell: ShellViewModel = hiltViewModel()
                val authState by shell.authState.collectAsStateWithLifecycle()
                SideEffect { authResolved = authState !is AuthState.Loading }

                val nav = rememberNavController()
                navController = nav
                val signedIn = authState as? AuthState.SignedIn

                LaunchedEffect(signedIn?.uid) {
                    val uid = signedIn?.uid ?: return@LaunchedEffect
                    shell.syncPushToken(uid)
                    // Only ask if we do not already have it. Firing the
                    // request every time the signed-in user changes is noise,
                    // and after two refusals Android stops showing it anyway -
                    // so the ones that matter are the ones we do not waste.
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                        ContextCompat.checkSelfPermission(
                            this@MainActivity,
                            Manifest.permission.POST_NOTIFICATIONS,
                        ) != PackageManager.PERMISSION_GRANTED
                    ) {
                        notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }

                // The start destination is fixed on the first resolved auth state.
                // Changing it later rebuilds the graph and discards the back stack,
                // so signing in mid-session is handled by navigation instead.
                var startDestination by rememberSaveable { mutableStateOf<String?>(null) }
                if (startDestination == null && authState !is AuthState.Loading) {
                    startDestination = when {
                        signedIn == null -> Routes.WELCOME
                        !signedIn.emailVerified -> Routes.VERIFY_EMAIL
                        else -> Routes.HOME
                    }
                }

                startDestination?.let { start ->
                    // A link that arrived during a cold start is held until the
                    // graph exists, so nothing is dropped on launch.
                    LaunchedEffect(start) {
                        pendingDeepLink?.let { link ->
                            pendingDeepLink = null
                            navigateDeepLink(nav, link)
                        }
                    }

                    val entry by nav.currentBackStackEntryAsState()
                    val showBottomBar = bottomTabs.any { it.route == entry?.destination?.route }

                    Scaffold(
                        containerColor = androidx.compose.ui.graphics.Color.Transparent,
                        bottomBar = { if (showBottomBar) RrrBottomBar(nav) },
                    ) { padding ->
                        RootNavHost(
                            navController = nav,
                            authState = authState,
                            gateway = gateway,
                            startDestination = start,
                            modifier = Modifier.padding(
                                bottom = if (showBottomBar) padding.calculateBottomPadding() else 0.dp,
                            ),
                            onShare = ::shareCompetition,
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val link = intent.data?.toString() ?: return
        val nav = navController
        if (nav != null) navigateDeepLink(nav, link) else pendingDeepLink = link
    }

    private fun shareCompetition(competitionId: String) {
        val url = "https://rodrunners.co.uk/competition/$competitionId"
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, "Have a look at this raffle on Rod Runners Raffles: $url")
        }
        startActivity(Intent.createChooser(send, "Share this raffle"))
    }
}
