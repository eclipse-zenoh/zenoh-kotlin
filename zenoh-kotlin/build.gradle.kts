import com.android.build.gradle.internal.scope.ProjectInfo.Companion.getBaseName
import org.gradle.internal.classpath.ClassPath

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
    kotlin("multiplatform")
    id("com.adarshr.test-logger") version "3.2.0"
    id("com.android.library")
    id("org.mozilla.rust-android-gradle.rust-android")
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

    sourceSets {
        getByName("main") {
            manifest.srcFile("src/androidMain/AndroidManifest.xml")
        }
    }
}

cargo {
    pythonCommand = "python3"
    module  = "../zenoh-jni"
    libname = "zenoh-jni"
    targets = arrayListOf("arm64")
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
    androidTarget()

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

tasks.withType<com.android.build.gradle.tasks.PackageApplication> {
    dependsOn("cargoBuild")
}
