plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.astrawave.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.astrawave.app"
        minSdk = 23
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"

        val firebaseApiKey = providers.gradleProperty("ASTRAWAVE_FIREBASE_API_KEY").orNull
            ?: providers.environmentVariable("ASTRAWAVE_FIREBASE_API_KEY").orNull
            ?: ""
        val firebaseAppId = providers.gradleProperty("ASTRAWAVE_FIREBASE_APP_ID").orNull
            ?: providers.environmentVariable("ASTRAWAVE_FIREBASE_APP_ID").orNull
            ?: ""
        val firebaseProjectId = providers.gradleProperty("ASTRAWAVE_FIREBASE_PROJECT_ID").orNull
            ?: providers.environmentVariable("ASTRAWAVE_FIREBASE_PROJECT_ID").orNull
            ?: ""
        val firebaseSenderId = providers.gradleProperty("ASTRAWAVE_FIREBASE_SENDER_ID").orNull
            ?: providers.environmentVariable("ASTRAWAVE_FIREBASE_SENDER_ID").orNull
            ?: ""
        val tmdbBearerToken = providers.gradleProperty("ASTRAWAVE_TMDB_BEARER_TOKEN").orNull
            ?: providers.environmentVariable("ASTRAWAVE_TMDB_BEARER_TOKEN").orNull
            ?: ""
        val gitSha = providers.gradleProperty("ASTRAWAVE_GIT_SHA").orNull
            ?: providers.environmentVariable("GITHUB_SHA").orNull
            ?: "local-untracked"

        buildConfigField("String", "FIREBASE_API_KEY", "\"$firebaseApiKey\"")
        buildConfigField("String", "FIREBASE_APP_ID", "\"$firebaseAppId\"")
        buildConfigField("String", "FIREBASE_PROJECT_ID", "\"$firebaseProjectId\"")
        buildConfigField("String", "FIREBASE_SENDER_ID", "\"$firebaseSenderId\"")
        buildConfigField("String", "TMDB_BEARER_TOKEN", "\"$tmdbBearerToken\"")
        buildConfigField("String", "GIT_SHA", "\"$gitSha\"")
    }

    val releaseStoreFile = providers.environmentVariable("ASTRAWAVE_RELEASE_STORE_FILE").orNull
    val releaseStorePassword = providers.environmentVariable("ASTRAWAVE_RELEASE_STORE_PASSWORD").orNull
    val releaseKeyAlias = providers.environmentVariable("ASTRAWAVE_RELEASE_KEY_ALIAS").orNull
    val releaseKeyPassword = providers.environmentVariable("ASTRAWAVE_RELEASE_KEY_PASSWORD").orNull
    val hasReleaseSigning = listOf(
        releaseStoreFile,
        releaseStorePassword,
        releaseKeyAlias,
        releaseKeyPassword,
    ).all { !it.isNullOrBlank() }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = file(requireNotNull(releaseStoreFile))
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            signingConfigs.findByName("release")?.let { signingConfig = it }
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.01.01"))
    implementation("androidx.activity:activity-compose:1.10.0")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.navigation:navigation-compose:2.8.5")
    implementation("androidx.media3:media3-exoplayer:1.6.1")
    implementation("androidx.media3:media3-exoplayer-hls:1.6.1")
    implementation("androidx.media3:media3-ui:1.6.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.1")
    implementation("io.coil-kt.coil3:coil-compose:3.1.0")
    implementation("io.coil-kt.coil3:coil-network-okhttp:3.1.0")

    implementation(platform("com.google.firebase:firebase-bom:33.16.0"))
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-firestore")

    testImplementation("junit:junit:4.13.2")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
