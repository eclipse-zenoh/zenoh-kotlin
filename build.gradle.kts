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

buildscript {
    repositories {
        google()
    }
    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:1.9.0")
        classpath("org.mozilla.rust-android-gradle:plugin:0.9.6")
        classpath("com.android.tools.build:gradle:7.4.2")
        classpath("com.gradleup.shadow:shadow-gradle-plugin:9.0.0-beta6")
    }
}

plugins {
    id("com.android.library") version "7.4.2" apply false
    id("org.jetbrains.kotlin.android") version "1.9.10" apply false
    id("org.jetbrains.kotlin.multiplatform") version "1.9.0" apply false
    id("org.mozilla.rust-android-gradle.rust-android") version "0.9.6" apply false
    id("org.jetbrains.dokka") version "2.0.0" apply false
    id("com.adarshr.test-logger") version "3.2.0" apply false
    kotlin("plugin.serialization") version "1.9.0" apply false
    id("io.github.gradle-nexus.publish-plugin") version "2.0.0"
}

group = "org.eclipse.zenoh"

val baseVersion = file("version.txt").readText().trim()
version = if (project.hasProperty("SNAPSHOT")) {
    "$baseVersion-SNAPSHOT"
} else {
    baseVersion
}

nexusPublishing {
    repositories {
        sonatype {
            nexusUrl = uri("https://central.sonatype.org/service/local/")
            snapshotRepositoryUrl = uri("https://central.sonatype.org/content/repositories/snapshots/")

            username = System.getenv("ORG_OSSRH_USERNAME")
            password = System.getenv("ORG_OSSRH_PASSWORD")
        }
    }
}

subprojects {
    repositories {
        google()
        mavenCentral()
    }
}
