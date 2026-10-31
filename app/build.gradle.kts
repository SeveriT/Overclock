import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    id("com.google.gms.google-services")
}

val localProperties = Properties()
val localPropertiesFile = rootProject.file("local.properties")
if (localPropertiesFile.exists()) {
    localProperties.load(localPropertiesFile.inputStream())
}
val premiumEmails = localProperties.getProperty("PREMIUM_EMAILS") ?: ""

android {
    namespace = "com.serkka.tracker"
    compileSdk = 37
    buildFeatures {
        buildConfig = true
    }

    defaultConfig {
        applicationId = "com.serkka.tracker"
        minSdk = 26
        targetSdk = 35
        versionCode = 52
        versionName = "2.4.0"

        buildConfigField("String", "PREMIUM_EMAILS", "\"$premiumEmails\"")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            // R8/ProGuard disabled — not publishing, so skip minification/shrinking to
            // avoid obfuscation-related crashes and speed up release builds.
            isMinifyEnabled = false
            isShrinkResources = false
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

// APK file name, e.g. Overclock-v2.3.0-release.apk — follows versionName so it never goes stale.
base {
    archivesName.set("Overclock-v${android.defaultConfig.versionName}")
}

// AGP 9 compiles Kotlin itself (built-in Kotlin), so there's no kotlin-android plugin.
kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
    }
}

// ── GitHub release ────────────────────────────────────────────────────────────
// Android Studio: Gradle panel → Tracker → app → Tasks → release → githubRelease.
// Builds the release APK and publishes it as GitHub release "v<versionName>" on the commit
// you built, with notes generated from the commits since the previous release.
// Needs the GitHub CLI once: `winget install GitHub.cli`, then `gh auth login`.
// Releases are built locally on purpose: release APKs are signed with this machine's debug
// key, so a CI-built APK couldn't install as an update over the app on the phone.
tasks.register<Exec>("githubRelease") {
    group = "release"
    description = "Builds the release APK and publishes it as a GitHub release."
    dependsOn("assembleRelease")

    val versionName = android.defaultConfig.versionName
    val tag = "v$versionName"
    val apk = layout.buildDirectory.file("outputs/apk/release/Overclock-$tag-release.apk").get().asFile
    workingDir = rootDir

    // gh may not be on the PATH Android Studio started with (e.g. installed while AS was open)
    val gh = listOf(
        "C:\\Program Files\\GitHub CLI\\gh.exe",
        System.getenv("LOCALAPPDATA")?.let { "$it\\Programs\\GitHub CLI\\gh.exe" }
    ).firstOrNull { it != null && File(it).exists() } ?: "gh"

    fun git(vararg args: String): String =
        providers.exec { commandLine("git", *args); workingDir = rootDir }
            .standardOutput.asText.get().trim()

    doFirst {
        require(apk.exists()) { "Release APK not found: $apk" }
        // The release is created on the remote, so everything it describes must be pushed.
        require(git("status", "--porcelain").isEmpty()) {
            "Uncommitted changes — commit them first so $tag matches the APK."
        }
        git("fetch", "--quiet", "origin")
        val head = git("rev-parse", "HEAD")
        require(git("branch", "-r", "--contains", head).isNotEmpty()) {
            "Commit $head isn't pushed yet — push first, then run githubRelease."
        }
        commandLine(
            gh, "release", "create", tag, apk.absolutePath,
            "--target", head,
            "--title", "Overclock $tag",
            "--generate-notes"
        )
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
    implementation(libs.androidx.navigation.compose)
    
    // Room Database
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // Lifecycle components
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)

    // Google Drive and Auth
    implementation(libs.play.services.auth)
    implementation(libs.google.api.client.android)
    implementation(libs.google.api.services.drive)
    
    // WorkManager
    implementation(libs.androidx.work.runtime.ktx)

    // Coil for Image Loading
    implementation(libs.coil.compose)

    // Palette for extracting colors from album art
    implementation(libs.androidx.palette.ktx)

    // Google Play Billing
    implementation(libs.billing.ktx)

    // Firebase (BOM 34+ folded the -ktx modules into the main artifacts)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.analytics)
    implementation(libs.firebase.config)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
