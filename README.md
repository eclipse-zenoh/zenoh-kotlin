<img src="https://raw.githubusercontent.com/eclipse-zenoh/zenoh/main/zenoh-dragon.png" height="150">

[![CI](https://github.com/eclipse-zenoh/zenoh-kotlin/workflows/CI/badge.svg)](https://github.com/eclipse-zenoh/zenoh-kotlin/actions?query=workflow%3A%22CI%22)
[![Release status](https://github.com/eclipse-zenoh/zenoh-kotlin/actions/workflows/release.yml/badge.svg)](https://github.com/eclipse-zenoh/zenoh-kotlin/actions/workflows/release.yml)
[![Discussion](https://img.shields.io/badge/discussion-on%20github-blue)](https://github.com/eclipse-zenoh/roadmap/discussions)
[![Discord](https://img.shields.io/badge/chat-on%20discord-blue)](https://discord.gg/2GJ958VuHs)
[![License](https://img.shields.io/badge/License-EPL%202.0-blue)](https://choosealicense.com/licenses/epl-2.0/)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)

# Eclipse Zenoh

The Eclipse Zenoh: Zero Overhead Pub/sub, Store/Query and Compute.

Zenoh (pronounce _/zeno/_) unifies data in motion, data at rest and computations. It carefully blends traditional pub/sub with geo-distributed storages, queries and computations, while retaining a level of time and space efficiency that is well beyond any of the mainstream stacks.

Check the website [zenoh.io](http://zenoh.io) and the [roadmap](https://github.com/eclipse-zenoh/roadmap) for more detailed information.

----

# <img src="kotlin-logo.png" alt="Kotlin" height="50">  Kotlin API

This repository provides a Kotlin binding based on the main [Zenoh implementation written in Rust](https://github.com/eclipse-zenoh/zenoh).

The code relies on a native library written in Rust, communicating with the Kotlin layer through the Java Native Interface (JNI). That library is not built in this repository: it is generated and published separately as [zenoh-flat-jni](https://github.com/eclipse-zenoh/zenoh-flat-jni) and consumed here as an ordinary Maven dependency.

## <img src="doc_icon.png" alt="Zenoh" height="70"> Documentation

The documentation of the API is published at <https://eclipse-zenoh.github.io/zenoh-kotlin/index.html>.

Alternatively, you can build it locally as [explained below](#building-the-documentation).

----

# How to import

## <img src="android-robot.png" alt="Android" height="50"> Android

First add the Maven central repository to your `settings.gradle.kts`:

```kotlin
dependencyResolutionManagement {
    // ...
    repositories {
        mavenCentral()
    }
}
```

After that add to the dependencies in the app's `build.gradle.kts`:

```kotlin
implementation("org.eclipse.zenoh:zenoh-kotlin-android:1.1.1")
```

### Platforms

The library targets the following platforms:

- x86
- x86_64
- arm
- arm64

### SDK

The minimum SDK is 30.

### Permissions

Zenoh is a communications protocol, therefore the permissions required are:

```xml
<uses-permission android:name="android.permission.INTERNET"/>
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE"/>
```

### Example

Checkout the [Zenoh demo app](https://github.com/eclipse-zenoh/zenoh-demos/tree/main/zenoh-android/ZenohApp) for an example on how to use the library.

----

## <img src="jvm.png" alt="Java" height="50">  JVM

First add the Maven central repository to your `settings.gradle.kts`:

```kotlin
dependencyResolutionManagement {
    // ...
    repositories {
        mavenCentral()
    }
}
```

After that add to the dependencies in the app's `build.gradle.kts`:

```kotlin
implementation("org.eclipse.zenoh:zenoh-kotlin:1.1.1")
```

### Platforms

For the moment, the library targets the following platforms:

- x86_64-unknown-linux-gnu
- aarch64-unknown-linux-gnu
- x86_64-apple-darwin
- aarch64-apple-darwin
- x86_64-pc-windows-msvc
- aarch64-pc-windows-msvc

----

# How to build it

## What you need

Basically:

- Kotlin ([Installation guide](https://kotlinlang.org/docs/getting-started.html#backend))
- Gradle ([Installation guide](https://gradle.org/install/))

and in case of targetting Android you'll also need:

- Android SDK ([Installation guide](https://developer.android.com/about/versions/11/setup-sdk))

> **Note:** zenoh-kotlin builds no native code, so no Rust toolchain and no NDK
> are needed. The generated JNI bindings and the native libraries come from
> [zenoh-flat-jni](https://github.com/eclipse-zenoh/zenoh-flat-jni), resolved as
> `org.eclipse.zenoh:zenoh-flat-jni` — a Kotlin Multiplatform library, so the JVM
> or Android variant is selected automatically. Building against its _source_
> instead does need a Rust toolchain — see below. For releases, see
> [PUBLISHING.md](PUBLISHING.md).

## Building against zenoh-flat-jni source

The default build resolves `zenoh-flat-jni` from Maven Central like any consumer,
and needs nothing from this section. A build can be pointed at its source instead,
through a Gradle composite build — for working on the bindings and this SDK
together, or for reproducing a CI run. `settings.gradle.kts` decides where that
source comes from, in this order:

| | says "use this source" | where it comes from |
| --- | --- | --- |
| 1 | `-PflatJniDir=<path>` | that directory, as it is |
| 2 | `path = "…"` in `Cargo.toml` | that directory, as it is |
| 3 | `-PuseLocalFlatJni=true` | the commit `Cargo.lock` pins, fetched into `.zenoh-flat-jni/` |
| 4 | nothing | Maven Central — no composite build at all |

Rows 1–3 build the native library from source, so they need a Rust toolchain
([rustup.rs](https://rustup.rs)); Gradle drives cargo for you. Row 4 — the
default — needs none.

Working against a checkout of your own is the ordinary Cargo edit, in
`Cargo.toml`:

```toml
# zenoh-flat-jni = { git = "https://github.com/eclipse-zenoh/zenoh-flat-jni.git", branch = "main" }
zenoh-flat-jni = { path = "../zenoh-flat-jni" }
```

and `./gradlew build` picks it up with no properties at all. `-PflatJniDir=…`
does the same without editing anything, for a checkout somewhere else.

Reproducing what CI tested is:

```bash
./gradlew jvmTest -PuseLocalFlatJni=true
```

which fetches the pinned commit into `.zenoh-flat-jni/` — that is the whole of
what CI does, so this one command reproduces a CI run anywhere. It fetches only
when that directory is not already at the pinned commit, and
`-PflatJniCommit=<sha>` tries a different commit without touching the lockfile.

### Where the pinned commit comes from

Rows 2 and 3 read a Rust crate at the repository root — `Cargo.toml`,
`ci/pin.rs`, `rust-toolchain.toml`, `Cargo.lock` — that compiles to nothing
anyone ships. Its only content is a dependency on `zenoh-flat-jni`, and its only
purpose is to make the commit this SDK is tested against a _resolved lockfile
entry_:

```toml
zenoh-flat-jni = { git = "https://github.com/eclipse-zenoh/zenoh-flat-jni.git", branch = "main" }
```

```text
Cargo.lock:  source = "git+https://github.com/eclipse-zenoh/zenoh-flat-jni.git?branch=main#<40-hex commit>"
```

A lockfile is the one pin `eclipse-zenoh/ci` already knows how to move. Its
lockfile sync overwrites a dependant's `Cargo.lock` with zenoh's, resolves the
manifest again — which writes back the current zenoh-flat-jni commit — compiles
the result, and opens an auto-merging pull request. This repository is an
ordinary dependant of that workflow, not a special case in it, which is why the
crate sits at the root rather than in a subdirectory.

The pin keeps a CI run reproducible from this repository's commit alone; the bot
keeps it from going stale.

Nothing here builds that crate. Running `cargo build` at the repository root
compiles zenoh and the bindings to produce an empty library — if an IDE offers to
load the root `Cargo.toml` as a Rust project, decline. Gradle only _reads_ these
two files; it never runs Cargo against them, so switching to `path = "…"` leaves
`Cargo.lock` untouched.

### Moving the pin

Normally you don't — the bot's pull request does. When you need to:

```bash
cargo update -p zenoh-flat-jni                 # to zenoh-flat-jni's main tip
cargo update -p zenoh-flat-jni --precise <sha> # to one specific commit
```

Commit the resulting `Cargo.lock`. Two things to avoid, because both defeat the
mechanism rather than steering it:

- **Do not add `rev = "…"` to `Cargo.toml`.** That freezes resolution at a
  commit, so the sync can no longer move the pin and the bot goes silent.
- **Do not commit the `path = "…"` form.** It is meant to be a local edit: the
  lockfile then pins no commit, CI says so and fails, and the lockfile sync
  cannot resolve `../zenoh-flat-jni` on a runner either. `git checkout Cargo.toml`
  when you are done — and `Cargo.lock` too, if you ran Cargo while it was set.

A release resolves the Maven artifact and must never use a composite build; see
[PUBLISHING.md](PUBLISHING.md#building-against-zenoh-flat-jni-source).

## <img src="jvm.png" alt="JVM" height="50"> JVM

To publish a library for a JVM project into Maven local, run

```bash
gradle publishJvmPublicationToMavenLocal
```

This publishes the zenoh-kotlin library to Maven local. The published artifact declares a dependency on `zenoh-flat-jni`, which provides the generated JNI bindings and the native binaries, and is released separately from the [zenoh-flat-jni](https://github.com/eclipse-zenoh/zenoh-flat-jni) repository.

Once we have published the package, we should be able to find it under `~/.m2/repository/org/eclipse/zenoh/zenoh-kotlin/1.1.1`.

Finally, in the `build.gradle.kts` file of the project where you intend to use this library, add mavenLocal to the list of repositories and add zenoh-kotlin as a dependency:

```kotlin
repositories {
    mavenCentral()
    mavenLocal()
}

dependencies {
    implementation("org.eclipse.zenoh:zenoh-kotlin:1.1.1")
}
```

## <img src="android-robot.png" alt="Android" height="50"> Android

In order to use these bindings in a native Android project, publish them into Maven local:

```bash
gradle -Pandroid=true publishAndroidReleasePublicationToMavenLocal
```

This publishes the zenoh-kotlin-android artifact to Maven local. It declares a dependency on `zenoh-flat-jni`, whose Android variant carries the prebuilt native libraries for all four ABIs, released separately from the [zenoh-flat-jni](https://github.com/eclipse-zenoh/zenoh-flat-jni) repository — no Rust toolchain and no NDK cross-compilation is required here.

You should now be able to see the package under `~/.m2/repository/org/eclipse/zenoh/zenoh-kotlin-android/1.1.1`.

Finally, in the `build.gradle.kts` file of the project where you intend to use this library, add mavenLocal to the list of repositories and add zenoh-kotlin-android as a dependency:

```kotlin
repositories {
    mavenCentral()
    mavenLocal()
}

dependencies {
    implementation("org.eclipse.zenoh:zenoh-kotlin-android:1.1.1")
}
```

Reminder that in order to work during runtime, the following permissions must be enabled in the app's manifest:

```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
```

## Building the documentation

Because it's a Kotlin project, we use [Dokka](https://kotlinlang.org/docs/dokka-introduction.html) to generate the documentation.

In order to build it, run:

```bash
gradle dokkaGenerate
```

## Running the tests

```bash
gradle jvmTest
```

By default this resolves `zenoh-flat-jni` from Maven Central, so no Rust
toolchain is involved. To run the tests against a sibling `../zenoh-flat-jni`
checkout instead — which is what CI does, and what you want when changing both
repositories together — add `-PuseLocalFlatJni=true`:

```bash
gradle jvmTest -PuseLocalFlatJni=true
```

That substitutes the artifact through a Gradle composite build and does compile
the native library from source, so it requires a Rust toolchain (see
[rustup.rs](https://rustup.rs)).

## Logging

Rust logs are propagated when setting the `RUST_LOG` environment variable.

For instance running the ZPub test as follows:

```bash
RUST_LOG=debug gradle ZPub
```

causes the logs to appear in standard output.

The log levels are the ones from Rust, typically `trace`, `info`, `debug`, `error` and `warn` (though other log filtering options are available, see <https://docs.rs/env_logger/latest/env_logger/#enabling-logging>).

Alternatively, the logs can be enabled programmatically through `Zenoh.initLogFromEnvOr(logfilter)`, for instance:

```kotlin
Zenoh.initLogFromEnvOr("debug")
```

----

# Examples

You can find some examples located under the [`/examples` folder](examples). Checkout the [examples README file](/examples/README.md).

----

# Old packages

Old released versions were published into Github packages.

In case you want to use one of the versions published into github packages, add the Github packages repository to your `settings.gradle.kts` as follows:

```kotlin
dependencyResolutionManagement {
    // ...
    repositories {
        google()
        mavenCentral()
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/eclipse-zenoh/zenoh-kotlin")
            credentials {
                username = providers.gradleProperty("user").get()
                password = providers.gradleProperty("token").get()
            }
        }
    }
}
```

where the username and token are your github username and a personal access token you need to generate on github with package read permissions (see the [Github documentation](https://docs.github.com/en/authentication/keeping-your-account-and-data-secure/managing-your-personal-access-tokens)).
This is required by Github in order to import the package, even if it's from a public repository.

Then after that, add the dependency as usual:

```kotlin
dependencies {
    implementation("org.eclipse.zenoh:zenoh-kotlin:<version>")
}
```
