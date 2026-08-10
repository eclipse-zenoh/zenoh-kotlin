# CI in zenoh-kotlin

What CI here checks, and how it stays current with the rest of the stack. For
releases see [PUBLISHING.md](PUBLISHING.md); for building against zenoh-flat-jni
source yourself, [README.md](README.md#building-against-zenoh-flat-jni-source).

## Contents

- [What CI runs](#what-ci-runs)
- [The pin](#the-pin)
- [Lockfile synchronization](#lockfile-synchronization)
- [Moving the pin by hand](#moving-the-pin-by-hand)
- [Publishing does not use any of this](#publishing-does-not-use-any-of-this)

## What CI runs

One job per platform, and one command that matters:

```bash
./gradlew jvmTest --info -PuseLocalFlatJni=true
```

That property makes the build resolve `zenoh-flat-jni` from **source** rather
than from Maven Central: `settings.gradle.kts` fetches the commit this
repository's `Cargo.lock` pins, includes it as a composite build, and Gradle
drives cargo for its native library. So the same command reproduces a CI run on
any machine, and CI checks out nothing but this repository.

Nothing else Rust runs here. Formatting, clippy and the native build belong to
zenoh-flat-jni's own CI, which runs them on three platforms for the very commit
pinned here; repeating them from this repository would only add ways for two
toolchains to disagree.

## The pin

CI has to answer one question — *which* `zenoh-flat-jni` is this SDK tested
against? — and both easy answers are bad. A commit written into the workflow is
reproducible and goes stale, because moving it is somebody's chore. Tracking
that repository's `main` is never stale and is not reproducible: a CI result
stops being determined by this repository's commit.

A lockfile is neither, because a bot moves it. That is what the crate at the
repository root is for — `Cargo.toml`, `ci/pin.rs`, `rust-toolchain.toml`,
`Cargo.lock`. It compiles to nothing anyone ships. Its entire content is one
dependency:

```toml
zenoh-flat-jni = { git = "https://github.com/eclipse-zenoh/zenoh-flat-jni.git", branch = "main" }
```

whose only job is to make the commit under test a resolved lockfile entry:

```text
Cargo.lock:  source = "git+https://github.com/eclipse-zenoh/zenoh-flat-jni.git?branch=main#<40-hex commit>"
```

Nothing here builds that crate. `cargo build` at the repository root would
compile zenoh and the bindings only to produce an empty library — if an IDE
offers to load the root `Cargo.toml` as a Rust project, decline. Gradle only
*reads* these two files; it never runs Cargo against them.

## Lockfile synchronization

The pin makes a run reproducible. What keeps it from going stale is
[`eclipse-zenoh/ci`](https://github.com/eclipse-zenoh/ci)'s **`sync-lockfiles`**
workflow, which every zenoh dependant already uses to stay aligned with zenoh:

```text
zenoh ──> zenoh-flat ──> zenoh-flat-jni ──> zenoh-kotlin
```

It is triggered by a push to zenoh's `main` that touches its `Cargo.lock`, and
for each dependant it:

1. **overwrites** the dependant's `Cargo.lock` with zenoh's;
2. **rectifies** it — resolves and compiles the manifest again — which restores
   whatever zenoh's lockfile did not carry, while keeping the dependency
   versions zenoh pins;
3. opens a pull request that **auto-merges** once that repository's own CI
   passes.

For this repository, step 1 removes the `zenoh-flat-jni` entry — zenoh's
lockfile has never heard of it — and step 2 writes it back at zenoh-flat-jni's
current `main`, having compiled it first. So the pin advances only to a commit
that builds, and only if the tests here pass against it. That is also why the
pin crate sits at the repository **root**: it makes this repository an ordinary
dependant of that workflow rather than a special case inside it.

One consequence worth knowing: the sync also re-pins `zenoh` in this lockfile,
where zenoh is merely a transitive dependency of the pin crate. Harmless —
nothing is built from this lockfile — but it means the zenoh revision recorded
here is not necessarily the one inside the zenoh-flat-jni commit it pins. The
commit is the pin; the rest of the lockfile is a by-product.

## Moving the pin by hand

Normally you don't — the bot's pull request does. When you need to, for example
to test against an unreleased zenoh-flat-jni change:

```bash
cargo update -p zenoh-flat-jni                 # to zenoh-flat-jni's main tip
cargo update -p zenoh-flat-jni --precise <sha> # to one specific commit
```

Commit the resulting `Cargo.lock`. Two edits defeat the mechanism rather than
steering it:

- **`rev = "…"` in `Cargo.toml`** freezes resolution at a commit, so the sync can
  no longer move the pin and the bot goes silent.
- **A committed `path = "…"`** leaves the lockfile pinning no commit at all: CI
  says so and fails, and the sync cannot resolve a sibling directory on a runner
  either. It is meant to be a local edit — `git checkout Cargo.toml` when you are
  done, and `Cargo.lock` too if you ran Cargo while it was set.

To try a commit without touching the lockfile at all, pass
`-PflatJniCommit=<sha>`.

## Publishing does not use any of this

A release resolves `org.eclipse.zenoh:zenoh-flat-jni:$zenohFlatJniVersion` from
Maven Central, like any other consumer. The pin, the lockfile and the composite
build play no part in it — they exist so that *testing* against unreleased
bindings is reproducible.

The two are deliberately independent: `zenohFlatJniVersion` in
`gradle.properties` says which **release** this SDK is built and published
against, `Cargo.lock` says which **commit** it is tested against, and moving one
does not move the other. A release must in fact avoid the composite build
entirely — the artifact would be built from source on the builder's disk while
the POM still claimed the released version — so `build.gradle.kts` fails any
`publish*` task while an included build is present. See
[PUBLISHING.md](PUBLISHING.md#building-against-zenoh-flat-jni-source).
