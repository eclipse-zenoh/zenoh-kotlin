#!/usr/bin/env bash
#
# Is the zenoh-flat-jni copy this repository publishes already the commit we pin?
#
# The SDK snapshot names org.eclipse.zenoh:zenoh-flat-jni:<qualified version>,
# and this repository is what puts that coordinate there — built from the commit
# Cargo.lock pins, so the dependency exists and is the code the SDK compiled
# against, whether or not zenoh-flat-jni's CI has ever run. Producing it means
# cross-compiling ten targets, on the order of half an hour, so it is rebuilt
# only when the published copy is not already that commit.
#
# The published copy says which commit it was built from as a POM property, so
# the check costs two small requests per coordinate rather than a 39 MB
# download. Anything missing — no metadata, no POM, no stamp — reads as "not
# ours" and rebuilds, which is the safe direction.
#
# Writes `commit`, `version`, `base`, `qualifier` and `rebuild` to $GITHUB_OUTPUT
# when running under Actions, and prints them either way. `--self-test` runs the
# parsers against fixtures and exits.
#
set -euo pipefail

cd "$(dirname "${BASH_SOURCE[0]}")/../.."

readonly repository=${SNAPSHOT_REPOSITORY:-https://central.sonatype.com/repository/maven-snapshots}
readonly group_path=org/eclipse/zenoh
# The qualifier that keeps our copy from overwriting zenoh-flat-jni's own
# snapshot or zenoh-java's. Checked against gradle.properties below, and
# handed to zenoh-flat-jni's publication workflow, so the two cannot drift.
readonly qualifier=${FLAT_JNI_QUALIFIER:-kotlin}
# All three coordinates, not just the root: one Gradle invocation uploads them,
# but a partial failure leaves them at different builds, and it is the platform
# ones that carry the native libraries.
#
# Each with the extension of its binary, because a matching stamp is not on its
# own proof of a finished publication. The POM goes up before the Gradle module
# metadata and before the jar or aar, so a publication that died in between —
# whether it was the first or an overwrite — leaves a readable stamp with no
# module metadata for Gradle to resolve a variant against, and a decision to skip
# rebuilding. Forever, since the next run reads the same stamp. Nothing
# downstream would catch it either: the consumer smoke test runs on Linux, and
# the broken variant could be the Android one.
readonly artifacts=(
    zenoh-flat-jni:jar
    zenoh-flat-jni-jvm:jar
    zenoh-flat-jni-android:aar
)

# The commit Cargo.lock pins for the git dependency on zenoh-flat-jni. Reads
# stdin so it can be tested without a lockfile.
pinned_commit() {
    grep -Eom1 'zenoh-flat-jni\.git[^#"]*#[0-9a-f]{40}' | grep -Eo '[0-9a-f]{40}$'
}

# Which timestamped build a snapshot currently resolves to, from that version's
# maven-metadata.xml on stdin:
#   <version without -SNAPSHOT>-<timestamp>-<buildNumber>
# It is also the middle of every file name in that build:
#   <artifact>-<value>.<ext>
snapshot_value() { # <version>
    local metadata timestamp build
    metadata=$(cat)
    timestamp=$(sed -n 's:.*<timestamp>\(.*\)</timestamp>.*:\1:p' <<<"$metadata" | head -1)
    build=$(sed -n 's:.*<buildNumber>\(.*\)</buildNumber>.*:\1:p' <<<"$metadata" | head -1)
    [[ -n $timestamp && -n $build ]] || return 1
    printf '%s-%s-%s' "${1%-SNAPSHOT}" "$timestamp" "$build"
}

# The commit a published POM on stdin was built from; empty when it has no stamp.
pom_commit() {
    sed -n 's:.*<zenoh\.flatJniCommit>\(.*\)</zenoh\.flatJniCommit>.*:\1:p' | head -1
}

# Whether the metadata on stdin says every one of the given extensions is at the
# given build. Each <snapshotVersion> carries its own <value>, updated as that
# file lands, so an overwrite that failed part-way leaves the POM at build N+1
# while the module metadata and the binary are still at N — which is the case a
# presence check cannot see, since all three entries exist either way and have
# since the first publication.
#
# The anchor is what excludes the sources and javadoc jars: the schema puts
# <classifier> before <extension>, so only a main artifact starts its entry with
# the extension.
all_at() { # <value> <extension>…
    local blocks value ext
    blocks=$(tr -d '[:space:]' | sed 's:<snapshotVersion>:\n:g')
    value=${1//./\\.}
    shift
    for ext in "$@"; do
        grep -q "^<extension>$ext</extension><value>$value</value>" <<<"$blocks" || return 1
    done
}

# The stamp of the published copy of one coordinate; empty if it is not there, or
# not all of it is at the same build.
published_commit() { # <artifact> <version> <binary extension>
    local base_url="$repository/$group_path/$1/$2" metadata value
    metadata=$(curl -sf "$base_url/maven-metadata.xml") || return 0
    value=$(snapshot_value "$2" <<<"$metadata") || return 0
    all_at "$value" pom module "$3" <<<"$metadata" || return 0
    # The POM is fetched by that name, so a metadata entry naming a file that
    # never landed reads as no stamp — and rebuilds.
    { curl -sf "$base_url/$1-$value.pom" || true; } | pom_commit
}

self_test() {
    local got
    got=$(pinned_commit <<<'source = "git+https://github.com/eclipse-zenoh/zenoh-flat-jni.git?branch=main#e75529ce3758401ce213456e7b8e4e5667635cf8"')
    [[ $got == e75529ce3758401ce213456e7b8e4e5667635cf8 ]] || { echo "pinned_commit: $got" >&2; exit 1; }

    # The real lockfile too, so a change to how Cargo writes it fails here
    # rather than by silently rebuilding on every run.
    got=$(pinned_commit <Cargo.lock)
    [[ $got =~ ^[0-9a-f]{40}$ ]] || { echo "pinned_commit(Cargo.lock): $got" >&2; exit 1; }

    got=$(snapshot_value 1.9.0-kotlin-SNAPSHOT <<'EOF'
<versioning>
    <snapshot>
      <timestamp>20260810.012355</timestamp>
      <buildNumber>1</buildNumber>
    </snapshot>
</versioning>
EOF
    )
    [[ $got == 1.9.0-kotlin-20260810.012355-1 ]] || { echo "snapshot_value: $got" >&2; exit 1; }

    # A release-style metadata carries no <snapshot> block: no build to name.
    if snapshot_value 1.9.0 <<<'<versioning><latest>1.9.0</latest></versioning>' >/dev/null; then
        echo "snapshot_value accepted metadata with no snapshot block" >&2
        exit 1
    fi

    got=$(pom_commit <<<'  <zenoh.flatJniCommit>e75529ce3758401ce213456e7b8e4e5667635cf8</zenoh.flatJniCommit>')
    [[ $got == e75529ce3758401ce213456e7b8e4e5667635cf8 ]] || { echo "pom_commit: $got" >&2; exit 1; }

    got=$(pom_commit <<<'<project><version>1.9.0-kotlin-SNAPSHOT</version></project>')
    [[ -z $got ]] || { echo "pom_commit on an unstamped POM: $got" >&2; exit 1; }

    # A finished publication of build -1, in the layout zenoh-flat-jni really
    # produces — checked against the published 1.9.0-rc8-SNAPSHOT.
    local n=1.9.0-kotlin-20260810.012355-1
    local finished="
      <snapshotVersion><extension>pom</extension><value>$n</value></snapshotVersion>
      <snapshotVersion><extension>module</extension><value>$n</value></snapshotVersion>
      <snapshotVersion><classifier>sources</classifier><extension>jar</extension><value>$n</value></snapshotVersion>
      <snapshotVersion><extension>jar</extension><value>$n</value></snapshotVersion>"
    all_at "$n" pom module jar <<<"$finished" || { echo "all_at: finished rejected" >&2; exit 1; }
    if all_at "$n" pom module aar <<<"$finished"; then
        echo "all_at: accepted a jar publication as an aar one" >&2; exit 1
    fi
    if all_at "$n" pom module jar <<<"<snapshotVersion><extension>pom</extension><value>$n</value></snapshotVersion>"; then
        echo "all_at: accepted a publication that stopped after the POM" >&2; exit 1
    fi
    if all_at "$n" jar <<<"<snapshotVersion><classifier>sources</classifier><extension>jar</extension><value>$n</value></snapshotVersion>"; then
        echo "all_at: took the sources jar for the main one" >&2; exit 1
    fi

    # The case a presence check cannot see: an overwrite that replaced the POM
    # and then failed, leaving the module metadata and the binary at the build
    # before it. Every extension is still listed; only the values disagree.
    local m=1.9.0-kotlin-20260811.030000-2
    if all_at "$m" pom module jar <<<"
      <snapshotVersion><extension>pom</extension><value>$m</value></snapshotVersion>
      <snapshotVersion><extension>module</extension><value>$n</value></snapshotVersion>
      <snapshotVersion><extension>jar</extension><value>$n</value></snapshotVersion>"; then
        echo "all_at: accepted a coordinate split across two builds" >&2; exit 1
    fi

    echo "flat-jni-copy.bash self-test OK"
}

main() {
    local version base commit rebuild=false entry artifact stamp
    version=$(sed -n 's/^zenohFlatJniVersion=//p' gradle.properties | tr -d '[:space:]')
    [[ $version == *-$qualifier-SNAPSHOT ]] || {
        echo "::error::zenohFlatJniVersion=$version is not a -$qualifier-SNAPSHOT copy;" \
             "only that coordinate is ours to publish" >&2
        exit 1
    }
    # zenoh-flat-jni derives the coordinate from its own version.txt, so it needs
    # to be told what we expect: a pin that moved past a version bump there would
    # otherwise publish 1.10.0-kotlin-SNAPSHOT while we still resolve this.
    base=${version%-$qualifier-SNAPSHOT}
    commit=$(pinned_commit <Cargo.lock)

    for entry in "${artifacts[@]}"; do
        artifact=${entry%:*}
        stamp=$(published_commit "$artifact" "$version" "${entry#*:}")
        if [[ $stamp == "$commit" ]]; then
            echo "$artifact:$version is already $commit"
        else
            echo "$artifact:$version stamp is ${stamp:-none}, want $commit"
            rebuild=true
        fi
    done

    echo "commit=$commit base=$base rebuild=$rebuild"
    if [[ -n ${GITHUB_OUTPUT:-} ]]; then
        {
            echo "commit=$commit"
            echo "version=$version"
            echo "base=$base"
            echo "qualifier=$qualifier"
            echo "rebuild=$rebuild"
        } >>"$GITHUB_OUTPUT"
    fi
}

if [[ ${1:-} == --self-test ]]; then self_test; else main; fi
