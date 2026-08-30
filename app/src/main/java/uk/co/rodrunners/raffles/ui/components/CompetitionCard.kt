package uk.co.rodrunners.raffles.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import uk.co.rodrunners.raffles.R
import uk.co.rodrunners.raffles.core.Money
import uk.co.rodrunners.raffles.data.model.Competition
import uk.co.rodrunners.raffles.ui.theme.Dimens
import uk.co.rodrunners.raffles.ui.theme.RrrColors
import uk.co.rodrunners.raffles.ui.theme.RrrShapes
import uk.co.rodrunners.raffles.ui.theme.RrrType

/**
 * The list row from the catalogue: photograph left, the four facts that matter
 * right; what it is, what an entry costs, how many are left, when it closes.
 * Everything else is deliberately absent at this size.
 */
@Composable
fun CompetitionRow(
    competition: Competition,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isFavourite: Boolean = false,
    onToggleFavourite: (() -> Unit)? = null,
) {
    val remainingLabel = if (competition.isSoldOut) "Sold out" else "${competition.entriesRemaining} left"
    Row(
        modifier
            .fillMaxWidth()
            .clip(RrrShapes.large)
            .background(RrrColors.Surface)
            .border(BorderStroke(1.dp, RrrColors.Hairline), RrrShapes.large)
            .clickable(onClick = onClick)
            .clearAndSetSemantics {
                contentDescription = "${competition.title}. " +
                    "Entry ${Money.formatCompact(competition.entryPricePence)}. $remainingLabel."
            }
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(96.dp)
                .clip(RrrShapes.medium)
                .background(RrrColors.SurfaceRaised)
        ) {
            PrizeImage(
                url = competition.heroImageUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
            )
            if (competition.isSoldOut) {
                Box(Modifier.fillMaxSize().background(RrrColors.Ink.copy(alpha = 0.55f)))
                StatusPill("sold_out", Modifier.align(Alignment.Center))
            }
        }

        Spacer(Modifier.width(14.dp))

        Column(Modifier.weight(1f)) {
            Text(
                competition.title,
                style = MaterialTheme.typography.titleMedium,
                color = RrrColors.Bone,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    Money.formatCompact(competition.entryPricePence),
                    style = RrrType.NumericSmall,
                    color = RrrColors.Gold,
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    "per entry",
                    style = MaterialTheme.typography.labelSmall,
                    color = RrrColors.Mist,
                )
            }
            Spacer(Modifier.height(10.dp))
            EntriesProgress(competition.entriesSold, competition.maxEntries, showLabels = false)
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    remainingLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (competition.isSoldOut) RrrColors.Warning else RrrColors.Mist,
                )
                CountdownText(competition.closesAtMillis, style = MaterialTheme.typography.labelSmall)
            }
        }

        if (onToggleFavourite != null) {
            IconButton(onClick = onToggleFavourite, modifier = Modifier.size(Dimens.minTouchTarget)) {
                Icon(
                    imageVector = if (isFavourite) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                    contentDescription = stringResource(
                        if (isFavourite) R.string.cd_favourite_remove else R.string.cd_favourite_add
                    ),
                    tint = if (isFavourite) RrrColors.Gold else RrrColors.Slate,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

/**
 * The featured card at the top of Home: full-bleed photography with the type
 * sitting on a bottom scrim. This is the one place the app spends real screen
 * area on a photograph.
 */
@Composable
fun FeaturedCompetitionCard(
    competition: Competition,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .fillMaxWidth()
            .aspectRatio(1.35f)
            .clip(RrrShapes.large)
            .background(RrrColors.Surface)
            .border(BorderStroke(1.dp, RrrColors.Hairline), RrrShapes.large)
            .clickable(onClick = onClick)
    ) {
        PrizeImage(
            url = competition.heroImageUrl,
            contentDescription = stringResource(R.string.cd_prize_image),
            modifier = Modifier.fillMaxSize(),
        )
        Box(Modifier.fillMaxSize().background(RrrColors.ScrimBottom))

        // Two lit tags: the LIVE lamp, and how long is left. Inside three days
        // the countdown flashes, which is the only thing on a card allowed to
        // move on its own.
        Row(
            Modifier.align(Alignment.TopStart).padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (competition.isSoldOut) {
                StatusPill("sold_out")
            } else {
                LivePill()
                Spacer(Modifier.width(8.dp))
                CountdownPill(competition.closesAtMillis)
            }

            if (competition.isDemo) {
                Spacer(Modifier.width(8.dp))
                StatusPill("demo")
            }
        }

        Column(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                "THIS WEEK'S HEADLINE DRAW",
                style = RrrType.Eyebrow,
                color = RrrColors.Gold,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                competition.title,
                style = MaterialTheme.typography.headlineSmall,
                color = RrrColors.Bone,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Worth ${Money.format(competition.retailValuePence)}",
                style = MaterialTheme.typography.bodySmall,
                color = RrrColors.Mist,
            )
            Spacer(Modifier.height(12.dp))
            EntriesProgress(competition.entriesSold, competition.maxEntries)
            Spacer(Modifier.height(12.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        Money.formatCompact(competition.entryPricePence),
                        style = RrrType.Numeric,
                        color = RrrColors.Gold,
                        fontWeight = FontWeight.Bold,
                    )
                    Text("per entry", style = MaterialTheme.typography.labelSmall, color = RrrColors.Mist)
                }
                CountdownText(
                    competition.closesAtMillis,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}

/** Compact card for horizontal rows ("New this week", "Recommended"). */
@Composable
fun CompetitionTile(
    competition: Competition,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .width(168.dp)
            .clip(RrrShapes.large)
            .background(RrrColors.Surface)
            .border(BorderStroke(1.dp, RrrColors.Hairline), RrrShapes.large)
            .clickable(onClick = onClick)
            .clearAndSetSemantics {
                contentDescription = "${competition.title}. Entry ${Money.formatCompact(competition.entryPricePence)}."
            }
    ) {
        PrizeImage(
            url = competition.heroImageUrl,
            contentDescription = null,
            modifier = Modifier.fillMaxWidth().height(104.dp),
        )
        Column(Modifier.padding(10.dp)) {
            Text(
                competition.title,
                style = MaterialTheme.typography.labelMedium,
                color = RrrColors.Bone,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.height(34.dp),
            )
            Spacer(Modifier.height(8.dp))
            EntriesProgress(competition.entriesSold, competition.maxEntries, showLabels = false)
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    Money.formatCompact(competition.entryPricePence),
                    style = MaterialTheme.typography.labelMedium,
                    color = RrrColors.Gold,
                )
                CountdownText(
                    competition.closesAtMillis,
                    showIcon = false,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
}
