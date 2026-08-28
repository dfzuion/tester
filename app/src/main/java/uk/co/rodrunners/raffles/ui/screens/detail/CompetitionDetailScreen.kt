package uk.co.rodrunners.raffles.ui.screens.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import uk.co.rodrunners.raffles.R
import uk.co.rodrunners.raffles.core.Money
import uk.co.rodrunners.raffles.core.TimeFormat
import uk.co.rodrunners.raffles.data.model.RulesDocument
import uk.co.rodrunners.raffles.ui.components.CountdownText
import uk.co.rodrunners.raffles.ui.components.DemoDataBanner
import uk.co.rodrunners.raffles.ui.components.EntriesProgress
import uk.co.rodrunners.raffles.ui.components.GoldButton
import uk.co.rodrunners.raffles.ui.components.GoldRule
import uk.co.rodrunners.raffles.ui.components.PrizeImage
import uk.co.rodrunners.raffles.ui.components.SectionHeader
import uk.co.rodrunners.raffles.ui.components.StateHost
import uk.co.rodrunners.raffles.ui.components.StatusPill
import uk.co.rodrunners.raffles.ui.theme.Dimens
import uk.co.rodrunners.raffles.ui.theme.RrrColors
import uk.co.rodrunners.raffles.ui.theme.RrrType

/**
 * Prize, price, entries left, closing time and rules, in that order: everything
 * needed before spending money.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompetitionDetailScreen(
    onBack: () -> Unit,
    onEnter: (String) -> Unit,
    onShare: (String) -> Unit,
    onSignIn: () -> Unit,
    viewModel: DetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.cd_back), tint = RrrColors.Bone)
                    }
                },
                actions = {
                    val detail = (state as? uk.co.rodrunners.raffles.core.UiState.Success)?.data
                    if (detail?.signedIn == true) {
                        IconButton(onClick = viewModel::toggleFavourite) {
                            Icon(
                                if (detail.isFavourite) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                                stringResource(
                                    if (detail.isFavourite) R.string.cd_favourite_remove else R.string.cd_favourite_add
                                ),
                                tint = if (detail.isFavourite) RrrColors.Gold else RrrColors.Bone,
                            )
                        }
                    }
                    IconButton(onClick = { detail?.competition?.id?.let(onShare) }) {
                        Icon(Icons.Outlined.Share, stringResource(R.string.cd_share), tint = RrrColors.Bone)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            )
        },
        bottomBar = {
            val detail = (state as? uk.co.rodrunners.raffles.core.UiState.Success)?.data
            if (detail != null) {
                EnterBar(
                    detail = detail,
                    onEnter = { onEnter(detail.competition.id) },
                    onSignIn = onSignIn,
                )
            }
        },
    ) { padding ->
        StateHost(state = state, modifier = Modifier.padding(padding), onRetry = viewModel::load) { detail ->
            val c = detail.competition
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {

                Box(Modifier.fillMaxWidth().aspectRatio(1.1f).background(RrrColors.Surface)) {
                    PrizeImage(
                        c.heroImageUrl,
                        stringResource(R.string.cd_prize_image),
                        Modifier.fillMaxSize(),
                    )
                    Row(Modifier.align(Alignment.TopStart).padding(16.dp)) {
                        StatusPill(
                            when {
                                c.status == "drawn" -> "drawn"
                                c.isSoldOut -> "sold_out"
                                c.millisRemaining() in 1..21_600_000 -> "ending_soon"
                                else -> c.status
                            }
                        )
                    }
                }

                Column(Modifier.padding(Dimens.gutter)) {
                    if (c.isDemo) {
                        DemoDataBanner(Modifier.fillMaxWidth())
                        Spacer(Modifier.height(16.dp))
                    }

                    Text(c.brand.uppercase(), style = RrrType.Eyebrow, color = RrrColors.Gold)
                    Spacer(Modifier.height(6.dp))
                    Text(c.title, style = MaterialTheme.typography.headlineMedium, color = RrrColors.Bone)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Retail value ${Money.format(c.retailValuePence)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = RrrColors.Mist,
                    )

                    Spacer(Modifier.height(20.dp))
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            Money.formatCompact(c.entryPricePence),
                            style = MaterialTheme.typography.displayLarge,
                            color = RrrColors.Gold,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "per entry",
                            style = MaterialTheme.typography.bodyMedium,
                            color = RrrColors.Mist,
                            modifier = Modifier.padding(bottom = 4.dp),
                        )
                    }

                    Spacer(Modifier.height(20.dp))
                    EntriesProgress(c.entriesSold, c.maxEntries)

                    Spacer(Modifier.height(20.dp))
                    FactGrid(
                        facts = listOfNotNull(
                            "Entries sold" to "${c.entriesSold} of ${c.maxEntries}",
                            "Closes" to if (c.closesAtMillis > 0) TimeFormat.full(c.closesAtMillis) else "—",
                            "Limit per person" to "${c.perCustomerLimit} entries",
                            if (detail.myEntryCount > 0) "You hold" to "${detail.myEntryCount} entries" else null,
                            "Numbers issued" to if (c.allocationMode == "random") "At random" else "In sequence",
                            "Age" to "${c.minimumAge}+",
                        )
                    )

                    if (c.canEnter) {
                        Spacer(Modifier.height(16.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Closes in ", style = MaterialTheme.typography.bodyMedium, color = RrrColors.Mist)
                            CountdownText(c.closesAtMillis, showIcon = false, style = MaterialTheme.typography.titleMedium)
                        }
                    }

                    Spacer(Modifier.height(28.dp))
                    SectionHeader("The prize")
                    Spacer(Modifier.height(12.dp))
                    Text(c.description, style = MaterialTheme.typography.bodyLarge, color = RrrColors.Mist)

                    if (c.bundles.isNotEmpty()) {
                        Spacer(Modifier.height(28.dp))
                        SectionHeader("Entry bundles")
                        Spacer(Modifier.height(12.dp))
                        c.bundles.sortedBy { it.quantity }.forEach { bundle ->
                            val each = if (bundle.quantity > 0) bundle.pricePence / bundle.quantity else 0
                            Row(
                                Modifier.fillMaxWidth().padding(vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    bundle.label ?: "${bundle.quantity} entries",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = RrrColors.Bone,
                                )
                                Text(
                                    "${Money.format(bundle.pricePence)}  (${Money.format(each)} each)",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = RrrColors.Gold,
                                )
                            }
                        }
                    }

                    detail.previousWinner?.let { winner ->
                        Spacer(Modifier.height(28.dp))
                        SectionHeader("Result")
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "Entry ${winner.winningEntryNumber} won this raffle on " +
                                TimeFormat.date(winner.drawnAtMillis) + ", drawn for ${winner.winnerDisplayName}.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = RrrColors.Bone,
                        )
                    }

                    Spacer(Modifier.height(28.dp))
                    RulesSection(detail.rules)

                    Spacer(Modifier.height(48.dp))
                }
            }
        }
    }
}

@Composable
private fun FactGrid(facts: List<Pair<String, String>>) {
    Column {
        facts.forEachIndexed { index, (label, value) ->
            Row(
                Modifier.fillMaxWidth().padding(vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(label, style = MaterialTheme.typography.bodyMedium, color = RrrColors.Mist)
                Text(value, style = MaterialTheme.typography.bodyMedium, color = RrrColors.Bone)
            }
            if (index != facts.lastIndex) HorizontalDivider(thickness = 1.dp, color = RrrColors.Hairline)
        }
    }
}

/** Rules come from the raffle's own Firestore document. If there isn't one, say so. */
@Composable
private fun RulesSection(rules: RulesDocument?) {
    SectionHeader("Competition rules")
    Spacer(Modifier.height(12.dp))
    if (rules == null) {
        Text(
            "Rules for this raffle haven't been published yet. Entries can't be purchased until they are.",
            style = MaterialTheme.typography.bodyMedium,
            color = RrrColors.Warning,
        )
        return
    }
    if (rules.legalReviewRequired) {
        Text(
            "These rules are placeholder copy pending legal review.",
            style = MaterialTheme.typography.labelSmall,
            color = RrrColors.Warning,
        )
        Spacer(Modifier.height(12.dp))
    }
    rules.sections.forEach { section ->
        Text(section.heading, style = MaterialTheme.typography.titleMedium, color = RrrColors.Bone)
        Spacer(Modifier.height(4.dp))
        Text(section.body, style = MaterialTheme.typography.bodyMedium, color = RrrColors.Mist)
        Spacer(Modifier.height(16.dp))
    }
    GoldRule(width = 24.dp)
    Spacer(Modifier.height(8.dp))
    Text(
        "Version ${rules.version}",
        style = MaterialTheme.typography.labelSmall,
        color = RrrColors.Slate,
    )
}

@Composable
private fun EnterBar(
    detail: CompetitionDetail,
    onEnter: () -> Unit,
    onSignIn: () -> Unit,
) {
    val c = detail.competition
    Column(
        Modifier
            
            .navigationBarsPadding()
            .padding(horizontal = Dimens.gutter, vertical = 12.dp)
    ) {
        HorizontalDivider(thickness = 1.dp, color = RrrColors.Hairline)
        Spacer(Modifier.height(12.dp))
        when {
            !detail.signedIn -> GoldButton(
                text = "Log in to enter",
                onClick = onSignIn,
                modifier = Modifier.fillMaxWidth(),
            )
            c.status == "drawn" -> Text(
                "This raffle has been drawn. The winning number is shown above.",
                style = MaterialTheme.typography.bodyMedium,
                color = RrrColors.Mist,
            )
            c.isSoldOut -> GoldButton("Sold out", {}, Modifier.fillMaxWidth(), enabled = false)
            !c.canEnter -> GoldButton("Closed", {}, Modifier.fillMaxWidth(), enabled = false)
            detail.atPersonalLimit -> Column {
                GoldButton("Entry limit reached", {}, Modifier.fillMaxWidth(), enabled = false)
                Spacer(Modifier.height(6.dp))
                Text(
                    "You hold the maximum ${c.perCustomerLimit} entries for this raffle.",
                    style = MaterialTheme.typography.labelSmall,
                    color = RrrColors.Mist,
                )
            }
            else -> Column {
                GoldButton(
                    text = stringResource(R.string.action_enter_now),
                    onClick = onEnter,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "You can buy up to ${detail.remainingAllowance} more ${if (detail.remainingAllowance == 1) "entry" else "entries"}.",
                    style = MaterialTheme.typography.labelSmall,
                    color = RrrColors.Slate,
                )
            }
        }
    }
}
