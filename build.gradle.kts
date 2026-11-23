plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.kmp.library) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.compose.multiplatform) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.room) apply false
    alias(libs.plugins.roborazzi) apply false
}

// "Only the search module may touch the network": fail the build if an HTTP client or Coil's
// network fetcher appears on the Android/JVM compile classpath of any other shared module, or if
// any of their source files imports one. (Kotlin/Native klib paths list transitive implementation
// dependencies for linking, so the source scan is what covers iOS.) Apple's own networking APIs
// need no library at all, so the scan also looks for them by name. The same goes for Java's own
// networking, which the Mac app (desktopApp) could otherwise reach without any of the libraries.
val networkLibraries = listOf("io.ktor", "com.squareup.okhttp3", "io.coil-kt.coil3:coil-network")
val networkFreeModules = setOf(":shared:core", ":shared:data", ":shared:ui", ":desktopApp")
val appleNetworking = Regex("""\b(NSURLSession|NSURLConnection|NSURLRequest|NSMutableURLRequest|nw_connection|nw_endpoint|CFNetwork|CFHTTP)\w*|ContentsOfURL\b|\bcontentsOfURL\b""")
// java.net.URI, URLEncoder and URLDecoder only handle text, so they stay allowed.
val javaNetworking = Regex("""\bjava\.net\.(URL\b|URLConnection|HttpURLConnection|JarURLConnection|Socket|ServerSocket|DatagramSocket|MulticastSocket|InetAddress|NetworkInterface|http\b|\*)|\bjavax\.net\b|\bHttpURLConnection\b|\bjava\.nio\.channels\.(SocketChannel|ServerSocketChannel|DatagramChannel|AsynchronousSocketChannel)""")

subprojects {
    if (path in networkFreeModules) {
        tasks.register("checkNoNetworkLibraries") {
            group = "verification"
            doLast {
                val offenders = configurations
                    .filter { it.isCanBeResolved && it.name.endsWith("CompileClasspath", ignoreCase = true) && !it.name.startsWith("metadata") && !it.name.contains("Test", ignoreCase = true) }
                    .flatMap { config ->
                        config.incoming.resolutionResult.allComponents.mapNotNull { c ->
                            c.moduleVersion?.let { "${it.group}:${it.name}" }?.takeIf { id -> networkLibraries.any { id.startsWith(it) } }
                                ?.let { "${config.name}: $it" }
                        }
                    }
                    .distinct()
                    .toMutableList()
                val checked = configurations.filter { it.isCanBeResolved && it.name.endsWith("CompileClasspath", ignoreCase = true) && !it.name.startsWith("metadata") && !it.name.contains("Test", ignoreCase = true) }.map { it.name }
                logger.lifecycle("$path: checked ${checked.joinToString()} and sources")
                val imports = Regex("""^import (io\.ktor|okhttp3|coil3\.network)""", RegexOption.MULTILINE)
                fileTree("src") { include("**/*.kt") }.forEach { f ->
                    val text = f.readText()
                    if (imports.containsMatchIn(text)) offenders += "source: ${f.relativeTo(projectDir)}"
                    appleNetworking.find(text)?.let { offenders += "source: ${f.relativeTo(projectDir)} uses ${it.value}" }
                    javaNetworking.find(text)?.let { offenders += "source: ${f.relativeTo(projectDir)} uses ${it.value}" }
                }
                if (offenders.isNotEmpty()) throw GradleException("Network libraries on $path:\n" + offenders.joinToString("\n"))
            }
        }
    }
}

// iOS simulator tests run on the simulator CI picks (-PiosSimulator=<udid>), not a hard-coded model.
// Each test is named as it starts, so a slow or stuck one shows in CI's log.
subprojects {
    tasks.withType<org.jetbrains.kotlin.gradle.targets.native.tasks.KotlinNativeSimulatorTest>().configureEach {
        providers.gradleProperty("iosSimulator").orNull?.let { device.set(it) }
        testLogging {
            events("started", "failed", "skipped")
            exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        }
    }
}

// Only a Mac builds the iPhone app, so the parts of it that are plain files are checked here, on every push.
tasks.register("checkIosProject") {
    group = "verification"
    description = "Checks the iOS project spec, the shared version file, the app icon and the privacy manifest."
    val spec = layout.projectDirectory.file("iosApp/project.yml")
    val versionFile = layout.projectDirectory.file("version.xcconfig")
    val icon = layout.projectDirectory.file("iosApp/iosApp/Assets.xcassets/AppIcon.appiconset/AppIcon.png")
    val launchColour = layout.projectDirectory.file("iosApp/iosApp/Assets.xcassets/LaunchBackground.colorset/Contents.json")
    val manifest = layout.projectDirectory.file("iosApp/iosApp/PrivacyInfo.xcprivacy")
    doLast {
        val problems = mutableListOf<String>()

        val version = versionFile.asFile.readLines()
            .mapNotNull { Regex("""^\s*(\w+)\s*=\s*(\S+)\s*$""").find(it)?.destructured?.let { (k, v) -> k to v } }.toMap()
        if (version["MARKETING_VERSION"]?.matches(Regex("""\d+\.\d+\.\d+""")) != true) problems += "version.xcconfig: MARKETING_VERSION must be numbers only (x.y.z)"
        if (version["CURRENT_PROJECT_VERSION"]?.toIntOrNull() == null) problems += "version.xcconfig: CURRENT_PROJECT_VERSION must be a whole number"

        val yml = spec.asFile.readText()
        listOf(
            "../version.xcconfig", "CFBundleShortVersionString: \$(MARKETING_VERSION)", "CFBundleVersion: \$(CURRENT_PROJECT_VERSION)",
            "NSCameraUsageDescription:", "NSFaceIDUsageDescription:", "NSPhotoLibraryAddUsageDescription:",
            "UIApplicationSupportsMultipleScenes: false", "UIColorName: LaunchBackground", "ASSETCATALOG_COMPILER_APPICON_NAME: AppIcon",
            "TARGETED_DEVICE_FAMILY: \"1\"", "ITSAppUsesNonExemptEncryption: false",
            // Compose Multiplatform stops at launch without it.
            "CADisableMinimumFrameDurationOnPhone: true",
        ).filterNot { it in yml }.forEach { problems += "iosApp/project.yml has no $it" }

        // App Store Connect wants a 1024 px icon without transparency: PNG colour type 2 (RGB) and no tRNS chunk.
        val png = icon.asFile.readBytes()
        fun int(at: Int) = (0..3).fold(0) { acc, i -> (acc shl 8) or (png[at + i].toInt() and 0xFF) }
        if (int(16) != 1024 || int(20) != 1024) problems += "AppIcon.png must be 1024 x 1024"
        if (png[25].toInt() != 2 || "tRNS" in String(png, Charsets.ISO_8859_1)) problems += "AppIcon.png must be opaque RGB: run java tools/RenderIosAppIcon.java"
        if (!launchColour.asFile.exists()) problems += "the LaunchBackground colour set is missing"

        // Well-formed, tracks nothing, and every declared API has a reason. (CI's macOS job checks it against the binary.)
        val plist = manifest.asFile.readText()
        runCatching {
            javax.xml.parsers.DocumentBuilderFactory.newInstance()
                .apply { setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false) }
                .newDocumentBuilder().parse(manifest.asFile)
        }.onFailure { problems += "PrivacyInfo.xcprivacy isn't well-formed: ${it.message}" }
        if (!Regex("""<key>NSPrivacyTracking</key>\s*<false/>""").containsMatchIn(plist)) problems += "PrivacyInfo.xcprivacy must say NSPrivacyTracking false"
        val categories = Regex("""<string>(NSPrivacyAccessedAPICategory\w+)</string>\s*<key>NSPrivacyAccessedAPITypeReasons</key>\s*<array>\s*<string>""").findAll(plist).count()
        val declared = Regex("""<string>NSPrivacyAccessedAPICategory\w+</string>""").findAll(plist).count()
        if (declared == 0 || categories != declared) problems += "PrivacyInfo.xcprivacy: every accessed API type needs at least one reason"

        if (problems.isNotEmpty()) throw GradleException(problems.joinToString("\n"))
        logger.lifecycle("iOS project: version ${version["MARKETING_VERSION"]} (${version["CURRENT_PROJECT_VERSION"]}), icon, launch colour and privacy manifest look right")
    }
}

tasks.register("checkNetworkIsolation") {
    group = "verification"
    description = "Fails if any module other than :shared:search can compile against a network library or calls Apple's or Java's networking APIs."
    dependsOn(networkFreeModules.map { "$it:checkNoNetworkLibraries" })
}
