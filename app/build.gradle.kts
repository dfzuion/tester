import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.google.services)
    alias(libs.plugins.crashlytics)
}

// Secrets are read from local.properties (never committed) or from CI environment variables.
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
fun secret(key: String, fallback: String = ""): String =
    (localProps.getProperty(key) ?: System.getenv(key) ?: fallback)

// A release build is only signable when all four values are present. If they
// aren't, the release build type is left unsigned and the check below stops the
// build with a readable message rather than producing an artifact Play rejects.
val releaseKeystorePath = secret("RRR_KEYSTORE_PATH")
val releaseKeystorePassword = secret("RRR_KEYSTORE_PASSWORD")
val releaseKeyAlias = secret("RRR_KEY_ALIAS")
val releaseKeyPassword = secret("RRR_KEY_PASSWORD")
val hasReleaseSigning =
    listOf(releaseKeystorePath, releaseKeystorePassword, releaseKeyAlias, releaseKeyPassword)
        .all { it.isNotBlank() } && rootProject.file(releaseKeystorePath).exists()

android {
    namespace = "uk.co.rodrunners.raffles"
    compileSdk = 35

    defaultConfig {
        applicationId = "uk.co.rodrunners.raffles"
        minSdk = 26
        targetSdk = 35
        // CI overrides the code per upload; Play requires it to increase every time.
        versionCode = secret("RRR_VERSION_CODE", "1").toInt()
        versionName = secret("RRR_VERSION_NAME", "1.0.0")
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true
    }

    signingConfigs {
        // A fixed debug key. Android Studio's default keystore differs on every
        // machine and CI generates a fresh one per build, so the SHA-1 would
        // change constantly - and Google sign-in only works for fingerprints
        // registered in Firebase. Checking one in keeps every debug build
        // identical to Firebase's eyes. It is a debug key: it signs nothing
        // that reaches Play.
        getByName("debug") {
            val shared = rootProject.file("keystore/debug.keystore")
            if (shared.exists()) {
                storeFile = shared
                storePassword = "android"
                keyAlias = "androiddebugkey"
                keyPassword = "android"
            }
        }

        if (hasReleaseSigning) {
            create("release") {
                storeFile = rootProject.file(releaseKeystorePath)
                storePassword = releaseKeystorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
                enableV1Signing = false
                enableV2Signing = true
                enableV3Signing = true
                enableV4Signing = false
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            isDebuggable = true
            buildConfigField("String", "STRIPE_PUBLISHABLE_KEY", "\"${secret("STRIPE_PUBLISHABLE_KEY_TEST", "pk_test_REPLACE_ME")}\"")
            buildConfigField("boolean", "USE_FIREBASE_EMULATORS", secret("USE_FIREBASE_EMULATORS", "false"))
            buildConfigField("boolean", "ALLOW_DEMO_SEED", "true")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (hasReleaseSigning) signingConfig = signingConfigs.getByName("release")
            buildConfigField("String", "STRIPE_PUBLISHABLE_KEY", "\"${secret("STRIPE_PUBLISHABLE_KEY_LIVE", "pk_live_REPLACE_ME")}\"")
            buildConfigField("boolean", "USE_FIREBASE_EMULATORS", "false")
            buildConfigField("boolean", "ALLOW_DEMO_SEED", "false")
        }
    }

    flavorDimensions += "environment"
    productFlavors {
        create("staging") {
            dimension = "environment"
            applicationIdSuffix = ".staging"
            versionNameSuffix = "-staging"
            resValue("string", "app_name", "Rod Runners Raffles")
            // Staging never touches live money, whichever build type it is paired with.
            buildConfigField(
                "String",
                "STRIPE_PUBLISHABLE_KEY",
                "\"${secret("STRIPE_PUBLISHABLE_KEY_TEST", "pk_test_REPLACE_ME")}\"",
            )
            buildConfigField("boolean", "ALLOW_DEMO_SEED", "true")
        }
        create("production") {
            dimension = "environment"
            resValue("string", "app_name", "Rod Runners Raffles")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = false
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources.excludes += setOf("/META-INF/{AL2.0,LGPL2.1}")
    }

    bundle {
        // Play delivers only the density, language and ABI slices each device
        // needs. Leave these on unless something breaks.
        density { enableSplit = true }
        language { enableSplit = true }
        abi { enableSplit = true }
    }

    // The SDK dependency block Play shows in the console is signed metadata we
    // don't need; keeping it out avoids a needless third-party disclosure.
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }
}

// Fail early and clearly instead of half an hour into a release build.
gradle.taskGraph.whenReady {
    val buildingRelease = allTasks.any {
        it.project == project && it.name.contains("Release") &&
            (it.name.startsWith("bundle") || it.name.startsWith("assemble"))
    }
    if (buildingRelease && !hasReleaseSigning) {
        throw GradleException(
            """
            Release signing is not configured, so this build would produce an unsigned artifact.
            Set these in local.properties (or as CI environment variables):

              RRR_KEYSTORE_PATH      path to the .jks, relative to the project root
              RRR_KEYSTORE_PASSWORD
              RRR_KEY_ALIAS
              RRR_KEY_PASSWORD

            See SETUP.md, "Building a signed release for Google Play".
            """.trimIndent()
        )
    }
    // Only the production flavour ships the live key; staging release builds are
    // for testing and stay on the test key.
    val buildingProductionRelease = allTasks.any {
        it.project == project && it.name.contains("ProductionRelease") &&
            (it.name.startsWith("bundle") || it.name.startsWith("assemble"))
    }
    if (buildingProductionRelease && secret("STRIPE_PUBLISHABLE_KEY_LIVE").isBlank()) {
        throw GradleException(
            "STRIPE_PUBLISHABLE_KEY_LIVE is not set. A production build must not ship the placeholder key."
        )
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.splashscreen)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.datastore.preferences)

    implementation(libs.hilt.android)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services)
    implementation(libs.googleid)
    ksp(libs.hilt.compiler)

    implementation(libs.coil.compose)
    implementation(libs.accompanist.permissions)

    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.functions)
    implementation(libs.firebase.storage)
    implementation(libs.firebase.messaging)
    implementation(libs.firebase.analytics)
    implementation(libs.firebase.crashlytics)
    implementation(libs.firebase.appcheck)
    debugImplementation(libs.firebase.appcheck.debug)

    implementation(libs.stripe.android)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.kotlinx.serialization.json)

    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)

    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.turbine)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
}
