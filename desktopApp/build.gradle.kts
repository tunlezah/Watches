import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
}

// One version for every platform: Android and Xcode read the same file.
val appVersion: Map<String, String> = providers.fileContents(rootProject.layout.projectDirectory.file("version.xcconfig")).asText.get()
    .lines()
    .mapNotNull { Regex("""^\s*(\w+)\s*=\s*(\S+)\s*$""").find(it)?.destructured?.let { (key, value) -> key to value } }
    .toMap()
val marketingVersion: String = appVersion.getValue("MARKETING_VERSION")
val buildNumber: String = appVersion.getValue("CURRENT_PROJECT_VERSION")

// jpackage won't make a Mac bundle whose version starts with 0, and the app is at 0.x. Until 1.0.0
// the bundle is 1.0.<build> instead, which still rises with every build. That number shows only in
// Finder's Get Info: About, the disk image's name and every backup's manifest carry the real version.
val macBundleVersion: String = if (marketingVersion.substringBefore('.') == "0") "1.0.$buildNumber" else marketingVersion

// The version as a constant the shell can read: it goes into About and into every backup's manifest.
val generateBuildInfo = tasks.register("generateBuildInfo") {
    val output = layout.buildDirectory.dir("generated/buildInfo/kotlin")
    val version = marketingVersion
    val build = buildNumber
    inputs.property("version", version)
    inputs.property("build", build)
    outputs.dir(output)
    doLast {
        val file = output.get().asFile.resolve("io/github/tunlezah/watches/desktop/BuildInfo.kt")
        file.parentFile.mkdirs()
        file.writeText(
            """
            // Generated from version.xcconfig by :desktopApp:generateBuildInfo. Do not edit.
            package io.github.tunlezah.watches.desktop

            internal object BuildInfo {
                const val VERSION = "$version"
                const val BUILD = $build
            }
            """.trimIndent() + "\n",
        )
    }
}

// The Mac bridge (the macBridge module): Apple's image code as a native library, carried inside the
// app. Only a Mac can link it, so anywhere else the app is built without one and uses its plain-Java
// image code, which is what the tests and screenshots on Linux run.
val onMac = System.getProperty("os.name").startsWith("Mac")
val bridgeResources = tasks.register<Sync>("bridgeResources") {
    into(layout.buildDirectory.dir("bridgeResources"))
    if (onMac) {
        dependsOn(":macBridge:linkReleaseSharedMacosArm64")
        // The folder's name is the plugin's own for "an Apple silicon Mac".
        from(rootProject.layout.projectDirectory.dir("macBridge/build/bin/macosArm64/releaseShared")) {
            include("libwatchesbridge.dylib")
            into("macos-arm64")
        }
    }
}

kotlin {
    sourceSets.main { kotlin.srcDir(generateBuildInfo) }
    compilerOptions {
        optIn.add("kotlin.time.ExperimentalTime")
        optIn.add("androidx.compose.ui.ExperimentalComposeUiApi")
    }
}

dependencies {
    implementation(project(":shared:ui"))
    // Compose for this machine's system: the Mac when packaged, Linux in CI.
    implementation(compose.desktop.currentOs)
    implementation(libs.compose.runtime)
    implementation(libs.compose.foundation)
    implementation(libs.compose.ui)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.koin.compose)
    // The shared screens open the Mac's file panels through FileKit, which wants the app's name at start-up.
    implementation(libs.filekit.core)
    // Dispatchers.Main, which the view models run on, is the Swing event thread on a desktop.
    implementation(libs.kotlinx.coroutines.swing)

    testImplementation(kotlin("test"))
    testImplementation(libs.koin.test)
    testImplementation(libs.kotlinx.coroutines.test)
    // To write a database file of a chosen version, for the newer-collection check.
    testImplementation(libs.sqlite.bundled)
}

tasks.withType<Test>().configureEach {
    // No display in CI: the tests draw off-screen.
    systemProperty("java.awt.headless", "true")
    // Screens drawn by DesktopScreenshotTest, kept by CI for review like the Android ones.
    systemProperty("watches.screenshots", layout.buildDirectory.dir("outputs/screenshots").get().asFile.absolutePath)
    testLogging { events("failed", "skipped") }
}

compose.desktop {
    application {
        mainClass = "io.github.tunlezah.watches.desktop.MainKt"
        // The title bar, menus and file dialogs follow macOS's Light or Dark.
        jvmArgs += listOf("-Dapple.awt.application.appearance=system")

        nativeDistributions {
            targetFormats(TargetFormat.Dmg)
            packageName = "Watches"
            packageVersion = marketingVersion
            description = "A private, offline-first watch collection"
            // The Java runtime is trimmed to what the app uses. `--self-test` inside the packaged app
            // (CI runs it) is what proves the list: a missing module shows only at run time.
            // (java.base, java.desktop, java.logging and jdk.crypto.ec are the plugin's own defaults; the rest is
            // what :desktopApp:suggestRuntimeModules finds in the app and its libraries.)
            modules("java.instrument", "java.management", "jdk.security.auth", "jdk.unsupported")
            // Files the app carries beside its code: the Mac bridge. At run time the plugin names their
            // folder in the system property compose.application.resources.dir.
            appResourcesRootDir.set(layout.buildDirectory.dir("bridgeResources"))

            macOS {
                // The app's identity on the Mac, for good: the notification permission and the Dock hang off it.
                bundleID = "io.github.tunlezah.watches.mac"
                dockName = "Watches"
                minimumSystemVersion = "13.0"
                packageVersion = macBundleVersion
                packageBuildVersion = buildNumber
                iconFile.set(project.file("icons/Watches.icns"))
            }
        }
    }
}

// The plugin copies the app's resources in a task of its own, which has to wait for the bridge.
tasks.matching { it.name == "prepareAppResources" }.configureEach { dependsOn(bridgeResources) }
