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

## Where the native library comes from

Three ways to build. Pick by what you are doing:

| I want to… | build with | Rust needed |
| --- | --- | --- |
| just build or use the SDK | `./gradlew build` | no |
| build the bindings from source too | `./gradlew build -PuseLocalJni=true` | yes |
| build against my own checkout | `./gradlew build -PlocalJniDir=../zenoh-flat-jni` | yes |

**The default** downloads `org.eclipse.zenoh:zenoh-flat-jni` with the native
library already inside it. Nothing is compiled from Rust and no toolchain is
needed. On `main` that is `1.9.0-kotlin-SNAPSHOT`, published from this repository
alongside the SDK snapshot ([CI.md](CI.md#what-publishing-uses)); a release
names a zenoh-flat-jni release on Maven Central. Either way it is one coordinate
in `gradle.properties`, and if it has not been published yet the other two rows
build without it.

**`-PuseLocalJni=true`** builds the bindings from source, as `Cargo.toml`
says — the usual Rust arrangement, and the one CI uses. A `git` dependency there
means the exact commit recorded in `Cargo.lock` (resolved on the spot if there is
no lockfile yet); a `path` means that directory. So

```bash
./gradlew jvmTest -PuseLocalJni=true
```

reproduces a CI run exactly. See [CI.md](CI.md) for how that commit is chosen and
kept current.

**`-PlocalJniDir=<path>`** points straight at a checkout, no `Cargo.toml` involved.
Use it to try a branch or a scratch copy without editing anything.

Both source options need a Rust toolchain ([rustup.rs](https://rustup.rs));
Gradle drives cargo for you. To work further down the stack — on `zenoh-flat` or
`zenoh` themselves — edit inside your `zenoh-flat-jni` checkout, whose own
`Cargo.toml` points at them the same way.

Releases always use the first option; see [PUBLISHING.md](PUBLISHING.md).

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
toolchain is involved. The two source options from
[Where the native library comes from](#where-the-native-library-comes-from)
apply here as well, and both compile the native library, so both need a Rust
toolchain (see [rustup.rs](https://rustup.rs)):

```bash
gradle jvmTest -PuseLocalJni=true                    # the pinned commit — what CI runs
gradle jvmTest -PlocalJniDir=../zenoh-flat-jni       # your own checkout
```

Use the second when you are changing both repositories together — the first
tests the commit `Cargo.lock` pins, not your working tree.

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
