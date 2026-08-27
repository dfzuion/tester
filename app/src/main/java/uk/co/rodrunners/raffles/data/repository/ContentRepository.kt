package uk.co.rodrunners.raffles.data.repository

import com.google.firebase.firestore.FirebaseFirestore
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.tasks.await
import uk.co.rodrunners.raffles.core.Collections
import uk.co.rodrunners.raffles.data.model.BannerSet
import uk.co.rodrunners.raffles.data.model.CompanyInfo
import uk.co.rodrunners.raffles.data.model.FaqItem
import uk.co.rodrunners.raffles.data.model.LegalDocument

/**
 * Banners, FAQs, terms, privacy and company details are all editable content.
 * Nothing in this app hard-codes a legal claim or a marketing message.
 */
@Singleton
class ContentRepository @Inject constructor(private val db: FirebaseFirestore) {

    suspend fun banners(): BannerSet =
        db.collection(Collections.APP_CONTENT).document("home_banners").get().await()
            .toObject(BannerSet::class.java) ?: BannerSet()

    suspend fun faqs(): List<FaqItem> =
        db.collection(Collections.FAQ).whereEqualTo("published", true)
            .orderBy("order").get().await().toObjects(FaqItem::class.java)

    suspend fun legal(docId: String): LegalDocument? =
        db.collection(Collections.APP_CONTENT).document(docId).get().await()
            .toObject(LegalDocument::class.java)

    suspend fun company(): CompanyInfo =
        db.collection(Collections.APP_CONTENT).document("company").get().await()
            .toObject(CompanyInfo::class.java) ?: CompanyInfo()
}
