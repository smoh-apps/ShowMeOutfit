plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

// Stable alpha signing (SMO-S3-06).
//
// `assembleDebug` on a hosted runner would otherwise generate a brand-new debug keystore on every
// run, so each published alpha would carry a different certificate and Android would refuse to
// install it over the previous one (INSTALL_FAILED_UPDATE_INCOMPATIBLE).
//
// When the four variables below are present the distributed debug APK is signed with the project's
// alpha key instead; a normal local build without them keeps using the default debug keystore.
// `ALPHA_SIGNING_REQUIRED=true` (set by the Pages workflow) turns a missing keystore into a build
// failure so a rotating key can never be published silently.
val alphaKeystorePath = providers.environmentVariable("ALPHA_KEYSTORE_PATH").orNull
val alphaKeystore = alphaKeystorePath?.let { file(it) }
val alphaSigningAvailable = alphaKeystore?.exists() == true
val alphaSigningRequired = providers.environmentVariable("ALPHA_SIGNING_REQUIRED").orNull == "true"

if (alphaSigningRequired && !alphaSigningAvailable) {
    throw GradleException(
        "ALPHA_SIGNING_REQUIRED is set but no alpha keystore was found at " +
            "'${alphaKeystorePath ?: "<ALPHA_KEYSTORE_PATH unset>"}'. Publishing an unsigned/" +
            "debug-keyed APK would break in-place updates; see docs/distribution/alpha-signing.md."
    )
}

android {
    namespace = "com.smoh.showmeoutfit"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.smoh.showmeoutfit"
        minSdk = 26
        targetSdk = 37
        // Monotonic per published build: the Pages workflow passes `github.run_number`, so each
        // alpha supersedes the previous one. Local builds keep 1.
        versionCode = providers.environmentVariable("ALPHA_VERSION_CODE").orNull?.toIntOrNull() ?: 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    val releaseKeystorePath = providers.environmentVariable("RELEASE_KEYSTORE_PATH").orNull
    val releaseKeystore = releaseKeystorePath?.let { file(it) }
    val releaseSigningAvailable = releaseKeystore?.exists() == true

    signingConfigs {
        if (alphaSigningAvailable) {
            create("alpha") {
                storeFile = alphaKeystore
                storePassword = providers.environmentVariable("ALPHA_KEYSTORE_PASSWORD").orNull
                keyAlias = providers.environmentVariable("ALPHA_KEY_ALIAS").orNull
                keyPassword = providers.environmentVariable("ALPHA_KEY_PASSWORD").orNull
            }
        }
        if (releaseSigningAvailable) {
            create("release") {
                storeFile = releaseKeystore
                storePassword = providers.environmentVariable("RELEASE_KEYSTORE_PASSWORD").orNull
                keyAlias = providers.environmentVariable("RELEASE_KEY_ALIAS").orNull
                keyPassword = providers.environmentVariable("RELEASE_KEY_PASSWORD").orNull
            }
        }
    }

    buildTypes {
        if (alphaSigningAvailable) {
            // The alpha ships the debug variant (no R8), so the override belongs here rather than
            // on a separate build type: same artifact path, same tests, stable certificate.
            getByName("debug") {
                signingConfig = signingConfigs.getByName("alpha")
            }
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (releaseSigningAvailable) {
                signingConfig = signingConfigs["release"]
            }
            optimization {
                enable = true
                packageScope = setOf("androidx.**", "kotlin.**", "kotlinx.**")
            }
        }
    }
    // SMO-S5-04: per-app locale switching is useless if Play splits vi/fr out of the base APK,
    // so all locales ship in every device split.
    bundle {
        language {
            enableSplit = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
    }
    lint {
        // Version currency is audited separately in the version catalog; these three checks only
        // report "a newer release exists" and would drown out actionable findings in the sprint
        // gate (they fire for every declared library on every build).
        disable += setOf(
            "GradleDependency",
            "NewerVersionAvailable",
            "AndroidGradlePluginVersion"
        )
        warningsAsErrors = false
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(libs.kotlinx.coroutines.android)

    // Koin
    implementation(libs.koin.core)
    implementation(libs.koin.android)
    implementation(libs.koin.androidx.compose)

    // Ktor Client & Serialization
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.kotlinx.serialization.json)

    // Room
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // DataStore
    implementation(libs.datastore.preferences)

    // Location
    implementation(libs.play.services.location)

    // Background work (Morning alert)
    implementation(libs.androidx.work.runtime.ktx)

    // Unit Testing
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockk)
    testImplementation(libs.turbine)
    testImplementation(libs.ktor.client.mock)
    testImplementation(libs.koin.test)

    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.work.testing)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}