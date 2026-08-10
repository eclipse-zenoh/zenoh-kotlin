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
// A build can be pointed at its *source* instead, through a composite build.
// Where that source comes from is decided here, in this order:
//
//   1. -PflatJniDir=<path>       an existing checkout, used as it is
//   2. path = "…" in Cargo.toml  the ordinary Cargo way of saying "use my copy",
//                                and the only switch that lives in a tracked file
//   3. -PuseLocalFlatJni=true    the commit Cargo.lock pins - fetched into
//                                .zenoh-flat-jni/ unless it is already there, or
//                                overridden with -PflatJniCommit=<sha>
//   4. otherwise                 the Maven artifact, with no composite at all
//
// A release takes 4, and must: with a composite build the published artifact
// would carry whatever was on the builder's disk while the POM still claimed the
// released version.
val flatJniRepository = "https://github.com/eclipse-zenoh/zenoh-flat-jni.git"

fun git(dir: File, vararg args: String): String {
    val process = ProcessBuilder(listOf("git", *args))
        .directory(dir)
        .redirectErrorStream(true)
        .start()
    val output = process.inputStream.bufferedReader().readText().trim()
    check(process.waitFor() == 0) { "git ${args.joinToString(" ")} failed in $dir:\n$output" }
    return output
}

/** The pinned commit, checked out under [into]; fetched only when it is not already there. */
fun checkoutPinned(commit: String, into: File): File {
    if (File(into, ".git").isDirectory && git(into, "rev-parse", "HEAD") == commit) return into
    into.mkdirs()
    if (!File(into, ".git").isDirectory) git(into, "init", "--quiet")
    println("Fetching zenoh-flat-jni $commit into $into")
    git(into, "fetch", "--depth", "1", "--quiet", flatJniRepository, commit)
    git(into, "checkout", "--quiet", "--detach", "FETCH_HEAD")
    return into
}

/** The `path = "…"` of a zenoh-flat-jni dependency written as a single-line inline table. */
fun cargoTomlPath(): String? =
    File(settingsDir, "Cargo.toml").takeIf { it.isFile }?.readText()
        ?.let { Regex("""^\s*zenoh-flat-jni\s*=\s*\{[^}\n]*\bpath\s*=\s*"([^"]+)"""", RegexOption.MULTILINE).find(it) }
        ?.groupValues?.get(1)

/** The commit Cargo.lock pins for the root pin crate; null if a path override erased it. */
fun cargoLockCommit(): String? =
    File(settingsDir, "Cargo.lock").takeIf { it.isFile }?.readText()
        ?.let { Regex("""/zenoh-flat-jni\.git[^#"]*#([0-9a-f]{40})"""").find(it) }
        ?.groupValues?.get(1)

val flatJniSource: File? =
    providers.gradleProperty("flatJniDir").orNull?.let { File(settingsDir, it) }
        ?: cargoTomlPath()?.let { File(settingsDir, it) }
        ?: if (providers.gradleProperty("useLocalFlatJni").orNull?.toBoolean() == true) {
            val commit = providers.gradleProperty("flatJniCommit").orNull ?: cargoLockCommit()
            checkNotNull(commit) {
                "Cargo.lock pins no zenoh-flat-jni commit. A path override or [patch] erases it - see PUBLISHING.md."
            }
            checkoutPinned(commit, File(settingsDir, ".zenoh-flat-jni"))
        } else null

if (flatJniSource != null) {
    check(File(flatJniSource, "settings.gradle.kts").isFile) {
        "$flatJniSource is not a zenoh-flat-jni checkout"
    }
    includeBuild(flatJniSource)
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version("0.4.0")
}
