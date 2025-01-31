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

The code relies on the Zenoh JNI native library, which written in Rust and communicates with the Kotlin layer via the Java Native Interface (JNI).

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
implementation("org.eclipse.zenoh:zenoh-kotlin-jvm:1.1.1")
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

- Rust ([Installation guide](https://doc.rust-lang.org/cargo/getting-started/installation.html))
- Kotlin ([Installation guide](https://kotlinlang.org/docs/getting-started.html#backend))
- Gradle ([Installation guide](https://gradle.org/install/))

and in case of targetting Android you'll also need:

- Android SDK ([Installation guide](https://developer.android.com/about/versions/11/setup-sdk))

## <img src="jvm.png" alt="JVM" height="50"> JVM

To publish a library for a JVM project into Maven local, run

```bash
gradle publishJvmPublicationToMavenLocal
```

This will first, trigger the compilation of Zenoh-JNI, and second publish the library into maven local, containing the native library
as a resource that will be loaded during runtime.

:warning: The native library will be compiled against the default rustup target on your machine, so although it may work fine
for you on your desktop, the generated publication may not be working on another computer with a different operating system and/or a different cpu architecture.

Once we have published the package, we should be able to find it under `~/.m2/repository/org/eclipse/zenoh/zenoh-kotlin-jvm/1.1.1`.

Finally, in the `build.gradle.kts` file of the project where you intend to use this library, add mavenLocal to the list of repositories and add zenoh-kotlin as a dependency:

```kotlin
repositories {
    mavenCentral()
    mavenLocal()
}

dependencies {
    implementation("org.eclipse.zenoh:zenoh-kotlin-jvm:1.1.1")
}
```

## <img src="android-robot.png" alt="Android" height="50"> Android

In order to use these bindings in a native Android project, what we will do is to build them as an Android NDK Library,
publishing it into Maven local for us to be able to easily import it in our project.

It is required to have the [NDK (native development kit)](https://developer.android.com/ndk) installed, since we are going to compile Zenoh JNI for multiple
android native targets. The currently used NDK version is **26.0.10792818**.
It can be set up by using Android Studio (go to `Preferences > Languages & Frameworks > Android SDK > SDK Tools`, tick `Show Package Details` and pick the right NDK version),
or alternatively it can be found [here](https://developer.android.com/ndk/downloads).

The native platforms we are going to target are the following ones:

- x86
- x86_64
- arm
- arm64

Therefore, if they are not yet already added to the Rust toolchain, run:

```bash
rustup target add armv7-linux-androideabi; \
rustup target add i686-linux-android; \
rustup target add aarch64-linux-android; \
rustup target add x86_64-linux-android
```

to install them.

So, in order to publish the library onto Maven Local, run:

```bash
gradle -Pandroid=true publishAndroidReleasePublicationToMavenLocal
```

This will first trigger the compilation of the Zenoh-JNI for the previously mentioned targets, and secondly will
publish the library, containing the native binaries.

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

To run the tests, run:

```bash
gradle jvmTest
```

This will compile the native library on debug mode (if not already available) and run the tests afterward against the JVM target.

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
    implementation("org.eclipse.zenoh:zenoh-kotlin-jvm:<version>")
}
```
