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

## Documentation

The documentation of the API is published at https://eclipse-zenoh.github.io/zenoh-kotlin/index.html.

Alternatively, you can build it locally as [explained below](#building-the-documentation).

----
# How to import

:warning: Note nº1: **Breaking changes incoming** 

We are currently releasing a series of alpha versions, the latests one being currently the 
[1.0.0-alpha.6](https://github.com/eclipse-zenoh/zenoh-kotlin/releases/tag/1.0.0-alpha.6). 
We are currently working to provide an upgraded API across all the languages supported by  the Zenoh ecosystem.
This last alpha release still offers an 'old-fashioned' API, although it's fully
compatible with the other 1.0.0 alpha releases of Zenoh. You must take into consideration when importing this library
that the API is about to be modified with some considerable breaking changes. A migration guide will be provided when
the time comes.

:warning: Note nº2: **Using the `dev/1.0.0` branch**

The `main` branch contains the codebase for the `0.11.0` version of Zenoh-Kotlin,
compatible with the same version of Zenoh. We are currently working with the `dev/1.0.0` branch. So in case you want 
to build the project with the latest changes being implemented, switch to that branch!

:warning: Note nº3: **About the released packages**

The released packages can be found in the "packages" section of this repository, or in the links below:
* [Android packages](https://github.com/eclipse-zenoh/zenoh-kotlin/packages/1968034/versions)
* [JVM packages](https://github.com/eclipse-zenoh/zenoh-kotlin/packages/2016733/versions)

These are 'nightly' packages that need to be imported as explained in the following sections of this README.
We plan to publish our packages into Maven central for the `1.0.0` stable release.



## <img src="android-robot.png" alt="Android" height="50"> Android

For this first version we have published a [Github package](https://github.com/eclipse-zenoh/zenoh-kotlin/packages/1968034) with the library which can be imported on your projects.

Checkout the [Zenoh demo app](https://github.com/eclipse-zenoh/zenoh-demos/tree/main/zenoh-android/ZenohApp) for an example on how to use the library.

First add the Github packages repository to your `settings.gradle.kts`:

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

After that add to the dependencies in the app's `build.gradle.kts`:

```kotlin
implementation("io.zenoh:zenoh-kotlin-android:0.11.0")
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

---
# <img src="jvm.png" alt="Java" height="50">  JVM

Similar to Android, we have published a Github package to import on your projects.

First add the Github packages repository to your `settings.gradle.kts`:

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

After that add to the dependencies in the app's `build.gradle.kts`:

```kotlin
implementation("io.zenoh:zenoh-kotlin-jvm:0.11.0")
```

### Platforms

For the moment, the library targets the following platforms:

    - x86_64-unknown-linux-gnu
    - aarch64-unknown-linux-gnu
    - x86_64-apple-darwin
    - aarch64-apple-darwin
    - x86_64-pc-windows-msvc


---

# How to build it

## What you need

Basically:
* Rust ([Installation guide](https://doc.rust-lang.org/cargo/getting-started/installation.html))
* Kotlin ([Installation guide](https://kotlinlang.org/docs/getting-started.html#backend))
* Gradle ([Installation guide](https://gradle.org/install/))

and in case of targetting Android you'll also need:
* Android SDK ([Installation guide](https://developer.android.com/about/versions/11/setup-sdk))

## <img src="jvm.png" alt="JVM" height="50"> JVM

To publish a library for a JVM project into Maven local, run

```bash
gradle -Prelease=true publishJvmPublicationToMavenLocal
```

This will first, trigger the compilation of Zenoh-JNI in release (if you want debug, specify `-Prelease=false`), and second publish the library into maven local, containing the native library
as a resource that will be loaded during runtime.

:warning: The native library will be compiled against the default rustup target on your machine, so although it may work fine
for you on your desktop, the generated publication may not be working on another computer with a different operating system and/or a different cpu architecture.

Once we have published the package, we should be able to find it under `~/.m2/repository/io/zenoh/zenoh-kotlin-jvm/0.11.0`.

Finally, in the `build.gradle.kts` file of the project where you intend to use this library, add mavenLocal to the list of repositories and add zenoh-kotlin as a dependency:

```
repositories {
    mavenCentral()
    mavenLocal()
}

dependencies {
    testImplementation(kotlin("test"))
    implementation("io.zenoh:zenoh-kotlin-jvm:0.11.0")
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
```
- x86
- x86_64
- arm
- arm64
```

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

You should now be able to see the package under `~/.m2/repository/io/zenoh/zenoh-kotlin-android/0.11.0`
with the following files:
```
zenoh-kotlin-android-0.11.0-sources.jar
zenoh-kotlin-android-0.11.0.aar
zenoh-kotlin-android-0.11.0.module
zenoh-kotlin-android-0.11.0.pom
```

Now the library is published on maven local, let's now see how to import it into an Android project.

First, we need to indicate we want to look into mavenLocal for our library, so in your top level `build.gradle.kts` you need to specify
the `mavenLocal` repository:
```
repositories {
    mavenCentral()
    ...
    mavenLocal() // We add this line
}
```

Then in your app's `build.gradle.kts` filen add the dependency:
```
implementation("io.zenoh:zenoh-kotlin-android:0.11.0")
```

And finally, do not forget to add the required internet permissions on your manifest!

```
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
```

And that was it! You can now import the code from the `io.zenoh` package and use it at your will.

## Building the documentation

Because it's a Kotlin project, we use [Dokka](https://kotlinlang.org/docs/dokka-introduction.html) to generate the documentation.

In order to build it, run:
```bash
gradle zenoh-kotlin:dokkaHtml
```

## Running the tests

To run the tests, run:

```bash
gradle jvmTest
```

This will compile the native library on debug mode (if not already available) and run the tests afterward against the JVM target.
Running the tests against the Android target (by using `gradle testDebugUnitTest`) is equivalent to running them against the JVM one, since they are common
tests executed locally as Unit tests.

## Logging

Rust logs are propagated when setting the property `zenoh.logger=debug` (using RUST_LOG=debug will result in nothing)

For instance running the ZPub test as follows:

```bash
gradle -Pzenoh.logger=debug ZPub
```

causes the logs to appear in standard output.

The log levels are the ones from Rust: `trace`, `info`, `debug`, `error` and `warn`.

---

# Examples

You can find some examples located under the [`/examples` folder](examples).
Once we've built the project, to run them, simply run `./gradlew <EXAMPLE_NAME>`.

For instance in order to run the [ZPub](examples/src/main/kotlin/io.zenoh/ZPub.kt) example, type:

```bash
./gradlew ZPub
```

You can find more info about these examples on the [examples README file](/examples/README.md).





----

# :warning: Considerations & Future work

### Packaging

We intend to publish this code on Maven in the short term in order to ease the installation, but for the moment, until we
add some extra functionalities and test this library a bit further, we will only publish packages to Github packages.

### Potential API changes

When using this library, keep in mind the api is not fully stable. Changes are to be expected, especially for version 1.0.0. 


### Performance

The communication between the Kotlin code and the Rust code through the java native interface (JNI) has its toll on performance.

Some preliminary performance evaluations done on an M2 Mac indicate around a 50% performance drop regarding the publication throughput
(compared to Rust-Rust communication), and for subscription throughput the performance is similar to that of zenoh-python, with around 500K messages per second
for an 8 bytes payload messages.
