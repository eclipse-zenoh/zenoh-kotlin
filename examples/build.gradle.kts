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

plugins {
    kotlin("jvm")
}

kotlin {
    jvmToolchain(11)
}

dependencies {
    implementation(project(":zenoh-kotlin"))
    implementation("commons-net:commons-net:3.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
}

tasks {
    val examples = listOf(
        "ZDelete",
        "ZGet",
        "ZPub",
        "ZPubThr",
        "ZPut",
        "ZQueryable",
        "ZSub",
        "ZSubThr"
    )

    examples.forEach { example ->
        register(example, JavaExec::class) {
            dependsOn("CompileZenohJNI")
            description = "Run the $example example"
            mainClass.set("io.zenoh.${example}Kt")
            classpath(sourceSets["main"].runtimeClasspath)
            val zenohPaths = "../zenoh-jni/target/release"
            val defaultJvmArgs = arrayListOf("-Djava.library.path=$zenohPaths")
            val loggerLvl = project.findProperty("zenoh.logger")?.toString()
            if (loggerLvl != null) {
                jvmArgs(defaultJvmArgs + "-Dzenoh.logger=$loggerLvl")
            } else {
                jvmArgs(defaultJvmArgs)
            }
        }
    }
}

tasks.register("CompileZenohJNI") {
    project.exec {
        commandLine("cargo", "build", "--release", "--manifest-path", "../zenoh-jni/Cargo.toml")
    }
}
