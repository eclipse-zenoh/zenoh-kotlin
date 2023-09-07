<img src="https://raw.githubusercontent.com/eclipse-zenoh/zenoh/master/zenoh-dragon.png" height="150">

[![Discussion](https://img.shields.io/badge/discussion-on%20github-blue)](https://github.com/eclipse-zenoh/roadmap/discussions)
[![Discord](https://img.shields.io/badge/chat-on%20discord-blue)](https://discord.gg/2GJ958VuHs)
[![License](https://img.shields.io/badge/License-EPL%202.0-blue)](https://choosealicense.com/licenses/epl-2.0/)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)


# Eclipse Zenoh

The Eclipse Zenoh: Zero Overhead Pub/sub, Store/Query and Compute.

Zenoh (pronounce _/zeno/_) unifies data in motion, data at rest and computations. It carefully blends traditional pub/sub with geo-distributed storages, queries and computations, while retaining a level of time and space efficiency that is well beyond any of the mainstream stacks.

Check the website [zenoh.io](http://zenoh.io) and the [roadmap](https://github.com/eclipse-zenoh/roadmap) for more detailed information.

----

# Kotlin API


This repository provides a Kotlin binding based on the main [Zenoh implementation written in Rust](https://github.com/eclipse-zenoh/zenoh).

The code relies on native code written in Rust and communicates with it via the Java Native Interface (JNI).

----

# How to build it

## What you need

Basically:
* Rust ([Installation guide](https://doc.rust-lang.org/cargo/getting-started/installation.html))
* Kotlin ([Installation guide](https://kotlinlang.org/docs/getting-started.html#backend))
* Gradle ([Installation guide](https://gradle.org/install/))

## Step by step

### Building zenoh-jni

Since Zenoh-Kotlin relies on a native rust interface that communicates with Zenoh, first you need to build it.

Find the code in this repository on [here](/zenoh-jni):

```bash
cd zenoh-jni
```

The let's build it with Cargo:

```bash
cargo build --release
```

This will generate a library under `/target/release` named:
* MacOS: `libzenoh_jni.dylib`
* Linux: `libzenoh_jni.so`
* Windows: `libzenoh_jni.dll`

This file needs to be discoverable by the JVM. Therefore, `zenoh_jni` library should also be on the `java.library.path`. Thus depending on your
system make sure to install it at the proper place.

For MacOS and Unix-like operating systems, the library is expected to be found under `/usr/local/lib`.
For Windows users you may want to add the location of the library to your `$PATH` environment variable.

:warning: Note that failure to make `zenoh_jni` discoverable will cause the kotlin tests fail during the kotlin build process and
any further intent to use this library will result in an error during runtime, due to an `UnsatisfiedLinkError`.

### Building Kotlin!

Now let's go to the [zenoh-kotlin subdirectory](zenoh-kotlin) of this repository.

```bash
cd zenoh-kotlin
```


It is best to build and run using the `gradle` wrapper, thus type:

    $ gradle wrapper

Then you can build by simply:

    $ ./gradlew build



That was it! We now can build our first Kotlin app using Zenoh!

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
add some extra functionalities and test this library a bit further, we will hold the publication.

### Potential API changes

When using this library, keep in mind changes may occur, especially since this is the first version of the library. We have, however,
aimed to make the design as stable as possible from the very beginning, so changes on the code probably won't be substantial.

### Missing features

There are some missing features we will implement soon. The most notorious is the Pull Subscriber feature.

### Performance

The communication between the Kotlin code and the Rust code through the java native interface (JNI) has its toll on performance.

Some preliminary performance evaluations done on an M2 Mac indicate around a 50% performance drop regarding the publication throughput
(compared to Rust-Rust communication), and for subscription throughput the performance is similar to that of zenoh-python, with around 500K messages per second
for an 8 bytes payload messages.

### Java compatibility

This library is not Java compatible due to it relying specially on Results and on coroutine Channels, two features that are not Java compatible.

However, for the short term, we aim to make some implementations in order to make this library Java compatible.
