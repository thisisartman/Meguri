import java.text.SimpleDateFormat
import java.util.Date
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.room)
}

// Release signing lives outside the repo. ~/.gradle/gradle.properties sets
// COLLECTIBLES_SIGNING to a properties file with storeFile, storePassword,
// keyAlias and keyPassword. Without it the release build is left unsigned.
val signingProps: Properties? = providers.gradleProperty("COLLECTIBLES_SIGNING").orNull
    ?.let { file(it) }
    ?.takeIf { it.isFile }
    ?.let { f -> Properties().apply { f.inputStream().use { load(it) } } }

// Owner, 2026-11-09: the stable release after the beta.9 testing
val appVersionName = "0.7.1"

android {
    namespace = "io.github.thisisartman.collectibles"
    compileSdk = 37

    defaultConfig {
        // Permanent since real data went in (issue #14). Changing it would make a new app and strand the data.
        applicationId = "io.github.thisisartman.collectibles"
        minSdk = 29
        targetSdk = 37
        versionCode = 16
        versionName = appVersionName
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        manifestPlaceholders["appLabel"] = "Meguri"
        ndk { abiFilters += "arm64-v8a" }
    }

    signingConfigs {
        if (signingProps != null) {
            create("release") {
                storeFile = file(signingProps.getProperty("storeFile"))
                storePassword = signingProps.getProperty("storePassword")
                keyAlias = signingProps.getProperty("keyAlias")
                keyPassword = signingProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".dev"
            versionNameSuffix = "-dev"
            manifestPlaceholders["appLabel"] = "Meguri (dev)"
            // Also x86_64, so the map library runs natively on a laptop emulator instead of through ARM translation
            ndk { abiFilters += "x86_64" }
            // Region packs served from the laptop over USB (adb reverse tcp:8765 tcp:8765); see tools/packs/README.md
            buildConfigField("String", "PACKS_URL", "\"http://127.0.0.1:8765/\"")
        }
        release {
            // Region packs (0.5): the public data repository's latest release (hosting pending the owner's yes)
            buildConfigField("String", "PACKS_URL", "\"https://github.com/thisisartman/meguri-data/releases/latest/download/\"")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release")
        }
        // The release build's shrunk code under the dev app's name and debug key: lets a phone's dev copy run exactly
        // what a release runs (the shrinker broke the card scanner once, 2026-11-08) without touching the real app
        create("devRelease") {
            initWith(getByName("release"))
            applicationIdSuffix = ".dev"
            versionNameSuffix = "-devrelease"
            manifestPlaceholders["appLabel"] = "Meguri (dev)"
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    // Page-by-page mockups in dev builds only (src/mockup, opened with adb ... --es page <name>); release has none
    sourceSets {
        getByName("debug") { kotlin.directories.add("src/mockup/java") }
        getByName("devRelease") { kotlin.directories.add("src/mockup/java") }
    }
}

// Test build for the bulk-suggest page (#206) only: above the Poco's dev copy (18), so it installs over it.
// Drop this before merging.
androidComponents {
    onVariants(selector().withBuildType("devRelease")) { v -> v.outputs.forEach { it.versionCode.set(19) } }
}

room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    // Places on Nearby (#192): train, temple, shop front and the other standard icons; release shrinks it to what is used
    implementation(libs.androidx.compose.material.icons.extended)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.androidx.work.runtime)
    implementation(libs.maplibre.android)
    implementation(libs.okhttp)
    implementation(libs.androidx.documentfile)
    // Geofencing only (PRD FR-12); everything else uses Android's own LocationManager
    implementation(libs.play.services.location)
    // Scan a card (M7, M8): edge-finding scanner and Japanese text reading, both through Play services (small)
    implementation(libs.mlkit.doc.scanner)
    implementation(libs.mlkit.text.ja)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)

    testImplementation(libs.junit)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.okhttp.tls)
    // The real org.json for JVM unit tests (Android's copy is only a stub there)
    testImplementation("org.json:json:20240303")
    // room-testing needs serialization 1.8; the test APK has to match the app, which got 1.7.3 through navigation
    implementation(libs.kotlinx.serialization.core)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(libs.androidx.test.core)
}

// Copies the built APKs to build/dist as meguri-<dev|release>-v<version>-<yyyymmdd-hhmm>.apk.
listOf("debug" to "dev", "release" to "release").forEach { (type, label) ->
    val cap = type.replaceFirstChar { it.uppercase() }
    // Only local strings here: the rename lambda must not reach back into the build script,
    // or the configuration cache cannot store the task. The time is read inside the lambda,
    // so it is taken when the copy runs, not frozen in the cached configuration.
    val prefix = "meguri-$label-v$appVersionName-"
    tasks.register<Copy>("dist$cap") {
        dependsOn("assemble$cap")
        from(layout.buildDirectory.dir("outputs/apk/$type")) { include("*.apk") }
        into(layout.buildDirectory.dir("dist"))
        rename { prefix + SimpleDateFormat("yyyyMMdd-HHmm").format(Date()) + ".apk" }
    }
}

// The offline map is no longer inside the app (0.5): it comes as region packs (tools/packs, data/RegionPacks.kt).
