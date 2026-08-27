package uk.co.rodrunners.raffles.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.PropertyName

@IgnoreExtraProperties
data class Competition(
    @DocumentId val id: String = "",
    val title: String = "",
    val prizeName: String = "",
    val brand: String = "",
    val category: String = "",
    val description: String = "",
    val heroImageUrl: String = "",
    val galleryImageUrls: List<String> = emptyList(),
    val retailValuePence: Int = 0,
    val entryPricePence: Int = 0,
    val bookingFeePence: Int = 0,
    val bundles: List<Bundle> = emptyList(),
    val maxEntries: Int = 0,
    val entriesSold: Int = 0,
    val maxEntriesPerCustomer: Int = 0,
    val allocationMode: String = "sequential",
    val status: String = "draft",
    val featured: Boolean = false,
    @get:PropertyName("isDemo") @set:PropertyName("isDemo")
    var isDemo: Boolean = false,
    val rulesId: String? = null,
    val winnerMechanism: String = "random_eligible_entry",
    val winnerNameDisplay: String = "first_name_last_initial",
    val minimumAge: Int = 18,
    val geoRestriction: String = "GB",
    val opensAt: Timestamp? = null,
    val closesAt: Timestamp? = null,
    val drawnAt: Timestamp? = null,
    val createdAt: Timestamp? = null,
    val winningEntryNumber: Int? = null,
    val resultPublished: Boolean = false,
    val previousWinnerDisplayName: String? = null,
) {
    val entriesRemaining: Int get() = (maxEntries - entriesSold).coerceAtLeast(0)
    val soldFraction: Float get() = if (maxEntries == 0) 0f else (entriesSold.toFloat() / maxEntries).coerceIn(0f, 1f)
    val closesAtMillis: Long get() = closesAt?.toDate()?.time ?: 0L
    val isLive: Boolean get() = status == "live"
    val isSoldOut: Boolean get() = entriesRemaining == 0
    val canEnter: Boolean get() = isLive && !isSoldOut && closesAtMillis > System.currentTimeMillis()

    fun millisRemaining(now: Long = System.currentTimeMillis()): Long = (closesAtMillis - now).coerceAtLeast(0)

    companion object {
        val CATEGORIES = listOf(
            "rods" to "Rods",
            "reels" to "Reels",
            "alarms" to "Alarms",
            "bivvies" to "Bivvies & shelters",
            "bedchairs" to "Bedchairs",
            "luggage" to "Luggage",
            "accessories" to "Accessories",
            "bundles" to "Bundles",
        )
    }
}

data class Bundle(
    val quantity: Int = 0,
    val pricePence: Int = 0,
    val label: String? = null,
)

enum class CompetitionSort(val label: String) {
    ENDING_SOON("Ending soon"),
    NEWEST("Newest"),
    PRICE_LOW("Price: low to high"),
    POPULARITY("Most entered"),
}

enum class CompetitionTab(val label: String) {
    ALL("All"),
    LIVE("Live now"),
    ENDING_SOON("Ending soon"),
    NEW("New"),
    COMPLETED("Completed"),
}
