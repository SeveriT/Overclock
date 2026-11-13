plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    id("com.google.gms.google-services")
}

// Premium whitelist lives only in Firebase Remote Config ("premium_emails"). Never bake
// emails into BuildConfig: release APKs are published on GitHub and R8 is off, so any
// string compiled in is readable by anyone who downloads the APK.

android {
    namespace = "com.serkka.tracker"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.serkka.tracker"
        minSdk = 26
        targetSdk = 35
        versionCode = 73
        versionName = "3.4.2"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            // Android Studio picks this variant by default (Build Variants panel can still switch)
            isDefault = true
            // R8 shrinking on (unused code + resources removed: mostly the huge icons library),
            // but no renaming (-dontobfuscate in proguard-rules.pro): renaming caused the past
            // R8 crashes and saves little here, and readable names keep crash logs useful.
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
    // Same notes as the app's "What's new" popup (WhatsNew.kt)
    val notesJson = file("src/main/assets/release_notes.json")
    val notesOut = layout.buildDirectory.file("release-notes.md").get().asFile
    workingDir = rootDir

    // gh may not be on the PATH Android Studio started with (e.g. installed while AS was open)
    val gh = listOf(
        "C:\\Program Files\\GitHub CLI\\gh.exe",
        System.getenv("LOCALAPPDATA")?.let { "$it\\Programs\\GitHub CLI\\gh.exe" }
    ).firstOrNull { it != null && File(it).exists() } ?: "gh"

    // Plain locals, not `providers`/`rootDir` off the script: the configuration cache can't
    // store a task action that holds on to the whole Project.
    val execs = providers
    val root = rootDir
    fun git(vararg args: String): String =
        execs.exec { commandLine("git", *args); workingDir = root }
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
        // Release notes: this version's entries from release_notes.json as a bullet list, with
        // GitHub's generated notes (compare link) appended. The in-app updater shows these too.
        @Suppress("UNCHECKED_CAST")
        val items = (groovy.json.JsonSlurper().parse(notesJson) as Map<String, Any?>)[versionName]
            as? List<Map<String, String>> ?: emptyList()
        val notesArgs = if (items.isEmpty()) emptyList() else {
            notesOut.parentFile.mkdirs()
            notesOut.writeText(items.joinToString("\n", postfix = "\n") { "- **${it["title"]}** — ${it["text"]}" })
            listOf("--notes-file", notesOut.absolutePath)
        }
        commandLine(
            listOf(gh, "release", "create", tag, apk.absolutePath, "--target", head, "--title", "Overclock $tag") +
                notesArgs + "--generate-notes"
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
