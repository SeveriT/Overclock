import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    id("com.google.gms.google-services")
}

val localProperties = Properties()
val localPropertiesFile = rootProject.file("local.properties")
if (localPropertiesFile.exists()) {
    localProperties.load(localPropertiesFile.inputStream())
}
val stravaSecret = localProperties.getProperty("STRAVA_CLIENT_SECRET") ?: "MISSING_SECRET"
val stravaClientId = localProperties.getProperty("STRAVA_CLIENT_ID") ?: "MISSING_ID"
val geminiApiKey = localProperties.getProperty("GEMINI_API_KEY") ?: "MISSING_KEY"
val premiumEmails = localProperties.getProperty("PREMIUM_EMAILS") ?: ""

android {
    namespace = "com.serkka.tracker"
    compileSdk = 36
    buildFeatures {
        buildConfig = true
    }

    defaultConfig {
        applicationId = "com.serkka.tracker"
        minSdk = 26
        targetSdk = 35
        versionCode = 35
        versionName = "1.9.1"

        buildConfigField("String", "STRAVA_CLIENT_SECRET", "\"$stravaSecret\"")
        buildConfigField("String", "STRAVA_CLIENT_ID", "\"$stravaClientId\"")
        buildConfigField("String", "GEMINI_API_KEY", "\"$geminiApiKey\"")
        buildConfigField("String", "PREMIUM_EMAILS", "\"$premiumEmails\"")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
        isCoreLibraryDesugaringEnabled = true
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "META-INF/DEPENDENCIES"
            excludes += "META-INF/INDEX.LIST"
        }
    }
}

dependencies {
    coreLibraryDesugaring(libs.desugar.jdk.libs)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.material3.adaptive.navigation.suite)
    implementation("androidx.navigation:navigation-compose:2.8.5")
    
    // Room Database
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // Lifecycle components - using stable version to avoid lint bugs
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")

    // Google Drive and Auth
    implementation(libs.play.services.auth)
    implementation(libs.google.api.client.android)
    implementation(libs.google.api.services.drive)
    
    // WorkManager
    implementation(libs.androidx.work.runtime.ktx)

    // Networking (Strava)
    implementation(libs.retrofit)
    implementation(libs.retrofit.gson)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    
    // Coil for Image Loading
    implementation(libs.coil.compose)

    // Palette for extracting colors from album art
    implementation("androidx.palette:palette-ktx:1.0.0")

    // Encrypted SharedPreferences for API key storage
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    // Google Play Billing
    implementation("com.android.billingclient:billing-ktx:7.1.1")

    // Firebase
    implementation(platform("com.google.firebase:firebase-bom:33.7.0"))
    implementation("com.google.firebase:firebase-analytics")
    implementation("com.google.firebase:firebase-config-ktx")

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
