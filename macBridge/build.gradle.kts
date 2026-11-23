plugins {
    alias(libs.plugins.kotlin.multiplatform)
}

// The Mac bridge (docs/MACOS_PLAN.md, 8.2.6). The Mac app runs on Java, which can't call the Mac's
// own frameworks. This module is the way through: a small native library of plain C functions
// that the app loads with JNA. Its code is the Kotlin the iPhone runs (shared/data's appleMain), so
// the two Apple platforms decode a photo with one implementation.
//
// Linux compiles it on every push. Linking it into a library needs Apple's tools, so only a Mac does that.
kotlin {
    macosArm64 {
        binaries.sharedLib {
            baseName = "watchesbridge"
        }
    }

    sourceSets {
        commonMain.dependencies {
            // Not exported: only the functions in Bridge.kt are the library's surface, and only what they
            // reach is linked into it.
            implementation(project(":shared:data"))
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.okio)
        }
    }
}
