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

import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    id("com.gradleup.shadow")
}

kotlin {
    jvmToolchain(11)
}

dependencies {
    implementation(project(":zenoh-kotlin"))
    implementation("commons-net:commons-net:3.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    implementation("com.github.ajalt.clikt:clikt:4.2.2")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.0")
}

tasks {
    val examples = listOf(
        "ZBytes",
        "ZDelete",
        "ZGet",
        "ZGetLiveliness",
        "ZInfo",
        "ZLiveliness",
        "ZPing",
        "ZPong",
        "ZPub",
        "ZPubThr",
        "ZPut",
        "ZQuerier",
        "ZQueryable",
        "ZScout",
        "ZSub",
        "ZSubLiveliness",
        "ZSubThr",
        "ZAdvancedPublisher",
        "ZAdvancedSubscriber"
    )

    examples.forEach { example ->
        register<ShadowJar>("${example}Jar") {
            group = "build"
            description = "Build a fat JAR for the $example example"
            from(sourceSets["main"].output)
            manifest {
                attributes["Main-Class"] = "io.zenoh.${example}Kt"
            }
            configurations.empty()
            configurations.add(project.configurations.getByName("runtimeClasspath"))

            archiveBaseName.set(example)
            archiveVersion.set("")
            archiveClassifier.set("")
        }
    }

    register("buildExamples") {
        group = "build"
        description = "Build all fat JARs for the Zenoh Kotlin examples"
        dependsOn(examples.map { "${it}Jar" })
    }

    examples.forEach { example ->
        register(example, JavaExec::class) {
            dependsOn("CompileZenohJNI")
            description = "Run the $example example"
            mainClass.set("io.zenoh.${example}Kt")
            classpath(sourceSets["main"].runtimeClasspath)
            val zenohPaths = "../zenoh-jni/target/release"
            val defaultJvmArgs = arrayListOf("-Djava.library.path=$zenohPaths")
            jvmArgs(defaultJvmArgs)
        }
    }
}

tasks.register("CompileZenohJNI") {
    project.exec {
        commandLine("cargo", "build", "--release", "--manifest-path", "../zenoh-jni/Cargo.toml")
    }
}
