//
// Copyright (c) 2023 ZettaScale Technology
//
// This program and the accompanying materials are made available under the
// terms of the Eclipse Public License 2.0 which is available at
// http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
// which is available at https://www.apache.org/licenses/LICENSE-2.0.
//
// SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
//
// Contributors:
//   ZettaScale Zenoh Team, <zenoh@zettascale.tech>
//

group = "io.zenoh"
version = "0.10.0-rc"

plugins {
    id("com.android.library")
    kotlin("multiplatform")
    id("com.adarshr.test-logger")
    id("org.jetbrains.dokka")
    id("org.mozilla.rust-android-gradle.rust-android")
    `maven-publish`
}

android {
    namespace = "io.zenoh"
    compileSdk = 30

    defaultConfig {
        minSdk = 30
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
        }
        getByName("debug") {
            isMinifyEnabled = false
        }
    }
    sourceSets {
        getByName("main") {
            manifest.srcFile("src/androidMain/AndroidManifest.xml")
        }
    }
    publishing {
        singleVariant("release") {
            withSourcesJar()
            withJavadocJar()
        }
    }
}

cargo {
    pythonCommand = "python3"
    module = "../zenoh-jni"
    libname = "zenoh-jni"
    targetIncludes = arrayOf("libzenoh_jni.so")
    targetDirectory = "../zenoh-jni/target/"
    profile = "release"
    targets = arrayListOf(
        "arm",
        "arm64",
        "x86",
        "x86_64",
    )
}

kotlin {
    jvmToolchain(11)
    jvm {
        compilations.all {
            kotlinOptions.jvmTarget = "11"
        }
        testRuns["test"].executionTask.configure {
            val zenohPaths = "../zenoh-jni/target/release:../zenoh-jni/target/debug"
            jvmArgs("-Djava.library.path=$zenohPaths")
        }
    }
    androidTarget {
        publishLibraryVariants("release")
    }

    @Suppress("Unused") sourceSets {
        val commonMain by getting {
            dependencies {
                implementation("commons-net:commons-net:3.9.0")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }
        val androidUnitTest by getting {
            dependencies {
                implementation(kotlin("test-junit"))
            }
        }
        val jvmMain by getting {
            resources.srcDir("../zenoh-jni/target/release").include(arrayListOf("*.dylib", "*.so", "*.dll"))
        }
    }
}

tasks.withType<Test> {
    // The Android tests. They run locally and therefore running this task is
    // equivalent to running the jvmTest tasks. The difference is that we need
    // to specify the path to the native library as a system property and not as
    // a jvmArg.

    doFirst {
        buildZenohJNI(BuildMode.DEBUG)
        systemProperty("java.library.path", "../zenoh-jni/target/debug")
    }
}

tasks.whenObjectAdded {
    if ((this.name == "mergeDebugJniLibFolders" || this.name == "mergeReleaseJniLibFolders")) {
        this.dependsOn("cargoBuild")
        this.inputs.dir(buildDir.resolve("rustJniLibs/android"))
    }
}

tasks.named("compileKotlinJvm") {
    doFirst {
        buildZenohJNI(BuildMode.RELEASE)
    }
}

tasks.create("cleanZenohJNI") {
    val result = project.exec {
        commandLine("cargo", "clean", "--manifest-path", "../zenoh-jni/Cargo.toml")
    }
    if (result.exitValue != 0) {
        throw GradleException("Failed to clean zenoh-jni.")
    }
}

//
//tasks.create("addDesktopRustTargets") {
//    doLast {
//        val rustTargets = listOf(
//            "x86_64-unknown-linux-gnu",
//            "aarch64-apple-darwin",
//            "x86_64-apple-darwin",
//            "x86_64-pc-windows-gnu",
//            "x86_64-pc-windows-msvc"
//        )
//
//        rustTargets.forEach { target -> addRustTarget(target) }
//    }
//}

fun addRustTarget(target: String) {
    val result = project.exec {
        commandLine("rustup", "target", "add", target)
    }

    if (result.exitValue != 0) {
        throw GradleException("Failed to add Rust target: $target")
    }
}

fun buildZenohJNI(mode: BuildMode = BuildMode.DEBUG, target: Target? = null) {
    val cargoCommand = mutableListOf("cargo", "build")

    if (mode == BuildMode.RELEASE) {
        cargoCommand.add("--release")
    }

    target?.let {
        cargoCommand.addAll(listOf("--target", it.toString()))
    }

    val result = project.exec {
        commandLine(*(cargoCommand.toTypedArray()), "--manifest-path", "../zenoh-jni/Cargo.toml")
    }

    if (result.exitValue != 0) {
        throw GradleException("Failed to build Zenoh-JNI with Rust target: $target")
    }
}

enum class Target {
    WINDOWS_X86_64_GNU {
        override fun toString(): String {
            return "x86_64-pc-windows-gnu"
        }
    },
    WINDOWS_X86_64_MSVC {
        override fun toString(): String {
            return "x86_64-pc-windows-msvc"
        }
    },
    LINUX_AARCH64_GNU {
        override fun toString(): String {
            return "aarch64-unknown-linux-gnu"
        }
    },
    LINUX_X86_64_GNU {
        override fun toString(): String {
            return "x86_64-unknown-linux-gnu"
        }
    },
    APPLE_AARCH64_DARWIN {
        override fun toString(): String {
            return "x86_64-apple-darwin"
        }
    },
    APPLE_X86_64_DARWIN {
        override fun toString(): String {
            return "aarch64-apple-darwin"
        }
    }
}

enum class BuildMode {
    DEBUG {
        override fun toString(): String {
            return "debug"
        }
    },
    RELEASE {
        override fun toString(): String {
            return "release"
        }
    }
}

fun findSystemTarget(): Target {
    val osName = System.getProperty("os.name").lowercase()
    val osArch = System.getProperty("os.arch").lowercase()

    val target = when {
        osName.contains("win") && osArch.contains("64") && osArch.contains("gnu") -> {
            Target.WINDOWS_X86_64_GNU
        }

        osName.contains("win") && osArch.contains("64") && osArch.contains("msvc") -> {
            Target.WINDOWS_X86_64_MSVC
        }

        osName.contains("linux") && osArch.contains("aarch64") -> {
            Target.LINUX_AARCH64_GNU
        }

        osName.contains("linux") && osArch.contains("x86") -> {
            Target.LINUX_X86_64_GNU
        }

        osName.contains("mac os x") && osArch.contains("x86") -> {
            Target.APPLE_X86_64_DARWIN
        }

        osName.contains("mac os x") && osArch.contains("aarch64") -> {
            Target.APPLE_AARCH64_DARWIN
        }

        else -> {
            throw GradleException("Couldn't find target for $osName $osArch")
        }
    }
    return target
}
