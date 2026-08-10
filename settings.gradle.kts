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

// zenoh-flat-jni (https://github.com/eclipse-zenoh/zenoh-flat-jni) is consumed as
// an ordinary Maven artifact: org.eclipse.zenoh:zenoh-flat-jni:$zenohFlatJniVersion.
//
// For coordinated local development, `-PuseLocalFlatJni=true` substitutes a
// sibling checkout through a composite build. It is off by default and must stay
// off for a release: with it on, the published artifact would be built against
// whatever is on that developer's disk rather than the resolved dependency.
if (providers.gradleProperty("useLocalFlatJni").orNull?.toBoolean() == true) {
    includeBuild("../zenoh-flat-jni")
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version("0.4.0")
}
