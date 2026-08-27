package uk.co.rodrunners.raffles.di

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.MemoryCacheSettings
import com.google.firebase.firestore.PersistentCacheSettings
import com.google.firebase.firestore.firestoreSettings
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.ktx.functions
import com.google.firebase.ktx.Firebase
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.ktx.messaging
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.ktx.storage
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import uk.co.rodrunners.raffles.BuildConfig
import uk.co.rodrunners.raffles.core.Functions

@Module
@InstallIn(SingletonComponent::class)
object FirebaseModule {

    @Provides @Singleton
    fun provideAuth(): FirebaseAuth = Firebase.auth.apply {
        if (BuildConfig.USE_FIREBASE_EMULATORS) useEmulator("10.0.2.2", 9099)
    }

    @Provides @Singleton
    fun provideFirestore(): FirebaseFirestore = Firebase.firestore.apply {
        if (BuildConfig.USE_FIREBASE_EMULATORS) useEmulator("10.0.2.2", 8080)
        // Offline persistence keeps browsing usable on a riverbank with one bar.
        firestoreSettings = firestoreSettings {
            setLocalCacheSettings(
                if (BuildConfig.USE_FIREBASE_EMULATORS) MemoryCacheSettings.newBuilder().build()
                else PersistentCacheSettings.newBuilder().setSizeBytes(48L * 1024 * 1024).build()
            )
        }
    }

    @Provides @Singleton
    fun provideFunctions(): FirebaseFunctions = Firebase.functions(Functions.REGION).apply {
        if (BuildConfig.USE_FIREBASE_EMULATORS) useEmulator("10.0.2.2", 5001)
    }

    @Provides @Singleton
    fun provideStorage(): FirebaseStorage = Firebase.storage

    @Provides @Singleton
    fun provideMessaging(): FirebaseMessaging = Firebase.messaging
}
