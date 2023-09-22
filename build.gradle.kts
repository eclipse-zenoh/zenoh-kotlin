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
        classpath("org.mozilla.rust-android-gradle:plugin:0.9.3")
        classpath("com.android.tools.build:gradle:7.4.2")
    }
}

plugins {
    id("org.jetbrains.dokka") version "1.8.20"
    id("com.android.library") version "7.4.2" apply false
    id("org.jetbrains.kotlin.android") version "1.9.10" apply false
    id("org.mozilla.rust-android-gradle.rust-android") version "0.9.3"
}

subprojects {
    apply(plugin = "org.jetbrains.dokka")

    repositories {
        google()
        mavenCentral()
    }
}
