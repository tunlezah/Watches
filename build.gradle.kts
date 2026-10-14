plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.kmp.library) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
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
// dependencies for linking, so the source scan is what covers iOS.)
val networkLibraries = listOf("io.ktor", "com.squareup.okhttp3", "io.coil-kt.coil3:coil-network")
val networkFreeModules = setOf(":shared:core", ":shared:data", ":shared:ui")

subprojects {
    if (path in networkFreeModules) {
        tasks.register("checkNoNetworkLibraries") {
            group = "verification"
            doLast {
                val offenders = configurations
                    .filter { it.isCanBeResolved && it.name.endsWith("CompileClasspath") && !it.name.startsWith("metadata") && !it.name.contains("Test", ignoreCase = true) }
                    .flatMap { config ->
                        config.incoming.resolutionResult.allComponents.mapNotNull { c ->
                            c.moduleVersion?.let { "${it.group}:${it.name}" }?.takeIf { id -> networkLibraries.any { id.startsWith(it) } }
                                ?.let { "${config.name}: $it" }
                        }
                    }
                    .distinct()
                    .toMutableList()
                val checked = configurations.filter { it.isCanBeResolved && it.name.endsWith("CompileClasspath") && !it.name.startsWith("metadata") && !it.name.contains("Test", ignoreCase = true) }.map { it.name }
                logger.lifecycle("$path: checked ${checked.joinToString()} and sources")
                val imports = Regex("""^import (io\.ktor|okhttp3|coil3\.network)""", RegexOption.MULTILINE)
                fileTree("src") { include("**/*.kt") }.forEach { f ->
                    if (imports.containsMatchIn(f.readText())) offenders += "source: ${f.relativeTo(projectDir)}"
                }
                if (offenders.isNotEmpty()) throw GradleException("Network libraries on $path:\n" + offenders.joinToString("\n"))
            }
        }
    }
}

tasks.register("checkNetworkIsolation") {
    group = "verification"
    description = "Fails if any module other than :shared:search can compile against a network library."
    dependsOn(networkFreeModules.map { "$it:checkNoNetworkLibraries" })
}
