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
// Three ways to build, and nothing else to know:
//
//   nothing                the Maven artifact, no composite build, no Rust
//   -PuseLocalJni=true whatever Cargo.toml says: `git` means the commit
//                          Cargo.lock pins (resolved with Cargo if there is no
//                          lockfile yet), `path` means that directory
//   -PlocalJniDir=<path>    that directory, skipping Cargo.toml altogether
//
// A release takes the first, and must: with a composite build the published
// artifact would carry whatever was on the builder's disk while the POM still
// claimed the released version.
//
// A `path = "…"` in Cargo.toml is honoured with or without the property - it is
// a deliberate local edit, and Cargo would honour it too.
val flatJniRepository = "https://github.com/eclipse-zenoh/zenoh-flat-jni.git"

fun run(command: String, dir: File, vararg args: String): String {
    val process = ProcessBuilder(listOf(command, *args))
        .directory(dir)
        .redirectErrorStream(true)
        .start()
    val output = process.inputStream.bufferedReader().readText().trim()
    check(process.waitFor() == 0) { "$command ${args.joinToString(" ")} failed in $dir:\n$output" }
    return output
}

fun git(dir: File, vararg args: String): String = run("git", dir, *args)

/** A commit that `git fetch <url> <sha>` can actually ask for: a full 40-hex hash. */
fun fullCommit(value: String): String = value.lowercase().also {
    check(it.matches(Regex("[0-9a-f]{40}"))) {
        "-PlocalJniCommit must be a full 40-character commit hash, not \"$value\": " +
            "a remote cannot be asked to fetch an abbreviation."
    }
}

/** The pinned commit, checked out under [into]; fetched only when it is not already there. */
fun checkoutPinned(commit: String, into: File): File {
    // rev-parse fails on a directory that was initialised but never fetched: that
    // means "not there yet", not "give up".
    val head = runCatching { git(into, "rev-parse", "HEAD") }.getOrNull()
    if (head == commit) return into
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

/** The commit Cargo.lock pins for the root pin crate; null if there is no lockfile or no entry. */
fun cargoLockCommit(): String? =
    File(settingsDir, "Cargo.lock").takeIf { it.isFile }?.readText()
        ?.let { Regex("""/zenoh-flat-jni\.git[^#"]*#([0-9a-f]{40})"""").find(it) }
        ?.groupValues?.get(1)

/** The commit the git dependency in Cargo.toml resolves to, resolving it first if need be. */
fun pinnedCommit(): String {
    cargoLockCommit()?.let { return it }
    // No lockfile, or none that mentions zenoh-flat-jni: let Cargo write one. That
    // is the same resolution a `cargo build` here would do, and it needs the
    // network but not a compiler.
    println("No zenoh-flat-jni commit in Cargo.lock; resolving it with Cargo")
    run("cargo", settingsDir, "generate-lockfile")
    return checkNotNull(cargoLockCommit()) {
        "Cargo.toml declares no git dependency on zenoh-flat-jni to resolve - see CI.md."
    }
}

val flatJniSource: File? =
    providers.gradleProperty("localJniDir").orNull?.let { settingsDir.resolve(it) }
        ?: cargoTomlPath()?.let { settingsDir.resolve(it) }
        ?: if (providers.gradleProperty("useLocalJni").orNull?.toBoolean() == true) {
            val commit = providers.gradleProperty("localJniCommit").orNull?.let { fullCommit(it) }
                ?: pinnedCommit()
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
