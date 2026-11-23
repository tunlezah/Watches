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
val generatePhotoFixtures = tasks.register("generatePhotoFixtures") {
    val input = layout.projectDirectory.dir("src/commonTest/fixtures/photos")
    val output = layout.buildDirectory.dir("generated/photoFixtures/kotlin")
    inputs.dir(input)
    outputs.dir(output)
    doLast {
        val files = input.asFile.listFiles().orEmpty().filter { it.isFile && it.name != "README.md" }.sortedBy { it.name }
        val source = buildString {
            appendLine("// Generated from src/commonTest/fixtures/photos by :shared:data:generatePhotoFixtures. Do not edit.")
            appendLine("package io.github.tunlezah.watches.data.photos")
            appendLine()
            appendLine("internal object PhotoFixtures {")
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
        output.get().asFile.apply { deleteRecursively(); mkdirs() }.resolve("PhotoFixtures.kt").writeText(source)
    }
}

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
        getByName("androidDeviceTest").dependencies {
            implementation(libs.androidx.test.runner)
            implementation(libs.androidx.test.ext.junit)
        }
        commonTest {
            kotlin.srcDir(generatePhotoFixtures)
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
    listOf("kspAndroid", "kspJvm", "kspIosArm64", "kspIosSimulatorArm64").forEach {
        add(it, libs.room.compiler)
    }
}
