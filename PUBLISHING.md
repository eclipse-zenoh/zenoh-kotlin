# Publishing zenoh-kotlin

This document describes how `zenoh-kotlin` is built, verified, and published to
Maven Central, and how to test the release process with a dry run.

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
- [The snapshot publication](#the-snapshot-publication)
  - [Updating the snapshot dependency](#updating-the-snapshot-dependency)
  - [What it does not guarantee](#what-it-does-not-guarantee)
- [Running a release](#running-a-release)
  - [Release dry run](#release-dry-run)
    - [Choosing a JNI snapshot for a dry run](#choosing-a-jni-snapshot-for-a-dry-run)
  - [The real release](#the-real-release)
  - [After a release](#after-a-release)
  - [If a release fails](#if-a-release-fails)
  - [If a step after Maven Central fails](#if-a-step-after-maven-central-fails)
    - [Creating a GitHub release on its own](#creating-a-github-release-on-its-own)
- [How the release pipeline works](#how-the-release-pipeline-works)
  - [Maven dependency resolution](#maven-dependency-resolution)
- [Building against zenoh-flat-jni source](#building-against-zenoh-flat-jni-source)
- [Required secrets](#required-secrets)
- [Known gaps](#known-gaps)
- [Release checklist](#release-checklist)
  - [Before the live release](#before-the-live-release)
  - [After the live release](#after-the-live-release)
  - [When merging back to main](#when-merging-back-to-main)

## What this repository publishes

```text
org.eclipse.zenoh:zenoh-kotlin:<version>          the JVM artifact
org.eclipse.zenoh:zenoh-kotlin-android:<version>  the Android artifact
```

`<version>` comes from this repository's `version.txt`; snapshot uploads append
`-SNAPSHOT` to it for both artifacts.

Both are **pure JVM/Kotlin**. Neither carries Rust or a native library: those
arrive inside the compiled zenoh-flat-jni artifacts, which a consumer of
`zenoh-kotlin` gets transitively.

The small Rust crate in this repository supports
[JNI dependency tracking](#relationship-to-zenoh-flat-jni); it contributes
nothing to these artifacts.

`zenoh-flat-jni` is itself a Kotlin Multiplatform library, so this SDK declares
**one** dependency on its root coordinate and Gradle resolves the variant
matching each target:

| zenoh-kotlin artifact | resolves | which carries |
| --- | --- | --- |
| `zenoh-kotlin` | `zenoh-flat-jni-jvm` | six desktop targets |
| `zenoh-kotlin-android` | `zenoh-flat-jni-android` | four Android ABIs, as `jni/<abi>/` |

Gradle selects the matching JNI variant for each SDK target. For a permanent
release, both SDK publications are uploaded in one Gradle invocation to a
single staging repository and released together.

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

## Relationship to zenoh-flat-jni

zenoh-kotlin uses the Kotlin bindings and native libraries packaged in
zenoh-flat-jni. `zenohFlatJniVersion` in `gradle.properties` selects that Maven
package; the published SDK records it as a dependency so installations download
the JNI package too.

CI also tests JNI changes before they have a Maven release. It builds an exact
source commit recorded in `Cargo.lock`. The dummy `Cargo.toml` and empty
`ci/pin.rs` exist so this Gradle project can reuse Zenoh's lockfile-update bot;
they produce nothing that ships. Gradle builds the pinned JNI source for tests,
without needing a Maven publication. [CI.md](CI.md#lockfile-synchronization)
explains how the bot advances the pin.

Source tests do not exercise signing, uploads, or the published dependency
metadata. To test that path, main CI first publishes its own JNI copy from the
pin, then builds and publishes the SDK against it. A separate smoke test
downloads the SDK and runs it through JNI. Publishing the copy ensures its
bindings match the tested source, independently of upstream JNI publications.

The copy is named `<JNI base version>-kotlin-SNAPSHOT`. `-SNAPSHOT` allows
successive development builds to replace it; `-kotlin` prevents collisions with
snapshots published by zenoh-flat-jni or zenoh-java, which may pin different
commits. The base comes from the pinned JNI source's `version.txt`.

## The snapshot publication

Every merge to `main`, and a manual CI dispatch on `main`, first runs the SDK
tests against the pinned JNI source.
If they pass, CI uploads an SDK snapshot to the
[Central snapshot repository](https://central.sonatype.com/repository/maven-snapshots/)
and tests the downloaded package in a separate consumer build. Pull requests
run tests without publishing.

The publication covers five coordinates:

```text
org.eclipse.zenoh:zenoh-kotlin:<version>-SNAPSHOT
org.eclipse.zenoh:zenoh-kotlin-android:<version>-SNAPSHOT
org.eclipse.zenoh:zenoh-flat-jni:1.10.1-kotlin-SNAPSHOT        (+ -jvm, -android)
```

The last three are Kotlin's JNI copy. CI publishes that copy first, when it
needs rebuilding, then builds and publishes the SDK against it. Central stores
successive uploads as timestamped snapshot builds under these fixed names.

Rebuilding the JNI copy means cross-compiling ten targets, on the order of
half an hour. CI reuses the published copy when its commit matches the pin.
Every POM zenoh-flat-jni publishes carries the commit it was built from:

```console
$ curl -s .../1.10.1-kotlin-SNAPSHOT/maven-metadata.xml           # ~2.9 kB
$ curl -s .../zenoh-flat-jni-1.10.1-kotlin-<timestamp>-<n>.pom    # ~1.8 kB
<zenoh.flatJniCommit>e75529ce…</zenoh.flatJniCommit>
```

`ci/scripts/flat-jni-copy.bash` reads that stamp from all three coordinates and
compares it with the pin; anything missing or different means rebuild. Run it
locally to see the decision, or `--self-test` to check its parsers.

The SDK publication uses `--refresh-dependencies` so Gradle downloads the JNI
copy just published rather than reusing an older cached snapshot.

Every successful snapshot upload is followed by `ci/consumer-smoke-test`, a
separate Gradle build with no project or composite dependency on this one. It
resolves `zenoh-kotlin:<version>-SNAPSHOT` from the snapshot repository with
`--refresh-dependencies` and runs a key-expression round trip through JNI. This
checks the uploaded SDK metadata, its transitive JNI dependency, and native
library loading together.

### Updating the snapshot dependency

The JNI dependency on `main` has this form in `gradle.properties`:

```properties
zenohFlatJniVersion=<JNI base version>-kotlin-SNAPSHOT
```

When [updating the JNI source pin](#building-against-zenoh-flat-jni-source), read
`version.txt` at the pinned **zenoh-flat-jni** commit to determine the base.
For base version `1.10.1`, use `1.10.1-kotlin-SNAPSHOT`. Leave this property
alone when the base version stays the same; if it changes, update the property
in the same PR as `Cargo.lock`. The JNI publisher checks that the
requested base matches the pinned source before building. Leave the SDK's own
`version.txt` unchanged for a JNI-only update.

Merge the tested change to `main` to start the snapshot publication
automatically. No manual publication is needed; PRs publish nothing.

### What it does not guarantee

The two uploads are separate Gradle invocations and a snapshot repository has no
staging-and-flip, so nothing makes the pair atomic. `main`'s CI runs are
serialized rather than cancelled — cancelling mid-publication is what splits
them — but a failure during the second upload still leaves a split state until
the next successful run. That is accepted for a mutable pre-release artifact;
strict coherence would need the SDK to name an immutable, timestamped snapshot,
which conflicts with the fixed names above.

## Running a release

Run the **Release** workflow from the Actions tab. Its `branch` input selects
the source branch or commit to release; empty means the repository's default
branch, `main`. The Actions **Use workflow from** selector chooses the workflow
definition to run, not the source the shared release action clones. For a patch
release based on `release/1.10.1`, explicitly enter `branch=release/1.10.1`.
For a live release, use the [release checklist](#release-checklist).

The workflow cuts `release/<version>` for a live run or
`release/dry-run/<version>` for a release dry run, then bumps, tags, builds and
publishes from that new branch. Always supply `version` explicitly. An empty
input is accepted and replaced with `git describe` output, which can produce a
tag such as `1.9.0-42-g07cbcc32` even for a live run. These defaults come from
the shared [create-release-branch action](https://github.com/eclipse-zenoh/ci/blob/main/src/create-release-branch.ts).

Two checkboxes decide what it does. `live-run` chooses what "publish" *means*;
`maven_publish` chooses whether the upload happens at all. Three combinations
are accepted and the fourth is refused:

| `live-run` | `maven_publish` | What it does |
| --- | --- | --- |
| ✗ | ✓ | **Release dry run.** Uploads `<version>-SNAPSHOT` to the mutable snapshot repository — a real signed upload, so credentials and signing are exercised |
| ✗ | ✗ | Assembles both publications and their POMs, uploads nothing |
| ✓ | ✓ | **The real release.** Permanent and immutable on Maven Central |
| ✓ | ✗ | **Refused** by the `tag` job — it would tag a version and publish nothing |

`maven_publish` defaults to checked so a dry run tests signing and uploading.
The snapshot repository is mutable, is not on consumers' default resolution
path, and is cleaned after 90 days. A dry run without uploads cannot verify the
signing key and credentials.

### Release dry run

| Field | Value |
| --- | --- |
| `live-run` | **unchecked** |
| `branch` | `main`, or the maintenance branch for a patch release, e.g. `release/1.10.1` |
| `version` | an explicit provisional SDK number reserved for this dry run; never an already-released number or one reserved for a live release |
| `zenoh-flat-jni-version` | empty to inherit the branch's checked-in JNI version, or an available JNI Maven release or snapshot |
| `maven_publish` | checked to test a real signed upload; unchecked to assemble without uploading |
| `github_release` | either — a dry run creates no GitHub release |

The workflow prepares a dry-run release branch and applies any explicit
`zenoh-flat-jni-version` to `gradle.properties` there, leaving `main` unchanged.
It publishes the SDK as `<version>-SNAPSHOT` when `maven_publish` is checked. When the
run reaches tagging, `bump-and-tag.bash` creates and **force-pushes** the tag
even with `LIVE_RUN=false`: its `[[ ${live_run} ]]` test accepts either nonempty
string, `true` or `false`. A dry run given an already-released number can move
the real release tag onto the dry-run commit. Use a fresh explicit number such
as `1.10.2-rc1` and check existing tags before dispatching. Reuse a version only
when retrying your own unpublished attempt under
[If a release fails](#if-a-release-fails).

Tags from dry runs are not cleaned up automatically. The shared action prunes
old `release/dry-run/*` branches to retain a small history, but leaves tags such
as `1.10.0-rc1` through `1.10.0-rc4` behind. A dry run with `maven_publish` checked also
runs the consumer smoke test described under
[The snapshot publication](#the-snapshot-publication).

To create documentation or a GitHub release for an SDK already on Central, use the
[recovery workflows](#if-a-step-after-maven-central-fails) instead of running
Release again.

#### Choosing a JNI snapshot for a dry run

A dry run can run before the next JNI release exists on Maven Central,
provided its JNI snapshot has already been published. For a dry run from
`main`, leave `zenoh-flat-jni-version` empty to use the JNI snapshot named in
`gradle.properties`. The dry run does not build or publish that
package; the [main CI pipeline](#the-snapshot-publication) does.

To use another JNI snapshot, find an available version:

```bash
curl -s https://central.sonatype.com/repository/maven-snapshots/org/eclipse/zenoh/zenoh-flat-jni/maven-metadata.xml
```

Enter the chosen version in the `zenoh-flat-jni-version` workflow input.
For an optional local build using a published snapshot, override the Gradle
property, for example:

```bash
./gradlew build -PzenohFlatJniVersion=1.10.1-kotlin-SNAPSHOT
```

That override changes neither the checked-in property nor the source pin used
by [source-based tests](#building-against-zenoh-flat-jni-source).

### The real release

First confirm that the JNI release you will select is already on Maven Central.
You can open its POM URL in a browser or check it from the command line:

```bash
curl -fsSI https://repo1.maven.org/maven2/org/eclipse/zenoh/zenoh-flat-jni/<version>/zenoh-flat-jni-<version>.pom
```

Run a [release dry run](#release-dry-run) with `maven_publish` checked, the intended
source and JNI release, and a separate provisional SDK version. After that
workflow succeeds, start the live release with:

| Field | Value |
| --- | --- |
| `live-run` | **checked** |
| `branch` | `main`, or the maintenance branch for a patch release |
| `version` | the explicit release number; never leave empty or reuse a version already accepted by Central |
| `zenoh-flat-jni-version` | the zenoh-flat-jni release to build against — **must already be on Central** |
| `maven_publish` | checked — the only accepted value on a live run |
| `github_release` | checked |

Set `zenoh-flat-jni-version` to the released JNI Maven version, for example
`1.10.1`. The workflow commits `zenohFlatJniVersion=1.10.1` in
`gradle.properties` on the **release branch** before tagging and leaves `main`
unchanged. This selects the JNI package independently of the source pin in
`Cargo.lock`.

`ci/scripts/bump-and-tag.bash` checks the effective property after applying the
input and refuses a live run if it ends in `-SNAPSHOT`. Omitting the input on
`main` therefore does not bypass the check.

### After a release

Confirm both SDK artifacts are available on Central and their POMs name the
selected JNI version. The JVM POM must depend on `zenoh-flat-jni-jvm`, and the
Android POM on `zenoh-flat-jni-android`. For example:

```bash
curl -fsSL https://repo1.maven.org/maven2/org/eclipse/zenoh/zenoh-kotlin/<version>/zenoh-kotlin-<version>.pom \
  | grep -A2 zenoh-flat-jni

curl -fsSL https://repo1.maven.org/maven2/org/eclipse/zenoh/zenoh-kotlin-android/<version>/zenoh-kotlin-android-<version>.pom \
  | grep -A2 zenoh-flat-jni
```

If a GitHub release was requested, confirm it exists and its generated notes
start at the correct previous release tag. The shared action can choose the
wrong predecessor for a backfilled release; see
[eclipse-zenoh/ci#470](https://github.com/eclipse-zenoh/ci/issues/470).

When merging the release branch back into `main`, preserve or restore
`zenohFlatJniVersion=<JNI base version>-kotlin-SNAPSHOT` in `gradle.properties`
as part of that merge. Use the base from the JNI pin that `main` will contain
after the merge, following [Updating the snapshot dependency](#updating-the-snapshot-dependency).
There is no automatic reset: carrying the plain release value back would break
snapshot publication even if source-based tests pass.

Merging the release branch can also carry the SDK's `version.txt` back to
`main`. Retaining that number is accepted for the publication test: after the
`1.10.1` release, `main` can publish `1.10.1-SNAPSHOT`. Maven orders that
snapshot below `1.10.1`, so it is not a way to advertise `main` as newer than
the release. The Release workflow takes the next SDK version from its `version`
input; merging that release branch back updates `main`. The ordering is defined in
[Maven's version specification](https://maven.apache.org/pom.html#version-order-specification).

### If a release fails

**The tag job runs first and pushes before anything is published.** So a run
that dies in `publish` — an unresolvable dependency, a credential problem, a
Central outage — still leaves the release branch and the tag pushed, for a
version that has no artifacts anywhere. zenoh-kotlin's
[failed nightly dry run of 2026-08-12](https://github.com/eclipse-zenoh/zenoh-kotlin/actions/runs/31549368670)
tagged and then failed to resolve `zenoh-flat-jni:1.9.0` while compiling.

Nothing downstream runs, though. `publish-dokka` and `publish-github` both
`need` the publish job, so no documentation is deployed and **no GitHub release
is created**. The whole visible residue is a tag pointing at unpublished code.

**Retrying the same version is safe, as long as Central did not accept it.**
Nothing accumulates across attempts, because every step is recreated rather than
advanced:

- `create-release-branch` does `git switch --force-create` and `git push
  --force`, recreating the release branch from the selected source ref
  (`main` only when `branch` is empty or explicitly `main`);
- `bump-and-tag.bash` therefore rewrites `version.txt` on a fresh branch, so its
  bump commit applies cleanly on a retry;
- the tag is `git tag --force` and `git push --force`.

Fix the cause and run it again with the same explicit version and original
source `branch` input, rather than using the generated release branch as the
source. Check the Central Portal first for a staging repository left open by
the failed attempt, and drop it.

**The one case where retrying the same number is wrong** is a run that got far
enough for Central to accept the version. Maven Central is immutable: the
version cannot be republished, and re-running would force-move the tag onto a
new commit while Central keeps the artifact built from the old one — the tag and
the published artifact would then describe different code. Move to a new version
instead. This is also why a dry run must never use a number reserved for a
permanent release.

If a version reached Central but produced no GitHub release, that is the
recovery case for
[Release (GitHub)](#creating-a-github-release-on-its-own) — and its
`check-maven` check is what distinguishes it from this one, since a tag left by
a failed publish resolves to nothing on Central and is refused.

### If a step after Maven Central fails

Once Central accepts the version, **re-running the release is not an option**:
the version cannot be republished, and the tag would force-move onto a different
commit than the artifact was built from. Everything the pipeline does after that
point must therefore be recoverable on its own, and each piece is:

| Produced | By | Recover with |
| --- | --- | --- |
| Maven Central coordinates | `publish` | nothing to do — immutable and done |
| Release branch and tag | `tag` | already pushed, before `publish` ran |
| GitHub release | `publish-github` | [Release (GitHub)](#creating-a-github-release-on-its-own) |
| `gh-pages` documentation | `publish-dokka` | **Publish (Dokka)**, `live-run` checked and `branch` set to `refs/tags/<version>` |

Both recovery workflows need `live-run` **checked** to publish. Unchecked, each
builds and verifies without publishing, allowing a dry run. Give `publish-dokka`
the released tag as `branch`, written in full as
`refs/tags/<version>`: the release branch can move afterwards, the tag cannot,
and a bare `1.10.0` would resolve to a branch of that name before the tag.

#### Creating a GitHub release on its own

Use the **Release (GitHub)** workflow when the SDK reached Maven Central but
its GitHub release was not created. It uses the existing tag without re-running
the tag or Maven publication jobs.

| Field | Value |
| --- | --- |
| `live-run` | **checked** — unchecked verifies without creating a release |
| `version` | the released number, e.g. `1.10.0` |
| `branch` | the release branch the tag is on, e.g. `release/1.10.0` |
| `check-maven` | checked |

`version` is the exact Git tag name: `bump-and-tag.bash` writes the SDK's
`version.txt` and creates that tag during the release workflow. The GitHub
workflow calls `gh release create` with `--verify-tag`, which requires the tag
to exist. The `branch` input would select where to create a missing tag, but
that fallback is unused here because the existing tag is required.

Before creating the release, the workflow also checks:

| Check | Purpose |
| --- | --- |
| `version.txt` at the tag equals the requested `version` | rejects a tag pointing at a commit naming a different SDK version |
| `check-maven` | confirms the SDK version was published to Maven Central |

A tag from a dry run also passes the version check, so keep `check-maven`
enabled for a standalone workflow run. The normal release pipeline disables it because its
successful Maven publication job supplies that evidence already.

These checks establish that the tag names this version and that the version
exists on Central. They do not prove the Maven artifact was built from the
same commit: the SDK publication carries no build-commit stamp. This is why
[retrying a version already accepted by Central](#if-a-release-fails) is unsafe.

If the Maven check fails, the workflow distinguishes these cases:

| What happened | What to do |
| --- | --- |
| Central says the version is absent | correct the version, or wait if a recent publication has not propagated |
| Central does not answer | retry later, or disable `check-maven` to proceed without that verification |

The documentation deploy can be repeated. The GitHub workflow creates a
release and fails if one already exists. Edit an existing release on its GitHub
page, or delete it there before running the recovery workflow again.
The equivalent GitHub CLI commands are:

```bash
gh release edit <version> --repo eclipse-zenoh/zenoh-kotlin --notes-file notes.md
gh release delete <version> --repo eclipse-zenoh/zenoh-kotlin   # then re-run
```

Deleting the GitHub release leaves its Git tag in place. Publishing a GitHub
release notifies release watchers.

## How the release pipeline works

`release.yml` runs four jobs:

1. **`tag`** — `eclipse-zenoh/ci/create-release-branch` cuts the release branch,
   then `ci/scripts/bump-and-tag.bash` writes `version.txt`, optionally rewrites
   `zenohFlatJniVersion` in `gradle.properties`, commits and tags.
2. **`publish`** — assembles both SDK artifacts and uploads them when
   `maven_publish` is checked. Live releases use one staging repository;
   dry runs upload to the snapshot repository. Native libraries come from the
   selected JNI Maven dependency.
3. **`publish-dokka`** — regenerates the API documentation and, on a live run
   only, deploys it to the `gh-pages` site README.md links to. The javadoc JAR
   attached to the Maven publications does not serve that site; this job does.
4. **`publish-github`** — creates the GitHub release from the tag, on a live run
   only, and only when `maven_publish` was checked. It calls `release-github.yml`,
   which is **also dispatchable on its own** — see [Creating a GitHub release on
   its own](#creating-a-github-release-on-its-own).

Jobs 3 and 4 both delegate to workflows that can be dispatched directly, which
is what makes [recovery](#if-a-step-after-maven-central-fails) possible without
re-running a release.

The full pipeline [released 1.10.1 on 2026-09-07](https://github.com/eclipse-zenoh/zenoh-kotlin/actions/runs/34136408421).
Standalone GitHub-release recovery was also exercised: it
[created the 1.10.0 GitHub release on 2026-08-18](https://github.com/eclipse-zenoh/zenoh-kotlin/actions/runs/32131721797).

The release itself is created by `eclipse-zenoh/ci/publish-crates-github`, the
shared action the rest of the Zenoh repositories use. Despite the name it
publishes no crate: it creates the release from the tag with generated notes,
then attaches any `*-standalone.zip` / `*-debian.zip` build archives. This
repository produces none, so that half is inert and every release it has made
here carries notes and GitHub's own source archives only.

The shared release action's unnecessary TOML-tool installation and incorrect
notes range for backfilled releases are tracked in
[eclipse-zenoh/ci#470](https://github.com/eclipse-zenoh/ci/issues/470). These
operational fixes belong upstream so they reach every Zenoh repository.

Publishing goes through `io.github.gradle-nexus.publish-plugin` to the Central
Portal, signed with the organization GPG key, exactly as in zenoh-flat-jni.

Every third-party action these workflows use is pinned to a **commit SHA**, with
the version in a trailing comment — a tag is mutable, and a moved tag would run
code nobody reviewed on a job that holds the signing key and the Central token.
The `eclipse-zenoh/ci` actions are ours and stay on `@main` deliberately: the
tagging and GitHub-release steps track whatever that branch holds at run time.
Bumping a pin is an ordinary pull request; read the diff of the action first.

### Maven dependency resolution

`build.gradle.kts` enables the Central snapshot repository only when the
requested JNI version ends in `-SNAPSHOT`:

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

For a released JNI version, the snapshot repository is absent from the
resolution path. The [live-release check](#the-real-release) ensures that a
permanent SDK release uses a released JNI dependency.

## Building against zenoh-flat-jni source

CI builds zenoh-flat-jni from source through a Gradle composite build.
The [README](README.md#where-the-native-library-comes-from) describes local build
options; [The pin](CI.md#the-pin) explains how the source commit is selected.

To adopt new JNI code, let the bot update `Cargo.lock` or
[move the pin by hand](CI.md#moving-the-pin-by-hand). Keep the Git dependency in
`Cargo.toml` tracking `main` so the bot can continue advancing it. When updating
the pin on `main`, also check the
[snapshot dependency's base version](#updating-the-snapshot-dependency).

To reproduce CI locally, run:

```bash
./gradlew jvmTest -PuseLocalJni=true
```

Pass `useLocalJni` on the command line and keep the checked-in
`useLocalJni=false` so publication can resolve the Maven package.

**Publication must use the Maven package.** With a composite build, the SDK
would compile against local JNI output while its metadata named a Maven
package that might contain different bindings. Nothing opts in by default, and
`build.gradle.kts` fails any `publish*` task while an included build is present,
so a leftover `-PuseLocalJni`, `-PlocalJniDir` or `path = "…"` cannot reach a
publication silently.

## Required secrets

| Secret | Use |
| --- | --- |
| `CENTRAL_SONATYPE_TOKEN_USERNAME` / `_PASSWORD` | Central Portal user token |
| `ORG_GPG_KEY_ID` / `_SUBKEY_ID` / `_PRIVATE_KEY` / `_PASSPHRASE` | signing |
| `BOT_TOKEN_WORKFLOW` | release branch, tag push, GitHub release |

All are organization-level on `eclipse-zenoh`; nothing is configured per
repository.

## Known gaps

- **A live release can publish the SDK without testing the JNI package users
  will download.** CI builds JNI from the source commit in `Cargo.lock` and
  tests the SDK against it. The release job instead downloads the selected JNI
  Maven package and compiles the SDK against it, but runs no tests. Passing
  source-based CI therefore does not establish that the selected Maven package
  works with the SDK. Publication also starts without waiting for the release
  branch's CI result. A test job against the selected JNI Maven package is
  missing from the release workflow.
- **Published release artifacts have no consumer test.** Snapshot uploads,
  including release dry runs, run a smoke test against the downloaded SDK and
  its JNI dependency. Live releases skip that job and have no consumer test
  before or after publication.
- **The GitHub release carries generated notes only** — notes and the source
  archives GitHub attaches itself. There are no build artifacts to add: the
  binaries live on Maven Central.
- **The Android artifact has no runtime test.** Only the JVM tests run
  (`jvmTest`); the Android variant is assembled and published unexercised.

## Release checklist

Use the GitHub workflows and published metadata for these checks. The runtime
tests missing from the release workflow remain listed under
[Known gaps](#known-gaps).

### Before the live release

- [ ] The JNI Maven release selected in `zenoh-flat-jni-version` is already
      [available on Central](#the-real-release).
- [ ] A [release dry run](#release-dry-run) succeeded with the intended source
      and JNI release, `maven_publish` checked, and a separate provisional SDK version.
- [ ] The live Release inputs select the intended `branch`, an explicit unused
      SDK `version`, and the intended JNI release, with `live-run` and
      `maven_publish` checked.

### After the live release

- [ ] Both SDK artifacts resolve from Central. The JVM POM names
      `zenoh-flat-jni-jvm`; the Android POM names `zenoh-flat-jni-android`.
      Both name the selected JNI version; see [After a release](#after-a-release).
- [ ] If requested, the GitHub release exists and its notes start at the correct
      previous release tag.

### When merging back to main

- [ ] `main` keeps `zenohFlatJniVersion=<JNI base version>-kotlin-SNAPSHOT`,
      using the base version of the JNI commit it pins after the merge.
