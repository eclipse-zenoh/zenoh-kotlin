# CI in zenoh-kotlin

What CI here checks, and how it stays current with the rest of the stack. For
releases see [PUBLISHING.md](PUBLISHING.md); for building against zenoh-flat-jni
source yourself, [README.md](README.md#where-the-native-library-comes-from).

## Contents

- [What CI runs](#what-ci-runs)
- [How the source is resolved](#how-the-source-is-resolved)
- [The pin](#the-pin)
- [Lockfile synchronization](#lockfile-synchronization)
- [Moving the pin by hand](#moving-the-pin-by-hand)
- [What publishing uses](#what-publishing-uses)

## What CI runs

One job per platform, and one command that matters:

```bash
./gradlew jvmTest --info -PuseLocalJni=true
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

## How the source is resolved

`settings.gradle.kts` decides where `zenoh-flat-jni` comes from before any
project is configured, because a composite build has to be declared at settings
time. In order:

| | | |
| --- | --- | --- |
| `-PlocalJniDir=<path>` | that directory, as it is | `Cargo.toml` not read |
| `-PuseLocalJni=true` | whatever `Cargo.toml` says | `git` → the `Cargo.lock` commit; `path` → that directory |
| neither | Maven Central | no composite build |

The middle row is the Rust-shaped one: the manifest is the switch, exactly as it
would be for a Cargo build.

- **`git`** (the committed form) resolves to the commit `Cargo.lock` records. If
  there is no lockfile, or none mentioning zenoh-flat-jni, Gradle runs `cargo
  generate-lockfile` to produce one — the same resolution a `cargo build` here
  would do, needing the network but no compiler. The commit is then fetched into
  `.zenoh-flat-jni/` (gitignored), shallow, and only when that directory is not
  already at it. `-PlocalJniCommit=<sha>` overrides the commit without touching
  the lockfile; it has to be a full 40-character hash, because that is what a
  remote can be asked to fetch.
- **`path`** is honoured with or without the property — it is a deliberate local
  edit, and Cargo would honour it too:

  ```toml
  # zenoh-flat-jni = { git = "https://github.com/eclipse-zenoh/zenoh-flat-jni.git", branch = "main" }
  zenoh-flat-jni = { path = "../zenoh-flat-jni" }
  ```

  Don't commit that form: the lockfile then pins no commit, and the sync below
  cannot resolve a sibling directory on a runner. `git checkout Cargo.toml` when
  you are done — and `Cargo.lock` too, if you ran Cargo while it was set.

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
offers to load the root `Cargo.toml` as a Rust project, decline. Gradle reads
these two files, and runs Cargo against them only to *resolve* a missing
lockfile, never to compile.

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

Note what moves the pin, and what does not: the trigger is a change to *zenoh's*
lockfile, not to zenoh-flat-jni. The pin advances because every sync run
re-resolves it, not because zenoh-flat-jni moved. In practice that is frequent
enough — zenoh's lockfile changes every day or two — but it is a side effect, so
when you need a zenoh-flat-jni commit picked up now rather than at the next sync,
move the pin by hand as below.

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

Commit the resulting `Cargo.lock`. Do not add `rev = "…"` to `Cargo.toml`: that
freezes resolution at a commit, so the sync can no longer move the pin and the
bot goes silent. (The other way to defeat it — committing the `path = "…"` form —
is covered above.)

## What publishing uses

A **release** does not use any of this. It resolves
`org.eclipse.zenoh:zenoh-flat-jni:$zenohFlatJniVersion` from Maven Central like
any other consumer; the pin, the lockfile and the composite build play no part.
There, `zenohFlatJniVersion` says which **release** the SDK is published against
and `Cargo.lock` says which **commit** it is tested against, and moving one does
not move the other.

A **snapshot** makes them the same commit, by construction. It has to satisfy
two things at once — it must publish even if zenoh-flat-jni's CI has never run,
and the dependency it names must exist and be the code it compiled against — and
the only construction that does both is to publish what it depends on:

```text
Cargo.lock pin ──> build zenoh-flat-jni from that commit
                   publish it as 1.9.0-kotlin-SNAPSHOT
                                   |
                                   v
                   build the SDK against that coordinate
                   publish zenoh-kotlin:<version>-SNAPSHOT
```

So on `main`, the pin drives the publication as well as the tests, and
`gradle.properties` names our own copy rather than a zenoh-flat-jni release.
The mechanics — the qualifier, the commit stamp that decides whether the copy
needs rebuilding, what the pairing does and does not guarantee — are in
[PUBLISHING.md](PUBLISHING.md#the-snapshot-publication).

Either way a publication must avoid the composite build: the artifact would be
built from source on the builder's disk while the POM still claimed a resolved
version, so `build.gradle.kts` fails any `publish*` task while an included build
is present. See
[PUBLISHING.md](PUBLISHING.md#building-against-zenoh-flat-jni-source).
