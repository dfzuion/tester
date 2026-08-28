package uk.co.rodrunners.raffles.ui.screens.admin

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.util.Calendar
import uk.co.rodrunners.raffles.core.Money
import uk.co.rodrunners.raffles.data.model.Competition
import uk.co.rodrunners.raffles.ui.components.Chip
import uk.co.rodrunners.raffles.ui.components.PrizeImage
import uk.co.rodrunners.raffles.ui.components.GoldButton
import uk.co.rodrunners.raffles.ui.components.QuietButton
import uk.co.rodrunners.raffles.ui.components.SectionHeader
import uk.co.rodrunners.raffles.ui.screens.auth.RrrTextField
import uk.co.rodrunners.raffles.ui.theme.Dimens
import uk.co.rodrunners.raffles.ui.theme.RrrColors

/** Pence entered as pounds, so the admin types 2.50 rather than 250. */
private fun poundsToPence(text: String): Int {
    val cleaned = text.replace("£", "").replace(",", "").trim()
    val value = cleaned.toDoubleOrNull() ?: return 0
    return Math.round(value * 100).toInt()
}

private fun penceToPounds(pence: Int): String =
    if (pence == 0) "" else String.format("%.2f", pence / 100.0)

private fun daysFromNow(days: Int): Long = Calendar.getInstance().apply {
    add(Calendar.DAY_OF_YEAR, days)
    set(Calendar.HOUR_OF_DAY, 20); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0)
}.timeInMillis

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompetitionEditorScreen(
    competitionId: String?,
    onBack: () -> Unit,
    onSaved: (String) -> Unit,
    viewModel: CompetitionEditorViewModel = hiltViewModel(),
) {
    LaunchedEffect(competitionId) { viewModel.start(competitionId) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val d = state.draft

    LaunchedEffect(state.savedId) {
        state.savedId?.let { onSaved(it); viewModel.consumeSaved() }
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (competitionId == null) "New raffle" else "Edit raffle",
                        style = MaterialTheme.typography.titleLarge,
                    )
                },
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
            contentPadding = PaddingValues(
                start = Dimens.gutter, end = Dimens.gutter, top = 8.dp, bottom = 40.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { SectionHeader("The prize") }
            item {
                RrrTextField(d.title, { v -> viewModel.edit { it.copy(title = v) } }, "Raffle title")
            }
            item {
                RrrTextField(d.prizeName, { v -> viewModel.edit { it.copy(prizeName = v) } }, "Prize name")
            }
            item {
                RrrTextField(d.brand, { v -> viewModel.edit { it.copy(brand = v) } }, "Brand (optional)")
            }
            item {
                HeroImagePicker(
                    url = d.heroImageUrl,
                    uploading = state.uploadingImage,
                    onPicked = { uri -> viewModel.uploadHeroImage(uri) },
                    onClear = { viewModel.edit { it.copy(heroImageUrl = "") } },
                )
            }
            item {
                RrrTextField(
                    d.description, { v -> viewModel.edit { it.copy(description = v) } },
                    "Description", singleLine = false, minLines = 3,
                )
            }
            item {
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Competition.CATEGORIES.forEach { (key, label) ->
                        Chip(label, d.category == key, onClick = { viewModel.edit { it.copy(category = key) } })
                    }
                }
            }
            item {
                RrrTextField(
                    penceToPounds(d.retailValuePence),
                    { v -> viewModel.edit { it.copy(retailValuePence = poundsToPence(v)) } },
                    "Retail value (£)", keyboardType = KeyboardType.Decimal,
                )
            }

            item { Spacer(Modifier.height(8.dp)); SectionHeader("Entries and price") }
            if (state.structuralLocked) {
                item {
                    Text(
                        "${state.entriesSold} entries have been sold, so the price, entry count and bundles are locked. " +
                            "Customers bought into these terms.",
                        style = MaterialTheme.typography.bodySmall,
                        color = RrrColors.Warning,
                    )
                }
            }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    RrrTextField(
                        penceToPounds(d.entryPricePence),
                        { v -> viewModel.edit { it.copy(entryPricePence = poundsToPence(v)) } },
                        "Entry price (£)", modifier = Modifier.weight(1f),
                        keyboardType = KeyboardType.Decimal,
                    )
                    RrrTextField(
                        if (d.maxEntries == 0) "" else d.maxEntries.toString(),
                        { v -> viewModel.edit { it.copy(maxEntries = v.filter { c -> c.isDigit() }.toIntOrNull() ?: 0) } },
                        "Total entries", modifier = Modifier.weight(1f),
                        keyboardType = KeyboardType.Number,
                    )
                }
            }
            item {
                Text(
                    "Sell-out take: ${Money.format(d.potentialRevenuePence)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = RrrColors.Mist,
                )
            }
            item {
                RrrTextField(
                    if (d.maxEntriesPerCustomer == 0) "" else d.maxEntriesPerCustomer.toString(),
                    { v -> viewModel.edit { it.copy(maxEntriesPerCustomer = v.filter { c -> c.isDigit() }.toIntOrNull() ?: 0) } },
                    "Max entries per customer", keyboardType = KeyboardType.Number,
                    supportingText = "Leave blank for the default of 100 per person.",
                )
            }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Chip("Random numbers", d.allocationMode == "random",
                        onClick = { viewModel.edit { it.copy(allocationMode = "random") } })
                    Chip("In order", d.allocationMode == "sequential",
                        onClick = { viewModel.edit { it.copy(allocationMode = "sequential") } })
                }
            }

            item { Spacer(Modifier.height(8.dp)); SectionHeader("Bundles") }
            itemsIndexedBundles(d, viewModel)
            item {
                QuietButton("Add a bundle", onClick = { viewModel.addBundle() })
            }

            item { Spacer(Modifier.height(8.dp)); SectionHeader("Closing") }
            item {
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(3 to "3 days", 7 to "1 week", 14 to "2 weeks", 30 to "1 month").forEach { (days, label) ->
                        val ms = daysFromNow(days)
                        Chip(label, kotlin.math.abs(d.closesAtMillis - ms) < 60_000,
                            onClick = { viewModel.edit { it.copy(closesAtMillis = ms) } })
                    }
                }
            }
            item {
                Text(
                    if (d.closesValid) "Closes ${java.text.SimpleDateFormat("EEE d MMM 'at' HH:mm", java.util.Locale.UK).format(java.util.Date(d.closesAtMillis))}"
                    else "Pick when entries close.",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (d.closesValid) RrrColors.Mist else RrrColors.Warning,
                )
            }
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Feature on the home screen", style = MaterialTheme.typography.bodyMedium,
                        color = RrrColors.Bone, modifier = Modifier.weight(1f))
                    Switch(
                        checked = d.featured,
                        onCheckedChange = { v -> viewModel.edit { it.copy(featured = v) } },
                        colors = SwitchDefaults.colors(checkedTrackColor = RrrColors.Gold),
                    )
                }
            }

            state.error?.let { err ->
                item {
                    Text(err.message, style = MaterialTheme.typography.bodySmall, color = RrrColors.Danger)
                }
            }

            item {
                Spacer(Modifier.height(16.dp))
                GoldButton(
                    text = if (competitionId == null) "Create draft raffle" else "Save changes",
                    onClick = { viewModel.save() },
                    enabled = d.isComplete && !state.saving,
                    loading = state.saving,
                    modifier = Modifier.fillMaxWidth().navigationBarsPadding(),
                )
            }
            item {
                Text(
                    "New raffles are saved as a draft. Publish it from the admin list when you're ready.",
                    style = MaterialTheme.typography.bodySmall,
                    color = RrrColors.Slate,
                )
            }
        }
    }
}

/** Bundle rows, kept out of the main list body for readability. */
private fun androidx.compose.foundation.lazy.LazyListScope.itemsIndexedBundles(
    draft: uk.co.rodrunners.raffles.data.model.CompetitionDraft,
    viewModel: CompetitionEditorViewModel,
) {
    itemsIndexed(draft.bundles) { index, bundle ->
        Column {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                RrrTextField(
                    if (bundle.quantity == 0) "" else bundle.quantity.toString(),
                    { v -> viewModel.editBundle(index) { b -> b.copy(quantity = v.filter { c -> c.isDigit() }.toIntOrNull() ?: 0) } },
                    "Entries", modifier = Modifier.weight(1f), keyboardType = KeyboardType.Number,
                )
                RrrTextField(
                    penceToPounds(bundle.pricePence),
                    { v -> viewModel.editBundle(index) { b -> b.copy(pricePence = poundsToPence(v)) } },
                    "Price (£)", modifier = Modifier.weight(1f), keyboardType = KeyboardType.Decimal,
                )
            }
            QuietButton("Remove bundle", onClick = { viewModel.removeBundle(index) })
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.itemsIndexed(
    bundles: List<uk.co.rodrunners.raffles.data.model.Bundle>,
    content: @Composable (Int, uk.co.rodrunners.raffles.data.model.Bundle) -> Unit,
) {
    bundles.forEachIndexed { index, bundle ->
        item(key = "bundle_$index") { content(index, bundle) }
    }
}

/**
 * Pick a photo from the phone and upload it. The old screen asked for a URL,
 * which meant finding somewhere to host the picture first - fine for a
 * developer, useless for anyone actually running raffles.
 */
@Composable
private fun HeroImagePicker(
    url: String,
    uploading: Boolean,
    onPicked: (android.net.Uri) -> Unit,
    onClear: () -> Unit,
) {
    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri -> if (uri != null) onPicked(uri) }

    fun open() = picker.launch(
        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
    )

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "Prize photo",
            style = MaterialTheme.typography.labelLarge,
            color = RrrColors.Mist,
        )
        Box(
            Modifier
                .fillMaxWidth()
                .height(200.dp)
                .clip(RoundedCornerShape(Dimens.cardRadius))
                .background(RrrColors.SurfaceRaised)
                .border(1.dp, RrrColors.Hairline, RoundedCornerShape(Dimens.cardRadius))
                .clickable(enabled = !uploading) { open() },
            contentAlignment = Alignment.Center,
        ) {
            when {
                uploading -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = RrrColors.Khaki)
                    Spacer(Modifier.height(12.dp))
                    Text("Uploading photo...", color = RrrColors.Mist,
                        style = MaterialTheme.typography.bodySmall)
                }
                url.isNotBlank() -> PrizeImage(
                    url = url,
                    contentDescription = "Prize photo",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
                else -> Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text("Tap to choose a photo",
                        style = MaterialTheme.typography.titleSmall, color = RrrColors.Bone)
                    Text("Straight from your phone. PNG or JPG, up to 8MB.",
                        style = MaterialTheme.typography.bodySmall, color = RrrColors.Slate,
                        textAlign = TextAlign.Center)
                }
            }
        }
        if (url.isNotBlank() && !uploading) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                QuietButton("Change photo", onClick = { open() })
                QuietButton("Remove", onClick = onClear)
            }
        }
    }
}
