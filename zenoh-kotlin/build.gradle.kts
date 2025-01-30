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

import com.nishtahir.CargoExtension

plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
    id("com.adarshr.test-logger")
    id("org.jetbrains.dokka")
    `maven-publish`
    signing
}

val androidEnabled = project.findProperty("android")?.toString()?.toBoolean() == true
val release = project.findProperty("release")?.toString()?.toBoolean() == true

// If the publication is meant to be done on a remote repository (Maven central).
// Modifying this property will affect the release workflows!
val isRemotePublication = project.findProperty("remotePublication")?.toString()?.toBoolean() == true

var buildMode = if (release) BuildMode.RELEASE else BuildMode.DEBUG

if (androidEnabled) {
    apply(plugin = "com.android.library")
    apply(plugin = "org.mozilla.rust-android-gradle.rust-android")

    configureCargo()
    configureAndroid()
}

kotlin {
    jvmToolchain(11)
    jvm {
        compilations.all {
            kotlinOptions.jvmTarget = "11"
        }
    }
    if (androidEnabled) {
        androidTarget {
            publishLibraryVariants("release")
        }
    }

    @Suppress("Unused")
    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation("commons-net:commons-net:3.9.0")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.0")
                implementation("org.jetbrains.kotlin:kotlin-reflect")
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation("org.junit.jupiter:junit-jupiter-api:5.10.0")
                runtimeOnly("org.junit.jupiter:junit-jupiter-engine:5.10.0")
                implementation("org.junit.jupiter:junit-jupiter-params:5.10.0")
            }
        }
        if (androidEnabled) {
            val androidUnitTest by getting {
                dependencies {
                    implementation(kotlin("test-junit"))
                }
            }
        }
        val jvmMain by getting {
            if (isRemotePublication) {
                // The line below is intended to load the native libraries that are crosscompiled on GitHub actions when publishing a JVM package.
                resources.srcDir("../jni-libs").include("*/**")
            } else {
                resources.srcDir("../zenoh-jni/target/$buildMode").include(arrayListOf("*.dylib", "*.so", "*.dll"))
            }
        }

        val jvmTest by getting {
            resources.srcDir("../zenoh-jni/target/$buildMode").include(arrayListOf("*.dylib", "*.so", "*.dll"))
        }
    }

    val javadocJar by tasks.registering(Jar::class) {
        dependsOn("dokkaGeneratePublicationHtml")
        archiveClassifier.set("javadoc")
        from("${buildDir}/dokka/html")
    }

    publishing {
        publications.withType<MavenPublication> {
            groupId = "org.eclipse.zenoh"
            artifactId = "zenoh-kotlin"
            version = rootProject.version.toString()

            artifact(javadocJar)

            pom {
                name.set("Zenoh Kotlin")
                description.set("The Eclipse Zenoh: Zero Overhead Pub/sub, Store/Query and Compute.")
                url.set("https://zenoh.io/")

                licenses {
                    license {
                        name.set("Eclipse Public License 2.0 OR Apache License 2.0")
                        url.set("http://www.eclipse.org/legal/epl-2.0")
                    }
                }
                developers {
                    developer {
                        id.set("ZettaScale")
                        name.set("ZettaScale Zenoh Team")
                        email.set("zenoh@zettascale.tech")
                    }
                    developer {
                        id.set("DariusIMP")
                        name.set("Darius Maitia")
                        email.set("darius@zettascale.tech")
                    }
                    developer {
                        id.set("Mallets")
                        name.set("Luca Cominardi")
                        email.set("luca@zettascale.tech")
                    }
                    developer {
                        id.set("Kydos")
                        name.set("Angelo Corsaro")
                        email.set("angelo@zettascale.tech")
                    }
                    developer {
                        id.set("Wyfo")
                        name.set("Joseph Perez")
                        email.set("joseph.perez@zettascale.tech")
                    }
                }
                scm {
                    connection.set("scm:git:https://github.com/eclipse-zenoh/zenoh-kotlin.git")
                    developerConnection.set("scm:git:https://github.com/eclipse-zenoh/zenoh-kotlin.git")
                    url.set("https://github.com/eclipse-zenoh/zenoh-kotlin")
                }
            }
        }
    }
}

signing {
    isRequired = isRemotePublication
    useInMemoryPgpKeys(System.getenv("ORG_GPG_SUBKEY_ID"), System.getenv("ORG_GPG_PRIVATE_KEY"), System.getenv("ORG_GPG_PASSPHRASE"))
    sign(publishing.publications)
}

tasks.withType<PublishToMavenRepository>().configureEach {
    dependsOn(tasks.withType<Sign>())
}

tasks.withType<Test> {
    doFirst {
        // The line below is added for the Android Unit tests which are equivalent to the JVM tests.
        // For them to work we need to specify the path to the native library as a system property and not as a jvmArg.
        systemProperty("java.library.path", "../zenoh-jni/target/$buildMode")
    }
}

tasks.whenObjectAdded {
    if ((this.name == "mergeDebugJniLibFolders" || this.name == "mergeReleaseJniLibFolders")) {
        this.dependsOn("cargoBuild")
    }
}

tasks.named("compileKotlinJvm") {
    dependsOn("buildZenohJni")
}

tasks.register("buildZenohJni") {
    doLast {
        if (!isRemotePublication) {
            // This is intended for local publications. For publications done through GitHub workflows,
            // the zenoh-jni build is achieved and loaded differently from the CI
            buildZenohJNI(buildMode)
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

    Thread.sleep(1000)
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

fun Project.configureAndroid() {
    extensions.configure<com.android.build.gradle.LibraryExtension>("android") {
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
}

fun Project.configureCargo() {
    extensions.configure<CargoExtension>("cargo") {
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
}
