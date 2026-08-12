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
- [The snapshot publication](#the-snapshot-publication)
  - [What it does not guarantee](#what-it-does-not-guarantee)
- [Running a release](#running-a-release)
  - [Before the first run](#before-the-first-run)
  - [Rehearsal (dry run)](#rehearsal-dry-run)
  - [The real release](#the-real-release)
  - [After a release](#after-a-release)
- [Rehearsing before zenoh-flat-jni is released](#rehearsing-before-zenoh-flat-jni-is-released)
  - [Rehearsing the release workflow with a snapshot](#rehearsing-the-release-workflow-with-a-snapshot)
- [How the pipeline works](#how-the-pipeline-works)
- [Building against zenoh-flat-jni source](#building-against-zenoh-flat-jni-source)
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
**live** release against a `-SNAPSHOT` binding version. It checks the value
`gradle.properties` ends up with, not the workflow input: between releases that
file names a snapshot, so *omitting* the input is the way a release would reach
one. A rehearsal is allowed to, which is what makes the transition workable —
see
[Rehearsing before zenoh-flat-jni is released](#rehearsing-before-zenoh-flat-jni-is-released).

## The snapshot publication

Between releases, every merge to `main` uploads a mutable pre-release build to
the [Central snapshot
repository](https://central.sonatype.com/repository/maven-snapshots/). Its
purpose is to keep the upload machinery exercised — signing keys, credentials,
what Central accepts — and to give people a way to try the current `main`.

It publishes **five** coordinates, not two:

```text
org.eclipse.zenoh:zenoh-kotlin:<version>-SNAPSHOT
org.eclipse.zenoh:zenoh-kotlin-android:<version>-SNAPSHOT
org.eclipse.zenoh:zenoh-flat-jni:1.9.0-kotlin-SNAPSHOT        (+ -jvm, -android)
```

The last three are **our own copy** of zenoh-flat-jni, built from the commit
`Cargo.lock` pins. Publishing what we depend on is what makes the snapshot both
self-sufficient and coherent:

- **self-sufficient** — if zenoh-flat-jni's CI were switched off entirely, this
  publication still works. It uses that repository's *source at a commit we
  choose*, never an artifact its CI produced.
- **coherent** — the dependency our POM names is the code we compiled against.
  Pointing instead at zenoh-flat-jni's own `1.9.0-SNAPSHOT` would name the tip
  of *its* `main` while we compiled against our pin; JNI being a binary
  contract, that mismatch surfaces as `UnsatisfiedLinkError` at runtime rather
  than as a build failure.

The `-kotlin` qualifier keeps our copy from overwriting the one zenoh-flat-jni
publishes itself, or zenoh-java's — the three can legitimately pin different
commits at the same moment. The names are fixed rather than derived from a
commit, so each is overwritten in place and storage does not grow with the
number of builds. (Central still stores snapshots as timestamped builds and
cleans them after 90 days, so it is the consumer-facing *name* that is constant,
not the bytes behind it.)

Rebuilding that copy means cross-compiling ten targets, on the order of half an
hour, and the pin moves roughly once a day — so it is rebuilt only when it has
to be. Every POM zenoh-flat-jni publishes carries the commit it was built from:

```console
$ curl -s .../1.9.0-kotlin-SNAPSHOT/maven-metadata.xml           # ~2.9 kB
$ curl -s .../zenoh-flat-jni-1.9.0-kotlin-<timestamp>-<n>.pom    # ~1.8 kB
<zenoh.flatJniCommit>e75529ce…</zenoh.flatJniCommit>
```

`ci/scripts/flat-jni-copy.bash` reads that stamp from all three coordinates and
compares it with the pin; anything missing or different means rebuild. Run it
locally to see the decision, or `--self-test` to check its parsers.

### What it does not guarantee

The two uploads are separate Gradle invocations and a snapshot repository has no
staging-and-flip, so nothing makes the pair atomic. `main`'s CI runs are
serialized rather than cancelled — cancelling mid-publication is what splits
them — but a failure during the second upload still leaves a split state until
the next successful run. That is accepted for a mutable pre-release artifact;
strict coherence would need the SDK to name an immutable, timestamped snapshot,
which conflicts with the fixed names above.

Every publication is followed by `ci/consumer-smoke-test`, a separate Gradle
build with no connection to this one, which resolves the published
`zenoh-kotlin:<version>-SNAPSHOT` from the snapshot repository with
`--refresh-dependencies` and runs a key-expression round trip through JNI. That
is the check that the whole chain — POM, transitive zenoh-flat-jni, native
library — works for someone who is not us.

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
| `zenoh-flat-jni-version` | a version that exists — today a snapshot, see [below](#rehearsing-the-release-workflow-with-a-snapshot). Empty falls back to `gradle.properties`, which names our own `1.9.0-kotlin-SNAPSHOT` copy: fine for a rehearsal, refused for a live run |
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
| local build and tests | its source — the pinned commit, or your own checkout ([README](README.md#where-the-native-library-comes-from)) | the code compiles and the tests pass |
| CI, `maven_publish` unchecked | the snapshot repository | the artifacts assemble |
| CI, snapshot publication | `zenoh-flat-jni:<version>-SNAPSHOT` | signing, credentials, a real upload |
| live release | `zenoh-flat-jni:<version>` on Central | **blocked until that exists** |

A snapshot may depend on a snapshot, because nothing published is permanent —
which is what [The snapshot publication](#the-snapshot-publication) above rests
on. `gradle.properties` names `1.9.0-kotlin-SNAPSHOT`, our own copy, and every
merge to `main` republishes it from the pinned commit. So a rehearsal needs
nothing set: the default already resolves.

Name another one on the command line to build against a different
zenoh-flat-jni — the snapshot it publishes itself, or one from a rehearsal
there:

```bash
./gradlew build -PzenohFlatJniVersion=1.9.0-rc8-SNAPSHOT
```

### Rehearsing the release workflow with a snapshot

Find what is actually published — the list moves as zenoh-flat-jni rehearses:

```bash
curl -s https://central.sonatype.com/repository/maven-snapshots/org/eclipse/zenoh/zenoh-flat-jni/maven-metadata.xml
```

```xml
<latest>1.9.0-rc8-SNAPSHOT</latest>
```

Then run **Release** from the Actions tab with:

| input | value |
| --- | --- |
| `live-run` | **unchecked** — a live run refuses a `-SNAPSHOT` binding |
| `zenoh-flat-jni-version` | the version above, e.g. `1.9.0-rc8-SNAPSHOT` |
| `maven_publish` | checked to rehearse the upload too, unchecked to stop at assembly |
| `version`, `branch` | leave empty unless you are rehearsing a specific one |

**`zenoh-flat-jni-version` decides what the rehearsal builds against.** Left
empty it falls back to `zenohFlatJniVersion` in `gradle.properties` —
`1.9.0-kotlin-SNAPSHOT`, our own copy, which `main` republishes on every merge. A
rehearsal against that is a real rehearsal — leaving the field empty is now a
sound default rather than the guaranteed compile failure it used to be.

What that fallback cannot do is reach a **live** release:
`ci/scripts/bump-and-tag.bash` refuses a `-SNAPSHOT` binding, and it checks the
value `gradle.properties` ends up with rather than the input, precisely because
an omitted input now inherits one.

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

Every third-party action these workflows use is pinned to a **commit SHA**, with
the version in a trailing comment — a tag is mutable, and a moved tag would run
code nobody reviewed on a job that holds the signing key and the Central token.
The `eclipse-zenoh/ci` actions are ours and stay on `@main` deliberately: the
tagging and GitHub-release steps track whatever that branch holds at run time.
Bumping a pin is an ordinary pull request; read the diff of the action first.

## Building against zenoh-flat-jni source

A build can be pointed at zenoh-flat-jni's *source* through a Gradle composite
build, which is what CI and local development do — see
[Where the native library comes from](README.md#where-the-native-library-comes-from) in the README,
and [CI.md](CI.md) for the commit pin behind it.

**A release must not.** With a composite build the published artifact would be
built from source on the builder's disk while the POM still claimed the released
version it was supposed to be built against. Nothing opts in by default, and
`build.gradle.kts` fails any `publish*` task while an included build is present,
so a leftover `-PuseLocalJni`, `-PlocalJniDir` or `path = "…"` cannot reach a
publication silently.

Which `zenoh-flat-jni` *release* this SDK is built and published against is
`zenohFlatJniVersion` in `gradle.properties`, and that is unrelated to the above.

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
- **No consumer test before a *release*.** Every snapshot publication is followed
  by `ci/consumer-smoke-test`, which resolves the published artifact from the
  snapshot repository and runs it — but a release goes to a staging repository
  and is not resolvable at that point, so nothing consumes a release candidate
  the way zenoh-flat-jni's own dry-run repository lets it consume one.
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
- [ ] `useLocalJni` is off — the release resolves from Central.
- [ ] The published POM references a released `zenoh-flat-jni`, not a snapshot.
- [ ] For an Android release: the Android POM references
      `zenoh-flat-jni-android`, not the desktop coordinate.
- [ ] The released coordinates resolve from Maven Central.
