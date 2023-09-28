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

    cargo {
        pythonCommand = "python3"
        module  = "../zenoh-jni"
        libname = "zenoh-jni"
        targetIncludes = arrayOf("libzenoh_jni.so")
        targetDirectory = "../zenoh-jni/target/"
        profile = "release"
        targets = arrayListOf("arm", "arm64", "x86", "x86_64")
    }
}

kotlin {
    jvmToolchain(11)
    jvm {
        compilations.all {
            kotlinOptions.jvmTarget = "11"
        }
        testRuns["test"].executionTask.configure {
            useJUnitPlatform()
            val zenohPaths = "/usr/local/lib:../zenoh-jni/target/release:../zenoh-jni/target/debug"
            jvmArgs("-Djava.library.path=$zenohPaths")
        }
    }

    androidTarget {
        publishLibraryVariants("release")
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation("commons-net:commons-net:3.9.0")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
            }
        }
        val jvmTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }
        val androidMain by getting {
            kotlin.srcDir("src/commonMain/kotlin")
        }
    }
}

tasks.whenObjectAdded {
    if ((this.name == "mergeDebugJniLibFolders" || this.name == "mergeReleaseJniLibFolders")) {
        this.dependsOn("cargoBuild")
        // fix mergeDebugJniLibFolders  UP-TO-DATE
        this.inputs.dir(buildDir.resolve("rustJniLibs/android"))
    }
}
