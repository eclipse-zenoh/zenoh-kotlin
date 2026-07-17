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

pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        google()
    }
}
rootProject.name = "zenoh-kotlin"

include(":zenoh-kotlin")
include(":examples")

// Include the local zenoh-java submodule only when explicitly requested via the
// zenoh.useLocalJniRuntime property (local dev/test only — not for publication).
val useLocalJniRuntime = settings.providers.gradleProperty("zenoh.useLocalJniRuntime")
    .orNull?.toBoolean() == true
if (useLocalJniRuntime) {
    require(file("zenoh-java/settings.gradle.kts").exists()) {
        "zenoh.useLocalJniRuntime=true was requested but the zenoh-java submodule is not initialized. " +
            "Run: git submodule update --init --recursive"
    }
    includeBuild("zenoh-java") {
        dependencySubstitution {
            substitute(module("org.eclipse.zenoh:zenoh-jni-runtime"))
                .using(project(":zenoh-jni-runtime"))
        }
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version("0.4.0")
}
