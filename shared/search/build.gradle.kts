plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kmp.library)
    alias(libs.plugins.kotlin.serialization)
}

// The parser tests read saved pages from src/commonTest/fixtures. They're compiled into the tests as
// a generated source, so the tests read them the same way on the JVM and on the iOS simulator.
val generateFixtures by tasks.registering {
    val input = layout.projectDirectory.dir("src/commonTest/fixtures")
    val output = layout.buildDirectory.dir("generated/fixtures/kotlin")
    inputs.dir(input)
    outputs.dir(output)
    doLast {
        val root = input.asFile
        val files = root.walkTopDown().filter { it.isFile && it.name != "README.md" }.sortedBy { it.invariantSeparatorsPath }.toList()
        // Everything outside printable ASCII is escaped, so a chunk boundary can't split a character's bytes.
        fun literal(text: String) = buildString {
            append('"')
            for (c in text) when {
                c == '\\' -> append("\\\\")
                c == '"' -> append("\\\"")
                c == '$' -> append("\\$")
                c in ' '..'~' -> append(c)
                else -> append("\\u").append(c.code.toString(16).padStart(4, '0'))
            }
            append('"')
        }
        val source = buildString {
            appendLine("// Generated from src/commonTest/fixtures by :shared:search:generateFixtures. Do not edit.")
            appendLine("package io.github.tunlezah.watches.search")
            appendLine()
            appendLine("internal object Fixtures {")
            appendLine("    fun text(path: String): String = when (path) {")
            for (f in files) {
                val chunks = f.readText().chunked(4_000).joinToString(",\n            ") { literal(it) }
                appendLine("        \"${f.relativeTo(root).invariantSeparatorsPath}\" -> listOf(\n            $chunks,\n        ).joinToString(\"\")")
            }
            appendLine("        else -> error(\"Missing fixture \$path\")")
            appendLine("    }")
            appendLine("}")
        }
        output.get().asFile.apply { deleteRecursively(); mkdirs() }.resolve("Fixtures.kt").writeText(source)
    }
}

// The search module is the only module allowed to touch the network.
// Ktor stays an implementation detail so it never leaks onto another module's classpath.
kotlin {
    android {
        namespace = "io.github.tunlezah.watches.search"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()
    }
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
            implementation(libs.ktor.client.core)
            implementation(libs.ksoup)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.koin.core)
        }
        androidMain.dependencies {
            implementation(libs.ktor.client.okhttp)
        }
        jvmMain.dependencies {
            implementation(libs.ktor.client.okhttp)
        }
        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
        }
        commonTest {
            kotlin.srcDir(generateFixtures)
            dependencies {
                implementation(kotlin("test"))
                implementation(libs.kotlinx.coroutines.test)
                implementation(libs.ktor.client.mock)
            }
        }
    }
}
