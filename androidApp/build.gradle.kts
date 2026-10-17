plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.roborazzi)
}

// Release signing comes from the environment (CI secrets) or ~/.gradle/gradle.properties, never the repo.
// Without it, release builds are produced unsigned.
val keystorePath: String? = providers.environmentVariable("WATCHES_KEYSTORE").orNull
    ?: providers.gradleProperty("watches.keystore").orNull
val keystorePassword: String? = providers.environmentVariable("WATCHES_KEYSTORE_PASSWORD").orNull
    ?: providers.gradleProperty("watches.keystorePassword").orNull
val keyAliasName: String = providers.environmentVariable("WATCHES_KEY_ALIAS").orNull
    ?: providers.gradleProperty("watches.keyAlias").orNull ?: "watches"
val keyPasswordValue: String? = providers.environmentVariable("WATCHES_KEY_PASSWORD").orNull
    ?: providers.gradleProperty("watches.keyPassword").orNull ?: keystorePassword

android {
    namespace = "io.github.tunlezah.watches"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "io.github.tunlezah.watches"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 3
        versionName = "0.3.0-m3"
    }

    signingConfigs {
        if (keystorePath != null && keystorePassword != null) {
            create("release") {
                storeFile = file(keystorePath)
                storePassword = keystorePassword
                keyAlias = keyAliasName
                keyPassword = keyPasswordValue
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release")
        }
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources.excludes += setOf("/META-INF/{AL2.0,LGPL2.1}", "/META-INF/versions/9/previous-compilation-data.bin")
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.all { test ->
            // Optional offline Robolectric runtime (set robolectric.dependency.dir in ~/.gradle/gradle.properties).
            providers.gradleProperty("robolectric.dependency.dir").orNull?.let { dir ->
                test.systemProperty("robolectric.offline", "true")
                test.systemProperty("robolectric.dependency.dir", dir)
            }
            test.maxHeapSize = "3g"
            test.jvmArgs("--add-opens=java.base/java.io=ALL-UNNAMED", "--add-opens=java.base/java.lang=ALL-UNNAMED")
        }
    }
}

dependencies {
    implementation(project(":shared:ui"))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.koin.android)
    implementation(libs.androidx.profileinstaller)
    implementation(libs.androidx.work.runtime)

    // Screenshot tests render real screens on the JVM (Robolectric), so every direction can be checked without a device.
    testImplementation(libs.junit)
    testImplementation(libs.koin.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.compose.foundation)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
