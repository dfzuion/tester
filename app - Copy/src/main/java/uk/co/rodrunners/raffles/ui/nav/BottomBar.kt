package uk.co.rodrunners.raffles.ui.nav

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Sell
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Sell
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import uk.co.rodrunners.raffles.R
import uk.co.rodrunners.raffles.ui.theme.RrrColors

data class TabItem(
    val route: String,
    val labelRes: Int,
    val icon: ImageVector,
    val selectedIcon: ImageVector,
)

val bottomTabs = listOf(
    TabItem(Routes.HOME, R.string.nav_home, Icons.Outlined.Home, Icons.Filled.Home),
    TabItem(Routes.COMPETITIONS, R.string.nav_competitions, Icons.Outlined.Sell, Icons.Filled.Sell),
    TabItem(Routes.RESULTS, R.string.nav_results, Icons.Outlined.EmojiEvents, Icons.Filled.EmojiEvents),
    TabItem(Routes.ACCOUNT, R.string.nav_account, Icons.Outlined.Person, Icons.Filled.Person),
)

@Composable
fun RrrBottomBar(navController: NavHostController) {
    val entry by navController.currentBackStackEntryAsState()
    val current = entry?.destination

    androidx.compose.foundation.layout.Column {
        HorizontalDivider(thickness = 1.dp, color = RrrColors.Hairline)
        NavigationBar(
            containerColor = RrrColors.Ink,
            tonalElevation = 0.dp,
            modifier = Modifier.height(72.dp),
        ) {
            bottomTabs.forEach { tab ->
                val selected = current?.hierarchy?.any { it.route == tab.route } == true
                NavigationBarItem(
                    selected = selected,
                    onClick = {
                        if (!selected) {
                            navController.navigate(tab.route) {
                                popUpTo(navController.graph.startDestinationId) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    },
                    icon = {
                        Icon(
                            imageVector = if (selected) tab.selectedIcon else tab.icon,
                            contentDescription = null,
                            modifier = Modifier.size(22.dp),
                        )
                    },
                    label = { Text(stringResource(tab.labelRes)) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = RrrColors.Gold,
                        selectedTextColor = RrrColors.Gold,
                        unselectedIconColor = RrrColors.Slate,
                        unselectedTextColor = RrrColors.Slate,
                        indicatorColor = RrrColors.Ink,
                    ),
                )
            }
        }
    }
}
