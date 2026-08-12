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
        classpath("com.android.tools.build:gradle:7.4.2")
        classpath("com.gradleup.shadow:shadow-gradle-plugin:9.0.0-beta6")
    }
}

plugins {
    id("com.android.library") version "7.4.2" apply false
    id("org.jetbrains.kotlin.android") version "1.9.10" apply false
    id("org.jetbrains.kotlin.multiplatform") version "1.9.0" apply false
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
            nexusUrl = uri("https://ossrh-staging-api.central.sonatype.com/service/local/")
            snapshotRepositoryUrl = uri("https://central.sonatype.com/repository/maven-snapshots/")

            username = System.getenv("CENTRAL_SONATYPE_TOKEN_USERNAME")
            password = System.getenv("CENTRAL_SONATYPE_TOKEN_PASSWORD")
        }
    }
}

// A composite build must never reach a publication: the artifact would be built
// from zenoh-flat-jni source on the builder's disk, while the POM went on
// naming the released version it was supposed to be built against. Nothing opts
// in by default, so this only catches a leftover - `-PuseLocalJni`,
// `-PlocalJniDir`, or a `path = "..."` left in Cargo.toml.
gradle.taskGraph.whenReady {
    val included = gradle.includedBuilds.map { it.name }
    check(included.isEmpty() || allTasks.none { it.name.startsWith("publish") }) {
        "Refusing to publish while zenoh-flat-jni is an included build $included. " +
            "See README.md, Where the native library comes from."
    }
}

val zenohFlatJniVersion: String by project

subprojects {
    repositories {
        google()
        mavenCentral()

        // Between releases this SDK builds against a snapshot: our own copy of
        // zenoh-flat-jni, `1.9.0-kotlin-SNAPSHOT`, published by the same job
        // that publishes this SDK's snapshot (see CI.md). A rehearsal can name
        // another one. Reachable only when the version asked for is a snapshot,
        // so a release — whose version never ends in -SNAPSHOT — cannot resolve
        // a mutable artifact by accident.
        //
        // includeGroup, not includeModule: the dependency names the root
        // coordinate, but what Gradle downloads is zenoh-flat-jni-jvm or
        // zenoh-flat-jni-android. Filtering to one module would hide those.
        if (zenohFlatJniVersion.endsWith("-SNAPSHOT")) {
            maven {
                name = "centralSnapshots"
                url = uri("https://central.sonatype.com/repository/maven-snapshots/")
                content { includeGroup("org.eclipse.zenoh") }
            }
        }
    }
}
