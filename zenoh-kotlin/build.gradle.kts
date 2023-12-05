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
version = "0.11.0-dev"

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

    ndkVersion = "26.0.10792818"

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
            val zenohPaths = "../zenoh-jni/target/release"
            jvmArgs("-Djava.library.path=$zenohPaths")
        }
    }
    androidTarget {
        publishLibraryVariants("release")
    }

    @Suppress("Unused")
    sourceSets {
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
            resources.srcDir("../zenoh-jni/target")
            for (target in Target.values()) {
                resources.include(arrayListOf("/$target/release/*.dylib", "/$target/release/*.so", "/$target/release/*.dll"))
            }
        }
        val jvmTest by getting {
            resources.srcDir("../zenoh-jni/target/release").include(arrayListOf("*.dylib", "*.so", "*.dll"))
        }
    }

    publishing {
        repositories {
            maven {
                name = "GithubPackages"
                url = uri("https://maven.pkg.github.com/DariusIMP/zenoh-kotlin")
                credentials {
                    username = System.getenv("GITHUB_ACTOR")
                    password = System.getenv("GITHUB_TOKEN")
                }
            }
        }
    }
}

tasks.withType<Test> {
    doFirst {
        buildZenohJNI(BuildMode.RELEASE)

        // The line below is added for the Android Unit tests which are equivalent to the JVM tests.
        // For them to work we need to specify the path to the native library as a system property and not as a jvmArg.
        systemProperty("java.library.path", "../zenoh-jni/target/release")
    }
}

tasks.whenObjectAdded {
    if ((this.name == "mergeDebugJniLibFolders" || this.name == "mergeReleaseJniLibFolders")) {
        this.dependsOn("cargoBuild")
        this.inputs.dir(buildDir.resolve("rustJniLibs/android"))
    }
}

//tasks.named("compileKotlinJvm") {
//    doFirst {
//        buildZenohJNI(BuildMode.RELEASE)
//    }
//}

tasks.named("publishJvmPublicationToMavenLocal") {
    doFirst {
        for (target in Target.values()) {
            crossCompileZenohJNI(BuildMode.RELEASE, target)
        }
    }
}

tasks.named("publishJvmPublicationToGithubPackagesRepository") {
    doFirst {
        for (target in Target.values()) {
            crossCompileZenohJNI(BuildMode.RELEASE, target)
        }
    }
}

fun buildZenohJNI(mode: BuildMode = BuildMode.DEBUG) {
    val cargoCommand = mutableListOf("cargo", "build")

    if (mode == BuildMode.RELEASE) {
        cargoCommand.add("--release")
    }

    val result = project.exec {
        commandLine(*(cargoCommand.toTypedArray()), "--manifest-path", "../zenoh-jni/Cargo.toml")
    }

    if (result.exitValue != 0) {
        throw GradleException("Failed to build Zenoh-JNI.")
    }
}

fun crossCompileZenohJNI(mode: BuildMode = BuildMode.DEBUG, target: Target) {

    val cargoCommand = when (target) {
        Target.WINDOWS_X86_64_MSVC -> {
            mutableListOf("cargo", "xwin", "build")
        }
        else -> {
            mutableListOf("cargo", "zigbuild")
        }
    }

    if (mode == BuildMode.RELEASE) {
        cargoCommand.add("--release")
    }

    val result = project.exec {
//        if (target == Target.LINUX_AARCH64_MUSL || target == Target.LINUX_X86_64_MUSL) {
//            environment("RUSTFLAGS", "-Ctarget-feature=-crt-static")
//        }
        commandLine(*(cargoCommand.toTypedArray()),"--target", target.toString(), "--manifest-path", "../zenoh-jni/Cargo.toml")
    }

    if (result.exitValue != 0) {
        throw GradleException("Failed to build Zenoh-JNI.")
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

enum class Target {
//    WINDOWS_X86_64_GNU,
//    LINUX_AARCH64_MUSL,
//    LINUX_X86_64_MUSL,
    WINDOWS_X86_64_MSVC,
    LINUX_X86_64,
    LINUX_AARCH64,
    APPLE_AARCH64,
    APPLE_X86_64;

    override fun toString(): String {
        return when (this) {
//            WINDOWS_X86_64_GNU -> "x86_64-pc-windows-gnu"
//            LINUX_AARCH64_MUSL -> "aarch64-unknown-linux-musl"
//            LINUX_X86_64_MUSL -> "x86_64-unknown-linux-musl"
            WINDOWS_X86_64_MSVC -> "x86_64-pc-windows-msvc"
            LINUX_X86_64 -> "x86_64-unknown-linux-gnu"
            LINUX_AARCH64 -> "aarch64-unknown-linux-gnu"
            APPLE_AARCH64 -> "aarch64-apple-darwin"
            APPLE_X86_64 -> "x86_64-apple-darwin"
        }
    }
}
