import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

/**
 * The release signing material is never committed. It arrives either as a
 * gitignored `keystore.properties` next to this file or as Gradle project
 * properties (ORG_GRADLE_PROJECT_storePassword and friends), which is how CI
 * passes it. Until one of them exists the release build falls back to the
 * debug key so the pipeline never breaks on a missing secret.
 */
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) {
        keystorePropertiesFile.inputStream().use { load(it) }
    }
}

fun signingSecret(name: String): String? =
    keystoreProperties.getProperty(name) ?: providers.gradleProperty(name).orNull

android {
    namespace = "com.soluplay.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.soluplay.app"
        minSdk = 21
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"

        ndk {
            abiFilters += listOf("armeabi-v7a", "arm64-v8a")
        }
    }

    /**
     * One project, two surfaces. The phone flavour is the one that ships: it
     * carries both the phone and the TV launcher, so a single APK covers a
     * handset and an Android TV box. The tv flavour is the prepared split for
     * the day the two need separate packages (PLAN.md section 12).
     */
    flavorDimensions += "surface"
    productFlavors {
        create("phone") {
            dimension = "surface"
            applicationId = "com.soluplay.app"
        }
        create("tv") {
            dimension = "surface"
            applicationId = "com.soluplay.tv"
        }
    }

    signingConfigs {
        if (signingSecret("storeFile") != null) {
            create("release") {
                storeFile = rootProject.file(signingSecret("storeFile")!!)
                storePassword = signingSecret("storePassword")
                keyAlias = signingSecret("keyAlias")
                keyPassword = signingSecret("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = if (signingConfigs.findByName("release") != null) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
        }
    }

    buildFeatures {
        compose = true
    }

    /**
     * One Gradle project, so the shared code is a source directory rather than
     * a module: core/ is on every variant, feature-tv/ is on every variant
     * (the TV UI ships inside the phone APK), and feature-phone/ is on the
     * phone flavour only.
     */
    sourceSets {
        named("main") {
            java.srcDirs("../core/src/main/kotlin", "../feature-tv/src/main/kotlin")
        }
        named("phone") {
            java.srcDirs("../feature-phone/src/main/kotlin")
        }
        named("test") {
            java.srcDirs("../core/src/test/kotlin")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    lint {
        abortOnError = false
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.foundation)
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)

    implementation(libs.media3.exoplayer)
    implementation(libs.media3.exoplayer.hls)
    implementation(libs.media3.ui)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
}
