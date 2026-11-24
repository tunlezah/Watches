plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kmp.library)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
}

// The photo tests use small real photos from src/commonTest/fixtures/photos (made by
// tools/photo_fixtures.py). They're compiled into the tests as a generated source, so the same tests
// read them on the JVM, on Android and on the iOS simulator, none of which share a file system.
fun registerFixtures(task: String, folder: String, pkg: String, objectName: String) = tasks.register(task) {
    val input = layout.projectDirectory.dir("src/commonTest/fixtures/$folder")
    val output = layout.buildDirectory.dir("generated/$folder-fixtures/kotlin")
    inputs.dir(input)
    outputs.dir(output)
    doLast {
        val files = input.asFile.listFiles().orEmpty().filter { it.isFile && it.name != "README.md" }.sortedBy { it.name }
        val source = buildString {
            appendLine("// Generated from src/commonTest/fixtures/$folder by :shared:data:$task. Do not edit.")
            appendLine("package $pkg")
            appendLine()
            appendLine("internal object $objectName {")
            appendLine("    val names: List<String> = listOf(${files.joinToString { "\"${it.name}\"" }})")
            appendLine()
            appendLine("    /** A fresh copy each time: the tests change the bytes they're given. */")
            appendLine("    fun bytes(name: String): ByteArray = decode(")
            appendLine("        when (name) {")
            for (f in files) {
                val hex = f.readBytes().joinToString("") { "%02x".format(it) }
                appendLine("            \"${f.name}\" -> listOf(")
                hex.chunked(4_000).forEach { appendLine("                \"$it\",") }
                appendLine("            )")
            }
            appendLine("            else -> error(\"Missing fixture \$name\")")
            appendLine("        },")
            appendLine("    )")
            appendLine()
            appendLine("    private fun decode(chunks: List<String>): ByteArray {")
            appendLine("        val hex = chunks.joinToString(\"\")")
            appendLine("        return ByteArray(hex.length / 2) { hex.substring(2 * it, 2 * it + 2).toInt(16).toByte() }")
            appendLine("    }")
            appendLine("}")
        }
        output.get().asFile.apply { deleteRecursively(); mkdirs() }.resolve("$objectName.kt").writeText(source)
    }
}

val generatePhotoFixtures = registerFixtures("generatePhotoFixtures", "photos", "io.github.tunlezah.watches.data.photos", "PhotoFixtures")

// A backup made by the Mac's image code (the macOS workflow's ReferenceArchiveMaker step), which the
// same tests restore on every platform.
val generateArchiveFixtures = registerFixtures("generateArchiveFixtures", "archives", "io.github.tunlezah.watches.data", "ArchiveFixtures")

kotlin {
    android {
        namespace = "io.github.tunlezah.watches.data"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()
        // The shared tests also run on an Android device or emulator (the Photos workflow), because the
        // photo conformance suite has to meet Android's real decoders, which no desktop stand-in has.
        withDeviceTestBuilder { sourceSetTreeName = "test" }.configure {
            instrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        }
    }
    // The JVM target exists for fast database and file tests on any machine.
    jvm()
    iosArm64()
    iosSimulatorArm64()
    // The Mac app itself runs on the JVM target. This native one is for what the Mac shares with the
    // iPhone: Apple's own frameworks, used from one source set (appleMain) by both. Its tests run on
    // a Mac directly, with no simulator.
    macosArm64()

    sourceSets.all {
        languageSettings.optIn("kotlin.time.ExperimentalTime")
        languageSettings.optIn("kotlin.uuid.ExperimentalUuidApi")
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":shared:core"))
            api(libs.room.runtime)
            implementation(libs.sqlite.bundled)
            api(libs.okio)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.koin.core)
        }
        androidMain.dependencies {
            implementation(libs.androidx.exifinterface)
        }
        jvmMain.dependencies {
            // Loads the Mac's native library (the macBridge module) into the desktop app.
            implementation(libs.jna)
        }
        getByName("androidDeviceTest").dependencies {
            implementation(libs.androidx.test.runner)
            implementation(libs.androidx.test.ext.junit)
        }
        commonTest {
            kotlin.srcDir(generatePhotoFixtures)
            kotlin.srcDir(generateArchiveFixtures)
            dependencies {
                implementation(kotlin("test"))
                implementation(libs.kotlinx.coroutines.test)
                implementation(libs.turbine)
            }
        }
    }
}

room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    listOf("kspAndroid", "kspJvm", "kspIosArm64", "kspIosSimulatorArm64", "kspMacosArm64").forEach {
        add(it, libs.room.compiler)
    }
}

// On a Mac, the JVM tests can run against Apple's image code through the bridge, as the Mac app does:
// WATCHES_BRIDGE names the library that :macBridge:linkReleaseSharedMacosArm64 built. Without it
// they use the plain-Java codec, as on Linux.
// On Linux, WATCHES_SYSTEM_IMAGE_LIBS (required, off, or unset) says whether the plain-Java codec takes
// HEIC and WebP from the system's libheif and libwebp (SystemImageLibraries), so it is an input too:
// a result from one setting is never reused for another.
tasks.withType<Test>().configureEach {
    val bridge = providers.environmentVariable("WATCHES_BRIDGE").orNull?.takeIf { it.isNotBlank() }
    inputs.property("watches.bridge", bridge ?: "")
    if (bridge != null) systemProperty("watches.bridge", bridge)
    inputs.property("watches.systemImageLibs", providers.environmentVariable("WATCHES_SYSTEM_IMAGE_LIBS").orElse(""))
    // The photos LocationRemovalTest cleaned, which CI reads back with Pillow. Declared, so that a test
    // run restored from the build cache brings them back too, rather than leaving the check nothing to read.
    outputs.dir(layout.projectDirectory.dir("build/outputs/cleaned-photos")).optional()
}
