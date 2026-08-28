package uk.co.rodrunners.raffles.ui.screens.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import uk.co.rodrunners.raffles.R
import uk.co.rodrunners.raffles.ui.components.GoldButton
import uk.co.rodrunners.raffles.ui.components.OutlineButton
import uk.co.rodrunners.raffles.ui.components.PrizeImage
import uk.co.rodrunners.raffles.ui.components.Wordmark
import uk.co.rodrunners.raffles.ui.theme.Dimens
import uk.co.rodrunners.raffles.ui.theme.RrrColors

/**
 * The welcome screen is the one place the brand gets the whole screen: a single
 * bank photograph, the wordmark, and two choices. No carousel, no feature tour.
 *
 * @param heroImageUrl served from Firebase Storage so marketing can change the
 * opening image without an app release. Falls back to flat ink if it fails.
 */
@Composable
fun WelcomeScreen(
    heroImageUrl: String?,
    onBrowse: () -> Unit,
    onLogin: () -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        PrizeImage(
            url = heroImageUrl,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
        )
        Box(Modifier.fillMaxSize().background(RrrColors.ScrimBottom))
        Box(Modifier.fillMaxSize().background(RrrColors.ScrimTop))

        // The mark sits top centre and large - it is the first thing anyone
        // sees, so it leads rather than being tucked in above the buttons.
        Column(
            Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(top = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Wordmark()
            Spacer(Modifier.height(14.dp))
            Text(
                stringResource(R.string.brand_strapline),
                style = MaterialTheme.typography.bodyMedium,
                color = RrrColors.Bone,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = Dimens.gutter),
            )
        }

        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = Dimens.gutter, vertical = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            GoldButton(
                text = stringResource(R.string.action_browse),
                onClick = onBrowse,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            OutlineButton(
                text = stringResource(R.string.action_login_register),
                onClick = onLogin,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(20.dp))
            Text(
                stringResource(R.string.legal_notice_placeholder),
                style = MaterialTheme.typography.labelSmall,
                color = RrrColors.Slate,
                textAlign = TextAlign.Center,
            )
        }
    }
}
