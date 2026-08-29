package uk.co.rodrunners.raffles.ui.screens.home

import androidx.compose.animation.Crossfade
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Search
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.delay
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import uk.co.rodrunners.raffles.R
import uk.co.rodrunners.raffles.core.TimeFormat
import uk.co.rodrunners.raffles.data.model.Banner
import uk.co.rodrunners.raffles.data.model.TicketGroup
import uk.co.rodrunners.raffles.data.model.Winner
import uk.co.rodrunners.raffles.ui.components.CompetitionRow
import uk.co.rodrunners.raffles.ui.components.CompetitionTile
import uk.co.rodrunners.raffles.ui.components.DemoDataBanner
import uk.co.rodrunners.raffles.ui.components.FeaturedCompetitionCard
import uk.co.rodrunners.raffles.ui.components.PrizeImage
import uk.co.rodrunners.raffles.ui.components.QuietButton
import uk.co.rodrunners.raffles.ui.components.SectionHeader
import uk.co.rodrunners.raffles.ui.components.StateHost
import uk.co.rodrunners.raffles.ui.components.Wordmark
import uk.co.rodrunners.raffles.ui.theme.Dimens
import uk.co.rodrunners.raffles.ui.theme.RrrColors
import uk.co.rodrunners.raffles.ui.theme.camoHeader
import uk.co.rodrunners.raffles.ui.theme.RrrShapes
import uk.co.rodrunners.raffles.ui.theme.RrrType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onOpenCompetition: (String) -> Unit,
    onOpenSearch: () -> Unit,
    onOpenNotifications: () -> Unit,
    onOpenResults: () -> Unit,
    onOpenAllCompetitions: () -> Unit,
    onOpenTickets: () -> Unit,
    unreadCount: Int = 0,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
          // The masthead: camo band behind a large mark, closed by a bronze
          // rule. This is the only place the camo appears.
          Column(Modifier.fillMaxWidth().camoHeader()) {
            TopAppBar(
                modifier = Modifier.height(96.dp),
                // Centred rather than left-aligned: with search and
                // notifications on the right, a left-aligned mark looked like
                // an afterthought squeezed into the corner.
                title = {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Wordmark(compact = true, height = 74.dp)
                    }
                },
                actions = {
                    IconButton(onClick = onOpenSearch) {
                        Icon(Icons.Outlined.Search, stringResource(R.string.cd_search), tint = RrrColors.Bone)
                    }
                    Box {
                        IconButton(onClick = onOpenNotifications) {
                            Icon(Icons.Outlined.Notifications, stringResource(R.string.cd_notifications), tint = RrrColors.Bone)
                        }
                        if (unreadCount > 0) {
                            Box(
                                Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(top = 10.dp, end = 10.dp)
                                    .size(8.dp)
                                    .clip(RrrShapes.small)
                                    .background(RrrColors.Gold)
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            )
            Box(
                Modifier
                    .padding(horizontal = Dimens.gutter)
                    .fillMaxWidth()
                    .height(2.dp)
                    .background(
                        androidx.compose.ui.graphics.Brush.horizontalGradient(
                            0f to Color.Transparent,
                            0.18f to RrrColors.Gold,
                            0.82f to RrrColors.Gold,
                            1f to Color.Transparent,
                        )
                    )
            )
            Spacer(Modifier.height(4.dp))
          }
        },
    ) { padding ->
        StateHost(
            state = state,
            modifier = Modifier.padding(padding),
            onRetry = viewModel::load,
            onEmptyAction = onOpenResults,
        ) { home ->
            // The featured slot rotates through the live raffles rather than
            // showing one and stopping. Crossfade rather than a pager: the
            // pager APIs have moved around between Compose versions and this
            // needs nothing a fade cannot do.
            val slides = remember(home.featured, home.live) {
                (listOfNotNull(home.featured) +
                    home.live.filter { it.id != home.featured?.id }).take(5)
            }
            var slide by remember(slides.size) { mutableStateOf(0) }

            LaunchedEffect(slides.size) {
                if (slides.size > 1) {
                    while (true) {
                        delay(6000)
                        slide = (slide + 1) % slides.size
                    }
                }
            }

            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(Dimens.sectionGap),
            ) {
                if (home.showsDemoData) {
                    item { DemoDataBanner(Modifier.padding(horizontal = Dimens.gutter)) }
                }

                if (slides.isNotEmpty()) {
                    item {
                        Column {
                            Crossfade(
                                targetState = slides[slide % slides.size],
                                label = "featured",
                            ) { competition ->
                                FeaturedCompetitionCard(
                                    competition = competition,
                                    onClick = { onOpenCompetition(competition.id) },
                                    modifier = Modifier.padding(horizontal = Dimens.gutter),
                                )
                            }

                            if (slides.size > 1) {
                                Spacer(Modifier.height(12.dp))
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.Center,
                                ) {
                                    slides.indices.forEach { i ->
                                        val on = i == slide % slides.size
                                        Box(
                                            Modifier
                                                .padding(horizontal = 3.dp)
                                                .height(8.dp)
                                                .width(if (on) 22.dp else 8.dp)
                                                .clip(CircleShape)
                                                .background(
                                                    if (on) RrrColors.Khaki
                                                    else RrrColors.Slate
                                                )
                                                .clickable { slide = i }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                if (home.myActiveTickets.isNotEmpty()) {
                    item {
                        Column {
                            SectionHeader(
                                "Your live entries",
                                Modifier.padding(horizontal = Dimens.gutter),
                                action = { QuietButton("All tickets", onOpenTickets) },
                            )
                            Spacer(Modifier.height(12.dp))
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = Dimens.gutter),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                items(home.myActiveTickets, key = { it.competitionId }) { group ->
                                    ActiveEntryChipCard(group) { onOpenCompetition(group.competitionId) }
                                }
                            }
                        }
                    }
                }

                if (home.endingSoon.isNotEmpty()) {
                    item {
                        Column {
                            SectionHeader(
                                "Ending soon",
                                Modifier.padding(horizontal = Dimens.gutter),
                                action = { QuietButton("See all", onOpenAllCompetitions) },
                            )
                            Spacer(Modifier.height(12.dp))
                            Column(
                                Modifier.padding(horizontal = Dimens.gutter),
                                verticalArrangement = Arrangement.spacedBy(Dimens.cardGap),
                            ) {
                                home.endingSoon.take(3).forEach { c ->
                                    CompetitionRow(
                                        competition = c,
                                        onClick = { onOpenCompetition(c.id) },
                                        isFavourite = home.favouriteIds.contains(c.id),
                                        onToggleFavourite = {
                                            viewModel.toggleFavourite(c.id, !home.favouriteIds.contains(c.id))
                                        },
                                    )
                                }
                            }
                        }
                    }
                }

                if (home.banners.isNotEmpty()) {
                    item {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = Dimens.gutter),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            items(home.banners, key = { it.id }) { banner ->
                                PromoBanner(banner) {
                                    banner.deepLink?.substringAfterLast('/')?.let(onOpenCompetition)
                                }
                            }
                        }
                    }
                }

                if (home.newest.isNotEmpty()) {
                    item { HorizontalCompetitionRow("New this week", home.newest, onOpenCompetition) }
                }

                if (home.recommended.isNotEmpty()) {
                    item { HorizontalCompetitionRow("Because you entered", home.recommended, onOpenCompetition) }
                }

                if (home.latestWinners.isNotEmpty()) {
                    item {
                        Column {
                            SectionHeader(
                                "Latest winners",
                                Modifier.padding(horizontal = Dimens.gutter),
                                action = { QuietButton("All results", onOpenResults) },
                            )
                            Spacer(Modifier.height(12.dp))
                            Column(
                                Modifier.padding(horizontal = Dimens.gutter),
                                verticalArrangement = Arrangement.spacedBy(Dimens.cardGap),
                            ) {
                                home.latestWinners.take(4).forEach { WinnerRow(it) }
                            }
                        }
                    }
                }

                if (home.live.isNotEmpty()) {
                    item {
                        Column {
                            SectionHeader("Live now", Modifier.padding(horizontal = Dimens.gutter))
                            Spacer(Modifier.height(12.dp))
                            Column(
                                Modifier.padding(horizontal = Dimens.gutter),
                                verticalArrangement = Arrangement.spacedBy(Dimens.cardGap),
                            ) {
                                home.live.take(6).forEach { c ->
                                    CompetitionRow(
                                        competition = c,
                                        onClick = { onOpenCompetition(c.id) },
                                        isFavourite = home.favouriteIds.contains(c.id),
                                        onToggleFavourite = {
                                            viewModel.toggleFavourite(c.id, !home.favouriteIds.contains(c.id))
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HorizontalCompetitionRow(
    title: String,
    items: List<uk.co.rodrunners.raffles.data.model.Competition>,
    onOpen: (String) -> Unit,
) {
    Column {
        SectionHeader(title, Modifier.padding(horizontal = Dimens.gutter))
        Spacer(Modifier.height(12.dp))
        LazyRow(
            contentPadding = PaddingValues(horizontal = Dimens.gutter),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(items, key = { it.id }) { c ->
                CompetitionTile(c, onClick = { onOpen(c.id) })
            }
        }
    }
}

@Composable
private fun ActiveEntryChipCard(group: TicketGroup, onClick: () -> Unit) {
    Column(
        Modifier
            .width(190.dp)
            .clip(RrrShapes.large)
            .background(RrrColors.Surface)
            .clickable(onClick = onClick)
            .padding(12.dp)
    ) {
        Text(
            group.competitionTitle,
            style = MaterialTheme.typography.labelMedium,
            color = RrrColors.Bone,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "${group.entryNumbers.size} ${if (group.entryNumbers.size == 1) "entry" else "entries"}",
            style = RrrType.NumericSmall,
            color = RrrColors.Gold,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            "Numbers ${group.entryNumbers.take(3).joinToString(", ")}" +
                if (group.entryNumbers.size > 3) "…" else "",
            style = MaterialTheme.typography.labelSmall,
            color = RrrColors.Mist,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun PromoBanner(banner: Banner, onClick: () -> Unit) {
    Box(
        Modifier
            .width(300.dp)
            .height(120.dp)
            .clip(RrrShapes.large)
            .background(RrrColors.Surface)
            .clickable(onClick = onClick)
    ) {
        PrizeImage(banner.imageUrl, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        Box(Modifier.fillMaxSize().background(RrrColors.CardScrim))
        Column(Modifier.align(Alignment.CenterStart).padding(16.dp)) {
            Text(banner.title, style = MaterialTheme.typography.titleMedium, color = RrrColors.Bone)
            Spacer(Modifier.height(4.dp))
            Text(banner.subtitle, style = MaterialTheme.typography.bodySmall, color = RrrColors.Mist)
        }
    }
}

@Composable
private fun WinnerRow(winner: Winner) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RrrShapes.medium)
            .background(RrrColors.Surface)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PrizeImage(
            winner.prizeImageUrl, null,
            Modifier.size(52.dp).clip(RrrShapes.small),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                winner.competitionTitle,
                style = MaterialTheme.typography.labelLarge,
                color = RrrColors.Bone,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "Won by ${winner.winnerDisplayName} — entry ${winner.winningEntryNumber}",
                style = MaterialTheme.typography.labelSmall,
                color = RrrColors.Mist,
            )
        }
        Text(
            TimeFormat.short(winner.drawnAtMillis),
            style = MaterialTheme.typography.labelSmall,
            color = RrrColors.Slate,
        )
    }
}
