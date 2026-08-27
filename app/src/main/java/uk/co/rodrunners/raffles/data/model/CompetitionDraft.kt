package uk.co.rodrunners.raffles.data.model

/**
 * What the admin form collects before it becomes a raffle. Money is held in
 * pence and times in millis so nothing is lost to formatting on the way to the
 * server, which validates all of it again.
 */
data class CompetitionDraft(
    val title: String = "",
    val prizeName: String = "",
    val brand: String = "",
    val category: String = "rods",
    val description: String = "",
    val heroImageUrl: String = "",
    val retailValuePence: Int = 0,
    val entryPricePence: Int = 0,
    val bookingFeePence: Int = 0,
    val maxEntries: Int = 0,
    val maxEntriesPerCustomer: Int = 0,
    val allocationMode: String = "random",
    val closesAtMillis: Long = 0L,
    val featured: Boolean = false,
    val bundles: List<Bundle> = emptyList(),
) {
    val titleValid: Boolean get() = title.trim().length >= 3
    val prizeValid: Boolean get() = prizeName.trim().length >= 2
    val imageValid: Boolean get() = heroImageUrl.trim().startsWith("http")
    val priceValid: Boolean get() = entryPricePence in 1..100_000
    val entriesValid: Boolean get() = maxEntries in 2..1_000_000
    val closesValid: Boolean get() = closesAtMillis > System.currentTimeMillis()

    val isComplete: Boolean
        get() = titleValid && prizeValid && imageValid && priceValid && entriesValid && closesValid

    /** Best-case take if every entry sells - shown so pricing mistakes are obvious. */
    val potentialRevenuePence: Long get() = entryPricePence.toLong() * maxEntries

    fun toPayload(): Map<String, Any?> = mapOf(
        "title" to title.trim(),
        "prizeName" to prizeName.trim(),
        "brand" to brand.trim(),
        "category" to category,
        "description" to description.trim(),
        "heroImageUrl" to heroImageUrl.trim(),
        "retailValuePence" to retailValuePence,
        "entryPricePence" to entryPricePence,
        "bookingFeePence" to bookingFeePence,
        "maxEntries" to maxEntries,
        "maxEntriesPerCustomer" to maxEntriesPerCustomer,
        "allocationMode" to allocationMode,
        "closesAtMillis" to closesAtMillis,
        "featured" to featured,
        "bundles" to bundles.map {
            mapOf("quantity" to it.quantity, "pricePence" to it.pricePence, "label" to it.label)
        },
    )

    companion object {
        fun from(c: Competition) = CompetitionDraft(
            title = c.title,
            prizeName = c.prizeName,
            brand = c.brand,
            category = c.category.ifBlank { "rods" },
            description = c.description,
            heroImageUrl = c.heroImageUrl,
            retailValuePence = c.retailValuePence,
            entryPricePence = c.entryPricePence,
            bookingFeePence = c.bookingFeePence,
            maxEntries = c.maxEntries,
            maxEntriesPerCustomer = c.maxEntriesPerCustomer,
            allocationMode = c.allocationMode,
            closesAtMillis = c.closesAtMillis,
            featured = c.featured,
            bundles = c.bundles,
        )
    }
}
