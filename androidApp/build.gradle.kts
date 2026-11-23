plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.roborazzi)
}

// Release signing comes from the environment (CI secrets) or ~/.gradle/gradle.properties, never the repo.
// Without it, release builds are signed with the debug key instead (see buildTypes). Blank counts as
// unset: GitHub Actions passes a secret that doesn't exist as an empty string, and an empty alias
// would fail the build.
fun signingSetting(env: String, property: String): String? =
    providers.environmentVariable(env).orNull?.takeIf { it.isNotBlank() }
        ?: providers.gradleProperty(property).orNull?.takeIf { it.isNotBlank() }

val keystorePath: String? = signingSetting("WATCHES_KEYSTORE", "watches.keystore")
val keystorePassword: String? = signingSetting("WATCHES_KEYSTORE_PASSWORD", "watches.keystorePassword")
val keyAliasName: String = signingSetting("WATCHES_KEY_ALIAS", "watches.keyAlias") ?: "watches"
val keyPasswordValue: String? = signingSetting("WATCHES_KEY_PASSWORD", "watches.keyPassword") ?: keystorePassword

// One version for both phones: Xcode reads the same file (iosApp/project.yml).
val appVersion: Map<String, String> = providers.fileContents(rootProject.layout.projectDirectory.file("version.xcconfig")).asText.get()
    .lines()
    .mapNotNull { Regex("""^\s*(\w+)\s*=\s*(\S+)\s*$""").find(it)?.destructured?.let { (key, value) -> key to value } }
    .toMap()

android {
    namespace = "io.github.tunlezah.watches"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "io.github.tunlezah.watches"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = appVersion.getValue("CURRENT_PROJECT_VERSION").toInt()
        versionName = appVersion.getValue("MARKETING_VERSION")
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
            // Without a release key, the debug key: the APK still installs. Each machine has its own
            // debug key, and each CI run is a new machine, so those builds don't install over each other.
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
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
