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
    kotlin("multiplatform")
    kotlin("plugin.serialization")
    id("com.adarshr.test-logger")
    id("org.jetbrains.dokka")
    `maven-publish`
    signing
}

val androidEnabled = project.findProperty("android")?.toString()?.toBoolean() == true

// If the publication is meant to be done on a remote repository (Maven central).
// Modifying this property will affect the release workflows!
val isRemotePublication = project.findProperty("remotePublication")?.toString()?.toBoolean() == true

if (androidEnabled) {
    apply(plugin = "com.android.library")
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
                implementation("org.eclipse.zenoh:zenoh-jni-runtime:${property("zenohJniRuntimeVersion")}")
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
        // jvmAndAndroidMain is an intermediate source set between commonMain and both jvmMain/androidMain.
        // It holds code that uses kotlin-reflect — available on JVM and Android (ART),
        // but absent on Kotlin/Native and Kotlin/JS targets.
        val jvmAndAndroidMain by creating { dependsOn(commonMain) }
        val jvmMain by getting {
            dependsOn(jvmAndAndroidMain)
        }
        if (androidEnabled) {
            val androidMain by getting { dependsOn(jvmAndAndroidMain) }
            val androidUnitTest by getting {
                dependencies {
                    implementation(kotlin("test-junit"))
                }
            }
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
