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

val appVersionName = "0.2.0"

android {
    namespace = "io.github.thisisartman.collectibles"
    compileSdk = 37

    defaultConfig {
        // Permanent since real data went in (issue #14). Changing it would make a new app and strand the data.
        applicationId = "io.github.thisisartman.collectibles"
        minSdk = 29
        targetSdk = 37
        versionCode = 2
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
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release")
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
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.androidx.work.runtime)
    implementation(libs.maplibre.android)
    implementation(libs.androidx.documentfile)
    // Geofencing only (PRD FR-12); everything else uses Android's own LocationManager
    implementation(libs.play.services.location)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.core)
}

// Copies the built APKs to build/dist as collectibles-<dev|release>-v<version>-<yyyymmdd-hhmm>.apk.
listOf("debug" to "dev", "release" to "release").forEach { (type, label) ->
    val cap = type.replaceFirstChar { it.uppercase() }
    // Only local strings here: the rename lambda must not reach back into the build script,
    // or the configuration cache cannot store the task. The time is read inside the lambda,
    // so it is taken when the copy runs, not frozen in the cached configuration.
    val prefix = "collectibles-$label-v$appVersionName-"
    tasks.register<Copy>("dist$cap") {
        dependsOn("assemble$cap")
        from(layout.buildDirectory.dir("outputs/apk/$type")) { include("*.apk") }
        into(layout.buildDirectory.dir("dist"))
        rename { prefix + SimpleDateFormat("yyyyMMdd-HHmm").format(Date()) + ".apk" }
    }
}
