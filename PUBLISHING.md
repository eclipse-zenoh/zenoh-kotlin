# Publishing zenoh-kotlin

This document describes how `zenoh-kotlin` is built, verified, and published to
Maven Central, and how to rehearse a release without publishing one.

It describes the pipeline as it exists in this repository. Where something is
not yet implemented or not yet exercised, it is listed under
[Known gaps](#known-gaps) rather than described as if it worked.

If you just need to run a release, go to [Running a release](#running-a-release).
For the JVM publishing concepts — coordinates, staging, signing — see
[zenoh-flat-jni's PUBLISHING.md](https://github.com/eclipse-zenoh/zenoh-flat-jni/blob/main/PUBLISHING.md#background-if-you-do-not-work-in-the-jvm-ecosystem),
which covers them once for the whole stack.

## Contents

- [What this repository publishes](#what-this-repository-publishes)
- [Relationship to zenoh-flat-jni](#relationship-to-zenoh-flat-jni)
- [Running a release](#running-a-release)
  - [Before the first run](#before-the-first-run)
  - [Rehearsal (dry run)](#rehearsal-dry-run)
  - [The real release](#the-real-release)
  - [After a release](#after-a-release)
- [Rehearsing before zenoh-flat-jni is released](#rehearsing-before-zenoh-flat-jni-is-released)
- [How the pipeline works](#how-the-pipeline-works)
- [Local development](#local-development)
- [Required secrets](#required-secrets)
- [Known gaps](#known-gaps)
- [Release checklist](#release-checklist)

## What this repository publishes

```text
org.eclipse.zenoh:zenoh-kotlin:<version>          the JVM artifact
org.eclipse.zenoh:zenoh-kotlin-android:<version>  the Android artifact
```

Both are **pure JVM/Kotlin**. This repository contains no Rust and builds no
native libraries: they arrive inside the zenoh-flat-jni artifacts, already
cross-compiled and verified by that repository's own release, and a consumer of
`zenoh-kotlin` gets them transitively.

`zenoh-flat-jni` is itself a Kotlin Multiplatform library, so this SDK declares
**one** dependency on its root coordinate and Gradle resolves the variant
matching each target:

| zenoh-kotlin artifact | resolves | which carries |
| --- | --- | --- |
| `zenoh-kotlin` | `zenoh-flat-jni-jvm` | six desktop targets |
| `zenoh-kotlin-android` | `zenoh-flat-jni-android` | four Android ABIs, as `jni/<abi>/` |

Nothing selects between them by hand, so a publication cannot name the wrong one.
And because one Gradle invocation produces both publications correctly, they are
uploaded into a single staging repository and released together.

That automatic resolution applies to the *dependency*, not to this SDK itself.
zenoh-kotlin keeps the two plain coordinates above: the release publishes the
`jvm` and `androidRelease` publications only, never the Kotlin Multiplatform
root module, so there is no module metadata for a consumer to resolve against
and an Android consumer must name `zenoh-kotlin-android` explicitly. That is
deliberate — it keeps `org.eclipse.zenoh:zenoh-kotlin` meaning the JVM artifact,
as it always has. It is also why the publish workflow names its two publication
tasks instead of using `publishAllPublicationsTo…`: the unpublished root
publication carries the same `zenoh-kotlin` artifactId as the JVM one, and
publishing both would upload two different things to one coordinate.

That is the whole reason the publishing here is simple — there is no build
matrix, no cross-compilation, and no native artifact to inspect.

## Relationship to zenoh-flat-jni

```text
zenoh-flat-jni:<version> released to Maven Central
        │
        └── zenoh-kotlin:<version> built against it, then released
```

The order is not a convention, it is a constraint. `zenoh-kotlin` cannot be
released until the `zenoh-flat-jni` version it depends on is really on Maven
Central, because:

- a release must not depend on a snapshot — consumers do not configure the
  snapshot repository, and snapshots mutate and are eventually removed;
- Maven Central releases are immutable, so a `zenoh-kotlin` release pointing at
  a binding version that never materializes cannot be repaired, only superseded.

`ci/scripts/bump-and-tag.bash` enforces the first half: it refuses to run a
**live** release against a `-SNAPSHOT` binding version. A rehearsal is allowed
to, which is what makes the transition workable — see
[Rehearsing before zenoh-flat-jni is released](#rehearsing-before-zenoh-flat-jni-is-released).

## Running a release

Run the **Release** workflow from the Actions tab, from `main` or a release
branch. The workflow creates the release branch, bumps the version, tags it,
builds and publishes.

### Before the first run

- **Secrets are already in place.** `CENTRAL_SONATYPE_TOKEN_*` and `ORG_GPG_*`
  are organization-level secrets on `eclipse-zenoh`, inherited automatically.
- **The zenoh-flat-jni version must already be on Maven Central** for a live
  run. Check before starting:

  ```bash
  curl -sfI https://repo1.maven.org/maven2/org/eclipse/zenoh/zenoh-flat-jni/<version>/zenoh-flat-jni-<version>.pom
  ```

### Rehearsal (dry run)

| Field | Value |
| --- | --- |
| `live-run` | **unchecked** |
| `version` | a fresh provisional number, not one already used |
| `zenoh-flat-jni-version` | leave empty for a rehearsal, or a released version |
| `maven_publish` | checked — or uncheck for the very first run |

`live-run` and `maven_publish` behave exactly as in zenoh-flat-jni: unchecking
`live-run` publishes `<version>-SNAPSHOT` to the **mutable** snapshot repository
and never runs `closeAndReleaseSonatypeStagingRepository`, while `maven_publish`
decides whether any upload happens at all. **A rehearsal with `maven_publish`
checked performs a real, signed upload** — into the snapshot repository — and is
the only configuration that exercises the credentials.

`bump-and-tag.bash` tags whatever version it is handed, rehearsals included, so
never give a rehearsal the number you intend to release.

### The real release

| Field | Value |
| --- | --- |
| `live-run` | **checked** |
| `version` | the release number |
| `zenoh-flat-jni-version` | the zenoh-flat-jni release to build against — **must already be on Central** |
| `maven_publish` | checked |

Supplying `zenoh-flat-jni-version` rewrites `zenohFlatJniVersion` in
`gradle.properties` and commits it, so the published POM records exactly which
binding release the SDK was built against.

### After a release

Confirm the coordinates resolve, then verify the dependency is right — the POM
must reference a real `zenoh-flat-jni` release:

```bash
curl -s https://repo1.maven.org/maven2/org/eclipse/zenoh/zenoh-kotlin/<version>/zenoh-kotlin-<version>.pom \
  | grep -A2 zenoh-flat-jni
```

## Rehearsing before zenoh-flat-jni is released

This is the common case during the transition, and it works — only the *live*
release is blocked.

| Rehearsal | resolves zenoh-flat-jni from | proves |
| --- | --- | --- |
| local build and tests | its source — the pinned commit, or your own checkout ([Local development](#local-development)) | the code compiles and the tests pass |
| CI, `maven_publish` unchecked | the snapshot repository | the artifacts assemble |
| CI, snapshot publication | `zenoh-flat-jni:<version>-SNAPSHOT` | signing, credentials, a real upload |
| live release | `zenoh-flat-jni:<version>` on Central | **blocked until that exists** |

A snapshot may depend on a snapshot, because nothing published is permanent. So
the answer is to consume the snapshot that zenoh-flat-jni's *own* rehearsal
published — a rehearsal there with `maven_publish` enabled uploads
`zenoh-flat-jni:<version>-SNAPSHOT` to the Central snapshot repository.

Nothing needs editing. Name the version on the command line:

```bash
./gradlew build -PzenohFlatJniVersion=1.9.0-rc4-SNAPSHOT
```

or pass the same value as the `zenoh-flat-jni-version` input to a rehearsal of
the release workflow.

The Central snapshot repository is declared **conditionally** in
`build.gradle.kts`, and this is the part worth understanding:

```kotlin
if (zenohFlatJniVersion.endsWith("-SNAPSHOT")) {
    maven {
        url = uri("https://central.sonatype.com/repository/maven-snapshots/")
        content { includeGroup("org.eclipse.zenoh") }
    }
}
```

`includeGroup`, not `includeModule`: the dependency is declared on the root
coordinate, but what Gradle actually downloads is `zenoh-flat-jni-jvm` or
`zenoh-flat-jni-android`. Naming a single module would hide those from the
snapshot repository and the build would fail to resolve.

It enters the resolution path only when a snapshot version was explicitly asked
for, and even then serves only that one group. A release version never ends in
`-SNAPSHOT`, so a release build cannot resolve a mutable artifact — not by
policy, but because the repository that serves them is not on the path.

## How the pipeline works

`release.yml` runs three jobs:

1. **`tag`** — `eclipse-zenoh/ci/create-release-branch` cuts the release branch,
   then `ci/scripts/bump-and-tag.bash` writes `version.txt`, optionally rewrites
   `zenohFlatJniVersion` in `gradle.properties`, commits and tags. It refuses a
   `-SNAPSHOT` binding version on a live run.
2. **`publish`** — compiles Kotlin and publishes both artifacts in one Gradle
   invocation, so they share one staging repository and are released together.
   No native toolchain is installed and no Rust is built; both would be
   pointless here.
3. **`publish-dokka`** — regenerates the API documentation and, on a live run
   only, deploys it to the `gh-pages` site README.md links to. The javadoc JAR
   attached to the Maven publications does not serve that site; this job does.

Publishing goes through `io.github.gradle-nexus.publish-plugin` to the Central
Portal, signed with the organization GPG key, exactly as in zenoh-flat-jni.

## Local development

The default build resolves `zenoh-flat-jni` from Maven Central like any consumer.
A build can be pointed at its *source* instead, through a Gradle composite build.
`settings.gradle.kts` decides where that source comes from, in this order:

| | says "use this source" | where it comes from |
| --- | --- | --- |
| 1 | `-PflatJniDir=<path>` | that directory, as it is |
| 2 | `path = "…"` in `Cargo.toml` | that directory, as it is |
| 3 | `-PuseLocalFlatJni=true` | the commit `Cargo.lock` pins, fetched into `.zenoh-flat-jni/` |
| 4 | nothing | Maven Central — no composite build at all |

So working against a checkout of your own is the ordinary Cargo edit, in
`Cargo.toml`:

```toml
# zenoh-flat-jni = { git = "…/zenoh-flat-jni.git", branch = "main" }
zenoh-flat-jni = { path = "../zenoh-flat-jni" }
```

and `./gradlew build` picks it up with no properties at all. `-PflatJniDir=…`
does the same without editing anything, for a checkout somewhere else.

Reproducing what CI tested is `./gradlew jvmTest -PuseLocalFlatJni=true`, which
fetches the pinned commit into `.zenoh-flat-jni/` — that is the whole of what CI
does, so the same command reproduces a CI run anywhere. It fetches only when that
directory is not already at the pinned commit, and `-PflatJniCommit=<sha>` tries a
different one without touching the lockfile.

**A release takes row 4, and must.** With a composite build the published
artifact would be built against whatever was on the builder's disk while the POM
still claimed the released version. Nothing opts in by default; CI opts in
explicitly.

### The pin crate

Rows 2 and 3 read a Rust crate at the repository root — `Cargo.toml`,
`ci/pin.rs`, `rust-toolchain.toml`, `Cargo.lock` — that compiles to nothing
anyone ships. Its only content is a dependency on `zenoh-flat-jni`, and its only
purpose is to make the commit under test a *resolved lockfile entry*:

```toml
zenoh-flat-jni = { git = "…/zenoh-flat-jni.git", branch = "main" }
```

```text
Cargo.lock:  source = "git+…/zenoh-flat-jni.git?branch=main#<40-hex commit>"
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
load the root `Cargo.toml` as a Rust project, decline. Gradle only *reads* these
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

The pin governs source builds only. Which `zenoh-flat-jni` *release* this SDK is
built and published against is `zenohFlatJniVersion` in `gradle.properties`.

## Required secrets

| Secret | Use |
| --- | --- |
| `CENTRAL_SONATYPE_TOKEN_USERNAME` / `_PASSWORD` | Central Portal user token |
| `ORG_GPG_KEY_ID` / `_SUBKEY_ID` / `_PRIVATE_KEY` / `_PASSPHRASE` | signing |
| `BOT_TOKEN_WORKFLOW` | release branch and tag push |

All are organization-level on `eclipse-zenoh`; nothing is configured per
repository.

## Known gaps

- **The rewritten release path has never run.** The workflows were repaired for
  a repository that no longer contains Rust; no rehearsal has yet exercised
  them.
- **No consumer test.** Unlike zenoh-flat-jni, nothing resolves the published
  `zenoh-kotlin` artifact from a repository and runs it before release. The
  tests here run against the build's own output.
- **No GitHub release is created.** Unlike zenoh-java, `release.yml` has no
  `publish-github` job, so a release produces Maven artifacts, a tag and updated
  documentation, but no GitHub release entry. This predates the flat-jni
  transition and is left as it was.
- **The Android artifact has no runtime test.** Only the JVM tests run
  (`jvmTest`); the Android variant is assembled and published unexercised.
- **`zenoh-flat-jni` itself has not been released**, so the ordering constraint
  above has never been satisfied for a real release.

## Release checklist

- [ ] The `zenoh-flat-jni` version to build against is on Maven Central.
- [ ] `version.txt` and the intended tag agree.
- [ ] A rehearsal completed under a fresh version, with publication enabled at
      least once so signing and credentials were exercised.
- [ ] `useLocalFlatJni` is off — the release resolves from Central.
- [ ] The published POM references a released `zenoh-flat-jni`, not a snapshot.
- [ ] For an Android release: the Android POM references
      `zenoh-flat-jni-android`, not the desktop coordinate.
- [ ] The released coordinates resolve from Maven Central.
